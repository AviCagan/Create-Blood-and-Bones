package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.TraitEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The goals Social's effects hand out: a minion hunting by a sense, and mobs coming to the defence of a trait's carrier. */
public final class SocialGoals {
    /** The jobs that go after monsters on their own, and so hunt by a sense too. */
    public static final Set<String> HUNTING_JOBS = Set.of("guard", "hunter", "sentry");
    /** How far a guard goes from home for a monster, as its own targeting does. */
    private static final double GUARD_REACH = 16.0;

    private SocialGoals() {
    }

    /**
     * A minion that hunts (a guard) and senses by echolocation or tremor finds its monsters through walls
     * (docs/PARTS-AND-TRAITS.md section 5.4, type 11): the nearest within the sense's range that it senses, whether it
     * can see it or not, and keeps it while unseen. By tremor it feels only what moves; a guard still keeps near home.
     */
    public static class SenseTarget extends NearestAttackableTargetGoal<Mob> {
        private final MinionEntity minion;

        public SenseTarget(MinionEntity minion) {
            super(minion, Mob.class, 10, false, false, null);
            this.minion = minion;
        }

        @Override
        public boolean canUse() {
            if (minion.poweredDown() || minion.stats().mindless() || !minion.stats().fights() || HUNTING_JOBS.stream().noneMatch(minion::hasJob)) {
                return false;
            }
            List<ActiveTraits.Found<SenseEffect>> senses = hunting(minion);
            if (senses.isEmpty()) {
                return false;
            }
            float range = 0.0F;
            for (ActiveTraits.Found<SenseEffect> found : senses) {
                range = Math.max(range, found.effect().reach(found.entry().level()));
            }
            boolean guard = minion.hasJob("guard");
            Vec3 home = Vec3.atCenterOf(minion.home());
            targetConditions = TargetingConditions.forCombat().range(range).ignoreLineOfSight().selector(target ->
                    target instanceof Enemy && !(target instanceof MinionEntity) && (!guard || target.distanceToSqr(home) < GUARD_REACH * GUARD_REACH)
                            && senses.stream().anyMatch(f -> f.effect().senses(minion, target, f.entry().level())));
            return super.canUse();
        }

        /** Its echolocation and tremor senses that work now, their conditions holding. */
        private static List<ActiveTraits.Found<SenseEffect>> hunting(MinionEntity minion) {
            List<ActiveTraits.Found<SenseEffect>> all = ActiveTraits.peek(minion).find(SenseEffect.class);
            if (all.isEmpty()) {
                return all;
            }
            return all.stream().filter(f -> ("echolocate".equals(f.effect().kind()) || "tremor".equals(f.effect().kind()))
                    && TraitEvents.holds(minion, f.entry(), f.facet(), null)).toList();
        }
    }

    /**
     * Mobs a trait makes defend whoever carries it (a reaction in "defend" mode: golems for the beloved): whatever hurt a
     * carrier within the radius lately, or whatever it is fighting, becomes their target. Never the carrier itself, nor
     * one of its side.
     */
    public static class DefendHost extends TargetGoal {
        private final ResourceLocation trait;
        private final float radius;
        @Nullable
        private LivingEntity enemy;

        public DefendHost(Mob mob, ResourceLocation trait, float radius) {
            super(mob, false, false);
            this.trait = trait;
            this.radius = radius;
            setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            enemy = null;
            if (mob.getRandom().nextInt(reducedTickDelay(10)) != 0) {
                return false;
            }
            for (LivingEntity carrier : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(radius),
                    e -> e.isAlive() && ActiveTraits.peek(e).level(trait) > 0)) {
                LivingEntity attacker = carrier.getLastHurtByMob();
                if (attacker != null && carrier.tickCount - carrier.getLastHurtByMobTimestamp() < 100 && fair(carrier, attacker)) {
                    enemy = attacker;
                    return true;
                }
                LivingEntity attacked = carrier.getLastHurtMob();
                if (attacked != null && carrier.tickCount - carrier.getLastHurtMobTimestamp() < 100 && fair(carrier, attacked)) {
                    enemy = attacked;
                    return true;
                }
            }
            return false;
        }

        private boolean fair(LivingEntity carrier, LivingEntity enemy) {
            return enemy != mob && enemy.isAlive() && !SocialFilter.allied(carrier, enemy) && enemy.getType() != mob.getType()
                    && canAttack(enemy, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(enemy);
            super.start();
        }
    }
}
