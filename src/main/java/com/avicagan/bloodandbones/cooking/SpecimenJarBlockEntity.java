package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** The piece in the jar. It keeps: a carried piece does not rot, and neither does one in a jar. */
public class SpecimenJarBlockEntity extends SmartBlockEntity {
    private ItemStack specimen = ItemStack.EMPTY;

    public SpecimenJarBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public ItemStack specimen() {
        return specimen;
    }

    public boolean put(ItemStack stack) {
        if (!specimen.isEmpty() || CarcassPieceItem.piece(stack) == null) {
            return false;
        }
        specimen = stack;
        notifyUpdate();
        return true;
    }

    public ItemStack take() {
        ItemStack out = specimen;
        specimen = ItemStack.EMPTY;
        notifyUpdate();
        return out;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        if (!specimen.isEmpty()) {
            tag.put("Specimen", specimen.save(registries));
        }
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        specimen = tag.contains("Specimen") ? ItemStack.parseOptional(registries, tag.getCompound("Specimen")) : ItemStack.EMPTY;
        super.read(tag, registries, clientPacket);
    }
}
