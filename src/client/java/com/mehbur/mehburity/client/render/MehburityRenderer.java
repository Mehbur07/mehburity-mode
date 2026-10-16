package com.mehbur.mehburity.client.render;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws Mehburity as a real, smooth UV sphere (not cubes) wrapped in an equirectangular texture:
 * the centre of the texture is the face, pointing where Mehburity looks. It bobs gently while hovering.
 */
public class MehburityRenderer extends EntityRenderer<MehburityEntity, MehburityRenderState> {
	/** Placeholder art, 128x64 equirectangular. Replace the PNG to change the look. */
	private static final Identifier TEXTURE = MehburityMod.id("textures/entity/mehburity/mehburity.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
	private static final float RADIUS = 0.4F;
	private static final int STACKS = 16;
	private static final int SLICES = 24;
	/** Neon glow: Mehburity is drawn fully lit, so it shines even in Verity darkness. */
	private static final int MIN_BLOCK_LIGHT = 15;

	public MehburityRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.shadowRadius = 0.35F;
	}

	@Override
	public MehburityRenderState createRenderState() {
		return new MehburityRenderState();
	}

	@Override
	public void extractRenderState(MehburityEntity entity, MehburityRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
		state.xRot = entity.getXRot(partialTicks);
		state.emergeProgress = entity.getEmergeProgress(partialTicks);
	}

	@Override
	protected int getBlockLightLevel(MehburityEntity entity, BlockPos pos) {
		return Math.max(MIN_BLOCK_LIGHT, super.getBlockLightLevel(entity, pos));
	}

	@Override
	public void submit(MehburityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		float emerge = easeOutBack(state.emergeProgress);
		float bob = Mth.sin(state.ageInTicks * 0.1F) * 0.06F * state.emergeProgress;
		float scale = 0.25F + 0.75F * emerge;

		poseStack.pushPose();
		poseStack.translate(0.0F, state.boundingBoxHeight * 0.5F + bob, 0.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot * 0.5F));
		// Spins one full turn while rising out of the box.
		poseStack.mulPose(Axis.YP.rotationDegrees((1.0F - state.emergeProgress) * 360.0F));
		poseStack.scale(scale, scale, scale);
		int light = state.lightCoords;
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> buildSphere(pose, buffer, light));
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static float easeOutBack(float t) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float u = t - 1.0F;
		return 1.0F + c3 * u * u * u + c1 * u * u;
	}

	/** Latitude/longitude sphere. u runs around the equator (0.5 = front, +Z), v from top to bottom. */
	private static void buildSphere(PoseStack.Pose pose, VertexConsumer buffer, int light) {
		for (int stack = 0; stack < STACKS; stack++) {
			float v0 = (float) stack / STACKS;
			float v1 = (float) (stack + 1) / STACKS;
			for (int slice = 0; slice < SLICES; slice++) {
				float u0 = (float) slice / SLICES;
				float u1 = (float) (slice + 1) / SLICES;
				vertex(pose, buffer, light, u0, v0);
				vertex(pose, buffer, light, u0, v1);
				vertex(pose, buffer, light, u1, v1);
				vertex(pose, buffer, light, u1, v0);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float u, float v) {
		float theta = v * Mth.PI;
		float phi = (u - 0.5F) * Mth.TWO_PI;
		float nx = Mth.sin(theta) * Mth.sin(phi);
		float ny = Mth.cos(theta);
		float nz = Mth.sin(theta) * Mth.cos(phi);
		buffer.addVertex(pose, nx * RADIUS, ny * RADIUS, nz * RADIUS)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
