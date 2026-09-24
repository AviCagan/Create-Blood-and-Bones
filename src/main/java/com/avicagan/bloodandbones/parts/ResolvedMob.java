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
 */
public record ResolvedMob(ResourceLocation entity, List<ResourceLocation> layers, ResourceLocation material, int colour,
                          Map<String, Part> parts, Map<String, List<com.google.gson.JsonElement>> minion, List<TraitList.Resolved> hide,
                          Map<ResourceLocation, Organ> organs, Optional<FullSet> fullSet) {
    public record Part(List<TraitList.Resolved> armour, Map<String, List<TraitList.Resolved>> pieces) {
    }

    public record Organ(List<TraitList.Resolved> minion, List<TraitList.Resolved> armour) {
    }

    public record FullSet(String name, List<TraitList.Resolved> bonus, List<TraitList.Resolved> drawback) {
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
