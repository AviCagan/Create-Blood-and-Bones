package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The parts-and-traits data (docs/PARTS-AND-TRAITS.md section 4): mob groups (archetypes, families, overlays),
 * per-mob files, traits, scrap materials and the bone slot rules. Loaded on the server from data packs and
 * sent to clients as the files were written, so both sides resolve a mob the same way.
 */
public final class PartsData {
    private static final Gson GSON = new GsonBuilder().create();
    public static final ResourceLocation DEFAULT_MATERIAL = BloodAndBones.asResource("gristle");

    /** The file kinds, each a folder under data/&lt;ns&gt;/. */
    public enum Kind {
        MOB_GROUP("mob_group"), MOB_TRAITS("mob_traits"), TRAIT("trait"), SCRAP_MATERIAL("scrap_material"), BONE_SLOT_RULES("bone_slot_rules");

        public final String folder;

        Kind(String folder) {
            this.folder = folder;
        }
    }

    /** One side's data and its cache of resolved mobs. */
    public static final class Store {
        private final Map<Kind, Map<ResourceLocation, String>> raw = new ConcurrentHashMap<>();
        private volatile Map<ResourceLocation, MobGroup> groups = Map.of();
        private volatile Map<ResourceLocation, MobGroup> mobFiles = Map.of();
        private volatile Map<ResourceLocation, Trait> traits = Map.of();
        private volatile Map<ResourceLocation, ScrapMaterial> materials = Map.of();
        private volatile PartSlots.Rules slotRules = PartSlots.DEFAULT;
        private final Map<String, ResolvedMob> resolved = new ConcurrentHashMap<>();
        private volatile int generation;

        /** Every file of one kind, as written, by id. */
        public Map<ResourceLocation, String> raw(Kind kind) {
            return raw.getOrDefault(kind, Map.of());
        }

        /** Parse one kind's files and take them in place of what was there. */
        public void load(Kind kind, Map<ResourceLocation, String> files, DynamicOps<JsonElement> ops) {
            raw.put(kind, Map.copyOf(files));
            switch (kind) {
                case MOB_GROUP -> {
                    Map<ResourceLocation, MobGroup> out = new LinkedHashMap<>();
                    files.forEach((id, text) -> parse(id, text, json -> out.put(id, MobGroup.parse(id, json, MobGroup.Kind.FAMILY, ops))));
                    groups = Map.copyOf(out);
                }
                case MOB_TRAITS -> {
                    Map<ResourceLocation, MobGroup> out = new LinkedHashMap<>();
                    files.forEach((id, text) -> {
                        // data/<ns>/mob_traits/<entity_ns>/<entity_path>.json: the mob is in the path
                        int slash = id.getPath().indexOf('/');
                        ResourceLocation entity = slash < 0 ? id : ResourceLocation.fromNamespaceAndPath(id.getPath().substring(0, slash), id.getPath().substring(slash + 1));
                        parse(id, text, json -> out.put(entity, MobGroup.parse(entity, json, MobGroup.Kind.MOB, ops)));
                    });
                    mobFiles = Map.copyOf(out);
                }
                case TRAIT -> {
                    Map<ResourceLocation, Trait> out = new LinkedHashMap<>();
                    files.forEach((id, text) -> parse(id, text, json -> out.put(id, MobGroup.decode(Trait.CODEC, json, ops, "trait " + id))));
                    traits = Map.copyOf(out);
                }
                case SCRAP_MATERIAL -> {
                    Map<ResourceLocation, ScrapMaterial> out = new LinkedHashMap<>();
                    files.forEach((id, text) -> parse(id, text, json -> out.put(id, MobGroup.decode(ScrapMaterial.CODEC, json, ops, "scrap material " + id))));
                    materials = Map.copyOf(out);
                }
                case BONE_SLOT_RULES -> {
                    List<PartSlots.Rule> rules = new ArrayList<>();
                    List<PartSlots.SubRule> subs = new ArrayList<>();
                    files.keySet().stream().sorted().forEach(id -> parse(id, files.get(id), json -> {
                        PartSlots.Rules r = PartSlots.parse(json);
                        rules.addAll(r.rules());
                        subs.addAll(r.subs());
                    }));
                    slotRules = rules.isEmpty() ? PartSlots.DEFAULT : new PartSlots.Rules(List.copyOf(rules), subs.isEmpty() ? PartSlots.DEFAULT.subs() : List.copyOf(subs));
                }
            }
            resolved.clear();
            generation++;
        }

        private static void parse(ResourceLocation id, String text, java.util.function.Consumer<JsonObject> use) {
            try {
                use.accept(JsonParser.parseString(text).getAsJsonObject());
            } catch (RuntimeException e) {
                BloodAndBones.LOGGER.error("Bad parts data {}: {}", id, e.getMessage());
            }
        }

        public Map<ResourceLocation, MobGroup> groups() {
            return groups;
        }

        @Nullable
        public MobGroup mobFile(ResourceLocation entity) {
            return mobFiles.get(entity);
        }

        public Map<ResourceLocation, MobGroup> mobFiles() {
            return mobFiles;
        }

        @Nullable
        public Trait trait(ResourceLocation id) {
            return traits.get(id);
        }

        public Map<ResourceLocation, Trait> traits() {
            return traits;
        }

        public ScrapMaterial material(ResourceLocation id) {
            ScrapMaterial m = materials.get(id);
            return m != null ? m : materials.getOrDefault(DEFAULT_MATERIAL, FALLBACK_MATERIAL);
        }

        public Map<ResourceLocation, ScrapMaterial> materials() {
            return materials;
        }

        public PartSlots.Rules slotRules() {
            return slotRules;
        }

        /** Bumped whenever data changes, so caches built from it know to rebuild. */
        public int generation() {
            return generation;
        }

        /** Forget resolved mobs (tags changed). */
        public void invalidate() {
            resolved.clear();
            generation++;
        }

        /** What this mob's parts do, all layers applied. */
        public ResolvedMob resolve(ResourceLocation entity, boolean baby) {
            return resolved.computeIfAbsent(entity + (baby ? "#baby" : ""), k -> build(entity, baby));
        }

        private ResolvedMob build(ResourceLocation entity, boolean baby) {
            MobGroup file = mobFiles.get(entity);
            Optional<EntityType<?>> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity);
            List<MobGroup> layers = new ArrayList<>();
            MobGroup archetype = file != null && file.archetype().isPresent() ? groups.get(file.archetype().get()) : best(MobGroup.Kind.ARCHETYPE, entity, type);
            if (archetype == null) {
                archetype = groups.get(BloodAndBones.asResource(legs(entity, baby) >= 4 ? "quadruped" : "biped"));
            }
            if (archetype != null) {
                layers.add(archetype);
            }
            MobGroup family = file != null && file.family().isPresent() ? groups.get(file.family().get()) : best(MobGroup.Kind.FAMILY, entity, type);
            if (family != null) {
                layers.add(family);
            }
            List<MobGroup> overlays = new ArrayList<>();
            for (MobGroup group : groups.values()) {
                if (group.kind() == MobGroup.Kind.OVERLAY && (member(group, entity, type) || file != null && file.overlays().contains(group.id()))) {
                    overlays.add(group);
                }
            }
            overlays.sort(Comparator.comparingInt(MobGroup::priority));
            layers.addAll(overlays);
            if (file != null) {
                layers.add(file);
            }
            return merge(entity, layers);
        }

        private ResolvedMob merge(ResourceLocation entity, List<MobGroup> layers) {
            ResourceLocation material = DEFAULT_MATERIAL;
            int colour = 0x8a6a5a;
            Map<String, List<TraitList.Resolved>> generic = new LinkedHashMap<>();
            Map<String, Map<String, List<TraitList.Resolved>>> pieces = new LinkedHashMap<>();
            Map<String, List<JsonElement>> minion = new LinkedHashMap<>();
            List<TraitList.Resolved> hide = List.of();
            Map<ResourceLocation, List<TraitList.Resolved>> organMinion = new LinkedHashMap<>();
            Map<ResourceLocation, List<TraitList.Resolved>> organArmour = new LinkedHashMap<>();
            String setName = null;
            List<TraitList.Resolved> bonus = List.of();
            List<TraitList.Resolved> drawback = List.of();
            List<ResourceLocation> ids = new ArrayList<>();
            for (MobGroup layer : layers) {
                ids.add(layer.id());
                if (layer.scrapMaterial().isPresent()) {
                    material = layer.scrapMaterial().get();
                }
                if (layer.colour().isPresent()) {
                    colour = layer.colour().get();
                }
                for (Map.Entry<String, MobGroup.PartEntry> e : layer.parts().entrySet()) {
                    String key = e.getKey();
                    MobGroup.PartEntry part = e.getValue();
                    part.minion().ifPresent(json -> minion.computeIfAbsent(key, k -> new ArrayList<>()).add(json));
                    if (part.armour().isPresent()) {
                        generic.put(key, part.armour().get().applyTo(generic.getOrDefault(key, List.of()), entity));
                    }
                    for (Map.Entry<String, TraitList> p : part.armourPieces().entrySet()) {
                        Map<String, List<TraitList.Resolved>> byPiece = pieces.computeIfAbsent(key, k -> new LinkedHashMap<>());
                        byPiece.put(p.getKey(), p.getValue().applyTo(byPiece.getOrDefault(p.getKey(), List.of()), entity));
                    }
                }
                if (layer.hide().isPresent()) {
                    hide = layer.hide().get().applyTo(hide, entity);
                }
                for (Map.Entry<ResourceLocation, MobGroup.OrganEntry> e : layer.organTraits().entrySet()) {
                    ResourceLocation organ = e.getKey();
                    e.getValue().minion().ifPresent(t -> organMinion.put(organ, t.applyTo(organMinion.getOrDefault(organ, List.of()), entity)));
                    e.getValue().armour().ifPresent(t -> organArmour.put(organ, t.applyTo(organArmour.getOrDefault(organ, List.of()), entity)));
                }
                if (layer.fullSet().isPresent()) {
                    MobGroup.FullSet set = layer.fullSet().get();
                    if (set.replace()) {
                        bonus = List.of();
                        drawback = List.of();
                    }
                    setName = set.name().orElse(setName);
                    bonus = set.bonus().applyTo(bonus, entity);
                    drawback = set.drawback().applyTo(drawback, entity);
                }
            }
            Map<String, ResolvedMob.Part> parts = new LinkedHashMap<>();
            java.util.Set<String> keys = new java.util.LinkedHashSet<>(generic.keySet());
            keys.addAll(pieces.keySet());
            for (String key : keys) {
                parts.put(key, new ResolvedMob.Part(generic.getOrDefault(key, List.of()), Map.copyOf(pieces.getOrDefault(key, Map.of()))));
            }
            Map<ResourceLocation, ResolvedMob.Organ> organs = new LinkedHashMap<>();
            java.util.Set<ResourceLocation> organIds = new java.util.LinkedHashSet<>(organMinion.keySet());
            organIds.addAll(organArmour.keySet());
            for (ResourceLocation organ : organIds) {
                organs.put(organ, new ResolvedMob.Organ(organMinion.getOrDefault(organ, List.of()), organArmour.getOrDefault(organ, List.of())));
            }
            Optional<ResolvedMob.FullSet> set = bonus.isEmpty() && drawback.isEmpty() ? Optional.empty()
                    : Optional.of(new ResolvedMob.FullSet(setName == null ? "set.bloodandbones.pure" : setName, bonus, drawback));
            Map<String, List<JsonElement>> minionCopy = new LinkedHashMap<>();
            minion.forEach((k, v) -> minionCopy.put(k, List.copyOf(v)));
            return new ResolvedMob(entity, List.copyOf(ids), material, colour, Map.copyOf(parts), Map.copyOf(minionCopy), hide, Map.copyOf(organs), set);
        }

        /** The group of this kind that lists the mob, by id or tag; the highest priority wins. */
        @Nullable
        private MobGroup best(MobGroup.Kind kind, ResourceLocation entity, Optional<EntityType<?>> type) {
            MobGroup best = null;
            for (MobGroup group : groups.values()) {
                if (group.kind() == kind && member(group, entity, type) && (best == null || group.priority() > best.priority())) {
                    best = group;
                }
            }
            return best;
        }

        private static boolean member(MobGroup group, ResourceLocation entity, Optional<EntityType<?>> type) {
            if (group.ids().contains(entity)) {
                return true;
            }
            if (type.isEmpty()) {
                return false;
            }
            for (ResourceLocation tag : group.tags()) {
                if (type.get().is(TagKey.create(Registries.ENTITY_TYPE, tag))) {
                    return true;
                }
            }
            return false;
        }

        /** How many legs the mob's rig has, for the fallback body shape. */
        private int legs(ResourceLocation entity, boolean baby) {
            Optional<Rig> rig = rig(entity, baby);
            if (rig.isEmpty()) {
                return 0;
            }
            int n = 0;
            for (var bone : rig.get().bones()) {
                if (PartSlots.of(this, entity, rig.get(), bone.name()).slot() == PartSlot.LEG) {
                    n++;
                }
            }
            return n;
        }

        public Optional<Rig> rig(ResourceLocation entity, boolean baby) {
            return this == CLIENT ? RigManager.clientRig(entity, baby) : RigManager.forEntity(entity, baby);
        }
    }

    /** A material for when none is loaded (never with the mod's own data). */
    static final ScrapMaterial FALLBACK_MATERIAL = new ScrapMaterial("gristle", Map.of("helmet", 1, "chestplate", 3, "leggings", 2, "boots", 1),
            0.0F, 0.0F, 9, 10, 24.0F, List.of());

    public static final Store SERVER = new Store();
    public static final Store CLIENT = new Store();

    private PartsData() {
    }

    /** The data for this side. */
    public static Store of(@Nullable Level level) {
        return level != null && level.isClientSide ? CLIENT : SERVER;
    }

    /** A loader for one kind of file, parsed with the registries of the reload it came with. */
    public static final class Loader extends SimpleJsonResourceReloadListener {
        private final Kind kind;
        private final HolderLookup.Provider registries;

        public Loader(Kind kind, HolderLookup.Provider registries) {
            super(GSON, kind.folder);
            this.kind = kind;
            this.registries = registries;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> jsons, ResourceManager manager, ProfilerFiller profiler) {
            Map<ResourceLocation, String> files = new HashMap<>();
            jsons.forEach((id, json) -> files.put(id, GSON.toJson(json)));
            SERVER.load(kind, files, RegistryOps.create(JsonOps.INSTANCE, registries));
            BloodAndBones.LOGGER.info("Loaded {} parts files of kind {}", files.size(), kind.folder);
        }
    }

    /** One kind of parts file, as written, from the server to a client. */
    public record SyncPayload(int kind, Map<ResourceLocation, String> files) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(BloodAndBones.asResource("parts_sync"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SyncPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SyncPayload::kind,
                ByteBufCodecs.map(HashMap::new, ResourceLocation.STREAM_CODEC, ByteBufCodecs.stringUtf8(1 << 20)), SyncPayload::files,
                SyncPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Everything the server has, one payload per kind. */
    public static List<SyncPayload> payloads() {
        List<SyncPayload> out = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            out.add(new SyncPayload(kind.ordinal(), new HashMap<>(SERVER.raw(kind))));
        }
        return out;
    }

    /** On the client: take the server's files. */
    public static void receive(SyncPayload payload, HolderLookup.Provider registries) {
        Kind kind = Kind.values()[payload.kind()];
        CLIENT.load(kind, payload.files(), RegistryOps.create(JsonOps.INSTANCE, registries));
    }
}
