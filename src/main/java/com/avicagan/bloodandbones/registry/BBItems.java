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
            .model(NonNullBiConsumer.noop()) // hand-made: handheld, and bloody after use
            .lang("Flensing Knife")
            .register();

    public static final ItemEntry<com.avicagan.bloodandbones.item.CleaverItem> BLOOD_STEEL_CLEAVER = BloodAndBones.REGISTRATE
            .item("blood_steel_cleaver", p -> new com.avicagan.bloodandbones.item.CleaverItem(p, net.minecraft.world.item.Tiers.DIAMOND, 2))
            .properties(p -> p.stacksTo(1))
            .model(NonNullBiConsumer.noop()) // hand-made: handheld, and bloody after use
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

    // ---- implants: basic (nothing to run), organic (blood from the backtank), cybernetic (soul blood)
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> PEG_LEG = implant("peg_leg", "Peg Leg",
            com.avicagan.bloodandbones.body.ImplantSpec.basic(com.avicagan.bloodandbones.body.BodyPart.Kind.LEG, 0.9F, 1.0F, bodyTexture("peg_leg")));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> HOOK_HAND = implant("hook_hand", "Hook Hand",
            com.avicagan.bloodandbones.body.ImplantSpec.basic(com.avicagan.bloodandbones.body.BodyPart.Kind.ARM, 1.0F, 0.7F, bodyTexture("hook_hand")));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> FLESH_ARM = implant("flesh_arm", "Flesh Arm",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.ARM, "blood", 1, 1.0F, 1.0F, 1.3F, 1.0F, 0.0F, 0.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.NONE, "flesh_arm"));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> SINEW_LEG = implant("sinew_leg", "Sinew Leg",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.LEG, "blood", 1, 1.1F, 1.3F, 1.0F, 0.0F, 0.0F, 0.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.NONE, "sinew_leg"));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> HYDRAULIC_ARM = implant("hydraulic_arm", "Hydraulic Arm",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.ARM, "soul_blood", 2, 1.0F, 1.0F, 1.8F, 3.0F, 1.0F, 0.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.NONE, "hydraulic_arm"));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> PISTON_LEG = implant("piston_leg", "Piston Leg",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.LEG, "soul_blood", 2, 1.2F, 1.6F, 1.0F, 0.0F, 0.0F, 6.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.NONE, "piston_leg"));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> VENT_ARM = implant("vent_arm", "Vent Arm",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.ARM, "any", 0, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.VENT, "vent_arm"));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> PORT_ARM = implant("port_arm", "Port Arm",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.ARM, null, 0, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.PORT, "port_arm"));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> OPTIC_EYE = implant("optic_eye", "Optic Eye",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.EYE, "soul_blood", 1, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.NIGHT_VISION, null));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> PUMP_HEART = implant("pump_heart", "Pump Heart",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.HEART, "soul_blood", 2, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.REGENERATION, null));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> BELLOWS_LUNGS = implant("bellows_lungs", "Bellows Lungs",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.LUNGS, "soul_blood", 1, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.WATER_BREATHING, null));
    public static final ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> FURNACE_STOMACH = implant("furnace_stomach", "Furnace Stomach",
            powered(com.avicagan.bloodandbones.body.BodyPart.Kind.STOMACH, "blood", 1, 1.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, com.avicagan.bloodandbones.body.ImplantSpec.Ability.IRON_GUT, null));

    // ---- parts of a body, taken out on the Surgery Table and put back as flesh
    public static final ItemEntry<com.avicagan.bloodandbones.body.SeveredLimbItem> SEVERED_ARM = part("severed_arm", "Severed Arm", "Arm", com.avicagan.bloodandbones.body.BodyPart.Kind.ARM);
    public static final ItemEntry<com.avicagan.bloodandbones.body.SeveredLimbItem> SEVERED_LEG = part("severed_leg", "Severed Leg", "Leg", com.avicagan.bloodandbones.body.BodyPart.Kind.LEG);
    public static final ItemEntry<com.avicagan.bloodandbones.body.SeveredLimbItem> EYE = part("eye", "Eye", "Eye", com.avicagan.bloodandbones.body.BodyPart.Kind.EYE);
    public static final ItemEntry<com.avicagan.bloodandbones.body.SeveredLimbItem> HEART = part("heart", "Heart", "Heart", com.avicagan.bloodandbones.body.BodyPart.Kind.HEART);
    public static final ItemEntry<com.avicagan.bloodandbones.body.SeveredLimbItem> LUNGS = part("lungs", "Lungs", "Lungs", com.avicagan.bloodandbones.body.BodyPart.Kind.LUNGS);
    public static final ItemEntry<com.avicagan.bloodandbones.body.SeveredLimbItem> STOMACH = part("stomach", "Stomach", "Stomach", com.avicagan.bloodandbones.body.BodyPart.Kind.STOMACH);

    /** The item a part of this kind comes out as. */
    public static com.avicagan.bloodandbones.body.SeveredLimbItem partItem(com.avicagan.bloodandbones.body.BodyPart.Kind kind) {
        return (switch (kind) {
            case ARM -> SEVERED_ARM;
            case LEG -> SEVERED_LEG;
            case EYE -> EYE;
            case HEART -> HEART;
            case LUNGS -> LUNGS;
            case STOMACH -> STOMACH;
        }).get();
    }

    private static net.minecraft.resources.ResourceLocation bodyTexture(String name) {
        return BloodAndBones.asResource("textures/entity/implant/" + name + ".png");
    }

    private static com.avicagan.bloodandbones.body.ImplantSpec powered(com.avicagan.bloodandbones.body.BodyPart.Kind kind, String fuel, int drain,
                                                                        float walk, float jump, float work, float attack, float reach, float safeFall,
                                                                        com.avicagan.bloodandbones.body.ImplantSpec.Ability ability, String texture) {
        return com.avicagan.bloodandbones.body.ImplantSpec.powered(kind, fuel, drain, walk, jump, work, attack, reach, safeFall, ability,
                texture == null ? null : bodyTexture(texture));
    }

    private static ItemEntry<com.avicagan.bloodandbones.body.ImplantItem> implant(String id, String name, com.avicagan.bloodandbones.body.ImplantSpec spec) {
        return BloodAndBones.REGISTRATE.item(id, p -> new com.avicagan.bloodandbones.body.ImplantItem(p, spec)).lang(name).register();
    }

    private static ItemEntry<com.avicagan.bloodandbones.body.SeveredLimbItem> part(String id, String name, String owned, com.avicagan.bloodandbones.body.BodyPart.Kind kind) {
        BloodAndBones.REGISTRATE.addRawLang("item.bloodandbones." + id + ".of", "%s's " + owned);
        return BloodAndBones.REGISTRATE.item(id, p -> new com.avicagan.bloodandbones.body.SeveredLimbItem(p, kind)).lang(name).register();
    }

    public static final ItemEntry<net.minecraft.world.item.Item> SOUL_NETHERITE_INGOT = BloodAndBones.REGISTRATE
            .item("soul_netherite_ingot", net.minecraft.world.item.Item::new)
            .properties(p -> p.fireResistant())
            .lang("Soul Netherite Ingot")
            .register();

    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem> INCOMPLETE_SOUL_NETHERITE_INGOT = BloodAndBones.REGISTRATE
            .item("incomplete_soul_netherite_ingot", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
            .properties(p -> p.fireResistant())
            .removeTab(BBCreativeTabs.MAIN.getKey())
            .lang("Incomplete Soul Netherite Ingot")
            .register();

    /** A Fluid Backtank of each tier, worn in the chest slot. */
    public static final java.util.Map<com.avicagan.bloodandbones.backtank.BacktankTier, ItemEntry<com.avicagan.bloodandbones.backtank.FluidBacktankItem>> BACKTANKS = backtanks();

    private static java.util.Map<com.avicagan.bloodandbones.backtank.BacktankTier, ItemEntry<com.avicagan.bloodandbones.backtank.FluidBacktankItem>> backtanks() {
        java.util.Map<com.avicagan.bloodandbones.backtank.BacktankTier, ItemEntry<com.avicagan.bloodandbones.backtank.FluidBacktankItem>> out =
                new java.util.EnumMap<>(com.avicagan.bloodandbones.backtank.BacktankTier.class);
        for (com.avicagan.bloodandbones.backtank.BacktankTier tier : com.avicagan.bloodandbones.backtank.BacktankTier.values()) {
            String name = tier.getSerializedName();
            String title = java.util.Arrays.stream(name.split("_")).map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1))
                    .collect(java.util.stream.Collectors.joining(" "));
            ItemEntry<com.avicagan.bloodandbones.backtank.FluidBacktankItem> entry = BloodAndBones.REGISTRATE
                    .item(name + "_fluid_backtank", p -> new com.avicagan.bloodandbones.backtank.FluidBacktankItem(p, tier,
                            () -> (net.minecraft.world.item.BlockItem) BBBlocks.FLUID_BACKTANK.asItem()))
                    .properties(p -> tier == com.avicagan.bloodandbones.backtank.BacktankTier.SOUL_NETHERITE ? p.fireResistant() : p)
                    .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/fluid_backtank_" + name)))
                    .lang(title + " Fluid Backtank")
                    .register();
            out.put(tier, entry);
        }
        return out;
    }

    public static com.avicagan.bloodandbones.backtank.FluidBacktankItem backtank(com.avicagan.bloodandbones.backtank.BacktankTier tier) {
        return BACKTANKS.get(tier).get();
    }

    public static void register() {
    }
}
