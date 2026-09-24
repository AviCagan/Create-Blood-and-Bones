package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.config.BBServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
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

/** What a minion does, by job, and how it looks after its blood. */
public final class MinionGoals {
    /** How fast it drinks at a trough, mB a second. */
    public static final int DRINK = 100;

    private MinionGoals() {
    }

    /**
     * Who it fights: a companion or bodyguard what hurts it or its maker, and what its maker hits; a guard monsters near
     * home. Never its maker (MinionEntity#canAttack), and a pacifist (no arm that hits) takes no target at all.
     */
    static void targets(MinionEntity minion, GoalSelector targets) {
        targets.addGoal(1, new HurtByTargetGoal(minion) {
            @Override
            public boolean canUse() {
                return minion.stats().mindless() == false && minion.stats().fights() && super.canUse();
            }
        });
        targets.addGoal(2, new DefendMaker(minion));
        targets.addGoal(3, new NearestAttackableTargetGoal<>(minion, Mob.class, 10, true, false,
                target -> target instanceof Enemy && !(target instanceof MinionEntity) && target.distanceToSqr(Vec3.atCenterOf(minion.home())) < 256.0) {
            @Override
            public boolean canUse() {
                return minion.hasJob("guard") && minion.stats().fights() && super.canUse();
            }

            /** No further than its head notices things (a blind head: 4 blocks); asked first while it is being made. */
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

    /** A companion or bodyguard goes for what hurts its maker, and what its maker goes for. */
    static class DefendMaker extends TargetGoal {
        private final MinionEntity minion;
        @Nullable
        private LivingEntity enemy;

        DefendMaker(MinionEntity minion) {
            super(minion, false);
            this.minion = minion;
            setFlags(EnumSet.of(Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!(minion.hasJob("companion") || minion.hasJob("bodyguard")) || minion.stats().mindless() || !minion.stats().fights()) {
                return false;
            }
            Player maker = minion.maker();
            if (maker == null) {
                return false;
            }
            LivingEntity attacker = maker.getLastHurtByMob();
            LivingEntity attacked = maker.getLastHurtMob();
            enemy = attacker != null && maker.tickCount - maker.getLastHurtByMobTimestamp() < 100 ? attacker
                    : minion.hasJob("bodyguard") && attacked != null && maker.tickCount - maker.getLastHurtMobTimestamp() < 100 ? attacked : null;
            return enemy != null && !(enemy instanceof MinionEntity) && canAttack(enemy, TargetingConditions.DEFAULT);
        }

        @Override
        public void start() {
            mob.setTarget(enemy);
            super.start();
        }
    }

    /** It goes for its target with its arms, or its teeth if it has none. */
    public static class Bite extends MeleeAttackGoal {
        private final MinionEntity minion;

        public Bite(MinionEntity minion) {
            super(minion, 1.2, true);
            this.minion = minion;
        }

        @Override
        public boolean canUse() {
            // a pacifist (a villager's pair of arms and no bite of its own worth using) never attacks, and a sentry
            // shoots from where it stands rather than closing in
            return !minion.stats().mindless() && minion.stats().fights() && !minion.hasJob("sentry") && super.canUse();
        }
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

    /** A companion keeps near its maker. */
    public static class FollowMaker extends Goal {
        private final MinionEntity minion;
        @Nullable
        private Player maker;

        public FollowMaker(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!minion.hasJob("companion") && !minion.hasJob("bodyguard")) {
                return false;
            }
            maker = minion.maker();
            return maker != null && !maker.isSpectator() && minion.distanceToSqr(maker) > 36.0 && minion.getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return maker != null && minion.distanceToSqr(maker) > 9.0 && !minion.getNavigation().isDone();
        }

        @Override
        public void start() {
            if (maker != null) {
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
     * A surgeon keeps by its Surgery Table (its home, or the nearest one it finds when set down elsewhere), where the
     * amputation ritual needs it (Surgery#surgeonAt), and tends whoever lies there, a heart a few seconds.
     */
    public static class AttendTable extends Goal {
        /** How far it looks for a table when its home is not one. */
        private static final int SEARCH = 6;
        private final MinionEntity minion;
        private int nextSearch;
        /** Its table out of its reach (a door shut): it tries again after this. */
        private int restUntil;
        private final Approach approach = new Approach();

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
            return minion.hasJob("surgeon") && minion.tickCount >= restUntil && table() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            approach.reset(minion);
        }

        @Nullable
        private BlockPos table() {
            // a home far off, unloaded, is not looked at (looking would load it)
            if (minion.level().isLoaded(minion.home())
                    && minion.level().getBlockState(minion.home()).getBlock() instanceof com.avicagan.bloodandbones.body.SurgeryTableBlock) {
                return minion.home();
            }
            if (minion.tickCount < nextSearch) {
                return null;
            }
            nextSearch = minion.tickCount + 100;
            BlockPos from = minion.blockPosition().offset(-SEARCH, -2, -SEARCH);
            BlockPos to = minion.blockPosition().offset(SEARCH, 2, SEARCH);
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
            if (best != null) {
                minion.setHome(best);
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
                // it tends them: a heart every five seconds while they lie there hurt
                if (minion.tickCount % 100 == 0 && patient.getHealth() < patient.getMaxHealth()) {
                    patient.heal(1.0F);
                    minion.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
            } else {
                minion.getLookControl().setLookAt(centre);
            }
        }
    }

    /** Minions with work at home wander back there when idle (a sentry to its post). */
    public static class StayNearHome extends Goal {
        private final MinionEntity minion;

        public StayNearHome(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return MinionJobs.HOMEBODIES.contains(minion.job()) && minion.distanceToSqr(Vec3.atCenterOf(minion.home())) > 16.0;
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

    /** A courier or farmer picks up what lies about near home, while it has room. */
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
            if (!minion.hasJob("courier") && !minion.hasJob("farmer") || minion.getRandom().nextInt(10) != 0 || full()) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                unreachable.clear();
                forgotAt = minion.tickCount;
            }
            List<ItemEntity> items = minion.level().getEntitiesOfClass(ItemEntity.class, new AABB(minion.home()).inflate(MinionEntity.RANGE),
                    e -> e.isAlive() && !e.hasPickUpDelay() && !unreachable.contains(e.getId()) && room(e.getItem()));
            item = items.stream().min(Comparator.comparingDouble(minion::distanceToSqr)).orElse(null);
            return item != null;
        }

        /** Every slot taken and every stack full: nothing on the ground could go in, so none is looked at. */
        private boolean full() {
            for (int i = 0; i < minion.stats().slots(); i++) {
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
            if (minion.distanceToSqr(item) < 2.5 + minion.getBbWidth()) {
                ItemStack left = minion.carry(item.getItem().copy());
                if (left.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(left);
                }
                minion.take(item, 1);
                item = null;
            } else if (!approach.step(minion, item.blockPosition(), 1, 1.0)) {
                unreachable.add(item.getId());
                item = null;
            }
        }
    }

    /** A courier, farmer, fisher, butcher, digger or barterer carries what it holds to a container by home. */
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
                if (!unreachable.contains(minion, pos) && minion.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
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

        /** Its tick counts game ticks (a drink a second, a fresh path now and then), so it must run on every one. */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!MinionJobs.STORERS.contains(minion.job()) || minion.inventory.isEmpty() || minion.getRandom().nextInt(10) != 0) {
                return false;
            }
            container = findContainer();
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

    /** A farmer harvests ripe crops near home and plants them again from what it reaped. */
    public static class Farm extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos crop;
        private final Unreachable unreachable = new Unreachable();
        private final Approach approach = new Approach();

        public Farm(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private BlockPos findRipe() {
            BlockPos home = minion.home();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            if (!loaded(minion, home.offset(-8, -2, -8), home.offset(8, 2, 8))) {
                return null;
            }
            for (BlockPos pos : BlockPos.betweenClosed(home.offset(-8, -2, -8), home.offset(8, 2, 8))) {
                BlockState state = minion.level().getBlockState(pos);
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state) && !unreachable.contains(minion, pos)) {
                    double d = minion.distanceToSqr(Vec3.atCenterOf(pos));
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = pos.immutable();
                    }
                }
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
            if (!minion.hasJob("farmer") || minion.getRandom().nextInt(10) != 0) {
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
