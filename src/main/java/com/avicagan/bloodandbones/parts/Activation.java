package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.registry.BBFluids;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The activate trigger (docs/PARTS-AND-TRAITS.md section 5.2). A player's Organ Ability key fires the next ready activate
 * effect among the pieces worn, in the order helmet, chestplate, leggings, boots (a full set's last), so pressing again
 * goes on to the next; a minion's AI fires one when its target is within the effect's range. Each has its own cooldown,
 * shown on the piece it came from as an ender pearl's is, and may cost blood ({@code cost_mb}): from the worn tank or a
 * strapped chestplate for a player, or {@link #HUNGER_COST} hunger instead when that holds too little blood, or soul blood,
 * or there is none (section 7.6; refused, with a word, only if they are too hungry for that too); from its own blood or
 * canister for a minion.
 */
public final class Activation {
    /** One activate effect a host can fire: the trait it came from, its place in that trait, and its entry. */
    public record Facet(ActiveTraits.Entry entry, int index, TraitEffect facet) {
        String key() {
            return entry.id() + "#" + index;
        }
    }

    /** What an Organ Ability costs a player whose tank cannot pay its blood: hunger points (docs/PARTS-AND-TRAITS.md section 7.6). */
    public static final int HUNGER_COST = 3;

    /** The facet each player last fired or tried, so the next press goes on from there. */
    private static final Map<LivingEntity, String> LAST = Collections.synchronizedMap(new WeakHashMap<>());

    private Activation() {
    }

    /** The host's activate effects that work here, piece by piece in the key's order (worked out once for its traits). */
    public static List<Facet> facets(ActiveTraits traits) {
        if (traits.isEmpty()) {
            return List.of();
        }
        List<Facet> known = traits.facets;
        if (known == null) {
            known = collect(traits);
            traits.facets = known;
        }
        return known;
    }

    private static List<Facet> collect(ActiveTraits traits) {
        List<Facet> out = new ArrayList<>();
        for (int s = 0; s <= ActiveTraits.PIECES.length; s++) {
            EquipmentSlot slot = s < ActiveTraits.PIECES.length ? ActiveTraits.PIECES[s] : null;
            for (ActiveTraits.Entry entry : traits.entries()) {
                if (entry.slot() != slot) {
                    continue;
                }
                List<TraitEffect> effects = entry.trait().effects();
                for (int i = 0; i < effects.size(); i++) {
                    if (effects.get(i).trigger() == Trigger.ACTIVATE && traits.applies(effects.get(i))) {
                        out.add(new Facet(entry, i, effects.get(i)));
                    }
                }
            }
        }
        return List.copyOf(out);
    }

    /**
     * A press of the Organ Ability key, on the server: the next activate effect after the last one tried that is off
     * cooldown and whose condition holds. Too little blood for it, and too hungry to pay in hunger, refuses it with a
     * word, and the next press moves on.
     *
     * @return whether something fired
     */
    public static boolean press(Player player) {
        if (player.level().isClientSide || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        List<Facet> facets = facets(ActiveTraits.of(player));
        if (facets.isEmpty()) {
            return false;
        }
        String last = LAST.get(player);
        int start = 0;
        for (int i = 0; i < facets.size(); i++) {
            if (facets.get(i).key().equals(last)) {
                start = i + 1;
                break;
            }
        }
        for (int n = 0; n < facets.size(); n++) {
            Facet facet = facets.get((start + n) % facets.size());
            if (TraitEvents.coolingDown(player, facet.entry(), facet.index()) || !TraitEvents.holds(player, facet.entry(), facet.facet(), null)) {
                continue;
            }
            LAST.put(player, facet.key());
            if (fire(player, facet, null)) {
                return true;
            }
            player.displayClientMessage(Component.translatable("bloodandbones.organ.no_blood", facet.facet().costMb(), HUNGER_COST).withStyle(ChatFormatting.RED), true);
            player.playNotifySound(SoundEvents.DISPENSER_FAIL, player.getSoundSource(), 0.5F, 0.6F);
            return false;
        }
        return false;
    }

    /** The first activate effect a minion can fire at this target now (in range, off cooldown, its condition holding, affordable), or null. */
    @Nullable
    public static Facet readyFor(MinionEntity minion, LivingEntity target) {
        for (Facet facet : facets(ActiveTraits.of(minion))) {
            float range = facet.facet().range();
            if (minion.distanceToSqr(target) > range * range || TraitEvents.coolingDown(minion, facet.entry(), facet.index())
                    || minion.power() - facet.facet().costMb() < 1.0F || !TraitEvents.holds(minion, facet.entry(), facet.facet(), null)) {
                continue;
            }
            return facet;
        }
        return null;
    }

    /**
     * Fire one: pay for it (a player whose tank cannot, in hunger), start its cooldown (shown on the piece it came from),
     * and run it (if its chance comes up).
     *
     * @param target a minion's target; null for a player, whose effects aim along their look
     * @return false, with nothing done, if the host could not pay
     */
    public static boolean fire(LivingEntity host, Facet facet, @Nullable LivingEntity target) {
        TraitEffect entry = facet.facet();
        if (!pay(host, entry.costMb()) && !payHunger(host)) {
            return false;
        }
        TraitEvents.startCooldown(host, facet.entry(), facet.index(), entry.cooldown());
        if (host instanceof Player player && facet.entry().slot() != null && entry.cooldown() > 0) {
            ItemStack piece = player.getItemBySlot(facet.entry().slot());
            if (!piece.isEmpty()) {
                player.getCooldowns().addCooldown(piece.getItem(), entry.cooldown());
            }
        }
        // the organ works with a wet squelch; brass with a hiss
        boolean brass = host instanceof MinionEntity minion && minion.cybernetic();
        host.level().playSound(null, host.getX(), host.getY(), host.getZ(), brass ? SoundEvents.PISTON_EXTEND : SoundEvents.SLIME_SQUISH,
                host.getSoundSource(), 0.6F, (brass ? 1.4F : 0.6F) + host.getRandom().nextFloat() * 0.2F);
        if (entry.chance() >= 1.0F || host.getRandom().nextFloat() < entry.chance()) {
            entry.effect().run(new TraitContext(host, ActiveTraits.of(host), facet.entry(), facet.index(), entry, Trigger.ACTIVATE, target, null, 0.0F));
        }
        return true;
    }

    /**
     * Pay blood for something. A player from the worn tank or strapped chestplate, which must hold blood (anything
     * {@code c:blood}); a creative player pays nothing. A minion from its own blood or canister, never its last drop.
     *
     * @return false, and nothing taken, if there is too little
     */
    public static boolean pay(LivingEntity host, int mb) {
        if (mb <= 0) {
            return true;
        }
        if (host instanceof MinionEntity minion) {
            return minion.usePower(mb);
        }
        if (host instanceof Player player && player.hasInfiniteMaterials()) {
            return true;
        }
        ItemStack tank = FluidBacktankItem.wornBy(host);
        FluidStack fluid = FluidBacktankItem.fluid(tank);
        if (tank.isEmpty() || !fluid.is(BBFluids.BLOOD_TAG) || fluid.getAmount() < mb) {
            return false;
        }
        fluid.shrink(mb);
        FluidBacktankItem.setFluid(tank, fluid);
        return true;
    }

    /**
     * An Organ Ability a player's tank cannot pay for (none worn, too little in it, or soul blood) costs
     * {@link #HUNGER_COST} hunger instead, if they have that much to give.
     *
     * @return false, and nothing taken, if they have too little
     */
    private static boolean payHunger(LivingEntity host) {
        if (!(host instanceof Player player)) {
            return false;
        }
        FoodData food = player.getFoodData();
        if (food.getFoodLevel() < HUNGER_COST) {
            return false;
        }
        food.setFoodLevel(food.getFoodLevel() - HUNGER_COST);
        food.setSaturation(Math.min(food.getSaturationLevel(), food.getFoodLevel()));
        return true;
    }

    /** Forget a player (they left). */
    static void forget(LivingEntity host) {
        LAST.remove(host);
    }
}
