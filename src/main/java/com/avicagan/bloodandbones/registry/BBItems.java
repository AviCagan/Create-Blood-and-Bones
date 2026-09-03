package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.item.MeatHookItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

public class BBItems {
    public static final ItemEntry<MeatHookItem> MEAT_HOOK = BloodAndBones.REGISTRATE
            .item("meat_hook", MeatHookItem::new)
            .properties(p -> p.stacksTo(1))
            .model(NonNullBiConsumer.noop()) // hand-made 3D model in assets/bloodandbones/models/item
            .lang("Meat Hook")
            .register();

    public static final ItemEntry<com.avicagan.bloodandbones.item.CleaverItem> CLEAVER = BloodAndBones.REGISTRATE
            .item("cleaver", com.avicagan.bloodandbones.item.CleaverItem::new)
            .properties(p -> p.stacksTo(1))
            .model(NonNullBiConsumer.noop()) // hand-made handheld model in assets/bloodandbones/models/item
            .lang("Cleaver")
            .register();

    public static final ItemEntry<com.avicagan.bloodandbones.item.CarcassPieceItem> CARCASS_PIECE = BloodAndBones.REGISTRATE
            .item("carcass_piece", com.avicagan.bloodandbones.item.CarcassPieceItem::new)
            .properties(p -> p.stacksTo(1))
            .model(NonNullBiConsumer.noop())
            .lang("Carcass Piece")
            .register();

    public static void register() {
    }
}
