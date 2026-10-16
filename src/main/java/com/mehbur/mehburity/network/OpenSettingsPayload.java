package com.mehbur.mehburity.network;

import com.mehbur.mehburity.MehburityMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: open the Mehburity settings screen ({@code /mehburity ayarlar}). The command lives on the server
 * because a client-side {@code /mehburity} command would hide the server's other subcommands.
 */
public record OpenSettingsPayload() implements CustomPacketPayload {
	public static final OpenSettingsPayload INSTANCE = new OpenSettingsPayload();
	public static final Type<OpenSettingsPayload> TYPE = new Type<>(MehburityMod.id("open_settings"));
	public static final StreamCodec<ByteBuf, OpenSettingsPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
