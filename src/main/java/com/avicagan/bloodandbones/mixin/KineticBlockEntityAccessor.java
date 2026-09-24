package com.avicagan.bloodandbones.mixin;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Analytical Lens reads a machine's network stress and capacity, which Create sends to clients but keeps protected. */
@Mixin(value = KineticBlockEntity.class, remap = false)
public interface KineticBlockEntityAccessor {
    @Accessor("stress")
    float bloodandbones$stress();

    @Accessor("capacity")
    float bloodandbones$capacity();
}
