# For next time

The whole brief was also audited against the code on 29 September 2026: docs/BRIEF-AUDIT.md lists every gap, grouped into work packages, and the decisions only the owner can make.

The owner's list of what to take up next, newest decisions first. Each item says what was decided, what it replaces,
and what is still to be settled together before any code is written.

## 1. Tasks instead of jobs (decided 24 September 2026; designed 29 September 2026; built 29 September 2026)

**The decision.** There are to be no jobs. Any task, from a list we draw up together, can be given to any minion. Some minions do a task better than others because of their own stats, and those stats come from what they are built of.

**What it replaces.** Today a minion's head offers it one to three jobs (docs/PARTS-AND-TRAITS.md 6.4 and 6.9). All of the following goes:
- the jobs list in `MinionStats.JOBS` and the hand rule in `HANDS`;
- each head's `jobs` data, with the villager-profession variants;
- blind heads losing the jobs that need sight;
- the crouch-click that cycles through the offered jobs.

**Summary of the design.** Every point is a recommendation, and the owner can overturn any of them.
- **Sixteen tasks.** The seventeen goal packages become fourteen: companion and bodyguard fold into Guard and Idle, and scavenger folds into Courier. With Idle (stay, or follow) and one new task, Tender (it keeps troughs and cradles stocked and carries blood to fallen minions), that makes sixteen. Five tasks can be done "with me", at the maker's side, as well as at home: Idle, Guard, Hunter, Medic and Courier. (This summary said six; the table in 1.1 has five.)
- **Fitness.** Every minion gets one number for every task, shown as a percentage. 100% is how the task works today, 200% is the best and 10% the worst. The formula is the same for every task: knack × disposition × the task's main stat × the square root of its second stat. The stats are read from the build:
  - pace (legs);
  - sight (head);
  - hands (what it holds with);
  - blow (arms);
  - ranged;
  - toughness (health);
  - carry (torso slots);
  - pull (torso weight).
- **What fitness does.** It makes the handwork faster or slower (a stroke, a catch, a find, a throw, a shot). It also makes the aim truer and the butcher less wasteful, and it lowers the blood used at work: 25 mB a minute at 100%, anywhere from 12.5 to 50. It never beats the brief's own rules. No minion drags a carcass with less slowdown than a player, and no butcher beats hand yields.
- **Impossible only when the body cannot.** A task is shut only when the body lacks the part it needs:
  - a hand to hold a surgeon's blade;
  - anything at all to hold a blade, pick or catch with;
  - anything to strike with;
  - a detonating organ;
  - a nose or paws to dig with.

  The screen says why. Everything else is only poor. A missing tool never shuts a task: the minion waits for one.
- **A screen.** The maker crouches and uses an empty hand on the minion. The screen shows:
  - every task, with its fitness and the reasons for it;
  - where it works (at home, or with me);
  - how far it reaches;
  - a "Home here" button.

  The status line, the Surgery Table's line while building, the surgery screen, JEI and piece tooltips show the rest.
- **The surgeon (the owner's call).** Recommended, and built as the default: any minion with a hand can operate. Its fitness decides how ragged the stump is. A villager or pillager head leaves a stump that costs one bucket of blood to fit later, as now; a worse surgeon's costs two or three. Fitness also decides how fast it tends a patient. The safety floor is untouched.
- **Data:**
  - heads' `jobs` become `knacks` (a multiplier per task);
  - legs may name a grip (paw, hoof, claw, tentacle);
  - each disposition gets a file, and so does each task (its numbers).

  Anything missing counts as 1, so a modded mob works on day one from its attributes and its archetype.
- **Old saves convert:**
  - a job becomes the same task;
  - companion and bodyguard become Guard with me;
  - a ragged stump costs one bucket.

  Old datapacks' `jobs` lists are read as knacks.

**Where it stands: built.** All six stages of 1.10 are built: A (the numbers), B (tasks in the jobs' place, the screen, the wake rule, the status line and old saves), C (the levers: what fitness makes of the work in play), D (the surgeon: the fittest cuts, and its fitness prices the stump), E (the Tender, and the butcher at the Butcher's Table) and F (what is shown elsewhere: the Surgery Table's line, JEI and piece tooltips, `/bloodandbones minion fitness`, and the docs). What each stage does differently from the design, and why, is under "As built" in 1.1, 1.3, 1.4, 1.5, 1.9 and 1.10, and in docs/ARCHITECTURE-PROPOSAL.md 15.18 to 15.21; a review's findings are put right in 15.22, and a second review's in 15.23. Only the surgeon question below is left, for the owner.

**The one question the owner must answer: the surgeon (1.5).** There are two choices:
- any minion with a hand may do the ritual's cutting, badly unless it has a surgeon's head (recommended, and the default as built);
- only villager, illager and witch heads may cut, as the brief's words say.

Either is one data switch: the surgeon task's file says `"needs_surgeon_head": false` (the default, as shipped) or `true` (the brief's letter). To flip it, a datapack puts `{"needs_surgeon_head": true}` in `data/bloodandbones/minion_task/surgeon.json` (a task file is read over the code's defaults field by field, so that one line is enough), or the shipped file's `false` is changed to `true`. Both are built and tested at the table (stage D). This is the owner's call; until the owner answers, the default (any minion with a hand may cut) stands.

### 1.1 The task list

"With me" means the task is centred on the maker, not on home. The minion follows the maker and works round them while the maker is in the same world and within 64 blocks; otherwise it works at home. Reach is the default today's code uses. The maker can set it from 2 up to twice the default: at most 32 for tasks that look for creatures or loose items, and at most 12 for tasks that read blocks (every block in the box is read). Sight still caps how far it notices creatures and loose items, as today.

| Task | What it does | Where | The least it needs | From |
|---|---|---|---|---|
| **Idle** | "Stay" at home: keeps within 4 blocks of home. "Follow" with me: keeps near its maker. It only fights back. | home, or with me | nothing | companion's following |
| **Guard** | At home: patrols its reach and attacks monsters there. With me: follows, and goes for what hurts its maker and what its maker hits, as a tamed wolf does. | home (16), or with me | something to strike with: an arm that is not folded, or a head to bite with (a wing only buffets) | guard, bodyguard, companion's defending |
| **Sentry** | Never leaves its post, and shoots what comes within range. With no ranged attack, it strikes only what comes within reach. | its post (where it stood when set) | something to strike with | sentry |
| **Hunter** | Kills prey near home or near its maker. With a Meat Hook in hand it leaves intact carcasses. | home (12), or with me | something to strike with; the mobGriefing rule on | hunter |
| **Sapper** | Walks to its target, or to the banner it was shown, and blows itself up through its organ. Then it lies powered down. | home (16) | a detonating organ | sapper (bb-organs) |
| **Surgeon** | Keeps by its Surgery Table, does the amputation ritual's cutting, and tends whoever lies on the table. | its table (home, or the nearest within its reach, 6) | a hand | surgeon |
| **Medic** | Throws the healing potions it carries at hurt allies. | home (16), or with me | something to throw with; healing potions | medic |
| **Herder** | Leads strays back within reach of home, using the food it holds. | home (keeps within 8, looks out to 20) | something to hold the food with; food | herder |
| **Tender** (new) | Keeps troughs and cradles near home stocked from the containers there:<br>• blood into troughs, from buckets, or poured from buckets it fills at a tank or a Bleeding Rack's tank;<br>• full canisters and brass sheets into cradles, with the empties taken back;<br>• a bucket of blood or a canister carried to any of its maker's minions lying powered down within reach. | home (8) | nothing: something can always be carried | new |
| **Courier** | Picks up loose items within reach and takes them to the container nearest home, or, with me, to its maker's hands. Holding something, it takes only items like it. A brass minion's filter narrows this further. | home (10, up to 32), or with me | nothing | courier and scavenger |
| **Hauler** | Drags whole carcasses to the nearest free Shackle Hook or Bleeding Rack. | home (24) | nothing | hauler |
| **Farmer** | Reaps ripe crops and replants them. | home (8) | something to pick with | farmer |
| **Fisher** | Fishes still water: with a rod as a player's rod does; without one, by hand, paw or mouth. | home (8) | something to catch with | fisher |
| **Butcher** | Takes carcasses near home apart at hand yields. It also chops pieces laid on a Butcher's Table there (`ButcherTableBlockEntity.chop`, as a Deployer does). | home (6) | something to hold a blade with; a Cleaver or Flensing Knife | butcher, and the table |
| **Barterer** | Trades gold from the container by home using the piglin table. | home (6) | something to pick with | barterer |
| **Digger** | Sniffs up what a sniffer finds in the ground near home. | home (6) | a head (a nose), or paws or claws | digger |

**Merged:**
- Companion's following becomes Idle with me, and its defending becomes Guard with me.
- Bodyguard is Guard with me. That is what a tamed wolf does, and one entry fewer.
- Scavenger is Courier with a sample in hand. The destination is the only real difference, and that is exactly what at home or with me decides. It also gives flesh minions a one-item filter by holding a sample, which the scavenger already allowed.

**New:** Tender, from the brief's troughs and cradles and the rule that neglect never destroys a minion. It is also an early-game loop with no pipes: a Bleeding Rack's tank to a trough, by bucket.

**Considered and left out:**
- **Guarding a Surgery Table.** Guard at home, with home on the table, does it.
- **Feeding a Bleeding Rack.** The hauler already lays bodies on racks. A later step could take a drained body on to a hook.
- **Butchering bodies hanging from a Shackle Hook.** A good addition to the butcher, but check first that hand butchery works on a hooked body.
- **Turning a Create Hand Crank** (for the Spit Roast). It would give flesh minions the kinetic power only brass has, through the Rotational Coupler. That goes against the brief's rule that each kind has what the other lacks.
- **Mining and building.** Minions never break or place blocks, apart from the sapper's blast (spec 6.9).
- **Signature jobs in spec 8.2** (caravan, trader's guard, homing, brute guard, lazy sentry). These become knacks or stay as signature work.

**As built in stage E** (docs/ARCHITECTURE-PROPOSAL.md 15.21), where the Tender and the butcher differ from the table:
- **"The containers there"** are the chests, barrels, shulker boxes and Create Item Vaults within the Tender's reach of home. Never a machine's slots (a Deployer's, a depot's), a trough, a cradle, a table or a furnace: it would take what a Deployer holds or put an empty bucket in a furnace's fuel slot.
- **"A tank"** is a Create Fluid Tank or a Bleeding Rack's tray with a whole bucket of blood in it, as the table says. Never a basin (its blood waits for a recipe) or a trough (it would pour from one trough into another).
- **Brass sheets** go into a cradle up to 16, a new number in the Tender's task file (`"sheets"`). A cradle's sheet slot holds 64, and filling it would take every sheet for one cradle.
- **A fallen minion's bucket spills nothing.** Most flesh minions hold less than a bucket (250 + 1000 × torso volume mB: a zombie's torso 344, a cow's 780), so a bucket poured in whole, as a hand pours one, would spill the rest. The fallen one takes what it holds; the Tender drinks the rest if it is flesh with room, and pours what is left into troughs by home, the nearest to the fallen one first. With nowhere for it all, it does not pour, and its status line says so. A fallen brass minion takes a canister only with room for all of it (brass holds one or two whole, so a fallen one always has).
- **What it carries** it takes as many at a trip as it has room for, so a bigger torso makes fewer trips (carry already counts in its number). What it no longer needs (the empties, what is left over) it puts back into a container.
- **The wake rule takes the Tender** now (stage B skipped it until its goals were built). A whole cow and the cow on rabbit legs wake as Tenders: their best task, Herder, waits for food, and Tender and Courier tie at 146%, Tender first in the list.
- **At the Butcher's Table** the butcher chops only with a Cleaver (the table takes no knife), a stroke a piece at its strokes' pace, and keeps what comes off, at its yield share. Of a carcass and a table, the one nearer home goes first. It goes to a table only with a free slot for each kind of thing the piece gives, else it takes what it carries to the container first; carrying nothing, it chops whatever its room, and what does not fit falls on the table top, as a Deployer's chop leaves it.
- **Found on the way:** a task that stores its takings put them in the container nearest home, even one that takes none of them, and a Butcher's Table (it takes only a piece) by home stood between a butcher and its chest for good. It is now the nearest container that takes some of what it carries.

**Put right after the review of the tasks** (docs/ARCHITECTURE-PROPOSAL.md 15.22):
- **Folded arms under a head bite with it.** The screen showed a whole villager able to guard, by its bite, but its fight goals asked whether an arm strikes, so it never took a target, even when hit. `MinionStats.fights()` is now what the table says: an arm that strikes, or a head. Folded arms with no head still fight nothing.
- **A sapper with no head** finds no target and no banner by sight. It sets itself off at a monster against it (or what hurt it there), as a headless guard strikes only what touches it, and never walks after one. Before, it was offered the task and never did anything.
- **Nothing is picked up twice.** A courier's or farmer's pickup could copy a stack another had just taken (its maker walking over it, or a second courier), since a running goal is ticked between the times it is asked whether to go on. What a minion takes whole is emptied as it goes, and one already gone is let be.
- **A courier with its maker** hands over only what they have room for. What does not fit it keeps, says "its maker has no room for what it brings", and tries again ten seconds later. It never fetches what its maker threw away.
- **The surgeon's reach counts:** with no table at home it takes the nearest within its reach of where it stands (6, up to 12), where it looked only 6 blocks round whatever its reach. It reads its fitness again for each heart it tends, so a nocturnal head tends faster once night falls.
- **A farmer picks up** what it reaped as far as it reaps, and two blocks more, where it looked only 10 blocks from home. It looks along a band of its box at a time, so a farmer set to reach 12 reads no more blocks a second than today's; a Tender asks a Create tank of many blocks once, at its controller.

**Put right after the second review** (docs/ARCHITECTURE-PROPOSAL.md 15.23):
- **A fitter farmer never notices a ripe crop later than a 100% one.** Its bands were sized by its own look, so the fitter it was, the narrower each look's band: a 200% farmer at its own reach of 8 read its box in three looks, 30 ticks, where a 100% farmer read it whole every 20. A band is now as wide as a 100% farmer's at that reach (all the box at 8 or less), so a fitter farmer goes over its box as much sooner as it looks more often. It reads more blocks a second than today's farmer only by that much, twice at most. A poorer one reads as many more a look as keeps it to today's rate.
- **Wings only buffet.** A wing's flap does no harm, so a winged body is shown and fights as its head: it bites, and its wings throw back what it bites. Before, the screen showed its bite and it flapped at its target for ever, hurting nothing. A headless body with only wings (or folded arms) has nothing to fight with: no Guard, Sentry or Hunter, and it strikes nothing that touches it.
- **With its maker, its reach counts.** Idle keeps as far from its maker as its reach, as it does from home: it sets off 2 blocks past its reach and stops 1 short, never nearer than 3 (6 and 3 at its own 4, as before). A guard with its maker goes only for what is within its reach of them, and gives up on one that gets further. Before, both ignored the reach the screen let the maker set.
- **A surgeon with no table in reach says so:** "no Surgery Table within 6 of where it stands", and its status line has it "at home" until it has one, where it said "at its table".

### 1.2 What makes a minion good or bad at a task

**The formula, the same for every task:**

fitness = knack × disposition × main × √second, held between 10% and 200%, or **cannot**.

- *main* and *second* are two of the factors below. Each factor is its stat over a reference value, held between 0.25 and 2. The second counts at half weight (its square root). A task may have no second.
- *knack* comes from the parts' data (1.6); it is 1 by default.
- *disposition* comes from the head (below); it is 1 by default.

**100% is today.** Every lever below turns a fitness of 100% into today's constant. A minion whose factors are all at their reference works exactly as its job does now. Where a lever uses fitness, the fitness is held between 25% and 200%, so nothing is ever more than four times slower.

**The factors.** Stats that traits change are read after the traits.

| Factor | Stat it reads | 1.0 is | Examples |
|---|---|---|---|
| Pace | movement speed: its legs, or the torso's own way of moving | 0.25 | cow legs 0.8, zombie 0.92, rabbit 1.3 to 1.4, a legless crawl 0.48 |
| Sight | how far its head notices things: the head mob's follow range, plus follow-range traits. Blind: 4. No head: 2 (it feels its way). An echolocate or tremor sense gives at least 12. | 16 | most heads 1; vindicator 0.75 (12); pillager 2 (32); zombie 2 (35); villager 2 (48); blind 0.25 |
| Hands | the best thing it holds with, from the task's grip table (below), plus 15% for each more of them past two, up to +60% | a hand | a spider with eight zombie arms 1.6 |
| Blow | its hardest strike times its strike rate (+15% for each striking arm past two). With no striking arm, its bite. Berserk ×1.5. | 2.5 (a zombie arm) | a wolf's bite 0.6, an iron golem's arm 2 |
| Ranged | a bow, crossbow or trident in a hand that fights, or an innate shot from its traits | has one | none 0.25 |
| Toughness | max health | 20 | cow 0.75, iron golem 2 |
| Carry | carry slots, storage traits included | 9 | zombie torso 0.33, cow 1, horse 1.44 |
| Pull | the square root of its torso mob's rig weight (as knockback resistance reads it) | 0.8 (a cow) | rabbit 0.25, horse about 1.3, ravager 2 |

**What each part brings:**

| Part | What it gives |
|---|---|
| Head | Sight, and its senses. A mouth to hold things with, and a nose to dig with. Its knacks, its disposition and its bite. No head: sight 2, the mindless disposition, no mouth. Both eyes out: sight 4. |
| Arms | What it holds with (grip) and how many holders it has; its blows and strike rate; whether it can draw a bow. Folded (pacifist) arms hold things but never strike. Wings hold a little, never a tool. |
| Legs | Pace. On four-legged bodies, their front paws, hooves or claws as a last resort for handwork (new data, 1.6). A front leg is still a leg (docs/ARCHITECTURE-PROPOSAL.md 15.1 item 7). Climbing, swimming and lava walking work by themselves in the world. |
| Torso | Carry, pull, toughness, and its reservoir. |
| Organ | Its special: a detonator makes the sapper possible, an innate shot counts as ranged, and a trait may add a knack (1.6). |
| Tail | Nothing for tasks; its passive works by itself. |

**Grips.** The best thing the build holds with counts. A dash means that grip cannot do that work. If the build has nothing the task's column allows, the task cannot be done.

| Holds with | Tool (surgeon) | Blade (butcher) | Picking (farmer, barterer, medic's throw) | Fishing | Holding one thing (herder's bait, courier's sample) | Carrying (courier, tender) | Nose (digger) |
|---|---|---|---|---|---|---|---|
| hand | 1 | 1 | 1 | 0.35, or 1 with a rod | 1 | 1 | – |
| paw, claw | – | – | 0.75 | 0.6 | 1 | 1 | 0.6 |
| tentacle | – | 0.5 | 0.6 | 0.6 | 1 | 1 | – |
| hoof | – | – | 0.45 | – | – | 0.75 | – |
| wing | – | – | – | – | 0.6 | 0.6 | – |
| mouth (any head) | – | 0.35 | 0.35 | 0.35, or 1 for a head whose data says `no_tool` | 0.75 | 0.75 | 1 |
| nothing (its own body) | – | – | – | – | – | 0.35 | – |

**Dispositions:**

| Disposition | Heads today | Fight | Tend | Fetch | Work | Otherwise |
|---|---|---|---|---|---|---|
| none | elemental, guardian, marine, piglin, spirit, swine | 1 | 1 | 1 | 1 | |
| brave | canid, blob, illager, pillager | 1.25 | 1 | 1 | 1 | |
| berserk | wither, zoglin, the killer bunny, a vindicator named Johnny | 1.5 | 0.5 | 0.75 | 0.75 | |
| territorial | bear, golem, shelled | 1 | 1 | 1 | 1 | Guard and Sentry 1.25 |
| loyal | biped, quadruped, equine, floater | 1 | 1 | 1 | 1 | anything done with me 1.25 |
| docile | grazer, panda | 0.75 | 1.25 | 1 | 1 | |
| meek | villager | 0.5 | 1.25 | 1 | 1 | |
| skittish | bird, fish, fowl, small prey, tentacled, vermin | 0.5 | 1 | 1.25 | 1 | |
| nocturnal | arthropod, arachnid, feline, flier, skyborne | | | | | 1.25 at night, 0.75 by day |
| dim (new: the nitwit) | | 0.75 | 0.75 | 0.75 | 0.75 | |
| mindless (no head) | | 0.5 | 0.25 | 0.75 | 0.75 | |

**Each task's formula, and what its fitness changes in play.** Every task also changes the blood used at work: 25 mB a minute ÷ fitness, with fitness held between 50% and 200%, so 12.5 to 50. Brass still uses a quarter of that.

| Task | Kind | Main | Second | Grips | Cannot when | What fitness changes (100% = today) |
|---|---|---|---|---|---|---|
| Idle | – | – | – | – | never | nothing; shown as "–" |
| Guard | fight | Blow | Toughness | – | nothing to strike with | nothing more: its fights run on its own damage, health and speed |
| Sentry | fight | Ranged | Sight | – | nothing to strike with | time between shots ÷ fitness (bow 1 s, crossbow 1 to 2 s, trident 2 s), never under half; aim spread (vanilla's 14 − 4 × difficulty) ÷ fitness |
| Hunter | fight | Blow | Pace | – | nothing to strike with | nothing more |
| Sapper | fight | Pace | Toughness | – | no detonating organ | nothing more |
| Surgeon | tend | Hands | Sight | tool | no hand | the stump's price (1.5); tending a heart every 5 s ÷ fitness, never under 2 s |
| Medic | tend | Hands | Sight | picking | nothing to throw with | time between throws (3 s) ÷ fitness, never under 1 s; aim spread (8) ÷ fitness |
| Herder | tend | Pace | Sight | holding | nothing to hold food with | how long it waits for a stray (30 s) × fitness |
| Tender | tend | Carry | Pace | carrying | never | how often it looks round (every second) ÷ fitness |
| Courier | fetch | Carry | Pace | carrying (holding, for a sample) | never | how often it looks round (every second) ÷ fitness |
| Hauler | fetch | Pull | Pace | – | never | its slowdown while towing: a player's × 1 ÷ fitness below 100%, at most 90%, and never less than a player's |
| Farmer | work | Hands | Sight | picking | nothing to pick with | how often it looks for ripe crops (every second) ÷ fitness |
| Fisher | work | Hands | Sight | fishing | nothing to catch with | time between catches (30 to 60 s) ÷ fitness, never under a sixth (the Lure floor) |
| Butcher | work | Hands | Blow | blade | nothing to hold a blade with | time between strokes (0.75 s) ÷ fitness, never under 0.3 s; below 100%, its yields × fitness (waste) |
| Barterer | work | Hands | – | picking | nothing to pick with | time looking the gold over (6 s) ÷ fitness |
| Digger | work | Hands | – | nose | no head, paws or claws | time between finds (1 to 2 min) ÷ fitness |

"Every second" is what the courier and farmer jobs did: a one-in-ten chance each time the goal was asked whether to start, which vanilla does every other tick. The first draft of this table read it as every half second. Each look is now a one-in-half-the-ticks chance, as vanilla's own goals take theirs (`Goal.reducedTickDelay`), so the screen's "Looks round every 1 s" is what happens.

Pace, carry and sight already work in the world: a fast minion gets there sooner, a small torso fills up sooner, and a short-sighted head finds less. They count in the number anyway, so the screen tells the truth about who is better. What the game does with the number is limited to what the world cannot already do.

**Power, reservoir, traits, brass:**
- **Power and reservoir.** Neither is in any formula. The world counts them already (a small reservoir drinks more often), and blood at work is the lever.
- **Traits** count through the stats they change:
  - speed, damage, health, follow range, drag strength and storage slots;
  - senses (sight for a blind head);
  - innate shots (ranged);
  - the optional `task_knack` effect (1.6).
- **Brass and flesh** have the same fitness. Each keeps its own specials: brass's filter narrows a courier, farmer, hunter, herder, guard or sentry; flesh keeps its produce.

**Impossible or only poor.** "Cannot" is only for what the body physically lacks, as in the "Cannot when" column, with the reason shown. Everything else is only poor, down to 10%.

A missing item never shuts a task: a blade, a rod, food, potions, gold, a bow, or the mobGriefing rule for hunting. The minion takes the task and waits, and says what for. Where an item would change its fitness, the screen shows the fitness with it ("Hand it a Cleaver: 140%").

The three cases in the old list:
- **Butchering with no arms.** With a head, the blade goes in its mouth: about a quarter of a zombie-armed butcher's fitness, and it wastes most of each cut. With no head either: "Nothing to hold a blade with."
- **Fishing with no head.** A rod in a hand fishes by feel: sight 2, mindless, about 37%.
- **Hauling on a rabbit torso.** About 37%. It tows a cow with nearly three times a player's slowdown.

**Worked examples.** These first left out the traits a build's parts and hides give it. Worked out again by the formula with them (stage A, `fitnessMatchesItsFormula`), four numbers changed, each marked "was":
- **The design's cow torso on four rabbit legs, with a cow head** (spec 6.5):
  - Herder 200%: pace 1.37 (1.3, and the rabbit's hide, which a flesh minion keeps, is Swift: 5% faster) × grazer knack 1.25 × docile 1.25.
  - Courier 146% (was 143%).
  - Hauler 138% (was 114%: the cow torso's Beast of Burden takes 15% off a drag's slowdown, so pull 1.18, and Swift).
  - Farmer 75% (rabbit paws).
  - Guard 26%.
  - Surgeon: cannot, no hand.
- **Spider thorax, eight zombie arms, a farmer villager's head** (spec 6.5):
  - Surgeon 200%: hands 1.6 × √2 sight × knack 1.5 × meek 1.25. Its stump costs one bucket.
  - Farmer 200%.
  - Guard 72% (was 65%: the spider's torso has its mob's 16 health; spec 6.4's 13 for the thorax alone was never built). It is no longer a pacifist: zombie arms punch.
- **An all-zombie build:**
  - Guard 125% at home, 156% with me (loyal).
  - Surgeon 141% (stumps of two buckets).
  - Butcher 100%.
  - Courier 40% (three slots).
- **A cow torso on wolf legs, with a wolf head:**
  - Herder 200% (216% before the cap; was 180%: each wolf leg is Swift, and four sum to 20% faster, so pace 1.44). A sheepdog.
  - Hunter 135% (was 124%, the same way).
  - Farmer 75% (paws).
  - Fisher 60%.
  - Butcher 27% (blade in its mouth).
  - Surgeon: cannot.

### 1.3 How a task is given and changed

**Recommended: a screen.** The maker crouches and uses an empty hand on the awake minion. That click cycled jobs; crouch-holding on a powered-down minion still folds it, and a plain click by anyone is still the status line.

The screen:
- lists every task in a fixed order in four groups (fight, tend, fetch, work);
- shows each task's fitness as a bar, a percentage and a word: Hopeless under 50%, Fair to 90%, Able to 130%, Good to 170%, Born to it above that. A task it cannot do shows "Cannot" and the reason;
- on hovering a row, shows each factor, its value and where it came from. For example: "Hands: zombie arm, hand, ×1.3 for four; Sight: villager head, 48 blocks; Knack: farmer's head ×1.5; Disposition: meek; At work: 17 mB a minute";
- marks the current task.

Clicking a row sets the task at once. Beside the list:
- **At home / With me**, for the tasks that allow it;
- **Reach**;
- **Home here**, which sets home to where the minion stands (lead it there first).

The server works out the rows and sends them when the screen opens. It checks every request:
- it comes from the maker, not from a machine's stand-in, within 8 blocks;
- the minion is awake;
- the task is possible;
- the anchor is allowed;
- the reach is within bounds.

A task changes only when its maker sets it here:
- Losing a tool no longer moves the minion to another task (`keepValid` goes). It waits.
- A data reload that makes its task impossible sets it to Idle at home, and the status line says why.

It **wakes** to its fittest task that waits on nothing it lacks, never Hunter or Sapper (today's rule), with ties going to list order. With nothing else possible it wakes to Idle. The action bar names the task: "Woke as a Surgeon (200%)". A villager-headed minion made at the table is ready to operate, as today.

**As built in stage B** (docs/ARCHITECTURE-PROPOSAL.md 15.19):
- **The screen** is drawn in Create's own schedule frame (`AllGuiTextures.SCHEDULE`), with its cards, brass strip and pointer for the task now, Create's icon buttons and its scroll input for the reach. Sixteen rows and four headings are taller than the frame's list, so the list scrolls, as the schedule's does; it opens scrolled to the task now. A task it cannot do shows "Cannot" and its reason in small print on the row. A task that would wait for something shows a small amber mark, and its reasons say for what. The rows are worked out at the anchor it works at now, where the task allows it (a loyal head's "with me" 1.25 shows when "With me" is on). A reach scrolled to is sent once it has been left alone half a second, or when the screen closes. Clicking a row sets that task at the anchor it works at now if the task allows it (else at home), with the task's own reach. After every request the server sends the rows again, and an open screen is brought up to date; a screen closed meanwhile stays closed.
- **The wake rule skips the Tender** until its goals are built (stage E). Given now, it keeps home. A whole cow wakes as a Courier: its best task, Herder, waits for food. (Since stage E it takes the Tender too, and a whole cow wakes as a Tender: see 1.1, "As built in stage E".)
- **A missing tool** stops the goal as well: a butcher with no blade in hand waits, and so does a herder with nothing in its mouth and a medic with no healing potions.
- **What its work waits on in the world** is said by five goals, for the status line's next five seconds: the fisher (no still water within its reach), the digger (nothing to sniff), the barterer (no gold), the hauler (no free hook or rack) and any task that stores (no container by home).
- **The status line** reads "Farmer 120% at home, blood 300 of 780 mB", with "at its post" for a sentry, "at its table" for a surgeon, "with its maker", or "at home, its maker away"; then what it waits for, or "it can no longer be a Surgeon: No hand to hold a surgeon's blade" after a data reload.
- **A companion or bodyguard whose body could not guard** (a torso with no arm and no head) loads as Idle with its maker, the nearest thing it can do; no shipped body could have been one, since such a body offered only companion before stage B's data change.

**Why not the others:**
- **A written task tag.** It still needs a writing screen, it shows no comparison, and machines never change a minion's task anyway (15.13).
- **Cycling.** Clicking through sixteen tasks blind is slow, and it hides the numbers this whole change is about.

### 1.4 How fitness is shown, and how it saves

**Shown:**
- **The task screen** (1.3): everything.
- **The status line** (a plain click, anyone): "Farmer 120% at home, blood 300 of 780 mB", plus "waiting for a Cleaver" or "no still water within 8 of home". Each task goal says why it is idle.
- **The Surgery Table's line while building:** "15 health, speed 0.33; best: Herder 200%, Courier 146%; 4 of 6 sockets filled". The maker sees each part's effect as it goes on.
- **The surgery screen:** before any cut, the surgeon's name, fitness and the stump's price (1.5).
- **JEI:** the Body Parts page (bb-organs) gains each part's knacks, grip and disposition. These are part facts; a fitness needs a whole build. A "Minion Tasks" page per task (what counts, which heads have a knack for it) can come later.
- **Piece tooltips,** with Ctrl, as organs do: the same part facts.
- **`/bloodandbones minion fitness`,** for the minion looked at: the full breakdown for every task, for balancing with the owner (slice 10).

**Saved:**
- On the minion, beside `Home`: `Task` (its id), `Anchor` (home or maker) and `Reach` (0 means the task's default). A Dormant Minion item keeps them with the rest.
- Fitness is never saved. It is worked out from the build and data together with the stats, so a datapack retune reaches every minion (15.1 item 4's rule). The time of day and the held item are applied when it is read.
- The screen's rows come from the server, so task and disposition files never need to go to clients. The knacks and grips JEI shows are in the mob data clients already have.

**As built in stage F** (docs/ARCHITECTURE-PROPOSAL.md 15.21):
- **The Surgery Table's line** (built in stage B) reads, for the whole cow on rabbit legs, "15 health, speed 0.32; best: Herder 200%, Tender 146%; 5 of 5 sockets filled" (a cow's torso has five sockets: its head and four legs; its speed is 0.325, shown to two places). Tender, not Courier: the two tie at 146% and ties go to the list's order. The example above had 146% for the Courier once stage A put the numbers right (143% before), 0.33 rounded up, and "4 of 6" for a build of another shape.
- **What a part brings** (`TaskWords.partFacts`): its knacks, those of 1 left out ("Knacks: Surgeon ×1.5, Farmer ×1.25"); what an arm holds with ("Holds with: hand", a villager's folded pair "hand, 2 of them"); what a leg holds with, where its data names a grip ("Holds with: paw, on a body with 4 legs or more"); a head's disposition ("Disposition: Meek"), and "A surgeon's head: it may do the ritual's cutting even where only such heads may" for the heads the switch in 1.5 reads (by default any hand may cut). JEI's Body Parts page shows them under each part as a new one of that mob has them, and says which traits its carcass keeps change them: a new villager's profession is "none", which every villager's carcass records, so its head shows Surgeon and Courier ×1.5 and "As a new one has them: its profession changes them" (a name is listed too for a mob whose data has a variant by name). (Before the review it showed the family's layer under the professions, Farmer ×1.25 among them, which no villager head in play has.) A piece's tooltip shows its own, the profession's knack with them, under "In a minion:" while Ctrl is held, and says "Hold Ctrl for what it brings to a minion's tasks" otherwise.
- **`/bloodandbones minion fitness`** is its own command class (`minion/MinionCommand`), not `parts/TraitsCommand`'s as 1.7 has it: it reads a live minion, not a mob's data. It takes the minion under the crosshair within 16 blocks (a wall stops the look), for its maker or an operator, and gives the task screen's hover lines for every task, each with the number before it was held where the cap took some off ("(213% before it was held)").
- **A "Minion Tasks" page per task in JEI** is not built: as above, it can come later.

### 1.5 The surgeon

**This is the owner's call, and still open.** Both ways are built (stage D), one data switch apart: the surgeon task's file (`data/bloodandbones/minion_task/surgeon.json`) says `"needs_surgeon_head": false`, the recommendation below, and that is the default as shipped; a datapack setting it to `true` gets the other way, the brief's letter. The heads whose data says `"surgeon": true` are the villager family's (the villager, the witch, the wandering trader and the zombie villager) and the illager family's (the pillager, the vindicator, the evoker and the illusioner). `MinionFitness.mayCut` answers for either setting (`surgeonHeadFlagDecidesWhoCuts` tests both), the Surgery Table asks it who may cut, and `surgeonHeadIsTheOwnersCall` makes real cuts both ways at the table: by default a zombie-headed surgeon cuts and leaves a stump of two buckets; with the switch on it is passed over (though it may still be set to Surgeon and tend) and a villager-headed one cuts, its stump a bucket.

**Recommended (the default): any minion with a hand can operate, and the head decides how badly.**
- Anyone may be set to Surgeon.
- The ritual's cut needs an awake minion on the Surgeon task within 4 blocks of the table (`Surgery.surgeonAt`). Its one physical need is a hand, and the fittest of several does the cutting.

Its fitness sets two things:
- **The stump's price.** This is what fitting anything but a crude prosthetic there later costs:
  - one bucket of blood at 150% and over: villager and pillager heads with hands (200%) and the witch's (about 187%). This is today's price;
  - two buckets from 75% to 150%: most other heads, such as a zombie's (141%), a cow's (125%), a vindicator's (about 130%), the zombie villager's shaky hands (about 88%) and a blind villager's (94%);
  - three buckets below 75%: a headless surgeon (12%).

  The body keeps the price per part (today it keeps only whether the stump is ragged). The operator pays it from any blood they carry, buckets and a tank added together. The stump is drawn raggeder the dearer it is.
- **Tending.** A heart every 5 s at 100%, ÷ fitness.

**Why the safety rules still hold:**
- A crude prosthetic still goes into any stump for nothing and never needs a surgeon.
- Fitting, reattaching and swapping still never need one.
- The cut never fails, since no chance is involved ("surgery cannot go wrong").
- The price is shown before the cut.
- A dear stump only costs more blood for a better limb later. It never takes away the way back to baseline.

The brief's words keep their weight: villager and pillager heads are still the surgeons that leave today's stump.

**The other way, keeping the brief's words exactly (`"needs_surgeon_head": true`).** Only a head whose data says `"surgeon": true` may cut: the villager and illager families and the witch. Any minion may still be set to Surgeon to tend at the table, and fitness still sets the tending and the stump's price among those heads. It is smaller to build and keeps the brief to the letter, but it is the one place where "any task to any minion" would not quite hold.

**The owner's answer replaces** docs/ARCHITECTURE-PROPOSAL.md 15.1 item 8 and spec 11 item 8 (surgeon heads by group).

**As built in stage D** (docs/ARCHITECTURE-PROPOSAL.md 15.20):
- **The fittest cuts.** `Surgery.surgeonAt` takes, of the awake minions on the Surgeon task within 4 blocks that may cut, the one with the highest fitness at it; of two alike, the first found.
- **The price's bounds are data.** The surgeon file's numbers `"one_bucket_from": 1.5` and `"two_buckets_from": 0.75` are the design's 150% and 75%, so a datapack can move them. The shipped file writes `"needs_surgeon_head": false` out, so the switch is there to see.
- **The price per part.** `Body` keeps each ragged stump's buckets; a body saved with the old list of ragged parts reads each as one bucket, its price then (`oldRaggedStumpIsOneBucket`). `Surgery.raggedCost` gives the blood a fitting takes (0 for a crude prosthetic, whatever the stump).
- **Paying.** `Surgery.payBlood(player, mB, take)` adds up all the blood the operator carries and takes nothing unless all of it is there. What gives only all it holds or nothing (a bucket) goes first, in the order carried, while it is no more than is owed; the rest comes out of what gives any part (a backtank). So a two-bucket stump takes a bucket and a backtank's thousand (`raggedStumpPaidAcrossContainers`), and a bucket and 500 mB are refused with "Needs 2 buckets of blood", nothing taken.
- **Shown before the cut.** The surgery screen names the surgeon by the table ("Surgeon: Minion (Zombie head), 141%"), what its stumps will cost ("Its stumps cost 2 buckets of blood to fit", amber at two, red at three), or that there is none; and each ragged stump its price ("Ragged stump: 2 buckets"). Task and disposition files never go to clients, so the server tells them, with each minion, its fitness at its task and, for a surgeon that may cut, its stumps' price: once a second, and at once when its task or build changes. The task screen's hover on Surgeon says the same ("A stump it cuts costs a bucket of blood to fit", or with the switch on and no surgeon's head, "It tends, but only a surgeon's head may cut").
- **Drawn.** A stump is drawn raggeder the dearer it is: at one bucket as before (longer than a clean cut, its end torn, flaps hanging off it); at two longer still, its flaps hanging further and flaring wider, with torn strips at its corners; at three more so. In bloodless mode the end stays plain, as before.
- **Found on the way.** The surgery screen closed at once when the server opened it while laying the patient on the table, whenever the seat they ride reached the client a tick after the screen (the showcase's `showcase_body_2.png` had shown no screen since at least stage B). It now waits a second for them to be seen on the table.

### 1.6 The data

**In a part's `minion` object** (group files and mob files, any part key):
- **`knacks`** (replaces `jobs`): a map from task id to a multiplier.
  - It merges key by key across the layers (archetype, family, overlays, the mob's own file), as trait lists do (spec 4.2). A family inherits its archetype's knacks and can change one without restating the rest.
  - Each matching variant then applies in order.
  - A build's knack for a task is the product, over its part slots, of the best knack among that slot's pieces. For the head, the first head only, as today. The product is held between 0.25 and 2.5.
- **`grip`** on a leg (new), as on an arm: paw, claw, hoof or tentacle. It counts for handwork only, never for blows or held weapons.
- **Kept:**
  - `disposition`, which now does something for every head;
  - `senses`, `prey`, `no_tool` (a fish's mouth fishes), `bite` and `steer`.
- **Gone:**
  - `jobs`;
  - `pace` (bb-organs' zombie villager), which becomes a surgeon knack of 0.5.

```json
"head": {"minion": {
  "knacks": {"bloodandbones:surgeon": 1.5, "bloodandbones:farmer": 1.25, "bloodandbones:guard": 1.0},
  "disposition": "meek",
  "variants": [
    {"if": {"trait": "profession"}, "knacks": {"bloodandbones:farmer": 1.0}},
    {"if": {"trait": "profession", "equals": "farmer"}, "knacks": {"bloodandbones:farmer": 1.5}},
    {"if": {"trait": "profession", "equals": "nitwit"}, "knacks": {"bloodandbones:surgeon": 1.0}, "disposition": "dim"}
  ]}}
```

**New data kinds** (server only, loaded by `PartsData` as two more kinds):
- **`data/<ns>/minion_task/<task>.json`**, one per task, holding:
  - its kind;
  - the anchors it allows;
  - reach and maximum reach;
  - its main and second factors (a closed list: pace, sight, hands, blow, ranged, toughness, carry, pull);
  - its grip table (a map);
  - its tool (an item tag; whether it is required stays the code's, since the goals decide it);
  - whether it stores its takings in the container by home;
  - the base numbers its levers scale.

  Code defaults equal today's constants, so a missing file changes nothing. A datapack can retune any of this; a new task still needs code (its goals).

  ```json
  {"kind": "work", "anchors": ["home"], "reach": 8, "main": "hands", "second": "sight",
   "grips": {"hand": 1.0, "paw": 0.75, "claw": 0.75, "tentacle": 0.6, "hoof": 0.45, "mouth": 0.35},
   "stores": true, "numbers": {"look_every": 20}}
  ```
- **`data/<ns>/minion_disposition/<name>.json`**: `kinds` (fight, tend, fetch, work), `tasks` (per-task overrides), `with_me`, `night` and `day`. For example, meek is `{"kinds": {"fight": 0.5, "tend": 1.25}}`.

**An optional new trait effect.** `bloodandbones:task_knack` (`task`, `multiplier`) in the minion context lets an organ or a hide make a minion better at one task. For example, the sniffer's Olfactory Bulb could give Digger ×1.5, and the Night Stalker Gland could give Hunter ×1.25. It is small and follows `AttributeEffect`.

**Day-one defaults for modded mobs.** Every factor comes from what a mob already has:
- from its attributes: speed, attack damage, health and follow range;
- from its rig: size and weight.

Every missing knack and disposition counts as 1. Archetypes carry knacks and leg grips, so a modded mob sorted into an archetype by its body shape (spec 3.7) can do every task its body allows, with no file of its own. The new leg grips are:
- quadruped `leg.front`: hoof;
- canid, feline, bear, amphibian and small prey `leg.front`: paw;
- arthropod `leg`: claw;
- tentacled and cephalopod `leg`: tentacle.

**The shipped knacks.** Old jobs become knacks. Companion is dropped, bodyguard becomes guard, and scavenger becomes courier.

| Head (file) | Knacks |
|---|---|
| quadruped / biped / blob | courier 1.25 / guard 1.25, courier 1.25 / guard 1.25, courier 1.25 |
| bird / flier / floater | courier 1.5 / courier 1.5, sentry 1.25 / courier 1.5, sentry 1.25 |
| arthropod / arachnid / feline / amphibian | hunter 1.5, guard 1.25 / hunter 1.5, guard 1.25 / hunter 1.5 / hunter 1.25 |
| colossus / elemental / guardian / shelled / shellback | sentry 1.5, guard 1.5 / sentry 1.5, guard 1.25 / sentry 1.5 / sentry 1.5, guard 1.25 / guard 1.25, sentry 1.25 |
| fish / marine; cod, salmon | fisher 1.5, courier 1.25 |
| tentacled / fowl / skyborne / small prey / spirit / vermin | courier 1.25 / courier 1.25 / courier 1.25, sentry 1.25 / courier 1.5 / courier 1.5, guard 1.25 / courier 1.25, digger 1.25 |
| bear | guard 1.5, fisher 1.25 (bears fish) |
| behemoth / equine / sniffer | hauler 1.5, guard 1.25 / hauler 1.5, courier 1.25 / digger 2, hauler 1.25 |
| canid | hunter 1.5, herder 1.5, guard 1.25 |
| golem / humanoid / piglin family | guard 1.5, sentry 1.25 / guard 1.25, courier 1.25 / guard 1.25, courier 1.25 |
| grazer / swine | herder 1.25, courier 1.25 / hunter 1.25, digger 1.25 (pigs root) |
| villager | surgeon 1.5; its profession's task 1.5 (farmer, fisher, butcher, medic for a cleric, herder for a shepherd, sentry for a fletcher, hauler for a leatherworker, guard for an armorer, weaponsmith or toolsmith, courier for the rest); nitwit: dim |
| illager / pillager / witch / zombie villager | surgeon 1.5, sentry 1.25, guard 1.25 / surgeon 1.5, sentry 1.5 / surgeon 1.5, medic 1.5 / surgeon 0.5 over its family's |
| piglin (mob) / allay / fox / axolotl | barterer 2, guard 1.25 / courier 2 / courier 1.5, hunter 1.25 / hunter 1.25 |
| volatile / panda / killer bunny | sapper 1.5, guard 1.25 / courier 1.25 (aggressive: guard 1.5; lazy: sentry 1.25) / guard 1.5, berserk |

**As built in stage A** (docs/ARCHITECTURE-PROPOSAL.md 15.18), where the data differs from the above:
- **The old `jobs` lists stayed beside the `knacks` until stage B.** Today's jobs could not be read from the knacks without changing what heads offered and woke as, and stage A changed nothing in play (1.10). Stage B deleted them with the job system: the shipped data has only `knacks` now. A third party's file with `jobs` and no `knacks` is still read as knacks, logged once.
- **`pace` stayed** on the zombie villager beside its surgeon knack of 0.5 until stage B, which deleted it: `AttendTable` tends at the surgeon's fitness now (a heart every 5 s at 100%, never under 2 s), so the shaky hands are the knack's.
- **"Courier for the rest"** is a variant for the librarian, the cartographer, the mason and an unemployed villager (`"none"`). A modded profession gets the family's surgeon 1.5 and the biped's courier 1.25, and nothing more, until its datapack names it.
- **The villager's own file no longer restates its family's `meek`.** A mob's own file wins over its family's variants (spec 4.2), so the nitwit's variant could not make it dim.
- **Leg grips count only on a body standing on at least four legs.** A body standing on two wolf front legs uses them to stand, and holds with its mouth.
- **The task file's tool** is `{"items": "<id or #tag>", "grips": {...}}`, each optional: with no `items`, the task's own test in code (a Cleaver or Flensing Knife, a rod, a bow, crossbow or trident, healing splash potions, anything held as bait or sample); `grips`, the grip table while it holds one (a rod in hand fishes at 1, a courier's sample is held). The file also has `max_reach`, and the surgeon's `needs_surgeon_head` (1.5). As built, the tool also had `required` and `carried`; after the second review these are the code's (below).
- **The Olfactory Bulb gives Truffle Nose**, a new trait: `task_knack` Digger ×1.5. The Night Stalker Gland is left as it was.

**What a task file can change, after the review** (docs/ARCHITECTURE-PROPOSAL.md 15.22):
- **Its kind** is what a disposition scales and what the task screen lists it under: a hauler's file saying `"kind": "fight"` makes a brave head haul at 1.25 and moves the row under Fight. What the goals do stays code: a hauler does not start going after monsters.
- **Its tool's `items`** narrow what the goals work with as well as what the row shows: a butcher's file naming only the Flensing Knife leaves a Cleaver waiting, row and goal alike (`MinionFitness.isTool`, which every goal that waits for a tool asks). A file cannot teach a goal a new tool: a stick named as a butcher's is no blade.
- **Its tool's `required` and `carried`** are fixed in code, since the goals decide them: a butcher cannot cut without a blade, a fisher fishes by hand, a medic throws the potions it carries. A file that names them otherwise is logged and they are left as they are, and a tool given to a task that works with none (a farmer's) is left out (`MinionTask.checked`). Only the butcher, the herder and the medic wait for a tool. Where a file narrows the items, the words name them: "waiting for Flensing Knife", "With Crossbow: 150%" (a tag by its name where it has one).
- **Its anchors** may be taken away, never added: only Idle, Guard, Hunter, Medic and Courier have goals that work round their maker. A file giving a Farmer "maker" is logged and the anchor left out; one with nothing left keeps the task's own. A task done only with its maker (a Guard's file with `["maker"]`) is set, woken and put back there.
- **A head's `disposition` and its `knacks` keys** are checked as the data loads: a name that is no id ("Brave", "bloodandbones:Surgeon") or a knack that is no number is logged and left out, and the layer under it shows through. Before, it failed later, in play, and took the server down.

### 1.7 What changes in code

**New:**
- **`minion/MinionTask`** — the task list: id, kind, goal package, allowed anchors, and the "cannot" tests that are code (nothing to strike with, no detonator). It replaces `MinionStats.JOBS`, `HANDS`, `SIGHT` and `HARVESTING`, and `MinionJobs.HOMEBODIES` and `STORERS`.
- **`minion/MinionFitness`** — pure. From the store, build, stats, held item and time of day it gives, per task: fitness, cannot-reason, waiting-for, and the factors with their sources. It is unit-testable like `MinionStats`.
- **Task and disposition file loading** in `parts/PartsData`: two more kinds, server-only.
- **`client/MinionTaskScreen`** and **`network/MinionTaskPayload`**: open (rows) and set (task, anchor, reach, "home here").
- **The Tender goal package.**
- **The `task_knack` effect** (optional).

**Changed:**
- **`MinionStats`:**
  - drop `jobs`;
  - add the build's holders (arm grips, leg grips, mouth), torso weight, the merged knacks and the disposition;
  - sight reads follow-range traits and senses;
  - `MINDLESS_SIGHT` goes from 8 to 2;
  - `fights()` is false with no striking arm and no head.
- **`MinionData`:** a merging `knacks` reader, and reading `jobs` as knacks for old files.
- **`MinionJobs`** is renamed `MinionTasks`:
  - each goal takes its lever from `minion.fitness(task)`;
  - the courier takes in the scavenger's goal;
  - the butcher chops at the Butcher's Table.
- **`MinionGoals`:**
  - an anchor instead of home in `StayNearHome`, `Collect`, `Deposit` and the guard's targets;
  - `FollowMaker` and `DefendMaker` for tasks done with me;
  - `AttendTable` tends at its fitness's pace;
  - a headless minion strikes only what touches it;
  - a sentry with no ranged attack strikes within reach without moving.
- **`MinionEntity`:**
  - `JOB` becomes `TASK`, with new `ANCHOR` and `REACH`;
  - new `task()`, `setTask`, `hasTask`, `anchor()` and `fitness(task)` (cached with the stats);
  - the crouch-click opens the screen;
  - the status line shows fitness;
  - save and load convert old jobs;
  - the working drain is ÷ fitness.
- **`MinionSapper`** (bb-organs): `hasDetonator` is its cannot-test.
- **`MinionAssembly.status`:** the best two tasks.
- **`parts/effect/SocialGoals`, `TeleportEffect`:** "hunting jobs" and "companion or bodyguard" become kinds and "with me".
- **`body/Surgery`:**
  - `surgeonAt` finds the fittest surgeon;
  - `raggedCost` replaces the ragged check;
  - `payBlood(player, mB, take)` adds containers up;
  - the surgery screen's surgeon line.
- **`body/Body`:** a ragged stump's price per part. The codec reads the old list as one bucket.
- **`client/SurgeryScreen`:** the surgeon line.
- **`carcass/CarcassDrag`:** a dragger's slowdown factor, for the hauler.
- **`carcass/CarcassButchery`:** a yield scale around `cut` and `skin`, as `capturing` works.
- **`registry/BBLang`:** task names (`bloodandbones.minion.task.*`), the cannot and waiting reasons, the screen, disposition names, all with bloodless wording (butcher: Dismantler; "the open socket costs two buckets of essence").
- **`compat/jei/BodyPartsCategory` and `parts/TraitsCommand`** (bb-organs): knacks, grips and dispositions, and `minion fitness`.
- **Data:** every `jobs` field becomes `knacks`, the leg grips are added, and the new task and disposition files.
- **Docs:**
  - spec 6.3, 6.4, 6.5, 6.7 and 6.9;
  - spec 8.2's "X job" becomes "a knack for X";
  - ARCHITECTURE 12, and a new 15.x once it is built.

**Deleted:**
- `MinionStats.JOBS`, `HANDS`, `SIGHT`, `HARVESTING`, `COMPANION` and the `jobs` component;
- `MinionJobs.offered`, `needsMet` (its checks become waiting reasons), `wakeJob`, `keepValid` and `startJob`;
- the crouch-click cycling and `bloodandbones.minion.job_now`;
- `MinionEntity.jobKey`;
- bb-organs' `pace` reading in `AttendTable`.

### 1.8 Old saves and old data

- **A saved minion's `Job`** becomes a `Task`, and its home is kept:

  | Old job | New task |
  |---|---|
  | companion, bodyguard | Guard, with me |
  | guard | Guard, at home |
  | sentry | Sentry, at its post |
  | scavenger | Courier at home, keeping what it holds as its sample |
  | any other job | the same task, at home |
  | an id with no task | Idle, at home |

  Every old job was offered under stricter rules than "cannot", so none becomes a task the minion cannot do. A folded minion converts when it is unfolded.
- **A saved body's `ragged` list** becomes one bucket per part.
- **Data with `jobs` and no `knacks`** is read as knacks: the first job 1.5, the rest 1.25, companion dropped, bodyguard as guard and scavenger as courier. The log gets one warning per file, so third-party datapacks keep working.

### 1.9 Tests

**New, worked out without a world** (template `empty`; built in stage A, `gametest/MinionFitnessTests`, with four descriptions put right where the formula said otherwise, each marked "as built"):
- `fitnessMatchesItsFormula` — for the four worked examples in 1.2, every task's fitness equals the number worked out by hand in the test, to within 1%.
- `oneHundredIsToday` — a stand-in build with every factor at its reference scores 100% at every task, and every lever gives today's constant: catches 600 to 1200 ticks, strokes 15, admiring 120, tending 100, working 25 mB.
- `cannotOnlyWhenTheBodyCannot` — a headless, armless cow torso cannot do Surgeon, Butcher, Farmer, Fisher, Medic, Barterer, Digger, Herder, Guard, Sentry, Hunter or Sapper, each with its own reason; it can do Idle, Courier, Hauler and Tender.
- `missingToolWaitsNotCannot` — a butcher with no blade, a herder with no food and a medic with no potions each take the task and wait, naming the item, and are shown at their fitness with it. As built: a fisher with no rod does not wait, since the grip table lets a hand fish without one (0.35, as 1.1 says it fishes "by hand, paw or mouth"); it takes the task at that fitness and is shown the fitness a rod would give it, as a sentry with no bow is shown a bow's. A hunter with the mobGriefing rule off waits for it.
- `villagerAndPillagerHeadsAreTheBestSurgeons` — with zombie arms: villager and pillager heads 200%; a witch's at least 150%; a zombie's between 75% and 150%; a blind villager's below a seeing one's. Chicken wings under a villager head cannot.
- `professionSetsItsKnack` — each profession's head (the old `villagerHeadOffersSurgeon` table) has knack 1.5 for its task and for surgeon; a nitwit's head is lower than an unemployed villager's at every task. As built: at every task that tends, fetches or works. At the four fights it is higher, as the disposition table has it: dim is 0.75 there and a villager's meekness 0.5.
- `blindHeadIsPoorNotBarred` — with both eyes out, farmer, fisher, surgeon, sentry and hunter are still possible but lower, and sight is 4. One eye out changes nothing. Echolocate or tremor brings sight back to 12. As built: the hunter's number reads Blow and Pace, not Sight, so it is still possible but no lower by the number (a blind hunter still finds less in the world, where sight caps its search). A blind head's 4 and a headless body's 2 take nothing from follow-range traits: no eyes see no farther for Keen Eye.
- `moreHandsWorkFaster` — eight zombie arms on a spider thorax give 1.6 times the two-armed fitness at Butcher and Farmer (before the cap), and strike 60% faster. As built: 1.6 times at Farmer, and at Butcher 1.6 × √1.6 (about 2), since its second is Blow, which the strike rate raises too. The extra holders counted are of the best grip only: a mouth that fishes as well as two hands is not a third hand.
- `pawsAndHoovesPickPoorly` — wolf legs pick at 75% and cow legs at 45%; neither holds a surgeon's blade, and the legs' speed is unchanged.
- `knacksMergeAcrossLayers` — a farmer's zombie-villager head has its own file's surgeon 0.5 and the family's farmer 1.5; the villager keeps the biped's courier 1.25 and its own guard 1.
- `oldJobsListReadAsKnacks` — a test file still saying `"jobs": [fisher, courier, companion]` resolves to fisher 1.5 and courier 1.25, with one warning.
- `dispositionsScaleTheirKinds` — one body under meek, brave, berserk and nocturnal heads moves as the table says: nocturnal is 1.25 at midnight and 0.75 at noon, and loyal is 1.25 with me.
- `moddedMobWorksFromItsArchetype` — a mob with only archetype data gets every task its body allows, from its attributes and the archetype's knacks and leg grips.
- `taskKnackTraitCounts` (if built) — a gland's knack raises that task and no other. Built: the sniffer's Olfactory Bulb.
- `surgeonHeadFlagDecidesWhoCuts` (new, for 1.5) — by default any surgeon with a hand may cut; with `"needs_surgeon_head": true`, only villager, illager and witch heads; with no hand, none.

**New, in the world** (those built in stage B are marked; `gametest/MinionTaskTests`):
- `taskScreenRowsForMakerOnly` (built in B) — the maker's crouching empty-hand use sends one row per task, matching `MinionFitness`. A stranger, a Deployer's stand-in and a powered-down minion get none, and folding still works. As built, the plain click that shows the status line is a stranger's: the maker's empty hand on a minion wearing a helmet takes the helmet off, as it did before.
- `setTaskFromTheScreen` (built in B) — the maker sets Fisher at home, reach 6, with Home here, and it fishes there. A stranger's request, an impossible task, "with me" for a Farmer and a reach past the maximum are all refused. Everything survives saving and loading.
- `oldJobConvertsOnLoad` (built in B) — the old jobs load as the table in 1.8 says, homes kept, including through a Dormant Minion item. As built, one set down from the item makes its home where it is set down, as unfolding always has; and a companion whose body has nothing to fight with loads as Idle with its maker.
- `wakesToItsFittestTask` (built in B) — a villager-headed build wakes as Surgeon and says so; a wolf head never wakes to Hunter, and a creeper's sac never to Sapper.
- `fitterButcherIsFasterAndCleaner` (built in C) — over 20 s, a 200% butcher makes about four times the strokes of a 50% one on a like carcass, and the poor one gets about half a player's beef per cut. As built: the 200% butcher is a butcher's head over a spider's torso on four of its legs with four zombie arms, the 50% one a whole villager (its folded arms hold a blade but never strike); each has a cow of its own behind a glass wall. At the carcass the fit one strokes every 8 ticks (7.5, rounded) and the poor one every 30, 3.75 times as often, and the test reads that. The count over the whole 20 s is not four times: a piece's last stroke takes it off, and each butcher then looks for its next piece (a one-in-twenty chance on every other tick, about two seconds, now and then six) and walks to it, the same for both and at random, so over the runs the fit one made 1.2 to 4 times the poor one's strokes, most often about twice; read as a pass or fail it failed now and then. The poor one's half of a player's beef is read over 200 cuts of a cow's body under the same scale its goal cuts under (`CarcassButchery.yielding`), since a cut's yield is rounded by a dice throw and the few cuts in 20 s do not even out.
- `poorHaulerCrawlsFitOneNoBetterThanAPlayer` (built in C) — a rabbit-torso hauler tows a cow with nearly three times a player's slowdown; a horse hauler's slowdown is exactly a player's. As built: a whole rabbit hauls at 36.5% (2.74 times a player's slowdown); the horse at 200%, its torso's hauler trait of drag strength counting in its fitness only.
- `bloodAtWorkFollowsFitness` (built in C) — couriers at 200% and 50% use 12.5 and 50 mB a minute at work, to within 10%. As built: a horse (200%) and an all-zombie courier (40%, held at 50%), and a brass horse at a quarter of what its fitness would cost flesh (a little under 200%: brass keeps no hide, so none of the horse's coat's speed); each held at work for 30 s.
- `moreArmsStrikeMoreOften` (added in C, for 1.10's strike rate) — an all-zombie guard strikes the husk by it every second, a spider's torso with eight zombie arms every 13 ticks (60% more often).
- `guardWithMeIsAWolf` (built in B) — it follows, and goes for what hurts its maker and what its maker hits; Idle with me follows and only fights back.
- `courierWithMeFillsTheMakersHands` (built in B) — it picks up round the maker and hands the items over; holding an apple, it takes only apples.
- `hunterWithMeHuntsBesideTheMaker` (built in B) — it hunts prey near the maker, not near home; with a Meat Hook the carcass is intact. As built, its reach is set to 4 so the two cows' hunting grounds are apart in a 10-block pen, and the cows are still, since a cow hit flees beyond so short a reach and a hunter lets prey go that leaves its ground.
- `medicWithMeHealsOnTheMove` (built in B) — it follows and throws at its hurt maker. As built, its maker is hurt once it has followed them some way: a medic stops to throw where it stands, and within its following distance of 6 blocks has no need to come nearer.
- `tenderFillsTroughsAndCradles` (built in E) — blood buckets from a chest go into a trough and the empties come back; a Create tank of blood fills its buckets; canisters go from a chest into a cradle and the empties come back. As built: a whole cow tends a chest of two buckets of blood and two empties, a barrel of three canisters and five brass sheets, a trough, a Create Fluid Tank of 2000 mB and a cradle holding two empties; at the end the trough holds 4000 mB and the tank none, the cradle three canisters and five sheets and no empties, the containers the four empty buckets and two empty canisters and nothing else, and nothing is on the ground or left in its hands.
- `tenderWakesAFallenMinion` (built in E) — a flesh minion lying powered down within reach gets blood and gets up; a brass one gets a canister (its time: see "Added after the review" below). As built: the Tender is brass, which drinks no blood, and the flesh one a zombie's torso holding 344 mB, so the rest of the bucket goes into the trough by home, to the drop; the empty bucket and canister go back into the chest. It first checks how a bucket is shared out (the fallen one, the Tender, then the troughs; all of it or none).
- `butcherChopsAtTheTable` (built in E) — pieces on a Butcher's Table by home are chopped. As built: a butcher's head over a spider's torso on four legs with four zombie arms (a slot for each of the four things a cow's body gives) chops a cow's body, then a second laid on through the table's slot as a funnel would; what they give ends in the chest beyond the table, none on the ground, and the Cleaver comes away bloody.
- `partFactsShowKnacksGripsAndDispositions` (added in F; `gametest/MinionFitnessTests`) — a farmer villager's head has Surgeon, Farmer and Courier knacks and none shown for Guard (1), is meek and a surgeon's head; a nitwit's is dim; a cow's head has Herder and Courier and is docile; a zombie's arm holds with a hand, a villager's pair with two, a wolf's front leg with a paw; a carried piece's tooltip reads the same facts.
- `fitnessCommandShowsEveryTask` (added in F) — the command's minion is the one its maker looks at, and none when they look away; its lines say what it is doing, then every task, with Herder's 213% before it was held and Surgeon's reason it cannot.
- `headlessFightsOnlyWhatTouchesIt` (built in B) — a headless Guard ignores a zombie 4 blocks off and hits one beside it. As strengthened after the review: for three seconds it takes no target with a husk 1.8 blocks off (within the 2 blocks it notices things in, but not against it) and one 4 off; then it strikes a third put against it, and for two seconds more goes for neither of the others and never walks off. (It had passed with a headless guard hunting by sight, since the first blow ended it.)
- `sentryWithNoBowHoldsItsPost` (built in B) — it strikes within reach and never moves. As strengthened: for three seconds only a husk out of its reach is there, and it takes no target; then one comes within reach, is struck, and it still never moves toward the other.
- `reloadTakesAnImpossibleTask` (added in B) — a data reload that leaves a Farmer's body with no hand sets it to Idle at home, and the status line says why.
- `taskWordsReadBloodless` (added in B) — every task, screen and status string has its bloodless wording, and none of them reads blood, bleeding, a carcass, flesh, organs, gore, guts, butchery or a minion there.
- `surgeonHeadIsTheOwnersCall` (added in B, for 1.5; `gametest/SurgeonTests`) — at the table, a zombie-headed surgeon cuts by default and is passed over with `"needs_surgeon_head": true`; a villager-headed one cuts either way.

**Added after the review** (docs/ARCHITECTURE-PROPOSAL.md 15.22; in `gametest/MinionTaskTests` unless named):
- `foldedArmsBiteWithTheHead` — a whole villager is shown able to guard, and set to Guard it bites the husk by home. `cannotOnlyWhenTheBodyCannot` now also reads that folded arms with no head cannot fight and under a head can, row and `fights()` agreeing; `pacifistTakesNoTarget` (`gametest/MinionTests`) is the headless villager body.
- `headlessSapperGoesOffAtATouch` (`gametest/MinionVariantTests`) — a cow's body with the sac and no head is a mindless sapper: it leaves a husk 2.2 blocks off alone and never walks, and goes off once one is against it.
- `pickUpTakesNothingTwice` — a stack taken whole is gone and empty, so a second minion's pickup that tick takes nothing; nor does one after a player took the stack; part of a stack leaves the rest on the ground.
- `courierKeepsWhatItsMakerHasNoRoomFor` — with its maker's pack full it keeps the three apples it fetched and says why, and hands them over once there is room; it never fetches the stick its maker threw away.
- `poorButcherWastesWhatItCuts` — two 50% butchers, one at a Butcher's Table and one at a loose cow's body, each keep half a player's beef from what their own goal cut (2 or 3 of 4.22). `fitterButcherIsFasterAndCleaner` had worked the waste out itself.
- `sentryShootsByItsFitness`, `surgeonTendsByItsFitness` and `fisherWaitsByItsFitness` — levers measured in the world: 200% and 50% sentries loose an arrow every 30 and 60 ticks (a second's draw and their wait); a 200% surgeon tends a heart every 50 ticks and, its head swapped for a zombie villager's mid-way, every 113 from the next; a 200% fisher's wait is set between 15 and 30 s and it catches when it is up, a 49% one's between 61 and 121 s, with no hurrying.
- `surgeonFindsATableWithinItsReach` — a table 8 blocks off is beyond its own reach of 6 and within the 10 its maker sets.
- `taskFileKindToolAndAnchorsCount` (`gametest/MinionFitnessTests`) and `taskFileKindAndAnchorsInPlay` — a hauler's file saying fight is scaled and listed as one; a butcher's naming only the knife leaves a Cleaver waiting, and a stick is no blade; a sentry's naming only the crossbow leaves a bow no ranged attack; a farmer's "maker" is left out, a guard's "maker" only is set there.
- `badMinionDataLeftOutAsItLoads` (`gametest/MinionFitnessTests`) — a disposition "Brave" and knacks that are no id or no number are left out as the data loads, the layer under them showing through, and such a name read in play is no disposition, never a crash.
- `partFactsShowKnacksGripsAndDispositions` now also reads JEI's page for a villager as a new one has it (Surgeon and Courier ×1.5, no Farmer, "its profession changes them"), and a witch's says nothing of one.
- `tenderWakesAFallenMinion` has 3000 ticks, not 1600. Its Tender is 32% fit, and looked round half as often as its number said (the one-in-n chance was taken on every other tick), so its five errands ran past 80 s 2 times in 125. With the looks put right, they took 12 to 33 s over 38 runs.

**Added after the second review** (docs/ARCHITECTURE-PROPOSAL.md 15.23; in `gametest/MinionTaskTests` unless named):
- `fitFarmerFindsRipeCropsNoLater` — at every reach a farmer may be set to, one at 150% or 200% goes over its box in no more ticks than a 100% one, and a 50% one reads no more than its share. In the world, a 200% farmer at reach 6 by the pen's side (two bands, as it was) starts on a crop ripening on one side, then on the other, 30 times, within 16 ticks on average; a 100% farmer's look is 20. With the old bands it failed 5 runs in 6 (17 to 25 ticks).
- `looksComeAsOftenAsTheScreenSays` — over two minutes a 200% courier looks round 240 times or so (every 10 ticks) and a poor one as its look ticks make, counted in the world. With the old one-in-n chance they looked about half as often and it failed every time.
- `wingsBuffetTheHeadBites` — a farmer villager's head on a chicken's wings bites the husk by home as a Guard, its Blow its bite; a headless chicken body on its wings is offered no Guard, Sentry or Hunter.
- `withMeKeepsToItsReach` — a guard with its maker at reach 4 leaves be what its maker hits 8 blocks off and goes for what they hit beside them; Idle with its maker at reach 8 stays while they are 9 off, and at its own reach follows.
- `surgeonWithNoTableSaysSo` — with no table in its reach, its status line says so, "at home", all the time until a table is set down in reach, which it takes, the line then "at its table".
- `reloadMovesATaskWhereItIsDone` — a Guard saved with its maker and loaded under a guard file allowing only home is Guard at home; one saved at home under a file allowing only its maker is with its maker; each keeps its home.
- `tenderFillsTroughsAndCradles` now fills from a Create tank of eight blocks, two by two by two, and its look finds it once, at its block nearest the Tender.
- `taskFileKindToolAndAnchorsCount` (`gametest/MinionFitnessTests`) now also reads that a file cannot make a butcher's tool optional, a fisher's required or a medic's held, nor give a farmer a tool, and that the words name a narrowed tool ("waiting for Flensing Knife", "With Crossbow") and the task's own otherwise.

**The surgeon, if the recommendation is taken** (all built in D, `gametest/SurgeonTests`):
- `anySurgeonWithAHandCuts` — a zombie-headed surgeon cuts and leaves a stump of two buckets; a villager-headed one leaves one bucket, and cuts first when both are there; a headless one leaves three. As built it also reads what the surgery screen is told of each: its fitness and its stumps' price.
- `raggedStumpPaidAcrossContainers` — two buckets are paid from a bucket and a backtank together; one short is refused with "Needs blood".
- `oldRaggedStumpIsOneBucket` — an old saved ragged arm costs one bucket.
- `shakySurgeonReadsItsKnack` (added in D) — bb-organs had no test of its own for the zombie villager's shaky hands (the signature lint reads only that its head has data). Its head's surgeon knack of 0.5 makes an 88% surgeon with a zombie's arm: a stump of two buckets, a heart tended every 113 ticks where a villager's head tends one every 50.
- The switch both ways: `surgeonHeadIsTheOwnersCall` (above) makes real cuts, with the switch off and on.

**Changed:**
- `amputationNeedsSurgeon`: a minion by the table on any other task does not count. As built in D, the villager-headed surgeon's stump is one bucket.
- `raggedStumpCostsBlood` and `raggedStumpPaidFromBacktank`: the price comes from the surgeon. As built in D: a zombie-headed surgeon's stump takes two buckets (one is refused, and left full), a villager-headed one's reattached arm one, and a headless one's stump three from an iron backtank of 3500 mB.
- `safetyFloorNeverNeedsSurgeon`: a crude prosthetic goes into a three-bucket stump for nothing.
- `oneHundredIsToday` (in C): every lever of 1.2 is today's constant at 100% and moves within its bounds, and the stump's price bounds are the surgeon file's (in D).
- `taskWordsReadBloodless` (in C and D): the task screen's new lines of what fitness makes of the work, and the surgery screen's surgeon and price words.
- `surgeonKeepsToItsTable` and `unfoldedSurgeonTakesTheTableBesideIt`: tasks are set with `setTask`.
- Every job test that uses `switchTo` or `setJob` now uses `setTask`.
- `scavengerFetchesMatchingItem` and `filteredScavengerFetchesWhatItPasses` become courier tests. As built in B: `courierWithSampleFetchesItsLike` (at home, holding a shard, into the chest by home) and `filteredCourierFetchesWhatItPasses` (brass with a filter and an empty hand, with its maker, into their hands).
- `buildCowOnFourRabbitLegs`: its best task is Herder.
- `wingsAreNoHands`: Surgeon cannot; a bow held in a beak is still no ranged attack. (Built in B, with the other changed tests here that stage B could make: `amputationNeedsSurgeon`, `surgeonKeepsToItsTable`, `unfoldedSurgeonTakesTheTableBesideIt`, every `setJob` and `switchTo`, the two courier tests, `buildCowOnFourRabbitLegs`. The shaky surgeon's knack is read in stage A's `knacksMergeAcrossLayers`; the stump's price is stage D's.) `fisherFishesByWater` also changed: with no rod a fisher's head fishes by hand and is shown a rod's fitness, and the sapper tests ask whether a body can sap (a detonating organ) rather than whether a head offers it.
- bb-organs' shaky-surgeon test reads the knack.

**Deleted, each replaced above** (deleted in stage B):
- `cycleJobWithEmptyHand`;
- `villagerHeadOffersSurgeon`;
- `pillagerHeadOffersSurgeon`;
- `nitwitOffersCompanionOnly`;
- `villagerAndPillagerHeadsOfferSurgeon`;
- `blindHeadLosesSightJobs`.

### 1.10 Order of work

Start after bb-organs is on main (it is, since 40dfb66). This change touches its sapper, variants, `pace`, `MinionStats` and `MinionJobs`.
- **A. The fitness, worked out without a world (medium). Built 29 September 2026** (docs/ARCHITECTURE-PROPOSAL.md 15.18). `MinionTask` (the list, its kinds, anchors, the code's tests of the body and each task's data), `MinionDisposition`, `MinionFitness` (rows, factors and their sources, the stump's price, who may cut, and the levers, pure), knacks, leg grips, the disposition and task files, the `task_knack` effect, and the migrated data, with the tests worked out without a world. Nothing in play changes yet, but for 1.11's three: a headless body sees 2 blocks, a seeing head's Keen Eye and Relentless count, and a body with no arm and no head has nothing to fight with.
- **B. Tasks replace jobs (large). Built 29 September 2026** (docs/ARCHITECTURE-PROPOSAL.md 15.19). `MinionTasks` (each task's goals, the wake rule, old jobs read as tasks, a reload that takes a task away, the screen's rows and the checks on its requests), anchor and reach, the "with me" goals (`FollowMaker`, `DefendMaker`), a headless minion's touch and a bowless sentry's strike, the screen (`MinionTaskScreen`, `MinionTaskPayload`), the status lines (`TaskWords`, which turns `MinionFitness.Source` into words), the Surgery Table's best two tasks, the save conversion, and the deleted `jobs` lists and `pace`, with their tests. Also done here, ahead of stage C, because stage B removed what they stood on or the screen shows them: the surgeon's tending reads its fitness (`pace` is gone), blood at work is 25 mB a minute ÷ fitness (the hover shows it), and `Surgery.surgeonAt` asks `MinionFitness.mayCut`, so the surgeon file's switch (1.5) already decides who cuts; the fittest surgeon and the stump's price stay stage D's. The guard at home keeps within 4 blocks of home and fights what comes within its reach, as the guard job did; "patrols its reach" is read as watching it, since a guard walking a 16-block beat would wander into its neighbours' land. A herder looks out for strays as much further as its reach is set further (20 at its own 8). A courier notices loose items within its sight as well as its reach, as the scavenger did.
- **C. The levers (medium). Built 29 September 2026** (docs/ARCHITECTURE-PROPOSAL.md 15.20). The levers are written and tested at 100% (`MinionFitness.quicker` and the rest); the goals read them here (the goals already read their base numbers from the task files). Blood at work and the surgeon's tending came in stage B. The strike rate counts in Blow already, but blows do not land more often in play yet (spec 6.4's "+15% for each arm beyond 2" was never built): the guard's and hunter's melee goals take it here. As built:
  - Every lever in 1.2's table is read by its goal from the minion's fitness at its task, worked out once a second (`MinionEntity.taskFitness`): the sentry's time between shots (a bow's, a crossbow's after loading and after loosing, a trident's) and its spread at the world's difficulty, the medic's throws and spread, the herder's wait on a stray, the courier's and farmer's looks (a one-in-n chance each time the goal is asked, as the old one-in-ten was; since the review, the chance is one in half the look's ticks, as the goal is asked every other tick), the fisher's catches (never under a sixth of today's least, with Lure), the barterer's look at the gold, the digger's finds and the butcher's strokes and yield. The Tender's look (`lookTicks`, every second at 100%) waits for its goals (stage E); the screen shows it already.
  - The butcher's yield below 100% is scaled by `CarcassButchery.yielding`, around the cut or stroke of skinning as `capturing` works (a scale over 1 counts as 1). The hauler's slowdown comes through `CarcassDrag.Dragger`, which a minion is: a player's slowdown, never less, ÷ its fitness below 100%, at most 90%. Its drag strength counts only in its fitness now (its pull), where before it eased its slowdown directly, so a cow torso's Beast of Burden no longer tows with less slowdown than a player.
  - The strike rate: `MinionGoals.blowTicks`, a second at two striking arms, 15% sooner for each past two, to 60% (13 ticks at eight), in the melee goal (`Bite`, vanilla's goal with its own blow timing), a headless body's `Feel` and a bowless sentry's strike.
  - The task screen's hover says what the fitness makes of each task's work, in the task's own numbers: "A stroke every 3 s, keeping 25% of each cut", "Keeps after a stray for 60 s", "Towing, slowed 2.74 times as much as a player, to at most 90%", "Strikes every 0.65 s".
  - Found by `fitterButcherIsFasterAndCleaner`: a butcher measured its reach to the piece from its feet, and a resting body's torso lies a block up, so a butcher standing against the body where its path ended was out of reach, stood there five seconds and gave the body up for half a minute. It now reaches as far across as it did, and two and a half blocks over or under its feet, as a hauler's reach is measured across.
- **D. The surgeon (small). Built 29 September 2026** (docs/ARCHITECTURE-PROPOSAL.md 15.20), as recommended and as the default, with the brief's letter one data switch away (1.5, "As built in stage D"); the owner's answer is still open.
- **E. Tender and the Butcher's Table (medium). Built 29 September 2026** (docs/ARCHITECTURE-PROPOSAL.md 15.21). `MinionTender` (its goal, one errand a look), the butcher's chop at the table (`ButcherTableBlockEntity.chop` handing what it gives to the butcher), the wake rule taking the Tender, with their tests. Where it differs from 1.1: see 1.1, "As built in stage E".
- **F. Shown elsewhere (small). Built 29 September 2026** (docs/ARCHITECTURE-PROPOSAL.md 15.21): the Surgery Table's line (stage B's, photographed), JEI's Body Parts page and piece tooltips with each part's knacks, grip and disposition, `/bloodandbones minion fitness`, and the docs (spec 6.3, 6.4, 6.5, 6.7, 6.8, 6.9 and 8.2). Where it differs from 1.4: see 1.4, "As built in stage F".

### 1.11 Found while reading (not part of this change)

- **Debugging left on bb-organs.** `MinionGoals.Bite.tick` logged "[tmpbite]" every 2 s, and there was a `gametest/TmpMergeRepro.java`. Both were gone before bb-organs was merged (40dfb66).
- **`MINDLESS_SIGHT` (8) is more than `BLIND_SIGHT` (4).** A body with no head notices more than a blind head does. The design sets it to 2. Fixed in stage A.
- **Keen Eye and Relentless do nothing on a minion.** They raise its follow-range attribute, but `MinionStats.sight` never reads it and every search takes the smaller of the two. The design's sight counts them. Fixed in stage A, for a head that sees.
- **`MinionStats.fights()` is true for a body with no arms and no head.** It would bite with no mouth. This is harmless today, because a mindless minion never fights. Fixed in stage A.
- **`Surgery.payBlood` looks for one container holding the whole amount.** Stumps costing two or three buckets need it to add containers together. Fixed in stage D.

## 2. Rig source B, the Sable Ragdolls way (built and judged 27 September 2026; waiting on the owner)

Every mob was also rigged the way Sable Ragdolls does it, behind a switch. Both versions were measured against the
brief's physics. The branch `bb-rig-b` has the code and the full page, `docs/RIG-COMPARISON.md`; it is kept locally and
not on main.

**Verdict:** it does not work better overall. Our rigs win 7 checks, theirs 4, and the rest are ties.

**Theirs is better at:**
- turning rear-first when hooked by a hind leg;
- falling away from a blow to the flank;
- heads and limbs hanging looser;
- costing about a quarter less to simulate while it falls.

**Ours is better at:**
- weight;
- being dragged up a step by a hind leg;
- looking dead rather than posed;
- staying where it fell;
- drawing every part.

**The owner is to choose:**
1. Copy B's wins into our rigs one at a time (recommended).
2. Put B on main behind the switch.
3. Drop it.

**Found along the way, to fix either way:** a wolf hung on a Shackle Hook hangs from its right hind hip, and by the same
code a ravager would hang from its right front shoulder (docs/ARCHITECTURE-PROPOSAL.md 15.18 on that branch).

## 3. Already on the list

- **Open questions answered with the design's defaults** (docs/ARCHITECTURE-PROPOSAL.md 15.1). All fourteen are still
  open for the owner to confirm or overturn.
- **Known gaps:**
  - A torso's empty arm socket shows its raw stump as a flat square beside the shoulder (the space the arm's top left,
    as a carcass's cut arm shows it), which from behind reads as a thin plate sticking out.
  - `meatHookDragsByLeg` failed once in more than 50 runs (the drag physics under load); it is being watched.
- **Later slices** (docs/PARTS-AND-TRAITS.md section 9):
  - the balance pass and Ponder scenes (slice 9);
  - the Deployer route for fitting armour;
  - a performance test with many minions;
  - config multipliers tuned with the owner (slice 10).
