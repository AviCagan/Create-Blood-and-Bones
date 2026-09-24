package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.effect.SenseEffect;
import com.avicagan.bloodandbones.registry.BBEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Social's effect types (docs/ARCHITECTURE-PROPOSAL.md section 15.8): kin keeps its mobs off the carrier until it hurts
 * one, golems defend the beloved, pack hits harder for allies near, auras heal allies, pull items, calm and rally, the
 * alert sense pings, and a minion hunts by echolocation through a wall. Traits made for a test live under its own ids
 * ({@link TestTraits}).
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class SocialEffectTests {
    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static PieceRef ref(String entity, String bone) {
        return new PieceRef(ResourceLocation.withDefaultNamespace(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + entity + ".png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    private static MinionEntity minion(GameTestHelper helper, Player maker, BlockPos pos, MinionBuild build) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, build, 1000.0F);
        level.addFreshEntity(minion);
        return minion;
    }

    /** A mock player standing at this spot of the test (mock players are not in the world, so they never move or tick). */
    private static Player player(GameTestHelper helper, BlockPos pos) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(pos));
        player.setPos(at.x, at.y, at.z);
        return player;
    }

    /** A helmet of a made-up mob whose head gives only this trait. */
    private static ItemStack helmet(GameTestHelper helper, String path, ResourceLocation trait) {
        ResourceLocation mob = TestTraits.mob(helper, path, "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [\"" + trait + "\"]}}}}");
        return TestTraits.piece("helmet", mob);
    }

    /** An organ of a made-up mob that gives a minion only this trait. */
    private static Optional<CarcassArmour.Organ> organ(GameTestHelper helper, String path, ResourceLocation trait) {
        ResourceLocation mob = TestTraits.mob(helper, path, "{\"organ_traits\": {\"bloodandbones:heart\": {\"minion\": [\"" + trait + "\"]}}}");
        return Optional.of(new CarcassArmour.Organ(bb("heart"), mob, false));
    }

    private static Zombie zombie(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos);
        // a hat, so the sun does not set it burning
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return zombie;
    }

    /**
     * A full set of zombie armour has the Shambler set's dead_face: a zombie's aim at the wearer is called off, until the
     * wearer hits it, and then it turns on them by itself.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void fullZombieSetKin(GameTestHelper helper) {
        ResourceLocation zombieId = ResourceLocation.withDefaultNamespace("zombie");
        Player player = player(helper, new BlockPos(3, 2, 5));
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", zombieId));
        player.setItemSlot(EquipmentSlot.CHEST, TestTraits.piece("chestplate", zombieId));
        player.setItemSlot(EquipmentSlot.LEGS, TestTraits.piece("leggings", zombieId));
        player.setItemSlot(EquipmentSlot.FEET, TestTraits.piece("boots", zombieId));
        ActiveTraits traits = ActiveTraits.rebuild(player);
        if (traits.level(bb("dead_face")) < 1) {
            helper.fail("A full set of zombie should have dead_face: " + traits.entries());
            return;
        }
        Zombie zombie = zombie(helper, new BlockPos(7, 2, 5));
        zombie.setTarget(player);
        if (zombie.getTarget() != null) {
            helper.fail("A zombie should not set its sights on a wearer of the full zombie set");
            return;
        }
        // hit a few ticks on (a mob hurt on its first tick never notices: its revenge goal reads the tick it was hurt on)
        helper.startSequence()
                .thenExecuteAfter(5, () -> zombie.hurt(player.damageSources().playerAttack(player), 1.0F))
                .thenWaitUntil(() -> {
                    if (zombie.getTarget() != player) {
                        helper.fail("Hit by the wearer, the zombie should turn on them");
                    }
                })
                .thenExecute(zombie::discard)
                .thenSucceed();
    }

    /** Kin with a short window: a husk hurt by the wearer may go for them at once, and may not once the window has passed. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void kinProvokedWindowExpires(GameTestHelper helper) {
        ResourceLocation kin = TestTraits.trait(helper, "social_kin_window", """
                {"name": "trait.bloodandbones.dead_face",
                 "effects": [{"effect": {"type": "bloodandbones:kin", "entities": "minecraft:husk", "provoked_seconds": 1}}]}""");
        Player player = player(helper, new BlockPos(3, 2, 5));
        player.setItemSlot(EquipmentSlot.HEAD, helmet(helper, "social_kin_window", kin));
        ActiveTraits.rebuild(player);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(7, 2, 5));
        husk.setNoAi(true);
        husk.setTarget(player);
        if (husk.getTarget() != null) {
            helper.fail("An unprovoked husk should leave its kin be");
            return;
        }
        husk.hurt(player.damageSources().playerAttack(player), 1.0F);
        husk.setTarget(player);
        if (husk.getTarget() != player) {
            helper.fail("Just hurt by the wearer, the husk may go for them");
            return;
        }
        husk.setTarget(null);
        helper.runAfterDelay(30, () -> {
            husk.setTarget(player);
            if (husk.getTarget() != null) {
                helper.fail("A second and a half later, past the one-second window, the husk should leave its kin be again");
                return;
            }
            husk.discard();
            helper.succeed();
        });
    }

    /** Pack: 15% an ally near, counted up to 3; with none the hit is as it was. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void packHunterScalesWithAllies(GameTestHelper helper) {
        ResourceLocation pack = TestTraits.trait(helper, "social_pack", """
                {"name": "trait.bloodandbones.pack_hunter",
                 "effects": [{"effect": {"type": "bloodandbones:pack", "per_ally": 0.15, "allies": "minecraft:wolf", "radius": 3, "cap": 3}}]}""");
        Player player = player(helper, new BlockPos(5, 2, 5));
        player.setItemSlot(EquipmentSlot.HEAD, helmet(helper, "social_pack", pack));
        ActiveTraits.rebuild(player);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 7));
        pig.setNoAi(true);
        float alone = hit(player, pig);
        List<Wolf> wolves = new java.util.ArrayList<>();
        for (BlockPos at : List.of(new BlockPos(4, 2, 5), new BlockPos(6, 2, 5))) {
            Wolf wolf = helper.spawn(EntityType.WOLF, at);
            wolf.setNoAi(true);
            wolves.add(wolf);
        }
        float two = hit(player, pig);
        for (BlockPos at : List.of(new BlockPos(5, 2, 4), new BlockPos(4, 2, 4))) {
            Wolf wolf = helper.spawn(EntityType.WOLF, at);
            wolf.setNoAi(true);
            wolves.add(wolf);
        }
        float four = hit(player, pig);
        wolves.forEach(Wolf::discard);
        pig.discard();
        if (Math.abs(alone - 2.0F) > 0.01F || Math.abs(two - 2.6F) > 0.01F || Math.abs(four - 2.9F) > 0.01F) {
            helper.fail("A 2-damage hit should do 2 alone, 2.6 with two wolves and 2.9 with four (three count): " + alone + ", " + two + ", " + four);
            return;
        }
        helper.succeed();
    }

    /** How much a 2-damage hit from the player takes off the pig, from full health. */
    private static float hit(Player player, Pig pig) {
        pig.setHealth(pig.getMaxHealth());
        pig.invulnerableTime = 0;
        float before = pig.getHealth();
        pig.hurt(player.damageSources().playerAttack(player), 2.0F);
        return before - pig.getHealth();
    }

    /** A purring aura gives the wearer's own minion Regeneration, and leaves a zombie beside it alone. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void auraPurrHealsAllies(GameTestHelper helper) {
        ResourceLocation purr = TestTraits.trait(helper, "social_purr", """
                {"name": "trait.bloodandbones.purr",
                 "effects": [{"effect": {"type": "bloodandbones:aura", "action": "mob_effect", "effect": "minecraft:regeneration",
                                         "duration": 100, "radius": 4, "interval": 200, "filter": "allies"}}]}""");
        Player player = player(helper, new BlockPos(5, 2, 5));
        player.setItemSlot(EquipmentSlot.HEAD, helmet(helper, "social_purr", purr));
        ActiveTraits.rebuild(player);
        MinionEntity minion = minion(helper, player, new BlockPos(7, 2, 5), MinionBuild.of(ref("cow", "body")));
        minion.setHealth(minion.getMaxHealth() * 0.5F);
        Zombie zombie = zombie(helper, new BlockPos(3, 2, 5));
        zombie.setNoAi(true);
        TraitEvents.tick(player);
        boolean healing = minion.hasEffect(MobEffects.REGENERATION);
        boolean stranger = zombie.hasEffect(MobEffects.REGENERATION);
        minion.discard();
        zombie.discard();
        if (!healing || stranger) {
            helper.fail("The purr should give the wearer's minion Regeneration and not the zombie: " + healing + ", " + stranger);
            return;
        }
        helper.succeed();
    }

    /** An item magnet makes a loose item four blocks off hop to the wearer. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void itemMagnetPullsItems(GameTestHelper helper) {
        ResourceLocation magnet = TestTraits.trait(helper, "social_magnet", """
                {"name": "trait.bloodandbones.item_magnet",
                 "effects": [{"effect": {"type": "bloodandbones:aura", "action": "pull_items", "radius": 6, "interval": 20}}]}""");
        Player player = player(helper, new BlockPos(5, 2, 5));
        player.setItemSlot(EquipmentSlot.HEAD, helmet(helper, "social_magnet", magnet));
        ActiveTraits.rebuild(player);
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(5, 2, 1)));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.BONE));
        item.setDeltaMovement(Vec3.ZERO);
        item.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(item);
        helper.runAfterDelay(5, () -> {
            double before = item.distanceTo(player);
            TraitEvents.tick(player);
            helper.runAfterDelay(20, () -> {
                double after = item.distanceTo(player);
                item.discard();
                if (after > before - 2.0) {
                    helper.fail("The item should have hopped most of the way to the wearer: " + before + " to " + after);
                    return;
                }
                helper.succeed();
            });
        });
    }

    /**
     * A calming aura: a zombie going for the wearer gives up and cannot take aim again while calm, until the wearer hits
     * it; a zombie going for a pig is left to it.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void calmAuraClearsTargets(GameTestHelper helper) {
        ResourceLocation calm = TestTraits.trait(helper, "social_calm", """
                {"name": "trait.bloodandbones.play_dead",
                 "effects": [{"effect": {"type": "bloodandbones:aura", "action": "calm", "radius": 8, "duration": 100, "filter": "hostile"}}]}""");
        Player player = player(helper, new BlockPos(5, 2, 5));
        player.setItemSlot(EquipmentSlot.HEAD, helmet(helper, "social_calm", calm));
        ActiveTraits.rebuild(player);
        Zombie after = zombie(helper, new BlockPos(3, 2, 5));
        after.setNoAi(true);
        after.setTarget(player);
        Zombie other = zombie(helper, new BlockPos(7, 2, 5));
        other.setNoAi(true);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(8, 2, 7));
        pig.setNoAi(true);
        other.setTarget(pig);
        if (after.getTarget() != player || other.getTarget() != pig) {
            helper.fail("Both zombies should have their targets to begin with");
            return;
        }
        TraitEvents.tick(player);
        if (after.getTarget() != null || other.getTarget() != pig) {
            helper.fail("The calm should call off the zombie after the wearer and leave the one after the pig: " + after.getTarget() + ", " + other.getTarget());
            return;
        }
        after.setTarget(player);
        if (after.getTarget() != null) {
            helper.fail("While calm, the zombie should not take aim at the wearer again");
            return;
        }
        after.hurt(player.damageSources().playerAttack(player), 1.0F);
        after.setTarget(player);
        boolean back = after.getTarget() == player;
        after.discard();
        other.discard();
        pig.discard();
        if (!back) {
            helper.fail("Hit by the wearer, the zombie is calm no longer and may go for them");
            return;
        }
        helper.succeed();
    }

    /**
     * A minion with a rallying organ is hit by a pig: its fellow minion (the same maker) and a husk near it, of the kind
     * the rally names, turn on the pig.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rallyAuraSetsAlliesOnAttacker(GameTestHelper helper) {
        ResourceLocation rally = TestTraits.trait(helper, "social_rally", """
                {"name": "trait.bloodandbones.horde_call",
                 "effects": [{"trigger": "hurt", "effect": {"type": "bloodandbones:aura", "action": "rally", "radius": 8,
                                                             "filter": ["allies", "minecraft:husk"]}}]}""");
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity host = minion(helper, maker, new BlockPos(5, 2, 5), MinionBuild.of(ref("cow", "body")).withOrgan(organ(helper, "social_rally", rally)));
        MinionEntity ally = minion(helper, maker, new BlockPos(2, 2, 5), MinionBuild.of(ref("cow", "body")));
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(5, 2, 8));
        husk.setNoAi(true);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(8, 2, 5));
        pig.setNoAi(true);
        ActiveTraits.of(host);
        host.hurt(helper.getLevel().damageSources().mobAttack(pig), 1.0F);
        boolean allyTurned = ally.getTarget() == pig;
        boolean huskTurned = husk.getTarget() == pig;
        host.discard();
        ally.discard();
        husk.discard();
        pig.discard();
        if (!allyTurned || !huskTurned) {
            helper.fail("Hurt by the pig, the minion should rally its fellow minion and the husk onto it: " + allyTurned + ", " + huskTurned);
            return;
        }
        helper.succeed();
    }

    /**
     * The beloved trait's reactions: an iron golem will not take aim at a beloved minion (friendly), and goes for the pig
     * that hurt it (defend).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void belovedGolemsDefend(GameTestHelper helper) {
        ResourceLocation beloved = bb("beloved");
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity host = minion(helper, maker, new BlockPos(3, 2, 5), MinionBuild.of(ref("cow", "body")).withOrgan(organ(helper, "social_beloved", beloved)));
        IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(7, 2, 5));
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 8));
        pig.setNoAi(true);
        if (ActiveTraits.of(host).level(beloved) < 1) {
            helper.fail("The minion's organ should make it beloved: " + ActiveTraits.of(host).entries());
            return;
        }
        golem.setTarget(host);
        if (golem.getTarget() != null) {
            helper.fail("An iron golem should not take aim at a beloved minion");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(5, () -> host.hurt(helper.getLevel().damageSources().mobAttack(pig), 1.0F))
                .thenWaitUntil(() -> {
                    if (golem.getTarget() != pig) {
                        helper.fail("The golem should go for the pig that hurt the beloved minion");
                    }
                })
                .thenExecute(() -> {
                    host.discard();
                    golem.discard();
                    pig.discard();
                })
                .thenSucceed();
    }

    /** An alert sense pings when a zombie within its reach takes aim at the wearer, and not for one beyond it. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void alertSendsPing(GameTestHelper helper) {
        ResourceLocation alert = TestTraits.trait(helper, "social_alert", """
                {"name": "trait.bloodandbones.wide_eyes",
                 "effects": [{"effect": {"type": "bloodandbones:sense", "kind": "alert", "range": 4}}]}""");
        Player player = player(helper, new BlockPos(5, 2, 2));
        player.setItemSlot(EquipmentSlot.HEAD, helmet(helper, "social_alert", alert));
        ActiveTraits.rebuild(player);
        Zombie near = zombie(helper, new BlockPos(5, 2, 5));
        near.setNoAi(true);
        Zombie far = zombie(helper, new BlockPos(5, 2, 9));
        far.setNoAi(true);
        near.setTarget(player);
        far.setTarget(player);
        boolean pinged = SenseEffect.alerted(player, near);
        boolean farPinged = SenseEffect.alerted(player, far);
        near.discard();
        far.discard();
        if (!pinged || farPinged) {
            helper.fail("The zombie 3 blocks off should ping and the one 7 off should not: " + pinged + ", " + farPinged);
            return;
        }
        helper.succeed();
    }

    /**
     * A guard minion with an echolocating organ finds a zombie walled up in stone within its range and takes it for its
     * target; the same guard without the organ never does.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void echolocateMinionTargetsThroughWall(GameTestHelper helper) {
        ResourceLocation echo = TestTraits.trait(helper, "social_echo", """
                {"name": "trait.bloodandbones.echo_sense",
                 "effects": [{"effect": {"type": "bloodandbones:sense", "kind": "echolocate", "range": 8}}]}""");
        // a stone cell with the zombie in it: nothing sees in or out
        for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(7, 2, 3), new BlockPos(9, 5, 5))) {
            helper.setBlock(pos, Blocks.STONE);
        }
        helper.setBlock(new BlockPos(8, 3, 4), Blocks.AIR);
        helper.setBlock(new BlockPos(8, 4, 4), Blocks.AIR);
        Zombie zombie = zombie(helper, new BlockPos(8, 3, 4));
        zombie.setNoAi(true);
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionBuild guardBody = MinionBuild.of(ref("zombie", "body")).with("head", ref("zombie", "head"));
        MinionEntity hearing = minion(helper, maker, new BlockPos(3, 2, 4), guardBody.withOrgan(organ(helper, "social_echo", echo)));
        MinionEntity deaf = minion(helper, maker, new BlockPos(3, 2, 8), guardBody);
        if (!hearing.setJob(bb("guard")) || !deaf.setJob(bb("guard"))) {
            helper.fail("A zombie's head should offer the guard job: " + hearing.stats().jobs());
            return;
        }
        helper.succeedWhen(() -> {
            if (hearing.getTarget() != zombie) {
                if (hearing.getTarget() != null) {
                    // something of another test's in sight; let it look again
                    hearing.setTarget(null);
                }
                helper.fail("The echolocating guard has not found the walled-up zombie yet");
            }
            if (deaf.getTarget() == zombie) {
                helper.fail("A guard without the sense should not find a zombie it cannot see");
            }
            hearing.discard();
            deaf.discard();
            zombie.discard();
        });
    }
}
