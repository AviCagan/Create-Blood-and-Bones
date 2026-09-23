# Changelog

## Unreleased (development builds on `claude/chat-session-ipebci`)

Everything below is in development builds only; the art is placeholder (see the README).

- Meat Hook kills leave physics carcasses; dragging, resting (a still carcass folds into one body
  and unfolds when disturbed), rot, and the death handover with no gap.
- Butchery: Cleaver, Flensing Knife, carried pieces, data-driven yields that spoil with rot.
- Cut limbs leave raw wounds, bone showing, on the stump and on the piece, and pour blood for a while.
- 79 rigged vanilla mobs with variants, the wither and the pufferfish included; hook kills drop the mob's gear and inventory.
- Blood and Soul Blood fluids, the Bleeding Rack, fan-boosted bleeding, slower rot once bled.
- Blood stains on the ground from kills, cuts, drag trails and uncaught bleeding; they dry, fade
  and wash off in rain, and bloodless mode hides them. Bloodless mobs no longer spray blood.
- Shackle Hook and Shackle Trolley on Create chain conveyors; trolleys queue a body's length apart.
- Mangler, Guillotine, Beheader and Deglover kinetic machines.
- Blood Steel, Blood Diamond, Soul Blood recipes; Blood Steel Cleaver.
- Spit Roast and Specimen Jar.
- Item descriptions, JEI pages (a Butchery page per mob, sent to players on servers too), Ponder
  scenes, advancements.
- A `bloodandbonesBloodless` game rule forces bloodless mode for everyone; bloodless mode also shows
  skinned carcasses pale and the hook in a carcass clean.
- Flies gather over rotting carcasses.
- Rotten carcasses fall apart after a day; a server config sets the rot speed and the falling apart.

### Known gaps

- Baby mobs still die normally: a rig has one size, so babies (and slimes smaller than size 4) do
  not become carcasses yet.
- Not rigged: the ender dragon and tropical fish.
- Trolleys cannot ride chain conveyors that sit on a Sable sub-level (a moving ship).
- Bloodless mode does not yet rename items or recolour the blood fluid itself.
- The ghast carcass test failed once in about twenty runs: its heavy body can press a thin tentacle
  a fraction of a block into the floor as it lands.
- All art is placeholder (see the README).
