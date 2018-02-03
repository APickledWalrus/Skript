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

/**
 * Magic numbers used by the Skript parser.
 * 
 */
public interface ParserMagic {
	
	/**
	 * Parse other expressions.
	 */
	public static final int PARSE_EXPRESSIONS = 0b1;
	
	/**
	 * Parse literals. For them, final value can be determined parse-time.
	 */
	public static final int PARSE_LITERALS = 0b10;
	
	/**
	 * Parse variables. Probably would be used 
	 */
	public static final int PARSE_VARIABLES = 0b100;
	
	/**
	 * Parse expressions, literals and variables.
	 */
	public static final int PARSE_EVERYTHING = PARSE_EXPRESSIONS | PARSE_LITERALS | PARSE_VARIABLES;
}
