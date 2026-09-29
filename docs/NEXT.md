# For next time

The whole brief was also audited against the code on 29 September 2026: docs/BRIEF-AUDIT.md lists every gap, grouped into work packages, and the decisions only the owner can make.

The owner's list of what to take up next, newest decisions first. Each item says what was decided, what it replaces,
and what is still to be settled together before any code is written.

## 1. Tasks instead of jobs (decided 24 September 2026; designed 29 September 2026; not started)

**The decision.** There are to be no jobs. Any task, from a list we draw up together, can be given to any minion. Some minions do a task better than others because of their own stats, and those stats come from what they are built of.

**What it replaces.** Today a minion's head offers it one to three jobs (docs/PARTS-AND-TRAITS.md 6.4 and 6.9). All of the following goes:
- the jobs list in `MinionStats.JOBS` and the hand rule in `HANDS`;
- each head's `jobs` data, with the villager-profession variants;
- blind heads losing the jobs that need sight;
- the crouch-click that cycles through the offered jobs.

**Summary of the design.** Every point is a recommendation, and the owner can overturn any of them.
- **Sixteen tasks.** The seventeen goal packages become fourteen: companion and bodyguard fold into Guard and Idle, and scavenger folds into Courier. With Idle (stay, or follow) and one new task, Tender (it keeps troughs and cradles stocked and carries blood to fallen minions), that makes sixteen. Six tasks can be done "with me", at the maker's side, as well as at home.
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
- **The surgeon (the owner's call).** Recommended: any minion with a hand can operate. Its fitness decides how ragged the stump is. A villager or pillager head leaves a stump that costs one bucket of blood to fit later, as now; a worse surgeon's costs two or three. Fitness also decides how fast it tends a patient. The safety floor is untouched.
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

**The one question the owner must answer: the surgeon (1.5).** There are two choices:
- any minion with a hand may do the ritual's cutting, badly unless it has a surgeon's head (recommended);
- only villager, illager and witch heads may cut, as the brief's words say.

### 1.1 The task list

"With me" means the task is centred on the maker, not on home. The minion follows the maker and works round them while the maker is in the same world and within 64 blocks; otherwise it works at home. Reach is the default today's code uses. The maker can set it from 2 up to twice the default: at most 32 for tasks that look for creatures or loose items, and at most 12 for tasks that read blocks (every block in the box is read). Sight still caps how far it notices creatures and loose items, as today.

| Task | What it does | Where | The least it needs | From |
|---|---|---|---|---|
| **Idle** | "Stay" at home: keeps within 4 blocks of home. "Follow" with me: keeps near its maker. It only fights back. | home, or with me | nothing | companion's following |
| **Guard** | At home: patrols its reach and attacks monsters there. With me: follows, and goes for what hurts its maker and what its maker hits, as a tamed wolf does. | home (16), or with me | something to strike with: an arm that is not folded, or a head to bite with | guard, bodyguard, companion's defending |
| **Sentry** | Never leaves its post, and shoots what comes within range. With no ranged attack, it strikes only what comes within reach. | its post (where it stood when set) | something to strike with | sentry |
| **Hunter** | Kills prey near home or near its maker. With a Meat Hook in hand it leaves intact carcasses. | home (12), or with me | something to strike with; the mobGriefing rule on | hunter |
| **Sapper** | Walks to its target, or to the banner it was shown, and blows itself up through its organ. Then it lies powered down. | home (16) | a detonating organ | sapper (bb-organs) |
| **Surgeon** | Keeps by its Surgery Table, does the amputation ritual's cutting, and tends whoever lies on the table. | its table (home, or the nearest within 6) | a hand | surgeon |
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
| Courier | fetch | Carry | Pace | carrying (holding, for a sample) | never | how often it looks round (every half second) ÷ fitness |
| Hauler | fetch | Pull | Pace | – | never | its slowdown while towing: a player's × 1 ÷ fitness below 100%, at most 90%, and never less than a player's |
| Farmer | work | Hands | Sight | picking | nothing to pick with | how often it looks for ripe crops (every half second) ÷ fitness |
| Fisher | work | Hands | Sight | fishing | nothing to catch with | time between catches (30 to 60 s) ÷ fitness, never under a sixth (the Lure floor) |
| Butcher | work | Hands | Blow | blade | nothing to hold a blade with | time between strokes (0.75 s) ÷ fitness, never under 0.3 s; below 100%, its yields × fitness (waste) |
| Barterer | work | Hands | – | picking | nothing to pick with | time looking the gold over (6 s) ÷ fitness |
| Digger | work | Hands | – | nose | no head, paws or claws | time between finds (1 to 2 min) ÷ fitness |

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

**Worked examples:**
- **The design's cow torso on four rabbit legs, with a cow head** (spec 6.5):
  - Herder 200%: pace 1.3 × grazer knack 1.25 × docile 1.25.
  - Courier 143%.
  - Hauler 114%.
  - Farmer 75% (rabbit paws).
  - Guard 26%.
  - Surgeon: cannot, no hand.
- **Spider thorax, eight zombie arms, a farmer villager's head** (spec 6.5):
  - Surgeon 200%: hands 1.6 × √2 sight × knack 1.5 × meek 1.25. Its stump costs one bucket.
  - Farmer 200%.
  - Guard 65%. It is no longer a pacifist: zombie arms punch.
- **An all-zombie build:**
  - Guard 125% at home, 156% with me (loyal).
  - Surgeon 141% (stumps of two buckets).
  - Butcher 100%.
  - Courier 40% (three slots).
- **A cow torso on wolf legs, with a wolf head:**
  - Herder 180% (a sheepdog).
  - Hunter 124%.
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

**Why not the others:**
- **A written task tag.** It still needs a writing screen, it shows no comparison, and machines never change a minion's task anyway (15.13).
- **Cycling.** Clicking through sixteen tasks blind is slow, and it hides the numbers this whole change is about.

### 1.4 How fitness is shown, and how it saves

**Shown:**
- **The task screen** (1.3): everything.
- **The status line** (a plain click, anyone): "Farmer 120% at home, blood 300 of 780 mB", plus "waiting for a Cleaver" or "no still water within 8 of home". Each task goal says why it is idle.
- **The Surgery Table's line while building:** "15 health, speed 0.33; best: Herder 200%, Courier 143%; 4 of 6 sockets filled". The maker sees each part's effect as it goes on.
- **The surgery screen:** before any cut, the surgeon's name, fitness and the stump's price (1.5).
- **JEI:** the Body Parts page (bb-organs) gains each part's knacks, grip and disposition. These are part facts; a fitness needs a whole build. A "Minion Tasks" page per task (what counts, which heads have a knack for it) can come later.
- **Piece tooltips,** with Ctrl, as organs do: the same part facts.
- **`/bloodandbones minion fitness`,** for the minion looked at: the full breakdown for every task, for balancing with the owner (slice 10).

**Saved:**
- On the minion, beside `Home`: `Task` (its id), `Anchor` (home or maker) and `Reach` (0 means the task's default). A Dormant Minion item keeps them with the rest.
- Fitness is never saved. It is worked out from the build and data together with the stats, so a datapack retune reaches every minion (15.1 item 4's rule). The time of day and the held item are applied when it is read.
- The screen's rows come from the server, so task and disposition files never need to go to clients. The knacks and grips JEI shows are in the mob data clients already have.

### 1.5 The surgeon

**Recommended: any minion with a hand can operate, and the head decides how badly.**
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

**The other way, keeping the brief's words exactly.** Only a head whose data says `"surgeon": true` may cut: the villager and illager families and the witch. Any minion may still be set to Surgeon to tend at the table, and fitness still sets the tending and the stump's price among those heads. It is smaller to build and keeps the brief to the letter, but it is the one place where "any task to any minion" would not quite hold.

**The owner's answer replaces** docs/ARCHITECTURE-PROPOSAL.md 15.1 item 8 and spec 11 item 8 (surgeon heads by group).

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
  - its tool (an item tag, and whether it is required);
  - whether it stores its takings in the container by home;
  - the base numbers its levers scale.

  Code defaults equal today's constants, so a missing file changes nothing. A datapack can retune any of this; a new task still needs code (its goals).

  ```json
  {"kind": "work", "anchors": ["home"], "reach": 8, "main": "hands", "second": "sight",
   "grips": {"hand": 1.0, "paw": 0.75, "claw": 0.75, "tentacle": 0.6, "hoof": 0.45, "mouth": 0.35},
   "stores": true, "numbers": {"look_every": 10}}
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

**New, worked out without a world** (template `empty`):
- `fitnessMatchesItsFormula` — for the four worked examples in 1.2, every task's fitness equals the number worked out by hand in the test, to within 1%.
- `oneHundredIsToday` — a stand-in build with every factor at its reference scores 100% at every task, and every lever gives today's constant: catches 600 to 1200 ticks, strokes 15, admiring 120, tending 100, working 25 mB.
- `cannotOnlyWhenTheBodyCannot` — a headless, armless cow torso cannot do Surgeon, Butcher, Farmer, Fisher, Medic, Barterer, Digger, Herder, Guard, Sentry, Hunter or Sapper, each with its own reason; it can do Idle, Courier, Hauler and Tender.
- `missingToolWaitsNotCannot` — a butcher with no blade, a fisher with no rod, a herder with no food and a medic with no potions each take the task and wait, naming the item, and are shown at their fitness with it.
- `villagerAndPillagerHeadsAreTheBestSurgeons` — with zombie arms: villager and pillager heads 200%; a witch's at least 150%; a zombie's between 75% and 150%; a blind villager's below a seeing one's. Chicken wings under a villager head cannot.
- `professionSetsItsKnack` — each profession's head (the old `villagerHeadOffersSurgeon` table) has knack 1.5 for its task and for surgeon; a nitwit's head is lower than an unemployed villager's at every task.
- `blindHeadIsPoorNotBarred` — with both eyes out, farmer, fisher, surgeon, sentry and hunter are still possible but lower, and sight is 4. One eye out changes nothing. Echolocate or tremor brings sight back to 12.
- `moreHandsWorkFaster` — eight zombie arms on a spider thorax give 1.6 times the two-armed fitness at Butcher and Farmer (before the cap), and strike 60% faster.
- `pawsAndHoovesPickPoorly` — wolf legs pick at 75% and cow legs at 45%; neither holds a surgeon's blade, and the legs' speed is unchanged.
- `knacksMergeAcrossLayers` — a farmer's zombie-villager head has its own file's surgeon 0.5 and the family's farmer 1.5; the villager keeps the biped's courier 1.25 and its own guard 1.
- `oldJobsListReadAsKnacks` — a test file still saying `"jobs": [fisher, courier, companion]` resolves to fisher 1.5 and courier 1.25, with one warning.
- `dispositionsScaleTheirKinds` — one body under meek, brave, berserk and nocturnal heads moves as the table says: nocturnal is 1.25 at midnight and 0.75 at noon, and loyal is 1.25 with me.
- `moddedMobWorksFromItsArchetype` — a mob with only archetype data gets every task its body allows, from its attributes and the archetype's knacks and leg grips.
- `taskKnackTraitCounts` (if built) — a gland's knack raises that task and no other.

**New, in the world:**
- `taskScreenRowsForMakerOnly` — the maker's crouching empty-hand use sends one row per task, matching `MinionFitness`. A stranger, a Deployer's stand-in and a powered-down minion get none, and folding still works.
- `setTaskFromTheScreen` — the maker sets Fisher at home, reach 6, with Home here, and it fishes there. A stranger's request, an impossible task, "with me" for a Farmer and a reach past the maximum are all refused. Everything survives saving and loading.
- `oldJobConvertsOnLoad` — the old jobs load as the table in 1.8 says, homes kept, including through a Dormant Minion item.
- `wakesToItsFittestTask` — a villager-headed build wakes as Surgeon and says so; a wolf head never wakes to Hunter, and a creeper's sac never to Sapper.
- `fitterButcherIsFasterAndCleaner` — over 20 s, a 200% butcher makes about four times the strokes of a 50% one on a like carcass, and the poor one gets about half a player's beef per cut.
- `poorHaulerCrawlsFitOneNoBetterThanAPlayer` — a rabbit-torso hauler tows a cow with nearly three times a player's slowdown; a horse hauler's slowdown is exactly a player's.
- `bloodAtWorkFollowsFitness` — couriers at 200% and 50% use 12.5 and 50 mB a minute at work, to within 10%.
- `guardWithMeIsAWolf` — it follows, and goes for what hurts its maker and what its maker hits; Idle with me follows and only fights back.
- `courierWithMeFillsTheMakersHands` — it picks up round the maker and hands the items over; holding an apple, it takes only apples.
- `hunterWithMeHuntsBesideTheMaker` — it hunts prey near the maker, not near home; with a Meat Hook the carcass is intact.
- `medicWithMeHealsOnTheMove` — it follows and throws at its hurt maker.
- `tenderFillsTroughsAndCradles` — blood buckets from a chest go into a trough and the empties come back; a Create tank of blood fills its buckets; canisters go from a chest into a cradle and the empties come back.
- `tenderWakesAFallenMinion` — a flesh minion lying powered down within reach gets blood and gets up; a brass one gets a canister.
- `butcherChopsAtTheTable` — pieces on a Butcher's Table by home are chopped.
- `headlessFightsOnlyWhatTouchesIt` — a headless Guard ignores a zombie 4 blocks off and hits one beside it.
- `sentryWithNoBowHoldsItsPost` — it strikes within reach and never moves.

**The surgeon, if the recommendation is taken:**
- `anySurgeonWithAHandCuts` — a zombie-headed surgeon cuts and leaves a stump of two buckets; a villager-headed one leaves one bucket, and cuts first when both are there; a headless one leaves three.
- `raggedStumpPaidAcrossContainers` — two buckets are paid from a bucket and a backtank together; one short is refused with "Needs blood".
- `oldRaggedStumpIsOneBucket` — an old saved ragged arm costs one bucket.

**Changed:**
- `amputationNeedsSurgeon`: a minion by the table on any other task does not count.
- `raggedStumpCostsBlood` and `raggedStumpPaidFromBacktank`: the price comes from the surgeon.
- `safetyFloorNeverNeedsSurgeon`: a crude prosthetic goes into a three-bucket stump for nothing.
- `surgeonKeepsToItsTable` and `unfoldedSurgeonTakesTheTableBesideIt`: tasks are set with `setTask`.
- Every job test that uses `switchTo` or `setJob` now uses `setTask`.
- `scavengerFetchesMatchingItem` and `filteredScavengerFetchesWhatItPasses` become courier tests.
- `buildCowOnFourRabbitLegs`: its best task is Herder.
- `wingsAreNoHands`: Surgeon cannot; a bow held in a beak is still no ranged attack.
- bb-organs' shaky-surgeon test reads the knack.

**Deleted, each replaced above:**
- `cycleJobWithEmptyHand`;
- `villagerHeadOffersSurgeon`;
- `pillagerHeadOffersSurgeon`;
- `nitwitOffersCompanionOnly`;
- `villagerAndPillagerHeadsOfferSurgeon`;
- `blindHeadLosesSightJobs`.

### 1.10 Order of work

Start after bb-organs is on main. This change touches its sapper, variants, `pace`, `MinionStats` and `MinionJobs`.
- **A. The fitness, worked out without a world (medium).** `MinionFitness`, knacks, leg grips, the disposition and task files, and the migrated data, with the tests worked out without a world. Nothing in play changes yet.
- **B. Tasks replace jobs (large).** `MinionTask`, the renames, anchor and reach, the screen, the wake rule, the status lines and the save conversion, with their tests.
- **C. The levers and blood at work (medium).**
- **D. The surgeon (small),** once the owner has answered.
- **E. Tender and the Butcher's Table (medium).**
- **F. Shown elsewhere (small):** JEI, tooltips, the command and the docs.

### 1.11 Found while reading (not part of this change)

- **Debugging left on bb-organs.** `MinionGoals.Bite.tick` logs "[tmpbite]" every 2 s, and there is a `gametest/TmpMergeRepro.java`. Both should go before bb-organs is merged.
- **`MINDLESS_SIGHT` (8) is more than `BLIND_SIGHT` (4).** A body with no head notices more than a blind head does. The design sets it to 2.
- **Keen Eye and Relentless do nothing on a minion.** They raise its follow-range attribute, but `MinionStats.sight` never reads it and every search takes the smaller of the two. The design's sight counts them.
- **`MinionStats.fights()` is true for a body with no arms and no head.** It would bite with no mouth. This is harmless today, because a mindless minion never fights.
- **`Surgery.payBlood` looks for one container holding the whole amount.** Stumps costing two or three buckets need it to add containers together.

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
