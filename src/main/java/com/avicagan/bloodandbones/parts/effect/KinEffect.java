package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Kin (docs/PARTS-AND-TRAITS.md section 5.4, type 9): mobs of these kinds take the host for one of their own and never
 * set their sights on it ({@code LivingChangeTargetEvent}, cancelled in {@link SocialEffects}), unless the host hurt
 * that very mob within the last {@code provoked_seconds}. Kept up, it also calls off any of them already after the host
 * (a set put on in the middle of a fight).
 *
 * @param entities        which mobs: an id, a "#tag", or any of {@link SocialFilter}'s words
 * @param provokedSeconds how long a mob the host hurt may still fight back
 */
public record KinEffect(SocialFilter entities, int provokedSeconds) implements TraitEffect.Effect {
    public static final MapCodec<KinEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SocialFilter.CODEC.fieldOf("entities").forGetter(KinEffect::entities),
            Codec.INT.optionalFieldOf("provoked_seconds", 30).forGetter(KinEffect::provokedSeconds)
    ).apply(i, KinEffect::new));

    /** How far round the host a passive kin calls off mobs already after it. */
    private static final double CALL_OFF = 16.0;

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** Mobs of its kind already after the host, and not provoked, let it be. */
    @Override
    public void keepUp(TraitContext ctx) {
        LivingEntity host = ctx.host();
        for (Mob mob : host.level().getEntitiesOfClass(Mob.class, host.getBoundingBox().inflate(CALL_OFF), m -> m.getTarget() == host)) {
            if (entities.test(host, mob) && !SocialEffects.provoked(mob, host, provokedSeconds)) {
                SocialEffects.callOff(mob);
            }
        }
    }

    /**
     * Whether the target's kin traits keep this mob off it: one of the kinds, not hurt by the target lately, and the
     * trait's condition holding.
     */
    public static boolean spares(Mob mob, LivingEntity target) {
        ActiveTraits traits = ActiveTraits.peek(target);
        if (traits.isEmpty()) {
            return false;
        }
        for (ActiveTraits.Found<KinEffect> found : traits.find(KinEffect.class)) {
            KinEffect kin = found.effect();
            if (found.facet().trigger() == Trigger.PASSIVE && kin.entities.test(target, mob) && !SocialEffects.provoked(mob, target, kin.provokedSeconds)
                    && TraitEvents.holds(target, found.entry(), found.facet(), null)) {
                return true;
            }
        }
        return false;
    }
}
