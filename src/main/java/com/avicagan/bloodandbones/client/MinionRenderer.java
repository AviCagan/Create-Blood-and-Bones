package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * A minion: a stitched body of pale flesh on the player's shape, with the parts it was not given left off
 * and its implants and backtank drawn as on a player.
 */
public class MinionRenderer extends HumanoidMobRenderer<MinionEntity, PlayerModel<MinionEntity>> {
    private static final ResourceLocation TEXTURE = BloodAndBones.asResource("textures/entity/minion.png");

    public MinionRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)), context.getModelManager()));
        addLayer(new BodyRendering.ImplantLayer<>(this));
        addLayer(new FluidBacktankLayer<>(this));
    }

    @Override
    public ResourceLocation getTextureLocation(MinionEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MinionEntity entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffers, int light) {
        PlayerModel<MinionEntity> model = getModel();
        Body body = BodyEffects.body(entity);
        model.setAllVisible(true);
        for (BodyPart part : BodyPart.values()) {
            ModelPart limb = BodyRendering.part(model, part);
            if (limb != null && body.state(part) != Body.State.NATURAL) {
                limb.visible = false;
                BodyRendering.outer(model, part).visible = false;
            }
        }
        super.render(entity, yaw, partialTicks, poseStack, buffers, light);
        model.setAllVisible(true);
    }
}
