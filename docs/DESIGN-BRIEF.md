<!-- The user's design brief, kept verbatim as the source of truth for what the mod is.
     Reread it before starting any new feature. Later decisions the user made in conversation are
     recorded in docs/ARCHITECTURE-PROPOSAL.md (§12 and §14) and take precedence where they differ. -->

# Create: Blood & Bones — design brief

I'm building a Create addon called **Create: Blood & Bones**. Mob carcass butchery,
blood as a processable fluid, body horror progression, and gore-themed decoration —
all built on Create's existing kinetic, fluid and contraption systems rather than
parallel machinery of its own.

This document is what the mod *is*. It's design, not implementation — decide the
technical approach yourself, but check with me before locking in anything that
would be expensive to reverse.

## Target

- Minecraft 1.21.1, NeoForge, Create 6.0.x (chain conveyors must be available), Java 21
- Mod id `bloodandbones`
- Don't change any of the above without asking

---

## The five rules everything else follows from



**2. Groups, not species.**
Nothing is ever configured per-mob if it can be configured per-group. Mobs sort into
body archetypes (quadruped, biped, bird, arthropod, fish, and so on) and into weight
classes, and everything — rigs, butchery yields, drag penalty, part lists — keys off
those. A specific mob can override its group by having its own file, but it must
never be *required* to. A modded mob nobody has ever heard of should work on day one.

**3. Everything is data.**
Recipes, yields, which mob is in which group, what a group's body looks like — all
data files, no hardcoded logic. Another mod or a datapack should be able to add a
creature, or retune the whole mod, without touching code.

**4. Ship the gore toggle from day one.**
A `bloodless_mode` config that reframes the whole mod as mechanical rather than
organic — "replacement" not "amputation", clean plating not grafted flesh, essence
not blood, constructs not corpses. Identical mechanics, presentation only, no
separate logic paths. Wire it in from the first feature, because retrofitting it
means touching every render and particle call in the mod.

**5. Contraption-safe.**
Every block moves on a gantry and glues to a contraption. A Create addon whose
blocks can't move feels broken. Carcasses ride contraptions correctly too.

---

## The carcass

The central object. A dead mob you can physically pick up and take apart.

**It's a set of parts, not a stage.** A carcass knows which parts it still has —
hide, head, limbs, torso, organs, blood — and parts come off in any order. There is
no linear "butchery progress" number. A filtered machine pulls one specific part out
of a mixed line and passes the rest through untouched. A carcass is finished when
nothing can take anything else off it.

**Not every carcass has every part.** A skeleton has no blood and no hide. What a
fresh carcass of a given mob starts with comes from its group, and anything asking
"is this whole?" has to ask relative to what it started with.

**Quality matters.** Killed with the Meat Hook → intact, full yields. Killed any
other way → damaged, failure chance downstream. This is the reason the Meat Hook is
worth using despite being a bad weapon.

**Weight matters.** Mobs fall into weight classes that drive a movement speed penalty
on whoever's dragging one — roughly 5% for a chicken up to 55% for a ravager.
Hauling has to be annoying enough that building a conveyor line reads as the
solution. This is load-bearing, not flavour.

**Guardrails.** Carcassas not processesed or preserved will degrade and rot over time and eventually melt or fade or degrade away

---

## Physics — what I actually want

This is the part that's been hardest and I care about it most.

A carcass should behave like a dead body, not like a model being slid around:

kill a mob with a meathook to turn it into a carcass, it should die and ragdoll immediately, with the proper weight and stiffness in the correct limbs with the correct jointage
- It **ragdolls**. Limbs hang, the head lolls, it settles into whatever shape the
  ground gives it.
- **Where you hook it matters.** Hook a hind leg and walk away and the animal comes
  round arse-first before it goes anywhere. Hook the head and it follows head-first.
- **Where the killing blow landed matters.** An animal shot in the flank goes down
  sideways; one hit from behind pitches onto its nose. Nothing about which way it
  falls should be scripted.
- **Hanging on a hook keeps it a ragdoll** — held by one shoulder, everything below
  still loose, swinging when knocked, hanging differently once a leg's been cut off.
- It can be **dragged up a one-block step** without jamming.
- Cutting a limb off changes how the rest of it behaves, because the limb is gone.

Constraints: it has to be cheap enough that a dozen at once is fine, so a carcass
should only be under real simulation while something is actually happening to it, and
should cost nothing while it's lying in a field. It has to look right in multiplayer.
And it has to survive being dragged onto a moving simulated sable physics contraption.


### Bodies and rigs

Body shapes are per-group models, with per-mob models as an optional upgrade layered
on top. **The joints should be derived from the model, not written down separately** —
if a model and a joint list can disagree, they eventually will, and every version of
that arrangement has drifted and produced limbs drawn in one place and grabbable in
another.

I want this to be applicable to all vanilla mobs and in the future some modded mobs aswell, so we will need a clear, documented naming
and structure convention for a model so the joints fall out of it automatically, and
tooling that gets most from vanilla mob models rather than making
you build sixty animals from scratch. Carcasses should wear the mob's own texture
wherever possible — hand-painting art per mob is not a thing that finishes.

---

## Tools

| Tool | What it's for |
|---|---|
| **Meat Hook** | Slow, heavy, low damage, bad against groups. The only weapon that leaves an intact carcass. |
| **Flensing Knife** | Field butchery by hand. Hold on a part to take it off. Roughly half the yield of a machine, with random loss. |

The yield gap between hand and machine is the point: the early game works with no
Create infrastructure at all, and machines are obviously worth building.

---

## Machines

| Block | What it does |
|---|---|
| **Shackle Hook** | Where a carcass stops being an entity. Hangs one, loads it onto a chain conveyor. The transport layer and the mod's visual signature. |
| **Deglover** | Kinetic rollers. Strips the hide off a whole carcass or a single limb. High stress, low RPM. |
| **Guillotine** | Winds up under rotation, drops on a redstone edge. The clean, straight limb cut — output feeds minion parts and mounted-limb decoration. |
| **Beheader** | Inline, continuous, fast, single-purpose, cheap. |
| **Mangler** | Terminal grind. Takes carcasses, degloved carcasses, loose limbs and degloved limbs — output varies by which. Returns a low-grade pile: bone, meat, and other junk to be used in custom armor crafting, plus the mob's normal vanilla drops. Deliberately wasteful. |
| **Bleeding Rack** | Hangs a carcass and drains blood into a tank. Needs no power. |
| **Spit Roast** | Cook whole carcasses and limbs, not just normal food. Hand-cranked or shaft-driven, much faster on a shaft. Cooked results carry effects that scale with what and how much you cooked. |
| **Surgery Table** | One block, two crafted attachments that slot into it and change both its model and its job — one for assembling minions, one for extracting organs and operating on players. |
| **Specimen Jar** | Displays one item, any item. |
| **Steel Table / Steel Rack** | Morgue furniture. Tables join into a run and share legs; racks display parts on shelves. Cold grey, no copper, no blood tones — the morgue reads as separate from the machines. |

Every station that removes parts carries a filter, so one line pulls a single part
out of a mixed stream and passes the rest through.

Three processing paths that must stay meaningfully distinct: the Mangler is fastest
and least selective with the worst yield; a filtered station is fast, dedicated to
one part, full yield; the Surgery Table is slowest, totally reconfigurable, full
yield plus organs.

---

## Blood and materials

**Blood** is a real fluid — pumps, basins, spouts, mixers, heaters all apply. Tagged
so other blood mods can pipe it in. Never forms infinite sources.

**Soul Blood** is the expensive tier, made through a congeal → haunt → re-melt chain
using Create's own heating, pressing, haunting and mixing rather than any new
machine. A trickle path exists by bleeding nether mobs directly, which is enough to
get a player their first prosthetic; the full line is what makes the late game
viable. Tagged separately from normal blood.

**Blood Steel** — iron quenched in blood (250mb) under a spout **Blood Diamond** — a diamond taken through
a blooded spout with a lot of blood (1bucket) and 1 bucket of liquid XP from a spout (like a sequenced crafting). and soul blood netherite which takes 1B of soul blood from a spout and 1B of liquid XP from a spout in sequenced crafting setup 

**Blood Backtank** — a direct parallel to Create's Copper Backtank. Worn on the
chest, refilled from a station via pipes, with a gauge styled to match Create's own. used as a battery for prosthetics and cybernetics which run off the 2 liquids


**Freshness.** Carcasses decay over time, and cold keeps them. A carcass in a cold
chain stays fresh indefinitely; one left in a field degrades in quality. This should
work with Create's own cold-adjacent machinery and be compatible with other addons
that do bulk freezing.

---

## Armor

Mix-and-match, built from what the butchery line produces:

1. **Mangler scraps** give the base material — different mob families, different base
   stats.
2. **Hide** modifies an existing piece rather than being a piece of its own.
3. **An organ** adds one special ability. Every mob has at least one; some have
   several.
4. All three can come from **different mobs**, freely combined.
5. Wearing a full set built from a **single** mob grants an extra bonus with a
   drawback attached — an optional purity bonus on top of the flexible system, not
   the system itself.

The per-mob organ list and what each ability does is deliberately deferred until the
base system works. Each mob will have a specific cool ability for one of the 3 things, this will make making different combinations almost endless, with tiers that can upgrade the base armor points using blood iron - blood diamonds - and soul blood netherite

---

## Self-augmentation

The highest-risk system in the mod. **Read this constraint first:** the failure mode
this design exists to prevent is a player who amputates a limb, can't afford to
finish, and is now permanently crippled with no way forward. Every rule below traces
back to that. Don't relax any of them for difficulty.

**It's a slot system, not damage.** Arms, legs, eyes, heart. Amputation doesn't hurt
you — it *opens a slot*. Empty slot is a penalty, filled slot is power. That framing
is what keeps it recoverable and reads as progression.

Limb state persists through death. Resetting on respawn is miserable and is not
happening.

**The safety floor ships before the thing it protects against.** A Crude Prosthetic
made of iron, leather and bone, craftable at any point in progression with no nether
requirement, removes the empty-slot penalty entirely and grants zero bonus. Build
this *before* building amputation. Don't ever ship a version where you can cut a limb
off and can't get back to baseline. these crude prosthetics just return normal functioning with no extra benefit

**Amputation is a prepared ritual, never a field action.** The player has to be
seated at the surgery station with a special surgeon minion (when making a minion with a villager head as a component you can give it the job of surgeon, this will also work with pillager heads)
when doing surgery it will bring you to an outside view where you are looking at yourself from a small amount above, and select augments in a new UI. the surgeon when asked to cut stuff leaves a ragged stump that costs more to fit later.


**Empty-slot penalties should be interesting, not a health shave** — an empty arm
means no offhand and slow swings, an empty leg means no sprinting, an empty eye means
reduced vision. A heart can't be removed while empty; replacement is simultaneous.

**Decay is use-based, never wall-clock.** Organic prosthetics accrue necrosis from
swinging, mining and running — never from time passing while you're logged off.
Perfusing with normal blood clears it, cheaply and routinely. At maximum the limb
stops giving its bonus and applies a small penalty; it never falls off and never
kills you. Restoring it costs Blood. You can also have brass varients called cybernetics which are crafted using brass and give each limb or augment a special ability (we can figure these out later) 
**Don't cap the number of augments.** Each one raises the player's global upkeep. Six
replaced limbs demands a serious farm, and someone who has built that farm has earned
the right to be a monster.


---

## Minions

Assembled from parts the butchery line already produces, Organic minions are powered with blood and must have access in their pathfinding to a Blood trough to recharge, and cybernetic minions are animated with Soul Blood, charge using a soul blood canister (empty canister in spout with soul blood for soul canister) these can be automatically placed somehow in the cybernetic minions by some sort of charging station that is different from the blood trough. the cybernetic minions should have some sort of special ability the organic ones cannot do, and same vice versa to keep the two seperate but neither should be more OP then the other.

Each part decides something: the **torso** sets size and health, the **head** sets
behaviour (specific behavior can be set later), the **arms**
set attack type, the **legs** set movement (spider legs climb, rabbit legs are fast,
horse legs are rideable), and an optional **organ** adds one special.

 **Never permanently destroy a minion through neglect.** Someone who
spent an hour assembling a ravager-torso creature must not lose it to an unattended
weekend. instead they just "power down" wherever they are and lay on the ground

put a cap in the config but default is no cap

**On jank:** mismatched limb scaling, uneven gaits, a cow torso walking on rabbit
legs — do not smooth this out. The wrongness is the appeal. Don't spend engineering
time on blending or IK. It should look jank, but not buggy

---

## Cybernetics

The second endgame, and it must feel different from flesh rather than better:


| Identity | You become the monster | You become the machine |

There's a natural pipeline here worth keeping: **limbs that were never degloved feed
the organic path; degloved limbs feed the cybernetic one.**

**Build the throttle system first, once, as shared infrastructure.** Every module has
a genuinely useful low-cost baseline plus a player-controlled ramp that trades Soul
Blood for power — hold to spool up, release to fire, with a gauge, a rising audio
pitch, and visible state on the limb itself. The drain curve is steeply nonlinear:
full throttle is something you do for ten seconds to solve a problem, never a
sustainable mode. Player-controlled cost is the entire identity of this tier.

Brass limbs are chassis with module slots. Modules swap at the surgery station with
no amputation needed. The ones I want:

- **Grappling Spool** — reuses the meat hook tether. Light target gets pulled to you;
  heavy target pulls *you* to it. Same button, opposite outcome, and hitting the wrong
  thing must be genuinely bad.
- **Rotational Coupler** — your arm becomes a kinetic source, with a shaft that
  physically extends and connects to the machine. This is the mod's best visual and
  worth real animation time. Enough baseline power for a handheld drill or a single
  press; ramped much higher at a steeply disproportionate cost.
- **Piston Ram** — a knockback strike, but the interesting use is hitting the ground
  to launch yourself. Pairs with the stabiliser below; two modules that only fully
  work together is exactly what a slot system is for.
- **Magnet Coil** — item vacuum, ramped for radius. At high ramp it pulls *carcasses*,
  which ties the endgame back to the butchery core.
- **Analytical Lens** (eye) — read Create stress, RPM and machine state through walls.
  Flat low cost, a utility rather than a throttle module.
- **Gyroscopic Stabilizer** (leg) — no fall damage,  Cost scales with fall distance, and running out mid-fall means you take
  the damage. Checking your gauge before stepping off a cliff is good tension.
- **Barometric Vent** (leg) — brief hover, ramped for duration.

we can add more or tweak these later but build the functionality for now

**Explicitly rejected:** a module that toggles redstone links from anywhere. It solves
a Create *build* problem with a body part. Don't add it.

**Set bonuses:** four or more flesh grafts, or four or more brass modules, each grant
their own bonus. Mixing gets neither bonus and **no penalty** — I don't want to punish
experimentation.


---

## Decoration

- **Gut Chain** — behaves like a vanilla chain but mounts on chain conveyors, so it
  moves.
- **Wall-mounted Meat Hook** — accepts any carcass or body part as a rendered
  attachment. One block, infinite variety from what's hanging on it.
- Ribcage arches, bone piles, blood-stained variants of Create's cladding.

---

## Environment interactions

- A Create fan blowing over a carcass hanging above a Bleeding Rack should make it
  drain faster.
- Cold air keeps carcasses fresh; this should play nicely with other addons' bulk
  freezing.
- Carcasses on belts, in vaults, on contraptions — all should behave as ordinary
  items, because that's what they are once they leave a player's hands.

---

## How I want you to work

- **Follow Create's own patterns, and read Create's source rather than guessing at its
  API.** Reuse its recipe systems, its stress and fluid systems, its processing
  framework. Don't reinvent anything Create already does.
- **Ask before adding a dependency.**
- **If something here conflicts with how Create actually works, stop and tell me**
  rather than working around it.
- **Verify things by running them, not by reasoning about them.** Automated tests for
  the things that go quietly wrong, and actually look at anything visual on screen
  before calling it done — every visual constant in this mod was a guess until someone
  looked at it.
- Build in vertical slices — one complete path working end to end and testable in
  game, before breadth. Placeholder art is fine early; don't spend time on textures.
- Tell me plainly when something doesn't work. I'd rather hear "this is unstable and
  here's why" than see a green test suite over a broken feature.

as for visuals and sound effects I want everything squishy, gross, gory, bloody, you know

Dependencies for now will be Sable (this is what i want the ragdoll physics to come from) and create, if you need anything else ask.

Any questions? if you are confused, or are unsure of something ask. I want everything rock solid from the get go so ask all your questions to ensure you get a proper vision
