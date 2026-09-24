package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.network.OrganActivatePayload;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.Trait;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.parts.effect.DeflectEffect;
import com.avicagan.bloodandbones.parts.effect.DetonateEffect;
import com.avicagan.bloodandbones.parts.effect.MotionFlags;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.VanillaGameEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Motion's effect types (docs/ARCHITECTURE-PROPOSAL.md section 15.8): flags, impulses, teleports, deflections, blasts
 * and visibility, on players in carcass armour and on minions. Traits and made-up mobs a test needs live under its own
 * ids ({@link TestTraits}); the enderman's helmet and the creeper's powder sac are the real data.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MotionEffectTests {
    private static final ResourceLocation CREEPER = ResourceLocation.withDefaultNamespace("creeper");
    private static final ResourceLocation ENDERMAN = ResourceLocation.withDefaultNamespace("enderman");

    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static PieceRef cow(String bone) {
        return new PieceRef(ResourceLocation.withDefaultNamespace("cow"), bone, ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build) {
        return minion(helper, helper.makeMockPlayer(GameType.SURVIVAL), pos, build);
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

    /** A cow torso with a made-up mob's heart in it, whose minion traits are these. */
    private static MinionBuild withOrganTraits(GameTestHelper helper, String path, String... traits) {
        StringBuilder list = new StringBuilder();
        for (String t : traits) {
            list.append(list.isEmpty() ? "" : ", ").append('"').append(t).append('"');
        }
        ResourceLocation mob = TestTraits.mob(helper, "motion_" + path, "{\"organ_traits\": {\"bloodandbones:heart\": {\"minion\": [" + list + "]}}}");
        return MinionBuild.of(cow("body")).withOrgan(Optional.of(new CarcassArmour.Organ(bb("heart"), mob, false)));
    }

    /** A made-up mob whose parts give armour these traits: its head (helmet), torso (chestplate) and legs (leggings, boots). */
    private static ResourceLocation wornMob(GameTestHelper helper, String path, String slot, String... traits) {
        StringBuilder list = new StringBuilder();
        for (String t : traits) {
            list.append(list.isEmpty() ? "" : ", ").append('"').append(t).append('"');
        }
        return TestTraits.mob(helper, "motion_" + path, "{\"parts\": {\"" + slot + "\": {\"armour\": {\"replace\": true, \"add\": [" + list + "]}}}}");
    }

    /** A blood-filled iron backtank strapped to a chestplate, to pay for its abilities. */
    private static ItemStack strapped(ItemStack chestplate) {
        ItemStack tank = new ItemStack(BBItems.backtank(BacktankTier.IRON));
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.blood(), 1000));
        return FluidBacktankItem.strap(chestplate, tank);
    }

    /** A creeper chestplate with the creeper's powder sac fitted (the component says so; the sac has no item of its own yet). */
    private static ItemStack creeperChestplate() {
        ItemStack chest = TestTraits.piece("chestplate", CREEPER);
        CarcassArmour armour = CarcassArmourItem.armour(chest).withOrgan(Optional.of(new CarcassArmour.Organ(bb("powder_sac"), CREEPER, false)));
        return CarcassArmourItem.make(chest, armour, PartsData.SERVER);
    }

    // ---- flags on the armour's own hooks

    /**
     * An enderman's head scraps make a helmet that is an ender mask (the enderman's data: its head's armour list);
     * a cow's helmet is not; ender mask worn on another piece still calms them, through {@code EnderManAngerEvent}.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void endermanHelmetIsEnderMask(GameTestHelper helper) {
        EnderMan enderman = EntityType.ENDERMAN.create(helper.getLevel());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = TestTraits.piece("helmet", ENDERMAN);
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        ActiveTraits.rebuild(player);
        if (ActiveTraits.of(player).level(bb("ender_mask")) != 1 || !helmet.isEnderMask(player, enderman)) {
            helper.fail("An enderman helmet should be an ender mask: " + ActiveTraits.of(player).entries());
            return;
        }
        ItemStack cowHelmet = TestTraits.piece("helmet", ResourceLocation.withDefaultNamespace("cow"));
        player.setItemSlot(EquipmentSlot.HEAD, cowHelmet);
        ActiveTraits.rebuild(player);
        if (cowHelmet.isEnderMask(player, enderman) || CommonHooks.shouldSuppressEnderManAnger(enderman, player, cowHelmet)) {
            helper.fail("A cow helmet should not be an ender mask");
            return;
        }
        player.setItemSlot(EquipmentSlot.LEGS, TestTraits.piece("leggings", wornMob(helper, "ender_legs", "leg", "bloodandbones:ender_mask")));
        ActiveTraits.rebuild(player);
        if (!CommonHooks.shouldSuppressEnderManAnger(enderman, player, cowHelmet)) {
            helper.fail("Ender mask on the leggings should keep endermen calm under a cow helmet too");
            return;
        }
        helper.succeed();
    }

    /** A minion with ender calm is never taken as an enderman's target; one without is. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void enderCalmMinionNeverTargeted(GameTestHelper helper) {
        MinionEntity calm = minion(helper, new BlockPos(2, 2, 2), withOrganTraits(helper, "ender_calm", "bloodandbones:ender_calm"));
        MinionEntity plain = minion(helper, new BlockPos(8, 2, 8), MinionBuild.of(cow("body")));
        ActiveTraits.of(calm);
        EnderMan enderman = helper.spawn(EntityType.ENDERMAN, new BlockPos(5, 2, 5));
        enderman.setNoAi(true);
        enderman.setTarget(calm);
        boolean calmed = enderman.getTarget() == null;
        enderman.setTarget(plain);
        boolean aimed = enderman.getTarget() == plain;
        enderman.discard();
        calm.discard();
        plain.discard();
        if (!calmed || !aimed) {
            helper.fail("An enderman should not take the calm minion as a target, but should the other: " + calmed + ", " + aimed);
            return;
        }
        helper.succeed();
    }

    // ---- detonate

    /**
     * A creeper chestplate with its powder sac: below half health the Organ Ability sets off a blast (50 mB from the
     * strapped tank) that hurts the zombie beside the wearer and not the wearer, standing in the world where the blast
     * could reach them. With mobGriefing off no block is touched, even by a blast that may break blocks on a server
     * that allows it; with mobGriefing on that one does.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void creeperSacBlastSparesWearer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, strapped(creeperChestplate()));
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(player);
        ActiveTraits.rebuild(player);
        if (ActiveTraits.of(player).level(bb("blast")) != 1) {
            helper.fail("The powder sac should give the chestplate blast: " + ActiveTraits.of(player).entries());
            return;
        }
        player.setHealth(8.0F);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 5));
        zombie.setNoAi(true);
        float zombieHealth = zombie.getHealth();
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean griefed = griefing.get();
        boolean allowed = BBServerConfig.MINION_BLOCK_DAMAGE.get();
        boolean before = floorWhole(helper);
        boolean floorKept;
        boolean floorKept2;
        boolean breaks;
        try {
            // the rule and the setting are the whole server's: changed and put back within this one call
            griefing.set(false, level.getServer());
            OrganActivatePayload.handle(player);
            floorKept = floorWhole(helper);
            BBServerConfig.MINION_BLOCK_DAMAGE.set(true);
            new DetonateEffect(LevelBasedValue.constant(3.0F), false, true, 0, false).blow(player, 3.0F);
            floorKept2 = floorWhole(helper);
            griefing.set(true, level.getServer());
            new DetonateEffect(LevelBasedValue.constant(3.0F), false, true, 0, false).blow(player, 3.0F);
            breaks = !floorWhole(helper);
        } finally {
            griefing.set(griefed, level.getServer());
            BBServerConfig.MINION_BLOCK_DAMAGE.set(allowed);
        }
        float health = player.getHealth();
        int left = FluidBacktankItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
        boolean zombieHurt = !zombie.isAlive() || zombie.getHealth() < zombieHealth;
        player.discard();
        zombie.discard();
        if (health != 8.0F || !zombieHurt || left != 950) {
            helper.fail("The blast should spare its wearer (" + health + " of 8) and hurt the zombie (" + zombieHurt + "), for 50 mB (" + left + " left)");
            return;
        }
        if (!floorKept || !floorKept2 || !breaks) {
            helper.fail("Blocks should break only with mobGriefing on, the server allowing it and the blast asking to: whole before " + before
                    + ", kept " + floorKept + ", " + floorKept2 + ", broke " + breaks);
            return;
        }
        helper.succeed();
    }

    /**
     * A minion's blast spares its own side, as its shoves and shots do: its maker and its maker's other minion beside it
     * take nothing, the zombie beside it is hurt.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void minionBlastSparesItsSide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 2.0, 3.5));
        maker.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(maker);
        MinionEntity bomber = minion(helper, maker, new BlockPos(5, 2, 5), MinionBuild.of(cow("body")));
        MinionEntity sibling = minion(helper, maker, new BlockPos(6, 2, 7), MinionBuild.of(cow("body")));
        bomber.setNoAi(true);
        sibling.setNoAi(true);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 5));
        zombie.setNoAi(true);
        float zombieHealth = zombie.getHealth();
        float makerHealth = maker.getHealth();
        float siblingHealth = sibling.getHealth();
        new DetonateEffect(LevelBasedValue.constant(3.0F), false, false, 0, false).blow(bomber, 3.0F);
        boolean makerSpared = maker.getHealth() == makerHealth;
        boolean siblingSpared = sibling.getHealth() == siblingHealth && !sibling.poweredDown();
        boolean zombieHurt = !zombie.isAlive() || zombie.getHealth() < zombieHealth;
        maker.discard();
        bomber.discard();
        sibling.discard();
        zombie.discard();
        if (!makerSpared || !siblingSpared || !zombieHurt) {
            helper.fail("The blast should spare the maker (" + makerSpared + ") and its other minion (" + siblingSpared + ") and hurt the zombie ("
                    + zombieHurt + ")");
            return;
        }
        helper.succeed();
    }

    /** Whether the stone floor under the middle of the test is all still there. */
    private static boolean floorWhole(GameTestHelper helper) {
        for (int x = 3; x <= 8; x++) {
            for (int z = 3; z <= 8; z++) {
                if (!helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.STONE)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A minion with a creeper's powder sac, its target in reach: it hisses, blows up (the pig next to it hurt, itself not),
     * and powers down where it stands, alive and in the world, never destroyed. No block breaks by default.
     */
    @GameTest(template = "empty", timeoutTicks = 120)
    public static void creeperSacPowersDownNotDestroyed(GameTestHelper helper) {
        // a head, or it would be mindless and never take aim at anything
        MinionBuild build = MinionBuild.of(cow("body")).with("head", cow("head"))
                .withOrgan(Optional.of(new CarcassArmour.Organ(bb("powder_sac"), CREEPER, false)));
        MinionEntity minion = minion(helper, new BlockPos(4, 2, 5), build);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(6, 2, 5));
        pig.setNoAi(true);
        float pigHealth = pig.getHealth();
        minion.setTarget(pig);
        helper.succeedWhen(() -> {
            if (!minion.poweredDown()) {
                if (pig.isAlive() && minion.getTarget() == null) {
                    minion.setTarget(pig);
                }
                helper.fail("The minion has not blown up yet");
            }
            if (minion.isRemoved() || !minion.isAlive() || minion.getHealth() < minion.getMaxHealth()) {
                helper.fail("The minion should be spared by its own blast and lie powered down: " + minion.getHealth() + ", removed " + minion.isRemoved()
                        + ", last hurt by " + (minion.getLastDamageSource() == null ? "nothing" : minion.getLastDamageSource().getMsgId()));
            }
            if (pig.isAlive() && pig.getHealth() >= pigHealth) {
                helper.fail("The pig beside it should have been hurt");
            }
            if (!floorWhole(helper)) {
                helper.fail("No block should break without the server allowing it");
            }
            minion.discard();
            pig.discard();
        });
    }

    // ---- movement flags a client predicts

    /**
     * The climb logic both sides run, on a mock player hanging in the air beside a wall: one piece of wall climber clings
     * (it slides no faster than 0.05 a tick, its fall forgotten), and even holding jump does not climb; two pieces climb
     * at 0.2 while jump is held and cling when it is not. Away from the wall, nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void climbFlagClingsAndClimbs(GameTestHelper helper) {
        ResourceLocation mob = wornMob(helper, "climber", "leg", "bloodandbones:wall_climber");
        helper.setBlock(new BlockPos(5, 3, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 4, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 5, 5), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 wall = helper.absoluteVec(new Vec3(5.5, 3.5, 5.0 - 0.3 - 0.02));
        player.setPos(wall.x, wall.y, wall.z);
        player.setItemSlot(EquipmentSlot.FEET, TestTraits.piece("boots", mob));
        ActiveTraits.rebuild(player);
        if (!MotionFlags.againstWall(player)) {
            helper.fail("The player should be against the wall");
            return;
        }
        player.setDeltaMovement(0.0, -0.5, 0.0);
        player.fallDistance = 4.0F;
        boolean clung = MotionFlags.climb(player, true);
        if (!clung || Math.abs(player.getDeltaMovement().y + MotionFlags.CLING) > 1.0E-6 || player.fallDistance != 0.0F) {
            helper.fail("One piece should cling, sliding at 0.05, and forget the fall: " + player.getDeltaMovement() + ", " + player.fallDistance);
            return;
        }
        player.setItemSlot(EquipmentSlot.LEGS, TestTraits.piece("leggings", mob));
        ActiveTraits.rebuild(player);
        if (ActiveTraits.of(player).level(bb("wall_climber")) != 2) {
            helper.fail("Two pieces of wall climber should sum to level 2: " + ActiveTraits.of(player).entries());
            return;
        }
        player.setDeltaMovement(0.0, -0.5, 0.0);
        MotionFlags.climb(player, true);
        double climbing = player.getDeltaMovement().y;
        player.setDeltaMovement(0.0, -0.5, 0.0);
        MotionFlags.climb(player, false);
        double holding = player.getDeltaMovement().y;
        if (Math.abs(climbing - MotionFlags.CLIMB) > 1.0E-6 || Math.abs(holding + MotionFlags.CLING) > 1.0E-6) {
            helper.fail("Two pieces should climb at 0.2 with jump held, and cling without: " + climbing + ", " + holding);
            return;
        }
        Vec3 away = helper.absoluteVec(new Vec3(5.5, 3.5, 2.5));
        player.setPos(away.x, away.y, away.z);
        player.setDeltaMovement(0.0, -0.5, 0.0);
        if (MotionFlags.climb(player, true) || player.getDeltaMovement().y != -0.5) {
            helper.fail("Away from any wall it should not cling");
            return;
        }
        helper.succeed();
    }

    /**
     * Bouncy boots: a 10-block fall does no damage and sends the wearer back up (again on the tick after, the landing
     * having stopped them); crouching, they land flat and take the fall.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bounceCancelsFall(GameTestHelper helper) {
        ResourceLocation mob = wornMob(helper, "bouncer", "leg", "bloodandbones:bouncy");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.FEET, TestTraits.piece("boots", mob));
        ActiveTraits.rebuild(player);
        boolean hurt = player.causeFallDamage(10.0F, 1.0F, player.damageSources().fall());
        double up = player.getDeltaMovement().y;
        if (hurt || player.getHealth() < player.getMaxHealth() || up < 0.5) {
            helper.fail("A bouncer should take no fall damage and go back up: hurt " + hurt + ", " + player.getHealth() + ", up " + up);
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        MotionFlags.moveTick(player, false);
        if (Math.abs(player.getDeltaMovement().y - up) > 1.0E-6) {
            helper.fail("The bounce should be put back on the tick after the landing stopped it: " + player.getDeltaMovement());
            return;
        }
        player.setShiftKeyDown(true);
        player.setDeltaMovement(Vec3.ZERO);
        hurt = player.causeFallDamage(10.0F, 1.0F, player.damageSources().fall());
        if (!hurt || player.getHealth() >= player.getMaxHealth()) {
            helper.fail("Crouching, it should land flat and take the fall");
            return;
        }
        helper.succeed();
    }

    /** A carcass chestplate with glider glides as an elytra does, on hunger: it stops once too hungry to sprint. Boots never glide. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void gliderChestplateGlidesOnHunger(GameTestHelper helper) {
        ResourceLocation wings = wornMob(helper, "glider", "torso", "bloodandbones:glider");
        ResourceLocation feet = wornMob(helper, "glider_feet", "leg", "bloodandbones:glider");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack chest = TestTraits.piece("chestplate", wings);
        ItemStack boots = TestTraits.piece("boots", feet);
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        ActiveTraits.rebuild(player);
        if (!chest.canElytraFly(player) || boots.canElytraFly(player)) {
            helper.fail("The chestplate should glide and the boots not");
            return;
        }
        float exhaustion = player.getFoodData().getExhaustionLevel();
        boolean going = true;
        for (int tick = 0; tick < 40; tick++) {
            going &= chest.elytraFlightTick(player, tick);
        }
        if (!going || player.getFoodData().getExhaustionLevel() <= exhaustion || chest.getDamageValue() != 0) {
            helper.fail("Gliding should go on, costing hunger and no durability: " + player.getFoodData().getExhaustionLevel() + ", " + chest.getDamageValue());
            return;
        }
        player.getFoodData().setFoodLevel(6);
        if (chest.elytraFlightTick(player, 41)) {
            helper.fail("Too hungry to sprint, the glide should end");
            return;
        }
        helper.succeed();
    }

    // ---- deflect

    /** An arrow shot at a minion that reflects projectiles comes back the way it came, now the minion's, and does it no harm. */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void deflectReflectsArrow(GameTestHelper helper) {
        TestTraits.trait(helper, "motion_deflect_reflect", """
                {"name": "trait.bloodandbones.deflector",
                 "effects": [{"effect": {"type": "bloodandbones:deflect", "mode": "reflect"}}]}""");
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 6), withOrganTraits(helper, "deflect_reflect", "bloodandbones:test/motion_deflect_reflect"));
        minion.setNoAi(true);
        ActiveTraits.of(minion);
        float health = minion.getHealth();
        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        Vec3 start = helper.absoluteVec(new Vec3(5.5, 2.0 + minion.getBbHeight() * 0.6, 3.0));
        arrow.setPos(start.x, start.y, start.z);
        Vec3 aim = minion.position().add(0.0, minion.getBbHeight() * 0.6, 0.0).subtract(start);
        arrow.shoot(aim.x, aim.y, aim.z, 2.5F, 0.0F);
        helper.getLevel().addFreshEntity(arrow);
        helper.succeedWhen(() -> {
            if (arrow.getOwner() != minion) {
                helper.fail("The arrow has not been sent back yet: arrow at " + arrow.position() + ", minion " + minion.getBoundingBox()
                        + ", health " + minion.getHealth());
            }
            if (minion.getHealth() < health) {
                helper.fail("The reflected arrow should not hurt the minion");
            }
            if (arrow.getZ() > minion.getZ() - 0.5) {
                helper.fail("The arrow should be going back toward where it came from");
            }
            arrow.discard();
            minion.discard();
        });
    }

    /** Evasive dodges a creature's own blow (every time, at chance 1) but not magic; the real trait dodges a tenth a level. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void evasiveDodgesMelee(GameTestHelper helper) {
        Trait evasive = PartsData.SERVER.trait(bb("evasive"));
        if (evasive == null || !(evasive.effects().get(0).effect() instanceof DeflectEffect dodge)
                || Math.abs(dodge.chance().calculate(3) - 0.3F) > 1.0E-4 || !dodge.turnsMelee() || dodge.turnsProjectiles()) {
            helper.fail("Evasive should dodge blows only, three tenths of them at level 3");
            return;
        }
        ResourceLocation always = TestTraits.trait(helper, "motion_evasive_always", """
                {"name": "trait.bloodandbones.evasive",
                 "effects": [{"effect": {"type": "bloodandbones:deflect", "mode": "dodge", "against": "melee"}}]}""");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", wornMob(helper, "evasive", "head", always.toString())));
        ActiveTraits.rebuild(player);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        zombie.setNoAi(true);
        boolean hit = player.hurt(player.damageSources().mobAttack(zombie), 4.0F);
        boolean dodged = !hit && player.getHealth() == player.getMaxHealth();
        boolean magic = player.hurt(player.damageSources().magic(), 2.0F);
        zombie.discard();
        if (!dodged || !magic) {
            helper.fail("The zombie's blow should be dodged and magic not: " + dodged + ", " + magic);
            return;
        }
        helper.succeed();
    }

    // ---- teleport

    /** A random blink when hurt lands the minion somewhere else, standing on solid ground, in the clear and dry. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void teleportRandomStaysOnGround(GameTestHelper helper) {
        TestTraits.trait(helper, "motion_teleport_random", """
                {"name": "trait.bloodandbones.rift",
                 "effects": [{"trigger": "hurt", "effect": {"type": "bloodandbones:teleport", "mode": "random", "radius": 4}}]}""");
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), withOrganTraits(helper, "teleport_random", "bloodandbones:test/motion_teleport_random"));
        minion.setNoAi(true);
        ActiveTraits.of(minion);
        Vec3 before = minion.position();
        TraitEvents.fire(minion, Trigger.HURT, null, null, 1.0F);
        Vec3 after = minion.position();
        ServerLevel level = helper.getLevel();
        boolean grounded = level.getBlockState(BlockPos.containing(after.x, after.y - 0.5, after.z)).blocksMotion() && after.y == Math.floor(after.y);
        boolean clear = level.noCollision(minion) && !level.containsAnyLiquid(minion.getBoundingBox());
        minion.discard();
        if (after.distanceToSqr(before) < 0.25 || !grounded || !clear) {
            helper.fail("The blink should land somewhere else, on the ground and in the clear: " + before + " to " + after + ", " + grounded + ", " + clear);
            return;
        }
        helper.succeed();
    }

    // ---- impulse

    /**
     * Leap, the real trait on leggings: the Organ Ability throws the wearer forward (the way they face) and up; the
     * second press finds it cooling down.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void impulseLeapOnActivate(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.LEGS, TestTraits.piece("leggings", wornMob(helper, "leaper", "leg", "bloodandbones:leap")));
        ActiveTraits.rebuild(player);
        player.setYRot(0.0F);
        OrganActivatePayload.handle(player);
        Vec3 v = player.getDeltaMovement();
        if (v.y < 0.6 || v.z < 0.8 || Math.abs(v.x) > 1.0E-3 || !player.hurtMarked) {
            helper.fail("Leap should throw the wearer forward and up: " + v);
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        OrganActivatePayload.handle(player);
        if (player.getDeltaMovement().lengthSqr() > 0.0) {
            helper.fail("A second press should find leap cooling down");
            return;
        }
        helper.succeed();
    }

    /** Wind burst throws the wearer high, and their next landing does no harm; the one after does. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void windBurstCushionsLanding(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.CHEST, strapped(TestTraits.piece("chestplate", wornMob(helper, "wind", "torso", "bloodandbones:wind_burst"))));
        ActiveTraits.rebuild(player);
        OrganActivatePayload.handle(player);
        if (Math.abs(player.getDeltaMovement().y - 1.2) > 1.0E-4) {
            helper.fail("Wind burst should throw the wearer up at 1.2: " + player.getDeltaMovement());
            return;
        }
        player.causeFallDamage(12.0F, 1.0F, player.damageSources().fall());
        float cushioned = player.getHealth();
        player.causeFallDamage(12.0F, 1.0F, player.damageSources().fall());
        if (cushioned < player.getMaxHealth() || player.getHealth() >= player.getMaxHealth()) {
            helper.fail("The first landing should be soft and the next not: " + cushioned + ", " + player.getHealth());
            return;
        }
        helper.succeed();
    }

    // ---- flags read where they act

    /**
     * Silent steps: the wearer's steps, landings and splashes are cancelled as vibrations (the event sculk hears
     * through), their other game events and anyone else's steps are not; a minion with it dampens its vibrations.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void silentStepsNoVibration(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player quiet = helper.makeMockPlayer(GameType.SURVIVAL);
        quiet.setItemSlot(EquipmentSlot.FEET, TestTraits.piece("boots", wornMob(helper, "silent", "leg", "bloodandbones:silent_steps")));
        ActiveTraits.rebuild(quiet);
        Player loud = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
        boolean step = heard(level, GameEvent.STEP, at, quiet);
        boolean landing = heard(level, GameEvent.HIT_GROUND, at, quiet);
        boolean splash = heard(level, GameEvent.SPLASH, at, quiet);
        boolean placing = heard(level, GameEvent.BLOCK_PLACE, at, quiet);
        boolean other = heard(level, GameEvent.STEP, at, loud);
        if (step || landing || splash || !placing || !other) {
            helper.fail("Only the quiet wearer's steps, landings and splashes should go unheard: " + step + landing + splash + placing + other);
            return;
        }
        MinionEntity hushed = minion(helper, new BlockPos(2, 2, 2), withOrganTraits(helper, "silent_minion", "bloodandbones:silent_steps"));
        MinionEntity plain = minion(helper, new BlockPos(8, 2, 8), MinionBuild.of(cow("body")));
        ActiveTraits.of(hushed);
        ActiveTraits.of(plain);
        boolean dampens = hushed.dampensVibrations() && !plain.dampensVibrations();
        hushed.discard();
        plain.discard();
        if (!dampens) {
            helper.fail("A minion with silent steps should dampen its vibrations, and only it");
            return;
        }
        helper.succeed();
    }

    /** Whether a game event from this entity goes through to listeners (sculk, wardens), as the server level sends it. */
    private static boolean heard(ServerLevel level, net.minecraft.core.Holder<GameEvent> event, Vec3 at, Player from) {
        return !NeoForge.EVENT_BUS.post(new VanillaGameEvent(level, event, at, GameEvent.Context.of(from))).isCanceled();
    }

    /**
     * A minion with lava walk dropped onto a pool of lava stands on it (a strider's half-block surface) and is not hurt by
     * the fire; one without sinks to the bottom. Off the lava, fire hurts it as it would any other.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void minionLavaWalkStandsOnLava(GameTestHelper helper) {
        pool(helper, 1, 1);
        pool(helper, 6, 6);
        MinionEntity walker = minion(helper, new BlockPos(2, 4, 2), withOrganTraits(helper, "lava_walk", "bloodandbones:lava_walk"));
        MinionEntity sinker = minion(helper, new BlockPos(7, 4, 7), MinionBuild.of(cow("body")));
        ActiveTraits.of(walker);
        if (!walker.canStandOnFluid(Fluids.LAVA.defaultFluidState()) || sinker.canStandOnFluid(Fluids.LAVA.defaultFluidState())
                || walker.canStandOnFluid(Fluids.WATER.defaultFluidState())) {
            helper.fail("Only the lava walker should stand on lava, and not on water");
            return;
        }
        MinionEntity ashore = minion(helper, new BlockPos(8, 2, 2), withOrganTraits(helper, "lava_walk", "bloodandbones:lava_walk"));
        ActiveTraits.of(ashore);
        float dry = ashore.getHealth();
        ashore.hurt(ashore.damageSources().inFire(), 2.0F);
        boolean burnt = ashore.getHealth() < dry;
        ashore.discard();
        if (!burnt) {
            helper.fail("Off the lava, fire should hurt a lava walker");
            return;
        }
        float health = walker.getHealth();
        double surface = helper.absoluteVec(new Vec3(0.0, 2.5, 0.0)).y;
        // the lowest the other gets: it sinks to the bottom before it paddles up again
        double[] lowest = {sinker.getY()};
        helper.onEachTick(() -> lowest[0] = Math.min(lowest[0], sinker.getY()));
        helper.runAfterDelay(40, () -> {
            double walkerY = walker.getY();
            double sinkerY = lowest[0];
            float walkerHealth = walker.getHealth();
            walker.discard();
            sinker.discard();
            if (Math.abs(walkerY - surface) > 0.1 || walkerHealth < health) {
                helper.fail("The lava walker should stand unhurt on the lava's surface at " + surface + ": " + walkerY + ", " + walkerHealth);
                return;
            }
            if (sinkerY > surface - 0.3) {
                helper.fail("The minion without it should sink: " + sinkerY);
                return;
            }
            helper.succeed();
        });
    }

    /** A 3 by 3 pool of lava one block deep on the floor, walled with stone, its corner at (x, 2, z). */
    private static void pool(GameTestHelper helper, int x, int z) {
        for (int dx = -1; dx <= 3; dx++) {
            for (int dz = -1; dz <= 3; dz++) {
                boolean rim = dx < 0 || dz < 0 || dx > 2 || dz > 2;
                if (x + dx >= 0 && z + dz >= 0 && x + dx <= 10 && z + dz <= 10) {
                    helper.setBlock(new BlockPos(x + dx, 2, z + dz), rim ? Blocks.STONE : Blocks.LAVA);
                }
            }
        }
    }

    /** Hiss halves how far off mobs notice the wearer; without it they see them as usual. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hissHalvesVisibility(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        double plain = player.getVisibilityPercent(zombie);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", wornMob(helper, "hiss", "head", "bloodandbones:hiss")));
        ActiveTraits.rebuild(player);
        double hissing = player.getVisibilityPercent(zombie);
        if (Math.abs(plain - 1.0) > 1.0E-6 || Math.abs(hissing - 0.5) > 1.0E-6) {
            helper.fail("Hiss should halve the wearer's visibility: " + plain + " to " + hissing);
            return;
        }
        helper.succeed();
    }

    /** Quick draw: a bow being drawn gains an extra tick each tick; bread being eaten does not. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void quickDrawDrawsTwiceAsFast(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int plain = EventHooks.onItemUseTick(player, new ItemStack(Items.BOW), 71990);
        player.setItemSlot(EquipmentSlot.CHEST, TestTraits.piece("chestplate", wornMob(helper, "quick_draw", "torso", "bloodandbones:quick_draw")));
        ActiveTraits.rebuild(player);
        int bow = EventHooks.onItemUseTick(player, new ItemStack(Items.BOW), 71990);
        int crossbow = EventHooks.onItemUseTick(player, new ItemStack(Items.CROSSBOW), 20);
        int bread = EventHooks.onItemUseTick(player, new ItemStack(Items.BREAD), 20);
        if (plain != 71990 || bow != 71989 || crossbow != 19 || bread != 20) {
            helper.fail("Only drawing a bow or crossbow should gain a tick: " + plain + ", " + bow + ", " + crossbow + ", " + bread);
            return;
        }
        helper.succeed();
    }

    /** Inverted healing: a healing potion's instant health hurts, and instant damage heals, as for the undead. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void invertedHealingSwaps(GameTestHelper helper) {
        Player plain = helper.makeMockPlayer(GameType.SURVIVAL);
        plain.setHealth(10.0F);
        MobEffects.HEAL.value().applyInstantenousEffect(null, null, plain, 0, 1.0);
        Player cursed = helper.makeMockPlayer(GameType.SURVIVAL);
        cursed.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", wornMob(helper, "inverted", "head", "bloodandbones:inverted_healing")));
        ActiveTraits.rebuild(cursed);
        cursed.setHealth(10.0F);
        MobEffects.HEAL.value().applyInstantenousEffect(null, null, cursed, 0, 1.0);
        float healed = cursed.getHealth();
        MobEffects.HARM.value().applyInstantenousEffect(null, null, cursed, 0, 1.0);
        float harmed = cursed.getHealth();
        if (plain.getHealth() <= 10.0F || healed >= 10.0F || harmed <= healed) {
            helper.fail("Instant health should hurt and instant damage heal: " + plain.getHealth() + ", " + healed + ", " + harmed);
            return;
        }
        helper.succeed();
    }

    /**
     * A trampling minion breaks the leaves against it only where both the server (minion_block_damage) and mobGriefing
     * allow it.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void trampleNeedsGriefingAndConfig(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos leaves = new BlockPos(6, 2, 5);
        helper.setBlock(leaves, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), withOrganTraits(helper, "trample", "bloodandbones:trample"));
        minion.setNoAi(true);
        ActiveTraits.of(minion);
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean griefed = griefing.get();
        boolean allowed = BBServerConfig.MINION_BLOCK_DAMAGE.get();
        int byDefault;
        int noGriefing;
        int both;
        try {
            // the rule and the setting are the whole server's: changed and put back within this one call
            BBServerConfig.MINION_BLOCK_DAMAGE.set(false);
            griefing.set(true, level.getServer());
            byDefault = MotionFlags.trample(minion);
            BBServerConfig.MINION_BLOCK_DAMAGE.set(true);
            griefing.set(false, level.getServer());
            noGriefing = MotionFlags.trample(minion);
            griefing.set(true, level.getServer());
            both = MotionFlags.trample(minion);
        } finally {
            griefing.set(griefed, level.getServer());
            BBServerConfig.MINION_BLOCK_DAMAGE.set(allowed);
        }
        boolean gone = helper.getBlockState(leaves).isAir();
        minion.discard();
        if (byDefault != 0 || noGriefing != 0 || both < 1 || !gone) {
            helper.fail("It should trample only with both allowed: " + byDefault + ", " + noGriefing + ", " + both + ", gone " + gone);
            return;
        }
        helper.succeed();
    }
}
