# Create: Blood & Bones

A [Create](https://github.com/Creators-of-Create/Create) addon for Minecraft 1.21.1 / NeoForge:
mob carcass butchery, blood as a processable fluid, body-horror progression and gore-themed
decoration, built on Create's kinetic, fluid and contraption systems, with ragdoll physics
provided by [Sable](https://github.com/ryanhcode/sable).

- Mod id: `bloodandbones`
- Target: Minecraft 1.21.1, NeoForge 21.1.x, Create 6.0.x, Sable 2.0.x, Java 21
- Design: the brief is `docs/DESIGN-BRIEF.md`; decisions and implementation notes are in
  `docs/ARCHITECTURE-PROPOSAL.md`

## What it does

- **The Meat Hook.** Kill a mob with it and the body stays behind as a ragdoll carcass (Sable
  physics, one body per limb, jointed) instead of dropping loot. Its gear still drops. Hook a limb
  to drag the body; heavier animals slow you more. 79 vanilla mobs are rigged, with their colours
  and variants (all but the ender dragon and tropical fish). Babies of every kind that has them
  (36) leave carcasses shaped like the baby: a calf's big head on a small body, a foal's long legs.
  Big slimes and magma cubes leave carcasses, and so do the smallest ones.
- **Butchery.** The Cleaver takes limbs off at the joint, leaving raw wounds with the bone
  showing on both ends, and breaks loose pieces down into meat, bone, offal and fat, with scraps of
  meat flying. Blades come away bloody and stay that way for a few minutes. The Flensing
  Knife takes the hide off (with a sheep's wool). Yields are data-driven per mob and spoil as the
  carcass rots. Light pieces can be picked up and carried, and chopped up on a Butcher's Table
  (by hand, or by a Deployer holding a Cleaver, fed by a funnel). Carcasses land with a wet thud.
- **Rot.** Carcasses rot over game time; cold slows it, ice stops it. Flies gather as the meat
  goes off, and maggots crawl over it near the end. Rotten meat turns to rotten flesh, and a
  carcass left rotten for a day falls apart into bones and rotten flesh, so old ones don't pile up.
  Rot speed and the falling apart can be changed per world in
  `serverconfig/bloodandbones-server.toml`, or in game under Mods, Blood & Bones, Config.
- **Hanging and blood.** The Shackle Hook hangs a carcass by the neck. A Bleeding Rack under a
  hanging (or lying) carcass collects its blood, faster with an Encased Fan blowing across it.
  Blood and Soul Blood are real fluids for pipes, tanks, spouts and basins; nether mobs
  (piglins, hoglins, striders) bleed Soul Blood, dark teal drops and stains included. A bled carcass keeps longer. Blood that nothing
  catches stains the ground: kills, cuts, drag trails and carcasses hanging over bare floor leave
  splashes that dry dark and wash away in the rain.
- **Chain conveyors.** Right-click a Create chain conveyor with the Meat Hook while dragging a
  carcass: it rides the chain on a trolley and stops at frogports addressed for it. Trolleys queue
  behind each other instead of bunching up.
- **Machines** (shaft from below, stress 4 to 8 per RPM): the Mangler tears a carcass apart and
  grinds it down; the Guillotine takes limbs off; the Beheader takes heads (sometimes the skull);
  the Deglover strips hides. Each works whatever carcass lies on or hangs over it, and a filter slot
  on its top edge can limit it to one kind of mob.
- **Materials.** Blood Steel (spout-fill iron with blood), Soul Blood (blood set under a Diesel
  Generators Basin Lid, haunted by a fan through soul fire, melted back in a superheated mixer; or a
  tenth as much in one step), the Blood Diamond (a sequenced assembly: a Spout of a bucket of blood,
  then one of liquid experience), and the Blood Steel Cleaver (twice as deep a chop).
- **Sorting.** Create's Attribute Filter knows carcass pieces: which mob, which part (head, body,
  limb, tail), fresh or rotting, skinned, or from a baby, so funnels and frogports can sort meat. The
  machines have a filter slot too: a spawn egg, a piece or a filter picks the carcasses they work on.
- **Cooking and display.** The Spit Roast turns a carcass piece over a fire until it browns (or
  burns). The Specimen Jar keeps a piece on show, and so does the Butcher's Hook, on a wall, where
  a fresh piece drips blood on the floor. The Bloody Casing (spout 250 mB of blood onto an Andesite
  Casing, and the same for Brass and Copper Casing) joins up like Create's casings, and the Gut Chain
  (three offal in a column) hangs like a chain, and rides Create's chain conveyors when used on one.
  The morgue is cold steel: Steel Tables join into one run and hold an item each, and a Steel Rack
  shows four parts on its shelves. Ribcage Arches shape themselves into the inside of a ribcage, and
  Bone Piles heap up in layers like snow.
- **The body.** Lie on the Surgery Table and choose what to do to each part of you: arms, legs,
  eyes, heart, lungs and stomach. A Cleaver on the table takes one out (you keep it, with your name on
  it), but only with a surgeon minion (a villager's or pillager's head) beside the table, and its ragged
  stump takes a bucket of blood to fit later; an implant or a part goes where one is missing, or swaps
  straight in. Nothing takes a part any
  other way, and surgery cannot go wrong. As the design brief has it, a missing part is an interesting
  penalty, not a health shave: an arm gone, no off-hand and slower swings; a leg gone, no sprinting; an
  eye gone, fog closing in; the heart is only ever swapped. Crude prosthetics (Peg Leg, Hook Hand, Glass
  Eye, Crude Heart, Lungs and Stomach: iron, leather and bone) give back normal working and nothing more; organic ones (Flesh
  Arm, Sinew Leg, Furnace Stomach) on blood and cybernetics (Hydraulic Arm, Piston Leg, Optic Eye, Pump
  Heart, Bellows Lungs) on soul blood from the backtank, and stop working when it runs dry. The Vent Arm
  sprays whatever the tank holds (lava burns, water douses, experience gives experience); the Port Arm
  plugs the tank into pipes at a Backtank Port. Everyone sees what you are missing and what is fitted.
  The table takes other patients too: another player, a mob led onto it on a lead, or a carcass piece
  (or a carcass too heavy to carry, lying over it) whose organs a Cleaver (or a Deployer holding one)
  takes out one a cut: its heart, lungs and stomach, its eyes, and its own special organs, a creeper's
  powder sac, a cow's rumen, a sniffer's olfactory bulb, a skeleton's marrow. Each fits carcass armour or
  a minion for that mob's ability.
- **Minions.** Stitched together from carcass pieces on the Surgery Table (with its Assembly Frame): a
  torso, then the heads, legs, arms and tails of any mob. Every piece does its own thing: the torso sets
  size, health and how much it carries, the head its sight, bite and knacks, the legs speed and how it moves, so a cow on rabbit legs
  hops, spider legs climb, strider legs walk across lava, drowned legs walk the seabed, horse legs can be
  saddled and ridden (a pig's head steered with a carrot on a stick, a camel carrying two), wings strong
  enough fly it, and arms hit in their own styles. Any of sixteen tasks can go to any minion, and what it
  is built of makes it better or worse at each (a villager's head is a born surgeon and good at its trade's
  task; paws pick poorly; eight arms work fast): guard, sentry, hunter, sapper (with a creeper's powder sac:
  it walks up to a monster and blows itself up, then lies powered down), surgeon, medic, herder, tender,
  courier, hauler, farmer, fisher, butcher, barterer and digger, worked with whatever you hand it and drawn
  holding it. Crouch and use an empty hand on it for its task screen. Woken
  with a bucket of blood, it runs on blood and drinks from a Blood Trough; run dry, it lies down alive
  until it gets more (a Tender brings it some), and is never destroyed by neglect. Built of skinned pieces under Brass Sheathing,
  it is a brass minion instead: woken and kept going on Soul Canisters that a Charging Cradle swaps in
  (Mechanical Arms and funnels keep the cradle stocked), with a cybernetic module of its own and a filter
  slot that takes a Create filter.
- **The Fluid Backtank.** A tank for any fluid, worn in the chest slot with the armour of its tier:
  copper 2 buckets, gold 3, iron 4, diamond 6, blood steel 8, blood diamond 16, and soul netherite 32
  (a smithing upgrade with a Soul Netherite Ingot: a netherite ingot filled with soul blood and pressed
  with a super experience block). Set it down and pipes fill or empty it; a Spout or Item Drain works
  on it in the hand. It is drawn on the wearer's back. Prosthetics and cybernetics will run on it.
- **Bloodless mode.** `bloodless_mode` in the client config (`config/bloodandbones-client.toml`)
  hides blood drops and stains, shows skinned carcasses as pale meat, the hook in a carcass and
  the machines clean, and blood itself as a muddy brown. Names and descriptions are reworded too
  (blood reads as "essence", bleeding as "draining", bloody as "stained"). A server can force it
  on for everyone with `/gamerule bloodandbonesBloodless true`. Nothing about how the game plays
  changes.
- Tooltips (hold Shift), JEI pages (including a Butchery page per mob showing what its carcass
  gives and a Body Parts page showing what its parts and organs do, and what each part brings to a
  minion's tasks), Ponder scenes and an advancement tab explain it all in game. `/bloodandbones traits
  explain <mob>` says what a mob's parts do; `/bloodandbones traits dump` writes them all to a CSV
  file; `/bloodandbones minion fitness` shows how fit the minion you look at is at every task, and why.

## Building

```
./gradlew build
```

The first build downloads NeoForge and decompiles Minecraft (several minutes); later builds are
incremental. `./gradlew runClient` / `runServer` / `runData` are configured by ModDevGradle.

### Development aids

- `./gradlew runGameTestServer` runs the game tests headless (590: every rigged mob and baby,
  butchery and its three yield paths, rot, cold air, bleeding, machines, their motion and their part filters, cooking and display, decoration, surgery and its screen's choices, the body through a real death, organs, implants, backtanks, carcass armour and its traits, trait effects, minions and their tasks, movement and mounts, chains, recipes
  and the soul blood line on Create's machines, bloodless mode (no game logic reads it; no gory word or wet sound
  left), advancements and sounds, what a dozen carcasses at once cost the server, and the carcass physics: which way a
  hooked carcass turns and a struck one falls).
  `-Dbloodandbones.debug.only=name,name` runs only those tests, and `-Dbloodandbones.debug.repeat=N` runs them N times.
  To time what carcasses cost, run one of these on its own with `-Dbloodandbones.debug.cost=N`:
  `-Dbloodandbones.debug.only=dozenCarcasses` prints the server's tick times with N dozen carcasses (one to four) made
  in the same tick, awake and then resting; `-Dbloodandbones.debug.only=dozenHung` with a dozen hanging on hooks, for a
  minute.
- The two-client check: with `-Dbloodandbones.multiplayer=true`, run `runMpServer`, then `runMpButcher` and
  `runMpWatcher`, each in its own terminal. The server makes a fresh flat world in `run/mp-server`; the Butcher kills,
  drags, hangs, cuts and skins a cow with real clicks while the Watcher photographs it into
  `run/mp-watcher/screenshots/mp_watcher_*.png`. Both clients log where they draw every carcass body, and the server
  where it really is (the `[mp]` lines of each `logs/latest.log`). All three quit after about forty seconds of play.
- `./gradlew runData -Dbloodandbones.dump_layers=minecraft:goat#main,...` writes those vanilla
  models' part trees to `run/build/layer-dump.txt`, for writing new rig targets in
  `src/main/rig_targets`.
- `./gradlew runClient -Dbloodandbones.showcase=true` makes a flat world, builds a scene of
  carcasses, machines and the rest, screenshots it (and six Ponder scenes, the cow's JEI
  page, a creeper's powder sac's and a villager's Body Parts page, a minion's task screen and a
  Surgery Table's line while building) into `run/screenshots/showcase_*.png`, and quits. It runs without a screen under
  `xvfb-run`. `-Dbloodandbones.showcase=bloodless` does the same with bloodless mode forced on.

## Licence

All code and assets in this repository are released under the MIT licence (see `LICENSE`).
You may use, modify and redistribute them, including in your own mods, as long as you keep the
copyright notice. Credit is appreciated but the licence only requires the notice.

Dependencies are **not** bundled in this repository or in the built jar. Players install them
separately: Create (MIT), Sable (see its own licence), Create Enchantment Industry (LGPL-3.0),
Create: Dragons Plus (LGPL-3.0) and Create Diesel Generators (MIT). Nothing from those projects
is copied into this repository; the mod only calls their public APIs.

## AI usage disclosure

This mod is built with AI assistance. The design, direction, decisions, testing and review are
done by a human (AviCagan); most of the code is written by Anthropic's Claude Code under that
direction, from the design brief in `docs/ARCHITECTURE-PROPOSAL.md`. Every commit made this way
carries a `Co-Authored-By` trailer naming the tool, so the history shows exactly what was
AI-assisted.

**Art status (placeholder).** Every texture and block model in the repository right now is a
development placeholder produced by Claude Code, drawn with small scripts so the features could be
built and tested. None of it is final art. The intent is to replace all of it with hand-made art
before any public release. Until then, treat these as not releasable:

- Recoloured from vanilla Minecraft textures (Mojang's assets cannot be redistributed under MIT):
  `item/blood_steel_ingot.png`, `item/blood_steel_nugget.png`, `item/blood_diamond.png`,
  `block/blood_steel_block.png`, and the Butcher's Table's `block/butcher_table_top.png`,
  `block/butcher_table_side.png` and their `_clean` twins (the iron block).
- Recoloured from Create's textures: `block/bloody_casing.png` and `block/bloody_casing_connected.png`
  (andesite casing and its connected sheet), `block/bloody_brass_casing.png`,
  `block/bloody_copper_casing.png` and their `_connected` sheets (brass and copper casing), `block/bloody_saw.png` and its bloodless twin
  `block/bloody_saw_clean.png` (the saw blade).
- This mod's own tool placeholders with blood added: `item/cleaver_bloody.png`,
  `item/flensing_knife_bloody.png`, `item/blood_steel_cleaver_bloody.png`.
- The other `*_clean.png` textures and `entity/flesh_bloodless.png` are this mod's own placeholders
  with the red taken out.
- Recoloured from this mod's own cleaver: `item/blood_steel_cleaver.png`.
- Everything else under `assets/bloodandbones/textures` was drawn pixel by pixel by script; check
  its origin before release anyway.

The screenshots in `docs/screenshots` are real in-game captures (from the developer showcase below),
not generated images, but they show this placeholder art. The mod page will only ever use real
screenshots of the final art.

When this mod is published:

- **Modrinth** requires the "Contains AI-generated content" disclosure whenever "a substantial
  portion of the project's code is a product of AI output" (Content Rules §6.1). This project
  will enable that disclosure with the *code* category, and will not use AI-generated images
  anywhere on the project page (Content Rules §6.2 forbids it).
- **CurseForge** currently has no general AI-code rule. Its moderation policy only requires a
  visible disclaimer on AI-modified showcase images that could misrepresent the mod, and its
  author terms require keeping third-party licence notices. This project follows both: no
  AI-modified showcase images, and dependency licences are credited above.
