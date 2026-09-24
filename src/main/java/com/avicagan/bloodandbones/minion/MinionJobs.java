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
import com.avicagan.bloodandbones.item.CleaverItem;
import com.avicagan.bloodandbones.item.FlensingKnifeItem;
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
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The jobs heads offer beyond the first few (docs/PARTS-AND-TRAITS.md section 6.9), each a package of goals: the
 * sentry, scavenger, herder, fisher, hunter, hauler, butcher, medic, barterer and digger. A head's data offers a job;
 * the job stands only while its needs are met: a sentry needs a ranged attack in hand, a fisher a rod (or a fish's
 * mouth), a butcher a Cleaver or Flensing Knife. What it holds its maker hands it: one of whatever they use on it
 * (the old one comes back), and an empty hand takes it back; arrows for its bow and a medic's healing potions go in
 * with what it carries. What a scavenger fetches and what a herder leads by is whatever it holds. A brass minion's
 * filter ({@link MinionFilter}) narrows what the scavenger, herder, hunter and sentry take.
 * <p>
 * None of them breaks or places a block. The sapper waits for the detonate effect of the Motion group. The two game
 * events here are the sentry's: its arrows pass through its own side, and a crossbow's can be picked up.
 */
public final class MinionJobs {
    public static final ResourceLocation SENTRY = BloodAndBones.asResource("sentry");
    public static final ResourceLocation SCAVENGER = BloodAndBones.asResource("scavenger");
    public static final ResourceLocation HERDER = BloodAndBones.asResource("herder");
    public static final ResourceLocation FISHER = BloodAndBones.asResource("fisher");
    public static final ResourceLocation HUNTER = BloodAndBones.asResource("hunter");
    public static final ResourceLocation HAULER = BloodAndBones.asResource("hauler");
    public static final ResourceLocation BUTCHER = BloodAndBones.asResource("butcher");
    public static final ResourceLocation MEDIC = BloodAndBones.asResource("medic");
    public static final ResourceLocation BARTERER = BloodAndBones.asResource("barterer");
    public static final ResourceLocation DIGGER = BloodAndBones.asResource("digger");

    /** Jobs worked from home: idle, it goes back there (a sentry to its post). */
    public static final List<ResourceLocation> HOMEBODIES = List.of(BloodAndBones.asResource("farmer"), BloodAndBones.asResource("courier"),
            BloodAndBones.asResource("guard"), SENTRY, SCAVENGER, HERDER, FISHER, HUNTER, HAULER, BUTCHER, MEDIC, BARTERER, DIGGER);
    /** Jobs whose takings go into the nearest container by home. */
    public static final List<ResourceLocation> STORERS = List.of(BloodAndBones.asResource("courier"), BloodAndBones.asResource("farmer"),
            FISHER, BUTCHER, DIGGER, BARTERER);

    /** How far a sentry shoots, and looks for something to shoot. */
    public static final double SENTRY_RANGE = 16.0;
    /** How far a scavenger looks for what it fetches. */
    public static final double FETCH_RANGE = 32.0;
    /** A herder keeps its animals this near home, and looks this far out for strays. */
    public static final double HERD_HOME = 8.0;
    public static final double HERD_SEARCH = 20.0;
    /** A hunter hunts this near home. */
    public static final double HUNT_RANGE = 12.0;
    /** A hauler fetches carcasses this far from home, to a hook or rack this far from the carcass. */
    public static final double HAUL_RANGE = 24.0;
    /** A butcher works on carcasses this near home. */
    public static final double BUTCHER_RANGE = 6.0;
    /** A medic looks this far for the hurt, and throws from this near. */
    public static final double MEDIC_RANGE = 16.0;
    public static final double THROW_RANGE = 8.0;
    /** Water a fisher fishes, ground a digger sniffs and a container a barterer takes gold from, this near home. */
    public static final int FISH_RANGE = 8;
    public static final int DIG_RANGE = 6;
    public static final int BARTER_RANGE = 6;
    /** Ticks at the water between catches (30 to 60 seconds, less with Lure) and at the ground between finds. */
    public static final int CATCH_MIN = 600;
    public static final int CATCH_MAX = 1200;
    public static final int DIG_MIN = 1200;
    public static final int DIG_MAX = 2400;
    /** A piglin looks the gold over this long before it trades. */
    public static final int ADMIRE = 120;
    /** The work a fisher or digger still has to do before its next catch or find, kept with the minion. */
    private static final String WORK_LEFT = BloodAndBones.MOD_ID + ":work_left";

    private MinionJobs() {
    }

    /** Every job's goals, given to every minion; each works only while the minion has its job. */
    static void goals(MinionEntity minion, GoalSelector goals, GoalSelector targets) {
        goals.addGoal(2, new Sentry(minion));
        goals.addGoal(3, new Fish(minion));
        goals.addGoal(3, new Dig(minion));
        goals.addGoal(3, new Barter(minion));
        goals.addGoal(3, new Butcher(minion));
        goals.addGoal(3, new Haul(minion));
        goals.addGoal(3, new Medic(minion));
        goals.addGoal(3, new Herd(minion));
        goals.addGoal(5, new Scavenge(minion));
        // a sentry takes as its target a monster it can see within its reach; a hunter the prey near home
        targets.addGoal(3, new NearestAttackableTargetGoal<>(minion, Mob.class, 10, true, false,
                target -> target instanceof Enemy && !(target instanceof MinionEntity) && minion.filter().allows(minion.level(), target)) {
            @Override
            public boolean canUse() {
                return minion.hasJob("sentry") && !minion.stats().mindless() && minion.hasRangedAttack() && super.canUse();
            }

            @Override
            protected double getFollowDistance() {
                // asked first while it is being made, before it has a build
                return minion.build().isEmpty() ? SENTRY_RANGE : Math.min(SENTRY_RANGE, minion.stats().sight());
            }
        });
        targets.addGoal(3, new Hunt(minion));
    }

    // ---- what it can do now

    /**
     * The jobs its head offers that it can do now, in the head's order (docs/PARTS-AND-TRAITS.md section 6.4); with
     * none, it keeps its maker company.
     */
    public static List<ResourceLocation> offered(MinionEntity minion) {
        MinionBuild build = minion.build().orElse(null);
        return build == null ? List.of(MinionStats.COMPANION)
                : offered(PartsData.of(minion.level()), build, minion.stats(), minion.getMainHandItem(), minion.hasRangedAttack());
    }

    /** The same for a build not yet woken (the table's line on it), holding whatever it would hold. */
    public static List<ResourceLocation> offered(PartsData.Store store, MinionBuild build, MinionStats stats, ItemStack held, boolean ranged) {
        List<ResourceLocation> out = new ArrayList<>();
        for (ResourceLocation job : stats.jobs()) {
            if (needsMet(store, build, stats, held, ranged, job)) {
                out.add(job);
            }
        }
        if (out.isEmpty()) {
            out.add(MinionStats.COMPANION);
        }
        return out;
    }

    /** A job's needs beyond the build: a ranged attack for a sentry, a rod or a fish's mouth for a fisher, a blade for a butcher. */
    static boolean needsMet(PartsData.Store store, MinionBuild build, MinionStats stats, ItemStack held, boolean ranged, ResourceLocation job) {
        if (job.equals(SENTRY)) {
            return ranged;
        }
        if (job.equals(FISHER)) {
            return !stats.strikes().isEmpty() && held.getItem() instanceof FishingRodItem || ownTool(store, build, FISHER);
        }
        if (job.equals(BUTCHER)) {
            return held.getItem() instanceof CleaverItem || held.getItem() instanceof FlensingKnifeItem;
        }
        return true;
    }

    /** Whether its head does this job with nothing in hand (its data's "no_tool": a fish fishes with its mouth). */
    static boolean ownTool(PartsData.Store store, MinionBuild build, ResourceLocation job) {
        PieceRef head = MinionStats.head(store, build);
        return head != null && MinionData.ids(store.resolve(head.entity(), head.baby()), head.traits(), "head", "no_tool").contains(job);
    }

    /**
     * The job it wakes to, of those it is offered with nothing in hand: the first but hunting, which would go straight
     * for the animals kept round the table it was made at (unless it is offered nothing else). Its maker puts it to
     * hunting with a click.
     */
    public static ResourceLocation wakeJob(List<ResourceLocation> offered) {
        return offered.stream().filter(job -> !job.equals(HUNTER)).findFirst().orElse(offered.get(0));
    }

    /** A job it has that it can no longer do (its bow broke, its rod was taken) gives way to the first it can. */
    static void keepValid(MinionEntity minion) {
        List<ResourceLocation> jobs = offered(minion);
        if (!jobs.contains(minion.job())) {
            startJob(minion, jobs.get(0));
        }
    }

    /** Put it to a job it can do: a fresh start, a sentry taking its post where it stands. */
    static void startJob(MinionEntity minion, ResourceLocation job) {
        if (minion.setJob(job)) {
            minion.setTarget(null);
            minion.getNavigation().stop();
            if (job.equals(SENTRY)) {
                minion.setHome(minion.blockPosition());
            }
        }
    }

    // ---- what it holds

    /**
     * Its maker's hand on it, awake and standing: an item goes into its hand (one of it; what it held comes back),
     * an empty hand takes back what it holds. Arrows for the bow or crossbow it holds, and healing for a medic's head,
     * go in with what it carries instead, the whole stack, its hand kept. Null when this is not that (crouching changes
     * its job instead; saddled, an empty hand climbs on).
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
            if (holding.isEmpty() || hand != InteractionHand.MAIN_HAND || minion.isSaddled() && !minion.isVehicle()) {
                return null;
            }
            minion.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            player.getInventory().placeItemBackInInventory(holding);
            minion.level().playSound(null, minion.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.6F, 0.8F);
            keepValid(minion);
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
        keepValid(minion);
        return InteractionResult.CONSUME;
    }

    /** What goes in with what it carries rather than into its hand: ammunition for the weapon it holds, healing for a medic. */
    private static boolean stocks(MinionEntity minion, ItemStack stack) {
        ItemStack held = minion.getMainHandItem();
        return held.getItem() instanceof ProjectileWeaponItem weapon && weapon.getAllSupportedProjectiles(held).test(stack)
                || minion.stats().jobs().contains(MEDIC) && heals(stack);
    }

    /** What it takes into its hand: anything but what already does something to a mob (a saddle, a lead, a name tag, an egg). */
    private static boolean takes(ItemStack stack) {
        return !stack.is(Items.SADDLE) && !stack.is(Items.LEAD) && !stack.is(Items.NAME_TAG) && !(stack.getItem() instanceof SpawnEggItem)
                && !(stack.getItem() instanceof DormantMinionItem);
    }

    /** Killed (where minions may die): what it held drops with what it carried. */
    static void dropHeld(MinionEntity minion) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
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

    /** Whether one of the slots it can use is empty: room for whatever a job turns up. */
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

    /** A tick of work toward a catch or find: true when it comes, and the next wait starts. */
    private static boolean worked(MinionEntity minion, int min, int max, int less) {
        CompoundTag data = minion.getPersistentData();
        int left = data.contains(WORK_LEFT) ? data.getInt(WORK_LEFT) : nextWait(minion, min, max, less);
        if (left <= 0) {
            data.putInt(WORK_LEFT, nextWait(minion, min, max, less));
            return true;
        }
        data.putInt(WORK_LEFT, left - 1);
        return false;
    }

    /** Ticks to the next catch or find: somewhere between the two, less what Lure takes off, never under a sixth of the least. */
    private static int nextWait(MinionEntity minion, int min, int max, int less) {
        return Math.max(min / 6, min + minion.getRandom().nextInt(max - min + 1) - less);
    }

    // ---- sentry

    /**
     * A sentry never moves from its post: it turns to what it has for a target and shoots it with the bow, crossbow or
     * trident in its hand, in the way of vanilla's ranged goals (RangedBowAttackGoal, RangedCrossbowAttackGoal, the
     * drowned's throw) but standing still. Arrows come from what it carries; a trident is thrown as the drowned throws
     * one, and wears the one in hand.
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
            return minion.hasJob("sentry") && target != null && target.isAlive() && minion.hasRangedAttack();
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
            boolean near = minion.distanceToSqr(target) <= SENTRY_RANGE * SENTRY_RANGE;
            cooldown--;
            if (weapon.getItem() instanceof BowItem) {
                if (minion.isUsingItem()) {
                    if (!sees && seeTime < -60) {
                        minion.stopUsingItem();
                    } else if (sees && minion.getTicksUsingItem() >= 20) {
                        int drawn = minion.getTicksUsingItem();
                        minion.stopUsingItem();
                        shootBow(level, minion, weapon, target, BowItem.getPowerForTime(drawn));
                        cooldown = 20;
                    }
                } else if (cooldown <= 0 && sees && near && !minion.getProjectile(weapon).isEmpty()) {
                    minion.startUsingItem(InteractionHand.MAIN_HAND);
                }
            } else if (weapon.getItem() instanceof CrossbowItem crossbow) {
                if (CrossbowItem.isCharged(weapon)) {
                    if (cooldown <= 0 && sees && near) {
                        loosing = minion;
                        try {
                            crossbow.performShooting(level, minion, InteractionHand.MAIN_HAND, weapon, 1.6F, 14 - level.getDifficulty().getId() * 4, target);
                        } finally {
                            loosing = null;
                        }
                        cooldown = 20 + minion.getRandom().nextInt(20);
                    }
                } else if (minion.isUsingItem()) {
                    if (minion.getTicksUsingItem() >= CrossbowItem.getChargeDuration(weapon, minion)) {
                        // loaded from what it carries (MinionEntity#getProjectile)
                        minion.releaseUsingItem();
                        cooldown = 20;
                    }
                } else if (sees && near && !minion.getProjectile(weapon).isEmpty()) {
                    minion.startUsingItem(InteractionHand.MAIN_HAND);
                }
            } else if (cooldown <= 0 && sees && near) {
                throwTrident(level, minion, weapon, target);
                cooldown = 40;
            }
        }
    }

    /** A bow shot as a skeleton looses one, the arrow taken from what it carries (one that lands can be picked up). */
    static void shootBow(ServerLevel level, MinionEntity minion, ItemStack bow, LivingEntity target, float power) {
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
        arrow.shoot(dx, dy + flat * 0.2F, dz, 1.6F, 14 - level.getDifficulty().getId() * 4);
        arrow.pickup = free ? AbstractArrow.Pickup.CREATIVE_ONLY : AbstractArrow.Pickup.ALLOWED;
        minion.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (minion.getRandom().nextFloat() * 0.4F + 0.8F));
        level.addFreshEntity(arrow);
        bow.hurtAndBreak(1, minion, EquipmentSlot.MAINHAND);
    }

    /** A trident thrown as the drowned throws one (a copy that cannot be picked up, loyal to nobody); the one in hand wears. */
    static void throwTrident(ServerLevel level, MinionEntity minion, ItemStack trident, LivingEntity target) {
        ItemStack copy = trident.copyWithCount(1);
        EnchantmentHelper.updateEnchantments(copy, enchantments -> enchantments.removeIf(e -> e.is(Enchantments.LOYALTY) || e.is(Enchantments.RIPTIDE)));
        ThrownTrident thrown = new ThrownTrident(level, minion, copy);
        double dx = target.getX() - minion.getX();
        double dy = target.getY(1.0 / 3.0) - thrown.getY();
        double dz = target.getZ() - minion.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        thrown.shoot(dx, dy + flat * 0.2F, dz, 1.6F, 14 - level.getDifficulty().getId() * 4);
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

    // ---- scavenger

    /**
     * A scavenger fetches, as an allay does, items like the one in its hand from as far as 32 blocks (as far as its head
     * sees), and brings them to its maker while its maker is about home, as far out as it fetches from (an allay goes to
     * its player only within 64 of it). With its maker away it keeps what it found until they are back. Brass with a
     * filter fetches only what the filter passes too, and with its hand empty, whatever the filter passes.
     */
    static class Scavenge extends Goal {
        private final MinionEntity minion;
        @Nullable
        private ItemEntity item;
        private boolean bringing;
        private final MinionGoals.Approach approach = new MinionGoals.Approach();
        /** Items it could not get to, by entity id, forgotten every half minute. */
        private final List<Integer> unreachable = new ArrayList<>();
        private int forgotAt;

        Scavenge(MinionEntity minion) {
            this.minion = minion;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            if (!minion.hasJob("scavenger") || minion.getMainHandItem().isEmpty() && minion.filter().isEmpty() || minion.getRandom().nextInt(10) != 0) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                unreachable.clear();
                forgotAt = minion.tickCount;
            }
            item = find();
            bringing = item == null && carrying() && maker() != null;
            return item != null || bringing;
        }

        @Override
        public boolean canContinueToUse() {
            return minion.hasJob("scavenger") && (item != null && item.isAlive() || bringing && maker() != null);
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

        /** The nearest item it fetches that it has room for. */
        @Nullable
        private ItemEntity find() {
            double range = Math.min(FETCH_RANGE, minion.stats().sight());
            List<ItemEntity> near = minion.level().getEntitiesOfClass(ItemEntity.class, minion.getBoundingBox().inflate(range),
                    e -> e.isAlive() && !e.hasPickUpDelay() && !unreachable.contains(e.getId()) && fetches(e.getItem()) && minion.canCarry(e.getItem())
                            && e.distanceToSqr(minion) < range * range);
            return near.stream().min(Comparator.comparingDouble(minion::distanceToSqr)).orElse(null);
        }

        /** Like what it holds (anything, if it holds nothing), and passed by its filter. */
        private boolean fetches(ItemStack stack) {
            ItemStack held = minion.getMainHandItem();
            return !stack.isEmpty() && (held.isEmpty() ? !minion.filter().isEmpty() : alike(held, stack)) && minion.filter().allows(minion.level(), stack);
        }

        private boolean carrying() {
            for (int i = 0; i < minion.slots(); i++) {
                if (fetches(minion.inventory.getItem(i))) {
                    return true;
                }
            }
            return false;
        }

        /** Its maker, while about home: it never sets off after them further than it fetches from. */
        @Nullable
        private Player maker() {
            Player maker = minion.maker();
            return maker != null && maker.isAlive() && !maker.isSpectator() && maker.level() == minion.level()
                    && maker.distanceToSqr(Vec3.atBottomCenterOf(minion.home())) < FETCH_RANGE * FETCH_RANGE ? maker : null;
        }

        @Override
        public void tick() {
            if (item != null) {
                if (minion.distanceToSqr(item) < 2.5 + minion.getBbWidth()) {
                    keep(minion, item.getItem().copy());
                    minion.take(item, item.getItem().getCount());
                    item.discard();
                    minion.level().playSound(null, minion.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.NEUTRAL, 0.6F, 0.9F);
                    // the next one, or home to its maker with them
                    item = find();
                    bringing = item == null && maker() != null;
                    approach.reset(minion);
                } else if (!approach.step(minion, item.blockPosition(), 1, 1.1)) {
                    unreachable.add(item.getId());
                    item = null;
                }
                return;
            }
            Player maker = maker();
            if (!bringing || maker == null) {
                return;
            }
            minion.getLookControl().setLookAt(maker);
            if (minion.distanceToSqr(maker) < Math.pow(2.5 + minion.getBbWidth(), 2)) {
                // into its maker's hands
                for (int i = 0; i < minion.inventory.getContainerSize(); i++) {
                    ItemStack stack = minion.inventory.getItem(i);
                    if (fetches(stack)) {
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
     * A herder keeps the animals that would follow what it holds (wheat for cattle and sheep, seeds for chickens) within
     * 8 of home: it walks out to a stray and leads it back, the stray walking after it, as an animal follows a player
     * with its food. Holding nothing, it herds nothing. Never someone's own animal (tamed or named), whom its owner has
     * put where it is. Brass with a filter herds only the animals it passes.
     */
    static class Herd extends Goal {
        private static final int GIVE_UP = 600;
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
            if (!minion.hasJob("herder") || held.isEmpty() || minion.getRandom().nextInt(20) != 0) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                lost.clear();
                forgotAt = minion.tickCount;
            }
            Vec3 home = Vec3.atBottomCenterOf(minion.home());
            double range = Math.min(HERD_SEARCH, minion.stats().sight());
            stray = minion.level().getEntitiesOfClass(Animal.class, new AABB(minion.home()).inflate(HERD_SEARCH),
                            a -> a.isAlive() && a.isFood(held) && minion.filter().allows(minion.level(), a) && !a.isLeashed() && !a.isVehicle()
                                    && !a.isPassenger() && !owned(a) && !lost.contains(a.getId())
                                    && a.distanceToSqr(home) > HERD_HOME * HERD_HOME && a.distanceToSqr(minion) < range * range)
                    .stream().min(Comparator.comparingDouble(a -> a.distanceToSqr(home))).orElse(null);
            return stray != null;
        }

        @Override
        public boolean canContinueToUse() {
            return stray != null && stray.isAlive() && minion.hasJob("herder") && minion.tickCount - startedAt < GIVE_UP
                    && stray.distanceToSqr(Vec3.atBottomCenterOf(minion.home())) > (HERD_HOME - 2.0) * (HERD_HOME - 2.0);
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
            if (stray != null && minion.tickCount - startedAt >= GIVE_UP) {
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

    // ---- fisher

    /**
     * A fisher by open water near home, a rod in hand (or a fish's mouth), rolls the fishing loot table every 30 to 60
     * seconds it spends there (less with Lure; no treasure, which needs a bobber in open water), into what it carries.
     * A rod wears a point a catch, as a player's does.
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
            if (!minion.hasJob("fisher") || minion.getRandom().nextInt(20) != 0 || !minion.canCarry(new ItemStack(Items.COD))) {
                return false;
            }
            water = find();
            return water != null;
        }

        @Nullable
        private BlockPos find() {
            BlockPos home = minion.home();
            BlockPos from = home.offset(-FISH_RANGE, -2, -FISH_RANGE);
            BlockPos to = home.offset(FISH_RANGE, 2, FISH_RANGE);
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
            return water != null && !caught && minion.hasJob("fisher") && minion.level().isLoaded(water) && fishable(water);
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
            if (worked(minion, CATCH_MIN, CATCH_MAX, lure)) {
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
     * A digger sniffs the grass, moss and dirt round home (vanilla's sniffer_diggable_block tag), and every minute or
     * two it spends at it turns up what a sniffer digs (the sniffer_digging loot table), into what it carries. It
     * leaves the ground as it was.
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
            if (!minion.hasJob("digger") || minion.getRandom().nextInt(20) != 0 || !minion.canCarry(new ItemStack(Items.TORCHFLOWER_SEEDS))) {
                return false;
            }
            spot = find();
            return spot != null;
        }

        /** A diggable block with room over it, not one of the last few it dug, picked at random. */
        @Nullable
        private BlockPos find() {
            BlockPos home = minion.home();
            BlockPos from = home.offset(-DIG_RANGE, -2, -DIG_RANGE);
            BlockPos to = home.offset(DIG_RANGE, 2, DIG_RANGE);
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
            return spot != null && !found && minion.hasJob("digger") && minion.level().isLoaded(spot) && diggable(spot);
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
            if (worked(minion, DIG_MIN, DIG_MAX, 0)) {
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
     * A barterer takes a gold ingot from the container by home, looks it over as a piglin does (six seconds), and
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
            if (!minion.hasJob("barterer") || minion.getRandom().nextInt(20) != 0 || !freeSlot(minion)) {
                return false;
            }
            chest = find();
            return chest != null;
        }

        /** The nearest container by home with gold in it. */
        @Nullable
        private BlockPos find() {
            BlockPos home = minion.home();
            BlockPos from = home.offset(-BARTER_RANGE, -2, -BARTER_RANGE);
            BlockPos to = home.offset(BARTER_RANGE, 2, BARTER_RANGE);
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
            return chest != null && !traded && minion.hasJob("barterer") && minion.level().isLoaded(chest);
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
            if (++admired < ADMIRE) {
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
     * A hunter takes for its target grown prey near home that it can see (its head's data names the prey, "prey": ids
     * and #tags of any passive mob, a fish too, else #bloodandbones:hunter_prey), never a named, tamed, leashed or ridden
     * one; its bite or arms kill it. The rules hold for the whole chase: prey leashed or named meanwhile, or run off
     * from its hunting ground, is let be. It hunts only where mobs may do harm (the mobGriefing rule, spec section 10),
     * and is not woken to it (MinionJobs#wakeJob). With a Meat Hook in hand the kill leaves an intact carcass, exactly as
     * a player's Meat Hook kill does (CarcassEvents#onDeath reads the killer's hand). Brass with a filter hunts only the
     * prey it passes.
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
            return minion.hasJob("hunter") && !minion.stats().mindless() && minion.stats().fights() && EventHooks.canEntityGrief(minion.level(), minion);
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
            // asked once while it is being made, before it knows its minion
            return minion == null || minion.build().isEmpty() ? HUNT_RANGE * 2.0 : Math.min(HUNT_RANGE * 2.0, minion.stats().sight());
        }

        private boolean hunts(PathfinderMob mob) {
            if (mob instanceof Enemy || mob instanceof MinionEntity || mob.isBaby() || owned(mob) || mob.isLeashed() || mob.isVehicle() || mob.isPassenger()
                    || mob.distanceToSqr(Vec3.atBottomCenterOf(minion.home())) > HUNT_RANGE * HUNT_RANGE || !minion.filter().allows(minion.level(), mob)) {
                return false;
            }
            if (prey.isEmpty()) {
                return mob.getType().is(BBTags.HUNTER_PREY);
            }
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            for (String name : prey) {
                if (name.startsWith("#") ? mob.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(name.substring(1))))
                        : id.toString().equals(name)) {
                    return true;
                }
            }
            return false;
        }
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
     * A medic throws a splash potion of healing (or regeneration) from what it carries at an ally two hearts or more down:
     * its maker, its maker's other flesh minions, a villager. Brass it leaves to its brass sheets and cradle, which are
     * what mend brass (spec 6.6). It closes to throwing range, then throws as a witch does.
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
            if (!minion.hasJob("medic") || minion.tickCount < cooldown || minion.getRandom().nextInt(10) != 0 || potion(minion) < 0) {
                return false;
            }
            patient = patient();
            return patient != null;
        }

        /** The worst-hurt ally it can see. */
        @Nullable
        private LivingEntity patient() {
            double range = Math.min(MEDIC_RANGE, minion.stats().sight());
            List<LivingEntity> allies = new ArrayList<>();
            Player maker = minion.maker();
            if (maker != null && maker.level() == minion.level() && !maker.isSpectator()) {
                allies.add(maker);
            }
            allies.addAll(minion.level().getEntitiesOfClass(LivingEntity.class, minion.getBoundingBox().inflate(range),
                    e -> e != minion && (e instanceof AbstractVillager || e instanceof MinionEntity other && !other.poweredDown() && !other.cybernetic()
                            && minion.makerId() != null && minion.makerId().equals(other.makerId()))));
            return allies.stream().filter(e -> e.isAlive() && hurt(e) && e.distanceToSqr(minion) < range * range && minion.hasLineOfSight(e))
                    .min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
        }

        private static boolean hurt(LivingEntity entity) {
            return entity.getHealth() <= entity.getMaxHealth() - 4.0F;
        }

        @Override
        public boolean canContinueToUse() {
            return patient != null && patient.isAlive() && !thrown && hurt(patient) && minion.hasJob("medic") && minion.tickCount - startedAt < 400
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
            potion.shoot(dx, dy + flat * 0.2, dz, 0.75F, 8.0F);
            level.playSound(null, minion.getX(), minion.getY(), minion.getZ(), SoundEvents.WITCH_THROW, SoundSource.NEUTRAL, 1.0F,
                    0.8F + minion.getRandom().nextFloat() * 0.4F);
            level.addFreshEntity(potion);
            minion.swing(InteractionHand.MAIN_HAND);
            cooldown = minion.tickCount + 60;
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

    /**
     * A butcher takes carcasses near home apart by hand (docs/PARTS-AND-TRAITS.md section 6.9), a blow a little under a
     * second, through the same CarcassButchery a player's Cleaver or Flensing Knife uses, so the yields are a player's
     * hand yields: with a Cleaver it breaks down loose pieces and cuts limbs off whole bodies; with a Flensing Knife it
     * skins them. What comes off goes into what it carries.
     */
    static class Butcher extends Goal {
        private static final int STROKE = 15;
        private final MinionEntity minion;
        @Nullable
        private UUID carcass;
        @Nullable
        private String bone;
        private boolean done;
        private int nextStroke;
        private final List<UUID> unreachable = new ArrayList<>();
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
            if (!minion.hasJob("butcher") || minion.getRandom().nextInt(20) != 0 || !(minion.level() instanceof ServerLevel level)
                    || !minion.canCarry(new ItemStack(Items.BEEF))) {
                return false;
            }
            if (minion.tickCount - forgotAt > 600) {
                unreachable.clear();
                forgotAt = minion.tickCount;
            }
            return pick(level);
        }

        /** The nearest carcass by home with work in it for the blade it holds, and the bone to work on. */
        private boolean pick(ServerLevel level) {
            boolean skinning = minion.getMainHandItem().getItem() instanceof FlensingKnifeItem;
            Vec3 home = Vec3.atBottomCenterOf(minion.home());
            double best = Double.MAX_VALUE;
            carcass = null;
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
                if (d < BUTCHER_RANGE * BUTCHER_RANGE && d < best) {
                    best = d;
                    carcass = c.id;
                    bone = work;
                }
            }
            return carcass != null;
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
            return carcass != null && !done && minion.hasJob("butcher") && minion.canCarry(new ItemStack(Items.BEEF));
        }

        @Override
        public void start() {
            minion.working = true;
            done = false;
            nextStroke = minion.tickCount + STROKE;
            approach.reset(minion);
        }

        @Override
        public void stop() {
            minion.working = false;
            carcass = null;
            bone = null;
        }

        @Override
        public void tick() {
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
            double reach = 2.0 + minion.getBbWidth() / 2.0;
            if (minion.distanceToSqr(point) > reach * reach) {
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
            nextStroke = minion.tickCount + STROKE;
            ItemStack blade = minion.getMainHandItem();
            boolean skinning = blade.getItem() instanceof FlensingKnifeItem;
            boolean bloody = com.avicagan.bloodandbones.carcass.Blood.bloody(c);
            // what comes off goes into its hands, as a machine's yields go to the machine
            boolean did = CarcassButchery.capturing(stack -> keep(minion, stack),
                    () -> skinning ? CarcassButchery.skin(level, null, c, at) : CarcassButchery.cut(level, null, c, bone, at));
            minion.swing(InteractionHand.MAIN_HAND);
            if (did && bloody) {
                com.avicagan.bloodandbones.carcass.Blood.bloody(blade, level);
            }
            if (!did || skinning && c.skinned) {
                done = true;
            }
        }
    }

    // ---- hauler

    /**
     * A hauler drags whole carcasses lying within 24 of home to the nearest free Shackle Hook or Bleeding Rack, with the
     * same drag a player's Meat Hook makes (CarcassDrag: the spring pull, the slowdown by the carcass's weight that its
     * drag strength eases, the drips and trail). At a hook it hangs the carcass by its torso as a player's Meat Hook
     * click does, from where a player could: the tip within reach above the body, nothing solid between (never through
     * a floor to the storey above); at a rack it pulls the body over the tray and lets it down there to bleed. Someone
     * else taking hold of the body (a player's Meat Hook, another hauler) makes it let go.
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
        /** Ticks a body let down on a rack is given to settle before it is checked to lie in the tray. */
        private static final int SETTLE = 40;
        /** Ticks it may stand still with the body not over the tray before it takes another pass. */
        private static final int STUCK = 60;
        /** Passes over a rack before it gives the body up. */
        private static final int TRIES = 3;
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
            if (!minion.hasJob("hauler") || minion.getRandom().nextInt(20) != 0 || !(minion.level() instanceof ServerLevel level)
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
            List<BlockPos> ends = destinations(level);
            if (ends.isEmpty()) {
                return false;
            }
            double best = Double.MAX_VALUE;
            carcass = null;
            to = null;
            for (CarcassSavedData.Carcass c : CarcassSavedData.get(level).all()) {
                // the cheap tests first: most of the world's carcasses lie nowhere near home
                Vector3d at = CarcassAssembler.boneWorldPosition(level, c, c.rootBone);
                double d = at == null ? Double.MAX_VALUE : home.distanceToSqr(at.x, at.y, at.z);
                if (d >= HAUL_RANGE * HAUL_RANGE || d >= best || unreachable.contains(c.id) || !whole(c)
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
            int r = Mth.ceil(HAUL_RANGE);
            for (int cx = (home.getX() - r) >> 4; cx <= (home.getX() + r) >> 4; cx++) {
                for (int cz = (home.getZ() - r) >> 4; cz <= (home.getZ() + r) >> 4; cz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (be.getBlockPos().distSqr(home) >= HAUL_RANGE * HAUL_RANGE) {
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
            double near = HAUL_RANGE + 4.0;
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
            return carcass != null && to != null && !done && minion.hasJob("hauler") && minion.tickCount - startedAt < GIVE_UP && minion.level().isLoaded(to);
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
         * The body missed the tray: it takes hold again from where it stands and walks another line over the rack, or,
         * after {@link #TRIES} passes, gives it up.
         */
        private void another() {
            stuckSince = -1;
            if (++tries >= TRIES) {
                giveUp();
                return;
            }
            dragging = false;
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
                if (minion.tickCount < settleUntil) {
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
                // it walks a straight line from here through the hook or rack, fixed now: towed behind it, the body comes
                // onto that line and so under or over it
                Vec3 end = level.getBlockEntity(to) instanceof ShackleHookBlockEntity hook ? ShackleHookBlock.tip(to, hook.getBlockState()) : Vec3.atCenterOf(to);
                Vec3 line = new Vec3(end.x - minion.getX(), 0.0, end.z - minion.getZ());
                through = line.lengthSqr() < 1.0e-4 ? Vec3.ZERO : line.normalize();
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
                boolean stood = minion.getNavigation().isDone();
                boolean overTray = Math.hypot(torso.x - tray.x, torso.z - tray.z) < ON_TRAY && torso.y > tray.y - 0.5 && torso.y < tray.y + 2.0;
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
                giveUp();
            }
        }
    }

    /** A cell of a carcass's torso to hook, in its body's plot (as a player's hook takes one), or null if it has none. */
    @Nullable
    static BlockPos torsoCell(ServerLevel level, CarcassSavedData.Carcass carcass) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        UUID id = carcass.bones.get(carcass.rootBone);
        if (container == null || id == null || !(container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body) || body.isRemoved()) {
            return null;
        }
        return body.getPlot().getCenterBlock();
    }
}
