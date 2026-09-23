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
 * Spouts fill it and Item Drains empty it as it is.
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

    /** The backtank worn in the chest slot, or empty. */
    public static ItemStack wornBy(@Nullable Entity entity) {
        if (entity instanceof LivingEntity living && living.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof FluidBacktankItem) {
            return living.getItemBySlot(EquipmentSlot.CHEST);
        }
        return ItemStack.EMPTY;
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
        return Math.round(13.0F * Mth.clamp(fluid(stack).getAmount() / (float) tier.capacity(), 0.0F, 1.0F));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x8E1010;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FluidStack fluid = fluid(stack);
        tooltip.add(fluid.isEmpty()
                ? Component.translatable("bloodandbones.backtank.empty", tier.buckets()).withStyle(ChatFormatting.GRAY)
                : Component.translatable("bloodandbones.backtank.holding", fluid.getHoverName(), fluid.getAmount(), tier.capacity()).withStyle(ChatFormatting.GRAY));
    }
}
