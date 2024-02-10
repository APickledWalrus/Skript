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

import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffectType;

import ch.njol.skript.conditions.base.PropertyCondition;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Is Poisoned")
@Description("Checks whether an entity is poisoned.")
@Examples({
	"if the player is poisoned:",
		"\tcure the player from poison",
		"\tmessage \"You have been cured!\" to the player"
})
@Since("1.4.4")
public class CondIsPoisoned extends PropertyCondition<LivingEntity> {

	public static void register(SyntaxRegistry registry) {
		register(registry, CondIsPoisoned.class, "poisoned", "livingentities");
	}

	@Override
	public boolean check(LivingEntity entity) {
		return entity.hasPotionEffect(PotionEffectType.POISON);
	}

	@Override
	protected String getPropertyName() {
		return "poisoned";
	}

}
