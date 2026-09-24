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

import java.util.Optional;

/**
 * Hides, for fitting over carcass armour (docs/PARTS-AND-TRAITS.md section 7.1). A raw hide skinned off a
 * carcass is stamped with its mob; hides that mobs drop normally have no stamp, so a data map
 * (data_maps/item/hide_sources.json) says whose they are: leather a cow's, rabbit hide a rabbit's. A raw hide
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
            return new CarcassArmour.Hide(Optional.of(source.entity()), stack.getItem());
        }
        HideSource mapped = stack.getItemHolder().getData(SOURCES);
        if (mapped != null) {
            return new CarcassArmour.Hide(Optional.of(mapped.entity()), stack.getItem());
        }
        return stack.is(BBItems.RAW_HIDE.get()) ? new CarcassArmour.Hide(Optional.empty(), stack.getItem()) : null;
    }

    /** A raw hide skinned off this mob, named for it ("Raw Cow Hide"). */
    public static ItemStack stamp(ItemStack stack, ResourceLocation entity) {
        stack.set(BBDataComponents.SOURCE.get(), new Source(entity, "hide", false));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.bloodandbones.raw_hide.of", ScrapsItem.mobName(entity)));
        return stack;
    }

    /** The hides a piece had fitted, as they went in: stamped again if they were stamped raw hides. */
    public static ItemStack giveBack(CarcassArmour.Hide hide, int count) {
        ItemStack stack = new ItemStack(hide.item(), count);
        if (hide.entity().isPresent() && stack.is(BBItems.RAW_HIDE.get())) {
            stamp(stack, hide.entity().get());
        }
        return stack;
    }
}
