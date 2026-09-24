package com.avicagan.bloodandbones.backtank;

import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBItems;
import com.avicagan.bloodandbones.registry.BBRecipes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Strapping a Fluid Backtank to a carcass chestplate, and taking it off again (docs/PARTS-AND-TRAITS.md
 * section 7.8). A backtank takes the chest slot, so without this carcass armour would unplug every prosthetic
 * that runs on the tank.
 * <ul>
 *     <li>A carcass chestplate and a backtank: the chestplate, carrying the tank's tier and its fluid.</li>
 *     <li>A chestplate with a tank strapped on, alone: the tank, fluid and all; the chestplate comes back.</li>
 * </ul>
 * Create's Mechanical Crafters give back nothing a recipe returns, so they strap tanks on but never take them off.
 */
public class BacktankStrapRecipe extends CustomRecipe {
    public BacktankStrapRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack chestplate = ItemStack.EMPTY;
        ItemStack tank = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(BBItems.CARCASS_CHESTPLATE.get()) && chestplate.isEmpty()) {
                chestplate = stack;
            } else if (stack.getItem() instanceof FluidBacktankItem && tank.isEmpty()) {
                tank = stack;
            } else {
                return false;
            }
        }
        if (chestplate.isEmpty() || com.avicagan.bloodandbones.parts.CarcassArmourItem.armour(chestplate) == null) {
            return false;
        }
        boolean strapped = chestplate.has(BBDataComponents.STRAPPED_TANK);
        return tank.isEmpty() ? strapped && !com.avicagan.bloodandbones.parts.CarcassArmourFittingRecipe.inCrafter(input) : !strapped;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack chestplate = ItemStack.EMPTY;
        ItemStack tank = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.is(BBItems.CARCASS_CHESTPLATE.get())) {
                chestplate = stack;
            } else if (stack.getItem() instanceof FluidBacktankItem) {
                tank = stack;
            }
        }
        if (chestplate.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return tank.isEmpty() ? FluidBacktankItem.unstrapped(chestplate) : FluidBacktankItem.strap(chestplate, tank);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> out = super.getRemainingItems(input);
        int items = 0;
        int at = -1;
        for (int i = 0; i < input.size(); i++) {
            if (!input.getItem(i).isEmpty()) {
                items++;
                at = i;
            }
        }
        if (items == 1 && input.getItem(at).has(BBDataComponents.STRAPPED_TANK)) {
            // taking the tank off: the chestplate stays where it lay, without it
            out.set(at, FluidBacktankItem.withoutTank(input.getItem(at)));
        }
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BBRecipes.BACKTANK_STRAP.get();
    }
}
