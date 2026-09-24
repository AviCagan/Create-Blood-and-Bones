package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.carcass.Blood;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3d;

/**
 * Bleeding (docs/PARTS-AND-TRAITS.md section 5.7): half a heart every two seconds, faster at each level (never quicker
 * than every 12 ticks), with a squelch, drops running off the wound and, now and then, a stain on the ground under it
 * through the stains' own code (every time from the third level). Something with no blood in it (a skeleton, a golem)
 * leaks instead: the same harm, grey sparks, no stain. In bloodless mode it is called Leaking and every drop shows as a
 * grey spark (the client's choice, in {@code RangedClient}); what it does is the same.
 */
public class BleedingMobEffect extends MobEffect {
    /** Ticks between wounds at the first level; halved each level above. */
    public static final int INTERVAL = 40;
    /** No quicker than this: a creature shrugs off a second hurt inside half its hurt time. */
    public static final int FASTEST = 12;
    public static final float DAMAGE = 0.5F;

    public BleedingMobEffect() {
        super(MobEffectCategory.HARMFUL, 0x8A0A0A);
    }

    /** Drops (sparks in bloodless mode) rather than potion swirls. */
    @Override
    public ParticleOptions createParticleOptions(MobEffectInstance effect) {
        return RangedContent.BLEEDING_DRIP.get();
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % interval(amplifier) == 0;
    }

    public static int interval(int amplifier) {
        return Math.max(FASTEST, INTERVAL >> Math.min(amplifier, 8));
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return true;
        }
        entity.hurt(level.damageSources().source(RangedContent.BLEEDING_DAMAGE), DAMAGE);
        Vector3d wound = new Vector3d(entity.getX(), entity.getY(0.5), entity.getZ());
        if (Blood.bleeds(entity)) {
            boolean soul = Blood.soul(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
            for (int i = 0; i <= amplifier; i++) {
                Blood.drip(level, new Vector3d(entity.getRandomX(0.4), entity.getRandomY(), entity.getRandomZ(0.4)), soul);
            }
            Blood.burst(level, wound, 2 + amplifier, soul);
            // a chance of a stain under it, sure from the third level
            if (entity.getRandom().nextInt(3) <= amplifier) {
                Blood.stain(level, new Vector3d(entity.getX(), entity.getY() + 0.1, entity.getZ()), 1, soul);
            }
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.SLIME_SQUISH_SMALL, entity.getSoundSource(), 0.5F,
                    0.5F + entity.getRandom().nextFloat() * 0.2F);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, entity.getSoundSource(), 0.6F, 0.6F);
        } else {
            // nothing to bleed: it leaks, a hiss and a spray of grey sparks
            level.sendParticles(RangedContent.LEAK_SPARK.get(), wound.x, wound.y, wound.z, 4 + 2 * amplifier, 0.2, 0.3, 0.2, 0.05);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, entity.getSoundSource(), 0.25F, 1.6F);
        }
        return true;
    }
}
