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

- Spit Roast and Specimen Jar.
- Butcher's Hook: a wall hook to hang a piece on; a fresh piece drips blood onto the floor below
  until it runs dry.
- Bloody Casing: andesite casing filled with blood, joining up like Create's casings.
- Gut Chain: a string of guts hung like a chain (three offal make three).

### The body

- Surgery Table: lie on it (empty hand) and a screen shows each limb. A Cleaver laid on the table
  takes one off, and you keep it ("Steve's Arm"); a Peg Leg, a Hook Hand or a severed limb laid on it
  goes where one is missing; a prosthetic unclips. Limbs are only ever lost by choice, and nothing
  can go wrong.
- A missing arm uses, places and swings nothing, and blocks break slowly without the main arm. A
  missing leg slows you (60% walking on one) and weakens the jump. A Peg Leg walks at 90%; a Hook Hand
  works the hand and breaks blocks at 70%.
- Drawn on the player for everyone: missing limbs gone, prosthetics in their place, in third and
  first person. The body is saved and kept through death.
- Eyes and organs too: a heart, lungs and stomach can be taken out and put back, or swapped straight
  for an implant. No working eye blinds you; no heart leaves you weak and slow (it does not kill); no
  lungs, no sprinting; no stomach, no eating. An empty socket shows where an eye was.
- Organic prosthetics on blood (Flesh Arm, Sinew Leg, Furnace Stomach) and cybernetics on soul blood
  (Hydraulic Arm, Piston Leg, Optic Eye with a glowing lens, Pump Heart, Bellows Lungs) draw a mB or two
  a second from the worn Fluid Backtank, and stop working, as if the part were missing, when it runs
  dry.
- Vent Arm: hold use empty-handed to spray the tank ahead of you. What it does comes from a data map
  (`data_maps/fluid/vent_effects.json`): lava burns, water puts fires out, liquid experience gives
  experience, milk clears effects, anything else spills.
- Port Arm and Backtank Port: next to a port with a Port Arm, your tank is the port to the pipes, so a
  pump fills or empties it.

### The Fluid Backtank

- Seven tiers worn in the chest slot, each with its own armour: copper (2 buckets), gold (3), iron
  (4), diamond (6), blood steel (8), blood diamond (16) and soul netherite (32). Holds any fluid.
- Set down as a block, pipes fill and empty it from any side, and it keeps its fluid when broken.
  Spouts fill it and Item Drains empty it in the hand. Drawn on the wearer's back.
- Soul Netherite Ingot: sequenced assembly, a netherite ingot filled with 1000 mB of soul blood and
  pressed with a super experience block. The soul netherite tank is a smithing upgrade of the blood
  diamond one.

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
- A patient on the Surgery Table is drawn sitting, not lying. An item held in a missing hand still
  shows in third person.
- All art is placeholder (see the README).
