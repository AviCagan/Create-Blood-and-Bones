package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitContext;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * A sense (docs/PARTS-AND-TRAITS.md section 5.4, type 11): something the host notices that others do not.
 * <ul>
 *     <li>{@code night}: night vision, kept up quietly</li>
 *     <li>{@code echolocate}: every 5 seconds a click, and the creatures within range show as outlines for a moment</li>
 *     <li>{@code tremor}: anything moving within range (not still, not creeping) shows as an outline</li>
 *     <li>{@code reveal}: the creatures the filter picks (monsters, unless it says) show as outlines</li>
 *     <li>{@code see_invisible}: invisible creatures show as outlines</li>
 *     <li>{@code alert}: a ping, and which way to look, when something within range sets its sights on the host</li>
 * </ul>
 * Outlines go through walls and are the wearer's alone ({@link SensePayload}, drawn by the client), never vanilla
 * Glowing, which everyone would see. A minion hunts by echolocation or tremor through walls ({@link SocialGoals.SenseTarget})
 * within the range, sees the invisible, and turns to look at whatever takes aim at it.
 *
 * @param range  how far it reaches, in blocks
 * @param filter which creatures it picks out ({@link SocialFilter}); by default monsters for reveal, anything for the rest
 */
public record SenseEffect(String kind, LevelBasedValue range, Optional<SocialFilter> filter) implements TraitEffect.Effect {
    public static final List<String> KINDS = List.of("night", "see_invisible", "echolocate", "tremor", "reveal", "alert");
    public static final MapCodec<SenseEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.validate(k -> KINDS.contains(k) ? DataResult.success(k) : DataResult.error(() -> "Unknown sense '" + k + "': one of " + KINDS))
                    .fieldOf("kind").forGetter(SenseEffect::kind),
            LevelBasedValue.CODEC.optionalFieldOf("range", LevelBasedValue.constant(16.0F)).forGetter(SenseEffect::range),
            SocialFilter.CODEC.optionalFieldOf("filter").forGetter(SenseEffect::filter)
    ).apply(i, SenseEffect::new));

    /** Echolocation clicks this often. */
    public static final int ECHO_TICKS = 100;
    /** The same mob taking aim again this soon pings no more. */
    public static final int ALERT_QUIET_TICKS = 100;
    /** At most this many creatures are outlined at once, the nearest. */
    private static final int MOST = 64;

    /** When each host was last pinged about each mob, by the mob's entity id. */
    private static final Map<LivingEntity, Map<Integer, Long>> ALERTED = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    /** Whom it picks out: its filter, or by default monsters for reveal and anything for the rest. */
    public SocialFilter who() {
        return filter.orElse("reveal".equals(kind) ? SocialFilter.HOSTILE : SocialFilter.ANY);
    }

    /** How far it reaches at this trait level. */
    public float reach(int level) {
        return Math.max(0.0F, range.calculate(level));
    }

    /** Whether the host notices this creature by it: within reach, picked by the filter, moving for tremor, invisible for see_invisible. */
    public boolean senses(LivingEntity host, LivingEntity other, int level) {
        float reach = reach(level);
        if (host.distanceToSqr(other) > reach * reach || !who().test(host, other)) {
            return false;
        }
        return switch (kind) {
            case "tremor" -> SocialFilter.moving(other);
            case "see_invisible" -> other.isInvisible();
            default -> true;
        };
    }

    /** Kept up every half second: night vision; outlines refreshed (echolocation every 5 seconds, the rest every second). */
    @Override
    public void keepUp(TraitContext ctx) {
        LivingEntity host = ctx.host();
        switch (kind) {
            case "night" -> nightVision(host, 220);
            case "echolocate" -> {
                if (host.tickCount % ECHO_TICKS < 10) {
                    pulse(ctx, 60);
                }
            }
            case "tremor", "reveal", "see_invisible" -> {
                if (host.tickCount % 20 < 10) {
                    pulse(ctx, 30);
                }
            }
            default -> {
                // alert: heard when something takes aim (SocialEffects#onChangeTarget)
            }
        }
    }

    /** Once, from a trigger: night vision for a while, a ping about whoever set it off, or one sweep of outlines. */
    @Override
    public void run(TraitContext ctx) {
        switch (kind) {
            case "night" -> nightVision(ctx.host(), 600);
            case "alert" -> {
                if (ctx.other() != null && senses(ctx.host(), ctx.other(), ctx.traitLevel())) {
                    alert(ctx.host(), ctx.other());
                }
            }
            default -> pulse(ctx, 60);
        }
    }

    private static void nightVision(LivingEntity host, int ticks) {
        host.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, ticks, 0, true, false, true));
    }

    /**
     * One sweep: the creatures sensed now, outlined for the wearer alone for this long (an echolocation's click goes out
     * even when nothing answers; the steady senses send nothing when there is nothing to show). Minions hunt by it instead.
     */
    private void pulse(TraitContext ctx, int ticks) {
        if (ctx.host() instanceof ServerPlayer player) {
            List<Integer> ids = sensed(player, ctx.traitLevel()).stream().map(LivingEntity::getId).toList();
            if (!ids.isEmpty() || "echolocate".equals(kind)) {
                PacketDistributor.sendToPlayer(player, new SensePayload(kind, ids, ticks));
            }
        }
    }

    /** The creatures the host senses by this now, nearest first. */
    public List<LivingEntity> sensed(LivingEntity host, int level) {
        float reach = reach(level);
        List<LivingEntity> found = new java.util.ArrayList<>(host.level().getEntitiesOfClass(LivingEntity.class, host.getBoundingBox().inflate(reach),
                e -> senses(host, e, level)));
        if (found.size() > MOST) {
            found.sort(Comparator.comparingDouble(host::distanceToSqr));
            found = found.subList(0, MOST);
        }
        return found;
    }

    /**
     * A mob has set its sights on the host: a player gets a ping and which way to look ({@link AlertPayload}), a minion
     * turns to look at it. The same mob pings again only after a few seconds.
     */
    public static void alert(LivingEntity host, LivingEntity mob) {
        long now = host.level().getGameTime();
        Map<Integer, Long> own = ALERTED.computeIfAbsent(host, h -> new HashMap<>());
        Long last = own.get(mob.getId());
        if (last != null && now - last < ALERT_QUIET_TICKS) {
            return;
        }
        own.values().removeIf(t -> now - t >= ALERT_QUIET_TICKS);
        own.put(mob.getId(), now);
        if (host instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new AlertPayload(mob.getId(), mob.getX(), mob.getEyeY(), mob.getZ(), mob.getType().getDescriptionId()));
        } else if (host instanceof MinionEntity minion) {
            minion.getLookControl().setLookAt(mob, 30.0F, 30.0F);
        }
    }

    /** Whether the host was pinged about this mob in the last few seconds. */
    public static boolean alerted(LivingEntity host, LivingEntity mob) {
        Map<Integer, Long> own = ALERTED.get(host);
        Long last = own == null ? null : own.get(mob.getId());
        return last != null && host.level().getGameTime() - last < ALERT_QUIET_TICKS;
    }

    /** The host's passive senses of one kind that work now (their condition is the caller's to check). */
    public static List<ActiveTraits.Found<SenseEffect>> of(LivingEntity host, String kind) {
        List<ActiveTraits.Found<SenseEffect>> all = ActiveTraits.peek(host).find(SenseEffect.class);
        if (all.isEmpty()) {
            return all;
        }
        return all.stream().filter(f -> f.effect().kind().equals(kind)).toList();
    }

    static void clear() {
        ALERTED.clear();
    }
}
