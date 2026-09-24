package com.avicagan.bloodandbones.cyber;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** A cybernetic module, fitted into a brass limb's slot at the Surgery Table. */
public class ModuleItem extends Item {
    private final Module module;

    public ModuleItem(Properties properties, Module module) {
        super(properties.stacksTo(1));
        this.module = module;
    }

    public Module module() {
        return module;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("bloodandbones.module.fits." + module.kind().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("bloodandbones.module.mode." + module.mode().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(ChatFormatting.DARK_AQUA));
    }
}
