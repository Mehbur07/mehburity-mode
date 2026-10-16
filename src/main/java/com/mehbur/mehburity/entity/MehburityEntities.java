package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.MehburityMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class MehburityEntities {
	/** 0.8-block floating sphere. {@code noSummon}: Mehburity only comes out of its box, one per player. */
	public static final EntityType<MehburityEntity> MEHBURITY = register("mehburity",
			EntityType.Builder.of(MehburityEntity::new, MobCategory.MISC)
					.sized(0.8F, 0.8F)
					.eyeHeight(0.45F)
					.fireImmune()
					.noSummon()
					.clientTrackingRange(10)
					.updateInterval(2));

	public static final EntityType<MehburityBoxEntity> BOX = register("box",
			EntityType.Builder.of(MehburityBoxEntity::new, MobCategory.MISC)
					.sized(0.75F, 0.75F)
					.fireImmune()
					.noSummon()
					.clientTrackingRange(10)
					.updateInterval(5));

	private MehburityEntities() {
	}

	private static <T extends net.minecraft.world.entity.Entity> EntityType<T> register(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MehburityMod.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	public static void register() {
		FabricDefaultAttributeRegistry.register(MEHBURITY, MehburityEntity.createAttributes());
	}
}
