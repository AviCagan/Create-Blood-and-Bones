package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassBleeding;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassRest;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity;
import com.avicagan.bloodandbones.item.FlensingKnifeItem;
import com.avicagan.bloodandbones.network.MinionTaskPayload;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBTags;
import com.google.gson.JsonElement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The tasks at work (docs/NEXT.md 1.1): each task's package of goals, given to every minion and working only while it has
 * that task, and what giving a task takes: the task a minion wakes to, an old job read as a task, a data reload that leaves
 * its body unable to do its task, the task screen's rows and the requests it sends back (docs/NEXT.md 1.3), and what its
 * maker hands it to hold. The work: the guard, sentry, hunter, sapper ({@link MinionSapper}), surgeon (at its table,
 * {@link MinionGoals.AttendTable}), medic, herder, courier, hauler, farmer ({@link MinionGoals.Farm}), fisher, butcher,
 * barterer, digger and Tender ({@link MinionTender}); Idle only stays at home or follows its maker. Five tasks can be done
 * "with me", round the maker: Idle, Guard, Hunter, Medic and Courier.
 * <p>
 * What it holds its maker hands it: one of whatever they use on it (the old one comes back), and an empty hand takes it
 * back; arrows for its bow and a medic's healing potions go in with what it carries. A missing tool never moves it to
 * another task: it waits for one, and says so. What a courier fetches and what a herder leads by is whatever it holds. A
 * brass minion's filter ({@link MinionFilter}) narrows what the courier, herder, hunter, guard and sentry take.
 * <p>
 * None of them breaks or places a block, but for the sapper's blast where the server allows it ({@link MinionSapper}).
 * The two game events here are the sentry's: its arrows pass through its own side, and a crossbow's can be picked up.
 */
public final class MinionTasks {
    /** The least reach its maker may set (docs/NEXT.md 1.1); the most is twice the task's own, its data's "max_reach". */
    public static final int LEAST_REACH = 2;
    /** How near its maker must stand to use its task screen (docs/NEXT.md 1.3). */
    public static final double SCREEN_REACH = 8.0;
    /** A medic throws from this near. */
    public static final double THROW_RANGE = 8.0;
    /** The work a fisher or digger still has to do before its next catch or find, kept with the minion. */
    private static final String WORK_LEFT = BloodAndBones.MOD_ID + ":work_left";

    private MinionTasks() {
    }

    /** Every task's goals, given to every minion; each works only while the minion has its task. */
    static void goals(MinionEntity minion, GoalSelector goals, GoalSelector targets) {
        goals.addGoal(2, new Sentry(minion));
        goals.addGoal(2, new SentryStrike(minion));
        goals.addGoal(2, new MinionSapper.Sap(minion));
        goals.addGoal(3, new Fish(minion));
        goals.addGoal(3, new Dig(minion));
        goals.addGoal(3, new Barter(minion));
        goals.addGoal(3, new Butcher(minion));
        goals.addGoal(3, new Haul(minion));
        goals.addGoal(3, new Medic(minion));
        goals.addGoal(3, new Herd(minion));
        goals.addGoal(3, new MinionTender.Tend(minion));
        goals.addGoal(5, new Fetch(minion));
        // a sentry takes as its target a monster it can see within its range of its post, or with no ranged attack, one it
        // can strike from where it stands; a hunter the prey near where it hunts
        targets.addGoal(3, new NearestAttackableTargetGoal<>(minion, Mob.class, 10, true, false,
                target -> target instanceof Enemy && !(target instanceof MinionEntity) && minion.filter().allows(minion.level(), target)
                        && (minion.hasRangedAttack() || minion.isWithinMeleeAttackRange(target))) {
            @Override
            public boolean canUse() {
                return minion.hasTask(MinionTask.SENTRY) && !minion.stats().mindless() && minion.stats().fights() && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                LivingEntity target = minion.getTarget();
                // with no ranged attack, only while it can still strike it from its post
                return super.canContinueToUse() && (minion.hasRangedAttack() || target != null && minion.isWithinMeleeAttackRange(target));
            }

            @Override
            protected double getFollowDistance() {
                // asked first while it is being made, before it has a build
                return minion.build().isEmpty() ? 16.0 : Math.min(minion.reach(), minion.stats().sight());
            }
        });
        targets.addGoal(3, new Hunt(minion));
    }

    // ---- the task it wakes to, and an old job read as a task (docs/NEXT.md 1.3 and 1.8)

    /**
     * The task it wakes to: its fittest at home that waits on nothing it lacks, holding nothing (a villager-headed body made
     * at the table is ready to operate), ties going to the list's order; with none, Idle. Never Hunter, which would go
     * straight for the animals kept round the table it was made at, nor Sapper, which would spend its blast on the first
     * monster to wander by: its maker puts it to either.
     */
    public static MinionTask wakeTask(MinionEntity minion) {
        MinionTask best = MinionTask.IDLE;
        float fittest = 0.0F;
        for (MinionTask task : MinionTask.values()) {
            if (!wakes(task)) {
                continue;
            }
            MinionFitness.Row row = minion.row(task, MinionTask.Anchor.HOME);
            if (row.ready() && row.fitness() > fittest) {
                best = task;
                fittest = row.fitness();
            }
        }
        return best;
    }

    /** Whether it may wake to this task (see {@link #wakeTask}). */
    public static boolean wakes(MinionTask task) {
        return task.rated() && task != MinionTask.HUNTER && task != MinionTask.SAPPER;
    }

    /** Its maker's action bar as it wakes: "Woke as a Surgeon (200%)". */
    public static Component woke(MinionEntity minion) {
        MinionTask task = minion.task();
        return task.rated() ? Component.translatable("bloodandbones.minion.woke", TaskWords.name(task), TaskWords.percent(minion.row(task, MinionTask.Anchor.HOME).fitness()))
                : Component.translatable("bloodandbones.minion.woke_idle", TaskWords.name(task));
    }

    /**
     * A minion saved with a job, from before tasks (docs/NEXT.md 1.8): companion and bodyguard become Guard with its maker,
     * a guard Guard at home, a sentry Sentry at its post, a scavenger a Courier at home keeping what it holds as its sample,
     * any other job the same task at home, and a job no task is named after Idle at home. Its home is kept. Every old job was
     * offered under stricter rules than a task's, so it can do what it gets; should a datapack's retune say otherwise, it is
     * Idle (with its maker, if it was going to work with them).
     */
    static void fromJob(MinionEntity minion, String job) {
        MinionTask task;
        MinionTask.Anchor anchor = MinionTask.Anchor.HOME;
        ResourceLocation id = ResourceLocation.tryParse(job);
        String path = id != null && id.getNamespace().equals(BloodAndBones.MOD_ID) ? id.getPath() : "";
        switch (path) {
            case "companion", "bodyguard" -> {
                task = MinionTask.GUARD;
                anchor = MinionTask.Anchor.MAKER;
            }
            case "scavenger" -> task = MinionTask.COURIER;
            default -> {
                MinionTask named = MinionTask.byId(id);
                task = named == null ? MinionTask.IDLE : named;
            }
        }
        // its home stays as it was (an old sentry's home was its post already)
        BlockPos home = minion.home();
        if (!minion.setTask(task, anchor, 0) && !minion.setTask(MinionTask.IDLE, anchor, 0)) {
            minion.setTask(MinionTask.IDLE);
        }
        minion.setHome(home);
    }

    /**
     * A data reload that leaves its body unable to do its task (docs/NEXT.md 1.3) sets it to Idle at home, and its status
     * line says why; one that no longer lets its task be done with its maker brings it home. A missing tool never does
     * either: it waits for one.
     */
    static void keepPossible(MinionEntity minion) {
        MinionTask task = minion.task();
        if (minion.build().isEmpty() || minion.level().isClientSide) {
            return;
        }
        MinionFitness.Row row = minion.row(task, MinionTask.Anchor.HOME);
        if (!row.can()) {
            minion.loseTask(task, row.cannot().orElse("bloodandbones.minion.cannot.strike"));
        } else if (minion.anchor() == MinionTask.Anchor.MAKER && !PartsData.of(minion.level()).task(task).allows(MinionTask.Anchor.MAKER)) {
            minion.setTask(task, MinionTask.Anchor.HOME, 0);
        }
    }

    // ---- what it is doing, for its status line (docs/NEXT.md 1.4)

    /**
     * Its status line, for anyone's plain click: "Farmer 120% at home, blood 300 of 780 mB", and what it waits for ("waiting
     * for a Cleaver or a Flensing Knife", "no still water within 8 of home"), or why a data reload took its task.
     */
    public static Component status(MinionEntity minion) {
        Component doing = TaskWords.doing(minion);
        Component line;
        if (minion.poweredDown()) {
            line = Component.translatable("bloodandbones.minion.status_down", doing, Math.round(minion.power()), minion.stats().reservoir());
        } else if (!minion.filter().isEmpty()) {
            line = Component.translatable("bloodandbones.minion.status_filtered", doing, Math.round(minion.power()), minion.stats().reservoir(),
                    minion.filter().stack().getHoverName());
        } else {
            line = Component.translatable("bloodandbones.minion.status", doing, Math.round(minion.power()), minion.stats().reservoir());
        }
        Component more = minion.poweredDown() ? null : TaskWords.waiting(minion);
        return more == null ? line : Component.translatable("bloodandbones.minion.status_more", line, more);
    }

    // ---- the task screen (docs/NEXT.md 1.3)

    /**
     * Something that can be shown the task screen without a connection: a test's stand-in maker. A real player is sent it.
     */
    public interface Viewer {
        void showTasks(MinionTaskPayload.Open open);
    }

    /** Its maker's crouching empty hand on it awake: the task screen, its rows worked out here. Anyone else, or asleep: nothing. */
    public static void showScreen(MinionEntity minion, Player player) {
        open(minion, player).ifPresent(open -> send(player, open));
    }

    private static void send(Player player, MinionTaskPayload.Open open) {
        if (player instanceof ServerPlayer server && !(player instanceof FakePlayer)) {
            PacketDistributor.sendToPlayer(server, open);
        } else if (player instanceof Viewer viewer) {
            viewer.showTasks(open);
        }
    }

    /** Whether this player may use the task screen on it: its maker (not a machine's stand-in), near it, and it awake. */
    public static boolean mayDirect(MinionEntity minion, Player player) {
        return !(player instanceof FakePlayer) && minion.isMaker(player) && minion.isAlive() && !minion.poweredDown() && minion.build().isPresent()
                && player.level() == minion.level() && player.distanceToSqr(minion) <= SCREEN_REACH * SCREEN_REACH;
    }

    /**
     * The task screen's rows, one a task in the list's order, as this player would see them (docs/NEXT.md 1.3): each at the
     * anchor the minion works at if the task allows it, else at home; nothing for anyone who may not direct it.
     */
    public static Optional<MinionTaskPayload.Open> open(MinionEntity minion, Player player) {
        if (!mayDirect(minion, player)) {
            return Optional.empty();
        }
        PartsData.Store store = PartsData.of(minion.level());
        List<MinionTaskPayload.Row> rows = new ArrayList<>();
        for (MinionTask task : MinionTask.values()) {
            MinionTask.Data data = store.task(task);
            MinionFitness.Row row = minion.row(task, minion.anchor());
            int anchors = 0;
            for (MinionTask.Anchor anchor : data.anchors()) {
                anchors |= 1 << anchor.ordinal();
            }
            rows.add(new MinionTaskPayload.Row(task.ordinal(), row.fitness(), row.can(), row.waitsFor().isPresent(),
                    TaskWords.lines(store, minion, row, data.allows(minion.anchor()) ? minion.anchor() : MinionTask.Anchor.HOME), anchors, data.reach(),
                    data.maxReach()));
        }
        return Optional.of(new MinionTaskPayload.Open(minion.getId(), minion.getDisplayName(), minion.task().ordinal(), minion.anchor().ordinal(),
                minion.reachSet(), false, rows));
    }

    /**
     * A request from the task screen, checked here (docs/NEXT.md 1.3): from its maker, not a machine's stand-in, within 8
     * blocks; it awake; the task one its body can do; the anchor one the task allows; the reach 0 (the task's own) or within
     * its bounds. "Home here" sets home where it stands. Whatever was done, the screen is sent again. False, and nothing
     * changed, if it was refused.
     */
    public static boolean handle(Player player, MinionTaskPayload.Set request) {
        if (!(player.level().getEntity(request.minion()) instanceof MinionEntity minion) || !mayDirect(minion, player)
                || request.task() < 0 || request.task() >= MinionTask.values().length || request.anchor() < 0
                || request.anchor() >= MinionTask.Anchor.values().length) {
            return false;
        }
        MinionTask task = MinionTask.values()[request.task()];
        MinionTask.Anchor anchor = MinionTask.Anchor.values()[request.anchor()];
        boolean done = task == minion.task() && anchor == minion.anchor() && request.reach() == minion.reachSet()
                || minion.setTask(task, anchor, request.reach());
        if (done && request.homeHere()) {
            minion.setHome(minion.blockPosition());
        }
        open(minion, player).ifPresent(open -> send(player, open.refreshed()));
        return done;
    }

    // ---- what it holds

    /**
     * Its maker's hand on it, awake and standing: an item goes into its hand (one of it; what it held comes back),
     * an empty hand takes back what it holds. Arrows for the bow or crossbow it holds, and healing for a medic, go in with
     * what it carries instead, the whole stack, its hand kept. Null when this is not that (crouching opens its task screen
     * instead; saddled, an empty hand climbs on). Taking a tool away never changes its task: it waits for another.
     */
    @Nullable
    static InteractionResult handInteract(MinionEntity minion, Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive() || minion.poweredDown()) {
            return null;
        }
        ItemStack given = player.getItemInHand(hand);
        if (minion.level().isClientSide) {
            // the client does not know who made it: an item it may take is handed over, not used as well (a bow drawn)
            return !given.isEmpty() && takes(given) ? InteractionResult.SUCCESS : null;
        }
        if (!minion.isMaker(player)) {
            return null;
        }
        ItemStack holding = minion.getMainHandItem();
        if (given.isEmpty()) {
            if (holding.isEmpty() && hand == InteractionHand.MAIN_HAND && !minion.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && !(minion.isSaddled() && !minion.isVehicle())) {
                // nothing in its hand: the helmet comes off
                player.getInventory().placeItemBackInInventory(minion.getItemBySlot(EquipmentSlot.HEAD));
                minion.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                minion.level().playSound(null, minion.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 0.6F, 0.8F);
                return InteractionResult.CONSUME;
            }
            if (holding.isEmpty() || hand != InteractionHand.MAIN_HAND || minion.isSaddled() && !minion.isVehicle()) {
                return null;
            }
            minion.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(holding);
            minion.level().playSound(null, minion.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 0.8F);
            return InteractionResult.CONSUME;
        }
        if (!takes(given)) {
            return null;
        }
        if (stocks(minion, given)) {
            ItemStack left = minion.carry(given.copy());
            int taken = given.getCount() - left.getCount();
            if (taken <= 0) {
                player.displayClientMessage(Component.translatable("bloodandbones.minion.no_room"), true);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("bloodandbones.minion.carries", given.getHoverName()), true);
            if (!player.hasInfiniteMaterials()) {
                given.shrink(taken);
            }
            minion.level().playSound(null, minion.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 1.0F);
            return InteractionResult.CONSUME;
        }
        if (!minion.stats().mindless() && minion.getEquipmentSlotForItem(given) == EquipmentSlot.HEAD) {
            // a helmet (or a pumpkin) goes on its head, as a zombie's keeps the sun off it (section 8.1)
            ItemStack worn = minion.getItemBySlot(EquipmentSlot.HEAD);
            ItemStack one = given.copyWithCount(1);
            if (!player.hasInfiniteMaterials()) {
                given.shrink(1);
            }
            if (!worn.isEmpty()) {
                player.getInventory().placeItemBackInInventory(worn);
            }
            minion.setItemSlot(EquipmentSlot.HEAD, one);
            minion.setDropChance(EquipmentSlot.HEAD, 0.0F);
            minion.level().playSound(null, minion.blockPosition(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.NEUTRAL, 0.6F, 1.0F);
            player.displayClientMessage(Component.translatable("bloodandbones.minion.wears", one.getHoverName()), true);
            return InteractionResult.CONSUME;
        }
        if (minion.stats().mindless() && minion.stats().strikes().isEmpty()) {
            // no head to hold it in its mouth and no hand
            player.displayClientMessage(Component.translatable("bloodandbones.minion.cannot_hold"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack one = given.copyWithCount(1);
        if (!player.hasInfiniteMaterials()) {
            given.shrink(1);
        }
        if (!holding.isEmpty()) {
            player.getInventory().placeItemBackInInventory(holding);
        }
        minion.setItemSlot(EquipmentSlot.MAINHAND, one);
        // it is dropped with what it carries (MinionEntity#dropEquipment), never by the chance of mob loot
        minion.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        minion.level().playSound(null, minion.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 1.2F);
        player.displayClientMessage(Component.translatable("bloodandbones.minion.holds", one.getHoverName()), true);
        return InteractionResult.CONSUME;
    }

    /** What goes in with what it carries rather than into its hand: ammunition for the weapon it holds, healing for a medic. */
    private static boolean stocks(MinionEntity minion, ItemStack stack) {
        ItemStack held = minion.getMainHandItem();
        return held.getItem() instanceof ProjectileWeaponItem weapon && weapon.getAllSupportedProjectiles(held).test(stack)
                || minion.hasTask(MinionTask.MEDIC) && heals(stack);
    }

    /** What it takes into its hand: anything but what already does something to a mob (a saddle, a lead, a name tag, an egg). */
    private static boolean takes(ItemStack stack) {
        return !stack.is(Items.SADDLE) && !stack.is(Items.LEAD) && !stack.is(Items.NAME_TAG) && !(stack.getItem() instanceof SpawnEggItem)
                && !(stack.getItem() instanceof DormantMinionItem);
    }

    /** Killed (where minions may die): what it held drops with what it carried. */
    static void dropHeld(MinionEntity minion) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD}) {
            ItemStack held = minion.getItemBySlot(slot);
            if (!held.isEmpty()) {
                minion.spawnAtLocation(held);
                minion.setItemSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    /**
     * A bow, crossbow or trident in a hand that fights (a pacifist's pair of arms does not, nor a wing or a shell: a
     * held weapon needs an arm of hand grip, section 5.6), or null.
     */
    @Nullable
    static ItemStack heldWeapon(MinionEntity minion) {
        ItemStack held = minion.getMainHandItem();
        boolean weapon = held.getItem() instanceof BowItem || held.getItem() instanceof CrossbowItem || held.getItem() instanceof TridentItem;
        return weapon && minion.stats().strikes().stream().anyMatch(s -> "hand".equals(s.grip()) && !"pacifist".equals(s.style())) ? held : null;
    }

    /** Ammunition for this weapon from what it carries: the stack itself, so a shot takes one from it. */
    static ItemStack ammo(MinionEntity minion, ItemStack weapon) {
        ItemStack found = ItemStack.EMPTY;
        if (weapon.getItem() instanceof ProjectileWeaponItem item) {
            var fits = item.getAllSupportedProjectiles(weapon);
            for (int i = 0; i < minion.slots(); i++) {
                ItemStack stack = minion.inventory.getItem(i);
                if (!stack.isEmpty() && fits.test(stack)) {
                    found = stack;
                    break;
                }
            }
        }
        return net.neoforged.neoforge.common.CommonHooks.getProjectile(minion, weapon, found);
    }

    /** Put something it got into its inventory; what does not fit drops where it stands. */
    static void keep(MinionEntity minion, ItemStack stack) {
        ItemStack left = minion.carry(stack);
        if (!left.isEmpty()) {
            minion.spawnAtLocation(left);
        }
    }

    /** Whether one of the slots it can use is empty: room for whatever its work turns up. */
    static boolean freeSlot(MinionEntity minion) {
        for (int i = 0; i < minion.slots(); i++) {
            if (minion.inventory.getItem(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** For tests: its next catch or find comes at once, once it is at the water or the ground. */
    public static void hurry(MinionEntity minion) {
        minion.getPersistentData().putInt(WORK_LEFT, 0);
    }

    /**
     * A tick of work toward a catch or find: true when it comes, and the next wait starts. The wait is somewhere between
     * {@code times}' two (its fitness's), less what Lure takes off, never under {@code least}.
     */
    private static boolean worked(MinionEntity minion, int[] times, int less, int least) {
        CompoundTag data = minion.getPersistentData();
        int left = data.contains(WORK_LEFT) ? data.getInt(WORK_LEFT) : nextWait(minion, times, less, least);
        if (left <= 0) {
            data.putInt(WORK_LEFT, nextWait(minion, times, less, least));
            return true;
        }
        data.putInt(WORK_LEFT, left - 1);
        return false;
    }

    private static int nextWait(MinionEntity minion, int[] times, int less, int least) {
        int min = times[0];
        int max = Math.max(min, times[1]);
        return Math.max(least, min + minion.getRandom().nextInt(max - min + 1) - less);
    }

    // ---- sentry

    /**
     * A sentry never moves from its post: it turns to what it has for a target and shoots it with the bow, crossbow or
     * trident in its hand, in the way of vanilla's ranged goals (RangedBowAttackGoal, RangedCrossbowAttackGoal, the
     * drowned's throw) but standing still. Arrows come from what it carries; a trident is thrown as the drowned throws
     * one, and wears the one in hand. Its fitness (docs/NEXT.md 1.2) sets the time between its shots (a bow's second, a
     * crossbow's one to two, a trident's two at 100%, never under half) and how true they fly (vanilla's spread at 100%).
     */
    static class Sentry extends Goal {
        private final MinionEntity minion;
        private int cooldown;
        private int seeTime;

        Sentry(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = minion.getTarget();
            return minion.hasTask(MinionTask.SENTRY) && target != null && target.isAlive() && minion.hasRangedAttack();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            minion.getNavigation().stop();
            minion.setAggressive(true);
        }

        @Override
        public void stop() {
            minion.setAggressive(false);
            minion.stopUsingItem();
            seeTime = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = minion.getTarget();
            ItemStack weapon = heldWeapon(minion);
            if (target == null || weapon == null || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            minion.getNavigation().stop();
            minion.getLookControl().setLookAt(target, 30.0F, 30.0F);
            boolean sees = minion.getSensing().hasLineOfSight(target);
            seeTime = sees ? Math.max(0, seeTime) + 1 : Math.min(0, seeTime) - 1;
            // its range is its reach from its post (16 blocks unless its maker set it otherwise)
            double range = minion.reach();
            boolean near = minion.distanceToSqr(target) <= range * range;
            MinionTask.Data data = PartsData.of(level).task(MinionTask.SENTRY);
            float fitness = minion.taskFitness();
            float spread = MinionFitness.shotSpread(data, level.getDifficulty().getId(), fitness);
            cooldown--;
            if (weapon.getItem() instanceof BowItem) {
                if (minion.isUsingItem()) {
                    if (!sees && seeTime < -60) {
                        minion.stopUsingItem();
                    } else if (sees && minion.getTicksUsingItem() >= 20) {
                        int drawn = minion.getTicksUsingItem();
                        minion.stopUsingItem();
                        shootBow(level, minion, weapon, target, BowItem.getPowerForTime(drawn), spread);
                        cooldown = MinionFitness.shotTicks(data.number("bow_every", 20.0F), fitness);
                    }
                } else if (cooldown <= 0 && sees && near && !minion.getProjectile(weapon).isEmpty()) {
                    minion.startUsingItem(InteractionHand.MAIN_HAND);
                }
            } else if (weapon.getItem() instanceof CrossbowItem crossbow) {
                if (CrossbowItem.isCharged(weapon)) {
                    if (cooldown <= 0 && sees && near) {
                        loosing = minion;
                        try {
                            crossbow.performShooting(level, minion, InteractionHand.MAIN_HAND, weapon, 1.6F, spread, target);
                        } finally {
                            loosing = null;
                        }
                        int least = Math.round(data.number("crossbow_min", 20.0F));
                        int most = Math.max(least, Math.round(data.number("crossbow_max", 40.0F)));
                        cooldown = MinionFitness.shotTicks(least + minion.getRandom().nextInt(most - least + 1), fitness);
                    }
                } else if (minion.isUsingItem()) {
                    if (minion.getTicksUsingItem() >= CrossbowItem.getChargeDuration(weapon, minion)) {
                        // loaded from what it carries (MinionEntity#getProjectile)
                        minion.releaseUsingItem();
                        cooldown = MinionFitness.shotTicks(data.number("crossbow_min", 20.0F), fitness);
                    }
                } else if (sees && near && !minion.getProjectile(weapon).isEmpty()) {
                    minion.startUsingItem(InteractionHand.MAIN_HAND);
                }
            } else if (cooldown <= 0 && sees && near) {
                throwTrident(level, minion, weapon, target, spread);
                cooldown = MinionFitness.shotTicks(data.number("trident_every", 40.0F), fitness);
            }
        }
    }

    /**
     * A sentry with no ranged attack still never leaves its post: it strikes only what comes within its reach where it
     * stands, a blow as often as a melee goal lands one (vanilla's MeleeAttackGoal; sooner with more arms that strike,
     * {@link MinionGoals#blowTicks}), turning to it and taking no step.
     */
    static class SentryStrike extends Goal {
        private final MinionEntity minion;
        private int cooldown;

        SentryStrike(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = minion.getTarget();
            return minion.hasTask(MinionTask.SENTRY) && target != null && target.isAlive() && !minion.hasRangedAttack() && minion.stats().fights();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            minion.getNavigation().stop();
            minion.setAggressive(true);
        }

        @Override
        public void stop() {
            minion.setAggressive(false);
        }

        @Override
        public void tick() {
            LivingEntity target = minion.getTarget();
            if (target == null) {
                return;
            }
            minion.getNavigation().stop();
            minion.getLookControl().setLookAt(target, 30.0F, 30.0F);
            cooldown = Math.max(0, cooldown - 1);
            if (cooldown == 0 && minion.isWithinMeleeAttackRange(target) && minion.getSensing().hasLineOfSight(target)) {
                cooldown = MinionGoals.blowTicks(minion);
                minion.doHurtTarget(target);
            }
        }
    }

    /**
     * A bow shot as a skeleton looses one, the arrow taken from what it carries (one that lands can be picked up), with this
     * spread (a skeleton's is 14 less 4 a step of difficulty).
     */
    static void shootBow(ServerLevel level, MinionEntity minion, ItemStack bow, LivingEntity target, float power, float spread) {
        ItemStack ammo = minion.getProjectile(bow);
        if (ammo.isEmpty()) {
            return;
        }
        boolean free = EnchantmentHelper.processAmmoUse(level, bow, ammo, 1) == 0;
        ItemStack one = free ? ammo.copyWithCount(1) : ammo.split(1);
        AbstractArrow arrow = ProjectileUtil.getMobArrow(minion, one, power, bow);
        if (bow.getItem() instanceof ProjectileWeaponItem item) {
            arrow = item.customArrow(arrow, one, bow);
        }
        double dx = target.getX() - minion.getX();
        double dy = target.getY(1.0 / 3.0) - arrow.getY();
        double dz = target.getZ() - minion.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        arrow.shoot(dx, dy + flat * 0.2F, dz, 1.6F, spread);
        arrow.pickup = free ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
        minion.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (minion.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
        bow.hurtAndBreak(1, minion, EquipmentSlot.MAINHAND);
    }

    /** A trident thrown as the drowned throws one (a copy that cannot be picked up, loyal to nobody), with this spread; the one in hand wears. */
    static void throwTrident(ServerLevel level, MinionEntity minion, ItemStack trident, LivingEntity target, float spread) {
        ItemStack copy = trident.copyWithCount(1);
        EnchantmentHelper.updateEnchantments(copy, enchantments -> enchantments.removeIf(e -> e.is(Enchantments.LOYALTY) || e.is(Enchantments.RIPTIDE)));
        ThrownTrident thrown = new ThrownTrident(level, minion, copy);
        double dx = target.getX() - minion.getX();
        double dy = target.getY(1.0 / 3.0) - thrown.getY();
        double dz = target.getZ() - minion.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        thrown.shoot(dx, dy + flat * 0.2F, dz, 1.6F, spread);
        minion.playSound(SoundEvents.DROWNED_SHOOT, 1.0F, 1.0F / (minion.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(thrown);
        minion.swing(InteractionHand.MAIN_HAND);
        trident.hurtAndBreak(1, minion, EquipmentSlot.MAINHAND);
    }

    /** The sentry loosing its crossbow at this moment: the arrows that join the world meanwhile are its shot's. */
    @Nullable
    private static MinionEntity loosing;

    /**
     * A crossbow sentry's arrows can be picked up where they land, as its bow's can (vanilla allows that only for a
     * player's: AbstractArrow#setOwner). A multishot's copies and an infinite arrow stay creative-only.
     */
    @SubscribeEvent
    public static void onArrowLoosed(EntityJoinLevelEvent event) {
        if (loosing != null && event.getEntity() instanceof AbstractArrow arrow && arrow.getOwner() == loosing
                && arrow.pickup == AbstractArrow.Pickup.DISALLOWED) {
            arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        }
    }

    /**
     * A minion's arrows and tridents pass through its own side as if it were not there: its maker, its maker's other
     * minions, villagers. A sentry shooting at what chases its maker past it hits what chases them.
     */
    @SubscribeEvent
    public static void onFriendlyFire(ProjectileImpactEvent event) {
        if (event.getProjectile() instanceof AbstractArrow arrow && !arrow.level().isClientSide && arrow.getOwner() instanceof MinionEntity minion
                && event.getRayTraceResult() instanceof EntityHitResult hit && ally(minion, hit.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Whether this is on its side: its maker, its maker's other minions, or a villager (whom a medic heals too). */
    static boolean ally(MinionEntity minion, Entity other) {
        UUID maker = minion.makerId();
        return other instanceof AbstractVillager
                || maker != null && (maker.equals(other.getUUID()) || other instanceof MinionEntity them && maker.equals(them.makerId()));
    }

    // ---- courier

    /**
     * A courier picks up loose items within its reach (as far as its head sees them), as an allay does: holding one, only
     * items like it (its sample), and brass with a filter only what the filter passes too. At home it leaves them for the
     * container by home ({@link MinionGoals.Deposit}); with its maker, it brings them to its maker's hands, as an allay
     * brings its player what it found. It takes only what it has room for. It looks round every half second or so at 100%, a
     * fitter courier more often (docs/NEXT.md 1.2).
     */
    static class Fetch extends Goal {
        private final MinionEntity minion;
        @Nullable
        private ItemEntity item;
        private boolean bringing;
        private final MinionGoals.Approach approach = new MinionGoals.Approach();
        /** Items it could not get to, by entity id, forgotten every half minute. */
        private final List<Integer> unreachable = new ArrayList<>();
        private int forgotAt;

        Fetch(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.COURIER) || minion.getRandom().nextInt(MinionFitness.lookTicks(PartsData.of(minion.level()).task(MinionTask.COURIER),
                    minion.taskFitness())) != 0) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                unreachable.clear();
                forgotAt = minion.tickCount;
            }
            item = find();
            bringing = item == null && carrying() && minion.withMaker();
            return item != null || bringing;
        }

        @Override
        public boolean canContinueToUse() {
            return minion.hasTask(MinionTask.COURIER) && (item != null && item.isAlive() || bringing && minion.withMaker());
        }

        @Override
        public void start() {
            minion.working = true;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            item = null;
            bringing = false;
        }

        /** The nearest item it fetches that it has room for: within its reach of where it works, and as far as it sees. */
        @Nullable
        private ItemEntity find() {
            Vec3 centre = minion.centre();
            double reach = minion.reach();
            double sight = minion.stats().sight();
            List<ItemEntity> near = minion.level().getEntitiesOfClass(ItemEntity.class, new AABB(centre, centre).inflate(reach),
                    e -> e.isAlive() && !e.hasPickUpDelay() && !unreachable.contains(e.getId()) && fetches(minion, e.getItem()) && minion.canCarry(e.getItem())
                            && e.distanceToSqr(centre) < reach * reach && e.distanceToSqr(minion) < sight * sight);
            return near.stream().min(Comparator.comparingDouble(minion::distanceToSqr)).orElse(null);
        }

        /** Whether it carries anything it fetched, to bring. */
        private boolean carrying() {
            for (int i = 0; i < minion.slots(); i++) {
                if (fetches(minion, minion.inventory.getItem(i))) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public void tick() {
            if (item != null) {
                if (minion.distanceToSqr(item) < 2.5 + minion.getBbWidth()) {
                    ItemStack left = minion.carry(item.getItem().copy());
                    minion.take(item, item.getItem().getCount() - left.getCount());
                    if (left.isEmpty()) {
                        item.discard();
                    } else {
                        item.setItem(left);
                    }
                    minion.level().playSound(null, minion.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.NEUTRAL, 0.6F, 0.9F);
                    // the next one, or to its maker with them
                    item = find();
                    bringing = item == null && minion.withMaker();
                    approach.reset(minion);
                } else if (!approach.step(minion, item.blockPosition(), 1, 1.1)) {
                    unreachable.add(item.getId());
                    item = null;
                }
                return;
            }
            Player maker = minion.workingMaker();
            if (!bringing || maker == null) {
                return;
            }
            minion.getLookControl().setLookAt(maker);
            if (minion.distanceToSqr(maker) < Math.pow(2.5 + minion.getBbWidth(), 2)) {
                // into its maker's hands
                for (int i = 0; i < minion.inventory.getContainerSize(); i++) {
                    if (fetches(minion, minion.inventory.getItem(i))) {
                        maker.getInventory().placeItemBackInInventory(minion.inventory.removeItemNoUpdate(i));
                    }
                }
                minion.level().playSound(null, minion.blockPosition(), SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 0.6F, 0.9F);
                minion.swing(InteractionHand.MAIN_HAND);
                bringing = false;
            } else if (!approach.step(minion, maker.blockPosition(), 2, 1.1)) {
                bringing = false;
            }
        }
    }

    /** What a courier fetches: like what it holds (anything, if it holds nothing), and what its filter passes. */
    static boolean fetches(MinionEntity minion, ItemStack stack) {
        ItemStack held = minion.getMainHandItem();
        return !stack.isEmpty() && (held.isEmpty() || alike(held, stack)) && minion.filter().allows(minion.level(), stack);
    }

    /** Someone's own, which a herder and a hunter leave be: tamed (a pet, a broken-in horse or llama) or named. */
    static boolean owned(Mob mob) {
        return mob instanceof TamableAnimal tame && tame.isTame() || mob instanceof AbstractHorse horse && horse.isTamed() || mob.hasCustomName();
    }

    /** Items the allay's way alike: the same item, and the same potion in it. */
    static boolean alike(ItemStack held, ItemStack stack) {
        return !stack.isEmpty() && ItemStack.isSameItem(held, stack)
                && Objects.equals(held.get(DataComponents.POTION_CONTENTS), stack.get(DataComponents.POTION_CONTENTS));
    }

    // ---- herder

    /**
     * A herder keeps the animals that would follow what it holds (wheat for cattle and sheep, seeds for chickens) within its
     * reach of home (8): it looks out as far again and more for strays (20 at its own reach), walks out to one and leads it
     * back, the stray walking after it, as an animal follows a player with its food, giving up on it after half a minute.
     * Holding nothing, it herds nothing, and waits for food. Never someone's own animal (tamed or named), whom its owner has
     * put where it is. Brass with a filter herds only the animals it passes.
     */
    static class Herd extends Goal {
        private final MinionEntity minion;
        @Nullable
        private Animal stray;
        private boolean leading;
        private int startedAt;
        private final MinionGoals.Approach approach = new MinionGoals.Approach();
        private final List<Integer> lost = new ArrayList<>();
        private int forgotAt;

        Herd(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            ItemStack held = minion.getMainHandItem();
            if (!minion.hasTask(MinionTask.HERDER) || held.isEmpty() || minion.getRandom().nextInt(20) != 0) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                lost.clear();
                forgotAt = minion.tickCount;
            }
            Vec3 home = Vec3.atBottomCenterOf(minion.home());
            double keep = minion.reach();
            double search = search(minion);
            double range = Math.min(search, minion.stats().sight());
            stray = minion.level().getEntitiesOfClass(Animal.class, new AABB(minion.home()).inflate(search),
                            a -> a.isAlive() && a.isFood(held) && minion.filter().allows(minion.level(), a) && !a.isLeashed() && !a.isVehicle()
                                    && !a.isPassenger() && !owned(a) && !lost.contains(a.getId())
                                    && a.distanceToSqr(home) > keep * keep && a.distanceToSqr(minion) < range * range)
                    .stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(home))).orElse(null);
            return stray != null;
        }

        @Override
        public boolean canContinueToUse() {
            double keep = minion.reach() - 2.0;
            return stray != null && stray.isAlive() && minion.hasTask(MinionTask.HERDER) && minion.tickCount - startedAt < giveUp(minion)
                    && stray.distanceToSqr(Vec3.atBottomCenterOf(minion.home())) > keep * keep;
        }

        @Override
        public void start() {
            minion.working = true;
            leading = false;
            startedAt = minion.tickCount;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            if (stray != null && minion.tickCount - startedAt >= giveUp(minion)) {
                lost.add(stray.getId());
            }
            if (stray != null) {
                stray.getNavigation().stop();
            }
            stray = null;
        }

        @Override
        public void tick() {
            if (stray == null) {
                return;
            }
            double close = 2.5 + minion.getBbWidth() / 2.0 + stray.getBbWidth() / 2.0;
            if (!leading) {
                minion.getLookControl().setLookAt(stray);
                if (minion.distanceToSqr(stray) < close * close) {
                    leading = true;
                    approach.reset(minion);
                } else if (!approach.step(minion, stray.blockPosition(), 1, 1.1)) {
                    lost.add(stray.getId());
                    stray = null;
                }
                return;
            }
            // it walks home; the stray walks after what it holds
            if (minion.distanceToSqr(stray) > 10.0 * 10.0) {
                leading = false;
                approach.reset(minion);
                return;
            }
            if (minion.tickCount % 5 == 0) {
                stray.getNavigation().moveTo(minion, 1.2);
                stray.getLookControl().setLookAt(minion);
            }
            if (minion.distanceToSqr(stray) > close * close * 1.5) {
                // it waits for the stray to catch up
                minion.getNavigation().stop();
                minion.getLookControl().setLookAt(stray);
            } else if (!approach.step(minion, minion.home(), 1, 0.8)) {
                lost.add(stray.getId());
                stray = null;
            }
        }
    }

    /** How far out a herder looks for strays: its task's "search" (20) at its own reach, and as much more as its reach is more. */
    static double search(MinionEntity minion) {
        MinionTask.Data data = PartsData.of(minion.level()).task(MinionTask.HERDER);
        return data.number("search", 20.0F) * minion.reach() / Math.max(1, data.reach());
    }

    /** How long a herder keeps after one stray before it gives up on it: its task's "wait", 30 s at 100%, a fitter herder longer. */
    private static int giveUp(MinionEntity minion) {
        return MinionFitness.strayTicks(PartsData.of(minion.level()).task(MinionTask.HERDER), minion.taskFitness());
    }

    // ---- fisher

    /**
     * A fisher by still water within its reach of home rolls the fishing loot table every 30 to 60 seconds it spends there
     * at 100% (a fitter fisher sooner; less with Lure; never under a sixth of 30 s, Lure's own floor; no treasure, which needs
     * a bobber in open water), into what it carries: with a rod in hand as a player's rod does, a point off it a catch;
     * without one, by hand, paw or mouth (a fish's mouth as well as a rod).
     */
    static class Fish extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos water;
        private boolean caught;
        private final MinionGoals.Unreachable unreachable = new MinionGoals.Unreachable();
        private final MinionGoals.Approach approach = new MinionGoals.Approach();

        Fish(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.FISHER) || minion.getRandom().nextInt(20) != 0 || !minion.canCarry(new ItemStack(Items.COD))) {
                return false;
            }
            water = find();
            if (water == null) {
                minion.idle(Component.translatable("bloodandbones.minion.idle.fisher", minion.reach()));
            }
            return water != null;
        }

        @Nullable
        private BlockPos find() {
            BlockPos home = minion.home();
            int reach = minion.reach();
            BlockPos from = home.offset(-reach, -2, -reach);
            BlockPos to = home.offset(reach, 2, reach);
            if (!MinionGoals.loaded(minion, from, to)) {
                return null;
            }
            BlockPos best = null;
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (fishable(pos) && !unreachable.contains(minion, pos) && (best == null || pos.distSqr(minion.blockPosition()) < best.distSqr(minion.blockPosition()))) {
                    best = pos.immutable();
                }
            }
            return best;
        }

        /** Still water with open air over it. */
        private boolean fishable(BlockPos pos) {
            var fluid = minion.level().getFluidState(pos);
            return fluid.is(FluidTags.WATER) && fluid.isSource() && minion.level().getBlockState(pos.above()).isAir();
        }

        @Override
        public boolean canContinueToUse() {
            return water != null && !caught && minion.hasTask(MinionTask.FISHER) && minion.level().isLoaded(water) && fishable(water);
        }

        @Override
        public void start() {
            minion.working = true;
            caught = false;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            water = null;
        }

        @Override
        public void tick() {
            if (water == null || caught || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 at = Vec3.atCenterOf(water);
            double reach = MinionGoals.accuracy(minion) + 1.5 + minion.getBbWidth() / 2.0;
            if (minion.distanceToSqr(at) > reach * reach) {
                if (!approach.step(minion, water, MinionGoals.accuracy(minion) + 1, 1.0)) {
                    unreachable.add(water);
                    water = null;
                }
                return;
            }
            approach.reset(minion);
            minion.getNavigation().stop();
            minion.getLookControl().setLookAt(at);
            ItemStack rod = minion.getMainHandItem();
            boolean byRod = rod.getItem() instanceof FishingRodItem && !minion.stats().strikes().isEmpty();
            int lure = byRod ? Math.round(EnchantmentHelper.getFishingTimeReduction(level, rod, minion) * 20.0F) : 0;
            MinionTask.Data data = PartsData.of(level).task(MinionTask.FISHER);
            if (worked(minion, MinionFitness.catchTicks(data, minion.taskFitness()), lure, MinionFitness.catchLeast(data))) {
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, at)
                        .withParameter(LootContextParams.TOOL, byRod ? rod : ItemStack.EMPTY)
                        .withParameter(LootContextParams.ATTACKING_ENTITY, minion)
                        .withLuck(byRod ? EnchantmentHelper.getFishingLuckBonus(level, rod, minion) : 0.0F)
                        .create(LootContextParamSets.FISHING);
                for (ItemStack stack : level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params)) {
                    keep(minion, stack);
                }
                level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 12, 0.3, 0.1, 0.3, 0.1);
                level.playSound(null, water, SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL, 1.0F, 0.9F);
                minion.swing(InteractionHand.MAIN_HAND);
                if (byRod) {
                    rod.hurtAndBreak(1, minion, EquipmentSlot.MAINHAND);
                }
                caught = true;
            }
        }
    }

    // ---- digger

    /**
     * A digger sniffs the grass, moss and dirt within its reach of home (vanilla's sniffer_diggable_block tag), and every
     * minute or two it spends at it (at 100%; a fitter digger sooner) turns up what a sniffer digs (the sniffer_digging loot
     * table), into what it carries. It leaves the ground as it was.
     */
    static class Dig extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos spot;
        private boolean found;
        private final List<BlockPos> dug = new ArrayList<>();
        private final MinionGoals.Unreachable unreachable = new MinionGoals.Unreachable();
        private final MinionGoals.Approach approach = new MinionGoals.Approach();

        Dig(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.DIGGER) || minion.getRandom().nextInt(20) != 0 || !minion.canCarry(new ItemStack(Items.TORCHFLOWER_SEEDS))) {
                return false;
            }
            spot = find();
            if (spot == null) {
                minion.idle(Component.translatable("bloodandbones.minion.idle.digger", minion.reach()));
            }
            return spot != null;
        }

        /** A diggable block with room over it, not one of the last few it dug, picked at random. */
        @Nullable
        private BlockPos find() {
            BlockPos home = minion.home();
            int reach = minion.reach();
            BlockPos from = home.offset(-reach, -2, -reach);
            BlockPos to = home.offset(reach, 2, reach);
            if (!MinionGoals.loaded(minion, from, to)) {
                return null;
            }
            List<BlockPos> spots = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (diggable(pos) && !dug.contains(pos) && !unreachable.contains(minion, pos)) {
                    spots.add(pos.immutable());
                }
            }
            return spots.isEmpty() ? null : spots.get(minion.getRandom().nextInt(spots.size()));
        }

        private boolean diggable(BlockPos pos) {
            return minion.level().getBlockState(pos).is(BlockTags.SNIFFER_DIGGABLE_BLOCK) && !minion.level().getBlockState(pos.above()).isSolid();
        }

        @Override
        public boolean canContinueToUse() {
            return spot != null && !found && minion.hasTask(MinionTask.DIGGER) && minion.level().isLoaded(spot) && diggable(spot);
        }

        @Override
        public void start() {
            minion.working = true;
            found = false;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            spot = null;
        }

        @Override
        public void tick() {
            if (spot == null || found || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            Vec3 top = Vec3.atCenterOf(spot).add(0.0, 0.5, 0.0);
            double reach = 1.5 + minion.getBbWidth() / 2.0;
            if (minion.distanceToSqr(top) > reach * reach) {
                if (!approach.step(minion, spot.above(), MinionGoals.accuracy(minion) - 1, 1.0)) {
                    unreachable.add(spot);
                    spot = null;
                }
                return;
            }
            approach.reset(minion);
            minion.getNavigation().stop();
            minion.getLookControl().setLookAt(top.x, top.y - 0.5, top.z);
            BlockState ground = level.getBlockState(spot);
            if (minion.tickCount % 20 == 0) {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), top.x, top.y, top.z, 6, 0.3, 0.05, 0.3, 0.05);
                level.playSound(null, spot, SoundEvents.SNIFFER_SNIFFING, SoundSource.NEUTRAL, 0.6F, 1.1F);
            }
            MinionTask.Data data = PartsData.of(level).task(MinionTask.DIGGER);
            if (worked(minion, MinionFitness.digTicks(data, minion.taskFitness()), 0, 1)) {
                LootParams params = new LootParams.Builder(level)
                        .withParameter(LootContextParams.ORIGIN, top)
                        .withParameter(LootContextParams.THIS_ENTITY, minion)
                        .create(LootContextParamSets.GIFT);
                for (ItemStack stack : level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.SNIFFER_DIGGING).getRandomItems(params)) {
                    keep(minion, stack);
                }
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), top.x, top.y, top.z, 20, 0.3, 0.1, 0.3, 0.1);
                level.playSound(null, spot, SoundEvents.SNIFFER_DIGGING_STOP, SoundSource.NEUTRAL, 1.0F, 1.0F);
                minion.swing(InteractionHand.MAIN_HAND);
                dug.add(spot);
                if (dug.size() > 6) {
                    dug.remove(0);
                }
                found = true;
            }
        }
    }

    // ---- barterer

    /**
     * A barterer takes a gold ingot from a container within its reach of home, looks it over as a piglin does (six seconds at
     * 100%, a fitter barterer sooner), and
     * rolls the piglin_bartering loot table, putting what it got back into that container (what does not fit it keeps,
     * for the next trip to it). It trades only while it has a slot free for what it gets, so a full chest never has its
     * gold turned into litter; and the ingot it looks over is in what it carries, so it is saved, folded and dropped
     * with it, never lost.
     */
    static class Barter extends Goal {
        private final MinionEntity minion;
        @Nullable
        private BlockPos chest;
        /** It took an ingot and is looking it over. */
        private boolean admiring;
        private int admired;
        private boolean traded;
        private final MinionGoals.Unreachable unreachable = new MinionGoals.Unreachable();
        private final MinionGoals.Approach approach = new MinionGoals.Approach();

        Barter(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.BARTERER) || minion.getRandom().nextInt(20) != 0 || !freeSlot(minion)) {
                return false;
            }
            chest = find();
            if (chest == null) {
                minion.idle(Component.translatable("bloodandbones.minion.idle.barterer", minion.reach()));
            }
            return chest != null;
        }

        /** The nearest container by home with gold in it. */
        @Nullable
        private BlockPos find() {
            BlockPos home = minion.home();
            int reach = minion.reach();
            BlockPos from = home.offset(-reach, -2, -reach);
            BlockPos to = home.offset(reach, 2, reach);
            if (!MinionGoals.loaded(minion, from, to)) {
                return null;
            }
            BlockPos best = null;
            for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
                if (!unreachable.contains(minion, pos) && goldSlot(pos) >= 0 && (best == null || pos.distSqr(home) < best.distSqr(home))) {
                    best = pos.immutable();
                }
            }
            return best;
        }

        private int goldSlot(BlockPos pos) {
            if (minion.level().getBlockState(pos).getBlock() instanceof BloodTroughBlock) {
                return -1;
            }
            IItemHandler handler = minion.level().getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
            if (handler == null) {
                return -1;
            }
            for (int i = 0; i < handler.getSlots(); i++) {
                if (handler.getStackInSlot(i).is(Items.GOLD_INGOT) && !handler.extractItem(i, 1, true).isEmpty()) {
                    return i;
                }
            }
            return -1;
        }

        @Override
        public boolean canContinueToUse() {
            return chest != null && !traded && minion.hasTask(MinionTask.BARTERER) && minion.level().isLoaded(chest);
        }

        @Override
        public void start() {
            minion.working = true;
            traded = false;
            admiring = false;
            admired = 0;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            // called away before it traded, the gold stays in what it carries (and goes back to the chest with the rest)
            minion.working = false;
            chest = null;
        }

        @Override
        public void tick() {
            if (chest == null || traded || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            if (minion.distanceToSqr(Vec3.atCenterOf(chest)) > 6.0 + minion.getBbWidth() * 2) {
                if (!approach.step(minion, chest, 1, 1.0)) {
                    unreachable.add(chest);
                    chest = null;
                }
                return;
            }
            approach.reset(minion);
            minion.getNavigation().stop();
            minion.getLookControl().setLookAt(Vec3.atCenterOf(chest));
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, chest, null);
            if (handler == null) {
                chest = null;
                return;
            }
            if (!admiring) {
                int slot = goldSlot(chest);
                if (slot < 0 || !freeSlot(minion)) {
                    chest = null;
                    return;
                }
                keep(minion, handler.extractItem(slot, 1, false));
                admiring = true;
                admired = 0;
                level.playSound(null, minion.blockPosition(), SoundEvents.PIGLIN_ADMIRING_ITEM, SoundSource.NEUTRAL, 1.0F, 1.0F);
                return;
            }
            if (++admired < MinionFitness.admireTicks(PartsData.of(level).task(MinionTask.BARTERER), minion.taskFitness())) {
                return;
            }
            admiring = false;
            if (minion.inventory.removeItemType(Items.GOLD_INGOT, 1).isEmpty()) {
                // the ingot is gone from what it carries: no trade
                chest = null;
                return;
            }
            LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.THIS_ENTITY, minion).create(LootContextParamSets.PIGLIN_BARTER);
            for (ItemStack stack : level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PIGLIN_BARTERING).getRandomItems(params)) {
                ItemStack left = ItemHandlerHelper.insertItemStacked(handler, stack, false);
                if (!left.isEmpty()) {
                    keep(minion, left);
                }
            }
            minion.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, minion.blockPosition(), SoundEvents.PIGLIN_CELEBRATE, SoundSource.NEUTRAL, 0.8F, 1.0F);
            traded = true;
        }
    }

    // ---- hunter

    /**
     * A hunter takes for its target grown prey within its reach of where it hunts (home, or its maker while it hunts beside
     * them) that it can see (its head's data names the prey, "prey": ids and #tags of any passive mob, a fish too, else
     * #bloodandbones:hunter_prey), never a named, tamed, leashed or ridden one; its bite or arms kill it. The rules hold for
     * the whole chase: prey leashed or named meanwhile, or run off from its hunting ground, is let be. It hunts only where
     * mobs may do harm (the mobGriefing rule, spec section 10), and waits for it otherwise; it is never woken to hunting
     * ({@link #wakeTask}). With a Meat Hook in hand the kill leaves an intact carcass, exactly as a player's Meat Hook kill
     * does (CarcassEvents#onDeath reads the killer's hand). Brass with a filter hunts only the prey it passes.
     */
    static class Hunt extends NearestAttackableTargetGoal<PathfinderMob> {
        private final MinionEntity minion;
        private List<String> prey = List.of();

        Hunt(MinionEntity minion) {
            super(minion, PathfinderMob.class, 10, true, false, null);
            this.minion = minion;
            targetConditions = targetConditions.selector(e -> e instanceof PathfinderMob mob && hunts(mob));
        }

        /** A hunter with a head and a blow to strike, where mobs may do harm. */
        private boolean hunting() {
            return minion.hasTask(MinionTask.HUNTER) && !minion.stats().mindless() && minion.stats().fights() && EventHooks.canEntityGrief(minion.level(), minion);
        }

        @Override
        public boolean canUse() {
            if (!hunting()) {
                return false;
            }
            prey = prey(minion);
            return super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return hunting() && minion.getTarget() instanceof PathfinderMob target && hunts(target) && super.canContinueToUse();
        }

        @Override
        protected double getFollowDistance() {
            // asked once while it is being made, before it knows its minion: as far again as its reach, as far as it sees
            return minion == null || minion.build().isEmpty() ? 24.0 : Math.min(minion.reach() * 2.0, minion.stats().sight());
        }

        /** Prey on its hunting ground: within its reach of home, or of its maker while it hunts beside them. */
        private boolean hunts(PathfinderMob mob) {
            double reach = minion.reach();
            return mob.distanceToSqr(minion.centre()) <= reach * reach && isPrey(minion, mob, prey);
        }
    }

    /**
     * Whether a hunter goes for this mob wherever it is: grown prey its head names ({@code names}, or with none the default
     * tag), neither a monster nor a minion, nobody's own, not leashed or ridden, and passed by its filter.
     */
    static boolean isPrey(MinionEntity minion, PathfinderMob mob, List<String> names) {
        if (mob instanceof Enemy || mob instanceof MinionEntity || mob.isBaby() || owned(mob) || mob.isLeashed() || mob.isVehicle() || mob.isPassenger()
                || !minion.filter().allows(minion.level(), mob)) {
            return false;
        }
        if (names.isEmpty()) {
            return mob.getType().is(BBTags.HUNTER_PREY);
        }
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        for (String name : names) {
            if (name.startsWith("#") ? mob.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(name.substring(1))))
                    : id.toString().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /** What its head hunts, as written ("minecraft:rabbit", "#minecraft:axolotl_hunt_targets"); empty for the default tag. */
    static List<String> prey(MinionEntity minion) {
        MinionBuild build = minion.build().orElse(null);
        PartsData.Store store = PartsData.of(minion.level());
        PieceRef head = build == null ? null : MinionStats.head(store, build);
        if (head == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        MinionData.field(store.resolve(head.entity(), head.baby()), head.traits(), "head", "prey").filter(JsonElement::isJsonArray)
                .ifPresent(a -> a.getAsJsonArray().forEach(e -> out.add(e.getAsString())));
        return out;
    }

    // ---- medic

    /**
     * A medic throws a splash potion of healing (or regeneration) from what it carries at an ally two hearts or more down
     * within its reach of where it works (home, or its maker while it goes with them): its maker, its maker's other flesh
     * minions, a villager. Brass it leaves to its brass sheets and cradle, which are what mend brass (spec 6.6). It closes
     * to throwing range, then throws as a witch does. With no healing potions it waits for some. Its fitness (docs/NEXT.md
     * 1.2) sets the time between throws (3 s at 100%, never under 1 s) and how true they fly (a witch's spread at 100%).
     */
    static class Medic extends Goal {
        private final MinionEntity minion;
        @Nullable
        private LivingEntity patient;
        private int cooldown;
        private int startedAt;
        private boolean thrown;
        private final MinionGoals.Approach approach = new MinionGoals.Approach();

        Medic(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.MEDIC) || minion.tickCount < cooldown || minion.getRandom().nextInt(10) != 0 || potion(minion) < 0) {
                return false;
            }
            patient = patient();
            return patient != null;
        }

        /** The worst-hurt ally it can see. */
        @Nullable
        private LivingEntity patient() {
            double range = Math.min(minion.reach(), minion.stats().sight());
            double reach = minion.reach();
            Vec3 centre = minion.centre();
            List<LivingEntity> allies = new ArrayList<>();
            Player maker = minion.maker();
            if (maker != null && maker.level() == minion.level() && !maker.isSpectator()) {
                allies.add(maker);
            }
            allies.addAll(minion.level().getEntitiesOfClass(LivingEntity.class, minion.getBoundingBox().inflate(range),
                    e -> e != minion && (e instanceof AbstractVillager || e instanceof MinionEntity other && !other.poweredDown() && !other.cybernetic()
                            && minion.makerId() != null && minion.makerId().equals(other.makerId()))));
            return allies.stream().filter(e -> e.isAlive() && hurt(e) && e.distanceToSqr(minion) < range * range && e.distanceToSqr(centre) < reach * reach
                            && minion.hasLineOfSight(e))
                    .min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
        }

        private static boolean hurt(LivingEntity entity) {
            return entity.getHealth() <= entity.getMaxHealth() - 4.0F;
        }

        @Override
        public boolean canContinueToUse() {
            return patient != null && patient.isAlive() && !thrown && hurt(patient) && minion.hasTask(MinionTask.MEDIC) && minion.tickCount - startedAt < 400
                    && patient.level() == minion.level() && potion(minion) >= 0;
        }

        @Override
        public void start() {
            minion.working = true;
            thrown = false;
            startedAt = minion.tickCount;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            patient = null;
        }

        @Override
        public void tick() {
            // its tick runs every tick, but whether it goes on is asked only every other one: one throw a turn
            if (patient == null || thrown || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            minion.getLookControl().setLookAt(patient);
            if (minion.distanceToSqr(patient) > THROW_RANGE * THROW_RANGE || !minion.hasLineOfSight(patient)) {
                if (!approach.step(minion, patient.blockPosition(), 2, 1.1)) {
                    patient = null;
                }
                return;
            }
            approach.reset(minion);
            minion.getNavigation().stop();
            int slot = potion(minion);
            if (slot < 0) {
                return;
            }
            // thrown as a witch throws, leading the target a little
            ItemStack stack = minion.inventory.getItem(slot).split(1);
            Vec3 lead = patient.getDeltaMovement();
            double dx = patient.getX() + lead.x - minion.getX();
            double dy = patient.getEyeY() - 1.1F - minion.getY();
            double dz = patient.getZ() + lead.z - minion.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            ThrownPotion potion = new ThrownPotion(level, minion);
            potion.setItem(stack);
            potion.setXRot(potion.getXRot() + 20.0F);
            MinionTask.Data data = PartsData.of(level).task(MinionTask.MEDIC);
            potion.shoot(dx, dy + flat * 0.2, dz, 0.75F, MinionFitness.throwSpread(data, minion.taskFitness()));
            level.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.WITCH_THROW, SoundSource.NEUTRAL, 1.0F,
                    0.8F + minion.getRandom().nextFloat() * 0.4F);
            level.addFreshEntity(potion);
            minion.swing(InteractionHand.MAIN_HAND);
            cooldown = minion.tickCount + MinionFitness.throwTicks(data, minion.taskFitness());
            thrown = true;
        }
    }

    /** The slot of a splash (or lingering) potion that heals: instant health or regeneration. -1 for none. */
    static int potion(MinionEntity minion) {
        for (int i = 0; i < minion.slots(); i++) {
            if (heals(minion.inventory.getItem(i))) {
                return i;
            }
        }
        return -1;
    }

    /** A splash or lingering potion of instant health or regeneration: what a medic throws. */
    static boolean heals(ItemStack stack) {
        if (stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION)) {
            PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            for (var effect : contents.getAllEffects()) {
                if (effect.is(MobEffects.HEAL) || effect.is(MobEffects.REGENERATION)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- butcher

    /** A butcher's blade: a Cleaver, or a Flensing Knife. With neither in hand it waits for one. */
    static boolean blade(ItemStack stack) {
        return stack.getItem() instanceof com.avicagan.bloodandbones.item.CleaverItem || stack.getItem() instanceof FlensingKnifeItem;
    }

    /**
     * A butcher takes carcasses within its reach of home apart by hand (docs/PARTS-AND-TRAITS.md section 6.9), a stroke every
     * 0.75 s at 100%, through the same CarcassButchery a player's Cleaver or Flensing Knife uses, so the yields are a player's
     * hand yields: with a Cleaver it breaks down loose pieces and cuts limbs off whole bodies; with a Flensing Knife it
     * skins them. With a Cleaver it also chops the pieces laid on a Butcher's Table within its reach of home, a stroke a
     * piece, as a Deployer holding one does (ButcherTableBlockEntity#chop). Whichever lies nearest home goes first. What
     * comes off goes into what it carries. Its fitness (docs/NEXT.md 1.2) sets how often it strokes (a fitter butcher sooner,
     * never under 0.3 s) and, below 100%, how much of each cut it wastes: its yields times its fitness, so no butcher beats
     * hand yields.
     */
    static class Butcher extends Goal {
        /** How far over or under its feet a piece may lie for it to cut: a resting body's torso lies a block up. */
        private static final double REACH_UP = 2.5;
        private final MinionEntity minion;
        @Nullable
        private UUID carcass;
        @Nullable
        private String bone;
        /** A Butcher's Table with a piece on it to chop, when that is its work rather than a carcass. */
        @Nullable
        private BlockPos table;
        private boolean done;
        private int nextStroke;
        private final List<UUID> unreachable = new ArrayList<>();
        private final MinionGoals.Unreachable tablesOutOfReach = new MinionGoals.Unreachable();
        private int forgotAt;
        private final MinionGoals.Approach approach = new MinionGoals.Approach();

        Butcher(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.BUTCHER) || !blade(minion.getMainHandItem()) || minion.getRandom().nextInt(20) != 0 || !(minion.level() instanceof ServerLevel level)
                    || !minion.canCarry(new ItemStack(Items.BEEF))) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                unreachable.clear();
                forgotAt = minion.tickCount;
            }
            return pick(level);
        }

        /**
         * The nearest work by home for the blade it holds: a carcass and the bone to work on, or, with a Cleaver, a Butcher's
         * Table with a piece on it.
         */
        private boolean pick(ServerLevel level) {
            boolean skinning = minion.getMainHandItem().getItem() instanceof FlensingKnifeItem;
            Vec3 home = Vec3.atBottomCenterOf(minion.home());
            double best = Double.MAX_VALUE;
            carcass = null;
            table = null;
            if (!skinning) {
                for (BlockPos at : tables(level)) {
                    double d = home.distanceToSqr(Vec3.atCenterOf(at));
                    if (d < best) {
                        best = d;
                        table = at;
                    }
                }
            }
            for (CarcassSavedData.Carcass c : CarcassSavedData.get(level).all()) {
                if (unreachable.contains(c.id) || CarcassDrag.isDraggingCarcass(c.id)
                        || com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity.isHanging(level, c.id)) {
                    continue;
                }
                String work = skinning ? skinnable(c) : cuttable(c);
                Vector3d at = work == null ? null : CarcassAssembler.boneWorldPosition(level, c, work);
                if (at == null || !level.isLoaded(BlockPos.containing(at.x, at.y, at.z))) {
                    continue;
                }
                double d = home.distanceToSqr(at.x, at.y, at.z);
                double reach = minion.reach();
                if (d < reach * reach && d < best) {
                    best = d;
                    carcass = c.id;
                    bone = work;
                    table = null;
                }
            }
            return carcass != null || table != null;
        }

        /**
         * Whether it has a free slot for each kind of thing the piece on this table comes apart into (or, carrying nothing,
         * however few slots it has): else it takes what it carries to the container by home first.
         */
        private boolean roomToChop(ButcherTableBlockEntity t) {
            int free = 0;
            for (int i = 0; i < minion.slots(); i++) {
                if (minion.inventory.getItem(i).isEmpty()) {
                    free++;
                }
            }
            return free >= Math.min(t.yieldKinds(), minion.slots());
        }

        /** Butcher's Tables within its reach of home with a piece on them to chop, from the loaded chunks' block entities. */
        private List<BlockPos> tables(ServerLevel level) {
            List<BlockPos> out = new ArrayList<>();
            BlockPos home = minion.home();
            double reach = minion.reach();
            int r = Mth.ceil(reach);
            for (int cx = (home.getX() - r) >> 4; cx <= (home.getX() + r) >> 4; cx++) {
                for (int cz = (home.getZ() - r) >> 4; cz <= (home.getZ() + r) >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be instanceof ButcherTableBlockEntity t && be.getBlockPos().distToCenterSqr(Vec3.atBottomCenterOf(home)) < reach * reach
                                && t.canChop() && roomToChop(t) && !tablesOutOfReach.contains(minion, be.getBlockPos())) {
                            out.add(be.getBlockPos());
                        }
                    }
                }
            }
            return out;
        }

        /** Ticks to its next stroke: 0.75 s at 100%, sooner for a fitter butcher, never under 0.3 s. */
        private int stroke() {
            return MinionFitness.strokeTicks(PartsData.of(minion.level()).task(MinionTask.BUTCHER), minion.taskFitness());
        }

        /** A Flensing Knife's work: the torso of a carcass not yet skinned that has a hide to give. */
        @Nullable
        private static String skinnable(CarcassSavedData.Carcass c) {
            boolean hide = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(c.entity).map(t -> !t.hide().isEmpty()).orElse(false);
            return !c.skinned && hide ? c.rootBone : null;
        }

        /**
         * A Cleaver's work: a loose piece (nothing jointed to it) to break down, else a limb to cut off, the end of a
         * chain first (a head before its neck); the torso cannot be cut through while limbs hang off it. A resting
         * carcass's limbs are in its rest poses; the first cut unfolds it.
         */
        @Nullable
        private static String cuttable(CarcassSavedData.Carcass c) {
            if (!CarcassButchery.isAttached(c, c.rootBone)) {
                return c.bones.containsKey(c.rootBone) ? c.rootBone : null;
            }
            String any = null;
            for (var joint : c.joints) {
                String child = joint.child();
                if (child.equals(c.rootBone) || c.severed.contains(child) || !c.bones.containsKey(child) && !c.restPoses.containsKey(child)) {
                    continue;
                }
                if (c.joints.stream().noneMatch(j -> j.parent().equals(child))) {
                    return child;
                }
                any = any == null ? child : any;
            }
            return any;
        }

        @Override
        public boolean canContinueToUse() {
            return (carcass != null || table != null) && !done && minion.hasTask(MinionTask.BUTCHER) && blade(minion.getMainHandItem())
                    && minion.canCarry(new ItemStack(Items.BEEF));
        }

        @Override
        public void start() {
            minion.working = true;
            done = false;
            nextStroke = minion.tickCount + stroke();
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            carcass = null;
            bone = null;
            table = null;
        }

        @Override
        public void tick() {
            if (table != null && !done && minion.level() instanceof ServerLevel level) {
                chopAtTable(level);
                return;
            }
            if (carcass == null || bone == null || done || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            CarcassSavedData.Carcass c = CarcassSavedData.get(level).carcass(carcass);
            Vector3d at = c == null ? null : CarcassAssembler.boneWorldPosition(level, c, bone);
            if (c == null || at == null || !c.bones.containsKey(bone) && !(c.resting && c.restPoses.containsKey(bone))) {
                // the piece is gone: broken down, or cut off into a record of its own (the next pick finds it)
                done = true;
                return;
            }
            if (CarcassDrag.isDraggingCarcass(carcass)) {
                // someone is dragging it away (a player's Meat Hook, a hauler): it lets it go
                done = true;
                return;
            }
            Vec3 point = new Vec3(at.x, at.y, at.z);
            minion.getLookControl().setLookAt(point);
            // within its arm's length across, and a little over or under its feet: a resting body's torso lies a block up,
            // and a butcher standing against it where its path ends is as near as it gets (measured from its feet, it stood
            // there out of reach until it gave the body up)
            double reach = 2.0 + minion.getBbWidth() / 2.0;
            if (Math.hypot(minion.getX() - point.x, minion.getZ() - point.z) > reach || Math.abs(point.y - minion.getY()) > REACH_UP) {
                if (!approach.step(minion, BlockPos.containing(point), 2, 1.0)) {
                    unreachable.add(carcass);
                    carcass = null;
                }
                return;
            }
            approach.reset(minion);
            minion.getNavigation().stop();
            if (minion.tickCount < nextStroke) {
                return;
            }
            nextStroke = minion.tickCount + stroke();
            ItemStack blade = minion.getMainHandItem();
            boolean skinning = blade.getItem() instanceof FlensingKnifeItem;
            boolean bloody = com.avicagan.bloodandbones.carcass.Blood.bloody(c);
            // what comes off goes into its hands, as a machine's yields go to the machine, less what a poor butcher wastes
            boolean did = CarcassButchery.yielding(MinionFitness.yieldShare(minion.taskFitness()), () -> CarcassButchery.capturing(stack -> keep(minion, stack),
                    () -> skinning ? CarcassButchery.skin(level, null, c, at) : CarcassButchery.cut(level, null, c, bone, at)));
            minion.swing(InteractionHand.MAIN_HAND);
            if (did && bloody) {
                com.avicagan.bloodandbones.carcass.Blood.bloody(blade, level);
            }
            if (!did || skinning && c.skinned) {
                done = true;
            }
        }

        /**
         * At a Butcher's Table: it stands at arm's length from the top and, at its stroke, chops the piece there with its
         * Cleaver as a Deployer does, keeping what comes off (less what a poor butcher wastes), the Cleaver coming away bloody.
         * It goes to a table only with a free slot for each kind of thing the piece comes apart into, so what it chops stays
         * in its hands; carrying nothing, it chops whatever its room, and what it has no room for falls on the table top.
         */
        private void chopAtTable(ServerLevel level) {
            if (!(level.getBlockEntity(table) instanceof ButcherTableBlockEntity at) || !at.canChop()) {
                // chopped or taken off meanwhile (by a player, a Deployer, a funnel)
                done = true;
                return;
            }
            Vec3 top = Vec3.atCenterOf(table).add(0.0, 0.5, 0.0);
            minion.getLookControl().setLookAt(top);
            double reach = 2.0 + minion.getBbWidth() / 2.0;
            if (Math.hypot(minion.getX() - top.x, minion.getZ() - top.z) > reach || Math.abs(top.y - minion.getY()) > REACH_UP) {
                if (!approach.step(minion, table, 1, 1.0)) {
                    tablesOutOfReach.add(table);
                    table = null;
                }
                return;
            }
            approach.reset(minion);
            minion.getNavigation().stop();
            if (minion.tickCount < nextStroke) {
                return;
            }
            nextStroke = minion.tickCount + stroke();
            ItemStack blade = minion.getMainHandItem();
            if (!(blade.getItem() instanceof com.avicagan.bloodandbones.item.CleaverItem)) {
                done = true;
                return;
            }
            // what it has no room for falls on the table top, as a Deployer's chop leaves it
            CarcassButchery.yielding(MinionFitness.yieldShare(minion.taskFitness()), () -> at.chop(level, blade, minion::carry));
            minion.swing(InteractionHand.MAIN_HAND);
            // one chop takes the whole piece apart
            done = true;
        }
    }

    // ---- hauler

    /**
     * A hauler drags whole carcasses lying within its reach of home (24) to the nearest free Shackle Hook or Bleeding Rack, with the
     * same drag a player's Meat Hook makes (CarcassDrag: the spring pull, the slowdown by the carcass's weight that its
     * drag strength eases, the drips and trail). At a hook it hangs the carcass by its torso as a player's Meat Hook
     * click does, from where a player could: the tip within reach above the body, nothing solid between (never through
     * a floor to the storey above); at a rack it pulls the body over the tray and lets it down there to bleed, steadying it
     * until it lies still. Someone else taking hold of the body (a player's Meat Hook, another hauler) makes it let go.
     */
    static class Haul extends Goal {
        private static final int GIVE_UP = 1200;
        /**
         * How near under the hook the body it drags must be for it to hang it: right under it, or, once the hauler stands
         * as near as it can, as near as a player hangs one from (the hook's joint takes it the rest of the way).
         */
        private static final double HANG_REACH = 2.5;
        private static final double HOOK_REACH = 5.0;
        /** How high over the body a hook's tip may be for it to be hung there, as from a player's reach beside it. */
        private static final double HOOK_HEIGHT = 4.0;
        /** How near over the middle of a rack the body must be to be let down on it (a tray catches a little wide). */
        private static final double ON_TRAY = 0.8;
        /** Ticks a body let down on a rack is given (and steadied) to settle before it is checked to lie in the tray. */
        private static final int SETTLE = 40;
        /**
         * Ticks more it waits, standing still and steadying it, for the body to come to rest (fold and be pinned where it
         * lies) before it judges where it lies: a body judged sooner could still slide off, or be shoved off by the hauler
         * itself walking home through it, and a body lying on a rack only bleeds into it once at rest.
         */
        private static final int REST_WAIT = CarcassRest.STILL_TICKS + 100;
        /** Ticks it may stand still with the body not over the tray before it takes another pass. */
        private static final int STUCK = 60;
        /** Passes over a rack before it gives the body up. */
        private static final int TRIES = 3;
        /** Share of its motion each piece of a body keeps from one tick to the next while the hauler steadies it on a rack. */
        private static final double STEADY = 0.5;
        /**
         * Ticks after taking hold of the body before standing still counts as having got there: the path it stood at the end
         * of is the last pass's, and letting the body down at once (as it used to) left it where it lay, off the tray.
         */
        private static final int WALK_OFF = 10;
        private final MinionEntity minion;
        @Nullable
        private UUID carcass;
        @Nullable
        private BlockPos to;
        private boolean dragging;
        /** The way it drags the body, fixed when it hooks it: from where it stood, through the hook or rack. */
        private Vec3 through = Vec3.ZERO;
        private boolean done;
        private int startedAt;
        /** When a body let down on a rack is checked, or -1 when it has not been let down. */
        private int settleUntil = -1;
        /** Since when it has stood still with the body short of the tray, or -1. */
        private int stuckSince = -1;
        private int tries;
        /** When it last took hold of the body. */
        private int hookedAt;
        /** The way round the body to where it takes hold for another pass (see {@link #wayRound}), still to walk. */
        private final List<Vec3> round = new ArrayList<>();
        /** Whether the way round the body is still to be worked out, for a pass about to begin. */
        private boolean plan;
        /**
         * Whether it has got to where it takes hold from for this pass: from there it only steps nearer the body, if the
         * body lies out of reach. Heading back to the spot each time a step took it off, it paced to and fro between the
         * two for as long as it had, never taking hold of a body lying more than a couple of blocks from the rack.
         */
        private boolean placed;
        /** When it set out for the next place on its way to take hold again. */
        private int wayFrom;
        /** How near the middle of the tray the body has come on this pass. */
        private double nearestToTray = Double.MAX_VALUE;
        private final List<UUID> unreachable = new ArrayList<>();
        private int forgotAt;
        private final MinionGoals.Approach approach = new MinionGoals.Approach();

        Haul(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasTask(MinionTask.HAULER) || minion.getRandom().nextInt(20) != 0 || !(minion.level() instanceof ServerLevel level)
                    || !MinionGoals.canPath(minion)) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                unreachable.clear();
                forgotAt = minion.tickCount;
            }
            return pick(level);
        }

        /** The nearest whole carcass by home that lies loose, and the nearest free hook or rack to it. */
        private boolean pick(ServerLevel level) {
            Vec3 home = Vec3.atBottomCenterOf(minion.home());
            double reach = minion.reach();
            List<BlockPos> ends = destinations(level);
            if (ends.isEmpty()) {
                minion.idle(Component.translatable("bloodandbones.minion.idle.hauler", minion.reach()));
                return false;
            }
            double best = Double.MAX_VALUE;
            carcass = null;
            to = null;
            for (CarcassSavedData.Carcass c : CarcassSavedData.get(level).all()) {
                // the cheap tests first: most of the world's carcasses lie nowhere near home
                Vector3d at = CarcassAssembler.boneWorldPosition(level, c, c.rootBone);
                double d = at == null ? Double.MAX_VALUE : home.distanceToSqr(at.x, at.y, at.z);
                if (d >= reach * reach || d >= best || unreachable.contains(c.id) || !whole(c)
                        || !level.isLoaded(BlockPos.containing(at.x, at.y, at.z)) || CarcassRest.isHeld(level, c) || onRack(level, c) != null) {
                    continue;
                }
                // the nearest free end on its own storey (a hook up through the floor above is none of its)
                BlockPos end = ends.stream().filter(p -> sameStorey(level, p, at.y))
                        .min(Comparator.comparingDouble(p -> p.distToCenterSqr(at.x, at.y, at.z))).orElse(null);
                if (end != null) {
                    best = d;
                    carcass = c.id;
                    to = end;
                }
            }
            return carcass != null;
        }

        /** Where a body goes on a hook or rack: the hook's tip, the middle of the rack's tray. */
        private static Vec3 over(ServerLevel level, BlockPos end) {
            BlockState state = level.getBlockState(end);
            return state.getBlock() instanceof ShackleHookBlock ? ShackleHookBlock.tip(end, state) : Vec3.atCenterOf(end);
        }

        /** Whether a body at this height is on the hook's or rack's storey: a hook's tip within reach over it, a tray about level with it. */
        private static boolean sameStorey(ServerLevel level, BlockPos end, double torsoY) {
            double up = over(level, end).y - torsoY;
            return level.getBlockState(end).getBlock() instanceof ShackleHookBlock ? up > -1.0 && up < HOOK_HEIGHT : up > -2.0 && up < 1.5;
        }

        /** Whether nothing solid stands between the body and the hook's tip (a floor, a wall), so a player there could hang it. */
        private static boolean clearTo(ServerLevel level, Vec3 torso, Vec3 tip, BlockPos hook) {
            // the world's own blocks, walked as vanilla's clip walks them (a clip of the level would stop at the carcass's own body)
            return BlockGetter.traverseBlocks(torso, tip, level, (world, pos) -> {
                if (pos.equals(hook)) {
                    return null;
                }
                return !world.isLoaded(pos) || world.getBlockState(pos).getCollisionShape(world, pos).clip(torso, tip, pos) != null ? Boolean.FALSE : null;
            }, world -> Boolean.TRUE);
        }

        /** A body with its torso, not a lone severed limb. */
        private static boolean whole(CarcassSavedData.Carcass c) {
            return RigManager.forCarcass(c).map(rig -> rig.root().name().equals(c.rootBone)).orElse(false);
        }

        /** Free Shackle Hooks and Bleeding Racks within reach of home, from the loaded chunks' block entities (none is loaded to look). */
        private List<BlockPos> destinations(ServerLevel level) {
            List<BlockPos> out = new ArrayList<>();
            BlockPos home = minion.home();
            Set<BlockPos> racksInUse = null;
            double reach = minion.reach();
            int r = Mth.ceil(reach);
            for (int cx = (home.getX() - r) >> 4; cx <= (home.getX() + r) >> 4; cx++) {
                for (int cz = (home.getZ() - r) >> 4; cz <= (home.getZ() + r) >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be.getBlockPos().distSqr(home) >= reach * reach) {
                            continue;
                        }
                        if (be instanceof BleedingRackBlockEntity && racksInUse == null) {
                            racksInUse = racksInUse(level);
                        }
                        if (be instanceof ShackleHookBlockEntity hook && !hook.isOccupied()
                                || be instanceof BleedingRackBlockEntity && !racksInUse.contains(be.getBlockPos())) {
                            out.add(be.getBlockPos());
                        }
                    }
                }
            }
            return out;
        }

        /** The racks with a body lying on them already, worked out once from the carcasses near home (one on a rack lies right by it). */
        private Set<BlockPos> racksInUse(ServerLevel level) {
            Set<BlockPos> out = new HashSet<>();
            Vec3 home = Vec3.atBottomCenterOf(minion.home());
            double near = minion.reach() + 4.0;
            for (CarcassSavedData.Carcass c : CarcassSavedData.get(level).all()) {
                Vector3d at = CarcassAssembler.boneWorldPosition(level, c, c.rootBone);
                BleedingRackBlockEntity rack = at == null || home.distanceToSqr(at.x, at.y, at.z) >= near * near ? null : onRack(level, c);
                if (rack != null) {
                    out.add(rack.getBlockPos());
                }
            }
            return out;
        }

        /** The rack this body would bleed into lying where it is (CarcassBleeding's own test, from its lowest point), or null. */
        @Nullable
        private static BleedingRackBlockEntity onRack(ServerLevel level, CarcassSavedData.Carcass c) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            UUID id = c.bones.get(c.rootBone);
            if (container == null || id == null || !(container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel torso) || torso.isRemoved()) {
                return null;
            }
            Vector3d drip = CarcassBleeding.lowestPoint(c, torso);
            return level.isLoaded(BlockPos.containing(drip.x, drip.y, drip.z)) ? CarcassBleeding.rackBelow(level, drip, CarcassBleeding.LYING_REACH) : null;
        }

        @Override
        public boolean canContinueToUse() {
            return carcass != null && to != null && !done && minion.hasTask(MinionTask.HAULER) && minion.tickCount - startedAt < GIVE_UP && minion.level().isLoaded(to);
        }

        @Override
        public void start() {
            minion.working = true;
            dragging = false;
            done = false;
            startedAt = minion.tickCount;
            settleUntil = -1;
            stuckSince = -1;
            tries = 0;
            plan = false;
            placed = false;
            round.clear();
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            if (minion.level() instanceof ServerLevel level && CarcassDrag.isDragging(minion)) {
                CarcassDrag.stop(level, minion);
            }
            if (carcass != null && !done) {
                // called away before it was done (to drink, say): the next haul may pick another
                unreachable.add(carcass);
            }
            carcass = null;
            to = null;
        }

        /** Lower a body it lets go of: every piece stops where it is, so it does not slide on at the pace it was towed. */
        private static void layDown(ServerLevel level, CarcassSavedData.Carcass c) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (!(container instanceof dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer server)) {
                return;
            }
            var pipeline = server.physicsSystem().getPipeline();
            for (UUID id : c.bones.values()) {
                if (server.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel bone && !bone.isRemoved()) {
                    pipeline.resetVelocity(bone);
                }
            }
        }

        /**
         * Steady a body let down on a rack until it lies still: every piece keeps only part of its motion from one tick to
         * the next. Left to itself, a leg hanging off the tray swung on for half a minute, and a body rests (and so bleeds
         * into the tray) only once all of it is still.
         */
        private static void steady(ServerLevel level, CarcassSavedData.Carcass c) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            if (!(container instanceof dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer server)) {
                return;
            }
            var pipeline = server.physicsSystem().getPipeline();
            Vector3d linear = new Vector3d();
            Vector3d angular = new Vector3d();
            for (UUID id : c.bones.values()) {
                if (server.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel bone && !bone.isRemoved()) {
                    pipeline.getLinearVelocity(bone, linear).mul(STEADY - 1.0);
                    pipeline.getAngularVelocity(bone, angular).mul(STEADY - 1.0);
                    pipeline.addLinearAndAngularVelocity(bone, linear, angular);
                }
            }
        }

        /**
         * Where it takes hold of a body for another pass over a rack: past the rack on the line from the body through the
         * middle of the tray, so it tows the body on over the tray and away from the rack (stepping nearer first if the
         * body is out of reach from there); null when there is no room to stand there. Taking hold again wherever the last
         * pass left it, it walked back into its own body lying between it and the rack and stood there pushing it against
         * the rack; or, the body lying off to one side, towed it past the tray again.
         */
        @Nullable
        private Vec3 holdSpot(ServerLevel level, Vec3 torso, Vec3 tray) {
            Vec3 across = new Vec3(tray.x - torso.x, 0.0, tray.z - torso.z);
            double off = across.length();
            if (off < 1.0e-3) {
                return null;
            }
            // clear of the rack as a path finder sees a mob's width (whole blocks, one way from where it stands), or it
            // sees no way to stand there at all and heads for the nearest place it can, maybe the other side of the body
            double clear = Math.max(Mth.floor(minion.getBbWidth() + 1.0F), 0.5 + minion.getBbWidth() / 2.0) + 0.1;
            Vec3 spot = tray.add(across.scale(clear / off));
            return roomAt(level, spot) ? spot : null;
        }

        /**
         * The way round the body to where it takes hold, when the straight way there passes the body (it lies between: a
         * body towed past the tray lies between the hauler and the rack): a step aside, along beside it, and back in. A
         * path is worked out as if the body were not there, and walking into it the hauler stood stuck against it.
         */
        private List<Vec3> wayRound(ServerLevel level, CarcassSavedData.Carcass c, Vec3 torso, Vec3 spot) {
            Vec3 from = new Vec3(minion.getX(), 0.0, minion.getZ());
            Vec3 goal = new Vec3(spot.x, 0.0, spot.z);
            Vec3 body = new Vec3(torso.x, 0.0, torso.z);
            Vec3 way = goal.subtract(from);
            double length = way.length();
            if (length < 1.0e-3) {
                return List.of();
            }
            Vec3 dir = way.scale(1.0 / length);
            double along = body.subtract(from).dot(dir);
            double clear = bodyReach(level, c, torso) + minion.getBbWidth() / 2.0 + 0.3;
            if (along <= 0.0 || along >= length || from.add(dir.scale(along)).distanceTo(body) >= clear) {
                return List.of();
            }
            Vec3 side = new Vec3(-dir.z, 0.0, dir.x);
            if (from.subtract(body).dot(side) < 0.0) {
                // round by the side it stands on
                side = side.reverse();
            }
            for (Vec3 n : new Vec3[]{side, side.reverse()}) {
                Vec3 out = from.add(n.scale(Math.max(0.0, clear - from.subtract(body).dot(n))));
                Vec3 in = goal.add(n.scale(Math.max(0.0, clear - goal.subtract(body).dot(n))));
                if (roomAt(level, out) && roomAt(level, in)) {
                    return List.of(out, in);
                }
            }
            return List.of();
        }

        /** Whether it has room to stand here, at the height it stands at now. */
        private boolean roomAt(ServerLevel level, Vec3 at) {
            BlockPos feet = BlockPos.containing(at.x, minion.getY(), at.z);
            return level.isLoaded(feet) && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
        }

        /** How far a body reaches out from its torso, flat: the furthest corner of any of its pieces. */
        private static double bodyReach(ServerLevel level, CarcassSavedData.Carcass c, Vec3 torso) {
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            double reach = 0.5;
            for (UUID id : c.bones.values()) {
                if (container != null && container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel bone && !bone.isRemoved()) {
                    var box = bone.boundingBox();
                    reach = Math.max(reach, Math.hypot(Math.max(Math.abs(box.minX() - torso.x), Math.abs(box.maxX() - torso.x)),
                            Math.max(Math.abs(box.minZ() - torso.z), Math.abs(box.maxZ() - torso.z))));
                }
            }
            return reach;
        }

        /**
         * The body missed the tray: it takes hold again and walks another line over the rack (see {@link #holdSpot}), or,
         * after {@link #TRIES} passes, gives it up.
         */
        private void another() {
            stuckSince = -1;
            if (++tries >= TRIES) {
                giveUp();
                return;
            }
            dragging = false;
            plan = true;
            placed = false;
            wayFrom = minion.tickCount;
            approach.reset(minion);
        }

        /** It cannot get this carcass there: it lets go and leaves it for half a minute. */
        private void giveUp() {
            if (carcass != null) {
                unreachable.add(carcass);
            }
            carcass = null;
        }

        @Override
        public void tick() {
            if (carcass == null || to == null || done || !(minion.level() instanceof ServerLevel level)) {
                return;
            }
            if (!level.isLoaded(to)) {
                // the hook or rack is out of the loaded world now: it is never loaded to be looked at
                giveUp();
                return;
            }
            CarcassSavedData.Carcass c = CarcassSavedData.get(level).carcass(carcass);
            Vector3d at = c == null ? null : CarcassAssembler.boneWorldPosition(level, c, c.rootBone);
            if (c == null || at == null) {
                done = true;
                return;
            }
            Vec3 torso = new Vec3(at.x, at.y, at.z);
            if (settleUntil >= 0) {
                // let down on a rack: once it has settled it must lie in the tray (where the bleeding finds the rack), or it
                // slid off and is taken up again for another pass
                if (!c.resting && !CarcassRest.isHeld(level, c)) {
                    // it steadies the body as it settles, until it lies still
                    steady(level, c);
                }
                if (minion.tickCount < settleUntil) {
                    return;
                }
                if (!c.resting && minion.tickCount < settleUntil + REST_WAIT) {
                    minion.getNavigation().stop();
                    return;
                }
                settleUntil = -1;
                BleedingRackBlockEntity under = onRack(level, c);
                if (under != null && under.getBlockPos().equals(to)) {
                    done = true;
                } else {
                    another();
                }
                return;
            }
            if (!dragging) {
                // within arm's length of the body (it lies in the way of getting any nearer), as a player hooks one from
                double reach = 3.0 + minion.getBbWidth() / 2.0;
                minion.getLookControl().setLookAt(torso);
                boolean rack = level.getBlockEntity(to) instanceof BleedingRackBlockEntity;
                Vec3 end = level.getBlockEntity(to) instanceof ShackleHookBlockEntity hook ? ShackleHookBlock.tip(to, hook.getBlockState()) : Vec3.atCenterOf(to);
                Vec3 spot = rack && tries > 0 ? holdSpot(level, torso, end) : null;
                if (spot != null && !placed) {
                    // another pass: first to where it takes hold from, round the body if it lies in the way
                    if (plan) {
                        plan = false;
                        round.clear();
                        round.addAll(wayRound(level, c, torso, spot));
                        wayFrom = minion.tickCount;
                    }
                    Vec3 next = round.isEmpty() ? spot : round.get(0);
                    boolean there = Math.hypot(minion.getX() - next.x, minion.getZ() - next.z) < 0.7
                            || minion.getNavigation().isDone() && minion.tickCount - wayFrom > WALK_OFF;
                    if (!round.isEmpty() && there) {
                        round.remove(0);
                        wayFrom = minion.tickCount;
                        approach.reset(minion);
                        return;
                    }
                    if (!there) {
                        if (!approach.step(minion, BlockPos.containing(next.x, minion.getY(), next.z), 0, 1.0)) {
                            if (round.isEmpty()) {
                                // it cannot get round there: this pass came to nothing
                                another();
                            } else {
                                // no way round on that side: straight for where it takes hold
                                round.clear();
                                wayFrom = minion.tickCount;
                                approach.reset(minion);
                            }
                        }
                        return;
                    }
                    // there: from here on only nearer the body
                    placed = true;
                    approach.reset(minion);
                }
                if (Math.hypot(minion.getX() - torso.x, minion.getZ() - torso.z) > reach || Math.abs(minion.getY() - torso.y) > 3.0) {
                    if (!approach.step(minion, BlockPos.containing(torso), 2, 1.0)) {
                        giveUp();
                    }
                    return;
                }
                BlockPos cell = torsoCell(level, c);
                if (cell == null || CarcassRest.isHeld(level, c) || !CarcassDrag.start(level, minion, cell, null)) {
                    // someone took hold of it first (a player's Meat Hook, another hauler), or hung it up
                    giveUp();
                    return;
                }
                dragging = true;
                hookedAt = minion.tickCount;
                // it walks a straight line through the hook or rack, fixed now: towed behind it, the body comes onto that line
                // and so under or over it. The first line runs from where it took hold; another pass over a rack runs from the
                // body through the middle of the tray (see holdSpot)
                Vec3 line = spot != null ? new Vec3(end.x - torso.x, 0.0, end.z - torso.z) : new Vec3(end.x - minion.getX(), 0.0, end.z - minion.getZ());
                through = line.lengthSqr() < 1.0e-4 ? Vec3.ZERO : line.normalize();
                nearestToTray = Double.MAX_VALUE;
                approach.reset(minion);
                return;
            }
            if (!CarcassDrag.isDragging(minion) || CarcassDrag.isDraggedByAnother(carcass, minion)) {
                // it tore loose (caught on something, left too far behind), or someone else took hold of it too: it lets go
                giveUp();
                return;
            }
            Vec3 over;
            boolean hook = level.getBlockEntity(to) instanceof ShackleHookBlockEntity;
            if (hook) {
                ShackleHookBlockEntity shackle = (ShackleHookBlockEntity) level.getBlockEntity(to);
                over = ShackleHookBlock.tip(to, shackle.getBlockState());
                double trailing = Math.hypot(torso.x - over.x, torso.z - over.z);
                // as near under it as its width lets it stand (a path keeps a broad body further off)
                boolean under = minion.getNavigation().isDone() && Math.hypot(minion.getX() - over.x, minion.getZ() - over.z) < MinionGoals.accuracy(minion) + 1.0;
                // and as a player could hang it from there: within reach below the tip, nothing solid between
                boolean reachable = sameStorey(level, to, torso.y) && clearTo(level, torso, over, to);
                if (reachable && (trailing < HANG_REACH || under && trailing < HOOK_REACH)) {
                    // up it goes, by its torso, as a player's Meat Hook click hangs it
                    done = shackle.hang(level, minion);
                    if (!done) {
                        giveUp();
                    }
                    return;
                }
            } else if (level.getBlockEntity(to) instanceof BleedingRackBlockEntity) {
                Vec3 tray = Vec3.atCenterOf(to);
                BleedingRackBlockEntity under = onRack(level, c);
                boolean stood = minion.getNavigation().isDone() && minion.tickCount - hookedAt > WALK_OFF;
                // over the tray once it has reached the middle along the line it is towed, or as near it as this pass brings it
                // (it is drawing away again): let down any sooner, the body drops back the way it came as it settles (up to a
                // block), and off the tray
                double fromMiddle = Math.hypot(torso.x - tray.x, torso.z - tray.z);
                boolean nearest = fromMiddle > nearestToTray;
                nearestToTray = Math.min(nearestToTray, fromMiddle);
                boolean overTray = fromMiddle < ON_TRAY && torso.y > tray.y - 0.5 && torso.y < tray.y + 2.0
                        && (through.lengthSqr() < 1.0e-4 || nearest || (torso.x - tray.x) * through.x + (torso.z - tray.z) * through.z >= 0.0);
                if (under != null && under.getBlockPos().equals(to) && (overTray || stood)) {
                    // over the tray, where the bleeding finds this rack under the body: it lets it down there, gently (not
                    // flung on at a walk), and waits
                    CarcassDrag.stop(level, minion);
                    minion.getNavigation().stop();
                    layDown(level, c);
                    settleUntil = minion.tickCount + SETTLE;
                    stuckSince = -1;
                    return;
                }
                if (!stood) {
                    stuckSince = -1;
                } else if (stuckSince < 0) {
                    stuckSince = minion.tickCount;
                } else if (minion.tickCount - stuckSince > STUCK) {
                    // stood where its path ends with the body trailing short of the tray: it lets go and takes another pass
                    CarcassDrag.stop(level, minion);
                    another();
                    return;
                }
                over = Vec3.atCenterOf(to).add(0.0, 0.5, 0.0);
            } else {
                // the hook or rack is gone
                giveUp();
                return;
            }
            // it walks on past the hook or rack, so the body trailing behind it comes under or over it (even if its path
            // ends a little short)
            Vec3 on = through.scale(1.6 + minion.getBbWidth() / 2.0 + MinionGoals.accuracy(minion));
            BlockPos stand = BlockPos.containing(over.x + on.x, minion.getY(), over.z + on.z);
            if (!level.isLoaded(stand) || !level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()) {
                // no room past it (a wall): as near under it as it can get
                stand = BlockPos.containing(over.x, minion.getY(), over.z);
            }
            minion.getLookControl().setLookAt(over);
            if (!approach.step(minion, stand, MinionGoals.accuracy(minion), 0.9)) {
                if (hook) {
                    giveUp();
                } else {
                    // it has got nowhere with the body in tow (caught on the rack's rim, say): a pass gone wrong, not a body
                    // it cannot reach, so it lets go and takes another pass rather than leaving it for half a minute
                    CarcassDrag.stop(level, minion);
                    another();
                }
            }
        }
    }

    /** A cell of a carcass's torso to hook, in its body's plot (as a player's hook takes one), or null if it has none. */
    @Nullable
    public static BlockPos torsoCell(ServerLevel level, CarcassSavedData.Carcass carcass) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        UUID id = carcass.bones.get(carcass.rootBone);
        if (container == null || id == null || !(container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body) || body.isRemoved()) {
            return null;
        }
        return body.getPlot().getCenterBlock();
    }
}
