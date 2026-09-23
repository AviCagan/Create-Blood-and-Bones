package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;

/**
 * Text that is not a plain name: Create's item descriptions (hold Shift on an item), JEI information pages
 * and goggle lines. In descriptions, _underscored_ words are highlighted the way Create highlights them.
 */
public class BBLang {
    public static void register() {
        // ---- tools
        item("meat_hook",
                "The butcher's first tool. Anything _killed_ with it is left behind as a whole _physics carcass_ instead of dropping loot.",
                "When R-Clicked on a Carcass", "_Hooks_ the limb you clicked and _drags_ the body behind you. Heavier animals slow you more. R-Click again to let go.",
                "When R-Clicked on a Shackle Hook", "Hangs the carcass you are dragging _by the neck_.",
                "When R-Clicked on a Chain Conveyor", "Hangs the carcass you are dragging on a _trolley_ that rides the chain.");
        item("cleaver",
                "A heavy blade for taking a carcass _apart_. Slow in a fight, but it goes through bone.",
                "When R-Clicked on a Limb", "_Cuts_ into the joint. Three cuts and the limb comes off as a piece of its own.",
                "When R-Clicked on a Loose Piece", "_Butchers_ it: three cuts and it falls apart into meat, bone and, from the body, offal and fat.");
        item("blood_steel_cleaver",
                "A cleaver of _blood steel_. Every chop goes _twice as deep_.",
                "When R-Clicked on a Carcass", "Takes a limb off, or butchers a piece, in _two_ chops instead of three.");
        item("flensing_knife",
                "A curved knife for taking the _hide_ off in one piece.",
                "When R-Clicked on a Carcass", "_Skins_ it: four strokes take the whole hide off, and a sheep's wool with it. The carcass shows _bare meat_ afterwards.",
                "Before Butchering", "Skin _first_: a piece butchered with its hide on loses the hide. A rotting hide is poorer; a rotten one is gone.");
        item("carcass_piece",
                "A piece of carcass light enough to _carry_: a head, a leg, or a whole small animal.",
                "When Shift-R-Clicked with an Empty Hand", "Picks a _loose_ piece up off the ground. Pieces still attached have to be cut off first.",
                "When R-Clicked on the Ground", "Puts it back down as a _body_ again.",
                "On a Spit Roast or in a Specimen Jar", "It can be _roasted_, or kept on _show_.");

        // ---- hanging, bleeding
        block("shackle_hook",
                "A hook on a chain for _hanging_ carcasses. Mount it under a ceiling or on a wall.",
                "When R-Clicked with a Meat Hook", "Hangs the carcass you are dragging by the _neck_, belly facing out, swinging. Click again to let it down.",
                "Hanging", "A hung carcass never _settles_, can be worked from every side, and _bleeds_ into a Bleeding Rack below.");
        block("bleeding_rack",
                "A copper _drip tray_ with a tank for catching _blood_.",
                "Under a Hanging Carcass", "Catches the blood of a carcass hung up to _8 blocks_ above it.",
                "Under a Lying Carcass", "A carcass lying still _on_ the rack drains too, at half the speed.",
                "With Fans", "An _Encased Fan_ blowing across the body drains it up to _four times_ faster.",
                "With Pipes", "Pipes pull blood from its _sides and bottom_. When it is full, the carcass stops draining. A _bled_ carcass rots slower.");

        // ---- machines
        block("mangler",
                "Teeth and rollers that tear a carcass _apart_ and grind it down. Driven by a _shaft from below_.",
                "With a Carcass Over It", "Tears the _limbs_ off, then grinds every piece into meat, bone, offal and fat.",
                "Output", "Keeps what it makes _inside_: take it with an empty hand, or pull it out with a _funnel_, chute or hopper. When full, it stops.");
        block("guillotine",
                "A heavy blade on two posts. Driven by a _shaft from below_.",
                "With a Carcass Over It", "Takes the nearest _limb_ off in one stroke. It never takes the head.");
        block("beheader",
                "A spinning saw at neck height. Driven by a _shaft from below_.",
                "With a Carcass Over It", "Takes the _head_ off in one stroke.",
                "Skulls", "Zombies, skeletons, creepers and piglins sometimes leave their _skull_ whole. A wither skeleton's rarely survives.");
        block("deglover",
                "Spiked rollers that strip the _hide_ off a carcass. Driven by a _shaft from below_.",
                "With a Carcass Over It", "Skins it a stroke at a time: _hide_ and, from a sheep, _wool_ into its output.");

        // ---- materials
        item("blood_steel_ingot",
                "Iron _quenched in blood_. Fill an iron ingot with _250 mB_ of blood in a Spout.");
        item("blood_diamond",
                "A diamond steeped in _soul blood_. Fill a diamond with _1000 mB_ of soul blood in a Spout.");

        // ---- cooking and display
        block("spit_roast",
                "Posts and a turning _spit_ for roasting carcass pieces over a fire.",
                "When R-Clicked with a Carcass Piece", "_Skewers_ it. It roasts only while the spit _turns_ and there is _heat_ below: a campfire, fire, lava or a Blaze Burner. Hotter and faster cooks quicker.",
                "When R-Clicked with an Empty Hand", "Takes it off: raw, it comes back as it was; _browned_, it comes apart into cooked meat and bones. Left twice as long, it _burns_ to charcoal.");
        block("specimen_jar",
                "A jar of cloudy _preserving fluid_ for keeping a carcass piece on show.",
                "When R-Clicked with a Carcass Piece", "Puts it in, to drift in the fluid. R-Click with an _empty hand_ to take it out.");

        // ---- goggles
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.carcass_machine.output", "%1$s items waiting");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.roasting", "Roasting: %1$s%%");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.cooked", "Cooked: take it off");
        BloodAndBones.REGISTRATE.addRawLang("gamerule.bloodandbonesBloodless", "Bloodless mode for everyone");
        BloodAndBones.REGISTRATE.addRawLang("gamerule.bloodandbonesBloodless.description",
                "Hides blood, gore and wet textures for every player, whatever their own setting. Carcasses and machines work the same.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.burnt", "Burnt to a crisp");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.no_heat", "Needs a fire below");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.carcass_piece.named", "%s %s");

        // ---- advancements
        advancement("butchery", "Create: Blood & Bones", "Make a Meat Hook. Whatever it kills stays whole");
        advancement("cleaver", "Clean Cuts", "Make a Cleaver to take a carcass apart at the joints");
        advancement("offal", "Guts and Glory", "Get your hands on some offal");
        advancement("skinned", "Skin in the Game", "Take a hide off with the Flensing Knife");
        advancement("hanging", "Hang in There", "Make a Shackle Hook to hang carcasses by the neck");
        advancement("blood", "Bloodletting", "Get a bucket of blood");
        advancement("machine", "Industrial Slaughter", "Build a butchery machine");
        advancement("blood_steel", "Tempered in Blood", "Quench iron in blood with a Spout");
        advancement("soul_blood", "Soul Food", "Give blood a soul");
        advancement("blood_diamond", "Priceless", "Steep a diamond in soul blood");
        advancement("spit_roast", "Low and Slow", "Build a Spit Roast");
        advancement("specimen", "Curiosities", "Make a Specimen Jar to keep a piece of something on show");

        // ---- Ponder scenes (text_N in the order each scene shows its text)
        ponder("mangler", "Grinding Carcasses with the Mangler", "The Mangler tears the limbs off a carcass, then grinds every piece into meat, bone, offal and fat", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper");
        ponder("guillotine", "Taking Limbs Off with the Guillotine", "The Guillotine takes the nearest limb off a carcass in one stroke. It never takes the head", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper");
        ponder("beheader", "Taking Heads with the Beheader", "The Beheader takes heads off. Zombies, skeletons, creepers and piglins sometimes leave their skull whole", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper");
        ponder("deglover", "Skinning with the Deglover", "The Deglover strips the hide off a carcass, and a sheep's wool with it", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper");
        ponder("bleeding_rack", "Draining Blood with the Bleeding Rack", "The Bleeding Rack is a drip tray with a tank for catching blood", "Hang a carcass on a Shackle Hook up to 8 blocks above it, and its blood drips into the tray", "An Encased Fan blowing across the body drains it up to four times faster", "Pipes can pull the blood from the rack's sides and bottom. When the rack is full, the carcass stops draining");
        ponder("spit_roast", "Roasting on the Spit Roast", "Set the Spit Roast over heat: a campfire, fire, lava or a Blaze Burner", "A shaft turns the spit. It only roasts while it turns", "Right-click with a carcass piece to skewer it. It browns as it cooks", "Take it off with an empty hand once cooked: it comes apart into cooked meat and bones. Leave it too long and it burns");

        // ---- JEI butchery page
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.category.butchery", "Butchery");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.butchery.skinned", "Skinned");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.butchery.butchered", "Butchered, whole animal");

        // ---- JEI information pages
        jei("meat_hook",
                "The Meat Hook is where a carcass comes from. Kill an animal or monster with it and the whole body stays behind as a ragdoll you can hook, drag, hang, cut up and process, instead of its loot.",
                "Right-click a limb to hook it and drag the body behind you. Heavier animals slow you down more. Right-click again to let go. What the mob was carrying (saddles, chests, armour, held items) still drops.");
        jei("shackle_hook",
                "Mount the Shackle Hook under a ceiling or on a wall. Drag a carcass close and click the hook with the Meat Hook to hang it by the neck.",
                "To send a carcass down a line, drag it under a Create chain conveyor and right-click the chain with the Meat Hook: it rides the chain on a trolley, round wheels and along strands, and stops at frogports addressed for it.");
        jei("cleaver",
                "The Cleaver takes a carcass apart. Right-click a limb three times to cut through the joint; it comes free as a piece of its own that can be hooked, carried, hung or butchered.",
                "Three more cuts on a loose piece butcher it into meat and bone. The body is butchered last, once nothing hangs off it; it gives the offal and fat.");
        jei("flensing_knife",
                "The Flensing Knife takes the hide off. Four strokes on any part of a carcass and the whole hide drops, with a sheep's wool, a mooshroom's mushrooms or a rabbit's pelt as it applies.",
                "Skin before you butcher: a piece cut down with its hide still on loses it. Rot spoils hides.");
        jei("butchery",
                "Bigger pieces give more: meat and bone come from every part by its size, offal and fat from the body.",
                "Fresh meat is whole. Half-rotten meat gives half. Badly rotten meat turns to rotten flesh, and a rotten carcass has no hide worth taking. Cold slows rot, ice stops it, and a bled carcass keeps longer.");
        jei("bleeding_rack",
                "Put a Bleeding Rack under a Shackle Hook (up to 8 blocks below) and hang a carcass: its blood drips into the tray. A carcass lying still on the rack drains too, more slowly.",
                "A cow holds about a bucket. Encased Fans blowing across the body drain it up to four times faster. Pipe the blood out of the sides or bottom.");
        jei("machines",
                "The Mangler, Guillotine, Beheader and Deglover work any carcass lying on or hanging over them. Each takes a shaft from below; the faster it turns, the faster it works. Set them flush in a floor so a body lies across them.",
                "Guillotine: limbs off. Beheader: heads off, sometimes a skull. Deglover: hides off. Mangler: everything, down to meat and bone. Take their output with a funnel or an empty hand.");
        jei("soul_blood",
                "Soul Blood is blood with a soul in it. Mix blood, soul sand and a little liquid experience over a superheated Blaze Burner, or ferment blood with nether wart and soul soil under a Basin Lid.");
    }

    private static void advancement(String id, String title, String description) {
        BloodAndBones.REGISTRATE.addRawLang("advancements.bloodandbones." + id + ".title", title);
        BloodAndBones.REGISTRATE.addRawLang("advancements.bloodandbones." + id + ".description", description);
    }

    private static void ponder(String scene, String header, String... texts) {
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.ponder." + scene + ".header", header);
        for (int i = 0; i < texts.length; i++) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.ponder." + scene + ".text_" + (i + 1), texts[i]);
        }
    }

    private static void jei(String id, String... pages) {
        for (int i = 0; i < pages.length; i++) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei." + id + "." + (i + 1), pages[i]);
        }
    }

    private static void block(String id, String summary, String... pairs) {
        describe("block." + BloodAndBones.MOD_ID + "." + id + ".tooltip", id, summary, pairs);
    }

    private static void item(String id, String summary, String... pairs) {
        describe("item." + BloodAndBones.MOD_ID + "." + id + ".tooltip", id, summary, pairs);
    }

    /** Create's description keys: a summary line, then condition and behaviour pairs. */
    private static void describe(String base, String id, String summary, String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("Description of " + id + " needs condition and behaviour pairs");
        }
        BloodAndBones.REGISTRATE.addRawLang(base, id.replace('_', ' ').toUpperCase());
        BloodAndBones.REGISTRATE.addRawLang(base + ".summary", summary);
        for (int i = 0; i < pairs.length / 2; i++) {
            BloodAndBones.REGISTRATE.addRawLang(base + ".condition" + (i + 1), pairs[2 * i]);
            BloodAndBones.REGISTRATE.addRawLang(base + ".behaviour" + (i + 1), pairs[2 * i + 1]);
        }
    }
}
