# Create: Blood & Bones — Technical Proposal

**Status: decisions from the first review are folded in (marked *decided*); a few items remain open
(§12).** Everything marked *verified* was checked by reading the actual Create 6.0.11
(`mc1.21.1/dev`), Sable 2.0.5, Sable Companion, Create Aeronautics/Simulated 1.3.2, Sable Player
Ragdoll 0.7.5, Create Enchantment Industry 2.5.3, Create: Dragons Plus 1.11.7, Create Diesel
Generators 1.3.15 and vanilla 1.21.1 (decompiled) sources, or by running a build.

The design brief (what the mod *is*) is the source of truth; this document is only about how to build
it on what Create, Sable and the other addons actually provide.

---

> **Working rule (from the user):** reread `docs/DESIGN-BRIEF.md` before starting any new piece of
> work. Where a later decision recorded here differs from the brief, the later decision stands; anything
> the brief says that no decision has changed is binding.

## 0. Summary of what was verified

| Area | Result |
|---|---|
| Toolchain | A NeoForge 21.1.249 project compiling against Create `6.0.11-300`, Sable `2.0.5`, Flywheel 1.0.6, Ponder 1.0.85, Registrate MC1.21-1.3.0+67 **builds** (ModDevGradle 2.0.146, Gradle 9.2.1, Java 21). Cold build 4m52s, incremental 8s. Create's published access transformer is consumable by ModDevGradle. |
| Sable physics | Server-only Rapier scene per dimension. Two kinds of rigid body: block **sub-levels** (networked, persisted, buoyant, interpolated on clients, usable by every Aeronautics tool) and non-block boxes (none of that). Joints between any two bodies or the world: `Fixed`, `Free`, `Rotary`, `Generic` with per-axis locks, **limits**, PD motors, re-anchorable frames, contact toggling, impulse readback. Impulse-at-point, velocity, teleport, wake-up. |
| Sable gaps | No sleep API (Rapier auto-sleep only), no collision events, no capsule/sphere colliders, no entity<->body binding, no attaching a body to an ordinary Create contraption (only to Aeronautics sub-levels). Per-block physics data is datapack JSON. |
| Existing ragdolls | Sable Player Ragdoll builds each limb as a block sub-level and derives mob rigs by reflecting over the model on a client, trusting the packet. Reference only. |
| Create kinetics | `KineticBlockEntity` API unchanged. **Stress registration moved**: `BlockStressValues.IMPACTS/CAPACITIES`; Create's `CStress` builder transforms throw for non-Create mod ids (as do `BuilderTransformers.backtank/encasedShaft/...`). No block "winds up under rotation and fires on a redstone edge"; the Guillotine is composed from Weighted Ejector (wind-up), Sequenced Gearshift (rising edge), Pressing cycle. |
| Chain conveyors | Internal, no api package. Only `ChainConveyorPackage(ItemStack)` rides; consumers assume `PackageItem`. The model on the chain **is the item model** keyed by item id in a public map that we must populate. Mid-chain sinks must be `FrogportBlockEntity` subclasses. Chain strip is raw quads with a hard-coded texture. |
| Recipes/fluids | Codec-driven; addon types register via `IRecipeTypeInfo` and can be sequenced-assembly steps. Create's mixer/press/spout only run Create's own types. Fan processing types are a registry. No callback for "a fan is blowing on this block". New fluids are finite by default. Recipe JSON must be generated (Create's fluid ingredient format requires a `type` field and is slated to change). |
| Backtank | Create's is hard-wired to integer air; no fluid anywhere. Our fluid backtank is its own item/block/BE/overlay following the same pattern. |
| Vanilla models | Rig data is fully obtainable from baked `ModelPart` trees via `LayerDefinitions.createRoots()` + `ModelPart.visit()` with no reflection; naming is consistent enough for automatic joints with a documented exception list. |
| Addons | Enchantment Industry: fluid `create_enchantment_industry:experience`, items `super_experience_block`/`super_experience_nugget`. Dragons Plus: fan processing type + recipe type `create_dragons_plus:freezing`, passive freezer block tag. Diesel Generators: `createdieselgenerators:basin_fermenting` (basin + Basin Lid; fluid in, item out) and `bulk_fermenting`. Mavens: `maven.dragons.plus/releases` for the two DragonsPlus mods; Diesel Generators via the Modrinth maven. All three are LGPL/MIT, Create 6.0.10+, NeoForge ≥21.1.228. |
| Cold | Create itself has no cold system; Dragons Plus provides bulk freezing, which is what we build on. |

---

## 1. Target and toolchain (verified, *decided*)

- Minecraft 1.21.1, NeoForge **21.1.249**, Java 21, mod id `bloodandbones`, ModDevGradle 2.0.146.
- Hard dependencies: Create `6.0.11-300`, Sable `2.0.5`, Create Enchantment Industry `2.5.3b`,
  Create: Dragons Plus `1.11.7b` (Enchantment Industry requires it anyway), Create Diesel Generators
  `1.21.1-1.3.15`. Flywheel is a client-side requirement inherited from Create. Catnip ships inside the
  Ponder jar that Create bundles; nothing extra is declared.
- Create Aeronautics is **not** a dependency, but the mod must work on and with it (§3).
- Licences: Sable is PolyForm Shield (fine for addons). Enchantment Industry and Dragons Plus are
  LGPL-3.0; we only depend on them, we do not copy code. Diesel Generators is MIT. Sable Player Ragdoll
  is reference only. Our own licence: MIT (*decided*: others may build on it with credit).

---

## 2. Architecture overview

```
                     ┌──────────────── data (datapack JSON) ────────────────┐
                     │ mob groups / weight classes / part lists / rigs /    │
                     │ recipes / physics block properties / yields          │
                     └──────────────────────┬───────────────────────────────┘
                                            │
   ┌────────────┐   kill w/ Meat Hook   ┌────▼─────────┐  Shackle Hook  ┌────────────────┐
   │ living mob │ ─────────────────────►│ CARCASS      │───────────────►│ carcass ITEM   │
   └────────────┘                       │ (Sable       │◄───────────────│ (PackageItem   │
                                        │  sub-levels) │  drop/unhook   │  subclass)     │
                                        └──────┬───────┘                └───────┬────────┘
                                               │ tether / hooks / hanging       │ chains, belts,
                                               │ fans / contraptions / tools    │ vaults, machines
                                        ┌──────▼───────┐                ┌───────▼────────┐
                                        │ Sable physics│                │ Create machines│
                                        │ (server)     │                │ (kinetic BEs)  │
                                        └──────────────┘                └────────────────┘
```

Two representations, one state record:

- **Carcass state**: entity type, variant NBT snapshot, rig id, group, weight class, quality
  (intact/damaged), freshness, blood remaining, and the *set of parts remaining*. Stored as one data
  component on the item and in the carcass's own saved data while physical.
- **Carcass (physical)**: one Sable sub-level per limb, joined by Sable joints, owned by a small
  server-side record (not a living mob).
- **Carcass item** (a `PackageItem` subclass) once it leaves a player's hands into Create logistics.

The Shackle Hook is the transition point in both directions.

---

## 3. Carcass physics on Sable (*decided*: one sub-level per limb)

### 3.1 Why sub-levels (in plain terms)

Sable can simulate two kinds of things: block structures ("sub-levels", what Aeronautics ships are)
and invisible boxes. Both are Sable, both collide with Aeronautics ships and ordinary Create
contraptions. The difference is what comes for free:

| | Sub-level per limb (chosen) | Box per limb |
|---|---|---|
| Sent to other players, interpolated smoothly | by Sable | we'd write it |
| Saved with the world, survives chunk unload | by Sable | we'd write it |
| Floats or sinks by material volume | by Sable | we'd write it |
| Aeronautics ropes, winches, grapple plungers, physics staff work on it | yes | no |
| Can be jointed to an Aeronautics ship | yes | yes |
| Rides an ordinary Create gantry/piston | by friction | by friction |
| Cost while idle | a reserved chunk per limb | almost nothing |

Your requirement is Aeronautics compatibility, and that only comes fully with sub-levels. The price is
memory per limb, which §3.4 manages.

### 3.2 What Sable gives us (verified)

- A sub-level per limb: assembled from one or more invisible "limb blocks" whose physics collider is
  the limb's box (`BlockSubLevelCollisionShape`), mass and volume from `physics_block_properties`
  per blockstate (this is how weight class turns into sinking or floating: heavy classes get volume
  below their mass, light classes above).
- Joints: `Generic` with linear axes locked is a ball joint; `setLimit` on angular axes gives hinges
  (knees, elbows, jaw) and cones (hips, shoulders, neck); `setMotor` gives muscle tone and, for slimes
  and shulkers, springiness; `setContactsEnabled(false)` stops adjacent limbs fighting;
  `getJointImpulses` is the tear-off signal; `setFrame1` re-anchors a joint at runtime (the tether).
- `applyImpulseAtPoint`: the killing blow, transformed into the struck limb's frame.
- Each limb's anchor block entity implements `BlockEntitySubLevelActor` for per-physics-tick logic and
  `sable$getConnectionDependencies` so all limbs of one carcass load and unload together.
- Sable's own snapshot sync, client interpolation, save/restore and NaN recovery apply.

### 3.3 Proposed design

**Server**

- On a Meat Hook kill: the mob dies normally (loot, XP, advancements), then a `Carcass` record is
  created: type, variant NBT, rig from data (§4), death pose captured from the model. Limb sub-levels
  are assembled at the mob's position and posed, joints attached, the killing-blow impulse applied at
  the hit point plus inherited velocity. The carcass id lives in each limb sub-level's user-data tag
  and in one `SavedData` map (limb ids, joint topology, part set), re-attaching joints on load the way
  the reference mod does, but as a group via connection dependencies.
- **Tether (Meat Hook drag)**: a joint from the world (updated each tick to the player's hand, via
  `setFrame1`) to the grabbed limb, with linear limits equal to the rope length. Hook a hind leg and
  that limb is pulled; the rest follows through the joints, so the animal turns rear-first. Nothing is
  scripted. Speed penalty by weight class is an attribute modifier on the player while tethered.
- **One-block step**: pulled horizontally, a limb box catches on a full step. The tether adds a small
  upward bias when the grabbed limb is blocked at foot height (read from joint impulses); if that does
  not feel right in slice 1, a short "hoist" impulse on the grabbed limb.
- **Hanging**: same joint with linear axes locked at the hook point. On a static hook the anchor is the
  world; on an Aeronautics ship the joint is made to the ship's body directly (Sable supports this); on
  an ordinary Create contraption the anchor follows the hook's world position each tick.
- **Removing a part** removes that limb's sub-level and joint; the rest hangs differently because the
  mass is gone.
- **Fans**: the carcass is not a Minecraft entity, so Create's fan does not fling it; we read the fan's
  current and apply it as a force (and use it for the Bleeding Rack bonus).

**Client**: nothing custom for motion. Limb rendering is a block-entity renderer on the invisible limb
block that draws the mob's own model parts for that bone with the mob's own texture (§4.4), posed by
Sable's interpolated sub-level pose. Removed parts are not drawn.

### 3.4 Keeping it cheap (*proposed*)

Each sub-level reserves a plot chunk and a chunk ticket even while asleep, so a dozen six-limb
carcasses is ~72 sub-levels. Mitigation, in order:

1. **Resting form**: after a carcass has been still for a few seconds and nothing is acting on it, its
   limbs are merged into **one** rigid sub-level (all limb blocks in one plot, joints removed, poses
   baked in). It is still a physics object (pushable, floats or sinks, rides ships, Aeronautics tools
   work). When hooked, hit, or dragged it splits back into articulated limbs at the same poses.
   Cost drops from N to 1 per carcass.
2. Sable's own sleeping handles the physics cost of resting bodies.
3. A per-dimension cap on live articulated carcasses (config), beyond which the oldest rests.
4. Rot removes carcasses entirely over time (§5).

### 3.5 Contraption riding

- Aeronautics ship: limbs collide with it, get carried, can be jointed to it (hooks on ships work
  natively). The limb blocks are tagged so Sable keeps them inside a ship's plot when hung there.
- Ordinary Create contraption (gantry, piston, bearing, train): carried by friction (Sable injects the
  contraption's velocity into contacts, verified). A carcass hanging on a hook that a contraption picks
  up becomes hook data (Create has no way to attach anything but a seat to a contraption block,
  verified); the hook's renderer draws it while moving, and it is re-spawned as limbs on disassembly.
- Chain conveyor: the carcass is an item there (§6).

### 3.6 Risks to retire first

- Limb sub-levels are built from blocks; collision is block-shaped colliders sized to the limb, not
  full blocks (the reference mod does this and it works). The unknown is feel: joint limits and
  motors need tuning on screen.
- Sable's API is small and moves between versions; all Sable calls go through one adapter class and
  the version is pinned.
- Physics engine failing to load on a client (natives): see §12.

---

## 4. Rigs derived from models (*decided in principle*; plain-English question in §12)

Joints come from the model; nothing lists joints by hand. Rig files are generated; a per-mob override
file can adjust one.

### 4.1 Source of truth

Vanilla exposes every mob model as a `LayerDefinition` reachable through `LayerDefinitions.createRoots()`
(static, includes modded registrations via NeoForge's hook). Baking gives a `ModelPart` tree with, for
every part: name, parent, rest pivot, cube bounds. `ModelPart.visit()` walks it with names and composed
matrices. Verified, no reflection needed.

- **Datagen step** (a client-side run) bakes every layer, runs each model's rest pose, walks it, and
  emits `data/bloodandbones/rig/<namespace>/<entity>.json` for every vanilla mob. Deterministic,
  reviewable, server-authoritative.
- **Unknown modded mobs on day one**: group heuristics (leg count if known, otherwise hitbox size and
  category) pick an archetype whose *generic rig* (torso, head, four legs) is scaled to the hitbox, so
  any mob works immediately, approximately. A client that has rendered the mob can extract its real
  rig with the same walker and send it; the server accepts it only as a cache for that entity type,
  validated against caps, only to upgrade from the generic rig. Pack authors can also run
  `/bloodandbones rig export <entity>` client-side to write the JSON for a datapack.

### 4.2 Bone vocabulary and derivation rules

Canonical bones: `TORSO`, `TORSO2`, `NECK`, `HEAD`, `JAW`, `TAIL[n]`, `{LEFT,RIGHT}_{FRONT,MID,HIND}_LEG`,
`{LEFT,RIGHT}_ARM`, `{LEFT,RIGHT}_WING`, `SEGMENT[n]`, `TENTACLE[n]`, `DECOR` (never a body).

Resolver, first match wins on the full part path: `head`, `real_head`, `head_parts/head`, `body/head`,
`neck/head` → `HEAD`; `neck`, horse `head_parts` → `NECK`; `body`, `body0`, `bone` → `TORSO`;
`upper_body`, `body1` → `TORSO2`; `*_hind_leg`, `*_front_leg`, `*_mid_leg`, `*_leg`, `leg0..7`,
`*_haunch` → legs; `*_arm` → arms; `*wing*` → wings; `tail`, `real_tail`, `tail1/tail2`, `tail_base/tip`
→ tail chain; `segmentN`/`cubeN` → segment chain; `tentacleN` → tentacle chain; `saddle*`, `*_chest`,
`mane`, `reins`, `bridle`, `*_ear`, `*_horn`, `nose`, `mouth`, `goatee`, `hat*`, `jacket`, `*_sleeve`,
`*_pants`, `*_fin`, `*_gills`, `*_bristle` → `DECOR` (rendered with their parent bone). Zero-thickness
cubes never become colliders.

Three derivation rules cover most of the mess (all verified against the full vanilla survey):

- **Virtual parent for flat trees.** Most vanilla models put every part directly under the root. Legs,
  head and tail are re-parented to the torso by name; only bat, phantom, dolphin, warden, camel,
  sniffer, armadillo and bee express real parent links.
- **Joint at the child's pivot, with a fallback.** When a child's pivot coincides with the parent's
  (cod/salmon head, guardian tail, wither heads, iron golem arms), the joint moves to the face of the
  child's cube nearest the parent's cube.
- **Rest pose from the model, not from `PartPose` alone.** Spider leg splay, parrot tilt and wing
  fold, bee wings/legs and blaze rods are set in code every frame; the exporter runs the model's pose
  code with zero limb swing before reading pivots.

Collider = union of the bone's cubes (empty wrapper parts collapse into their cube-bearing children),
scaled by an explicit per-entity `render_scale` (horse 1.1, donkey 0.87, mule 0.92, cat 0.8, polar bear
1.2, husk 1.0625, wither skeleton 1.2, giant 6, cave spider 0.7, ghast 4.5, elder guardian 2.35, player
and villagers 0.9375, plus the scale attribute), seeded at datagen and overridable in data. Joint type
and limits come from the **archetype** file keyed by bone role, not from the model.

### 4.3 Mobs needing override files (verified)

Wolf, horse family, rabbit, ocelot/cat, goat, sniffer, strider, armadillo, llama, polar bear, ravager,
villager/wandering trader/witch (arms are one folded block), illagers, iron golem, creeper, enderman,
warden, allay/vex, spider/cave spider, bee, chicken/parrot, bat, phantom, axolotl/turtle/frog/tadpole,
guardian, squid, pufferfish, tropical fish, wither. Details per mob are in the survey notes and become
the override files.

### 4.4 Special mobs (*decided*)

- **Slimes / magma cubes**: killing a big one still splits it into smaller slimes as normal. The
  smallest slime killed with the Meat Hook becomes a single gooey one-block carcass: one sub-level,
  rendered with a squash-and-stretch wobble driven by its velocity (Sable bodies are rigid; the goo is
  visual), draggable and butcherable (slime parts).
- **Shulker**: three sub-levels (top shell, bottom shell, inner creature) joined by springy joints
  (motors with low stiffness), so it jiggles.
- **Snow golem**: three parts, two stiff joints. In a hot biome or near heat it melts fast: parts
  shrink and vanish, leaving the pumpkin as a one-block sub-level if it wore one, nothing otherwise.
- **Ghast**: the big box is the torso; each tentacle is its own articulated chain of segments.
- **Later, not never**: warden (has a body plan; deferred as boss-tier), ender dragon, wither, blaze,
  breeze. They stay on a deny tag until then and drop loot normally.

### 4.5 Babies (*decided*: a baby carcass looks exactly like the living baby)

Vanilla scales baby models in four different ways (per-model head/body groups, uniform, llama's
per-axis groups, horse's separate baby legs). Rather than re-implement each, the rig exporter captures
the model twice, adult and baby, by rendering it into a capturing vertex consumer, so every part's
real rendered position and size is recorded for both. A baby carcass uses the baby rig and the baby
render transform; nothing changes visually at the moment of death.

### 4.6 Texture

Carcass rendering keeps vanilla cubes with their baked UVs and uses the entity renderer's texture for
the variant. Secondary layers (sheep wool, pig saddle, llama decor, horse armor) are separate baked
trees; "degloved" is "stop drawing the fur/hide layer and swap the body texture to the flesh texture"
(one placeholder flesh texture per archetype early on).

---

## 5. Data model (*proposed*)

All datapack JSON, all overridable:

- `mob_group/<id>.json`: archetype, member entity types or tags, weight class, default part list with
  yield tables, blood volume, freshness half-life, joint limit table per bone role, generic rig.
- `weight_class/<id>.json`: limb mass, limb volume (heavy classes sink, light classes float), drag
  penalty, hook size, chain clearance (§6).
- `rig/<ns>/<entity>.json` (generated) and optional `rig_override/<ns>/<entity>.json`.
- `carcass_yield/…`: what each part gives per station; hand ≈ half with random loss; quality penalties.
- Physics block properties for our limb blocks and machines (Sable's format).
- Recipes as Create/addon JSON (§7, §8), generated by datagen.

A specific mob is never required to have a file.

**Rot** (*decided*): game-time based while loaded; unloaded chunks do not rot. Cold keeps a carcass:
Dragons Plus's freezing tag (`passive_block_freezers`, plus its fan freezing current) counts as cold, so
snow, ice and a freezing fan preserve carcasses; a `bloodandbones:cold_sources` block tag lets other
mods join. At full rot the carcass melts away, leaving a stain.

---

## 6. Shackle Hook and chain conveyors (*decided*: routable by frogports, with clearance)

- **Shackled carcass item** = `PackageItem` subclass per archetype × weight class with its own
  `PackageStyle` and shackle rigging model; at client init we register its box and shackle models in
  Create's two public partial-model maps under our item ids (verified necessary). Removed from
  `PackageStyles.STANDARD_BOXES/ALL_BOXES` so packagers never emit it. Routable by ordinary frogports
  and addresses like any package.
- **Ground clearance** (*decided*: hanging length plus one block): a carcass only leaves a hook onto
  a chain, and only passes a chain segment, if the chain's height above the ground along that segment
  leaves at least the carcass's hanging length plus 1 block clear (hanging length per weight class). The Shackle Hook checks the target segment
  before exporting; segments that are too low refuse the package, so it waits at the hook.
- **Shackle Hook** (on-ramp): a `PackagePortBlockEntity` subclass using Create's own
  `ChainConveyorFrogportTarget`, so capacity, speed, reversal and routing come for free.
- **Stations** (off-ramps): each has a frogport-subclass hook with its address filter = part filter.
- Dropped carcass items become the physical carcass again (replacing Create's package entity).
- **Gut Chain** (*decided*: texture swap first): a small render-time swap of the chain texture per
  conveyor. Physical decorations riding the chain can come later as decorative packages.

---

## 7. Machines (*proposed*; each mapped to a verified Create pattern)

Common: `KineticBlock` + `KineticBlockEntity` (or `SmartBlockEntity` when unpowered), a
`FilteringBehaviour` for the part filter, our own `IRecipeTypeInfo` enum, stress through
`BlockStressValues` registries backed by our own config. Processing time scales with speed the
Millstone way, gated on `getSpeed()==0`.

| Machine | Pattern |
|---|---|
| Deglover | Kinetic, large base impact (stress cost is linear in RPM, so high impact makes low RPM the sane operating point); takes a carcass or limb from a belt or its own hook; filter = hide. |
| Guillotine | Ejector-style state machine (WINDING→ARMED→DROPPING→RESET), wind rate ∝ speed, drop on a **rising redstone edge** via the Sequenced Gearshift pattern. |
| Beheader | Inline continuous belt processor like the Mechanical Press over a belt. |
| Mangler | Terminal grinder modelled on Crushing Wheels; low-grade pile + vanilla loot roll. |
| Bleeding Rack | Unpowered, internal tank exposing a fluid capability (pipes work), partial collision shape (a full cube stops fan air), polls for an encased fan's current and scales drain by its speed. The hanging carcass stays a live ragdoll. |
| Spit Roast | (*decided*) A base with a shaft input on top; anything kinetic connects there, including Create's Hand Crank. Cooking progress ∝ speed. |
| Surgery Table | One block; attachments swap a blockstate and recipe set; recipes implement `IAssemblyRecipe` so minion assembly and organ extraction can be sequenced chains. |
| Specimen Jar, Steel Table/Rack, wall Meat Hook | Decorative; the wall hook renders any carcass/part with the same renderer. |

**Filters.** Create's plain filter ignores data components (verified), so part filtering uses custom
item attributes (`has part: hide`, `archetype`, `quality`, `fresh`) that work on every machine and on
Create's own funnels and frogports.

**Decoration.** Create 6 has no "cladding"; blood-stained variants are casings (connected-texture
blocks) built with Create's casing builder and our own sprites, plus a small blood-stained palette.

---

## 8. Blood, Soul Blood, materials, backtank

- `blood`: our own Create-style fluid, tagged `c:blood`, finite by default. `soul_blood`: separate.
- **Soul Blood chain** (*decided*): blood pumped into a basin with a Diesel Generators **Basin Lid**
  ferments into a congealed blood block (`createdieselgenerators:basin_fermenting`: fluid in, item out,
  verified format) → **haunted** under a fan with soul fire (`create:haunting`) → re-melted in a
  heated basin (`create:mixing`, superheated) into soul blood. Trickle path: bleeding nether mobs
  yields soul blood directly.
- Blood Steel: `create:filling` (iron + 250 mB blood). Blood Diamond: sequenced assembly with a spout of
  1000 mB blood and a spout of 1000 mB Enchantment Industry liquid experience. Soul-Blood Netherite
  (*decided*): sequenced assembly with a spout of 1000 mB soul blood and a deployer applying one
  Enchantment Industry **super experience block**.
- Cold: Dragons Plus bulk freezing (`create_dragons_plus:freezing` recipes and its freezer tag) is
  the cold source for carcass preservation and any "frozen" recipes we add.
- **Fluid Backtank** (*decided*, replaces "Blood Backtank"): a generic wearable tank holding any fluid
  in the game, in tiers (buckets): copper 2, gold 3, iron 4, diamond 6, blood steel 8, blood diamond 16,
  **soul netherite** 32 (the material is renamed "soul netherite"). Refilled from a port block (below)
  or by placing the backtank as a block next to a pipe with a pump. Holding blood powers
  organic prosthetics; holding soul blood powers cybernetics. A cybernetic module ("Vent Arm", name
  open) sprays the tank's contents as an effect chosen by fluid tag: experience gives XP, lava and
  anything tagged as fuel is a flamethrower, water/potions splash, others just spill. Effects are a
  data map from fluid tag to effect, so other mods' fluids slot in.
- **Backtank Port** (*decided*): a placeable block like a pump with a direction. A player with the
  port cybernetic must stand next to it; fluid then flows out of the worn tank into the pipe network
  or from the network into the tank depending on the port's direction. A backtank placed as a block
  also connects to pipes directly (pump toward it to fill, away to empty).
- All recipe JSON is produced by datagen through the builders, never hand-written.

---

## 9. Bloodless mode (*decided*: client toggle **and** a server gamerule)

A `ConfigBool` in a client config, plus a gamerule that forces bloodless presentation for everyone on
a server (the client reads "server forces it OR I chose it"). Every renderer, particle and sound call
goes through one `Presentation` facade. Our particles implement the toggle inside their client-side
providers, not only at spawn sites, because some particles arrive from the server as packets
(verified). Names and descriptions have no Create hook: our items override their display name
client-side and our tooltip modifier re-reads the toggle. Lang keys are duplicated under a
`bloodless.` prefix. No logic path branches on it. Wired into slice 1.

---

## 10. Contraption safety (checklist, verified against Create 6)

- Shape methods must work with Create's wrapper level and no block entity; block entity data
  round-trips with `clientPacket` handled; nothing processes while moving unless it is an actor.
- Empty-collision blocks (wall Meat Hook, Gut Chain, Specimen Jar) tagged
  `create:movable_empty_collider`. Wall/ceiling-mounted blocks register an attached-check and a
  brittle-check in code (there is no attachment tag; a brittle tag alone leaves them behind on bearings
  and gantries). Custom orientation properties implement `TransformableBlock`.
- Storage: machines and hooks tagged `create:fallback_mounted_storage_blacklist`; tanks register a
  `MountedFluidStorageType` (no fluid fallback exists).
- Hooks with a hanging carcass inside a contraption: carcass becomes hook data (§3.5). Hooks are never
  tagged `create:seats`.
- Contraption actors that cut or hook mobs subclass `BlockBreakingMovementBehaviour` for its
  entity-damage path, which Sable already patches for ships.
- On Aeronautics ships: our blocks implement `BlockEntitySubLevelActor` only where needed, ship
  `physics_block_properties` for mass, and route world-position logic through Sable's helpers.
- The mod ships its own creative tab (Create's tabs only list Create's entries).

---

## 11. Vertical slices (order)

1. **Cow, Meat Hook, ragdoll, drag, hang, rest** (the physics spike): kill → limb sub-levels from the
   generated cow rig → killing-blow reaction → drag by any limb → one-block step → hang on a static
   Shackle Hook → resting form and split → survives relog → looks right with two clients → sits on an
   Aeronautics ship. Presentation facade and bloodless toggle in from day one. Placeholder textures.
   *Exit criterion*: a video-worthy cow and a written verdict on tuning and cost.
2. Shackle Hook → chain conveyor (with clearance) → Bleeding Rack → blood into a Create tank, fan bonus.
3. Deglover + Guillotine + Beheader + Mangler with filters, data-driven yields, Flensing Knife.
4. Groups and rigs for all vanilla mobs, weight classes, floating/sinking, rot and cold, special mobs.
5. Materials, Fluid Backtank tiers and port, Soul Blood chain via the three addons.
6. Armor → prosthetics safety floor → surgery → minions → cybernetics (incl. the fluid vent) → decoration.

Each slice ends with in-game verification on screen and automated tests for the things that fail
quietly (rig generation for every vanilla mob, carcass state round-trips, recipe validity, rest/split
persistence).

---

## 12. Resolved and open

**Resolved in the first review**: sub-level per limb; one body per bone capped at 12; Aeronautics
compatibility required; float/sink by weight class; special mobs (slimes, shulker, snow golem, ghast;
bosses, blaze, breeze later); Enchantment Industry, Dragons Plus and Diesel Generators as
dependencies; soul blood via basin-lid fermenting; super experience block for netherite; generic
fluid backtank with tiers, vent cybernetic and port block; frogport-routable carcasses with 1-block
clearance; rot by game time; cold via Dragons Plus; Spit Roast with a top shaft input; Gut Chain
texture swap first; bloodless as client toggle plus gamerule.

**Resolved in the second review**: physics-engine failure → carcasses appear as stiff statues that
can still be butchered; rigs are generated automatically from the game's own models, starting with the
**cow only** until the system is proven, then the rest; baby carcasses look exactly like the living
baby; licence MIT; backtank tiers copper 2 / gold 3 / iron 4 / diamond 6 / blood steel 8 / blood
diamond 16 / soul netherite 32; port block works with an adjacent wearer and with a placed backtank on
pipes; chain clearance = hanging length + 1 block.

**Resolved in the third review (Block 11, the body)**: the Surgery Table takes anyone: you, another
player, a mob or a carcass, and it works by hand with tools or powered like Create's machines. Players
really lose limbs, but only by choice at the table: nothing takes one at random, and surgery cannot go
wrong. Minions are built on the table from carcass parts, limbs and organs; what they are given decides
what they do, across a wide range of jobs. Cybernetics cover every body part. A prosthetic or cybernetic
that runs on the backtank stops working when the tank runs dry. The Fluid Backtank is worn in the chest
slot and has armour variants. The plan for building it is §14.

**Decided after the jobs were built (24 September 2026)**: no jobs. Any task, from a list drawn up with the owner,
can be given to any minion. Some minions do a task better than others because of the stats they get from what they are
built of. This replaces heads offering jobs (spec 6.4 and 6.9). It was built in six stages (docs/NEXT.md 1.10), all
done: the numbers (§15.21) and tasks in the jobs' place, with the task screen (§15.22); the levers and the surgeon's
stump price (§15.23); the Tender, the butcher at the Butcher's Table, and the fitness shown on the Surgery Table, in JEI,
on piece tooltips and by `/bloodandbones minion fitness` (§15.24); then a review's findings put right (§15.25). The surgeon (docs/NEXT.md 1.5) is still the owner's
call: by default any minion with a hand may cut, its fitness pricing the stump, and a datapack's surgeon task file with
`"needs_surgeon_head": true` limits the cutting to surgeons' heads (the villager and illager families', the witch's
among them), the brief's letter. Both are built and tested.

**Still open** (as of the latest build): everything up to and including machines, materials,
cooking, display and decoration is built and tested (§13). What remains needs design decisions
before code: the body-horror progression (Block 11: Surgery Table, prosthetics, minions,
cybernetics, and the Fluid Backtank and its port, whose tiers are decided above), a carcass riding a
hook on a Create contraption (§3.5), trolleys on chain conveyors that sit on a ship, the ender
dragon and tropical fish, middle-sized (size 2) slimes, and final art.

## 13. Slice 1 implementation notes (what is actually built, verified by the headless game tests)

- **Limb colliders.** Sable bakes a physics collider once per *block state*, with a fake level and no
  block entity, so a limb's shape must live in the state. `carcass_part` has `size_x/y/z` (1–16) and its
  box always fills `[0, size]` pixels from the block's minimum corner. A limb wider than 16 px on an
  axis is split into cells along that axis that hug the same corner, so the cells form one box
  (cow body 12×18×10 = two cells stacked on Y). 4096 states, one multipart blockstate applying an empty
  model. Mass, friction and restitution come from `physics_block_properties/carcass_part.json`.
- **Rig files** are generated at datagen from the vanilla baked model tree
  (`data/bloodandbones/rig/<ns>/<mob>.json`): every part with cubes is a bone, its biggest cube is
  the physics box, the biggest top-level bone is the torso and other top-level bones hang off it.
  Joint limits are picked by part name and can be hand-edited afterwards.
- **Placement math.** Vanilla draws model pixel `P` at `feet + (0, 1.501, 0) + G·P/16` with
  `G = rotY(180° − bodyYaw)·rotZ(180°)`. Each limb sub-level gets orientation `G·partRotation` and is
  moved so the part origin lands at `feet + (0, 1.501, 0) + G·partOffset/16`; the box minimum corner
  sits on the corner of the plot's center block. The renderer draws the part with its pose zeroed,
  translated by `−boxMin/16`, so the drawn cubes match the physics box exactly.
- **Assembly.** Limb cells are placed for one tick in free air near the top of the world above the
  mob, handed to `SubLevelAssemblyHelper.assembleBlocks`, then teleported into pose. Joints are
  `Generic` constraints with all three linear axes locked (a ball joint), contacts between the two
  limbs disabled, angular limits from the rig, and a light damping motor. Anchors are plot-space,
  frames are `parentRot⁻¹·childRot` on the parent and identity on the child.
- **Persistence.** Sable saves sub-levels but not joints. `CarcassSavedData` (per level) stores
  bone → sub-level id plus every joint spec; the root limb's `sable$tick` re-creates joints whenever
  the live handles are missing or invalid (world reload, chunk unload). Limbs list each other as
  Sable connection dependencies so they load and unload together.
- **Meat Hook kills**: `LivingDeathEvent` → assemble → cancel `LivingDropsEvent` and
  `LivingExperienceDropEvent` → discard the entity. Babies fall through to a normal death for now.
- **Not yet verified visually**: the renderer runs only on a real client. Everything above is
  covered by `runGameTestServer`.

### 13.1 Findings from the drag slice (verified)

- **Sable's physics runs in single precision** (`marten::Real = f32` in its Rapier build). Vanilla's
  headless test server places tests at random coordinates up to ±15 million blocks, where a float cannot
  represent half a block: bodies snapped by ±0.5, small motions were rounded away, limbs launched. Our
  test-only mixin pins game tests within 64 blocks of the origin. For players this only matters millions
  of blocks out, but it is worth knowing: never place carcass physics far from the origin in tests.
- **Fresh sub-levels have no collider for a few ticks** unless their plot sections are re-uploaded bound to
  the body. `CarcassAssembler.bindColliders` does what Sable's own plot loading and recovery do.
- **Joint motor targets are only honoured on a freshly created joint.** Aeronautics' physics staff and
  handle re-create their grab joint every tick, and so does `CarcassDrag`: a Free joint from the world
  origin to the hooked point with linear motors whose targets are the desired world coordinates.
- **`RigidBodyHandle.applyImpulseAtPoint(plotPoint, impulse)` applies an impulse (not a force) expressed in
  the body's local frame.** Verified by the `probeImpulseIsLocalFrame` game test.
- Game test templates sit one block above their structure block: `absolutePos(x, 1, z)` is the floor
  itself, the first air layer is `y = 2`. The test framework also walls each test in with barriers, so
  the arena is 11×7×11 to leave room for ragdolls and dragging.
- Limb mass is per block state, generated by `PhysicsPropertiesProvider` as volume × flesh density; the
  cow weighs about 0.81 in Sable units (a solid block is 1.0). The kill shove is 2 blocks per second for a
  cow, scaled by the square root of the weight ratio, strongest on the limb the attacker was looking at.
- **Drag visual.** The server broadcasts `DragSyncPayload` (player, limb sub-level id, hook point in plot
  space) on grab, release and every two seconds; `DragRenderer` draws the Meat Hook item stuck in the limb
  and a sagging line to the player's hand, using Sable's client-side interpolated pose for the limb.
- **JEI.** `BBJeiPlugin` registers an information page per item (placeholder text). Recipe categories
  come with the machines. JEI is compile-only for the mod and present in the dev runtime.
- **Dev runtime extras** (not mod dependencies): Create Aeronautics for its physics staff, Concentration
  for borderless fullscreen (Cubes Without Borders ships a multi-loader jar NeoForge refuses). The game
  tests pass with Aeronautics loaded.

### 13.2 Resting form (verified)

- A carcass that has lain still for 60 ticks (every body under 0.05 blocks/s and 0.1 rad/s, nobody
  dragging it, no hook holding it) **folds into one body**: the joints are removed, each limb's origin and
  orientation relative to the torso bone are remembered (`RestPose`, saved), coarse collision cells for the
  limbs are added to the torso's plot (one cell per block the limb reaches into, sized from the cell's
  minimum corner to the furthest sampled point, two legs sharing a block share a cell), the limb bodies are
  removed for good, and the torso's root cell draws every part from a `MergedPart` list.
- Sable has **no merge primitive** and `SubLevelAssemblyHelper.moveBlocks` only rotates in 90° steps, so
  rasterising is the only way to get limb collision into the torso; a plain `level.setBlock` into a plot
  works once the plot chunk exists (`plot.newEmptyChunk`), and new sections need their colliders bound
  (`bindColliders`) or they collide a few ticks late.
- The merged body is **pinned by a world joint with all six axes locked**. Without it the torso, having
  lost the legs that propped it up, settled 0.75 blocks lower and the remembered limb poses no longer
  matched. Pinning also means a resting carcass is inert until disturbed: the Meat Hook on any cell, a
  punch on any cell (`CarcassPartBlock.attack`), or losing whatever was under it (checked every second:
  nothing solid within a fifth of a block under its lowest corner) splits it (`CarcassRest.split`) by
  re-assembling each limb at torso-pose × rest-pose and re-attaching the joints, which now live in
  bone-local terms (`CarcassJoints.Spec`) so they survive new plots. Rest cells are found by an exact
  oriented-box test against each block, so 2 px wolf legs get cells too, and each cell is named after its
  limb so an unfold can sweep up cells an older save forgot.
- The fold itself runs from `LevelTickEvent.Post`, not from the root cell's `sable$tick`: that callback
  fires inside Sable's loop over every sub-level, and removing bodies there mutates the list being walked.
  Only the torso's root cell counts stillness; every limb's first cell ticks, and counting from all of
  them folded a cow six times too fast.
- Sable's `PhysicsConstraintHandle.isValid()` stays true for a joint whose body was just removed until the
  next physics tick, so losing a limb tears down every live joint explicitly and lets the root tick
  rebuild the survivors.

### 13.3 Rot (verified)

- `Carcass.freshness` runs from 1 to 0 over the rig's `rot_time` (a day for a cow, 5 minutes for a zombie,
  two days for a skeleton). It is driven by **game time, not ticks seen**, so a carcass in an unloaded
  chunk catches up (at most a day at once) when it loads again. The torso's root cell is told the value
  every five seconds and the renderer multiplies every pass by a grey-green tint.
- Surroundings are sampled every second within two blocks of the torso in the **world**, never in the
  plot (plot chunks keep the biome frozen at spawn). Anything in `bloodandbones:preserves` (packed and
  blue ice) or any block Create: Dragons Plus's `BlockFreezer.findFreeze` rates FROZEN or colder stops rot;
  anything in `bloodandbones:chills` (ice, snow, powder snow, plus CDP's `passive_block_freezers` tag) or a
  PASSIVE freezer quarters it. The biome's own `coldEnoughToSnow(pos)` rule gives 40%, a base temperature
  under 0.5 gives 70%, 1.5 and up gives 150%.
- There is no cold `HeatLevel` anywhere in Create or its addons; CDP's `BlockFreezer` is the exact cold
  mirror of Create's `BoilerHeater` and is what we build on.
- Flies (`bloodandbones:fly`, a particle that darts about where it was born) gather over a carcass
  below 45% fresh, more as it goes off; none while cold stops the rot, none over bloodless mobs.
  Below 15% maggots crawl over it: an overlay pass on the same model, two frames swapped every 0.3 s
  (`CarcassModels.drawMaggots`), hidden in bloodless mode.
- Once rotten it keeps counting (`Carcass.decay`, saved, at the same rate, so ice still stops it). At
  `crumble_after_days` (server config, a day by default) it falls apart at the end of the level tick
  (`CarcassRot.levelTick`, never inside a body's own tick): every piece drops half its table's yields
  with rot applied (bones, a little rotten flesh), the record is forgotten first so removing the bodies
  does not split it, then the bodies go. It waits while any unfolded piece is unloaded. `rot_speed`
  scales all rot; 0 turns it off.

### 13.4 Data-driven rigs for more mobs (verified)

- Rigs are still generated from the vanilla models, but **what to generate comes from
  `src/main/rig_targets/<mob>.json`** (read by the data run through the `bloodandbones.rig_targets`
  system property): texture (with `{variant}`-style placeholders), extra render passes, rot time, torso,
  hidden parts (saddles, baby legs), parts merged into the bone above them (a horse's head, mane and
  mouth fold into the neck), sibling parts attached to a bone (a chicken's beak and wattle, a zombie's
  hat), parent overrides (a wolf's head and front legs hang off the chest, the chest off the rear body),
  physics box overrides and joint overrides. Everything else follows the old rules.
- Rigs are **sent to clients** on join and data pack reload (`RigSyncPayload`, one packet per rig after a
  reset packet, only to clients that negotiated the optional channel), the way vanilla sends recipes. Root cells store only the mob id, the bone name and the resolved look; the renderer reads part
  paths, hidden children and attached parts from the client's copy of the rig.
- `CarcassLook` captures what the dying mob wore before it is gone: a sheep's wool colour
  (`Sheep.getColor`) and sheared flag, a horse's variant and markings, a wolf's variant texture (tame or
  wild). A rig's `passes` say which of those they use (`"tint": "wool"`, `"unless": "sheared"`), so a
  coat is data plus a small fixed vocabulary of things the code knows how to read off a mob.
- Facts that shaped the targets: the pig's and sheep's head boxes overlap their torsos by 2 px at rest
  (pig: neck contacts off; sheep: head box trimmed to the torso's face so contacts can stay on); the
  wolf's chest cube is bigger than its rear body, so the torso is named; horse head parts are a nested
  tree under `head_parts` whose union box is 6×17×14 px (two cells tall); humanoid `hat` and chicken
  `beak`/`red_thing` are top-level siblings of `head` with the same pivot.

### 13.5 The Meat Hook in the meat (verified in tests; look still to be judged in game)

- The hook is drawn **by the limb it is stuck in** (`CarcassPartRenderer.drawHook`), inside the limb's own
  frame, so it moves exactly as the meat does; the old world-space line-and-hook drawn from
  `RenderLevelStageEvent` lagged the limb by a frame. The drag packet carries the hook point and the
  direction it went in (the player's look at the grab, in the limb's plot frame); the model's shank is
  turned to point back out along that direction and buried to its curl.
- There is no visible tether while dragging: the pull is invisible on purpose.
- Item display frames, checked against `ItemInHandLayer` and `ItemInHandRenderer`: third person +Y is
  forward out of the fist and +Z up; first person is camera space; rotation and scale pivot on model
  (8,8,8); left-hand entries must be left out because the engine mirrors the right-hand ones (declaring
  mirrored copies double-negates and gives the left hand the right-hand pose, which the old model did).
- Art notes from real butcher hooks: blood belongs on the point and the lower shank only, the grip end
  stays clean; steel needs a highlight and a shadow band to read as metal at 16 px. With auto box UVs
  every face samples the middle columns of its texture, so the textures put their bands there and the
  model uses different textures per face direction and per hook segment rather than hand-painted UVs.

### 13.5 Death handover, the hook in the meat, butchering (verified)

- **Death handover.** The carcass is built the instant the mob dies, but discarding the mob at once left a
  frame or two with nothing drawn. The mob now stays three ticks, frozen and untouchable (death event
  cancelled, health set to 1, no AI, no gravity, head turned to the body), while every carcass body is
  teleported back to its built pose each tick and its cells are left blank; then the mob goes, the cells
  get their look, and the kill shove lands, all in one tick.
- **The hook is drawn by the limb it is in** (`CarcassPartRenderer.drawHook`) from the anchor and entry
  direction the server sends (`DragSyncPayload`), buried to its curl along the way it went in, using a
  separate bloodied model (`item/meat_hook_bloody`, registered as a standalone model). No tether is drawn.
  The hand model is clean. Hand transforms were derived from the real hand frames: third person +Y is
  forward out of the fist and +Z up, first person is camera space; left-hand entries are omitted so the
  engine's own mirroring applies.
- **Dragging by a limb**: besides the tether, a torque spring turns the hooked limb so its joint-to-hook
  line points at the tether target and damps its spin (`CarcassDrag.aim`), so the grabbed limb leads and
  the body follows through the joints instead of the limb flailing.
- **Resting form keeps only the torso body.** The coarse limb cells were dropped; the support check uses
  the remembered limb poses instead.
- **Cleaver** (`CarcassButchery`): three cuts on a limb sever its joint (spec removed, `severed` set
  saved); the limb stays in the carcass record as a free body. The torso cannot be cut. Blood particles
  (`Blood`) on the kill, on a hook going in, dripping while dragged, on cuts.

### 13.6 Butchery yields (verified)

- Tables are generated per mob into `data/bloodandbones/butchery/<ns>/<path>.json` from the rig target's
  `butchery` section: meat and bone by bone volume, hide by weight, offal and fat from the torso, plus
  named `hide_extras` and `part_extras` (a sheep's `{wool}`, a polar bear's fish). Counts are expected
  values; the fraction is rolled as a chance.
- Rot spoils yields: past 60% fresh everything is whole; past 30% meat, offal and fat are halved; below
  that meat turns to rotten flesh and hides are halved; rotten gives no hide.
- `CarcassButchery.capturing(sink, action)` hands every yield of a butchery action to a sink instead of the
  ground. Machines and the Spit Roast use it; players' tools do not.

### 13.6a Wounds (verified in tests; look checked in the showcase)

- `CarcassRot.cuts` lists the rig joints that touch a record but no longer hold ("parent>child"): a limb
  cut off (its stump), a piece cut from its parent (its own end), or one butchered away. Root cells carry
  the list (`CarcassPartBlockEntity.cuts`), refreshed with the look and freshness and at once after a
  sever or butcher.
- `WoundCaps` draws a wound texture (raw meat, a bone ring) over the face of the lost limb's physics box
  that faces its parent (outward normal most toward the parent's pivot; the face nearest the limb's own
  pivot if the two coincide): just outside it on the limb, just inside the space it left on the stump,
  placed in the parent's frame from the two bones' rest offsets and rotations. Not drawn in bloodless
  mode, nor for bloodless mobs (dry cut ends). A fresh cut over a Bleeding Rack pours into it.
- Blades that cut a bleeding carcass or hit a bleeding mob get `bloodandbones:bloodied_at` (game time);
  the `bloodandbones:bloody` item property shows a bloody model for 6000 ticks after, never in bloodless
  mode.
- Severing and butchering throw meat scraps (`bloodandbones:gib`, three sprites, tumbling, a wet bounce;
  hidden in bloodless mode by its client provider).
- A fresh cut pours for 15 seconds wherever the body lies (`CarcassBleeding.gush`): drops and a stain
  every couple of seconds under the stump (the child's pivot in the parent's frame, from the joint's own
  maths) and under the piece's cut end; the stump drains 1% of the body's blood per step. Carried pieces
  (hand, Spit Roast, Specimen Jar) draw wounds on every end.

### 13.7 Blood and the Bleeding Rack (verified)

- Blood and Soul Blood are Registrate fluids tinted from vanilla water textures (Create's
  `AllFluids.TintedFluidType`); plain `BucketItem`s so spouts and drains accept them; tagged `c:blood`
  and `create:bottomless/deny`.
- A carcass holds `weight × 1000` mB (skeletons, spirits and golems are in `#bloodandbones:bloodless`).
  Blood belongs to the body's record: severed pieces hold none.
- It drains from the torso's lowest corner into the first Bleeding Rack straight below: up to 8 blocks
  under a hanging body (hook or trolley), or 1 block under a resting one (half rate). Anywhere else a
  hanging body's blood is lost. Encased fans blowing across the body multiply the rate up to 4x
  (`FanAirflow.fanSpeedAt`, which reads Create's `AirCurrent.bounds` on the server). A full rack stops
  the draining. A bled carcass rots at 70% speed.

- The trickle path to Soul Blood: carcasses of mobs in `#bloodandbones:soul_bleeders` (piglins,
  hoglins, zoglins, striders, zombified piglins) drain Soul Blood instead (`CarcassBleeding.fluidOf`).
  A rack holds one fluid, so a carcass drains into the nearest rack that is empty or holds its own
  fluid, passing over one of the other (`rackBelow(..., fluid)`). A rack of the other fluid with no
  better one in reach counts as a full rack: nothing drains and the blood waits in the body, rather
  than spilling or vanishing. `hoglinBleedsSoulBlood` hangs a hoglin over a grid whose middle rack
  already holds blood; `soulBloodPassesOverARackOfBlood` checks the waiting. Their drops
  are a `soul_blood_drop` particle and their stains a `soul` state of the stain block, both with teal
  copies of the red textures (a tint cannot turn red teal); every `Blood` call that knows the mob passes
  `Blood.soul(...)`. `hoglinStainsSoulBlood`.

### 13.7a Blood stains (verified)

- `blood_stain` (`BloodStainBlock`): a flat, non-colliding, replaceable block with `size` 1-4 and `age`
  0-2, no item. `Blood.stain` drops blood straight down through air (at most 12 blocks) onto the first
  sturdy top face; grass, fluids and other blocks in the way take none. Landing on a stain makes it
  bigger and wet again.
- Made by: a hanging carcass bleeding with no rack under it (every bleed step), the kill spray, hooking,
  Cleaver cuts, butchering and severing, and a dragged carcass now and then (a trail). Mobs in
  `#bloodandbones:bloodless` make no blood particles or stains at all.
- Random ticks: rain washes it off; otherwise one in three dries it a step (a tint from `BlockColor`:
  wet red, brown-red, near black), then shrinks it, then removes it: roughly ten to twenty minutes.
- Bloodless mode wraps the stain's baked models so they return no quads, and a client config reload
  redraws the world (`LevelRenderer.allChanged`), so switching it needs no restart.

### 13.7c Thuds (verified)

- `CarcassThuds`, once a tick from a moving carcass's torso: each bone's vertical speed (Sable's rigid
  body velocity, blocks a second) is kept from the last tick; a bone that was falling at 3 or more and
  has slowed by 3 or more has landed. Slime and honey fall sounds, louder with speed and the bone's box
  volume, deeper for bigger bones; 6 or more splats blood (a burst and a stain) for a mob that bleeds.
  One thud per carcass per 8 ticks; none while it is dragged, hung or on a trolley, and only with
  something solid within about a block under the bone. Skeletons and the wither clatter (bone block) instead. The ground can be world blocks or a
  ship's deck: the same points are looked up in each sub-level near the bone
  (`SubLevelContainer.queryIntersecting`, then the point in its own plot), skipping carcass limbs, so a
  landing on another carcass stays silent. `droppedCarcassThuds` drops a cow four blocks and checks a cow
  built on the ground stays quiet; `carcassThudsOnADeck` drops one onto a sub-level deck on corner posts,
  with only air in the world under it.

### 13.7b Bloodless mode as built

- Client: `BBClientConfig.bloodless()` is the client toggle OR the server's `bloodandbonesBloodless` game
  rule, which `BloodlessRulePayload` carries on login and on every change (reset on leaving a server).
  Either changing redraws the world so baked stains appear or go.
- Hidden or swapped so far: blood drop particles (in the particle provider, since the server sends them),
  blood stains (baked model wrapper), the flesh texture of a skinned carcass (a pale bloodless copy), the
  hook drawn in a carcass (the clean model), and the machines' bloody casings, blades, saw, roller and
  Mangler top (`BloodlessSwap` re-points each quad's UVs from the bloody sprite to a clean one, for block
  and item models; the clean textures are the bloody ones with the red taken out; it swaps the model's
  particle texture too, so the bits that fly off when one is broken, dug, run or landed on are clean), and the blood fluid,
  drawn a muddy brown in the world, tanks, pipes and buckets (`TintedFluidType` picks its tint per
  frame).
- Names and descriptions: `BloodlessLanguage` wraps the game's language (`Language.inject`, and I18n's
  own reference by reflection, since Create's descriptions read through I18n) and, in bloodless mode,
  rewords this mod's keys: a translation's own `bloodless.<key>` if it has one, else
  `BloodlessWords.soften` (English only: blood to essence, bleeding to draining, bloody to stained,
  whole words, capitals kept; "bloodless", the mod's name, and the settings and game rule that describe
  bloodless mode itself are left alone). A client tick puts the
  wrapper back after a resource reload replaces the language; a mode change injects a fresh wrapper so
  all text is looked up again, and our `BBDescriptionModifier` rebuilds Create's cached descriptions.
  Checked in the real client (the bloodless showcase logs "Essence Steel Ingot | Draining Rack |
  Stained Casing | Essence").

### 13.8 Machines (verified)

- One block entity, four blocks (`MachineKind`): Mangler, Guillotine, Beheader, Deglover. Millstone
  pattern: vertical shaft from below, stress impact registered with `BlockStressValues.IMPACTS` (addons
  cannot use `CStress`), speed from `|rpm|/16` like the Millstone.
- The work zone is the space over the machine (0.75 past each edge, 2.5 blocks up), so a body lying
  across a machine set flush in a floor, or hanging over it, is reached. A resting carcass is unfolded
  first. A body over the machine offers its nearest limb of the right kind.
- Yields go to a 9-slot output exposed as an extract-only item handler; a full output stops the machine.
- A Create `FilteringBehaviour` on the top face by the north edge (`MachineFilterSlot`: the machines sit
  flush in a floor, so the top is the face within reach, and its middle is under the body). Empty: any
  carcass. A spawn egg or a carcass piece: that mob (Create's plain filter would match any piece). A
  list filter asks each entry the same way, as a whitelist or blacklist; an attribute filter is asked
  about a piece of the carcass's body, so the piece attributes work. Only eggs, pieces and filters go in
  the slot, turned away in `canShortInteract` and on a clipboard paste: Create hands the old filter back
  before it checks the new item, so refusing any later would let each refused click copy the old
  filter. A refused item gets Create's own "not a valid filter" message and deny sound. It is only
  asked about carcasses over the machine. A Deployer's stand-in player cannot set
  it (Create lets fake players past the slot's hit test, which would have stolen their clicks). The machine's renderer now runs with Flywheel too, for the slot; it still leaves
  the shaft to the visual.

### 13.9 Materials (verified)

- Hand-written JSON recipes: spout-filling iron with blood (Blood Steel), diamond with soul blood (Blood
  Diamond); soul blood by superheated mixing with CEI liquid experience, or by CDG basin fermenting.
  The `recipesLoad` game test checks every recipe file parsed, since a broken one only logs an error.
  (Since replaced: recipes come from datagen, the Blood Diamond is a sequenced assembly, and soul blood has its
  full line on Create's machines; see section 15.20.)

### 13.10 Every vanilla mob (verified; the ender dragon and tropical fish excepted)

- 79 rig targets. `LayerDumpProvider` (`-Dbloodandbones.dump_layers=ns:model#layer,...` on the data run)
  prints part trees to `run/build/layer-dump.txt` for writing new ones.
- Variants come through `CarcassLook` placeholders (`{variant}`, `{cat_texture}`, villager type,
  profession and level). A coat can use another model's layer by full name (`minecraft:llama#decor`).
- Hook kills drop the mob's belongings (saddles, armour, chests and contents, held items) by calling the
  protected vanilla `dropCustomDeathLoot` and `dropEquipment` before the mob is removed.
- Big slimes and magma cubes (size 4) and the smallest (size 1) become carcasses; size 2 splits and dies
  as usual. The rig is drawn at size 4, and the renderer scales the whole model by the size about the
  feet, so size 1 is the rig's baby shape: everything at 0.25, moved down 72 model pixels
  (`0.25 * (24 + 72)` puts the feet back on the feet). The assembler marks a size 1 slime as a baby; its
  pieces' tooltip says "From a small one" instead, though the Attribute Filter's "from a baby" still
  matches them. A baby's yields are cut by its weight against the grown rig's, which for a slime (a 64th)
  would leave nothing; the smallest slime gives a quarter instead (`CarcassButchery.babyYieldScale`: one
  slime ball of four, about the game's own drop) and the smallest magma cube none (the game only drops
  magma cream from bigger ones). The test butchers a piece of each and counts. The special cases are by
  entity type, so a modded slime with a rig and a baby shape would get the weight rule and "From a
  baby". `smallestSlimesLeaveCarcasses`,
  `smallSlimePiecesSaySmall`. The pufferfish is rigged
  on its fully puffed model (its spikes stuck to the body), whatever its state when it died; the wither at
  its drawn double size, with its tail hung from the ribcage, bloodless, its heads sometimes giving a
  wither skeleton skull. Not rigged: the ender dragon (a multi-part entity with its own long death) and
  tropical fish (two body shapes under one mob, and a rig has one model).

### 13.10a Babies (verified)

- A rig target may carry a `baby` section copied from the vanilla model's constructor
  (`AgeableListModel`): the head bones, the head's scale and offset, the body's scale and offset. A
  model point `p` is drawn at `scale * (p + offset)` in model pixels; the villager, which its renderer
  simply halves, has both halves at 0.5 around the feet. Datagen reads it beside the target (the
  target codec is full) and writes it into the rig.
- `Rig.asBaby()` works out the baby rig at run time: offsets, boxes and extras scaled, a per-bone draw
  scale (`Bone.scale`), weight from the new boxes. `RigManager.forCarcass` / `forEntity(id, baby)` /
  `clientRig(id, baby)` hand it out (cached, cleared on reload). The record, the root cells and carried
  pieces remember `baby`; butchery gives as much less as the baby weighs less.
- All 36 kinds with babies (cow, mooshroom, pig, sheep, chicken, wolf, goat, polar bear, panda, ocelot, cat, fox,
  hoglin, zoglin, zombie, husk, drowned, zombie villager, piglin, villager, turtle, rabbit, horse, donkey,
  mule, llama, trader llama, zombified piglin, skeleton and zombie horse (their shapes copied from the
  piglin's and the horse's, whose models they use), and, shrunk whole around the feet, axolotl, bee, sniffer, armadillo, camel,
  strider). The rabbit draws its baby by
  hand (`RabbitModel#renderToBuffer`), in the same form: the scales are vanilla's divided by the grown
  rabbit's 0.6, and the offsets leave out the grown rabbit's own 16 px lift, which the rig does not store
  (the assembler's 1.501 × 0.6 happens to equal 1.501 − 0.6): head 22 − 16·0.6/0.5667, body 36 − 16·0.6/0.4.
  `babyRabbitSitsWhereTheGameDrawsIt` checks both against vanilla's numbers. Others with babies die as usual until they get a shape. Foals (horse, donkey, mule and the
  undead horses) stand on the game's own baby-leg parts, 22 px cubes at half size with the top 5.5 px
  hidden in the body. Drawing those would leave a stub of hide poking out past a cut leg's wound, so the
  grown leg is drawn instead, half as wide and three quarters as long from the same pivot (a `group`,
  below), which shows the same leg. Llamas use `groups` too: bones the game draws at their own
  scale per axis and offset (`LlamaModel#renderToBuffer` squashes the head, the body and the legs each
  differently). The pivot is scaled along the model's axes; the box, extras and drawing along the part's
  own (`BabyShape.alongPart`, exact for quarter-turned parts, which vanilla's are), so `Bone.scale` is a
  vector now (one number in JSON when even, three when not). Extras get the bone's size before their
  own turn.
- Assembly lifts a body so no box starts below the feet: the ghast's tentacles hang below its feet in
  the model and used to start stuck through the ground. Its tentacle collision boxes are short (drawn
  full length) so the body does not end up on stilts.

### 13.11 Chain conveyors (verified)

- `ChainCursor` follows a Create chain with Create's own rules through public API (`connectionStats`
  after `prepareStats()`, `getSpeed()`, `reversed`, `loopThresholdCrossed`, routing table and ports).
  `ShackleTrolleyEntity` moves in `LevelTickEvent.Pre` (before Sable steps) and slides the world end of a
  ball joint each substep with `setFrame1`. A carcass on a trolley counts as hanging.
- Trolleys queue: one waits while another is within `GAP` (1.6 blocks) and ahead along its heading, so
  a line of carcasses keeps a body's length apart and backs up behind one stopped at a frogport (Create
  itself lets packages overlap). Two on the same spot: the newer waits.
- Open: conveyors on Sable sub-levels are not supported.

### 13.12 Cooking and display (verified)

- Spit Roast: horizontal-axis kinetic block over heat (campfire, fire, lava, magma, Blaze Burner by heat
  level). Cooks only while turning; done at a volume-based time, burnt at twice that. Taking it off
  smelts each butchery yield through the vanilla smelting recipes.
- Specimen Jar: holds one carcass piece in a translucent jar; the piece is drawn with the shared
  `CarcassModels.drawPiece`.
- Butcher's Hook (the "wall Meat Hook"): a horizontal-facing block on the side of a sturdy block, with no
  collision, holding one piece that hangs from its tip and sways. It falls with its wall, dropping the
  piece. Its block entity is the Specimen Jar's, plus dripping: a piece that still has blood (a mob that
  bleeds) loses 1/120 of it a second (saved each second, sent to clients only when it runs dry), drops fall
  from under it on the client, and every third second a stain lands on the floor below.
- Bloody Casing: a Create `CasingBlock` built with `BuilderTransformers.casing` and our own connected
  sheet (`BBSpriteShifts`), made by spout-filling an Andesite Casing with 250 mB of blood. In bloodless
  mode it shows as plain andesite casing: the swap wraps the model after Create's connected-texture
  wrapper (lowest event priority) and finds the sprite a quad shows by where its UVs fall, since
  Create moves the UVs onto the connected sheet but leaves the quad's sprite field alone.
- Butcher's Table: a work table, not the brief's morgue Steel Table (that is its own block, §13.14). It
  holds one piece like the jar, drawn lying on the top;
  a Cleaver chops it into `CarcassButchery.pieceYields` (the same yields a loose piece gives, now shared
  with the Spit Roast), dropped on the top, with the spray, the sound and a bloodied cleaver. Clean steel
  in bloodless mode. A piece with nothing to cut it into (no butchery table for its mob, or none for
  that part) stays whole, so a chop that finds nothing never uses the piece up. An item handler takes one piece at a time (funnels, hoppers) and gives it back. A
  Deployer's stand-in player never ticks, so its item cooldowns would never run out: the table does not
  put a fake player's cleaver on cooldown (the Deployer's own pace limits it). `deployerChopsOnTheTable`
  feeds two pieces in and checks both are chopped.
- Gut Chain: a vanilla `ChainBlock` with our own two-plane model (4 px wide strips) and a lumpy, wet
  texture; hand-breakable, slime sounds; three offal in a column make three. Bloodless mode swaps it to
  a plain cord texture and rewords "gut" as "cord". It also hangs from Create's chain conveyors and
  rides them (§13.14).
- On Create contraptions (`BBMovementChecks`): both hooks count as attached to the block they hang from
  and as brittle, and the Butcher's Hook is a `create:movable_empty_collider`. The Shackle Hook now turns
  with a structure or bearing (it had no `rotate`/`mirror`). A hook carrying a carcass is not yet made
  into contraption data (§3.5): the carcass falls when its hook is moved, and when the contraption is put
  down the hook lets it go rather than pulling the body back across the world: the hook saves its own
  position (`HookPos`, which Create leaves alone when it rewrites x/y/z) and, read back somewhere else,
  releases. A reload in place rejoins as before, unless the body swung more than 8 blocks off while the
  hook was unloaded (a hook in the world only; on a ship the tip is in ship coordinates).

### 13.12a Sorting pieces with Create's filters (verified)

- `BBItemAttributes` registers `ItemAttributeType`s through a `DeferredRegister` on
  `CreateRegistries.ITEM_ATTRIBUTE_TYPE`, as Enchantment Industry does, with text under
  `create.item_attributes.bloodandbones.*` so Create's own `format` finds it. Singletons: fresh meat
  (freshness 0.6 or more, where butchering gives everything), rotting (under 0.3, where meat comes out
  as rotten flesh), skinned, from a baby. With a value: a piece of a given mob, and a carcass part (head
  and neck by name, the Beheader's rule; body = the rig's root; tail by name; the rest limbs). The filter
  screen asks on the client, so the part lookup uses the client's copy of the rigs there.

### 13.13 JEI Butchery pages (verified)

- One page per butchery table (`compat/jei/ButcheryCategory`): the mob's spawn egg in, the hide from
  skinning and the summed yields of every piece out; `{wool}` and `{mushroom}` shown as white and red.
- The client never loads data packs, so the server sends the tables (`ButcherySyncPayload`, one packet,
  about 52 KB for 77 mobs) alongside the rigs on `OnDatapackSyncEvent`. JEI may start before they arrive,
  so on arrival the plugin hides the pages it had and adds fresh ones. `NetworkTests` writes the rig and
  butchery packets to bytes and back, since a single-player world never serialises them.

### 13.14 Decoration from the brief (verified)

What the brief's Machines table (Steel Table, Steel Rack) and Decoration list (Gut Chain on chain
conveyors, ribcage arches, bone piles, blood-stained cladding) asked for that did not exist yet. The new
blocks live in `decoration/`. Each has a loot table, a recipe in `data/.../recipe`, a name and a Create
description in `BBLang`, a place in the creative tab and JEI information pages ("morgue" and a longer
"decoration"). `DecorationTests` covers them.

- Steel Table (`SteelTableBlock`): the morgue table, separate from the Butcher's Table. Four joins in the
  block state (north, east, south, west), true when the neighbour that way is another Steel Table,
  worked out on placement and kept up to date as neighbours change. The model is a multipart: the top
  always, a raised lip along each side that joins nothing (and its corner piece when either side of the
  corner is open), and a leg in a corner only when neither side of that corner joins. So a straight run
  stands on legs at its two ends, a turn has one leg on its outside corner, and a table in the middle has
  none; the block's shape follows the same rule and the model's heights (the top 12 to 15 pixels, the
  legs up to it; the test reads them from the model files). Turning the block (a contraption, a structure block)
  turns the joins, as a fence's do. It holds one item, any item, reusing the Specimen Jar's block entity:
  put on by clicking the top (clicking a side places a held block as usual, so a run can be built out),
  taken back with an empty hand, loaded and emptied by funnels and hoppers through a one-slot item
  handler, dropped when broken. A carcass piece lies on its back, drawn with the shared
  `CarcassModels.drawPiece` at its own size (only a piece longer than the table is shrunk); a flat item
  lies face up; a block or a skull sits on the top as it would lie on the ground. Cold brushed steel, no
  copper and no blood, so nothing changes in bloodless mode. Recipe: three iron sheets over two iron
  ingots make two.
- Steel Rack (`SteelRackBlock`): steel shelving, facing whoever placed it, two shelves of two places.
  Things go on from the front: the place a click goes to is worked out from where it landed and which
  way the rack faces (`slotAt`: the upper or lower half, and the left or right half as seen from the
  front); a click on a side places a held block as usual, so racks can stand in a row. An empty hand
  takes back the thing at that place. Four one-item slots for funnels and hoppers, filled from the lower
  left; everything drops when it is broken. Things stand on the shelves facing out, turned a little each.
  Same cold steel. Recipe: iron bars and iron sheets.
- Ribcage Arch (`RibcageArchBlock`): one segment of a giant rib, with a facing and a shape that follows
  its neighbours: straight when another rib is above it, curving in towards its facing when it is the top
  of a stack or on its own, and level (the crown) when it hangs over air with another rib beside it along
  its facing. Two stacks facing each other with level ribs between make an arch; a row of arches is the
  inside of a ribcage. Placed, a rib faces the player, or takes the facing of the rib it was placed
  against, so stacks and spans stay in line. The curve is built from segments tilted 22.5 and 45 degrees
  (all a block model allows), and every face's texture coordinates are kept inside the texture, since
  segments poking out of the block would otherwise read the next texture on the atlas. Bone coloured with
  wet red joints; in bloodless mode the bone and joints swap to bleached, dry ones (`BloodlessSwap.BONES`)
  and the description has its own bleached wording (a `bloodless.` key in `BBLang`). Recipe: three bones
  in a curve make two.
- Bone Pile (`BonePileBlock`): layers like snow, one to eight, two pixels each. Using a Bone Pile on one
  adds a layer (the snow rule: the pile can be replaced by its own item); on a full pile it starts a new
  one on top. Collision is a layer lower than it looks, as snow's, so a single layer is walked through.
  It needs a solid floor or a full pile under it. The loot table drops two bones a layer (data, one
  set-count per layer). Four turned versions of each height, picked by position, so the loose 3D bones
  on top do not repeat. Bloodless: clean bones, no blood between them. Recipe: four bones make two, so
  crafting and breaking come out even.
- Gut Chain on chain conveyors (`HangingGutChainEntity`, `GutChainHanging`): using Gut Chain on a chain
  conveyor's chain hangs one link there. It rides the chain with the same `ChainCursor` the Shackle
  Trolley uses (it has no address, so it never stops at a frogport, and like Create's packages it does not
  queue). Using more Gut Chain on the hanging string adds a link, up to eight; hitting it takes it down
  and drops all its links (none for a creative player). A broken chain drops it the same way. The chain is
  found with `ChainPicker`, which now works on either side: the client asks too, and when the chain is
  nearer than the block behind it, it does not place the held Gut Chain on that block. The length is
  synced entity data, so every client draws the right length. Each link is a hit box of its own, a
  NeoForge part entity (`HangingGutChainEntity.Link`, as the Ender Dragon's parts), passing hits and
  clicks on to the string: the game files an entity under the 16-block section its position is in and a
  search for entities looks only a little below its box, so one long box hanging from the chain was
  missed by a player or an arrow aiming at its lower end once that hung below a section line. Parts are
  kept in a list of their own that every search looks through. The position follows the usual entity
  tracking, every tick, and a client glides to each new position over a few ticks (`lerpTo`, as a
  minecart), so it moves smoothly between ticks. Each client swings its own copy of
  the string (a Verlet rope, two joints a link, the top held on the chain, gravity and air drag), so it
  trails and sways as it goes round wheels and stops; the swinging is drawn only, never simulated on the
  server, and the renderer culls by a box round the swinging joints, since on a fast chain the string
  trails several blocks behind its top. Drawn with the Gut Chain block's texture as two crossed strips four pixels wide, bending at
  each joint; the plain cord texture in bloodless mode, and "Hanging Cord Chain" as its name. The hit
  boxes hang straight down, so a string trailing behind a fast chain is hit where it would hang, not
  where it is drawn.
  Not done: a chain conveyor on a Sable sub-level, as for trolleys (§13.11); a conveyor moved by a
  contraption drops its strings.
- Blood-stained cladding: Bloody Brass Casing and Bloody Copper Casing, made exactly as the Bloody
  Casing: a Create `CasingBlock` with `BuilderTransformers.casing` and its own connected sheet in
  `BBSpriteShifts`, by spout-filling a Brass or Copper Casing with 250 mB of blood. The textures are
  Create's own brass and copper casing sheets (copied out of the Create jar) with blood drips and splats
  painted over each tile. In bloodless mode they show as Create's plain brass and copper casings
  (`BloodlessSwap.CLADDING`, wrapped after Create's connected textures like the Bloody Casing), named
  "Stained Brass Casing" and "Stained Copper Casing"; the bits that fly off them when broken are the
  plain casings' too (the showcase throws them in mid-air to check).
- On contraptions: the table, rack, rib and casings are ordinary solid blocks, and the table's and rack's
  item handlers are not a plain `ItemStackHandler`, so Create carries their contents as block data rather
  than as the contraption's storage. A bone pile lies on the block below as a carpet does: attached
  downwards and brittle (`BBMovementChecks`), which also makes a single layer, which has no collision,
  move at all. Create counts a brittle block as holding nothing up on any side, so a pile would not push
  the block in front of it and the piston stalled against it; as Create does for carpets, only a pile's
  top holds nothing up (a full pile's top does). `decorationRidesAContraption` pushes a table with a
  carcass piece on it, a rack with two things on it, a rib, both new casings, a three-layer pile on one of
  them and a stone that pile pushes, with a single layer on that stone, two blocks with a Mechanical
  Piston, and checks they are set down whole with nothing dropped.
  Not done: a pile right in front of a piston's head is not picked up, since Create's piston makes that
  exception for its own carpets only (as for a torch there): a single layer is broken by the head,
  dropping its bones, and a thicker pile stops the piston. Tried in a game test, not guessed.
- Tests (`DecorationTests`): `steelTablesJoinIntoARun`, `steelTableHoldsOneItem`,
  `steelRackPlacesWhatYouLookAt`, `ribcageArchesShapeThemselves`, `bonePilesLayerUp`,
  `gutChainRidesAChainConveyor` (a player aims at the chain and hangs a link, lengthens it past eight,
  a client's copy gets the length from the synced data, it saves and loads, it rides the moving chain,
  and hit it drops eight links), `gutChainIsHitBelowASectionLine` (a string hung across a section line,
  aimed at from below the line the way the game aims: found, lengthened and taken down),
  `gutChainGlidesOnClients` (a client's copy fed a position a tick moves at the chain's speed every tick,
  and trailing behind a fast chain stays inside its culling box), `decorationRidesAContraption`,
  `bloodyCladdingRecipes`; the recipes are in `recipesLoad`. The tests pick up what they drop before they
  finish, to keep their ground tidy. The minion test `courierCarries` timed out once in an earlier run;
  the cause is not known. It was not these tests' bones: from a neighbouring test's ground they land at
  least 15 blocks from its courier's home along x or z, and it looks 10 blocks round. Looked at in the showcase, normal and bloodless (row E: a run of tables and a rack with
  things on them, four rib arches, piles of each height, the casings beside Create's own, gut chains
  riding a turning conveyor, and the bits that fly off each bloody block when broken, thrown in mid-air).

---

## 14. Block 11: the body (*decided* above; this is the build plan)

Decisions from the third review are in §12. Details the review left open are filled in here with
defaults, marked *default*, to be changed freely.

**The body.** Every player carries a body record (a NeoForge data attachment, saved, sent to the
player's client and to anyone watching, and kept through death: a lost arm stays lost). It lists
parts, each natural, missing, or fitted with an implant (an item). Limbs first (both arms, both legs),
then organs (eyes, heart, lungs, stomach) with the cybernetics. Mobs and minions use the same record
later. The head and torso cannot be taken.

**What a missing part does** (*default*):
- An arm: nothing can be used, placed or swung in that hand, and breaking blocks is slow with the main
  arm gone. The empty hand still works the Surgery Table, so an armless player can always get help.
- A leg: slower walking (about 40% per leg) and a weaker jump; no sprinting with none.
- Drawn on the player: the limb and its sleeve are hidden, in third person and first person; a fitted
  implant is drawn in the limb's place.

**Implants, in three kinds:**
1. Basic prosthetics, unpowered, the safety floor: a peg leg, a hook hand. They always work, a little
   worse than flesh (*default*: a peg leg walks at 90%, a hook hand breaks blocks at 70%).
2. Organic prosthetics run on blood from the backtank; cybernetics run on Soul Blood. Better than flesh
   while the tank has the fluid, and dead weight (as good as missing) once it is dry.
3. Cybernetics for every part (§8's Vent Arm and port among them).

**The Surgery Table** (slice 11a builds the hand-worked, self-surgery part):
- It holds one item on its top, like the Butcher's Table: a blade, an implant or a severed limb.
- Right-click it with an empty hand to lie on it (a seat entity; lying drawn as sitting for now). A
  screen shows your body; for each part it offers what the item on the table allows: a blade takes a
  natural limb off (you get it as a severed limb item); an implant or a severed limb is fitted where a
  part is missing; an empty hand unclips an implant and hands it back. Nothing can fail.
- Later: a powered table (a shaft and a Deployer's tools, as Create's sequenced assembly), surgery on
  another player, a mob or a carcass (organs out), and minion building.

**Slices:**
- 11a: the body record, limbs lost and fitted at a hand-worked table, basic prosthetics, the effects,
  drawing, tests.
- 11b: the Fluid Backtank in its tiers (copper 2, gold 3, iron 4, diamond 6, blood steel 8, blood diamond
  16, soul netherite 32 buckets), worn in the chest slot with the armour of its tier, filled from pipes as
  a placed block, and the Backtank Port.
- 11c: organic prosthetics and cybernetics, drawing on the tank, dead when it is dry; organs.
- 11d: the powered table; surgery on other players, mobs and carcasses; organs out of carcasses.
- 11e: minions from parts, with jobs from what they are given.

### 14.1 Slice 11a as built (verified)

- `body/Body`: the parts that are gone and the implants in their place, with a codec (saved under the
  player's NeoForge attachments, only when something is missing) and a stream codec. `BBAttachments.BODY`
  is `copyOnDeath`. `BodySync.Payload` goes to the player and everyone tracking them on every change, on
  login, respawn, dimension change and when someone starts tracking them.
- `BodyEffects`, on both sides from the synced body: `RightClickItem`, `RightClickBlock` and the two entity
  interactions are cancelled for a hand with no working arm when it holds something; attacks need a
  working main arm; `BreakSpeed` times the main arm's figure (missing 0.2, Hook Hand 0.7). Legs are two
  transient attribute modifiers (`bloodandbones:legs`, multiply total) on movement speed and jump
  strength: the two legs' figures averaged, a missing leg 0.2 for walking and 0.4 for jumping, a Peg Leg
  0.9. Refreshed on change and every ten ticks, since transient modifiers are not saved. No sprinting on
  no legs.
- Drawing: `RenderPlayerEvent.Pre` fires after vanilla's `setModelProperties`, so hiding a part and its
  outer layer there sticks for the frame (`Post` shows them again). `BodyRendering.ImplantLayer`, added to
  both player renderers, draws each implant's part in the same pose with the implant's own texture, laid
  out like a skin. First person: `RenderArmEvent` hides a missing arm or draws the implant as vanilla's
  `renderHand` does; `RenderHandEvent` hides what a missing hand holds.
- `SurgeryTableBlock`: one item (a Cleaver, an `ImplantItem` or a `SeveredLimbItem`); an empty hand lies
  you on a `SurgerySeatEntity` (invisible, no physics, gone when empty or when the table is) and the
  server sends `Surgery.OpenPayload`; the screen's buttons send `Surgery.ActionPayload`, done only for a
  player lying on that table (`Surgery.lyingOn`). `Surgery.action` decides the button on both sides:
  an implant unclips; flesh comes off with a blade (bloodied, it stays on the table); a missing part takes
  a fitting implant, or a severed limb (anyone's, either side) as flesh again.
- `BodyTests`: the body saved and loaded (on its own and on a player), a whole round of surgery on one
  arm, the walk and jump figures, the hands, the seat, and the screen's check. The showcase photographs a
  player with a Peg Leg, a Hook Hand and an arm gone, first-person, and the surgery screen.

### 14.2 Slice 11b as built (verified)

- One armour material per tier (`BBArmorMaterials`, a `DeferredRegister` on `Registries.ARMOR_MATERIAL`
  as Create registers its copper), chest defence only: copper 4, gold 5, iron 6, diamond 8 (+2
  toughness), blood steel 7 (+1), blood diamond 8 (+2.5, 5% knockback resistance), soul netherite 8 (+3,
  10%). The armour layer is a strap harness per tier (`textures/models/armor/backtank_<tier>_layer_1`).
- `FluidBacktankItem` keeps its fluid in a `SimpleFluidContent` component; NeoForge's
  `FluidHandlerItemStack` is its item capability, so Create's Spout and Item Drain work on it. As
  Create's backtank does, `useOn` hands placing to a separate block item (hidden from the tab): one
  `fluid_backtank` block with a `tier` property, whose block entity's `FluidTank` is exposed to pipes on
  every side. Broken, it drops the tier's item with the fluid (not in creative); middle-click gives an
  empty one.
- `FluidBacktankLayer` draws the tier's block model on the back as Create's `BacktankArmorLayer` does
  (body transform, then flipped y and z). The showcase had to face the player's body the way it wanted,
  since a teleport turns the head at once and the body only slowly.
- Soul Netherite Ingot: `create:sequenced_assembly` (filling 1000 mB soul blood, deploying Enchantment
  Industry's `super_experience_block`) with an incomplete ingot; the soul netherite tank is a
  `smithing_transform` of the blood diamond one, which keeps the fluid.
- The Backtank Port waits for 11c, since only a player with the port cybernetic can use it.
- `BacktankTests`: each tier's capacity through the item handler, the armour, set down with a mock
  player's `useOn`, drained and filled by a pipe (water refused), broken and dropped with the right
  amount, and the fluid saved on the item.

### 14.3 Slice 11c as built (verified)

- `BodyPart` gains the eyes, heart, lungs and stomach. Every part can be taken out with a blade, put back
  as flesh (the part's item, anyone's), or swapped in one step for an implant that fits (`REPLACE`); the
  organ comes out in your hands. *Default*: a missing or dead organ debilitates but never kills (no working
  eye: blindness; heart: weakness II and slowness II; lungs: no sprinting; stomach: food cannot be
  started). Effects are applied once a second on the server (`BodyEffects.second`).
- `ImplantSpec`: kind, walk, jump, work, attack, reach, safe fall, fuel, drain (mB a second), ability,
  texture. Fuel `null` never stops; `blood`/`soul_blood` work while the worn tank holds that fluid;
  `any` (the Vent Arm) works while the tank holds anything and only uses it as it sprays. The second's
  drain comes from the worn tank's item component. Limb bonuses are transient attribute modifiers
  (movement, jump, safe fall, attack on the main arm, block and entity reach).
- The Vent Arm and Port Arm were first written to run on soul blood, which the tests showed cannot work:
  a tank holds one fluid, so the Vent Arm could never spray lava and the Port Arm could never pipe blood.
  The Vent Arm runs on whatever it sprays; the Port Arm needs nothing (so an empty tank can be filled).
- `Vent`: the client sends `Vent.Payload` every third tick while use is held empty-handed with a ready
  Vent Arm; the server keeps its own pace (three ticks) and takes 50 mB a shot. The effect is a NeoForge
  data map on fluids (`bloodandbones:vent_effects`, tag keys allowed); unlisted fluids spill. Liquid
  experience is counted a point per mB, *assumed* to be Enchantment Industry's rate.
- `BacktankPortBlockEntity` exposes, on its nozzle side only, a proxy fluid handler: the worn tank's item
  handler of the first player within a block who has a Port Arm, else an empty handler. Create's pumps and
  pipes do the moving, so the direction is the pump's.
- Drawing: eyes are drawn on the head a hair out from the face (the head turns about the model origin),
  an empty socket for a missing eye, a red lens for an Optic Eye (through `RenderType.eyes`, glowing,
  while it works). The surgery screen fits nine rows and marks a dead implant "(dry)".
- `ImplantTests`: a Piston Leg on soul blood, not blood, draining to dead; a Hydraulic Arm's bonuses; a
  heart swapped for a Pump Heart, weak when dry and healing when fed, eyes out one at a time to blindness;
  the Vent Arm's fire, water and spill; the port reaching a worn tank only with a Port Arm.

### 14.4 Slice 11d as built (verified)

- Anyone on the table: `Surgery.patientAt` is the seat's rider. Right-clicking with an empty hand opens the
  screen for someone else lying there (`OpenPayload` now carries the patient's id); the server does the
  action only if `mayOperate` (you are the patient on that table, or you stand within 6 blocks of it) and
  gives what comes out to the surgeon. The screen closes when that stops being true.
- Mobs: led onto the table on a lead (the lead drops). The body attachment works on any living thing;
  `BodyEffects` refreshes a mob's once a second from `EntityTickEvent`, only for mobs already operated on
  (`hasData`, so other mobs are never given a body). A mob's arms scale its attack (half with one gone).
  Nothing is drawn on mobs yet.
- Carcass organs: a Cleaver on a carcass piece lying on the table takes the next organ (a body: heart,
  lungs, stomach; a head: two eyes; bloodless mobs none), counted in the piece's `organs_taken` trait and
  named for the animal. A Deployer's stand-in player would keep the organ and Create's Deployer stalls while
  it holds overflow, so for a fake player the organ drops on the table. *Default*: a piece's organs are not
  taken off its later butchery yield.
- The "powered table" is so far the Deployer on carcass pieces. Machines working on living patients wait
  for the minion slice.
- `PatientTests`: another player's arm to the surgeon, and no reach from across the room; a zombie's leg
  and arm (60% walking, half damage, the leg named for it); a cow's body and head organs and none from a
  skeleton; a Deployer taking all three organs.

### 14.5 Review of slices 11a-11c (fixed)

- A missing or dry stomach cancelled eating and so starved players to death on Hard; hunger now stops at
  1 while the stomach does not work.
- The Vent Arm counted liquid experience at a point a millibucket; NeoForge's `c:experience` rate is 20 mB
  a point, and the higher rate made an experience loop with any collector. Now 20 mB a point.
- A lone player could take both arms off and then push nothing onto the table; the hand check now skips
  the Surgery Table, so a stump can always reach it.
- The Vent now spares players where PvP is off, never touches spectators, needs `mayInteract` to put out
  fires, and cannot be used by a spectator or the dead.
- The Backtank Port plugged in anyone with a Port Arm who stood by it; now only a player crouching next to
  it, and the search runs once a tick, not on every handler call.
- Attribute modifiers were rebuilt every ten ticks, which marks the attribute dirty and sends it to every
  watcher; now only a changed modifier is replaced. The render's hidden-parts list is cleared at each
  frame's start in case another mod cancels the render before `Post`. The table no longer uses up items
  in creative; night vision now lasts about ten seconds past a stopped Optic Eye.
- Known: in third person a held item and armour are still drawn on a missing limb.

### 14.6 Slice 11e as built: minions (verified)

- The frame (`minion/MinionFrame`): a carcass body piece on the Surgery Table. Its `minion_frame`
  component holds a `Body` (arms, legs and eyes missing to start; organs missing as far as its
  `organs_taken`) and whose head it has. A carcass head fits once (bringing its eyes, less those taken);
  a severed part or an implant fits the first missing part of its kind (eyes only with a head). An empty
  hand reads out the status; a soul blood bucket wakes it when it has a head and a heart (the bucket comes
  back empty; `summoned_entity` is triggered for the advancement).
- `MinionJob.of(body, wearer)`, recomputed every second: a working Hook Hand, Hydraulic Arm or Vent Arm
  on either arm makes a FIGHTER; both arms working and an eye, a FARMER; both arms and no eye, a COURIER;
  otherwise a COMPANION. *Default* choices, all easy to change.
- `MinionEntity` (a `PathfinderMob`, persistent): maker, home (the table), head, a 9-slot inventory. Goals:
  melee, or the Vent from range with a Vent Arm; follow the maker (fighter, companion); reap ripe
  `CropBlock`s within 8 of home, taking the drops and replanting from them (farmer); pick up items within
  10 of home and put them in the nearest item-handler block within 6 (courier, farmer); drift home. It
  targets monsters (`Enemy`) only as a fighter. The body attachment makes its legs, arms and organs work
  as for any mob (§14.4). Goals that wait between searches use a random chance, not `tickCount % n`:
  goal starts are checked on alternate ticks offset by the entity id, so a fixed modulus could never
  line up (the courier and farmer tests caught it).
- Drawing: `MinionRenderer` is a `HumanoidMobRenderer` on the player model with a stitched flesh texture,
  hiding missing limbs every frame; the implant and backtank layers are now generic over humanoid models,
  so minions show their implants, eyes and tank like players.
- `MinionTests`: building (a head, two arms, a leg and a Peg Leg; no third arm) and waking a farmer;
  jobs from parts; a Hook Hand fighter hurting a zombie; a courier storing dropped bones; a farmer reaping,
  replanting and storing wheat; a minion saved and loaded.
- Not yet: the minion's head drawn as the carcass's; more jobs (miner, builder, fluid carrier with a
  Port Arm); machines working on living patients.

### 14.7 Aligned with the design brief's self-augmentation rules

The design brief (§ Self-augmentation) was not re-read when slices 11a-11e were built, and several defaults
contradicted it. Fixed:
- Crude prosthetics "just return normal functioning with no extra benefit": the Peg Leg and Hook Hand
  were 90% and 70%, now exactly flesh, and their recipes are iron, leather and bone. Every removable part
  now has one (Glass Eye, Crude Heart, Crude Lungs, Crude Stomach), so the safety floor covers every slot.
- Empty-slot penalties as the brief lists them, for players: an arm out means no off-hand and a quarter
  slower swings (attack speed and mining) per arm, and the main hand always works; a leg out means no
  sprinting (the walk is unchanged); an eye out means reduced vision, fog on the client
  (`ViewportEvent.RenderFog`, half the view with one eye, six blocks with none), not the blindness effect.
  Mobs keep the old scheme (slower walk, weaker hit, blind), having no off-hand or sprint.
- "A heart can't be removed while empty; replacement is simultaneous": a blade does nothing to a heart,
  an implant or heart swaps in (`REPLACE`), and a heart implant only swaps out (`SWAP`, now for every
  part: an implant on the table that fits swaps for the fitted one).
- Still to align with the brief then: amputation needing a surgeon minion, the outside camera view
  and the ragged stump; the table's two attachments; use-based necrosis on organic prosthetics; the
  cybernetic throttle system and modules (Grappling Spool, Rotational Coupler, Piston Ram, Magnet Coil,
  Analytical Lens, Gyroscopic Stabilizer, Barometric Vent); the graft and module set bonuses. Since done:
  the outside view and the attachments (14.8), necrosis (14.9), the throttle and modules (14.10), the set
  bonuses (14.11). Still open: the surgeon minion and the ragged stump (they wait for minions built from
  carcass parts).

### 14.8 The table's attachments and the outside view (brief § Machines, § Self-augmentation)

- `SurgeryTableBlock.ATTACHMENT` (none, surgical, assembly), a multipart model per attachment. Right-click an
  empty table with a Surgical Rig or Assembly Frame to fit it (the old one comes back); sneak with an empty
  hand and nothing on the table to take it off; breaking the table drops it. With the Rig the table does
  everything surgical (patients, tools, organs from carcass pieces; the screen's actions check for it);
  with the Frame it only takes a carcass body and builds a minion on it. `attachmentsSetTheJob`.
- The outside view: while the surgery screen is open on yourself, the camera is the front third-person one
  with the head tipped back 55 degrees (so it sits above and looks down) and pulled in to 2.5 blocks
  (`CalculateDetachedCameraDistanceEvent`); the camera and tilt go back when the screen closes. NeoForge's
  `ComputeCameraAngles` only turns the camera, it cannot move it, hence the tilt.

### 14.9 Necrosis (brief § Self-augmentation: "Decay is use-based, never wall-clock")

- `body/Necrosis`: a `necrosis` component (0 to 100) on a fitted organic implant (one that runs on blood).
  A hit or a block broken rots the main arm's by 1, a meal the stomach's by 3, every 4 blocks walked on the
  ground (6 sprinted) each leg's by 1. Nothing happens while logged off. Once a second, each rotting organic
  implant takes 1 mB of blood from the worn tank and clears 2 (soul blood does not perfuse). At 100 the
  implant is not working (`ImplantItem.working(stack, wearer)`), which is the small empty-slot penalty; it
  works again as soon as perfusion takes it below. The body is re-sent when rot crosses a tenth.
- Drawn: the implant's texture tinted by the carcass rot colour. Tooltip shows the percentage.
- `fleshArmRotsFromUse`.


### 14.10 Cybernetics: the throttle and the modules (brief § Cybernetics)

The brief says to build the throttle once, as shared infrastructure, and then the modules on it. As built:

- **Brass limbs are chassis.** The Hydraulic Arm, Piston Leg and Optic Eye (the soul-blood cybernetics) take
  modules: 2, 2 and 1 (`ImplantSpec.slots`). A module is an item (`ModuleItem`); the ones in a limb are a
  `modules` component on the fitted implant, so they stay with the limb when it is unclipped and moved.
- **Fitting.** At a Surgery Table with the Surgical Rig, no amputation: lay a module on the table and the
  limb's row offers *Fit the module* (into a free slot, or in place of the first when full, which comes
  back); lay Create's Wrench on the table and it offers *Take a module out* (the last one). A module only
  goes in the limb it is made for. `modulesFitAtTheTable`.
- **The throttle** (`cyber/Throttle`). Two keys: hold *Cybernetic throttle* (R) to spool the chosen module,
  tap *Next cybernetic module* (V) to choose. The spool climbs from 0 to full over 2 seconds. While held, soul
  blood drains at 150 mB a second times the cube of the spool: 19 mB/s at half, 150 at full, so full
  throttle is for a few seconds, never a way of life. Letting go fires a firing module at the spool reached
  (plus a 5 mB tap); a held module works every tick at its spool. A tank with no soul blood chokes it: a
  sputter, the message "No soul blood in your backtank", nothing fires. The server keeps the state; the
  client sends only key down (for which limb and slot) and key up. `throttleSpoolsAndDrainsSteeply`,
  `throttleChokesDry`.
- **Seen and heard.** A gauge by the crosshair: the module's icon, a half-dial whose needle climbs with the
  spool from soul-cyan to red, the percentage, and a bar of soul blood left. A whine (the beacon hum) whose
  pitch climbs from 0.5 to 2.0 with the spool, heard by everyone near. The brass limb glows along its seams
  (`*_glow.png`, drawn additively, tinted by the same colour), and near the top it smokes, then throws soul
  fire. Everyone tracking the player is told when spooling starts and stops (and at what game time), and works
  the spool out themselves.
- **The modules** (`cyber/ModuleActions`), each with a cheap baseline and a ramp:
  - *Grappling Spool* (arm, fire): the hook flies along your look, 12 blocks on a tap, 32 at full. A mob no
    bulkier than one and a half players, or a carcass whose rig weighs at most 1.5, is reeled in to you (a
    carcass that arrives is handed to the Meat Hook's own drag tether, as the brief asks); anything heavier,
    a block included, reels *you* in to it at 0.9 to 2.1 blocks a tick. Hitting a wall at more than 0.8 hurts
    as flying into one does. The cable is drawn from your hand, flying out at 3 blocks a tick.
    `grapplingSpoolReels`.
  - *Rotational Coupler* (arm, hold): look at the end of any Create block's shaft within 3.5 blocks and a
    hidden generator (`CouplerBlock`, no collision, no drop) appears in front of that face, turning it at 16
    RPM on a tap and up to 256 at full (stress capacity 16 su per RPM, so 256 su to 4096 su). A brass rod is
    drawn from it to your hand, turning with it and sliding out over its first 6 ticks. Let go, look away or
    run dry and it goes; one left by a reload removes itself. Contraptions leave it behind.
    `couplerDrivesAShaft`.
  - *Piston Ram* (arm, fire): what you look at in reach takes 2 to 8 damage and 1.5 to 5 knockback; looking
    30 degrees or more below level at the ground within reach, it launches you (0.6 to 1.8 blocks a tick
    up, a little forward). With no Gyroscopic Stabilizer the landing is yours to pay for.
    `pistonRamLaunchesAndStrikes`.
  - *Magnet Coil* (arm, hold): always, loose items (not ones just dropped) and experience within 3 blocks
    drift to you (1 mB/s upkeep); held, from 3 to 16 blocks, and from three quarters spool up, carcasses in
    reach are drawn in too (a resting one unfolds first). `magnetCoilDrawsItems`.
  - *Analytical Lens* (eye, always on, 1 mB/s): counts as Create's goggles (`GogglesItem.addIsWearingPredicate`);
    looking at a machine through walls (up to 24 blocks), a box left of the crosshair shows its name and
    distance, its speed, its network's stress against capacity (read through a mixin accessor, since Create
    keeps them protected), overstress, and what goggles would say. `analyticalLensIsGoggles`.
  - *Gyroscopic Stabilizer* (leg, always on): every block fallen past the safe distance costs 10 mB; paid
    in full, no damage; paid in part, that part of the damage. `stabilizerPaysForTheFall`.
  - *Barometric Vent* (leg, fire): a puff of lift, then a hover that lets you sink at 0.04 blocks a tick and
    keeps no fall, for 1 second on a tap and 3.5 at full (kept under the 4 seconds a server lets anyone float
    before kicking them; sinking just faster than the server counts as floating anyway). Crouch to drop.
    `barometricVentHovers`.
  - No redstone-link module, as the brief rejects it.
- **Moving the player.** The player's client moves them, so a hover or a reel is sent to it and done there
  each tick (`ModuleActions.move`, shared with the server, which clears the fall distance and checks for
  walls); a launch is a motion packet.

### 14.11 Set bonuses (brief § Cybernetics)

- `cyber/SetBonus`: four or more flesh grafts (organic prosthetics, the ones on blood) with no cybernetic in
  the body make the **flesh set**: 15% of melee damage dealt comes back as health, and necrosis builds half
  as fast. Four or more brass modules with no flesh graft make the **brass set**: the throttle and the
  stabilizer cost a quarter less, and knockback resistance +0.25. Both kinds in one body: neither, and no
  penalty. Crude prosthetics count for neither. `setBonusesNeedFourAndNoMixing`.

### 14.12 The surgeon minion and the ragged stump (brief § Self-augmentation)

- "Amputation is a prepared ritual, never a field action": cutting flesh off a player (taking a part off with a
  Cleaver on the table, or swapping flesh for an implant in one go) needs an awake **surgeon minion** within 4
  blocks of the table (`Surgery.surgeonAt`). Without one the screen shows "Needs a surgeon" and nothing happens.
  A mob on the table (led on with a lead) is still cut by the player standing by it: the ritual is about the
  player's own body. `amputationNeedsSurgeon` (none, one out of blood, one across the room, one at the table).
- **Surgeons**: the surgeon job comes from the head, as the brief says: villager and pillager heads (their data
  in `mob_traits/minecraft/villager.json` and `pillager.json`; a villager's head starts as a surgeon and also
  offers farmer and courier). The job needs a hand, so a head on a body with no arm fitted is not offered it (nor
  farmer). A surgeon keeps to its table (its home, or the nearest table within 6 blocks when it is set down
  elsewhere) and tends whoever lies there, a heart every five seconds. `villagerAndPillagerHeadsOfferSurgeon`,
  `surgeonKeepsToItsTable`.
- **The ragged stump**: what a surgeon cuts off leaves the stump ragged (`Body.ragged`, saved with the body).
  Fitting anything but a crude prosthetic into a ragged stump (an implant, or the limb back) takes a bucket's worth
  of blood as well (`Surgery.costsBlood`), from a bucket or any fluid item the operator carries, a worn Fluid
  Backtank included. Once fitted the stump is
  dressed: take the implant out and it is an ordinary empty slot. Swapping an implant straight in for flesh
  leaves no stump. `raggedStumpCostsBlood`, `raggedStumpPaidFromBacktank`. (Since §15.23 the fittest surgeon by the table
  cuts, and a stump costs one to three buckets by its fitness, paid from all the operator carries.)
- **The safety floor holds**: fitting, reattaching, swapping implants and modules never need a surgeon, and a crude
  prosthetic (`ImplantItem.crude`: runs on nothing, gives nothing past flesh) never needs blood, even in a ragged
  stump, so one can always go on. `safetyFloorNeverNeedsSurgeon`.
- **Drawn**: a limb gone leaves a stump on the player, the top of the limb in their own skin (sleeve or trouser
  leg and all) with a raw end; a ragged one is longer, its end torn, flaps of flesh hanging off it. In bloodless
  mode the end is plain.
- Found on the way: minion goals that count game ticks ran on every other tick (Minecraft's default), so a
  minion by a trough sometimes never drank; they now run every tick. And a legless body's crawl was 0.05, which
  (a speed counts about squared) barely moved: now 0.12.

## 15. Parts and traits: minions and carcass armour from every piece of a mob

The user asked for "each part of a mob (leg arm head torso and special organ) being a craft ingredient for
either a minion or the armor sets", carried over to "a massively diverse system of unique characteristics".
The design is in `docs/PARTS-AND-TRAITS.md` (a design panel's merged spec, checked against the brief): every
piece has two futures, whole into a minion or through the Mangler into armour scraps that remember their mob
and part; what each does comes from data layered as body shape, family, tag overlays and an optional per-mob
file, composed from about 30 effect types.

### 15.1 Open questions, answered with the design's defaults for now (the user may overturn any)

1. Scraps come from the Mangler only, as the brief's Mangler row says.
2. A carcass chestplate can have a Fluid Backtank strapped on (tier and fluid carried on the chestplate), so
   armour does not unplug every prosthetic.
3. A minion killed in a fight collapses, powered down at 1 HP; never destroyed (config `minion_death`).
4. Items store only where their parts came from; a datapack retune changes items already made (rule 3).
5. Heavy torsos and limbs (never items) are dragged into the Surgery Table's work zone and claimed with an
   empty hand.
6. Organic frames take only unskinned pieces and brass frames only skinned ones (the brief's deglove
   pipeline); hideless mobs count as both.
7. A quadruped's front leg is a leg.
8. Surgeon heads: built for villager and pillager heads, as the brief names them; their kin (other illagers, the
   zombie villager) come with the per-mob data. *Now the owner's call in docs/NEXT.md 1.5* (tasks instead of jobs): any
   minion with a hand may cut, its fitness setting the stump's price (the default), or, with the surgeon task file's
   `"needs_surgeon_head": true`, only heads whose data says `"surgeon": true` (15.21; both built at the table in 15.23).
9. Per-mob signatures are authored last, once the base system works, as the brief defers them.
10. Boss parts (warden, wither) usable by default.
11. Tiers upgrade by crafting (piece + ingot), which Mechanical Crafters automate.
12. A powered-down minion can be folded into a Dormant Minion item by its maker.
13. Mobs that pick up carcass armour get its armour points only.
14. The name is "carcass armour", apart from the "flesh grafts" of self-augmentation.

### 15.2 Slice 1 as built: cow and rabbit armour, end to end (verified)

- **Part slots** (`parts/PartSlots`): the rules are data (`bone_slot_rules/default.json`), matched on the last word of
  a bone's path; the rig's root is the torso; a mob file can name a bone's slot; a bone no rule names goes with its
  parent. Sub-slots (front, mid, hind) and forms (wing, pair, tentacle) come with the slot. `partSlotsFromNames`.
- **Data** (`parts/PartsData`): five folders, loaded with the registries of the reload (so vanilla loot conditions
  parse): `mob_group` (archetypes, families, overlays, one format), `mob_traits/<ns>/<mob>.json`, `trait`,
  `scrap_material`, `bone_slot_rules`. The files go to clients as written (one payload a kind), and both sides resolve
  a mob the same way: archetype (listed, or by leg count), family (highest priority listing it, by id or tag), the
  overlays listing it in priority order, then its own file. Plain trait lists add (the same trait once, at its highest
  level); `{"add", "remove", "replace"}` edits. Cached per mob until data or tags change. `cowAndRabbitResolve`.
- **Traits** (`parts/Trait`, `TraitEffect`, `TraitEffects`): a named, levelled bundle of (trigger, condition, chance,
  cooldown, effect); effect types live in their own registry (`bloodandbones:trait_effect_type`). Built so far:
  attribute, mob_effect, damage, immunity, diet (graze), reaction (hunt, flee); triggers passive, tick, hurt, attack
  (damage out), targeted. 17 traits for the cow and the rabbit.
- **Scraps** (`parts/ScrapsItem`, `source` component): the Mangler's grind of each piece gives its volume in blocks
  times its material's density, half again skinned, half rotten, at least one; they remember mob and part ("Cow Leg
  Scraps"), tinted with the family colour, gore on top (none in bloodless mode, where they are "Salvage").
  `scrapCountsByVolume`, `mangledCowGivesPartScraps` (a real cow ground on a real Mangler).
- **Carcass armour** (`parts/CarcassArmourItem`, `CarcassArmourRecipe`): helmet (5 head scraps), leggings (4 leg +
  3 hips: another mob's tail scraps or more legs), boots (4 legs); every body cell one mob and the right part, or
  torso scraps where the mob has no such bone. A shaped recipe, so JEI and Mechanical Crafters handle it. Armour,
  toughness, knockback resistance and the quirk come from the material through `ItemAttributeModifierEvent`;
  durability is set when made. "Cow Hide Boots", "Rabbit Sinew Leggings" (bloodless: "Rabbit Plated Leggings").
  Worn: its traits (`ActiveTraits`) go on as transient attribute modifiers, damage changes, and so on; a sum trait
  (swift) adds over pieces, others count once; four pieces of one mob add its set's bonus and drawback. The
  chestplate is registered but has no recipe until the backtank strap-on (slice 4). `craftCowBoots`,
  `mixedHelmetRefused`, `rabbitLeggingsRaiseJump`, `sameTraitCountsOnce`, `fullSetOfOneMob`, `fallGuardSoftensFalls`,
  `bloodlessNames`.
- **Drag strength** (`bloodandbones:drag_strength`, a player attribute): takes its share off the carcass-drag
  slowdown, so the Herd Beast set's Hauler III makes hauling 45% easier.
- **Simplification noted:** scraps keep their slot but not which leg they were, so armour gets a mob's traits for
  every sub-key of the slot ("leg" and "leg.hind" alike); minions, built from whole pieces, will tell them apart.
- Seen in the headless client: a player in a cow hide hood and boots and rabbit sinew leggings; scraps and pieces
  in the hotbar.

### 15.3 Slice 2 as built: a cow torso on rabbit legs (the minion rebuild)

The old minion (a player-shaped body with implants, its job from which arms and eyes it had) is gone; a minion
saved the old way falls apart on its first tick, dropping what it carried, the backtank it wore and its implants.

- **The build** (`minion/MinionBuild`, `PieceRef`): a torso and a piece in each socket, each piece remembered as it
  was fitted (mob, bone, look, freshness, skinned, baby). Pieces never rot once fitted. Kept on the Surgery Table
  while built and on the minion once woken, synced to clients as entity data (`MinionSerializers`).
- **Building** (`minion/MinionAssembly`, on the table with the Assembly Frame): lay a carried torso down, or
  claim a whole carcass lying on the table with an empty hand (what is still jointed to its torso comes along,
  and the carcass leaves the world: `cowFrameClaimedFromTable`). A piece goes into the free socket it suits:
  a head the head's, legs the lowest limb sockets, arms the highest. A flesh frame takes pieces with their hide on,
  a brass one only skinned pieces (hideless mobs either way). Too big (3 wide or 4 tall) is refused. A Cleaver takes
  back the last piece, then the torso (`cleaverTakesBackLastPiece`). A bucket of blood wakes it.
- **Sockets and shape** (`minion/MinionBody`): the torso's own rig gives the sockets (its head, limb, tail and
  extension bones); a body with no head or fewer than two limbs gets a made-up set. Each piece is placed at its
  socket, at its own size and in its own rest turn: a rabbit's leg under a cow is a rabbit's leg. The whole is
  lowered or raised so its lowest point stands on the ground. The server sizes the hitbox and the client draws
  from the same layout (`feetNeverBelowGround`, `hitboxContainsParts`).
- **What it adds up to** (`minion/MinionStats`, `MinionData`), read from each piece's own mob data, the most
  specific key first ("leg.hind" before "leg"): health is the torso mob's times its `health_factor` (6 to 150);
  slots and blood held from the torso's size; speed the mean of its legs' speeds, cut by the share of the torso's
  own legs present; the mode (walk, hop) the one most legs share; no legs, the torso's own way (most crawl at
  0.12); jobs and bite from the head. A cow on four rabbit legs with a cow's head is 15 health, hops at 0.325 and
  starts as a courier (`buildCowOnFourRabbitLegs`, `cowOnRabbitLegsOutpacesCow`). Data so far: the quadruped and
  biped archetypes, the grazer and small prey families, the rabbit's hind legs, the villager's head (farmer).
- **Blood** (`MinionEntity`): it drinks 3 mB a minute idle, 15 moving, 25 working, 40 fighting (times the
  `power_drain` config). Below a quarter it walks to the nearest Blood Trough it can reach (a path must reach it;
  within `trough_radius`, 48) and drinks its fill (`walksToTroughAndRefills`, `cannotReachTroughStaysDown`).
- **Never destroyed by neglect**: empty, it powers down where it is and lies on its side, alive; nothing runs;
  only a player can hurt it and mobs do not see it (`drainToZeroPowersDownAlive`, `zombieCannotKillPoweredDown`).
  A lethal blow collapses it the same way by default (`minion_death`: collapse, scatter, destroy). Blood from a
  bucket or a trough beside it wakes it. Its maker can fold one that is down into a Dormant Minion item and set it
  down elsewhere (`foldAndUnfold`). A cap per player (`max_minions_per_player`, default none) is kept in a census
  (`minionCapRespected`).
- **Blood Trough** (`minion/BloodTroughBlock`): four buckets of any `c:blood`, Create's fluid tank behaviour; pipes,
  Spouts and buckets fill it from any side; its surface rises and falls as the Bleeding Rack's does.
- **Drawing** (`client/StitchedBody`, `StitchedMinionRenderer`): every piece in its own mob's look, gone off as far
  as it was when fitted, raw where it was cut and at each empty socket. Legs swing as a walking animal's (one side
  against the other, front against hind), a hopper's together with the body bounding, a head turns to look, a
  tail sways. Down, it lies on its side. The table draws the minion being built lying on it.
- Jobs kept from before: companion, courier, farmer, bodyguard, guard (`courierCarries`, `farmerReaps`); the
  others heads name (herder, surgeon, ...) come with slice 7.

### 15.4 Slice 3a as built: every one of the 79 mobs resolves (data breadth)

- **Groups** (`mob_group/`): the 11 archetypes of spec 3.2 (biped, quadruped, bird, flier, arthropod, fish,
  tentacled, floater, blob, shelled, colossus), each with a complete default for every slot its body has, minion
  side and armour side, so a mob with only an archetype is fully usable (a modded mob by body shape on day one);
  the 27 families of 3.3, each open to modded mobs through `#bloodandbones:family/<id>`; the 16 overlays of 3.4,
  keyed off the tags modded mobs already carry (`#minecraft:undead`, `#minecraft:arthropod`, `#minecraft:aquatic`,
  `#minecraft:fall_damage_immune` and so on, plus our `overlay/nether`, `overlay/ender` and `bosses`). Mob files
  for the strider, the wither and the shulker (its lid is a shell arm), besides the rabbit's, villager's and
  pillager's. `allMobsResolve` checks all 79 against the 3.5 table.
- **Slots**: every bone of every rig gets a slot by rule, not by falling through to its parent; the segment rule
  (spec 2.2) now puts silverfish and endermite segments at head, neck and tail by chain position.
  `everyBoneHasASlot` (with 55 bone-to-slot expectations from the 2.6 table).
- **Materials**: the 15 of spec 3.6, each with its own armour sheets and a clean bloodless set (chitin purple-black,
  bone bloodied white, ember charred with orange cracks, sculk veined teal, golem plate rusted...).
  `everyMaterialLoads`.
- **Traits**: 100 traits in all, those the six built effect types (attribute, mob effect, damage, immunity, diet,
  reaction) with loot conditions can express faithfully. The design names 214: the rest wait on later effect types
  (flags such as wall_climber and silent_steps, kin, senses, produce, auras, pack, impulses, teleports,
  projectiles, hitscans, deflect, detonate, mount, storage, power) and are left out of the data until those exist,
  rather than referenced as names that do nothing. `everyTraitReferenceExists`, `everyPartsFileParses`,
  `everyNameTranslated`.
- Found on the way: trait conditions are read before fluid tags load, so a condition on `#minecraft:water` dropped
  the whole trait; they name the fluids instead. A family's full set now replaces the archetype's (the cow's is
  exactly Herd Beast / Placid), as the rabbit's already did.
- Deliberate numbers the data agents chose where the spec gives none: fish flop on land at 0.05; a shulker
  blink-steps at 0.12; the parrot's tail bone is a tail; `bear_hide` and `golem_plate` read "Pelt" and "Plate" in
  item names ("Polar Bear Pelt Chestplate", not "Polar Bear Bear Hide Chestplate").

### 15.5 Minion bodies: legs set movement, arms the attack (brief § Minions)

- **Legs** (`MinionStats`): at least half the legs climbing (spider legs, `"movement": {"mode": "climb"}`) makes it
  a climber: a spider's `WallClimberNavigation` and `onClimbable` while it is against a wall, synced as the spider's
  is; hungry, it goes straight over a wall to a trough a walking path cannot reach. `spiderLegsMakeItClimb`,
  `spiderLegsClimbAWallToTheTrough`.
- **Riding**: at least two rideable legs (horse legs, `"rideable": true`), at least half of all its legs, and a torso
  that is at least 0.4 of its whole bulk (so a rabbit's torso on horse legs cannot carry you) make it take a saddle
  (`Saddleable`, vanilla's Saddle item). Saddled, its maker climbs on with an empty hand and steers it as a horse is
  steered (`getControllingPassenger`, `tickRidden`, `getRiddenInput`, `getRiddenSpeed`). Out of blood it throws its
  rider; if its horse legs come off, so does the saddle. The saddle is drawn on its back. `horseLegsAcceptRider`.
- **Flying torsos**: a torso that flies, hovers or floats by itself (a bat's, a blaze's) wins over any legs, which
  dangle: flying navigation and move control, no gravity, no fall damage; moving costs twice the blood. Out of blood it
  drops. `flyingTorsoFlies`.
- **Arms**: each arm brings its strike (`"strike": {"style": ...}`, spec 5.6), and they take turns: punch, kick
  (throws back), ram, fling (throws up), grab (Slowness II), sting (Poison), slam (hits everything within 2 of the
  target), claw and hook (tear, with blood), pounce, flap (a buffet, no harm), scrabble (light). A pacifist's arms (a
  villager's pair) never attack. With no arm it bites. `armsTakeTurnsInTheirStyles`, `villagerArmsArePacifist`.

### 15.6 Brass minions: soul canisters and the Charging Cradle (brief § Minions)

- **Building brass**: a skinned torso laid on the Assembly Frame makes a brass frame (the brief's deglove pipeline:
  skinned pieces feed brass, unskinned flesh); it takes only skinned pieces (hideless mobs either way). Brass
  Sheathing (8 brass sheets round an andesite alloy) goes over it, and a Soul Canister wakes it; the empty comes back.
  Blood does not wake brass, nor a canister an unsheathed frame. `brassFrameWakesOnCanister`.
- **Soul Canisters**: an Empty Soul Canister (brass and glass) and 1000 mB of soul blood through a plain
  `create:filling` recipe (a Spout) make a Soul Canister; `create:emptying` (an Item Drain) reverses it, so JEI shows
  it and stock Create automates it. A brass minion holds one canister's worth, two in a torso over a block. Its maker
  can hand-feed one. `spoutFillsCanister`.
- **Power**: brass drains a quarter of what flesh does for the same activity. `brassDrainsAQuarter`.
- **The Charging Cradle**: a kinetic block (2 su per RPM, shaft from below, at least 16 RPM) holding 8 full and 8
  empty canisters and a stack of brass sheets. Any brass minion within 2 blocks that is below a quarter or powered
  down gets a full canister (the empty stays in the cradle for a hopper to take back to the Spout); the faster it
  turns the quicker the swap (a second at 64 RPM). With sheets it mends a docked brass minion a heart a second, a
  sheet every ten. Funnels, hoppers and chutes put full canisters and sheets in and take empties out; by hand, a
  canister or sheet goes in and an empty hand takes the empties. Brass minions low on soul blood walk to the nearest
  cradle with a full canister. Cradles keep a per-level list, and one riding a contraption does nothing.
  `cradleRevivesPoweredDown`, `cradleAutomation`.
- **Each kind has what the other lacks** (spec 6.6): brass is not poisoned, withered or starved, and does not drown,
  but never heals itself: a brass sheet by hand mends 10 health, or the cradle's sheets; flesh mends a heart every five
  seconds on its blood (5 mB each) while it has more than a tenth left. `eachKindHasWhatTheOtherLacks`.
- **The module socket** (brass only): its maker fits one of the player's cybernetic modules by hand (one already
  there comes back; a Wrench takes it out), run at its baseline with no throttle: Magnet Coil (loose items within 6
  drift to it), Analytical Lens (sees its targets through walls, within 24), Rotational Coupler (standing still by the
  end of a machine's shaft, it drives it at 16 RPM, 256 su, the same hidden generator a player's arm uses, gone when
  it moves off or runs dry), Piston Ram (its blows throw hard), Gyroscopic Stabilizer (no fall damage), Barometric
  Vent (drifts down slowly). The Grappling Spool needs aiming, so it is not for minions. `brassMinionTakesAModule`,
  `magnetCoilDrawsItems`, `couplerDrivesAShaft`, `lensSeesThroughWalls`.

### 15.7 Slice 4 as built: armour B (verified in tests and on screen)

The brief's Armor section: "Mangler scraps give the base material; hide modifies an existing piece rather than being a
piece of its own; an organ adds one special ability; all three can come from different mobs, freely combined; a full
set from a single mob grants an extra bonus with a drawback; tiers that upgrade the base armor points using blood
iron, blood diamonds and soul blood netherite".

- **The chestplate** (`recipe/carcass_chestplate.json`, the slice 1 `CarcassArmourRecipe`): six torso scraps of one
  mob under two shoulder cells, arm scraps of any one mob or, where the body's mob has no arm bones (a cow), its own
  torso scraps. Its traits are the torso's and the shoulder mob's arm traits. `craftCowChestplate` (a cow chestplate
  with chicken-wing shoulders: a rabbit has no arm bones to give shoulders, and another mob's torso scraps do not
  stand in).
- **Stamped ingredients** (spec 7.1): the heart, lungs, stomach and eyes `Surgery.harvest` cuts out of a carcass piece
  carry `bloodandbones:source` (mob, torso or head, baby) as well as their name ("Cow's Heart"). What a surgeon takes
  out of a live mob on the Surgery Table (`Surgery.cutOut`) is named for its kind and stamped the same way, a limb with
  its own part (arm, leg); a player's own parts are named for them and unstamped as before. A raw hide skinned off a
  carcass (Flensing Knife or Deglover) is stamped and named for its mob ("Raw Cow Hide"). Hides mobs drop are
  unstamped, so a data map says whose they are (`data_maps/item/hide_sources.json`, `parts/Hides`: leather a cow's,
  rabbit hide a rabbit's, feathers a chicken's, the scutes, phantom membrane, shulker shell). A hide skinned off a mob
  the map gives to another mob is stamped with the skinned one and keeps its own name (a parrot's feathers are the
  parrot's); one the map already gives to its mob (a chicken's feathers) is left unstamped, so it stacks. A raw hide
  with no stamp is a plain covering from no mob. `ScrapsItem.source` reads only scraps, so a stamped hide or organ is
  never taken for scraps. `skinnedParrotFeathersAreTheParrots`, `organFromALiveMobFits`.
- **Fitting** (`parts/CarcassArmourFittingRecipe`, `bloodandbones:carcass_armour_fitting`, one special shapeless
  recipe): a piece and one kind of thing. Hides: one for a helmet or boots, two for leggings, three for a chestplate,
  all of one mob but of any items its hide comes as (two leather and a raw cow hide); they add the mob's `hide` traits.
  An organ: eyes in a helmet, a heart or lungs in a chestplate, a stomach in a chestplate or leggings; it adds the mob's
  `organ_traits` for that organ (every mob's data names its organs' traits since 15.9).
  The next tier's item. A hide or organ replaces the one before, which comes back through `getRemainingItems` into the
  slot the piece lay in, stamped and named as it was (hides of a second item go where new hides lay). The piece keeps
  its wear, enchantments and strapped tank, and its durability is baked again. `CarcassArmour` has `hide` {mob or none,
  the item of each hide} and `organ` {organ, mob, baby}. A hide or organ of another mob breaks a full set (a plain
  covering does not). `hideReplacesAndReturns`, `hidesOfOneMobMayMix`, `fittingReturnsOldOrgan`, `mixedHideBreaksSet`.
- **Mending and wear**: pieces are `setNoRepair`, since vanilla's `repair_item` crafting recipe and the grindstone's
  merge make a blank piece of the larger durability and would lose what both are made of, tiers and tanks included:
  two pieces never combine there. An anvil still mends a piece with scraps of its own mob (`isValidRepairItem`), and
  still merges two pieces as vanilla does, the second used up whole. A strapped tank never breaks with its chestplate:
  `damageItem` takes it off as the chestplate gives way and hands it to the wearer (or drops it at their feet;
  with no wearer, the chestplate holds at its last point), and `onDestroyed` lets it fall free when the chestplate
  burns or is blown up as an item, as a bundle's contents do. The chestplate itself is fire resistant only at tier 3,
  so a soul netherite tank on a lower tier chestplate is left floating in the lava the chestplate burnt in.
  `twoPiecesNeverCombine`, `onlyScrapsMendOnAnvil`, `brokenChestplateGivesTankBack`, `burntChestplateLetsTankFree`.
- **Mechanical Crafters** (checked in `RecipeGridHandler` and `MechanicalCrafterBlockEntity`): they find crafting
  recipes and call `assemble` as a grid does, but give back only what an item leaves by itself
  (`getCraftingRemainingItem`), never a recipe's `getRemainingItems`. So when the input is a `MechanicalCraftingInput`
  a fitting that would give something back, and taking a tank off, are refused and the crafters throw the items out
  whole; first hides, first organs, tiers and strapping work. `mechanicalCraftersFitButNeverSwap`.
- **Tiers** (`parts/ArmourTier`, `armour_tier/<id>.json`, loaded and sent to clients with the other parts files):
  a Blood Steel Ingot (tier 1), a Blood Diamond (2, needs 1), a Soul Netherite Ingot (3, needs 2). Per piece: tier 1
  +1/+2/+2/+1 armour, 0.5 toughness, durability x1.5; tier 2 +2/+3/+3/+2, 2, x2.5; tier 3 +2/+4/+3/+2, 3, 0.1
  knockback resistance, x3.3 and `DataComponents.FIRE_RESISTANT`. Each replaces the last, added with the material's
  in `ItemAttributeModifierEvent`; a hide plate set is 9, 15, 19 and 20 armour, 12 toughness at tier 3.
  `tierNeedsPreviousTier`, `soulNetheriteIsFireResistant`.
- **The Fluid Backtank strapped on** (spec 7.8; `backtank/BacktankStrapRecipe`, `bloodandbones:backtank_strap`):
  a carcass chestplate and a backtank make the chestplate with `bloodandbones:strapped_tank` (the tier) and the tank's
  fluid in `bloodandbones:fluid`. `FluidBacktankItem.wornBy` returns it, and `tier(stack)` and `capacity(stack)` read
  either kind, so implants, the Vent Arm, the throttle and the Backtank Port work unchanged. A `FluidHandlerItemStack`
  on the carcass chestplate, only while strapped, lets Spouts and Item Drains fill and empty it. Its chest armour and
  toughness are the better of the chestplate's and the tank's. Crafted alone, the tank comes back with its fluid and
  the chestplate stays in the grid. `FluidBacktankLayer` draws the tank a pixel further out, on the chestplate.
  `spoutFillsStrappedTank` (through Create's `FillingBySpout` and `GenericItemEmptying`), `implantDrainsStrappedTank`
  (a Flesh Arm on the strapped tank's blood), `unstrapReturnsTank`. A chestplate with no tank on has no fluid handler
  at all.
- **Words**: tooltip lines "Hide: Rabbit" (or "plain"), "Organ: Cow Heart", "Tier 1: Blood Steel Ingot", "Strapped
  on: Iron Fluid Backtank" and its fluid; item descriptions; a JEI information page (JEI does not list special
  recipes). Bloodless: plated armour of salvage, "Covering", "Raw Cow Covering", "Core: Cow Pump" (heart, lungs,
  stomach, eye are pump, bellows, hopper, lens in armour's text, and so are the organ items, "Cow's Pump", and their
  places on the surgery screen), "Fitting a Covering", "Installing a Core", essence steel and essence diamond tiers.
  `fittingWordsHaveBloodlessWording` checks each of these keys has bloodless wording with none of the bloody words.
- Traits are rebuilt on an equipment change only when what the piece is made of changes, not when it wears or its
  strapped tank drains.
- **Simplifications and what waits:**
  - Of slice 4's list, the new effect types and their tests (`blazeCoreChestIgnoresFire`, `endermanHelmetIsEnderMask`,
    `creeperSacBlastSparesWearer`, `fullZombieSetKinAndSunCursed`, `reductionFlooredAt20Percent`) are not built; the
    Organ Ability key, cooldowns and blood cost, and the new triggers are (15.8).
  - Which piece takes which organ is in code (`ORGAN_PIECES`) until organ files with `armour_pieces` exist; the
    gland item, `organ_sources` (rabbit's foot, ink sacs, spider eye) and `Surgery.harvest` reading organ lists by slot
    are slice 3. (All built since: 15.16.)
  - Wool is in no data map and is not stamped, so it stacks with sheared wool and is no hide: a sheep's covering is
    its raw hide.
  - A strapped tank keeps its tier and fluid only; a name or enchantments on it are lost.
  - Not drawn yet: a hide's tinted layer, a tier's trim, an organ's pip. The Deployer route waits for slice 10.

### 15.8 The effect engine (verified in tests; the groundwork the effect types are built on)

The design's 30 effect types (spec 5.4) are split among four groups built side by side, each in its own branch. This
section is their contract: what the shared code does for every effect, and where each group puts its own work so that
no two groups edit the same file.

**What the shared code does**

- **Effects run themselves.** `TraitEffect.Effect` (the record a type is) has `run(TraitContext)`, called when a
  triggered entry comes up (tick, hurt, attack, fall, kill, targeted, activate), and `keepUp(TraitContext)`, called every
  half second while a passive entry's condition holds. `TraitEvents` only finds which entries are due (trigger, context,
  chance, cooldown, `requirements`) and calls them; the six first types keep their special paths (attribute modifiers
  in `ActiveTraits`, damage in `onIncomingDamage` and `onFall`, immunity, graze, reactions), and `mob_effect` now does
  its work in its record. Types that change something continuously (a flag, a visibility, a deflection) are read where
  it happens, through `ActiveTraits.of(host).find(SomeEffect.class)` (or `peek` on a hot path), each `Found` carrying
  the entry, the effect's index in its trait, the entry's data (`facet`) and the effect.
- **`TraitContext`** (host, traits, entry, index, facet, trigger, other, source, amount): `level()` is the ServerLevel,
  `traitLevel()` the trait's level, `context()` "armour" or "minion", `other` the attacker (hurt), victim (attack,
  kill), the mob taking aim (targeted), a minion's target (activate) or the killer (a lethal save), `source` the damage,
  `amount` the damage, the victim's most health (kill) or the distance fallen (fall). `scaled(LevelBasedValue)` is an
  amount times the server's `trait_strength`; `levelled(LevelBasedValue)` a count left alone (amplifiers, durations,
  radii); `pay(mb)` takes blood (below).
- **Who gets traits** (spec 5.9): players from their carcass armour, context "armour"; minions from their build,
  context "minion" (`ActiveTraits.contextOf`); nobody else (a zombie in carcass armour gets its armour points only). A
  trait whose `contexts` leave the host's out is never built in; an effect whose `context` names the other one never
  runs (`ActiveTraits.applies`, which also skips types the server switched off).
- **Minions get their parts' traits** (spec 6.4). `MinionData.traits(store, build)` (pure) lists, one list per source,
  the torso's and every fitted piece's "traits" from the "minion" object of its slot key, layered as armour's are (the
  general key's layers first, "leg", then the specific one's, "leg.hind"; a plain list adds, `{"add", "remove",
  "replace"}` edits; a torso extension counts as "torso", a neck as "head"), and the organ's `organ_traits` "minion"
  list. `ActiveTraits` stacks them as it does armour (max, unless the trait sums). The build now has one organ slot
  (`MinionBuild.organ`, an organ cut out of a mob, fitted by using it on the Assembly Frame; a Cleaver takes it out first;
  it drops with the rest). Traits are rebuilt when the build changes (`setBuild`, or noticed by `ActiveTraits.of`) or data
  reloads; their attribute effects go on as transient modifiers; `MinionStats` stays pure. The minion runs the trait tick
  from its own `tick`, every 10 ticks staggered by id as players are; powered down it keeps its passives but runs no tick
  effects. `minionGetsLegTraits` (a cow on its own legs is sure-footed and steps higher), `minionContextOnly`.
- **The activate trigger** (`parts/Activation`). The Organ Ability key (`client/OrganAbilityClient`, G, rebindable,
  in the Blood & Bones key category; "Core Ability" in bloodless mode) sends `OrganActivatePayload`; the server fires
  the next ready activate effect after the last one tried, piece by piece (helmet, chestplate, leggings, boots, a full
  set's last), so repeated presses cycle. Each effect has its own `cooldown`, shown with `ItemCooldowns` on the piece it
  came from (`ActiveTraits.Entry.slot`, the piece giving the trait its level), and may cost blood: `cost_mb` on the
  effect (default 0), drawn from the worn backtank or strapped chestplate, which must hold `c:blood` (a creative
  player pays nothing). With no tank, too little in it, or soul blood, it costs 3 hunger instead (spec 7.6); too hungry
  for that as well refuses it with a word on the action bar, and the next press moves on (15.11). A minion's `MinionGoals.UseOrgan` (no move or look flags, so it fires while closing in) fires one
  when its target is within the effect's `range` (default 8) and its condition holds, paying from its own blood or
  canister (`MinionEntity.usePower`, never its last drop, so an ability never powers it down); a mindless minion never
  has a target. `activateCyclesPieces`, `activateCostsBlood`, `minionFiresOrganAtTarget`.
- **Our four conditions** (`parts/TraitConditions`, registered loot condition types, so they mix with vanilla's
  `inverted`, `all_of` and `any_of`): `bloodandbones:health_below` {fraction}; `bloodandbones:power_below` {fraction}, a
  minion's blood or canister, or the worn tank (none counts as empty); `bloodandbones:near` {entities or blocks, each an
  id or a "#tag", radius (8), count (1)}, blocks looked for at most 8 out; `bloodandbones:dry_for` {seconds, at_most
  (false)}, from `ActiveTraits.drySeconds`, the time since the host was last in water, rain or a bubble column, noted by
  the trait tick. Tags are named as `TagKey`s, so they parse before tags load. `healthBelowCondition`, `dryForCondition`.
- **The other triggers.** `fall` (`LivingFallEvent`: fall effects run with the distance; a fall-trigger damage effect
  scales that fall's damage, reductions floored as everywhere) and `kill` (`LivingDeathEvent` at low priority, once the
  death stands, run on the killer) are wired as hurt and attack are.
- **Lethal saves.** An effect record that also implements `TraitEffect.DeathSaver` is asked on every lethal blow
  (`TraitEvents.onDeath`, high priority), whatever its trigger, if it is off cooldown, its chance comes up and its
  condition holds; `save(ctx)` returns true only once it has put the host's health above 0, and the death is called off
  (its cooldown starts then). A minion collapses before this is asked, which is its own lethal save.
- **Server config** (`[traits]`): `trait_strength` (1.0) multiplies trait amounts (attribute modifiers, the change a
  damage multiplier makes, and whatever an effect reads through `scaled`); `disabled_effect_types` (ids) makes those
  types do nothing, the traits keeping their other effects. A change reloads everyone's traits. `disabledEffectTypeSkipped`.
- **Tests** can make traits and mob data of their own (`gametest/TestTraits`): `trait(helper, path, json)` gives
  "bloodandbones:test/&lt;path&gt;", `mob(helper, path, json)` a made-up mob "bloodandbones:test_mob/&lt;path&gt;" (it
  resolves on the biped archetype, so its lists say `{"replace": true, "add": [...]}`), `piece(piece, mob)` a piece of it,
  `condition(helper, json)` a loot condition. They sit on top of the loaded data for the rest of the run
  (`PartsData.Store.addTestTrait`, `addTestMobFile`): lookups see them, the lists, the sync to clients and the data lints
  do not. A minion takes a made-up mob's organ (its `organ_traits`), which needs no rig.

**Adding an effect type** is one record in `parts/effect/`, registered in its group's `types`:

```java
/** A shove: the host's target (or whoever hurt it) thrown back and up. */
public record ShoveEffect(LevelBasedValue strength, float up) implements TraitEffect.Effect {
    public static final MapCodec<ShoveEffect> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            LevelBasedValue.CODEC.fieldOf("strength").forGetter(ShoveEffect::strength),
            Codec.FLOAT.optionalFieldOf("up", 0.3F).forGetter(ShoveEffect::up)
    ).apply(i, ShoveEffect::new));

    @Override
    public MapCodec<? extends TraitEffect.Effect> codec() {
        return CODEC;
    }

    @Override
    public void run(TraitContext ctx) {
        if (ctx.other() != null) {
            Vec3 away = ctx.other().position().subtract(ctx.host().position()).normalize().scale(ctx.scaled(strength));
            ctx.other().push(away.x, up, away.z);
            ctx.other().hurtMarked = true;
        }
    }
}
// in MotionEffects.types(types):
types.register("shove", () -> ShoveEffect.CODEC);
```

and data then says `{"trigger": "hurt", "filter": "melee", "cooldown": 40, "effect": {"type": "bloodandbones:shove",
"strength": {"type": "minecraft:linear", "base": 0.6, "per_level_above_first": 0.2}}}`.

**The four groups and their files.** Each group owns one registrar in `parts/effect/` and one client class in
`client/effect/`, and puts its new records and classes in those packages (or its own files elsewhere). Each registrar
has, each empty until the group fills it:

| Hook | Called from | For |
|---|---|---|
| `types(DeferredRegister<MapCodec<? extends TraitEffect.Effect>>)` | `TraitEffects`' static block | its effect types |
| `registerContent(IEventBus modBus)` | the mod's constructor | its own DeferredRegisters (blocks, items, mob effects, entity types, sounds, loot condition or enchantment entity effect types) or Registrate entries in its own class |
| `lang()` | `BBLang.register`, after the trait names | `BBLang.trait(id, name, description)`, `BBLang.bloodless(key, text)`, `BBLang.raw(key, text)` |
| `payloads(PayloadRegistrar)` | `BBNetwork.register` | its payloads; a client-bound handler calls its client class inside the lambda |
| `minionGoals(MinionEntity, GoalSelector goals, GoalSelector targets)` | `MinionEntity.registerGoals` | goals it gives every minion |
| `@SubscribeEvent` static methods | the class is registered on `NeoForge.EVENT_BUS` beside `TraitEvents` | its game events (each has `onServerStopped` so the bus accepts it) |
| `client/effect/<Group>Client.init(IEventBus modBus)` | `BBClientSetup.initEffects`, from the constructor's client branch only | renderers, layers, particles, keys, client payload handlers |

| Group | Owns | Hooks the shared code already calls (neutral until filled in) |
|---|---|---|
| Motion (`MotionEffects`, `MotionClient`) | flag (all 11 flags of spec 5.6), impulse, teleport, deflect, detonate, visibility | `flag(host, name)` (read by `CarcassArmourItem.isEnderMask`, `makesPiglinsNeutral`, `canWalkOnPowderedSnow`), `canGlide` and `glideTick` (the chestplate's elytra hooks), `climbing`, `standsOn(minion, fluid)` and `silent` (`MinionEntity.onClimbable`, `canStandOnFluid`, `dampensVibrations`) |
| Ranged (`RangedEffects`, `RangedClient`) | the vanilla adapter and our 7 actions (spec 5.5), the Bleeding mob effect ("Leaking" in bloodless mode, grey sparks instead of drips), the temporary_web and cooled_crust blocks, projectile, hitscan with its beam | minion ranged attacks go through `UseOrgan` (an activate effect with a `range`) or its own `minionGoals` |
| Social (`SocialEffects`, `SocialClient`) | kin, sense, aura, pack, glow, and anything added to reaction | reaction's goals are handed out in `TraitEvents.onJoin`, which Social owns |
| Upkeep (`UpkeepEffects`, `UpkeepClient`) | regen, mend, produce, storage, power, the rest of diet, lethal_save (a `DeathSaver`) | `drainMultiplier(minion)` (`MinionEntity.drain`), `extraSlots(minion)` (`MinionEntity.slots`), `interact(minion, player, hand)` (first thing in `MinionEntity.mobInteract`), `repairsWith(piece, repair)` (`CarcassArmourItem.isValidRepairItem`); graze in `TraitEvents.onUseBlock`, which Upkeep owns |

**Shared files a group should still stay out of**: `TraitEvents`, `ActiveTraits`, `Activation`, `TraitEffect`,
`TraitContext` and `TraitConditions` (ask for a change instead of making it, or add a hook to your own registrar);
`MinionEntity` and `CarcassArmourItem` beyond the methods the table gives you. Traits are data files (one per trait,
new files, so no clashes); a trait may be added to a mob group's lists, which is where two groups can meet: add to
different lines. Two things are shared whatever is done:

- `src/generated/resources/assets/bloodandbones/lang/en_us.json` (and `en_ud.json`) are written by `runData` from
  every group's `lang()`: when branches merge, take either side and run `runData` again rather than merging by hand.
- `assets/bloodandbones/sounds.json` is one hand-written file: play vanilla sounds pitched and layered (the owner's
  choice) rather than adding sound events.

Rules every effect keeps: player-facing words read right in bloodless mode (`BloodlessWords` softens blood words; add
a `bloodless.` key where it needs more, as the Organ Ability's "Core Ability"); gory visuals have a clean version
through the existing bloodless checks, not another logic path; new blocks are ordinary blocks that move on
contraptions; an effect on a minion never destroys it (power it down instead: `MinionEntity.powerDown`); effects run on
the server (`ctx.level()` is a ServerLevel), and movement a client predicts reads the same traits there through
`ActiveTraits.of`, which notices a change of armour on either side.

**Motion as built** (`parts/effect/MotionEffects`, `MotionFlags`, `FlagEffect`, `ImpulseEffect`, `TeleportEffect`,
`DeflectEffect`, `DetonateEffect`, `VisibilityEffect`; `client/effect/MotionClient`; verified in tests. On the
headless client the client side loads and runs through the showcase with no new errors, but nothing there climbs,
glides or bounces: design risk 2's run with lag on a real client and a dedicated server is still to do).

- **flag** {flag, strength (1)}: the closed list of spec 5.6, read where each acts through `MotionEffects.flag(host,
  name)` (the strongest passive one). A player's own client builds their traits from the armour it sees (`ActiveTraits.of`
  on the client, `peek` on the server). The four a client predicts (climb, glide, bounce, powder_snow) ignore their
  entry's condition on both sides, since a condition only holds on the server; the rest honour it.
  - climb: `MotionFlags.climb`, run by both sides at the start of the player's tick (`PlayerTickEvent.Pre`; the client
    passes whether jump is held, which the server never knows). Against a wall and off the ground, strength 1 clings
    (no faster than 0.05 down), 2 or more climbs at 0.2 with jump held; the fall is forgotten. "Against a wall" is a
    solid block just past the host's sides, worked out from the world rather than `horizontalCollision`, because the
    server never moves a player itself and so never sees them collide. A minion clings while `horizontalCollision`
    (`climbing`); only climbing legs swap its navigation to `WallClimberNavigation`, since the hooks cannot reach it.
  - glide: the chestplate's `canElytraFly` and `elytraFlightTick`, the carcass chestplate worn on the chest only; 0.4
    exhaustion a second, no durability, and it ends when the wearer is too hungry to sprint.
  - bounce: `LivingFallEvent` (high priority, both sides) cancelled for a fall of 2 or more, not while crouching; the
    speed back up comes from the fall's length (both sides agree on it) and is put back on the next tick, the landing
    having zeroed it.
  - powder_snow, ender_mask, piglin_neutral: the armour's own hooks. Vanilla asks only the boots about powder snow and
    only the helmet about the mask, so the mask from any other piece also cancels `EnderManAngerEvent`; on a minion it
    stops endermen taking it as a target (the ender_calm trait itself is Social's kin since the merge, below).
  - silent_steps: `VanillaGameEvent` STEP, HIT_GROUND and SPLASH from the host cancelled; a minion `dampensVibrations`.
  - quick_draw: `LivingEntityUseItemEvent.Tick` takes `strength` extra ticks off a bow or crossbow each tick (twice as
    fast at 1), the same on both sides.
  - inverted_healing: a mixin (`InvertedHealingMixin`) on `LivingEntity#isInvertedHealAndHarm`, the switch vanilla's
    undead use, instead of the spec's `MobEffectEvent.Applicable`: drunk, splashed and cloud potions apply instant
    health directly and never pass that event.
  - trample: a minion breaks `#bloodandbones:trampleable` (leaves, grass, flowers, snow, cobweb...) it walks into or
    through, only where mobGriefing and the new server setting `minion_block_damage` (false) both allow it.
  - lava_walk: `canStandOnFluid` for lava, floating as a strider does after each move, and a goal above the float goal
    that holds the jump while it stands on lava so it does not paddle. Found on the way: Sable replaces the entity
    collision context (`TheFasterEntityCollisionContext`), whose `canStandOnFluid(above, fluid)` asks the entity about
    the fluid *above* the surface rather than the lava, so under Sable nothing stands on lava, the vanilla strider
    included. `standsOn` counts the empty fluid over lava the minion is on as that lava.
- **impulse** {target: self, other or area; forward, up, away; radius; arc: any, front, behind; cushion}: set with
  `hurtMarked` (a player gets it as knockback is sent). "forward" is the host's flat facing, or toward a minion's target;
  a self lunge with `away` and `radius` throws what is round it aside (charge); `arc` is where the other must be (fly
  swat: behind); `cushion` spares whoever is thrown fall damage until they next land (wind burst).
- **teleport** {mode: random, look, behind_target, to_owner; radius; who: self or other; beyond}: every mode lands the
  enderman's way (`EntityTeleportEvent.EnderEntity` may stop or move it, then `randomTeleport` drops it to solid ground
  and takes the spot only if it fits and is dry), random with a chorus fruit's sixteen tries at whole-block heights;
  to_owner only for a companion or bodyguard further than `beyond` from its maker, ten tries round them as a tamed
  wolf's.
- **deflect** {chance, projectiles (ids or "#tags"), mode: reflect, dodge, pass; arc: any or front; against:
  projectile, melee or both}: `ProjectileImpactEvent` cancelled, reflect sending it back with
  `ProjectileDeflection.REVERSE` as the host's; a projectile dodged or let pass is let by for the rest of its flight.
  Blows (a creature's own melee) are dodged in `LivingIncomingDamageEvent`. Its own chance comes up once a blow; the
  entry's cooldown starts when it works.
- **detonate** {power, fire, block_damage, fuse, minion_powers_down}: `Level#explode` with the host as its source, the
  host and its own side (15.11) taken off the list it hits (`ExplosionEvent.Detonate`); blocks only with the trait's `block_damage`,
  `minion_block_damage` and mobGriefing (then fire too); a fuse hisses and smokes first; a minion then powers down.
- **visibility** {multiplier, vs}: `LivingVisibilityEvent#modifyVisibility`, the change scaled by the trait strength.
- Look and sound: a wet burst of blood where a blink leaves and where a blast goes off, scraps of meat from a minion's
  self-destruct, sparks from brass; bloodless mode draws none of the blood (the drops' own check) and keeps the vanilla
  portal and explosion particles. Vanilla sounds pitched down and layered (slime and honey for the squelch).
- **Traits** (new data, with names, descriptions and a bloodless reading): wall_climber (sums), glider, bouncy,
  powder_walker, silent_steps, quick_draw, trample, lava_walk (fire does not hurt it on or in lava), ender_mask, ender_calm,
  piglin_kin, inverted_healing, insulated, evasive, deflector, hiss, leap, dash, charge, warp, wind_burst, blast,
  self_destruct, rift, blink, fly_swat, loyal. stubborn needed no flag and is unchanged. Mob data only where a test
  needs it: the enderman's head armour is an ender mask, and the creeper's `bloodandbones:powder_sac` gives blast
  (armour) and self_destruct (minion); the sac has no item yet, so only a piece or build given it directly has it.
- **Tests** (`gametest/MotionEffectTests`, 18): `endermanHelmetIsEnderMask`, `enderCalmMinionNeverTargeted`,
  `creeperSacBlastSparesWearer` (the wearer in the world, and blocks untouched with mobGriefing off even for a blast
  that may break them), `creeperSacPowersDownNotDestroyed`, `climbFlagClingsAndClimbs`, `bounceCancelsFall`,
  `gliderChestplateGlidesOnHunger`, `deflectReflectsArrow`, `evasiveDodgesMelee`, `teleportRandomStaysOnGround`,
  `impulseLeapOnActivate`, `windBurstCushionsLanding`, `silentStepsNoVibration`, `minionLavaWalkStandsOnLava`,
  `hissHalvesVisibility`, `quickDrawDrawsTwiceAsFast`, `invertedHealingSwaps`, `trampleNeedsGriefingAndConfig`.
- **Left out**, waiting on other groups' types: lava_wader and frost_path (the vanilla adapter's `replace_disk` and the
  `cooled_crust` block, Ranged), displacer (`blink_target`, Ranged), all three since built by Ranged (below); rideable (mount, no group yet). `loyal` has no test:
  its maker must be a player in the world's player list, which a game test sharing one world should not add.
- **Shared files touched**: `BBServerConfig` (the `minion_block_damage` setting spec 6.11 names), and
  `bloodandbones.mixins.json` (one line for the mixin).

**Ranged, as built** (`RangedEffects`, `RangedContent`, `RangedClient`; verified in tests, and the look checked on a
client under xvfb in both modes):

- **The vanilla adapter** (`bloodandbones:vanilla` {effect, target}, `VanillaEffect`): any enchantment entity effect runs
  as `effect.apply(level, traitLevel, new EnchantedItemInUse(piece, slot, host), entity, position)`, the piece being the
  one the trait counts from (nothing on a minion or from a set). `target` is "self", "other" (also written "attacker",
  "victim", "target": whoever else is in it, or for a player's key the creature they look at within the entry's range;
  nothing if nobody) or "look" (the creature or block face looked at, or a minion's target; on a block the host stands in
  as the entity, so it is for place effects: summon_entity, explode, particles). A passive entry runs every half second
  while its condition holds. `damage_item` is refused where the trait is read, nested in all_of or not, and in a
  hitscan's `hit` (a lint that cannot be missed: the trait fails to load with the reason).
- **Our seven actions** in `Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE` (so datapack enchantments can use them), one record
  a file: `launch` {up, away (0)} and `pull` {strength} (knockback resistance takes its share; both land at the end of the
  tick, after the knockback of the hit that set them off, which would otherwise flatten them), `web` {seconds} (a
  `temporary_web` round the feet, only in air or grass-like space and never in water), `bleed` {seconds, amplifier (0)},
  `steal_item` {} (a mob's main hand, else off hand, into the thief's inventory or a minion's, dropped if full; never from
  a player or a minion), `blink_target` {range} (16 tries, as an enderman, through `EntityTeleportEvent.EnderEntity`;
  bosses stay), `ink_cloud` {radius, seconds} (Blindness to all within the radius but the host's side, a puff of squid
  ink and a squirt).
- **Bleeding** (`bloodandbones:bleeding`, `BleedingMobEffect`): half a heart every 40 ticks, halved each level, never
  quicker than 12 (inside half a creature's hurt time a second wound would be shrugged off), as the new damage type
  `bloodandbones:bleeding` (bypasses armour, no knockback; "bled out", bloodless "leaked dry"). Each wound drips (existing
  blood drops, soul blood for nether mobs), squelches, and lays a stain under it through `Blood.stain` one time in three
  at the first level, two in three at the second, always from the third. What has no blood (`Blood.bleeds`: skeletons,
  golems) leaks: the same harm, a hiss, grey sparks (`leak_spark`), no drops, no stain. The ambient particle is its own
  `bleeding_drip`, which a client in bloodless mode shows as a grey spark; the effect is named "Leaking" there and its HUD
  and inventory icon is swapped for a grey one (`IClientMobEffectExtensions`), through `BBClientConfig.bloodless()` as
  every other gory visual is.
- **Blocks**: `temporary_web` (holds as a cobweb, `life` 1-15 seconds counted down by scheduled ticks, then tears with the
  cobweb's snap; no item, no drops, swords cut it fast) and `cooled_crust` (frosted ice for lava: ages a step every one or
  two seconds after two or three, glowing through its cracks from the third, melts back to lava, and one left with fewer
  than two crusts beside it melts at once, as does one broken). Both are ordinary blocks: they ride contraptions and go on
  ageing once set down (the web, with no collision, is in Create's `movable_empty_collider` tag as a cobweb is, 15.11).
- **projectile** (`ProjectileEffect` {kind, count, spread, speed, damage, potion, power}): the eleven kinds of spec 5.6,
  vanilla's own entities. A player's key fires along their look; a minion's organ (an activate entry with a range) at its
  target; a hurt or attack entry at whoever else is in it. Falling kinds are aimed a little high as a skeleton aims.
  `damage` is an arrow's base damage and, for the other kinds, what the hit does in place of their own (blasts left
  alone; the shot is marked with the `trait_shot` attachment). A shot passes through its host's side. A minion's blasts
  and fires break and light nothing unless the server's `minion_block_damage` (Motion's setting, off by default) allows
  it on top of mobGriefing (`EntityMobGriefingEvent`, asked of the shot or of its minion as it lands). Innate arrows and a thrown trident
  (only when the host holds one) cannot be picked up; a splash potion comes out of the host's inventory (a minion's too),
  else from `potion`.
- **A minion's ranged attack**: a passive projectile or hitscan entry (its arms' or organ's) makes it fight at a distance
  with vanilla's `RangedAttackGoal` (`MinionRangedGoal`, priority 1 from `minionGoals`; it holds a vanilla goal and builds
  it again when the minion's longest range or shortest cooldown changes). `MinionEntity` implements `RangedAttackMob`, its
  `performRangedAttack` calling `RangedEffects.rangedAttack`: each shot in range, off its own cooldown, its condition
  holding, its chance come up and its `cost_mb` paid (never the last drop) fires. With arms that hit it lets the melee goal
  have anything within 4 blocks, and it gives way to hunger. Skeleton arms are Bowmen (`mob_traits/minecraft/skeleton.json`):
  arrows of 2, every 30 ticks, 1 mB each, from 12 blocks.
- **hitscan** (`HitscanEffect` {range (16), damage, damage_type (mob_projectile), windup, beam, knockback, hit}): `level.clip`
  for blocks, then the line swept for creatures (0.3 fat), the first hit hurt with `damageSources().source(type, host)`,
  pushed, and given `hit` (any vanilla or our effect; the web shot's web). It squirts blood where it goes in. With a windup
  it charges on the server (a list ticked each server tick) locked on a minion's target or on what a player looked at when
  they pressed (else it follows their look), and lands if the host is still there. `BeamPayload` tells the clients
  tracking the host (and the host) to draw the beam: `RangedClient` draws it as `GuardianRenderer` does (the
  guardian's texture, a turning tube): the guardian's warming from purple to yellow, the warden's wider and sculk teal
  (and its rings along the line when it lands, with its sounds), a strand of silk thin and pale; a shot with no windup
  flashes for 4 ticks.
- **Traits** (new files, lang and bloodless wording; the armour side costs and cooldowns from spec 8, the minion side
  cheaper): fireball (three small fireballs; armour 50 mB, 8 s; minion 9 mB, 5 s, range 12), great_fireball (100 mB, 30 s;
  minion 20 mB), web_shot (hitscan 10, a 5 s web; 25 mB, 10 s; minion 8 s), spit (10 mB, 2 s), shulker_bolt (40 mB, 5 s),
  sonic_boom (10 ignoring armour, 34 tick charge, 100 mB, 30 s), guardian_beam (6, 3 s charge, 40 mB, 5 s), fangs (an evoker
  fang through vanilla's summon_entity where you look; 30 mB, 10 s), snowball_volley (armour: five, 3 s; minion: a passive
  volley of three every second, free), tongue (pull within 6; 15 mB, 5 s), bowman (minion only), webbing (20%, 3 s web, 5 s
  cooldown), bleeding (3 s a level; "Leaking" in bloodless mode), mauler (attack damage +1 and a 3 s bleed), flinger (up
  0.3 a level), displacer (15%, blink within 8), thief (5%), searing (ignite 2 s a level), barbed (1 thorns damage a level
  to melee attackers, not set off by thorns), ember_skin (melee attackers burn 2 s), lava_wader (replace_disk of lava, and
  of its own crust so standing still keeps it, into `cooled_crust` under you while on the ground; fire ×0.75), frost_path
  (replace_disk water → frosted_ice, as Frost Walker). On-hit traits need a direct hit. ink_cloud was a mob_effect
  stand-in; it is now the ink_cloud action: a minion's when hurt (15 s), armour's when hurt below half health (30 s).
- **Tests** (`RangedEffectTests`): `skeletonArmsShoot`, `bleedingDamagesOverTime`, `bleedingBloodlessNoStain`,
  `webShotPlacesTemporaryWebThatDecays`, `lavaWaderCoolsLavaThatMeltsBack`, `fireballActivateCostsBlood`,
  `hitscanHitsFirstInLine`, `squidInkBlinds`, `stealItemNeverFromPlayer`, `vanillaDamageItemRejected`, and
  `flingerThrowsUpAfterTheBlow` (the throw lands after the blow's knockback).
- **Left out, and why**: no trait waits on another group's type. Held weapons (a bow or crossbow in a minion's hand, with
  ammo) are spec 6.4's slice 6 goals, not built; skeleton arms shoot innately. Mauler's +1 is the attack damage attribute,
  which a minion's strikes do not read (their damage comes from its build), so on a minion only its bleed works, as with
  brawler. Only the skeleton's arms are wired to a trait; every other mob's ranged signature (blaze core, ghast, spider,
  llama, shulker, warden, guardian, evoker, snow golem, frog, strider) waits for the wiring step. Fangs are one fang, where
  the evoker's are a row.

**Social, as built** (verified in tests and on a headless client; `SocialEffects`, `SocialClient`, records in
`parts/effect/`). Five types, and two new reaction modes:

- **Who an effect picks** (`SocialFilter`, one word or a list, any matching; "+" joins tests that must all hold):
  `any`, `allies` (one side: a player, their minions and tamed animals, a minion's maker and fellow minions, team
  mates), `others`, `hostile` (monsters, and any mob going for the host or its side), `wounded` (under half health),
  `invisible`, `underwater`, `moving` (not still, not sneaking), an entity id or "#tag", `family:<group>` (a mob group
  in the resolved layers, so "family:canid" is wolves and foxes).
- **kin** {entities, provoked_seconds (30)}: `LivingChangeTargetEvent` (both goal and brain targeting) is cancelled
  for those mobs unless the carrier hurt that very mob within the window (`SocialEffects.provoked`, a per-victim
  last-hurt time noted in `LivingDamageEvent.Post`); kept up, it calls off any already after the carrier within 16.
  Brain targets and anger are cleared too (`callOff`).
- **sense** {kind, range (16), filter}: `night` keeps night vision up. For a player, `echolocate` (every 5 s),
  `tremor`, `reveal` (monsters unless filtered) and `see_invisible` send the entity ids sensed (`SensePayload`, to that
  player alone, never vanilla Glowing); the client outlines them through walls in the sense's colour (echoes pale with
  a wet click and echoes back from what it found; tremor teal and shivering; reveal blood red, grey in bloodless mode,
  its wounded dripping, grey sparks in bloodless mode; the unseen violet). `alert` pings (`AlertPayload`) when a mob
  within range sets its sights on the player, as a passive or from the targeted trigger: a heartbeat's thump and "Zombie
  has its eye on you" at the bottom right with an arrow for which way to look, turning as you turn. A minion guard
  (or hunter or sentry, once those jobs exist) with echolocate or tremor gets `SocialGoals.SenseTarget`: monsters within
  the sense's range as targets without line of sight (tremor only what moves); `see_invisible` restores an invisible
  creature's visibility to it (`LivingVisibilityEvent`); an alert makes it look round.
- **aura** {action, radius (4), interval (20, at least 20), filter, effect, amplifier, duration (100), strength,
  sound, pitch, particle}: `mob_effect`, `pull_items` (items hop to the host, landing about where it stands),
  `bonemeal` (one crop, sapling or berry bush, `BoneMealItem.applyBonemeal`, NeoForge's form of `growCrop`), `calm`
  (mobs going for the host or its side give up and may not take aim at them again for `duration` ticks, until the
  host hurts them), `push` (a knockback away), `rally` (the host's allies, or the filter's mobs, set on whoever hurt it,
  or kept up, on whoever hurt it in the last 5 s). An `effect` with any other action goes on everything touched (the
  roar's slowness). Kept up it goes off every interval; from a trigger, then; never twice in a second
  (`pulseReady`).
- **pack** {per_ally, allies (allies), radius (8), cap (3)}: a direct hit by the carrier is multiplied by 1 plus the
  share for each ally within the radius, up to the cap, times the trait strength (`LivingIncomingDamageEvent`).
- **glow** {colour ("#5fe8c8")}: drawn only. `CarcassGlowLayer` on players draws each worn piece whose own traits glow
  (all four for a full set's) again with a speckle of photophores, full bright and added to what is under it, nudged
  towards the camera as `armorCutoutNoCull` is, breathing slowly; a glowing minion is drawn at full brightness.
- **reaction** gains `friendly` (those mobs never take aim at the carrier, unless it hurt that one within 30 s, as kin)
  and `defend` (`SocialGoals.DefendHost`, handed out in `TraitEvents.onJoin`: whatever hurt a carrier within the radius
  lately, or any monster it is fighting, becomes their target).
- **Traits** (data, with words and bloodless wording): dead_face, skeleton_kin, raider_kin, piglin_kin, ender_calm
  (minions), golem_trust, beloved, echo_sense (8 + 8L), tremor_sense, spectral_sight, blood_scent ("Damage Sense"
  in bloodless mode), wide_eyes, pack_hunter, item_magnet (3 + 2L), horde_call, purr, toxin_puff, glow_aura,
  wither_aura, fatigue_aura, roar (Organ Ability, 20 s), play_dead (its regeneration, and a calm for "hostiles lose
  you"), cat_terror and warped_dread (mob_effect under the near condition), luminous (the glow, for glow squid parts).
  alert gains its ping, dolphin_kick its minion half (Dolphin's Grace for its side), and the Shambler set (rotting)
  dead_face. New entity tags `friendly_golem_trust`, `friendly_beloved`, `defends_beloved`.
- **Tests** (`SocialEffectTests`): `fullZombieSetKin`, `kinProvokedWindowExpires`, `packHunterScalesWithAllies`,
  `auraPurrHealsAllies`, `itemMagnetPullsItems`, `calmAuraClearsTargets`, `rallyAuraSetsAlliesOnAttacker`,
  `alertSendsPing`, `echolocateMinionTargetsThroughWall`, `belovedGolemsDefend`, `socialTraitsLoadWithWords`.
- **Shared code touched**: `TraitEvents.onJoin` (Social's) hands out the defend goal; `StitchedMinionRenderer` passes
  its light through `SocialClient.minionLight`, one line, as nothing else reaches a minion's drawing.
- **Left out**: no trait on the list waits for another group's type. Not built: senses outlining blocks (the
  sniffer's suspicious sand) and the mimic's alarm; a sense does not raise a minion's follow range attribute (the
  hunting goal reaches as far as the sense instead); villagers need no friendly reaction (they never attack), so
  beloved's lists golems only. The mobs whose signatures use these (bat, dolphin, warden, spider, parrot, wolf, cat,
  pufferfish, glow squid, zombified piglin, ravager) are wired with the rest of the mobs.

**Upkeep, as built** (`UpkeepEffects`, its records in `parts/effect/`, `Diet`; verified in tests; nothing new is drawn,
so `UpkeepClient` is still empty):

- **regen** (`RegenEffect` {amount, cure, mob_effect}): `amount` health each time its entry comes up. A flesh minion pays
  5 mB a heart (`MinionEntity.REGEN_COST`) through `usePower`, never below a tenth of its blood, as its own mending does; a
  brass minion never heals itself (the brief). `cure` clears "harmful", "all" or named effects and `mob_effect` gives one
  after, so one ability is honey (poison cured, then Regeneration II) and cleanse is a regen of nothing that clears every
  harmful effect. (The spec gives mob_effect a `clear`; putting it on regen kept the engine's record untouched.)
- **mend** (`MendEffect` {mode, items, amount}): "item": one of `items` (ids or "#tags") used on a hurt minion mends
  `amount`, flesh or brass alike, and is used up (the `interact` hook); on armour it is a valid anvil repair
  (`repairsWith`). "self": the piece the trait counts from (every worn piece, for a full set's) knits `amount` durability
  back on each tick.
- **produce** (`ProduceEffect` {item or loot_table, count, consumes, cost_mb, coloured, sound}): a flesh minion puts it in
  its pack (on the ground when full), paying `cost_mb`; a brass one makes nothing. A player gets it in their pack, paying
  from the tank. With `consumes` it needs that container (bucket, bowl, glass bottle) and makes nothing without; the same
  container used on the minion by hand is filled at once for the same blood (milking). `coloured` swaps a white item for
  the colour the minion's sheep carcass kept ("wool"). A tick entry's first go waits a whole interval after the host loads,
  so reloading a chunk is no faster way to milk it. Loot tables roll with the gift parameters, as a cat's morning gift does.
- **storage** (`StorageEffect` {slots, chest}): slots on top of the torso's (`extraSlots`). `MinionEntity.inventory`
  now holds 54, the spec's most; `slots()` is what it may use. Once a second (`onMinionTick`, down or not) anything past
  its slots moves into a free slot or falls out, never lost. `chest`: only once its maker has used a chest on it (kept in
  the entity's persistent data; dropped if the trait goes, or on death).
- **power** (`PowerEffect` {capacity_mult, drain_mult, refuel, feed_on_kill_mb}): `capacity_mult` scales a flesh
  build's reservoir in `MinionStats` (pure: `PowerEffect.capacity(store, build)` stacks the build's traits as
  `ActiveTraits` does; brass holds whole canisters), so troughs, gauges and conditions all see it. `drain_mult` multiplies
  `drainMultiplier`, with the minion's `blood_upkeep`. `refuel` {item or "#tag": mB} is blood food: fed by hand, or eaten
  from its pack once below a quarter. `feed_on_kill_mb` feeds a minion, or a player's worn tank if it holds blood or
  nothing. On a player, `drain_mult` goes on the new `bloodandbones:blood_upkeep` attribute (1 by default, on players and
  minions, spec 5.7), which `BodyEffects.drain` now pays implant drain at (a share of a drop paid that share of the time).
- **diet** (`TraitEffects.DietEffect`, grown in place: {effect, hunger, foods, saturation, amount, mob_effect, forage_mb};
  the eating is `Diet`): on `LivingEntityUseItemEvent.Finish`, "safe" takes off the food's harmful effects,
  "bonus_saturation" adds `saturation` plus `amount` of the food's own, "cure_one" clears one harmful effect, "toxic" gives
  `mob_effect` (poison by default); any kind may give `mob_effect`. "edible" makes things food: used while hungry (a
  `RightClickItem`), eaten at once for `hunger` and `saturation`. A flesh minion forages a diet with `forage_mb` once a second
  while awake and under half full: food from its pack, else (where mobs may grief) grass or mushrooms at its feet, or a
  grass, mycelium or podzol block under it, which goes to dirt. Foods are ids or "#tags", read when eaten, so tags need not
  have loaded when the trait did. Graze stays in `TraitEvents#onUseBlock`.
- **lethal_save** (`LethalSaveEffect` {chance, health, cost_mb}, a `DeathSaver`): `chance` at the trait's level; a player
  is left on `health` with harmful effects and fire gone, paying `cost_mb` from the tank; a minion collapses (1 health,
  powered down). With the default `minion_death` a minion collapses before savers are asked; with scatter or destroy the
  saver collapses it. `MinionEntity.die` now forgets a minion in the census only if the death went through. A heartbeat,
  a totem's flicker and a spray of blood (bloodless mode leaves the flicker: the drops' own check hides them).
- **exposure** (`ExposureEffect` {damage, damage_type, ignite}), not one of the spec's 30: the harm a drawback's
  surroundings do. The spec reaches sun_cursed, water_hurts and heat_hurts through the vanilla adapter's ignite and
  damage_entity (Ranged's). They stay on exposure after the merge: vanilla's `damage_entity` names the host as the one
  who hurt it, so it would die "whilst trying to escape" itself and its own attack traits (a bleed, a theft, a harder
  hit) would go off against it; and exposure's harm is scaled by `trait_strength` as the rest of a trait's is.
- **Traits** (new files, lang, bloodless names where the word is flesh: Milk Tap, Stew Tap, Egg Dispenser, Silk
  Spinner, Ink and Glow Reservoir, Honey Hopper, Bamboo and Mycelial Stomach, Lean Core): milk_udder, stew_udder
  (5 min, 50 mB; by hand too), egg_layer (minion 5 min, 10 mB; armour 10 min), wool_regrowth (5 min, 20 mB, its sheep's
  colour), silk_gland, ink_gland, glow_gland, honey_stomach (a bottle a minute; since the merge also Social's bonemeal
  aura, a crop within 4 each minute, as the spec's bee has), scute_shed, morning_gift (the cat's gift table in the first minute of the morning, once a day), four_chambers
  (plant foods +50% saturation), omnivore (+1), seed_eater, bamboo_gut, mycelial_gut (mushrooms edible; mushrooms and
  stews give Regeneration I 5 s), forager (wheat, hay, grass: 100 mB each), cookie_poison, iron_gut (extended: rotten flesh
  and raw meat safe; a minion's blood food at 25 mB), beast_of_burden (drag strength +0.15, a chest for +18; minions now
  have the drag_strength attribute), saddlebags (+15), hump (reservoir x2; no second seat, as there are no seats yet),
  leaky (x1.5), marrow (x0.8), repair_with_iron (an ingot mends 25; minion-only, as spec 5.10 lists it), cleanse (50 mB,
  2 min), honey (30 mB, 2 min), undying (20% a level, 5 minutes), second_wind, adrenaline (levelled to II), roll_up,
  sun_cursed (by day, open sky, dry a second: alight 8 s; no helmet helps), water_hurts (1 a second while wet, as drowning),
  dry_out (a minute dry: Slowness and armour -2), heat_hurts (the biomes where snow golems melt, named one by one since
  biome tags are not bound when traits load, or the Nether). The chicken's egg gland is wired
  (`mob_traits/minecraft/chicken.json`).
- **Tests** (`UpkeepEffectTests`): `chickenOrganLaysEgg`, `produceInertOnCyber`, `regenHealsMinionForBlood`,
  `brassNeverSelfHeals`, `repairWithIronHeals25`, `saddlebagsAddStorageKeepsItems`, `undyingSavesOnceThenCooldown`,
  `undyingMinionCollapsesNotDies`, `sunCursedIgnitesInDaylight` (open sky, the time set and put back in one call),
  `dryOutAfterSeconds` (a one-second twin of dry_out), `ironGutRefuelsMinion`, `dietOnEating`, `milkByHandAndHump`,
  `woolRegrowsInItsColour`, `mendSelfRepairsArmour`.
- **Shared code touched**: `TraitEffects.DietEffect` (its fields), `MinionStats.of` (the reservoir), `MinionEntity`
  (54 slots; the census after a called-off death), `BodyEffects.drain` (blood upkeep).
- **Left for later**: the beast of burden's chest is not drawn; the evoker's totem undying (500 mB, 20 minutes) and every
  other mob's wiring wait for the per-mob data.

**The four groups merged** (Motion, Ranged, Social, Upkeep, in that order; verified by the full suite, run three times):

- **One trait, two files.** Motion (as flags) and Social (as kin) both wrote `ender_calm` and `piglin_kin`, and both
  registered their words, which datagen refuses. `ender_calm` is Social's kin for endermen, with its 30 second window
  for an enderman the minion hurt; Motion's ender mask still keeps endermen off any minion that has one. `piglin_kin`
  carries both: the `piglin_neutral` flag (vanilla's gold check, for worn armour) and the kin for piglins and brutes
  (minions too, with the window). The words are Social's, reworded to cover both.
- **Handlers meeting on one event.** Motion's ender mask check on `LivingChangeTargetEvent` runs early (high), with
  Social's kin and calm, so a target called off never sets off the host's targeted effects or an alert. Ranged's
  "a shot passes through its own side" runs early on `ProjectileImpactEvent`, before Motion's deflect, so a friend's
  deflector never turns a shot that was going through it; its note of a minion's shot landing stays late.
- **Settings and blocks.** A minion's shots now break and light blocks where `minion_block_damage` and mobGriefing both
  allow it, as its blasts and trampling do. Trampling tears the temporary web as it does a cobweb
  (`#bloodandbones:trampleable`).
- **What the merge unblocked.** honey_stomach gains Social's bonemeal aura (a crop within 4 each minute, the spec's
  bee). lean (blood upkeep −10% a level, spec 5.10) is data on Upkeep's `blood_upkeep` attribute. The drawbacks stay
  on exposure, for the reason given under Upkeep.
- **Tests** (`MergedEffectTests`): `piglinKinBothHalves`, `calledOffTargetFiresNoAlert`,
  `friendlyShotPassesFriendsDeflector`, `minionBlockDamageCoversShotsAndWebs`, `honeyStomachGrowsCrops`,
  `leanLowersUpkeep`.
- **Still waiting**: rideable and the hump's second seat (the mount type, slice 6); keen_butcher (the `butchery_yield`
  attribute of spec 5.7); held weapons and a minion's own strikes reading attack damage (strike, slice 6). The per-mob
  wiring of the signatures (spec 8.2) is 15.9.

### 15.9 Every mob wired (verified in tests)

The effect engine's traits are placed in the mob data as spec 3.3 (families), 3.4 (overlays), 8.1 (the detailed samples)
and 8.2 (the 79 signatures) give them: minion traits in a part's `"minion": {"traits"}`, armour traits in its `"armour"`
lists (per piece where the spec names one), hide traits in `"hide"`, an organ's in `"organ_traits"` (with the organ named
in the `"organs"` list of the part it comes from, for when organs are cut out by those lists), and sets in `"full_set"`.
Group defaults first (brief rule 2): a facet shared by a family or an overlay lives there, and a mob's own file holds only
its signature. 59 new mob files (69 in all), each listing its signature facets.

- **Groups.** Families: the grazer torso is a beast of burden and its stomach four-chambered; equine tails swat flies;
  swine charge and their hide is barbed; canid bites bleed, their helmets smell blood and their boots are silent, the
  Pack Gland hunts in packs; felines step silently, their chest dodges, Nine Lives is undying; the bear's Brown Fat holds
  more blood; behemoths trample; villagers' hide is trusted by golems, their heart beloved, their pair of arms carries
  nine more stacks; illagers' helmets and Raider Gland are raider kin (Raid Captain too); piglins' helmets, hide and set
  are piglin kin; golem shoulders fling; arachnid legs climb walls (one piece clings, two climb) and the Spinneret shoots
  webs on a minion and webs on hit in armour; vermin boots are silent; the fowl Gizzard eats seeds; spirit helmets see
  the invisible; guardian chests are barbed and the Prism Eye fires the beam; slimes bounce; marine, amphibian and
  cephalopod sets dry out. Archetypes gained the stomach's defaults (spec 4.5: a minion forages, armour is an omnivore).
  Overlays: rotting torsos burn in the sun, rotting helmets are dead faces, the Rot Gut; skeletal torsos leak blood and
  the Marrow (armour: lean); undead sets add inverted healing, frozen sets heat and frozen torsos and hides are
  insulated; ender chests blink away when hurt (rift) and ender sets hurt in water; the breeze's deflect overlay reflects
  arrows (torso and chest); raiders' minions are raider kin; and the snow overlay (`#minecraft:powder_snow_walkable_mobs`)
  gives boots the powder walker, now that the flag exists.
- **Every mob has a special organ** (the brief: "every mob has at least one"; spec 5.8's lint): an organ other than the
  heart, lungs, stomach, eyes and core, with an ability. Where the spec names none, the design's own words or the mob's
  vanilla habits chose it: the Rot Gut of every rotting mob (spec 7.10 names it; iron gut and hunger-proof), the equine
  Spleen (a horse's spleen dumps its stored blood when it runs: second wind), the fowl Gizzard (spec 3.3's "Stomach:
  Gizzard"), the goat's Leap Gland (a goat's long jump) and the sniffer's Olfactory Bulb (blood scent, until senses can
  outline blocks). Every special organ has a name and a bloodless one that is a machine part (spec 7.10: Rumen is a
  Fermenter, the Rot Gut an Iron Hopper, the Spleen a Reserve Cell), in `BBLang`. The organs have no item yet (the gland
  item and harvesting by these lists are slice 3's), so in play only a piece or build given one directly has it. (They
  have since: 15.16.)
- **Sets.** The rotting and skeletal overlays' sets (Shambler, Ossuary) are now whole sets, replacing the archetype's and
  family's (spec 7.7: an overlay's set beats them). To let the tag overlays after them still add their drawbacks
  (undead's inverted healing, frozen's heat), rotting and skeletal apply first among overlays: priority 105 and 106 (the
  spec's 200 and 210 put them after undead, which a replace would have undone). Mob sets: Inferno (blaze), Walking Bomb
  (creeper), Voidwalker (enderman), Mountaineer (goat), Desert Shambler (husk), Colossus (iron golem); the hoglin's set
  adds warped dread, the parrot's cookie poison.
- **Contexts made honest.** The lint (below) found traits whose effects did nothing on one kind of host, and they now
  say so: luck, attack speed and the player-only attributes (appraiser, lucky, pecking_order, quick_hands, burrower,
  deep_digger, long_reach, stealthy) and the diets a minion cannot forage (four_chambers, omnivore, cud_chewer,
  cookie_poison) are armour only, as are glider, powder_walker, quick_draw and blood_scent (a reveal outline is drawn for
  a player alone); iron gut's and mycelial gut's eating, insulated's powder snow and piglin kin's gold check are armour
  entries. So the Gold Gizzard and Burrow Gland give minions nothing (their minion halves wait for jobs), and the rabbit's
  foot gives a minion evasion only.
- **New traits** (13, each of built effect types, with words and bloodless wording): fat_reserve (a quarter more blood a
  level: bacon fat, brown fat, hump fat), quench (burning time halved: the Blaze Core), stinger (a quarter of blows,
  Poison II: bee, bogged), wind_shot (a minion's wind charges: the Wind Core), creeper_kin (Walking Bomb), iron_will (+4
  armour below half health: the Golem Core), pouch (+9 slots: villager arms, the fox's Cheek Pouch), lanolin (the piece
  mends a point every 30 s: the Lanolin Gland), eight_eyes (hostiles outlined within 8 in the dark: the spider's helmet),
  traders_draught (invisible when targeted at night), potion_thrower (the witch's arms), remedy (the witch's Alchemical
  Gland) and totem (the evoker's Totem Gland: 500 mB, 20 minutes).
- **Stand-ins and caps**, where a built trait is near but not exact: levels past a trait's most are capped (the iron
  golem's Hardy V is III, the turtle's Thick Hide IV is III); Nine Lives uses undying's 5-minute cooldown, not 20; the
  amphibian set dries out at 60 s, not 120; leap for the fox's and phantom's pounce;
  echo sense for the dolphin's Melon; featherfall for the ghast's boots;
  searing for the magma core's minion; blood scent for the sniffer; plain innate arrows for the stray's and bogged's
  tipped ones. A few built minion fields came with them: ram bites for goat, hoglin and zoglin heads (spec 6.4's horned
  heads), rideable camel legs.
- **Lints** (`DataLintTests.dataLints`, spec 5.8): every trait's cooldowns are 0 or at least 20 and its tick intervals at
  least 20, no trait uses damage_item, and no effect is in a context where it does nothing (an attribute the host type
  lacks, a flag it never reads, a diet a minion cannot forage, storage or capacity on armour, a passive shot or a reveal
  where only the other kind fires or sees it); every trait a group or mob file names is loaded, meant for the list's kind
  of host, within its most level, and named, with bloodless words free of blood; every vanilla mob's resolved levels are
  within the most, it has a special organ with an ability, named, and named as a machine part in bloodless mode, and its
  set is named. `DataLintTests.signatureLint`: each mob file's `"signature"` names only what the file has, every mob signs
  its own file or its family's (horse, guardian) or has a note of what it waits for, and the notes are logged every run
  as the report of what is missing (jobs, movement modes, mounts, variant and name captures, held weapons, multi-head
  mouths; and of the groups, Centaur's mount jump, Glutton, Alpha's wolves, Nine Lives' cooldowns, crusher boots,
  Infestation's arthropod kin, Ethereal's passing projectiles, turtle and shulker scutes, rideable and keen_butcher).
- **Tests** (`SignatureTests`): `blazeCoreChestIgnoresFire` (slice 4: burns out in half the time, fire hurts a quarter
  less, the core's three fireballs for 50 mB), `fullZombieSetKinAndSunCursed` (slice 4: the Shambler whole, zombies leave
  the wearer be, and it burns by day under the sky), `reductionFlooredAt20Percent` (slice 4: one piece or two let a fifth
  through, a full set's bonus may make an immunity), `signatureTraitsReachTheirHosts` (a zombie torso burns and a husk's
  does not; bacon fat and brown fat hold more blood; villager arms carry nine more; a cow's rumen and a rabbit's foot in
  armour). The cow now has its own file and the rabbit the snow overlay, which `cowAndRabbitResolve` and `allMobsResolve`
  expect.
- **Section 9 tests still not possible**: `skeletonGivesMarrow` (harvesting organs by these lists; built in 15.16), `phantomWingsLiftCow`
  and `chickenWingsDoNot` (flight from wing lift), `moddedZombieByTagIsRotting` (a modded entity type to tag).

### 15.10 Review of the minion work (fixed)

- A minion could turn on its maker (a sweep of the sword made it the last attacker, and the hurt-by goal now runs
  for every job); `MinionEntity.canAttack` leaves out its maker and its maker's other minions. A pacifist (no arm
  that hits) took targets it never fought, paying the fighting rate and refusing to follow; no target goal starts
  for one now. `neverTurnsOnItsMaker`, `pacifistTakesNoTarget`.
- Minions drank through walls and floors: a trough within reach was taken with no path, and one powered down woke
  from any trough near it. Now a trough must be reached by a path, and drinking needs a clear line from its head
  to the trough (`MinionGoals.overTheRim`). `troughBehindAWallIsNoUse`; `cannotReachTroughStaysDown` now starts
  it hungry with blood enough to walk there, so it tests the rule.
- Seeking and job goals searched a new path every tick once a path ended short, and never gave up. They re-path
  once a second now (`MinionGoals.Approach`; asking again while a path is followed costs nothing), give up after
  five seconds without moving a block, and remember what they could not reach for half a minute, so the next
  trough, cradle, crop, chest or item is tried. The Charging Cradle is chosen only if a path reaches it and it can
  swap now (turning, a full canister, room for the empty: `ChargingCradleBlockEntity.canServe`). A surgeon whose
  table cannot be reached stands down for ten seconds; it counts as at its table from as far as a path of its width
  ends (it stood too far off to tend when broad). `lowBrassWalksToTheCradle`, `walledInCradleIsNoUse`.
- A flier (never on the ground) or a swimmer never set off for a trough; the gate is now whether its navigation can
  path from where it is. `hungryFlierDrinks`.
- A folded minion kept its old home, so a surgeon walked back to its old table and farmers and couriers read the
  blocks round a far-off home (loading those chunks). Unfolding sets its home where it is set down, and job goals
  never read blocks in unloaded chunks. `foldAndUnfold`, `unfoldedSurgeonTakesTheTableBesideIt`.
- Never lost: a Dormant Minion item is fire resistant, cannot be hurt, never ages away, and one fallen out of the
  world is set down on solid ground (where it was made, else the world's spawn, else the End's platform:
  `MinionEntity.safeGround`); a minion fallen out of the world is set down powered down, as a killing blow leaves it
  (by default). `foldedMinionIsNeverLost`, `fallenOutOfTheWorldIsSetDown`. Its maker's Cleaver takes a powered-down
  minion lying on a clear Assembly Frame table back into a frame there (spec 6.2.6), freeing its place under the cap.
  `takenApartOnTheTable`.
- An old-style minion fell apart dropping only its inventory; its worn Fluid Backtank and fitted implants now come
  too. Where minions may die, what they carried, their saddle, module and scattered parts drop with their equipment
  (as a horse's chest does), not with mob loot, so `doMobLoot` off no longer eats them.
  `oldMinionFallsApartKeepingEverything`.
- The safety floor: a crude prosthetic needed a bucket of blood to go into a surgeon's ragged stump. It never does
  now; other implants and the limb itself still pay. `safetyFloorNeverNeedsSurgeon`, `raggedStumpCostsBlood` (now
  with a Flesh Arm and a reattached arm), and `amputationNeedsSurgeon` also tries swapping a Hook Hand in with no
  surgeon.
- Flesh against brass: flesh now keeps the hide traits of up to three different mobs among its pieces (spec 6.6;
  `ActiveTraits` builds them for a flesh minion, and trait effects are read in the "minion" context there), a real
  thing brass cannot do. A coupled brass minion pays the working rate. A ridden minion paid the idle rate (the server
  holds a ridden mob's own motion at nothing); a rider pressing on counts as moving, and a ridden brass minion does
  not couple. `fleshKeepsItsHidesTraits`; `brassDrainsAQuarter` now runs a full minute and checks the ratio.
- The rider sat on top of the whole hitbox (a raised head and all); it sits on the saddle now, the torso's top worked
  out once in common code (`MinionBody.saddlePoint`) for both the seat and the drawn saddle. `horseLegsAcceptRider`.
- Couplers: a coupler someone else holds was taken as free space, and letting go removed it for both; a minion's
  coupler went over water, snow and plants, leaving air. Only its owner's own coupler counts as free now, a release
  removes only one's own, and a minion couples only into air. A coupler reloaded with its minion sends the minion's
  new entity id, so the shaft is drawn to it again. `couplerLeavesSnowAlone`.
- Smaller: the Magnet Coil marks the items it pulls so clients see them glide; a blood bucket by hand goes in only
  when half of it fits; trough and cradle lists are cleared when the server stops; a courier or farmer looks for
  items one tick in ten, and not at all when full; drawing a minion no longer rebuilds per piece, every frame, what
  its layout already fixes (`StitchedBody`), nor a stump's key and random source.
- Bloodless: new wording for the ragged stump ("Open socket"), the surgery and trough pages and the flesh-only
  messages, minion and stitch reworded as construct and rivet by `BloodlessWords`, and a clean riveted icon for the
  Dormant Minion.
- Left as it is: a half-built frame on a carcass body from before the rebuild (the old `minion_frame` component) loses
  what was fitted to it. Only development builds ever had it. `minion_death` scatter and destroy still let the void
  kill.

### 15.11 Review of the effects work (fixed)

The effects branch took in the minion review (15.10) by a merge, not by keeping its own side of the files both touched:
a minion's `canAttack`, `onBelowWorld` and `safeGround`, the saddle seat and `steered`, the half-bucket rule, the take-apart
and the drops with equipment all stand beside the effect hooks. Flesh's hide traits (`MinionEntity.hides()`, spec 6.6)
are one more source in `ActiveTraits.fromBuild`; the trait tick runs once, every half second, awake or down (tick effects
wait while it is down). `fleshKeepsItsHidesTraits` now asks that brass has no hide trait, since brass has its parts' own.

- **Cooldowns start when the effect goes.** A hurt, attack, tick or fall entry used up its cooldown on a check whose
  condition failed, so a Spleen's second wind hit at 18 of 20 health was spent for a minute, and a cat's morning gift
  came up at two fixed times of day. `TraitEvents.goes`: off cooldown, the condition holding, then the chance; only then
  does the cooldown start. `cooldownWaitsForTheCondition`.
- **Cooldowns are saved** (spec 7.6). They were kept in a map by creature object, so a relog, a trip home from the End or
  a minion's chunk reloading cleared them (a Totem every few seconds). Now each is the game time it runs out: an Organ
  Ability's in the `cooldown_until` component of the piece it came from (it goes with the piece; logging in shows it on the
  piece again), everything else in the `trait_cooldowns` attachment, saved with the creature and kept through a clone
  that is not a death. `cooldownsOutliveTheCreature`.
- **A minion's trait health survives a reload.** Trait modifiers are transient and not saved, so a loaded minion's health
  was cut down to its torso's before its traits came back. `readAdditionalSaveData` builds its traits and sets the saved
  health again. `traitHealthSurvivesReload`.
- **Caps** (spec 5.8): trait modifiers on movement speed rise at most 40% of its base together, on jump strength 0.3, and
  a minion's health stays within `MinionStats.MIN_HEALTH` and `MAX_HEALTH` (6 to 150); rises are scaled down together,
  falls likewise (`ActiveTraits.capScale`). `traitCapsHold`.
- **Organ Abilities without blood** cost 3 hunger (spec 7.6; `Activation.HUNGER_COST`), refused only when the player has
  less. `activateCostsBlood`, `fireballActivateCostsBlood`.
- **Nothing cheats the void or /kill** (`BYPASSES_INVULNERABILITY`), as with a Totem of Undying. `nothingSavesFromTheVoid`.
- **The attack trigger is for blows.** Thorns sent back (a barbed hide's `damage_entity`), magic and blasts never fire
  the causer's attack effects nor its outgoing multipliers or pack bonus (`TraitEvents.blow`: not
  `avoids_guardian_thorns`), and a trait's beam strikes with no direct creature, so the data's "direct hit" guard keeps
  on-hit traits out of it. `attackTriggerOnlyOnBlows`.
- **Brass is brass** (spec 6.6): its own traits' Regeneration and Instant Health never go on it (`MobEffectEffect`), nor do
  its flesh parts' weaknesses reach it (`ExposureEffect`: sun, water, heat). `brassNeverSelfHeals` now checks both.
- **A minion's blast spares its side** (its maker and the maker's other minions), as its shoves and shots do.
  `minionBlastSparesItsSide`.
- **Webs.** A creature's web honours mobGriefing (vanilla's Weaving), and a minion's goes over grass, snow and the like
  (which the web takes with it when it tears) only where `minion_block_damage` allows it too; into air it goes where mobs
  may grief. `minionBlockDamageCoversShotsAndWebs` now spins webs under each setting, checks a landing shot's gate, and
  that Create moves the web.
- **Milking is the maker's.** Only its maker fills a bucket, bowl or bottle from a producing minion. `milkByHandAndHump`.
- **lava_walk's fire immunity** holds only on or in lava (a `location_check` naming the lava fluids; immunity entries now
  read their condition), so a strider-legged minion burns off the lava. `minionLavaWalkStandsOnLava`.
- **dry_for** counts a creature first seen dry as long dry, so water_hurts no longer stings on login.
  `dryForCondition`.
- **Signatures written with the built types** (spec 8.2), their waits notes gone: the shulker's lid deflects 30% from
  the front (shell_lid); the phantom's gland is swift only at night (night_swift); the Elder Eye in armour gives melee
  attackers Mining Fatigue II for 6 s (elder_curse); the hoglin helmet tosses 15% of the time (toss); evoker shoulders
  call fangs on 10% of hits, every 10 s at most (fang_strike); the Golem Core mends a minion standing still (still_mend)
  and a zombie torso mends in the dark (dark_mend); the zombie villager's Curable Heart keeps Weakness off and makes a
  golden apple Absorption II (curable, its own file).
- **Hot paths make nothing each tick**: `ActiveTraits.find` and the activate facets are worked out once per built traits
  and kept; the flag and drain loops walk those lists by index; the ranged goal looks at its shots again only when the
  traits change; the sense goal asks for senses before jobs, with no stream; slot counts are read once per loop.

### 15.12 Slice 7 as built: heads and jobs (brief § Minions: "the head sets behaviour")

- **Job options from the head** (`MinionStats`, `MinionData`): a head's "jobs" are read for the very piece fitted. A
  part's minion object may hold "variants", matched against the piece's captured traits (`{"if": {"trait":
  "profession", "equals": "farmer"}, "jobs": [...]}`, or `"in": [...]`, or neither for any value; the last match wins,
  and a later layer, a mob's own file, still comes before the variants of the layers under it, spec 4.2's order). A head
  offers only jobs that are built (`MinionStats.JOBS`), less those needing a hand it lacks (`HANDS`: farmer, surgeon,
  butcher, medic, which need an arm of hand grip; a paw or claw does for the farmer) or eyes (below). Of those, a minion is offered what it can do now (`MinionJobs.offered`): a sentry
  needs a ranged attack (`MinionEntity.hasRangedAttack`: a bow, crossbow or trident in a hand that fights, for the
  innate shots to extend), a fisher a rod in hand or a head whose data says `"no_tool"` (the fish archetype: a fish
  fishes with its mouth), a butcher a Cleaver or Flensing Knife; with none, it keeps company. It wakes as the first but
  hunting (`MinionJobs.wakeJob`: it would go straight for the animals round the table it was made at); its
  maker's crouching empty-hand click cycles them, naming the new one on the action bar. A job it can no longer do (its
  bow taken back or broken) gives way to the first it can, checked each second. A sentry takes its post where it
  stands. `cycleJobWithEmptyHand`, `pillagerHeadOffersSurgeon`.
- **Villager heads by profession**: `CarcassLook.traits` now keeps a villager's (or zombie villager's) profession on the
  carcass, and so on its pieces. The villager family's head carries the variants: surgeon, then farmer for a farmer,
  fisher for a fisherman, butcher, medic for a cleric, herder for a shepherd, sentry for a fletcher, hauler for a
  leatherworker, guard for an armorer, weaponsmith or toolsmith, courier for any other; a nitwit keeps company only. The
  villager's own file no longer names jobs, so the family's apply (a head from before professions were kept: surgeon,
  farmer, courier). Pillager heads: surgeon and sentry. Heads section 8.2 names, in mob files of their own: piglin
  (barterer, guard, courier), sniffer (digger, hauler), witch (medic, surgeon, courier). `villagerHeadOffersSurgeon`,
  `nitwitOffersCompanionOnly`.
- **Blind heads**: a head piece with both eyes taken out at the Surgical Rig (`Surgery.ORGANS_TAKEN` 2 on a head bone)
  loses farmer, sentry, surgeon, hunter and fisher, and its sight (`MinionStats.sight`: the head mob's FOLLOW_RANGE, 8
  with no head) is 4; a "senses" list naming echolocate or tremor would keep them (no head has one yet). Sight bounds
  what it notices, not how far it paths: the FOLLOW_RANGE attribute stays 48, which the path budget (FOLLOW_RANGE × 16)
  needs for troughs; the guard's, sentry's and hunter's target searches and the scavenger's, herder's and medic's
  looking round are capped by it. `blindHeadLosesSightJobs`.
- **Holding things**: its maker uses an item on it (not a saddle, lead, name tag or egg, which do their own thing) and it
  takes one into its hand (a head's mouth will do; what it held comes back); an empty hand takes it back (saddled, an
  empty hand climbs on instead). Arrows for the bow or crossbow it holds, and healing potions for a medic's head, go in
  with what it carries, the whole stack, its hand kept ("Carrying ..."). What it holds
  drops with what it carries if it dies, comes back when it is taken apart on the table, and stays with it folded. A
  minion keeps hold of the player who woke it while that player is about, so a test's stand-in maker counts too.
- **The jobs** (`MinionJobs`, a package of goals each, added in `registerGoals` by one line; none breaks or places a
  block):
  - sentry: takes a monster it can see within 16 for its target, turns and shoots with the bow (drawn for a second, as
    `RangedBowAttackGoal` does), the crossbow (charged and loosed as `RangedCrossbowAttackGoal` does; loaded through
    `getProjectile`, which hands vanilla the arrows from its inventory) or the trident (a copy thrown as the drowned
    throws one, the held one wearing). It never leaves its post nor closes to melee; its arrows, the crossbow's too, can
    be picked up where they land, and they pass through its own side. `sentryShootsWithHeldBow`,
    `crossbowSentryArrowsCanBePickedUp`, `sentryArrowsSpareItsSide`.
  - scavenger: as an allay, items like the one it holds (the same item, the same potion) within 32 and its sight, taken
    up and brought to its maker's hands while its maker is within 32 of home. `scavengerFetchesMatchingItem`.
  - herder: the animals that would follow what it holds (`Animal#isFood`) found within 20 of home and more than 8 from
    it: it walks out to the stray and leads it home, the stray pathing after it and waited for; never a tamed or named
    animal. Holding nothing, it herds nothing. `herderKeepsAnimalsHome` (the cow is kept from wandering home by itself).
  - fisher: by still, open water within 8 of home, `minecraft:gameplay/fishing` every 30 to 60 s it spends there (less
    with Lure; luck from the rod; no treasure, which needs a bobber in open water), into what it carries; a rod wears a
    point a catch. The wait counts only time at the water and is kept with the minion. `fisherFishesByWater`.
  - hunter: grown prey near home (within 12) that it can see, never a named, tamed, leashed or ridden animal, for the
    whole chase; the prey is the head's "prey" list (ids and #tags of any passive mob, fish too) or
    `#bloodandbones:hunter_prey` (livestock and game). It hunts only where the mobGriefing rule lets mobs do harm (spec
    10.13). Its blows kill; with a
    Meat Hook in hand, `CarcassEvents.onDeath` (which reads the killer's hand) leaves an intact carcass, exactly a
    player's hook kill. `hunterWithMeatHookLeavesCarcass`.
  - butcher: carcasses within 6 of home, a blow every three quarters of a second through `CarcassButchery.cut` or
    `skin` with no player, so the yields are hand yields, caught into what it carries (`CarcassButchery.capturing`, as a
    machine's are): with a Cleaver the loose pieces first, then a limb at the end of a chain; with a Flensing Knife the
    hide. The blade comes away bloody. `butcherButchersWithCleaver`.
  - medic: a splash (or lingering) potion of healing or regeneration from what it carries, thrown as a witch throws at
    the worst-hurt ally it can see (two hearts down or more): its maker, its maker's other flesh minions, villagers; one throw,
    then three seconds. `medicThrowsHealingAtHurtMaker` (the stand-in maker is not in the world, so the test sees the
    throw aimed at the maker, and the healing land on a hurt villager).
  - barterer: a gold ingot from the nearest container within 6 of home, looked over for six seconds (in what it carries
    meanwhile), `minecraft:gameplay/piglin_bartering` rolled and put back into that container, only while it has a slot
    free. `bartererTradesGold`.
  - digger: sniffs a random `#minecraft:sniffer_diggable_block` with room over it within 6 of home, and every one to two
    minutes it spends there turns up `minecraft:gameplay/sniffer_digging`, into what it carries; the ground is left as
    it was. `diggerDigsUp`.
  - Fishers, butchers, diggers and barterers store what they have in the container nearest home, as couriers do; every
    job goes home when idle.
- **Hauling with the player's drag**: `CarcassDrag` now takes any living dragger. A hauler walks to a whole carcass
  within 24 of home that nothing holds, hooks its torso from arm's length (`CarcassDrag.start`: the same spring pull,
  drips and trail, and the same slowdown by the carcass's weight, eased by its `drag_strength`, which minions now have,
  so a horse torso's hauler trait counts), and walks a straight line through the nearest free Shackle Hook or Bleeding
  Rack, the body towed behind. A body that comes under the hook is hung by its torso through
  `ShackleHookBlockEntity.hang`, the Meat Hook click's own code (a broad hauler that cannot stand close enough hangs it
  from a player's reach, the hook's joint lifting it the rest of the way); one towed over the middle of a rack is let
  down there and bleeds into it. Only the pull point differs from a player's: held at arm's length back along the line to
  the body rather than in front of the feet, so the body trails wherever the hauler walks. A drag that is not a
  player's is kept by the level tick and let go when its hauler is unloaded, killed or powered down. In the tests it tows
  the body three to five blocks and hangs or lays it; in a cramped place it gives up after five seconds stuck and tries
  again half a minute later. The tow is jank (the body swings round behind it), not buggy.
  `haulerDragsCarcassToHook`, `haulerLaysCarcassOnRack`.
- **Tests changed**: the design's cow on rabbit legs is a herder (6.5), no longer a courier; the courier test and the
  brass test's pig-headed minion are put to courier (a pig's head hunts, and would hunt the other tests' animals).
- Found on the way: a job goal ticked every tick is asked whether to go on only every other tick, so a medic threw two
  potions at once; each job goal now stops acting the moment its work is done. Game tests stand things at y 2 (the
  template's floor is at y 1).
- **Left out**: the sapper waits for the detonate effect (the Motion group's). Held items are not drawn on the minion
  yet. The brass filter slot (slice 8) will narrow what a hunter, herder or scavenger takes. Variants patch only a
  part's minion data (armour scraps keep no traits). The zombie villager's slow surgeon, the fox thief, the panda's
  genes, the llama's caravan and the trader llama's guard (8.2) are signature work for slice 9.

### 15.13 Slice 8 finished: brass (brief § Minions: "some sort of charging station"; spec 6.6 to 6.8)

Most of slice 8 was built with the minion rebuild (15.6): Brass Sheathing, skinned-only fitting, the Soul Canister and
its filling and emptying recipes, the Charging Cradle (kinetic, item handler, contraption-safe, sheet repair), the module
socket, and brass repair by hand. This finishes it.

- **The filter slot** (`minion/MinionFilter`, brass only: spec 6.6 puts it among what only brass has, beside the module;
  flesh has its hides, its mending and its organs' produce). Its maker crouches and uses an item on the minion, as on one
  of Create's filter slots (`FilteringBehaviour#onShortInteract`): a Filter or Attribute Filter goes in itself, the one
  there before coming back; any other item puts a copy there and stays in hand. A crouching Wrench takes it out (so the
  module now comes out with a standing Wrench only); with a Filter, a flesh minion's maker is told only brass takes one.
  The status line (a plain click) names the filter. It is saved with the minion, stays with it folded, comes back when it
  is taken apart on the table, and drops with it where minions may die (a plain item's copy is not an item to give back).
  What passes is Create's own test (`FilterItemStack#test`), so a Filter's list, allow or deny, and an Attribute Filter's
  attributes work as on a funnel. A mob is asked about as its spawn egg, as the carcass machines' filters ask: an egg or
  a carcass piece names that mob (alone or in a Filter's list), an Attribute Filter is asked about the mob's egg, and a
  mob with no egg passes only a deny list. Who obeys it:
  - the courier and farmer pick up only what it passes (`MinionGoals.Collect`), and the farmer reaps only crops whose
    seed or a drop passes;
  - the scavenger fetches what is like what it holds and passes; with nothing in hand, whatever passes;
  - the herder herds, and the hunter hunts, only the animals it passes (still only those its held food leads, and its
    head's prey);
  - the guard and sentry take only monsters it passes for targets (defending itself or its maker is not filtered).
  `filterSlotWorksAsCreates` (flesh refused, the swap and the copy, a list, a deny list, an Attribute Filter, eggs alone,
  in a list and asked by attribute, saved and loaded, the Wrench), `filteredCourierOnlyMovesIron` (the cobblestone lies
  nearer than the iron, so a courier that ignored the filter would take it first), `filteredHunterSparesTheCow` (a pig's
  egg in the list; the cow stands nearer), `filteredScavengerFetchesWhatItPasses` (an empty hand and an Attribute Filter
  for food: the apples come, the nearer stick stays).
- **The cradle as a Mechanical Arm point** (`minion/CradleArmPoint`): a type in Create's
  `arm_interaction_point_type` registry (`CreateRegistries.ARM_INTERACTION_POINT_TYPE`, through our own
  DeferredRegister, as Create's own are in `AllArmInteractionPointTypes`), reaching for the cradle's top (10 pixels up).
  The arm works through the cradle's item handler, the one funnels and hoppers use: set to put things there, it puts full
  canisters and brass sheets in; set to take from it, it takes the empties out; nothing else either way. `armLoadsCradle`:
  one arm takes a full canister off a Depot and puts it in the cradle, the cradle swaps it into a brass minion powered down
  beside it, and a second arm takes the empty out onto another Depot. `cradleSwapsFromHopper`: a hopper beside the
  cradle feeds it two full canisters; one wakes the minion, one waits, the empty stays. A hopper cannot take the empties
  from under the cradle, where its shaft is: a funnel on its side or an arm does.
- **Brass repair by Deployer**: the Deployer uses its item on an entity through `entity.interact` with its stand-in
  player (`DeployerHandler.activateInner`), so a Deployer holding brass sheets over a hurt brass minion mends it 10 a
  sheet through the hand's own path, and keeps its sheets once the minion is whole. The stand-in carries the UUID of the
  player who placed the Deployer (`DeployerFakePlayer#getUUID`), so it passed for the minion's maker: a Deployer holding
  a Cleaver could have taken a powered-down minion apart on the table, and one holding a Wrench taken its module out. Now
  any `FakePlayer` only charges (a canister), feeds (a blood bucket) and mends (a sheet); everything else to do with the
  minion (taking apart, folding, riding, module, filter, job, what it holds) passes it by. Nor does a stand-in's damage
  (a Deployer's punch) ever hurt a minion, awake or down. `deployerRepairsBrass` (a real Deployer: two sheets mend 12, the
  third stays in hand; then set to punch with a diamond sword, it leaves the minion whole), `deployerNeverTakesMinionApart`
  (a stand-in placed by the maker: module, Wrench, filter, empty hand, blows awake and down, Cleaver and fold on the
  table all do nothing; the maker's own Cleaver still takes it apart, the Filter coming back).
- **Tests of section 9** now all present but one: `spoutFillsCanister` (now also a real Spout over a Depot filling an
  empty canister from a bucket's worth), `cradleSwapsFromHopper`, `cradleRevivesPoweredDown`,
  `filteredCourierOnlyMovesIron`, `deployerRepairsBrass`, `cyberImmuneToPoison` (brass takes no poison, wither or hunger
  and does not drown out of air under water; flesh is poisoned and drowns). `produceInertOnCyber` waits for the produce
  effect (the Upkeep group's, not on this branch); spec 6.4's rule that produce does nothing on brass goes in with it.
- Found on the way: NeoForge's breathing hook (`CommonHooks.onLivingBreathe`) leaves a mob that cannot drown with the air
  it had (none refilled) but never hurts it, so the test looks at health, not air. A Mechanical Arm's base is a small cog
  with no shaft: it turns from a cogwheel beside it, not a motor under it.
- Left out: no screen for the filter slot (Create's slots have none; the crouching click is theirs). The filter is not
  drawn on the minion, nor sent to clients beyond the status line.
- The suite is 337 tests (328 before, and nine new; `spoutFillsCanister` was already there).

### 15.14 Review of the jobs (fixed)

- Stocking: a player had no way to give a sentry its arrows or a medic its potions (a use put one item in its hand and
  gave back the bow; the tests filled the inventory directly), so only the trident sentry worked. Arrows for the weapon
  it holds (`ProjectileWeaponItem#getAllSupportedProjectiles`) and healing potions for a head that offers medic now go in
  with what it carries, the whole stack. `sentryShootsWithHeldBow` and `medicThrowsHealingAtHurtMaker` stock them by
  hand now.
- Grips: any arm counted as a hand. `MinionStats.Strike` now carries the arm's grip from its data (an arm naming none has
  a hand): surgeon, butcher, medic and held weapons need an arm of hand grip, the farmer a hand, paw or claw (spec 5.6).
  `wingsAreNoHands`.
- One head for everything: `MinionStats` took the last head fitted, the jobs' own data (`no_tool`, `prey`) the first.
  Both use `MinionStats.head` now, the first, as spec 6.4 says.
- The barterer held the ingot it looked over in a goal field, which a save, a fold, a take-apart or a death lost. It is
  in what it carries now, and it trades only with a slot free, so a full chest's gold is no longer turned into litter at
  its feet. `bartererTradesGold` sees the ingot in what it carries.
- The hauler could hang a body from a hook on the storey above, through the floor, by the joint. It picks hooks and
  racks on the body's own storey, and hangs a body only from a tip within 4 above it with nothing solid between, as a
  player could. It lets go when someone else takes hold of the body (a player's Meat Hook, another hauler), and checks
  first that nobody has; a butcher stops cutting a body being dragged. The hook's chunk is never loaded to look at
  (`isLoaded` first, the review's rule), and a pick tests the distance from home before the costly rack tests, working
  out once which racks are taken.
- The hunter kept chasing prey leashed, named, ridden or filtered out meanwhile, or run off from its 12 blocks; its rules
  hold for the whole chase now. It searched only `Animal`, so a "prey" list of fish (the axolotl's tag) matched nothing;
  it searches any passive `PathfinderMob`. It obeys mobGriefing (spec 10.13), and a minion wakes to another of its jobs, so a
  spider or pig head woken at the table does not go straight for the animals kept by it (`hunterWithMeatHookLeavesCarcass`
  and `filteredHunterSparesTheCow` put their wolf heads to hunting by hand). The digger changes no block
  (as the sniffer's own digging, which vanilla does not gate), so it is left as it was.
- The herder led tamed and named animals (a saddled horse from its stable, a sitting wolf) home; it leaves someone's own
  animals be now, as the hunter does.
- The sentry's arrows and tridents hit its maker, its maker's other minions and villagers in the line of fire; they
  pass through its side now (`ProjectileImpactEvent`, cancelled for a minion's arrow hitting its side). A crossbow's arrows could never be picked up (vanilla allows that only for a
  player's); the arrows its crossbow looses are marked as its bow's are.
- The scavenger followed its maker any distance in the same dimension; it brings what it found only while its maker is
  within 32 of home, and keeps it until they are back.
- A saddled minion holding something could not be mounted with an empty hand (the hand took the item back first); an
  empty hand climbs on now. `horseLegsAcceptRider` gives it wheat first.
- A medic no longer heals brass minions: brass is mended by brass sheets, by hand or cradle (spec 6.6).
- `butcherButchersWithCleaver` checks that what came off is in the butcher's hands, not only off the ground.
- The suite is 340 tests.

### 15.15 Putting the effects and the jobs together (fixed)

The effects and the jobs were built apart; together, four things broke that neither did alone.
- **A lava walker hung the server.** The empty fluid over lava counts as lava to stand on (Sable's collisions ask with
  it, 15.8), and a path's start (`WalkNodeEvaluator.getStart`) climbs up through everything the mob can stand on: a lava
  walker standing on lava that set off anywhere climbed the air forever. It only showed once the jobs let a minion find a
  test's stand-in maker and follow it. While a minion works out a path (`MinionEntity.pathing`, around every walking
  navigation's `createPath`), only the lava itself counts. `lavaWalkerFindsAPathOnLava`.
- **A body lying beside a Bleeding Rack bled or not by a rounding.** Its lowest point sits right on the floor's top, and
  whether that fell in the floor block or the one above decided whether the rack beside it was found. It counts in the
  block above now (`CarcassBleeding.rackBelow`). The hauler also lets a body down on a rack gently (every piece stilled,
  not slid on at walking pace), only where the bleeding finds that rack, and after it settles checks it is still in the
  tray, taking another pass (up to three) if not. `bodyOnTheFloorBesideARackFindsIt`; the hauler's rack test passed 96
  runs in a row, where it had failed about one run in three.
- **Zombie-framed workers burned to a collapse.** The rotting family's torso is sun-cursed (spec 8.1: "it burns by day
  unless it wears a helmet, and at worst collapses"), but nothing let a minion wear one. The maker now puts a helmet (or a
  pumpkin) on a minion with a head by clicking it, and takes it off with an empty hand when it holds nothing; a minion's
  helmet takes the sun for it and wears as a zombie's does. A player's helmet still does not (the set's own is part of
  the curse). It drops, folds and comes back from the table as a held item does. `sunCursedMinionShadedByHelmet`; the
  jobs tests' zombie-framed minions wear leather helmets, as a player would give them.
- **Tests' stand-in makers stood at the world's origin**, which minions now walk off to follow. The effect tests stand
  them beside their minions.
- **Not fixed yet:** a lava walker stands on lava but cannot walk across it; its path treats lava as a wall and it wades
  in when it moves. It needs a strider's navigation (lava as a stable, walkable node), which is on the next minion list.

### 15.16 Organs as items (slice 3's organ half; spec 4.9, 5.7, 7.1 and 7.3)

The brief's Armor section: "An organ adds one special ability. Every mob has at least one; some have several." Every
mob's data already named its organs and what they do (15.9), but only the heart, lungs, stomach and eyes had items, so
in survival nobody could cut a rumen or a powder sac out and fit it.

- **Organ files** (`parts/OrganKind`, `data/<ns>/organ/<id>.json`, spec 4.9: item, name, bloodless_name, look, tint,
  armour_pieces, extra_drops), loaded by `PartsData` as a sixth kind and sent to clients with the others. One for each
  of the 77 organs the groups and mob files name (`everyOrganHasAFile` lists any that has none). The generic heart,
  lungs, stomach and eye point at their own items, so self-surgery and the fittings made before work as they did; the
  rabbit's foot, ink sac and glow sac are the vanilla items; the other 70 are the Gland. An organ with no file (a
  datapack's) comes out as a plain Gland that fits a chestplate.
- **Organ lists** (`MobGroup.organs`, `ResolvedMob.organLists`): the "organs" of each layer are now read, by part key,
  layered as trait lists are (a plain list adds, and organs may come twice: two eyes; `remove` takes every one of that
  organ; `replace` starts again), so the bloodless overlay's removals and the skeletal overlay's marrow apply.
- **The Gland** (`parts/GlandItem`, `bloodandbones:gland`): one item carrying the organ's id (`bloodandbones:organ`, a new
  component) and the `source` stamp (mob, the part it came out of, baby), stacking to 16 with its like. Its name comes from
  the organ file ("Creeper's Powder Sac"; in bloodless mode its `bloodless_name`, "Creeper's Powder Core"). Every organ
  item, the heart and a plain rabbit's foot too, says on its tooltip whose it is, which armour pieces take it and what it
  gives there, and what it gives a minion, from its mob's `organ_traits` (what each does while Ctrl is held); a carcass
  piece says which organs are still in it (`Organs.onTooltip`). Its look is one of eleven shapes by the file's "look"
  (gland, sac, bulb, core, bladder, spinneret, fat, marrow, gut, heart, eye), grey flesh tinted with the file's colour
  through an item colour handler, with a wet overlay on top (a shine, dark veins, a torn vessel, blood running off the
  bottom); in bloodless mode the same outline as a machined steel core, riveted, the colour glowing through a window
  (`client/GlandClient`, item properties `look` and `bloodless`). The creative tab lists one of each organ, as the first
  mob holding it gives it. Placeholder art, drawn by a script, checked on screen in both modes.
- **Whose an unstamped item is** (`Organs.SOURCES`, `data_maps/item/organ_sources.json`, as `hide_sources`): a rabbit's
  foot is a rabbit's rabbit_foot, an ink sac a squid's ink_sac, a glow ink sac a glow squid's glow_sac, a spider eye a
  spider's eye. An organ the map already gives to its own mob comes out plain, so it stacks with the ones mobs drop; one
  cut out of another mob is stamped (a glow squid's ink sac). `organSourcesInkSacIsSquid`.
- **Harvesting** (`Surgery.harvest`, the Surgical Rig): the Cleaver takes a piece's organs out in its lists' order, one a
  cut (`Organs.held`: its slot's list, then its form's and sub-slot's, "leg.hind" for a rabbit's haunch; a body that is
  its own head, a blaze or a slime, holds its head's organs too; a neck or torso extension holds none). Each comes out as
  its file says, stamped, with its extra drops (a powder sac 1 to 2 gunpowder, a blaze core blaze powder, marrow bone
  meal, a golem core iron nuggets...). A mob with no blood is no longer refused: it gives what it has (a skeleton its
  marrow, a creeper its sac), dry, the blade left clean, a puff of bone dust and a crack instead of the squelch and spray.
  The piece counts what was taken (`organs_taken`, as before, so a blind head is still two eyes out).
  `cleaverTakesTorsoOrgansInOrder` (a cow's torso: heart, lungs, stomach, rumen; a creeper's: its sac and gunpowder; a
  rabbit's hind leg its foot, its front leg nothing), `skeletonGivesMarrow` (`PatientTests.organsFromACarcass` now expects
  the rumen and the marrow too).
- **From a heavy carcass** (spec 6.2: "the same work-zone rule lets the Surgical Rig take organs out of a heavy carcass
  lying over it"): with nothing laid on the table and nobody on it, a Cleaver used on it goes into the nearest carcass
  lying in the Assembly Frame's work zone (`Surgery.carcassOn`, 0.75 past each edge, 2.5 up, not one being dragged)
  instead of being laid down: its torso's organs first, then each bone still attached, each bone counting its own
  (`organs_taken:<bone>` on the carcass, which a piece cut off it reads, and a piece put down again turns its own count
  into its bone's). `heavyCarcassOrgansOnRig` (a whole cow: its four, then its head's two eyes, the Cleaver kept in hand).
- **Fitting** (`CarcassArmourFittingRecipe`, `MinionAssembly.fitOrgan` unchanged): `Organs.of` reads any stack as an
  organ (a stamped Gland, a stamped heart, a mapped vanilla item) and `Organs.stack` makes one again, so the old one comes
  back exactly as it went in, in armour and on the Assembly Frame. Which pieces take it is the file's `armour_pieces`,
  no longer a map in code: eyes and head organs a helmet, hearts and most cores a chestplate, guts and fats a chestplate or
  leggings, a rabbit's foot or a leap gland leggings or boots, a lanolin gland any piece. Its traits then work where
  15.8 built them: the chestplate's Organ Ability key, a minion's organ slot. `glandFitsOnlyAllowedPieces`,
  `creeperSacChestplateEndToEnd` (cut out with a Cleaver click, fitted in a grid, worn with a tank of blood, the key
  pressed below half health: the blast spares the wearer, hurts the zombie beside, costs 50 mB), `glandInMinionGivesTraits`.
- **JEI** (`compat/jei/BodyPartsCategory`, `BBJeiPlugin`): a Body Parts page per rigged mob ("Parts and Cores" in
  bloodless mode) beside its Butchery page: its spawn egg, the organ items the Surgical Rig cuts out of it (so JEI finds
  the page from a Gland), its hide items, and a scrolling list of what each part gives a minion and armour (per piece
  where the data says), its hide, each organ (which piece holds it, which armour pieces take it, what it gives in each),
  and its full set. Each organ fitting is shown as a crafting recipe of its own (a piece its file allows and the organ, as
  the first mob holding it gives it, make the piece with it fitted), and the Gland has an information page. Glands are one
  JEI entry per organ (a subtype by organ id). Checked on screen in both modes.
- **Commands** (`parts/TraitsCommand`, spec 4.11): `/bloodandbones traits explain <mob> [baby]` lists its layers (with
  their kinds), its scrap material, each part key's minion and armour traits, its hide, the organs each part holds, what
  each organ does (on a minion, and in which pieces of armour) and its full set. `/bloodandbones traits dump` (operators)
  writes `bloodandbones-traits.csv` in the server's folder: one row per trait of every rigged mob, with its archetype,
  family, overlays, material, facet (a part key, the hide, an organ, a set) and host (minion, armour, a piece, a set's
  bonus or drawback), and a row per organ in each part's list, about 2500 rows. `explainCommandRuns`.
- **Words**: the organ names were already in `BBLang` with bloodless machine-part names (15.9); now the files point at
  them. New wording for the Gland (a Core in bloodless mode), its tooltip, the Body Parts page, the commands, the Surgery
  Table's and every armour piece's description (every piece now names organs that fit it), each with bloodless wording.
  `bloodlessOrganNames` (every organ's bloodless name free of flesh words, the new words, and a clean model and textures
  for every look).
- **Shared files touched, and why**: `body/Surgery` (harvest by organ lists, the heavy carcass, the counts),
  `body/SurgeryTableBlock` (a Cleaver on an empty table over a carcass), `item/CarcassPieceItem` (one line: a piece put
  down keeps its organ count as its bone's), `parts/CarcassArmourFittingRecipe` (organs by file), `parts/CarcassArmourItem`
  (the organ's name from its file), `parts/MobGroup`, `ResolvedMob` and `PartsData` (organ lists and files),
  `registry/BBItems`, `BBDataComponents` and `BBLang`, `BloodAndBones` (the data map, the tooltip handler, the command),
  `compat/jei/BBJeiPlugin`, `client/DevShowcase` (the Gland in the hotbar, and the creeper's Body Parts and fitting pages
  photographed), `gametest/PatientTests` (the rumen and the marrow). No file in `minion/` changed: the Assembly Frame's
  organ slot takes a Gland through `CarcassArmourFittingRecipe.organ`, which it already called.
- **Found on the way, not fixed here (the minion jobs are another branch's; both fixed since, 15.17 and 15.17a)**: two job tests fail now and then on the
  starting commit as well, shown by running many copies of each at once there (`haulerLaysCarcassOnRack` once in 50 to
  100 copies, `hunterWithMeatHookLeavesCarcass` twice in 50); one or the other failed in 2 of the 19 full runs made for
  this slice. The hauler lets a body down when its torso is within 0.8 of the tray's middle (`Haul.ON_TRAY`), which is
  past the tray's rim (a rack is a block with 8-pixel rims): let down over the rim, the body tips off onto the floor
  beside the rack, out of the reach the bleeding looks in; its next passes start from right beside the rack, the tow line
  too short to bring the torso inside the rims, and it lets it down there again (its lowest point, still by the rim,
  finds the rack through the tray's catch round it), until after three it gives up. The hunter closes to about two
  blocks of a cow standing still, its navigation done, and never strikes: vanilla's melee goal paths to the target again
  only if the target has moved a block (or one tick in twenty), and each path ends where it stands, out of reach.
- **Left out**: the sounds do not change to clanks in bloodless mode (nothing in the mod swaps sounds by that setting
  yet). A Deployer holding a Cleaver over a heavy carcass should work as it does over a piece (it clicks as a player
  does), but is not tested. Organ lists name which part holds an organ, not which bone of it, so both of a rabbit's
  haunches give a foot. The explain command lists every organ its data gives traits to, the ones a live mob gives up on
  the table (a creeper's heart) as well as those its carcass holds. The creative tab and JEI take the organ files the
  client had when they were built; a `/reload` that adds organ files shows in JEI (which restarts) but not in the
  creative tab until the next world. The spec's powder sac bloodless name is "Charge Cell"; the data's is "Powder Core"
  (Motion's wording, kept).
- The suite is 428 tests (417 before, and eleven new in `OrganTests`).

### 15.17 The minion leftovers: the sapper, held things drawn, lava, wings, the seabed, mounts and variants (brief § Minions)

What 15.12 left out, 15.15 had not fixed yet and the signature lint was waiting for, where the built jobs and movement
now reach.

- **The sapper** (`minion/MinionSapper`, spec 6.9): a head that offers it (the volatile family's, so a creeper's: sapper
  or guard, spec 8.1) and an organ whose minion traits hold an activate detonate effect (`MinionSapper.hasDetonator`: the
  creeper's Powder Sac, whose Self-Destruct is Motion's detonate) make a minion that walks up to its target and blows
  itself up there through its organ (`Activation.fire`, as its UseOrgan goal fires it, paying from its own blood), holding
  still while the fuse hisses; the detonate effect then powers it down where it stands, whole, never destroyed, and blocks
  break only where the effect may break them, `minion_block_damage` and mobGriefing all allow it. Its targets are a
  guard's (monsters near home it can see) and whatever hurts it; it never bites. Its mark is a banner: handed a banner, it
  goes for the nearest banner of that colour standing within 16 of home (block entities of loaded chunks only), as a sapper
  goes for the flag its side planted; the maker takes the banner back to stop it. It never wakes a sapper
  (`MinionJobs.wakeJob`, as with hunting: it would spend its blast on the first monster to wander by); its maker puts it
  to sapping with a click. Only a sapper sets its organ off: the organ goal leaves detonations out (15.17b). `sapperDetonatesAndPowersDown` (offered only with the sac in; it walks to a husk, blows, hurts
  it, lies down unhurt, the floor whole), `sapperGoesForItsBanner` (the red banner, not the blue; both still stand).
- **Held things and helmets drawn** (spec 6.10; `MinionBody.anchors`, the same layout both sides use, and
  `StitchedBody.Attach`, a hook that draws on a piece in its own frame as it is turned that frame): what a minion holds is at
  the far end of its first arm of hand grip, at the middle across and the front, which for a humanoid arm is exactly
  vanilla's hand point (1 pixel in, 10 down, 2 forward), turned and drawn as `ItemInHandLayer` does (a right or left hand's
  item by the side the arm is on); a pair of folded arms holds it in front of them, upright, as `CrossedArmsItemLayer`
  does; a head with no hand holds it across its mouth, as `FoxHeldItemLayer` does. What it wears on its head sits over the
  head it has (`MinionStats.head`): a helmet is the humanoid helmet (the outer armour model's head and hat) stretched to the
  head's box, a cow's long skull getting a long helmet, in its material's layers, tinted and glinting through NeoForge's
  armour hooks (so dyed leather and carcass helmets draw right); a skull or mob head is the skull model sitting on the
  head's bottom, 1.1875 times it, as `CustomHeadLayer` draws one (15.17b); anything else worn (a carved pumpkin) is the
  item's own head look sized to the head, as `CustomHeadLayer` sizes it to a humanoid's. Wings beat in the air, a flier
  bobs (a floater slower and deeper), and a mood tail (a wolf's, `"mood": true`) is held up by its health and hangs as it is
  hurt. `heldItemAnchors`. On screen: the showcase's zombie with a cow's head holds a bow, an iron helmet stretched over
  the cow's skull, a pig's head wears a carved pumpkin, and a cow hangs in the air on a phantom's wings.
- **Walking across lava** (15.15's "not fixed yet"): a lava walker's navigation is a strider's
  (`MinionMoves.LavaNavigation`: lava a stable destination and no bar to a path) and, while it has lava_walk (from at
  least half its legs, 15.17b), its path cost for lava is 0 (vanilla's own is put back when it loses it; fire keeps
  vanilla's costs, 15.17b), so it goes straight over a pool rather than round it or nowhere; the path is still worked out inside `MinionEntity.pathing`. It is never set
  alight nor burnt by the lava it walks in (`lavaHurt`, through a synced flag so its client does not show it burning), so
  it steps off the far side unhurt, and one that walks in from a bank lower than the surface steps up onto the lava as onto
  a slab rather than wading (`MotionFlags.floatOnLava`). `lavaWalkerCrossesLava` (a cow on strider legs follows its maker
  over a wall-to-wall pool: never below the surface, never alight, never hurt).
- **Wing lift** (spec 6.4): each arm's `"lift"` adds up (phantom wings 0.4, chicken 0.02, parrot 0.06); at least the
  torso's volume flies it (mode fly, at least a bat's 0.2, flying navigation, no gravity, twice the blood moving), less only
  slows its fall as a chicken's wings do (`MinionStats.slowFalls`: its fall held to six tenths, and no fall damage).
  `phantomWingsLiftCow`, `chickenWingsDoNot` (it falls far slower than a wingless cow and lands unhurt).
- **Sink and float** (spec 5.6): sink legs (drowned, iron golem, skeleton and zombie horse) walk the bottom of water: the
  float goal leaves it be, its `water_movement_efficiency` is 1 (so on the bottom it walks at its land speed, vanilla's own
  attribute) and it drops through water rather than drifting; ground navigation paths along the bottom. Only a sinker's
  rider stays on under water (`canBeRiddenUnderFluidType`), the undead steeds' signature; the others throw theirs off as a
  horse does. A sinker short of breath goes up for air (15.17b). Legs whose own mode keeps it up (float, hover, fly: a
  ghast's tentacles float) fly it as a flying torso does.
  `sinkWalksSeabed` (eight blocks along a channel's bottom within six seconds, never more than 0.6 off it),
  `floatStaysUp`.
- **Mounts** (spec 5.4 type 25, as part data rather than a trait: the legs say rideable, the head and legs what steers,
  the torso how many sit): `MinionStats.Mount` (seats, steer, wear). A head's `"steer"` comes first (the pig's
  `{"item": "minecraft:carrot_on_a_stick", "wear": 7}`), else the one at least half its rideable legs share (strider legs'
  warped fungus on a stick, wear 1), else the saddle alone. Steered with a stick, its front rider controls it only while
  holding one, it goes on where they look, and a use spurs it on for a while by vanilla's own `ItemBasedSteering`; vanilla's
  `FoodOnAStickItem` boosts only its own mob type, so `MinionMoves.onUseStick` (`PlayerInteractEvent.RightClickItem`)
  does it for a minion, wearing the stick as vanilla does (`hurtAndConvertOnBreak` back to a fishing rod). The torso's
  `"seats"` (the camel's, the behemoth family's for the ravager) let its maker ride in front and anyone else, with an
  empty hand, behind, spread along the torso's back (`MinionBody.seats`); those behind get off with the maker (15.17b). The rideable torso share is now against the torso
  and its legs only, as spec 6.4's rabbit example means, so a whole ravager (whose great head and neck outweighed it) takes
  a saddle as spec 6.5 says. `pigHeadSteersWithCarrot`, `striderLegsSteerWithFungus`, `camelCarriesTwo`.
- **Variant capture** (spec 9, slice 3): `CarcassLook.traits` now keeps a mob's `variant` (any `VariantHolder`: its
  own name, or its registry id's path for a vanilla one: a snow fox, a warm frog, the killer bunny `evil`), a panda's `gene`
  (the one it shows), a creeper's `charged` and the `name` it was given. Data uses them three ways: a part's minion object's
  variants may now carry `"traits"` (added after that layer's own, `MinionData.traits`; a warm frog's legs are fireproof, a
  weak panda's head sneezes); and a mob file's top-level `"variants"`, each `{"if", "hide", "organ_traits"}`, add hide and
  organ traits (`MobGroup.Variant`, `ResolvedMob.Variant`, `ResolvedMob.hide(traits)`, `organMinion`, `organArmour`: the
  snow fox's hide is insulated, a charged creeper's sac self-destructs at level 2, power 4). A flesh minion's hides are read
  from the first piece of each mob (`MinionEntity.hidePieces`), with its traits; an organ now carries what its carcass
  kept (`CarcassArmour.Organ.traits`, optional in the codec, the old constructor kept), which organ harvesting (15.16) is to
  fill. A head's `"disposition": "berserk"` (the zoglin; the killer bunny, as a bodyguard or guard biting for 8; a
  vindicator named Johnny) goes for any mob it can see but its own side, half again as hard (`MinionStats.berserk`).
  `chargedCreeperSacIsStronger`, `snowFoxHideInsulates`, `carcassKeepsVariants`.
- **Signatures wired** (spec 8.2, in the mob files, the lint's `"variants"` facet new): allay (scavenger head), camel (two
  seats), cod and salmon (fishers with their mouths), creeper (sapper head through its family; the charged sac), fox (a
  thief's head fetches, and its bite now always snatches; the snow fox's hide), frog (warm and cold legs), ghast (floating
  tentacles), iron golem and drowned (sink legs), panda (docile, but aggressive a bodyguard or guard, lazy a sentry, weak
  sneezes the `panda_sneeze` table), parrot (wing lift), pig (carrot steering), pillager (quick-drawing arms: a sentry's
  bow and crossbow are drawn with use-item ticks, so Quick Draw now works on minions too), rabbit (the killer bunny),
  ravager (rideable legs), skeleton and zombie horse (seafloor steeds: sink, rideable, gills), strider (fungus-steered
  legs; its Lava Bladder is now Lava Soak, fire and lava half as harmful only while in lava), vindicator (Johnny), wolf (the
  mood tail), zoglin (berserk), zombie (arms that grab, spec 8.1), zombie villager (a shaky surgeon: its head's `"pace"` of
  0.5 tends a patient every ten seconds, not five). New traits: `sneeze`, `lava_soak`. The lint also reads variants' traits.
  The waiting list went from 48 mobs to 33; what still waits, and why, is logged every run (below).
- **Tests changed**: `armsTakeTurnsInTheirStyles` now expects the zombie's arm to grab (spec 8.1), and checks the hold's
  slowness.
- Found on the way: `haulerLaysCarcassOnRack` failed in two of the six full runs made before the fix (one of them on the
  starting commit): the hauler let a body down the moment its torso came within 0.8 of the tray's middle (so
  always just past the rim, the torso arriving from outside), and called it done 40 ticks later if the bleeding's test
  found the rack then; but the bleeding waits for the body to come to rest (lie still and fold), and a body lying across the
  rim rocked there without resting, or slid off beyond the tray's reach first (the failures left it 1.1 to 1.7 blocks past
  the middle, resting, the rack dry). Logged runs showed both. Now it is let down once well inside the rim (0.35) or where
  it passes nearest the middle (within 0.8), or with its path ended short only with the torso still over the tray's edge
  (1.2); it is steadied (every piece stilled) each second while it settles; and it is done only once the body has come to
  rest where the bleeding finds the rack, else it takes another pass. In the logged runs after, every body came to rest in
  the tray on the first pass, within five to seven seconds. A random blink could land within half a block of where it
  started (a pick that close in the radius: `teleportRandomStaysOnGround` failed once on the starting commit); a pick
  within a block of the start is skipped now. A
  test that posts a stick's use through the whole event bus reaches another mod's handler, which takes a stand-in player
  (not a server player) for a client and loads client classes on the server; the tests call the minion's handler directly.
  A client turns a new mob's body to its head's turn, so the showcase sets both.
- **Shared files touched**: `BloodAndBones` (MinionMoves on the bus, one line), `BBLang` (the sapper's name, two traits'
  words), `ActiveTraits` (a flesh minion's hides with their traits), `CarcassArmour` (the organ's traits),
  `MobGroup`, `ResolvedMob`, `PartsData` (variants), `CarcassLook` (the captures), `MotionFlags` (stepping up onto lava),
  `TeleportEffect` (the blink fix), `RangedEffects` (the thief's words), `MinionJobs` (the sapper's hooks, the hauler's
  lay-down), `DataLintTests`, `MinionBodyTests`, `DevShowcase`, `README`, `CHANGELOG`.
- **Left out, and why**: a held weapon outside a sentry's post (a bow in a skeleton-armed fight, a drowned's thrown trident,
  the axeman's and brute's axe damage: a minion's blows still come from its build, not its held item); a ridden minion's
  jump (`PlayerRideableJumping`, and so Centaur's Jump Boost for your mount); armour trims and other mods' custom armour
  models on a helmet (the vanilla helmet shape is stretched instead); dispositions other than berserk (still only data);
  armour-side variants (a hide or scraps carry no traits of their mob, so a snow fox hide fitted to armour is a plain fox's)
  and organs cut out in play carrying their carcass's traits (organ harvesting's, 15.16, to fill `Organ.traits`: done in 15.17a); the
  playful panda's tumbles and the worried one's flight from thunder; and the other waits the signature lint logs (the sting
  that spends a limb, carrying blocks, the row of fangs, the caravan, trader's guard and homing jobs, the mimic alarm,
  skull-firing heads, rolling, being scooped into a bucket, froglights, the Gold Gizzard's double roll, and the rest), each a
  mechanism of its own rather than wiring.
- The suite is 431 tests (417 before, and fourteen new: `MinionMovementTests`, 8, and `MinionVariantTests`, 6), and passed
  three runs in a row.

### 15.17a Organs and the minion leftovers together, with main's hauler fix (verified)

15.16 and 15.17 were built apart from the same starting point and merged; the session doing it restarted part way, so
the merge was saved unbuilt and untested. It was then gone over, main's own hauler fix merged in, and all of it run.

- **What the merge joined** (both halves' intent kept): a mob file's layers carry both organ lists and variants
  (`MobGroup`, `ResolvedMob`, `PartsData`). An organ cut out now keeps what its carcass kept, the gap 15.17 left for
  15.16: `Surgery.harvest` passes the piece's or the heavy carcass's traits to `Organs.stack`, which keeps only those a
  variant of that organ reads and that match (`ResolvedMob.organTraitsKept`: a charged creeper's sac keeps
  `{charged: true}`; its name, or a plain creeper, keeps nothing), in a new component, `bloodandbones:organ_traits`, set
  only when there is something, so plain sacs still stack. `Organs.of` reads it back, the tooltip and the fitting use the
  variant's traits, and the fitting and the Assembly Frame give it back as it went in (`Organs.stack(store, organ)`).
  `/bloodandbones traits explain` lists each variant ("One whose carcass kept charged = true adds: ..."), and the dump
  gives its rows the facet `variant[charged = true]:organ:...`. `chargedSacKeepsItsCharge` (cut out of a charged torso,
  it blasts at 2 in a chestplate and self-destructs at 2 in a minion, and comes back out of each still charged).
- **Left half-done by the interruption, and cleared**: a test class that ran the hunter's and the hauler's tests 140
  times over (`TmpMergeRepro`), and a debug log in the bite goal. No conflict markers were left, nothing is registered
  twice, and no method is there in both halves' versions. (13.5 is numbered twice, as it already was on main.)
- **Main merged in** (the hauler fix, `-Dbloodandbones.debug.only` and `.repeat` for the test server, docs/NEXT.md): main
  and 15.17 had each fixed the hauler dropping a body off a rack, in the same lines. Both are kept: from main, standing
  still counts as having got there only once it has walked a moment since taking hold, a body let down short keeps its
  pass's line, it is let down once past the middle along the line it is towed, it stands still while the body settles,
  and a body over a rack is supported by any corner (`CarcassRest`); from 15.17, it is let down where it passes nearest
  the middle, steadied every second while it settles, and done only once the body has come to rest in the tray. Its
  settling time is main's two waits added up.
- **Found running it, and fixed**:
  - `haulerLaysCarcassOnRack` failed twice in a hundred copies run at once, both the same way (every decision logged): the
    cow lies off to the side of the line the hauler walks, cuts the corner as it is towed and passed the tray 1.2 to 1.5
    wide of the middle; the merged rule let a body down at a pass's end only within 1.2, so it took another pass, and
    standing past the rack by the pen's wall, with the body between it and the rack, it walked into the body, stalled
    and gave up, over and over. At a pass's end it now lets the body down wherever the bleeding finds this rack under
    it (main's rule; the tray catches a little wide), and does the same when it stalls over the rack. 150 copies passed
    after, none taking a second pass.
  - `hunterWithMeatHookLeavesCarcass` (15.16's finding, what the debug log was chasing): a body a block wide is pathed as
    two blocks wide, so by a wall or in a corner, where a hurt cow runs, the bite goal's path stops a block or two short,
    and vanilla's melee goal paths again only once the target moves, each new path ending where it stood. With its path
    done and what it goes for within 4 blocks and in sight, a minion now walks straight at it (`MinionGoals.Bite`), over
    safe ground only (15.17b).
  - On screen: a carved pumpkin on a pig's head flickered where the snout, a pixel proud of the head, met the pumpkin's
    front; what a minion wears on its head now has 4% more room than vanilla gives a humanoid's (`ROOM`), and the snout
    stays inside. The showcase's hand shot now holds the powder sac (the client is told the hotbar slot).
- **The showcase** photographs the new things close: the bowman from in front (a cow's head in an iron helmet stretched
  to it, its horns poking through the top, a bow in its right hand at its side), the cow on phantom wings from below
  (wings raised, raw stitched shoulders, hind legs dangling), and the pig's head in its pumpkin (`minions_2` to `4`).
- **Not done**: a torso's empty arm socket shows its raw stump as a flat square beside the shoulder (the face of the
  space the arm's top left, as `WoundCaps` draws a carcass's), which from behind reads as a thin plate sticking out; it
  is on docs/NEXT.md. docs/NEXT.md lost its "Already on the list" heading on main, and has it back.
- The suite is 443 tests (431, eleven from 15.16, and `chargedSacKeepsItsCharge`), and passed three runs in a row.

### 15.17b The review of 15.17 and 15.17a, and its fixes (verified)

A review of the merged tree listed seventeen problems, some of them twice. Each was checked against the code and all
were real. Each is fixed, and a test now shows it: every new or strengthened test was also run against the code with its
fix undone, and failed there.

- **The last stretch walked into lava** (15.17a's hunter fix): `MinionGoals.Bite` walked straight at what it went for
  whenever its path ran out within 4 blocks of it, in sight. The move control looks at nothing on the way, and a path
  stops short because the way on is lava, fire or a drop as often as because of a wall, so a guard by a lava river walked
  into it. The last stretch is now taken over safe ground only (`MinionGoals.clearWay`: every block its body would pass
  over, as wide as it is, each half block along, is one its own path finding costs nothing, with footing no more than a
  step down or up). `lastStretchStopsAtLava` (a husk three blocks off across a lava channel two wide: it stays at the
  bank, unhurt).
- **A lava walker went straight over fire and magma**: its path costs for fire and the edge of fire were 0, as a
  strider's are, but strider legs do not make a cow fire-proof (the trait spares it fire only in lava). Only lava costs
  it nothing now, and lava is the only thing added to what its navigation may cross (`MinionMoves.lavaMalus`,
  `LavaNavigation`). `lavaWalkerKeepsOffMagma` (it goes round a strip of magma by the gap at its end), and
  `lavaWalkerCrossesLava` checks that fire keeps vanilla's costs.
- **One strider leg made a lava walker**, against spec 6.4 (a capability needs at least half the fitted legs): the
  lava_walk flag came from any piece. A leg's trait that carries a leg capability flag (lava_walk, and climb: one spider
  leg also made a cow cling to walls) now counts only when at least half the fitted legs carry it
  (`MinionData.LEG_CAPABILITIES`, applied in `MinionData.traits`, so the flag, the navigation, standing on lava and the
  fire immunity in lava all follow). From anything else (an organ) it counts as before. `legCapabilitiesNeedHalfTheLegs`.
- **A resting carcass kept its limbs' organs**: harvesting a heavy carcass walked its live bones, but a carcass lying
  still folds its limbs into rest poses (`CarcassRest.rest`), so a cow left over the table gave up only its torso's.
  `Surgery.nextOrgan` walks the folded limbs too (and leaves out a limb cut through). `restingCarcassOrgansOnRig`
  (harvested once it has folded; `heavyCarcassOrgansOnRig` still clicks before it does).
- **A spent carcass over the table kept the Cleaver off it**: a Cleaver click on an empty rig went into any carcass over
  it, even one with nothing left in it, so the blade could not be laid down for surgery. It goes into the carcass only
  while an organ is left; `heavyCarcassOrgansOnRig` then lays it on the table.
- **A guard with a creeper's sac blew itself up**: the sapper is not woken to so that it does not spend its blast on the
  first monster (15.17), but the organ goal fired any ready activate effect, the sac's Self-Destruct included, so a guard
  did exactly that. The organ goal now leaves detonations out (`Activation.readyFor`); only the sapper's own goal sets
  one off, once it has walked up (spec 6.4 now says so). `guardKeepsItsBlast` (it wakes a guard, bites a husk from
  beside it, and never lights its fuse). Two older tests had a non-sapper blow up and now put theirs to sapping:
  `creeperSacPowersDownNotDestroyed` (walled in glass now, since a sapper's targets are a guard's) and
  `chargedCreeperSacIsStronger` (walled in too, the charged one held still until the plain one has blown, as its
  comment always meant).
- **A rider behind kept the maker's mount**: with the maker off, the rider behind became the front rider and steered it,
  and the maker could not get back on. When the maker gets off, whoever rode behind gets off too
  (`MinionEntity.removePassenger`). Steering is not limited to the maker instead, because the client does not know who
  the maker is and both sides must agree on who steers. `camelCarriesTwo` (the maker gets off, the friend with them, and
  the maker climbs back on).
- **A sinker drowned**: sink legs held a cow's torso on the bottom with no way up for air. A sinker short of breath
  (under a third of its air) now paddles up as any minion does, and goes back down once it has its fill
  (`MinionEntity.sinking`, its `surfacing` set in `baseTick` on both sides, since a rider's client moves its mount). With
  gills (a drowned's lungs, the undead horses' legs) or of brass it never runs short. Sink legs were not given gills: the
  design gives those to the Drowned Lungs. `sinkerSurfacesForAir` (a cow on iron golem legs, nearly out of breath: up,
  filled, back down, never hurt).
- **A stick-steered mount counted as standing still**: it goes on by itself while its rider holds the stick, but
  `steered()` read only the rider's keys, so it paid the idle rate and a brass one could couple to a shaft it walked
  past. For a stick-steered mount it is now true while its rider holds the stick. `pigHeadSteersWithCarrot`, and
  `camelCarriesTwo` (a saddle-only mount only while its rider presses on).
- **A fitted Spider Eye came back as the mod's Eye**: the organ_sources map makes a Spider Eye a spider's eye, but the
  eye's file names the mod's Eye, so one given back (by the armour fitting or the Assembly Frame) was a stamped Eye.
  `Organs.stack` now makes an organ as the vanilla item the map gives to that very organ of that mob, so a spider's eye is
  a plain Spider Eye both cut out of a spider's head and given back; a cave spider's is still the mod's Eye, stamped.
  `organSourcesInkSacIsSquid`.
- **A datapack's organ on a heart or eye item read as the plain one**: which organ a heart, lungs, stomach or eye was
  depended on the first organ file naming the item. `PartsData.organFor` prefers the file of the item's own id, and
  `Organs.stack` stamps the organ's id on such an item whenever it is some other organ (plain hearts stay unstamped and
  stack as before). `datapackOrganOnAHeartKeepsItsId` (`PartsData.addTestOrgan`, looked up like `addTestTrait`).
- **A worn skull was drawn inside the head**: skulls and mob heads went through the item's head look, which a skull's
  item model does not have, so it came out small and inside the head. They are now drawn as `CustomHeadLayer` draws them
  on a humanoid (the skull model, 1.1875 times the head, sitting on its bottom, a dragon's head working its jaw as it
  walks). On screen (`showcase_minions_5.png`, new): the whole cow in the row wears a zombie's head, a full-sized head over
  its own, face forward, one of the cow's horns poking through its side.
- **The waiting list** (`signatureLint`'s report): the piglin's note had lost "a held crossbow outside a sentry's post (its
  head offers no sentry)", which nothing had built, and has it back. The aggressive panda's head lacked spec 8.2's
  Brawler II; its variant now gives it (`carcassKeepsVariants` checks it), so "the other genes are wired" is true.
- The suite is 450 tests (443 and the seven named above), and passed three runs in a row.

### 15.18 Checks nobody had run (verified)

Brief audit package 18. Three things the brief asks for had never been run: "verify things by running them … actually
look at anything visual on screen", "cheap enough that a dozen at once is fine", and "it has to look right in
multiplayer". Each check is now built and has been run, and each turned something up.

**Two clients on a dedicated server** (section 11, slice 1's "looks right with two clients", never recorded as done):

- **How to run it.** `-Dbloodandbones.multiplayer=true` adds three Gradle runs, each in its own folder under `run/`:
  - `runMpServer`: a dedicated server. Every start makes a fresh flat creative world, lets offline development clients
    in, and accepts the game's EULA in that folder (a development server will not start until it is accepted).
  - `runMpButcher` and `runMpWatcher`: two clients called Butcher and Watcher. Each joins `localhost:25565` (or the
    address in `-Dbloodandbones.multiplayer.server`), retrying until the server is up.

  Start the server, then the two clients, each in its own terminal (`gradlew.bat` on Windows). All three stop by
  themselves after about forty seconds of play.
- **What happens.** Once both players are in, the server (`gametest/MultiplayerCheck`) waits twenty seconds more, since
  a client drawing in software is slow to load. Then it:
  - clears a patch of the flat world;
  - puts a cow three blocks in front of the Butcher;
  - puts a Shackle Hook four blocks up and six to the side, with a Bleeding Rack under it;
  - gives the Butcher a Meat Hook, a Cleaver and a Flensing Knife;
  - tells both clients when the clock starts.

  The Butcher's client (`client/MultiplayerShowcase`) then plays with real clicks, each aimed at what its own client
  draws: two blows of the Meat Hook, a click on a leg, a walk of six blocks facing the carcass, a click on the hook,
  three Cleaver strokes through a front leg, and four Flensing Knife strokes down the body. The Watcher hovers and takes
  29 pictures (`run/mp-watcher/screenshots/mp_watcher_*.png`); 21 of them are a film of the drag, one every six ticks.
  The Butcher takes three of its own.
- **What is compared.** The `[mp]` lines in each `logs/latest.log`:
  - the server writes, every tick, where every carcass body is, where the Butcher stands, the rack's blood and the
    stains on the ground;
  - the Watcher writes where it draws every body, every tick and every frame, whether it sees the cow, and its own count
    of the rack's blood and of the stains.
- **Where it ran.** The server and both clients on one four-core machine with no graphics card (xvfb, software
  drawing, 854×480). There were seven runs. The numbers below are from the fifth, the first with the fixes below; the
  sixth never started (item 3), and the seventh, the same as the fifth, found the cut leg falling through the ground
  (item 4).

What the Watcher's pictures show. Four from the seventh run are in `docs/screenshots/multiplayer_*.png`: `_drag`
(enlarged), `_hung`, `_cut` and `_skinned`. The fifth run's pictures were written over by a later run and not kept.

- after the kill, the cow falls and lies where the server has it;
- the drag: the Butcher steps back and sideways, the cow hooked by a front leg. The Meat Hook is stuck in the leg with a
  line to the Butcher's hand, and a trail of drops and stains is left behind;
- the cow hangs head-up from the hook by the neck, and blood drops into the rack below. The tray's blood rises;
- after the cut, the Butcher holds a bloody Cleaver and fresh blood lies under the cow and by the rack. The cut leg is
  not in the picture: in the seventh run it fell through the ground (item 4). In the fifth run both the server and the
  Watcher had it lying on the grass by the rack;
- after skinning, the hanging body is bare flesh and the raw hide lies on the ground by the rack;
- from the side at the end: the rack, the stains and the hide.

What the numbers show:

- **Where bodies are drawn.** The Watcher draws each body 3.5 cm on average from where the server had it a tick earlier
  (its interpolation delay). That is 12 cm over the drag, 3 cm while hanging, and under a millimetre once still.
- **Nothing went missing.** Every tick of the handover, either the frozen cow or its carcass was on the Watcher's
  screen. Every body the server had, the Watcher had, except in the first ticks of the carcass while the frozen cow still
  stood. Its count of the rack's blood and of the stains, taken each whole second, matched the server's 38 times in 39
  in the fifth run and 37 times in 39 in the seventh (at twenty seconds: 132 mB and 8 stains on both sides). Each miss
  was a change the two logged a few ticks apart (the two clocks are a few ticks off each other; for example 231 mB on
  the Watcher at 24 seconds, which the server logged three ticks later), and the next second they agreed again.
- **Smoothness.** The Watcher drew about 20 frames a second over the drag. On most ticks it moved the carcass at the
  server's speed (2 to 9 blocks a second), with no frame jumping back. A few times its drawing stood still for one to
  three ticks (once about seven, at the start of the drag) and then caught up exactly. These match the Watcher's own
  frame stalls of up to 0.3 seconds (software drawing, three games on four cores, screenshots being written). They are
  not something the mod sends: the server kept 20 ticks a second throughout.

What it turned up:

1. **Hanging a carcass threw the player standing by it** (the throw is fixed; a player against the body is still
   jostled).
   - The Shackle Hook held its body with a ball joint made at once. A body lying a few blocks off went up to the tip in
     one tick, at about 80 blocks a second.
   - Sable gives a player the motion of any moving body that pushes them. The Butcher, standing beside the body, was
     thrown about 110 blocks up and 220 along. A single-player game does the same, because the push happens on the
     client.
   - The hook now hoists the body up at 3 blocks a second (`ShackleHookBlockEntity.hoist`: a push at the hooked point
     that carries the whole carcass's weight). It makes the joint once the hooked point is within 0.3 blocks of the tip.
     A body still short of the tip after four seconds (caught under something) is held where it got to: the joint is
     made at the hooked point's own place, not at the tip.
   - Whether it is hoisting is not saved. A hook read back with its body more than 0.3 blocks off the tip (saved or
     unloaded part way up, or held where it got to) hoists it again rather than snapping it up; as before, a body more
     than 8 blocks off is let go.
   - The Shackle Trolley made the same snap: a Meat Hook click on a chain strand made its joint at once, on the
     trolley's first tick. It now hoists the same way, with the same push, and waits where it is on the chain until the
     body is up. A body it cannot bring up in four seconds it lets fall, since it could not carry it along the chain from
     where it got to. A body that came away from the chain while unloaded is hoisted back too.
   - A hook on a ship is not changed: it still makes its joint at once. (Reading Sable's constraint checks, a joint to the
     world may not be anchored inside a ship's plot, so it may not hold a body at all. Nothing tests hooks on ships, and
     this was not run.)
   - Tests, each failing without its fix (old code, eight runs each):
     - `shackleHookHoistsWithoutFlinging`: a cow hung from four blocks away rises at no more than 3.6 blocks a second and
       hangs from the tip (89 blocks a second without the hoist);
     - `shackleHookReloadedPartWayUpKeepsHoisting`: the hook is read back from what it saved when the cow is 4.6 blocks
       short of the tip, and the cow still rises at the hoist's pace to the tip (88 to 90 blocks a second before);
     - `shackleHookHoldsACaughtBodyWhereItGotTo`: a cow in a roofed stone pen stays in it, held where it got to (dragged
       out through the stone at 95 to 104 blocks a second before);
     - `shackleTrolleyHoistsWithoutFlinging`: a cow under a chain rises to the trolley at the hoist's pace, and the
       trolley then carries it on (69 to 71 blocks a second before).
   - **Not fixed: a player right against the body is still jostled.** In the seventh run the Butcher stood against the
     cow as it rose. It lifted him about a block and a half, from where it had pressed him into the ground, and carried
     him about two blocks sideways, once at 22 blocks a second in a single tick; he was off the ground for about 14 ticks
     (`run/mp-watcher/screenshots/mp_watcher_04_hanging.png` shows him in the air, tangled with the rising cow). In the
     fifth run he stood clear and nothing happened. So the hoist ends the throw across the world, not the jostle. The drag
     already stops pulling a body that touches its player; the hoist could wait the same way, but then a body would hang
     low for as long as a player stands by it. Left for package 1, with the drag.
2. **Walking forward while dragging carries you off** (not fixed here; for package 1. Fixed since: 15.28, "What you drag
   never pushes you").
   - The drag pulls the hooked point to 1.1 blocks in front of where the player looks. Walking forward, facing where
     you go, pulls the carcass into your path. You walk into it and it pushes you, which moves the point it is pulled
     to, and so on.
   - In the second run the Butcher stopped walking and was still carried about 20 blocks over four and a half seconds,
     the drag holding throughout.
   - Walking backwards and sideways facing the carcass, as the later runs do, works. A hauler holds its carcass
     behind it; whether a player's drag should too is a question for package 1.
3. **A client failed to start once** in fourteen starts, with Registrate's "Found unused register callbacks" while
   loading mods. It did not happen again.
4. **A cut leg fell through the ground** (not fixed; not reproduced).
   - In the seventh run the front leg, cut off the hanging cow, fell four blocks, touched the grass, and went on
     falling through it into the void as if there were no ground at all (it fell at the rate of gravity, 250 blocks
     down within eight seconds). Both the server and the Watcher had it so: the Watcher's picture after the cut shows the
     blood but no leg (`docs/screenshots/multiplayer_cut.png`). The leg was cut off in three of the seven runs (the
     fourth, fifth and seventh); in the other two it lay on the grass by the rack.
   - Game tests of the same thing did not reproduce it in 42 runs, over a hundred legs: a hung cow's leg cut off over
     stone and over grass, legs dropped from four blocks onto chunk borders, and legs with blood stains put down under
     them. Every leg stayed on the floor.
   - So it happens on a dedicated server, sometimes. Sable only builds the ground a body can hit in the chunk sections
     near a body (`PhysicsChunkTicketManager`), so that is where to look first. Until it is found, a cut limb can be
     lost now and then.

**A dozen carcasses at once.**

- **The tests** (`CostTests`). `dozenCarcassesAtOnce` drops twelve fresh carcasses of every size from up to three
  blocks: chicken, rabbit, pig, sheep, cow, wolf, villager, zombie, spider, horse, llama and polar bear, 79 bodies in
  all. Each is knocked over as a kill knocks it. `dozenHungCarcasses` hangs the same twelve on twelve Shackle Hooks.
  Both time every server tick.
- **Measuring.** Each was run on its own, with `-Dbloodandbones.debug.cost=N`, on the same four-core machine. Then the
  dozen test makes N dozen in the same tick in its one arena, 33 blocks across. Before anything is timed it makes a
  dozen and clears it away, so that nothing timed runs for the first time. The hung dozen is timed for a minute. In the
  full suite the tests only check that all the dropped carcasses come to rest on the floor, and that every hung one is
  hoisted to its hook's tip without being flung.
- The first measurement, which the review looked at, is replaced. It never timed the tick the carcasses were made in
  (the 49 to 58 ms it gave was the first physics step after, with nothing run before it). Its 24 and 48 were copies of
  the test side by side, made up to a second apart, and it showed the empty-arena time of only one copy (the others
  were 18 to 27 ms, with spikes of about half a second, from the copies around them).

| Carcasses | Empty arena, mean | The tick they are made in; the next | Awake, the 5 s after: mean / 95th percentile | All resting, mean | All at rest after |
|---|---|---|---|---|---|
| 12 (two runs) | 1.1 and 1.3 ms | 143 and 149 ms; 19 and 26 ms | 7.5 / 11.1 and 8.7 / 12.6 ms | 1.6 and 1.6 ms | 11 s and 9 s |
| 24 | 1.2 ms | 334 ms; 41 ms | 14.7 / 19.5 ms | 2.1 ms | 8.5 s |
| 48 | 1.4 ms | 571 ms; 70 ms | 26.5 / 32.0 ms | 1.9 ms | 15 s |
| 12 hung on hooks | 1.4 ms | (hung one by one) | 6.6 / 8.6 ms over the whole minute | none rest | never |

These are milliseconds of a server tick, whose budget is 50.

- **Making a carcass is the dear part: about 12 ms each**, in the tick of the kill. One kill is a quarter of a tick; a
  dozen kills in the same tick stall the server for a seventh of a second.
- Awake, a carcass costs about 0.55 ms of every tick. Resting ones cost little: the 79 bodies of a dozen become 12, and
  a dozen to four dozen resting add under a millisecond to the empty arena.
- **A hung carcass never rests.** `CarcassRest.isHeld` keeps a hung, trolleyed or dragged carcass out of its resting
  form, and the hook's belly-out spring pushes its torso every physics step, which keeps all its bodies awake. A dozen
  hanging cost about 5 ms of every tick, 0.43 ms each, for as long as they hang: all 79 bodies were still awake after a
  minute. Trolleys hold their bodies the same way (the same joint and spring) and were not timed apart.
- **The per-dimension cap of section 3.4 is still open, and was not built.** Carcasses on the ground are cheap: a dozen
  awake use about a sixth of the tick for the ten seconds or so before they rest, four dozen about half of it for
  fifteen seconds, and then almost nothing. But a butchery line keeps its carcasses hanging: 48 on hooks or trolleys
  would cost about 20 ms of every tick, all the time. So a line of hung carcasses needs a cap, or a still, hung carcass
  that is let sleep (the spring left off once it hangs still), or folded as a resting one is. And many kills in one tick
  are costly on their own. Decision 3 (every kill leaving a damaged carcass) would bring both, so settle them together.
- **It found a rabbit spinning on its hook** (fixed). The hook's and the trolley's turn spring used a mass of at least
  0.05 for a torso. A rabbit's torso is 0.018, so the spring was nearly three times too stiff for it and spun it at about
  60 radians a second; being hoisted, it never came up to the tip. They now use the body's own mass. Without the fix
  `dozenHungCarcasses` fails with the rabbit 3 to 6 blocks short of its tip.

**A spider never rested** (fixed; found by the dozen).

- Its eight thin, light legs twitched against the ground for ever: 0.1 to 1.5 radians a second, a leg's tip wandering
  up to a tenth of a block in three seconds. That is above the stillness bar, so a dead spider kept eleven bodies of
  physics running for good, and the whole dozen never all came to rest.
- A carcass now also rests once all its bodies have stayed where they lie for five seconds, however they twitch: each
  within a quarter of a block, the torso within a tenth (`CarcassRest.stayedPut`).
- Spiders and cave spiders now rest in under six seconds, and the dozen in 9 to 14. Without this, `dozenCarcassesAtOnce`
  fails with the spider still awake.

**Tests for what was built but unproven** (`UnprovenTests`). Each was also run with its feature taken out, and failed:

| Test | What it shows | Taken out to prove it |
|---|---|---|
| `fanSpeedsUpBleeding` | a hung cow bleeds 110 mB in five seconds with no fan, and 310 mB with a fan at 128 RPM under it | the fan's boost in `CarcassBleeding.tick` |
| `skeletonCannotBeSkinned` | eight Flensing Knife strokes do nothing to a skeleton: no hide, no bare flesh | the no-hide check in `CarcassButchery.skin` |
| `bloodNeverMakesASourceBlock` | a gap between two sources of blood (and of Soul Blood) fills with flowing blood, never a new source | blood and Soul Blood made able to form sources |
| `degloverSkinsASingleLimb` | a cow's leg put down on its own on a turning Deglover comes off skinned | the Deglover limited to whole carcasses |
| `guillotineLimbGoesToAMinionAndAWallHook` | a Guillotine takes two legs off a cow, never its head; both are picked up; one hangs on a Butcher's Hook, and the other is fitted to a cow's frame and the minion wakes walking on it | the Guillotine's cut |
| `killWithoutTheMeatHookLeavesNoCarcass` | pigs killed with a sword, with a bare hand and by plain damage die and drop pork, with no carcass | the Meat Hook check in `CarcassEvents.onDeath` |
| `pieceRidesABelt` | a leg cut off a cow is picked up and dropped on a running belt; it rides to the end and falls off, the same piece | pieces becoming items (`CarcassButchery.pickUp`) |
| `magnetCoilAtHighSpoolDrawsInACarcass` | a cow seven blocks off stays put below three quarters of full spool; at full spool it is drawn to 3.3 to 4 blocks in two seconds | the coil's carcass pull |
| `grapplingSpoolHandsACarcassToTheDrag` | a cow lying still six blocks ahead is hit by the spool, reeled in, and then held by the Meat Hook's drag | the hand-off to `CarcassDrag.start` |
| `shackleHookHoistsWithoutFlinging`, `shackleHookReloadedPartWayUpKeepsHoisting`, `shackleHookHoldsACaughtBodyWhereItGotTo`, `shackleTrolleyHoistsWithoutFlinging` | see item 1 above | the hoist, and each of its three fixes |
| `dozenHungCarcasses` | see the dozen above | the turn spring's own mass |

The Grappling Spool test turned something up too. Run in the full suite, and then 30 times on its own, it failed 7
times in 30. The spool gave a carcass it reeled in only 20 ticks more than a mob (a mob gets 34 ticks for a spool
fired at once, so a carcass had 54), but a carcass is dragged along the ground, far slower than a mob is yanked
through the air: a cow six blocks off came within reach just as the time ran out, or just after, and was then never
handed to the Meat Hook's drag. A carcass now has 100 ticks more than a mob (`ModuleActions.CARCASS_HAUL_TICKS`): 80
more than before, 134 in all for a spool fired at once. The cow arrives in about 58 ticks of its 134, and the test
passed 30 times in 30, and the ten new tests 10 times each.

What is not built of these: the Guillotine leaves the limbs it cuts on the floor, as bodies, and nothing but a player
takes them further. It has no output to a belt or funnel, and nothing puts a piece on a wall hook or a minion frame by
itself (package 4 and decision 9). Nor does it "wind up and drop on a redstone edge": it cuts on a timer while it turns
(package 15).

**On screen.** The showcase gains a slow belt east of the decoration row, with a Depot at each end and three carcass
pieces dropped on it. In `showcase_17.png` (enlarged in `docs/screenshots/showcase_belt.png`) a pig's leg stands on the
Depot at the end the belt runs to, and a cow's head and a cow's leg wait behind it on the belt, each drawn as the part it
is.

The Magnet Coil test reached into the tests beside it (found in review). At full spool the coil reaches 16 blocks, and
an ordinary test area is 11 blocks across with 5 between: it drew in the carcasses and items of its neighbours, and a
Grappling Spool test run beside it failed 6 times in 8, its cow pulled away by the coil faster than the spool reeled
it in. The full suite only passed because of where the tests happened to be placed. It now has an area of its own 33
blocks across (`empty_wide`) and stands in the middle of it, and fails at once if the coil's reach ever outgrows it.
Run beside each other eight times, both passed every time.

The suite is 466 tests (the 451 on main and the fifteen above), and passed three runs in a row.

### 15.19 The machines, the three paths and the filters (docs/BRIEF-AUDIT.md packages 5, 10, 15 and 16)

Built on 054cdfd. This closes audit package 16, and 15 except the Spit Roast's effects, which wait for the owner's
decision 8. Packages 5 and 10 are partly built; what is left of them is listed at the end of 15.19.1 and 15.19.2. Decision 6
is open, so the Flensing Knife and the Cleaver stay two tools. Main has since taken `bb-organs` (bfd6e0d), whose organs
come from data (`Organs.held`) and whose `Surgery.harvest` also takes organs out of a carcass lying over the table.
This branch counts organs under main's key and with main's functions (`organsTaken(traits, bone, root)`, `putDown`,
15.19.5), so the two never keep two counts; when they meet, the rig's organ step (`SurgicalRig`, the three of a body and a
head's two eyes from `Surgery#organs`) should give way to main's harvest, with the rig's filter asked as here.

#### 15.19.1 The yield gap and three paths that stay apart (package 5)

- **Paths are data.** `data/<ns>/butchery_path/<id>.json` (`ButcheryPath`: `scale`, a share per kind in `kinds`,
  `loss`, `loot_table`, `scraps`), loaded by `ButcheryPaths`. `CarcassButchery.dropYields` multiplies each yield by
  the path's share for its kind and by the hand's butchery yield, rolls the fraction as before, then rolls the loss once
  for every whole item. The path is set around the work: `onPath(path, yield, action)`, or `byHand(who, action)`, where
  a player's own hand or a minion's is the hand path at its `butchery_yield`, and a Deployer's stand-in or no one is a
  station. A real player's `cut` or `skin` with no path set is by hand. Rot's crumbling keeps its own half, outside any
  path. A path with no file takes everything.

  | Path | Meat, bone | Offal, fat | Hide | Other | Loss | Also |
  |---|---|---|---|---|---|---|
  | `hand` | 0.6 | 0.6 | 0.6 | 0.6 | 15% | |
  | `station` | 1 | 1 | 1 | 1 | none | |
  | `mangler` | 0.25 | 0 | 0 | 0.25 | none | the mob's loot table with its torso, less what its table gives; scraps for every piece |
  | `surgery` | 1 | 1 | 1 | 1 | none | the organs, from the rig |

  By hand is 0.6 × 0.85, about half. A Flensing Knife or Cleaver in a player's hand, a Cleaver at the Butcher's Table or
  the Surgical Rig in a player's hand and a butcher minion's blade are by hand. The Deglover, a Deployer's Cleaver at the
  Butcher's Table and the Spit Roast's cooking are a station. The Mangler and a Deployer at the Surgical Rig have their
  own. Every share is read somewhere: the hide share by the knife, the Deglover and the rig when they flay, and by the
  Mangler for an unskinned piece it grinds (`CarcassButchery.groundHide`; its 0 grinds the hide away, and a datapack
  that raises it gets hide back).
- **`butchery_yield`** (PARTS-AND-TRAITS 5.7): an attribute from 0 to 4, 1 on players and minions. The trait
  `keen_butcher` (+10% a level) is now data on it; no mob carries it yet.
- **The Mangler** takes one stroke a piece. An attached limb is severed and ground in the same stroke
  (`CarcassButchery.sever` now returns the piece's own record); a loose piece or a bare body is ground where it lies.
  Grinding the rig's torso rolls the mob's own loot table (`CarcassButchery.rollLoot`: a fresh instance of the mob, never
  added to the world, a generic damage source; a baby drops nothing and `doMobLoot` is obeyed, as vanilla's
  `shouldDropLoot`). The mob is set as the carcass was where its loot reads it: a sheep's colour from its `wool` trait
  and sheared once skinned or if it was sheared, a slime or magma cube at its size (the rig's baby is the smallest, else
  the biggest). Any item the mob's butchery table also gives is left out of the roll: the table's quarter already
  stands for it. Without that the drop came on top, and an iron golem's 3 to 5 ingots, a chicken's one raw chicken and a
  cow's beef gave the "wasteful" Mangler as much as or more than a station. What is left is what no table gives: a
  cow's leather, a zombie's rare iron, a skeleton's arrows. New sound `machine.grind` (slime, honey, bone and berry
  bush), with a clean twin (15.19.3).
- **Costs** (`MachineKind`: stress per RPM, stroke as a share of 80 ticks at 16 RPM, top speed):

  | Machine | Stress/RPM | Stroke | Works faster up to |
  |---|---|---|---|
  | Mangler | 8 | 0.5 | 256 RPM |
  | Guillotine | 4 | 1.0 (its wind-up) | 256 RPM |
  | Beheader | 2 | 0.25 | 256 RPM |
  | Deglover | 16 | 0.5 | 32 RPM |

  The Beheader is the cheapest and quickest. The Deglover costs the most and gains nothing past 32 RPM (its goggles say
  so), so a slow shaft is the sensible one.
- **The Flensing Knife is held on a part.** `UseAnim.BRUSH`, a use of 72000 ticks. A right-click on a carcass part
  starts the hold (`CarcassPartBlock`). `onUseTick` finds the part under the crosshair the way `BrushItem` does (Sable's
  `clip` reaches into sub-levels), strokes every 10 ticks, and lets go when the hide is off after four strokes or when
  the look leaves the carcass. A Deployer's stand-in never ticks a use, so each of its pushes is one stroke.
- **The Surgical Rig is a whole path** (`SurgicalRig`). Each Cleaver cut takes one thing: first an organ, then the
  hide if it is still on (all of it at once, `CarcassButchery.flay`; a carried piece its share, `pieceHide`), then a
  limb at its joint (the end of a chain first), then the piece's whole table. It works a carried piece laid on the table
  (once its organs and hide are out, the piece comes apart on the top), or, with nothing laid on it, a carcass lying on
  its top (not one hanging over it from a hook or a trolley: `lyingOn` leaves those out). A Deployer's Cleaver works it
  as a station, on the surgery path; a player's own hand gets the organs whole but the hide and meat on the hand path,
  about half. Whoever cuts, a cut starts `SurgicalRig.PAUSE` (30 ticks) before the next may (`SurgicalRig.click`, kept
  on the table so a Deployer is held to it too, and shown on a player's Cleaver as its cooldown), so the rig is the
  slowest path at any speed. A carcass in the world counts each part's organs in its traits under main's key
  (`organs_taken:<bone>`), which a piece picked up off it keeps; a carried piece counts under the plain key, and set down
  (`CarcassPieceItem.useOn`, or off the Spit Roast) that becomes its bone's own (`Surgery.putDown`). A click that finds
  nothing it may take lays the Cleaver on the table, as before; one the filter turned away says so.
- **The Butcher's Table** also chops a loose piece lying on its top (`CarcassButchery.lyingOn`), so a torso too heavy
  to carry can be dragged there. That is the full-yield end of the filtered path.
- **Tests.** `cowDownEachPath` sends a cow down each path at 32 RPM. By hand: the knife held, then the Cleaver on each
  joint and piece. The Mangler: until nothing is left. The stations: a Deglover, a Beheader and a Guillotine swapped in
  under the body in turn, then a Deployer's Cleaver at a Butcher's Table swapped in under the body, with the light pieces
  laid on it. The Surgical Rig: a Deployer's Cleaver, with the pieces that fall off laid back on, each cut the longer of
  the Deployer's push and the rig's pause. One run:

  | Path | Time at 32 RPM | At 256 RPM | What came out |
  |---|---|---|---|
  | By hand | 436 ticks | | 2 beef, 3 bone, 1 hide, 1 offal |
  | Mangler | 126 ticks | 30 ticks | 2 beef, 1 bone, 1 leather (its drop), 19 scraps |
  | Stations | 490 ticks | 135 ticks | 5 beef, 6 bone, 2 hide, 2 offal, 1 fat |
  | Surgical Rig | 680 ticks | 510 ticks (510 by hand) | 5 beef, 2 bone, 3 hide, 1 offal, 1 fat, a heart, lungs, a stomach, 2 eyes |

  It asserts that the Mangler is quickest and the Surgical Rig slowest at both speeds and by hand, that the Deglover's
  hide is whole and so is the rig's, that the Mangler gives the cow's own drop and scraps but no hide or offal, and that
  only the rig gives organs. It then rolls 300 cows down each path through the same code, the Mangler's with its loot.
  By hand came to 0.51 of a station's meat and bone, the Mangler's to a quarter, and the Surgical Rig to all of it.
  `handYieldIsAboutHalfWithRealLoss` shows the loss is real, apart from rounding: four beef exactly, by a hand whose
  butchery yield makes up the share, still comes out short now and then, while a Deployer always gets four. It also shows
  that a butchery yield of 2 gives about twice as much. `manglerGrindsInTheMobsOwnDrops` grinds an iron golem: no more
  iron than its quarter of the table; a cow's loot gives leather and never beef; a black sheep's gives no wool, skinned
  or not; a calf's nothing; a ground piece's hide goes at the path's share. `manglerKeepsTheLeastOfAnyMob` rolls 300 cows,
  chickens and iron golems down the Mangler (loot and all) and by hand: the Mangler gets no more of any item the table
  gives, and about half a hand's in all. `surgicalRigIsPacedWhoeverCuts` puts a real Deployer at 256 RPM over a rig
  (three organs take at least two pauses) and a player clicking every tick at another (in three pauses, two eyes and the
  hide, and the head still there). `surgicalRigByHandGetsAHandsShare`: a hundred cow bodies cut up at the rig by a
  player's hand give 0.52 of a Deployer's meat and bone, and the Deployer's come to the bodies' whole table. (One cow's
  rolls can give the rig as little as the whole numbers of its table, so `cowDownEachPath` asks no more of the one cow;
  it asked for two bones, and one run in about seventy came up with one.) `machinesCostAsTheBriefSays` checks the costs.
  `flensingKnifeIsHeldOnAPart` checks that a click starts the hold, three strokes are not enough, four are, and it lets
  go. `rigTakesEachOrganOnce` (`SurgicalRigTests`) takes a cow's head's eyes as a carried piece, sets it down on the rig
  and gets no more eyes; and takes a cow's five organs lying on the rig, cuts off its head, picks it up, lays it on the
  rig and gets its hide, not an eye. `rigTakesTheHideAndLeavesAHungBodyAlone`: a leg's first cut flays it, a cow's sixth
  (after its five organs) flays it whole, and a Cleaver clicked on a rig with only a hung cow over it is laid down.
- **Not built:** nothing in survival carries `keen_butcher` yet, so `butchery_yield` is 1 on every player and minion;
  which mobs' parts or which minion build gives it is for the balance pass (PARTS-AND-TRAITS slice 9).

#### 15.19.2 Filters that pick one part (package 10)

- `PartFilter` and `PartFilteringBehaviour` are taken out of the machine and shared. The filter is asked about each part
  as the item that part would be (`CarcassPieceItem.of(carcass, bone)`, an organ as its organ item). A spawn egg or a
  carcass piece means that mob; a list filter asks each entry, as a whitelist or a blacklist; an attribute filter asks as
  Create would. The machine asks about every part it could take (`accepts(carcass, bone)`), and only unfolds a resting
  carcass when one of its parts would be taken.
- **A finer part attribute**, `piece_slot` (`BBItemAttributes.PieceSlot`), is "is a carcass hind leg / front leg /
  middle leg / leg / tentacle / arm / wing / pair of arms / neck / back half". It comes from `PartSlots`, the slot rules
  that are data, so a rabbit's haunch is a hind leg and a modded mob's legs sort by the same rules. Any slot key the
  rules or a mob file give is one (a datapack's `arm.fin` too), worded from the lang file where it has words
  (`bloodandbones.piece_slot.<key>`) and from the key's own parts where it has not ("fin arm"). It is offered for limbs
  only: heads, bodies, tails and decoration are `piece_part`'s.
- **The Butcher's Table and the Surgical Rig** carry the filter on the edge of their tops, on whichever side you look
  from (`TableFilterSlot`, the Basin's pattern); the Surgery Table shows it only with the rig fitted. The table's item
  handler takes only pieces its filter passes, so a funnel over a mixed belt pulls those and lets the rest go by. Its
  Cleaver chops only those. The rig works only the parts its filter passes (an organ is asked about as the part it is
  in, the hide as the body it comes off). A Create filter in the slot is the player's own item: both tables call
  `IBE.onRemove`, as Create's blocks do, so breaking one drops it, and taking the rig off (or fitting the Assembly Frame
  over it) hands it back and clears the slot (`SurgeryTableBlockEntity.takeFilter`).
- **Cheap to wait.** A machine ready and idle looks every 2 ticks at first, and each look that finds nothing doubles the
  wait, up to 10 ticks; a stroke resets it. A look passes over a record whose root lies more than 6 blocks from its zone
  before touching the rest of it, and makes no map for a record with nothing in reach. The part filter asks the filter
  the slot already read (`FilteringBehaviour`'s own `FilterItemStack`) rather than reading the item again each time.
  The Mangler's `hasWork` now matches what it can take: a body counts only once nothing hangs off it, so a Mangler set
  to bodies no longer unfolds a resting cow with its head on every stroke, for nothing.
- **Not built, for the tasks work (decision 12):** the butchering minion's filter. The butcher job would read a filter
  through `PartFilter.takes` the same way; which slot it lives in belongs with how a task is given (the audit says
  package 10 needs no owner decision, but a minion's job is now a task, and how a task is given is decision 12).
- **Not built:** a filter for single organs at the rig (take only hearts). The rig asks about the part an organ is in,
  so the brief's "every station that removes parts carries a filter" holds for parts but not yet for organs. Organs
  come from main's data now; the filter belongs with main's harvest when the two meet.
- **Tests.** `partFilterAsksAboutEachPart` checks cow, pig and rabbit parts against hind-leg, head, egg and blacklist
  filters, and what the Attribute Filter offers. `guillotineTakesOnlyHindLegs`: a Guillotine set to hind legs takes a
  cow's two and then passes the rest over through a hundred more ticks of drops. `mixedLineSortsAtTheTables`: a
  head-only table takes two heads out of a line of four pieces and turns the legs away; a head-only rig passes a body
  over and takes a head's eyes.

#### 15.19.3 The machines move (package 15)

- **The Guillotine** keeps `wind`, `falling` and `powered`, all saved. While it turns it winds at one stroke's length
  (80 ticks at 16 RPM, 40 at 32), on both sides (the client draws it), with a ratchet click (`machine.wind`), and holds
  at the top, armed, with a clack. `CarcassMachineBlock.neighborChanged` hands `hasNeighborSignal` to `redstone()`: a
  rising edge drops an armed blade (the Sequenced Gearshift's way), it falls for 4 ticks and cuts one limb at the bottom,
  and it winds up again. A pulse while it winds is wasted, and a signal held on is not an edge. Its goggles show
  "Winding up: N%" or "Armed".
- **Strike when ready.** The Mangler, Beheader and Deglover count up to a stroke and then wait ready, looking every 2
  ticks and striking as soon as something they take is in reach. A Beheader under a chain takes the head off a passing
  trolley (`beheaderTakesHeadsOffAPassingTrolley`).
- **Moving parts.** `CarcassMachineRenderer` follows Create's own kinetic renderers (`MechanicalMixerRenderer`): a
  partial model per part, turned by render time and speed. The Mangler has two toothed grinders turning into each other
  in an open pit (a new block model and a `mangler_grinder` texture). The Deglover has two rollers turning against each
  other, the Beheader a saw at four times the shaft, and the Guillotine its blade and weight, with a rope stretched to a
  drum on the crossbar. `BBPartialModels` has a clean twin of each bloody part, drawn in bloodless mode. The block models
  lost their moving parts; the items keep them, standing still (`block/<kind>_item`). Every face of the machines' models
  above the block now has its own UV; before, the Guillotine's posts and crossbar, the Mangler's rim and the Beheader's
  slots sampled their neighbours on the texture atlas.
- **The Spit Roast takes whole carcasses.** Right-click it with the Meat Hook while dragging one (within 3 blocks), or
  with one lying over it. Every piece goes on and the bodies leave the world (`CarcassButchery.takeAway`). The cook time
  goes by all its meat, up to 4800 ticks. Cooked, it gives every piece's yields cooked; raw, it is set down on the spit
  whole again (`CarcassAssembler.assembleWhole`: its root piece laid as a carried piece is, every other piece at its place
  on the living mob round it, joined to its parent, the whole lifted clear of the ground, built folded and unfolded at
  once, as `CarcassRest` unfolds a still body). The spit remembers that it holds a carcass from the world (`Carcass`,
  saved), not a count of pieces, so a torso with every limb cut off is still one: it never goes into a hand, however
  it comes off, and cooks to the whole carcass's cap. It is drawn whole at its rest pose, head to tail along the spit,
  turning. It now cooks
  |RPM| / 32 times the campfire pace, up to 8 at 256 RPM (it was 1 + RPM/64, at most 2): a Hand Crank at 1, a fast shaft
  eight times that (`shaftCooksFarFasterThanACrank`, `spitRoastTakesAWholeCarcass`).
- **Not built, for decision 8:** effects on cooked results.
- **Sounds in bloodless mode.** A server plays the same sound for everyone, so each client picks: `BloodlessSounds`
  swaps a gory sound for its clean twin on `PlaySoundEvent`. Only `machine.grind` has a twin so far; the other sounds are
  audit package 9's.
- Ponder: the four machines' scenes say what each takes and add a line on the filter; the Spit Roast's and the Butcher's
  Table's scenes cover whole carcasses, the crank, the hand's half and the filter. The line on the shaft is each
  machine's own (`BBScenes.speedLine`): the Guillotine only winds faster, the Deglover is no faster past 32 RPM. The
  Guillotine's scene puts a lever beside it, says that only a redstone pulse drops the armed blade, and drops it.
- Showcase: row F, west of the machines, has the four machines bare with their parts turning, a Guillotine armed and one
  stopped part-wound, and the Butcher's Table and Surgical Rig with filters set. There is also a whole cow on a spit, the
  row B Mangler set to limbs (it grinds a cow's legs and leaves the body), an observer clock dropping row B's Guillotine,
  and the knife held on a carcass in the second hand shot.
  Pictures: `docs/screenshots/machines_moving.png`, `guillotine_armed_and_winding.png`, `whole_cow_roast.png`,
  `table_filters.png`, `mangler_limb_filter.png`, `bloodless_machines_moving.png`. Since the review (15.19.5): a third spit
  whose cow was taken off raw, standing whole over it (`spit_raw_set_down_whole.png`); a cow's hind leg flayed on the rig
  in row F (`rig_flayed_leg.png`); and the Guillotine's Ponder with its lever (`guillotine_ponder_lever.png`).

#### 15.19.4 Cold air and other addons' freezing (package 16)

- `FanAirflow.processingAt` gives every fan current through a block and its processing there, not only the fastest.
  `CarcassRot.rateAround` asks at the torso's block and the one under it (a body lies low in air along the floor). A
  processing type tagged `#bloodandbones:preserves` stops rot; `#bloodandbones:chills` quarters it. These are tags on
  Create's `fan_processing_type` registry. `create_dragons_plus:freezing` is in `preserves` (optional). `chills` is
  empty, for other addons.
- Tests. `freezingFanKeepsACarcass` uses a real encased fan blowing through powder snow, Dragons Plus's own bulk-freezing
  set-up: a cow four blocks past the snow does not rot, and one in a plain fan's air beside it does.
  `dragonsPlusFreezersKeepACarcass`: Dragons Plus rates ice a passive freezer, which quarters rot, and a block another
  addon registers in Dragons Plus's `BlockFreezer` registry as freezing stops rot outright, though none of our own tags
  name it.

#### 15.19.5 What the review found

A review of this section's work made 19 findings about 15 things: three were made twice, and the two about the organ
count are one. Each was checked in the code and found real before it was fixed, and each fix has a test that fails
without it (or, for what only a client decides, a test of the server's half).

- **An organ could come out twice.** A carried piece counted its organs under `organs_taken`, a carcass lying on the rig
  under `organs_taken.<bone>`, and neither read the other, so a head carried, cut and set down on the rig gave its eyes
  again. Now one count, main's (`organs_taken:<bone>`, `Surgery.organsTaken(traits, bone, root)` and `putDown`, the same
  code as main's): a piece set down turns its own count into its bone's. The dot key never left this branch, so nothing
  reads it. `rigTakesEachOrganOnce`.
- **Table filters were lost** when a Butcher's Table or a Surgery Table was broken, and stranded in a slot that was no
  longer there when the rig came off. 15.19.2. `tableFiltersAreNeverLost`.
- **The Mangler unfolded a body it could not grind**, stroke after stroke. 15.19.2. `manglerLeavesABodyItCannotGrind`.
- **The rig by hand was full yield and unpaced**, quicker than a Mangler and richer than any station. 15.19.1: by hand is
  the hand path, and every cut waits `SurgicalRig.PAUSE`. `surgicalRigIsPacedWhoeverCuts`, `surgicalRigByHandGetsAHandsShare`,
  and `cowDownEachPath` at 256 RPM and by hand.
- **Idle machines looked for work at many times the old cost.** 15.19.2.
- **The other hand lost its turn** with a Cleaver at an empty Butcher's Table or the Meat Hook at an empty spit: the
  client consumed the click. Both sides now decide alike from what both can see: at an empty table, a Cleaver lets a
  piece the table would take in the other hand go on first; the hook swings only at an empty spit, while dragging or
  with no piece in the other hand to skewer. `theOtherHandHasItsTurn` checks the server's half.
- **A body could be carried off the spit** once only its torso was left. 15.19.3. `spitRoastKeepsAHeavyBodyABody`.
- **The rig destroyed the hide.** It flays now, as one of its cuts (15.19.1); the Mangler's hide share is read too.
  `rigTakesTheHideAndLeavesAHungBodyAlone`, and the rig's hide in `cowDownEachPath`.
- **The Mangler's loot came on top of its share**, so it could beat a station. 15.19.1. `manglerKeepsTheLeastOfAnyMob`.
- **Package 10 was claimed closed.** It and package 5 are marked partly built here and in the audit, with what is left.
- **The loot was rolled for a mob in its default state** (a black sheep dropped white wool). 15.19.1.
  `manglerGrindsInTheMobsOwnDrops`.
- **`piece_slot` knew only the shipped keys.** 15.19.2. `pieceSlotTakesAnySlotTheDataGives`.
- **The Ponder said every machine works faster the faster it turns**, and never showed the Guillotine's redstone. 15.19.3.
- **A Cleaver on the rig did nothing and said nothing** when only a hung body or a filtered one was over it. A hung body
  is not lying on it now; a click with nothing to take lays the blade down, and one the filter turned away says so.
- **A raw carcass came off the spit as six pieces heaped at one spot.** It comes off whole (15.19.3), looked at in the
  showcase. `spitRoastTakesAWholeCarcass` now checks one body, six pieces, five joints, the head off the body and the
  legs apart.

- The suite is 442 tests.

#### 15.19.6 Merged with the organs and the checks (verified)

The branch met main's organs (15.16 to 15.17b) and checks (15.18) on the integration branch, as the start of 15.19 said
it should.

- **One organ system.** The rig's organ step is main's harvest now. `SurgicalRig` asks `Surgery.organsLeft` (main's
  `Organs.held`, less what was taken) and hands the cut to `Surgery.harvest`: for a piece laid on the table the four-argument
  one, as before; for a carcass lying on its top a new `Surgery.harvest(level, surgeon, table, blade, carcass, bone)`, the
  body of main's carcass branch, which counts under `organs_taken:<bone>`. The rig walks main's order (`Surgery.organBones`:
  the torso, then what is still attached, and a resting carcass's rest poses) and asks its filter about each bone before
  cutting. `Surgery.organs(kind)` (the machines' fixed heart, lungs, stomach and two eyes) is gone, and so is the rig's own
  organ item and count. So a cow gives its rumen at the rig too, a skeleton its marrow (dry, the blade left clean), and an
  organ never comes out twice whichever way the part reached the table. The organ step runs before the rig unfolds a
  resting carcass, so organs come out of one folded to rest where it lies, as main's did; the hide, limbs and meat after
  them unfold it first, as the machines' did.
- **Which carcass.** A click with the blade on an empty table goes to the rig whenever a carcass lies on its top
  (`CarcassButchery.lyingOn`: not one hanging over it). Main's wider zone (`Surgery.carcassOn`, the Assembly Frame's) is
  still what the four-argument `Surgery.harvest` uses when it is called with nothing laid on the table.
- **Tests changed to fit.** The machines' rig tests count six organs for a cow (four in the body, two eyes) and flay on
  the seventh cut; `cowDownEachPath` also expects the rumen, and no Gland from any other path;
  `surgicalRigByHandGetsAHandsShare` marks the body's organs out by the organ data's count. Main's
  `heavyCarcassOrgansOnRig` and `restingCarcassOrgansOnRig` click once each of the rig's pauses (they clicked six times in
  one tick), expect the rig's next cut after the organs to be the hide rather than the Cleaver laid down, and the resting
  one checks the carcass is still folded when its organs are out. `guillotineLimbGoesToAMinionAndAWallHook` (15.18) now
  drops the Guillotine with a redstone pulse, as 15.19.3 has it.
- **Showcase.** The debris shot is found by its view, not a fixed index, now that both sides added views.

### 15.20 Materials, bloodless mode and the decoration leftovers (audit packages 14, 9 and 17)

What docs/BRIEF-AUDIT.md packages 14 (blood, Soul Blood and the materials as written), 9 (finish bloodless mode)
and 17 (decoration leftovers) asked for, built on main after 054cdfd. Package 14 and package 9 are closed (package
14 leaves the owner one question, in 15.20.2: a press as a second way to congeal); package 17 is closed except the part
that waits on decision 9 (below). Package 2's line about hand-written recipes is closed too.

#### 15.20.1 Recipes come from datagen

- All 78 hand-written recipe files are now written by `datagen/BBRecipeGen`, run through Registrate's recipe
  provider, as section 8 decided ("never hand-written"). Create's processing recipes use Create's own builders
  (`StandardProcessingRecipe.Builder`, `SequencedAssemblyRecipeBuilder`), so they come out in whatever format Create
  reads, in a folder named for their type as Create's do (`filling/blood_steel_ingot`); the CDG basin fermenting
  recipe goes through the same builder with CDG's own recipe class. Crafting recipes are the game's own recipe
  objects written with their own codecs; the palette's stairs and slabs use Registrate's helpers.
- The generated files were checked against the hand-written ones before anything changed: all 78 matched, apart
  from the new ids of the 11 processing recipes (and a `loops: 1` Create leaves out as its default).

#### 15.20.2 Soul Blood, the Blood Diamond and the tags (package 14)

- **Tags.** Soul blood has its own tag, `c:soul_blood` (and `c:buckets/soul_blood`), apart from `c:blood`. Liquid
  experience is our own tag, `bloodandbones:liquid_experience`, holding Create Enchantment Industry's fluid: a point
  of experience a millibucket, as Enchantment Industry counts it. It is not `c:experience`, which NeoForge counts at
  20 mB a point: with both in one tag, a recipe by the millibucket costs twenty times less in one mod's fluid than
  another's (Enchantment Industry keeps its fluid out of the common tag too, and converts other mods' by its own
  unit data map). The Blood Diamond and the mixing shortcut ask for our tag; the Vent Arm pays any liquid
  experience at its own rate (`Vent.mbPerPoint`: Enchantment Industry's unit for its own fluid and those in its
  data map, a point a millibucket for our tag, 20 mB for the rest of `c:experience`).
- **Every consumer matches the tags**, not our fluid: every recipe's fluid ingredient is a tag ingredient (Create
  reads NeoForge's `SizedFluidIngredient`, which takes tags; Create's own honey compacting does the same); organic
  implants run on `c:blood` and cybernetics on `c:soul_blood` (`ImplantItem.fuelTag`); perfusion takes any
  `c:blood` (`Necrosis.perfuse`); the throttle, its gauge and the Vent Arm's spill read the tags
  (`BBFluids.isBlood`, `isSoulBlood`); a minion is fed and woken, and the Surgery Table takes, any bucket in
  `c:buckets/blood`; the Blood Trough was already on `c:blood`.
- **The soul blood line** (section 8's decided chain): a Diesel Generators Basin Lid on a basin sets 250 mB of
  blood into **Congealed Blood** in ten seconds (`createdieselgenerators:basin_fermenting`); an Encased Fan blowing
  through soul fire haunts it into a **Soul Clot** (`create:haunting`); a Mechanical Mixer over a superheated basin
  melts the clot back into 200 mB of soul blood (`create:mixing`). Four fifths of the blood comes back as soul blood.
  For a while the congealing was a Mechanical Press over a heated basin (the brief names "pressing"); a review
  found that overrode section 8 and 12's decision, so it is the Basin Lid again. **For the owner:** whether the
  press should come back as a second way to congeal.
- **The shortcuts, much weaker.** Superheated mixing of a bucket of blood, soul sand and 100 mB of liquid experience,
  and CDG fermenting of a bucket of blood with soul soil and two nether wart, each give 100 mB: a tenth, against the
  full line's four fifths. Under the same lid a basin tries the recipe with the most items first (Create's
  `BasinOperatingBlockEntity`), so wart and soul soil in with the blood go the shortcut way, and blood alone sets.
  The trickle path (nether mobs bleeding soul blood) is unchanged.
- **The Blood Diamond** is a sequenced assembly, as the brief and section 8 have it: a diamond through a Spout of
  1000 mB of blood (`c:blood`), then a Spout of 1000 mB of liquid experience (`bloodandbones:liquid_experience`), one pass, through an
  **Incomplete Blood Diamond**. The one-fill recipe of soul blood is gone. Soul netherite keeps section 8's decided
  form (a Spout of 1000 mB of soul blood, then a Deployer with a super experience block), now on `c:soul_blood`.
  The Blood Diamond's advancement now follows Blood Steel, not Soul Blood.
- **The gauge** (`client/BacktankGauge`), styled as Create's air gauge (`RemainingAirOverlay`) and in its place, to
  the right of the hotbar's top, drawn with Create's `GuiGameElement`: the worn tank, what it holds in buckets to a
  tenth, and a bar of the fluid's own texture in its own tint, as far along as the tank is full. Unlike Create's it
  is always up while a tank is worn, and while any powered implant is fitted (with no tank, a faded copper tank and
  0.0 B). Under the bar sit the powered implants, half size on a dark strip, dimmed when the tank does not feed them, so a blood-fed
  Flesh Arm reads as running and a Hydraulic Arm on the same tank as not. With implants and under a tenth full, the
  reading flashes red as Create's does. While Create's own air gauge is up (a Create backtank's air breathed under
  water or in lava, checked as `RemainingAirOverlay` checks it), this one moves up a row, clear of Create's even
  where Create draws a netherite tank lower. The backtank's item bar is the fluid's colour too (its texture's average,
  tinted), no longer always red.
- Items: Congealed Blood, Soul Clot and the Incomplete Blood Diamond, each with a clean copy of its texture for
  bloodless mode (essence brown, the item model's `bloodandbones:bloodless` override); descriptions, the JEI soul
  blood pages (on the bucket, the clot and the congealed blood) and the implants page on the gauge.
- Tests (`MaterialsTests`): `soulBloodTaggedApart`; `fillingRecipesTakeTheTags`, `soulBloodLineRecipes` and
  `sequencedMaterialsRecipes` read each recipe back from the loaded recipes (type, item ingredients, fluid tag and
  amount, heat, outputs and amounts, the full line at least five times either shortcut, the old one-fill diamond
  gone, the press's congealing gone); `spoutsMakeABloodDiamond` runs a real Spout over a Depot (blood, then
  experience makes the diamond; a Spout of experience first leaves a diamond alone); `basinLidSetsBlood` (and a
  basin with no lid does not), `fanHauntsCongealedBlood` and `mixerMeltsSoulClot` run the line on the machines;
  `ventPaysLiquidExperienceAtItsOwnRate` (Enchantment Industry's a point a millibucket, the rest 20 mB, and a real
  shot); `implantsRunOnTheTags` (a Flesh Arm on blood and not soul blood, a Hydraulic Arm the other way, perfusion
  only with blood, water runs neither). `soulBloodTaggedApart` checks Enchantment Industry's fluid is in our tag
  and not in `c:experience`.

#### 15.20.3 Bloodless mode finished (package 9)

- **Words** (`BloodlessWords`): a carcass is a wreck ("constructs not corpses": a working body is a construct, a
  dead one a wreck), carcass armour plated armour, severed detached, amputation replacement, a stump a socket, flesh
  plated, necrosis wear; vanilla's Rotten Flesh keeps its name. Own bloodless text where the rewording would read
  badly: the Plated Arm (Flesh Arm), Cabled Leg (Sinew Leg) and Furnace Hopper (Furnace Stomach) and what they say,
  the four carcass armour pieces as Plated Helmet and so on, a piece's keeping as Sound, Worn and Corroded and a
  skinned one as Stripped, the flesh set bonus, the implants JEI page. Organs already had theirs (Lens, Pump,
  Bellows, Hopper).
- **Looks.** A carcass is drawn as a wreck of a construct (`client/ConstructPlating`): each mob texture's plated
  copy, made once on the client and kept (reloaded with resources), in three tones of cold steel taken from the
  texture's own light and dark, with a dark seam wherever the tone drops and rivets every few pixels, so a cow's
  patches read as a cow's plates. A skinned one is the darker frame under the plating. Coats (wool, a llama's decor)
  are plated too, and so are minions, which are drawn the same way. The Plated Arm and Cabled Leg, the severed limbs
  and the organs have plated icons, and the two implants a plated look on the body (`BodyRendering.worn`). The bits
  that come off a carcass when it lands, rolls or is struck (the carcass block's own particles, red specks of meat)
  come off as steel, and a blood stain scuffed away (drawn as nothing) leaves specks of mud, not blood: the
  showcase caught both as red on the ground beside the plated wrecks.
- **Sounds.** Every wet sound the mod plays is its own event (`BBSounds`), with a metal twin
  `bloodless.<name>`: vanilla anvil, chain, iron and grindstone sounds pitched in sounds.json, and a chain rattle
  laid over the heavy ones (thud, sever, crumble), for real files to replace later. The mod no longer plays
  vanilla slime and honey sounds itself: those calls, and the Gut Chain's and blood stains' sound types, now play
  `flesh.*` and `stain.*` events (squelch, squish, slide, tear, step, slap), the trait effects' shoves play
  `flesh.lunge` (a lunge), `flesh.fling` (the launch action) and `flesh.slap` (an area shove, a thud on the other),
  and Bleeding's drip is `blood.drip` (bloodless, oil dripping). The game always plays the wet one;
  a client in bloodless mode hears the twin, where the wet one was, as loud and at its pitch
  (`client/BloodlessSounds` on NeoForge's `PlaySoundEvent`, reading the asked volume and pitch through a client
  mixin accessor). Twins have their own subtitles ("Wreck clanks", "Plating is cut").
- **The rule, tested.** `BloodlessTests.onlyPresentationReadsBloodless` reads every class of the mod with ASM (from
  the mod file's scan data) and lists each method that reads the client setting or the game rule. It fails if any
  is outside presentation code: the client package, the setting and the game rule themselves, the tests, and four
  named methods elsewhere (the fluid's tint and fog, Create's description cache, the carcass armour's texture, and
  the Gut Chain's renderer). It also fails if the mod reads any vanilla sound event or block sound type whose name
  starts with `SLIME_` or `HONEY_`, or the water drip, and checks it really found the known readers and a known
  vanilla sound (Bleeding's hiss), so an empty scan cannot pass. `bloodlessTextIsClean` reads the language file and fails on any
  reworded line that still shows blood, gore, guts, a carcass, flesh, severing, a stump, necrosis, sinew, maggots
  or stitches, and checks the names above. `everyWetSoundHasATwin` checks every twin is registered, in sounds.json
  with metal under it and a subtitle, and that the Gut Chain and stains sound through them.

#### 15.20.4 Decoration leftovers (package 17)

- **The Butcher's Hook takes every body part** (brief: "accepts any carcass or body part as a rendered
  attachment"): whatever is in the item tag `bloodandbones:hangs_on_hooks` (data): carcass pieces, severed limbs,
  organs, scraps, the mod's meat, offal, fat, hide and clots, vanilla meat and fish, heads, rotten flesh, bones,
  leather, hides, rabbit's feet, spider eyes, ink sacs, feathers, membranes, slime balls, ghast tears, blaze rods
  and scutes. A carcass piece is drawn as the body part it is, as before; anything else as its item, speared through
  its top on the hook's point, swaying and twisting a little. What is in `bloodandbones:drips_on_hooks` (severed
  parts, organs, scraps, raw meat, offal, hide) drips for a minute once hung, soul blood from a nether mob's parts,
  nothing from a skeleton's. Where it came from is read from the item's source stamp, whatever the item (severed
  parts and organs, scraps, raw hides all carry one). A player hangs anything in the tag by using it on the hook.
- **Not built, decision 9:** heavy whole carcasses on the wall hook. They are bodies, never items (section 15.1
  #5), and whether the hook should take them is the owner's call.
- **Bloody Train Casing:** Create's train casing (`railway_casing`) filled with 250 mB of blood, joined up top and
  sides as Create's is (`BuilderTransformers.layeredCasing`, two connected sheets); Create's four sheets with blood
  painted over. Bloodless: Create's own (`BloodlessSwap.RAILWAY`).
- **The small stained palette** (section 7): Create's cut calcite, polished cut calcite, cut calcite bricks and
  small calcite bricks, white as a slaughterhouse wall, spout-filled with 100 mB of blood each, blood run into the
  joints; a stonecutter turns the stained cut calcite into the other three, and each has stairs and a slab (crafted
  or cut). Bloodless: Create's plain calcite (`BloodlessSwap.PALETTE`), named Stained.
- **The grazers' scrap material is Brawn**, not Hide (grazers, horses and pigs): cow boots of scraps alone read
  "Cow Brawn Boots", and "Hide:" on the tooltip means a hide really is fitted. The data id `hide_plate` is unchanged.
- Tests: `butcherHookTakesEveryBodyPart` (a player hangs a heart by using it; the hook takes a severed arm, scraps,
  beef, a zombie head, a bone, a skeleton's heart and scraps, a piglin's scraps and a hoglin's raw hide, and refuses
  a diamond, stone and a Cleaver; the cow's heart leaves a red stain, the bone and the skeleton's heart and scraps
  none, the piglin's scraps and the hoglin's hide soul blood); `decorationRidesAContraption` now pushes the train
  casing with a stained stair on it; `craftCowBoots` checks the name; the new recipes are read by
  `fillingRecipesTakeTheTags` and loaded in `recipesLoad`.

#### 15.20.5 Looked at in the showcase

Row F (normal and bloodless): the soul blood line (a Basin Lid on a basin of blood, a basin of what it set beside
it, the fan and the mixer), the Blood Diamond on its depot, the train casing beside Create's and the stained palette
with its stairs and slabs, and a wall of hooks hung with a severed arm, a heart, a piglin's scraps dripping soul
blood, a zombie head, cow scraps, a pig's leg and an eye; `showcase_gauge.png`, the gauge over a copper tank of
blood running a Flesh Arm and a Sinew Leg beside a dimmed Hydraulic Arm; `showcase_gauge_diving.png`, diving on
Create's copper backtank with a Hydraulic Arm fitted: Create's air gauge in its place, this one a row above it. In
the bloodless run the carcass shots show the plated wrecks, and the debris shot what comes off a carcass and a
scuffed stain: steel and mud.

- The suite is 432 tests (14 new: ten in `MaterialsTests`, three in `BloodlessTests`, one in `DecorationTests`).

#### 15.20.6 Review (fixed)

- The Butcher's Hook found where a hung part came from only on severed parts and organs, so scraps and raw hides
  counted as bleeding red: a skeleton's scraps dripped blood and a piglin's red, not soul blood. It reads the
  source stamp from any item now; the test hangs a skeleton's scraps (no drip, no stain), a piglin's scraps and a
  hoglin's hide (soul stains).
- Create Enchantment Industry's liquid experience had been put in `c:experience`, which counts 20 mB a point where
  Enchantment Industry's is a point a millibucket: the Blood Diamond would have cost twenty times less in another
  mod's experience, and the Vent Arm paid a twentieth of a tank of Enchantment Industry's. Our own tag and each
  fluid's own rate now (15.20.2).
- The trait effects still played vanilla slime sounds (a lunge, the launch action, a shove and its thud) and
  Bleeding a water drip, none with a metal twin; the rule 4 test listed only some slime and honey sounds. Own
  twinned events now, and the test takes every slime and honey sound (15.20.3).
- The plated texture cache built a string key on every lookup, for every bone of every carcass each frame in
  bloodless mode; two maps keyed by the texture now, so a lookup builds nothing.
- The congealing had moved to a heated press, against section 8's decided Basin Lid (15.20.2).
- The gauge sat exactly where Create's air gauge is and stayed up with only an implant fitted, so diving on
  Create's backtank drew the two over each other; it moves up a row while Create's is up (15.20.2).

#### 15.20.7 Merged with the machines, the organs and the checks (verified)

- **Recipes.** Nothing on the integration branch had added or changed a recipe file since 054cdfd, and the machines
  added none, so nothing had to move into `BBRecipeGen`. After datagen every one of the 78 recipe files main had is in
  the generated set: 27 the same, 49 changed only as 15.20.1 and 15.20.2 meant (fluids asked for by tag, the shortcut
  yields cut to 100 mB, Create-style ids), and two replaced on purpose (the one-fill Blood Diamond by
  `sequenced_assembly/blood_diamond`, the old soul blood mixing by `mixing/soul_blood_from_soul_sand`). The three
  generated cooked meat recipes are there too: 108 in all.
- **Create Diesel Generators is a required dependency** (`neoforge.mods.toml`, both sides, as Dragons Plus and
  Enchantment Industry are), so the Basin Lid recipe needs no `mod_loaded` condition.
- **Bloodless mode over the others' work.** The Mangler's grind (15.19.1) is a wet sound with a twin like the rest,
  `bloodless.machine.grind`, the grindstone and chain the machines had given `machine.grind_clean`, which it replaces:
  `BloodlessSounds` is this section's, for every twin. Main's organ harvest played vanilla slime and honey sounds, and a
  minion's steering slap a slime squish: they play `flesh.squish_small`, `flesh.slide` and `flesh.slap` now.
  `Organs#name` reads the setting to give an organ its file's `bloodless_name`, which is words, so it is on
  `onlyPresentationReadsBloodless`'s list. The machines' bloodless text for the Surgical Rig said "carcass": "wreck".
- **The Butcher's Hook takes a Gland** (main's organ item) and it drips: `bloodandbones:gland` is in both hook tags, and
  `butcherHookTakesEveryBodyPart` hangs a cow's rumen and looks for its red stain.
- **Showcase.** This section's row is row G, north of the decoration, beside the machines' row F to the west; its four
  views come after the machines'. The gauge shots come after main's longer minion and effects timeline (ticks 345 and
  395 of that stage).
- The suite is 504 tests.
### 15.21 Tasks, stage A as built: the fitness, worked out without a world (verified)

docs/NEXT.md item 1 is the design the owner asked for: no jobs; any task from one list to any minion, each better or
worse at it by what it is built of. Stage A (1.10) works the numbers out with no world. Nothing in play reads them yet:
the job system is as it was until stage B, but for three fixes 1.11 found (below).

- **The task list** (`minion/MinionTask`): the sixteen tasks in the order a task screen lists them, each with its kind
  (fight, tend, fetch or work; Idle none), the anchors it allows (home; with its maker too for Idle, Guard, Hunter, Medic
  and Courier), and the test of the body that is code: something to strike with (an arm that is not folded, or a head to
  bite with) for Guard, Sentry and Hunter; a detonating organ for the Sapper (`MinionSapper.hasDetonator`); and, for the
  handwork, anything its grip table allows (1.2's table, a column a task). What a datapack may retune is its
  `MinionTask.Data`: kind, anchors, reach and the most its maker may set it to, the stats its fitness reads, the grip
  table, its tool (items, required, carried, the grips while held), whether it stores its takings, the numbers its levers
  scale, and the surgeon's `needs_surgeon_head`. The defaults are today's constants;
  `data/<ns>/minion_task/<task>.json` overrides them field by field, "numbers" and the tool's fields one by one. Datagen
  writes the sixteen files from the defaults (`MinionTaskDataProvider`), so a datapack sees what can be changed and a
  missing file changes nothing.
- **Dispositions** (`minion/MinionDisposition`): the eleven of 1.2 by kind, by task (territorial's Guard and Sentry),
  with the maker (loyal) and by night or day (nocturnal), in `data/<ns>/minion_disposition/<name>.json`, written the same
  way. A head names one by plain name ("meek") or a datapack's "ns:name"; one not loaded counts as none. Both kinds are
  loaded by `PartsData` and never sent to clients (`PartsData.Kind.synced`): the fitness is worked out on the server.
- **What the build brings** (`MinionStats`): `holders`, what it holds things with: each arm's grip (a villager's pair of
  arms is two hands, its data's "hands"), the head's mouth, and on a body of at least four legs the legs' grips
  (`GRIPPING_LEGS`: a zombie standing on a wolf's two front legs uses them to stand); `torsoWeight`; `knacks`;
  `disposition` ("none" when the head names none, "mindless" with no head). A seeing head's sight counts its build's
  follow-range traits as its attribute would take them (Keen Eye, Relentless); a blind head stays at 4 and a headless body
  at 2 (`MINDLESS_SIGHT`, 8 before) whatever they add; an echolocate or tremor sense, in the head's data or a passive
  trait (a bat's Echo Ear, a warden's head), finds its way at least 12. `fights()` is false for a body with no arm and no
  head. These three are the only changes in play.
- **Knacks** (`MinionData.knacks`, `MinionStats.knackParts`): a part's "knacks" merge key by key as trait lists do, the
  general key's layers and then the specific key's, each layer's own map and then each variant it matches, in order. A
  build's knack for a task is, for each part slot, the best its pieces give (one naming none counts 1; of heads the first
  only), multiplied together and by its traits' `task_knack`s, held between 0.25 and 2.5; where each part came from is
  kept for the screen. Old data's "jobs" with no "knacks" reads as knacks (the first 1.5, the rest 1.25, companion
  dropped, bodyguard a guard, scavenger a courier), and `MobGroup.parse` logs such a file once (`MobGroup.OLD_JOBS`).
- **The fitness** (`minion/MinionFitness`), pure. A `Body` first: the stats after the traits `ActiveTraits` would give
  the minion (its parts', its organ's and, on flesh, its hides'): speed with the +40% cap on rises, health within 6 to
  150, drag strength, blows its traits make harder, storage (a chest's only with a chest fitted), innate shots; a
  detonator; what its head does with no tool; whether its head is a surgeon's; its knacks' parts and its disposition.
  Then a `Row` per task: fitness = knack × disposition × main × √second, held 10% to 200%; the number before it was held;
  why it cannot (a lang key); what it waits for (a lang key); the fitness a missing tool would give where that differs;
  and each factor with its stat, its value and its `Source`s (a piece with its part and grip, a trait at its level, an
  item, or a rule: blind, mindless, bite, more, strike rate, berserk, no ranged...). A required tool (the butcher's blade,
  the herder's bait, the medic's potions) is taken as had, since the minion waits for it; with the mobGriefing rule off a
  hunter waits for that. Blow is the hardest strike times the strike rate (+15% for each striking arm past two, to +60%),
  or the bite with no striking arm; pull is the square root of the torso's weight over a cow's, over what drag strength
  leaves of the slowdown.
- **The levers**, pure, for stage C's goals: `quicker`, `longer`, `spread`, `yieldShare`, `towing`, `workingDrain` and
  the tasks' own (`catchTicks`, `strokeTicks`, `admireTicks`, `tendTicks`, `throwTicks`, `digTicks`, `lookTicks`), each
  today's constant at 100%.
- **The surgeon, the owner's call (1.5).** Built both ways, one data switch apart. The default is the recommendation: any
  minion with a hand may cut, and its fitness sets the stump's price (`MinionFitness.stumpBuckets`: one bucket at 150% and
  over, two from 75%, three below). A datapack's `minion_task/surgeon.json` with `"needs_surgeon_head": true` gets the
  brief's letter: only heads whose data says `"surgeon": true` cut (the villager family, the witch among them, and the
  illager family), while anyone may still tend (`MinionFitness.mayCut`). The ritual reads it in stage D.
- **The data.** Every shipped head has its knacks per 1.6's table beside its old jobs list; the villager's professions
  are 1.5 at their task, the rest of them (librarian, cartographer, mason, unemployed) couriers, a nitwit dim and no
  surgeon; the zombie villager's shaky hands are a surgeon's 0.5 over its family's. Front legs have their grips: hooves on
  a quadruped, paws on canid, feline, bear, amphibian and small prey, claws on arthropods, tentacles on the tentacled and
  cephalopods. The sniffer's Olfactory Bulb gives a new trait, Truffle Nose (`task_knack` Digger ×1.5).
- **Changed from the design, and why** (each recorded where the design says it, in docs/NEXT.md):
  - The old `jobs` lists stay in the shipped data beside the knacks until stage B. Today's jobs cannot be read from the
    knacks without changing what heads offer and wake as (1.6's table drops companion, bodyguard and scavenger and
    reorders: a cow's head would wake as a courier, a wolf's as a herder, a horse's as a hauler, a professional villager's
    would offer the biped's courier), and stage A changes nothing in play. Stage B deletes them with the job system.
  - The zombie villager keeps its `pace` beside its knack, for `AttendTable` until the tending reads the fitness.
  - The villager's own file no longer restates its family's meek: a mob's own file wins over its family's variants, so
    the nitwit's variant could not make it dim.
  - Four worked examples of 1.2 left the traits out; worked out with them (a rabbit's hide and a wolf's legs are Swift,
    a cow's torso is a Beast of Burden, a spider's torso has its mob's 16 health), 1.2 now says Courier 146%, Hauler 138%,
    Guard 72%, Herder 200% (216% before the cap) and Hunter 135% where it said 143%, 114%, 65%, 180% and 124%. The summary's
    "six tasks with me" is five, as 1.1's table has it.
  - Four test descriptions in 1.9 said more than the formula does: a fisher with no rod fishes by hand, so it does not
    wait (it is shown a rod's fitness); a nitwit is braver than an unemployed villager at the fights (dim 0.75, meek 0.5);
    a blind hunter's number does not read sight; eight arms give a butcher 1.6 × √1.6, since its second is its blow.
  - More holders count only of the best grip, so a mouth that fishes as well as two hands is not a third hand; a blind or
    headless body's sight takes nothing from follow-range traits.
- **Tests** (`gametest/MinionFitnessTests`, 15): `fitnessMatchesItsFormula`, `oneHundredIsToday`,
  `cannotOnlyWhenTheBodyCannot`, `missingToolWaitsNotCannot`, `villagerAndPillagerHeadsAreTheBestSurgeons`,
  `surgeonHeadFlagDecidesWhoCuts`, `professionSetsItsKnack`, `blindHeadIsPoorNotBarred`, `moreHandsWorkFaster`,
  `pawsAndHoovesPickPoorly`, `knacksMergeAcrossLayers`, `oldJobsListReadAsKnacks`, `dispositionsScaleTheirKinds`,
  `moddedMobWorksFromItsArchetype` (a made-up mob with only an archetype, on a copy of the wolf's rig:
  `RigManager.addTestRig`, looked up like `PartsData.addTestMobFile`) and `taskKnackTraitCounts`. `dataLints` knows the
  new effect works on minions only. Every old test passes unchanged.
- **On screen** (`showcase_tasks_0.png`, new): the minion row's shot with the chat on, and in it each shown minion's four
  best tasks and what it cannot do, worked out on the server from its data as the task screen will (`DevShowcase.fitness`,
  the tasks by their own names). The cow on rabbit legs is Herder 200%, Tender and Courier 146%, Hauler 138%, and cannot
  be a Sapper or a Surgeon, as 1.2 now says; the whole cow Herder 125%; the zombie with a pig's head on a rabbit's haunches
  and one arm Hunter 152% (a swine's knack for it); the legless cow a Digger at 100%; the zombie with a cow's head and a bow
  Herder 144% and Surgeon 125%. The other shots are as they were.
- The suite is 466 tests (451 and the fifteen above), and passed two runs in a row, the fifteen three times over as well.


### 15.22 Tasks, stage B as built: tasks in the jobs' place, and the task screen (verified)

docs/NEXT.md item 1 is the design; stage B (1.10) switches the game over from jobs to tasks. Any of the sixteen tasks can
be given to any minion; its fitness (§15.21) says how well it does it, and now the screen, the status line, the wake rule
and the blood it uses at work read it.

- **Each task's goals** (`minion/MinionTasks`, renamed from `MinionJobs`): given to every minion, each working only while
  it has its task (`MinionEntity.hasTask`). Companion and bodyguard folded into Guard and Idle done with the maker; the
  scavenger into the Courier, whose `Fetch` goal picks up loose items within its reach of where it works (and within its
  sight, as the scavenger's did): holding something, only items like it (its sample), brass with a filter only what the
  filter passes. At home the container by home takes them (`MinionGoals.Deposit`, for every task whose data says
  `"stores"`); with its maker they go into the maker's hands. The farmer keeps `Collect` for what it reaped. The Tender
  can be set but its goals are stage E's: until then it keeps home.
- **Anchor and reach.** `MinionEntity` keeps `TASK`, `ANCHOR` (home, or the maker) and `REACH` (0: the task's own) as
  synced data and saves them beside `Home`. `reach()` is what its maker set, held to 2..the task's `max_reach`, else its
  own (`MinionTask.Data.reach`); every goal that searched a fixed range reads it (the guard's and sapper's monsters, the
  sentry's range, the herder's keep (its look-out scales with it, 20 at 8), the fisher's water, the digger's ground, the
  barterer's gold, the butcher's carcasses, the hauler's carcasses and hooks, the farmer's crops, the medic's patients,
  the courier's items, the sapper's banner). `workingMaker()` is its maker while its task is done with them and they are
  in the same world within 64 blocks; `centre()` is then their feet, else home. Farther off, or elsewhere, it works at
  home (`StayNearHome` takes it back there).
- **With me.** `FollowMaker` keeps a minion near its maker as vanilla's `FollowOwnerGoal` keeps a wolf (setting off at 6
  blocks and stopping at 3, as the companion did, its path renewed every half second). `DefendMaker` is the wolf's two
  goals, for Guard with the maker: what hurt the maker, and what the maker hit, each new blow once and while fresh (5 s),
  never the maker's own side, a tamed animal or horse, a player the maker may not hurt, a creeper or an armour stand. Idle
  with its maker only fights back (`HurtByTargetGoal`, every task's). The hunter hunts prey within its reach of the maker,
  the medic throws at the hurt within its reach of the maker, the courier hands the maker what it fetched round them.
  `TeleportEffect`'s blink to its owner works for a minion working with its maker; `SocialGoals.SenseTarget` hunts by a
  sense for any fight task but a guard with its maker, within its reach of where it works.
- **A body with no head** (`MinionGoals.TouchTarget`, `Feel`) takes for its target only what touches it (within half a
  block of its body): what hurt it, and on Guard or Sentry any monster, on Hunter prey (where mobs may grief), and strikes
  it once a second, never going after it. **A sentry with no ranged attack** (`MinionTasks.SentryStrike`) takes a monster
  it can strike from its post and strikes it there, never taking a step.
- **The wake rule** (`MinionTasks.wakeTask`): its fittest task at home that waits on nothing, holding nothing, ties to the
  list's order; never Hunter or Sapper, and not the Tender until its goals are built; with none, Idle. Its maker's action
  bar: "Woke as a Surgeon (200%)".
- **Tasks change only when its maker sets them.** `keepValid` is gone: a missing tool makes it wait (the butcher's goal,
  the herder's and the medic's look for theirs), and the status line says for what. `MinionTasks.keepPossible`, every
  second: a data reload that leaves its body unable to do its task sets it to Idle at home and remembers why for the status
  line ("it can no longer be a Surgeon: No hand to hold a surgeon's blade"); one that no longer allows its task with the
  maker brings it home.
- **Fitness on the minion.** `fitnessBody()` is `MinionFitness.body` worked out once with its stats (again when its build
  or the data changes); `row(task, anchor)` and `fitness(task)` apply what it holds and carries, the time of day, the
  mobGriefing rule and a fitted chest when read. Ahead of stage C, because stage B removed or shows them: blood at work is
  25 mB a minute ÷ its fitness (12.5 to 50; brass a quarter), read once a second; the surgeon tends a heart every 5 s ÷ its
  fitness, never under 2 s (the zombie villager's `pace` is gone, its shaky hands are its knack of 0.5); and
  `Surgery.surgeonAt` counts only a minion on the Surgeon task that `MinionFitness.mayCut` allows, so the surgeon file's
  `needs_surgeon_head` (docs/NEXT.md 1.5, the owner's call; false by default) already decides who cuts. The fittest of
  several surgeons and the stump's price are stage D's.
- **The status line** (`MinionTasks.status`, `TaskWords`): "Farmer 120% at home, blood 300 of 780 mB" ("at its post",
  "at its table", "with its maker", "at home, its maker away"), then what it waits for, what its work waits on in the world
  for the next five seconds (the fisher's water, the digger's ground, the barterer's gold, the hauler's hook or rack, the
  container by home: `MinionEntity.idle`), or why a reload took its task. The Surgery Table's line while building names
  the best two tasks the build can do where it named jobs: the cow on rabbit legs, whole, is "15 health, speed 0.33;
  best: Herder 200%, Tender 146%" and its sockets.
- **Old saves** (`MinionTasks.fromJob`, docs/NEXT.md 1.8): a saved `Job` becomes its task as 1.8's table says, home kept;
  a companion whose body could not guard is Idle with its maker. A folded minion converts as it is set down.
- **The task screen** (`client/MinionTaskScreen`, `network/MinionTaskPayload`). The maker's crouching empty hand on the
  minion awake asks the server, which works out one row a task (`MinionTasks.open`) and sends them (`Open`): each task's
  fitness at the anchor it works at now where the task allows it, whether it can, whether it would wait, its hover lines
  already in words (`TaskWords.lines`, such as "Hands ×1.3: hand, 4 of them, Zombie arm"; "Sight ×1.41, at half weight: 48 blocks,
  Villager head"; "Knack ×1.5: Villager head ×1.5"; "Disposition ×1.25: Meek"; "At work: 12.5 mB of blood a minute"), the
  anchors it allows and its reach and most. Task and disposition files never go to clients. A stranger, a machine's
  stand-in (even the maker's own Deployer) and anyone on it powered down get nothing; crouch-holding on it down still folds
  it, and a plain click is still the status line. Its requests (`Set`: a task, an anchor, a reach, "Home here") are
  checked again (`MinionTasks.handle`): the maker, not a stand-in, within 8 blocks, it awake, the task one its body can do,
  the anchor one the task allows, the reach 0 or within its bounds; the server then sends the rows again, and an open
  screen is brought up to date (a closed one stays closed). A test's stand-in maker, which has no connection, is shown
  the screen through `MinionTasks.Viewer`.
- **How it looks.** It is drawn in Create's own schedule frame (`AllGuiTextures.SCHEDULE`), each task a Create schedule
  card (`SCHEDULE_CARD_*`), the four groups under brass headings, the task now marked with the schedule's brass strip and
  pointer; the bar, the percentage and the word (Hopeless red to Born to it teal), "Cannot" and its reason in small print;
  Create's `IconButton`s for At home (a bed) and With me (a head), Home here (Create's target icon) and done, and its
  `ScrollInput` and `Label` for the reach. The list scrolls as the schedule's does, fading at its ends.
- **Data.** The shipped heads' old `jobs` lists are gone (47 files), leaving the knacks; the zombie villager's `pace` is
  gone. A third party's `jobs` list is still read as knacks, logged once.
- **Words** (`BBLang`): the tasks' names and what each does, the words for fitness, where it works, the waits and what
  its work waits on, the factors, stats, grips, parts and rules of a row, the screen's buttons and headings, the status
  lines. Bloodless mode has its own wording where the usual rewording is not enough (the Dismantler takes bodies apart,
  the hunter downs prey and leaves them whole, the hauler drags bodies to a Draining Rack, the surgeon does the ritual's
  work, the Tender carries essence to fallen constructs), checked by `taskWordsReadBloodless`.
- **Deleted:** `MinionStats.JOBS`, `HANDS`, `SIGHT`, `HARVESTING`, `COMPANION` and its `jobs`; `MinionJobs.offered`,
  `needsMet`, `wakeJob`, `keepValid`, `startJob` and the fixed ranges; the crouch-click cycling and `job_now`;
  `MinionEntity.jobKey`, `job()`, `setJob`, `hasJob`; `AttendTable`'s `pace` and `MinionGoals.headScalar`.
- **Changed from the design, and why** (each recorded where the design says it, in docs/NEXT.md 1.3, 1.9 and 1.10):
  - The screen scrolls: sixteen rows and four headings are taller than the schedule frame's list. It opens scrolled to
    the task now. A reach scrolled to is sent once left alone half a second, or as the screen closes. Clicking a row sets
    that task at the anchor it works at now if the task allows it, else at home, with the task's own reach.
  - The wake rule skips the Tender, whose goals are stage E's. A whole cow, or a cow on rabbit legs, wakes as a Courier:
    its best task, Herder, waits for food.
  - Blood at work, the surgeon's tending and who may cut came in ahead of stages C and D (above).
  - The guard at home keeps within 4 blocks of home and fights what comes within its reach, as the guard job did:
    "patrols its reach" is read as watching it. The herder's look-out grows with its reach; the courier notices loose
    items within its sight as well as its reach, as the scavenger did.
  - A companion or bodyguard whose body could not guard loads as Idle with its maker. One set down from a Dormant Minion
    makes its home where it is set down, as unfolding always has.
  - A missing tool stops the goal as well as showing on the screen: the butcher with no blade, the herder with nothing in
    its mouth and the medic with no healing potions wait, and the status line says for what.
- **Tests.** `gametest/MinionTaskTests` (renamed from `MinionJobTests`), twelve new: `taskScreenRowsForMakerOnly`,
  `setTaskFromTheScreen`, `oldJobConvertsOnLoad`, `wakesToItsFittestTask`, `reloadTakesAnImpossibleTask` (a made-up mob
  on a copy of the zombie's rig whose data is changed under a Farmer to leave it no hand), `taskWordsReadBloodless`,
  `guardWithMeIsAWolf`, `courierWithMeFillsTheMakersHands`, `hunterWithMeHuntsBesideTheMaker`, `medicWithMeHealsOnTheMove`,
  `headlessFightsOnlyWhatTouchesIt` and `sentryWithNoBowHoldsItsPost`; and in `gametest/SurgeonTests`
  `surgeonHeadIsTheOwnersCall`, the owner's call at the table both ways (the surgeon file swapped for one tick through
  `PartsData.Store.setTestTask`). A test's maker is a stand-in player with no connection, shown the screen's rows through
  `MinionTasks.Viewer`. Changed: every `setJob` and `switchTo` is `setTask`; `scavengerFetchesMatchingItem` and
  `filteredScavengerFetchesWhatItPasses` are `courierWithSampleFetchesItsLike` (at home, into the chest by home) and
  `filteredCourierFetchesWhatItPasses` (with its maker, into their hands); `fisherFishesByWater` (a fisher's head with no
  rod fishes by hand); the sapper tests ask whether a body can sap; `wingsAreNoHands`, `buildCowOnFourRabbitLegs` (best at
  herding), `minionSavedAndLoaded` (task, anchor and reach saved), `amputationNeedsSurgeon` (a minion by the table on
  another task is no surgeon), `carcassKeepsVariants` (a knack, not a jobs list). Deleted, each replaced as 1.9 says:
  `cycleJobWithEmptyHand`, `villagerHeadOffersSurgeon`, `pillagerHeadOffersSurgeon`, `villagerAndPillagerHeadsOfferSurgeon`,
  `nitwitOffersCompanionOnly`, `blindHeadLosesSightJobs`.
  `medicWithMeHealsOnTheMove` hurts its maker only once the medic has followed them 3 blocks: hurt sooner, a medic
  stops to throw where it stands, within its following distance of 6 blocks, and one full run caught it short.
- **On screen** (`showcase_tasks_1.png` and `showcase_tasks_2.png`, new; `DevShowcase` opens the screen on the cow on rabbit
  legs from the server, as the maker's crouching click would). It woke as a Courier, its best task (Herder) waiting for
  food. In the first the mouse is on Herder: its card has the brass rim, and the hover reads "Herder: 200%, Born to it",
  what the task does, "waiting for food to lead animals with" in gold, "Pace ×1.37: speed 0.34, Rabbit leg, Swift I",
  "Sight ×1, at half weight: 16 blocks, Cow head", "Knack ×1.25: Cow head ×1.25", "Disposition ×1.25: Docile", "At work:
  12.5 mB of blood a minute" and "Click to set it to this"; above it Sapper and Surgeon read "Cannot", with "Nothing in it
  that detonates" and "No hand to hold a surgeon's blade" under them. The second is scrolled to the end: Medic 94% Able,
  Herder 200% Born to it, Tender 146% Good, Courier 146% Good (the task now, with the brass strip and the pointer), Hauler
  138%, Farmer 75% Fair, Fisher 60%, Butcher 22% Hopeless, Barterer 75%, Digger 100% Able; the amber marks on Medic,
  Herder and Butcher are the potions, food and blade they would wait for. At the foot At home is lit (the bed), With me
  (a head) is not, Reach 10 (the courier's own), Home here and done. In the bloodless run's
  (`showcase_bloodless_tasks_1.png`, `_2.png`) the title is "Construct: Tasks", the butcher the Dismantler, the surgeon's
  reason "No hand to hold a tool" and the blood at work "12.5 mB of essence a minute". The other shots are as they were.
- The suite is 473 tests (466, less the six deleted, and the thirteen above), and passed two runs in a row after the
  medic test's fix; `medicWithMeHealsOnTheMove` passed twenty times over, and the three added last
  (`reloadTakesAnImpossibleTask`, `taskWordsReadBloodless`, `surgeonHeadIsTheOwnersCall`) three to five times over.


### 15.23 Tasks, stages C and D as built: the levers, and the surgeon's stump (verified)

docs/NEXT.md item 1 is the design. Stage C (1.10) puts every lever of 1.2's table to work: what a minion's fitness at its
task makes of the work in play, each today's constant at 100%. Stage D prices the surgeon's stump by its fitness, as the
recommendation in 1.5 has it, with the brief's letter one data switch away; which of the two stands is still the owner's
call, and the recommendation is the default until they answer.

- **The levers** (`MinionFitness`, pure; each goal reads its task's numbers from its file, `MinionTask.Data`). The fitness a
  lever reads is held between 25% and 200% (`lever`), so nothing is ever more than four times slower, and the goals read
  the minion's fitness at its task worked out once a second and whenever its task or build changes
  (`MinionEntity.taskFitness`), not on every tick they ask:
  - the sentry: the time between shots ÷ fitness, never under half (`shotTicks`: a bow's second after it looses, a
    crossbow's second after loading and one to two after loosing, a trident's two), and its spread, vanilla's 14 less 4 a
    step of difficulty, ÷ fitness (`shotSpread`), for the bow, the crossbow and the trident alike;
  - the medic: a throw every 3 s ÷ fitness, never under 1 s (`throwTicks`), at a witch's spread of 8 ÷ fitness
    (`throwSpread`);
  - the herder: 30 s after a stray × fitness (`strayTicks`);
  - the courier and the farmer: how often they look round, every half second ÷ fitness (`lookTicks`), as the one-in-ten
    chance each time its goal was asked became one in that many; the Tender's (every second) waits for its goals (stage E);
  - the fisher: 30 to 60 s between catches ÷ fitness, never under a sixth of 30 s with Lure's cut as well (`catchTicks`,
    `catchLeast`); the digger 1 to 2 minutes ÷ fitness (`digTicks`); the barterer 6 s looking the gold over
    (`admireTicks`);
  - the butcher: a stroke every 0.75 s ÷ fitness, never under 0.3 s (`strokeTicks`), and below 100% its yields × fitness
    (`yieldShare`), through `CarcassButchery.yielding`, a scale around a cut or a stroke of skinning as `capturing` sends
    its yields elsewhere; a scale over 1 counts as 1, so no butcher beats hand yields;
  - the hauler: towing, a player's slowdown at 100% and over, never less, and a player's ÷ fitness below, at most 90%
    (`towing`), through `CarcassDrag.Dragger`, which a dragger that is not a player answers for itself. A minion's drag
    strength counts only in its fitness now (its pull): before, it eased its slowdown directly, so a cow torso's Beast of
    Burden towed with less slowdown than a player;
  - the surgeon's tending (a heart every 5 s ÷ fitness, never under 2 s) and blood at work (25 mB a minute ÷ fitness held
    50% to 200%, brass a quarter) came in stage B.
- **The strike rate** (spec 6.4's "+15% for each arm beyond 2, up to +60%", counted in Blow since stage A): blows land
  that much more often, `MinionGoals.blowTicks`, a second at two arms that strike and 13 ticks at eight. The melee goal
  (`Bite`, vanilla's `MeleeAttackGoal` with its own blow timing, its first blow at once as vanilla's), a headless body's
  `Feel` and a bowless sentry's strike all take it.
- **Shown.** The task screen's hover says what a task's fitness makes of its work, in the task's own numbers
  (`TaskWords.levers`): "A stroke every 3 s, keeping 25% of each cut", "Shoots every 0.5 s with a bow, spread 3", "Keeps
  after a stray for 60 s", "Towing, slowed 2.74 times as much as a player, to at most 90%", "Strikes every 0.65 s".
- **The surgeon** (docs/NEXT.md 1.5):
  - `Surgery.surgeonAt` takes the fittest awake minion on the Surgeon task within 4 blocks that may cut (`MinionFitness.mayCut`:
    by default any with a hand; with the surgeon file's `"needs_surgeon_head": true` only a head whose data says
    `"surgeon": true`, while anyone may still be set to Surgeon to tend). The shipped surgeon file writes the switch out as
    false.
  - Its fitness prices the stump: one bucket at 150% and over, two from 75%, three below (`MinionFitness.stumpBuckets`,
    from the surgeon file's `"one_bucket_from"` and `"two_buckets_from"`). A villager's or pillager's head with a hand
    is 200%, a zombie's 141%, the zombie villager's shaky hands 88%, a headless body 12%.
  - `Body` keeps each ragged stump's price per part (`raggedBuckets`); its codec reads a body saved with the old list of
    ragged parts as a bucket each. `Surgery.raggedCost` replaces the old check: the stump's buckets for fitting anything
    but a crude prosthetic, 0 for a crude one whatever the stump.
  - `Surgery.payBlood(player, mB, take)` adds up all the blood the operator carries, taking nothing unless all of it is
    there: what gives only all it holds or nothing (a bucket) first, in the order carried, while it is no more than is
    owed, then the rest from what gives any part (a backtank). "Needs 2 buckets of blood" when short.
  - The surgery screen, before any cut: "Surgeon: Minion (Zombie head), 141%" and "Its stumps cost 2 buckets of blood to
    fit" at its foot (amber at two, red at three), or "No surgeon by the table"; each ragged stump with its price
    ("Ragged stump: 2 buckets"). Clients are sent, with each minion, its fitness at its task and, for a surgeon that may
    cut, its stumps' price (`shownFitness`, `shownStump`), since task and disposition files never go to clients; the
    server works both out once a second and at once when its task or build changes. The task screen's Surgeon row says
    the price too, or that with the switch on only a surgeon's head may cut.
  - The stump is drawn raggeder the dearer it is (`BodyRendering.stump`): at a bucket as before; at two longer, its flaps
    hanging further and flaring wider, with torn strips at its corners; at three more so. Bloodless mode keeps a plain end.
  - The JEI surgery pages say any minion with a hand set to Surgeon, and the price by its fitness.
- **Words** (`BBLang`): the levers' lines, the surgeon and price lines, "a bucket" and "%s buckets", each with bloodless
  wording where the usual rewording is not enough (an open socket leaves a price in essence; only a surgeon's head may do
  the ritual's work). `taskWordsReadBloodless` reads them too.
- **Changed from the design, and why** (each recorded in docs/NEXT.md 1.5, 1.9 and 1.10):
  - `fitterButcherIsFasterAndCleaner` reads the pace at the carcass (a stroke every 8 ticks at 200%, every 30 at 50%: 3.75
    times as often), not the count over the whole 20 s: between pieces each butcher looks for its next one (a one-in-twenty
    chance on every other tick, about two seconds, now and then six) and walks to it, the same for both and at random, so
    the count ranged from 1.2 to 4 times over the runs, most often about twice, and failed now and then as a check. The
    poor one's half of a player's beef is read over 200 cuts under the scale its goal cuts under, since each cut's yield is
    rounded by a dice throw.
  - The 200% butcher is a butcher's head over a spider's torso on four of its own legs with four zombie arms, and the 50%
    one a whole villager: the design named only the numbers.
  - `bloodAtWorkFollowsFitness` adds a brass horse, a little under 200% (brass keeps no hide), at a quarter of what its
    fitness would cost flesh.
  - bb-organs had no test of its own for the zombie villager's shaky hands; `shakySurgeonReadsItsKnack` is new.
  - The Tender's look waits for its goals (stage E).
- **Found on the way:**
  - A butcher measured its reach to a piece from its feet, and a resting body's torso lies a block up: standing against
    the body where its path ended, it was out of reach, stood there five seconds and gave the body up for half a minute
    (more than half the runs of the new butcher test, one butcher or the other). It now reaches as far across as before and two and a half blocks
    over or under its feet, as the hauler measures across.
  - The surgery screen closed at once whenever the server opened it (laying the patient on the table) a tick before the
    seat they ride reached the client, so `showcase_body_2.png` had shown no screen. It now waits a second for them to be
    seen on the table.
- **Tests** (eight new, seven changed): in `gametest/MinionTaskTests` `fitterButcherIsFasterAndCleaner`,
  `poorHaulerCrawlsFitOneNoBetterThanAPlayer` (and the Hauler row's words on its maker's screen),
  `bloodAtWorkFollowsFitness`, `moreArmsStrikeMoreOften`, and `taskWordsReadBloodless` reading the new words; in
  `gametest/MinionFitnessTests` `oneHundredIsToday` with every lever at 100% and its bounds; in `gametest/SurgeonTests`
  `anySurgeonWithAHandCuts`, `raggedStumpPaidAcrossContainers`, `oldRaggedStumpIsOneBucket`, `shakySurgeonReadsItsKnack`,
  `surgeonHeadIsTheOwnersCall` cutting both ways, and `amputationNeedsSurgeon`, `raggedStumpCostsBlood`,
  `raggedStumpPaidFromBacktank` and `safetyFloorNeverNeedsSurgeon` reading the price.
- **On screen:** `showcase_body_2.png` is the surgery screen on yourself with a zombie-headed surgeon by the table: "Left
  arm: Ragged stump: 2 buckets" in red, the rest of the body's rows, and at the foot "Surgeon: Minion (Zombie head), 141%"
  and, in amber, "Its stumps cost 2 buckets of blood to fit"; bloodless, "Open socket: 2 buckets", "Construct (Zombie
  head)" and "Its open sockets cost 2 buckets of essence to fit", the eyes lenses and the organs pump, bellows and hopper.
  `showcase_stumps.png` has a stump of each price side by side: a bucket on the right arm (short, its flaps short), two on
  the left leg (longer flaps, torn at the corners), three on the left arm (the longest, flaring widest), and a clean cut on
  the right leg; bloodless, plain ends. `showcase_tasks_3.png` (new) hovers on the cow on rabbit legs' Butcher (22%,
  Hopeless): its mouth and bite, "A stroke every 3 s, keeping 25% of each cut" and "At work: 50 mB of blood a minute";
  bloodless, the Dismantler and essence. `showcase_tasks_1.png` gains "Keeps after a stray for 60 s" on Herder. The other
  shots are as they were.
- The suite is 481 tests (473 and the eight new above), and passed two full runs in a row; the new and changed tests passed
  five times over, and `fitterButcherIsFasterAndCleaner` (after the reach fix) 34 times.


### 15.24 Tasks, stages E and F as built: the Tender, the Butcher's Table, and the fitness shown elsewhere (verified)

docs/NEXT.md item 1 is the design. Stage E (1.10) builds the sixteenth task, the Tender, and puts the butcher to work at the
Butcher's Table; stage F shows a minion's fitness where the design says, outside the task screen. With them all six stages
are built. The surgeon (docs/NEXT.md 1.5) is still the owner's call: by default any minion with a hand may cut, its fitness
pricing the stump, and a datapack's `data/bloodandbones/minion_task/surgeon.json` with `{"needs_surgeon_head": true}`
limits the cutting to surgeons' heads (the villager and illager families', the witch's among them), the brief's letter.
Both ways are built and tested (`surgeonHeadFlagDecidesWhoCuts`, `surgeonHeadIsTheOwnersCall`).

**Stage E: the Tender** (`minion/MinionTender`, its goal `Tend`):
- **One errand a look.** Every second or so at 100% (its task file's `look_every`, a fitter Tender sooner), it looks over
  its reach of home (8, up to 12) from the loaded chunks' block entities (`survey`): its maker's minions lying powered
  down, the Blood Troughs with room, the Charging Cradles wanting canisters or sheets or holding empties, the tanks with a
  bucket of blood to give (a Create Fluid Tank or a Bleeding Rack's tray), and the containers (chests, barrels, shulker
  boxes, Create Item Vaults: `store`). It then picks the most pressing errand it can run now (`plan`), walks there and
  does it: a bucket or canister to a fallen minion first; buckets of blood into a trough; canisters and sheets into a
  cradle; then fetching what they want out of a container (buckets of blood, or empty buckets to fill at a tank when none
  are to be had, canisters, sheets), filling empty buckets at a tank, taking a cradle's empties out, and last putting back
  what it no longer wants. It takes as many at a trip as it has room for. A place or a fallen minion it cannot get to, or
  that turns out not to do what it looked like doing, is left alone half a minute.
- **Nothing is made or lost.** Every move is tried first and made only whole: a bucket pours into a trough only with room
  for all of it (NeoForge's `FluidUtil.tryEmptyContainer`, simulated then done), fills at a tank only from a whole bucket
  of blood (`tryFillContainer` the same way), items come out of a container only as many as it has room for, and what a
  cradle or container does not take stays in its hands (the insert's remainder). A fallen flesh minion mostly holds less
  than a bucket: it takes what it holds, the Tender drinks the rest if it is flesh with room, and the troughs by home take
  what is left, nearest the fallen one first (`MinionTender.share`); with nowhere for it all it does not pour, and its
  status line says so. A fallen brass minion takes a canister only with room for all of it.
- **Stocked:** troughs to the full; cradles to eight canisters and up to 16 brass sheets (a new number in the Tender's task
  file, `"sheets"`); the cradles' empty canisters out to a container.
- **Its status line** says what it lacks when something is wanted and nothing is to be had: "no blood to be had by the
  bucket within 8 of home", "no full Soul Canister in a container within 8 of home", "nowhere by home for the rest of a
  bucket that a fallen minion cannot hold".
- **The wake rule takes it now** (`MinionTasks.wakes`): stage B skipped it until its goals were built. A whole cow and the
  cow on rabbit legs wake as Tenders: their best, Herder, waits for food, and Tender and Courier tie at 146% (Tender first
  in the list).

**Stage E: the butcher at the Butcher's Table:**
- With a Cleaver, a butcher chops the pieces laid on a Butcher's Table within its reach of home, a stroke a piece at its
  strokes' pace, as a Deployer does (`ButcherTableBlockEntity.chop`, which now hands what it gives to the butcher, and what
  it has no room for falls on the table top as a Deployer's chop leaves it). It keeps what comes off, at its yield share,
  and the Cleaver comes away bloody. Of a carcass and a table the one nearer home goes first. It goes to a table only with
  a free slot for each kind of thing the piece gives (`yieldKinds`), else it empties itself into the container first.
- **Found on the way:** a task that stores its takings (`MinionGoals.Deposit`) took them to the block with slots nearest
  home, even one that takes none of them: a Butcher's Table by home, which takes only a piece, stood for good between a
  butcher and its chest. It is now the nearest container that takes some of what it carries.

**Stage F: shown elsewhere:**
- **The Surgery Table's line while building** (stage B's `MinionAssembly.status`): "15 health, speed 0.32; best: Herder
  200%, Tender 146%; 5 of 5 sockets filled" for the cow on rabbit legs.
- **What a part brings** (`TaskWords.partFacts`, from the mob data clients already have): its knacks, those of 1 left out;
  what an arm holds with (a villager's folded pair "hand, 2 of them"), and a leg whose data names a grip ("paw, on a body
  with 4 legs or more"); a head's disposition, and whether it is a surgeon's head. JEI's Body Parts page shows them under
  each part (`BodyPartsCategory`), as the mob has them; a carcass piece's tooltip shows its own (a villager's profession
  with them) under "In a minion:" while Ctrl is held, and "Hold Ctrl for what it brings to a minion's tasks" otherwise
  (`CarcassPieceItem.facts`).
- **`/bloodandbones minion fitness`** (`minion/MinionCommand`): the minion under its maker's (or an operator's) crosshair
  within 16 blocks, a wall stopping the look; what it is doing, then every task's lines as the task screen's hover has
  them, with the number before it was held where the cap took some off ("(213% before it was held)").
- **Words** (`BBLang`): the Tender's waits, the part facts, the command's lines; `taskWordsReadBloodless` reads them all in
  bloodless mode.
- **Docs:** spec 6.3, 6.4, 6.5, 6.7, 6.8, 6.9 and 8.2 ("a knack for X" throughout); §12; docs/NEXT.md item 1 marked built;
  the changelog and the README.

**Changed from the design, and why** (each recorded where the design says it, in docs/NEXT.md 1.1, 1.4, 1.9 and 1.10):
- "The containers there" are chests, barrels, shulker boxes and item vaults, never a machine's slots, a trough, a cradle, a
  table or a furnace: it would take what a Deployer holds or put an empty bucket in a furnace's fuel slot. "A tank" is a
  Create Fluid Tank or a Bleeding Rack's tray, never a basin (whose blood waits for a recipe) or a trough (it would pour one
  trough into another).
- A fallen flesh minion's bucket is shared out (above) rather than poured in whole as a hand pours one, which would spill
  what it cannot hold.
- A cradle is kept to 16 brass sheets, a number the design did not give.
- The butcher goes to a table only with room for what the piece gives, so what it chops ends in its hands and not on the
  table top.
- The Surgery Table's line names the Tender where 1.4's example named the Courier: the two tie, and ties go to the list's
  order. Its example's numbers were for another build.
- The command is its own class, not `TraitsCommand`'s, as it reads a live minion rather than a mob's data.
- A JEI "Minion Tasks" page per task is not built; the design left it for later.
- The JEI page is one per mob, so it shows a head's knacks as the mob has them; what the carcass kept (a villager's
  profession) shows on the piece's own tooltip.

**Tests** (five new): in `gametest/MinionTaskTests` `tenderFillsTroughsAndCradles` (a whole cow tends a chest of two buckets
of blood and two empties, a barrel of three canisters and five sheets, a trough, a Create Fluid Tank of 2000 mB and a cradle
holding two empties: at the end the trough holds 4000 mB and the tank none, the cradle three canisters and five sheets and
no empties, the containers the four empty buckets and two empty canisters and nothing else, nothing on the ground or left in
its hands), `tenderWakesAFallenMinion` (a brass Tender, which drinks no blood, wakes a zombie-torsoed flesh minion holding
344 mB with the chest's bucket, the other 656 into the trough to the drop, and a brass one with the chest's canister, the
empties back in the chest; it checks `share` first), `butcherChopsAtTheTable` (a butcher's head over a spider's torso on
four legs with four zombie arms chops a cow's body and a second laid on through the table's slot; what they give ends in
the chest beyond the table, none on the ground, the Cleaver bloody) and `fitnessCommandShowsEveryTask`; in
`gametest/MinionFitnessTests` `partFactsShowKnacksGripsAndDispositions`. `taskWordsReadBloodless` reads the new words.
- **On screen:** `showcase_tasks_4.png` (new) hovers on the cow on rabbit legs' Tender row, now its task (the brass strip
  and the pointer): "Tender: 146%, Good", what it does, "Carry ×1: 9 slots, Cow torso, Beast of Burden", "Pace ×1.17, at
  half weight: speed 0.34, Rabbit leg, Swift I", "Disposition ×1.25: Docile", "Looks round every 0.7 s" and "At work: 17.12
  mB of blood a minute"; bloodless, it "carries essence to fallen constructs" and the butcher's row is the Dismantler's.
  `showcase_tasks_2.png` now has the Tender as its task and its own reach, 8. `showcase_jei_knacks.png` (new) is the
  villager's Body Parts page scrolled to its head: "Knacks: Courier ×1.25, Surgeon ×1.5, Farmer ×1.25", "Disposition:
  Meek", "A surgeon's head: it may always do the ritual's cutting" (bloodless, "the ritual's work"), then its armour;
  `showcase_jei_organs.png`, the creeper's page, gains "Knacks: Courier ×1.25, Sapper ×1.5, Guard ×1.25" under its head.
  `showcase_table_line.png` (new) looks down at the Surgery Table with the cow on rabbit legs being built on it, its line on
  the action bar: "15 health, speed 0.32; best: Herder 200%, Tender 146%; 5 of 5 sockets filled". The other shots are as
  they were.
- The suite is 486 tests (481 and the five new above), and passed three full runs in a row; the five new tests passed ten
  times over, all at once in one world.

### 15.25 Tasks: the review's findings put right (verified)

A review of stages A to F (docs/NEXT.md item 1) found nineteen things; all were real, and all are put right here. Where
the design is recorded, docs/NEXT.md 1.1, 1.2, 1.4, 1.6, 1.9 and 1.10 say so too.

**What the screen said and what the goals did now agree:**
- **Folded arms under a head bite.** `MinionFitness` counted a head as something to strike with (the design's "an arm that
  is not folded, or a head to bite with"), but `MinionStats.fights()`, which every fight goal asks, was false whenever all
  the arms were folded. A whole villager set to Guard was shown at its bite's fitness and never took a target, even when
  hit. `fights()` is now an arm that strikes or a head; with neither it fights nothing, as before.
- **A sapper with no head** was offered the task (it has the organ) and never acted: its goal and its target goal both
  needed a head. It now sets itself off at a monster against it or what hurt it there (`MinionGoals.TouchTarget` takes the
  sapper's monsters; `MinionSapper.Sap` goes on only while it touches its target), never walking after one or seeing a
  banner; `Feel` leaves it to the sapper's goal.
- **A task file's `kind`** was read and never used. The disposition scales by it and the screen groups by it (the row
  carries it in `MinionTaskPayload.Row`); `SocialGoals` asks for the four fight tasks by name, as what they do is code.
- **A task file's tool** changed only the row. Every goal that waits for a tool now asks `MinionFitness.isTool` (the
  goal's own test, narrowed to the file's `items`): the butcher's blade, the fisher's rod, the sentry's weapon
  (`heldWeapon`, so `hasRangedAttack` too), the medic's potions and the herder's bait, and a courier's sample.
- **A task file's anchors** could name "maker" for a task whose goals work only from home. `MinionTask.checked` leaves out,
  and logs, an anchor the task's own defaults do not have; with none left, it keeps its own. `Data.anchorFor` is where a
  task is set when asked for somewhere it is not done: waking, `setTask(task)`, losing a task, the rows and the command.
- **The looks came half as often as they said.** The courier's, farmer's and Tender's one-in-`lookTicks` chance was taken
  each time its goal was asked whether to start, which vanilla's `Mob.serverAiStep` does every other tick. `MinionGoals.looks`
  takes one in half of it, as vanilla's `Goal.reducedTickDelay` does, and the courier's and farmer's `look_every` is 20 (a
  second, what their jobs did), where it had been written 10 from the one-in-ten. The Tender looks every second at 100%,
  as its row said, where it had looked every two.
- **The surgeon's reach** did nothing: `AttendTable` looked 6 blocks round for a table whatever it was set to. It now looks
  within its reach. It also worked out its tending pace once, as it started; each heart's wait is now read from its
  fitness then (`nextHeart`).
- **The farmer's pickup** looked 10 blocks round home while it reaps out to its reach (up to 12): `Collect` reaches its
  reach and two blocks more (`MinionEntity.RANGE`, now 2).

**Bugs:**
- **Items copied.** A courier's and a farmer's goals run every tick, and between the ticks it is asked whether to go on it
  is not; each could take up an item another had taken that tick, the maker walking over it or a second courier, since a
  stack taken whole was discarded with its count left on it (vanilla's `ItemEntity.playerTouch` puts the count back
  after it discards). `MinionGoals.pickUp` takes nothing from an item gone or empty and empties one it takes whole
  (as `HopperBlockEntity.addItem` does); both goals drop an item gone meanwhile.
- **A courier with its maker** put everything into their pack with `placeItemBackInInventory`, which drops at their feet
  what does not fit, and then fetched it back, over and over. It now hands over only what fits (`Inventory.add`), keeps
  the rest, says "its maker has no room for what it brings" and tries again in ten seconds, and never fetches an item its
  maker threw.
- **Bad data crashed in play.** A head's `disposition` that is no id ("Brave") or a knack key that is none
  ("bloodandbones:Surgeon") threw from the entity tick (the server) and from the stats' size (clients). `MobGroup`
  leaves them out as the data loads, with a warning, and every place that turns them into ids takes a bad one as none.
- **The farmer's scan and the Tender's tanks.** A fit farmer at reach 12 read four times the blocks a second that today's
  did. It now reads a band of its box a look (`FARM_SCAN`, today's 17 × 17 × 5 once a second at most), moving to the next
  band when one has no ripe crop. The Tender asked every block of a Create tank for its fluid, a lookup and two
  simulations each; it asks each tank once, at its controller (itself while it has none), and goes to its block nearest.

**Shown right:**
- **JEI's Body Parts page** showed a villager's head with the family's knacks under the professions (Farmer ×1.25 among
  them), which no villager head in play has: every villager's carcass records its profession, "none" included. The page
  now reads each part as a new one of its mob has it, the traits made by a mob never added to the world (only for a mob
  whose parts' data has variants), and says which kept trait changes that ("As a new one has them: its profession changes
  them", `TaskWords.mobFacts`). The surgeon's fact no longer says "it may always do the ritual's cutting": by default any
  hand may cut, so it says "it may do the ritual's cutting even where only such heads may".

**Tests made to test:** `headlessFightsOnlyWhatTouchesIt` and `sentryWithNoBowHoldsItsPost` ended at the first blow and put
the other husk where no targeting could reach it, so a headless guard hunting by sight, or a bowless sentry going for any
monster in its reach, would have passed. Each now waits three seconds with only what it must leave alone in reach (a husk
1.8 blocks off for the headless guard, within the 2 it notices things in), asserts no target and no step, then gives it
the one it may strike and watches on after. The butcher's waste, the sentry's shots, the surgeon's hearts and the
fisher's wait are now measured from what the goals do (docs/NEXT.md 1.9, "Added after the review").

**The Tender test that failed now and then.** `tenderWakesAFallenMinion` failed 2 times in 125, each time waiting on a
different errand. Its Tender is 32% fit (3 slots of 9 × the square root of pace 0.92): 63 ticks a look by its number, but
126 in play, because of the half-rate looks above. Five errands, one a look, came to about 630 ticks of looking alone on
average, and five random gaps of 126 run past the 1600-tick budget with their walks about 2% of the time. With the looks
put right its runs took 239 to 650 ticks (38 runs); the test has 3000.

**Tests** (498 in all): the twelve new ones are listed in docs/NEXT.md 1.9 under "Added after the review":
`foldedArmsBiteWithTheHead`, `headlessSapperGoesOffAtATouch`, `pickUpTakesNothingTwice`,
`courierKeepsWhatItsMakerHasNoRoomFor`, `poorButcherWastesWhatItCuts`, `sentryShootsByItsFitness`,
`surgeonTendsByItsFitness`, `fisherWaitsByItsFitness`, `surgeonFindsATableWithinItsReach`, `taskFileKindToolAndAnchorsCount`,
`taskFileKindAndAnchorsInPlay` and `badMinionDataLeftOutAsItLoads`; `headlessFightsOnlyWhatTouchesIt`,
`sentryWithNoBowHoldsItsPost`, `cannotOnlyWhenTheBodyCannot`, `villagerArmsArePacifist` and
`partFactsShowKnacksGripsAndDispositions` were strengthened. The new and strengthened tests and `tenderWakesAFallenMinion`
passed twenty times over, all in one world; `tenderWakesAFallenMinion` passed 60 times more in batches shared with the
other Tender, butcher, courier and hunter tests, where it had failed 2 times in 60; and the suite passed three full runs in
a row.

### 15.26 Tasks: the second review's findings put right (verified)

A second review, of 15.25's fixes, found eight things. All were real; all are put right here, and docs/NEXT.md 1.1, 1.6
and 1.9 say so where the design is recorded.

- **The farmer's bands ran the wrong way above 100%.** 15.25 sized each look's band as `FARM_SCAN` × the look's ticks, so
  the quicker a farmer looked, the narrower its band, and a band moved on only when it held no ripe crop: a 200% farmer at
  its own reach of 8 read its box in three looks (8, 8 and 1 columns), 30 ticks, where a 100% farmer read it whole every
  20. `MinionGoals.farmColumns` now sizes a band by the longer of its look and a 100% farmer's, so a fitter farmer's band
  is a 100% farmer's (the whole box at reach 8 or less) and it goes over its box as much sooner as it looks more often,
  reading at most twice today's blocks a second; a poorer one's band is wider, keeping it to `FARM_SCAN` a tick.
- **Wings.** A wing's strike style is "flap", damage 0, and `doHurtTarget` only knocked back with it, while Blow left it out
  and read the head's bite; `fights()` and the Strike need counted it, so a headless body on wings was offered Guard and
  flapped at what touched it for ever. `MinionStats.Strike.hurts` (not folded, not a flap, some damage) is now what Blow,
  the Strike need (`MinionFitness.Body.canStrike` is `fights()`), `fights()`, the ranged goal's "arm that hits" and
  `doHurtTarget` all ask. With no arm that hurts, the head bites and the wings throw back what it bites (0.8, the flap's
  knockback); with no head either there is nothing to fight with.
- **A task file's `required` and `carried`** changed only the row: a butcher's file making its blade optional left the
  butcher standing, ready by its row, with no reason; a fisher's making its rod required showed an untranslated key while it
  fished by hand; a medic's `carried` false had its row read the hand while it threw from what it carried. The goals decide
  both, so `MinionTask.checked` keeps the code's and logs a file's word otherwise, and leaves out a tool given to a task
  that works with none; the task files no longer write them. Where a file narrows the items, `TaskWords.waits` and
  `TaskWords.tool` name them ("waiting for Flensing Knife", "With Crossbow"; a tag by its conventional name where it has
  one), where the butcher's line said "a Cleaver or a Flensing Knife" whatever the file named.
- **The maker's reach with them.** Idle with its maker followed at `FollowMaker`'s fixed 6 and 3 whatever its reach, and a
  guard with its maker went for anything its maker hit or was hurt by, however far. Idle now sets off 2 blocks past its
  reach and stops 1 short (never nearer than 3), so 6 and 3 at its own 4 as before; `DefendMaker` takes only what is within
  its reach of the maker and gives up on one that gets further.
- **The surgeon with no table** in reach said nothing, and its status line said "at its table". `AttendTable` says "no
  Surgery Table within %s of where it stands" until it next looks (it looks every 5 s; the line is kept up in between) and
  clears it once it has one; `TaskWords.where` says "at its table" only while its home is one.
- **The docs** still had tasks unbuilt: BRIEF-AUDIT's package 13 is marked built and decision 12 narrowed to the surgeon
  switch, and PARTS-AND-TRAITS says tasks where it said jobs.
- **Untested:** the look chance, the farmer's bands, the Tender's many-block tank, and a reload that moves a task where it
  is done. Each now has a test (below). `MinionEntity.looksTaken` counts the looks `MinionGoals.looks` takes, for the test;
  `MinionTender.tanksFound` gives a Tender's look's tanks.

Checked against the old code: with the old bands `fitFarmerFindsRipeCropsNoLater` failed every run at its sums and 5 runs in
6 in the world (the 200% farmer took 17 to 25 ticks on average to start on a ripe crop); with the old one-in-n chance
`looksComeAsOftenAsTheScreenSays` counted 102 to 131 looks for the 240 expected and failed every run.

**Tests** (504 in all): `fitFarmerFindsRipeCropsNoLater`, `looksComeAsOftenAsTheScreenSays`, `wingsBuffetTheHeadBites`,
`withMeKeepsToItsReach`, `surgeonWithNoTableSaysSo` and `reloadMovesATaskWhereItIsDone` are new;
`tenderFillsTroughsAndCradles` fills from a tank of eight blocks, and `taskFileKindToolAndAnchorsCount` reads the tool's
fixed fields and words. The new and changed tests passed ten times over in one world, and the suite passed three full runs
in a row.

### 15.27 Tasks with the machines, materials and checks: the merge (verified)

The integration branch's organs, checks (15.18), machines (15.19) and materials (15.20) merged into the tasks work
(15.21 to 15.26, numbered 15.18 to 15.23 before the merge). Where the two met:

- **The butcher is work by hand.** The machines' three paths make a blade in a player's or a minion's hand the hand path
  (`data/bloodandbones/butchery_path/hand.json`: 60%, each whole piece a 15% chance to botch), scaled by the hand's
  butchery yield; the tasks made a poor butcher waste part of each cut (`CarcassButchery.yielding`,
  `MinionFitness.yieldShare`). Both now scale every yield in `CarcassButchery.dropYields`: a minion butcher by a body cuts
  under `byHand(minion)` and its share, and a 50% butcher gets half a player's hand, a 100% one or better all of it and
  never more.
- **The Butcher's Table.** Its chop takes the machines' lying piece and hand share and the tasks' sink for what a minion
  keeps: `chop(level, cleaver, who)` for a player or a Deployer, `chop(level, cleaver, who, into)` for a minion, whose
  chop is by hand. `canChop` (the minion's test for work there) now asks the table's part filter too, so a butcher chops
  only the pieces the filter takes, as a Deployer does.
- **The Surgery Table and Surgical Rig** keep both sides: the stump priced by the surgeon's fitness and paid from any
  container (`Surgery.payBlood`), and the rig's organs through `Surgery.harvest` and `organsLeft`.
- **Sounds.** Minion code plays only the mod's own wet sounds (their bloodless twins through `BloodlessSounds`) and
  vanilla sounds that are not wet; the bloodless sound test finds none missing a twin.
- **The showcase** keeps both runs: the villager's Body Parts page and the task screens, then the Surgery Table's line,
  then the gauge and diving shots after it (the table's line cleared from the action bar first, and the diving gear's
  toasts before the diving shot). With the machines and materials scenes the frames come slower than the ticks, and a
  pin or scroll on the task screen three ticks before its picture was not yet drawn in it (the herder's reasons were
  missing and two shots came out the same); the task screen's steps are now ten ticks apart. Photographed in both modes:
  the fitness lines, the task screen with the herder's, butcher's and Tender's reasons (the Dismantler's and "essence"
  in bloodless mode), the Surgery Table's line, the surgeon's stump price on the surgery screen ("open socket" in
  bloodless mode), the villager's knacks in JEI, the gauge and the diving gauge, the Guillotine's Ponder scene and the
  materials' basins and Butcher's Hooks.

**Tests:** `poorButcherWastesWhatItCuts` expected a 50% butcher to keep half the table's beef (2 or 3 of 4.22); by hand
that is half of 60%, less the botching, too few to weigh in one cut. It now checks that each butcher keeps no more
than half a hand's beef rounded up, and weighs the share over a thousand cuts through the very calls the goals make (the
body by `byHand` at the butcher's share, the table's `chop` in its hands) against a player's hand: half, give or take
6% (six times the spread). `butcherChopsAtTheTable` counts all four things a cow's body gives in the chest, since by
hand any one of them can be botched away.


**Suite:** 557 in all (504 here and 504 on the integration branch, 451 of them shared), passing three full runs in a row.

### 15.28 Physics and weight: where you hook it, where the blow lands, what dragging costs (verified)

This partly closes docs/BRIEF-AUDIT.md package 1 (physics: where you hook it and where the blow lands) and closes
package 3 (weight and the drag penalty). Package 1 is only partly done because the physics does not yet hold for most
mobs (see "What got worse" below: a cow comes round rear first, but six mobs of twelve do not), and because the hang
point waits on the owner's decision 5. The looser necks and the loose hang are built as the recommended answer to
decision 4, and can be undone (below). The physics measurement that `bb-rig-b` used to set our
rigs beside rig B's (docs/NEXT.md item 2) is kept for our rigs only (`gametest/RigComparison`, `RigScenarios`,
`RigComparisonTests`; off unless `-Dbloodandbones.debug.rig_compare=true`, see the README), and every change here was
measured with it before it was kept.

**Where you hook it.**
- **A lying carcass is hooked by the part aimed at.** A carcass lying still has one body; its legs and head are drawn
  from their remembered poses and have no cells. The aim is judged against every part's box as drawn
  (`CarcassAim.resting`, the same on both sides), the carcass unfolds, and the hook goes into that part where the look
  met it (`CarcassDrag.startResting`, blood welling there). A look at a drawn leg passes through it to the floor behind,
  or to the air, so the Meat Hook's use on a block or in the air is caught first (`CarcassEvents.onUseOnBlock`,
  `onUseInAir`) and hooks the part if it is nearer than any block (`CarcassDrag.useOnDrawn`); a click on the carcass's
  own cells judges the same way. A hauler still takes the torso. `lyingCarcassHooksThePartAimedAt` now clicks as the
  game does, through NeoForge's events.
- **Not through a wall.** What blocks the look is the player's own ray cast (`CarcassDrag.hookReach`, the same on both
  sides), not the block the use names. A use in the air names no block; a stone wall has no use of its own, so the
  game sends the item's use after the block's, and that reached through the wall with the hook's full five blocks; and a
  block of a Sable ship is named in the ship's plot, far from the player, so nothing ever blocked the look. The ray cast
  meets ships' blocks too, and a hit on one is brought out of its plot before it is measured. The same reach decides
  whether a click while dragging lets go. `meatHookDoesNotHookThroughAWall`: a stone wall, then the same wall made a
  sub-level, between a player and a lying cow's drawn leg, clicked at by the block event and then the item event; the
  cow stays folded, a drag of another cow is kept, and with the wall gone the same click hooks the leg.
- **The hooked part leads.** The aim spring on the hooked limb (`CarcassDrag.aim`) is sized by all the mass on the hook,
  not the limb's own, so once the limb reaches its joint's limit the turn carries on into the body and swings it round
  behind the limb (sized by the limb it could only swing itself, and a cow dragged by a hind leg went on head first). A
  player walking away from what they hooked now drags it trailing at arm's length back along the line to the hook, at
  hand height; facing it they still hold it a little in front, along their look, to lift it onto a rack or swing it
  under a hook, and between the two the targets blend so turning round never jerks it across (`CarcassDrag.target`).
  Held in front always, a carcass walked away from was pulled past its dragger's feet, bumped against their heels (where
  the drag holds off) and never came round. `hindLegHookComesRoundRearFirst`, `headHookFollowsHeadFirst`,
  `draggedByHindLegUpAStep`.
- **`meatHookDragsByLeg` (about 2 runs in 100) had a cause.** The drag holds off pulling while the hooked part touches
  its dragger (`isAgainstPlayer`, so it never shoves them), and the test's player, facing away, stood between the cow
  and the point in front of their feet it was pulled to: the leg came to rest against their back 2.1 to 2.35 blocks from
  that point, either side of the test's 2.25. The trailing target takes the cause away (nothing pulls the leg through
  its dragger any more). A second cause turned up once the test's player walked off rather than jumping there: a leg
  that slid on into a dragger who had stopped was let go the moment it touched them and lay against their back up to
  1.4 blocks short (5 runs in 60). While it touches them the drag now keeps its damping (it stops the leg closing on
  them) and only drops the pull. The test no longer lets a leg resting against its dragger pass: the leg must reach
  within 1.25 blocks of where it is pulled to.

**What you drag never pushes you.**
- **Walking on into it carried you off** (found by the multiplayer check, 15.18 item 2). Sable moves an
  entity out of any body it walks into and carries it along with that body as the body moves. Walking on into a
  carcass held in front, a player was pushed along by it, which moved the point it was pulled to on, which pulled it on:
  it carried the player about twenty blocks after they stopped. With the trailing target, walking off away from a
  carcass was already safe, but walking into one still did it: in `walkingIntoWhatYouDragNeverCarriesYou` the player
  was lifted 1.25 blocks onto the cow, stepped half a block in a tick, and after stopping was carried on up to 1.2
  blocks, in 3 runs of 3.
- **The fix.** The carcass someone drags is left out of Sable's collisions for that one entity
  (`mixin/SubLevelEntityCollisionMixin`, `CarcassDrag.isDraggedBy`): they walk through it. On the server that goes by the
  drag; on a client, which moves its own player, by the carcass the hooked body's cells belong to. Everyone else still
  bumps into it, and it lies on the ground and on other bodies as before. A hauler walks through what it drags too.
- `walkingIntoWhatYouDragNeverCarriesYou` and `walkingOffWhileDraggingNeverCarriesTheDragger`: a stand-in player,
  moved by the game's own walk and Sable's collisions (not placed), hooks a lying cow's body and walks straight on for
  two and a half seconds, into it or away from it, then stands for two. No step is longer than a walk's (0.156 blocks a
  tick, with the cow's slowdown), they are never lifted, and they stay where they stopped, still dragging.

**Where the blow lands.**
- **The kill is a blow, not a shove.** `CarcassAssembler.blow` replaces `shove` (1.0 on the bone nearest the look, 0.5
  on every other). The part the killer's look first meets, and the point on it, are found as the carcass is built
  (`CarcassAim.first`, the nearest part if the look glances past) and kept in that part's plot; the handover then gives
  that part one impulse at that point, along the look lifted a little, sized to set the whole carcass moving at 2.6
  blocks a second for a cow, slower for heavier ones as the shove was, and a little faster for lighter ones but no more
  than 3 (the shove allowed 5, and with the whole blow on one point a chicken or a wolf tumbled four blocks and more).
  Nothing else is pushed: the joints carry it, so which way it falls follows from where it was hit and how it is built.
  A carcass built with no killer (tests, the showcase) is struck by a stand-in two blocks back along the look, at a
  player's eye height, swinging at the torso's middle. The impulse goes in as the change of velocity it makes, worked
  out from Sable's mass data (`CarcassAssembler.impulseAt`): Sable's own impulse at a point goes through the mass the
  physics engine holds for the body, which a body made that tick does not have until the engine next steps, so a carcass
  struck as it was built (the showcase's row stood where they were made) or a leg knocked as a lying carcass unfolds did
  not move. `killingBlowLandsOnThePartItHits`, `flankBlowLandsItOnItsSide`, `blowFromBehindLandsItNoseDown`.
- **A punch lands where it hits, on any carcass.** `CarcassPartBlock.attack` no longer ignores a carcass that is awake.
  On one lying still it unfolds it and knocks the part aimed at, drawn or not (`CarcassRest.disturb`); on one lying,
  dragged or hung it knocks it (`CarcassRest.knock`): one impulse at the point on the part struck, times the swing's
  strength (a player's swing is weaker until it recharges, as it is against a mob). `hungCarcassSwingsWhenKnocked`.
- **A punch is one push, so weight tells.** It was first sized by the whole carcass's mass, which set every carcass off
  at the same 1.5 blocks a second, a ravager as fast as a chicken. It is now the same push whatever it lands on
  (`CarcassRest.PUNCH`, what sets a cow off at 1.5): a ravager barely moves and a chicken is knocked away.
  `punchMovesALightCarcassMoreThanAHeavyOne`.
- **Neither a punch nor a blow flings the part it lands on.** Sized for the whole carcass and put on one light part,
  a blow to a cow's face set its head off at 22 blocks a second, and a punch on a hung cow's leg set the leg off at 26
  (about 100 turns of a radian a second about the hip), yanking the body after it. `CarcassAssembler.impulseAt` now
  makes an impulse smaller if it would set the spot it lands on moving faster than a limit, worked out from the part's
  mass and its turn about its middle: 7 blocks a second for a blow (`BLOW_MAX_STRUCK`), 3 for a punch
  (`PUNCH_MAX_STRUCK`). A blow to the torso, which carries most of the weight, is not held back. The blow's speed now
  goes by what the bodies really weigh, so a golem of plate is knocked slower than flesh its size.
  `blowToTheHeadSnapsItBackWithoutFlingingIt` (a Meat Hook kill from in front, at the head: the head's fastest point
  stays under 7.5 blocks a second, the joints hold, the cow goes down within 1.5 blocks of where it stood; it slid 1.7
  before) and `punchOnAHungLegSwingsItWithoutFlingingIt` (the leg's fastest point under 4.5, the joints hold, the cow
  stays on its hook).

**Limbs hang, the head lolls.** These two are the recommended answer to the owner's decision 4 (docs/BRIEF-AUDIT.md;
docs/NEXT.md item 2: copy rig B's wins into our rigs one at a time), and the brief asks for them ("the head lolls";
hung, it swings and hangs differently with a leg off; dead animals look dead, limp and heavy). A review asked for them to
wait on the owner, and for a while they did (226422a); they are back (964a91d), for the owner to confirm. Undoing
them is one revert of that commit: the neck's default in `RigDerivation.jointFor`, the rig targets, and the
hang in `ShackleHookBlockEntity.hangTurn` (and `liftOnto`, since replaced by the checks' hoist), with their tests (`lyingHeadRestsBelowItsNeck`,
`legOffHangsLowerOnThatSide`, and the size of the swing in `hungCarcassSwingsWhenKnocked`). The hang point is not part of
it: that is decision 5, and it stays at the neck.
- **A dead neck.** The default head and neck joint (`RigDerivation.jointFor`) goes from a nod of −15 to 25 degrees, a
  turn of 30 and a tip of 6, stiffness 2 and damping 4, to −30 to 60, 45 and 15, stiffness 0.8 and damping 2.5: the head
  lolls well past level with only a faint pull toward its pose, so a long neck does not fold flat at once. A head
  sitting on top of its body (a biped's, read from the model: `restsOnTop`) still collides with it so it cannot sink
  into the chest; one held out in front (a cow's) no longer does, since its throat met its chest at the first nod and
  held the head level. The 47 rig targets that spell out their own head or neck joints were loosened the same way (a
  biped's nod to 50, the ravager's long neck a little stiffer); the wither's three heads are left as they were. Of
  those, 29 only spelled out the joint `jointFor` derives and now take it from there (a2bf47e), so a later retune of the
  neck reaches them; the ones kept are deliberate: the horse family's and the bipeds' necks, the ravager's long neck, the
  wither's heads, and seven heads that sit on top of their bodies but must not collide with them (allay, bat, frog,
  sheep, snow golem, vex, warden), which a target can only say by spelling out the whole joint.
  `lyingHeadRestsBelowItsNeck`.
- **A hung carcass hangs by its weight.** The spring that held a hung torso belly-out and head-up on every axis
  (stiffness 30, damping 7) is replaced by `ShackleHookBlockEntity.hangTurn`, which only turns it belly-out about the
  upright (10 and 3) and puts a light drag on its swing (1.0); how it tilts is left to gravity and to what is still on
  it, so everything below the hook hangs loose, a knock swings it for a few seconds, and a leg cut off changes how it
  hangs. Joined where it lay, the hook's joint had yanked the carcass up to the tip in one step, and with nothing
  holding its tilt any more the yank could throw it over the hook (`hangingCarcassBleedsIntoRack` failed 7 runs in 20):
  it was put up on the hook in its hanging pose first, every body moved as one and stilled (`liftOnto`). Merged with
  the checks' gentle hoist (15.18), that step is gone: the hook draws the neck junction up at 3 blocks a second and
  only holds it fast within 0.3 of the tip, so there is no yank left to throw it. While it rises `hoistTurn` turns it
  belly-out with a stiff spring about the upright and puts a heavy drag on every turn, so it tips slowly into the hang
  its weight gives it and arrives nearly still; once held it hangs loose (`hangTurn`). Two other ways were tried.
  Hoisted as it hangs (the light drag only), a cow lifted from lying on its side came up twisting and was still swaying
  seconds later. Held on the way up in the pose `liftOnto` put it in (main's old spring on every axis: head end straight
  up), it was let go at the tip some 29 degrees from where its weight hangs it, belly-down, and swung 12 degrees either
  side of that for more than five seconds, so `shackleHookHangsCarcass`, which asks the belly to face no more than 30
  degrees off level, read it at the edge of its swing now and then. Now it swings about 4 degrees there. A hook mounted
  on a Sable ship still joins at once, as before. Trolleys hoist and hang the same way. `legOffHangsLowerOnThatSide`.
- **Which way its head falls.** A hung cow's head, on its loose neck, falls to one side or the other of it and tips the
  whole cow about a degree that way: a whole cow's right side hangs 1.0 degree low with its head fallen right and 1.05
  high with it fallen left. `legOffHangsLowerOnThatSide` compared the cut cow with a whole one whose head could have
  fallen the other way (0.5 degrees low instead of 1.6 high, about 1 run in 30), so it now compares it with the whole
  cow as it would hang with its head on the same side (mirrored when it is not): 1.55 to 1.86 degrees high.
- **Still hung by the neck.** The hook still holds a carcass where its neck meets its body (255d386), not by one
  shoulder as the brief says: that is decision 5, the owner's.

**What it weighs.**
- **Bone, plate and flesh.** `Tissue` (flesh 1.0 a block, bone 1.5, plate 2.0) is a scalar of the parts data
  (docs/PARTS-AND-TRAITS.md 4.2): the skeletal overlay says bone, the golem family plate, and the snow golem's own file
  puts it back to flesh. Each tissue has a carcass block of its own (`carcass_part`, `carcass_part_bone`,
  `carcass_part_plate`, one `CarcassPartBlock` class; `BBBlocks.carcassPart`), as each wood is a block of its own, and
  Sable weighs each block's 4,096 sizes from its own `physics_block_properties` file (`PhysicsPropertiesProvider`); a
  datapack overrides them to retune. It was first a third block state property, and that cost too much: Sable matches
  every state of a block against every override of its file, for each world as it loads and for each joining client, so
  tripling the states made it nine times the work, some twenty seconds more for each world (the game tests' server took
  a minute longer to start), and one file of all 12,288 states was more than the 2 MB a client takes in one packet (it
  disconnected). As three blocks each weighing all 4,096 sizes it was three times what the one file cost on main, and
  that is paid more often than once: Sable applies every file for every world (dimension) as it loads, and again on
  each client, on its own thread, as it joins and at each /reload. So bone and plate now weigh only the cell sizes the
  rigs make (254 of the 4,096, every rig grown and baby, so a datapack may make any rigged mob bone or plate), and flesh,
  which any mob may be, keeps every size. A cell of a size no rig makes is built of flesh, weighed by its size, rather
  than weighing as a whole block of bone (`CarcassAssembler.cellState`). Applying all three takes 18.9 million matches,
  about 1.7 seconds on the test machine (1.6 of them flesh), where the one carcass block took 16.8 million: about a
  tenth more than main, for each world and each joining client, not three times. `boneAndPlateWeighMoreThanFlesh`,
  `cellMassesAreCheapToLoad` (each file under 1.5 MB to send; all the carcass blocks together within a quarter over the
  one block's matches; every size a rig makes weighed in every tissue; an odd size built of flesh; the time logged).
- A rig's `weight` stays its size as flesh (blood and yields go by how much animal there is), and a piece is light
  enough to carry by its size, not its weight, so a skeleton's skull is carried as a zombie's head is. The size is the
  bone's box in the rig (`Bone.volume`, as a minion judges a piece), not its mass divided by its tissue's density in
  code, so a datapack that makes bone heavier does not stop skulls being carried.

**The drag penalty (package 3).**
- **From the mass on the hook.** `CarcassDrag.penaltyFor` takes the mass of the bodies actually hooked, and the drag
  refreshes it every quarter second as pieces come off. `Rig.dragPenalty` (the whole mob's figure whenever it had a rig,
  so a severed ravager leg cost 55%) is gone.
- **The brief's curve.** Between a whole chicken (5%) and a whole ravager (55%) it rises with the logarithm of the mass,
  each doubling costing the same few points; below a chicken it falls in proportion to the mass; nothing costs more than
  a ravager. Measured: chicken 5.0%, rabbit about 1%, cow 29.0% (as before), horse 37%, iron golem (plate) about 47%,
  ravager 55.0%; a cow less a hind leg 28.3%, the leg alone 1.8%. `drag_strength` still eases it.
- **Tested at both ends and on the move.** `dragPenaltyRunsFromChickenToRavager`, `severedLegCostsWhatALegWeighs` (which
  now also asks that the drag still holds the body after the cut, and costs exactly what is left on it: a drag that let
  go would cost nothing and passed before), and
  `draggingSlowsThePlayerByThePenalty`: three stand-in players walk side by side for 25 ticks, one free (5.51 blocks),
  one dragging a chicken (5.23, 5.0% less) and one a ravager (2.48, 55.0% less).
- It moves onto weight classes when package 2 brings them.

**Tests.** `gametest/PhysicsTests`, 22 tests: one per physics sentence of the brief, each held to a direction or an
amount (not only that something moved), the wall, the blows and punches, the walk into what you drag, and the weight and
penalty tests above. The scenarios are the measurement's own, played on a cow; no other mob is held to them yet (see
below). The first version's tests passed 20 runs in a row with `-Dbloodandbones.debug.repeat`, and
`meatHookDragsByLeg` and `meatHookDragsByBody` 100 each; the finished code passed the whole suite three times in a row.
The suite is 439 tests.

**The showcase** (`DevShowcase`, after the Ponder shots) gets a physics yard 60 blocks west: `showcase_physics_0`, a cow
dragged by its right hind leg seen from in front of its dragger, its rear leading, the hook in its leg and its blood
trail behind; `physics_1`, three cows struck as they stood, one from the flank (on its side, on the left), one from
behind (nose down, on the right) and one in the face from in front (in the middle: down within a block of where it
stood, its front end tipped up, not flung); `physics_2`, three cows hung under a beam by the neck, legs and heads hanging
loose, the one on the left knocked a moment before and swinging, the middle one with its right hind leg cut off and the
leg lying in the blood under it. In `physics_0` the dragged cow trails behind its dragger on its back, the hooked hind
leg leading, in two runs of three with the walk-through fix and in both runs without it. In the third it was in front of
them, head first: the showcase moves its player by teleporting it a little each tick rather than walking it, and the cow
starts standing beside them and falls a different way each run; once it lies ahead of where they look, the drag holds
it there, as it does for a player facing what they hooked. The rear-first test starts its player beside a cow already
lying and holds; the showcase's standing start is left as it is, since it shows the drag as it happens.

**Physics, before and after.** The measurement (`-Dbloodandbones.debug.rig_compare=true`, eight runs of every scenario
on its twelve mobs, and what twelve at once cost) run on main and on this work. Mobs meeting the brief's bar in most
runs, of twelve (runs meeting it, of 96), and the median of all runs:

| Bar | Before | After |
|---|---|---|
| Flank: on its side, fallen away from the blow | 4 (33) | 8 (67) |
| Flank: tilted 45 degrees or more at rest | 7 (57), 88 | 10 (80), 90 |
| Flank: leaning within 60 degrees of the blow's way | 4 (38), 66 degrees off | 9 (75), 39 degrees off |
| Behind: 20 degrees nose down at some moment | 10 (80), 38 | 11 (88), 53 |
| Behind: 20 degrees nose down at rest | 8 (59), 22 | 6 (43), 18 |
| Behind: head on the ground at rest | 10 (80) | 11 (85) |
| Held upright: head droops 5 degrees or more | 8 (64), 25 | 10 (80), 60 |
| Held on its side: legs within 60 degrees of down | 8 (68) | 9 (70) |
| Hind-leg hook: rear first within 45 degrees | 3 (32), 70 degrees off | 6 (47), 46 degrees off |
| Head hook: head first within 45 degrees | 4 (35), 59 degrees off | 10 (80), 23 degrees off |
| Hung: legs within 45 degrees of straight down | 10 (82), 20 | 11 (88), 14 |
| Hung: swings 0.15 blocks or more when knocked | 1 (10), 0.06 | 6 (49), 0.17 |
| Cut leg changes how it hangs, 5 degrees or more | 1 (12), 0.5 | 3 (29), 2.7 |
| Up a step by the torso | 9 (67) | 12 (96) |
| Up a step by a hind leg (cow and horse) | 2 (16 of 16) | 2 (16 of 16) |
| Settled within 200 ticks; folds into one body | 11; 11 | 11; 11 |
| Joints held, nothing sunk into the floor | 12 | 12 |

Beside rig B's four wins (docs/NEXT.md item 2, measured then with the first version of these scenarios, so only roughly
comparable): turning rear first is now level with B's (B had 6 of 12), falling away from a flank blow nearly (B had 9 of
12), and heads and limbs hang as loose as B's or looser (a hung head ends 58 degrees from its standing pose, median,
where it ended 31; B's hung further than ours did). The fourth, cost while falling, is not won: twelve falling at once
cost 7.1 to 9.5 ms a tick before and 6.9 to 8.3 after (four runs each, medians 7.4 and 7.5), and lying still 1.4 to 2.6
before and 1.6 to 2.4 after, the same within the noise. Ours kept what it won: weight (now by tissue too), the step by a
hind leg (16 of 16), looking dead rather than posed (bodies end 44 degrees from their standing pose, median, where they
ended 32; B's 22), whole bodies, and staying where it fell, though less so: 2.1 blocks from where it was struck, median,
where it was 1.5 (B's throw sent them 9.6). The cow, which the tests use: hind leg 99 degrees off before (it went on
head first), 32 after; its head from 34 to 17; its head rests 0.07 blocks below its neck where it rested level with it;
hung, it swings 0.22 blocks where it swung 0.07.

What got worse, or did not come right (the tests hold only the cow, one of the mobs that got better; these are open):
- **At rest after a blow from behind** fewer end nose down (8 to 6), though they pitch further onto the nose as they
  fall (11 of 12, 53 degrees). Not traced; most likely the looser neck, whose head now folds under instead of propping
  the front down.
- **Hooked by a hind leg**, six of twelve now come round, but not the same six: the cow (99 degrees off to 32), polar
  bear (73 to 8), wolf (107 to 51), spider, villager and zombie got better; the pig (19 to 42), horse (23 to 36), llama
  (44 to 58), chicken (49 to 71) and sheep (79 to 137) got worse. The sheep's narrow body rolls onto its side under the
  pull and the hooked leg swings forward under it, pulling near its middle, so nothing turns it. Hind legs that swing
  forward less than back were tried on eight of the twelve and did not help overall.
- **A flank blow** now lands the chicken on its back and rolls the wolf onto its back in most runs, barely tips the
  rabbit and still never fells the spider.
- A cut leg changes how a cow hangs by only a degree and a half (a hind leg is a sixteenth of it, and hung by the neck
  it swings its weight back under the hook), so its test asks for that direction and 0.8 degrees, not the bar's 5. The
  side that loses its leg rides up and the side that kept its leg hangs lower, as the weight says; the audit's wording
  ("hangs lower on that side") reads the other way, and the physics was followed.

**Left open, for the owner.**
- **Decision 5, where a hook holds a carcass.** Still by the neck junction (255d386), not by one shoulder as the brief
  says. Hanging by a shoulder would change how a cut changes the hang the most.
- **Decision 4, rig B.** Not brought over. Its first three wins were aimed at in our own rigs instead (the looser necks
  and the loose hang, above), which is the audit's recommended answer; its fourth (cost while falling) not. For the
  owner to confirm; one revert takes the necks and the hang back to main's.
- **Most mobs.** Rear first holds for six of twelve, a swing for six, a flank fall for eight, and a cut leg changes the
  hang by 5 degrees or more for three (a cow's by under two degrees, which a player cannot see). The sheep and the pig
  are the ones to take next: both come round worse by a hind leg than before.
- **Weight classes** (package 2): the penalty and the densities are by mass and tissue until groups give classes.
- **The load cost of the carcass blocks' masses** (about 1.6 seconds for each world and each joining client on main,
  1.7 now) is almost all flesh's 4,096 sizes; weighing flesh only at the sizes the rigs make too would cut it to a sixth,
  but a datapack's own rig of a new mob could then make cells that weigh as whole blocks, so it waits on package 2's
  groups, which say what a mob is made of.

#### 15.28.1 Merged with the integration branch (organs, checks, machines, materials; verified)

This section was 15.19 on its own branch; the integration branch had taken 15.16 to 15.20 meanwhile, so it followed
them as 15.21. The tasks work took 15.21 to 15.27 in the next merge (15.28.2), so it is 15.28 now. Merged at a22b8a7.

- **The Shackle Hook and the Shackle Trolley** (both sides changed them). Kept both: the checks' gentle hoist (15.18:
  the neck junction drawn up at no more than 3 blocks a second, a push at the hooked point carrying the whole
  carcass's weight, never a snap) and this section's loose, belly-out hang (`hangTurn`). This section's `liftOnto`,
  which moved a carcass up onto the hook in one step to stop the joint's yank throwing it, is gone: the hoist does
  that gently. How the two meet (`hoistTurn` while it rises, `HOLD_REACH`) is under "A hung carcass hangs by its
  weight" above and below. A hook on a Sable ship still joins at once; the checks' finding that it probably cannot
  hold a carcass is left as it was.
- **Mob layers** carry both sides' fields: main's organ lists and variants and this section's tissue (`MobGroup`,
  `ResolvedMob`, `PartsData`).
- **Resting** (`CarcassRest`): main's rule that a carcass whose bodies stay put for five seconds rests even while its
  limbs twitch, and this section's blow that lands where it hits; a knocked carcass now also forgets where it was
  settling. `CarcassEvents`, `CarcassDrag` and `CarcassButchery` merged by themselves: the Flensing Knife held on a
  part, the butchery paths as data and the carrying by size sit side by side. The cost check's shove is the killing
  blow (`CarcassAssembler.blow`). Bone and plate carcass blocks swap to their bloodless look as flesh does.
- **The showcase** takes main's gauge and diving shots, then the physics yard, then the Ponder scenes.
- **Generated resources** merged without a conflict; datagen run after the merge changed nothing. This branch added no
  recipe and no sound.

**What failed after the merge, and why** (each found by running the suite, repeated with
`-Dbloodandbones.debug.repeat` until the cause showed):
- `legOffHangsLowerOnThatSide`, 2 runs in 10 when hoisted loose, and later about 1 in 30: the two ways a hung cow's
  head falls (above). The test now compares like with like.
- `shackleHookHangsCarcass`, now and then: the swing a body started when let go of a rigid pose at the tip (above).
- `dozenHungCarcasses` (main's): a hung rabbit spinning at 5.4 radians a second at the check. The hook's joint took up
  the last 0.3 blocks in one physics substep; the rabbit spun at the moment it was held, and the dozen's fastest torso
  was that jerk, 6.5 to 11 blocks a second against a limit of 10. A hook now hoists on to 0.05 before it holds the body
  (or holds it at the tip once it stops coming nearer within 0.3): the fastest torso is 4.5, the rabbit still at the
  check. A Shackle Trolley still holds at 0.3 (`trolleysQueueOnAChain`: two bodies on one chain keep each other that
  far off, and a trolley waits until its own is held). The turn springs are also capped so that they cannot overshoot
  on a small body; that was a guard, not the cause.
- `meatHookDragsByBody`, about 1 run in 25, before the merge too: since what someone drags no longer collides with
  them, a body could slide on into a dragger who had stopped and lie in them 0.76 blocks short, the drag holding off
  while it touched them. Once they stand still, it is drawn back out (not while they walk past it, which kept a cow
  dragged by a hind leg from coming round, 4 runs in 30).
- `butcherButchersWithCleaver` (main's), 1 run in 6: the butchery paths made a leg by hand give nothing six times in
  ten, and in 900 ticks the butcher broke down four or five pieces. It has the time to reach the torso now.

The suite is 525 tests, and passed three times in a row. The showcase was run in both modes and looked at: the
physics yard (the dragged cow, the three struck cows, the three hung cows, loose, one swinging, one a leg short with
the leg below it; in bloodless mode plated wrecks and no blood), the machines' row F, the materials' row G, the belt,
the knife held on a part, and the gauge and diving shots.

#### 15.28.2 Merged with the tasks work (verified)

The integration branch's minion tasks (15.21 to 15.27: `MinionJobs` renamed `MinionTasks`, `MinionFitness`,
`MinionTask`, the task screen, the levers, the Tender, the butcher at the Butcher's Table, the fitness-priced ragged
stump and `payBlood` across containers) merged into this section's work at e0618a0. Where the two met:

- **Numbering.** Both sides had written a 15.21. The tasks keep 15.21 to 15.27, and this section follows them as 15.28
  (its first merge 15.28.1, this one 15.28.2); every reference to it in the docs and the code comments moved with it.
- **Mob layers** (`PartsData`, `MobGroup`) carry both sides' fields: the tasks' knacks, which merge key by key and
  read an old `jobs` list as knacks, and this section's `tissue`, a scalar that replaces. docs/PARTS-AND-TRAITS.md 4.2
  lists both.
- **`butcherButchersWithCleaver`.** The tasks moved the job tests into `MinionTaskTests` and deleted
  `MinionJobTests`, where this section had given the test 2,400 ticks to reach the torso (15.28.1). The longer time is
  on the moved test now; a minion butcher keeps no more of a leg by hand than a player does, so the reason still holds.
- **The showcase.** Both sides added methods at the same place in `DevShowcase`; both are kept. The tasks' longer
  minion timeline (the fitness lines, the task screen, the table's line, the stumps by price) runs first, and its
  last step still hands on to the physics yard, then the Ponder scenes.
- **Generated resources** merged without a conflict, and datagen run after the merge changed nothing.
- `CarcassButchery`, `CarcassDrag`, `BloodAndBones` and the golem group merged by themselves.

Nothing failed after the merge. The suite is 578 tests (557 on the integration branch and 525 here, 504 of them
shared) and passed three times in a row. The showcase was run and looked at: the physics yard (the cow dragged by a
hind leg come round rear first, its head end pointing back west; the three struck cows down; the three hung cows, one
swinging, one a leg short with the leg on the ground below it), the fitness lines and the task screen (Herder's and
the Butcher's reasons, the list scrolled to its end, the Tender's reasons), the Surgery Table's line, the stumps by
price, the minions' row, and the machines.

### 15.29 Self-augmentation: the ritual's screen and the proofs (verified)

Closes docs/BRIEF-AUDIT.md package 11, but for the two parts that wait on owner decision 11 (below).

**The surgery screen** (`client/SurgeryScreen`, rewritten). The brief says to "select augments in a new UI". Before
this, the one item laid on the table decided every button, so trying another augment meant getting off the table, and
the buttons sat over the body in the outside view (`showcase_body_2.png` on main). Now:
- Two panels at the sides of the screen, each at most 140 wide, with at least 110 left clear between them, where the
  outside view puts the body. The HUD is hidden while the screen is up (put back as it was on closing), and nothing is
  drawn over the world (no dimming, no blur).
- The left panel is the body: a paper doll laid out as a player skin is (the patient faces you, so their right is on
  your left), the eyes in the head and the lungs, heart and stomach down the torso, each slot drawn as it is. Their own
  is skin or the organ's colour. A stump is a dark hole with the limb's root and a raw end; a ragged one is torn, its
  strips longer and a drop under it for each bucket fitting there costs. An implant is its kind's colour (brass,
  graft, iron) with its item on it, darkened when it is not working. A green mark sits on each slot where something
  carried goes in (`Surgery.offersCarried`: an implant, limb or module; a carried blade or wrench marks nothing, as it
  fits nothing), and the screen opens on the first such slot. Under the doll are the slot's name and state, and at the foot of the panel the surgeon,
  its fitness and its stumps' price, before any cut (the tasks work's lines, kept).
- The right panel is the slot picked on the doll: its state, what is on the table, the blood on you
  (`Surgery.bloodCarried`), a row of every implant, prosthetic, limb and module you carry that fits a slot of this
  body (`Surgery.carried`; a click picks that slot, an open one first; tinted green where it goes into the picked slot),
  and a card for each thing that can be done. "Fits" is one rule on both sides, `Surgery.fits`: what it would do puts
  it in (fit, replace, swap, reattach, a module). An item that fits nothing only unclips an implant it is used on, so
  "does something" is not "fits".
- The cards come from `Surgery.options`, the same on both sides: unclipping with bare hands, then what lies on the
  table, then each accepted item carried (inventory, armour and off-hand, as the payment already counted blood across
  them), each kind once (a second blade or wrench never; a second implant only if it differs). A card that cannot be
  done yet says why in red (`Surgery.blocked`: no surgeon to cut, not enough blood for a ragged stump).
- It is drawn as Create's value boards are (dark, in Create's brass frame textures) with Create's schedule cards and
  confirm button, as `MinionTaskScreen` is.
- "The worn tank" in the audit's words: implants are never kept in a tank, so the screen reads it as the blood the tank
  holds, which is shown and pays for a ragged stump. Items worn in the armour slots are offered like any other.

**The payload** (`Surgery.ActionPayload`) now names where the item comes from: `FROM_TABLE` (-1), `BARE` (-2, an
implant unclipped) or a slot of the surgeon's inventory. `Surgery.operate` takes the source; a carried item is taken
from its slot (a blade used stays there, bloodied), and `Surgery.handle` refuses a slot that is not there before
anything else. The payload also names what the card said it would do and with what item (air for bare hands), and
`handle` refuses it ("That cannot be done now") if that is no longer so. A slot index is reused as soon as it empties,
so without this a second click sent before the first one's answer arrived did something else: a fit clicked twice
unclipped what had just gone in, and a swap clicked twice swapped straight back. The old signatures (the table's item) are kept for the tests and the rig.

**Missing limbs hold and wear nothing** (known since 14.5). Two client mixins ask `BodyEffects.shows(entity, part)`:
`ItemInHandLayerMixin` stops vanilla drawing an item in a missing arm's hand in third person, and
`HumanoidArmorLayerMixin` hides a missing limb's part of each armour piece just after vanilla sets the piece's parts
visible. Only a player's: a mob's missing limbs are still drawn, and so is what it holds. A spyglass raised to the eye
is drawn by its own path and is not covered.

**Walking no longer wears the legs.** The brief names "swinging, mining and running"; a leg that rotted from walking
about would rot from simply playing. `Necrosis.onTick` counts only ground covered at a sprint, at the rate a sprint
always wore (a point every 2.7 blocks), and now runs for any player on the server side, not only a `ServerPlayer`.

**The fog's rule** moved to common code, `BodyEffects.sight` (both eyes working: no change; one, half as far; none, six
blocks), so a test can check it; `BodyRendering.onFog` draws what it gives.

**Tests** (`RitualTests`, 12, all new): `bodyKeptThroughDeath` (a server player killed and respawned by the server's
player list, stump price, rot and modules kept, and the swing penalty back at once), `amputationLeavesHealthAlone`,
`crudeOrgansLiftPenalties` (the heart's weakness, the lungs' sprint on a real tick, the stomach's eating as it starts),
`necrosisFromARealHit` (`Player#attack`), `necrosisFromARealBlockBreak` (the game mode's `destroyBlock`),
`necrosisFromARunNotAWalk` (the player's own tick, three seconds of each: 12 blocks walked on the ground for no rot,
checked to be a real walk, 16 sprinted for 12 points), `fogWithOneEyeOut`, `screenOffersWhatYouCarry` (the cards, the
carried row never pointing a Peg Leg or a Crude Heart at a brass arm, and no green mark from a carried Cleaver or
Wrench), `buttonPathThroughHandle` (each choice written and read through the payload's codec, then handled: the table's
Cleaver; a stranger's own Hook Hand refused from across the room and fitted from beside the table, and unclipped back
to them; a carried Hook Hand from its slot; and refused, the fit clicked twice, slots that are not there, a swap
clicked twice and the rig taken off; each refusal was checked to fail the test when its check is removed), `implantDrainsAddUp`, `sixBrassLimbsNeedASeriousFarm`, `missingLimbShowsNothing`. The tests that need a
real server player make one with a connection to no client (a channel of its own, as the test framework's mock player
has, sending nothing), and take it off the server's books when done.

**Balance: six brass limbs** (two Hydraulic Arms, two Piston Legs, two Optic Eyes), measured by the test over a minute
of the game's own drain, with the soul blood line's numbers read from its recipes and a cow's blood from its rig:
- 10 mB of soul blood a second: 600 a minute, **36 buckets an hour**, before the throttle or any module (a Magnet Coil
  or an Analytical Lens adds 1 each; `blood_upkeep` scales it all).
- The full line gives back 200 mB for every 250 of blood set under a Basin Lid, so that is **45 buckets of blood an
  hour**.
- A cow bleeds 809 mB, so **about 56 cows an hour**, near one a minute. At five minutes between breedings a pair gives
  twelve calves an hour, so that is five or so breeding pairs, twenty calves growing, and a line to kill, hang, bleed
  and pipe them.
- One Basin Lid sets 250 mB every 200 ticks, 72 buckets of soul blood an hour, so **half a Basin Lid** is enough; the
  line's limit is the animals, not the machines.
- The test holds it to "serious" (at least 20 cows an hour, more than a pen fed by hand) and "not impossible" (at most
  120 an hour, and at most four Basin Lids).
- The trickle path is cheaper per animal: a hoglin bleeds 2.2 buckets of soul blood straight, so sixteen or so an hour
  would do.

**Left for the owner (decision 11), not built:**
- Swapping an implant straight in for flesh (`REPLACE`) needs the surgeon but leaves no ragged stump, so a one-step
  swap always avoids the stump's price (14.12).
- A fully rotted limb works again after 1 mB of blood and a second of perfusion; the brief says "Restoring it costs
  Blood", and how much more it should cost from the maximum is the owner's call.

**Found on the way, not changed:** perfusion clears 2 points a second from each rotting graft while there is blood in
the tank, and a graft only works with blood in the tank, so in play rot barely builds: a sprint wears a Sinew Leg by
about 2.1 points a second, and swinging by a point a swing. Whether rot should build faster than blood clears it goes
with decision 11.

**Verified.** The suite is 590 tests (578 and these 12) and passed three times in a row; datagen changed nothing
after the new lines. The showcase was run in both modes and looked at.

**Showcase.** `showcase_body_2` is the new screen on yourself, the ragged stump the zombie-headed surgeon left picked:
what fits it, and its price. `showcase_surgery_1` is the same with the brass arm picked (unclip, swap, fit the carried
module) and a slot hovered: the Hook Hand, Flesh Arm, module and arm are tinted as going into it, the Peg Leg, Crude
Heart and Glass Eye are not. The player carries a Cleaver too, and the lungs and stomach have no green mark. `showcase_body_5` is a player with the left arm and right leg gone, holding a sword and a
shield in iron armour: the shield, the chestplate's left sleeve and that leg's armour are not drawn, the sword and
the rest are. In bloodless mode the doll's stumps are grey sockets with grey studs for the price, the organs a pump,
bellows and a hopper, and every line says essence and socket.

### 15.30 Rule 5: every block and every carcass on contraptions (verified)

This closes docs/BRIEF-AUDIT.md package 8, but for the cut leg that once fell into the void, which was chased and not
made to happen again (below). No owner decision was needed and none was taken.

**Every block on a Create contraption** (`gametest/ContraptionTests`). Create's own contraption tests put a contraption
together, move it, take it apart and look at what arrived; these do the same with a Mechanical Piston two poles long
built in the test (Create's use structure files). Each block is loaded first, then pushed two blocks on:
- **the Mangler, Guillotine, Beheader and Deglover** (one test each): turning on a motor under them, pushed off it onto a
  motor of another speed. As Create's own kinetic blocks do, a machine loses its drive while it moves and takes the drive
  where it is set down (it turns at the new motor's 32 RPM). It keeps its output (and nothing new appears in it), its
  filter, its stroke count and a Guillotine's wind. A machine is not an actor: it does nothing while it moves, as a
  Millstone or a Mixer does not.
- **the Bleeding Rack** with 2500 mB of blood: all of it arrives, none spills. Like Create's Basin and Item Drain, its
  fluid rides in its data; it is not a mounted tank that the contraption's own fluid storage could draw on.
- **the Spit Roast** with a whole cow on it, part cooked, pushing **a Specimen Jar** with a heart in it: both arrive with
  what they hold, the cooking neither goes on nor back, and the cow does not come off into the world.
- **the Butcher's Table** with a head on it and a filter; **the Blood Trough** with 3000 mB of blood pushing **the
  Charging Cradle** with canisters and sheets (and each is found by minions where it is now: the per-level lists move with
  them); **a Fluid Backtank** set down, iron, with 3000 mB of blood, pushing **the Backtank Port** (which still takes
  pipes where it is set down).
- **the Surgery Table and its seat**: with its Surgical Rig, a Cleaver laid on it, a filter and a villager lying on it.
  The table arrives with all of it. Its seat does not go with it: a contraption carries a rider only on Create's own
  seats (`Contraption#moveSeat` takes `SeatEntity` only, and puts riders back only on a `SeatBlock`), so the patient gets
  up where the table was and the seat, left with no table, goes. **Found:** the table dropped its attachment as it was
  taken up, so the rig arrived fitted and lay on the ground as well. The attachment is in the block's state, not its
  block entity (which Create takes away before it removes the block), so nothing else stopped it. A table moved whole
  (`movedByPiston`, which both Create and Sable pass when they move a block) keeps its attachment; it drops only when the
  table is broken. The test first passed one run in two: the rig was sometimes knocked out of the test's ground by the
  moving table, so each test now looks for the items it put in a little way round it too.
- Every test checks that nothing lies about as an item: nothing dropped or doubled on the way.

**Built into a Sable ship** (`blocksGoOntoAShipWhole`). Sable builds a ship by carrying each block's data into the ship's
plot and then removing the old block with its block entity still there (it only asks it to clear itself, if it is a
vanilla `Clearable`). Every block that drops its contents when removed dropped them: the jar's flesh, the Butcher's
Table's piece, the Surgery Table's Cleaver and rig, a backtank (as its own item), the steel table's bone and the rack's
diamond, all doubled, since the ship's copies kept theirs. The Mangler's output did not (it is `Clearable`), but its
filter would have. Sable's own answer is the block tag `sable:silent_assembly_removal` (a block entity it removes quietly
first), which datagen now fills with every block of ours that holds something (`BBTags.SILENT_ASSEMBLY_REMOVAL`); with the
attachment fix above, nothing drops.

**A hung carcass rides in its hook** (ARCHITECTURE 3.5's plan; `HookedCarcass`, `ShackleHookMovement`). Create can carry
nothing on a contraption block but its data, and used to take the hook's data and leave the carcass, which fell where the
hook had been (`movedShackleHookLetsGo` saw to it that the hook at least let go).
- The hook is an actor now (a `MovementBehaviour`, registered on the block as Create's own are). Create starts its actors
  while it puts the contraption together (`Contraption#startMoving`, called from each contraption type's `assemble`),
  before it removes the blocks and before the contraption entity is sent to anyone. The actor reads the carcass from the
  data Create took (not from the hook in the world: a train takes its blocks before it starts them), packs it and takes
  its bodies out of the world (`CarcassButchery.takeAway`, as the Spit Roast takes a whole carcass).
- **Packed**: the carcass's own record as it is saved, with its bodies' ids dropped and every other piece as a pose on
  the torso (as the resting form keeps them, `RestPose`), the torso's turn in the world, and where on the torso the hook
  held it. It goes into the hook's data (so it is set down with the hook) and a copy of what is needed to draw it into the
  actor's own data, which Create sends to every client with the contraption.
- **Drawn while it moves** (`HookedCarcassRenderer`, from `renderInContraption`, Create's way for an actor to draw in a
  contraption): the torso held at the tip with its turn, every piece at its pose, with the mob's texture and coats, its
  cut ends and its rot, the way the carcass's own cells draw it. It turns with the contraption. In bloodless mode it is
  the plated wreck, as every carcass is (`CarcassModels.drawBone`).
- **Set down**: Create loads the hook's data into the new hook, turns it (`TransformableBlockEntity`: a carcass set down by
  a bearing a quarter round has its turn and its belly's way turned with it) and the hook, on its first tick, hangs it
  again: the same record, its torso made anew and posed so the held point is on the tip, the rest unfolded and joined as a
  resting carcass unfolds (`CarcassRest.split`). Its rot counts the time it spent moving (it goes by game time).
- If the contraption does not move after all (a piston already at its end, a way blocked), the hook still in the world
  keeps the packed carcass too and hangs it again on its next tick. The actor finds that hook by the carcass it holds
  (`ShackleHookBlockEntity.holding`), not by where the contraption keeps the block: a piston with poles out keeps its
  blocks shifted back by them, so that lookup landed on a pole (found in review, below).
- `hungCarcassRidesAContraptionInItsHooksData`: while the hook moves the cow is out of the world and the actor has it to
  draw; set down, the same cow hangs at the moved hook's tip, skinned as it was, all six pieces back with five live
  joints, each within 0.35 blocks of where it was on the torso, and there is one cow, not two. `turnedHookTurnsItsCarcass`:
  a real Mechanical Bearing turns a block with the hook on its side a quarter round and stops; the cow hangs again from
  the moved hook, whole, its belly turned as far as the bearing turned (which way is read from where the hook ends up).
  `hungCarcassStaysWhenAPistonAtItsLimitGivesUp`: a piston already out to its full length has its motor's speed changed,
  starts the hook's actor and gives up; the cow hangs again from the same hook, made anew from its data. With the actor
  taken out, the first fails ("the cow is still in the world while its hook moves").

**Carcasses on Sable decks** (`CarcassRest`).
- **What holds a resting carcass up**: `isSupported` read only the world's blocks, so a carcass on a deck (whose blocks are
  in the ship's plot, far away) was found unsupported the second after it folded and unfolded again, for ever. It now
  looks, under each corner, at the world's block and at the block of any sub-level there (read in its own plot), another
  carcass included.
- **What pins it**: the fully locked joint that holds a folded carcass still was made to the world, so on a deck that moved
  it hung in the air while the deck went on. It is made to the deck when the carcass lies on one (a sub-level under it
  that is not a carcass, which might unfold and go), at the same point and turn in the deck's own plot and frame; the
  record says which (`restDeck`, not saved: the pin is made again after a reload, as before, and finds the deck again).
  Once a second, with the support check, it looks again at what it lies on, and pins itself afresh if that is not what
  it is pinned to (found in review, below).
- `restingCarcassIsPinnedToItsDeck`: a cow on a deck standing on four posts, nothing of the world under it, rests there
  for longer than two of the support checks, pinned to the deck.

**Shackle Hooks on ships** (`ShackleHookBlockEntity`). The checks were right (15.18, 15.28.1): a hook on a ship could not
hold a carcass at all. It joined the body to the world at the tip's position in the ship's plot, which Sable refuses (a
world joint's point may not be in the plot grid), so every tick it tried again and logged a warning, and the carcass
lay where it was dragged. Now:
- the joint is made to the ship's own body, at the tip in its plot, so the carcass goes where the ship goes;
- the hook hoists on a ship too, up to where the ship has carried its tip, the ship's own speed at the tip added to the
  hoist's; the belly-out spring turns with the ship;
- a ship built round a hook with a carcass on it (Sable carries the hook's data into the plot, and the body stays where
  it hangs) keeps it: a hook read back somewhere else lets its carcass go only if the carcass is not hanging at its tip
  (`shipBuiltRoundAHungHookKeepsItsCarcass`).
- `hookOnAShipHoldsItsCarcass`: a hook under a deck on posts hoists a cow up and holds it at the tip by a joint. With the
  joint made to the world again, it fails (the hoist alone kept the cow near the tip, so the test asks for the joint).

**A moving ship** (`carcassesRideAMovingShip`): a deck lying on the floor with a gallows at its back, a cow resting on
the deck and a cow hung from the gallows' hook, driven four blocks east over three seconds (its speed set each tick, as
a propeller would push it). The resting cow is still resting, within a quarter block of where it lay on the deck; the
hung cow is still held fast, within half a block of where the ship has carried the tip. With the pin to the world, or the
hook's joint to the world, it fails.

**Review findings put right.**
- *A piston at its limit lost the carcass on its hook.* Create starts a contraption's actors inside `assemble`, before
  it checks whether it can move (`MechanicalPistonBlockEntity.assemble` gives up after that when the piston is already at
  its limit or blocked, and throws the contraption away). The hook's actor had packed the carcass and taken its bodies
  out of the world by then, and looked for the hook still in the world at `anchor + localPos`; a `PistonContraption`
  keeps its blocks shifted back by the poles already out (its `addBlock` and `toLocalPos`), so for an extended piston
  the lookup landed on a pole, the hook was never given the packed carcass, and a speed change at the limit deleted the
  cow. The actor now finds the hook by the carcass it holds (the loaded hooks are listed as they load, not only once
  they first tick), and falls back on the position only for a hook not found so. `hungCarcassStaysWhenAPistonAtItsLimitGivesUp`
  fails without it ("the cow was lost when the piston gave up").
- *The bearing test used no bearing.* `turnedHookTurnsItsCarcass` packed, moved and turned the hook's data by hand and
  worked out the expected turn with the same hand-made transform. It now drives a real Mechanical Bearing (16 RPM for 20
  ticks, set down a quarter round by Create's own rounding) and reads the turn from where the hook was set down.
- *A ship built round a hung hook was not tested.* `shipBuiltRoundAHungHookKeepsItsCarcass` hangs a cow on a hook under a
  gallows in the world, builds the gallows and its deck into a ship, finds the hook in the ship's plot holding the cow
  fast at its tip, and drives the ship four blocks with the cow still on it. With the rule taken out (any hook read back
  elsewhere lets go), it fails.
- *A resting carcass kept the pin it was first given.* It was pinned once, when it folded (and again only if the pin
  broke), and a ship's deck under it counted as support, so a cow resting on the floor that a ship was then built out of
  (or assembled again at a dock) stayed pinned to the world, hung in the air as the deck moved away, and dropped when
  none of it was left under it; and a carcass whose deck was away when its pin was made again (its sub-level unloaded a
  while, or loaded after it) stayed pinned to the world when the deck came back, fixed on its own deck. `tickResting`
  now looks at what it lies on once a second and pins it afresh when that has changed (`restDeck` is read at last).
  Looking costs a field carcass one query of Sable's sub-levels over its own box, which finds nothing but itself.
  `restingCarcassGoesWithAShipBuiltUnderIt` fails without it ("the cow should be pinned to the deck built under it").
- *A carcass dragged onto a moving ship never rested.* Its stillness and whether it stayed put were measured in the
  world, where a carcass lying on a deck under way moves as fast as the deck, so it stayed a whole ragdoll held on by
  friction for as long as the ship moved: the costly case the brief rules out. When an awake carcass is moving in the
  world, `CarcassRest.tick` looks for a deck under its torso, and if there is one measures each body's speed less the
  deck's own speed at that body (as Sable works out a point's speed, `SubLevelHelper#getVelocity`), and where each lies in
  the deck's plot. `carcassDroppedOnAMovingShipRestsOnIt`: a cow dropped on a deck flying east at a block a second rests
  on it, pinned to it, while it flies, and stays where it lies on it. It fails without the change (it never rests).
  The ship in it is held on course before every physics substep (`ContraptionTests.cruise`), level and at its height,
  as a flying ship under way goes: driven once a tick while it slid on the floor, the deck jerked each tick and a cow on
  it crept back along it by a twentieth of a block a second, which is not a ship under way but the test's own driving.

**The cut leg that fell into the void** (15.18, item 4). Not made to happen again. `VoidLegTests` (off unless
`-Dbloodandbones.debug.void=true`) builds high over its test, where no other test looks: a grass platform whose top is a
chunk section's border, a cow lying on it and then hung four blocks over it for ten seconds, so that Sable drops that
section's copy of the ground (nothing is near it), then a front leg cut off. Four ways: straight down, over a chunk's
edge, and flung off at 6 and at 16 blocks a second across chunks. In 32 tries every leg stopped on the ground.
- What was read. Sable builds the ground a body can hit only in the chunk sections near a body
  (`PhysicsChunkTicketManager`: the body's box and one block round it, stretched down by one tick of its fall), once a
  tick before the physics steps, and drops a section 20 ticks after nothing is near it. A falling leg has its section
  built a tick or two before it gets there. Rapier's side keeps the ground only for sections it has been given, and a
  block changed in a section it has not been given is ignored, not half-made. No way was found for the ground to be
  missing where a leg lands.
- One lead, not followed to the end: a leg flung 14 blocks down at 16 blocks a second went 0.77 blocks into the world's
  grass in one tick before it was pushed out. At that speed a body moves 0.8 blocks a tick and Sable's physics has no
  continuous collision, so a thin leg can sink that far; it did not go through. A catch that lifted any body found half a
  block inside the ground back out, and built the ground again round it, was tried and taken out again: it could not be
  tested against the real fault, and lifting one limb of a jointed carcass out of its neighbours threw it sideways.

**Rule 4.** Nothing new is said to the player. The carcass drawn on a moving hook is drawn by `CarcassModels.drawBone`,
which gives the plated wreck in bloodless mode.

**The chain on a ship's hook.** Looking at the showcase found one more: the chain a hook draws down to its hanging body
was drawn from the tip in the ship's plot to the body in the world, across the whole plot grid, and so many links
overran the vertex buffer and crashed the game (a hook on a ship had never held a body before). The body is brought
into the ship's plot first, and no chain longer than a hook hoists from is drawn.

**Showcase** (a new step after the physics yard, before the Ponder scenes). `docs/screenshots/contraption_hook_moving.png`:
a Mechanical Piston half way through pushing a stone block with a Shackle Hook under it, the cow drawn hanging from the
hook while it rides in the hook's data (the log says one contraption and no cow in the world at that moment).
`contraption_hook_set_down.png`: two blocks on, set down, the cow hanging there again as a body, as it hung before.
`ship_moving_carcasses.png`: a spruce ship, a deck with a gallows, lifted just clear of the ground and flown east
with a cow hung from the gallows' hook and a cow resting on the deck, the camera following it. `ship_dropped_cow_rests.png`:
flying on, more slowly, with a third cow dropped on its deck beside the lying one as it went, seen once it has come to rest
there (the log says resting and pinned to the ship, the ship still going). In bloodless mode (`bloodless_contraption_hook_moving.png`) the
cow on the moving hook is drawn as the plated wreck every carcass is there, and no blood lies under it.

**Tests.** 20 new, all in `ContraptionTests`: ten for the blocks, one for building them into a ship, three for a hook on
a contraption and six for decks and ships (four of them, and the real bearing, came with the review's findings). The
suite is 598 tests (the switched-on-only `VoidLegTests` are not in it), and passed three times in a row on the final
code (the 594 before the review's findings had passed three times too, and once before the chain fix); the contraption
tests were also run four times over together. Each new test that checks a fix was
also run with its fix taken out, and failed: the hook's actor, the deck's support and pin, the hook's joint to the ship,
the Surgery Table's attachment and Sable's quiet removal; and after the review, the hook found by its carcass, the pin
that follows its deck, the stillness on a moving deck and the moved hook that keeps its carcass. Datagen run after the
last change changed nothing.

#### 15.30.1 Merged with the surgery screen and the ritual's proofs (verified)

The integration branch brought 15.29: the rebuilt surgery screen, the payload that names where its item comes from,
the missing limb that holds and wears nothing, and `RitualTests`. Both sections had been written as "15.29 (numbered
at merge)"; the surgery screen keeps 15.29, having reached the integration branch first, and this one is 15.30, with
the references to it in docs/BRIEF-AUDIT.md package 8, `VoidLegTests` and `DevShowcase` changed to match.
- **README.** Both sides changed the test list: it names the surgery screen's choices and the body through a real
  death as well as the contraptions and ships.
- **`SurgeryTableBlock`** merged by itself: the other side did not touch the block, so the table still keeps its
  Surgical Rig when a contraption or a ship moves it; the new screen and payload do not read the rig at all.
- **The showcase.** Both sides' shots are kept. The surgery shots sit in the body timeline and the contraption step
  after the physics yard, so they did not meet.
- **Generated resources** merged without a conflict, and datagen run after the merge changed nothing.
- CHANGELOG and docs/BRIEF-AUDIT.md merged by themselves, every line of both kept.

Nothing failed after the merge. The suite is 610 tests (598 here and the 12 in `RitualTests`) and passed three times in
a row. The showcase was run and looked at: `showcase_body_2` (the ragged left arm picked, the Hook Hand, Flesh Arm and
own arm offered, the zombie-headed surgeon at 141% and its 2-bucket price), `showcase_surgery_1` (the Hydraulic Arm
picked: unclip, three swaps and the Magnet Coil; the left eye hovered), `showcase_body_5` (the sword held, no shield in
the missing hand, no armour on the missing leg), and the contraption shots (the cow hung from a moving hook and set
down with it, and the ship's deck carrying a resting cow, a hung one and the cow dropped on it).

### 15.31 Groups first: any mob on day one, everything retuned by data (verified)

This closes docs/BRIEF-AUDIT.md package 2 but for what is left for the owner (the end of this section). Organ lists and
recipes were already data (15.16, 15.20.1) and are not touched. The guide for other mods' model authors that the
package asks for is docs/MODDED-MOBS.md.

**A body for any mob.** A mob with no rig file died as if the Meat Hook were any weapon (`CarcassAssembler.assemble`
returned null): a modded mob, the tropical fish, and a baby of a kind whose rig has no baby shape. Section 4.1 offered
two ways, a generic rig scaled to the hitbox, or the client reading the model and sending it. The code decided it: the
server builds every carcass's bodies, and must do so on a dedicated server with no client that has ever drawn the mob
(and in the game tests, which have none), so the server needs a body of its own; and a rig a client sends would be data
a client makes up, to be trusted on the server. So both are used, each where it can be: the server builds the body, the
client dresses it.
- **The body** (`carcass/rig/GenericRig`, data in `data/bloodandbones/generic_rig/<archetype>.json`, eleven files, one
  per archetype, named by the archetype's `"generic_rig"`). Each bone is a box measured against the hitbox: across and
  along in hitbox widths, up in hitbox heights, the front toward negative z as in the game's models. `GenericRig.build`
  turns it into an ordinary `Rig` of the mob, in model pixels, with the joints the naming rules give (`JointRules`,
  below) unless a bone's own `joint` in the file sets one (the same block a rig file's joints use), and a baby that is the grown one at half size about its feet. `RigManager.forEntity` returns it for any living
  mob with no rig file (`fileRig` is only files); it is built once per mob and parts-data generation, so it is the same
  object each time. Both sides build it the same way from data both have (the parts data is sent to clients; the
  hitbox is the entity type's), so nothing new is sent. Never for a player or an armour stand, nor for the mod's own
  mobs, nor for the new `#bloodandbones:no_carcass` tag (the ender dragon, which 4.4 keeps whole until it has a body
  plan, and the minion: stitched from carcasses, it dies by the server's minion rules, never as a fresh carcass that
  would give its flesh a second time).
- **Which archetype.** Section 3.1 of docs/PARTS-AND-TRAITS.md promised `match` rules for archetypes (rig counts,
  hitbox aspect, spawn category), and the quadruped file had some, but nothing read them: an unlisted mob fell to the
  biped. `MobGroup.Match` and `PartsData.matched` read them now, for a mob no file or tag lists: the highest-scoring
  rule that passes wins, ties to the lower id so every side agrees. The archetypes' rules: four or six legs and no arms
  (the quadruped, 50), two and two (the biped, 50), six or eight legs (the arthropod, 55); wider than tall and a
  creature (the quadruped, 20) or a monster (15); taller than wide (the biped, 20); much wider than tall and a monster
  (the arthropod, 25); a water category (the fish, 30); ambient (the flier, 30). The leg count now reads only rig
  files, never the generic body (which comes from the archetype this picks).
- **The look** (`client/FittedModels`). A generic rig says `"fitted": true`, and each bone's `part` lists the model
  part names it may wear ("tail|real_tail|tail1"). The client finds the mob's model under the layer named after it (as
  the game's own and most mods' are), or, failing that, the root of its renderer's model if that is a
  `HierarchicalModel` (the tropical fish's, whose layers are named for its two shapes). Each bone draws the shallowest
  part of a name it lists, with what hangs under it except other bones' parts, turned as it rests in the model and
  stretched to fill the bone's box; the skin is what its renderer gives a plain one of the mob, made once and never
  added to the world. A bone with no such part is a box in a patch of the skin. What each bone wears is kept by mob and
  bone name: two mobs of one archetype at one hitbox size have bones equal in every figure, and each must wear its own
  model's parts. Rot colour, maggots, wounds and
  bloodless plating go through the same drawing as any carcass.
- **Babies.** `RigManager.forEntity(id, true)` shrinks a rig with no baby shape to half about its feet
  (`RigManager.SHRUNK`), as the game draws such a baby; the baby rig is worked out once per grown rig object.

**Weight classes** (`carcass/WeightClass`, `data/bloodandbones/weight_class/<id>.json`). The brief's classes, for the
owner to confirm:

| Class | Size up to (blocks of flesh) | Floats or sinks | Rot time | Vanilla mobs in it |
|---|---|---|---|---|
| tiny | 0.05 | floats (1.3) | 12,000 | bat, cod, tadpole, parrot, rabbit, silverfish, endermite, allay, vex |
| small | 0.2 | floats (1.2) | 12,000 | salmon, cat, frog, bee, phantom, blaze, breeze, pufferfish, axolotl, chicken, ocelot, cave spider |
| medium | 0.7 | floats (1.1) | 20,000 | armadillo, fox, wolf, the skeletons, creeper, dolphin, enderman, villager, wandering trader, witch, the zombies, sheep, the piglins, the illagers, pig, spider, guardian, the squids |
| large | 1.6 | sinks slowly (0.9) | 24,000 | donkey, cow, mooshroom, snow golem, mule, turtle, goat, strider, the llamas, the horses, shulker, iron golem |
| huge | 10 | sinks (0.8) | 36,000 | hoglin, zoglin, panda, polar bear, wither, camel, warden, slime, ravager, magma cube, elder guardian, sniffer |
| colossal | above | sinks (0.7) | 48,000 | ghast |

A mob's groups (or its own file) may name its class (`"weight_class"`, the last layer naming one wins); with none, its
size picks one (`CarcassBody.weightClass`), so a modded mob has one on day one. Each class sets:
- `drag`: the drag penalty (15.28's curve, by the mass on the hook) is multiplied by it (`CarcassDrag.penaltyFor(mass,
  classDrag)`). All six ship at 1, so every penalty is as 15.28 measured it.
- `blood_per_weight`: mB a fresh carcass holds per block of animal (`CarcassBleeding.capacity`). All six ship at 1,000,
  the old constant.
- `buoyancy`: what water lifts, as a share of its weight when wholly under (`CarcassFloat`). Light classes float,
  heavy ones sink, as section 5 said.
- `rot_time`: for a mob whose rig and groups name none.

**Floating and sinking, and what Sable's water did to a carcass.** A carcass in water was lifted by Sable a block's
worth for every cell however small (`sable:volume` 1 on every carcass block), so all of them floated alike; that is now
0 (`PhysicsPropertiesProvider`) and the class's `buoyancy` lifts each body in water through the middle of its mass, by
its weight and how much of it is under. Testing it turned up a worse problem: a small carcass in water shook itself
apart. Sable works out its water drag once a game tick, from the speed at the start of the tick, counting each cell as a
whole block of water pushed aside, and holds that force through every physics step of the tick. A chicken's head (a
hundredth of a block of flesh) was pushed back about six times as hard as it was moving, so each tick it went the
other way faster, and within a second it was flung through the pool's floor. Undoing Sable's drag exactly each tick was
tried (`buoyancy.rs` worked through point by point); the least mismatch grew the same way, and it flung the chicken
upward instead. What holds: at the end of each tick a carcass body in water has its speed and turning put aside
(`CarcassFloat.postPhysicsTick`, on the new `ForgeSablePostPhysicsTickEvent` subscriber), so Sable, working out its drag
before the next, sees it still and drags it not at all; the first thing the next physics step does is give them back
(`CarcassFloat.giveBack`, first in `onPrePhysicsTick`). The water slows it instead by its real size, a share of its speed
taken away each step, which cannot overshoot. What reads a carcass's motion between ticks (whether it has come to rest,
whether it landed, a hauler steadying it) reads the put-aside speeds (`CarcassFloat.velocity`, `scaleAside`), and what
stops a body (a hauler laying a carcass down, the handover holding a mob killed in water in its pose) clears them too
(`CarcassFloat.stop`), or the next tick would give the speed back. A carcass lying still costs next to nothing here: a
body that has not moved is looked at again only once a second, one with no liquid anywhere in its bounds is dry without
sampling its cells, and a body lying still is neither slowed, nor has its (nil) speed put aside, nor is woken, so Sable
lets it sleep in water as on land (the lift is a force Sable wakes a body for only when it changes, as when water rises
round it). Water a Sable ship keeps out of its hull (its water occlusion) is dry for the lift and the slowing, so a
carcass in a ship's hold below the waterline lies as on land; Sable's own drag, which the put-aside undoes, does not
look at occlusion, so neither does the test for where it would drag.
`lightCarcassFloatsHeavyOneSinks`: a chicken built on the floor of a pool four deep comes up to the top, a cow built
with its back at the surface goes down to the floor.

**Rot time by group.** Every rig target named its own rot time (79 numbers, 14 different). It is now the rig's own if
a rig names one (a datapack still can), else the last of its groups to name one (`"rot_time"` on a group or a mob file),
else its weight class's (`CarcassBody.rotTime`). A group's or mob file's `rot_time` under a tick is refused with an
error, as a rig's and a class's are (rot divides by it). The mod's rigs name none now: the rotting overlay says 6,000, the
skeletal overlay 48,000, the golem and slime families never (1,000,000), the marine family 8,000, the cephalopods 12,000,
vermin and spirits 6,000, the felines and arachnids 20,000, the equines 36,000, the guardians 24,000; 52 of the 79 come
from groups and classes, and 25 mobs keep a figure of their own in their mob file (a new, otherwise empty file for the
tadpole). `rotTimesAreAsTheyWere` holds all 79 to the old table.

**Butchery by group.** Every mob's butchery table is worked out at once from its rig (its own or its generic body) and
what its groups say (`ButcheryManager.byGroup`, with `ButcheryDerivation` moved out of datagen into `carcass/butchery`).
A group's or mob file's `"butchery"` is what a rig target's butchery section was, any of its fields; the layers merge
field by field, the last to set one winning. Groups that say something: the rotting overlay (rotten flesh, no hide),
the skeletal overlay (bones, no meat), the golem, elemental, spirit and vermin families (no meat, hide or bone), the
slime family (slime balls), the fowl (chicken and feathers), the grazers (beef), swine and piglins (porkchops), small
prey (rabbit and its hide), the fish archetype (cod and bone meal), the arachnids (spider eyes and string), the
cephalopods (ink). The rig targets' butchery sections are gone, and datagen writes no tables: what each vanilla mob
does differently from its groups went into its mob file's `"butchery"` (61 mobs say something there, most one or two
fields, such as a goat's mutton or a cat's thinner hide; 18 butcher wholly by group: the chicken, cow, pig, hoglin, the
horses, the skeletons, the wolf, the villager...), so a group retuned (the fowl's hide, the grazers' meat) retunes every
mob in it that does not say otherwise. Checked table by table: every vanilla mob's table worked out this way is the one
datagen wrote before (`butcheryComesFromGroupsAndRetunesByGroup` holds all 79 to a fingerprint of the old tables). A
table file (`data/<ns>/butchery/<entity ns>/<entity path>.json`) stays the optional override a datapack may give one
mob; the mod ships none. Clients are sent every mob's table, worked out (`ButcheryManager.everyTable`), for the recipe
viewer. The tropical fish: its body gives bone meal by the fish archetype, and tropical fish (not cod) by one line in
its own file.

**The knobs moved out of code.** Kept at today's figures, which stay in the code as the defaults, and
`movedKnobsKeepTheirOldFigures` holds every default to the constant it replaced:
- server config (`bloodandbones-server.toml`): `[carcasses]` the drag curve's two ends (`drag_light_mass`,
  `drag_light_penalty`, `drag_heavy_mass`, `drag_heavy_penalty`), `cuts_to_sever`, `cuts_to_butcher`,
  `strokes_to_skin` and `carry_mass` (was `LIGHT_MASS`); `[rot]` the spoil thresholds `going_off_below` (0.6) and
  `rotting_below` (0.3), which the yields, the scraps, the piece tooltips, Create's fresh and rotting filters and the
  minion builder all read; `[machines]` each machine's stress per RPM and pace; `[cooking]` the Spit Roast's times;
  `[cybernetics]` the throttle's spool time, its full-spool drain and a fired module's cost, and each module's upkeep;
  `[necrosis]` the rates by swing, by blocks walked and by meal, and a perfusion's blood and what it takes away;
- data: blood per weight (the weight classes); the slime's and magma cube's baby yields (`"baby_yield"` in their mob
  files, which any group can set); the Beheader's skull map (the NeoForge data map
  `data/bloodandbones/data_maps/entity_type/beheader_skulls.json`, item and chance, so another mod's mob with a head of
  its own can join); every implant's figures and drain (`data/bloodandbones/implant/<item>.json`, written by datagen from
  the figures each implant item is built with, read through the parts data so clients have them: `ImplantFigures`, each
  side from its own copy, so a client never reads the last single-player world's figures on another server). What an
  implant is (the part it replaces, its fuel, its ability, its module slots) stays with the item. Whether an implant is
  crude (the safety floor anyone may fit without a surgeon) is judged on the figures in use, so a datapack that makes a
  peg leg strong also makes it need the surgeon.

**Fewer hand-written rig overrides.** What the rig targets spelled out again and again is now read from the part names
and the model's structure (`RigDerivation.withNamingRules`, `JointRules`), and the target says only what the rules get
wrong:
- decor parts (a nose, a beak, an ear, a horn, an eye, a hat, a jacket, a mane, a hump, a fin, a spike, the tip of a
  wing or tail, sleeves and trousers) are never bodies: under a body they draw with it, loose they ride with the head,
  their side's arm or leg, or the torso;
- tack (saddles and their straps, bridles, reins, chests, a foal's spare legs) is left off;
- the torso is the shallowest part named `body`;
- links named `segmentN` hang one off the next toward the torso; a front body (`body0` before the torso, or
  `upper_body`) carries the head and what joins at the front;
- joints by name for tentacles, segments, a body's back half (`_back`) and legs lying flat (a spider's).
The targets went from 154 joint entries in 39 files to 74 in 26, 103 merges to 7, 95 attachments to 44, 74 named torsos
to 14, 38 parents to 5 and 86 hidden parts to 20. Every rig and butchery table the data run writes is what it was
before, but for the order of a few lists that do not matter (the order a cod's fins and a slime's face are drawn in, and
the order of a horse's hidden tack), checked by comparing every generated file with those lists sorted.
`namingRulesGiveWhatTheTargetsSpelledOut` holds the rules to the rigs. The overrides left are deliberate: the zombies'
and skeletons' heads, arms and legs (tuned as a family), the horse family's necks and tails, the wither's heads, ribcage
and tail and what hangs off its shoulders, the ghast's tentacles (looser than a squid's), the turtle's flippers, the
chicken's wings, the ravager's neck, the shulker's lid, the wolf's shoulders and tail, the snow golem's upper body, the
rabbit's haunches and feet, seven heads that sit on top of their bodies but must not collide with them (15.28), the
torsos the rules cannot find (the spiders' abdomen, the guardians' and blaze's heads, a salmon's front half, the
silverfish's and endermite's middle links, the shulker's base, the slimes' cubes, the snow golem's lower body, the
wither's shoulders), the parts of a few mobs drawn as one piece (a bee's legs and wings, a blaze's rods, a breeze's rods,
a magma cube's layers, a silverfish's layers, an allay's and a vex's arms, a parrot's legs and tail, a rabbit's tail,
the tadpole's tail, a cat's and an ocelot's tail tips, the armadillo's tail, a parrot's second head part, the horse
family's head inside its head parts), a few parts hidden that are not tack (the illagers' folded arms and hats, the
piglins' cloaks and ears, the frog's croaking throat and tongue, the turtle's egg belly, the breeze's wind, the
armadillo's rolled-up shell), and every collision box smaller than what is drawn (47, in 21 files).

**Tests** (`gametest/GroupTests`, 11): `mobWithNoRigFileBecomesACarcass` (a cow with its rig file taken away, in a batch of
its own that runs before any other so no other cow is about: killed with the hook as a player kills one, it is the
generic quadruped's seven parts and six joints at its hitbox's size, goes down where it stood, holds its weight's worth
of blood and loses a leg at its joint; given back its file it is its own rig again. The hide is the test's own hold,
given back however the test ends, so copies of it run together by the repeat switch each keep and give back their own),
`minionKilledWithTheHookLeavesNoCarcass` (where killed minions scatter, one killed with the hook scatters and leaves no
carcass),
`tropicalFishBecomesACarcassAndButchersByGroup`, `babyWithNoBabyShapeBecomesAHalfSizeCarcass` (a baby wandering trader),
`unlistedMobsTakeTheirArchetypeByShape` (the giant a biped at its size; the ender dragon, a player and an armour stand
nothing), `weightClassesComeFromSizeOrGroups` (on a copy of the server's data with a class and a mob file added),
`lightCarcassFloatsHeavyOneSinks`, `butcheryComesFromGroupsAndRetunesByGroup` (every vanilla table as it was, the giant's
from the plain defaults, and on a copy of the server's data the fowl's hide doubled doubling a chicken's feathers and
the grazers' meat changed changing a cow's but not a goat's or a llama's), `namingRulesGiveWhatTheTargetsSpelledOut` (with
a generic body file that sets a joint of its own),
`rotTimesAreAsTheyWere`, `movedKnobsKeepTheirOldFigures`. A batch named `bloodandbones_alone_first` now runs before all
others (`GameTestServerMixin`). One trap found on the way: a step a test sets out from inside another step's
`runAfterDelay` may run more than once (the game's map of steps is added to while it is read), so a test's delayed steps
are all set out at its start. Another: a carcass built at a test's first tick on blocks the test has just set can fall
through them, before Sable has their colliders (the float test's chicken first went through its pool's floor). The first
full run of this work lost `cowDownEachPath` that way, once in four runs: its Surgery Table's cow was gone before the
Surgical Rig began, and the rig had nothing to cut. Its cows are now built once the arena has stood 20 ticks; it passed
30 times on its own after, and every full run.

One failure was not this work's, but is put right here: `hindLegHookComesRoundRearFirst` (15.28) failed once in a
full run (the cow stayed 66 degrees off the way it was dragged), and repeated 80 times it failed once here and once on
main at 894f097, the same way. Traced tick by tick over hundreds of runs, the cow did come round, but fast, and swung on
past rear first by as much again, rocked there, or came to lie crosswise. The aim spring that turns the hooked limb to
the hand (`CarcassDrag.aim`) is sized by all the mass on the hook, so once the limb is at its joint's limit it swings the
whole carcass round; its damping was sized by the limb and killed only the limb's own swing, so the carcass's turn was
all but undamped. Damping sized by the whole carcass cannot go on the limb (it would fling the light limb about while it
swings free within its joint), so a drag now takes a share of the carcass's turning away each physics step at its torso,
about the upright only, so it still rolls and tumbles as it is pulled (`CarcassDrag.steadyTurn`, 6 a second, the same
figure as the limb's damping). Repeated with the head-hook test, 80 runs of each before and after: the hind-leg hook's
worst run went from 103 degrees off (the middle run 21) to 26 (the middle run 11), the head hook's from 31 to 22.
Over 1,030 runs since, it failed twice, both the same rarer way: the hind leg short of its joint's limit, the torso
trailing straight behind the hook but turned some 40 degrees (the hip sits off the spine, so nothing turns it further),
then a swing the wrong way. That is about once in 500, where it was once in 60 to 80; it is not put right. Two ways
tried and dropped: judging whether the hooked part touches its dragger by its own box, not Sable's bounds of its body
(a whole block turned and squared up, which "touch" at arm's length a third of the time): at the start the hook, drawn
across its dragger's front, then lay in them and was flung out as they walked past, and the cow came round the wrong
way far more often (23 runs in 80).

Found while checking the drag's other tests over and over: `grapplingSpoolHandsACarcassToTheDrag` failed about once in
80 runs, the same before the change. The spool hands a carcass it reels in to the Meat Hook's drag once it is at hand,
and at hand was within three blocks of the player's middle. Its pull along the ground is a nudge of speed each tick,
less than the ground's friction takes from a carcass at rest, so a carcass comes in on the speed of its first yank, and
now and then the ground stopped it 3.3 to 3.6 blocks off, where it crept a hundredth of a block a tick until the reel
ran out. At hand is now within the Meat Hook's own reach of the player's eyes (5 blocks, `CarcassDrag.HOOK_REACH`),
where a player could hook it with the hook itself; 300 runs, none failed.

The suite is 589 tests and passed three full runs in a row after the last change (the review's findings below
included).

**The showcase** (`DevShowcase`, a groups yard after the physics yard). `showcase_groups_0`: a row of carcasses with no
rig file of their own, knocked down as a kill knocks them: a polar bear, a zombie villager, a cave spider and a bat
(whose files are taken away for the rest of the run; none of them is in another shot), a tropical fish, and a baby
wandering trader. Each wears its own model's parts in its own skin: the polar bear's white body, legs and snouted head,
the zombie villager lying in its robe, the cave spider's teal body and red eyes, the bat's dark wings; the fish is its
white base skin (the tint of its pattern is lost), and the baby is half size. `showcase_groups_1`: a glass tank of water
four deep from above, the chicken floating at the top and the cow sunk on the floor (the log gives the chicken's body a
fifth of a block under the surface and the cow's a block above the floor). `showcase_groups_2`: a husk and a drowned
lying face up, their files taken away too, the same generic biped at the same hitbox as the zombie villager drawn before
them, each in its own model's parts and skin (the husk's sandy hide, the drowned's teal flesh), not the zombie
villager's. The earlier shots are unchanged. Run again after the review's fixes and the drag's damping, and looked at:
the same three groups shots (the log puts the chicken's body a fifth of a block under the surface and the cow's a block
above the tank's floor), the physics yard's dragged and struck cows, and the minions' row.

**The review's findings put right.** The minion, which by its shape passed for a biped, was becoming a fresh carcass
when killed with the hook (its flesh given twice where minions scatter, a carcass left where they are destroyed, a brass
one bleeding): it is on `no_carcass`, and no mob of the mod's own gets a generic body. The fitted models were kept by
bone, and bones of two mobs of one archetype and size are equal, so the second mob drawn wore the first one's model; now
by mob and bone. The water was looked at every tick for every carcass body, dry or not, with a wake-up every physics
step for any body near water; now as above. A carcass laid down in water by a hauler, or held by the handover in water,
got its put-aside speed back on the next tick; now cleared. `rot_time` in a group or mob file was not checked; now as
rigs. The cow test hid the cow's rig for the whole server until it ran out, and a failure left it hidden; now its own
hold, given back on every exit. Implant figures on a client read the server's store when it held anything; now each
side its own. A generic body file could not set a joint; now it can. `crude()` read the built figures; now the data's.
Butchery by group was shadowed for every vanilla mob by its generated table; now as above.

**Left for the owner.**
- Size-2 slimes still split as they die, as 4.4 decided (only the smallest leave a carcass). The audit counts them among
  the mobs that miss out; which is wanted?
- The weight classes above: the list, where the sizes fall, and the floating figures. The drag penalty stays 15.28's
  curve by the mass on the hook, which each class only multiplies (1 for all); should a class set the penalty itself?
- The ender dragon stays on `no_carcass` until it has a body plan (4.4).
- Section 4.1's client-sent rigs and a `/bloodandbones rig export` command are not built; a modded mob's own rig comes
  from a datapack or a rig target (docs/MODDED-MOBS.md). A family by Java class (docs/PARTS-AND-TRAITS.md 3.1, through a
  throwaway instance) is not built either; tags and the archetype rules cover a modded mob meanwhile.

### 15.32 (numbered at merge) Dead animals look dead: no carcass is left standing on its legs (verified)

This takes docs/BRIEF-AUDIT.md package 1 further ("dead animals should look dead, limp and heavy"). One part of it waits
on the owner: which way a carcass dragged on its feet goes down (BRIEF-AUDIT decision 14, below). The physics
measurement (`-Dbloodandbones.debug.rig_compare=true`) was run eight times over on main's physics, on this work's first
version and on this one, all with this work's measurement, and every number below is from those runs unless it says
otherwise.

**Why a carcass stood.** The showcase's cow struck in the face stood where it was, and the cow set down on a ship's deck
stood on it. Measured, three things held a carcass up, and the resting form then pinned it that way for good:
- **Legs that cannot fold.** A leg is one rigid body on one joint at the hip. Four of them hold a body up: nothing tips
  one set down standing, and splayed to the ends of their joints they prop it like a trestle. A pig struck in the face
  swept its front legs back to the end of their swing (75 degrees) and stood on them as on struts, its torso 0.23 blocks
  up where it had stood 0.38 up. A cow struck in the face sat up like a dog, its rear on the ground and its chest a
  third of a block up on straight front legs, which the measurement took for down (its lowest corner touched the
  ground). A pig in the showcase's row was left balanced on end, belly toward its killer.
- **A blow too weak to topple it.** A blow to the head is held to what the head can take (15.28), and the rest of it was
  lost: a polar bear struck in the face moved 0.04 blocks and stood, upright to within a hundredth of a degree. A zombie
  or a villager struck from the side on its arm stood too.
- **The resting form.** Once still for three seconds it folds into one body, pinned as it lies: standing.

**What was built.**
- **Its legs give way** (`carcass/CarcassSlump`). That they do at all is made up: the rigs have no knees to fold at, so
  a carcass left standing is tipped over by code. Which side it goes to is its own. A loose carcass stands when its
  torso is within 45 degrees of upright, a leg reaches below it by more than a quarter as far as its legs held it (at
  half, a horse held up on splayed legs at half its height passed), that leg stands on something, and nothing (the
  ground, a rack, a deck, another carcass) is that close under the torso; or, on four legs, when it sits up on its
  front ones, the front of its belly that high and its rear lower; or, on four legs or more, when it is balanced on end,
  its spine within 15 degrees of straight up or down and what it reaches lowest with resting on something. One pitched
  onto its nose by a blow from behind is down: that is what the brief asks of that blow (a pig lies 64 degrees nose
  down, its spine 26 degrees from straight down). One held up in the air, its legs hanging, is not standing, and one in
  water floats or sinks and is never standing (`CarcassFloat.inLiquid`).
- **How it goes over.** After half a second standing nearly still (as it really moves: in water its speed is put aside
  between ticks, CarcassFloat; on a ship under way, still on the deck), the whole carcass is set turning over its
  outermost foot on one side, every part together so no joint fights it, a tenth faster than it takes to carry its
  middle over that foot, the same for any weight, as a fall is. On four legs it goes onto the side its torso leans to,
  however little, or failing that the side its weight lies over its feet; standing upright (a zombie) the way its
  weight lies over its feet, or failing that the way its torso leans; balanced on end the way its weight lies over
  that end, or failing that the way its top end leans out. As it goes, the legs of one on four legs slide out the other
  way, at 7 blocks a second for a cow's legs (0.75 blocks) and slower for shorter ones, as the square root of their
  length (at 7 a baby wolf slid three blocks). Set down standing, a cow lay 2.1 blocks from where it stood with its legs
  kept under it, 1.7 with them sliding at 3 blocks a second (this work's first version), 0.6 to 1.2 at 7; at 9 a sheep
  went on sliding. Going over is what moved it: turned over the outermost foot, its middle swings out as far as it
  stood high, and the legs sliding the other way take the foot it turns over back under it. Standing so squarely that
  nothing says which way (built standing, not struck), its legs slide out from under it at 1 block a second, which only
  breaks the balance; the next time it leans some way (at 3 a sheep splayed its legs and then went over them and slid
  two blocks; at 7 a cow went on over onto its back). The first version picked the side from the carcass's id when it
  stood square; nothing does now. Until it is down it is not let rest.
- **How many times.** Its legs give way at most six times while it stands, each a little harder (half a radian a
  second more), and then a carcass wedged upright somewhere is let rest as it is. The count starts again once it has
  been down two seconds, when it rests or is woken, when it is hung, and when a drag takes it up or lets it go; how long
  it has stood carries on (started again as it was hooked, a cow dragged by a hind leg went down five ticks later, the
  pull had turned it by then, and it came round rear first less often: 6 runs in 12 more than 60 degrees off, none so).
  The first version counted for the carcass's whole life and never saved it: 7 of 22 carcasses measured used all six
  within one drag, after which one set down on its feet would fold standing for good, and each give-way was harder than
  the last for good.
- **Dragged along on its feet** (a sheep hooked by a hind leg slid along standing, facing the wrong way) its legs give
  way however it moves, and it goes down away from the hooked leg, so that leg ends on top, free to lead. That side is
  picked, not left to the drag, and the owner is asked to decide (BRIEF-AUDIT decision 14). Left to its lean, a cow
  dragged by a hind leg went down onto that leg about as often as not (36 degrees off rear first, median of eight, and
  more than 60 in 3 runs of 8, which fails `hindLegHookComesRoundRearFirst`); left to the way it is pulled (away from a
  pull on a part below its middle, as feet pulled from under a body), a sheep did (52); not tipped at all while
  dragged, as on main, the sheep is dragged along on its feet (134). Not a body that stands upright (a zombie): the leg
  it is dragged by pulls it off its feet. Its legs do not slide out while it is dragged: they kicked out under a sheep
  pulled by a hind leg and it came round rear first less often (61 degrees off where it was 9 without, over four runs,
  its side left to its lean).
- **Held where it is worked.** A carcass standing with its feet on a table or a machine is held there, not tipped off it
  (a block tag, `bloodandbones:holds_carcasses`: the Butcher's, Surgery and Steel Tables, the Bleeding Rack and the four
  machines; a datapack adds its own). Its feet are the parts it reaches lowest with, and more than half of them must rest
  on such a block. The first version held one if any part's lowest corner was over one, so a cow on the floor beside a
  rack with a hoof in its tray, or its head drooped over a table, was held and folded standing.
- **A blow carries on into the body.** Of what a light part struck could not take (15.28's limit), a fifth goes on into
  the torso where that part's limb joins it (`CarcassAssembler.blow`). At two fifths a cow struck in the face slid 1.7
  blocks, as it did before the limit.

**Tests.** `PhysicsTests.dead{Cow,Pig,PolarBear,Horse,Sheep}NeverSettlesStanding`: four of a kind on open ground, struck
in the face, in the flank and from behind, and one built where it stood and not struck at all, each watched until it
folds, which it must, and then down (`RigScenarios.stood`: tipped 45 degrees or more, or its torso no higher than a
quarter of how high it stood, and not sitting up on its front legs, and, on four legs or more, not balanced on end).
Run on main's physics all five failed, ten carcasses of the twenty standing. Now all twenty lie down, and the test's
log says how far each lies from where it stood: set down standing, a horse 0.2 to 0.3 blocks, a pig 0.5 to 1.0, a cow
0.6 to 1.1, a sheep 0.5 to 1.0, a polar bear 0.6 to 1.3 (the first version: 1.2, 1.1, 1.7, 0.9, 1.6). New:
- `pigBalancedOnEndGoesOver`: a pig set on its snout and one on its rump, spine straight up and down, both go down. With
  the rule for a carcass on end taken out, the one on its rump rested on end, 3 runs of 3.
- `carcassStandingOnATableIsHeldThere` (a cow on a patch of Butcher's Tables rests standing where it was set down) and
  `carcassStandingBesideARackGoesDown` (a cow on the floor with one front hoof in a Bleeding Rack's tray goes down;
  with the first version's rule it rested standing, 2 runs of 2).
- `GroupTests.carcassesInWaterAreNotMadeToGiveWay`: a chicken floating up in a pool and a cow sunk to its floor are
  never tipped (without the water check the sunk cow's legs gave way twice in each run).
- `UnprovenTests.grapplingSpoolPullRisesWithTheThrottle` (below).
Tests put back as they were on main, now that a carcass going over moves less: `blowToTheHeadSnapsItBackWithoutFlingingIt`
again allows 1.5 blocks in any direction (the first version allowed 2), and it now watches the cow for 220 ticks and
checks that it has gone down by tick 210, so its going over is in what is measured: 1.26 to 1.30 blocks. The two cows of
`trolleysQueueOnAChain` and `trolleysPassEachOtherAndEmptyOnesLeave` face across the chain again, as on main (the first
version turned them along it, since they went over onto each other).

**Physics, before and after** (eight runs of every scenario on the twelve mobs; mobs meeting the bar in most runs, of
twelve, runs meeting it, of 96, and the median of all runs; "first" is this work's first version):

| Bar | Before | First | After |
|---|---|---|---|
| Face: down, not left standing | 7 (53) | 12 (96) | 12 (96) |
| Behind: down, not left standing | 10 (84) | 12 (96) | 12 (96) |
| Flank: down, not left standing | 11 (88) | 12 (96) | 12 (96) |
| Flank: on its side, fallen away from the blow | 7 (56) | 9 (72) | 9 (72) |
| Flank: leaning within 60 degrees of the blow's way | 8 (67), 44 degrees off | 10 (80), 33 | 10 (80), 39 |
| Hind-leg hook: rear first within 45 degrees | 3 (35), 64.5 degrees off | 6 (53), 39.5 | 6 (50), 42.5 |
| Face: knocked back 1.5 blocks or less | 11 (85), 0.90 | 10 (81), 0.94 | 10 (80), 0.93 |
| Face: within 1.5 blocks of where it stood | 11 (82), 0.94 | 8 (64), 1.22 | 10 (80), 1.05 |
| Behind: 20 degrees nose down at rest; head on the ground | 5 (40); 8 (65) | 5 (40); 8 (67) | 5 (39); 9 (72) |
| Held upright: head droops; held on its side: legs hang | 10 (80); 8 (68) | 11 (86); 9 (69) | 10 (80); 8 (68) |
| Head hook, hung, steps, cut leg, joints, sinking, folding | | within a few runs of before | within a few runs of before |

Left standing, by mob, before: struck in the face the cow, llama, pig, polar bear and sheep (every run), struck from
behind the horse (every run) and the llama (half), struck in the flank the zombie. After: none. Struck in the face each
now lies on its side, 90 degrees over (the polar bear mostly down on its belly, tilted 42 degrees), where before the
cow, llama, pig and sheep sat up at 9 to 21 degrees and the bear stood at 0. How far from where it stood,
struck in the face: the polar bear 0.54 blocks (first version 1.74, main 0.04), the horse 0.99 (1.39, 1.41), the pig
1.03 (1.17, 0.61), the cow 1.18 (1.35, 1.22), the llama 1.40 (1.63, 1.50), the sheep 1.25 (1.18, 1.22); the two outside
1.5 are the villager (1.61, as on main 1.56) and the zombie (1.53, main 1.39), which go down as they did. The measurement
itself changed in two ways: a carcass held up in the air (2a, 2b) is kept from being made to give way, as it is kept from
folding (the first version kicked the villager and the zombie six times a run while their limbs were measured), and a
carcass struck in the flank is measured the last tick before it folds if it folds before it keeps still (the chicken's
twitching legs let it rest first: in 7 runs of 8 the first version measured one body and read nothing).

**Hooked by a hind leg** (the brief: "comes round arse-first"; degrees off rear first while dragged, median of eight;
main, first version, now): sheep 134, 10, 15; llama 77, 19, 23; pig 52, 49, 30; cow 15, 19, 13; zombie 21, 20, 20;
villager 9, 9, 4; polar bear 121, 42, 58; wolf 105, 94, 68; horse 50, 63, 82; chicken 83, 126, 110; rabbit 58, 62, 83;
spider 89, 83, 107. The sheep, llama and pig come round; the horse does not, and is worse than on main (it tumbles over
its back while dragged; its tilt from upright swung between 30 and 160 degrees through a drag); the chicken's leg joins
its body under its middle, so which end of it leads says little (the measurement also reports whether the hooked part
itself leads, `part_leads_deg`).

**Flank blows.** The zombie and the villager, struck on the arm, now go down on their side away from the blow; the
wolf did before and still does. Not improved: the chicken, struck on its wing, falls forward or back rather than to the
side (66 degrees over, 83 off the blow's way); the rabbit slides and lies on its belly, not its side; the spider lies
flat, legs sprawled.

**The Grappling Spool's pull on a carcass** (`ModuleActions.carcassPull`) is the ground's grip on one lying on its side
(0.18 blocks a second a tick, `GRIP`) at the bottom of the throttle, and rises with every step of it to a quarter of the
haul speed at the top (0.26), as it was. The first version floored a quarter of the haul speed at the grip, so the
bottom 45 per cent of the throttle all pulled alike and the Soul Blood spent there bought nothing.
`grapplingSpoolPullRisesWithTheThrottle` checks it. The Magnet Coil pulls at the grip (it pulled 0.12, less than the
grip, and drew in only a carcass left standing).

**What else it changed, and why the tests changed.** Carcasses built standing are the setup of many tests, and every one
of them now goes over before it rests:
- Tests that hook a named leg of a cow that has gone over hook the one on top (`lyingCarcassHooksThePartAimedAt`,
  `meatHookDoesNotHookThroughAWall`, `meatHookDragsByLeg`): the one under it is hidden behind the other, or pinned under
  the body.
- Tests timed on a cow folding a fixed time after it is built wait for it to fold (`restingFormFoldsAndUnfolds`,
  `punchWakesRestingCarcass`, `restingCarcassFallsWhenUnsupported`); the fold/unfold test compares with the poses of the
  last tick before it folded. The rigged-mob assembly tests look once a falling carcass has landed.
- `raycastHitsLegs` looks at the legs before they give way (it looked ten ticks after building, the tick the legs are
  kicked out, and a leg was no longer where its middle had been read).
- `droppedCarcassThuds`: a cow going over thuds as its head comes down on the ground, as it should, so the quiet one is
  a cow lying down, woken where it rests.
- The cow meant to rot in `rottenCarcassFallsApart` faces away from the blue ice (going over toward it, it came within
  the cold's reach).

**The showcase.** `showcase_1`, "machines from above", was photographed from the ground: the player was put four blocks
up but not flying, and fell before the picture. A view can now hold the player where it was put (`View.fly`), and this
one does. The dropped cow on the flying ship is photographed 260 ticks after it lands, not 180. Looked at, before
(main) and after: `physics_1`, the three struck cows: before, the one struck in the face stood between the other two;
now it lies on its side beside the one struck in the flank, touching it, not over onto it (the first version's went
over onto it); the one struck from behind lies nose down as before. `contraption_2` and `contraption_3`: the cows on the
flying ship's deck lie on their sides. `showcase_0`, the row: the first version's picture still had a pig balanced on
end; in this run no carcass in the row was left so, and the pig lies on its side (a carcass on end is tipped now, and
`pigBalancedOnEndGoesOver` tests it). `groups_1`: the showcase's log has no carcass in the pool made to give way (the
first version's floated chicken was spun onto its side, and its sunk cow four times over). `physics_2` is unchanged. In
docs/screenshots, `ship_moving_carcasses.png` (`contraption_2`), `ship_dropped_cow_rests.png` (`contraption_3`),
`showcase_1.png` and `struck_cows_down.png` (`physics_1`) are this run's pictures. `physics_0`, the cow dragged by a hind
leg seen from in front of its dragger, shows only the player, as 15.28 noted: left as it was.

**The suite** is 631 tests (ten new) and passed three times in a row.

**Left open.**
- Which way a carcass dragged on its feet goes down (BRIEF-AUDIT decision 14).
- The horse, polar bear and chicken hooked by a hind leg, and the chicken, rabbit and spider struck in the flank.
- Going over still moves a carcass's middle up to about a block and a quarter, more than a body buckling at the knees
  would; the rigs have no knees. A sheep set down standing now and then goes on over onto its back (2 runs in 11): its
  box is flatter than it is wide, and lies lower so.
- A carcass on a table or a machine still stands on it if set down standing (it is held there): the showcase's pig on
  its Guillotine and zombie on its Beheader stand as before.
- `physics_0`'s camera (above).
