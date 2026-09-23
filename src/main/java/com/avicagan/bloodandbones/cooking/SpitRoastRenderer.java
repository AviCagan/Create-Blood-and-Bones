package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.client.CarcassModels;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/** The spit's shaft (without Flywheel) and the piece on it, turning with the shaft and browning as it cooks. */
public class SpitRoastRenderer extends KineticBlockEntityRenderer<SpitRoastBlockEntity> {
    public SpitRoastRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected BlockState getRenderedBlockState(SpitRoastBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }

    @Override
    protected void renderSafe(SpitRoastBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(be.piece());
        Rig rig = piece == null ? null : RigManager.clientRig(piece.entity()).orElse(null);
        Bone bone = rig == null ? null : rig.bone(piece.bone()).orElse(null);
        if (bone != null) {
            Direction.Axis axis = be.getBlockState().getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS);
            float angle = getAngleForBe(be, be.getBlockPos(), axis);
            ms.pushPose();
            ms.translate(0.5F, 0.5F, 0.5F);
            ms.mulPose(axis == Direction.Axis.X ? Axis.XP.rotation(angle) : Axis.ZP.rotation(angle));
            // lay the piece along the spit
            if (axis == Direction.Axis.X) {
                ms.mulPose(Axis.ZP.rotationDegrees(90));
            } else {
                ms.mulPose(Axis.XP.rotationDegrees(90));
            }
            CarcassModels.drawPiece(piece, rig, bone, 0.85F, browning(be.doneness()), ms, buffer, light);
            ms.popPose();
        }
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }

    /** Raw meat is untinted; cooked goes a rich brown; burnt goes black. */
    static int browning(float doneness) {
        if (doneness <= 0) {
            return -1;
        }
        float cooked = Mth.clamp(doneness, 0, 1);
        float burnt = Mth.clamp(doneness - 1, 0, 1);
        float r = Mth.lerp(cooked, 1.0F, 0.72F) * Mth.lerp(burnt, 1.0F, 0.25F);
        float g = Mth.lerp(cooked, 1.0F, 0.50F) * Mth.lerp(burnt, 1.0F, 0.25F);
        float b = Mth.lerp(cooked, 1.0F, 0.34F) * Mth.lerp(burnt, 1.0F, 0.25F);
        return FastColor.ARGB32.colorFromFloat(1.0F, r, g, b);
    }
}
