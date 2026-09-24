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

/**
 * What lies on the Surgery Table: one thing (a blade, an implant, a severed limb), or with the Assembly Frame a
 * minion being built.
 */
public class SurgeryTableBlockEntity extends SmartBlockEntity {
    private ItemStack item = ItemStack.EMPTY;
    private java.util.Optional<com.avicagan.bloodandbones.minion.MinionBuild> build = java.util.Optional.empty();

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

    /** The minion being built here, if one is. */
    public java.util.Optional<com.avicagan.bloodandbones.minion.MinionBuild> build() {
        return build;
    }

    public void setBuild(com.avicagan.bloodandbones.minion.MinionBuild build) {
        this.build = java.util.Optional.of(build);
        notifyUpdate();
    }

    public void clearBuild() {
        build = java.util.Optional.empty();
        notifyUpdate();
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
        build.flatMap(b -> com.avicagan.bloodandbones.minion.MinionBuild.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, b).result())
                .ifPresent(t -> tag.put("Build", t));
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        item = tag.contains("Item") ? ItemStack.parseOptional(registries, tag.getCompound("Item")) : ItemStack.EMPTY;
        build = tag.contains("Build") ? com.avicagan.bloodandbones.minion.MinionBuild.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("Build")).result()
                : java.util.Optional.empty();
        super.read(tag, registries, clientPacket);
    }
}
