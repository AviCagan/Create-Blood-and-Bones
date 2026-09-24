package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.carcass.Blood;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.EnchantmentEntityEffect;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * hitscan (docs/PARTS-AND-TRAITS.md section 5.4, row 20): a shot with no projectile, landing at once on the first creature
 * in line within range (level.clip for the blocks in the way, then a sweep of the line for creatures; none behind a
 * wall), hurt with {@code damage} of {@code damage_type}, pushed back by {@code knockback}, and given the {@code hit}
 * effect (any vanilla enchantment entity effect or one of our actions, a web say). Covers the guardian's beam, the
 * warden's sonic boom and the spider's web shot.
 * <p>
 * With a {@code windup} it charges first: the beam is drawn for that long (by the clients, from {@link BeamPayload}),
 * locked on a minion's target or on what the player looked at when they pressed the key (otherwise it follows their look),
 * and the shot lands when it is done, if the host is still there.
 *
 * @param beam "none", "guardian_beam" (the guardian's, warming from purple to yellow), "sonic_boom" (the warden's rings
 *             along the line) or "strand" (a flick of silk)
 */
public record HitscanEffect(float range, LevelBasedValue damage, ResourceKey<DamageType> damageType, int windup, String beam, float knockback,
                            Optional<EnchantmentEntityEffect> hit) implements TraitEffect.Effect {
    private static final List<String> BEAMS = List.of("none", "guardian_beam", "sonic_boom", "strand");

    public static final MapCodec<HitscanEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.floatRange(1.0F, 64.0F).optionalFieldOf("range", 16.0F).forGetter(HitscanEffect::range),
            LevelBasedValue.CODEC.optionalFieldOf("damage", LevelBasedValue.constant(0.0F)).forGetter(HitscanEffect::damage),
            ResourceKey.codec(Registries.DAMAGE_TYPE).optionalFieldOf("damage_type", DamageTypes.MOB_PROJECTILE).forGetter(HitscanEffect::damageType),
            Codec.intRange(0, 200).optionalFieldOf("windup", 0).forGetter(HitscanEffect::windup),
            Codec.STRING.validate(b -> BEAMS.contains(b) ? DataResult.success(b) : DataResult.error(() -> "Unknown beam " + b + ", not one of " + BEAMS))
                    .optionalFieldOf("beam", "none").forGetter(HitscanEffect::beam),
            Codec.FLOAT.optionalFieldOf("knockback", 0.0F).forGetter(HitscanEffect::knockback),
            EnchantmentEntityEffect.CODEC.validate(VanillaEffect::allowed).optionalFieldOf("hit").forGetter(HitscanEffect::hit)
    ).apply(i, HitscanEffect::new));

    /** A shot charging: whose, on whom, and when it lands. */
    private record Charging(HitscanEffect effect, TraitContext ctx, @Nullable Entity locked, long at) {
    }

    private static final List<Charging> CHARGING = new ArrayList<>();

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        LivingEntity host = ctx.host();
        ServerLevel level = ctx.level();
        // a minion aims at its target, a player's key at what they look at (a charging beam stays on it)
        Entity locked = ctx.other() != null ? ctx.other() : windup > 0 && host instanceof Player ? RangedAim.lookedAt(host, range) : null;
        if (!"none".equals(beam)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(host, new BeamPayload(host.getId(), locked == null ? -1 : locked.getId(), windup, range, beam));
        }
        if (windup <= 0) {
            fire(ctx, locked);
            return;
        }
        switch (beam) {
            case "guardian_beam" -> level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.GUARDIAN_ATTACK, host.getSoundSource(), 1.0F, 0.8F);
            case "sonic_boom" -> level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, host.getSoundSource(), 3.0F, 1.0F);
            default -> level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_SQUISH_SMALL, host.getSoundSource(), 0.6F, 0.5F);
        }
        CHARGING.add(new Charging(this, ctx, locked, level.getGameTime() + windup));
    }

    /** Every server tick: the charged shots whose time has come land (or fizzle, their host gone). */
    static void tickCharging() {
        if (CHARGING.isEmpty()) {
            return;
        }
        List<Charging> due = new ArrayList<>();
        Iterator<Charging> it = CHARGING.iterator();
        while (it.hasNext()) {
            Charging c = it.next();
            LivingEntity host = c.ctx().host();
            if (!host.isAlive() || host.isRemoved()) {
                it.remove();
            } else if (host.level().getGameTime() >= c.at()) {
                it.remove();
                due.add(c);
            }
        }
        // fired after, as a shot landing can start another charging (a victim's own beam, hurt)
        due.forEach(c -> c.effect().fire(c.ctx(), c.locked()));
    }

    static void forget() {
        CHARGING.clear();
    }

    /** The shot lands: along the line to the locked creature (still there and in reach), else along the host's look. */
    void fire(TraitContext ctx, @Nullable Entity locked) {
        LivingEntity host = ctx.host();
        ServerLevel level = ctx.level();
        Vec3 from = host.getEyePosition();
        boolean onIt = locked != null && locked.isAlive() && locked.level() == level && locked.distanceTo(host) <= range * 1.5F;
        Vec3 dir = onIt ? locked.getBoundingBox().getCenter().subtract(from).normalize() : host.getViewVector(1.0F).normalize();
        Vec3 to = from.add(dir.scale(range));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, host));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult struck = RangedAim.firstInLine(host, from, end);
        Vec3 landed = struck != null ? struck.getEntity().getBoundingBox().getCenter() : end;
        trail(level, from, landed);
        sound(level, host);
        if (struck == null || !(struck.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        float amount = ctx.scaled(damage);
        if (amount > 0.0F) {
            // the host's doing, from where it stands, but no blow of its own: nothing direct, so its on-hit traits keep out of it
            victim.hurt(new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(damageType), null, host,
                    host.position()), amount);
        }
        if (knockback > 0.0F) {
            double resist = 1.0 - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
            victim.push(dir.x * knockback * resist, dir.y * knockback * 0.2 * resist, dir.z * knockback * resist);
            victim.hurtMarked = true;
        }
        hit.ifPresent(effect -> effect.apply(level, ctx.traitLevel(), RangedAim.itemInUse(ctx), victim, victim.position()));
        // where it went in: a spurt from something that bleeds
        if (Blood.bleeds(victim) && amount > 0.0F) {
            ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
            Blood.burst(level, new Vector3d(landed.x, landed.y, landed.z), 4, Blood.soul(type));
        }
        level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.SLIME_SQUISH, victim.getSoundSource(), 0.6F, 0.7F);
    }

    /** What the line looks like as it lands: the warden's rings, a flick of silk, nothing for the others (theirs is the beam). */
    private void trail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double length = d.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 step = d.normalize();
        switch (beam) {
            case "sonic_boom" -> {
                for (int j = 1; j < Math.floor(length) + 1; j++) {
                    Vec3 p = from.add(step.scale(j));
                    level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
            case "strand" -> {
                BlockParticleOption silk = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBWEB.defaultBlockState());
                for (double t = 0.5; t < length; t += 0.5) {
                    Vec3 p = from.add(step.scale(t));
                    level.sendParticles(silk, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
                }
            }
            default -> {
            }
        }
    }

    private void sound(ServerLevel level, LivingEntity host) {
        switch (beam) {
            case "sonic_boom" -> level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.WARDEN_SONIC_BOOM, host.getSoundSource(), 3.0F, 1.0F);
            case "guardian_beam" -> level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.GUARDIAN_FLOP, host.getSoundSource(), 1.0F, 0.6F);
            case "strand" -> {
                level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SPIDER_AMBIENT, host.getSoundSource(), 0.5F, 1.8F);
                level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, host.getSoundSource(), 0.8F, 1.2F);
            }
            default -> level.playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.SLIME_SQUISH_SMALL, host.getSoundSource(), 0.6F, 0.6F);
        }
    }
}
