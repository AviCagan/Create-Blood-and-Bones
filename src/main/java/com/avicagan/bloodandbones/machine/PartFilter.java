package com.avicagan.bloodandbones.machine;

import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The part filter every station that takes parts carries (the brief: "every station that removes parts carries a filter,
 * so one line pulls a single part out of a mixed stream and passes the rest through"). A station asks it about each part
 * it could take, as the item that part would be: a carcass piece for a limb, a head or a body, an organ for an organ.
 * <ul>
 * <li>Empty: anything.</li>
 * <li>A spawn egg or a carcass piece: any part of that mob (Create's plain match would take any piece at all).</li>
 * <li>A list filter: each entry asked the same way, as a whitelist or a blacklist.</li>
 * <li>An attribute filter: asked about the part as Create asks it about an item, so "is a carcass hind leg" takes
 * only hind legs (BBItemAttributes), "is a piece of Cow" only a cow's.</li>
 * </ul>
 */
public final class PartFilter {
    private PartFilter() {
    }

    /** What may go in the slot: a spawn egg, a carcass piece, or a Create filter. */
    public static boolean allowed(ItemStack stack) {
        return stack.getItem() instanceof SpawnEggItem
                || stack.is(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get())
                || stack.getItem() instanceof FilterItem;
    }

    /** Whether a filter lets a station take this part of a carcass. */
    public static boolean takes(Level level, ItemStack filter, CarcassSavedData.Carcass carcass, String bone) {
        return filter.isEmpty() || takes(level, FilterItemStack.of(filter), carcass, bone);
    }

    /** Whether a filter lets a station take this part, given as the item it would be. */
    public static boolean takes(Level level, ItemStack filter, ItemStack part) {
        return filter.isEmpty() || takes(level, FilterItemStack.of(filter), part);
    }

    /** As {@link #takes(Level, ItemStack, CarcassSavedData.Carcass, String)}, with the filter already read (a slot keeps it read). */
    public static boolean takes(Level level, FilterItemStack filter, CarcassSavedData.Carcass carcass, String bone) {
        return filter.item().isEmpty() || test(level, filter, CarcassPieceItem.of(carcass, bone));
    }

    /** As {@link #takes(Level, ItemStack, ItemStack)}, with the filter already read. */
    public static boolean takes(Level level, FilterItemStack filter, ItemStack part) {
        return filter.item().isEmpty() || test(level, filter, part);
    }

    private static boolean test(Level level, FilterItemStack filter, ItemStack part) {
        ItemStack item = filter.item();
        if (item.getItem() instanceof SpawnEggItem egg) {
            return BuiltInRegistries.ENTITY_TYPE.getKey(egg.getType(item)).equals(mobOf(part));
        }
        if (item.is(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get())) {
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(item);
            return piece == null || piece.entity().equals(mobOf(part));
        }
        if (filter instanceof FilterItemStack.ListFilterItemStack list) {
            for (FilterItemStack entry : list.containedItems) {
                if (test(level, entry, part)) {
                    return !list.isBlacklist;
                }
            }
            return list.isBlacklist;
        }
        return filter.test(level, part);
    }

    /** The mob a part came from: a piece's, an organ's or a hide's stamp; null if it does not say. */
    @Nullable
    public static ResourceLocation mobOf(ItemStack part) {
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(part);
        if (piece != null) {
            return piece.entity();
        }
        com.avicagan.bloodandbones.parts.Source source = part.get(com.avicagan.bloodandbones.registry.BBDataComponents.SOURCE.get());
        return source == null ? null : source.entity();
    }
}
