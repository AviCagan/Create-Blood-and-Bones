package com.avicagan.bloodandbones.event;

import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CarcassEvents {
    /** Mobs whose body became a carcass: they drop nothing, the carcass is the loot. */
    private static final Set<UUID> CARCASS_DEATHS = ConcurrentHashMap.newKeySet();

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(RigManager.INSTANCE);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || entity instanceof Player) {
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
