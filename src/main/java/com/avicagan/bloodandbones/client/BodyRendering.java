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
            default -> null;
        };
    }

    static ModelPart outer(PlayerModel<?> model, BodyPart part) {
        return switch (part) {
            case LEFT_ARM -> model.leftSleeve;
            case RIGHT_ARM -> model.rightSleeve;
            case LEFT_LEG -> model.leftPants;
            case RIGHT_LEG -> model.rightPants;
            default -> null;
        };
    }

    @SubscribeEvent
    public static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        // anything left from a frame whose Post never came (another mod cancelled the render) is shown again
        for (ModelPart part : HIDDEN) {
            part.visible = true;
        }
        HIDDEN.clear();
        Body body = BodyEffects.body(event.getEntity());
        if (body.whole()) {
            return;
        }
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        for (BodyPart part : BodyPart.values()) {
            if (body.state(part) != Body.State.NATURAL && part(model, part) != null) {
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
                if (body.implant(part).getItem() instanceof ImplantItem implant && implant.texture() != null
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

    private static final net.minecraft.resources.ResourceLocation[] SOCKETS = {
            BloodAndBones.asResource("textures/entity/implant/eye_socket_left.png"), BloodAndBones.asResource("textures/entity/implant/eye_socket_right.png")};
    private static final net.minecraft.resources.ResourceLocation[] OPTICS = {
            BloodAndBones.asResource("textures/entity/implant/optic_eye_left.png"), BloodAndBones.asResource("textures/entity/implant/optic_eye_right.png")};

    /** An empty socket where an eye is gone, a red lens where an Optic Eye is, glowing while it works. */
    static void eyes(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player, Body body) {
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) {
            return;
        }
        ModelPart head = renderer.getModel().head;
        BodyPart[] eyes = {BodyPart.LEFT_EYE, BodyPart.RIGHT_EYE};
        for (int i = 0; i < 2; i++) {
            Body.State state = body.state(eyes[i]);
            if (state == Body.State.NATURAL) {
                continue;
            }
            boolean glowing = state == Body.State.IMPLANT && body.works(eyes[i], player);
            RenderType type = state == Body.State.MISSING ? RenderType.entityCutoutNoCull(SOCKETS[i])
                    : glowing ? RenderType.eyes(OPTICS[i]) : RenderType.entityCutoutNoCull(OPTICS[i]);
            // a hair out from the face, so it is not lost in it; the head turns about the neck, the origin
            poseStack.pushPose();
            poseStack.scale(1.02F, 1.02F, 1.02F);
            boolean visible = head.visible;
            head.visible = true;
            head.render(poseStack, buffers.getBuffer(type), glowing ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : packedLight, OverlayTexture.NO_OVERLAY);
            head.visible = visible;
            poseStack.popPose();
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
            eyes(poseStack, buffers, packedLight, player, body);
            for (BodyPart part : BodyPart.values()) {
                if (body.state(part) == Body.State.IMPLANT && body.implant(part).getItem() instanceof ImplantItem implant
                        && implant.texture() != null && part(getParentModel(), part) != null) {
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
