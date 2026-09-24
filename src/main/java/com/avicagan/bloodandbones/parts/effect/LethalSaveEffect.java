package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import org.joml.Vector3d;

import java.util.List;

/**
 * Cheating death (docs/PARTS-AND-TRAITS.md section 5.4, the damage type's lethal_save, here a type of its own): a killing
 * blow is called off, {@code chance} of the time at the trait's level, and the host is left on {@code health} with its
 * harmful effects and the fire on it gone, for {@code cost_mb} of blood from a player's tank (a creative player pays
 * nothing). A minion collapses instead: 1 health, powered down where it stands (never destroyed). Asked by
 * {@code TraitEvents#onDeath} on every lethal blow, whatever its trigger; the entry's cooldown starts when it saves.
 */
public record LethalSaveEffect(LevelBasedValue chance, float health, int costMb) implements TraitEffect.Effect, TraitEffect.DeathSaver {
    public static final MapCodec<LethalSaveEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.optionalFieldOf("chance", LevelBasedValue.constant(1.0F)).forGetter(LethalSaveEffect::chance),
            Codec.FLOAT.optionalFieldOf("health", 1.0F).forGetter(LethalSaveEffect::health),
            Codec.INT.optionalFieldOf("cost_mb", 0).forGetter(LethalSaveEffect::costMb)
    ).apply(i, LethalSaveEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public boolean save(TraitContext ctx) {
        LivingEntity host = ctx.host();
        float odds = chance.calculate(ctx.traitLevel());
        if (odds < 1.0F && ctx.random().nextFloat() >= odds) {
            return false;
        }
        if (host instanceof MinionEntity minion) {
            // a minion's way of not dying: it falls down where it is, empty, and waits for blood
            minion.setHealth(Math.max(1.0F, Math.min(health, minion.getMaxHealth())));
            minion.powerDown();
            gasp(ctx.level(), host);
            return true;
        }
        if (!ctx.pay(costMb)) {
            return false;
        }
        host.setHealth(Math.max(1.0F, Math.min(health, host.getMaxHealth())));
        RegenEffect.cure(host, List.of("harmful"));
        host.clearFire();
        gasp(ctx.level(), host);
        return true;
    }

    /** Back from the brink: a lurching heartbeat, a spray of blood and the flicker of a totem. */
    private static void gasp(ServerLevel level, LivingEntity host) {
        level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_HEARTBEAT, host.getSoundSource(), 1.0F, 0.7F);
        level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.TOTEM_USE, host.getSoundSource(), 0.5F, 0.5F);
        level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_SQUISH, host.getSoundSource(), 0.8F, 0.5F);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, host.getX(), host.getY(0.6), host.getZ(), 30, 0.3, 0.4, 0.3, 0.4);
        if (Blood.bleeds(host) && !(host instanceof MinionEntity minion && minion.cybernetic())) {
            Blood.burst(level, new Vector3d(host.getX(), host.getY(0.6), host.getZ()), 14, Blood.soul(BuiltInRegistries.ENTITY_TYPE.getKey(host.getType())));
        }
    }
}
