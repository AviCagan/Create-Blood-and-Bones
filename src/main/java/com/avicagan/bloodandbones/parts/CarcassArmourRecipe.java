package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.registry.BBRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A piece of carcass armour from scraps (docs/PARTS-AND-TRAITS.md section 7.2). It is a shaped recipe, so the
 * recipe viewer shows it and Create's Mechanical Crafters make it, but it also checks where the scraps came
 * from: the body's cells (H head, T torso, L legs) all one mob and the right part, a chestplate's shoulders
 * (S) arm scraps of any one mob, a pair of leggings' hips (P) tail scraps of any one mob or more of the body's
 * legs. A mob with no bone of a needed kind (a blaze has no legs) uses its torso scraps there.
 */
public class CarcassArmourRecipe extends ShapedRecipe {
    /** Which cell is which, per piece: the patterns are fixed by the design. */
    private static final Map<String, String[]> ROLES = Map.of(
            "helmet", new String[]{"HHH", "H.H"},
            "chestplate", new String[]{"S.S", "TTT", "TTT"},
            "leggings", new String[]{"PPP", "L.L", "L.L"},
            "boots", new String[]{"L.L", "L.L"});

    private final ShapedRecipePattern shape;
    private final ItemStack output;
    private final String piece;

    public CarcassArmourRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern, ItemStack result, String piece) {
        super(group, category, pattern, result, false);
        this.shape = pattern;
        this.output = result;
        this.piece = piece;
    }

    public String piece() {
        return piece;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return super.matches(input, level) && armour(input, PartsData.of(level)) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        PartsData.Store store = CarcassArmourItem.store();
        CarcassArmour armour = armour(input, store);
        return armour == null ? ItemStack.EMPTY : CarcassArmourItem.make(output.copy(), armour, store);
    }

    /** What the scraps in these cells make, or null if they do not fit together. */
    @Nullable
    public CarcassArmour armour(CraftingInput input, PartsData.Store store) {
        String[] grid = ROLES.get(piece);
        if (grid == null || input.width() != grid[0].length() || input.height() != grid.length) {
            return null;
        }
        ResourceLocation body = null;
        boolean baby = false;
        List<Source> body0 = new ArrayList<>();
        List<Source> shoulders = new ArrayList<>();
        List<Source> hips = new ArrayList<>();
        for (int y = 0; y < grid.length; y++) {
            for (int x = 0; x < grid[y].length(); x++) {
                char role = grid[y].charAt(x);
                if (role == '.') {
                    continue;
                }
                Source source = ScrapsItem.source(input.getItem(x, y));
                if (source == null) {
                    return null;
                }
                switch (role) {
                    case 'S' -> shoulders.add(source);
                    case 'P' -> hips.add(source);
                    default -> body0.add(source);
                }
            }
        }
        String needed = switch (piece) {
            case "helmet" -> "head";
            case "chestplate" -> "torso";
            default -> "leg";
        };
        for (Source source : body0) {
            if (body == null) {
                body = source.entity();
                baby = source.baby();
            } else if (!body.equals(source.entity())) {
                return null;
            }
        }
        if (body == null) {
            return null;
        }
        for (Source source : body0) {
            if (!source.part().equals(needed) && !(source.part().equals("torso") && lacks(store, body, needed))) {
                return null;
            }
        }
        Optional<ResourceLocation> shoulderMob = Optional.empty();
        if (!shoulders.isEmpty()) {
            ResourceLocation mob = shoulders.get(0).entity();
            for (Source source : shoulders) {
                boolean arm = source.part().equals("arm");
                boolean standIn = source.entity().equals(body) && source.part().equals("torso") && lacks(store, body, "arm");
                if (!source.entity().equals(mob) || !arm && !standIn) {
                    return null;
                }
            }
            shoulderMob = shoulders.get(0).part().equals("arm") ? Optional.of(mob) : Optional.empty();
        }
        Optional<ResourceLocation> hipMob = Optional.empty();
        if (!hips.isEmpty()) {
            ResourceLocation mob = hips.get(0).entity();
            for (Source source : hips) {
                boolean tail = source.part().equals("tail");
                boolean legs = source.entity().equals(body) && (source.part().equals("leg") || source.part().equals("torso") && lacks(store, body, "leg"));
                if (!source.entity().equals(mob) || !tail && !legs) {
                    return null;
                }
            }
            hipMob = hips.get(0).part().equals("tail") ? Optional.of(mob) : Optional.empty();
        }
        return new CarcassArmour(piece, body, baby, shoulderMob, hipMob, Optional.empty(), 0);
    }

    /** Whether this mob's rig has no bone of the slot that makes this part. */
    static boolean lacks(PartsData.Store store, ResourceLocation entity, String part) {
        Optional<Rig> rig = store.rig(entity, false);
        if (rig.isEmpty()) {
            return false;
        }
        for (var bone : rig.get().bones()) {
            if (PartSlots.of(store, entity, rig.get(), bone.name()).slot().scrapPart().equals(part)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BBRecipes.CARCASS_ARMOUR.get();
    }

    public static class Serializer implements RecipeSerializer<CarcassArmourRecipe> {
        public static final MapCodec<CarcassArmourRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.STRING.optionalFieldOf("group", "").forGetter(ShapedRecipe::getGroup),
                CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.EQUIPMENT).forGetter(ShapedRecipe::category),
                ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.shape),
                ItemStack.STRICT_CODEC.fieldOf("result").forGetter(r -> r.output),
                Codec.STRING.fieldOf("piece").forGetter(CarcassArmourRecipe::piece)
        ).apply(i, CarcassArmourRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, CarcassArmourRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ShapedRecipe::getGroup,
                CraftingBookCategory.STREAM_CODEC, ShapedRecipe::category,
                ShapedRecipePattern.STREAM_CODEC, r -> r.shape,
                ItemStack.STREAM_CODEC, r -> r.output,
                ByteBufCodecs.STRING_UTF8, CarcassArmourRecipe::piece,
                CarcassArmourRecipe::new);

        @Override
        public MapCodec<CarcassArmourRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, CarcassArmourRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
