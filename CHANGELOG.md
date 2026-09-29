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
- The Meat Hook takes the part you aim at, on a carcass lying still too, but never through a wall
  or a ship's side. Hook the head and it follows head first. Hook a cow by a hind leg and walk off
  and it comes round rear first; about half the animals do so far (a sheep or a pig still goes on
  head first).
- The killing blow lands where it hits: struck in the flank most carcasses go down on their side,
  away from the blow; struck from behind one pitches onto its nose; struck in the face its head
  snaps back without being flung off. Heads loll.
- Hit a hung carcass and it swings. A punch is the same push whatever it hits, so a chicken is
  knocked flying and a ravager barely moves. Cut a leg off a hung carcass and it hangs a little
  differently (on most animals only a little, so far).
- What you drag never pushes you: walk into it and you pass through it, where it used to carry
  you off.
- Skeletons weigh as bone and golems as iron plate, heavier than flesh for their size.
- Dragging slows you by what is actually on the hook, from about 5% for a chicken to 55% for a
  ravager; a severed leg costs what a leg weighs, not what its whole animal did.
- A dead spider lies still at last: a carcass whose limbs only twitch where it lies now folds into its resting form
  after five seconds, as a still one does, so it stops costing the server anything.
- A carcass in the air of a Create: Dragons Plus freezing fan (a fan blowing through powder snow) does not
  rot. Other addons' freezing fans and freezers join through Dragons Plus or a tag.

### Butchery

- Cleaver, Flensing Knife, carried pieces, and data-driven yields that spoil with rot.
- Cut limbs leave raw wounds, bone showing, on the stump and on the piece, and pour blood for a
  while. Scraps of meat fly off when a limb is cut through or a piece is butchered or ground.
- Cleavers and the Flensing Knife come away bloody from a cut or a hit and stay so for five minutes.
- A carried piece's tooltip says how it is keeping (fresh, going off, rotting), and whether it is
  skinned or from a baby.
- Butcher's Table: lay a carried piece on it and chop it up with a Cleaver. Automatable: a funnel
  or hopper puts pieces on it and a Deployer holding a Cleaver chops them.
- The yield gap: by hand (a Flensing Knife or Cleaver in your own hand, or a butcher minion's) you get about
  half of what a machine gets, and now and then a cut is botched and a piece lost. A Deployer at the Butcher's
  Table or the Surgery Table and the Deglover get all of it. The Mangler keeps the least meat and bone, whatever
  the mob, but gives armour scraps and whatever of the mob's own drops its butchery does not already give (a cow's
  leather, a zombie's rare iron, a skeleton's arrows). All of this is data (`data/bloodandbones/butchery_path/`).
- A new attribute, Butchery Yield, scales what your hand gets out of a carcass. The Keen Butcher trait raises it.
- The Flensing Knife is held on a carcass: it saws back and forth, a stroke every half second, and lets go
  when the hide is off.
- The Butcher's Table also chops a loose piece lying on its top, so a body too heavy to carry can be dragged
  onto it.
- The Surgery Table's Surgical Rig takes a carcass all the way down: its organs, one a cut, then its hide, then its
  limbs, then its meat and bone. It works a piece laid on it or a carcass lying on it (not one hanging over it). It
  is the slowest way: every cut takes a second and a half, by hand or by a Deployer. By hand you get the organs
  but only a hand's share of the rest; a Deployer holding a Cleaver gets all of it. Each organ comes out once,
  however the part got to the table.
- The Surgical Rig's organs are the organ data's: every organ a mob's part holds comes out at the rig, one a cut,
  before the hide (a cow's rumen as well as its heart, lungs, stomach and eyes; a skeleton's marrow, dry), and a
  carcass folded to rest on the table gives them up without being unfolded.

### Blood

- Blood and Soul Blood fluids, the Bleeding Rack, fan-boosted bleeding, slower rot once bled. A rack
  only takes one kind: blood waits in the body rather than mixing.
  Nether mobs (piglins, hoglins, zoglins, striders) drain Soul Blood straight into the rack, and
  their spray, drips and stains are its dark teal.
- Blood stains on the ground (they squelch underfoot) from kills, cuts, drag trails and uncaught
  bleeding; they dry, fade and wash off in rain. Bloodless mobs never spray blood.
- Blood Steel, Blood Diamond and Soul Blood recipes; the Blood Steel Cleaver.
- Soul Blood in bulk: a Diesel Generators Basin Lid on a basin of blood sets it into Congealed Blood, a fan
  through soul fire haunts it into a Soul Clot, a superheated Mechanical Mixer melts it back into soul blood
  (200 mB from 250 mB of blood). The old one-step mixing and fermenting recipes still work but give a tenth.
- The Blood Diamond is a sequenced assembly: a diamond through a Spout of a bucket of blood, then a Spout of a
  bucket of Create Enchantment Industry's liquid experience.
- Soul blood has its own tag (`c:soul_blood`). Everything that takes blood or soul blood takes any fluid in
  `c:blood` or `c:soul_blood` (recipes, implants, perfusion, minions, the Surgery Table), so other mods' blood
  works. Liquid experience is asked for by `bloodandbones:liquid_experience`, fluids at a point a millibucket
  (Create Enchantment Industry's), not by `c:experience`, which counts 20 mB a point.
- All recipes are generated by datagen; processing recipes moved to Create-style ids (`filling/blood_steel_ingot`).

### Machines and logistics

- Mangler, Guillotine, Beheader and Deglover kinetic machines, each with a filter slot on its top
  edge: a spawn egg, a carcass piece or a Create filter picks which carcasses it works on.
- The machines' filters are asked about each part they could take: a Guillotine with an Attribute Filter set
  to "is a carcass hind leg" takes only hind legs, from any mob, and passes the rest over. The Butcher's Table
  and the Surgical Rig carry a filter too, on the edge of their tops.
- The machines move: the Mangler's toothed grinders and the Deglover's rollers turn against each other, the
  Beheader's saw spins, and the Guillotine's blade rises on its rope.
- The Guillotine winds its blade up while it turns and drops it on a redstone pulse, taking one limb whole.
- The Beheader strikes as soon as a head is in reach, so it takes heads off carcasses passing over it on a
  chain.
- The Mangler goes through a carcass fastest: each limb is torn off and ground in one stroke. The Beheader is
  the quickest and cheapest machine; the Deglover costs the most stress and works no faster above 32 RPM.
- Carcass pieces in Create's Attribute Filter by limb: hind leg, front leg, wing, arm, tentacle, neck, and any
  other limb a datapack's slot rules name.
- A Create filter set on the Butcher's Table or the Surgical Rig drops when the table is broken, and comes back
  to you when the rig is taken off.
- Shackle Hook and Shackle Trolley on Create chain conveyors; trolleys queue a body's length apart.
- The Shackle Hook and the Shackle Trolley hoist a carcass up at a walking pace, the trolley waiting on the chain until
  it is up. They used to snap it there in a tick, which threw anyone standing beside it a couple of hundred blocks. A
  hook reloaded part way up goes on hoisting, and a body caught under something is held where it got to (a trolley
  lets it fall).
- A rabbit, or any carcass with a torso that light, comes up to a hook or trolley and hangs still. The belly-out
  spring was too stiff for so light a torso and spun it.
- Carcass pieces in Create's Attribute Filter: sort by mob, by part (head, body, limb, tail), fresh
  or rotting, skinned, or from a baby.
- Both hooks ride Create contraptions with the block they hang from.

### Cooking, display and decoration

- Spit Roast and Specimen Jar (the jar shows one item, any item; a carcass piece pickles in it).
- The Spit Roast takes whole carcasses: right-click it with the Meat Hook while dragging one. It cooks as fast
  as it turns: a Hand Crank slowly, a shaft at 256 RPM eight times faster. Taken off raw, the carcass is set down
  whole again, never handed over. With a Cleaver or the Meat Hook in one hand and a piece in the other, the piece
  goes on the Butcher's Table or the spit first.
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
- Bloody Train Casing, and a small stained palette of Create's cut calcite (cut, polished, bricks, small bricks,
  each with stairs and a slab), spout-filled with blood that runs into the joints.
- The Butcher's Hook takes any body part: carcass pieces, severed limbs, organs, scraps, meat, heads, bones and
  more (an item tag), drawn hanging on the point; fresh parts and meat drip for a while: soul blood from a nether
  mob's, nothing from a skeleton's. A special organ (a Gland) hangs and drips too.
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
  experience (each mod's at its own rate, by Create Enchantment Industry's unit map: a point a millibucket for
  its own, 20 mB a point for the rest), milk clears effects, anything else spills.
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
- The Grappling Spool gives a carcass it reels in time to arrive. One six blocks off often stopped a step short and
  was never handed to the Meat Hook.
- Set bonuses: four or more flesh grafts heal you from what you hit and rot half as fast; four or more
  brass modules make the throttle a quarter cheaper and you hard to shove. Both kinds in one body get
  neither, and nothing worse.

### Carcass armour (parts and traits)

- The Mangler now also grinds every piece into armour scraps that remember their mob and part ("Cow Leg
  Scraps"): more from a bigger piece, half again from a skinned one, half from a rotten one.
- Scraps of one mob make carcass armour: five head scraps a helmet, four leg scraps boots, seven leggings.
  The mob's family sets the material (a cow's is brawn, a rabbit's sinew), and the mob's parts give traits:
  cow boots have Hooves (soul sand and honey no longer slow you), rabbit leggings Springy II (jump higher, fall
  softer), rabbit boots Fall Guard II (half fall damage). Mix mobs freely; a full set of one mob adds its bonus
  and its drawback (the Warren: Springy IV and Light-Boned III, but Frail II and wolves, foxes and cats hunt you).
- A new attribute, Drag Strength, makes dragging carcasses easier (a full cow set gives 45%).
- The grazers' material (cows, sheep, goats, horses, pigs) is called Brawn, not Hide: cow boots of scraps alone
  read "Cow Brawn Boots", and only a fitted hide says Hide.
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
- Soul Netherite Ingot: sequenced assembly, a netherite ingot filled with 1000 mB of soul blood, then
  given a super experience block by a Deployer. The soul netherite tank is a smithing upgrade of the blood
  diamond one.
- A gauge where Create shows a backtank's air, always up while a tank is worn or a powered implant is
  fitted: the tank, what it holds, a bar of the fluid itself, and the implants it runs (dimmed when their fuel
  is not in it). While Create's own air gauge is up it moves a row higher. The tank's item bar is the fluid's
  colour.
- Strap one to a carcass chestplate by crafting the two together: the chestplate carries the tank and
  its fluid, prosthetics run on it, Spouts and Item Drains fill and empty it, and its armour is the
  better of the two. Craft the chestplate alone to take the tank off. If the chestplate breaks or burns,
  the tank falls free, fluid and all.

### Minions

- The sapper: a creeper's head, with a creeper's Powder Sac stitched in, offers a job that walks up to a monster near
  home (or to a banner of the colour you hand it) and blows itself up there, sparing itself and your side; then it lies
  powered down, whole, until it gets blood again. It breaks blocks only where the server lets minions break blocks.
- What a minion holds is drawn: in the hand at the end of its first arm (a sword, a bow, a rod), in front of a villager's
  folded arms, or in the mouth of a head with no hand. A helmet is stretched over whatever head it has (a cow's long skull
  gets a long helmet), and a carved pumpkin or skull sits on it sized to it.
- Lava walkers (strider legs) walk straight across lava to where they are going, as a strider does, never wading in and
  never catching alight from it.
- Wings as arms lift a torso they can carry: a cow on phantom wings flies; on a chicken's it only falls slowly and lands
  unhurt. Drowned, iron golem and undead horse legs walk the bottom of water at their land speed (and a skeleton or zombie
  horse's rider stays on under water); a ghast's tentacles float. Wings beat in the air, and a wolf's tail droops as its
  minion is hurt.
- Mounts: a pig's head is steered with a carrot on a stick and strider legs with a warped fungus on a stick (use it to
  spur it on; the stick wears as on a pig or strider). A camel carries its maker and one more behind; a whole ravager takes
  a saddle and two riders too.
- A carcass remembers more of its mob: a charged creeper, a fox's, frog's or rabbit's kind, a panda's gene, a name. A
  charged creeper's sac blows twice as hard (cut out, it stays charged, and does not stack with a plain one), a snow
  fox's hide shrugs off freezing, a warm frog's legs are fireproof and a cold one's frost-guarded, the killer bunny's
  head is a berserk bodyguard biting for 8, as is a vindicator named Johnny's and a zoglin's, a lazy panda's head keeps
  watch and a weak one's sneezes slime. More of the mobs' own specials are wired: cod and salmon heads fish with their
  mouths, allay and fox heads fetch, an axolotl's hunts what axolotls hunt, a pillager's arms draw bows twice as fast, a
  zombie's arms grab, a zombie villager's head is a shaky surgeon.
- Fixed: a creature's random blink could land where it already stood. A hauler sometimes left a body lying across a
  Bleeding Rack's rim, where it rocked or slid off without bleeding; it now lays it in the middle of the tray and
  steadies it until it lies still there. A minion fighting by a wall or in a corner could stop a block or two short of
  what it went for and never strike.
- A minion with a head wears a helmet (or a pumpkin) its maker puts on it; a zombie's torso in one does not burn by day,
  the helmet wearing for it instead.
- Fixed: a lava-walking minion standing on lava hung the server as soon as it set off anywhere.
- Fixed: a minion closing the last few blocks on what it fought could walk straight into lava, fire or off a drop; a
  lava walker walked over fire and magma as if it were fire-proof; one strider leg (or one spider leg) under a cow was
  enough to walk on lava (or cling to walls), where half the legs are needed; a guard with a creeper's sac in it blew
  itself up on the first monster (only a sapper does now); a cow on drowned or iron golem legs drowned on the bottom (it
  now goes up for air); when a mount's maker got off, the rider behind could ride it away (they get off too); a mount
  steered with a stick counted as standing still. A skull or mob head worn by a minion sits on its head as on a
  zombie's, not inside it. An aggressive panda's head brawls.
- Fixed: a carcass lying on the floor beside a Bleeding Rack sometimes did not bleed into it; a hauler lays bodies on a
  rack gently and makes sure they stay in the tray.
- Fixed: a hauler hopping up onto a Bleeding Rack yanked the body it towed up into itself, and the two were flung off
  the line together; a body that missed the tray was then fetched back by walking straight into it, and given up. A
  hauler's hands now keep to the ground it walks on, not to its feet in mid-hop; it comes round the body to take another
  pass; and it steadies a body on the tray until it lies still, so it bleeds in a few seconds rather than half a minute.
- Rebuilt from carcass pieces: fit an Assembly Frame to the Surgery Table, lay a carcass torso on it
  (or take a whole carcass lying on it with an empty hand, whatever is still attached coming along),
  then stitch on the heads, legs, arms and tails of any mob, one a click. A Cleaver takes the last
  piece back. A bucket of blood wakes it ("It's Alive!").
- Every piece does its own thing, from its mob's data: the torso its size, health and what it carries,
  the head its jobs and bite, the legs how fast and how it moves. A cow on four rabbit legs with a cow's
  head is 15 health, hops at 0.325 and is a herder. Crouch and right-click it with an empty hand to change
  its job; the action bar names the new one.
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
  hits, each in its own style and taking turns: a zombie's grab, an iron golem's fling that throws the
  target up, a spider's sting, a villager's pair of arms that never fights. Flesh mends itself slowly on
  its blood, and keeps the hide traits of up to three of the mobs it is built of (a cow's thick hide is
  armour).
- Brass minions: lay a skinned torso on the frame and build it of skinned pieces, sheathe it in Brass
  Sheathing, and wake it with a Soul Canister (an empty canister filled at a Spout; an Item Drain empties
  it). Brass drains a quarter as fast, shrugs off poison and drowning, and never heals itself (a brass
  sheet mends it). The Charging Cradle, a shaft-driven block, swaps full canisters into brass minions
  beside it and keeps the empties for a funnel or Mechanical Arm to take; stocked with brass sheets it mends them. A low brass minion
  walks to the nearest cradle it can reach that is turning with a full canister and room for the empty.
  Its maker can fit a brass minion with one of the player's cybernetic modules: a Magnet Coil draws items
  in, an Analytical Lens sees through walls, a Rotational Coupler drives a shaft it stands beside (at the
  working rate of soul blood; only into air, never over water, snow or plants, and never into someone
  else's coupler), and more.
- Brass, finished. A brass minion has a filter slot: its maker crouches and right-clicks it with a Create
  Filter or Attribute Filter (or any item), and it picks up, reaps, fetches, herds, hunts and fights only
  what the filter passes, as a funnel would; a spawn egg in the filter names a mob. A crouching Wrench takes
  the filter out (a module now comes out with a standing Wrench). Mechanical Arms work the Charging Cradle,
  putting full canisters and brass sheets in and taking the empties out. A Deployer holding brass sheets
  mends a brass minion as a hand does; no Deployer can hurt a minion or take it apart, whoever placed it.

- Heads and jobs: what a head offers now depends on whose head it was. A villager keeps its profession
  when it dies, and its head offers surgeon and its trade's job (a farmer's farms, a fisherman's fishes, a
  cleric's is a medic, a fletcher's a sentry, a leatherworker's a hauler...); a nitwit's only keeps
  company. Pillager heads offer surgeon and sentry; piglin heads barter, sniffer heads dig. A head whose
  eyes were both cut out at the Surgical Rig loses the jobs that need sight and notices things only four
  blocks off.
- Ten new jobs. Hand your minion something by using it on it (an empty hand takes it back), and it works
  with it; arrows for its bow and healing potions for a medic go in with what it carries, a stack at a time:
  - a sentry holding a bow, crossbow or trident stands its post and shoots monsters, arrows from what it
    carries; its arrows fly through you, your other minions and villagers, and can be picked up where they land;
  - a scavenger fetches items like the one it holds, from up to 32 blocks, and brings them to you when you are
    near home;
  - a herder holding wheat (or seeds, carrots...) walks animals that stray back home, leaving pets and named
    animals be;
  - a fisher with a rod (or a fish's head) fishes by water near home;
  - a hunter kills livestock and game near home, and with a Meat Hook in hand leaves intact carcasses (only where
    mobGriefing is on; a minion wakes to another of its jobs, and is put to hunting by hand);
  - a hauler drags carcasses to the nearest Shackle Hook and hangs them, or onto a Bleeding Rack to bleed;
  - a butcher with a Cleaver or Flensing Knife takes carcasses by home apart by hand;
  - a medic throws splash potions of healing at you, your other flesh minions and villagers when they are hurt;
  - a barterer trades gold from the chest by home as a piglin does;
  - a digger sniffs the grass and moss round home and turns up what a sniffer finds.

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
- Organs you can hold: every special organ (a cow's rumen, a creeper's powder sac, a sniffer's olfactory bulb, a blaze's
  core, about seventy) comes out as a Gland, named for its mob and organ, a tinted sac, gland, bulb, core, bladder,
  spinneret, fat, marrow, gut, heart or eye, wet and bloody (a machined core with a glowing window in bloodless mode).
  A Cleaver at the Surgical Rig takes a piece's organs out one a cut, in order: a torso's heart, lungs, stomach and
  special organ, a head's eyes, a rabbit's hind leg its foot. Some bring more with them (a powder sac spills gunpowder,
  a blaze core blaze powder, marrow bone meal). Mobs with no blood give their core instead of nothing (a skeleton its
  marrow). A carcass too heavy to carry gives up its organs lying over the table (its limbs' too once it has lain still and
  folded; with nothing left in it, a Cleaver clicked on the table is laid there). Each organ fits only the armour
  pieces it says (a powder sac a chestplate, a rabbit's foot leggings or boots), and a minion's organ slot; the one it
  replaces comes back. Rabbit's feet, ink sacs, glow ink sacs and spider eyes that mobs drop count as their mobs'
  organs, and a spider's eyes come out as spider eyes. Tooltips say what an organ gives in armour and in a minion, and
  what is still inside a carcass piece.
- JEI: a Body Parts page per mob (what each part, its hide and each organ gives, on a minion and in armour, and its
  full set), and each organ's fitting shown as a crafting recipe. `/bloodandbones traits explain <mob>` says the same
  in chat, and `/bloodandbones traits dump` writes every mob's traits to a CSV file for balancing.

### Presentation

- Bloodless mode (a client setting, or the `bloodandbonesBloodless` game rule for everyone): no
  blood drops or stains, skinned carcasses pale, the hook in a carcass and the machines clean (and
  the bits that fly off them when broken), blood
  a muddy brown, the Gut Chain plain cord, and names and descriptions reworded (Blood Steel reads as
  Essence Steel, the Bleeding Rack as the Draining Rack, a minion as a construct, a ragged stump as an
  open socket). A folded minion is a clean riveted bundle.
- The mod's own sounds with subtitles ("Carcass thuds", "Bone snaps", "Blade falls"...), playing
  vanilla sounds for now; a resource pack can replace them.
- Bloodless mode finished: carcasses are drawn as plated wrecks of constructs and called wrecks, the Flesh
  Arm, Sinew Leg and Furnace Stomach are the Plated Arm, Cabled Leg and Furnace Hopper, severed parts are
  detached, organs and flesh implants have plated icons, bits knocked off a carcass are steel, not meat, and
  every wet sound is heard as a clank of metal (its own sound event, for real sounds later): the squelches,
  the trait effects' lunges, flings and slaps, and a bleeding wound's drip. A test fails if any game logic reads
  the setting, or if the mod plays any vanilla slime or honey sound itself.
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
- The Meat Hook holds a dragged carcass just in front of you. Walk forward and you walk into it, and it can carry you
  along; step back or sideways while facing it.
- Now and then on a server, a cut-off limb falls through the ground and is lost. It has been seen once and not yet
  reproduced.
- A carcass rising to a hook or a trolley still jostles a player standing right against it, a block or two.
- A hung carcass never settles into its resting form, so every one hanging costs the server a little all the time
  (about 5 ms of each tick for a dozen).
- The drag tests used to miss their mark by a hair about once in thirty runs (a body still swinging
  at the one tick they looked). They now judge the middle value over the last second; the whole
  suite has passed every run made with that check (at least eleven), which is encouraging but not
  proof.
- A patient on the Surgery Table is drawn sitting, not lying. An item held in a missing hand, and
  armour over a missing limb, still show in third person. Nothing is drawn on mobs that have been
  operated on.
- Minions: a held bow, crossbow or trident is used only by a sentry at its post, and a held axe hits no harder; a ridden
  minion cannot jump. A hauler with no room to walk on past a hook or rack gives up and tries again later.
- What each mob's signature still waits for (33 mobs: new mechanisms such as the caravan and homing jobs, the mimic's
  alarm, skull-firing heads) is logged by the signature lint game test. Both of a rabbit's hind legs give a foot.
  Squelching sounds do not turn to clanks in bloodless mode yet.
- A half-built minion frame from a development build before the rebuild loses what had been fitted to it.
- All art is placeholder (see the README).
