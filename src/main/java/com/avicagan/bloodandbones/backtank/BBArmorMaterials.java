package com.avicagan.bloodandbones.backtank;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** One armour material per backtank tier, as Create registers its copper backtank's. Only the chest counts. */
public final class BBArmorMaterials {
    private static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, BloodAndBones.MOD_ID);
    private static final Map<BacktankTier, Holder<ArmorMaterial>> BY_TIER = new EnumMap<>(BacktankTier.class);

    static {
        for (BacktankTier tier : BacktankTier.values()) {
            BY_TIER.put(tier, MATERIALS.register("backtank_" + tier.getSerializedName(), () -> {
                EnumMap<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
                for (ArmorItem.Type type : ArmorItem.Type.values()) {
                    defense.put(type, type == ArmorItem.Type.CHESTPLATE ? tier.defense() : 0);
                }
                return new ArmorMaterial(defense, 10, tier.ordinal() >= BacktankTier.SOUL_NETHERITE.ordinal()
                        ? SoundEvents.ARMOR_EQUIP_NETHERITE : SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.EMPTY,
                        List.of(new ArmorMaterial.Layer(BloodAndBones.asResource("backtank_" + tier.getSerializedName()))),
                        tier.toughness(), tier.knockbackResistance());
            }));
        }
    }

    private BBArmorMaterials() {
    }

    public static Holder<ArmorMaterial> of(BacktankTier tier) {
        return BY_TIER.get(tier);
    }

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
    }
}
