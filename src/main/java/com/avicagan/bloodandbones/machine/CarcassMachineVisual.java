package com.avicagan.bloodandbones.machine;

import com.simibubi.create.AllPartialModels;
import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;

/** With Flywheel: the same half shaft, instanced. */
public class CarcassMachineVisual extends OrientedRotatingVisual<CarcassMachineBlockEntity> {
    public CarcassMachineVisual(VisualizationContext context, CarcassMachineBlockEntity be, float partialTick) {
        super(context, be, partialTick, Direction.SOUTH, Direction.DOWN, Models.partial(AllPartialModels.SHAFT_HALF));
    }
}
