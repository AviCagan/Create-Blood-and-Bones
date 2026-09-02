# Create: Blood & Bones — Technical Proposal

**Status: PROPOSED, awaiting sign-off.** Nothing below is locked in. Everything marked
*verified* was checked by reading the actual Create 6.0.11 (`mc1.21.1/dev`), Sable 2.0.5,
Sable Companion, Create Aeronautics/Simulated 1.3.2, Sable Player Ragdoll 0.7.5 and vanilla
1.21.1 (decompiled) sources, or by running a build. Everything marked *proposed* is a design
decision for you to accept or change. Open questions are collected at the end.

The design brief (what the mod *is*) is the source of truth; this document is only about
how to build it on what Create and Sable actually provide.

---

## 0. Summary of what was verified

| Area | Result |
|---|---|
| Toolchain | A NeoForge 21.1.249 project compiling against Create `6.0.11-300`, Sable `2.0.5`, Flywheel 1.0.6, Ponder 1.0.85, Registrate MC1.21-1.3.0+67 **builds** (ModDevGradle 2.0.146, Gradle 9.2.1, Java 21). Cold build 4m52s, incremental 8s. Create's published access transformer is consumable by ModDevGradle. |
| Sable physics | Server-only Rapier scene per dimension. Exposes a non-block rigid body (`BoxPhysicsObject`: one cuboid collider, mass, CCD) and joints between any two bodies or the world (`Fixed`, `Free`, `Rotary`, `Generic` with per-axis locks, **limits**, PD motors, re-anchorable frames, contact toggling, impulse readback). Impulse-at-point, velocity, teleport, wake-up all work on boxes. Boxes collide with terrain, sub-levels, other boxes, and with Create contraptions (bridged as kinematic colliders that carry bodies by friction). |
| Sable gaps | Boxes are **not networked, not persisted, have no sleep API, no collision events, no capsule/sphere, no parenting**. Only sub-levels get sync/interpolation. Create contraptions cannot be joint endpoints (Aeronautics "simulated" sub-levels can). No entity<->body binding exists. |
| Existing ragdolls | Sable Player Ragdoll builds each limb as a block sub-level (plot + shadow chunk + block entity per limb), derives mob rigs by reflecting over the model **on a client** and trusting the packet, has no joint limits, no sleep, and keeps the mob alive and hidden. Fine for its purpose; wrong shape for a dozen persistent carcasses. Published jar is All Rights Reserved; the repo HEAD is Apache-2.0. Treated as reference only. |
| Create kinetics | `KineticBlockEntity` API unchanged in shape. **Stress registration moved**: `BlockStressValues.IMPACTS/CAPACITIES` registries; Create's `CStress.setImpact/...` builder transforms throw for non-Create mod ids, as do `BuilderTransformers.backtank/encasedShaft/...`. Addon must register stress itself (config-backed provider). No Create block "winds up under rotation and fires on a redstone edge"; Guillotine is composed from Weighted Ejector (wind-up), Sequenced Gearshift (rising edge), Pressing cycle. |
| Chain conveyors | Entirely internal (no api package). The only rideable thing is `ChainConveyorPackage { chainPosition, ItemStack }`; consumers assume `PackageItem`. Rendered model on the chain **is the item model** keyed by item id in a public map. Mid-chain sinks must be `FrogportBlockEntity` subclasses (or poll). Chain strip is raw quads with a hard-coded vanilla chain texture (Gut Chain needs a small mixin or an additive render pass). |
| Recipes/fluids | Processing recipes are codec-driven; addon types register via `IRecipeTypeInfo` and can be sequenced-assembly steps. Create's mixer/press/spout only run Create's own recipe types. Fan processing types are a registry. **No callback for "a fan is blowing on this block"**: the Bleeding Rack must poll for an `IAirCurrentSource` whose `AirCurrent.bounds` intersects it. Fluids via Registrate `standardFluid`; a new fluid is finite by default. Quench = plain `create:filling` JSON. |
| Backtank | Hard-wired to an integer air component; refills only from a shaft; HUD is icon + mm:ss text, not a gauge texture. Blood Backtank = own item/block/BE/overlay classes following the same pattern, refillable via pipes because it exposes a fluid capability. |
| Vanilla models | Rig data (part names, pivots, cubes, parents) is fully obtainable from baked `ModelPart` trees via `LayerDefinitions.createRoots()` + `ModelPart.visit()` with **no reflection**. Vocabulary is consistent enough (`head/body/*_hind_leg/*_front_leg/tail`) to derive rigs automatically for most mobs, with a documented list of exceptions (wolf wrappers, horse `head_parts`, rabbit haunch/foot siblings, ocelot tail1/tail2 siblings, goat, sniffer six legs, strider). |
| Cold | Create has no temperature/cold system at all. |

---

## 1. Target and toolchain (verified, proposed to lock)

- Minecraft 1.21.1, NeoForge **21.1.249** (Create builds against 21.1.219; Sable requires ≥21.1.228), Java 21, mod id `bloodandbones`.
- Create `com.simibubi.create:create-1.21.1:6.0.11-300` from `maven.createmod.net` (Jenkins build of 6.0.11; there is no unsuffixed release coordinate). Ponder `1.0.85+mc1.21.1`, Flywheel `1.0.6` (api compileOnly, impl runtime), Registrate `MC1.21-1.3.0+67`.
- Sable `dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.5` from `maven.ryanhcode.dev/releases`. Veil, Sable Companion and the Rapier natives are nested inside the Sable jar; we must not bundle them.
- Build tool: **ModDevGradle 2.0.146** single module (Create and Aeronautics both use MDG). Create's access transformer is applied via MDG's `accessTransformers` configuration so `ModelPart.cubes/children` are usable at compile time exactly as they are at runtime.
- Dependencies declared in `neoforge.mods.toml`: create (required, BOTH), sable (required, BOTH), flywheel (required, CLIENT).
- Licences: Sable is PolyForm Shield (addons are fine; we are not competing with it). Sable Player Ragdoll and Sable Ragdoll Corpse are reference-only. Ragdoll mob corpses and Aeronautics *code* are MIT (reusable with attribution); Aeronautics *assets* are not.

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
   └────────────┘                       │ (entity +    │◄───────────────│ (PackageItem   │
                                        │  Sable boxes)│  drop/unhook   │  subclass)     │
                                        └──────┬───────┘                └───────┬────────┘
                                               │ tether / hooks / hanging       │ chains, belts,
                                               │ fans / contraptions            │ vaults, machines
                                        ┌──────▼───────┐                ┌───────▼────────┐
                                        │ physics layer│                │ Create machines│
                                        │ (server)     │                │ (kinetic BEs)  │
                                        └──────────────┘                └────────────────┘
```

Two representations, one state record:

- **Carcass state** (`CarcassData`): entity type, variant NBT snapshot, rig id, group, weight class,
  quality (intact/damaged), freshness, and the *set of parts remaining*. Serialised as one data
  component on the item and as NBT on the entity. The same record moves between the two forms.
- **Carcass entity** while it is a physical object in the world (ragdoll, being dragged, hanging).
- **Carcass item** (a `PackageItem` subclass) once it leaves a player's hands into Create logistics:
  chains, belts, depots, vaults, machines. Once an item, it behaves exactly like any other item, which
  is what the brief asks for.

The Shackle Hook is the transition point in both directions.

---

## 3. Carcass physics on Sable (the decision that is hardest to reverse)

### 3.1 What we get from Sable, verified

- One rigid body per limb: `BoxPhysicsObject(pose, halfExtents, mass)` added through
  `SubLevelPhysicsSystem.addObject`. Single cuboid collider (Minecraft limbs are boxes anyway), CCD on,
  fixed friction 0.45, mass from us.
- Joints: `GenericConstraintConfiguration` with `LINEAR_X/Y/Z` locked is a ball joint; `setLimit` on the
  angular axes gives hinges (knees, elbows, jaw) and cones (hips, shoulders, neck); `setMotor` gives
  muscle tone/damping; `setContactsEnabled(false)` stops adjacent limbs fighting; `getJointImpulses`
  gives a tear-off signal; `setFrame1/2` re-anchors a joint at runtime (this is the tether).
- `applyImpulseAtPoint` on a box: the killing blow. Point and impulse are body-local, so the hit
  position is transformed into the struck limb's frame first.
- Boxes collide with terrain, with Aeronautics sub-levels, and with ordinary Create contraptions
  (Sable makes every `AbstractContraptionEntity` a kinematic collider and injects its velocity into
  contacts, so a carcass lying on a moving gantry is carried by friction).
- Physics runs at 20 Hz × substeps (default 2) on the server thread; `SablePrePhysicsTickEvent` fires
  per substep for our per-tick joint updates and forces.

### 3.2 What we must build ourselves, verified

- **Networking**: Sable syncs only sub-levels. We send limb poses ourselves. Sable exposes
  `SubLevelTrackingPlugin` so our packets are stamped with Sable's interpolation tick and clients can
  interpolate on the same timeline (this is exactly how Aeronautics syncs its ropes).
- **Persistence**: boxes are destroyed on chunk unload with no hook. The carcass entity owns the
  authoritative limb poses and rebuilds the bodies on load/wake.
- **Sleep/cost**: there is no sleep API. "Costs nothing while lying in a field" is implemented as
  *freeze-to-static*: when every limb is below a velocity threshold for N ticks and nothing is acting on
  the carcass, we snapshot limb poses into the entity and **remove** the bodies and joints. Any
  interaction (hook, hit, drag, machine, contraption contact, fan) re-creates them at the stored poses
  and wakes them. A frozen carcass is a plain entity with a static pose: zero physics cost.
- **Entity glue**: nothing in Sable positions an entity from a body. The carcass entity follows its root
  (torso) box each tick; other limbs are rendered from the synced poses. The entity's bounding box is
  the union of limb boxes (for targeting, fans, pick-up) but collision with players is done by the
  physics bodies, not the entity.

### 3.3 Proposed design

**Server**

- `Carcass` entity (not a `LivingEntity`; the mob dies normally so loot/XP/advancements behave). On a
  Meat Hook kill: capture entity type, variant NBT (texture selection), death pose from the killing
  blow context, spawn the entity, build the rig from data, create boxes + joints, apply the kill impulse
  at the hit point plus inherited velocity.
- `CarcassPhysicsController` per carcass: owns handles, runs on `SablePrePhysicsTickEvent`, applies
  tether/hook constraints, samples velocities for freeze decisions and impact sounds, and tears joints
  when a machine removes a part (removing a limb removes its body and joint; the rest hangs
  differently because the mass is gone, which is the behaviour the brief wants for free).
- **Tether (Meat Hook drag)**: a world-anchored `Generic` joint to the grabbed limb with linear limits
  equal to the rope length and `setFrame1` updated every tick to the player's hand. Hook a hind leg and
  the joint pulls that limb; the rest follows through the joints, so the animal turns rear-first. No
  scripting of orientation anywhere.
- **Hanging**: same joint with linear axes locked at the hook point. Everything below stays dynamic
  (swings when knocked, hangs differently with a leg cut off). For a hook on a moving Create contraption
  the anchor frame is updated each tick from the contraption's transform; for a hook on an Aeronautics
  sub-level the joint is made directly between the sub-level body and the limb (Sable supports that).
- **Movement speed penalty**: while a player's tether is attached, apply an attribute modifier from the
  carcass's weight class (5%..55% per the brief). This is gameplay, not physics.
- **One-block step**: Rapier boxes against voxel terrain will catch on a full-height step if pulled
  horizontally. Plan: the tether applies a small upward bias when the grabbed limb is blocked at foot
  height (detected from joint impulse readback). This is the first thing the vertical slice must
  demonstrate on screen; if it does not feel right with limits alone, fall back to a short
  "hoist" impulse on the grabbed limb.
- **Fans**: `AirCurrent` overwrites the velocity of *every* entity in its bounds each tick. The carcass
  entity must ignore vanilla delta movement while simulated; instead we read the current's speed and
  apply it as a physics impulse (and use it for the Bleeding Rack drain bonus).
- **Freeze rules**: freeze after ~2 s of rest; never freeze while tethered, hanging on a hook in a
  machine, on a moving contraption, or in an air current.

**Client**

- One packet type: `CarcassPoseSnapshot(entityId, interpolationTick, [limb: pos, quat])`, sent only
  for limbs that moved past an epsilon, plus a `stopped` flag. A snapshot buffer per carcass keyed by
  Sable's tick pointer, lerp/slerp between snapshots, dead-reckon at most one tick (the Aeronautics rope
  interpolator, re-implemented).
- Renderer draws the mob's own baked `ModelPart` tree with each bone's parts posed by the limb
  transform and the mob's own texture (see §4). Removed parts are simply not drawn (and their texture
  region is swapped to a "cut" texture only if we ever ship art for it).

**Contraption riding**

- Ordinary Create contraption (gantry, piston, bearing, train): contact/friction carry, verified to be
  what Sable already does; when the carcass rests on one for N ticks we freeze it *parented* to the
  contraption block it sits on (poses stored contraption-local), so it survives being moved far away
  and wakes when the contraption stops or something touches it. Create's collider also carries free
  entities standing on a contraption, provided the entity has a normal push reaction (verified).
- A carcass hanging on a hook that is picked up by a contraption: Create has **no API to attach an
  entity to a contraption block** (only seats, which require a `SeatBlock`). So on pickup the hook's
  block entity serialises the carcass record plus its last pose into its own NBT and the entity is
  removed; the hook's renderer draws it from that data (block entity renderers do run inside
  contraptions, verified), and the entity is re-spawned when the contraption disassembles. This is the
  same "entity ↔ data" transition the Shackle Hook already does for chains.
- Aeronautics simulated contraption: it is a Sable sub-level; boxes collide with it natively, and a
  hook on it can joint to it directly. The carcass entity needs the `sable:retain_in_sub_level` tag so
  it is not kicked out of the plot when hung there.
- Chain conveyor: the carcass is an item there (§6), not a body.

### 3.4 Alternatives considered and rejected

1. **A block sub-level per limb** (the reference ragdoll's approach). Free networking and persistence,
   but each limb allocates a plot, a shadow chunk, a chunk ticket and block entities; collision is
   block-quantised; limbs cannot ride Create contraptions as items; a dozen 6-limb carcasses is ~72
   sub-levels. Rejected on the brief's cost requirement.
2. **Depend on Sable Player Ragdoll at runtime**. Rigs are authored by whichever client answers first
   (not server-authoritative, fails on servers with no player tracking the mob, trusts client geometry),
   no joint limits, no sleep, hidden-live-mob hack, ARR published jar. Rejected; we may borrow ideas
   (masked re-render of the mob model, hit-derived launch velocity) and, with attribution, small MIT
   pieces from Ragdoll mob corpses.
3. **Client-only cosmetic ragdoll (Verlet/PBD in Java, no Sable)**. Cheapest and simplest multiplayer,
   but then hooking, dragging, hanging and contraption interaction are all faked, which is the opposite
   of the brief. Kept only as a fallback for the `StaticPhysicsPipeline` case (Rapier natives failing
   to load), where carcasses would drop as a static posed entity.

### 3.5 Risks to retire first

- Sable itself never instantiates `BoxPhysicsObject`; we will be its first serious consumer. Joint
  anchors on boxes use an undocumented fallback (body-local because boxes have no centre-of-mass entry).
  Both are verified in source but must be proven in-game before anything else is built.
- Sable's API package is small and versions churn (the reference mod resolves constraint classes
  reflectively). We pin 2.0.5 and isolate all Sable calls behind one adapter class.
- Per-object chunk ticketing and wake-up scans are O(objects) per tick even when asleep, which is why
  freeze-to-static is mandatory, not optional.

---

## 4. Rigs derived from models (proposed)

**Principle**: joints come from the model; nothing lists joints by hand. A rig file is *generated*, and a
per-mob override file can adjust it.

### 4.1 Source of truth

Vanilla exposes every mob model as a `LayerDefinition` reachable through `LayerDefinitions.createRoots()`
(static, includes modded registrations via NeoForge's hook). Baking gives a `ModelPart` tree with, for
every part: name, parent, rest pivot (`PartPose`, public), and cube bounds (`Cube.minX..maxZ`, public).
`ModelPart.visit()` walks the tree with names and composed matrices, no reflection needed. Verified.

The server cannot load these classes, so:

- **Datagen step** (`runData`, which is a client-side run): bake every layer, walk it, and emit
  `data/bloodandbones/rig/<namespace>/<entity>.json` for every vanilla mob. Deterministic, reviewable,
  server-authoritative, and re-run whenever Minecraft or the mod changes.
- **Unknown modded mobs on day one**: two layers. (a) Group heuristics on the entity itself (leg-count
  from the rig if a client has reported it, otherwise dimensions and `MobCategory`) pick an archetype,
  and the archetype ships a *generic rig* (torso, head, four legs) scaled to the entity's hitbox, so any
  mob works immediately, looking approximate. (b) A client that renders the mob can extract its real
  rig with the same walker and send it; the server accepts it only as a *cache for that entity type*,
  validated against caps (part count, sizes within the hitbox), and only to upgrade from the generic rig.
  Pack authors can also run `/bloodandbones rig export <entity>` client-side to write the JSON into the
  config folder for a datapack.

### 4.2 Bone vocabulary and derivation rules

Canonical bones: `TORSO`, `TORSO2` (optional second segment), `NECK`, `HEAD`, `JAW`, `TAIL[n]`,
`{LEFT,RIGHT}_{FRONT,MID,HIND}_LEG`, `{LEFT,RIGHT}_{ARM}`, `{LEFT,RIGHT}_WING`, `DECOR` (never a body).

Resolver, first match wins on the full part path:
- `head`, `real_head`, `head_parts/head`, `body/head`, `neck/head` → `HEAD`; `neck`, horse `head_parts`
  → `NECK`; `body` → `TORSO`; `upper_body` → `TORSO2`; `*_hind_leg`, `*_front_leg`, `*_mid_leg`,
  `*_leg`, `leg0..7` → legs; `*_arm` → arms; `*wing*` → wings; `tail`, `real_tail`, `tail1/tail2`
  → tail chain; `saddle*`, `*_chest`, `mane`, `reins`, `bridle`, `*_ear`, `*_horn`, `nose`, `mouth`,
  `goatee` → `DECOR` (rendered with their parent bone, no body). Zero-thickness cubes are never colliders.
- Joint anchor = child part's rest pivot expressed in the parent bone's frame. For the twenty-odd
  models whose limbs are root-level siblings (the `QuadrupedModel` lineage), legs/head/tail are
  re-parented to `TORSO` by rule, and the anchor is computed after applying the torso's rest rotation
  (the π/2 X-rotation those models use).
- Collider = union of the bone's cubes (wrapper parts with no cubes collapse into their cube-bearing
  descendants), in bone-local space, scaled by the renderer's scale (horse 1.1, cat 0.8, polar bear 1.2,
  rabbit 0.6, babies via `getAgeScale`).
- Joint type and limits come from the **archetype** file, keyed by bone role (hip = cone 60°, knee =
  hinge 0..120°, neck = cone 45°, jaw = hinge 0..30°, …), not from the model.

### 4.3 Known exceptions (verified from the survey of all vanilla mobs; these get override files)

Three derivation rules cover most of the mess, and are applied before any override:

- **Virtual parent for flat trees.** Most vanilla models put every part directly under the root
  (all `QuadrupedModel` mobs, humanoids, fish, spider, chicken…). Legs/head/tail are re-parented to
  the torso by name; only bat, phantom, dolphin, warden, camel, sniffer, armadillo, bee express real
  parent links.
- **Joint at the child's pivot, with a fallback.** When a child's pivot coincides with the parent's
  (cod/salmon head vs body, guardian tail, wither heads, iron golem arms), the joint moves to the face of
  the child's cube nearest the parent's cube.
- **Rest pose from the model, not from `PartPose` alone.** Spider leg splay, parrot body tilt and wing
  fold, bee wings/legs and blaze rods are set in `setupAnim` every frame, not in the rest pose. The
  datagen exporter therefore runs `prepareMobModel`/`setupAnim` with zero limb swing on a baked model
  before reading pivots. Zero-thickness cubes (fins, wings, bristles, tack straps) get a minimum
  collider thickness and never become bodies of their own.

Per-mob override files are needed for (all verified): wolf (empty `head`/`tail` wrappers, `upper_body`
second torso, asymmetric leg cubes), horse family (`head_parts` is the neck, eight saddle parts,
duplicate baby legs, renderer scale), rabbit (haunch + foot siblings sharing a pivot, model-internal
0.6 scale), ocelot/cat (`tail2` sibling of `tail1`, cat renderer scale), goat (`nose` is the skull, body
unrotated, hind-leg cubes 4 px below the pivot), sniffer (six legs under `bone`), strider (two legs, no
head), armadillo (alternative rolled-up geometry), llama (non-uniform baby scaling), polar bear
(renderer scale 1.2, off-centre body pivot), ravager (real neck→head→jaw chain), villager/wandering
trader/witch (arms are one folded `arms` block), illagers (both `arms` and separate arms; use the
separate ones), iron golem (degenerate arm pivots), creeper (quadruped leg names on a biped torso,
cross-wired Java fields), enderman (whole model shifted −14, negative-grow hat), warden (nested
Blockbench tree), allay/vex (`root()` is a child group, no legs), spider/cave spider (three unlinked
body segments, coded leg splay, 0.7 renderer scale), bee (per-pair leg strips, non-hierarchical),
chicken/parrot (coded rest pose), bat (head sibling of body), phantom (size scale + translate),
axolotl/turtle/frog/tadpole (mixed naming and parenting; no `root()`), guardian (spikes animate
position, tail at origin), squid (custom renderer transform, radial tentacles), pufferfish (three
models by puff state), tropical fish (two bodies × pattern layer), wither (flat, 2× scale).

Mobs with no plausible body plan are excluded by default via a deny tag rather than forced: blaze,
breeze, ghast, slime, magma cube, shulker, snow golem, ender dragon (multipart), wither, warden
(boss-tier). Exclusion means they drop loot normally and never become carcasses; the tag is data.

Renderer-level scale is not in any model (horse 1.1, donkey 0.87, mule 0.92, cat 0.8, polar bear 1.2,
husk 1.0625, wither skeleton 1.2, giant 6, cave spider 0.7, ghast 4.5, elder guardian 2.35, player and
villagers 0.9375, plus the generic scale attribute), so the generated rig carries an explicit
`render_scale`, read from a small per-entity table seeded at datagen and overridable in data.

### 4.4 Texture

Carcass rendering keeps vanilla `Cube` instances with their baked UVs and uses the entity renderer's
`getTextureLocation` for the variant (cow vs mooshroom, fox vs snow fox, horse markings). Secondary
layers (sheep wool, pig saddle, llama decor, horse armor) are separate baked trees; "degloved" is
literally "stop drawing the fur/hide layer and swap the body texture to the flesh texture" (one
placeholder flesh texture per archetype early on).

---

## 5. Data model (proposed)

All datapack JSON, all overridable by a higher-priority pack:

- `mob_group/<id>.json`: archetype (`quadruped`, `biped`, `bird`, `arthropod`, `fish`, `amorphous`),
  member entity types or tags, weight class, default part list with per-part yield tables, blood volume,
  freshness half-life, joint limit table per bone role, generic rig.
- `weight_class/<id>.json`: mass per limb scale, drag penalty, hook size requirement.
- `rig/<ns>/<entity>.json`: generated (§4), plus optional `rig_override/<ns>/<entity>.json`.
- `carcass_yield/<...>.json`: what each part gives per station (machine full yield, hand ≈ half with
  loss), quality penalties.
- Physics block properties for our blocks (`physics_block_properties/*.json`, Sable's format) so our
  machines have sensible mass on simulated contraptions.
- Processing recipes as Create JSON (§7).

A specific mob is never *required* to have a file: group membership can come from entity-type tags,
and the generic rig covers the rest.

**Carcass state record**: `{type, variant NBT, rig, group, weight, quality, freshness, parts: bitset,
blood: mb}`. Small enough to ride as a data component (§6 needs it small: Create re-sends every package
on every chain sync).

**Freshness / rot**: a timestamp-based decay ticked while loaded (never while unloaded, per the brief's
spirit for prosthetics; carcasses are simpler and can decay by game time — question 12). Cold is
detected by a `bloodandbones:cold_sources` block tag (any addon can join it) and a light per-chunk check;
Create has no cold system to hook. At full rot the carcass "melts": parts vanish, the entity despawns,
leaving a stain.

---

## 6. Shackle Hook and chain conveyors (proposed)

Verified constraints: the chain carries only `ChainConveyorPackage(ItemStack)`, consumers assume a
`PackageItem`, the model shown on the chain is the item model looked up by item id, and mid-chain
removal without mixins requires a `FrogportBlockEntity` subclass.

- **Shackled carcass item** = `PackageItem` subclass with its own `PackageStyle` and rigging model
  (the shackle). Registered per archetype × weight class so the chain shows the right hanging model
  (the map is keyed by item id; a component-driven model would need our own renderer hook). Removed
  from `PackageStyles.STANDARD_BOXES/ALL_BOXES` after registration so packagers never emit it.
  Address component set to a reserved namespace (`bnb:carcass/*`) so ordinary frogports do not steal it.
- **Shackle Hook** (on-ramp): a `PackagePortBlockEntity` subclass targeting the chain with Create's own
  `ChainConveyorFrogportTarget`, so capacity/speed/reversal/routing come for free. Hanging a carcass
  entity on the hook converts it to the item and exports it.
- **Stations** (off-ramps: Deglover, Guillotine, Beheader, Bleeding Rack…): each has a `FrogportBlockEntity`
  subclass "hook" as its chain interface, with its Create address filter = the station's part filter.
  Non-matching carcasses pass through untouched, which is the brief's "pull one part out of a mixed
  line" behaviour using Create's own routing.
- Dropped/thrown carcass items become the carcass entity again (replacing Create's `PackageEntity`
  which would pop like a box).
- **Gut Chain**: no Create hook; a small `@ModifyArg` on `ChainConveyorRenderer.renderChains` selecting
  a texture per conveyor (state in a mod-side attachment), or an additive render pass sampling
  `forPointsAlongChains`. Both are client-only and low-risk.

---

## 7. Machines (proposed, each mapped to a verified Create pattern)

Common: every machine is a `KineticBlock` + `KineticBlockEntity` (or `SmartBlockEntity` for unpowered),
uses `FilteringBehaviour` for the part filter, our own `IRecipeTypeInfo` enum, and registers stress
through `BlockStressValues.IMPACTS.registerProvider(...)` backed by our own Catnip `ConfigBase`
(Create's builder transforms throw for addons). Processing time scales with speed the Millstone way
(`timer -= clamp(|speed|/k, 1, max)`), gated on `getSpeed()==0`.

| Machine | Pattern |
|---|---|
| Deglover | Kinetic, large base impact (stress cost is linear in RPM, so a high impact makes low RPM the sane operating point), takes a carcass or limb item from a belt (`BeltProcessingBehaviour`, 2 blocks above) or its own hook; recipe: part filter = hide. |
| Guillotine | `EjectorBlockEntity`-style state machine (WINDING→ARMED→DROPPING→RESET), wind rate ∝ speed, drop on **rising redstone edge** via the Sequenced Gearshift pattern (`neighborChanged` → scheduled tick → compare previous power). Cut = Pressing-style cycle midpoint. |
| Beheader | Inline continuous belt processor exactly like the Mechanical Press over a belt; single recipe type. |
| Mangler | Terminal grinder modelled on Crushing Wheels: accepts carcass/degloved/limb/degloved-limb items, outputs low-grade pile + vanilla loot table roll. |
| Bleeding Rack | Unpowered `SmartBlockEntity` with a `SmartFluidTankBehaviour` exposing `Capabilities.FluidHandler.BLOCK` (pipes/pumps just work). Partial collision shape (a full cube stops fan air). Polls for an `IAirCurrentSource` whose bounds intersect it and scales drain by fan speed. The hanging carcass stays a live ragdoll (hook joint). |
| Spit Roast | `GeneratingKineticBlockEntity` clone of the Hand Crank (player can turn it) that also accepts a shaft (Create's `applyNewSpeed` consumer branch). Cooking progress ∝ |speed|. Cooked results with effects scaled by mass. |
| Surgery Table | One block, attachment items swap a blockstate and the recipe set. Its recipes implement `IAssemblyRecipe` and the table calls `SequencedAssemblyRecipe.getRecipe(...)` first, so minion assembly and organ extraction can be sequenced-assembly chains. Player surgery UI later. |
| Specimen Jar, Steel Table/Rack, wall Meat Hook | Plain decorative blocks; the wall hook renders the carcass item/part with the same renderer as the entity. |

**Filters.** Create's plain filter compares item type only and ignores data components (verified), so
"pull the hide off a mixed line" cannot key on carcass state through a plain filter. Two measures:
one carcass item per archetype × weight class (which the chain renderer needs anyway, §6), and custom
item attributes registered in Create's attribute registry (`has part: hide`, `archetype: quadruped`,
`quality: intact`, `fresh`) so attribute filters and list filters with "respect NBT" work on every
machine and on Create's own funnels and frogports.

**Decoration.** Create 6 has no "cladding"; the visual wrap families are casings (connected-texture
blocks) and palettes. Blood-stained variants are new casing blocks built with Create's casing builder
and our own connected-texture sprites, plus a small blood-stained palette family generated the way
Create generates its stone palettes.

Three processing paths stay distinct by yield tables, not by code paths.

---

## 8. Blood, Soul Blood, materials, backtank (proposed)

- `blood` registered via our `CreateRegistrate.standardFluid` with a tinted fluid type; tagged
  `c:blood` (and Create's tags where useful). Not added to `create:bottomless/allow`, so it is finite
  under Create's default config (a server forcing `ALLOW_ALL` can make anything infinite; unavoidable).
- `soul_blood`: separate fluid and tag. Chain expressed entirely in Create's own JSON types because
  Create's mixer/press/fan only run their own types: `create:mixing` (heated) congeal → `create:haunting`
  (fan over soul fire) → `create:mixing` (superheated) re-melt. Trickle path: bleeding rack recipes for
  nether mobs output soul blood directly.
- Blood Steel: `create:filling` (iron ingot + 250 mB blood). Blood Diamond and Soul-Blood Netherite:
  `create:sequenced_assembly` with `create:filling` steps (blood/soul blood 1000 mB, liquid XP
  1000 mB) and a transitional item. Liquid XP: question 9.
- Blood Backtank: own item (armor, chest, fluid data component + `IFluidHandlerItem`), own placeable
  block that exposes a fluid capability (refill from pipes) and optionally still takes a shaft, own
  HUD layer copied from Create's `RemainingAirOverlay` (Create's is icon + text; if you want a gauge
  texture like Create's *goggle* gauges we draw one). Not tagged as a pressurized air source unless
  you want it to feed Create diving gear (question 10).

---

## 9. Bloodless mode (proposed)

A `ConfigBool` in a CLIENT `ConfigBase` (Create's pattern), read only from client code. Every
renderer, particle spawn and sound call goes through one `Presentation` facade
(`Presentation.gore()` / `Presentation.blood(fluid)` / `Presentation.lang(key)`), which picks the organic
or mechanical variant. Verified: all of Create's particle spawning is client-side already, so
particles and sounds need no server involvement. Names and descriptions have no Create hook: our items
override their display name client-side and our tooltip modifier re-reads the toggle (Create's own
modifier caches per language, so we ship our own). Lang keys are duplicated under a `bloodless.`
prefix. No logic path ever branches on it. Wired into the first vertical slice so the facade exists
before any content.

---

## 10. Contraption safety (proposed checklist, verified against Create 6)

- Every block: no full-cube assumptions in `getShape`; shape methods must work with Create's wrapper
  level and no block entity; `MovementBehaviour` registered via `.onRegister(movementBehaviour(...))`
  where the block does something while moving; block entity data round-trips through `write/read`
  with `clientPacket` handled (inside a contraption the client only sees the update tag, and there is
  no server-side block entity at all, so nothing processes while moving unless it is an actor).
- Blocks with an empty collision shape (wall Meat Hook, Gut Chain, Specimen Jar) must be tagged
  `create:movable_empty_collider` or contraptions leave them behind; wall/ceiling-mounted blocks get
  `create:brittle` plus an attached-check toward their support face; blocks with custom orientation
  properties implement `TransformableBlock` or they mis-rotate on bearings.
- Storage: any block exposing an item handler is silently mounted by Create's fallback storage and
  becomes contraption inventory; machines and hooks are tagged
  `create:fallback_mounted_storage_blacklist`, and tanks (Bleeding Rack, blood tank) register a
  `MountedFluidStorageType` because there is no fluid fallback.
- Carcass items on contraptions are items in mounted storage: nothing special.
- Hooks with a hanging carcass inside a contraption: carcass becomes hook data (see §3.3), rendered by
  the hook's renderer, re-spawned on disassembly. Hooks are never tagged `create:seats`.
- Contraption actors that cut or hook mobs (a mounted Guillotine or Meat Hook) subclass Create's
  `BlockBreakingMovementBehaviour` for its entity-damage path, which Sable already patches for
  sub-levels.
- Our own blocks on Aeronautics sub-levels: implement `BlockEntitySubLevelActor` only where a block
  needs per-physics-tick behaviour (hooks), ship `physics_block_properties` for mass, and route any
  world-position logic through `Sable.HELPER.projectOutOfSubLevel`.
- Creative tab: Create's tabs only list Create's own registrate entries, so the mod ships its own tab.

---

## 11. Vertical slices (proposed order)

1. **Cow, Meat Hook, ragdoll, drag, hang, freeze** (the physics spike): kill → box ragdoll from the
   generated cow rig → killing-blow reaction → drag by any limb → one-block step → hang on a static
   Shackle Hook → freeze/wake → survives relog and chunk reload → looks right with two clients.
   Presentation facade and bloodless toggle in from day one. Placeholder textures.
   *Exit criterion*: a video-worthy cow, and a written verdict on whether Sable boxes hold up.
2. **Shackle Hook → chain conveyor → Bleeding Rack** with blood as a fluid into a Create tank, fan bonus.
3. **Deglover + Guillotine + Beheader + Mangler** with filters, data-driven yields, the Flensing Knife.
4. **Groups and rigs for all vanilla mobs** (datagen export, overrides, generic fallback for modded mobs),
   weight classes, freshness/cold.
5. Materials (Blood Steel, Diamond, Netherite), Blood Backtank, Soul Blood chain.
6. Armor system → prosthetics safety floor → surgery/amputation → minions → cybernetics → decoration.

Each slice ends with in-game verification on screen and automated GameTests for the things that fail
quietly (rig generation for every vanilla mob, carcass state round-trips, recipe JSON validity,
freeze/wake persistence).

---

## 12. Open questions

Physics and carcass
1. Sable boxes + our own sync (server-authoritative) is the proposal. Confirm, or do you prefer the
   heavier sub-level-per-limb route for its free networking?
2. Body budget: one body per model bone (cow 6, horse 8–9, sniffer 8) or a fixed archetype budget
   (torso, head, four legs; wings/tail merged)? I propose "one per bone, decor merged", capped at 12.
3. "Simulated Sable physics contraption" in the brief: do you mean Create Aeronautics/Simulated
   contraptions specifically (then Aeronautics becomes an *optional* integration we test against), or
   just ordinary Create contraptions under Sable?
4. Should carcasses float in fluids? Sable buoyancy is sub-level-only; boxes sink unless we add a
   simple buoyancy force ourselves (cheap, but it is our code).
5. If Rapier natives fail to load (Sable falls back to a no-physics pipeline), is "carcass drops as a
   static posed entity, still butcherable" an acceptable degraded mode?

Rigs and mobs
6. Rig source: generated at datagen from vanilla models (server-authoritative) + client-assisted cache
   for unknown modded mobs + a generic archetype rig as day-one fallback. Agree?
7. Baby mobs: uniform scale by `getAgeScale` (slightly small heads) rather than replaying vanilla's
   two-group baby scaling per model? Cheaper and covers modded mobs.
8. Mobs to exclude by default (ender dragon multipart, wither, warden, slimes/magma cubes, ghast,
   phantom, bat, vex/allay/breeze, shulker, guardian): tag-based deny list, configurable. OK?

Content decisions that affect data formats
9. Liquid XP is not a Create fluid. Use Create Enchantment Industry's fluid if present (compat tag), and
   ship our own `liquid_experience` fluid otherwise? Or drop XP from the diamond/netherite recipes?
10. Blood Backtank: also a Create pressurized-air source, or strictly a prosthetic battery?
11. Should carcasses be visible to Create's normal logistics (addressable, so any frogport/packager can
    route them) or only to our stations (reserved address namespace)? I propose reserved namespace.
12. Rot: game-time based (a carcass in an unloaded chunk does not rot) is what I'd implement. Confirm.
13. "Compatible with other addons that do bulk freezing": which addons? I found nothing on Modrinth for
    NeoForge 1.21.1 that is a Create cold/freezer addon; Cold Sweat (a temperature mod) exists. I'd
    ship a `bloodandbones:cold_sources` block tag plus a Cold Sweat hook if you name that one.
14. Spit Roast: integrated crank (turn the block itself) as proposed, or a plain shaft consumer that
    works with Create's Hand Crank block?
15. Gut Chain: texture swap on the conveyor's chain (a mixin) or physical decorations riding the chain?
16. Bloodless mode: client-only toggle (each player chooses) as proposed, or additionally a server
    gamerule that forces it for everyone on a server?

Process
17. Mod licence: `gradle.properties` carries a placeholder (All Rights Reserved) until you pick one.
    The verified build skeleton (`build.gradle`, mod class, mods.toml) is committed alongside this
    document so slice 1 starts from something that compiles.
