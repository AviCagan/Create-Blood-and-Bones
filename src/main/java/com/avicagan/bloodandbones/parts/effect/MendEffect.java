package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.Trait;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEffects;
import com.avicagan.bloodandbones.parts.TraitList;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Mending (docs/PARTS-AND-TRAITS.md section 5.4, mend). {@code mode} "item": one of the {@code items} (ids or "#tags")
 * used on a minion mends {@code amount} health, flesh or brass (iron for golem parts), and on an anvil mends the carcass
 * armour the trait is on. "self": the armour piece the trait comes from (every worn piece, for a full set's) mends
 * {@code amount} durability each time a tick entry comes up.
 */
public record MendEffect(String mode, List<String> items, LevelBasedValue amount) implements TraitEffect.Effect {
    public static final MapCodec<MendEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("mode", "item").forGetter(MendEffect::mode),
            Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(MendEffect::items),
            LevelBasedValue.CODEC.optionalFieldOf("amount", LevelBasedValue.constant(1.0F)).forGetter(MendEffect::amount)
    ).apply(i, MendEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** Self-mending: worn carcass armour knits its wear back, a little each time. */
    @Override
    public void run(TraitContext ctx) {
        if (!"self".equals(mode) || ctx.onMinion()) {
            return;
        }
        int points = ctx.levelled(amount);
        EquipmentSlot slot = ctx.entry().slot();
        for (EquipmentSlot piece : slot == null ? ActiveTraits.PIECES : new EquipmentSlot[]{slot}) {
            ItemStack stack = ctx.host().getItemBySlot(piece);
            if (points > 0 && stack.getItem() instanceof CarcassArmourItem && stack.isDamaged()) {
                stack.setDamageValue(Math.max(0, stack.getDamageValue() - points));
            }
        }
    }

    /**
     * An item used on a minion that one of its mend effects takes: while it is hurt, it mends and the item is used up.
     * Brass and flesh alike (the brief's "brass never heals itself" is about healing on its own).
     */
    @Nullable
    static InteractionResult interact(MinionEntity minion, Player player, ItemStack held) {
        if (minion.getHealth() >= minion.getMaxHealth()) {
            return null;
        }
        for (ActiveTraits.Found<MendEffect> found : ActiveTraits.of(minion).find(MendEffect.class)) {
            MendEffect mend = found.effect();
            if (!"item".equals(mend.mode()) || !Diet.matches(mend.items(), held)) {
                continue;
            }
            if (!minion.level().isClientSide) {
                minion.heal(mend.amount().calculate(found.entry().level()) * TraitEffects.strength());
                held.consume(1, player);
                // hammered and stitched back together
                minion.level().playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.ANVIL_USE, minion.getSoundSource(), 0.4F, 1.4F);
                if (!minion.cybernetic()) {
                    minion.level().playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.SLIME_SQUISH, minion.getSoundSource(), 0.6F, 0.6F);
                }
            }
            return InteractionResult.sidedSuccess(minion.level().isClientSide);
        }
        return null;
    }

    /** Whether a carcass piece's own traits let this mend it on an anvil (a mend effect of mode "item" naming it). */
    static boolean repairsWith(ItemStack piece, ItemStack repair) {
        CarcassArmour armour = CarcassArmourItem.armour(piece);
        if (armour == null || repair.isEmpty()) {
            return false;
        }
        PartsData.Store store = CarcassArmourItem.store();
        for (TraitList.Resolved resolved : armour.traits(store)) {
            Trait trait = store.trait(resolved.id());
            if (trait == null || !trait.contexts().contains(ActiveTraits.ARMOUR)) {
                continue;
            }
            for (TraitEffect facet : trait.effects()) {
                if (facet.effect() instanceof MendEffect mend && "item".equals(mend.mode()) && facet.appliesIn(ActiveTraits.ARMOUR)
                        && TraitEffects.enabled(mend) && Diet.matches(mend.items(), repair)) {
                    return true;
                }
            }
        }
        return false;
    }
}
