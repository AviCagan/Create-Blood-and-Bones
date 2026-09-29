# Your mob in Blood & Bones

This page is for someone who makes a mob in another mod, or a datapack author who wants that mob to come apart well.
You do not have to do anything: every living mob becomes a carcass when it is killed with the Meat Hook. What follows
is how that works, and what you can do to make it better.

## With no files at all

A mob Blood & Bones has never heard of still becomes a carcass (docs/ARCHITECTURE-PROPOSAL.md 15.31).

1. **Its group.** The mod decides what shape of animal it is (its *archetype*) from:
   - your mob's own file, if you ship one (`data/<ns>/mob_traits/<your ns>/<mob>.json`, `"archetype"`);
   - a tag: add your mob to one of the mod's family or overlay tags (`#bloodandbones:family/bear`, for example);
   - otherwise from its shape: an archetype's `match` rules look at its hitbox (wider than tall is four-legged, taller
     than wide two-legged), its spawn category (a water creature is a fish, an ambient one a flier) and, if it has a rig
     file, how many legs and arms that has. The two-legged `biped` is the last resort.
2. **Its body.** The archetype names a *generic rig* (`data/bloodandbones/generic_rig/<archetype>.json`): a torso, a
   head and limbs, laid out against the hitbox, so they scale with it. A quadruped is a body, a head, four legs and a
   tail; a biped a body, a head, two arms and two legs; a fish a body, a head and a tail; and so on for all eleven
   archetypes. It weighs what its boxes hold, bleeds, drags, hangs, rots and is butchered like any other carcass. A baby
   is the same body at half size about its feet. Its joints come from its parts' names (below); a generic rig file may
   give any of its bones a `"joint"` of its own (`min_degrees`, `max_degrees`, `damping`, `stiffness`, `contacts`, as
   a rig file's joints), and that wins.
3. **Its look.** On the client, each part of the generic body looks for a part of your model with its name ("head",
   "body", "right_front_leg", "tail"...), and draws it stretched to fit, in the skin your renderer gives a plain one of
   your mob. The model is found under the layer named after your mob (`<your ns>:<mob>`, layer `main`, as the game
   names its own), or, failing that, from your renderer's model if it is a `HierarchicalModel`. A part it cannot find
   is drawn as a box in a patch of your skin. A model the game cannot read as parts (one drawn by another library) is
   all boxes.
4. **Its yields.** Butchery comes from your mob's groups (`"butchery"`), spread over the body by the size of each part.
5. **Its weight class** comes from its size (below), unless a group names one.

What this cannot do: the generic body has the archetype's parts, not yours. Six legs, two heads or a long neck come
out as the archetype has them. For that, give your mob a rig.

## A rig of its own

A rig is a JSON file of your mob's parts as bodies: `data/<ns>/rig/<your ns>/<mob>.json` (the format is
`carcass/rig/Rig.java`; the mod's own are in `src/generated/resources/data/bloodandbones/rig/minecraft/`). The mod
writes its rigs from the game's own models in a data run, reading the model and applying the rules below, and a
*rig target* (`src/main/rig_targets/<mob>.json`) says only what the rules get wrong. If your model follows the rules, the
target is three lines: the mob, its model layer and its texture.

### The rules the model is read by

**Which parts become bodies.** Every part with a solid cube (not a flat one) is a body of its own, and everything
under it that is not a body is drawn with it. Keep the parts that should flop separately separate; put what should stay
fixed (a horn, a hat, the mouth) inside the part it belongs to.

**The torso** is the shallowest part named `body`. If there is none, it is the biggest part at the top of the model.

**What hangs off what.** A part inside another part hangs off it. A part at the top of the model hangs off the torso,
except:
- links named `segment0`, `segment1`... hang one off the next, toward whichever of them is the torso (a silverfish);
- a front body carries what joins at the front: with a `body0` that is not the torso (a spider's front half), every
  leg and the head hang off it; with an `upper_body` (a wolf's shoulders), the head, the arms and the front legs do.

**Where a joint is.** At the child part's pivot, as you set it in the model. Put a leg's pivot at the hip, a head's at
the neck.

**How far a joint bends** comes from the part's own name (not the path above it):

| Name contains | Joint |
|---|---|
| `tentacle` | loose every way, heavily damped |
| starts with `segment` | bends side to side more than up and down |
| `head`, `neck` | lolls: nods −30 to 60 degrees, turns 45, tips 15; a head on top of its body (joined at or above the body's top, and rising from there) also cannot sink into it |
| `leg` held out flat (much longer than thick, along the level) | a spider's: little up and down, sweeps and twists a lot |
| `leg`, `arm` | swings 75 degrees fore and aft, splays 40 sideways, almost no pull back |
| `tail`, `wing`, `ear`, or ends in `_back` | 45 degrees every way |
| `body` (a second body part) | a stiff spine |
| anything else | 30 degrees every way |

**Parts that are never bodies** (decor): `nose`, `mouth`, `upper_mouth`, `lower_beak`, `beak`, `beak1`..., `goatee`,
`hat`, `jacket`, `mane`, `hump`, `feather`, `red_thing`, `stinger`, `spike0`..., `eye`, `eyes`, `right_ear`/`left_ear`,
`right_horn`/`left_horn`, `right_antenna`/`left_antenna`, `right_eye`/`left_eye`, `right_sleeve`/`left_sleeve`,
`right_pants`/`left_pants`, anything ending in `fin`, and anything ending in `_tip`. Inside a body they are drawn with
it. At the top of the model they ride along with the right body: sleeves and trousers with their side's arm and leg,
what belongs on a face with the `head`, the rest with the torso.

**Tack is left off**: anything with `saddle` in its name, `right_chest`/`left_chest`, `bridle`, `reins`, and anything
ending in `_baby_leg`.

**The collision box** of each body is its biggest cube. **The rest pose** is the model's as defined, not what its
animation code does each frame; pose it standing in the definition.

**Size.** If your renderer draws the model bigger or smaller than it is defined (a `scale` in its render), say so in the
target's `"scale"`.

### What a target is for

Only what the rules cannot know: a part that should not be a body (`"merge"` it into the one above, or `"attach"` it to
another), a joint that must be stiffer or looser than its name gives (`"joints"`), a collision box smaller than what is
drawn (`"boxes"`: a ghast's tentacles collide shorter than they are drawn), a different parent (`"parents"`), a torso
the rules do not find (`"torso"`), parts hidden outright (`"hidden"`), and how a baby is drawn (`"baby"`). Every name
in a target must be a real part; a typo stops the data run.

The mod's own targets keep these on purpose (ARCHITECTURE 15.31 lists them): the zombies' and skeletons' limbs and
heads, the horse family's necks and tails, the wither, the ghast's tentacles, the turtle's flippers, seven heads that sit
on top of their bodies but must not collide with them, and the parts of a few mobs drawn as one piece (a bee's legs and
wings, a blaze's rods, a magma cube's layers).

## Your mob's own file

`data/<ns>/mob_traits/<your ns>/<mob>.json` sets anything its groups get wrong. All of it is optional; for the carcass:

```json
{
  "archetype": "bloodandbones:quadruped",
  "family": "bloodandbones:grazer",
  "weight_class": "bloodandbones:huge",
  "rot_time": 30000,
  "butchery": {"meat": "minecraft:mutton", "hide": "examplemod:thick_hide", "hide_per_weight": 4.0},
  "baby_yield": 0.25,
  "tissue": "flesh"
}
```

- `generic_rig` (on a group or a mob file) picks a different generic body.
- `weight_class`: see below.
- `rot_time`: ticks from fresh to rotten in a temperate place (a rig's own `rot_time` beats it).
- `butchery`: any of `meat`, `meat_per_block`, `hide`, `hide_per_weight`, `bone`, `bone_per_block`, `bone_min`,
  `offal_per_weight`, `fat_per_weight`, `hide_extras`, `part_extras` (by body name). Each group can set some, and the
  last to set a field wins, so a mob's own file need say only what it does differently (the vanilla mobs' files do
  just that). A table of your own (`data/<ns>/butchery/<your ns>/<mob>.json`) beats all of them.
- `baby_yield`: a baby's share of the grown one's yields; by default its share of the size.
- `tissue`: `flesh`, `bone` or `plate`, which sets how heavy its bodies are.

## Weight classes

`data/bloodandbones/weight_class/<id>.json`. A mob no group names a class for goes in the smallest class whose
`up_to` its size (its rig's weight as flesh, a full block being 1) fits.

| Class | Up to | Floats or sinks | Rot time | Vanilla mobs in it |
|---|---|---|---|---|
| tiny | 0.05 | floats (1.3) | 12,000 | bat, cod, tadpole, parrot, rabbit, silverfish, endermite, allay, vex |
| small | 0.2 | floats (1.2) | 12,000 | salmon, cat, frog, bee, phantom, blaze, breeze, pufferfish, axolotl, chicken, ocelot, cave spider |
| medium | 0.7 | floats (1.1) | 20,000 | armadillo, fox, wolf, the skeletons, creeper, dolphin, enderman, the villagers, the zombies, sheep, the piglins, the illagers, pig, spider, guardian, the squids |
| large | 1.6 | sinks slowly (0.9) | 24,000 | donkey, cow, mooshroom, snow golem, mule, turtle, goat, strider, the llamas, the horses, shulker, iron golem |
| huge | 10 | sinks (0.8) | 36,000 | hoglin, zoglin, panda, polar bear, wither, camel, warden, slime, ravager, magma cube, elder guardian, sniffer |
| colossal | above | sinks (0.7) | 48,000 | ghast |

Each class also has `drag` (the drag penalty is multiplied by it; 1 for all six) and `blood_per_weight` (1,000 mB
for all six). The rot time is for a mob none of whose groups or files names one: many vanilla mobs have their own.
