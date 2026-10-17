package com.mehbur.mehburity.client.render;

import com.mehbur.mehburity.MehburityMod;
import com.mehbur.mehburity.entity.MehburityEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Mehburity as a real, smooth UV sphere (not cubes) wrapped in an equirectangular texture:
 * the centre of the texture is the face, pointing where Mehburity looks. It bobs gently while hovering.
 */
public class MehburityRenderer extends EntityRenderer<MehburityEntity, MehburityRenderState> {
	/** Placeholder art, 256x128 equirectangular. Replace the PNG to change the look. */
	private static final Identifier TEXTURE = MehburityMod.id("textures/entity/mehburity/mehburity.png");
	private static final RenderType RENDER_TYPE = RenderTypes.entityCutout(TEXTURE);
	/** The face during a rage. Replace the PNG (same size) to change it. */
	private static final Identifier ANGRY_TEXTURE = MehburityMod.id("textures/entity/mehburity/mehburity_angry.png");
	static final RenderType ANGRY_RENDER_TYPE = RenderTypes.entityCutout(ANGRY_TEXTURE);
	/** Expressions, same layout: a flat mouth when annoyed, a cross face when angry, wide eyes when scared. */
	private static final RenderType ANNOYED_RENDER_TYPE = RenderTypes.entityCutout(MehburityMod.id("textures/entity/mehburity/mehburity_annoyed.png"));
	static final RenderType MAD_RENDER_TYPE = RenderTypes.entityCutout(MehburityMod.id("textures/entity/mehburity/mehburity_mad.png"));
	private static final RenderType SCARED_RENDER_TYPE = RenderTypes.entityCutout(MehburityMod.id("textures/entity/mehburity/mehburity_scared.png"));
	/** From this anger on it looks annoyed (a third of the default rage threshold). */
	private static final float ANNOYED_ANGER = 33.0F;
	/** From this anger on, it looks cross and the sphere flashes red now and then as a warning. */
	private static final float WARNING_ANGER = 66.0F;
	static final float RADIUS = 0.4F;
	private static final int STACKS = 16;
	private static final int SLICES = 24;
	/** Neon glow: Mehburity is drawn fully lit, so it shines even in Verity darkness. */
	private static final int MIN_BLOCK_LIGHT = 15;
	/** Beam colours (ARGB): a near-white core inside a neon cyan glow. Drawn additively, so they shine in the dark. */
	private static final int BEAM_CORE = 0xE0FFFF;
	private static final int BEAM_GLOW = 0x00F0FF;
	private static final int RAGE_BEAM_CORE = 0xFFE0D0;
	private static final int RAGE_BEAM_GLOW = 0xFF1020;
	/** Share of the transformation spent swelling as a sphere before the demon starts to grow. */
	private static final float TRANSFORM_SPHERE_PART = 0.25F;
	private static final float MONSTER_SHADOW = 1.6F;

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
		state.anger = entity.getAnger();
		state.raging = entity.isRaging();
		state.scared = entity.isScared();
		state.monsterHeight = entity.getMonsterHeight();
		state.transformProgress = entity.getTransformProgress(partialTicks);
		state.swipeProgress = entity.getSwipeProgress(partialTicks);
		state.bodyRot = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
		state.walkPosition = entity.walkAnimation.position(partialTicks);
		state.walkSpeed = entity.walkAnimation.speed(partialTicks);
		LivingEntity target = entity.getBeamTarget();
		if (target != null) {
			state.beamTarget = lerpedCenter(target, partialTicks);
			state.beamCharge = entity.getBeamCharge(partialTicks);
			state.beamFlash = entity.getBeamFlash(partialTicks);
		} else {
			state.beamTarget = null;
		}
	}

	/** Keeps drawing the beam when Mehburity itself is off screen but the beam crosses the view. */
	@Override
	public boolean shouldRender(MehburityEntity entity, Frustum culler, double camX, double camY, double camZ) {
		if (super.shouldRender(entity, culler, camX, camY, camZ)) {
			return true;
		}
		LivingEntity target = entity.getBeamTarget();
		if (target == null) {
			return false;
		}
		Vec3 from = lerpedCenter(entity, 1.0F);
		Vec3 to = lerpedCenter(target, 1.0F);
		return culler.isVisible(new AABB(from.x, from.y, from.z, to.x, to.y, to.z));
	}

	private static Vec3 lerpedCenter(Entity entity, float partialTicks) {
		return new Vec3(Mth.lerp(partialTicks, entity.xOld, entity.getX()),
				Mth.lerp(partialTicks, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.5,
				Mth.lerp(partialTicks, entity.zOld, entity.getZ()));
	}

	@Override
	protected float getShadowRadius(MehburityRenderState state) {
		return state.monsterHeight > 0.0F ? MONSTER_SHADOW : super.getShadowRadius(state);
	}

	@Override
	protected int getBlockLightLevel(MehburityEntity entity, BlockPos pos) {
		return Math.max(MIN_BLOCK_LIGHT, super.getBlockLightLevel(entity, pos));
	}

	@Override
	public void submit(MehburityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.monsterHeight > 0.0F) {
			submitMonster(state, poseStack, collector);
			super.submit(state, poseStack, collector, camera);
			return;
		}
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
		// Very angry: a red flash every second, like a mob being hit.
		boolean flash = !state.raging && state.anger >= WARNING_ANGER && ((int) state.ageInTicks / 5) % 4 == 0;
		int overlay = OverlayTexture.pack(0.0F, flash);
		collector.submitCustomGeometry(poseStack, face(state), (pose, buffer) -> buildSphere(pose, buffer, light, overlay));
		poseStack.popPose();
		if (state.beamTarget != null) {
			submitBeam(state, poseStack, collector, bob);
		}
		super.submit(state, poseStack, collector, camera);
	}

	/** The face for its mood: anger wins over fear, fear over the usual smile. */
	private static RenderType face(MehburityRenderState state) {
		if (state.raging) {
			return ANGRY_RENDER_TYPE;
		}
		if (state.anger >= WARNING_ANGER) {
			return MAD_RENDER_TYPE;
		}
		if (state.anger >= ANNOYED_ANGER) {
			return ANNOYED_RENDER_TYPE;
		}
		return state.scared ? SCARED_RENDER_TYPE : RENDER_TYPE;
	}

	/**
	 * The rage form, a neon cyan demon. While transforming it trembles: first the angry sphere swells on the ground,
	 * then the demon grows out of it to its full height.
	 */
	private static void submitMonster(MehburityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
		float progress = state.transformProgress;
		poseStack.pushPose();
		if (progress < 1.0F) {
			float shake = 0.08F * (1.0F - progress);
			poseStack.translate(Mth.sin(state.ageInTicks * 3.1F) * shake, 0.0F, Mth.cos(state.ageInTicks * 2.7F) * shake);
		}
		if (progress < TRANSFORM_SPHERE_PART) {
			float swell = 1.0F + progress / TRANSFORM_SPHERE_PART;
			poseStack.translate(0.0F, RADIUS * swell, 0.0F);
			poseStack.mulPose(Axis.YP.rotationDegrees(-state.yRot));
			poseStack.scale(swell, swell, swell);
			int light = state.lightCoords;
			collector.submitCustomGeometry(poseStack, MAD_RENDER_TYPE,
					(pose, buffer) -> buildSphere(pose, buffer, light, OverlayTexture.NO_OVERLAY));
		} else {
			float grow = (progress - TRANSFORM_SPHERE_PART) / (1.0F - TRANSFORM_SPHERE_PART);
			grow = grow * grow * (3.0F - 2.0F * grow);
			float size = state.monsterHeight / MehburityMonsterModel.MODEL_HEIGHT;
			MehburityMonsterModel.submit(state, poseStack, collector, size * (0.1F + 0.9F * grow), MehburityMonsterModel.SKIN, MAD_RENDER_TYPE);
		}
		poseStack.popPose();
	}

	/**
	 * A square tube from Mehburity's centre to the target: thin and faint while charging, thick and bright for a
	 * moment when the shot lands. Uses the additive lightning render type, so it glows in Verity darkness.
	 */
	private static void submitBeam(MehburityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, float bob) {
		float startY = state.boundingBoxHeight * 0.5F + bob;
		Vec3 beam = state.beamTarget.subtract(state.x, state.y + startY, state.z);
		float length = (float) beam.length();
		if (length < 0.01F) {
			return;
		}
		beam = beam.scale(1.0 / length);
		float charge = state.beamCharge;
		float flash = state.beamFlash;
		float flicker = 0.85F + 0.15F * Mth.sin(state.ageInTicks * 2.3F);
		float coreRadius = 0.015F + 0.035F * charge + 0.07F * flash;
		float glowRadius = coreRadius * 2.6F + 0.12F * flash;
		float coreAlpha = Math.min(1.0F, (0.35F + 0.45F * charge + 0.4F * flash) * flicker);
		float glowAlpha = Math.min(1.0F, (0.12F + 0.18F * charge + 0.5F * flash) * flicker);

		poseStack.pushPose();
		poseStack.translate(0.0F, startY, 0.0F);
		// Same orientation trick as the guardian beam: turn +Y onto the beam direction.
		poseStack.mulPose(Axis.YP.rotation(Mth.HALF_PI - (float) Math.atan2(beam.z, beam.x)));
		poseStack.mulPose(Axis.XP.rotation((float) Math.acos(Mth.clamp(beam.y, -1.0, 1.0))));
		// Starts at the sphere's surface, not inside it.
		float start = RADIUS * 0.9F;
		int glow = state.raging ? RAGE_BEAM_GLOW : BEAM_GLOW;
		int core = state.raging ? RAGE_BEAM_CORE : BEAM_CORE;
		collector.submitCustomGeometry(poseStack, RenderTypes.lightning(), (pose, buffer) -> {
			tube(pose, buffer, start, length, glowRadius, glow, glowAlpha);
			tube(pose, buffer, start, length, coreRadius, core, coreAlpha);
		});
		poseStack.popPose();
	}

	/** Four quads along +Y, each emitted with both windings so the beam is visible from every side. */
	private static void tube(PoseStack.Pose pose, VertexConsumer buffer, float y0, float y1, float r, int rgb, float alpha) {
		int color = ((int) (alpha * 255.0F) << 24) | rgb;
		float[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
		for (int side = 0; side < 4; side++) {
			float[] a = corners[side];
			float[] b = corners[(side + 1) % 4];
			beamVertex(pose, buffer, a[0], y0, a[1], color);
			beamVertex(pose, buffer, b[0], y0, b[1], color);
			beamVertex(pose, buffer, b[0], y1, b[1], color);
			beamVertex(pose, buffer, a[0], y1, a[1], color);

			beamVertex(pose, buffer, a[0], y1, a[1], color);
			beamVertex(pose, buffer, b[0], y1, b[1], color);
			beamVertex(pose, buffer, b[0], y0, b[1], color);
			beamVertex(pose, buffer, a[0], y0, a[1], color);
		}
	}

	private static void beamVertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int color) {
		buffer.addVertex(pose, x, y, z).setColor(color);
	}

	private static float easeOutBack(float t) {
		float c1 = 1.70158F;
		float c3 = c1 + 1.0F;
		float u = t - 1.0F;
		return 1.0F + c3 * u * u * u + c1 * u * u;
	}

	/** Latitude/longitude sphere. u runs around the equator (0.5 = front, +Z), v from top to bottom. */
	static void buildSphere(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
		for (int stack = 0; stack < STACKS; stack++) {
			float v0 = (float) stack / STACKS;
			float v1 = (float) (stack + 1) / STACKS;
			for (int slice = 0; slice < SLICES; slice++) {
				float u0 = (float) slice / SLICES;
				float u1 = (float) (slice + 1) / SLICES;
				vertex(pose, buffer, light, overlay, u0, v0);
				vertex(pose, buffer, light, overlay, u0, v1);
				vertex(pose, buffer, light, overlay, u1, v1);
				vertex(pose, buffer, light, overlay, u1, v0);
			}
		}
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay, float u, float v) {
		float theta = v * Mth.PI;
		float phi = (u - 0.5F) * Mth.TWO_PI;
		float nx = Mth.sin(theta) * Mth.sin(phi);
		float ny = Mth.cos(theta);
		float nz = Mth.sin(theta) * Mth.cos(phi);
		buffer.addVertex(pose, nx * RADIUS, ny * RADIUS, nz * RADIUS)
				.setColor(-1)
				.setUv(u, v)
				.setOverlay(overlay)
				.setLight(light)
				.setNormal(pose, nx, ny, nz);
	}
}
