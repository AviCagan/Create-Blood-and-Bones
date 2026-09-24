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
 * run/screenshots/showcase_N.png and quits. It lets the look be checked on a machine with no screen. With
 * {@code =bloodless} it does the same with the bloodless game rule on.
 */
public final class DevShowcase {
    public static final String PROPERTY = "bloodandbones.showcase";
    /** {@code -Dbloodandbones.showcase=bloodless}: the same run with the bloodless game rule on, into showcase_bloodless_*. */
    private static final boolean BLOODLESS = "bloodless".equals(System.getProperty(PROPERTY));
    private static final String PREFIX = BLOODLESS ? "showcase_bloodless_" : "showcase_";

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
            BBBlocks.MANGLER::get, BBBlocks.BLEEDING_RACK::get, BBBlocks.SPIT_ROAST::get, BBBlocks.BUTCHER_HOOK::get, BBBlocks.BUTCHER_TABLE::get);

    private DevShowcase() {
    }

    public static void init() {
        if (Boolean.getBoolean(PROPERTY) || BLOODLESS) {
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
                    // no structures: a village landing on the scene buried the camera in a house once
                    mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, WorldOptions.defaultWithRandomSeed().withStructures(false),
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
                        Screenshot.grab(mc.gameDirectory, PREFIX + shot + ".png", mc.getMainRenderTarget(), message -> {
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
                    stage = 6;
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
                    Screenshot.grab(mc.gameDirectory, PREFIX + "hand_" + step + ".png", mc.getMainRenderTarget(), message -> {
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
                        // and every item of this mod in the list beside it, to see their icons
                        jei.getIngredientFilter().setFilterText("@bloodandbones");
                        jei.getRecipesGui().show(jei.getJeiHelpers().getFocusFactory().createFocus(
                                mezz.jei.api.recipe.RecipeIngredientRole.INPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                                new ItemStack(net.minecraft.world.item.Items.COW_SPAWN_EGG)));
                    } else if (phase == PONDER_GAP - 1 || com.avicagan.bloodandbones.compat.jei.BBJeiPlugin.runtime == null) {
                        Screenshot.grab(mc.gameDirectory, PREFIX + "jei.png", mc.getMainRenderTarget(), message -> {
                        });
                        // names as the player reads them: reworded in bloodless mode
                        BloodAndBones.LOGGER.info("[showcase] names: {} | {} | {} | {} | {}",
                                BBItems.BLOOD_STEEL_INGOT.asStack().getHoverName().getString(),
                                BBBlocks.BLEEDING_RACK.asStack().getHoverName().getString(),
                                BBBlocks.BLOODY_CASING.asStack().getHoverName().getString(),
                                com.avicagan.bloodandbones.registry.BBFluids.blood().getFluidType().getDescription().getString(),
                                net.minecraft.client.resources.language.I18n.get("block.bloodandbones.bleeding_rack.tooltip.summary"));
                        // what Create's Attribute Filter offers for a pig's head, as the player reads it
                        ItemStack head = new ItemStack(BBItems.CARCASS_PIECE.get());
                        head.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(
                                net.minecraft.resources.ResourceLocation.withDefaultNamespace("pig"), "head",
                                net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png"), List.of(), 1.0F,
                                false, java.util.Map.of(), 0.0F, 0.0F, 0.0F, false));
                        BloodAndBones.LOGGER.info("[showcase] filter: {}", com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute
                                .getAllAttributes(head, mc.level).stream()
                                .filter(a -> a.getTranslationKey().startsWith(BloodAndBones.MOD_ID))
                                .map(a -> a.format(false).getString()).toList());
                        BloodAndBones.LOGGER.info("[showcase] tooltip: {}", head.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player,
                                net.minecraft.world.item.TooltipFlag.Default.NORMAL).stream().map(net.minecraft.network.chat.Component::getString).toList());
                        BloodAndBones.LOGGER.info("[showcase] done");
                        stage = 5;
                        mc.stop();
                    }
                } else if (phase == 0) {
                    net.createmod.catnip.gui.ScreenOpener.open(net.createmod.ponder.foundation.ui.PonderUI.of(new ItemStack(PONDERS.get(scene).get())));
                } else if (phase == 20 && PONDERS.get(scene).get() == BBBlocks.BUTCHER_HOOK.get()
                        && mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ponder) {
                    // past the point where every hook has its piece
                    ponder.seekToTime(300);
                } else if (phase == 20 && PONDERS.get(scene).get() == BBBlocks.BUTCHER_TABLE.get()
                        && mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ponder) {
                    // the Deployer over the table, a piece on it
                    ponder.seekToTime(250);
                } else if (phase == PONDER_GAP - 1) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "ponder_" + scene + ".png", mc.getMainRenderTarget(), message -> {
                    });
                    BloodAndBones.LOGGER.info("[showcase] took ponder shot {}", scene);
                }
            }
            case 6 -> {
                // the body: a peg leg, a hook hand and an arm gone, from the front and first-person; then the surgery screen
                MinecraftServer server = mc.getSingleplayerServer();
                int t = ticks++;
                // the body turns after the head only slowly: face it the way the shot wants
                if (mc.player != null && t > 0 && t <= 60) {
                    float yaw = t <= 40 ? 180.0F : 0.0F;
                    mc.player.setYRot(yaw);
                    mc.player.yRotO = yaw;
                    mc.player.setYBodyRot(yaw);
                    mc.player.yBodyRotO = yaw;
                    mc.player.setYHeadRot(yaw);
                    mc.player.yHeadRotO = yaw;
                }
                if (t == 0) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    mc.options.hideGui = true;
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        var body = com.avicagan.bloodandbones.body.BodyEffects.body(player);
                        // a cybernetic body on soul blood: hydraulic and vent arms, a piston leg, a sinew leg left dry (it runs on
                        // blood), a red lens for one eye and an empty socket for the other
                        body.fit(com.avicagan.bloodandbones.body.BodyPart.LEFT_LEG, new ItemStack(BBItems.PISTON_LEG.get()));
                        body.fit(com.avicagan.bloodandbones.body.BodyPart.RIGHT_LEG, new ItemStack(BBItems.SINEW_LEG.get()));
                        ItemStack brass = new ItemStack(BBItems.HYDRAULIC_ARM.get());
                        com.avicagan.bloodandbones.cyber.Modules.set(brass, java.util.List.of(com.avicagan.bloodandbones.cyber.Module.PISTON_RAM,
                                com.avicagan.bloodandbones.cyber.Module.ROTATIONAL_COUPLER));
                        body.fit(com.avicagan.bloodandbones.body.BodyPart.RIGHT_ARM, brass);
                        body.fit(com.avicagan.bloodandbones.body.BodyPart.LEFT_ARM, new ItemStack(BBItems.VENT_ARM.get()));
                        body.fit(com.avicagan.bloodandbones.body.BodyPart.RIGHT_EYE, new ItemStack(BBItems.OPTIC_EYE.get()));
                        body.lose(com.avicagan.bloodandbones.body.BodyPart.LEFT_EYE);
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        ItemStack tank = new ItemStack(BBItems.backtank(com.avicagan.bloodandbones.backtank.BacktankTier.SOUL_NETHERITE));
                        com.avicagan.bloodandbones.backtank.FluidBacktankItem.setFluid(tank, new net.neoforged.neoforge.fluids.FluidStack(
                                com.avicagan.bloodandbones.registry.BBFluids.soulBlood(), 32000));
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, tank);
                        player.serverLevel().setBlockAndUpdate(player.blockPosition().offset(4, 0, 5), BBBlocks.BACKTANK_PORT.getDefaultState());
                        player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), 180.0F, -25.0F);
                        // every tier of backtank set down in a row behind
                        for (var tier : com.avicagan.bloodandbones.backtank.BacktankTier.values()) {
                            BlockPos at = player.blockPosition().offset(tier.ordinal() - 3, 0, 5);
                            player.serverLevel().setBlockAndUpdate(at, BBBlocks.FLUID_BACKTANK.getDefaultState()
                                    .setValue(com.avicagan.bloodandbones.backtank.FluidBacktankBlock.TIER, tier));
                        }
                    });
                } else if (t == 20) {
                    // the throttle spooling the brass arm, so it glows in the front shot
                    server.execute(() -> com.avicagan.bloodandbones.cyber.Throttle.press(server.getPlayerList().getPlayers().get(0),
                            com.avicagan.bloodandbones.body.BodyPart.RIGHT_ARM, 0));
                } else if (t == 40) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "body_0.png", mc.getMainRenderTarget(), message -> {
                    });
                    server.execute(() -> com.avicagan.bloodandbones.cyber.Throttle.release(server.getPlayerList().getPlayers().get(0), false));
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), 0.0F, 25.0F);
                    });
                } else if (t == 60) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "body_3.png", mc.getMainRenderTarget(), message -> {
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    mc.options.hideGui = false;
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        // a shaft end just ahead at eye level, for the Rotational Coupler to reach into
                        // east, so the camera behind (west) is clear of the minions (south)
                        for (int d = 2; d <= 4; d++) {
                            player.serverLevel().setBlockAndUpdate(player.blockPosition().east(d).above(), com.simibubi.create.AllBlocks.SHAFT.getDefaultState()
                                    .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, net.minecraft.core.Direction.Axis.X));
                        }
                        player.teleportTo(player.serverLevel(), player.getBlockX() + 0.5, player.getY(), player.getBlockZ() + 0.5, -90.0F, 5.0F);
                    });
                } else if (t == 65) {
                    server.execute(() -> com.avicagan.bloodandbones.cyber.Throttle.press(server.getPlayerList().getPlayers().get(0),
                            com.avicagan.bloodandbones.body.BodyPart.RIGHT_ARM, 1));
                } else if (t == 90) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "body_1.png", mc.getMainRenderTarget(), message -> {
                    });
                    // and from behind: the coupler's rod from the arm into the shaft
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    mc.options.hideGui = true;
                } else if (t == 100) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "body_4.png", mc.getMainRenderTarget(), message -> {
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    mc.options.hideGui = false;
                    BlockPos feet = mc.player.blockPosition();
                    BloodAndBones.LOGGER.info("[showcase] throttle: {} at {}; ahead {} | {}",
                            com.avicagan.bloodandbones.cyber.Throttle.spooling(server.getPlayerList().getPlayers().get(0)),
                            com.avicagan.bloodandbones.cyber.Throttle.level(server.getPlayerList().getPlayers().get(0)),
                            mc.level.getBlockState(feet.east(1).above()), mc.level.getBlockState(feet.east(2).above()));
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        com.avicagan.bloodandbones.cyber.Throttle.release(player, false);
                        for (int d = 2; d <= 4; d++) {
                            player.serverLevel().removeBlock(player.blockPosition().east(d).above(), false);
                        }
                        BlockPos at = player.blockPosition().east(2);
                        player.serverLevel().setBlockAndUpdate(at, BBBlocks.SURGERY_TABLE.getDefaultState()
                                .setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.SURGICAL));
                        if (player.serverLevel().getBlockEntity(at) instanceof com.avicagan.bloodandbones.body.SurgeryTableBlockEntity table) {
                            table.put(new ItemStack(BBItems.CLEAVER.get()));
                        }
                        if (com.avicagan.bloodandbones.body.SurgeryTableBlock.lieDown(player.serverLevel(), at, player)) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.avicagan.bloodandbones.body.Surgery.OpenPayload(at, player.getId()));
                        }
                    });
                } else if (t == 140) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "body_2.png", mc.getMainRenderTarget(), message -> {
                    });
                    BloodAndBones.LOGGER.info("[showcase] took body shots; screen {}", mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
                    mc.setScreen(null);
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.stopRiding();
                        player.setData(com.avicagan.bloodandbones.body.BBAttachments.BODY, new com.avicagan.bloodandbones.body.Body());
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                        // carcass armour: a cow's helmet and boots, a rabbit's leggings; scraps and the pieces in the hotbar
                        var cow = net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow");
                        var rabbit = net.minecraft.resources.ResourceLocation.withDefaultNamespace("rabbit");
                        var store = com.avicagan.bloodandbones.parts.PartsData.SERVER;
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, com.avicagan.bloodandbones.parts.CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_HELMET.get()),
                                new com.avicagan.bloodandbones.parts.CarcassArmour("helmet", cow, false, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), 0), store));
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, com.avicagan.bloodandbones.parts.CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_LEGGINGS.get()),
                                new com.avicagan.bloodandbones.parts.CarcassArmour("leggings", rabbit, false, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), 0), store));
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, com.avicagan.bloodandbones.parts.CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_BOOTS.get()),
                                new com.avicagan.bloodandbones.parts.CarcassArmour("boots", cow, false, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty(), 0), store));
                        String[] parts = {"head", "torso", "arm", "leg", "tail"};
                        for (int i = 0; i < parts.length; i++) {
                            player.getInventory().setItem(i, com.avicagan.bloodandbones.parts.ScrapsItem.of(new com.avicagan.bloodandbones.parts.Source(i % 2 == 0 ? cow : rabbit, parts[i], false), 3 + i));
                        }
                        player.getInventory().setItem(5, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy());
                        player.getInventory().setItem(6, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).copy());
                        player.getInventory().setItem(7, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).copy());
                        player.getInventory().selected = 8;
                        player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), 180.0F, 10.0F);
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    mc.options.hideGui = true;
                } else if (t == 165) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "armour_0.png", mc.getMainRenderTarget(), message -> {
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    mc.options.hideGui = false;
                } else if (t == 175) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "armour_1.png", mc.getMainRenderTarget(), message -> {
                    });
                    BloodAndBones.LOGGER.info("[showcase] armour: {} | {} | {}", mc.player.getInventory().getItem(0).getHoverName().getString(),
                            mc.player.getInventory().getItem(6).getHoverName().getString(), mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH));
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.LEGS,
                                net.minecraft.world.entity.EquipmentSlot.FEET}) {
                            player.setItemSlot(slot, ItemStack.EMPTY);
                        }
                        player.getInventory().clearContent();
                        // minions, somewhere clear: four stitched ones in a row, a trough, and one being built on the table
                        player.teleportTo(player.serverLevel(), player.getBlockX() + 0.5, player.getY(), player.getBlockZ() - 40.5, 0.0F, 20.0F);
                        // four stitched minions beside: a cow on rabbit legs, a whole cow, a zombie with a pig's head on a
                        // rabbit's haunches, and a legless cow out of blood on its side
                        java.util.function.BiFunction<String, String, com.avicagan.bloodandbones.minion.PieceRef> ref = (mob, bone) ->
                                new com.avicagan.bloodandbones.minion.PieceRef(net.minecraft.resources.ResourceLocation.withDefaultNamespace(mob), bone,
                                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/" + mob + "/" + (mob.equals("rabbit") ? "brown" : mob) + ".png"),
                                        java.util.List.of(), 1.0F, false, java.util.Map.of(), false);
                        var cow = com.avicagan.bloodandbones.minion.MinionBuild.of(ref.apply("cow", "body")).with("head", ref.apply("cow", "head"));
                        var hopper = cow.with("right_front_leg", ref.apply("rabbit", "right_front_leg")).with("left_front_leg", ref.apply("rabbit", "left_front_leg"))
                                .with("right_hind_leg", ref.apply("rabbit", "right_haunch")).with("left_hind_leg", ref.apply("rabbit", "left_haunch"));
                        var whole = cow;
                        for (String leg : new String[]{"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"}) {
                            whole = whole.with(leg, ref.apply("cow", leg));
                        }
                        var odd = com.avicagan.bloodandbones.minion.MinionBuild.of(ref.apply("zombie", "body")).with("head", ref.apply("pig", "head"))
                                .with("right_leg", ref.apply("rabbit", "right_haunch")).with("left_leg", ref.apply("rabbit", "left_haunch"))
                                .with("left_arm", ref.apply("zombie", "left_arm"));
                        var builds = java.util.List.of(hopper, whole, odd, cow);
                        for (int m = 0; m < builds.size(); m++) {
                            var minion = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(player.serverLevel());
                            BlockPos at = player.blockPosition().offset(m * 3 - 4, 0, 7);
                            minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180.0F, 0.0F);
                            minion.setup(player, at, builds.get(m), 1000.0F);
                            minion.setNoAi(true);
                            player.serverLevel().addFreshEntity(minion);
                            if (m == 3) {
                                minion.powerDown();
                            }
                        }
                        BlockPos troughAt = player.blockPosition().offset(7, 0, 7);
                        player.serverLevel().setBlockAndUpdate(troughAt, BBBlocks.BLOOD_TROUGH.getDefaultState());
                        if (player.serverLevel().getBlockEntity(troughAt) instanceof com.avicagan.bloodandbones.minion.BloodTroughBlockEntity trough) {
                            trough.tank().fill(new net.neoforged.neoforge.fluids.FluidStack(com.avicagan.bloodandbones.registry.BBFluids.blood(), 2500),
                                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                        }
                        BlockPos tableAt = player.blockPosition().offset(0, 0, 3);
                        player.serverLevel().setBlockAndUpdate(tableAt, BBBlocks.SURGERY_TABLE.getDefaultState()
                                .setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.ASSEMBLY));
                        if (player.serverLevel().getBlockEntity(tableAt) instanceof com.avicagan.bloodandbones.body.SurgeryTableBlockEntity table) {
                            table.setBuild(cow.with("right_front_leg", ref.apply("rabbit", "right_front_leg")).with("right_hind_leg", ref.apply("rabbit", "right_haunch")));
                        }
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    mc.options.hideGui = true;
                } else if (t == 200) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "minions_0.png", mc.getMainRenderTarget(), message -> {
                    });
                    // and from behind and to the side: legs and stumps
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.teleportTo(player.serverLevel(), player.getX() - 6.0, player.getY(), player.getZ() + 12.0, -135.0F, 12.0F);
                    });
                } else if (t == 220) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "minions_1.png", mc.getMainRenderTarget(), message -> {
                    });
                    mc.options.hideGui = false;
                    stage = 4;
                    ticks = 0;
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
        level.getGameRules().getRule(com.avicagan.bloodandbones.registry.BBGameRules.BLOODLESS).set(BLOODLESS, level.getServer());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        BlockPos o = origin;
        int ground = o.getY() - 1;

        // row A: carcasses on the ground
        EntityType<?>[] mobs = {EntityType.COW, EntityType.SHEEP, EntityType.PIG, EntityType.HORSE, EntityType.WOLF, EntityType.SPIDER,
                EntityType.VILLAGER, EntityType.LLAMA};
        for (int i = 0; i < mobs.length; i++) {
            carcass(level, mobs[i], o.offset((int) Math.round(-9 + i * 2.6), 0, 5));
        }

        // babies in front of the row: a calf, a piglet and a baby villager
        carcass(level, EntityType.COW, o.offset(-4, 0, 2), true, true);
        carcass(level, EntityType.PIG, o.offset(-1, 0, 2), true, true);
        carcass(level, EntityType.VILLAGER, o.offset(3, 0, 2), true, true);
        // and a foal on its long legs, beside a grown horse
        carcass(level, EntityType.HORSE, o.offset(7, 0, 2), false, true);
        carcass(level, EntityType.HORSE, o.offset(10, 0, 3), false, false);
        carcass(level, EntityType.LLAMA, o.offset(5, 0, 3), false, true);

        // off to the side: the biggest and the smallest odd ones
        witherShown = carcass(level, EntityType.WITHER, o.offset(-17, 0, 6));
        carcass(level, EntityType.PUFFERFISH, o.offset(-20, 0, 4));
        // the smallest slime and magma cube, a quarter the size of a big one
        for (EntityType<? extends net.minecraft.world.entity.monster.Slime> type : java.util.List.of(EntityType.SLIME, EntityType.MAGMA_CUBE)) {
            if (type.create(level) instanceof net.minecraft.world.entity.monster.Slime slime) {
                slime.setSize(1, true);
                slime.moveTo(o.getX() - (type == EntityType.SLIME ? 17.5 : 15.5), o.getY(), o.getZ() + 3.0, 0, 0);
                level.addFreshEntity(slime);
                CarcassAssembler.assemble(slime, null);
                slime.discard();
            }
        }
        // and one well gone off, with flies
        CarcassSavedData.Carcass rotting = carcass(level, EntityType.COW, o.offset(-15, 0, 4));
        if (rotting != null) {
            rotting.freshness = 0.12F;
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
            if (i == 0 && level.getBlockEntity(at) instanceof com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity machine) {
                // the filter slot, set for what lies on it
                machine.filtering.setFilter(new ItemStack(net.minecraft.world.item.Items.COW_SPAWN_EGG));
            }
        }

        // a spare Beheader with nothing on it, its filter slot set for zombies, in plain view
        BlockPos spare = new BlockPos(o.getX() + 10, ground, o.getZ() + 12);
        level.setBlockAndUpdate(spare, BBBlocks.BEHEADER.getDefaultState());
        if (level.getBlockEntity(spare) instanceof com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity machine) {
            machine.filtering.setFilter(new ItemStack(net.minecraft.world.item.Items.ZOMBIE_SPAWN_EGG));
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
        // and a hoglin beside it: a nether mob bleeds Soul Blood, dark teal
        BlockPos soulHook = bare.east(3);
        level.setBlockAndUpdate(soulHook.above(5), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(soulHook.above(4), BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        CarcassSavedData.Carcass soulDripping = carcass(level, EntityType.HOGLIN, soulHook.above());
        if (soulDripping != null) {
            level.getServer().execute(() -> hang(level, player, soulDripping, soulHook.above(4)));
        }
        for (int dx = 0; dx < 2; dx++) {
            level.setBlockAndUpdate(new BlockPos(o.getX() + 6 + dx, ground, z), BBFluids.blood().defaultFluidState().createLegacyBlock());
            level.setBlockAndUpdate(new BlockPos(o.getX() + 6 + dx, ground, z + 1), BBFluids.soulBlood().defaultFluidState().createLegacyBlock());
        }

        // row D: a wall of Bloody Casing (joined up) beside plain andesite casing, a Butcher's Hook with a
        // piece on each
        int wallZ = o.getZ() + 27;
        for (int dx = -4; dx <= 1; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                level.setBlockAndUpdate(new BlockPos(o.getX() + dx, o.getY() + dy, wallZ),
                        dx <= -2 ? BBBlocks.BLOODY_CASING.getDefaultState() : AllBlocks.ANDESITE_CASING.getDefaultState());
            }
        }
        // gut chains: a strand standing between the hooks, and one draped along the top of the bloody casing
        for (int dy = 0; dy < 2; dy++) {
            level.setBlockAndUpdate(new BlockPos(o.getX() - 1, o.getY() + dy, wallZ - 1), BBBlocks.GUT_CHAIN.getDefaultState());
        }
        for (int dx = -4; dx <= -2; dx++) {
            level.setBlockAndUpdate(new BlockPos(o.getX() + dx, o.getY() + 2, wallZ), BBBlocks.GUT_CHAIN.getDefaultState()
                    .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
        // a butcher's table in front, a cow's leg laid on it
        BlockPos tablePos = new BlockPos(o.getX(), o.getY(), wallZ - 2);
        level.setBlockAndUpdate(tablePos, BBBlocks.BUTCHER_TABLE.getDefaultState());
        if (roastCow != null && level.getBlockEntity(tablePos) instanceof com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity table) {
            table.put(CarcassPieceItem.of(roastCow, "left_hind_leg"));
        }
        BlockPos[] hooks = {new BlockPos(o.getX() - 3, o.getY() + 1, wallZ - 1), new BlockPos(o.getX() + 1, o.getY() + 1, wallZ - 1)};
        CarcassSavedData.Carcass[] hookMeat = {jarPig, roastCow};
        String[] hookBones = {"right_front_leg", "head"};
        for (int i = 0; i < hooks.length; i++) {
            level.setBlockAndUpdate(hooks[i], BBBlocks.BUTCHER_HOOK.getDefaultState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
            if (hookMeat[i] != null && level.getBlockEntity(hooks[i]) instanceof com.avicagan.bloodandbones.cooking.ButcherHookBlockEntity hook) {
                hook.put(CarcassPieceItem.of(hookMeat[i], hookBones[i]));
            }
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
                new View(o.getX() - 18.0, eye + 1.0, o.getZ() + 0.5, 0, 25),
                // a spare Beheader's filter slot, set for zombies
                new View(o.getX() + 10.5, eye + 1.6, o.getZ() + 10.3, 0, 55),
                // the foal beside a grown horse, and a baby llama
                new View(o.getX() + 8.5, eye + 1.2, o.getZ() - 2.0, 0, 18),
                // bloody casing and butcher's hooks
                new View(o.getX() - 1.0, eye + 1.0, wallZ - 4.0, 0, 8));
        BloodAndBones.LOGGER.info("[showcase] built at {}", o);
    }

    /** Step 0..HANDS-1: hold a tool facing a carcass; then hook a leg and drag it. */
    private static void hands(ServerLevel level, ServerPlayer player, int step) {
        BlockPos o = origin;
        player.teleportTo(level, o.getX() + 0.5, o.getY(), o.getZ() + 3.2, 0, 35);
        if (step < HANDS) {
            ItemStack stack = switch (step) {
                case 0 -> {
                    // just used: bloody
                    ItemStack cleaver = new ItemStack(BBItems.CLEAVER.get());
                    com.avicagan.bloodandbones.carcass.Blood.bloody(cleaver, level);
                    yield cleaver;
                }
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
                // a still carcass lists only its torso; count the limbs folded into it too
                if (d < bestDistance && com.avicagan.bloodandbones.carcass.CarcassRot.pieces(carcass).size() > 3
                        && com.avicagan.bloodandbones.carcass.Blood.bloody(carcass)) {
                    bestDistance = d;
                    best = carcass;
                }
            }
            if (best != null && container.getSubLevel(best.bones.get(best.rootBone)) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel torso) {
                // stand beside it, looking at it, then hook a leg
                org.joml.Vector3dc at = torso.logicalPose().position();
                player.teleportTo(level, at.x() + 1.8, at.y(), at.z(), 90, 35);
                if (best.resting) {
                    com.avicagan.bloodandbones.carcass.CarcassRest.split(level, best);
                }
                for (java.util.UUID id : best.bones.values()) {
                    if (container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel limb && !id.equals(best.bones.get(best.rootBone))) {
                        boolean started = CarcassDrag.start(level, player, limb.getPlot().getCenterBlock(), null);
                        BloodAndBones.LOGGER.info("[showcase] drag of {} started: {}", best.entity, started);
                        break;
                    }
                }
            }
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
        return carcass(level, type, at, shove, false);
    }

    private static CarcassSavedData.Carcass carcass(ServerLevel level, EntityType<?> type, BlockPos at, boolean shove, boolean baby) {
        if (!(type.create(level) instanceof Mob mob)) {
            return null;
        }
        mob.setBaby(baby);
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
