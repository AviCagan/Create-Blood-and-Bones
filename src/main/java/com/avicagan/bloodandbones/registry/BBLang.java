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

        item("meat_hook",
                "PH: Kill an animal with this and it leaves a whole carcass instead of loot. Right-click a carcass to drag it, right-click again to let go.",
                "PH: Dragging slows you down. Heavier animals slow you more.",
                "PH: Where you hook matters: the limb you grab is the one that gets pulled.");
    }

    /** Create's description keys: a summary line plus any number of behaviour/condition pairs. */
    private static void item(String id, String summary, String... notes) {
        String base = "item." + BloodAndBones.MOD_ID + "." + id + ".tooltip";
        BloodAndBones.REGISTRATE.addRawLang(base, id.replace('_', ' ').toUpperCase());
        BloodAndBones.REGISTRATE.addRawLang(base + ".summary", summary);
        for (int i = 0; i < notes.length; i++) {
            BloodAndBones.REGISTRATE.addRawLang(base + ".condition" + (i + 1), "PH: Note " + (i + 1));
            BloodAndBones.REGISTRATE.addRawLang(base + ".behaviour" + (i + 1), notes[i]);
        }
    }
}
