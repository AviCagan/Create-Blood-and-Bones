package com.avicagan.bloodandbones.backtank;

import com.avicagan.bloodandbones.registry.BBDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * A wearable tank for any fluid, in the chest slot, with the armour of its tier. Right-clicked on a block it
 * is set down as a Fluid Backtank block, fluid and all, for pipes to fill or empty; broken, it comes back.
 * Spouts fill it and Item Drains empty it as it is. It can also be strapped to the back of a carcass
 * chestplate (docs/PARTS-AND-TRAITS.md section 7.8), which then carries its tier and its fluid and counts as
 * the worn tank.
 */
public class FluidBacktankItem extends ArmorItem {
    private final BacktankTier tier;
    private final Supplier<? extends BlockItem> placeable;

    public FluidBacktankItem(Properties properties, BacktankTier tier, Supplier<? extends BlockItem> placeable) {
        super(BBArmorMaterials.of(tier), Type.CHESTPLATE, properties.stacksTo(1));
        this.tier = tier;
        this.placeable = placeable;
    }

    public BacktankTier tier() {
        return tier;
    }

    public static FluidStack fluid(ItemStack stack) {
        return stack.getOrDefault(BBDataComponents.FLUID, SimpleFluidContent.EMPTY).copy();
    }

    public static void setFluid(ItemStack stack, FluidStack fluid) {
        if (fluid.isEmpty()) {
            stack.remove(BBDataComponents.FLUID);
        } else {
            stack.set(BBDataComponents.FLUID, SimpleFluidContent.copyOf(fluid));
        }
    }

    /** The tank worn in the chest slot, a backtank or a chestplate with one strapped on; or empty. */
    public static ItemStack wornBy(@Nullable Entity entity) {
        if (entity instanceof LivingEntity living && tier(living.getItemBySlot(EquipmentSlot.CHEST)) != null) {
            return living.getItemBySlot(EquipmentSlot.CHEST);
        }
        return ItemStack.EMPTY;
    }

    /** The tank's tier: a backtank's own, or the one strapped to a chestplate; null if it is no tank. */
    @Nullable
    public static BacktankTier tier(ItemStack stack) {
        return stack.getItem() instanceof FluidBacktankItem tank ? tank.tier() : stack.get(BBDataComponents.STRAPPED_TANK);
    }

    /** How much the tank holds, in millibuckets; 0 if it is no tank. */
    public static int capacity(ItemStack stack) {
        BacktankTier tier = tier(stack);
        return tier == null ? 0 : tier.capacity();
    }

    /** A carcass chestplate with this backtank strapped to its back: its tier and fluid go onto a copy of the chestplate. */
    public static ItemStack strap(ItemStack chestplate, ItemStack tank) {
        ItemStack out = chestplate.copyWithCount(1);
        out.set(BBDataComponents.STRAPPED_TANK, ((FluidBacktankItem) tank.getItem()).tier());
        setFluid(out, fluid(tank));
        return out;
    }

    /** The backtank strapped to this chestplate, with its fluid, as an item again. */
    public static ItemStack unstrapped(ItemStack chestplate) {
        BacktankTier tier = chestplate.get(BBDataComponents.STRAPPED_TANK);
        if (tier == null) {
            return ItemStack.EMPTY;
        }
        ItemStack tank = new ItemStack(com.avicagan.bloodandbones.registry.BBItems.backtank(tier));
        setFluid(tank, fluid(chestplate));
        return tank;
    }

    /** The chestplate with its backtank taken off (and the fluid with it). */
    public static ItemStack withoutTank(ItemStack chestplate) {
        ItemStack out = chestplate.copyWithCount(1);
        out.remove(BBDataComponents.STRAPPED_TANK);
        out.remove(BBDataComponents.FLUID);
        return out;
    }

    /** The tooltip line for what a tank holds. */
    public static void describeFluid(ItemStack stack, List<Component> tooltip) {
        FluidStack fluid = fluid(stack);
        BacktankTier tier = tier(stack);
        if (tier == null) {
            return;
        }
        tooltip.add(fluid.isEmpty()
                ? Component.translatable("bloodandbones.backtank.empty", tier.buckets()).withStyle(ChatFormatting.GRAY)
                : Component.translatable("bloodandbones.backtank.holding", fluid.getHoverName(), fluid.getAmount(), tier.capacity()).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return placeable.get().useOn(context);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !fluid(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * Mth.clamp(fluid(stack).getAmount() / (float) capacity(stack), 0.0F, 1.0F));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x8E1010;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        describeFluid(stack, tooltip);
    }
}
