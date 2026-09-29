package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own attributes, which tie traits back to the rest of the mod (docs/PARTS-AND-TRAITS.md section
 * 5.7): drag strength takes that share off the slowdown of dragging a carcass; butchery yield scales what a blade
 * in the hand gets out of a carcass (CarcassButchery#byHand).
 */
public final class BBAttributes {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, BloodAndBones.MOD_ID);

    public static final DeferredHolder<Attribute, Attribute> DRAG_STRENGTH = ATTRIBUTES.register("drag_strength",
            () -> new RangedAttribute("attribute.bloodandbones.drag_strength", 0.0, 0.0, 0.75).setSyncable(true));
    public static final DeferredHolder<Attribute, Attribute> BUTCHERY_YIELD = ATTRIBUTES.register("butchery_yield",
            () -> new RangedAttribute("attribute.bloodandbones.butchery_yield", 1.0, 0.0, 4.0).setSyncable(true));

    private BBAttributes() {
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent event) -> {
            event.add(EntityType.PLAYER, DRAG_STRENGTH);
            event.add(EntityType.PLAYER, BUTCHERY_YIELD);
            // a butcher minion's blade is in its hand too
            event.add(BBEntities.MINION.get(), BUTCHERY_YIELD);
            // a hauler minion drags carcasses too, its parts' hauler traits easing it (docs/PARTS-AND-TRAITS.md section 6.9)
            event.add(BBEntities.MINION.get(), DRAG_STRENGTH);
        });
    }
}
