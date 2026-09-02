package com.avicagan.bloodandbones.carcass.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * An extra coat drawn over every bone after the skin: a sheep's wool, a horse's markings.
 *
 * @param layer   model layer the parts come from ("main", or "fur" for sheep wool); same part names
 * @param texture texture path, may contain {@code {variant}}-style placeholders resolved at death
 * @param tint    name of a tint the dying mob provides ("wool"), empty for none
 * @param unless  name of a flag the dying mob may set that skips this coat ("sheared", "no_markings")
 */
public record RenderPass(String layer, String texture, Optional<String> tint, Optional<String> unless) {
    public static final Codec<RenderPass> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("layer", "main").forGetter(RenderPass::layer),
            Codec.STRING.fieldOf("texture").forGetter(RenderPass::texture),
            Codec.STRING.optionalFieldOf("tint").forGetter(RenderPass::tint),
            Codec.STRING.optionalFieldOf("unless").forGetter(RenderPass::unless)
    ).apply(i, RenderPass::new));
}
