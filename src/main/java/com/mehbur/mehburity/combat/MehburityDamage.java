package com.mehbur.mehburity.combat;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * Mehburity's beam damage type, defined in {@code data/mehburity/damage_type/beam.json}. Its own type gives
 * death messages like "Zombie was zapped by Mehburity".
 */
public final class MehburityDamage {
	public static final ResourceKey<DamageType> BEAM = ResourceKey.create(Registries.DAMAGE_TYPE, MehburityMod.id("beam"));

	private MehburityDamage() {
	}

	public static DamageSource beam(ServerLevel level, MehburityEntity mehburity) {
		return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(BEAM)
				.map(type -> new DamageSource(type, mehburity))
				// A data pack that removed the type must not break combat.
				.orElseGet(() -> level.damageSources().mobAttack(mehburity));
	}
}
