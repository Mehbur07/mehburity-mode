package com.mehbur.mehburity.network;

import com.mehbur.mehburity.MehburityMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: the server's darkness settings, so every player sees the same darkness. */
public record DarknessSettingsPayload(boolean enabled, float strength) implements CustomPacketPayload {
	public static final Type<DarknessSettingsPayload> TYPE = new Type<>(MehburityMod.id("darkness_settings"));
	public static final StreamCodec<ByteBuf, DarknessSettingsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, DarknessSettingsPayload::enabled,
			ByteBufCodecs.FLOAT, DarknessSettingsPayload::strength,
			DarknessSettingsPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
