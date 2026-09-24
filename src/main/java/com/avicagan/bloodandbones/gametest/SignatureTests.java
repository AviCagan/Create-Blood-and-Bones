package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionData;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.network.OrganActivatePayload;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.TraitEvents;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.parts.effect.PowerEffect;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The mobs' own data at work (docs/PARTS-AND-TRAITS.md section 8, and section 9's slice 4 tests it allows): a blaze's core
 * in a chestplate, the full zombie set, the floor under damage reductions, and signature traits reaching the minions and
 * armour made of those mobs' parts.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class SignatureTests {
    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static ResourceLocation mob(String name) {
        return ResourceLocation.withDefaultNamespace(name);
    }

    private static PieceRef ref(String entity, String bone) {
        return new PieceRef(mob(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + "/" + entity + ".png"),
                List.of(), 1.0F, false, Map.of(), false);
    }

    /** A mock player standing at this spot of the test, looking east (mock players are not in the world: they never tick). */
    private static Player standing(GameTestHelper helper, Vec3 feet) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(feet);
        player.setPos(at.x, at.y, at.z);
        player.setYRot(-90.0F);
        player.setYHeadRot(-90.0F);
        player.setXRot(0.0F);
        return player;
    }

    /** A piece of one mob with an organ of that mob fitted (the component says so: special organs have no item yet). */
    private static ItemStack withOrgan(String piece, ResourceLocation mob, String organ) {
        ItemStack stack = TestTraits.piece(piece, mob);
        CarcassArmour armour = CarcassArmourItem.armour(stack).withOrgan(Optional.of(new CarcassArmour.Organ(bb(organ), mob, false)));
        return CarcassArmourItem.make(stack, armour, PartsData.SERVER);
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

    private static boolean has(List<TraitList.Resolved> list, String trait) {
        return list.stream().anyMatch(t -> t.id().equals(bb(trait)));
    }

    /**
     * Slice 4: a blaze chestplate with its Blaze Core (spec 8.1). Fire hardly holds on the wearer: once alight they burn
     * out in well under half the time (the core's Quench, and the ember scraps' own quirk) and burning hurts a quarter less
     * (the elemental family's fireproofing); a cow chestplate beside it does neither. The core's fireball is on the Organ
     * Ability: three fireballs where the wearer looks, 50 mB from the strapped tank.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void blazeCoreChestIgnoresFire(GameTestHelper helper) {
        ResourceLocation blaze = mob("blaze");
        Player wearer = standing(helper, new Vec3(2.5, 2.0, 2.5));
        ItemStack tank = new ItemStack(BBItems.backtank(BacktankTier.IRON));
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.blood(), 1000));
        wearer.setItemSlot(EquipmentSlot.CHEST, FluidBacktankItem.strap(withOrgan("chestplate", blaze, "blaze_core"), tank));
        ActiveTraits traits = ActiveTraits.rebuild(wearer);
        Player plain = standing(helper, new Vec3(2.5, 2.0, 6.5));
        plain.setItemSlot(EquipmentSlot.CHEST, TestTraits.piece("chestplate", mob("cow")));
        ActiveTraits.rebuild(plain);
        if (traits.level(bb("fireball")) != 1 || traits.level(bb("quench")) != 1 || traits.level(bb("fireproof")) < 1) {
            helper.fail("A blaze chestplate with its core should have fireball, quench and fireproof: " + traits.entries());
            return;
        }
        wearer.igniteForSeconds(8.0F);
        plain.igniteForSeconds(8.0F);
        int burns = wearer.getRemainingFireTicks();
        int plainBurns = plain.getRemainingFireTicks();
        wearer.clearFire();
        plain.clearFire();
        if (plainBurns != 160 || burns <= 0 || burns > plainBurns / 2) {
            helper.fail("Set alight for 8 seconds, the blaze core's wearer should burn under half as long: " + burns + " ticks against " + plainBurns);
            return;
        }
        float before = wearer.getHealth();
        wearer.hurt(wearer.damageSources().onFire(), 4.0F);
        float taken = before - wearer.getHealth();
        float plainBefore = plain.getHealth();
        plain.hurt(plain.damageSources().onFire(), 4.0F);
        float plainTaken = plainBefore - plain.getHealth();
        if (plainTaken < 3.9F || Math.abs(taken - plainTaken * 0.75F) > 0.05F) {
            helper.fail("Burning should hurt the blaze chestplate's wearer a quarter less: " + taken + " against " + plainTaken);
            return;
        }
        AABB around = new AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(12.0, 8.0, 12.0).inflate(8.0);
        OrganActivatePayload.handle(wearer);
        List<SmallFireball> fired = helper.getLevel().getEntitiesOfClass(SmallFireball.class, around, f -> f.getOwner() == wearer);
        int left = FluidBacktankItem.fluid(wearer.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
        fired.forEach(Entity::discard);
        if (fired.size() != 3 || left != 950) {
            helper.fail("The Blaze Core's fireball should throw three, for 50 mB: " + fired.size() + " thrown, " + left + " mB left");
            return;
        }
        helper.succeed();
    }

    /**
     * Slice 4: a full set of zombie is the Shambler (the rotting overlay's set, spec 3.4 and 8.1): Dead Face and Brawler II,
     * with Sun Cursed and the undead's Inverted Healing, and nothing of the biped's plain set. Zombies leave the wearer be,
     * and by day under open sky the wearer catches fire, helmet and all; at night not.
     */
    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fullZombieSetKinAndSunCursed(GameTestHelper helper) {
        ResourceLocation zombieId = mob("zombie");
        Player player = standing(helper, new Vec3(2.5, 2.0, 2.5));
        for (EquipmentSlot slot : ActiveTraits.PIECES) {
            String piece = switch (slot) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                default -> "boots";
            };
            player.setItemSlot(slot, TestTraits.piece(piece, zombieId));
        }
        ActiveTraits traits = ActiveTraits.rebuild(player);
        if (traits.set().isEmpty() || !traits.set().get().name().equals("set.bloodandbones.shambler") || traits.level(bb("dead_face")) != 1
                || traits.level(bb("brawler")) != 2 || traits.level(bb("sun_cursed")) != 1 || traits.level(bb("inverted_healing")) != 1
                || traits.level(bb("sluggish")) != 0) {
            helper.fail("A full zombie set should be the Shambler, dead face and brawler II against sun cursed and inverted healing: "
                    + traits.set().map(s -> s.name()).orElse("no set") + " " + traits.entries());
            return;
        }
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 6));
        zombie.setTarget(player);
        boolean kin = zombie.getTarget() == null;
        zombie.discard();
        if (!kin) {
            helper.fail("A zombie should not set its sights on a wearer of the full zombie set");
            return;
        }
        ActiveTraits.updateDry(player);
        helper.runAfterDelay(30, () -> {
            ServerLevel level = helper.getLevel();
            if (!level.canSeeSky(player.blockPosition())) {
                helper.fail("The test needs open sky over the player");
                return;
            }
            long time = level.getDayTime();
            boolean night;
            boolean day;
            try {
                level.setDayTime(18000L);
                TraitEvents.fire(player, Trigger.TICK, null, null, 0.0F);
                night = player.isOnFire();
                level.setDayTime(6000L);
                TraitEvents.fire(player, Trigger.TICK, null, null, 0.0F);
                day = player.isOnFire();
            } finally {
                level.setDayTime(time);
            }
            if (night || !day) {
                helper.fail("The Shambler should burn by day under the sky (" + day + "), not at night (" + night + ")");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Slice 4 (spec 5.8): reductions from traits multiply, and never take a blow below a fifth: a piece that takes all harm
     * away still lets 2 of 10 through, as do two that together would let 1.5 through; only a full set's bonus may make a
     * true immunity.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void reductionFlooredAt20Percent(GameTestHelper helper) {
        ResourceLocation none = TestTraits.trait(helper, "floor_none", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "hurt", "effect": {"type": "bloodandbones:damage", "direction": "in", "multiplier": 0}}]}""");
        ResourceLocation half = TestTraits.trait(helper, "floor_half", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "hurt", "effect": {"type": "bloodandbones:damage", "direction": "in", "multiplier": 0.5}}]}""");
        ResourceLocation third = TestTraits.trait(helper, "floor_third", """
                {"name": "trait.bloodandbones.hardy",
                 "effects": [{"trigger": "hurt", "effect": {"type": "bloodandbones:damage", "direction": "in", "multiplier": 0.3}}]}""");
        ResourceLocation onePiece = TestTraits.mob(helper, "floor_piece",
                "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [\"" + none + "\"]}}}}");
        ResourceLocation twoPieces = TestTraits.mob(helper, "floor_pieces",
                "{\"parts\": {\"head\": {\"armour\": {\"replace\": true, \"add\": [\"" + half + "\"]}},"
                        + " \"torso\": {\"armour\": {\"replace\": true, \"add\": [\"" + third + "\"]}}}}");
        ResourceLocation wholeSet = TestTraits.mob(helper, "floor_set",
                "{\"full_set\": {\"replace\": true, \"name\": \"set.bloodandbones.pure\", \"bonus\": [\"" + none + "\"]}}");
        Player single = standing(helper, new Vec3(1.5, 2.0, 1.5));
        single.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", onePiece));
        Player pair = standing(helper, new Vec3(3.5, 2.0, 1.5));
        pair.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", twoPieces));
        pair.setItemSlot(EquipmentSlot.CHEST, TestTraits.piece("chestplate", twoPieces));
        Player set = standing(helper, new Vec3(5.5, 2.0, 1.5));
        set.setItemSlot(EquipmentSlot.HEAD, TestTraits.piece("helmet", wholeSet));
        set.setItemSlot(EquipmentSlot.CHEST, TestTraits.piece("chestplate", wholeSet));
        set.setItemSlot(EquipmentSlot.LEGS, TestTraits.piece("leggings", wholeSet));
        set.setItemSlot(EquipmentSlot.FEET, TestTraits.piece("boots", wholeSet));
        float[] taken = new float[3];
        Player[] players = {single, pair, set};
        for (int i = 0; i < players.length; i++) {
            ActiveTraits.rebuild(players[i]);
            float before = players[i].getHealth();
            players[i].hurt(players[i].damageSources().magic(), 10.0F);
            taken[i] = before - players[i].getHealth();
        }
        if (Math.abs(taken[0] - 2.0F) > 0.01F || Math.abs(taken[1] - 2.0F) > 0.01F || taken[2] != 0.0F) {
            helper.fail("A blow of 10 should do 2 through one piece or two (the floor), and nothing through a whole set: "
                    + taken[0] + ", " + taken[1] + ", " + taken[2]);
            return;
        }
        helper.succeed();
    }

    /**
     * Signature traits reach what is made of those mobs' parts: a zombie's torso burns in the sun on a minion and a husk's
     * does not (sunbaked); a pig's bacon fat holds a quarter more blood and a polar bear's brown fat half as much again; a
     * villager's pair of arms carries nine more stacks; a cow's rumen in a chestplate cleanses and a rabbit's foot in
     * leggings is luck and a leap.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void signatureTraitsReachTheirHosts(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        if (!has(MinionData.traits(store.resolve(mob("zombie"), false), "torso"), "sun_cursed")
                || has(MinionData.traits(store.resolve(mob("husk"), false), "torso"), "sun_cursed")) {
            helper.fail("A zombie torso should be sun cursed on a minion, a husk's not");
            return;
        }
        MinionBuild cow = MinionBuild.of(ref("cow", "body"));
        float bacon = PowerEffect.capacity(store, cow.withOrgan(Optional.of(new CarcassArmour.Organ(bb("bacon_fat"), mob("pig"), false))));
        float brown = PowerEffect.capacity(store, cow.withOrgan(Optional.of(new CarcassArmour.Organ(bb("brown_fat"), mob("polar_bear"), false))));
        if (Math.abs(bacon - 1.25F) > 1.0E-4F || Math.abs(brown - 1.5F) > 1.0E-4F || PowerEffect.capacity(store, cow) != 1.0F) {
            helper.fail("Bacon fat should hold a quarter more blood, brown fat half as much again: " + bacon + ", " + brown);
            return;
        }
        MinionEntity plain = minion(helper, new BlockPos(2, 2, 2), cow);
        MinionEntity pouched = minion(helper, new BlockPos(6, 2, 6), cow.with("right_front_leg", ref("villager", "arms")));
        int extra = pouched.slots() - plain.slots();
        plain.discard();
        pouched.discard();
        if (extra != 9) {
            helper.fail("A villager's pair of arms should carry nine more stacks, not " + extra);
            return;
        }
        Player player = standing(helper, new Vec3(4.5, 2.0, 4.5));
        player.setItemSlot(EquipmentSlot.CHEST, withOrgan("chestplate", mob("cow"), "rumen"));
        player.setItemSlot(EquipmentSlot.LEGS, withOrgan("leggings", mob("rabbit"), "rabbit_foot"));
        ActiveTraits worn = ActiveTraits.rebuild(player);
        if (worn.level(bb("cleanse")) != 1 || worn.level(bb("lucky")) != 2 || worn.level(bb("leap")) != 1) {
            helper.fail("A cow's rumen should cleanse, a rabbit's foot give Lucky II and a leap: " + worn.entries());
            return;
        }
        helper.succeed();
    }
}
