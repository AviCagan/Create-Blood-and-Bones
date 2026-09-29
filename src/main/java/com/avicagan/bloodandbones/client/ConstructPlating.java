package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The construct look (brief rule 4: "constructs not corpses", "clean plating"): in bloodless mode a carcass wears a
 * plated copy of the mob's own texture instead of its hide, made here once per texture and kept. The copy keeps the
 * mob's markings as panels of steel in three tones, with a dark seam wherever the markings change and a rivet
 * every few pixels, so a cow still reads as a cow, built of plates. A skinned carcass (bare meat) comes out as the
 * darker frame under the plating. Like BloodlessSwap's clean textures, it only changes what is drawn.
 */
public final class ConstructPlating {
    /**
     * Texture -> its plated copy, or the texture itself if it could not be read; and the same for the frame. Two maps
     * keyed by the texture, so the lookup every bone of every carcass makes each frame builds nothing.
     */
    private static final Map<ResourceLocation, ResourceLocation> PLATED = new HashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> FRAMES = new HashMap<>();
    /** Pixels between rivets, and where in each cell a rivet sits. */
    private static final int RIVETS = 6;

    private ConstructPlating() {
    }

    /** The plated copy of a mob texture, made the first time it is asked for; render thread only. */
    public static ResourceLocation plated(ResourceLocation texture, boolean frame) {
        Map<ResourceLocation, ResourceLocation> made = frame ? FRAMES : PLATED;
        ResourceLocation plated = made.get(texture);
        if (plated == null) {
            plated = make(texture, frame);
            made.put(texture, plated);
        }
        return plated;
    }

    /** After a resource reload the copies are made again from whatever the textures are now. */
    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        for (Map<ResourceLocation, ResourceLocation> made : List.of(PLATED, FRAMES)) {
            made.values().forEach(to -> {
                if (to.getNamespace().equals(BloodAndBones.MOD_ID) && to.getPath().startsWith("plated/")) {
                    mc.getTextureManager().release(to);
                }
            });
            made.clear();
        }
    }

    private static ResourceLocation make(ResourceLocation texture, boolean frame) {
        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> resource = mc.getResourceManager().getResource(texture);
        if (resource.isEmpty()) {
            return texture;
        }
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            NativeImage plated = plate(image, frame);
            ResourceLocation out = BloodAndBones.asResource("plated/" + texture.getNamespace() + "/" + texture.getPath() + (frame ? "_frame" : ""));
            mc.getTextureManager().register(out, new DynamicTexture(plated));
            return out;
        } catch (Exception e) {
            BloodAndBones.LOGGER.warn("Could not plate {} for bloodless mode", texture, e);
            return texture;
        }
    }

    /** The plating itself, pixel by pixel (NativeImage keeps colours as ABGR). */
    static NativeImage plate(NativeImage in, boolean frame) {
        int w = in.getWidth();
        int h = in.getHeight();
        // each pixel's tone: its brightness, snapped to three plates
        int[] tone = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = in.getPixelRGBA(x, y);
                float light = ((abgr & 0xFF) * 0.3F + ((abgr >> 8) & 0xFF) * 0.59F + ((abgr >> 16) & 0xFF) * 0.11F) / 255.0F;
                tone[y * w + x] = light < 0.33F ? 0 : light < 0.62F ? 1 : 2;
            }
        }
        NativeImage out = new NativeImage(w, h, true);
        float[] steel = frame ? new float[]{0.22F, 0.30F, 0.38F} : new float[]{0.46F, 0.60F, 0.74F};
        int grid = frame ? 4 : 8;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int abgr = in.getPixelRGBA(x, y);
                int alpha = (abgr >>> 24) & 0xFF;
                if (alpha == 0) {
                    out.setPixelRGBA(x, y, 0);
                    continue;
                }
                int t = tone[y * w + x];
                float value = steel[t];
                // a seam where the tone changes to a darker plate below or to the right, and across the frame's lattice
                boolean seam = (x + 1 < w && tone[y * w + x + 1] < t) || (y + 1 < h && tone[(y + 1) * w + x] < t)
                        || (frame && (x % grid == 0 || y % grid == 0));
                if (seam) {
                    value *= 0.55F;
                } else if (x % RIVETS == 2 && y % RIVETS == 2) {
                    // a rivet head, lit from above left
                    value = Math.min(1.0F, value * 1.35F);
                } else if (x % RIVETS == 3 && y % RIVETS == 3) {
                    value *= 0.7F;
                } else if ((x + y) % 7 == 0) {
                    // brushed: a faint streak across the plate
                    value *= 1.06F;
                }
                // cold steel: a touch of blue
                int r = clamp(value * 0.94F);
                int g = clamp(value * 0.98F);
                int b = clamp(value * 1.06F);
                out.setPixelRGBA(x, y, alpha << 24 | b << 16 | g << 8 | r);
            }
        }
        return out;
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, Math.round(v * 255.0F)));
    }
}
