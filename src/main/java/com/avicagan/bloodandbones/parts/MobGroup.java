package com.avicagan.bloodandbones.parts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One layer of what mobs' parts do (docs/PARTS-AND-TRAITS.md section 4): an archetype (body shape), a family
 * (flavour), an overlay (patches by tag), or one mob's own file. All four share this format; layers apply in
 * that order, each adding to or editing what the ones below gave.
 *
 * @param members   entity ids and "#tag"s (mob files have none: the file's own path names the mob)
 * @param parts     by part key ("head", "leg", "leg.hind", "arm.wing"...): what it does on a minion, and in armour
 * @param boneSlots a mob file's slot for a bone the naming rules get wrong (the shulker's lid is an arm)
 * @param organs    by part key ("torso", "head", "leg.hind"): the organs cut out of that piece at the Surgical Rig, in order
 * @param variants  what a particular mob of this layer adds, by what its carcass kept of it (a snow fox's hide, a charged
 *                  creeper's sac): its hide traits and its organs' traits
 * @param tissue    what its carcass's bodies are made of, which sets how much they weigh (Tissue); a scalar, as scrap_material
 * @param carcass   what it says about its mobs' carcasses: the generic body, the archetype's match rules, the weight class,
 *                  the rot time, butchery yields and a baby's share of them
 */
public record MobGroup(ResourceLocation id, Kind kind, int priority, List<String> members,
                       Optional<ResourceLocation> archetype, Optional<ResourceLocation> family, List<ResourceLocation> overlays,
                       Optional<ResourceLocation> scrapMaterial, Map<String, PartEntry> parts, Optional<TraitList> hide,
                       Map<ResourceLocation, OrganEntry> organTraits, Optional<FullSet> fullSet, Map<String, SlotInfo> boneSlots,
                       Optional<Integer> colour, Map<String, OrganList> organs, List<Variant> variants,
                       Optional<com.avicagan.bloodandbones.carcass.Tissue> tissue, CarcassFacts carcass) {
    public enum Kind {
        ARCHETYPE, FAMILY, OVERLAY, MOB
    }

    /**
     * Files read so far whose minion data still lists a head's old "jobs" and no "knacks" (docs/NEXT.md 1.8): each is read
     * as knacks ({@code MinionData.oldJobs}) and logged once, so a third party's datapack keeps working.
     */
    public static final java.util.concurrent.atomic.AtomicInteger OLD_JOBS = new java.util.concurrent.atomic.AtomicInteger();

    /**
     * What one layer says about its mobs' carcasses (docs/ARCHITECTURE-PROPOSAL.md 15.31). Every field is a scalar the last
     * layer naming it wins, as scrap_material, except the butchery settings, which merge field by field.
     *
     * @param genericRig  the generic body (data/&lt;ns&gt;/generic_rig/&lt;id&gt;.json) a mob with no rig file is built from; an
     *                    archetype names one
     * @param match       an archetype's rules for claiming a mob no file lists (docs/PARTS-AND-TRAITS.md 3.1)
     * @param weightClass the weight class (data/&lt;ns&gt;/weight_class/&lt;id&gt;.json); with none named, its size picks one
     * @param rotTime     ticks to rot, where this layer's mobs differ from their weight class
     * @param butchery    what taking the carcass apart gives, as a rig target's butchery section spells it (any of its fields)
     * @param babyYield   a baby's share of a grown one's yields; with none named, its share of the grown one's size
     */
    public record CarcassFacts(Optional<ResourceLocation> genericRig, List<Match> match, Optional<ResourceLocation> weightClass,
                               Optional<Integer> rotTime, Optional<JsonObject> butchery, Optional<Float> babyYield) {
        public static final CarcassFacts NONE = new CarcassFacts(Optional.empty(), List.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty());

        static CarcassFacts parse(ResourceLocation id, JsonObject json) {
            List<Match> match = new ArrayList<>();
            if (json.has("match")) {
                for (JsonElement e : json.getAsJsonArray("match")) {
                    match.add(Match.parse(id, e.getAsJsonObject()));
                }
            }
            return new CarcassFacts(
                    json.has("generic_rig") ? Optional.of(ResourceLocation.parse(json.get("generic_rig").getAsString())) : Optional.empty(),
                    List.copyOf(match),
                    json.has("weight_class") ? Optional.of(ResourceLocation.parse(json.get("weight_class").getAsString())) : Optional.empty(),
                    rotTime(id, json),
                    json.has("butchery") ? Optional.of(json.getAsJsonObject("butchery").deepCopy()) : Optional.empty(),
                    json.has("baby_yield") ? Optional.of(json.get("baby_yield").getAsFloat()) : Optional.empty());
        }

        /** A rot time of at least a tick, as rigs and weight classes require (rot divides by it); anything less is left out. */
        private static Optional<Integer> rotTime(ResourceLocation id, JsonObject json) {
            if (!json.has("rot_time")) {
                return Optional.empty();
            }
            int ticks = json.get("rot_time").getAsInt();
            if (ticks < 1) {
                com.avicagan.bloodandbones.BloodAndBones.LOGGER.error("{}: rot_time must be at least 1 tick, not {}; left out", id, ticks);
                return Optional.empty();
            }
            return Optional.of(ticks);
        }
    }

    /**
     * One way an archetype claims a mob that no file lists: every condition given must hold, and the archetype whose
     * passing rule scores highest wins.
     *
     * @param legs       how many legs and arms its rig file has, from, to (only a mob with a rig file of its own can pass)
     * @param aspect     its hitbox's width over its height, from, to: a cow is wider than tall, a zombie taller than wide
     * @param category   the spawn categories it may be in ("creature", "monster", "water_ambient"...)
     * @param fireImmune whether it must be (or must not be) immune to fire
     */
    public record Match(int score, Optional<int[]> legs, Optional<int[]> arms, Optional<float[]> aspect, List<String> category,
                        Optional<Boolean> fireImmune) {
        static Match parse(ResourceLocation id, JsonObject o) {
            Optional<int[]> legs = Optional.empty();
            Optional<int[]> arms = Optional.empty();
            if (o.has("rig")) {
                JsonObject rig = o.getAsJsonObject("rig");
                legs = rig.has("legs") ? Optional.of(range(rig.get("legs"))) : Optional.empty();
                arms = rig.has("arms") ? Optional.of(range(rig.get("arms"))) : Optional.empty();
            }
            Optional<float[]> aspect = Optional.empty();
            if (o.has("aspect")) {
                com.google.gson.JsonArray a = o.getAsJsonArray("aspect");
                aspect = Optional.of(new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat()});
            }
            List<String> category = new ArrayList<>();
            if (o.has("category")) {
                JsonElement c = o.get("category");
                if (c.isJsonArray()) {
                    c.getAsJsonArray().forEach(e -> category.add(e.getAsString()));
                } else {
                    category.add(c.getAsString());
                }
            }
            if (legs.isEmpty() && arms.isEmpty() && aspect.isEmpty() && category.isEmpty() && !o.has("fire_immune")) {
                throw new IllegalArgumentException(id + " match: a rule needs a condition (rig, aspect, category, fire_immune)");
            }
            return new Match(o.has("score") ? o.get("score").getAsInt() : 1, legs, arms, aspect, List.copyOf(category),
                    o.has("fire_immune") ? Optional.of(o.get("fire_immune").getAsBoolean()) : Optional.empty());
        }

        /** A count, or [from, to]. */
        private static int[] range(JsonElement e) {
            if (e.isJsonArray()) {
                return new int[]{e.getAsJsonArray().get(0).getAsInt(), e.getAsJsonArray().get(1).getAsInt()};
            }
            return new int[]{e.getAsInt(), e.getAsInt()};
        }

        /**
         * Whether a mob passes.
         *
         * @param legs how many legs its rig file has, or -1 with no rig file
         */
        public boolean passes(net.minecraft.world.entity.EntityType<?> type, int legs, int arms) {
            if (this.legs.isPresent() && (legs < 0 || legs < this.legs.get()[0] || legs > this.legs.get()[1])) {
                return false;
            }
            if (this.arms.isPresent() && (arms < 0 || arms < this.arms.get()[0] || arms > this.arms.get()[1])) {
                return false;
            }
            if (aspect.isPresent()) {
                net.minecraft.world.entity.EntityDimensions size = type.getDimensions();
                float ratio = size.width() / Math.max(0.01F, size.height());
                if (ratio < aspect.get()[0] || ratio > aspect.get()[1]) {
                    return false;
                }
            }
            if (!category.isEmpty() && !category.contains(type.getCategory().getName())) {
                return false;
            }
            return fireImmune.isEmpty() || fireImmune.get() == type.fireImmune();
        }
    }

    /** The armour pieces a part's traits can be aimed at. */
    public static final List<String> PIECES = List.of("helmet", "chestplate", "leggings", "boots", "shoulders", "hips");

    /**
     * A part in one layer: its minion data (kept as written until minions read it) and its armour traits, for
     * whatever piece the part makes, or per piece ("leggings" and "boots" for legs).
     */
    public record PartEntry(Optional<JsonElement> minion, Optional<TraitList> armour, Map<String, TraitList> armourPieces) {
    }

    public record OrganEntry(Optional<TraitList> minion, Optional<TraitList> armour) {
    }

    /**
     * A list of organs in one layer, for one part. A plain list adds to what the layers below gave (organs can come
     * twice: two eyes); {"add": [...], "remove": [ids]} edits instead, a removed organ going however many times it was
     * there, and "replace": true throws away what was inherited first.
     */
    public record OrganList(List<ResourceLocation> add, List<ResourceLocation> remove, boolean replace) {
        static OrganList parse(JsonElement json) {
            if (json.isJsonArray()) {
                return new OrganList(ids(json), List.of(), false);
            }
            JsonObject o = json.getAsJsonObject();
            return new OrganList(o.has("add") ? ids(o.get("add")) : List.of(), o.has("remove") ? ids(o.get("remove")) : List.of(),
                    o.has("replace") && o.get("replace").getAsBoolean());
        }

        private static List<ResourceLocation> ids(JsonElement json) {
            List<ResourceLocation> out = new ArrayList<>();
            for (JsonElement e : json.getAsJsonArray()) {
                out.add(ResourceLocation.parse(e.getAsString()));
            }
            return List.copyOf(out);
        }

        /** This edit applied to what the layers below gave. */
        public List<ResourceLocation> applyTo(List<ResourceLocation> inherited) {
            List<ResourceLocation> out = new ArrayList<>(replace ? List.of() : inherited);
            out.removeIf(remove::contains);
            out.addAll(add);
            return List.copyOf(out);
        }
    }

    /**
     * What one kind of this layer's mobs adds on top (docs/PARTS-AND-TRAITS.md section 9, slice 3: variants from the carcass's
     * traits): {"if": {"trait": "variant", "equals": "snow"}, "hide": [...], "organ_traits": {id: {"minion": [...],
     * "armour": [...]}}}. A part's minion data has variants of its own, inside its "minion" object.
     */
    public record Variant(JsonObject when, Optional<TraitList> hide, Map<ResourceLocation, OrganEntry> organTraits) {
    }

    /** A full set from this one mob: its bonus and its drawback, always together. */
    public record FullSet(Optional<String> name, TraitList bonus, TraitList drawback, boolean replace) {
    }

    public static MobGroup parse(ResourceLocation id, JsonObject json, Kind defaultKind, DynamicOps<JsonElement> ops) {
        Kind kind = json.has("kind") ? Kind.valueOf(json.get("kind").getAsString().toUpperCase(java.util.Locale.ROOT)) : defaultKind;
        List<String> members = new ArrayList<>();
        if (json.has("members")) {
            for (JsonElement e : json.getAsJsonArray("members")) {
                members.add(e.getAsString());
            }
        }
        List<ResourceLocation> overlays = new ArrayList<>();
        if (json.has("overlays")) {
            for (JsonElement e : json.getAsJsonArray("overlays")) {
                overlays.add(ResourceLocation.parse(e.getAsString()));
            }
        }
        Map<String, PartEntry> parts = new LinkedHashMap<>();
        if (json.has("parts")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("parts").entrySet()) {
                parts.put(e.getKey(), part(e.getValue().getAsJsonObject(), ops, id));
            }
        }
        if (oldJobs(json)) {
            com.avicagan.bloodandbones.BloodAndBones.LOGGER.warn("{} lists minion \"jobs\" with no \"knacks\": read as knacks (the first 1.5, the rest 1.25)", id);
            OLD_JOBS.incrementAndGet();
        }
        Map<ResourceLocation, OrganEntry> organs = organs(json, ops, id);
        List<Variant> variants = new ArrayList<>();
        if (json.has("variants")) {
            for (JsonElement e : json.getAsJsonArray("variants")) {
                JsonObject o = e.getAsJsonObject();
                if (!o.has("if") || !o.get("if").isJsonObject()) {
                    throw new IllegalArgumentException(id + " variants: each needs an \"if\"");
                }
                variants.add(new Variant(o.getAsJsonObject("if"), opt(o, "hide", TraitList.CODEC, ops, id), organs(o, ops, id)));
            }
        }
        Optional<FullSet> set = Optional.empty();
        if (json.has("full_set")) {
            JsonObject o = json.getAsJsonObject("full_set");
            set = Optional.of(new FullSet(o.has("name") ? Optional.of(o.get("name").getAsString()) : Optional.empty(),
                    opt(o, "bonus", TraitList.CODEC, ops, id).orElse(TraitList.EMPTY), opt(o, "drawback", TraitList.CODEC, ops, id).orElse(TraitList.EMPTY),
                    o.has("replace") && o.get("replace").getAsBoolean()));
        }
        Map<String, SlotInfo> boneSlots = new LinkedHashMap<>();
        if (json.has("bone_slots")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("bone_slots").entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive()) {
                    boneSlots.put(e.getKey(), SlotInfo.of(PartSlot.byName(v.getAsString())));
                } else {
                    JsonObject o = v.getAsJsonObject();
                    boneSlots.put(e.getKey(), new SlotInfo(PartSlot.byName(o.get("slot").getAsString()),
                            o.has("form") ? o.get("form").getAsString() : "", o.has("sub") ? o.get("sub").getAsString() : ""));
                }
            }
        }
        Optional<Integer> colour = json.has("colour") ? Optional.of(Integer.parseInt(json.get("colour").getAsString().replace("#", ""), 16)) : Optional.empty();
        Map<String, OrganList> organLists = new LinkedHashMap<>();
        if (json.has("organs")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("organs").entrySet()) {
                organLists.put(e.getKey(), OrganList.parse(e.getValue()));
            }
        }
        return new MobGroup(id, kind, json.has("priority") ? json.get("priority").getAsInt() : 0, List.copyOf(members),
                json.has("archetype") ? Optional.of(ResourceLocation.parse(json.get("archetype").getAsString())) : Optional.empty(),
                json.has("family") ? Optional.of(ResourceLocation.parse(json.get("family").getAsString())) : Optional.empty(),
                List.copyOf(overlays),
                json.has("scrap_material") ? Optional.of(ResourceLocation.parse(json.get("scrap_material").getAsString())) : Optional.empty(),
                parts, opt(json, "hide", TraitList.CODEC, ops, id), organs, set, boneSlots, colour, organLists, List.copyOf(variants),
                json.has("tissue") ? Optional.of(com.avicagan.bloodandbones.carcass.Tissue.byName(json.get("tissue").getAsString())) : Optional.empty(),
                CarcassFacts.parse(id, json));
    }

    /** Whether a part's minion data here, or one of its variants, lists "jobs" and no "knacks". */
    private static boolean oldJobs(JsonObject json) {
        if (!json.has("parts") || !json.get("parts").isJsonObject()) {
            return false;
        }
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("parts").entrySet()) {
            if (!e.getValue().isJsonObject() || !(e.getValue().getAsJsonObject().get("minion") instanceof JsonObject minion)) {
                continue;
            }
            if (minion.has("jobs") && !minion.has("knacks")) {
                return true;
            }
            if (minion.get("variants") instanceof com.google.gson.JsonArray variants) {
                for (JsonElement v : variants) {
                    if (v instanceof JsonObject o && o.has("jobs") && !o.has("knacks")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** An object's "organ_traits": each organ's minion and armour lists. */
    private static Map<ResourceLocation, OrganEntry> organs(JsonObject json, DynamicOps<JsonElement> ops, ResourceLocation id) {
        Map<ResourceLocation, OrganEntry> organs = new LinkedHashMap<>();
        if (json.has("organ_traits")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("organ_traits").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                organs.put(ResourceLocation.parse(e.getKey()), new OrganEntry(opt(o, "minion", TraitList.CODEC, ops, id), opt(o, "armour", TraitList.CODEC, ops, id)));
            }
        }
        return organs;
    }

    private static PartEntry part(JsonObject o, DynamicOps<JsonElement> ops, ResourceLocation id) {
        Optional<TraitList> armour = Optional.empty();
        Map<String, TraitList> pieces = new LinkedHashMap<>();
        if (o.has("armour")) {
            JsonElement a = o.get("armour");
            if (a.isJsonObject() && a.getAsJsonObject().keySet().stream().anyMatch(PIECES::contains)) {
                for (Map.Entry<String, JsonElement> e : a.getAsJsonObject().entrySet()) {
                    pieces.put(e.getKey(), decode(TraitList.CODEC, e.getValue(), ops, id + " armour " + e.getKey()));
                }
            } else {
                armour = Optional.of(decode(TraitList.CODEC, a, ops, id + " armour"));
            }
        }
        return new PartEntry(o.has("minion") ? Optional.of(checkedMinion(o.get("minion"), id)) : Optional.empty(), armour, pieces);
    }

    /**
     * A part's minion data as written, less what could only fail later, in play, logged as it loads: a head's disposition
     * that is no id ("Brave"), and a knack whose task is no id ("bloodandbones:Surgeon") or whose value is no number, in its
     * own map or its variants. What is left out counts as never written: the layer under it, or 1.
     */
    private static JsonElement checkedMinion(JsonElement minion, ResourceLocation id) {
        if (!minion.isJsonObject()) {
            return minion;
        }
        JsonObject o = minion.getAsJsonObject().deepCopy();
        check(o, id);
        if (o.get("variants") instanceof com.google.gson.JsonArray variants) {
            for (JsonElement variant : variants) {
                if (variant instanceof JsonObject v) {
                    check(v, id);
                }
            }
        }
        return o;
    }

    private static void check(JsonObject o, ResourceLocation id) {
        JsonElement disposition = o.get("disposition");
        if (disposition != null && !(disposition.isJsonPrimitive() && com.avicagan.bloodandbones.minion.MinionDisposition.valid(disposition.getAsString()))) {
            com.avicagan.bloodandbones.BloodAndBones.LOGGER.warn("{}: disposition {} is no id (lower case, as \"meek\" or \"ns:name\"): left out", id, disposition);
            o.remove("disposition");
        }
        if (o.get("knacks") instanceof JsonObject knacks) {
            for (String task : List.copyOf(knacks.keySet())) {
                JsonElement value = knacks.get(task);
                if (ResourceLocation.tryParse(task) == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    com.avicagan.bloodandbones.BloodAndBones.LOGGER.warn("{}: knack {}: {} is no task id and number: left out", id, task, value);
                    knacks.remove(task);
                }
            }
        }
    }

    private static <T> Optional<T> opt(JsonObject o, String key, Codec<T> codec, DynamicOps<JsonElement> ops, ResourceLocation id) {
        return o.has(key) ? Optional.of(decode(codec, o.get(key), ops, id + " " + key)) : Optional.empty();
    }

    static <T> T decode(Codec<T> codec, JsonElement json, DynamicOps<JsonElement> ops, String where) {
        return codec.parse(ops, json).getOrThrow(error -> new IllegalArgumentException(where + ": " + error));
    }

    /** The members listed as plain ids (no tags). */
    public List<ResourceLocation> ids() {
        return members.stream().filter(m -> !m.startsWith("#")).map(ResourceLocation::parse).toList();
    }

    /** The members listed as tags. */
    public List<ResourceLocation> tags() {
        return members.stream().filter(m -> m.startsWith("#")).map(m -> ResourceLocation.parse(m.substring(1))).toList();
    }
}
