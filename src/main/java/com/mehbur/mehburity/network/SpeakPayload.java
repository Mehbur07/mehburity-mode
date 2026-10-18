package com.mehbur.mehburity.network;

import com.mehbur.mehburity.MehburityMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * Server → a player's client: say this out loud with Mehburity's voice. A {@link Component} so translated lines are
 * spoken in the player's own language. {@code speakerId} is the Mehburity that talks ({@link #NO_SPEAKER} when it is
 * not out in the world, e.g. carried) and {@code from} where it stood, in case the client does not know that entity
 * yet: the voice gets quieter the farther away it is.
 */
public record SpeakPayload(Component text, int speakerId, Optional<Vec3> from) implements CustomPacketPayload {
	public static final int NO_SPEAKER = -1;
	public static final Type<SpeakPayload> TYPE = new Type<>(MehburityMod.id("speak"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SpeakPayload> CODEC = StreamCodec.composite(
			ComponentSerialization.TRUSTED_STREAM_CODEC, SpeakPayload::text,
			ByteBufCodecs.VAR_INT, SpeakPayload::speakerId,
			ByteBufCodecs.optional(Vec3.STREAM_CODEC), SpeakPayload::from,
			SpeakPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
