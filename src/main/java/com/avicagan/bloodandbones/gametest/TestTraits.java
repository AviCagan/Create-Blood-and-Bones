package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.MobGroup;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.Trait;
import com.avicagan.bloodandbones.registry.BBItems;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Traits and mob data a game test makes for itself, under ids of its own ("bloodandbones:test/..." and the made-up mob
 * "bloodandbones:test_mob/..."), on top of the loaded data for the rest of the run. Real traits and real mobs are never
 * changed; the data lints, the lists and the sync to clients never see these. Pick ids no other test uses: tests share
 * one world and one data store.
 */
public final class TestTraits {
    private TestTraits() {
    }

    private static DynamicOps<JsonElement> ops(GameTestHelper helper) {
        return RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
    }

    /** A trait written as its data file would be, as "bloodandbones:test/&lt;path&gt;". */
    public static ResourceLocation trait(GameTestHelper helper, String path, String json) {
        ResourceLocation id = BloodAndBones.asResource("test/" + path);
        Trait trait = Trait.CODEC.parse(ops(helper), JsonParser.parseString(json)).getOrThrow(error -> new IllegalArgumentException(id + ": " + error));
        PartsData.SERVER.addTestTrait(id, trait);
        return id;
    }

    /**
     * A mob file for a made-up mob, "bloodandbones:test_mob/&lt;path&gt;", written as a mob_traits file would be. It resolves
     * as any mob: the biped archetype (a made-up mob has no rig to count legs on) with this on top, so a list that must
     * hold only the test's traits says {"replace": true, "add": [...]}.
     */
    public static ResourceLocation mob(GameTestHelper helper, String path, String json) {
        ResourceLocation id = BloodAndBones.asResource("test_mob/" + path);
        PartsData.SERVER.addTestMobFile(id, MobGroup.parse(id, JsonParser.parseString(json).getAsJsonObject(), MobGroup.Kind.MOB, ops(helper)));
        return id;
    }

    /** A loot condition written as a trait's "requirements" would be. */
    public static LootItemCondition condition(GameTestHelper helper, String json) {
        return LootItemCondition.DIRECT_CODEC.parse(ops(helper), JsonParser.parseString(json)).getOrThrow(IllegalArgumentException::new);
    }

    /** A piece of carcass armour ("helmet", "chestplate", "leggings", "boots") all of one mob. */
    public static ItemStack piece(String piece, ResourceLocation mob) {
        var item = switch (piece) {
            case "helmet" -> BBItems.CARCASS_HELMET.get();
            case "chestplate" -> BBItems.CARCASS_CHESTPLATE.get();
            case "leggings" -> BBItems.CARCASS_LEGGINGS.get();
            default -> BBItems.CARCASS_BOOTS.get();
        };
        return CarcassArmourItem.make(new ItemStack(item), CarcassArmour.of(piece, mob, false), PartsData.SERVER);
    }
}
