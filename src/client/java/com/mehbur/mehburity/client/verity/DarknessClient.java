package com.mehbur.mehburity.client.verity;

import com.mehbur.mehburity.network.DarknessSettingsPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.renderer.state.LightmapRenderState;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

/**
 * Applies the server's Verity darkness to the lightmap. Torches and other block light keep working;
 * what goes away is the brightness option, ambient light and most of the moonlight.
 */
public final class DarknessClient {
	private static volatile boolean enabled;
	private static volatile float strength;

	private DarknessClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(DarknessSettingsPayload.TYPE, (payload, context) -> {
			enabled = payload.enabled();
			strength = Mth.clamp(payload.strength(), 0.0F, 1.0F);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> enabled = false);
	}

	public static boolean isEnabled() {
		return enabled && strength > 0.0F;
	}

	public static float strength() {
		return strength;
	}

	/** Darkens the values the lightmap shader is about to use. */
	public static void apply(LightmapRenderState state) {
		if (!isEnabled()) {
			return;
		}
		float s = strength;
		// The brightness option ("Moody".."Bright") no longer lifts the shadows.
		state.brightness *= 1.0F - s;
		state.ambientColor = new Vector3f(state.ambientColor).mul(1.0F - s);
		// Daylight (factor 1) stays; night light (~0.2) is pushed close to zero.
		state.skyFactor = (float) Math.pow(Mth.clamp(state.skyFactor, 0.0F, 1.0F), 1.0F + 3.0F * s);
	}
}
