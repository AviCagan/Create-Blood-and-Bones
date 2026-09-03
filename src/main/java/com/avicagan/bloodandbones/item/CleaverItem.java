package com.avicagan.bloodandbones.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/** Cuts limbs off a carcass at the joint; a heavy, slow blade in a fight. */
public class CleaverItem extends SwordItem {
    public CleaverItem(Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 5, -3.0F)));
    }
}
