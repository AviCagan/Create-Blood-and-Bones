package com.avicagan.bloodandbones.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;

/** Cuts limbs off a carcass at the joint; a heavy, slow blade in a fight. */
public class CleaverItem extends SwordItem {
    /** Cuts each chop counts for: a blood steel blade goes through twice as deep. */
    public final int strokes;

    public CleaverItem(Properties properties) {
        this(properties, Tiers.IRON, 1);
    }

    public CleaverItem(Properties properties, Tier tier, int strokes) {
        super(tier, properties.attributes(SwordItem.createAttributes(tier, 5, -3.0F)));
        this.strokes = strokes;
    }

    /** Striking a living thing that bleeds leaves the blade bloody for a while. */
    @Override
    public boolean hurtEnemy(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.LivingEntity target, net.minecraft.world.entity.LivingEntity attacker) {
        if (!target.getType().is(com.avicagan.bloodandbones.registry.BBTags.BLOODLESS)) {
            com.avicagan.bloodandbones.carcass.Blood.bloody(stack, target.level());
        }
        return super.hurtEnemy(stack, target, attacker);
    }
}
