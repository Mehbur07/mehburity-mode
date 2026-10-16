package com.mehbur.mehburity.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers every custom payload type. Must run on both sides before any packet is sent. */
public final class MehburityNetwork {
	private MehburityNetwork() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DarknessSettingsPayload.TYPE, DarknessSettingsPayload.CODEC);
	}
}
