package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.config.BBClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A baked model that, in bloodless mode, draws its bloody textures as clean ones: each quad keeps its shape
 * and has its texture coordinates moved from the bloody sprite to the clean one, and its particles come off
 * clean. Block models are baked into chunk meshes, so a change of mode redraws the world
 * (BBClientConfig#redraw); items and particles follow at once.
 */
public final class BloodlessSwap extends BakedModelWrapper<BakedModel> {
    /** Bloody texture -> clean texture, for every machine and the Bloody Casing (its joined-up edges too). */
    public static final Map<ResourceLocation, ResourceLocation> MACHINES = Map.of(
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_casing"), ResourceLocation.fromNamespaceAndPath("create", "block/andesite_casing"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_casing_connected"), ResourceLocation.fromNamespaceAndPath("create", "block/andesite_casing_connected"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/mangler_top"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/mangler_top_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/butcher_blade"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/butcher_blade_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_saw"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_saw_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/deglover_roller"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/deglover_roller_clean"));

    /** The Shackle Hook's and Butcher's Hook's point, red in normal play. */
    public static final Map<ResourceLocation, ResourceLocation> HOOK = Map.of(
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "item/meat_hook_point"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "item/meat_hook_point_clean"));

    /** The Surgery Table's padding comes out unstained. */
    public static final Map<ResourceLocation, ResourceLocation> SURGERY = Map.of(
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/surgery_table_top"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/surgery_table_top_clean"));
    /** The Butcher's Table comes out clean steel. */
    public static final Map<ResourceLocation, ResourceLocation> TABLE = Map.of(
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/butcher_table_top"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/butcher_table_top_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/butcher_table_side"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/butcher_table_side_clean"));

    /** The Gut Chain comes out as plain cord. */
    public static final Map<ResourceLocation, ResourceLocation> GUTS = Map.of(
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/gut_chain"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/gut_chain_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "item/gut_chain"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "item/gut_chain_clean"));

    /** Ribs and bone piles come out bleached and dry: no wet gristle at the joints, no blood between the bones. */
    public static final Map<ResourceLocation, ResourceLocation> BONES = Map.of(
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/rib_bone"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/rib_bone_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/rib_joint"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/rib_joint_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bone_pile_top"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bone_pile_top_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bone_pile_side"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bone_pile_side_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "item/bone_pile"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "item/bone_pile_clean"));

    /** The bloody brass and copper casings come out as Create's own, as the Bloody Casing comes out andesite. */
    public static final Map<ResourceLocation, ResourceLocation> CLADDING = Map.of(
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_brass_casing"), ResourceLocation.fromNamespaceAndPath("create", "block/brass_casing"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_brass_casing_connected"), ResourceLocation.fromNamespaceAndPath("create", "block/brass_casing_connected"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_copper_casing"), ResourceLocation.fromNamespaceAndPath("create", "block/copper_casing"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_copper_casing_connected"), ResourceLocation.fromNamespaceAndPath("create", "block/copper_casing_connected"));

    private final Map<ResourceLocation, ResourceLocation> swaps;
    /** Resolved on first use: the block atlas is only ready once baking is over. */
    @Nullable
    private volatile Map<TextureAtlasSprite, TextureAtlasSprite> sprites;

    public BloodlessSwap(BakedModel model, Map<ResourceLocation, ResourceLocation> swaps) {
        super(model);
        this.swaps = swaps;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        List<BakedQuad> quads = super.getQuads(state, side, rand);
        return BBClientConfig.bloodless() ? swap(quads) : quads;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, data, renderType);
        return BBClientConfig.bloodless() ? swap(quads) : quads;
    }

    /** The wrapped model would hand the item renderer itself, skipping the swap. */
    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return List.of(this);
    }

    /** Likewise here: the item renderer carries on with whatever model this returns. */
    @Override
    public BakedModel applyTransform(net.minecraft.world.item.ItemDisplayContext context, com.mojang.blaze3d.vertex.PoseStack poseStack, boolean leftHand) {
        originalModel.applyTransform(context, poseStack, leftHand);
        return this;
    }

    /**
     * The bits that fly off when the block is broken, dug, run or landed on, and off the item when it breaks:
     * the clean texture too. Particles ask the model each time, so this follows the mode at once.
     */
    @Override
    public TextureAtlasSprite getParticleIcon() {
        TextureAtlasSprite sprite = super.getParticleIcon();
        return BBClientConfig.bloodless() ? sprites().getOrDefault(sprite, sprite) : sprite;
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        TextureAtlasSprite sprite = super.getParticleIcon(data);
        return BBClientConfig.bloodless() ? sprites().getOrDefault(sprite, sprite) : sprite;
    }

    private Map<TextureAtlasSprite, TextureAtlasSprite> sprites() {
        // chunk builder threads get here too: build the map whole, then publish it
        Map<TextureAtlasSprite, TextureAtlasSprite> map = sprites;
        if (map == null) {
            TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            Map<TextureAtlasSprite, TextureAtlasSprite> resolved = new HashMap<>();
            swaps.forEach((from, to) -> resolved.put(atlas.getSprite(from), atlas.getSprite(to)));
            map = Map.copyOf(resolved);
            sprites = map;
        }
        return map;
    }

    private List<BakedQuad> swap(List<BakedQuad> quads) {
        Map<TextureAtlasSprite, TextureAtlasSprite> map = sprites();
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            TextureAtlasSprite from = drawnFrom(quad, map);
            out.add(from == null ? quad : retexture(quad, from, map.get(from)));
        }
        return out;
    }

    /**
     * Which of the swapped sprites the quad actually shows, found by where its UVs are rather than by its
     * sprite field: Create's connected textures move the UVs onto the joined-up sheet but leave the field
     * naming the block's plain sprite.
     */
    @Nullable
    private static TextureAtlasSprite drawnFrom(BakedQuad quad, Map<TextureAtlasSprite, TextureAtlasSprite> map) {
        int[] vertices = quad.getVertices();
        int stride = vertices.length / 4;
        float u = 0;
        float v = 0;
        for (int i = 0; i < 4; i++) {
            u += Float.intBitsToFloat(vertices[i * stride + 4]) / 4.0F;
            v += Float.intBitsToFloat(vertices[i * stride + 5]) / 4.0F;
        }
        for (TextureAtlasSprite sprite : map.keySet()) {
            if (u >= sprite.getU0() && u <= sprite.getU1() && v >= sprite.getV0() && v <= sprite.getV1()) {
                return sprite;
            }
        }
        return null;
    }

    /** The same quad over another sprite: each corner's UV kept at the same place within its sprite. */
    private static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite from, TextureAtlasSprite to) {
        int[] vertices = quad.getVertices().clone();
        int stride = vertices.length / 4;
        for (int i = 0; i < 4; i++) {
            // block vertex format: position (3), colour (1), then UV (2)
            int uv = i * stride + 4;
            float u = (Float.intBitsToFloat(vertices[uv]) - from.getU0()) / (from.getU1() - from.getU0());
            float v = (Float.intBitsToFloat(vertices[uv + 1]) - from.getV0()) / (from.getV1() - from.getV0());
            vertices[uv] = Float.floatToRawIntBits(to.getU0() + u * (to.getU1() - to.getU0()));
            vertices[uv + 1] = Float.floatToRawIntBits(to.getV0() + v * (to.getV1() - to.getV0()));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), to, quad.isShade(), quad.hasAmbientOcclusion());
    }
}
