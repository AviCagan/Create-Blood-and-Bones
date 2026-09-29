package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionFitness;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.MinionTask;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * The brief's amputation ritual: a player's flesh comes off only with a surgeon minion (by default any minion on the
 * Surgeon task with a hand; with the surgeon file's switch, only a villager's, an illager's or a witch's head) awake at the
 * table; its cut leaves a ragged stump that costs one, two or three buckets of blood to fit by how fit the surgeon was
 * (docs/NEXT.md 1.5); and nothing about fitting ever needs a surgeon, so the safety floor is always in reach.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class SurgeonTests {
    private static PieceRef ref(String entity, String bone) {
        return new PieceRef(ResourceLocation.withDefaultNamespace(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + entity + ".png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    private static SurgeryTableBlockEntity table(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
        return (SurgeryTableBlockEntity) helper.getBlockEntity(at);
    }

    private static int count(Player player, net.minecraft.world.item.Item item) {
        return player.getInventory().countItem(item);
    }

    /**
     * A surgeon as {@link MinionTests#surgeon} makes one (a zombie's torso, one arm and legs, still), with this head in its
     * villager's place, or none.
     */
    private static MinionEntity surgeonWith(GameTestHelper helper, BlockPos at, @org.jetbrains.annotations.Nullable PieceRef head) {
        MinionEntity surgeon = MinionTests.surgeon(helper, at);
        MinionBuild build = MinionBuild.of(ref("zombie", "body")).with("right_arm", ref("zombie", "right_arm")).with("left_leg", ref("zombie", "left_leg"))
                .with("right_leg", ref("zombie", "right_leg"));
        surgeon.setBuild(head == null ? build : build.with("head", head));
        // the test's floor sees the sky, and a zombie's torso burns by day unless something is on its head
        surgeon.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return surgeon;
    }

    /** The translation key a message is, or "". */
    private static String key(@org.jetbrains.annotations.Nullable net.minecraft.network.chat.Component text) {
        return text != null && text.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t ? t.getKey() : "";
    }

    /** A Fluid Backtank of this tier holding this much blood. */
    private static ItemStack backtank(com.avicagan.bloodandbones.backtank.BacktankTier tier, int blood) {
        ItemStack tank = new ItemStack(BBItems.backtank(tier));
        com.avicagan.bloodandbones.backtank.FluidBacktankItem.setFluid(tank, new net.neoforged.neoforge.fluids.FluidStack(BBFluids.blood(), blood));
        return tank;
    }

    private static int tankBlood(Player player) {
        return com.avicagan.bloodandbones.backtank.FluidBacktankItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
    }

    /**
     * A Cleaver on the table does nothing to a player's arm with no surgeon there, nor with one out of blood, nor
     * with one across the room, nor with a minion beside it on any other task; nor does a prosthetic swapped straight in for
     * it. With a surgeon awake beside the table, the arm comes off and leaves a ragged stump.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void amputationNeedsSurgeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        table.put(new ItemStack(BBItems.HOOK_HAND.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.NONE || BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.NATURAL
                || !table.item().is(BBItems.HOOK_HAND.get())) {
            helper.fail("With no surgeon a Hook Hand should not be swapped in for the arm: that cuts flesh too");
            return;
        }
        table.take();
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE || BodyEffects.body(player).state(BodyPart.LEFT_ARM) != Body.State.NATURAL) {
            helper.fail("With no surgeon the arm should stay");
            return;
        }
        MinionEntity surgeon = MinionTests.surgeon(helper, new BlockPos(4, 2, 4));
        surgeon.powerDown();
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE) {
            helper.fail("A surgeon out of blood cannot operate");
            return;
        }
        surgeon.feed(500.0F);
        BlockPos far = helper.absolutePos(new BlockPos(10, 2, 10));
        surgeon.moveTo(far.getX() + 0.5, far.getY(), far.getZ() + 0.5);
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE) {
            helper.fail("A surgeon across the room cannot operate");
            return;
        }
        BlockPos near = helper.absolutePos(new BlockPos(4, 2, 4));
        surgeon.moveTo(near.getX() + 0.5, near.getY(), near.getZ() + 0.5);
        surgeon.setTask(com.avicagan.bloodandbones.minion.MinionTask.FARMER);
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE) {
            helper.fail("A minion by the table on another task (farming) is no surgeon");
            return;
        }
        surgeon.setTask(com.avicagan.bloodandbones.minion.MinionTask.SURGEON);
        Body body;
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.TAKE_OFF
                || (body = BodyEffects.body(player)).state(BodyPart.LEFT_ARM) != Body.State.MISSING || !body.ragged(BodyPart.LEFT_ARM)
                || body.raggedBuckets(BodyPart.LEFT_ARM) != 1 || count(player, BBItems.SEVERED_ARM.get()) != 1) {
            helper.fail("With a surgeon by the table the arm should come off, leaving a ragged stump of a bucket (a villager's head cuts cleanest)");
            return;
        }
        helper.succeed();
    }

    /**
     * Any minion set to Surgeon with a hand cuts (docs/NEXT.md 1.5, the default), and its fitness sets the stump's price. A
     * zombie-headed surgeon (141%) leaves a stump of two buckets; a villager-headed one (200%) cuts first when both are by
     * the table, and leaves one; a headless one (12%) leaves three.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void anySurgeonWithAHandCuts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        MinionEntity zombie = surgeonWith(helper, new BlockPos(4, 2, 4), ref("zombie", "head"));
        if (Surgery.surgeonAt(level, at) != zombie || Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.TAKE_OFF
                || BodyEffects.body(player).raggedBuckets(BodyPart.LEFT_ARM) != 2) {
            helper.fail("A zombie's head with a hand should cut, leaving a stump of two buckets: " + zombie.fitness(MinionTask.SURGEON) + ", "
                    + BodyEffects.body(player));
            return;
        }
        MinionEntity villager = MinionTests.surgeon(helper, new BlockPos(2, 2, 4));
        if (Surgery.surgeonAt(level, at) != villager || Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.TAKE_OFF
                || BodyEffects.body(player).raggedBuckets(BodyPart.RIGHT_ARM) != 1) {
            helper.fail("With both by the table the villager's head should cut, leaving a stump of one bucket: " + BodyEffects.body(player));
            return;
        }
        zombie.discard();
        villager.discard();
        MinionEntity headless = surgeonWith(helper, new BlockPos(4, 2, 2), null);
        if (Surgery.surgeonAt(level, at) != headless || Surgery.operate(level, player, table, BodyPart.LEFT_LEG) != Surgery.Action.TAKE_OFF
                || BodyEffects.body(player).raggedBuckets(BodyPart.LEFT_LEG) != 3) {
            helper.fail("A headless surgeon should cut too, leaving a stump of three buckets: " + headless.fitness(MinionTask.SURGEON) + ", "
                    + BodyEffects.body(player));
            return;
        }
        // what the surgery screen is told before any cut: each surgeon's fitness and its stumps' price
        if (zombie.shownStump() != 2 || villager.shownStump() != 1 || headless.shownStump() != 3 || villager.shownFitness() != MinionFitness.MOST
                || Math.abs(zombie.shownFitness() - zombie.fitness(MinionTask.SURGEON)) > 1.0E-6F) {
            helper.fail("Clients should be told each surgeon's fitness and price: " + zombie.shownStump() + ", " + villager.shownStump() + ", "
                    + headless.shownStump());
            return;
        }
        helper.succeed();
    }

    /**
     * The zombie villager's shaky hands (bb-organs' signature, docs/PARTS-AND-TRAITS.md 8.2) are its head's surgeon knack of
     * 0.5 over its family's 1.5: with a zombie's arm it operates at 88%, so its stumps cost two buckets, and it tends a heart
     * every 5.7 s where a villager's head, at 200%, tends one every 2.5.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void shakySurgeonReadsItsKnack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        MinionEntity shaky = surgeonWith(helper, new BlockPos(4, 2, 4), new PieceRef(ResourceLocation.withDefaultNamespace("zombie_villager"), "head",
                ResourceLocation.withDefaultNamespace("textures/entity/zombie_villager/zombie_villager.png"), List.of(), 1.0F, false, Map.of("profession", "farmer"), false));
        var data = PartsData.of(level).task(MinionTask.SURGEON);
        float fitness = shaky.fitness(MinionTask.SURGEON);
        if (shaky.stats().knack(MinionTask.SURGEON.id) != 0.5F || Math.abs(fitness - 0.884F) > 0.01F || MinionFitness.tendTicks(data, fitness) != 113
                || MinionFitness.tendTicks(data, 2.0F) != 50) {
            helper.fail("The shaky surgeon should read its knack of 0.5: " + shaky.stats().knack(MinionTask.SURGEON.id) + ", " + fitness + ", tending every "
                    + MinionFitness.tendTicks(data, fitness));
            return;
        }
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.TAKE_OFF || BodyEffects.body(player).raggedBuckets(BodyPart.LEFT_ARM) != 2) {
            helper.fail("Its shaky hands should leave a stump of two buckets: " + BodyEffects.body(player));
            return;
        }
        helper.succeed();
    }

    /**
     * The owner's call (docs/NEXT.md 1.5), at the table both ways. By default ({@code "needs_surgeon_head": false}, as the
     * shipped file says) a zombie's head with a hand cuts, leaving a stump of two buckets. With the surgeon task's
     * {@code "needs_surgeon_head": true} (the brief's letter) the zombie is passed over and nothing is cut, though it may
     * still be set to Surgeon and tend; a villager's head cuts, its stump one bucket. The data is changed and set back within
     * the one tick, since the tests share one world.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void surgeonHeadIsTheOwnersCall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos tableAt = new BlockPos(3, 2, 3);
        SurgeryTableBlockEntity bench = table(helper, tableAt);
        BlockPos table = helper.absolutePos(tableAt);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        bench.put(new ItemStack(BBItems.CLEAVER.get()));
        PartsData.Store store = PartsData.of(level);
        var any = store.task(MinionTask.SURGEON);
        if (any.needsSurgeonHead()) {
            helper.fail("The shipped surgeon file should leave the cutting to any minion with a hand");
            return;
        }
        MinionEntity zombie = surgeonWith(helper, new BlockPos(4, 2, 4), ref("zombie", "head"));
        if (Surgery.surgeonAt(level, table) != zombie || Surgery.operate(level, player, bench, BodyPart.LEFT_ARM) != Surgery.Action.TAKE_OFF
                || BodyEffects.body(player).raggedBuckets(BodyPart.LEFT_ARM) != 2) {
            helper.fail("By default a zombie's head with a hand should cut, its stump two buckets");
            return;
        }
        store.setTestTask(MinionTask.SURGEON, any.read(com.google.gson.JsonParser.parseString("{\"needs_surgeon_head\": true}").getAsJsonObject()));
        try {
            if (Surgery.surgeonAt(level, table) != null || Surgery.operate(level, player, bench, BodyPart.RIGHT_ARM) != Surgery.Action.NONE
                    || BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.NATURAL) {
                helper.fail("With needs_surgeon_head a zombie's head should not cut");
                return;
            }
            if (!zombie.hasTask(MinionTask.SURGEON) || !zombie.row(MinionTask.SURGEON, MinionTask.Anchor.HOME).can()) {
                helper.fail("With needs_surgeon_head any minion with a hand may still be set to Surgeon, to tend");
                return;
            }
            MinionEntity villager = MinionTests.surgeon(helper, new BlockPos(2, 2, 4));
            if (Surgery.surgeonAt(level, table) != villager || Surgery.operate(level, player, bench, BodyPart.RIGHT_ARM) != Surgery.Action.TAKE_OFF
                    || BodyEffects.body(player).raggedBuckets(BodyPart.RIGHT_ARM) != 1) {
                helper.fail("With needs_surgeon_head a villager's head should still cut, its stump a bucket");
                return;
            }
            villager.discard();
        } finally {
            store.setTestTask(MinionTask.SURGEON, null);
        }
        if (Surgery.surgeonAt(level, table) != zombie) {
            helper.fail("Set back, the zombie's head should cut again");
            return;
        }
        helper.succeed();
    }

    /**
     * Fitting anything but a crude prosthetic into a ragged stump takes its price in blood as well, and the price comes from
     * the surgeon that cut it (docs/NEXT.md 1.5): a zombie-headed surgeon's stump costs two buckets, so with none, or one,
     * nothing happens and nothing is taken; with two it goes on and both come back empty. The stump is dressed, so taking the
     * implant out and fitting again is free. Putting the limb itself back into a villager-headed surgeon's stump costs its one
     * bucket.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void raggedStumpCostsBlood(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity zombie = surgeonWith(helper, new BlockPos(4, 2, 4), ref("zombie", "head"));
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.TAKE_OFF || BodyEffects.body(player).raggedBuckets(BodyPart.RIGHT_ARM) != 2) {
            helper.fail("The zombie-headed surgeon's stump should cost two buckets");
            return;
        }
        zombie.discard();
        table.take();
        player.getInventory().clearContent();
        table.put(new ItemStack(BBItems.FLESH_ARM.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.NONE || !table.item().is(BBItems.FLESH_ARM.get())) {
            helper.fail("With no blood a Flesh Arm should not go into a ragged stump");
            return;
        }
        player.getInventory().add(new ItemStack(BBFluids.BLOOD.getBucket().get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.NONE || count(player, BBFluids.BLOOD.getBucket().get()) != 1) {
            helper.fail("With one bucket of the two it should not fit, and the bucket should be left full");
            return;
        }
        player.getInventory().add(new ItemStack(BBFluids.BLOOD.getBucket().get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.FIT || count(player, BBFluids.BLOOD.getBucket().get()) != 0
                || count(player, Items.BUCKET) != 2) {
            helper.fail("With two buckets of blood it should fit, both coming back empty");
            return;
        }
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.UNCLIP || BodyEffects.body(player).ragged(BodyPart.RIGHT_ARM)) {
            helper.fail("Taken out again, the stump should be dressed, not ragged");
            return;
        }
        ItemStack arm = player.getInventory().items.stream().filter(s -> s.is(BBItems.FLESH_ARM.get())).findFirst().orElseThrow();
        table.put(arm.split(1));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.FIT) {
            helper.fail("A dressed stump takes the Flesh Arm with no blood");
            return;
        }
        MinionEntity villager = MinionTests.surgeon(helper, new BlockPos(4, 2, 4));
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.TAKE_OFF || BodyEffects.body(player).raggedBuckets(BodyPart.LEFT_ARM) != 1) {
            helper.fail("The villager-headed surgeon's stump should cost one bucket");
            return;
        }
        villager.discard();
        table.take();
        table.put(BBItems.partItem(BodyPart.Kind.ARM).of(player));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE || BodyEffects.body(player).state(BodyPart.LEFT_ARM) != Body.State.MISSING) {
            helper.fail("With no blood the arm should not go back into a ragged stump");
            return;
        }
        player.getInventory().add(new ItemStack(BBFluids.BLOOD.getBucket().get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.REATTACH || count(player, BBFluids.BLOOD.getBucket().get()) != 0
                || BodyEffects.body(player).state(BodyPart.LEFT_ARM) != Body.State.NATURAL) {
            helper.fail("With a bucket of blood the arm should go back on, the bucket used");
            return;
        }
        helper.succeed();
    }

    /**
     * The blood for a ragged stump can come from a worn Fluid Backtank too, and its price from the surgeon that cut it: a
     * headless surgeon's stump costs three buckets, which come out of an iron backtank of 3500 mB, leaving 500.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void raggedStumpPaidFromBacktank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        MinionEntity headless = surgeonWith(helper, new BlockPos(4, 2, 4), null);
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_LEG) != Surgery.Action.TAKE_OFF || BodyEffects.body(player).raggedBuckets(BodyPart.LEFT_LEG) != 3) {
            helper.fail("A headless surgeon's stump should cost three buckets");
            return;
        }
        headless.discard();
        table.take();
        player.setItemSlot(EquipmentSlot.CHEST, backtank(com.avicagan.bloodandbones.backtank.BacktankTier.IRON, 3500));
        table.put(new ItemStack(BBItems.SINEW_LEG.get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_LEG) != Surgery.Action.FIT) {
            helper.fail("The backtank's blood should pay for the ragged stump");
            return;
        }
        if (tankBlood(player) != 500) {
            helper.fail("Three buckets' worth should come out of the backtank, leaving 500 mB: " + tankBlood(player));
            return;
        }
        helper.succeed();
    }

    /**
     * A stump's price is paid from all the blood the operator carries, added together (docs/NEXT.md 1.5): a two-bucket stump
     * takes a bucket and a backtank's thousand. One short (a bucket and 500 mB) is refused with "Needs 2 buckets of blood",
     * and nothing is taken.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void raggedStumpPaidAcrossContainers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BodyEffects.body(player).lose(BodyPart.LEFT_ARM, 2);
        table.put(new ItemStack(BBItems.FLESH_ARM.get()));
        player.getInventory().add(new ItemStack(BBFluids.BLOOD.getBucket().get()));
        player.setItemSlot(EquipmentSlot.CHEST, backtank(com.avicagan.bloodandbones.backtank.BacktankTier.COPPER, 500));
        net.minecraft.network.chat.Component why = Surgery.blocked(level, player, player, at, BodyEffects.body(player), Surgery.Action.FIT, BodyPart.LEFT_ARM,
                table.item());
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.NONE || !"bloodandbones.surgery.needs_blood".equals(key(why))
                || count(player, BBFluids.BLOOD.getBucket().get()) != 1 || tankBlood(player) != 500) {
            helper.fail("A bucket and 500 mB are one short of two buckets: it should be refused for want of blood, nothing taken (" + key(why) + ", "
                    + tankBlood(player) + " mB left)");
            return;
        }
        player.setItemSlot(EquipmentSlot.CHEST, backtank(com.avicagan.bloodandbones.backtank.BacktankTier.COPPER, 1500));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.FIT || count(player, BBFluids.BLOOD.getBucket().get()) != 0
                || count(player, Items.BUCKET) != 1 || tankBlood(player) != 500) {
            helper.fail("A bucket and the backtank together should pay the two buckets, the bucket emptied and 500 mB left in the tank: "
                    + tankBlood(player));
            return;
        }
        helper.succeed();
    }

    /**
     * A body saved before stumps had prices kept a list of its ragged parts (docs/NEXT.md 1.8): each reads as a stump of one
     * bucket, the price it had then, and a Flesh Arm goes into it for one bucket. Saved again, each stump keeps its own price.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void oldRaggedStumpIsOneBucket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.nbt.CompoundTag old = new net.minecraft.nbt.CompoundTag();
        net.minecraft.nbt.ListTag lost = new net.minecraft.nbt.ListTag();
        lost.add(net.minecraft.nbt.StringTag.valueOf("left_arm"));
        old.put("lost", lost);
        old.put("ragged", lost.copy());
        Body body = Body.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
        if (body.raggedBuckets(BodyPart.LEFT_ARM) != 1 || Surgery.raggedCost(body, Surgery.Action.FIT, BodyPart.LEFT_ARM, new ItemStack(BBItems.FLESH_ARM.get())) != 1000) {
            helper.fail("An old ragged arm should cost one bucket: " + body);
            return;
        }
        Body dearer = body.copy();
        dearer.lose(BodyPart.RIGHT_ARM, 3);
        Body back = Body.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, Body.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, dearer).getOrThrow()).getOrThrow();
        if (!back.equals(dearer) || back.raggedBuckets(BodyPart.LEFT_ARM) != 1 || back.raggedBuckets(BodyPart.RIGHT_ARM) != 3) {
            helper.fail("Saved again, each stump should keep its price: " + back);
            return;
        }
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setData(com.avicagan.bloodandbones.body.BBAttachments.BODY, body);
        table.put(new ItemStack(BBItems.FLESH_ARM.get()));
        player.getInventory().add(new ItemStack(BBFluids.BLOOD.getBucket().get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.FIT || count(player, BBFluids.BLOOD.getBucket().get()) != 0) {
            helper.fail("A Flesh Arm should go into the old stump for one bucket");
            return;
        }
        helper.succeed();
    }

    /**
     * The safety floor: a crude prosthetic goes into a clean empty slot with no surgeon and no blood, and into a
     * surgeon's ragged stump with no blood too (dressing it), even the dearest, three buckets' worth; and swapping an implant
     * straight in for flesh (with the surgeon) leaves no stump at all.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void safetyFloorNeverNeedsSurgeon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BodyEffects.body(player).lose(BodyPart.LEFT_LEG);
        table.put(new ItemStack(BBItems.PEG_LEG.get()));
        if (Surgery.operate(level, player, table, BodyPart.LEFT_LEG) != Surgery.Action.FIT) {
            helper.fail("A Peg Leg should always go into a clean empty slot, surgeon or not");
            return;
        }
        BodyEffects.body(player).lose(BodyPart.LEFT_ARM, 3);
        table.put(new ItemStack(BBItems.HOOK_HAND.get()));
        if (Surgery.raggedCost(BodyEffects.body(player), Surgery.Action.FIT, BodyPart.LEFT_ARM, table.item()) != 0
                || Surgery.operate(level, player, table, BodyPart.LEFT_ARM) != Surgery.Action.FIT || BodyEffects.body(player).ragged(BodyPart.LEFT_ARM)) {
            helper.fail("A Hook Hand should go into a three-bucket stump with no blood, dressing it");
            return;
        }
        MinionTests.surgeon(helper, new BlockPos(4, 2, 4));
        table.put(new ItemStack(BBItems.HOOK_HAND.get()));
        if (Surgery.operate(level, player, table, BodyPart.RIGHT_ARM) != Surgery.Action.REPLACE || BodyEffects.body(player).ragged(BodyPart.RIGHT_ARM)
                || BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.IMPLANT) {
            helper.fail("A Hook Hand swapped straight in for the arm should leave no ragged stump");
            return;
        }
        helper.succeed();
    }

    /** A surgeon set down away from its table walks back to it and keeps there. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void surgeonKeepsToItsTable(GameTestHelper helper) {
        BlockPos tableAt = new BlockPos(2, 2, 2);
        table(helper, tableAt);
        MinionEntity surgeon = MinionTests.surgeon(helper, new BlockPos(9, 2, 9));
        surgeon.setNoAi(false);
        surgeon.setHome(helper.absolutePos(tableAt));
        Vec3 centre = Vec3.atCenterOf(helper.absolutePos(tableAt));
        helper.succeedWhen(() -> helper.assertTrue(surgeon.distanceToSqr(centre) < Surgery.SURGEON_REACH * Surgery.SURGEON_REACH
                && Surgery.surgeonAt(helper.getLevel(), helper.absolutePos(tableAt)) == surgeon, "the surgeon has not got back to its table yet"));
    }

    /**
     * A surgeon folded up at one table and set down beside another, far off, takes the table beside it: it works from
     * where it is set down, and its old table's far-off chunk is never looked at.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void unfoldedSurgeonTakesTheTableBesideIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos first = new BlockPos(1, 2, 1);
        BlockPos second = new BlockPos(9, 2, 9);
        table(helper, first);
        table(helper, second);
        MinionEntity surgeon = MinionTests.surgeon(helper, new BlockPos(2, 2, 2));
        surgeon.setNoAi(false);
        surgeon.setHome(helper.absolutePos(first));
        surgeon.powerDown();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        com.avicagan.bloodandbones.minion.DormantMinionItem.fold(surgeon, maker);
        ItemStack folded = maker.getInventory().items.stream().filter(st -> st.is(BBItems.DORMANT_MINION.get())).findFirst().orElseThrow();
        BlockPos floor = helper.absolutePos(new BlockPos(8, 1, 7));
        folded.getItem().useOn(new net.minecraft.world.item.context.UseOnContext(level, maker, net.minecraft.world.InteractionHand.MAIN_HAND, folded,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(floor), net.minecraft.core.Direction.UP, floor, false)));
        MinionEntity back = level.getEntitiesOfClass(MinionEntity.class, new net.minecraft.world.phys.AABB(floor).inflate(2)).stream().findFirst().orElse(null);
        if (back == null) {
            helper.fail("It should unfold");
            return;
        }
        back.feed(500.0F);
        helper.succeedWhen(() -> helper.assertTrue(back.home().equals(helper.absolutePos(second)),
                "the surgeon has not taken the table beside it yet (home " + back.home() + ")"));
    }
}
