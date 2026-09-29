package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.BBAttachments;
import com.avicagan.bloodandbones.body.Body;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.Necrosis;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.cyber.Module;
import com.avicagan.bloodandbones.cyber.Modules;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

/**
 * Self-augmentation's ritual and the proofs docs/BRIEF-AUDIT.md package 11 asked for: the body kept through a real death
 * and respawn; an amputation leaving health alone; the crude organs lifting their penalties; necrosis from a real hit, a
 * real block broken and a real run (and none from walking); the fog with one eye out; the surgery screen's choices, sent
 * as the screen sends them and handled by {@link Surgery#handle}; several implants' drains adding up; and six brass limbs
 * costing a serious farm, not an impossible one.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class RitualTests {
    /**
     * A real server player, not the test framework's bare mock: it can lie on the table, break a block the game's own way,
     * die and be respawned. It is not logged in (there is no client): its connection is one the test framework's own mock
     * player would have, which sends nothing anywhere.
     */
    private static ServerPlayer serverPlayer(GameTestHelper helper, BlockPos at) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "bb-patient"), ClientInformation.createDefault());
        new Silent(player);
        Vec3 v = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(v.x, v.y, v.z, 0.0F, 0.0F);
        return player;
    }

    /** A connection to no client: a channel of its own, as the test framework's mock player has, and nothing sent down it. */
    private static final class Silent extends ServerGamePacketListenerImpl {
        Silent(ServerPlayer player) {
            super(player.server, channel(), player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        private static Connection channel() {
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            return connection;
        }

        @Override
        public void send(Packet<?> packet, @org.jetbrains.annotations.Nullable PacketSendListener listener) {
        }
    }

    /** Done with a server player: off the server's books (its stats, its advancements), as a player logging out is. */
    private static void dismiss(ServerPlayer player) {
        player.stopRiding();
        player.getServer().getPlayerList().remove(player);
    }

    private static SurgeryTableBlockEntity table(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.SURGICAL));
        return (SurgeryTableBlockEntity) helper.getBlockEntity(at);
    }

    /** A surgeon minion by the table, helmeted (the test's floor sees the sky, and a zombie's torso burns by day). */
    private static MinionEntity surgeon(GameTestHelper helper, BlockPos at) {
        MinionEntity surgeon = MinionTests.surgeon(helper, at);
        surgeon.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return surgeon;
    }

    private static void wear(Player player, BacktankTier tier, Fluid fluid, int amount) {
        ItemStack tank = new ItemStack(BBItems.backtank(tier));
        FluidBacktankItem.setFluid(tank, new FluidStack(fluid, amount));
        player.setItemSlot(EquipmentSlot.CHEST, tank);
    }

    private static int tank(Player player) {
        return FluidBacktankItem.fluid(FluidBacktankItem.wornBy(player)).getAmount();
    }

    // ---------------------------------------------------------------- limb state through death

    /**
     * "Limb state persists through death": a player with an arm hacked off (a ragged stump of two buckets), a Sinew Leg
     * part rotted, a brass arm with a module in it and an eye gone is killed and respawned the game's own way (the server's
     * player list makes the new player). The new one has the same body, stump price, rot and module included, and feels it
     * again at once (the arms' swing penalty is back on its attack speed).
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void bodyKeptThroughDeath(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = serverPlayer(helper, new BlockPos(3, 2, 3));
        Body body = BodyEffects.body(player);
        body.lose(BodyPart.LEFT_ARM, 2);
        ItemStack leg = new ItemStack(BBItems.SINEW_LEG.get());
        leg.set(BBDataComponents.NECROSIS, 40);
        body.fit(BodyPart.RIGHT_LEG, leg);
        ItemStack arm = new ItemStack(BBItems.HYDRAULIC_ARM.get());
        Modules.set(arm, List.of(Module.PISTON_RAM));
        body.fit(BodyPart.RIGHT_ARM, arm);
        body.lose(BodyPart.LEFT_EYE);
        player.setData(BBAttachments.BODY, body);
        Body before = body.copy();
        player.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
        if (!player.isDeadOrDying()) {
            helper.fail("The player should have died");
            return;
        }
        ServerPlayer back = level.getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);
        try {
            Body after = BodyEffects.body(back);
            if (back == player || !after.equals(before) || after.raggedBuckets(BodyPart.LEFT_ARM) != 2
                    || Necrosis.of(after.implant(BodyPart.RIGHT_LEG)) != 40 || !Modules.of(after.implant(BodyPart.RIGHT_ARM)).equals(List.of(Module.PISTON_RAM))) {
                helper.fail("The respawned player's body should be the one that died: " + after + ", was " + before);
                return;
            }
            var swing = back.getAttribute(Attributes.ATTACK_SPEED).getModifier(BloodAndBones.asResource("arms"));
            if (swing == null || swing.amount() >= 0.0) {
                helper.fail("The respawned player should swing slower at once, with an arm gone and the brass one dry (its tank dropped)");
                return;
            }
        } finally {
            dismiss(back);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the slot system, not damage

    /**
     * "Amputation doesn't hurt you, it opens a slot": taking off every part a surgeon can take, one by one, leaves the
     * player's health and most health as they were, and a second of what the missing parts do takes none either.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void amputationLeavesHealthAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3));
        surgeon(helper, new BlockPos(4, 2, 4));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        float max = player.getMaxHealth();
        player.setHealth(max);
        table.put(new ItemStack(BBItems.CLEAVER.get()));
        for (BodyPart part : BodyPart.values()) {
            if (part == BodyPart.HEART) {
                continue;
            }
            if (Surgery.operate(level, player, table, part) != Surgery.Action.TAKE_OFF) {
                helper.fail("The surgeon should take off the " + part.getSerializedName());
                return;
            }
            BodyEffects.second(player);
            if (player.getMaxHealth() != max || player.getHealth() != max) {
                helper.fail("Taking off the " + part.getSerializedName() + " left health " + player.getHealth() + " of " + player.getMaxHealth()
                        + ", was " + max + " of " + max);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * The safety floor for the organs: the Crude Heart, Crude Lungs and Crude Stomach each lift the penalty of the organ
     * gone, and give nothing more. No heart leaves you weak and slow, and a Crude Heart ends it; no lungs stop a sprint on
     * the next tick, and with Crude Lungs it goes on; no stomach refuses food as you start to eat, and a Crude Stomach eats.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void crudeOrgansLiftPenalties(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Body body = BodyEffects.body(player);
        body.lose(BodyPart.HEART);
        BodyEffects.second(player);
        if (!player.hasEffect(MobEffects.WEAKNESS) || !player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            helper.fail("With no heart a player should be weak and slow");
            return;
        }
        player.removeAllEffects();
        body.fit(BodyPart.HEART, new ItemStack(BBItems.CRUDE_HEART.get()));
        BodyEffects.second(player);
        if (player.hasEffect(MobEffects.WEAKNESS) || player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || player.hasEffect(MobEffects.REGENERATION)) {
            helper.fail("A Crude Heart should lift the weakness and slowness, and give nothing more");
            return;
        }
        body.lose(BodyPart.LUNGS);
        player.setSprinting(true);
        player.tick();
        if (player.isSprinting()) {
            helper.fail("With no lungs a player should be winded: no sprinting");
            return;
        }
        body.fit(BodyPart.LUNGS, new ItemStack(BBItems.CRUDE_LUNGS.get()));
        player.setSprinting(true);
        player.tick();
        if (!player.isSprinting()) {
            helper.fail("Crude Lungs should let a player sprint again");
            return;
        }
        body.lose(BodyPart.STOMACH);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BREAD));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        if (player.isUsingItem()) {
            helper.fail("With no stomach a player should not be able to start eating");
            return;
        }
        body.fit(BodyPart.STOMACH, new ItemStack(BBItems.CRUDE_STOMACH.get()));
        player.startUsingItem(InteractionHand.MAIN_HAND);
        if (!player.isUsingItem()) {
            helper.fail("A Crude Stomach should let a player eat again");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- necrosis from use, the game's own way

    /** A Flesh Arm in the main arm, working on a backtank of blood. */
    private static ItemStack fleshArm(Player player) {
        BodyPart arm = BodyEffects.armFor(player, InteractionHand.MAIN_HAND);
        BodyEffects.body(player).fit(arm, new ItemStack(BBItems.FLESH_ARM.get()));
        wear(player, BacktankTier.COPPER, BBFluids.blood(), 1000);
        return BodyEffects.body(player).implant(arm);
    }

    /** A real swing: the player hits a pig the way the game does (Player#attack, which fires the attack event), and the Flesh Arm rots a point. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void necrosisFromARealHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(3.5, 2, 2.5));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        fleshArm(player);
        var pig = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 3));
        float health = pig.getHealth();
        player.attack(pig);
        ItemStack arm = BodyEffects.body(player).implant(BodyEffects.armFor(player, InteractionHand.MAIN_HAND));
        if (pig.getHealth() >= health || Necrosis.of(arm) != Necrosis.SWING) {
            helper.fail("A hit should land and rot the Flesh Arm by " + Necrosis.SWING + ", but the pig is at " + pig.getHealth() + " of " + health
                    + " and the arm at " + Necrosis.of(arm));
            return;
        }
        helper.succeed();
    }

    /** A real block broken: the game's own breaking (the player's game mode, which fires the break event) rots the Flesh Arm a point. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void necrosisFromARealBlockBreak(GameTestHelper helper) {
        ServerPlayer player = serverPlayer(helper, new BlockPos(3, 2, 2));
        try {
            fleshArm(player);
            helper.setBlock(new BlockPos(3, 2, 3), Blocks.DIRT);
            boolean broke = player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(3, 2, 3)));
            ItemStack arm = BodyEffects.body(player).implant(BodyEffects.armFor(player, InteractionHand.MAIN_HAND));
            if (!broke || !helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(3, 2, 3))).isAir() || Necrosis.of(arm) != Necrosis.SWING) {
                helper.fail("Breaking a block should rot the Flesh Arm by " + Necrosis.SWING + ": broke " + broke + ", arm at " + Necrosis.of(arm));
                return;
            }
        } finally {
            dismiss(player);
        }
        helper.succeed();
    }

    /**
     * A real run, and a walk: a player on two Sinew Legs, ticked by the game's own player tick (the movement from its
     * input, then the tick event), walks round in circles for three seconds and then sprints for three. The walk leaves the
     * legs as they were (the brief names running, not walking); the sprint rots both. The blood in the tank would clear the
     * rot again once a second, so the player's clock is kept short of a second: only the run shows.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void necrosisFromARunNotAWalk(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Body body = BodyEffects.body(player);
        body.fit(BodyPart.LEFT_LEG, new ItemStack(BBItems.SINEW_LEG.get()));
        body.fit(BodyPart.RIGHT_LEG, new ItemStack(BBItems.SINEW_LEG.get()));
        wear(player, BacktankTier.COPPER, BBFluids.blood(), 4000);
        Vec3 centre = helper.absoluteVec(new Vec3(5.5, 2, 5.5));
        player.moveTo(centre.x + 2.0, centre.y, centre.z, 0.0F, 0.0F);
        int[] t = {0};
        int[] walked = {-1};
        double[] distance = {0, 0};
        helper.onEachTick(() -> {
            int now = t[0]++;
            boolean sprint = now >= 60;
            if (now == 60) {
                walked[0] = rot(body);
            }
            if (now == 120) {
                int ran = rot(body) - walked[0];
                BloodAndBones.LOGGER.info("[ritual] walked {} blocks for {} rot, sprinted {} for {}", String.format("%.1f", distance[0]), walked[0],
                        String.format("%.1f", distance[1]), ran);
                helper.assertTrue(walked[0] == 0, "walking " + String.format("%.1f", distance[0]) + " blocks should not rot the legs, but it did by " + walked[0]);
                helper.assertTrue(distance[1] > 10.0 && ran >= 4, "sprinting " + String.format("%.1f", distance[1])
                        + " blocks should rot both legs (a point each every 2.7 blocks), but they rotted by " + ran);
                helper.succeed();
                return;
            }
            if (now > 120) {
                return;
            }
            Vec3 was = player.position();
            player.setSprinting(sprint);
            player.zza = 1.0F;
            // round and round, inside the test's own ground
            player.setYRot(player.getYRot() + (sprint ? 8.0F : 5.0F));
            player.tickCount = 1;
            player.tick();
            distance[sprint ? 1 : 0] += Math.hypot(player.getX() - was.x, player.getZ() - was.z);
        });
    }

    private static int rot(Body body) {
        return Necrosis.of(body.implant(BodyPart.LEFT_LEG)) + Necrosis.of(body.implant(BodyPart.RIGHT_LEG));
    }

    // ---------------------------------------------------------------- reduced vision

    /**
     * An eye gone is reduced vision, the fog the client draws closing in to what {@link BodyEffects#sight} gives: both eyes,
     * no change; one out, half as far; an Optic Eye in the socket but its tank dry, still half; the tank filled, full sight
     * again; both out, a few blocks; Glass Eyes in both, full sight.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fogWithOneEyeOut(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Body body = BodyEffects.body(player);
        float far = 192.0F;
        helper.assertTrue(BodyEffects.sight(body, player, far) == far, "two eyes should see as far as ever");
        body.lose(BodyPart.LEFT_EYE);
        helper.assertTrue(BodyEffects.sight(body, player, far) == far * BodyEffects.ONE_EYE_SIGHT, "one eye out should halve the view, got "
                + BodyEffects.sight(body, player, far));
        body.fit(BodyPart.LEFT_EYE, new ItemStack(BBItems.OPTIC_EYE.get()));
        helper.assertTrue(BodyEffects.sight(body, player, far) == far * BodyEffects.ONE_EYE_SIGHT, "a dry Optic Eye should not see");
        wear(player, BacktankTier.COPPER, BBFluids.soulBlood(), 1000);
        helper.assertTrue(BodyEffects.sight(body, player, far) == far, "an Optic Eye on soul blood should see");
        body.lose(BodyPart.LEFT_EYE);
        body.lose(BodyPart.RIGHT_EYE);
        helper.assertTrue(BodyEffects.sight(body, player, far) == BodyEffects.NO_EYE_SIGHT, "no eyes should see a few blocks");
        body.fit(BodyPart.LEFT_EYE, new ItemStack(BBItems.GLASS_EYE.get()));
        body.fit(BodyPart.RIGHT_EYE, new ItemStack(BBItems.GLASS_EYE.get()));
        helper.assertTrue(BodyEffects.sight(body, player, far) == far, "Glass Eyes should give back full sight");
        helper.succeed();
    }

    // ---------------------------------------------------------------- the screen and its button path

    /**
     * The surgery screen offers what you carry: for each slot, unclipping with nothing (an implant, not the heart), then
     * what lies on the table, then each implant, prosthetic, limb and module carried (inventory, armour or off-hand) that the
     * slot takes, each kind once; nothing that does not fit (a Peg Leg for an arm, dirt).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void screenOffersWhatYouCarry(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Body body = BodyEffects.body(player);
        body.lose(BodyPart.RIGHT_ARM);
        body.fit(BodyPart.LEFT_ARM, new ItemStack(BBItems.HYDRAULIC_ARM.get()));
        var inventory = player.getInventory();
        inventory.setItem(0, new ItemStack(BBItems.HOOK_HAND.get()));
        inventory.setItem(1, new ItemStack(BBItems.PEG_LEG.get()));
        inventory.setItem(2, new ItemStack(Blocks.DIRT));
        inventory.setItem(3, new ItemStack(BBItems.HOOK_HAND.get()));
        inventory.setItem(4, BBItems.partItem(BodyPart.Kind.ARM).of(player));
        inventory.setItem(5, new ItemStack(BBItems.module(Module.PISTON_RAM)));
        inventory.setItem(6, new ItemStack(BBItems.CLEAVER.get()));
        inventory.setItem(7, new ItemStack(BBItems.CRUDE_HEART.get()));
        // the off-hand counts as carried too
        inventory.setItem(40, new ItemStack(BBItems.PISTON_LEG.get()));
        ItemStack onTable = new ItemStack(BBItems.CLEAVER.get());

        List<Surgery.Option> open = Surgery.options(body, BodyPart.RIGHT_ARM, onTable, player);
        helper.assertTrue(open.size() == 2 && open.get(0).source() == 0 && open.get(0).action() == Surgery.Action.FIT
                && open.get(1).source() == 4 && open.get(1).action() == Surgery.Action.REATTACH, "an open arm should offer the Hook Hand once and "
                + "the arm back, got " + describe(open));
        List<Surgery.Option> brass = Surgery.options(body, BodyPart.LEFT_ARM, onTable, player);
        helper.assertTrue(brass.size() == 4 && brass.get(0).source() == Surgery.BARE && brass.get(0).action() == Surgery.Action.UNCLIP
                && brass.get(1).source() == 0 && brass.get(1).action() == Surgery.Action.SWAP && brass.get(2).source() == 4
                && brass.get(3).source() == 5 && brass.get(3).action() == Surgery.Action.FIT_MODULE, "a brass arm should offer unclipping, the Hook "
                + "Hand and the arm swapped in, and the module, got " + describe(brass));
        List<Surgery.Option> leg = Surgery.options(body, BodyPart.LEFT_LEG, onTable, player);
        helper.assertTrue(leg.size() == 3 && leg.get(0).source() == Surgery.FROM_TABLE && leg.get(0).action() == Surgery.Action.TAKE_OFF
                && leg.get(1).source() == 1 && leg.get(1).action() == Surgery.Action.REPLACE && leg.get(2).source() == 40,
                "a leg of flesh should offer the table's Cleaver (not the carried one again), the Peg Leg and the off-hand's Piston Leg, got " + describe(leg));
        List<Surgery.Option> heart = Surgery.options(body, BodyPart.HEART, onTable, player);
        helper.assertTrue(heart.size() == 1 && heart.get(0).source() == 7 && heart.get(0).action() == Surgery.Action.REPLACE,
                "a heart should offer only a heart swapped in, never a cut, got " + describe(heart));
        helper.succeed();
    }

    private static String describe(List<Surgery.Option> options) {
        return options.stream().map(o -> o.action() + " from " + o.source() + " (" + o.stack().getItem() + ")").toList().toString();
    }

    /** A choice as the screen sends it: its payload written and read back as it crosses the network, then handled. */
    private static void click(ServerPlayer player, Surgery.ActionPayload payload) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        Surgery.ActionPayload.STREAM_CODEC.encode(buffer, payload);
        Surgery.handle(player, Surgery.ActionPayload.STREAM_CODEC.decode(buffer));
    }

    /**
     * The real button path: a player lies on the table with a surgeon beside it, and each choice goes as the screen sends it
     * (the payload, over the wire and back) through {@link Surgery#handle}. The table's Cleaver takes the right arm off; a
     * Hook Hand carried in the ninth slot goes in, out of that slot; unclipped with bare hands it comes back. A choice
     * naming a slot that is not there, one from someone neither on nor near the table, and one with the rig taken off the
     * table do nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void buttonPathThroughHandle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(3, 2, 3);
        SurgeryTableBlockEntity table = table(helper, at);
        BlockPos pos = helper.absolutePos(at);
        surgeon(helper, new BlockPos(4, 2, 4));
        ServerPlayer player = serverPlayer(helper, new BlockPos(2, 2, 3));
        ServerPlayer stranger = serverPlayer(helper, new BlockPos(3, 2, 3));
        try {
            if (!SurgeryTableBlock.lieDown(level, pos, player)) {
                helper.fail("The player should lie down on the table");
                return;
            }
            table.put(new ItemStack(BBItems.CLEAVER.get()));
            Body body = BodyEffects.body(player);
            click(player, new Surgery.ActionPayload(pos, BodyPart.RIGHT_ARM, Surgery.FROM_TABLE));
            if (BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.MISSING || player.getInventory().countItem(BBItems.SEVERED_ARM.get()) != 1
                    || !table.item().is(BBItems.CLEAVER.get())) {
                helper.fail("The table's Cleaver should take the arm off, into the player's hands, and stay on the table");
                return;
            }
            player.getInventory().setItem(8, new ItemStack(BBItems.HOOK_HAND.get()));
            Surgery.Option hook = Surgery.options(BodyEffects.body(player), BodyPart.RIGHT_ARM, table.item(), player).stream()
                    .filter(o -> o.stack().is(BBItems.HOOK_HAND.get())).findFirst().orElse(null);
            if (hook == null || hook.source() != 8) {
                helper.fail("The screen should offer the carried Hook Hand from its slot");
                return;
            }
            // not there, or not theirs to send: nothing happens
            click(player, new Surgery.ActionPayload(pos, BodyPart.RIGHT_ARM, 99));
            stranger.moveTo(pos.getX() + 20.5, pos.getY(), pos.getZ() + 0.5);
            click(stranger, Surgery.ActionPayload.of(pos, BodyPart.RIGHT_ARM, hook));
            if (BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.MISSING || !player.getInventory().getItem(8).is(BBItems.HOOK_HAND.get())) {
                helper.fail("A choice naming no slot, or sent from across the room, should do nothing");
                return;
            }
            click(player, Surgery.ActionPayload.of(pos, BodyPart.RIGHT_ARM, hook));
            body = BodyEffects.body(player);
            if (body.state(BodyPart.RIGHT_ARM) != Body.State.IMPLANT || !body.implant(BodyPart.RIGHT_ARM).is(BBItems.HOOK_HAND.get())
                    || !player.getInventory().getItem(8).isEmpty()) {
                helper.fail("The carried Hook Hand should go in, out of its slot");
                return;
            }
            click(player, new Surgery.ActionPayload(pos, BodyPart.RIGHT_ARM, Surgery.BARE));
            if (BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.MISSING || player.getInventory().countItem(BBItems.HOOK_HAND.get()) != 1) {
                helper.fail("Unclipped with bare hands, the Hook Hand should come back");
                return;
            }
            // the rig off the table: it is a bare table, and nothing is done
            level.setBlockAndUpdate(pos, level.getBlockState(pos).setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.NONE));
            int slot = player.getInventory().findSlotMatchingItem(new ItemStack(BBItems.HOOK_HAND.get()));
            click(player, new Surgery.ActionPayload(pos, BodyPart.RIGHT_ARM, slot));
            if (BodyEffects.body(player).state(BodyPart.RIGHT_ARM) != Body.State.MISSING) {
                helper.fail("With no Surgical Rig on the table nothing should be done");
                return;
            }
        } finally {
            dismiss(player);
            dismiss(stranger);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- upkeep

    /**
     * Several implants' drains add up, each second, from the worn tank: on soul blood a Hydraulic Arm (2) with a Magnet
     * Coil (1), a Piston Leg (2), an Optic Eye (1) with an Analytical Lens (1), a Pump Heart (2) and Bellows Lungs (1), ten
     * in all; a Hook Hand (crude) takes nothing, nor does a Furnace Stomach, which runs on blood and so is not working on
     * this tank. Five seconds take fifty. At half the blood upkeep, a second takes five.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void implantDrainsAddUp(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Body body = BodyEffects.body(player);
        ItemStack arm = new ItemStack(BBItems.HYDRAULIC_ARM.get());
        Modules.set(arm, List.of(Module.MAGNET_COIL));
        body.fit(BodyPart.RIGHT_ARM, arm);
        body.fit(BodyPart.LEFT_ARM, new ItemStack(BBItems.HOOK_HAND.get()));
        body.fit(BodyPart.LEFT_LEG, new ItemStack(BBItems.PISTON_LEG.get()));
        ItemStack eye = new ItemStack(BBItems.OPTIC_EYE.get());
        Modules.set(eye, List.of(Module.ANALYTICAL_LENS));
        body.fit(BodyPart.RIGHT_EYE, eye);
        body.fit(BodyPart.HEART, new ItemStack(BBItems.PUMP_HEART.get()));
        body.fit(BodyPart.LUNGS, new ItemStack(BBItems.BELLOWS_LUNGS.get()));
        body.fit(BodyPart.STOMACH, new ItemStack(BBItems.FURNACE_STOMACH.get()));
        wear(player, BacktankTier.COPPER, BBFluids.soulBlood(), 1000);
        BodyEffects.drain(player);
        helper.assertTrue(tank(player) == 990, "one second should take 2 + 1 + 2 + 1 + 1 + 2 + 1 = 10 mB, but left " + tank(player));
        for (int i = 0; i < 5; i++) {
            BodyEffects.drain(player);
        }
        helper.assertTrue(tank(player) == 940, "five more seconds should take 50 mB, but left " + tank(player));
        player.getAttribute(com.avicagan.bloodandbones.parts.effect.UpkeepEffects.BLOOD_UPKEEP).setBaseValue(0.5);
        BodyEffects.drain(player);
        helper.assertTrue(tank(player) == 935, "at half the upkeep a second should take 5 mB, but left " + tank(player));
        helper.succeed();
    }

    /** What makes a farm serious, and what makes it impossible, in cows an hour and basins (docs/ARCHITECTURE-PROPOSAL.md 15.29). */
    public static final int SERIOUS_COWS_AN_HOUR = 20;
    public static final int IMPOSSIBLE_COWS_AN_HOUR = 120;
    public static final int MOST_BASINS = 4;

    /**
     * "Six replaced limbs demands a serious farm": two Hydraulic Arms, two Piston Legs and two Optic Eyes, measured the
     * game's way over a minute of drain, cost what the soul blood line makes from a certain number of cows an hour. The
     * line's own numbers are read from its recipes (the blood a Basin Lid sets, how long it takes, what a clot melts back
     * to) and a cow's blood from its rig. The farm must be serious (at least 20 cows an hour: more than a pen fed by hand)
     * but not impossible (at most 120 an hour, two a minute, and four Basin Lids).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void sixBrassLimbsNeedASeriousFarm(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Body body = BodyEffects.body(player);
        for (BodyPart part : BodyPart.values()) {
            switch (part.kind()) {
                case ARM -> body.fit(part, new ItemStack(BBItems.HYDRAULIC_ARM.get()));
                case LEG -> body.fit(part, new ItemStack(BBItems.PISTON_LEG.get()));
                case EYE -> body.fit(part, new ItemStack(BBItems.OPTIC_EYE.get()));
                default -> {
                }
            }
        }
        wear(player, BacktankTier.COPPER, BBFluids.soulBlood(), 4000);
        for (int second = 0; second < 60; second++) {
            BodyEffects.drain(player);
        }
        int perMinute = 4000 - tank(player);
        double soulPerHour = perMinute * 60.0;
        var recipes = helper.getLevel().getRecipeManager();
        var congeal = (com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>) recipes.byKey(BloodAndBones.asResource("basin_fermenting/congealed_blood"))
                .orElseThrow().value();
        var melt = (com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>) recipes.byKey(BloodAndBones.asResource("mixing/soul_blood"))
                .orElseThrow().value();
        int bloodPerClot = congeal.getFluidIngredients().get(0).amount();
        int soulPerClot = melt.getFluidResults().get(0).getAmount();
        int ticksPerClot = congeal.getProcessingDuration();
        double cowBlood = com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(EntityType.COW).orElseThrow().weight()
                * com.avicagan.bloodandbones.carcass.CarcassBleeding.BLOOD_PER_WEIGHT;
        double bloodPerHour = soulPerHour * bloodPerClot / soulPerClot;
        double cowsPerHour = bloodPerHour / cowBlood;
        double basins = soulPerHour / (soulPerClot * 72000.0 / ticksPerClot);
        BloodAndBones.LOGGER.info("[ritual] six brass limbs: {} mB of soul blood a minute, {} an hour; {} mB of blood an hour through the line "
                        + "({} mB a clot in {} ticks, {} mB back); a cow bleeds {} mB, so {} cows an hour and {} Basin Lids",
                perMinute, Math.round(soulPerHour), Math.round(bloodPerHour), bloodPerClot, ticksPerClot, soulPerClot, Math.round(cowBlood),
                String.format("%.1f", cowsPerHour), String.format("%.2f", basins));
        helper.assertTrue(perMinute == 600, "six brass limbs should drain 10 mB a second, 600 a minute, but took " + perMinute);
        helper.assertTrue(cowsPerHour >= SERIOUS_COWS_AN_HOUR, "six brass limbs should need a serious farm, at least " + SERIOUS_COWS_AN_HOUR
                + " cows an hour, but need " + String.format("%.1f", cowsPerHour));
        helper.assertTrue(cowsPerHour <= IMPOSSIBLE_COWS_AN_HOUR && basins <= MOST_BASINS, "six brass limbs should not need an impossible farm, at most "
                + IMPOSSIBLE_COWS_AN_HOUR + " cows an hour and " + MOST_BASINS + " Basin Lids, but need " + String.format("%.1f", cowsPerHour)
                + " and " + String.format("%.2f", basins));
        helper.succeed();
    }

    // ---------------------------------------------------------------- what a missing limb shows

    /**
     * What is held, and armour, is drawn only on a limb that is there ({@link BodyEffects#shows}, which the third-person
     * held-item and armour layers ask): not on a player's missing arm or leg; on a stump fitted with a prosthetic, yes; on a
     * mob, whose missing limbs are still drawn, yes.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void missingLimbShowsNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(BodyEffects.shows(player, BodyPart.LEFT_ARM), "a whole player shows what they hold");
        Body body = BodyEffects.body(player);
        body.lose(BodyPart.LEFT_ARM);
        body.lose(BodyPart.RIGHT_LEG);
        body.fit(BodyPart.RIGHT_ARM, new ItemStack(BBItems.HOOK_HAND.get()));
        helper.assertTrue(!BodyEffects.shows(player, BodyPart.LEFT_ARM) && !BodyEffects.shows(player, BodyPart.RIGHT_LEG),
                "a missing arm should hold nothing and a missing leg wear nothing");
        helper.assertTrue(BodyEffects.shows(player, BodyPart.RIGHT_ARM) && BodyEffects.shows(player, BodyPart.LEFT_LEG),
                "a Hook Hand and a leg of flesh should still show what they hold and wear");
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        BodyEffects.body(zombie).lose(BodyPart.LEFT_ARM);
        helper.assertTrue(BodyEffects.shows(zombie, BodyPart.LEFT_ARM), "a mob's missing arm is still drawn, and so is what it holds");
        helper.succeed();
    }
}
