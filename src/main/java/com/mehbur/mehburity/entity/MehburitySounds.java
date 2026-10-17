package com.mehbur.mehburity.entity;

import com.mehbur.mehburity.MehburityMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

/**
 * Mod sound events. {@code assets/mehburity/sounds.json} currently points them at vanilla sounds as
 * placeholders; drop real {@code .ogg} files in {@code assets/mehburity/sounds/} and update that file.
 */
public final class MehburitySounds {
	public static final SoundEvent BOX_SHAKE = register("box.shake");
	public static final SoundEvent BOX_OPEN = register("box.open");
	public static final SoundEvent EMERGE = register("mehburity.emerge");
	/** Played instead of a voice when there is no Gemini key or text-to-speech failed. */
	public static final SoundEvent SPEAK = register("mehburity.speak");
	public static final SoundEvent BEAM_CHARGE = register("mehburity.beam.charge");
	public static final SoundEvent BEAM_FIRE = register("mehburity.beam.fire");
	/** Mehburity flies into a rage. */
	public static final SoundEvent RAGE = register("mehburity.rage");

	private MehburitySounds() {
	}

	private static SoundEvent register(String name) {
		var id = MehburityMod.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/** Forces class loading during mod init. */
	public static void register() {
	}
}
