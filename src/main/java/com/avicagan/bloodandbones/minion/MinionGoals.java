package com.avicagan.bloodandbones.minion;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/** What a minion does, by job. */
public final class MinionGoals {
    private MinionGoals() {
    }

    /** A fighter with a weapon arm (a Hook Hand, a Hydraulic Arm) goes for what it is fighting. */
    public static class Melee extends MeleeAttackGoal {
        private final MinionEntity minion;

        public Melee(MinionEntity minion) {
            super(minion, 1.2, true);
            this.minion = minion;
        }

        @Override
        public boolean canUse() {
            return minion.job() == MinionJob.FIGHTER && !com.avicagan.bloodandbones.body.Vent.ready(minion) && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return minion.job() == MinionJob.FIGHTER && !com.avicagan.bloodandbones.body.Vent.ready(minion) && super.canContinueToUse();
        }
    }

    /** A fighter with a Vent Arm keeps its distance and sprays what it is fighting with whatever its tank holds. */
    public static class VentAttack extends Goal {
        private final MinionEntity minion;

        public VentAttack(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = minion.getTarget();
            return minion.job() == MinionJob.FIGHTER && com.avicagan.bloodandbones.body.Vent.ready(minion)
                    && !com.avicagan.bloodandbones.backtank.FluidBacktankItem.fluid(com.avicagan.bloodandbones.backtank.FluidBacktankItem.wornBy(minion)).isEmpty()
                    && target != null && target.isAlive();
        }

        @Override
        public void tick() {
            LivingEntity target = minion.getTarget();
            if (target == null) {
                return;
            }
            minion.getLookControl().setLookAt(target, 60.0F, 60.0F);
            double distance = minion.distanceTo(target);
            if (distance > com.avicagan.bloodandbones.body.Vent.RANGE - 2.0) {
                minion.getNavigation().moveTo(target, 1.1);
            } else {
                minion.getNavigation().stop();
                // aim straight at it, then spray
                Vec3 to = target.getBoundingBox().getCenter().subtract(minion.getEyePosition());
                minion.setYRot((float) (Math.toDegrees(Math.atan2(-to.x, to.z))));
                minion.setXRot((float) (-Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)))));
                minion.yHeadRot = minion.getYRot();
                com.avicagan.bloodandbones.body.Vent.spray(minion);
            }
        }
    }

    /** Fighters and companions keep near their maker. */
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
            if (minion.job() != MinionJob.FIGHTER && minion.job() != MinionJob.COMPANION) {
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

    /** Farmers and couriers wander back home when they have nothing to do. */
    public static class StayNearHome extends Goal {
        private final MinionEntity minion;

        public StayNearHome(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return (minion.job() == MinionJob.FARMER || minion.job() == MinionJob.COURIER)
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

    /** Couriers and farmers pick up what lies about near home, while they have room. */
    public static class Collect extends Goal {
        private final MinionEntity minion;
        @Nullable
        private ItemEntity item;

        public Collect(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (minion.job() != MinionJob.COURIER && minion.job() != MinionJob.FARMER) {
                return false;
            }
            List<ItemEntity> items = minion.level().getEntitiesOfClass(ItemEntity.class, new AABB(minion.home()).inflate(MinionEntity.RANGE),
                    e -> e.isAlive() && !e.hasPickUpDelay() && minion.inventory.canAddItem(e.getItem()));
            item = items.stream().min(java.util.Comparator.comparingDouble(minion::distanceToSqr)).orElse(null);
            return item != null;
        }

        @Override
        public boolean canContinueToUse() {
            return item != null && item.isAlive() && minion.inventory.canAddItem(item.getItem());
        }

        @Override
        public void tick() {
            if (item == null) {
                return;
            }
            if (minion.distanceToSqr(item) < 2.5) {
                ItemStack left = minion.inventory.addItem(item.getItem().copy());
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

    /** Couriers and farmers carry what they hold to a container by home. */
    public static class Deposit extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos container;

        public Deposit(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        private boolean holding() {
            return !minion.inventory.isEmpty();
        }

        @Nullable
        private BlockPos findContainer() {
            BlockPos home = minion.home();
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (BlockPos pos : BlockPos.betweenClosed(home.offset(-6, -2, -6), home.offset(6, 2, 6))) {
                if (minion.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null) != null
                        && !(minion.level().getBlockState(pos).getBlock() instanceof com.avicagan.bloodandbones.body.SurgeryTableBlock)) {
                    double d = pos.distSqr(home);
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = pos.immutable();
                    }
                }
            }
            return best;
        }

        @Override
        public boolean canUse() {
            if ((minion.job() != MinionJob.COURIER && minion.job() != MinionJob.FARMER) || !holding() || minion.getRandom().nextInt(10) != 0) {
                return false;
            }
            container = findContainer();
            return container != null;
        }

        @Override
        public boolean canContinueToUse() {
            return container != null && holding();
        }

        @Override
        public void tick() {
            if (container == null) {
                return;
            }
            if (minion.distanceToSqr(Vec3.atCenterOf(container)) < 6.0) {
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

    /** Farmers harvest ripe crops near home and plant them again from what they reaped. */
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

        @Override
        public boolean canUse() {
            if (minion.job() != MinionJob.FARMER || minion.getRandom().nextInt(10) != 0) {
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
        public void tick() {
            if (crop == null || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            minion.getLookControl().setLookAt(Vec3.atCenterOf(crop));
            if (minion.distanceToSqr(Vec3.atCenterOf(crop)) < 5.0) {
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
                        ItemStack left = minion.inventory.addItem(drop);
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
