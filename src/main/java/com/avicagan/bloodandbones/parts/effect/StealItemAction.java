package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.phys.Vec3;

/**
 * steal_item {} (docs/PARTS-AND-TRAITS.md section 5.5): snatches what a mob holds (its main hand, else its off hand) for
 * whoever's effect it is: into a player's inventory or a minion's, or dropped at their feet if there is no room. Never
 * from a player, and never from a minion (what a minion holds, its maker gave it).
 */
public record StealItemAction() implements EnchantmentEntityEffect {
    public static final MapCodec<StealItemAction> CODEC = MapCodec.unit(new StealItemAction());

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        if (!(entity instanceof Mob mob) || entity instanceof MinionEntity || entity == item.owner()) {
            return;
        }
        InteractionHand hand = !mob.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        ItemStack taken = mob.getItemInHand(hand);
        if (taken.isEmpty()) {
            return;
        }
        mob.setItemInHand(hand, ItemStack.EMPTY);
        give(item.owner(), mob, taken);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ITEM_PICKUP, mob.getSoundSource(), 0.8F, 0.7F);
        level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.FOX_BITE, mob.getSoundSource(), 0.6F, 0.8F);
    }

    private static void give(LivingEntity thief, Mob from, ItemStack stack) {
        if (thief instanceof Player player) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        } else if (thief instanceof MinionEntity minion) {
            ItemStack left = minion.carry(stack);
            if (!left.isEmpty()) {
                minion.spawnAtLocation(left);
            }
        } else {
            from.spawnAtLocation(stack);
        }
    }

    @Override
    public MapCodec<StealItemAction> codec() {
        return CODEC;
    }
}
