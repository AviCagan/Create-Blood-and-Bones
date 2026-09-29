package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.ImplantItem;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.TagFluidIngredient;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Blood, Soul Blood and the materials, as the brief has them (docs/BRIEF-AUDIT.md package 14): the soul blood tag,
 * every recipe's inputs, amounts and outputs read back from the loaded recipes, the soul blood line and the Blood
 * Diamond run on Create's own machines, and the implants that run on the tags.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MaterialsTests {
    private static final ResourceLocation EXPERIENCE = ResourceLocation.fromNamespaceAndPath("create_enchantment_industry", "experience");

    /** Soul blood has a tag of its own, apart from blood; liquid experience is in the common tag the recipes ask for. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void soulBloodTaggedApart(GameTestHelper helper) {
        if (!BBFluids.soulBlood().is(BBFluids.SOUL_BLOOD_TAG) || BBFluids.soulBlood().is(BBFluids.BLOOD_TAG)) {
            helper.fail("Soul blood should be in c:soul_blood and not in c:blood");
        }
        if (!BBFluids.blood().is(BBFluids.BLOOD_TAG) || BBFluids.blood().is(BBFluids.SOUL_BLOOD_TAG)) {
            helper.fail("Blood should be in c:blood and not in c:soul_blood");
        }
        if (!new ItemStack(BBFluids.SOUL_BLOOD.getBucket().get()).is(BBFluids.SOUL_BLOOD_BUCKETS)
                || !new ItemStack(BBFluids.BLOOD.getBucket().get()).is(BBFluids.BLOOD_BUCKETS)
                || new ItemStack(BBFluids.SOUL_BLOOD.getBucket().get()).is(BBFluids.BLOOD_BUCKETS)) {
            helper.fail("Each bucket should be in its own fluid's bucket tag only");
        }
        if (!BuiltInRegistries.FLUID.get(EXPERIENCE).is(Tags.Fluids.EXPERIENCE)) {
            helper.fail("Create Enchantment Industry's liquid experience should be in c:experience");
        }
        helper.succeed();
    }

    /** Blood Steel, the blood-stained casings and the soul canister take their fluid by tag, in the brief's amounts. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fillingRecipesTakeTheTags(GameTestHelper helper) {
        ProcessingRecipe<?, ?> steel = processing(helper, "filling/blood_steel_ingot", AllRecipeTypes.FILLING.getType());
        if (steel == null) {
            return;
        }
        requireItems(helper, steel, "Blood Steel", new ItemStack(Items.IRON_INGOT));
        requireFluid(helper, steel, "Blood Steel", BBFluids.BLOOD_TAG, 250);
        requireOutput(helper, steel, "Blood Steel", BBItems.BLOOD_STEEL_INGOT.get(), 1);
        for (String casing : List.of("bloody_casing", "bloody_brass_casing", "bloody_copper_casing", "bloody_railway_casing")) {
            ProcessingRecipe<?, ?> recipe = processing(helper, "filling/" + casing, AllRecipeTypes.FILLING.getType());
            if (recipe == null) {
                return;
            }
            requireFluid(helper, recipe, casing, BBFluids.BLOOD_TAG, 250);
            requireOutput(helper, recipe, casing, BuiltInRegistries.ITEM.get(BloodAndBones.asResource(casing)), 1);
        }
        ProcessingRecipe<?, ?> canister = processing(helper, "filling/soul_canister", AllRecipeTypes.FILLING.getType());
        if (canister == null) {
            return;
        }
        requireItems(helper, canister, "the soul canister", new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get()));
        requireFluid(helper, canister, "the soul canister", BBFluids.SOUL_BLOOD_TAG, 1000);
        requireOutput(helper, canister, "the soul canister", BBItems.SOUL_CANISTER.get(), 1);
        ProcessingRecipe<?, ?> emptying = processing(helper, "emptying/soul_canister", AllRecipeTypes.EMPTYING.getType());
        if (emptying != null && (emptying.getFluidResults().size() != 1 || !emptying.getFluidResults().get(0).is(BBFluids.soulBlood())
                || emptying.getFluidResults().get(0).getAmount() != 1000)) {
            helper.fail("An Item Drain should take 1000 mB of soul blood out of a canister");
        }
        helper.succeed();
    }

    /**
     * The soul blood line (brief § Blood and materials: "congeal, haunt, re-melt ... heating, pressing, haunting and
     * mixing"): a press over a heated basin sets 250 mB of blood, a fan through soul fire haunts it, a superheated mixer
     * melts it back into 200 mB; the two one-step shortcuts give a tenth, far poorer.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void soulBloodLineRecipes(GameTestHelper helper) {
        ProcessingRecipe<?, ?> congeal = processing(helper, "compacting/congealed_blood", AllRecipeTypes.COMPACTING.getType());
        ProcessingRecipe<?, ?> haunt = processing(helper, "haunting/soul_clot", AllRecipeTypes.HAUNTING.getType());
        ProcessingRecipe<?, ?> melt = processing(helper, "mixing/soul_blood", AllRecipeTypes.MIXING.getType());
        ProcessingRecipe<?, ?> mixShortcut = processing(helper, "mixing/soul_blood_from_soul_sand", AllRecipeTypes.MIXING.getType());
        ProcessingRecipe<?, ?> fermentShortcut = processing(helper, "basin_fermenting/soul_blood",
                BuiltInRegistries.RECIPE_TYPE.get(ResourceLocation.fromNamespaceAndPath("createdieselgenerators", "basin_fermenting")));
        if (congeal == null || haunt == null || melt == null || mixShortcut == null || fermentShortcut == null) {
            return;
        }
        requireItems(helper, congeal, "Congealing");
        requireFluid(helper, congeal, "Congealing", BBFluids.BLOOD_TAG, 250);
        requireHeat(helper, congeal, "Congealing", HeatCondition.HEATED);
        requireOutput(helper, congeal, "Congealing", BBItems.CONGEALED_BLOOD.get(), 1);

        requireItems(helper, haunt, "Haunting", new ItemStack(BBItems.CONGEALED_BLOOD.get()));
        requireOutput(helper, haunt, "Haunting", BBItems.SOUL_CLOT.get(), 1);

        requireItems(helper, melt, "Re-melting", new ItemStack(BBItems.SOUL_CLOT.get()));
        requireHeat(helper, melt, "Re-melting", HeatCondition.SUPERHEATED);
        int line = requireFluidOutput(helper, melt, "Re-melting", 200);

        requireItems(helper, mixShortcut, "The mixing shortcut", new ItemStack(Items.SOUL_SAND));
        requireFluid(helper, mixShortcut, "The mixing shortcut", BBFluids.BLOOD_TAG, 1000);
        requireFluid(helper, mixShortcut, "The mixing shortcut", Tags.Fluids.EXPERIENCE, 100);
        requireHeat(helper, mixShortcut, "The mixing shortcut", HeatCondition.SUPERHEATED);
        int mixed = requireFluidOutput(helper, mixShortcut, "The mixing shortcut", 100);

        requireItems(helper, fermentShortcut, "The fermenting shortcut", new ItemStack(Items.SOUL_SOIL), new ItemStack(Items.NETHER_WART), new ItemStack(Items.NETHER_WART));
        requireFluid(helper, fermentShortcut, "The fermenting shortcut", BBFluids.BLOOD_TAG, 1000);
        int fermented = requireFluidOutput(helper, fermentShortcut, "The fermenting shortcut", 100);

        // soul blood a millibucket of blood gives: the full line at least five times either shortcut
        float full = line / 250.0F;
        if (full < 5 * mixed / 1000.0F || full < 5 * fermented / 1000.0F) {
            helper.fail("The full line should be far better than the shortcuts: " + full + " a mB against " + mixed / 1000.0F + " and " + fermented / 1000.0F);
        }
        helper.succeed();
    }

    /**
     * The Blood Diamond is a sequenced assembly (brief: "a diamond taken through a blooded spout with a lot of blood
     * (1 bucket) and 1 bucket of liquid XP from a spout"); soul netherite is one too (section 8: a spout of soul
     * blood, then a deployer with a super experience block).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sequencedMaterialsRecipes(GameTestHelper helper) {
        SequencedAssemblyRecipe diamond = sequence(helper, "sequenced_assembly/blood_diamond");
        SequencedAssemblyRecipe netherite = sequence(helper, "sequenced_assembly/soul_netherite_ingot");
        if (diamond == null || netherite == null) {
            return;
        }
        if (!diamond.getIngredient().test(new ItemStack(Items.DIAMOND)) || diamond.getLoops() != 1
                || !diamond.getTransitionalItem().is(BBItems.INCOMPLETE_BLOOD_DIAMOND.get())
                || !diamond.getResultItem(helper.getLevel().registryAccess()).is(BBItems.BLOOD_DIAMOND.get())) {
            helper.fail("The Blood Diamond should be one pass of a diamond, through an Incomplete Blood Diamond");
            return;
        }
        if (diamond.getSequence().size() != 2) {
            helper.fail("The Blood Diamond should take two steps, found " + diamond.getSequence().size());
            return;
        }
        ProcessingRecipe<?, ?> blood = diamond.getSequence().get(0).getRecipe();
        ProcessingRecipe<?, ?> experience = diamond.getSequence().get(1).getRecipe();
        if (blood.getType() != AllRecipeTypes.FILLING.getType() || experience.getType() != AllRecipeTypes.FILLING.getType()) {
            helper.fail("Both of the Blood Diamond's steps should be a spout's");
        }
        requireFluid(helper, blood, "The Blood Diamond's first step", BBFluids.BLOOD_TAG, 1000);
        requireFluid(helper, experience, "The Blood Diamond's second step", Tags.Fluids.EXPERIENCE, 1000);
        if (helper.getLevel().getRecipeManager().byKey(BloodAndBones.asResource("filling/blood_diamond")).isPresent()) {
            helper.fail("The old one-fill Blood Diamond should be gone");
        }

        if (!netherite.getIngredient().test(new ItemStack(Items.NETHERITE_INGOT)) || netherite.getSequence().size() != 2
                || !netherite.getResultItem(helper.getLevel().registryAccess()).is(BBItems.SOUL_NETHERITE_INGOT.get())) {
            helper.fail("Soul netherite should be two steps on a netherite ingot");
            return;
        }
        requireFluid(helper, netherite.getSequence().get(0).getRecipe(), "Soul netherite's first step", BBFluids.SOUL_BLOOD_TAG, 1000);
        ProcessingRecipe<?, ?> deploy = netherite.getSequence().get(1).getRecipe();
        if (deploy.getType() != AllRecipeTypes.DEPLOYING.getType()
                || !deploy.getIngredients().get(1).test(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create_enchantment_industry", "super_experience_block"))))) {
            helper.fail("Soul netherite's second step should be a deployer applying a super experience block");
        }
        helper.succeed();
    }

    /**
     * A real Spout over a Depot: blood first turns a diamond into an Incomplete Blood Diamond, experience then makes it a
     * Blood Diamond. Beside it, a Spout of experience over a plain diamond does nothing: the order is the recipe's.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void spoutsMakeABloodDiamond(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos depot = new BlockPos(3, 2, 3);
        BlockPos spout = depot.above(2);
        BlockPos wrongDepot = new BlockPos(7, 2, 3);
        BlockPos wrongSpout = wrongDepot.above(2);
        for (BlockPos at : List.of(depot, wrongDepot)) {
            helper.setBlock(at, AllBlocks.DEPOT.getDefaultState());
            helper.setBlock(at.above(2), AllBlocks.SPOUT.getDefaultState());
        }
        IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(spout), Direction.UP);
        IFluidHandler wrongTank = level.getCapability(Capabilities.FluidHandler.BLOCK, helper.absolutePos(wrongSpout), Direction.UP);
        IItemHandler onDepot = level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(depot), null);
        IItemHandler onWrongDepot = level.getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(wrongDepot), null);
        Fluid experience = BuiltInRegistries.FLUID.get(EXPERIENCE);
        if (tank == null || wrongTank == null || onDepot == null || onWrongDepot == null
                || tank.fill(new FluidStack(BBFluids.blood(), 1000), IFluidHandler.FluidAction.EXECUTE) != 1000
                || wrongTank.fill(new FluidStack(experience, 1000), IFluidHandler.FluidAction.EXECUTE) != 1000
                || !onDepot.insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty()
                || !onWrongDepot.insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty()) {
            helper.fail("The Spouts should take a bucket each and the Depots a diamond each");
            return;
        }
        boolean[] second = {false};
        helper.onEachTick(() -> {
            if (!second[0] && onDepot.getStackInSlot(0).is(BBItems.INCOMPLETE_BLOOD_DIAMOND.get()) && tank.getFluidInTank(0).isEmpty()) {
                second[0] = true;
                tank.fill(new FluidStack(experience, 1000), IFluidHandler.FluidAction.EXECUTE);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(second[0], "the blood has not gone into the diamond yet: " + onDepot.getStackInSlot(0));
            helper.assertTrue(onDepot.getStackInSlot(0).is(BBItems.BLOOD_DIAMOND.get()) && tank.getFluidInTank(0).isEmpty(),
                    "the experience has not finished the diamond yet: " + onDepot.getStackInSlot(0));
            helper.assertTrue(onWrongDepot.getStackInSlot(0).is(Items.DIAMOND) && wrongTank.getFluidInTank(0).getAmount() == 1000,
                    "experience first should do nothing: " + onWrongDepot.getStackInSlot(0));
        });
    }

    /** A Mechanical Press over a basin on a lit Blaze Burner sets 250 mB of blood into Congealed Blood; unheated, it does not. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void pressSetsBloodInAHeatedBasin(GameTestHelper helper) {
        BasinBlockEntity hot = basin(helper, new BlockPos(3, 3, 3), HeatCondition.HEATED);
        BasinBlockEntity cold = basin(helper, new BlockPos(7, 3, 3), HeatCondition.NONE);
        for (BlockPos basin : List.of(new BlockPos(3, 3, 3), new BlockPos(7, 3, 3))) {
            BlockPos press = basin.above(2);
            helper.setBlock(press, AllBlocks.MECHANICAL_PRESS.getDefaultState().setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
            motor(helper, press.east(), Direction.WEST);
        }
        if (hot == null || cold == null) {
            return;
        }
        for (BasinBlockEntity basin : List.of(hot, cold)) {
            basin.getTanks().getFirst().getCapability().fill(new FluidStack(BBFluids.blood(), 250), IFluidHandler.FluidAction.EXECUTE);
        }
        helper.runAtTickTime(300, () -> {
            if (count(cold.getOutputInventory(), BBItems.CONGEALED_BLOOD.get()) + count(cold.getInputInventory(), BBItems.CONGEALED_BLOOD.get()) > 0) {
                helper.fail("An unheated basin should not set blood");
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(count(hot.getOutputInventory(), BBItems.CONGEALED_BLOOD.get()) == 1,
                    "no Congealed Blood in the heated basin yet; blood left " + hot.getTanks().getFirst().getPrimaryHandler().getFluidAmount());
            helper.assertTrue(helper.getTick() >= 300, "waiting on the cold basin");
        });
    }

    /** An Encased Fan blowing through a soul campfire haunts Congealed Blood lying on a Depot into a Soul Clot. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void fanHauntsCongealedBlood(GameTestHelper helper) {
        BlockPos motor = new BlockPos(1, 2, 5);
        BlockPos fan = motor.east();
        BlockPos depot = fan.east(2);
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(fan, AllBlocks.ENCASED_FAN.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        helper.setBlock(fan.east(), Blocks.SOUL_CAMPFIRE.defaultBlockState());
        helper.setBlock(depot, AllBlocks.DEPOT.getDefaultState());
        IItemHandler onDepot = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(depot), null);
        if (onDepot == null || !onDepot.insertItem(0, new ItemStack(BBItems.CONGEALED_BLOOD.get()), false).isEmpty()) {
            helper.fail("The Depot should take the Congealed Blood");
            return;
        }
        helper.runAfterDelay(2, () -> {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(motor)) instanceof CreativeMotorBlockEntity be) {
                be.generatedSpeed.setValue(64);
            }
        });
        helper.succeedWhen(() -> helper.assertTrue(onDepot.getStackInSlot(0).is(BBItems.SOUL_CLOT.get()),
                "not haunted yet: " + onDepot.getStackInSlot(0)));
    }

    /** A Mechanical Mixer over a superheated basin melts a Soul Clot into 200 mB of soul blood. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void mixerMeltsSoulClot(GameTestHelper helper) {
        BlockPos at = new BlockPos(4, 3, 4);
        BasinBlockEntity basin = basin(helper, at, HeatCondition.SUPERHEATED);
        BlockPos mixer = at.above(2);
        helper.setBlock(mixer, AllBlocks.MECHANICAL_MIXER.getDefaultState());
        // the mixer is a small cog: a cog beside it, turned from below
        helper.setBlock(mixer.east(), AllBlocks.COGWHEEL.getDefaultState());
        motor(helper, mixer.east().below(), Direction.UP);
        if (basin == null) {
            return;
        }
        basin.getInputInventory().insertItem(0, new ItemStack(BBItems.SOUL_CLOT.get()), false);
        helper.succeedWhen(() -> {
            FluidStack out = basin.getTanks().getSecond().getCapability().getFluidInTank(0);
            helper.assertTrue(out.is(BBFluids.soulBlood()) && out.getAmount() == 200, "no soul blood yet: " + out.getAmount() + " mB of " + out.getHoverName().getString());
        });
    }

    /**
     * Implants run on the tags: a Flesh Arm on blood (c:blood) but not on soul blood, a Hydraulic Arm the other way
     * round; perfusion clears rot with blood and not with soul blood.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void implantsRunOnTheTags(GameTestHelper helper) {
        ImplantItem flesh = BBItems.FLESH_ARM.get();
        ImplantItem brass = BBItems.HYDRAULIC_ARM.get();
        if (flesh.fuelTag() != BBFluids.BLOOD_TAG || brass.fuelTag() != BBFluids.SOUL_BLOOD_TAG) {
            helper.fail("Organic implants should run on c:blood and cybernetics on c:soul_blood");
            return;
        }
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5.5, 2, 5.5)));
        ItemStack arm = new ItemStack(flesh);
        arm.set(com.avicagan.bloodandbones.registry.BBDataComponents.NECROSIS, 50);
        BodyEffects.body(player).fit(BodyPart.RIGHT_ARM, arm);
        ItemStack tank = new ItemStack(BBItems.backtank(BacktankTier.COPPER));
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.soulBlood(), 1000));
        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, tank);
        if (flesh.working(player) || !brass.working(player)) {
            helper.fail("On soul blood a Flesh Arm should hang dead and a Hydraulic Arm work");
            return;
        }
        if (com.avicagan.bloodandbones.body.Necrosis.perfuse(player)) {
            helper.fail("Soul blood should not perfuse a Flesh Arm");
            return;
        }
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.blood(), 1000));
        if (!flesh.working(player) || brass.working(player)) {
            helper.fail("On blood a Flesh Arm should work and a Hydraulic Arm stop");
            return;
        }
        if (!com.avicagan.bloodandbones.body.Necrosis.perfuse(player)
                || com.avicagan.bloodandbones.body.Necrosis.of(BodyEffects.body(player).implant(BodyPart.RIGHT_ARM)) >= 50) {
            helper.fail("Blood should perfuse the Flesh Arm");
            return;
        }
        FluidBacktankItem.setFluid(tank, new FluidStack(Fluids.WATER, 1000));
        if (flesh.working(player) || brass.working(player)) {
            helper.fail("Water runs neither");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    @Nullable
    private static ProcessingRecipe<?, ?> processing(GameTestHelper helper, String id, RecipeType<?> type) {
        var holder = helper.getLevel().getRecipeManager().byKey(BloodAndBones.asResource(id));
        if (holder.isEmpty() || !(holder.get().value() instanceof ProcessingRecipe<?, ?> recipe) || holder.get().value().getType() != type) {
            helper.fail("No " + BuiltInRegistries.RECIPE_TYPE.getKey(type) + " recipe " + id);
            return null;
        }
        return recipe;
    }

    @Nullable
    private static SequencedAssemblyRecipe sequence(GameTestHelper helper, String id) {
        var holder = helper.getLevel().getRecipeManager().byKey(BloodAndBones.asResource(id));
        if (holder.isEmpty() || !(holder.get().value() instanceof SequencedAssemblyRecipe recipe)) {
            helper.fail("No sequenced assembly " + id);
            return null;
        }
        return recipe;
    }

    /** Its item ingredients take exactly these, in order (a filling step's first one is the item filled). */
    private static void requireItems(GameTestHelper helper, Recipe<?> recipe, String what, ItemStack... items) {
        var ingredients = recipe.getIngredients();
        if (ingredients.size() != items.length) {
            helper.fail(what + " should take " + items.length + " items, takes " + ingredients.size());
            return;
        }
        for (int i = 0; i < items.length; i++) {
            if (!ingredients.get(i).test(items[i])) {
                helper.fail(what + " should take " + items[i].getHoverName().getString() + " as its item " + (i + 1));
            }
        }
    }

    /** One of its fluid ingredients is this tag, whole (any fluid in it goes), in this amount. */
    private static void requireFluid(GameTestHelper helper, ProcessingRecipe<?, ?> recipe, String what, TagKey<Fluid> tag, int amount) {
        for (SizedFluidIngredient fluid : recipe.getFluidIngredients()) {
            if (fluid.ingredient() instanceof TagFluidIngredient byTag && byTag.tag().equals(tag)) {
                if (fluid.amount() != amount) {
                    helper.fail(what + " should take " + amount + " mB of #" + tag.location() + ", takes " + fluid.amount());
                }
                return;
            }
        }
        helper.fail(what + " should take #" + tag.location() + " by its tag; it takes " + recipe.getFluidIngredients());
    }

    private static void requireHeat(GameTestHelper helper, ProcessingRecipe<?, ?> recipe, String what, HeatCondition heat) {
        if (recipe.getRequiredHeat() != heat) {
            helper.fail(what + " should need " + heat + ", needs " + recipe.getRequiredHeat());
        }
    }

    private static void requireOutput(GameTestHelper helper, ProcessingRecipe<?, ?> recipe, String what, Item item, int count) {
        var results = recipe.getRollableResults();
        if (results.size() != 1 || !results.get(0).getStack().is(item) || results.get(0).getStack().getCount() != count || results.get(0).getChance() != 1.0F) {
            helper.fail(what + " should make " + count + " " + item + ", makes " + results.stream().map(r -> r.getStack().toString()).toList());
        }
    }

    /** It makes only soul blood, this much; returns the amount. */
    private static int requireFluidOutput(GameTestHelper helper, ProcessingRecipe<?, ?> recipe, String what, int amount) {
        var fluids = recipe.getFluidResults();
        if (fluids.size() != 1 || !fluids.get(0).is(BBFluids.soulBlood()) || fluids.get(0).getAmount() != amount || !recipe.getRollableResults().isEmpty()) {
            helper.fail(what + " should make " + amount + " mB of soul blood and nothing else, makes " + fluids.stream().map(f -> f.getAmount() + " " + f.getHoverName().getString()).toList());
            return 0;
        }
        return amount;
    }

    /** A basin at {@code at} on a Blaze Burner lit to this heat: coal for heated, a blaze cake for superheated. */
    @Nullable
    private static BasinBlockEntity basin(GameTestHelper helper, BlockPos at, HeatCondition heat) {
        BlockPos burner = at.below();
        if (heat != HeatCondition.NONE) {
            helper.setBlock(burner, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(BlazeBurnerBlock.HEAT_LEVEL, BlazeBurnerBlock.HeatLevel.SMOULDERING));
            ItemStack fuel = heat == HeatCondition.SUPERHEATED ? AllItems.BLAZE_CAKE.asStack() : new ItemStack(Items.COAL);
            BlazeBurnerBlock.tryInsert(helper.getBlockState(burner), helper.getLevel(), helper.absolutePos(burner), fuel, false, false, false);
        }
        helper.setBlock(at, AllBlocks.BASIN.getDefaultState());
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof BasinBlockEntity basin)) {
            helper.fail("No basin");
            return null;
        }
        return basin;
    }

    private static void motor(GameTestHelper helper, BlockPos at, Direction facing) {
        helper.setBlock(at, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, facing));
        helper.runAfterDelay(2, () -> {
            if (helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof CreativeMotorBlockEntity be) {
                be.generatedSpeed.setValue(64);
            }
        });
    }

    private static int count(IItemHandler handler, Item item) {
        int n = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            if (handler.getStackInSlot(slot).is(item)) {
                n += handler.getStackInSlot(slot).getCount();
            }
        }
        return n;
    }
}
