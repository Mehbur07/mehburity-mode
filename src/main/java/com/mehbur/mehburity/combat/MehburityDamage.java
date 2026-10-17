package com.mehbur.mehburity.combat;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.entity.MehburityEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * Mehburity's damage types, defined in {@code data/mehburity/damage_type/}. Their own types give death messages
 * like "Zombie was zapped by Mehburity" (beam) or "Steve was torn apart by Mehburity" (the monster's claws).
 */
public final class MehburityDamage {
	public static final ResourceKey<DamageType> BEAM = ResourceKey.create(Registries.DAMAGE_TYPE, MehburityMod.id("beam"));
	public static final ResourceKey<DamageType> CLAW = ResourceKey.create(Registries.DAMAGE_TYPE, MehburityMod.id("claw"));

	private MehburityDamage() {
	}

	public static DamageSource beam(ServerLevel level, MehburityEntity mehburity) {
		return source(level, mehburity, BEAM);
	}

	public static DamageSource claw(ServerLevel level, MehburityEntity mehburity) {
		return source(level, mehburity, CLAW);
	}

	private static DamageSource source(ServerLevel level, MehburityEntity mehburity, ResourceKey<DamageType> key) {
		return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).get(key)
				.map(type -> new DamageSource(type, mehburity))
				// A data pack that removed the type must not break combat.
				.orElseGet(() -> level.damageSources().mobAttack(mehburity));
	}
}
