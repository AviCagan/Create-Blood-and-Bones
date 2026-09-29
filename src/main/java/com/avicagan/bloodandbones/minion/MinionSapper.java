package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.parts.Activation;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.Trait;
import com.avicagan.bloodandbones.parts.TraitEffect;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.parts.effect.DetonateEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * The sapper (docs/PARTS-AND-TRAITS.md section 6.9; docs/NEXT.md 1.1): any minion with an organ that detonates (the
 * creeper's Powder Sac) can be set to it, and a creeper's head has a knack for it. It walks to its target, or to the block
 * its maker marked, and blows itself up there through its organ, sparing itself and its side; then it lies powered down
 * where it stands until it gets blood again, never destroyed (the detonate effect powers it down). Its targets are a
 * guard's (monsters within its reach of home that it can see) and whatever hurts it. The mark is a banner: handed a banner,
 * it goes for the nearest banner of that colour standing within its reach of home (16), as a sapper goes for the flag its
 * side planted. Blocks break only where the organ's blast may break them, the server's {@code minion_block_damage} and
 * mobGriefing all allow it. With no detonating organ a body cannot be a sapper at all ({@link #hasDetonator}, the task's
 * test of its body). A body with no head finds no target and no banner: it sets itself off at a monster against it, as a
 * headless guard strikes only what touches it (MinionGoals.TouchTarget), and never walks after one.
 */
public final class MinionSapper {
    public static final ResourceLocation SAPPER = BloodAndBones.asResource("sapper");

    private MinionSapper() {
    }

    /** Whether a build has a detonating organ (an activate detonate effect among its organ's minion traits). */
    public static boolean hasDetonator(PartsData.Store store, MinionBuild build) {
        if (build.organ().isEmpty()) {
            return false;
        }
        var organ = build.organ().get();
        for (TraitList.Resolved resolved : store.resolve(organ.entity(), organ.baby()).organMinion(organ.organ(), organ.traits())) {
            Trait trait = store.trait(resolved.id());
            if (trait != null && trait.contexts().contains(ActiveTraits.MINION)) {
                for (TraitEffect facet : trait.effects()) {
                    if (facet.trigger() == Trigger.ACTIVATE && facet.effect() instanceof DetonateEffect && facet.appliesIn(ActiveTraits.MINION)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Its detonation, if it can set it off now: off cooldown, its condition holding, and blood enough to pay for it. */
    @Nullable
    static Activation.Facet detonator(MinionEntity minion) {
        for (Activation.Facet facet : Activation.facets(ActiveTraits.of(minion))) {
            if (facet.facet().effect() instanceof DetonateEffect && !TraitEvents.coolingDown(minion, facet.entry(), facet.index())
                    && minion.power() - facet.facet().costMb() >= 1.0F && TraitEvents.holds(minion, facet.entry(), facet.facet(), null)) {
                return facet;
            }
        }
        return null;
    }

    /**
     * It walks to its target or its mark and, there, sets off its organ; while the fuse hisses it stands still, swelling
     * as a creeper does. It gives up on a mark it cannot reach, trying again half a minute later.
     */
    static class Sap extends Goal {
        private final MinionEntity minion;
        private final MinionGoals.Approach approach = new MinionGoals.Approach();
        private final MinionGoals.Unreachable unreachable = new MinionGoals.Unreachable();
        @Nullable
        private BlockPos mark;

        Sap(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        /** Its tick counts game ticks (a fresh path now and then), so it must run on every one. */
        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.SAPPER) || minion.poweredDown() || detonator(minion) == null && !DetonateEffect.lit(minion)) {
                return false;
            }
            LivingEntity target = minion.getTarget();
            if (target != null && target.isAlive()) {
                mark = null;
                return felt(target);
            }
            // a body with no head sees no banner
            mark = !minion.stats().mindless() && minion.getRandom().nextInt(20) == 0 ? findMark() : null;
            return mark != null;
        }

        /** Whether it knows where this is: it sees it, or, with no head, it touches it. */
        private boolean felt(LivingEntity target) {
            return !minion.stats().mindless() || MinionGoals.touches(minion, target);
        }

        @Override
        public boolean canContinueToUse() {
            if (!minion.hasTask(MinionTask.SAPPER) || minion.poweredDown()) {
                return false;
            }
            if (DetonateEffect.lit(minion)) {
                return true;
            }
            LivingEntity target = minion.getTarget();
            return target != null && target.isAlive() && felt(target) || mark != null && marked(mark);
        }

        @Override
        public void start() {
            minion.working = true;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            mark = null;
            minion.getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = minion.getTarget();
            Vec3 at = target != null && target.isAlive() ? target.position() : mark != null ? Vec3.atBottomCenterOf(mark) : null;
            if (at == null) {
                return;
            }
            minion.getLookControl().setLookAt(at.x, at.y + 0.5, at.z);
            if (DetonateEffect.lit(minion)) {
                // hissing: it holds still for the blast
                minion.getNavigation().stop();
                return;
            }
            Activation.Facet facet = detonator(minion);
            if (facet == null) {
                return;
            }
            double reach = target != null && target.isAlive() ? Math.max(1.0, facet.facet().range() - 0.5)
                    : 1.0 + minion.getBbWidth() / 2.0;
            if (minion.position().distanceToSqr(at) <= reach * reach) {
                minion.getNavigation().stop();
                Activation.fire(minion, facet, target != null && target.isAlive() ? target : null);
                approach.reset(minion);
            } else if (minion.stats().mindless()) {
                // it knows only what is against it, and never walks after it
                minion.getNavigation().stop();
            } else if (!approach.step(minion, BlockPos.containing(at), 1, 1.2)) {
                if (mark != null && (target == null || !target.isAlive())) {
                    unreachable.add(mark);
                    mark = null;
                }
            }
        }

        /** The colour of the banner in its hand, or null. */
        @Nullable
        private DyeColor shown() {
            return minion.getMainHandItem().getItem() instanceof BannerItem banner ? banner.getColor() : null;
        }

        /** Whether a banner of the colour it holds still stands there. */
        private boolean marked(BlockPos pos) {
            DyeColor colour = shown();
            return colour != null && minion.level().isLoaded(pos) && minion.level().getBlockEntity(pos) instanceof BannerBlockEntity banner
                    && banner.getBaseColor() == colour;
        }

        /** The nearest banner of the colour it holds within its reach of home, in loaded chunks only (none loaded to look). */
        @Nullable
        private BlockPos findMark() {
            DyeColor colour = shown();
            if (colour == null || !(minion.level() instanceof ServerLevel level)) {
                return null;
            }
            BlockPos home = minion.home();
            int reach = minion.reach();
            BlockPos best = null;
            for (int cx = (home.getX() - reach) >> 4; cx <= (home.getX() + reach) >> 4; cx++) {
                for (int cz = (home.getZ() - reach) >> 4; cz <= (home.getZ() + reach) >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        BlockPos pos = be.getBlockPos();
                        if (be instanceof BannerBlockEntity banner && banner.getBaseColor() == colour && pos.closerThan(home, reach)
                                && !unreachable.contains(minion, pos) && (best == null || pos.distSqr(minion.blockPosition()) < best.distSqr(minion.blockPosition()))) {
                            best = pos.immutable();
                        }
                    }
                }
            }
            return best;
        }
    }
}
