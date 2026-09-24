package com.avicagan.bloodandbones.body;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A part of a body taken out at the Surgery Table (a limb, an eye, an organ), named for whoever it came
 * from. Fitted back where a part of its kind is missing (anyone's, either side), it is flesh again.
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

    /** One of this creature's parts, with its name on it. */
    public ItemStack of(net.minecraft.world.entity.Entity owner) {
        return of(owner.getName());
    }

    /** A part with a name on it: a player's, a mob's, a kind of animal's. */
    public ItemStack of(Component owner) {
        ItemStack stack = new ItemStack(this);
        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME, Component.translatable(getDescriptionId() + ".of", owner));
        return stack;
    }

    /**
     * A part cut out of a mob or its carcass: named for the kind of animal, and stamped with where it came from (its
     * mob, the part it was in, a baby or not), so carcass armour can take an organ's traits.
     */
    public ItemStack of(net.minecraft.resources.ResourceLocation entity, boolean baby) {
        ItemStack stack = of(com.avicagan.bloodandbones.parts.ScrapsItem.mobName(entity));
        String in = switch (kind) {
            case EYE -> "head";
            case ARM -> "arm";
            case LEG -> "leg";
            default -> "torso";
        };
        stack.set(com.avicagan.bloodandbones.registry.BBDataComponents.SOURCE.get(), new com.avicagan.bloodandbones.parts.Source(entity, in, baby));
        return stack;
    }

    /** The mob an organ was cut out of, or null for one with no stamp (a player's own). */
    @org.jetbrains.annotations.Nullable
    public static com.avicagan.bloodandbones.parts.Source source(ItemStack stack) {
        return stack.getItem() instanceof SeveredLimbItem ? stack.get(com.avicagan.bloodandbones.registry.BBDataComponents.SOURCE.get()) : null;
    }
}
