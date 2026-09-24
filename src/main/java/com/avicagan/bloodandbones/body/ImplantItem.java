package com.avicagan.bloodandbones.body;

import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Something fitted in a part's place at the Surgery Table: a basic prosthetic (a peg leg, a hook hand)
 * that always works, an organic prosthetic that runs on blood, or a cybernetic that runs on soul blood, both
 * from the Fluid Backtank. See {@link ImplantSpec}.
 */
public class ImplantItem extends Item {
    private final ImplantSpec spec;

    public ImplantItem(Properties properties, ImplantSpec spec) {
        super(properties.stacksTo(1));
        this.spec = spec;
    }

    public ImplantSpec spec() {
        return spec;
    }

    public BodyPart.Kind kind() {
        return spec.kind();
    }

    public boolean fits(BodyPart part) {
        return part.kind() == spec.kind();
    }

    public float walk() {
        return spec.walk();
    }

    public float work() {
        return spec.work();
    }

    @Nullable
    public ResourceLocation texture() {
        return spec.texture();
    }

    public boolean powered() {
        return spec.fuel() != null;
    }

    @Nullable
    public Fluid fuel() {
        return switch (spec.fuel() == null ? "" : spec.fuel()) {
            case "blood" -> com.avicagan.bloodandbones.registry.BBFluids.blood();
            case "soul_blood" -> com.avicagan.bloodandbones.registry.BBFluids.soulBlood();
            default -> null;
        };
    }

    /** Runs on whatever is in the tank, using it only as it goes (the Vent Arm sprays it). */
    public boolean anyFuel() {
        return "any".equals(spec.fuel());
    }

    /** An organic prosthetic, the kind that rots with use (it runs on blood). */
    public boolean organic() {
        return "blood".equals(spec.fuel());
    }

    /**
     * Whether this fitted implant is doing its job on this wearer: its kind must be working, and an organic one
     * must not have rotted all the way (Necrosis).
     */
    public boolean working(ItemStack stack, @Nullable LivingEntity wearer) {
        return working(wearer) && (!organic() || Necrosis.of(stack) < Necrosis.MAX);
    }

    /** Whether its kind is doing its job on this wearer: a basic one always; a powered one while the worn tank has its fuel. */
    public boolean working(@Nullable LivingEntity wearer) {
        if (spec.fuel() == null) {
            return true;
        }
        FluidStack tank = FluidBacktankItem.fluid(FluidBacktankItem.wornBy(wearer));
        if (anyFuel()) {
            return !tank.isEmpty();
        }
        return tank.is(fuel()) && tank.getAmount() > 0;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Fluid fuel = fuel();
        if (anyFuel()) {
            tooltip.add(Component.translatable("bloodandbones.implant.runs_on_any").withStyle(ChatFormatting.GRAY));
        } else if (fuel != null) {
            tooltip.add(Component.translatable("bloodandbones.implant.runs_on", new FluidStack(fuel, 1).getHoverName(), spec.drain())
                    .withStyle(ChatFormatting.GRAY));
        }
        int slots = spec.slots();
        if (slots > 0) {
            java.util.List<com.avicagan.bloodandbones.cyber.Module> modules = com.avicagan.bloodandbones.cyber.Modules.of(stack);
            java.util.List<Component> names = new java.util.ArrayList<>();
            for (int i = 0; i < slots; i++) {
                names.add(i < modules.size() ? Component.translatable(modules.get(i).translationKey())
                        : Component.translatable("bloodandbones.implant.module_empty"));
            }
            tooltip.add(Component.translatable("bloodandbones.implant.modules", net.minecraft.network.chat.ComponentUtils.formatList(names, Component.literal(", ")))
                    .withStyle(ChatFormatting.GOLD));
        }
        int necrosis = Necrosis.of(stack);
        if (organic() && necrosis > 0) {
            tooltip.add(Component.translatable("bloodandbones.implant.necrosis", necrosis * 100 / Necrosis.MAX)
                    .withStyle(necrosis >= Necrosis.MAX ? ChatFormatting.DARK_RED : ChatFormatting.RED));
        }
    }
}
