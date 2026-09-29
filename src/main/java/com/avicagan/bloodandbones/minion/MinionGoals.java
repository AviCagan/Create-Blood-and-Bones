package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.config.BBServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** What a minion does whatever its task, by task where the goal is shared (docs/NEXT.md 1.1), and how it looks after its blood. */
public final class MinionGoals {
    /** How fast it drinks at a trough, mB a second. */
    public static final int DRINK = 100;

    private MinionGoals() {
    }

    /**
     * Who it fights (docs/NEXT.md 1.1): on any task, what hurts it (Idle does no more); a guard at home the monsters within its
     * reach of home, and a guard with its maker what hurts its maker and what its maker hits, as a tamed wolf does. Never its
     * maker (MinionEntity#canAttack), and a body with nothing to fight with (no arm that hits, no head to bite with) takes no
     * target at all; folded arms under a head bite with it. A body with no head fights only what touches it ({@link #touches}).
     */
    static void targets(MinionEntity minion, GoalSelector targets) {
        berserk(minion, targets);
        targets.addGoal(1, new HurtByTargetGoal(minion) {
            @Override
            public boolean canUse() {
                return !minion.stats().mindless() && minion.stats().fights() && super.canUse();
            }
        });
        targets.addGoal(1, new TouchTarget(minion));
        targets.addGoal(2, new DefendMaker(minion, false));
        targets.addGoal(2, new DefendMaker(minion, true));
        targets.addGoal(3, new NearestAttackableTargetGoal<>(minion, Mob.class, 10, true, false,
                target -> target instanceof Enemy && !(target instanceof MinionEntity) && target.distanceToSqr(minion.centre()) < minion.reach() * minion.reach()
                        && minion.filter().allows(minion.level(), target)) {
            /** A guard's at home, and a sapper's: what it walks up to and blows up beside. */
            @Override
            public boolean canUse() {
                return (minion.hasTask(MinionTask.GUARD) && !minion.withMaker() || minion.hasTask(MinionTask.SAPPER)) && !minion.stats().mindless()
                        && minion.stats().fights() && super.canUse();
            }

            /** No further than its head notices things (a blind head: 4 blocks); asked first while it is being made. */
            @Override
            protected double getFollowDistance() {
                return minion.build().isEmpty() ? super.getFollowDistance() : Math.min(super.getFollowDistance(), minion.stats().sight());
            }
        });
    }

    /**
     * A berserk head (docs/PARTS-AND-TRAITS.md section 8.2: the zoglin, the killer bunny, a vindicator named Johnny) goes
     * for any creature it can see nearby but its own side (MinionEntity#canAttack); players only when they hurt it.
     */
    static void berserk(MinionEntity minion, GoalSelector targets) {
        targets.addGoal(3, new NearestAttackableTargetGoal<>(minion, Mob.class, 10, true, false,
                target -> !(target instanceof MinionEntity other && other.makerId() != null && other.makerId().equals(minion.makerId()))) {
            @Override
            public boolean canUse() {
                return !minion.build().isEmpty() && minion.stats().berserk() && !minion.stats().mindless() && minion.stats().fights() && super.canUse();
            }

            @Override
            protected double getFollowDistance() {
                return minion.build().isEmpty() ? super.getFollowDistance() : Math.min(super.getFollowDistance(), minion.stats().sight());
            }
        });
    }

    /**
     * Whether its navigation can make a path from where it is: on the ground, in water, or (a flier) in the air. A
     * walker in the air cannot, which is not the same as there being no way.
     */
    static boolean canPath(MinionEntity minion) {
        return minion.onGround() || minion.isInLiquid() || minion.isNoGravity();
    }

    /**
     * The way to a place a goal chose: a fresh path each second (not each tick: a path search is dear, and asking again
     * while one is being followed costs nothing), and giving up when it gets nowhere: five seconds without moving a
     * block (its path ran out short of the place, a door shut behind it, a climber could not get over).
     */
    static final class Approach {
        private static final int REPATH = 20;
        private static final int STALL = 100;
        private int pathedAt;
        private int movedAt;
        private Vec3 from = Vec3.ZERO;

        /** Setting out, or there already. */
        void reset(MinionEntity minion) {
            pathedAt = minion.tickCount - REPATH;
            movedAt = minion.tickCount;
            from = minion.position();
        }

        /** A step toward it; false once it is plain it cannot get there. */
        boolean step(MinionEntity minion, BlockPos target, int accuracy, double speed) {
            if (minion.position().distanceToSqr(from) > 1.0) {
                from = minion.position();
                movedAt = minion.tickCount;
            } else if (minion.tickCount - movedAt > STALL) {
                return false;
            }
            if (minion.tickCount - pathedAt >= REPATH) {
                Path path = minion.getNavigation().createPath(target, accuracy);
                // none in mid-hop or mid-jump: ask again shortly, keeping the path it has
                pathedAt = path == null ? minion.tickCount - REPATH + 5 : minion.tickCount;
                if (path != null) {
                    minion.getNavigation().moveTo(path, speed);
                }
            }
            return true;
        }
    }

    /** Places a goal found it could not get to, forgotten every half minute (a wall may have come down since). */
    static final class Unreachable {
        private final List<BlockPos> places = new ArrayList<>();
        private int forgotAt;

        boolean contains(MinionEntity minion, BlockPos pos) {
            if (minion.tickCount - forgotAt > 600) {
                places.clear();
                forgotAt = minion.tickCount;
            }
            return places.contains(pos);
        }

        void add(BlockPos pos) {
            places.add(pos.immutable());
        }
    }

    /** Whether it can reach this place by a path now: one that gets there, or (a climber) any at all, its own way going over. */
    static boolean reaches(MinionEntity minion, BlockPos pos, int accuracy) {
        Path path = minion.getNavigation().createPath(pos, accuracy);
        return path != null && (path.canReach() || minion.stats().climbs());
    }

    /** Whether every block from here to there is loaded: a goal never reads a far-off place and so loads it. */
    static boolean loaded(MinionEntity minion, BlockPos from, BlockPos to) {
        return minion.level().hasChunksAt(from, to);
    }

    /**
     * A guard with its maker goes for what hurts its maker ({@code hit} false) or what its maker hits ({@code hit} true),
     * each time anew, as a tamed wolf does (vanilla's OwnerHurtByTargetGoal and OwnerHurtTargetGoal): never its own side,
     * someone's tamed animal or horse, a player its maker may not hurt, a creeper or an armour stand. It needs a head to
     * see it by. Its reach is how far from its maker it goes: nothing further off than that from them, and it gives up one
     * that gets further.
     */
    static class DefendMaker extends TargetGoal {
        /** A blow older than this is past: it is not taken up late, when the task was only just given. */
        private static final int FRESH = 100;
        private final MinionEntity minion;
        private final boolean hit;
        @Nullable
        private LivingEntity enemy;
        private int timestamp;

        DefendMaker(MinionEntity minion, boolean hit) {
            super(minion, false);
            this.minion = minion;
            this.hit = hit;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.GUARD) || minion.stats().mindless() || !minion.stats().fights()) {
                return false;
            }
            Player maker = minion.workingMaker();
            if (maker == null) {
                return false;
            }
            enemy = hit ? maker.getLastHurtMob() : maker.getLastHurtByMob();
            int when = hit ? maker.getLastHurtMobTimestamp() : maker.getLastHurtByMobTimestamp();
            return enemy != null && when != timestamp && maker.tickCount - when < FRESH && wantsToAttack(enemy, maker) && withinReach(enemy, maker)
                    && canAttack(enemy, TargetingConditions.DEFAULT);
        }

        @Override
        public boolean canContinueToUse() {
            Player maker = minion.workingMaker();
            LivingEntity target = mob.getTarget();
            return maker != null && target != null && withinReach(target, maker) && super.canContinueToUse();
        }

        private boolean withinReach(LivingEntity target, Player maker) {
            double reach = minion.reach();
            return target.distanceToSqr(maker) <= reach * reach;
        }

        @Override
        public void start() {
            mob.setTarget(enemy);
            Player maker = minion.workingMaker();
            if (maker != null) {
                timestamp = hit ? maker.getLastHurtMobTimestamp() : maker.getLastHurtByMobTimestamp();
            }
            super.start();
        }

        /** What a tamed wolf will go for on its owner's behalf (Wolf#wantsToAttack), and never the maker's own side. */
        private boolean wantsToAttack(LivingEntity target, Player maker) {
            if (target instanceof MinionEntity || target instanceof net.minecraft.world.entity.monster.Creeper
                    || target instanceof net.minecraft.world.entity.decoration.ArmorStand || target == maker) {
                return false;
            }
            if (target instanceof Player player && !maker.canHarmPlayer(player)) {
                return false;
            }
            return !(target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse && horse.isTamed())
                    && !(target instanceof net.minecraft.world.entity.TamableAnimal tame && tame.isTame());
        }
    }

    /** How near something must come to a body with no head for it to feel it: against it, or all but. */
    static final double TOUCH = 0.5;

    /** Whether this touches it: a body with no head knows only what it feels (docs/NEXT.md 1.1). */
    public static boolean touches(MinionEntity minion, Entity other) {
        return other.getBoundingBox().intersects(minion.getBoundingBox().inflate(TOUCH));
    }

    /**
     * A body with no head strikes only what touches it: what hurt it, and on a task that goes for monsters (Guard, Sentry)
     * any monster against it, and a hunter's prey there (where mobs may do harm). Idle only fights back. A sapper with no
     * head sets itself off at a monster against it, or what hurt it there, having nothing else to find its target by
     * (MinionSapper.Sap). It drops what moves away.
     */
    static class TouchTarget extends TargetGoal {
        private final MinionEntity minion;
        @Nullable
        private LivingEntity felt;

        TouchTarget(MinionEntity minion) {
            super(minion, false);
            this.minion = minion;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!minion.stats().mindless() || !minion.stats().fights() && !minion.hasTask(MinionTask.SAPPER) || minion.poweredDown() || minion.tickCount % 5 != 0) {
                return false;
            }
            felt = null;
            LivingEntity hurtBy = minion.getLastHurtByMob();
            if (hurtBy != null && hurtBy.isAlive() && touches(minion, hurtBy) && minion.canAttack(hurtBy)) {
                felt = hurtBy;
                return true;
            }
            MinionTask task = minion.task();
            boolean monsters = task == MinionTask.GUARD || task == MinionTask.SENTRY || task == MinionTask.SAPPER;
            boolean hunts = task == MinionTask.HUNTER && net.neoforged.neoforge.event.EventHooks.canEntityGrief(minion.level(), minion);
            if (!monsters && !hunts) {
                return false;
            }
            List<String> prey = hunts ? MinionTasks.prey(minion) : List.of();
            for (LivingEntity other : minion.level().getEntitiesOfClass(LivingEntity.class, minion.getBoundingBox().inflate(TOUCH),
                    e -> e != minion && e.isAlive() && minion.canAttack(e))) {
                if (monsters && other instanceof Enemy && !(other instanceof MinionEntity) && minion.filter().allows(minion.level(), other)
                        || hunts && other instanceof net.minecraft.world.entity.PathfinderMob mob && MinionTasks.isPrey(minion, mob, prey)) {
                    felt = other;
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = minion.getTarget();
            return target != null && target.isAlive() && touches(minion, target) && minion.stats().mindless();
        }

        @Override
        public void start() {
            mob.setTarget(felt);
            super.start();
        }
    }

    /** Ticks between blows a melee goal lands: vanilla's second, less for each arm that strikes past two (docs/NEXT.md 1.2). */
    public static final int BLOW_EVERY = 20;

    /**
     * Ticks between its blows: a second, as vanilla's melee goal lands them, 15% sooner for each arm that strikes past two, up
     * to 60% (spec 6.4; the strike rate its Blow counts).
     */
    public static int blowTicks(MinionEntity minion) {
        MinionFitness.Body body = minion.fitnessBody();
        return body == null ? BLOW_EVERY : Math.max(1, Math.round(BLOW_EVERY / body.strikeRate()));
    }

    /**
     * A body with no head strikes what it holds for its target while that touches it, a blow as often as a melee goal lands
     * one, and never goes after it.
     */
    public static class Feel extends Goal {
        private final MinionEntity minion;
        private int cooldown;

        public Feel(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = minion.getTarget();
            // a sapper sets itself off instead (MinionSapper.Sap)
            return minion.stats().mindless() && minion.stats().fights() && !minion.hasTask(MinionTask.SAPPER) && target != null && target.isAlive()
                    && touches(minion, target);
        }

        @Override
        public void start() {
            minion.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = minion.getTarget();
            if (target == null) {
                return;
            }
            minion.getNavigation().stop();
            minion.getLookControl().setLookAt(target, 30.0F, 30.0F);
            cooldown = Math.max(0, cooldown - 1);
            if (cooldown == 0) {
                cooldown = blowTicks(minion);
                minion.doHurtTarget(target);
            }
        }
    }

    /**
     * It goes for its target with its arms, or its teeth if it has none that strike (folded arms under a head): a blow a
     * second, as vanilla's melee goal lands them, more often with more arms that strike ({@link #blowTicks}).
     */
    public static class Bite extends MeleeAttackGoal {
        private static final double SPEED = 1.2;
        /** How near what it goes for must be for it to walk straight at it when its path runs out short. */
        private static final double LAST_STRETCH = 4.0;
        private final MinionEntity minion;

        /** When it may land its next blow. */
        private int nextBlow;

        public Bite(MinionEntity minion) {
            super(minion, SPEED, true);
            this.minion = minion;
        }

        @Override
        public void start() {
            super.start();
            // its first blow at once, as vanilla's goal lands it
            nextBlow = minion.tickCount;
        }

        /** Vanilla's blow, at its own rate: a second, sooner with more arms that strike. */
        @Override
        protected void checkAndPerformAttack(LivingEntity target) {
            if (minion.tickCount >= nextBlow && minion.isWithinMeleeAttackRange(target) && minion.getSensing().hasLineOfSight(target)) {
                nextBlow = minion.tickCount + blowTicks(minion);
                minion.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                minion.doHurtTarget(target);
            }
        }

        @Override
        public boolean canUse() {
            // a body with nothing to fight with never attacks (folded arms under a head bite with it), a sentry strikes or
            // shoots from where it stands rather than closing in, a sapper walks up and blows itself up instead, and a body
            // with no head only strikes what touches it (Feel)
            return !minion.stats().mindless() && minion.stats().fights() && !minion.hasTask(MinionTask.SENTRY) && !minion.hasTask(MinionTask.SAPPER)
                    && super.canUse();
        }

        /**
         * Its path ended out of reach of what it goes for (a body a block wide is pathed as two blocks wide, so by a wall
         * or in a corner its path stops a block or two short, and vanilla's goal paths again only once the target moves):
         * it walks the last of the way straight at what it can see, if that way is safe ground ({@link #clearWay}). A path
         * stops short just as often because the way on is lava, fire or a drop, and the move control that walks it
         * straight there looks at none of it.
         */
        @Override
        public void tick() {
            super.tick();
            LivingEntity target = minion.getTarget();
            if (target != null && minion.getNavigation().isDone() && !minion.isWithinMeleeAttackRange(target)
                    && minion.distanceToSqr(target) < LAST_STRETCH * LAST_STRETCH && minion.getSensing().hasLineOfSight(target)
                    && clearWay(minion, target.position())) {
                minion.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), SPEED);
            }
        }
    }

    /**
     * Whether the straight way from where it stands to there is safe to walk: every block its body would pass over (as
     * wide as it is, each half block along) is one its own path finding costs nothing, so no lava (bar a lava walker's), fire
     * or the edge of it, cactus, berry bush, powder snow, water or shut door, with footing under it no more than a step
     * down or up from where it stands, so never over a drop.
     */
    static boolean clearWay(MinionEntity minion, Vec3 to) {
        PathfindingContext context = new PathfindingContext(minion.level(), minion);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        Vec3 from = minion.position();
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        int steps = Math.max(1, Mth.ceil(Math.sqrt(dx * dx + dz * dz) * 2.0));
        double half = minion.getBbWidth() / 2.0 - 1.0E-3;
        int y = minion.getBlockY();
        for (int i = 1; i <= steps; i++) {
            double x = from.x + dx * i / steps;
            double z = from.z + dz * i / steps;
            for (int bx = Mth.floor(x - half); bx <= Mth.floor(x + half); bx++) {
                for (int bz = Mth.floor(z - half); bz <= Mth.floor(z + half); bz++) {
                    if (!footing(minion, context, at, bx, y, bz)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Safe footing in this column: at the height it stands, a step down, or a step up. */
    private static boolean footing(MinionEntity minion, PathfindingContext context, BlockPos.MutableBlockPos at, int x, int y, int z) {
        PathType here = WalkNodeEvaluator.getPathTypeStatic(context, at.set(x, y, z));
        if (here == PathType.OPEN) {
            // nothing to stand on at this height: a step down is fine, a drop (or water, or lava) is not
            return safe(minion, WalkNodeEvaluator.getPathTypeStatic(context, at.set(x, y - 1, z)));
        }
        if (here == PathType.BLOCKED) {
            // a block at its feet: a step up onto it
            return safe(minion, WalkNodeEvaluator.getPathTypeStatic(context, at.set(x, y + 1, z)));
        }
        return safe(minion, here);
    }

    private static boolean safe(MinionEntity minion, PathType type) {
        return type != PathType.OPEN && type != PathType.BLOCKED && minion.getPathfindingMalus(type) == 0.0F;
    }

    /**
     * Its organ at work in a fight (docs/PARTS-AND-TRAITS.md section 6.4): with a target within an activate effect's
     * range and that effect's condition holding, it fires it, paying its cost from its own blood (brass: its canister).
     * It takes no control of moving or looking, so it fires while it closes in.
     */
    public static class UseOrgan extends Goal {
        private final MinionEntity minion;
        @Nullable
        private com.avicagan.bloodandbones.parts.Activation.Facet facet;

        public UseOrgan(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = minion.getTarget();
            if (target == null || !target.isAlive() || minion.poweredDown() || minion.stats().mindless()) {
                return false;
            }
            facet = com.avicagan.bloodandbones.parts.Activation.readyFor(minion, target);
            return facet != null;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            if (facet != null && minion.getTarget() != null) {
                com.avicagan.bloodandbones.parts.Activation.fire(minion, facet, minion.getTarget());
            }
            facet = null;
        }
    }

    /** Low on blood, it walks to the nearest Blood Trough it can reach and drinks its fill. */
    public static class SeekBlood extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos trough;
        private final Unreachable unreachable = new Unreachable();
        private final Approach approach = new Approach();

        public SeekBlood(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        /** Its tick counts game ticks (a drink a second, a fresh path now and then), so it must run on every one. */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (minion.cybernetic() || minion.powerShare() >= MinionEntity.HUNGRY || !canPath(minion) || minion.getRandom().nextInt(20) != 0) {
                return false;
            }
            trough = find();
            return trough != null;
        }

        /** The nearest trough with blood in it that a path reaches (one right beside it through a wall does not count). */
        @Nullable
        private BlockPos find() {
            int radius = BBServerConfig.troughRadius();
            List<BlockPos> near = BloodTroughBlockEntity.all(minion.level()).stream()
                    .filter(p -> p.distSqr(minion.blockPosition()) < (double) radius * radius && !unreachable.contains(minion, p)
                            && minion.level().isLoaded(p) && minion.level().getBlockEntity(p) instanceof BloodTroughBlockEntity t && t.amount() > 0)
                    .sorted(Comparator.comparingDouble(p -> p.distSqr(minion.blockPosition()))).toList();
            for (BlockPos pos : near) {
                // a climber goes straight up and over what a walking path cannot (its navigation heads for the spot
                // itself when the path runs out, as a spider's does)
                if (reaches(minion, pos, accuracy(minion))) {
                    return pos;
                }
                // it must walk there itself: a trough it cannot reach is no use to it
                unreachable.add(pos);
            }
            return null;
        }

        @Override
        public boolean canContinueToUse() {
            return trough != null && minion.powerShare() < 0.98F && minion.level().isLoaded(trough)
                    && minion.level().getBlockEntity(trough) instanceof BloodTroughBlockEntity t && t.amount() > 0;
        }

        @Override
        public void start() {
            approach.reset(minion);
        }

        @Override
        public void tick() {
            if (trough == null) {
                return;
            }
            if (minion.distanceToSqr(Vec3.atCenterOf(trough)) < reach(minion) * reach(minion) && overTheRim(minion, trough)) {
                minion.getNavigation().stop();
                minion.getLookControl().setLookAt(Vec3.atCenterOf(trough));
                approach.reset(minion);
                if (minion.tickCount % 20 == 0) {
                    drink(minion, trough);
                }
            } else if (!approach.step(minion, trough, accuracy(minion), 1.1)) {
                // it cannot get there after all: the next trough, if there is one
                unreachable.add(trough);
                trough = null;
            }
        }

        @Override
        public void stop() {
            trough = null;
        }
    }

    /**
     * How near a path has to end to count as reaching a trough: a mob wider than a block cannot stand right against
     * one (the path finder keeps its whole width clear), so a big body gets as far off as it is wide.
     */
    static int accuracy(MinionEntity minion) {
        return Mth.ceil(minion.getBbWidth()) + 1;
    }

    /** How far from a trough's middle it can drink: from wherever such a path ends, its head over the rim. */
    static double reach(MinionEntity minion) {
        return accuracy(minion) + 0.5 + minion.getBbWidth() / 2.0;
    }

    /**
     * Brass, running low, goes to the nearest Charging Cradle with a full canister in it and waits beside it; the
     * cradle does the swap. Like the trough, it must be able to walk there.
     */
    public static class SeekCradle extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos cradle;
        private final Unreachable unreachable = new Unreachable();
        private final Approach approach = new Approach();

        public SeekCradle(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.cybernetic() || minion.powerShare() >= MinionEntity.HUNGRY || !canPath(minion) || minion.getRandom().nextInt(20) != 0) {
                return false;
            }
            int radius = BBServerConfig.troughRadius();
            List<BlockPos> near = ChargingCradleBlockEntity.all(minion.level()).stream()
                    .filter(p -> p.distSqr(minion.blockPosition()) < (double) radius * radius && !unreachable.contains(minion, p) && serves(p))
                    .sorted(Comparator.comparingDouble(p -> p.distSqr(minion.blockPosition()))).toList();
            cradle = null;
            for (BlockPos pos : near) {
                if (reaches(minion, pos, accuracy(minion))) {
                    cradle = pos;
                    break;
                }
                unreachable.add(pos);
            }
            return cradle != null;
        }

        /** A cradle that can swap one in now: turning, a full canister in it, room for the empty. */
        private boolean serves(BlockPos pos) {
            return minion.level().isLoaded(pos) && minion.level().getBlockEntity(pos) instanceof ChargingCradleBlockEntity c && c.canServe();
        }

        @Override
        public boolean canContinueToUse() {
            return cradle != null && minion.powerShare() < MinionEntity.HUNGRY && serves(cradle);
        }

        @Override
        public void start() {
            approach.reset(minion);
        }

        @Override
        public void tick() {
            if (cradle == null) {
                return;
            }
            double near = ChargingCradleBlockEntity.REACH + 0.5;
            if (minion.distanceToSqr(Vec3.atCenterOf(cradle)) < near * near) {
                minion.getNavigation().stop();
                minion.getLookControl().setLookAt(Vec3.atCenterOf(cradle));
                approach.reset(minion);
            } else if (!approach.step(minion, cradle, 1, 1.1)) {
                unreachable.add(cradle);
                cradle = null;
            }
        }

        @Override
        public void stop() {
            cradle = null;
        }
    }

    /** A second's drink from a trough. */
    static void drink(MinionEntity minion, BlockPos trough) {
        if (minion.level().getBlockEntity(trough) instanceof BloodTroughBlockEntity t) {
            float room = minion.stats().reservoir() - minion.power();
            int taken = t.drink((int) Math.min(DRINK, Math.ceil(room)));
            if (taken > 0) {
                minion.feed(taken);
                minion.level().playSound(null, minion.blockPosition(), net.minecraft.sounds.SoundEvents.GENERIC_DRINK, net.minecraft.sounds.SoundSource.NEUTRAL, 0.6F, 0.6F);
            }
        }
    }

    /** Powered down: a trough within reach of where it lies, and not behind a wall or a floor, revives it. */
    static void drinkNearby(MinionEntity minion) {
        if (minion.cybernetic()) {
            return;
        }
        double reach = reach(minion);
        for (BlockPos pos : BloodTroughBlockEntity.all(minion.level())) {
            if (pos.distToCenterSqr(minion.position()) < reach * reach && overTheRim(minion, pos)) {
                drink(minion, pos);
                return;
            }
        }
    }

    /** Whether nothing solid stands between its head and the trough: it drinks over the rim, never through a wall. */
    static boolean overTheRim(MinionEntity minion, BlockPos trough) {
        net.minecraft.world.phys.BlockHitResult hit = minion.level().clip(new net.minecraft.world.level.ClipContext(minion.getEyePosition(),
                Vec3.atCenterOf(trough), net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, minion));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getBlockPos().equals(trough);
    }

    /**
     * On a task done with its maker, it keeps near them while they are in the same world within 64 blocks
     * ({@link MinionEntity#workingMaker}), as a tamed wolf follows its owner (vanilla's FollowOwnerGoal): it sets off once
     * they are six blocks off and stops three short, looking at them, its path worked out again every half second. Idle's
     * reach is how far it keeps from them, as it is from home: it sets off two blocks past its reach and stops one short of
     * it, never nearer than three (6 and 3 at its own 4).
     */
    public static class FollowMaker extends Goal {
        private static final double START = 6.0;
        private static final double STOP = 3.0;
        private final MinionEntity minion;
        @Nullable
        private Player maker;
        private int repath;

        public FollowMaker(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            maker = minion.workingMaker();
            double start = setOff();
            return maker != null && minion.distanceToSqr(maker) > start * start && minion.getTarget() == null && canPath(minion);
        }

        @Override
        public boolean canContinueToUse() {
            double stop = stopAt();
            return maker != null && maker == minion.workingMaker() && minion.distanceToSqr(maker) > stop * stop && minion.getTarget() == null
                    && !minion.getNavigation().isDone();
        }

        /** How far off its maker must be for it to set off after them. */
        public double setOff() {
            return minion.hasTask(MinionTask.IDLE) ? minion.reach() + 2.0 : START;
        }

        /** How near it comes before it stops. */
        public double stopAt() {
            return minion.hasTask(MinionTask.IDLE) ? Math.max(STOP, minion.reach() - 1.0) : STOP;
        }

        @Override
        public void start() {
            repath = 0;
            if (maker != null) {
                minion.getNavigation().moveTo(maker, 1.1);
            }
        }

        @Override
        public void tick() {
            if (maker == null) {
                return;
            }
            minion.getLookControl().setLookAt(maker, 10.0F, minion.getMaxHeadXRot());
            if (--repath <= 0) {
                repath = adjustedTickDelay(10);
                minion.getNavigation().moveTo(maker, 1.1);
            }
        }

        @Override
        public void stop() {
            minion.getNavigation().stop();
            maker = null;
        }
    }

    /**
     * A surgeon keeps by its Surgery Table (its home, or the nearest one within its reach of where it stands when set down
     * elsewhere), where the amputation ritual needs it (Surgery#surgeonAt), and tends whoever lies there: a heart every five
     * seconds at 100%, ÷ its fitness, never under two (docs/NEXT.md 1.5), each heart's wait read from its fitness then.
     */
    public static class AttendTable extends Goal {
        private final MinionEntity minion;
        private int nextSearch;
        /** Its table out of its reach (a door shut): it tries again after this. */
        private int restUntil;
        private final Approach approach = new Approach();
        /** When it tends the patient's next heart; 0 while nobody hurt lies there. */
        private int nextHeart;
        /** Why it found no table when it last looked, said until it looks again; null once it has one. */
        @Nullable
        private net.minecraft.network.chat.Component noTable;

        public AttendTable(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            return minion.hasTask(MinionTask.SURGEON) && minion.tickCount >= restUntil && table() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            approach.reset(minion);
            nextHeart = 0;
        }

        /** Ticks to the next heart it tends, at its fitness now: a zombie villager's shaky hands are slow, a nocturnal head quicker by night. */
        private int every() {
            return MinionFitness.tendTicks(com.avicagan.bloodandbones.parts.PartsData.of(minion.level()).task(MinionTask.SURGEON), minion.taskFitness());
        }

        @Nullable
        private BlockPos table() {
            // a home far off, unloaded, is not looked at (looking would load it)
            if (minion.level().isLoaded(minion.home())
                    && minion.level().getBlockState(minion.home()).getBlock() instanceof com.avicagan.bloodandbones.body.SurgeryTableBlock) {
                noTable = null;
                return minion.home();
            }
            if (minion.tickCount < nextSearch) {
                if (noTable != null) {
                    minion.idle(noTable);
                }
                return null;
            }
            nextSearch = minion.tickCount + 100;
            int reach = minion.reach();
            BlockPos from = minion.blockPosition().offset(-reach, -2, -reach);
            BlockPos to = minion.blockPosition().offset(reach, 2, reach);
            if (!loaded(minion, from, to)) {
                return null;
            }
            BlockPos best = null;
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (minion.level().getBlockState(pos).getBlock() instanceof com.avicagan.bloodandbones.body.SurgeryTableBlock
                        && (best == null || pos.distSqr(minion.blockPosition()) < best.distSqr(minion.blockPosition()))) {
                    best = pos.immutable();
                }
            }
            // with none, it says why until it next looks: a reach set too short, or set down too far off
            noTable = best == null ? net.minecraft.network.chat.Component.translatable("bloodandbones.minion.idle.surgeon", reach) : null;
            if (best != null) {
                minion.setHome(best);
            } else {
                minion.idle(noTable);
            }
            return best;
        }

        @Override
        public void tick() {
            BlockPos table = minion.home();
            Vec3 centre = Vec3.atCenterOf(table);
            // as near as a path of its width ends (a broad body stops further off), still well within the ritual's reach
            double near = reach(minion);
            if (minion.distanceToSqr(centre) > near * near) {
                if (!approach.step(minion, table, accuracy(minion), 1.0)) {
                    // it cannot get to its table now: it stands down a while
                    minion.getNavigation().stop();
                    restUntil = minion.tickCount + 200;
                }
                return;
            }
            approach.reset(minion);
            minion.getNavigation().stop();
            net.minecraft.world.entity.LivingEntity patient = com.avicagan.bloodandbones.body.Surgery.patientAt(minion.level(), table);
            if (patient != null) {
                minion.getLookControl().setLookAt(patient);
            } else {
                minion.getLookControl().setLookAt(centre);
            }
            if (patient == null || patient.getHealth() >= patient.getMaxHealth()) {
                nextHeart = 0;
            } else if (nextHeart == 0) {
                // it tends them while they lie there hurt: the first heart after a wait, as each after it
                nextHeart = minion.tickCount + every();
            } else if (minion.tickCount >= nextHeart) {
                patient.heal(1.0F);
                minion.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                nextHeart = minion.tickCount + every();
            }
        }
    }

    /**
     * Working at home (not with its maker, or its maker away), it goes back there when it has wandered off with nothing to
     * do: more than 4 blocks, or Idle further than its reach (a sentry to its post).
     */
    public static class StayNearHome extends Goal {
        private final MinionEntity minion;

        public StayNearHome(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            double keep = minion.hasTask(MinionTask.IDLE) ? minion.reach() : 4.0;
            return !minion.withMaker() && minion.distanceToSqr(Vec3.atCenterOf(minion.home())) > keep * keep;
        }

        @Override
        public boolean canContinueToUse() {
            return !minion.getNavigation().isDone();
        }

        @Override
        public void start() {
            BlockPos home = minion.home();
            minion.getNavigation().moveTo(home.getX() + 0.5, home.getY() + 1, home.getZ() + 0.5, 1.0);
        }
    }

    /**
     * A farmer picks up what lies about near home, what it reaped that fell short, while it has room (and, brass, what its
     * filter passes): within its reach of home and two blocks more, as far as it reaps. A courier fetches with its own goal
     * (MinionTasks.Fetch).
     */
    public static class Collect extends Goal {
        private final MinionEntity minion;
        @Nullable
        private ItemEntity item;
        private final Approach approach = new Approach();
        /** Items it could not get to, by entity id, forgotten every half minute. */
        private final List<Integer> unreachable = new ArrayList<>();
        private int forgotAt;

        public Collect(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean room(ItemStack stack) {
            return minion.canCarry(stack);
        }

        /** Its tick counts game ticks (a drink a second, a fresh path now and then), so it must run on every one. */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.FARMER) || minion.getRandom().nextInt(10) != 0 || full()) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                unreachable.clear();
                forgotAt = minion.tickCount;
            }
            // as far as it reaps, and what rolled off the furthest crop
            List<ItemEntity> items = minion.level().getEntitiesOfClass(ItemEntity.class, new AABB(minion.home()).inflate(minion.reach() + MinionEntity.RANGE),
                    e -> e.isAlive() && !e.hasPickUpDelay() && !unreachable.contains(e.getId()) && room(e.getItem())
                            && minion.filter().allows(minion.level(), e.getItem()));
            item = items.stream().min(Comparator.comparingDouble(minion::distanceToSqr)).orElse(null);
            return item != null;
        }

        /** Every slot taken and every stack full: nothing on the ground could go in, so none is looked at. */
        private boolean full() {
            for (int i = 0; i < minion.slots(); i++) {
                ItemStack in = minion.inventory.getItem(i);
                if (in.isEmpty() || in.getCount() < in.getMaxStackSize()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return item != null && item.isAlive() && room(item.getItem());
        }

        @Override
        public void start() {
            minion.working = true;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
        }

        @Override
        public void tick() {
            if (item == null) {
                return;
            }
            if (!item.isAlive() || item.getItem().isEmpty()) {
                // taken meanwhile (by a player, another minion): this tick is not asked whether it goes on
                item = null;
                return;
            }
            if (minion.distanceToSqr(item) < 2.5 + minion.getBbWidth()) {
                pickUp(minion, item);
                item = null;
            } else if (!approach.step(minion, item.blockPosition(), 1, 1.0)) {
                unreachable.add(item.getId());
                item = null;
            }
        }
    }

    /**
     * It takes up what it has room for of an item lying on the ground, as a player's pickup does, and the count it took.
     * Nothing from one already gone or emptied: another took it this tick, and a goal that runs every tick is not asked
     * whether it goes on in the ticks between (vanilla's Mob#serverAiStep). What stays on the ground is what it did not take,
     * so one taken whole is emptied as it goes (HopperBlockEntity#addItem does the same): vanilla's own pickup puts a
     * discarded item's count back, and a copy of that would be a second stack.
     */
    public static int pickUp(MinionEntity minion, ItemEntity item) {
        if (!item.isAlive() || item.getItem().isEmpty()) {
            return 0;
        }
        ItemStack left = minion.carry(item.getItem().copy());
        int taken = item.getItem().getCount() - left.getCount();
        if (taken <= 0) {
            return 0;
        }
        minion.take(item, taken);
        if (left.isEmpty()) {
            item.setItem(ItemStack.EMPTY);
            item.discard();
        } else {
            item.setItem(left);
        }
        return taken;
    }

    /**
     * A task whose takings are stored (its data's "stores": the courier, farmer, fisher, butcher, digger and barterer)
     * carries what it has to the container nearest home that takes any of it, working at home (a Butcher's Table by home,
     * which takes only a carcass piece, is passed over for the chest beyond it).
     */
    public static class Deposit extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos container;
        private final Unreachable unreachable = new Unreachable();
        private final Approach approach = new Approach();

        public Deposit(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Nullable
        private BlockPos findContainer() {
            BlockPos home = minion.home();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            if (!loaded(minion, home.offset(-6, -2, -6), home.offset(6, 2, 6))) {
                return null;
            }
            for (BlockPos pos : BlockPos.betweenClosed(home.offset(-6, -2, -6), home.offset(6, 2, 6))) {
                IItemHandler handler = unreachable.contains(minion, pos) ? null : minion.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null && takesAny(handler)
                        && !(minion.level().getBlockState(pos).getBlock() instanceof com.avicagan.bloodandbones.body.SurgeryTableBlock)
                        && !(minion.level().getBlockState(pos).getBlock() instanceof BloodTroughBlock)) {
                    double d = pos.distSqr(home);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = pos.immutable();
                    }
                }
            }
            return best;
        }

        /** Whether this container would take any of what it carries (tried, not done). */
        private boolean takesAny(IItemHandler handler) {
            for (int i = 0; i < minion.inventory.getContainerSize(); i++) {
                ItemStack stack = minion.inventory.getItem(i);
                if (!stack.isEmpty() && ItemHandlerHelper.insertItemStacked(handler, stack.copy(), true).getCount() < stack.getCount()) {
                    return true;
                }
            }
            return false;
        }

        /** Its tick counts game ticks (a drink a second, a fresh path now and then), so it must run on every one. */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (minion.inventory.isEmpty() || minion.getRandom().nextInt(10) != 0 || minion.withMaker()
                    || !com.avicagan.bloodandbones.parts.PartsData.of(minion.level()).task(minion.task()).stores()) {
                return false;
            }
            container = findContainer();
            if (container == null) {
                minion.idle(net.minecraft.network.chat.Component.translatable("bloodandbones.minion.idle.container"));
            }
            return container != null;
        }

        @Override
        public boolean canContinueToUse() {
            return container != null && !minion.inventory.isEmpty();
        }

        @Override
        public void start() {
            minion.working = true;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
        }

        @Override
        public void tick() {
            if (container == null) {
                return;
            }
            if (minion.distanceToSqr(Vec3.atCenterOf(container)) < 6.0 + minion.getBbWidth() * 2) {
                IItemHandler handler = minion.level().getCapability(Capabilities.ItemHandler.BLOCK, container, null);
                if (handler == null) {
                    container = null;
                    return;
                }
                for (int i = 0; i < minion.inventory.getContainerSize(); i++) {
                    ItemStack stack = minion.inventory.getItem(i);
                    if (!stack.isEmpty()) {
                        minion.inventory.setItem(i, ItemHandlerHelper.insertItemStacked(handler, stack, false));
                    }
                }
                container = null;
            } else if (!approach.step(minion, container, 1, 1.0)) {
                unreachable.add(container);
                container = null;
            }
        }
    }

    /**
     * Whether a task that looks round (a courier, a farmer, a Tender) looks now: once every {@link MinionFitness#lookTicks}
     * ticks on average, a second at 100% (docs/NEXT.md 1.2). A goal is asked whether to start only every other tick (vanilla's
     * Mob#serverAiStep ticks only the running goals in between), so the chance each time is one in half that, as vanilla's
     * own goals take theirs (Goal#reducedTickDelay).
     */
    static boolean looks(MinionEntity minion, MinionTask task) {
        int every = MinionFitness.lookTicks(com.avicagan.bloodandbones.parts.PartsData.of(minion.level()).task(task), minion.taskFitness());
        if (minion.getRandom().nextInt(Math.max(1, Mth.positiveCeilDiv(every, 2))) != 0) {
            return false;
        }
        minion.looksTaken++;
        return true;
    }

    /**
     * The most blocks a 100% farmer reads a tick when it looks for ripe crops, on average: today's farmer's, its 17 by 17 by
     * 5 box once a second. A far-reaching farmer's box is bigger, so each look reads a band of the box, as wide as this
     * allows.
     */
    static final float FARM_SCAN = 17 * 17 * 5 / 20.0F;

    /**
     * How many columns of its box (each its width long and 5 high) a farmer reads a look: as many as a 100% farmer at this
     * reach reads, so a fitter farmer, looking more often, goes over its whole box sooner, never later (at its own reach, the
     * whole box every look); a poorer one, looking less often, reads as many more a look as keeps it to FARM_SCAN a tick.
     * So a farmer reads more blocks a second than today's only by as much as its looks are quicker, twice at most.
     */
    static int farmColumns(int width, int lookTicks, int baseLookTicks) {
        return Mth.clamp(Math.round(FARM_SCAN * Math.max(lookTicks, baseLookTicks)) / (width * 5), 1, width);
    }

    /**
     * A farmer harvests ripe crops within its reach of home (brass: those its filter passes) and plants them again from what
     * it reaped. It looks for ripe ones every second or so at 100%, a fitter farmer more often (docs/NEXT.md 1.2). A look
     * reads a band of the box within its reach (all of it at its own reach), as wide as a 100% farmer's look
     * ({@link #farmColumns}): it works one band until it finds no ripe crop there, then looks along the next.
     */
    public static class Farm extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos crop;
        /** The band of its box it looks along next. */
        private int band;
        private final Unreachable unreachable = new Unreachable();
        private final Approach approach = new Approach();

        public Farm(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        /** The ripe crop nearest it in the band it looks along now; with none there, the next band's turn comes. */
        @Nullable
        private BlockPos findRipe() {
            BlockPos home = minion.home();
            int reach = minion.reach();
            if (!loaded(minion, home.offset(-reach, -2, -reach), home.offset(reach, 2, reach))) {
                return null;
            }
            int width = reach * 2 + 1;
            MinionTask.Data data = com.avicagan.bloodandbones.parts.PartsData.of(minion.level()).task(MinionTask.FARMER);
            int columns = farmColumns(width, MinionFitness.lookTicks(data, minion.taskFitness()), MinionFitness.lookTicks(data, 1.0F));
            int bands = Mth.positiveCeilDiv(width, columns);
            band = Math.floorMod(band, bands);
            int x = home.getX() - reach + band * columns;
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(x, home.getY() - 2, home.getZ() - reach, Math.min(x + columns - 1, home.getX() + reach),
                    home.getY() + 2, home.getZ() + reach)) {
                BlockState state = minion.level().getBlockState(pos);
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && !unreachable.contains(minion, pos)
                        && minion.filter().allowsCrop(minion.level(), state, pos)) {
                    double d = minion.distanceToSqr(Vec3.atCenterOf(pos));
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = pos.immutable();
                    }
                }
            }
            if (best == null) {
                band++;
            }
            return best;
        }

        /** Its tick counts game ticks (a drink a second, a fresh path now and then), so it must run on every one. */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.FARMER) || !looks(minion, MinionTask.FARMER)) {
                return false;
            }
            crop = findRipe();
            return crop != null;
        }

        @Override
        public boolean canContinueToUse() {
            return crop != null && minion.level().getBlockState(crop).getBlock() instanceof CropBlock;
        }

        @Override
        public void start() {
            minion.working = true;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
        }

        @Override
        public void tick() {
            if (crop == null || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            minion.getLookControl().setLookAt(Vec3.atCenterOf(crop));
            if (minion.distanceToSqr(Vec3.atCenterOf(crop)) < 5.0 + minion.getBbWidth()) {
                BlockState state = level.getBlockState(crop);
                if (state.getBlock() instanceof CropBlock block && block.isMaxAge(state)) {
                    List<ItemStack> drops = Block.getDrops(state, level, crop, null, minion, ItemStack.EMPTY);
                    ItemStack seed = state.getBlock().getCloneItemStack(level, crop, state);
                    level.destroyBlock(crop, false, minion);
                    boolean replanted = false;
                    for (ItemStack drop : drops) {
                        if (!replanted && !seed.isEmpty() && ItemStack.isSameItem(drop, seed)) {
                            drop.shrink(1);
                            replanted = true;
                        }
                        ItemStack left = minion.carry(drop);
                        if (!left.isEmpty()) {
                            Block.popResource(level, crop, left);
                        }
                    }
                    if (replanted) {
                        level.setBlockAndUpdate(crop, block.getStateForAge(0));
                    }
                    minion.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                crop = null;
            } else if (!approach.step(minion, crop, 1, 1.0)) {
                unreachable.add(crop);
                crop = null;
            }
        }
    }
}
