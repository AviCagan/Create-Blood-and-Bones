package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/** Surgery on others: another player, a mob, and a carcass piece's organs. */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class PatientTests {
    private static SurgeryTableBlockEntity table(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState());
        return (SurgeryTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
    }

    private static int count(Player player, net.minecraft.world.item.Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** A surgeon standing by works on another player lying on the table; the arm is the surgeon's to keep. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void surgeryOnAnotherPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        Player patient = helper.makeMockPlayer(GameType.SURVIVAL);
        Player surgeon = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos at = table.getBlockPos();
        surgeon.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
        SurgeryTableBlock.lieDown(level, at, patient);
        if (Surgery.patientAt(level, at) != patient || !Surgery.mayOperate(surgeon, patient, at)) {
            helper.fail("A surgeon beside the table should be able to work on the player lying on it");
            return;
        }
        if (Surgery.operate(level, patient, surgeon, table, BodyPart.LEFT_ARM) != Surgery.Action.TAKE_OFF
                || count(surgeon, BBItems.SEVERED_ARM.get()) != 1 || count(patient, BBItems.SEVERED_ARM.get()) != 0
                || BodyEffects.body(patient).state(BodyPart.LEFT_ARM) != Body.State.MISSING) {
            helper.fail("The patient's arm should come off into the surgeon's hands");
            return;
        }
        surgeon.moveTo(at.getX() + 20.5, at.getY(), at.getZ() + 0.5);
        if (Surgery.mayOperate(surgeon, patient, at)) {
            helper.fail("A surgeon across the room should not be able to operate");
            return;
        }
        patient.stopRiding();
        helper.succeed();
    }

    /** A zombie on the table loses a leg: it walks slower and its leg is named for it. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void mobOnTheTable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
        zombie.setNoAi(true);
        double walk = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double hit = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Player surgeon = helper.makeMockPlayer(GameType.SURVIVAL);
        SurgeryTableBlock.lieDown(level, table.getBlockPos(), zombie);
        if (Surgery.operate(level, zombie, surgeon, table, BodyPart.RIGHT_LEG) != Surgery.Action.TAKE_OFF
                || Surgery.operate(level, zombie, surgeon, table, BodyPart.RIGHT_ARM) != Surgery.Action.TAKE_OFF) {
            helper.fail("A Cleaver should take a zombie's leg and arm off");
            return;
        }
        ItemStack leg = surgeon.getInventory().items.stream().filter(s -> s.is(BBItems.SEVERED_LEG.get())).findFirst().orElse(ItemStack.EMPTY);
        if (Math.abs(zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) - walk * 0.6) > 1.0E-4
                || Math.abs(zombie.getAttributeValue(Attributes.ATTACK_DAMAGE) - hit * 0.5) > 1.0E-4
                || !leg.getHoverName().getString().contains(zombie.getName().getString())) {
            helper.fail("A zombie with a leg and an arm off should walk at 60% and hit half as hard; leg named " + leg.getHoverName().getString());
            return;
        }
        helper.succeed();
    }

    /** A cow's body piece gives a heart, lungs and a stomach to a Cleaver, then nothing; its head, two eyes. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void organsFromACarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        Player surgeon = helper.makeMockPlayer(GameType.SURVIVAL);
        ResourceLocation cow = ResourceLocation.withDefaultNamespace("cow");
        String root = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(cow).orElseThrow().root().name();
        table.put(piece(cow, root));
        ItemStack blade = new ItemStack(BBItems.CLEAVER.get());
        for (int i = 0; i < 3; i++) {
            if (!Surgery.harvest(level, surgeon, table, blade)) {
                helper.fail("Cut " + (i + 1) + " should take an organ out of the cow's body");
                return;
            }
        }
        if (Surgery.harvest(level, surgeon, table, blade) || count(surgeon, BBItems.HEART.get()) != 1 || count(surgeon, BBItems.LUNGS.get()) != 1
                || count(surgeon, BBItems.STOMACH.get()) != 1 || !CarcassPieceItem.piece(table.item()).traits().get(Surgery.ORGANS_TAKEN).equals("3")) {
            helper.fail("A cow's body should give exactly a heart, lungs and a stomach");
            return;
        }
        table.take();
        table.put(piece(cow, "head"));
        Surgery.harvest(level, surgeon, table, blade);
        Surgery.harvest(level, surgeon, table, blade);
        if (count(surgeon, BBItems.EYE.get()) != 2 || Surgery.harvest(level, surgeon, table, blade)) {
            helper.fail("A cow's head should give two eyes and no more");
            return;
        }
        table.take();
        table.put(piece(ResourceLocation.withDefaultNamespace("skeleton"), "body"));
        if (Surgery.harvest(level, surgeon, table, blade)) {
            helper.fail("A skeleton has no organs");
            return;
        }
        helper.succeed();
    }

    /** A Deployer holding a Cleaver over the table takes the organs out of a carcass piece on it. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void deployerTakesOrgans(GameTestHelper helper) {
        BlockPos tablePos = new BlockPos(3, 2, 3);
        BlockPos deployerPos = tablePos.above(2);
        SurgeryTableBlockEntity table = table(helper, tablePos);
        helper.setBlock(tablePos, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.SURGICAL));
        table = (SurgeryTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(tablePos));
        helper.setBlock(deployerPos, com.simibubi.create.AllBlocks.DEPLOYER.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, net.minecraft.core.Direction.DOWN)
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        helper.setBlock(deployerPos.east(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, net.minecraft.core.Direction.WEST));
        if (helper.getLevel().getBlockEntity(helper.absolutePos(deployerPos.east())) instanceof com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(128);
        }
        ResourceLocation cow = ResourceLocation.withDefaultNamespace("cow");
        table.put(piece(cow, com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(cow).orElseThrow().root().name()));
        SurgeryTableBlockEntity onTable = table;
        helper.runAfterDelay(2, () -> {
            var deployer = (com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(deployerPos));
            deployer.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(BBItems.CLEAVER.get()));
        });
        helper.succeedWhen(() -> helper.assertTrue(CarcassPieceItem.piece(onTable.item()) != null
                && Surgery.organsTaken(CarcassPieceItem.piece(onTable.item())) == 3, "the deployer has not taken all three organs yet: "
                + (CarcassPieceItem.piece(onTable.item()) == null ? "no piece" : Surgery.organsTaken(CarcassPieceItem.piece(onTable.item())))));
    }

    /** The table's job comes from its attachment: a bare table takes nothing; a Surgical Rig takes tools; an Assembly Frame, a body. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void attachmentsSetTheJob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(3, 2, 3);
        table(helper, rel);
        BlockPos at = helper.absolutePos(rel);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at),
                net.minecraft.core.Direction.UP, at, false);
        java.util.function.Function<ItemStack, net.minecraft.world.ItemInteractionResult> click = stack -> {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            return level.getBlockState(at).useItemOn(stack, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
        };
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) level.getBlockEntity(at);
        click.apply(new ItemStack(BBItems.CLEAVER.get()));
        if (!table.item().isEmpty()) {
            helper.fail("A bare table should take nothing");
            return;
        }
        ItemStack rig = new ItemStack(BBItems.SURGICAL_RIG.get());
        click.apply(rig);
        if (SurgeryTableBlock.attachment(level, at) != com.avicagan.bloodandbones.body.TableAttachment.SURGICAL || !rig.isEmpty()) {
            helper.fail("The Surgical Rig should fit and be used up");
            return;
        }
        click.apply(new ItemStack(BBItems.CLEAVER.get()));
        if (!table.item().is(BBItems.CLEAVER.get())) {
            helper.fail("With the Surgical Rig the table should take a Cleaver");
            return;
        }
        table.take();
        click.apply(new ItemStack(BBItems.ASSEMBLY_FRAME.get()));
        if (SurgeryTableBlock.attachment(level, at) != com.avicagan.bloodandbones.body.TableAttachment.ASSEMBLY || count(player, BBItems.SURGICAL_RIG.get()) != 1) {
            helper.fail("The Assembly Frame should swap in and hand the Surgical Rig back");
            return;
        }
        click.apply(new ItemStack(BBItems.CLEAVER.get()));
        ResourceLocation cow = ResourceLocation.withDefaultNamespace("cow");
        click.apply(piece(cow, com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(cow).orElseThrow().root().name()));
        if (!table.item().isEmpty() || table.build().map(b -> !b.torso().entity().equals(cow)).orElse(true)) {
            helper.fail("With the Assembly Frame the table should take a carcass body as a minion's torso, not a Cleaver: " + table.item());
            return;
        }
        helper.succeed();
    }

    private static ItemStack piece(ResourceLocation entity, String bone) {
        ItemStack stack = new ItemStack(BBItems.CARCASS_PIECE.get());
        stack.set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(entity, bone,
                ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of(), 1.0F, false, Map.of(), 0.0F, 0.0F, 0.0F, false));
        return stack;
    }
}
