package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * One piece of a minion's body, as it was when fitted: which mob and which bone, how it looked, how fresh
 * it was. Only this is kept; what the piece does is looked up in data (docs/PARTS-AND-TRAITS.md section 4.10).
 * Parts never rot once fitted.
 */
public record PieceRef(ResourceLocation entity, String bone, ResourceLocation texture, List<CarcassPieceItem.Coat> coats, float freshness,
                       boolean skinned, Map<String, String> traits, boolean baby) {
    public static final Codec<PieceRef> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(PieceRef::entity),
            Codec.STRING.fieldOf("bone").forGetter(PieceRef::bone),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(PieceRef::texture),
            CarcassPieceItem.Coat.CODEC.listOf().optionalFieldOf("coats", List.of()).forGetter(PieceRef::coats),
            Codec.FLOAT.optionalFieldOf("freshness", 1.0F).forGetter(PieceRef::freshness),
            Codec.BOOL.optionalFieldOf("skinned", false).forGetter(PieceRef::skinned),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("traits", Map.of()).forGetter(PieceRef::traits),
            Codec.BOOL.optionalFieldOf("baby", false).forGetter(PieceRef::baby)
    ).apply(i, PieceRef::new));

    public static PieceRef of(CarcassPieceItem.Piece piece) {
        return new PieceRef(piece.entity(), piece.bone(), piece.texture(), piece.coats(), piece.freshness(), piece.skinned(), piece.traits(), piece.baby());
    }

    /** Back as a carried piece, at the freshness it went in with. */
    public CarcassPieceItem.Piece toPiece() {
        return new CarcassPieceItem.Piece(entity, bone, texture, coats, freshness, skinned, traits, 0.0F, 0.0F, 0.0F, baby);
    }
}
