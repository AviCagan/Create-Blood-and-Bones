package com.avicagan.bloodandbones.event;

import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.neoforge.event.ForgeSablePrePhysicsTickEvent;
import dev.ryanhcode.sable.neoforge.event.ForgeSableSubLevelContainerReadyEvent;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CarcassEvents {
    /** Mobs whose body became a carcass: they drop nothing, the carcass is the loot. */
    private static final Set<UUID> CARCASS_DEATHS = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onSableContainerReady(ForgeSableSubLevelContainerReadyEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            event.getContainer().addObserver(new SubLevelObserver() {
                @Override
                public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
                    if (reason == SubLevelRemovalReason.REMOVED) {
                        CarcassSavedData.get(level).onSubLevelRemoved(subLevel.getUniqueId());
                    }
                }
            });
        }
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(RigManager.INSTANCE);
        event.addListener(com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.INSTANCE);
        event.addListener(com.avicagan.bloodandbones.carcass.butchery.ButcheryPaths.INSTANCE);
        for (com.avicagan.bloodandbones.parts.PartsData.Kind kind : com.avicagan.bloodandbones.parts.PartsData.Kind.values()) {
            event.addListener(new com.avicagan.bloodandbones.parts.PartsData.Loader(kind, event.getRegistryAccess()));
        }
    }

    /** Hand every joining or reloading player the rigs and butchery tables, the way vanilla hands out recipes and tags. */
    @SubscribeEvent
    public static void onDatapackSync(net.neoforged.neoforge.event.OnDatapackSyncEvent event) {
        // one packet per rig keeps each well under the frame limit however many mobs get rigs;
        // the channel is optional, so a client without the mod must not be sent any of them
        java.util.List<com.avicagan.bloodandbones.network.RigSyncPayload> payloads = new java.util.ArrayList<>();
        RigManager.all().forEach((id, rig) -> payloads.add(new com.avicagan.bloodandbones.network.RigSyncPayload(java.util.Map.of(id, rig))));
        event.getRelevantPlayers()
                .filter(player -> player.connection.hasChannel(com.avicagan.bloodandbones.network.RigSyncPayload.TYPE))
                .forEach(player -> {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, com.avicagan.bloodandbones.network.RigSyncPayload.RESET);
                    for (com.avicagan.bloodandbones.network.RigSyncPayload payload : payloads) {
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
                    }
                    // the butchery tables are small enough to go in one
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                            new com.avicagan.bloodandbones.network.ButcherySyncPayload(com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.all()));
                });
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (entity instanceof Player player) {
            CarcassDrag.stop((ServerLevel) player.level(), player);
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof LivingEntity killer)) {
            return;
        }
        if (!killer.getMainHandItem().is(BBItems.MEAT_HOOK.get())) {
            return;
        }
        if (com.avicagan.bloodandbones.carcass.CarcassHandover.isHandingOver(entity)) {
            event.setCanceled(true);
            return;
        }
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(entity, killer, false);
        if (carcass != null) {
            // the mob stays a few ticks, frozen, so the client never sees an empty gap before the carcass
            CARCASS_DEATHS.add(entity.getUUID());
            event.setCanceled(true);
            com.avicagan.bloodandbones.carcass.CarcassHandover.begin((ServerLevel) entity.level(), entity, carcass, killer.getLookAngle(), source);
        }
    }

    /**
     * A Meat Hook used at a carcass lying still, where one of its folded parts (a leg, the head) is drawn: those parts have
     * no cells, so the game's own aim passes through them to the block behind. If the look meets such a part before any
     * block, the part is hooked instead (on the server) and the block is left alone (on both sides). What blocks the look
     * is the player's own ray cast (CarcassDrag#hookReach), not the block this use names, so a wall between the player and
     * the part stops the hook. A click that lands on the carcass's own cells goes to CarcassPartBlock, which judges the
     * same way.
     */
    @SubscribeEvent
    public static void onUseOnBlock(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !player.getMainHandItem().is(BBItems.MEAT_HOOK.get())
                || event.getLevel().getBlockState(event.getPos()).getBlock() instanceof com.avicagan.bloodandbones.carcass.CarcassPartBlock) {
            return;
        }
        if (usedOnDrawn(player)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    /**
     * The same, used at nothing, or at a block with no use of its own: the game then uses the item as well, and the server
     * hears of that use without the block, so here too only the player's own ray cast says what is in the way.
     */
    @SubscribeEvent
    public static void onUseInAir(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (event.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && player.getMainHandItem().is(BBItems.MEAT_HOOK.get())
                && usedOnDrawn(player)) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.SUCCESS);
        }
    }

    private static boolean usedOnDrawn(Player player) {
        if (player.level() instanceof ServerLevel level) {
            return CarcassDrag.useOnDrawn(level, player);
        }
        return com.avicagan.bloodandbones.carcass.CarcassAim.nearestResting(player.level(), player.getEyePosition(), player.getLookAngle(),
                CarcassDrag.hookReach(player)) != null;
    }

    /** A mob mid-handover takes no more damage. */
    @SubscribeEvent
    public static void onHurt(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (!event.getEntity().level().isClientSide() && com.avicagan.bloodandbones.carcass.CarcassHandover.isHandingOver(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        CarcassDrag.physicsTick(event.getPhysicsSystem().getLevel(), event.getPhysicsSystem().getPartialPhysicsTick(), event.getTimeStep());
        ShackleHookBlockEntity.physicsTick(event.getPhysicsSystem().getLevel(), event.getTimeStep());
        com.avicagan.bloodandbones.carcass.CarcassFloat.physicsTick(event.getPhysicsSystem().getLevel(), event.getTimeStep());
        com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity.physicsTick(event.getPhysicsSystem().getLevel(), event.getPhysicsSystem().getPartialPhysicsTick(), event.getTimeStep());
    }

    /** Trolleys move before Sable steps physics, so the carcass keeps up with the chain instead of trailing a tick. */
    @SubscribeEvent
    public static void onLevelTickPre(net.neoforged.neoforge.event.tick.LevelTickEvent.Pre event) {
        if (event.getLevel() instanceof ServerLevel level) {
            com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity.advanceAll(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(net.neoforged.neoforge.event.tick.LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            com.avicagan.bloodandbones.carcass.CarcassHandover.tick(level);
            // hauler minions' drags (a player's is kept by its own tick)
            CarcassDrag.tickOthers(level);
            com.avicagan.bloodandbones.carcass.CarcassRest.levelTick(level);
            com.avicagan.bloodandbones.carcass.CarcassRot.levelTick(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            CarcassDrag.tick(level, event.getEntity());
        }
    }

    /** A joining player learns whether the server forces bloodless mode. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) {
            com.avicagan.bloodandbones.registry.BBGameRules.tell(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            CarcassDrag.stop(level, event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CarcassDrag.stopAll();
        com.avicagan.bloodandbones.carcass.CarcassHandover.clear();
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (CARCASS_DEATHS.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    /**
     * The handover is done and the mob is going: forget it here. Its death was cancelled, so no drop event of
     * its own ever came to clear it, and the belongings the handover drops next must not be cancelled.
     */
    public static void handedOver(UUID entity) {
        CARCASS_DEATHS.remove(entity);
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (CARCASS_DEATHS.remove(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }
}
