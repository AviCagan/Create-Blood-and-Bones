package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.Trigger;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import org.jetbrains.annotations.Nullable;

/**
 * More room in a minion (docs/PARTS-AND-TRAITS.md section 5.4, storage): {@code slots} more than its torso gives, up to
 * the 54 a minion can hold. With {@code chest}, only once its maker has fitted it with a chest (a chest used on it), as a
 * donkey takes one: beast of burden's +18. When what it can carry shrinks (a part taken off, its data changed), what no
 * longer fits moves into free slots, or falls out onto the ground; nothing is lost.
 */
public record StorageEffect(LevelBasedValue slots, boolean chest) implements TraitEffect.Effect {
    public static final MapCodec<StorageEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("slots").forGetter(StorageEffect::slots),
            Codec.BOOL.optionalFieldOf("chest", false).forGetter(StorageEffect::chest)
    ).apply(i, StorageEffect::new));

    /** Kept in the minion's own saved data, so it goes with it when it is folded up. */
    private static final String CHEST = "bloodandbones:chest";

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** The slots its passive storage effects add now (those needing a chest only with one fitted). */
    static int extra(MinionEntity minion) {
        int extra = 0;
        boolean chested = hasChest(minion);
        for (ActiveTraits.Found<StorageEffect> found : ActiveTraits.of(minion).find(StorageEffect.class)) {
            if (found.facet().trigger() == Trigger.PASSIVE && (!found.effect().chest() || chested)) {
                extra += Math.max(0, (int) found.effect().slots().calculate(found.entry().level()));
            }
        }
        return extra;
    }

    public static boolean hasChest(MinionEntity minion) {
        return minion.getPersistentData().getBoolean(CHEST);
    }

    /** Whether one of its storage effects takes a chest. */
    private static boolean takesChest(MinionEntity minion) {
        for (ActiveTraits.Found<StorageEffect> found : ActiveTraits.of(minion).find(StorageEffect.class)) {
            if (found.effect().chest()) {
                return true;
            }
        }
        return false;
    }

    /** Its maker uses a chest on a minion that takes one and has none yet: it is strapped on. */
    @Nullable
    static InteractionResult fitChest(MinionEntity minion, Player player, ItemStack held) {
        if (!held.is(Items.CHEST) || !minion.isMaker(player) || hasChest(minion) || !takesChest(minion)) {
            return null;
        }
        if (!minion.level().isClientSide) {
            minion.getPersistentData().putBoolean(CHEST, true);
            held.consume(1, player);
            minion.level().playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.DONKEY_CHEST, minion.getSoundSource(), 1.0F, 0.8F);
        }
        return InteractionResult.sidedSuccess(minion.level().isClientSide);
    }

    /**
     * What it carries beyond the slots it has now moves into a free slot, or falls out; a chest it no longer takes falls
     * off. Never deletes anything.
     */
    static void settle(MinionEntity minion) {
        if (hasChest(minion) && !takesChest(minion)) {
            minion.getPersistentData().remove(CHEST);
            minion.spawnAtLocation(Items.CHEST);
        }
        int slots = minion.slots();
        for (int i = slots; i < minion.inventory.getContainerSize(); i++) {
            ItemStack stack = minion.inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            minion.inventory.setItem(i, ItemStack.EMPTY);
            ItemStack left = minion.carry(stack);
            if (!left.isEmpty()) {
                minion.spawnAtLocation(left);
            }
        }
    }
}
