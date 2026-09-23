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
    private static long moveAt;
    /** Server ticks to let the scene play before the first picture, and between pictures. */
    private static final int SETTLE = 400;
    private static final int SHOT_GAP = 40;

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
                        shot++;
                    }
                } else if (age - moveAt > SHOT_GAP + 20) {
                    BloodAndBones.LOGGER.info("[showcase] done");
                    stage = 3;
                    mc.stop();
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
                new View(o.getX() + 2.5, eye + 1.5, o.getZ() + 15.0, 0, -5));
        BloodAndBones.LOGGER.info("[showcase] built at {}", o);
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
