package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.ExtraPart;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Draws one limb: the vanilla model part the bone came from, wearing the mob's texture and any coats over
 * it, placed so the part's box lines up with the physics box of the block cells. Parts under this one that
 * are bones of their own are hidden while drawing, and parts the rig attaches (a beak, a hat) ride along.
 */
public class CarcassPartRenderer implements BlockEntityRenderer<CarcassPartBlockEntity> {
    private final EntityModelSet modelSet;

    public CarcassPartRenderer(BlockEntityRendererProvider.Context context) {
        this.modelSet = context.getModelSet();
    }

    @Override
    public void render(CarcassPartBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (!be.isRoot()) {
            return;
        }
        Rig rig = RigManager.clientRig(be.entity()).orElse(null);
        if (rig == null) {
            return;
        }
        Bone bone = rig.bone(be.bone()).orElse(null);
        if (bone == null) {
            return;
        }
        int rot = CarcassModels.rotColor(be.freshness());
        Vector3f min = bone.boxMin();
        poseStack.pushPose();
        // The block cell's minimum corner is the box's minimum corner, and the part's own origin sits
        // at -boxMin from there (in pixels).
        poseStack.translate(-min.x / 16.0F, -min.y / 16.0F, -min.z / 16.0F);
        CarcassModels.drawBone(rig, bone, be.texture(), be.passes(), rot, poseStack, buffers, packedLight);
        WoundCaps.draw(rig, bone, be.cuts(), rot, poseStack, buffers, packedLight);
        // resting form: the other limbs, posed relative to this bone's frame
        for (CarcassPartBlockEntity.MergedPart mergedPart : be.merged()) {
            Bone other = rig.bone(mergedPart.bone()).orElse(null);
            if (other == null) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(mergedPart.position().x, mergedPart.position().y, mergedPart.position().z);
            poseStack.mulPose(mergedPart.orientation());
            CarcassModels.drawBone(rig, other, be.texture(), be.passes(), rot, poseStack, buffers, packedLight);
            WoundCaps.draw(rig, other, be.cuts(), rot, poseStack, buffers, packedLight);
            poseStack.popPose();
        }
        poseStack.popPose();
        drawHook(be, poseStack, buffers, packedLight);
    }

    /** How deep the hook's bend sits inside the meat, in model pixels along the shank. */
    private static final float HOOK_DEPTH_PX = 6.5F;
    private static final float HOOK_SCALE = 0.6F;

    /**
     * The Meat Hook buried in this limb, drawn in the limb's own frame so it moves exactly with the meat.
     * The model's shank runs up +Y with the point at the bottom; +Y is turned to face back the way the hook
     * went in, so the point is inside and the eye faces the player.
     */
    private void drawHook(CarcassPartBlockEntity be, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        if (be.getLevel() == null) {
            return;
        }
        dev.ryanhcode.sable.sublevel.SubLevel subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(be.getLevel(), be.getBlockPos());
        if (subLevel == null) {
            return;
        }
        ClientDragState.Drag drag = ClientDragState.hookIn(subLevel.getUniqueId());
        if (drag == null) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        Vector3f out = new Vector3f((float) -drag.entry().x, (float) -drag.entry().y, (float) -drag.entry().z);
        if (out.lengthSquared() < 1.0E-6F) {
            out.set(0, 1, 0);
        }
        out.normalize();
        poseStack.pushPose();
        poseStack.translate(drag.anchor().x - pos.getX(), drag.anchor().y - pos.getY(), drag.anchor().z - pos.getZ());
        poseStack.mulPose(new org.joml.Quaternionf().rotationTo(new Vector3f(0.0F, 1.0F, 0.0F), out));
        poseStack.scale(HOOK_SCALE, HOOK_SCALE, HOOK_SCALE);
        // FIXED display centres the model on (8,8,8); slide it so the bend sits inside the flesh
        poseStack.translate(0.0, HOOK_DEPTH_PX / 16.0, 0.0);
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        // the hook in the meat is the bloodied one (the clean one in bloodless mode); the clean model sits in the hand
        net.minecraft.world.item.ItemStack hook = new net.minecraft.world.item.ItemStack(com.avicagan.bloodandbones.registry.BBItems.MEAT_HOOK.get());
        net.minecraft.client.resources.model.BakedModel model = com.avicagan.bloodandbones.config.BBClientConfig.bloodless()
                ? minecraft.getItemRenderer().getItemModelShaper().getItemModel(hook)
                : minecraft.getModelManager().getModel(BBClientModels.MEAT_HOOK_BLOODY);
        minecraft.getItemRenderer().render(hook,
                net.minecraft.world.item.ItemDisplayContext.FIXED, false, poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY, model);
        poseStack.popPose();
    }

    /**
     * Sable draws sub-level block entities itself, outside vanilla's frustum test, so the only visibility
     * rule that applies is the view distance; a resting carcass drawn from its torso cell needs a bit more.
     */
    @Override
    public int getViewDistance() {
        return 96;
    }
}
