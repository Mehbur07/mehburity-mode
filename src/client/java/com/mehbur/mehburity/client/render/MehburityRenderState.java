package com.mehbur.mehburity.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class MehburityRenderState extends EntityRenderState {
	/** Where the face points (head yaw/pitch), in degrees. */
	public float yRot;
	public float xRot;
	/** 0 → just started rising out of the box, 1 → fully out. */
	public float emergeProgress = 1.0F;
}
