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
 * JEI integration: a Butchery page per mob (what its carcass gives), a Body Parts page per mob (what its parts, hide and
 * organs do), how each organ fits carcass armour, and information pages for the tools, machines and fluids.
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
        registration.addRecipeCategories(new BodyPartsCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    /** Glands are told apart by their organ (a powder sac is not a rumen), not by whose they are. */
    @Override
    public void registerItemSubtypes(mezz.jei.api.registration.ISubtypeRegistration registration) {
        registration.registerSubtypeInterpreter(BBItems.GLAND.get(), new mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter<net.minecraft.world.item.ItemStack>() {
            @Override
            public Object getSubtypeData(net.minecraft.world.item.ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext context) {
                return stack.get(com.avicagan.bloodandbones.registry.BBDataComponents.ORGAN.get());
            }

            @Override
            public String getLegacyStringSubtypeInfo(net.minecraft.world.item.ItemStack stack, mezz.jei.api.ingredients.subtypes.UidContext context) {
                ResourceLocation organ = stack.get(com.avicagan.bloodandbones.registry.BBDataComponents.ORGAN.get());
                return organ == null ? "" : organ.toString();
            }
        });
    }

    @Override
    public void registerRecipeCatalysts(mezz.jei.api.registration.IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBItems.CLEAVER.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBItems.BLOOD_STEEL_CLEAVER.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBItems.FLENSING_KNIFE.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBBlocks.MANGLER.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBBlocks.DEGLOVER.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBBlocks.BUTCHER_TABLE.get()), ButcheryCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBItems.SURGICAL_RIG.get()), BodyPartsCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBItems.ASSEMBLY_FRAME.get()), BodyPartsCategory.TYPE);
        registration.addRecipeCatalyst(new net.minecraft.world.item.ItemStack(BBBlocks.MANGLER.get()), BodyPartsCategory.TYPE);
    }

    /** the Butchery pages JEI is showing, so they can be swapped when the server sends new tables */
    private static java.util.List<ButcheryCategory.Entry> shown = java.util.List.of();
    /** the Body Parts pages likewise: their hides come from the tables */
    private static java.util.List<BodyPartsCategory.Entry> shownParts = java.util.List.of();

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
        if (!shownParts.isEmpty()) {
            runtime.getRecipeManager().hideRecipes(BodyPartsCategory.TYPE, shownParts);
        }
        shownParts = BodyPartsCategory.entries();
        if (!shownParts.isEmpty()) {
            runtime.getRecipeManager().addRecipes(BodyPartsCategory.TYPE, shownParts);
        }
    }

    /**
     * Fitting an organ is a special recipe JEI cannot list, so each is shown as a crafting recipe of its own: the piece of
     * carcass armour its file allows and the organ, as the first mob holding it gives it, make the piece with it fitted.
     */
    private static java.util.List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> organFittings() {
        com.avicagan.bloodandbones.parts.PartsData.Store store = com.avicagan.bloodandbones.parts.PartsData.CLIENT;
        java.util.List<net.minecraft.world.item.crafting.RecipeHolder<net.minecraft.world.item.crafting.CraftingRecipe>> out = new java.util.ArrayList<>();
        for (ResourceLocation organ : store.organs().keySet()) {
            java.util.List<ResourceLocation> mobs = com.avicagan.bloodandbones.parts.Organs.mobsWith(store, organ);
            if (mobs.isEmpty()) {
                continue;
            }
            ResourceLocation mob = mobs.get(0);
            net.minecraft.world.item.ItemStack item = com.avicagan.bloodandbones.parts.Organs.stack(store, organ, mob, false);
            for (String piece : com.avicagan.bloodandbones.parts.Organs.kind(store, organ).armourPieces()) {
                net.minecraft.world.item.Item made = switch (piece) {
                    case "helmet" -> BBItems.CARCASS_HELMET.get();
                    case "chestplate" -> BBItems.CARCASS_CHESTPLATE.get();
                    case "leggings" -> BBItems.CARCASS_LEGGINGS.get();
                    default -> BBItems.CARCASS_BOOTS.get();
                };
                com.avicagan.bloodandbones.parts.CarcassArmour armour = com.avicagan.bloodandbones.parts.CarcassArmour.of(piece, mob, false);
                net.minecraft.world.item.ItemStack blank = com.avicagan.bloodandbones.parts.CarcassArmourItem.make(new net.minecraft.world.item.ItemStack(made), armour, store);
                net.minecraft.world.item.ItemStack fitted = com.avicagan.bloodandbones.parts.CarcassArmourItem.make(new net.minecraft.world.item.ItemStack(made),
                        armour.withOrgan(java.util.Optional.of(new com.avicagan.bloodandbones.parts.CarcassArmour.Organ(organ, mob, false))), store);
                out.add(new net.minecraft.world.item.crafting.RecipeHolder<>(BloodAndBones.asResource("jei/organ_fitting/" + organ.getPath() + "/" + piece),
                        new net.minecraft.world.item.crafting.ShapelessRecipe("organ_fitting", net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT, fitted,
                                net.minecraft.core.NonNullList.of(net.minecraft.world.item.crafting.Ingredient.EMPTY,
                                        net.minecraft.world.item.crafting.Ingredient.of(blank), net.minecraft.world.item.crafting.Ingredient.of(item)))));
            }
        }
        return out;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        shown = ButcheryCategory.entries();
        registration.addRecipes(ButcheryCategory.TYPE, shown);
        shownParts = BodyPartsCategory.entries();
        registration.addRecipes(BodyPartsCategory.TYPE, shownParts);
        registration.addRecipes(mezz.jei.api.constants.RecipeTypes.CRAFTING, organFittings());
        java.util.List<net.minecraft.world.item.ItemStack> glands = com.avicagan.bloodandbones.parts.PartsData.CLIENT.organs().keySet().stream()
                .map(organ -> com.avicagan.bloodandbones.parts.Organs.example(com.avicagan.bloodandbones.parts.PartsData.CLIENT, organ))
                .filter(stack -> stack.is(BBItems.GLAND.get())).toList();
        if (!glands.isEmpty()) {
            registration.addIngredientInfo(glands, mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                    Component.translatable("bloodandbones.jei.gland.1"), Component.translatable("bloodandbones.jei.gland.2"));
        }
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
        registration.addIngredientInfo(java.util.List.of(new net.minecraft.world.item.ItemStack(com.avicagan.bloodandbones.registry.BBFluids.SOUL_BLOOD.getBucket().get()),
                        new net.minecraft.world.item.ItemStack(BBItems.CONGEALED_BLOOD.get()), new net.minecraft.world.item.ItemStack(BBItems.SOUL_CLOT.get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, Component.translatable("bloodandbones.jei.soul_blood.1"), Component.translatable("bloodandbones.jei.soul_blood.2"));
        registration.addIngredientInfo(java.util.List.of(new net.minecraft.world.item.ItemStack(BBBlocks.BUTCHER_HOOK.get()), new net.minecraft.world.item.ItemStack(BBBlocks.SPECIMEN_JAR.get()),
                        new net.minecraft.world.item.ItemStack(BBBlocks.BUTCHER_TABLE.get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.display.1"), Component.translatable("bloodandbones.jei.display.2"));
        registration.addIngredientInfo(java.util.stream.Stream.concat(java.util.stream.Stream.of(BBBlocks.BLOODY_CASING, BBBlocks.BLOODY_BRASS_CASING,
                                BBBlocks.BLOODY_COPPER_CASING, BBBlocks.BLOODY_RAILWAY_CASING, BBBlocks.GUT_CHAIN, BBBlocks.RIBCAGE_ARCH, BBBlocks.BONE_PILE),
                                BBBlocks.stainedPalette().stream())
                        .map(e -> new net.minecraft.world.item.ItemStack(e.get())).toList(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.decoration.1"), Component.translatable("bloodandbones.jei.decoration.2"),
                Component.translatable("bloodandbones.jei.decoration.3"));
        registration.addIngredientInfo(java.util.List.of(new net.minecraft.world.item.ItemStack(BBBlocks.STEEL_TABLE.get()), new net.minecraft.world.item.ItemStack(BBBlocks.STEEL_RACK.get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.morgue.1"), Component.translatable("bloodandbones.jei.morgue.2"));
        registration.addIngredientInfo(java.util.List.of(new net.minecraft.world.item.ItemStack(BBBlocks.SURGERY_TABLE.get()), new net.minecraft.world.item.ItemStack(BBItems.PEG_LEG.get()),
                        new net.minecraft.world.item.ItemStack(BBItems.HOOK_HAND.get()), new net.minecraft.world.item.ItemStack(BBItems.SEVERED_ARM.get()),
                        new net.minecraft.world.item.ItemStack(BBItems.SEVERED_LEG.get())),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.surgery.1"), Component.translatable("bloodandbones.jei.surgery.2"));
        registration.addIngredientInfo(java.util.stream.Stream.of(BBItems.FLESH_ARM, BBItems.SINEW_LEG, BBItems.HYDRAULIC_ARM, BBItems.PISTON_LEG, BBItems.VENT_ARM,
                        BBItems.PORT_ARM, BBItems.OPTIC_EYE, BBItems.PUMP_HEART, BBItems.BELLOWS_LUNGS, BBItems.FURNACE_STOMACH)
                        .map(e -> new net.minecraft.world.item.ItemStack(e.get())).toList(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.implants.1"), Component.translatable("bloodandbones.jei.implants.2"),
                Component.translatable("bloodandbones.jei.implants.3"));
        registration.addIngredientInfo(new net.minecraft.world.item.ItemStack(BBBlocks.SURGERY_TABLE.get()), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.minions.1"), Component.translatable("bloodandbones.jei.minions.2"));
        registration.addIngredientInfo(BBItems.BACKTANKS.values().stream().map(e -> new net.minecraft.world.item.ItemStack(e.get())).toList(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.backtank.1"), Component.translatable("bloodandbones.jei.backtank.2"),
                Component.translatable("bloodandbones.jei.backtank.3"));
        // fitting and strapping are special recipes, which JEI does not list, so they are told here
        registration.addIngredientInfo(java.util.stream.Stream.of(BBItems.CARCASS_HELMET, BBItems.CARCASS_CHESTPLATE, BBItems.CARCASS_LEGGINGS, BBItems.CARCASS_BOOTS)
                        .map(e -> new net.minecraft.world.item.ItemStack(e.get())).toList(),
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                Component.translatable("bloodandbones.jei.carcass_armour.1"), Component.translatable("bloodandbones.jei.carcass_armour.2"),
                Component.translatable("bloodandbones.jei.carcass_armour.3"));
        registration.addIngredientInfo(BBBlocks.SHACKLE_HOOK.get(),
                Component.translatable("bloodandbones.jei.shackle_hook.1"),
                Component.translatable("bloodandbones.jei.shackle_hook.2"));
    }
}
