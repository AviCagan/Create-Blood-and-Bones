package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.bleeding.FanAirflow;
import com.avicagan.bloodandbones.body.BodyEffects;
import com.avicagan.bloodandbones.body.BodyPart;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.ShackleHookBlock;
import com.avicagan.bloodandbones.carcass.ShackleHookBlockEntity;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.cooking.ButcherHookBlockEntity;
import com.avicagan.bloodandbones.cyber.Module;
import com.avicagan.bloodandbones.cyber.ModuleActions;
import com.avicagan.bloodandbones.cyber.Modules;
import com.avicagan.bloodandbones.cyber.Throttle;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.minion.MinionAssembly;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.kinetics.simpleRelays.ShaftBlock;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Things that were built but that no test proved (docs/BRIEF-AUDIT.md package 18): each of these fails if its
 * feature is taken out. Game tests share one world, so each test only looks at the carcasses it made itself.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class UnprovenTests {
    private static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");

    /** The body of one of this test's carcasses, or null if it has gone. */
    private static ServerSubLevel body(ServerLevel level, CarcassSavedData.Carcass carcass, String bone) {
        UUID id = carcass.bones.get(bone);
        return id != null && SubLevelContainer.getContainer(level).getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved() ? body : null;
    }

    /** A carcass of this mob lying here, made as a kill would make it. */
    private static CarcassSavedData.Carcass carcass(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos at) {
        Mob mob = helper.spawn(type, at);
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass == null) {
            helper.fail("Carcass assembly returned null for " + type);
        }
        return carcass;
    }

    /** The records that exist now, so a test can tell the ones it made from everyone else's. */
    private static Set<UUID> known(ServerLevel level) {
        Set<UUID> ids = new HashSet<>();
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            ids.add(carcass.id);
        }
        return ids;
    }

    /** Records made since {@code before}, of this mob, whose root lies within {@code reach} of a point. */
    private static List<CarcassSavedData.Carcass> madeNear(ServerLevel level, Set<UUID> before, ResourceLocation entity, Vec3 at, double reach) {
        return CarcassSavedData.get(level).all().stream().filter(c -> !before.contains(c.id) && c.entity.equals(entity)).filter(c -> {
            Vector3d p = CarcassAssembler.boneWorldPosition(level, c, c.rootBone);
            return p != null && p.distance(at.x, at.y, at.z) <= reach;
        }).toList();
    }

    // ---- a fan speeds up bleeding (brief: "A Create fan blowing over a carcass hanging above a Bleeding Rack should make it drain faster")

    /**
     * A cow hung on a Shackle Hook bleeds a fixed amount every half second. With an encased fan blowing up through
     * the column it hangs in, it bleeds about three times as fast (128 RPM is a boost of 1 + 128 / 64).
     */
    @GameTest(template = "empty", timeoutTicks = 420)
    public static void fanSpeedsUpBleeding(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(5, 2, 5));
        helper.setBlock(new BlockPos(5, 7, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 6, 5), BBBlocks.SHACKLE_HOOK.get().defaultBlockState().setValue(ShackleHookBlock.FACING, Direction.UP));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(5, 2, 5))));
        player.setOldPosAndRot();
        float[] blood = new float[4];
        BlockPos[] column = new BlockPos[1];
        helper.runAfterDelay(10, () -> {
            ServerSubLevel leg = body(level, cow, "right_hind_leg");
            if (leg == null || !CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null)) {
                helper.fail("Could not hook the cow");
                return;
            }
            ((ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(new BlockPos(5, 6, 5)))).toggle(level, player);
        });
        // hung and still: bleeding with no fan
        helper.runAfterDelay(100, () -> blood[0] = cow.blood);
        helper.runAfterDelay(200, () -> {
            blood[1] = cow.blood;
            // a fan on the floor under the torso, blowing up through the column it hangs in (the fan reads where the
            // torso's body is, as the bleeding does)
            ServerSubLevel torso = body(level, cow, cow.rootBone);
            if (torso == null) {
                helper.fail("The cow's torso is gone");
                return;
            }
            Vector3d at = torso.logicalPose().position();
            column[0] = BlockPos.containing(at.x(), at.y(), at.z());
            BlockPos fan = new BlockPos(column[0].getX(), helper.absolutePos(new BlockPos(0, 2, 0)).getY(), column[0].getZ());
            level.setBlockAndUpdate(fan.below(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
            level.setBlockAndUpdate(fan, AllBlocks.ENCASED_FAN.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
            if (level.getBlockEntity(fan.below()) instanceof CreativeMotorBlockEntity motor) {
                motor.generatedSpeed.setValue(128);
            }
        });
        // the fan works out its air current within a couple of seconds
        helper.runAfterDelay(260, () -> {
            blood[2] = cow.blood;
            float fan = FanAirflow.fanSpeedAt(level, column[0]);
            if (fan <= 0.0F) {
                helper.fail("The fan's air should pass through the hanging torso's block " + column[0]);
            }
        });
        helper.runAfterDelay(360, () -> {
            blood[3] = cow.blood;
            float still = blood[0] - blood[1];
            float blown = blood[2] - blood[3];
            BloodAndBones.LOGGER.info("[unproven] fan: {} mB in 100 ticks with no fan, {} mB with the fan ({} of {} left)", still, blown, cow.blood, cow.bloodMax);
            if (still <= 0.0F) {
                helper.fail("A hung cow should bleed with no fan, bled " + still + " mB in five seconds");
            }
            if (blown < still * 2.0F) {
                helper.fail("A fan at 128 RPM should about triple the bleeding: " + still + " mB with no fan, " + blown + " mB with it");
            }
            helper.succeed();
        });
    }

    // ---- found by the two-client check: hanging a carcass threw the player standing by it

    /**
     * A cow lying on the floor a few blocks from a Shackle Hook is hung: it comes up to the hook at a steady pace and
     * then hangs there. Held fast at once, it flew up to the tip in a single tick, at some 80 blocks a second, and threw
     * the player beside it tens of blocks.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void shackleHookHoistsWithoutFlinging(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(3, 2, 5));
        BlockPos hookAt = new BlockPos(6, 6, 5);
        helper.setBlock(hookAt.above(), Blocks.STONE);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.get().defaultBlockState().setValue(ShackleHookBlock.FACING, Direction.UP));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3))));
        player.setOldPosAndRot();
        double[] fastest = {0.0};
        Vector3d[] last = {null};
        int[] hungAt = {-1};
        helper.runAfterDelay(40, () -> {
            ServerSubLevel leg = body(level, cow, "right_hind_leg");
            if (leg == null || !CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null)) {
                helper.fail("Could not hook the cow");
                return;
            }
            ((ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hookAt))).toggle(level, player);
            hungAt[0] = (int) helper.getTick();
        });
        helper.onEachTick(() -> {
            ServerSubLevel torso = body(level, cow, cow.rootBone);
            if (hungAt[0] < 0 || torso == null) {
                return;
            }
            Vector3d now = new Vector3d(torso.logicalPose().position());
            if (last[0] != null) {
                fastest[0] = Math.max(fastest[0], now.distance(last[0]) * 20.0);
            }
            last[0] = now;
            if (helper.getTick() == hungAt[0] + 100) {
                ShackleHookBlockEntity hook = (ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hookAt));
                Vec3 tip = ShackleHookBlock.tip(helper.absolutePos(hookAt), hook.getBlockState());
                double gap = torso.logicalPose().transformPosition(hook.hookedAnchor(), new Vector3d()).distance(tip.x, tip.y, tip.z);
                BloodAndBones.LOGGER.info("[unproven] hoist: fastest {} blocks a second, {} from the tip after five seconds", fastest[0], gap);
                if (fastest[0] > 10.0) {
                    helper.fail("The hook should hoist the cow up, not fling it: its torso moved at " + fastest[0] + " blocks a second");
                    return;
                }
                if (!hook.isOccupied() || gap > 0.35) {
                    helper.fail("The cow should hang from the hook's tip after five seconds, it is " + gap + " blocks off");
                    return;
                }
                helper.succeed();
            }
        });
    }

    /** A stand-in player holding a Meat Hook, who hooks this cow by a hind leg and hangs it on the hook here. */
    private static boolean hang(GameTestHelper helper, CarcassSavedData.Carcass cow, BlockPos hookAt, BlockPos standAt) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(standAt)));
        player.setOldPosAndRot();
        ServerSubLevel leg = body(level, cow, "right_hind_leg");
        if (leg == null || !CarcassDrag.start(level, player, leg.getPlot().getCenterBlock(), null)) {
            helper.fail("Could not hook the cow");
            return false;
        }
        ((ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hookAt))).toggle(level, player);
        return true;
    }

    /** How far the hooked point of the hook's body is from its tip. */
    private static double gapToTip(GameTestHelper helper, BlockPos hookAt, ServerSubLevel torso) {
        ShackleHookBlockEntity hook = (ShackleHookBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(hookAt));
        Vec3 tip = ShackleHookBlock.tip(helper.absolutePos(hookAt), hook.getBlockState());
        return torso.logicalPose().transformPosition(hook.hookedAnchor(), new Vector3d()).distance(tip.x, tip.y, tip.z);
    }

    /**
     * A hook saved part way through its hoist (the world closed, or its chunk unloaded) comes back knowing what it holds
     * but not that it was hoisting. It must hoist the body the rest of the way, not snap it up to the tip from where it
     * is.
     */
    @GameTest(template = "empty", timeoutTicks = 260)
    public static void shackleHookReloadedPartWayUpKeepsHoisting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(2, 2, 5));
        BlockPos hookAt = new BlockPos(7, 6, 5);
        net.minecraft.world.level.block.state.BlockState hookState = BBBlocks.SHACKLE_HOOK.get().defaultBlockState().setValue(ShackleHookBlock.FACING, Direction.UP);
        helper.setBlock(hookAt.above(), Blocks.STONE);
        helper.setBlock(hookAt, hookState);
        double[] fastest = {0.0};
        double[] reloadedAt = {-1.0};
        Vector3d[] last = {null};
        int[] hungAt = {-1};
        helper.runAfterDelay(40, () -> {
            if (hang(helper, cow, hookAt, new BlockPos(2, 2, 3))) {
                hungAt[0] = (int) helper.getTick();
            }
        });
        helper.onEachTick(() -> {
            ServerSubLevel torso = body(level, cow, cow.rootBone);
            if (hungAt[0] < 0 || torso == null) {
                return;
            }
            long since = helper.getTick() - hungAt[0];
            if (since == 8) {
                // what a save and load does to the hook: a new block entity read back from what the old one saved
                reloadedAt[0] = gapToTip(helper, hookAt, torso);
                BlockPos at = helper.absolutePos(hookAt);
                net.minecraft.nbt.CompoundTag saved = level.getBlockEntity(at).saveWithFullMetadata(level.registryAccess());
                level.removeBlockEntity(at);
                level.setBlockEntity(net.minecraft.world.level.block.entity.BlockEntity.loadStatic(at, hookState, saved, level.registryAccess()));
            }
            Vector3d now = new Vector3d(torso.logicalPose().position());
            if (last[0] != null && since > 8) {
                fastest[0] = Math.max(fastest[0], now.distance(last[0]) * 20.0);
            }
            last[0] = now;
            if (since == 130) {
                ShackleHookBlockEntity hook = (ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hookAt));
                double gap = gapToTip(helper, hookAt, torso);
                BloodAndBones.LOGGER.info("[unproven] reloaded hoist: {} from the tip when reloaded, fastest {} blocks a second after, {} from the tip at the end",
                        reloadedAt[0], fastest[0], gap);
                if (reloadedAt[0] < 1.0) {
                    helper.fail("The test should reload the hook while the cow is still well short of the tip, it was " + reloadedAt[0] + " blocks off");
                    return;
                }
                if (fastest[0] > 10.0) {
                    helper.fail("A reloaded hook should go on hoisting, not snap the cow up: its torso moved at " + fastest[0] + " blocks a second");
                    return;
                }
                if (!hook.isOccupied() || gap > 0.35) {
                    helper.fail("The cow should hang from the reloaded hook's tip, it is " + gap + " blocks off");
                    return;
                }
                helper.succeed();
            }
        });
    }

    /**
     * A body the hook cannot bring up to its tip (here it lies in a stone pen) is held where it got to once the hoist's
     * time is up, not yanked up to the tip through whatever holds it.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void shackleHookHoldsACaughtBodyWhereItGotTo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // a pen two blocks high with a stone roof, the hook outside it and up
        for (int x = 0; x <= 6; x++) {
            for (int z = 2; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, 4, z), Blocks.STONE);
                if (x == 0 || x == 6 || z == 2 || z == 8) {
                    helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
                    helper.setBlock(new BlockPos(x, 3, z), Blocks.STONE);
                }
            }
        }
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(3, 2, 5));
        BlockPos hookAt = new BlockPos(9, 6, 5);
        helper.setBlock(hookAt.above(), Blocks.STONE);
        helper.setBlock(hookAt, BBBlocks.SHACKLE_HOOK.get().defaultBlockState().setValue(ShackleHookBlock.FACING, Direction.UP));
        AABB pen = new AABB(Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(1, 2, 3))), Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(6, 4, 8))));
        double[] fastest = {0.0};
        Vector3d[] last = {null};
        int[] hungAt = {-1};
        helper.runAfterDelay(40, () -> {
            if (hang(helper, cow, hookAt, new BlockPos(3, 2, 1))) {
                hungAt[0] = (int) helper.getTick();
            }
        });
        helper.onEachTick(() -> {
            ServerSubLevel torso = body(level, cow, cow.rootBone);
            if (hungAt[0] < 0 || torso == null) {
                return;
            }
            Vector3d now = new Vector3d(torso.logicalPose().position());
            if (last[0] != null) {
                fastest[0] = Math.max(fastest[0], now.distance(last[0]) * 20.0);
            }
            last[0] = now;
            if (helper.getTick() == hungAt[0] + ShackleHookBlockEntity.HOIST_TICKS + 60) {
                ShackleHookBlockEntity hook = (ShackleHookBlockEntity) level.getBlockEntity(helper.absolutePos(hookAt));
                BloodAndBones.LOGGER.info("[unproven] caught hoist: fastest {} blocks a second, torso at {}, {} from the tip", fastest[0], now, gapToTip(helper, hookAt, torso));
                if (fastest[0] > 10.0) {
                    helper.fail("The hook should not yank a body it cannot lift: its torso moved at " + fastest[0] + " blocks a second");
                    return;
                }
                if (!pen.contains(now.x, now.y, now.z)) {
                    helper.fail("The cow should still be in its pen, it is at " + now);
                    return;
                }
                if (!hook.isOccupied()) {
                    helper.fail("The hook should still hold the cow where it got to");
                    return;
                }
                helper.succeed();
            }
        });
    }

    /**
     * Hung on a Shackle Trolley, a cow lying under the chain is hoisted up to it at the hook's pace, and the trolley
     * carries it on once it is up. Held at once, it flew up to the chain in a tick, as it did on the hook.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void shackleTrolleyHoistsWithoutFlinging(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos aRel = new BlockPos(1, 6, 5);
        BlockPos bRel = new BlockPos(9, 6, 5);
        helper.setBlock(aRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(bRel, AllBlocks.CHAIN_CONVEYOR.getDefaultState());
        helper.setBlock(aRel.above(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(com.simibubi.create.content.kinetics.motor.CreativeMotorBlock.FACING, Direction.DOWN));
        BlockPos a = helper.absolutePos(aRel);
        BlockPos b = helper.absolutePos(bRel);
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(5, 2, 5));
        com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity[] trolley = new com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity[1];
        double[] fastest = {0.0};
        Vector3d[] last = {null};
        Vec3[] startedAt = {null};
        helper.runAfterDelay(5, () -> {
            var aBe = (com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity) level.getBlockEntity(a);
            var bBe = (com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity) level.getBlockEntity(b);
            if (!bBe.addConnectionTo(a) || !aBe.addConnectionTo(b)) {
                helper.fail("Could not connect the chain conveyors");
            }
            // slow, so what moves the body fast can only be the hoist
            ((CreativeMotorBlockEntity) level.getBlockEntity(a.above())).generatedSpeed.setValue(16);
        });
        helper.runAfterDelay(30, () -> {
            var aBe = (com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity) level.getBlockEntity(a);
            ServerSubLevel torso = body(level, cow, cow.rootBone);
            if (torso == null) {
                helper.fail("No torso");
                return;
            }
            aBe.prepareStats();
            var cursor = new com.avicagan.bloodandbones.carcass.trolley.ChainCursor(a, b.subtract(a), 0.5f, aBe.reversed);
            trolley[0] = com.avicagan.bloodandbones.carcass.trolley.ShackleTrolleyEntity.create(com.avicagan.bloodandbones.registry.BBEntities.SHACKLE_TROLLEY.get(),
                    level, cursor, cow, torso);
            level.addFreshEntity(trolley[0]);
            startedAt[0] = trolley[0].position();
        });
        helper.onEachTick(() -> {
            ServerSubLevel torso = body(level, cow, cow.rootBone);
            if (trolley[0] == null || torso == null) {
                return;
            }
            Vector3d now = new Vector3d(torso.logicalPose().position());
            if (last[0] != null) {
                fastest[0] = Math.max(fastest[0], now.distance(last[0]) * 20.0);
            }
            last[0] = now;
            if (helper.getTick() == 30 + 160) {
                Vec3 anchor = trolley[0].anchor();
                double gap = anchor == null ? Double.NaN : torso.logicalPose().transformPosition(trolley[0].anchorPlot(), new Vector3d()).distance(anchor.x, anchor.y, anchor.z);
                double moved = trolley[0].position().distanceTo(startedAt[0]);
                BloodAndBones.LOGGER.info("[unproven] trolley hoist: fastest {} blocks a second, {} from the chain point, the trolley {} along", fastest[0], gap, moved);
                if (fastest[0] > 10.0) {
                    helper.fail("The trolley should hoist the cow up, not fling it: its torso moved at " + fastest[0] + " blocks a second");
                    return;
                }
                if (trolley[0].isRemoved() || !(gap <= 0.35)) {
                    helper.fail("The cow should hang from the trolley, it is " + gap + " blocks off");
                    return;
                }
                if (moved < 0.5) {
                    helper.fail("The trolley should carry the cow on along the chain once it is up, it went " + moved);
                    return;
                }
                trolley[0].dropCarcass(level);
                helper.succeed();
            }
        });
    }

    // ---- a skeleton cannot be skinned (brief: "A skeleton has no blood and no hide")

    /** Flensing Knife strokes on a skeleton do nothing: no hide, no bare flesh, not skinned. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void skeletonCannotBeSkinned(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CarcassSavedData.Carcass skeleton = carcass(helper, EntityType.SKELETON, new BlockPos(5, 2, 5));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.FLENSING_KNIFE.get()));
        CarcassLook before = skeleton.look;
        helper.runAfterDelay(20, () -> {
            for (int i = 0; i < CarcassButchery.STROKES_TO_SKIN * 2; i++) {
                if (CarcassButchery.skin(level, player, skeleton, null)) {
                    helper.fail("A Flensing Knife stroke on a skeleton should do nothing (stroke " + (i + 1) + ")");
                    return;
                }
            }
            if (skeleton.skinned || !skeleton.look.equals(before) || skeleton.look.texture().equals(CarcassLook.FLESH)) {
                helper.fail("A skeleton has no hide: it should not be skinned, look " + skeleton.look);
            }
        });
        helper.runAfterDelay(25, () -> {
            AABB around = new AABB(helper.absolutePos(new BlockPos(5, 2, 5))).inflate(3.0);
            if (!level.getEntitiesOfClass(ItemEntity.class, around, item -> item.getItem().is(BBItems.RAW_HIDE.get())).isEmpty()) {
                helper.fail("A skeleton should give no hide");
            }
            helper.succeed();
        });
    }

    // ---- blood never makes a source block (ARCHITECTURE 8: "finite by default")

    /**
     * The trick that makes water endless: a source at each end of a three-block trench. The middle fills with
     * flowing blood (and Soul Blood), never a new source.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void bloodNeverMakesASourceBlock(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.level.material.Fluid[] fluids = {BBFluids.blood(), BBFluids.soulBlood()};
        for (int i = 0; i < fluids.length; i++) {
            int z = 3 + i * 4;
            // a stone trench one block wide, three long, with a floor under it
            for (int x = 2; x <= 6; x++) {
                for (int dz = -1; dz <= 1; dz++) {
                    helper.setBlock(new BlockPos(x, 2, z + dz), Blocks.STONE);
                }
            }
            for (int x = 3; x <= 5; x++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
            helper.setBlock(new BlockPos(3, 2, z), fluids[i].defaultFluidState().createLegacyBlock());
            helper.setBlock(new BlockPos(5, 2, z), fluids[i].defaultFluidState().createLegacyBlock());
        }
        // blood flows every 15 ticks, Soul Blood every 20: give each several of its steps
        helper.runAfterDelay(150, () -> {
            for (int i = 0; i < fluids.length; i++) {
                FluidState middle = level.getFluidState(helper.absolutePos(new BlockPos(4, 2, 3 + i * 4)));
                if (!middle.getType().isSame(fluids[i])) {
                    helper.fail("The middle of the trench should have filled with " + fluids[i] + ", holds " + middle);
                    return;
                }
                if (middle.isSource()) {
                    helper.fail(fluids[i] + " between two sources should stay flowing, never become a source");
                    return;
                }
            }
            helper.succeed();
        });
    }

    // ---- the Deglover skins a single limb (brief: "Strips the hide off a whole carcass or a single limb")

    /** A cow's leg put down on its own over a turning Deglover comes off skinned: bare flesh. */
    @GameTest(template = "empty", timeoutTicks = 900)
    public static void degloverSkinsASingleLimb(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos motor = new BlockPos(5, 2, 5);
        BlockPos machine = motor.above();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(machine.offset(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(machine, BBBlocks.DEGLOVER.getDefaultState());
        if (level.getBlockEntity(helper.absolutePos(motor)) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(32);
        }
        Rig rig = RigManager.forEntity(COW, false).orElse(null);
        if (rig == null || rig.bone("left_front_leg").isEmpty()) {
            helper.fail("No cow rig with a left front leg");
            return;
        }
        CarcassSavedData.Carcass leg = CarcassAssembler.assemblePiece(level, rig, rig.bone("left_front_leg").get(),
                new CarcassLook(ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of()), 1.0F,
                Vec3.atBottomCenterOf(helper.absolutePos(machine.above())), 0.0F);
        if (leg == null) {
            helper.fail("Could not put the leg down");
            return;
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(CarcassSavedData.get(level).carcass(leg.id) != null, "the leg's record is gone");
            helper.assertTrue(leg.skinned && leg.look.texture().equals(CarcassLook.FLESH), "the leg is not skinned yet");
        });
    }

    // ---- a Guillotine's limb goes all the way to a minion and to a wall hook (brief: "output feeds minion parts and mounted-limb decoration")

    /**
     * A Guillotine takes two legs off a cow. Each lies on the floor as a piece of its own; a player picks both up.
     * One hangs on a Butcher's Hook on a wall; the other goes into a minion's frame on the Surgery Table and the
     * minion wakes wearing it. (The Guillotine leaves the legs on the floor: nothing but a player takes them further.)
     */
    @GameTest(template = "empty", timeoutTicks = 1600)
    public static void guillotineLimbGoesToAMinionAndAWallHook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos motor = new BlockPos(5, 2, 5);
        BlockPos machine = motor.above();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.setBlock(machine.offset(x, 0, z), Blocks.STONE);
            }
        }
        helper.setBlock(motor, AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.UP));
        helper.setBlock(machine, BBBlocks.GUILLOTINE.getDefaultState());
        if (level.getBlockEntity(helper.absolutePos(motor)) instanceof CreativeMotorBlockEntity creative) {
            creative.generatedSpeed.setValue(64);
        }
        Set<UUID> before = known(level);
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, machine.above());
        // the wall hook and the table, off to the side
        BlockPos wall = new BlockPos(1, 2, 9);
        BlockPos hookAt = wall.east();
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(hookAt, BBBlocks.BUTCHER_HOOK.getDefaultState().setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        BlockPos tableAt = new BlockPos(9, 2, 1);
        helper.setBlock(tableAt, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, TableAttachment.ASSEMBLY));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        // the minion's maker stands by the table: a minion follows its maker
        player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(tableAt.south())));
        Vec3 blade = Vec3.atCenterOf(helper.absolutePos(machine));
        int[] offAt = {-1};
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0]) {
                return;
            }
            List<CarcassSavedData.Carcass> legs = madeNear(level, before, COW, blade, 4.0).stream()
                    .filter(c -> c.rootBone.contains("leg") && c.bones.size() == 1).toList();
            if (legs.size() < 2) {
                return;
            }
            if (offAt[0] < 0) {
                offAt[0] = (int) helper.getTick();
                return;
            }
            // let the second one land before picking them up
            if (helper.getTick() < offAt[0] + 20) {
                return;
            }
            done[0] = true;
            if (cow.joints.stream().noneMatch(joint -> joint.child().equals("head"))) {
                helper.fail("The Guillotine should never take the head");
                return;
            }
            // picked up by hand: each becomes a piece item of the cow's leg
            List<ItemStack> pieces = new java.util.ArrayList<>();
            for (CarcassSavedData.Carcass leg : legs.subList(0, 2)) {
                String bone = leg.rootBone;
                if (!CarcassButchery.pickUp(level, player, leg, bone)) {
                    helper.fail("A cow's leg the Guillotine took off should be light enough to pick up");
                    return;
                }
                ItemStack carried = ItemStack.EMPTY;
                for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                    ItemStack stack = player.getInventory().getItem(slot);
                    CarcassPieceItem.Piece piece = CarcassPieceItem.piece(stack);
                    if (piece != null && piece.entity().equals(COW) && piece.bone().equals(bone)) {
                        carried = stack.copy();
                        player.getInventory().setItem(slot, ItemStack.EMPTY);
                        break;
                    }
                }
                if (carried.isEmpty()) {
                    helper.fail("Picking up the " + bone + " should give its piece");
                    return;
                }
                pieces.add(carried);
            }
            // one on the wall hook
            ButcherHookBlockEntity hook = (ButcherHookBlockEntity) level.getBlockEntity(helper.absolutePos(hookAt));
            if (!hook.put(pieces.get(0)) || CarcassPieceItem.piece(hook.specimen()) == null
                    || !CarcassPieceItem.piece(hook.specimen()).bone().equals(CarcassPieceItem.piece(pieces.get(0)).bone())) {
                helper.fail("The wall hook should take the Guillotine's leg");
                return;
            }
            // the other into a minion: a cow's torso for the frame, the leg fitted, a bucket of blood to wake it
            SurgeryTableBlockEntity table = (SurgeryTableBlockEntity) level.getBlockEntity(helper.absolutePos(tableAt));
            ItemStack torso = new ItemStack(BBItems.CARCASS_PIECE.get());
            torso.set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(COW, "body", ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"),
                    List.of(), 1.0F, false, Map.of(), 0.0F, 0.0F, 0.0F, false));
            if (!MinionAssembly.layDown(table, torso, level)) {
                helper.fail("A cow's torso should lie down as a frame");
                return;
            }
            String legBone = CarcassPieceItem.piece(pieces.get(1)).bone();
            var problem = MinionAssembly.fit(level, table, pieces.get(1));
            if (problem != null) {
                helper.fail("The Guillotine's " + legBone + " should fit the frame: " + problem.getString());
                return;
            }
            MinionEntity minion = MinionAssembly.wake(level, player, table, new ItemStack(BBFluids.BLOOD.getBucket().get()));
            if (minion == null) {
                helper.fail("A bucket of blood should wake the minion");
                return;
            }
            MinionBuild build = minion.build().orElse(null);
            boolean wearsIt = build != null && build.parts().stream().anyMatch(f -> f.piece().entity().equals(COW) && f.piece().bone().equals(legBone));
            // it is done with: it should not wander into the next test
            minion.discard();
            if (!wearsIt) {
                helper.fail("The minion should walk on the Guillotine's " + legBone + ": " + build);
                return;
            }
            helper.succeed();
        });
    }

    // ---- a kill without the Meat Hook leaves no carcass (brief: "Killed with the Meat Hook -> intact")

    /** A pig killed with a sword, one killed with a bare hand and one that just dies: each leaves pork, never a carcass. */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void killWithoutTheMeatHookLeavesNoCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<UUID> before = known(level);
        Player swordsman = helper.makeMockPlayer(GameType.SURVIVAL);
        swordsman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        Player boxer = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig[] pigs = {helper.spawn(EntityType.PIG, new BlockPos(2, 2, 5)), helper.spawn(EntityType.PIG, new BlockPos(5, 2, 5)),
                helper.spawn(EntityType.PIG, new BlockPos(8, 2, 5))};
        for (Pig pig : pigs) {
            pig.setNoAi(true);
        }
        Vec3[] where = new Vec3[pigs.length];
        helper.runAfterDelay(5, () -> {
            for (int i = 0; i < pigs.length; i++) {
                where[i] = pigs[i].position();
            }
            pigs[0].hurt(level.damageSources().playerAttack(swordsman), 1000.0F);
            pigs[1].hurt(level.damageSources().playerAttack(boxer), 1000.0F);
            pigs[2].hurt(level.damageSources().generic(), 1000.0F);
        });
        helper.runAfterDelay(40, () -> {
            for (int i = 0; i < pigs.length; i++) {
                if (!pigs[i].isRemoved()) {
                    helper.fail("Pig " + i + " should have died and gone");
                    return;
                }
                if (!madeNear(level, before, ResourceLocation.withDefaultNamespace("pig"), where[i], 3.0).isEmpty()) {
                    helper.fail("Pig " + i + " was not killed with the Meat Hook: it should leave no carcass");
                    return;
                }
                AABB around = new AABB(BlockPos.containing(where[i])).inflate(1.5);
                if (level.getEntitiesOfClass(ItemEntity.class, around, item -> item.getItem().is(Items.PORKCHOP)).isEmpty()) {
                    helper.fail("Pig " + i + " should drop its pork as usual");
                    return;
                }
            }
            helper.succeed();
        });
    }

    // ---- a piece rides a belt (brief: "Carcasses on belts, in vaults, on contraptions - all should behave as ordinary items")

    /**
     * A leg cut off a cow is picked up and dropped on a running belt: it rides the belt to the end and falls off
     * there, still that cow's leg with its blood and freshness.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void pieceRidesABelt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = new BlockPos(2, 2, 3);
        BlockPos end = new BlockPos(7, 2, 3);
        helper.setBlock(start, AllBlocks.SHAFT.getDefaultState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        helper.setBlock(end, AllBlocks.SHAFT.getDefaultState().setValue(ShaftBlock.AXIS, Direction.Axis.Z));
        BeltConnectorItem.createBelts(level, helper.absolutePos(start), helper.absolutePos(end));
        helper.setBlock(start.north(), AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(DirectionalKineticBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(helper.absolutePos(start.north())) instanceof CreativeMotorBlockEntity motor) {
            motor.generatedSpeed.setValue(64);
        }
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(5, 2, 8));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack[] leg = {ItemStack.EMPTY};
        BlockPos[] from = new BlockPos[1];
        BlockPos[] to = new BlockPos[1];
        helper.runAfterDelay(20, () -> {
            CarcassButchery.sever(level, cow, "right_front_leg", null);
            Vec3 lay = Vec3.atCenterOf(helper.absolutePos(new BlockPos(5, 2, 8)));
            CarcassSavedData.Carcass piece = CarcassSavedData.get(level).all().stream()
                    .filter(c -> c.rootBone.equals("right_front_leg") && c.entity.equals(COW) && c.bones.size() == 1)
                    .filter(c -> {
                        Vector3d p = CarcassAssembler.boneWorldPosition(level, c, c.rootBone);
                        return p != null && p.distance(lay.x, p.y, lay.z) < 3.0;
                    }).findFirst().orElse(null);
            if (piece == null || !CarcassButchery.pickUp(level, player, piece, piece.rootBone)) {
                helper.fail("The cut-off leg should be picked up as a piece");
                return;
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                if (CarcassPieceItem.piece(player.getInventory().getItem(slot)) != null) {
                    leg[0] = player.getInventory().removeItemNoUpdate(slot);
                    break;
                }
            }
            if (leg[0].isEmpty()) {
                helper.fail("No piece in hand after picking the leg up");
                return;
            }
            // dropped onto the end the belt runs from (which way a belt runs depends on how its shaft turns)
            if (!(level.getBlockEntity(helper.absolutePos(start)) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity belt) || belt.getSpeed() == 0) {
                helper.fail("The belt should be running");
                return;
            }
            boolean east = belt.getMovementFacing() == Direction.EAST;
            from[0] = east ? start : end;
            to[0] = east ? end.east() : start.west();
            Vec3 drop = Vec3.atBottomCenterOf(helper.absolutePos(from[0])).add(0.0, 0.6, 0.0);
            ItemEntity item = new ItemEntity(level, drop.x, drop.y, drop.z, leg[0].copy());
            item.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(item);
        });
        boolean[] rode = {false};
        helper.onEachTick(() -> {
            if (leg[0].isEmpty() || rode[0]) {
                return;
            }
            // somewhere along the belt, carried: in a belt segment's inventory
            for (int x = start.getX(); x <= end.getX(); x++) {
                if (level.getBlockEntity(helper.absolutePos(new BlockPos(x, 2, 3))) instanceof com.simibubi.create.content.kinetics.belt.BeltBlockEntity belt
                        && belt.isController() && belt.getInventory() != null
                        && belt.getInventory().getTransportedItems().stream().anyMatch(t -> CarcassPieceItem.piece(t.stack) != null)) {
                    rode[0] = true;
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(rode[0], "the piece never got onto the belt");
            // off the far end, on the floor just past it
            AABB past = new AABB(helper.absolutePos(to[0])).expandTowards(0.0, 1.0, 0.0);
            List<ItemEntity> off = level.getEntitiesOfClass(ItemEntity.class, past.inflate(1.0), item -> CarcassPieceItem.piece(item.getItem()) != null);
            helper.assertTrue(!off.isEmpty(), "the piece has not come off the end of the belt yet");
            CarcassPieceItem.Piece was = CarcassPieceItem.piece(leg[0]);
            CarcassPieceItem.Piece is = CarcassPieceItem.piece(off.get(0).getItem());
            helper.assertTrue(is.equals(was), "the piece should come off the belt as it went on: " + is + " was " + was);
        });
    }

    // ---- the Magnet Coil at high spool draws in a carcass (brief: "At high ramp it pulls carcasses")

    private static void wearBrass(Player player, Module module) {
        ItemStack tank = new ItemStack(BBItems.backtank(BacktankTier.IRON));
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.soulBlood(), 2000));
        player.setItemSlot(EquipmentSlot.CHEST, tank);
        ItemStack arm = new ItemStack(BBItems.HYDRAULIC_ARM.get());
        Modules.set(arm, List.of(module));
        BodyEffects.body(player).fit(mainArm(player), arm);
        BodyEffects.refresh(player);
    }

    private static BodyPart mainArm(Player player) {
        return BodyEffects.armFor(player, InteractionHand.MAIN_HAND);
    }

    /**
     * A cow lying seven blocks off. The Magnet Coil held low leaves it where it is; held past three quarters of full
     * spool it draws the carcass in.
     * <p>
     * At full spool the coil reaches 16 blocks, further than an ordinary test area and the gap to the next: in one it
     * drew in the carcasses and items of the tests beside it. So this test has an area 33 blocks across and stands in
     * the middle of it, where all it reaches is its own.
     */
    @GameTest(template = "empty_wide", timeoutTicks = 300)
    public static void magnetCoilAtHighSpoolDrawsInACarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (ModuleActions.MAGNET_RADIUS + ModuleActions.MAGNET_RAMP > 16.0) {
            helper.fail("The coil now reaches past this test's own area: widen the area, or it pulls on the tests beside it");
            return;
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(16.5, 2.0, 16.5)));
        wearBrass(player, Module.MAGNET_COIL);
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(23, 2, 16));
        double[] distance = new double[3];
        int[] held = {-1};
        java.util.function.DoubleSupplier away = () -> {
            Vector3d at = CarcassAssembler.boneWorldPosition(level, cow, cow.rootBone);
            return at == null ? Double.NaN : at.distance(player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ());
        };
        // let it land and lie still first
        helper.runAfterDelay(100, () -> {
            distance[0] = away.getAsDouble();
            if (!Throttle.press(player, mainArm(player), 0)) {
                helper.fail("The throttle should take the Magnet Coil");
                return;
            }
            held[0] = (int) helper.getTick();
        });
        helper.onEachTick(() -> {
            if (held[0] < 0) {
                return;
            }
            // a mock player never ticks: drive the throttle as the player's own tick would
            Throttle.tick(player);
            ModuleActions.tick(player);
            long ticks = helper.getTick() - held[0];
            // just short of three quarters of full spool: still only items
            if (ticks == (long) (Throttle.SPOOL_TICKS * ModuleActions.MAGNET_CARCASS) - 2) {
                distance[1] = away.getAsDouble();
            } else if (ticks == Throttle.SPOOL_TICKS + 40) {
                distance[2] = away.getAsDouble();
                Throttle.release(player, false);
                BloodAndBones.LOGGER.info("[unproven] magnet: cow {} blocks off, {} at low spool, {} after two seconds at full", distance[0], distance[1], distance[2]);
                if (Math.abs(distance[1] - distance[0]) > 0.3) {
                    helper.fail("Below three quarters of full spool the coil should leave carcasses alone: " + distance[0] + " to " + distance[1]);
                    return;
                }
                if (!(distance[2] < distance[1] - 1.5)) {
                    helper.fail("At full spool the coil should draw the cow in: " + distance[1] + " to " + distance[2] + " blocks off");
                    return;
                }
                helper.succeed();
            }
        });
    }

    // ---- the Grappling Spool hands a carcass to the drag tether (brief: "reuses the meat hook tether")

    /**
     * A light carcass lying still six blocks ahead is hit by the Grappling Spool: it is reeled in, and once it is at
     * hand the Meat Hook's own drag has hold of it.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void grapplingSpoolHandsACarcassToTheDrag(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 2.0, 5.5)));
        wearBrass(player, Module.GRAPPLING_SPOOL);
        CarcassSavedData.Carcass cow = carcass(helper, EntityType.COW, new BlockPos(7, 2, 5));
        boolean[] fired = {false};
        helper.runAfterDelay(100, () -> {
            if (!cow.resting) {
                BloodAndBones.LOGGER.info("[unproven] spool: the cow is not resting yet when fired");
            }
            // look at the middle of its torso
            Vector3d at = CarcassAssembler.boneWorldPosition(level, cow, cow.rootBone);
            Vec3 eye = player.getEyePosition();
            Vec3 to = new Vec3(at.x - eye.x, at.y - eye.y, at.z - eye.z);
            float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z)));
            player.moveTo(player.getX(), player.getY(), player.getZ(), yaw, pitch);
            if (!Throttle.press(player, mainArm(player), 0)) {
                helper.fail("The throttle should take the Grappling Spool");
                return;
            }
            Throttle.release(player, true);
            fired[0] = true;
        });
        helper.onEachTick(() -> {
            if (!fired[0]) {
                return;
            }
            ModuleActions.tick(player);
            if (CarcassDrag.isDragging(player)) {
                CarcassDrag.tick(level, player);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(fired[0], "not fired yet");
            CarcassDrag.Drag drag = CarcassDrag.current(player);
            helper.assertTrue(drag != null, "the carcass has not been handed to the drag yet");
            helper.assertTrue(drag.carcass.equals(cow.id), "the drag should hold the reeled-in cow");
            CarcassDrag.stop(level, player);
            ModuleActions.clear(player);
        });
    }
}
