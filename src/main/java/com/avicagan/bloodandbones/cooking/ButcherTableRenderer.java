package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.client.CarcassModels;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** The piece lying on the table top, turned a different way on each table. */
public class ButcherTableRenderer extends SafeBlockEntityRenderer<ButcherTableBlockEntity> {
    /** How big the piece is drawn, its longest side in blocks. */
    private static final float SIZE = 0.8F;

    public ButcherTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** The piece lies above the block, so the block's own box would cull it too soon. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(ButcherTableBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    protected void renderSafe(ButcherTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(be.specimen());
        Rig rig = piece == null ? null : RigManager.clientRig(piece.entity(), piece.baby()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        if (bone == null) {
            return;
        }
        // drawPiece stands the piece up, centred and fitted to SIZE; laid on its back its depth is its height
        float largest = Math.max(bone.boxSize().x, Math.max(bone.boxSize().y, bone.boxSize().z)) / 16.0F;
        float fit = SIZE / Math.max(largest, 0.3F);
        float lying = bone.boxSize().z / 16.0F * fit;
        ms.pushPose();
        ms.translate(0.5F, 1.0F + lying / 2.0F, 0.5F);
        ms.mulPose(Axis.YP.rotationDegrees((be.getBlockPos().hashCode() & 3) * 90.0F + 20.0F));
        ms.mulPose(Axis.XP.rotationDegrees(90.0F));
        CarcassModels.drawPiece(piece, rig, bone, SIZE, -1, ms, buffer, light);
        ms.popPose();
    }
}
