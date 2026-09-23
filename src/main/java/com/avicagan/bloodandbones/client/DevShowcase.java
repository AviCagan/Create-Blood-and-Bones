package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.cooking.SpecimenJarBlockEntity;
import com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.base.HorizontalAxisKineticBlock;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * Developer aid, off unless {@code -Dbloodandbones.showcase=true} is given to the client run: makes a flat
 * creative world, builds a scene of carcasses, machines and the rest, photographs it from a few places into
 * run/screenshots/showcase_N.png and quits. It lets the look be checked on a machine with no screen.
 */
public final class DevShowcase {
    public static final String PROPERTY = "bloodandbones.showcase";

    private record View(double x, double y, double z, float yaw, float pitch) {
    }

    private static int stage;
    private static int ticks;
    private static int shot;
    private static BlockPos origin;
    private static List<View> views;
    private static long builtAt = -1;
    private static int moved;
    /** Logged at each shot: the wither is the biggest, oddest body in the scene. */
    private static CarcassSavedData.Carcass witherShown;
    private static long moveAt;
    /** Server ticks to let the scene play before the first picture, and between pictures. */
    private static final int SETTLE = 400;
    private static final int SHOT_GAP = 40;
    /** Held-item pictures: tools in hand, then two of a drag. */
    private static final int HANDS = 5;
    private static final int HAND_GAP = 40;
    /** Client ticks per Ponder scene: long enough for its first line of text. */
    private static final int PONDER_GAP = 110;
    private static final List<java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike>> PONDERS = List.of(
            BBBlocks.MANGLER::get, BBBlocks.BLEEDING_RACK::get, BBBlocks.SPIT_ROAST::get);

    private DevShowcase() {
    }

    public static void init() {
        if (Boolean.getBoolean(PROPERTY)) {
            NeoForge.EVENT_BUS.addListener(DevShowcase::tick);
            BloodAndBones.LOGGER.info("[showcase] enabled");
        }
    }

    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        switch (stage) {
            case 0 -> {
                // the title screen, or whatever first-launch screen stands in front of it
                if (mc.level == null && (mc.screen instanceof TitleScreen
                        || mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)) {
                    stage = 1;
                    LevelSettings settings = new LevelSettings("bb_showcase_" + System.currentTimeMillis(), GameType.CREATIVE, false, Difficulty.PEACEFUL,
                            true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, WorldOptions.defaultWithRandomSeed(),
                            access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                            new TitleScreen());
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && ++ticks > 60) {
                    stage = 2;
                    ticks = 0;
                    MinecraftServer server = mc.getSingleplayerServer();
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    origin = player.blockPosition();
                    server.execute(() -> build(player.serverLevel(), player));
                }
            }
            case 2 -> {
                // wait on the server's clock: with software drawing the client can outrun a lagging server
                MinecraftServer server = mc.getSingleplayerServer();
                long age = server == null || builtAt < 0 ? 0 : server.overworld().getGameTime() - builtAt;
                if (age < SETTLE) {
                    return;
                }
                int due = (int) ((age - SETTLE) / SHOT_GAP);
                if (shot < views.size()) {
                    if (due >= shot && moved <= shot) {
                        View view = views.get(shot);
                        moved = shot + 1;
                        moveAt = age;
                        server.execute(() -> {
                            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                            player.teleportTo(player.serverLevel(), view.x(), view.y(), view.z(), view.yaw(), view.pitch());
                        });
                    } else if (moved > shot && age - moveAt >= SHOT_GAP - 5) {
                        Screenshot.grab(mc.gameDirectory, "showcase_" + shot + ".png", mc.getMainRenderTarget(), message -> {
                        });
                        BloodAndBones.LOGGER.info("[showcase] took shot {} at server age {}", shot, age);
                        if (witherShown != null) {
                            MinecraftServer srv = mc.getSingleplayerServer();
                            srv.execute(() -> witherShown.bones.forEach((bone, id) -> {
                                if (dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(srv.overworld()).getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body) {
                                    BloodAndBones.LOGGER.info("[showcase] wither {} at {} (built at {})", bone, body.logicalPose().position(), origin.offset(-17, 0, 6));
                                }
                            }));
                        }
                        shot++;
                    }
                } else if (age - moveAt > SHOT_GAP + 20) {
                    stage = 3;
                    ticks = 0;
                }
            }
            case 3 -> {
                // what the tools look like in hand, then a drag seen first-person and from behind
                int step = ticks / HAND_GAP;
                int phase = ticks % HAND_GAP;
                ticks++;
                MinecraftServer server = mc.getSingleplayerServer();
                if (step >= HANDS + 2) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    server.execute(() -> CarcassDrag.stop(server.overworld(), server.getPlayerList().getPlayers().get(0)));
                    stage = 4;
                    ticks = 0;
                    return;
                }
                if (phase == 0) {
                    int s = step;
                    server.execute(() -> hands(server.overworld(), server.getPlayerList().getPlayers().get(0), s));
                    if (step == HANDS + 1) {
                        mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    }
                } else if (phase == HAND_GAP - 1) {
                    Screenshot.grab(mc.gameDirectory, "showcase_hand_" + step + ".png", mc.getMainRenderTarget(), message -> {
                    });
                    BloodAndBones.LOGGER.info("[showcase] took hand shot {}", step);
                }
            }
            case 4 -> {
                // open each Ponder scene and photograph it once its first text is up
                int scene = ticks / PONDER_GAP;
                int phase = ticks % PONDER_GAP;
                ticks++;
                if (scene >= PONDERS.size()) {
                    if (phase == 0 && com.avicagan.bloodandbones.compat.jei.BBJeiPlugin.runtime != null) {
                        mc.setScreen(null);
                        // the cow's page: what a cow's carcass gives
                        var jei = com.avicagan.bloodandbones.compat.jei.BBJeiPlugin.runtime;
                        jei.getRecipesGui().show(jei.getJeiHelpers().getFocusFactory().createFocus(
                                mezz.jei.api.recipe.RecipeIngredientRole.INPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                                new ItemStack(net.minecraft.world.item.Items.COW_SPAWN_EGG)));
                    } else if (phase == PONDER_GAP - 1 || com.avicagan.bloodandbones.compat.jei.BBJeiPlugin.runtime == null) {
                        Screenshot.grab(mc.gameDirectory, "showcase_jei.png", mc.getMainRenderTarget(), message -> {
                        });
                        BloodAndBones.LOGGER.info("[showcase] done");
                        stage = 5;
                        mc.stop();
                    }
                } else if (phase == 0) {
                    net.createmod.catnip.gui.ScreenOpener.open(net.createmod.ponder.foundation.ui.PonderUI.of(new ItemStack(PONDERS.get(scene).get())));
                } else if (phase == PONDER_GAP - 1) {
                    Screenshot.grab(mc.gameDirectory, "showcase_ponder_" + scene + ".png", mc.getMainRenderTarget(), message -> {
                    });
                    BloodAndBones.LOGGER.info("[showcase] took ponder shot {}", scene);
                }
            }
            default -> {
            }
        }
    }

    private static void build(ServerLevel level, ServerPlayer player) {
        builtAt = level.getGameTime();
        level.setDayTime(6000);
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        BlockPos o = origin;
        int ground = o.getY() - 1;

        // row A: carcasses on the ground
        EntityType<?>[] mobs = {EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.HORSE, EntityType.WOLF, EntityType.SPIDER,
                EntityType.VILLAGER, EntityType.LLAMA};
        for (int i = 0; i < mobs.length; i++) {
            carcass(level, mobs[i], o.offset((int) Math.round(-9 + i * 2.6), 0, 5));
        }

        // off to the side: the biggest and the smallest odd ones
        witherShown = carcass(level, EntityType.WITHER, o.offset(-17, 0, 6));
        carcass(level, EntityType.PUFFERFISH, o.offset(-20, 0, 4));

        // row B: the machines set flush in the floor, a carcass on each
        BlockEntry<?>[] machines = {BBBlocks.MANGLER, BBBlocks.GUILLOTINE, BBBlocks.BEHEADER, BBBlocks.DEGLOVER};
        EntityType<?>[] onThem = {EntityType.COW, EntityType.PIG, EntityType.ZOMBIE, EntityType.SHEEP};
        for (int i = 0; i < machines.length; i++) {
            BlockPos at = new BlockPos(o.getX() - 6 + i * 4, ground, o.getZ() + 12);
            level.setBlockAndUpdate(at.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
            level.setBlockAndUpdate(at, machines[i].getDefaultState());
            if (level.getBlockEntity(at.below()) instanceof CreativeMotorBlockEntity motor) {
                motor.generatedSpeed.setValue(64);
            }
            carcass(level, onThem[i], at.above(), false);
        }

        // row C: spit roast, specimen jar, bleeding rack under a hanging carcass, blood pools
        int z = o.getZ() + 19;
        BlockPos fire = new BlockPos(o.getX() - 7, o.getY(), z);
        level.setBlockAndUpdate(fire, Blocks.CAMPFIRE.defaultBlockState());
        level.setBlockAndUpdate(fire.above(), BBBlocks.SPIT_ROAST.getDefaultState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Direction.Axis.X));
        level.setBlockAndUpdate(fire.above().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        CarcassSavedData.Carcass roastCow = carcass(level, EntityType.COW, new BlockPos(o.getX() - 12, o.getY(), z + 6));
        if (roastCow != null && level.getBlockEntity(fire.above()) instanceof SpitRoastBlockEntity spit) {
            spit.skewer(CarcassPieceItem.of(roastCow, "right_hind_leg"));
            spit.progress = spit.cookTime() * 0.8F;
        }
        BlockPos jar = new BlockPos(o.getX() - 3, o.getY(), z);
        level.setBlockAndUpdate(jar, BBBlocks.SPECIMEN_JAR.getDefaultState());
        CarcassSavedData.Carcass jarPig = carcass(level, EntityType.PIG, new BlockPos(o.getX() - 12, o.getY(), z + 9));
        if (jarPig != null && level.getBlockEntity(jar) instanceof SpecimenJarBlockEntity specimen) {
            specimen.put(CarcassPieceItem.of(jarPig, "head"));
        }
        BlockPos rack = new BlockPos(o.getX() + 2, o.getY(), z);
        level.setBlockAndUpdate(rack, BBBlocks.BLEEDING_RACK.getDefaultState());
        level.setBlockAndUpdate(rack.above(5), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(rack.above(4), BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        CarcassSavedData.Carcass hung = carcass(level, EntityType.COW, rack.above());
        if (hung != null) {
            level.getServer().execute(() -> hang(level, player, hung, rack.above(4)));
        }
        // a second hook with nothing under it: the pig bleeds onto the grass
        BlockPos bare = new BlockPos(o.getX() + 11, o.getY(), z);
        level.setBlockAndUpdate(bare.above(5), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(bare.above(4), BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        CarcassSavedData.Carcass dripping = carcass(level, EntityType.PIG, bare.above());
        if (dripping != null) {
            level.getServer().execute(() -> hang(level, player, dripping, bare.above(4)));
        }
        for (int dx = 0; dx < 2; dx++) {
            level.setBlockAndUpdate(new BlockPos(o.getX() + 6 + dx, ground, z), BBFluids.blood().defaultFluidState().createLegacyBlock());
            level.setBlockAndUpdate(new BlockPos(o.getX() + 6 + dx, ground, z + 1), BBFluids.soulBlood().defaultFluidState().createLegacyBlock());
        }

        double eye = o.getY();
        views = List.of(
                // carcasses, from behind the row
                new View(o.getX() + 0.5, eye, o.getZ() - 1.5, 0, 25),
                // machines, from above
                new View(o.getX() + 0.5, eye + 4, o.getZ() + 7.5, 0, 45),
                // mangler and guillotine close up
                new View(o.getX() - 3.5, eye + 1.5, o.getZ() + 9.5, 0, 35),
                // beheader and deglover close up
                new View(o.getX() + 4.5, eye + 1.5, o.getZ() + 9.5, 0, 35),
                // spit roast from the side
                new View(o.getX() - 6.5, eye + 1.2, o.getZ() + 17.0, 0, 25),
                // jar close
                new View(o.getX() - 2.5, eye + 0.5, o.getZ() + 17.5, 0, 20),
                // rack and the hanging cow
                new View(o.getX() + 2.5, eye + 1.5, o.getZ() + 15.0, 0, -5),
                // the pig bleeding onto the ground
                new View(o.getX() + 11.5, eye + 1.5, o.getZ() + 13.5, 0, 12),
                // the wither and the pufferfish
                new View(o.getX() - 18.0, eye + 1.0, o.getZ() + 0.5, 0, 25));
        BloodAndBones.LOGGER.info("[showcase] built at {}", o);
    }

    /** Step 0..HANDS-1: hold a tool facing a carcass; then hook a leg and drag it. */
    private static void hands(ServerLevel level, ServerPlayer player, int step) {
        BlockPos o = origin;
        player.teleportTo(level, o.getX() + 0.5, o.getY(), o.getZ() + 3.2, 0, 35);
        if (step < HANDS) {
            ItemStack stack = switch (step) {
                case 0 -> new ItemStack(BBItems.CLEAVER.get());
                case 1 -> new ItemStack(BBItems.FLENSING_KNIFE.get());
                case 2 -> new ItemStack(BBItems.BLOOD_STEEL_CLEAVER.get());
                case 3 -> carriedPiece(level);
                default -> new ItemStack(BBFluids.BLOOD.getBucket().get());
            };
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            return;
        }
        if (step == HANDS) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
            // hook the nearest carcass leg in the row in front and walk back with it
            var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
            CarcassSavedData.Carcass best = null;
            double bestDistance = Double.MAX_VALUE;
            for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
                java.util.UUID torso = carcass.bones.get(carcass.rootBone);
                if (container == null || torso == null || !(container.getSubLevel(torso) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body)) {
                    continue;
                }
                double d = body.logicalPose().position().distance(player.getX(), player.getY(), player.getZ());
                if (d < bestDistance && carcass.bones.size() > 3) {
                    bestDistance = d;
                    best = carcass;
                }
            }
            if (best != null) {
                if (best.resting) {
                    com.avicagan.bloodandbones.carcass.CarcassRest.split(level, best);
                }
                for (java.util.UUID id : best.bones.values()) {
                    if (container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel limb && !id.equals(best.bones.get(best.rootBone))) {
                        CarcassDrag.start(level, player, limb.getPlot().getCenterBlock(), null);
                        break;
                    }
                }
            }
            player.teleportTo(level, o.getX() + 0.5, o.getY(), o.getZ() + 1.0, 180, 30);
        }
    }

    private static ItemStack carriedPiece(ServerLevel level) {
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            if (carcass.entity.getPath().equals("pig") && carcass.bones.containsKey("head")) {
                return CarcassPieceItem.of(carcass, "head");
            }
        }
        return new ItemStack(BBItems.CARCASS_PIECE.get());
    }

    private static CarcassSavedData.Carcass carcass(ServerLevel level, EntityType<?> type, BlockPos at) {
        return carcass(level, type, at, true);
    }

    private static CarcassSavedData.Carcass carcass(ServerLevel level, EntityType<?> type, BlockPos at, boolean shove) {
        if (!(type.create(level) instanceof Mob mob)) {
            return null;
        }
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        mob.setYHeadRot(0);
        mob.yBodyRot = 0;
        if (mob instanceof Sheep sheep) {
            sheep.setColor(DyeColor.WHITE);
        }
        level.addFreshEntity(mob);
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass != null && shove) {
            // as a kill would: knock it over
            double angle = level.random.nextDouble() * Math.PI * 2;
            CarcassAssembler.shove(level, carcass, new net.minecraft.world.phys.Vec3(Math.cos(angle), 0, Math.sin(angle)));
        }
        return carcass;
    }

    private static void hang(ServerLevel level, ServerPlayer player, CarcassSavedData.Carcass carcass, BlockPos hook) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        java.util.UUID legId = carcass.bones.get("right_hind_leg");
        if (container == null || legId == null || !(container.getSubLevel(legId) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel leg)) {
            return;
        }
        player.teleportTo(hook.getX() + 0.5, hook.getY() - 3, hook.getZ() - 1.5);
        if (CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null)
                && level.getBlockEntity(hook) instanceof ShackleHookBlockEntity shackle) {
            shackle.toggle(level, player);
        }
    }
}
