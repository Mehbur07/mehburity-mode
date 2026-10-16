package com.mehbur.mehburity.network;

import com.mehbur.mehburity.MehburityMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Owner's client → server: Mehburity's answer to request {@code id}; {@code fromGemini} is false for built-in replies. */
public record AiReplyPayload(int id, String text, boolean fromGemini) implements CustomPacketPayload {
	public static final Type<AiReplyPayload> TYPE = new Type<>(MehburityMod.id("ai_reply"));
	public static final StreamCodec<ByteBuf, AiReplyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AiReplyPayload::id,
			ByteBufCodecs.stringUtf8(2048), AiReplyPayload::text,
			ByteBufCodecs.BOOL, AiReplyPayload::fromGemini,
			AiReplyPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
