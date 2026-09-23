package com.avicagan.bloodandbones.carcass.rig;

import com.avicagan.bloodandbones.BloodAndBones;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads {@code data/<namespace>/rig/<entity namespace>/<entity path>.json} files on the server and keeps the
 * copy the server sends to each client, which the renderer reads.
 */
public class RigManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    public static final RigManager INSTANCE = new RigManager();

    private volatile Map<ResourceLocation, Rig> rigs = Map.of();
    /** what the server last sent us; on an integrated server this is a separate copy of the same data */
    private static volatile Map<ResourceLocation, Rig> clientRigs = Map.of();

    private RigManager() {
        super(GSON, "rig");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Rig> loaded = new HashMap<>();
        jsons.forEach((id, json) -> Rig.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> BloodAndBones.LOGGER.error("Bad rig {}: {}", id, error))
                .ifPresent(rig -> loaded.put(rig.entity(), rig)));
        rigs = Map.copyOf(loaded);
        BABIES.clear();
        BloodAndBones.LOGGER.info("Loaded {} carcass rigs", rigs.size());
    }

    public static Optional<Rig> forEntity(EntityType<?> type) {
        return forEntity(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    public static Optional<Rig> forEntity(ResourceLocation entityId) {
        return Optional.ofNullable(INSTANCE.rigs.get(entityId));
    }

    public static Map<ResourceLocation, Rig> all() {
        return INSTANCE.rigs;
    }

    /** Baby rigs worked out from the adult ones on first use; cleared whenever the rigs change. */
    private static final Map<ResourceLocation, Optional<Rig>> BABIES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Optional<Rig>> CLIENT_BABIES = new java.util.concurrent.ConcurrentHashMap<>();

    /** The rig for a mob, or for its baby; empty for a baby whose kind has no baby shape. */
    public static Optional<Rig> forEntity(ResourceLocation entityId, boolean baby) {
        if (!baby) {
            return forEntity(entityId);
        }
        return BABIES.computeIfAbsent(entityId, id -> forEntity(id).filter(rig -> rig.baby().isPresent()).map(Rig::asBaby));
    }

    /** Whether this rig is one worked out for a baby (its mob's own rig is another object). */
    public static boolean isBaby(Rig rig) {
        return rig.baby().isEmpty() && forEntity(rig.entity()).map(adult -> adult != rig).orElse(false);
    }

    /** The rig a carcass was built from: its mob's, or its mob's baby's. */
    public static Optional<Rig> forCarcass(com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcass) {
        return forEntity(carcass.entity, carcass.baby);
    }

    /** Client side: the rig for a mob or its baby. */
    public static Optional<Rig> clientRig(ResourceLocation entityId, boolean baby) {
        if (!baby) {
            return clientRig(entityId);
        }
        return CLIENT_BABIES.computeIfAbsent(entityId, id -> clientRig(id).filter(rig -> rig.baby().isPresent()).map(Rig::asBaby));
    }

    /** Client side: the rig the server told us about for this mob. */
    public static Optional<Rig> clientRig(ResourceLocation entityId) {
        return Optional.ofNullable(clientRigs.get(entityId));
    }

    /** An empty map clears what we had; otherwise the rigs are added to it. */
    public static void receiveClientRigs(Map<ResourceLocation, Rig> received) {
        CLIENT_BABIES.clear();
        if (received.isEmpty()) {
            clientRigs = Map.of();
            return;
        }
        Map<ResourceLocation, Rig> merged = new HashMap<>(clientRigs);
        merged.putAll(received);
        clientRigs = Map.copyOf(merged);
        BloodAndBones.LOGGER.debug("Client now knows {} carcass rigs", clientRigs.size());
    }
}
