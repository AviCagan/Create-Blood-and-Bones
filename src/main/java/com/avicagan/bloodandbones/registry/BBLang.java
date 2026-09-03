package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;

/**
 * Tooltip text shown by Create's item description system (hold Shift on an item).
 * All wording is placeholder until the real descriptions are written.
 */
public class BBLang {
    public static void register() {
        // JEI information pages
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.meat_hook.1",
                "PH: The Meat Hook is how carcasses enter the pipeline. Kill an animal with it and the whole body stays behind as a physics carcass instead of dropping loot.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.meat_hook.2",
                "PH: Right-click a limb to hook it and drag the carcass behind you. Heavier animals slow you down more. Right-click again to let go.");

        block("shackle_hook",
                "PH: Hangs a carcass. Drag one up to it and click the hook with the Meat Hook to hang it by the limb you are holding.",
                "PH: A hanging carcass keeps swinging and can be worked on from all sides.",
                "PH: Click the hook again, or with an empty hand, to let it down.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.shackle_hook.1",
                "PH: Mount the Shackle Hook under a ceiling or on a wall. Drag a carcass close and click the hook with the Meat Hook to hang it.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.shackle_hook.2",
                "PH: Hanging carcasses stay ragdolls, so they swing, and will be routable along chain conveyors.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.cleaver.1",
                "PH: The Cleaver takes a carcass apart. Right-click a limb three times to cut through the joint; the limb comes free as its own piece.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.cleaver.2",
                "PH: Severed limbs can still be hooked and dragged on their own. The body cannot be cut through, only its limbs.");
        item("cleaver",
                "PH: Right-click a limb of a carcass to cut into it. Three cuts sever the joint and the limb comes off.",
                "PH: The body itself cannot be cut through.",
                "PH: A severed limb is its own piece: hook it, drag it, hang it.");
        item("meat_hook",
                "PH: Kill an animal with this and it leaves a whole carcass instead of loot. Right-click a carcass to drag it, right-click again to let go.",
                "PH: Dragging slows you down. Heavier animals slow you more.",
                "PH: Where you hook matters: the limb you grab is the one that gets pulled.");
    }

    private static void block(String id, String summary, String... notes) {
        describe("block." + BloodAndBones.MOD_ID + "." + id + ".tooltip", id, summary, notes);
    }

    /** Create's description keys: a summary line plus any number of behaviour/condition pairs. */
    private static void item(String id, String summary, String... notes) {
        describe("item." + BloodAndBones.MOD_ID + "." + id + ".tooltip", id, summary, notes);
    }

    private static void describe(String base, String id, String summary, String... notes) {
        BloodAndBones.REGISTRATE.addRawLang(base, id.replace('_', ' ').toUpperCase());
        BloodAndBones.REGISTRATE.addRawLang(base + ".summary", summary);
        for (int i = 0; i < notes.length; i++) {
            BloodAndBones.REGISTRATE.addRawLang(base + ".condition" + (i + 1), "PH: Note " + (i + 1));
            BloodAndBones.REGISTRATE.addRawLang(base + ".behaviour" + (i + 1), notes[i]);
        }
    }
}
