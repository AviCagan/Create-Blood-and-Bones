package com.avicagan.bloodandbones.minion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a minion is built of (docs/PARTS-AND-TRAITS.md section 6): a torso, and a piece in each socket that has
 * one. Organic (blood, unskinned pieces) or cybernetic (soul blood, skinned pieces). Kept on the Surgery Table
 * while it is built and on the minion once woken.
 */
public record MinionBuild(boolean cybernetic, PieceRef torso, List<Fitted> parts) {
    /** A piece in a socket; the socket is named for the torso bone it stands in for ("right_front_leg", "head"). */
    public record Fitted(String socket, PieceRef piece) {
        public static final Codec<Fitted> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("socket").forGetter(Fitted::socket),
                PieceRef.CODEC.fieldOf("piece").forGetter(Fitted::piece)
        ).apply(i, Fitted::new));
    }

    public static final Codec<MinionBuild> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("cybernetic", false).forGetter(MinionBuild::cybernetic),
            PieceRef.CODEC.fieldOf("torso").forGetter(MinionBuild::torso),
            Fitted.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(MinionBuild::parts)
    ).apply(i, MinionBuild::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MinionBuild> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static MinionBuild of(PieceRef torso) {
        return new MinionBuild(false, torso, List.of());
    }

    public Optional<PieceRef> in(String socket) {
        for (Fitted f : parts) {
            if (f.socket().equals(socket)) {
                return Optional.of(f.piece());
            }
        }
        return Optional.empty();
    }

    public MinionBuild with(String socket, PieceRef piece) {
        List<Fitted> out = new ArrayList<>(parts);
        out.removeIf(f -> f.socket().equals(socket));
        out.add(new Fitted(socket, piece));
        return new MinionBuild(cybernetic, torso, List.copyOf(out));
    }

    /** Without the last piece fitted. */
    public MinionBuild withoutLast() {
        return parts.isEmpty() ? this : new MinionBuild(cybernetic, torso, List.copyOf(parts.subList(0, parts.size() - 1)));
    }
}
