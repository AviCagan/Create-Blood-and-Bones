package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.network.OrganActivatePayload;
import com.avicagan.bloodandbones.parts.Activation;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.Trait;
import com.avicagan.bloodandbones.parts.TraitEffects;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The trait engine (docs/ARCHITECTURE-PROPOSAL.md section 15.8): minions get their parts' traits, contexts keep armour
 * and minion traits apart, the Organ Ability key cycles the pieces with cooldowns and a blood cost, our conditions, and
 * the server's switch for effect types. Traits made for a test live under its own ids ({@link TestTraits}).
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class TraitEngineTests {
    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static PieceRef cow(String bone) {
        return new PieceRef(ResourceLocation.withDefaultNamespace("cow"), bone, ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(helper.makeMockPlayer(GameType.SURVIVAL), at, build, 1000.0F);
        level.addFreshEntity(minion);
        return minion;
    }

    /** Whether the attribute carries the modifier of this trait's effect. */
    private static boolean has(LivingEntity host, Holder<Attribute> attribute, ResourceLocation trait, int index) {
        AttributeInstance instance = host.getAttribute(attribute);
        return instance != null && instance.getModifier(ActiveTraits.modifierId(trait, index)) != null;
    }

    /**
     * A cow on its own legs has the grazer's leg trait for minions (sure-footed) and its step height; with no legs, it
     * has neither. The data is the cow's own: its legs' "minion": {"traits": [...]}.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void minionGetsLegTraits(GameTestHelper helper) {
        ResourceLocation sureFooted = bb("sure_footed");
        Trait trait = PartsData.SERVER.trait(sureFooted);
        if (trait == null) {
            helper.fail("sure_footed is not loaded");
            return;
        }
        MinionBuild legs = MinionBuild.of(cow("body")).with("right_front_leg", cow("right_front_leg")).with("left_front_leg", cow("left_front_leg"))
                .with("right_hind_leg", cow("right_hind_leg")).with("left_hind_leg", cow("left_hind_leg"));
        MinionEntity walker = minion(helper, new BlockPos(2, 2, 2), legs);
        MinionEntity stump = minion(helper, new BlockPos(6, 2, 6), MinionBuild.of(cow("body")));
        ActiveTraits traits = ActiveTraits.of(walker);
        int level = traits.level(sureFooted);
        float expected = ((TraitEffects.AttributeEffect) trait.effects().get(0).effect()).amount().calculate(level) * TraitEffects.strength();
        AttributeInstance step = walker.getAttribute(Attributes.STEP_HEIGHT);
        var modifier = step == null ? null : step.getModifier(ActiveTraits.modifierId(sureFooted, 0));
        if (!ActiveTraits.MINION.equals(traits.context()) || level < 1 || modifier == null || Math.abs(modifier.amount() - expected) > 1.0E-4) {
            helper.fail("A cow on its legs should be sure-footed, its step height up by " + expected + ": " + traits.entries() + ", " + modifier);
            return;
        }
        if (ActiveTraits.of(stump).level(sureFooted) != 0 || has(stump, Attributes.STEP_HEIGHT, sureFooted, 0)) {
            helper.fail("A cow torso with no legs should not be sure-footed: " + ActiveTraits.of(stump).entries());
            return;
        }
        walker.discard();
        stump.discard();
        helper.succeed();
    }

    /**
     * A trait for armour only never reaches a minion, one for minions only never a player, and one effect of a trait for
     * both works only in the context it names: the same helmet list and organ list give each only its own.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void minionContextOnly(GameTestHelper helper) {
        ResourceLocation armourOnly = TestTraits.trait(helper, "context_armour_only", """
                {"name": "trait.bloodandbones.hardy", "contexts": ["armour"],
                 "effects": [{"effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.armor", "amount": 1}}]}""");
        ResourceLocation minionOnly = TestTraits.trait(helper, "context_minion_only", """
                {"name": "trait.bloodandbones.hardy", "contexts": ["minion"],
                 "effects": [{"effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.armor", "amount": 2}}]}""");
        ResourceLocation split = TestTraits.trait(helper, "context_split", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"context": "armour", "effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.armor", "amount": 4}},
                             {"context": "minion", "effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.armor", "amount": 8}}]}""");
        String list = "{\"replace\": true, \"add\": [\"" + armourOnly + "\", \"" + minionOnly + "\", \"" + split + "\"]}";
        ResourceLocation mob = TestTraits.mob(helper, "context", "{\"parts\": {\"head\": {\"armour\": " + list + "}},"
                + " \"organ_traits\": {\"bloodandbones:heart\": {\"minion\": " + list + "}}}");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits worn = ActiveTraits.rebuild(player);
        if (worn.level(armourOnly) != 1 || worn.level(minionOnly) != 0 || worn.level(split) != 1
                || !has(player, Attributes.ARMOR, armourOnly, 0) || has(player, Attributes.ARMOR, minionOnly, 0)
                || !has(player, Attributes.ARMOR, split, 0) || has(player, Attributes.ARMOR, split, 1)) {
            helper.fail("A player should get the armour-only trait and the split trait's armour half only: " + worn.entries());
            return;
        }
        MinionBuild build = MinionBuild.of(cow("body")).withOrgan(Optional.of(new CarcassArmour.Organ(bb("heart"), mob, false)));
        MinionEntity minion = minion(helper, new BlockPos(4, 2, 4), build);
        ActiveTraits fitted = ActiveTraits.of(minion);
        if (fitted.level(armourOnly) != 0 || fitted.level(minionOnly) != 1 || fitted.level(split) != 1
                || has(minion, Attributes.ARMOR, armourOnly, 0) || !has(minion, Attributes.ARMOR, minionOnly, 0)
                || has(minion, Attributes.ARMOR, split, 0) || !has(minion, Attributes.ARMOR, split, 1)) {
            helper.fail("A minion's organ should give the minion-only trait and the split trait's minion half only: " + fitted.entries());
            return;
        }
        minion.discard();
        helper.succeed();
    }

    /**
     * Two pieces with an activate effect each: the first press fires the helmet's, the second the chestplate's, each
     * piece showing its cooldown; a third press finds both cooling down and fires nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void activateCyclesPieces(GameTestHelper helper) {
        ResourceLocation head = TestTraits.trait(helper, "activate_head", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "activate", "cooldown": 200, "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:luck", "duration": 100}}]}""");
        ResourceLocation chest = TestTraits.trait(helper, "activate_chest", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "activate", "cooldown": 200, "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:unluck", "duration": 100}}]}""");
        ResourceLocation mob = TestTraits.mob(helper, "activate", "{\"parts\": {"
                + "\"head\": {\"armour\": {\"replace\": true, \"add\": [\"" + head + "\"]}},"
                + "\"torso\": {\"armour\": {\"replace\": true, \"add\": [\"" + chest + "\"]}}}}");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        player.setItemSlot(EquipmentSlot.CHEST, TestTraits.piece("chestplate", mob));
        ActiveTraits.rebuild(player);

        OrganActivatePayload.handle(player);
        if (!player.hasEffect(MobEffects.LUCK) || player.hasEffect(MobEffects.UNLUCK) || !player.getCooldowns().isOnCooldown(BBItems.CARCASS_HELMET.get())
                || player.getCooldowns().isOnCooldown(BBItems.CARCASS_CHESTPLATE.get())) {
            helper.fail("The first press should fire the helmet's effect and cool the helmet down");
            return;
        }
        OrganActivatePayload.handle(player);
        if (!player.hasEffect(MobEffects.UNLUCK) || !player.getCooldowns().isOnCooldown(BBItems.CARCASS_CHESTPLATE.get())) {
            helper.fail("The second press should fire the chestplate's effect and cool it down");
            return;
        }
        player.removeAllEffects();
        OrganActivatePayload.handle(player);
        if (player.hasEffect(MobEffects.LUCK) || player.hasEffect(MobEffects.UNLUCK)) {
            helper.fail("A third press should find both cooling down and fire nothing");
            return;
        }
        helper.succeed();
    }

    /**
     * An activate effect costing 50 mB, with no tank on, costs 3 hunger instead (spec 7.6), and is refused only when the
     * wearer is too hungry for that too; with a backtank of blood strapped to the chestplate it takes the blood and no
     * hunger, and with soul blood in the tank it takes hunger again.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void activateCostsBlood(GameTestHelper helper) {
        ResourceLocation costly = TestTraits.trait(helper, "activate_costly", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "activate", "cost_mb": 50, "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:luck", "duration": 100}}]}""");
        ResourceLocation mob = TestTraits.mob(helper, "costly", "{\"parts\": {\"torso\": {\"armour\": {\"replace\": true, \"add\": [\"" + costly + "\"]}}}}");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack chestplate = TestTraits.piece("chestplate", mob);
        player.setItemSlot(EquipmentSlot.CHEST, chestplate);
        ActiveTraits.rebuild(player);
        player.getFoodData().setFoodLevel(2);
        OrganActivatePayload.handle(player);
        if (player.hasEffect(MobEffects.LUCK) || player.getFoodData().getFoodLevel() != 2) {
            helper.fail("With no tank to pay from and too hungry to pay in hunger, it should be refused");
            return;
        }
        player.getFoodData().setFoodLevel(20);
        OrganActivatePayload.handle(player);
        if (!player.hasEffect(MobEffects.LUCK) || player.getFoodData().getFoodLevel() != 20 - Activation.HUNGER_COST) {
            helper.fail("With no tank to pay from it should fire for 3 hunger: " + player.getFoodData().getFoodLevel());
            return;
        }
        player.removeAllEffects();
        player.getFoodData().setFoodLevel(20);
        ItemStack tank = new ItemStack(BBItems.backtank(BacktankTier.IRON));
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.blood(), 1000));
        player.setItemSlot(EquipmentSlot.CHEST, FluidBacktankItem.strap(chestplate, tank));
        ActiveTraits.rebuild(player);
        OrganActivatePayload.handle(player);
        int left = FluidBacktankItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
        if (!player.hasEffect(MobEffects.LUCK) || left != 950 || player.getFoodData().getFoodLevel() != 20) {
            helper.fail("Strapped to a tank of blood it should fire and take 50 mB, no hunger: " + left + " left, food " + player.getFoodData().getFoodLevel());
            return;
        }
        player.removeAllEffects();
        ItemStack souls = new ItemStack(BBItems.backtank(BacktankTier.IRON));
        FluidBacktankItem.setFluid(souls, new FluidStack(BBFluids.soulBlood(), 1000));
        player.setItemSlot(EquipmentSlot.CHEST, FluidBacktankItem.strap(TestTraits.piece("chestplate", mob), souls));
        ActiveTraits.rebuild(player);
        OrganActivatePayload.handle(player);
        int soulsLeft = FluidBacktankItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
        if (!player.hasEffect(MobEffects.LUCK) || soulsLeft != 1000 || player.getFoodData().getFoodLevel() != 20 - Activation.HUNGER_COST) {
            helper.fail("Soul blood in the tank should be left alone and hunger paid: " + soulsLeft + " left, food " + player.getFoodData().getFoodLevel());
            return;
        }
        helper.succeed();
    }

    /**
     * An entry with a cooldown and a condition uses none of its cooldown when the condition fails (spec 5.1: its cooldown
     * starts when it goes): hit and ticked at full health, nothing; low, both go at once; again, both cool down.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cooldownWaitsForTheCondition(GameTestHelper helper) {
        ResourceLocation second = TestTraits.trait(helper, "second_wind_like", """
                {"name": "trait.bloodandbones.second_wind",
                 "effects": [{"trigger": "hurt", "cooldown": 1200, "requirements": {"condition": "bloodandbones:health_below", "fraction": 0.3},
                              "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:luck", "duration": 100}},
                             {"trigger": "tick", "interval": 20, "cooldown": 1200, "requirements": {"condition": "bloodandbones:health_below", "fraction": 0.3},
                              "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:unluck", "duration": 100}}]}""");
        ResourceLocation mob = TestTraits.mob(helper, "second_wind_like", "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [\"" + second + "\"]}}}}");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        TraitEvents.fire(player, Trigger.HURT, player.damageSources().generic(), null, 1.0F);
        TraitEvents.tick(player);
        if (player.hasEffect(MobEffects.LUCK) || player.hasEffect(MobEffects.UNLUCK)) {
            helper.fail("At full health neither should go");
            return;
        }
        player.setHealth(4.0F);
        TraitEvents.fire(player, Trigger.HURT, player.damageSources().generic(), null, 1.0F);
        TraitEvents.tick(player);
        if (!player.hasEffect(MobEffects.LUCK) || !player.hasEffect(MobEffects.UNLUCK)) {
            helper.fail("Low, both should go, their cooldowns untouched by the checks that failed: " + player.hasEffect(MobEffects.LUCK) + ", "
                    + player.hasEffect(MobEffects.UNLUCK));
            return;
        }
        player.removeAllEffects();
        TraitEvents.fire(player, Trigger.HURT, player.damageSources().generic(), null, 1.0F);
        TraitEvents.tick(player);
        if (player.hasEffect(MobEffects.LUCK) || player.hasEffect(MobEffects.UNLUCK)) {
            helper.fail("Once gone, both should cool down");
            return;
        }
        helper.succeed();
    }

    /**
     * Cooldowns are saved (spec 7.6): a player saved and loaded again (a relog) still has a hurt effect's cooldown, and the
     * Organ Ability's is on the helmet, so another wearer of it finds it cooling down too; a minion saved and loaded again
     * (a chunk reloaded, or folded and unfolded) keeps its own.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cooldownsOutliveTheCreature(GameTestHelper helper) {
        ResourceLocation kept = TestTraits.trait(helper, "kept_cooldowns", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "hurt", "cooldown": 1200, "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:luck", "duration": 20}},
                             {"trigger": "activate", "cooldown": 1200, "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:unluck", "duration": 20}}]}""");
        String list = "{\"replace\": true, \"add\": [\"" + kept + "\"]}";
        ResourceLocation mob = TestTraits.mob(helper, "kept_cooldowns", "{\"parts\": {\"head\": {\"armour\": " + list + "}},"
                + " \"organ_traits\": {\"bloodandbones:heart\": {\"minion\": " + list + "}}}");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        TraitEvents.fire(player, Trigger.HURT, player.damageSources().generic(), null, 1.0F);
        OrganActivatePayload.handle(player);
        if (!player.hasEffect(MobEffects.LUCK) || !player.hasEffect(MobEffects.UNLUCK)) {
            helper.fail("Both effects should go first time");
            return;
        }
        Player again = helper.makeMockPlayer(GameType.SURVIVAL);
        again.load(player.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        ActiveTraits.Entry entry = entry(ActiveTraits.rebuild(again), kept);
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        other.setItemSlot(EquipmentSlot.HEAD, again.getItemBySlot(EquipmentSlot.HEAD).copy());
        ActiveTraits.Entry otherEntry = entry(ActiveTraits.rebuild(other), kept);
        if (entry == null || otherEntry == null || !TraitEvents.coolingDown(again, entry, 0) || !TraitEvents.coolingDown(again, entry, 1)
                || TraitEvents.coolingDown(other, otherEntry, 0) || !TraitEvents.coolingDown(other, otherEntry, 1)) {
            helper.fail("Loaded again both should still cool down, and the Organ Ability's go with the helmet: " + (entry != null && TraitEvents.coolingDown(again, entry, 0))
                    + ", " + (entry != null && TraitEvents.coolingDown(again, entry, 1)) + ", " + (otherEntry != null && TraitEvents.coolingDown(other, otherEntry, 1)));
            return;
        }
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), MinionBuild.of(cow("body")).withOrgan(Optional.of(new CarcassArmour.Organ(bb("heart"), mob, false))));
        TraitEvents.fire(minion, Trigger.HURT, minion.damageSources().generic(), null, 1.0F);
        MinionEntity reloaded = BBEntities.MINION.get().create(helper.getLevel());
        reloaded.load(minion.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        minion.discard();
        ActiveTraits.Entry minionEntry = entry(ActiveTraits.of(reloaded), kept);
        if (minionEntry == null || !TraitEvents.coolingDown(reloaded, minionEntry, 0)) {
            helper.fail("A minion loaded again should still cool down: " + ActiveTraits.of(reloaded).entries());
            return;
        }
        helper.succeed();
    }

    @org.jetbrains.annotations.Nullable
    private static ActiveTraits.Entry entry(ActiveTraits traits, ResourceLocation id) {
        for (ActiveTraits.Entry entry : traits.entries()) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * A minion's health from its traits is not saved with it (the modifiers are put on again), yet a minion saved at
     * full health above its torso's comes back at that health, not cut down to the torso's.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void traitHealthSurvivesReload(GameTestHelper helper) {
        ResourceLocation big = TestTraits.trait(helper, "reload_health", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.max_health", "amount": 20}}]}""");
        ResourceLocation mob = TestTraits.mob(helper, "reload_health", "{\"organ_traits\": {\"bloodandbones:heart\": {\"minion\": [\"" + big + "\"]}}}");
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), MinionBuild.of(cow("body")).withOrgan(Optional.of(new CarcassArmour.Organ(bb("heart"), mob, false))));
        minion.setHealth(minion.getMaxHealth());
        float saved = minion.getHealth();
        MinionEntity reloaded = BBEntities.MINION.get().create(helper.getLevel());
        reloaded.load(minion.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
        minion.discard();
        if (saved <= 20.0F || Math.abs(reloaded.getHealth() - saved) > 1.0E-3F) {
            helper.fail("Loaded again it should keep its " + saved + " health: " + reloaded.getHealth() + " of " + reloaded.getMaxHealth());
            return;
        }
        helper.succeed();
    }

    /**
     * Spec 5.8's caps: two traits raising speed 30% each give 40% between them, and jump strength 0.25 each 0.3; a
     * minion's traits never take its health over 150 nor under 6.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void traitCapsHold(GameTestHelper helper) {
        String moves = """
                {"name": "trait.bloodandbones.swift",
                 "effects": [{"effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.movement_speed", "amount": 0.3, "operation": "add_multiplied_base"}},
                             {"effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.jump_strength", "amount": 0.25}}]}""";
        ResourceLocation fast = TestTraits.trait(helper, "cap_fast", moves);
        ResourceLocation faster = TestTraits.trait(helper, "cap_faster", moves);
        ResourceLocation mob = TestTraits.mob(helper, "caps", "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [\"" + fast + "\", \""
                + faster + "\"]}}}}");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        float strength = TraitEffects.strength();
        double speed = amount(player, Attributes.MOVEMENT_SPEED, fast, 0) + amount(player, Attributes.MOVEMENT_SPEED, faster, 0);
        double jump = amount(player, Attributes.JUMP_STRENGTH, fast, 1) + amount(player, Attributes.JUMP_STRENGTH, faster, 1);
        if (Math.abs(speed - Math.min(0.6 * strength, ActiveTraits.SPEED_CAP)) > 1.0E-4 || Math.abs(jump - Math.min(0.5 * strength, ActiveTraits.JUMP_CAP)) > 1.0E-4) {
            helper.fail("Speed should be capped at +40% and jump at +0.3: " + speed + ", " + jump);
            return;
        }
        ResourceLocation huge = TestTraits.trait(helper, "cap_huge", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.max_health", "amount": 500}}]}""");
        ResourceLocation frail = TestTraits.trait(helper, "cap_frail", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.max_health", "amount": -500}}]}""");
        MinionEntity big = minion(helper, new BlockPos(2, 2, 2), MinionBuild.of(cow("body")).withOrgan(Optional.of(
                new CarcassArmour.Organ(bb("heart"), TestTraits.mob(helper, "cap_huge", "{\"organ_traits\": {\"bloodandbones:heart\": {\"minion\": {\"replace\": true, \"add\": [\"" + huge + "\"]}}}}"), false))));
        MinionEntity small = minion(helper, new BlockPos(6, 2, 6), MinionBuild.of(cow("body")).withOrgan(Optional.of(
                new CarcassArmour.Organ(bb("heart"), TestTraits.mob(helper, "cap_frail", "{\"organ_traits\": {\"bloodandbones:heart\": {\"minion\": {\"replace\": true, \"add\": [\"" + frail + "\"]}}}}"), false))));
        ActiveTraits.of(big);
        ActiveTraits.of(small);
        float most = big.getMaxHealth();
        float least = small.getMaxHealth();
        big.discard();
        small.discard();
        if (Math.abs(most - com.avicagan.bloodandbones.minion.MinionStats.MAX_HEALTH) > 1.0E-3F
                || Math.abs(least - com.avicagan.bloodandbones.minion.MinionStats.MIN_HEALTH) > 1.0E-3F) {
            helper.fail("A minion's traits should keep its health within 6 to 150: " + most + ", " + least);
            return;
        }
        helper.succeed();
    }

    /** The amount of one trait effect's modifier on an attribute, 0 if it is not on. */
    private static double amount(LivingEntity host, Holder<Attribute> attribute, ResourceLocation trait, int index) {
        AttributeInstance instance = host.getAttribute(attribute);
        var modifier = instance == null ? null : instance.getModifier(ActiveTraits.modifierId(trait, index));
        return modifier == null ? 0.0 : modifier.amount();
    }

    /**
     * The attack trigger is for a blow (spec 5.2): an on-hit trait guarded as the shipped ones are (a direct hit) marks
     * a zombie struck by its minion, but not one stung by thorns the minion sent back (a barbed hide's), nor one hit by
     * the minion's own beam.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void attackTriggerOnlyOnBlows(GameTestHelper helper) {
        ResourceLocation onHit = TestTraits.trait(helper, "blow_mark", """
                {"name": "trait.bloodandbones.flinger",
                 "effects": [{"trigger": "attack", "requirements": {"condition": "minecraft:damage_source_properties", "predicate": {"is_direct": true}},
                              "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:glowing", "target": "victim", "duration": 200}}]}""");
        ResourceLocation beam = TestTraits.trait(helper, "blow_beam", """
                {"name": "trait.bloodandbones.web_shot",
                 "effects": [{"trigger": "activate", "range": 10, "effect": {"type": "bloodandbones:hitscan", "range": 10, "damage": 1}}]}""");
        ResourceLocation mob = TestTraits.mob(helper, "blow", "{\"organ_traits\": {\"bloodandbones:heart\": {\"minion\": [\"" + onHit + "\", \"" + beam + "\"]}}}");
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 4), MinionBuild.of(cow("body")).withOrgan(Optional.of(new CarcassArmour.Organ(bb("heart"), mob, false))));
        minion.setNoAi(true);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 4));
        zombie.setNoAi(true);
        var facet = com.avicagan.bloodandbones.parts.Activation.readyFor(minion, zombie);
        float full = zombie.getHealth();
        if (facet != null) {
            com.avicagan.bloodandbones.parts.Activation.fire(minion, facet, zombie);
        }
        boolean beamed = zombie.getHealth() < full;
        boolean markedByBeam = zombie.hasEffect(MobEffects.GLOWING);
        zombie.invulnerableTime = 0;
        zombie.hurt(minion.damageSources().thorns(minion), 1.0F);
        boolean markedByThorns = zombie.hasEffect(MobEffects.GLOWING);
        zombie.invulnerableTime = 0;
        zombie.hurt(minion.damageSources().mobAttack(minion), 1.0F);
        boolean markedByBlow = zombie.hasEffect(MobEffects.GLOWING);
        minion.discard();
        zombie.discard();
        if (!beamed || markedByBeam || markedByThorns || !markedByBlow) {
            helper.fail("Only a blow should set off the on-hit trait: beam landed " + beamed + ", marked by beam " + markedByBeam + ", by thorns "
                    + markedByThorns + ", by a blow " + markedByBlow);
            return;
        }
        helper.succeed();
    }

    /**
     * health_below: true under the share, false over it, read from data as a trait's requirements would be; and a
     * passive armour bonus with it comes on only once the wearer is low.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void healthBelowCondition(GameTestHelper helper) {
        LootItemCondition below = TestTraits.condition(helper, "{\"condition\": \"bloodandbones:health_below\", \"fraction\": 0.5}");
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        zombie.setNoAi(true);
        zombie.setHealth(zombie.getMaxHealth() * 0.4F);
        boolean low = below.test(Enchantment.entityContext(helper.getLevel(), 1, zombie, zombie.position()));
        zombie.setHealth(zombie.getMaxHealth() * 0.6F);
        boolean high = below.test(Enchantment.entityContext(helper.getLevel(), 1, zombie, zombie.position()));
        zombie.discard();
        if (!low || high) {
            helper.fail("health_below 0.5 should hold at 40% and not at 60%: " + low + ", " + high);
            return;
        }
        ResourceLocation desperate = TestTraits.trait(helper, "health_below_armour", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"requirements": {"condition": "bloodandbones:health_below", "fraction": 0.5},
                              "effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.armor", "amount": 3}}]}""");
        ResourceLocation mob = TestTraits.mob(helper, "health_below", "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [\"" + desperate + "\"]}}}}");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        TraitEvents.tick(player);
        boolean healthy = has(player, Attributes.ARMOR, desperate, 0);
        player.setHealth(4.0F);
        TraitEvents.tick(player);
        if (healthy || !has(player, Attributes.ARMOR, desperate, 0)) {
            helper.fail("The armour bonus should come on only below half health: " + healthy);
            return;
        }
        helper.succeed();
    }

    /**
     * dry_for: just out of the water it has been dry no time; a second and a half later, for at least one second. One
     * never seen wet (just logged in on dry land) has been dry a long while, not just out of the water.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void dryForCondition(GameTestHelper helper) {
        LootItemCondition atLeast = TestTraits.condition(helper, "{\"condition\": \"bloodandbones:dry_for\", \"seconds\": 1}");
        LootItemCondition atMost = TestTraits.condition(helper, "{\"condition\": \"bloodandbones:dry_for\", \"seconds\": 1, \"at_most\": true}");
        Player dryAll = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 land = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(7, 2, 2)));
        dryAll.setPos(land.x, land.y, land.z);
        var first = Enchantment.entityContext(helper.getLevel(), 1, dryAll, dryAll.position());
        if (!atLeast.test(first) || atMost.test(first)) {
            helper.fail("Never seen wet, it should count as long dry: " + ActiveTraits.drySeconds(dryAll));
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos water = new BlockPos(2, 2, 2);
        helper.setBlock(water, Blocks.WATER);
        Vec3 wet = helper.absoluteVec(Vec3.atBottomCenterOf(water));
        player.setPos(wet.x, wet.y, wet.z);
        ActiveTraits.updateDry(player);
        var context = Enchantment.entityContext(helper.getLevel(), 1, player, player.position());
        if (atLeast.test(context) || !atMost.test(context)) {
            helper.fail("Standing in water it should not count as dry");
            return;
        }
        // taken away before it can spread
        helper.setBlock(water, Blocks.AIR);
        Vec3 dry = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(6, 2, 6)));
        player.setPos(dry.x, dry.y, dry.z);
        helper.runAfterDelay(30, () -> {
            ActiveTraits.updateDry(player);
            var later = Enchantment.entityContext(helper.getLevel(), 1, player, player.position());
            if (!atLeast.test(later) || atMost.test(later)) {
                helper.fail("A second and a half out of the water it should be dry for at least a second: " + ActiveTraits.drySeconds(player));
                return;
            }
            helper.succeed();
        });
    }

    /** An effect type the server switches off does nothing: a passive attribute of that type is not put on, and comes back with it. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void disabledEffectTypeSkipped(GameTestHelper helper) {
        ResourceLocation plated = TestTraits.trait(helper, "disabled_type", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.armor", "amount": 2}}]}""");
        ResourceLocation mob = TestTraits.mob(helper, "disabled_type", "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [\"" + plated + "\"]}}}}");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        if (!has(player, Attributes.ARMOR, plated, 0)) {
            helper.fail("The attribute effect should be on while its type is allowed");
            return;
        }
        // the setting is the whole server's: switched off and back within this one call, so no other test sees it
        List<? extends String> before = BBServerConfig.DISABLED_EFFECT_TYPES.get();
        boolean skipped;
        try {
            BBServerConfig.DISABLED_EFFECT_TYPES.set(List.of("bloodandbones:attribute"));
            ActiveTraits.rebuild(player);
            skipped = !has(player, Attributes.ARMOR, plated, 0);
        } finally {
            BBServerConfig.DISABLED_EFFECT_TYPES.set(before);
        }
        ActiveTraits.rebuild(player);
        if (!skipped || !has(player, Attributes.ARMOR, plated, 0)) {
            helper.fail("Switched off, the attribute effect should not be put on, and come back after: " + skipped);
            return;
        }
        helper.succeed();
    }

    /**
     * A minion's organ with an activate effect fires from its AI once its target is within the effect's range: a glowing
     * mark on the pig, paid for with 20 mB of the minion's own blood (and a little more drunk in the meantime).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void minionFiresOrganAtTarget(GameTestHelper helper) {
        ResourceLocation mark = TestTraits.trait(helper, "minion_mark", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "activate", "range": 6, "cost_mb": 20, "cooldown": 400,
                              "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:glowing", "target": "target", "duration": 200}}]}""");
        ResourceLocation mob = TestTraits.mob(helper, "minion_mark", "{\"organ_traits\": {\"bloodandbones:heart\": {\"minion\": [\"" + mark + "\"]}}}");
        // a head, or it would be mindless and never take aim at anything
        MinionBuild build = MinionBuild.of(cow("body")).with("head", cow("head")).withOrgan(Optional.of(new CarcassArmour.Organ(bb("heart"), mob, false)));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), build);
        float power = minion.power();
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 2));
        pig.setNoAi(true);
        minion.setTarget(pig);
        helper.succeedWhen(() -> {
            if (!pig.hasEffect(MobEffects.GLOWING)) {
                minion.setTarget(pig);
                helper.fail("The minion has not fired its organ at the pig yet");
            }
            float paid = power - minion.power();
            if (paid < 20.0F || paid > 25.0F) {
                helper.fail("Firing should cost 20 mB of its blood: " + paid);
            }
            minion.discard();
            pig.discard();
        });
    }
}
