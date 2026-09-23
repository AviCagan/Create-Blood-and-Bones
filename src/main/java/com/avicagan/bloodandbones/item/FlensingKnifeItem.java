package com.avicagan.bloodandbones.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/** Takes the hide off a carcass in long strokes; a quick, light blade in a fight. */
public class FlensingKnifeItem extends SwordItem {
    public FlensingKnifeItem(Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 1, -1.8F)));
    }
}
