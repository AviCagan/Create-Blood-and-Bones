# For next time

The owner's list of what to take up next, newest decisions first. Each item says what was decided, what it replaces,
and what is still to be settled together before any code is written.

## 1. Tasks instead of jobs (decided 24 September 2026; not started)

**The decision.** There are to be no jobs. Any task, from a list we will draw up together, can be given to any minion.
Some minions are better at a task than others because of their own stats, which come from what they are built of.

**What it replaces.** Today a minion's head offers it one to three jobs (docs/PARTS-AND-TRAITS.md section 6.9, and
"Head: behaviour" in 6.4). This covers:
- the jobs list in `MinionStats.JOBS` and the hand rule in `HANDS`;
- each head's `jobs` data, with the villager-profession variants;
- blind heads losing the jobs that need sight;
- the crouch-click that cycles through the offered jobs.

All of that goes. The 17 goal packages already written are a ready first draft of the task list: companion, bodyguard,
guard, sentry, courier, scavenger, farmer, herder, fisher, hunter, hauler, butcher, surgeon, medic, barterer, digger and
sapper.

**To settle together first:**
- **The task list:** keep, merge or drop the 17, and add new ones.
- **What makes a minion good or bad at a task.** Which stats count for which task, and by how much. The parts already
  give it:
  - speed and movement mode (legs);
  - carry slots and weight (torso);
  - sight and follow range (head);
  - grip (hand, paw, claw, hoof, wing, tentacle, none);
  - strike damage and style (arms);
  - health, power and reservoir;
  - its traits.

  "Better" could mean faster, further, more yield, fewer mistakes, or less blood used.
- **Whether anything is ever impossible or only poor.** For example, butchering with no arms, fishing with no head, or
  hauling on a rabbit torso.
- **How a task is given:** a screen, an item such as a written task tag, or a click.
- **The surgeon.** The brief ties the amputation ritual's surgeon to villager and pillager heads. Does that stay, or
  does any minion become a surgeon, badly?
- **How a minion's fitness for each task is shown:** a tooltip, the Surgery Table's status line, or JEI.
- **The data:** how each part states its aptitude for tasks, so it stays data-driven (brief rule 3) and modded mobs
  work on day one (rule 2).

## 2. Already on the list

- **Open questions answered with the design's defaults** (docs/ARCHITECTURE-PROPOSAL.md 15.1). All fourteen are still
  open for the owner to confirm or overturn.
- **Known gaps:**
  - A lava-walking minion stands on lava but cannot walk across it. This is being built now.
  - Held items and helmets are not drawn on minions yet. This is also being built now.
  - `meatHookDragsByLeg` failed once in more than 50 runs (the drag physics under load); it is being watched.
- **Later slices** (docs/PARTS-AND-TRAITS.md section 9):
  - the balance pass and Ponder scenes (slice 9);
  - the Deployer route for fitting armour;
  - a performance test with many minions;
  - config multipliers tuned with the owner (slice 10).
