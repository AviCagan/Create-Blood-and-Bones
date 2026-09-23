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
  draws them. The smallest slimes and magma cubes leave little carcasses too.
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

- Middle-sized slimes and magma cubes (size 2) still split and die normally, and pieces of the
  smallest ones say "from a baby".
- Not rigged: the ender dragon and tropical fish.
- Trolleys cannot ride chain conveyors that sit on a Sable sub-level (a moving ship).
- A carcass hanging from a hook that a Create contraption moves falls off rather than going along.
- The drag tests used to miss their mark by a hair about once in thirty runs (a body still swinging
  at the one tick they looked). They now judge the middle value over the last second; the whole
  suite has passed every run made with that check (at least eleven), which is encouraging but not
  proof.
- All art is placeholder (see the README).
