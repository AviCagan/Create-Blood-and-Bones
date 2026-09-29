package com.avicagan.bloodandbones.datagen;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.cyber.Module;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.fan.processing.SplashingRecipe;
import com.simibubi.create.content.kinetics.fan.processing.HauntingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeBuilder;
import com.tterrag.registrate.providers.ProviderType;
import com.tterrag.registrate.providers.RegistrateRecipeProvider;
import com.tterrag.registrate.util.DataIngredient;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.Tags;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.world.item.crafting.CraftingBookCategory.BUILDING;
import static net.minecraft.world.item.crafting.CraftingBookCategory.EQUIPMENT;
import static net.minecraft.world.item.crafting.CraftingBookCategory.MISC;

/**
 * Every recipe of the mod, written out by datagen into src/generated (docs/ARCHITECTURE-PROPOSAL.md section 8:
 * "All recipe JSON is produced by datagen through the builders, never hand-written"). Create's processing
 * recipes go through Create's own builders, so they come out in whatever format Create reads; crafting
 * recipes are built as the game's own recipe objects and written with their own codecs. Processing recipes
 * land in a folder named for their type, as Create's do ({@code filling/blood_steel_ingot}).
 */
public final class BBRecipeGen {
    private BBRecipeGen() {
    }

    /** Called from the mod's constructor: Registrate runs it when data is generated. */
    public static void register() {
        BloodAndBones.REGISTRATE.addDataGenerator(ProviderType.RECIPE, BBRecipeGen::generate);
    }

    private static void generate(RegistrateRecipeProvider out) {
        crafting(out);
        processing(out);
        palette(out);
    }

    // ---------------------------------------------------------------- crafting

    private static void crafting(RecipeOutput out) {
        special(out, "backtank_strap", com.avicagan.bloodandbones.backtank.BacktankStrapRecipe::new);
        special(out, "carcass_armour_fitting", com.avicagan.bloodandbones.parts.CarcassArmourFittingRecipe::new);
        shaped(out, "analytical_lens", EQUIPMENT, BBItems.module(Module.ANALYTICAL_LENS), 1, List.of(" G ", "PMP", " E "),
                'G', AllItems.GOGGLES, 'P', plates("brass"), 'M', AllItems.PRECISION_MECHANISM, 'E', AllItems.ELECTRON_TUBE);
        shaped(out, "assembly_frame", BUILDING, BBItems.ASSEMBLY_FRAME, 1, List.of("ICI", "L L", "ICI"),
                'I', Tags.Items.INGOTS_IRON, 'C', Items.CHAIN, 'L', Items.LEATHER);
        shaped(out, "backtank_port", BUILDING, BBBlocks.BACKTANK_PORT, 1, List.of("CUC", "CTC"),
                'C', plates("copper"), 'U', AllBlocks.MECHANICAL_PUMP, 'T', AllBlocks.FLUID_PIPE);
        shaped(out, "barometric_vent", EQUIPMENT, BBItems.module(Module.BAROMETRIC_VENT), 1, List.of("PBP", "PMP", " T "),
                'P', plates("brass"), 'B', AllItems.PROPELLER, 'M', AllItems.PRECISION_MECHANISM, 'T', AllBlocks.FLUID_PIPE);
        shaped(out, "beheader", MISC, BBBlocks.BEHEADER, 1, List.of(" W ", "ICI", " S "),
                'W', AllBlocks.MECHANICAL_SAW, 'I', Tags.Items.INGOTS_IRON, 'C', AllBlocks.ANDESITE_CASING, 'S', AllBlocks.SHAFT);
        shaped(out, "bellows_lungs", EQUIPMENT, BBItems.BELLOWS_LUNGS, 1, List.of("PFP", "PMP", " P "),
                'P', plates("brass"), 'F', AllBlocks.ENCASED_FAN, 'M', AllItems.PRECISION_MECHANISM);
        shaped(out, "bleeding_rack", MISC, BBBlocks.BLEEDING_RACK, 1, List.of("SBS", "SSS"),
                'S', plates("copper"), 'B', Items.IRON_BARS);
        shaped(out, "blood_diamond_fluid_backtank", EQUIPMENT, BBItems.backtank(BacktankTier.BLOOD_DIAMOND), 1, List.of("MLM", "MTM", " M "),
                'M', BBItems.BLOOD_DIAMOND, 'L', Items.LEATHER, 'T', AllBlocks.FLUID_TANK);
        shaped(out, "blood_steel_block", MISC, BBBlocks.BLOOD_STEEL_BLOCK, 1, List.of("III", "III", "III"),
                'I', BBItems.BLOOD_STEEL_INGOT);
        shaped(out, "blood_steel_cleaver", EQUIPMENT, BBItems.BLOOD_STEEL_CLEAVER, 1, List.of("II", "II", "S "),
                'I', BBItems.BLOOD_STEEL_INGOT, 'S', Tags.Items.RODS_WOODEN);
        shaped(out, "blood_steel_fluid_backtank", EQUIPMENT, BBItems.backtank(BacktankTier.BLOOD_STEEL), 1, List.of("MLM", "MTM", " M "),
                'M', BBItems.BLOOD_STEEL_INGOT, 'L', Items.LEATHER, 'T', AllBlocks.FLUID_TANK);
        shapeless(out, "blood_steel_ingot_from_block", MISC, BBItems.BLOOD_STEEL_INGOT, 9, BBBlocks.BLOOD_STEEL_BLOCK);
        shaped(out, "blood_steel_ingot_from_nuggets", MISC, BBItems.BLOOD_STEEL_INGOT, 1, List.of("NNN", "NNN", "NNN"),
                'N', BBItems.BLOOD_STEEL_NUGGET);
        shapeless(out, "blood_steel_nugget", MISC, BBItems.BLOOD_STEEL_NUGGET, 9, BBItems.BLOOD_STEEL_INGOT);
        shaped(out, "blood_trough", MISC, BBBlocks.BLOOD_TROUGH, 1, List.of("P P", "IBI", "PPP"),
                'P', ItemTags.PLANKS, 'I', Tags.Items.NUGGETS_IRON, 'B', Items.BUCKET);
        shaped(out, "bone_pile", BUILDING, BBBlocks.BONE_PILE, 2, List.of("BB", "BB"),
                'B', Items.BONE);
        shaped(out, "brass_sheathing", MISC, BBItems.BRASS_SHEATHING, 1, List.of("BBB", "BAB", "BBB"),
                'B', plates("brass"), 'A', AllItems.ANDESITE_ALLOY);
        shaped(out, "butcher_hook", MISC, BBBlocks.BUTCHER_HOOK, 2, List.of("I", "N"),
                'I', Tags.Items.INGOTS_IRON, 'N', Tags.Items.NUGGETS_IRON);
        shaped(out, "butcher_table", BUILDING, BBBlocks.BUTCHER_TABLE, 1, List.of("III", "B B"),
                'I', Tags.Items.INGOTS_IRON, 'B', Items.IRON_BARS);
        armour(out, "carcass_boots", "boots", BBItems.CARCASS_BOOTS, List.of("L L", "L L"),
                'L', BBItems.SCRAPS);
        armour(out, "carcass_chestplate", "chestplate", BBItems.CARCASS_CHESTPLATE, List.of("S S", "TTT", "TTT"),
                'S', BBItems.SCRAPS, 'T', BBItems.SCRAPS);
        armour(out, "carcass_helmet", "helmet", BBItems.CARCASS_HELMET, List.of("HHH", "H H"),
                'H', BBItems.SCRAPS);
        armour(out, "carcass_leggings", "leggings", BBItems.CARCASS_LEGGINGS, List.of("PPP", "L L", "L L"),
                'L', BBItems.SCRAPS, 'P', BBItems.SCRAPS);
        shaped(out, "charging_cradle", MISC, BBBlocks.CHARGING_CRADLE, 1, List.of("B B", "PCP", " S "),
                'B', plates("brass"), 'P', AllItems.PRECISION_MECHANISM, 'C', AllBlocks.BRASS_CASING, 'S', AllBlocks.SHAFT);
        shaped(out, "cleaver", EQUIPMENT, BBItems.CLEAVER, 1, List.of("II", "II", "S "),
                'I', Tags.Items.INGOTS_IRON, 'S', Tags.Items.RODS_WOODEN);
        shaped(out, "copper_fluid_backtank", EQUIPMENT, BBItems.backtank(BacktankTier.COPPER), 1, List.of("MLM", "MTM", " M "),
                'M', Tags.Items.INGOTS_COPPER, 'L', Items.LEATHER, 'T', AllBlocks.FLUID_TANK);
        shaped(out, "crude_heart", EQUIPMENT, BBItems.CRUDE_HEART, 1, List.of("LIL", "IBI", "LIL"),
                'L', Items.LEATHER, 'I', Items.IRON_NUGGET, 'B', Items.BONE);
        shaped(out, "crude_lungs", EQUIPMENT, BBItems.CRUDE_LUNGS, 1, List.of("L L", "LBL", "N N"),
                'L', Items.LEATHER, 'B', Items.BONE, 'N', Items.IRON_NUGGET);
        shaped(out, "crude_stomach", EQUIPMENT, BBItems.CRUDE_STOMACH, 1, List.of("NLN", "LBL", "NLN"),
                'L', Items.LEATHER, 'B', Items.BONE, 'N', Items.IRON_NUGGET);
        shaped(out, "deglover", MISC, BBBlocks.DEGLOVER, 1, List.of(" K ", "ICI", " S "),
                'K', BBItems.FLENSING_KNIFE, 'I', Tags.Items.INGOTS_IRON, 'C', AllBlocks.ANDESITE_CASING, 'S', AllBlocks.SHAFT);
        shaped(out, "diamond_fluid_backtank", EQUIPMENT, BBItems.backtank(BacktankTier.DIAMOND), 1, List.of("MLM", "MTM", " M "),
                'M', Tags.Items.GEMS_DIAMOND, 'L', Items.LEATHER, 'T', AllBlocks.FLUID_TANK);
        shaped(out, "empty_soul_canister", MISC, BBItems.EMPTY_SOUL_CANISTER, 2, List.of(" B ", "BGB", " B "),
                'B', plates("brass"), 'G', Tags.Items.GLASS_BLOCKS);
        shaped(out, "flensing_knife", EQUIPMENT, BBItems.FLENSING_KNIFE, 1, List.of(" I", "S "),
                'I', Tags.Items.INGOTS_IRON, 'S', Tags.Items.RODS_WOODEN);
        shaped(out, "flesh_arm", EQUIPMENT, BBItems.FLESH_ARM, 1, List.of("MOM", "MBM", " M "),
                'M', BBItems.RAW_MEAT, 'O', BBItems.OFFAL, 'B', Items.BONE);
        shaped(out, "furnace_stomach", EQUIPMENT, BBItems.FURNACE_STOMACH, 1, List.of("PBP", "PMP", " P "),
                'P', plates("brass"), 'B', Items.BLAST_FURNACE, 'M', AllItems.PRECISION_MECHANISM);
        shaped(out, "glass_eye", EQUIPMENT, BBItems.GLASS_EYE, 1, List.of(" G ", "NBN"),
                'G', Tags.Items.GLASS_BLOCKS, 'N', Items.IRON_NUGGET, 'B', Items.BONE_MEAL);
        shaped(out, "gold_fluid_backtank", EQUIPMENT, BBItems.backtank(BacktankTier.GOLD), 1, List.of("MLM", "MTM", " M "),
                'M', Tags.Items.INGOTS_GOLD, 'L', Items.LEATHER, 'T', AllBlocks.FLUID_TANK);
        shaped(out, "grappling_spool", EQUIPMENT, BBItems.module(Module.GRAPPLING_SPOOL), 1, List.of(" H ", "PMP", " C "),
                'H', BBItems.MEAT_HOOK, 'P', plates("brass"), 'M', AllItems.PRECISION_MECHANISM, 'C', Items.CHAIN);
        shaped(out, "guillotine", MISC, BBBlocks.GUILLOTINE, 1, List.of("IBI", "ICI", " S "),
                'B', BBItems.CLEAVER, 'I', Tags.Items.INGOTS_IRON, 'C', AllBlocks.ANDESITE_CASING, 'S', AllBlocks.SHAFT);
        shaped(out, "gut_chain", BUILDING, BBBlocks.GUT_CHAIN, 3, List.of("O", "O", "O"),
                'O', BBItems.OFFAL);
        shaped(out, "gyroscopic_stabilizer", EQUIPMENT, BBItems.module(Module.GYROSCOPIC_STABILIZER), 1, List.of("PFP", "PMP"),
                'P', plates("brass"), 'F', AllBlocks.FLYWHEEL, 'M', AllItems.PRECISION_MECHANISM);
        shaped(out, "hook_hand", EQUIPMENT, BBItems.HOOK_HAND, 1, List.of("NI", "BL"),
                'N', Items.IRON_NUGGET, 'I', Tags.Items.INGOTS_IRON, 'B', Items.BONE, 'L', Items.LEATHER);
        shaped(out, "hydraulic_arm", EQUIPMENT, BBItems.HYDRAULIC_ARM, 1, List.of("PSP", "PMP", " P "),
                'P', plates("brass"), 'S', BBItems.BLOOD_STEEL_INGOT, 'M', AllItems.PRECISION_MECHANISM);
        shaped(out, "iron_fluid_backtank", EQUIPMENT, BBItems.backtank(BacktankTier.IRON), 1, List.of("MLM", "MTM", " M "),
                'M', Tags.Items.INGOTS_IRON, 'L', Items.LEATHER, 'T', AllBlocks.FLUID_TANK);
        shaped(out, "magnet_coil", EQUIPMENT, BBItems.module(Module.MAGNET_COIL), 1, List.of("CRC", "CMC", "CRC"),
                'C', plates("copper"), 'R', Items.REDSTONE, 'M', AllItems.PRECISION_MECHANISM);
        shaped(out, "mangler", MISC, BBBlocks.MANGLER, 1, List.of("TTT", "ICI", " S "),
                'T', Items.IRON_BARS, 'I', Tags.Items.INGOTS_IRON, 'C', AllBlocks.ANDESITE_CASING, 'S', AllBlocks.SHAFT);
        shaped(out, "meat_hook", EQUIPMENT, BBItems.MEAT_HOOK, 1, List.of(" N", "N ", "S "),
                'N', Tags.Items.INGOTS_IRON, 'S', Tags.Items.RODS_WOODEN);
        shaped(out, "optic_eye", EQUIPMENT, BBItems.OPTIC_EYE, 1, List.of(" G ", "GEG", " G "),
                'G', Items.RED_STAINED_GLASS_PANE, 'E', AllItems.ELECTRON_TUBE);
        shaped(out, "peg_leg", EQUIPMENT, BBItems.PEG_LEG, 1, List.of("L", "I", "B"),
                'L', Items.LEATHER, 'I', Tags.Items.INGOTS_IRON, 'B', Items.BONE);
        shaped(out, "piston_leg", EQUIPMENT, BBItems.PISTON_LEG, 1, List.of("PSP", "PMP", "P P"),
                'P', plates("brass"), 'S', BBItems.BLOOD_STEEL_INGOT, 'M', AllBlocks.MECHANICAL_PISTON);
        shaped(out, "piston_ram", EQUIPMENT, BBItems.module(Module.PISTON_RAM), 1, List.of("PKP", "PMP"),
                'P', plates("brass"), 'K', AllBlocks.MECHANICAL_PISTON, 'M', AllItems.PRECISION_MECHANISM);
        shaped(out, "port_arm", EQUIPMENT, BBItems.PORT_ARM, 1, List.of("PTP", "PMP", " P "),
                'P', plates("brass"), 'T', AllBlocks.FLUID_PIPE, 'M', AllItems.PRECISION_MECHANISM);
        shaped(out, "pump_heart", EQUIPMENT, BBItems.PUMP_HEART, 1, List.of("PUP", "PMP", " P "),
                'P', plates("brass"), 'U', AllBlocks.MECHANICAL_PUMP, 'M', AllItems.PRECISION_MECHANISM);
        shaped(out, "ribcage_arch", BUILDING, BBBlocks.RIBCAGE_ARCH, 2, List.of(" B", "B ", "B "),
                'B', Items.BONE);
        shaped(out, "rotational_coupler", EQUIPMENT, BBItems.module(Module.ROTATIONAL_COUPLER), 1, List.of("PSP", "PMP", " G "),
                'P', plates("brass"), 'S', AllBlocks.SHAFT, 'M', AllItems.PRECISION_MECHANISM, 'G', AllBlocks.COGWHEEL);
        shaped(out, "shackle_hook", MISC, BBBlocks.SHACKLE_HOOK, 1, List.of("C", "H"),
                'C', Items.CHAIN, 'H', BBItems.MEAT_HOOK);
        shaped(out, "sinew_leg", EQUIPMENT, BBItems.SINEW_LEG, 1, List.of("MHM", "MBM", "M M"),
                'M', BBItems.RAW_MEAT, 'H', BBItems.RAW_HIDE, 'B', Items.BONE);
        smithing(out, "soul_netherite_fluid_backtank", Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE, BBItems.backtank(BacktankTier.BLOOD_DIAMOND), BBItems.SOUL_NETHERITE_INGOT, BBItems.backtank(BacktankTier.SOUL_NETHERITE));
        shaped(out, "specimen_jar", MISC, BBBlocks.SPECIMEN_JAR, 1, List.of(" C ", "G G", "GGG"),
                'C', plates("copper"), 'G', Tags.Items.GLASS_BLOCKS);
        shaped(out, "spit_roast", MISC, BBBlocks.SPIT_ROAST, 1, List.of("LSL"),
                'L', ItemTags.LOGS, 'S', AllBlocks.SHAFT);
        shaped(out, "steel_rack", BUILDING, BBBlocks.STEEL_RACK, 1, List.of("BPB", "B B", "BPB"),
                'P', plates("iron"), 'B', Items.IRON_BARS);
        shaped(out, "steel_table", BUILDING, BBBlocks.STEEL_TABLE, 2, List.of("PPP", "I I"),
                'P', plates("iron"), 'I', Tags.Items.INGOTS_IRON);
        shaped(out, "surgery_table", BUILDING, BBBlocks.SURGERY_TABLE, 1, List.of("WWW", "IAI", "I I"),
                'W', Items.WHITE_WOOL, 'I', Tags.Items.INGOTS_IRON, 'A', AllItems.ANDESITE_ALLOY);
        shaped(out, "surgical_rig", BUILDING, BBItems.SURGICAL_RIG, 1, List.of("IGI", "ISI", " C "),
                'I', Tags.Items.INGOTS_IRON, 'G', Items.GLOWSTONE_DUST, 'S', Items.SHEARS, 'C', BBItems.CLEAVER);
        shaped(out, "vent_arm", EQUIPMENT, BBItems.VENT_ARM, 1, List.of("PNP", "PMP", " P "),
                'P', plates("brass"), 'N', AllBlocks.NOZZLE, 'M', AllItems.PRECISION_MECHANISM);
    }

    // ---------------------------------------------------------------- Create's processing

    /** Blood and soul blood by their common tags, so other mods' fluids go in too; liquid experience by our own (BBFluids#LIQUID_EXPERIENCE). */
    private static final TagKey<Fluid> BLOOD = BBFluids.BLOOD_TAG;
    private static final TagKey<Fluid> SOUL_BLOOD = BBFluids.SOUL_BLOOD_TAG;
    private static final TagKey<Fluid> EXPERIENCE = BBFluids.LIQUID_EXPERIENCE;

    /** The soul blood line's amounts: what a Basin Lid sets in a basin, and what one clot melts back to. */
    private static final int CONGEAL_MB = 250;
    private static final int REMELT_MB = 200;
    /** Ticks blood takes to set under a Basin Lid, as long as Diesel Generators' own fermenting takes. */
    private static final int CONGEAL_TICKS = 200;
    /** The two one-step shortcuts: a litre of blood for a tenth of it back, where the full line gives four fifths. */
    private static final int SHORTCUT_BLOOD_MB = 1000;
    private static final int SHORTCUT_MB = 100;
    /** A splash of blood over a block of the stained palette. */
    private static final int PALETTE_MB = 100;

    private static void processing(RecipeOutput out) {
        // Blood Steel: iron quenched in blood (brief § Blood and materials)
        new StandardProcessingRecipe.Builder<>(FillingRecipe::new, asResource("blood_steel_ingot"))
                .require(Tags.Items.INGOTS_IRON).require(BLOOD, 250)
                .output(BBItems.BLOOD_STEEL_INGOT.get()).build(out);
        // blood-stained cladding: Create's casings with blood spouted over them
        new StandardProcessingRecipe.Builder<>(FillingRecipe::new, asResource("bloody_casing"))
                .require(AllBlocks.ANDESITE_CASING.get()).require(BLOOD, 250)
                .output(BBBlocks.BLOODY_CASING.get()).build(out);
        new StandardProcessingRecipe.Builder<>(FillingRecipe::new, asResource("bloody_brass_casing"))
                .require(AllBlocks.BRASS_CASING.get()).require(BLOOD, 250)
                .output(BBBlocks.BLOODY_BRASS_CASING.get()).build(out);
        new StandardProcessingRecipe.Builder<>(FillingRecipe::new, asResource("bloody_copper_casing"))
                .require(AllBlocks.COPPER_CASING.get()).require(BLOOD, 250)
                .output(BBBlocks.BLOODY_COPPER_CASING.get()).build(out);
        new StandardProcessingRecipe.Builder<>(FillingRecipe::new, asResource("bloody_railway_casing"))
                .require(AllBlocks.RAILWAY_CASING.get()).require(BLOOD, 250)
                .output(BBBlocks.BLOODY_RAILWAY_CASING.get()).build(out);
        // soul canisters, for brass minions: filled at a Spout, emptied at an Item Drain
        new StandardProcessingRecipe.Builder<>(FillingRecipe::new, asResource("soul_canister"))
                .require(BBItems.EMPTY_SOUL_CANISTER.get()).require(SOUL_BLOOD, 1000)
                .output(BBItems.SOUL_CANISTER.get()).build(out);
        new StandardProcessingRecipe.Builder<>(EmptyingRecipe::new, asResource("soul_canister"))
                .require(BBItems.SOUL_CANISTER.get())
                .output(BBItems.EMPTY_SOUL_CANISTER.get()).output(BBFluids.soulBlood(), 1000).build(out);
        // raw hide washed into leather
        new StandardProcessingRecipe.Builder<>(SplashingRecipe::new, asResource("raw_hide"))
                .require(BBItems.RAW_HIDE.get())
                .output(Items.LEATHER).build(out);

        // Soul Blood, the full line as section 8 of docs/ARCHITECTURE-PROPOSAL.md decided it: blood in a basin under a
        // Diesel Generators Basin Lid ferments into congealed blood; a fan through soul fire haunts it; a superheated
        // mixer melts it back as soul blood
        new StandardProcessingRecipe.Builder<>(com.jesz.createdieselgenerators.content.basin_lid.BasinFermentingRecipe::new, asResource("congealed_blood"))
                .require(BLOOD, CONGEAL_MB)
                .duration(CONGEAL_TICKS)
                .output(BBItems.CONGEALED_BLOOD.get()).build(out);
        new StandardProcessingRecipe.Builder<>(HauntingRecipe::new, asResource("soul_clot"))
                .require(BBItems.CONGEALED_BLOOD.get())
                .output(BBItems.SOUL_CLOT.get()).build(out);
        new StandardProcessingRecipe.Builder<>(MixingRecipe::new, asResource("soul_blood"))
                .require(BBItems.SOUL_CLOT.get())
                .requiresHeat(HeatCondition.SUPERHEATED)
                .output(BBFluids.soulBlood(), REMELT_MB).build(out);
        // and the two one-step shortcuts, far poorer: a tenth of the blood back
        new StandardProcessingRecipe.Builder<>(MixingRecipe::new, asResource("soul_blood_from_soul_sand"))
                .require(Items.SOUL_SAND).require(BLOOD, SHORTCUT_BLOOD_MB).require(EXPERIENCE, 100)
                .requiresHeat(HeatCondition.SUPERHEATED)
                .output(BBFluids.soulBlood(), SHORTCUT_MB).build(out);
        // under the same lid: a basin tries the recipe with the most items first (Create's BasinOperatingBlockEntity),
        // so wart and soul soil in with a bucket of blood go this way, and blood alone sets
        new StandardProcessingRecipe.Builder<>(com.jesz.createdieselgenerators.content.basin_lid.BasinFermentingRecipe::new, asResource("soul_blood"))
                .require(Items.SOUL_SOIL).require(Items.NETHER_WART).require(Items.NETHER_WART).require(BLOOD, SHORTCUT_BLOOD_MB)
                .duration(600)
                .output(BBFluids.soulBlood(), SHORTCUT_MB).build(out);

        // Blood Diamond: a diamond through a spout of a bucket of blood, then a spout of a bucket of liquid
        // experience, in sequence (brief § Blood and materials)
        new SequencedAssemblyRecipeBuilder(asResource("blood_diamond"))
                .require(Tags.Items.GEMS_DIAMOND)
                .transitionTo(BBItems.INCOMPLETE_BLOOD_DIAMOND.get())
                .addOutput(BBItems.BLOOD_DIAMOND.get(), 1)
                .loops(1)
                .addStep(FillingRecipe::new, rb -> rb.require(BLOOD, 1000))
                .addStep(FillingRecipe::new, rb -> rb.require(EXPERIENCE, 1000))
                .build(out);
        // Soul Netherite (section 8: a spout of soul blood, then a deployer with a super experience block)
        new SequencedAssemblyRecipeBuilder(asResource("soul_netherite_ingot"))
                .require(Items.NETHERITE_INGOT)
                .transitionTo(BBItems.INCOMPLETE_SOUL_NETHERITE_INGOT.get())
                .addOutput(BBItems.SOUL_NETHERITE_INGOT.get(), 1)
                .loops(1)
                .addStep(FillingRecipe::new, rb -> rb.require(SOUL_BLOOD, 1000))
                .addStep(DeployerApplicationRecipe::new, rb -> rb.require(item("create_enchantment_industry:super_experience_block")))
                .build(out);
    }

    // ---------------------------------------------------------------- the stained palette

    /**
     * Create's cut calcite blocks with blood spouted over them; from the stained cut calcite, the other three by
     * stonecutter as Create's own stone types go; stairs and slabs from each, crafted or cut.
     */
    private static void palette(RegistrateRecipeProvider out) {
        String[][] stained = {{"cut_calcite", "bloody_cut_calcite"}, {"polished_cut_calcite", "bloody_polished_cut_calcite"},
                {"cut_calcite_bricks", "bloody_cut_calcite_bricks"}, {"small_calcite_bricks", "bloody_small_calcite_bricks"}};
        for (String[] pair : stained) {
            new StandardProcessingRecipe.Builder<>(FillingRecipe::new, asResource(pair[1]))
                    .require(item("create:" + pair[0])).require(BLOOD, PALETTE_MB)
                    .output(item("bloodandbones:" + pair[1])).build(out);
        }
        DataIngredient cut = DataIngredient.items(BBBlocks.BLOODY_CUT_CALCITE.get());
        out.stonecutting(cut, RecipeCategory.BUILDING_BLOCKS, BBBlocks.BLOODY_POLISHED_CUT_CALCITE);
        out.stonecutting(cut, RecipeCategory.BUILDING_BLOCKS, BBBlocks.BLOODY_CUT_CALCITE_BRICKS);
        out.stonecutting(cut, RecipeCategory.BUILDING_BLOCKS, BBBlocks.BLOODY_SMALL_CALCITE_BRICKS);
        List<com.tterrag.registrate.util.entry.BlockEntry<?>> blocks = BBBlocks.stainedPalette();
        for (int i = 0; i < 4; i++) {
            DataIngredient base = DataIngredient.items(blocks.get(i).get());
            out.stairs(base, RecipeCategory.BUILDING_BLOCKS, blocks.get(4 + i)::get, null, true);
            out.slab(base, RecipeCategory.BUILDING_BLOCKS, blocks.get(8 + i)::get, null, true);
        }
    }

    // ---------------------------------------------------------------- helpers

    private static ResourceLocation asResource(String path) {
        return BloodAndBones.asResource(path);
    }

    /** Create's brass, copper and iron sheets: the common plates tag. */
    private static TagKey<Item> plates(String metal) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "plates/" + metal));
    }

    /** Another mod's item, by id, looked up once the registries are filled. */
    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    /** An item, an item tag or a registered entry, as an ingredient. */
    private static Ingredient ingredient(Object thing) {
        if (thing instanceof TagKey<?> tag) {
            @SuppressWarnings("unchecked")
            TagKey<Item> items = (TagKey<Item>) tag;
            return Ingredient.of(items);
        }
        return Ingredient.of((ItemLike) thing);
    }

    /** A shaped recipe; {@code key} is pairs of a character and what it stands for. */
    private static void shaped(RecipeOutput out, String name, CraftingBookCategory category, ItemLike result, int count, List<String> pattern, Object... key) {
        out.accept(asResource(name), new ShapedRecipe("", category, ShapedRecipePattern.of(keys(key), pattern), new ItemStack(result, count)), null);
    }

    /** One of the four carcass armour pieces: a shaped grid of scraps, checked for fitting together as it is crafted. */
    private static void armour(RecipeOutput out, String name, String piece, ItemLike result, List<String> pattern, Object... key) {
        out.accept(asResource(name), new com.avicagan.bloodandbones.parts.CarcassArmourRecipe("", EQUIPMENT, ShapedRecipePattern.of(keys(key), pattern),
                new ItemStack(result), piece), null);
    }

    private static Map<Character, Ingredient> keys(Object... key) {
        Map<Character, Ingredient> map = new LinkedHashMap<>();
        for (int i = 0; i < key.length; i += 2) {
            map.put((Character) key[i], ingredient(key[i + 1]));
        }
        return map;
    }

    private static void shapeless(RecipeOutput out, String name, CraftingBookCategory category, ItemLike result, int count, Object... ingredients) {
        NonNullList<Ingredient> list = NonNullList.create();
        for (Object ingredient : ingredients) {
            list.add(ingredient(ingredient));
        }
        out.accept(asResource(name), new ShapelessRecipe("", category, new ItemStack(result, count), list), null);
    }

    private static void smithing(RecipeOutput out, String name, Object template, Object base, Object addition, ItemLike result) {
        out.accept(asResource(name), new SmithingTransformRecipe(ingredient(template), ingredient(base), ingredient(addition), new ItemStack(result)), null);
    }

    /** A crafting recipe worked out in code (fitting carcass armour, strapping a backtank on): only its type and category are data. */
    private static void special(RecipeOutput out, String name,
                                java.util.function.Function<CraftingBookCategory, net.minecraft.world.item.crafting.CraftingRecipe> factory) {
        out.accept(asResource(name), factory.apply(EQUIPMENT), null);
    }
}
