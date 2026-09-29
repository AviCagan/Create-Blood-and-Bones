package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.minion.MinionBody;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/**
 * A minion as it was built: the pieces of carcass stitched together on the Surgery Table, each in its own mob's
 * look ({@link StitchedBody}). It walks the way its legs do, and lies on its side when it has run out of blood. What it
 * holds is in the hand at the end of its first arm of hand grip (a pair of arms holds it in front of them, a head with no
 * hand in its mouth), and what it wears on its head sits over its head, sized to it (MinionBody#anchors, the same
 * layout the rest is drawn with).
 */
public class StitchedMinionRenderer extends EntityRenderer<MinionEntity> {
    private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final net.minecraft.world.item.ItemStack SADDLE = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE);
    /**
     * A touch more room than vanilla gives a humanoid's head under what it wears (a helmet or a pumpkin a pixel proud of
     * it all round): a snout flush with that pixel (a pig's) stays inside rather than flickering through the front.
     */
    private static final float ROOM = 1.04F;
    /** A humanoid's helmet, stretched over whatever head it is put on. */
    private final HumanoidArmorModel<MinionEntity> helmet;

    public StitchedMinionRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.5F;
        helmet = new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR));
    }

    @Override
    public void render(MinionEntity minion, float yaw, float partialTicks, PoseStack ms, MultiBufferSource buffers, int light) {
        MinionBuild build = minion.build().orElse(null);
        if (build == null) {
            return;
        }
        MinionBody.Layout layout = StitchedBody.layout(build);
        shadowRadius = Math.max(0.25F, layout.width() * 0.5F);
        float bodyYaw = Mth.rotLerp(partialTicks, minion.yBodyRotO, minion.yBodyRot);
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partialTicks, minion.yHeadRotO, minion.yHeadRot) - bodyYaw);
        float speed = minion.walkAnimation.speed(partialTicks);
        StitchedBody.Motion motion = new StitchedBody.Motion(minion.walkAnimation.position(partialTicks), speed, headYaw,
                Mth.lerp(partialTicks, minion.xRotO, minion.getXRot()), minion.stats().mode(), minion.tickCount + partialTicks,
                minion.getHealth() / Math.max(1.0F, minion.getMaxHealth()));
        boolean lying = minion.poweredDown() || minion.deathTime > 0;
        // hurt, it flashes red as any mob does
        int tint = minion.hurtTime > 0 || minion.deathTime > 0 ? 0xFFFF9999 : -1;
        // a glowing minion (the glow trait) shines whatever the dark round it
        int shine = com.avicagan.bloodandbones.client.effect.SocialClient.minionLight(minion, light);
        MinionBody.Anchors anchors = StitchedBody.anchors(build);
        ItemStack held = minion.getMainHandItem();
        ItemStack worn = minion.getItemBySlot(EquipmentSlot.HEAD);
        ms.pushPose();
        ms.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        // entity models are drawn upside down, their ground at 24 pixels (as LivingEntityRenderer does)
        ms.scale(-1.0F, -1.0F, 1.0F);
        ms.translate(0.0F, -1.501F, 0.0F);
        StitchedBody.draw(build, motion, lying, tint, ms, buffers, shine, held.isEmpty() && worn.isEmpty() ? null : (piece, placement, pose) -> {
            if (piece == anchors.hold() && !held.isEmpty()) {
                drawHeld(minion, held, anchors, placement, pose, buffers, shine);
            }
            if (piece == anchors.head() && !worn.isEmpty()) {
                drawWorn(minion, worn, placement, pose, buffers, shine);
            }
        });
        ms.popPose();
        if (minion.isSaddled() && !lying) {
            // a plain saddle thrown over its back
            org.joml.Vector3f at = MinionBody.saddlePoint(layout);
            ms.pushPose();
            ms.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
            ms.translate(at.x, at.y + 0.02F, at.z);
            ms.mulPose(Axis.XP.rotationDegrees(90.0F));
            ms.scale(0.75F, 0.75F, 0.75F);
            net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(SADDLE,
                    net.minecraft.world.item.ItemDisplayContext.FIXED, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, ms, buffers, minion.level(), 0);
            ms.popPose();
        }
        super.render(minion, yaw, partialTicks, ms, buffers, light);
    }

    /**
     * What it holds, where its anchors say: gripped at the end of an arm as vanilla's hand layer grips it (turned a
     * quarter down and a half round, drawn as a hand's item), in front of a pair of arms as a villager holds its wares, or
     * across a mouth as a fox carries things.
     */
    private static void drawHeld(MinionEntity minion, ItemStack held, MinionBody.Anchors anchors, MinionBody.Placement placement, PoseStack ms,
                                 MultiBufferSource buffers, int light) {
        Vector3f at = anchors.holdAt();
        ms.pushPose();
        ms.translate(at.x / 16.0F, at.y / 16.0F, at.z / 16.0F);
        ItemDisplayContext context;
        boolean left = false;
        switch (anchors.how()) {
            case "pair" -> {
                // upright whatever the folded arms' own tilt
                ms.mulPose(new org.joml.Quaternionf(placement.bone().rotation()).conjugate());
                ms.mulPose(Axis.XP.rotationDegrees(180.0F));
                context = ItemDisplayContext.GROUND;
            }
            case "mouth" -> {
                ms.mulPose(Axis.XP.rotationDegrees(90.0F));
                context = ItemDisplayContext.GROUND;
            }
            default -> {
                ms.mulPose(Axis.XP.rotationDegrees(-90.0F));
                ms.mulPose(Axis.YP.rotationDegrees(180.0F));
                left = !anchors.right();
                context = left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }
        }
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(minion, held, context, left, ms, buffers, light);
        ms.popPose();
    }

    /**
     * What it wears on its head, over the head's box: a helmet is a humanoid's helmet stretched to fit the head (a cow's
     * long skull gets a long helmet), in its material's layers, dyed and glinting as worn armour is; anything else (a
     * carved pumpkin, a skull) is the item's own head look, sized to the head as vanilla sizes it to a humanoid's.
     */
    private void drawWorn(MinionEntity minion, ItemStack worn, MinionBody.Placement placement, PoseStack ms, MultiBufferSource buffers, int light) {
        Vector3f lo = placement.bone().boxMin();
        Vector3f hi = placement.bone().boxMax();
        float sx = (hi.x - lo.x) / 8.0F * ROOM;
        float sy = (hi.y - lo.y) / 8.0F * ROOM;
        float sz = (hi.z - lo.z) / 8.0F * ROOM;
        ms.pushPose();
        ms.translate((lo.x + hi.x) / 32.0F, (lo.y + hi.y) / 32.0F, (lo.z + hi.z) / 32.0F);
        if (worn.getItem() instanceof ArmorItem armour && armour.getEquipmentSlot() == EquipmentSlot.HEAD) {
            ms.scale(sx, sy, sz);
            // the helmet's head is 8 pixels square, its joint at the bottom of it
            ms.translate(0.0F, 4.0F / 16.0F, 0.0F);
            ModelPart[] parts = {helmet.head, helmet.hat};
            for (ModelPart part : parts) {
                part.resetPose();
                part.visible = true;
            }
            ArmorMaterial material = armour.getMaterial().value();
            var extensions = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(worn);
            int fallback = extensions.getDefaultDyeColor(worn);
            for (int i = 0; i < material.layers().size(); i++) {
                ArmorMaterial.Layer layer = material.layers().get(i);
                int colour = extensions.getArmorLayerTintColor(worn, minion, layer, i, fallback);
                if (colour != 0) {
                    ResourceLocation texture = net.neoforged.neoforge.client.ClientHooks.getArmorTexture(minion, worn, layer, false, EquipmentSlot.HEAD);
                    for (ModelPart part : parts) {
                        part.render(ms, buffers.getBuffer(RenderType.armorCutoutNoCull(texture)), light, OverlayTexture.NO_OVERLAY, colour);
                    }
                }
            }
            if (worn.hasFoil()) {
                for (ModelPart part : parts) {
                    part.render(ms, buffers.getBuffer(RenderType.armorEntityGlint()), light, OverlayTexture.NO_OVERLAY);
                }
            }
        } else {
            ms.mulPose(Axis.YP.rotationDegrees(180.0F));
            ms.scale(0.625F * sx, -0.625F * sy, -0.625F * sz);
            Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer().renderItem(minion, worn, ItemDisplayContext.HEAD, false, ms, buffers, light);
        }
        ms.popPose();
    }

    @Override
    public ResourceLocation getTextureLocation(MinionEntity minion) {
        return NONE;
    }
}
