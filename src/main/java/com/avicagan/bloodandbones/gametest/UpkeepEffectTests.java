package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.Trait;
import com.avicagan.bloodandbones.parts.TraitConditions;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.registry.BBEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Upkeep group's effects (docs/ARCHITECTURE-PROPOSAL.md section 15.8): produce (flesh only), regen on blood (never on
 * brass), mending with iron, storage that drops what no longer fits, lethal saves (a minion collapses), the sun and the dry
 * as drawbacks, and blood food. Traits and mobs made for a test live under its own ids ({@link TestTraits}).
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class UpkeepEffectTests {
    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static PieceRef cow(String bone, boolean skinned) {
        return new PieceRef(ResourceLocation.withDefaultNamespace("cow"), bone, ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"),
                List.of(), 1.0F, skinned, Map.of(), false);
    }

    private static MinionBuild flesh() {
        return MinionBuild.of(cow("body", false));
    }

    /** A brass cow torso, sheathed, as the Assembly Frame makes one from a skinned torso. */
    private static MinionBuild brass() {
        return new MinionBuild(true, cow("body", true), List.of(), true);
    }

    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build, float power) {
        return minion(helper, helper.makeMockPlayer(GameType.SURVIVAL), pos, build, power);
    }

    private static MinionEntity minion(GameTestHelper helper, Player maker, BlockPos pos, MinionBuild build, float power) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, build, power);
        level.addFreshEntity(minion);
        return minion;
    }

    /** A made-up mob whose heart gives a minion exactly these traits (and nothing its body shape's heart would). */
    private static CarcassArmour.Organ heart(GameTestHelper helper, String name, String... traits) {
        StringBuilder list = new StringBuilder();
        for (String trait : traits) {
            list.append(list.isEmpty() ? "" : ", ").append(trait.startsWith("{") ? trait : "\"" + trait + "\"");
        }
        ResourceLocation mob = TestTraits.mob(helper, name, "{\"organ_traits\": {\"bloodandbones:heart\": {\"minion\": {\"replace\": true, \"add\": ["
                + list + "]}}}}");
        return new CarcassArmour.Organ(bb("heart"), mob, false);
    }

    /** A made-up mob whose head scraps give a helmet exactly these traits. */
    private static ResourceLocation helmetMob(GameTestHelper helper, String name, String... traits) {
        StringBuilder list = new StringBuilder();
        for (String trait : traits) {
            list.append(list.isEmpty() ? "" : ", ").append(trait.startsWith("{") ? trait : "\"" + trait + "\"");
        }
        return TestTraits.mob(helper, name, "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [" + list + "]}}}}");
    }

    private static Player wearing(GameTestHelper helper, ResourceLocation mob, BlockPos at) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        player.setPos(pos.x, pos.y, pos.z);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        return player;
    }

    /**
     * A flesh minion with a chicken's egg gland lays an egg into what it carries when its tick comes round, paying 10 mB.
     * The chicken's own data wires the gland (mob_traits/minecraft/chicken.json).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void chickenOrganLaysEgg(GameTestHelper helper) {
        MinionBuild build = flesh().withOrgan(Optional.of(new CarcassArmour.Organ(bb("egg_gland"), ResourceLocation.withDefaultNamespace("chicken"), false)));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), build, 500.0F);
        if (ActiveTraits.of(minion).level(bb("egg_layer")) != 1) {
            helper.fail("A chicken's egg gland should make the minion an egg layer: " + ActiveTraits.of(minion).entries());
            return;
        }
        // a whole interval on: the first go never comes the moment it wakes (or loads)
        minion.tickCount = 6000;
        float before = minion.power();
        TraitEvents.fire(minion, Trigger.TICK, null, null, 0.0F);
        float paid = before - minion.power();
        int eggs = minion.inventory.countItem(Items.EGG);
        minion.discard();
        if (eggs != 1 || Math.abs(paid - 10.0F) > 0.5F) {
            helper.fail("It should lay one egg for 10 mB: " + eggs + " eggs, " + paid + " mB");
            return;
        }
        helper.succeed();
    }

    /** A brass minion with the same gland makes nothing and pays nothing: produce is for flesh (spec 6.6). */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void produceInertOnCyber(GameTestHelper helper) {
        MinionBuild build = brass().withOrgan(Optional.of(new CarcassArmour.Organ(bb("egg_gland"), ResourceLocation.withDefaultNamespace("chicken"), false)));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), build, 1000.0F);
        minion.tickCount = 6000;
        float before = minion.power();
        TraitEvents.fire(minion, Trigger.TICK, null, null, 0.0F);
        int eggs = minion.inventory.countItem(Items.EGG);
        float paid = before - minion.power();
        minion.discard();
        if (eggs != 0 || paid != 0.0F) {
            helper.fail("A brass minion should make nothing and pay nothing: " + eggs + " eggs, " + paid + " mB");
            return;
        }
        helper.succeed();
    }

    /** Regen mends a flesh minion out of its blood: 4 health for 20 mB, 5 mB a heart. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void regenHealsMinionForBlood(GameTestHelper helper) {
        ResourceLocation regen = TestTraits.trait(helper, "regen_four", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "tick", "interval": 100, "effect": {"type": "bloodandbones:regen", "amount": 4}}]}""");
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), flesh().withOrgan(Optional.of(heart(helper, "regen_four", regen.toString()))), 500.0F);
        minion.setHealth(minion.getMaxHealth() - 6.0F);
        float health = minion.getHealth();
        float power = minion.power();
        TraitEvents.fire(minion, Trigger.TICK, null, null, 0.0F);
        float healed = minion.getHealth() - health;
        float paid = power - minion.power();
        minion.discard();
        if (Math.abs(healed - 4.0F) > 1.0E-3F || Math.abs(paid - 20.0F) > 1.0E-3F) {
            helper.fail("It should mend 4 health for 20 mB: " + healed + " health, " + paid + " mB");
            return;
        }
        helper.succeed();
    }

    /**
     * The same regen on a brass minion does nothing: brass never heals itself (the brief), and pays nothing for it. Nor
     * does Regeneration from its own trait (a horse's Spleen, hurt) go on it, nor its flesh parts' weaknesses (the sun
     * setting a rotting torso alight) reach it, as both do on flesh (spec 6.6).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void brassNeverSelfHeals(GameTestHelper helper) {
        ResourceLocation regen = TestTraits.trait(helper, "regen_brass", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "tick", "interval": 100, "effect": {"type": "bloodandbones:regen", "amount": 4}}]}""");
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), brass().withOrgan(Optional.of(heart(helper, "regen_brass", regen.toString()))), 1000.0F);
        minion.setHealth(minion.getMaxHealth() - 6.0F);
        float health = minion.getHealth();
        float power = minion.power();
        TraitEvents.fire(minion, Trigger.TICK, null, null, 0.0F);
        boolean healed = minion.getHealth() != health || minion.power() != power;
        minion.discard();
        if (healed) {
            helper.fail("A brass minion should not mend itself");
            return;
        }
        ResourceLocation fleshy = TestTraits.trait(helper, "regen_brass_potion", """
                {"name": "trait.bloodandbones.second_wind",
                 "effects": [{"trigger": "hurt", "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:regeneration", "duration": 100}},
                             {"trigger": "tick", "interval": 20, "effect": {"type": "bloodandbones:exposure", "ignite": 5}}]}""");
        CarcassArmour.Organ organ = heart(helper, "regen_brass_potion", fleshy.toString());
        MinionEntity brass = minion(helper, new BlockPos(5, 2, 2), brass().withOrgan(Optional.of(organ)), 1000.0F);
        MinionEntity flesh = minion(helper, new BlockPos(2, 2, 5), flesh().withOrgan(Optional.of(organ)), 1000.0F);
        for (MinionEntity each : List.of(brass, flesh)) {
            TraitEvents.fire(each, Trigger.HURT, each.damageSources().generic(), null, 1.0F);
            TraitEvents.fire(each, Trigger.TICK, null, null, 0.0F);
        }
        boolean brassHealing = brass.hasEffect(MobEffects.REGENERATION);
        boolean brassBurning = brass.getRemainingFireTicks() > 0;
        boolean fleshHealing = flesh.hasEffect(MobEffects.REGENERATION);
        boolean fleshBurning = flesh.getRemainingFireTicks() > 0;
        brass.discard();
        flesh.discard();
        if (brassHealing || brassBurning || !fleshHealing || !fleshBurning) {
            helper.fail("Its own Regeneration and its parts' weakness should reach flesh only: brass " + brassHealing + ", " + brassBurning
                    + "; flesh " + fleshHealing + ", " + fleshBurning);
            return;
        }
        helper.succeed();
    }

    /**
     * repair_with_iron: an iron ingot used on a hurt minion mends 25 health and is used up, flesh or brass. A mend effect of
     * the item kind on armour lets its item mend the piece on an anvil (iron here, not gold).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void repairWithIronHeals25(GameTestHelper helper) {
        ResourceLocation big = TestTraits.trait(helper, "iron_big", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.max_health", "amount": 40}}]}""");
        CarcassArmour.Organ organ = heart(helper, "iron_mended", "bloodandbones:repair_with_iron", big.toString());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (MinionBuild build : List.of(flesh(), brass())) {
            MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), build.withOrgan(Optional.of(organ)), 800.0F);
            minion.setHealth(5.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 2));
            minion.interact(player, InteractionHand.MAIN_HAND);
            float health = minion.getHealth();
            int left = player.getItemInHand(InteractionHand.MAIN_HAND).getCount();
            minion.discard();
            if (Math.abs(health - 30.0F) > 1.0E-3F || left != 1) {
                helper.fail((build.cybernetic() ? "Brass" : "Flesh") + " should mend 25 on an ingot: " + health + " health, " + left + " ingots left");
                return;
            }
        }
        ResourceLocation anvil = TestTraits.trait(helper, "iron_anvil", """
                {"name": "trait.bloodandbones.repair_with_iron",
                 "effects": [{"effect": {"type": "bloodandbones:mend", "mode": "item", "items": ["minecraft:iron_ingot"], "amount": 25}}]}""");
        ResourceLocation mob = helmetMob(helper, "iron_mended_armour", anvil.toString());
        ItemStack helmet = TestTraits.piece("helmet", mob);
        if (!(helmet.getItem() instanceof CarcassArmourItem item) || !item.isValidRepairItem(helmet, new ItemStack(Items.IRON_INGOT))
                || item.isValidRepairItem(helmet, new ItemStack(Items.GOLD_INGOT))) {
            helper.fail("Iron, and not gold, should mend the armour on an anvil");
            return;
        }
        helper.succeed();
    }

    /**
     * Saddlebags give a cow torso 15 more slots, filled; taken off again, what no longer fits falls out onto the ground,
     * none of it lost.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void saddlebagsAddStorageKeepsItems(GameTestHelper helper) {
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3),
                flesh().withOrgan(Optional.of(heart(helper, "saddlebags", "bloodandbones:saddlebags"))), 500.0F);
        int base = minion.stats().slots();
        if (minion.slots() != base + 15) {
            helper.fail("Saddlebags should add 15 slots to " + base + ": " + minion.slots());
            return;
        }
        ItemStack marked = new ItemStack(Items.PAPER);
        marked.set(DataComponents.CUSTOM_NAME, Component.literal("saddlebags test"));
        // one to a slot, so what no longer fits cannot squeeze onto another stack
        marked.set(DataComponents.MAX_STACK_SIZE, 1);
        for (int i = 0; i < minion.slots(); i++) {
            minion.inventory.setItem(i, marked.copy());
        }
        int total = minion.slots();
        minion.setBuild(flesh());
        helper.succeedWhen(() -> {
            if (minion.slots() != base) {
                helper.fail("Without saddlebags it should have its torso's " + base + " slots: " + minion.slots());
            }
            int kept = 0;
            for (int i = 0; i < minion.inventory.getContainerSize(); i++) {
                if (ItemStack.isSameItemSameComponents(minion.inventory.getItem(i), marked)) {
                    kept += minion.inventory.getItem(i).getCount();
                    if (i >= base) {
                        helper.fail("Something is still in a slot it no longer has: " + i);
                    }
                }
            }
            int dropped = 0;
            for (ItemEntity item : helper.getEntities(EntityType.ITEM)) {
                if (ItemStack.isSameItemSameComponents(item.getItem(), marked)) {
                    dropped += item.getItem().getCount();
                }
            }
            if (kept != base || kept + dropped != total) {
                helper.fail("It should keep " + base + " and drop the rest of " + total + ": kept " + kept + ", dropped " + dropped);
            }
            minion.discard();
        });
    }

    /**
     * Undying at V saves a player from a killing blow, on 1 health; a second blow inside its five minutes kills.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void undyingSavesOnceThenCooldown(GameTestHelper helper) {
        ResourceLocation mob = helmetMob(helper, "undying", "{\"trait\": \"bloodandbones:undying\", \"level\": 5}");
        Player player = wearing(helper, mob, new BlockPos(2, 2, 2));
        if (ActiveTraits.of(player).level(bb("undying")) != 5) {
            helper.fail("The helmet should give undying V: " + ActiveTraits.of(player).entries());
            return;
        }
        player.hurt(player.damageSources().magic(), 100.0F);
        if (!player.isAlive() || Math.abs(player.getHealth() - 1.0F) > 1.0E-3F) {
            helper.fail("The first killing blow should leave the player on 1 health: " + player.getHealth());
            return;
        }
        player.invulnerableTime = 0;
        player.hurt(player.damageSources().magic(), 100.0F);
        if (!player.isDeadOrDying()) {
            helper.fail("A second killing blow inside the cooldown should kill: " + player.getHealth());
            return;
        }
        helper.succeed();
    }

    /**
     * Nothing saves from what gets past invulnerability (the void, /kill), as with a Totem of Undying: a player with
     * undying V falls out of the world and dies, its cooldown not started, and a minion with it, where minions may die,
     * dies too.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nothingSavesFromTheVoid(GameTestHelper helper) {
        ResourceLocation mob = helmetMob(helper, "undying_void", "{\"trait\": \"bloodandbones:undying\", \"level\": 5}");
        Player player = wearing(helper, mob, new BlockPos(2, 2, 2));
        player.hurt(player.damageSources().fellOutOfWorld(), 100.0F);
        ActiveTraits.Entry entry = null;
        for (ActiveTraits.Entry e : ActiveTraits.of(player).entries()) {
            if (e.id().equals(bb("undying"))) {
                entry = e;
            }
        }
        boolean cooling = entry != null && TraitEvents.coolingDown(player, entry, 0);
        if (!player.isDeadOrDying() || cooling) {
            helper.fail("The void should kill through undying, starting no cooldown: " + player.getHealth() + ", " + cooling);
            return;
        }
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5),
                flesh().withOrgan(Optional.of(heart(helper, "undying_void_minion", "{\"trait\": \"bloodandbones:undying\", \"level\": 5}"))), 500.0F);
        BBServerConfig.MinionDeath before = BBServerConfig.MINION_DEATH.get();
        try {
            BBServerConfig.MINION_DEATH.set(BBServerConfig.MinionDeath.DESTROY);
            minion.hurt(minion.damageSources().genericKill(), 1000.0F);
        } finally {
            BBServerConfig.MINION_DEATH.set(before);
        }
        boolean died = minion.isDeadOrDying();
        minion.discard();
        if (!died) {
            helper.fail("/kill should kill a minion through undying");
            return;
        }
        helper.succeed();
    }

    /**
     * With the server set to destroy minions that are killed, a minion with undying collapses instead: alive on 1 health,
     * powered down. One without it dies.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void undyingMinionCollapsesNotDies(GameTestHelper helper) {
        MinionEntity saved = minion(helper, new BlockPos(2, 2, 2),
                flesh().withOrgan(Optional.of(heart(helper, "undying_minion", "{\"trait\": \"bloodandbones:undying\", \"level\": 5}"))), 500.0F);
        MinionEntity plain = minion(helper, new BlockPos(5, 2, 5), flesh(), 500.0F);
        // the setting is the whole server's: changed and put back within this one call, so no other test sees it
        BBServerConfig.MinionDeath before = BBServerConfig.MINION_DEATH.get();
        try {
            BBServerConfig.MINION_DEATH.set(BBServerConfig.MinionDeath.DESTROY);
            saved.hurt(saved.damageSources().magic(), 1000.0F);
            plain.hurt(plain.damageSources().magic(), 1000.0F);
        } finally {
            BBServerConfig.MINION_DEATH.set(before);
        }
        boolean collapsed = saved.isAlive() && !saved.isRemoved() && saved.poweredDown() && saved.getHealth() >= 1.0F;
        boolean died = plain.isDeadOrDying();
        saved.discard();
        plain.discard();
        if (!collapsed || !died) {
            helper.fail("Undying should collapse the minion (" + collapsed + "), and the plain one should die (" + died + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * sun_cursed: a second and more out of the water, under open sky, by day, its own carcass helmet on, the wearer
     * catches fire; at night they do not. The time is set and put back within one call.
     */
    @GameTest(template = "empty", timeoutTicks = 60, skyAccess = true)
    public static void sunCursedIgnitesInDaylight(GameTestHelper helper) {
        ResourceLocation mob = helmetMob(helper, "sun_cursed", "bloodandbones:sun_cursed");
        Player player = wearing(helper, mob, new BlockPos(2, 2, 2));
        ActiveTraits.updateDry(player);
        helper.runAfterDelay(30, () -> {
            ServerLevel level = helper.getLevel();
            if (!level.canSeeSky(player.blockPosition())) {
                helper.fail("The test needs open sky over the player");
                return;
            }
            long time = level.getDayTime();
            boolean night;
            boolean day;
            try {
                level.setDayTime(18000L);
                TraitEvents.fire(player, Trigger.TICK, null, null, 0.0F);
                night = player.isOnFire();
                level.setDayTime(6000L);
                TraitEvents.fire(player, Trigger.TICK, null, null, 0.0F);
                day = player.isOnFire() && player.getRemainingFireTicks() >= 150;
            } finally {
                level.setDayTime(time);
            }
            if (night || !day) {
                helper.fail("By day under the sky it should burn (" + day + "), at night not (" + night + ")");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * dry_out: after its time out of the water, Slowness and 2 armour less; not while wet. The real trait waits a minute,
     * so this one is its twin that waits a second.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void dryOutAfterSeconds(GameTestHelper helper) {
        Trait real = PartsData.SERVER.trait(bb("dry_out"));
        if (real == null || !(real.effects().get(0).requirements().orElse(null) instanceof TraitConditions.DryFor dry) || dry.seconds() != 60) {
            helper.fail("dry_out should wait 60 seconds dry");
            return;
        }
        ResourceLocation twin = TestTraits.trait(helper, "dry_out_quick", """
                {"name": "trait.bloodandbones.dry_out",
                 "effects": [{"requirements": {"condition": "bloodandbones:dry_for", "seconds": 1},
                              "effect": {"type": "bloodandbones:mob_effect", "effect": "minecraft:slowness", "target": "self"}},
                             {"requirements": {"condition": "bloodandbones:dry_for", "seconds": 1},
                              "effect": {"type": "bloodandbones:attribute", "attribute": "minecraft:generic.armor", "amount": -2}}]}""");
        BlockPos water = new BlockPos(2, 2, 2);
        helper.setBlock(water, Blocks.WATER);
        Player player = wearing(helper, helmetMob(helper, "dry_out", twin.toString()), water);
        TraitEvents.tick(player);
        var armour = player.getAttribute(Attributes.ARMOR);
        if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || armour == null || armour.getModifier(ActiveTraits.modifierId(twin, 1)) != null) {
            helper.fail("In the water it should not be drying out");
            return;
        }
        helper.setBlock(water, Blocks.AIR);
        Vec3 dryPos = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(6, 2, 6)));
        player.setPos(dryPos.x, dryPos.y, dryPos.z);
        helper.runAfterDelay(30, () -> {
            TraitEvents.tick(player);
            if (!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || armour.getModifier(ActiveTraits.modifierId(twin, 1)) == null) {
                helper.fail("A second and a half dry it should be slowed and 2 armour less: " + ActiveTraits.drySeconds(player));
                return;
            }
            helper.succeed();
        });
    }

    /**
     * iron_gut on a minion (a zombie's stomach): rotten flesh by hand is 25 mB of blood; running low, it eats raw beef it
     * carries for the same.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void ironGutRefuelsMinion(GameTestHelper helper) {
        MinionBuild build = flesh().withOrgan(Optional.of(new CarcassArmour.Organ(bb("stomach"), ResourceLocation.withDefaultNamespace("zombie"), false)));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), build, 100.0F);
        if (ActiveTraits.of(minion).level(bb("iron_gut")) < 1) {
            helper.fail("A zombie's stomach should give the minion iron gut: " + ActiveTraits.of(minion).entries());
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ROTTEN_FLESH, 2));
        float before = minion.power();
        minion.interact(player, InteractionHand.MAIN_HAND);
        float fed = minion.power() - before;
        if (Math.abs(fed - 25.0F) > 0.5F || player.getItemInHand(InteractionHand.MAIN_HAND).getCount() != 1) {
            helper.fail("Rotten flesh by hand should be 25 mB: " + fed);
            return;
        }
        minion.inventory.setItem(0, new ItemStack(Items.BEEF, 3));
        float low = minion.power();
        helper.succeedWhen(() -> {
            if (minion.inventory.countItem(Items.BEEF) != 2) {
                helper.fail("Running low, it should eat one beef it carries: " + minion.inventory.countItem(Items.BEEF));
            }
            float gained = minion.power() - low;
            if (gained < 24.0F || gained > 25.5F) {
                helper.fail("The beef should be 25 mB: " + gained);
            }
            minion.discard();
        });
    }

    /**
     * Diet on eating: cookie poison poisons, omnivore fills a little more; seeds are food to a seed eater, eaten at once
     * while hungry.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void dietOnEating(GameTestHelper helper) {
        ResourceLocation mob = helmetMob(helper, "diet", "bloodandbones:cookie_poison", "bloodandbones:omnivore", "bloodandbones:seed_eater");
        Player player = wearing(helper, mob, new BlockPos(2, 2, 2));
        player.getFoodData().setFoodLevel(10);
        player.getFoodData().setSaturation(0.0F);
        ItemStack cookie = new ItemStack(Items.COOKIE);
        player.getFoodData().eat(cookie.getFoodProperties(player));
        float plain = player.getFoodData().getSaturationLevel();
        NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, cookie, 0, ItemStack.EMPTY));
        if (!player.hasEffect(MobEffects.POISON) || player.getFoodData().getSaturationLevel() < plain + 0.99F) {
            helper.fail("A cookie should poison and, to an omnivore, fill 1 more: " + plain + " then " + player.getFoodData().getSaturationLevel());
            return;
        }
        int hunger = player.getFoodData().getFoodLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WHEAT_SEEDS, 2));
        // straight to the handler: other mods' handlers of this event take a mock player for a client one
        com.avicagan.bloodandbones.parts.effect.UpkeepEffects.onUseItem(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
        if (player.getFoodData().getFoodLevel() != hunger + 1 || player.getItemInHand(InteractionHand.MAIN_HAND).getCount() != 1) {
            helper.fail("A seed should be eaten for 1 hunger: " + player.getFoodData().getFoodLevel());
            return;
        }
        helper.succeed();
    }

    /**
     * A flesh minion with a milk udder is milked by hand, by its maker: an empty bucket used on it comes back full, for
     * 50 mB. Anyone else's stays empty and costs it nothing. A hump doubles what a flesh body holds.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void milkByHandAndHump(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity minion = minion(helper, player, new BlockPos(2, 2, 2),
                flesh().withOrgan(Optional.of(heart(helper, "milk_udder", "bloodandbones:milk_udder"))), 500.0F);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        float untouched = minion.power();
        minion.interact(stranger, InteractionHand.MAIN_HAND);
        if (!stranger.getItemInHand(InteractionHand.MAIN_HAND).is(Items.BUCKET) || minion.power() != untouched) {
            minion.discard();
            helper.fail("Only its maker should milk it: " + stranger.getItemInHand(InteractionHand.MAIN_HAND) + ", " + (untouched - minion.power()) + " mB spent");
            return;
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        float before = minion.power();
        minion.interact(player, InteractionHand.MAIN_HAND);
        float paid = before - minion.power();
        minion.discard();
        if (!player.getItemInHand(InteractionHand.MAIN_HAND).is(Items.MILK_BUCKET) || Math.abs(paid - 50.0F) > 0.5F) {
            helper.fail("A bucket should come back full of milk for 50 mB: " + player.getItemInHand(InteractionHand.MAIN_HAND) + ", " + paid);
            return;
        }
        int plain = MinionStats.of(PartsData.SERVER, flesh()).reservoir();
        int humped = MinionStats.of(PartsData.SERVER, flesh().withOrgan(Optional.of(heart(helper, "hump", "bloodandbones:hump")))).reservoir();
        if (Math.abs(humped - 2 * plain) > 1) {
            helper.fail("A hump should double the reservoir: " + plain + " to " + humped);
            return;
        }
        helper.succeed();
    }

    /** Wool regrowth grows a fleece the colour its sheep had (a red one, as its carcass kept it), white with no colour kept. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void woolRegrowsInItsColour(GameTestHelper helper) {
        CarcassArmour.Organ organ = heart(helper, "wool_regrowth", "bloodandbones:wool_regrowth");
        PieceRef red = new PieceRef(ResourceLocation.withDefaultNamespace("cow"), "body", ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"),
                List.of(), 1.0F, false, Map.of("wool", "red"), false);
        MinionEntity dyed = minion(helper, new BlockPos(2, 2, 2), MinionBuild.of(red).withOrgan(Optional.of(organ)), 500.0F);
        MinionEntity plain = minion(helper, new BlockPos(5, 2, 5), flesh().withOrgan(Optional.of(organ)), 500.0F);
        dyed.tickCount = 6000;
        plain.tickCount = 6000;
        TraitEvents.fire(dyed, Trigger.TICK, null, null, 0.0F);
        TraitEvents.fire(plain, Trigger.TICK, null, null, 0.0F);
        int redWool = dyed.inventory.countItem(Items.RED_WOOL);
        int whiteWool = plain.inventory.countItem(Items.WHITE_WOOL);
        dyed.discard();
        plain.discard();
        if (redWool != 1 || whiteWool != 1) {
            helper.fail("A red sheep's fleece should come red, and none kept white: " + redWool + " red, " + whiteWool + " white");
            return;
        }
        helper.succeed();
    }

    /** Mend of the self kind: worn carcass armour knits its wear back when its tick comes round, the piece the trait is on. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mendSelfRepairsArmour(GameTestHelper helper) {
        ResourceLocation knit = TestTraits.trait(helper, "mend_self", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "tick", "interval": 600, "effect": {"type": "bloodandbones:mend", "mode": "self", "amount": 5}}]}""");
        Player player = wearing(helper, helmetMob(helper, "mend_self", knit.toString()), new BlockPos(2, 2, 2));
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        helmet.setDamageValue(12);
        TraitEvents.fire(player, Trigger.TICK, null, null, 0.0F);
        if (player.getItemBySlot(EquipmentSlot.HEAD).getDamageValue() != 7) {
            helper.fail("The helmet should mend 5 of its 12 wear: " + player.getItemBySlot(EquipmentSlot.HEAD).getDamageValue());
            return;
        }
        helper.succeed();
    }
}
