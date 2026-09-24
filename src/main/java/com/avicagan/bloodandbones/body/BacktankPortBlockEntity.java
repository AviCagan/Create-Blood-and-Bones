package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.EmptyFluidHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Backtank Port: to pipes on its nozzle side it is the backtank of whoever crouches next to it with a
 * Port Arm (crouching is plugging in: nobody is filled or emptied without meaning to be), so a Create pump
 * pumping into it fills their tank and one pumping out of it empties it. With nobody there it is shut.
 */
public class BacktankPortBlockEntity extends SmartBlockEntity {
    private final IFluidHandler handler = new Proxy();

    public BacktankPortBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public IFluidHandler handler() {
        return handler;
    }

    /** Whether this player can plug into a port: a working Port Arm, and a backtank on. */
    public static boolean canUse(Player player) {
        return !player.isSpectator() && player.isAlive() && BodyEffects.body(player).has(ImplantSpec.Ability.PORT, player)
                && !FluidBacktankItem.wornBy(player).isEmpty();
    }

    /** The worn tank of a player who can use a port, as a fluid handler; null if they cannot. */
    @Nullable
    public static IFluidHandler tankOf(Player player) {
        if (!canUse(player)) {
            return null;
        }
        ItemStack tank = FluidBacktankItem.wornBy(player);
        return tank.getCapability(Capabilities.FluidHandler.ITEM);
    }

    private long lookedAt = Long.MIN_VALUE;
    @Nullable
    private Player plugged;

    /** Who is plugged in: looked for once a tick, since Create asks the handler many times a tick. */
    private IFluidHandler target() {
        if (level == null) {
            return EmptyFluidHandler.INSTANCE;
        }
        if (level.getGameTime() != lookedAt) {
            lookedAt = level.getGameTime();
            plugged = null;
            for (Player player : level.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(1.0))) {
                if (player.isCrouching() && canUse(player)) {
                    plugged = player;
                    break;
                }
            }
        }
        IFluidHandler tank = plugged == null ? null : tankOf(plugged);
        return tank == null ? EmptyFluidHandler.INSTANCE : tank;
    }

    private class Proxy implements IFluidHandler {
        @Override
        public int getTanks() {
            return target().getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            IFluidHandler target = target();
            return tank < target.getTanks() ? target.getFluidInTank(tank) : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            IFluidHandler target = target();
            return tank < target.getTanks() ? target.getTankCapacity(tank) : 0;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            IFluidHandler target = target();
            return tank < target.getTanks() && target.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return target().fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return target().drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return target().drain(maxDrain, action);
        }
    }
}
