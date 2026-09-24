package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * A blast from the host that spares the host (docs/PARTS-AND-TRAITS.md section 5.4, detonate): {@code Level#explode}
 * with the host as its source, and the host taken off the list of what it hits ({@link MotionEffects#onDetonate}).
 * Blocks break only if the trait says so, the server's {@code minion_block_damage} allows it and so does mobGriefing;
 * otherwise nothing but creatures is touched. A minion that self-destructs then powers down where it stands, never
 * destroyed, until it gets blood again.
 *
 * @param power             the blast's power at the trait's level (a creeper's is 3)
 * @param fire              it sets fire (only where it may break blocks)
 * @param blockDamage       it may break blocks, where the server and mobGriefing allow
 * @param fuse              ticks of hissing before it goes (0: at once)
 * @param minionPowersDown  a minion is left powered down after it
 */
public record DetonateEffect(LevelBasedValue power, boolean fire, boolean blockDamage, int fuse, boolean minionPowersDown) implements TraitEffect.Effect {
    public static final MapCodec<DetonateEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("power").forGetter(DetonateEffect::power),
            Codec.BOOL.optionalFieldOf("fire", false).forGetter(DetonateEffect::fire),
            Codec.BOOL.optionalFieldOf("block_damage", false).forGetter(DetonateEffect::blockDamage),
            Codec.INT.optionalFieldOf("fuse", 0).forGetter(DetonateEffect::fuse),
            Codec.BOOL.optionalFieldOf("minion_powers_down", true).forGetter(DetonateEffect::minionPowersDown)
    ).apply(i, DetonateEffect::new));

    /** Who is setting off a blast right now: the blast leaves them out. */
    static final Set<Entity> SPARED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Hosts hissing toward a blast, and what is left of their fuse. */
    private static final Map<LivingEntity, Lit> LIT = Collections.synchronizedMap(new WeakHashMap<>());

    private record Lit(DetonateEffect effect, float power, int[] left) {
    }

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        LivingEntity host = ctx.host();
        float strength = ctx.scaled(power);
        if (strength <= 0.0F || LIT.containsKey(host)) {
            return;
        }
        if (fuse <= 0) {
            blow(host, strength);
            return;
        }
        LIT.put(host, new Lit(this, strength, new int[]{fuse}));
        host.gameEvent(GameEvent.PRIME_FUSE);
        ctx.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.CREEPER_PRIMED, host.getSoundSource(), 1.0F, 0.5F);
        ctx.level().playSound(null, host.getX(), host.getY(), host.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, host.getSoundSource(), 1.0F, 0.5F);
    }

    /** Whether the host is hissing toward a blast now. */
    public static boolean lit(LivingEntity host) {
        return LIT.containsKey(host);
    }

    /** Every server tick: burn down the lit fuses, and set off those that reach the end (a host gone or powered down in the meantime is let off). */
    static void tickFuses() {
        if (LIT.isEmpty()) {
            return;
        }
        Map<LivingEntity, Lit> due = new java.util.HashMap<>();
        synchronized (LIT) {
            Iterator<Map.Entry<LivingEntity, Lit>> it = LIT.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LivingEntity, Lit> e = it.next();
                LivingEntity host = e.getKey();
                if (host.isRemoved() || !host.isAlive() || host instanceof MinionEntity minion && minion.poweredDown()) {
                    it.remove();
                } else if (--e.getValue().left()[0] <= 0) {
                    it.remove();
                    due.put(host, e.getValue());
                } else if (host.level() instanceof ServerLevel level && e.getValue().left()[0] % 4 == 0) {
                    // it swells and smokes as it goes
                    level.sendParticles(ParticleTypes.SMOKE, host.getX(), host.getY(0.6), host.getZ(), 3, 0.2, 0.2, 0.2, 0.01);
                }
            }
        }
        due.forEach((host, lit) -> lit.effect().blow(host, lit.power()));
    }

    /** Forget every lit fuse (the server stopped). */
    static void forgetAll() {
        LIT.clear();
        SPARED.clear();
    }

    /** The blast itself, sparing the host; then a minion powers down. */
    public void blow(LivingEntity host, float strength) {
        if (!(host.level() instanceof ServerLevel level)) {
            return;
        }
        boolean blocks = blockDamage && BBServerConfig.minionBlockDamage() && EventHooks.canEntityGrief(level, host);
        Vec3 at = new Vec3(host.getX(), host.getY(0.5), host.getZ());
        SPARED.add(host);
        try {
            level.explode(host, at.x, at.y, at.z, strength, fire && blocks, blocks ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
        } finally {
            SPARED.remove(host);
        }
        // a wet burst out of whoever set it off
        MotionEffects.gore(level, host, at, 24);
        if (host instanceof MinionEntity) {
            MotionEffects.gibs(level, host, at, 10);
        }
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, host.getSoundSource(), 1.5F, 0.5F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_BREAK, host.getSoundSource(), 1.2F, 0.6F);
        if (minionPowersDown && host instanceof MinionEntity minion) {
            minion.powerDown();
        }
    }
}
