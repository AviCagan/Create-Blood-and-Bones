# Brief audit

29 September 2026

This page checks `docs/DESIGN-BRIEF.md` against the code, one sentence at a time. Three auditors went through the brief separately. Their findings are merged here: duplicates are dropped, and any item that looked doubtful was checked against the code again. Their list stopped partway through the self-augmentation section, so upkeep, minions and cybernetics were checked separately for this page.

"Main" means the code at 255d386, the commit where the Shackle Hook hangs a carcass by the neck. The branch `bb-organs` is 10 commits ahead of main and one behind: it does not have 255d386. Where `bb-organs` closes a gap, this page says so.

The brief's own header says that later decisions in docs/ARCHITECTURE-PROPOSAL.md (sections 12, 14 and 15) win where they differ from the brief. Where the code differs from the brief and no such decision is recorded, the difference is listed here as a gap.

Sizes:
- **Small:** one sitting, a few files and a test or two.
- **Medium:** one slice, touching several systems, with new tests.
- **Large:** several slices, or a redesign.

## In short

**What works.** The core loop is built and tested:
- A Meat Hook kill turns any of the 79 vanilla mobs into a ragdoll carcass.
- The carcass can be dragged, hung, bled, and butchered by hand or by four machines.
- Pieces can be cooked and displayed. Carcasses rot unless kept cold.
- Parts become armour and minions.
- Blood is a Create fluid.
- The body, prosthetics, cybernetics and the throttle all work, and the safety floor holds.

**The biggest gaps against the brief:**
1. **The physics the brief cares most about is only half proven.**
   - A carcass lying still can only be hooked by its torso.
   - The killing blow is a push on every body at once, not a blow at the point where it landed.
   - A hanging carcass cannot be knocked into a swing.
   - The tests check that things happen, not which way they happen.
2. **Rules 2 and 3 hold for parts and traits, but not for bodies.**
   - Every mob needs its own rig file, so a modded mob nobody has heard of just dies normally.
   - Weight classes do not exist.
   - Much of the tuning can only be changed in code.
3. **Whole carcasses never become items.** They cannot go on belts or into vaults. A chain line can carry them but cannot send them anywhere or unload them. The brief contradicts itself on this point, so the owner has to choose.
4. **The Meat Hook and the machines are not worth it in the way the brief says.**
   - Hand tools and machines give the same yields.
   - The three processing paths give the same yields as each other.
   - There are no damaged carcasses.

**Quickest win:** merge `bb-organs`. It brings organs as data and items, plus the minion leftovers. (Done on 29 September, 40dfb66.)

**Missing text in the brief itself:** rule 1 is blank, and the flesh-and-brass table under Cybernetics has only one row.

## Decisions only the owner can make

Each decision unblocks one or more work packages below; the package numbers are in brackets. Where the build already uses a default, it is given.

1. **Rule 1 and the Cybernetics table.**
   - The heading promises five rules, but rule 1 has been blank since the brief was first committed (181f0e6).
   - The comparison table under Cybernetics has one row ("Identity: you become the monster / you become the machine") and no header row. The rest is missing.
   - Nothing can be built or audited against either. [all; 12]
2. **Carcasses as items or as bodies.**
   - The brief says the Shackle Hook is "where a carcass stops being an entity", and that carcasses "on belts, in vaults, on contraptions" behave as ordinary items. It also says hanging "keeps it a ragdoll".
   - ARCHITECTURE 6 planned package items on chains. Section 13.11 built ragdoll trolleys instead, and 15.1 #5 made heavy torsos and limbs never become items. Neither change has been confirmed.
   - Choose one:
     - package items, which become bodies again when dropped; or
     - bodies throughout, with the brief's items rule applying only to pieces.
   - Also decide whether a piece should rot in a chest or vault. Today it does not. [4]
3. **Which kills make carcasses, and what "damaged" means.**
   - Only Meat Hook kills make carcasses today. The brief wants every other kill to leave a damaged carcass.
   - Every carcass is a physics body, so each one has a real performance cost. Decide:
     - whose kills count: players only, or mobs' and machines' too;
     - whether there is a cap;
     - what "damaged" means: lower yields, a failure chance per cut, or missing parts. [6]
4. **Rig B (docs/NEXT.md item 2).** Choose one:
   - copy rig B's four wins into our rigs one at a time (recommended);
   - put rig B on main behind the switch; or
   - drop it.

   Rig B is on the local branch `bb-rig-b`. [1]

   Package 1 built the first option, the recommended one, for the owner to confirm: rig B's looser necks and loose hang
   copied into our rigs (ARCHITECTURE 15.28; one revert takes them back).
5. **Where a Shackle Hook holds a carcass.** The brief says "held by one shoulder". The code holds it at the neck on purpose (255d386), and docs/NEXT.md treats a shoulder hang as a bug. Which is wanted? [1]
6. **The Flensing Knife and the Cleaver.** The brief names one hand tool. The build has two: the knife skins, and the Cleaver severs and butchers. No decision records the split. Keep it? [5]
7. **Loading a chain.** In the brief, the Shackle Hook "loads it onto a chain conveyor". Today a player does it by right-clicking a chain with the Meat Hook while dragging. Should the hook block do it itself? [4]
8. **Spit Roast effects.** Which effects cooked carcasses carry, and how they scale with what was cooked and how much. [15]
9. **The wall hook.** Should the Butcher's Hook take heavy whole carcasses, and every other body part (severed limbs, organs, scraps)? [17]
10. **Flesh against brass.**
    - Brass implants beat flesh ones on every figure, and brass never rots.
    - A worn tank holds one fluid, so a body with both flesh grafts and cybernetics can only power one kind at a time. In practice that punishes the mixing the brief says must not be punished.
    - Should player implants be made from never-degloved and degloved limbs, as minions are? [12]
11. **The ragged stump, and restoring rot.**
    - Swapping an implant straight in for flesh needs the surgeon but leaves no ragged stump (14.12). So a one-step swap always avoids the ragged-stump cost.
    - A fully rotted limb works again after 1 mB of blood and one second. How much more should restoring it from the maximum cost? [11]
12. **Tasks instead of jobs (docs/NEXT.md item 1).** Designed and built on 29 September 2026: the task list, what makes a minion good at a task, what is impossible and what only done badly, how a task is given, how fitness is shown and how parts state their aptitude in data are settled in docs/NEXT.md 1.1 to 1.6. One question is left:
    - whether the surgeon stays tied to villager and pillager heads (the surgeon switch, docs/NEXT.md 1.5). [13]
13. **The fourteen defaults of ARCHITECTURE 15.1** are still unconfirmed (docs/NEXT.md). Two of them overlap with decisions above:
    - #5 (heavy pieces never become items) is decision 2;
    - #8 (other illagers and the witch also make surgeons) goes with decision 12.

## Work packages

The packages are in order of how much their gap hurts the brief:
1. the physics the brief cares about most;
2. the rules everything else follows from;
3. the loop the brief calls load-bearing (weight, hauling, lines);
4. the yield gap it calls "the point";
5. everything else.

### 1. Physics: where you hook it and where the blow lands

**Why.** The brief says: "This is the part that's been hardest and I care about it most."
- Hook a hind leg and the animal comes round arse-first; hook the head and it follows head-first.
- A blow to the flank drops it sideways; one from behind pitches it onto its nose. "Nothing about which way it falls should be scripted."
- The head lolls.
- Hanging, it swings when knocked, and hangs differently once a leg is off.

**What is there.** The ragdoll, the resting form, dragging by the hooked bone (`CarcassDrag.aim`), hanging and cutting all work. But:
- **Lying carcasses can only be hooked by the torso.** A carcass that lies still for three seconds folds into one body. A hook on it then always takes the torso, wherever you click (`CarcassDrag.start`), because folded limbs have no cells. In normal play you cannot hook the leg or head of a lying carcass without punching it awake first.
- **The kill is scripted.** It sets a velocity on every body along the killer's look: 1.0 on the bone nearest the look, 0.5 on the rest (`CarcassAssembler.shove`). It is not a blow at the point where it landed.
- **A hanging carcass cannot be knocked into a swing.**
  - `CarcassPartBlock.attack` ignores any carcass that is not resting, so hitting a hanging one does nothing.
  - A spring (`ShackleHookBlockEntity.turn`, stiffness 30, damping 7) holds a hanging torso belly-out and head-up. So it hangs the same way after a leg comes off.
- **The neck is stiff** (stiffness 2, turn ±30°, nod −15° to 25°), so the head barely lolls.
- **Everything weighs the same for its size.** Bone, golem plate and flesh all use one `FLESH_DENSITY`, for every mob.
- **The tests check that things happen, not which way.**
  - They check, for example, that a torso dropped 0.25 blocks, or that a hook point came within 2.25 blocks of its target.
  - Nothing on main tests rear-first, head-first, a blow from behind, a swing when knocked, or being dragged up a step.
  - `meatHookDragsByLeg` failed once in more than 50 runs.

**What to build.**
- On a lying carcass, hook the bone you click: unfold it, then hook the bone nearest the hit point. The client's punch ray in `DragRenderer` already sees the drawn limbs.
- Replace the kill shove with an impulse at the hit point. Sable supports impulse-at-point.
- Let a blow knock a hanging carcass into a swing. Ease the spring so everything below the hook hangs loose, and a cut changes how it hangs.
- Give the neck looser joints, and give bone, plate and flesh different densities.
- Add one test per physics sentence in the brief, each checking direction:
  - a carcass hooked by a hind leg turns rear-first;
  - one hooked by the head follows head-first;
  - a flank blow lands it on its side;
  - a blow from behind lands it nose-down;
  - a lying carcass's head rests lower than its neck;
  - a hanging carcass swings when knocked;
  - one with a leg off hangs lower on that side;
  - a carcass dragged by a hind leg gets up a one-block step.
- Find the cause of the `meatHookDragsByLeg` failure.

**Size.** Large.

**Touches.** `carcass/CarcassDrag`, `CarcassRest`, `CarcassAssembler`, `CarcassPartBlock`, `ShackleHookBlockEntity`, `datagen/RigDerivation`, the rig targets, and game tests.

**Owner first.** Partly. Decisions 4 and 5 shape the joints and the hang. The hooking fix, the impulse and the tests can start now.

### 2. Groups first: any mob works on day one, and everything retunes by data (built 29 September 2026, `bb-groups`, but what waits on the owner)

Built (ARCHITECTURE 15.31, docs/MODDED-MOBS.md): a generic body for any mob with no rig, from its archetype, scaled to its
hitbox and wearing its own model's parts and skin on the client; the archetypes' match rules read at last; babies with no
baby shape; weight classes as data (listed in 15.31 for the owner), driving drag, blood, floating or sinking and rot time;
rot time and butchery by group for every mob, vanilla ones too (a mob's own file says only what it does differently; a
butchery table file is a datapack's optional override, and the mod ships none); the constants below moved into the server
config or data at today's figures; naming rules in place of most rig-target overrides; the guide for model authors.
Found on the way (15.31): `hindLegHookComesRoundRearFirst` failed about once in 60 to 80 runs, on main too (a dragged
carcass's turn was all but undamped); now about once in 500, a rarer way left open. `grapplingSpoolHandsACarcassToTheDrag`
failed about once in 80 (the spool handed a carcass over only nearer than the ground let it come); put right.
Left for the owner: whether a size-2 slime should leave a carcass (4.4 says it splits), the class list and whether a
class should set the drag penalty itself; not built: the client-sent rigs and export command of 4.1, and families by Java
class.

**Why.**
- Rule 2: "Nothing is ever configured per-mob if it can be configured per-group … rigs, butchery yields, drag penalty, part lists … keys off those." A specific mob's own file "must never be required". "A modded mob nobody has ever heard of should work on day one."
- Rule 3: "Another mod or a datapack should be able to add a creature, or retune the whole mod, without touching code."
- Bodies and rigs: "per-group models, with per-mob models as an optional upgrade". "The joints should be derived from the model, not written down separately." The brief also asks for "a clear, documented naming and structure convention".

**What is there.** Parts, traits and scrap materials come from groups: 11 archetypes, 27 families and 16 overlays keyed off vanilla tags. Every vanilla mob resolves, and a mob's own file is optional there. But:
- **No fallback rig.** Rigs are one generated file per mob (section 12 chose model-generated rigs), and `RigManager` has nothing to fall back on.
  - With no rig, `CarcassAssembler.assemble` returns null and the mob dies normally. An unknown modded mob killed with the Meat Hook just drops its loot, and nothing else in the mod ever reaches it.
  - The same happens to the ender dragon, tropical fish, size-2 slimes, and babies of kinds that have no baby shape.
- **Adding a creature means hand-writing a joint list.** Rigs come only from a client data run over 79 hand-listed targets. There is no in-game export (ARCHITECTURE 4.1).
- **The rig targets are largely hand-written.**
  - They write joints in 55 files, physics boxes in 21, re-parenting in 7, merges in 30 and attachments in 29.
  - In 21 mobs, what can be grabbed is already different from what is drawn. Some of this is deliberate, such as the ghast's tentacles, which collide shorter than they are drawn.
- **No weight classes.** Weight is a number worked out from each model's boxes; ARCHITECTURE 5's `weight_class/<id>.json` was never built. Butchery yields, rot time and blood are set per mob or in code.
- **Much of the tuning can only be changed in code:**
  - the drag curve;
  - `CUTS_TO_SEVER`, `CUTS_TO_BUTCHER`, `STROKES_TO_SKIN` and `LIGHT_MASS`;
  - blood per unit of weight (1000 mB);
  - the spoil thresholds (0.6 and 0.3);
  - the Beheader's skull map;
  - the slime and magma cube baby yields;
  - machine stress and pace;
  - Spit Roast times;
  - every implant's figures and drain (`registry/BBItems`);
  - the throttle curve and module costs;
  - the necrosis rates.
- **Organ lists are code on main** (`Surgery.harvest`); `bb-organs` makes them data.
- **The naming rules are undocumented.** They live in code (`RigDerivation.jointFor`) and in `bone_slot_rules`. There is no guide for someone making a modded mob's model.
- **Recipes are hand-written.** Section 8 decided recipe JSON would be generated, because Create's fluid ingredient format is due to change. In fact 78 recipe files are hand-written and 3 are generated. (Done 29 September 2026, `bb-materials`: every recipe comes from datagen, ARCHITECTURE 15.20.1.)

**What to build.**
- A fallback body for any mob with no rig: its archetype's generic rig scaled to the mob's hitbox, wearing its own texture as far as possible. The other option is reading the model on the client, as section 4.1 proposed. Add a test with a mob that has no rig file.
- Weight classes as data: set by group, overridable per mob, and driving drag, blood, floating or sinking, and rot time.
- Butchery yields set by group, with a per-mob table as an optional override.
- Move the constants above into data, or into the server config where they are balance knobs.
- Fewer hand-written overrides: turn what the rig targets say into naming rules, and record the deliberate exceptions.
- A short guide for modded model authors: which part names and structure make the joints come out with no target file.
- Recipes generated by datagen.

**Size.** Large.

**Touches.** `carcass/rig/`, `datagen/`, `carcass/butchery/`, `CarcassDrag`, `CarcassRot`, `CarcassBleeding`, `CarcassButchery`, `machine/`, `body/`, `cyber/`, recipes, new data folders, and docs.

**Owner first.** No; ARCHITECTURE 5 already sets the shape. Show the owner the list of weight classes.

### 3. Weight and the drag penalty

**Why.** "Mobs fall into weight classes that drive a movement speed penalty on whoever's dragging one: roughly 5% for a chicken up to 55% for a ravager … This is load-bearing, not flavour."

**What is there.** Dragging slows you, and the `drag_strength` attribute eases it. But:
- **Dragging a piece costs as much as the whole animal.** `CarcassDrag.dragPenalty` uses the whole mob's figure whenever a rig exists. It ignores the mass actually attached (`attachedMass`), which it already works out. A severed ravager leg slows you by 55%.
- **The curve is off at the light end:**

  | Mob | Penalty now |
  |---|---|
  | Chicken | 12.5% (the brief says about 5%) |
  | Rabbit | 8% |
  | Cow | 28% |
  | Horse | 38% |
  | Ravager | 55% |

  The comment on `Rig.dragPenalty` wrongly says a chicken costs 5%.
- **The size of the penalty is untested.** The only test checks that the slowdown is below zero.

**What to build.**
- Take the penalty from the mass actually attached.
- Refit the curve so a chicken costs about 5% and a ravager 55%.
- Add tests for:
  - both ends of the curve;
  - a severed leg against its whole body;
  - a player's actual speed while dragging.

This moves onto weight classes when package 2 is done.

**Size.** Small.

**Touches.** `carcass/CarcassDrag`, `carcass/rig/Rig`.

**Owner first.** No.

### 4. The carcass as an item: the Shackle Hook, lines, belts and vaults

**Why.**
- Shackle Hook: "Where a carcass stops being an entity. Hangs one, loads it onto a chain conveyor. The transport layer."
- Environment: "Carcasses on belts, in vaults, on contraptions: all should behave as ordinary items."
- Weight: "Hauling has to be annoying enough that building a conveyor line reads as the solution."

**What is there.** A player can hang a carcass on a chain by hand. It then rides a Shackle Trolley as a live ragdoll, and trolleys follow Create's chains, queue and pass each other. But:
- **Routing is coded but cannot be reached.** `ChainCursor.address` is never set in play, and `ShackleTrolleyEntity.resume()` is never called. A trolley cannot be sent anywhere or unloaded at a stop, so a line only works as a loop, and that loop is untested.
- **No clearance check.** ARCHITECTURE 6 decided on hanging length plus one block; it was not built.
- **The Shackle Hook block never loads a chain.**
- **Whole carcasses never become items.** Only light pieces (mass 0.13 or less: heads, legs, a whole chicken) do. A whole cow can never go on a belt or into a vault.
- **The machines take no items.** They take nothing from belts, funnels or chutes; there is no belt processing in `machine/`. They only work on a body lying on them or hanging over them.
- **Pieces never rot as items.** A piece carried as an item keeps its freshness for ever, so a chest or vault keeps meat fresh with no cold at all.

**What to build (after decision 2).** First, one of:
- the package item of ARCHITECTURE 6: a carcass on a chain or a belt is a package, and when dropped it becomes a body again; or
- bodies throughout, with the brief's items rule applying to pieces only.

Then, either way:
- sending a trolley to an address and unloading it at a station;
- the clearance check;
- the Shackle Hook putting what it holds onto a chain above it;
- machines taking pieces from belts, the way Create's press does;
- pieces going off in storage, if the owner wants that;
- one test of a whole line: hook, chain, Beheader, Deglover, Mangler.

**Size.** Large.

**Touches.** `carcass/trolley/`, `ShackleHookBlockEntity`, `machine/`, `item/CarcassPieceItem`, `CarcassRot`, and game tests.

**Owner first.** Yes: decisions 2 and 7.

### 5. The yield gap, and three paths that stay distinct (partly built 29 September 2026, `bb-machines`)

**Partly built.** ARCHITECTURE 15.19.1 has the details. The paths are data, by hand is about half with real loss, the
Mangler is fastest with the least of anything a table gives (its loot less what its table already gives), the stations
get all of it, and the Surgical Rig is the slowest at any speed with organs and the hide on top (a hand at the rig gets
a hand's share). The knife is held on a part, and one test sends a cow down each path. **Not built:** nothing in
survival carries the Keen Butcher trait, so the butchery yield is 1 for every player and minion (a balance-pass
choice); decision 6 is still open, so the knife and the Cleaver stay two tools. The rest of this section is kept as it
was written.

**Why.** The brief says:
- Flensing Knife: "Roughly half the yield of a machine, with random loss … Hold on a part to take it off."
- "The yield gap between hand and machine is the point."
- "Three processing paths that must stay meaningfully distinct":
  - "the Mangler is fastest and least selective with the worst yield";
  - "a filtered station is fast, dedicated to one part, full yield";
  - "the Surgery Table is slowest, totally reconfigurable, full yield plus organs".
- Mangler: "plus the mob's normal vanilla drops. Deliberately wasteful."
- Deglover: "High stress, low RPM." Beheader: "fast, single-purpose, cheap".

**What is there.** The early game works with no Create machines at all. But:
- **Hand and machine yields are the same.** Hand tools and machines call the same butchery code at the same scale:
  - the Flensing Knife and the Deglover skin alike;
  - the Cleaver and the Mangler butcher alike, and the Mangler adds scraps on top of the full table.
- **There is no real random loss.** The only randomness is the same rounding roll everyone gets.
- **The Mangler is the slowest per part, not the fastest.** It takes three strokes per joint and three per piece. The Guillotine and Beheader take one stroke per limb.
- **No vanilla drops.** Nothing in the mod rolls a mob's loot table.
- **The Deglover is the cheapest machine, not the most expensive.** It costs 4 su per RPM, tied with the Beheader (the Mangler costs 8), and it has the quickest stroke.
- **The knife works by separate clicks,** each one taking the whole hide, rather than holding on a part.
- **The Surgery Table gives no meat or bone of its own.** Its "full yield" needs a separate Butcher's Table, and no test runs that path end to end.

**What to build.**
- A yield scale per path, stored as data:
  - by hand: about half, with a real loss roll (the `butchery_yield` attribute from PARTS-AND-TRAITS 5.7 can build on this);
  - filtered machines: full yield;
  - the Mangler: the least meat and bone, but the mob's loot table rolled plus scraps, and the fastest per carcass;
  - the Surgery Table: organs on top of a full yield.
- A Deglover whose stress cost makes low RPM the sensible setting, and a Beheader that is the fastest and cheapest machine.
- A Flensing Knife that is held on a part.
- One test that sends a cow down each path and compares yields and times.

**Size.** Medium.

**Touches.** `carcass/CarcassButchery`, `machine/MachineKind`, `CarcassMachineBlockEntity`, the knife, and butchery data.

**Owner first.** Only decision 6. The brief already gives the shape of the numbers.

### 6. Quality and the Meat Hook

**Why.**
- "Killed with the Meat Hook: intact, full yields. Killed any other way: damaged, failure chance downstream. This is the reason the Meat Hook is worth using despite being a bad weapon."
- Meat Hook: "Slow, heavy, low damage, bad against groups."

**What is there.**
- **Only the hook makes carcasses.** A carcass is made only when the killer holds the Meat Hook in its main hand; players and minions count alike. Every other kill drops vanilla loot.
- **There is no quality anywhere.**
- **The hook is a good weapon, not a bad one.** It is an iron sword with a slower swing:
  - 6 damage, the same as an iron sword;
  - 1.2 swings a second;
  - it sweeps (1 damage without Sweeping Edge), and a sweep kill also makes a carcass;
  - nothing makes it heavy.

So the hook is required rather than worth choosing, and the trade-off the brief describes does not exist.

**What to build (after decision 3).**
- A quality value on the carcass record and on pieces.
- Damaged carcasses from other kills, within a cap.
- A failure chance in hand butchery and in the machines.
- A quality attribute for Create's filters.
- A Meat Hook with low damage, no sweep, and a drawback while held.
- Tests for all of the above.

**Size.** Large.

**Touches.** `event/CarcassEvents`, `CarcassSavedData`, `item/CarcassPieceItem`, `CarcassButchery`, `machine/`, `item/MeatHookItem`, `BBItemAttributes`, and the server config.

**Owner first.** Yes: decision 3.

### 7. Merge `bb-organs` (done 29 September 2026, 40dfb66)

**Done.** Main's newer commits were merged into `bb-organs` (the hauler kept main's stress-tested version), the review's 17 findings were fixed, all 451 tests passed three runs in a row, and the branch is now main. The rest of this section is kept as it was written.

**Why.**
- Armor: "An organ adds one special ability. Every mob has at least one; some have several."
- Rule 3, which needs organs to be data.
- "A carcass knows which parts it still has … organs."

**What is there.**
- **On main:**
  - every mob's special organ exists only in data;
  - only the heart, lungs, stomach and eyes are items;
  - the organ lists are code;
  - a heavy carcass cannot be operated on.
- **On `bb-organs`** (verified: 443 tests, passing three runs in a row):
  - every organ has a file and an item (the Gland);
  - organ lists are set by part;
  - the Surgical Rig can take organs from a heavy carcass, and the carcass record counts its own organs;
  - the minion leftovers from docs/NEXT.md are done: held items and helmets are drawn, and minions can walk across lava, fly on wings, walk the seabed, be ridden, and keep their variants;
  - the list of mobs still waiting for their signature ability drops from 48 to 33.

**What to build.**
- Merge main's neck-hook commit (255d386) into `bb-organs`.
- Run the full suite.
- Merge `bb-organs` into main.

Two things stay open there: bloodless sounds (package 9), and both of a rabbit's haunches give a foot.

**Size.** Small.

**Touches.** `parts/`, `body/Surgery`, `minion/`, and JEI.

**Owner first.** No.

### 8. Rule 5: every block and every carcass on contraptions (done 29 September 2026, `bb-contraptions`)

**Done.** ARCHITECTURE 15.30 has the details. The rest of this section is kept as it was written.
- A contraption test for every block listed below (`ContraptionTests`), and one for building the blocks that hold things
  into a Sable ship. Two blocks doubled what they held and are fixed: the Surgery Table dropped its attachment when a
  contraption moved it, and every block that drops its contents when removed dropped them when a ship was built round it.
- A hung carcass rides in its Shackle Hook's data while a contraption moves the hook, is drawn hanging there, and hangs
  again, turned as the contraption turned, where it is set down (ARCHITECTURE 3.5's plan).
- A resting carcass on a deck counts the deck's blocks as holding it up and is pinned to the deck, not the world; it
  looks again once a second at what it lies on, so a ship built under it takes it along. A carcass dropped onto a
  moving deck is still when it keeps still on the deck, so it rests there while the ship moves.
- A Shackle Hook on a ship is joined to the ship. The checks were right: it could not hold a carcass at all (Sable refused
  its joint). It now hoists the carcass up to where the ship has carried its tip and holds it there.
- Tests on moving decks: a ship driven four blocks with a cow resting on its deck and one hung from its gallows; a
  ship built round a hook already holding a cow, and under a cow already resting, each then driven; a cow dropped on a
  ship flying along. A review's findings are put right (ARCHITECTURE 15.30, "Review findings put right").
- **Not done:**
  - the cut leg that fell into the void was not made to happen again, in 32 tries (`VoidLegTests`, switched on only);
  - chain conveyors on sub-levels (13.11), which this section named but did not ask to be built;
  - a patient on a moved Surgery Table gets up where the table was (only Create's own seats carry a rider);
  - a carcass kept in a hook's data is lost if the contraption is broken up and its blocks drop.

**Why.**
- "Every block moves on a gantry and glues to a contraption … Carcasses ride contraptions correctly too."
- The carcass "has to survive being dragged onto a moving simulated Sable physics contraption".

**What is there.** There are movement checks for the hooks and the bone pile, and contraption tests for the hooks, tables, racks, ribcage arches, casings and bone pile. But:
- **These blocks have no contraption test:**
  - the Mangler, Guillotine, Beheader and Deglover;
  - the Bleeding Rack and the fluid in it;
  - the Spit Roast and the Specimen Jar;
  - the Butcher's Table;
  - the Surgery Table and its seat;
  - the Blood Trough and the Charging Cradle;
  - a placed Fluid Backtank, and the Backtank Port.
- **A moved hook drops its carcass.** When a contraption moves a Shackle Hook, the carcass falls. ARCHITECTURE 3.5 planned to keep it as hook data while moving; that was not built.
- **Carcasses on Sable decks are fragile:**
  - `CarcassRest.isSupported` reads world blocks only, so a carcass on a deck never folds into its cheap resting form;
  - the resting form is pinned to the world, which can hold a body still while the deck moves under it;
  - a Shackle Hook on a ship joins its body to the world at ship coordinates, which looks wrong.
- **Only a still deck is tested.** Chain conveyors on sub-levels are not supported (13.11).

**What to build.**
- A contraption test for each block above.
- A hanging carcass kept as hook data while its hook moves, and put back when the contraption is taken apart.
- A resting carcass pinned to the deck under it, not to the world.
- Hooks on ships joined to the ship.
- A test on a moving deck.

**Size.** Large.

**Touches.** `registry/BBMovementChecks`, `CarcassRest`, `ShackleHookBlockEntity`, and game tests.

**Owner first.** No.

### 9. Rule 4: finish bloodless mode (done 29 September 2026, `bb-materials`)

**Done.** ARCHITECTURE 15.20.3 has the details. The rest of this section is kept as it was written.

**Why.** Bloodless mode "reframes the whole mod as mechanical rather than organic: 'replacement' not 'amputation', clean plating not grafted flesh, essence not blood, constructs not corpses. Identical mechanics, presentation only … Wire it in from the first feature."

**What is there.** There is a client setting and a gamerule. Words (blood to essence, minion to construct, gut to cord and so on), textures, particles and stains all swap, and only presentation code reads the setting. Still missing:
- carcasses still look like dead animals, and are still called carcasses and carcass pieces;
- the Flesh Arm, Sinew Leg and Severed Arm keep their names;
- no sound ever changes;
- no test would fail if some game logic started reading the setting.

**What to build.**
- Bloodless names for carcasses, pieces, flesh implants and severed parts.
- A construct look for carcasses.
- A set of clank sounds, chosen by the setting on the client.
- A test that fails if anything outside presentation code reads the setting.

**Size.** Medium.

**Touches.** `config/BloodlessWords`, `client/BloodlessSwap`, `registry/BBLang`, `sounds.json`, and `client/CarcassModels`.

**Owner first.** No.

### 10. Filters that pick one part out of a mixed line (partly built 29 September 2026, `bb-machines`)

**Partly built.** ARCHITECTURE 15.19.2 has the details. The machines, the Butcher's Table and the Surgical Rig ask their
filter about each part they could take, a limb attribute takes any slot the data names, and mixed lines are tested.
**Not built:** the butchering minion's filter (a minion's job is becoming a task, and how a task is given is decision
12); a filter for single organs at the rig (it asks about the part an organ is in, so it cannot take only hearts;
organs are main's data now). The rest of this section is kept as it was written.

**Why.**
- "A filtered machine pulls one specific part out of a mixed line and passes the rest through untouched."
- "Every station that removes parts carries a filter."

**What is there.** The four machines each carry a Create filter, but it picks the mob, not the part. Which part a machine takes is fixed by the machine:
- the Beheader takes heads;
- the Deglover takes the hide;
- the Guillotine takes the nearest limb that is not the head, and cannot be told which one;
- the Mangler takes everything.

An attribute filter set to a part is asked about the whole body, so it never matches a head on a whole carcass. The Butcher's Table, the Surgical Rig (used by hand or by a Deployer) and a butchering minion have no filter at all. No test runs a mixed line.

**What to build.**
- Ask the filter about each part the machine could take, so a Guillotine set to "hind leg" takes only hind legs.
- Filters on the Butcher's Table, the Surgical Rig and the butchering task.
- A test of a mixed line.

**Size.** Medium.

**Touches.** `machine/CarcassMachineBlockEntity`, `MachineFilterSlot`, `registry/BBItemAttributes`, `body/Surgery`, and the Butcher's Table.

**Owner first.** No.

### 11. Self-augmentation: the ritual and the proofs (built 29 September 2026, `bb-surgery`, but decision 11's two parts)

**Why.** This is the brief's highest-risk system. The safety floor holds and is tested. What is left is the ritual's screen, two rules, and missing proofs.

**What is there, and what to build.**
- **The surgery screen.** The brief says to "select augments in a new UI".
  - Today the one item laid on the table decides every button, so trying another augment means leaving the screen.
  - The buttons are plain, and they cover your own body in the outside view (`run/screenshots/showcase_body_2.png`).
  - Build: a screen that offers what you carry, draws the body, and keeps the view clear.
- **"Restoring it costs Blood":** decision 11.
- **The one-step swap that avoids the ragged stump:** decision 11.
- **Missing limbs still show what they held.** In third person, a held item and armour are still drawn on a missing limb (known since 14.5).
- **Tests that do not exist yet:**
  - the body kept through a real death and respawn (only saving and loading is tested);
  - health unchanged by an amputation;
  - the Crude Heart, Crude Lungs and Crude Stomach lifting their penalties;
  - necrosis from a real hit, a real block break and a real run (walking wears the legs too, while the brief names only running);
  - the fog with one eye out;
  - the real button path through `Surgery.handle`.
- **Upkeep.**
  - Each powered implant takes 1 or 2 mB a second, and a Magnet Coil or Analytical Lens takes 1 more, all scaled by `blood_upkeep`. Crude parts take nothing.
  - Six brass parts cost about 10 mB a second: 36 buckets of soul blood an hour, before any throttle.
  - Only one implant's drain is tested. Add a test that several drains add up, and a balance check that six limbs demands "a serious farm", not an impossible one.

**Size.** Medium.

**Touches.** `client/SurgeryScreen`, `body/Surgery`, `Necrosis`, `BodyRendering`, and game tests.

**Owner first.** Partly: decision 11.

**Built** (ARCHITECTURE 15.29, with 12 new tests in `RitualTests`):
- The surgery screen: a paper doll of the body and the picked slot's choices in two panels at the sides, nothing over
  the body, every implant, prosthetic, limb and module you carry offered (and what is on the table, and unclipping by
  hand), the surgeon, its fitness and the stump's price before any cut. The payload names where the item comes from,
  what the card said it would do and with what, so a stale second click is refused. The green mark and the carried row
  count only what goes in, never a blade or a wrench.
- A missing arm holds nothing and a missing limb wears no armour in third person.
- Walking no longer wears the legs; only a sprint does.
- Every proof listed above, and the balance check: six brass limbs cost 36 buckets of soul blood an hour, 45 of blood
  through the full line, about 56 cows an hour and half a Basin Lid. A serious farm, not an impossible one.

**Left for decision 11:** the one-step swap that avoids the ragged stump, and what restoring a rotted limb should cost.

### 12. Cybernetics: different from flesh, not better

**Why.**
- "The second endgame, and it must feel different from flesh rather than better."
- "Limbs that were never degloved feed the organic path; degloved limbs feed the cybernetic one."
- Set bonuses: "Mixing gets neither bonus and no penalty."

**What is there.** The throttle, the seven modules, module slots and both set bonuses are built and tested. But:
- **Every brass limb beats its flesh match on every figure:**

  | | Flesh | Brass |
  |---|---|---|
  | Arm | Flesh Arm: mines at 1.3, hits +1 | Hydraulic Arm: mines at 1.8, hits +3, reaches +1, takes two modules |
  | Leg | Sinew Leg: walks 1.1, jumps 1.3 | Piston Leg: walks 1.2, jumps 1.6, falls 6 blocks safely, takes two modules |

- **Brass never rots.** Flesh's only advantages are cheaper fuel and the flesh set bonus.
- **A mixed body can only run one kind.** A tank holds one fluid, so a body with both flesh grafts and cybernetics can power only one kind at a time.
- **The deglove pipeline exists only for minions.** Player implants are crafted from meat, offal, hide, brass and blood steel, not from limbs.
- **Some implants blur the line:**
  - the Furnace Stomach is made of brass, but runs on blood, rots, and counts as a flesh graft;
  - there is no soul-blood stomach;
  - the Optic Eye has no brass in it.

**What to build (after decision 10 and the missing table).**
- Flesh grafts with strengths of their own, not just smaller numbers.
- The answer on mixed fuel.
- Limb recipes from unskinned and skinned pieces, if the owner wants them.
- Implant figures in data (with package 2).

**Size.** Medium.

**Touches.** `registry/BBItems`, `body/ImplantSpec`, `ImplantItem`, `backtank/`, `cyber/SetBonus`, and recipes.

**Owner first.** Yes: decisions 1 and 10.

### 13. Minions: tasks for any minion

**Why.**
- The brief: "the head sets behaviour (specific behavior can be set later)".
- The owner decided on 24 September that there are no jobs. Any task can be given to any minion, which does it better or worse depending on its stats (docs/NEXT.md item 1).
- The brief also says neither kind of minion "should be more OP than the other".

**Built on 29 September 2026** (docs/NEXT.md 1.10; docs/ARCHITECTURE-PROPOSAL.md 15.21 to 15.26). Any minion can be given any of 16 tasks and does it better or worse by its parts: its fitness comes from its stats, its head's knacks and disposition, all in data, and is shown on the task screen, the status line, the Surgery Table, JEI and piece tooltips. Only the surgeon switch (decision 12, docs/NEXT.md 1.5) waits for the owner.

**What is still open.** Flesh and brass minions each have something the other lacks, but nothing compares them:
- brass drains a quarter as much blood, ignores poison, wither, hunger and drowning, and takes a module;
- flesh mends itself.

That comparison belongs in the balance pass (PARTS-AND-TRAITS slice 9).

**What was built.**
- Tasks, with aptitude coming from the parts and stored as data (`minion/MinionTask`, `MinionFitness`, the task files and head data).
- The task list (docs/NEXT.md 1.1).
- A way to give a task: the task screen (`MinionTaskScreen`), with anchor and reach.
- A way to show how fit a minion is for each task (`TaskWords`, the status line, JEI, `/bloodandbones minion fitness`).

**Size.** Large; done.

**Touched.** `minion/MinionStats`, `MinionTasks` (in `MinionJobs`' place), head data, `body/Surgery.surgeonAt`, the Surgery Table's status line, and JEI.

**Owner first.** Only the surgeon switch.

### 14. Blood, Soul Blood and the materials as written (done 29 September 2026, `bb-materials`)

**Done.** ARCHITECTURE 15.20.2 has the details; it leaves the owner one question (a press as a second way to congeal). The rest of this section is kept as it was written.

**Why.** The brief says:
- Soul Blood "is the expensive tier, made through a congeal, haunt, re-melt chain using Create's own heating, pressing, haunting and mixing … the full line is what makes the late game viable. Tagged separately from normal blood."
- Blood Diamond: "a diamond taken through a blooded spout with a lot of blood (1 bucket) and 1 bucket of liquid XP … like a sequenced crafting".
- Blood is "tagged so other blood mods can pipe it in".
- The backtank has "a gauge styled to match Create's own".

**What is there.**
- **Soul blood is made in one step, two ways:**
  - basin fermenting;
  - superheated mixing of 500 mB blood, soul sand and 50 mB liquid experience into 500 mB soul blood.

  There is no congealed blood, and no haunting or pressing step.
- **The Blood Diamond recipe is wrong.** It is a single fill of 1000 mB soul blood: no liquid experience, and not a sequence. Section 8 agreed with the brief, and nothing recorded since changed it.
- **Soul blood has no tag of its own.**
- **Other mods' blood is ignored in many places.** Our blood is in the `c:blood` tag, but these take only our own fluid:
  - every blood recipe;
  - organic implant fuel;
  - perfusion (clearing necrosis).
- **The gauge is not like Create's.** The backtank shows an item bar, always red whatever it holds. There is no always-on gauge like Create's air overlay, and blood-fuelled implants show no reading at all.
- **Only recipe loading is tested.**

**What to build.**
- The congeal, haunt, re-melt chain as the full line. Keep the one-step recipes only as much weaker shortcuts.
- The Blood Diamond as a sequenced assembly.
- A soul blood tag, and consumers that match tags rather than our own fluid.
- A gauge like Create's, coloured by the fluid.
- Tests that read each recipe's inputs, amounts and outputs.

**Size.** Medium.

**Touches.** Recipes, `registry/BBFluids`, `BBItems`, `body/ImplantItem`, `Necrosis`, a client overlay, and game tests.

**Owner first.** No; section 8 decided the chain.

### 15. The machines as the brief describes them (built 29 September 2026, `bb-machines`, but the Spit Roast's effects)

**Built** but the Spit Roast's effects, which wait for decision 8. ARCHITECTURE 15.19.3 has the details. The rest of this
section is kept as it was written.

**Why.**
- Guillotine: "Winds up under rotation, drops on a redstone edge. The clean, straight limb cut."
- Deglover: "Kinetic rollers."
- Beheader: "Inline, continuous".
- Spit Roast: "Cook whole carcasses and limbs … much faster on a shaft. Cooked results carry effects that scale with what and how much you cooked."

**What is there.**
- **No moving parts.** All four machines are one timed stroke with a static model; only a half shaft underneath turns.
- **The Guillotine reads no redstone.** ARCHITECTURE 7 planned the wind-up; 13.8 built the Millstone pattern instead, with no recorded decision. Its cut is no different from any other.
- **The Beheader is untested inline.** Nothing tests a trolley passing over it.
- **The Spit Roast falls short of the brief:**
  - it takes only pieces a player can carry (its test skewers a cow body built in code, which a player never could);
  - a shaft at 64 RPM cooks at 2x, and a Hand Crank at 1.5x;
  - cooked results are plain cooked items, with no effects.

**What to build.**
- The Guillotine's wind-up, its redstone drop and a falling blade.
- Rollers, grinders and blades that move.
- The Beheader tested inline.
- A Spit Roast that takes whole carcasses, a shaft clearly faster than a crank, and effects (decision 8).

**Size.** Medium.

**Touches.** `machine/`, `cooking/SpitRoastBlockEntity`, models, and Ponder.

**Owner first.** Only for the Spit Roast's effects.

### 16. Cold air and other addons' freezing (done 29 September 2026, `bb-machines`)

**Done.** ARCHITECTURE 15.19.4 has the details. The rest of this section is kept as it was written.

**Why.** "Cold air keeps carcasses fresh; this should play nicely with other addons' bulk freezing."

**What is there.**
- Cold blocks within 2 blocks of the torso slow or stop rot, including Dragons Plus freezers and its passive freezer tag.
- A carcass in a Dragons Plus freezing fan's air current is not chilled. `FanAirflow` finds the current's processing type, but nothing reads it, even though section 5 decided a freezing current counts as cold.
- Nothing is tested with Dragons Plus.

**What to build.**
- A freezing current chills any carcass in it.
- Tests with a Dragons Plus freezer and a freezing fan.

**Size.** Small.

**Touches.** `carcass/CarcassRot`, `bleeding/FanAirflow`.

**Owner first.** No.

### 17. Decoration leftovers (done 29 September 2026, `bb-materials`, but heavy carcasses on the wall hook)

**Done** but whole carcasses on the wall hook, which wait for decision 9. ARCHITECTURE 15.20.4 has the details. The rest of this section is kept as it was written.

**Why.**
- "Wall-mounted Meat Hook: accepts any carcass or body part as a rendered attachment."
- "Blood-stained variants of Create's cladding."

**What is there.**
- The Butcher's Hook takes light carcass pieces only. It refuses heavy carcasses, severed limbs, organs and scraps.
- There is no stained railway casing, and no small stained palette (section 7).
- Cow boots made from scraps alone read "Cow Hide Boots" with no hide fitted, because the grazer family's scrap material is named "Hide".

**What to build.**
- A wall hook that takes every body part, and heavy carcasses too if decision 9 says so.
- The missing casing.
- A different name for the grazer material.

**Size.** Small.

**Touches.** `cooking/ButcherHookBlock`, its renderer, casings, and lang.

**Owner first.** Only decision 9.

### 18. Checks nobody has run (done 29 September 2026)

**Done.** ARCHITECTURE 15.18 has the details and the numbers. The rest of this section is kept as it was written.
- **Two clients** on a dedicated server (`-Dbloodandbones.multiplayer=true`: `runMpServer`, `runMpButcher`,
  `runMpWatcher`). One client kills, drags, hangs, cuts and skins a cow with real clicks; the other photographs it and
  logs where it draws every body.
  - The Watcher sees it right. Bodies are drawn 3.5 cm on average from where the server has them. The rack's blood and
    the stains match the server's within a few ticks, and the handover never leaves a gap.
  - Hanging a carcass threw the player beside it about 220 blocks. Fixed: the Shackle Hook and the Shackle Trolley now
    hoist the body up at 3 blocks a second, and neither snaps it up after a reload or when it is caught under something.
    A player standing right against the rising body is still jostled (a block or two); left for package 1. A hook on a
    ship is unchanged and untested.
  - Walking forward while dragging, your own carcass pushes you along. Not fixed; left for package 1.
  - A cut leg fell through the ground into the void in one run of seven (one of the three in which the leg was cut
    off). Not fixed: game tests could not reproduce it.
- **A dozen at once.** Twelve awake cost 7.5 to 9 ms of the 50 ms server tick for about ten seconds; resting, under a
  millisecond. Four dozen awake cost about 27 ms at first, and all rest within fifteen seconds. Making a carcass costs about 12 ms in the tick of
  the kill. Hung carcasses never rest: a dozen on hooks cost about 5 ms of every tick for as long as they hang, so a line
  of four dozen would cost about 20 ms all the time. ARCHITECTURE 3.4's cap is still open and was not built; settle it
  with decision 3.
  - It found that a spider carcass never rested (its legs twitched for ever). Fixed: a carcass that stays where it lies
    for five seconds rests, however it twitches.
  - It found that a rabbit spun on its hook and could not be hoisted (the belly-out spring was too stiff for so light a
    torso). Fixed.
- **Nine tests** for what was built but unproven, and four for the hanging fix (the hook, a hook read back part way up,
  a body caught under something, the trolley). Each was also run with its feature or fix taken out, and failed.
  - The Grappling Spool test found that a reeled-in carcass often ran out of time a step short of your hand and was
    never handed to the drag. Fixed: a carcass has four seconds more to arrive than it had (five more than a mob).
  - Not built: the Guillotine's limbs reach a minion or a wall hook only through a player's hands (package 4, decision 9).

**Why.**
- "Verify things by running them, not by reasoning about them … actually look at anything visual on screen."
- "Cheap enough that a dozen at once is fine."
- "It has to look right in multiplayer."

**What to build.**
- **A two-client check.** Drag, hang, butcher and bleed a carcass, and watch it from the other client. ARCHITECTURE 11's slice 1 check was never recorded as done.
- **A dozen carcasses at once.** Measure the cost with all of them awake and all of them resting, then decide whether ARCHITECTURE 3.4's per-dimension cap is needed.
- **One test each for things that are built but unproven:**
  - a fan speeds up bleeding (today, removing that line of code would still pass every test);
  - a skeleton cannot be skinned;
  - blood never makes a source block;
  - the Deglover skins a single limb;
  - a Guillotine's limb goes all the way to a minion and to a wall hook;
  - a kill without the hook leaves no carcass;
  - a piece rides a belt;
  - a Magnet Coil at high spool draws in a carcass;
  - a Grappling Spool hands a carcass to the drag tether.

**Size.** Medium.

**Touches.** Game tests and the showcase.

**Owner first.** No.

### 19. Sound and art

**Why.** "As for visuals and sound effects I want everything squishy, gross, gory, bloody." Final art is still open (section 12).

**What is there.**
- All ten of the mod's sound events are vanilla stand-ins (slime, bone block, honey, anvil, beacon hum). The mod has no sound files of its own.
- Much of the art is placeholder:
  - organs drawn by a script;
  - armour tiers with no look of their own yet (15.7);
  - a minion's empty arm socket, which looks like a plate from behind (docs/NEXT.md);
  - machines with no moving parts (package 15).

**What to build.**
- Sounds of our own, with bloodless versions (package 9).
- Final art.

**Size.** Medium, mostly art.

**Owner first.** No.

## Built and tested

One line each. Details and test names are in ARCHITECTURE sections 13 to 15.

- **Target:** Minecraft 1.21.1, NeoForge 21.1.249, Create 6.0.11, Sable 2.0.5, Java 21, mod id `bloodandbones`, plus the three addons agreed in the first review.
- **Mob groups:** 11 archetypes, 27 families and 16 overlays, and all 79 vanilla mobs resolve.
- **Bloodless mode:** a client setting and a gamerule, covering words, textures, particles and stains.
- **The kill:** a Meat Hook kill becomes a ragdoll carcass at once, for 79 mobs and their babies.
- **Carcasses are parts, not a stage:**
  - picked up if light, dragged if heavy;
  - severed, skinned, butchered and bled in any order, with the torso last;
  - finished when nothing is left.
- **Skeletons have no blood:** 20 mobs are bloodless.
- **The resting form:** a still carcass costs almost nothing, and wakes when hooked, punched or left unsupported.
- **Hanging:** a Shackle Hook keeps the carcass a ragdoll and bleeds it into a rack below.
- **Rot:** by game time; cold slows or stops it; the carcass crumbles at the end; freshness drives yields and shows in Create's filters.
- **Looks:** carcasses wear the mob's own texture, variants, wool, markings and clothes.
- **Rigs:** generated from vanilla models by a data run.
- **The Mangler:** grinds carcasses and loose limbs into meat, bone, offal, fat, and scraps stamped with the mob and the part.
- **The other machines:** the Beheader, Guillotine and Deglover each take their part.
- **The Bleeding Rack:** drains into a tank with no power; nether mobs bleed soul blood.
- **Furniture:** the Surgery Table and its two attachments, the Specimen Jar, Steel Tables that join into a run, and Steel Racks with shelves.
- **Blood:** blood and soul blood work as Create fluids through pipes, tanks, spouts and drains.
- **The Fluid Backtank:** seven tiers, worn on the chest, filled by pipe, Spout or Backtank Port, and powering implants.
- **Carcass armour:**
  - scraps by family, hides that modify a piece, organs that add abilities;
  - mobs freely mixed, a full set's bonus and drawback, and three tiers.
- **Decoration:** the Gut Chain (on chain conveyors too), ribcage arches, bone piles, and three bloody casings.
- **Chain trolleys:** carry hung carcasses along Create's chains and queue.
- **The body:** a set of slots, with crude prosthetics that give back exactly what flesh does. The safety floor never needs a surgeon or blood.
- **Amputation:** only at the table, with a surgeon minion (villager or pillager head). A ragged stump costs a bucket of blood to fit; the heart can only be swapped. (Since tasks' stage D: any minion with a hand set to Surgeon may cut by default, the owner's call in docs/NEXT.md 1.5, and its stump costs one to three buckets by its fitness.)
- **Empty-slot penalties:** a missing arm means no off-hand and slow swings; a missing leg means no sprinting.
- **Necrosis:** on organic implants, from swinging, mining and sprinting (not walking), cleared by blood perfusion. At the maximum the limb stops working, but never falls off or kills.
- **The surgery screen:** a paper doll of the body and every augment you carry that fits, with nothing over the body in the outside view (package 11).
- **Cybernetics:** the throttle (gauge, pitch, glow), all seven modules, no redstone-link module, and both set bonuses.
- **Minions from carcass parts:**
  - the torso sets size and health;
  - the legs set movement: spiders climb, rabbits are fast, horses can be ridden;
  - the arms set the strike;
  - an organ adds a special.
- **Power:** flesh minions drink at a Blood Trough they can reach; brass minions run on Soul Canisters swapped by the Charging Cradle; each kind has something the other lacks.
- **Never destroyed:** a neglected or beaten minion powers down and lies where it is. The minion cap defaults to none.
- **Contraptions and ships:** every block moves on a Create contraption keeping what it holds; a hung carcass rides in its
  hook; on a Sable ship a hook holds its carcass and a resting carcass stays pinned to the deck as it moves (package 8).
- **Checked by running it:** two clients on a dedicated server see the same carcass, and a dozen carcasses at once cost
  a sixth of a server tick until they rest (package 18). Hung carcasses do not rest; a line of them needs the cap
  (package 18, decision 3).

## Different from the brief by recorded decision (not gaps)

- **Rigs:** one per mob, generated from the game's own models (section 12). A per-group fallback is still wanted (package 2).
- **Bleeding Rack:** a tray under a Shackle Hook or a trolley, rather than something that hangs the carcass itself.
- **Soul netherite:** made with a super experience block instead of a second bucket of liquid experience, and renamed (sections 8 and 12).
- **Backtank:** a generic Fluid Backtank in seven tiers, placed as a block, with a Port for wearers who have a Port Arm (sections 8 and 12).
- **Cold:** Create has no cold system, so cold comes from Dragons Plus (sections 12 and 13.3).
- **Body slots:** lungs and stomach were added (section 12).
- **Cladding:** Create 6 has no cladding, so the blood-stained variants are casings (section 7).
- **Surgery on mobs:** a mob on the table is cut without a surgeon, because the ritual is about the player's own body (14.12).

## Checked and corrected

These are the auditors' items that were changed after checking the code, and the findings this page adds.

**Corrections:**
- **The safety floor and glowstone.** One auditor flagged the glowstone in the Surgical Rig's recipe as a soft Nether requirement on the way back to baseline. It is not a hole in the floor:
  - a part can only be lost at a table with a Surgical Rig;
  - the same rig fits the crude part;
  - the crude parts themselves need nothing from the Nether.
- **The Meat Hook's sweep.** It does sweep, but a sweep deals 1 damage without Sweeping Edge, so sweep kills are rare.
- **Contraption tests.** The list of blocks without one is longer than reported: it also includes the Butcher's Table, a placed backtank and the Backtank Port.
- **Static machines.** All four machines are static, not only the Deglover.
- **No belt input.** Confirmed that the machines take nothing from belts or funnels; moved into package 4.

**Added:**
- **Upkeep, minions and cybernetics.** The relayed list stopped at upkeep, so these were checked for this page. New findings:
  - brass implants beat flesh ones on every figure;
  - one tank runs only one kind of implant;
  - player implants do not use carcass limbs;
  - the brief's flesh-and-brass table is missing rows.
- **Elsewhere:**
  - every sound is a vanilla stand-in;
  - bloodless mode leaves the flesh implant and severed part names unchanged;
  - recipes are hand-written, although section 8 says they should be generated.

**Re-checked in the code and holding:**
- the drag figures;
- routing never being set, and `resume()` never being called;
- no quality and no weight class anywhere;
- the Blood Diamond recipe;
- soul blood's tags;
- machine stress and pace;
- the fan's unused processing type;
- pieces not rotting as items;
- the Butcher's Hook refusing everything but light pieces;
- the counts of rig target overrides;
- the hard-coded skull map, baby yields and blood per weight;
- the resting support check reading world blocks only.