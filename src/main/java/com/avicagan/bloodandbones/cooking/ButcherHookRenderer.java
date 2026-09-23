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
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** The piece hanging from the hook, turning a little on it. */
public class ButcherHookRenderer extends SafeBlockEntityRenderer<ButcherHookBlockEntity> {
    public ButcherHookRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(ButcherHookBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(be.specimen());
        Rig rig = piece == null ? null : RigManager.clientRig(piece.entity(), piece.baby()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        if (bone == null || be.getLevel() == null) {
            return;
        }
        Direction facing = be.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        float time = (be.getLevel().getGameTime() + partialTicks) / 20.0F + be.getBlockPos().hashCode() % 100;
        ms.pushPose();
        ms.translate(0.5F, 0.5F, 0.5F);
        // the model points north; turn it the way the hook points
        ms.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() + 180.0F));
        // hanging from the hook's tip, out from the wall, swaying a little
        ms.translate(0.0F, 0.1F, -0.28F);
        ms.mulPose(Axis.ZP.rotationDegrees(4.0F * (float) Math.sin(time * 0.7F)));
        ms.translate(0.0F, -0.42F, 0.0F);
        CarcassModels.drawPiece(piece, rig, bone, 0.55F, -1, ms, buffer, light);
        ms.popPose();
    }
}
