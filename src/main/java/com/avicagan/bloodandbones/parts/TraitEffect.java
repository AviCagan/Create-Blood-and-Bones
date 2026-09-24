package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

/**
 * One entry in a trait: when it happens, on what condition (any vanilla loot condition, or one of ours in
 * {@link TraitConditions}), how often, and what it does. What it does is one of a small registered set of effect
 * types ({@link TraitEffects}); data only composes them.
 *
 * @param interval ticks between goes, for the tick trigger (at least 20)
 * @param cooldown ticks before it can happen again (0, or at least 20); an activate entry shows it on the piece
 * @param context  "minion" or "armour" to limit it to one; empty for both
 * @param filter   for hurt: "melee", "projectile", "fire" or "any"
 * @param range    for activate: how near its target must be before a minion fires it
 * @param costMb   for activate: the blood it costs, from the worn tank (a player) or its own blood or canister (a minion)
 */
public record TraitEffect(Trigger trigger, int interval, float chance, int cooldown, Optional<LootItemCondition> requirements,
                          Optional<String> context, String filter, float range, int costMb, Effect effect) {
    /**
     * What an effect does. Each type is a record with its own parameters, registered by id, and does its own work:
     * {@link #run} when a triggered entry comes up, {@link #keepUp} while a passive one holds. Types that change
     * something continuously (damage taken, immunities, flags) are read where that happens instead, through
     * {@link ActiveTraits#find}.
     */
    public interface Effect {
        MapCodec<? extends Effect> codec();

        /** Do it now: an entry of this effect came up (tick, hurt, attack, fall, kill, targeted or activate). */
        default void run(TraitContext ctx) {
        }

        /** Keep it going: a passive entry of this effect holds, checked every half second. */
        default void keepUp(TraitContext ctx) {
        }
    }

    /**
     * An effect that can stop its host dying (a lethal save). Asked on every lethal blow, before the death goes
     * through; the first that saves wins, and the death is called off.
     */
    public interface DeathSaver {
        /** Keep the host alive if it can; true only if it did, its health above 0 again. */
        boolean save(TraitContext ctx);
    }

    public static final Codec<TraitEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Trigger.CODEC.optionalFieldOf("trigger", Trigger.PASSIVE).forGetter(TraitEffect::trigger),
            Codec.INT.optionalFieldOf("interval", 20).forGetter(TraitEffect::interval),
            Codec.FLOAT.optionalFieldOf("chance", 1.0F).forGetter(TraitEffect::chance),
            Codec.INT.optionalFieldOf("cooldown", 0).forGetter(TraitEffect::cooldown),
            LootItemCondition.DIRECT_CODEC.optionalFieldOf("requirements").forGetter(TraitEffect::requirements),
            Codec.STRING.optionalFieldOf("context").forGetter(TraitEffect::context),
            Codec.STRING.optionalFieldOf("filter", "any").forGetter(TraitEffect::filter),
            Codec.FLOAT.optionalFieldOf("range", 8.0F).forGetter(TraitEffect::range),
            Codec.INT.optionalFieldOf("cost_mb", 0).forGetter(TraitEffect::costMb),
            TraitEffects.CODEC.fieldOf("effect").forGetter(TraitEffect::effect)
    ).apply(i, TraitEffect::new));

    public boolean appliesIn(String where) {
        return context.isEmpty() || context.get().equals(where);
    }
}
