package com.avicagan.bloodandbones.parts;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A special organ cut out of a mob at the Surgery Table (docs/PARTS-AND-TRAITS.md section 7.1): a sac, a gland, a core.
 * One item for all of them: which organ it is ({@code bloodandbones:organ}) and whose ({@code bloodandbones:source}) are
 * on the stack, and its name, look and colour come from the organ's own file ("Creeper's Powder Sac", a green sac). What
 * it does fitted into carcass armour or a minion is its mob's {@code organ_traits}, on its tooltip ({@link Organs#onTooltip}).
 * In bloodless mode it is a machined core with the organ's colour glowing through a window, named for its machine part.
 */
public class GlandItem extends Item {
    public GlandItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        PartsData.Store store = CarcassArmourItem.store();
        CarcassArmour.Organ organ = Organs.of(stack, store);
        if (organ == null) {
            return super.getName(stack);
        }
        return Component.translatable("item.bloodandbones.gland.of", ScrapsItem.mobName(organ.entity()), Organs.name(store, organ.organ()));
    }
}
