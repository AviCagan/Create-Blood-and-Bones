package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.network.OrganActivatePayload;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.parts.effect.CooledCrustBlock;
import com.avicagan.bloodandbones.parts.effect.RangedContent;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Ranged group's effects (docs/ARCHITECTURE-PROPOSAL.md section 15.8): a minion's ranged attack from its arms, the
 * Bleeding effect (and Leaking, on what has no blood), the web and lava crust that wear away again, a costed fireball, a
 * hitscan that stops at the first creature or wall, a squid organ's ink, a thief who never robs players, and vanilla's
 * damage_item refused. Traits and mobs made for a test live under ids of their own ({@link TestTraits}).
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class RangedEffectTests {
    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static PieceRef piece(String mob, String bone) {
        return new PieceRef(ResourceLocation.withDefaultNamespace(mob), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + mob + ".png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build) {
        ServerLevel level = helper.getLevel();
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos at = helper.absolutePos(pos);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(helper.makeMockPlayer(GameType.SURVIVAL), at, build, 1000.0F);
        level.addFreshEntity(minion);
        return minion;
    }

    /** Stands a player at a point of the test (feet there), for the key to aim from. */
    private static Player standing(GameTestHelper helper, GameType type, Vec3 feet) {
        Player player = helper.makeMockPlayer(type);
        Vec3 at = helper.absoluteVec(feet);
        player.setPos(at.x, at.y, at.z);
        if (type == GameType.CREATIVE) {
            // a mock player is never told its game mode's abilities: this is what spares a creative player the blood
            player.getAbilities().instabuild = true;
        }
        return player;
    }

    /** Turns a player to look level along +x (a player's look follows its head, which a mock player never turns itself). */
    private static void facingEast(Player player) {
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F);
        player.setXRot(0.0F);
    }

    /** A made-up mob whose helmet has these traits and nothing else. */
    private static ResourceLocation helmetMob(GameTestHelper helper, String path, String... traits) {
        StringBuilder list = new StringBuilder();
        for (String t : traits) {
            list.append(list.isEmpty() ? "" : ", ").append('"').append(t).append('"');
        }
        return TestTraits.mob(helper, path, "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [" + list + "]}}}}");
    }

    /**
     * A minion with a skeleton's arms (the skeleton's own data gives them Bowman, a passive arrow shot) fights at a
     * distance: vanilla's ranged goal brings it in range and it looses an arrow at the pig.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void skeletonArmsShoot(GameTestHelper helper) {
        MinionBuild build = MinionBuild.of(piece("skeleton", "body")).with("head", piece("skeleton", "head"))
                .with("right_arm", piece("skeleton", "right_arm")).with("left_arm", piece("skeleton", "left_arm"));
        MinionEntity minion = minion(helper, new BlockPos(1, 2, 5), build);
        if (ActiveTraits.of(minion).level(bb("bowman")) < 1) {
            helper.fail("Skeleton arms should make a minion a Bowman: " + ActiveTraits.of(minion).entries());
            return;
        }
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(8, 2, 5));
        pig.setNoAi(true);
        float start = pig.getHealth();
        minion.setTarget(pig);
        helper.succeedWhen(() -> {
            List<Arrow> arrows = helper.getLevel().getEntitiesOfClass(Arrow.class, minion.getBoundingBox().inflate(16.0), a -> a.getOwner() == minion);
            if (arrows.isEmpty() && pig.getHealth() >= start) {
                minion.setTarget(pig);
                helper.fail("The minion has not shot at the pig yet");
            }
            arrows.forEach(Entity::discard);
            minion.discard();
            pig.discard();
        });
    }

    /**
     * Bleeding takes half a heart every two seconds (three wounds in five seconds), as bleeding damage; bleeding hard
     * (the third level) it leaves a blood stain under the cow every time.
     */
    @GameTest(template = "empty", timeoutTicks = 140)
    public static void bleedingDamagesOverTime(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(3, 2, 3));
        cow.setNoAi(true);
        Cow deep = helper.spawn(EntityType.COW, new BlockPos(7, 2, 7));
        deep.setNoAi(true);
        float start = cow.getHealth();
        // wounds with 160, 120 and 80 ticks left: 10, 50 and 90 ticks from now
        cow.addEffect(new MobEffectInstance(RangedContent.BLEEDING, 170, 0));
        deep.addEffect(new MobEffectInstance(RangedContent.BLEEDING, 100, 2));
        helper.runAfterDelay(100, () -> {
            float lost = start - cow.getHealth();
            if (lost < 0.99F || lost > 1.51F) {
                helper.fail("Half a heart every two seconds should take 1 to 1.5 health in five seconds, took " + lost);
                return;
            }
            if (cow.getLastDamageSource() == null || !cow.getLastDamageSource().is(RangedContent.BLEEDING_DAMAGE)) {
                helper.fail("The cow should have been hurt by bleeding: " + cow.getLastDamageSource());
                return;
            }
            if (!helper.getBlockState(new BlockPos(7, 2, 7)).is(BBBlocks.BLOOD_STAIN.get())) {
                helper.fail("A cow bleeding hard should leave blood under it");
                return;
            }
            cow.discard();
            deep.discard();
            helper.succeed();
        });
    }

    /**
     * A skeleton has no blood: bleeding, it leaks (it is hurt just the same) and leaves no stain. And the effect is called
     * Leaking in bloodless mode.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void bleedingBloodlessNoStain(GameTestHelper helper) {
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(4, 2, 4));
        skeleton.setNoAi(true);
        skeleton.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        float start = skeleton.getHealth();
        skeleton.addEffect(new MobEffectInstance(RangedContent.BLEEDING, 60, 2));
        helper.runAfterDelay(50, () -> {
            if (start - skeleton.getHealth() < 1.0F) {
                helper.fail("A leaking skeleton should be hurt as a bleeding cow is: " + (start - skeleton.getHealth()));
                return;
            }
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(6, 3, 6)))) {
                if (helper.getLevel().getBlockState(pos).is(BBBlocks.BLOOD_STAIN.get())) {
                    helper.fail("A skeleton has no blood to stain the ground with, at " + pos);
                    return;
                }
            }
            try (var in = BloodAndBones.class.getResourceAsStream("/assets/bloodandbones/lang/en_us.json")) {
                JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!"Bleeding".equals(lang.get("effect.bloodandbones.bleeding").getAsString())
                        || !"Leaking".equals(lang.get("bloodless.effect.bloodandbones.bleeding").getAsString())) {
                    helper.fail("Bleeding should be called Leaking in bloodless mode");
                    return;
                }
            } catch (Exception e) {
                helper.fail("Could not read the language file: " + e);
                return;
            }
            skeleton.discard();
            helper.succeed();
        });
    }

    /**
     * A helmet's Web Shot, fired with the key at a pig: it stings, and leaves a temporary web round the pig's feet that is
     * still there three seconds later and gone after its five.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void webShotPlacesTemporaryWebThatDecays(GameTestHelper helper) {
        ResourceLocation mob = helmetMob(helper, "ranged_web_shot", "bloodandbones:web_shot");
        Player player = standing(helper, GameType.CREATIVE, new Vec3(1.5, 2.0, 5.5));
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(6, 2, 5));
        pig.setNoAi(true);
        float start = pig.getHealth();
        player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
        OrganActivatePayload.handle(player);
        BlockPos web = pig.blockPosition();
        ServerLevel level = helper.getLevel();
        if (!level.getBlockState(web).is(RangedContent.TEMPORARY_WEB.get()) || pig.getHealth() >= start) {
            helper.fail("The web shot should sting the pig and web it in: " + level.getBlockState(web) + ", " + pig.getHealth());
            return;
        }
        helper.runAfterDelay(60, () -> {
            if (!level.getBlockState(web).is(RangedContent.TEMPORARY_WEB.get())) {
                helper.fail("The web should last its five seconds, not three");
            }
        });
        helper.succeedWhen(() -> {
            if (level.getBlockState(web).is(RangedContent.TEMPORARY_WEB.get())) {
                helper.fail("The web should wear away after five seconds");
            }
            pig.discard();
        });
    }

    /**
     * Lava Wader on the edge of a lava pool: the lava round the wearer's feet crusts over into Cooled Crust, and the crust
     * melts back to lava (every block of the pool lava again) within a few seconds.
     */
    @GameTest(template = "empty", timeoutTicks = 320)
    public static void lavaWaderCoolsLavaThatMeltsBack(GameTestHelper helper) {
        // a pool of lava five by five on the floor, walled in stone
        for (int x = 3; x <= 9; x++) {
            for (int z = 2; z <= 8; z++) {
                boolean pool = x >= 4 && x <= 8 && z >= 3 && z <= 7;
                helper.setBlock(new BlockPos(x, 2, z), pool ? Blocks.LAVA : Blocks.STONE);
            }
        }
        ResourceLocation mob = helmetMob(helper, "ranged_lava_wader", "bloodandbones:lava_wader");
        Player player = standing(helper, GameType.SURVIVAL, new Vec3(3.5, 3.0, 5.5));
        player.setOnGround(true);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        TraitEvents.tick(player);
        int crust = 0;
        for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(4, 2, 3)), helper.absolutePos(new BlockPos(8, 2, 7)))) {
            if (helper.getLevel().getBlockState(pos).is(RangedContent.COOLED_CRUST.get())) {
                crust++;
            }
        }
        if (crust < 4) {
            helper.fail("The lava at the wader's feet should crust over: " + crust + " blocks did");
            return;
        }
        helper.succeedWhen(() -> {
            for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(4, 2, 3)), helper.absolutePos(new BlockPos(8, 2, 7)))) {
                var state = helper.getLevel().getBlockState(pos);
                if (state.is(RangedContent.COOLED_CRUST.get())) {
                    helper.fail("The crust should melt back, still at age " + state.getValue(CooledCrustBlock.AGE));
                }
                if (!state.is(Blocks.LAVA) || !state.getFluidState().isSource()) {
                    helper.fail("Every block of the pool should be lava again: " + state + " at " + pos);
                }
            }
        });
    }

    /**
     * A chestplate's Fireball costs 50 mB: with no tank it is refused and nothing flies; strapped to a tank of blood,
     * three small fireballs fly off where the wearer looks, and the tank is 50 mB lighter.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void fireballActivateCostsBlood(GameTestHelper helper) {
        ResourceLocation mob = TestTraits.mob(helper, "ranged_fireball", "{\"parts\": {\"torso\": {\"armour\": {\"replace\": true, \"add\": [\"bloodandbones:fireball\"]}}}}");
        Player player = standing(helper, GameType.SURVIVAL, new Vec3(2.5, 2.0, 5.5));
        facingEast(player);
        ItemStack chestplate = TestTraits.piece("chestplate", mob);
        player.setItemSlot(EquipmentSlot.CHEST, chestplate);
        ActiveTraits.rebuild(player);
        AABB around = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(12.0, 8.0, 12.0).inflate(8.0);
        OrganActivatePayload.handle(player);
        if (!helper.getLevel().getEntitiesOfClass(SmallFireball.class, around, f -> f.getOwner() == player).isEmpty()) {
            helper.fail("With no tank to pay from, no fireball should fly");
            return;
        }
        ItemStack tank = new ItemStack(BBItems.backtank(BacktankTier.IRON));
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.blood(), 1000));
        player.setItemSlot(EquipmentSlot.CHEST, FluidBacktankItem.strap(chestplate, tank));
        ActiveTraits.rebuild(player);
        OrganActivatePayload.handle(player);
        List<SmallFireball> fired = helper.getLevel().getEntitiesOfClass(SmallFireball.class, around, f -> f.getOwner() == player);
        int left = FluidBacktankItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
        boolean ahead = fired.stream().allMatch(f -> f.getDeltaMovement().x > 0.5);
        fired.forEach(Entity::discard);
        if (fired.size() != 3 || left != 950 || !ahead) {
            helper.fail("Paid from the tank, three fireballs should fly where the wearer looks: " + fired.size() + " fired, " + left + " mB left, ahead " + ahead);
            return;
        }
        helper.succeed();
    }

    /**
     * A hitscan hurts the first creature in its line and nothing behind it, and nothing at all through a wall: two pigs in
     * a row at eye height, a stone block put in front of them and taken away again.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void hitscanHitsFirstInLine(GameTestHelper helper) {
        ResourceLocation zap = TestTraits.trait(helper, "ranged_zap", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "activate", "effect": {"type": "bloodandbones:hitscan", "range": 16, "damage": 4}}]}""");
        ResourceLocation mob = helmetMob(helper, "ranged_zap", zap.toString());
        // eyes level with the middle of a pig
        Player player = standing(helper, GameType.CREATIVE, new Vec3(1.5, 2.45 - 1.62, 5.5));
        facingEast(player);
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        Pig near = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 5));
        Pig far = helper.spawn(EntityType.PIG, new BlockPos(7, 2, 5));
        near.setNoAi(true);
        far.setNoAi(true);
        float full = near.getMaxHealth();
        helper.setBlock(new BlockPos(3, 2, 5), Blocks.STONE);
        OrganActivatePayload.handle(player);
        if (near.getHealth() < full || far.getHealth() < far.getMaxHealth()) {
            helper.fail("A hitscan should not go through a wall");
            return;
        }
        helper.setBlock(new BlockPos(3, 2, 5), Blocks.AIR);
        OrganActivatePayload.handle(player);
        if (Math.abs(near.getHealth() - (full - 4.0F)) > 1.0E-3 || far.getHealth() < far.getMaxHealth()) {
            helper.fail("A hitscan should hurt the first pig in line by 4 and spare the one behind: " + near.getHealth() + ", " + far.getHealth());
            return;
        }
        near.discard();
        far.discard();
        helper.succeed();
    }

    /**
     * A minion with a squid's ink sac, hurt by a zombie, squirts ink: the zombie beside it is blinded; the minion and a pig
     * well out of the cloud are not.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void squidInkBlinds(GameTestHelper helper) {
        MinionBuild build = MinionBuild.of(piece("cow", "body")).with("head", piece("cow", "head"))
                .withOrgan(Optional.of(new CarcassArmour.Organ(bb("ink_sac"), ResourceLocation.withDefaultNamespace("squid"), false)));
        MinionEntity minion = minion(helper, new BlockPos(3, 2, 3), build);
        if (ActiveTraits.of(minion).level(bb("ink_cloud")) < 1) {
            helper.fail("A squid's ink sac should give a minion Ink Cloud: " + ActiveTraits.of(minion).entries());
            return;
        }
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 3));
        zombie.setNoAi(true);
        Pig far = helper.spawn(EntityType.PIG, new BlockPos(9, 2, 9));
        far.setNoAi(true);
        minion.hurt(helper.getLevel().damageSources().mobAttack(zombie), 1.0F);
        boolean blinded = zombie.hasEffect(MobEffects.BLINDNESS);
        boolean self = minion.hasEffect(MobEffects.BLINDNESS);
        boolean distant = far.hasEffect(MobEffects.BLINDNESS);
        zombie.discard();
        far.discard();
        minion.discard();
        if (!blinded || self || distant) {
            helper.fail("The ink should blind the zombie beside it (" + blinded + "), not the minion (" + self + ") nor the far pig (" + distant + ")");
            return;
        }
        helper.succeed();
    }

    /** steal_item takes a zombie's sword into the thief's inventory, and never a player's. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void stealItemNeverFromPlayer(GameTestHelper helper) {
        ResourceLocation grab = TestTraits.trait(helper, "ranged_steal", """
                {"name": "trait.bloodandbones.thief",
                 "effects": [{"trigger": "attack", "effect": {"type": "bloodandbones:vanilla", "target": "victim", "effect": {"type": "bloodandbones:steal_item"}}}]}""");
        ResourceLocation mob = helmetMob(helper, "ranged_steal", grab.toString());
        Player thief = standing(helper, GameType.SURVIVAL, new Vec3(2.5, 2.0, 2.5));
        thief.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(thief);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 2));
        zombie.setNoAi(true);
        zombie.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_SWORD));
        TraitEvents.fire(thief, Trigger.ATTACK, helper.getLevel().damageSources().playerAttack(thief), zombie, 1.0F);
        boolean robbed = zombie.getMainHandItem().isEmpty() && thief.getInventory().contains(new ItemStack(Items.IRON_SWORD));
        zombie.discard();
        if (!robbed) {
            helper.fail("The zombie's sword should be in the thief's inventory");
            return;
        }
        Player victim = standing(helper, GameType.SURVIVAL, new Vec3(4.5, 2.0, 4.5));
        victim.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        TraitEvents.fire(thief, Trigger.ATTACK, helper.getLevel().damageSources().playerAttack(thief), victim, 1.0F);
        if (!victim.getMainHandItem().is(Items.DIAMOND_SWORD) || thief.getInventory().contains(new ItemStack(Items.DIAMOND_SWORD))) {
            helper.fail("A player's sword should never be stolen");
            return;
        }
        helper.succeed();
    }

    /**
     * Flinger's throw lands after the blow's own knockback (which would otherwise flatten it): a pig struck by a Flinger III
     * wearer goes up well over the block and a half a plain blow's knockback lifts it.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void flingerThrowsUpAfterTheBlow(GameTestHelper helper) {
        ResourceLocation mob = TestTraits.mob(helper, "ranged_flinger",
                "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [{\"trait\": \"bloodandbones:flinger\", \"level\": 3}]}}}}");
        Player player = standing(helper, GameType.SURVIVAL, new Vec3(3.5, 2.0, 5.5));
        player.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", mob));
        ActiveTraits.rebuild(player);
        // with its wits about it: a mob with no AI does not move at all, pushed or not
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 5));
        double ground = pig.getY();
        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        double[] top = {ground};
        helper.onEachTick(() -> top[0] = Math.max(top[0], pig.getY()));
        helper.runAfterDelay(30, () -> {
            pig.discard();
            if (top[0] - ground < 1.5) {
                helper.fail("Flung, the pig should go well up: it rose " + (top[0] - ground));
                return;
            }
            helper.succeed();
        });
    }

    /**
     * damage_item would wear the carcass armour itself out: a trait using it is refused when read, even inside all_of
     * or as a hitscan's hit; other vanilla effects are read as they are.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void vanillaDamageItemRejected(GameTestHelper helper) {
        String[] refused = {
                "{\"type\": \"bloodandbones:vanilla\", \"effect\": {\"type\": \"minecraft:damage_item\", \"amount\": 1}}",
                "{\"type\": \"bloodandbones:vanilla\", \"effect\": {\"type\": \"minecraft:all_of\", \"effects\": ["
                        + "{\"type\": \"minecraft:ignite\", \"duration\": 1}, {\"type\": \"minecraft:damage_item\", \"amount\": 1}]}}",
                "{\"type\": \"bloodandbones:hitscan\", \"damage\": 1, \"hit\": {\"type\": \"minecraft:damage_item\", \"amount\": 1}}"};
        for (int i = 0; i < refused.length; i++) {
            try {
                TestTraits.trait(helper, "ranged_damage_item_" + i, "{\"name\": \"trait.bloodandbones.hardy\", \"effects\": [{\"trigger\": \"attack\", \"effect\": " + refused[i] + "}]}");
                helper.fail("A trait using damage_item should be refused: " + refused[i]);
                return;
            } catch (IllegalArgumentException expected) {
                if (!expected.getMessage().contains("damage_item")) {
                    helper.fail("It should be refused for its damage_item: " + expected.getMessage());
                    return;
                }
            }
        }
        try {
            TestTraits.trait(helper, "ranged_ignite", """
                    {"name": "trait.bloodandbones.hardy",
                     "effects": [{"trigger": "attack", "effect": {"type": "bloodandbones:vanilla", "target": "victim", "effect": {"type": "minecraft:ignite", "duration": 2}}}]}""");
        } catch (IllegalArgumentException e) {
            helper.fail("Any other vanilla effect should be read: " + e.getMessage());
            return;
        }
        helper.succeed();
    }
}
