package com.avicagan.bloodandbones.carcass.rig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class RigCodecs {
    public static final Codec<Vector3f> VEC3 = Codec.FLOAT.listOf().comapFlatMap(
            list -> list.size() == 3
                    ? DataResult.success(new Vector3f(list.get(0), list.get(1), list.get(2)))
                    : DataResult.error(() -> "Expected 3 floats, got " + list.size()),
            v -> List.of(v.x, v.y, v.z));

    /** Quaternion stored as [x, y, z, w]. */
    public static final Codec<Quaternionf> QUAT = Codec.FLOAT.listOf().comapFlatMap(
            list -> list.size() == 4
                    ? DataResult.success(new Quaternionf(list.get(0), list.get(1), list.get(2), list.get(3)))
                    : DataResult.error(() -> "Expected 4 floats, got " + list.size()),
            q -> List.of(q.x, q.y, q.z, q.w));

    private RigCodecs() {
    }
}
