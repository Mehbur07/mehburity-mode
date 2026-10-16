package com.mehbur.mehburity.network;

import com.mehbur.mehburity.MehburityMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → owner's client: say this out loud with Mehburity's voice. A {@link Component} so translated lines are
 * spoken in the player's own language.
 */
public record SpeakPayload(Component text) implements CustomPacketPayload {
	public static final Type<SpeakPayload> TYPE = new Type<>(MehburityMod.id("speak"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SpeakPayload> CODEC =
			ComponentSerialization.TRUSTED_STREAM_CODEC.map(SpeakPayload::new, SpeakPayload::text);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
