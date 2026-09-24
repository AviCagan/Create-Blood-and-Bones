package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.registry.BBDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Armour scraps: what the Mangler grinds a piece of carcass into, keeping which mob and which part it was
 * ("Cow Leg Scraps"). Scraps of different mobs or parts do not stack.
 */
public class ScrapsItem extends Item {
    public ScrapsItem(Properties properties) {
        super(properties);
    }

    @Nullable
    public static Source source(ItemStack stack) {
        return stack.get(BBDataComponents.SOURCE.get());
    }

    public static ItemStack of(Source source, int count) {
        ItemStack stack = new ItemStack(com.avicagan.bloodandbones.registry.BBItems.SCRAPS.get(), count);
        stack.set(BBDataComponents.SOURCE.get(), source);
        return stack;
    }

    /** The mob's name, as the game has it. */
    public static Component mobName(net.minecraft.resources.ResourceLocation entity) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(EntityType::getDescription).orElse(Component.literal(entity.getPath()));
    }

    @Override
    public Component getName(ItemStack stack) {
        Source source = source(stack);
        if (source == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.bloodandbones.scraps.named", mobName(source.entity()),
                Component.translatable("bloodandbones.part." + source.part()));
    }
}
