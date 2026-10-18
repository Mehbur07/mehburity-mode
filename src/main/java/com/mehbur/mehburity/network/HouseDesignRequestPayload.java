package com.mehbur.mehburity.network;

import com.mehbur.mehburity.MehburityMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → owner's client: "ask Gemini how to build this house". The client answers with
 * {@link HouseDesignReplyPayload} carrying the same {@code id} (an empty design when it has no key).
 */
public record HouseDesignRequestPayload(int id, String description) implements CustomPacketPayload {
	public static final Type<HouseDesignRequestPayload> TYPE = new Type<>(MehburityMod.id("house_design_request"));
	public static final StreamCodec<ByteBuf, HouseDesignRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HouseDesignRequestPayload::id,
			ByteBufCodecs.stringUtf8(2048), HouseDesignRequestPayload::description,
			HouseDesignRequestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
