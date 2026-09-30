package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassRest;
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
    /** The zombie and the cow of the trait effects shot, by entity id. */
    private static volatile int effectHost = -1;
    private static volatile int effectTarget = -1;
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
    /** The minions of the minion shots, what each is, for the fitness shot's lines (server side). */
    private static final List<java.util.Map.Entry<String, com.avicagan.bloodandbones.minion.MinionBuild>> SHOWN = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** The cow on rabbit legs of the minion shots, whose task screen is photographed (server side). */
    private static volatile java.util.UUID taskMinion;
    /** The Surgery Table with a minion being built on it, whose line is photographed (server side). */
    private static volatile BlockPos frameAt;
    /** That build, whole: the cow on rabbit legs. */
    private static volatile com.avicagan.bloodandbones.minion.MinionBuild frameBuild;
    /** Logged at each shot: the wither is the biggest, oddest body in the scene. */
    private static CarcassSavedData.Carcass witherShown;
    private static long moveAt;
    /** The view whose picture shows bits breaking off the bloody blocks, and where they are thrown. */
    private static int debrisView = -1;
    private static BlockPos debrisAt;
    /** An empty Butcher's Table and an empty Spit Roast, for the client's half of a click with something in the other hand. */
    private static BlockPos emptyTable;
    private static BlockPos emptySpit;
    /** Where the diving shot's cell of water stands: the player's feet on the ground. */
    private static BlockPos diveAt;
    /** Server ticks to let the scene play before the first picture, and between pictures. */
    private static final int SETTLE = 400;
    private static final int SHOT_GAP = 40;
    /** Held-item pictures: tools in hand, then two of a drag. */
    private static final int HANDS = 5;
    private static final int HAND_GAP = 40;
    /** Client ticks per Ponder scene: long enough for its first line of text. */
    private static final int PONDER_GAP = 110;
    private static final List<java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike>> PONDERS = List.of(
            BBBlocks.MANGLER::get, BBBlocks.BLEEDING_RACK::get, BBBlocks.SPIT_ROAST::get, BBBlocks.BUTCHER_HOOK::get, BBBlocks.BUTCHER_TABLE::get,
            BBBlocks.GUILLOTINE::get);

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
                    // the movement tutorial's toast would cover a corner of every picture
                    mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
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
                    } else if (moved > shot && shot == debrisView && age - moveAt >= SHOT_GAP - 15) {
                        debris(mc);
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
                } else if (phase == 8 && step == 1 && mc.player != null) {
                    // the Flensing Knife held on the carcass in front: it saws back and forth as it works the hide loose
                    mc.player.startUsingItem(InteractionHand.MAIN_HAND);
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
                    // the cow's pages first, then where a creeper's powder sac comes from (its Body Parts page), then where it goes (fitting it)
                    int page = scene - PONDERS.size();
                    String[] names = {"jei.png", "jei_organs.png", "jei_knacks.png", "jei_fitting.png"};
                    ItemStack sac = com.avicagan.bloodandbones.parts.Organs.stack(com.avicagan.bloodandbones.parts.PartsData.CLIENT,
                            BloodAndBones.asResource("powder_sac"), net.minecraft.resources.ResourceLocation.withDefaultNamespace("creeper"), false);
                    // the villager's heart, whose Body Parts page is the villager's: its head's knacks, disposition and surgeon's head
                    ItemStack heart = com.avicagan.bloodandbones.parts.Organs.stack(com.avicagan.bloodandbones.parts.PartsData.CLIENT,
                            BloodAndBones.asResource("village_heart"), net.minecraft.resources.ResourceLocation.withDefaultNamespace("villager"), false);
                    if (phase == 0 && page == 0) {
                        otherHand(mc);
                    }
                    if (phase == 0 && com.avicagan.bloodandbones.compat.jei.BBJeiPlugin.runtime != null) {
                        mc.setScreen(null);
                        // the cow's page: what a cow's carcass gives
                        var jei = com.avicagan.bloodandbones.compat.jei.BBJeiPlugin.runtime;
                        // and every item of this mod in the list beside it, to see their icons
                        jei.getIngredientFilter().setFilterText(page > 0 ? "@bloodandbones gland" : "@bloodandbones");
                        jei.getRecipesGui().show(jei.getJeiHelpers().getFocusFactory().createFocus(
                                page == 1 || page == 2 ? mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT : mezz.jei.api.recipe.RecipeIngredientRole.INPUT,
                                mezz.jei.api.constants.VanillaTypes.ITEM_STACK, page == 2 ? heart : page > 0 ? sac : new ItemStack(net.minecraft.world.item.Items.COW_SPAWN_EGG)));
                    } else if (page == 2 && phase == 30 && mc.screen != null) {
                        // the villager's page scrolled down to its head (the list's box, as a mouse wheel over it scrolls it)
                        int x = mc.getWindow().getGuiScaledWidth() / 2;
                        int y = mc.getWindow().getGuiScaledHeight() / 2 + 20;
                        mc.screen.mouseScrolled(x, y, 0.0, -2.0);
                        BloodAndBones.LOGGER.info("[showcase] villager's head: {}", com.avicagan.bloodandbones.minion.TaskWords.mobFacts(
                                com.avicagan.bloodandbones.parts.PartsData.CLIENT.resolve(net.minecraft.resources.ResourceLocation.withDefaultNamespace("villager"), false),
                                java.util.Map.of("profession", "none"), "head").stream().map(net.minecraft.network.chat.Component::getString).toList());
                    } else if (page < names.length - 1 && phase == PONDER_GAP - 1 && com.avicagan.bloodandbones.compat.jei.BBJeiPlugin.runtime != null) {
                        Screenshot.grab(mc.gameDirectory, PREFIX + names[page], mc.getMainRenderTarget(), message -> {
                        });
                    } else if (phase == PONDER_GAP - 1 || com.avicagan.bloodandbones.compat.jei.BBJeiPlugin.runtime == null) {
                        Screenshot.grab(mc.gameDirectory, PREFIX + names[Math.min(page, names.length - 1)], mc.getMainRenderTarget(), message -> {
                        });
                        BloodAndBones.LOGGER.info("[showcase] gland: {} | {}", sac.getHoverName().getString(), sac.getTooltipLines(
                                net.minecraft.world.item.Item.TooltipContext.of(mc.level), mc.player, net.minecraft.world.item.TooltipFlag.Default.NORMAL)
                                .stream().map(net.minecraft.network.chat.Component::getString).toList());
                        // what /bloodandbones traits explain says of a creeper, as a player reads it
                        BloodAndBones.LOGGER.info("[showcase] explain:\n{}", String.join("\n", com.avicagan.bloodandbones.parts.TraitsCommand.explain(
                                com.avicagan.bloodandbones.parts.PartsData.CLIENT, net.minecraft.resources.ResourceLocation.withDefaultNamespace("creeper"), false)
                                .stream().map(net.minecraft.network.chat.Component::getString).toList()));
                        // names as the player reads them: reworded in bloodless mode
                        BloodAndBones.LOGGER.info("[showcase] names: {} | {} | {} | {} | {}",
                                BBItems.BLOOD_STEEL_INGOT.asStack().getHoverName().getString(),
                                BBBlocks.BLEEDING_RACK.asStack().getHoverName().getString(),
                                BBBlocks.BLOODY_CASING.asStack().getHoverName().getString(),
                                com.avicagan.bloodandbones.registry.BBFluids.blood().getFluidType().getDescription().getString(),
                                net.minecraft.client.resources.language.I18n.get("block.bloodandbones.bleeding_rack.tooltip.summary"));
                        BloodAndBones.LOGGER.info("[showcase] decoration names: {} | {} | {} | {}",
                                BBBlocks.BLOODY_BRASS_CASING.asStack().getHoverName().getString(),
                                BBBlocks.GUT_CHAIN.asStack().getHoverName().getString(),
                                com.avicagan.bloodandbones.registry.BBEntities.HANGING_GUT_CHAIN.get().getDescription().getString(),
                                net.minecraft.client.resources.language.I18n.get("block.bloodandbones.ribcage_arch.tooltip.summary"));
                        BloodAndBones.LOGGER.info("[showcase] material names: {} | {} | {} | {} | {} | {} | {}",
                                BBItems.FLESH_ARM.asStack().getHoverName().getString(),
                                BBItems.SEVERED_ARM.asStack().getHoverName().getString(),
                                BBItems.CONGEALED_BLOOD.asStack().getHoverName().getString(),
                                BBItems.SOUL_CLOT.asStack().getHoverName().getString(),
                                BBBlocks.BLOODY_RAILWAY_CASING.asStack().getHoverName().getString(),
                                BBBlocks.BLOODY_CUT_CALCITE_BRICKS.asStack().getHoverName().getString(),
                                BBItems.CARCASS_BOOTS.asStack().getHoverName().getString());
                        // what a wet sound is heard as here: itself, or in bloodless mode its metal twin (the
                        // headless client has no sound, so the event the sound engine would send is sent by hand)
                        var squelch = net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.avicagan.bloodandbones.registry.BBSounds.FLESH_SQUISH.get(), 0.8F, 0.5F);
                        var heard = new net.neoforged.neoforge.client.event.sound.PlaySoundEvent(null, squelch);
                        BloodlessSounds.onPlay(heard);
                        BloodAndBones.LOGGER.info("[showcase] sound: {} is heard as {} (pitch {}, volume {})", squelch.getLocation(),
                                heard.getSound() == null ? "nothing" : heard.getSound().getLocation(),
                                heard.getSound() instanceof net.minecraft.client.resources.sounds.AbstractSoundInstance a ? ((com.avicagan.bloodandbones.mixin.SoundInstanceAccessor) a).bloodandbones$pitch() : -1.0F,
                                heard.getSound() instanceof net.minecraft.client.resources.sounds.AbstractSoundInstance a2 ? ((com.avicagan.bloodandbones.mixin.SoundInstanceAccessor) a2).bloodandbones$volume() : -1.0F);
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
                } else if (phase == PONDER_GAP - 4 && PONDERS.get(scene).get() == BBBlocks.GUILLOTINE.get()
                        && mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ponder && ponder.getActiveScene().getCurrentTime() < 274) {
                    // the lever beside it just pulled and the blade down, when the shot is taken (late: a scene slows down
                    // while its text is up)
                    ponder.seekToTime(274);
                } else if (phase == PONDER_GAP - 1) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "ponder_" + scene + ".png", mc.getMainRenderTarget(), message -> {
                    });
                    BloodAndBones.LOGGER.info("[showcase] took ponder shot {} at scene time {}", scene,
                            mc.screen instanceof net.createmod.ponder.foundation.ui.PonderUI ponder ? ponder.getActiveScene().getCurrentTime() : -1);
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
                // the missing-limb shot: square on to the camera, looking level
                if (mc.player != null && t >= 181 && t <= 188) {
                    float yaw = mc.player.getYRot();
                    mc.player.setYBodyRot(yaw);
                    mc.player.yBodyRotO = yaw;
                    mc.player.setYHeadRot(yaw);
                    mc.player.yHeadRotO = yaw;
                    mc.player.setXRot(0.0F);
                    mc.player.xRotO = 0.0F;
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
                        // a surgeon by the table, a zombie's head on a zombie (docs/NEXT.md 1.5: 141%, its stumps two buckets), and
                        // the stump its cutting left where the vent arm was: the screen names it and both prices before any cut
                        var surgeon = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(player.serverLevel());
                        BlockPos by = at.south(2);
                        surgeon.moveTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5, 180.0F, 0.0F);
                        surgeon.setup(player, by, com.avicagan.bloodandbones.minion.MinionBuild.of(piece("zombie", "body")).with("head", piece("zombie", "head"))
                                .with("right_arm", piece("zombie", "right_arm")).with("left_arm", piece("zombie", "left_arm"))
                                .with("right_leg", piece("zombie", "right_leg")).with("left_leg", piece("zombie", "left_leg")), 1000.0F);
                        surgeon.setNoAi(true);
                        surgeon.setTask(com.avicagan.bloodandbones.minion.MinionTask.SURGEON);
                        surgeon.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
                        player.serverLevel().addFreshEntity(surgeon);
                        com.avicagan.bloodandbones.body.BodyEffects.body(player).lose(com.avicagan.bloodandbones.body.BodyPart.LEFT_ARM, 2);
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                        // what the screen offers is what they carry: prosthetics, grafts, a module, their own arm, and blood to pay with
                        var inventory = player.getInventory();
                        inventory.setItem(1, new ItemStack(BBItems.HOOK_HAND.get()));
                        inventory.setItem(2, new ItemStack(BBItems.FLESH_ARM.get()));
                        inventory.setItem(3, new ItemStack(BBItems.PEG_LEG.get()));
                        inventory.setItem(4, new ItemStack(BBItems.CRUDE_HEART.get()));
                        inventory.setItem(5, new ItemStack(BBItems.GLASS_EYE.get()));
                        inventory.setItem(6, new ItemStack(BBItems.module(com.avicagan.bloodandbones.cyber.Module.MAGNET_COIL)));
                        inventory.setItem(7, BBItems.partItem(com.avicagan.bloodandbones.body.BodyPart.Kind.ARM).of(player));
                        // and a Cleaver of their own, which fits nothing: no green mark from it, and not in the carried row
                        inventory.setItem(8, new ItemStack(BBItems.CLEAVER.get()));
                        inventory.setItem(9, new ItemStack(com.avicagan.bloodandbones.registry.BBFluids.BLOOD.getBucket().get()));
                        if (com.avicagan.bloodandbones.body.SurgeryTableBlock.lieDown(player.serverLevel(), at, player)) {
                            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new com.avicagan.bloodandbones.body.Surgery.OpenPayload(at, player.getId()));
                        }
                    });
                } else if (t == 118) {
                    // first the brass arm picked: unclip it, swap it, fit the carried module; a slot on the doll hovered
                    if (mc.screen instanceof SurgeryScreen surgery) {
                        surgery.select(com.avicagan.bloodandbones.body.BodyPart.RIGHT_ARM);
                        surgery.pinHover(com.avicagan.bloodandbones.body.BodyPart.LEFT_EYE);
                    }
                    mc.getToasts().clear();
                } else if (t == 124) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "surgery_1.png", mc.getMainRenderTarget(), message -> {
                    });
                } else if (t == 126) {
                    // then the ragged stump: what fits it, and what it costs
                    if (mc.screen instanceof SurgeryScreen surgery) {
                        surgery.select(com.avicagan.bloodandbones.body.BodyPart.LEFT_ARM);
                        surgery.pinHover(null);
                    }
                } else if (t == 137) {
                    // the advancement toasts would cover the screen's corner
                    mc.getToasts().clear();
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
                                com.avicagan.bloodandbones.parts.CarcassArmour.of("helmet", cow, false), store));
                        // a polar bear's pelt chestplate with a copper backtank of blood strapped to its back
                        ItemStack tank = new ItemStack(BBItems.backtank(com.avicagan.bloodandbones.backtank.BacktankTier.COPPER));
                        com.avicagan.bloodandbones.backtank.FluidBacktankItem.setFluid(tank, new net.neoforged.neoforge.fluids.FluidStack(com.avicagan.bloodandbones.registry.BBFluids.blood(), 1500));
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, com.avicagan.bloodandbones.backtank.FluidBacktankItem.strap(
                                com.avicagan.bloodandbones.parts.CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_CHESTPLATE.get()),
                                        com.avicagan.bloodandbones.parts.CarcassArmour.of("chestplate", net.minecraft.resources.ResourceLocation.withDefaultNamespace("polar_bear"), false), store), tank));
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, com.avicagan.bloodandbones.parts.CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_LEGGINGS.get()),
                                com.avicagan.bloodandbones.parts.CarcassArmour.of("leggings", rabbit, false), store));
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, com.avicagan.bloodandbones.parts.CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_BOOTS.get()),
                                com.avicagan.bloodandbones.parts.CarcassArmour.of("boots", cow, false), store));
                        String[] parts = {"head", "torso", "arm", "leg", "tail"};
                        for (int i = 0; i < parts.length; i++) {
                            player.getInventory().setItem(i, com.avicagan.bloodandbones.parts.ScrapsItem.of(new com.avicagan.bloodandbones.parts.Source(i % 2 == 0 ? cow : rabbit, parts[i], false), 3 + i));
                        }
                        player.getInventory().setItem(5, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy());
                        player.getInventory().setItem(6, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).copy());
                        player.getInventory().setItem(7, player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).copy());
                        // and in hand, a creeper's powder sac cut out of it
                        player.getInventory().setItem(8, com.avicagan.bloodandbones.parts.Organs.stack(store, BloodAndBones.asResource("powder_sac"),
                                net.minecraft.resources.ResourceLocation.withDefaultNamespace("creeper"), false));
                        player.getInventory().selected = 8;
                        // the client keeps its own hotbar slot: told, it holds the sac
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(8));
                        player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), 180.0F, 10.0F);
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    mc.options.hideGui = true;
                } else if (t == 165) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "armour_0.png", mc.getMainRenderTarget(), message -> {
                    });
                    // and from behind: the tank strapped over the chestplate
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                } else if (t == 170) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "armour_2.png", mc.getMainRenderTarget(), message -> {
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
                        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                            player.setItemSlot(slot, ItemStack.EMPTY);
                        }
                        player.getInventory().clearContent();
                        // minions, somewhere clear: four stitched ones in a row, a trough, and one being built on the table
                        // on the ground there, whatever the player was standing on before
                        player.stopRiding();
                        int groundY = player.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                                player.getBlockX(), player.getBlockZ() - 41);
                        player.teleportTo(player.serverLevel(), player.getBlockX() + 0.5, groundY, player.getBlockZ() - 40.5, 0.0F, 20.0F);
                        // four stitched minions beside: a cow on rabbit legs, a whole cow, a zombie with a pig's head on a
                        // rabbit's haunches, and a legless cow out of blood on its side
                        java.util.function.BiFunction<String, String, com.avicagan.bloodandbones.minion.PieceRef> ref = (mob, bone) ->
                                new com.avicagan.bloodandbones.minion.PieceRef(net.minecraft.resources.ResourceLocation.withDefaultNamespace(mob), bone,
                                        net.minecraft.resources.ResourceLocation.withDefaultNamespace(mob.equals("phantom") ? "textures/entity/phantom.png"
                                                : "textures/entity/" + mob + "/" + (mob.equals("rabbit") ? "brown" : mob.equals("horse") ? "horse_brown" : mob) + ".png"),
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
                        SHOWN.add(java.util.Map.entry("Cow on rabbit legs", hopper));
                        frameBuild = hopper;
                        SHOWN.add(java.util.Map.entry("Whole cow", whole));
                        SHOWN.add(java.util.Map.entry("Zombie, pig's head, rabbit's haunches, one arm", odd));
                        SHOWN.add(java.util.Map.entry("Legless cow", cow));
                        for (int m = 0; m < builds.size(); m++) {
                            var minion = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(player.serverLevel());
                            BlockPos at = player.blockPosition().offset(m * 3 - 4, 0, 7);
                            minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180.0F, 0.0F);
                            minion.setup(player, at, builds.get(m), 1000.0F);
                            minion.setNoAi(true);
                            if (m == 2) {
                                // the zombie's torso burns by day: a carved pumpkin on its pig's head keeps the sun off
                                minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN));
                            } else if (m == 1) {
                                // and the whole cow in a zombie's head, as a skull is worn
                                minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.ZOMBIE_HEAD));
                            }
                            player.serverLevel().addFreshEntity(minion);
                            if (m == 0) {
                                taskMinion = minion.getUUID();
                            }
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
                        frameAt = tableAt;
                        player.serverLevel().setBlockAndUpdate(tableAt, BBBlocks.SURGERY_TABLE.getDefaultState()
                                .setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.ASSEMBLY));
                        if (player.serverLevel().getBlockEntity(tableAt) instanceof com.avicagan.bloodandbones.body.SurgeryTableBlockEntity table) {
                            table.setBuild(cow.with("right_front_leg", ref.apply("rabbit", "right_front_leg")).with("right_hind_leg", ref.apply("rabbit", "right_haunch")));
                        }
                        // a brass cow of skinned pieces with a Magnet Coil, beside a Charging Cradle turned by a motor below
                        java.util.function.Function<String, com.avicagan.bloodandbones.minion.PieceRef> skinned = bone ->
                                new com.avicagan.bloodandbones.minion.PieceRef(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow"), bone,
                                        com.avicagan.bloodandbones.carcass.CarcassLook.FLESH, java.util.List.of(), 1.0F, true, java.util.Map.of(), false);
                        var brass = new com.avicagan.bloodandbones.minion.MinionBuild(true, skinned.apply("body"), java.util.List.of(), true)
                                .with("head", skinned.apply("head")).with("right_front_leg", skinned.apply("right_front_leg"))
                                .with("left_front_leg", skinned.apply("left_front_leg")).with("right_hind_leg", skinned.apply("right_hind_leg"))
                                .with("left_hind_leg", skinned.apply("left_hind_leg"));
                        BlockPos cradleAt = player.blockPosition().offset(-7, 0, 9);
                        player.serverLevel().setBlockAndUpdate(cradleAt.below(), com.simibubi.create.AllBlocks.CREATIVE_MOTOR.getDefaultState()
                                .setValue(com.simibubi.create.content.kinetics.base.DirectionalKineticBlock.FACING, net.minecraft.core.Direction.UP));
                        player.serverLevel().setBlockAndUpdate(cradleAt, BBBlocks.CHARGING_CRADLE.getDefaultState());
                        if (player.serverLevel().getBlockEntity(cradleAt) instanceof com.avicagan.bloodandbones.minion.ChargingCradleBlockEntity cradle) {
                            cradle.inventory.insertItem(0, new ItemStack(BBItems.SOUL_CANISTER.get()), false);
                            cradle.inventory.insertItem(1, new ItemStack(BBItems.SOUL_CANISTER.get()), false);
                        }
                        var brassMinion = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(player.serverLevel());
                        BlockPos brassAt = player.blockPosition().offset(-7, 0, 7);
                        brassMinion.moveTo(brassAt.getX() + 0.5, brassAt.getY(), brassAt.getZ() + 0.5, 150.0F, 0.0F);
                        brassMinion.setup(player, brassAt, brass, 1000.0F);
                        brassMinion.setModule(com.avicagan.bloodandbones.cyber.Module.MAGNET_COIL);
                        brassMinion.setNoAi(true);
                        player.serverLevel().addFreshEntity(brassMinion);
                        // and a cow on horse legs, saddled
                        var mount = cow.with("right_front_leg", ref.apply("horse", "right_front_leg")).with("left_front_leg", ref.apply("horse", "left_front_leg"))
                                .with("right_hind_leg", ref.apply("horse", "right_hind_leg")).with("left_hind_leg", ref.apply("horse", "left_hind_leg"));
                        var horse = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(player.serverLevel());
                        BlockPos horseAt = player.blockPosition().offset(8, 0, 10);
                        horse.moveTo(horseAt.getX() + 0.5, horseAt.getY(), horseAt.getZ() + 0.5, 200.0F, 0.0F);
                        horse.setup(player, horseAt, mount, 1000.0F);
                        horse.setNoAi(true);
                        player.serverLevel().addFreshEntity(horse);
                        horse.equipSaddle(new ItemStack(net.minecraft.world.item.Items.SADDLE), null);
                        // someone in the saddle, to see the seat sits on the torso and not on top of the head
                        var rider = net.minecraft.world.entity.EntityType.VILLAGER.create(player.serverLevel());
                        rider.moveTo(horse.getX(), horse.getY() + 1.0, horse.getZ(), 200.0F, 0.0F);
                        rider.setNoAi(true);
                        player.serverLevel().addFreshEntity(rider);
                        rider.startRiding(horse, true);
                        // a zombie with a cow's head holding a bow, an iron helmet stretched over the cow's skull, and a cow
                        // hanging in the air on a phantom's wings (its hind legs dangling)
                        var archer = com.avicagan.bloodandbones.minion.MinionBuild.of(ref.apply("zombie", "body")).with("head", ref.apply("cow", "head"))
                                .with("right_arm", ref.apply("zombie", "right_arm")).with("left_arm", ref.apply("zombie", "left_arm"))
                                .with("right_leg", ref.apply("zombie", "right_leg")).with("left_leg", ref.apply("zombie", "left_leg"));
                        var bowman = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(player.serverLevel());
                        BlockPos bowAt = player.blockPosition().offset(-3, 0, 11);
                        bowman.moveTo(bowAt.getX() + 0.5, bowAt.getY(), bowAt.getZ() + 0.5, 160.0F, 0.0F);
                        // a client turns a new mob's body to its head's turn
                        bowman.setYHeadRot(160.0F);
                        bowman.setYBodyRot(160.0F);
                        bowman.setup(player, bowAt, archer, 1000.0F);
                        SHOWN.add(java.util.Map.entry("Zombie with a cow's head, holding a bow", archer));
                        bowman.setNoAi(true);
                        bowman.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(net.minecraft.world.item.Items.BOW));
                        bowman.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                        player.serverLevel().addFreshEntity(bowman);
                        var winged = cow.with("right_front_leg", ref.apply("phantom", "body/right_wing_base")).with("left_front_leg", ref.apply("phantom", "body/left_wing_base"))
                                .with("right_hind_leg", ref.apply("cow", "right_hind_leg")).with("left_hind_leg", ref.apply("cow", "left_hind_leg"));
                        var flier = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(player.serverLevel());
                        BlockPos flyAt = player.blockPosition().offset(3, 2, 11);
                        flier.moveTo(flyAt.getX() + 0.5, flyAt.getY(), flyAt.getZ() + 0.5, 200.0F, 0.0F);
                        flier.setYHeadRot(200.0F);
                        flier.setYBodyRot(200.0F);
                        flier.setup(player, flyAt, winged, 1000.0F);
                        flier.setNoAi(true);
                        flier.setNoGravity(true);
                        player.serverLevel().addFreshEntity(flier);
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    mc.options.hideGui = true;
                } else if (t == 178) {
                    // a missing limb holds and wears nothing: the left arm gone with a shield in that hand, the right leg gone in
                    // iron leggings and boots; the shield and that leg's armour are not drawn, the sword and the rest are
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        var body = com.avicagan.bloodandbones.body.BodyEffects.body(player);
                        body.lose(com.avicagan.bloodandbones.body.BodyPart.LEFT_ARM);
                        body.lose(com.avicagan.bloodandbones.body.BodyPart.RIGHT_LEG, 3);
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(net.minecraft.world.item.Items.IRON_LEGGINGS));
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(net.minecraft.world.item.Items.IRON_BOOTS));
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(net.minecraft.world.item.Items.SHIELD));
                        // turned away from the minions, so the camera in front has open ground behind it
                        player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), 180.0F, 0.0F);
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                } else if (t == 188) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "body_5.png", mc.getMainRenderTarget(), message -> {
                    });
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.setData(com.avicagan.bloodandbones.body.BBAttachments.BODY, new com.avicagan.bloodandbones.body.Body());
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                            player.setItemSlot(slot, ItemStack.EMPTY);
                        }
                        // facing the minions again, as their shots want
                        player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ(), 0.0F, 20.0F);
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                } else if (t == 200) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "minions_0.png", mc.getMainRenderTarget(), message -> {
                    });
                    // and from behind and to the side: legs and stumps
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.teleportTo(player.serverLevel(), player.getX() - 6.0, player.getY(), player.getZ() + 12.0, -135.0F, 12.0F);
                        fitness(player);
                    });
                    // the fitness lines in the chat, over that view
                    mc.options.hideGui = false;
                } else if (t == 203) {
                    // a recipe toast would cover the corner of the lines
                    mc.getToasts().clear();
                } else if (t == 206) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "tasks_0.png", mc.getMainRenderTarget(), message -> {
                    });
                    // the cow on rabbit legs' task screen, opened as its maker's crouching empty hand opens it (docs/NEXT.md 1.3); each
                    // step below is ten ticks after the last, so that a frame is drawn between them however slowly the scene draws
                    // (a picture is the last frame drawn, and the screen comes back from the server)
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        if (taskMinion != null && player.serverLevel().getEntity(taskMinion) instanceof com.avicagan.bloodandbones.minion.MinionEntity minion) {
                            com.avicagan.bloodandbones.minion.MinionTasks.showScreen(minion, player);
                            BloodAndBones.LOGGER.info("[showcase] task screen on the cow on rabbit legs: {}", com.avicagan.bloodandbones.minion.MinionTasks.status(minion).getString());
                        }
                    });
                } else if (t == 216) {
                    // its reasons for herding shown over its row, as a hovering mouse shows them
                    if (mc.screen instanceof MinionTaskScreen screen) {
                        screen.pinHover(com.avicagan.bloodandbones.minion.MinionTask.HERDER);
                    }
                } else if (t == 226) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "tasks_1.png", mc.getMainRenderTarget(), message -> {
                    });
                    BloodAndBones.LOGGER.info("[showcase] took task screen shot; screen {}", mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
                    // and the rest of the list, scrolled to its end
                    if (mc.screen instanceof MinionTaskScreen screen) {
                        screen.pinHover(null);
                        screen.scrollToEnd();
                    }
                } else if (t == 236) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "tasks_2.png", mc.getMainRenderTarget(), message -> {
                    });
                    // and what its fitness makes of a butcher's work (docs/NEXT.md 1.2): its strokes and how much of each cut it keeps
                    if (mc.screen instanceof MinionTaskScreen screen) {
                        screen.pinHover(com.avicagan.bloodandbones.minion.MinionTask.BUTCHER);
                    }
                } else if (t == 246) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "tasks_3.png", mc.getMainRenderTarget(), message -> {
                    });
                    // and the Tender's (stage E): what it does, what it reads and how often it looks round
                    if (mc.screen instanceof MinionTaskScreen screen) {
                        screen.pinHover(com.avicagan.bloodandbones.minion.MinionTask.TENDER);
                    }
                } else if (t == 256) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "tasks_4.png", mc.getMainRenderTarget(), message -> {
                    });
                    mc.setScreen(null);
                    mc.options.hideGui = true;
                } else if (t == 260) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "minions_1.png", mc.getMainRenderTarget(), message -> {
                    });
                    // and near, from in front: the bowman's bow and helmet
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.teleportTo(player.serverLevel(), player.getX() + 2.0, player.getY(), player.getZ() - 3.8, -20.0F, 10.0F);
                    });
                } else if (t == 272) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "minions_2.png", mc.getMainRenderTarget(), message -> {
                    });
                    // then the flier's wings, from below and in front
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.teleportTo(player.serverLevel(), player.getX() + 7.0, player.getY(), player.getZ(), 0.0F, -20.0F);
                    });
                } else if (t == 287) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "minions_3.png", mc.getMainRenderTarget(), message -> {
                    });
                    // then the pig's head in its carved pumpkin, from in front (the row faces away from where it was made)
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ() + 1.5, 160.0F, 5.0F);
                    });
                } else if (t == 302) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "minions_4.png", mc.getMainRenderTarget(), message -> {
                    });
                    // then the whole cow beside it in its zombie's head, from in front the same way
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.teleportTo(player.serverLevel(), player.getX() - 3.0, player.getY(), player.getZ(), 160.0F, 5.0F);
                    });
                } else if (t == 312) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "minions_5.png", mc.getMainRenderTarget(), message -> {
                    });
                    // stumps, raggeder the dearer (docs/NEXT.md 1.5): the right arm a fit surgeon's (a bucket), the left leg a fair
                    // one's (two), the left arm a poor one's (three), and a clean cut for the right leg
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        var body = com.avicagan.bloodandbones.body.BodyEffects.body(player);
                        body.lose(com.avicagan.bloodandbones.body.BodyPart.RIGHT_ARM, 1);
                        body.lose(com.avicagan.bloodandbones.body.BodyPart.LEFT_LEG, 2);
                        body.lose(com.avicagan.bloodandbones.body.BodyPart.LEFT_ARM, 3);
                        body.lose(com.avicagan.bloodandbones.body.BodyPart.RIGHT_LEG, 0);
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                        // the carcass armour off, which is still drawn over a limb that is gone
                        for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{net.minecraft.world.entity.EquipmentSlot.HEAD,
                                net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET}) {
                            player.setItemSlot(slot, ItemStack.EMPTY);
                        }
                        // back beyond where the view from behind was taken, clear of the minions, for this and the night's effects
                        player.teleportTo(player.serverLevel(), player.getX() - 12.0, player.getY(), player.getZ() + 2.3, 150.0F, 15.0F);
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                } else if (t == 322) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "stumps.png", mc.getMainRenderTarget(), message -> {
                    });
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.setData(com.avicagan.bloodandbones.body.BBAttachments.BODY, new com.avicagan.bloodandbones.body.Body());
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                    });
                    // trait effects at night: a glow squid's sac in a chestplate, and ahead a bleeding cow, a guardian's beam at it,
                    // and both outlined as a sense would show them
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        ServerLevel level = player.serverLevel();
                        level.setDayTime(18000);
                        var squid = net.minecraft.resources.ResourceLocation.withDefaultNamespace("glow_squid");
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, com.avicagan.bloodandbones.parts.CarcassArmourItem.make(new ItemStack(BBItems.CARCASS_CHESTPLATE.get()),
                                com.avicagan.bloodandbones.parts.CarcassArmour.of("chestplate", squid, false).withOrgan(java.util.Optional.of(new com.avicagan.bloodandbones.parts.CarcassArmour.Organ(
                                        BloodAndBones.asResource("glow_sac"), squid, false))), com.avicagan.bloodandbones.parts.PartsData.of(level)));
                        com.avicagan.bloodandbones.parts.ActiveTraits.rebuild(player);
                        player.teleportTo(level, player.getX(), player.getY(), player.getZ(), 0.0F, 10.0F);
                        net.minecraft.world.phys.Vec3 ahead = player.position().add(0.0, 0.0, 6.0);
                        var cow = EntityType.COW.create(level);
                        cow.moveTo(ahead.x + 1.5, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(ahead.x + 1.5), (int) Math.floor(ahead.z)), ahead.z, 90.0F, 0.0F);
                        cow.setNoAi(true);
                        level.addFreshEntity(cow);
                        cow.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.avicagan.bloodandbones.parts.effect.RangedContent.BLEEDING, 1200, 1));
                        // a snow golem for the beam's source: the world is peaceful, so no monster stays
                        var zombie = EntityType.SNOW_GOLEM.create(level);
                        zombie.moveTo(ahead.x - 3.0, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(ahead.x - 3.0), (int) Math.floor(ahead.z + 1.5)), ahead.z + 1.5, -90.0F, 0.0F);
                        zombie.setNoAi(true);
                        level.addFreshEntity(zombie);
                        effectHost = zombie.getId();
                        effectTarget = cow.getId();
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                    mc.options.hideGui = true;
                } else if (t == 332) {
                    com.avicagan.bloodandbones.client.effect.RangedClient.receive(new com.avicagan.bloodandbones.parts.effect.BeamPayload(effectHost, effectTarget, 400, 16.0F, "guardian_beam"));
                    com.avicagan.bloodandbones.client.effect.SocialClient.receive(new com.avicagan.bloodandbones.parts.effect.SensePayload("reveal",
                            java.util.List.of(effectHost, effectTarget), 400));
                } else if (t == 344) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "effects_0.png", mc.getMainRenderTarget(), message -> {
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                } else if (t == 360) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "effects_1.png", mc.getMainRenderTarget(), message -> {
                    });
                    // the backtank's gauge: a copper tank of blood worn, running a Flesh Arm and a Sinew Leg; a Hydraulic Arm
                    // beside them has no soul blood, so it shows dimmed
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.serverLevel().setDayTime(6000);
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
                        // back to the Surgery Table with the minion being built on it, its build now the cow on rabbit legs,
                        // looking down at it (docs/NEXT.md 1.4)
                        if (frameAt != null) {
                            if (frameBuild != null && player.serverLevel().getBlockEntity(frameAt) instanceof com.avicagan.bloodandbones.body.SurgeryTableBlockEntity table) {
                                table.setBuild(frameBuild);
                            }
                            player.teleportTo(player.serverLevel(), frameAt.getX() + 0.5, frameAt.getY(), frameAt.getZ() - 2.6, 0.0F, 32.0F);
                        }
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    mc.options.hideGui = false;
                    // the night's outlines ended
                    com.avicagan.bloodandbones.client.effect.SocialClient.receive(new com.avicagan.bloodandbones.parts.effect.SensePayload("reveal",
                            java.util.List.of(effectHost, effectTarget), 0));
                } else if (t == 388) {
                    // the fitness lines off the chat, which would cover it
                    mc.gui.getChat().clearMessages(false);
                    // a plain click on the table: its line while building, on the action bar
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        if (frameAt != null) {
                            net.minecraft.world.level.block.state.BlockState table = player.serverLevel().getBlockState(frameAt);
                            table.useWithoutItem(player.serverLevel(), player, new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(frameAt),
                                    net.minecraft.core.Direction.UP, frameAt, false));
                            if (player.serverLevel().getBlockEntity(frameAt) instanceof com.avicagan.bloodandbones.body.SurgeryTableBlockEntity built) {
                                built.build().ifPresent(b -> BloodAndBones.LOGGER.info("[showcase] table line: {}", com.avicagan.bloodandbones.minion.MinionAssembly
                                        .status(com.avicagan.bloodandbones.parts.PartsData.SERVER, b).getString()));
                            }
                        }
                    });
                } else if (t == 396) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "table_line.png", mc.getMainRenderTarget(), message -> {
                    });
                    // the table's line off the action bar, which would sit over the gauge
                    mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.empty(), false);
                    // the backtank's gauge: a copper tank of blood worn, running a Flesh Arm and a Sinew Leg; a Hydraulic Arm
                    // beside them has no soul blood, so it shows dimmed
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        ItemStack tank = new ItemStack(BBItems.backtank(com.avicagan.bloodandbones.backtank.BacktankTier.COPPER));
                        com.avicagan.bloodandbones.backtank.FluidBacktankItem.setFluid(tank, new net.neoforged.neoforge.fluids.FluidStack(BBFluids.blood(), 1500));
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, tank);
                        var body = com.avicagan.bloodandbones.body.BodyEffects.body(player);
                        body.fit(com.avicagan.bloodandbones.body.BodyPart.RIGHT_ARM, new ItemStack(BBItems.FLESH_ARM.get()));
                        body.fit(com.avicagan.bloodandbones.body.BodyPart.LEFT_ARM, new ItemStack(BBItems.HYDRAULIC_ARM.get()));
                        body.fit(com.avicagan.bloodandbones.body.BodyPart.RIGHT_LEG, new ItemStack(BBItems.SINEW_LEG.get()));
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                        // a second tank in the hotbar, to show its item bar in the blood's colour; the hand stays empty (the
                        // armour shots left the last slot the one held)
                        player.getInventory().setItem(0, tank.copy());
                    });
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                    mc.options.hideGui = false;
                } else if (t == 418) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "gauge.png", mc.getMainRenderTarget(), message -> {
                    });
                    BloodAndBones.LOGGER.info("[showcase] gauge: {} implants; bar colour {}", BacktankGauge.poweredImplants(mc.player).size(),
                            Integer.toHexString(BacktankGauge.colour(com.avicagan.bloodandbones.backtank.FluidBacktankItem.fluid(
                                    com.avicagan.bloodandbones.backtank.FluidBacktankItem.wornBy(mc.player)))));
                    // then diving on Create's own backtank, with a Hydraulic Arm fitted and no Fluid Backtank: Create's air
                    // gauge takes the place, and this mod's (a faded tank, the arm dimmed) moves up a row above it
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        player.getInventory().clearContent();
                        player.setData(com.avicagan.bloodandbones.body.BBAttachments.BODY, new com.avicagan.bloodandbones.body.Body());
                        com.avicagan.bloodandbones.body.BodyEffects.body(player).fit(com.avicagan.bloodandbones.body.BodyPart.LEFT_ARM,
                                new ItemStack(BBItems.HYDRAULIC_ARM.get()));
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                        ItemStack air = com.simibubi.create.AllItems.COPPER_BACKTANK.asStack();
                        air.set(com.simibubi.create.AllDataComponents.BACKTANK_AIR, 900);
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, air);
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, com.simibubi.create.AllItems.COPPER_DIVING_HELMET.asStack());
                        player.setGameMode(GameType.SURVIVAL);
                        diveAt = dive(player.serverLevel(), player, true);
                    });
                } else if (t == 458) {
                    // the recipe and advancement toasts the diving gear brings would cover the corner
                    mc.getToasts().clear();
                } else if (t == 468) {
                    Screenshot.grab(mc.gameDirectory, PREFIX + "gauge_diving.png", mc.getMainRenderTarget(), message -> {
                    });
                    BloodAndBones.LOGGER.info("[showcase] diving: Create's air gauge up {}, this gauge {} implants",
                            mc.player != null && BacktankGauge.createAirShowing(mc.player), mc.player == null ? 0 : BacktankGauge.poweredImplants(mc.player).size());
                    server.execute(() -> {
                        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                        dive(player.serverLevel(), player, false);
                        player.setGameMode(GameType.CREATIVE);
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, ItemStack.EMPTY);
                        player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
                        player.getInventory().clearContent();
                        player.setData(com.avicagan.bloodandbones.body.BBAttachments.BODY, new com.avicagan.bloodandbones.body.Body());
                        com.avicagan.bloodandbones.body.BodyEffects.changed(player);
                    });
                    // then the physics yard, which goes on to the Ponder scenes
                    stage = 7;
                    ticks = 0;
                }
            }
            case 7 -> physics(mc);
            case 8 -> contraptions(mc);
            case 9 -> groups(mc);
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- the physics yard (docs/ARCHITECTURE-PROPOSAL.md 15.28)

    /** Server time the physics yard was started, -1 before; its corner; how far through its steps it is. */
    private static long yardAt = -1;
    private static BlockPos yard;
    private static int yardStep;
    private static CarcassSavedData.Carcass dragged;
    private static CarcassSavedData.Carcass flanked;
    private static CarcassSavedData.Carcass behind;
    private static CarcassSavedData.Carcass headed;
    private static final CarcassSavedData.Carcass[] hungCows = new CarcassSavedData.Carcass[3];

    /**
     * The physics the brief asks for, photographed: a cow dragged by a hind leg, come round rear first behind its dragger;
     * three cows killed by a blow, one from the flank (down on its side, away from the blow), one from behind (pitched
     * forward) and one struck in the face from in front (its head snapped back, down about where it stood, not flung),
     * their heads lolled onto the ground; and three hung cows, one whole, one with its right hind leg cut off (hanging
     * differently, lower on the side that kept its leg) and one knocked a moment before, mid-swing. Timed on the server's
     * clock, as the scene's pictures are.
     */
    private static void physics(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (yardAt < 0) {
            yardAt = now;
            yard = origin.offset(-60, 0, 0);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                player.getInventory().clearContent();
                player.teleportTo(player.serverLevel(), yard.getX() + 0.5, yard.getY(), yard.getZ() - 4.5, 0.0F, 20.0F);
            });
            return;
        }
        long age = now - yardAt;
        if (yardStep == 0 && age >= 10) {
            yardStep = 1;
            server.execute(() -> yardBuild(server.overworld(), server.getPlayerList().getPlayers().get(0)));
        } else if (yardStep == 1 && age >= 30) {
            yardStep = 2;
            // the second hung cow loses its right hind leg, and the leg drops away
            server.execute(() -> {
                ServerLevel level = server.overworld();
                for (int i = 0; i < CarcassButchery.CUTS_TO_SEVER && hungCows[1] != null; i++) {
                    CarcassButchery.cut(level, null, hungCows[1], "right_hind_leg", null);
                }
            });
        } else if (yardStep >= 2 && yardStep <= 3 && age >= 31) {
            // walk east, away from the cow, dragging it by its hind leg, looking half right (south-east): still walking
            // away from it, and the camera in front, looking back, sees past the player to the cow
            double x = yard.getX() + 0.5 + 0.1 * (age - 31);
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(player.serverLevel(), x, yard.getY(), yard.getZ() + 1.5, -45.0F, 5.0F);
            });
            if (yardStep == 2 && age >= 92) {
                yardStep = 3;
                // from in front: the player walking at the camera, the cow trailing behind, rear first
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
            } else if (yardStep == 3 && age >= 104) {
                yardStep = 4;
                Screenshot.grab(mc.gameDirectory, PREFIX + "physics_0.png", mc.getMainRenderTarget(), message -> {
                });
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                    BloodAndBones.LOGGER.info("[showcase] dragged cow's head end {} (dragged east, +x), player at {}", headEnd(level, dragged),
                            player.position());
                    CarcassDrag.stop(level, player);
                    // two cows struck dead as they stand, facing south: one on its right flank, one from behind
                    flanked = carcass(level, EntityType.COW, yard.offset(2, 0, 10), false);
                    behind = carcass(level, EntityType.COW, yard.offset(8, 0, 8), false);
                    if (flanked != null) {
                        CarcassAssembler.blow(level, flanked, new net.minecraft.world.phys.Vec3(1.0, 0.0, 0.0));
                    }
                    if (behind != null) {
                        CarcassAssembler.blow(level, behind, new net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0));
                    }
                    // and one in the face, as a killer in front of it swinging at its head lands the blow
                    headed = carcass(level, EntityType.COW, yard.offset(5, 0, 12), false);
                    if (headed != null) {
                        faceBlow(level, headed);
                    }
                });
            }
        } else if (yardStep == 4 && age < 250) {
            // watch them fall from the south, held there (nothing else moves the camera meanwhile)
            yardView(server, 5.0, 1.0, 18.5, 180.0F, 20.0F);
        } else if (yardStep == 4) {
            yardStep = 5;
            Screenshot.grab(mc.gameDirectory, PREFIX + "physics_1.png", mc.getMainRenderTarget(), message -> {
            });
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                if (flanked != null && behind != null && headed != null) {
                    BloodAndBones.LOGGER.info("[showcase] struck cows: flank at {} head end {}, behind at {} head end {}, face at {} head end {} (stood at {}, struck on its {}); "
                                    + "camera at {}",
                            CarcassAssembler.boneWorldPosition(player.serverLevel(), flanked, flanked.rootBone), headEnd(player.serverLevel(), flanked),
                            CarcassAssembler.boneWorldPosition(player.serverLevel(), behind, behind.rootBone), headEnd(player.serverLevel(), behind),
                            CarcassAssembler.boneWorldPosition(player.serverLevel(), headed, headed.rootBone), headEnd(player.serverLevel(), headed),
                            yard.offset(5, 0, 12), headed.hitBone,
                            player.position());
                }
            });
        } else if (yardStep == 5 && age < 300) {
            // the hooks, from in front
            yardView(server, 4.0, 0.5, 14.5, 0.0F, -10.0F);
        } else if (yardStep == 5 && age >= 300) {
            yardStep = 6;
            // a punch from the east on the third hung cow, a moment before the picture
            server.execute(() -> {
                ServerLevel level = server.overworld();
                if (hungCows[2] != null) {
                    CarcassRest.knock(level, hungCows[2], hungCows[2].rootBone, null, new net.minecraft.world.phys.Vec3(-1.0, 0.0, 0.0), 1.0);
                }
            });
        } else if (yardStep == 6 && age < 307) {
            yardView(server, 4.0, 0.5, 14.5, 0.0F, -10.0F);
        } else if (yardStep == 6) {
            yardStep = 7;
            Screenshot.grab(mc.gameDirectory, PREFIX + "physics_2.png", mc.getMainRenderTarget(), message -> {
            });
            server.execute(() -> {
                ServerLevel level = server.overworld();
                // what the pictures show, in numbers
                for (int i = 0; i < hungCows.length; i++) {
                    CarcassSavedData.Carcass cow = hungCows[i];
                    var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
                    if (cow != null && container.getSubLevel(cow.bones.get(cow.rootBone)) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel torso) {
                        org.joml.Vector3d up = torso.logicalPose().orientation().transform(new org.joml.Vector3d(0, -1, 0));
                        BloodAndBones.LOGGER.info("[showcase] hung cow {}: bodies {}, head end {}", i, cow.bones.size(), up);
                    }
                }
            });
            // then the carcasses on contraptions and ships, which go on to the Ponder scenes
            stage = 8;
            ticks = 0;
        }
    }

    // ---------------------------------------------------------------- carcasses on contraptions and ships (15.30)

    /** Server time the contraption yard was started, -1 before; its corner; how far through its steps it is. */
    private static long rideAt = -1;
    private static BlockPos ride;
    private static int rideStep;
    private static dev.ryanhcode.sable.sublevel.ServerSubLevel rideShip;
    private static boolean rideShipShot;
    /** Where on the ship's deck (in its plot) a cow is dropped while it flies, and that cow. */
    private static org.joml.Vector3d rideDrop;
    private static CarcassSavedData.Carcass rideDropped;

    /**
     * Rule 5, photographed: a cow hung on a Shackle Hook under a stone block that a Mechanical Piston pushes, seen while it
     * moves (the cow rides in the hook's data and the hook's actor draws it hanging there) and once set down (hung again,
     * a body); and a Sable ship, a deck with a gallows, carrying a cow resting on its deck and one hung from its hook, seen
     * as it lifts off the ground and flies along; then, flying on more slowly, a third cow dropped on its deck, seen once
     * it has come to rest there, pinned to the moving deck. Timed on the server's clock.
     */
    private static void contraptions(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (rideAt < 0) {
            rideAt = now;
            ride = origin.offset(-60, 0, 45);
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            mc.options.hideGui = true;
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(player.serverLevel(), ride.getX() + 0.5, ride.getY(), ride.getZ() - 4.5, 0.0F, 20.0F);
            });
            return;
        }
        long age = now - rideAt;
        if (rideStep == 0 && age >= 10) {
            rideStep = 1;
            server.execute(() -> rideBuild(server.overworld(), server.getPlayerList().getPlayers().get(0)));
        } else if (rideStep == 1 && age < 200) {
            // the piston's row from the north, back far enough to see where the hook starts and where it stops
            rideView(server, 4.5, 1.5, -6.5, 0.0F, -8.0F);
        } else if (rideStep == 1) {
            rideStep = 2;
            // the piston starts, slowly: two blocks east in a few seconds
            server.execute(() -> {
                if (server.overworld().getBlockEntity(ride.offset(2, 3, 0)) instanceof CreativeMotorBlockEntity motor) {
                    motor.generatedSpeed.setValue(-16);
                }
            });
        } else if (rideStep == 2 && age >= 230) {
            rideStep = 3;
            Screenshot.grab(mc.gameDirectory, PREFIX + "contraption_0.png", mc.getMainRenderTarget(), message -> {
            });
            server.execute(() -> BloodAndBones.LOGGER.info("[showcase] moving hook: contraptions {}, cow in the world {}",
                    server.overworld().getEntitiesOfClass(com.simibubi.create.content.contraptions.AbstractContraptionEntity.class,
                            new net.minecraft.world.phys.AABB(ride).inflate(8)).size(),
                    CarcassSavedData.get(server.overworld()).all().stream().filter(c -> {
                        org.joml.Vector3d at = CarcassAssembler.boneWorldPosition(server.overworld(), c, c.rootBone);
                        return at != null && at.distance(ride.getX(), ride.getY(), ride.getZ()) < 8;
                    }).count()));
        } else if (rideStep == 3 && age >= 360) {
            rideStep = 4;
            Screenshot.grab(mc.gameDirectory, PREFIX + "contraption_1.png", mc.getMainRenderTarget(), message -> {
            });
        } else if (rideStep == 4 && age < 380) {
            // the ship from the south
            rideView(server, 5.0, 2.0, 23.0, 180.0F, 12.0F);
        } else if (rideStep == 4 && age < 440) {
            // it lifts clear of the ground and flies east, kept level, set before every physics substep so it goes
            // smoothly; follow it
            double dx = (age - 380) * 0.07;
            rideView(server, 5.0 + dx, 2.0, 23.0, 180.0F, 12.0F);
            if (age == 380) {
                server.execute(() -> {
                    if (rideShip != null && !rideShip.isRemoved()) {
                        com.avicagan.bloodandbones.gametest.ContraptionTests.cruise(rideShip, new org.joml.Vector3d(1.4, 0.0, 0.0), 0.25);
                    }
                });
            }
            if (age >= 430 && !rideShipShot) {
                rideShipShot = true;
                Screenshot.grab(mc.gameDirectory, PREFIX + "contraption_2.png", mc.getMainRenderTarget(), message -> {
                });
                server.execute(() -> BloodAndBones.LOGGER.info("[showcase] ship at {}", rideShip == null ? null : rideShip.logicalPose().position()));
            }
        } else if (rideStep == 4) {
            // slower now, and a cow dropped on its deck as it goes: it comes to rest on the moving deck, pinned to it
            rideStep = 5;
            server.execute(() -> {
                if (rideShip != null && !rideShip.isRemoved()) {
                    com.avicagan.bloodandbones.gametest.ContraptionTests.cruise(rideShip, new org.joml.Vector3d(0.6, 0.0, 0.0));
                    org.joml.Vector3d over = rideShip.logicalPose().transformPosition(rideDrop, new org.joml.Vector3d()).add(0.0, 1.5, 0.0);
                    rideDropped = carcass(server.overworld(), EntityType.COW, BlockPos.containing(over.x, over.y, over.z), false, false, 0.0F);
                }
            });
        } else if (rideStep == 5 && age < 620) {
            rideView(server, 5.0 + 4.2 + (age - 440) * 0.03, 2.0, 23.0, 180.0F, 12.0F);
        } else if (rideStep == 5) {
            rideStep = 6;
            Screenshot.grab(mc.gameDirectory, PREFIX + "contraption_3.png", mc.getMainRenderTarget(), message -> {
            });
            server.execute(() -> BloodAndBones.LOGGER.info("[showcase] cow dropped on the flying ship: resting {}, pinned to the ship {}, ship going {}",
                    rideDropped != null && rideDropped.resting, rideDropped != null && rideShip != null && rideShip.getUniqueId().equals(rideDropped.restDeck),
                    rideShip == null ? null : dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(rideShip).getLinearVelocity(new org.joml.Vector3d())));
        } else if (rideStep == 6) {
            rideStep = 7;
            server.execute(() -> {
                if (rideShip != null) {
                    com.avicagan.bloodandbones.gametest.ContraptionTests.stopCruising(rideShip);
                }
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(player.serverLevel(), origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0.0F, 0.0F);
            });
            // then the groups yard, which goes on to the Ponder scenes
            stage = 9;
            ticks = 0;
        }
    }

    /** Hold the player (the camera) at a spot of the contraption yard, looking one way. */
    private static void rideView(MinecraftServer server, double x, double y, double z, float yaw, float pitch) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.teleportTo(player.serverLevel(), ride.getX() + x, ride.getY() + y, ride.getZ() + z, yaw, pitch);
        });
    }

    /**
     * The contraption yard: a Mechanical Piston two poles long pushing a stone block with a Shackle Hook under it, a cow hung
     * on the hook (its motor still until the picture); and to the south a ship, a deck of stone lying on the ground with a
     * gallows at its back, a cow hung from the gallows' hook and one lying on the deck.
     */
    private static void rideBuild(ServerLevel level, ServerPlayer player) {
        BlockPos y = ride;
        for (int x = 0; x <= 1; x++) {
            level.setBlockAndUpdate(y.offset(x, 4, 0), AllBlocks.PISTON_EXTENSION_POLE.getDefaultState()
                    .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.EAST));
        }
        level.setBlockAndUpdate(y.offset(2, 4, 0), AllBlocks.MECHANICAL_PISTON.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST)
                .setValue(com.simibubi.create.content.kinetics.base.DirectionalAxisKineticBlock.AXIS_ALONG_FIRST_COORDINATE, true));
        level.setBlockAndUpdate(y.offset(2, 3, 0), AllBlocks.CREATIVE_MOTOR.getDefaultState()
                .setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.UP));
        if (level.getBlockEntity(y.offset(2, 3, 0)) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(0);
        }
        level.setBlockAndUpdate(y.offset(3, 4, 0), Blocks.STONE.defaultBlockState());
        BlockPos hook = y.offset(3, 3, 0);
        level.setBlockAndUpdate(hook, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        CarcassSavedData.Carcass onPiston = carcass(level, EntityType.COW, hook.below(3), false);
        if (onPiston != null) {
            hang(level, player, onPiston, hook);
        }
        // the ship: a deck on the ground, a gallows at its back (north) with a hook under its beam
        int z0 = 12;
        List<BlockPos> blocks = new java.util.ArrayList<>();
        for (int x = 2; x <= 7; x++) {
            for (int z = z0; z <= z0 + 5; z++) {
                blocks.add(y.offset(x, 0, z));
            }
        }
        for (int h = 1; h <= 3; h++) {
            blocks.add(y.offset(2, h, z0));
            blocks.add(y.offset(7, h, z0));
        }
        for (int x = 2; x <= 7; x++) {
            blocks.add(y.offset(x, 4, z0));
        }
        for (BlockPos at : blocks) {
            level.setBlockAndUpdate(at, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState());
        }
        BlockPos shipHook = y.offset(4, 3, z0);
        level.setBlockAndUpdate(shipHook, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
        blocks.add(shipHook);
        BlockPos min = y.offset(2, 0, z0);
        BlockPos max = y.offset(7, 4, z0 + 5);
        rideShip = dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(level, blocks.get(0), blocks,
                new dev.ryanhcode.sable.companion.math.BoundingBox3i(min, max));
        if (rideShip == null) {
            BloodAndBones.LOGGER.warn("[showcase] the contraption yard's ship was not made");
            return;
        }
        CarcassAssembler.bindColliders(level, rideShip);
        // the free part of the deck, beside the lying cow, where the cow dropped as it flies lands
        rideDrop = rideShip.logicalPose().transformPositionInverse(new org.joml.Vector3d(y.getX() + 3.5, y.getY() + 1.0, y.getZ() + z0 + 3.5), new org.joml.Vector3d());
        carcass(level, EntityType.COW, y.offset(5, 2, z0 + 3), false);
        CarcassSavedData.Carcass onShip = carcass(level, EntityType.COW, y.offset(4, 2, z0 + 1), false);
        ShackleHookBlockEntity shackle = null;
        for (var holder : rideShip.getPlot().getLoadedChunks()) {
            for (var be : holder.getChunk().getBlockEntities().values()) {
                if (be instanceof ShackleHookBlockEntity found) {
                    shackle = found;
                }
            }
        }
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (onShip != null && shackle != null && container.getSubLevel(onShip.bones.get("right_hind_leg")) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel leg) {
            player.teleportTo(level, shipHook.getX() + 0.5, shipHook.getY() - 2.0, shipHook.getZ() + 3.5, 180.0F, 0.0F);
            if (CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null)) {
                shackle.toggle(level, player);
            }
        }
    }

    // ---------------------------------------------------------------- the groups yard (docs/ARCHITECTURE-PROPOSAL.md 15.31)

    private static long groupsAt = -1;
    private static BlockPos groupsYard;
    private static int groupsStep;
    private static final java.util.List<CarcassSavedData.Carcass> GENERIC_SHOWN = new java.util.ArrayList<>();
    private static CarcassSavedData.Carcass floater;
    private static CarcassSavedData.Carcass sinker;

    /**
     * Mobs with no rig file of their own, photographed: a polar bear, a zombie villager, a cave spider and a bat whose rig
     * files are taken away for the rest of the run (none of them is in any other shot), a tropical fish (which has none),
     * and a baby wandering trader (whose rig has no baby shape), each built from its archetype's generic body at its size
     * and wearing its own model's parts and skin ({@code groups_0}); then a glass tank of water with a chicken floating at
     * the top and a cow sunk to the bottom, as their weight classes say ({@code groups_1}); then a husk and a drowned, their
     * rig files taken away too, the same generic biped at the same hitbox as the zombie villager drawn before them, each in
     * its own model's parts and skin, not the first one's ({@code groups_2}).
     */
    private static void groups(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        long now = server.overworld().getGameTime();
        if (groupsAt < 0) {
            groupsAt = now;
            // south of the contraption yard, clear of its ship's deck and the way it flies
            groupsYard = origin.offset(-60, 0, 80);
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(player.serverLevel(), groupsYard.getX() + 0.5, groupsYard.getY(), groupsYard.getZ() - 4.5, 0.0F, 20.0F);
            });
            return;
        }
        long age = now - groupsAt;
        if (groupsStep == 0 && age >= 10) {
            groupsStep = 1;
            server.execute(() -> groupsBuild(server.overworld()));
        } else if (groupsStep == 1 && age >= 40 && tankAt != null) {
            server.execute(() -> fillTank(server.overworld()));
            groupsView(server, 6.0, 2.5, -2.5, 0.0F, 30.0F);
        } else if (groupsStep == 1 && age < 150) {
            // the row, from in front and a little above
            groupsView(server, 6.0, 2.5, -2.5, 0.0F, 30.0F);
        } else if (groupsStep == 1) {
            groupsStep = 2;
            Screenshot.grab(mc.gameDirectory, PREFIX + "groups_0.png", mc.getMainRenderTarget(), message -> {
            });
            server.execute(() -> {
                ServerLevel level = server.overworld();
                for (CarcassSavedData.Carcass carcass : GENERIC_SHOWN) {
                    var rig = com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass).orElse(null);
                    BloodAndBones.LOGGER.info("[showcase] generic {}{}: {} bodies {}, weight {}, at {}", carcass.entity, carcass.baby ? " (baby)" : "",
                            rig != null && rig.fitted() ? "generic" : "own rig", carcass.bones.keySet(), rig == null ? 0 : rig.weight(),
                            CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone));
                }
            });
        } else if (groupsStep == 2 && age < 330) {
            // the tank, from above its north wall, looking down through the water
            groupsView(server, 6.5, 6.0, 8.5, 0.0F, 55.0F);
        } else if (groupsStep == 2) {
            groupsStep = 3;
            Screenshot.grab(mc.gameDirectory, PREFIX + "groups_1.png", mc.getMainRenderTarget(), message -> {
            });
            server.execute(() -> {
                ServerLevel level = server.overworld();
                BloodAndBones.LOGGER.info("[showcase] tank: chicken's body at {}, cow's at {} (water from {} to {})",
                        floater == null ? null : CarcassAssembler.boneWorldPosition(level, floater, floater.rootBone),
                        sinker == null ? null : CarcassAssembler.boneWorldPosition(level, sinker, sinker.rootBone), groupsYard.getY(), groupsYard.getY() + 4);
            });
        } else if (groupsStep == 3 && age < 400) {
            // the husk and the drowned, close to, from in front and above
            groupsView(server, -3.5, 2.0, -0.5, 0.0F, 40.0F);
        } else if (groupsStep == 3) {
            groupsStep = 4;
            Screenshot.grab(mc.gameDirectory, PREFIX + "groups_2.png", mc.getMainRenderTarget(), message -> {
            });
            server.execute(() -> {
                GENERIC_SHOWN.clear();
                ServerPlayer player = server.getPlayerList().getPlayers().get(0);
                player.teleportTo(server.overworld(), origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5, 0.0F, 0.0F);
            });
            mc.options.hideGui = false;
            stage = 4;
            ticks = 0;
        }
    }

    private static void groupsView(MinecraftServer server, double x, double y, double z, float yaw, float pitch) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.teleportTo(player.serverLevel(), groupsYard.getX() + x, groupsYard.getY() + y, groupsYard.getZ() + z, yaw, pitch);
        });
    }

    private static void groupsBuild(ServerLevel level) {
        BlockPos y = groupsYard;
        int forever = Integer.MAX_VALUE;
        for (EntityType<?> type : List.of(EntityType.POLAR_BEAR, EntityType.ZOMBIE_VILLAGER, EntityType.CAVE_SPIDER, EntityType.BAT, EntityType.HUSK,
                EntityType.DROWNED)) {
            com.avicagan.bloodandbones.carcass.rig.RigManager.hideForTest(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type), forever);
        }
        // the row, facing the camera (north), a stride apart: none has a rig file of its own now
        List<EntityType<?>> row = List.of(EntityType.POLAR_BEAR, EntityType.ZOMBIE_VILLAGER, EntityType.CAVE_SPIDER, EntityType.TROPICAL_FISH, EntityType.BAT);
        for (int i = 0; i < row.size(); i++) {
            // knocked down as a kill knocks them
            CarcassSavedData.Carcass carcass = carcass(level, row.get(i), y.offset(i * 2 + 1, 0, 2), true, false, 180.0F);
            if (carcass != null) {
                GENERIC_SHOWN.add(carcass);
            }
        }
        CarcassSavedData.Carcass baby = carcass(level, EntityType.WANDERING_TRADER, y.offset(11, 0, 2), true, true, 180.0F);
        if (baby != null) {
            GENERIC_SHOWN.add(baby);
        }
        // off to the west, for the last shot: two more of the zombie villager's archetype and hitbox, lying face up
        for (EntityType<?> twin : List.of(EntityType.HUSK, EntityType.DROWNED)) {
            CarcassSavedData.Carcass carcass = carcass(level, twin, y.offset(twin == EntityType.HUSK ? -5 : -3, 0, 2), false, false, 180.0F);
            if (carcass != null) {
                GENERIC_SHOWN.add(carcass);
            }
        }
        // the tank, behind the row: glass walls round water four deep, seven across
        BlockPos tank = y.offset(2, 0, 10);
        for (int dx = 0; dx <= 8; dx++) {
            for (int dz = 0; dz <= 8; dz++) {
                for (int dy = 0; dy <= 4; dy++) {
                    boolean wall = dx == 0 || dx == 8 || dz == 0 || dz == 8;
                    if (wall) {
                        level.setBlockAndUpdate(tank.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
                    } else if (dy < 4) {
                        level.setBlockAndUpdate(tank.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
                    }
                }
            }
        }
        floater = null;
        sinker = null;
        // built a moment later, once the tank's water has colliders: the chicken on the floor, the cow with its back at the top
        tankAt = tank;
    }

    /** Where the tank's water starts, once built; the two carcasses go in a moment later. */
    private static volatile BlockPos tankAt;

    private static void fillTank(ServerLevel level) {
        BlockPos tank = tankAt;
        if (tank == null) {
            return;
        }
        tankAt = null;
        floater = carcass(level, EntityType.CHICKEN, tank.offset(3, 0, 4), false, false, 90.0F);
        sinker = carcass(level, EntityType.COW, tank.offset(5, 2, 4), false, false, 90.0F);
    }

    /**
     * A killing blow to a cow's face, from a stand-in killer two blocks in front of it (it faces south) at a player's eye
     * height, swinging at the middle of its head: where the look meets the head is where the blow lands, as a kill finds it.
     */
    private static void faceBlow(ServerLevel level, CarcassSavedData.Carcass carcass) {
        var rig = com.avicagan.bloodandbones.carcass.rig.RigManager.forCarcass(carcass).orElse(null);
        org.joml.Vector3d head = CarcassAssembler.boneWorldPosition(level, carcass, "head");
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (rig == null || head == null || !(container.getSubLevel(carcass.bones.get("head")) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body)) {
            return;
        }
        net.minecraft.world.phys.Vec3 at = new net.minecraft.world.phys.Vec3(head.x, head.y, head.z);
        net.minecraft.world.phys.Vec3 eye = at.add(0.0, 0.6, 2.0);
        net.minecraft.world.phys.Vec3 look = at.subtract(eye).normalize();
        com.avicagan.bloodandbones.carcass.CarcassAim.Hit hit = com.avicagan.bloodandbones.carcass.CarcassAim.first(level, carcass, rig, eye, look, 8.0);
        if (hit != null && hit.bone().equals("head")) {
            carcass.hitBone = "head";
            carcass.hitPoint = body.logicalPose().transformPositionInverse(hit.point(), new org.joml.Vector3d());
        }
        CarcassAssembler.blow(level, carcass, look);
    }

    /** Hold the player (the camera) at a spot of the physics yard, looking one way. */
    private static void yardView(MinecraftServer server, double x, double y, double z, float yaw, float pitch) {
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().get(0);
            player.teleportTo(player.serverLevel(), yard.getX() + x, yard.getY() + y, yard.getZ() + z, yaw, pitch);
        });
    }

    /** Which way a carcass's torso points its head end (a cow's part-local -y), in the world; null if it is gone. */
    @org.jetbrains.annotations.Nullable
    private static org.joml.Vector3d headEnd(ServerLevel level, @org.jetbrains.annotations.Nullable CarcassSavedData.Carcass carcass) {
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (carcass == null || container == null || !(container.getSubLevel(carcass.bones.get(carcass.rootBone)) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel torso)) {
            return null;
        }
        return torso.logicalPose().orientation().transform(new org.joml.Vector3d(0, -1, 0));
    }

    /** The physics yard: a cow to drag, two to strike and three on hooks under a beam. */
    private static void yardBuild(ServerLevel level, ServerPlayer player) {
        BlockPos y = yard;
        // the cow to drag, facing east: dragged east, it has to come round to lead with its rear
        dragged = carcass(level, EntityType.COW, y, false, false, -90.0F);
        // a stone beam with three hooks under it, a cow hung on each
        for (int dx = 0; dx <= 8; dx++) {
            level.setBlockAndUpdate(y.offset(dx, 5, 20), Blocks.STONE.defaultBlockState());
        }
        for (int i = 0; i < 3; i++) {
            BlockPos hook = y.offset(1 + 3 * i, 4, 20);
            level.setBlockAndUpdate(hook, BBBlocks.SHACKLE_HOOK.getDefaultState().setValue(ShackleHookBlock.FACING, Direction.UP));
            hungCows[i] = carcass(level, EntityType.COW, hook.below(3), false);
            if (hungCows[i] != null) {
                hang(level, player, hungCows[i], hook);
            }
        }
        // back to the cow to drag: stand beside its rear and hook its right hind leg
        var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
        if (dragged != null && container.getSubLevel(dragged.bones.get("right_hind_leg")) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel leg) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
            player.teleportTo(level, y.getX() + 0.5, y.getY(), y.getZ() + 1.5, -90.0F, 20.0F);
            boolean started = CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null);
            BloodAndBones.LOGGER.info("[showcase] physics yard: drag by the hind leg started: {}", started);
        }
    }

    /** A piece of a vanilla mob, for a minion built here. */
    private static com.avicagan.bloodandbones.minion.PieceRef piece(String mob, String bone) {
        return new com.avicagan.bloodandbones.minion.PieceRef(net.minecraft.resources.ResourceLocation.withDefaultNamespace(mob), bone,
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/" + mob + "/" + mob + ".png"), List.of(), 1.0F, false,
                java.util.Map.of(), false);
    }

    /**
     * Each shown minion's best tasks and what it cannot do, worked out as a task screen will (docs/NEXT.md 1.2), in the chat:
     * the fitness in a running world, from the server's data, by the tasks' own names (bloodless ones in the bloodless run).
     */
    private static void fitness(ServerPlayer player) {
        var store = com.avicagan.bloodandbones.parts.PartsData.SERVER;
        for (var shown : SHOWN) {
            var build = shown.getValue();
            var rows = new java.util.ArrayList<>(com.avicagan.bloodandbones.minion.MinionFitness.rows(store, build,
                    com.avicagan.bloodandbones.minion.MinionStats.of(store, build), com.avicagan.bloodandbones.minion.MinionFitness.Context.NONE
                            .holding(shown.getKey().contains("bow") ? new ItemStack(net.minecraft.world.item.Items.BOW) : ItemStack.EMPTY)));
            rows.removeIf(r -> !r.task().rated());
            rows.sort(java.util.Comparator.comparingDouble(r -> r.can() ? -r.fitness() : 1.0));
            net.minecraft.network.chat.MutableComponent line = net.minecraft.network.chat.Component.literal(shown.getKey() + ": ");
            var best = rows.stream().filter(com.avicagan.bloodandbones.minion.MinionFitness.Row::can).limit(4).toList();
            for (int i = 0; i < best.size(); i++) {
                line.append(net.minecraft.network.chat.Component.translatable(best.get(i).task().nameKey()))
                        .append(" " + Math.round(best.get(i).fitness() * 100.0F) + "%" + (i < best.size() - 1 ? ", " : ""));
            }
            var cannot = rows.stream().filter(r -> !r.can()).toList();
            if (!cannot.isEmpty()) {
                line.append("; cannot: ");
                for (int i = 0; i < cannot.size(); i++) {
                    line.append(net.minecraft.network.chat.Component.translatable(cannot.get(i).task().nameKey())).append(i < cannot.size() - 1 ? ", " : "");
                }
            }
            player.sendSystemMessage(line);
            BloodAndBones.LOGGER.info("[showcase] fitness: {}", line.getString());
        }
    }

    /**
     * The diving shot's water: two blocks of it where the player stands on the ground, walled in glass so it cannot
     * run; {@code fill} false takes it all away again.
     */
    private static BlockPos dive(ServerLevel level, ServerPlayer player, boolean fill) {
        BlockPos feet = fill ? level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, player.blockPosition()) : diveAt;
        if (feet == null) {
            return null;
        }
        for (int dy = 0; dy < 2; dy++) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                level.setBlockAndUpdate(feet.above(dy).relative(side), fill ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
            level.setBlockAndUpdate(feet.above(dy), fill ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        if (fill) {
            player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0.0F, 10.0F);
        }
        return feet;
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
                // the filter slot, set for limbs: it tears the cow's legs off and grinds them, and leaves the rest lying on it
                machine.filtering.setFilter(partFilter(new com.avicagan.bloodandbones.registry.BBItemAttributes.PiecePart("limb")));
            }
            if (machines[i] == BBBlocks.GUILLOTINE) {
                observerClock(level, at);
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

        // row E, the brief's decoration: morgue furniture, a ribcage, bone piles, bloody cladding, gut chain on a conveyor
        int decoZ = o.getZ() + 34;
        decoration(level, o, decoZ, jarPig, roastCow);
        // beside it, carcass pieces riding a belt to a Depot (the brief: off the carcass, they behave as ordinary items)
        belt(level, o, decoZ, jarPig, roastCow);

        // row F, off to the west: the machines bare, their parts turning (docs/BRIEF-AUDIT.md package 15), and the tables' filters
        movingParts(level, new BlockPos(o.getX() - 34, ground, o.getZ() + 12), roastCow);
        // beside them, a rig set to a heart that took a cow's heart and left the rest in, and two butcher minions each holding
        // a Cleaver and, beside it, a cow's hind leg as its sample (docs/BRIEF-AUDIT.md packages 10 and 5)
        organFilterAndButchers(level, player, new BlockPos(o.getX() - 30, o.getY(), o.getZ() + 15), roastCow);
        // a whole cow on a second spit, most of the way cooked
        BlockPos wholeFire = new BlockPos(o.getX() - 11, o.getY(), z);
        level.setBlockAndUpdate(wholeFire, Blocks.CAMPFIRE.defaultBlockState());
        level.setBlockAndUpdate(wholeFire.above(), BBBlocks.SPIT_ROAST.getDefaultState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Direction.Axis.X));
        level.setBlockAndUpdate(wholeFire.above().west(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        if (level.getBlockEntity(wholeFire.above().west()) instanceof CreativeMotorBlockEntity spitMotor) {
            spitMotor.generatedSpeed.setValue(8);
        }
        CarcassSavedData.Carcass wholeCow = carcass(level, EntityType.COW, new BlockPos(o.getX() - 12, o.getY(), z + 12), false);
        if (wholeCow != null && level.getBlockEntity(wholeFire.above()) instanceof SpitRoastBlockEntity wholeSpit) {
            wholeSpit.skewer(level, wholeCow);
            wholeSpit.progress = wholeSpit.cookTime() * 0.75F;
        }
        // and a third spit that a cow went on and came off raw: it is set down whole again, not in six heaped pieces
        BlockPos rawSpit = new BlockPos(o.getX() - 15, o.getY(), z);
        emptySpit = rawSpit;
        // an empty Butcher's Table out of the way, for the client's half of a click
        emptyTable = new BlockPos(o.getX() - 19, o.getY(), z);
        level.setBlockAndUpdate(emptyTable, BBBlocks.BUTCHER_TABLE.getDefaultState());
        level.setBlockAndUpdate(rawSpit, BBBlocks.SPIT_ROAST.getDefaultState().setValue(HorizontalAxisKineticBlock.HORIZONTAL_AXIS, Direction.Axis.X));
        CarcassSavedData.Carcass rawCow = carcass(level, EntityType.COW, new BlockPos(o.getX() - 16, o.getY(), z + 12), false);
        if (rawCow != null && level.getBlockEntity(rawSpit) instanceof SpitRoastBlockEntity raw && raw.skewer(level, rawCow)) {
            raw.takeOff(player);
            CarcassSavedData.get(level).all().stream().filter(c -> c.entity.equals(rawCow.entity)
                            && CarcassAssembler.boneWorldPosition(level, c, c.rootBone) != null
                            && CarcassAssembler.boneWorldPosition(level, c, c.rootBone).distance(rawSpit.getX() + 0.5, rawSpit.getY() + 1.0, rawSpit.getZ() + 0.5) < 3.0)
                    .forEach(c -> BloodAndBones.LOGGER.info("[showcase] set down off the spit: {} bones, {} joints", c.bones.size(), c.joints.size()));
        }

        // row G, north of the decoration: the materials and the decoration leftovers (docs/BRIEF-AUDIT.md packages 14 and 17)
        int materialsZ = o.getZ() + 52;
        materials(level, o, materialsZ, jarPig, roastCow);

        double eye = o.getY();
        // the bits that fly off the bloody blocks when broken, thrown in mid-air just before the shot
        View debris = new View(o.getX() + 0.5, eye, decoZ + 8.0, 0, 0);
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
                new View(o.getX() - 1.0, eye + 1.0, wallZ - 4.0, 0, 8),
                // steel tables in a run, turning a corner
                new View(o.getX() - 4.5, eye + 1.0, decoZ - 3.1, 0, 25),
                // the steel rack and a table on its own
                new View(o.getX() - 1.5, eye + 0.5, decoZ - 2.2, 0, 20),
                // that table's front under the crosshair: its outline should hug the drawn top
                new View(o.getX() - 0.5, eye, decoZ - 1.5, 0, 28),
                // inside the ribcage
                new View(o.getX() + 3.0, eye + 0.2, decoZ - 3.0, 0, 5),
                // bone piles and the bloody brass and copper casings
                new View(o.getX() + 10.0, eye + 1.2, decoZ - 4.0, 0, 18),
                // carcass pieces on a belt and on the Depots at its ends
                new View(o.getX() + 17.5, eye + 2.2, decoZ - 2.5, 0, 38),
                debris,
                // gut chains riding the chain conveyor
                new View(o.getX() - 2.5, eye + 1.5, decoZ + 1.0, 0, -12),
                // the bare machines, parts turning: Mangler, Deglover, Beheader, and two Guillotines, one armed, one fallen
                new View(o.getX() - 29.5, eye + 2.2, o.getZ() + 8.8, 0, 38),
                // the Mangler's grinders and the Deglover's rollers, close
                new View(o.getX() - 32.5, eye + 1.2, o.getZ() + 10.6, 0, 50),
                // the Beheader's saw and the Guillotines' blades, close
                new View(o.getX() - 27.5, eye + 1.2, o.getZ() + 10.0, 0, 30),
                // the Butcher's Table and the Surgical Rig with their filters set
                new View(o.getX() - 32.5, eye + 1.3, o.getZ() + 12.7, 0, 35),
                // the two Guillotines from the south, level with their blades: one up, one fallen and winding back
                new View(o.getX() - 26.5, eye, o.getZ() + 14.9, 180, 18),
                // a whole cow roasting on a spit
                new View(o.getX() - 10.5, eye + 1.0, o.getZ() + 16.6, 0, 18),
                // a cow taken off a spit raw, set down whole
                new View(o.getX() - 14.5, eye + 1.2, o.getZ() + 16.2, 0, 22));
        views = new java.util.ArrayList<>(views);
        // these four from the ground, where the player lands (it does not fly), looking level or up
        views.addAll(List.of(
                // the soul blood line: a Basin Lid on a basin of blood, fan through soul fire, mixer over a superheated basin
                new View(o.getX() - 3.5, eye, materialsZ - 8.0, 0, 0),
                // the Blood Diamond on its depot under a spout, and a finished one beside it
                new View(o.getX() + 6.0, eye, materialsZ - 3.5, 0, -8),
                // the stained palette and the train casing, bloody beside Create's own, hooks hung with every kind of part
                new View(o.getX() + 0.5, eye, materialsZ + 4.5, 0, 5),
                // the hooks close up
                new View(o.getX() - 1.5, eye, materialsZ + 6.8, 0, -18)));
        // the rig set to a heart, its heart out on the top and the rest left in, and the two butchers with their samples
        views.add(new View(o.getX() - 28.5, eye + 1.2, o.getZ() + 12.4, 0, 28));
        // the view with the flying bits, wherever it falls in the list
        debrisView = views.indexOf(debris);
        debrisAt = new BlockPos(o.getX(), o.getY() + 1, decoZ + 13);
        BloodAndBones.LOGGER.info("[showcase] built at {}", o);
    }

    /**
     * Client: what the client decides on a click with a Cleaver at an empty Butcher's Table and the Meat Hook at an empty
     * spit, with a piece in the other hand and without. With one, the click should pass, so the other hand has its turn;
     * without, it should be taken (the arm swings: only the server can see a piece lying there to chop).
     */
    private static void otherHand(Minecraft mc) {
        if (mc.level == null || mc.player == null || emptyTable == null || emptySpit == null) {
            return;
        }
        ItemStack before = mc.player.getOffhandItem();
        ItemStack piece = new ItemStack(BBItems.CARCASS_PIECE.get());
        piece.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow"), "left_hind_leg",
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of(), 1.0F,
                false, java.util.Map.of(), 0.0F, 0.0F, 0.0F, false));
        List<String> results = new java.util.ArrayList<>();
        for (BlockPos at : List.of(emptyTable, emptySpit)) {
            ItemStack tool = new ItemStack(at == emptyTable ? BBItems.CLEAVER.get() : BBItems.MEAT_HOOK.get());
            net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(at), Direction.UP, at, false);
            for (ItemStack other : List.of(piece, ItemStack.EMPTY)) {
                mc.player.setItemInHand(InteractionHand.OFF_HAND, other.copy());
                results.add(mc.level.getBlockState(at).useItemOn(tool, mc.level, mc.player, InteractionHand.MAIN_HAND, hit).name());
            }
        }
        mc.player.setItemInHand(InteractionHand.OFF_HAND, before);
        BloodAndBones.LOGGER.info("[showcase] client clicks: Cleaver at an empty table, a piece in the other hand {}, none {}; "
                + "Meat Hook at an empty spit, a piece {}, none {}", results.toArray());
    }

    /**
     * Client: bits flying off each bloody block as if it were broken, in a row in mid-air: bone pile, rib,
     * brass, copper and andesite casing, gut chain; above them, what comes off a carcass (landing, rolling, struck)
     * and off a blood stain scuffed away. In bloodless mode they should come off clean: steel, and something damp.
     */
    private static void debris(Minecraft mc) {
        net.minecraft.world.level.block.state.BlockState[] states = {BBBlocks.BONE_PILE.getDefaultState().setValue(com.avicagan.bloodandbones.decoration.BonePileBlock.LAYERS, 8),
                BBBlocks.RIBCAGE_ARCH.getDefaultState(), BBBlocks.BLOODY_BRASS_CASING.getDefaultState(), BBBlocks.BLOODY_COPPER_CASING.getDefaultState(),
                BBBlocks.BLOODY_CASING.getDefaultState(), BBBlocks.GUT_CHAIN.getDefaultState()};
        for (int i = 0; i < states.length; i++) {
            mc.particleEngine.destroy(debrisAt.offset(i * 2 - 5, 0, 0), states[i]);
        }
        mc.particleEngine.destroy(debrisAt.offset(3, 3, 0), BBBlocks.CARCASS_PART.getDefaultState());
        mc.particleEngine.destroy(debrisAt.offset(-3, 3, 0), BBBlocks.BLOOD_STAIN.getDefaultState());
    }

    /** The brief's decoration, laid out along one row from x - 7 to x + 12. */
    private static void decoration(ServerLevel level, BlockPos o, int z, CarcassSavedData.Carcass pig, CarcassSavedData.Carcass cow) {
        int y = o.getY();
        // a run of steel tables turning a corner, and one on its own; things laid on them
        List<BlockPos> tables = List.of(new BlockPos(o.getX() - 7, y, z), new BlockPos(o.getX() - 6, y, z), new BlockPos(o.getX() - 5, y, z),
                new BlockPos(o.getX() - 5, y, z + 1), new BlockPos(o.getX() - 1, y, z));
        for (BlockPos at : tables) {
            level.setBlockAndUpdate(at, BBBlocks.STEEL_TABLE.getDefaultState());
        }
        for (BlockPos at : tables) {
            level.setBlockAndUpdate(at, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(at), level, at));
        }
        ItemStack[] onTables = {pig == null ? ItemStack.EMPTY : CarcassPieceItem.of(pig, "head"), new ItemStack(BBItems.HEART.get()),
                cow == null ? ItemStack.EMPTY : CarcassPieceItem.of(cow, "body"), new ItemStack(net.minecraft.world.item.Items.BONE),
                new ItemStack(net.minecraft.world.item.Items.SKELETON_SKULL)};
        for (int i = 0; i < tables.size(); i++) {
            if (level.getBlockEntity(tables.get(i)) instanceof com.avicagan.bloodandbones.decoration.SteelTableBlockEntity table && !onTables[i].isEmpty()) {
                table.put(onTables[i]);
            }
        }
        // a rack beside them, a part on each place
        BlockPos rackPos = new BlockPos(o.getX() - 3, y, z);
        level.setBlockAndUpdate(rackPos, BBBlocks.STEEL_RACK.getDefaultState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(rackPos) instanceof com.avicagan.bloodandbones.decoration.SteelRackBlockEntity rack) {
            ItemStack[] parts = {pig == null ? ItemStack.EMPTY : CarcassPieceItem.of(pig, "right_front_leg"), new ItemStack(BBItems.OFFAL.get()),
                    new ItemStack(BBItems.HEART.get()), cow == null ? ItemStack.EMPTY : CarcassPieceItem.of(cow, "head")};
            for (int slot = 0; slot < parts.length; slot++) {
                rack.put(slot, parts[slot]);
            }
        }
        // a ribcage: four arches in a row, two stacks of three facing each other with two level ribs between
        List<BlockPos> ribs = new java.util.ArrayList<>();
        for (int dz = 0; dz < 4; dz++) {
            for (int dy = 0; dy < 3; dy++) {
                place(level, ribs, new BlockPos(o.getX() + 1, y + dy, z + dz), Direction.EAST);
                place(level, ribs, new BlockPos(o.getX() + 4, y + dy, z + dz), Direction.WEST);
            }
            place(level, ribs, new BlockPos(o.getX() + 2, y + 2, z + dz), Direction.EAST);
            place(level, ribs, new BlockPos(o.getX() + 3, y + 2, z + dz), Direction.WEST);
        }
        for (BlockPos at : ribs) {
            level.setBlockAndUpdate(at, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(at), level, at));
        }
        // bones heaped on its floor, and piles of each height beside it, one full with more on top
        pile(level, new BlockPos(o.getX() + 2, y, z + 1), 2);
        pile(level, new BlockPos(o.getX() + 3, y, z + 2), 1);
        int[] heights = {1, 3, 5, 8};
        for (int i = 0; i < heights.length; i++) {
            pile(level, new BlockPos(o.getX() + 7 + i, y, z), heights[i]);
        }
        pile(level, new BlockPos(o.getX() + 10, y + 1, z), 3);
        // bloody brass and copper casing, each beside Create's own
        for (int dy = 0; dy < 2; dy++) {
            for (int dx = 0; dx < 6; dx++) {
                BlockEntry<?> casing = switch (dx) {
                    case 0, 1 -> BBBlocks.BLOODY_BRASS_CASING;
                    case 2 -> AllBlocks.BRASS_CASING;
                    case 3, 4 -> BBBlocks.BLOODY_COPPER_CASING;
                    default -> AllBlocks.COPPER_CASING;
                };
                level.setBlockAndUpdate(new BlockPos(o.getX() + 7 + dx, y + dy, z + 3), casing.getDefaultState());
            }
        }
        // a chain conveyor overhead, turning, with gut chains of two, three and four links riding it
        BlockPos a = new BlockPos(o.getX() - 7, y + 4, z + 6);
        BlockPos b = new BlockPos(o.getX() + 1, y + 4, z + 6);
        level.setBlockAndUpdate(a, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        level.setBlockAndUpdate(b, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        level.setBlockAndUpdate(a.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.DOWN));
        if (level.getBlockEntity(a) instanceof com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity aBe
                && level.getBlockEntity(b) instanceof com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity bBe) {
            // a conveyor just placed has not worked out its strands yet
            aBe.prepareStats();
            bBe.prepareStats();
            bBe.addConnectionTo(a);
            aBe.addConnectionTo(b);
            if (level.getBlockEntity(a.above()) instanceof CreativeMotorBlockEntity motor) {
                motor.generatedSpeed.setValue(24);
            }
            aBe.prepareStats();
            float[] along = {1.5F, 4.0F, 6.5F};
            int[] links = {2, 3, 4};
            for (int i = 0; i < along.length; i++) {
                var cursor = new com.avicagan.bloodandbones.carcass.trolley.ChainCursor(a, b.subtract(a), along[i], aBe.reversed);
                level.addFreshEntity(com.avicagan.bloodandbones.decoration.HangingGutChainEntity.create(level, cursor, links[i]));
            }
        }
    }

    /**
     * A slow belt east of the decoration row with a Depot at each end, and three carcass pieces dropped on it: by the
     * time it is photographed they have ridden it to whichever end it runs to, one on the Depot, the rest waiting behind.
     */
    private static void belt(ServerLevel level, BlockPos o, int z, CarcassSavedData.Carcass pig, CarcassSavedData.Carcass cow) {
        BlockPos start = new BlockPos(o.getX() + 15, o.getY(), z + 1);
        BlockPos end = start.east(5);
        for (BlockPos pulley : new BlockPos[]{start, end}) {
            level.setBlockAndUpdate(pulley, AllBlocks.SHAFT.getDefaultState().setValue(com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock.AXIS, Direction.Axis.Z));
        }
        com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem.createBelts(level, start, end);
        level.setBlockAndUpdate(start.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(start.north()) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(16);
        }
        level.setBlockAndUpdate(start.west(), AllBlocks.DEPOT.getDefaultState());
        level.setBlockAndUpdate(end.east(), AllBlocks.DEPOT.getDefaultState());
        ItemStack[] pieces = {pig == null ? ItemStack.EMPTY : CarcassPieceItem.of(pig, "left_front_leg"),
                cow == null ? ItemStack.EMPTY : CarcassPieceItem.of(cow, "head"), cow == null ? ItemStack.EMPTY : CarcassPieceItem.of(cow, "left_front_leg")};
        for (int i = 0; i < pieces.length; i++) {
            if (!pieces[i].isEmpty()) {
                net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, start.getX() + 1.5 + i * 1.5,
                        start.getY() + 0.8, start.getZ() + 0.5, pieces[i]);
                item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                level.addFreshEntity(item);
            }
        }
    }

    /**
     * A pair of observers watching each other in the floor east of a machine: they pulse on and off for ever, and the one
     * against the machine gives it a rising redstone edge every few ticks, so a Guillotine drops whenever it is wound up.
     */
    private static void observerClock(ServerLevel level, BlockPos machine) {
        BlockPos near = machine.east();
        BlockPos far = near.east();
        level.setBlockAndUpdate(far, Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.WEST));
        level.setBlockAndUpdate(near, Blocks.OBSERVER.defaultBlockState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.EAST));
    }

    /**
     * The four machines with nothing on them, on motors, so their parts are seen turning: the Mangler's grinders, the
     * Deglover's rollers, the Beheader's saw; a Guillotine wound up and armed, and one stopped with its blade fallen and
     * part-wound. Beside them a Butcher's Table and a Surgery Table with its Surgical Rig, each with a filter set.
     */
    private static void movingParts(ServerLevel level, BlockPos start, CarcassSavedData.Carcass cow) {
        BlockEntry<?>[] machines = {BBBlocks.MANGLER, BBBlocks.DEGLOVER, BBBlocks.BEHEADER, BBBlocks.GUILLOTINE, BBBlocks.GUILLOTINE};
        for (int i = 0; i < machines.length; i++) {
            BlockPos at = start.east(i * 2);
            level.setBlockAndUpdate(at, machines[i].getDefaultState());
            if (i < 4) {
                level.setBlockAndUpdate(at.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
                if (level.getBlockEntity(at.below()) instanceof CreativeMotorBlockEntity motor) {
                    motor.generatedSpeed.setValue(i == 1 ? 24 : 48);
                }
            } else if (level.getBlockEntity(at) instanceof com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity stopped) {
                // no shaft: a blade that fell and was a third of the way back up when the shaft stopped
                stopped.wind = 0.35F;
                stopped.sendData();
            }
        }
        // the tables, a little further on, filters set: the table for heads, the rig for bodies
        BlockPos table = start.above().south(3);
        level.setBlockAndUpdate(table, BBBlocks.BUTCHER_TABLE.getDefaultState());
        if (level.getBlockEntity(table) instanceof com.avicagan.bloodandbones.cooking.ButcherTableBlockEntity butcher) {
            butcher.filtering.setFilter(partFilter(new com.avicagan.bloodandbones.registry.BBItemAttributes.PiecePart("head")));
            if (cow != null) {
                butcher.put(CarcassPieceItem.of(cow, "head"));
            }
        }
        BlockPos rig = table.east(2);
        level.setBlockAndUpdate(rig, BBBlocks.SURGERY_TABLE.getDefaultState()
                .setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.SURGICAL));
        if (level.getBlockEntity(rig) instanceof com.avicagan.bloodandbones.body.SurgeryTableBlockEntity surgery) {
            surgery.filtering.setFilter(partFilter(new com.avicagan.bloodandbones.registry.BBItemAttributes.PieceSlot("leg.hind")));
            if (cow != null) {
                // a hind leg laid on it and one cut made, as a Deployer makes it: its hide is off, the meat bare
                surgery.put(CarcassPieceItem.of(cow, "right_hind_leg"));
                com.avicagan.bloodandbones.body.SurgicalRig.cut(level, net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level), surgery,
                        new ItemStack(BBItems.CLEAVER.get()));
            }
        }
    }

    /**
     * A Surgical Rig set to a heart (the organ itself in its slot), a cow's body laid on it and cut twice as a Deployer
     * cuts: the first cut takes the heart, the second nothing, the lungs, stomach and rumen left in. Beside it two butcher
     * minions standing still, each with a Cleaver and a cow's hind leg beside it as its sample: a villager, holding them
     * either side of its folded arms, and a zombie with a butcher villager's head, one in each hand.
     */
    private static void organFilterAndButchers(ServerLevel level, ServerPlayer player, BlockPos rig, CarcassSavedData.Carcass cow) {
        level.setBlockAndUpdate(rig, BBBlocks.SURGERY_TABLE.getDefaultState()
                .setValue(com.avicagan.bloodandbones.body.SurgeryTableBlock.ATTACHMENT, com.avicagan.bloodandbones.body.TableAttachment.SURGICAL));
        if (cow != null && level.getBlockEntity(rig) instanceof com.avicagan.bloodandbones.body.SurgeryTableBlockEntity surgery) {
            surgery.filtering.setFilter(new ItemStack(BBItems.HEART.get()));
            surgery.put(CarcassPieceItem.of(cow, "body"));
            var deployer = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
            boolean first = com.avicagan.bloodandbones.body.SurgicalRig.cut(level, deployer, surgery, new ItemStack(BBItems.CLEAVER.get()));
            surgery.nextCut = 0;
            boolean second = com.avicagan.bloodandbones.body.SurgicalRig.cut(level, deployer, surgery, new ItemStack(BBItems.CLEAVER.get()));
            // a Deployer's stand-in keeps what it cuts: lay the heart on the top, where a Deployer's would come out
            for (ItemStack got : deployer.getInventory().items) {
                if (!got.isEmpty()) {
                    net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, rig.getX() + 0.5, rig.getY() + 1.05, rig.getZ() + 0.5, got.copy());
                    item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                    level.addFreshEntity(item);
                }
            }
            deployer.getInventory().clearContent();
            CarcassPieceItem.Piece piece = CarcassPieceItem.piece(surgery.item());
            BloodAndBones.LOGGER.info("[showcase] rig set to a heart: first cut {}, second cut {}, taken {}, still in {}", first, second,
                    piece == null ? "?" : piece.traits().get(com.avicagan.bloodandbones.body.Surgery.ORGANS_TAKEN),
                    piece == null ? "?" : com.avicagan.bloodandbones.body.Surgery.organsLeft(com.avicagan.bloodandbones.parts.PartsData.SERVER, piece));
        }
        java.util.function.BiFunction<String, String, com.avicagan.bloodandbones.minion.PieceRef> villager = (bone, profession) ->
                new com.avicagan.bloodandbones.minion.PieceRef(net.minecraft.resources.ResourceLocation.withDefaultNamespace("villager"), bone,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/villager/villager.png"), List.of(), 1.0F, false,
                        profession == null ? java.util.Map.of() : java.util.Map.of("profession", profession), false);
        java.util.function.Function<String, com.avicagan.bloodandbones.minion.PieceRef> zombie = bone ->
                new com.avicagan.bloodandbones.minion.PieceRef(net.minecraft.resources.ResourceLocation.withDefaultNamespace("zombie"), bone,
                        net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png"), List.of(), 1.0F, false, java.util.Map.of(), false);
        var folded = com.avicagan.bloodandbones.minion.MinionBuild.of(villager.apply("body", null)).with("head", villager.apply("head", "butcher"))
                .with("arms", villager.apply("arms", null)).with("right_leg", villager.apply("right_leg", null)).with("left_leg", villager.apply("left_leg", null));
        var handed = com.avicagan.bloodandbones.minion.MinionBuild.of(zombie.apply("body")).with("head", villager.apply("head", "butcher"))
                .with("right_arm", zombie.apply("right_arm")).with("left_arm", zombie.apply("left_arm"))
                .with("right_leg", zombie.apply("right_leg")).with("left_leg", zombie.apply("left_leg"));
        List<com.avicagan.bloodandbones.minion.MinionBuild> builds = List.of(folded, handed);
        for (int i = 0; i < builds.size(); i++) {
            var minion = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(level);
            BlockPos at = rig.offset(2 + i * 2, 0, 1);
            minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 180.0F, 0.0F);
            minion.setYHeadRot(180.0F);
            minion.setYBodyRot(180.0F);
            minion.setup(player, at, builds.get(i), 1000.0F);
            minion.setNoAi(true);
            minion.setTask(com.avicagan.bloodandbones.minion.MinionTask.BUTCHER);
            minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(BBItems.CLEAVER.get()));
            if (cow != null) {
                minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, CarcassPieceItem.of(cow, "left_hind_leg"));
            }
            if (i == 1) {
                // the zombie's torso burns by day
                minion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.LEATHER_HELMET));
            }
            level.addFreshEntity(minion);
        }
    }

    /**
     * Row G: the soul blood line on Create's own machines (a Basin Lid over a basin of blood, a fan through soul fire onto a
     * depot, a mixer over a superheated basin), the Blood Diamond on a depot under a spout, and a wall of the stained
     * palette and the bloody train casing beside Create's own, with Butcher's Hooks hung with every kind of body part.
     */
    private static void materials(ServerLevel level, BlockPos o, int z, CarcassSavedData.Carcass pig, CarcassSavedData.Carcass cow) {
        int y = o.getY();
        // the Basin Lid (section 8's congealing): a basin of blood under a Diesel Generators lid, setting it; a basin
        // beside it with no lid, holding the congealed blood it set
        BlockPos lidBasin = new BlockPos(o.getX() - 7, y, z);
        level.setBlockAndUpdate(lidBasin, AllBlocks.BASIN.getDefaultState());
        level.setBlockAndUpdate(lidBasin.above(), com.jesz.createdieselgenerators.CDGBlocks.BASIN_LID.getDefaultState()
                .setValue(com.jesz.createdieselgenerators.content.basin_lid.BasinLidBlock.ON_A_BASIN, true));
        BlockPos setBasin = lidBasin.west();
        level.setBlockAndUpdate(setBasin, AllBlocks.BASIN.getDefaultState());
        for (BlockPos at : List.of(lidBasin, setBasin)) {
            if (level.getBlockEntity(at) instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity basin) {
                basin.getTanks().getFirst().getCapability().fill(new net.neoforged.neoforge.fluids.FluidStack(BBFluids.blood(), at.equals(lidBasin) ? 1000 : 500),
                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                if (at.equals(setBasin)) {
                    basin.getOutputInventory().insertItem(0, new ItemStack(BBItems.CONGEALED_BLOOD.get(), 3), false);
                }
            }
        }
        // the fan: blowing east through a soul campfire onto a depot of congealed blood
        BlockPos fan = new BlockPos(o.getX() - 5, y, z);
        motor(level, fan.west(), Direction.EAST, 64);
        level.setBlockAndUpdate(fan, AllBlocks.ENCASED_FAN.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.EAST));
        level.setBlockAndUpdate(fan.east(), Blocks.SOUL_CAMPFIRE.defaultBlockState());
        BlockPos depot = fan.east(2);
        level.setBlockAndUpdate(depot, AllBlocks.DEPOT.getDefaultState());
        if (level.getBlockEntity(depot) != null) {
            var onDepot = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, depot, null);
            if (onDepot != null) {
                onDepot.insertItem(0, new ItemStack(BBItems.SOUL_CLOT.get()), false);
            }
        }
        // the mixer: a superheated burner, a basin with a soul clot melting to soul blood
        BlockPos mixBasin = new BlockPos(o.getX() - 1, y + 1, z);
        burner(level, mixBasin.below(), com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING);
        level.setBlockAndUpdate(mixBasin, AllBlocks.BASIN.getDefaultState());
        level.setBlockAndUpdate(mixBasin.above(2), AllBlocks.MECHANICAL_MIXER.getDefaultState());
        level.setBlockAndUpdate(mixBasin.above(2).east(), AllBlocks.COGWHEEL.getDefaultState());
        motor(level, mixBasin.above(1).east(), Direction.UP, 32);
        if (level.getBlockEntity(mixBasin) instanceof com.simibubi.create.content.processing.basin.BasinBlockEntity basin) {
            basin.getTanks().getSecond().getCapability().fill(new net.neoforged.neoforge.fluids.FluidStack(BBFluids.soulBlood(), 600),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            basin.getInputInventory().insertItem(0, new ItemStack(BBItems.SOUL_CLOT.get(), 2), false);
        }
        // the Blood Diamond: a spout of blood over an Incomplete Blood Diamond on a depot, and a finished one beside it
        BlockPos diamondDepot = new BlockPos(o.getX() + 5, y, z);
        level.setBlockAndUpdate(diamondDepot, AllBlocks.DEPOT.getDefaultState());
        level.setBlockAndUpdate(diamondDepot.above(2), AllBlocks.SPOUT.getDefaultState());
        level.setBlockAndUpdate(diamondDepot.above(3), Blocks.STONE.defaultBlockState());
        var spoutTank = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, diamondDepot.above(2), Direction.UP);
        if (spoutTank != null) {
            spoutTank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("create_enchantment_industry", "experience")), 500),
                    net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        var diamondOn = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, diamondDepot, null);
        if (diamondOn != null) {
            diamondOn.insertItem(0, new ItemStack(BBItems.INCOMPLETE_BLOOD_DIAMOND.get()), false);
        }
        BlockPos doneDepot = diamondDepot.east(2);
        level.setBlockAndUpdate(doneDepot, AllBlocks.DEPOT.getDefaultState());
        var doneOn = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, doneDepot, null);
        if (doneOn != null) {
            doneOn.insertItem(0, new ItemStack(BBItems.BLOOD_DIAMOND.get()), false);
        }

        // the wall: bloody train casing beside Create's, then the stained palette, two high; hooks on its face
        int wallZ = z + 10;
        List<net.minecraft.world.level.block.state.BlockState> column = List.of(BBBlocks.BLOODY_RAILWAY_CASING.getDefaultState(),
                BBBlocks.BLOODY_RAILWAY_CASING.getDefaultState(), AllBlocks.RAILWAY_CASING.getDefaultState(), BBBlocks.BLOODY_CUT_CALCITE.getDefaultState(),
                BBBlocks.BLOODY_POLISHED_CUT_CALCITE.getDefaultState(), BBBlocks.BLOODY_CUT_CALCITE_BRICKS.getDefaultState(),
                BBBlocks.BLOODY_SMALL_CALCITE_BRICKS.getDefaultState());
        for (int i = 0; i < column.size(); i++) {
            for (int dy = 0; dy < 3; dy++) {
                level.setBlockAndUpdate(new BlockPos(o.getX() - 3 + i, y + dy, wallZ), column.get(i));
            }
        }
        // stairs and slabs of the palette along the foot of the wall
        List<BlockEntry<?>> palette = BBBlocks.stainedPalette();
        for (int i = 0; i < 4; i++) {
            level.setBlockAndUpdate(new BlockPos(o.getX() + i, y, wallZ - 1), palette.get(4 + i).getDefaultState()
                    .setValue(net.minecraft.world.level.block.StairBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(new BlockPos(o.getX() + i, y, wallZ - 2), palette.get(8 + i).getDefaultState());
        }
        ItemStack[] hung = {new ItemStack(BBItems.SEVERED_ARM.get()), BBItems.HEART.get().of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow"), false),
                // a piglin's scraps, dripping soul blood onto the ground in front of the train casing
                com.avicagan.bloodandbones.parts.ScrapsItem.of(new com.avicagan.bloodandbones.parts.Source(net.minecraft.resources.ResourceLocation.withDefaultNamespace("piglin"), "torso", false), 1),
                new ItemStack(net.minecraft.world.item.Items.ZOMBIE_HEAD),
                com.avicagan.bloodandbones.parts.ScrapsItem.of(new com.avicagan.bloodandbones.parts.Source(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow"), "leg", false), 1),
                pig == null ? new ItemStack(BBItems.OFFAL.get()) : CarcassPieceItem.of(pig, "left_front_leg"),
                BBItems.EYE.get().of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("cow"), false)};
        for (int i = 0; i < hung.length; i++) {
            BlockPos hook = new BlockPos(o.getX() - 3 + i, y + 2, wallZ - 1);
            level.setBlockAndUpdate(hook, BBBlocks.BUTCHER_HOOK.getDefaultState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.NORTH));
            if (level.getBlockEntity(hook) instanceof com.avicagan.bloodandbones.cooking.ButcherHookBlockEntity be) {
                be.put(hung[i]);
            }
        }
    }

    /** Create's Attribute Filter set to one of our part attributes. */
    private static ItemStack partFilter(com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute attribute) {
        ItemStack filter = new ItemStack(com.simibubi.create.AllItems.ATTRIBUTE_FILTER.get());
        filter.set(com.simibubi.create.AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, List.of(
                new com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute.ItemAttributeEntry(attribute, false)));
        return filter;
    }

    private static void burner(ServerLevel level, BlockPos at, com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel heat) {
        level.setBlockAndUpdate(at, AllBlocks.BLAZE_BURNER.getDefaultState().setValue(com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HEAT_LEVEL,
                com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SMOULDERING));
        ItemStack fuel = heat == com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel.SEETHING
                ? com.simibubi.create.AllItems.BLAZE_CAKE.asStack() : new ItemStack(net.minecraft.world.item.Items.COAL, 1);
        com.simibubi.create.content.processing.burner.BlazeBurnerBlock.tryInsert(level.getBlockState(at), level, at, fuel, false, true, false);
    }

    private static void motor(ServerLevel level, BlockPos at, Direction facing, int speed) {
        level.setBlockAndUpdate(at, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, facing));
        if (level.getBlockEntity(at) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(speed);
        }
    }

    private static void place(ServerLevel level, List<BlockPos> placed, BlockPos at, Direction facing) {
        level.setBlockAndUpdate(at, BBBlocks.RIBCAGE_ARCH.getDefaultState().setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing));
        placed.add(at);
    }

    private static void pile(ServerLevel level, BlockPos at, int layers) {
        level.setBlockAndUpdate(at, BBBlocks.BONE_PILE.getDefaultState().setValue(com.avicagan.bloodandbones.decoration.BonePileBlock.LAYERS, layers));
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
            if (step == 1) {
                // stand beside the nearest carcass of the front row looking at its middle, to hold the knife on it
                faceNearestCarcass(level, player);
                player.startUsingItem(InteractionHand.MAIN_HAND);
            }
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

    /** Stand 1.6 blocks west of the nearest carcass with a hide still on, looking at its torso. */
    private static void faceNearestCarcass(ServerLevel level, ServerPlayer player) {
        org.joml.Vector3d best = null;
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            org.joml.Vector3d at = CarcassAssembler.boneWorldPosition(level, carcass, carcass.rootBone);
            if (at != null && !carcass.skinned && com.avicagan.bloodandbones.carcass.Blood.bloody(carcass)
                    && (best == null || at.distanceSquared(player.getX(), player.getY(), player.getZ()) < best.distanceSquared(player.getX(), player.getY(), player.getZ()))) {
                best = at;
            }
        }
        if (best == null) {
            return;
        }
        double x = best.x - 1.6;
        double y = best.y - 0.9;
        double dy = best.y - (y + player.getEyeHeight());
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, 1.6));
        player.teleportTo(level, x, y, best.z, -90.0F, pitch);
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
        return carcass(level, type, at, shove, baby, 0.0F);
    }

    private static CarcassSavedData.Carcass carcass(ServerLevel level, EntityType<?> type, BlockPos at, boolean shove, boolean baby, float yaw) {
        if (!(type.create(level) instanceof Mob mob)) {
            return null;
        }
        mob.setBaby(baby);
        mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        mob.setYHeadRot(yaw);
        mob.yBodyRot = yaw;
        if (mob instanceof Sheep sheep) {
            sheep.setColor(DyeColor.WHITE);
        }
        level.addFreshEntity(mob);
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass != null && shove) {
            // as a kill would: knock it over
            double angle = level.random.nextDouble() * Math.PI * 2;
            CarcassAssembler.blow(level, carcass, new net.minecraft.world.phys.Vec3(Math.cos(angle), 0, Math.sin(angle)));
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
