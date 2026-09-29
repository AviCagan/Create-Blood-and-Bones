package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.SurgicalRig;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryManager;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryPaths;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryTable;
import com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.item.FlensingKnifeItem;
import com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity;
import com.avicagan.bloodandbones.machine.MachineKind;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Docs/BRIEF-AUDIT.md package 5: the yield gap between hand and machine, and the brief's three processing paths kept
 * apart (the Mangler fastest with the least meat and bone but the mob's own drops and scraps, a filtered station full,
 * the Surgery Table slowest with the organs on top), and the machines' costs.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class ButcheryPathTests {
    private static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");
    /** The speed every machine and Deployer in the comparison turns at; the times are worked out at the top speed too. */
    private static final int RPM = 32;
    private static final int TOP_RPM = 256;

    /** Ticks a Deployer takes to push once at a speed: DeployerBlockEntity's timer runs 1000 out, 1000 back, 500 waiting. */
    private static int deployerCycle(int rpm) {
        return (int) Math.ceil(2500.0 / Math.max(8, Math.min(512, rpm * 2)));
    }
    /** A Cleaver's pause between cuts by hand (CarcassPartBlock). */
    private static final int CLEAVER_PAUSE = 12;

    /** One cow's worth of what a path gave, and how long it took. */
    private static final class Tally {
        final Map<Item, Integer> items = new HashMap<>();
        int ticks;

        void add(ItemStack stack) {
            if (!stack.isEmpty()) {
                items.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }

        int of(Item item) {
            return items.getOrDefault(item, 0);
        }

        int meatAndBone() {
            return of(Items.BEEF) + of(Items.BONE);
        }

        @Override
        public String toString() {
            return ticks + " ticks, " + items;
        }
    }

    /** A machine flush in a floor on a creative motor turning it at the comparison's speed. */
    private static CarcassMachineBlockEntity machine(GameTestHelper helper, BlockPos at, BlockEntry<?> kind) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(at.offset(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(at.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(at, kind.getDefaultState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(at.below())) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(RPM);
        }
        return (CarcassMachineBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
    }

    /** A cow killed with the Meat Hook just above a spot. */
    private static CarcassSavedData.Carcass cow(GameTestHelper helper, BlockPos at) {
        Mob mob = helper.spawn(EntityType.COW, at);
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
        }
        return carcass;
    }

    /** Every cow record with a body in this box (relative to the test): one cow and the pieces cut off it. */
    private static List<CarcassSavedData.Carcass> cowsIn(GameTestHelper helper, AABB box) {
        ServerLevel level = helper.getLevel();
        AABB world = box.move(helper.absolutePos(BlockPos.ZERO).getX(), helper.absolutePos(BlockPos.ZERO).getY(), helper.absolutePos(BlockPos.ZERO).getZ());
        List<CarcassSavedData.Carcass> found = new ArrayList<>();
        for (CarcassSavedData.Carcass carcass : List.copyOf(CarcassSavedData.get(level).all())) {
            if (!carcass.entity.equals(COW)) {
                continue;
            }
            for (String bone : carcass.bones.keySet()) {
                Vector3d at = CarcassAssembler.boneWorldPosition(level, carcass, bone);
                if (at != null && world.contains(at.x, at.y, at.z)) {
                    found.add(carcass);
                    break;
                }
            }
        }
        return found;
    }

    /** Pick up every light piece of the cows in a box, as a player's empty hand would, and hand them over. */
    private static List<ItemStack> pickUpLight(GameTestHelper helper, AABB box, Player player) {
        ServerLevel level = helper.getLevel();
        List<ItemStack> picked = new ArrayList<>();
        for (CarcassSavedData.Carcass piece : cowsIn(helper, box)) {
            if (!piece.joints.isEmpty() || piece.bones.size() != 1) {
                continue;
            }
            player.getInventory().clearContent();
            if (CarcassButchery.pickUp(level, player, piece, piece.rootBone)) {
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    if (!player.getInventory().getItem(slot).isEmpty()) {
                        picked.add(player.getInventory().getItem(slot).copy());
                    }
                }
            }
            player.getInventory().clearContent();
        }
        return picked;
    }

    /** Every item lying in a box (relative to the test), counted into a tally and cleared away. */
    private static void sweep(GameTestHelper helper, AABB box, Tally into) {
        AABB world = box.move(helper.absolutePos(BlockPos.ZERO).getX(), helper.absolutePos(BlockPos.ZERO).getY(), helper.absolutePos(BlockPos.ZERO).getZ());
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, world)) {
            into.add(item.getItem());
            item.discard();
        }
    }

    /**
     * The brief's paths, one cow down each, and what each gave and took (at 32 RPM for every machine and Deployer):
     * <ul>
     * <li>by hand: the Flensing Knife held on it, the Cleaver on each joint and piece (a real player's hand);</li>
     * <li>the Mangler: lying on one, until nothing of it is left;</li>
     * <li>filtered stations: a Deglover, a Beheader, a Guillotine under it in turn (each swapped in under the body), then
     * a Deployer's Cleaver at a Butcher's Table for the body lying on it and each light piece laid on it;</li>
     * <li>the Surgical Rig: lying on the table, a Deployer's Cleaver cut after cut, the pieces that fall off laid back on.</li>
     * </ul>
     * The Mangler is quickest, then the stations, the Surgery Table slowest; the stations' hide is whole where the hand's
     * is half or lost; the Mangler's output has the mob's own drops and scraps, the table the organs. What each path gets
     * of a cow on average is then rolled 300 times through the same code: the hand about half of a station, the Mangler's
     * share of meat and bone the least, the Surgery Table all of it.
     */
    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void cowDownEachPath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer deployer = FakePlayerFactory.getMinecraft(level);
        ItemStack deployerCleaver = new ItemStack(BBItems.CLEAVER.get());
        Player hand = helper.makeMockPlayer(GameType.SURVIVAL);
        Player carrier = helper.makeMockPlayer(GameType.SURVIVAL);
        Tally byHand = new Tally();
        Tally mangled = new Tally();
        Tally filtered = new Tally();
        Tally surgery = new Tally();

        BlockPos manglerAt = new BlockPos(2, 3, 2);
        BlockPos stationAt = new BlockPos(8, 3, 2);
        BlockPos tableAt = new BlockPos(2, 2, 8);
        AABB manglerArea = new AABB(0, 2, 0, 5, 7, 5);
        AABB stationArea = new AABB(6, 2, 0, 11, 7, 5);
        AABB tableArea = new AABB(0, 1, 6, 5, 7, 11);
        AABB handArea = new AABB(6, 1, 6, 11, 7, 11);

        CarcassMachineBlockEntity mangler = machine(helper, manglerAt, BBBlocks.MANGLER);
        machine(helper, stationAt, BBBlocks.DEGLOVER);
        // the Surgery Table sunk so its top is flush with a floor round it: the body lies across it
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(tableAt.offset(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(tableAt, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) level.getBlockEntity(helper.absolutePos(tableAt));

        CarcassSavedData.Carcass manglerCow = cow(helper, manglerAt.above());
        cow(helper, stationAt.above());
        cow(helper, tableAt.above());
        CarcassSavedData.Carcass handCow = cow(helper, new BlockPos(8, 2, 8));
        if (manglerCow == null || handCow == null || table == null) {
            return;
        }
        long[] started = new long[1];
        int[] stroke = {0, 0, 0};
        // the Butcher's Table's chops and the rig's cuts, for the times at the top speed
        int[] chopped = {0};
        int[] rigCuts = {0};

        helper.startSequence()
                // by hand: all at once, timed by the tools' own pauses
                .thenExecute(() -> {
                    int strokes = 0;
                    while (!handCow.skinned && strokes < 20) {
                        CarcassButchery.capturing(byHand::add, () -> CarcassButchery.skin(level, hand, handCow, null));
                        strokes++;
                    }
                    int cuts = 0;
                    for (int guard = 0; guard < 400 && !cowsIn(helper, handArea).isEmpty(); guard++) {
                        CarcassSavedData.Carcass piece = cowsIn(helper, handArea).getFirst();
                        String bone = piece.joints.isEmpty() ? piece.rootBone : piece.joints.stream()
                                .filter(j -> piece.joints.stream().noneMatch(o -> o.parent().equals(j.child()))).findFirst().orElse(piece.joints.getFirst()).child();
                        if (CarcassButchery.capturing(byHand::add, () -> CarcassButchery.cut(level, hand, piece, bone, null))) {
                            cuts++;
                        }
                    }
                    byHand.ticks = strokes * FlensingKnifeItem.STROKE_TICKS + cuts * CLEAVER_PAUSE;
                    helper.assertTrue(cowsIn(helper, handArea).isEmpty(), "the cow taken apart by hand should be all gone");
                    started[0] = helper.getTick();
                })
                // the Mangler until the cow is gone; the Deglover until it is skinned, at the same time
                .thenWaitUntil(() -> {
                    if (mangled.ticks == 0 && cowsIn(helper, manglerArea).isEmpty()) {
                        mangled.ticks = (int) (helper.getTick() - started[0]);
                    }
                    helper.assertTrue(mangled.ticks > 0, "the Mangler has not finished the cow yet");
                })
                .thenExecute(() -> {
                    for (int slot = 0; slot < mangler.output.getSlots(); slot++) {
                        mangled.add(mangler.output.getStackInSlot(slot));
                    }
                })
                .thenWaitUntil(() -> {
                    List<CarcassSavedData.Carcass> here = cowsIn(helper, stationArea);
                    helper.assertTrue(!here.isEmpty() && here.stream().allMatch(c -> c.skinned), "the Deglover has not skinned the cow yet");
                })
                .thenExecute(() -> {
                    CarcassMachineBlockEntity deglover = (CarcassMachineBlockEntity) level.getBlockEntity(helper.absolutePos(stationAt));
                    stroke[0] = deglover.strokes;
                    for (int slot = 0; slot < deglover.output.getSlots(); slot++) {
                        filtered.add(deglover.output.getStackInSlot(slot));
                        deglover.output.setStackInSlot(slot, ItemStack.EMPTY);
                    }
                    helper.setBlock(stationAt, BBBlocks.BEHEADER.getDefaultState());
                })
                .thenWaitUntil(() -> helper.assertTrue(cowsIn(helper, stationArea).stream().noneMatch(c -> c.joints.stream().anyMatch(j -> j.child().equals("head"))),
                        "the Beheader has not taken the head yet"))
                .thenExecute(() -> {
                    CarcassMachineBlockEntity beheader = (CarcassMachineBlockEntity) level.getBlockEntity(helper.absolutePos(stationAt));
                    stroke[1] = beheader.strokes;
                    for (int slot = 0; slot < beheader.output.getSlots(); slot++) {
                        filtered.add(beheader.output.getStackInSlot(slot));
                    }
                    helper.setBlock(stationAt, BBBlocks.GUILLOTINE.getDefaultState());
                })
                // the Guillotine drops each time it is wound up (a pulse from a block of redstone beside it)
                .thenWaitUntil(() -> {
                    CarcassMachineBlockEntity guillotine = (CarcassMachineBlockEntity) level.getBlockEntity(helper.absolutePos(stationAt));
                    if (guillotine.wind >= 1.0F) {
                        helper.setBlock(stationAt.east(), Blocks.REDSTONE_BLOCK);
                    } else if (level.getBlockState(helper.absolutePos(stationAt.east())).is(Blocks.REDSTONE_BLOCK)) {
                        helper.setBlock(stationAt.east(), Blocks.STONE);
                    }
                    helper.assertTrue(cowsIn(helper, stationArea).stream().allMatch(c -> c.joints.isEmpty()), "the Guillotine has not taken every leg yet: "
                            + cowsIn(helper, stationArea).stream().map(c -> c.joints.stream().map(j -> j.child()).toList() + "@"
                            + CarcassAssembler.boneWorldPosition(level, c, c.rootBone)).toList() + " wind " + guillotine.wind + " strokes " + guillotine.strokes
                            + " at " + helper.absolutePos(stationAt));
                })
                .thenExecute(() -> {
                    CarcassMachineBlockEntity guillotine = (CarcassMachineBlockEntity) level.getBlockEntity(helper.absolutePos(stationAt));
                    stroke[2] = guillotine.strokes;
                    // the machines' time: their strokes at this speed (the Guillotine's are wind-ups)
                    int machineTicks = stroke[0] * strokeTicks(MachineKind.DEGLOVER, RPM) + stroke[1] * strokeTicks(MachineKind.BEHEADER, RPM)
                            + stroke[2] * strokeTicks(MachineKind.GUILLOTINE, RPM);
                    // the light pieces go into hands; the body stays where it lies, and a Butcher's Table takes the machine's place under it
                    List<ItemStack> carried = pickUpLight(helper, stationArea, carrier);
                    helper.setBlock(stationAt.east(), Blocks.STONE);
                    helper.setBlock(stationAt, BBBlocks.BUTCHER_TABLE.getDefaultState());
                    ButcherTableBlockEntity butcher = (ButcherTableBlockEntity) level.getBlockEntity(helper.absolutePos(stationAt));
                    int chops = 0;
                    for (int guard = 0; guard < 4 && butcher.lying(level) != null; guard++) {
                        if (butcher.chop(level, deployerCleaver, deployer)) {
                            chops++;
                        }
                    }
                    for (ItemStack piece : carried) {
                        butcher.put(piece);
                        if (butcher.chop(level, deployerCleaver, deployer)) {
                            chops++;
                        }
                        butcher.take();
                    }
                    filtered.ticks = machineTicks + chops * deployerCycle(RPM);
                    chopped[0] = chops;
                    sweep(helper, stationArea.inflate(1.0), filtered);
                    helper.assertTrue(cowsIn(helper, stationArea).isEmpty(), "the stations should have taken the whole cow: "
                            + cowsIn(helper, stationArea).stream().map(c -> c.bones.keySet()).toList());
                })
                // the Surgical Rig: a Deployer's Cleaver, cut after cut, each taking the rig's pause at least; what falls off
                // the table is laid back on it
                .thenExecute(() -> {
                    int cuts = 0;
                    for (int guard = 0; guard < 60 && !cowsIn(helper, tableArea).isEmpty(); guard++) {
                        if (SurgicalRig.cut(level, deployer, table, deployerCleaver)) {
                            cuts++;
                            continue;
                        }
                        for (ItemStack piece : pickUpLight(helper, tableArea, carrier)) {
                            table.put(piece);
                            while (!table.item().isEmpty() && SurgicalRig.cut(level, deployer, table, deployerCleaver)) {
                                cuts++;
                            }
                            table.take();
                        }
                    }
                    surgery.ticks = cuts * Math.max(SurgicalRig.PAUSE, deployerCycle(RPM));
                    rigCuts[0] = cuts;
                    sweep(helper, tableArea.inflate(1.0), surgery);
                    helper.assertTrue(cowsIn(helper, tableArea).isEmpty(), "the Surgical Rig should have taken the whole cow");
                })
                .thenExecute(() -> {
                    BloodAndBones.LOGGER.info("[paths] hand {} | mangler {} | stations {} | surgery {}", byHand, mangled, filtered, surgery);
                    // times: the Mangler quickest, the Surgery Table slowest, at 32 RPM and at 256, and by hand at the rig
                    helper.assertTrue(mangled.ticks < filtered.ticks, "the Mangler should be quicker through a cow than the stations: " + mangled.ticks + " / " + filtered.ticks);
                    helper.assertTrue(filtered.ticks < surgery.ticks, "the Surgery Table should be the slowest: " + filtered.ticks + " / " + surgery.ticks);
                    int manglerTop = mangler.strokes * strokeTicks(MachineKind.MANGLER, TOP_RPM);
                    int stationsTop = stroke[0] * strokeTicks(MachineKind.DEGLOVER, TOP_RPM) + stroke[1] * strokeTicks(MachineKind.BEHEADER, TOP_RPM)
                            + stroke[2] * strokeTicks(MachineKind.GUILLOTINE, TOP_RPM) + chopped[0] * deployerCycle(TOP_RPM);
                    int rigTop = rigCuts[0] * Math.max(SurgicalRig.PAUSE, deployerCycle(TOP_RPM));
                    int rigByHand = rigCuts[0] * SurgicalRig.PAUSE;
                    BloodAndBones.LOGGER.info("[paths] at {} RPM: mangler {} | stations {} | surgery {} | surgery by hand {}", TOP_RPM, manglerTop, stationsTop, rigTop, rigByHand);
                    helper.assertTrue(manglerTop < stationsTop && stationsTop < rigTop, "at 256 RPM too the Mangler should be quickest and the rig slowest: "
                            + manglerTop + " / " + stationsTop + " / " + rigTop);
                    helper.assertTrue(rigByHand > filtered.ticks && rigByHand > stationsTop, "the rig by hand should be slower than the stations: " + rigByHand);
                    // what each gave that the others could not
                    helper.assertTrue(filtered.of(BBItems.RAW_HIDE.get()) >= 2, "a Deglover takes the whole hide (2.4 on a cow): " + filtered);
                    helper.assertTrue(mangled.of(Items.BEEF) >= 1 && mangled.items.containsKey(BBItems.SCRAPS.get()),
                            "the Mangler's output should hold its quarter of the cow's beef and armour scraps: " + mangled);
                    helper.assertTrue(mangled.of(BBItems.RAW_HIDE.get()) == 0 && mangled.of(BBItems.OFFAL.get()) == 0,
                            "the Mangler grinds the hide and the offal away: " + mangled);
                    helper.assertTrue(surgery.of(BBItems.HEART.get()) == 1 && surgery.of(BBItems.LUNGS.get()) == 1 && surgery.of(BBItems.STOMACH.get()) == 1
                            && surgery.of(BBItems.GLAND.get()) == 1 && surgery.of(BBItems.EYE.get()) == 2,
                            "the Surgery Table should give a cow's heart, lungs, stomach, rumen and two eyes: " + surgery);
                    // one cow's rolls: at least the whole numbers of its table (4.22 beef and 1.58 bone in the body alone); that the rig
                    // gets all of it is the average's to show, below and in surgicalRigByHandGetsAHandsShare
                    helper.assertTrue(surgery.of(Items.BEEF) >= 4 && surgery.of(Items.BONE) >= 1, "and its meat and bone besides: " + surgery);
                    helper.assertTrue(surgery.of(BBItems.RAW_HIDE.get()) >= 2, "and its whole hide, taken as one of its cuts (2.4 on a cow): " + surgery);
                    helper.assertTrue(filtered.of(BBItems.HEART.get()) == 0 && byHand.of(BBItems.HEART.get()) == 0 && filtered.of(BBItems.GLAND.get()) == 0
                            && byHand.of(BBItems.GLAND.get()) == 0 && mangled.of(BBItems.GLAND.get()) == 0, "only the Surgery Table takes organs out");
                    // and on average, rolled many times through the same code
                    Tally hands = average(level, hand, ButcheryPaths.HAND, COW);
                    Tally stations = average(level, null, ButcheryPaths.STATION, COW);
                    Tally ground = average(level, null, ButcheryPaths.MANGLER, COW);
                    Tally operated = average(level, null, ButcheryPaths.SURGERY, COW);
                    BloodAndBones.LOGGER.info("[paths] 300 cows: hand {} | mangler {} | stations {} | surgery {}", hands.items, ground.items, stations.items, operated.items);
                    float handShare = hands.meatAndBone() / (float) stations.meatAndBone();
                    helper.assertTrue(handShare > 0.42F && handShare < 0.6F, "by hand should give about half a station's meat and bone: " + handShare);
                    helper.assertTrue(ground.meatAndBone() < hands.meatAndBone(), "the Mangler should keep the least of the carcass's meat and bone");
                    helper.assertTrue(Math.abs(operated.meatAndBone() - stations.meatAndBone()) < stations.meatAndBone() * 0.05F,
                            "the Surgery Table should give all of it, as a station does");
                    helper.assertTrue(hands.of(BBItems.RAW_HIDE.get()) < stations.of(BBItems.RAW_HIDE.get()) * 0.6F, "and about half the hide by hand");
                })
                .thenSucceed();
    }

    /** A machine's stroke (the Guillotine's wind-up) at a speed. */
    private static int strokeTicks(MachineKind kind, int rpm) {
        int speed = Math.max(1, Math.min(rpm, kind.maxRpm) / 16);
        return Math.max(CarcassMachineBlockEntity.MIN_STROKE, Math.round(CarcassMachineBlockEntity.BASE_STROKE * kind.pace / speed));
    }

    /**
     * What 300 fresh carcasses of a mob give down a path, their hides and every piece, summed, and down a path that rolls
     * the mob's own loot (the Mangler's), that too; a hand path by this player's hand.
     */
    private static Tally average(ServerLevel level, Player who, ResourceLocation path, ResourceLocation mob) {
        Tally tally = new Tally();
        ButcheryTable table = ButcheryManager.forEntity(mob).orElseThrow();
        String root = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(mob).orElseThrow().root().name();
        for (int i = 0; i < 300; i++) {
            CarcassSavedData.Carcass stand = new CarcassSavedData.Carcass(UUID.randomUUID(), mob, root);
            java.util.function.Supplier<Boolean> all = () -> {
                CarcassButchery.dropYields(level, stand, table.hide(), 1.0F, new Vector3d());
                for (String bone : table.parts().keySet()) {
                    CarcassButchery.dropYields(level, stand, table.part(bone), 1.0F, new Vector3d());
                }
                if (CarcassButchery.path().lootTable()) {
                    CarcassButchery.rollLoot(level, stand, new Vector3d());
                }
                return true;
            };
            CarcassButchery.capturing(tally::add, () -> who != null ? CarcassButchery.byHand(who, all) : CarcassButchery.onPath(path, 1.0F, all));
        }
        return tally;
    }

    /**
     * By hand about half, with real loss: past the share, each whole piece is a chance to botch it. Four beef exactly, by
     * a hand whose butchery yield makes up the hand's share, still sometimes comes out fewer; a Deployer's stand-in always
     * gets all four. A player's butchery yield (the keen_butcher trait's attribute) scales what their hand gets.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void handYieldIsAboutHalfWithRealLoss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var attribute = player.getAttribute(com.avicagan.bloodandbones.registry.BBAttributes.BUTCHERY_YIELD);
        helper.assertTrue(attribute != null && attribute.getValue() == 1.0, "players should have a butchery yield of 1");
        List<com.avicagan.bloodandbones.carcass.butchery.Yield> four = List.of(new com.avicagan.bloodandbones.carcass.butchery.Yield("minecraft:beef", 4.0F, "meat"));
        CarcassSavedData.Carcass stand = new CarcassSavedData.Carcass(UUID.randomUUID(), COW, "body");
        attribute.setBaseValue(1.0 / ButcheryPaths.get(ButcheryPaths.HAND).share("meat"));
        int botched = 0;
        int stationShort = 0;
        for (int i = 0; i < 200; i++) {
            int[] beef = {0, 0};
            CarcassButchery.capturing(stack -> beef[0] += stack.getCount(), () -> CarcassButchery.byHand(player, () -> {
                CarcassButchery.dropYields(level, stand, four, 1.0F, new Vector3d());
                return true;
            }));
            CarcassButchery.capturing(stack -> beef[1] += stack.getCount(), () -> CarcassButchery.byHand(FakePlayerFactory.getMinecraft(level), () -> {
                CarcassButchery.dropYields(level, stand, four, 1.0F, new Vector3d());
                return true;
            }));
            botched += beef[0] < 4 ? 1 : 0;
            stationShort += beef[1] != 4 ? 1 : 0;
        }
        helper.assertTrue(stationShort == 0, "a Deployer's stand-in works as a station: four beef should always come out four");
        helper.assertTrue(botched > 0, "by hand, loss is real: now and then four beef should come out fewer");
        // twice as good a butcher gets about twice as much
        attribute.setBaseValue(1.0);
        Tally plain = average(level, player, ButcheryPaths.HAND, COW);
        attribute.setBaseValue(2.0);
        Tally keen = average(level, player, ButcheryPaths.HAND, COW);
        helper.assertTrue(keen.meatAndBone() > plain.meatAndBone() * 1.6F, "twice the butchery yield should give about twice as much: "
                + keen.meatAndBone() + " / " + plain.meatAndBone());
        helper.succeed();
    }

    /**
     * The Mangler's path: the carcass's own table at a quarter, the offal, fat and hide ground away, armour scraps for
     * every piece, and the mob's own loot table with its body, less what the table already gives: an iron golem ground
     * gives no more iron than its quarter of the table (its loot's 3 to 5 ingots are the table's iron), a cow gives the
     * leather its table does not (and never its loot's beef on top), and a black sheep never a white fleece (its loot is
     * rolled as the sheep it was: black, and no wool once it is skinned). A baby gives no drops, as in the game.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void manglerGrindsInTheMobsOwnDrops(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(5, 2, 5));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(golem, null);
        golem.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        helper.runAfterDelay(5, () -> {
            List<ItemStack> out = new ArrayList<>();
            Vector3d at = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
            CarcassButchery.mangling(out::add, () -> {
                CarcassButchery.butcher(level, carcass, carcass.rootBone, at);
                return true;
            });
            // its body's table gives three iron, of which the Mangler keeps a quarter (none or one); its loot's iron is not added
            int iron = out.stream().filter(s -> s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(iron <= 1, "an iron golem ground by the Mangler should give no more than its quarter of the iron: " + out);
            helper.assertTrue(out.stream().anyMatch(s -> s.is(BBItems.SCRAPS.get())), "and armour scraps: " + out);
            // a cow's loot, rolled over and over: its leather, which no table gives, and never its beef
            List<ItemStack> cow = new ArrayList<>();
            CarcassSavedData.Carcass cowStand = new CarcassSavedData.Carcass(UUID.randomUUID(), COW, "body");
            CarcassButchery.capturing(cow::add, () -> {
                for (int i = 0; i < 30; i++) {
                    CarcassButchery.rollLoot(level, cowStand, new Vector3d());
                }
                return true;
            });
            helper.assertTrue(cow.stream().anyMatch(s -> s.is(Items.LEATHER)) && cow.stream().noneMatch(s -> s.is(Items.BEEF)),
                    "a cow's own drops should add its leather and not its beef: " + cow);
            // a black sheep, skinned or not, gives no fleece of the wrong colour
            for (boolean skinned : List.of(false, true)) {
                CarcassSavedData.Carcass sheep = new CarcassSavedData.Carcass(UUID.randomUUID(), ResourceLocation.withDefaultNamespace("sheep"), "body");
                sheep.traits.put("wool", "black");
                sheep.skinned = skinned;
                List<ItemStack> fleece = new ArrayList<>();
                CarcassButchery.capturing(fleece::add, () -> {
                    for (int i = 0; i < 20; i++) {
                        CarcassButchery.rollLoot(level, sheep, new Vector3d());
                    }
                    return true;
                });
                helper.assertTrue(fleece.stream().noneMatch(s -> s.is(net.minecraft.tags.ItemTags.WOOL)), "a black sheep's own drops should give no wool again: " + fleece);
            }
            CarcassSavedData.Carcass stand = new CarcassSavedData.Carcass(UUID.randomUUID(), carcass.entity, carcass.rootBone);
            List<ItemStack> kept = new ArrayList<>();
            CarcassButchery.mangling(kept::add, () -> {
                ButcheryManager.forEntity(carcass.entity).ifPresent(t -> CarcassButchery.dropYields(level, stand, t.part(stand.rootBone), 1.0F, new Vector3d()));
                return true;
            });
            helper.assertTrue(kept.stream().filter(s -> s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum() <= 1,
                    "of its body's own three iron the Mangler should keep a quarter: " + kept);
            // a baby drops nothing, as in the game
            CarcassSavedData.Carcass calf = new CarcassSavedData.Carcass(UUID.randomUUID(), COW, "body");
            calf.baby = true;
            List<ItemStack> baby = new ArrayList<>();
            CarcassButchery.capturing(baby::add, () -> {
                CarcassButchery.rollLoot(level, calf, new Vector3d());
                return true;
            });
            helper.assertTrue(baby.isEmpty(), "a baby's loot table is not rolled: " + baby);
            // an unskinned piece ground whole: its hide goes at the path's share for hide, none on the Mangler's (a path
            // with no file of its own takes everything, so there the body's share of the hide comes out)
            CarcassSavedData.Carcass hidden = new CarcassSavedData.Carcass(UUID.randomUUID(), COW, "body");
            List<ItemStack> ground = new ArrayList<>();
            List<ItemStack> kept2 = new ArrayList<>();
            CarcassButchery.mangling(ground::add, () -> {
                CarcassButchery.groundHide(level, hidden, "body", new Vector3d());
                return true;
            });
            CarcassButchery.capturing(kept2::add, () -> CarcassButchery.onPath(BloodAndBones.asResource("test_no_such_path"), 1.0F, () -> {
                CarcassButchery.groundHide(level, hidden, "body", new Vector3d());
                return true;
            }));
            helper.assertTrue(ground.isEmpty() && kept2.stream().anyMatch(s -> s.is(BBItems.RAW_HIDE.get())),
                    "the hide share of a path should decide what a ground piece's hide gives: " + ground + " / " + kept2);
            // the path's shares are data
            var mangler = ButcheryPaths.get(ButcheryPaths.MANGLER);
            helper.assertTrue(mangler.lootTable() && mangler.scraps() && mangler.share("meat") < ButcheryPaths.get(ButcheryPaths.HAND).share("meat")
                    && mangler.share("offal") == 0.0F && mangler.share("hide") == 0.0F, "the Mangler's path should be the least meat, no offal or hide, loot and scraps");
            helper.succeed();
        });
    }

    /**
     * The Mangler gets the least of anything a butchery table gives, for any mob, with the mob's own drops counted in: 300
     * cows, chickens and iron golems each, down the Mangler (drops and all) and by hand. A chicken's own drop is about all
     * of its table's meat and a golem's more than its table's iron, so drops on top of a quarter would beat a hand's half.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void manglerKeepsTheLeastOfAnyMob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player hand = helper.makeMockPlayer(GameType.SURVIVAL);
        for (ResourceLocation mob : List.of(COW, ResourceLocation.withDefaultNamespace("chicken"), ResourceLocation.withDefaultNamespace("iron_golem"))) {
            Tally hands = average(level, hand, ButcheryPaths.HAND, mob);
            Tally ground = average(level, null, ButcheryPaths.MANGLER, mob);
            ButcheryTable table = ButcheryManager.forEntity(mob).orElseThrow();
            java.util.Set<Item> tabled = new java.util.HashSet<>();
            table.hide().forEach(y -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(y.item())).ifPresent(tabled::add));
            table.parts().values().forEach(ys -> ys.forEach(y -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(y.item()))
                    .ifPresent(tabled::add)));
            int handTotal = 0;
            int groundTotal = 0;
            for (Item item : tabled) {
                handTotal += hands.of(item);
                groundTotal += ground.of(item);
                helper.assertTrue(ground.of(item) <= hands.of(item), mob + ": the Mangler should get no more " + item + " than a hand: "
                        + ground.of(item) + " / " + hands.of(item));
            }
            BloodAndBones.LOGGER.info("[paths] 300 {}: hand {} | mangler {}", mob, hands.items, ground.items);
            helper.assertTrue(groundTotal < handTotal * 0.75F, mob + ": the Mangler should get the least of what the table gives: " + groundTotal + " / " + handTotal);
            if (mob.equals(COW)) {
                helper.assertTrue(ground.of(Items.LEATHER) > 0, "a cow's own leather should come with the Mangler's drops: " + ground);
            }
        }
        helper.succeed();
    }

    /**
     * The Surgical Rig is paced, whoever holds the Cleaver: a Deployer at 256 RPM pushes every few ticks, but the rig takes
     * a cow body's four organs no quicker than one each {@link SurgicalRig#PAUSE} ticks; a player clicking every tick on a
     * cow's head gets its two eyes and its hide in the time three pauses take, not the whole head at once.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void surgicalRigIsPacedWhoeverCuts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rigPos = new BlockPos(3, 2, 3);
        BlockPos deployerPos = rigPos.above(2);
        helper.setBlock(rigPos, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
        helper.setBlock(deployerPos, AllBlocks.DEPLOYER.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.DOWN)
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        helper.setBlock(deployerPos.east(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.WEST));
        if (level.getBlockEntity(helper.absolutePos(deployerPos.east())) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(TOP_RPM);
        }
        SurgeryTableBlockEntity byDeployer = (SurgeryTableBlockEntity) level.getBlockEntity(helper.absolutePos(rigPos));
        byDeployer.put(freshPiece("body"));
        // a player's own rig beside it, a head on it
        BlockPos handPos = new BlockPos(8, 2, 3);
        helper.setBlock(handPos, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
        SurgeryTableBlockEntity byHand = (SurgeryTableBlockEntity) level.getBlockEntity(helper.absolutePos(handPos));
        byHand.put(freshPiece("head"));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack cleaver = new ItemStack(BBItems.CLEAVER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, cleaver);
        BlockPos handAt = helper.absolutePos(handPos);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(handAt), Direction.UP, handAt, false);
        long[] firstCut = {-1};
        long[] lastCut = {-1};
        int[] seen = {0};
        int[] clicks = {0};
        helper.runAfterDelay(2, () -> {
            var deployer = (com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity) level.getBlockEntity(helper.absolutePos(deployerPos));
            deployer.getPlayer().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.CLEAVER.get()));
        });
        helper.onEachTick(() -> {
            // the player clicks every tick for three pauses' worth
            if (clicks[0] < SurgicalRig.PAUSE * 3 - 2) {
                clicks[0]++;
                level.getBlockState(handAt).useItemOn(cleaver, level, player, InteractionHand.MAIN_HAND, hit);
            }
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(byDeployer.item());
            int taken = piece == null ? seen[0] : com.avicagan.bloodandbones.body.Surgery.organsTaken(piece);
            if (taken > seen[0]) {
                if (firstCut[0] < 0) {
                    firstCut[0] = level.getGameTime();
                }
                lastCut[0] = level.getGameTime();
                seen[0] = taken;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen[0] >= 3 && clicks[0] >= SurgicalRig.PAUSE * 3 - 2, "the Deployer has taken " + seen[0] + " organs");
            helper.assertTrue(lastCut[0] - firstCut[0] >= 2L * SurgicalRig.PAUSE, "a Deployer at 256 RPM should wait the rig's pause between cuts: "
                    + (lastCut[0] - firstCut[0]) + " ticks for three organs");
            CarcassPieceItem.Piece head = CarcassPieceItem.piece(byHand.item());
            int eyes = (int) player.getInventory().items.stream().filter(s -> s.is(BBItems.EYE.get())).mapToInt(ItemStack::getCount).sum();
            helper.assertTrue(head != null && eyes == 2 && head.skinned(), "a player clicking every tick for three pauses should get two eyes and the hide, and "
                    + "still have the head on the rig: " + eyes + " eyes, " + (head == null ? "no head" : head.skinned() ? "skinned" : "hide on"));
        });
    }

    /** A fresh piece of a cow as carried, its organs and hide still in it. */
    private static ItemStack freshPiece(String bone) {
        ItemStack stack = new ItemStack(BBItems.CARCASS_PIECE.get());
        stack.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new com.avicagan.bloodandbones.item.CarcassPieceItem.Piece(COW, bone,
                ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of(), 1.0F, false, Map.of(), 0.0F, 0.0F, 0.0F, false));
        return stack;
    }

    /**
     * By hand the rig gets the organs whole but the meat and bone as a hand does: a hundred cow bodies, organs and hide
     * already out, cut up at the rig by a player's own Cleaver come to about half of what a Deployer's give.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void surgicalRigByHandGetsAHandsShare(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rigPos = new BlockPos(3, 2, 3);
        helper.setBlock(rigPos, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
        SurgeryTableBlockEntity rig = (SurgeryTableBlockEntity) level.getBlockEntity(helper.absolutePos(rigPos));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        FakePlayer deployer = FakePlayerFactory.getMinecraft(level);
        ItemStack blade = new ItemStack(BBItems.CLEAVER.get());
        Tally hand = new Tally();
        Tally station = new Tally();
        AABB top = new AABB(helper.absolutePos(rigPos)).inflate(2.0);
        for (int i = 0; i < 100; i++) {
            for (Player who : List.of(player, deployer)) {
                ItemStack body = new ItemStack(BBItems.CARCASS_PIECE.get());
                body.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new com.avicagan.bloodandbones.item.CarcassPieceItem.Piece(COW, "body",
                        ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of(), 1.0F, true,
                        Map.of(com.avicagan.bloodandbones.body.Surgery.ORGANS_TAKEN, Integer.toString(com.avicagan.bloodandbones.parts.Organs.held(
                                com.avicagan.bloodandbones.parts.PartsData.SERVER, COW, false, "body").size())), 0.0F, 0.0F, 0.0F, false));
                rig.put(body);
                helper.assertTrue(SurgicalRig.cut(level, who, rig, blade) && rig.item().isEmpty(), "a skinned body with its organs out should come apart in one cut");
                for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, top)) {
                    (who == player ? hand : station).add(item.getItem());
                    item.discard();
                }
            }
        }
        float share = hand.meatAndBone() / (float) station.meatAndBone();
        BloodAndBones.LOGGER.info("[paths] rig by hand {} | rig by Deployer {}", hand.items, station.items);
        helper.assertTrue(share > 0.4F && share < 0.62F, "by hand the rig should give about half the meat and bone a Deployer gets: " + share);
        // and a Deployer all of it: a hundred bodies' worth of the body's table
        float table = 0.0F;
        for (var yield : ButcheryManager.forEntity(COW).orElseThrow().part("body")) {
            if (yield.kind().equals("meat") || yield.kind().equals("bone")) {
                table += yield.count() * 100;
            }
        }
        helper.assertTrue(Math.abs(station.meatAndBone() - table) < table * 0.1F, "a Deployer at the rig should get all of the table's meat and bone: "
                + station.meatAndBone() + " / " + table);
        helper.succeed();
    }

    /**
     * The machines' costs, as the brief has them: the Beheader is the cheapest and quickest; the Deglover costs the most
     * stress and works no faster above 32 RPM, so a slow shaft is the sensible one; the Mangler takes a whole carcass in
     * one stroke a piece.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void machinesCostAsTheBriefSays(GameTestHelper helper) {
        double mangler = BlockStressValues.getImpact(BBBlocks.MANGLER.get());
        double guillotine = BlockStressValues.getImpact(BBBlocks.GUILLOTINE.get());
        double beheader = BlockStressValues.getImpact(BBBlocks.BEHEADER.get());
        double deglover = BlockStressValues.getImpact(BBBlocks.DEGLOVER.get());
        helper.assertTrue(beheader < guillotine && beheader < mangler && beheader < deglover, "the Beheader should cost the least stress");
        helper.assertTrue(deglover > mangler && deglover > guillotine, "the Deglover should cost the most stress");
        BlockPos at = new BlockPos(5, 3, 5);
        CarcassMachineBlockEntity be = machine(helper, at, BBBlocks.DEGLOVER);
        CreativeMotorBlockEntity motor = (CreativeMotorBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at.below()));
        helper.runAfterDelay(5, () -> {
            int at32 = be.strokeTicks();
            motor.generatedSpeed.setValue(256);
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(Math.abs(be.getSpeed()) == 256, "the Deglover should turn at 256 RPM now");
                helper.assertTrue(be.strokeTicks() == at32, "the Deglover should work no faster at 256 RPM than at 32: " + be.strokeTicks() + " / " + at32);
                helper.setBlock(at, BBBlocks.BEHEADER.getDefaultState());
                helper.runAfterDelay(5, () -> {
                    CarcassMachineBlockEntity beheader2 = (CarcassMachineBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
                    helper.assertTrue(beheader2.strokeTicks() < at32, "the Beheader should strike faster than the Deglover");
                    helper.assertTrue(strokeTicks(MachineKind.BEHEADER, RPM) <= strokeTicks(MachineKind.MANGLER, RPM)
                            && strokeTicks(MachineKind.BEHEADER, RPM) < strokeTicks(MachineKind.GUILLOTINE, RPM), "the Beheader should be the quickest machine");
                    helper.succeed();
                });
            });
        });
    }

    /** Turn a stand-in player that is never ticked: its view is worked out from where it looked last tick too. */
    private static void look(Player player, Runnable turn) {
        turn.run();
        player.xRotO = player.getXRot();
        player.yRotO = player.getYRot();
        player.yHeadRotO = player.getYHeadRot();
    }

    /**
     * The Flensing Knife is held on a part (the brief: "hold on a part to take it off"): a click only starts it, each ten
     * ticks held is a stroke, the hide comes away after four, and it lets go by itself; looking off the carcass lets go too.
     */
    @GameTest(template = "empty", timeoutTicks = 80)
    public static void flensingKnifeIsHeldOnAPart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CarcassSavedData.Carcass cow = cow(helper, new BlockPos(5, 2, 5));
        if (cow == null) {
            return;
        }
        helper.runAfterDelay(30, () -> {
            Vector3d torso = CarcassAssembler.boneWorldPosition(level, cow, cow.rootBone);
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack knife = new ItemStack(BBItems.FLENSING_KNIFE.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, knife);
            // stand beside it, looking down at its middle
            player.moveTo(torso.x - 1.6, torso.y - 0.9, torso.z, 0, 0);
            look(player, () -> player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, new Vec3(torso.x, torso.y, torso.z)));
            var part = FlensingKnifeItem.partLookedAt(level, player);
            helper.assertTrue(part != null && cow.id.equals(part.carcassId()), "the player should be looking at the cow");
            // the click on the part starts the hold, and does nothing else yet
            BlockPos cell = part.getBlockPos();
            net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(cell), Direction.UP, cell, false);
            level.getBlockState(cell).useItemOn(knife, level, player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(player.isUsingItem() && !cow.skinned, "a click should start the knife working, not skin it");
            int duration = knife.getUseDuration(player);
            for (int held = 1; held <= 30; held++) {
                knife.getItem().onUseTick(level, player, knife, duration - held + 1);
            }
            helper.assertTrue(!cow.skinned && player.isUsingItem(), "three strokes should not have the hide off yet");
            for (int held = 31; held <= 45 && player.isUsingItem(); held++) {
                knife.getItem().onUseTick(level, player, knife, duration - held + 1);
            }
            helper.assertTrue(cow.skinned, "four strokes held should take the hide off");
            helper.assertTrue(!player.isUsingItem(), "with the hide off the knife lets go");
            // looking away from a carcass lets go at once
            CarcassSavedData.Carcass other = cow;
            player.startUsingItem(InteractionHand.MAIN_HAND);
            look(player, () -> {
                player.setXRot(-80.0F);
                player.setYRot(player.getYRot() + 180.0F);
            });
            knife.getItem().onUseTick(level, player, knife, duration);
            helper.assertTrue(!player.isUsingItem(), "looking off the carcass should let go");
            helper.assertTrue(other.skinned, "and leave it as it was");
            helper.succeed();
        });
    }
}
