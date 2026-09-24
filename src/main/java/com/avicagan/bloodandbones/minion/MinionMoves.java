package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.parts.effect.FlagEffect;
import com.avicagan.bloodandbones.parts.effect.MotionEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.Set;

/**
 * How a minion gets about beyond walking, flying and climbing (docs/PARTS-AND-TRAITS.md section 5.4, movement and mount;
 * section 5.6's modes): a lava walker paths over lava as a strider does, a sinker walks the bottom of water, and a rider
 * steers it with an item on a stick where its head or legs say so (a pig's head a carrot on a stick, strider legs a
 * warped fungus on a stick), the stick wearing as it does on a pig or a strider. Its one game event is that stick's use.
 */
public final class MinionMoves {
    /** How much faster than water lets it a sinker drops each tick, so it goes down like a stone rather than drifting. */
    public static final double SINK = 0.04;
    /** What a lava walker's path may cross that a walker's may not, as a strider's. */
    private static final Set<PathType> LAVA_PATHS = Set.of(PathType.LAVA, PathType.DANGER_FIRE, PathType.DAMAGE_FIRE);

    private MinionMoves() {
    }

    /** Whether it walks on lava now (the lava_walk flag, from strider legs or anything else). */
    static boolean lavaWalker(MinionEntity minion) {
        return !minion.level().isClientSide && MotionEffects.flag(minion, FlagEffect.LAVA_WALK) > 0;
    }

    /** A lava walker's path finding costs lava and fire nothing, as a strider's does; anyone else's what vanilla says. */
    static void lavaMalus(MinionEntity minion, boolean lava) {
        for (PathType type : LAVA_PATHS) {
            float malus = lava ? 0.0F : type.getMalus();
            if (minion.getPathfindingMalus(type) != malus) {
                minion.setPathfindingMalus(type, malus);
            }
        }
    }

    /**
     * A lava walker's navigation: a strider's (vanilla's StriderPathNavigation). Lava is a stable, walkable place to
     * stand and fire no bar to a path; with the path costs above, it walks over a lava pool rather than round it. While it
     * works out a path, only the lava itself counts to stand on (MinionEntity#pathing).
     */
    static final class LavaNavigation extends GroundPathNavigation {
        private final MinionEntity minion;

        LavaNavigation(MinionEntity minion, Level level) {
            super(minion, level);
            this.minion = minion;
        }

        @Override
        protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy, float followRange) {
            return minion.pathing(() -> super.createPath(targets, regionOffset, offsetUpward, accuracy, followRange));
        }

        @Override
        protected boolean hasValidPathType(PathType type) {
            return LAVA_PATHS.contains(type) || super.hasValidPathType(type);
        }

        @Override
        public boolean isStableDestination(BlockPos pos) {
            return level.getFluidState(pos).is(FluidTags.LAVA) || super.isStableDestination(pos);
        }
    }

    /** Whether this rider steers it: with nothing but the saddle, or holding the item on a stick its head or legs need. */
    static boolean steers(MinionEntity minion, Player rider) {
        return minion.stats().mount().steer().map(item -> rider.isHolding(BuiltInRegistries.ITEM.get(item))).orElse(true);
    }

    /**
     * A rider using the stick that steers their minion spurs it on for a while, and the stick takes its wear (a carrot on
     * a stick 7, a warped fungus on a stick 1; a worn-out one goes back to a fishing rod), as vanilla's does for a pig or a
     * strider: vanilla's own stick asks only its own mob.
     */
    @SubscribeEvent
    public static void onUseStick(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!(player.getControlledVehicle() instanceof MinionEntity minion)) {
            return;
        }
        MinionStats.Mount mount = minion.stats().mount();
        ItemStack stick = event.getItemStack();
        if (mount.steer().isEmpty() || !stick.is(BuiltInRegistries.ITEM.get(mount.steer().get()))) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide));
        if (!player.level().isClientSide && minion.boost()) {
            EquipmentSlot slot = LivingEntity.getSlotForHand(event.getHand());
            ItemStack left = stick.hurtAndConvertOnBreak(mount.wear(), Items.FISHING_ROD, player, slot);
            if (left != stick) {
                player.setItemInHand(event.getHand(), left);
            }
            // a wet slap on the flank
            minion.level().playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.NEUTRAL, 0.8F, 0.7F);
        }
    }
}
