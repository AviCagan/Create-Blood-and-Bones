package com.avicagan.bloodandbones.carcass.trolley;

import com.avicagan.bloodandbones.registry.BBEntities;

import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Meat Hook right-click on a chain strand while dragging a carcass: hang it on a trolley. */
public class TrolleyEvents {
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (tryHang(event.getEntity())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // the client sends use-on-block whenever a block is in reach, even if the chain is in front of it
        if (event.getHitVec() != null && tryHang(event.getEntity())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    private static boolean tryHang(Player player) {
        if (!(player.level() instanceof ServerLevel level) || !player.getMainHandItem().is(BBItems.MEAT_HOOK.get())) {
            return false;
        }
        CarcassDrag.Drag drag = CarcassDrag.current(player);
        if (drag == null) {
            return false;
        }
        ChainPicker.Hit hit = ChainPicker.pick(level, player, player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE) + 1);
        if (hit == null || !(level.getBlockEntity(hit.conveyor()) instanceof ChainConveyorBlockEntity be)) {
            return false;
        }
        CarcassSavedData.Carcass carcass = CarcassSavedData.get(level).carcass(drag.carcass);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        SubLevel torso = carcass == null || container == null ? null : container.getSubLevel(carcass.bones.get(carcass.rootBone));
        if (!(torso instanceof ServerSubLevel serverTorso) || serverTorso.isRemoved()) {
            return false;
        }
        CarcassDrag.stop(level, player);
        be.prepareStats();
        ChainCursor cursor = new ChainCursor(hit.conveyor(), hit.connection(), hit.position(), be.reversed);
        level.addFreshEntity(ShackleTrolleyEntity.create(BBEntities.SHACKLE_TROLLEY.get(), level, cursor, carcass, serverTorso));
        return true;
    }
}
