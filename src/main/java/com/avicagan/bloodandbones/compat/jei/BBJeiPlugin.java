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
 * JEI integration: a Butchery page per mob (what its carcass gives), and information pages for the tools,
 * machines and fluids.
 */
@JeiPlugin
public class BBJeiPlugin implements IModPlugin {
    private static final ResourceLocation ID = BloodAndBones.asResource("jei");

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    /** For the developer showcase, which opens the butchery page to photograph it. */
    public static mezz.jei.api.runtime.IJeiRuntime runtime;

    @Override
    public void registerCategories(mezz.jei.api.registration.IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ButcheryCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBItems.CLEAVER.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBItems.BLOOD_STEEL_CLEAVER.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBItems.FLENSING_KNIFE.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBBlocks.MANGLER.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBBlocks.DEGLOVER.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBBlocks.BUTCHER_TABLE.get()), ButcheryCategory.TYPE);
    }

    /** the Butchery pages JEI is showing, so they can be swapped when the server sends new tables */
    private static java.util.List<ButcheryCategory.Entry> shown = java.util.List.of();

    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** The tables can arrive after JEI has started: swap the old pages for new ones. */
    private static void refreshButchery() {
        if (runtime == null) {
            return;
        }
        if (!shown.isEmpty()) {
            runtime.getRecipeManager().hideRecipes(ButcheryCategory.TYPE, shown);
        }
        shown = ButcheryCategory.entries();
        if (!shown.isEmpty()) {
            runtime.getRecipeManager().addRecipes(ButcheryCategory.TYPE, shown);
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        shown = ButcheryCategory.entries();
        registration.addRecipes(ButcheryCategory.TYPE, shown);
        com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.onClientTables = BBJeiPlugin::refreshButchery;
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
        registration.addIngredientInfo(java.util.List.of(new net.minecraft.world.item.ItemStack(BBBlocks.MANGLER.get()), new net.minecraft.world.item.ItemStack(BBBlocks.GUILLOTINE.get()),
                        new net.minecraft.world.item.ItemStack(BBBlocks.BEHEADER.get()), new net.minecraft.world.item.ItemStack(BBBlocks.DEGLOVER.get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.machines.1"), Component.translatable("bloodandbones.jei.machines.2"),
                Component.translatable("bloodandbones.jei.machines.3"));
        registration.addIngredientInfo(new net.minecraft.world.item.ItemStack(com.avicagan.bloodandbones.registry.BBFluids.SOUL_BLOOD.getBucket().get()),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable("bloodandbones.jei.soul_blood.1"));
        registration.addIngredientInfo(java.util.List.of(new net.minecraft.world.item.ItemStack(BBBlocks.BUTCHER_HOOK.get()), new net.minecraft.world.item.ItemStack(BBBlocks.SPECIMEN_JAR.get()),
                        new net.minecraft.world.item.ItemStack(BBBlocks.BUTCHER_TABLE.get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.display.1"), Component.translatable("bloodandbones.jei.display.2"));
        registration.addIngredientInfo(java.util.List.of(new net.minecraft.world.item.ItemStack(BBBlocks.BLOODY_CASING.get()), new net.minecraft.world.item.ItemStack(BBBlocks.GUT_CHAIN.get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.decoration.1"), Component.translatable("bloodandbones.jei.decoration.2"));
        registration.addIngredientInfo(BBBlocks.SHACKLE_HOOK.get(),
                Component.translatable("bloodandbones.jei.shackle_hook.1"),
                Component.translatable("bloodandbones.jei.shackle_hook.2"));
    }
}
