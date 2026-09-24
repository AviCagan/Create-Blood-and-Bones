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
 * 5.7): drag strength takes that share off the slowdown of dragging a carcass.
 */
public final class BBAttributes {
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, BloodAndBones.MOD_ID);

    public static final DeferredHolder<Attribute, Attribute> DRAG_STRENGTH = ATTRIBUTES.register("drag_strength",
            () -> new RangedAttribute("attribute.bloodandbones.drag_strength", 0.0, 0.0, 0.75).setSyncable(true));

    private BBAttributes() {
    }

    public static void register(IEventBus modBus) {
        ATTRIBUTES.register(modBus);
        modBus.addListener((EntityAttributeModificationEvent event) -> {
            event.add(EntityType.PLAYER, DRAG_STRENGTH);
            // a hauler minion drags carcasses too, its parts' hauler traits easing it (docs/PARTS-AND-TRAITS.md section 6.9)
            event.add(BBEntities.MINION.get(), DRAG_STRENGTH);
        });
    }
}
