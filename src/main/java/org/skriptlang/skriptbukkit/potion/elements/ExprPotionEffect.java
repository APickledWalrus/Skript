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
package org.skriptlang.skriptbukkit.potion.elements;

import ch.njol.skript.Skript;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.ExpressionType;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.util.Timespan;
import ch.njol.util.Kleenean;
import org.skriptlang.skriptbukkit.potion.util.PotionUtils;
import org.skriptlang.skriptbukkit.potion.util.SkriptPotionEffect;
import org.bukkit.event.Event;
import org.bukkit.potion.PotionEffectType;
import org.eclipse.jdt.annotation.Nullable;

@Name("Potion Effect")
@Description({
	"Create a new potion effect to apply to an entity or item type.",
	"Note that when applying potion effects to items like tipped arrows and lingering potions, Minecraft reduces the timespan."
})
@Examples({
	"set {_p} to potion effect of speed 2 without particles for 10 minutes",
	"add {_p} to potion effects of player's tool",
	"add {_p} to potion effects of target entity",
	"add a potion effect of speed 1 to the potion effects of the player",
	"apply ambient speed 2 to player for 30 seconds"
})
@Since("2.5.2, INSERT VERSION (syntax changes)")
public class ExprPotionEffect extends SimpleExpression<SkriptPotionEffect> {

	static {
		Skript.registerExpression(ExprPotionEffect.class, SkriptPotionEffect.class, ExpressionType.COMBINED,
				"[a[n]] [:ambient] potion effect of %potioneffecttype% [[of tier] %-number%] [:without particles] [for %-timespan%]",
				"[a[n]] [:ambient] %potioneffecttype% [of tier] %number% [potion [effect]] [:without particles] [for %-timespan%]"
		);
	}

	@SuppressWarnings("NotNullFieldNotInitialized")
	private Expression<PotionEffectType> potionEffectType;
	@Nullable
	private Expression<Number> amplifier;
	@Nullable
	private Expression<Timespan> duration;
	private boolean particles;
	private boolean ambient;
	
	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		potionEffectType = (Expression<PotionEffectType>) exprs[0];
		amplifier = (Expression<Number>) exprs[1];
		duration = (Expression<Timespan>) exprs[2];
		particles = !parseResult.hasTag("without particles");
		ambient = parseResult.hasTag("ambient");
		return true;
	}
	
	@Override
	@Nullable
	protected SkriptPotionEffect[] get(Event event) {
		PotionEffectType potionEffectType = this.potionEffectType.getSingle(event);
		if (potionEffectType == null)
			return new SkriptPotionEffect[0];

		int amplifier = 0;
		if (this.amplifier != null) {
			Number amplifierNumber = this.amplifier.getSingle(event);
			if (amplifierNumber != null)
				amplifier = amplifierNumber.intValue() - 1;
		}

		int duration = PotionUtils.DEFAULT_DURATION_TICKS;
		if (this.duration != null) {
			Timespan timespan = this.duration.getSingle(event);
			if (timespan != null)
				duration = (int) timespan.getTicks_i();
		}

		return new SkriptPotionEffect[]{
				new SkriptPotionEffect(potionEffectType)
						.duration(duration)
						.amplifier(amplifier)
						.ambient(ambient)
						.particles(particles)
		};
	}
	
	@Override
	public boolean isSingle() {
		return true;
	}
	
	@Override
	public Class<? extends SkriptPotionEffect> getReturnType() {
		return SkriptPotionEffect.class;
	}
	
	@Override
	public String toString(@Nullable Event event, boolean debug) {
		StringBuilder builder = new StringBuilder();
		if (ambient)
			builder.append("ambient ");
		builder.append("potion effect of ").append(potionEffectType.toString(event, debug));
		if (amplifier != null)
			builder.append(" of tier ").append(amplifier.toString(event, debug));
		if (!particles)
			builder.append(" without particles");
		builder.append(" for ");
		if (duration != null) {
			builder.append(duration.toString(event, debug));
		} else {
			builder.append(PotionUtils.DEFAULT_DURATION_STRING);
		}
		return builder.toString();
	}
	
}
