package com.avicagan.bloodandbones.cooking;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** The piece on a butcher's hook: held just like a piece in a Specimen Jar. */
public class ButcherHookBlockEntity extends SpecimenJarBlockEntity {
    public ButcherHookBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
}
