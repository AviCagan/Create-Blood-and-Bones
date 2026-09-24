package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.minion.BloodTroughBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;

/** The blood in a trough, its surface rising and falling with the level as the Bleeding Rack's does. */
public class BloodTroughRenderer extends SmartBlockEntityRenderer<BloodTroughBlockEntity> {
    /** Inside of the trough, in block units; matches BloodTroughBlock's shape. */
    private static final float MIN = 2 / 16f;
    private static final float MAX = 14 / 16f;
    private static final float FLOOR = 2 / 16f;
    private static final float DEPTH = 7.5f / 16f;

    public BloodTroughRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BloodTroughBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        if (be.behaviour() == null) {
            return;
        }
        TankSegment segment = be.behaviour().getPrimaryTank();
        FluidStack fluid = segment.getRenderedFluid();
        float fill = Mth.clamp(segment.getFluidLevel().getValue(partialTicks), 0, 1);
        if (fluid.isEmpty() || fill < 1 / 256f) {
            return;
        }
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, MIN, FLOOR, MIN, MAX, FLOOR + DEPTH * fill, MAX, buffer, ms, light, false, false);
    }
}
