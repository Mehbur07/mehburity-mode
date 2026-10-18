package com.mehbur.mehburity.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/** Registers every custom payload type. Must run on both sides before any packet is sent. */
public final class MehburityNetwork {
	private MehburityNetwork() {
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(DarknessSettingsPayload.TYPE, DarknessSettingsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AiRequestPayload.TYPE, AiRequestPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(SpeakPayload.TYPE, SpeakPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenSettingsPayload.TYPE, OpenSettingsPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(HouseDesignRequestPayload.TYPE, HouseDesignRequestPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AiReplyPayload.TYPE, AiReplyPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(HouseDesignReplyPayload.TYPE, HouseDesignReplyPayload.CODEC);
	}
}
