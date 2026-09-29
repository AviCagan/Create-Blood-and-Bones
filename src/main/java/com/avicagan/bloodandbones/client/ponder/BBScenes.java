package com.avicagan.bloodandbones.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import com.avicagan.bloodandbones.cooking.ButcherHookBlockEntity;
import com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity;

/**
 * Ponder scenes. Schematics are in assets/bloodandbones/ponder/, 5 wide on a snow plate. Ponder worlds do
 * not run kinetics, so every turning block is given its speed here. The text of each scene is in the lang
 * file under bloodandbones.ponder.&lt;scene&gt;.header and .text_N, in showText order.
 */
public final class BBScenes {
    private BBScenes() {
    }

    /** What the shaft's speed does for each machine: the Guillotine only winds faster, the Deglover gains nothing past 32 RPM. */
    public static String speedLine(com.avicagan.bloodandbones.machine.MachineKind kind) {
        return switch (kind) {
            case GUILLOTINE -> "It is driven by a shaft from below. The faster it turns, the faster it winds its blade up";
            case DEGLOVER -> "It is driven by a shaft from below. Up to 32 RPM, the faster it turns, the faster it works; past that it works no faster";
            default -> "It is driven by a shaft from below. The faster it turns, the faster it works";
        };
    }

    /**
     * The four carcass machines share one layout: a shaft up into the machine. The Guillotine's also has a lever beside
     * it, and shows the blade dropping when it is pulled.
     */
    public static void machine(SceneBuilder builder, SceneBuildingUtil util, com.avicagan.bloodandbones.machine.MachineKind kind, String header,
                               String what, ItemStack shows) {
        String id = kind.id;
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title(id, header);
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        BlockPos machine = util.grid().at(2, 2, 2);
        Selection machineSel = util.select().position(machine);
        Selection shaft = util.select().position(2, 1, 2);
        Selection kinetics = machineSel.add(shaft);
        scene.world().setKineticSpeed(kinetics, 0);
        scene.idle(5);
        scene.world().showSection(machineSel, Direction.DOWN);
        scene.idle(15);
        Vec3 top = util.vector().topOf(machine);
        scene.overlay().showText(70).attachKeyFrame().text(what).pointAt(top).placeNearTarget();
        scene.idle(80);
        scene.world().showSection(shaft, Direction.UP);
        scene.idle(10);
        scene.world().setKineticSpeed(kinetics, 32);
        scene.effects().indicateSuccess(machine);
        scene.overlay().showText(70).attachKeyFrame().colored(PonderPalette.GREEN)
                .text(speedLine(kind))
                .pointAt(util.vector().blockSurface(machine, Direction.DOWN)).placeNearTarget();
        scene.idle(80);
        if (kind == com.avicagan.bloodandbones.machine.MachineKind.GUILLOTINE) {
            guillotineDrop(scene, util, machine);
        }
        scene.overlay().showText(80).attachKeyFrame()
                .text("It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it")
                .pointAt(top).placeNearTarget();
        scene.idle(90);
        scene.overlay().showControls(top, Pointing.DOWN, 50).withItem(shows);
        scene.overlay().showText(80).attachKeyFrame()
                .text("What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper")
                .pointAt(top).placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(90).attachKeyFrame()
                .text("The filter on its top edge picks which parts it takes: an Attribute Filter set to a part, such as a hind leg, or a spawn egg for one kind of mob")
                .pointAt(util.vector().blockSurface(machine, Direction.UP).add(0, 0, -0.35)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    /** A lever beside the Guillotine: wound up it holds the blade, armed, and a pull on the lever drops it. */
    private static void guillotineDrop(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos machine) {
        BlockPos support = machine.east().below();
        BlockPos lever = machine.east();
        scene.world().setBlock(support, com.simibubi.create.AllBlocks.ANDESITE_CASING.getDefaultState(), false);
        scene.world().setBlock(lever, net.minecraft.world.level.block.Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(net.minecraft.world.level.block.LeverBlock.FACING, Direction.WEST), false);
        scene.world().showSection(util.select().fromTo(support, lever), Direction.DOWN);
        scene.world().modifyBlockEntity(machine, com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.class, be -> be.wind = 1.0F);
        scene.idle(15);
        scene.overlay().showText(90).attachKeyFrame().colored(PonderPalette.RED)
                .text("Wound up, it holds the blade at the top, armed. Only a redstone pulse drops it: a lever, a button or a clock beside it")
                .pointAt(util.vector().topOf(lever)).placeNearTarget();
        scene.idle(60);
        scene.overlay().showControls(util.vector().topOf(lever), Pointing.DOWN, 20).rightClick();
        scene.world().toggleRedstonePower(util.select().position(lever));
        scene.effects().indicateRedstone(lever);
        // the blade falls, a tick at a time, and lands (set here: a Ponder world never sees the redstone edge that drops it)
        for (int left = com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.DROP_TICKS; left >= 0; left--) {
            int falling = left;
            scene.world().modifyBlockEntity(machine, com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.class, be -> {
                be.falling = falling;
                be.wind = 0.0F;
            });
            scene.idle(1);
        }
        scene.idle(35);
        scene.world().toggleRedstonePower(util.select().position(lever));
        scene.idle(10);
    }

    public static void bleedingRack(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("bleeding_rack", "Draining Blood with the Bleeding Rack");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        BlockPos rack = util.grid().at(2, 1, 2);
        BlockPos hook = util.grid().at(2, 4, 2);
        scene.idle(5);
        scene.world().showSection(util.select().position(rack), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70).attachKeyFrame().text("The Bleeding Rack is a drip tray with a tank for catching blood")
                .pointAt(util.vector().topOf(rack)).placeNearTarget();
        scene.idle(80);
        scene.world().showSection(util.select().fromTo(2, 4, 2, 2, 5, 2), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(90).attachKeyFrame()
                .text("Hang a carcass on a Shackle Hook up to 8 blocks above it, and its blood drips into the tray")
                .pointAt(util.vector().centerOf(hook)).placeNearTarget();
        scene.idle(100);
        scene.overlay().showText(80).attachKeyFrame().colored(PonderPalette.GREEN)
                .text("An Encased Fan blowing across the body drains it up to four times faster")
                .pointAt(util.vector().centerOf(util.grid().at(2, 3, 2))).placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(80).attachKeyFrame()
                .text("Pipes can pull the blood from the rack's sides and bottom. When the rack is full, the carcass stops draining")
                .pointAt(util.vector().blockSurface(rack, Direction.EAST)).placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    public static void spitRoast(SceneBuilder builder, SceneBuildingUtil util, ItemStack piece) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("spit_roast", "Roasting on the Spit Roast");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        BlockPos fire = util.grid().at(2, 1, 2);
        BlockPos spit = util.grid().at(2, 2, 2);
        Selection kinetics = util.select().fromTo(1, 2, 2, 2, 2, 2);
        scene.world().setKineticSpeed(kinetics, 0);
        scene.idle(5);
        scene.world().showSection(util.select().position(fire), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(spit), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80).attachKeyFrame()
                .text("Set the Spit Roast over heat: a campfire, fire, lava or a Blaze Burner")
                .pointAt(util.vector().topOf(fire)).placeNearTarget();
        scene.idle(90);
        scene.world().showSection(util.select().position(1, 2, 2), Direction.EAST);
        scene.idle(10);
        scene.world().setKineticSpeed(kinetics, 16);
        scene.overlay().showText(70).attachKeyFrame().colored(PonderPalette.GREEN)
                .text("A shaft turns the spit, or a Hand Crank, slowly. It only roasts while it turns, and a fast shaft roasts up to eight times as fast")
                .pointAt(util.vector().centerOf(spit)).placeNearTarget();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(spit), Pointing.DOWN, 50).rightClick().withItem(piece);
        scene.overlay().showText(80).attachKeyFrame()
                .text("Right-click with a carcass piece to skewer it. It browns as it cooks")
                .pointAt(util.vector().topOf(spit)).placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(90).attachKeyFrame()
                .text("Take it off with an empty hand once cooked: it comes apart into cooked meat and bones. Leave it too long and it burns")
                .pointAt(util.vector().topOf(spit)).placeNearTarget();
        scene.idle(100);
        scene.overlay().showText(90).attachKeyFrame()
                .text("A whole carcass goes on too: right-click the spit with the Meat Hook while dragging one. It takes longer, and gives all its meat cooked")
                .pointAt(util.vector().topOf(spit)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    /** A pillar of Bloody Casing with a Butcher's Hook on each side, each hung with a body part. */
    public static void butcherHook(SceneBuilder builder, SceneBuildingUtil util, List<ItemStack> pieces) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("butcher_hook", "Hanging Meat on the Butcher's Hook");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        BlockPos top = util.grid().at(2, 2, 2);
        List<BlockPos> hooks = List.of(util.grid().at(2, 2, 1), util.grid().at(3, 2, 2), util.grid().at(2, 2, 3), util.grid().at(1, 2, 2));
        scene.idle(5);
        scene.world().showSection(util.select().fromTo(2, 1, 2, 2, 2, 2), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80).attachKeyFrame()
                .text("Bloody Casing: fill an Andesite Casing with 250 mB of blood from a Spout. It joins up like any casing")
                .pointAt(util.vector().topOf(top)).placeNearTarget();
        scene.idle(90);
        for (BlockPos hook : hooks) {
            scene.world().showSection(util.select().position(hook), Direction.DOWN);
            scene.idle(5);
        }
        scene.idle(10);
        scene.overlay().showText(70).attachKeyFrame()
                .text("A Butcher's Hook goes on the side of a solid block")
                .pointAt(util.vector().topOf(top)).placeNearTarget();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(hooks.get(0)), Pointing.DOWN, 50).rightClick().withItem(pieces.get(0));
        for (int i = 0; i < hooks.size(); i++) {
            ItemStack piece = pieces.get(i % pieces.size());
            scene.world().modifyBlockEntity(hooks.get(i), ButcherHookBlockEntity.class, be -> be.put(piece.copy()));
            scene.idle(5);
        }
        scene.overlay().showText(80).attachKeyFrame().colored(PonderPalette.GREEN)
                .text("Right-click with any body part to hang it up: a carcass piece, a severed limb, an organ, scraps or meat. A carcass piece keeps there, like a piece in a Specimen Jar")
                .pointAt(util.vector().topOf(top)).placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(90).attachKeyFrame()
                .text("Take it down with an empty hand. If the block behind it is broken, the hook falls and drops the piece")
                .pointAt(util.vector().topOf(top)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    /** A piece laid on the Butcher's Table and chopped with a Cleaver; then a Deployer doing the same. */
    public static void butcherTable(SceneBuilder builder, SceneBuildingUtil util, ItemStack piece, ItemStack cleaver, List<ItemStack> yields) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("butcher_table", "Chopping Pieces on the Butcher's Table");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        BlockPos table = util.grid().at(2, 1, 2);
        BlockPos deployer = util.grid().at(2, 3, 2);
        scene.idle(5);
        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(table), Pointing.DOWN, 40).rightClick().withItem(piece);
        scene.world().modifyBlockEntity(table, ButcherTableBlockEntity.class, be -> be.put(piece.copy()));
        scene.overlay().showText(70).attachKeyFrame()
                .text("Right-click the Butcher's Table with a carcass piece to lay it on the top")
                .pointAt(util.vector().topOf(table)).placeNearTarget();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(table), Pointing.DOWN, 40).rightClick().withItem(cleaver);
        scene.idle(10);
        chop(scene, util, table, yields);
        scene.overlay().showText(80).attachKeyFrame().colored(PonderPalette.RED)
                .text("Chop it with a Cleaver: it comes apart into meat, bone, offal and fat, spoiled as far as it had rotted. By hand you get about half")
                .pointAt(util.vector().topOf(table)).placeNearTarget();
        scene.idle(90);
        scene.world().showSection(util.select().position(deployer), Direction.DOWN);
        scene.world().modifyBlockEntityNBT(util.select().position(deployer), com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity.class,
                nbt -> nbt.put("HeldItem", cleaver.saveOptional(scene.world().getHolderLookupProvider())));
        scene.idle(15);
        scene.world().modifyBlockEntity(table, ButcherTableBlockEntity.class, be -> be.put(piece.copy()));
        scene.overlay().showText(80).attachKeyFrame()
                .text("A Deployer holding a Cleaver chops too, and gets all of it. A funnel or hopper can lay the pieces on the table")
                .pointAt(util.vector().centerOf(deployer)).placeNearTarget();
        scene.idle(30);
        scene.world().moveDeployer(deployer, 1, 20);
        scene.idle(20);
        chop(scene, util, table, yields);
        scene.world().moveDeployer(deployer, -1, 20);
        scene.idle(40);
        scene.overlay().showText(90).attachKeyFrame()
                .text("The filter on the edge of its top picks which pieces it takes. A body too heavy to carry can be dragged onto it and chopped where it lies")
                .pointAt(util.vector().blockSurface(table, Direction.NORTH)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }

    /** The piece on the table comes apart: it goes, and its yields pop up off the top. */
    private static void chop(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos table, List<ItemStack> yields) {
        scene.world().modifyBlockEntity(table, ButcherTableBlockEntity.class, ButcherTableBlockEntity::take);
        for (int i = 0; i < yields.size(); i++) {
            double side = (i - (yields.size() - 1) / 2.0) * 0.08;
            scene.world().createItemEntity(util.vector().topOf(table), util.vector().of(side, 0.2, side * 0.5), yields.get(i));
        }
    }
}
