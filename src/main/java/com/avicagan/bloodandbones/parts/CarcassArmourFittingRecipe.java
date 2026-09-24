package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.registry.BBRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fitting a piece of carcass armour (docs/PARTS-AND-TRAITS.md section 7.3): the piece and one kind of thing,
 * anywhere in the grid.
 * <ul>
 *     <li>Hides of one mob, as many as the piece needs (one for a helmet or boots, two leggings, three a
 *     chestplate), of any items that mob's hide comes as (two leather and a raw cow hide): its covering, with
 *     that mob's hide traits. They take the place of any hides it had, which come back as they went in.</li>
 *     <li>An organ cut out of a mob, into a piece its file's "armour_pieces" name: eyes a helmet, a heart or lungs a
 *     chestplate, a stomach a chestplate or leggings, a creeper's powder sac a chestplate, a rabbit's foot leggings or
 *     boots. It takes the place of any organ it had, which comes back as it went in.</li>
 *     <li>The next tier's ingot or gem: blood steel, then blood diamond, then soul netherite.</li>
 * </ul>
 * Create's Mechanical Crafters call {@link #assemble} as a crafting grid does, so they fit hides, organs and
 * tiers too. They give back only what an item leaves behind by itself, never what a recipe gives back, so
 * in them a fitting that would take something out is refused rather than losing it: that swap is by hand.
 */
public class CarcassArmourFittingRecipe extends CustomRecipe {
    public CarcassArmourFittingRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** What fitting makes: the piece as it comes out, where the piece lay, and what comes back out of it. */
    public record Fit(ItemStack result, int pieceSlot, List<ItemStack> giveBack) {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        Fit fit = fit(input, PartsData.of(level));
        return fit != null && (fit.giveBack().isEmpty() || !inCrafter(input));
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        Fit fit = fit(input, CarcassArmourItem.store());
        return fit == null ? ItemStack.EMPTY : fit.result();
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> out = super.getRemainingItems(input);
        Fit fit = fit(input, CarcassArmourItem.store());
        if (fit != null && !fit.giveBack().isEmpty()) {
            // the piece is used up, so what came out of it goes back where it lay; hides of a second kind go where new ones lay
            List<ItemStack> back = fit.giveBack();
            out.set(fit.pieceSlot(), back.get(0));
            int next = 1;
            for (int i = 0; i < input.size() && next < back.size(); i++) {
                if (i != fit.pieceSlot() && !input.getItem(i).isEmpty() && out.get(i).isEmpty()) {
                    out.set(i, back.get(next++));
                }
            }
        }
        return out;
    }

    /** What these items fit together into, or null if they do not. */
    @Nullable
    public static Fit fit(CraftingInput input, PartsData.Store store) {
        int pieceSlot = -1;
        List<ItemStack> modifiers = new ArrayList<>();
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof CarcassArmourItem && CarcassArmourItem.armour(stack) != null) {
                if (pieceSlot >= 0) {
                    return null;
                }
                pieceSlot = i;
            } else {
                modifiers.add(stack);
            }
        }
        if (pieceSlot < 0 || modifiers.isEmpty()) {
            return null;
        }
        ItemStack piece = input.getItem(pieceSlot);
        CarcassArmour armour = CarcassArmourItem.armour(piece);
        ItemStack first = modifiers.get(0);

        CarcassArmour.Hide hide = Hides.of(first);
        if (hide != null) {
            // hides from one mob, as many as the piece needs, each item remembered
            List<Item> items = new ArrayList<>();
            for (ItemStack other : modifiers) {
                CarcassArmour.Hide each = Hides.of(other);
                if (each == null || !each.entity().equals(hide.entity())) {
                    return null;
                }
                items.add(other.getItem());
            }
            if (modifiers.size() != armour.hidesNeeded()) {
                return null;
            }
            List<ItemStack> giveBack = armour.hide().map(Hides::giveBack).orElse(List.of());
            return new Fit(remake(piece, armour.withHide(Optional.of(new CarcassArmour.Hide(hide.entity(), List.copyOf(items)))), store), pieceSlot, giveBack);
        }
        if (modifiers.size() != 1) {
            return null;
        }
        CarcassArmour.Organ organ = Organs.of(first, store);
        if (organ != null) {
            if (!Organs.fits(store, organ.organ(), armour.piece())) {
                return null;
            }
            List<ItemStack> giveBack = armour.organ().map(old -> Organs.stack(store, old.organ(), old.entity(), old.baby())).filter(back -> !back.isEmpty())
                    .map(List::of).orElse(List.of());
            return new Fit(remake(piece, armour.withOrgan(Optional.of(organ)), store), pieceSlot, giveBack);
        }
        ArmourTier tier = store.tierFor(first.getItem());
        if (tier != null && tier.order() == armour.tier() + 1) {
            return new Fit(remake(piece, armour.withTier(tier.order()), store), pieceSlot, List.of());
        }
        return null;
    }

    /** The piece with what it is made of changed, its durability baked again, and its wear and enchantments kept. */
    private static ItemStack remake(ItemStack piece, CarcassArmour armour, PartsData.Store store) {
        return CarcassArmourItem.make(piece.copyWithCount(1), armour, store);
    }

    /**
     * An organ cut out of a mob, as armour and minions take it ({@link Organs#of}); null for anything else, or an organ
     * with no mob on it (a player's own heart).
     */
    @Nullable
    public static CarcassArmour.Organ organ(ItemStack stack) {
        return Organs.of(stack, CarcassArmourItem.store());
    }

    /** A fitted organ as an item again, named and stamped as it was when cut out. */
    public static ItemStack organItem(CarcassArmour.Organ organ) {
        return Organs.stack(CarcassArmourItem.store(), organ.organ(), organ.entity(), organ.baby());
    }

    /** Whether Create's Mechanical Crafters are asking: they give back nothing a recipe returns. */
    public static boolean inCrafter(CraftingInput input) {
        return input instanceof com.simibubi.create.content.kinetics.crafter.MechanicalCraftingInput;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BBRecipes.CARCASS_ARMOUR_FITTING.get();
    }
}
