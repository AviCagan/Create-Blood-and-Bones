package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionData;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.Trait;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEffects;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a body runs on blood (docs/PARTS-AND-TRAITS.md section 5.4, power). A flesh minion holds {@code capacity_mult}
 * times the blood (hump, bacon fat; worked out with its stats, so every trough, cradle and gauge sees it), goes through
 * it {@code drain_mult} times as fast (leaky, marrow), and turns the {@code refuel} items into that many mB each (fed by
 * hand, or eaten from what it carries once it runs low); {@code feed_on_kill_mb} tops it up on a kill. A player's
 * {@code drain_mult} goes on their blood upkeep (what their implants drink), and a kill puts {@code feed_on_kill_mb} into
 * the tank they wear, if it holds blood or nothing.
 */
public record PowerEffect(LevelBasedValue capacityMult, LevelBasedValue drainMult, Map<String, Integer> refuel, int feedOnKillMb)
        implements TraitEffect.Effect {
    private static final LevelBasedValue ONE = LevelBasedValue.constant(1.0F);

    public static final MapCodec<PowerEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.optionalFieldOf("capacity_mult", ONE).forGetter(PowerEffect::capacityMult),
            LevelBasedValue.CODEC.optionalFieldOf("drain_mult", ONE).forGetter(PowerEffect::drainMult),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("refuel", Map.of()).forGetter(PowerEffect::refuel),
            Codec.INT.optionalFieldOf("feed_on_kill_mb", 0).forGetter(PowerEffect::feedOnKillMb)
    ).apply(i, PowerEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /**
     * What a flesh build's reservoir is multiplied by: its passive, unconditional power effects' {@code capacity_mult}, from
     * the traits its parts give it (stacked as {@link ActiveTraits} stacks them). Pure, for {@code MinionStats}.
     */
    public static float capacity(PartsData.Store store, MinionBuild build) {
        Map<ResourceLocation, Integer> levels = new LinkedHashMap<>();
        for (List<TraitList.Resolved> source : MinionData.traits(store, build)) {
            for (TraitList.Resolved t : source) {
                Trait trait = store.trait(t.id());
                if (trait != null && trait.sums()) {
                    levels.merge(t.id(), t.level(), Integer::sum);
                } else {
                    levels.merge(t.id(), t.level(), Math::max);
                }
            }
        }
        float mult = 1.0F;
        for (Map.Entry<ResourceLocation, Integer> e : levels.entrySet()) {
            Trait trait = store.trait(e.getKey());
            if (trait == null || !trait.contexts().contains(ActiveTraits.MINION)) {
                continue;
            }
            int level = Math.min(e.getValue(), Math.max(1, trait.maxLevel()));
            for (TraitEffect facet : trait.effects()) {
                if (facet.effect() instanceof PowerEffect power && facet.trigger() == Trigger.PASSIVE && facet.requirements().isEmpty()
                        && facet.appliesIn(ActiveTraits.MINION) && TraitEffects.enabled(power)) {
                    mult *= Math.max(0.0F, UpkeepEffects.strengthened(power.capacityMult().calculate(level)));
                }
            }
        }
        return mult;
    }

    /** What the host's blood use is multiplied by from its power effects ({@code drain_mult}), those whose condition holds. */
    static float drain(LivingEntity host) {
        float mult = 1.0F;
        for (ActiveTraits.Found<PowerEffect> found : ActiveTraits.peek(host).find(PowerEffect.class)) {
            if (found.facet().trigger() == Trigger.PASSIVE && UpkeepEffects.holds(host, found)) {
                mult *= Math.max(0.0F, UpkeepEffects.strengthened(found.effect().drainMult().calculate(found.entry().level())));
            }
        }
        return mult;
    }

    /** The mB this item is worth to one of the minion's power effects, 0 if none takes it. */
    private static int worth(MinionEntity minion, ItemStack stack) {
        for (ActiveTraits.Found<PowerEffect> found : ActiveTraits.of(minion).find(PowerEffect.class)) {
            for (Map.Entry<String, Integer> e : found.effect().refuel().entrySet()) {
                if (e.getValue() > 0 && Diet.matches(List.of(e.getKey()), stack)) {
                    return e.getValue();
                }
            }
        }
        return 0;
    }

    /** Blood food used on a flesh minion with room for it: swallowed, and it is that much fuller (awake again, if it was down). */
    @Nullable
    static InteractionResult refuel(MinionEntity minion, Player player, ItemStack held) {
        if (minion.cybernetic() || minion.power() >= minion.stats().reservoir() - 1.0F) {
            return null;
        }
        int mb = worth(minion, held);
        if (mb <= 0) {
            return null;
        }
        if (minion.level() instanceof ServerLevel level) {
            ItemStack eaten = held.copyWithCount(1);
            minion.feed(mb);
            held.consume(1, player);
            chew(level, minion, eaten);
        }
        return InteractionResult.sidedSuccess(minion.level().isClientSide);
    }

    /**
     * A flesh minion running low (under a quarter) eats one thing it carries that a power effect turns into blood.
     *
     * @return whether it ate
     */
    static boolean eatStores(MinionEntity minion) {
        if (minion.powerShare() >= MinionEntity.HUNGRY) {
            return false;
        }
        for (int i = 0; i < minion.slots(); i++) {
            ItemStack stack = minion.inventory.getItem(i);
            int mb = stack.isEmpty() ? 0 : worth(minion, stack);
            if (mb > 0) {
                ItemStack eaten = stack.copyWithCount(1);
                stack.shrink(1);
                minion.inventory.setChanged();
                minion.feed(mb);
                chew(minion.level() instanceof ServerLevel level ? level : null, minion, eaten);
                return true;
            }
        }
        return false;
    }

    /** A kill feeds the killer: a flesh minion's blood, a player's worn tank (if it holds blood, or nothing). */
    static void fedOnKill(LivingEntity killer) {
        int mb = 0;
        for (ActiveTraits.Found<PowerEffect> found : ActiveTraits.peek(killer).find(PowerEffect.class)) {
            if (UpkeepEffects.holds(killer, found)) {
                mb += Math.max(0, found.effect().feedOnKillMb());
            }
        }
        if (mb <= 0) {
            return;
        }
        if (killer instanceof MinionEntity minion) {
            if (!minion.cybernetic()) {
                minion.feed(mb);
            }
            return;
        }
        ItemStack tank = FluidBacktankItem.wornBy(killer);
        if (tank.isEmpty()) {
            return;
        }
        FluidStack fluid = FluidBacktankItem.fluid(tank);
        int room = FluidBacktankItem.capacity(tank) - fluid.getAmount();
        if (room > 0 && (fluid.isEmpty() || fluid.is(BBFluids.BLOOD_TAG))) {
            FluidStack in = fluid.isEmpty() ? new FluidStack(BBFluids.blood(), Math.min(mb, room)) : fluid.copyWithAmount(fluid.getAmount() + Math.min(mb, room));
            FluidBacktankItem.setFluid(tank, in);
        }
    }

    /** Wolfed down: a wet chewing and crumbs. */
    private static void chew(@Nullable ServerLevel level, MinionEntity minion, ItemStack food) {
        if (level == null) {
            return;
        }
        level.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.GENERIC_EAT, minion.getSoundSource(), 0.8F, 0.5F);
        level.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.SLIME_SQUISH, minion.getSoundSource(), 0.5F, 0.5F);
        Diet.crumbs(level, minion, food);
    }
}
