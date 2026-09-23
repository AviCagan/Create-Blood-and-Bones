package com.avicagan.bloodandbones.item;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;

/** Takes the hide off a carcass in long strokes; a quick, light blade in a fight. */
public class FlensingKnifeItem extends SwordItem {
    public FlensingKnifeItem(Properties properties) {
        super(Tiers.IRON, properties.attributes(SwordItem.createAttributes(Tiers.IRON, 1, -1.8F)));
    }

    /** Striking a living thing that bleeds leaves the blade bloody for a while. */
    @Override
    public boolean hurtEnemy(net.minecraft.world.item.ItemStack stack, net.minecraft.world.entity.LivingEntity target, net.minecraft.world.entity.LivingEntity attacker) {
        if (com.avicagan.bloodandbones.carcass.Blood.bleeds(target)) {
            com.avicagan.bloodandbones.carcass.Blood.bloody(stack, target.level());
        }
        return super.hurtEnemy(stack, target, attacker);
    }

    /**
     * Drawing blood only changes the blade's components; the held blade should not dip out of view and
     * back each time it does. Another item, or another slot, still swaps as usual.
     */
    @Override
    public boolean shouldCauseReequipAnimation(net.minecraft.world.item.ItemStack oldStack, net.minecraft.world.item.ItemStack newStack, boolean slotChanged) {
        return slotChanged || !net.minecraft.world.item.ItemStack.isSameItem(oldStack, newStack);
    }
}
