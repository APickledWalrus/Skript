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
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import org.skriptlang.skript.bukkit.potion.util.PotionUtils;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.eclipse.jdt.annotation.Nullable;

@Name("Poison/Cure")
@Description("Poison or cure an entity. If the entity is already poisoned, the duration may be overwritten.")
@Examples({
	"poison the player",
	"poison the victim for 20 seconds",
	"cure the player from poison"
})
@Since("1.3.2")
public class EffPoison extends Effect {

	static {
		Skript.registerEffect(EffPoison.class,
				"poison %livingentities% [for %-timespan%]",
				"(cure|unpoison) %livingentities% [(from|of) poison]");
	}
	
	@SuppressWarnings("NotNullFieldNotInitialized")
	private Expression<LivingEntity> entities;
	@Nullable
	private Expression<Timespan> duration;
	
	private boolean cure;
	
	@SuppressWarnings({"unchecked", "null"})
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		entities = (Expression<LivingEntity>) exprs[0];
		if (matchedPattern == 0)
			duration = (Expression<Timespan>) exprs[1];
		cure = matchedPattern == 1;
		return true;
	}
	
	@Override
	protected void execute(Event event) {
		if (cure) {
			for (LivingEntity entity : entities.getArray(event))
				entity.removePotionEffect(PotionEffectType.POISON);
		} else {
			int duration = PotionUtils.DEFAULT_DURATION_TICKS;
			if (this.duration != null) {
				Timespan timespan = this.duration.getSingle(event);
				if (timespan != null)
					duration = (int) timespan.getTicks();
			}
			for (LivingEntity livingEntity : entities.getArray(event)) {
				int specificDuration = duration;
				if (livingEntity.hasPotionEffect(PotionEffectType.POISON)) { // if the entity is already poisoned, increase the duration
					//noinspection ConstantConditions - PotionEffect cannot be null (checked above)
					specificDuration += livingEntity.getPotionEffect(PotionEffectType.POISON).getDuration();
				}
				livingEntity.addPotionEffect(new PotionEffect(PotionEffectType.POISON, specificDuration, 0));
			}
		}
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return (cure ? "cure" : "poison")
				+ " " + entities.toString(event, debug)
				+ (duration != null ? " for " + duration.toString(event, debug) : "");
	}
	
}
