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

/**
 * Ponder scenes. Schematics are in assets/bloodandbones/ponder/, 5 wide on a snow plate. Ponder worlds do
 * not run kinetics, so every turning block is given its speed here. The text of each scene is in the lang
 * file under bloodandbones.ponder.&lt;scene&gt;.header and .text_N, in showText order.
 */
public final class BBScenes {
    private BBScenes() {
    }

    /** The four carcass machines share one layout: a shaft up into the machine. */
    public static void machine(SceneBuilder builder, SceneBuildingUtil util, String id, String header, String what, ItemStack shows) {
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
                .text("It is driven by a shaft from below. The faster it turns, the faster it works")
                .pointAt(util.vector().blockSurface(machine, Direction.DOWN)).placeNearTarget();
        scene.idle(80);
        scene.overlay().showText(80).attachKeyFrame()
                .text("It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it")
                .pointAt(top).placeNearTarget();
        scene.idle(90);
        scene.overlay().showControls(top, Pointing.DOWN, 50).withItem(shows);
        scene.overlay().showText(80).attachKeyFrame()
                .text("What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper")
                .pointAt(top).placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
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
                .text("A shaft turns the spit. It only roasts while it turns")
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
        scene.markAsFinished();
    }

    /** A pillar of Bloody Casing with a Butcher's Hook on each side, each hung with a piece. */
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
        scene.overlay().showControls(util.vector().topOf(top), Pointing.DOWN, 50).rightClick().withItem(pieces.get(0));
        for (int i = 0; i < hooks.size(); i++) {
            ItemStack piece = pieces.get(i % pieces.size());
            scene.world().modifyBlockEntity(hooks.get(i), ButcherHookBlockEntity.class, be -> be.put(piece.copy()));
            scene.idle(5);
        }
        scene.overlay().showText(80).attachKeyFrame().colored(PonderPalette.GREEN)
                .text("Right-click with a carcass piece to hang it up. It keeps there, like a piece in a Specimen Jar")
                .pointAt(util.vector().topOf(top)).placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(90).attachKeyFrame()
                .text("Take it down with an empty hand. If the block behind it is broken, the hook falls and drops the piece")
                .pointAt(util.vector().topOf(top)).placeNearTarget();
        scene.idle(100);
        scene.markAsFinished();
    }
}
