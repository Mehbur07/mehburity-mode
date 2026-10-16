package com.mehbur.mehburity.verity;

import com.mehbur.mehburity.MehburityMod;

import java.util.List;

/** All Verity features in registration order. Later phases add the personality, scripted events and more. */
public final class VerityFeatures {
	public static final List<VerityFeature> ALL = List.of(
			new BoxFeature(),
			new DarknessFeature());

	private VerityFeatures() {
	}

	public static void registerAll() {
		for (VerityFeature feature : ALL) {
			feature.register();
			MehburityMod.LOGGER.debug("Registered Verity feature '{}'", feature.id());
		}
	}
}
