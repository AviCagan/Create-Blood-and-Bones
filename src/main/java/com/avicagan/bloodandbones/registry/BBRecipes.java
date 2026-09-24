package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own recipe kinds. */
public final class BBRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, BloodAndBones.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, com.avicagan.bloodandbones.parts.CarcassArmourRecipe.Serializer> CARCASS_ARMOUR =
            SERIALIZERS.register("carcass_armour", com.avicagan.bloodandbones.parts.CarcassArmourRecipe.Serializer::new);

    /** A piece of carcass armour and a hide, an organ or the next tier's ingot. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<com.avicagan.bloodandbones.parts.CarcassArmourFittingRecipe>> CARCASS_ARMOUR_FITTING =
            SERIALIZERS.register("carcass_armour_fitting", () -> new SimpleCraftingRecipeSerializer<>(com.avicagan.bloodandbones.parts.CarcassArmourFittingRecipe::new));

    /** A Fluid Backtank strapped to a carcass chestplate, or taken off it. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<com.avicagan.bloodandbones.backtank.BacktankStrapRecipe>> BACKTANK_STRAP =
            SERIALIZERS.register("backtank_strap", () -> new SimpleCraftingRecipeSerializer<>(com.avicagan.bloodandbones.backtank.BacktankStrapRecipe::new));

    private BBRecipes() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
