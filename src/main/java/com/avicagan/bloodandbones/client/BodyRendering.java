package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.ImplantItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Drawing a body on a player: a part that is gone is not drawn (nor its sleeve or trouser leg), and an
 * implant is drawn in its place, in third person and first person. What is held in a hand with no arm is
 * not drawn in first person either.
 */
@EventBusSubscriber(modid = BloodAndBones.MOD_ID, value = Dist.CLIENT)
public final class BodyRendering {
    private static final List<ModelPart> HIDDEN = new ArrayList<>();

    private BodyRendering() {
    }

    static ModelPart part(PlayerModel<?> model, BodyPart part) {
        return switch (part) {
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
        };
    }

    static ModelPart outer(PlayerModel<?> model, BodyPart part) {
        return switch (part) {
            case LEFT_ARM -> model.leftSleeve;
            case RIGHT_ARM -> model.rightSleeve;
            case LEFT_LEG -> model.leftPants;
            case RIGHT_LEG -> model.rightPants;
        };
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        Body body = BodyEffects.body(event.getEntity());
        if (body.whole()) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        for (BodyPart part : BodyPart.values()) {
            if (body.state(part) != Body.State.NATURAL) {
                hide(part(model, part));
                hide(outer(model, part));
            }
        }
    }

    private static void hide(ModelPart part) {
        if (part.visible) {
            part.visible = false;
            HIDDEN.add(part);
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerDone(RenderPlayerEvent.Post event) {
        for (ModelPart part : HIDDEN) {
            part.visible = true;
        }
        HIDDEN.clear();
    }

    /** The bare arm in first person: none if it is gone, the implant if one is fitted. */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        BodyPart part = BodyPart.arm(event.getArm());
        Body body = BodyEffects.body(player);
        switch (body.state(part)) {
            case NATURAL -> {
            }
            case MISSING -> event.setCanceled(true);
            case IMPLANT -> {
                event.setCanceled(true);
                if (body.implant(part).getItem() instanceof ImplantItem implant
                        && Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
                    PlayerModel<AbstractClientPlayer> model = renderer.getModel();
                    model.attackTime = 0.0F;
                    model.crouching = false;
                    model.swimAmount = 0.0F;
                    model.setupAnim(player, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
                    ModelPart arm = part(model, part);
                    arm.xRot = 0.0F;
                    boolean visible = arm.visible;
                    arm.visible = true;
                    arm.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(implant.texture())),
                            event.getPackedLight(), OverlayTexture.NO_OVERLAY);
                    arm.visible = visible;
                }
            }
        }
    }

    /** Nothing is held, in first person, by an arm that is not there. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && !event.getItemStack().isEmpty()
                && BodyEffects.body(player).state(BodyEffects.armFor(player, event.getHand())) == Body.State.MISSING) {
            event.setCanceled(true);
        }
    }

    /** Implants drawn where the parts they replace were, posed like them. */
    public static class ImplantLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        public ImplantLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            Body body = BodyEffects.body(player);
            if (body.whole() || player.isInvisible()) {
                return;
            }
            for (BodyPart part : BodyPart.values()) {
                if (body.state(part) == Body.State.IMPLANT && body.implant(part).getItem() instanceof ImplantItem implant) {
                    ModelPart model = part(getParentModel(), part);
                    boolean visible = model.visible;
                    model.visible = true;
                    model.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(implant.texture())), packedLight,
                            net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(player, 0.0F));
                    model.visible = visible;
                }
            }
        }
    }
}
