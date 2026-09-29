package com.avicagan.bloodandbones.cooking;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.machine.PartFilteringBehaviour;
import com.avicagan.bloodandbones.machine.TableFilterSlot;
import com.avicagan.bloodandbones.registry.BBTags;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;

/**
 * The piece on a butcher's table: held like a piece in a jar until a Cleaver takes it apart, or a loose piece lying on
 * its top in the world (a body too heavy to carry, dragged there). Its filter picks which pieces it takes, so funnels
 * only put those on it and a Cleaver only chops those: a filtered station on a mixed line. By hand it gets what a hand
 * gets; a Deployer holding the Cleaver works it as a station, getting all of it.
 */
public class ButcherTableBlockEntity extends SpecimenJarBlockEntity {
    /** Height of the table top, in blocks. */
    public static final double TOP = 1.0;

    /** Which pieces it takes; see {@link com.avicagan.bloodandbones.machine.PartFilter}. */
    public PartFilteringBehaviour filtering;

    /** A carcass piece its filter lets on. */
    @Override
    protected boolean accepts(ItemStack stack) {
        return CarcassPieceItem.piece(stack) != null && (filtering == null || filtering.takes(stack));
    }

    /** For funnels and hoppers: one carcass piece goes on an empty table, and can be taken off again. */
    public final net.neoforged.neoforge.items.IItemHandler inventory = new net.neoforged.neoforge.items.IItemHandler() {
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
            return accepts(stack);
        }
    };

    public ButcherTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        filtering = new PartFilteringBehaviour(this, new TableFilterSlot(14, state -> true));
        behaviours.add(filtering);
    }

    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                com.avicagan.bloodandbones.registry.BBBlockEntities.BUTCHER_TABLE.get(), (be, side) -> be.inventory);
    }

    /** A loose piece lying on the top that the filter lets it chop (nothing jointed to it, with something to cut it into). */
    @Nullable
    public CarcassButchery.Lying lying(ServerLevel level) {
        for (CarcassButchery.Lying lying : CarcassButchery.lyingOn(level, getBlockPos(), TOP)) {
            if (!CarcassButchery.isAttached(lying.carcass(), lying.bone()) && CarcassButchery.hasYields(lying.carcass(), lying.bone())
                    && (filtering == null || filtering.takes(lying.carcass(), lying.bone()))) {
                return lying;
            }
        }
        return null;
    }

    /**
     * One chop: the piece on the table, or else the piece lying on it, comes apart into what butchering it gives, dropped
     * on the table top, with the wet sound and spray of a cut. The cleaver comes away bloody from a mob that bleeds.
     *
     * @param who whoever holds the cleaver (a player's own hand gets the hand's share, a Deployer's all of it)
     * @return false when there is nothing to chop, or its mob or part has nothing to cut it into
     */
    public boolean chop(ServerLevel level, ItemStack cleaver, @Nullable Player who) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(specimen());
        if (piece == null) {
            CarcassButchery.Lying lying = lying(level);
            if (lying == null) {
                return false;
            }
            CarcassButchery.byHand(who, () -> {
                CarcassButchery.butcher(level, lying.carcass(), lying.bone(), lying.at());
                return true;
            });
            if (Blood.bloody(lying.carcass())) {
                Blood.bloody(cleaver, level);
            }
            return true;
        }
        boolean cuttable = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(piece.entity())
                .map(table -> !table.part(piece.bone()).isEmpty()).orElse(false);
        if (!cuttable) {
            // no butchery table for this mob or this part: the piece stays whole rather than vanish
            return false;
        }
        // (a piece that could give something is used up even when this chop's rolls come up empty)
        List<ItemStack> yields = CarcassButchery.byHand(who, () -> CarcassButchery.pieceYields(level, piece));
        take();
        BlockPos pos = getBlockPos();
        Vector3d top = new Vector3d(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
        for (ItemStack stack : yields) {
            ItemEntity item = new ItemEntity(level, top.x, top.y, top.z, stack);
            item.setDeltaMovement(level.random.triangle(0.0, 0.08), 0.15, level.random.triangle(0.0, 0.08));
            level.addFreshEntity(item);
        }
        level.playSound(null, top.x, top.y, top.z, com.avicagan.bloodandbones.registry.BBSounds.CARCASS_CUT.get(), SoundSource.BLOCKS, 0.9F, 0.7F);
        boolean bleeds = BuiltInRegistries.ENTITY_TYPE.getOptional(piece.entity()).map(type -> !type.is(BBTags.BLOODLESS)).orElse(true);
        if (bleeds) {
            Blood.burst(level, top, 10, Blood.soul(piece.entity()));
            Blood.bloody(cleaver, level);
        }
        return true;
    }
}
