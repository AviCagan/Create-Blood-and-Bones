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
                "Output", "Keeps what it makes _inside_: take it with an empty hand, or pull it out with a _funnel_, chute or hopper. When full, it stops.",
                "Filter Slot", "On the top edge. A _spawn egg_ or a _carcass piece_ sets it to one kind of mob; a Create _filter_ works too. Empty, it takes anything.");
        block("guillotine",
                "A heavy blade on two posts. Driven by a _shaft from below_.",
                "With a Carcass Over It", "Takes the nearest _limb_ off in one stroke. It never takes the head.",
                "Filter Slot", "On the top edge. A _spawn egg_ or a _carcass piece_ sets it to one kind of mob; a Create _filter_ works too. Empty, it takes anything.");
        block("beheader",
                "A spinning saw at neck height. Driven by a _shaft from below_.",
                "With a Carcass Over It", "Takes the _head_ off in one stroke.",
                "Skulls", "Zombies, skeletons, creepers and piglins sometimes leave their _skull_ whole. A wither skeleton's rarely survives.",
                "Filter Slot", "On the top edge. A _spawn egg_ or a _carcass piece_ sets it to one kind of mob; a Create _filter_ works too. Empty, it takes anything.");
        block("deglover",
                "Spiked rollers that strip the _hide_ off a carcass. Driven by a _shaft from below_.",
                "With a Carcass Over It", "Skins it a stroke at a time: _hide_ and, from a sheep, _wool_ into its output.",
                "Filter Slot", "On the top edge. A _spawn egg_ or a _carcass piece_ sets it to one kind of mob; a Create _filter_ works too. Empty, it takes anything.");

        // ---- materials
        item("blood_steel_ingot",
                "Iron _quenched in blood_. Fill an iron ingot with _250 mB_ of blood in a Spout.");
        item("blood_diamond",
                "A diamond steeped in _blood_ and _experience_, one after the other, as a sequenced assembly.",
                "When Made", "Put a diamond through a _Spout_ of _1000 mB_ of blood, then a Spout of _1000 mB_ of Enchantment Industry's liquid experience. Any mod's blood will do.");
        item("congealed_blood",
                "Blood left to _set_ under a lid: a dark, wobbling slab. The first step of the _soul blood_ line.",
                "When Made", "A Diesel Generators _Basin Lid_ on a Basin of blood sets _250 mB_ of it into one.",
                "Next", "_Haunt_ it: an _Encased Fan_ blowing through _soul fire_ turns it into a Soul Clot.");
        item("soul_clot",
                "Congealed blood with a _soul_ caught in it, faintly glowing.",
                "When Made", "Blow an _Encased Fan_ through _soul fire_ over Congealed Blood.",
                "Next", "A _Mechanical Mixer_ over a _superheated_ Basin melts it back into _200 mB_ of soul blood.");

        // ---- cooking and display
        block("spit_roast",
                "Posts and a turning _spit_ for roasting carcass pieces over a fire.",
                "When R-Clicked with a Carcass Piece", "_Skewers_ it. It roasts only while the spit _turns_ and there is _heat_ below: a campfire, fire, lava or a Blaze Burner. Hotter and faster cooks quicker.",
                "When R-Clicked with an Empty Hand", "Takes it off: raw, it comes back as it was; _browned_, it comes apart into cooked meat and bones. Left twice as long, it _burns_ to charcoal.");
        block("bloody_casing",
                "Andesite casing _smeared with blood_, for a slaughterhouse that looks the part. Joins up with its neighbours like any casing.",
                "When Made", "_Fill_ an Andesite Casing with 250 mB of blood using a _Spout_.");
        block("butcher_table",
                "A steel table for cutting up carcass pieces by hand.",
                "When R-Clicked with a Carcass Piece", "Lays it on the table.",
                "When R-Clicked with a Cleaver", "_Chops_ the piece apart into meat, bone, offal and fat, spoiled as far as it had rotted.",
                "When R-Clicked with an Empty Hand", "Takes the piece back.");
        block("gut_chain",
                "A string of _guts_, hung like a chain. Squelches underfoot and in the hand.",
                "When Made", "Three pieces of _offal_ in a column make three.",
                "When R-Clicked on a Chain Conveyor", "_Hangs_ a link from the chain, to ride it round and swing. R-Click the hanging string to add links, up to _eight_. _Hit_ it to take it down.");
        block("bloody_brass_casing",
                "Brass casing _splashed with blood_. Joins up with its neighbours like any casing.",
                "When Made", "_Fill_ a Brass Casing with 250 mB of blood using a _Spout_.");
        block("bloody_copper_casing",
                "Copper casing _splashed with blood_. Joins up with its neighbours like any casing.",
                "When Made", "_Fill_ a Copper Casing with 250 mB of blood using a _Spout_.");
        block("bloody_railway_casing",
                "Create's train casing _splashed with blood_. Joins up with its neighbours as Create's does.",
                "When Made", "_Fill_ a Train Casing with 250 mB of blood using a _Spout_.");
        for (String[] stone : new String[][]{{"bloody_cut_calcite", "Cut Calcite"}, {"bloody_polished_cut_calcite", "Polished Cut Calcite"},
                {"bloody_cut_calcite_bricks", "Cut Calcite Bricks"}, {"bloody_small_calcite_bricks", "Small Calcite Bricks"}}) {
            block(stone[0],
                    "Create's " + stone[1] + ", white as a slaughterhouse wall, _splashed with blood_ that has run into the joints.",
                    "When Made", "_Fill_ Create's " + stone[1] + " with 100 mB of blood using a _Spout_. A _stonecutter_ turns the stained cut calcite into the others, and any of them into stairs and slabs.");
        }
        block("steel_table",
                "A cold steel _morgue table_. Tables side by side _join into one run_, with legs only where it ends or turns.",
                "When R-Clicked on the Top", "Lays the held item on it: _one item_, any item. A carcass piece lies on its back.",
                "When R-Clicked with an Empty Hand", "Takes it back. A _funnel_ or _hopper_ can lay things on it and take them off.");
        block("steel_rack",
                "Cold steel _shelves_ to show parts on: two shelves, _two places_ on each.",
                "When R-Clicked on the Front", "Puts the held item on the place you are _looking at_, one item a place.",
                "When R-Clicked with an Empty Hand", "Takes back the thing you are looking at. Funnels and hoppers fill it from the _lower left_.");
        block("ribcage_arch",
                "A segment of _giant rib_, wet and bloody at the joints. Stacked, ribs rise straight; the top one _bends in_; ribs hung in the air beside it run level, the _crown_ of the arch.",
                "When Placed", "Faces you, so a stack built from inside the arch bends towards you. Placed against another rib, it _lines up_ with it.");
        bloodless("block.bloodandbones.ribcage_arch.tooltip.summary",
                "A segment of _giant rib_, bleached clean. Stacked, ribs rise straight; the top one _bends in_; ribs hung in the air beside it run level, the _crown_ of the arch.");
        block("bone_pile",
                "Bones _heaped in layers_, as snow lies. A thin scatter can be walked through.",
                "When Used on a Bone Pile", "Adds a _layer_, up to a full block.",
                "When Broken", "Drops _two bones_ a layer.");
        block("butcher_hook",
                "A hook for a wall, to hang _any body part_ on for show: a carcass piece, a severed limb, an organ, scraps, meat or a head.",
                "When R-Clicked with a Body Part", "Hangs it on the hook. A carcass piece _keeps_ there. A fresh piece, a severed part or raw meat _drips_ for a while. R-Click with an _empty hand_ to take it down.");
        block("specimen_jar",
                "A jar of cloudy _preserving fluid_ for keeping _one item_ on show, any item.",
                "When R-Clicked with an Item", "Puts it in, to drift in the fluid; a carcass piece pickles pale. R-Click with an _empty hand_ to take it out.");

        // ---- the body
        block("surgery_table",
                "A padded table with straps. What it does depends on its _attachment_: a _Surgical Rig_ to operate (have a limb _off_, or a new one _on_; nothing can go wrong), an _Assembly Frame_ to build minions.",
                "When R-Clicked with a Blade, Implant or Limb", "Lays it on the table: a _Cleaver_ takes a limb off (a player's only with a _surgeon minion_ at the table), a _prosthetic_ or a _severed limb_ goes where one is missing.",
                "When R-Clicked with an Empty Hand", "You _lie down_ on it and choose which part to operate on. _Sneak_ to get up.",
                "When Sneak-R-Clicked with an Empty Hand", "Takes back what lies on the table.",
                "With Someone Else on It", "R-Click with an _empty hand_ to operate on them: another player, or a _mob_ you led onto it on a lead. What comes out is yours.",
                "With a Carcass Piece on It", "R-Click with a _Cleaver_ to take its _organs_ out, one a cut: a body's heart, lungs, stomach and the _special organ_ its mob has (a sac, a gland, a core), a head's eyes. With nothing laid on it, the Cleaver works on a _carcass lying over it_ that is too heavy to carry.",
                "Building a Minion", "With an _Assembly Frame_ fitted, lay a carcass _torso_ on it (or R-Click with an empty hand to take a whole carcass lying on it), then R-Click with carcass _heads_, _legs_, _arms_ and _tails_ of any mob to stitch them on. A _Cleaver_ takes the last back off; a bucket of _blood_ wakes it.");
        bloodless("block.bloodandbones.surgery_table.tooltip.behaviour5",
                "R-Click with a _Cleaver_ to take its _cores_ out, one a cut: a body's pump, bellows, hopper and the _special core_ its mob has, a head's lenses. With nothing laid on it, the Cleaver works on a _body lying over it_ that is too heavy to carry.");
        item("peg_leg",
                "A _crude prosthetic_ leg of iron, leather and bone. Needs nothing to run.",
                "When Fitted", "The leg works as it did: you can _sprint_ again. Nothing more.");
        item("hook_hand",
                "A _crude prosthetic_ arm: an iron hook on a leather cuff. Needs nothing to run.",
                "When Fitted", "The arm works as it did: the _off-hand_ and full-speed swings come back. Nothing more.");
        item("glass_eye",
                "A _crude prosthetic_ eye of glass and iron. Needs nothing to run.",
                "When Fitted", "You _see_ as you did. Nothing more.");
        item("crude_heart",
                "A _crude prosthetic_ heart: a leather pump on an iron frame. Needs nothing to run.",
                "When Fitted", "It beats like your own. A heart is only ever _swapped_, never taken out, so keep one of these by the table.");
        item("crude_lungs",
                "_Crude prosthetic_ lungs: two leather bags on a bone frame. Need nothing to run.",
                "When Fitted", "You breathe, and _sprint_, as you did.");
        item("crude_stomach",
                "A _crude prosthetic_ stomach: a leather sack in an iron cage. Needs nothing to run.",
                "When Fitted", "You can _eat_ as you did.");
        item("severed_arm",
                "Somebody's _arm_, taken off on a Surgery Table.",
                "On a Surgery Table", "Goes back on where an arm is _missing_, anyone's, either side. It is flesh again.");
        item("severed_leg",
                "Somebody's _leg_, taken off on a Surgery Table.",
                "On a Surgery Table", "Goes back on where a leg is _missing_, anyone's, either side. It is flesh again.");
        item("flesh_arm",
                "An _organic_ arm of meat and sinew, stitched on. Runs on _blood_ from a worn Fluid Backtank.",
                "While It Has Blood", "Blocks break _30%_ faster and it hits _harder_. When the tank runs dry it hangs _dead_, as good as no arm.",
                "Necrosis", "Every swing _rots_ it a little. Blood in your backtank clears the rot as you go; fully rotted, it gives no bonus until it has been _perfused_.");
        item("sinew_leg",
                "An _organic_ leg, all muscle. Runs on _blood_ from a worn Fluid Backtank.",
                "While It Has Blood", "Walks a little faster and jumps _higher_. When the tank runs dry it is _dead weight_.",
                "Necrosis", "Every stretch you _run_ rots it a little. Blood in your backtank clears the rot as you go; fully rotted, it gives no bonus.");
        item("hydraulic_arm",
                "A _cybernetic_ arm of brass and blood steel. Runs on _soul blood_ from a worn Fluid Backtank.",
                "While It Has Soul Blood", "Blocks break _80%_ faster, it hits _much_ harder, and it reaches a block further. Dry, it stops.",
                "Brass Chassis", "Takes _2 arm modules_, fitted at a Surgery Table with no cutting: lay the module on the table.");
        item("piston_leg",
                "A _cybernetic_ leg on a piston. Runs on _soul blood_ from a worn Fluid Backtank.",
                "While It Has Soul Blood", "Walks faster, jumps _far_ higher and lands soft. Dry, it stops.",
                "Brass Chassis", "Takes _2 leg modules_, fitted at a Surgery Table with no cutting: lay the module on the table.");
        item("vent_arm",
                "A _cybernetic_ arm that ends in a nozzle. Runs on _whatever is in the tank_, and sprays it.",
                "When Use Is Held with an Empty Hand", "Sprays the tank ahead of you: _lava_ or fuel burns, _water_ puts fires out, _liquid experience_ gives experience, _milk_ clears effects, anything else spills.");
        item("port_arm",
                "A _cybernetic_ arm with a pipe coupling for a hand. Needs nothing to run.",
                "Crouching Next to a Backtank Port", "Plugs your worn tank into the _pipes_: pump into the port to fill it, out of it to empty it.");
        item("optic_eye",
                "A _cybernetic_ eye with a red lens. Runs on _soul blood_.",
                "While It Has Soul Blood", "You see in the _dark_. Dry, it sees nothing.",
                "Brass Chassis", "Takes _1 eye module_, fitted at a Surgery Table with no cutting: lay the module on the table.");
        item("pump_heart",
                "A _cybernetic_ heart, a brass pump. Runs on _soul blood_.",
                "While It Has Soul Blood", "It keeps _healing_ you. Dry, it barely beats: you are _weak and slow_ until it has soul blood again.");
        item("bellows_lungs",
                "_Cybernetic_ lungs, a pair of bellows. Run on _soul blood_.",
                "While They Have Soul Blood", "You breathe _underwater_. Dry, you are too winded to sprint.");
        item("furnace_stomach",
                "An _organic_ stomach with a fire in it. Runs on _blood_.",
                "While It Has Blood", "Nothing you eat makes you _sick_: no hunger, no poison. Dry, you cannot eat (but you do not starve).",
                "Necrosis", "Every meal rots it a little. Blood in your backtank clears the rot as you go.");
        item("eye",
                "Somebody's _eye_.",
                "On a Surgery Table", "Goes back in where an eye is _missing_. An eye gone closes in your _view_; with none you see only a few blocks.");
        item("heart",
                "Somebody's _heart_. It is still warm.",
                "On a Surgery Table", "_Swaps_ in for a heart. A heart is never taken out alone; one that stops (a dry Pump Heart) leaves you _weak and slow_, but alive.");
        item("lungs",
                "Somebody's _lungs_.",
                "On a Surgery Table", "Go back in where lungs are _missing_. Without working lungs you cannot sprint.");
        item("stomach",
                "Somebody's _stomach_.",
                "On a Surgery Table", "Goes back in where a stomach is _missing_. Without a working stomach you cannot eat, though you never starve.");
        // bloodless mode: organic implants are plating and cable, not grafted flesh, and they wear rather than rot
        bloodless("item.bloodandbones.flesh_arm", "Plated Arm");
        bloodless("item.bloodandbones.flesh_arm.tooltip.summary", "A _plated_ arm of panels and cable, riveted on. Runs on _essence_ from a worn Fluid Backtank.");
        bloodless("item.bloodandbones.flesh_arm.tooltip.behaviour2", "Every swing _wears_ it a little. Essence in your backtank clears the wear as you go; fully worn, it gives no bonus until it has been _flushed_.");
        bloodless("item.bloodandbones.sinew_leg", "Cabled Leg");
        bloodless("item.bloodandbones.sinew_leg.tooltip.summary", "A _plated_ leg, all cable. Runs on _essence_ from a worn Fluid Backtank.");
        bloodless("item.bloodandbones.sinew_leg.tooltip.behaviour2", "Every stretch you _run_ wears it a little. Essence in your backtank clears the wear as you go; fully worn, it gives no bonus.");
        bloodless("item.bloodandbones.furnace_stomach", "Furnace Hopper");
        bloodless("item.bloodandbones.furnace_stomach.tooltip.summary", "A _plated_ hopper with a fire in it. Runs on _essence_.");
        bloodless("item.bloodandbones.furnace_stomach.tooltip.behaviour2", "Every meal wears it a little. Essence in your backtank clears the wear as you go.");
        for (String limb : new String[]{"severed_arm", "severed_leg"}) {
            bloodless("item.bloodandbones." + limb + ".tooltip.behaviour1", "Goes back on where " + ("severed_arm".equals(limb) ? "an arm" : "a leg")
                    + " is _missing_, anyone's, either side. It works again.");
        }
        for (String piece : new String[]{"Helmet", "Chestplate", "Leggings", "Boots"}) {
            bloodless("item.bloodandbones.carcass_" + piece.toLowerCase(java.util.Locale.ROOT), "Plated " + piece);
        }
        bloodless("bloodandbones.set_bonus.flesh", "Plated set: you mend from what you hit, and wear half as fast");
        // bloodless mode calls organs what they would be in a machine (docs/PARTS-AND-TRAITS.md section 7.10), here as in armour's text
        String[][] cores = {
                {"eye", "Lens", "Somebody's _lens_.", "Goes back in where a lens is _missing_. A lens gone closes in your _view_; with none you see only a few blocks."},
                {"heart", "Pump", "Somebody's _pump_.", "_Swaps_ in for a pump. A pump is never taken out alone; one that stops (a dry Pump Heart) leaves you _weak and slow_, but alive."},
                {"lungs", "Bellows", "Somebody's _bellows_.", "Go back in where bellows are _missing_. Without working bellows you cannot sprint."},
                {"stomach", "Hopper", "Somebody's _hopper_.", "Goes back in where a hopper is _missing_. Without a working hopper you cannot eat, though you never starve."}};
        for (String[] core : cores) {
            bloodless("item.bloodandbones." + core[0], core[1]);
            bloodless("item.bloodandbones." + core[0] + ".of", "%s's " + core[1]);
            bloodless("item.bloodandbones." + core[0] + ".tooltip.summary", core[2]);
            bloodless("item.bloodandbones." + core[0] + ".tooltip.behaviour1", core[3]);
        }
        block("backtank_port",
                "A _port_ for a worn Fluid Backtank, like a pump with a direction. Pipes connect to its _nozzle_.",
                "With a Port Arm", "_Crouch_ next to it with a _Port Arm_ and a backtank on to plug in: to the pipes, the port _is_ your tank. Pump into it to fill, out of it to empty.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.implant.runs_on", "Runs on %s: %s mB a second");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.implant.runs_on_any", "Runs on whatever is in the tank, a shot at a time");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.implant.necrosis", "Necrosis: %s%% (blood in your backtank clears it)");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.left_eye", "Left eye");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.right_eye", "Right eye");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.heart", "Heart");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.lungs", "Lungs");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.stomach", "Stomach");
        bloodless("bloodandbones.body.left_eye", "Left lens");
        bloodless("bloodandbones.body.right_eye", "Right lens");
        bloodless("bloodandbones.body.heart", "Pump");
        bloodless("bloodandbones.body.lungs", "Bellows");
        bloodless("bloodandbones.body.stomach", "Hopper");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.dead", "%s (dry)");
        // ---- minions (docs/PARTS-AND-TRAITS.md section 6)
        // the tasks (docs/NEXT.md 1.1), what each does, why a body cannot do one, what one waits for, and the heads'
        // dispositions (docs/NEXT.md 1.2); a construct takes things apart rather than butchering them (brief rule 4)
        for (String[] task : new String[][]{
                {"idle", "Idle", "Stays at home, or follows you. It only fights back."},
                {"guard", "Guard", "At home, fights the monsters within its reach. With you, goes for what hurts you and what you hit, as a tamed wolf does."},
                {"sentry", "Sentry", "Never leaves its post, and shoots what comes within range. With no bow, it strikes what comes within reach."},
                {"hunter", "Hunter", "Kills prey near home or near you. With a Meat Hook in hand, it leaves whole carcasses."},
                {"sapper", "Sapper", "Walks up to its target, or to the banner it was shown, and blows itself up. Then it lies powered down."},
                {"surgeon", "Surgeon", "Keeps by its Surgery Table, does the ritual's cutting, and tends whoever lies there."},
                {"medic", "Medic", "Throws the healing potions it carries at hurt allies."},
                {"herder", "Herder", "Leads strays back home with the food it holds."},
                {"tender", "Tender", "Keeps troughs and cradles near home stocked, and carries blood to fallen minions."},
                {"courier", "Courier", "Picks up loose items and takes them to the container by home, or with you, to your hands. Holding one, it takes only its like."},
                {"hauler", "Hauler", "Drags whole carcasses to a free Shackle Hook or Bleeding Rack."},
                {"farmer", "Farmer", "Reaps ripe crops near home and plants them again."},
                {"fisher", "Fisher", "Fishes still water near home, with a rod or by hand."},
                {"butcher", "Butcher", "Takes carcasses near home apart with a Cleaver or a Flensing Knife, at hand yields."},
                {"barterer", "Barterer", "Trades gold from the container by home as a piglin does."},
                {"digger", "Digger", "Sniffs up what a sniffer finds in the ground near home."}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.task." + task[0], task[1]);
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.task." + task[0] + ".desc", task[2]);
        }
        bloodless("bloodandbones.minion.task.butcher", "Dismantler");
        bloodless("bloodandbones.minion.task.butcher.desc", "Takes bodies near home apart with a Cleaver or a Flensing Knife, at hand yields.");
        bloodless("bloodandbones.minion.task.hunter.desc", "Downs prey near home or near you. With a Meat Hook in hand, it leaves them whole.");
        bloodless("bloodandbones.minion.task.hauler.desc", "Drags whole bodies to a free Shackle Hook or Draining Rack.");
        bloodless("bloodandbones.minion.task.surgeon.desc", "Keeps by its Surgery Table, does the ritual's work, and tends whoever lies there.");
        bloodless("bloodandbones.minion.task.tender.desc", "Keeps troughs and cradles near home stocked, and carries essence to fallen constructs.");
        for (String[] cannot : new String[][]{{"strike", "Nothing to strike with"}, {"detonator", "Nothing in it that detonates"},
                {"hand", "No hand to hold a surgeon's blade"}, {"throw", "Nothing to throw with"}, {"bait", "Nothing to hold food with"},
                {"pick", "Nothing to pick with"}, {"catch", "Nothing to catch with"}, {"blade", "Nothing to hold a blade with"},
                {"nose", "No nose, paws or claws to dig with"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.cannot." + cannot[0], cannot[1]);
        }
        bloodless("bloodandbones.minion.cannot.hand", "No hand to hold a tool");
        for (String[] wants : new String[][]{{"butcher", "waiting for a Cleaver or a Flensing Knife"}, {"herder", "waiting for food to lead animals with"},
                {"medic", "waiting for splash potions of healing"}, {"griefing", "waiting for the mobGriefing rule, which is off"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.wants." + wants[0], wants[1]);
        }
        // a tool its task file narrows, named from the file
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.wants.items", "waiting for %s");
        // what a tool it lacks would make of it
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.with_tool", "With %s: %s");
        for (String[] tool : new String[][]{{"sentry", "a bow, crossbow or trident"}, {"fisher", "a fishing rod"}, {"courier", "a sample to fetch the like of"},
                {"butcher", "a Cleaver or a Flensing Knife"}, {"herder", "food"}, {"medic", "healing potions"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.tool." + tool[0], tool[1]);
        }
        // what its task's work waits on in the world, for its status line
        for (String[] idle : new String[][]{{"fisher", "no still water within %s of home"}, {"digger", "no grass, moss or dirt to sniff within %s of home"},
                {"barterer", "no gold in a container within %s of home"}, {"hauler", "no free Shackle Hook or Bleeding Rack within %s of home"},
                {"container", "no container within 6 of home to put its takings in"},
                {"tender_blood", "no blood to be had by the bucket within %s of home"},
                {"tender_canister", "no full Soul Canister in a container within %s of home"},
                {"tender_rest", "nowhere by home for the rest of a bucket that a fallen minion cannot hold"},
                {"maker_full", "its maker has no room for what it brings"},
                {"surgeon", "no Surgery Table within %s of where it stands"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.idle." + idle[0], idle[1]);
        }
        // how fit it is, in a word (docs/NEXT.md 1.3)
        for (String[] fit : new String[][]{{"hopeless", "Hopeless"}, {"fair", "Fair"}, {"able", "Able"}, {"good", "Good"}, {"born", "Born to it"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.fit." + fit[0], fit[1]);
        }
        // where it works
        for (String[] where : new String[][]{{"home", "at home"}, {"post", "at its post"}, {"table", "at its table"}, {"maker", "with its maker"},
                {"maker_away", "at home, its maker away"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.where." + where[0], where[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.doing", "%s %s %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.doing_idle", "%s %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.task_fitness", "%s %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.woke", "Woke as a %s (%s)");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.woke_idle", "Woke, %s at home");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.lost", "it can no longer be a %s: %s");
        // a row's reasons: each stat it reads, its value and where that came from (docs/NEXT.md 1.3)
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.factor", "%s ×%s: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.factor_second", "%s ×%s, at half weight: %s");
        for (String[] stat : new String[][]{{"pace", "Pace"}, {"sight", "Sight"}, {"hands", "Hands"}, {"blow", "Blow"}, {"ranged", "Ranged"},
                {"toughness", "Toughness"}, {"carry", "Carry"}, {"pull", "Pull"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.stat." + stat[0], stat[1]);
        }
        for (String[] value : new String[][]{{"pace", "speed %s"}, {"sight", "%s blocks"}, {"blow", "%s a blow"}, {"ranged", "a ranged attack"},
                {"no_ranged", "no ranged attack"}, {"toughness", "%s health"}, {"carry", "%s slots"}, {"pull", "weight %s"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.value." + value[0], value[1]);
        }
        for (String[] grip : new String[][]{{"hand", "hand"}, {"paw", "paw"}, {"claw", "claw"}, {"hoof", "hoof"}, {"tentacle", "tentacle"},
                {"wing", "wing"}, {"mouth", "mouth"}, {"none", "its own body"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.grip." + grip[0], grip[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.source.piece", "%s %s");
        for (String[] part : new String[][]{{"head", "head"}, {"torso", "torso"}, {"arm", "arm"}, {"leg", "leg"}, {"tail", "tail"}, {"neck", "neck"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.part." + part[0], part[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.part_front", "front %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.part_mid", "middle %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.part_hind", "hind %s");
        for (String[] rule : new String[][]{{"blind", "both eyes out"}, {"mindless", "no head: it feels its way"}, {"bite", "its bite"},
                {"own_way", "its own way of moving"}, {"more", "%s of them"}, {"strike_rate", "%s arms that strike"}, {"berserk", "berserk"},
                {"no_tool", "its mouth, as good as a tool"}, {"chest", "a chest, %s slots more"}, {"own_body", "its own body"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.rule." + rule[0], rule[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.knack", "Knack ×%s: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.knack_part", "%s %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.disposition_line", "Disposition ×%s: %s");
        // what its fitness makes of its work (docs/NEXT.md 1.2)
        for (String[] lever : new String[][]{{"strike", "Strikes every %s s"}, {"sentry", "Shoots every %s s with a bow, spread %s"},
                {"surgeon", "Tends a heart every %s s"}, {"surgeon_stump", "A stump it cuts costs %s of blood to fit"},
                {"surgeon_no_cut", "It tends, but only a surgeon's head may cut"}, {"medic", "Throws every %s s, spread %s"},
                {"herder", "Keeps after a stray for %s s"}, {"look", "Looks round every %s s"}, {"hauler", "Towing, slowed %s times as much as a player, to at most %s"},
                {"hauler_player", "Towing, slowed as much as a player, no less"},
                {"farmer", "Looks for ripe crops every %s s"}, {"fisher", "A catch every %s to %s s"}, {"butcher", "A stroke every %s s, keeping %s of each cut"},
                {"barterer", "Looks gold over for %s s"}, {"digger", "A find every %s to %s s"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.lever." + lever[0], lever[1]);
        }
        bloodless("bloodandbones.minion.lever.surgeon_stump", "An open socket it leaves costs %s of essence to fit");
        bloodless("bloodandbones.minion.lever.surgeon_no_cut", "It tends, but only a surgeon's head may do the ritual's work");
        // what a part brings to a minion's tasks, on JEI's Body Parts page and a piece's tooltip (docs/NEXT.md 1.4)
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.knacks", "Knacks: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.knack", "%s ×%s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.grip", "Holds with: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.grip_pair", "Holds with: %s, %s of them");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.leg_grip", "Holds with: %s, on a body with %s legs or more");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.disposition", "Disposition: %s");
        // what the surgeon file's switch reads (docs/NEXT.md 1.5): by default any hand may cut, so the head counts only with it on
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.surgeon", "A surgeon's head: it may do the ritual's cutting even where only such heads may");
        bloodless("bloodandbones.minion.facts.surgeon", "A surgeon's head: it may do the ritual's work even where only such heads may");
        // JEI's page shows a part as a new mob of its kind has it, and says what a carcass keeps of its mob that changes that
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.varies", "As a new one has them: its %s changes them");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.or", " or ");
        for (String[] trait : new String[][]{{"profession", "profession"}, {"gene", "gene"}, {"variant", "kind"}, {"name", "name"}, {"wool", "wool"},
                {"charged", "charge"}, {"mushroom", "colour"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.trait." + trait[0], trait[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.in_minion", "In a minion:");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.facts.hold_ctrl", "Hold Ctrl for what it brings to a minion's tasks");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.at_work", "At work: %s mB of blood a minute");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.at_work_brass", "At work: %s mB of soul blood a minute");
        // the task screen (docs/NEXT.md 1.3)
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.title", "%s: Tasks");
        for (String[] group : new String[][]{{"fight", "Fight"}, {"tend", "Tend"}, {"fetch", "Fetch"}, {"work", "Work"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.group." + group[0], group[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.cannot", "Cannot");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.cannot_line", "%s: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.fit_line", "%s: %s, %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.row_with_me", "As it would do it with you");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.click", "Click to set it to this");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.now", "Its task now");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.at_home", "At home");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.at_home.hint", "It works from home: a sentry from its post, a surgeon at its table");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.with_me", "With me");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.with_me.hint", "It works round you while you are within 64 blocks; farther off, at home");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.with_me.never", "A %s works only from home");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.reach", "Reach");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.reach.hint", "How far from home, or from you, it works: %s to %s blocks (its own: %s)");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.home_here", "Home here");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.screen.home_here.hint", "Its home becomes where it stands now: lead it there first");
        for (String[] disposition : new String[][]{{"none", "Even-Tempered"}, {"brave", "Brave"}, {"berserk", "Berserk"}, {"territorial", "Territorial"},
                {"loyal", "Loyal"}, {"docile", "Docile"}, {"meek", "Meek"}, {"skittish", "Skittish"}, {"nocturnal", "Nocturnal"}, {"dim", "Dim"},
                {"mindless", "Mindless"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.disposition." + disposition[0], disposition[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.holds", "Holding %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.wears", "Wearing %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.cannot_hold", "It has no hand or head to hold that with");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.carries", "Carrying %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.no_room", "It has no room left to carry that");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.status", "%s, blood %s of %s mB");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.status_more", "%s; %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.status_down", "%s, out of blood (%s of %s mB): give it blood to wake it");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.frame_stats", "%s health, speed %s; best: %s; %s of %s sockets filled");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.no_fit", "That doesn't go on a minion");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.needs_hide", "A flesh minion takes pieces with their hide still on");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.needs_skinned", "A brass minion takes only skinned pieces");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.no_socket", "There is nowhere left on it for that");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.too_big", "It would be too big to get up");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.too_rotten", "The body has gone off too far to wake");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.cap", "You have as many minions as this world allows");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.dormant", "Folded up, out of blood. Set it down and give it blood to wake it.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.take_apart_where", "Lay it on a clear Assembly Frame table to take it apart");
        bloodless("bloodandbones.minion.needs_hide", "An essence construct takes parts with their covering still on");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.left_arm", "Left arm");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.right_arm", "Right arm");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.left_leg", "Left leg");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.right_leg", "Right leg");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.title", "Surgery");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.bare", "Fit a Surgical Rig to operate, or an Assembly Frame to build minions");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.lay_body", "Lay a carcass torso on the frame to build a minion on it");
        item("surgical_rig",
                "A Surgery Table _attachment_: an overhead arm of lamps, clamps and blades. Makes the table a place to _operate_.",
                "When R-Clicked on a Surgery Table", "Fits it (swapping out any other attachment). Then patients can lie on the table, and a carcass laid on it gives up its _organs_ to a Cleaver, then its _hide_, limbs and meat, one slow cut at a time. By hand the meat and hide come out as a hand's do, about half; a _Deployer_ with a Cleaver gets all of it.",
                "When Sneak-R-Clicked off an Empty Table", "An empty hand takes it back off.");
        bloodless("item.bloodandbones.surgical_rig.tooltip.behaviour1", "Fits it (swapping out any other attachment). Then patients can lie on the table, and a wreck laid on it gives up its _cores_ to a Cleaver, then its _covering_, limbs and meat, one slow cut at a time. By hand the meat and covering come out as a hand's do, about half; a _Deployer_ with a Cleaver gets all of it.");
        item("assembly_frame",
                "A Surgery Table _attachment_: clamps and a jig for stitching bodies together. Makes the table a place to build _minions_.",
                "When R-Clicked on a Surgery Table", "Fits it (swapping out any other attachment). Then a carcass _torso_ laid on it can be built into a minion, with the heads, legs and arms of _any_ mob.",
                "When Sneak-R-Clicked off an Empty Table", "An empty hand takes it back off.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.title_other", "Surgery on %s");
        bloodless("block.bloodandbones.blood_trough.tooltip.summary",
                "A trough of _essence_ for essence constructs: they walk to the nearest one they can reach and drink when they run low.");
        block("blood_trough",
                "A trough of _blood_ for flesh minions: they walk to the nearest one they can reach and drink when they run low.",
                "When Filled", "Takes _four buckets_ of any blood, from a bucket, a _Spout_ or _pipes_ on any side.");
        item("empty_soul_canister",
                "A brass canister with a glass window, for _soul blood_ to animate a _brass minion_.",
                "When Filled", "A _Spout_ fills it with a bucket's worth of soul blood; an _Item Drain_ empties it again.");
        item("soul_canister",
                "A canister of _soul blood_: a brass minion's power.",
                "When Used on a Brass Minion", "Swaps into it, and the empty comes back. On the Surgery Table it wakes a sheathed brass frame.");
        item("brass_sheathing",
                "Brass plates for a frame of _skinned_ carcass pieces on the Surgery Table.",
                "When R-Clicked on a Brass Frame", "Sheathes it. Then a _Soul Canister_ wakes it as a brass minion: slower to drain, immune to poison and drowning, but it never heals itself (a _brass sheet_ mends it, from a hand or a _Deployer_). Its maker crouch-R-Clicks it with a _Filter_ (or any item) to limit what it picks up, reaps or goes for; a crouching _Wrench_ takes the filter out.");
        block("charging_cradle",
                "Where _brass minions_ get fresh soul blood. Needs a shaft from below turning at _16 RPM_ or more; the faster, the quicker the swap.",
                "When Stocked", "Swaps a full _Soul Canister_ into any brass minion beside it that is running low or has powered down, and keeps the empty. With _brass sheets_ it mends one docked there.",
                "With Funnels, Hoppers and Arms", "Full canisters and sheets go in, empties come out, by funnel, hopper, chute or _Mechanical Arm_. By hand: R-Click with a canister or sheet to put it in, with an empty hand to take the empties.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.needs_sheathing", "A brass frame needs its Brass Sheathing before it can wake");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.wake_brass", "Brass wakes on a Soul Canister");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.wake_flesh", "Flesh wakes on a bucket of blood");
        bloodless("bloodandbones.minion.wake_flesh", "An essence construct wakes on a bucket of essence");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.sheathe_skinned", "Brass Sheathing goes over a frame of skinned pieces");
        // a brass minion's filter slot
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.filter_set", "Filter: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.filter_taken", "Filter taken out");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.filter_none", "It has no filter");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.filter_brass_only", "Only brass minions take a filter");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.status_filtered", "%s, blood %s of %s mB, filter: %s");
        item("dormant_minion",
                "A minion that ran out of blood, _folded up_ to carry. It keeps everything: what it is built of, its task, what it carries.",
                "When Used on a Block", "Sets it down there, still out of blood. Give it _blood_ to wake it.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.no_organs", "Nothing more to take out of it");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.filtered", "The rig's filter passes this over");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.on_table", "On the table: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.natural", "Your own");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.missing", "Missing");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.ragged", "Ragged stump");
        bloodless("bloodandbones.surgery.state.ragged", "Open socket");
        // a ragged stump's price (docs/NEXT.md 1.5): what fitting anything but a crude prosthetic there costs
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.ragged_price", "Ragged stump: %s");
        bloodless("bloodandbones.surgery.state.ragged_price", "Open socket: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.bucket", "a bucket");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.buckets", "%s buckets");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.needs_surgeon", "Needs a surgeon");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.needs_blood", "Needs %s of blood");
        // the surgeon by the table, before any cut: who, how fit, and what its stumps will cost
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.surgeon", "Surgeon: %s, %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.surgeon_price", "Its stumps cost %s of blood to fit");
        bloodless("bloodandbones.surgery.surgeon_price", "Its open sockets cost %s of essence to fit");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.surgeon_head", "Minion (%s head)");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.surgeon_headless", "Minion (no head)");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.no_surgeon", "No surgeon by the table");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.no_surgeon.hint", "Set a minion to Surgeon within 4 blocks to cut");
        bloodless("bloodandbones.surgery.no_surgeon.hint", "Set a construct to Surgeon within 4 blocks for the ritual");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.done", "Done");
        // the surgery screen (package 11): the doll's key, the right panel's lines and what each card does
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.ragged_fit", "Ragged stump: %s of blood to fit");
        bloodless("bloodandbones.surgery.state.ragged_fit", "Open socket: %s of essence to fit");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.fits_key", "Something you carry fits here");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.surgeon_short", "Surgeon: %s, %s fit");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.table_empty", "Nothing on the table");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.blood_carried", "Blood on you: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.mb", "%s mB");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.carried", "You carry");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.carried_none", "Nothing you carry fits this body");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.choices", "What can be done");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.nothing_here", "Nothing on the table or on you can be used here");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.pick", "Click to pick");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.fits", "Goes in: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.from.bare", "Back into your hands");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.from.table", "%s, on the table");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.from.carried", "%s, carried");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.do.none", "-");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.do.take_off", "Take it off");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.do.replace", "Take it off, fit this");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.do.swap", "Swap it for this");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.do.fit", "Fit this");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.do.reattach", "Put it back on");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.do.unclip", "Unclip it");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.do.fit_module", "Fit this module");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.do.take_module", "Take a module out");

        // ---- cybernetic modules and the throttle
        item("grappling_spool",
                "An _arm module_: a barbed hook on a reel of cable.",
                "Throttle: Hold, Then Let Go", "Fires the hook where you look, further the longer you spooled. A _light_ thing is reeled in to you; anything _heavy_ (a wall, a big mob, a heavy carcass) reels _you_ in to it. Choose what you hook: a wall at speed hurts.");
        item("rotational_coupler",
                "An _arm module_: a telescoping shaft in the forearm.",
                "Throttle: Hold", "Look at the end of a machine's shaft and the shaft reaches out and _drives_ it: 16 RPM on a tap, up to 256 RPM at full spool, with stress capacity to match. Let go and it pulls back.");
        item("piston_ram",
                "An _arm module_: a piston behind the knuckles.",
                "Throttle: Hold, Then Let Go", "Strikes what you look at with _knockback_ that grows with the spool. Looking down at the ground, it _launches_ you instead. Pair it with a _Gyroscopic Stabilizer_ for the landing.");
        item("magnet_coil",
                "An _arm module_: a coil of copper round the forearm.",
                "Always", "Loose items and experience _drift_ to you from a few blocks.",
                "Throttle: Hold", "Pulls from further the longer it is held. Near the top it takes hold of _carcasses_ too.");
        item("analytical_lens",
                "An _eye module_: a ground lens with a brass iris.",
                "Always", "Counts as Create's _goggles_. Looking at a machine _through walls_, you read its speed, its network's stress and what goggles would tell you. Costs a little soul blood, all the time.");
        item("gyroscopic_stabilizer",
                "A _leg module_: a spinning gyroscope in the shin.",
                "Always", "Takes _no fall damage_, paying soul blood for every block fallen past the safe distance. What the tank cannot pay for, you take: check your gauge before you step off.");
        item("barometric_vent",
                "A _leg module_: a pressure vent in the heel.",
                "Throttle: Hold, Then Let Go", "A puff of lift, then a slow _hover_ down: a second on a tap, a few seconds at full spool. Crouch to drop.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.module.fits.arm", "Fits a brass arm");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.module.fits.leg", "Fits a brass leg");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.module.fits.eye", "Fits a brass eye");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.module.mode.fire", "Throttle: spool up, let go to fire");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.module.mode.hold", "Throttle: works while held");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.module.mode.passive", "Always on");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.implant.modules", "Modules: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.implant.module_empty", "empty");
        BloodAndBones.REGISTRATE.addRawLang("key.categories.bloodandbones", "Blood & Bones");
        BloodAndBones.REGISTRATE.addRawLang("key.bloodandbones.throttle", "Cybernetic throttle (hold)");
        BloodAndBones.REGISTRATE.addRawLang("key.bloodandbones.next_module", "Next cybernetic module");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.throttle.selected", "Throttle drives: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.throttle.dry", "No soul blood in your backtank");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.lens.seen", "%s, %s blocks off");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.lens.speed", "Speed: %s RPM");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.lens.stress", "Network stress: %s of %s su");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.lens.overstressed", "Overstressed");
        // ---- parts and traits: scraps and carcass armour
        item("scraps",
                "What the _Mangler_ grinds a piece of carcass into, keeping which _mob_ and which _part_ it was.",
                "In a Crafting Grid", "Scraps of one mob make _carcass armour_: head scraps a helmet, torso scraps a chestplate, leg scraps leggings and boots. The mob decides the armour's material and its _traits_.");
        bloodless("item.bloodandbones.scraps.tooltip.behaviour1",
                "Salvage of one mob makes _plated armour_: head salvage a helmet, torso salvage a chestplate, leg salvage leggings and boots. The mob decides the armour's material and its _traits_.");
        String worn = "Every mob's parts do something different: _mix_ them freely. A full set from _one_ mob adds that mob's bonus, and its drawback.";
        String hide = "Craft it with _hides of one mob_ (one for a helmet or boots, two for leggings, three for a chestplate) to add that mob's _hide traits_. A new hide replaces the old, which comes back.";
        String covering = "Craft it with _coverings of one mob_ (one for a helmet or boots, two for leggings, three for a chestplate) to add that mob's _covering traits_. A new covering replaces the old, which comes back.";
        String tiers = "Craft it with a _Blood Steel Ingot_, then a _Blood Diamond_, then a _Soul Netherite Ingot_: each tier gives more armour, toughness and durability than the last. Soul netherite does not burn.";
        String tiersBloodless = "Craft it with an _Essence Steel Ingot_, then an _Essence Diamond_, then a _Soul Netherite Ingot_: each tier gives more armour, toughness and durability than the last. Soul netherite does not burn.";
        String strap = "Craft it with a _Fluid Backtank_ to strap the tank on its back: prosthetics run on it as if it were worn, Spouts and Item Drains fill and empty it, and the armour is the better of the two. Craft the chestplate alone to take the tank off; if the chestplate breaks or burns, the tank falls free.";
        java.util.Map<String, String> organs = java.util.Map.of("helmet", "an _eye_ or an _olfactory bulb_", "chestplate", "a _heart_, _lungs_ or a creeper's _powder sac_",
                "leggings", "a _stomach_ or a spider's _spinneret_", "boots", "a _rabbit's foot_ or a goat's _leap gland_");
        java.util.Map<String, String> coreNames = java.util.Map.of("helmet", "a _lens_ or a _scent filter_", "chestplate", "a _pump_, _bellows_ or a creeper's _powder core_",
                "leggings", "a _hopper_ or a spider's _thread spinner_", "boots", "a _lucky charm_ or a goat's _spring core_");
        for (String piece : new String[]{"helmet", "leggings", "boots", "chestplate"}) {
            java.util.List<String> pairs = new java.util.ArrayList<>(java.util.List.of("When Worn", worn, "Fitting a Hide", hide));
            String organ = organs.get(piece);
            if (organ != null) {
                pairs.addAll(java.util.List.of("Fitting an Organ", "Craft it with an organ taken out of a mob on the Surgery Table, such as " + organ + ", to add that mob's _organ traits_ (each organ says which pieces take it). One organ a piece; a new one replaces the old, which comes back."));
            }
            pairs.addAll(java.util.List.of("Upgrading", tiers));
            int upgrading = pairs.size() / 2;
            if (piece.equals("chestplate")) {
                pairs.addAll(java.util.List.of("With a Fluid Backtank", strap));
            }
            item("carcass_" + piece,
                    "Armour of _scraps_ from the Mangler. Its _material_ comes from the family of the mob it was made of, its _traits_ from that mob's parts.",
                    pairs.toArray(String[]::new));
            // bloodless mode plates armour with salvage, fits a covering and installs a core
            String key = "item.bloodandbones.carcass_" + piece + ".tooltip.";
            bloodless(key + "summary", "Armour of _salvage_ from the Mangler. Its _material_ comes from the family of the mob it was made of, its _traits_ from that mob's parts.");
            bloodless(key + "condition2", "Fitting a Covering");
            bloodless(key + "behaviour2", covering);
            if (organ != null) {
                bloodless(key + "condition3", "Installing a Core");
                bloodless(key + "behaviour3", "Craft it with a core taken out of a mob on the Surgery Table, such as " + coreNames.get(piece) + ", to add that mob's _core traits_ (each core says which pieces take it). One core a piece; a new one replaces the old, which comes back.");
            }
            bloodless(key + "behaviour" + upgrading, tiersBloodless);
        }
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.scraps.named", "%s %s Scraps");
        BloodAndBones.REGISTRATE.addRawLang("bloodless.item.bloodandbones.scraps.named", "%s %s Salvage");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.carcass_armour.named", "%s %s %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodless.item.bloodandbones.carcass_armour.named", "%1$s Plated %3$s");
        for (String part : new String[]{"head", "torso", "arm", "leg", "tail"}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.part." + part, Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        for (String piece : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.piece." + piece, Character.toUpperCase(piece.charAt(0)) + piece.substring(1));
        }
        // the grazers' material is their meat and gristle, not their hide: hide is fitted on top, and a cow's boots
        // of scraps alone must not read "Cow Hide Boots"
        BloodAndBones.REGISTRATE.addRawLang("scrap_material.bloodandbones.hide_plate", "Brawn");
        BloodAndBones.REGISTRATE.addRawLang("scrap_material.bloodandbones.sinew", "Sinew");
        bloodless("scrap_material.bloodandbones.sinew", "Cable");
        BloodAndBones.REGISTRATE.addRawLang("scrap_material.bloodandbones.gristle", "Gristle");
        // the item name is mob, material, piece: "Pelt" and "Plate", so a polar bear's is not "Polar Bear Bear Hide Chestplate"
        String[][] materials = {
                {"bear_hide", "Pelt"}, {"shell", "Shell"}, {"chitin", "Chitin"}, {"bone", "Bone"}, {"skin", "Skin"}, {"scale", "Scale"},
                {"feather", "Feather"}, {"ectoplasm", "Ectoplasm"}, {"gel", "Gel"}, {"ember", "Ember"}, {"golem_plate", "Plate"}, {"sculk", "Sculk"}};
        for (String[] material : materials) {
            BloodAndBones.REGISTRATE.addRawLang("scrap_material.bloodandbones." + material[0], material[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.body", "Made from: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.shoulders", "Shoulders: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.hips", "Hips: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.hide", "Hide: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodless.bloodandbones.carcass_armour.hide", "Covering: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.hide_plain", "Hide: plain");
        bloodless("bloodandbones.carcass_armour.hide_plain", "Covering: plain");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.organ", "Organ: %s %s");
        bloodless("bloodandbones.carcass_armour.organ", "Core: %s %s");
        // the organs by name in armour's own text; bloodless mode calls them by what they would be in a machine
        String[][] organNames = {{"eye", "Eye", "Lens"}, {"heart", "Heart", "Pump"}, {"lungs", "Lungs", "Bellows"}, {"stomach", "Stomach", "Hopper"}};
        for (String[] organ : organNames) {
            BloodAndBones.REGISTRATE.addRawLang("organ.bloodandbones." + organ[0], organ[1]);
            bloodless("organ.bloodandbones." + organ[0], organ[2]);
        }
        // the special organs the mob data names, every mob at least one (docs/PARTS-AND-TRAITS.md section 8); bloodless
        // mode calls each by the part it would be in a machine (section 7.10). The powder sac is Motion's.
        String[][] specialOrgans = {
                {"core", "Core", "Core"}, {"alchemical_gland", "Alchemical Gland", "Alchemical Core"}, {"bacon_fat", "Bacon Fat", "Fuel Cell"},
                {"bamboo_gut", "Bamboo Gut", "Bamboo Hopper"}, {"blaze_core", "Blaze Core", "Blaze Core"}, {"boar_heart", "Boar Heart", "Boar Pump"},
                {"brown_fat", "Brown Fat", "Insulation Cell"}, {"burrow_gland", "Burrow Gland", "Burrow Core"},
                {"cheek_pouch", "Cheek Pouch", "Storage Pouch"}, {"curable_heart", "Curable Heart", "Restorable Pump"},
                {"drowned_lungs", "Drowned Lungs", "Drowned Bellows"},
                {"echo_ear", "Echo Ear", "Echo Receiver"}, {"egg_gland", "Egg Gland", "Egg Dispenser"}, {"elder_eye", "Elder Eye", "Elder Lens"},
                {"ender_gland", "Ender Gland", "Ender Core"}, {"frost_core", "Frost Core", "Frost Core"}, {"frost_marrow", "Frost Marrow", "Rime Core"},
                {"gizzard", "Gizzard", "Grinder"}, {"glow_sac", "Glow Sac", "Glow Cell"}, {"gold_gizzard", "Gold Gizzard", "Gold Sorter"},
                {"golem_core", "Golem Core", "Golem Core"}, {"harmonic_gland", "Harmonic Gland", "Harmonic Core"},
                {"honey_stomach", "Honey Stomach", "Honey Hopper"}, {"hump_fat", "Hump Fat", "Reserve Tank"}, {"ink_sac", "Ink Sac", "Ink Reservoir"},
                {"lanolin_gland", "Lanolin Gland", "Oil Core"}, {"lava_bladder", "Lava Bladder", "Lava Tank"}, {"leap_gland", "Leap Gland", "Spring Core"},
                {"levitation_gland", "Levitation Gland", "Levitation Core"}, {"magma_core", "Magma Core", "Magma Core"}, {"marrow", "Marrow", "Lean Core"},
                {"melon", "Melon", "Sonar Lens"}, {"mirror_gland", "Mirror Gland", "Mirror Core"}, {"mycelial_gut", "Mycelial Gut", "Mycelial Hopper"},
                {"night_stalker_gland", "Night Stalker Gland", "Night Stalker Core"}, {"nine_lives", "Nine Lives", "Spare Life Core"},
                {"olfactory_bulb", "Olfactory Bulb", "Scent Filter"}, {"pack_gland", "Pack Gland", "Pack Core"}, {"pack_sinew", "Pack Sinew", "Pack Strut"},
                {"prism_eye", "Prism Eye", "Prism Lens"}, {"purr_box", "Purr Box", "Purr Box"}, {"rabbit_foot", "Rabbit's Foot", "Lucky Charm"},
                {"racing_heart", "Racing Heart", "Racing Pump"}, {"rage_gland", "Rage Gland", "Overdrive Core"}, {"raider_gland", "Raider Gland", "Raider Core"},
                {"regrowth_gland", "Regrowth Gland", "Regrowth Core"}, {"rift_mite", "Rift Mite", "Rift Core"}, {"rot_gut", "Rot Gut", "Iron Hopper"},
                {"rumen", "Rumen", "Fermenter"}, {"salt_gland", "Salt Gland", "Salt Filter"}, {"scute_gland", "Scute Gland", "Scute Press"},
                {"shell_gland", "Shell Gland", "Shell Core"}, {"slime_core", "Slime Core", "Slime Core"}, {"sonic_core", "Sonic Core", "Sonic Core"},
                {"spinneret", "Spinneret", "Thread Spinner"}, {"spit_gland", "Spit Gland", "Spit Nozzle"}, {"spleen", "Spleen", "Reserve Cell"},
                {"spore_marrow", "Spore Marrow", "Spore Core"}, {"sticky_tongue", "Sticky Tongue", "Grapple Line"}, {"stinger", "Stinger", "Needle"},
                {"swim_bladder", "Swim Bladder", "Buoyancy Tank"}, {"tear_gland", "Tear Gland", "Tear Core"}, {"totem_gland", "Totem Gland", "Totem Core"},
                {"toxin_sac", "Toxin Sac", "Toxin Cell"}, {"traders_draught", "Trader's Draught", "Draught Flask"}, {"venom_sac", "Venom Sac", "Venom Cell"},
                {"vex_wisp", "Vex Wisp", "Vex Wisp"}, {"village_heart", "Village Heart", "Village Pump"}, {"war_heart", "War Heart", "War Pump"},
                {"wind_core", "Wind Core", "Wind Core"}, {"wither_core", "Wither Core", "Wither Core"}, {"wither_marrow", "Wither Marrow", "Blight Core"}};
        for (String[] organ : specialOrgans) {
            BloodAndBones.REGISTRATE.addRawLang("organ.bloodandbones." + organ[0], organ[1]);
            bloodless("organ.bloodandbones." + organ[0], organ[2]);
        }
        organs();
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.tier", "Tier %s: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.strapped", "Strapped on: %s");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.raw_hide.of", "Raw %s Hide");
        bloodless("item.bloodandbones.raw_hide.of", "Raw %s Covering");
        bloodless("item.bloodandbones.raw_hide", "Raw Covering");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.full_set", "A full set of one mob: %s");
        BloodAndBones.REGISTRATE.addRawLang("set.bloodandbones.beast", "Beast");
        BloodAndBones.REGISTRATE.addRawLang("set.bloodandbones.herd_beast", "Herd Beast");
        BloodAndBones.REGISTRATE.addRawLang("set.bloodandbones.warren", "Warren");
        BloodAndBones.REGISTRATE.addRawLang("set.bloodandbones.pure", "Pure Set");
        String[][] sets = {
                {"centaur", "Centaur"}, {"hog_wild", "Hog Wild"}, {"alpha", "Alpha"}, {"nine_lives", "Nine Lives"}, {"hibernator", "Hibernator"},
                {"fortress", "Fortress"}, {"juggernaut", "Juggernaut"}, {"amphibious", "Amphibious"}, {"elder", "Elder"}, {"raid_captain", "Raid Captain"},
                {"bartered", "Bartered"}, {"brood", "Brood"}, {"infestation", "Infestation"}, {"featherweight", "Featherweight"},
                {"night_wing", "Night Wing"}, {"ethereal", "Ethereal"}, {"gills", "Gills"}, {"deep_one", "Deep One"}, {"kraken", "Kraken"},
                {"gelatinous", "Gelatinous"}, {"decay_lord", "Decay Lord"}, {"shambler", "Shambler"}, {"ossuary", "Ossuary"},
                {"inferno", "Inferno"}, {"walking_bomb", "Walking Bomb"}, {"voidwalker", "Voidwalker"}, {"mountaineer", "Mountaineer"},
                {"desert_shambler", "Desert Shambler"}, {"colossus", "Colossus"}};
        for (String[] set : sets) {
            BloodAndBones.REGISTRATE.addRawLang("set.bloodandbones." + set[0], set[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("attribute.bloodandbones.drag_strength", "Drag Strength");
        BloodAndBones.REGISTRATE.addRawLang("attribute.bloodandbones.butchery_yield", "Butchery Yield");
        bloodless("attribute.bloodandbones.butchery_yield", "Salvage Yield");
        String[][] traits = {
                {"hardy", "Hardy"}, {"frail", "Frail"}, {"swift", "Swift"}, {"sluggish", "Sluggish"}, {"steady", "Steady"}, {"sturdy", "Sturdy"},
                {"barrel_chest", "Barrel Chest"}, {"hooves", "Hooves"}, {"thick_hide", "Thick Hide"}, {"hauler", "Hauler"}, {"keen_butcher", "Keen Butcher"}, {"meek", "Meek"},
                {"springy", "Springy"}, {"light_boned", "Light-Boned"}, {"fall_guard", "Fall Guard"}, {"cud_chewer", "Cud Chewer"},
                {"alert", "Alert"}, {"prey", "Prey"},
                {"appraiser", "Appraiser"}, {"aquaphobe", "Aquaphobe"}, {"bane_weak", "Weak to Bane"}, {"beached", "Beached"},
                {"big_heart", "Big Heart"}, {"blast_padding", "Blast Padding"}, {"bolt", "Bolt"}, {"brawler", "Brawler"}, {"burrower", "Burrower"},
                {"canter", "Canter"}, {"cat_ward", "Cat Ward"}, {"chilling", "Chilling"}, {"clear_eyed", "Clear-Eyed"}, {"dark_sight", "Dark Sight"},
                {"deep_digger", "Deep Digger"}, {"dolphin_kick", "Dolphin Kick"}, {"downy", "Downy"}, {"draft_chest", "Draft Chest"},
                {"drowsy", "Drowsy"}, {"featherfall", "Featherfall"}, {"fins", "Fins"}, {"fire_weak", "Weak to Fire"}, {"fireproof", "Fireproof"},
                {"flight_response", "Flight Response"}, {"frost_guard", "Frost Guard"}, {"galloper", "Galloper"}, {"gills", "Gills"},
                {"gold_fever", "Gold Fever"}, {"golems_hostile", "Golems Hostile"}, {"grabbing", "Grabbing"}, {"groomed_coat", "Groomed Coat"},
                {"hay_burner", "Hay Burner"}, {"heavy", "Heavy"}, {"hero", "Hero"}, {"hibernator", "Hibernator"}, {"hog_wild", "Hog Wild"},
                {"hollow_frame", "Hollow Frame"}, {"howl", "Howl"}, {"hunger_proof", "Hunger-Proof"}, {"hungering", "Hungering"},
                {"ink_cloud", "Ink Cloud"}, {"iron_gut", "Iron Gut"}, {"iron_skin", "Iron Skin"}, {"juggernaut", "Juggernaut"},
                {"keen_eye", "Keen Eye"}, {"land_on_feet", "Land on Feet"}, {"lard", "Lard"}, {"light_hurts", "Light Hurts"},
                {"long_reach", "Long Reach"}, {"long_winded", "Long-Winded"}, {"lucky", "Lucky"}, {"magic_ward", "Magic Ward"}, {"mirror", "Mirror"},
                {"murk_sight", "Murk Sight"}, {"night_eyes", "Night Eyes"}, {"pecking_order", "Pecking Order"}, {"quick_hands", "Quick Hands"},
                {"regrowth", "Regrowth"}, {"relentless", "Relentless"}, {"screamer", "Screamer"}, {"sharpshooter", "Sharpshooter"},
                {"shell_guard", "Shell Guard"}, {"slick", "Slick"}, {"stallion_heart", "Stallion Heart"}, {"stealthy", "Stealthy"},
                {"sticky", "Sticky"}, {"stubborn", "Stubborn"}, {"sure_footed", "Sure-Footed"}, {"territorial", "Territorial"}, {"thirst", "Thirst"},
                {"tough", "Tough"}, {"toxic_skin", "Toxic Skin"}, {"trotters", "Trotters"}, {"turtle_up", "Turtle Up"},
                {"undead_body", "Undead Body"}, {"venom_proof", "Venom-Proof"}, {"venomous", "Venomous"}, {"villagers_flee", "Villagers Flee"},
                {"waterborn", "Waterborn"}, {"wither_proof", "Wither-Proof"}, {"withering", "Withering"}, {"zombie_bait", "Zombie Bait"},
                {"zombify", "Zombify"}};
        for (String[] trait : traits) {
            BloodAndBones.REGISTRATE.addRawLang("trait.bloodandbones." + trait[0], trait[1]);
        }
        // softening "gut" would make it "Iron Cord"
        BloodAndBones.REGISTRATE.addRawLang("bloodless.trait.bloodandbones.iron_gut", "Iron Stomach");
        // traits the mob signatures needed beyond the four groups' (docs/PARTS-AND-TRAITS.md section 8), each made of their types
        trait("fat_reserve", "Fat Reserve", "A minion holds a quarter more blood a level.");
        bloodless("trait.bloodandbones.fat_reserve", "Reserve Tank");
        trait("quench", "Quench", "Once alight, you burn for half as long (a quarter as long at II).");
        trait("stinger", "Stinger", "A quarter of your blows sting: Poison II for 3 seconds, at most once every 10 seconds.");
        trait("wind_shot", "Wind Shot", "A minion keeps its distance and fires wind charges at its target, one every 2 seconds, for 2 mB of blood each.");
        trait("creeper_kin", "Creeper Kin", "Creepers take you for one of their own and leave you be. One you hurt fights back for 30 seconds.");
        trait("iron_will", "Iron Will", "Below half health, 4 more armour.");
        trait("pouch", "Pouch", "A minion carries 9 more stacks a level.");
        trait("lanolin", "Lanolin", "The piece oils itself, mending a point of wear every 30 seconds.");
        trait("eight_eyes", "Eight Eyes", "In the dark, hostile mobs within 8 blocks show through walls.");
        trait("traders_draught", "Trader's Draught", "At night, when a mob sets its sights on you, you drink yourself invisible for 20 seconds. "
                + "Once every 5 minutes.");
        trait("potion_thrower", "Potion Thrower", "A minion keeps its distance and throws the splash potions it carries at its target, "
                + "or a poison of its own brewing for 5 mB of blood, every 3 seconds.");
        trait("remedy", "Remedy", "Hurt below half health, you drink a remedy: Regeneration for 5 seconds, and Fire Resistance too if you "
                + "are burning. Once every 30 seconds.");
        trait("totem", "Totem", "A killing blow leaves you on 1 health instead, as a Totem of Undying does, for 500 mB of blood; then not "
                + "again for 20 minutes. A minion collapses rather than dying.");
        trait("shell_lid", "Shell Lid", "A three in ten chance to send back projectiles that come at you from the front, as a shulker's lid does.");
        trait("night_swift", "Night Swift", "At night you move a twentieth faster.");
        trait("elder_curse", "Elder Curse", "Whatever strikes you up close gets Mining Fatigue II for 6 seconds.");
        trait("toss", "Toss", "One blow in seven or so tosses what you hit up into the air, as a hoglin's tusks do.");
        trait("fang_strike", "Fang Strike", "A tenth of your blows call evoker fangs up under what you hit, at most once every 10 seconds.");
        trait("still_mend", "Still Mend", "A minion standing still knits back 1 health every 4 seconds, for 5 mB of blood (brass never heals itself).");
        trait("dark_mend", "Dark Mend", "A minion in the dark knits back 1 health every 10 seconds, for 5 mB of blood (brass never heals itself).");
        trait("curable", "Curable", "Weakness never takes hold of you, and a golden apple gives you Absorption II.");
        // the minion leftovers (docs/ARCHITECTURE-PROPOSAL.md section 15.17)
        trait("sneeze", "Sneeze", "A minion with a weak panda's head sneezes every two minutes or so, for 5 mB of blood, and what comes out (slime, mostly) goes in with what it carries.");
        trait("lava_soak", "Lava Soak", "In lava, fire and lava hurt you half as much.");
        // the four groups of trait effects add their own (docs/ARCHITECTURE-PROPOSAL.md section 15.8)
        com.avicagan.bloodandbones.parts.effect.MotionEffects.lang();
        com.avicagan.bloodandbones.parts.effect.RangedEffects.lang();
        com.avicagan.bloodandbones.parts.effect.SocialEffects.lang();
        com.avicagan.bloodandbones.parts.effect.UpkeepEffects.lang();
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.hold_ctrl", "Hold Ctrl for what they do");
        // the Organ Ability (bloodless mode's organs are cores)
        BloodAndBones.REGISTRATE.addRawLang("key.bloodandbones.organ_ability", "Organ Ability");
        bloodless("key.bloodandbones.organ_ability", "Core Ability");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.organ.no_blood", "Not enough blood in your tank, and too hungry: that takes %s mB or %s hunger");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.organ_fitted", "Stitched in: %s");
        bloodless("bloodandbones.minion.organ_fitted", "Installed: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.set_bonus.flesh", "Flesh set: you heal from what you hit, and rot half as fast");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.set_bonus.brass", "Brass set: the throttle costs a quarter less, and you are hard to shove");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.nothing", "Nothing on the table can do that");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.occupied", "Someone is already on the table");

        // ---- the Fluid Backtank
        item("copper_fluid_backtank",
                "A tank for _any fluid_, worn on the back in the chest slot. Holds _2 buckets_ and gives the armour of copper.",
                "When R-Clicked on a Block", "Sets it _down_, fluid and all. Pipes fill or empty it from any side; break it to pick it up again.",
                "Filling and Emptying", "A _Spout_ fills it and an _Item Drain_ empties it. Organic prosthetics will run on the _blood_ in it, cybernetics on _soul blood_.");
        item("gold_fluid_backtank",
                "A tank for _any fluid_, worn on the back in the chest slot. Holds _3 buckets_ and gives the armour of gold.",
                "When R-Clicked on a Block", "Sets it _down_, fluid and all. Pipes fill or empty it from any side; break it to pick it up again.",
                "Filling and Emptying", "A _Spout_ fills it and an _Item Drain_ empties it. Organic prosthetics will run on the _blood_ in it, cybernetics on _soul blood_.");
        item("iron_fluid_backtank",
                "A tank for _any fluid_, worn on the back in the chest slot. Holds _4 buckets_ and gives the armour of iron.",
                "When R-Clicked on a Block", "Sets it _down_, fluid and all. Pipes fill or empty it from any side; break it to pick it up again.",
                "Filling and Emptying", "A _Spout_ fills it and an _Item Drain_ empties it. Organic prosthetics will run on the _blood_ in it, cybernetics on _soul blood_.");
        item("diamond_fluid_backtank",
                "A tank for _any fluid_, worn on the back in the chest slot. Holds _6 buckets_ and gives the armour of diamond.",
                "When R-Clicked on a Block", "Sets it _down_, fluid and all. Pipes fill or empty it from any side; break it to pick it up again.",
                "Filling and Emptying", "A _Spout_ fills it and an _Item Drain_ empties it. Organic prosthetics will run on the _blood_ in it, cybernetics on _soul blood_.");
        item("blood_steel_fluid_backtank",
                "A tank for _any fluid_, worn on the back in the chest slot. Holds _8 buckets_ and gives the armour of blood steel.",
                "When R-Clicked on a Block", "Sets it _down_, fluid and all. Pipes fill or empty it from any side; break it to pick it up again.",
                "Filling and Emptying", "A _Spout_ fills it and an _Item Drain_ empties it. Organic prosthetics will run on the _blood_ in it, cybernetics on _soul blood_.");
        item("blood_diamond_fluid_backtank",
                "A tank for _any fluid_, worn on the back in the chest slot. Holds _16 buckets_ and gives the armour of blood diamond.",
                "When R-Clicked on a Block", "Sets it _down_, fluid and all. Pipes fill or empty it from any side; break it to pick it up again.",
                "Filling and Emptying", "A _Spout_ fills it and an _Item Drain_ empties it. Organic prosthetics will run on the _blood_ in it, cybernetics on _soul blood_.");
        item("soul_netherite_fluid_backtank",
                "A tank for _any fluid_, worn on the back in the chest slot. Holds _32 buckets_ and gives the armour of soul netherite.",
                "When R-Clicked on a Block", "Sets it _down_, fluid and all. Pipes fill or empty it from any side; break it to pick it up again.",
                "Filling and Emptying", "A _Spout_ fills it and an _Item Drain_ empties it. Organic prosthetics will run on the _blood_ in it, cybernetics on _soul blood_.");
        item("soul_netherite_ingot",
                "Netherite with a _soul_ in it.",
                "When Made", "_Sequenced assembly_: fill a netherite ingot with 1000 mB of soul blood, then press a _super experience block_ into it with a Deployer.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.backtank.empty", "Empty: holds %s buckets of any fluid");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.backtank.holding", "%s: %s / %s mB");

        // ---- goggles
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.carcass_machine.output", "%1$s items waiting");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.carcass_machine.too_fast", "Works no faster above %1$s RPM");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.guillotine.winding", "Winding up: %1$s%%");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.guillotine.armed", "Armed: a redstone pulse drops the blade");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.roasting", "Roasting: %1$s%%");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.cooked", "Cooked: take it off");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.speed", "Cooking %1$sx as fast as by Hand Crank");
        // NeoForge's settings screen (Mods, Blood & Bones, Config)
        config("presentation", "Presentation", "How blood and gore look. Nothing here changes how the game plays.");
        config("bloodless_mode", "Bloodless mode", "Hides blood drops and stains, draws skinned carcasses pale, the machines and hooks clean and blood brown. A server can force it on for everyone with the bloodandbonesBloodless game rule.");
        config("rot", "Rot", "How carcasses rot and what becomes of them.");
        config("rot_speed", "Rot speed", "How fast carcasses rot: 1 is normal, 2 twice as fast, 0 never.");
        config("rotten_carcasses_crumble", "Rotten carcasses fall apart", "A carcass left rotten falls apart into bones and a little rotten flesh, so old ones do not pile up.");
        config("crumble_after_days", "Falls apart after (days)", "How long a rotten carcass lasts before it falls apart, in Minecraft days of 20 minutes, counted at the rot speed.");
        config("traits", "Traits", "The traits of carcass armour and minions.");
        config("trait_strength", "Trait strength", "Trait amounts (attribute changes, damage changes, heals, pushes, damage dealt) are multiplied by this: 1 is normal, 0 takes them away.");
        config("disabled_effect_types", "Switched-off effect types", "Trait effect types that do nothing on this server, by id, such as bloodandbones:teleport. Traits that use them keep their other effects.");
        BloodAndBones.REGISTRATE.addRawLang("gamerule.bloodandbonesBloodless", "Bloodless mode for everyone");
        BloodAndBones.REGISTRATE.addRawLang("gamerule.bloodandbonesBloodless.description",
                "Hides blood, gore and wet textures for every player, whatever their own setting. Carcasses and machines work the same.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.burnt", "Burnt to a crisp");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.no_heat", "Needs a fire below");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.carcass_piece.named", "%s %s");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.carcass_piece.fresh", "Fresh (%s%%)");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.carcass_piece.going_off", "Going off (%s%%)");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.carcass_piece.rotting", "Rotting (%s%%)");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.carcass_piece.skinned", "Skinned");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.carcass_piece.baby", "From a baby");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.carcass_piece.small", "From a small one");
        // a wreck's pieces do not rot, they corrode; skinned, they are stripped of their plating
        bloodless("item.bloodandbones.carcass_piece.fresh", "Sound (%s%%)");
        bloodless("item.bloodandbones.carcass_piece.going_off", "Worn (%s%%)");
        bloodless("item.bloodandbones.carcass_piece.rotting", "Corroded (%s%%)");
        bloodless("item.bloodandbones.carcass_piece.skinned", "Stripped");
        // and Create's filter asks after the same keeping
        bloodless("create.item_attributes.bloodandbones.fresh_piece", "is in sound condition");
        bloodless("create.item_attributes.bloodandbones.fresh_piece.inverted", "is not in sound condition");
        bloodless("create.item_attributes.bloodandbones.rotting_piece", "is corroded");
        bloodless("create.item_attributes.bloodandbones.rotting_piece.inverted", "is not corroded");
        bloodless("create.item_attributes.bloodandbones.skinned_piece", "is stripped");
        bloodless("create.item_attributes.bloodandbones.skinned_piece.inverted", "is not stripped");

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
        advancement("blood_diamond", "Priceless", "Steep a diamond in blood and experience");
        advancement("spit_roast", "Low and Slow", "Build a Spit Roast");
        advancement("specimen", "Curiosities", "Make a Specimen Jar to keep a piece of something on show");
        advancement("butcher_table", "Chop Shop", "Make a Butcher's Table to cut pieces up on");
        advancement("butcher_hook", "Hung Out to Dry", "Make a Butcher's Hook to hang your work on the wall");
        advancement("bloody_casing", "Redecorating", "Fill an Andesite Casing with blood");
        advancement("gut_chain", "Strung Out", "String offal into a Gut Chain");
        advancement("surgery_table", "Under the Knife", "Make a Surgery Table");
        advancement("severed", "Disarming", "Take off one of your own limbs on a Surgery Table");
        advancement("prosthetic", "Spare Parts", "Make a Peg Leg or a Hook Hand");
        advancement("backtank", "Tank Top", "Make a Fluid Backtank");
        advancement("organic", "Grown, Not Made", "Make an organic prosthetic");
        advancement("cybernetic", "More Machine Than Man", "Make a cybernetic");
        advancement("heart", "Heartless", "Hold your own heart");
        advancement("minion", "It's Alive!", "Stitch a minion together and wake it with blood");
        advancement("soul_netherite", "Thirty-Two Buckets", "Make a Soul Netherite Fluid Backtank");

        // ---- Ponder scenes (text_N in the order each scene shows its text)
        ponder("mangler", "Grinding Carcasses with the Mangler", "The Mangler tears each limb off a carcass and grinds it at once, then the body: the quickest way through a carcass. It keeps the least meat and bone, but gives armour scraps and the mob's own drops", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper", "The filter on its top edge picks which parts it takes: an Attribute Filter set to a part, such as a hind leg, or a spawn egg for one kind of mob");
        ponder("guillotine", "Taking Limbs Off with the Guillotine", "The Guillotine winds its blade up while it turns. A redstone pulse drops it through one limb, whole. It never takes the head", "It is driven by a shaft from below. The faster it turns, the faster it winds its blade up", "Wound up, it holds the blade at the top, armed. Only a redstone pulse drops it: a lever, a button or a clock beside it", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper", "The filter on its top edge picks which parts it takes: an Attribute Filter set to a part, such as a hind leg, or a spawn egg for one kind of mob");
        ponder("beheader", "Taking Heads with the Beheader", "The Beheader takes heads off as they come, one quick stroke each, under a line of hanging carcasses too. Zombies, skeletons, creepers and piglins sometimes leave their skull whole", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper", "The filter on its top edge picks which parts it takes: an Attribute Filter set to a part, such as a hind leg, or a spawn egg for one kind of mob");
        ponder("deglover", "Skinning with the Deglover", "The Deglover rolls the hide off a carcass whole, and a sheep's wool with it. It costs a lot of stress: turn it slowly", "It is driven by a shaft from below. Up to 32 RPM, the faster it turns, the faster it works; past that it works no faster", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper", "The filter on its top edge picks which parts it takes: an Attribute Filter set to a part, such as a hind leg, or a spawn egg for one kind of mob");
        ponder("bleeding_rack", "Draining Blood with the Bleeding Rack", "The Bleeding Rack is a drip tray with a tank for catching blood", "Hang a carcass on a Shackle Hook up to 8 blocks above it, and its blood drips into the tray", "An Encased Fan blowing across the body drains it up to four times faster", "Pipes can pull the blood from the rack's sides and bottom. When the rack is full, the carcass stops draining");
        ponder("butcher_hook", "Hanging Meat on the Butcher's Hook", "Bloody Casing: fill an Andesite Casing with 250 mB of blood from a Spout. It joins up like any casing", "A Butcher's Hook goes on the side of a solid block", "Right-click with any body part to hang it up: a carcass piece, a severed limb, an organ, scraps or meat. A carcass piece keeps there, like a piece in a Specimen Jar", "Take it down with an empty hand. If the block behind it is broken, the hook falls and drops the piece");
        ponder("butcher_table", "Chopping Pieces on the Butcher's Table", "Right-click the Butcher's Table with a carcass piece to lay it on the top", "Chop it with a Cleaver: it comes apart into meat, bone, offal and fat, spoiled as far as it had rotted. By hand you get about half", "A Deployer holding a Cleaver chops too, and gets all of it. A funnel or hopper can lay the pieces on the table", "The filter on the edge of its top picks which pieces it takes. A body too heavy to carry can be dragged onto it and chopped where it lies");
        ponder("spit_roast", "Roasting on the Spit Roast", "Set the Spit Roast over heat: a campfire, fire, lava or a Blaze Burner", "A shaft turns the spit, or a Hand Crank, slowly. It only roasts while it turns, and a fast shaft roasts up to eight times as fast", "Right-click with a carcass piece to skewer it. It browns as it cooks", "Take it off with an empty hand once cooked: it comes apart into cooked meat and bones. Leave it too long and it burns", "A whole carcass goes on too: right-click the spit with the Meat Hook while dragging one. It takes longer, and gives all its meat cooked");

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
                "The Mangler, Guillotine, Beheader and Deglover work any carcass lying on or hanging over them. Each takes a shaft from below; the faster it turns, the faster it works, but the Deglover no faster past 32 RPM, and the Guillotine only winds its blade up faster: a redstone pulse drops it. Set them flush in a floor so a body lies across them.",
                "Guillotine: limbs off. Beheader: heads off, sometimes a skull. Deglover: hides off. Mangler: everything, down to meat and bone. Take their output with a funnel or an empty hand.",
                "The filter slot on each machine's top edge picks what it works on: a spawn egg or a carcass piece for one kind of mob, or a Create list or attribute filter.");
        jei("display",
                "Show off your work. The Butcher's Hook hangs on the side of a solid block and takes any body part: a carcass piece, a severed limb, an organ, scraps, meat or a head. The Specimen Jar sits anywhere and holds one thing, any thing. A carcass piece keeps in either. Right-click with it, and with an empty hand to take it back.",
                "The Butcher's Table holds a piece too, lying on its top: right-click it with a Cleaver and it comes apart into meat, bone, offal and fat, spoiled as far as it had rotted.");
        jei("decoration",
                "Bloody Casing: fill an Andesite Casing with 250 mB of blood from a Spout. It joins up with its neighbours like Create's own casings. Brass, Copper and Train Casings take blood the same way. So does Create's cut calcite, 100 mB a block: a small stained palette of cut, polished, bricks and small bricks, with stairs and slabs from the stonecutter.",
                "Gut Chain: three pieces of offal in a column make three. It hangs and lies like a chain. Used on a Create chain conveyor's chain, it hangs a link from it that rides round with the chain; use more on the string to lengthen it, up to eight links, and hit it to take it down.",
                "Ribcage Arch: build two stacks facing each other; the top of each bends inward, and ribs hung in the air between them run level to close the arch. A row of arches makes the inside of a ribcage. Bone Pile: use it on a pile to add a layer; each layer drops two bones.");
        jei("morgue",
                "The Steel Table holds one item, any item, on its top. Tables side by side join into one run, with legs only where the run ends or turns. Funnels and hoppers can load it.",
                "The Steel Rack has two shelves of two places. Right-click its front with an item on the place you are looking at; an empty hand takes it back. Funnels and hoppers fill it from the lower left.");
        jei("surgery",
                "Amputation is a ritual: lay a Cleaver on the Surgery Table (with its Surgical Rig), have a minion with a hand awake beside it and set to Surgeon, and lie on the table (right-click with an empty hand) to have one of your own limbs, eyes or organs taken out. You get it back, with your name on it. Nothing takes a part any other way, and nothing can go wrong.",
                "The surgeon hacks: what it takes off leaves a ragged stump, and fitting anything but a crude prosthetic there later takes blood as well (from buckets and a Fluid Backtank you carry, added together): a bucket after a fit surgeon (a villager's or a pillager's head), two or three after a poorer one. The screen shows the price before the cut. Swap an implant straight in for a part of flesh and there is no stump at all.",
                "A missing arm means no off-hand and slower swings, a missing leg no sprinting, a missing eye less to see by. Lay an implant or a part on the table and lie down again to fit it; an implant unclips with nothing on the table. Fitting never needs a surgeon, and a crude prosthetic never needs blood, so one can always go on.");
        bloodless("bloodandbones.jei.surgery.1",
                "Replacement is a procedure: lay a Cleaver on the Surgery Table (with its Surgical Rig), have a construct with a hand awake beside it and set to Surgeon, and lie on the table (right-click with an empty hand) to have one of your own limbs, eyes or organs removed. You get it back, with your name on it. Nothing removes a part any other way, and nothing can go wrong.");
        bloodless("bloodandbones.jei.surgery.2",
                "The surgeon works roughly: what it removes leaves an open socket, and fitting anything but a crude prosthetic there later takes essence as well (from buckets and a Fluid Backtank you carry, added together): a bucket after a fit surgeon (a villager's or a pillager's head), two or three after a poorer one. The screen shows the price first. Swap an implant straight in for a part and there is no open socket at all.");
        bloodless("bloodandbones.jei.surgery.3",
                "A missing arm means no off-hand and slower swings, a missing leg no sprinting, a missing eye less to see by. Lay an implant or a part on the table and lie down again to fit it; an implant unclips with nothing on the table. Fitting never needs a surgeon, and a crude prosthetic never needs essence, so one can always go on.");
        jei("implants",
                "Basic prosthetics (Peg Leg, Hook Hand) need nothing. Organic ones (Flesh Arm, Sinew Leg, Furnace Stomach) run on blood and cybernetics (Hydraulic Arm, Piston Leg, Optic Eye, Pump Heart, Bellows Lungs) on soul blood, from a worn Fluid Backtank, a mB or two a second. The Vent Arm runs on whatever the tank holds; the Port Arm needs nothing.",
                "The gauge by the hotbar, where Create shows a backtank's air, reads the worn tank whenever one is on or a powered implant is fitted: how much it holds, a bar of the fluid itself, and under it the implants it runs, dimmed when their fuel is not in the tank.",
                "When the tank runs out of their fluid they stop working, as if the part were missing, until it is filled again. The Vent Arm sprays the tank (hold use, empty-handed); the Port Arm plugs the tank into pipes at a Backtank Port.");
        bloodless("bloodandbones.jei.implants.1", "Basic prosthetics (Peg Leg, Hook Hand) need nothing. Plated ones (Plated Arm, Cabled Leg, Furnace Hopper) run on essence and cybernetics (Hydraulic Arm, Piston Leg, Optic Eye, Pump Heart, Bellows Lungs) on soul essence, from a worn Fluid Backtank, a mB or two a second. The Vent Arm runs on whatever the tank holds; the Port Arm needs nothing.");
        jei("backtank",
                "The Fluid Backtank holds any fluid, worn in the chest slot with the armour of its tier: copper 2 buckets, gold 3, iron 4, diamond 6, blood steel 8, blood diamond 16, soul netherite 32.",
                "Right-click a block to set it down; pipes fill or empty it from any side, and it keeps its fluid when broken. A Spout fills it and an Item Drain empties it in the hand. The soul netherite tank is a smithing upgrade of the blood diamond one.",
                "Crafted with a carcass chestplate, it straps onto the chestplate's back, fluid and all: worn, it is your tank, and the chestplate has the better armour of the two. Craft the chestplate alone to take the tank off again.");
        bloodless("bloodandbones.jei.backtank.3",
                "Crafted with a plated chestplate, it straps onto the chestplate's back, fluid and all: worn, it is your tank, and the chestplate has the better armour of the two. Craft the chestplate alone to take the tank off again.");
        jei("carcass_armour",
                "Carcass armour is made of scraps from the Mangler. Five head scraps of one mob make a helmet, four leg scraps boots. A chestplate is six torso scraps with two arm scraps of any one mob on top (the shoulders); leggings are four leg scraps with three tail scraps of any one mob, or more legs, on top (the hips). A mob with no bone of a kind uses its torso scraps there.",
                "Craft a piece with one thing to fit it: hides of one mob (one for a helmet or boots, two for leggings, three for a chestplate) for its hide traits; an organ taken out of a mob on the Surgery Table (a heart, eyes, a Gland, a rabbit's foot: each says which pieces take it, eyes a helmet, a heart a chestplate) for its organ traits. What it replaces comes back.",
                "Tiers, in order: a Blood Steel Ingot, then a Blood Diamond, then a Soul Netherite Ingot, each giving more armour, toughness and durability than the last. Mechanical Crafters fit hides, organs and tiers too, but only where nothing comes back out.");
        bloodless("bloodandbones.jei.carcass_armour.1",
                "Plated armour is made of salvage from the Mangler. Five pieces of head salvage from one mob make a helmet, four of leg salvage boots. A chestplate is six of torso salvage with two of arm salvage from any one mob on top (the shoulders); leggings are four of leg salvage with three of tail salvage from any one mob, or more legs, on top (the hips). A mob with no bone of a kind uses its torso salvage there.");
        bloodless("bloodandbones.jei.carcass_armour.2",
                "Craft a piece with one thing to fit it: coverings of one mob (one for a helmet or boots, two for leggings, three for a chestplate) for its covering traits; a core taken out of a mob on the Surgery Table (a pump, lenses, a lucky charm: each says which pieces take it, lenses a helmet, a pump a chestplate) for its core traits. What it replaces comes back.");
        bloodless("bloodandbones.jei.carcass_armour.3",
                "Tiers, in order: an Essence Steel Ingot, then an Essence Diamond, then a Soul Netherite Ingot, each giving more armour, toughness and durability than the last. Mechanical Crafters fit coverings, cores and tiers too, but only where nothing comes back out.");
        jei("minions",
                "Fit an Assembly Frame to a Surgery Table and lay a carcass torso on it, or take a whole carcass lying on it with an empty hand (what is still attached comes along). Stitch on heads, legs, arms and tails of any mob, one a click: a cow on rabbit legs is a cow that hops. A Cleaver takes the last piece back. Wake it with a bucket of blood.",
                "Every part does its own thing. The torso sets its size, health and how much it carries; the head its sight, its bite and its knacks; the legs how fast and how it moves; arms its blows and what it holds with. Any minion can take any task, some far better than others. Crouch and R-Click it with an empty hand for its tasks: how well it does each and why, where it works and how far it reaches.",
                "It runs on blood: a little all the time, more moving, working and fighting. Low, it walks to a Blood Trough to drink. Empty, it lies down where it is, alive, and nothing but a player can hurt it; give it blood and it gets up. Crouch-R-Click one lying down a few times to fold it up and carry it; set down, it works from there. Its maker's Cleaver on one lying down on an Assembly Frame table takes it back apart into a frame there.");
        jei("soul_blood",
                "Soul Blood is blood with a soul in it, and it takes a line of machines to make in bulk. First a Basin Lid on a Basin of blood sets 250 mB of it into Congealed Blood. Then an Encased Fan blowing through soul fire haunts it into a Soul Clot. Last, a Mechanical Mixer over a superheated Basin melts the clot back into 200 mB of Soul Blood.",
                "There are two quicker ways, both far poorer: mix a bucket of blood with soul sand and 100 mB of liquid experience over a superheated Blaze Burner, or put nether wart and soul soil in with the blood under the Basin Lid. Each gives back only 100 mB. Nether mobs hung over a Bleeding Rack bleed a little Soul Blood straight away.");
    }

    /** Organs as items (the Gland), their words, the Body Parts pages and the traits commands. */
    private static void organs() {
        item("gland",
                "A special _organ_ cut out of a mob on the Surgery Table: a sac, a gland, a bulb, a core. Each mob has its own, and what it does comes from that mob.",
                "In Carcass Armour", "Craft it with a piece of _carcass armour_ it fits (it says which) to add its mob's _organ traits_. A new organ replaces the old, which comes back.",
                "In a Minion", "R-Click a minion being built on the _Assembly Frame_ with it: its _one special_. A new one replaces the old, which comes back.");
        bloodless("item.bloodandbones.gland", "Core");
        bloodless("item.bloodandbones.gland.tooltip.summary",
                "A special _core_ taken out of a mob on the Surgery Table: a cell, a filter, a press, a tank. Each mob has its own, and what it does comes from that mob.");
        bloodless("item.bloodandbones.gland.tooltip.condition1", "In Plated Armour");
        bloodless("item.bloodandbones.gland.tooltip.behaviour1",
                "Craft it with a piece of _plated armour_ it fits (it says which) to add its mob's _core traits_. A new core replaces the old, which comes back.");
        bloodless("item.bloodandbones.gland.tooltip.condition2", "In a Construct");
        bloodless("item.bloodandbones.gland.tooltip.behaviour2",
                "R-Click a construct being built on the _Assembly Frame_ with it: its _one special_. A new one replaces the old, which comes back.");
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones.gland.of", "%s's %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.organ.of", "Cut out of: %s");
        bloodless("bloodandbones.organ.of", "Taken from: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.organ.armour", "In armour (%s): ");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.organ.minion", "In a minion: ");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.organ.nothing", "nothing");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.organ.inside", "Organs still in it: %s");
        bloodless("bloodandbones.organ.inside", "Cores still in it: %s");
        // the parts of a body by their data keys, for the traits commands and the Body Parts pages
        for (String[] part : new String[][]{{"torso", "Torso"}, {"torso_ext", "Torso extension"}, {"neck", "Neck"}, {"head", "Head"}, {"arm", "Arm"},
                {"leg", "Leg"}, {"tail", "Tail"}, {"extra", "Decoration"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.part_key." + part[0], part[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.title", "%s (%s): what its parts do");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.layers", "Layers: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.material", "Scrap material: %s");
        bloodless("bloodandbones.command.explain.material", "Salvage material: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.part", "%s: minion %s; armour %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.hide", "Hide: %s");
        bloodless("bloodandbones.command.explain.hide", "Covering: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.organs", "%s holds: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.organ", "%s: minion %s; armour (%s) %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.set", "Full set %s: bonus %s; drawback %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.variant", "One whose carcass kept %s adds:");
        bloodless("bloodandbones.command.explain.variant", "One whose body kept %s adds:");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.explain.nothing", "nothing");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.dump.done", "Wrote %s traits of %s mobs to %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.dump.failed", "Could not write the traits: %s");
        // /bloodandbones minion fitness (docs/NEXT.md 1.4)
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.minion.title", "%s, now %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.minion.raw", "(%s before it was held)");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.minion.none", "Look at a minion within 16 blocks to see how fit it is at each task");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.command.minion.not_yours", "Only its maker, or an operator, may see that");
        // JEI's Body Parts pages and the organ fitting
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.category.body_parts", "Body Parts");
        bloodless("bloodandbones.jei.category.body_parts", "Parts and Cores");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.organs", "Organs");
        bloodless("bloodandbones.jei.body_parts.organs", "Cores");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.minion", " As a minion part: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.armour", " In armour: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.armour_piece", " In its %s: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.hide_line", "Hide: %s");
        bloodless("bloodandbones.jei.body_parts.hide_line", "Covering: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.organ_line", "%s (%s): ");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.organ_minion", " In a minion: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.organ_armour", " In armour (%s): %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.set", "Full set: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.set_traits", " %s; drawback %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.jei.body_parts.nothing", "nothing");
        jei("gland",
                "Every mob has a special organ, some several: a creeper's powder sac, a cow's rumen, a blaze's core, a skeleton's marrow. Lay a carcass piece on a Surgery Table with its Surgical Rig and R-Click with a Cleaver: out come its organs, one a cut, each named for its mob. A carcass too heavy to carry gives them up lying over the table. Some bring more with them (a powder sac spills gunpowder).",
                "Fit one into a piece of carcass armour in a crafting grid (each says which pieces take it), or into a minion on the Assembly Frame. What it does there comes from its mob: the Body Parts page of each mob lists them.");
        bloodless("bloodandbones.jei.gland.1",
                "Every mob has a special core, some several: a creeper's powder core, a cow's fermenter, a blaze's core, a skeleton's lean core. Lay a part on a Surgery Table with its Surgical Rig and R-Click with a Cleaver: out come its cores, one a click, each named for its mob. A body too heavy to carry gives them up lying over the table. Some bring more with them (a powder core spills gunpowder).");
        bloodless("bloodandbones.jei.gland.2",
                "Install one into a piece of plated armour in a crafting grid (each says which pieces take it), or into a construct on the Assembly Frame. What it does there comes from its mob: the Parts and Cores page of each mob lists them.");
    }

    private static void config(String key, String name, String tooltip) {
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.configuration." + key, name);
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.configuration." + key + ".tooltip", tooltip);
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

    /** Bloodless mode's own wording for a key, where the general rewording ({@code BloodlessWords}) would not fit what is drawn. */
    public static void bloodless(String key, String text) {
        BloodAndBones.REGISTRATE.addRawLang("bloodless." + key, text);
    }

    /** A line of text under its own key; bloodless mode rewords it with {@code BloodlessWords} unless it has its own. */
    public static void raw(String key, String text) {
        BloodAndBones.REGISTRATE.addRawLang(key, text);
    }

    /**
     * A trait's name ({@code trait.bloodandbones.<id>}, what its file's "name" points at) and what it does
     * ({@code trait.bloodandbones.<id>.desc}, shown on armour while Ctrl is held).
     */
    public static void trait(String id, String name, String description) {
        BloodAndBones.REGISTRATE.addRawLang("trait.bloodandbones." + id, name);
        BloodAndBones.REGISTRATE.addRawLang("trait.bloodandbones." + id + ".desc", description);
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
