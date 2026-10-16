package com.mehbur.mehburity.client.mixin;

import com.mehbur.mehburity.client.verity.DarknessClient;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapRenderStateExtractor.class)
abstract class LightmapRenderStateExtractorMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void mehburity$applyDarkness(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
		if (renderState.needsUpdate) {
			DarknessClient.apply(renderState);
		}
	}
}
