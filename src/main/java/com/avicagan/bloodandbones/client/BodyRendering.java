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
                    float spool = implant.spec().slots() > 0 ? CyberClient.level(player, part) : 0.0F;
                    if (spool > 0.0F) {
                        arm.render(event.getPoseStack(), event.getMultiBufferSource().getBuffer(RenderType.eyes(glow(implant.texture()))),
                                net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glowColor(spool));
                    }
                    arm.visible = visible;
                }
            }
        }
    }

    /**
     * An eye gone is reduced vision: fog closes in, to half the view with one working eye, to a few blocks
     * with none.
     */
    @SubscribeEvent
    public static void onFog(net.neoforged.neoforge.client.event.ViewportEvent.RenderFog event) {
        var player = Minecraft.getInstance().player;
        if (player == null || event.getCamera().getEntity() != player || !BodyEffects.altered(player)) {
            return;
        }
        Body body = BodyEffects.body(player);
        int eyes = (body.works(BodyPart.LEFT_EYE, player) ? 1 : 0) + (body.works(BodyPart.RIGHT_EYE, player) ? 1 : 0);
        if (eyes == 2) {
            return;
        }
        float far = eyes == 1 ? event.getFarPlaneDistance() * 0.5F : 6.0F;
        if (far < event.getFarPlaneDistance()) {
            event.setNearPlaneDistance(Math.min(event.getNearPlaneDistance(), far * 0.25F));
            event.setFarPlaneDistance(far);
            event.setCanceled(true);
        }
    }

    /** The outside view during surgery on yourself sits close: a little above, looking down. */
    @SubscribeEvent
    public static void onCameraDistance(net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent event) {
        if (SurgeryScreen.operatingOnSelf()) {
            event.setDistance(Math.min(event.getDistance(), 2.5F));
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
    private static final String[] SIDES = {"_left.png", "_right.png"};

    /** An empty socket where an eye is gone; an implant's own eye where one is fitted, glowing if it sees in the dark and works. */
    static void eyes(PoseStack poseStack, MultiBufferSource buffers, int packedLight, net.minecraft.world.entity.LivingEntity player, Body body, ModelPart head) {
        BodyPart[] eyes = {BodyPart.LEFT_EYE, BodyPart.RIGHT_EYE};
        for (int i = 0; i < 2; i++) {
            Body.State state = body.state(eyes[i]);
            if (state == Body.State.NATURAL) {
                continue;
            }
            ImplantItem implant = state == Body.State.IMPLANT && body.implant(eyes[i]).getItem() instanceof ImplantItem item ? item : null;
            if (state == Body.State.IMPLANT && (implant == null || implant.texture() == null)) {
                continue;
            }
            boolean glowing = implant != null && implant.spec().ability() == com.avicagan.bloodandbones.body.ImplantSpec.Ability.NIGHT_VISION
                    && body.works(eyes[i], player);
            String side = SIDES[i];
            net.minecraft.resources.ResourceLocation texture = implant == null ? SOCKETS[i] : implant.texture().withPath(
                    p -> p.substring(0, p.length() - ".png".length()) + side);
            RenderType type = glowing ? RenderType.eyes(texture) : RenderType.entityCutoutNoCull(texture);
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

    /** The seams' glow at this spool: the gauge's colour, brighter as it climbs. */
    static int glowColor(float spool) {
        int shade = (int) (255 * (0.55F + 0.45F * spool));
        int color = CyberClient.heat(spool);
        int r = (color >> 16 & 0xFF) * shade / 255, g = (color >> 8 & 0xFF) * shade / 255, b = (color & 0xFF) * shade / 255;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** A brass limb's glow sheet: its texture's name with _glow. */
    static net.minecraft.resources.ResourceLocation glow(net.minecraft.resources.ResourceLocation texture) {
        return texture.withPath(p -> p.substring(0, p.length() - ".png".length()) + "_glow.png");
    }

    /** Implants drawn where the parts they replace were, posed like them. */
    public static class ImplantLayer<T extends net.minecraft.world.entity.LivingEntity, M extends PlayerModel<T>> extends RenderLayer<T, M> {
        public ImplantLayer(RenderLayerParent<T, M> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, T player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            Body body = BodyEffects.body(player);
            if (body.whole() || player.isInvisible()) {
                return;
            }
            eyes(poseStack, buffers, packedLight, player, body, getParentModel().head);
            for (BodyPart part : BodyPart.values()) {
                if (body.state(part) == Body.State.IMPLANT && body.implant(part).getItem() instanceof ImplantItem implant
                        && implant.texture() != null && part(getParentModel(), part) != null) {
                    ModelPart model = part(getParentModel(), part);
                    boolean visible = model.visible;
                    model.visible = true;
                    // an organic part greys and greens as it rots from use
                    float rot = implant.organic() ? com.avicagan.bloodandbones.body.Necrosis.of(body.implant(part)) / (float) com.avicagan.bloodandbones.body.Necrosis.MAX : 0.0F;
                    model.render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(implant.texture())), packedLight,
                            net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(player, 0.0F), CarcassModels.rotColor(1.0F - rot));
                    // a brass limb being spooled glows along its seams, brighter as the throttle climbs
                    float spool = implant.spec().slots() > 0 ? CyberClient.level(player, part) : 0.0F;
                    if (spool > 0.0F) {
                        model.render(poseStack, buffers.getBuffer(RenderType.eyes(glow(implant.texture()))), net.minecraft.client.renderer.LightTexture.FULL_BRIGHT,
                                OverlayTexture.NO_OVERLAY, glowColor(spool));
                    }
                    model.visible = visible;
                }
            }
        }
    }
}
