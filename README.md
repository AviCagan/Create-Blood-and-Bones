# Create: Blood & Bones

A [Create](https://github.com/Creators-of-Create/Create) addon for Minecraft 1.21.1 / NeoForge:
mob carcass butchery, blood as a processable fluid, body-horror progression and gore-themed
decoration, built on Create's kinetic, fluid and contraption systems, with ragdoll physics
provided by [Sable](https://github.com/ryanhcode/sable).

- Mod id: `bloodandbones`
- Target: Minecraft 1.21.1, NeoForge 21.1.x, Create 6.0.x, Sable 2.0.x, Java 21
- Design: see `docs/ARCHITECTURE-PROPOSAL.md`

## What it does

- **The Meat Hook.** Kill a mob with it and the body stays behind as a ragdoll carcass (Sable
  physics, one body per limb, jointed) instead of dropping loot. Its gear still drops. Hook a limb
  to drag the body; heavier animals slow you more. 79 vanilla mobs are rigged, with their colours
  and variants (all but the ender dragon and tropical fish).
- **Butchery.** The Cleaver takes limbs off at the joint, leaving raw wounds with the bone
  showing on both ends, and breaks loose pieces down into meat, bone, offal and fat. The Flensing
  Knife takes the hide off (with a sheep's wool). Yields are data-driven per mob and spoil as the
  carcass rots. Light pieces can be picked up and carried.
- **Rot.** Carcasses rot over game time; cold slows it, ice stops it. Flies gather as the meat
  goes off. Rotten meat turns to rotten flesh, and a carcass left rotten for a day falls apart into bones and rotten flesh, so old ones
  don't pile up. Rot speed and the falling apart can be changed per world in
  `serverconfig/bloodandbones-server.toml`.
- **Hanging and blood.** The Shackle Hook hangs a carcass by the neck. A Bleeding Rack under a
  hanging (or lying) carcass collects its blood, faster with an Encased Fan blowing across it.
  Blood and Soul Blood are real fluids for pipes, tanks, spouts and basins. A bled carcass keeps
  longer. Blood that nothing catches stains the ground: kills, cuts, drag trails and carcasses
  hanging over bare floor leave splashes that dry dark and wash away in the rain.
- **Chain conveyors.** Right-click a Create chain conveyor with the Meat Hook while dragging a
  carcass: it rides the chain on a trolley and stops at frogports addressed for it. Trolleys queue
  behind each other instead of bunching up.
- **Machines** (shaft from below, stress 4 to 8 per RPM): the Mangler tears a carcass apart and
  grinds it down; the Guillotine takes limbs off; the Beheader takes heads (sometimes the skull);
  the Deglover strips hides. Each works whatever carcass lies on or hangs over it.
- **Materials.** Blood Steel (spout-fill iron with blood), Soul Blood (superheated mix with liquid
  experience, or a Diesel Generators fermenting basin), the Blood Diamond (spout-fill a diamond
  with soul blood), and the Blood Steel Cleaver (twice as deep a chop).
- **Cooking and display.** The Spit Roast turns a carcass piece over a fire until it browns (or
  burns). The Specimen Jar keeps a piece on show.
- **Bloodless mode.** `bloodless_mode` in the client config (`config/bloodandbones-client.toml`)
  hides blood drops and stains, shows skinned carcasses as pale meat, the hook in a carcass and
  the machines clean. A server can force it on for everyone with `/gamerule bloodandbonesBloodless true`.
  Nothing about how the game plays changes.
- Tooltips (hold Shift), JEI pages (including a Butchery page per mob showing what its carcass
  gives), Ponder scenes and an advancement tab explain it all in game.

## Building

```
./gradlew build
```

The first build downloads NeoForge and decompiles Minecraft (several minutes); later builds are
incremental. `./gradlew runClient` / `runServer` / `runData` are configured by ModDevGradle.

### Development aids

- `./gradlew runGameTestServer` runs the game tests headless (over a hundred: every rigged mob,
  butchery, rot, bleeding, machines, cooking, chains, recipes and advancements).
- `./gradlew runData -Dbloodandbones.dump_layers=minecraft:goat#main,...` writes those vanilla
  models' part trees to `run/build/layer-dump.txt`, for writing new rig targets in
  `src/main/rig_targets`.
- `./gradlew runClient -Dbloodandbones.showcase=true` makes a flat world, builds a scene of
  carcasses, machines and the rest, screenshots it (and three Ponder scenes and the cow's JEI
  page) into `run/screenshots/showcase_*.png`, and quits. It runs without a screen under
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
  `block/blood_steel_block.png`.
- Recoloured from Create's textures: `block/bloody_casing.png` (andesite casing),
  `block/bloody_saw.png` and its bloodless twin `block/bloody_saw_clean.png` (the saw blade).
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
