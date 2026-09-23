package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassPartBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.bleeding.BleedingRackBlock;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public class BBBlocks {
    /**
     * One block per limb cell of a physics carcass. Invisible; the limb is drawn by its block entity renderer
     * and the physics box comes from the block state. Never obtainable, never breakable.
     */
    public static final BlockEntry<CarcassPartBlock> CARCASS_PART = BloodAndBones.REGISTRATE
            .block("carcass_part", CarcassPartBlock::new)
            .properties(p -> p.mapColor(MapColor.COLOR_RED)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false))
            .blockstate(NonNullBiConsumer.noop())
            .loot(NonNullBiConsumer.noop())
            .lang("Carcass")
            .register();

    /** Hangs a dragged carcass by the hooked limb; a wall or ceiling mounted hook. */
    public static final BlockEntry<ShackleHookBlock> SHACKLE_HOOK = BloodAndBones.REGISTRATE
            .block("shackle_hook", ShackleHookBlock::new)
            .properties(p -> p.mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(net.minecraft.world.level.block.SoundType.CHAIN))
            .blockstate((c, p) -> p.directionalBlock(c.get(), p.models().getExistingFile(p.modLoc("block/shackle_hook"))))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Shackle Hook")
            .simpleItem()
            .register();

    /** Drip tray with a 4000 mB tank; Create pipes pull from its sides and bottom. */
    public static final BlockEntry<BleedingRackBlock> BLEEDING_RACK = BloodAndBones.REGISTRATE
            .block("bleeding_rack", BleedingRackBlock::new)
            .properties(p -> p.mapColor(MapColor.METAL)
                    .strength(2.0F, 6.0F)
                    .noOcclusion()
                    .requiresCorrectToolForDrops()
                    .sound(net.minecraft.world.level.block.SoundType.COPPER))
            .blockstate((c, p) -> p.simpleBlock(c.get(), p.models().getExistingFile(p.modLoc("block/bleeding_rack"))))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Bleeding Rack")
            .simpleItem()
            .register();

    /** Blood on the ground (see BloodStainBlock). No item: only bleeding makes it. */
    public static final BlockEntry<com.avicagan.bloodandbones.bleeding.BloodStainBlock> BLOOD_STAIN = BloodAndBones.REGISTRATE
            .block("blood_stain", com.avicagan.bloodandbones.bleeding.BloodStainBlock::new)
            .properties(p -> p.mapColor(MapColor.COLOR_RED)
                    .replaceable()
                    .noCollission()
                    .noOcclusion()
                    .instabreak()
                    .noLootTable()
                    .randomTicks()
                    .pushReaction(PushReaction.DESTROY)
                    .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK))
            .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStatesExcept(state -> {
                var model = p.models().getExistingFile(p.modLoc("block/blood_stain_" + state.getValue(com.avicagan.bloodandbones.bleeding.BloodStainBlock.SIZE)));
                // any of four turns, picked by position, so neighbouring stains do not repeat
                return new net.neoforged.neoforge.client.model.generators.ConfiguredModel[]{
                        new net.neoforged.neoforge.client.model.generators.ConfiguredModel(model, 0, 0, false),
                        new net.neoforged.neoforge.client.model.generators.ConfiguredModel(model, 0, 90, false),
                        new net.neoforged.neoforge.client.model.generators.ConfiguredModel(model, 0, 180, false),
                        new net.neoforged.neoforge.client.model.generators.ConfiguredModel(model, 0, 270, false)};
            }, com.avicagan.bloodandbones.bleeding.BloodStainBlock.AGE))
            .loot(NonNullBiConsumer.noop())
            // walking through it squelches, over whatever the ground sounds like
            .tag(net.minecraft.tags.BlockTags.COMBINATION_STEP_SOUND_BLOCKS)
            .lang("Blood Stain")
            .register();

    public static final BlockEntry<net.minecraft.world.level.block.Block> BLOOD_STEEL_BLOCK = BloodAndBones.REGISTRATE
            .block("blood_steel_block", net.minecraft.world.level.block.Block::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.CRIMSON_STEM))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE, net.minecraft.tags.BlockTags.NEEDS_IRON_TOOL)
            .lang("Block of Blood Steel")
            .simpleItem()
            .register();

    public static final BlockEntry<com.avicagan.bloodandbones.machine.CarcassMachineBlock> MANGLER = machine(com.avicagan.bloodandbones.machine.MachineKind.MANGLER, "Mangler");
    public static final BlockEntry<com.avicagan.bloodandbones.machine.CarcassMachineBlock> GUILLOTINE = machine(com.avicagan.bloodandbones.machine.MachineKind.GUILLOTINE, "Guillotine");
    public static final BlockEntry<com.avicagan.bloodandbones.machine.CarcassMachineBlock> BEHEADER = machine(com.avicagan.bloodandbones.machine.MachineKind.BEHEADER, "Beheader");
    public static final BlockEntry<com.avicagan.bloodandbones.machine.CarcassMachineBlock> DEGLOVER = machine(com.avicagan.bloodandbones.machine.MachineKind.DEGLOVER, "Deglover");

    /** A carcass machine: shaft from below, hand-made model, stress impact per RPM from its kind. */
    private static BlockEntry<com.avicagan.bloodandbones.machine.CarcassMachineBlock> machine(com.avicagan.bloodandbones.machine.MachineKind kind, String name) {
        return BloodAndBones.REGISTRATE
                .block(kind.id, p -> new com.avicagan.bloodandbones.machine.CarcassMachineBlock(p, kind))
                .initialProperties(com.simibubi.create.foundation.data.SharedProperties::stone)
                .properties(p -> p.mapColor(MapColor.METAL).noOcclusion().sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK))
                .transform(com.simibubi.create.foundation.data.TagGen.pickaxeOnly())
                .blockstate((c, p) -> p.simpleBlock(c.get(), p.models().getExistingFile(p.modLoc("block/" + kind.id))))
                .onRegister(block -> com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(block, () -> kind.stress))
                .lang(name)
                .simpleItem()
                .register();
    }

    public static final BlockEntry<com.avicagan.bloodandbones.cooking.SpitRoastBlock> SPIT_ROAST = BloodAndBones.REGISTRATE
            .block("spit_roast", com.avicagan.bloodandbones.cooking.SpitRoastBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.SPRUCE_FENCE)
            .properties(p -> p.noOcclusion())
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)
            .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStatesExcept(state -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                    .modelFile(p.models().getExistingFile(p.modLoc("block/spit_roast")))
                    .rotationY(state.getValue(com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock.HORIZONTAL_AXIS) == net.minecraft.core.Direction.Axis.X ? 0 : 90)
                    .build()))
            .onRegister(block -> com.simibubi.create.api.stress.BlockStressValues.IMPACTS.register(block, () -> 2.0))
            .lang("Spit Roast")
            // the block model is only the posts (the turning rod is drawn by the renderer), so the item gets its own with the rod
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/spit_roast_item"))).build()
            .register();

    public static final BlockEntry<com.avicagan.bloodandbones.cooking.SpecimenJarBlock> SPECIMEN_JAR = BloodAndBones.REGISTRATE
            .block("specimen_jar", com.avicagan.bloodandbones.cooking.SpecimenJarBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.GLASS)
            .properties(p -> p.noOcclusion())
            .blockstate((c, p) -> p.simpleBlock(c.get(), p.models().getExistingFile(p.modLoc("block/specimen_jar"))))
            .lang("Specimen Jar")
            .simpleItem()
            .register();

    /** A hook on a wall to hang a carried piece on for show. */
    public static final BlockEntry<com.avicagan.bloodandbones.cooking.ButcherHookBlock> BUTCHER_HOOK = BloodAndBones.REGISTRATE
            .block("butcher_hook", com.avicagan.bloodandbones.cooking.ButcherHookBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.IRON_BARS)
            .properties(p -> p.noOcclusion().noCollission())
            .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStates(state -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                    .modelFile(p.models().getExistingFile(p.modLoc("block/butcher_hook")))
                    .rotationY(((int) state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot() + 180) % 360)
                    .build()))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE, com.simibubi.create.AllTags.AllBlockTags.MOVABLE_EMPTY_COLLIDER.tag)
            .lang("Butcher's Hook")
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/butcher_hook"))).build()
            .register();

    public static void register() {
    }
}
