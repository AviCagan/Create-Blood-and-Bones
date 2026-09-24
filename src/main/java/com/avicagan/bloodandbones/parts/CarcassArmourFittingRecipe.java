package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.SeveredLimbItem;
import com.avicagan.bloodandbones.registry.BBRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fitting a piece of carcass armour (docs/PARTS-AND-TRAITS.md section 7.3): the piece and one kind of thing,
 * anywhere in the grid.
 * <ul>
 *     <li>Hides of one mob, as many as the piece needs (one for a helmet or boots, two leggings, three a
 *     chestplate): its covering, with that mob's hide traits. They take the place of any hide it had, which
 *     comes back.</li>
 *     <li>An organ cut out of a mob, into the piece that takes it: eyes a helmet, a heart or lungs a
 *     chestplate, a stomach a chestplate or leggings. It takes the place of any organ it had, which comes back.</li>
 *     <li>The next tier's ingot or gem: blood steel, then blood diamond, then soul netherite.</li>
 * </ul>
 * Create's Mechanical Crafters call {@link #assemble} as a crafting grid does, so they fit hides, organs and
 * tiers too. They give back only what an item leaves behind by itself, never what a recipe gives back, so
 * in them a fitting that would take something out is refused rather than losing it: that swap is by hand.
 */
public class CarcassArmourFittingRecipe extends CustomRecipe {
    /** Which pieces take each organ (until organs have files of their own, with their "armour_pieces"). */
    private static final Map<BodyPart.Kind, Set<String>> ORGAN_PIECES = Map.of(
            BodyPart.Kind.EYE, Set.of("helmet"),
            BodyPart.Kind.HEART, Set.of("chestplate"),
            BodyPart.Kind.LUNGS, Set.of("chestplate"),
            BodyPart.Kind.STOMACH, Set.of("chestplate", "leggings"));

    public CarcassArmourFittingRecipe(CraftingBookCategory category) {
        super(category);
    }

    /** What fitting makes: the piece as it comes out, where the piece lay, and what comes back out of it. */
    public record Fit(ItemStack result, int pieceSlot, ItemStack giveBack) {
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
            // the piece is used up, so what came out of it goes back where it lay
            out.set(fit.pieceSlot(), fit.giveBack());
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
            // all the same hide, from one mob, as many as the piece needs
            for (ItemStack other : modifiers) {
                if (!ItemStack.isSameItemSameComponents(first, other)) {
                    return null;
                }
            }
            if (modifiers.size() != armour.hidesNeeded()) {
                return null;
            }
            ItemStack giveBack = armour.hide().map(old -> Hides.giveBack(old, armour.hidesNeeded())).orElse(ItemStack.EMPTY);
            return new Fit(remake(piece, armour.withHide(Optional.of(hide)), store), pieceSlot, giveBack);
        }
        if (modifiers.size() != 1) {
            return null;
        }
        CarcassArmour.Organ organ = organ(first);
        if (organ != null) {
            if (!ORGAN_PIECES.getOrDefault(((SeveredLimbItem) first.getItem()).kind(), Set.of()).contains(armour.piece())) {
                return null;
            }
            ItemStack giveBack = armour.organ().map(CarcassArmourFittingRecipe::organItem).orElse(ItemStack.EMPTY);
            return new Fit(remake(piece, armour.withOrgan(Optional.of(organ)), store), pieceSlot, giveBack);
        }
        ArmourTier tier = store.tierFor(first.getItem());
        if (tier != null && tier.order() == armour.tier() + 1) {
            return new Fit(remake(piece, armour.withTier(tier.order()), store), pieceSlot, ItemStack.EMPTY);
        }
        return null;
    }

    /** The piece with what it is made of changed, its durability baked again, and its wear and enchantments kept. */
    private static ItemStack remake(ItemStack piece, CarcassArmour armour, PartsData.Store store) {
        return CarcassArmourItem.make(piece.copyWithCount(1), armour, store);
    }

    /** An organ cut out of a mob, as armour takes it; null for anything else, or an organ with no mob stamped on it (a player's own). */
    @Nullable
    public static CarcassArmour.Organ organ(ItemStack stack) {
        Source source = SeveredLimbItem.source(stack);
        if (source == null || !ORGAN_PIECES.containsKey(((SeveredLimbItem) stack.getItem()).kind())) {
            return null;
        }
        return new CarcassArmour.Organ(BuiltInRegistries.ITEM.getKey(stack.getItem()), source.entity(), source.baby());
    }

    /** A fitted organ as an item again, named and stamped as it was when cut out. */
    public static ItemStack organItem(CarcassArmour.Organ organ) {
        return BuiltInRegistries.ITEM.getOptional(organ.organ()).filter(item -> item instanceof SeveredLimbItem)
                .map(item -> ((SeveredLimbItem) item).of(organ.entity(), organ.baby())).orElse(ItemStack.EMPTY);
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
