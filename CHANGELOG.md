# Changelog

## Unreleased (development builds on `claude/chat-session-ipebci`)

Everything below is in development builds only; the art is placeholder (see the README).

- Meat Hook kills leave physics carcasses; dragging, resting (a still carcass folds into one body
  and unfolds when disturbed), rot, and the death handover with no gap.
- Butchery: Cleaver, Flensing Knife, carried pieces, data-driven yields that spoil with rot.
- Cut limbs leave raw wounds, bone showing, on the stump and on the piece, and pour blood for a while.
- Scraps of meat fly off when a limb is cut through or a piece is butchered or ground.
- Cleavers and the Flensing Knife come away bloody from a cut or a hit and stay so for five minutes.
- 79 rigged vanilla mobs with variants, the wither and the pufferfish included; babies of 28 kinds
  (calves, piglets, lambs, chicks, pups, kittens, cubs, bunnies, baby zombies and villagers...) shaped as the
  game draws them; hook kills drop the mob's gear and inventory.
- Blood and Soul Blood fluids, the Bleeding Rack, fan-boosted bleeding, slower rot once bled.
- Blood stains on the ground (they squelch underfoot) from kills, cuts, drag trails and uncaught bleeding; they dry, fade
  and wash off in rain, and bloodless mode hides them. Bloodless mobs no longer spray blood.
- Shackle Hook and Shackle Trolley on Create chain conveyors; trolleys queue a body's length apart.
- Mangler, Guillotine, Beheader and Deglover kinetic machines.
- Blood Steel, Blood Diamond, Soul Blood recipes; Blood Steel Cleaver.
- Spit Roast, Specimen Jar and Butcher's Hook (a wall hook to hang a piece on).
- Bloody Casing: andesite casing filled with blood, joining up like Create's casings.
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

- Babies of kinds without a baby shape yet (horses, donkeys, mules and llamas, which draw their young
  by their own rules) and slimes smaller than size 4 still die normally.
- Not rigged: the ender dragon and tropical fish.
- Trolleys cannot ride chain conveyors that sit on a Sable sub-level (a moving ship).
- A carcass hanging from a hook that a Create contraption moves falls off rather than going along.
- The drag tests occasionally miss their mark: `meatHookDragsByBody` by 0.02 blocks once in about thirty
  runs, and `meatHookDragsByLeg` once (both passed on the reruns).
- All art is placeholder (see the README).
