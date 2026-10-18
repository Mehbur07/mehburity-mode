package com.mehbur.mehburity.client.render;

import com.mehbur.mehburity.MehburityMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

/**
 * The rage form (and the watcher of the scripted scares): a tall, starved, skeletal humanoid. Thin limbs made of
 * smooth tapering round segments with ball joints, a narrow ribcage over a wasp waist, a deep hunch, arms that hang
 * down past the knees and end in long bony fingers, and an egg-shaped head with hollow black eyes and a crooked grin
 * that it tilts to one side and jerks now and then. Modelled 18 blocks tall with the feet at y = 0 and the face
 * towards +Z; other heights are the same model scaled.
 *
 * <p>Skin textures are 64x64: the top half covers the limbs, the third quarter the fingers and toes, the last quarter
 * the ribcage.
 */
final class MehburityMonsterModel {
	/** Rage form skin: neon cyan limbs, deep teal fingertips, a ribbed chest. Replace the PNG (same size) to change it. */
	static final RenderType SKIN = RenderTypes.entityCutout(MehburityMod.id("textures/entity/mehburity/mehburity_monster.png"));
	/** The height the model is built at. */
	static final float MODEL_HEIGHT = 18.0F;

	private static final float HIP_Y = 8.4F;
	private static final float THIGH = 4.3F;
	private static final float SHIN = 4.3F;
	private static final float UPPER_ARM = 4.7F;
	private static final float FOREARM = 4.7F;
	private static final float CHEST_TOP = 5.4F;
	private static final float WAIST = 2.0F;
	/** Front-to-back flattening of the torso. */
	private static final float TORSO_DEPTH = 0.6F;
	private static final float SHOULDER_X = 0.95F;
	private static final float NECK = 1.7F;
	private static final float HEAD_RADIUS = 1.15F;
	/** The head is an egg: this much taller than wide. */
	private static final float HEAD_STRETCH = 1.2F;
	/** Leaning forward, in degrees. */
	private static final float HUNCH = 24.0F;
	private static final float NECK_TILT = 22.0F;
	/** The head hangs to one side, in degrees. */
	private static final float HEAD_TILT = 11.0F;
	/** Every this many ticks the head jerks for {@link #TWITCH_TICKS}. */
	private static final float TWITCH_PERIOD = 53.0F;
	private static final float TWITCH_TICKS = 4.0F;

	/** Limbs (top half of the texture), fingers and toes (third quarter), ribcage (last quarter). */
	private static final float BODY_V0 = 0.0F;
	private static final float BODY_V1 = 0.5F;
	private static final float CLAW_V0 = 0.5F;
	private static final float CLAW_V1 = 0.75F;
	private static final float RIBS_V0 = 0.75F;
	private static final float RIBS_V1 = 1.0F;
	private static final int SIDES = 10;
	private static final int BALL_STACKS = 6;

	private MehburityMonsterModel() {
	}

	/**
	 * Draws the demon at {@code scale} (1 = 18 blocks tall), in the entity's local space (feet at the origin), with
	 * {@code skin} on its body and {@code head} as the head's texture.
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
		poseStack.scale(1.4F, 0.75F, 1.0F);
		parts.ball(0.34F, BODY_V0, BODY_V1);
		poseStack.popPose();

		// Torso, hunched forward from the hips: a wasp waist under a narrow, ribbed chest.
		poseStack.pushPose();
		poseStack.translate(0.0F, HIP_Y + 0.1F, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(HUNCH + breathe * 1.5F));
		poseStack.pushPose();
		poseStack.scale(1.0F, 1.0F, 0.75F);
		poseStack.translate(0.0F, WAIST, 0.0F);
		parts.segment(0.26F, 0.32F, WAIST, BODY_V0, BODY_V1);
		poseStack.popPose();
		poseStack.pushPose();
		poseStack.scale(1.0F, 1.0F, TORSO_DEPTH);
		poseStack.translate(0.0F, CHEST_TOP, 0.0F);
		parts.segment(0.8F, 0.26F, CHEST_TOP - WAIST, RIBS_V0, RIBS_V1);
		poseStack.scale(1.0F, 0.3F, 1.0F);
		parts.ball(0.8F, BODY_V0, BODY_V1);
		poseStack.popPose();
		spine(parts);
		for (int side = -1; side <= 1; side += 2) {
			arm(state, parts, side, legSwing);
		}
		neckAndHead(state, parts, head);
		poseStack.popPose();

		poseStack.popPose();
	}

	/** Bony thigh, knee, shin bending backwards on the way forward, and a long-toed foot. */
	private static void leg(Parts parts, int side, float swing, float walk, float stride) {
		PoseStack poseStack = parts.poseStack;
		float knee = 0.2F + Math.max(0.0F, -Mth.sin(walk + side * Mth.HALF_PI)) * 0.6F * stride;
		float hip = swing - knee * 0.5F;
		poseStack.pushPose();
		poseStack.translate(side * 0.42F, HIP_Y, 0.0F);
		poseStack.mulPose(Axis.XP.rotation(hip));
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 3.0F));
		parts.ball(0.25F, BODY_V0, BODY_V1);
		parts.segment(0.23F, 0.13F, THIGH, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -THIGH, 0.0F);
		parts.ball(0.17F, BODY_V0, BODY_V1);
		poseStack.mulPose(Axis.XP.rotation(knee));
		parts.segment(0.14F, 0.08F, SHIN, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -SHIN, 0.0F);
		parts.ball(0.11F, BODY_V0, BODY_V1);
		// Keep the foot flat on the ground whatever the leg does, then point it forward.
		poseStack.mulPose(Axis.XP.rotation(-hip - knee));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-side * 3.0F));
		for (int toe = -1; toe <= 1; toe++) {
			poseStack.pushPose();
			poseStack.mulPose(Axis.YP.rotationDegrees(toe * 20.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
			parts.segment(0.07F, 0.04F, 0.85F, BODY_V0, BODY_V1);
			poseStack.translate(0.0F, -0.85F, 0.0F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-30.0F));
			parts.segment(0.04F, 0.004F, 0.45F, CLAW_V0, CLAW_V1);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** Knobs of the spine showing through the skin of the hunched back. */
	private static void spine(Parts parts) {
		PoseStack poseStack = parts.poseStack;
		for (int i = 0; i < 6; i++) {
			float y = 0.6F + i * 0.75F;
			float radius = y < WAIST ? Mth.lerp(y / WAIST, 0.26F, 0.32F)
					: Mth.lerp((y - WAIST) / (CHEST_TOP - WAIST), 0.26F, 0.8F);
			poseStack.pushPose();
			poseStack.translate(0.0F, y, -radius * (y < WAIST ? 0.75F : TORSO_DEPTH) * 0.92F);
			parts.ball(0.12F, BODY_V0, BODY_V1);
			poseStack.popPose();
		}
	}

	/**
	 * A very long arm hanging from the shoulder past the knee, swinging against the legs, ending in four long bony
	 * fingers. The right arm (side +1) does the claw swipe: raised high, then slammed down in front.
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
		poseStack.translate(side * SHOULDER_X, CHEST_TOP - 0.3F, 0.0F);
		parts.ball(0.24F, BODY_V0, BODY_V1);
		// Undo most of the hunch so the arms hang down instead of reaching forward.
		poseStack.mulPose(Axis.XP.rotationDegrees(-HUNCH * 0.75F));
		poseStack.mulPose(Axis.XP.rotation(swing));
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 6.0F));
		parts.segment(0.15F, 0.1F, UPPER_ARM, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -UPPER_ARM, 0.0F);
		parts.ball(0.13F, BODY_V0, BODY_V1);
		poseStack.mulPose(Axis.XP.rotationDegrees(-16.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-side * 5.0F));
		parts.segment(0.11F, 0.07F, FOREARM, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -FOREARM, 0.0F);
		parts.ball(0.09F, BODY_V0, BODY_V1);
		poseStack.pushPose();
		poseStack.translate(0.0F, -0.15F, 0.0F);
		poseStack.scale(1.0F, 1.2F, 0.5F);
		parts.ball(0.2F, BODY_V0, BODY_V1);
		poseStack.popPose();
		for (int finger = 0; finger < 4; finger++) {
			float spread = finger - 1.5F;
			poseStack.pushPose();
			poseStack.translate(spread * 0.1F, -0.3F, 0.03F);
			poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F - Math.abs(spread) * 4.0F));
			poseStack.mulPose(Axis.ZP.rotationDegrees(spread * 7.0F));
			parts.segment(0.045F, 0.035F, 0.8F, BODY_V0, BODY_V1);
			poseStack.translate(0.0F, -0.8F, 0.0F);
			parts.ball(0.04F, BODY_V0, BODY_V1);
			poseStack.mulPose(Axis.XP.rotationDegrees(-22.0F));
			parts.segment(0.035F, 0.004F, 1.0F, CLAW_V0, CLAW_V1);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/**
	 * The neck jutting forward out of the chest, and the egg-shaped head: hanging to one side, turned towards
	 * whatever it looks at, and jerking sideways now and then.
	 */
	private static void neckAndHead(MehburityRenderState state, Parts parts, RenderType head) {
		PoseStack poseStack = parts.poseStack;
		poseStack.pushPose();
		poseStack.translate(0.0F, CHEST_TOP + 0.1F, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(NECK_TILT));
		// Segments hang down -Y: turned upside down, this one grows up out of the chest.
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
		parts.segment(0.22F, 0.16F, NECK, BODY_V0, BODY_V1);
		poseStack.translate(0.0F, -NECK, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
		poseStack.translate(0.0F, HEAD_RADIUS * HEAD_STRETCH * 0.75F, 0.0F);
		// Undo the hunch and the neck's tilt so the face looks level, then turn it like the head.
		poseStack.mulPose(Axis.XP.rotationDegrees(-HUNCH - NECK_TILT));
		float headYaw = Mth.clamp(Mth.wrapDegrees(state.yRot - state.bodyRot), -70.0F, 70.0F);
		float twitch = twitch(state.ageInTicks);
		poseStack.mulPose(Axis.YP.rotationDegrees(-headYaw + twitch * 14.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot * 0.6F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(HEAD_TILT + twitch * 28.0F));
		float radius = HEAD_RADIUS / MehburityRenderer.RADIUS;
		poseStack.scale(radius, radius * HEAD_STRETCH, radius);
		int light = parts.light;
		parts.collector.submitCustomGeometry(poseStack, head,
				(pose, buffer) -> MehburityRenderer.buildSphere(pose, buffer, light, OverlayTexture.NO_OVERLAY));
		poseStack.popPose();
	}

	/** 0 most of the time; a quick 0 → 1 → 0 jerk for a few ticks every {@link #TWITCH_PERIOD} ticks. */
	private static float twitch(float ageInTicks) {
		float phase = ageInTicks % TWITCH_PERIOD;
		return phase < TWITCH_TICKS ? Mth.sin(phase / TWITCH_TICKS * Mth.PI) : 0.0F;
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
