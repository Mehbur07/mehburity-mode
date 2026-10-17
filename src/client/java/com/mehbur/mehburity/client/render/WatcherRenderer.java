package com.mehbur.mehburity.client.render;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.verity.WatcherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;

/**
 * The demon of the scripted scares: the same body as Mehburity's rage form, standing still, with a dark sphere for a
 * head that wears Mehburity's smile in glowing white. Drawn fully lit, so its veins and face show in the dark.
 */
public class WatcherRenderer extends EntityRenderer<WatcherEntity, MehburityRenderState> {
	/** 128x64 equirectangular like Mehburity's sphere. Replace the PNG (same size) to change it. */
	private static final RenderType HEAD = RenderTypes.entityCutout(MehburityMod.id("textures/entity/mehburity/watcher.png"));
	private static final int FULL_LIGHT = 15;

	public WatcherRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.0F; // nothing there, really
	}

	@Override
	public MehburityRenderState createRenderState() {
		return new MehburityRenderState();
	}

	@Override
	public void extractRenderState(WatcherEntity entity, MehburityRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.monsterHeight = entity.getBbHeight();
		state.bodyRot = entity.getYRot(partialTicks);
		state.yRot = state.bodyRot;
		state.xRot = 0.0F;
		state.walkPosition = 0.0F;
		state.walkSpeed = 0.0F;
		state.swipeProgress = 1.0F;
	}

	@Override
	protected int getBlockLightLevel(WatcherEntity entity, BlockPos pos) {
		return FULL_LIGHT;
	}

	@Override
	public void submit(MehburityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		MehburityMonsterModel.submit(state, poseStack, collector, state.monsterHeight / MehburityMonsterModel.MODEL_HEIGHT, HEAD);
		super.submit(state, poseStack, collector, camera);
	}
}
