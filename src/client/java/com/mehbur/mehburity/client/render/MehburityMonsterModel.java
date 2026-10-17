package com.mehbur.mehburity.client.render;

import com.mehbur.mehburity.MehburityMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * The rage form: a tall, skinny demon built from boxes, with Mehburity's red, angry sphere as its head. Modelled
 * 18 blocks tall with the feet at y = 0 and the face towards +Z; other heights are the same model scaled.
 * Hunched, with long legs, arms hanging down to its knees, three claws per hand and two horns.
 */
final class MehburityMonsterModel {
	/** Placeholder skin, 64x64: dark with glowing red veins. Replace the PNG (same size) to change it. */
	private static final Identifier TEXTURE = MehburityMod.id("textures/entity/mehburity/mehburity_monster.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
	/** The height the model is built at. */
	static final float MODEL_HEIGHT = 18.0F;

	private static final float HIP_Y = 8.7F;
	private static final float THIGH = 4.4F;
	private static final float SHIN = 4.3F;
	private static final float UPPER_ARM = 4.2F;
	private static final float FOREARM = 4.0F;
	private static final float CLAW = 1.1F;
	private static final float HEAD_RADIUS = 1.25F;
	/** Leaning forward, in degrees. */
	private static final float HUNCH = 14.0F;

	private MehburityMonsterModel() {
	}

	/** Draws the demon at {@code scale} (1 = 18 blocks tall), in the entity's local space (feet at the origin). */
	static void submit(MehburityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, float scale) {
		int light = state.lightCoords;
		float walk = state.walkPosition * 0.25F;
		float stride = Math.min(1.0F, state.walkSpeed);
		float legSwing = Mth.sin(walk) * 0.5F * stride;
		float breathe = Mth.sin(state.ageInTicks * 0.06F);

		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.bodyRot));
		poseStack.scale(scale, scale, scale);

		// Legs: thigh, shin bending backwards on the way forward, and a long foot.
		for (int side = -1; side <= 1; side += 2) {
			float swing = side * legSwing;
			float knee = 0.15F + Math.max(0.0F, -Mth.sin(walk + side * Mth.HALF_PI)) * 0.6F * stride;
			poseStack.pushPose();
			poseStack.translate(side * 0.45F, HIP_Y, 0.0F);
			poseStack.mulPose(Axis.XP.rotation(swing - knee * 0.5F));
			part(poseStack, collector, light, -0.22F, -THIGH, -0.22F, 0.22F, 0.0F, 0.22F);
			poseStack.translate(0.0F, -THIGH, 0.0F);
			poseStack.mulPose(Axis.XP.rotation(knee));
			part(poseStack, collector, light, -0.17F, -SHIN, -0.17F, 0.17F, 0.0F, 0.17F);
			poseStack.translate(0.0F, -SHIN, 0.0F);
			poseStack.mulPose(Axis.XP.rotation(-swing + knee * -0.5F));
			part(poseStack, collector, light, -0.25F, -0.15F, -0.25F, 0.25F, 0.15F, 0.8F);
			poseStack.popPose();
		}
		part(poseStack, collector, light, -0.6F, HIP_Y - 0.3F, -0.3F, 0.6F, HIP_Y + 0.3F, 0.3F);

		// Torso, hunched forward from the hips.
		poseStack.pushPose();
		poseStack.translate(0.0F, HIP_Y + 0.2F, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(HUNCH + breathe * 1.5F));
		part(poseStack, collector, light, -0.4F, 0.0F, -0.25F, 0.4F, 2.4F, 0.25F);
		part(poseStack, collector, light, -0.85F, 2.4F, -0.4F, 0.85F, 5.0F, 0.4F);
		part(poseStack, collector, light, -1.2F, 4.6F, -0.3F, 1.2F, 5.1F, 0.3F);

		for (int side = -1; side <= 1; side += 2) {
			arm(state, poseStack, collector, light, side, legSwing);
		}

		// Neck and head: the angry sphere, turned towards whatever it looks at.
		poseStack.pushPose();
		poseStack.translate(0.0F, 5.0F, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(10.0F));
		part(poseStack, collector, light, -0.15F, 0.0F, -0.15F, 0.15F, 1.3F, 0.15F);
		poseStack.translate(0.0F, 1.2F + HEAD_RADIUS * 0.8F, 0.0F);
		// Undo the hunch so the face looks level, then turn it like the head.
		poseStack.mulPose(Axis.XP.rotationDegrees(-HUNCH - 10.0F));
		float headYaw = Mth.clamp(Mth.wrapDegrees(state.yRot - state.bodyRot), -70.0F, 70.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-headYaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot * 0.6F));
		for (int side = -1; side <= 1; side += 2) {
			poseStack.pushPose();
			poseStack.translate(side * 0.65F, HEAD_RADIUS * 0.7F, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(-side * 25.0F));
			part(poseStack, collector, light, -0.12F, 0.0F, -0.12F, 0.12F, 1.2F, 0.12F);
			poseStack.translate(0.0F, 1.2F, 0.0F);
			poseStack.mulPose(Axis.ZP.rotationDegrees(side * 35.0F));
			part(poseStack, collector, light, -0.07F, 0.0F, -0.07F, 0.07F, 0.7F, 0.07F);
			poseStack.popPose();
		}
		poseStack.scale(HEAD_RADIUS / MehburityRenderer.RADIUS, HEAD_RADIUS / MehburityRenderer.RADIUS, HEAD_RADIUS / MehburityRenderer.RADIUS);
		collector.submitCustomGeometry(poseStack, MehburityRenderer.ANGRY_RENDER_TYPE,
				(pose, buffer) -> MehburityRenderer.buildSphere(pose, buffer, light, OverlayTexture.NO_OVERLAY));
		poseStack.popPose();

		poseStack.popPose();
		poseStack.popPose();
	}

	/**
	 * A long arm hanging from the shoulder, swinging against the legs. The right arm (side +1) does the claw swipe:
	 * raised high, then slammed down.
	 */
	private static void arm(MehburityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int light,
			int side, float legSwing) {
		float swing = -side * legSwing * 0.6F;
		if (side > 0 && state.swipeProgress < 1.0F) {
			float s = state.swipeProgress;
			float raised = -2.8F;
			float slammed = -0.5F; // down in front of it
			swing = s < 0.4F ? Mth.lerp(s / 0.4F, 0.0F, raised)
					: s < 0.7F ? Mth.lerp((s - 0.4F) / 0.3F, raised, slammed)
					: Mth.lerp((s - 0.7F) / 0.3F, slammed, 0.0F);
		}
		poseStack.pushPose();
		poseStack.translate(side * 1.1F, 4.85F, 0.0F);
		// Undo part of the hunch so the arms hang down instead of reaching forward.
		poseStack.mulPose(Axis.XP.rotationDegrees(-HUNCH * 0.7F));
		poseStack.mulPose(Axis.XP.rotation(swing));
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 6.0F));
		part(poseStack, collector, light, -0.17F, -UPPER_ARM, -0.17F, 0.17F, 0.0F, 0.17F);
		poseStack.translate(0.0F, -UPPER_ARM, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(-18.0F));
		part(poseStack, collector, light, -0.14F, -FOREARM, -0.14F, 0.14F, 0.0F, 0.14F);
		poseStack.translate(0.0F, -FOREARM, 0.0F);
		part(poseStack, collector, light, -0.2F, -0.3F, -0.12F, 0.2F, 0.05F, 0.12F);
		for (int finger = -1; finger <= 1; finger++) {
			poseStack.pushPose();
			poseStack.translate(finger * 0.13F, -0.25F, 0.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-25.0F));
			poseStack.mulPose(Axis.ZP.rotationDegrees(finger * 8.0F));
			part(poseStack, collector, light, -0.04F, -CLAW, -0.04F, 0.04F, 0.0F, 0.04F);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	private static void part(PoseStack poseStack, SubmitNodeCollector collector, int light,
			float x0, float y0, float z0, float x1, float y1, float z1) {
		collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, buffer) -> box(pose, buffer, light, x0, y0, z0, x1, y1, z1));
	}

	/** A box with the whole texture on each face (the render type does not cull, so the winding does not matter). */
	private static void box(PoseStack.Pose pose, VertexConsumer buffer, int light,
			float x0, float y0, float z0, float x1, float y1, float z1) {
		face(pose, buffer, light, -1, 0, 0, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
		face(pose, buffer, light, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
		face(pose, buffer, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		face(pose, buffer, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		face(pose, buffer, light, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		face(pose, buffer, light, 0, 0, 1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1);
	}

	private static void face(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz,
			float ax, float ay, float az, float bx, float by, float bz,
			float cx, float cy, float cz, float dx, float dy, float dz) {
		vertex(pose, buffer, light, nx, ny, nz, ax, ay, az, 0.0F, 1.0F);
		vertex(pose, buffer, light, nx, ny, nz, bx, by, bz, 1.0F, 1.0F);
		vertex(pose, buffer, light, nx, ny, nz, cx, cy, cz, 1.0F, 0.0F);
		vertex(pose, buffer, light, nx, ny, nz, dx, dy, dz, 0.0F, 0.0F);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz,
			float x, float y, float z, float u, float v) {
		buffer.addVertex(pose, x, y, z)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
