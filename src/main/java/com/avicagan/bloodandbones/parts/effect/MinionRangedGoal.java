package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * A minion's ranged attack (docs/PARTS-AND-TRAITS.md sections 5.4 and 6.4): arms or an organ with a passive projectile or
 * hitscan entry (skeleton arms loose arrows, say) make it fight at a distance through vanilla's {@link RangedAttackGoal},
 * closing to the longest range among its shots and firing every so often at the shortest cooldown (each shot keeping its
 * own cooldown and blood cost, see {@link RangedEffects#rangedAttack}). With arms that can hit, it lets its melee goal have
 * anything within 4 blocks; hungry, it gives way to going to drink.
 * <p>
 * Vanilla's goal is built for one range and one rate, so this holds one and builds it again when the minion's shots change.
 */
public class MinionRangedGoal extends Goal {
    /** Nearer than this, a minion with arms to hit with fights hand to hand. */
    public static final double MELEE = 4.0;

    private final MinionEntity minion;
    @Nullable
    private RangedAttackGoal inner;
    private int interval = -1;
    private float radius = -1.0F;
    /** The traits its shots were last worked out from: they are worked out again only when those change. */
    @Nullable
    private ActiveTraits builtFor;
    private boolean hasShots;

    public MinionRangedGoal(MinionEntity minion) {
        this.minion = minion;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return ready() && build() && inner.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return ready() && inner != null && inner.canContinueToUse();
    }

    /** Awake, minded, fed, with a target far enough off (or no arms to fight up close with) and a shot to fire. */
    private boolean ready() {
        LivingEntity target = minion.getTarget();
        if (target == null || !target.isAlive() || minion.poweredDown() || minion.stats().mindless() || minion.powerShare() < MinionEntity.HUNGRY) {
            return false;
        }
        return !(minion.distanceToSqr(target) < MELEE * MELEE && hands());
    }

    /** Whether it has an arm that hits (asked every tick it fights, so no stream). */
    private boolean hands() {
        List<MinionStats.Strike> strikes = minion.stats().strikes();
        for (int i = 0; i < strikes.size(); i++) {
            if (!"pacifist".equals(strikes.get(i).style())) {
                return true;
            }
        }
        return false;
    }

    /** Vanilla's goal for the range and rate of the minion's shots now; false if it has none. Looked at again only when its traits change. */
    private boolean build() {
        ActiveTraits traits = ActiveTraits.of(minion);
        if (traits == builtFor) {
            return hasShots;
        }
        builtFor = traits;
        List<ActiveTraits.Found<?>> shots = RangedEffects.shots(traits);
        hasShots = !shots.isEmpty();
        if (!hasShots) {
            return false;
        }
        float reach = 0.0F;
        int every = Integer.MAX_VALUE;
        for (ActiveTraits.Found<?> shot : shots) {
            reach = Math.max(reach, shot.facet().range());
            every = Math.min(every, Math.max(20, shot.facet().cooldown()));
        }
        if (inner == null || reach != radius || every != interval) {
            radius = reach;
            interval = every;
            inner = new RangedAttackGoal(minion, 1.0, every, every + 10, reach);
        }
        return true;
    }

    @Override
    public void start() {
        if (inner != null) {
            inner.start();
        }
    }

    @Override
    public void stop() {
        if (inner != null) {
            inner.stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (inner != null) {
            inner.tick();
        }
    }
}
