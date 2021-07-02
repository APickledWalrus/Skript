/**
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
 * Copyright Peter Güttinger, SkriptLang team and contributors
 */
package ch.njol.skript.lang;

import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.parser.ParserInstance;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.eclipse.jdt.annotation.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class EffectSection extends Section {

	private boolean hasSection = false;

	public boolean hasSection() {
		return hasSection;
	}

	/**
	 * This method should not be overridden unless you know what you are doing!
	 */
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		SectionContext sectionContext = getParser().getData(SectionContext.class);
		//noinspection ConstantConditions - For an EffectSection, it may be null
		hasSection = sectionContext.sectionNode != null;
		return init(exprs, matchedPattern, isDelayed, parseResult, sectionContext.sectionNode, sectionContext.triggerItems);
	}

	@Override
	public abstract boolean init(Expression<?>[] exprs,
								 int matchedPattern,
								 Kleenean isDelayed,
								 ParseResult parseResult,
								 @Nullable SectionNode sectionNode,
								 @Nullable List<TriggerItem> triggerItems);

	@Override
	public abstract void execute(Event event);

	/**
	 * Similar to {@link Section#parse(String, String, SectionNode, List)}, but will only attempt to parse from other {@link EffectSection}s.
	 */
	@Nullable
	@SuppressWarnings({"unchecked", "rawtypes", "ConstantConditions"})
	public static EffectSection parse(String expr, @Nullable String defaultError, @Nullable SectionNode sectionNode, @Nullable List<TriggerItem> triggerItems) {
		SectionContext sectionContext = ParserInstance.get().getData(SectionContext.class);
		sectionContext.sectionNode = sectionNode;
		sectionContext.triggerItems = triggerItems;

		List<SyntaxElementInfo<? extends Section>> sections = new ArrayList<>(Skript.getSections());
		sections.removeIf(info -> !EffectSection.class.isAssignableFrom(info.c)); // Only parse from EffectSections

		return (EffectSection) SkriptParser.parse(expr, (Iterator) sections.iterator(), defaultError);
	}

}