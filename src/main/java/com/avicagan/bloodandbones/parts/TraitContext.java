package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.minion.MinionEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an effect gets when it runs ({@link TraitEffect.Effect#run}, {@link TraitEffect.Effect#keepUp},
 * {@link TraitEffect.DeathSaver#save}). Always on the server.
 *
 * @param host    who carries the trait: a player in carcass armour, or a minion
 * @param traits  all the host's traits now
 * @param entry   the trait this came from, at its level on the host
 * @param index   which of the trait's effects this is
 * @param facet   the effect's entry in the trait: its trigger, condition, cooldown, range and cost
 * @param trigger what set it off
 * @param other   whoever else is in it: the attacker (hurt), the victim (attack, kill), the mob taking aim
 *                (targeted), a minion's target (activate), the killer (a lethal save); null for tick, passive, fall
 *                and a player's activate
 * @param source  the damage, for hurt, attack, kill and a lethal save
 * @param amount  the damage (hurt, attack), the victim's most health (kill), or the distance fallen (fall); 0 otherwise
 */
public record TraitContext(LivingEntity host, ActiveTraits traits, ActiveTraits.Entry entry, int index, TraitEffect facet, Trigger trigger,
                           @Nullable LivingEntity other, @Nullable DamageSource source, float amount) {
    /** The world it happens in. */
    public ServerLevel level() {
        return (ServerLevel) host.level();
    }

    /** The trait's level on the host. */
    public int traitLevel() {
        return entry.level();
    }

    /** "armour" for a player, "minion" for a minion. */
    public String context() {
        return traits.context();
    }

    public boolean onMinion() {
        return host instanceof MinionEntity;
    }

    public RandomSource random() {
        return host.getRandom();
    }

    /**
     * An amount at the trait's level, times the server's trait strength: use it for attribute changes, damage
     * changes, heals, pushes and damage dealt.
     */
    public float scaled(LevelBasedValue value) {
        return value.calculate(entry.level()) * TraitEffects.strength();
    }

    /** A count at the trait's level, left alone by the trait strength: amplifiers, durations, radii, how many. */
    public int levelled(LevelBasedValue value) {
        return (int) value.calculate(entry.level());
    }

    /**
     * Pay blood for something: a player from the worn tank (or a strapped chestplate), a minion from its own blood
     * or canister. False, and nothing taken, if there is too little.
     */
    public boolean pay(int mb) {
        return Activation.pay(host, mb);
    }
}
