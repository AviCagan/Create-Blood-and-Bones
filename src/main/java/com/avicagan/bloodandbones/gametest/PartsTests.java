package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.PartSlot;
import com.avicagan.bloodandbones.parts.PartSlots;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import com.avicagan.bloodandbones.parts.ScrapsItem;
import com.avicagan.bloodandbones.parts.SlotInfo;
import com.avicagan.bloodandbones.parts.Source;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Parts and traits, slice 1: slots, resolution, scraps, carcass armour and its traits on a wearer. */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class PartsTests {
    private static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");
    private static final ResourceLocation RABBIT = ResourceLocation.withDefaultNamespace("rabbit");

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

    /** A crafting grid of this shape, '.' empty, other letters these scraps. */
    private static Optional<ItemStack> craft(GameTestHelper helper, String[] rows, java.util.Map<Character, ItemStack> key) {
        List<ItemStack> items = new ArrayList<>();
        for (String row : rows) {
            for (char c : row.toCharArray()) {
                items.add(c == '.' ? ItemStack.EMPTY : key.get(c).copy());
            }
        }
        CraftingInput input = CraftingInput.of(rows[0].length(), rows.length, items);
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(holder -> holder.value().assemble(input, helper.getLevel().registryAccess()));
    }

    /** The bones of a cow and a rabbit fall into the right slots, front and hind legs told apart, by name alone. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void partSlotsFromNames(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        Rig cow = RigManager.forEntity(COW).orElseThrow();
        Rig rabbit = RigManager.forEntity(RABBIT).orElseThrow();
        SlotInfo body = PartSlots.of(store, COW, cow, "body");
        SlotInfo head = PartSlots.of(store, COW, cow, "head");
        SlotInfo front = PartSlots.of(store, COW, cow, "right_front_leg");
        SlotInfo haunch = PartSlots.of(store, RABBIT, rabbit, "left_haunch");
        if (body.slot() != PartSlot.TORSO || head.slot() != PartSlot.HEAD || front.slot() != PartSlot.LEG || !front.sub().equals("front")
                || haunch.slot() != PartSlot.LEG || !haunch.key().equals("leg.hind")) {
            helper.fail("Slots wrong: " + body + " " + head + " " + front + " " + haunch);
            return;
        }
        helper.succeed();
    }

    /** A cow resolves as quadruped, grazer and its own file; a rabbit as quadruped, small prey, the snow overlay and its own file. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cowAndRabbitResolve(GameTestHelper helper) {
        ResolvedMob cow = PartsData.SERVER.resolve(COW, false);
        ResolvedMob rabbit = PartsData.SERVER.resolve(RABBIT, false);
        if (!cow.layers().equals(List.of(bb("quadruped"), bb("grazer"), COW)) || !cow.material().equals(bb("hide_plate"))
                || level(cow.armourTraits("leg", "boots"), "hooves") != 1 || level(cow.armourTraits("leg", "boots"), "swift") != 1
                || level(cow.armourTraits("leg", "leggings"), "hooves") != 0) {
            helper.fail("Cow resolved wrong: " + cow.layers() + " " + cow.material() + " " + cow.armourTraits("leg", "boots"));
            return;
        }
        if (!rabbit.layers().equals(List.of(bb("quadruped"), bb("small_prey"), bb("snow"), RABBIT)) || !rabbit.material().equals(bb("sinew"))
                || level(rabbit.armourTraits("leg", "leggings"), "springy") != 2 || level(rabbit.armourTraits("leg", "boots"), "fall_guard") != 2
                || rabbit.fullSet().isEmpty() || !rabbit.fullSet().get().name().equals("set.bloodandbones.warren")
                || level(rabbit.fullSet().get().bonus(), "springy") != 4) {
            helper.fail("Rabbit resolved wrong: " + rabbit.layers() + " " + rabbit.armourTraits("leg", "leggings") + " " + rabbit.fullSet());
            return;
        }
        helper.succeed();
    }

    /** Scraps by volume times density: a cow's torso about 13, half again skinned, half rotten. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void scrapCountsByVolume(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        Rig cow = RigManager.forEntity(COW).orElseThrow();
        float torso = CarcassButchery.scrapCount(store, COW, false, cow, "body", false, 1.0F);
        float skinned = CarcassButchery.scrapCount(store, COW, false, cow, "body", true, 1.0F);
        float rotten = CarcassButchery.scrapCount(store, COW, false, cow, "body", false, 0.1F);
        float head = CarcassButchery.scrapCount(store, COW, false, cow, "head", false, 1.0F);
        if (Math.abs(torso - 12.66F) > 0.1F || Math.abs(skinned - torso * 1.5F) > 1.0E-3F || Math.abs(rotten - torso * 0.5F) > 1.0E-3F
                || Math.abs(head - 2.25F) > 0.05F) {
            helper.fail("Scrap counts wrong: torso " + torso + ", skinned " + skinned + ", rotten " + rotten + ", head " + head);
            return;
        }
        helper.succeed();
    }

    /** Four cow leg scraps make Cow Hide Boots: hide plate's armour, durability 12 x 13, the cow's boot traits. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void craftCowBoots(GameTestHelper helper) {
        Optional<ItemStack> boots = craft(helper, new String[]{"L.L", "L.L"}, java.util.Map.of('L', scraps(COW, "leg")));
        CarcassArmour armour = boots.map(CarcassArmourItem::armour).orElse(null);
        if (boots.isEmpty() || !boots.get().is(BBItems.CARCASS_BOOTS.get()) || armour == null || !armour.body().equals(COW)
                || boots.get().getOrDefault(DataComponents.MAX_DAMAGE, 0) != 12 * 13) {
            helper.fail("Four cow leg scraps should make cow boots of durability 156: " + boots.map(s -> s + " " + s.getComponents()).orElse("nothing"));
            return;
        }
        double[] armourPoints = {0.0};
        double[] health = {0.0};
        boots.get().getAttributeModifiers().forEach(EquipmentSlot.FEET, (attribute, modifier) -> {
            if (attribute.is(Attributes.ARMOR)) {
                armourPoints[0] += modifier.amount();
            }
            if (attribute.is(Attributes.MAX_HEALTH)) {
                health[0] += modifier.amount();
            }
        });
        if (armourPoints[0] != 1.0 || health[0] != 0.5) {
            helper.fail("Hide plate boots should give 1 armour and the quirk's half heart: " + armourPoints[0] + ", " + health[0]);
            return;
        }
        helper.succeed();
    }

    /** Scraps of two mobs in one piece's body make nothing; nor do the wrong part's scraps. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mixedHelmetRefused(GameTestHelper helper) {
        java.util.Map<Character, ItemStack> key = new java.util.HashMap<>();
        key.put('H', scraps(COW, "head"));
        key.put('R', scraps(RABBIT, "head"));
        key.put('L', scraps(COW, "leg"));
        if (craft(helper, new String[]{"HHH", "H.R"}, key).isPresent()) {
            helper.fail("A helmet of cow and rabbit heads should not craft");
            return;
        }
        if (craft(helper, new String[]{"HHH", "H.L"}, key).isPresent()) {
            helper.fail("A helmet with a leg scrap in it should not craft");
            return;
        }
        if (craft(helper, new String[]{"HHH", "H.H"}, key).isEmpty()) {
            helper.fail("Five cow head scraps should make a helmet");
            return;
        }
        helper.succeed();
    }

    /** Rabbit leggings (leg scraps, and more of the rabbit's legs for the hips) raise the jump: springy II. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rabbitLeggingsRaiseJump(GameTestHelper helper) {
        Optional<ItemStack> leggings = craft(helper, new String[]{"PPP", "L.L", "L.L"}, java.util.Map.of('L', scraps(RABBIT, "leg"), 'P', scraps(RABBIT, "leg")));
        if (leggings.isEmpty() || !leggings.get().is(BBItems.CARCASS_LEGGINGS.get())) {
            helper.fail("Seven rabbit leg scraps should make leggings");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double jump = player.getAttributeValue(Attributes.JUMP_STRENGTH);
        player.setItemSlot(EquipmentSlot.LEGS, leggings.get());
        ActiveTraits traits = ActiveTraits.rebuild(player);
        if (traits.level(bb("springy")) != 2 || Math.abs(player.getAttributeValue(Attributes.JUMP_STRENGTH) - (jump + 0.16)) > 1.0E-4) {
            helper.fail("Rabbit leggings should give springy II, +0.16 jump: " + traits.entries() + ", " + player.getAttributeValue(Attributes.JUMP_STRENGTH));
            return;
        }
        player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        ActiveTraits.rebuild(player);
        if (Math.abs(player.getAttributeValue(Attributes.JUMP_STRENGTH) - jump) > 1.0E-6) {
            helper.fail("Taking them off should take the jump away");
            return;
        }
        helper.succeed();
    }

    /** The same trait counts once at its highest level, unless it sums (swift from two rabbit pieces is Swift II). */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sameTraitCountsOnce(GameTestHelper helper) {
        List<TraitList.Resolved> union = TraitList.union(List.of(new TraitList.Resolved(bb("steady"), 1)), List.of(new TraitList.Resolved(bb("steady"), 2)));
        if (union.size() != 1 || union.get(0).level() != 2) {
            helper.fail("The same trait twice should count once, at its highest: " + union);
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.LEGS, piece(BBItems.CARCASS_LEGGINGS.get(), "leggings", RABBIT));
        player.setItemSlot(EquipmentSlot.FEET, piece(BBItems.CARCASS_BOOTS.get(), "boots", RABBIT));
        ActiveTraits traits = ActiveTraits.rebuild(player);
        if (traits.level(bb("swift")) != 2 || traits.level(bb("springy")) != 2) {
            helper.fail("Swift sums over the two pieces, springy does not: " + traits.entries());
            return;
        }
        helper.succeed();
    }

    private static ItemStack piece(net.minecraft.world.item.Item item, String piece, ResourceLocation mob) {
        return CarcassArmourItem.make(new ItemStack(item), CarcassArmour.of(piece, mob, false), PartsData.SERVER);
    }

    /** Four pieces of one mob are a full set, bonus and drawback together; one piece of another mob, none. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fullSetOfOneMob(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, piece(BBItems.CARCASS_HELMET.get(), "helmet", RABBIT));
        player.setItemSlot(EquipmentSlot.CHEST, piece(BBItems.CARCASS_CHESTPLATE.get(), "chestplate", RABBIT));
        player.setItemSlot(EquipmentSlot.LEGS, piece(BBItems.CARCASS_LEGGINGS.get(), "leggings", RABBIT));
        player.setItemSlot(EquipmentSlot.FEET, piece(BBItems.CARCASS_BOOTS.get(), "boots", RABBIT));
        ActiveTraits traits = ActiveTraits.rebuild(player);
        if (traits.set().isEmpty() || traits.level(bb("springy")) != 4 || traits.level(bb("frail")) != 2 || traits.level(bb("prey")) != 1) {
            helper.fail("A full rabbit set should add Warren's bonus and its drawback: " + traits.entries());
            return;
        }
        player.setItemSlot(EquipmentSlot.HEAD, piece(BBItems.CARCASS_HELMET.get(), "helmet", COW));
        traits = ActiveTraits.rebuild(player);
        if (traits.set().isPresent() || traits.level(bb("frail")) != 0) {
            helper.fail("A cow helmet should break the rabbit set, with no penalty: " + traits.entries());
            return;
        }
        helper.succeed();
    }

    /** Rabbit boots (fall guard II) halve fall damage. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fallGuardSoftensFalls(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.FEET, piece(BBItems.CARCASS_BOOTS.get(), "boots", RABBIT));
        ActiveTraits.rebuild(player);
        float health = player.getHealth();
        double safe = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        player.causeFallDamage((float) safe + 8.0F, 1.0F, player.damageSources().fall());
        float taken = health - player.getHealth();
        if (Math.abs(taken - 4.0F) > 0.01F) {
            helper.fail("Eight blocks past safe should hurt 8, halved to 4 by fall guard II: " + taken);
            return;
        }
        helper.succeed();
    }

    /** Every new name has a bloodless wording. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bloodlessNames(GameTestHelper helper) {
        try (var in = BloodAndBones.class.getResourceAsStream("/assets/bloodandbones/lang/en_us.json")) {
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            for (String key : new String[]{"item.bloodandbones.scraps.named", "item.bloodandbones.carcass_armour.named"}) {
                if (!json.has(key) || !json.has("bloodless." + key)) {
                    helper.fail("No bloodless wording for " + key);
                    return;
                }
            }
        } catch (Exception e) {
            helper.fail("Could not read the language file: " + e);
            return;
        }
        helper.succeed();
    }
}
