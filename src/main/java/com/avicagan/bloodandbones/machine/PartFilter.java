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
 * <li>An organ (a heart, a Gland, a Spider Eye): that organ, out of any mob; no part that is not one.</li>
 * </ul>
 * A filter that names an organ ({@link #namesOrgans}: an organ in it, in its list, or "is the organ ..." among its
 * attributes) picks organs: the Surgical Rig asks it about each organ it could take, as the item that organ comes out as,
 * so one set to a heart takes only hearts. A filter that names only mobs and parts takes a part's organs with the part.
 */
public final class PartFilter {
    private PartFilter() {
    }

    /** What may go in the slot: a spawn egg, a carcass piece, or a Create filter. */
    public static boolean allowed(ItemStack stack) {
        return stack.getItem() instanceof SpawnEggItem
                || stack.is(com.avicagan.bloodandbones.registry.BBItems.CARCASS_PIECE.get())
                || stack.getItem() instanceof FilterItem
                || organ(stack) != null;
    }

    /** The organ an item in the slot (or in a list) names, whoever it came out of; null for none. */
    @Nullable
    static ResourceLocation organ(ItemStack stack) {
        return com.avicagan.bloodandbones.parts.Organs.idOf(stack, com.avicagan.bloodandbones.parts.CarcassArmourItem.store());
    }

    /**
     * Whether a filter picks organs rather than parts: an organ, a list with one in it, or an Attribute Filter asking which
     * organ an item is. The rig then asks it about each organ as itself; otherwise about the part the organ is in.
     */
    public static boolean namesOrgans(FilterItemStack filter) {
        if (filter.item().isEmpty()) {
            return false;
        }
        if (filter instanceof FilterItemStack.ListFilterItemStack list) {
            return list.containedItems.stream().anyMatch(PartFilter::namesOrgans);
        }
        if (filter instanceof FilterItemStack.AttributeFilterItemStack attributes) {
            return attributes.attributeTests.stream().anyMatch(test -> test.getFirst() instanceof com.avicagan.bloodandbones.registry.BBItemAttributes.OrganIs);
        }
        return !filter.isFilterItem() && organ(filter.item()) != null;
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
        ResourceLocation organ = item.getItem() instanceof FilterItem ? null : organ(item);
        if (organ != null) {
            return organ.equals(com.avicagan.bloodandbones.parts.Organs.idOf(part, com.avicagan.bloodandbones.parts.PartsData.of(level)));
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

    /**
     * Whether a part is like a sample held up to it (a butcher minion's other hand, docs/BRIEF-AUDIT.md package 10): a
     * carcass piece held means that part of that mob (a cow's hind leg: cows' hind legs, by the slot rules that are data);
     * a filter or anything else held is asked as the slot asks it.
     */
    public static boolean alike(Level level, ItemStack sample, ItemStack part) {
        if (sample.isEmpty()) {
            return true;
        }
        CarcassPieceItem.Piece held = CarcassPieceItem.piece(sample);
        if (held == null) {
            return takes(level, sample, part);
        }
        CarcassPieceItem.Piece piece = CarcassPieceItem.piece(part);
        return piece != null && held.entity().equals(piece.entity()) && held.baby() == piece.baby()
                && com.avicagan.bloodandbones.registry.BBItemAttributes.PiecePart.kindOf(held, level).equals(com.avicagan.bloodandbones.registry.BBItemAttributes.PiecePart.kindOf(piece, level))
                && java.util.Objects.equals(com.avicagan.bloodandbones.registry.BBItemAttributes.PieceSlot.slotOf(held, level),
                com.avicagan.bloodandbones.registry.BBItemAttributes.PieceSlot.slotOf(piece, level));
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
