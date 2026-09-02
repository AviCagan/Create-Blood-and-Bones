# Create: Blood & Bones — Technical Proposal

**Status: decisions from the first review are folded in (marked *decided*); a few items remain open
(§12).** Everything marked *verified* was checked by reading the actual Create 6.0.11
(`mc1.21.1/dev`), Sable 2.0.5, Sable Companion, Create Aeronautics/Simulated 1.3.2, Sable Player
Ragdoll 0.7.5, Create Enchantment Industry 2.5.3, Create: Dragons Plus 1.11.7, Create Diesel
Generators 1.3.15 and vanilla 1.21.1 (decompiled) sources, or by running a build.

The design brief (what the mod *is*) is the source of truth; this document is only about how to build
it on what Create, Sable and the other addons actually provide.

---

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

**Still open**: nothing blocking. Slice 1 (cow) is in progress.

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
