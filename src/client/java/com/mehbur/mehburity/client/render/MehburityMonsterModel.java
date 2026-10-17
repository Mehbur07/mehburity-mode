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
 * The rage form (and the watcher of the scripted scares): a tall, skinny demon. Its limbs are smooth, tapering
 * round segments joined by ball joints; it has a hunched chest, spikes down its back, long arms with three curved
 * claws per hand, digitigrade feet and curved horns, with a sphere for a head. Modelled 18 blocks tall with the feet
 * at y = 0 and the face towards +Z; other heights are the same model scaled.
 *
 * <p>Skin textures are 64x64: the top half covers the body, the bottom half the horns, claws and spikes.
 */
final class MehburityMonsterModel {
	/** Rage form skin: neon cyan body, deep teal horns and claws. Replace the PNG (same size) to change it. */
	static final RenderType SKIN = RenderTypes.entityCutout(MehburityMod.id("textures/entity/mehburity/mehburity_monster.png"));
	/** The height the model is built at. */
	static final float MODEL_HEIGHT = 18.0F;

	private static final float HIP_Y = 8.6F;
	private static final float THIGH = 4.3F;
	private static final float SHIN = 4.2F;
	private static final float UPPER_ARM = 4.0F;
	private static final float FOREARM = 3.8F;
	private static final float CHEST_TOP = 5.2F;
	private static final float WAIST = 2.0F;
	/** Front-to-back flattening of the torso. */
	private static final float TORSO_DEPTH = 0.62F;
	private static final float NECK = 1.3F;
	private static final float HEAD_RADIUS = 1.2F;
	/** Leaning forward, in degrees. */
	private static final float HUNCH = 14.0F;
	private static final float NECK_TILT = 15.0F;

	/** Body (top half of the texture) and horns/claws (bottom half). */
	private static final float BODY_V0 = 0.0F;
	private static final float BODY_V1 = 0.5F;
	private static final float HORN_V0 = 0.5F;
	private static final float HORN_V1 = 1.0F;
	private static final int SIDES = 10;
	private static final int BALL_STACKS = 6;

	private MehburityMonsterModel() {
	}

	/**
	 * Draws the demon at {@code scale} (1 = 18 blocks tall), in the entity's local space (feet at the origin), with
	 * {@code skin} on its body and {@code head} as the sphere's texture.
	 */
	static void submit(MehburityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, float scale,
			RenderType skin, RenderType head) {
		Parts parts = new Parts(poseStack, collector, skin, state.lightCoords);
		float walk = state.walkPosition * 0.25F;
		float stride = Math.min(1.0F, state.walkSpeed);
		float legSwing = Mth.sin(walk) * 0.5F * stride;
		float breathe = Mth.sin(state.ageInTicks * 0.06F);

		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.bodyRot));
		poseStack.scale(scale, scale, scale);

		for (int side = -1; side <= 1; side += 2) {
			leg(parts, side, side * legSwing, walk, stride);
		}
		poseStack.pushPose();
		poseStack.translate(0.0F, HIP_Y, 0.0F);
		poseStack.scale(1.5F, 0.8F, 1.0F);
		parts.ball(0.42F, BODY_V0, BODY_V1);
		poseStack.popPose();

		// Torso, hunched forward from the hips: a narrow waist under a wide, flat chest.
		poseStack.pushPose();
		poseStack.translate(0.0F, HIP_Y + 0.1F, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(HUNCH + breathe * 1.5F));
		poseStack.pushPose();
		poseStack.scale(1.0F, 1.0F, 0.7F);
		poseStack.translate(0.0F, WAIST, 0.0F);
		parts.segment(0.42F, 0.5F, WAIST, BODY_V0, BODY_V1);
		poseStack.popPose();
		poseStack.pushPose();
		poseStack.scale(1.0F, 1.0F, TORSO_DEPTH);
		poseStack.translate(0.0F, CHEST_TOP, 0.0F);
		parts.segment(0.95F, 0.42F, CHEST_TOP - WAIST, BODY_V0, BODY_V1);
		poseStack.scale(1.0F, 0.3F, 1.0F);
		parts.ball(0.95F, BODY_V0, BODY_V1);
		poseStack.popPose();
		backSpikes(parts);
		for (int side = -1; side <= 1; side += 2) {
			arm(state, parts, side, legSwing);
		}
		neckAndHead(state, parts, head);
		poseStack.popPose();

		poseStack.popPose();
	}

	/** Thigh, knee, shin bending backwards on the way forward, and a long clawed foot. */
	private static void leg(Parts parts, int side, float swing, float walk, float stride) {
		PoseStack poseStack = parts.poseStack;
		float knee = 0.15F + Math.max(0.0F, -Mth.sin(walk + side * Mth.HALF_PI)) * 0.6F * stride;
		float hip = swing - knee * 0.5F;
		poseStack.pushPose();
		poseStack.translate(side * 0.45F, HIP_Y, 0.0F);
		poseStack.mulPose(Axis.XP.rotation(hip));
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 3.0F));
		parts.ball(0.33F, BODY_V0, BODY_V1);
		parts.segment(0.32F, 0.2F, THIGH, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -THIGH, 0.0F);
		parts.ball(0.21F, BODY_V0, BODY_V1);
		poseStack.mulPose(Axis.XP.rotation(knee));
		parts.segment(0.2F, 0.11F, SHIN, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -SHIN, 0.0F);
		parts.ball(0.13F, BODY_V0, BODY_V1);
		// Keep the foot flat on the ground whatever the leg does, then point it forward.
		poseStack.mulPose(Axis.XP.rotation(-hip - knee));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-side * 3.0F));
		for (int toe = -1; toe <= 1; toe++) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(toe * 22.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
			parts.segment(0.1F, 0.05F, 0.75F, BODY_V0, BODY_V1);
			poseStack.translate(0.0F, -0.75F, 0.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-35.0F));
			parts.segment(0.05F, 0.005F, 0.35F, HORN_V0, HORN_V1);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** Four spikes down the back, pointing up and back. */
	private static void backSpikes(Parts parts) {
		PoseStack poseStack = parts.poseStack;
		for (int i = 0; i < 4; i++) {
			float y = 2.3F + i * 0.8F;
			float depth = (0.42F + (0.95F - 0.42F) * (y - WAIST) / (CHEST_TOP - WAIST)) * TORSO_DEPTH;
			poseStack.pushPose();
			poseStack.translate(0.0F, y, -depth * 0.85F);
			poseStack.mulPose(Axis.XP.rotationDegrees(120.0F));
			parts.segment(0.13F, 0.01F, 0.55F + i * 0.1F, HORN_V0, HORN_V1);
			poseStack.popPose();
		}
	}

	/**
	 * A long arm hanging from the shoulder, swinging against the legs, ending in three curved claws. The right arm
	 * (side +1) does the claw swipe: raised high, then slammed down in front.
	 */
	private static void arm(MehburityRenderState state, Parts parts, int side, float legSwing) {
		PoseStack poseStack = parts.poseStack;
		float swing = -side * legSwing * 0.6F;
		if (side > 0 && state.swipeProgress < 1.0F) {
			float s = state.swipeProgress;
			float raised = -2.8F;
			float slammed = -0.5F;
			swing = s < 0.4F ? Mth.lerp(s / 0.4F, 0.0F, raised)
					: s < 0.7F ? Mth.lerp((s - 0.4F) / 0.3F, raised, slammed)
					: Mth.lerp((s - 0.7F) / 0.3F, slammed, 0.0F);
		}
		poseStack.pushPose();
		poseStack.translate(side * 1.08F, CHEST_TOP - 0.35F, 0.0F);
		parts.ball(0.32F, BODY_V0, BODY_V1);
		// Undo most of the hunch so the arms hang down instead of reaching forward.
		poseStack.mulPose(Axis.XP.rotationDegrees(-HUNCH * 0.7F));
		poseStack.mulPose(Axis.XP.rotation(swing));
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 8.0F));
		parts.segment(0.22F, 0.15F, UPPER_ARM, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -UPPER_ARM, 0.0F);
		parts.ball(0.17F, BODY_V0, BODY_V1);
		poseStack.mulPose(Axis.XP.rotationDegrees(-20.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-side * 6.0F));
		parts.segment(0.15F, 0.1F, FOREARM, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -FOREARM, 0.0F);
		parts.ball(0.13F, BODY_V0, BODY_V1);
		poseStack.pushPose();
		poseStack.translate(0.0F, -0.18F, 0.0F);
		poseStack.scale(1.0F, 1.0F, 0.55F);
		parts.ball(0.27F, BODY_V0, BODY_V1);
		poseStack.popPose();
		for (int finger = -1; finger <= 1; finger++) {
			poseStack.pushPose();
			poseStack.translate(finger * 0.15F, -0.32F, 0.04F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-12.0F));
			poseStack.mulPose(Axis.ZP.rotationDegrees(finger * 9.0F));
			parts.segment(0.065F, 0.045F, 0.55F, BODY_V0, BODY_V1);
			poseStack.translate(0.0F, -0.55F, 0.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-28.0F));
			parts.segment(0.045F, 0.005F, 0.75F, HORN_V0, HORN_V1);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** The neck leaning forward out of the chest, and the head turned towards whatever it looks at. */
	private static void neckAndHead(MehburityRenderState state, Parts parts, RenderType head) {
		PoseStack poseStack = parts.poseStack;
		poseStack.pushPose();
		poseStack.translate(0.0F, CHEST_TOP + 0.1F, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(NECK_TILT));
		// Segments hang down -Y: turned upside down, this one grows up out of the chest.
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
		parts.segment(0.36F, 0.26F, NECK, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -NECK, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
		poseStack.translate(0.0F, HEAD_RADIUS * 0.8F, 0.0F);
		// Undo the hunch and the neck's tilt so the face looks level, then turn it like the head.
		poseStack.mulPose(Axis.XP.rotationDegrees(-HUNCH - NECK_TILT));
		float headYaw = Mth.clamp(Mth.wrapDegrees(state.yRot - state.bodyRot), -70.0F, 70.0F);
		poseStack.mulPose(Axis.YP.rotationDegrees(-headYaw));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot * 0.6F));
		for (int side = -1; side <= 1; side += 2) {
			horn(parts, side);
		}
		poseStack.scale(HEAD_RADIUS / MehburityRenderer.RADIUS, HEAD_RADIUS / MehburityRenderer.RADIUS, HEAD_RADIUS / MehburityRenderer.RADIUS);
		int light = parts.light;
		parts.collector.submitCustomGeometry(poseStack, head,
				(pose, buffer) -> MehburityRenderer.buildSphere(pose, buffer, light, OverlayTexture.NO_OVERLAY));
		poseStack.popPose();
	}

	/** A horn rising from the top of the head and curving back, its tip flaring out, in three tapering pieces. */
	private static void horn(Parts parts, int side) {
		PoseStack poseStack = parts.poseStack;
		poseStack.pushPose();
		poseStack.translate(side * HEAD_RADIUS * 0.55F, HEAD_RADIUS * 0.7F, -0.1F);
		// Pointing up and a little outwards (a segment hangs down -Y, so about 160 degrees around Z).
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 160.0F));
		parts.segment(0.26F, 0.18F, 0.8F, HORN_V0, HORN_V1);
		poseStack.translate(0.0F, -0.8F, 0.0F);
		// Positive X turns the tip backwards.
		poseStack.mulPose(Axis.XP.rotationDegrees(30.0F));
		parts.segment(0.18F, 0.1F, 0.7F, HORN_V0, HORN_V1);
		poseStack.translate(0.0F, -0.7F, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(35.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-side * 15.0F));
		parts.segment(0.1F, 0.005F, 0.7F, HORN_V0, HORN_V1);
		poseStack.popPose();
	}

	/** Submits each part with the pose current at the time of the call. */
	private record Parts(PoseStack poseStack, SubmitNodeCollector collector, RenderType skin, int light) {
		/** A round segment hanging down from the origin: radius {@code r0} at the top, {@code r1} at the bottom. */
		void segment(float r0, float r1, float length, float v0, float v1) {
			collector.submitCustomGeometry(poseStack, skin, (pose, buffer) -> {
				// The side leans in as the segment tapers; the normals follow that slope.
				float slope = (r0 - r1) / length;
				for (int i = 0; i < SIDES; i++) {
					float a0 = Mth.TWO_PI * i / SIDES;
					float a1 = Mth.TWO_PI * (i + 1) / SIDES;
					float u0 = (float) i / SIDES;
					float u1 = (float) (i + 1) / SIDES;
					ring(pose, buffer, a0, r0, 0.0F, u0, v0, slope);
					ring(pose, buffer, a0, r1, -length, u0, v1, slope);
					ring(pose, buffer, a1, r1, -length, u1, v1, slope);
					ring(pose, buffer, a1, r0, 0.0F, u1, v0, slope);
				}
			});
		}

		private void ring(PoseStack.Pose pose, VertexConsumer buffer, float angle, float r, float y, float u, float v, float slope) {
			float cos = Mth.cos(angle);
			float sin = Mth.sin(angle);
			float length = Mth.sqrt(1.0F + slope * slope);
			vertex(pose, buffer, cos * r, y, sin * r, u, v, cos / length, slope / length, sin / length);
		}

		/** A small sphere at the origin (joints, caps, the hand). */
		void ball(float r, float v0, float v1) {
			collector.submitCustomGeometry(poseStack, skin, (pose, buffer) -> {
				for (int stack = 0; stack < BALL_STACKS; stack++) {
					float t0 = Mth.PI * stack / BALL_STACKS;
					float t1 = Mth.PI * (stack + 1) / BALL_STACKS;
					for (int i = 0; i < SIDES; i++) {
						float p0 = Mth.TWO_PI * i / SIDES;
						float p1 = Mth.TWO_PI * (i + 1) / SIDES;
						ballVertex(pose, buffer, r, t0, p0, v0, v1);
						ballVertex(pose, buffer, r, t1, p0, v0, v1);
						ballVertex(pose, buffer, r, t1, p1, v0, v1);
						ballVertex(pose, buffer, r, t0, p1, v0, v1);
					}
				}
			});
		}

		private void ballVertex(PoseStack.Pose pose, VertexConsumer buffer, float r, float theta, float phi, float v0, float v1) {
			float nx = Mth.sin(theta) * Mth.cos(phi);
			float ny = Mth.cos(theta);
			float nz = Mth.sin(theta) * Mth.sin(phi);
			vertex(pose, buffer, nx * r, ny * r, nz * r, phi / Mth.TWO_PI, Mth.lerp(theta / Mth.PI, v0, v1), nx, ny, nz);
		}

		private void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
				float nx, float ny, float nz) {
			buffer.addVertex(pose, x, y, z)
					.setColor(-1)
					.setUv(u, v)
					.setOverlay(OverlayTexture.NO_OVERLAY)
					.setLight(light)
					.setNormal(pose, nx, ny, nz);
		}
	}
}
