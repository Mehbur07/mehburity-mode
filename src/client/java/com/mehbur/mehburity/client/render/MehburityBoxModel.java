package com.mehbur.mehburity.client.render;

import com.mehbur.mehburity.MehburityMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A 12-pixel cardboard box with four top flaps. Opening: it shakes, the front/back flaps swing open,
 * then the side flaps follow.
 */
public class MehburityBoxModel extends EntityModel<MehburityBoxRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MehburityMod.id("box"), "main");
	private static final float MAX_FLAP_ANGLE = 2.2F;

	private final ModelPart box;
	private final ModelPart flapNorth;
	private final ModelPart flapSouth;
	private final ModelPart flapWest;
	private final ModelPart flapEast;

	public MehburityBoxModel(ModelPart root) {
		super(root);
		this.box = root.getChild("box");
		this.flapNorth = box.getChild("flap_north");
		this.flapSouth = box.getChild("flap_south");
		this.flapWest = box.getChild("flap_west");
		this.flapEast = box.getChild("flap_east");
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition box = root.addOrReplaceChild("box",
				CubeListBuilder.create().texOffs(0, 0).addBox(-6.0F, -12.0F, -6.0F, 12.0F, 12.0F, 12.0F),
				PartPose.offset(0.0F, 24.0F, 0.0F));
		// Flaps are flat planes hinged on the top edges, lying inwards when closed.
		box.addOrReplaceChild("flap_north",
				CubeListBuilder.create().texOffs(0, 24).addBox(-6.0F, 0.0F, 0.0F, 12.0F, 0.0F, 6.0F),
				PartPose.offset(0.0F, -12.01F, -6.0F));
		box.addOrReplaceChild("flap_south",
				CubeListBuilder.create().texOffs(0, 24).addBox(-6.0F, 0.0F, -6.0F, 12.0F, 0.0F, 6.0F),
				PartPose.offset(0.0F, -12.01F, 6.0F));
		box.addOrReplaceChild("flap_west",
				CubeListBuilder.create().texOffs(0, 32).addBox(0.0F, 0.0F, -6.0F, 6.0F, 0.0F, 12.0F),
				PartPose.offset(-6.0F, -12.02F, 0.0F));
		box.addOrReplaceChild("flap_east",
				CubeListBuilder.create().texOffs(0, 32).addBox(-6.0F, 0.0F, -6.0F, 6.0F, 0.0F, 12.0F),
				PartPose.offset(6.0F, -12.02F, 0.0F));
		return LayerDefinition.create(mesh, 64, 64);
	}

	@Override
	public void setupAnim(MehburityBoxRenderState state) {
		super.setupAnim(state);
		float t = state.openProgress;

		// 0.0-0.3: the box shakes as if something is inside.
		float shake = t > 0.0F && t < 0.3F ? Mth.sin(state.ageInTicks * 2.5F) * 0.12F * (1.0F - t / 0.3F) : 0.0F;
		box.zRot = shake;

		float frontBack = smooth(Mth.clamp((t - 0.25F) / 0.45F, 0.0F, 1.0F)) * MAX_FLAP_ANGLE;
		float sides = smooth(Mth.clamp((t - 0.45F) / 0.45F, 0.0F, 1.0F)) * MAX_FLAP_ANGLE;
		flapNorth.xRot = frontBack;
		flapSouth.xRot = -frontBack;
		flapWest.zRot = -sides;
		flapEast.zRot = sides;
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}
}
