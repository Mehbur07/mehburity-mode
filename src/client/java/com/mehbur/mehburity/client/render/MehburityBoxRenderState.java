package com.mehbur.mehburity.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class MehburityBoxRenderState extends EntityRenderState {
	public float yRot;
	/** 0 = closed, 1 = fully open. */
	public float openProgress;
}
