package com.avicagan.bloodandbones.backtank;

import net.minecraft.util.StringRepresentable;

/**
 * The Fluid Backtank's tiers: how many buckets each holds, and the armour it gives worn in the chest slot
 * (defence, toughness, knockback resistance).
 */
public enum BacktankTier implements StringRepresentable {
    COPPER("copper", 2, 4, 0.0F, 0.0F),
    GOLD("gold", 3, 5, 0.0F, 0.0F),
    IRON("iron", 4, 6, 0.0F, 0.0F),
    DIAMOND("diamond", 6, 8, 2.0F, 0.0F),
    BLOOD_STEEL("blood_steel", 8, 7, 1.0F, 0.0F),
    BLOOD_DIAMOND("blood_diamond", 16, 8, 2.5F, 0.05F),
    SOUL_NETHERITE("soul_netherite", 32, 8, 3.0F, 0.1F);

    private final String name;
    private final int buckets;
    private final int defense;
    private final float toughness;
    private final float knockbackResistance;

    BacktankTier(String name, int buckets, int defense, float toughness, float knockbackResistance) {
        this.name = name;
        this.buckets = buckets;
        this.defense = defense;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    /** In millibuckets. */
    public int capacity() {
        return buckets * 1000;
    }

    public int buckets() {
        return buckets;
    }

    public int defense() {
        return defense;
    }

    public float toughness() {
        return toughness;
    }

    public float knockbackResistance() {
        return knockbackResistance;
    }
}
