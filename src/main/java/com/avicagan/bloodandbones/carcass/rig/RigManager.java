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
 * copy the server sends to each client, which the renderer reads. A mob with no rig file (a modded one nobody has heard
 * of, a tropical fish) is built from its archetype's generic body at the size of its hitbox (GenericRig), worked out the
 * same way on both sides from data both have, so it is never sent. A baby of a kind whose rig has no baby shape is its
 * grown rig shrunk about the feet, as the game draws such a baby.
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
        GENERIC.clear();
        BloodAndBones.LOGGER.info("Loaded {} carcass rigs", rigs.size());
    }

    public static Optional<Rig> forEntity(EntityType<?> type) {
        return forEntity(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    /** The rig a mob's carcass is built from: its own rig file, or else its archetype's generic body at its size. */
    public static Optional<Rig> forEntity(ResourceLocation entityId) {
        Optional<Rig> own = fileRig(entityId);
        return own.isPresent() ? own : generic(entityId, com.avicagan.bloodandbones.parts.PartsData.SERVER, GENERIC);
    }

    /** Only a rig file's rig (or a test's), never a generic body. */
    public static Optional<Rig> fileRig(ResourceLocation entityId) {
        Rig rig = hiddenNow(entityId) ? null : INSTANCE.rigs.get(entityId);
        return Optional.ofNullable(rig != null ? rig : TEST_RIGS.get(entityId));
    }

    /**
     * No-carcass mobs: the ender dragon until it has a body plan (ARCHITECTURE 4.4), the mod's own minion (it dies by the
     * server's minion rules, never as a fresh carcass to butcher again); anything a datapack adds.
     */
    public static final net.minecraft.tags.TagKey<EntityType<?>> NO_CARCASS = net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE, BloodAndBones.asResource("no_carcass"));

    /** Generic bodies built so far, by mob; each is built once, so it is the same object every time (see isBaby). */
    private static final Map<ResourceLocation, Generic> GENERIC = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Generic> CLIENT_GENERIC = new java.util.concurrent.ConcurrentHashMap<>();

    /** A generic body, and the parts data it was built from: built again once that data has changed. */
    private record Generic(int generation, Optional<Rig> rig) {
    }

    /**
     * A mob's generic body: its archetype's (or the generic_rig its groups name), at its hitbox's size. None for what is
     * not a living mob (a player never becomes a carcass, an armour stand is no mob), for the mod's own mobs (a minion is
     * stitched from carcasses and dies by its own rules), for the no_carcass tag, or when its groups name no generic body.
     */
    private static Optional<Rig> generic(ResourceLocation entityId, com.avicagan.bloodandbones.parts.PartsData.Store store, Map<ResourceLocation, Generic> cache) {
        int generation = store.generation();
        Generic known = cache.get(entityId);
        if (known != null && known.generation() == generation) {
            return known.rig();
        }
        Optional<Rig> built = Optional.empty();
        Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityId);
        if (type.isPresent() && type.get() != EntityType.PLAYER && type.get() != EntityType.ARMOR_STAND && !type.get().is(NO_CARCASS)
                && !entityId.getNamespace().equals(BloodAndBones.MOD_ID)
                && net.minecraft.world.entity.ai.attributes.DefaultAttributes.hasSupplier(type.get())) {
            Optional<ResourceLocation> id = store.resolve(entityId, false).carcass().genericRig();
            GenericRig body = id.map(store::genericRig).orElse(null);
            if (body != null) {
                net.minecraft.world.entity.EntityDimensions size = type.get().getDimensions();
                built = Optional.of(body.build(entityId, size.width(), size.height()));
            } else if (id.isPresent()) {
                BloodAndBones.LOGGER.warn("{} names generic rig {}, which is not loaded", entityId, id.get());
            }
        }
        cache.put(entityId, new Generic(generation, built));
        return built;
    }

    /** Whether this rig is a generic body rather than a mob's own. */
    public static boolean isGeneric(Rig rig) {
        return rig.fitted();
    }

    /**
     * For game tests and the showcase: act as if this mob had no rig file until the server reaches this tick, or until the
     * returned hold is let go, whichever comes first. Holds are counted, so two tests hiding the same mob (a test run many
     * times over in one batch) each keep it hidden until both are done. In a single-player world the client's copy is
     * hidden alike.
     */
    public static Runnable hideForTest(ResourceLocation entityId, int untilTick) {
        Object hold = new Object();
        TEST_HIDDEN.computeIfAbsent(entityId, k -> new java.util.concurrent.ConcurrentHashMap<>()).put(hold, untilTick);
        return () -> {
            Map<Object, Integer> holds = TEST_HIDDEN.get(entityId);
            if (holds != null) {
                holds.remove(hold);
            }
        };
    }

    /** Hides by mob: each hold and the tick it runs out. */
    private static final Map<ResourceLocation, Map<Object, Integer>> TEST_HIDDEN = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean hiddenNow(ResourceLocation entityId) {
        Map<Object, Integer> holds = TEST_HIDDEN.get(entityId);
        if (holds == null) {
            return false;
        }
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        int now = server == null ? Integer.MAX_VALUE : server.getTickCount();
        holds.values().removeIf(until -> now >= until);
        return !holds.isEmpty();
    }

    /** What game tests add: a made-up mob's rig, under an id of the test's own. Looked up like the rest; never listed or sent. */
    private static final Map<ResourceLocation, Rig> TEST_RIGS = new java.util.concurrent.ConcurrentHashMap<>();

    /** For game tests: a rig for a made-up mob (a modded one with no data of its own), for the rest of the run. */
    public static void addTestRig(Rig rig) {
        TEST_RIGS.put(rig.entity(), rig);
    }

    public static Map<ResourceLocation, Rig> all() {
        return INSTANCE.rigs;
    }

    /** Baby rigs worked out from the grown ones on first use; cleared whenever the rigs change. */
    private static final Map<ResourceLocation, Baby> BABIES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Baby> CLIENT_BABIES = new java.util.concurrent.ConcurrentHashMap<>();

    /** The rig for a mob, or for its baby: its baby shape's, or its grown rig shrunk about the feet if it has none. */
    public static Optional<Rig> forEntity(ResourceLocation entityId, boolean baby) {
        return baby ? babyOf(entityId, forEntity(entityId), BABIES) : forEntity(entityId);
    }

    /** A baby rig and the grown one it was worked out from: worked out again when the grown one is another object. */
    private record Baby(Rig grown, Rig rig) {
    }

    private static Optional<Rig> babyOf(ResourceLocation entityId, Optional<Rig> grown, Map<ResourceLocation, Baby> cache) {
        if (grown.isEmpty()) {
            return Optional.empty();
        }
        Baby known = cache.get(entityId);
        if (known != null && known.grown() == grown.get()) {
            return Optional.of(known.rig());
        }
        Rig adult = grown.get();
        Rig baby = (adult.baby().isPresent() ? adult : adult.withBaby(Optional.of(SHRUNK))).asBaby();
        cache.put(entityId, new Baby(adult, baby));
        return Optional.of(baby);
    }

    /** A baby drawn as its grown kind at half size about its feet, as the game draws a baby it has no model of its own for. */
    public static final BabyShape SHRUNK = new BabyShape(java.util.List.of(), 1.0F, new org.joml.Vector3f(), 0.5F, 24.0F, java.util.List.of());

    /** Whether this rig is one worked out for a baby (its mob's own rig is another object). */
    public static boolean isBaby(Rig rig) {
        Baby server = BABIES.get(rig.entity());
        Baby client = CLIENT_BABIES.get(rig.entity());
        return server != null && server.rig() == rig || client != null && client.rig() == rig;
    }

    /** The rig a carcass was built from: its mob's, or its mob's baby's. */
    public static Optional<Rig> forCarcass(com.avicagan.bloodandbones.carcass.CarcassSavedData.Carcass carcass) {
        return forEntity(carcass.entity, carcass.baby);
    }

    /** Client side: the rig for a mob or its baby. */
    public static Optional<Rig> clientRig(ResourceLocation entityId, boolean baby) {
        return baby ? babyOf(entityId, clientRig(entityId), CLIENT_BABIES) : clientRig(entityId);
    }

    /** Client side: the rig the server told us about for this mob, or else its generic body, worked out here the same way. */
    public static Optional<Rig> clientRig(ResourceLocation entityId) {
        Optional<Rig> own = clientFileRig(entityId);
        return own.isPresent() ? own : generic(entityId, com.avicagan.bloodandbones.parts.PartsData.CLIENT, CLIENT_GENERIC);
    }

    /** Client side: only the rig the server sent for this mob (hidden alike in a single-player world's tests and showcase). */
    public static Optional<Rig> clientFileRig(ResourceLocation entityId) {
        return hiddenNow(entityId) ? Optional.empty() : Optional.ofNullable(clientRigs.get(entityId));
    }

    /** An empty map clears what we had; otherwise the rigs are added to it. */
    public static void receiveClientRigs(Map<ResourceLocation, Rig> received) {
        CLIENT_BABIES.clear();
        CLIENT_GENERIC.clear();
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
