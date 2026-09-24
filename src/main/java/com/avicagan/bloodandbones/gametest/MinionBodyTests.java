package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.minion.BloodTroughBlockEntity;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The brief's minion parts at work (docs/PARTS-AND-TRAITS.md section 6.4): legs set movement (spider legs climb, horse
 * legs are rideable), arms set the attack, a flying torso flies.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinionBodyTests {
    private static PieceRef ref(String entity, String bone) {
        return new PieceRef(ResourceLocation.withDefaultNamespace(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + entity + ".png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    private static MinionBuild cow() {
        return MinionBuild.of(ref("cow", "body")).with("head", ref("cow", "head"));
    }

    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build, float blood, Player maker) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, build, blood);
        level.addFreshEntity(minion);
        return minion;
    }

    /** A cow on spider legs climbs; on its own legs it does not. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spiderLegsMakeItClimb(GameTestHelper helper) {
        MinionBuild spiderLegs = cow().with("right_front_leg", ref("spider", "right_front_leg")).with("left_front_leg", ref("spider", "left_front_leg"))
                .with("right_hind_leg", ref("spider", "right_hind_leg")).with("left_hind_leg", ref("spider", "left_hind_leg"));
        MinionBuild cowLegs = cow().with("right_front_leg", ref("cow", "right_front_leg")).with("left_front_leg", ref("cow", "left_front_leg"));
        if (!MinionStats.of(PartsData.SERVER, spiderLegs).climbs() || MinionStats.of(PartsData.SERVER, cowLegs).climbs()) {
            helper.fail("Spider legs should climb and cow legs not");
            return;
        }
        helper.succeed();
    }

    /** Hungry, a cow on spider legs goes straight up a wall three high to the trough on the other side. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void spiderLegsClimbAWallToTheTrough(GameTestHelper helper) {
        for (int z = 0; z <= 10; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        helper.setBlock(new BlockPos(9, 2, 5), BBBlocks.BLOOD_TROUGH.getDefaultState());
        BloodTroughBlockEntity trough = (BloodTroughBlockEntity) helper.getBlockEntity(new BlockPos(9, 2, 5));
        trough.tank().fill(new FluidStack(BBFluids.blood(), 4000), IFluidHandler.FluidAction.EXECUTE);
        MinionBuild build = cow().with("right_front_leg", ref("spider", "right_front_leg")).with("left_front_leg", ref("spider", "left_front_leg"))
                .with("right_hind_leg", ref("spider", "right_hind_leg")).with("left_hind_leg", ref("spider", "left_hind_leg"));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 5), build, 100.0F, helper.makeMockPlayer(GameType.SURVIVAL));
        float start = minion.power();
        helper.succeedWhen(() -> helper.assertTrue(minion.power() > start + 200.0F && trough.amount() < 4000,
                "it has not climbed over to the trough and drunk yet (at " + minion.position() + ", blood " + minion.power() + ")"));
    }

    /**
     * Horse legs under a cow make it rideable: a saddle goes on, its maker climbs on and steers. A rabbit's torso on
     * horse legs is too light to carry anyone, and a single horse leg is not enough.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void horseLegsAcceptRider(GameTestHelper helper) {
        MinionBuild horseLegs = cow().with("right_front_leg", ref("horse", "right_front_leg")).with("left_front_leg", ref("horse", "left_front_leg"))
                .with("right_hind_leg", ref("horse", "right_hind_leg")).with("left_hind_leg", ref("horse", "left_hind_leg"));
        MinionBuild rabbit = MinionBuild.of(ref("rabbit", "body")).with("right_front_leg", ref("horse", "right_front_leg"))
                .with("left_front_leg", ref("horse", "left_front_leg"));
        MinionBuild oneLeg = cow().with("right_front_leg", ref("horse", "right_front_leg")).with("left_front_leg", ref("cow", "left_front_leg"))
                .with("right_hind_leg", ref("cow", "right_hind_leg")).with("left_hind_leg", ref("cow", "left_hind_leg"));
        if (!MinionStats.of(PartsData.SERVER, horseLegs).rideable() || MinionStats.of(PartsData.SERVER, rabbit).rideable()
                || MinionStats.of(PartsData.SERVER, oneLeg).rideable()) {
            helper.fail("A cow on horse legs should be rideable; a rabbit on horse legs, or a cow with one horse leg, not");
            return;
        }
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity minion = minion(helper, new BlockPos(4, 2, 4), horseLegs, 800.0F, maker);
        if (!minion.isSaddleable() || minion.isSaddled()) {
            helper.fail("It should take a saddle");
            return;
        }
        minion.equipSaddle(new ItemStack(Items.SADDLE), null);
        maker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        minion.interact(maker, InteractionHand.MAIN_HAND);
        if (maker.getVehicle() != minion || minion.getControllingPassenger() != maker) {
            helper.fail("Saddled, its maker should climb on and steer it");
            return;
        }
        // seated on its back where the saddle is drawn, below the top of its raised head
        float saddle = com.avicagan.bloodandbones.minion.MinionBody.saddlePoint(com.avicagan.bloodandbones.minion.MinionBody.layout(PartsData.SERVER, horseLegs)).y;
        double seat = minion.getPassengerRidingPosition(maker).y - minion.getY();
        if (Math.abs(seat - saddle) > 0.01 || !(seat < minion.getBbHeight() - 0.05)) {
            helper.fail("Its rider should sit on the saddle, at " + saddle + ", not at " + seat + " (its hitbox " + minion.getBbHeight() + " tall)");
            return;
        }
        minion.powerDown();
        if (maker.getVehicle() == minion) {
            helper.fail("Out of blood, it throws its rider");
            return;
        }
        helper.succeed();
    }

    /** Arms take turns, each in its own style: a zombie's punch, then an iron golem's fling that throws the target up. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void armsTakeTurnsInTheirStyles(GameTestHelper helper) {
        MinionBuild build = MinionBuild.of(ref("zombie", "body")).with("head", ref("zombie", "head"))
                .with("right_arm", ref("zombie", "right_arm")).with("left_arm", ref("iron_golem", "left_arm"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        if (stats.strikes().size() != 2 || !"punch".equals(stats.strikes().get(0).style()) || !"fling".equals(stats.strikes().get(1).style())
                || !(stats.strikes().get(1).damage() > stats.strikes().get(0).damage())) {
            helper.fail("A zombie arm should punch and an iron golem's fling, harder: " + stats.strikes());
            return;
        }
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), build, 800.0F, helper.makeMockPlayer(GameType.SURVIVAL));
        Zombie first = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 3));
        first.setNoAi(true);
        minion.doHurtTarget(first);
        if (first.getDeltaMovement().y > 0.3) {
            helper.fail("The punch should not throw the zombie up");
            return;
        }
        Zombie second = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        second.setNoAi(true);
        minion.doHurtTarget(second);
        if (!(second.getDeltaMovement().y > 0.5) || !(second.getHealth() < second.getMaxHealth())) {
            helper.fail("The golem arm's fling should hurt the zombie and throw it up: " + second.getDeltaMovement());
            return;
        }
        helper.succeed();
    }

    /** A villager's pair of arms is a pacifist's: it never attacks. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void villagerArmsArePacifist(GameTestHelper helper) {
        MinionBuild build = MinionBuild.of(ref("villager", "body")).with("head", ref("villager", "head")).with("arms", ref("villager", "arms"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        if (stats.fights() || stats.strikes().isEmpty()) {
            helper.fail("Villager arms should be there and never fight: " + stats.strikes());
            return;
        }
        helper.succeed();
    }

    /** A bat's torso flies by itself, legs or none; out of blood it drops. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void flyingTorsoFlies(GameTestHelper helper) {
        MinionBuild build = MinionBuild.of(ref("bat", "body")).with("head", ref("bat", "head"));
        if (!MinionStats.of(PartsData.SERVER, build).flies()) {
            helper.fail("A bat's torso should fly");
            return;
        }
        MinionEntity minion = minion(helper, new BlockPos(4, 3, 4), build, 300.0F, helper.makeMockPlayer(GameType.SURVIVAL));
        helper.runAfterDelay(5, () -> {
            if (!minion.isNoGravity()) {
                helper.fail("A flier should not fall");
                return;
            }
            minion.powerDown();
            if (minion.isNoGravity()) {
                helper.fail("Out of blood, a flier drops");
                return;
            }
            helper.succeed();
        });
    }

    /** A flier running low goes to a trough from the air (it is never on the ground to set off from) and drinks. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void hungryFlierDrinks(GameTestHelper helper) {
        helper.setBlock(new BlockPos(8, 2, 5), BBBlocks.BLOOD_TROUGH.getDefaultState());
        BloodTroughBlockEntity trough = (BloodTroughBlockEntity) helper.getBlockEntity(new BlockPos(8, 2, 5));
        trough.tank().fill(new FluidStack(BBFluids.blood(), 4000), IFluidHandler.FluidAction.EXECUTE);
        MinionBuild build = MinionBuild.of(ref("bat", "body")).with("head", ref("bat", "head"));
        // a fifth of what it holds: hungry
        MinionEntity minion = minion(helper, new BlockPos(2, 4, 5), build, MinionStats.of(PartsData.SERVER, build).reservoir() * 0.2F,
                helper.makeMockPlayer(GameType.SURVIVAL));
        float start = minion.power();
        helper.succeedWhen(() -> helper.assertTrue(minion.isNoGravity() && minion.power() > start + 50.0F && trough.amount() < 4000,
                "the flier has not flown to the trough and drunk yet (at " + minion.position() + ", blood " + minion.power() + ")"));
    }
}
