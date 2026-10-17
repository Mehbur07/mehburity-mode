package com.mehbur.mehburity.verity;

import com.mehbur.mehburity.MehburityMod;

import java.util.List;

/** All Verity features in registration order: box, darkness, flashlight and scripted scares. */
public final class VerityFeatures {
	public static final List<VerityFeature> ALL = List.of(
			new BoxFeature(),
			new DarknessFeature(),
			new FlashlightFeature(),
			new EventsFeature());

	private VerityFeatures() {
	}

	public static void registerAll() {
		for (VerityFeature feature : ALL) {
			feature.register();
			MehburityMod.LOGGER.debug("Registered Verity feature '{}'", feature.id());
		}
	}
}
