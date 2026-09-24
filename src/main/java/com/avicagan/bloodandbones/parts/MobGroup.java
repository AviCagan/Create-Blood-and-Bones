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
 */
public record MobGroup(ResourceLocation id, Kind kind, int priority, List<String> members,
                       Optional<ResourceLocation> archetype, Optional<ResourceLocation> family, List<ResourceLocation> overlays,
                       Optional<ResourceLocation> scrapMaterial, Map<String, PartEntry> parts, Optional<TraitList> hide,
                       Map<ResourceLocation, OrganEntry> organTraits, Optional<FullSet> fullSet, Map<String, SlotInfo> boneSlots,
                       Optional<Integer> colour) {
    public enum Kind {
        ARCHETYPE, FAMILY, OVERLAY, MOB
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
        Map<ResourceLocation, OrganEntry> organs = new LinkedHashMap<>();
        if (json.has("organ_traits")) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("organ_traits").entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                organs.put(ResourceLocation.parse(e.getKey()), new OrganEntry(opt(o, "minion", TraitList.CODEC, ops, id), opt(o, "armour", TraitList.CODEC, ops, id)));
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
        return new MobGroup(id, kind, json.has("priority") ? json.get("priority").getAsInt() : 0, List.copyOf(members),
                json.has("archetype") ? Optional.of(ResourceLocation.parse(json.get("archetype").getAsString())) : Optional.empty(),
                json.has("family") ? Optional.of(ResourceLocation.parse(json.get("family").getAsString())) : Optional.empty(),
                List.copyOf(overlays),
                json.has("scrap_material") ? Optional.of(ResourceLocation.parse(json.get("scrap_material").getAsString())) : Optional.empty(),
                parts, opt(json, "hide", TraitList.CODEC, ops, id), organs, set, boneSlots, colour);
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
        return new PartEntry(o.has("minion") ? Optional.of(o.get("minion")) : Optional.empty(), armour, pieces);
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
