package local.bodaddon.client;

import local.bodaddon.eel.EelEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A long, thin body in four hinged segments (head, two body pieces, tail) that ripple in a wave as it swims.
 * The texture layout is drawn by {@code tools/gen_eel.py}, which holds the same box sizes and offsets.
 */
public class EelModel extends HierarchicalModel<EelEntity> {
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart front;
    private final ModelPart back;
    private final ModelPart tail;

    public EelModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.front = root.getChild("front");
        this.back = this.front.getChild("back");
        this.tail = this.back.getChild("tail");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        parts.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 3.0F),
            PartPose.offset(0.0F, 22.5F, -5.0F));
        PartDefinition front = parts.addOrReplaceChild("front",
            CubeListBuilder.create().texOffs(0, 6).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F)
                .texOffs(16, 8).addBox(0.0F, -2.0F, 0.0F, 0.0F, 1.0F, 5.0F),
            PartPose.offset(0.0F, 22.5F, -5.0F));
        PartDefinition back = front.addOrReplaceChild("back",
            CubeListBuilder.create().texOffs(0, 14).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 5.0F)
                .texOffs(16, 14).addBox(0.0F, -2.0F, 0.0F, 0.0F, 1.0F, 5.0F),
            PartPose.offset(0.0F, 0.0F, 5.0F));
        PartDefinition tail = back.addOrReplaceChild("tail",
            CubeListBuilder.create().texOffs(0, 22).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 4.0F),
            PartPose.offset(0.0F, 0.0F, 5.0F));
        tail.addOrReplaceChild("tail_fin", CubeListBuilder.create().texOffs(16, 0).addBox(0.0F, -2.0F, 0.0F, 0.0F, 4.0F, 3.0F),
            PartPose.offset(0.0F, 0.0F, 4.0F));
        parts.addOrReplaceChild("right_fin", CubeListBuilder.create().texOffs(24, 0).addBox(-2.0F, 0.0F, -1.0F, 2.0F, 0.0F, 2.0F),
            PartPose.offsetAndRotation(-1.0F, 23.0F, -4.0F, 0.0F, 0.0F, (float) (-Math.PI / 4)));
        parts.addOrReplaceChild("left_fin", CubeListBuilder.create().texOffs(24, 2).addBox(0.0F, 0.0F, -1.0F, 2.0F, 0.0F, 2.0F),
            PartPose.offsetAndRotation(1.0F, 23.0F, -4.0F, 0.0F, 0.0F, (float) (Math.PI / 4)));
        return LayerDefinition.create(mesh, 32, 32);
    }

    @Override
    public ModelPart root() { return root; }

    @Override
    public void setupAnim(EelEntity eel, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float strength = eel.isInWater() ? 0.5F : 0.9F;
        float t = 0.45F * ageInTicks;
        this.head.yRot = -0.4F * strength * Mth.sin(t - 0.2F);
        this.front.yRot = 0.5F * strength * Mth.sin(t);
        this.back.yRot = 0.6F * strength * Mth.sin(t - 0.9F);
        this.tail.yRot = 0.7F * strength * Mth.sin(t - 1.8F);
    }
}
