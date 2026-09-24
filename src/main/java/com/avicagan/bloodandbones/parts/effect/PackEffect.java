package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEffects;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.LevelBasedValue;

/**
 * Pack (docs/PARTS-AND-TRAITS.md section 5.4, type 15): the host hits harder for every ally near it, up to a cap, read
 * where the damage is ({@code LivingIncomingDamageEvent}, in {@link SocialEffects}). Direct hits only, as the attack
 * trigger's are.
 *
 * @param perAlly how much more for each ally (0.15: 15%)
 * @param allies  who counts ({@link SocialFilter}): the host's own side by default, or a kind or family of mob
 * @param radius  how near they must be
 * @param cap     how many count at most
 */
public record PackEffect(LevelBasedValue perAlly, SocialFilter allies, float radius, int cap) implements TraitEffect.Effect {
    public static final MapCodec<PackEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("per_ally").forGetter(PackEffect::perAlly),
            SocialFilter.CODEC.optionalFieldOf("allies", SocialFilter.ALLIES).forGetter(PackEffect::allies),
            Codec.FLOAT.optionalFieldOf("radius", 8.0F).forGetter(PackEffect::radius),
            Codec.INT.optionalFieldOf("cap", 3).forGetter(PackEffect::cap)
    ).apply(i, PackEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** How many allies stand with the host against this victim now (the victim never counts), up to the cap. */
    public int allies(LivingEntity host, LivingEntity victim) {
        int found = host.level().getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(radius),
                e -> e != victim && e.distanceToSqr(host) <= radius * radius && allies.test(host, e)).size();
        return Math.min(found, Math.max(0, cap));
    }

    /**
     * What the attacker's pack traits multiply this hit by: 1 plus each one's share for each ally, times the trait
     * strength. 1 for a creature with none, or a hit that is not its own blow (thorns it sent back, say).
     */
    public static float multiplier(LivingEntity attacker, LivingEntity victim, DamageSource source) {
        if (source.getDirectEntity() != attacker || !TraitEvents.blow(source)) {
            return 1.0F;
        }
        ActiveTraits traits = ActiveTraits.peek(attacker);
        if (traits.isEmpty()) {
            return 1.0F;
        }
        float multiplier = 1.0F;
        for (ActiveTraits.Found<PackEffect> found : traits.find(PackEffect.class)) {
            Trigger trigger = found.facet().trigger();
            if ((trigger == Trigger.PASSIVE || trigger == Trigger.ATTACK) && TraitEvents.holds(attacker, found.entry(), found.facet(), source)) {
                int allies = found.effect().allies(attacker, victim);
                multiplier *= 1.0F + found.effect().perAlly().calculate(found.entry().level()) * allies * TraitEffects.strength();
            }
        }
        return Math.max(0.0F, multiplier);
    }
}
