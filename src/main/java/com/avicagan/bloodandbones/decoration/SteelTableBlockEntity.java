package com.avicagan.bloodandbones.decoration;

import com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity;
import com.avicagan.bloodandbones.registry.BBBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

/** What lies on a steel table: one item, any item, kept like the jar's. */
public class SteelTableBlockEntity extends SpecimenJarBlockEntity {
    /**
     * For funnels and hoppers: one item goes on an empty table and can be taken off again. Not a plain
     * ItemStackHandler, so Create does not make it a contraption's storage: the item rides along as block data.
     */
    public final IItemHandler inventory = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return specimen();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!specimen().isEmpty() || !isItemValid(slot, stack)) {
                return stack;
            }
            if (!simulate) {
                put(stack.copyWithCount(1));
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || specimen().isEmpty()) {
                return ItemStack.EMPTY;
            }
            return simulate ? specimen().copy() : take();
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

    public SteelTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, BBBlockEntities.STEEL_TABLE.get(), (be, side) -> be.inventory);
    }
}
