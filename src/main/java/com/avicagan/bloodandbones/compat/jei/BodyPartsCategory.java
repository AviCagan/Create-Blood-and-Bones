package com.avicagan.bloodandbones.compat.jei;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryManager;
import com.avicagan.bloodandbones.carcass.butchery.Yield;
import com.avicagan.bloodandbones.minion.MinionData;
import com.avicagan.bloodandbones.minion.TaskWords;
import com.avicagan.bloodandbones.parts.Hides;
import com.avicagan.bloodandbones.parts.Organs;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.parts.Traits;
import com.avicagan.bloodandbones.registry.BBItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A mob's Body Parts page (docs/PARTS-AND-TRAITS.md section 7.9), beside its Butchery page: what each of its parts gives
 * fitted into a minion (its traits, and what it brings to a minion's tasks: its knacks, what it holds with, a head's
 * disposition; docs/NEXT.md 1.4) and in carcass armour, its hide, and its organs (the items the Surgical Rig cuts out of it, shown
 * so JEI finds this page from a Gland) and what each of them gives, and a full set of it. Read from the parts data the
 * server sent, so a datapack's changes show.
 */
public class BodyPartsCategory extends AbstractRecipeCategory<BodyPartsCategory.Entry> {
    public static final RecipeType<Entry> TYPE = RecipeType.create(BloodAndBones.MOD_ID, "body_parts", Entry.class);
    private static final int WIDTH = 166;
    private static final int HEIGHT = 140;
    private static final int ROW = 8;
    /** The order parts are listed in: the body, then the head, the limbs and the tail. */
    private static final List<String> ORDER = List.of("torso", "torso_ext", "neck", "head", "arm", "leg", "tail", "extra");

    /** A mob, the items it gives up (organs, hides), and the lines of what its parts do. */
    public record Entry(ResourceLocation entity, ItemStack icon, List<ItemStack> organs, List<ItemStack> hides, List<FormattedText> lines) {
    }

    public BodyPartsCategory(IGuiHelper helper) {
        super(TYPE, Component.translatable("bloodandbones.jei.category.body_parts"), helper.createDrawableItemLike(BBItems.SURGICAL_RIG.get()), WIDTH, HEIGHT);
    }

    /** One page per mob with a rig. */
    public static List<Entry> entries() {
        PartsData.Store store = PartsData.CLIENT;
        List<Entry> out = new ArrayList<>();
        for (ResourceLocation entity : Organs.rigged(store)) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entity).orElse(null);
            if (type == null) {
                continue;
            }
            SpawnEggItem egg = SpawnEggItem.byId(type);
            ItemStack icon = egg != null ? new ItemStack(egg) : new ItemStack(BBItems.CARCASS_PIECE.get());
            ResolvedMob resolved = store.resolve(entity, false);
            Set<ResourceLocation> organIds = new LinkedHashSet<>();
            resolved.organLists().values().forEach(organIds::addAll);
            List<ItemStack> organs = new ArrayList<>();
            for (ResourceLocation organ : organIds) {
                organs.add(Organs.stack(store, organ, entity, false));
            }
            out.add(new Entry(entity, icon, organs, hides(entity), lines(store, resolved, organIds)));
        }
        return out;
    }

    /** What its hide comes off as: its butchery table's hide, and any item the hide_sources data map gives it. */
    private static List<ItemStack> hides(ResourceLocation entity) {
        List<ItemStack> out = new ArrayList<>();
        ButcheryManager.clientAll().values().stream().filter(t -> t.entity().equals(entity)).findFirst().ifPresent(table -> {
            for (Yield yield : table.hide()) {
                ResourceLocation id = ResourceLocation.tryParse(yield.item());
                Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item != null && out.stream().noneMatch(s -> s.is(item))) {
                    out.add(Hides.stamp(new ItemStack(item), entity));
                }
            }
        });
        BuiltInRegistries.ITEM.getDataMap(Hides.SOURCES).forEach((key, source) -> {
            Item item = BuiltInRegistries.ITEM.get(key);
            if (source.entity().equals(entity) && out.stream().noneMatch(s -> s.is(item))) {
                out.add(new ItemStack(item));
            }
        });
        return out;
    }

    private static List<FormattedText> lines(PartsData.Store store, ResolvedMob resolved, Set<ResourceLocation> organIds) {
        List<FormattedText> out = new ArrayList<>();
        List<String> keys = new ArrayList<>(new java.util.TreeSet<>(resolved.parts().keySet()));
        resolved.minion().keySet().stream().filter(k -> !keys.contains(k)).forEach(keys::add);
        keys.sort(java.util.Comparator.comparingInt((String k) -> {
            int at = ORDER.indexOf(k.contains(".") ? k.substring(0, k.indexOf('.')) : k);
            return at < 0 ? ORDER.size() : at;
        }).thenComparing(k -> k));
        for (String key : keys) {
            List<TraitList.Resolved> minion = MinionData.traits(resolved, key);
            ResolvedMob.Part part = resolved.parts().get(key);
            // what the part brings to a minion's tasks: its knacks, what it holds with, a head's disposition
            List<Component> facts = TaskWords.partFacts(resolved, Map.of(), key);
            if (minion.isEmpty() && facts.isEmpty() && (part == null || part.armour().isEmpty() && part.pieces().isEmpty())) {
                continue;
            }
            out.add(partName(key).withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
            if (!minion.isEmpty()) {
                out.add(Component.translatable("bloodandbones.jei.body_parts.minion", list(store, minion)).withStyle(ChatFormatting.DARK_GRAY));
            }
            for (Component fact : facts) {
                out.add(Component.literal(" ").append(fact).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (part != null && !part.armour().isEmpty()) {
                out.add(Component.translatable("bloodandbones.jei.body_parts.armour", list(store, part.armour())).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (part != null) {
                for (Map.Entry<String, List<TraitList.Resolved>> piece : part.pieces().entrySet()) {
                    out.add(Component.translatable("bloodandbones.jei.body_parts.armour_piece", Component.translatable("bloodandbones.piece." + piece.getKey()),
                            list(store, piece.getValue())).withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        out.add(Component.translatable("bloodandbones.jei.body_parts.hide_line", list(store, resolved.hide())).withStyle(ChatFormatting.DARK_GRAY));
        if (!organIds.isEmpty()) {
            out.add(Component.translatable("bloodandbones.jei.body_parts.organs").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        for (ResourceLocation organ : organIds) {
            ResolvedMob.Organ traits = resolved.organs().get(organ);
            out.add(Component.translatable("bloodandbones.jei.body_parts.organ_line", Organs.name(store, organ),
                    partName(Organs.partOf(store, resolved.entity(), false, organ))).withStyle(ChatFormatting.BLACK));
            out.add(Component.translatable("bloodandbones.jei.body_parts.organ_minion", list(store, traits == null ? List.of() : traits.minion()))
                    .withStyle(ChatFormatting.DARK_GRAY));
            MutableComponent pieces = Component.empty();
            List<String> fits = Organs.kind(store, organ).armourPieces();
            for (int i = 0; i < fits.size(); i++) {
                pieces.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Component.translatable("bloodandbones.piece." + fits.get(i)));
            }
            out.add(Component.translatable("bloodandbones.jei.body_parts.organ_armour", pieces, list(store, traits == null ? List.of() : traits.armour()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        resolved.fullSet().ifPresent(set -> {
            out.add(Component.translatable("bloodandbones.jei.body_parts.set", Component.translatable(set.name())).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD));
            out.add(Component.translatable("bloodandbones.jei.body_parts.set_traits", list(store, set.bonus()), list(store, set.drawback()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        });
        return out;
    }

    /** "Leg (hind)", from a part key. */
    private static MutableComponent partName(String key) {
        String base = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
        MutableComponent name = Component.translatable("bloodandbones.part_key." + base);
        return key.contains(".") ? name.append(" (" + key.substring(key.indexOf('.') + 1) + ")") : name;
    }

    private static MutableComponent list(PartsData.Store store, List<TraitList.Resolved> traits) {
        if (traits.isEmpty()) {
            return Component.translatable("bloodandbones.jei.body_parts.nothing");
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < traits.size(); i++) {
            out.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Traits.describe(store, traits.get(i)).withStyle(ChatFormatting.DARK_AQUA));
        }
        return out;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Entry entry, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 0, 0).setStandardSlotBackground().addItemStack(entry.icon());
        for (int i = 0; i < Math.min(ROW, entry.organs().size()); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, i * 18, 20).setStandardSlotBackground().addItemStack(entry.organs().get(i));
        }
        for (int i = 0; i < Math.min(2, entry.hides().size()); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, WIDTH - 18 - i * 18, 0).setStandardSlotBackground().addItemStack(entry.hides().get(i));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Entry entry, IFocusGroup focuses) {
        builder.addScrollBoxWidget(WIDTH, HEIGHT - 40, 0, 40).setContents(entry.lines());
    }

    @Override
    public void draw(Entry entry, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        BuiltInRegistries.ENTITY_TYPE.getOptional(entry.entity()).ifPresent(type ->
                graphics.drawString(font, type.getDescription(), 22, 5, 0xFF202020, false));
    }

    @Override
    public ResourceLocation getRegistryName(Entry entry) {
        return BloodAndBones.asResource("body_parts/" + entry.entity().getNamespace() + "/" + entry.entity().getPath());
    }
}
