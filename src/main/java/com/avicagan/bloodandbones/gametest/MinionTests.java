package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.minion.BloodTroughBlockEntity;
import com.avicagan.bloodandbones.minion.DormantMinionItem;
import com.avicagan.bloodandbones.minion.MinionAssembly;
import com.avicagan.bloodandbones.minion.MinionBody;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Minions built of carcass pieces (docs/PARTS-AND-TRAITS.md section 6): on the table, what their parts add up to,
 * the shape they stand in, running on blood and never destroyed by running out.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinionTests {
    private static ResourceLocation mob(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    private static PieceRef ref(String entity, String bone) {
        return new PieceRef(mob(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + (entity.equals("rabbit") ? "brown" : entity) + ".png"), List.of(), 1.0F,
                false, Map.of(), false);
    }

    private static ItemStack piece(String entity, String bone) {
        ItemStack stack = new ItemStack(BBItems.CARCASS_PIECE.get());
        stack.set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(mob(entity), bone,
                ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + entity + ".png"), List.of(), 1.0F, false, Map.of(), 0.0F, 0.0F, 0.0F, false));
        return stack;
    }

    /** A cow's torso and head on four rabbit legs: two front legs and two haunches. */
    private static MinionBuild cowOnRabbitLegs() {
        return MinionBuild.of(ref("cow", "body")).with("head", ref("cow", "head"))
                .with("right_front_leg", ref("rabbit", "right_front_leg")).with("left_front_leg", ref("rabbit", "left_front_leg"))
                .with("right_hind_leg", ref("rabbit", "right_haunch")).with("left_hind_leg", ref("rabbit", "left_haunch"));
    }

    /** A cow as it was: its own head and legs. */
    private static MinionBuild wholeCow() {
        MinionBuild build = MinionBuild.of(ref("cow", "body")).with("head", ref("cow", "head"));
        for (String leg : new String[]{"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"}) {
            build = build.with(leg, ref("cow", leg));
        }
        return build;
    }

    private static SurgeryTableBlockEntity assemblyTable(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.ASSEMBLY));
        return (SurgeryTableBlockEntity) helper.getBlockEntity(pos);
    }

    /** A minion of this build standing here, with this much blood, its maker a mock player. */
    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build, float blood) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(helper.makeMockPlayer(GameType.SURVIVAL), at, build, blood);
        level.addFreshEntity(minion);
        return minion;
    }

    /**
     * A surgeon minion standing here, still (no AI, so a test knows where it is): a zombie's torso and arm, a
     * villager's head. The amputation ritual needs one at the table.
     */
    public static MinionEntity surgeon(GameTestHelper helper, BlockPos pos) {
        MinionBuild build = MinionBuild.of(ref("zombie", "body")).with("head", ref("villager", "head")).with("right_arm", ref("zombie", "right_arm"))
                .with("left_leg", ref("zombie", "left_leg")).with("right_leg", ref("zombie", "right_leg"));
        MinionEntity minion = minion(helper, pos, build, 1000.0F);
        minion.setNoAi(true);
        minion.setJob(BloodAndBones.asResource("surgeon"));
        return minion;
    }

    /** A trough here with this much blood in it. */
    private static BloodTroughBlockEntity trough(GameTestHelper helper, BlockPos pos, int blood) {
        helper.setBlock(pos, BBBlocks.BLOOD_TROUGH.getDefaultState());
        BloodTroughBlockEntity trough = (BloodTroughBlockEntity) helper.getBlockEntity(pos);
        trough.tank().fill(new FluidStack(BBFluids.blood(), blood), IFluidHandler.FluidAction.EXECUTE);
        return trough;
    }

    /** A whole cow carcass lying on the table is claimed with an empty hand: its torso the frame, its head and legs already on, the carcass gone. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cowFrameClaimedFromTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(5, 2, 5);
        SurgeryTableBlockEntity table = assemblyTable(helper, at);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 3, 5));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        cow.discard();
        helper.runAfterDelay(30, () -> {
            if (!MinionAssembly.claim(level, table)) {
                helper.fail("The cow lying on the table should be claimed");
                return;
            }
            MinionBuild build = table.build().orElseThrow();
            if (!build.torso().entity().equals(mob("cow")) || build.parts().size() != 5 || build.in("head").isEmpty()) {
                helper.fail("The frame should be the cow's torso with its head and four legs fitted: " + build);
                return;
            }
            // other tests run in the same world with carcasses of their own: this one must be gone
            if (CarcassSavedData.get(level).all().stream().anyMatch(c -> c.id.equals(carcass.id))) {
                helper.fail("The claimed carcass should be gone from the world");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The design's own example: a cow torso and head on four rabbit legs, built on the table a click at a time,
     * is 15 health, hops at 0.325 and carries things. Woken with a bucket of blood, it gets up so.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void buildCowOnFourRabbitLegs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(3, 2, 3);
        SurgeryTableBlockEntity table = assemblyTable(helper, at);
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!MinionAssembly.layDown(table, piece("cow", "body"), level)) {
            helper.fail("A cow torso should lie down as a frame");
            return;
        }
        if (MinionAssembly.layDown(table, piece("cow", "head"), level)) {
            helper.fail("Only one frame at a time");
            return;
        }
        for (ItemStack stack : List.of(piece("cow", "head"), piece("rabbit", "right_front_leg"), piece("rabbit", "left_front_leg"),
                piece("rabbit", "right_haunch"), piece("rabbit", "left_haunch"))) {
            var problem = MinionAssembly.fit(level, table, stack);
            if (problem != null) {
                helper.fail("Should fit " + CarcassPieceItem.piece(stack).bone() + ": " + problem.getString());
                return;
            }
        }
        if (MinionAssembly.fit(level, table, piece("rabbit", "right_haunch")) == null) {
            helper.fail("A fifth leg should find no socket on a cow");
            return;
        }
        MinionStats stats = MinionStats.of(PartsData.SERVER, table.build().orElseThrow());
        if (Math.abs(stats.health() - 15.0F) > 0.01F || Math.abs(stats.speed() - 0.325F) > 0.001F || !"hop".equals(stats.mode())
                || !stats.jobs().get(0).equals(BloodAndBones.asResource("courier")) || stats.mindless()) {
            helper.fail("A cow on rabbit legs should be 15 health, hop at 0.325 and start as a courier: " + stats);
            return;
        }
        MinionEntity minion = MinionAssembly.wake(level, maker, table, new ItemStack(BBFluids.BLOOD.getBucket().get()));
        if (minion == null || table.build().isPresent()) {
            helper.fail("A bucket of blood should wake it and clear the table");
            return;
        }
        // a bucket is more than it holds: it is full (the design's "about 780 mB" for a cow's torso)
        if (Math.abs(minion.getMaxHealth() - 15.0F) > 0.01F || Math.abs(minion.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - 0.325) > 0.001
                || minion.power() != stats.reservoir() || Math.abs(stats.reservoir() - 780) > 10 || !minion.isMaker(maker)) {
            helper.fail("The woken minion should carry its build's stats and the bucket's blood: " + minion.getMaxHealth() + " "
                    + minion.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) + " " + minion.power());
            return;
        }
        helper.succeed();
    }

    /** A cow on rabbit legs is faster than a cow, and hops where the cow walks. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cowOnRabbitLegsOutpacesCow(GameTestHelper helper) {
        MinionStats hopper = MinionStats.of(PartsData.SERVER, cowOnRabbitLegs());
        MinionStats cow = MinionStats.of(PartsData.SERVER, wholeCow());
        if (!(hopper.speed() > cow.speed()) || !"walk".equals(cow.mode()) || !"hop".equals(hopper.mode()) || Math.abs(cow.speed() - 0.2F) > 0.001F) {
            helper.fail("Rabbit legs should outpace a cow's own (" + hopper.speed() + " vs " + cow.speed() + ", " + hopper.mode() + " vs " + cow.mode() + ")");
            return;
        }
        // two legs out of four go half as fast; none at all, it crawls
        MinionStats twoLegs = MinionStats.of(PartsData.SERVER, MinionBuild.of(ref("cow", "body")).with("head", ref("cow", "head"))
                .with("right_hind_leg", ref("cow", "right_hind_leg")).with("left_hind_leg", ref("cow", "left_hind_leg")));
        MinionStats none = MinionStats.of(PartsData.SERVER, MinionBuild.of(ref("cow", "body")));
        if (Math.abs(twoLegs.speed() - 0.1F) > 0.001F || !"crawl".equals(none.mode()) || !none.mindless()) {
            helper.fail("Two legs of four should halve its speed, and a legless headless torso crawl mindless: " + twoLegs.speed() + " " + none.mode());
            return;
        }
        helper.succeed();
    }

    /**
     * However it is built, it stands on its legs: a cow's four legs all reach the ground, and the cow on rabbit legs is
     * held up off it by them, its torso clear. Every piece is drawn.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void feetNeverBelowGround(GameTestHelper helper) {
        List<MinionBuild> builds = List.of(wholeCow(), cowOnRabbitLegs(), MinionBuild.of(ref("cow", "body")),
                MinionBuild.of(ref("rabbit", "body")).with("right_haunch", ref("cow", "right_hind_leg")).with("left_haunch", ref("cow", "left_hind_leg")),
                MinionBuild.of(ref("zombie", "body")).with("head", ref("pig", "head")).with("right_leg", ref("rabbit", "right_haunch")));
        for (MinionBuild build : builds) {
            MinionBody.Layout layout = MinionBody.layout(PartsData.SERVER, build);
            if (layout.pieces().size() != build.parts().size() + 1) {
                helper.fail("Every piece drawn: " + build.torso().entity() + " with " + build.parts().size() + " parts, " + layout.pieces().size() + " drawn");
                return;
            }
        }
        // worked out from each piece's own placed box, not from the lift that was made to fit them
        MinionBody.Layout cow = MinionBody.layout(PartsData.SERVER, wholeCow());
        int legs = 0;
        for (MinionBody.Placement placement : cow.pieces()) {
            if (placement.slot().slot() == com.avicagan.bloodandbones.parts.PartSlot.LEG) {
                legs++;
                if (Math.abs(lowest(placement) + cow.lift() - MinionBody.GROUND) > 0.01F) {
                    helper.fail("Each of a cow's legs should reach the ground: " + placement.piece().bone() + " ends at " + (lowest(placement) + cow.lift()));
                    return;
                }
            }
        }
        MinionBody.Layout hopper = MinionBody.layout(PartsData.SERVER, cowOnRabbitLegs());
        MinionBody.Placement torso = hopper.pieces().stream().filter(p -> p.socket() == null).findFirst().orElseThrow();
        if (legs != 4 || !(lowest(torso) + hopper.lift() < MinionBody.GROUND - 0.5F)) {
            helper.fail("Rabbit legs should hold the cow's torso up off the ground: " + legs + " legs, torso's underside at " + (lowest(torso) + hopper.lift()));
            return;
        }
        // rabbit legs are shorter than a cow's: the rabbit-legged cow stands lower
        MinionBody.Layout tall = MinionBody.layout(PartsData.SERVER, wholeCow());
        MinionBody.Layout low = MinionBody.layout(PartsData.SERVER, cowOnRabbitLegs());
        if (!(low.height() < tall.height())) {
            helper.fail("A cow on rabbit legs should stand lower than a cow: " + low.height() + " vs " + tall.height());
            return;
        }
        helper.succeed();
    }

    /** The lowest point of a placed piece, in model pixels (y down), before the lift. */
    private static float lowest(MinionBody.Placement placement) {
        float max = -Float.MAX_VALUE;
        for (org.joml.Vector3f c : MinionBody.corners(placement.pose(), placement.bone())) {
            max = Math.max(max, c.y);
        }
        return max;
    }

    /** Its hitbox holds all of it: as wide as its widest side and as tall as it stands. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hitboxContainsParts(GameTestHelper helper) {
        for (MinionBuild build : List.of(wholeCow(), cowOnRabbitLegs())) {
            MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), build, 500.0F);
            MinionBody.Layout layout = MinionBody.layout(PartsData.SERVER, build);
            float width = minion.getBbWidth();
            float height = minion.getBbHeight();
            if (width + 0.001F < (layout.max().x - layout.min().x) / 16.0F || width + 0.001F < (layout.max().z - layout.min().z) / 16.0F
                    || height + 0.001F < (layout.max().y - layout.min().y) / 16.0F) {
                helper.fail("The hitbox " + width + " by " + height + " should hold the body " + (layout.max().x - layout.min().x) / 16.0F + " by "
                        + (layout.max().z - layout.min().z) / 16.0F + " by " + (layout.max().y - layout.min().y) / 16.0F);
                return;
            }
            minion.discard();
        }
        helper.succeed();
    }

    /** Run dry, it powers down alive where it is, and lies on its side: its hitbox lower than it was. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void drainToZeroPowersDownAlive(GameTestHelper helper) {
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), cowOnRabbitLegs(), 0.3F);
        float standing = minion.getBbHeight();
        helper.succeedWhen(() -> {
            helper.assertTrue(minion.poweredDown(), "it has not run dry yet");
            helper.assertTrue(minion.isAlive() && !minion.isRemoved() && minion.power() == 0.0F, "a minion out of blood should be alive with none");
            helper.assertTrue(minion.getBbHeight() < standing, "lying down it should be lower than standing");
        });
    }

    /** A zombie cannot hurt a powered-down minion, nor even see it; a player still can. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void zombieCannotKillPoweredDown(GameTestHelper helper) {
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), wholeCow(), 100.0F);
        minion.powerDown();
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
        float health = minion.getHealth();
        minion.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1000.0F);
        if (minion.getHealth() != health || !minion.isAlive() || minion.canBeSeenAsEnemy()) {
            helper.fail("Powered down, a zombie should not hurt it nor see it");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        minion.invulnerableTime = 0;
        minion.hurt(helper.getLevel().damageSources().playerAttack(player), 2.0F);
        if (!(minion.getHealth() < health)) {
            helper.fail("A player can still hurt it");
            return;
        }
        // a lethal blow collapses it, never destroys it (the default)
        minion.invulnerableTime = 0;
        minion.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        if (!minion.isAlive() || minion.isRemoved() || !minion.poweredDown()) {
            helper.fail("A lethal blow should collapse it, not destroy it");
            return;
        }
        helper.succeed();
    }

    /** Low on blood, it walks round a wall to a trough across the pen and drinks its fill. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void walksToTroughAndRefills(GameTestHelper helper) {
        // a wall down the middle, open at the far end: the way round is a dozen blocks and more
        for (int z = 0; z < 8; z++) {
            helper.setBlock(new BlockPos(5, 2, z), Blocks.STONE);
            helper.setBlock(new BlockPos(5, 3, z), Blocks.STONE);
        }
        BloodTroughBlockEntity trough = trough(helper, new BlockPos(9, 2, 1), 4000);
        MinionEntity minion = minion(helper, new BlockPos(1, 2, 1), cowOnRabbitLegs(), 100.0F);
        float start = minion.power();
        helper.succeedWhen(() -> {
            helper.assertTrue(minion.power() > start + 200.0F, "it has not drunk from the trough yet (" + minion.power() + ")");
            helper.assertTrue(trough.amount() < 4000, "the trough should be lower");
        });
    }

    /**
     * A trough walled in where it cannot get to is no use: hungry, with blood enough to walk there and back, it never
     * drinks from it, not even from right up against the glass.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void cannotReachTroughStaysDown(GameTestHelper helper) {
        BlockPos at = new BlockPos(8, 2, 8);
        trough(helper, at, 4000);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int up = 0; up < 3; up++) {
                helper.setBlock(at.relative(side).above(up), Blocks.GLASS);
                helper.setBlock(at.relative(side).relative(side.getClockWise()).above(up), Blocks.GLASS);
            }
        }
        helper.setBlock(at.above(), Blocks.GLASS);
        MinionEntity minion = minion(helper, new BlockPos(1, 2, 1), cowOnRabbitLegs(), 150.0F);
        float start = minion.power();
        helper.runAfterDelay(250, () -> {
            BloodTroughBlockEntity trough = (BloodTroughBlockEntity) helper.getBlockEntity(at);
            if (trough.amount() != 4000 || minion.power() > start) {
                helper.fail("It should never drink from the walled-in trough (" + minion.power() + " of " + start + ", trough " + trough.amount() + ")");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A trough just the other side of a wall, within reach of its head, is no use either: an awake minion does not
     * drink through the wall, and one powered down beside it is not woken through it.
     */
    @GameTest(template = "empty", timeoutTicks = 240)
    public static void troughBehindAWallIsNoUse(GameTestHelper helper) {
        for (int z = 0; z <= 10; z++) {
            for (int y = 2; y <= 6; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.STONE);
            }
        }
        trough(helper, new BlockPos(7, 2, 2), 4000);
        trough(helper, new BlockPos(7, 2, 8), 4000);
        MinionEntity awake = minion(helper, new BlockPos(3, 2, 2), cowOnRabbitLegs(), 150.0F);
        MinionEntity down = minion(helper, new BlockPos(3, 2, 8), cowOnRabbitLegs(), 150.0F);
        down.powerDown();
        float start = awake.power();
        helper.runAfterDelay(200, () -> {
            BloodTroughBlockEntity near = (BloodTroughBlockEntity) helper.getBlockEntity(new BlockPos(7, 2, 2));
            BloodTroughBlockEntity far = (BloodTroughBlockEntity) helper.getBlockEntity(new BlockPos(7, 2, 8));
            if (near.amount() != 4000 || awake.power() > start) {
                helper.fail("It should not drink through the wall (" + awake.power() + " of " + start + ", trough " + near.amount() + ")");
                return;
            }
            if (far.amount() != 4000 || !down.poweredDown()) {
                helper.fail("A trough behind a wall should not wake one lying by it (" + far.amount() + ")");
                return;
            }
            helper.succeed();
        });
    }

    /** A minion saved and loaded again, as a chunk unloading and loading does. */
    private static MinionEntity reload(GameTestHelper helper, MinionEntity minion) {
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        minion.saveWithoutId(tag);
        MinionEntity loaded = BBEntities.MINION.get().create(helper.getLevel());
        loaded.load(tag);
        return loaded;
    }

    /** Saved and loaded, it keeps its build, job, blood, being awake or down, maker, home, what it carries, its saddle and its module. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void minionSavedAndLoaded(GameTestHelper helper) {
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), cowOnRabbitLegs(), 321.0F);
        minion.inventory.addItem(new ItemStack(Items.WHEAT, 5));
        MinionEntity loaded = reload(helper, minion);
        if (!loaded.build().equals(minion.build()) || !loaded.home().equals(minion.home()) || loaded.inventory.countItem(Items.WHEAT) != 5
                || !loaded.job().equals(minion.job()) || loaded.poweredDown() || loaded.power() != 321.0F || loaded.makerId() == null
                || !loaded.makerId().equals(minion.makerId()) || Math.abs(loaded.getMaxHealth() - 15.0F) > 0.01F) {
            helper.fail("A saved minion should come back the same, awake with its 321 mB: " + loaded.power() + " " + loaded.poweredDown());
            return;
        }
        minion.powerDown();
        if (!reload(helper, minion).poweredDown()) {
            helper.fail("One saved powered down should come back down");
            return;
        }
        MinionEntity saddled = minion(helper, new BlockPos(6, 2, 2), horseLegs(), 500.0F);
        saddled.equipSaddle(new ItemStack(Items.SADDLE), null);
        MinionEntity brass = minion(helper, new BlockPos(2, 2, 6), new MinionBuild(true, skinned("cow", "body"), List.of(), true).with("head", skinned("cow", "head")), 800.0F);
        brass.setModule(com.avicagan.bloodandbones.cyber.Module.MAGNET_COIL);
        if (!reload(helper, saddled).isSaddled() || reload(helper, brass).module() != com.avicagan.bloodandbones.cyber.Module.MAGNET_COIL) {
            helper.fail("Its saddle and its module should come back with it");
            return;
        }
        helper.succeed();
    }

    /** A cow on horse legs: rideable. */
    private static MinionBuild horseLegs() {
        return MinionBuild.of(ref("cow", "body")).with("head", ref("cow", "head")).with("right_front_leg", ref("horse", "right_front_leg"))
                .with("left_front_leg", ref("horse", "left_front_leg")).with("right_hind_leg", ref("horse", "right_hind_leg"))
                .with("left_hind_leg", ref("horse", "left_hind_leg"));
    }

    private static PieceRef skinned(String entity, String bone) {
        return new PieceRef(mob(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + entity + ".png"), List.of(), 1.0F,
                true, Map.of(), false);
    }

    /** With a cap of one set, a second minion will not wake; the table keeps its build. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void minionCapRespected(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var config = com.avicagan.bloodandbones.config.BBServerConfig.MAX_MINIONS;
        int before = com.avicagan.bloodandbones.config.BBServerConfig.maxMinions();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            config.set(1);
            SurgeryTableBlockEntity first = assemblyTable(helper, new BlockPos(2, 2, 2));
            first.setBuild(wholeCow());
            SurgeryTableBlockEntity second = assemblyTable(helper, new BlockPos(6, 2, 6));
            second.setBuild(wholeCow());
            if (MinionAssembly.wake(level, maker, first, new ItemStack(BBFluids.BLOOD.getBucket().get())) == null) {
                helper.fail("The first minion should wake");
                return;
            }
            if (MinionAssembly.wake(level, maker, second, new ItemStack(BBFluids.BLOOD.getBucket().get())) != null || second.build().isEmpty()) {
                helper.fail("The second should not wake over the cap, and stay on its table");
                return;
            }
        } finally {
            config.set(before);
        }
        helper.succeed();
    }

    /** Its maker folds it up once it is down; set down again it keeps everything, still down until given blood. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void foldAndUnfold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), cowOnRabbitLegs(), 50.0F);
        minion.inventory.addItem(new ItemStack(Items.BONE, 2));
        minion.powerDown();
        Player maker = level.getPlayerByUUID(minion.makerId()) instanceof Player p ? p : helper.makeMockPlayer(GameType.SURVIVAL);
        DormantMinionItem.fold(minion, maker);
        ItemStack folded = maker.getInventory().items.stream().filter(s -> s.is(BBItems.DORMANT_MINION.get())).findFirst().orElse(ItemStack.EMPTY);
        if (!minion.isRemoved() || folded.isEmpty()) {
            helper.fail("Folding should put it in its maker's hands");
            return;
        }
        BlockPos floor = helper.absolutePos(new BlockPos(6, 1, 6));
        folded.getItem().useOn(new UseOnContext(level, maker, net.minecraft.world.InteractionHand.MAIN_HAND, folded,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        List<MinionEntity> back = level.getEntitiesOfClass(MinionEntity.class, new AABB(floor).inflate(2));
        if (back.size() != 1 || !back.get(0).poweredDown() || !back.get(0).build().equals(minion.build()) || back.get(0).inventory.countItem(Items.BONE) != 2) {
            helper.fail("Set down it should come back as it was, still down: " + back.size());
            return;
        }
        if (!back.get(0).home().equals(floor.above())) {
            helper.fail("Set down somewhere new, it should work from there: home " + back.get(0).home() + ", set down at " + floor.above());
            return;
        }
        back.get(0).feed(100.0F);
        if (back.get(0).poweredDown()) {
            helper.fail("Blood should wake it");
            return;
        }
        helper.succeed();
    }

    /** A Cleaver takes the pieces back off one at a time, last first, then the torso. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cleaverTakesBackLastPiece(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = assemblyTable(helper, new BlockPos(3, 2, 3));
        table.setBuild(cowOnRabbitLegs());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionAssembly.takeBack(level, table, player);
        MinionBuild build = table.build().orElseThrow();
        ItemStack back = player.getInventory().items.stream().filter(s -> s.is(BBItems.CARCASS_PIECE.get())).findFirst().orElse(ItemStack.EMPTY);
        if (build.parts().size() != 4 || back.isEmpty() || !CarcassPieceItem.piece(back).bone().equals("left_haunch")) {
            helper.fail("The Cleaver should take back the last leg fitted");
            return;
        }
        for (int i = 0; i < 5; i++) {
            MinionAssembly.takeBack(level, table, player);
        }
        if (table.build().isPresent()) {
            helper.fail("Then the torso");
            return;
        }
        helper.succeed();
    }

    /** A courier (a cow's head) carries something dropped near home into the chest there. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void courierCarries(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), wholeCow(), 1000.0F);
        BlockPos drop = helper.absolutePos(new BlockPos(8, 2, 8));
        helper.getLevel().addFreshEntity(new ItemEntity(helper.getLevel(), drop.getX() + 0.5, drop.getY() + 0.2, drop.getZ() + 0.5, new ItemStack(Items.BONE, 3)));
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
            helper.assertTrue(minion.hasJob("courier") && chest.countItem(Items.BONE) == 3, "the courier has not put the bones in the chest yet");
        });
    }

    /** A villager's head makes a farmer: it reaps ripe wheat by home, plants it again, and puts the wheat in the chest. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void farmerReaps(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        BlockPos field = new BlockPos(6, 1, 6);
        helper.setBlock(field, Blocks.FARMLAND);
        helper.setBlock(field.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        MinionBuild build = MinionBuild.of(ref("zombie", "body")).with("head", ref("villager", "head"))
                .with("right_leg", ref("zombie", "right_leg")).with("left_leg", ref("zombie", "left_leg"))
                .with("right_arm", ref("zombie", "right_arm")).with("left_arm", ref("zombie", "left_arm"));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), build, 1000.0F);
        // a villager's head starts as a surgeon; its maker puts it to farming
        if (!minion.hasJob("surgeon") || !minion.setJob(BloodAndBones.asResource("farmer"))) {
            helper.fail("A villager's head should start as a surgeon and offer farming");
            return;
        }
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
            var crop = helper.getBlockState(field.above());
            helper.assertTrue(minion.hasJob("farmer") && chest.countItem(Items.WHEAT) >= 1
                    && crop.is(Blocks.WHEAT) && crop.getValue(CropBlock.AGE) < 7, "the farmer has not reaped, replanted and stored the wheat yet");
        });
    }

    /** A minion of this build, made by this player. */
    private static MinionEntity minionOf(GameTestHelper helper, BlockPos pos, MinionBuild build, float blood, Player maker) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, build, blood);
        level.addFreshEntity(minion);
        return minion;
    }

    /** Hit by its maker (a sweep of the sword, a slip), it does not turn on them; nor would it on its maker's other minions. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void neverTurnsOnItsMaker(GameTestHelper helper) {
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        // standing beside it, well within what it would chase
        BlockPos by = helper.absolutePos(new BlockPos(5, 2, 3));
        maker.moveTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
        MinionEntity minion = minionOf(helper, new BlockPos(3, 2, 3), wholeCow(), 500.0F, maker);
        MinionEntity other = minionOf(helper, new BlockPos(7, 2, 7), wholeCow(), 500.0F, maker);
        if (minion.canAttack(maker) || minion.canAttack(other)) {
            helper.fail("A minion should never take its maker, or its maker's other minions, for a target");
            return;
        }
        // not on its first tick: a hurt then is stamped with the same tick a goal starts from, and no goal would see it
        helper.runAfterDelay(5, () -> {
            minion.hurt(helper.getLevel().damageSources().playerAttack(maker), 1.0F);
            helper.runAfterDelay(40, () -> {
                if (minion.getTarget() == maker) {
                    helper.fail("Hurt by its maker, it should not go for them");
                    return;
                }
                helper.succeed();
            });
        });
    }

    /** A pacifist (a villager's pair of arms) takes no target when hurt: it never attacks, so it neither pays for fighting nor stands its ground. */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void pacifistTakesNoTarget(GameTestHelper helper) {
        MinionBuild build = MinionBuild.of(ref("villager", "body")).with("head", ref("villager", "head")).with("arms", ref("villager", "arms"));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), build, 500.0F);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 7));
        zombie.setNoAi(true);
        helper.runAfterDelay(5, () -> {
            minion.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0F);
            helper.runAfterDelay(40, () -> {
                if (minion.stats().fights() || minion.getTarget() != null) {
                    helper.fail("A pacifist should take no target: " + minion.getTarget());
                    return;
                }
                helper.succeed();
            });
        });
    }

    /**
     * Its maker's Cleaver on it, powered down and lying on a clear Assembly Frame table: it comes apart into a frame on
     * the table again, what it carried and its saddle come back, and it no longer counts toward the cap. Anywhere else,
     * the Cleaver does nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void takenApartOnTheTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        maker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(BBItems.CLEAVER.get()));
        MinionEntity away = minionOf(helper, new BlockPos(8, 2, 8), wholeCow(), 100.0F, maker);
        away.powerDown();
        away.interact(maker, net.minecraft.world.InteractionHand.MAIN_HAND);
        if (away.isRemoved()) {
            helper.fail("Off the table, the Cleaver should do nothing");
            return;
        }
        SurgeryTableBlockEntity table = assemblyTable(helper, new BlockPos(3, 2, 3));
        MinionEntity minion = minionOf(helper, new BlockPos(3, 3, 3), horseLegs(), 100.0F, maker);
        minion.inventory.addItem(new ItemStack(Items.BONE, 4));
        minion.equipSaddle(new ItemStack(Items.SADDLE), null);
        MinionBuild build = minion.build().orElseThrow();
        com.avicagan.bloodandbones.minion.MinionCensus.count(level.getServer(), maker.getUUID(), minion.getUUID());
        int before = com.avicagan.bloodandbones.minion.MinionCensus.of(level.getServer(), maker.getUUID());
        minion.powerDown();
        helper.runAfterDelay(10, () -> {
            minion.interact(maker, net.minecraft.world.InteractionHand.MAIN_HAND);
            if (!minion.isRemoved() || !table.build().equals(java.util.Optional.of(build))) {
                helper.fail("It should come apart into its frame on the table: " + minion.isRemoved() + " " + table.build());
                return;
            }
            if (maker.getInventory().countItem(Items.BONE) != 4 || maker.getInventory().countItem(Items.SADDLE) != 1
                    || com.avicagan.bloodandbones.minion.MinionCensus.of(level.getServer(), maker.getUUID()) != before - 1) {
                helper.fail("What it carried and its saddle should come back, and it should count no more");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A minion saved before minions were built of carcass pieces falls apart on its first tick, and gives back all it
     * had: what it carried, the Fluid Backtank it wore (fluid and all) and the implants fitted in it.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void oldMinionFallsApartKeepingEverything(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinionEntity old = BBEntities.MINION.get().create(level);
        ItemStack tank = new ItemStack(BBItems.backtank(com.avicagan.bloodandbones.backtank.BacktankTier.COPPER));
        com.avicagan.bloodandbones.backtank.FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.blood(), 1500));
        old.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, tank);
        com.avicagan.bloodandbones.body.BodyEffects.body(old).fit(com.avicagan.bloodandbones.body.BodyPart.RIGHT_ARM, new ItemStack(BBItems.HOOK_HAND.get()));
        old.inventory.addItem(new ItemStack(Items.BONE, 3));
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        old.saveWithoutId(tag);
        tag.remove("Build");
        MinionEntity loaded = BBEntities.MINION.get().create(level);
        loaded.load(tag);
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
        loaded.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        level.addFreshEntity(loaded);
        helper.runAfterDelay(5, () -> {
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(3));
            boolean backtank = drops.stream().anyMatch(e -> e.getItem().getItem() instanceof com.avicagan.bloodandbones.backtank.FluidBacktankItem
                    && com.avicagan.bloodandbones.backtank.FluidBacktankItem.fluid(e.getItem()).getAmount() == 1500);
            boolean hook = drops.stream().anyMatch(e -> e.getItem().is(BBItems.HOOK_HAND.get()));
            int bones = drops.stream().filter(e -> e.getItem().is(Items.BONE)).mapToInt(e -> e.getItem().getCount()).sum();
            if (!loaded.isRemoved() || !backtank || !hook || bones != 3) {
                helper.fail("It should fall apart, dropping its backtank with its blood, its Hook Hand and its bones: " + loaded.isRemoved() + " "
                        + backtank + " " + hook + " " + bones);
                return;
            }
            helper.succeed();
        });
    }

    /** Knocked or fallen out of the world, it is set back down on solid ground, powered down, not destroyed. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void fallenOutOfTheWorldIsSetDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), wholeCow(), 500.0F);
        minion.teleportTo(minion.getX(), level.getMinBuildHeight() - 70, minion.getZ());
        helper.runAfterDelay(5, () -> {
            if (!minion.isAlive() || minion.isRemoved() || !minion.poweredDown() || minion.getY() < level.getMinBuildHeight()) {
                helper.fail("Out of the world it should be set down on the ground, powered down: " + minion.getY() + " " + minion.isAlive());
                return;
            }
            helper.succeed();
        });
    }

    /** Folded up and dropped, it never goes: it does not age away, burn, or break on a cactus, and it comes back up out of the void. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void foldedMinionIsNeverLost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), cowOnRabbitLegs(), 50.0F);
        minion.powerDown();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        DormantMinionItem.fold(minion, maker);
        ItemStack folded = maker.getInventory().items.stream().filter(st -> st.is(BBItems.DORMANT_MINION.get())).findFirst().orElseThrow();
        BlockPos at = helper.absolutePos(new BlockPos(6, 2, 6));
        ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, folded.copy());
        level.addFreshEntity(item);
        item.hurt(level.damageSources().lava(), 100.0F);
        item.hurt(level.damageSources().cactus(), 100.0F);
        item.hurt(level.damageSources().explosion(null, null), 100.0F);
        if (item.isRemoved() || !item.fireImmune()) {
            helper.fail("Lava, a cactus or a blast should not destroy a folded minion");
            return;
        }
        helper.runAfterDelay(3, () -> {
            if (item.getAge() != -32768) {
                helper.fail("A folded minion on the ground should never age away: " + item.getAge());
                return;
            }
            item.teleportTo(item.getX(), level.getMinBuildHeight() - 10, item.getZ());
            helper.runAfterDelay(3, () -> {
                if (item.isRemoved() || item.getY() < level.getMinBuildHeight()) {
                    helper.fail("Fallen out of the world, it should be set back on the ground: " + item.getY());
                    return;
                }
                helper.succeed();
            });
        });
    }
}
