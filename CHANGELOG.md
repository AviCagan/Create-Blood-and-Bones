# Changelog

## Unreleased (development builds on `claude/chat-session-ipebci`)

Everything below is in development builds only; the art is placeholder (see the README).

- Meat Hook kills leave physics carcasses; dragging, resting (a still carcass folds into one body
  and unfolds when disturbed), rot, and the death handover with no gap.
- Butchery: Cleaver, Flensing Knife, carried pieces, data-driven yields that spoil with rot.
- Cut limbs leave raw wounds, bone showing, on the stump and on the piece, and pour blood for a while.
- Scraps of meat fly off when a limb is cut through or a piece is butchered or ground.
- Carcasses land with a wet thud, louder and deeper for heavier, faster falls; a hard landing splats blood.
- The mod's own sounds with subtitles ("Carcass thuds", "Bone snaps", "Blade falls"...), playing
  vanilla sounds for now; a resource pack can replace them.
- Cleavers and the Flensing Knife come away bloody from a cut or a hit and stay so for five minutes.
- 79 rigged vanilla mobs with variants, the wither and the pufferfish included; babies of all 36
  kinds that have them (calves, piglets, lambs, chicks, pups, kittens, cubs, bunnies, foals on their
  long legs, crias, baby zombies and villagers...) shaped as the game draws them; hook kills drop the
  mob's gear and inventory.
- Blood and Soul Blood fluids, the Bleeding Rack, fan-boosted bleeding, slower rot once bled. Nether
  mobs (piglins, hoglins, zoglins, striders) drain Soul Blood straight into the rack.
- Blood stains on the ground (they squelch underfoot) from kills, cuts, drag trails and uncaught bleeding; they dry, fade
  and wash off in rain, and bloodless mode hides them. Bloodless mobs no longer spray blood.
- Shackle Hook and Shackle Trolley on Create chain conveyors; trolleys queue a body's length apart.
- Mangler, Guillotine, Beheader and Deglover kinetic machines, each with a filter slot on its top
  edge: a spawn egg, a carcass piece or a Create filter picks which carcasses it works on.
- Blood Steel, Blood Diamond, Soul Blood recipes; Blood Steel Cleaver.
- Spit Roast, Specimen Jar and Butcher's Hook (a wall hook to hang a piece on; a fresh piece drips
  blood onto the floor below until it runs dry).
- Butcher's Table: lay a carried piece on it and chop it up with a Cleaver. Automatable: a funnel or
  hopper puts pieces on it and a Deployer holding a Cleaver chops them.
- Bloody Casing: andesite casing filled with blood, joining up like Create's casings.
- Gut Chain: a string of guts hung like a chain (three offal make three); plain cord in bloodless mode.
- Both hooks ride Create contraptions with the block they hang from.
- Carcass pieces in Create's Attribute Filter: sort by mob, by part (head, body, limb, tail), fresh or
  rotting, skinned, or from a baby.
- Item descriptions, JEI pages (a Butchery page per mob, sent to players on servers too), Ponder
  scenes, advancements.
- A `bloodandbonesBloodless` game rule forces bloodless mode for everyone; bloodless mode also shows
  skinned carcasses pale and the hook in a carcass clean, and rewords names and descriptions (Blood
  Steel reads as Essence Steel, the Bleeding Rack as the Draining Rack).
- Flies gather over rotting carcasses, and maggots squirm over ones nearly gone.
- An in-game settings screen (Mods, Blood & Bones, Config) for bloodless mode and the rot settings.
- Rotten carcasses fall apart after a day; a server config sets the rot speed and the falling apart.

### Known gaps

- Slimes and magma cubes smaller than size 4 still die normally.
- Not rigged: the ender dragon and tropical fish.
- Trolleys cannot ride chain conveyors that sit on a Sable sub-level (a moving ship).
- A carcass hanging from a hook that a Create contraption moves falls off rather than going along.
- The drag tests used to miss their mark by a hair about once in thirty runs (a body still swinging at
  the one tick they looked). They now take the closest the hooked point came over the last second,
  which should end that; not yet proven over many runs.
- All art is placeholder (see the README).
