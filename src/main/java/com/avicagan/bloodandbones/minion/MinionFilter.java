package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A brass minion's filter slot (docs/PARTS-AND-TRAITS.md section 6.6, brass only: flesh has its hides, its mending
 * and its organs' produce instead). What is in it limits what the minion picks up (a courier, farmer or scavenger), what
 * it reaps (a farmer) and what it goes for (a hunter, herder, guard or sentry), tested as Create's own filter slots test
 * ({@link FilterItemStack}), so a Filter's list, an Attribute Filter's attributes or any plain item work as they do on a
 * funnel. A mob is asked about as its spawn egg, as the carcass machines' filters ask: an egg or a carcass piece in the
 * filter (or in a Filter's list) names that mob, and an Attribute Filter is asked about the mob's egg.
 * <p>
 * Its maker crouches and uses a filter (or any item) on it, as on one of Create's filter slots: a Filter or Attribute
 * Filter goes in itself, the one there before coming back; anything else puts a copy there and stays in hand. A
 * crouching Wrench takes it out. It stays with the minion folded, drops with it where minions may die, and comes back
 * when it is taken apart on the table.
 */
public final class MinionFilter {
    private ItemStack stack = ItemStack.EMPTY;
    private FilterItemStack test = FilterItemStack.empty();

    public ItemStack stack() {
        return stack;
    }

    public boolean isEmpty() {
        return stack.isEmpty();
    }

    /** Put this in the slot (one of it), or empty it. */
    public void set(ItemStack filter) {
        stack = filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1);
        // Create trims a filter's enchantments off the stack it is given: its own copy
        test = FilterItemStack.of(stack.copy());
    }

    /** Whether it may take this item (nothing in the slot lets everything through). */
    public boolean allows(Level level, ItemStack item) {
        return stack.isEmpty() || test.test(level, item);
    }

    /** Whether it may go for this mob. */
    public boolean allows(Level level, Entity mob) {
        return stack.isEmpty() || matches(level, test, mob);
    }

    /** Whether it may reap this crop: its seed, or something it drops, passes. */
    public boolean allowsCrop(Level level, BlockState state, net.minecraft.core.BlockPos pos) {
        if (stack.isEmpty() || allows(level, state.getBlock().getCloneItemStack(level, pos, state))) {
            return true;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            for (ItemStack drop : Block.getDrops(state, server, pos, null)) {
                if (allows(level, drop)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A spawn egg or a carcass piece means that mob; a list asks the same of each entry, as a whitelist or a blacklist;
     * anything else (an Attribute Filter) is asked about the mob's spawn egg. A mob with no egg passes only a blacklist.
     */
    private static boolean matches(Level level, FilterItemStack filter, Entity mob) {
        ItemStack item = filter.item();
        if (item.getItem() instanceof SpawnEggItem egg) {
            return egg.getType(item) == mob.getType();
        }
        if (item.is(BBItems.CARCASS_PIECE.get())) {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(item);
            return piece == null || piece.entity().equals(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()));
        }
        if (filter instanceof FilterItemStack.ListFilterItemStack list) {
            for (FilterItemStack entry : list.containedItems) {
                if (matches(level, entry, mob)) {
                    return !list.isBlacklist;
                }
            }
            return list.isBlacklist;
        }
        SpawnEggItem egg = SpawnEggItem.byId(mob.getType());
        return egg != null && filter.test(level, new ItemStack(egg));
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!stack.isEmpty()) {
            tag.put("Filter", stack.save(registries));
        }
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        set(tag.contains("Filter", Tag.TAG_COMPOUND) ? ItemStack.parseOptional(registries, tag.getCompound("Filter")) : ItemStack.EMPTY);
    }

    /** What comes back out of the slot when it is emptied: a Filter or Attribute Filter itself; a plain item was only a copy. */
    public ItemStack takeOut() {
        ItemStack out = stack.getItem() instanceof FilterItem ? stack.copy() : ItemStack.EMPTY;
        set(ItemStack.EMPTY);
        return out;
    }

    /** Not for the slot: what already does something to a mob (a saddle, a lead, a name tag) and a folded minion. */
    private static boolean settable(ItemStack stack) {
        return !stack.is(Items.SADDLE) && !stack.is(Items.LEAD) && !stack.is(Items.NAME_TAG) && !(stack.getItem() instanceof DormantMinionItem);
    }

    /**
     * Its maker crouching with an item: into the slot (brass only), or with a Wrench, out of it. Null when this is not
     * that, so the click goes on to what else it might be.
     */
    @Nullable
    static InteractionResult interact(MinionEntity minion, Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || held.isEmpty() || !settable(held)) {
            return null;
        }
        boolean filterItem = held.getItem() instanceof FilterItem;
        if (minion.level().isClientSide) {
            // the client does not know who made it: a filter meant for the slot is not opened as well
            return minion.cybernetic() || filterItem ? InteractionResult.SUCCESS : null;
        }
        if (!minion.isMaker(player)) {
            return null;
        }
        if (!minion.cybernetic()) {
            if (!filterItem) {
                return null;
            }
            player.displayClientMessage(Component.translatable("bloodandbones.minion.filter_brass_only"), true);
            return InteractionResult.CONSUME;
        }
        MinionFilter filter = minion.filter();
        if (held.is(AllItems.WRENCH.get())) {
            if (filter.isEmpty()) {
                player.displayClientMessage(Component.translatable("bloodandbones.minion.filter_none"), true);
                return InteractionResult.CONSUME;
            }
            ItemStack out = filter.takeOut();
            if (!out.isEmpty()) {
                player.getInventory().placeItemBackInInventory(out);
            }
            player.displayClientMessage(Component.translatable("bloodandbones.minion.filter_taken"), true);
            minion.level().playSound(null, minion.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.NEUTRAL, 0.6F, 0.8F);
            return InteractionResult.CONSUME;
        }
        ItemStack old = filter.takeOut();
        if (!old.isEmpty()) {
            player.getInventory().placeItemBackInInventory(old);
        }
        filter.set(held);
        if (filterItem && !player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
        player.displayClientMessage(Component.translatable("bloodandbones.minion.filter_set", filter.stack().getHoverName()), true);
        minion.level().playSound(null, minion.blockPosition(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 0.6F, 0.8F);
        return InteractionResult.CONSUME;
    }
}
