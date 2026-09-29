package com.avicagan.bloodandbones.parts.effect;

import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionTask;
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

/** The goals Social's effects hand out: a minion hunting by a sense, and mobs coming to the defence of a trait's carrier. */
public final class SocialGoals {

    private SocialGoals() {
    }

    /**
     * A minion on a task that goes after monsters on its own (a fight: Guard, Sentry, Hunter or Sapper, but a guard with its
     * maker, which goes only for what its maker fights) that senses by echolocation or tremor finds its monsters through
     * walls (docs/PARTS-AND-TRAITS.md section 5.4, type 11): the nearest within the sense's range that it senses, whether it
     * can see it or not, and keeps it while unseen. By tremor it feels only what moves; it still keeps to its reach of where
     * its task is centred (home, a sentry's post, or its maker).
     */
    public static class SenseTarget extends NearestAttackableTargetGoal<Mob> {
        private final MinionEntity minion;

        public SenseTarget(MinionEntity minion) {
            super(minion, Mob.class, 10, false, false, null);
            this.minion = minion;
        }

        @Override
        public boolean canUse() {
            if (minion.poweredDown() || ActiveTraits.peek(minion).find(SenseEffect.class).isEmpty() || minion.stats().mindless()
                    || !minion.stats().fights() || !hunts(minion)) {
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
            Vec3 centre = minion.centre();
            double reach = minion.reach();
            targetConditions = TargetingConditions.forCombat().range(range).ignoreLineOfSight().selector(target ->
                    target instanceof Enemy && !(target instanceof MinionEntity) && target.distanceToSqr(centre) < reach * reach
                            && senses.stream().anyMatch(f -> f.effect().senses(minion, target, f.entry().level())));
            return super.canUse();
        }

        /**
         * Whether its task's goals go after monsters on their own: the guard's (but with its maker), the sentry's, the hunter's
         * and the sapper's. What the goals do is code; the task file's kind only says how a disposition scales it.
         */
        private static boolean hunts(MinionEntity minion) {
            MinionTask task = minion.task();
            return (task == MinionTask.GUARD || task == MinionTask.SENTRY || task == MinionTask.HUNTER || task == MinionTask.SAPPER)
                    && !(task == MinionTask.GUARD && minion.withMaker());
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
     * carrier within the radius lately, or any monster it is fighting, becomes their target. Never the carrier itself,
     * one of its side, or one of their own kind.
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
                // what it fights too, but only a monster: a golem never turns on a villager or a player for it
                LivingEntity attacked = carrier.getLastHurtMob();
                if (attacked instanceof Enemy && carrier.tickCount - carrier.getLastHurtMobTimestamp() < 100 && fair(carrier, attacked)) {
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
