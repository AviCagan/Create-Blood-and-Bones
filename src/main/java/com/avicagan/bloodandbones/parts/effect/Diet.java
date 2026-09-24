package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the diet effect ({@code TraitEffects.DietEffect}, docs/PARTS-AND-TRAITS.md section 5.4) does when something is
 * eaten: a player's food made safe, more filling, curing or poisonous, and things that are no food at all (seeds, bamboo,
 * mushrooms) made food. A flesh minion forages the foods its diet names for blood, from what it carries and from the
 * grass or mushrooms it stands on, but only until it is half full; a trough is for the rest. Graze (grass eaten
 * bare-handed) is in {@code TraitEvents#onUseBlock}.
 */
public final class Diet {
    /** A diet with no foods of its own named (a toxic one) poisons for this long. */
    private static final int POISON_TICKS = 200;

    private Diet() {
    }

    /** Whether the stack is one of these (item ids or "#tags"); with none named, whether it is food at all. */
    public static boolean matches(List<String> foods, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (foods.isEmpty()) {
            return stack.getFoodProperties(null) != null;
        }
        for (String food : foods) {
            if (food.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(food.substring(1));
                if (tag != null && stack.is(TagKey.create(Registries.ITEM, tag))) {
                    return true;
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(food);
                if (id != null && stack.getItemHolder().is(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a block in the world is one of these foods (a block id or "#tag" among them). */
    static boolean matches(List<String> foods, BlockState state) {
        for (String food : foods) {
            if (food.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(food.substring(1));
                if (tag != null && state.is(TagKey.create(Registries.BLOCK, tag))) {
                    return true;
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(food);
                if (id != null && state.getBlockHolder().is(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The host has eaten this: each diet effect naming it does its part, if its condition holds. Safe leaves off the
     * food's harmful effects, bonus saturation fills more, cure one clears one harmful effect, toxic poisons; any of them
     * may add its own effect.
     */
    static void eaten(LivingEntity host, ItemStack food) {
        FoodProperties properties = food.getFoodProperties(host);
        for (ActiveTraits.Found<TraitEffects.DietEffect> found : ActiveTraits.of(host).find(TraitEffects.DietEffect.class)) {
            TraitEffects.DietEffect diet = found.effect();
            if (!matches(diet.foods(), food) || !UpkeepEffects.holds(host, found)) {
                continue;
            }
            switch (diet.effect()) {
                case "safe" -> {
                    if (properties != null) {
                        for (FoodProperties.PossibleEffect possible : properties.effects()) {
                            Holder<MobEffect> effect = possible.effect().getEffect();
                            if (effect.value().getCategory() == MobEffectCategory.HARMFUL) {
                                host.removeEffect(effect);
                            }
                        }
                    }
                }
                case "bonus_saturation" -> {
                    if (host instanceof Player player) {
                        float own = properties == null ? 0.0F : properties.saturation();
                        float more = (diet.saturation() + diet.amount() * own) * TraitEffects.strength();
                        var data = player.getFoodData();
                        data.setSaturation(Math.min(data.getSaturationLevel() + more, data.getFoodLevel()));
                    }
                }
                case "cure_one" -> {
                    for (MobEffectInstance active : List.copyOf(host.getActiveEffects())) {
                        if (active.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                            host.removeEffect(active.getEffect());
                            break;
                        }
                    }
                }
                case "toxic" -> {
                    host.addEffect(diet.mobEffect().map(MobEffectInstance::new).orElseGet(() -> new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 1)));
                    // a sick, wet retch
                    host.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, host.getSoundSource(), 0.6F, 0.5F);
                    host.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_SQUISH, host.getSoundSource(), 0.5F, 0.5F);
                    continue;
                }
                default -> {
                }
            }
            diet.mobEffect().ifPresent(effect -> host.addEffect(new MobEffectInstance(effect)));
        }
    }

    /**
     * A player uses something their diet makes food but the game does not (seeds, bamboo): while they are hungry, it is
     * eaten at once, for the diet's hunger and saturation, and anything else the diet does with it.
     *
     * @return whether it was eaten (on either side: the client guesses the same from the armour it sees)
     */
    static boolean eatEdible(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty() || held.getFoodProperties(player) != null || player.isSpectator() || !player.canEat(false)) {
            return false;
        }
        TraitEffects.DietEffect edible = null;
        for (ActiveTraits.Found<TraitEffects.DietEffect> found : ActiveTraits.of(player).find(TraitEffects.DietEffect.class)) {
            if ("edible".equals(found.effect().effect()) && !found.effect().foods().isEmpty() && matches(found.effect().foods(), held)
                    && (player.level().isClientSide || UpkeepEffects.holds(player, found))) {
                edible = found.effect();
                break;
            }
        }
        if (edible == null) {
            return false;
        }
        if (player.level() instanceof ServerLevel level) {
            ItemStack eaten = held.copyWithCount(1);
            player.getFoodData().eat(new FoodProperties(Math.max(0, edible.hunger()), Math.max(0.0F, edible.saturation()), false, 1.6F,
                    Optional.empty(), List.of()));
            held.consume(1, player);
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EAT, player.getSoundSource(), 0.8F,
                    0.8F + level.random.nextFloat() * 0.3F);
            crumbs(level, player, eaten);
            player.gameEvent(GameEvent.EAT);
            eaten(player, eaten);
        }
        return true;
    }

    /**
     * An awake flesh minion under half full eats the first food its diet forages that it carries (or stands on, where
     * mobs may trample: grass at its feet, a grass or mycelium block under it goes to dirt), for the diet's
     * {@code forage_mb}, never past half full.
     *
     * @return whether it ate
     */
    static boolean forage(MinionEntity minion) {
        List<TraitEffects.DietEffect> diets = new ArrayList<>();
        for (ActiveTraits.Found<TraitEffects.DietEffect> found : ActiveTraits.of(minion).find(TraitEffects.DietEffect.class)) {
            if (found.effect().forageMb() > 0 && !found.effect().foods().isEmpty() && UpkeepEffects.holds(minion, found)) {
                diets.add(found.effect());
            }
        }
        float half = minion.stats().reservoir() * 0.5F;
        if (diets.isEmpty() || minion.power() >= half) {
            return false;
        }
        ServerLevel level = (ServerLevel) minion.level();
        int slots = minion.slots();
        for (TraitEffects.DietEffect diet : diets) {
            for (int i = 0; i < slots; i++) {
                ItemStack stack = minion.inventory.getItem(i);
                if (!stack.isEmpty() && matches(diet.foods(), stack)) {
                    ItemStack eaten = stack.copyWithCount(1);
                    stack.shrink(1);
                    minion.inventory.setChanged();
                    gulp(level, minion, eaten, Math.min(diet.forageMb(), half - minion.power()));
                    return true;
                }
            }
        }
        if (!EventHooks.canEntityGrief(level, minion)) {
            return false;
        }
        BlockPos feet = minion.blockPosition();
        BlockPos under = feet.below();
        for (TraitEffects.DietEffect diet : diets) {
            BlockState at = level.getBlockState(feet);
            BlockState below = level.getBlockState(under);
            if (!at.isAir() && at.getCollisionShape(level, feet).isEmpty() && matches(diet.foods(), at)) {
                level.destroyBlock(feet, false, minion);
                gulp(level, minion, new ItemStack(at.getBlock()), Math.min(diet.forageMb(), half - minion.power()));
                return true;
            }
            if ((below.is(Blocks.GRASS_BLOCK) || below.is(Blocks.MYCELIUM) || below.is(Blocks.PODZOL)) && matches(diet.foods(), below)) {
                level.levelEvent(2001, under, Block.getId(below));
                level.setBlock(under, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
                gulp(level, minion, new ItemStack(below.getBlock()), Math.min(diet.forageMb(), half - minion.power()));
                return true;
            }
        }
        return false;
    }

    /** A minion swallows something for blood: a wet, greedy chewing, and crumbs. */
    private static void gulp(ServerLevel level, MinionEntity minion, ItemStack food, float mb) {
        minion.feed(Math.max(0.0F, mb));
        level.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.GENERIC_EAT, minion.getSoundSource(), 0.7F,
                0.5F + level.random.nextFloat() * 0.2F);
        level.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.SLIME_SQUISH_SMALL, minion.getSoundSource(), 0.5F, 0.6F);
        crumbs(level, minion, food);
        minion.gameEvent(GameEvent.EAT);
    }

    /** Bits of what was eaten, falling from the mouth. */
    static void crumbs(ServerLevel level, LivingEntity eater, ItemStack food) {
        if (!food.isEmpty()) {
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food), eater.getX(), eater.getEyeY() - 0.2, eater.getZ(), 8,
                    0.15, 0.1, 0.15, 0.05);
        }
    }
}
