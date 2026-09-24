package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.SeveredLimbItem;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.Hides;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ScrapsItem;
import com.avicagan.bloodandbones.parts.Source;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.content.fluids.spout.FillingBySpout;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parts and traits, slice 4 (armour B): the chestplate, fitting hides, organs and tiers (returning what they
 * replace), and a Fluid Backtank strapped to a chestplate.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArmourFittingTests {
    private static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");
    private static final ResourceLocation RABBIT = ResourceLocation.withDefaultNamespace("rabbit");
    private static final ResourceLocation CHICKEN = ResourceLocation.withDefaultNamespace("chicken");
    private static final ResourceLocation ZOMBIE = ResourceLocation.withDefaultNamespace("zombie");
    private static final ResourceLocation PIG = ResourceLocation.withDefaultNamespace("pig");
    private static final ResourceLocation PARROT = ResourceLocation.withDefaultNamespace("parrot");

    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static int level(List<TraitList.Resolved> traits, String id) {
        for (TraitList.Resolved t : traits) {
            if (t.id().equals(bb(id))) {
                return t.level();
            }
        }
        return 0;
    }

    private static ItemStack scraps(ResourceLocation mob, String part) {
        return ScrapsItem.of(new Source(mob, part, false), 1);
    }

    private static ItemStack piece(Item item, String piece, ResourceLocation mob) {
        return CarcassArmourItem.make(new ItemStack(item), CarcassArmour.of(piece, mob, false), PartsData.SERVER);
    }

    private static CarcassArmour armour(ItemStack stack) {
        return CarcassArmourItem.armour(stack);
    }

    /** What crafting these in a grid gives, and what is left in the grid after. */
    private record Crafted(ItemStack result, List<ItemStack> left) {
        /** The one thing left in the grid, or empty. */
        ItemStack leftOver() {
            return left.stream().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        }
    }

    /** These items, anywhere in a crafting grid; null if they make nothing. */
    @Nullable
    private static Crafted craft(GameTestHelper helper, ItemStack... items) {
        List<ItemStack> grid = new ArrayList<>();
        for (ItemStack item : items) {
            grid.add(item.copy());
        }
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> new Crafted(holder.value().assemble(input, helper.getLevel().registryAccess()), holder.value().getRemainingItems(input)))
                .orElse(null);
    }

    /** A crafting grid of this shape, '.' empty, other letters these items; the result, or empty. */
    private static ItemStack craftShaped(GameTestHelper helper, String[] rows, Map<Character, ItemStack> key) {
        List<ItemStack> items = new ArrayList<>();
        for (String row : rows) {
            for (char c : row.toCharArray()) {
                items.add(c == '.' ? ItemStack.EMPTY : key.get(c).copy());
            }
        }
        CraftingInput input = CraftingInput.of(rows[0].length(), rows.length, items);
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess())).orElse(ItemStack.EMPTY);
    }

    /** These items in a row of Create's Mechanical Crafters: what they make, or null (the crafters would throw them out). */
    @Nullable
    private static ItemStack crafters(GameTestHelper helper, ItemStack... items) {
        RecipeGridHandler.GroupedItems grid = new RecipeGridHandler.GroupedItems(items[items.length - 1].copy());
        for (int i = items.length - 2; i >= 0; i--) {
            RecipeGridHandler.GroupedItems next = new RecipeGridHandler.GroupedItems(items[i].copy());
            grid.mergeOnto(next, Pointing.LEFT);
            grid = next;
        }
        return RecipeGridHandler.tryToApplyRecipe(helper.getLevel(), grid);
    }

    /** What a piece gives of one attribute, worn. */
    private static double attribute(ItemStack stack, EquipmentSlot slot, Holder<Attribute> attribute) {
        double[] sum = {0.0};
        stack.getAttributeModifiers().forEach(slot, (a, modifier) -> {
            if (a.is(attribute)) {
                sum[0] += modifier.amount();
            }
        });
        return sum[0];
    }

    /** What skinning this mob's carcass gives. */
    private static List<ItemStack> skin(GameTestHelper helper, ResourceLocation mob) {
        List<ItemStack> skinned = new ArrayList<>();
        CarcassSavedData.Carcass carcass = new CarcassSavedData.Carcass(java.util.UUID.randomUUID(), mob, "body");
        CarcassButchery.capturing(skinned::add, () -> {
            CarcassButchery.dropYields(helper.getLevel(), carcass, com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(mob).orElseThrow().hide(),
                    1.0F, new org.joml.Vector3d());
            return true;
        });
        return skinned;
    }

    /** An iron backtank holding this much blood. */
    private static ItemStack tank(BacktankTier tier, int blood) {
        ItemStack tank = new ItemStack(BBItems.backtank(tier));
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.blood(), blood));
        return tank;
    }

    /** A cow chestplate with an iron backtank of blood strapped on, crafted as a player would. */
    private static ItemStack strapped(GameTestHelper helper, int blood) {
        Crafted crafted = craft(helper, piece(BBItems.CARCASS_CHESTPLATE.get(), "chestplate", COW), tank(BacktankTier.IRON, blood));
        return crafted == null ? ItemStack.EMPTY : crafted.result();
    }

    /**
     * Six cow torso scraps under two chicken wing scraps make a Cow Hide Chestplate with chicken shoulders: hide
     * plate's 4 armour, durability 12 x 16, the cow's torso traits. The cow has no arms, so its own torso scraps
     * stand in for shoulders (and the piece stays all cow); another armless mob's torso does not.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void craftCowChestplate(GameTestHelper helper) {
        String[] shape = {"S.S", "TTT", "TTT"};
        ItemStack chest = craftShaped(helper, shape, Map.of('S', scraps(CHICKEN, "arm"), 'T', scraps(COW, "torso")));
        CarcassArmour armour = armour(chest);
        if (!chest.is(BBItems.CARCASS_CHESTPLATE.get()) || armour == null || !armour.body().equals(COW) || !armour.shoulders().equals(Optional.of(CHICKEN))
                || chest.getOrDefault(DataComponents.MAX_DAMAGE, 0) != 12 * 16 || attribute(chest, EquipmentSlot.CHEST, Attributes.ARMOR) != 4.0) {
            helper.fail("Cow torso scraps under chicken wings should make a cow chestplate with chicken shoulders, 4 armour, durability 192: "
                    + chest + " " + chest.getComponents());
            return;
        }
        List<TraitList.Resolved> traits = armour.traits(PartsData.SERVER);
        if (level(traits, "barrel_chest") != 1 || level(traits, "hardy") != 1 || armour.pure(COW)) {
            helper.fail("The chestplate should have the cow's torso traits and not count as all cow: " + traits);
            return;
        }
        ItemStack allCow = craftShaped(helper, shape, Map.of('S', scraps(COW, "torso"), 'T', scraps(COW, "torso")));
        if (armour(allCow) == null || armour(allCow).shoulders().isPresent() || !armour(allCow).pure(COW)) {
            helper.fail("Eight cow torso scraps should make an all-cow chestplate (a cow has no arms)");
            return;
        }
        if (!craftShaped(helper, shape, Map.of('S', scraps(RABBIT, "torso"), 'T', scraps(COW, "torso"))).isEmpty()) {
            helper.fail("Only the chestplate's own mob's torso scraps stand in for shoulders");
            return;
        }
        List<ItemStack> mixed = new ArrayList<>(List.of(scraps(CHICKEN, "arm"), ItemStack.EMPTY, scraps(ZOMBIE, "arm")));
        for (int i = 0; i < 6; i++) {
            mixed.add(scraps(COW, "torso"));
        }
        CraftingInput input = CraftingInput.of(3, 3, mixed);
        if (helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isPresent()) {
            helper.fail("Shoulders of two mobs should make nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * A rabbit hide on cow boots gives them a rabbit covering and nothing comes back; a cow's raw hide then
     * takes its place and the rabbit hide comes back, as the cow's does when leather (a cow's) replaces it.
     * Skinning a cow stamps its raw hide; the wrong number of hides, or two kinds, fit nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hideReplacesAndReturns(GameTestHelper helper) {
        List<ItemStack> skinned = new ArrayList<>();
        CarcassSavedData.Carcass carcass = new CarcassSavedData.Carcass(java.util.UUID.randomUUID(), COW, "body");
        CarcassButchery.capturing(skinned::add, () -> {
            CarcassButchery.dropYields(helper.getLevel(), carcass, com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(COW).orElseThrow().hide(),
                    1.0F, new org.joml.Vector3d());
            return true;
        });
        ItemStack cowHide = skinned.stream().filter(s -> s.is(BBItems.RAW_HIDE.get())).findFirst().orElse(ItemStack.EMPTY);
        Source stamp = cowHide.get(BBDataComponents.SOURCE.get());
        if (stamp == null || !stamp.entity().equals(COW) || !cowHide.getHoverName().getString().contains("Cow")) {
            helper.fail("Skinning a cow should give a raw hide stamped as the cow's: " + skinned);
            return;
        }
        cowHide.setCount(1);
        ItemStack boots = piece(BBItems.CARCASS_BOOTS.get(), "boots", COW);
        Crafted rabbit = craft(helper, boots, new ItemStack(Items.RABBIT_HIDE));
        if (rabbit == null || armour(rabbit.result()).hide().isEmpty() || !armour(rabbit.result()).hide().get().entity().equals(Optional.of(RABBIT))
                || !rabbit.leftOver().isEmpty()) {
            helper.fail("A rabbit hide on cow boots should give them a rabbit covering, with nothing coming back");
            return;
        }
        Crafted cow = craft(helper, rabbit.result(), cowHide);
        if (cow == null || !armour(cow.result()).hide().get().entity().equals(Optional.of(COW)) || !cow.leftOver().is(Items.RABBIT_HIDE)
                || cow.leftOver().getCount() != 1 || level(armour(cow.result()).traits(PartsData.SERVER), "thick_hide") != 1) {
            helper.fail("A cow's hide should replace the rabbit's, which comes back; and give the cow's Thick Hide: "
                    + (cow == null ? "nothing" : cow.result().getComponents() + " left " + cow.leftOver()));
            return;
        }
        Crafted leather = craft(helper, cow.result(), new ItemStack(Items.LEATHER));
        Source back = leather == null ? null : leather.leftOver().get(BBDataComponents.SOURCE.get());
        if (leather == null || !leather.leftOver().is(BBItems.RAW_HIDE.get()) || back == null || !back.entity().equals(COW)
                || !armour(leather.result()).hide().get().items().equals(List.of(Items.LEATHER))) {
            helper.fail("Leather (a cow's) should replace the cow's raw hide, which comes back stamped as it was");
            return;
        }
        Crafted plain = craft(helper, boots, new ItemStack(BBItems.RAW_HIDE.get()));
        if (plain == null || armour(plain.result()).hide().get().entity().isPresent() || !armour(plain.result()).pure(COW)) {
            helper.fail("A raw hide with no stamp should fit as a plain covering, from no mob");
            return;
        }
        if (craft(helper, boots, new ItemStack(Items.RABBIT_HIDE), new ItemStack(Items.RABBIT_HIDE)) != null
                || craft(helper, piece(BBItems.CARCASS_LEGGINGS.get(), "leggings", COW), new ItemStack(Items.RABBIT_HIDE), new ItemStack(Items.LEATHER)) != null
                || craft(helper, piece(BBItems.CARCASS_LEGGINGS.get(), "leggings", COW), new ItemStack(Items.LEATHER), new ItemStack(Items.LEATHER)) == null) {
            helper.fail("Boots take one hide, leggings two of one mob");
            return;
        }
        helper.succeed();
    }

    /**
     * Hides of one mob may be different items: two leather and a cow's raw hide cover a cow chestplate, and when rabbit
     * hides replace them all three come back as they went in. A renamed leather is still a cow's; a rabbit hide among
     * cow hides fits nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hidesOfOneMobMayMix(GameTestHelper helper) {
        ItemStack chest = piece(BBItems.CARCASS_CHESTPLATE.get(), "chestplate", COW);
        ItemStack rawCow = Hides.stamp(new ItemStack(BBItems.RAW_HIDE.get()), COW);
        Crafted mixed = craft(helper, chest, new ItemStack(Items.LEATHER), rawCow, new ItemStack(Items.LEATHER));
        if (mixed == null || !armour(mixed.result()).hide().get().entity().equals(Optional.of(COW)) || !armour(mixed.result()).pure(COW)) {
            helper.fail("Two leather and a raw cow hide are all a cow's: they should cover a cow chestplate");
            return;
        }
        Crafted swapped = craft(helper, mixed.result(), new ItemStack(Items.RABBIT_HIDE), new ItemStack(Items.RABBIT_HIDE), new ItemStack(Items.RABBIT_HIDE));
        List<ItemStack> back = swapped == null ? List.of() : swapped.left().stream().filter(stack -> !stack.isEmpty()).toList();
        if (swapped == null || back.size() != 2 || back.stream().noneMatch(stack -> stack.is(Items.LEATHER) && stack.getCount() == 2)
                || back.stream().noneMatch(stack -> stack.getCount() == 1 && ItemStack.isSameItemSameComponents(stack, rawCow))) {
            helper.fail("Rabbit hides replacing them should give back the two leather and the raw cow hide as they went in: " + back);
            return;
        }
        ItemStack named = new ItemStack(Items.LEATHER);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Old Boot"));
        if (craft(helper, piece(BBItems.CARCASS_LEGGINGS.get(), "leggings", COW), new ItemStack(Items.LEATHER), named) == null
                || craft(helper, chest, new ItemStack(Items.LEATHER), rawCow, new ItemStack(Items.RABBIT_HIDE)) != null) {
            helper.fail("A renamed leather is still a cow's hide; a rabbit hide among cow hides should fit nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * Skinning a parrot gives feathers stamped as the parrot's (the data map says feathers are a chicken's); they cover
     * parrot boots with the parrot's own hide, keeping them all parrot, and come back as the parrot's. A chicken's
     * feathers need no stamp, so they still stack with any other feathers.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void skinnedParrotFeathersAreTheParrots(GameTestHelper helper) {
        ItemStack parrot = skin(helper, PARROT).stream().filter(stack -> stack.is(Items.FEATHER)).findFirst().orElse(ItemStack.EMPTY);
        ItemStack chicken = skin(helper, CHICKEN).stream().filter(stack -> stack.is(Items.FEATHER)).findFirst().orElse(ItemStack.EMPTY);
        Source stamp = parrot.get(BBDataComponents.SOURCE.get());
        if (stamp == null || !stamp.entity().equals(PARROT) || chicken.isEmpty() || chicken.has(BBDataComponents.SOURCE.get())) {
            helper.fail("A parrot's feathers should be stamped as the parrot's, a chicken's left plain: " + parrot.getComponents() + " " + chicken.getComponents());
            return;
        }
        parrot.setCount(1);
        Crafted fitted = craft(helper, piece(BBItems.CARCASS_BOOTS.get(), "boots", PARROT), parrot);
        if (fitted == null || !armour(fitted.result()).hide().get().entity().equals(Optional.of(PARROT)) || !armour(fitted.result()).pure(PARROT)) {
            helper.fail("A parrot's feathers should cover parrot boots with the parrot's hide, keeping them all parrot");
            return;
        }
        Crafted swapped = craft(helper, fitted.result(), new ItemStack(Items.FEATHER));
        if (swapped == null || !ItemStack.isSameItemSameComponents(swapped.leftOver(), parrot)
                || !armour(swapped.result()).hide().get().entity().equals(Optional.of(CHICKEN))) {
            helper.fail("Plain feathers (a chicken's) should replace the parrot's, which come back stamped as the parrot's");
            return;
        }
        helper.succeed();
    }

    /**
     * A heart cut out of a cow's body on the Surgery Table is stamped as the cow's; fitted into a cow chestplate,
     * then swapped for the cow's lungs, the heart comes back as it was. Eyes are not for chestplates, a heart not
     * for helmets, and a player's own heart (no mob on it) fits nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fittingReturnsOldOrgan(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BBBlocks.SURGERY_TABLE.getDefaultState());
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 3)));
        Player surgeon = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack body = new ItemStack(BBItems.CARCASS_PIECE.get());
        body.set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(COW, com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(COW).orElseThrow().root().name(),
                ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of(), 1.0F, false, Map.of(), 0.0F, 0.0F, 0.0F, false));
        table.put(body);
        Surgery.harvest(helper.getLevel(), surgeon, table, new ItemStack(BBItems.CLEAVER.get()));
        ItemStack heart = surgeon.getInventory().items.stream().filter(s -> s.is(BBItems.HEART.get())).findFirst().orElse(ItemStack.EMPTY);
        Source source = SeveredLimbItem.source(heart);
        if (source == null || !source.entity().equals(COW) || !source.part().equals("torso") || !heart.getHoverName().getString().contains("Cow")) {
            helper.fail("A heart cut out of a cow should be named and stamped as the cow's: " + heart.getComponents());
            return;
        }
        ItemStack chest = piece(BBItems.CARCASS_CHESTPLATE.get(), "chestplate", COW);
        Crafted withHeart = craft(helper, chest, heart);
        if (withHeart == null || !armour(withHeart.result()).organ().equals(Optional.of(new CarcassArmour.Organ(bb("heart"), COW, false)))
                || !withHeart.leftOver().isEmpty() || !armour(withHeart.result()).pure(COW)) {
            helper.fail("The cow's heart should fit a cow chestplate, which stays all cow");
            return;
        }
        Crafted withLungs = craft(helper, withHeart.result(), BBItems.LUNGS.get().of(COW, false));
        ItemStack back = withLungs == null ? ItemStack.EMPTY : withLungs.leftOver();
        if (withLungs == null || !armour(withLungs.result()).organ().get().organ().equals(bb("lungs")) || !back.is(BBItems.HEART.get())
                || !ItemStack.isSameItemSameComponents(back, heart)) {
            helper.fail("The lungs should take the heart's place, and the heart come back as it was: " + back.getComponents());
            return;
        }
        if (craft(helper, chest, BBItems.EYE.get().of(COW, false)) != null
                || craft(helper, piece(BBItems.CARCASS_HELMET.get(), "helmet", COW), heart) != null
                || craft(helper, piece(BBItems.CARCASS_HELMET.get(), "helmet", COW), BBItems.EYE.get().of(COW, false)) == null
                || craft(helper, piece(BBItems.CARCASS_BOOTS.get(), "boots", COW), BBItems.STOMACH.get().of(COW, false)) != null
                || craft(helper, piece(BBItems.CARCASS_LEGGINGS.get(), "leggings", COW), BBItems.STOMACH.get().of(COW, false)) == null) {
            helper.fail("Eyes go in helmets, hearts in chestplates, a stomach in leggings but not boots");
            return;
        }
        if (craft(helper, chest, BBItems.HEART.get().of(Component.literal("Steve"))) != null || craft(helper, chest, heart, new ItemStack(Items.LEATHER)) != null) {
            helper.fail("A player's own heart, or an organ and a hide together, should fit nothing");
            return;
        }
        Crafted pig = craft(helper, chest, BBItems.HEART.get().of(PIG, false));
        if (pig == null || armour(pig.result()).pure(COW)) {
            helper.fail("A pig's heart in a cow chestplate should stop it counting as all cow");
            return;
        }
        helper.succeed();
    }

    /** A live cow on the Surgery Table gives up its lungs stamped as the cow's, as a carcass's are, and they fit a cow chestplate. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void organFromALiveMobFits(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BBBlocks.SURGERY_TABLE.getDefaultState());
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(new BlockPos(3, 2, 3)));
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(6, 2, 6));
        cow.setNoAi(true);
        Player surgeon = helper.makeMockPlayer(GameType.SURVIVAL);
        SurgeryTableBlock.lieDown(helper.getLevel(), table.getBlockPos(), cow);
        if (Surgery.operate(helper.getLevel(), cow, surgeon, table, BodyPart.LUNGS) != Surgery.Action.TAKE_OFF) {
            helper.fail("A Cleaver should take a live cow's lungs out");
            return;
        }
        ItemStack lungs = surgeon.getInventory().items.stream().filter(s -> s.is(BBItems.LUNGS.get())).findFirst().orElse(ItemStack.EMPTY);
        Source source = SeveredLimbItem.source(lungs);
        if (source == null || !source.entity().equals(COW) || !source.part().equals("torso") || !lungs.getHoverName().getString().contains("Cow")) {
            helper.fail("Lungs taken out of a live cow should be named and stamped as the cow's: " + lungs.getComponents());
            return;
        }
        Crafted fitted = craft(helper, piece(BBItems.CARCASS_CHESTPLATE.get(), "chestplate", COW), lungs);
        if (fitted == null || !armour(fitted.result()).organ().equals(Optional.of(new CarcassArmour.Organ(bb("lungs"), COW, false)))) {
            helper.fail("A live cow's lungs should fit a cow chestplate");
            return;
        }
        helper.succeed();
    }

    /**
     * A Blood Diamond will not go on boots that have no tier; a Blood Steel Ingot makes them tier 1 (hide plate's
     * 1 armour plus 1, toughness 0.5, durability 156 x 1.5), then the diamond tier 2 (1 plus 2, toughness 2,
     * durability 156 x 2.5), keeping their wear. A tier cannot be fitted twice.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tierNeedsPreviousTier(GameTestHelper helper) {
        ItemStack boots = piece(BBItems.CARCASS_BOOTS.get(), "boots", COW);
        if (craft(helper, boots, new ItemStack(BBItems.BLOOD_DIAMOND.get())) != null || craft(helper, boots, new ItemStack(BBItems.SOUL_NETHERITE_INGOT.get())) != null) {
            helper.fail("Tier 0 boots should refuse a Blood Diamond and a Soul Netherite Ingot");
            return;
        }
        Crafted steel = craft(helper, boots, new ItemStack(BBItems.BLOOD_STEEL_INGOT.get()));
        ItemStack t1 = steel == null ? ItemStack.EMPTY : steel.result();
        if (steel == null || armour(t1).tier() != 1 || attribute(t1, EquipmentSlot.FEET, Attributes.ARMOR) != 2.0
                || attribute(t1, EquipmentSlot.FEET, Attributes.ARMOR_TOUGHNESS) != 0.5 || t1.getMaxDamage() != 234) {
            helper.fail("A Blood Steel Ingot should make the boots tier 1: 2 armour, 0.5 toughness, durability 234: "
                    + (steel == null ? "nothing" : attribute(t1, EquipmentSlot.FEET, Attributes.ARMOR) + " " + t1.getMaxDamage()));
            return;
        }
        if (craft(helper, t1, new ItemStack(BBItems.BLOOD_STEEL_INGOT.get())) != null) {
            helper.fail("Tier 1 boots should not take a second Blood Steel Ingot");
            return;
        }
        t1.setDamageValue(10);
        Crafted diamond = craft(helper, t1, new ItemStack(BBItems.BLOOD_DIAMOND.get()));
        ItemStack t2 = diamond == null ? ItemStack.EMPTY : diamond.result();
        if (diamond == null || armour(t2).tier() != 2 || attribute(t2, EquipmentSlot.FEET, Attributes.ARMOR) != 3.0
                || attribute(t2, EquipmentSlot.FEET, Attributes.ARMOR_TOUGHNESS) != 2.0 || t2.getMaxDamage() != 390 || t2.getDamageValue() != 10) {
            helper.fail("A Blood Diamond should make tier 1 boots tier 2: 3 armour, 2 toughness, durability 390, wear kept: "
                    + (diamond == null ? "nothing" : attribute(t2, EquipmentSlot.FEET, Attributes.ARMOR) + " " + t2.getMaxDamage() + " " + t2.getDamageValue()));
            return;
        }
        helper.succeed();
    }

    /**
     * A Soul Netherite Ingot makes a tier 2 chestplate tier 3: fire resistant, 4 + 4 armour, toughness 3,
     * knockback resistance 0.1, durability 192 x 3.3. A whole cow set at tier 3 is netherite's 20 armour and 12
     * toughness.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void soulNetheriteIsFireResistant(GameTestHelper helper) {
        ItemStack chest = CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_CHESTPLATE.get()), CarcassArmour.of("chestplate", COW, false).withTier(2), PartsData.SERVER);
        if (chest.has(DataComponents.FIRE_RESISTANT)) {
            helper.fail("A tier 2 chestplate should burn");
            return;
        }
        Crafted soul = craft(helper, chest, new ItemStack(BBItems.SOUL_NETHERITE_INGOT.get()));
        ItemStack t3 = soul == null ? ItemStack.EMPTY : soul.result();
        if (soul == null || armour(t3).tier() != 3 || !t3.has(DataComponents.FIRE_RESISTANT) || t3.canBeHurtBy(helper.getLevel().damageSources().lava())
                || attribute(t3, EquipmentSlot.CHEST, Attributes.ARMOR) != 8.0 || attribute(t3, EquipmentSlot.CHEST, Attributes.ARMOR_TOUGHNESS) != 3.0
                || Math.abs(attribute(t3, EquipmentSlot.CHEST, Attributes.KNOCKBACK_RESISTANCE) - 0.1) > 1.0E-6 || t3.getMaxDamage() != 634) {
            helper.fail("Soul netherite should make the chestplate tier 3 and fire resistant, 8 armour, 3 toughness, 0.1 knockback, 634 durability: "
                    + (soul == null ? "nothing" : attribute(t3, EquipmentSlot.CHEST, Attributes.ARMOR) + " " + t3.getMaxDamage()));
            return;
        }
        Map<EquipmentSlot, ItemStack> set = Map.of(
                EquipmentSlot.HEAD, CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_HELMET.get()), CarcassArmour.of("helmet", COW, false).withTier(3), PartsData.SERVER),
                EquipmentSlot.CHEST, t3,
                EquipmentSlot.LEGS, CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_LEGGINGS.get()), CarcassArmour.of("leggings", COW, false).withTier(3), PartsData.SERVER),
                EquipmentSlot.FEET, CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_BOOTS.get()), CarcassArmour.of("boots", COW, false).withTier(3), PartsData.SERVER));
        double armour = 0.0;
        double toughness = 0.0;
        for (Map.Entry<EquipmentSlot, ItemStack> e : set.entrySet()) {
            armour += attribute(e.getValue(), e.getKey(), Attributes.ARMOR);
            toughness += attribute(e.getValue(), e.getKey(), Attributes.ARMOR_TOUGHNESS);
        }
        if (armour != 20.0 || toughness != 12.0) {
            helper.fail("A tier 3 cow set should be 20 armour and 12 toughness: " + armour + ", " + toughness);
            return;
        }
        helper.succeed();
    }

    /**
     * A carcass chestplate crafted with an iron backtank of blood carries its tier and blood. A Spout fills it (it
     * takes 3000 mB more, to the tank's 4000) and an Item Drain empties it a bucket at a time; the chestplate with
     * no tank has no fluid handler at all. Strapped, its armour is the better of the two: the iron tank's 6 over hide
     * plate's 4. Mechanical Crafters strap tanks on too.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spoutFillsStrappedTank(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack chest = piece(BBItems.CARCASS_CHESTPLATE.get(), "chestplate", COW);
        ItemStack strapped = strapped(helper, 1000);
        if (strapped.get(BBDataComponents.STRAPPED_TANK.get()) != BacktankTier.IRON || FluidBacktankItem.fluid(strapped).getAmount() != 1000
                || !FluidBacktankItem.fluid(strapped).is(BBFluids.blood()) || armour(strapped) == null) {
            helper.fail("Strapping an iron backtank of 1000 mB of blood should put its tier and blood on the chestplate: " + strapped.getComponents());
            return;
        }
        if (FillingBySpout.canItemBeFilled(level, chest) || GenericItemEmptying.canItemBeEmptied(level, chest)
                || chest.getCapability(Capabilities.FluidHandler.ITEM) != null || strapped.getCapability(Capabilities.FluidHandler.ITEM) == null) {
            helper.fail("A chestplate with no tank should have no fluid handler at all, and be nothing to a Spout or an Item Drain; strapped, it should have one");
            return;
        }
        FluidStack spout = new FluidStack(BBFluids.blood(), 5000);
        if (!FillingBySpout.canItemBeFilled(level, strapped) || FillingBySpout.getRequiredAmountForItem(level, strapped, spout.copy()) != 3000) {
            helper.fail("A Spout should see room for 3000 mB in the strapped iron tank");
            return;
        }
        ItemStack filled = FillingBySpout.fillItem(level, 3000, strapped.copy(), spout);
        if (!filled.is(BBItems.CARCASS_CHESTPLATE.get()) || FluidBacktankItem.fluid(filled).getAmount() != 4000 || spout.getAmount() != 2000 || armour(filled) == null) {
            helper.fail("The Spout should fill the strapped tank to 4000 mB and leave the chestplate as it was: " + filled.getComponents());
            return;
        }
        var drained = GenericItemEmptying.emptyItem(level, filled.copy(), false);
        if (!GenericItemEmptying.canItemBeEmptied(level, filled) || drained.getFirst().getAmount() != 1000 || FluidBacktankItem.fluid(drained.getSecond()).getAmount() != 3000) {
            helper.fail("An Item Drain should take a bucket out of the strapped tank");
            return;
        }
        if (attribute(strapped, EquipmentSlot.CHEST, Attributes.ARMOR) != 6.0 || attribute(chest, EquipmentSlot.CHEST, Attributes.ARMOR) != 4.0) {
            helper.fail("Strapped, the chestplate should have the iron tank's 6 armour over its own 4: " + attribute(strapped, EquipmentSlot.CHEST, Attributes.ARMOR));
            return;
        }
        ItemStack byCrafters = crafters(helper, chest, tank(BacktankTier.IRON, 500));
        if (byCrafters == null || byCrafters.get(BBDataComponents.STRAPPED_TANK.get()) != BacktankTier.IRON || FluidBacktankItem.fluid(byCrafters).getAmount() != 500) {
            helper.fail("Mechanical Crafters should strap a tank on too");
            return;
        }
        helper.succeed();
    }

    /** A Flesh Arm runs on the blood in a backtank strapped to the worn chestplate, and drinks it; with no tank strapped on it does not work. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void implantDrainsStrappedTank(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BodyPart arm = BodyEffects.armFor(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        Body body = BodyEffects.body(player);
        body.fit(arm, new ItemStack(BBItems.FLESH_ARM.get()));
        player.setItemSlot(EquipmentSlot.CHEST, piece(BBItems.CARCASS_CHESTPLATE.get(), "chestplate", COW));
        if (!FluidBacktankItem.wornBy(player).isEmpty() || body.works(arm, player)) {
            helper.fail("A chestplate with no tank on is no tank, and the Flesh Arm should not work");
            return;
        }
        player.setItemSlot(EquipmentSlot.CHEST, strapped(helper, 1000));
        if (FluidBacktankItem.wornBy(player) != player.getItemBySlot(EquipmentSlot.CHEST) || FluidBacktankItem.capacity(FluidBacktankItem.wornBy(player)) != 4000
                || !body.works(arm, player)) {
            helper.fail("A strapped chestplate should be the worn tank, holding 4 buckets, and run the Flesh Arm");
            return;
        }
        BodyEffects.drain(player);
        BodyEffects.drain(player);
        if (FluidBacktankItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount() != 998) {
            helper.fail("Two seconds of the Flesh Arm should drink 2 mB from the strapped tank: "
                    + FluidBacktankItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount());
            return;
        }
        helper.succeed();
    }

    /**
     * A strapped chestplate crafted alone gives the tank back with its blood, and the chestplate stays in the grid
     * without it. It cannot take a second tank; Mechanical Crafters, which give back nothing, do not unstrap.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unstrapReturnsTank(GameTestHelper helper) {
        ItemStack strapped = strapped(helper, 1234);
        Crafted off = craft(helper, strapped);
        if (off == null || !off.result().is(BBItems.backtank(BacktankTier.IRON)) || FluidBacktankItem.fluid(off.result()).getAmount() != 1234
                || !FluidBacktankItem.fluid(off.result()).is(BBFluids.blood())) {
            helper.fail("Crafted alone, the strapped chestplate should give the iron tank back with its 1234 mB of blood");
            return;
        }
        ItemStack left = off.leftOver();
        if (!left.is(BBItems.CARCASS_CHESTPLATE.get()) || armour(left) == null || left.has(BBDataComponents.STRAPPED_TANK.get())
                || !FluidBacktankItem.fluid(left).isEmpty()) {
            helper.fail("The chestplate should stay in the grid, with no tank and no fluid: " + left.getComponents());
            return;
        }
        if (craft(helper, strapped, tank(BacktankTier.IRON, 0)) != null) {
            helper.fail("A chestplate with a tank on should not take a second");
            return;
        }
        if (crafters(helper, strapped) != null) {
            helper.fail("Mechanical Crafters should not take a tank off (the chestplate would be lost)");
            return;
        }
        helper.succeed();
    }

    /** A strapped chestplate worn down breaks, but its tank never breaks with it: it comes off into the wearer's inventory, blood and all. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void brokenChestplateGivesTankBack(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack chest = strapped(helper, 1000);
        chest.setDamageValue(chest.getMaxDamage() - 1);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.getItemBySlot(EquipmentSlot.CHEST).hurtAndBreak(4, player, EquipmentSlot.CHEST);
        ItemStack tank = player.getInventory().items.stream().filter(s -> s.is(BBItems.backtank(BacktankTier.IRON))).findFirst().orElse(ItemStack.EMPTY);
        if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || FluidBacktankItem.fluid(tank).getAmount() != 1000) {
            helper.fail("The chestplate should break and its iron tank come back with its 1000 mB of blood: " + tank);
            return;
        }
        helper.succeed();
    }

    /**
     * A strapped chestplate burnt up in lava as an item lets its tank fall free: a soul netherite tank, which does not
     * burn, is left with its fluid though the tier 0 chestplate burns.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void burntChestplateLetsTankFree(GameTestHelper helper) {
        var level = helper.getLevel();
        ItemStack soul = new ItemStack(BBItems.backtank(BacktankTier.SOUL_NETHERITE));
        FluidBacktankItem.setFluid(soul, new FluidStack(BBFluids.soulBlood(), 5000));
        Crafted crafted = craft(helper, piece(BBItems.CARCASS_CHESTPLATE.get(), "chestplate", COW), soul);
        if (crafted == null || crafted.result().has(DataComponents.FIRE_RESISTANT)) {
            helper.fail("A soul netherite tank should strap onto a tier 0 chestplate, which still burns");
            return;
        }
        Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        ItemEntity dropped = new ItemEntity(level, at.x, at.y, at.z, crafted.result());
        level.addFreshEntity(dropped);
        dropped.hurt(level.damageSources().lava(), 100.0F);
        ItemStack tank = level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(2.0)).stream().map(ItemEntity::getItem)
                .filter(s -> s.is(BBItems.backtank(BacktankTier.SOUL_NETHERITE))).findFirst().orElse(ItemStack.EMPTY);
        if (!dropped.isRemoved() || FluidBacktankItem.fluid(tank).getAmount() != 5000 || tank.canBeHurtBy(level.damageSources().lava())) {
            helper.fail("The chestplate should burn and leave its soul netherite tank, with its 5000 mB, to float in the lava: " + tank);
            return;
        }
        helper.succeed();
    }

    /**
     * Two pieces never combine as vanilla combines worn tools: two strapped chestplates in a crafting grid make nothing
     * (vanilla would make a blank chestplate and lose both tanks), and a grindstone will not merge tier 0 and tier 3 boots.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void twoPiecesNeverCombine(GameTestHelper helper) {
        ItemStack first = strapped(helper, 1000);
        ItemStack second = strapped(helper, 2000);
        first.setDamageValue(50);
        second.setDamageValue(60);
        if (craft(helper, first, second) != null) {
            helper.fail("Two strapped chestplates in a crafting grid should make nothing");
            return;
        }
        ItemStack plain = piece(BBItems.CARCASS_BOOTS.get(), "boots", COW);
        ItemStack tiered = CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_BOOTS.get()), CarcassArmour.of("boots", COW, false).withTier(3), PartsData.SERVER);
        plain.setDamageValue(100);
        tiered.setDamageValue(10);
        GrindstoneMenu grindstone = new GrindstoneMenu(0, helper.makeMockPlayer(GameType.SURVIVAL).getInventory());
        grindstone.getSlot(0).set(plain);
        grindstone.getSlot(1).set(tiered);
        if (!grindstone.getSlot(2).getItem().isEmpty()) {
            helper.fail("A grindstone should not merge two pieces: " + grindstone.getSlot(2).getItem().getComponents());
            return;
        }
        helper.succeed();
    }

    /** Only scraps of the piece's own mob mend it on an anvil: not a raw hide or a heart stamped as that mob's. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void onlyScrapsMendOnAnvil(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack boots = piece(BBItems.CARCASS_BOOTS.get(), "boots", COW);
        boots.setDamageValue(100);
        Map<String, ItemStack> tries = Map.of("cow leg scraps", scraps(COW, "leg"), "a raw cow hide", Hides.stamp(new ItemStack(BBItems.RAW_HIDE.get()), COW),
                "a cow's heart", BBItems.HEART.get().of(COW, false));
        for (Map.Entry<String, ItemStack> e : tries.entrySet()) {
            AnvilMenu anvil = new AnvilMenu(0, player.getInventory());
            anvil.getSlot(0).set(boots.copy());
            anvil.getSlot(1).set(e.getValue().copy());
            ItemStack out = anvil.getSlot(2).getItem();
            boolean mends = e.getValue().is(BBItems.SCRAPS.get());
            if (mends != (!out.isEmpty() && out.getDamageValue() < 100 && armour(out) != null)) {
                helper.fail("Cow boots on an anvil with " + e.getKey() + (mends ? " should be mended" : " should not be") + ": " + out.getComponents());
                return;
            }
        }
        helper.succeed();
    }

    /**
     * A full cow set is the Herd Beast (Hauler III); a rabbit hide on the boots breaks it, with no penalty. A
     * cow's own hide, or a plain one, keeps it.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mixedHideBreaksSet(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, piece(BBItems.CARCASS_HELMET.get(), "helmet", COW));
        player.setItemSlot(EquipmentSlot.CHEST, piece(BBItems.CARCASS_CHESTPLATE.get(), "chestplate", COW));
        player.setItemSlot(EquipmentSlot.LEGS, piece(BBItems.CARCASS_LEGGINGS.get(), "leggings", COW));
        ItemStack boots = piece(BBItems.CARCASS_BOOTS.get(), "boots", COW);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        ActiveTraits traits = ActiveTraits.rebuild(player);
        if (traits.set().isEmpty() || traits.level(bb("hauler")) != 3) {
            helper.fail("A full cow set should be the Herd Beast: " + traits.entries());
            return;
        }
        player.setItemSlot(EquipmentSlot.FEET, craft(helper, boots, new ItemStack(Items.RABBIT_HIDE)).result());
        traits = ActiveTraits.rebuild(player);
        if (traits.set().isPresent() || traits.level(bb("hauler")) != 0 || traits.level(bb("meek")) != 0) {
            helper.fail("A rabbit hide on the boots should break the set, bonus and drawback: " + traits.entries());
            return;
        }
        player.setItemSlot(EquipmentSlot.FEET, craft(helper, boots, new ItemStack(Items.LEATHER)).result());
        if (ActiveTraits.rebuild(player).set().isEmpty()) {
            helper.fail("Leather is a cow's hide: the set should hold");
            return;
        }
        player.setItemSlot(EquipmentSlot.FEET, craft(helper, boots, new ItemStack(BBItems.RAW_HIDE.get())).result());
        if (ActiveTraits.rebuild(player).set().isEmpty()) {
            helper.fail("A plain raw hide comes from no mob: the set should hold");
            return;
        }
        helper.succeed();
    }

    /**
     * Create's Mechanical Crafters fit a tier and a first hide, the piece keeping what it is made of; a swap that
     * would give a hide back is refused rather than losing the hide.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mechanicalCraftersFitButNeverSwap(GameTestHelper helper) {
        ItemStack boots = piece(BBItems.CARCASS_BOOTS.get(), "boots", COW);
        ItemStack tiered = crafters(helper, boots, new ItemStack(BBItems.BLOOD_STEEL_INGOT.get()));
        if (tiered == null || armour(tiered) == null || armour(tiered).tier() != 1 || !armour(tiered).body().equals(COW)) {
            helper.fail("Mechanical Crafters should make cow boots tier 1");
            return;
        }
        ItemStack covered = crafters(helper, tiered, new ItemStack(Items.RABBIT_HIDE));
        if (covered == null || armour(covered).hide().isEmpty() || armour(covered).tier() != 1) {
            helper.fail("Mechanical Crafters should fit a first hide");
            return;
        }
        if (crafters(helper, covered, new ItemStack(Items.LEATHER)) != null) {
            helper.fail("Mechanical Crafters should refuse a swap, which would lose the old hide");
            return;
        }
        helper.succeed();
    }

    /**
     * The new words have their bloodless wording (docs/PARTS-AND-TRAITS.md section 7.10): plated armour of salvage, a
     * covering, a core and its parts, the organs themselves by the same names; none of it says carcass, scraps, hide,
     * organ, heart, lungs, stomach, eye or blood.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fittingWordsHaveBloodlessWording(GameTestHelper helper) {
        java.util.regex.Pattern bloody = java.util.regex.Pattern.compile("(?i)(?<![a-z])(carcass|scraps?|hides?|organs?|hearts?|lungs|stomachs?|eyes?|blood)(?![a-z])");
        try (var in = BloodAndBones.class.getResourceAsStream("/assets/bloodandbones/lang/en_us.json")) {
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            for (String key : new String[]{"bloodandbones.carcass_armour.hide_plain", "bloodandbones.carcass_armour.organ", "organ.bloodandbones.heart",
                    "organ.bloodandbones.lungs", "organ.bloodandbones.stomach", "organ.bloodandbones.eye",
                    "item.bloodandbones.carcass_chestplate.tooltip.summary", "item.bloodandbones.carcass_chestplate.tooltip.condition2",
                    "item.bloodandbones.carcass_chestplate.tooltip.behaviour2", "item.bloodandbones.carcass_chestplate.tooltip.condition3",
                    "item.bloodandbones.carcass_chestplate.tooltip.behaviour3", "item.bloodandbones.carcass_chestplate.tooltip.behaviour4",
                    "item.bloodandbones.carcass_helmet.tooltip.behaviour3", "item.bloodandbones.carcass_boots.tooltip.behaviour3",
                    "item.bloodandbones.scraps.tooltip.behaviour1", "bloodandbones.jei.carcass_armour.1", "bloodandbones.jei.carcass_armour.2",
                    "bloodandbones.jei.carcass_armour.3", "bloodandbones.jei.backtank.3", "item.bloodandbones.raw_hide.of",
                    "item.bloodandbones.heart.of", "item.bloodandbones.lungs.of", "item.bloodandbones.stomach.of", "item.bloodandbones.eye.of",
                    "item.bloodandbones.heart.tooltip.summary", "bloodandbones.body.heart", "bloodandbones.body.left_eye"}) {
                if (!json.has(key) || !json.has("bloodless." + key)) {
                    helper.fail("No bloodless wording for " + key);
                    return;
                }
                if (bloody.matcher(json.get("bloodless." + key).getAsString()).find()) {
                    helper.fail("The bloodless wording for " + key + " still says " + json.get("bloodless." + key).getAsString());
                    return;
                }
            }
            if (!json.get("bloodless.item.bloodandbones.heart.of").getAsString().equals("%s's Pump")) {
                helper.fail("Bloodless mode should call a cow's heart the Cow's Pump, as the armour's core line does");
                return;
            }
            if (!json.get("bloodless.organ.bloodandbones.heart").getAsString().equals("Pump")
                    || !json.get("bloodless.item.bloodandbones.carcass_chestplate.tooltip.condition3").getAsString().equals("Installing a Core")) {
                helper.fail("Bloodless mode should call a heart a pump, and fitting an organ installing a core");
                return;
            }
        } catch (Exception e) {
            helper.fail("Could not read the language file: " + e);
            return;
        }
        helper.succeed();
    }
}
