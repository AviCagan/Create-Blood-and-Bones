package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;

@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineTests {
    private static final BlockPos MOTOR = new BlockPos(5, 2, 5);
    private static final BlockPos MACHINE = MOTOR.above();

    /** A creative motor turning the machine at 32 RPM, and a fresh carcass of the mob lying over it. */
    private static UUID setUp(GameTestHelper helper, BlockEntry<?> machine, EntityType<? extends Mob> type) {
        // the machine sits flush in a floor, as it would in a real build
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(MACHINE.offset(x, 0, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(MACHINE, machine.getDefaultState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(MOTOR)) instanceof CreativeMotorBlockEntity motor) {
            // a slow shaft: the carcass has time to settle and fold between strokes
            motor.generatedSpeed.setValue(32);
        }
        Mob mob = helper.spawn(type, MACHINE.above());
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned false");
            return null;
        }
        return carcass.id;
    }

    private static boolean hasJoint(CarcassSavedData.Carcass carcass, String child) {
        return carcass.joints.stream().anyMatch(joint -> joint.child().equals(child));
    }

    private static int held(GameTestHelper helper, Item item) {
        IItemHandler output = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MACHINE), null);
        int count = 0;
        for (int slot = 0; slot < output.getSlots(); slot++) {
            ItemStack stack = output.getStackInSlot(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void machinesTurnAndCostStress(GameTestHelper helper) {
        helper.setBlock(MOTOR, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(MACHINE, BBBlocks.MANGLER.getDefaultState());
        if (BlockStressValues.getImpact(BBBlocks.MANGLER.get()) != 8.0 || BlockStressValues.getImpact(BBBlocks.DEGLOVER.get()) != 4.0) {
            helper.fail("Stress impacts not registered");
        }
        helper.runAfterDelay(10, () -> {
            CarcassMachineBlockEntity be = (CarcassMachineBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(MACHINE));
            if (be.getSpeed() == 0) {
                helper.fail("The Mangler is not turning over a creative motor");
            }
            IItemHandler output = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MACHINE), Direction.NORTH);
            if (output == null || output.insertItem(0, new ItemStack(Items.BEEF), false).isEmpty()) {
                helper.fail("The output must not take items in");
            }
            helper.succeed();
        });
    }

    /** The Guillotine takes limbs off a cow lying on it, but never the head. */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void guillotineTakesLegsNotHead(GameTestHelper helper) {
        UUID id = setUp(helper, BBBlocks.GUILLOTINE, EntityType.COW);
        ServerLevel level = helper.getLevel();
        helper.succeedWhen(() -> {
            CarcassSavedData.Carcass cow = CarcassSavedData.get(level).carcass(id);
            helper.assertTrue(cow != null, "the cow's record is gone");
            // a limb that comes off leaves the cow's record, so count the joints left
            helper.assertTrue(cow.joints.size() < 5, "no limb off yet");
            helper.assertTrue(hasJoint(cow, "head"), "the head came off");
        });
    }

    /**
     * A machine's filter picks what it works on: with a pig's spawn egg in it, a Guillotine leaves a cow
     * lying on it alone; with a cow's egg or a cow piece it takes the cow; with a Create attribute filter
     * set to "is a piece of Cow", too.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void machineFilterPicksTheMob(GameTestHelper helper) {
        UUID id = setUp(helper, BBBlocks.GUILLOTINE, EntityType.COW);
        ServerLevel level = helper.getLevel();
        var machine = (com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity) level.getBlockEntity(helper.absolutePos(MACHINE));
        CarcassSavedData.Carcass cow = CarcassSavedData.get(level).carcass(id);
        if (!machine.accepts(cow)) {
            helper.fail("With no filter the machine should take any carcass");
        }
        machine.filtering.setFilter(new ItemStack(net.minecraft.world.item.Items.PIG_SPAWN_EGG));
        if (machine.accepts(cow)) {
            helper.fail("A pig egg in the filter should pass over a cow");
        }
        helper.runAfterDelay(200, () -> {
            CarcassSavedData.Carcass still = CarcassSavedData.get(level).carcass(id);
            helper.assertTrue(still != null && still.joints.size() == 5, "a guillotine set for pigs cut the cow");
            machine.filtering.setFilter(new ItemStack(net.minecraft.world.item.Items.COW_SPAWN_EGG));
            helper.assertTrue(machine.accepts(still), "a cow egg in the filter should take the cow");
            machine.filtering.setFilter(com.avicagan.bloodandbones.item.CarcassPieceItem.of(still, "head"));
            helper.assertTrue(machine.accepts(still), "a cow piece in the filter should take the cow");
            ItemStack attributeFilter = new ItemStack(com.simibubi.create.AllItems.ATTRIBUTE_FILTER.get());
            attributeFilter.set(com.simibubi.create.AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, java.util.List.of(
                    new com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry(
                            new com.avicagan.bloodandbones.registry.BBItemAttributes.PieceOf(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow")), false)));
            machine.filtering.setFilter(attributeFilter);
            helper.assertTrue(machine.accepts(still), "an attribute filter for cow pieces should take the cow");
            helper.succeed();
        });
    }

    /** The Beheader takes the head off and leaves the legs on. */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void beheaderTakesTheHead(GameTestHelper helper) {
        UUID id = setUp(helper, BBBlocks.BEHEADER, EntityType.COW);
        ServerLevel level = helper.getLevel();
        helper.succeedWhen(() -> {
            CarcassSavedData.Carcass cow = CarcassSavedData.get(level).carcass(id);
            helper.assertTrue(cow != null, "the cow's record is gone");
            helper.assertTrue(!hasJoint(cow, "head"), "the head is still on");
            helper.assertTrue(cow.joints.size() == 4, "more than the head came off: " + cow.joints.size() + " joints left");
        });
    }

    /** The Deglover skins a sheep over it: hide and wool in its output. */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void degloverSkinsASheep(GameTestHelper helper) {
        UUID id = setUp(helper, BBBlocks.DEGLOVER, EntityType.SHEEP);
        ServerLevel level = helper.getLevel();
        helper.succeedWhen(() -> {
            CarcassSavedData.Carcass sheep = CarcassSavedData.get(level).carcass(id);
            helper.assertTrue(sheep != null && sheep.skinned, "not skinned yet");
            helper.assertTrue(held(helper, com.avicagan.bloodandbones.registry.BBItems.RAW_HIDE.get()) + held(helper, Items.WHITE_WOOL) > 0,
                    "nothing in the output");
        });
    }

    /** The Mangler tears a cow apart and grinds the body into meat. */
    @GameTest(template = "empty", timeoutTicks = 1600)
    public static void manglerGrindsACow(GameTestHelper helper) {
        setUp(helper, BBBlocks.MANGLER, EntityType.COW);
        helper.succeedWhen(() -> helper.assertTrue(held(helper, Items.BEEF) > 0,
                "no beef in the output yet"));
    }
}
