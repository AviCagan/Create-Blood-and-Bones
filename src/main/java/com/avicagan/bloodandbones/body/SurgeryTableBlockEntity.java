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

    /** With the Surgical Rig: which parts it takes (see SurgicalRig and com.avicagan.bloodandbones.machine.PartFilter). */
    public com.avicagan.bloodandbones.machine.PartFilteringBehaviour filtering;
    /** The game time the rig's last cut is done and the next may start (SurgicalRig#PAUSE); not saved. */
    public long nextCut;

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filtering = new com.avicagan.bloodandbones.machine.PartFilteringBehaviour(this, new com.avicagan.bloodandbones.machine.TableFilterSlot(12.5F,
                state -> state.hasProperty(SurgeryTableBlock.ATTACHMENT) && state.getValue(SurgeryTableBlock.ATTACHMENT) == TableAttachment.SURGICAL)).withOrgans();
        filtering.onlyActiveWhen(() -> getBlockState().hasProperty(SurgeryTableBlock.ATTACHMENT)
                && getBlockState().getValue(SurgeryTableBlock.ATTACHMENT) == TableAttachment.SURGICAL);
        behaviours.add(filtering);
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

    /**
     * The Surgical Rig comes off, and its filter with it: a Create filter in the slot goes back to whoever took the rig off
     * (or drops), and the slot is cleared, so nothing is left in a slot that is no longer there.
     */
    public void takeFilter(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.player.Player player) {
        ItemStack filter = filtering.getFilter();
        if (filter.getItem() instanceof com.simibubi.create.content.logistics.filter.FilterItem && level != null && !level.isClientSide) {
            if (player != null) {
                player.getInventory().placeItemBackInInventory(filter.copy());
            } else {
                net.minecraft.world.level.block.Block.popResource(level, worldPosition, filter.copy());
            }
        }
        filtering.setFilter(ItemStack.EMPTY);
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
