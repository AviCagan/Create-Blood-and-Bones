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

    private static final net.minecraft.resources.ResourceLocation WOUND = BloodAndBones.asResource("textures/entity/wound.png");
    private static final net.minecraft.resources.ResourceLocation RAGGED = BloodAndBones.asResource("textures/entity/wound_ragged.png");
    private static final net.minecraft.resources.ResourceLocation FLAPS = BloodAndBones.asResource("textures/entity/stump_flaps.png");
    /** How long a stump is, in model pixels: a clean cut close, a ragged one longer and torn. */
    private static final float CLEAN_STUMP = 3.0F;
    private static final float RAGGED_STUMP = 4.5F;
    /** How far a ragged stump's torn flaps hang past its end. */
    private static final float FLAP = 2.0F;

    /**
     * A stump where a limb was: the top of the limb in its own skin, cut short, its end raw (none of the blood in
     * bloodless mode). A ragged one is longer, its end torn, with flaps of flesh hanging off it.
     */
    static void stump(PoseStack poseStack, MultiBufferSource buffers, int packedLight, int overlay, PlayerModel<?> model, BodyPart part,
                      boolean ragged, net.minecraft.resources.ResourceLocation skin) {
        ModelPart limb = part(model, part);
        if (limb == null || limb.isEmpty()) {
            return;
        }
        ModelPart.Cube box = limb.getRandomCube(net.minecraft.util.RandomSource.create(0));
        float length = ragged ? RAGGED_STUMP : CLEAN_STUMP;
        float end = box.minY + length;
        poseStack.pushPose();
        limb.translateAndRotate(poseStack);
        // the top of the limb in the wearer's own skin, sleeve or trouser leg and all, cut short
        stumpPart(part, box, length).render(poseStack, buffers.getBuffer(RenderType.entityCutoutNoCull(skin)), packedLight, overlay);
        if (!com.avicagan.bloodandbones.config.BBClientConfig.bloodless()) {
            PoseStack.Pose pose = poseStack.last();
            // over the end of the sleeve or trouser leg too, which sits a quarter pixel out
            float x0 = box.minX - 0.3F, x1 = box.maxX + 0.3F, z0 = box.minZ - 0.3F, z1 = box.maxZ + 0.3F;
            float y = end + 0.3F;
            var wound = buffers.getBuffer(RenderType.entityCutoutNoCull(ragged ? RAGGED : WOUND));
            quad(wound, pose, packedLight, overlay, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, 0, 1, 0);
            if (ragged) {
                // torn flesh hanging off each side, flaring a little
                var flaps = buffers.getBuffer(RenderType.entityCutoutNoCull(FLAPS));
                float f = 0.3F;
                float low = y + FLAP;
                quad(flaps, pose, packedLight, overlay, x0, y, z0, x1, y, z0, x1 + f, low, z0 - f, x0 - f, low, z0 - f, 0, 0, -1);
                quad(flaps, pose, packedLight, overlay, x1, y, z1, x0, y, z1, x0 - f, low, z1 + f, x1 + f, low, z1 + f, 0, 0, 1);
                quad(flaps, pose, packedLight, overlay, x0, y, z1, x0, y, z0, x0 - f, low, z0 - f, x0 - f, low, z1 + f, -1, 0, 0);
                quad(flaps, pose, packedLight, overlay, x1, y, z0, x1, y, z1, x1 + f, low, z1 + f, x1 + f, low, z0 - f, 1, 0, 0);
            }
        }
        poseStack.popPose();
    }

    private static final java.util.Map<String, ModelPart> STUMPS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A short box over the top of a limb, textured from the same place on the player skin as the limb (and its
     * outer layer, a little bigger), so a stump shows the shoulder or hip, not a squashed hand or shoe.
     */
    private static ModelPart stumpPart(BodyPart part, ModelPart.Cube box, float length) {
        float width = box.maxX - box.minX;
        return STUMPS.computeIfAbsent(part + "/" + width + "/" + length, key -> {
            int[] uv = switch (part) {
                case RIGHT_ARM -> new int[]{40, 16, 40, 32};
                case LEFT_ARM -> new int[]{32, 48, 48, 48};
                case RIGHT_LEG -> new int[]{0, 16, 0, 32};
                default -> new int[]{16, 48, 0, 48};
            };
            var mesh = new net.minecraft.client.model.geom.builders.MeshDefinition();
            mesh.getRoot().addOrReplaceChild("stump", net.minecraft.client.model.geom.builders.CubeListBuilder.create()
                    .texOffs(uv[0], uv[1]).addBox(box.minX, box.minY, box.minZ, width, length, box.maxZ - box.minZ)
                    .texOffs(uv[2], uv[3]).addBox(box.minX, box.minY, box.minZ, width, length, box.maxZ - box.minZ,
                            new net.minecraft.client.model.geom.builders.CubeDeformation(0.25F)), net.minecraft.client.model.geom.PartPose.ZERO);
            return net.minecraft.client.model.geom.builders.LayerDefinition.create(mesh, 64, 64).bakeRoot().getChild("stump");
        });
    }

    /** One textured quad, corners in model pixels, the texture's top edge along the first two. */
    private static void quad(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, int light, int overlay,
                             float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3,
                             float nx, float ny, float nz) {
        float[][] corners = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            buffer.addVertex(pose, corners[i][0] / 16.0F, corners[i][1] / 16.0F, corners[i][2] / 16.0F)
                    .setColor(0xFFFFFFFF)
                    .setUv(uvs[i][0], uvs[i][1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, nx, ny, nz);
        }
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
            // a limb gone leaves a stump: a clean one where a blade took it, a ragged one where a surgeon hacked it
            for (BodyPart part : new BodyPart[]{BodyPart.LEFT_ARM, BodyPart.RIGHT_ARM, BodyPart.LEFT_LEG, BodyPart.RIGHT_LEG}) {
                if (body.state(part) == Body.State.MISSING) {
                    stump(poseStack, buffers, packedLight, net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(player, 0.0F),
                            getParentModel(), part, body.ragged(part), getTextureLocation(player));
                }
            }
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
