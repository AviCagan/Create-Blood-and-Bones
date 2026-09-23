package com.avicagan.bloodandbones.compat.jei;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryManager;
import com.avicagan.bloodandbones.carcass.butchery.Yield;
import com.avicagan.bloodandbones.registry.BBItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a mob's carcass gives: its hide when skinned, and everything its pieces give when butchered, added
 * up over the whole animal. Counts are the expected amount from a fresh carcass, rounded.
 */
public class ButcheryCategory extends AbstractRecipeCategory<ButcheryCategory.Entry> {
    public static final RecipeType<Entry> TYPE = RecipeType.create(BloodAndBones.MOD_ID, "butchery", Entry.class);
    private static final int WIDTH = 166;
    private static final int HEIGHT = 86;

    /** A mob, its hide yields and its butchered yields, as stacks. */
    public record Entry(ResourceLocation entity, ItemStack icon, List<ItemStack> hide, List<ItemStack> butchered) {
    }

    public ButcheryCategory(IGuiHelper helper) {
        super(TYPE, Component.translatable("bloodandbones.jei.category.butchery"), helper.createDrawableItemLike(BBItems.CLEAVER.get()), WIDTH, HEIGHT);
    }

    /** One entry per butchery table the server has sent us. */
    public static List<Entry> entries() {
        List<Entry> out = new ArrayList<>();
        ButcheryManager.clientAll().forEach((id, table) -> {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(table.entity()).orElse(null);
            if (type == null) {
                return;
            }
            SpawnEggItem egg = SpawnEggItem.byId(type);
            ItemStack icon = egg != null ? new ItemStack(egg) : new ItemStack(BBItems.MEAT_HOOK.get());
            List<ItemStack> hide = stacks(table.hide());
            List<Yield> all = new ArrayList<>();
            table.parts().values().forEach(all::addAll);
            List<ItemStack> butchered = stacks(all);
            if (!hide.isEmpty() || !butchered.isEmpty()) {
                out.add(new Entry(table.entity(), icon, hide, butchered));
            }
        });
        out.sort((a, b) -> a.entity().toString().compareTo(b.entity().toString()));
        return out;
    }

    /** Yields summed by item; placeholders shown with an example (a white sheep's wool, red mushrooms). */
    private static List<ItemStack> stacks(List<Yield> yields) {
        Map<Item, Float> totals = new LinkedHashMap<>();
        for (Yield yield : yields) {
            String id = yield.item().replace("{wool}", "white").replace("{mushroom}", "red");
            if (id.contains("{")) {
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(id);
            Item item = key == null ? null : BuiltInRegistries.ITEM.getOptional(key).orElse(null);
            if (item != null) {
                totals.merge(item, yield.count(), Float::sum);
            }
        }
        List<ItemStack> out = new ArrayList<>();
        totals.forEach((item, count) -> out.add(new ItemStack(item, Math.max(1, Math.round(count)))));
        return out;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 4, 36).setStandardSlotBackground().addItemStack(entry.icon());
        for (int i = 0; i < Math.min(3, entry.hide().size()); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 40 + i * 18, 22).setStandardSlotBackground().addItemStack(entry.hide().get(i));
        }
        for (int i = 0; i < Math.min(12, entry.butchered().size()); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 40 + (i % 6) * 18, 50 + (i / 6) * 18).setStandardSlotBackground().addItemStack(entry.butchered().get(i));
        }
    }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        BuiltInRegistries.ENTITY_TYPE.getOptional(entry.entity()).ifPresent(type ->
                graphics.drawString(font, type.getDescription(), 0, 0, 0xFF202020, false));
        if (!entry.hide().isEmpty()) {
            graphics.drawString(font, Component.translatable("bloodandbones.jei.butchery.skinned"), 40, 12, 0xFF404040, false);
        }
        graphics.drawString(font, Component.translatable("bloodandbones.jei.butchery.butchered"), 40, 40, 0xFF404040, false);
    }
}
