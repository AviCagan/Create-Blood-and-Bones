package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity;
import com.avicagan.bloodandbones.minion.MinionAssembly;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Brass minions (docs/PARTS-AND-TRAITS.md section 6.6 and 6.7): skinned pieces under Brass Sheathing, woken and kept
 * running on soul blood in canisters that a Spout fills and a Charging Cradle swaps in. Each kind has what the other
 * lacks: brass drains a quarter as fast and shrugs off poison, but never heals itself; flesh mends on its blood.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BrassMinionTests {
    private static PieceRef ref(String entity, String bone, boolean skinned) {
        return new PieceRef(ResourceLocation.withDefaultNamespace(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + entity + ".png"),
                List.of(), 1.0F, skinned, Map.of(), false);
    }

    private static MinionBuild brassCow() {
        return new MinionBuild(true, ref("cow", "body", true), List.of(), true).with("head", ref("cow", "head", true))
                .with("right_front_leg", ref("cow", "right_front_leg", true)).with("left_front_leg", ref("cow", "left_front_leg", true))
                .with("right_hind_leg", ref("cow", "right_hind_leg", true)).with("left_hind_leg", ref("cow", "left_hind_leg", true));
    }

    private static MinionBuild fleshCow() {
        return MinionBuild.of(ref("cow", "body", false)).with("head", ref("cow", "head", false))
                .with("right_front_leg", ref("cow", "right_front_leg", false)).with("left_front_leg", ref("cow", "left_front_leg", false));
    }

    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build, float power) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(helper.makeMockPlayer(GameType.SURVIVAL), at, build, power);
        level.addFreshEntity(minion);
        return minion;
    }

    /** A Spout fills an empty canister with 1000 mB of soul blood; an Item Drain takes it back out: plain Create recipes. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void spoutFillsCanister(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        var filling = recipes.byKey(BloodAndBones.asResource("soul_canister_filling"));
        var emptying = recipes.byKey(BloodAndBones.asResource("soul_canister_emptying"));
        if (filling.isEmpty() || filling.get().value().getType() != AllRecipeTypes.FILLING.getType()
                || !filling.get().value().getResultItem(helper.getLevel().registryAccess()).is(BBItems.SOUL_CANISTER.get())) {
            helper.fail("A Spout should fill the empty canister into a Soul Canister");
            return;
        }
        if (emptying.isEmpty() || emptying.get().value().getType() != AllRecipeTypes.EMPTYING.getType()) {
            helper.fail("An Item Drain should empty a Soul Canister");
            return;
        }
        helper.succeed();
    }

    /**
     * A skinned torso laid on the frame is brass: it takes only skinned pieces, will not wake on blood or before its
     * sheathing, and wakes on a Soul Canister (the empty comes back) with a canister's worth in it.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void brassFrameWakesOnCanister(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(3, 2, 3);
        helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.ASSEMBLY));
        SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) helper.getBlockEntity(at);
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack torso = new ItemStack(BBItems.CARCASS_PIECE.get());
        torso.set(BBDataComponents.PIECE.get(), ref("cow", "body", true).toPiece());
        if (!MinionAssembly.layDown(table, torso, level) || !table.build().orElseThrow().cybernetic()) {
            helper.fail("A skinned torso should make a brass frame");
            return;
        }
        ItemStack hide = new ItemStack(BBItems.CARCASS_PIECE.get());
        hide.set(BBDataComponents.PIECE.get(), ref("cow", "head", false).toPiece());
        if (MinionAssembly.fit(level, table, hide) == null) {
            helper.fail("A brass frame takes only skinned pieces");
            return;
        }
        if (MinionAssembly.wake(level, maker, table, new ItemStack(BBFluids.BLOOD.getBucket().get())) != null
                || MinionAssembly.wake(level, maker, table, new ItemStack(BBItems.SOUL_CANISTER.get())) != null) {
            helper.fail("Brass does not wake on blood, nor before it is sheathed");
            return;
        }
        if (!MinionAssembly.sheathe(table) || !table.build().orElseThrow().sheathed()) {
            helper.fail("Brass Sheathing should go on");
            return;
        }
        MinionEntity minion = MinionAssembly.wake(level, maker, table, new ItemStack(BBItems.SOUL_CANISTER.get()));
        if (minion == null || !minion.cybernetic() || minion.power() != MinionStats.CANISTER || maker.getInventory().countItem(BBItems.EMPTY_SOUL_CANISTER.get()) != 1) {
            helper.fail("A Soul Canister should wake it brass, full, the empty coming back");
            return;
        }
        helper.succeed();
    }

    /** Brass drains a quarter as fast as flesh doing the same. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void brassDrainsAQuarter(GameTestHelper helper) {
        MinionEntity brass = minion(helper, new BlockPos(2, 2, 2), brassCow(), 500.0F);
        MinionEntity flesh = minion(helper, new BlockPos(7, 2, 7), fleshCow(), 500.0F);
        brass.setNoAi(true);
        flesh.setNoAi(true);
        helper.runAfterDelay(280, () -> {
            float brassUsed = 500.0F - brass.power();
            float fleshUsed = 500.0F - flesh.power();
            if (!(fleshUsed > 0.0F) || Math.abs(brassUsed - fleshUsed * MinionEntity.BRASS_DRAIN) > fleshUsed * 0.2F + 0.06F) {
                helper.fail("Brass should use a quarter of what flesh uses: " + brassUsed + " vs " + fleshUsed);
                return;
            }
            helper.succeed();
        });
    }

    /** A turning cradle stocked with a canister wakes a brass minion powered down beside it, and keeps the empty. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void cradleRevivesPoweredDown(GameTestHelper helper) {
        BlockPos motor = new BlockPos(4, 1, 4);
        BlockPos at = motor.above();
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(at, BBBlocks.CHARGING_CRADLE.getDefaultState());
        if (helper.getLevel().getBlockEntity(helper.absolutePos(motor)) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(64);
        }
        ChargingCradleBlockEntity cradle = (ChargingCradleBlockEntity) helper.getBlockEntity(at);
        cradle.inventory.insertItem(0, new ItemStack(BBItems.SOUL_CANISTER.get()), false);
        MinionEntity minion = minion(helper, new BlockPos(5, 2, 5), brassCow(), 10.0F);
        minion.powerDown();
        helper.succeedWhen(() -> {
            helper.assertTrue(!minion.poweredDown() && minion.power() >= MinionStats.CANISTER - 1.0F, "the cradle has not swapped a canister in yet");
            helper.assertTrue(cradle.fullCanisters() == 0 && cradle.inventory.getStackInSlot(ChargingCradleBlockEntity.FULL).is(BBItems.EMPTY_SOUL_CANISTER.get()),
                    "the empty should stay in the cradle");
        });
    }

    /** Pipes of items: full canisters and sheets go in, empties come out, and nothing else either way. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cradleAutomation(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 2, 3), BBBlocks.CHARGING_CRADLE.getDefaultState());
        ChargingCradleBlockEntity cradle = (ChargingCradleBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3));
        IItemHandler handler = cradle.automation();
        ItemStack rest = new ItemStack(BBItems.SOUL_CANISTER.get());
        for (int i = 0; i < handler.getSlots() && !rest.isEmpty(); i++) {
            rest = handler.insertItem(i, rest, false);
        }
        ItemStack empty = new ItemStack(BBItems.EMPTY_SOUL_CANISTER.get());
        for (int i = 0; i < handler.getSlots() && !empty.isEmpty(); i++) {
            empty = handler.insertItem(i, empty, false);
        }
        ItemStack sheet = new ItemStack(AllItems.BRASS_SHEET.get(), 5);
        for (int i = 0; i < handler.getSlots() && !sheet.isEmpty(); i++) {
            sheet = handler.insertItem(i, sheet, false);
        }
        if (!rest.isEmpty() || empty.isEmpty() || !sheet.isEmpty() || cradle.fullCanisters() != 1) {
            helper.fail("A full canister and sheets go in; an empty canister does not");
            return;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.extractItem(i, 64, true).isEmpty()) {
                helper.fail("Nothing but empties comes out, and there are none");
                return;
            }
        }
        helper.succeed();
    }

    /** Brass shrugs off poison and mends with a brass sheet by hand; flesh mends on its own blood. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void eachKindHasWhatTheOtherLacks(GameTestHelper helper) {
        MinionEntity brass = minion(helper, new BlockPos(2, 2, 2), brassCow(), 500.0F);
        MinionEntity flesh = minion(helper, new BlockPos(7, 2, 7), fleshCow(), 500.0F);
        brass.setNoAi(true);
        flesh.setNoAi(true);
        if (brass.addEffect(new MobEffectInstance(MobEffects.POISON, 100)) || !flesh.addEffect(new MobEffectInstance(MobEffects.POISON, 100))) {
            helper.fail("Brass should not take poison; flesh should");
            return;
        }
        flesh.removeAllEffects();
        brass.setHealth(brass.getMaxHealth() - 12.0F);
        flesh.setHealth(flesh.getMaxHealth() - 4.0F);
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        maker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(AllItems.BRASS_SHEET.get()));
        brass.interact(maker, InteractionHand.MAIN_HAND);
        if (Math.abs(brass.getHealth() - (brass.getMaxHealth() - 2.0F)) > 0.01F || !maker.getMainHandItem().isEmpty()) {
            helper.fail("A brass sheet should mend 10 health on brass");
            return;
        }
        float brassHealth = brass.getHealth();
        float fleshHealth = flesh.getHealth();
        float fleshBlood = flesh.power();
        helper.runAfterDelay(MinionEntity.REGEN_TICKS * 2 + 5, () -> {
            if (!(flesh.getHealth() > fleshHealth) || !(flesh.power() < fleshBlood - MinionEntity.REGEN_COST + 0.01F)) {
                helper.fail("Flesh should mend itself, paying blood: " + flesh.getHealth() + " " + flesh.power());
                return;
            }
            if (brass.getHealth() > brassHealth) {
                helper.fail("Brass never heals itself");
                return;
            }
            helper.succeed();
        });
    }
}
