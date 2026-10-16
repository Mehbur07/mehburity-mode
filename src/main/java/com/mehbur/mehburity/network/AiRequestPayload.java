package com.mehbur.mehburity.network;

import com.mehbur.mehburity.MehburityMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → owner's client: "ask Gemini this". {@code event} marks a game event Mehburity should react to on its own
 * instead of a chat question. The client answers with {@link AiReplyPayload} carrying the same {@code id}.
 */
public record AiRequestPayload(int id, boolean event, String message, String context) implements CustomPacketPayload {
	public static final Type<AiRequestPayload> TYPE = new Type<>(MehburityMod.id("ai_request"));
	public static final StreamCodec<ByteBuf, AiRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AiRequestPayload::id,
			ByteBufCodecs.BOOL, AiRequestPayload::event,
			ByteBufCodecs.stringUtf8(2048), AiRequestPayload::message,
			ByteBufCodecs.stringUtf8(8192), AiRequestPayload::context,
			AiRequestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
