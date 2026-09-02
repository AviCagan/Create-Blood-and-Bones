package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.item.MeatHookItem;
import com.tterrag.registrate.util.entry.ItemEntry;

public class BBItems {
    public static final ItemEntry<MeatHookItem> MEAT_HOOK = BloodAndBones.REGISTRATE
            .item("meat_hook", MeatHookItem::new)
            .properties(p -> p.stacksTo(1))
            .lang("Meat Hook")
            .register();

    public static void register() {
    }
}
