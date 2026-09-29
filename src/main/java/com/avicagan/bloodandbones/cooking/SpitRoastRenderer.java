package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.carcass.CarcassLook;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

/**
 * The spit's shaft (without Flywheel) and what is on it, turning with the shaft and browning as it cooks: a carried
 * piece, or a whole carcass laid along the spit as it stood, every piece it still had in place.
 */
public class SpitRoastRenderer extends KineticBlockEntityRenderer<SpitRoastBlockEntity> {
    /** How long a whole carcass is drawn along the spit, in blocks, at most. */
    private static final float WHOLE_LENGTH = 1.7F;

    public SpitRoastRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected BlockState getRenderedBlockState(SpitRoastBlockEntity be) {
        return shaft(getRotationAxisOf(be));
    }

    /** A whole carcass sticks out past the block. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SpitRoastBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).inflate(1.0);
    }

    @Override
    protected void renderSafe(SpitRoastBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        CarcassPieceItem.Piece first = CarcassPieceItem.piece(be.piece());
        Rig rig = first == null ? null : RigManager.clientRig(first.entity(), first.baby()).orElse(null);
        if (rig != null) {
            Direction.Axis axis = be.getBlockState().getValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS);
            float angle = getAngleForBe(be, be.getBlockPos(), axis);
            ms.pushPose();
            ms.translate(0.5F, 0.5F, 0.5F);
            ms.mulPose(axis == Direction.Axis.X ? Axis.XP.rotation(angle) : Axis.ZP.rotation(angle));
            if (be.whole()) {
                drawWhole(be, rig, axis, ms, buffer, light);
            } else {
                Bone bone = rig.bone(first.bone()).orElse(null);
                if (bone != null) {
                    // lay the piece along the spit
                    if (axis == Direction.Axis.X) {
                        ms.mulPose(Axis.ZP.rotationDegrees(90));
                    } else {
                        ms.mulPose(Axis.XP.rotationDegrees(90));
                    }
                    CarcassModels.drawPiece(first, rig, bone, 0.85F, browning(be.doneness()), ms, buffer, light);
                }
            }
            ms.popPose();
        }
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }

    /**
     * Every piece at its place in the standing mob, the whole turned so it runs head to tail along the spit and centred
     * on it, shrunk to fit if it is long.
     */
    private static void drawWhole(SpitRoastBlockEntity be, Rig rig, Direction.Axis axis, PoseStack ms, MultiBufferSource buffer, int light) {
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        for (ItemStack stack : be.pieces()) {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
            Bone bone = piece == null ? null : rig.bone(piece.bone()).orElse(null);
            if (bone == null) {
                continue;
            }
            for (int i = 0; i < 8; i++) {
                Vector3f corner = new Vector3f((i & 1) == 0 ? bone.boxMin().x : bone.boxMax().x, (i & 2) == 0 ? bone.boxMin().y : bone.boxMax().y,
                        (i & 4) == 0 ? bone.boxMin().z : bone.boxMax().z);
                bone.rotation().transform(corner).add(bone.offset()).div(16.0F);
                min.min(corner);
                max.max(corner);
            }
        }
        if (min.x > max.x) {
            return;
        }
        Vector3f size = new Vector3f(max).sub(min);
        // the mob's own length runs along its Z; its long way goes along the spit
        float length = Math.max(size.z, Math.max(size.x, size.y));
        float fit = Math.min(1.0F, WHOLE_LENGTH / Math.max(length, 0.1F));
        int tint = browning(be.doneness());
        ms.pushPose();
        if (axis == Direction.Axis.X) {
            ms.mulPose(Axis.YP.rotationDegrees(90));
        }
        ms.scale(fit, fit, fit);
        // entity models are drawn upside down in their own space
        ms.mulPose(Axis.ZP.rotationDegrees(180.0F));
        ms.translate(-(min.x + max.x) / 2.0F, -(min.y + max.y) / 2.0F, -(min.z + max.z) / 2.0F);
        for (ItemStack stack : be.pieces()) {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
            Bone bone = piece == null ? null : rig.bone(piece.bone()).orElse(null);
            if (bone == null) {
                continue;
            }
            ms.pushPose();
            ms.translate(bone.offset().x / 16.0F, bone.offset().y / 16.0F, bone.offset().z / 16.0F);
            ms.mulPose(bone.rotation());
            int color = CarcassModels.rotColor(piece.freshness());
            if (tint != -1) {
                color = FastColor.ARGB32.multiply(color, tint);
            }
            java.util.List<CarcassLook.Coat> coats = piece.coats().stream().map(c -> new CarcassLook.Coat(c.layer(), c.texture(), c.tint())).toList();
            CarcassModels.drawBone(rig, bone, piece.texture(), coats, color, ms, buffer, light);
            ms.popPose();
        }
        ms.popPose();
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
