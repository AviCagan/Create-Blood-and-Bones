package com.avicagan.bloodandbones.parts;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Everything one mob's parts do, with its layers applied (archetype, family, overlays by priority, its own
 * file): built once per mob and kept until data reloads.
 *
 * @param layers   the layer ids applied, in order, for "explain"
 * @param parts    by part key: the armour traits, for any piece and per piece, resolved to levels for this mob
 * @param minion   by part key: the minion data of each layer, in order, for minions to read
 * @param variants what particular mobs add, by the traits their carcass kept (a snow fox's hide, a charged creeper's sac)
 */
public record ResolvedMob(ResourceLocation entity, List<ResourceLocation> layers, ResourceLocation material, int colour,
                          Map<String, Part> parts, Map<String, List<com.google.gson.JsonElement>> minion, List<TraitList.Resolved> hide,
                          Map<ResourceLocation, Organ> organs, Optional<FullSet> fullSet, List<Variant> variants) {
    public record Part(List<TraitList.Resolved> armour, Map<String, List<TraitList.Resolved>> pieces) {
    }

    public record Organ(List<TraitList.Resolved> minion, List<TraitList.Resolved> armour) {
    }

    public record FullSet(String name, List<TraitList.Resolved> bonus, List<TraitList.Resolved> drawback) {
    }

    /**
     * What a particular kind of this mob adds on top, when what its carcass kept matches {@code when}: more hide traits,
     * more organ traits (added to the plain ones, the same trait counting once at its highest level).
     */
    public record Variant(com.google.gson.JsonObject when, List<TraitList.Resolved> hide, Map<ResourceLocation, Organ> organs) {
        /**
         * Whether traits a carcass kept match a variant's "if": {"trait": name, "equals": value}, {"trait": name, "in":
         * [values]}, or just {"trait": name} for any value at all.
         */
        public static boolean matches(@org.jetbrains.annotations.Nullable com.google.gson.JsonObject when, Map<String, String> traits) {
            if (when == null || !when.has("trait")) {
                return false;
            }
            String value = traits.get(when.get("trait").getAsString());
            if (value == null) {
                return false;
            }
            if (when.has("equals")) {
                return value.equals(when.get("equals").getAsString());
            }
            if (when.has("in") && when.get("in").isJsonArray()) {
                for (com.google.gson.JsonElement e : when.getAsJsonArray("in")) {
                    if (value.equals(e.getAsString())) {
                        return true;
                    }
                }
                return false;
            }
            return true;
        }
    }

    /** The hide traits of one particular mob of this kind: the plain ones and its variants'. */
    public List<TraitList.Resolved> hide(Map<String, String> traits) {
        List<TraitList.Resolved> out = hide;
        for (Variant variant : variants) {
            if (!variant.hide().isEmpty() && Variant.matches(variant.when(), traits)) {
                out = TraitList.union(out, variant.hide());
            }
        }
        return out;
    }

    /** An organ's minion traits, as cut out of one particular mob of this kind (a charged creeper's sac holds more). */
    public List<TraitList.Resolved> organMinion(ResourceLocation organ, Map<String, String> traits) {
        Organ plain = organs.get(organ);
        List<TraitList.Resolved> out = plain == null ? List.of() : plain.minion();
        for (Variant variant : variants) {
            Organ more = variant.organs().get(organ);
            if (more != null && Variant.matches(variant.when(), traits)) {
                out = TraitList.union(out, more.minion());
            }
        }
        return out;
    }

    /** The same for armour. */
    public List<TraitList.Resolved> organArmour(ResourceLocation organ, Map<String, String> traits) {
        Organ plain = organs.get(organ);
        List<TraitList.Resolved> out = plain == null ? List.of() : plain.armour();
        for (Variant variant : variants) {
            Organ more = variant.organs().get(organ);
            if (more != null && Variant.matches(variant.when(), traits)) {
                out = TraitList.union(out, more.armour());
            }
        }
        return out;
    }

    /**
     * The armour traits a piece gets from this mob's parts of one slot ("head", "leg"...): those for any piece
     * and those aimed at this piece, from the slot's own key and every sub-key ("leg.hind" too: scraps do not
     * keep which leg they were).
     */
    public List<TraitList.Resolved> armourTraits(String slot, String piece) {
        List<TraitList.Resolved> out = List.of();
        for (Map.Entry<String, Part> e : parts.entrySet()) {
            String key = e.getKey();
            if (key.equals(slot) || key.startsWith(slot + ".")) {
                out = TraitList.union(out, e.getValue().armour());
                out = TraitList.union(out, e.getValue().pieces().getOrDefault(piece, List.of()));
            }
        }
        return out;
    }
}
