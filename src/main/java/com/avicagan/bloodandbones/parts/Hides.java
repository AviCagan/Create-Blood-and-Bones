package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hides, for fitting over carcass armour (docs/PARTS-AND-TRAITS.md section 7.1). A raw hide skinned off a
 * carcass is stamped with its mob; hides that mobs drop normally have no stamp, so a data map
 * (data_maps/item/hide_sources.json) says whose they are: leather a cow's, rabbit hide a rabbit's. A hide skinned
 * off a mob the data map does not name for it (a parrot's feathers are not a chicken's) is stamped too. A raw hide
 * with no stamp (from before hides were stamped) is a plain covering, from no mob.
 */
public final class Hides {
    /** Whose hide an unstamped item is. */
    public record HideSource(ResourceLocation entity) {
        public static final Codec<HideSource> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("entity").forGetter(HideSource::entity)
        ).apply(i, HideSource::new));
    }

    public static final DataMapType<Item, HideSource> SOURCES = DataMapType.builder(BloodAndBones.asResource("hide_sources"), Registries.ITEM, HideSource.CODEC)
            .synced(HideSource.CODEC, false).build();

    private Hides() {
    }

    /** What this item is as a hide for armour, or null if it is not one. */
    @Nullable
    public static CarcassArmour.Hide of(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Source source = stack.get(BBDataComponents.SOURCE.get());
        if (source != null && source.part().equals("hide")) {
            return new CarcassArmour.Hide(Optional.of(source.entity()), List.of(stack.getItem()));
        }
        HideSource mapped = stack.getItemHolder().getData(SOURCES);
        if (mapped != null) {
            return new CarcassArmour.Hide(Optional.of(mapped.entity()), List.of(stack.getItem()));
        }
        return stack.is(BBItems.RAW_HIDE.get()) ? new CarcassArmour.Hide(Optional.empty(), List.of(stack.getItem())) : null;
    }

    /**
     * A hide skinned off this mob, stamped with it where the item alone would not say so: a raw hide, named for the
     * mob too ("Raw Cow Hide"), and a hide the data map gives to another mob (a parrot's feathers). The rest (a
     * rabbit's rabbit hide) stay unstamped, to stack with the same items mobs drop.
     */
    public static ItemStack stamp(ItemStack stack, ResourceLocation entity) {
        if (stack.is(BBItems.RAW_HIDE.get())) {
            stack.set(BBDataComponents.SOURCE.get(), new Source(entity, "hide", false));
            stack.set(DataComponents.ITEM_NAME, Component.translatable("item.bloodandbones.raw_hide.of", ScrapsItem.mobName(entity)));
            return stack;
        }
        HideSource mapped = stack.getItemHolder().getData(SOURCES);
        if (mapped != null && !mapped.entity().equals(entity)) {
            stack.set(BBDataComponents.SOURCE.get(), new Source(entity, "hide", false));
        }
        return stack;
    }

    /** The hides a piece had fitted, as they went in: a stack of each item, stamped again as they were. */
    public static List<ItemStack> giveBack(CarcassArmour.Hide hide) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        for (Item item : hide.items()) {
            counts.merge(item, 1, Integer::sum);
        }
        List<ItemStack> out = new ArrayList<>();
        counts.forEach((item, count) -> out.add(hide.entity().map(entity -> stamp(new ItemStack(item, count), entity)).orElse(new ItemStack(item, count))));
        return out;
    }
}
