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
import com.avicagan.bloodandbones.minion.MinionJobs;
import com.avicagan.bloodandbones.minion.MinionStats;
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
 * Heads and jobs (docs/PARTS-AND-TRAITS.md section 6.4 and 6.9, slice 7): what a head offers, by its profession and its
 * eyes, the maker's crouching click that changes the job, and each new job at its work. The working tests wall their
 * pen in glass, so a minion never sees or wanders to what the other tests in this world have made.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinionJobTests {
    private static ResourceLocation mob(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    private static ResourceLocation job(String name) {
        return BloodAndBones.asResource(name);
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

    /** A stand-in maker standing here, who remembers what the action bar told them. */
    private static final class Maker extends Player {
        final List<Component> told = new ArrayList<>();

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

    /** Its maker crouches and clicks it with an empty hand until it has this job (at most as many times as it has jobs). */
    private static boolean switchTo(MinionEntity minion, Player maker, String name) {
        maker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        maker.setShiftKeyDown(true);
        for (int i = 0; i < 20 && !minion.hasJob(name); i++) {
            minion.interact(maker, InteractionHand.MAIN_HAND);
        }
        maker.setShiftKeyDown(false);
        return minion.hasJob(name);
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

    /** Whether the action bar's last line was this one. */
    private static boolean said(Maker maker, String key) {
        return !maker.told.isEmpty() && maker.told.get(maker.told.size() - 1).getContents() instanceof TranslatableContents last && last.getKey().equals(key);
    }

    private static boolean saidJob(Maker maker, String name) {
        if (maker.told.isEmpty() || !(maker.told.get(maker.told.size() - 1).getContents() instanceof TranslatableContents said)) {
            return false;
        }
        return said.getKey().equals("bloodandbones.minion.job_now") && said.getArgs().length == 1
                && said.getArgs()[0] instanceof Component arg && arg.getContents() instanceof TranslatableContents key
                && key.getKey().equals(MinionEntity.jobKey(job(name)));
    }

    // ---- what heads offer

    /**
     * A villager's head offers surgeon and its profession's job: the profession is kept on the carcass (and so on its
     * pieces) when the villager dies, and each profession names its job in the villager family's data. A head from
     * before professions were kept offers the family's surgeon, farmer and courier.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void villagerHeadOffersSurgeon(GameTestHelper helper) {
        Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FISHERMAN));
        String kept = CarcassLook.traits(villager).get("profession");
        villager.discard();
        if (!"fisherman".equals(kept)) {
            helper.fail("A villager's carcass should keep its profession: " + kept);
            return;
        }
        Map<String, String> expected = Map.ofEntries(Map.entry("farmer", "farmer"), Map.entry("fisherman", "fisher"), Map.entry("butcher", "butcher"),
                Map.entry("cleric", "medic"), Map.entry("shepherd", "herder"), Map.entry("fletcher", "sentry"), Map.entry("leatherworker", "hauler"),
                Map.entry("armorer", "guard"), Map.entry("weaponsmith", "guard"), Map.entry("toolsmith", "guard"), Map.entry("librarian", "courier"),
                Map.entry("cartographer", "courier"), Map.entry("mason", "courier"), Map.entry("none", "courier"));
        for (Map.Entry<String, String> e : expected.entrySet()) {
            MinionStats stats = MinionStats.of(PartsData.SERVER, armed(villagerHead(e.getKey())));
            if (!stats.jobs().equals(List.of(job("surgeon"), job(e.getValue())))) {
                helper.fail("A " + e.getKey() + "'s head should offer surgeon and " + e.getValue() + ": " + stats.jobs());
                return;
            }
        }
        MinionStats old = MinionStats.of(PartsData.SERVER, armed(ref("villager", "head")));
        if (!old.jobs().equals(List.of(job("surgeon"), job("farmer"), job("courier")))) {
            helper.fail("A villager's head with no profession kept should offer surgeon, farmer and courier: " + old.jobs());
            return;
        }
        helper.succeed();
    }

    /**
     * A pillager's head offers surgeon and sentry (section 8.2). Sentry stands only with a ranged attack in a hand that
     * fights: given a bow, the minion can take it up; with no arms, never.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void pillagerHeadOffersSurgeon(GameTestHelper helper) {
        MinionStats stats = MinionStats.of(PartsData.SERVER, armed(ref("pillager", "head")));
        if (!stats.jobs().equals(List.of(job("surgeon"), job("sentry")))) {
            helper.fail("A pillager's head should offer surgeon and sentry: " + stats.jobs());
            return;
        }
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), armed(ref("pillager", "head")), maker);
        if (!MinionJobs.offered(minion).equals(List.of(job("surgeon"))) || !minion.hasJob("surgeon")) {
            helper.fail("With nothing in hand it should start as a surgeon, sentry withheld: " + MinionJobs.offered(minion));
            return;
        }
        give(minion, maker, new ItemStack(Items.BOW));
        if (!minion.getMainHandItem().is(Items.BOW) || !MinionJobs.offered(minion).contains(job("sentry")) || !minion.setJob(job("sentry"))) {
            helper.fail("Given a bow it should hold it and be offered sentry: " + minion.getMainHandItem() + " " + MinionJobs.offered(minion));
            return;
        }
        MinionEntity stump = minion(helper, new BlockPos(7, 2, 7), armless(ref("pillager", "head")), maker);
        give(stump, maker, new ItemStack(Items.BOW));
        if (MinionJobs.offered(stump).contains(job("sentry")) || stump.hasRangedAttack()) {
            helper.fail("With no arm to draw it, a bow in its mouth is no ranged attack: " + MinionJobs.offered(stump));
            return;
        }
        minion.discard();
        stump.discard();
        helper.succeed();
    }

    /** A nitwit's head offers companion and nothing else, hands or no hands. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void nitwitOffersCompanionOnly(GameTestHelper helper) {
        MinionStats stats = MinionStats.of(PartsData.SERVER, armed(villagerHead("nitwit")));
        MinionStats farmer = MinionStats.of(PartsData.SERVER, armed(villagerHead("farmer")));
        if (!stats.jobs().equals(List.of(MinionStats.COMPANION)) || farmer.jobs().equals(stats.jobs())) {
            helper.fail("A nitwit's head should offer only companion: " + stats.jobs());
            return;
        }
        helper.succeed();
    }

    /**
     * A hand's work needs a hand (section 5.6): a chicken's own wings under a butcher's head give neither surgeon nor
     * butcher, and cannot draw a bow held in its beak; a zombie's arms under the same head give both jobs.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void wingsAreNoHands(GameTestHelper helper) {
        MinionBuild winged = MinionBuild.of(ref("chicken", "body")).with("head", villagerHead("butcher")).with("right_wing", ref("chicken", "right_wing"))
                .with("left_wing", ref("chicken", "left_wing"));
        MinionStats stats = MinionStats.of(PartsData.SERVER, winged);
        if (stats.strikes().size() != 2 || !stats.strikes().stream().allMatch(s -> "wing".equals(s.grip())) || !stats.jobs().equals(List.of(MinionStats.COMPANION))) {
            helper.fail("A butcher's head on a chicken's wings should keep company only, its wings no hands: " + stats.jobs() + " " + stats.strikes());
            return;
        }
        MinionStats handed = MinionStats.of(PartsData.SERVER, armed(villagerHead("butcher")));
        if (!handed.jobs().equals(List.of(job("surgeon"), job("butcher")))) {
            helper.fail("With a zombie's hands the same head should offer surgeon and butcher: " + handed.jobs());
            return;
        }
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), winged, maker);
        give(minion, maker, new ItemStack(Items.BOW));
        if (!minion.getMainHandItem().is(Items.BOW) || minion.hasRangedAttack() || MinionJobs.offered(minion).contains(job("sentry"))) {
            helper.fail("It may hold a bow in its beak, but cannot draw it with wings: " + minion.getMainHandItem() + " " + minion.hasRangedAttack());
            return;
        }
        minion.discard();
        helper.succeed();
    }

    /**
     * Its maker crouches and clicks it with an empty hand: the next job its head offers, round and round, named on the
     * action bar. Someone else's click changes nothing. A job it cannot do yet (sentry with no bow) is left out until it can.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cycleJobWithEmptyHand(GameTestHelper helper) {
        Maker maker = new Maker(helper, new BlockPos(3, 2, 3));
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), armed(villagerHead("fletcher")), maker);
        maker.setShiftKeyDown(true);
        minion.interact(maker, InteractionHand.MAIN_HAND);
        if (!minion.hasJob("surgeon") || !saidJob(maker, "surgeon")) {
            helper.fail("A fletcher's head with no bow can only be a surgeon: " + minion.job());
            return;
        }
        give(minion, maker, new ItemStack(Items.BOW));
        maker.setShiftKeyDown(true);
        minion.interact(maker, InteractionHand.MAIN_HAND);
        if (!minion.hasJob("sentry") || !saidJob(maker, "sentry")) {
            helper.fail("Holding a bow, the next job should be sentry, named on the action bar: " + minion.job() + " " + maker.told);
            return;
        }
        minion.interact(maker, InteractionHand.MAIN_HAND);
        if (!minion.hasJob("surgeon") || !saidJob(maker, "surgeon")) {
            helper.fail("And round again to surgeon: " + minion.job());
            return;
        }
        Maker stranger = new Maker(helper, new BlockPos(7, 2, 7));
        stranger.setShiftKeyDown(true);
        minion.interact(stranger, InteractionHand.MAIN_HAND);
        if (!minion.hasJob("surgeon")) {
            helper.fail("Someone else's click should not change its job");
            return;
        }
        // its maker takes the bow back: sentry goes from the list
        minion.setJob(job("sentry"));
        maker.setShiftKeyDown(false);
        maker.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        minion.interact(maker, InteractionHand.MAIN_HAND);
        if (!maker.getMainHandItem().is(Items.BOW) && maker.getInventory().countItem(Items.BOW) != 1 || minion.hasJob("sentry")) {
            helper.fail("An empty hand should take the bow back, and with it the sentry's job: " + minion.job());
            return;
        }
        minion.discard();
        helper.succeed();
    }

    /**
     * Both eyes taken out of a head at the Surgical Rig: it loses the jobs that need sight (farmer, sentry, surgeon,
     * hunter, fisher) and notices things only 4 blocks off. One eye out changes nothing; a zombie's jobs need no eyes.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void blindHeadLosesSightJobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(new BlockPos(3, 2, 3), BBBlocks.SURGERY_TABLE.getDefaultState());
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3));
        ItemStack head = new ItemStack(BBItems.CARCASS_PIECE.get());
        head.set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(mob("villager"), "head", ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png"),
                List.of(), 1.0F, false, Map.of("profession", "farmer"), 0.0F, 0.0F, 0.0F, false));
        table.put(head);
        Player surgeon = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        ItemStack blade = new ItemStack(BBItems.CLEAVER.get());
        Surgery.harvest(level, surgeon, table, blade);
        MinionStats oneEye = MinionStats.of(PartsData.SERVER, armed(PieceRef.of(CarcassPieceItem.piece(table.item()))));
        Surgery.harvest(level, surgeon, table, blade);
        MinionStats blind = MinionStats.of(PartsData.SERVER, armed(PieceRef.of(CarcassPieceItem.piece(table.item()))));
        if (!oneEye.jobs().equals(List.of(job("surgeon"), job("farmer"))) || !(oneEye.sight() > MinionStats.BLIND_SIGHT)) {
            helper.fail("One eye out should change nothing: " + oneEye.jobs() + ", sight " + oneEye.sight());
            return;
        }
        if (!blind.jobs().equals(List.of(MinionStats.COMPANION)) || blind.sight() != MinionStats.BLIND_SIGHT) {
            helper.fail("A farmer's head with both eyes out should lose surgeon and farmer, and see 4 blocks: " + blind.jobs() + ", sight " + blind.sight());
            return;
        }
        Map<String, String> eyesOut = Map.of("profession", "fisherman", Surgery.ORGANS_TAKEN, "2");
        MinionStats fisher = MinionStats.of(PartsData.SERVER, armed(ref("villager", "head", eyesOut)));
        MinionStats zombie = MinionStats.of(PartsData.SERVER, armed(ref("zombie", "head", Map.of(Surgery.ORGANS_TAKEN, "2"))));
        MinionStats seeing = MinionStats.of(PartsData.SERVER, armed(ref("zombie", "head")));
        if (fisher.jobs().contains(job("fisher")) || !zombie.jobs().equals(seeing.jobs()) || zombie.sight() != MinionStats.BLIND_SIGHT
                || !(seeing.sight() > 8.0F)) {
            helper.fail("A blind fisherman loses fishing; a blind zombie keeps its jobs but sees 4 blocks: " + fisher.jobs() + " " + zombie.jobs()
                    + " sight " + zombie.sight() + " (seeing " + seeing.sight() + ")");
            return;
        }
        helper.succeed();
    }

    // ---- the jobs at work

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
        if (!switchTo(minion, maker, "sentry")) {
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
        if (!minion.getMainHandItem().is(Items.CROSSBOW) || count(minion, Items.ARROW) != 16 || !switchTo(minion, maker, "sentry")) {
            helper.fail("Holding a crossbow and carrying the arrows, a pillager's head should take up sentry: " + minion.getMainHandItem() + ", "
                    + count(minion, Items.ARROW) + " arrows, " + minion.job());
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

    /** A scavenger holding an amethyst shard fetches the shards lying across its pen for its maker, and leaves the stick. */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void scavengerFetchesMatchingItem(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionBuild build = armed(ref("chicken", "head"));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), build, maker);
        give(minion, maker, new ItemStack(Items.AMETHYST_SHARD));
        if (!minion.hasJob("scavenger")) {
            helper.fail("A chicken's head should make a scavenger: " + minion.job());
            return;
        }
        BlockPos far = helper.absolutePos(new BlockPos(8, 2, 8));
        level.addFreshEntity(new ItemEntity(level, far.getX() + 0.5, far.getY() + 0.2, far.getZ() + 0.5, new ItemStack(Items.AMETHYST_SHARD, 3)));
        BlockPos other = helper.absolutePos(new BlockPos(8, 2, 2));
        ItemEntity stick = new ItemEntity(level, other.getX() + 0.5, other.getY() + 0.2, other.getZ() + 0.5, new ItemStack(Items.STICK));
        level.addFreshEntity(stick);
        helper.succeedWhen(() -> {
            helper.assertTrue(maker.getInventory().countItem(Items.AMETHYST_SHARD) == 3, "its maker has "
                    + maker.getInventory().countItem(Items.AMETHYST_SHARD) + " of the 3 shards (it carries " + count(minion, Items.AMETHYST_SHARD) + ")");
            helper.assertTrue(stick.isAlive() && maker.getInventory().countItem(Items.STICK) == 0 && count(minion, Items.STICK) == 0,
                    "it should leave the stick alone");
            helper.assertTrue(minion.getMainHandItem().is(Items.AMETHYST_SHARD), "it should still hold its own shard");
        });
    }

    /**
     * A fisherman's head with a rod in hand walks to the pool by home and fishes: a catch in what it carries, a point off
     * the rod. A cod's head fishes with its mouth, no rod needed; a fisherman's head without one cannot.
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
        if (MinionJobs.offered(minion).contains(job("fisher"))) {
            helper.fail("With no rod, a fisherman's head should not be offered fishing");
            return;
        }
        MinionEntity cod = minion(helper, new BlockPos(2, 2, 8), armless(ref("cod", "head")), maker);
        if (!MinionJobs.offered(cod).contains(job("fisher"))) {
            helper.fail("A cod's head fishes with its mouth: " + MinionJobs.offered(cod));
            return;
        }
        cod.discard();
        give(minion, maker, new ItemStack(Items.FISHING_ROD));
        if (!switchTo(minion, maker, "fisher")) {
            helper.fail("With a rod, it should take up fishing");
            return;
        }
        // no half-minute wait in a test: the first catch comes as soon as it is at the water
        MinionJobs.hurry(minion);
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
     * A hunter (a wolf's head, which wakes as a guard: never straight to the animals round its table) with a Meat Hook in
     * hand kills a cow in its pen, and the cow is left an intact carcass, as a player's Meat Hook kill leaves it: no beef
     * on the ground.
     */
    @GameTest(template = "empty", timeoutTicks = 500)
    public static void hunterWithMeatHookLeavesCarcass(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Set<UUID> before = new HashSet<>();
        CarcassSavedData.get(level).all().forEach(c -> before.add(c.id));
        Maker maker = new Maker(helper, new BlockPos(1, 2, 1));
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 2), armed(ref("wolf", "head/real_head")), maker);
        if (minion.hasJob("hunter") || !switchTo(minion, maker, "hunter")) {
            helper.fail("A wolf's head should offer hunting, but not wake to it: " + minion.job() + " of " + MinionJobs.offered(minion));
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
        if (!switchTo(minion, maker, "hauler")) {
            helper.fail("A horse's head should offer hauling: " + MinionJobs.offered(minion));
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

    /** A horse on its own legs, its own head on: a hauler (its head offers it; its torso's hauler trait eases the drag). */
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
        if (!switchTo(minion, maker, "hauler")) {
            helper.fail("A leatherworker's head should offer hauling: " + MinionJobs.offered(minion));
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
     * A butcher's head with a Cleaver in hand takes a cow carcass by home apart by hand: a leg cut off and broken down,
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
        if (MinionJobs.offered(minion).contains(job("butcher"))) {
            helper.fail("With no blade, a butcher's head should not be offered butchering");
            return;
        }
        give(minion, maker, new ItemStack(BBItems.CLEAVER.get()));
        if (!switchTo(minion, maker, "butcher")) {
            helper.fail("With a Cleaver in hand, it should take up butchering");
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
        if (!switchTo(minion, maker, "medic")) {
            helper.fail("A cleric's head should offer medic: " + MinionJobs.offered(minion));
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
                    + minion.inventory.countItem(Items.SPLASH_POTION) + " left, the medic at " + minion.position() + ", " + minion.job() + ")");
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
        if (!minion.hasJob("barterer")) {
            helper.fail("A piglin's head should make a barterer: " + minion.job());
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
        if (!minion.hasJob("digger")) {
            helper.fail("A sniffer's head should make a digger: " + minion.job());
            return;
        }
        // no minute's wait in a test: the first find comes as soon as it is at the grass
        MinionJobs.hurry(minion);
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
        if (!minion.hasJob("herder")) {
            helper.fail("A cow's head should make a herder: " + minion.job());
            return;
        }
        give(minion, maker, new ItemStack(Items.WHEAT));
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(9, 2, 9));
        // left to itself it strolls only about where it stands
        cow.restrictTo(cow.blockPosition(), 1);
        Vec3 home = Vec3.atBottomCenterOf(minion.home());
        helper.succeedWhen(() -> helper.assertTrue(cow.position().distanceTo(home) < MinionJobs.HERD_HOME,
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
        if (!minion.cybernetic() || !switchTo(minion, maker, "hunter")) {
            helper.fail("A brass wolf's head should make a hunter: " + minion.job());
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
     * A brass scavenger with nothing in its hand fetches whatever its filter passes: an Attribute Filter for food brings
     * its maker the apples, and the stick lying nearer stays where it is.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void filteredScavengerFetchesWhatItPasses(GameTestHelper helper) {
        pen(helper);
        ServerLevel level = helper.getLevel();
        Maker maker = new Maker(helper, new BlockPos(2, 2, 2));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), brassArmed(ref("chicken", "head")), maker);
        if (!minion.setJob(job("scavenger"))) {
            helper.fail("A chicken's head should offer scavenger: " + MinionJobs.offered(minion));
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
}
