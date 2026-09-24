package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BBArmorMaterials;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * A piece of carcass armour. Its material has no numbers of its own: armour, toughness, knockback
 * resistance and the material's quirk come from the scraps' data and its tier when it is worn
 * ({@link TraitEvents}), its durability is set when it is made or upgraded, and its traits live in
 * {@link ActiveTraits}. A chestplate can carry a Fluid Backtank strapped to its back.
 * <p>
 * A piece is mended only with scraps on an anvil. Two pieces never combine (in a crafting grid or on a grindstone):
 * vanilla would make a blank piece of the larger durability, losing what each is made of and any tank on it.
 */
public class CarcassArmourItem extends ArmorItem {
    private final String piece;

    public CarcassArmourItem(Properties properties, Type type) {
        super(BBArmorMaterials.CARCASS, type, properties.durability(type.getDurability(10)).setNoRepair());
        this.piece = switch (type) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            default -> "boots";
        };
    }

    public String piece() {
        return piece;
    }

    @Nullable
    public static CarcassArmour armour(ItemStack stack) {
        return stack.get(BBDataComponents.CARCASS_ARMOUR.get());
    }

    /** The data for whichever side is asking (armour is looked at from both). */
    public static PartsData.Store store() {
        return net.neoforged.fml.util.thread.EffectiveSide.get().isClient() ? PartsData.CLIENT : PartsData.SERVER;
    }

    /** The scrap material of the piece's body. */
    public static ScrapMaterial material(CarcassArmour armour, PartsData.Store store) {
        return store.material(store.resolve(armour.body(), armour.baby()).material());
    }

    /**
     * Stamp a piece with what it is made of, and bake what cannot be looked up live: its durability, from the
     * material times its tier's multiplier, and fire resistance from a tier that gives it.
     */
    public static ItemStack make(ItemStack stack, CarcassArmour armour, PartsData.Store store) {
        stack.set(BBDataComponents.CARCASS_ARMOUR.get(), armour);
        ArmourTier tier = store.tier(armour.tier());
        float mult = tier == null ? 1.0F : tier.durabilityMult();
        stack.set(DataComponents.MAX_DAMAGE, Math.max(1, Math.round(material(armour, store).durability() * ScrapMaterial.slotDurability(armour.piece()) * mult)));
        if (tier != null && tier.fireResistant()) {
            stack.set(DataComponents.FIRE_RESISTANT, net.minecraft.util.Unit.INSTANCE);
        } else {
            stack.remove(DataComponents.FIRE_RESISTANT);
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        CarcassArmour armour = armour(stack);
        if (armour == null) {
            return super.getName(stack);
        }
        ResourceLocation material = store().resolve(armour.body(), armour.baby()).material();
        return Component.translatable("item.bloodandbones.carcass_armour.named", ScrapsItem.mobName(armour.body()),
                Component.translatable("scrap_material." + material.getNamespace() + "." + material.getPath()),
                Component.translatable("bloodandbones.piece." + piece));
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        CarcassArmour armour = armour(stack);
        return armour == null ? 10 : material(armour, store()).enchantability();
    }

    /**
     * A strapped tank does not break with the chestplate: as the chestplate gives way the tank comes off, fluid and
     * all, into the wearer's inventory or at their feet. With nobody to hand it to, the chestplate holds at its last
     * point instead.
     */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        if (amount <= 0 || !stack.has(BBDataComponents.STRAPPED_TANK.get()) || stack.getDamageValue() + amount < stack.getMaxDamage()) {
            return amount;
        }
        if (entity == null) {
            return Math.max(0, stack.getMaxDamage() - 1 - stack.getDamageValue());
        }
        if (!entity.hasInfiniteMaterials()) {
            ItemStack tank = FluidBacktankItem.takeOff(stack);
            if (!(entity instanceof Player player && player.getInventory().add(tank))) {
                entity.spawnAtLocation(tank);
            }
        }
        return amount;
    }

    /** A strapped chestplate burnt, blown up or otherwise destroyed as an item lets its tank fall free (which may survive it). */
    @Override
    public void onDestroyed(ItemEntity itemEntity) {
        ItemStack tank = FluidBacktankItem.takeOff(itemEntity.getItem());
        if (!tank.isEmpty()) {
            ItemUtils.onContainerDestroyed(itemEntity, List.of(tank));
        }
    }

    /** Scraps of the piece's own mob mend it on an anvil (not a raw hide or an organ stamped with that mob). */
    @Override
    public boolean isValidRepairItem(ItemStack armour, ItemStack repair) {
        CarcassArmour a = armour(armour);
        Source source = ScrapsItem.source(repair);
        return a != null && source != null && source.entity().equals(a.body());
    }

    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        CarcassArmour armour = armour(stack);
        String look = armour == null ? "hide" : material(armour, store()).look();
        if (com.avicagan.bloodandbones.config.BBClientConfig.bloodless()) {
            look = look + "_clean";
        }
        return BloodAndBones.asResource("textures/models/armor/carcass_" + look + "_layer_" + (innerModel ? 2 : 1) + ".png");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CarcassArmour armour = armour(stack);
        if (armour == null) {
            return;
        }
        PartsData.Store store = store();
        tooltip.add(Component.translatable("bloodandbones.carcass_armour.body", ScrapsItem.mobName(armour.body())).withStyle(ChatFormatting.GRAY));
        armour.shoulders().ifPresent(m -> tooltip.add(Component.translatable("bloodandbones.carcass_armour.shoulders", ScrapsItem.mobName(m)).withStyle(ChatFormatting.GRAY)));
        armour.hips().ifPresent(m -> tooltip.add(Component.translatable("bloodandbones.carcass_armour.hips", ScrapsItem.mobName(m)).withStyle(ChatFormatting.GRAY)));
        armour.hide().ifPresent(hide -> tooltip.add((hide.entity().isPresent()
                ? Component.translatable("bloodandbones.carcass_armour.hide", ScrapsItem.mobName(hide.entity().get()))
                : Component.translatable("bloodandbones.carcass_armour.hide_plain")).withStyle(ChatFormatting.GRAY)));
        armour.organ().ifPresent(organ -> tooltip.add(Component.translatable("bloodandbones.carcass_armour.organ", ScrapsItem.mobName(organ.entity()),
                Component.translatable("organ." + organ.organ().getNamespace() + "." + organ.organ().getPath())).withStyle(ChatFormatting.GRAY)));
        ArmourTier tier = store.tier(armour.tier());
        if (tier != null) {
            tooltip.add(Component.translatable("bloodandbones.carcass_armour.tier", armour.tier(), tier.item().getDescription()).withStyle(ChatFormatting.GOLD));
        }
        com.avicagan.bloodandbones.backtank.BacktankTier tank = stack.get(BBDataComponents.STRAPPED_TANK.get());
        if (tank != null) {
            tooltip.add(Component.translatable("bloodandbones.carcass_armour.strapped",
                    com.avicagan.bloodandbones.registry.BBItems.backtank(tank).getDescription()).withStyle(ChatFormatting.GRAY));
            com.avicagan.bloodandbones.backtank.FluidBacktankItem.describeFluid(stack, tooltip);
        }
        for (TraitList.Resolved trait : armour.traits(store)) {
            tooltip.add(Traits.describe(store, trait).withStyle(ChatFormatting.DARK_AQUA));
        }
        store.resolve(armour.body(), armour.baby()).fullSet().ifPresent(set -> tooltip.add(Component.translatable("bloodandbones.carcass_armour.full_set",
                Component.translatable(set.name())).withStyle(ChatFormatting.DARK_PURPLE)));
    }
}
