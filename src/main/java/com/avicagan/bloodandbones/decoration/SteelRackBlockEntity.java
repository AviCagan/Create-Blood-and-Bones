package com.avicagan.bloodandbones.decoration;

import com.avicagan.bloodandbones.registry.BBBlockEntities;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/** The four things on a steel rack's shelves, one item on each place. */
public class SteelRackBlockEntity extends SmartBlockEntity {
    public static final int SLOTS = 4;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);

    /**
     * For funnels and hoppers: one item a place, filled from the lower left. Not a plain ItemStackHandler,
     * so Create does not make it a contraption's storage: the items ride along as block data.
     */
    public final IItemHandler inventory = new IItemHandler() {
        @Override
        public int getSlots() {
            return SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return item(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !item(slot).isEmpty()) {
                return stack;
            }
            if (!simulate) {
                put(slot, stack.copyWithCount(1));
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || item(slot).isEmpty()) {
                return ItemStack.EMPTY;
            }
            return simulate ? item(slot).copy() : take(slot);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty();
        }
    };

    public SteelRackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BBBlockEntities.STEEL_RACK.get(), (be, side) -> be.inventory);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public ItemStack item(int slot) {
        return slot >= 0 && slot < SLOTS ? items.get(slot) : ItemStack.EMPTY;
    }

    public boolean put(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOTS || stack.isEmpty() || !items.get(slot).isEmpty()) {
            return false;
        }
        items.set(slot, stack);
        notifyUpdate();
        return true;
    }

    public ItemStack take(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return ItemStack.EMPTY;
        }
        ItemStack out = items.get(slot);
        items.set(slot, ItemStack.EMPTY);
        notifyUpdate();
        return out;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        ListTag list = new ListTag();
        for (int slot = 0; slot < SLOTS; slot++) {
            if (!items.get(slot).isEmpty()) {
                CompoundTag entry = new CompoundTag();
                entry.putByte("Slot", (byte) slot);
                entry.put("Item", items.get(slot).save(registries));
                list.add(entry);
            }
        }
        tag.put("Items", list);
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        items.replaceAll(stack -> ItemStack.EMPTY);
        for (Tag element : tag.getList("Items", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            int slot = entry.getByte("Slot");
            if (slot >= 0 && slot < SLOTS) {
                items.set(slot, ItemStack.parseOptional(registries, entry.getCompound("Item")));
            }
        }
        super.read(tag, registries, clientPacket);
    }
}
