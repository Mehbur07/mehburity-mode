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
 * The rage form (and the watcher of the scripted scares): a tall, starved humanoid in the spirit of Verity's
 * monster. A gaunt human body with the ribs showing under the skin, a pinched waist, long thin limbs with bony knees
 * and elbows, arms hanging past the knees into long-fingered human hands, and a human-shaped head (broad skull,
 * hollow cheeks, narrow jaw) with hollow black eyes and a cheek-to-cheek grin of human teeth. It stoops a little,
 * cranes its head forward and jerks it now and then. Modelled 18 blocks tall with the feet at y = 0 and the face
 * towards +Z; other heights are the same model scaled.
 *
 * <p>Skin textures are square: the top half covers the limbs, the third quarter the torso (ribs, spine, collar
 * bones), the last quarter the hands and feet. Around every part u starts at +X and runs through the front (+Z) at
 * u = 0.25. The head takes an equirectangular texture like Mehburity's sphere (u = 0.5 is the front).
 */
final class MehburityMonsterModel {
	/** Rage form skin: neon cyan, ribs shading the chest. Replace the PNG (same layout) to change it. */
	static final RenderType SKIN = RenderTypes.entityCutout(MehburityMod.id("textures/entity/mehburity/mehburity_monster.png"));
	/** The height the model is built at. */
	static final float MODEL_HEIGHT = 18.0F;

	private static final float HIP_Y = 8.9F;
	private static final float HIP_X = 0.5F;
	private static final float THIGH = 4.1F;
	private static final float SHIN = 4.3F;
	private static final float UPPER_ARM = 4.3F;
	private static final float FOREARM = 4.1F;
	private static final float SHOULDER_X = 1.25F;
	/** Shoulder joints and the base of the neck, above the hips in the torso's own (stooped) frame. */
	private static final float SHOULDER_Y = 5.05F;
	private static final float NECK_Y = 5.75F;
	private static final float NECK = 1.05F;
	/** Half the head's height; its width and depth are set in {@link #headPoint}. */
	private static final float HEAD_HALF_HEIGHT = 1.15F;
	/** Leaning forward from the hips, in degrees. */
	private static final float STOOP = 11.0F;
	/** The neck juts forward out of the shoulders, in degrees. */
	private static final float NECK_LEAN = 24.0F;
	/** The head hangs a little to one side, in degrees. */
	private static final float HEAD_TILT = 6.0F;
	/** Every this many ticks the head jerks for {@link #TWITCH_TICKS}. */
	private static final float TWITCH_PERIOD = 53.0F;
	private static final float TWITCH_TICKS = 4.0F;

	/** Limbs (top half of the texture), torso (third quarter), hands and feet (last quarter). */
	private static final float LIMB_V0 = 0.0F;
	private static final float LIMB_V1 = 0.5F;
	private static final float TORSO_V0 = 0.5F;
	private static final float TORSO_V1 = 0.75F;
	private static final float HAND_V0 = 0.75F;
	private static final float HAND_V1 = 1.0F;
	/**
	 * The torso from below the hips to the neck, as rings {y, half width, half depth, forward shift}: hips, the
	 * pinched waist, the ribcage flaring out under it, the shoulders and the base of the neck.
	 */
	private static final float[][] TORSO = {
			{-0.8F, 0.02F, 0.02F, 0.0F},
			{-0.75F, 0.25F, 0.2F, 0.0F},
			{-0.45F, 0.72F, 0.46F, 0.0F},
			{0.0F, 0.86F, 0.52F, 0.0F},
			{0.55F, 0.76F, 0.46F, 0.02F},
			{1.25F, 0.55F, 0.36F, 0.04F},
			{1.7F, 0.53F, 0.36F, 0.05F},
			{2.3F, 0.84F, 0.56F, 0.1F},
			{3.0F, 1.0F, 0.68F, 0.1F},
			{3.8F, 1.06F, 0.7F, 0.06F},
			{4.5F, 1.08F, 0.64F, 0.02F},
			{5.0F, 1.16F, 0.54F, 0.0F},
			{5.35F, 0.98F, 0.46F, 0.0F},
			{5.6F, 0.55F, 0.38F, 0.04F},
			{5.85F, 0.33F, 0.31F, 0.08F},
	};
	private static final int SIDES = 14;
	private static final int BALL_STACKS = 7;
	private static final int HEAD_STACKS = 20;
	private static final int HEAD_SLICES = 28;

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
		float legSwing = Mth.sin(walk) * 0.45F * stride;
		float breathe = Mth.sin(state.ageInTicks * 0.06F);

		poseStack.pushPose();
		poseStack.mulPose(Axis.YP.rotationDegrees(-state.bodyRot));
		poseStack.scale(scale, scale, scale);

		for (int side = -1; side <= 1; side += 2) {
			leg(parts, side, side * legSwing, walk, stride);
		}

		poseStack.pushPose();
		poseStack.translate(0.0F, HIP_Y, 0.0F);
		poseStack.mulPose(Axis.XP.rotationDegrees(STOOP + breathe));
		parts.loft(TORSO, TORSO_V0, TORSO_V1);
		for (int side = -1; side <= 1; side += 2) {
			arm(state, parts, side, legSwing);
		}
		neckAndHead(state, parts, head);
		poseStack.popPose();

		poseStack.popPose();
	}

	/** Thigh, bony knee, shin and a long thin foot; the knee bends on the way forward. */
	private static void leg(Parts parts, int side, float swing, float walk, float stride) {
		PoseStack poseStack = parts.poseStack;
		float knee = 0.08F + Math.max(0.0F, -Mth.sin(walk + side * Mth.HALF_PI)) * 0.6F * stride;
		float hip = swing - knee * 0.5F;
		poseStack.pushPose();
		poseStack.translate(side * HIP_X, HIP_Y - 0.15F, 0.0F);
		poseStack.mulPose(Axis.XP.rotation(hip));
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 2.0F));
		parts.ball(0.52F, LIMB_V0, LIMB_V1);
		parts.segment(0.55F, 0.3F, THIGH, LIMB_V0, LIMB_V1);
		poseStack.translate(0.0F, -THIGH, 0.0F);
		// The kneecap pokes out in front.
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.0F, 0.08F);
		poseStack.scale(1.0F, 1.25F, 1.0F);
		parts.ball(0.33F, LIMB_V0, LIMB_V1);
		poseStack.popPose();
		poseStack.mulPose(Axis.XP.rotation(knee));
		parts.segment(0.33F, 0.19F, SHIN, LIMB_V0, LIMB_V1);
		poseStack.translate(0.0F, -SHIN, 0.0F);
		parts.ball(0.21F, LIMB_V0, LIMB_V1);
		// Keep the foot flat on the ground whatever the leg does.
		poseStack.mulPose(Axis.XP.rotation(-hip - knee));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-side * 2.0F));
		poseStack.pushPose();
		poseStack.translate(0.0F, -0.32F, 0.55F);
		poseStack.scale(0.3F, 0.16F, 0.95F);
		parts.ball(1.0F, HAND_V0, HAND_V1);
		poseStack.popPose();
		for (int toe = -2; toe <= 2; toe++) {
			poseStack.pushPose();
			poseStack.translate(toe * 0.11F, -0.4F, 1.35F - Math.abs(toe) * 0.1F);
			poseStack.mulPose(Axis.YP.rotationDegrees(toe * 6.0F));
			poseStack.mulPose(Axis.XP.rotationDegrees(-100.0F));
			parts.segment(0.065F, 0.03F, 0.42F - Math.abs(toe) * 0.05F, HAND_V0, HAND_V1);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/**
	 * A long thin arm hanging from the shoulder past the knee, swinging against the legs, with a bony elbow and a
	 * long-fingered hand. The right arm (side +1) does the claw swipe: raised high, then slammed down in front.
	 */
	private static void arm(MehburityRenderState state, Parts parts, int side, float legSwing) {
		PoseStack poseStack = parts.poseStack;
		float swing = -side * legSwing * 0.7F;
		if (side > 0 && state.swipeProgress < 1.0F) {
			float s = state.swipeProgress;
			float raised = -2.8F;
			float slammed = -0.5F;
			swing = s < 0.4F ? Mth.lerp(s / 0.4F, 0.0F, raised)
					: s < 0.7F ? Mth.lerp((s - 0.4F) / 0.3F, raised, slammed)
					: Mth.lerp((s - 0.7F) / 0.3F, slammed, 0.0F);
		}
		poseStack.pushPose();
		poseStack.translate(side * SHOULDER_X, SHOULDER_Y, 0.0F);
		parts.ball(0.36F, LIMB_V0, LIMB_V1);
		// Undo the stoop so the arms hang straight down, a little away from the body.
		poseStack.mulPose(Axis.XP.rotationDegrees(-STOOP));
		poseStack.mulPose(Axis.XP.rotation(swing));
		poseStack.mulPose(Axis.ZP.rotationDegrees(side * 5.0F));
		parts.segment(0.38F, 0.26F, UPPER_ARM, LIMB_V0, LIMB_V1);
		poseStack.translate(0.0F, -UPPER_ARM, 0.0F);
		parts.ball(0.29F, LIMB_V0, LIMB_V1);
		poseStack.mulPose(Axis.XP.rotationDegrees(-14.0F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(-side * 4.0F));
		parts.segment(0.28F, 0.17F, FOREARM, LIMB_V0, LIMB_V1);
		poseStack.translate(0.0F, -FOREARM, 0.0F);
		parts.ball(0.19F, LIMB_V0, LIMB_V1);
		hand(parts, side);
		poseStack.popPose();
	}

	/** A bony palm facing the leg, four long jointed fingers curling in, and a thumb towards the front. */
	private static void hand(Parts parts, int side) {
		PoseStack poseStack = parts.poseStack;
		poseStack.pushPose();
		poseStack.translate(0.0F, -0.48F, 0.0F);
		poseStack.scale(0.13F, 0.55F, 0.36F);
		parts.ball(1.0F, HAND_V0, HAND_V1);
		poseStack.popPose();
		float[] lengths = {1.05F, 1.25F, 1.3F, 1.2F};
		for (int finger = 0; finger < 4; finger++) {
			float spread = finger - 1.5F;
			poseStack.pushPose();
			poseStack.translate(0.0F, -0.98F, -spread * 0.17F);
			poseStack.mulPose(Axis.XP.rotationDegrees(spread * 6.0F));
			finger(parts, side, lengths[finger], 0.075F);
			poseStack.popPose();
		}
		poseStack.pushPose();
		poseStack.translate(-side * 0.04F, -0.45F, 0.32F);
		poseStack.mulPose(Axis.XP.rotationDegrees(-28.0F));
		finger(parts, side, 0.95F, 0.085F);
		poseStack.popPose();
	}

	/** Three bones with knuckles, each bent a little more towards the palm (inwards, -X for the right hand). */
	private static void finger(Parts parts, int side, float length, float radius) {
		PoseStack poseStack = parts.poseStack;
		float[] share = {0.42F, 0.33F, 0.25F};
		float r = radius;
		for (int bone = 0; bone < 3; bone++) {
			float bend = 9.0F + bone * 9.0F;
			poseStack.mulPose(Axis.ZP.rotationDegrees(-side * bend));
			parts.ball(r * 1.15F, HAND_V0, HAND_V1);
			float tip = bone == 2 ? 0.006F : r * 0.82F;
			parts.segment(r, tip, length * share[bone], HAND_V0, HAND_V1);
			poseStack.translate(0.0F, -length * share[bone], 0.0F);
			r = tip;
		}
	}

	/**
	 * The neck craning forward out of the shoulders, and the head: level, looking where the body faces, hanging a
	 * little to one side and jerking sideways now and then.
	 */
	private static void neckAndHead(MehburityRenderState state, Parts parts, RenderType head) {
		PoseStack poseStack = parts.poseStack;
		poseStack.pushPose();
		poseStack.translate(0.0F, NECK_Y, 0.08F);
		poseStack.mulPose(Axis.XP.rotationDegrees(NECK_LEAN));
		// Segments hang down -Y: turned upside down, this one grows up out of the shoulders.
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
		parts.segment(0.33F, 0.27F, NECK, LIMB_V0, LIMB_V1);
		poseStack.translate(0.0F, -NECK, 0.0F);
		poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
		// Undo the stoop and the neck's lean so the face looks level, then turn it like the head.
		poseStack.mulPose(Axis.XP.rotationDegrees(-STOOP - NECK_LEAN));
		poseStack.translate(0.0F, HEAD_HALF_HEIGHT * 0.8F, 0.15F);
		// The whole body turns to whom it hunts, so the face stays straight ahead (the synced head yaw lags behind).
		float twitch = twitch(state.ageInTicks);
		poseStack.mulPose(Axis.YP.rotationDegrees(twitch * 14.0F));
		poseStack.mulPose(Axis.XP.rotationDegrees(state.xRot * 0.6F));
		poseStack.mulPose(Axis.ZP.rotationDegrees(HEAD_TILT + twitch * 26.0F));
		int light = parts.light;
		parts.collector.submitCustomGeometry(poseStack, head, (pose, buffer) -> buildHead(pose, buffer, light));
		poseStack.popPose();
	}

	/** 0 most of the time; a quick 0 → 1 → 0 jerk for a few ticks every {@link #TWITCH_PERIOD} ticks. */
	private static float twitch(float ageInTicks) {
		float phase = ageInTicks % TWITCH_PERIOD;
		return phase < TWITCH_TICKS ? Mth.sin(phase / TWITCH_TICKS * Mth.PI) : 0.0F;
	}

	// ---------------------------------------------------------------- head

	/** The head as a reshaped latitude/longitude sphere, so it takes the same textures as Mehburity's ball. */
	private static void buildHead(PoseStack.Pose pose, VertexConsumer buffer, int light) {
		for (int stack = 0; stack < HEAD_STACKS; stack++) {
			float v0 = (float) stack / HEAD_STACKS;
			float v1 = (float) (stack + 1) / HEAD_STACKS;
			for (int slice = 0; slice < HEAD_SLICES; slice++) {
				float u0 = (float) slice / HEAD_SLICES;
				float u1 = (float) (slice + 1) / HEAD_SLICES;
				headVertex(pose, buffer, light, u0, v0);
				headVertex(pose, buffer, light, u0, v1);
				headVertex(pose, buffer, light, u1, v1);
				headVertex(pose, buffer, light, u1, v0);
			}
		}
	}

	private static void headVertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float u, float v) {
		float[] p = headPoint(u, v);
		// Normal from the neighbouring points along both directions of the surface.
		float e = 0.004F;
		float[] du0 = headPoint(u - e, v);
		float[] du1 = headPoint(u + e, v);
		float[] dv0 = headPoint(u, Math.max(0.0F, v - e));
		float[] dv1 = headPoint(u, Math.min(1.0F, v + e));
		float ax = du1[0] - du0[0], ay = du1[1] - du0[1], az = du1[2] - du0[2];
		float bx = dv1[0] - dv0[0], by = dv1[1] - dv0[1], bz = dv1[2] - dv0[2];
		float nx = by * az - bz * ay;
		float ny = bz * ax - bx * az;
		float nz = bx * ay - by * ax;
		float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
		if (length < 1.0E-6F) {
			nx = 0.0F;
			ny = v < 0.5F ? 1.0F : -1.0F;
			nz = 0.0F;
		} else {
			nx /= length;
			ny /= length;
			nz /= length;
		}
		buffer.addVertex(pose, p[0], p[1], p[2])
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(OverlayTexture.NO_OVERLAY)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}

	/**
	 * A point of the head for sphere coordinates (u = 0.5 is the front, v = 0 the top): a broad skull, temples and
	 * cheeks that cave in, a jaw that narrows to a pointed chin pushed forward.
	 */
	private static float[] headPoint(float u, float v) {
		float theta = v * Mth.PI;
		float phi = (u - 0.5F) * Mth.TWO_PI;
		float sx = Mth.sin(theta) * Mth.sin(phi);
		float sy = Mth.cos(theta);
		float sz = Mth.sin(theta) * Mth.cos(phi);
		float jaw = smoothstep(0.15F, -0.9F, sy);
		float front = Math.max(0.0F, sz);
		float width = 0.86F - 0.36F * jaw;
		float cheek = (float) Math.exp(-((sy + 0.2F) / 0.25F) * ((sy + 0.2F) / 0.25F)) * front;
		width *= 1.0F - 0.13F * cheek;
		float depth = sz > 0.0F ? 0.93F - 0.12F * jaw : 1.05F - 0.25F * jaw;
		float height = sy > 0.0F ? 1.0F : 0.96F;
		float chin = jaw * jaw * front * 0.22F;
		return new float[] {sx * width, sy * height * HEAD_HALF_HEIGHT, sz * depth + chin};
	}

	private static float smoothstep(float edge0, float edge1, float x) {
		float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	// ---------------------------------------------------------------- parts

	/** Submits each part with the pose current at the time of the call. */
	private record Parts(PoseStack poseStack, SubmitNodeCollector collector, RenderType skin, int light) {
		/** A round segment hanging down from the origin: radius {@code r0} at the top, {@code r1} at the bottom. */
		void segment(float r0, float r1, float length, float v0, float v1) {
			loft(new float[][] {{-length, r1, r1, 0.0F}, {0.0F, r0, r0, 0.0F}}, v1, v0);
		}

		/**
		 * A smooth body through elliptical rings {y, half width, half depth, forward shift}, listed bottom to top.
		 * The texture runs from {@code vBottom} at the first ring to {@code vTop} at the last.
		 */
		void loft(float[][] rings, float vBottom, float vTop) {
			collector.submitCustomGeometry(poseStack, skin, (pose, buffer) -> {
				float y0 = rings[0][0];
				float span = rings[rings.length - 1][0] - y0;
				for (int ring = 0; ring < rings.length - 1; ring++) {
					float[] a = rings[ring];
					float[] b = rings[ring + 1];
					float va = Mth.lerp((a[0] - y0) / span, vBottom, vTop);
					float vb = Mth.lerp((b[0] - y0) / span, vBottom, vTop);
					// The side leans in or out with the radius; the normals follow that slope.
					float slope = ((a[1] + a[2]) - (b[1] + b[2])) * 0.5F / (b[0] - a[0]);
					for (int i = 0; i < SIDES; i++) {
						float a0 = Mth.TWO_PI * i / SIDES;
						float a1 = Mth.TWO_PI * (i + 1) / SIDES;
						float u0 = (float) i / SIDES;
						float u1 = (float) (i + 1) / SIDES;
						ringVertex(pose, buffer, b, a0, u0, vb, slope);
						ringVertex(pose, buffer, a, a0, u0, va, slope);
						ringVertex(pose, buffer, a, a1, u1, va, slope);
						ringVertex(pose, buffer, b, a1, u1, vb, slope);
					}
				}
			});
		}

		private void ringVertex(PoseStack.Pose pose, VertexConsumer buffer, float[] ring, float angle, float u, float v, float slope) {
			float cos = Mth.cos(angle);
			float sin = Mth.sin(angle);
			float rx = ring[1];
			float rz = ring[2];
			// Outward normal of an ellipse, tilted up or down by the slope.
			float nx = cos * rz;
			float nz = sin * rx;
			float flat = Mth.sqrt(nx * nx + nz * nz);
			nx /= flat;
			nz /= flat;
			float length = Mth.sqrt(1.0F + slope * slope);
			vertex(pose, buffer, cos * rx, ring[0], sin * rz + ring[3], u, v, nx / length, slope / length, nz / length);
		}

		/** A small sphere at the origin (joints, the palm, the feet). */
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
