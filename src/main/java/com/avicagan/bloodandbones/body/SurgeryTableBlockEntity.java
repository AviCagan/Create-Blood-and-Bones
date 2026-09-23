package com.avicagan.bloodandbones.body;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** The one thing lying on the Surgery Table: a blade, an implant or a severed limb. */
public class SurgeryTableBlockEntity extends SmartBlockEntity {
    private ItemStack item = ItemStack.EMPTY;

    public SurgeryTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public ItemStack item() {
        return item;
    }

    public boolean put(ItemStack stack) {
        if (!item.isEmpty() || !Surgery.accepts(stack)) {
            return false;
        }
        item = stack.copyWithCount(1);
        notifyUpdate();
        return true;
    }

    public ItemStack take() {
        ItemStack out = item;
        item = ItemStack.EMPTY;
        notifyUpdate();
        return out;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (!item.isEmpty()) {
            tag.put("Item", item.save(registries));
        }
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        item = tag.contains("Item") ? ItemStack.parseOptional(registries, tag.getCompound("Item")) : ItemStack.EMPTY;
        super.read(tag, registries, clientPacket);
    }
}
