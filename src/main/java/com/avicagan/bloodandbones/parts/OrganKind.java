package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * An organ's own file (docs/PARTS-AND-TRAITS.md section 4.9, {@code data/<ns>/organ/<id>.json}): the item it comes out
 * as (the Gland for the special ones, the heart, lungs, stomach and eye their own items, a few vanilla items), its name
 * and its bloodless name (a machine part), how its Gland looks (a base shape tinted with its colour), which carcass
 * armour pieces take it, and what else comes out with it when it is cut out (a creeper's sac spills gunpowder). What it
 * does is not here: that is each mob's {@code organ_traits}.
 *
 * @param look         the Gland's shape: sac, gland, bulb, core, bladder, spinneret, fat, marrow, gut, heart or eye
 * @param tint         the Gland's colour, 0xRRGGBB
 * @param armourPieces the carcass armour pieces it fits ("helmet", "chestplate", "leggings", "boots")
 */
public record OrganKind(Item item, String name, Optional<String> bloodlessName, String look, int tint, List<String> armourPieces,
                        List<ExtraDrop> extraDrops) {
    /** Something that comes out with the organ: between min and max of an item. */
    public record ExtraDrop(Item item, int min, int max) {
        public static final Codec<ExtraDrop> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(ExtraDrop::item),
                Codec.INT.optionalFieldOf("min", 1).forGetter(ExtraDrop::min),
                Codec.INT.optionalFieldOf("max", 1).forGetter(ExtraDrop::max)
        ).apply(i, ExtraDrop::new));
    }

    /** The Gland's shapes, in the order its model picks them (an unknown one draws as a gland). */
    public static final List<String> LOOKS = List.of("gland", "sac", "bulb", "core", "bladder", "spinneret", "fat", "marrow", "gut", "heart", "eye");

    /** "#8b9a46" and back. */
    private static final Codec<Integer> COLOUR = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return DataResult.error(() -> "not a colour: " + s);
        }
    }, c -> String.format("#%06x", c));

    public static final Codec<OrganKind> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(OrganKind::item),
            Codec.STRING.fieldOf("name").forGetter(OrganKind::name),
            Codec.STRING.optionalFieldOf("bloodless_name").forGetter(OrganKind::bloodlessName),
            Codec.STRING.optionalFieldOf("look", "gland").forGetter(OrganKind::look),
            COLOUR.optionalFieldOf("tint", 0x9A4A4A).forGetter(OrganKind::tint),
            Codec.STRING.listOf().optionalFieldOf("armour_pieces", List.of()).forGetter(OrganKind::armourPieces),
            ExtraDrop.CODEC.listOf().optionalFieldOf("extra_drops", List.of()).forGetter(OrganKind::extraDrops)
    ).apply(i, OrganKind::new));

    /** Which of {@link #LOOKS} it is, for the Gland's model. */
    public int lookIndex() {
        return Math.max(0, LOOKS.indexOf(look));
    }
}
