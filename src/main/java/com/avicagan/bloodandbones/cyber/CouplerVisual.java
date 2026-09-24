package com.avicagan.bloodandbones.cyber;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;

/** With Flywheel: the half shaft into the machine, instanced (the rod back to the arm is the renderer's). */
public class CouplerVisual extends OrientedRotatingVisual<CouplerBlockEntity> {
    public CouplerVisual(VisualizationContext context, CouplerBlockEntity be, float partialTick) {
        super(context, be, partialTick, Direction.SOUTH, be.getBlockState().getValue(CouplerBlock.FACING), Models.partial(AllPartialModels.SHAFT_HALF));
    }
}
