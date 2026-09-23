package com.avicagan.bloodandbones.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/**
 * Which parts of a body are its own, which are gone, and what has been fitted in their place. Kept on a
 * player as a data attachment (see {@link BBAttachments#BODY}): saved, kept through death, sent to clients.
 */
public final class Body {
    public enum State {
        NATURAL, MISSING, IMPLANT
    }

    private final EnumSet<BodyPart> lost = EnumSet.noneOf(BodyPart.class);
    private final EnumMap<BodyPart, ItemStack> implants = new EnumMap<>(BodyPart.class);

    public static final Codec<Body> CODEC = RecordCodecBuilder.create(i -> i.group(
            BodyPart.CODEC.listOf().optionalFieldOf("lost", List.of()).forGetter(b -> List.copyOf(b.lost)),
            Codec.unboundedMap(BodyPart.CODEC, ItemStack.CODEC).optionalFieldOf("implants", Map.of()).forGetter(b -> Map.copyOf(b.implants))
    ).apply(i, Body::of));

    public static final StreamCodec<RegistryFriendlyByteBuf, Body> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public Body() {
    }

    private static Body of(List<BodyPart> lost, Map<BodyPart, ItemStack> implants) {
        Body body = new Body();
        body.lost.addAll(lost);
        implants.forEach((part, stack) -> {
            if (!stack.isEmpty()) {
                body.lost.add(part);
                body.implants.put(part, stack.copyWithCount(1));
            }
        });
        return body;
    }

    public Body copy() {
        return of(List.copyOf(lost), implants);
    }

    public State state(BodyPart part) {
        if (!lost.contains(part)) {
            return State.NATURAL;
        }
        return implants.containsKey(part) ? State.IMPLANT : State.MISSING;
    }

    /** The implant in a part's place, or empty. */
    public ItemStack implant(BodyPart part) {
        return implants.getOrDefault(part, ItemStack.EMPTY);
    }

    /** Whether the part does its job for this wearer: its own, or an implant that is working. */
    public boolean works(BodyPart part, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity wearer) {
        return switch (state(part)) {
            case NATURAL -> true;
            case MISSING -> false;
            case IMPLANT -> implant(part).getItem() instanceof ImplantItem implant && implant.working(wearer);
        };
    }

    /** The part comes off; anything fitted there is gone with it. */
    public void lose(BodyPart part) {
        lost.add(part);
        implants.remove(part);
    }

    public void fit(BodyPart part, ItemStack implant) {
        lost.add(part);
        implants.put(part, implant.copyWithCount(1));
    }

    /** A limb of flesh back in its place. */
    public void restore(BodyPart part) {
        lost.remove(part);
        implants.remove(part);
    }

    /** Take the implant out, leaving the part missing. */
    public ItemStack unclip(BodyPart part) {
        ItemStack out = implants.remove(part);
        return out == null ? ItemStack.EMPTY : out;
    }

    public boolean whole() {
        return lost.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Body other) || !lost.equals(other.lost) || !implants.keySet().equals(other.implants.keySet())) {
            return false;
        }
        for (Map.Entry<BodyPart, ItemStack> e : implants.entrySet()) {
            if (!ItemStack.isSameItemSameComponents(e.getValue(), other.implants.get(e.getKey()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        return lost.hashCode() * 31 + implants.keySet().hashCode();
    }

    @Override
    public String toString() {
        return "Body{lost=" + lost + ", implants=" + implants + "}";
    }
}
