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
 * Loads {@code data/<namespace>/rig/<entity namespace>/<entity path>.json} files.
 */
public class RigManager extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    public static final RigManager INSTANCE = new RigManager();

    private volatile Map<ResourceLocation, Rig> rigs = Map.of();

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
        BloodAndBones.LOGGER.info("Loaded {} carcass rigs", rigs.size());
    }

    public static Optional<Rig> forEntity(EntityType<?> type) {
        return Optional.ofNullable(INSTANCE.rigs.get(BuiltInRegistries.ENTITY_TYPE.getKey(type)));
    }

    public static Optional<Rig> forEntity(ResourceLocation entityId) {
        return Optional.ofNullable(INSTANCE.rigs.get(entityId));
    }

    public static Map<ResourceLocation, Rig> all() {
        return INSTANCE.rigs;
    }
}
