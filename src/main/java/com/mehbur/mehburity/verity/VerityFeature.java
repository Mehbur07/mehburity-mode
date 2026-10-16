package com.mehbur.mehburity.verity;

/**
 * One feature adapted from Verity. Add new ones by implementing this and listing them in {@link VerityFeatures}.
 * Each feature reads its own on/off switch from the {@code verity} section of the config at runtime,
 * so {@code /mehburity yenile} can turn it on or off without a restart.
 */
public interface VerityFeature {
	/** Short id used in logs, e.g. {@code "darkness"}. */
	String id();

	/** Registers events and payloads. Called once during mod initialization, on both client and server. */
	void register();
}
