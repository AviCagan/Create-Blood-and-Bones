package com.avicagan.bloodandbones.machine;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Create's filter slot, holding a {@link PartFilter}: only spawn eggs, carcass pieces and Create filters go in, and a
 * Deployer's stand-in player cannot set it. The four machines, the Butcher's Table and the Surgical Rig carry one.
 */
public class PartFilteringBehaviour extends FilteringBehaviour {
    public PartFilteringBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
        super(be, slot);
        withPredicate(PartFilter::allowed);
    }

    /** Whether it lets its station take this part of a carcass (asked of the filter as the slot keeps it read, not read again). */
    public boolean takes(com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcass, String bone) {
        return PartFilter.takes(getWorld(), filter, carcass, bone);
    }

    /** Whether it lets its station take this part, given as the item it would be. */
    public boolean takes(ItemStack part) {
        return PartFilter.takes(getWorld(), filter, part);
    }

    /**
     * A Deployer's stand-in player is let through Create's slot hit test: it would set the filter instead of putting a
     * piece on the station or taking its output.
     */
    @Override
    public boolean mayInteract(Player player) {
        return !(player instanceof FakePlayer);
    }

    /**
     * Create hands the old filter back before it asks whether the new item may go in: an item that may not has to be
     * turned away before that, or each refused click would copy the old filter.
     */
    @Override
    public boolean canShortInteract(ItemStack toApply) {
        return super.canShortInteract(toApply) && (toApply.isEmpty() || PartFilter.allowed(toApply));
    }

    /** Turned away, with Create's own "invalid item" message and sound. */
    @Override
    public void onShortInteract(Player player, InteractionHand hand, Direction side, BlockHitResult hitResult) {
        ItemStack toApply = player.getItemInHand(hand);
        if (!toApply.isEmpty() && !PartFilter.allowed(toApply)) {
            if (!player.level().isClientSide) {
                player.displayClientMessage(CreateLang.translateDirect("logistics.filter.invalid_item"), true);
                AllSoundEvents.DENY.playOnServer(player.level(), player.blockPosition(), 1, 1);
            }
            return;
        }
        super.onShortInteract(player, hand, side, hitResult);
    }

    @Override
    public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate) {
        if (tag.contains("Filter")) {
            ItemStack copied = ItemStack.parseOptional(registries, tag.getCompound("Filter"));
            if (!copied.isEmpty() && !PartFilter.allowed(copied)) {
                return false;
            }
        }
        return super.readFromClipboard(registries, tag, player, side, simulate);
    }
}
