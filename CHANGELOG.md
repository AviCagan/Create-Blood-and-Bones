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
- Gut Chain: a string of guts hung like a chain (three offal make three).

### The body

- The Surgery Table takes one of two attachments, as the brief has it: a Surgical Rig (an overhead
  arm of lamps and blades) makes it a place to operate and to take organs out of carcasses; an Assembly
  Frame (clamps and a jig) makes it a place to build minions. A bare table does nothing. Operating on
  yourself turns the camera to look at you from a little above.
- Surgery Table: lie on it (empty hand) and a screen shows each limb. A Cleaver laid on the table
  takes one off, and you keep it ("Steve's Arm"); a Peg Leg, a Hook Hand or a severed limb laid on it
  goes where one is missing; a prosthetic unclips. Limbs are only ever lost by choice, and nothing
  can go wrong.
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

### Carcass armour (parts and traits, first slice)

- The Mangler now also grinds every piece into armour scraps that remember their mob and part ("Cow Leg
  Scraps"): more from a bigger piece, half again from a skinned one, half from a rotten one.
- Scraps of one mob make carcass armour: five head scraps a helmet, four leg scraps boots, seven leggings.
  The mob's family sets the material (a cow's is hide, a rabbit's sinew), and the mob's parts give traits:
  cow boots have Hooves (soul sand and honey no longer slow you), rabbit leggings Springy II (jump higher, fall
  softer), rabbit boots Fall Guard II (half fall damage). Mix mobs freely; a full set of one mob adds its bonus
  and its drawback (the Warren: Springy IV and Light-Boned III, but Frail II and wolves, foxes and cats hunt you).
- A new attribute, Drag Strength, makes dragging carcasses easier (a full cow set gives 45%).

### The Fluid Backtank

- Seven tiers worn in the chest slot, each with its own armour: copper (2 buckets), gold (3), iron
  (4), diamond (6), blood steel (8), blood diamond (16) and soul netherite (32). Holds any fluid.
- Set down as a block, pipes fill and empty it from any side, and it keeps its fluid when broken.
  Spouts fill it and Item Drains empty it in the hand. Drawn on the wearer's back.
- Soul Netherite Ingot: sequenced assembly, a netherite ingot filled with 1000 mB of soul blood and
  pressed with a super experience block. The soul netherite tank is a smithing upgrade of the blood
  diamond one.

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
  fold one that is down into a Dormant Minion to carry. A killing blow collapses it the same way (a
  server setting can make it fall apart into its pieces, or die). A server can cap minions per player
  (no cap by default).
- A minion from an older build falls apart, dropping what it carried.

### Presentation

- Bloodless mode (a client setting, or the `bloodandbonesBloodless` game rule for everyone): no
  blood drops or stains, skinned carcasses pale, the hook in a carcass and the machines clean, blood
  a muddy brown, the Gut Chain plain cord, and names and descriptions reworded (Blood Steel reads as
  Essence Steel, the Bleeding Rack as the Draining Rack).
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
  other mobs' pieces work from their body shape's defaults. Brass (cybernetic) minions, organs in
  minions, and the herder and surgeon jobs are still to come.
- All art is placeholder (see the README).
