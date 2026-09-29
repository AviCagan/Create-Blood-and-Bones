package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.DormantMinionItem;
import com.avicagan.bloodandbones.minion.MinionFitness;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.MinionTask;
import com.avicagan.bloodandbones.minion.MinionTasks;
import com.avicagan.bloodandbones.network.MinionTaskPayload;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBItems;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tasks in the world (docs/NEXT.md 1.9, "in the world"; slice 7's jobs before them): the task screen and the checks on its
 * requests, the task a minion wakes to, old jobs read as tasks, each task at its work, and the tasks done with the maker.
 * The working tests wall their pen in glass, so a minion never sees or wanders to what the other tests in this world have
 * made.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinionTaskTests {
    private static ResourceLocation mob(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    private static PieceRef ref(String entity, String bone) {
        return ref(entity, bone, Map.of());
    }

    private static PieceRef ref(String entity, String bone, Map<String, String> traits) {
        return new PieceRef(mob(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + ".png"), List.of(), 1.0F, false, traits, false);
    }

    private static PieceRef villagerHead(String profession) {
        return ref("villager", "head", Map.of("profession", profession));
    }

    /** A zombie's torso, arms and legs under this head: hands to work with. */
    private static MinionBuild armed(PieceRef head) {
        return MinionBuild.of(ref("zombie", "body")).with("head", head).with("right_arm", ref("zombie", "right_arm")).with("left_arm", ref("zombie", "left_arm"))
                .with("right_leg", ref("zombie", "right_leg")).with("left_leg", ref("zombie", "left_leg"));
    }

    /** The same torso and legs with no arms. */
    private static MinionBuild armless(PieceRef head) {
        return MinionBuild.of(ref("zombie", "body")).with("head", head).with("right_leg", ref("zombie", "right_leg")).with("left_leg", ref("zombie", "left_leg"));
    }

    /** A cow as it was, its head swapped for another's. */
    private static MinionBuild cowWith(PieceRef head) {
        MinionBuild build = MinionBuild.of(ref("cow", "body")).with("head", head);
        for (String leg : new String[]{"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"}) {
            build = build.with(leg, ref("cow", leg));
        }
        return build;
    }

    /** A stand-in maker standing here, who remembers what the action bar told them and the task screens they were shown. */
    static final class Maker extends Player implements MinionTasks.Viewer {
        final List<Component> told = new ArrayList<>();
        final List<MinionTaskPayload.Open> shown = new ArrayList<>();

        Maker(GameTestHelper helper, BlockPos at) {
            super(helper.getLevel(), helper.absolutePos(at), 0.0F, new GameProfile(UUID.randomUUID(), "test-maker"));
            setPos(Vec3.atBottomCenterOf(helper.absolutePos(at)));
            setOldPosAndRot();
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
            told.add(message);
        }

        @Override
        public void showTasks(MinionTaskPayload.Open open) {
            shown.add(open);
        }

        /** Walk to this spot of the test (a stand-in never moves by itself). */
        void stand(GameTestHelper helper, Vec3 at) {
            Vec3 pos = helper.absoluteVec(at);
            setPos(pos);
            setOldPosAndRot();
        }
    }

    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build, Player maker) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, build, 1000.0F);
        // the pens are open to the sky, and a zombie's torso burns by day unless it wears a helmet (section 8.1)
        minion.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        level.addFreshEntity(minion);
        return minion;
    }

    /** Its maker hands it this, as a player's right-click does. */
    private static void give(MinionEntity minion, Player maker, ItemStack stack) {
        maker.setShiftKeyDown(false);
        maker.setItemInHand(InteractionHand.MAIN_HAND, stack);
        minion.interact(maker, InteractionHand.MAIN_HAND);
    }

    /** Its maker crouches and uses an empty hand on it: the task screen, or folding it up if it is down. */
    private static void crouchClick(MinionEntity minion, Player maker) {
        maker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        maker.setShiftKeyDown(true);
        minion.interact(maker, InteractionHand.MAIN_HAND);
        maker.setShiftKeyDown(false);
    }

    /** Glass walls round the pen, three high, so a minion keeps to its own test and sees none of the others. */
    static void pen(GameTestHelper helper) {
        for (int i = 0; i <= 10; i++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(i, y, 0), Blocks.GLASS);
                helper.setBlock(new BlockPos(i, y, 10), Blocks.GLASS);
                helper.setBlock(new BlockPos(0, y, i), Blocks.GLASS);
                helper.setBlock(new BlockPos(10, y, i), Blocks.GLASS);
            }
        }
    }

    private static AABB area(GameTestHelper helper) {
        return AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(10, 7, 10)));
    }

    private static int count(MinionEntity minion, net.minecraft.world.item.Item item) {
        return minion.inventory.countItem(item);
    }

    /** Whether its status line (a plain click's) names this key anywhere in it. */
    static boolean statusSays(MinionEntity minion, String key) {
        return names(MinionTasks.status(minion), key);
    }

    /** Whether this text is, or holds among its arguments or siblings, a translation of this key. */
    static boolean names(Component text, String key) {
        if (text.getContents() instanceof TranslatableContents t) {
            if (t.getKey().equals(key)) {
                return true;
            }
            for (Object arg : t.getArgs()) {
                if (arg instanceof Component c && names(c, key)) {
                    return true;
                }
            }
        }
        for (Component sibling : text.getSiblings()) {
            if (names(sibling, key)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the action bar's last line was this one. */
    private static boolean said(Maker maker, String key) {
        return !maker.told.isEmpty() && maker.told.get(maker.told.size() - 1).getContents() instanceof TranslatableContents last && last.getKey().equals(key);
    }

    /** A saved minion, loaded again into a new one, as a world does. */
    private static MinionEntity reload(GameTestHelper helper, net.minecraft.nbt.CompoundTag tag) {
        MinionEntity loaded = BBEntities.MINION.get().create(helper.getLevel());
        loaded.load(tag);
        return loaded;
    }

    // ---- what a hand is for

    /**
     * A hand's work needs a hand (section 5.6; docs/NEXT.md 1.2): a chicken's own wings under a butcher's head cannot hold a
     * surgeon's blade, so it cannot be a surgeon, and a bow held in its beak is still no ranged attack; a zombie's arms under
     * the same head make it a surgeon and a butcher.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wingsAreNoHands(GameTestHelper helper) {
        MinionBuild winged = MinionBuild.of(ref("chicken", "body")).with("head", villagerHead("butcher")).with("right_wing", ref("chicken", "right_wing"))
                .with("left_wing", ref("chicken", "left_wing"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, winged);
        MinionFitness.Row surgeon = MinionFitness.of(PartsData.SERVER, winged, stats, MinionFitness.Context.NONE, MinionTask.SURGEON);
        if (stats.strikes().size() != 2 || !stats.strikes().stream().allMatch(s -> "wing".equals(s.grip())) || surgeon.can()
                || !"bloodandbones.minion.cannot.hand".equals(surgeon.cannot().orElse(""))) {
            helper.fail("A butcher's head on a chicken's wings cannot be a surgeon, its wings no hands: " + surgeon.cannot() + " " + stats.strikes());
            return;
        }
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), winged, maker);
        give(minion, maker, new ItemStack(Items.BOW));
        if (!minion.getMainHandItem().is(Items.BOW) || minion.hasRangedAttack() || minion.setTask(MinionTask.SURGEON)) {
            helper.fail("It may hold a bow in its beak, but cannot draw it with wings, nor be a surgeon: " + minion.getMainHandItem() + " "
                    + minion.hasRangedAttack() + " " + minion.task());
            return;
        }
        MinionEntity handed = minion(helper, new BlockPos(7, 2, 7), armed(villagerHead("butcher")), maker);
        if (!handed.setTask(MinionTask.SURGEON) || !handed.setTask(MinionTask.BUTCHER)) {
            helper.fail("With a zombie's hands the same head should take surgery and butchery: " + handed.task());
            return;
        }
        minion.discard();
        handed.discard();
        helper.succeed();
    }

    // ---- the tasks at work

    /**
     * A sentry holding a bow never moves from its post: it turns to a husk in its pen and shoots it, arrows from what it
     * carries. Its maker stocks the arrows as they hand it anything, by using the stack on it: they go in with what it
     * carries, the bow stays in its hand.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sentryShootsWithHeldBow(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 5), armed(ref("pillager", "head")), maker);
        give(minion, maker, new ItemStack(Items.BOW));
        give(minion, maker, new ItemStack(Items.ARROW, 16));
        if (!minion.getMainHandItem().is(Items.BOW) || count(minion, Items.ARROW) != 16 || !maker.getMainHandItem().isEmpty()
                || !said(maker, "bloodandbones.minion.carries")) {
            helper.fail("The stack of arrows should go in with what it carries, the bow kept in hand: holding " + minion.getMainHandItem() + ", "
                    + count(minion, Items.ARROW) + " arrows carried, " + maker.getMainHandItem() + " left in the maker's hand");
            return;
        }
        if (!minion.setTask(MinionTask.SENTRY)) {
            helper.fail("Holding a bow, a pillager's head should take up sentry");
            return;
        }
        Vec3 post = minion.position();
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 5));
        husk.setNoAi(true);
        float full = husk.getHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(minion.position().distanceTo(post) < 1.0, "the sentry left its post: " + minion.position() + " from " + post);
            helper.assertTrue(husk.getHealth() < full || !husk.isAlive(), "the husk has not been hit yet (" + count(minion, Items.ARROW) + " arrows left)");
            helper.assertTrue(husk.getLastDamageSource() != null && husk.getLastDamageSource().getDirectEntity() instanceof AbstractArrow
                    && husk.getLastDamageSource().getEntity() == minion, "the husk was hurt by something else: " + husk.getLastDamageSource());
            helper.assertTrue(count(minion, Items.ARROW) < 16, "no arrow was taken from what it carries");
        });
    }

    /**
     * A sentry's arrows pass through its own side: loosed straight at a villager and at another of its maker's minions they
     * hurt neither and fly on, while one loosed at a husk hurts it.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sentryArrowsSpareItsSide(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity sentry = minion(helper, new BlockPos(2, 2, 5), armed(ref("pillager", "head")), maker);
        sentry.setNoAi(true);
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
        villager.setNoAi(true);
        // a cow of its maker's (a zombie's torso would burn in the sun, which is not what this looks at)
        MinionEntity other = minion(helper, new BlockPos(6, 2, 5), cowWith(ref("cow", "head")), maker);
        other.setNoAi(true);
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 2, 7));
        husk.setNoAi(true);
        for (net.minecraft.world.entity.LivingEntity target : List.of(villager, other, husk)) {
            net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(level, sentry, new ItemStack(Items.ARROW), null);
            arrow.shoot(target.getX() - sentry.getX(), target.getY(0.5) - arrow.getY(), target.getZ() - sentry.getZ(), 1.6F, 0.0F);
            level.addFreshEntity(arrow);
        }
        // long after all three would have landed
        helper.runAfterDelay(30, () -> {
            if (!(husk.getHealth() < husk.getMaxHealth())) {
                helper.fail("The arrow loosed at the husk should hurt it");
                return;
            }
            if (villager.getHealth() < villager.getMaxHealth() || other.getHealth() < other.getMaxHealth()) {
                helper.fail("Its arrows should pass through the villager and its maker's other minion: " + villager.getHealth() + ", " + other.getHealth());
                return;
            }
            sentry.discard();
            other.discard();
            helper.succeed();
        });
    }

    /**
     * A sentry with a crossbow loads it from the arrows its maker handed it and shoots a husk; its arrows, like its bow's,
     * can be picked up where they land (vanilla leaves a mob's crossbow arrows to no one).
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void crossbowSentryArrowsCanBePickedUp(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 5), armed(ref("pillager", "head")), maker);
        give(minion, maker, new ItemStack(Items.CROSSBOW));
        give(minion, maker, new ItemStack(Items.ARROW, 16));
        if (!minion.getMainHandItem().is(Items.CROSSBOW) || count(minion, Items.ARROW) != 16 || !minion.setTask(MinionTask.SENTRY)) {
            helper.fail("Holding a crossbow and carrying the arrows, a pillager's head should take up sentry: " + minion.getMainHandItem() + ", "
                    + count(minion, Items.ARROW) + " arrows, " + minion.task());
            return;
        }
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 5));
        husk.setNoAi(true);
        float full = husk.getHealth();
        AABB area = area(helper);
        Set<AbstractArrow.Pickup> seen = new HashSet<>();
        helper.onEachTick(() -> level.getEntitiesOfClass(AbstractArrow.class, area, a -> a.getOwner() == minion).forEach(a -> seen.add(a.pickup)));
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < full || !husk.isAlive(), "the husk has not been hit yet (" + count(minion, Items.ARROW) + " arrows left)");
            helper.assertTrue(seen.contains(AbstractArrow.Pickup.ALLOWED) && !seen.contains(AbstractArrow.Pickup.DISALLOWED),
                    "its crossbow's arrows should be free to pick up: " + seen);
            // shot enough: it looks for nothing else to shoot while the other tests run
            minion.discard();
        });
    }

    /**
     * A courier at home holding an amethyst shard (its sample: the scavenger's way, docs/NEXT.md 1.1) fetches the shards
     * lying across its pen to the chest by home, and leaves the stick.
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void courierWithSampleFetchesItsLike(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(1, 2, 3), Blocks.CHEST);
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionBuild build = armed(ref("chicken", "head"));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), build, maker);
        give(minion, maker, new ItemStack(Items.AMETHYST_SHARD));
        if (!minion.setTask(MinionTask.COURIER)) {
            helper.fail("A chicken's head should take carrying: " + minion.task());
            return;
        }
        BlockPos far = helper.absolutePos(new BlockPos(8, 2, 8));
        level.addFreshEntity(new ItemEntity(level, far.getX() + 0.5, far.getY() + 0.2, far.getZ() + 0.5, new ItemStack(Items.AMETHYST_SHARD, 3)));
        BlockPos other = helper.absolutePos(new BlockPos(8, 2, 2));
        ItemEntity stick = new ItemEntity(level, other.getX() + 0.5, other.getY() + 0.2, other.getZ() + 0.5, new ItemStack(Items.STICK));
        level.addFreshEntity(stick);
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 3));
            helper.assertTrue(chest.countItem(Items.AMETHYST_SHARD) == 3, "the chest has " + chest.countItem(Items.AMETHYST_SHARD)
                    + " of the 3 shards (it carries " + count(minion, Items.AMETHYST_SHARD) + ")");
            helper.assertTrue(stick.isAlive() && chest.countItem(Items.STICK) == 0 && count(minion, Items.STICK) == 0, "it should leave the stick alone");
            helper.assertTrue(minion.getMainHandItem().is(Items.AMETHYST_SHARD), "it should still hold its own shard");
        });
    }

    /**
     * A fisherman's head with a rod in hand walks to the pool by home and fishes: a catch in what it carries, a point off
     * the rod. Without one it fishes by hand, poorly, and is shown the rod's fitness; a cod's head fishes with its mouth as
     * well as with a rod.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fisherFishesByWater(GameTestHelper helper) {
        pen(helper);
        for (int x = 6; x <= 9; x++) {
            for (int z = 5; z <= 8; z++) {
                boolean rim = x == 6 || x == 9 || z == 5 || z == 8;
                helper.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE : Blocks.WATER);
            }
        }
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), armed(villagerHead("fisherman")), maker);
        MinionFitness.Row byHand = minion.row(MinionTask.FISHER, MinionTask.Anchor.HOME);
        if (!byHand.can() || byHand.waitsFor().isPresent() || byHand.withTool().isEmpty() || !(byHand.withTool().get() > byHand.fitness())) {
            helper.fail("With no rod, a fisherman's head should fish by hand, and be shown a rod's better fitness: " + byHand.fitness() + " " + byHand.withTool());
            return;
        }
        MinionEntity cod = minion(helper, new BlockPos(2, 2, 8), armless(ref("cod", "head")), maker);
        if (!cod.setTask(MinionTask.FISHER) || cod.row(MinionTask.FISHER, MinionTask.Anchor.HOME).fitness() < 1.0F) {
            helper.fail("A cod's head fishes with its mouth as well as with a rod: " + cod.row(MinionTask.FISHER, MinionTask.Anchor.HOME).fitness());
            return;
        }
        cod.discard();
        give(minion, maker, new ItemStack(Items.FISHING_ROD));
        if (!minion.setTask(MinionTask.FISHER) || !(minion.fitness(MinionTask.FISHER) > byHand.fitness())) {
            helper.fail("With a rod, it should take up fishing, and be fitter at it");
            return;
        }
        // no half-minute wait in a test: the first catch comes as soon as it is at the water
        MinionTasks.hurry(minion);
        helper.succeedWhen(() -> {
            int caught = 0;
            for (int i = 0; i < minion.inventory.getContainerSize(); i++) {
                caught += minion.inventory.getItem(i).getCount();
            }
            helper.assertTrue(caught > 0, "the fisher has caught nothing yet (at " + minion.position() + ")");
            helper.assertTrue(minion.getMainHandItem().is(Items.FISHING_ROD) && minion.getMainHandItem().getDamageValue() >= 1, "the rod should wear");
        });
    }

    /**
     * A hunter (a wolf's head, which never wakes to hunting: never straight to the animals round its table) with a Meat
     * Hook in hand kills a cow in its pen, and the cow is left an intact carcass, as a player's Meat Hook kill leaves it: no
     * beef on the ground.
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void hunterWithMeatHookLeavesCarcass(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Set<UUID> before = new HashSet<>();
        CarcassSavedData.get(level).all().forEach(c -> before.add(c.id));
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), armed(ref("wolf", "head/real_head")), maker);
        if (minion.hasTask(MinionTask.HUNTER) || !minion.setTask(MinionTask.HUNTER)) {
            helper.fail("A wolf's head should take hunting, but not wake to it: " + minion.task());
            return;
        }
        give(minion, maker, new ItemStack(BBItems.MEAT_HOOK.get()));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(7, 2, 7));
        AABB area = area(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(!cow.isAlive() || cow.isRemoved(), "the cow is still alive (" + cow.getHealth() + ")");
            boolean carcass = CarcassSavedData.get(level).all().stream().anyMatch(c -> !before.contains(c.id) && c.entity.equals(mob("cow"))
                    && inside(area, CarcassAssembler.boneWorldPosition(level, c, c.rootBone)));
            helper.assertTrue(carcass, "the kill should leave a cow carcass in the pen");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.BEEF) || e.getItem().is(Items.LEATHER)).isEmpty(),
                    "a Meat Hook kill drops no loot");
        });
    }

    private static boolean inside(AABB area, Vector3d at) {
        return at != null && area.inflate(2.0).contains(at.x, at.y, at.z);
    }

    /**
     * A hauler (a horse's head) drags a cow carcass lying in one corner of its pen to the Shackle Hook under the ceiling
     * in the other, with a player's drag, and hangs it there by its torso. While dragging it is slowed by the carcass, less so for the drag
     * strength its horse torso's hauler trait gives it.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void haulerDragsCarcassToHook(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(8, 6, 8), Blocks.STONE);
        helper.setBlock(new BlockPos(8, 5, 8), BBBlocks.SHACKLE_HOOK.get().defaultBlockState().setValue(ShackleHookBlock.FACING, Direction.UP));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (carcass == null) {
            helper.fail("The cow carcass was not made");
            return;
        }
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(6, 2, 2), horse(), maker);
        if (!minion.setTask(MinionTask.HAULER)) {
            helper.fail("A horse should take hauling: " + minion.task());
            return;
        }
        double[] slowest = {0.0};
        // how far the drag itself brought the body, before it went up on the hook
        Vector3d[] from = {null};
        double[] dragged = {0.0};
        helper.onEachTick(() -> {
            var speed = minion.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
            var dragging = speed == null ? null : speed.getModifier(BloodAndBones.asResource("dragging"));
            if (dragging != null) {
                slowest[0] = Math.min(slowest[0], dragging.amount());
                Vector3d at = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
                if (at != null) {
                    from[0] = from[0] == null ? new Vector3d(at) : from[0];
                    dragged[0] = Math.max(dragged[0], Math.hypot(at.x - from[0].x, at.z - from[0].z));
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(ShackleHookBlockEntity.isHanging(level, carcass.id), "the carcass is not hanging on the hook yet (minion at "
                    + minion.position() + ", dragging " + com.avicagan.bloodandbones.carcass.CarcassDrag.isDragging(minion) + ")");
            ShackleHookBlockEntity hook = (ShackleHookBlockEntity) helper.getBlockEntity(new BlockPos(8, 5, 8));
            helper.assertTrue(hook.isOccupied() && carcass.rootBone.equals(hook.hookedBone()), "it should hang by its torso");
            helper.assertTrue(dragged[0] > 2.0, "the drag should have brought the body across the pen (it moved " + dragged[0] + " blocks)");
            double strength = minion.getAttributeValue(com.avicagan.bloodandbones.registry.BBAttributes.DRAG_STRENGTH);
            helper.assertTrue(slowest[0] < 0.0 && strength > 0.0, "dragging should have slowed it, eased by its drag strength (" + slowest[0] + ", "
                    + strength + ")");
            helper.assertTrue(!com.avicagan.bloodandbones.carcass.CarcassDrag.isDragging(minion) && minion.getAttribute(
                    net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getModifier(BloodAndBones.asResource("dragging")) == null,
                    "once hung, the drag and its slowdown should be over");
        });
    }

    /** A horse on its own legs, its own head on: a hauler (its head has a knack for it; its torso's hauler trait eases the drag). */
    private static MinionBuild horse() {
        MinionBuild horse = MinionBuild.of(ref("horse", "body")).with("head", ref("horse", "head_parts"));
        for (String leg : new String[]{"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"}) {
            horse = horse.with(leg, ref("horse", leg));
        }
        return horse;
    }

    /**
     * A hauler (a leatherworker's head) with a Bleeding Rack by home and no hook drags the cow carcass over the tray and
     * lets it down there, where it bleeds into it.
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void haulerLaysCarcassOnRack(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(5, 2, 5), BBBlocks.BLEEDING_RACK.getDefaultState());
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 2, 2));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (carcass == null) {
            helper.fail("The cow carcass was not made");
            return;
        }
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        // a leatherworker's head hauls; on a zombie's frame it is narrow enough to walk right past the rack
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 6), armed(villagerHead("leatherworker")), maker);
        if (!minion.setTask(MinionTask.HAULER)) {
            helper.fail("A leatherworker's head should take hauling: " + minion.task());
            return;
        }
        helper.succeedWhen(() -> {
            Vector3d at = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
            helper.assertTrue(!com.avicagan.bloodandbones.carcass.CarcassDrag.isDragging(minion), "it has not let go of it on the rack yet (at " + at
                    + ", the hauler at " + minion.position() + ")");
            var rack = (com.avicagan.bloodandbones.bleeding.BleedingRackBlockEntity) helper.getBlockEntity(new BlockPos(5, 2, 5));
            helper.assertTrue(rack.getFluid().getAmount() > 0, "the carcass lying on the rack has not bled into it yet (resting " + carcass.resting
                    + ", blood " + carcass.blood + ", at " + at + ", rack at " + helper.absolutePos(new BlockPos(5, 2, 5)) + ")");
        });
    }

    /**
     * A butcher's head set to butchery with no blade waits for one, and says so; with a Cleaver in hand it takes a cow
     * carcass by home apart by hand: a leg cut off and broken down,
     * what came off in its own hands (seen in what it carries, none left lying). A leg's small yield may roll nothing, so
     * it has the time and the reach (home beside the cow) to go on to the next piece.
     */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void butcherButchersWithCleaver(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(6, 2, 6));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (carcass == null) {
            helper.fail("The cow carcass was not made");
            return;
        }
        int bones = carcass.bones.size();
        Set<UUID> before = new HashSet<>();
        CarcassSavedData.get(level).all().forEach(c -> before.add(c.id));
        before.remove(carcass.id);
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(4, 2, 4), armed(villagerHead("butcher")), maker);
        if (!minion.setTask(MinionTask.BUTCHER) || !statusSays(minion, "bloodandbones.minion.wants.butcher")) {
            helper.fail("With no blade, a butcher's head should take butchery and wait for one: " + MinionTasks.status(minion).getString());
            return;
        }
        give(minion, maker, new ItemStack(BBItems.CLEAVER.get()));
        if (!minion.hasTask(MinionTask.BUTCHER) || statusSays(minion, "bloodandbones.minion.wants.butcher")) {
            helper.fail("With a Cleaver in hand, it should wait no longer");
            return;
        }
        AABB area = area(helper);
        // the most it has carried at once (a butcher stores what it has in a container by home, as a courier does)
        int[] carried = {0};
        helper.onEachTick(() -> {
            int now = 0;
            for (int i = 0; i < minion.inventory.getContainerSize(); i++) {
                now += minion.inventory.getItem(i).getCount();
            }
            carried[0] = Math.max(carried[0], now);
        });
        helper.succeedWhen(() -> {
            // this cow's pieces: the carcass and whatever was cut off it since
            int left = 0;
            for (CarcassSavedData.Carcass c : CarcassSavedData.get(level).all()) {
                if (!before.contains(c.id) && c.entity.equals(mob("cow")) && inside(area, CarcassAssembler.boneWorldPosition(level, c, c.rootBone))) {
                    left += Math.max(c.bones.size(), c.resting ? c.restPoses.size() + 1 : 0);
                }
            }
            helper.assertTrue(left < bones, "no piece of the cow has been broken down yet (" + left + " of " + bones + " left)");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.BEEF) || e.getItem().is(Items.BONE)
                    || e.getItem().is(BBItems.OFFAL.get())).isEmpty(), "what it cut off should be in its hands, not on the ground");
            helper.assertTrue(carried[0] > 0, "what it broke down should be in what it carries, but it has carried nothing yet (" + left + " of "
                    + bones + " pieces left, the butcher at " + minion.position() + ")");
        });
    }

    // ---- the levers: what a minion's fitness makes of its work (docs/NEXT.md 1.2; stage C)

    /** The spec's spider (6.5): its torso with a zombie's arm in each of its eight leg sockets, under this head. */
    private static MinionBuild spiderOfArms(PieceRef head) {
        MinionBuild build = MinionBuild.of(ref("spider", "body1")).with("head", head);
        String[] sockets = {"right_front_leg", "left_front_leg", "right_middle_front_leg", "left_middle_front_leg", "right_middle_hind_leg",
                "left_middle_hind_leg", "right_hind_leg", "left_hind_leg"};
        for (int i = 0; i < sockets.length; i++) {
            build = build.with(sockets[i], ref("zombie", i % 2 == 0 ? "right_arm" : "left_arm"));
        }
        return build;
    }

    /** A spider's torso on four of its own legs, with a zombie's arm in each of its four front sockets, under this head. */
    private static MinionBuild spiderOnFour(PieceRef head) {
        MinionBuild build = MinionBuild.of(ref("spider", "body1")).with("head", head);
        String[] arms = {"right_front_leg", "left_front_leg", "right_middle_front_leg", "left_middle_front_leg"};
        for (int i = 0; i < arms.length; i++) {
            build = build.with(arms[i], ref("zombie", i % 2 == 0 ? "right_arm" : "left_arm"));
        }
        for (String leg : new String[]{"right_middle_hind_leg", "left_middle_hind_leg", "right_hind_leg", "left_hind_leg"}) {
            build = build.with(leg, ref("spider", leg));
        }
        return build;
    }

    /** A whole villager with no trade: its folded arms hold a blade but never strike. */
    private static MinionBuild villager() {
        return MinionBuild.of(ref("villager", "body")).with("head", villagerHead("none")).with("arms", ref("villager", "arms"))
                .with("right_leg", ref("villager", "right_leg")).with("left_leg", ref("villager", "left_leg"));
    }

    /** A whole rabbit: a torso too light to pull much. */
    private static MinionBuild rabbit() {
        return MinionBuild.of(ref("rabbit", "body")).with("head", ref("rabbit", "head")).with("right_front_leg", ref("rabbit", "right_front_leg"))
                .with("left_front_leg", ref("rabbit", "left_front_leg")).with("right_haunch", ref("rabbit", "right_haunch"))
                .with("left_haunch", ref("rabbit", "left_haunch"));
    }

    /** The middle one of these ticks' gaps: for strokes on one piece after another, the time between two on the same piece. */
    private static int medianGap(List<Integer> ticks) {
        List<Integer> gaps = new ArrayList<>();
        for (int i = 1; i < ticks.size(); i++) {
            gaps.add(ticks.get(i) - ticks.get(i - 1));
        }
        gaps.sort(Integer::compare);
        return gaps.isEmpty() ? -1 : gaps.get(gaps.size() / 2);
    }

    /**
     * A 200% butcher (a butcher's head over a spider's torso on four of its legs, with four zombie arms) and a 50% one (a whole villager, whose
     * folded arms hold a Cleaver but never strike), each with a Cleaver and a cow of its own on its side of a glass wall.
     * Over 20 s the fit one's strokes come every 0.4 s (7.5 ticks, rounded), the poor one's every 1.5 s: about four times as
     * often at the carcass. The whole 20 s's count is not read: a piece's last stroke takes it off, and each butcher then
     * looks for its next piece and walks to it, the same for both and at random (about two seconds, now and then six), so
     * the fit one made from 1.2 to 4 times the poor one's strokes over the runs when this was written, most often about
     * twice. The poor one wastes half of each cut: a player's beef from a cow's body is 4.22 a cut, the poor butcher's about
     * half that (the same scale its goal cuts under, over 200 cuts, so the dice of each cut's rounding even out); the fit
     * one gets no more than a player's.
     */
    @GameTest(template = "empty", timeoutTicks = 520)
    public static void fitterButcherIsFasterAndCleaner(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        // a glass wall down the middle: each butcher keeps to its own half and its own cow
        for (int z = 1; z <= 9; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.GLASS);
            }
        }
        Set<UUID> before = new HashSet<>();
        CarcassSavedData.get(level).all().forEach(c -> before.add(c.id));
        for (int x : new int[]{2, 8}) {
            Cow cow = helper.spawn(EntityType.COW, new BlockPos(x, 2, 6));
            if (CarcassAssembler.assemble(cow, null) == null) {
                helper.fail("The cow carcass was not made");
                return;
            }
            cow.discard();
        }
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity fit = minion(helper, new BlockPos(2, 2, 3), spiderOnFour(villagerHead("butcher")), maker);
        MinionEntity poor = minion(helper, new BlockPos(8, 2, 3), villager(), maker);
        MinionTask.Data data = PartsData.of(level).task(MinionTask.BUTCHER);
        for (MinionEntity butcher : new MinionEntity[]{fit, poor}) {
            give(butcher, maker, new ItemStack(BBItems.CLEAVER.get()));
            // home by its cow, reaching only its own half
            butcher.setHome(helper.absolutePos(new BlockPos(butcher == fit ? 2 : 8, 2, 6)));
            if (!butcher.setTask(MinionTask.BUTCHER, MinionTask.Anchor.HOME, 3)) {
                helper.fail("Both should take butchery");
                return;
            }
        }
        float fitness = fit.fitness(MinionTask.BUTCHER);
        float poorly = poor.fitness(MinionTask.BUTCHER);
        if (Math.abs(fitness - 2.0F) > 1.0E-3F || Math.abs(poorly - 0.5F) > 1.0E-3F || MinionFitness.strokeTicks(data, fitness) != 8
                || MinionFitness.strokeTicks(data, poorly) != 30) {
            helper.fail("The butchers should be 200% and 50%, stroking every 8 and 30 ticks: " + fitness + ", " + poorly);
            return;
        }
        // every stroke, by the cuts on each bone of each side's cow (a piece's last stroke takes it off, or breaks it down)
        double wall = helper.absoluteVec(new Vec3(5.5, 2.0, 5.0)).x;
        AABB area = area(helper);
        Map<String, Integer> last = new java.util.HashMap<>();
        List<List<Integer>> strokes = List.of(new ArrayList<>(), new ArrayList<>());
        helper.onEachTick(() -> {
            Map<String, Integer> now = new java.util.HashMap<>();
            for (CarcassSavedData.Carcass c : CarcassSavedData.get(level).all()) {
                Vector3d at = CarcassAssembler.boneWorldPosition(level, c, c.rootBone);
                if (before.contains(c.id) || !c.entity.equals(mob("cow")) || !inside(area, at)) {
                    continue;
                }
                c.cuts.forEach((bone, cuts) -> now.put((at.x < wall ? "fit:" : "poor:") + bone, cuts));
            }
            int tick = (int) helper.getTick();
            now.forEach((key, cuts) -> {
                for (int i = last.getOrDefault(key, 0); i < cuts; i++) {
                    strokes.get(key.startsWith("fit:") ? 0 : 1).add(tick);
                }
            });
            last.forEach((key, cuts) -> {
                if (!now.containsKey(key)) {
                    for (int i = cuts; i < com.avicagan.bloodandbones.carcass.CarcassButchery.CUTS_TO_SEVER; i++) {
                        strokes.get(key.startsWith("fit:") ? 0 : 1).add(tick);
                    }
                }
            });
            last.clear();
            last.putAll(now);
        });
        helper.runAfterDelay(400, () -> {
            List<Integer> fast = strokes.get(0);
            List<Integer> slow = strokes.get(1);
            int fastGap = medianGap(fast);
            int slowGap = medianGap(slow);
            if (Math.abs(fastGap - 8) > 1 || Math.abs(slowGap - 30) > 1) {
                StringBuilder cows = new StringBuilder();
                for (CarcassSavedData.Carcass c : CarcassSavedData.get(level).all()) {
                    Vector3d at = CarcassAssembler.boneWorldPosition(level, c, c.rootBone);
                    if (!before.contains(c.id) && c.entity.equals(mob("cow")) && inside(area, at)) {
                        cows.append(c.rootBone).append(c.bones.keySet()).append(c.resting ? " resting" : "").append(" at ").append(helper.relativeVec(new Vec3(at.x, at.y, at.z))).append("; ");
                    }
                }
                helper.fail("At the carcass the fit one should stroke every 8 ticks and the poor one every 30: " + fastGap + ", " + slowGap
                        + " (strokes at " + fast + " and " + slow + "); the poor one at " + helper.relativeVec(poor.position()) + " holding " + poor.getMainHandItem()
                        + ", " + MinionTasks.status(poor).getString() + "; the fit one at " + helper.relativeVec(fit.position()) + "; cows: " + cows);
                return;
            }
            BloodAndBones.LOGGER.info("[butchers] over 20 s the 200% butcher struck {} times, the 50% one {}", fast.size(), slow.size());
            // what each gets of a cut, under the same scale its goal cuts under: the poor one about half a player's
            var table = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(mob("cow")).orElseThrow();
            CarcassSavedData.Carcass body = new CarcassSavedData.Carcass(UUID.randomUUID(), mob("cow"), "body");
            float[] shares = {1.0F, MinionFitness.yieldShare(poorly), MinionFitness.yieldShare(fitness)};
            int[] beef = new int[shares.length];
            for (int who = 0; who < shares.length; who++) {
                int[] got = {0};
                for (int cut = 0; cut < 200; cut++) {
                    com.avicagan.bloodandbones.carcass.CarcassButchery.yielding(shares[who], () -> com.avicagan.bloodandbones.carcass.CarcassButchery.capturing(
                            stack -> got[0] += stack.is(Items.BEEF) ? stack.getCount() : 0, () -> {
                                com.avicagan.bloodandbones.carcass.CarcassButchery.dropYields(level, body, table.part("body"), 1.0F, new Vector3d());
                                return true;
                            }));
                }
                beef[who] = got[0];
            }
            float half = beef[1] / (float) beef[0];
            if (half < 0.44F || half > 0.56F || shares[2] != 1.0F) {
                helper.fail("The poor one should get about half a player's beef a cut, the fit one all of it: " + beef[0] / 200.0F + " a cut for a player, "
                        + beef[1] / 200.0F + " for the poor one, a share of " + shares[2] + " for the fit one");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A hauler's slowdown towing a cow carcass (docs/NEXT.md 1.2): a whole rabbit hauls at about 37% (a torso that weighs
     * next to nothing), so it tows the cow with nearly three times a player's slowdown; a horse hauls at 200%, and its
     * torso's hauler trait eases a drag, but no minion tows with less slowdown than a player: its slowdown is exactly a
     * player's.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void poorHaulerCrawlsFitOneNoBetterThanAPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        cow.discard();
        BlockPos cell = carcass == null ? null : MinionTasks.torsoCell(level, carcass);
        if (cell == null) {
            helper.fail("The cow carcass was not made");
            return;
        }
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity weak = minion(helper, new BlockPos(2, 2, 5), rabbit(), maker);
        MinionEntity strong = minion(helper, new BlockPos(8, 2, 5), horse(), maker);
        weak.setNoAi(true);
        strong.setNoAi(true);
        if (!weak.setTask(MinionTask.HAULER) || !strong.setTask(MinionTask.HAULER)) {
            helper.fail("Anything can haul");
            return;
        }
        Player player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        net.minecraft.world.entity.LivingEntity[] draggers = {player, weak, strong};
        float[] slowed = new float[draggers.length];
        for (int i = 0; i < draggers.length; i++) {
            if (!com.avicagan.bloodandbones.carcass.CarcassDrag.start(level, draggers[i], cell, null)) {
                helper.fail("It should take hold of the cow: " + draggers[i]);
                return;
            }
            var dragging = draggers[i].getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                    .getModifier(BloodAndBones.asResource("dragging"));
            slowed[i] = dragging == null ? 0.0F : (float) -dragging.amount();
            com.avicagan.bloodandbones.carcass.CarcassDrag.stop(level, draggers[i]);
        }
        float weakly = weak.fitness(MinionTask.HAULER);
        float strength = (float) strong.getAttributeValue(com.avicagan.bloodandbones.registry.BBAttributes.DRAG_STRENGTH);
        if (!(slowed[0] > 0.0F) || weakly > 0.4F || Math.abs(slowed[1] - slowed[0] / weakly) > 1.0E-3F || slowed[1] / slowed[0] < 2.5F || slowed[1] > 0.9F) {
            helper.fail("The rabbit (" + weakly + ") should be slowed a player's ÷ its fitness, nearly three times: " + slowed[1] + " to a player's " + slowed[0]);
            return;
        }
        if (!(strong.fitness(MinionTask.HAULER) >= 1.0F) || !(strength > 0.0F) || Math.abs(slowed[2] - slowed[0]) > 1.0E-5F) {
            helper.fail("The horse (" + strong.fitness(MinionTask.HAULER) + ", drag strength " + strength + ") should be slowed exactly as a player is: "
                    + slowed[2] + " to " + slowed[0]);
            return;
        }
        // and its maker's task screen says so over the Hauler row (from between the two, within reach of both)
        maker.stand(helper, new Vec3(5.5, 2.0, 3.5));
        List<Component> weakLines = MinionTasks.open(weak, maker).orElseThrow().rows().get(MinionTask.HAULER.ordinal()).lines();
        List<Component> strongLines = MinionTasks.open(strong, maker).orElseThrow().rows().get(MinionTask.HAULER.ordinal()).lines();
        String times = com.avicagan.bloodandbones.minion.TaskWords.number(1.0F / weakly);
        if (weakLines.stream().noneMatch(line -> line.getContents() instanceof TranslatableContents t && t.getKey().equals("bloodandbones.minion.lever.hauler")
                && t.getArgs().length > 0 && times.equals(t.getArgs()[0])) || strongLines.stream().noneMatch(line -> names(line, "bloodandbones.minion.lever.hauler_player"))) {
            helper.fail("The Hauler row should say the rabbit is slowed " + times + " times as much as a player, the horse as much as one: " + weakLines + " | "
                    + strongLines);
            return;
        }
        helper.succeed();
    }

    /**
     * Blood at work follows fitness (docs/NEXT.md 1.2): 25 mB a minute at 100%, ÷ its fitness held between 50% and 200%. A
     * horse couriers at 200% and uses 12.5 mB a minute at work; an all-zombie courier (40%, held at 50%) 50; a brass horse (a
     * little under 200%: brass keeps no hide, so none of the horse's coat's speed) a quarter of what its fitness would cost
     * flesh. Each is held at work (standing still, as its goals would have it while they work) for 30 s, each to within 10%.
     */
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void bloodAtWorkFollowsFitness(GameTestHelper helper) {
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionBuild horse = horse();
        MinionEntity fit = minion(helper, new BlockPos(2, 2, 3), horse, maker);
        MinionEntity poor = minion(helper, new BlockPos(6, 2, 3), armed(ref("zombie", "head")), maker);
        MinionEntity brass = minion(helper, new BlockPos(4, 2, 7), new MinionBuild(true, horse.torso(), horse.parts(), horse.sheathed()), maker);
        MinionEntity[] couriers = {fit, poor, brass};
        float[] expected = {12.5F, 50.0F, 0.0F};
        float[] from = new float[couriers.length];
        for (int i = 0; i < couriers.length; i++) {
            MinionEntity courier = couriers[i];
            courier.setNoAi(true);
            if (!courier.setTask(MinionTask.COURIER)) {
                helper.fail("Anything can carry");
                return;
            }
            courier.setWorking(true);
            from[i] = courier.power();
        }
        expected[2] = MinionFitness.workingDrain(brass.fitness(MinionTask.COURIER)) * MinionEntity.BRASS_DRAIN;
        if (fit.fitness(MinionTask.COURIER) < 2.0F || poor.fitness(MinionTask.COURIER) > 0.5F || expected[2] > 25.0F * MinionEntity.BRASS_DRAIN) {
            helper.fail("The horse should courier at 200%, the zombie at no more than 50%, and the brass horse better than 100%: "
                    + fit.fitness(MinionTask.COURIER) + ", " + poor.fitness(MinionTask.COURIER) + ", " + brass.fitness(MinionTask.COURIER));
            return;
        }
        helper.runAfterDelay(600, () -> {
            StringBuilder used = new StringBuilder();
            boolean right = true;
            for (int i = 0; i < couriers.length; i++) {
                // mB a minute, over the half minute
                float perMinute = (from[i] - couriers[i].power()) * 2.0F / com.avicagan.bloodandbones.config.BBServerConfig.powerDrain();
                used.append(perMinute).append(i + 1 < couriers.length ? ", " : "");
                right &= Math.abs(perMinute - expected[i]) <= expected[i] * 0.1F;
            }
            if (!right) {
                helper.fail("At work they should use 12.5, 50 and " + expected[2] + " mB a minute: " + used);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Blows land more often with more arms that strike (spec 6.4, the strike rate its Blow counts; docs/NEXT.md 1.10, stage
     * C): an all-zombie guard strikes the husk by it every second, as vanilla's melee goal lands blows; a spider's torso with
     * eight zombie arms 60% more often, every 13 ticks. Each keeps to its side of a glass wall with its husk, which has
     * health enough to stand the blows.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void moreArmsStrikeMoreOften(GameTestHelper helper) {
        pen(helper);
        for (int z = 1; z <= 9; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.GLASS);
            }
        }
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity two = minion(helper, new BlockPos(2, 2, 3), armed(ref("zombie", "head")), maker);
        MinionEntity eight = minion(helper, new BlockPos(8, 2, 3), spiderOfArms(ref("zombie", "head")), maker);
        Husk[] husks = new Husk[2];
        List<List<Integer>> blows = List.of(new ArrayList<>(), new ArrayList<>());
        int[] lastHurt = {-1, -1};
        for (int i = 0; i < 2; i++) {
            Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(i == 0 ? 2 : 8, 2, 5));
            husk.setNoAi(true);
            husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
            husk.setHealth(1000.0F);
            husks[i] = husk;
        }
        for (MinionEntity guard : new MinionEntity[]{two, eight}) {
            if (!guard.setTask(MinionTask.GUARD, MinionTask.Anchor.HOME, 3)) {
                helper.fail("Both should guard");
                return;
            }
        }
        helper.onEachTick(() -> {
            for (int i = 0; i < 2; i++) {
                int hurt = husks[i].getLastHurtByMobTimestamp();
                if (hurt != lastHurt[i] && husks[i].getLastHurtByMob() instanceof MinionEntity) {
                    lastHurt[i] = hurt;
                    blows.get(i).add((int) helper.getTick());
                }
            }
        });
        helper.runAfterDelay(300, () -> {
            int slow = medianGap(blows.get(0));
            int quick = medianGap(blows.get(1));
            if (slow != 20 || quick != 13 || blows.get(1).size() <= blows.get(0).size()) {
                helper.fail("Two arms should strike every 20 ticks and eight every 13: " + slow + " (" + blows.get(0).size() + " blows), " + quick + " ("
                        + blows.get(1).size() + " blows)");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * A cleric's head (a medic), handed two splash potions of healing, throws one at its hurt maker, and one at a hurt
     * villager, whom it heals. (The stand-in maker is not in the world, so no splash reaches them; the villager shows the
     * healing lands.)
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void medicThrowsHealingAtHurtMaker(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(5, 2, 5));
        maker.setHealth(8.0F);
        MinionBuild build = MinionBuild.of(ref("villager", "body")).with("head", villagerHead("cleric")).with("arms", ref("villager", "arms"))
                .with("right_leg", ref("villager", "right_leg")).with("left_leg", ref("villager", "left_leg"));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), build, maker);
        if (!minion.setTask(MinionTask.MEDIC)) {
            helper.fail("A cleric's head should take medic: " + minion.task());
            return;
        }
        // its maker hands it the potions as anything is handed over: healing goes in with what it carries, its hand left empty
        give(minion, maker, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
        give(minion, maker, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
        if (minion.inventory.countItem(Items.SPLASH_POTION) != 2 || !minion.getMainHandItem().isEmpty()) {
            helper.fail("Both healing potions should go in with what it carries: " + minion.inventory.countItem(Items.SPLASH_POTION) + " carried, holding "
                    + minion.getMainHandItem());
            return;
        }
        boolean[] atMaker = {false};
        List<ThrownPotion> seen = new ArrayList<>();
        Villager[] villager = {null};
        AABB area = area(helper);
        helper.onEachTick(() -> {
            for (ThrownPotion potion : level.getEntitiesOfClass(ThrownPotion.class, area, p -> p.getOwner() == minion && !seen.contains(p))) {
                seen.add(potion);
                Vec3 to = maker.position().subtract(potion.position());
                Vec3 flying = potion.getDeltaMovement();
                if (!atMaker[0] && to.x * flying.x + to.z * flying.z > 0.8 * Math.sqrt(to.x * to.x + to.z * to.z) * Math.sqrt(flying.x * flying.x + flying.z * flying.z)) {
                    atMaker[0] = true;
                    // the maker is seen to; now a villager in the pen is hurt
                    maker.setHealth(maker.getMaxHealth());
                    villager[0] = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 2));
                    villager[0].setNoAi(true);
                    villager[0].setHealth(10.0F);
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(atMaker[0], "no healing potion was thrown at its hurt maker yet (" + seen.size() + " thrown)");
            helper.assertTrue(villager[0] != null && villager[0].getHealth() > 10.0F, "the hurt villager has not been healed yet ("
                    + (villager[0] == null ? "none" : villager[0].getHealth() + " at " + villager[0].position()) + "; " + seen.size() + " thrown, "
                    + minion.inventory.countItem(Items.SPLASH_POTION) + " left, the medic at " + minion.position() + ", " + minion.task() + ")");
            helper.assertTrue(minion.inventory.countItem(Items.SPLASH_POTION) == 0, "both potions should have come out of what it carries");
            villager[0].discard();
        });
    }

    /**
     * A piglin's head (a barterer) takes gold from the chest by home and puts back what a piglin would trade for it; the
     * ingot it is looking over is kept with what it carries meanwhile.
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void bartererTradesGold(GameTestHelper helper) {
        pen(helper);
        helper.setBlock(new BlockPos(2, 2, 5), Blocks.CHEST);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(2, 2, 5));
        chest.setItem(0, new ItemStack(Items.GOLD_INGOT, 3));
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(4, 2, 5), armed(ref("piglin", "head")), maker);
        if (!minion.setTask(MinionTask.BARTERER)) {
            helper.fail("A piglin's head should take bartering: " + minion.task());
            return;
        }
        // while it looks the ingot over, the ingot is in what it carries (saved, folded and dropped with it)
        boolean[] carried = {false};
        helper.onEachTick(() -> carried[0] |= chest.countItem(Items.GOLD_INGOT) == 2 && count(minion, Items.GOLD_INGOT) == 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(carried[0], "the ingot it looks over was never seen in what it carries");
            int gold = chest.countItem(Items.GOLD_INGOT);
            boolean traded = false;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                traded |= !stack.isEmpty() && !stack.is(Items.GOLD_INGOT);
            }
            helper.assertTrue(gold < 3 && traded, "no gold traded yet (" + gold + " gold in the chest)");
        });
    }

    /**
     * A sniffer's head (a digger) sniffs the grass by home and turns up what a sniffer digs, the ground left as it was.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void diggerDigsUp(GameTestHelper helper) {
        pen(helper);
        for (int x = 6; x <= 7; x++) {
            for (int z = 6; z <= 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
            }
        }
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), cowWith(ref("sniffer", "root/bone/body/head")), maker);
        if (!minion.setTask(MinionTask.DIGGER)) {
            helper.fail("A sniffer's head should take digging: " + minion.task());
            return;
        }
        // no minute's wait in a test: the first find comes as soon as it is at the grass
        MinionTasks.hurry(minion);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(minion, Items.TORCHFLOWER_SEEDS) + count(minion, Items.PITCHER_POD) > 0, "nothing dug up yet (at " + minion.position() + ")");
            for (int x = 6; x <= 7; x++) {
                for (int z = 6; z <= 7; z++) {
                    helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(x, 1, z));
                }
            }
        });
    }

    /**
     * A herder (a cow's head) holding wheat walks out to a cow that strayed across its pen and leads it back within 8 of
     * home. The cow is kept from wandering there by itself.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void herderKeepsAnimalsHome(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(1, 2, 1), cowWith(ref("cow", "head")), maker);
        if (!minion.setTask(MinionTask.HERDER) || !statusSays(minion, "bloodandbones.minion.wants.herder")) {
            helper.fail("A cow's head should take herding, and with nothing in its mouth wait for food: " + MinionTasks.status(minion).getString());
            return;
        }
        give(minion, maker, new ItemStack(Items.WHEAT));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(9, 2, 9));
        // left to itself it strolls only about where it stands
        cow.restrictTo(cow.blockPosition(), 1);
        Vec3 home = Vec3.atBottomCenterOf(minion.home());
        helper.succeedWhen(() -> helper.assertTrue(cow.position().distanceTo(home) < minion.reach(),
                "the cow is still " + cow.position().distanceTo(home) + " from home (the herder at " + minion.position() + ")"));
    }

    // ---- a brass minion's filter (slice 8)

    /** The armed zombie body under this head, in brass (a zombie has no hide, so its pieces do for either kind). */
    private static MinionBuild brassArmed(PieceRef head) {
        MinionBuild flesh = armed(head);
        return new MinionBuild(true, flesh.torso(), flesh.parts(), true);
    }

    /** Its maker crouches and uses this on it: into its filter slot. */
    private static void setFilter(MinionEntity minion, Player maker, ItemStack filter) {
        maker.setShiftKeyDown(true);
        maker.setItemInHand(InteractionHand.MAIN_HAND, filter);
        minion.interact(maker, InteractionHand.MAIN_HAND);
        maker.setShiftKeyDown(false);
    }

    /** A brass hunter with a Filter holding a pig's spawn egg hunts the pig and spares the cow standing nearer. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void filteredHunterSparesTheCow(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), brassArmed(ref("wolf", "head/real_head")), maker);
        if (!minion.cybernetic() || !minion.setTask(MinionTask.HUNTER)) {
            helper.fail("A brass wolf's head should take hunting: " + minion.task());
            return;
        }
        setFilter(minion, maker, BrassMinionTests.listFilter(Items.PIG_SPAWN_EGG));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(4, 2, 5));
        net.minecraft.world.entity.animal.Pig pig = helper.spawn(EntityType.PIG, new BlockPos(8, 2, 8));
        helper.succeedWhen(() -> {
            helper.assertTrue(!pig.isAlive(), "the pig is still alive (" + pig.getHealth() + ")");
            helper.assertTrue(cow.isAlive() && cow.getHealth() >= cow.getMaxHealth(), "it should leave the cow alone");
        });
    }

    /**
     * A brass courier with its maker and nothing in its hand fetches whatever its filter passes: an Attribute Filter for food
     * brings its maker the apples, and the stick lying nearer stays where it is.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void filteredCourierFetchesWhatItPasses(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), brassArmed(ref("chicken", "head")), maker);
        if (!minion.setTask(MinionTask.COURIER, MinionTask.Anchor.MAKER, 0)) {
            helper.fail("A chicken's head should take carrying with its maker: " + minion.task());
            return;
        }
        setFilter(minion, maker, BrassMinionTests.attributeFilter(new com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry(
                com.simibubi.create.content.logistics.item.filter.attribute.AllItemAttributeTypes.CONSUMABLE.createAttribute(), false)));
        BlockPos near = helper.absolutePos(new BlockPos(6, 2, 3));
        ItemEntity stick = new ItemEntity(level, near.getX() + 0.5, near.getY() + 0.2, near.getZ() + 0.5, new ItemStack(Items.STICK));
        level.addFreshEntity(stick);
        BlockPos far = helper.absolutePos(new BlockPos(8, 2, 8));
        level.addFreshEntity(new ItemEntity(level, far.getX() + 0.5, far.getY() + 0.2, far.getZ() + 0.5, new ItemStack(Items.APPLE, 3)));
        helper.succeedWhen(() -> {
            helper.assertTrue(maker.getInventory().countItem(Items.APPLE) == 3, "its maker has " + maker.getInventory().countItem(Items.APPLE)
                    + " of the 3 apples (it carries " + count(minion, Items.APPLE) + ")");
            helper.assertTrue(stick.isAlive() && count(minion, Items.STICK) == 0 && maker.getInventory().countItem(Items.STICK) == 0,
                    "it should leave the stick alone");
        });
    }

    // ---- the task screen (docs/NEXT.md 1.3)

    /**
     * The maker's crouching empty hand on the minion awake sends the task screen: one row per task in the list's order, each
     * as {@link MinionFitness} works it out, its task now marked. A stranger, a machine's stand-in (a Deployer's, even one
     * placed by the maker) and anyone on it powered down get none; crouching on it down still folds it up, and a plain click
     * is still its status line.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void taskScreenRowsForMakerOnly(GameTestHelper helper) {
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), armed(villagerHead("farmer")), maker);
        crouchClick(minion, maker);
        if (maker.shown.size() != 1) {
            helper.fail("Its maker's crouching empty hand should open the task screen once: " + maker.shown.size());
            return;
        }
        MinionTaskPayload.Open open = maker.shown.get(0);
        if (open.rows().size() != MinionTask.values().length || open.minion() != minion.getId() || open.task() != minion.task().ordinal() || open.refresh()) {
            helper.fail("The screen should have one row a task, on this minion, marking its task now: " + open.rows().size() + " rows, task " + open.task());
            return;
        }
        for (MinionTask task : MinionTask.values()) {
            MinionTaskPayload.Row row = open.rows().get(task.ordinal());
            MinionFitness.Row worked = MinionFitness.of(PartsData.SERVER, minion.build().orElseThrow(), minion.stats(),
                    minion.fitnessContext(MinionTask.Anchor.HOME), task);
            if (row.task() != task.ordinal() || Math.abs(row.fitness() - worked.fitness()) > 1.0E-4F || row.can() != worked.can()
                    || row.waiting() != worked.waitsFor().isPresent() || row.lines().isEmpty() || row.maxReach() != PartsData.SERVER.task(task).maxReach()) {
                helper.fail("The row for " + task + " should be as its fitness is worked out: " + row.fitness() + " for " + worked.fitness());
                return;
            }
        }
        Maker stranger = new Maker(helper, new BlockPos(4, 2, 5));
        crouchClick(minion, stranger);
        FakePlayer standIn = FakePlayerFactory.get(helper.getLevel(), new GameProfile(maker.getUUID(), "stand-in"));
        standIn.moveTo(maker.position());
        if (!stranger.shown.isEmpty() || MinionTasks.open(minion, standIn).isPresent() || MinionTasks.open(minion, stranger).isPresent()) {
            helper.fail("A stranger and a machine's stand-in should get no task screen");
            return;
        }
        // anyone's plain click (its maker's empty hand would take its helmet off)
        stranger.setShiftKeyDown(false);
        stranger.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        minion.interact(stranger, InteractionHand.MAIN_HAND);
        if (stranger.told.isEmpty() || !names(stranger.told.get(stranger.told.size() - 1), "bloodandbones.minion.status") || !stranger.shown.isEmpty()) {
            helper.fail("A plain click should still be its status line");
            return;
        }
        minion.powerDown();
        for (int i = 0; i < 12; i++) {
            crouchClick(minion, maker);
        }
        if (maker.shown.size() != 1 || !minion.isRemoved() || maker.getInventory().countItem(BBItems.DORMANT_MINION.get()) != 1) {
            helper.fail("On it powered down, no screen, and held there it folds up: " + maker.shown.size() + " screens, removed " + minion.isRemoved());
            return;
        }
        helper.succeed();
    }

    /**
     * From the screen, its maker sets Fisher at home, reaching 6, with Home here where it was led, and it fishes there. A
     * stranger's request, a machine's stand-in's, a task its body cannot do, "with me" for a task done only at home, and a
     * reach past the most are all refused, nothing changed. Its task, anchor, reach and home all survive saving and loading.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void setTaskFromTheScreen(GameTestHelper helper) {
        pen(helper);
        for (int x = 6; x <= 9; x++) {
            for (int z = 6; z <= 9; z++) {
                boolean rim = x == 6 || x == 9 || z == 6 || z == 9;
                helper.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE : Blocks.WATER);
            }
        }
        Maker maker = new Maker(helper, new BlockPos(4, 2, 4));
        MinionEntity minion = minion(helper, new BlockPos(1, 2, 1), armed(villagerHead("fisherman")), maker);
        int fisher = MinionTask.FISHER.ordinal();
        int home = MinionTask.Anchor.HOME.ordinal();
        int withMe = MinionTask.Anchor.MAKER.ordinal();
        Maker stranger = new Maker(helper, new BlockPos(3, 2, 3));
        FakePlayer standIn = FakePlayerFactory.get(helper.getLevel(), new GameProfile(maker.getUUID(), "stand-in"));
        standIn.moveTo(maker.position());
        MinionTask before = minion.task();
        if (MinionTasks.handle(stranger, new MinionTaskPayload.Set(minion.getId(), fisher, home, 6, true))
                || MinionTasks.handle(standIn, new MinionTaskPayload.Set(minion.getId(), fisher, home, 6, true))
                || MinionTasks.handle(maker, new MinionTaskPayload.Set(minion.getId(), MinionTask.SAPPER.ordinal(), home, 0, false))
                || MinionTasks.handle(maker, new MinionTaskPayload.Set(minion.getId(), MinionTask.FARMER.ordinal(), withMe, 0, false))
                || MinionTasks.handle(maker, new MinionTaskPayload.Set(minion.getId(), fisher, home, PartsData.SERVER.task(MinionTask.FISHER).maxReach() + 1, false))
                || MinionTasks.handle(maker, new MinionTaskPayload.Set(minion.getId(), fisher, home, 1, false))
                || minion.task() != before || !minion.home().equals(helper.absolutePos(new BlockPos(1, 2, 1)))) {
            helper.fail("A stranger's, a stand-in's, an impossible, a with-me farming and an out-of-bounds request should all be refused: " + minion.task());
            return;
        }
        // its maker leads it to the pool's side, and sets it fishing there
        BlockPos side = helper.absolutePos(new BlockPos(5, 2, 5));
        minion.moveTo(side.getX() + 0.5, side.getY(), side.getZ() + 0.5);
        maker.shown.clear();
        if (!MinionTasks.handle(maker, new MinionTaskPayload.Set(minion.getId(), fisher, home, 6, true)) || !minion.hasTask(MinionTask.FISHER)
                || minion.anchor() != MinionTask.Anchor.HOME || minion.reach() != 6 || !minion.home().equals(side)) {
            helper.fail("Its maker's request should set Fisher at home, reaching 6, home where it stands: " + minion.task() + " " + minion.reach() + " " + minion.home());
            return;
        }
        if (maker.shown.size() != 1 || !maker.shown.get(0).refresh() || maker.shown.get(0).task() != fisher || maker.shown.get(0).reach() != 6) {
            helper.fail("The screen should be brought up to date after the request");
            return;
        }
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        minion.saveWithoutId(tag);
        MinionEntity loaded = reload(helper, tag);
        if (!loaded.hasTask(MinionTask.FISHER) || loaded.anchor() != MinionTask.Anchor.HOME || loaded.reachSet() != 6 || !loaded.home().equals(side)) {
            helper.fail("Its task, anchor, reach and home should survive saving: " + loaded.task() + " " + loaded.reachSet() + " " + loaded.home());
            return;
        }
        MinionTasks.hurry(minion);
        helper.succeedWhen(() -> {
            int caught = 0;
            for (int i = 0; i < minion.inventory.getContainerSize(); i++) {
                caught += minion.inventory.getItem(i).getCount();
            }
            helper.assertTrue(caught > 0, "the fisher has caught nothing yet (at " + minion.position() + ")");
        });
    }

    /**
     * Old saves (docs/NEXT.md 1.8): a companion and a bodyguard load as Guard with their maker, a guard as Guard at home, a
     * sentry as Sentry at its post, a scavenger as a Courier at home keeping what it holds as its sample, any other job as
     * the same task at home, and a job no task is named after as Idle at home; homes are kept. A body that could not guard
     * (no arm, no head) loads as Idle with its maker. Through a Dormant Minion item a bodyguard converts as it is set down.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oldJobConvertsOnLoad(GameTestHelper helper) {
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), armed(ref("zombie", "head")), maker);
        give(minion, maker, new ItemStack(Items.AMETHYST_SHARD));
        BlockPos elsewhere = helper.absolutePos(new BlockPos(8, 2, 1));
        minion.setHome(elsewhere);
        net.minecraft.nbt.CompoundTag saved = new net.minecraft.nbt.CompoundTag();
        minion.saveWithoutId(saved);
        saved.remove("Task");
        saved.remove("Anchor");
        saved.remove("Reach");
        Object[][] table = {
                {"bloodandbones:companion", MinionTask.GUARD, MinionTask.Anchor.MAKER}, {"bloodandbones:bodyguard", MinionTask.GUARD, MinionTask.Anchor.MAKER},
                {"bloodandbones:guard", MinionTask.GUARD, MinionTask.Anchor.HOME}, {"bloodandbones:sentry", MinionTask.SENTRY, MinionTask.Anchor.HOME},
                {"bloodandbones:scavenger", MinionTask.COURIER, MinionTask.Anchor.HOME}, {"bloodandbones:farmer", MinionTask.FARMER, MinionTask.Anchor.HOME},
                {"bloodandbones:hauler", MinionTask.HAULER, MinionTask.Anchor.HOME}, {"bloodandbones:no_such_job", MinionTask.IDLE, MinionTask.Anchor.HOME}};
        for (Object[] row : table) {
            net.minecraft.nbt.CompoundTag tag = saved.copy();
            tag.putString("Job", (String) row[0]);
            MinionEntity loaded = reload(helper, tag);
            if (loaded.task() != row[1] || loaded.anchor() != row[2] || loaded.reachSet() != 0 || !loaded.home().equals(elsewhere)
                    || !loaded.getMainHandItem().is(Items.AMETHYST_SHARD)) {
                helper.fail("An old " + row[0] + " should load as " + row[1] + " " + row[2] + ", its home and what it holds kept: " + loaded.task() + " "
                        + loaded.anchor() + " " + loaded.home());
                return;
            }
        }
        // a body with nothing to fight with, saved keeping company
        MinionEntity torso = minion(helper, new BlockPos(7, 2, 7), MinionBuild.of(ref("cow", "body")), maker);
        net.minecraft.nbt.CompoundTag company = new net.minecraft.nbt.CompoundTag();
        torso.saveWithoutId(company);
        company.remove("Task");
        company.putString("Job", "bloodandbones:companion");
        MinionEntity idle = reload(helper, company);
        if (!idle.hasTask(MinionTask.IDLE) || idle.anchor() != MinionTask.Anchor.MAKER) {
            helper.fail("A companion with nothing to fight with should load as Idle with its maker: " + idle.task() + " " + idle.anchor());
            return;
        }
        torso.discard();
        // folded before tasks, and set down now
        minion.powerDown();
        DormantMinionItem.fold(minion, maker);
        ItemStack folded = maker.getInventory().items.stream().filter(st -> st.is(BBItems.DORMANT_MINION.get())).findFirst().orElseThrow();
        net.minecraft.nbt.CompoundTag inside = folded.get(BBDataComponents.DORMANT.get()).copyTag();
        inside.remove("Task");
        inside.remove("Anchor");
        inside.remove("Reach");
        inside.putString("Job", "bloodandbones:bodyguard");
        folded.set(BBDataComponents.DORMANT.get(), net.minecraft.world.item.component.CustomData.of(inside));
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 7));
        folded.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), maker, InteractionHand.MAIN_HAND, folded,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        MinionEntity back = helper.getLevel().getEntitiesOfClass(MinionEntity.class, new AABB(floor).inflate(1.5)).stream().findFirst().orElse(null);
        if (back == null || !back.hasTask(MinionTask.GUARD) || back.anchor() != MinionTask.Anchor.MAKER) {
            helper.fail("A folded bodyguard should unfold as Guard with its maker: " + (back == null ? "none" : back.task() + " " + back.anchor()));
            return;
        }
        back.discard();
        helper.succeed();
    }

    /**
     * It wakes to its fittest task that waits on nothing it lacks, ties to the list's order, and says so (docs/NEXT.md 1.3):
     * a villager-headed build is a surgeon at 200%, "Woke as a Surgeon (200%)"; a wolf's head never wakes to Hunter (it has
     * the knack) and a body with a creeper's sac never to Sapper; a cow on rabbit legs, best at herding, waits for food, so
     * it wakes to the task it is next best at.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wakesToItsFittestTask(GameTestHelper helper) {
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionEntity surgeon = minion(helper, new BlockPos(5, 2, 5), armed(ref("villager", "head")), maker);
        Component said = maker.told.isEmpty() ? Component.empty() : maker.told.get(maker.told.size() - 1);
        if (!surgeon.hasTask(MinionTask.SURGEON) || !(said.getContents() instanceof TranslatableContents woke) || !woke.getKey().equals("bloodandbones.minion.woke")
                || !(woke.getArgs()[0] instanceof Component name && names(name, MinionTask.SURGEON.nameKey())) || !"200%".equals(woke.getArgs()[1])) {
            helper.fail("A villager's head with hands should wake as a Surgeon, and say so at 200%: " + surgeon.task() + " " + said.getString());
            return;
        }
        MinionEntity wolf = minion(helper, new BlockPos(7, 2, 5), armed(ref("wolf", "head/real_head")), maker);
        MinionEntity sapper = minion(helper, new BlockPos(7, 2, 7), cowWith(ref("creeper", "head")).withOrgan(java.util.Optional.of(
                new com.avicagan.bloodandbones.parts.CarcassArmour.Organ(BloodAndBones.asResource("powder_sac"), mob("creeper"), false))), maker);
        if (wolf.hasTask(MinionTask.HUNTER) || !wolf.row(MinionTask.HUNTER, MinionTask.Anchor.HOME).can() || sapper.hasTask(MinionTask.SAPPER)
                || !sapper.row(MinionTask.SAPPER, MinionTask.Anchor.HOME).can()) {
            helper.fail("A wolf's head should never wake to hunting, nor a creeper's sac to sapping: " + wolf.task() + ", " + sapper.task());
            return;
        }
        MinionEntity cow = minion(helper, new BlockPos(3, 2, 7), cowOnRabbitLegs(), maker);
        MinionTask woken = cow.task();
        float herder = cow.row(MinionTask.HERDER, MinionTask.Anchor.HOME).fitness();
        for (MinionTask task : MinionTask.values()) {
            MinionFitness.Row row = cow.row(task, MinionTask.Anchor.HOME);
            if (MinionTasks.wakes(task) && row.ready() && row.fitness() > cow.row(woken, MinionTask.Anchor.HOME).fitness() + 1.0E-4F) {
                helper.fail("It should wake to its fittest ready task, not " + woken + " below " + task);
                return;
            }
        }
        if (woken == MinionTask.HERDER || !(herder > cow.row(woken, MinionTask.Anchor.HOME).fitness())) {
            helper.fail("A cow on rabbit legs, best at herding but with no food, should wake to its next best: " + woken);
            return;
        }
        surgeon.discard();
        wolf.discard();
        sapper.discard();
        cow.discard();
        helper.succeed();
    }

    /**
     * A task changes only when its maker sets it, but for a data reload that leaves its body unable to do it (docs/NEXT.md
     * 1.3): a made-up mob's hands make it a surgeon; its data changed to say they are wings, it is Idle at home, and its
     * status line says why. Taking its tool away, by contrast, only makes it wait.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void reloadTakesAnImpossibleTask(GameTestHelper helper) {
        ResourceLocation id = TestTraits.mob(helper, "reload_hands", "{\"archetype\": \"bloodandbones:biped\"}");
        com.avicagan.bloodandbones.carcass.rig.Rig zombie = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(mob("zombie")).orElseThrow();
        com.avicagan.bloodandbones.carcass.rig.RigManager.addTestRig(new com.avicagan.bloodandbones.carcass.rig.Rig(id, zombie.model(), zombie.layer(),
                zombie.texture(), zombie.variantNames(), zombie.passes(), zombie.scale(), zombie.weight(), zombie.rotTime(), zombie.bones(), zombie.baby()));
        PartsData.SERVER.invalidate();
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionBuild build = MinionBuild.of(new PieceRef(id, "body", ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png"), List.of(), 1.0F, false, Map.of(), false));
        for (String part : new String[]{"head", "right_arm", "left_arm", "right_leg", "left_leg"}) {
            build = build.with(part, new PieceRef(id, part, ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png"), List.of(), 1.0F, false, Map.of(), false));
        }
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), build, maker);
        minion.setNoAi(true);
        if (!minion.setTask(MinionTask.SURGEON)) {
            helper.fail("Its hands should make it a surgeon: " + minion.row(MinionTask.SURGEON, MinionTask.Anchor.HOME).cannot());
            return;
        }
        // the data changes under it: its arms are wings now
        TestTraits.mob(helper, "reload_hands", "{\"archetype\": \"bloodandbones:biped\", \"parts\": {\"arm\": {\"minion\": {\"grip\": \"wing\"}}}}");
        helper.succeedWhen(() -> {
            helper.assertTrue(minion.hasTask(MinionTask.IDLE) && minion.anchor() == MinionTask.Anchor.HOME, "it should be Idle at home: " + minion.task());
            helper.assertTrue(statusSays(minion, "bloodandbones.minion.lost") && statusSays(minion, "bloodandbones.minion.cannot.hand"),
                    "its status line should say why: " + MinionTasks.status(minion).getString());
            minion.discard();
        });
    }

    /** The design's cow on four rabbit legs, with a cow's head (spec 6.5). */
    private static MinionBuild cowOnRabbitLegs() {
        return MinionBuild.of(ref("cow", "body")).with("head", ref("cow", "head")).with("right_front_leg", ref("rabbit", "right_front_leg"))
                .with("left_front_leg", ref("rabbit", "left_front_leg")).with("right_hind_leg", ref("rabbit", "right_haunch"))
                .with("left_hind_leg", ref("rabbit", "left_haunch"));
    }

    /**
     * Every word of the tasks, the screen (what each task's fitness makes of its work too) and the status line, the surgery
     * screen's surgeon and stump prices, what a part brings to a minion's tasks (JEI and a piece's tooltip) and the fitness
     * command, reads right in bloodless mode (rule 4): its own bloodless wording where it has one (the butcher a
     * Dismantler), else the usual rewording; none of it says blood, carcass, butcher, flesh, organ, gore or minion.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void taskWordsReadBloodless(GameTestHelper helper) {
        java.util.regex.Pattern bloody = java.util.regex.Pattern.compile(
                "(?i)(?<![a-z])(carcass(es)?|blood|bleed\\w*|butcher\\w*|flesh|organs?|gore|guts?|minions?)(?![a-z])");
        List<String> prefixes = List.of("task.", "screen.", "fit.", "where.", "idle.", "factor", "stat.", "value.", "rule.", "at_work", "woke", "lost",
                "wants.", "cannot.", "tool.", "with_tool", "grip.", "part", "knack", "disposition", "doing", "status", "source.", "frame_stats", "lever.",
                "facts.");
        // the surgery screen's words for the surgeon and a stump's price (docs/NEXT.md 1.5)
        List<String> surgery = List.of("surgeon", "no_surgeon", "bucket", "needs_blood", "state.ragged");
        int checked = 0;
        try (var in = BloodAndBones.class.getResourceAsStream("/assets/bloodandbones/lang/en_us.json")) {
            var json = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            for (String key : json.keySet()) {
                if (!(key.startsWith("bloodandbones.minion.") && prefixes.stream().anyMatch(p -> key.startsWith("bloodandbones.minion." + p))
                        || surgery.stream().anyMatch(p -> key.startsWith("bloodandbones.surgery." + p)) || key.startsWith("bloodandbones.command.minion."))) {
                    continue;
                }
                String reads = json.has("bloodless." + key) ? json.get("bloodless." + key).getAsString()
                        : com.avicagan.bloodandbones.config.BloodlessWords.soften(json.get(key).getAsString());
                if (bloody.matcher(reads).find()) {
                    helper.fail("In bloodless mode " + key + " still reads \"" + reads + "\"");
                    return;
                }
                checked++;
            }
            if (checked < 100 || !"Dismantler".equals(json.get("bloodless.bloodandbones.minion.task.butcher").getAsString())) {
                helper.fail("The task words should all have been read, the butcher a Dismantler: " + checked);
                return;
            }
        } catch (Exception e) {
            helper.fail("Could not read the language file: " + e);
            return;
        }
        helper.succeed();
    }

    // ---- with me (docs/NEXT.md 1.1)

    /**
     * A guard with its maker is a tamed wolf: it follows them, and goes for what hurts them and for what they hit. Idle with
     * its maker follows too, and only fights back: what hurts its maker it leaves be.
     */
    @GameTest(template = "empty", timeoutTicks = 800)
    public static void guardWithMeIsAWolf(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionEntity guard = minion(helper, new BlockPos(2, 2, 3), armed(ref("zombie", "head")), maker);
        MinionEntity idle = minion(helper, new BlockPos(3, 2, 2), armed(ref("zombie", "head")), maker);
        if (!guard.setTask(MinionTask.GUARD, MinionTask.Anchor.MAKER, 0) || !idle.setTask(MinionTask.IDLE, MinionTask.Anchor.MAKER, 0)) {
            helper.fail("A zombie should take guarding and Idle with its maker");
            return;
        }
        Husk[] husks = new Husk[3];
        helper.startSequence()
                // its maker walks across the pen: both follow
                .thenExecute(() -> maker.stand(helper, new Vec3(8.5, 2.0, 8.5)))
                .thenWaitUntil(() -> {
                    helper.assertTrue(guard.distanceTo(maker) < 5.0F && idle.distanceTo(maker) < 5.0F, "they have not followed their maker yet: "
                            + guard.distanceTo(maker) + ", " + idle.distanceTo(maker));
                })
                // something hurts its maker
                .thenExecute(() -> {
                    husks[0] = helper.spawn(EntityType.HUSK, new BlockPos(5, 2, 8));
                    husks[0].setNoAi(true);
                    maker.tickCount += 200;
                    maker.setLastHurtByMob(husks[0]);
                })
                .thenWaitUntil(() -> helper.assertTrue(husks[0].getHealth() < husks[0].getMaxHealth() || !husks[0].isAlive(),
                        "the guard has not gone for what hurt its maker (its target " + guard.getTarget() + ")"))
                .thenExecute(() -> {
                    husks[0].discard();
                    guard.setTarget(null);
                    // its maker hits something
                    husks[1] = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 5));
                    husks[1].setNoAi(true);
                    maker.tickCount += 200;
                    maker.setLastHurtMob(husks[1]);
                })
                .thenWaitUntil(() -> helper.assertTrue(husks[1].getHealth() < husks[1].getMaxHealth() || !husks[1].isAlive(),
                        "the guard has not gone for what its maker hit (its target " + guard.getTarget() + ")"))
                .thenExecute(() -> {
                    husks[1].discard();
                    guard.discard();
                    guard.setTarget(null);
                    // what hurts its maker now, the idle one leaves be
                    husks[2] = helper.spawn(EntityType.HUSK, new BlockPos(6, 2, 7));
                    husks[2].setNoAi(true);
                    maker.tickCount += 200;
                    maker.setLastHurtByMob(husks[2]);
                })
                .thenIdle(80)
                .thenExecute(() -> {
                    boolean spared = husks[2].isAlive() && husks[2].getHealth() >= husks[2].getMaxHealth() && idle.getTarget() == null;
                    husks[2].discard();
                    idle.discard();
                    helper.assertTrue(spared, "Idle with its maker should only fight back, not go for what hurt its maker");
                })
                .thenSucceed();
    }

    /** A courier with its maker picks up round them and hands the items over: holding an apple, it takes only apples. */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void courierWithMeFillsTheMakersHands(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(5, 2, 5));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), armed(ref("chicken", "head")), maker);
        give(minion, maker, new ItemStack(Items.APPLE));
        if (!minion.setTask(MinionTask.COURIER, MinionTask.Anchor.MAKER, 0) || !minion.getMainHandItem().is(Items.APPLE)) {
            helper.fail("A chicken's head should hold its apple and take carrying with its maker: " + minion.task());
            return;
        }
        BlockPos far = helper.absolutePos(new BlockPos(8, 2, 8));
        level.addFreshEntity(new ItemEntity(level, far.getX() + 0.5, far.getY() + 0.2, far.getZ() + 0.5, new ItemStack(Items.APPLE, 3)));
        BlockPos other = helper.absolutePos(new BlockPos(8, 2, 3));
        ItemEntity stick = new ItemEntity(level, other.getX() + 0.5, other.getY() + 0.2, other.getZ() + 0.5, new ItemStack(Items.STICK));
        level.addFreshEntity(stick);
        helper.succeedWhen(() -> {
            helper.assertTrue(maker.getInventory().countItem(Items.APPLE) == 3, "its maker has " + maker.getInventory().countItem(Items.APPLE)
                    + " of the 3 apples (it carries " + count(minion, Items.APPLE) + ")");
            helper.assertTrue(stick.isAlive() && count(minion, Items.STICK) == 0 && maker.getInventory().countItem(Items.STICK) == 0,
                    "it should leave the stick alone");
            helper.assertTrue(minion.getMainHandItem().is(Items.APPLE), "it should still hold its own apple");
        });
    }

    /**
     * A hunter with its maker hunts beside them, not by home: with its reach set to 4, the cow by its maker is killed and the
     * one by home is let be; with a Meat Hook in hand the kill is an intact carcass.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void hunterWithMeHuntsBesideTheMaker(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Set<UUID> before = new HashSet<>();
        CarcassSavedData.get(level).all().forEach(c -> before.add(c.id));
        Maker maker = new Maker(helper, new BlockPos(8, 2, 8));
        MinionEntity minion = minion(helper, new BlockPos(1, 2, 1), armed(ref("wolf", "head/real_head")), maker);
        give(minion, maker, new ItemStack(BBItems.MEAT_HOOK.get()));
        if (!minion.setTask(MinionTask.HUNTER, MinionTask.Anchor.MAKER, 4)) {
            helper.fail("A wolf's head should take hunting with its maker, reaching 4");
            return;
        }
        Cow byHome = helper.spawn(EntityType.COW, new BlockPos(2, 2, 3));
        byHome.setNoAi(true);
        Cow byMaker = helper.spawn(EntityType.COW, new BlockPos(8, 2, 5));
        byMaker.setNoAi(true);
        AABB area = area(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(!byMaker.isAlive() || byMaker.isRemoved(), "the cow by its maker is still alive (" + byMaker.getHealth() + ", the hunter at "
                    + helper.relativeVec(minion.position()) + ")");
            helper.assertTrue(byHome.isAlive() && byHome.getHealth() >= byHome.getMaxHealth(), "it should let the cow by home be");
            boolean carcass = CarcassSavedData.get(level).all().stream().anyMatch(c -> !before.contains(c.id) && c.entity.equals(mob("cow"))
                    && inside(area, CarcassAssembler.boneWorldPosition(level, c, c.rootBone)));
            helper.assertTrue(carcass, "the kill should leave a cow carcass");
            byHome.discard();
        });
    }

    /** A medic with its maker follows them as they walk off, and throws its healing at them hurt on the way. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void medicWithMeHealsOnTheMove(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionBuild build = MinionBuild.of(ref("villager", "body")).with("head", villagerHead("cleric")).with("arms", ref("villager", "arms"))
                .with("right_leg", ref("villager", "right_leg")).with("left_leg", ref("villager", "left_leg"));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 3), build, maker);
        if (!minion.setTask(MinionTask.MEDIC, MinionTask.Anchor.MAKER, 0) || !statusSays(minion, "bloodandbones.minion.wants.medic")) {
            helper.fail("A cleric's head should take medic with its maker, and wait for potions: " + MinionTasks.status(minion).getString());
            return;
        }
        give(minion, maker, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
        give(minion, maker, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HEALING));
        Vec3 start = minion.position();
        // its maker walks to the far corner, and is hurt once the medic has followed it there: hurt sooner, the medic stops
        // to throw where it is, within its following distance, and has no need to come nearer
        maker.stand(helper, new Vec3(8.5, 2.0, 8.5));
        boolean[] thrown = {false};
        List<ThrownPotion> seen = new ArrayList<>();
        AABB area = area(helper);
        helper.onEachTick(() -> {
            if (maker.getHealth() >= maker.getMaxHealth() && minion.position().distanceTo(start) > 3.0) {
                maker.setHealth(8.0F);
            }
            for (ThrownPotion potion : level.getEntitiesOfClass(ThrownPotion.class, area, p -> p.getOwner() == minion && !seen.contains(p))) {
                seen.add(potion);
                Vec3 to = maker.position().subtract(potion.position());
                Vec3 flying = potion.getDeltaMovement();
                thrown[0] |= to.x * flying.x + to.z * flying.z > 0.8 * Math.sqrt(to.x * to.x + to.z * to.z) * Math.sqrt(flying.x * flying.x + flying.z * flying.z);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(minion.position().distanceTo(start) > 3.0, "the medic has not followed its maker (at " + helper.relativeVec(minion.position()) + ")");
            helper.assertTrue(thrown[0], "no healing potion was thrown at its hurt maker yet (" + seen.size() + " thrown)");
        });
    }

    // ---- fighting (docs/NEXT.md 1.1)

    /**
     * A guard with no head feels its way (docs/NEXT.md 1.1). For three seconds it leaves be a husk 1.8 blocks off (within the
     * 2 blocks a headless body notices things in, but not against it) and one 4 blocks off: it takes no target and stands
     * where it was. Then a third husk is put against it: it strikes that one, and for two seconds more still goes for
     * neither of the others and never walks off.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void headlessFightsOnlyWhatTouchesIt(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionBuild headless = MinionBuild.of(ref("zombie", "body")).with("right_arm", ref("zombie", "right_arm")).with("left_arm", ref("zombie", "left_arm"))
                .with("right_leg", ref("zombie", "right_leg")).with("left_leg", ref("zombie", "left_leg"));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 5), headless, maker);
        if (!minion.stats().mindless() || !minion.setTask(MinionTask.GUARD) || minion.stats().sight() != MinionStats.MINDLESS_SIGHT) {
            helper.fail("A zombie's arms with no head should still take guarding, noticing things 2 blocks off");
            return;
        }
        Vec3 post = minion.position();
        Husk close = helper.spawn(EntityType.HUSK, new Vec3(5.3, 2.0, 5.5));
        close.setNoAi(true);
        Husk far = helper.spawn(EntityType.HUSK, new BlockPos(7, 2, 5));
        far.setNoAi(true);
        Husk[] near = new Husk[1];
        double[] strayed = {0.0};
        String[] wrong = {null};
        helper.onEachTick(() -> {
            strayed[0] = Math.max(strayed[0], minion.position().distanceTo(post));
            if (wrong[0] == null && (close.getHealth() < close.getMaxHealth() || far.getHealth() < far.getMaxHealth())) {
                wrong[0] = "it went for a husk not against it: " + close.getHealth() + ", " + far.getHealth();
            }
            if (wrong[0] == null && near[0] == null && minion.getTarget() != null) {
                wrong[0] = "with nothing against it, it took a target: " + minion.getTarget() + " at " + minion.distanceTo(minion.getTarget());
            }
        });
        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(wrong[0] == null, String.valueOf(wrong[0]));
                    helper.assertTrue(!com.avicagan.bloodandbones.minion.MinionGoals.touches(minion, close), "the husk 1.8 blocks off should not be against it");
                    near[0] = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 5));
                    near[0].setNoAi(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(near[0].getHealth() < near[0].getMaxHealth() || !near[0].isAlive(),
                        "it has not struck the husk against it yet (target " + minion.getTarget() + ")"))
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(wrong[0] == null, String.valueOf(wrong[0]));
                    helper.assertTrue(strayed[0] < 1.5, "it should not walk off after anything: " + strayed[0]);
                    close.discard();
                    far.discard();
                    near[0].discard();
                })
                .thenSucceed();
    }

    /**
     * A sentry with no bow holds its post (docs/NEXT.md 1.1). For three seconds only a husk 5 blocks off is in its pen: it
     * takes no target (it could not strike one out of its reach without leaving its post) and does not stir. Then a husk
     * comes within its reach: it strikes that one, and for a second more still never moves toward the one further off.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sentryWithNoBowHoldsItsPost(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 5), armed(ref("pillager", "head")), maker);
        if (minion.hasRangedAttack() || !minion.setTask(MinionTask.SENTRY)) {
            helper.fail("A pillager's head with no bow should take up sentry");
            return;
        }
        Vec3 post = minion.position();
        double[] strayed = {0.0};
        Husk far = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 5));
        far.setNoAi(true);
        Husk[] near = new Husk[1];
        String[] wrong = {null};
        helper.onEachTick(() -> {
            strayed[0] = Math.max(strayed[0], minion.position().distanceTo(post));
            if (wrong[0] == null && minion.getTarget() == far) {
                wrong[0] = "it took the husk out of its reach for a target";
            }
        });
        helper.startSequence()
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(wrong[0] == null && minion.getTarget() == null, wrong[0] + ", its target " + minion.getTarget());
                    helper.assertTrue(strayed[0] < 0.5, "the sentry left its post: " + strayed[0]);
                    near[0] = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 5));
                    near[0].setNoAi(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(near[0].getHealth() < near[0].getMaxHealth() || !near[0].isAlive(),
                        "the sentry has not struck the husk within its reach yet"))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(wrong[0] == null, String.valueOf(wrong[0]));
                    helper.assertTrue(strayed[0] < 0.5, "the sentry left its post: " + strayed[0]);
                    helper.assertTrue(far.isAlive() && far.getHealth() >= far.getMaxHealth(), "it should never go for the husk out of its reach");
                    far.discard();
                    near[0].discard();
                })
                .thenSucceed();
    }

    // ---- the Tender and the Butcher's Table (docs/NEXT.md 1.1; stage E)

    private static ItemStack bloodBucket() {
        return new ItemStack(com.avicagan.bloodandbones.registry.BBFluids.BLOOD.getBucket().get());
    }

    /** How many of this item the containers here hold between them. */
    private static int inStores(GameTestHelper helper, net.minecraft.world.item.Item item, BlockPos... at) {
        int n = 0;
        for (BlockPos pos : at) {
            if (helper.getBlockEntity(pos) instanceof net.minecraft.world.Container container) {
                n += container.countItem(item);
            }
        }
        return n;
    }

    /** Everything it carries, counted. */
    private static int carried(MinionEntity minion) {
        int n = 0;
        for (int i = 0; i < minion.inventory.getContainerSize(); i++) {
            n += minion.inventory.getItem(i).getCount();
        }
        return n;
    }

    /**
     * A Tender keeps the trough and the cradle by home stocked (docs/NEXT.md 1.1): the two buckets of blood in the chest go
     * into the trough, then it fills the empties at a Create Fluid Tank of blood and pours those in too; the three canisters
     * and five brass sheets in the barrel go into the cradle, and the two empty canisters in the cradle come back out. Nothing
     * is made or lost on the way: four buckets and five canisters from first to last, the buckets' 2000 mB and the tank's 2000
     * all in the trough, the empties back in the containers, and nothing on the ground or left in its hands. The tank is
     * eight blocks, two by two by two: its look finds it once, at its block nearest the Tender.
     */
    @GameTest(template = "empty", timeoutTicks = 2400)
    public static void tenderFillsTroughsAndCradles(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        BlockPos chestAt = new BlockPos(2, 2, 8);
        BlockPos barrelAt = new BlockPos(8, 2, 8);
        BlockPos troughAt = new BlockPos(8, 2, 2);
        BlockPos tankAt = new BlockPos(2, 2, 2);
        BlockPos cradleAt = new BlockPos(5, 2, 8);
        helper.setBlock(chestAt, Blocks.CHEST);
        helper.setBlock(barrelAt, Blocks.BARREL);
        helper.setBlock(troughAt, BBBlocks.BLOOD_TROUGH.getDefaultState());
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    helper.setBlock(tankAt.offset(dx, dy, dz), com.simibubi.create.AllBlocks.FLUID_TANK.getDefaultState());
                }
            }
        }
        helper.setBlock(cradleAt, BBBlocks.CHARGING_CRADLE.getDefaultState());
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestAt);
        chest.setItem(0, bloodBucket());
        chest.setItem(1, bloodBucket());
        chest.setItem(2, new ItemStack(Items.BUCKET, 2));
        var barrel = (net.minecraft.world.level.block.entity.BarrelBlockEntity) helper.getBlockEntity(barrelAt);
        barrel.setItem(0, new ItemStack(BBItems.SOUL_CANISTER.get(), 3));
        barrel.setItem(1, new ItemStack(com.simibubi.create.AllItems.BRASS_SHEET.get(), 5));
        var cradle = (com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity) helper.getBlockEntity(cradleAt);
        cradle.inventory.setStackInSlot(com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.FULL, new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get()));
        cradle.inventory.setStackInSlot(com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.FULL + 1, new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get()));
        var trough = (com.avicagan.bloodandbones.minion.BloodTroughBlockEntity) helper.getBlockEntity(troughAt);
        BlockPos tankAbs = helper.absolutePos(tankAt);
        Maker maker = new Maker(helper, new BlockPos(4, 2, 5));
        MinionEntity tender = minion(helper, new BlockPos(5, 2, 5), cowWith(ref("cow", "head")), maker);
        helper.runAfterDelay(1, () -> {
            if (helper.getBlockEntity(tankAt) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity corner) {
                com.simibubi.create.api.connectivity.ConnectivityHandler.formMulti(corner);
            }
            var controller = helper.getBlockEntity(tankAt.offset(1, 1, 1)) instanceof com.simibubi.create.content.fluids.tank.FluidTankBlockEntity part
                    ? part.getControllerBE() : null;
            if (controller == null || controller.getWidth() != 2 || controller.getHeight() != 2) {
                helper.fail("The eight tank blocks should make one tank, two by two by two");
                return;
            }
            var tank = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, tankAbs, null);
            if (tank == null || tank.fill(new net.neoforged.neoforge.fluids.FluidStack(com.avicagan.bloodandbones.registry.BBFluids.blood(), 2000),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) != 2000) {
                helper.fail("The tank should take 2000 mB of blood");
                return;
            }
            // one tank, where it is nearest
            List<BlockPos> tanks = com.avicagan.bloodandbones.minion.MinionTender.tanksFound(level, tender);
            if (!tanks.equals(List.of(helper.absolutePos(tankAt.offset(1, 0, 1))))) {
                helper.fail("A Tender's look should find the tank of eight blocks once, at its block nearest it: " + tanks);
            }
        });
        if (!tender.setTask(MinionTask.TENDER)) {
            helper.fail("Anything can be a Tender");
            return;
        }
        AABB area = area(helper);
        helper.succeedWhen(() -> {
            var tank = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, tankAbs, null);
            int inTank = tank == null ? -1 : tank.getFluidInTank(0).getAmount();
            helper.assertTrue(trough.amount() == 4000 && inTank == 0, "the trough has " + trough.amount() + " of 4000 mB, the tank " + inTank + " left");
            helper.assertTrue(cradle.fullCanisters() == 3 && cradle.inventory.getStackInSlot(com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.SHEETS).getCount() == 5,
                    "the cradle has " + cradle.fullCanisters() + " of the 3 canisters and "
                            + cradle.inventory.getStackInSlot(com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.SHEETS).getCount() + " of the 5 sheets");
            for (int i = com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.FULL; i < com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity.SHEETS; i++) {
                helper.assertTrue(cradle.inventory.getStackInSlot(i).isEmpty(), "the cradle's empties should have been taken out");
            }
            helper.assertTrue(carried(tender) == 0, "it should have put back all it carried: " + tender.inventory);
            int blood = inStores(helper, com.avicagan.bloodandbones.registry.BBFluids.BLOOD.getBucket().get(), chestAt, barrelAt);
            int buckets = inStores(helper, Items.BUCKET, chestAt, barrelAt);
            int canisters = inStores(helper, BBItems.SOUL_CANISTER.get(), chestAt, barrelAt);
            int empties = inStores(helper, BBItems.EMPTY_SOUL_CANISTER.get(), chestAt, barrelAt);
            int sheets = inStores(helper, com.simibubi.create.AllItems.BRASS_SHEET.get(), chestAt, barrelAt);
            helper.assertTrue(blood == 0 && buckets == 4 && canisters == 0 && empties == 2 && sheets == 0, "the containers should hold the 4 empty buckets and"
                    + " the 2 empty canisters, and nothing else: " + blood + " of blood, " + buckets + " empty, " + canisters + " canisters, " + empties
                    + " empties, " + sheets + " sheets");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "nothing should be on the ground");
        });
    }

    /**
     * Its maker's minions lying powered down within its reach get up (docs/NEXT.md 1.1): a brass Tender carries the chest's
     * bucket of blood to a fallen flesh minion and its canister to a fallen brass one, and puts the empty bucket and canister
     * back. None of the bucket is spilled: the flesh one holds less than a bucket, a brass Tender drinks no blood, so the rest
     * goes into the trough by home, to the drop. The sharing out itself is checked first: the fallen one, the Tender, then
     * the troughs; with nowhere for the rest, no pour. The Tender is poor at it (32%: it carries 3 slots of 9), so it looks
     * round only every three seconds or so, one errand a look: five errands (a bucket out, the flesh one, a canister out, the
     * brass one, the empties back) with their walks took 12 to 33 s over 38 runs. It has two and a half minutes. (It had 80 s
     * while its looks came half as often as they should, and ran out of them 2 times in 125.)
     */
    @GameTest(template = "empty", timeoutTicks = 3000)
    public static void tenderWakesAFallenMinion(GameTestHelper helper) {
        int[] one = com.avicagan.bloodandbones.minion.MinionTender.share(344, 0, new int[]{4000});
        int[] two = com.avicagan.bloodandbones.minion.MinionTender.share(344, 200, new int[]{300, 4000});
        int[] whole = com.avicagan.bloodandbones.minion.MinionTender.share(2000, 500, new int[0]);
        if (one == null || one[0] != 344 || one[1] != 0 || one[2] != 656 || two == null || two[0] != 344 || two[1] != 200 || two[2] != 300 || two[3] != 156
                || whole == null || whole[0] != 1000 || whole[1] != 0 || com.avicagan.bloodandbones.minion.MinionTender.share(344, 0, new int[]{100}) != null
                || com.avicagan.bloodandbones.minion.MinionTender.share(0, 1000, new int[]{4000}) != null) {
            helper.fail("A bucket is shared out to the fallen one, the Tender, then the troughs, all of it or none");
            return;
        }
        pen(helper);
        ServerLevel level = helper.getLevel();
        BlockPos chestAt = new BlockPos(2, 2, 8);
        BlockPos troughAt = new BlockPos(8, 2, 8);
        helper.setBlock(chestAt, Blocks.CHEST);
        helper.setBlock(troughAt, BBBlocks.BLOOD_TROUGH.getDefaultState());
        ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestAt);
        chest.setItem(0, bloodBucket());
        chest.setItem(1, new ItemStack(BBItems.SOUL_CANISTER.get()));
        var trough = (com.avicagan.bloodandbones.minion.BloodTroughBlockEntity) helper.getBlockEntity(troughAt);
        Maker maker = new Maker(helper, new BlockPos(4, 2, 5));
        MinionEntity flesh = minion(helper, new BlockPos(8, 2, 2), armed(ref("zombie", "head")), maker);
        MinionEntity brass = minion(helper, new BlockPos(2, 2, 2), brassArmed(ref("zombie", "head")), maker);
        MinionEntity tender = minion(helper, new BlockPos(5, 2, 5), brassArmed(ref("chicken", "head")), maker);
        flesh.powerDown();
        brass.powerDown();
        int holds = flesh.stats().reservoir();
        if (!tender.setTask(MinionTask.TENDER) || holds >= 1000) {
            helper.fail("A brass Tender, and a fallen minion holding less than a bucket: " + tender.task() + ", " + holds);
            return;
        }
        AABB area = area(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(!flesh.poweredDown() && flesh.power() >= holds - 5.0F, "the flesh minion has not been given blood yet (" + flesh.power() + " of " + holds + ")");
            helper.assertTrue(!brass.poweredDown() && brass.power() >= MinionStats.CANISTER - 5.0F, "the brass minion has not been given a canister yet");
            helper.assertTrue(trough.amount() == 1000 - holds, "the rest of the bucket should be in the trough: " + trough.amount() + " of " + (1000 - holds));
            helper.assertTrue(chest.countItem(Items.BUCKET) == 1 && chest.countItem(BBItems.EMPTY_SOUL_CANISTER.get()) == 1 && carried(tender) == 0,
                    "the empty bucket and canister should be back in the chest: " + chest.countItem(Items.BUCKET) + ", "
                            + chest.countItem(BBItems.EMPTY_SOUL_CANISTER.get()) + ", carrying " + carried(tender));
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "nothing should be on the ground");
        });
    }

    /** A cow's body as a carried piece, fresh. */
    private static ItemStack cowPiece() {
        ItemStack stack = new ItemStack(BBItems.CARCASS_PIECE.get());
        stack.set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(mob("cow"), "body", ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"),
                List.of(), 1.0F, false, Map.of(), 0.0F, 0.0F, 0.0F, false));
        return stack;
    }

    /**
     * A butcher chops the pieces laid on a Butcher's Table by home with its Cleaver, as a Deployer does (docs/NEXT.md 1.1):
     * one piece, then a second put on through the table's slot as a funnel would. What they come apart into goes into what
     * it carries (a slot for each of the four things a cow's body gives: it empties them into the chest before the second)
     * and on into the chest by home, none on the ground, and the Cleaver comes away bloody. The table is nearer home than the
     * chest, and takes only pieces: the butcher passes it over for the chest.
     */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void butcherChopsAtTheTable(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        BlockPos tableAt = new BlockPos(4, 2, 7);
        BlockPos chestAt = new BlockPos(8, 2, 5);
        helper.setBlock(tableAt, BBBlocks.BUTCHER_TABLE.getDefaultState());
        helper.setBlock(chestAt, Blocks.CHEST);
        var table = (com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity) helper.getBlockEntity(tableAt);
        table.put(cowPiece());
        Maker maker = new Maker(helper, new BlockPos(4, 2, 4));
        MinionEntity butcher = minion(helper, new BlockPos(5, 2, 5), spiderOnFour(villagerHead("butcher")), maker);
        if (!butcher.setTask(MinionTask.BUTCHER) || butcher.slots() < 4) {
            helper.fail("A butcher's head with hands should take butchery, with a slot for each of the four things a cow's body gives: " + butcher.slots());
            return;
        }
        give(butcher, maker, new ItemStack(BBItems.CLEAVER.get()));
        int[] laid = {1};
        helper.onEachTick(() -> {
            if (laid[0] < 2 && table.specimen().isEmpty()) {
                laid[0]++;
                if (!table.inventory.insertItem(0, cowPiece(), false).isEmpty()) {
                    helper.fail("The empty table should take a piece through its slot");
                }
            }
        });
        AABB area = area(helper);
        helper.succeedWhen(() -> {
            ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(chestAt);
            helper.assertTrue(laid[0] == 2 && table.specimen().isEmpty(), "it has not chopped both pieces yet (" + laid[0] + " laid)");
            helper.assertTrue(chest.countItem(Items.BEEF) > 0, "the beef should be in the chest by home (it carries " + count(butcher, Items.BEEF) + ")");
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "what it chopped should never be on the ground");
            helper.assertTrue(butcher.getMainHandItem().get(BBDataComponents.BLOODIED_AT.get()) != null, "its Cleaver should be bloody");
        });
    }

    /**
     * {@code /bloodandbones minion fitness} (docs/NEXT.md 1.4): the minion its maker looks at, and not when they look away;
     * for it, what it is doing, then every task's full breakdown in the screen's order, with the number before it was held
     * where the cap took some off (a cow on rabbit legs herds at 200%, 213% before: pace 1.37 × knack 1.25 × docile 1.25).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fitnessCommandShowsEveryTask(GameTestHelper helper) {
        Maker maker = new Maker(helper, new BlockPos(2, 2, 5));
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), cowOnRabbitLegs(), maker);
        maker.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, minion.position().add(0.0, minion.getBbHeight() / 2.0, 0.0));
        if (com.avicagan.bloodandbones.minion.MinionCommand.lookedAt(maker, com.avicagan.bloodandbones.minion.MinionCommand.REACH) != minion) {
            helper.fail("Its maker is looking right at it");
            return;
        }
        // a player looks where its head turns
        maker.setYRot(maker.getYRot() + 180.0F);
        maker.setYHeadRot(maker.getYRot());
        if (com.avicagan.bloodandbones.minion.MinionCommand.lookedAt(maker, com.avicagan.bloodandbones.minion.MinionCommand.REACH) != null) {
            helper.fail("Looking away, its maker sees no minion");
            return;
        }
        List<Component> lines = com.avicagan.bloodandbones.minion.MinionCommand.fitness(minion);
        if (lines.isEmpty() || !names(lines.get(0), "bloodandbones.command.minion.title")) {
            helper.fail("It should begin with what the minion is doing: " + lines);
            return;
        }
        for (MinionTask task : MinionTask.values()) {
            // each task's first line is its name (Idle's), or its name with its fitness or "Cannot"
            if (lines.stream().noneMatch(line -> line.getContents() instanceof TranslatableContents t && (t.getKey().equals(task.nameKey())
                    || t.getArgs().length > 0 && t.getArgs()[0] instanceof Component name && names(name, task.nameKey())))) {
                helper.fail("Every task has its lines, not " + task);
                return;
            }
        }
        if (lines.stream().noneMatch(line -> names(line, MinionTask.HERDER.nameKey()) && line.getSiblings().stream().anyMatch(sibling -> sibling.getContents()
                instanceof TranslatableContents raw && raw.getKey().equals("bloodandbones.command.minion.raw") && "213%".equals(raw.getArgs()[0])))
                || lines.stream().noneMatch(line -> names(line, "bloodandbones.minion.cannot.hand"))
                || lines.stream().noneMatch(line -> names(line, "bloodandbones.minion.factor"))) {
            helper.fail("Herder should show what the cap took off (213%), Surgeon why it cannot, and the stats each reads: " + lines.size() + " lines");
            return;
        }
        minion.discard();
        helper.succeed();
    }

    // ---- the review of the tasks (docs/ARCHITECTURE-PROPOSAL.md 15.22)

    /**
     * Folded arms never strike, but a head over them bites (docs/NEXT.md 1.7): a whole villager is shown able to guard, by
     * its bite, and set to Guard it bites the husk by home, as its row says. The row and its fight goals agree.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void foldedArmsBiteWithTheHead(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 5), villager(), maker);
        MinionFitness.Row row = minion.row(MinionTask.GUARD, MinionTask.Anchor.HOME);
        if (!row.can() || !minion.stats().fights() || !minion.setTask(MinionTask.GUARD)) {
            helper.fail("A whole villager should be able to guard by its bite: " + row.cannot() + ", fights " + minion.stats().fights());
            return;
        }
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 2, 5));
        husk.setNoAi(true);
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth() || !husk.isAlive(), "the villager has not bitten the husk yet (target " + minion.getTarget() + ")");
            helper.assertTrue(husk.getLastHurtByMob() == minion, "the husk was hurt by something else: " + husk.getLastHurtByMob());
            husk.discard();
            minion.discard();
        });
    }

    /**
     * An item taken up is gone for everyone after it (the review's duplication). A minion takes a stack whole and leaves the
     * item empty as it goes, so a second minion's pickup of it on the same tick (a running goal's tick, before anyone asks
     * whether it goes on) takes nothing; nor does one after a player took the stack, though vanilla's pickup puts the count
     * back on the stack it discards. Part of a stack taken leaves the rest on the ground for the next.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pickUpTakesNothingTwice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity first = minion(helper, new BlockPos(3, 2, 3), armed(ref("chicken", "head")), maker);
        MinionEntity second = minion(helper, new BlockPos(5, 2, 3), armed(ref("chicken", "head")), maker);
        Vec3 at = helper.absoluteVec(new Vec3(4.5, 2.2, 3.5));
        ItemEntity apples = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.APPLE, 5));
        level.addFreshEntity(apples);
        int one = com.avicagan.bloodandbones.minion.MinionGoals.pickUp(first, apples);
        int two = com.avicagan.bloodandbones.minion.MinionGoals.pickUp(second, apples);
        if (one != 5 || two != 0 || count(first, Items.APPLE) != 5 || count(second, Items.APPLE) != 0 || apples.isAlive() || !apples.getItem().isEmpty()) {
            helper.fail("A stack taken whole should be gone and empty, and the second pickup take nothing: " + one + ", " + two + ", "
                    + count(first, Items.APPLE) + ", " + count(second, Items.APPLE));
            return;
        }
        ItemEntity bread = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.BREAD, 4));
        level.addFreshEntity(bread);
        bread.playerTouch(maker);
        int after = com.avicagan.bloodandbones.minion.MinionGoals.pickUp(second, bread);
        if (maker.getInventory().countItem(Items.BREAD) != 4 || bread.isAlive() || after != 0 || count(second, Items.BREAD) != 0) {
            helper.fail("What a player picked up should not be taken again: " + maker.getInventory().countItem(Items.BREAD) + ", " + after);
            return;
        }
        // a slot left with room for 3 more: 3 are taken and 2 stay on the ground, a stack of their own
        for (int i = 0; i < first.inventory.getContainerSize(); i++) {
            first.inventory.setItem(i, i < first.slots() ? new ItemStack(Items.STONE, 64) : ItemStack.EMPTY);
        }
        first.inventory.setItem(0, new ItemStack(Items.CARROT, 61));
        ItemEntity carrots = new ItemEntity(level, at.x, at.y, at.z, new ItemStack(Items.CARROT, 5));
        level.addFreshEntity(carrots);
        int part = com.avicagan.bloodandbones.minion.MinionGoals.pickUp(first, carrots);
        if (part != 3 || !carrots.isAlive() || carrots.getItem().getCount() != 2 || count(first, Items.CARROT) != 64) {
            helper.fail("Part of a stack taken should leave the rest on the ground: took " + part + ", left " + carrots.getItem().getCount());
            return;
        }
        carrots.discard();
        first.discard();
        second.discard();
        helper.succeed();
    }

    /**
     * A courier with its maker hands over only what they have room for (docs/NEXT.md 1.1). With their pack full, it keeps the
     * three apples it fetched (none are dropped at their feet), says why and tries again later; once they have room, it
     * hands them over. The stick its maker threw away lies at their feet all the while: it never fetches that back.
     */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void courierKeepsWhatItsMakerHasNoRoomFor(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(5, 2, 5));
        for (int i = 0; i < maker.getInventory().items.size(); i++) {
            maker.getInventory().items.set(i, new ItemStack(Items.STONE, 64));
        }
        MinionEntity minion = minion(helper, new BlockPos(4, 2, 4), armed(ref("chicken", "head")), maker);
        if (!minion.setTask(MinionTask.COURIER, MinionTask.Anchor.MAKER, 0)) {
            helper.fail("A chicken's head should take carrying with its maker");
            return;
        }
        BlockPos far = helper.absolutePos(new BlockPos(8, 2, 8));
        level.addFreshEntity(new ItemEntity(level, far.getX() + 0.5, far.getY() + 0.2, far.getZ() + 0.5, new ItemStack(Items.APPLE, 3)));
        Vec3 feet = helper.absoluteVec(new Vec3(6.5, 2.2, 5.5));
        ItemEntity thrown = new ItemEntity(level, feet.x, feet.y, feet.z, new ItemStack(Items.STICK));
        thrown.setThrower(maker);
        level.addFreshEntity(thrown);
        AABB area = area(helper);
        String[] wrong = {null};
        boolean[] fetched = {false};
        helper.onEachTick(() -> {
            fetched[0] |= count(minion, Items.APPLE) == 3;
            if (wrong[0] == null && fetched[0] && !level.getEntitiesOfClass(ItemEntity.class, area, e -> e.getItem().is(Items.APPLE)).isEmpty()) {
                wrong[0] = "an apple it had fetched is on the ground";
            }
            if (wrong[0] == null && (!thrown.isAlive() || count(minion, Items.STICK) > 0)) {
                wrong[0] = "it fetched the stick its maker threw away";
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(fetched[0], "it has not fetched the apples yet (" + count(minion, Items.APPLE) + ")"))
                .thenWaitUntil(() -> helper.assertTrue(statusSays(minion, "bloodandbones.minion.idle.maker_full"),
                        "it has not tried to hand them over yet: " + MinionTasks.status(minion).getString()))
                .thenExecute(() -> {
                    helper.assertTrue(wrong[0] == null, String.valueOf(wrong[0]));
                    helper.assertTrue(count(minion, Items.APPLE) == 3 && maker.getInventory().countItem(Items.APPLE) == 0,
                            "with no room it should keep the apples: " + count(minion, Items.APPLE));
                    // room for them
                    maker.getInventory().items.set(0, ItemStack.EMPTY);
                })
                .thenWaitUntil(() -> helper.assertTrue(maker.getInventory().countItem(Items.APPLE) == 3, "its maker has "
                        + maker.getInventory().countItem(Items.APPLE) + " of the 3 apples (it carries " + count(minion, Items.APPLE) + ")"))
                .thenExecute(() -> {
                    helper.assertTrue(wrong[0] == null, String.valueOf(wrong[0]));
                    thrown.discard();
                    minion.discard();
                })
                .thenSucceed();
    }

    /**
     * What a poor butcher wastes is what its own goal cut (the review: the old test worked the waste out itself). Two whole
     * villagers butcher at 50%, each behind its own glass: one at a Butcher's Table with a cow's body laid on it, one at a
     * cow's body lying loose (the test cuts the limbs off first and breaks them down itself, so the body is all that is
     * left). A player gets 4 or 5 beef from a cow's body (4.22, a dice throw for the rest); each of them gets 2 or 3, half.
     */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void poorButcherWastesWhatItCuts(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        for (int z = 1; z <= 9; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.GLASS);
            }
        }
        BlockPos tableAt = new BlockPos(2, 2, 7);
        helper.setBlock(tableAt, BBBlocks.BUTCHER_TABLE.getDefaultState());
        var table = (com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity) helper.getBlockEntity(tableAt);
        table.put(cowPiece());
        Set<UUID> before = new HashSet<>();
        CarcassSavedData.get(level).all().forEach(c -> before.add(c.id));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(8, 2, 7));
        CarcassSavedData.Carcass body = CarcassAssembler.assemble(cow, null);
        cow.discard();
        if (body == null) {
            helper.fail("The cow carcass was not made");
            return;
        }
        before.add(body.id);
        // its limbs off, each at the end of its chain first, and broken down here, so the body is the only piece left
        for (int tries = 0; !body.joints.isEmpty(); tries++) {
            String end = body.joints.stream().map(j -> j.child()).filter(child -> body.joints.stream().noneMatch(j -> j.parent().equals(child)))
                    .findFirst().orElse(null);
            if (end == null || tries > 40) {
                helper.fail("Could not cut the cow's limbs off: " + body.joints);
                return;
            }
            for (int i = 0; i < com.avicagan.bloodandbones.carcass.CarcassButchery.CUTS_TO_SEVER; i++) {
                com.avicagan.bloodandbones.carcass.CarcassButchery.cut(level, null, body, end, null);
            }
        }
        for (CarcassSavedData.Carcass piece : List.copyOf(CarcassSavedData.get(level).all())) {
            if (!before.contains(piece.id)) {
                com.avicagan.bloodandbones.carcass.CarcassButchery.butcher(level, piece, piece.rootBone, null);
            }
        }
        AABB area = area(helper);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity atTable = minion(helper, new BlockPos(2, 2, 3), villager(), maker);
        MinionEntity atBody = minion(helper, new BlockPos(8, 2, 3), villager(), maker);
        for (MinionEntity butcher : new MinionEntity[]{atTable, atBody}) {
            give(butcher, maker, new ItemStack(BBItems.CLEAVER.get()));
            butcher.setHome(helper.absolutePos(new BlockPos(butcher == atTable ? 2 : 8, 2, 6)));
            if (!butcher.setTask(MinionTask.BUTCHER, MinionTask.Anchor.HOME, 3) || Math.abs(butcher.fitness(MinionTask.BUTCHER) - 0.5F) > 1.0E-3F) {
                helper.fail("A whole villager should take butchery at 50%: " + butcher.fitness(MinionTask.BUTCHER));
                return;
            }
        }
        // what a player gets of a cow's body's beef, and so what half of it rounds to either way
        float beef = com.avicagan.bloodandbones.carcass.butchery.ButcheryManager.forEntity(mob("cow")).orElseThrow().part("body").stream()
                .filter(y -> y.item().equals("minecraft:beef")).map(y -> y.count()).reduce(0.0F, Float::sum);
        float half = beef * MinionFitness.yieldShare(0.5F);
        helper.succeedWhen(() -> {
            helper.assertTrue(table.specimen().isEmpty(), "the table's piece has not been chopped yet");
            helper.assertTrue(CarcassSavedData.get(level).carcass(body.id) == null, "the loose body has not been broken down yet (the butcher at "
                    + helper.relativeVec(atBody.position()) + ", " + MinionTasks.status(atBody).getString() + ")");
            for (MinionEntity butcher : new MinionEntity[]{atTable, atBody}) {
                int got = count(butcher, Items.BEEF);
                helper.assertTrue(got >= Math.floor(half) && got <= Math.ceil(half) && got < Math.floor(beef), (butcher == atTable ? "At the table" : "At the body")
                        + " a 50% butcher should keep half a player's " + beef + " beef, " + Math.floor(half) + " or " + Math.ceil(half) + ": it has " + got);
            }
            helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, area).isEmpty(), "what they cut should be in their hands, not on the ground");
            atTable.discard();
            atBody.discard();
        });
    }

    /**
     * A sentry's fitness sets the time between its shots (docs/NEXT.md 1.2), measured in the world: a pillager's head with a
     * bow (200%) and a chicken's (50%), each with a husk of its own behind glass, shoot every 20 ticks of drawing and half a
     * second, or two seconds, of waiting: about 30 ticks and 60 between the arrows each takes from what it carries.
     */
    @GameTest(template = "empty", timeoutTicks = 700)
    public static void sentryShootsByItsFitness(GameTestHelper helper) {
        pen(helper);
        for (int z = 1; z <= 9; z++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(5, y, z), Blocks.GLASS);
            }
        }
        Maker maker = new Maker(helper, new BlockPos(5, 2, 0));
        MinionEntity fit = minion(helper, new BlockPos(2, 2, 2), armed(ref("pillager", "head")), maker);
        MinionEntity poor = minion(helper, new BlockPos(8, 2, 2), armed(ref("chicken", "head")), maker);
        MinionTask.Data data = PartsData.of(helper.getLevel()).task(MinionTask.SENTRY);
        List<List<Integer>> shots = List.of(new ArrayList<>(), new ArrayList<>());
        int[] carried = new int[2];
        MinionEntity[] sentries = {fit, poor};
        for (int i = 0; i < 2; i++) {
            give(sentries[i], maker, new ItemStack(Items.BOW));
            give(sentries[i], maker, new ItemStack(Items.ARROW, 64));
            if (!sentries[i].setTask(MinionTask.SENTRY)) {
                helper.fail("Both should take up sentry");
                return;
            }
            carried[i] = count(sentries[i], Items.ARROW);
            Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(i == 0 ? 2 : 8, 2, 8));
            husk.setNoAi(true);
            // it takes the arrows without dying or being knocked out of its sentry's sight
            husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000.0);
            husk.setHealth(1000.0F);
            husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0);
        }
        float[] fitness = {fit.fitness(MinionTask.SENTRY), poor.fitness(MinionTask.SENTRY)};
        if (Math.abs(fitness[0] - 2.0F) > 1.0E-3F || Math.abs(fitness[1] - 0.5F) > 1.0E-3F) {
            helper.fail("The sentries should be 200% and 50%: " + fitness[0] + ", " + fitness[1]);
            return;
        }
        helper.onEachTick(() -> {
            for (int i = 0; i < 2; i++) {
                int now = count(sentries[i], Items.ARROW);
                for (int shot = now; shot < carried[i]; shot++) {
                    shots.get(i).add((int) helper.getTick());
                }
                carried[i] = now;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(shots.get(0).size() >= 6 && shots.get(1).size() >= 6, "they have not shot six arrows each yet: " + shots);
            for (int i = 0; i < 2; i++) {
                // a bow is drawn for a second, then it waits its time between shots
                int want = 20 + MinionFitness.shotTicks(data.number("bow_every", 20.0F), fitness[i]);
                int gap = medianGap(shots.get(i));
                helper.assertTrue(gap >= want && gap <= want + 3, (i == 0 ? "The 200%" : "The 50%") + " sentry should shoot every " + want + " ticks or so: "
                        + gap + " (shots at " + shots.get(i) + ")");
            }
            fit.discard();
            poor.discard();
        });
    }

    /**
     * A surgeon tends by its fitness (docs/NEXT.md 1.5), measured in the world and read again for each heart (the review: it
     * read its fitness once, as it started): a villager's head over a zombie's arm (200%) tends the pig on its table a heart
     * every 50 ticks; after three, its head is swapped for a zombie villager's shaky one (88%) while it stands there, and it
     * tends one every 113 from the next heart on.
     */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void surgeonTendsByItsFitness(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tableAt = new BlockPos(5, 2, 5);
        helper.setBlock(tableAt, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT,
                com.avicagan.bloodandbones.body.TableAttachment.SURGICAL));
        BlockPos table = helper.absolutePos(tableAt);
        net.minecraft.world.entity.animal.Pig patient = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 7));
        patient.setNoAi(true);
        if (!com.avicagan.bloodandbones.body.SurgeryTableBlock.lieDown(level, table, patient)) {
            helper.fail("The patient should be lying on the table");
            return;
        }
        patient.setHealth(1.0F);
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionBuild steady = MinionBuild.of(ref("zombie", "body")).with("head", villagerHead("none")).with("right_arm", ref("zombie", "right_arm"))
                .with("left_leg", ref("zombie", "left_leg")).with("right_leg", ref("zombie", "right_leg"));
        MinionBuild shaky = steady.with("head", ref("zombie_villager", "head", Map.of("profession", "farmer")));
        MinionEntity surgeon = minion(helper, new BlockPos(5, 2, 4), steady, maker);
        surgeon.setHome(table);
        if (!surgeon.setTask(MinionTask.SURGEON) || surgeon.fitness(MinionTask.SURGEON) != MinionFitness.MOST) {
            helper.fail("A villager's head over a zombie's arm should take up surgery at 200%: " + surgeon.fitness(MinionTask.SURGEON));
            return;
        }
        List<Integer> hearts = new ArrayList<>();
        float[] health = {patient.getHealth()};
        helper.onEachTick(() -> {
            if (patient.getHealth() > health[0]) {
                hearts.add((int) helper.getTick());
                if (hearts.size() == 3) {
                    surgeon.setBuild(shaky);
                }
            }
            health[0] = patient.getHealth();
        });
        MinionTask.Data data = PartsData.of(level).task(MinionTask.SURGEON);
        helper.succeedWhen(() -> {
            helper.assertTrue(hearts.size() >= 6, "it has tended " + hearts.size() + " of 6 hearts (at " + helper.relativeVec(surgeon.position()) + ")");
            float shakyFitness = surgeon.fitness(MinionTask.SURGEON);
            int steadyEvery = MinionFitness.tendTicks(data, MinionFitness.MOST);
            int shakyEvery = MinionFitness.tendTicks(data, shakyFitness);
            List<Integer> gaps = new ArrayList<>();
            for (int i = 1; i < hearts.size(); i++) {
                gaps.add(hearts.get(i) - hearts.get(i - 1));
            }
            // the heart after the swap was already due at the steady pace; the ones after it come at the shaky one
            helper.assertTrue(steadyEvery == 50 && shakyEvery == 113 && Math.abs(shakyFitness - 0.884F) < 0.01F, "the paces should be 50 and 113 ticks: "
                    + steadyEvery + ", " + shakyEvery + " at " + shakyFitness);
            helper.assertTrue(Math.abs(gaps.get(0) - 50) <= 1 && Math.abs(gaps.get(1) - 50) <= 1 && Math.abs(gaps.get(3) - 113) <= 1
                    && Math.abs(gaps.get(4) - 113) <= 1, "it should tend every 50 ticks, then every 113 once shaky: " + gaps);
            surgeon.discard();
            patient.discard();
        });
    }

    /**
     * A surgeon set down away from a table takes the nearest within its reach of where it stands (docs/NEXT.md 1.1), and its
     * maker's reach counts: a table 8 blocks off is beyond its own 6, and within the 10 its maker sets.
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void surgeonFindsATableWithinItsReach(GameTestHelper helper) {
        pen(helper);
        BlockPos tableAt = new BlockPos(9, 2, 5);
        helper.setBlock(tableAt, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT,
                com.avicagan.bloodandbones.body.TableAttachment.SURGICAL));
        BlockPos table = helper.absolutePos(tableAt);
        Maker maker = new Maker(helper, new BlockPos(1, 2, 4));
        MinionEntity surgeon = minion(helper, new BlockPos(1, 2, 5), armed(villagerHead("none")), maker);
        BlockPos home = surgeon.home();
        if (!surgeon.setTask(MinionTask.SURGEON) || surgeon.reach() != 6) {
            helper.fail("It should take up surgery, reaching 6");
            return;
        }
        helper.startSequence()
                .thenIdle(150)
                .thenExecute(() -> {
                    helper.assertTrue(surgeon.home().equals(home), "a table 8 blocks off is beyond its reach of 6: its home is now " + surgeon.home());
                    helper.assertTrue(surgeon.setTask(MinionTask.SURGEON, MinionTask.Anchor.HOME, 10), "its maker may set its reach to 10");
                })
                .thenWaitUntil(() -> helper.assertTrue(surgeon.home().equals(table), "with a reach of 10 it has not taken the table yet"))
                .thenExecute(surgeon::discard)
                .thenSucceed();
    }

    /**
     * A fisher's fitness sets its wait for a catch (docs/NEXT.md 1.2), measured in the world with no hurrying: a fisherman's
     * head with a rod (200%) sets its wait between 15 and 30 s and catches when it is up; a zombie's head fishing by hand
     * (49%) sets its between about 61 and 121 s.
     */
    @GameTest(template = "empty", timeoutTicks = 1000)
    public static void fisherWaitsByItsFitness(GameTestHelper helper) {
        pen(helper);
        for (int x = 6; x <= 9; x++) {
            for (int z = 3; z <= 7; z++) {
                boolean rim = x == 6 || x == 9 || z == 3 || z == 7;
                helper.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE : Blocks.WATER);
            }
        }
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity fit = minion(helper, new BlockPos(3, 2, 3), armed(villagerHead("fisherman")), maker);
        MinionEntity poor = minion(helper, new BlockPos(3, 2, 7), armed(ref("zombie", "head")), maker);
        give(fit, maker, new ItemStack(Items.FISHING_ROD));
        if (!fit.setTask(MinionTask.FISHER) || !poor.setTask(MinionTask.FISHER)) {
            helper.fail("Both should take up fishing");
            return;
        }
        MinionTask.Data data = PartsData.of(helper.getLevel()).task(MinionTask.FISHER);
        float[] fitness = {fit.fitness(MinionTask.FISHER), poor.fitness(MinionTask.FISHER)};
        int[][] times = {MinionFitness.catchTicks(data, fitness[0]), MinionFitness.catchTicks(data, fitness[1])};
        if (fitness[0] != MinionFitness.MOST || Math.abs(fitness[1] - 0.495F) > 0.01F || times[1][0] <= times[0][1]) {
            helper.fail("The fishers should be 200% and about 49%, their waits apart: " + fitness[0] + ", " + fitness[1]);
            return;
        }
        // the wait each set as it first set to work, and when; and the tick of the fit one's catch
        int[] wait = {-1, -1};
        int[] from = {-1, -1};
        int[] caught = {-1};
        MinionEntity[] fishers = {fit, poor};
        helper.onEachTick(() -> {
            for (int i = 0; i < 2; i++) {
                int left = MinionTasks.workLeft(fishers[i]);
                if (wait[i] < 0 && left >= 0) {
                    wait[i] = left;
                    from[i] = (int) helper.getTick();
                }
            }
            if (caught[0] < 0 && carried(fit) > 0) {
                caught[0] = (int) helper.getTick();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(wait[0] >= 0 && wait[1] >= 0, "they have not both set to work yet: " + wait[0] + ", " + wait[1]);
            // a wait is set and its first tick worked at once, so a tick less is seen
            for (int i = 0; i < 2; i++) {
                helper.assertTrue(wait[i] >= times[i][0] - 1 && wait[i] <= times[i][1], (i == 0 ? "The 200%" : "The 49%") + " fisher's wait should be "
                        + times[i][0] + " to " + times[i][1] + " ticks: " + (wait[i] + 1));
            }
            helper.assertTrue(caught[0] >= 0, "the fit one has not caught anything yet (" + MinionTasks.workLeft(fit) + " ticks of its wait left)");
            int took = caught[0] - from[0];
            helper.assertTrue(took >= wait[0] && took <= wait[0] + 40, "its catch should come when its wait is up: after " + took + " ticks of " + (wait[0] + 1));
            fit.discard();
            poor.discard();
        });
    }

    /**
     * A task file's kind moves the task under that group on the screen, its anchors are where it is set, and its tool is
     * what the goals take (docs/NEXT.md 1.6): a hauler whose file says it is a fight is sent as one; a guard whose file has it
     * only with its maker is set there by the plain call that sets tasks at home; a sentry whose file names only the crossbow
     * has no ranged attack with a bow in hand. Each file is set and set back within the one tick, since the tests share one
     * world.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void taskFileKindAndAnchorsInPlay(GameTestHelper helper) {
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), armed(ref("pillager", "head")), maker);
        give(minion, maker, new ItemStack(Items.BOW));
        PartsData.Store store = PartsData.of(helper.getLevel());
        MinionTask.Data hauler = store.task(MinionTask.HAULER);
        MinionTask.Data guard = store.task(MinionTask.GUARD);
        MinionTask.Data sentry = store.task(MinionTask.SENTRY);
        int kind;
        boolean guardSet;
        MinionTask.Anchor guardAt;
        boolean bowWith;
        try {
            store.setTestTask(MinionTask.HAULER, hauler.read(com.google.gson.JsonParser.parseString("{\"kind\": \"fight\"}").getAsJsonObject()));
            kind = MinionTasks.open(minion, maker).orElseThrow().rows().get(MinionTask.HAULER.ordinal()).kind();
            store.setTestTask(MinionTask.GUARD, guard.read(com.google.gson.JsonParser.parseString("{\"anchors\": [\"maker\"]}").getAsJsonObject()));
            guardSet = minion.setTask(MinionTask.GUARD);
            guardAt = minion.anchor();
            store.setTestTask(MinionTask.SENTRY, sentry.read(com.google.gson.JsonParser.parseString("{\"tool\": {\"items\": \"minecraft:crossbow\"}}").getAsJsonObject()));
            bowWith = minion.hasRangedAttack();
        } finally {
            store.setTestTask(MinionTask.HAULER, null);
            store.setTestTask(MinionTask.GUARD, null);
            store.setTestTask(MinionTask.SENTRY, null);
        }
        int shipped = MinionTasks.open(minion, maker).orElseThrow().rows().get(MinionTask.HAULER.ordinal()).kind();
        boolean bowWithout = minion.hasRangedAttack();
        minion.discard();
        if (kind != MinionTask.Kind.FIGHT.ordinal() || shipped != MinionTask.Kind.FETCH.ordinal()) {
            helper.fail("The screen should list the hauler under its file's kind: " + kind + ", " + shipped);
            return;
        }
        if (!guardSet || guardAt != MinionTask.Anchor.MAKER) {
            helper.fail("A guard done only with its maker should be set there: " + guardSet + ", " + guardAt);
            return;
        }
        if (bowWith || !bowWithout) {
            helper.fail("A sentry whose file names only the crossbow should have no ranged attack with a bow: " + bowWith + ", " + bowWithout);
            return;
        }
        helper.succeed();
    }

    // ---- the second review of the tasks (docs/ARCHITECTURE-PROPOSAL.md 15.23)

    /**
     * Wings only buffet (docs/NEXT.md 1.7): a farmer villager's head on a chicken's body and wings bites, as its Blow reads
     * it, and set to Guard it hurts the husk by home. A chicken's headless body on its wings has nothing to fight with: it
     * is offered no fight task.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void wingsBuffetTheHeadBites(GameTestHelper helper) {
        pen(helper);
        MinionBuild headless = MinionBuild.of(ref("chicken", "body")).with("right_wing", ref("chicken", "right_wing")).with("left_wing", ref("chicken", "left_wing"));
        MinionBuild winged = headless.with("head", villagerHead("farmer"));
        MinionStats bare = MinionStats.of(PartsData.SERVER, headless);
        for (MinionTask task : new MinionTask[]{MinionTask.GUARD, MinionTask.SENTRY, MinionTask.HUNTER}) {
            MinionFitness.Row row = MinionFitness.of(PartsData.SERVER, headless, bare, MinionFitness.Context.NONE, task);
            if (bare.fights() || !bare.flaps() || row.can()) {
                helper.fail("A headless body on wings has nothing to strike with, so no " + task + ": fights " + bare.fights() + ", " + row.cannot());
                return;
            }
        }
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 5), winged, maker);
        MinionFitness.Row row = minion.row(MinionTask.GUARD, MinionTask.Anchor.HOME);
        MinionFitness.Body body = minion.fitnessBody();
        if (!row.can() || !minion.stats().fights() || body == null || body.striking() != 0 || body.hardest() != minion.stats().biteDamage()
                || !minion.setTask(MinionTask.GUARD)) {
            helper.fail("A head on wings should guard by its bite, as its Blow reads it: " + row.cannot() + ", "
                    + (body == null ? "no body" : body.striking() + " striking, hardest " + body.hardest()) + " for a bite of " + minion.stats().biteDamage());
            return;
        }
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(6, 2, 5));
        husk.setNoAi(true);
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth() || !husk.isAlive(), "the head on wings has not bitten the husk yet (target "
                    + minion.getTarget() + ")");
            helper.assertTrue(husk.getLastHurtByMob() == minion, "the husk was hurt by something else: " + husk.getLastHurtByMob());
            husk.discard();
            minion.discard();
        });
    }

    /**
     * The reach its maker sets counts with them too (docs/NEXT.md 1.1): a guard with its maker at reach 4 leaves be what its
     * maker hits 8 blocks off, and goes for what they hit beside them; Idle with its maker keeps as far from them as its reach
     * (it sets off 2 blocks past it and stops 1 short), so at 8 it stays put while they stand 9 off, and at its own 4 it
     * follows.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void withMeKeepsToItsReach(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionEntity guard = minion(helper, new BlockPos(2, 2, 3), armed(ref("zombie", "head")), maker);
        MinionEntity idle = minion(helper, new BlockPos(3, 2, 2), armed(ref("zombie", "head")), maker);
        if (!guard.setTask(MinionTask.GUARD, MinionTask.Anchor.MAKER, 4) || !idle.setTask(MinionTask.IDLE, MinionTask.Anchor.MAKER, 0)) {
            helper.fail("A zombie should take guarding and Idle with its maker");
            return;
        }
        com.avicagan.bloodandbones.minion.MinionGoals.FollowMaker idleFollows = new com.avicagan.bloodandbones.minion.MinionGoals.FollowMaker(idle);
        com.avicagan.bloodandbones.minion.MinionGoals.FollowMaker guardFollows = new com.avicagan.bloodandbones.minion.MinionGoals.FollowMaker(guard);
        double ownStart = idleFollows.setOff();
        double ownStop = idleFollows.stopAt();
        idle.setTask(MinionTask.IDLE, MinionTask.Anchor.MAKER, 8);
        if (ownStart != 6.0 || ownStop != 3.0 || idleFollows.setOff() != 10.0 || idleFollows.stopAt() != 7.0 || guardFollows.setOff() != 6.0
                || guardFollows.stopAt() != 3.0) {
            helper.fail("Idle with its maker keeps as far as its reach, 6 and 3 at its own 4, 10 and 7 at 8; the guard follows at 6 and 3: " + ownStart + " "
                    + ownStop + " " + idleFollows.setOff() + " " + idleFollows.stopAt() + " " + guardFollows.setOff() + " " + guardFollows.stopAt());
            return;
        }
        Husk[] husks = new Husk[2];
        Vec3[] idleFrom = new Vec3[1];
        helper.startSequence()
                .thenExecute(() -> {
                    // its maker hits something 8 blocks off, beyond the guard's reach of them
                    husks[0] = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 8));
                    husks[0].setNoAi(true);
                    maker.tickCount += 200;
                    maker.setLastHurtMob(husks[0]);
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(husks[0].getHealth() >= husks[0].getMaxHealth() && guard.getTarget() != husks[0],
                            "the guard should leave be what its maker hits beyond its reach of them (its target " + guard.getTarget() + ")");
                    husks[0].discard();
                    // now beside them
                    husks[1] = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 2));
                    husks[1].setNoAi(true);
                    maker.tickCount += 200;
                    maker.setLastHurtMob(husks[1]);
                })
                .thenWaitUntil(() -> helper.assertTrue(husks[1].getHealth() < husks[1].getMaxHealth() || !husks[1].isAlive(),
                        "the guard has not gone for what its maker hit beside them (its target " + guard.getTarget() + ")"))
                .thenExecute(() -> {
                    husks[1].discard();
                    guard.discard();
                    // its maker walks 9 blocks off: within the Idle's 10 at reach 8
                    maker.stand(helper, new Vec3(8.5, 2.0, 8.5));
                    idleFrom[0] = idle.position();
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(idle.distanceTo(maker) > 8.0F && idle.position().distanceTo(idleFrom[0]) < 1.0,
                            "Idle at reach 8 should stay while its maker is 9 off: now " + idle.distanceTo(maker) + " from them");
                    idle.setTask(MinionTask.IDLE, MinionTask.Anchor.MAKER, 0);
                })
                .thenWaitUntil(() -> helper.assertTrue(idle.distanceTo(maker) < 5.0F, "Idle at its own reach should follow its maker: "
                        + idle.distanceTo(maker)))
                .thenExecute(idle::discard)
                .thenSucceed();
    }

    /**
     * Its looks come as often as its screen says (docs/NEXT.md 1.2): over two minutes a 200% courier (a horse) looks round
     * every 10 ticks and a poor one (a zombie) every so many more, counted in the world as its goal asks, which is every
     * other tick. Each count is within four and a half of its spread of what its look ticks make; one in half the look's
     * ticks a time is the chance, and the one-in-the-look's-ticks it had before gave half as many, far outside it.
     */
    @GameTest(template = "empty", timeoutTicks = 2500)
    public static void looksComeAsOftenAsTheScreenSays(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(5, 2, 5));
        MinionEntity fit = minion(helper, new BlockPos(3, 2, 3), horse(), maker);
        MinionEntity poor = minion(helper, new BlockPos(7, 2, 7), armed(ref("zombie", "head")), maker);
        // reaching 2, it finds nothing to fetch and so goes on looking, never working
        if (!fit.setTask(MinionTask.COURIER, MinionTask.Anchor.HOME, 2) || !poor.setTask(MinionTask.COURIER, MinionTask.Anchor.HOME, 2)) {
            helper.fail("Anything can carry");
            return;
        }
        int[] from = new int[2];
        int[] every = new int[2];
        int span = 2400;
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    MinionTask.Data courier = PartsData.SERVER.task(MinionTask.COURIER);
                    every[0] = MinionFitness.lookTicks(courier, fit.taskFitness());
                    every[1] = MinionFitness.lookTicks(courier, poor.taskFitness());
                    helper.assertTrue(every[0] == 10 && every[1] >= 30, "the horse should look every 10 ticks and the zombie every 30 or more: "
                            + every[0] + ", " + every[1]);
                    from[0] = fit.looksTaken();
                    from[1] = poor.looksTaken();
                })
                .thenIdle(span)
                .thenExecute(() -> {
                    MinionEntity[] couriers = {fit, poor};
                    StringBuilder counts = new StringBuilder();
                    boolean right = true;
                    for (int i = 0; i < 2; i++) {
                        MinionTask.Data courier = PartsData.SERVER.task(MinionTask.COURIER);
                        helper.assertTrue(MinionFitness.lookTicks(courier, couriers[i].taskFitness()) == every[i] && !couriers[i].working(),
                                "its fitness or its work changed on the way");
                        double chance = 1.0 / Math.max(1, (every[i] + 1) / 2);
                        double asks = span / 2.0;
                        double mean = asks * chance;
                        double spread = Math.sqrt(asks * chance * (1.0 - chance));
                        int looks = couriers[i].looksTaken() - from[i];
                        counts.append(looks).append(" looks, expected ").append(Math.round(mean)).append(i == 0 ? "; " : "");
                        right &= Math.abs(looks - mean) <= 4.5 * spread;
                    }
                    helper.assertTrue(right, "each should look about as often as its look ticks make: " + counts);
                    fit.discard();
                    poor.discard();
                })
                .thenSucceed();
    }

    /**
     * A fitter farmer notices a newly ripe crop no later than a 100% one (docs/NEXT.md 1.2), wherever in its box it ripens.
     * The bands first: at every reach its maker may set, a farmer at 200% and at 150% goes over its whole box in no more
     * ticks than a 100% one, and a 50% one reads no more than FARM_SCAN blocks a tick. Then in the world: a farmer villager's
     * head on eight zombie arms (200%), set to reach 6 by the pen's side, where a farmer looking 10 ticks apart read its box
     * in two bands; a crop ripens on one side of the pen, then, once it is reaped, on the other, 30 times. It starts on
     * each ripe one within its 10-tick look on average (a 100% farmer's average is 20 ticks); in two bands it took 20.
     */
    @GameTest(template = "empty", timeoutTicks = 3000)
    public static void fitFarmerFindsRipeCropsNoLater(GameTestHelper helper) {
        MinionTask.Data farming = PartsData.SERVER.task(MinionTask.FARMER);
        int base = MinionFitness.lookTicks(farming, 1.0F);
        for (int reach = MinionTasks.LEAST_REACH; reach <= farming.maxReach(); reach++) {
            int width = reach * 2 + 1;
            int baseSweep = Math.ceilDiv(width, com.avicagan.bloodandbones.minion.MinionGoals.farmColumns(width, base, base)) * base;
            for (float fitness : new float[]{1.5F, 2.0F}) {
                int look = MinionFitness.lookTicks(farming, fitness);
                int sweep = Math.ceilDiv(width, com.avicagan.bloodandbones.minion.MinionGoals.farmColumns(width, look, base)) * look;
                if (sweep > baseSweep) {
                    helper.fail("At reach " + reach + " a farmer at " + fitness + " goes over its box in " + sweep + " ticks, a 100% one in " + baseSweep);
                    return;
                }
            }
            int slow = MinionFitness.lookTicks(farming, 0.5F);
            int columns = com.avicagan.bloodandbones.minion.MinionGoals.farmColumns(width, slow, base);
            if (columns > 1 && columns * width * 5 > com.avicagan.bloodandbones.minion.MinionGoals.FARM_SCAN * slow + width * 5) {
                helper.fail("At reach " + reach + " a 50% farmer reads " + columns + " columns a look, more than its share");
                return;
            }
        }
        pen(helper);
        BlockPos[] crops = {new BlockPos(4, 2, 5), new BlockPos(8, 2, 5)};
        for (BlockPos crop : crops) {
            helper.setBlock(crop.below(), Blocks.FARMLAND);
            helper.setBlock(crop, Blocks.WHEAT);
        }
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionEntity farmer = minion(helper, new BlockPos(3, 2, 5), spiderOfArms(villagerHead("farmer")), maker);
        if (!farmer.setTask(MinionTask.FARMER, MinionTask.Anchor.HOME, 6)) {
            helper.fail("A farmer's head on eight arms should take farming at reach 6: " + farmer.row(MinionTask.FARMER, MinionTask.Anchor.HOME).cannot());
            return;
        }
        List<Integer> waits = new ArrayList<>();
        long[] ripened = {-1L};
        int[] next = {0};
        helper.onEachTick(() -> {
            if (waits.size() >= 30 || helper.getTick() < 20) {
                return;
            }
            if (ripened[0] < 0) {
                BlockPos crop = crops[next[0] % 2];
                if (!farmer.working() && helper.getBlockState(crop).is(Blocks.WHEAT) && helper.getBlockState(crop).getValue(net.minecraft.world.level.block.CropBlock.AGE) < 7) {
                    helper.setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
                    ripened[0] = helper.getTick();
                    next[0]++;
                }
            } else if (farmer.working()) {
                waits.add((int) (helper.getTick() - ripened[0]));
                ripened[0] = -1L;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(MinionFitness.lookTicks(farming, farmer.taskFitness()) == 10, "the farmer should look every 10 ticks: " + farmer.taskFitness());
            helper.assertTrue(waits.size() >= 30, "it has found " + waits.size() + " of 30 ripe crops");
            double mean = waits.stream().mapToInt(Integer::intValue).average().orElse(0.0);
            helper.assertTrue(mean <= 16.0, "the 200% farmer took " + mean + " ticks on average to start on a ripe crop (a 100% farmer's look is 20): " + waits);
            farmer.discard();
        });
    }

    /**
     * A surgeon with no Surgery Table within its reach says so (docs/NEXT.md 1.4): "no Surgery Table within 6 of where it
     * stands", and its status line has it "at home", not "at its table", until it has one.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void surgeonWithNoTableSaysSo(GameTestHelper helper) {
        pen(helper);
        Maker maker = new Maker(helper, new BlockPos(1, 2, 4));
        MinionEntity surgeon = minion(helper, new BlockPos(2, 2, 5), armed(villagerHead("none")), maker);
        if (!surgeon.setTask(MinionTask.SURGEON)) {
            helper.fail("It should take up surgery");
            return;
        }
        BlockPos tableAt = new BlockPos(5, 2, 5);
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> helper.assertTrue(statusSays(surgeon, "bloodandbones.minion.idle.surgeon")
                        && statusSays(surgeon, "bloodandbones.minion.where.home") && !statusSays(surgeon, "bloodandbones.minion.where.table"),
                        "with no table in reach it should say so, at home: " + MinionTasks.status(surgeon).getString()))
                .thenIdle(150)
                .thenExecute(() -> {
                    helper.assertTrue(statusSays(surgeon, "bloodandbones.minion.idle.surgeon"), "it should go on saying so between its looks: "
                            + MinionTasks.status(surgeon).getString());
                    helper.setBlock(tableAt, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT,
                            com.avicagan.bloodandbones.body.TableAttachment.SURGICAL));
                })
                .thenWaitUntil(() -> helper.assertTrue(surgeon.home().equals(helper.absolutePos(tableAt)) && statusSays(surgeon, "bloodandbones.minion.where.table")
                        && !statusSays(surgeon, "bloodandbones.minion.idle.surgeon"), "it has not taken the table yet: " + MinionTasks.status(surgeon).getString()))
                .thenExecute(surgeon::discard)
                .thenSucceed();
    }

    /**
     * A data reload that leaves a task done only elsewhere moves it there (docs/NEXT.md 1.6, 1.8): a Guard saved working
     * with its maker, loaded under a guard file allowing only home, is Guard at home; one saved at home, under a file
     * allowing only its maker, is Guard with its maker. Each keeps its home. Each file is set and set back in the one tick.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void reloadMovesATaskWhereItIsDone(GameTestHelper helper) {
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), armed(ref("zombie", "head")), maker);
        BlockPos home = helper.absolutePos(new BlockPos(8, 2, 1));
        minion.setHome(home);
        net.minecraft.nbt.CompoundTag withMaker = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.CompoundTag atHome = new net.minecraft.nbt.CompoundTag();
        if (!minion.setTask(MinionTask.GUARD, MinionTask.Anchor.MAKER, 0)) {
            helper.fail("A zombie should guard with its maker");
            return;
        }
        minion.saveWithoutId(withMaker);
        minion.setTask(MinionTask.GUARD, MinionTask.Anchor.HOME, 0);
        minion.setHome(home);
        minion.saveWithoutId(atHome);
        minion.discard();
        PartsData.Store store = PartsData.SERVER;
        MinionTask.Data guard = store.task(MinionTask.GUARD);
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(BloodAndBones.MOD_ID, "test_reload_anchors");
        MinionEntity moved;
        MinionEntity joined;
        MinionTask.Anchor movedWas;
        MinionTask.Anchor joinedWas;
        try {
            store.setTestTask(MinionTask.GUARD, MinionTask.GUARD.checked(guard.read(com.google.gson.JsonParser.parseString("{\"anchors\": [\"home\"]}")
                    .getAsJsonObject()), file));
            moved = reload(helper, withMaker);
            movedWas = moved.anchor();
            MinionTasks.keepPossible(moved);
            store.setTestTask(MinionTask.GUARD, MinionTask.GUARD.checked(guard.read(com.google.gson.JsonParser.parseString("{\"anchors\": [\"maker\"]}")
                    .getAsJsonObject()), file));
            joined = reload(helper, atHome);
            joinedWas = joined.anchor();
            MinionTasks.keepPossible(joined);
        } finally {
            store.setTestTask(MinionTask.GUARD, null);
        }
        if (movedWas != MinionTask.Anchor.MAKER || !moved.hasTask(MinionTask.GUARD) || moved.anchor() != MinionTask.Anchor.HOME || !moved.home().equals(home)) {
            helper.fail("A guard saved with its maker, under a file allowing only home, should be Guard at home, its home kept: loaded " + movedWas + ", now "
                    + moved.task() + " " + moved.anchor() + " " + moved.home());
            return;
        }
        if (joinedWas != MinionTask.Anchor.HOME || !joined.hasTask(MinionTask.GUARD) || joined.anchor() != MinionTask.Anchor.MAKER || !joined.home().equals(home)) {
            helper.fail("A guard saved at home, under a file allowing only its maker, should be Guard with its maker, its home kept: loaded " + joinedWas
                    + ", now " + joined.task() + " " + joined.anchor() + " " + joined.home());
            return;
        }
        helper.succeed();
    }
}
