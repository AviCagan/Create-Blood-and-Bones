package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;

/** Draws the tray's fluid as a box whose top rises with the fill level, the way Create's item drain does. */
public class BleedingRackRenderer extends SmartBlockEntityRenderer<BleedingRackBlockEntity> {
    /** Inside of the tray, in block units; matches BleedingRackBlock.SHAPE. */
    private static final float MIN = 1 / 16f;
    private static final float MAX = 15 / 16f;
    private static final float FLOOR = 2 / 16f;
    private static final float DEPTH = 5.5f / 16f; // a full tray stops half a pixel under the 8 px rim

    public BleedingRackRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(BleedingRackBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer,
                              int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);
        SmartFluidTankBehaviour tank = be.getTank();
        if (tank == null) {
            return;
        }
        TankSegment segment = tank.getPrimaryTank();
        // the rendered fluid outlives an emptied tank so the surface can sink smoothly instead of popping out
        FluidStack fluid = segment.getRenderedFluid();
        float fill = Mth.clamp(segment.getFluidLevel().getValue(partialTicks), 0, 1);
        if (fluid.isEmpty() || fill < 1 / 256f) {
            return;
        }
        float top = FLOOR + DEPTH * fill;
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(fluid, MIN, FLOOR, MIN, MAX, top, MAX,
                buffer, ms, light, false, false);
    }
}
