package com.avicagan.bloodandbones.backtank;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import java.util.List;

/** A Fluid Backtank set down: its tank, open to pipes on every side. */
public class FluidBacktankBlockEntity extends SmartBlockEntity {
    private final FluidTank tank;

    public FluidBacktankBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        tank = new FluidTank(state.getValue(FluidBacktankBlock.TIER).capacity()) {
            @Override
            protected void onContentsChanged() {
                setChanged();
                sendData();
            }
        };
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public FluidTank tank() {
        return tank;
    }

    public void setFluid(FluidStack fluid) {
        tank.setFluid(fluid.copyWithAmount(Math.min(fluid.getAmount(), tank.getCapacity())));
        setChanged();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        super.read(tag, registries, clientPacket);
    }
}
