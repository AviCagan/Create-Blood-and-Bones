package com.avicagan.bloodandbones.parts;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Where an ingredient came from: which mob, which part of it (head, torso, arm, leg, tail, or hide), and
 * whether it was a baby. Only this is stored; what it does is looked up in data when it is used.
 */
public record Source(ResourceLocation entity, String part, boolean baby) {
    public static final Codec<Source> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(Source::entity),
            Codec.STRING.optionalFieldOf("part", "").forGetter(Source::part),
            Codec.BOOL.optionalFieldOf("baby", false).forGetter(Source::baby)
    ).apply(i, Source::new));

    public static final StreamCodec<ByteBuf, Source> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, Source::entity, ByteBufCodecs.STRING_UTF8, Source::part, ByteBufCodecs.BOOL, Source::baby, Source::new);
}
