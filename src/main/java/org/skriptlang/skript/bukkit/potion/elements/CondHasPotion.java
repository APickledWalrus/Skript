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
package org.skriptlang.skript.bukkit.potion.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.conditions.base.PropertyCondition;
import ch.njol.skript.conditions.base.PropertyCondition.PropertyType;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Condition;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.util.Kleenean;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.potion.PotionEffectType;
import org.eclipse.jdt.annotation.Nullable;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Has Potion")
@Description("Checks whether the given living entities have specific potion effects.")
@Examples({
	"if player has potion speed:",
		"\tsend \"You are sonic!\"",
	"if all players have potion effects speed and haste:",
		"\tbroadcast \"You are ready to MINE!\""
})
@Since("2.6.1")
public class CondHasPotion extends Condition {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.CONDITION, SyntaxInfo.builder(CondHasPotion.class)
				.addPatterns(
						"%livingentities% (has|have) potion[s] [effect[s]] %potioneffecttypes%",
						"%livingentities% (doesn't|does not|do not|don't) have potion[s] [effect[s]] %potioneffecttypes%"
				)
				.build()
		);
	}

	@SuppressWarnings("NotNullFieldNotInitialized")
	private Expression<LivingEntity> livingEntities;
	@SuppressWarnings("NotNullFieldNotInitialized")
	private Expression<PotionEffectType> potionEffects;

	@Override
	@SuppressWarnings({"unchecked", "null"})
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		livingEntities = (Expression<LivingEntity>) exprs[0];
		potionEffects = (Expression<PotionEffectType>) exprs[1];
		setNegated(matchedPattern == 1);
		return true;
	}

	@Override
	public boolean check(Event event) {
		return livingEntities.check(event,
				livingEntity -> potionEffects.check(event,
						livingEntity::hasPotionEffect
				), isNegated()
		);
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return PropertyCondition.toString(
				this, PropertyType.HAVE, event, debug, livingEntities,
				"potion effects " + potionEffects.toString(event, debug)
		);
	}

}
