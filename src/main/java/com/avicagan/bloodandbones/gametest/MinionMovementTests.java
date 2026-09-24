package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The minion's ways of getting about that the last merge left out (docs/PARTS-AND-TRAITS.md sections 5.4, 5.6 and 6.4;
 * docs/ARCHITECTURE-PROPOSAL.md section 15.17): walking over lava as a strider does, wings that lift a torso or only
 * slow its fall, legs that walk the seabed or float, and mounts steered with an item on a stick or seating two. Each
 * test that lets a minion move walls its pen in glass, so it never sees or wanders to another test's things.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinionMovementTests {
    private static PieceRef ref(String entity, String bone) {
        return new PieceRef(ResourceLocation.withDefaultNamespace(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + ".png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    /** A cow's torso and head, these pieces in its four leg sockets (front right, front left, hind right, hind left). */
    private static MinionBuild cowOn(PieceRef frontRight, PieceRef frontLeft, PieceRef hindRight, PieceRef hindLeft) {
        return MinionBuild.of(ref("cow", "body")).with("head", ref("cow", "head")).with("right_front_leg", frontRight).with("left_front_leg", frontLeft)
                .with("right_hind_leg", hindRight).with("left_hind_leg", hindLeft);
    }

    private static MinionEntity minion(GameTestHelper helper, Vec3 pos, MinionBuild build, Player maker) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        Vec3 at = helper.absoluteVec(pos);
        minion.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        minion.setup(maker, BlockPos.containing(at), build, 1000.0F);
        level.addFreshEntity(minion);
        return minion;
    }

    /** A stand-in maker standing here (in the test's own coordinates). */
    private static Player makerAt(GameTestHelper helper, Vec3 pos) {
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(pos);
        maker.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        return maker;
    }

    /** Glass walls round the pen's edge, from y 2 up to this height. */
    private static void pen(GameTestHelper helper, int top) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                if (x == 0 || z == 0 || x == 10 || z == 10) {
                    for (int y = 2; y <= top; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
                    }
                }
            }
        }
    }

    /**
     * A cow on strider legs follows its maker straight over a lava pool as a strider would, rather than finding no way:
     * its path runs over the lava, and it stays on the surface the whole way across, never wading in, never catching
     * alight and never hurt. The pool runs wall to wall between stone banks, so there is no way round it.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void lavaWalkerCrossesLava(GameTestHelper helper) {
        pen(helper, 4);
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                helper.setBlock(new BlockPos(x, 2, z), x >= 3 && x <= 5 ? Blocks.LAVA : Blocks.STONE);
            }
        }
        MinionBuild build = cowOn(ref("strider", "right_leg"), ref("strider", "left_leg"), ref("strider", "right_leg"), ref("strider", "left_leg"));
        Player maker = makerAt(helper, new Vec3(9.5, 3.0, 5.5));
        MinionEntity walker = minion(helper, new Vec3(1.5, 3.0, 5.5), build, maker);
        walker.setJob(MinionStats.COMPANION);
        ActiveTraits.of(walker);
        float health = walker.getHealth();
        double surface = helper.absoluteVec(new Vec3(0.0, 2.5, 0.0)).y;
        String[] wrong = {null};
        boolean[] crossed = {false};
        helper.onEachTick(() -> {
            if (wrong[0] != null) {
                return;
            }
            BlockPos at = walker.blockPosition();
            boolean overLava = helper.getLevel().getFluidState(at).is(FluidTags.LAVA) || helper.getLevel().getFluidState(at.below()).is(FluidTags.LAVA);
            if (overLava && walker.getY() < surface - 0.05) {
                wrong[0] = "it waded into the lava: at " + walker.getY() + ", the surface at " + surface;
            } else if (walker.getHealth() < health || walker.isOnFire()) {
                wrong[0] = "the lava hurt it or set it alight: " + walker.getHealth() + " of " + health + ", on fire " + walker.isOnFire();
            }
            if (overLava) {
                crossed[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            if (wrong[0] != null) {
                walker.discard();
                helper.fail(wrong[0]);
            }
            helper.assertTrue(walker.getPathfindingMalus(PathType.LAVA) == 0.0F, "a lava walker's path should cost lava nothing");
            Vec3 local = helper.relativeVec(walker.position());
            // a companion stops three blocks short of its maker: on the stone past the lava
            helper.assertTrue(crossed[0] && local.x > 6.0 && walker.distanceToSqr(maker) < 3.5 * 3.5,
                    "it has not crossed the lava to its maker yet: at " + local);
            walker.discard();
        });
    }

    /**
     * Phantom wings (0.4 lift each, spec 6.4) lift a cow's torso (about 0.53 of a block): it flies, and hangs in the air
     * where it was woken.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void phantomWingsLiftCow(GameTestHelper helper) {
        MinionBuild build = cowOn(ref("phantom", "body/right_wing_base"), ref("phantom", "body/left_wing_base"), ref("cow", "right_hind_leg"),
                ref("cow", "left_hind_leg"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        if (!stats.flies() || !"fly".equals(stats.mode()) || Math.abs(stats.lift() - 0.8F) > 0.01F || stats.slowFalls()) {
            helper.fail("Two phantom wings should lift a cow and fly it: flies " + stats.flies() + ", " + stats.mode() + ", lift " + stats.lift());
            return;
        }
        MinionEntity minion = minion(helper, new Vec3(5.5, 5.0, 5.5), build, makerAt(helper, new Vec3(6.5, 5.0, 5.5)));
        double start = minion.getY();
        helper.runAfterDelay(40, () -> {
            boolean up = minion.isNoGravity() && minion.getY() > start - 0.5;
            double y = helper.relativeVec(minion.position()).y;
            minion.discard();
            if (!up) {
                helper.fail("On phantom wings it should stay up in the air, not at " + y);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Chicken wings (0.02 each) never lift a cow: it cannot fly, but drops slowly, as a chicken does, and lands unhurt; a
     * wingless cow dropped from the same height falls fast.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void chickenWingsDoNot(GameTestHelper helper) {
        MinionBuild winged = cowOn(ref("chicken", "right_wing"), ref("chicken", "left_wing"), ref("cow", "right_hind_leg"), ref("cow", "left_hind_leg"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, winged);
        if (stats.flies() || !stats.slowFalls() || !(stats.lift() > 0.0F)) {
            helper.fail("Chicken wings should never fly a cow, only slow its fall: flies " + stats.flies() + ", lift " + stats.lift());
            return;
        }
        MinionBuild wingless = cowOn(ref("cow", "right_front_leg"), ref("cow", "left_front_leg"), ref("cow", "right_hind_leg"), ref("cow", "left_hind_leg"));
        MinionEntity drifter = minion(helper, new Vec3(3.5, 6.0, 5.5), winged, makerAt(helper, new Vec3(3.5, 2.0, 7.5)));
        MinionEntity stone = minion(helper, new Vec3(7.5, 6.0, 5.5), wingless, makerAt(helper, new Vec3(7.5, 2.0, 7.5)));
        float health = drifter.getHealth();
        double[] fastest = {0.0};
        helper.onEachTick(() -> fastest[0] = Math.min(fastest[0], drifter.getDeltaMovement().y));
        helper.runAfterDelay(8, () -> {
            double drifted = helper.relativeVec(drifter.position()).y;
            double fell = helper.relativeVec(stone.position()).y;
            if (!(drifted > fell + 0.5)) {
                drifter.discard();
                stone.discard();
                helper.fail("The chicken-winged cow should fall slower than a wingless one: at " + drifted + " against " + fell);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(drifter.onGround(), "the chicken-winged cow has not landed yet");
            boolean slow = fastest[0] > -0.3;
            float after = drifter.getHealth();
            drifter.discard();
            stone.discard();
            helper.assertTrue(slow && after >= health, "it should have fallen slowly and landed unhurt: fastest " + fastest[0] + ", health " + after);
        });
    }

    /**
     * Drowned legs sink (spec 5.6): a cow on them walks along the bottom of a channel of water to its maker at the far end at
     * a land walker's pace, never paddling up to the surface.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sinkWalksSeabed(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 3; z <= 7; z++) {
                for (int y = 2; y <= 5; y++) {
                    boolean wall = x == 0 || x == 10 || z == 3 || z == 7;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.GLASS : y <= 4 ? Blocks.WATER : Blocks.AIR);
                }
            }
        }
        MinionBuild build = cowOn(ref("drowned", "right_leg"), ref("drowned", "left_leg"), ref("drowned", "right_leg"), ref("drowned", "left_leg"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        if (!stats.sinks()) {
            helper.fail("Drowned legs should sink: " + stats.mode());
            return;
        }
        Player maker = makerAt(helper, new Vec3(9.5, 2.0, 5.5));
        MinionEntity sinker = minion(helper, new Vec3(1.5, 2.0, 5.5), build, maker);
        sinker.setJob(MinionStats.COMPANION);
        double bottom = helper.absoluteVec(new Vec3(0.0, 2.0, 0.0)).y;
        double[] highest = {0.0};
        long started = helper.getTick();
        helper.onEachTick(() -> highest[0] = Math.max(highest[0], sinker.getY() - bottom));
        helper.succeedWhen(() -> {
            helper.assertTrue(sinker.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY) >= 1.0, "a sinker walks water as it walks land");
            if (highest[0] > 0.6) {
                sinker.discard();
                helper.fail("A sinker should keep to the bottom, but it rose " + highest[0] + " off it");
            }
            // a companion stops three blocks short of its maker
            helper.assertTrue(sinker.distanceToSqr(maker) < 3.5 * 3.5, "it has not walked the seabed to its maker yet: at " + helper.relativeVec(sinker.position()));
            long took = helper.getTick() - started;
            sinker.discard();
            // eight blocks at a land walker's pace; water's own drag would take it far longer
            helper.assertTrue(took <= 120, "it took " + took + " ticks, too slow for walking at full speed");
        });
    }

    /** Ghast tentacles float (spec 8.2): a cow on them stays up in the air, as a ghast does, and bobs there. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void floatStaysUp(GameTestHelper helper) {
        MinionBuild build = cowOn(ref("ghast", "tentacle0"), ref("ghast", "tentacle1"), ref("ghast", "tentacle2"), ref("ghast", "tentacle3"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        if (!stats.flies() || !"float".equals(stats.mode())) {
            helper.fail("Ghast tentacles should float it: " + stats.mode());
            return;
        }
        MinionEntity floater = minion(helper, new Vec3(5.5, 5.0, 5.5), build, makerAt(helper, new Vec3(7.5, 5.0, 5.5)));
        double start = floater.getY();
        helper.runAfterDelay(60, () -> {
            boolean up = floater.isNoGravity() && floater.getY() > start - 0.5;
            double y = helper.relativeVec(floater.position()).y;
            floater.discard();
            if (!up) {
                helper.fail("A floater should stay up, not sink to " + y);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A rider using the item in their main hand in the air, as the server hears it: straight to the minion's handler (the
     * whole event would reach other mods' handlers, which take a stand-in player that is not a server player for a client).
     */
    private static InteractionResult useStick(Player rider) {
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem(rider, InteractionHand.MAIN_HAND);
        com.avicagan.bloodandbones.minion.MinionMoves.onUseStick(event);
        return event.isCanceled() ? event.getCancellationResult() : InteractionResult.PASS;
    }

    /** Saddle it, and put its maker on it. */
    private static void mount(MinionEntity minion, Player maker) {
        minion.equipSaddle(new ItemStack(Items.SADDLE), null);
        maker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        minion.interact(maker, InteractionHand.MAIN_HAND);
    }

    /**
     * A pig's head steers with a carrot on a stick (spec 6.4): on horse legs under a cow, its rider steers it only while
     * holding one (a cow's head needs only the saddle), and using it spurs it on for a while, wearing the stick 7, as a
     * pig's does.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void pigHeadSteersWithCarrot(GameTestHelper helper) {
        MinionBuild build = MinionBuild.of(ref("cow", "body")).with("head", ref("pig", "head")).with("right_front_leg", ref("horse", "right_front_leg"))
                .with("left_front_leg", ref("horse", "left_front_leg")).with("right_hind_leg", ref("horse", "right_hind_leg"))
                .with("left_hind_leg", ref("horse", "left_hind_leg"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        if (!stats.rideable() || !stats.mount().steer().equals(java.util.Optional.of(ResourceLocation.withDefaultNamespace("carrot_on_a_stick")))
                || stats.mount().wear() != 7) {
            helper.fail("A pig's head on horse legs should be steered with a carrot on a stick: " + stats.mount());
            return;
        }
        Player maker = makerAt(helper, new Vec3(6.5, 2.0, 5.5));
        MinionEntity minion = minion(helper, new Vec3(5.5, 2.0, 5.5), build, maker);
        mount(minion, maker);
        if (maker.getVehicle() != minion || minion.getControllingPassenger() != null) {
            helper.fail("Its maker should be up, but not steering it without a carrot on a stick");
            minion.discard();
            return;
        }
        maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT_ON_A_STICK));
        if (minion.getControllingPassenger() != maker) {
            helper.fail("With a carrot on a stick its rider should steer it");
            minion.discard();
            return;
        }
        float before = minion.riddenSpeed();
        InteractionResult result = useStick(maker);
        ItemStack stick = maker.getMainHandItem();
        if (!result.consumesAction() || !stick.is(Items.CARROT_ON_A_STICK) || stick.getDamageValue() != 7) {
            helper.fail("Using the stick should spur it on and wear the stick 7: " + result + ", " + stick + " worn " + stick.getDamageValue());
            minion.discard();
            return;
        }
        MinionBuild cowHead = build.with("head", ref("cow", "head"));
        if (MinionStats.of(PartsData.SERVER, cowHead).mount().steer().isPresent()) {
            helper.fail("A cow's head on horse legs needs only the saddle");
            minion.discard();
            return;
        }
        helper.runAfterDelay(20, () -> {
            float spurred = minion.riddenSpeed();
            minion.discard();
            // the spur swells from nothing over its random length (7 to 49 seconds), as a pig's does
            if (!(spurred > before * 1.02F)) {
                helper.fail("Spurred on, it should go faster for a while: " + spurred + " against " + before);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Strider legs steer with a warped fungus on a stick (spec 6.4): a strider's torso on its own legs (no head) takes a
     * saddle, walks on lava as a strider does, and is steered only with the fungus, whose stick wears 1 a use.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void striderLegsSteerWithFungus(GameTestHelper helper) {
        MinionBuild build = MinionBuild.of(ref("strider", "body")).with("right_leg", ref("strider", "right_leg")).with("left_leg", ref("strider", "left_leg"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        if (!stats.rideable() || !stats.mount().steer().equals(java.util.Optional.of(ResourceLocation.withDefaultNamespace("warped_fungus_on_a_stick")))
                || stats.mount().wear() != 1) {
            helper.fail("Strider legs should be steered with a warped fungus on a stick: rideable " + stats.rideable() + ", " + stats.mount());
            return;
        }
        Player maker = makerAt(helper, new Vec3(7.0, 2.0, 5.5));
        MinionEntity minion = minion(helper, new Vec3(5.5, 2.0, 5.5), build, maker);
        mount(minion, maker);
        maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CARROT_ON_A_STICK));
        boolean carrot = minion.getControllingPassenger() == maker;
        maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK));
        boolean fungus = minion.getControllingPassenger() == maker;
        useStick(maker);
        int worn = maker.getMainHandItem().getDamageValue();
        helper.runAfterDelay(25, () -> {
            // its traits give it the strider's lava walking, and so a strider's path costs
            boolean lava = minion.getPathfindingMalus(PathType.LAVA) == 0.0F;
            minion.discard();
            if (carrot || !fungus || worn != 1 || !lava) {
                helper.fail("Only a warped fungus on a stick should steer strider legs, wearing 1 a use, and it should path over lava: carrot "
                        + carrot + ", fungus " + fungus + ", worn " + worn + ", lava " + lava);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A camel carries two (spec 8.2): its hump holds twice the blood, its maker climbs into the front seat, anyone else into
     * the one behind, and a third finds no room. The rider in front steers it.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void camelCarriesTwo(GameTestHelper helper) {
        MinionBuild build = MinionBuild.of(ref("camel", "body")).with("body/head", ref("camel", "body/head")).with("right_front_leg", ref("camel", "right_front_leg"))
                .with("left_front_leg", ref("camel", "left_front_leg")).with("right_hind_leg", ref("camel", "right_hind_leg"))
                .with("left_hind_leg", ref("camel", "left_hind_leg"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        // 250 mB and 1000 a block of its torso (about 1.19), twice over for the hump
        if (!stats.rideable() || stats.mount().seats() != 2 || stats.reservoir() < 2800) {
            helper.fail("A camel should take a saddle and seat two, holding twice the blood: " + stats.rideable() + ", " + stats.mount() + ", "
                    + stats.reservoir() + " mB");
            return;
        }
        Player maker = makerAt(helper, new Vec3(7.0, 2.0, 5.5));
        MinionEntity camel = minion(helper, new Vec3(4.5, 2.0, 5.5), build, maker);
        mount(camel, maker);
        Player friend = makerAt(helper, new Vec3(7.0, 2.0, 6.5));
        camel.interact(friend, InteractionHand.MAIN_HAND);
        Player third = makerAt(helper, new Vec3(7.0, 2.0, 4.5));
        camel.interact(third, InteractionHand.MAIN_HAND);
        boolean seated = maker.getVehicle() == camel && friend.getVehicle() == camel && third.getVehicle() == null && camel.getPassengers().size() == 2;
        boolean steers = camel.getControllingPassenger() == maker;
        // it faces +z: the front seat is further along it
        Vec3 front = camel.getPassengerRidingPosition(maker);
        Vec3 back = camel.getPassengerRidingPosition(friend);
        camel.ejectPassengers();
        camel.discard();
        if (!seated || !steers || !(front.z > back.z + 0.4)) {
            helper.fail("Its maker should ride in front, steering, and one more behind, with no room for a third: seated " + seated + ", steers "
                    + steers + ", front " + front + ", back " + back);
            return;
        }
        helper.succeed();
    }
}
