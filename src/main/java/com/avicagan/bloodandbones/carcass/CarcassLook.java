package com.avicagan.bloodandbones.carcass;

import com.avicagan.bloodandbones.carcass.rig.RenderPass;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.animal.horse.Markings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What one particular dying mob looked like, boiled down to the texture it wore and the coats over it,
 * because a carcass has no entity to ask any more. The rig says which placeholders, tints and flags it
 * cares about; this fills them in from the mob.
 *
 * @param texture the skin
 * @param passes  extra coats, in draw order
 */
public record CarcassLook(ResourceLocation texture, List<Coat> passes) {
    /** A resolved coat: which model layer's parts, which texture, tinted how (ARGB, -1 for none). */
    public record Coat(String layer, ResourceLocation texture, int tint) {
    }

    private static final Map<Markings, String> MARKINGS = Map.of(
            Markings.NONE, "",
            Markings.WHITE, "white",
            Markings.WHITE_FIELD, "whitefield",
            Markings.WHITE_DOTS, "whitedots",
            Markings.BLACK_DOTS, "blackdots");

    public static CarcassLook of(LivingEntity entity, Rig rig) {
        Map<String, String> variables = new HashMap<>();
        Map<String, Integer> tints = new HashMap<>();
        Set<String> flags = new HashSet<>();
        if (entity instanceof Sheep sheep) {
            tints.put("wool", Sheep.getColor(sheep.getColor()));
            if (sheep.isSheared()) {
                flags.add("sheared");
            }
        }
        if (entity instanceof Horse horse) {
            variables.put("variant", horse.getVariant().getSerializedName());
            variables.put("markings", MARKINGS.getOrDefault(horse.getMarkings(), ""));
            if (horse.getMarkings() == Markings.NONE) {
                flags.add("no_markings");
            }
        }
        if (entity instanceof Wolf wolf) {
            var variant = wolf.getVariant().value();
            variables.put("wolf_texture", (wolf.isTame() ? variant.tameTexture() : variant.wildTexture()).toString());
        }
        rig.variantNames().forEach((from, to) -> {
            if (to != null && from.equals(variables.get("variant"))) {
                variables.put("variant", to);
            }
        });
        ResourceLocation texture = resolve(rig.texture(), variables);
        List<Coat> passes = new ArrayList<>();
        for (RenderPass pass : rig.passes()) {
            if (pass.unless().isPresent() && flags.contains(pass.unless().get())) {
                continue;
            }
            int tint = pass.tint().map(name -> tints.getOrDefault(name, -1)).orElse(-1);
            passes.add(new Coat(pass.layer(), resolve(pass.texture(), variables), tint));
        }
        return new CarcassLook(texture, passes);
    }

    /** Fills {@code {name}} placeholders; a placeholder naming a full texture path replaces the whole string. */
    static ResourceLocation resolve(String pattern, Map<String, String> variables) {
        String text = pattern;
        for (Map.Entry<String, String> variable : variables.entrySet()) {
            text = text.replace("{" + variable.getKey() + "}", variable.getValue());
        }
        ResourceLocation parsed = ResourceLocation.tryParse(text);
        if (parsed == null) {
            // an unfilled placeholder: fall back to the pattern with placeholders stripped, so something draws
            parsed = ResourceLocation.tryParse(text.replaceAll("\\{[a-z_]+\\}", "").replace("__", "_"));
        }
        return parsed == null ? ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png") : parsed;
    }
}
