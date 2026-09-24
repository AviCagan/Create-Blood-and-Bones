package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.parts.effect.MotionFlags;
import com.avicagan.bloodandbones.parts.effect.RangedContent;
import com.avicagan.bloodandbones.parts.effect.UpkeepEffects;
import com.avicagan.bloodandbones.registry.BBEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where the four effect groups meet (docs/ARCHITECTURE-PROPOSAL.md section 15.8, the merge): the traits two groups both
 * wrote, the handlers two groups hang on one event, the setting one group made and another reads, and the traits the
 * merge let through. Traits and made-up mobs a test needs live under its own ids ({@link TestTraits}).
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MergedEffectTests {
    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static PieceRef cow(String bone) {
        return new PieceRef(ResourceLocation.withDefaultNamespace("cow"), bone, ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    private static MinionEntity minion(GameTestHelper helper, Player maker, BlockPos pos, MinionBuild build) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, build, 1000.0F);
        level.addFreshEntity(minion);
        minion.setNoAi(true);
        return minion;
    }

    /** A cow torso with a made-up mob's heart in it, whose minion traits are these. */
    private static MinionBuild withOrganTraits(GameTestHelper helper, String path, String... traits) {
        ResourceLocation mob = TestTraits.mob(helper, "merged_" + path, "{\"organ_traits\": {\"bloodandbones:heart\": {\"minion\": [" + quoted(traits) + "]}}}");
        return MinionBuild.of(cow("body")).withOrgan(Optional.of(new CarcassArmour.Organ(bb("heart"), mob, false)));
    }

    /** A made-up mob whose head gives armour these traits (its helmet). */
    private static ResourceLocation headMob(GameTestHelper helper, String path, String... traits) {
        return TestTraits.mob(helper, "merged_" + path, "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [" + quoted(traits) + "]}}}}");
    }

    private static String quoted(String... traits) {
        StringBuilder list = new StringBuilder();
        for (String t : traits) {
            list.append(list.isEmpty() ? "" : ", ").append('"').append(t).append('"');
        }
        return list.toString();
    }

    // ---- one trait, two groups

    /**
     * piglin_kin carries both halves: Motion's piglin_neutral flag (vanilla's gold check, on a worn piece) and Social's kin
     * (brutes too, which gold never calms, and a minion as well as a player).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void piglinKinBothHalves(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack helmet = TestTraits.piece("helmet", headMob(helper, "piglin_head", "bloodandbones:piglin_kin"));
        player.setItemSlot(EquipmentSlot.HEAD, helmet);
        ActiveTraits.rebuild(player);
        MinionEntity minion = minion(helper, player, new BlockPos(8, 2, 8), withOrganTraits(helper, "piglin_organ", "bloodandbones:piglin_kin"));
        ActiveTraits.of(minion);
        PiglinBrute brute = helper.spawn(EntityType.PIGLIN_BRUTE, new BlockPos(5, 2, 5));
        brute.setNoAi(true);
        Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos(3, 2, 7));
        piglin.setNoAi(true);
        boolean neutral = helmet.makesPiglinsNeutral(player);
        brute.setTarget(player);
        boolean bruteSpared = brute.getTarget() == null;
        piglin.setTarget(minion);
        boolean minionSpared = piglin.getTarget() == null;
        brute.discard();
        piglin.discard();
        minion.discard();
        if (!neutral || !bruteSpared || !minionSpared) {
            helper.fail("piglin_kin should make piglins neutral as gold does and keep brutes and piglins off its carriers: "
                    + neutral + ", " + bruteSpared + ", " + minionSpared);
            return;
        }
        helper.succeed();
    }

    // ---- handlers meeting on one event

    /**
     * Motion's ender mask calls an enderman off a minion before the targeted trigger looks, so the minion's alert never
     * goes off for a target that never was; a zombie taking aim at the same kind of minion does set it off.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void calledOffTargetFiresNoAlert(GameTestHelper helper) {
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity masked = minion(helper, maker, new BlockPos(2, 2, 2), withOrganTraits(helper, "mask_alert", "bloodandbones:ender_mask", "bloodandbones:alert"));
        MinionEntity plain = minion(helper, maker, new BlockPos(8, 2, 8), withOrganTraits(helper, "alert", "bloodandbones:alert"));
        ActiveTraits.of(masked);
        ActiveTraits.of(plain);
        EnderMan enderman = helper.spawn(EntityType.ENDERMAN, new BlockPos(5, 2, 5));
        enderman.setNoAi(true);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 2, 5));
        zombie.setNoAi(true);
        enderman.setTarget(masked);
        boolean calledOff = enderman.getTarget() == null && !masked.hasEffect(MobEffects.MOVEMENT_SPEED);
        zombie.setTarget(plain);
        boolean alerted = zombie.getTarget() == plain && plain.hasEffect(MobEffects.MOVEMENT_SPEED);
        enderman.discard();
        zombie.discard();
        masked.discard();
        plain.discard();
        if (!calledOff || !alerted) {
            helper.fail("A target called off should set off no alert, and one let through should: " + calledOff + ", " + alerted);
            return;
        }
        helper.succeed();
    }

    /**
     * A minion's shot passes through a fellow minion before that one's deflector (Motion's) sees it, so it is neither sent
     * back nor made the deflector's; a stranger's arrow at the same minion is sent back.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void friendlyShotPassesFriendsDeflector(GameTestHelper helper) {
        ResourceLocation deflect = TestTraits.trait(helper, "merged_deflect", """
                {"name": "trait.bloodandbones.deflector",
                 "effects": [{"effect": {"type": "bloodandbones:deflect", "mode": "reflect"}}]}""");
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity shooter = minion(helper, maker, new BlockPos(2, 2, 5), MinionBuild.of(cow("body")));
        MinionEntity friend = minion(helper, maker, new BlockPos(6, 2, 5), withOrganTraits(helper, "deflect", deflect.toString()));
        ActiveTraits.of(friend);
        Arrow ours = arrow(helper, shooter, friend);
        ours.setData(RangedContent.SHOT, 2.0F);
        boolean passed = EventHooks.onProjectileImpact(ours, new EntityHitResult(friend));
        Zombie stranger = EntityType.ZOMBIE.create(helper.getLevel());
        Arrow theirs = arrow(helper, stranger, friend);
        boolean turned = EventHooks.onProjectileImpact(theirs, new EntityHitResult(friend));
        boolean ownerKept = ours.getOwner() == shooter;
        boolean madeFriends = theirs.getOwner() == friend;
        ours.discard();
        theirs.discard();
        shooter.discard();
        friend.discard();
        if (!passed || !ownerKept || !turned || !madeFriends) {
            helper.fail("A friend's shot should pass untouched and a stranger's be sent back: " + passed + ", " + ownerKept + ", "
                    + turned + ", " + madeFriends);
            return;
        }
        helper.succeed();
    }

    private static Arrow arrow(GameTestHelper helper, LivingEntity owner, LivingEntity at) {
        Arrow arrow = EntityType.ARROW.create(helper.getLevel());
        arrow.setOwner(owner);
        arrow.setPos(at.getX() - 1.0, at.getY() + 0.5, at.getZ());
        arrow.setDeltaMovement(1.5, 0.0, 0.0);
        return arrow;
    }

    // ---- one group's setting, another's shots and blocks

    /**
     * A minion's shot (Ranged) breaks blocks only where Motion's minion_block_damage and mobGriefing both allow it, as a
     * minion's blast and trampling do; and trampling tears Ranged's temporary web as it does a cobweb.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void minionBlockDamageCoversShotsAndWebs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos web = new BlockPos(6, 2, 5);
        helper.setBlock(web, RangedContent.TEMPORARY_WEB.get().defaultBlockState());
        MinionEntity minion = minion(helper, maker, new BlockPos(5, 2, 5), withOrganTraits(helper, "trample", "bloodandbones:trample"));
        ActiveTraits.of(minion);
        Arrow shot = EntityType.ARROW.create(level);
        shot.setOwner(minion);
        shot.setData(RangedContent.SHOT, 2.0F);
        GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
        boolean griefed = griefing.get();
        boolean allowed = BBServerConfig.MINION_BLOCK_DAMAGE.get();
        boolean byDefault;
        boolean noGriefing;
        boolean both;
        int trampled;
        try {
            // the rule and the setting are the whole server's: changed and put back within this one call
            griefing.set(true, level.getServer());
            BBServerConfig.MINION_BLOCK_DAMAGE.set(false);
            byDefault = EventHooks.canEntityGrief(level, shot);
            BBServerConfig.MINION_BLOCK_DAMAGE.set(true);
            both = EventHooks.canEntityGrief(level, shot);
            trampled = MotionFlags.trample(minion);
            griefing.set(false, level.getServer());
            noGriefing = EventHooks.canEntityGrief(level, shot);
        } finally {
            griefing.set(griefed, level.getServer());
            BBServerConfig.MINION_BLOCK_DAMAGE.set(allowed);
        }
        boolean torn = helper.getBlockState(web).isAir();
        shot.discard();
        minion.discard();
        if (byDefault || noGriefing || !both) {
            helper.fail("A minion's shot should grief only with both allowed: " + byDefault + ", " + noGriefing + ", " + both);
            return;
        }
        if (trampled < 1 || !torn) {
            helper.fail("Trampling should tear a temporary web: " + trampled + ", torn " + torn);
            return;
        }
        helper.succeed();
    }

    // ---- what the merge let through

    /**
     * The bee's honey stomach grows a crop within 4 each minute (Social's bonemeal aura in Upkeep's trait): a field of
     * young wheat round the minion has some of it grown within a few pulses.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void honeyStomachGrowsCrops(GameTestHelper helper) {
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.WHEAT);
            }
        }
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity minion = minion(helper, maker, new BlockPos(5, 2, 5), withOrganTraits(helper, "honey", "bloodandbones:honey_stomach"));
        if (ActiveTraits.of(minion).level(bb("honey_stomach")) != 1) {
            helper.fail("The organ should give the honey stomach: " + ActiveTraits.of(minion).entries());
            return;
        }
        helper.succeedWhen(() -> {
            // the aura goes off at most once a second however often it is asked
            TraitEvents.fire(minion, Trigger.TICK, null, null, 0.0F);
            int grown = 0;
            for (int x = 1; x <= 9; x++) {
                for (int z = 1; z <= 9; z++) {
                    if (helper.getBlockState(new BlockPos(x, 2, z)).getValue(CropBlock.AGE) > 0) {
                        grown++;
                    }
                }
            }
            if (grown == 0) {
                helper.fail("No wheat has grown yet");
            }
            minion.discard();
        });
    }

    /** lean lowers Upkeep's blood_upkeep attribute a tenth a level, and with it what a minion drinks. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void leanLowersUpkeep(GameTestHelper helper) {
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity lean = minion(helper, maker, new BlockPos(3, 2, 3), withOrganTraits(helper, "lean", "bloodandbones:lean"));
        MinionEntity plain = minion(helper, maker, new BlockPos(7, 2, 7), MinionBuild.of(cow("body")));
        ActiveTraits.of(lean);
        ActiveTraits.of(plain);
        double upkeep = lean.getAttributeValue(UpkeepEffects.BLOOD_UPKEEP);
        float drain = UpkeepEffects.drainMultiplier(lean);
        float plainDrain = UpkeepEffects.drainMultiplier(plain);
        lean.discard();
        plain.discard();
        if (Math.abs(upkeep - 0.9) > 1.0E-4 || Math.abs(drain - 0.9F * plainDrain) > 1.0E-4) {
            helper.fail("Lean should take a tenth off blood upkeep: " + upkeep + ", drain " + drain + " against " + plainDrain);
            return;
        }
        helper.succeed();
    }
}
