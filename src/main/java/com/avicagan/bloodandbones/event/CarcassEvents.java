package com.avicagan.bloodandbones.event;

import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
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
        if (CarcassAssembler.assemble(entity, killer)) {
            CARCASS_DEATHS.add(entity.getUUID());
            entity.discard();
        }
    }

    @SubscribeEvent
    public static void onPrePhysicsTick(ForgeSablePrePhysicsTickEvent event) {
        CarcassDrag.physicsTick(event.getPhysicsSystem().getLevel(), event.getPhysicsSystem().getPartialPhysicsTick(), event.getTimeStep());
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel level) {
            CarcassDrag.tick(level, event.getEntity());
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
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (CARCASS_DEATHS.contains(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (CARCASS_DEATHS.remove(event.getEntity().getUUID())) {
            event.setCanceled(true);
        }
    }
}
