package com.avicagan.bloodandbones.machine;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Without Flywheel: the half shaft under the machine, spinning (Create's model points south, turned down). */
public class CarcassMachineRenderer extends KineticBlockEntityRenderer<CarcassMachineBlockEntity> {
    public CarcassMachineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /** The filter slot on the top; the shaft itself only without Flywheel (the visual draws it with). */
    @Override
    protected void renderSafe(CarcassMachineBlockEntity be, float partialTicks, com.mojang.blaze3d.vertex.PoseStack ms,
                              net.minecraft.client.renderer.MultiBufferSource buffer, int light, int overlay) {
        com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(CarcassMachineBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacing(AllPartialModels.SHAFT_HALF, state, Direction.DOWN);
    }
}
