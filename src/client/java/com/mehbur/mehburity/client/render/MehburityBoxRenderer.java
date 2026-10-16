package com.mehbur.mehburity.client.render;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.entity.MehburityBoxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

public class MehburityBoxRenderer extends EntityRenderer<MehburityBoxEntity, MehburityBoxRenderState> {
	/** Placeholder cardboard art (64x64). */
	private static final Identifier TEXTURE = MehburityMod.id("textures/entity/mehburity/box.png");
	private final MehburityBoxModel model;

	public MehburityBoxRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new MehburityBoxModel(context.bakeLayer(MehburityBoxModel.LAYER));
		this.shadowRadius = 0.45F;
	}

	@Override
	public MehburityBoxRenderState createRenderState() {
		return new MehburityBoxRenderState();
	}

	@Override
	public void extractRenderState(MehburityBoxEntity entity, MehburityBoxRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot();
		state.openProgress = entity.getOpenProgress(partialTicks);
	}

	@Override
	public void submit(MehburityBoxRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yRot));
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.translate(0.0F, -1.501F, 0.0F);
		collector.submitModel(model, state, poseStack, TEXTURE, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}
}
