# Changelog

## Unreleased (development builds on `claude/chat-session-ipebci`)

Everything below is in development builds only; the art is placeholder (see the README).

### Carcasses

- Meat Hook kills leave physics carcasses; dragging, resting (a still carcass folds into one body
  and unfolds when disturbed), rot, and the death handover with no gap. Hook kills still drop the
  mob's gear and inventory.
- 79 rigged vanilla mobs with variants, the wither and the pufferfish included.
- Babies of all 36 kinds that have them (calves, piglets, lambs, chicks, pups, kittens, cubs,
  bunnies, foals on their long legs, crias, baby zombies and villagers...), shaped as the game
  draws them. The smallest slimes and magma cubes leave little carcasses too (a slime ball from the
  slime; no magma cream from the magma cube, as in the game).
- Carcasses land with a wet thud, louder and deeper for heavier, faster falls, on the ground or a
  ship's deck; a hard landing splats blood.
- Flies gather over rotting carcasses, and maggots squirm over ones nearly gone. Rotten carcasses
  fall apart after a day.

### Butchery

- Cleaver, Flensing Knife, carried pieces, and data-driven yields that spoil with rot.
- Cut limbs leave raw wounds, bone showing, on the stump and on the piece, and pour blood for a
  while. Scraps of meat fly off when a limb is cut through or a piece is butchered or ground.
- Cleavers and the Flensing Knife come away bloody from a cut or a hit and stay so for five minutes.
- A carried piece's tooltip says how it is keeping (fresh, going off, rotting), and whether it is
  skinned or from a baby.
- Butcher's Table: lay a carried piece on it and chop it up with a Cleaver. Automatable: a funnel
  or hopper puts pieces on it and a Deployer holding a Cleaver chops them.

### Blood

- Blood and Soul Blood fluids, the Bleeding Rack, fan-boosted bleeding, slower rot once bled. A rack
  only takes one kind: blood waits in the body rather than mixing.
  Nether mobs (piglins, hoglins, zoglins, striders) drain Soul Blood straight into the rack, and
  their spray, drips and stains are its dark teal.
- Blood stains on the ground (they squelch underfoot) from kills, cuts, drag trails and uncaught
  bleeding; they dry, fade and wash off in rain. Bloodless mobs never spray blood.
- Blood Steel, Blood Diamond and Soul Blood recipes; the Blood Steel Cleaver.

### Machines and logistics

- Mangler, Guillotine, Beheader and Deglover kinetic machines, each with a filter slot on its top
  edge: a spawn egg, a carcass piece or a Create filter picks which carcasses it works on.
- Shackle Hook and Shackle Trolley on Create chain conveyors; trolleys queue a body's length apart.
- Carcass pieces in Create's Attribute Filter: sort by mob, by part (head, body, limb, tail), fresh
  or rotting, skinned, or from a baby.
- Both hooks ride Create contraptions with the block they hang from.

### Cooking, display and decoration

- Spit Roast and Specimen Jar (the jar shows one item, any item; a carcass piece pickles in it).
- Butcher's Hook: a wall hook to hang a piece on; a fresh piece drips blood onto the floor below
  until it runs dry.
- Bloody Casing: andesite casing filled with blood, joining up like Create's casings.
- Gut Chain: a string of guts hung like a chain (three offal make three). Used on a Create chain
  conveyor's chain it hangs there and rides the chain round, swinging as it goes; use more on it to
  lengthen it (up to eight links), hit it to take it down.
- Steel Table: morgue table in cold steel. Tables side by side join into one run, with legs only where
  it ends or turns; each holds one item, any item (a carcass piece lies on its back). Funnels and hoppers
  load it.
- Steel Rack: steel shelves, two shelves of two places; right-click its front to put the held item on
  the place you look at, an empty hand takes it back. Funnels and hoppers fill it.
- Ribcage Arch: segments of giant rib that shape themselves by their neighbours (straight in a stack,
  bending in at the top, level across the crown), so stacks and spans build the inside of a ribcage. Wet
  red joints; bleached bone in bloodless mode.
- Bone Pile: bones heaped in layers like snow; use one on a pile to add a layer, two bones back a layer.
- Bloody Brass Casing and Bloody Copper Casing: Create's brass and copper casing filled with blood, as
  the Bloody Casing.
- All of these ride Create contraptions, keeping what they hold.

### The body

- The Surgery Table takes one of two attachments, as the brief has it: a Surgical Rig (an overhead
  arm of lamps and blades) makes it a place to operate and to take organs out of carcasses; an Assembly
  Frame (clamps and a jig) makes it a place to build minions. A bare table does nothing. Operating on
  yourself turns the camera to look at you from a little above.
- Surgery Table: lie on it (empty hand) and a screen shows each limb. A Cleaver laid on the table
  takes one off, and you keep it ("Steve's Arm"); a Peg Leg, a Hook Hand or a severed limb laid on it
  goes where one is missing; a prosthetic unclips. Limbs are only ever lost by choice, and nothing
  can go wrong.
- Amputation is a ritual, as the brief has it: a player's flesh only comes off with a surgeon minion
  (a villager's or a pillager's head, and an arm) awake beside the table. It hacks: the stump it leaves
  is ragged, and fitting anything but a crude prosthetic there later takes a bucket of blood as well (from
  a bucket or a worn backtank). Fitting never needs a surgeon, and a crude prosthetic never needs blood, so
  one can always go on. A surgeon keeps to its table and tends whoever lies on it.
- Stumps show: a limb gone leaves the top of it in your own skin with a raw end; a ragged one is
  longer, torn, with flaps of flesh hanging off.
- Missing parts follow the design brief: an arm gone means no off-hand and swings a quarter slower
  (the main hand always works); a leg gone means no sprinting; an eye gone closes the view in with fog
  (to a few blocks with none); the heart is only ever swapped, never taken out alone.
- Crude prosthetics are the safety floor, of iron, leather and bone: Peg Leg, Hook Hand, Glass Eye,
  Crude Heart, Crude Lungs and Crude Stomach give back normal working and nothing more. An implant on
  the table swaps for one that fits.
- Drawn on the player for everyone: missing limbs gone, prosthetics in their place, in third and
  first person. The body is saved and kept through death.
- Eyes and organs too: a heart, lungs and stomach can be taken out and put back, or swapped straight
  for an implant. No working eye blinds you; no heart leaves you weak and slow (it does not kill); no
  lungs, no sprinting; no stomach, no eating. An empty socket shows where an eye was.
- Organic prosthetics on blood (Flesh Arm, Sinew Leg, Furnace Stomach) and cybernetics on soul blood
  (Hydraulic Arm, Piston Leg, Optic Eye with a glowing lens, Pump Heart, Bellows Lungs) draw a mB or two
  a second from the worn Fluid Backtank, and stop working, as if the part were missing, when it runs
  dry.
- Necrosis, from the brief: organic prosthetics rot from use (swings, running, meals), never from time.
  Blood in the backtank clears it cheaply as you go; rotted through, the part gives no bonus (a small
  penalty) until perfused. It never falls off and never kills. The part greys and greens as it rots.
- Vent Arm: hold use empty-handed to spray the tank ahead of you. What it does comes from a data map
  (`data_maps/fluid/vent_effects.json`): lava burns, water puts fires out, liquid experience gives
  experience, milk clears effects, anything else spills.
- The Surgery Table takes others: with another player or a mob (led onto it on a lead) lying on it,
  an empty hand opens the screen for them and what comes out is yours. A mob missing a leg walks
  slower, one missing an arm hits softer. A carcass piece on the table gives up its organs to a Cleaver,
  one a cut (a body's heart, lungs and stomach, a head's eyes, named for the animal); a Deployer holding
  a Cleaver does it too, dropping them on the table.
- Port Arm and Backtank Port: crouch next to a port with a Port Arm and your tank is the port to the
  pipes, so a pump fills or empties it.
- Cybernetic modules and the throttle, from the brief. The Hydraulic Arm and Piston Leg take two modules
  each and the Optic Eye one, fitted at the Surgery Table with no cutting (lay the module on the table;
  a Wrench on the table takes one out). Hold the throttle key (R) to spool the chosen module up, and
  let go to fire it; V picks the next module. Holding costs soul blood steeply: next to nothing on a
  tap, 150 mB a second at full, and a dry tank sputters. A gauge by the crosshair, a whine that climbs
  in pitch, and the brass limb glowing along its seams show how far it is spooled.
- The seven modules: Grappling Spool (reels light things in, and you in to heavy ones; a wall at speed
  hurts), Rotational Coupler (look at a shaft's end and a rod reaches out of your arm and drives it, 16
  to 256 RPM), Piston Ram (a knockback strike, or a blow to the ground that launches you), Magnet Coil
  (items drift to you; held, from further, and carcasses too near the top), Analytical Lens (counts as
  Create's goggles and reads machines through walls), Gyroscopic Stabilizer (no fall damage, paid in
  soul blood by the block) and Barometric Vent (a puff up and a slow hover).
- Set bonuses: four or more flesh grafts heal you from what you hit and rot half as fast; four or more
  brass modules make the throttle a quarter cheaper and you hard to shove. Both kinds in one body get
  neither, and nothing worse.

### Carcass armour (parts and traits)

- The Mangler now also grinds every piece into armour scraps that remember their mob and part ("Cow Leg
  Scraps"): more from a bigger piece, half again from a skinned one, half from a rotten one.
- Scraps of one mob make carcass armour: five head scraps a helmet, four leg scraps boots, seven leggings.
  The mob's family sets the material (a cow's is hide, a rabbit's sinew), and the mob's parts give traits:
  cow boots have Hooves (soul sand and honey no longer slow you), rabbit leggings Springy II (jump higher, fall
  softer), rabbit boots Fall Guard II (half fall damage). Mix mobs freely; a full set of one mob adds its bonus
  and its drawback (the Warren: Springy IV and Light-Boned III, but Frail II and wolves, foxes and cats hunt you).
- A new attribute, Drag Strength, makes dragging carcasses easier (a full cow set gives 45%).
- Chestplates: six torso scraps of one mob with two arm scraps of any mob on top for the shoulders (a mob with no
  arms, like a cow, uses more of its torso scraps).
- Fitting: craft a piece with hides of one mob (one for a helmet or boots, two for leggings, three for a
  chestplate; two leather and a raw cow hide are all a cow's) for that mob's hide traits, or with an organ cut out of
  a mob on the Surgery Table (eyes in a helmet, a heart or lungs in a chestplate, a stomach in a chestplate or
  leggings). What it replaces comes back as it went in. Raw hides skinned off a carcass, and organs cut out of one or
  out of a live mob, now remember their mob; leather counts as a cow's, rabbit hide as a rabbit's, and a parrot's
  feathers as the parrot's. A hide or organ of another mob breaks a full set.
- Two pieces no longer combine in a crafting grid or on a grindstone (that made a blank piece and lost everything in
  both); scraps of the piece's own mob, and nothing else, mend it on an anvil.
- Tiers: a Blood Steel Ingot, then a Blood Diamond, then a Soul Netherite Ingot, each giving more armour, toughness
  and durability; soul netherite does not burn. A full hide set at tier 3 matches netherite.
- Mechanical Crafters fit hides, organs and tiers too, but refuse a swap rather than lose what would come back.

### The Fluid Backtank

- Seven tiers worn in the chest slot, each with its own armour: copper (2 buckets), gold (3), iron
  (4), diamond (6), blood steel (8), blood diamond (16) and soul netherite (32). Holds any fluid.
- Set down as a block, pipes fill and empty it from any side, and it keeps its fluid when broken.
  Spouts fill it and Item Drains empty it in the hand. Drawn on the wearer's back.
- Soul Netherite Ingot: sequenced assembly, a netherite ingot filled with 1000 mB of soul blood and
  pressed with a super experience block. The soul netherite tank is a smithing upgrade of the blood
  diamond one.
- Strap one to a carcass chestplate by crafting the two together: the chestplate carries the tank and
  its fluid, prosthetics run on it, Spouts and Item Drains fill and empty it, and its armour is the
  better of the two. Craft the chestplate alone to take the tank off. If the chestplate breaks or burns,
  the tank falls free, fluid and all.

### Minions

- Rebuilt from carcass pieces: fit an Assembly Frame to the Surgery Table, lay a carcass torso on it
  (or take a whole carcass lying on it with an empty hand, whatever is still attached coming along),
  then stitch on the heads, legs, arms and tails of any mob, one a click. A Cleaver takes the last
  piece back. A bucket of blood wakes it ("It's Alive!").
- Every piece does its own thing, from its mob's data: the torso its size, health and what it carries,
  the head its jobs and bite, the legs how fast and how it moves. A cow on four rabbit legs with a cow's
  head is 15 health, hops at 0.325 and starts as a courier. A villager's head makes a farmer. Crouch and
  right-click it with an empty hand to change its job.
- Drawn as what it is: each piece in its own mob's skin, raw where it was cut, legs walking (or a
  hopper bounding), head looking round. The table shows the minion being built.
- It runs on blood, more when it moves, works or fights. Low, it walks to the nearest Blood Trough it
  can reach (four buckets of blood, filled by bucket, Spout or pipe) and drinks. Empty, it lies down
  where it is, alive: only a player can hurt it and mobs ignore it. Blood wakes it again. Its maker can
  fold one that is down into a Dormant Minion to carry, and set it down to work somewhere new; a folded
  minion never despawns, burns or breaks, and comes back up out of the void. A killing blow collapses it
  the same way, and so does falling out of the world (a server setting can make it fall apart into its
  pieces, or die, dropping what it carried either way). A server can cap minions per player (no cap by
  default); its maker's Cleaver on one lying down on an Assembly Frame table takes it back apart into a
  frame there, freeing its place.
- A minion never turns on its maker, and one that never fights (a villager's pair of arms) no longer
  stands its ground when hurt. It drinks only from a trough it can walk to, never through a wall, and
  gives up on a trough, cradle, crop or chest it cannot get to rather than standing by it.
- A minion from an older build falls apart, dropping what it carried, the backtank it wore and the
  implants that were in it.
- Legs set how it moves, as the brief has it: spider legs climb walls (a hungry minion goes straight over
  a wall to its trough); horse legs under a heavy enough torso take a saddle, and its maker rides and steers
  it (its rider sits on the saddle, and riding costs what walking does); a flying torso (a bat's, a
  blaze's) flies, its legs dangling, and goes to a trough from the air when it runs low. Arms set how it
  hits, each in its own style and taking turns: a zombie's punch, an iron golem's fling that throws the
  target up, a spider's sting, a villager's pair of arms that never fights. Flesh mends itself slowly on
  its blood, and keeps the hide traits of up to three of the mobs it is built of (a cow's thick hide is
  armour).
- Brass minions: lay a skinned torso on the frame and build it of skinned pieces, sheathe it in Brass
  Sheathing, and wake it with a Soul Canister (an empty canister filled at a Spout; an Item Drain empties
  it). Brass drains a quarter as fast, shrugs off poison and drowning, and never heals itself (a brass
  sheet mends it). The Charging Cradle, a shaft-driven block, swaps full canisters into brass minions
  beside it and keeps the empties for a hopper; stocked with brass sheets it mends them. A low brass minion
  walks to the nearest cradle it can reach that is turning with a full canister and room for the empty.
  Its maker can fit a brass minion with one of the player's cybernetic modules: a Magnet Coil draws items
  in, an Analytical Lens sees through walls, a Rotational Coupler drives a shaft it stands beside (at the
  working rate of soul blood; only into air, never over water, snow or plants, and never into someone
  else's coupler), and more.

### Parts and traits

- Every one of the 79 rigged mobs now has parts that do their own thing, from 11 body shapes, 27 families
  and 16 overlays keyed off tags (so a modded undead or aquatic mob joins in by the tags its author already
  set). About 100 traits, and 15 armour materials each with its own look (chitin, bone, ember, sculk, golem
  plate, scale, feather...), bloodless versions included.
- Minions now have their parts' traits: a cow on its own legs is sure-footed and steps up blocks, a zombie torso
  shrugs off poison, a fish's tail swims faster. An organ cut out of a mob can be stitched into a minion on the
  Assembly Frame (a Cleaver takes it out again) for that organ's special.
- The Organ Ability key (G by default, "Core Ability" in bloodless mode) fires the active abilities of the carcass
  armour you wear, one piece after the next (helmet, chestplate, leggings, boots) with each press. Each has its own
  cooldown, shown on the piece, and some cost blood from your backtank or the tank strapped to your chestplate (or 3
  hunger without blood). A
  minion fires its organ's ability at what it fights, paying from its own blood. (The abilities themselves come
  with the next traits.)
- Traits work only where they belong: armour traits on players, minion traits on minions; a mob that picks up
  carcass armour gets its armour points only. Traits can now depend on your health, your blood, what is near you
  and how long you have been dry, and can act when you land or make a kill.
- Server settings for traits: their strength, and effect types to switch off.
- Hold Ctrl over a carcass piece to read what its traits do, for traits that say.
- Movement and blast traits: wall climbing (one piece clings, two climb while you hold jump), gliding on a carcass
  chestplate for hunger instead of durability, bouncing back up from long falls, walking on powder snow, silent steps,
  quick draw, the ender mask and piglin kinship, dodging blows and sending projectiles back, leaping, dashing,
  charging, warping where you look, a wind burst with a soft landing, blinking away when hurt, and the creeper's powder
  sac: a blast that spares its wearer or, in a minion, a self-destruct that leaves it powered down, never destroyed.
  Minions can walk on lava, trample through leaves and grass, and blink back to a maker they fall behind. Blocks break
  only where mobGriefing and the new `minion_block_damage` server setting (off by default) both allow it.
- Ranged abilities and on-hit traits: fireballs (small and great), a web shot that leaves a temporary web, spit, a
  shulker's bolt, the warden's sonic boom and the guardian's beam (charged, drawn as a beam), evoker fangs, a snowball
  volley and a frog's tongue; hits that web, bleed, fling, blink away, set alight or steal what a mob holds; spikes and
  embers that hurt what hits you; lava that crusts over under a lava wader's feet and melts back, and water that freezes
  under a frost path. Minions with skeleton arms keep their distance and shoot arrows. The new Bleeding effect drips and
  stains the ground ("Leaking", with grey sparks, in bloodless mode, and on anything with no blood). A squid organ's ink
  now puffs out a real cloud.
- Social traits: kin (zombies, skeletons, raiders or piglins take you for one of their own until you hurt one),
  golems that trust and defend you, senses that outline creatures through walls for you alone (echolocation's wet
  click, tremors, the scent of blood, the invisible) and an alert with which way to look when something takes aim,
  pack hunting, auras (a purr that heals your minions, an item magnet, a calm, a roar, a horde called to your
  defence, poison, wither and fatigue), and glowing armour pieces and minions.
- Upkeep traits: flesh minions lay eggs, give milk and stew (use a bucket or bowl on them), grow wool in their sheep's
  colour, spin string, squeeze out ink and bring up honey (growing crops nearby as a bee does), paying blood for each;
  brass makes nothing. Minions mend on their blood (brass never mends itself), take an iron ingot for 25 health, carry
  more (saddlebags, a chest on a beast of burden), hold more blood (a hump) or less efficiently (leaky), eat rotten
  flesh and raw meat for blood, and forage grass, seeds or mushrooms until half full. Armour can make seeds, bamboo and
  mushrooms food, fill you more, poison you on cookies, cheat death once in a while (a minion collapses instead), and
  burn you in the sun, hurt you in water or heat, or slow you once you have been dry too long. A new Blood Upkeep
  attribute scales what implants and minions drink, and the Lean trait lowers it.
- Every mob's own abilities: the traits are now wired into all 79 mobs, their families and their overlays. A cow's
  torso gives milk and its rumen cleanses, a blaze's core throws fireballs and puts out the fire on you twice as fast, a
  spider's spinneret shoots webs and its legs climb walls, an enderman's gland warps you, a warden's core booms, a
  ravager roars, a zombie's heart cheats death and its torso burns in the sun... Every mob has at least one special
  organ (the undead's Rot Gut, the horse's Spleen, the goat's Leap Gland among the new ones), each with a machine-part
  name in bloodless mode. New full sets: Inferno, Walking Bomb, Voidwalker, Mountaineer, Desert Shambler and Colossus;
  the zombie's Shambler and the skeleton's Ossuary are now whole sets of their own, with the undead's inverted healing.
  Thirteen new traits. Traits that only ever worked for players (luck, attack speed, reach, sneaking, mining, gliding,
  powder snow, quick draw, some diets) are no longer given to minions.
- Trait fixes: cooldowns now last through a relog, a trip home from the End or a minion's chunk reloading (an Organ
  Ability's is kept on its piece), and a trait with a condition no longer spends its cooldown when the condition is not
  met (a Spleen's second wind, a cat's morning gift). With no blood in your tank, or soul blood, an Organ Ability costs 3
  hunger instead. Nothing cheats death in the void or on /kill. On-hit traits go off on your blows only, not on thorns
  sent back or a beam. Traits raise speed by at most 40% and jump by 0.3 between them, and keep a minion's health
  within 6 to 150; a minion loaded again keeps the health its traits give it. A minion's blast spares its maker and
  their other minions, and only its maker milks it. Brass takes no healing from its own traits and none of its flesh
  parts' weaknesses. A minion's webs honour mobGriefing and go over grass only where `minion_block_damage` allows, and
  temporary webs ride contraptions. A lava walker is fireproof only on or in lava. Eight more mob signatures: the
  shulker lid's frontal deflect, the phantom's night speed, the Elder Eye's curse on attackers, the hoglin's toss,
  evoker fangs on hit, the Golem Core mending a minion standing still, a zombie torso mending in the dark, and the
  zombie villager's Curable Heart.

### Presentation

- Bloodless mode (a client setting, or the `bloodandbonesBloodless` game rule for everyone): no
  blood drops or stains, skinned carcasses pale, the hook in a carcass and the machines clean (and
  the bits that fly off them when broken), blood
  a muddy brown, the Gut Chain plain cord, and names and descriptions reworded (Blood Steel reads as
  Essence Steel, the Bleeding Rack as the Draining Rack, a minion as a construct, a ragged stump as an
  open socket). A folded minion is a clean riveted bundle.
- The mod's own sounds with subtitles ("Carcass thuds", "Bone snaps", "Blade falls"...), playing
  vanilla sounds for now; a resource pack can replace them.
- Item descriptions, JEI pages (a Butchery page per mob, sent to players on servers too), Ponder
  scenes and advancements.
- An in-game settings screen (Mods, Blood & Bones, Config) for bloodless mode and the rot
  settings; a server config sets the rot speed and the falling apart.

### Known gaps

- Middle-sized slimes and magma cubes (size 2) still split and die normally. Pieces of the smallest
  ones say "From a small one", but the Attribute Filter counts them as "from a baby".
- Not rigged: the ender dragon and tropical fish.
- Trolleys cannot ride chain conveyors that sit on a Sable sub-level (a moving ship).
- A carcass hanging from a hook that a Create contraption moves falls off rather than going along.
- The drag tests used to miss their mark by a hair about once in thirty runs (a body still swinging
  at the one tick they looked). They now judge the middle value over the last second; the whole
  suite has passed every run made with that check (at least eleven), which is encouraging but not
  proof.
- A patient on the Surgery Table is drawn sitting, not lying. An item held in a missing hand, and
  armour over a missing limb, still show in third person. Nothing is drawn on mobs that have been
  operated on.
- Minions: only the cow, rabbit, zombie-shaped and villager heads have their own minion data so far;
  other mobs' pieces work from their body shape's defaults. Of the jobs, companion, courier, farmer,
  bodyguard, guard and surgeon are built; the herder and the rest are still to come.
- Special organs (glands, sacs, cores) are in every mob's data but have no item yet, so they cannot be cut out and
  fitted in survival; the heart, lungs, stomach and eyes can. What each mob's signature still waits for (jobs, movement
  modes, mounts, variants, held weapons) is logged by the signature lint game test.
- A half-built minion frame from a development build before the rebuild loses what had been fitted to it.
- All art is placeholder (see the README).
