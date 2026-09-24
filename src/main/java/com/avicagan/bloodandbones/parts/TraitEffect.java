package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import java.util.Optional;

/**
 * One entry in a trait: when it happens, on what condition (any vanilla loot condition), how often, and what
 * it does. What it does is one of a small registered set of effect types ({@link TraitEffects}); data only
 * composes them.
 *
 * @param interval ticks between goes, for the tick trigger (at least 20)
 * @param cooldown ticks before it can happen again (0, or at least 20)
 * @param context  "minion" or "armour" to limit it to one; empty for both
 * @param filter   for hurt: "melee", "projectile", "fire" or "any"
 */
public record TraitEffect(Trigger trigger, int interval, float chance, int cooldown, Optional<LootItemCondition> requirements,
                          Optional<String> context, String filter, Effect effect) {
    /** What an effect does. Each type is a record with its own parameters, registered by id. */
    public interface Effect {
        MapCodec<? extends Effect> codec();
    }

    public static final Codec<TraitEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Trigger.CODEC.optionalFieldOf("trigger", Trigger.PASSIVE).forGetter(TraitEffect::trigger),
            Codec.INT.optionalFieldOf("interval", 20).forGetter(TraitEffect::interval),
            Codec.FLOAT.optionalFieldOf("chance", 1.0F).forGetter(TraitEffect::chance),
            Codec.INT.optionalFieldOf("cooldown", 0).forGetter(TraitEffect::cooldown),
            LootItemCondition.DIRECT_CODEC.optionalFieldOf("requirements").forGetter(TraitEffect::requirements),
            Codec.STRING.optionalFieldOf("context").forGetter(TraitEffect::context),
            Codec.STRING.optionalFieldOf("filter", "any").forGetter(TraitEffect::filter),
            TraitEffects.CODEC.fieldOf("effect").forGetter(TraitEffect::effect)
    ).apply(i, TraitEffect::new));

    public boolean appliesIn(String where) {
        return context.isEmpty() || context.get().equals(where);
    }
}
