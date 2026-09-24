package com.avicagan.bloodandbones.decoration;

import com.avicagan.bloodandbones.carcass.trolley.ChainCursor;
import com.avicagan.bloodandbones.carcass.trolley.ChainPicker;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Gut Chain used on a Create chain conveyor's chain: a link of it hangs there and rides the chain (see
 * {@link HangingGutChainEntity}). Both sides look for the chain, so the client does not place the held
 * block on whatever is behind it; only the server hangs anything.
 */
public final class GutChainHanging {
    private GutChainHanging() {
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (tryHang(event.getEntity(), event.getHand(), null)) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // the client sends use-on-block whenever a block is in reach, even with the chain in front of it
        if (tryHang(event.getEntity(), event.getHand(), event.getHitVec().getLocation())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    /**
     * Whether the player is using Gut Chain on a chain strand nearer than the block they aim at (if any);
     * on the server this also hangs a link there.
     */
    public static boolean tryHang(Player player, InteractionHand hand, @Nullable Vec3 blockHit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(BBBlocks.GUT_CHAIN.asItem()) || player.isSpectator()) {
            return false;
        }
        ChainPicker.Hit hit = ChainPicker.pick(player.level(), player, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1);
        if (hit == null || (blockHit != null && hit.distance() > blockHit.distanceTo(player.getEyePosition()))) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return true;
        }
        if (!(level.getBlockEntity(hit.conveyor()) instanceof ChainConveyorBlockEntity be)) {
            return false;
        }
        be.prepareStats();
        ChainCursor cursor = new ChainCursor(hit.conveyor(), hit.connection(), hit.position(), be.reversed);
        HangingGutChainEntity chain = HangingGutChainEntity.create(level, cursor, 1);
        level.addFreshEntity(chain);
        stack.consume(1, player);
        level.playSound(null, chain.getX(), chain.getY() - 0.5, chain.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
        return true;
    }
}
