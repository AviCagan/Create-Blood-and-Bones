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

    /** Who it fights: a companion or bodyguard what hurts it or its maker, and what its maker hits; a guard monsters near home. */
    static void targets(MinionEntity minion, GoalSelector targets) {
        targets.addGoal(1, new HurtByTargetGoal(minion) {
            @Override
            public boolean canUse() {
                return minion.stats().mindless() == false && super.canUse();
            }
        });
        targets.addGoal(2, new DefendMaker(minion));
        targets.addGoal(3, new NearestAttackableTargetGoal<>(minion, Mob.class, 10, true, false,
                target -> target instanceof Enemy && !(target instanceof MinionEntity) && target.distanceToSqr(Vec3.atCenterOf(minion.home())) < 256.0) {
            @Override
            public boolean canUse() {
                return minion.hasJob("guard") && super.canUse();
            }
        });
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
            if (!(minion.hasJob("companion") || minion.hasJob("bodyguard")) || minion.stats().mindless()) {
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
            return !minion.stats().mindless() && super.canUse();
        }
    }

    /** Low on blood, it walks to the nearest Blood Trough it can reach and drinks its fill. */
    public static class SeekBlood extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos trough;
        private final List<BlockPos> unreachable = new ArrayList<>();
        /** When it last forgot which troughs it could not reach (a wall may have come down since). */
        private int forgotAt;

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
            // off the ground no path can be made, which is not the same as there being none
            if (minion.cybernetic() || minion.powerShare() >= MinionEntity.HUNGRY || !minion.onGround() || minion.getRandom().nextInt(20) != 0) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                unreachable.clear();
                forgotAt = minion.tickCount;
            }
            trough = find();
            return trough != null;
        }

        /** The nearest trough with blood in it that a path reaches. */
        @Nullable
        private BlockPos find() {
            int radius = BBServerConfig.troughRadius();
            List<BlockPos> near = BloodTroughBlockEntity.all(minion.level()).stream()
                    .filter(p -> p.distSqr(minion.blockPosition()) < (double) radius * radius && !unreachable.contains(p)
                            && minion.level().getBlockEntity(p) instanceof BloodTroughBlockEntity t && t.amount() > 0)
                    .sorted(Comparator.comparingDouble(p -> p.distSqr(minion.blockPosition()))).toList();
            for (BlockPos pos : near) {
                if (minion.distanceToSqr(Vec3.atCenterOf(pos)) < reach(minion) * reach(minion)) {
                    return pos;
                }
                Path path = minion.getNavigation().createPath(pos, accuracy(minion));
                if (path != null && path.canReach()) {
                    return pos;
                }
                // it must walk there itself: a trough it cannot reach is no use to it
                unreachable.add(pos);
            }
            return null;
        }

        @Override
        public boolean canContinueToUse() {
            return trough != null && minion.powerShare() < 0.98F && minion.level().getBlockEntity(trough) instanceof BloodTroughBlockEntity t
                    && t.amount() > 0;
        }

        @Override
        public void start() {
            moveTo();
        }

        private void moveTo() {
            if (trough != null) {
                minion.getNavigation().moveTo(minion.getNavigation().createPath(trough, accuracy(minion)), 1.1);
            }
        }

        @Override
        public void tick() {
            if (trough == null) {
                return;
            }
            if (minion.distanceToSqr(Vec3.atCenterOf(trough)) < reach(minion) * reach(minion)) {
                minion.getNavigation().stop();
                minion.getLookControl().setLookAt(Vec3.atCenterOf(trough));
                if (minion.tickCount % 20 == 0) {
                    drink(minion, trough);
                }
            } else if (minion.getNavigation().isDone() || minion.tickCount % 40 == 0) {
                moveTo();
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

    /** Powered down: a trough within reach of where it lies revives it. */
    static void drinkNearby(MinionEntity minion) {
        if (minion.cybernetic()) {
            return;
        }
        double reach = reach(minion);
        for (BlockPos pos : BloodTroughBlockEntity.all(minion.level())) {
            if (pos.distToCenterSqr(minion.position()) < reach * reach) {
                drink(minion, pos);
                return;
            }
        }
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

    /** Minions with work at home wander back there when idle. */
    public static class StayNearHome extends Goal {
        private final MinionEntity minion;

        public StayNearHome(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return (minion.hasJob("farmer") || minion.hasJob("courier") || minion.hasJob("guard"))
                    && minion.distanceToSqr(Vec3.atCenterOf(minion.home())) > 16.0;
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
            if (!minion.hasJob("courier") && !minion.hasJob("farmer")) {
                return false;
            }
            List<ItemEntity> items = minion.level().getEntitiesOfClass(ItemEntity.class, new AABB(minion.home()).inflate(MinionEntity.RANGE),
                    e -> e.isAlive() && !e.hasPickUpDelay() && room(e.getItem()));
            item = items.stream().min(Comparator.comparingDouble(minion::distanceToSqr)).orElse(null);
            return item != null;
        }

        @Override
        public boolean canContinueToUse() {
            return item != null && item.isAlive() && room(item.getItem());
        }

        @Override
        public void start() {
            minion.working = true;
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
            } else if (minion.getNavigation().isDone() || minion.tickCount % 20 == 0) {
                minion.getNavigation().moveTo(item, 1.0);
            }
        }
    }

    /** A courier or farmer carries what it holds to a container by home. */
    public static class Deposit extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos container;

        public Deposit(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Nullable
        private BlockPos findContainer() {
            BlockPos home = minion.home();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(home.offset(-6, -2, -6), home.offset(6, 2, 6))) {
                if (minion.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
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
            if ((!minion.hasJob("courier") && !minion.hasJob("farmer")) || minion.inventory.isEmpty() || minion.getRandom().nextInt(10) != 0) {
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
            } else if (minion.getNavigation().isDone() || minion.tickCount % 20 == 0) {
                minion.getNavigation().moveTo(container.getX() + 0.5, container.getY(), container.getZ() + 0.5, 1.0);
            }
        }
    }

    /** A farmer harvests ripe crops near home and plants them again from what it reaped. */
    public static class Farm extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos crop;

        public Farm(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Nullable
        private BlockPos findRipe() {
            BlockPos home = minion.home();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(home.offset(-8, -2, -8), home.offset(8, 2, 8))) {
                BlockState state = minion.level().getBlockState(pos);
                if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
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
            } else if (minion.getNavigation().isDone() || minion.tickCount % 20 == 0) {
                minion.getNavigation().moveTo(crop.getX() + 0.5, crop.getY(), crop.getZ() + 0.5, 1.0);
            }
        }
    }
}
