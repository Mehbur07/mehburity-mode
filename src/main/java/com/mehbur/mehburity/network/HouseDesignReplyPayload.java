package com.mehbur.mehburity.network;

import com.mehbur.mehburity.MehburityMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Owner's client → server: Gemini's house design (JSON) for {@link HouseDesignRequestPayload} {@code id}; "" if none. */
public record HouseDesignReplyPayload(int id, String design) implements CustomPacketPayload {
	public static final Type<HouseDesignReplyPayload> TYPE = new Type<>(MehburityMod.id("house_design_reply"));
	public static final StreamCodec<ByteBuf, HouseDesignReplyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HouseDesignReplyPayload::id,
			ByteBufCodecs.stringUtf8(8192), HouseDesignReplyPayload::design,
			HouseDesignReplyPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
