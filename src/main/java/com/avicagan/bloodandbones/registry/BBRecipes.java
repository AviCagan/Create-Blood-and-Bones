package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's own recipe kinds. */
public final class BBRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, BloodAndBones.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, com.avicagan.bloodandbones.parts.CarcassArmourRecipe.Serializer> CARCASS_ARMOUR =
            SERIALIZERS.register("carcass_armour", com.avicagan.bloodandbones.parts.CarcassArmourRecipe.Serializer::new);

    private BBRecipes() {
    }

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
