package com.avicagan.bloodandbones.decoration;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** What lies on a steel table, turned a different way on each table of a run. */
public class SteelTableRenderer extends SafeBlockEntityRenderer<SteelTableBlockEntity> {
    /** How big a piece is drawn, its longest side in blocks. */
    private static final float SIZE = 0.8F;

    public SteelTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** A piece lies above the block, so the block's own box would cull it too soon. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SteelTableBlockEntity be) {
        return new net.minecraft.world.phys.AABB(be.getBlockPos()).expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    protected void renderSafe(SteelTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        if (be.specimen().isEmpty() || be.getLevel() == null) {
            return;
        }
        ms.pushPose();
        ms.translate(0.5F, SteelTableBlock.TOP / 16.0F, 0.5F);
        ms.mulPose(Axis.YP.rotationDegrees((be.getBlockPos().hashCode() & 3) * 90.0F + 20.0F));
        ShownItem.lying(be.specimen(), SIZE, be.getLevel(), ms, buffer, light);
        ms.popPose();
    }
}
