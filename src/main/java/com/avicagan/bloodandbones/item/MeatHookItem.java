package com.avicagan.bloodandbones.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/**
 * Killing a mob with this leaves an intact physics carcass instead of loot.
 */
public class MeatHookItem extends SwordItem {
    public MeatHookItem(Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.8F)));
    }
}
