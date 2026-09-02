package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class BBTags {
    /** Blocks that slow rot when near a carcass: ice, snow, anything Create: Dragons Plus counts as a passive freezer. */
    public static final TagKey<Block> CHILLS = TagKey.create(Registries.BLOCK, BloodAndBones.asResource("chills"));
    /** Blocks that stop rot entirely when near a carcass: the deep-cold ices. */
    public static final TagKey<Block> PRESERVES = TagKey.create(Registries.BLOCK, BloodAndBones.asResource("preserves"));

    private BBTags() {
    }
}
