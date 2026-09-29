package com.avicagan.bloodandbones.carcass.butchery;

import com.avicagan.bloodandbones.BloodAndBones;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Loads the butchery paths ({@code data/<namespace>/butchery_path/<id>.json}). The mod uses four:
 * <ul>
 * <li>{@link #HAND}: a blade in a player's or a minion's hand, in the field or at the Butcher's Table: about half, with
 * real loss;</li>
 * <li>{@link #STATION}: a filtered machine or a Deployer at a station (the Deglover, the Butcher's Table): all of it;</li>
 * <li>{@link #MANGLER}: the terminal grind: the least meat and bone, but the mob's own drops and armour scraps;</li>
 * <li>{@link #SURGERY}: the Surgical Rig: all of it, with the organs on top (those come from the rig itself).</li>
 * </ul>
 * A path with no file takes everything, as the station does.
 */
public class ButcheryPaths extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();
    public static final ButcheryPaths INSTANCE = new ButcheryPaths();

    public static final ResourceLocation HAND = BloodAndBones.asResource("hand");
    public static final ResourceLocation STATION = BloodAndBones.asResource("station");
    public static final ResourceLocation MANGLER = BloodAndBones.asResource("mangler");
    public static final ResourceLocation SURGERY = BloodAndBones.asResource("surgery");

    private volatile Map<ResourceLocation, ButcheryPath> paths = Map.of();

    private ButcheryPaths() {
        super(GSON, "butchery_path");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, ButcheryPath> loaded = new HashMap<>();
        jsons.forEach((id, json) -> ButcheryPath.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> BloodAndBones.LOGGER.error("Bad butchery path {}: {}", id, error))
                .ifPresent(path -> loaded.put(id, path)));
        paths = Map.copyOf(loaded);
        BloodAndBones.LOGGER.info("Loaded {} butchery paths", paths.size());
    }

    public static ButcheryPath get(ResourceLocation id) {
        return INSTANCE.paths.getOrDefault(id, ButcheryPath.FULL);
    }
}
