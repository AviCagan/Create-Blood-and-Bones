package com.avicagan.bloodandbones.body;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A limb taken off at the Surgery Table, named for whoever it came from. Fitted back where a limb of its kind
 * is missing (anyone's, either side), it is flesh again.
 */
public class SeveredLimbItem extends Item {
    private final BodyPart.Kind kind;

    public SeveredLimbItem(Properties properties, BodyPart.Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    public BodyPart.Kind kind() {
        return kind;
    }

    public boolean fits(BodyPart part) {
        return part.kind() == kind;
    }

    /** One of this player's limbs, with their name on it. */
    public ItemStack of(Player owner) {
        ItemStack stack = new ItemStack(this);
        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.translatable(getDescriptionId() + ".of", owner.getName()));
        return stack;
    }
}
