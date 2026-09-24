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
  Fitting anything into a ragged stump (a prosthetic, or the limb back) takes a bucket's worth of blood as well,
  from a bucket or any fluid item the operator carries, a worn Fluid Backtank included. Once fitted the stump is
  dressed: take the implant out and it is an ordinary empty slot. Swapping an implant straight in for flesh
  leaves no stump. `raggedStumpCostsBlood`, `raggedStumpPaidFromBacktank`.
- **The safety floor holds**: fitting, reattaching, swapping implants and modules never need a surgeon, and blood
  is cheap and early (a Bleeding Rack needs no power), so a crude prosthetic can always go on.
  `safetyFloorNeverNeedsSurgeon`.
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
   zombie villager) come with the per-mob data.
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
saved the old way falls apart on its first tick, dropping what it carried.

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

### 15.7 Slice 4 as built: armour B (verified in tests; the look still to be checked on screen)

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
    are slice 3.
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
  player pays nothing). Too little refuses it with a word on the action bar ("Not enough blood in your tank"), and the
  next press moves on. A minion's `MinionGoals.UseOrgan` (no move or look flags, so it fires while closing in) fires one
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
  host taken off the list it hits (`ExplosionEvent.Detonate`); blocks only with the trait's `block_damage`,
  `minion_block_damage` and mobGriefing (then fire too); a fuse hisses and smokes first; a minion then powers down.
- **visibility** {multiplier, vs}: `LivingVisibilityEvent#modifyVisibility`, the change scaled by the trait strength.
- Look and sound: a wet burst of blood where a blink leaves and where a blast goes off, scraps of meat from a minion's
  self-destruct, sparks from brass; bloodless mode draws none of the blood (the drops' own check) and keeps the vanilla
  portal and explosion particles. Vanilla sounds pitched down and layered (slime and honey for the squelch).
- **Traits** (new data, with names, descriptions and a bloodless reading): wall_climber (sums), glider, bouncy,
  powder_walker, silent_steps, quick_draw, trample, lava_walk (fire does not hurt it either), ender_mask, ender_calm,
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
  ageing once set down.
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
  item and harvesting by these lists are slice 3's), so in play only a piece or build given one directly has it.
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
  amphibian set dries out at 60 s, not 120; leap for the fox's and phantom's pounce; flinger for the hoglin's 15% toss;
  echo sense for the dolphin's Melon; fatigue aura for the Elder Eye's armour side; featherfall for the ghast's boots;
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
- **Section 9 tests still not possible**: `skeletonGivesMarrow` (harvesting organs by these lists), `phantomWingsLiftCow`
  and `chickenWingsDoNot` (flight from wing lift), `moddedZombieByTagIsRotting` (a modded entity type to tag).
