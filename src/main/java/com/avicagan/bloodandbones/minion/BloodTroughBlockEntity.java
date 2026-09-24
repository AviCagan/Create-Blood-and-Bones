package com.avicagan.bloodandbones.minion;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The trough's tank: any fluid tagged as blood (c:blood, so other mods' blood pours in too). Troughs in the
 * world keep themselves on a list per level, so a minion finds the nearest cheaply; one riding a contraption is
 * not on the list, and nothing drinks from it until it is set down again.
 */
public class BloodTroughBlockEntity extends SmartBlockEntity {
    public static final int CAPACITY = 4000;
    public static final TagKey<Fluid> BLOOD = FluidTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "blood"));
    private static final Map<ResourceKey<Level>, Set<BlockPos>> TROUGHS = new ConcurrentHashMap<>();

    private SmartFluidTankBehaviour tank;

    public BloodTroughBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // Create's tank: synced to clients with a smoothed level, the way its Item Drain and Basin draw theirs
        tank = SmartFluidTankBehaviour.single(this, CAPACITY);
        tank.getPrimaryHandler().setValidator(stack -> stack.getFluid().is(BLOOD));
        behaviours.add(tank);
    }

    public SmartFluidTankBehaviour behaviour() {
        return tank;
    }

    /** What pipes, Spouts and buckets see. */
    public IFluidHandler tank() {
        return tank.getCapability();
    }

    public int amount() {
        return tank.getPrimaryHandler().getFluidAmount();
    }

    /** Take up to this much blood; how much there was. */
    public int drink(int amount) {
        FluidStack out = tank.getPrimaryHandler().drain(amount, IFluidHandler.FluidAction.EXECUTE);
        return out.getAmount();
    }

    /** On its first tick in the world (Create's way), it goes on the level's list. */
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            TROUGHS.computeIfAbsent(level.dimension(), k -> ConcurrentHashMap.newKeySet()).add(worldPosition.immutable());
        }
    }

    /** Broken, or its chunk unloaded: off the list. */
    @Override
    public void invalidate() {
        super.invalidate();
        forget();
    }

    private void forget() {
        if (level != null && !level.isClientSide) {
            Set<BlockPos> set = TROUGHS.get(level.dimension());
            if (set != null) {
                set.remove(worldPosition);
            }
        }
    }

    /** Every trough in this level now. */
    public static Set<BlockPos> all(Level level) {
        return TROUGHS.getOrDefault(level.dimension(), Set.of());
    }

    /** Forget every trough (the server stopped: chunks are saved, not broken, so none took itself off). */
    public static void clear() {
        TROUGHS.clear();
    }
}
