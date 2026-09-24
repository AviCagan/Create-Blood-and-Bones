<!-- The design for the parts-and-traits system: every piece of a mob as an ingredient for a minion or for
     carcass armour. Written by a design panel (three proposals, two judges, one synthesis) from the user's
     request and docs/DESIGN-BRIEF.md, then checked against the brief. Build status is kept in
     docs/ARCHITECTURE-PROPOSAL.md section 15. Where this and the brief differ, the brief wins. -->

> **Status.** Being built in the slices of section 9; slices 1 (cow and rabbit armour) and 2 (a cow torso on
> rabbit legs) are done. The open questions in section 11 were answered with
> this document's defaults so the work could go on; each is listed in ARCHITECTURE-PROPOSAL.md section 15,
> for the user to overturn.

# Create: Blood & Bones: parts, traits, minions and carcass armour
## The final merged design spec

The user's instruction: *"Do you remember what I said about each part of a mob (leg arm head torso and special organ) being a craft ingredient for either a minion or the armor sets? So that should carry over to a massively diverse system of unique characteristics and stuff."*

This spec turns that into one system. Every carcass piece is an ingredient with two possible futures:
- **Kept whole**, it becomes a minion part.
- **Ground in the Mangler**, it becomes armour scraps that remember which mob and which part they came from.

In both futures, what the ingredient does comes from data. Diversity comes from three things multiplied together:
- a small set of layered groups, so any mob works on day one;
- a signature on every one of the 79 vanilla mobs;
- a finite set of about 30 effect types, combined by named, levelled trait files.

---

## 0. How this spec was merged

The two judges picked different winners.
- One picked **data**, for groups, data-drivenness and the physical build.
- The other picked **engine**, for the effect engine and buildability.

This spec is built from both:
- **Structure from data:** the archetype → family → overlay → per-mob layering; tag-keyed overlays; items that store only their sources; variants read from carcass traits; heavy parts built physically on the table; every mob having a special organ; minion jobs tied back to butchery.
- **Engine from engine:** named, levelled trait files; vanilla `LevelBasedValue` and loot-condition requirements; a small set of effect types over NeoForge hooks; trait-id dedup; a pure `MinionStats`; two tests per effect; the cow-and-rabbit vertical slice first; the contraption-movable trough found through a per-level registry; Create Filter and Deployer repair for cybernetic minions.
- **Grafted from diversity:**
  - a signature for all 79 mobs, with a content test that lists what is missing;
  - the backtank strap-on;
  - scraps from the Mangler only;
  - stand-in scraps from self-contained mobs, so every mob can make a full set;
  - our own actions registered into vanilla's enchantment-effect registry;
  - the Dormant Minion item;
  - power drain by activity;
  - the feet-on-ground and hitbox tests;
  - `bloodless_name` on every organ;
  - the full bloodless wording table.

### Conflicts with the brief that the judges found, and how each is fixed

| # | Conflict (source) | Fix in this spec |
|---|---|---|
| 1 | Heavy torsos assumed to be items (diversity, engine). `CarcassButchery.LIGHT_MASS = 0.13` and `pickUp` is the only path to a `CarcassPieceItem`, so a cow, horse or ravager torso can never be an item. | A carcass or severed piece lying in the Surgery Table's work zone is claimed physically. Bones still attached count as parts already fitted. This works for any part, and for taking organs out of heavy carcasses with the Surgical Rig. (§6.2) |
| 2 | Ground-contact limb roles, where arms walk and legs fight (engine). | A piece's role is decided by what it is, never by where it hangs: arms attack, legs move. (§2.1, §6.4) |
| 3 | "Forelimbs are arms", so a quadruped's front legs become arm ingredients (diversity). One judge said this bends the user's split. | A leg is a leg. Front, mid and hind legs are sub-slots of LEG, so data can still give front legs their own flavour. This is listed as an open question in case the user prefers the other reading. |
| 4 | Ghast torso scaled down to fit (engine). | Parts are always drawn at native scale. A torso larger than `minion_max_torso_blocks` (3.0) is refused as a frame, not shrunk. Its tentacles, organs and scraps still work elsewhere. |
| 5 | Only about 35 signatures (engine). | All 79 vanilla mobs get a signature file (§8.2). A content test lists every vanilla mob still missing one. |
| 6 | A test that requires a file for every vanilla mob strains rule 2 (diversity). | The test is a content lint over our own shipped namespace only. At runtime no mob ever needs a file: delete every mob file and every mob still works from its groups. |
| 7 | Per-species material overrides (diversity). | Materials come from families. Tag overlays patch them: `skeletal` turns any skeleton into bone, `rotting` lowers durability. The skeleton horse and zombie horse need no material line of their own. |
| 8 | Cybernetic minions stacked +25% HP, +2 armour, immunities and a module (diversity). | Neither kind gets a flat stat bonus. Each gets a few exclusive abilities and one real weakness. (§6.6) |
| 9 | "Surgery Table attachments are not built" (diversity risk 9). | Wrong. `TableAttachment` NONE, SURGICAL and ASSEMBLY exist (§14.8). This spec builds on ASSEMBLY for minions and SURGICAL for organs. |
| 10 | Trough found as a PoiType; no contraption behaviour for the trough or cradle (diversity). | The trough and cradle register in a per-level set on onLoad and setRemoved. Both move on contraptions with their contents and do nothing while assembled. (rule 5) |
| 11 | "The Deployer needs a mixin" (engine). | `DeployerRecipeSearchEvent` exists in Create 6.0.11 (class checked in the jar). Deployer fitting is a later slice, after checking how the Deployer caches recipes. Crafting grids and Mechanical Crafters come first. |
| 12 | Cleaver gives half scraps (data). The brief names the Mangler as the scrap source. | Mangler only. The Cleaver path is an open question. |
| 13 | Waking needed both a head and a heart (data). | Waking needs only a torso plus power. With no head the minion is Mindless (companion only). The organ slot is optional. |
| 14 | Purity bonus checked only the scraps (data). | Strict: every scrap, shoulder, hip, hide and organ present must come from the same mob. Missing hides and organs are fine. |
| 15 | "Each butchery table gains a scrap yield kind" (data). | Scrap counts come only from rig volume × the group material's density, never from the per-mob butchery tables. |
| 16 | Deployer modification route had no mechanism (data). | Same answer as #11. |
| 17 | The graft chestplate takes the chest slot where the Blood Backtank is worn (data, engine). `FluidBacktankItem.wornBy` reads only `EquipmentSlot.CHEST`. | Strap-on: the carcass chestplate can carry the tank's tier and fluid components, and `wornBy` returns it. (§7.8) Listed for confirmation. |
| 18 | Class-based family matching only resolved after a mob type's first death (data). | Classes are resolved at the first resolution after server start, on a throwaway instance (`EntityType#create`, never added to the world, errors caught). They are cached per session, so tooltips and JEI are right from the first moment. |
| 19 | `IItemExtension#isGazeDisguise` (data) does not exist. | Uses `isEnderMask` (checked in the NeoForge 21.1.249 jar). |
| 20 | "Critter" and "hollow" lumped birds, fish and golems together (engine). | Body archetypes follow rule 2's words: biped, quadruped, bird, flier, arthropod, fish, tentacled, floater, blob, shelled, colossus. |
| 21 | Collapse-on-combat-death made the default without asking (diversity). | It is the default, marked for the user to confirm. (Open questions) |
| 22 | Grafted parts at 0.75 efficiency (data). | Removed. Jank is the appeal, and no part is weaker for being on the wrong body. |
| 23 | Slice 1 was infrastructure with no gameplay (all three). | Slice 1 is cow and rabbit armour end to end. Slice 2 is a cow torso on rabbit legs. (§9) |
| 24 | A datapack retune silently changes armour already crafted (data). | Kept, because rule 3 says retuning is data. Flagged for the user; durability is the only value baked into the item. |

### Naming decisions
- **The armour is called "carcass armour"** (items `carcass_helmet` and so on), never "graft armour". The brief already uses "flesh grafts" for organic prosthetics, and `cyber/SetBonus` counts those for the four-graft bonus. The two set bonuses are separate systems and can both apply.
- **"Bloodless mob"** means a mob in the existing `#bloodandbones:bloodless` tag (20 mobs with no blood: skeletons, golems, blaze...).
- **"Bloodless mode"** means the presentation toggle. They are unrelated. No logic ever branches on bloodless mode.

---

## 1. The system on one page

**Five ingredient kinds, plus a minor one:** HEAD, TORSO, ARM, LEG, ORGAN, and TAIL.
- Hides modify armour.
- Tiers upgrade armour.

**Two futures for each piece:**
- **Minion** (Surgery Table with the Assembly Frame):
  - the torso sets size, health, carrying and sockets;
  - the head sets the job;
  - the arms set the attack;
  - the legs set movement;
  - the tail adds a small passive;
  - one optional organ adds one special.
- **Armour:** the Mangler turns the piece into `scraps {entity, part}`.
  - Head scraps make helmets.
  - Torso scraps make the chestplate body, and arm scraps its shoulders.
  - Leg scraps make leggings and boots, and tail scraps the leggings' hips.
  - The scraps' family material sets base stats; each piece takes the trait of the part its scraps came from.
  - A hide and one organ modify each piece. Tiers add armour points.
  - A full set from one mob adds a bonus and a drawback.

**Where the traits come from, in layers:**
1. Archetype (body shape).
2. Family (flavour).
3. Overlays keyed off vanilla tags that mod authors already fill in (Smite and Bane tags, aquatic, freeze-immune...).
4. An optional per-mob file with the signature.

Items store only their sources; numbers are resolved from data.

**Effects:** about 30 effect types, 8 triggers, vanilla loot conditions plus 4 of our own, vanilla `LevelBasedValue`, and 7 actions registered into vanilla's own enchantment-effect registry. Named trait files compose these into a starter library of about 120 traits. Datapacks compose; code adds effect types only.

---

## 2. Part slots

### 2.1 Slot vocabulary

| Slot | What it is | Minion | Armour scrap |
|---|---|---|---|
| TORSO | The rig root, or a self-contained single piece | Frame: size, health, carrying, sockets | Chestplate body |
| TORSO_EXT | A non-root body bone: `upper_body`, `lower_body`, `body0/1`, `ribcage` | Fits a torso socket and brings its own sockets | Torso scraps |
| NECK | `neck`, and the head-side segments of segmented mobs | Fits a head socket and carries one head socket of its own | Head scraps |
| HEAD | Any bone with a `head` token; a head inside a head merges into the outer one | Job, disposition, senses, bite | Helmet |
| ARM | `*arm*`. Forms: `wing` (`*wing*`), `pair` (a villager's single `arms` bone), `shell` (shulker lid, by override) | Attack style, grip, wing lift | Chestplate shoulders |
| LEG | `*leg*`, `*haunch*`, `tentacle*`. Sub-slots `front`, `mid`, `hind`; forms `tentacle`, `flipper` | Movement | Leggings and boots |
| TAIL | `tail*`, `real_tail`, `tail_base`, `body_back`, fluke | One passive | Leggings hips (optional) |
| ORGAN | Not a bone. Taken at the Surgery Table (Surgical Rig) from the piece its list names | The one organ slot | One organ per armour piece |
| EXTRA | Decor: saddle, chest, mane, horns, rods, fins | Drawn with its parent bone | Ground with its parent |

The piece decides its role, not the socket.
- Zombie arms in a cow's front-leg sockets still grab.
- A spider leg in a zombie's shoulder socket still counts toward climbing.

The bone already stored on every piece (`CarcassPieceItem.Piece.bone`) is enough to resolve its slot.

### 2.2 The resolver

The resolver lives in `data/bloodandbones/bone_slot_rules.json`, so the classifier itself is data. It replaces the ad-hoc `BBItemAttributes.PiecePart.kindOf` and `CarcassMachineBlockEntity.isHead`. Those two become thin callers of `PartSlot.of(rig, bone)`, cached per rig.

First match wins:
1. **The mob file's `bone_slots` map** (exact bone path). Example: shulker `{"lid": "arm"}`, form shell.
2. **The rig root is TORSO.** If the rig has no HEAD bone anywhere, the root is also usable as a head. That makes it **self-contained**: blaze, breeze, guardian, elder guardian, squid, glow squid, pufferfish, tadpole, bee, slime, magma cube, strider, ghast. No file is needed.
3. **Group `bone_slots` globs** from the mob's layers, most specific first. Example: arthropod `"body0": "torso_ext"`.
4. **Global rules on the last path segment**, matched on whole underscore-separated words:
   - `upper_body|lower_body|body\d|ribcage|chest|thorax|abdomen` (not the root) → TORSO_EXT
   - `neck` → NECK
   - `head` → HEAD
   - `wing` → ARM/wing
   - `arms` → ARM/pair
   - `arm` → ARM
   - `leg|haunch` → LEG
   - `tentacle\d*` → LEG/tentacle
   - `tail|fluke` or exactly `body_back` → TAIL
   - `segment\d+` → by chain position: the frontmost segment on the head side of the root is HEAD, the other head-side segments NECK, and those behind the root TAIL
   - Sub-slot, checked in order: `middle|mid` → mid, `front` → front, `hind|haunch|back` → hind. So `left_middle_front_leg` is mid.
5. **Chain rule.** A bone whose parent has the same slot belongs to that piece, not to a socket of its own: guardian `tail1` and `tail2`, silverfish tail segments. Nested heads merge.
6. **Geometry fallback** for unknown modded names, from the rest pose:
   - a torso child whose box reaches within 2 px of the feet is LEG (front or hind by z);
   - the largest child pivoting on the torso's front or top face is HEAD;
   - a child hanging off a side, clear of the ground, is ARM;
   - a thin child behind is TAIL;
   - anything else is EXTRA.
7. **Side** comes from `left_` or `right_`, otherwise from the sign of x.

### 2.3 Self-contained pieces
A self-contained piece (rule 2 above):
- can be a minion frame, and supplies its own head traits unless another head is fitted;
- can also go into another torso's head socket (a blaze as an iron golem's head);
- in the Mangler, gives torso scraps that **stand in** for every class the mob's rig lacks (§7.2). So a blaze, slime, bee, cod or squid can make a full, pure set.

### 2.4 Sockets (minions only)
- **Where sockets come from.** A frame's sockets are its own rig's HEAD, NECK, ARM, LEG, TAIL and TORSO_EXT children, plus the children of any fitted TORSO_EXT, at their rest pivots.
- **Missing extensions.** A spider abdomen whose thorax was cut off has no leg sockets until a thorax is fitted.
- **Clamping.** Each socket pivot is clamped onto the torso box's surface, inflated by 1 px, so a fitted part always touches the body.
- **Blob layout.** A frame with no head socket, or with fewer than 2 limb sockets, gets the `blob_sockets` layout from `bone_slot_rules.json`: 1 head socket top-front, 4 limb sockets on the lower corners, 1 tail socket at the back, all clamped onto its box. This covers blaze, slime, cod, bee, axolotl, parrot, squid (head only), strider (head only) and snow golem (lower limbs).
- **What each socket takes:**
  - head sockets take HEAD, NECK or self-contained pieces;
  - limb sockets take any ARM or LEG piece;
  - an ARM/pair piece fills two limb sockets, or counts as one arm in a single socket;
  - tail sockets take TAIL pieces;
  - torso sockets take any TORSO_EXT piece.
- **Size limit.** A fit that would make the hitbox exceed `minion_max_width` (3.0) or `minion_max_height` (4.0) is refused with a message.

### 2.5 Where each scrap goes
- HEAD and NECK → helmet.
- TORSO and TORSO_EXT → chestplate body.
- ARM → chestplate shoulders.
- LEG → leggings and boots.
- TAIL → leggings hips.
- EXTRA → its parent's scraps.
- **Stand-in rule:** torso scraps fill any position whose part class the source mob's rig does not have at all.

### 2.6 Every body shape in mobs.txt

| Shape | Mobs | Resolution |
|---|---|---|
| Biped with hands (18) | zombie, husk, drowned, zombie_villager, skeleton, stray, bogged, wither_skeleton, piglin, piglin_brute, zombified_piglin, pillager, vindicator, evoker, illusioner, enderman, iron_golem, warden (on `bone/body/...` paths) | body TORSO, head HEAD, right/left_arm ARM, right/left_leg LEG. Sockets: 1 head, 4 limbs (2 high, 2 low). |
| Folded-arm biped (3) | villager, wandering_trader, witch | `arms` is ARM/pair (fills two limb sockets, grip "hand for two"). Legs LEG. |
| Frog (1) | frog | root/body TORSO, root/body/head HEAD, root/body/*_arm ARM (the name beats the position), root/*_leg LEG hind. |
| Legs-only biped (1) | strider | body TORSO, self-contained (no head bone). 2 LEG. Blob head socket. |
| Legless biped (1) | snow_golem | lower_body TORSO (root), upper_body TORSO_EXT carrying head HEAD and both arms ARM. No legs: the torso rolls. |
| Quadruped (18) | cow, mooshroom, pig, sheep, goat, llama, trader_llama, hoglin, zoglin, polar_bear, panda, creeper, turtle, armadillo, camel, cat, ocelot, fox | body TORSO; head HEAD (armadillo `body/head/head_cube` merges; camel `body/head`); four LEG front/hind; cat and ocelot `tail1`, fox `body/tail` are TAIL. Turtle legs are LEG/flipper through the shellback family. |
| Equine (5) | horse, donkey, mule, skeleton_horse, zombie_horse | `head_parts` HEAD (neck and mane merged), 4 LEG, `body/tail` TAIL. |
| Wolf (1) | wolf | body TORSO (the rear, root). upper_body TORSO_EXT carrying `head/real_head` HEAD and both front legs. Hind legs LEG. `tail/real_tail` TAIL. |
| Rabbit (1) | rabbit | front legs LEG front; left/right_haunch LEG hind. |
| Ravager (1) | ravager | body TORSO, `neck` NECK carrying `neck/head` HEAD, 4 LEG. |
| Sniffer (1) | sniffer | root/bone/body TORSO, body/head HEAD, 6 LEG (front, mid, hind). |
| Axolotl (1) | axolotl | body TORSO, body/head HEAD. Legs and tail are drawn on the body, so the torso moves amphibiously by itself. The blob layout adds 4 limb sockets. |
| Arachnid (2) | spider, cave_spider | body1 (abdomen, root) TORSO; body0 (thorax) TORSO_EXT carrying the head and 8 legs (front, middle_front and middle_hind as mid, hind). |
| Segmented (2) | silverfish, endermite | silverfish: segment2 root TORSO, segment1 NECK, segment0 HEAD, segments 3–6 one TAIL chain. endermite: segment1 root, segment0 HEAD, segments 2–3 TAIL. No legs: the torso slithers. |
| Insect (1) | bee | single `bone/body` TORSO, self-contained, flies by itself (legs and wings are drawn with it). |
| Bird (2) | chicken, parrot | chicken: 2 LEG, 2 ARM/wing. parrot: 2 ARM/wing; its legs are drawn with the body, so the blob layout gives leg sockets and it hops by itself. |
| Flier (2) | bat, phantom | bat: body and head, wings drawn on the body, flies by itself. phantom: `body/*_wing_base` ARM/wing, `body/tail_base` TAIL, `body/head` HEAD, no legs, flies. |
| Fish (7) | cod, salmon, dolphin, pufferfish, tadpole, guardian, elder_guardian | cod: body, head. salmon: body_front TORSO, head, body_back TAIL. dolphin: body, body/head, body/tail TAIL. pufferfish and tadpole: self-contained. Guardians: root `head` is the eye-body, TORSO and self-contained; `head/tail0` TAIL with tail1 and tail2 chained into it. All swim by themselves and flop at 0.05 on land. |
| Tentacled (3) | squid, glow_squid, ghast | body TORSO, self-contained; `tentacleN` LEG/tentacle. The ghast body (4.5 blocks) is over the frame limit: it gives scraps and organs, and its tentacles fit other minions. |
| Floater (4) | allay, vex, blaze, breeze | allay and vex: root/body TORSO and root/head HEAD, fly by themselves. blaze: its single bone `head` is the root, so TORSO and self-contained, hovering. breeze: single `body/head` root, self-contained, hop-floats. |
| Blob (2) | slime, magma_cube | `cube` / `inside_cube` TORSO, self-contained, hop by themselves. Size from the carcass (size 1 is the baby rig). |
| Shelled (1) | shulker | base TORSO, head HEAD, `lid` ARM/shell by the shulker's own file (the default rule would make it TORSO_EXT). |
| Colossus (1) | wither | shoulders TORSO; ribcage TORSO_EXT; tail TAIL (a child of ribcage); center_head, left_head and right_head are 3 head sockets, center primary. Floats. 2.5 blocks wide, inside the limit. |

That is 24 + 28 + 5 + 2 + 2 + 7 + 3 + 4 + 2 + 1 + 1 = 79. Only the shulker needs a slot line in a file, and the turtle's flippers and the ghast's tentacles get their flavour from family and mob data.

---

## 3. Groups

### 3.1 Layers and resolution
A mob resolves to:
- exactly one **archetype** (body shape: complete defaults for every slot, organ set, generic rig, the torso's own movement, fallback material);
- zero or one **family** (flavour: material, jobs, strikes, gaits, hide, organs, set);
- zero or more **overlays** (condition patches keyed off tags);
- an optional **mob file** (the signature).

All four are the same file format (`mob_group/<id>.json` with `"kind"`, plus `mob_traits/<ns>/<mob>.json`).

Membership order:
1. the mob file's `archetype`, `family` and `overlays` fields;
2. explicit `members` ids;
3. member tags (highest `priority` wins);
4. `match` predicates:
   - archetypes may use rig counts, hitbox aspect, MobCategory, fireImmune and weight ("fuzzy only picks shape");
   - families may use only a tag or a Java class, checked on a throwaway instance;
5. fallbacks: archetype `quadruped` if the rig has 4 legs, otherwise `biped`; no family.

The resolved view is built once per (entity type, baby) by applying, in order: archetype, then family, then overlays by ascending priority, then variants from the carcass traits, then the mob file. It is cached, and cleared on `/reload` and `TagsUpdatedEvent`. `/bloodandbones traits explain <entity>` prints the chain.

### 3.2 Archetypes (11) with their members

| Archetype | Members | Torso's own movement | Default organs |
|---|---|---|---|
| biped (24) | zombie, husk, drowned, skeleton, stray, bogged, wither_skeleton, piglin, piglin_brute, zombified_piglin, zombie_villager, pillager, vindicator, evoker, illusioner, villager, wandering_trader, witch, enderman, iron_golem, warden, snow_golem, frog, strider | crawl 0.05 | torso: heart, lungs, stomach; head: eye ×2 |
| quadruped (28) | cow, mooshroom, pig, sheep, goat, llama, trader_llama, camel, horse, donkey, mule, skeleton_horse, zombie_horse, hoglin, zoglin, polar_bear, panda, wolf, fox, cat, ocelot, rabbit, armadillo, turtle, creeper, ravager, sniffer, axolotl | crawl 0.05 | same |
| bird (2) | chicken, parrot | hop 0.15 | same |
| flier (2) | bat, phantom | fly 0.2 | same |
| arthropod (5) | spider, cave_spider, bee, silverfish, endermite | slither 0.2 | torso: heart, stomach; head: eye ×2 |
| fish (7) | cod, salmon, pufferfish, tadpole, dolphin, guardian, elder_guardian | swim 0.3 (land 0.05) | torso: heart, stomach; head: eye ×2 |
| tentacled (3) | squid, glow_squid, ghast | swim 0.3 (ghast float 0.15) | torso: heart, stomach |
| floater (4) | allay, vex, blaze, breeze | fly or hover 0.2 | (bloodless overlay replaces these with a core) |
| blob (2) | slime, magma_cube | hop 0.2 | core only |
| shelled (1) | shulker | blink_step | core only |
| colossus (1) | wither | float 0.15 | core only |

Every archetype file carries a complete default table for every slot it has, so a mob with only an archetype is fully usable. The archetype's fallback material is `gristle`.

### 3.3 Families (27, plus two mobs with none)
Each entry lists: members; scrap material; minion defaults (torso, head, arm, leg); armour defaults (helmet, chest, shoulders, leggings, boots); hide; organs; full set. Trait names refer to the starter library in §5.10.

1. **grazer** [cow, mooshroom, sheep, goat, llama, trader_llama, camel]. Material hide_plate.
   - Minion: torso health ×1.5 with beast_of_burden. Head: jobs herder, courier, companion; docile; bite 1. Legs: walk 0.2, amble, sure_footed 1.
   - Armour: helmet cud_chewer; chest barrel_chest; leggings sturdy; boots hooves.
   - Hide: thick_hide 1. Organs: heart big_heart; stomach four_chambers.
   - Set: Herd Beast (hauler 3, hardy 2) / Placid (meek 2).
2. **equine** [horse, donkey, mule, skeleton_horse, zombie_horse]. Material hide_plate.
   - Minion: torso health fixed at 22 (the vanilla supplier value is randomised later), takes a saddle, hauler 2. Head: courier, hauler, companion; loyal. Legs: walk 0.3, gallop, rideable, sure_footed 2. Tail: fly_swat.
   - Armour: helmet bolt; chest draft_chest; leggings canter; boots galloper 1; hips swift 1.
   - Hide: groomed_coat (stacks). Heart: stallion_heart.
   - Set: Centaur (galloper 3, sure_footed 2, your mount gets Jump Boost II) / Hay Burner.
3. **swine** [pig, hoglin, zoglin]. Material hide_plate.
   - Minion: torso ×1.2. Head: hunter, courier; bite 1.5. Legs: walk 0.25, trot; charge (leaps at targets within 4).
   - Armour: helmet stubborn; chest lard; leggings swift 1 while sprinting; boots trotters.
   - Hide: bristled_hide (barbed 1). Stomach: omnivore.
   - Set: Hog Wild (brawler 2 while sprinting) / Glutton (Hunger I below half food).
4. **canid** [wolf, fox]. Material sinew.
   - Minion: head hunter, guard, herder; brave; bite 1.5 with bleed. Legs: walk 0.3, lope, swift 1.
   - Armour: helmet blood_scent; chest brawler 1; leggings swift 1; boots silent_steps.
   - Hide: pelt (frost_guard 1). Special organ: Pack Gland (pack_hunter).
   - Set: Alpha (brawler 2; tamed wolves near you get Strength I) / Howl (sheep, rabbits and chickens flee you; skeletons hunt you).
5. **feline** [cat, ocelot]. Material sinew.
   - Minion: head hunter, companion; nocturnal; bite (claw style). Legs: walk 0.3, prowl, silent_steps, fall_guard 2.
   - Armour: helmet cat_ward; chest evasive 1; leggings stealthy 1; boots land_on_feet.
   - Hide: soft_pelt (stealthy 1). Special organ: Nine Lives (undying, 20-minute cooldown).
   - Set: Nine Lives (undying cooldown halved; fall immunity) / Aquaphobe (Slowness II and Weakness in water).
6. **bear** [polar_bear, panda]. Material bear_hide.
   - Minion: torso ×0.8. Head: guard, bodyguard; territorial; bite 2 (swipe style). Legs: walk 0.25, lumber, waterborn 1.
   - Armour: helmet territorial; chest thick_hide 2; leggings steady 1; boots waterborn 1.
   - Hide: thick_fur (frost_guard 2). Special organ: Brown Fat (hardy 1 and frost_guard 2; minion power capacity +50%).
   - Set: Hibernator (regen 1 HP/2 s while sneaking still) / Drowsy (Mining Fatigue I by day).
7. **small_prey** [rabbit]. Material sinew.
   - Minion: head courier, companion; skittish. Legs: hop 0.3.
   - Armour: helmet alert; chest light_boned 1; leggings springy 1; boots swift 1.
   - Hide: swift 1 (stacks). Special organ: Racing Heart (swift 2 while sprinting).
8. **shellback** [turtle, armadillo, shulker]. Material shell.
   - Minion: torso ×1.3, thick_hide 2. Head: guard, sentry; bite 1. Legs: walk 0.15, plod, steady 2.
   - Armour: helmet shell_guard 1; chest turtle_up; leggings steady 1; boots steady 1.
   - Hide: shell_guard 1. Special organ: Shell Gland (organic minion sheds its mob's scute every 20 minutes; armour thick_hide 2).
   - Set: Fortress (shell_guard 3, steady 3) / heavy 3.
9. **behemoth** [ravager, sniffer]. Material bear_hide.
   - Minion: torso ×0.5, saddle, 2 seats. Head: guard, hauler; bite 3 (ram style). Legs: walk 0.25, stomp, trample, steady 2.
   - Armour: helmet steady 1; chest thick_hide 2 and hauler 2; leggings heavy 1; boots crusher.
   - Hide: hauler 1.
   - Set: Juggernaut (knockback resistance +0.5, brawler 2) / heavy 3.
10. **amphibian** [frog, axolotl, tadpole]. Material scale.
    - Minion: head companion, hunter; bite 1. Legs: amphibious (walk 0.25, swim 0.35), paddle, springy 1.
    - Armour: helmet oxygen +2; chest regen when wet; leggings waterborn 1; boots springy 1.
    - Special organ: Regrowth Gland (regen when wet).
    - Set: Amphibious (gills; regen in rain) / dry_out (120 s).
11. **humanoid** [zombie, husk, drowned, skeleton, stray, bogged, wither_skeleton, enderman]. Material skin; the skeletal overlay makes it bone.
    - Minion: head guard, companion, courier; bite 1. Arms: punch, grip hand (uses held tools and weapons). Legs: walk 0.23, stride.
    - Armour: helmet clear_eyed; chest hardy 1; shoulders quick_hands 1; leggings swift 1; boots sure_footed 1.
    - No hide (no member has one). Sets come from overlays or mob files.
12. **villager** [villager, wandering_trader, witch, zombie_villager]. Material skin.
    - Minion: head surgeon, farmer, courier (profession variants reorder and add); meek. Arms: pair, pacifist, grip hand for two, +9 storage. Legs: walk 0.25.
    - Armour: helmet appraiser; chest hardy 1; shoulders quick_hands 1; leggings sure_footed 1; boots stealthy 1.
    - Hide: trustworthy. Special organ: Village Heart (hero).
    - Set: Elder (hero 2, lucky 2) / Meek (meek 2; zombies hunt you).
13. **illager** [pillager, vindicator, evoker, illusioner]. Material skin.
    - Minion: head surgeon, guard, sentry; brave. Arms: punch, grip hand. Legs: walk 0.26.
    - Armour: helmet raider_kin; chest brawler 1; shoulders quick_hands 1; leggings swift 1; boots stealthy 1.
    - Hide: magic_ward 1. Special organ: Raider Gland (raider_kin; raids never target the minion).
    - Set: Raid Captain (kin with #minecraft:raiders, brawler 1) / Wanted (villagers flee you; iron golems hunt you).
14. **piglin** [piglin, piglin_brute, zombified_piglin]. Material skin.
    - Minion: head guard, courier (the piglin adds barterer). Arms: punch, grip hand. Legs: walk 0.25.
    - Armour: helmet piglin_kin; chest fireproof 1; shoulders brawler 1 while holding gold; leggings fireproof 1; boots swift 1.
    - Hide: piglin_kin. Special organ: Gold Gizzard (lucky 1).
    - Set: Bartered (piglin_kin, lucky 1) / Zombify (outside the Nether: Nausea for 5 s, then Weakness I).
15. **golem** [iron_golem, snow_golem]. Material golem_plate.
    - Minion: torso ×0.5; the iron golem repairs with iron ingots. Head: guard, sentry, bodyguard; territorial. Arms: fling, grip hand. Legs: walk 0.2, heavy 1.
    - Armour: helmet steady 1; chest iron_skin; shoulders flinger 1; leggings heavy 1; boots steady 1.
    - No hide. Special organs come from the mob files.
16. **arachnid** [spider, cave_spider]. Material chitin.
    - Minion: head hunter, guard; nocturnal; dark_sight; bite 1.5. Legs: climb 0.3, skitter, wall_climber.
    - Armour: helmet dark_sight; chest shell_guard 1; leggings and boots wall_climber (stacks: one piece clings, two climb).
    - Special organ: Spinneret (webbing).
    - Set: Brood (wall_climber 2, venom_proof, fall_guard 4) / fire_weak 2.
17. **vermin** [silverfish, endermite]. Material chitin.
    - Minion: head scavenger, digger; skittish; bite 1. Slithers by itself at 0.25.
    - Armour: helmet dark_sight; chest shell_guard 1; leggings stealthy 1; boots silent_steps.
    - Special organ: Burrow Gland (burrower 2).
    - Set: Infestation (kin with #minecraft:arthropod, stealthy 2) / frail 2.
18. **fowl** [chicken, parrot]. Material feather.
    - Minion: head scavenger, companion; skittish; bite 1 (peck). Wings: flap strike, lift (chicken 0.02 each, parrot 0.06 each), featherfall. Legs: hop 0.25, strut.
    - Armour: helmet pecking_order; chest light_boned 1; shoulders featherfall; leggings swift 1; boots fall_guard 1.
    - Hide (feather): downy. Stomach: Gizzard (seed_eater).
    - Set: Featherweight (featherfall, fall immunity) / frail 3 and prey.
19. **skyborne** [bat, phantom, bee]. Material feather; the arthropod overlay turns the bee to chitin.
    - Minion: flies by itself. Head: sentry, scavenger; nocturnal. Wings: lift 0.4 each (phantom), glider.
    - Armour: helmet dark_sight; chest light_boned 1; shoulders featherfall; leggings light_boned 1; boots fall_guard 1.
    - Set: Night Wing (featherfall, dark_sight) / light_hurts.
20. **spirit** [allay, vex]. Material ectoplasm.
    - Minion: flies by itself. Head: scavenger, bodyguard.
    - Armour: helmet spectral_sight; chest light_boned 2; leggings light_boned 1; boots featherfall.
    - Set: Ethereal (featherfall; 20% of projectiles pass through you) / frail 3.
21. **elemental** [blaze, breeze, ghast]. Material ember.
    - Minion: hovers by itself; fireproof 2. Head: sentry, guard.
    - Armour: fireproof 1 on every piece. Special organs: a core per mob.
    - Sets come from the mob files.
22. **marine** [cod, salmon, pufferfish, dolphin]. Material scale.
    - Minion: swims by itself, gills. Head: fisher, courier. Tail: +30% swim.
    - Armour: helmet oxygen +2; chest waterborn 1; hips waterborn 1; leggings waterborn 1; boots swim speed +0.15.
    - Special organ: Swim Bladder (waterborn 1).
    - Set: Gills (gills, waterborn 3) / dry_out (60 s).
23. **guardian** [guardian, elder_guardian]. Material scale.
    - Minion: torso ×0.6, self-contained swimmer. Head: sentry. Tail: +30% swim.
    - Armour: helmet submerged mining speed +50%; chest barbed 2 (spikes); hips waterborn 1.
    - Special organ: Prism Eye (guardian_beam).
    - Set: Deep One (gills; full submerged mining speed) / sluggish 1 on land.
24. **cephalopod** [squid, glow_squid]. Material gristle.
    - Minion: self-contained swimmer. Tentacles: amphibious (swim 0.35, land slither 0.1).
    - Armour: helmet gills; chest waterborn 1; leggings slick; boots waterborn 1.
    - Special organ: Ink Sac (vanilla `ink_sac`).
    - Set: Kraken (gills, waterborn 3, night vision underwater) / dry_out (60 s).
25. **slime** [slime, magma_cube]. Material gel.
    - Minion: self-contained, hops, bouncy.
    - Armour: every piece is a stand-in; boots bouncy; others fall_guard 1. Special organs: a core per mob.
    - Set: Gelatinous (fall immunity, bouncy) / sluggish 1.
26. **volatile** [creeper]. Material gristle; the creeper's own file supplies everything distinctive.
27. **sculk** [warden]. Material sculk. Everything is in the warden's file; gated by `boss_parts`.

**No family:** strider and wither. Their archetype plus overlays plus mob file suffice. This deliberately shows that a family is optional. The strider's file picks material ember; the wither's picks bone.

### 3.4 Overlays (keyed off tags)
Overlays patch any body. Their members come from tags, so modded mobs join through the tags their authors already set for Smite, Bane and so on. Vanilla tag contents were checked in the 1.21.1 client jar.

**Condition overlays:**
- **rotting** — `#minecraft:zombies`: zombie, husk, drowned, zombie_villager, zombified_piglin, zoglin, zombie_horse. Priority 200.
  - Material durability ×0.75, enchantability −4.
  - Minion: head gets hungering; torso gets sun_cursed.
  - Armour: helmet gets dead_face.
  - Stomach: iron_gut.
  - Set: Shambler (dead_face, brawler 2) / sun_cursed.
- **skeletal** — `#minecraft:skeletons`: skeleton, stray, wither_skeleton, skeleton_horse, bogged. Priority 210.
  - Material becomes bone; no hide.
  - Organ set becomes [marrow]. Today skeletons give no organs, and the brief says every mob has one.
  - Minion torso: hollow_frame and leaky.
  - Set: Ossuary (skeleton_kin, sharpshooter 2) / sun_cursed.
- **undead** — `#minecraft:undead` (the two above, plus phantom and wither). Priority 150.
  - Minion torso immune to poison and regeneration (as vanilla).
  - Full sets add inverted_healing to the drawback.
- **nether** — `#bloodandbones:overlay/nether`: hoglin, zoglin, piglin, piglin_brute, zombified_piglin, strider, blaze, ghast, magma_cube, wither_skeleton, wither.
  - Torso and chestplate add fireproof 1.
- **aquatic** — `#minecraft:aquatic`: turtle, axolotl, guardian, elder_guardian, cod, pufferfish, salmon, dolphin, squid, glow_squid, tadpole.
  - A minion torso breathes water; out of water it loses 1% power a minute instead of suffocating.
- **frozen** — `#minecraft:freeze_immune_entity_types`: stray, polar_bear, snow_golem, wither.
  - Torso and hide get insulated; sets add heat_hurts to the drawback.
- **ender** — `#bloodandbones:overlay/ender`: enderman, endermite, shulker.
  - Minion torso blinks toward its goal after 3 s stuck (it rescues jank pathing).
  - Chest adds a 20% blink when hurt.
  - Sets add water_hurts.
- **arthropod** — `#minecraft:arthropod`: bee, endermite, silverfish, spider, cave_spider.
  - Material becomes chitin where the family has a softer one (the bee).
  - Sets add bane_weak (+50% damage from Bane of Arthropods weapons).
- **boss** — `#c:bosses` plus `#bloodandbones:bosses` (adds the warden): wither, warden.
  - Health factor ×0.3; scrap density ×2; gated by config `boss_parts`.
- **bloodless_mob** — the existing `#bloodandbones:bloodless` tag (20 mobs).
  - The organ set is replaced by the mob's special organ(s) only ("cores"); no hide.

**Tag-trait overlays** (one small effect each; free diversity for modded mobs):
- **fall** — `#minecraft:fall_damage_immune` (iron_golem, snow_golem, shulker, allay, bat, bee, blaze, cat, chicken, ghast, phantom, magma_cube, ocelot, parrot, wither, breeze): minion torso fall_guard 4 (immune); boots fall_guard 1.
- **breath** — `#minecraft:can_breathe_under_water` (#undead, axolotl, frog, guardian, elder_guardian, turtle, glow_squid, cod, pufferfish, salmon, squid, tadpole): minion torso gills.
- **snow** — `#minecraft:powder_snow_walkable_mobs` (rabbit, endermite, silverfish, fox): boots powder_walker.
- **deflect** — `#minecraft:deflects_projectiles` (breeze): torso and chest deflector 2.
- **raider** — `#minecraft:raiders` (evoker, pillager, ravager, vindicator, illusioner, witch): the minion is never targeted by raids.

### 3.5 Every one of the 79 mobs

| Mob | Archetype | Family | Overlays |
|---|---|---|---|
| allay | floater | spirit | bloodless_mob, fall |
| armadillo | quadruped | shellback | — |
| axolotl | quadruped | amphibian | aquatic, breath |
| bat | flier | skyborne | fall |
| bee | arthropod | skyborne | arthropod, bloodless_mob, fall |
| blaze | floater | elemental | nether, bloodless_mob, fall |
| bogged | biped | humanoid | skeletal, undead, bloodless_mob, breath |
| breeze | floater | elemental | bloodless_mob, fall, deflect |
| camel | quadruped | grazer | — |
| cat | quadruped | feline | fall |
| cave_spider | arthropod | arachnid | arthropod |
| chicken | bird | fowl | fall |
| cod | fish | marine | aquatic, breath |
| cow | quadruped | grazer | — |
| creeper | quadruped | volatile | bloodless_mob |
| dolphin | fish | marine | aquatic |
| donkey | quadruped | equine | — |
| drowned | biped | humanoid | rotting, undead, breath |
| elder_guardian | fish | guardian | aquatic, breath |
| enderman | biped | humanoid | ender |
| endermite | arthropod | vermin | arthropod, ender, bloodless_mob, snow |
| evoker | biped | illager | raider |
| fox | quadruped | canid | snow |
| frog | biped | amphibian | breath |
| ghast | tentacled | elemental | nether, bloodless_mob, fall |
| glow_squid | tentacled | cephalopod | aquatic, breath |
| goat | quadruped | grazer | — |
| guardian | fish | guardian | aquatic, breath |
| hoglin | quadruped | swine | nether |
| horse | quadruped | equine | — |
| husk | biped | humanoid | rotting, undead, breath |
| illusioner | biped | illager | raider |
| iron_golem | biped | golem | bloodless_mob, fall |
| llama | quadruped | grazer | — |
| magma_cube | blob | slime | nether, bloodless_mob, fall |
| mooshroom | quadruped | grazer | — |
| mule | quadruped | equine | — |
| ocelot | quadruped | feline | fall |
| panda | quadruped | bear | — |
| parrot | bird | fowl | fall |
| phantom | flier | skyborne | undead, fall, breath |
| pig | quadruped | swine | — |
| piglin | biped | piglin | nether |
| piglin_brute | biped | piglin | nether |
| pillager | biped | illager | raider |
| polar_bear | quadruped | bear | frozen |
| pufferfish | fish | marine | aquatic, breath |
| rabbit | quadruped | small_prey | snow |
| ravager | quadruped | behemoth | raider |
| salmon | fish | marine | aquatic, breath |
| sheep | quadruped | grazer | — |
| shulker | shelled | shellback | ender, bloodless_mob, fall |
| silverfish | arthropod | vermin | arthropod, bloodless_mob, snow |
| skeleton | biped | humanoid | skeletal, undead, bloodless_mob, breath |
| skeleton_horse | quadruped | equine | skeletal, undead, bloodless_mob, breath |
| slime | blob | slime | bloodless_mob |
| sniffer | quadruped | behemoth | — |
| snow_golem | biped | golem | frozen, bloodless_mob, fall |
| spider | arthropod | arachnid | arthropod |
| squid | tentacled | cephalopod | aquatic, breath |
| stray | biped | humanoid | skeletal, undead, frozen, bloodless_mob, breath |
| strider | biped | — | nether |
| tadpole | fish | amphibian | aquatic, breath |
| trader_llama | quadruped | grazer | — |
| turtle | quadruped | shellback | aquatic, breath |
| vex | floater | spirit | bloodless_mob |
| villager | biped | villager | — |
| vindicator | biped | illager | raider |
| wandering_trader | biped | villager | — |
| warden | biped | sculk | boss |
| witch | biped | villager | raider |
| wither | colossus | — | undead, nether, frozen, boss, bloodless_mob, fall, breath |
| wither_skeleton | biped | humanoid | skeletal, undead, nether, bloodless_mob, breath |
| wolf | quadruped | canid | — |
| zoglin | quadruped | swine | rotting, undead, nether, breath |
| zombie | biped | humanoid | rotting, undead, breath |
| zombie_horse | quadruped | equine | rotting, undead, breath |
| zombie_villager | biped | villager | rotting, undead, breath |
| zombified_piglin | biped | piglin | rotting, undead, nether, breath |

### 3.6 Scrap materials (15)
Columns: armour per piece (helmet/chest/legs/boots), toughness per piece, knockback resistance per piece, durability multiplier (× vanilla's per-slot 11/16/15/13), enchantability, quirk per piece.

| Material | Armour | Tough | KB | Dur | Ench | Quirk per piece | Families |
|---|---|---|---|---|---|---|---|
| hide_plate | 1/4/3/1 (9) | 0 | 0 | 12 | 12 | max health +0.5 | grazer, equine, swine |
| sinew | 1/3/2/1 (7) | 0 | 0 | 10 | 15 | speed +2% | canid, feline, small_prey |
| bear_hide | 2/5/4/2 (13) | 0.5 | 0 | 16 | 9 | speed −2% | bear, behemoth |
| shell | 2/6/4/2 (14) | 1.0 | 0.05 | 18 | 9 | speed −4% | shellback |
| chitin | 2/4/3/1 (10) | 1.0 | 0 | 14 | 10 | safe fall +0.5 | arachnid, vermin, arthropod overlay |
| bone | 2/4/3/1 (10) | 0 | 0 | 9 | 18 | — | skeletal overlay, wither |
| skin | 1/4/3/1 (9) | 0 | 0 | 11 | 14 | luck +0.25 | humanoid, villager, illager, piglin |
| scale | 1/3/3/1 (8) | 0 | 0 | 11 | 14 | oxygen +0.5 | amphibian, marine, guardian |
| feather | 1/2/2/1 (6) | 0 | 0 | 8 | 20 | gravity −3% | fowl, skyborne |
| ectoplasm | 1/2/2/1 (6) | 0 | 0 | 7 | 22 | gravity −5% | spirit |
| gel | 1/3/2/1 (7) | 0 | 0.05 | 10 | 12 | fall damage −10% | slime |
| ember | 1/4/3/1 (9) | 1.0 | 0 | 12 | 16 | burning time −15% | elemental, strider |
| golem_plate | 2/5/4/2 (13) | 1.0 | 0.05 | 20 | 5 | speed −3% | golem |
| sculk | 3/6/5/3 (17) | 2.0 | 0.1 | 25 | 12 | sneaking speed +0.1 | sculk |
| gristle | 1/3/2/1 (7) | 0 | 0 | 9 | 10 | — | fallback, cephalopod, volatile |

For comparison, vanilla leather totals 7, chain 12, iron 15 and diamond 20.

### 3.7 A modded mob on day one
Take `examplemod:glimmerbeast`: a PathfinderMob in `#minecraft:undead`, with a generic or exported rig and no data of its own.
1. **Archetype.** Its rig's leg count and hitbox aspect match quadruped.
2. **Family.** No family matches, which is allowed. A class predicate would match if it extended `Pig` (checked on a throwaway instance at the first resolution).
3. **Overlays.** undead from the tag its author set for Smite, and breath through `#can_breathe_under_water`.
4. **Numbers from its own attributes** (`DefaultAttributes.getSupplier`):
   - torso health = its MAX_HEALTH;
   - bite from ATTACK_DAMAGE;
   - follow range from FOLLOW_RANGE;
   - leg speed from its movement speed, clamped to 0.1–0.35.
5. **Organs.** It gets heart, lungs, stomach and eyes with archetype traits, plus undead immunities. Its armour is gristle.
6. **Later.** Its author adds one line to `#bloodandbones:family/bear`, or ships `mob_traits/examplemod/glimmerbeast.json`, for a signature.

---

## 4. Data schema

### 4.1 Files
All files are hot-reloadable. Each is loaded by a `SimpleJsonResourceReloadListener` registered in `AddReloadListenerEvent`, parsed with `RegistryOps` from `getRegistryAccess()` so holder and tag codecs work, as `rig/` and `butchery/` load today.

| Path | Contents |
|---|---|
| `data/<ns>/mob_group/<id>.json` | Archetypes, families and overlays (`"kind"`) |
| `data/<ns>/tags/entity_type/family/<id>.json`, `.../overlay/<id>.json` | Membership, additive, so another mod adds a mob in one line |
| `data/<ns>/mob_traits/<entity_ns>/<entity_path>.json` | Optional per-mob file: signature, overrides, variants. Mirrors `rig/`. |
| `data/<ns>/trait/<id>.json` | A named, levelled trait (the reusable unit) |
| `data/<ns>/organ/<id>.json` | Organ kinds: item, look, tint, which armour pieces take it, extra drops, bloodless name |
| `data/<ns>/scrap_material/<id>.json` | Armour base stats, look, density |
| `data/<ns>/armour_tier/<id>.json` | Blood steel, blood diamond, soul netherite |
| `data/bloodandbones/bone_slot_rules.json` | The slot classifier, blob sockets, clamps |
| `data/<ns>/data_maps/item/hide_sources.json`, `organ_sources.json` | NeoForge data maps (as `vent_effects` is today): unstamped vanilla items → mob |
| `data/<ns>/recipe/*.json` | The carcass_armour and carcass_armour_fitting recipes, Soul Canister filling and emptying, Blood Trough, Charging Cradle, Brass Sheathing |

Effect types live in a NeoForge custom registry, `bloodandbones:trait_effect_type` (a DeferredRegister of MapCodecs, created in `NewRegistryEvent`). Addons add types in code; datapacks only compose them.

### 4.2 Merge rules
- **Order.** Layers apply archetype → family → overlays (ascending priority) → variants → mob file.
- **Keys.** Inside `parts`, keys are tried most-specific first: `bones.<name>`, then `leg.hind` / `arm.wing`, then `leg`.
- **Scalars** replace: `speed`, `jobs`, `health`, `disposition`, `scrap_material`.
- **Trait lists** add to the inherited list, deduplicated by trait id with the highest level winning. An object `{"add": [...], "remove": [ids]}` edits instead; `{"replace": true, ...}` inside any object replaces its lists.
- **Numbers left out** come from the mob's `DefaultAttributes`: max_health, attack_damage, movement_speed, follow_range, armor, knockback_resistance, horse jump_strength.
- **Variants** patch by carcass traits. `CarcassLook` already captures variant, profession, wool and mushroom. The captured traits map is extended with:
  - `variant` from any `VariantHolder` (frog, fox, rabbit including `evil`, panda main gene, axolotl, parrot);
  - `profession`;
  - `charged` (creeper);
  - `name` (a custom name: "Johnny" for the vindicator);
  - `gene` (panda).

### 4.3 Trait references and levels
A trait is referenced in one of three ways:
- `"ns:id"` (level 1);
- `{"trait": "ns:id", "level": 2}`;
- `{"trait": "ns:id", "level": {"from": "max_health", "divide": 8, "min": 1, "max": 3}}`.

`from` takes max_health, attack_damage, armor, movement_speed or weight (from the rig). That is how numbers differ per mob with no per-mob data.

An inline anonymous trait is `{"name": "trait.ns.key", "effects": [...]}`.

### 4.4 Complete family file: `data/bloodandbones/mob_group/grazer.json`
```json
{
  "kind": "family",
  "priority": 100,
  "members": ["minecraft:cow", "minecraft:mooshroom", "minecraft:sheep", "minecraft:goat",
              "minecraft:llama", "minecraft:trader_llama", "minecraft:camel", "#bloodandbones:family/grazer"],
  "match": [
    {"score": 80, "class": "net.minecraft.world.entity.animal.Cow"},
    {"score": 80, "class": "net.minecraft.world.entity.animal.Sheep"},
    {"score": 80, "class": "net.minecraft.world.entity.animal.goat.Goat"},
    {"score": 80, "class": "net.minecraft.world.entity.animal.horse.Llama"},
    {"score": 80, "class": "net.minecraft.world.entity.animal.camel.Camel"}
  ],
  "scrap_material": "bloodandbones:hide_plate",
  "colour": "#6b4a2f",
  "parts": {
    "torso": {
      "minion": {"health_factor": 1.5, "traits": ["bloodandbones:beast_of_burden"]},
      "armour": ["bloodandbones:barrel_chest"]
    },
    "head": {
      "minion": {
        "jobs": ["bloodandbones:herder", "bloodandbones:courier", "bloodandbones:companion"],
        "disposition": "docile",
        "bite": {"style": "punch", "damage": 1.0, "knockback": 0.6}
      },
      "armour": ["bloodandbones:cud_chewer"]
    },
    "leg": {
      "minion": {
        "movement": {"mode": "walk", "speed": 0.2, "gait": "amble"},
        "traits": [{"trait": "bloodandbones:sure_footed", "level": 1}]
      },
      "armour": {"leggings": ["bloodandbones:sturdy"], "boots": ["bloodandbones:hooves"]}
    }
  },
  "hide": [{"trait": "bloodandbones:thick_hide", "level": 1}],
  "organ_traits": {
    "bloodandbones:heart":   {"minion": [{"trait": "bloodandbones:hardy", "level": 1}],
                              "armour": [{"trait": "bloodandbones:hardy", "level": 1}]},
    "bloodandbones:stomach": {"minion": ["bloodandbones:forager"], "armour": ["bloodandbones:four_chambers"]}
  },
  "full_set": {
    "name": "set.bloodandbones.herd_beast",
    "bonus": [{"trait": "bloodandbones:hauler", "level": 3}, {"trait": "bloodandbones:hardy", "level": 2}],
    "drawback": [{"trait": "bloodandbones:meek", "level": 2}]
  }
}
```
The lungs and eyes are left out on purpose: they come from the quadruped archetype. The grazers' special organs (rumen, lanolin gland and so on) come from each mob's own file.

### 4.5 Archetype file: `mob_group/quadruped.json`
```json
{
  "kind": "archetype",
  "priority": 0,
  "match": [
    {"score": 50, "rig": {"legs": [4, 6], "arms": 0}},
    {"score": 20, "aspect": [0.8, 99], "category": "creature"}
  ],
  "generic_rig": "bloodandbones:generic/quadruped",
  "scrap_material": "bloodandbones:gristle",
  "organs": {
    "torso": ["bloodandbones:heart", "bloodandbones:lungs", "bloodandbones:stomach"],
    "head": ["bloodandbones:eye", "bloodandbones:eye"]
  },
  "parts": {
    "torso": {"minion": {"health_factor": 1.0, "self_move": {"mode": "crawl", "speed": 0.12}},
              "armour": [{"trait": "bloodandbones:hardy", "level": 1}]},
    "head":  {"minion": {"jobs": ["bloodandbones:companion", "bloodandbones:courier"], "disposition": "loyal",
                         "bite": {"style": "bite", "damage": 1.0}},
              "armour": [{"trait": "bloodandbones:steady", "level": 1}]},
    "leg":   {"minion": {"movement": {"mode": "walk", "speed": 0.22, "gait": "trot"}},
              "armour": [{"trait": "bloodandbones:swift", "level": 1}]},
    "tail":  {"minion": {"traits": [{"trait": "bloodandbones:steady", "level": 1}]},
              "armour": [{"trait": "bloodandbones:swift", "level": 1}]}
  },
  "organ_traits": {
    "bloodandbones:heart":   {"minion": [{"trait": "bloodandbones:hardy", "level": 1}], "armour": [{"trait": "bloodandbones:hardy", "level": 1}]},
    "bloodandbones:lungs":   {"minion": ["bloodandbones:long_winded"], "armour": ["bloodandbones:long_winded"]},
    "bloodandbones:stomach": {"minion": ["bloodandbones:forager"], "armour": ["bloodandbones:omnivore"]},
    "bloodandbones:eye":     {"minion": ["bloodandbones:keen_eye"], "armour": ["bloodandbones:dark_sight"]}
  },
  "full_set": {"name": "set.bloodandbones.beast",
               "bonus": [{"trait": "bloodandbones:hardy", "level": 2}],
               "drawback": [{"trait": "bloodandbones:sluggish", "level": 1}]}
}
```

### 4.6 Overlay files
**`mob_group/rotting.json`**
```json
{
  "kind": "overlay",
  "priority": 200,
  "members": ["#minecraft:zombies", "#bloodandbones:overlay/rotting"],
  "scrap_material_patch": {"durability_mult": 0.75, "enchantability_add": -4},
  "parts": {
    "head":  {"minion": {"traits": {"add": ["bloodandbones:hungering"]}},
              "armour": {"add": ["bloodandbones:dead_face"]}},
    "torso": {"minion": {"traits": {"add": ["bloodandbones:sun_cursed"]}}}
  },
  "organ_traits": {
    "bloodandbones:stomach": {"minion": ["bloodandbones:iron_gut"], "armour": ["bloodandbones:iron_gut"]}
  },
  "full_set": {"name": "set.bloodandbones.shambler",
               "bonus": ["bloodandbones:dead_face", {"trait": "bloodandbones:brawler", "level": 2}],
               "drawback": ["bloodandbones:sun_cursed"]}
}
```
**`mob_group/fall.json`** (a tag-trait overlay)
```json
{
  "kind": "overlay",
  "priority": 50,
  "members": ["#minecraft:fall_damage_immune"],
  "parts": {
    "torso": {"minion": {"traits": {"add": [{"trait": "bloodandbones:fall_guard", "level": 4}]}}},
    "leg":   {"armour": {"boots": {"add": [{"trait": "bloodandbones:fall_guard", "level": 1}]}}}
  }
}
```

### 4.7 Trait files
**`trait/venomous.json`**
```json
{
  "name": "trait.bloodandbones.venomous",
  "max_level": 3,
  "stacking": "max",
  "contexts": ["minion", "armour"],
  "icon": "minecraft:spider_eye",
  "effects": [
    {
      "trigger": "attack",
      "chance": 1.0,
      "cooldown": 0,
      "requirements": {"condition": "minecraft:damage_source_properties", "predicate": {"is_direct": true}},
      "effect": {
        "type": "bloodandbones:mob_effect",
        "target": "victim",
        "effect": "minecraft:poison",
        "amplifier": {"type": "minecraft:linear", "base": 0, "per_level_above_first": 1},
        "duration": 60
      }
    }
  ]
}
```
**`trait/wall_climber.json`** (levels add up across worn pieces)
```json
{
  "name": "trait.bloodandbones.wall_climber",
  "max_level": 2,
  "stacking": "sum",
  "contexts": ["minion", "armour"],
  "effects": [
    {"trigger": "passive",
     "effect": {"type": "bloodandbones:flag", "flag": "climb",
                "strength": {"type": "minecraft:linear", "base": 1, "per_level_above_first": 1}}}
  ]
}
```
**`trait/blast.json`** (an active organ ability)
```json
{
  "name": "trait.bloodandbones.blast",
  "max_level": 2,
  "contexts": ["minion", "armour"],
  "cost_mb": 50,
  "effects": [
    {"trigger": "activate", "cooldown": 1200, "range": 3,
     "effect": {"type": "bloodandbones:detonate",
                "power": {"type": "minecraft:linear", "base": 3, "per_level_above_first": 1},
                "fire": false, "block_damage": false, "minion_powers_down": true}}
  ]
}
```

Trait fields:
- `trigger`: one of the 8 in §5.2.
- `interval`: for tick.
- `chance`, `cooldown` (0, or at least 20 ticks), `range` (activate, for minion AI).
- `requirements`: any vanilla LootItemCondition, evaluated with `Enchantment.damageContext`, `entityContext` or `locationContext` for the trigger.
- `context`: limits one effect to minion or armour.
- `include`: `[{trait, level}]` composes one trait from others.

An effect in a context it does not support is skipped, and caught by the lint.

### 4.8 Per-mob file: `mob_traits/minecraft/rabbit.json`
```json
{
  "signature": ["parts.leg.hind", "organs.bloodandbones:rabbit_foot"],
  "parts": {
    "leg.hind": {
      "minion": {"movement": {"mode": "hop", "speed": 0.35, "gait": "hop"},
                 "traits": {"add": [{"trait": "bloodandbones:springy", "level": 2}]}},
      "armour": {"leggings": [{"trait": "bloodandbones:springy", "level": 2}],
                 "boots": [{"trait": "bloodandbones:fall_guard", "level": 2}, {"trait": "bloodandbones:swift", "level": 1}]}
    }
  },
  "organs": {"leg.hind": {"add": ["bloodandbones:rabbit_foot"]}},
  "organ_traits": {
    "bloodandbones:rabbit_foot": {
      "minion": [{"trait": "bloodandbones:evasive", "level": 1}, {"trait": "bloodandbones:lucky", "level": 1}],
      "armour": [{"trait": "bloodandbones:lucky", "level": 2}, "bloodandbones:leap"]
    }
  },
  "hide": [{"trait": "bloodandbones:swift", "level": 1}],
  "variants": [
    {"if": {"trait": "variant", "equals": "evil"},
     "patch": {"parts": {"head": {"minion": {"jobs": ["bloodandbones:bodyguard", "bloodandbones:guard"],
                                              "disposition": "berserk",
                                              "bite": {"style": "bite", "damage": 8.0}}}}}}
  ],
  "full_set": {
    "replace": true,
    "name": "set.bloodandbones.warren",
    "bonus": [{"trait": "bloodandbones:springy", "level": 4}, {"trait": "bloodandbones:light_boned", "level": 3}],
    "drawback": [{"trait": "bloodandbones:frail", "level": 2}, "bloodandbones:prey"]
  }
}
```
The shulker's file is the only one that needs a slot line: `"bone_slots": {"lid": {"slot": "arm", "form": "shell"}}`.

### 4.9 Other files
**`organ/rumen.json`**
```json
{
  "item": "bloodandbones:gland",
  "name": "organ.bloodandbones.rumen",
  "bloodless_name": "organ.bloodandbones.rumen.bloodless",
  "look": "sac",
  "tint": "#8b9a46",
  "armour_pieces": ["chestplate", "leggings"],
  "extra_drops": []
}
```
**`organ/powder_sac.json`**
```json
{
  "item": "bloodandbones:gland",
  "name": "organ.bloodandbones.powder_sac",
  "bloodless_name": "organ.bloodandbones.powder_sac.bloodless",
  "look": "sac", "tint": "#4c7a3a",
  "armour_pieces": ["chestplate"],
  "extra_drops": [{"item": "minecraft:gunpowder", "min": 1, "max": 2}]
}
```
The generic organs point at the items that exist today (`"item": "bloodandbones:heart"` and so on), so self-surgery keeps working. `organ/rabbit_foot.json` uses `"item": "minecraft:rabbit_foot"`.

**`scrap_material/hide_plate.json`**
```json
{
  "look": "hide",
  "armour": {"helmet": 1, "chestplate": 4, "leggings": 3, "boots": 1},
  "toughness": 0.0,
  "knockback_resistance": 0.0,
  "durability": 12,
  "enchantability": 12,
  "density": 24,
  "quirk": [{"attribute": "minecraft:generic.max_health", "amount": 0.5, "operation": "add_value"}]
}
```
**`armour_tier/blood_steel.json`**
```json
{"order": 1, "item": "bloodandbones:blood_steel_ingot",
 "bonus": {"helmet": 1, "chestplate": 2, "leggings": 2, "boots": 1},
 "toughness": 0.5, "knockback_resistance": 0.0, "durability_mult": 1.5, "fire_resistant": false, "trim": "blood_steel"}
```
**`bone_slot_rules.json`** (excerpt)
```json
{
  "rules": [
    {"match": "(^|_)(upper_body|lower_body|body\\d|ribcage|chest|thorax|abdomen)$", "slot": "torso_ext", "not_root": true},
    {"match": "^neck$", "slot": "neck"},
    {"match": "(^|_)head(_|$)", "slot": "head"},
    {"match": "(^|_)wing(_|$)", "slot": "arm", "form": "wing"},
    {"match": "^arms$", "slot": "arm", "form": "pair"},
    {"match": "(^|_)arm$", "slot": "arm"},
    {"match": "(^|_)(leg|haunch)$", "slot": "leg"},
    {"match": "^tentacle\\d*$", "slot": "leg", "form": "tentacle"},
    {"match": "(^|_)(tail\\d*|fluke)(_|$)|^body_back$", "slot": "tail"},
    {"match": "^segment\\d+$", "slot": "segment_chain"}
  ],
  "sub_slots": [{"match": "(middle|mid)_", "sub": "mid"}, {"match": "front", "sub": "front"}, {"match": "(hind|haunch|back)", "sub": "hind"}],
  "unknown": "geometry",
  "blob_sockets": {"head": [0, 1, -0.6], "limbs": [[-1, -1, 1], [1, -1, 1], [-1, -1, -1], [1, -1, -1]], "tail": [0, 0, 1]},
  "clamps": {"health": [6, 150], "arm_damage": [1, 10], "leg_speed": [0.05, 0.4]}
}
```
**`data_maps/item/hide_sources.json`** (unstamped vanilla hides)
```json
{"values": {
  "minecraft:leather": {"entity": "minecraft:cow"},
  "minecraft:rabbit_hide": {"entity": "minecraft:rabbit"},
  "minecraft:feather": {"entity": "minecraft:chicken"},
  "minecraft:armadillo_scute": {"entity": "minecraft:armadillo"},
  "minecraft:turtle_scute": {"entity": "minecraft:turtle"},
  "minecraft:phantom_membrane": {"entity": "minecraft:phantom"},
  "minecraft:shulker_shell": {"entity": "minecraft:shulker"}
}}
```
`organ_sources.json` maps the same way:
- `minecraft:rabbit_foot` → rabbit / rabbit_foot
- `minecraft:ink_sac` → squid / ink_sac
- `minecraft:glow_ink_sac` → glow_squid / glow_sac
- `minecraft:spider_eye` → spider / eye

### 4.10 Item components (new)

| Component | Shape | On |
|---|---|---|
| `bloodandbones:source` | {entity, part?: head/torso/arm/leg/tail, organ?: id, baby} | scraps; hides (every `"kind": "hide"` yield of `CarcassButchery.skin` is stamped); organ items (stamped by `Surgery.harvest`) |
| `bloodandbones:carcass_armour` | {slot, body: {entity, baby}, shoulders?: {entity}, hips?: {entity}, hide?: {entity}, organ?: {entity, organ}, tier: 0–3} | the four carcass armour items |
| `bloodandbones:strapped_tank` | {tier}, alongside the existing `bloodandbones:fluid` | a carcass chestplate carrying a backtank |
| `bloodandbones:minion_build` (replaces `FRAME`) | {kind: organic or cybernetic, torso: PieceRef, extensions: [{socket, PieceRef}], sockets: {socketId: PieceRef}, organ?: ItemStack, saddle, chest} | the table's block entity; the Dormant Minion item |

A PieceRef is the existing `CarcassPieceItem.Piece` minus the blood fields: entity, bone, texture, coats, freshness, skinned, traits and baby. Only sources are stored. Stats are resolved from data when they are read, except `MAX_DAMAGE`, which is baked when a piece is crafted, tiered or has a hide fitted.

Items with different sources do not stack. Three new Create `ItemAttributeType`s, registered on `CreateRegistries.ITEM_ATTRIBUTE_TYPE` (checked in the jar) as today's piece attributes are, let filters sort them: "scraps of <mob>", "scrap part <part>" and "from family <family>".

### 4.11 Sync, caching, commands
- **Sync.** The raw files go to clients on `OnDatapackSyncEvent`, one payload per file kind, split below 256 KB (the butchery tables alone were about 52 KB). The resolved group ids per entity type go with them, so clients never run class checks. Both sides resolve with the same code. Loot-condition requirements are evaluated only on the server; the client needs only the flags used by predicted movement (climb, bounce, glide, powder snow), which use simple conditions.
- **Caches.** The resolved per-mob view is cached per (type, baby). The `ActiveTraits` of each host is cached and rebuilt on equipment change, minion rebuild and a reload generation counter.
- **Commands.**
  - `/bloodandbones traits explain <entity>`: the layer chain and the final table for every slot, hide, organ and set.
  - `/bloodandbones traits dump`: a CSV of every mob for balance review and pack authors.

### 4.12 Retuning
- Change one ability everywhere: edit `trait/<id>.json`.
- Rebalance a family: override its `mob_group` file.
- Move a mob: add it to a tag, or set `family` in its mob file.
- Change armour stats: edit `scrap_material/` or `armour_tier/`.
- Turn an effect type off on a server: config `disabled_effect_types`.

Because items store only sources, all of these reach existing armour and minions (see the open questions).

---

## 5. Effect vocabulary

### 5.1 The model
A **trait** is a named, levelled bundle. Each entry in it is (trigger, requirements, chance, cooldown, effect).
- Effects take vanilla `LevelBasedValue`s (constant, linear, clamped, fraction, levels_squared, lookup).
- Minions and armour share one runtime: a minion's fitted parts and a player's worn pieces both become one `ActiveTraits` list (a non-serialized data attachment), grouped by trigger.
- Every handler bails out on an empty list, so entities without traits cost nothing.

### 5.2 Triggers (8)

| Trigger | Hook | Cost |
|---|---|---|
| passive | Applied on rebuild. Conditional passives are re-checked every 10 ticks, staggered by entity id. | S |
| tick (interval of at least 20) | `PlayerTickEvent.Post` on the server; `MinionEntity#aiStep`. Staggered by id. | S |
| hurt (filter: melee, projectile, fire, any) | `LivingIncomingDamageEvent` to modify or cancel; `LivingDamageEvent.Post` for reactions | S |
| attack | The victim's `LivingIncomingDamageEvent` when the source entity is the host (outgoing multipliers); `LivingDamageEvent.Post` for on-hit. Direct melee only, unless `projectile: true`. | S |
| fall | `LivingFallEvent` (distance and damage multiplier) | S |
| kill | `LivingDeathEvent` whose source entity is the host | S |
| targeted | `LivingChangeTargetEvent` whose new target is the host (fires for goal and Brain targeting) | S |
| activate | Players: a `KeyMapping` "Organ Ability" (default G, rebindable), read in `ClientTickEvent.Post` and sent as `OrganActivatePayload`; the server fires the next ready activate facet in order helmet → chest → legs → boots, so repeated presses cycle. Minions: the AI fires when a target is within the effect's `range` and its requirements hold. | M |

### 5.3 Conditions
- **Any vanilla LootItemCondition:** entity_properties (flags such as is_sneaking, is_sprinting, is_swimming, is_on_fire; the 1.21 movement and fall_distance predicates), location_check (light, fluid, can_see_sky, biome, dimension), weather_check, time_check, damage_source_properties (tags, is_direct), random_chance, inverted, all_of, any_of.
- **Four of our own (S each):**
  - `bloodandbones:health_below` {fraction}
  - `bloodandbones:power_below` {fraction}
  - `bloodandbones:near` {entities or blocks tag, radius, count}
  - `bloodandbones:dry_for` {seconds, at_least or at_most}, backed by a per-host counter in `ActiveTraits`

### 5.4 Effect types (30)
S = small, M = medium, L = large. Class names marked (✓) were checked in the NeoForge 21.1.249 or Create 6.0.11 jars.

| # | Type (params) | Minion | Armour | Hook | Cost |
|---|---|---|---|---|---|
| 1 | **attribute** (attribute, amount, operation, id). Reuses the vanilla `EnchantmentAttributeEffect` codec. | Stat on build | Stat while worn | Unconditional armour: `ItemAttributeModifierEvent` (✓), so vanilla tooltips list it. Minions and conditional effects: `AttributeInstance#addOrUpdateTransientModifier`. Works with every vanilla and modded attribute, including step_height, gravity, safe_fall_distance, jump_strength, scale, oxygen_bonus, water_movement_efficiency, movement_efficiency, burning_time, explosion_knockback_resistance, sneaking_speed, mining_efficiency, submerged_mining_speed, sweeping_damage_ratio, luck, both interaction ranges, `neoforge:swim_speed`, and our four (§5.7). | S |
| 2 | **mob_effect** (effect, amplifier, duration, target: self/attacker/victim/vehicle/area, radius, filter, clear: harmful/all) | yes | yes | `LivingEntity#addEffect`. Passive and tick uses are hidden and ambient, refreshed before expiry (night vision above 200 ticks, to avoid flicker). | S |
| 3 | **damage** (direction: in/out, multiplier or add, damage_tags, vs: entity tag, attacker_weapon_enchantment, chance, lethal_save, cooldown) | yes | yes | `LivingIncomingDamageEvent#setAmount` (✓). lethal_save: `LivingDeathEvent` (✓) cancelled and health set to 1; for a minion this is collapse. | S |
| 4 | **immunity** (mob_effects, damage_tags, breathe) | yes | yes | `MobEffectEvent.Applicable` set to DO_NOT_APPLY (✓); `EntityInvulnerabilityCheckEvent#setInvulnerable` (✓); `LivingBreatheEvent#setCanBreathe` (✓) | S |
| 5 | **regen** (amount, interval) | heal (organic costs 5 mB per HP) | heal | trait tick | S |
| 6 | **mend** (mode: item or self, items, amount, interval) | the item heals it (iron for golems, brass sheets for cybernetic) | anvil repair; self-repair of durability | `mobInteract`; `Item#isValidRepairItem`; trait tick | S |
| 7 | **flag** (name, strength) | subset | yes | Per flag, §5.6 | S–M |
| 8 | **visibility** (multiplier, vs) | yes | yes | `LivingEvent.LivingVisibilityEvent#modifyVisibility` (✓) | S |
| 9 | **kin** (entities, provoked_seconds) | yes | yes | `LivingChangeTargetEvent#setCanceled` (✓), unless the host hurt that mob within the window (last-hurt timestamp) | S |
| 10 | **reaction** (entities, mode: hunt or flee, radius) | yes | yes | `EntityJoinLevelEvent` adds one `NearestAttackableTargetGoal` or `AvoidEntityGoal` only to entity types in the tag. The predicate reads the target's cached `ActiveTraits`. | M |
| 11 | **sense** (kind: night, see_invisible, echolocate, tremor, reveal, alert; range; filter) | Follow range; target acquisition without line of sight (echolocate, tremor) | night → mob_effect; reveal and echolocate: a client-only outline through `RenderLevelStageEvent` from a synced list, because server Glowing would show everyone; alert: `AlertPayload` ping with a direction subtitle | M |
| 12 | **diet** (foods, effect: safe, bonus_saturation, cure_one, edible, graze, toxic) | Forage for blood (at most up to 50%) | eating effects | `LivingEntityUseItemEvent.Finish`; `PlayerInteractEvent.RightClickItem` and `RightClickBlock` (grass, newly edible items) | S |
| 13 | **produce** (item or loot_table, count, interval, consumes, cost_mb) | Organic only, into its inventory | into your inventory | trait tick; `ItemHandlerHelper.insertItemStacked` or `Inventory#add` (dropped if full) | S |
| 14 | **aura** (radius, interval of at least 20, filter, action: mob_effect, pull_items, bonemeal, calm, push, rally) | yes | yes | tick plus `getEntitiesOfClass`; `BoneMealItem.growCrop`; `Mob#setTarget` (calm clears, rally sets it to the attacker). At most once a second. | S–M |
| 15 | **pack** (per_ally, allies: tag or family, radius, cap) | yes | yes | outgoing multiplier in `LivingIncomingDamageEvent` | S |
| 16 | **vanilla** (effect: any `EnchantmentEntityEffect`, target) | yes | yes | `effect.apply(ServerLevel, level, new EnchantedItemInUse(stack, slot, host), target, pos)`. One adapter gives all_of, apply_mob_effect, damage_entity, explode, ignite, play_sound, replace_block, replace_disk, run_function, set_block_properties, spawn_particles and summon_entity, plus our 7 actions (§5.5). `damage_item` is linted out. The level passed is the trait level. | S |
| 17 | **impulse** (target, forward, up, away, radius) | yes | yes | `setDeltaMovement` plus `hurtMarked` (sends the motion packet to players). Covers leap, dash, fling, bounce and knockback. | S |
| 18 | **teleport** (mode: random, look, behind_target, to_owner; radius; who) | yes | yes | `LivingEntity#randomTeleport` (16 tries, as `EnderMan#teleport`), guarded by `EntityTeleportEvent.EnderEntity` (✓) | S |
| 19 | **projectile** (kind, count, spread, speed, damage, potion, power, cost_mb) | Ranged attack (`RangedAttackMob` with vanilla `RangedAttackGoal`) | activate, aimed along your look | `EntityType#create`, `Projectile#shoot`, `setOwner`, plus a per-kind setup table (arrow base damage and potion, fireball power, shulker bullet target) | M |
| 20 | **hitscan** (range, damage, damage_type, windup, beam) | ranged | activate | `level.clip` plus an AABB sweep, then `hurt(damageSources().source(type, host))`. The windup beam renderer is M. Covers the guardian beam, the sonic boom and the web shot. | S/M |
| 21 | **deflect** (chance, projectiles, mode: dodge, reflect or pass; arc: front or any) | yes | yes | `ProjectileImpactEvent` (✓) cancel; `Projectile#deflect(ProjectileDeflection.REVERSE)` | S |
| 22 | **detonate** (power, fire, block_damage, fuse, minion_powers_down) | Self-destruct, then powers down; never destroyed | a blast that spares the wearer | `Level#explode` with ExplosionInteraction NONE, unless mobGriefing and `minion_block_damage` allow; `ExplosionEvent.Detonate` removes the host from the affected list | M |
| 23 | **movement** (mode, speed, gait, step, jump, lift) | legs, the torso's own movement | — | Navigation and MoveControl swapped only on rebuild: Ground, `WallClimberNavigation`, `WaterBoundPathNavigation` with `SmoothSwimmingMoveControl`, `AmphibiousPathNavigation`, `FlyingPathNavigation` with `FlyingMoveControl`; `onClimbable()` and `canStandOnFluid()` overrides | M (the set of modes L) |
| 24 | **strike** (style, damage_mult, speed_mult, reach, knockback) | Arms and the head's bite | — | one `MinionStrikeGoal` with a style switch; `doHurtTarget` | M |
| 25 | **mount** (control: saddle, carrot_on_a_stick, warped_fungus_on_a_stick; seats; jump) | rideable | — | `getControllingPassenger`, `tickRidden`, `getRiddenInput`, `getRiddenSpeed`, `PlayerRideableJumping`, as AbstractHorse, Strider and Camel do | L |
| 26 | **job** (options) | head | — | goal packages, §6.9 | L in total |
| 27 | **disposition** (docile, brave, skittish, territorial, loyal, nocturnal, berserk, meek) | head | — | chooses the target and avoid goals | S |
| 28 | **storage** (slots) | torso | — | resize the inventory on rebuild | S |
| 29 | **power** (capacity_mult, drain_mult, refuel {item: mB}, feed_on_kill_mb) | yes | (through the blood_upkeep attribute) | the power system | S |
| 30 | **glow** () | emissive pass | emissive layer | `RenderType.eyes` layer | S (client) |

`include` (composing traits) is trivial and not counted.

### 5.5 Actions added to vanilla's registry
These seven are registered into `Registries.ENCHANTMENT_ENTITY_EFFECT_TYPE`, so datapack enchantments can use them too. Each is S.
- `launch` {up, away}
- `pull` {strength}
- `web` {seconds}: a temporary web block that decays like frosted ice
- `bleed` {seconds}: the new Bleeding effect
- `steal_item` {}: takes a mob's held item; never a player's
- `blink_target` {range}
- `ink_cloud` {radius, seconds}: Blindness

### 5.6 Closed lists (enumerations in code)
- **Flags (11):**
  - `climb`: a shared `PlayerTickEvent.Pre` on both client and server, because player movement is client-predicted. On horizontalCollision: strength 1 clings (fall no faster than −0.05); strength 2 or more climbs at +0.2 with jump held; fall distance resets. Minion: `onClimbable()` and `WallClimberNavigation`.
  - `glide`: `IItemExtension#canElytraFly` and `elytraFlightTick` (✓), chestplate only; costs hunger, not durability.
  - `bounce`: cancel `LivingFallEvent` and set upward velocity, not while sneaking; client parity.
  - `powder_snow`: `canWalkOnPowderedSnow` (✓).
  - `ender_mask`: `isEnderMask` (✓).
  - `piglin_neutral`: `makesPiglinsNeutral` (✓).
  - `silent_steps`: cancel `VanillaGameEvent` (✓) STEP, HIT_GROUND and SPLASH from the host; minion `dampensVibrations`.
  - `quick_draw`: `LivingEntityUseItemEvent.Tick#setDuration` for bows and crossbows.
  - `inverted_healing`: `MobEffectEvent.Applicable` swaps instant_health and instant_damage.
  - `trample`: minion `aiStep` breaks `#bloodandbones:trampleable`, only if mobGriefing and config allow.
  - `lava_walk`: minion `canStandOnFluid` for lava.
- **Movement modes (13):** walk, hop, climb, swim, amphibious, fly, hover, float, crawl, roll, slither, sink (walks the bottom of water), blink_step.
- **Gaits** (drawing only, no code cost): amble, trot, gallop, lope, prowl, lumber, plod, stomp, stride, strut, hop, skitter, scuttle, paddle, undulate, dangle, roll.
- **Strike styles (15):**
  - punch: a plain hit, or the held weapon with a hand grip
  - claw: bleed
  - bite
  - grab: Slowness and a hold
  - pounce: leap, then bite
  - kick: strong knockback
  - fling: launch upward
  - slam: hits everything within 2
  - ram: charge, for armless horned heads
  - flap: buffet, no damage
  - scrabble: ×1.6 speed, ×0.5 damage
  - clamp: blocks, then hits
  - sting: poison, then the limb is spent for 60 s
  - hook: bleed, the Hook Hand implant
  - pacifist: never attacks; +storage
- **Grips (7):** hand (uses tools and weapons), paw, claw, hoof, wing, tentacle, none.
  - Hand is needed for farmer, surgeon, butcher, medic and held ranged weapons.
  - Paw and claw can carry and harvest.
  - The rest carry one stack in the mouth, and only with a head.
- **Projectile kinds (11):** arrow (tipped by `potion`), snowball, egg, small_fireball, large_fireball, wither_skull, llama_spit, shulker_bullet, wind_charge, trident (held), splash_potion (from inventory). Evoker fangs use vanilla `summon_entity`.

### 5.7 New game objects the vocabulary needs
- **Mob effect** `bloodandbones:bleeding`: 0.5 damage every 2 s, with drips and stains. In bloodless mode it is "Leaking", with grey sparks and no drips.
- **Attributes** (S each; each ties the part system back to existing systems):
  - `drag_strength`: `CarcassDrag`'s penalty is multiplied by (1 − x), with x capped at 0.75;
  - `butchery_yield`: hand yields of the knife and cleaver;
  - `blood_upkeep`: implant and minion drain;
  - `necrosis_resistance`: `Necrosis` build rate.
- **Blocks:**
  - `blood_trough` and `charging_cradle` (§6.7);
  - `cooled_crust`: a lava version of frosted ice, placed by vanilla `replace_disk` for lava walking;
  - `temporary_web`.

  All are ordinary blocks, so they move on contraptions (rule 5).
- **Items:**
  - `scraps`, `gland`
  - `carcass_helmet`, `carcass_chestplate`, `carcass_leggings`, `carcass_boots`
  - `brass_sheathing`
  - `empty_soul_canister` and `soul_canister`
  - `dormant_minion`

### 5.8 Stacking, caps, lints
- **Same trait id:**
  - from several sources, counts once at the highest level (`stacking: max`, the default);
  - traits marked `stacking: sum` add their levels up to max_level (wall_climber, groomed_coat, swift from rabbit hide).
- **Different traits stack.**
- **Damage reductions** from traits multiply, with a floor of 0.2 (at most 80% off), applied before armour. Only a full-set bonus may go to 0 (a true immunity).
- **Movement caps** from traits in total: speed +40%, jump strength +0.3.
- **Minion caps:** arm damage 1–10; health 6–150.
- **Lints** (a game test): cooldowns are 0 or at least 20 ticks; no effect in an unsupported context; levels no higher than max_level; no `damage_item`; every referenced trait exists and has lang; every vanilla mob has at least one special organ.
- **Config:** `trait_strength` and `power_drain` multipliers; `disabled_effect_types`.

### 5.9 Who gets traits
- Players, from worn carcass armour; minions, from their build.
- Other mobs that pick up carcass armour get its base stats (armour points, toughness), never its traits.
- Armour stands get nothing.

### 5.10 Starter trait library
These are data (about 120 files), and each uses only the effect types above. Levels are per level.

**Stats (attribute)**
- hardy: max health +2
- swift: speed +5%
- galloper: speed +7% while sprinting
- springy: jump +0.08, safe fall +1
- steady: knockback resistance +0.1
- sturdy: knockback resistance +0.05
- sure_footed: step +0.5 (max 2)
- light_boned: gravity −8%, fall damage −15%
- heavy: speed −5%, knockback resistance +0.15, gravity +10%
- long_reach: both interaction ranges +0.5
- brawler: attack +1
- quick_hands: attack speed +10%, block break speed +10%
- pecking_order: attack speed +10%
- thick_hide: armour +1
- tough: toughness +1
- waterborn: swim speed +20%, oxygen +1, water movement +0.15
- slick: water movement +0.33
- lucky: luck +1
- stealthy: sneaking speed +0.1
- burrower: mining efficiency +2
- hauler: drag strength +0.15
- keen_butcher: butchery yield +10%
- lean: blood upkeep −10%
- long_winded: oxygen +1
- keen_eye: follow range +4
- wide_eyes: alert sense 12
- frail: max health −2
- meek: attack −1
- sluggish: speed −5%

**Damage and immunity**
- fireproof: fire ×(1 − 0.25L)
- blast_padding: explosions ×(1 − 0.2L)
- shell_guard: projectiles ×(1 − 0.15L)
- fall_guard: falls ×(1 − 0.25L)
- frost_guard: freezing ×(1 − 0.25L)
- magic_ward: `#minecraft:witch_resistant_to` ×(1 − 0.1L)
- fire_weak: fire ×(1 + 0.25L)
- bane_weak: ×1.5 against Bane of Arthropods weapons
- sharpshooter: outgoing projectile damage +1
- evasive: 10% × L melee dodge
- undying: lethal save
- gills: breathe water
- venom_proof, hunger_proof, wither_proof: immunity to that effect
- clear_eyed: immune to blindness, darkness and nausea
- insulated: freezing immunity plus the powder_snow flag
- hollow_frame: poison and drowning immunity

**Senses**
- dark_sight: night vision while light is 7 or less
- night_eyes: night vision always
- echo_sense: echolocate 8 + 8L
- tremor_sense: tremor 16
- spectral_sight: see invisible
- alert: when targeted, Speed I for 3 s plus a ping
- blood_scent: reveals mobs under 50% health within 16

**Social**
- ender_mask, ender_calm (endermen never target the minion)
- piglin_kin
- dead_face: kin with `#minecraft:zombies`, provoked window 30 s
- skeleton_kin
- raider_kin
- golem_trust (trustworthy)
- beloved: villagers and golems are friendly, and golems defend it
- hero: Hero of the Village, hidden
- cat_ward: creepers and phantoms flee
- prey: wolves, foxes and cats hunt you
- villagers_flee
- golems_hostile

**Movement**
- wall_climber
- glider
- bouncy
- powder_walker
- silent_steps
- featherfall: Slow Falling once falling more than 3 blocks, not while sneaking
- land_on_feet: safe fall +3
- lava_wader: `replace_disk` lava → cooled_crust under you, plus fireproof 1
- frost_path: `replace_disk` water → frosted_ice
- quick_draw
- trample
- lava_walk (minion)
- rideable (mount)
- stubborn: steady 3 while ridden

**On hit (attack trigger)**
- venomous
- withering
- chilling: Slowness
- searing: ignite 2L s
- grabbing: Slowness III for 1 s
- hungering: Hunger for 10 s
- sticky: Slowness II for 2 s
- webbing: 20%, web for 3 s, cooldown 5 s
- bleeding: bleed 3L s
- flinger: launch up 0.3L
- displacer: 15%, blink_target 8
- mauler: bleed plus +1 damage
- thief: 5%, steal_item

**On hurt**
- barbed: L damage to melee attackers
- ember_skin: melee attackers burn for 2 s
- toxic_skin: melee attackers get Poison for 3 s
- ink_cloud
- bolt: Speed II for 2 s
- flight_response: Speed II for 3 s, cooldown 30 s
- second_wind: below 30%, Speed II and Regeneration I for 5 s, cooldown 60 s
- adrenaline: below 40%, Strength I and Speed I for 6 s, cooldown 60 s
- roll_up: below 30%, Resistance III and Slowness V for 4 s
- play_dead: below 25%, Regeneration II and hostiles lose you for 5 s
- turtle_up: while sneaking, damage ×0.6 and speed −60%
- mirror: Invisibility for 3 s and the attacker blinded for 3 s
- rift: 30% chance of a random blink of 4
- blink: 50% chance of a random blink of 16
- fly_swat: hit from behind, the attacker is pushed back 0.8

**Actives (activate)**
- leap, dash, charge, warp
- fireball: 3 small fireballs
- great_fireball
- wind_burst: up 1.2, fall immunity until landing
- web_shot: hitscan that webs
- spit, shulker_bolt
- sonic_boom, guardian_beam
- fangs
- blast, self_destruct
- snowball_volley
- tongue: pull
- cleanse
- honey: Regeneration II and cure poison
- roar: push away within 4, plus Slowness

**Produce**
- milk_udder, stew_udder, egg_layer, wool_regrowth (the captured colour), silk_gland, ink_gland, glow_gland, honey_stomach, scute_shed
- morning_gift: the `minecraft:gameplay/cat_morning_gift` loot table at dawn

**Diet**
- cud_chewer: graze grass for 1 hunger
- four_chambers: plant foods +50% saturation
- iron_gut: rotten flesh and raw meat safe; minion refuels 25 mB each
- omnivore: any food +1 saturation
- seed_eater
- bamboo_gut
- mycelial_gut
- forager
- cookie_poison (drawback)

**Aura and pack**
- pack_hunter: +15% per ally, up to 3
- item_magnet: radius 3 + 2L
- horde_call: rally
- purr: Regeneration I for allies within 4 every 10 s
- toxin_puff, glow_aura, wither_aura
- fatigue_aura: Mining Fatigue II on hostiles
- dolphin_kick: Dolphin's Grace

**Drawbacks**
- sun_cursed: ignite 8 s in daylight under open sky when not wet; the set's own helmet does not protect
- water_hurts: 1 damage a second in water or rain
- dry_out: Slowness I and armour −2 after N seconds dry
- heat_hurts: temperature above 1.0 or the Nether
- light_hurts: Weakness in light 12 or more under sky
- inverted_healing
- zombify
- warped_dread: warped fungus within 7
- cat_terror: within 8 of a cat or ocelot, Slowness II and Weakness
- screamer: every minute, a 20% chance of a goat scream and 3 s of Glowing
- hay_burner: Hunger I while sprinting
- thirst: Hunger I in daylight under sky

**Minion-only**
- beast_of_burden: hauler 1; takes a chest for +18 slots
- saddlebags: +15 storage
- relentless: follow range +8
- loyal: teleports to its maker beyond 24 while following
- leaky: organic drain ×1.5
- hump: reservoir ×2 and 2 seats
- repair_with_iron: +25 HP per ingot
- marrow: drain ×0.8

---

## 6. Minion rules

### 6.1 What changes from today
**Replaced:**
- `MinionFrame.Frame(Body, head)` and the `FRAME` component;
- `MinionJob.of(body, wearer)` (jobs from arm implants and eyes);
- waking with a soul blood bucket;
- the humanoid `MinionRenderer`.

**Kept:**
- `MinionEntity` as a persistent `PathfinderMob` with maker, home and inventory;
- the Farm, Collect, Deposit, Melee, FollowMaker and StayNearHome goals, ported into jobs;
- the Surgery Table and its ASSEMBLY attachment;
- `CarcassModels.drawBone` and `WoundCaps`.

Saved old minions get a one-time conversion that drops their parts. `MinionTests` is rewritten.

### 6.2 Building on the Surgery Table (Assembly Frame)
1. **The frame is a torso piece of at least 30% freshness.** Two ways in:
   - **Light torsos** (mass at most `LIGHT_MASS` 0.13: chicken, rabbit, bat, cod, bee...) go on as items, as now.
   - **Heavy torsos:** drag the carcass or severed torso into the table's work zone (the machines' rule: 0.75 blocks past each edge, 2.5 up) and right-click the table with an empty hand. The nearest one is claimed:
     - its Sable sub-levels are removed;
     - the table's block entity stores the torso's PieceRef;
     - every bone still attached becomes a part already fitted. So a whole cow dragged onto the table is a cow frame with its own head and legs.

   The same claim works for heavy severed parts, such as a ravager leg, as a fitted part. The same work-zone rule lets the **Surgical Rig** table take organs out of a heavy carcass lying over it; today `Surgery.harvest` only reads `table.item()`.
2. **Fitting.** Right-click with a piece, or use a Deployer's stand-in or a funnel:
   - HEAD or NECK → the head socket;
   - ARM or LEG → the free limb socket nearest the clicked point (arm pieces prefer high sockets, leg pieces low ones);
   - TAIL → the tail socket;
   - TORSO_EXT → a torso socket, which adds that piece's own sockets;
   - an organ → the one organ slot;
   - a saddle → the saddle slot;
   - a chest → the chest slot, if a trait allows it.

   A Cleaver click takes back the last part fitted. Implants still fit limb sockets through their ImplantSpec: the Hook Hand is strike style hook, the Peg Leg walks at 0.9.
3. **Deglove pipeline** (brief § Cybernetics: never-degloved limbs feed flesh, degloved limbs feed brass):
   - an **organic** frame accepts only unskinned pieces;
   - a **cybernetic** frame accepts only skinned pieces;
   - pieces of a mob that has no hide (skeletons, spiders, golems...) count as both.
4. **Making it cybernetic.** Apply a Brass Sheathing (4 brass sheets + 1 precision mechanism, one click, or a Deployer) to a torso with no hide left.
5. **Waking:**
   - organic: a Blood bucket (1000 mB into its reservoir; the bucket comes back);
   - cybernetic: a full Soul Canister.

   **No head and no heart are required.** A frame with no legs uses its torso's own movement, or crawls.
6. **Taking apart.** A Cleaver on a powered-down minion lying in an Assembly Frame table's work zone returns every part at the freshness it went in with. Parts never rot while assembled.
7. **Cap.** Server config `max_minions_per_player` = −1 (no cap).

### 6.3 The build and `MinionStats`
`MinionStats.of(build, resolvedData)` is a pure function, unit-testable without a world. It returns: health, armour, knockback resistance, hitbox, carry slots, reservoir or canisters, movement (mode, speed, step, jump, climb, swim, lava_walk, rideable, flight), the strikes list, ranged attacks, job options, senses and the `ActiveTraits` list. The server and client share one geometry helper for sockets, lift and hitbox, so they always agree.

### 6.4 What each part decides

**Torso: size and health**
- **Health** = source base × `health_factor` × the share of that mob's torso bones present (by volume), × 0.5 for a baby, clamped to 6–`minion_max_health` (150).
  - The base is the family's `health`, else the mob's MAX_HEALTH.
  - A foreign torso extension adds 25% of its own mob's torso health.
  - Examples: cow 15, rabbit 6, spider 16 (abdomen alone 13), zombie 20, horse 22, enderman 40, iron golem 50, ravager 50, elder guardian 48, warden 150 (capped).
- **Armour** from torso traits.
- **Knockback resistance** = clamp(source rig weight / 6, 0, 0.9).
- **Size:** drawn at native scale, never rescaled. The hitbox is the union of the drawn boxes at rest (§6.10).
- **Carry slots** = clamp(round(18 × torso volume in blocks³), 3, 27), plus storage traits (up to 54). Cow 9, spider 5, horse 13, squid 10, zombie 3.
- **Organic reservoir** = 250 + 1000 × torso volume mB. Cow about 780, zombie about 345, horse about 960, chicken about 320. The camel's Hump doubles it.
- **Cybernetic canisters:** 1, or 2 when the torso volume is over 1 block³.
- **Torso traits:** milkable, wool, shell, hover and so on.
- **The torso's own movement:** hover, swim, hop, fly, float, roll or slither works with no legs.

**Head: behaviour**
- **Job options** (1–3) from data. The maker cycles them with a crouch and an empty-hand click on the woken minion.
- Villager heads offer SURGEON plus their profession's job:
  - farmer → farmer
  - fisherman → fisher
  - butcher → butcher
  - cleric → medic
  - shepherd → herder
  - fletcher → sentry
  - leatherworker → hauler
  - armorer, weaponsmith, toolsmith → guard
  - others → courier
  - a nitwit offers only companion
- Pillager heads offer surgeon and sentry.
- **Follow range** from FOLLOW_RANGE. **Disposition** and **senses** from data.
- **Bite:** used when it has no arms, and as an extra attack. Damage 1 + 0.25 × the head mob's attack damage, or the data value. Horned heads (goat, hoglin, zoglin, ravager) ram.
- **Eyes taken out:** a head whose two eyes were taken out at the Surgical Rig is blind. It loses the jobs that need sight (farmer, sentry, surgeon, hunter, fisher) and has follow range 4, unless an echolocate or tremor sense replaces sight.
- **No head: Mindless.** Companion only, follow range 8, never picks targets.
- **Several heads** (the wither): the first sets the job; each extra head adds its senses and its bite as an extra ranged "mouth".

**Arms: attack type**
- Each ARM piece brings its strike style, its grip and (for wings) its lift.
- **Melee damage per arm** = clamp(1 + 0.5 × the source's ATTACK_DAMAGE, 1, 10) × the style multiplier. Zombie 2.5, enderman 4.5, iron golem 8.5, warden 10.
- **Strikes alternate** between arm pieces: a zombie arm and a polar bear arm grab, then maul.
- **Attack speed** +15% for each arm beyond 2, up to +60%.
- **Ranged:**
  - **Held weapons** (hand grip) use vanilla goals with ammo from the inventory: `RangedBowAttackGoal` for bows, `RangedCrossbowAttackGoal` for crossbows, a thrown trident, splash potions for the witch.
  - **Innate projectiles** from traits cost power per shot, not ammo: blaze fireballs, llama spit, snow golem snowballs, skeleton bone throws of 2 damage with no bow.
  - A ranged fighter keeps 6–12 blocks away with line of sight, and switches to melee arms under 4 blocks.
- **Pair arms** (villager): pacifist, a hand grip for two, +9 storage.

**Legs: movement**
- **Mode:** the one most fitted LEG pieces share. A tie goes to the slower mode.
- **Speed** = mean of the fitted legs' speeds × min(1, fitted legs / max(2, the frame's own leg sockets)).
  - A cow on four rabbit legs: (0.3 + 0.3 + 0.35 + 0.35) / 4 = 0.325, against 0.2 on cow legs.
  - Only the two haunches on a cow: 0.175.
  - Legs, not sockets, decide it: a zombie arm in a hind socket leaves that socket counting as empty for walking.
- **Capabilities** apply when at least half the fitted legs have them:
  - climb (spider legs climb);
  - swim;
  - lava_walk;
  - rideable: needs at least 2 rideable legs, a saddle, and a torso weight share of at least 0.4, so a rabbit torso on horse legs cannot carry you.
- **Steering:** a pig head steers with a carrot on a stick; strider legs with a warped fungus on a stick; otherwise the saddle.
- **Step height** is the highest leg's. **Jump** is the mean.
- **Flight:** wing ARMs add lift. It flies if the total lift is at least the torso volume, otherwise it only slow-falls.
  - Phantom wings (0.4 each) lift a cow (0.53): it flies.
  - Chicken wings (0.02 each) never do.
- **With the torso's own movement:** a hovering or flying torso wins and its legs dangle. A swimming torso plus walking legs is amphibious.

**Tail:** one passive (fly swat, swim +30%, mood tail, steady).

**Organ:** one special. Its minion traits apply. Activate effects are fired by the AI when a target is in range.
- The creeper's sac self-destructs, and the minion then powers down.
- The chicken's egg gland lays eggs.
- produce effects do nothing on a cybernetic minion.

### 6.5 Examples
- **Cow torso on four rabbit legs, cow head:** 15 HP, 9 slots, about 780 mB, hops at 0.325, herder, bash bite 1. It looks exactly as wrong as the brief wants.
- **Spider thorax with 8 zombie arms, villager head:** a surgeon. 13 HP (thorax plus a foreign abdomen would add more). Eight grabbing hands at +60% attack speed. No legs, so it crawls at 0.05. Pacifist, because it is a surgeon.
- **Blaze torso with a villager head and 2 horse legs:** hovers (the torso wins), the legs dangle, surgeon job, fireball organ if fitted.
- **Ravager torso claimed whole from the table:** 50 HP, its own legs rideable with a saddle, roar head. The brief's "ravager-torso creature".

### 6.6 Organic and cybernetic: each has what the other lacks, neither is stronger

| | Organic (unskinned pieces, blood) | Cybernetic (skinned pieces plus Brass Sheathing, soul blood) |
|---|---|---|
| Only this kind | Regenerates 1 HP every 5 s while blood is above 10% (5 mB per HP). Produce organs work (milk, eggs, wool, ink, honey). Keeps the hide trait of each different unskinned mob among its pieces, up to 3. Forage organs top it up to 50%. | One brass module socket, running a module from `cyber/Modules` at its baseline with no throttle: Magnet Coil (items within 6), Analytical Lens (targets through walls; reads a machine's stress), Rotational Coupler (standing next to a shaft, it drives it at 32 RPM and 256 su), Piston Ram, Gyroscopic Stabilizer, Barometric Vent. A Create Filter or Attribute Filter slot (`FilterItemStack#test`) limits what it picks up, attacks or farms. Immune to poison, wither, hunger and drowning. |
| Weakness | Suffers its parts' weaknesses (sun burn, poison, fire, drowning). Tied to reachable troughs. | Never heals itself: a Brass Sheet repairs 10 HP, by hand or by a Deployer (its stand-in drives `mobInteract`), and a cradle stocked with sheets repairs 1 HP a second while docked. Needs canister logistics. No produce and no hide traits. |
| Drain | the base rate | ×0.25 of the base, from canisters |

No flat HP or armour bonus for either kind.

### 6.7 Power
**Drain depends on activity, never on wall-clock time.** It happens only while the minion ticks, so an unloaded chunk costs nothing and a weekend away changes nothing.

| Activity | Organic mB per minute |
|---|---|
| Idle while awake | 3 |
| Moving | 15 (×2 flying, hovering or floating) |
| Working a job | 25 |
| Fighting | 40 |
| Organ active, innate shot | the trait's `cost_mb` (3–100) |

Cybernetic drain is ×0.25 of this. `blood_upkeep` and power traits scale both.

**Organic: the Blood Trough.** Below 25%, the minion paths to the nearest Blood Trough within `trough_search_radius` (48).
- It must reach the trough with its own navigation: no teleporting. If the path fails, it tries the next trough.
- It drinks 100 mB a second, then returns to its job.
- The trough is a new block with a 4,000 mB tank that accepts only `#c:blood`, filled by pipes and spouts (`Capabilities.FluidHandler.BLOCK`).
- Troughs register in a per-level set on onLoad and setRemoved, so the search is cheap.
- It moves on contraptions with its contents in its block entity data, and is not a drinking point while assembled (rule 5).
- **Hand feeding:** a blood bucket or bottle, or the maker crouch-using an empty hand to give 250 mB from their worn backtank.

**Cybernetic: the Soul Canister and the Charging Cradle.**
- **Soul Canister:** `empty_soul_canister` plus 1000 mB of soul blood through a plain `create:filling` recipe (a Spout) makes a `soul_canister`. `create:emptying` (an Item Drain) reverses it. So JEI shows it and stock Create automates it. The minion holds its current charge internally; partial canisters never exist as items.
- **Charging Cradle:** a new kinetic block, with low stress, at least 16 RPM, and faster swaps at higher RPM (1 s at 64 RPM).
  - It holds 8 full and 8 empty canisters, plus brass sheets.
  - Its item handler (`Capabilities.ItemHandler.BLOCK`) works with funnels, hoppers, chutes and belts.
  - It is a Mechanical Arm interaction point through `CreateRegistries.ARM_INTERACTION_POINT_TYPE` (✓).
  - It swaps a canister into any cybernetic minion within 2 blocks that is below 25% or powered down, and outputs the empty one for re-spouting.
  - It moves on contraptions with its inventory and is inert while assembled.
- The shared `SeekRechargeGoal` serves both kinds through a `RechargePoint` interface.

### 6.8 Powered down, dormant, combat death (never destroyed by neglect)
- **At 0 power:**
  - no AI and no drain;
  - persistence is forced;
  - it lies on its side: the renderer rolls the body 90° about the torso centre with limbs still, and the hitbox is the lying box.
- **While powered down:**
  - `isInvulnerableTo` is true for every source except a Player and `#minecraft:bypasses_invulnerability` (/kill, the void);
  - `canBeSeenAsEnemy` is false, so mobs ignore it;
  - it can be pushed, so it cannot be used as an indestructible wall.
- **Waking** happens on any power: blood, a canister, or a trough or cradle within 2 blocks. So a guard that collapses beside its trough recovers by itself.
- **Dormant Minion.** The maker crouch-holds an empty hand for 3 s on a powered-down minion. It folds into a `dormant_minion` item keeping its build, inventory, name and maker. Used on a block, it unfolds, powered down.
- **Combat death,** config `minion_death`:
  - `collapse` (default; confirm with the user): a lethal hit while powered leaves it at 1 HP with 0 power, powered down;
  - `scatter`: it falls apart into its parts as carcass pieces at 50% freshness, plus its organ and inventory;
  - `destroy`: it drops its parts and inventory.

### 6.9 Jobs (17; goal packages in code, offered by heads through data)

| Job | What it does | Needs | Cost |
|---|---|---|---|
| companion | Follows and defends its maker | — | S (exists) |
| bodyguard | Attacks what attacks its maker, or what the maker attacks | — | S (today's FIGHTER) |
| guard | Patrols 16 blocks around home and attacks `Enemy` mobs | — | S |
| sentry | Never moves; turns to shoot with ranged arms or its organ | a ranged attack | S |
| courier | Collects within 10 of home and deposits into the nearest item handler within 6 | — | S (exists) |
| scavenger | Allay-style: fetches items matching the one in its hand, from 32 blocks, and brings them to its maker | — | S |
| farmer | Reaps and replants | hand or paw, sight | S (exists) |
| herder | Keeps filtered animals within 8 of home | — | M |
| fisher | By water, rolls `minecraft:gameplay/fishing` every 30–60 s | a rod in hand or a fish head; sight | S |
| hunter | Kills filtered passive mobs within 12 of home. With a Meat Hook in hand, it leaves intact carcasses. | sight | M |
| hauler | Drags carcasses within 24 to the nearest Shackle Hook or Bleeding Rack using `CarcassDrag`; drag_strength counts | — | M |
| butcher | Hand-yield butchery (`CarcassButchery`) on carcasses within 6 of home | hand plus a Cleaver or Flensing Knife | M |
| surgeon | Stays within 3 of its home Surgery Table and exposes `Surgery.surgeonPresent(table)`, which the amputation ritual requires (brief § Self-augmentation). Heals a patient 1 HP per 5 s. Leaves the ragged stump the brief describes. Refitting parts and fitting crude prosthetics never needs a surgeon, so the safety floor is always reachable. | hand, sight | M |
| medic | Throws splash healing potions from its inventory at hurt allies | hand | S |
| barterer | Takes gold from the adjacent container, rolls `minecraft:gameplay/piglin_bartering`, stores the result | — | S |
| digger | Sniffs grass, moss and dirt around home and rolls `minecraft:gameplay/sniffer_digging` | — | S |
| sapper | Walks to a marked block or target and detonates through its organ, then powers down; never destroyed | a detonate organ | S |

### 6.10 Drawing the jank body
`StitchedMinionRenderer` (a plain EntityRenderer) replaces the humanoid renderer.
- **Parts.** Every part is drawn with `CarcassModels.drawBone` from its own mob's client rig (`RigManager.clientRig(entity, baby)`), with its own texture, coats and skinned look. It is drawn at its own scale (baby parts small, horse parts ×1.1), never normalised.
- **Placement.** Each piece's pivot sits on its socket pivot in its own rest rotation. A spider leg sticks out sideways from a cow. No retargeting, no IK, no blending.
- **Ground.** The body is lifted until no drawn box is below y=0. The pieces that touch the ground carry it; shorter legs dangle. A hovering torso floats 0.5 up and bobs.
- **Gait.** Deterministic sine swings:
  - legs swing on cos(limbSwing × 0.6662 + phase) × amplitude × limbSwingAmount, with the gait's frequency and amplitude, phase by side and index, and scaled inversely to leg length. Tiny rabbit haunches under a cow scurry while the body glides.
  - tentacles ripple; spider legs move in four phase groups;
  - wings flap when airborne; tails wag;
  - arms use the attack animation, alternating per strike;
  - the head follows yaw and pitch, clamped to 45° and 30°.
- **Seams:**
  - organic: a stitched seam ring where each part meets the body, and drips now and then;
  - cybernetic: brass collars;
  - bloodless mode: rivets and the existing pale or clean textures, no drips.
  - Empty sockets show WoundCaps, hidden in bloodless mode.
- **Hitbox.** The union of the drawn boxes at rest (`getDefaultDimensions` then `refreshDimensions`). It is set only when woken or rebuilt on the table, never while the minion stands somewhere too small.
- **Sync.** The build reaches clients through `IEntityWithComplexSpawn` and a `MinionBuildPayload` on rebuild.
- **"Jank but not buggy"** comes from nothing being simulated, so nothing jitters. Tests check that feet never go below the ground and that the hitbox contains every drawn box. The look is judged on screen.

### 6.11 Minion config
- `max_minions_per_player` −1
- `minion_max_health` 150
- `minion_max_width` 3.0, `minion_max_height` 4.0
- `minion_max_torso_blocks` 3.0
- `minion_block_damage` false (on top of mobGriefing: explosions, trample, ghast fireballs)
- `minion_death` collapse
- `boss_parts` true
- `trough_search_radius` 48
- `power_drain` 1.0

---

## 7. Armour rules

### 7.1 Ingredients (each carries `bloodandbones:source`)
- **Scraps:** one item, `bloodandbones:scraps` {entity, part, baby}, shown as, for example, "Cow Leg Scraps". Four icons by part, tinted with the family colour.
  - **Only the Mangler makes them**, per the brief's Mangler row. They are added in `CarcassMachineBlockEntity.mangle` through the existing `CarcassButchery.capturing` sink, alongside the junk and the mob's vanilla drops. The Mangler already tears limbs off one at a time, so each piece's scraps keep their part.
  - **Count per piece** = max(1, round(volume in blocks³ × the material's `density`, 24 by default)), fractions rolled as chance. A cow gives about 2 head, 13 torso and 4 leg scraps; a chicken about 1 head, 2 torso, 2 leg and 2 arm (wing).
  - **Degloved** input gives ×1.5 ("clean"). An unskinned piece's hide is destroyed, so Deglover then Mangler is the efficient line: hide to armour modifiers, flesh to scraps. This matches the brief's "output varies by which".
  - **Rotten** input (freshness under 0.3) gives ×0.5, reusing the rot rule. Babies scale by weight, as yields do today.
  - Counts come from the rig and the group material only, never from per-mob butchery tables.
- **Hides:** raw_hide and every other `"kind": "hide"` yield of `CarcassButchery.skin`, stamped with the skinned mob.
  - Unstamped vanilla hides resolve through the `hide_sources` data map: leather → cow, rabbit hide → rabbit, feather → chicken, the scutes, phantom membrane, shulker shell. So hides that mobs drop normally work too.
  - An unstamped legacy raw_hide fits as a plain covering with no trait.
  - Mobs with no hide in their butchery table (rotting, skeletal, chitin, golems, most fish) have none.
- **Organs:** the existing heart, lungs, stomach and eye items, stamped with their source, plus the new `bloodandbones:gland` {organ id} for specials. A few vanilla items are organs too: rabbit's foot, ink sac, glow ink sac, spider eye.
  - `Surgery.harvest` reads the group and mob organ lists by the piece's slot instead of its fixed body/head switch.
  - Bloodless mobs now give their core instead of nothing; `PatientTests`' "none from a skeleton" becomes "marrow from a skeleton".
  - The existing `organs_taken` trait keeps counting.

### 7.2 Making a piece
The custom recipe `bloodandbones:carcass_armour` extends `ShapedRecipe`, so JEI shows it, and overrides `matches` and `assemble`. It works in a crafting table and in Create's Mechanical Crafters (`RecipeGridHandler` calls `Recipe#assemble`; the class was checked in the Create 6.0.11 jar).

| Piece | Pattern | Cells |
|---|---|---|
| Helmet | `HHH / H.H` | 5 head scraps of one mob |
| Chestplate | `S.S / TTT / TTT` | 6 torso scraps of one mob (the base) + 2 shoulder cells: arm scraps of any one mob, or the base mob's own torso scraps if it has no arm bones |
| Leggings | `PPP / L.L / L.L` | 4 leg scraps of one mob (the base) + 3 hip cells: tail scraps of any one mob, or more of the base mob's leg scraps |
| Boots | `L.L / L.L` | 4 leg scraps of one mob |

- **Stand-in:** a mob with no bone of the needed class uses its torso scraps there. Blaze, slime, bee, cod, ghast and squid can make all four pieces.
- **Mixing inside a cell group** (two mobs in the helmet's five cells) gives no output.
- **Output:** a `carcass_*` item named "<Mob> <Material> <Piece>", for example "Spider Chitin Leggings", with the `carcass_armour` component, tier 0.
  - The armour material `bloodandbones:carcass` has zero defence; all numbers come from data.
  - `MAX_DAMAGE` = material durability × vanilla's per-slot 11/16/15/13 × the tier multiplier, baked when the piece is made.
- **Repair** on an anvil with scraps of the same base mob.

### 7.3 Fitting a hide, an organ or a tier
One shapeless special recipe, `bloodandbones:carcass_armour_fitting`: a piece plus exactly one kind of modifier.
- **Hide:** 1 for a helmet or boots, 2 for leggings, 3 for a chestplate, all from one mob. It replaces any earlier hide, which comes back through `getRemainingItems`. Durability-changing hides re-bake `MAX_DAMAGE`.
- **Organ:** one per piece, limited by the organ file's `armour_pieces` (eyes go in helmets; heart and lungs in chestplates; stomach in chestplates or leggings; rabbit's foot in leggings or boots; specials say which). The old organ comes back.
- **Tier**, in order:
  1. `blood_steel_ingot` (the brief's "blood iron");
  2. `blood_diamond`, which needs tier 1;
  3. `soul_netherite_ingot`, which needs tier 2.

These work in a crafting grid and in Mechanical Crafters. The Deployer route (a piece on a Depot, with a Deployer holding the modifier) is a later slice. Create's `RecipeApplier` rebuilds outputs from static JSON and drops components, so it needs `DeployerRecipeSearchEvent` (✓ in the jar) to supply a one-off recipe per search, after checking how the Deployer caches recipes.

### 7.4 What each piece gets
- **Helmet:** the head part's armour traits (mind and senses).
- **Chestplate:** the torso part's traits, plus the shoulder mob's arm traits (strike riders, reach, glide from phantom wings).
- **Leggings:** the leg part's `leggings` traits, plus the hip mob's tail traits.
- **Boots:** the leg part's `boots` traits.
- **Every piece:** base stats from the base mob's material and quirk, the tier bonus, the hide traits and the organ traits.

The same trait id worn twice counts once, at its highest level, unless it `sums`. So rabbit boots with spider leggings gives two different leg traits: mixing is rewarded.

### 7.5 Base stats and tiers
- **Tier 0:** the material table in §3.6.
- **Tier bonuses** (armour per piece, helmet/chest/legs/boots). Each replaces the previous tier's bonus.

| Tier | Material | Armour bonus | Toughness per piece | KB per piece | Durability × | Other |
|---|---|---|---|---|---|---|
| T1 | Blood Steel ingot | +1/+2/+2/+1 (+6) | +0.5 | 0 | 1.5 | |
| T2 | Blood Diamond | +2/+3/+3/+2 (+10) | +2 | 0 | 2.5 | |
| T3 | Soul Netherite ingot | +2/+4/+3/+2 (+11) | +3 | +0.1 | 3.3 | `DataComponents.FIRE_RESISTANT` |

A hide_plate set goes 9 → 15 → 19 → 20 armour with +12 toughness at T3, which is netherite parity. The material quirks, part traits, hides and organs keep every set different.

- **Computation.** Armour, toughness, knockback resistance, quirks and unconditional attribute traits are added in `ItemAttributeModifierEvent` (✓) for the piece's slot group, live from data. Enchantability is the material's plus the hide's, through `IItemExtension#getEnchantmentValue` (✓).

### 7.6 Active organs
- **Trigger:** the Organ Ability key (§5.2) fires the next ready active facet among the worn pieces.
- **Cooldown:** per piece, through `ItemCooldowns` (the icon shows it) and a `cooldown_until` field in the component.
- **Cost:** 10–100 mB of normal blood from the worn backtank, strapped or not. If the tank is empty, or holds soul blood, it costs 3 hunger points instead. Bloodless mode calls it "essence".

### 7.7 Full-set bonus (purity)
- **When it applies:** all four pieces are carcass armour; every body, shoulder, hip, hide and organ present comes from the same entity type (stand-in torso scraps count as that mob); missing hides and organs are fine.
- **What applies:** the mob's `full_set`, else its overlays' (highest priority), else its family's, else its archetype's. So every mob, modded ones included, has a set.
- **Always paired:** the bonus and the drawback always come together.
- **Detection:** `LivingEquipmentChangeEvent` (✓), cached in `ActiveTraits`.
- **Mixing** gives no bonus and no penalty.
- **Separate from self-augmentation:** the brief's flesh-graft and brass-module set bonus (`cyber/SetBonus`) is separate, and both can apply.

### 7.8 The Blood Backtank conflict: strap-on
`FluidBacktankItem.wornBy` reads only the chest slot, and the backtank is itself a chestplate carrying its tier's armour. So a carcass chestplate would unplug every organic prosthetic, cybernetic and active organ.

The fix:
- **Strapping.** A shapeless recipe, carcass chestplate + Fluid Backtank, copies the tank's tier into `bloodandbones:strapped_tank` and its fluid into the existing `bloodandbones:fluid` component on the chestplate.
- **`wornBy`** returns the chest stack when it is a `FluidBacktankItem` or carries `strapped_tank`.
- **Capacity** is read from the item or from the component, through one helper.
- **Existing code works unchanged:** `fluid()` and `setFluid()` already work on any stack.
- **Spouts and Item Drains:** a `FluidHandlerItemStack` capability is registered for the carcass chestplate, active only while strapped.
- **Armour.** Chest armour and toughness = the higher of the chestplate's and the tank tier's.
- **Unstrapping.** Crafting the chestplate alone gives the tank back with its fluid.
- **Drawing.** `FluidBacktankLayer` draws the tank on the back as now.

This changes tested backtank code, so it ships in the same slice as the chestplate, and `BacktankTests` are extended. It is listed for the user to confirm.

### 7.9 Look, tooltips, JEI
- **Texture:** one texture set per material look (15), through `IItemExtension#getArmorTexture` (✓).
- **Hide:** a tinted second layer through `IClientItemExtensions#getArmorLayerTintColor` (✓).
- **Tier:** trim-like details (rivets, studs, teal veins).
- **Organ:** an emissive pip on the icon.
- **Tooltips:** one line per facet, with source and level, for example "Springy II — Rabbit hind leg", "Creeper hide: —", "Blaze Core: Fireball [G] 50 mB"; the tier; and "Full set (Cow): Herd Beast / Placid" when worn.
- **Carcass pieces show both futures**, for example "As a minion leg: hops, Springy II · Mangled: 1 leg scrap → Springy II".
- **JEI** gets a "Body Parts" page per mob beside the existing Butchery pages.

### 7.10 Bloodless wording (presentation only; same mechanics; new `bloodless.` lang keys, with `BloodlessWords.soften` for the rest)

| Normal | Bloodless |
|---|---|
| Carcass armour ("Spider Chitin Leggings") | Plated armour ("Spider Plated Leggings") |
| Scraps | Salvage |
| Hide | Covering |
| Heart / Lungs / Stomach / Eye | Pump / Bellows / Hopper / Lens |
| Gland, sac, organ | Core (each organ file's own `bloodless_name`: Powder Sac → Charge Cell, Rumen → Fermenter, Egg Gland → Egg Dispenser) |
| Fitting a hide / an organ | Fitting a covering / Installing a core |
| Blood cost | Essence cost |
| Minion | Construct |
| Blood Trough | Essence Trough |
| Powered down | Dormant |
| Stitches | Rivets |
| Bleeding | Leaking |
| Rot Gut, Bloodlust | Iron Gut, Overdrive |

Squelch sounds swap to clanks. Armour layers swap to clean plating through `getArmorTexture` on the client. Hit and drip particles are already gated.

---

## 8. Sample traits

The brief deliberately defers "the per-mob organ list and what each ability does ... until the base system works". So the vocabulary and data format are built now, and these abilities are the authoring direction for slice 9, not a lock-in.

### 8.1 Detailed samples
Legend: **M** = the part fitted to a minion. **A** = the armour made from that part's scraps (head → helmet, torso → chest, arm → shoulders, leg → leggings and boots, tail → hips).

**COW** (quadruped, grazer)
- **Torso**
  - M: 15 HP, 9 slots (+18 with a chest), about 780 mB, knockback resistance 0.13, beast_of_burden. Signature Milk Udder: an organic cow fills an empty bucket from its inventory every 5 minutes for 50 mB.
  - A: barrel_chest (knockback resistance +0.1).
- **Head**
  - M: herder, courier or companion; docile; bite 1 with knockback 0.6.
  - A: cud_chewer (crouch-use grass bare-handed to eat it for 1 hunger; the grass turns to dirt).
- **Legs**
  - M: walk 0.2, amble, sure_footed 1.
  - A: leggings sturdy (knockback resistance +0.05); boots hooves (movement efficiency +0.35: soul sand and honey don't slow you).
- **Hide** (raw_hide, or unstamped leather): thick_hide 1.
- **Organs**
  - Heart: hardy 1.
  - Stomach: four_chambers.
  - Special: Rumen. M: forager (wheat, hay or grass for 100 mB each, up to 50%). A (chest or legs): cleanse (activate: clear harmful effects, 50 mB, 120 s).
- **Set:** Herd Beast (hauler 3, so dragging carcasses is 45% easier; hardy 2) / Placid (meek 2).

**SPIDER** (arthropod, arachnid, arthropod overlay)
- **Frame:** body1 plus body0. 16 HP, 5 slots.
- **Head**
  - M: hunter or guard; nocturnal; dark_sight; bite 1.5.
  - A (signature): dark_sight, plus reveal hostiles within 8 through walls in the dark.
- **Torso**
  - A: shell_guard 1.
- **Legs**
  - M: climb 0.3, skitter. The minion climbs walls when at least half its legs are spider legs.
  - A: wall_climber (one piece clings, two climb).
- **Organ:** Spinneret. M: web_shot (range 10, 8 s). A: webbing on hit (20%, 5 s), or activate web shot (25 mB, 10 s).
- **Eyes:** spider eyes.
- **Set:** Brood (wall_climber 2, venom_proof, fall_guard 4) / fire_weak 2 and bane_weak.

**CREEPER** (quadruped, volatile, bloodless mob)
- **Head**
  - M: sapper or guard; silent_steps.
  - A: Hiss (visibility ×0.5 to mobs, as the vanilla creeper head).
- **Torso**
  - M: 20 HP; blast_padding 4, so it survives its own organ.
  - A: blast_padding 2.
- **Legs**
  - M: walk 0.25, silent.
  - A: leggings stealthy 1; boots silent_steps.
- **Hide:** none.
- **Organ:** Powder Sac (+1–2 gunpowder on extraction).
  - M: detonate at power 2 (4 from a charged creeper, a variant) on reaching its target; no block damage unless mobGriefing and config allow; then it powers down, never destroyed.
  - A (chest): blast (activate below 50% health: a radius-3 burst that spares you, 50 mB, 60 s).
- **Set:** Walking Bomb (explosion immunity; creepers ignore you) / Cat Terror.

**ENDERMAN** (biped, humanoid, ender)
- **Head**
  - M: courier or scavenger; ender_calm.
  - A: ender_mask.
- **Torso**
  - M: 40 HP, water_hurts, blinks when stuck.
  - A: 30% chance to blink away when a projectile hits (10 s).
- **Arms**
  - M: punch 4.5 with reach +1, grip hand, carries blocks.
  - A (shoulders): long_reach 2.
- **Legs**
  - M: walk 0.3, step 1.0. Its 30 px legs lift the torso about 2 blocks: towering jank.
  - A: sure_footed 1.
- **Organ:** Ender Gland. M: to_owner teleport beyond 24. A: warp (activate: 8 blocks where you look, 50 mB, 6 s).
- **Set:** Voidwalker (blink cooldowns halved; endermen neutral) / water_hurts.

**BLAZE** (floater, elemental, nether, bloodless mob; self-contained)
- **As a frame**
  - M: 20 HP, hovers 3 up with no legs, fireproof 4, water_hurts; the blob layout lets it take a head and limbs.
- **As a head** on another torso
  - M: sentry or guard; its bite sets targets alight (searing).
- **Armour:** stand-in scraps make all four pieces. Helmet Ember Crown (fireproof 1, ember_skin); the others fireproof 1.
- **Organ:** Blaze Core (+blaze powder on extraction).
  - M: fireball (3 small fireballs, 5 s, 3 mB each).
  - A (chest): fireball (activate, 50 mB, 8 s) and burning time −50%.
- **Set:** Inferno (fire and lava immunity) / water_hurts.

**RABBIT** (quadruped, small_prey)
- **Head**
  - M: courier or companion; skittish. The killer bunny variant: bodyguard or guard, berserk, bite 8.
  - A: alert.
- **Torso**
  - M: 6 HP, 3 slots.
  - A: light_boned 1.
- **Front legs:** walk 0.3.
- **Hind legs** (signature)
  - M: hop 0.35 (the brief's fast legs), springy 2.
  - A: leggings springy 2; boots fall_guard 2 and swift 1.
- **Hide** (rabbit hide): swift 1, which sums across pieces.
- **Organ:** Rabbit's Foot (from a haunch). M: evasive 1, lucky 1. A (legs or boots): lucky 2, leap.
- **Set:** Warren (springy 4, light_boned 3) / frail 2 and prey.

**HORSE** (quadruped, equine)
- **Torso**
  - M: 22 HP, 13 slots, saddle, about 960 mB, hauler 2.
  - A: draft_chest (hauler 1).
- **Head**
  - M: courier, hauler or companion; loyal.
  - A: bolt.
- **Legs** (signature)
  - M: walk 0.3, gallop; rideable with 2 or more horse legs, a saddle and a heavy enough torso; jump from its jump strength; sure_footed 2.
  - A: leggings canter; boots galloper 1.
- **Tail:** fly_swat (M); hips swift 1 (A).
- **Hide:** groomed_coat (+5% speed per piece; sums).
- **Heart:** stallion_heart (hardy 2).
- **Set:** Centaur (galloper 3, sure_footed 2, your mount gets Jump Boost II) / Hay Burner.

**VILLAGER** (biped, villager)
- **Head**
  - M: SURGEON, the profession job (§6.4), courier.
  - A: appraiser (lucky 1).
- **Torso**
  - M: 20 HP, 3 slots.
  - A: hardy 1.
- **Arms** (a pair)
  - M: pacifist, hand grip for two, +9 storage.
  - A: quick_hands 1.
- **Legs**
  - M: walk 0.25.
  - A: leggings sure_footed 1; boots stealthy 1.
- **Hide:** trustworthy (golem_trust).
- **Organ:** Village Heart. M: beloved. A: hero.
- **Set:** Elder (hero 2, lucky 2) / Meek.

**ZOMBIE** (biped, humanoid, rotting, undead)
- **Head**
  - M: guard, companion or courier; relentless; hungering bite; Horde (undead-headed minions within 16 join its fights).
  - A: dead_face.
- **Torso**
  - M: 20 HP; sun_cursed (it burns by day unless it wears a helmet, and at worst collapses); hunger_proof; regenerates 1 HP every 10 s in the dark.
  - A: hardy 2.
- **Arms**
  - M: grab (2.5 damage, Slowness III for 1 s), grip hand, breaks doors.
  - A: grabbing 1.
- **Legs**
  - M: walk 0.23, shamble.
  - A: fall_guard 1.
- **Hide:** none.
- **Organs:** Rot Gut stomach (iron_gut); heart undying (20% chance, 5 minutes).
- **Set:** Shambler (dead_face, brawler 2) / sun_cursed and inverted_healing.

**SQUID** (tentacled, cephalopod, aquatic)
- **Torso**
  - M: 10 HP, 10 slots, swims with no legs, gills; dry, it drains 1% power a minute instead of suffocating.
  - A: waterborn 1.
- **As its own head**
  - M: courier or companion; underwater sense.
  - A (stand-in): gills.
- **Tentacles** (8 legs)
  - M: amphibious (swim 0.35, land slither 0.1).
  - A: leggings slick; boots waterborn 1.
- **Organ:** Ink Sac (vanilla ink sac).
  - M: when hurt, an ink cloud blinds everything within 4 for 3 s (15 s); an organic squid also makes an ink sac every 10 minutes.
  - A: the same cloud when hit below 50% health (30 s).
- **Set:** Kraken (gills, waterborn 3, night vision underwater) / dry_out (60 s).

**IRON GOLEM** (biped, golem, bloodless mob, fall)
- **Torso**
  - M: 50 HP, 12 slots, heavy 1, fall immune, sink mode in water; iron ingots repair 25 HP (both kinds).
  - A: iron_skin (thick_hide 2, steady 1).
- **Head**
  - M: guard, sentry or bodyguard; territorial (attacks what hurts villagers or its maker within 16).
  - A: steady 1.
- **Arms** (signature)
  - M: fling (8.5 damage, launches 0.6 up, attack speed ×0.6).
  - A: flinger 1.
- **Legs**
  - M: walk 0.2, heavy 2.
  - A: leggings heavy 1; boots crusher.
- **Organ:** Golem Core. M: hardy 5, steady 3, and regen 1 HP every 4 s while standing still. A: +4 max health, and Iron Will (+4 armour below 50% health).
- **Set:** Colossus (knockback immunity, brawler 3) / heavy 3.

**CHICKEN** (bird, fowl, fall)
- **Torso**
  - M: 6 HP, 3 slots, fall immune.
  - A: light_boned 1.
- **Head**
  - M: scavenger, companion or farmer (prefers seeds).
  - A: pecking_order.
- **Wings**
  - M: flap, lift 0.02 each (a cow with chicken wings slow-falls and never flies), featherfall.
  - A (shoulders): featherfall.
- **Legs**
  - M: hop 0.25, strut.
  - A: leggings swift 1; boots fall_guard 1.
- **Hide** (feather): downy.
- **Organ:** Egg Gland (signature). M (organic): an egg every 5 minutes for 10 mB. A (chest): an egg into your inventory every 10 minutes.
- **Stomach:** Gizzard (seed_eater).
- **Set:** Featherweight (featherfall, fall immunity) / frail 3 and prey.

### 8.2 The signature of every one of the 79 mobs
Each row is what that mob's own file adds on top of its layers. Every mob has at least one; most have two or three.

| Mob | Signature facets |
|---|---|
| allay | Head: a scavenger that fetches items matching what it holds, from 32 blocks. Organ Harmonic Gland: M item_magnet 2, A item_magnet 1. |
| armadillo | Torso: roll_up (both). Hide (scute): Scute Plating (tough 1, durability ×1.5). Organ Scute Gland: M sheds scutes, A thick_hide 1. |
| axolotl | Torso: play_dead (both). Head: hunter of `#minecraft:axolotl_hunt_targets`. Organ Regrowth Gland: M regen ×2 when wet, A regen 1 HP every 3 s when wet. |
| bat | Head: echolocation (M targets through walls within 16; helmet outlines mobs within 16 every 5 s, client only). Organ Echo Ear: echo_sense 2. |
| bee | Organ Stinger: M sting (Poison II, then the limb is spent 60 s), A 25% Poison II on hit (10 s). Organ Honey Stomach: M fills bottles with honey and bonemeals a crop within 4 each minute, A honey (activate, 30 mB, 120 s). |
| blaze | See §8.1. |
| bogged | Arm: poison arrows with a bow. Organ Spore Marrow: M aura Poison I within 3, A venom_proof plus 20% Poison on hit. |
| breeze | Torso: deflector 2 (projectiles bounce back). Organ Wind Core: M wind_charge shots, A wind_burst (40 mB, 10 s). |
| camel | Torso: hump (reservoir ×2, 2 seats). Legs: rideable with a dash, boots movement efficiency +0.5. Organ Hump Fat: M power capacity ×1.5, A foods +50% saturation. |
| cat | Organ Purr Box: M morning_gift at dawn, A regen 1 HP every 4 s while sneaking still. (Head cat_ward comes from the family.) |
| cave_spider | Organ Venom Sac: M venomous 2, A venomous 1. Its tiny torso fits 1-block gaps. |
| chicken | See §8.1. |
| cod | Head: fishes with its mouth (fisher with no rod). |
| cow | See §8.1. |
| creeper | See §8.1. |
| dolphin | Tail: dolphin_kick (M aura Dolphin's Grace for players within 8; hips Dolphin's Grace while swimming). Organ Melon: M targets underwater without line of sight within 32, helmet outlines mobs underwater within 32. Hide: slick. |
| donkey | Torso: saddlebags (+15, takes a chest). Organ Pack Sinew: A hauler 1 and steady 1. |
| drowned | Arm: throws a held trident. Legs: seabed walker (M sink mode at full speed, A water movement +0.2). Organ Drowned Lungs: gills. |
| elder_guardian | Organ Elder Eye: M fatigue_aura within 16 every 60 s, A melee attackers get Mining Fatigue II for 6 s. |
| enderman | See §8.1. |
| endermite | Organ Rift Mite: rift (both). |
| evoker | Organ Totem Gland: undying (A costs 500 mB, M 50% power; 20 minutes). Arm: fangs (M ranged; shoulders 10% on hit, 10 s). |
| fox | Head: thief (scavenger that steals the held items of mobs it bites; helmet 5%). Legs: pounce from 5 blocks. Variant: snow fox hide is insulated. Organ Cheek Pouch: M +9 storage, A item_magnet 0. |
| frog | Organ Sticky Tongue: M pulls a target within 6 and eats small slimes, dropping a froglight of its variant; A tongue (15 mB, 5 s). Legs: springy 3. Variants: warm legs fireproof 1, cold legs frost_guard 1. |
| ghast | Organ Tear Gland: great_fireball (M 20 mB, 30 s; A 100 mB, 30 s; blocks only with mobGriefing and config). Tentacles: float (M float mode; boots slow fall while sneaking). Its torso is too big to be a frame. |
| glow_squid | Organ Glow Sac: M glows, glow_aura within 8, makes glow ink; A night vision underwater and a glow layer. |
| goat | Head: ram (an armless minion rams; helmet charge dash, 10 s). Legs: sure_footed 2, fall_guard 1. Set: Mountaineer (fall immunity) / screamer. |
| guardian | Organ Prism Eye: guardian_beam (M 3 s charge, 6 damage; helmet charged by the key, 40 mB). Torso armour: barbed 2 (spikes). Tail: swim +30%. |
| hoglin | Head: toss (an armless bite launches upward; helmet 15% on hit launch). Organ Boar Heart: adrenaline. Set drawback: warped_dread. |
| horse | See §8.1. |
| husk | Arm: hungering. Torso: sunbaked (removes sun_cursed). Set: Desert Shambler (dead_face, fireproof 1) / thirst. |
| illusioner | Organ Mirror Gland: mirror (both, 20 s). Arm: blindness arrows with a bow. |
| iron_golem | See §8.1. |
| llama | Organ Spit Gland: spit (M 2 s; A 10 mB, 2 s). Head: caravan (a courier that follows the minion in front of it). Hide: frost_guard 1. |
| magma_cube | Organ Magma Core: landing from over 3 blocks sets entities within 2 alight (M; boots, plus fireproof 1). Torso: fire-immune and bouncy. |
| mooshroom | Torso: stew_udder (fills bowls with stew). Organ Mycelial Gut: M forages mushrooms, A mushrooms and stews give Regeneration I for 5 s. |
| mule | Torso: saddlebags. Legs: stubborn. |
| ocelot | Legs: sprinter (M swift 3; A swift 1 and galloper 1). |
| panda | Head: temperament from its gene (aggressive: bodyguard and brawler 2; lazy: a sentry that never moves; playful: random tumbles; worried: flees during thunder; weak: sneezes the `panda_sneeze` loot table; others docile). Organ Bamboo Gut: bamboo and sugar cane are edible. |
| parrot | Head: mimic (plays the nearest hostile's sound within 20 as an alarm; helmet alert). Wings: lift 0.06. Set drawback: cookie_poison. |
| phantom | Wings: glider (chestplate shoulders act as an elytra; M lift 0.4 each). Hide (membrane): featherfall. Organ Night Stalker Gland: A dark_sight and swift 1 at night, M pounces from above. |
| pig | Head: carrot steering (a rideable minion with a pig head is steered with a carrot on a stick). Organ Bacon Fat: M power capacity +25%, A hardy 1 and frost_guard 1. |
| piglin | Head: barterer job. Arm: crossbow. Organ Gold Gizzard: A lucky 1; the M barterer rolls twice 10% of the time. |
| piglin_brute | Head: brute guard (bodyguard; +50% damage with an axe). Torso: 50 HP. Organ Rage Gland: adrenaline 2. |
| pillager | Head: surgeon or sentry. Arm: quick_draw (both). |
| polar_bear | Hide: Polar Fur (insulated; boots powder_walker). Head: maul bite 3 with swipe. Organ: Brown Fat (family). |
| pufferfish | Organ Toxin Sac: M toxin_puff (poison aura when a target is within 2), A toxic_skin. |
| rabbit | See §8.1. |
| ravager | Head: roar (both, 20 s). Legs: trample. Torso: 50 HP, rideable, 2 seats. Organ War Heart: steady 3 and hardy 2. |
| salmon | Tail: upstream (M swims up water columns and waterfalls; hips waterborn 1). Head: fisher. |
| sheep | Torso: wool_regrowth in its captured colour (organic, unskinned). Hide: Fleece (frost_guard 2, fall_guard 1). Organ Lanolin Gland: A the piece mends 1 durability every 30 s, M wool twice as fast. |
| shulker | Lid (arm, by override): Shell Clamp (M clamp strike; shoulders 30% frontal deflect). Organ Levitation Gland: shulker_bolt (both; A 40 mB, 5 s). Hide (shell): shell_guard 2. |
| silverfish | Organ Burrow Gland: A burrower 2. Head: swarm (horde_call for silverfish-headed minions within 8). |
| skeleton | Arm: bowman (with a bow and arrows; without, it throws bones for 2). Shoulders: sharpshooter 1. Organ: Marrow (skeletal). |
| skeleton_horse | Legs: seafloor steed (rideable underwater, sink mode, never drowns). |
| slime | Organ Slime Core: sticky (both). Torso: bouncy. |
| sniffer | Head: digger job. Organ Olfactory Bulb: helmet activate outlines suspicious sand and gravel within 32 (20 mB). |
| snow_golem | Head: snow sentry (snowball_volley, no power cost). Lower body: roll (M), frost_path (boots). Torso: heat_hurts (it melts in hot biomes). Organ Frost Core: chilling 1. |
| spider | See §8.1. |
| squid | See §8.1. |
| stray | Arm: Slowness arrows. Legs: frostbitten (boots powder_walker and frost_guard 1). Organ Frost Marrow: chilling 1. |
| strider | Legs: lava walk (M lava_walk; boots lava_wader). Torso: rideable with a warped fungus on a stick. Organ Lava Bladder: fire and lava damage ×0.5 while in lava. |
| tadpole | Torso: bucketable (a water bucket scoops the minion into a dormant bucket item). |
| trader_llama | Head: trader's guard (a bodyguard that also defends wandering traders and villagers). Organ: Spit Gland. |
| turtle | Head: homing (M returns home when idle and never loses it; helmet gives 10 s of Water Breathing after leaving water). Torso: shell (M thick_hide 4, chest shell_guard 2). Organ Salt Gland: immune to dry_out, waterborn 1. |
| vex | Organ Vex Wisp: dash (M dash-strike through 6 blocks; A 20 mB, 5 s). |
| villager | See §8.1. |
| vindicator | Arm: axeman (+100% damage with an axe). Head variant: a head named "Johnny" is berserk. |
| wandering_trader | Organ Trader's Draught: targeted at night gives Invisibility for 20 s (5 minutes; both). |
| warden | Head: vibration sense (tremor: targets moving things through walls, not still or sneaking ones; helmet shows moving mobs within 16). Organ Sonic Core: sonic_boom (10 damage ignoring armour, 30 s, 100 mB). Legs: silent_steps. Hide: Muffled (silent_steps). Gated by `boss_parts`. |
| witch | Arms (pair): potion thrower (throws the splash potions it carries). Head: medic. Organ Alchemical Gland: below 50% health, Regeneration I for 5 s plus Fire Resistance if burning (30 s). |
| wither | Heads: 3 sockets, each firing wither skulls. Organ Wither Core: M wither_aura within 4, A withering 1 on hit. Set: Decay Lord (wither_proof, withering) / golems_hostile and frail 3. Gated. |
| wither_skeleton | Arm: withering on hit. Organ Wither Marrow: wither_proof. |
| wolf | Head: pack_hunter. Tail: mood tail (its angle shows the minion's health; hips steady 1). |
| zoglin | Head: berserk (attacks every mob except its maker, +50% damage). |
| zombie | See §8.1. |
| zombie_horse | Legs: undead steed (rideable, walks the seabed, never drowns). |
| zombie_villager | Head: shaky surgeon (surgeon at half speed). Organ Curable Heart: immune to Weakness; golden apples give Absorption II. |
| zombified_piglin | Head: horde_call (when hit, zombified piglins and undead-headed minions within 16 turn on the attacker). |

---

## 9. Build order
Each slice ends with headless game tests (`runGameTestServer`, in the style of BBGameTests and MinionTests) and an on-screen check for anything visual. Bloodless wording keys ship with every new string from slice 1 (rule 4).

**1. Cow and rabbit armour, end to end** (the vertical slice).
- Build:
  - `bone_slot_rules.json` and `PartSlot.of`;
  - loaders for mob_group, trait, mob_traits and scrap_material, with only the files needed (the quadruped archetype; grazer and small_prey families; hide_plate and sinew materials; cow and rabbit files);
  - the `source` component and the `scraps` item;
  - Mangler scraps through the capturing sink (degloved, rot and baby rules);
  - the `carcass_armour` recipe for helmet, leggings and boots (the chestplate waits for the strap-on in slice 4);
  - base stats through `ItemAttributeModifierEvent`; baked `MAX_DAMAGE`;
  - `ActiveTraits` for players; the passive, tick and hurt triggers; the attribute, mob_effect, damage and immunity effect types; tooltips.
- Tests: `mangledCowGivesPartScraps`, `deglovedGivesMore`, `rottenGivesHalf`, `craftCowBoots` (attributes and durability), `rabbitLeggingsRaiseJump`, `mixedHelmetRefused`, `mechanicalCrafterMakesLeggings` (components survive), `sameTraitCountsOnce`, `bloodlessNames`.
- On screen: rabbit leggings on a player.

**2. A cow torso on rabbit legs** (the minion rebuild, minimal).
- Build:
  - `MinionBuild` and a pure `MinionStats`;
  - the work-zone claim of heavy torsos and parts; item fitting; sockets from rigs plus the blob layout;
  - the organic wake with a blood bucket;
  - walk and hop; head bite; companion and courier jobs, ported;
  - `StitchedMinionRenderer` at native scale, with lift, sine gait and the union hitbox;
  - drain by activity; the Blood Trough (tank, pipes, per-level registry, contraption-movable) and the seek goal;
  - power-down (lying, invulnerable except to players and bypass sources, ignored, pushable); revive; Dormant Minion; the config cap.
  - Retire the old frame, `MinionJob` and the humanoid renderer, with a conversion for old minions.
- Tests: `cowFrameClaimedFromTable`, `buildCowOnFourRabbitLegs` (exact stats), `cowOnRabbitLegsOutpacesCow`, `feetNeverBelowGround`, `hitboxContainsParts`, `drainToZeroPowersDownAlive`, `zombieCannotKillPoweredDown`, `walks12BlocksToTroughAndRefills`, `cannotReachTroughStaysDown`, `unloadedChunkNeverDrains`, `minionSavedAndLoaded`, `minionCapRespected`.
- On screen: a video of the cow on rabbit legs, reviewed with the user for "jank, not buggy".

**3. Resolution breadth.**
- Build:
  - all 11 archetypes, 27 families and the overlays and tags; the slots of all 79 rigs;
  - class matching through a throwaway instance;
  - variants from carcass traits (extend capture: charged, gene, name, frog/fox/rabbit variant);
  - organ lists in `Surgery.harvest` (the `gland` item, stamped sources, bloodless cores, harvesting from heavy carcasses in the work zone);
  - hide stamping and the `hide_sources` and `organ_sources` data maps;
  - the sync payloads; `/bloodandbones traits explain` and `dump`;
  - JEI Body Parts pages; Create item attribute types.
- Tests: `allMobsResolve` (79 against the §3.5 table), `everyBoneHasASlot`, `moddedZombieByTagIsRotting`, `skeletonGivesMarrow` (replaces "none from a skeleton"), `organCarriesSource`, `hideStamped`, `heavyCarcassOrgansOnRig`, `networkRoundTrip`, `signatureLint` (reports only, until slice 9).

**4. Armour B.**
- Build:
  - the chestplate with the backtank strap-on;
  - the fitting recipe (hide, organ, tier, returning what it replaces);
  - the Organ Ability key, `ItemCooldowns` and blood cost; set detection;
  - the attack, fall, kill, targeted and activate triggers;
  - the effect types vanilla (plus our 7 actions), impulse, teleport, kin, visibility, regen, diet, mend, projectile, hitscan, deflect and detonate.
- Tests: `spoutFillsStrappedTank`, `implantDrainsStrappedTank`, `unstrapReturnsTank`, `blazeCoreChestIgnoresFire`, `endermanHelmetIsEnderMask`, `creeperSacBlastSparesWearer`, `fullZombieSetKinAndSunCursed`, `mixedHideBreaksSet`, `fittingReturnsOldOrgan`, `tierNeedsPreviousTier`, `reductionFlooredAt20Percent`.

**5. Player movement and senses** (client-predicted).
- Build: the climb, glide, bounce and powder_snow flags; lava_wader and frost_path through `replace_disk` and `cooled_crust`; silent_steps; the reveal and echolocate client outline; reaction goals (hunt and flee).
- Tested on a real client with simulated lag and on a dedicated server, not only headless.

**6. Minion bodies B.**
- Build: all movement modes; lift and flight; mount (saddle, carrot, fungus, seats); strike styles; held-weapon ranged goals; innate projectiles and hitscan; detonate then power down; produce (organic only); aura.
- Tests: `spiderLegsClimb3HighWall`, `rabbitOutrunsCow`, `horseLegsAcceptRider`, `phantomWingsLiftCow`, `chickenWingsDoNot`, `skeletonArmsShoot`, `creeperSacPowersDownNotDestroyed`, `chickenOrganLaysEgg`, `squidInkBlinds`.

**7. Heads and jobs.**
- Build: the job registry; head job options cycled by the maker; blind heads; new jobs in the order guard, bodyguard, sentry, scavenger, SURGEON (`Surgery.surgeonPresent` for the amputation ritual), hunter, hauler, butcher, fisher, barterer, digger, medic, herder, sapper.
- Tests: `villagerHeadOffersSurgeon`, `pillagerHeadOffersSurgeon`, `nitwitOffersCompanionOnly`, `blindHeadLosesSightJobs`, `haulerDragsCarcassToHook`, `hunterWithMeatHookLeavesCarcass`.

**8. Brass.**
- Build: Brass Sheathing; skinned-only fitting; the Soul Canister (filling and emptying recipes); the Charging Cradle (kinetic, item handler, Mechanical Arm point, contraption-safe, sheet repair); the module socket with baseline modules; the filter slot; brass repair by hand and by Deployer.
- Tests: `spoutFillsCanister`, `cradleSwapsFromHopper`, `cradleRevivesPoweredDown`, `filteredCourierOnlyMovesIron`, `deployerRepairsBrass`, `cyberImmuneToPoison`, `produceInertOnCyber`.

**9. Content breadth.**
- Build: the 79 signature files (the signature test must now be green); every full set; the starter library (about 120 traits) with lang and bloodless variants; the lints; a balance pass with the CSV dump; Ponder scenes.

**10. Later.**
- The Deployer route through `DeployerRecipeSearchEvent`.
- A performance test with 100 idle and 20 fighting minions, logged.
- Config multipliers tuned with the user.
- Optionally, mob limbs fitted to players as organic prosthetics reusing the same armour traits.

---

## 10. Risks
1. **Authoring and balance volume.** 79 mobs × up to about 11 facets. Group defaults mean nothing per mob is required; the signature test and lints turn gaps into a list; the CSV dump supports review.
   - Watch undead helmets plus the Shambler set (undead ignoring you), the Hero, Raid Captain and Colossus sets, and stacking activates.
   - The safety valves: caps, dedup, the 20% reduction floor, `disabled_effect_types`.
2. **Client-predicted player movement.** Climb, glide, bounce, lava and frost paths must run the same code on client and server from synced data, or "moved wrongly" rubber-banding follows. This is tested on a real client with lag and on a dedicated server.
3. **The backtank strap-on changes tested code.** The fluid capability has to pass through so Spouts and Drains still work. If it slips, the chestplate must not ship.
4. **Component-carrying recipes and Create.** Crafting grids and Mechanical Crafters call `assemble` (checked). The Deployer, fans, spouts and sequenced assembly do not, which is why they are avoided until `DeployerRecipeSearchEvent` is proven, including how the Deployer caches recipes. JEI shows the shaped recipes with placeholder scraps; dynamic outputs need a custom JEI category.
5. **Physical build on the table.** Claiming heavy carcasses turns Sable sub-levels into block entity data. It needs checks against a carcass on a hook, on a contraption, or being dragged by another player, and against claiming across a moving table.
6. **Rendering mixed rigs.** Different scales (horse 1.1, llama per-axis, babies), rest rotations (horizontal cow torso, vertical zombie torso), missing client rigs for modded mobs (they fall back to a cube) and pieces clipping into each other. The server and client must share the geometry helper, or pathing and clicks break. Only screenshots prove "jank, not buggy".
7. **Navigation swaps.** Vanilla builds navigation once, so swapping it on rebuild must clear the path and reset gravity. Flying and hovering pathing is costlier. Large torsos cannot path in caves. Waking a large frame must check the space around it first.
8. **Riding a custom mob.** Client control (`isControlledByLocalInstance`), the charge-jump HUD and several seats make this L. Ship without it if it slips.
9. **Existing code that changes:**
   - `MinionFrame` and `MinionJob` (and saved minions);
   - `Surgery.harvest` and `PatientTests`;
   - `FluidBacktankItem.wornBy`;
   - `CarcassButchery.skin`, which stamps hides;
   - `CarcassMachineBlockEntity.mangle`, which adds scraps;
   - `BBItemAttributes.PiecePart`, which becomes a caller of `PartSlot`;
   - `CarcassLook`, whose captured traits grow.
10. **Class-matching through a throwaway instance.** A modded entity whose constructor has side effects or crashes. It is wrapped in try/catch and falls back to tags and archetype. A config deny list skips it.
11. **Powered-down exploits.** Being ignored and invulnerable could make pack mules or mob-farm blockers. Pushability and "players can still hurt it" limit this; it needs review.
12. **Performance.** No default cap means big farms of pathfinding mobs; document it and leave the cap to server owners. `ActiveTraits` is cached and trait ticks are staggered. Reaction goals are injected only into tagged entity types. Auras run at most once a second. Reveal is client-only.
13. **Griefing.** Detonate, trample, ghast fireballs, the digger and hunter jobs all obey mobGriefing plus `minion_block_damage`.
14. **Sync size.** It will probably be 100–200 KB, so it is split per file kind.
15. **Translation volume.** About 120 traits, each needing a name, a description and a bloodless variant, plus organ names. Use lang datagen.
16. **Boss parts.** Warden and wither parts rely on the boss overlay, the health cap and `boss_parts`.
17. **Live resolution.** A datapack retune changes armour and minions that already exist. This is deliberate under rule 3, but it surprises players: patch notes must say so (see the open questions).
18. **API names not yet confirmed as signatures.** Class names were checked in the jars for everything marked (✓); method signatures beyond those `javap`-listed (`IItemExtension`, `IClientItemExtensions`) still need checking when coded. `ProjectileDeflection.REVERSE` and the vanilla `replace_disk` fields need a quick check.

---

## 11. Open questions for the user (expensive to reverse)
1. **Scrap source.** Mangler only (default, per the brief), or also a Cleaver at the Butcher's Table at half yield with random loss, so armour has an early game without Create?
2. **Backtank chest slot.** Is the strap-on (a carcass chestplate carries the backtank's tier and fluid) the right fix, or would you rather armour and backtank stay mutually exclusive, or have some other slot?
3. **Minion combat death.** The default is `collapse` (a lethal hit leaves it powered down at 1 HP). Or `scatter` (falls apart into its parts), or `destroy`?
4. **Live resolution.** Armour and minions store only where their parts came from, so a datapack retune changes items already made. Keep that (default), or bake the stats at craft time?
5. **Physical build.** Heavy torsos and limbs can never be items (`LIGHT_MASS` 0.13), so they are dragged into the table's work zone and claimed with an empty-hand click. Is that the interaction you want?
6. **Deglove pipeline on minions.** Organic frames take only unskinned pieces and brass frames only skinned ones (hideless mobs count as both). Enforce it (default), or let any piece fit either?
7. **Front legs.** Is a quadruped's front leg a leg (default: it moves the minion and makes leggings and boots), or should front legs count as arms (they would attack and make chestplate shoulders)?
8. **Surgeon heads.** Every villager-family and illager-family head offers Surgeon (default, by group), or only villager and pillager heads, as the brief literally says?
9. **Signatures.** Is the direction of the 79 signatures in §8.2 right? Per the brief, the per-mob abilities get authored after the base system works (slice 9).
10. **Boss parts.** Should warden and wither parts be usable by default (`boss_parts` true)?
11. **Tiers.** Upgrade by crafting (piece + ingot; automatable with Mechanical Crafters; default), or at the Smithing Table like vanilla netherite?
12. **Dormant Minion item.** The maker can fold a powered-down minion of any size into an item to carry home. Is that fine, given that hauling heavy carcasses is meant to be annoying?
13. **Mobs wearing carcass armour.** They get its armour points only, never its traits (default). OK?
14. **Name.** "Carcass armour" ("Spider Chitin Leggings"), chosen to avoid clashing with "flesh grafts" in self-augmentation. OK?
