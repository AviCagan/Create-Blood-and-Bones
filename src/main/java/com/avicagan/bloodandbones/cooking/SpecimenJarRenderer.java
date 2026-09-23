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
import net.minecraft.util.FastColor;

/** The specimen, drifting slowly in the jar's cloudy fluid. */
public class SpecimenJarRenderer extends SafeBlockEntityRenderer<SpecimenJarBlockEntity> {
    /** Preserving fluid takes the colour out: a pale, yellowed look. */
    private static final int PICKLED = FastColor.ARGB32.colorFromFloat(1.0F, 0.92F, 0.9F, 0.72F);

    public SpecimenJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(SpecimenJarBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(be.specimen());
        Rig rig = piece == null ? null : RigManager.clientRig(piece.entity()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        if (bone == null || be.getLevel() == null) {
            return;
        }
        float time = (be.getLevel().getGameTime() + partialTicks) / 20.0F + be.getBlockPos().hashCode() % 100;
        ms.pushPose();
        ms.translate(0.5F, 0.42F + 0.02F * (float) Math.sin(time * 0.8F), 0.5F);
        ms.mulPose(Axis.YP.rotationDegrees(time * 6.0F));
        ms.mulPose(Axis.ZP.rotationDegrees(8.0F * (float) Math.sin(time * 0.5F)));
        CarcassModels.drawPiece(piece, rig, bone, 0.5F, PICKLED, ms, buffer, light);
        ms.popPose();
    }
}
