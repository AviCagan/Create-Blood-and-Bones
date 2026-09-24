package com.avicagan.bloodandbones.decoration;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.client.CarcassModels;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Client: how the morgue furniture draws what it holds. A carcass piece is drawn as itself with the shared
 * {@link CarcassModels#drawPiece}; anything else with the game's item renderer. Every method starts from a
 * pose standing on the surface the thing rests on, and leaves the pose as it found it.
 */
final class ShownItem {
    private ShownItem() {
    }

    /** Lying flat on a top: a piece on its back, a flat item face up, a block sitting on it. */
    static void lying(ItemStack stack, float size, Level level, PoseStack ms, MultiBufferSource buffer, int light) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
        Rig rig = piece == null ? null : RigManager.clientRig(piece.entity(), piece.baby()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        ms.pushPose();
        if (bone != null) {
            // drawPiece stands the piece up, centred and fitted to size; laid on its back its depth is its height
            float drawn = drawnSize(bone, size);
            float fit = drawn / Math.max(largest(bone), 0.3F);
            ms.translate(0.0F, bone.boxSize().z / 16.0F * fit / 2.0F, 0.0F);
            ms.mulPose(Axis.XP.rotationDegrees(90.0F));
            CarcassModels.drawPiece(piece, rig, bone, drawn, -1, ms, buffer, light);
        } else if (solid(stack, level)) {
            solid(stack, size, level, ms, buffer, light);
        } else {
            ms.translate(0.0F, 0.02F, 0.0F);
            ms.mulPose(Axis.XP.rotationDegrees(90.0F));
            ms.scale(size * 0.6F, size * 0.6F, size * 0.6F);
            item(stack, level, ms, buffer, light);
        }
        ms.popPose();
    }

    /** Standing up on a shelf, its front to the pose's south (turn the pose first, as an item frame does). */
    static void standing(ItemStack stack, float size, Level level, PoseStack ms, MultiBufferSource buffer, int light) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
        Rig rig = piece == null ? null : RigManager.clientRig(piece.entity(), piece.baby()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        ms.pushPose();
        if (bone != null) {
            float drawn = drawnSize(bone, size);
            float fit = drawn / Math.max(largest(bone), 0.3F);
            ms.translate(0.0F, bone.boxSize().y / 16.0F * fit / 2.0F, 0.0F);
            CarcassModels.drawPiece(piece, rig, bone, drawn, -1, ms, buffer, light);
        } else if (solid(stack, level)) {
            solid(stack, size, level, ms, buffer, light);
        } else {
            // a flat item is a whole block high in the fixed view
            ms.translate(0.0F, size / 2.0F, 0.0F);
            ms.scale(size, size, size);
            item(stack, level, ms, buffer, light);
        }
        ms.popPose();
    }

    /** A piece's longest side as the mob had it, in blocks. */
    private static float largest(Bone bone) {
        return Math.max(bone.boxSize().x, Math.max(bone.boxSize().y, bone.boxSize().z)) / 16.0F;
    }

    /**
     * The size to hand drawPiece so a piece shows at its own size, only shrunk to fit: a pig's head stays a
     * pig's head, a cow's body fits the table. (drawPiece fits anything under 0.3 blocks as if it were 0.3.)
     */
    private static float drawnSize(Bone bone, float size) {
        return Math.min(Math.max(largest(bone), 0.3F), size);
    }

    /** A block, or anything else drawn solid (a skull, a chest), rather than as a flat picture. */
    private static boolean solid(ItemStack stack, Level level) {
        return Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0).isGui3d();
    }

    /**
     * Stood on the surface as it would lie on the ground, about half of size across. The ground view draws a
     * block a quarter size and lifts it; this undoes the lift so its bottom sits on the surface.
     */
    @SuppressWarnings("deprecation")
    private static void solid(ItemStack stack, float size, Level level, PoseStack ms, MultiBufferSource buffer, int light) {
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
        ItemTransform ground = model.getTransforms().getTransform(ItemDisplayContext.GROUND);
        float scale = size * 2.0F;
        ms.scale(scale, scale, scale);
        ms.translate(0.0F, 0.5F * ground.scale.y() - ground.translation.y(), 0.0F);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ms, buffer, level, 0);
    }

    private static void item(ItemStack stack, Level level, PoseStack ms, MultiBufferSource buffer, int light) {
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ms, buffer, level, 0);
    }
}
