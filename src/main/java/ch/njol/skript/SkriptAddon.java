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
package ch.njol.skript;

import ch.njol.skript.localization.Language;
import ch.njol.skript.util.Utils;
import ch.njol.skript.util.Version;
import ch.njol.util.StringUtils;
import ch.njol.util.coll.iterator.EnumerationIterable;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jdt.annotation.Nullable;
import org.skriptlang.skript.registration.Module;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for Skript addons. Use {@link Skript#registerAddon(JavaPlugin)} to create a SkriptAddon instance for your plugin.
 */
public final class SkriptAddon {
	
	public final JavaPlugin plugin;
	public final Version version;
	
	/**
	 * Package-private constructor. Use {@link Skript#registerAddon(JavaPlugin)} to get a SkriptAddon for your plugin.
	 * 
	 * @param plugin The plugin representing the SkriptAddon to be registered.
	 */
	SkriptAddon(JavaPlugin plugin) {
		this.plugin = plugin;

		Version version;
		String descriptionVersion = plugin.getDescription().getVersion();
		try {
			version = new Version(descriptionVersion);
		} catch (final IllegalArgumentException e) {
			Matcher m = Pattern.compile("(\\d+)(?:\\.(\\d+)(?:\\.(\\d+))?)?").matcher(descriptionVersion);
			if (!m.find())
				throw new IllegalArgumentException("The version of the plugin " + plugin.getName() + " does not contain any numbers: " + descriptionVersion);
			version = new Version(Utils.parseInt("" + m.group(1)), m.group(2) == null ? 0 : Utils.parseInt("" + m.group(2)), m.group(3) == null ? 0 : Utils.parseInt("" + m.group(3)));
			Skript.warning("The plugin " + plugin.getName() + " uses a non-standard version syntax: '" + descriptionVersion + "'. Skript will use " + version + " instead.");
		}
		this.version = version;
	}
	
	@Override
	public String toString() {
		return plugin.getName();
	}
	
	public String getName() {
		return plugin.getName();
	}

	/**
	 * Loads classes of the plugin by package. Useful for registering many syntax elements like Skript.
	 *
	 * @param basePackage The base package to start searching in (e.g. 'ch.njol.skript').
	 * @param subPackages Specific subpackages to search in (e.g. 'conditions')
	 *                    If no subpackages are provided, all subpackages of the base package will be searched.
	 * @return This SkriptAddon
	 */
	public SkriptAddon loadClasses(String basePackage, String... subPackages) {
		return loadClasses(null, true, basePackage, true, subPackages);
	}

	/**
	 * Loads classes of the plugin by package. Useful for registering many syntax elements like Skript.
	 *
	 * @param withClass A consumer that will run with each found class.
	 * @param initialize Whether classes found in the package search should be initialized.
	 * @param basePackage The base package to start searching in (e.g. 'ch.njol.skript').
	 * @param recursive Whether to recursively search through the subpackages provided.
	 * @param subPackages Specific subpackages to search in (e.g. 'conditions')
	 *                    If no subpackages are provided, all subpackages of the base package will be searched.
	 * @return This SkriptAddon
	 */
	@SuppressWarnings("ThrowableNotThrown")
	public SkriptAddon loadClasses(@Nullable Consumer<Class<?>> withClass, boolean initialize, String basePackage, boolean recursive, String... subPackages) {
		for (int i = 0; i < subPackages.length; i++)
			subPackages[i] = subPackages[i].replace('.', '/') + "/";
		basePackage = basePackage.replace('.', '/') + "/";

		int depth = !recursive ? StringUtils.count(basePackage, '/') + 1 : 0;

		File file = getFile();
		if (file == null) {
			Skript.error("Unable to retrieve file from addon '" + getName() + "'. Classes will not be loaded.");
			return this;
		}

		try (JarFile jar = new JarFile(file)) {
			List<String> classNames = new ArrayList<>();
			boolean hasWithClass = withClass != null;
			for (JarEntry e : new EnumerationIterable<>(jar.entries())) {
				String name = e.getName();
				if (name.startsWith(basePackage) && name.endsWith(".class") && (recursive || StringUtils.count(name, '/') <= depth)) {
					boolean load = subPackages.length == 0;
					for (String subPackage : subPackages) {
						if (e.getName().startsWith(subPackage, basePackage.length())) {
							load = true;
							break;
						}
					}
					if (load)
						classNames.add(e.getName().replace('/', '.').substring(0, e.getName().length() - ".class".length()));
				}
			}
			classNames.sort(String::compareToIgnoreCase);
			for (String c : classNames) {
				try {
					Class<?> clazz = Class.forName(c, initialize, plugin.getClass().getClassLoader());
					if (hasWithClass)
						withClass.accept(clazz);
				} catch (ClassNotFoundException ex) {
					Skript.exception(ex, "Cannot load class " + c);
				} catch (ExceptionInInitializerError err) {
					Skript.exception(err.getCause(), this + "'s class " + c + " generated an exception while loading");
				}
			}
		} catch (IOException e) {
			Skript.exception(e, "Failed to load classes for addon: " + plugin.getName());
		}
		return this;
	}

	/**
	 * Loads all module classes found in the package search.
	 * @param basePackage The base package to start searching in (e.g. 'ch.njol.skript').
	 * @param subPackages Specific subpackages to search in (e.g. 'conditions').
	 *                    If no subpackages are provided, all subpackages will be searched.
	 *                    Note that the search will go no further than the first layer of subpackages.
	 */
	@SuppressWarnings("ThrowableNotThrown")
	public SkriptAddon loadModules(String basePackage, String... subPackages) {
		return loadClasses(c -> {
			if (Module.class.isAssignableFrom(c) && !c.isInterface() && !Modifier.isAbstract(c.getModifiers())) {
				try {
					((Module) c.getConstructor().newInstance()).register(this);
				} catch (Exception e) {
					Skript.exception(e, "Failed to load registration " + c);
				}
			}
		}, false, basePackage, false, subPackages);
	}
	
	@Nullable
	private String languageFileDirectory = null;
	
	/**
	 * Loads language files from the specified directory (e.g. "lang") into Skript.
	 * Localized files will be read from the plugin's jar and the plugin's data file,
	 * but the <b>default.lang</b> file is only taken from the jar and <b>must</b> exist!
	 * 
	 * @param directory Directory name
	 * @return This SkriptAddon
	 */
	public SkriptAddon setLanguageFileDirectory(String directory) {
		if (languageFileDirectory != null)
			throw new IllegalStateException();
		directory = "" + directory.replace('\\', '/');
		if (directory.endsWith("/"))
			directory = "" + directory.substring(0, directory.length() - 1);
		languageFileDirectory = directory;
		Language.loadDefault(this);
		return this;
	}

	/**
	 * @return The language file directory set for this addon.
	 * It must first be set using {@link #setLanguageFileDirectory(String)}.
	 */
	@Nullable
	public String getLanguageFileDirectory() {
		return languageFileDirectory;
	}
	
	@Nullable
	private File file = null;
	
	/**
	 * @return The jar file of the plugin.
	 * 			After this method is first called, the file will be cached for future use.
	 */
	@Nullable
	public File getFile() {
		if (file != null)
			return file;
		try {
			Method getFile = JavaPlugin.class.getDeclaredMethod("getFile");
			getFile.setAccessible(true);
			file = (File) getFile.invoke(plugin);
			return file;
		} catch (NoSuchMethodException | IllegalArgumentException e) {
			Skript.outdatedError(e);
		} catch (IllegalAccessException e) {
			assert false;
		} catch (SecurityException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e.getCause());
		}
		return null;
	}
	
}
