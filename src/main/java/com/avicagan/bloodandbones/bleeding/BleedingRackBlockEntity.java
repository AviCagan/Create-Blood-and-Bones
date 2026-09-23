package com.avicagan.bloodandbones.bleeding;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import java.util.List;

/**
 * A drip tray under a hanging carcass. Holds one 4000 mB tank. Create pipes and pumps can pull from its sides and
 * bottom but cannot fill it; only {@link #collect} puts fluid in.
 */
public class BleedingRackBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public static final int CAPACITY = 4000;

    SmartFluidTankBehaviour tank;
    /** RPM of the fastest fan blowing through the block above, refreshed on the server every lazy tick. Not saved. */
    private float airflowAbove;

    public BleedingRackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        setLazyTickRate(20);
    }

    /** Called from the mod bus RegisterCapabilitiesEvent; see BBBlockEntities.registerCapabilities. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                BBBlockEntities.BLEEDING_RACK.get(),
                // the top is where the blood drips in, so pipes connect to the sides and the bottom only;
                // a null side (goggles, FluidHelper, comparators) sees the tank too
                (be, side) -> side == Direction.UP ? null : be.tank.getCapability());
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tank = SmartFluidTankBehaviour.single(this, CAPACITY)
                .allowExtraction()
                .forbidInsertion();
        behaviours.add(tank);
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (level == null || level.isClientSide) {
            return;
        }
        airflowAbove = FanAirflow.fanSpeedAt(level, worldPosition.above());
    }

    /**
     * Puts fluid into the tank, bypassing the pipe-facing insertion lock. Returns how much went in. The tank syncs
     * itself to clients (at most every 8 ticks) and marks the block entity changed.
     */
    public int collect(FluidStack stack, FluidAction action) {
        if (stack.isEmpty()) {
            return 0;
        }
        // getPrimaryHandler() is the raw SmartFluidTank, which ignores forbidInsertion()
        return tank.getPrimaryHandler().fill(stack, action);
    }

    public SmartFluidTankBehaviour getTank() {
        return tank;
    }

    public FluidStack getFluid() {
        return tank.getPrimaryHandler().getFluid();
    }

    public float airflowAbove() {
        return airflowAbove;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return containedFluidTooltip(tooltip, isPlayerSneaking, tank.getCapability());
    }
}
