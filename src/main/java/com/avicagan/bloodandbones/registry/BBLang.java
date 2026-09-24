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
                "A diamond steeped in _soul blood_. Fill a diamond with _1000 mB_ of soul blood in a Spout.");

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
                "A hook for a wall, to hang a carcass piece on for show.",
                "When R-Clicked with a Carcass Piece", "Hangs it on the hook. It _keeps_ there. R-Click with an _empty hand_ to take it down.");
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
                "With a Carcass Piece on It", "R-Click with a _Cleaver_ to take its _organs_ out, one a cut: a body's heart, lungs and stomach, a head's eyes.",
                "Building a Minion", "With an _Assembly Frame_ fitted, lay a carcass _torso_ on it (or R-Click with an empty hand to take a whole carcass lying on it), then R-Click with carcass _heads_, _legs_, _arms_ and _tails_ of any mob to stitch them on. A _Cleaver_ takes the last back off; a bucket of _blood_ wakes it.");
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
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.action.replace", "Swap in what is on the table");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.action.swap", "Swap it for what is on the table");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.dead", "%s (dry)");
        // ---- minions (docs/PARTS-AND-TRAITS.md section 6)
        for (String[] job : new String[][]{{"companion", "Companion"}, {"courier", "Courier"}, {"farmer", "Farmer"}, {"bodyguard", "Bodyguard"},
                {"guard", "Guard"}, {"herder", "Herder"}, {"surgeon", "Surgeon"}}) {
            BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.job." + job[0], job[1]);
        }
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.job_now", "Job: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.status", "%s, blood %s of %s mB");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.status_down", "%s, out of blood (%s of %s mB): give it blood to wake it");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.frame_stats", "%s health, speed %s, %s; %s of %s sockets filled");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.no_fit", "That doesn't go on a minion");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.needs_hide", "A flesh minion takes pieces with their hide still on");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.needs_skinned", "A brass minion takes only skinned pieces");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.no_socket", "There is nowhere left on it for that");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.too_big", "It would be too big to get up");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.too_rotten", "The body has gone off too far to wake");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.cap", "You have as many minions as this world allows");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.dormant", "Folded up, out of blood. Set it down and give it blood to wake it.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.left_arm", "Left arm");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.right_arm", "Right arm");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.left_leg", "Left leg");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.body.right_leg", "Right leg");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.title", "Surgery");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.bare", "Fit a Surgical Rig to operate, or an Assembly Frame to build minions");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.minion.lay_body", "Lay a carcass torso on the frame to build a minion on it");
        item("surgical_rig",
                "A Surgery Table _attachment_: an overhead arm of lamps, clamps and blades. Makes the table a place to _operate_.",
                "When R-Clicked on a Surgery Table", "Fits it (swapping out any other attachment). Then patients can lie on the table, and carcass pieces give up their _organs_.",
                "When Sneak-R-Clicked off an Empty Table", "An empty hand takes it back off.");
        item("assembly_frame",
                "A Surgery Table _attachment_: clamps and a jig for stitching bodies together. Makes the table a place to build _minions_.",
                "When R-Clicked on a Surgery Table", "Fits it (swapping out any other attachment). Then a carcass _torso_ laid on it can be built into a minion, with the heads, legs and arms of _any_ mob.",
                "When Sneak-R-Clicked off an Empty Table", "An empty hand takes it back off.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.title_other", "Surgery on %s");
        block("blood_trough",
                "A trough of _blood_ for flesh minions: they walk to the nearest one they can reach and drink when they run low.",
                "When Filled", "Takes _four buckets_ of any blood, from a bucket, a _Spout_ or _pipes_ on any side.");
        item("dormant_minion",
                "A minion that ran out of blood, _folded up_ to carry. It keeps everything: what it is built of, its job, what it carries.",
                "When Used on a Block", "Sets it down there, still out of blood. Give it _blood_ to wake it.");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.no_organs", "Nothing more to take out of it");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.empty", "Nothing on the table: lay a Cleaver, a prosthetic or a limb on it");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.on_table", "On the table: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.natural", "Your own");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.missing", "Missing");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.state.ragged", "Ragged stump");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.needs_surgeon", "Needs a surgeon");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.needs_blood", "Needs a bucket of blood");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.action.none", "-");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.action.take_off", "Take it off");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.action.fit", "Fit what is on the table");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.action.reattach", "Put the limb back on");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.action.unclip", "Unclip it");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.done", "Done");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.action.fit_module", "Fit the module");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.surgery.action.take_module", "Take a module out");

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
                "In a Crafting Grid", "Scraps of one mob make _carcass armour_: head scraps a helmet, leg scraps leggings and boots. The mob decides the armour's material and its _traits_.");
        for (String piece : new String[]{"helmet", "leggings", "boots", "chestplate"}) {
            item("carcass_" + piece,
                    "Armour of _scraps_ from the Mangler. Its _material_ comes from the family of the mob it was made of, its _traits_ from that mob's parts.",
                    "When Worn", "Every mob's parts do something different: _mix_ them freely. A full set from _one_ mob adds that mob's bonus, and its drawback.");
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
        BloodAndBones.REGISTRATE.addRawLang("scrap_material.bloodandbones.hide_plate", "Hide");
        BloodAndBones.REGISTRATE.addRawLang("scrap_material.bloodandbones.sinew", "Sinew");
        BloodAndBones.REGISTRATE.addRawLang("scrap_material.bloodandbones.gristle", "Gristle");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.body", "Made from: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.shoulders", "Shoulders: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.hips", "Hips: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.hide", "Hide: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodless.bloodandbones.carcass_armour.hide", "Covering: %s");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.carcass_armour.full_set", "A full set of one mob: %s");
        BloodAndBones.REGISTRATE.addRawLang("set.bloodandbones.beast", "Beast");
        BloodAndBones.REGISTRATE.addRawLang("set.bloodandbones.herd_beast", "Herd Beast");
        BloodAndBones.REGISTRATE.addRawLang("set.bloodandbones.warren", "Warren");
        BloodAndBones.REGISTRATE.addRawLang("set.bloodandbones.pure", "Pure Set");
        BloodAndBones.REGISTRATE.addRawLang("attribute.bloodandbones.drag_strength", "Drag Strength");
        String[][] traits = {
                {"hardy", "Hardy"}, {"frail", "Frail"}, {"swift", "Swift"}, {"sluggish", "Sluggish"}, {"steady", "Steady"}, {"sturdy", "Sturdy"},
                {"barrel_chest", "Barrel Chest"}, {"hooves", "Hooves"}, {"thick_hide", "Thick Hide"}, {"hauler", "Hauler"}, {"meek", "Meek"},
                {"springy", "Springy"}, {"light_boned", "Light-Boned"}, {"fall_guard", "Fall Guard"}, {"cud_chewer", "Cud Chewer"},
                {"alert", "Alert"}, {"prey", "Prey"}};
        for (String[] trait : traits) {
            BloodAndBones.REGISTRATE.addRawLang("trait.bloodandbones." + trait[0], trait[1]);
        }
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
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.roasting", "Roasting: %1$s%%");
        BloodAndBones.REGISTRATE.addRawLang("bloodandbones.gui.goggles.spit_roast.cooked", "Cooked: take it off");
        // NeoForge's settings screen (Mods, Blood & Bones, Config)
        config("presentation", "Presentation", "How blood and gore look. Nothing here changes how the game plays.");
        config("bloodless_mode", "Bloodless mode", "Hides blood drops and stains, draws skinned carcasses pale, the machines and hooks clean and blood brown. A server can force it on for everyone with the bloodandbonesBloodless game rule.");
        config("rot", "Rot", "How carcasses rot and what becomes of them.");
        config("rot_speed", "Rot speed", "How fast carcasses rot: 1 is normal, 2 twice as fast, 0 never.");
        config("rotten_carcasses_crumble", "Rotten carcasses fall apart", "A carcass left rotten falls apart into bones and a little rotten flesh, so old ones do not pile up.");
        config("crumble_after_days", "Falls apart after (days)", "How long a rotten carcass lasts before it falls apart, in Minecraft days of 20 minutes, counted at the rot speed.");
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
        ponder("mangler", "Grinding Carcasses with the Mangler", "The Mangler tears the limbs off a carcass, then grinds every piece into meat, bone, offal and fat", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper");
        ponder("guillotine", "Taking Limbs Off with the Guillotine", "The Guillotine takes the nearest limb off a carcass in one stroke. It never takes the head", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper");
        ponder("beheader", "Taking Heads with the Beheader", "The Beheader takes heads off. Zombies, skeletons, creepers and piglins sometimes leave their skull whole", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper");
        ponder("deglover", "Skinning with the Deglover", "The Deglover strips the hide off a carcass, and a sheep's wool with it", "It is driven by a shaft from below. The faster it turns, the faster it works", "It works any carcass lying on it or hanging over it. Set it flush in a floor so a body lies across it", "What it makes waits inside. Take it with an empty hand, or pull it out with a funnel or hopper");
        ponder("bleeding_rack", "Draining Blood with the Bleeding Rack", "The Bleeding Rack is a drip tray with a tank for catching blood", "Hang a carcass on a Shackle Hook up to 8 blocks above it, and its blood drips into the tray", "An Encased Fan blowing across the body drains it up to four times faster", "Pipes can pull the blood from the rack's sides and bottom. When the rack is full, the carcass stops draining");
        ponder("butcher_hook", "Hanging Meat on the Butcher's Hook", "Bloody Casing: fill an Andesite Casing with 250 mB of blood from a Spout. It joins up like any casing", "A Butcher's Hook goes on the side of a solid block", "Right-click with a carcass piece to hang it up. It keeps there, like a piece in a Specimen Jar", "Take it down with an empty hand. If the block behind it is broken, the hook falls and drops the piece");
        ponder("butcher_table", "Chopping Pieces on the Butcher's Table", "Right-click the Butcher's Table with a carcass piece to lay it on the top", "Chop it with a Cleaver: it comes apart into meat, bone, offal and fat, spoiled as far as it had rotted", "A Deployer holding a Cleaver chops too. A funnel or hopper can lay the pieces on the table");
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
                "Guillotine: limbs off. Beheader: heads off, sometimes a skull. Deglover: hides off. Mangler: everything, down to meat and bone. Take their output with a funnel or an empty hand.",
                "The filter slot on each machine's top edge picks what it works on: a spawn egg or a carcass piece for one kind of mob, or a Create list or attribute filter.");
        jei("display",
                "Show off your work. The Butcher's Hook hangs on the side of a solid block and the Specimen Jar sits anywhere; either holds one carcass piece, which keeps there. Right-click with the piece, and with an empty hand to take it back.",
                "The Butcher's Table holds a piece too, lying on its top: right-click it with a Cleaver and it comes apart into meat, bone, offal and fat, spoiled as far as it had rotted.");
        jei("decoration",
                "Bloody Casing: fill an Andesite Casing with 250 mB of blood from a Spout. It joins up with its neighbours like Create's own casings. Brass and Copper Casings take blood the same way.",
                "Gut Chain: three pieces of offal in a column make three. It hangs and lies like a chain. Used on a Create chain conveyor's chain, it hangs a link from it that rides round with the chain; use more on the string to lengthen it, up to eight links, and hit it to take it down.",
                "Ribcage Arch: build two stacks facing each other; the top of each bends inward, and ribs hung in the air between them run level to close the arch. A row of arches makes the inside of a ribcage. Bone Pile: use it on a pile to add a layer; each layer drops two bones.");
        jei("morgue",
                "The Steel Table holds one item, any item, on its top. Tables side by side join into one run, with legs only where the run ends or turns. Funnels and hoppers can load it.",
                "The Steel Rack has two shelves of two places. Right-click its front with an item on the place you are looking at; an empty hand takes it back. Funnels and hoppers fill it from the lower left.");
        jei("surgery",
                "Amputation is a ritual: lay a Cleaver on the Surgery Table (with its Surgical Rig), have a surgeon minion (one with a villager's or a pillager's head, and an arm) awake beside it, and lie on the table (right-click with an empty hand) to have one of your own limbs, eyes or organs taken out. You get it back, with your name on it. Nothing takes a part any other way, and nothing can go wrong.",
                "The surgeon hacks: what it takes off leaves a ragged stump, and fitting anything there later takes a bucket of blood as well (from a bucket or a Fluid Backtank you carry). Swap an implant straight in for a part of flesh and there is no stump at all.",
                "A missing arm means no off-hand and slower swings, a missing leg no sprinting, a missing eye less to see by. Lay an implant or a part on the table and lie down again to fit it; an implant unclips with nothing on the table. Fitting never needs a surgeon, so a crude prosthetic can always go on.");
        jei("implants",
                "Basic prosthetics (Peg Leg, Hook Hand) need nothing. Organic ones (Flesh Arm, Sinew Leg, Furnace Stomach) run on blood and cybernetics (Hydraulic Arm, Piston Leg, Optic Eye, Pump Heart, Bellows Lungs) on soul blood, from a worn Fluid Backtank, a mB or two a second. The Vent Arm runs on whatever the tank holds; the Port Arm needs nothing.",
                "When the tank runs out of their fluid they stop working, as if the part were missing, until it is filled again. The Vent Arm sprays the tank (hold use, empty-handed); the Port Arm plugs the tank into pipes at a Backtank Port.");
        jei("backtank",
                "The Fluid Backtank holds any fluid, worn in the chest slot with the armour of its tier: copper 2 buckets, gold 3, iron 4, diamond 6, blood steel 8, blood diamond 16, soul netherite 32.",
                "Right-click a block to set it down; pipes fill or empty it from any side, and it keeps its fluid when broken. A Spout fills it and an Item Drain empties it in the hand. The soul netherite tank is a smithing upgrade of the blood diamond one.");
        jei("minions",
                "Fit an Assembly Frame to a Surgery Table and lay a carcass torso on it, or take a whole carcass lying on it with an empty hand (what is still attached comes along). Stitch on heads, legs, arms and tails of any mob, one a click: a cow on rabbit legs is a cow that hops. A Cleaver takes the last piece back. Wake it with a bucket of blood.",
                "Every part does its own thing. The torso sets its size, health and how much it carries; the head its jobs and its bite; the legs how fast and how it moves; arms its blows. Crouch and R-Click it with an empty hand to change its job.",
                "It runs on blood: a little all the time, more moving, working and fighting. Low, it walks to a Blood Trough to drink. Empty, it lies down where it is, alive, and nothing but a player can hurt it; give it blood and it gets up. Crouch-R-Click one lying down a few times to fold it up and carry it.");
        jei("soul_blood",
                "Soul Blood is blood with a soul in it. Mix blood, soul sand and a little liquid experience over a superheated Blaze Burner, or ferment blood with nether wart and soul soil under a Basin Lid.");
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
    private static void bloodless(String key, String text) {
        BloodAndBones.REGISTRATE.addRawLang("bloodless." + key, text);
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
