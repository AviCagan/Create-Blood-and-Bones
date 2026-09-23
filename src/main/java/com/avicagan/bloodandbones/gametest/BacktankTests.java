package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankBlockEntity;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Fluid Backtank: what each tier holds and gives, and that it keeps its fluid set down and picked up. */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BacktankTests {
    /** Each tier fills to its buckets and no further, from its item handler (what a Spout uses). */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void tiersHoldTheirBuckets(GameTestHelper helper) {
        int[] buckets = {2, 3, 4, 6, 8, 16, 32};
        for (BacktankTier tier : BacktankTier.values()) {
            ItemStack stack = new ItemStack(BBItems.backtank(tier));
            net.neoforged.neoforge.fluids.capability.IFluidHandlerItem handler = stack.getCapability(Capabilities.FluidHandler.ITEM);
            if (handler == null) {
                helper.fail("No fluid handler on the " + tier + " backtank");
                return;
            }
            int filled = handler.fill(new FluidStack(Fluids.WATER, 100_000), IFluidHandler.FluidAction.EXECUTE);
            ItemStack after = handler.getContainer();
            if (filled != buckets[tier.ordinal()] * 1000 || FluidBacktankItem.fluid(after).getAmount() != filled) {
                helper.fail(tier + " backtank took " + filled + " mB, holds " + FluidBacktankItem.fluid(after).getAmount());
                return;
            }
        }
        if (BBItems.backtank(BacktankTier.DIAMOND).getDefense() != 8 || BBItems.backtank(BacktankTier.SOUL_NETHERITE).getToughness() != 3.0F
                || BBItems.backtank(BacktankTier.COPPER).getEquipmentSlot() != net.minecraft.world.entity.EquipmentSlot.CHEST) {
            helper.fail("The backtanks should be chest armour of their tier");
            return;
        }
        helper.succeed();
    }

    /** Set down, it keeps its blood; pipes take it out and refuse water; broken, it drops with what is left. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void setDownAndPickedUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack stack = new ItemStack(BBItems.backtank(BacktankTier.IRON));
        FluidBacktankItem.setFluid(stack, new FluidStack(BBFluids.blood(), 3000));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 3));
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
        BlockPos at = floor.above();
        if (!(level.getBlockEntity(at) instanceof FluidBacktankBlockEntity be) || be.tank().getFluidAmount() != 3000
                || level.getBlockState(at).getValue(com.avicagan.bloodandbones.backtank.FluidBacktankBlock.TIER) != BacktankTier.IRON) {
            helper.fail("The iron backtank should be set down with its 3000 mB of blood");
            return;
        }
        IFluidHandler pipe = level.getCapability(Capabilities.FluidHandler.BLOCK, at, Direction.EAST);
        if (pipe == null || pipe.drain(1000, IFluidHandler.FluidAction.EXECUTE).getAmount() != 1000
                || pipe.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE) != 0
                || pipe.fill(new FluidStack(BBFluids.blood(), 5000), IFluidHandler.FluidAction.EXECUTE) != 2000) {
            helper.fail("A pipe should drain the blood, refuse water, and fill it to 4 buckets");
            return;
        }
        level.destroyBlock(at, true);
        ItemStack dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2)).stream().map(ItemEntity::getItem)
                .filter(s -> s.getItem() instanceof FluidBacktankItem).findFirst().orElse(ItemStack.EMPTY);
        if (!dropped.is(BBItems.backtank(BacktankTier.IRON)) || FluidBacktankItem.fluid(dropped).getAmount() != 4000
                || !FluidBacktankItem.fluid(dropped).is(BBFluids.blood())) {
            helper.fail("Broken, it should drop the iron backtank with 4000 mB of blood, got " + dropped + " " + FluidBacktankItem.fluid(dropped).getAmount());
            return;
        }
        helper.succeed();
    }

    /** Its fluid survives being written to disk and read back on the item. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void backtankFluidSaves(GameTestHelper helper) {
        ItemStack stack = new ItemStack(BBItems.backtank(BacktankTier.SOUL_NETHERITE));
        FluidBacktankItem.setFluid(stack, new FluidStack(BBFluids.soulBlood(), 12345));
        net.minecraft.nbt.Tag tag = stack.save(helper.getLevel().registryAccess());
        ItemStack back = ItemStack.parseOptional(helper.getLevel().registryAccess(), (net.minecraft.nbt.CompoundTag) tag);
        if (FluidBacktankItem.fluid(back).getAmount() != 12345 || !FluidBacktankItem.fluid(back).is(BBFluids.soulBlood())) {
            helper.fail("The backtank's soul blood did not survive saving");
            return;
        }
        helper.succeed();
    }
}
