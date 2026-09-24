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
                var model = p.models().getExistingFile(p.modLoc((state.getValue(com.avicagan.bloodandbones.bleeding.BloodStainBlock.SOUL) ? "block/soul_blood_stain_" : "block/blood_stain_")
                        + state.getValue(com.avicagan.bloodandbones.bleeding.BloodStainBlock.SIZE)));
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

    /** Andesite casing smeared with blood; joins up with its neighbours like Create's casings. */
    public static final BlockEntry<com.simibubi.create.content.decoration.encasing.CasingBlock> BLOODY_CASING = BloodAndBones.REGISTRATE
            .block("bloody_casing", com.simibubi.create.content.decoration.encasing.CasingBlock::new)
            .properties(p -> p.mapColor(MapColor.CRIMSON_NYLIUM))
            .transform(com.simibubi.create.foundation.data.BuilderTransformers.casing(() -> BBSpriteShifts.BLOODY_CASING))
            .lang("Bloody Casing")
            .register();

    /** Brass casing splashed with blood, made and joined up as the Bloody Casing. */
    public static final BlockEntry<com.simibubi.create.content.decoration.encasing.CasingBlock> BLOODY_BRASS_CASING = BloodAndBones.REGISTRATE
            .block("bloody_brass_casing", com.simibubi.create.content.decoration.encasing.CasingBlock::new)
            .properties(p -> p.mapColor(MapColor.CRIMSON_NYLIUM))
            .transform(com.simibubi.create.foundation.data.BuilderTransformers.casing(() -> BBSpriteShifts.BLOODY_BRASS_CASING))
            .lang("Bloody Brass Casing")
            .register();

    /** Copper casing splashed with blood, made and joined up as the Bloody Casing. */
    public static final BlockEntry<com.simibubi.create.content.decoration.encasing.CasingBlock> BLOODY_COPPER_CASING = BloodAndBones.REGISTRATE
            .block("bloody_copper_casing", com.simibubi.create.content.decoration.encasing.CasingBlock::new)
            .properties(p -> p.mapColor(MapColor.CRIMSON_NYLIUM))
            .transform(com.simibubi.create.foundation.data.BuilderTransformers.casing(() -> BBSpriteShifts.BLOODY_COPPER_CASING))
            .lang("Bloody Copper Casing")
            .register();

    /** A steel table to lay a piece on and chop it up with a Cleaver. */
    public static final BlockEntry<com.avicagan.bloodandbones.cooking.ButcherTableBlock> BUTCHER_TABLE = BloodAndBones.REGISTRATE
            .block("butcher_table", com.avicagan.bloodandbones.cooking.ButcherTableBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK)
            .properties(p -> p.noOcclusion().strength(3.0F, 6.0F))
            .blockstate((c, p) -> p.simpleBlock(c.get(), p.models().getExistingFile(p.modLoc("block/butcher_table"))))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Butcher's Table")
            .simpleItem()
            .register();

    /** A string of guts hung like a chain. Squelches. */
    /** The end of a Rotational Coupler's shaft in front of a machine: hidden, placed and taken away by the module. */
    public static final BlockEntry<com.avicagan.bloodandbones.cyber.CouplerBlock> COUPLER = BloodAndBones.REGISTRATE
            .block("coupler", com.avicagan.bloodandbones.cyber.CouplerBlock::new)
            .properties(p -> p.mapColor(MapColor.NONE).noCollission().noOcclusion().noLootTable().strength(-1.0F, 3600000.0F)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY).replaceable())
            .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStates(state -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                    .modelFile(p.models().getExistingFile(p.mcLoc("block/air"))).build()))
            .lang("Rotational Coupler")
            .register();

    public static final BlockEntry<net.minecraft.world.level.block.ChainBlock> GUT_CHAIN = BloodAndBones.REGISTRATE
            .block("gut_chain", net.minecraft.world.level.block.ChainBlock::new)
            .properties(p -> p.mapColor(MapColor.COLOR_PINK).forceSolidOn().strength(0.5F).noOcclusion()
                    .sound(net.minecraft.world.level.block.SoundType.SLIME_BLOCK))
            .blockstate((c, p) -> p.axisBlock(c.get(), p.models().getExistingFile(p.modLoc("block/gut_chain")),
                    p.models().getExistingFile(p.modLoc("block/gut_chain"))))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE)
            .lang("Gut Chain")
            .item().model((c, p) -> p.generated(c, p.modLoc("item/gut_chain"))).build()
            .register();

    /** Morgue table: holds one item; tables side by side join into one run, legs only at its outer corners. */
    public static final BlockEntry<com.avicagan.bloodandbones.decoration.SteelTableBlock> STEEL_TABLE = BloodAndBones.REGISTRATE
            .block("steel_table", com.avicagan.bloodandbones.decoration.SteelTableBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK)
            .properties(p -> p.noOcclusion().strength(3.0F, 6.0F).mapColor(MapColor.METAL))
            .blockstate((c, p) -> {
                // the top always; a lip along each side that joins nothing, and its corner where either side is open;
                // a leg in each corner where neither side joins (models drawn for the north side and north-west corner)
                var builder = p.getMultipartBuilder(c.get());
                builder.part().modelFile(p.models().getExistingFile(p.modLoc("block/steel_table_top"))).addModel().end();
                net.minecraft.core.Direction[] sides = {net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.EAST,
                        net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST};
                for (int i = 0; i < 4; i++) {
                    net.minecraft.core.Direction side = sides[i];
                    net.minecraft.core.Direction before = sides[(i + 3) % 4];
                    builder.part().modelFile(p.models().getExistingFile(p.modLoc("block/steel_table_lip"))).rotationY(i * 90).addModel()
                            .condition(com.avicagan.bloodandbones.decoration.SteelTableBlock.side(side), false).end();
                    builder.part().modelFile(p.models().getExistingFile(p.modLoc("block/steel_table_lip_corner"))).rotationY(i * 90).addModel()
                            .useOr()
                            .condition(com.avicagan.bloodandbones.decoration.SteelTableBlock.side(side), false)
                            .condition(com.avicagan.bloodandbones.decoration.SteelTableBlock.side(before), false).end();
                    builder.part().modelFile(p.models().getExistingFile(p.modLoc("block/steel_table_leg"))).rotationY(i * 90).addModel()
                            .condition(com.avicagan.bloodandbones.decoration.SteelTableBlock.side(side), false)
                            .condition(com.avicagan.bloodandbones.decoration.SteelTableBlock.side(before), false).end();
                }
            })
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Steel Table")
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/steel_table_item"))).build()
            .register();

    /** Morgue shelves: two shelves of two places, each holding one item on show. */
    public static final BlockEntry<com.avicagan.bloodandbones.decoration.SteelRackBlock> STEEL_RACK = BloodAndBones.REGISTRATE
            .block("steel_rack", com.avicagan.bloodandbones.decoration.SteelRackBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK)
            .properties(p -> p.noOcclusion().strength(3.0F, 6.0F).mapColor(MapColor.METAL))
            .blockstate((c, p) -> p.horizontalBlock(c.get(), p.models().getExistingFile(p.modLoc("block/steel_rack"))))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Steel Rack")
            .simpleItem()
            .register();

    /** A segment of giant rib; stacks and spans of them shape themselves into arches. */
    public static final BlockEntry<com.avicagan.bloodandbones.decoration.RibcageArchBlock> RIBCAGE_ARCH = BloodAndBones.REGISTRATE
            .block("ribcage_arch", com.avicagan.bloodandbones.decoration.RibcageArchBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.BONE_BLOCK)
            .properties(p -> p.noOcclusion().strength(1.5F).mapColor(MapColor.SAND))
            .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStates(state -> net.neoforged.neoforge.client.model.generators.ConfiguredModel.builder()
                    // drawn facing north, bending towards z = 0
                    .modelFile(p.models().getExistingFile(p.modLoc("block/ribcage_arch_"
                            + state.getValue(com.avicagan.bloodandbones.decoration.RibcageArchBlock.SHAPE).getSerializedName())))
                    .rotationY(((int) state.getValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).toYRot() + 180) % 360)
                    .build()))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Ribcage Arch")
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/ribcage_arch_item"))).build()
            .register();

    /** Bones heaped in layers, as snow lies. Each layer drops two bones. */
    public static final BlockEntry<com.avicagan.bloodandbones.decoration.BonePileBlock> BONE_PILE = BloodAndBones.REGISTRATE
            .block("bone_pile", com.avicagan.bloodandbones.decoration.BonePileBlock::new)
            // dug with a shovel or by hand, as gravel, with the bone block's sounds
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.GRAVEL)
            .properties(p -> p.noOcclusion().strength(0.4F).mapColor(MapColor.SAND).sound(net.minecraft.world.level.block.SoundType.BONE_BLOCK).forceSolidOff()
                    .isViewBlocking((state, level, pos) -> state.getValue(com.avicagan.bloodandbones.decoration.BonePileBlock.LAYERS) >= com.avicagan.bloodandbones.decoration.BonePileBlock.MAX_LAYERS))
            .blockstate((c, p) -> p.getVariantBuilder(c.get()).forAllStates(state -> {
                var model = p.models().getExistingFile(p.modLoc("block/bone_pile_height" + 2 * state.getValue(com.avicagan.bloodandbones.decoration.BonePileBlock.LAYERS)));
                // any of four turns, picked by position, so the loose bones on top do not repeat
                return new net.neoforged.neoforge.client.model.generators.ConfiguredModel[]{
                        new net.neoforged.neoforge.client.model.generators.ConfiguredModel(model, 0, 0, false),
                        new net.neoforged.neoforge.client.model.generators.ConfiguredModel(model, 0, 90, false),
                        new net.neoforged.neoforge.client.model.generators.ConfiguredModel(model, 0, 180, false),
                        new net.neoforged.neoforge.client.model.generators.ConfiguredModel(model, 0, 270, false)};
            }))
            .loot((lt, block) -> lt.add(block, net.minecraft.world.level.storage.loot.LootTable.lootTable().withPool(
                    net.minecraft.world.level.storage.loot.LootPool.lootPool()
                            .add(lt.applyExplosionDecay(block, net.minecraft.world.level.storage.loot.entries.LootItem.lootTableItem(net.minecraft.world.item.Items.BONE)
                                    .apply(com.avicagan.bloodandbones.decoration.BonePileBlock.LAYERS.getPossibleValues(), layers ->
                                            net.minecraft.world.level.storage.loot.functions.SetItemCountFunction.setCount(
                                                            net.minecraft.world.level.storage.loot.providers.number.ConstantValue.exactly(layers * com.avicagan.bloodandbones.decoration.BonePileBlock.BONES_PER_LAYER))
                                                    .when(net.minecraft.world.level.storage.loot.predicates.LootItemBlockStatePropertyCondition.hasBlockStateProperties(block)
                                                            .setProperties(net.minecraft.advancements.critereon.StatePropertiesPredicate.Builder.properties()
                                                                    .hasProperty(com.avicagan.bloodandbones.decoration.BonePileBlock.LAYERS, layers)))))))))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)
            .lang("Bone Pile")
            .item().model((c, p) -> p.generated(c, p.modLoc("item/bone_pile"))).build()
            .register();

    /** Lie on it and have your limbs off, or new ones on. */
    public static final BlockEntry<com.avicagan.bloodandbones.body.SurgeryTableBlock> SURGERY_TABLE = BloodAndBones.REGISTRATE
            .block("surgery_table", com.avicagan.bloodandbones.body.SurgeryTableBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK)
            .properties(p -> p.noOcclusion().strength(3.0F, 6.0F))
            .blockstate((c, p) -> {
                // the table, and over it whichever attachment is fitted
                var builder = p.getMultipartBuilder(c.get());
                builder.part().modelFile(p.models().getExistingFile(p.modLoc("block/surgery_table"))).addModel().end();
                builder.part().modelFile(p.models().getExistingFile(p.modLoc("block/surgery_table_surgical"))).addModel()
                        .condition(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.SURGICAL).end();
                builder.part().modelFile(p.models().getExistingFile(p.modLoc("block/surgery_table_assembly"))).addModel()
                        .condition(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.ASSEMBLY).end();
            })
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Surgery Table")
            .simpleItem()
            .register();

    /** A Fluid Backtank set down, of any tier; its item form is the worn backtank, not this block's own item. */
    public static final BlockEntry<com.avicagan.bloodandbones.backtank.FluidBacktankBlock> FLUID_BACKTANK = BloodAndBones.REGISTRATE
            .block("fluid_backtank", com.avicagan.bloodandbones.backtank.FluidBacktankBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK)
            .properties(p -> p.noOcclusion().strength(2.0F, 6.0F).sound(net.minecraft.world.level.block.SoundType.NETHERITE_BLOCK))
            .blockstate((c, p) -> p.horizontalBlock(c.get(), state -> p.models().getExistingFile(p.modLoc("block/fluid_backtank_"
                    + state.getValue(com.avicagan.bloodandbones.backtank.FluidBacktankBlock.TIER).getSerializedName()))))
            .loot((lt, b) -> lt.add(b, net.minecraft.world.level.storage.loot.LootTable.lootTable()))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Fluid Backtank")
            .item(net.minecraft.world.item.BlockItem::new)
            .removeTab(BBCreativeTabs.MAIN.getKey())
            .model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/fluid_backtank_copper")))
            .build()
            .register();

    /** Where a player with a Port Arm plugs their backtank into the pipes. */
    public static final BlockEntry<com.avicagan.bloodandbones.body.BacktankPortBlock> BACKTANK_PORT = BloodAndBones.REGISTRATE
            .block("backtank_port", com.avicagan.bloodandbones.body.BacktankPortBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.COPPER_BLOCK)
            .properties(p -> p.noOcclusion().strength(2.0F, 6.0F))
            .blockstate((c, p) -> p.directionalBlock(c.get(), p.models().getExistingFile(p.modLoc("block/backtank_port"))))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
            .lang("Backtank Port")
            .simpleItem()
            .register();

    /** Where organic minions drink: four buckets of blood, filled by pipe, Spout or bucket. */
    public static final BlockEntry<com.avicagan.bloodandbones.minion.BloodTroughBlock> BLOOD_TROUGH = BloodAndBones.REGISTRATE
            .block("blood_trough", com.avicagan.bloodandbones.minion.BloodTroughBlock::new)
            .initialProperties(() -> net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS)
            .properties(p -> p.noOcclusion().strength(2.0F, 3.0F))
            .blockstate((c, p) -> p.horizontalBlock(c.get(), p.models().getExistingFile(p.modLoc("block/blood_trough"))))
            .tag(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)
            .lang("Blood Trough")
            .item().model((c, p) -> p.withExistingParent(c.getName(), p.modLoc("block/blood_trough"))).build()
            .register();

    public static void register() {
    }
}
