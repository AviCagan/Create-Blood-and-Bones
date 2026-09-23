package com.avicagan.bloodandbones.compat.jei;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI integration. Machines and their recipe categories arrive with the machines; until then every
 * item gets an information page. All text is placeholder until the real descriptions are written.
 */
@JeiPlugin
public class BBJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = BloodAndBones.asResource("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(BBItems.MEAT_HOOK.get(),
                Component.translatable("bloodandbones.jei.meat_hook.1"),
                Component.translatable("bloodandbones.jei.meat_hook.2"));
        registration.addIngredientInfo(BBItems.FLENSING_KNIFE.get(),
                Component.translatable("bloodandbones.jei.flensing_knife.1"),
                Component.translatable("bloodandbones.jei.flensing_knife.2"));
        registration.addIngredientInfo(java.util.List.of(new net.minecraft.world.item.ItemStack(BBItems.RAW_MEAT.get()), new net.minecraft.world.item.ItemStack(BBItems.OFFAL.get()),
                        new net.minecraft.world.item.ItemStack(BBItems.ANIMAL_FAT.get()), new net.minecraft.world.item.ItemStack(BBItems.RAW_HIDE.get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.butchery.1"),
                Component.translatable("bloodandbones.jei.butchery.2"));
        registration.addIngredientInfo(BBItems.CLEAVER.get(),
                Component.translatable("bloodandbones.jei.cleaver.1"),
                Component.translatable("bloodandbones.jei.cleaver.2"));
        registration.addIngredientInfo(java.util.List.of(new net.minecraft.world.item.ItemStack(BBBlocks.BLEEDING_RACK.get()),
                        new net.minecraft.world.item.ItemStack(com.avicagan.bloodandbones.registry.BBFluids.BLOOD.getBucket().get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.bleeding_rack.1"), Component.translatable("bloodandbones.jei.bleeding_rack.2"));
        registration.addIngredientInfo(BBBlocks.SHACKLE_HOOK.get(),
                Component.translatable("bloodandbones.jei.shackle_hook.1"),
                Component.translatable("bloodandbones.jei.shackle_hook.2"));
    }
}
