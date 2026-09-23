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

    public static final ItemEntry<com.avicagan.bloodandbones.item.FlensingKnifeItem> FLENSING_KNIFE = BloodAndBones.REGISTRATE
            .item("flensing_knife", com.avicagan.bloodandbones.item.FlensingKnifeItem::new)
            .properties(p -> p.stacksTo(1))
            .model((ctx, prov) -> prov.handheld(ctx))
            .lang("Flensing Knife")
            .register();

    public static final ItemEntry<com.avicagan.bloodandbones.item.CleaverItem> BLOOD_STEEL_CLEAVER = BloodAndBones.REGISTRATE
            .item("blood_steel_cleaver", p -> new com.avicagan.bloodandbones.item.CleaverItem(p, net.minecraft.world.item.Tiers.DIAMOND, 2))
            .properties(p -> p.stacksTo(1))
            .model((ctx, prov) -> prov.handheld(ctx))
            .lang("Blood Steel Cleaver")
            .register();

    public static final ItemEntry<net.minecraft.world.item.Item> BLOOD_STEEL_INGOT = BloodAndBones.REGISTRATE
            .item("blood_steel_ingot", net.minecraft.world.item.Item::new)
            .tag(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "ingots/blood_steel")))
            .lang("Blood Steel Ingot")
            .register();

    public static final ItemEntry<net.minecraft.world.item.Item> BLOOD_STEEL_NUGGET = BloodAndBones.REGISTRATE
            .item("blood_steel_nugget", net.minecraft.world.item.Item::new)
            .tag(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "nuggets/blood_steel")))
            .lang("Blood Steel Nugget")
            .register();

    public static final ItemEntry<net.minecraft.world.item.Item> BLOOD_DIAMOND = BloodAndBones.REGISTRATE
            .item("blood_diamond", net.minecraft.world.item.Item::new)
            .properties(p -> p.rarity(net.minecraft.world.item.Rarity.RARE))
            .tag(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "gems/blood_diamond")))
            .lang("Blood Diamond")
            .register();

    public static final ItemEntry<net.minecraft.world.item.Item> RAW_HIDE = BloodAndBones.REGISTRATE
            .item("raw_hide", net.minecraft.world.item.Item::new)
            .lang("Raw Hide")
            .register();

    public static final ItemEntry<net.minecraft.world.item.Item> COOKED_MEAT = BloodAndBones.REGISTRATE
            .item("cooked_meat", net.minecraft.world.item.Item::new)
            .properties(p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()))
            .tag(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "foods/cooked_meat")))
            .lang("Cooked Meat")
            .register();

    public static final ItemEntry<net.minecraft.world.item.Item> RAW_MEAT = BloodAndBones.REGISTRATE
            .item("raw_meat", net.minecraft.world.item.Item::new)
            .properties(p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(3).saturationModifier(0.3F).build()))
            .tag(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "foods/raw_meat")))
            .recipe((ctx, prov) -> prov.food(com.tterrag.registrate.util.DataIngredient.items(ctx), net.minecraft.data.recipes.RecipeCategory.FOOD, BBItems.COOKED_MEAT, 0.35F))
            .lang("Raw Meat")
            .register();

    public static final ItemEntry<net.minecraft.world.item.Item> OFFAL = BloodAndBones.REGISTRATE
            .item("offal", net.minecraft.world.item.Item::new)
            .properties(p -> p.food(new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.1F)
                    .effect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.HUNGER, 600, 0), 0.5F).build()))
            .lang("Offal")
            .register();

    public static final ItemEntry<net.minecraft.world.item.Item> ANIMAL_FAT = BloodAndBones.REGISTRATE
            .item("animal_fat", net.minecraft.world.item.Item::new)
            .lang("Animal Fat")
            .register();

    public static void register() {
    }
}
