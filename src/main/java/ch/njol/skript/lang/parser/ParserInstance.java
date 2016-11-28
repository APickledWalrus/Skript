/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * 
 * Copyright 2011-2016 Peter Güttinger and contributors
 * 
 */

package ch.njol.skript.lang.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.regex.Matcher;

import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

import ch.njol.skript.ScriptLoader;
import ch.njol.skript.Skript;
import ch.njol.skript.SkriptCommand;
import ch.njol.skript.SkriptEventHandler;
import ch.njol.skript.aliases.Aliases;
import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.classes.ClassInfo;
import ch.njol.skript.command.CommandEvent;
import ch.njol.skript.command.Commands;
import ch.njol.skript.command.ScriptCommand;
import ch.njol.skript.config.Config;
import ch.njol.skript.config.EntryNode;
import ch.njol.skript.config.Node;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.config.SimpleNode;
import ch.njol.skript.effects.Delay;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Conditional;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Loop;
import ch.njol.skript.lang.ParseContext;
import ch.njol.skript.lang.SelfRegisteringSkriptEvent;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptEventInfo;
import ch.njol.skript.lang.SkriptParser;
import ch.njol.skript.lang.Statement;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.lang.TriggerSection;
import ch.njol.skript.lang.While;
import ch.njol.skript.lang.function.Function;
import ch.njol.skript.lang.function.FunctionEvent;
import ch.njol.skript.lang.function.Functions;
import ch.njol.skript.lang.function.ScriptFunction;
import ch.njol.skript.log.ErrorQuality;
import ch.njol.skript.log.LogEntry;
import ch.njol.skript.log.LogHandler;
import ch.njol.skript.log.ParseLogHandler;
import ch.njol.skript.log.RetainingLogHandler;
import ch.njol.skript.log.SkriptLogger;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.registrations.Converters;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Callback;
import ch.njol.util.Kleenean;
import ch.njol.util.NonNullPair;
import ch.njol.util.StringUtils;
import ch.njol.util.coll.CollectionUtils;

/**
 * Instance of Skript parser. Runs asynchronously.
 */
public class ParserInstance implements Runnable, Comparable<ParserInstance>, ParseLogger {
	
	/**
	 * Dummy parser instance. Can be used for legacy code.
	 * All log handles submitted to this will print their contents
	 * immediately.
	 */
	public static final ParserInstance DUMMY = new ParserInstance() {
		@Override
		public void submitErrorLog(final ParseLogHandler log) {
			// TODO - for now, ignore
		}
		
		@Override
		public void submitParseLog(LogHandler log) {
			// TODO - for now, ignore
		}
	};
	
	public static ParserInstance forScript(String fileName, Config cfg, ScriptManager manager) {
		return new ParserInstance(fileName, cfg, manager);
	}
	
	public static ParserInstance forEffectCommand() {
		ParserInstance pi = new ParserInstance();
		pi.setCurrentEvent("effect command", CommandEvent.class);
		return pi;
	}
	
	public final Config config;
	private ScriptManager manager;
	public final Map<String, ItemType> aliases;
	public final Map<String, String> options;
	
	private int numCommands;
	private int numFunctions;
	private int numTriggers;
	
	public final List<ScriptCommand> commands;
	public final Map<NonNullPair<SkriptEventInfo<?>, SkriptEvent>,Trigger> selfRegisteringTriggers;
	public final Map<Class<? extends Event>[],Trigger> triggers;
	public final List<ScriptFunction<?>> functions;
	
	public final RetainingLogHandler log;
	@Nullable
	private Node node;
	
	public final List<TriggerSection> currentSections;
	public final List<Loop> currentLoops;
	
	private String fileName;
	
	private String indentation = "";
	
	public Kleenean hasDelayBefore = Kleenean.FALSE;
	
	/**
	 * use {@link #setCurrentEvent(String, Class...)}
	 */
	@Nullable
	private Class<? extends Event>[] currentEvents = null;
	
	/**
	 * use {@link #setCurrentEvent(String, Class...)}
	 */
	@Nullable
	private String currentEventName = null;
	
	@SuppressWarnings("null") // Note: only for dummy object
	protected ParserInstance() {
		config = null;
		aliases = null;
		options = null;
		commands = null;
		selfRegisteringTriggers = null;
		triggers = null;
		functions = null;
		currentSections = null;
		currentLoops = null;
		log = null;
	}
	
	protected ParserInstance(String fileName, Config config, ScriptManager manager) {
		this.fileName = fileName;
		this.config = config;
		this.manager = manager;
		this.aliases = new HashMap<>();
		this.options = new HashMap<>();
		this.commands = new ArrayList<>();
		this.selfRegisteringTriggers = new HashMap<>();
		this.triggers = new HashMap<>();
		this.functions = new ArrayList<>();
		this.log = SkriptLogger.startRetainingLog();
		this.currentSections = new ArrayList<>();
		this.currentLoops = new ArrayList<>();
	}
	
	/**
	 * Call {@link #deleteCurrentEvent()} after parsing.
	 * 
	 * @param name
	 * @param events
	 */
	@SafeVarargs
	public final void setCurrentEvent(final String name, final @Nullable Class<? extends Event>... events) {
		currentEventName = name;
		currentEvents = events;
		hasDelayBefore = Kleenean.FALSE;
	}
	
	public void deleteCurrentEvent() {
		currentEventName = null;
		currentEvents = null;
		hasDelayBefore = Kleenean.FALSE;
	}
	
	/**
	 * Gets name of current event.
	 * @return Name or null if not found.
	 */
	@Nullable
	public String getCurrentEventName() {
		return currentEventName;
	}
	
	public final boolean isCurrentEvent(final @Nullable Class<? extends Event> event) {
		return CollectionUtils.containsSuperclass(currentEvents, event);
	}
	
	@SafeVarargs
	public final boolean isCurrentEvent(final Class<? extends Event>... events) {
		return CollectionUtils.containsAnySuperclass(currentEvents, events);
	}
	
	/**
	 * Use this sparingly; {@link #isCurrentEvent(Class)} or {@link #isCurrentEvent(Class...)} should be used in most cases.
	 */
	@Nullable
	public Class<? extends Event>[] getCurrentEvents() {
		return currentEvents;
	}
	
	public String replaceOptions(final String s) {
		final String r = StringUtils.replaceAll(s, "\\{@(.+?)\\}", new Callback<String, Matcher>() {
			@Override
			@Nullable
			public String run(final Matcher m) {
				final String option = options.get(m.group(1));
				if (option == null) {
					error("undefined option " + m.group());
					return m.group();
				}
				return option;
			}
		});
		assert r != null;
		return r;
	}
	
	public List<TriggerItem> loadItems(final SectionNode node) {
		
		if (Skript.debug())
			indentation += "    ";
		
		final ArrayList<TriggerItem> items = new ArrayList<>();
		
		Kleenean hadDelayBeforeLastIf = Kleenean.FALSE;
		
		for (final Node n : node) {
			setNode(n);
			if (n instanceof SimpleNode) {
				final SimpleNode e = (SimpleNode) n;
				final String s = replaceOptions("" + e.getKey());
				if (!SkriptParser.validateLine(this, s))
					continue;
				final Statement stmt = Statement.parse(s, "Can't understand this condition/effect: " + s, this);
				if (stmt == null)
					continue;
				if (Skript.debug() || n.debug())
					Skript.debug(indentation + stmt.toString(null, true));
				items.add(stmt);
				if (stmt instanceof Delay)
					hasDelayBefore = Kleenean.TRUE;
			} else if (n instanceof SectionNode) {
				String name = replaceOptions("" + n.getKey());
				if (!SkriptParser.validateLine(this, name))
					continue;
				
				if (StringUtils.startsWithIgnoreCase(name, "loop ")) {
					final String l = "" + name.substring("loop ".length());
					final RetainingLogHandler h = SkriptLogger.startRetainingLog();
					Expression<?> loopedExpr;
					try {
						loopedExpr = new SkriptParser(this, l).parseExpression(Object.class);
						if (loopedExpr != null)
							loopedExpr = loopedExpr.getConvertedExpression(Object.class);
						if (loopedExpr == null) {
							h.printErrors("Can't understand this loop: '" + name + "'");
							continue;
						}
						h.printLog();
					} finally {
						h.stop();
					}
					if (loopedExpr.isSingle()) {
						error("Can't loop " + loopedExpr + " because it's only a single value");
						continue;
					}
					if (Skript.debug() || n.debug())
						Skript.debug(indentation + "loop " + loopedExpr.toString(null, true) + ":");
					final Kleenean hadDelayBefore = hasDelayBefore;
					items.add(new Loop(loopedExpr, (SectionNode) n, this));
					if (hadDelayBefore != Kleenean.TRUE && hasDelayBefore != Kleenean.FALSE)
						hasDelayBefore = Kleenean.UNKNOWN;
				} else if (StringUtils.startsWithIgnoreCase(name, "while ")) {
					final String l = "" + name.substring("while ".length());
					final Condition c = Condition.parse(this, l, "Can't understand this condition: " + l);
					if (c == null)
						continue;
					if (Skript.debug() || n.debug())
						Skript.debug(indentation + "while " + c.toString(null, true) + ":");
					final Kleenean hadDelayBefore = hasDelayBefore;
					items.add(new While(c, (SectionNode) n, this));
					if (hadDelayBefore != Kleenean.TRUE && hasDelayBefore != Kleenean.FALSE)
						hasDelayBefore = Kleenean.UNKNOWN;
				} else if (name.equalsIgnoreCase("else")) {
					if (items.size() == 0 || !(items.get(items.size() - 1) instanceof Conditional) || ((Conditional) items.get(items.size() - 1)).hasElseClause()) {
						error("'else' has to be placed just after an 'if' or 'else if' section");
						continue;
					}
					if (Skript.debug() || n.debug())
						Skript.debug(indentation + "else:");
					final Kleenean hadDelayAfterLastIf = hasDelayBefore;
					hasDelayBefore = hadDelayBeforeLastIf;
					((Conditional) items.get(items.size() - 1)).loadElseClause((SectionNode) n);
					hasDelayBefore = hadDelayBeforeLastIf.or(hadDelayAfterLastIf.and(hasDelayBefore));
				} else if (StringUtils.startsWithIgnoreCase(name, "else if ")) {
					if (items.size() == 0 || !(items.get(items.size() - 1) instanceof Conditional) || ((Conditional) items.get(items.size() - 1)).hasElseClause()) {
						error("'else if' has to be placed just after another 'if' or 'else if' section");
						continue;
					}
					name = "" + name.substring("else if ".length());
					final Condition cond = Condition.parse(this, name, "can't understand this condition: '" + name + "'");
					if (cond == null)
						continue;
					if (Skript.debug() || n.debug())
						Skript.debug(indentation + "else if " + cond.toString(null, true));
					final Kleenean hadDelayAfterLastIf = hasDelayBefore;
					hasDelayBefore = hadDelayBeforeLastIf;
					((Conditional) items.get(items.size() - 1)).loadElseIf(cond, (SectionNode) n);
					hasDelayBefore = hadDelayBeforeLastIf.or(hadDelayAfterLastIf.and(hasDelayBefore.and(Kleenean.UNKNOWN)));
				} else {
					if (StringUtils.startsWithIgnoreCase(name, "if "))
						name = "" + name.substring(3);
					final Condition cond = Condition.parse(this, name, "can't understand this condition: '" + name + "'");
					if (cond == null)
						continue;
					if (Skript.debug() || n.debug())
						Skript.debug(indentation + cond.toString(null, true) + ":");
					final Kleenean hadDelayBefore = hasDelayBefore;
					hadDelayBeforeLastIf = hadDelayBefore;
					items.add(new Conditional(cond, (SectionNode) n, this));
					hasDelayBefore = hadDelayBefore.or(hasDelayBefore.and(Kleenean.UNKNOWN));
				}
			}
		}
		
		for (int i = 0; i < items.size() - 1; i++)
			items.get(i).setNext(items.get(i + 1));
		
		setNode(node);
		
		if (Skript.debug())
			indentation = "" + indentation.substring(0, indentation.length() - 4);
		
		return items;
	}
	
	@Override
	public void submitErrorLog(ParseLogHandler log) {
		log(log.getError());
	}
	
	@Override
	public void submitParseLog(LogHandler log) {
		if (log instanceof ParseLogHandler)
			logAll(((ParseLogHandler) log).getLog());
		else if (log instanceof RetainingLogHandler)
			logAll(((RetainingLogHandler) log).getLog());
	}
	
	@SuppressWarnings("null")
	@Override
	public void error(@Nullable String msg, ErrorQuality quality) {
		if (msg != null)
			log(new LogEntry(Level.SEVERE, quality, msg, node));
	}
	
	@Override
	public void error(@Nullable String msg) {
		if (msg != null)
			error(msg, ErrorQuality.SEMANTIC_ERROR);
	}
	
	@SuppressWarnings("null")
	@Override
	public void warning(@Nullable String msg) {
		if (msg != null)
			log(new LogEntry(Level.WARNING, msg, node));
	}
	
	@SuppressWarnings("null")
	@Override
	public void info(@Nullable String msg) {
		if (msg != null)
			log(new LogEntry(Level.INFO, msg, node));
	}
	
	@Override
	public void log(@Nullable LogEntry entry) {
		if (entry != null)
			log.log(entry);
	}
	
	@Override
	public void setNode(@Nullable Node node) {
		this.node = node;
	}
	
	@Override
	@Nullable
	public Node getNode() {
		return node;
	}
 	
	@Override
	public void run() {
		ScriptLoader.registerParseThread(this); // This is hack for old addons
		
		for (final Node cnode : config.getMainNode()) {
			if (!(cnode instanceof SectionNode)) {
				error("invalid line - all code has to be put into triggers");
				continue;
			}
			
			final SectionNode node = ((SectionNode) cnode);
			String event = node.getKey();
			if (event == null)
				continue;
			
			if (event.equalsIgnoreCase("aliases")) {
				node.convertToEntries(0, "=");
				for (final Node n : node) {
					if (!(n instanceof EntryNode)) {
						error("invalid line in aliases section");
						continue;
					}
					final ItemType t = Aliases.parseAlias(((EntryNode) n).getValue());
					if (t == null)
						continue;
					aliases.put(((EntryNode) n).getKey().toLowerCase(), t);
				}
				continue;
			} else if (event.equalsIgnoreCase("options")) {
				node.convertToEntries(0);
				for (final Node n : node) {
					if (!(n instanceof EntryNode)) {
						error("invalid line in options");
						continue;
					}
					options.put(((EntryNode) n).getKey(), ((EntryNode) n).getValue());
				}
				continue;
			} else if (event.equalsIgnoreCase("variables")) {
				// TODO allow to make these override existing variables
				node.convertToEntries(0, "=");
				for (final Node n : node) {
					if (!(n instanceof EntryNode)) {
						error("Invalid line in variables section");
						continue;
					}
					String name = ((EntryNode) n).getKey().toLowerCase(Locale.ENGLISH);
					if (name.startsWith("{") && name.endsWith("}"))
						name = "" + name.substring(1, name.length() - 1);
					final String var = name;
					name = StringUtils.replaceAll(name, "%(.+)?%", new Callback<String, Matcher>() {
						@Override
						@Nullable
						public String run(final Matcher m) {
							if (m.group(1).contains("{") || m.group(1).contains("}") || m.group(1).contains("%")) {
								error("'" + var + "' is not a valid name for a default variable");
								return null;
							}
							final ClassInfo<?> ci = Classes.getClassInfoFromUserInput("" + m.group(1));
							if (ci == null) {
								error("Can't understand the type '" + m.group(1) + "'");
								return null;
							}
							return "<" + ci.getCodeName() + ">";
						}
					});
					if (name == null) {
						continue;
					} else if (name.contains("%")) {
						error("Invalid use of percent signs in variable name");
						continue;
					}
					if (Variables.getVariable(name, null, false) != null)
						continue;
					Object o;
					final ParseLogHandler log = SkriptLogger.startParseLogHandler();
					try {
						o = Classes.parseSimple(this, ((EntryNode) n).getValue(), Object.class, ParseContext.SCRIPT);
						if (o == null) {
							log.printError("Can't understand the value '" + ((EntryNode) n).getValue() + "'");
							continue;
						}
						log.printLog();
					} finally {
						log.stop();
					}
					final ClassInfo<?> ci = Classes.getSuperClassInfo(o.getClass());
					if (ci.getSerializer() == null) {
						error("Can't save '" + ((EntryNode) n).getValue() + "' in a variable");
						continue;
					} else if (ci.getSerializeAs() != null) {
						final ClassInfo<?> as = Classes.getExactClassInfo(ci.getSerializeAs());
						if (as == null) {
							assert false : ci;
							continue;
						}
						o = Converters.convert(o, as.getC());
						if (o == null) {
							error("Can't save '" + ((EntryNode) n).getValue() + "' in a variable");
							continue;
						}
					}
					Variables.setVariable(name, o, null, false);
				}
				continue;
			}
			
			if (!SkriptParser.validateLine(this, event))
				continue;
			
			if (event.toLowerCase().startsWith("command ")) {
				
				setCurrentEvent("command", CommandEvent.class);
				
				final ScriptCommand c = Commands.loadCommand(node, this);
				if (c != null) {
					numCommands++;
					commands.add(c);
				}
				
				deleteCurrentEvent();
				
				continue;
			} else if (event.toLowerCase().startsWith("function ")) {
				
				setCurrentEvent("function", FunctionEvent.class);
				
				final Function<?> func = Functions.loadFunction(node, this);
				if (func != null) {
					numFunctions++;
				}
				
				if (func instanceof ScriptFunction)
					functions.add((ScriptFunction<?>) func);
				
				deleteCurrentEvent();
				
				continue;
			}
			
			if (Skript.logVeryHigh() && !Skript.debug())
				Skript.info("loading trigger '" + event + "'");
			
			if (StringUtils.startsWithIgnoreCase(event, "on "))
				event = "" + event.substring("on ".length());
			
			event = replaceOptions(event);
			
			final NonNullPair<SkriptEventInfo<?>, SkriptEvent> parsedEvent = SkriptParser.parseEvent(this, event, "can't understand this event: '" + node.getKey() + "'");
			if (parsedEvent == null)
				continue;
			
			if (Skript.debug() || node.debug())
				Skript.debug(event + " (" + parsedEvent.getSecond().toString(null, true) + "):");
			
			setCurrentEvent("" + parsedEvent.getFirst().getName().toLowerCase(Locale.ENGLISH), parsedEvent.getFirst().events);
			final Trigger trigger;
			try {
				trigger = new Trigger(config.getFile(), event, parsedEvent.getSecond(), loadItems(node));
			} finally {
				deleteCurrentEvent();
			}
			
			if (parsedEvent.getSecond() instanceof SelfRegisteringSkriptEvent) {
				selfRegisteringTriggers.put(parsedEvent, trigger);
			} else {
				triggers.put(parsedEvent.getFirst().events, trigger);
			}
			
			numTriggers++;
		}
		
		manager.parseReady(this);
	}

	@Override
	public int compareTo(@Nullable ParserInstance o) {
		assert o != null;
		return fileName.compareTo(o.fileName);
	}
	
}
