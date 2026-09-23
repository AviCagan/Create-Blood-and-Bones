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
 * and has its texture coordinates moved from the bloody sprite to the clean one. Block models are baked into
 * chunk meshes, so a change of mode redraws the world (BBClientConfig#redraw); items follow at once.
 */
public final class BloodlessSwap extends BakedModelWrapper<BakedModel> {
    /** Bloody texture -> clean texture, for every machine. */
    public static final Map<ResourceLocation, ResourceLocation> MACHINES = Map.of(
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_casing"), ResourceLocation.fromNamespaceAndPath("create", "block/andesite_casing"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/mangler_top"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/mangler_top_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/butcher_blade"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/butcher_blade_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_saw"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/bloody_saw_clean"),
            ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/deglover_roller"), ResourceLocation.fromNamespaceAndPath("bloodandbones", "block/deglover_roller_clean"));

    private final Map<ResourceLocation, ResourceLocation> swaps;
    /** Resolved on first use: the block atlas is only ready once baking is over. */
    @Nullable
    private Map<TextureAtlasSprite, TextureAtlasSprite> sprites;

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

    private List<BakedQuad> swap(List<BakedQuad> quads) {
        if (sprites == null) {
            TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
            Map<TextureAtlasSprite, TextureAtlasSprite> resolved = new HashMap<>();
            swaps.forEach((from, to) -> resolved.put(atlas.getSprite(from), atlas.getSprite(to)));
            sprites = resolved;
        }
        List<BakedQuad> out = new ArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            TextureAtlasSprite to = sprites.get(quad.getSprite());
            out.add(to == null ? quad : retexture(quad, to));
        }
        return out;
    }

    /** The same quad over another sprite: each corner's UV kept at the same place within its sprite. */
    private static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite to) {
        TextureAtlasSprite from = quad.getSprite();
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
