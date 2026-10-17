package com.mehbur.mehburity.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

public class MehburityRenderState extends EntityRenderState {
	/** Where the face points (head yaw/pitch), in degrees. */
	public float yRot;
	public float xRot;
	/** 0 → just started rising out of the box, 1 → fully out. */
	public float emergeProgress = 1.0F;
	/** Middle of the beam's target, in world coordinates; null when Mehburity is not firing. */
	public @Nullable Vec3 beamTarget;
	/** 0..1 while charging. */
	public float beamCharge;
	/** 1 right after a shot, fading to 0. */
	public float beamFlash;
	/** Temper 0-100, and whether it is in a rage (red face, red beam). */
	public float anger;
	public boolean raging;
	/** Rage form: its height (0 = a sphere), 0..1 while it grows, and the claw swipe (1 = none). */
	public float monsterHeight;
	public float transformProgress = 1.0F;
	public float swipeProgress = 1.0F;
	/** Body yaw in degrees and the walk cycle, for the demon's legs and arms. */
	public float bodyRot;
	public float walkPosition;
	public float walkSpeed;
}
