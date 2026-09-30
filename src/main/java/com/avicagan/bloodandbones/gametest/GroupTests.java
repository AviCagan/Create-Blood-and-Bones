package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassBleeding;
import com.avicagan.bloodandbones.carcass.CarcassBody;
import com.avicagan.bloodandbones.carcass.CarcassButchery;
import com.avicagan.bloodandbones.carcass.CarcassDrag;
import com.avicagan.bloodandbones.carcass.CarcassPartBlockEntity;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.carcass.WeightClass;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryManager;
import com.avicagan.bloodandbones.carcass.butchery.ButcheryTable;
import com.avicagan.bloodandbones.carcass.butchery.Yield;
import com.avicagan.bloodandbones.carcass.rig.Bone;
import com.avicagan.bloodandbones.carcass.rig.Rig;
import com.avicagan.bloodandbones.carcass.rig.RigManager;
import com.avicagan.bloodandbones.config.BBServerConfig;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.registry.BBItems;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Groups first (docs/BRIEF-AUDIT.md package 2): a mob with no rig file still becomes a carcass, built from its archetype's
 * generic body at the size of its hitbox; weight classes, rot times and butchery come from groups, a mob's own file only
 * where it differs; and the balance knobs moved into data and the server config keep their old figures.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class GroupTests {
    private static final int SETTLE_TICKS = 20;

    private static ResourceLocation id(EntityType<?> type) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type);
    }

    /** A kill as the game makes one: a stand-in player holding the Meat Hook strikes the mob dead. */
    private static void hookKill(GameTestHelper helper, LivingEntity mob) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BBItems.MEAT_HOOK.get()));
        mob.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
    }

    /** The carcass of this kind nearest a spot of the test, or null. */
    private static CarcassSavedData.Carcass carcassOf(GameTestHelper helper, EntityType<?> type, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        Vec3 at = Vec3.atCenterOf(helper.absolutePos(relative));
        CarcassSavedData.Carcass best = null;
        double near = 6.0;
        for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
            if (!carcass.entity.equals(id(type))) {
                continue;
            }
            UUID root = carcass.bones.get(carcass.rootBone);
            if (root != null && container.getSubLevel(root) instanceof ServerSubLevel body && !body.isRemoved()) {
                double d = body.logicalPose().position().distance(at.x, at.y, at.z);
                if (d < near) {
                    near = d;
                    best = carcass;
                }
            }
        }
        return best;
    }

    /** Other tests share the world: a carcass this test is done with goes, bodies and all. */
    private static void remove(ServerLevel level, CarcassSavedData.Carcass carcass) {
        com.avicagan.bloodandbones.carcass.CarcassRest.unlock(carcass);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
        for (UUID bone : List.copyOf(carcass.bones.values())) {
            if (container != null && container.getSubLevel(bone) instanceof ServerSubLevel body && !body.isRemoved()) {
                container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            }
        }
        CarcassSavedData.get(level).forget(carcass);
    }

    /**
     * The audit's first gap: a mob with no rig file died as if the Meat Hook were any weapon. A cow whose rig file is taken
     * away (for this test only, in a batch of its own that runs before any other, so no other cow is about) is killed with
     * the hook as a player kills one: it becomes a carcass of the quadruped's generic body at its hitbox's size, which bleeds,
     * weighs, and comes apart at the joints like any other. The rig file is given back however the test ends, and the hide
     * is this test's own hold, so copies of it run together (the repeat switch) each hide and give back their own.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "bloodandbones_alone_first")
    public static void mobWithNoRigFileBecomesACarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation cow = id(EntityType.COW);
        Rig own = RigManager.all().get(cow);
        if (own == null) {
            helper.fail("A cow should have a rig file");
            return;
        }
        Runnable giveBack = RigManager.hideForTest(cow, level.getServer().getTickCount() + 200);
        Mob mob;
        Vec3 stood;
        Rig generic;
        try {
            generic = RigManager.forEntity(cow).orElse(null);
            if (generic == null || !generic.fitted() || generic == own) {
                helper.fail("With no rig file a cow should get its archetype's generic body, got " + generic);
                return;
            }
            List<String> names = generic.bones().stream().map(Bone::name).toList();
            if (!names.equals(List.of("body", "head", "right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg", "tail"))) {
                helper.fail("A generic quadruped should be a body, a head, four legs and a tail, not " + names);
                return;
            }
            // the body is 0.8 of the hitbox's width across, as the generic quadruped says
            float width = EntityType.COW.getDimensions().width();
            float across = generic.bone("body").orElseThrow().boxSize().x;
            if (Math.abs(across - 0.8F * width * 16.0F) > 0.01F) {
                helper.fail("The generic body should be scaled to the cow's hitbox: " + across + " pixels across, expected " + 0.8F * width * 16.0F);
                return;
            }
            mob = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
            stood = mob.position();
            hookKill(helper, mob);
        } catch (RuntimeException e) {
            giveBack.run();
            throw e;
        }
        Rig body = generic;
        helper.runAfterDelay(SETTLE_TICKS + 10, () -> {
            try {
                CarcassSavedData.Carcass carcass = carcassOf(helper, EntityType.COW, new BlockPos(5, 2, 5));
                if (mob.isAlive() || carcass == null) {
                    helper.fail("A cow with no rig file killed with the Meat Hook should leave a carcass");
                    return;
                }
                if (RigManager.forCarcass(carcass).orElseThrow() != body || carcass.bones.size() != 7 || carcass.liveJoints.size() != 6) {
                    helper.fail("The carcass should be the generic body's seven parts and six joints: " + carcass.bones.keySet() + ", "
                            + carcass.liveJoints.size() + " joints");
                    return;
                }
                ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
                ServerSubLevel torso = (ServerSubLevel) container.getSubLevel(carcass.bones.get(carcass.rootBone));
                if (torso.logicalPose().position().distance(stood.x, stood.y, stood.z) > 2.5) {
                    helper.fail("The carcass should go down where the cow stood, not " + torso.logicalPose().position());
                    return;
                }
                float blood = CarcassBleeding.capacity(carcass);
                if (Math.abs(blood - Math.round(body.weight() * 1000.0F)) > 0.5F || blood <= 0.0F) {
                    helper.fail("It should hold its weight's worth of blood, " + Math.round(body.weight() * 1000.0F) + " mB, not " + blood);
                    return;
                }
                CarcassSavedData.Carcass leg = CarcassButchery.sever(level, carcass, "right_hind_leg", null);
                if (leg == null || !leg.rootBone.equals("right_hind_leg") || carcass.bones.containsKey("right_hind_leg")) {
                    helper.fail("A leg of the generic body should come off at its joint as a piece of its own");
                    return;
                }
                remove(level, leg);
                remove(level, carcass);
            } finally {
                giveBack.run();
            }
            // another copy of this test may still hold the cow's rig hidden; once none does, the cow is its own rig again
            if (RigManager.fileRig(cow).isPresent() && RigManager.forEntity(cow).orElse(null) != own) {
                helper.fail("Given back its rig file, a cow should be its own rig again");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * The mod's own minion is no mob to butcher: no group lists it, and by its shape it would pass for a biped. Killed with
     * the Meat Hook where the server scatters killed minions, it falls apart into what it was built of, as any other kill
     * leaves it, and leaves no fresh carcass (which would give its flesh twice over, and make a brass one bleed).
     */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void minionKilledWithTheHookLeavesNoCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation minionId = id(com.avicagan.bloodandbones.registry.BBEntities.MINION.get());
        if (RigManager.forEntity(minionId).isPresent()) {
            helper.fail("A minion should never have a carcass body");
            return;
        }
        BlockPos at = helper.absolutePos(new BlockPos(5, 2, 5));
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        maker.moveTo(at.getX() + 1.5, at.getY(), at.getZ() + 0.5);
        com.avicagan.bloodandbones.minion.MinionEntity minion = com.avicagan.bloodandbones.registry.BBEntities.MINION.get().create(level);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, com.avicagan.bloodandbones.minion.MinionBuild.of(new com.avicagan.bloodandbones.minion.PieceRef(id(EntityType.COW), "body",
                ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of(), 1.0F, false, Map.of(), false)), 500.0F);
        minion.setNoAi(true);
        level.addFreshEntity(minion);
        // the setting is the whole server's: changed and put back within this one call (the death and its drops are in it)
        BBServerConfig.MinionDeath before = BBServerConfig.MINION_DEATH.get();
        try {
            BBServerConfig.MINION_DEATH.set(BBServerConfig.MinionDeath.SCATTER);
            hookKill(helper, minion);
        } finally {
            BBServerConfig.MINION_DEATH.set(before);
        }
        helper.runAfterDelay(10, () -> {
            for (CarcassSavedData.Carcass carcass : CarcassSavedData.get(level).all()) {
                if (carcass.entity.equals(minionId)) {
                    remove(level, carcass);
                    helper.fail("A minion killed with the Meat Hook should leave no carcass");
                    return;
                }
            }
            List<net.minecraft.world.entity.item.ItemEntity> dropped = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(at).inflate(3.0));
            boolean torso = dropped.stream().anyMatch(item -> item.getItem().is(BBItems.CARCASS_PIECE.get()));
            dropped.forEach(net.minecraft.world.entity.Entity::discard);
            if (minion.isAlive() || !torso) {
                helper.fail("It should die as the server's rule says, scattering the torso it was built on (dead " + !minion.isAlive()
                        + ", torso dropped " + torso + ")");
                return;
            }
            helper.succeed();
        });
    }

    /**
     * One of the audit's named mobs with no rig: a tropical fish, whose model changes with its pattern. It is claimed by the
     * fish archetype by its spawn category (no file lists it), becomes a carcass of the fish's generic body, and butchers
     * by its group (bone meal from the fish archetype) with its one line of its own (it gives tropical fish, not cod).
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void tropicalFishBecomesACarcassAndButchersByGroup(GameTestHelper helper) {
        ResourceLocation fish = id(EntityType.TROPICAL_FISH);
        if (RigManager.fileRig(fish).isPresent()) {
            helper.fail("The tropical fish has a rig file now; this test wants a mob with none");
            return;
        }
        if (!PartsData.SERVER.resolve(fish, false).layers().contains(BloodAndBones.asResource("fish"))) {
            helper.fail("A tropical fish should be claimed by the fish archetype, got " + PartsData.SERVER.resolve(fish, false).layers());
            return;
        }
        Mob mob = helper.spawn(EntityType.TROPICAL_FISH, new BlockPos(5, 2, 5));
        hookKill(helper, mob);
        helper.runAfterDelay(SETTLE_TICKS + 10, () -> {
            CarcassSavedData.Carcass carcass = carcassOf(helper, EntityType.TROPICAL_FISH, new BlockPos(5, 2, 5));
            if (carcass == null) {
                helper.fail("A tropical fish killed with the Meat Hook should leave a carcass");
                return;
            }
            Rig rig = RigManager.forCarcass(carcass).orElseThrow();
            if (!rig.fitted() || !carcass.bones.keySet().equals(java.util.Set.of("body", "head", "tail"))) {
                helper.fail("It should be the generic fish: body, head and tail, not " + carcass.bones.keySet());
                return;
            }
            ButcheryTable table = ButcheryManager.forEntity(fish).orElse(null);
            if (table == null || ButcheryManager.fileTable(fish).isPresent()) {
                helper.fail("A tropical fish has no butchery table of its own; its groups should give it one");
                return;
            }
            List<String> body = table.part("body").stream().map(Yield::item).toList();
            if (!body.contains("minecraft:tropical_fish") || !body.contains("minecraft:bone_meal") || body.contains("minecraft:cod")) {
                helper.fail("Its body should give tropical fish (its own file) and bone meal (the fish archetype), got " + body);
                return;
            }
            remove(helper.getLevel(), carcass);
            helper.succeed();
        });
    }

    /**
     * The audit's other named gap: a baby of a kind whose rig has no baby shape died as usual. A baby wandering trader (a
     * villager's kind, and a baby can be made of one, but its rig has no baby shape) is its grown rig at half size about
     * its feet, as the game draws such a baby.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void babyWithNoBabyShapeBecomesAHalfSizeCarcass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ResourceLocation trader = id(EntityType.WANDERING_TRADER);
        Rig grown = RigManager.forEntity(trader).orElseThrow();
        if (grown.baby().isPresent()) {
            helper.fail("The wandering trader's rig has a baby shape now; this test wants one without");
            return;
        }
        Mob mob = helper.spawn(EntityType.WANDERING_TRADER, new BlockPos(5, 2, 5));
        mob.setNoAi(true);
        mob.setBaby(true);
        if (!mob.isBaby()) {
            helper.fail("A wandering trader would not be a baby");
            return;
        }
        Vec3 feet = mob.position();
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(mob, null);
        mob.discard();
        if (carcass == null || !carcass.baby) {
            helper.fail("A baby of a kind with no baby shape should still leave a carcass");
            return;
        }
        Rig small = RigManager.forCarcass(carcass).orElseThrow();
        if (small == grown || !RigManager.isBaby(small) || Math.abs(small.weight() - grown.weight() / 8.0F) > 1.0E-4F) {
            helper.fail("Its rig should be the grown one at half size, an eighth the weight: " + small.weight() + " of " + grown.weight());
            return;
        }
        for (Bone bone : grown.bones()) {
            Bone baby = small.bone(bone.name()).orElseThrow();
            Vector3f expected = bone.boxSize().mul(0.5F);
            // shrunk about the feet: the model's origin, 24 pixels above them, comes down to 12
            Vector3f pivot = new Vector3f(bone.offset()).add(0.0F, 24.0F * grown.scale(), 0.0F).mul(0.5F);
            if (baby.boxSize().distance(expected) > 1.0E-3F || baby.offset().distance(pivot) > 1.0E-3F) {
                helper.fail("Baby bone " + bone.name() + " is " + baby.boxSize() + " at " + baby.offset() + ", expected " + expected + " at " + pivot);
                return;
            }
        }
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            ServerSubLevel torso = (ServerSubLevel) container.getSubLevel(carcass.bones.get(carcass.rootBone));
            Vector3d at = torso.logicalPose().position();
            if (at.y > feet.y + 1.0 || at.distance(feet.x, feet.y, feet.z) > 2.0) {
                helper.fail("The baby's carcass should lie small where it stood, its torso is at " + at);
                return;
            }
            if (!(level.getBlockEntity(torso.getPlot().getCenterBlock()) instanceof CarcassPartBlockEntity cell) || !cell.baby()) {
                helper.fail("The baby's cells should know it is a baby, so it is drawn small");
                return;
            }
            remove(level, carcass);
            helper.succeed();
        });
    }

    /**
     * An archetype claims a mob no file lists by its shape (docs/PARTS-AND-TRAITS.md 3.1): the giant, taller than wide and
     * a monster, is a biped and becomes the generic biped at its hitbox's size; the ender dragon (no_carcass), a player and
     * an armour stand never have a body at all. Mobs the files list keep their own.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void unlistedMobsTakeTheirArchetypeByShape(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        if (!store.resolve(id(EntityType.GIANT), false).layers().contains(BloodAndBones.asResource("biped"))) {
            helper.fail("A giant should be a biped by its shape, got " + store.resolve(id(EntityType.GIANT), false).layers());
            return;
        }
        Rig giant = RigManager.forEntity(id(EntityType.GIANT)).orElse(null);
        if (giant == null || !giant.fitted() || giant.bones().size() != 6) {
            helper.fail("A giant should be the generic biped's six parts, got " + giant);
            return;
        }
        float tall = giant.bone("head").orElseThrow().offset().y;
        // the head sits at 0.77 of its height: model y runs down from 24 pixels above the feet
        float expected = 24.0F - 16.0F * 0.77F * EntityType.GIANT.getDimensions().height();
        if (Math.abs(tall - expected) > 0.01F) {
            helper.fail("A giant's generic head should sit at its hitbox's height, " + expected + ", not " + tall);
            return;
        }
        for (EntityType<?> none : List.of(EntityType.ENDER_DRAGON, EntityType.PLAYER, EntityType.ARMOR_STAND)) {
            if (RigManager.forEntity(id(none)).isPresent()) {
                helper.fail(id(none) + " should never have a carcass body");
                return;
            }
        }
        if (!store.resolve(id(EntityType.COW), false).layers().contains(BloodAndBones.asResource("quadruped"))
                || RigManager.forEntity(id(EntityType.COW)).orElseThrow().fitted()) {
            helper.fail("A cow is listed: it keeps its archetype and its own rig");
            return;
        }
        helper.succeed();
    }

    /**
     * Weight classes (package 2, for the owner: the list is in ARCHITECTURE 15.31): by size for any mob no group names one
     * for, so a modded mob has one on day one; a group or a mob's own file can name one, and then its figures drive the
     * drag, the blood, the floating and the rot time. Checked on a copy of the server's data with one class and one mob file
     * added, so no other test sees them.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void weightClassesComeFromSizeOrGroups(GameTestHelper helper) {
        Map<EntityType<?>, String> bySize = new java.util.LinkedHashMap<>();
        bySize.put(EntityType.BAT, "tiny");
        bySize.put(EntityType.CHICKEN, "small");
        bySize.put(EntityType.ZOMBIE, "medium");
        bySize.put(EntityType.COW, "large");
        bySize.put(EntityType.RAVAGER, "huge");
        bySize.put(EntityType.GHAST, "colossal");
        bySize.put(EntityType.TROPICAL_FISH, "tiny");
        for (Map.Entry<EntityType<?>, String> e : bySize.entrySet()) {
            ResourceLocation got = CarcassBody.weightClassEntry(PartsData.SERVER, id(e.getKey())).map(Map.Entry::getKey).orElse(null);
            if (!BloodAndBones.asResource(e.getValue()).equals(got)) {
                helper.fail(id(e.getKey()) + " should be " + e.getValue() + " by its size, got " + got);
                return;
            }
        }
        PartsData.Store store = new PartsData.Store();
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        for (PartsData.Kind kind : PartsData.Kind.values()) {
            Map<ResourceLocation, String> files = new HashMap<>(PartsData.SERVER.raw(kind));
            if (kind == PartsData.Kind.WEIGHT_CLASS) {
                files.put(BloodAndBones.asResource("test_lead"), "{\"drag\": 2.0, \"blood_per_weight\": 1500, \"buoyancy\": 0.3, \"rot_time\": 1234}");
            }
            if (kind == PartsData.Kind.MOB_TRAITS) {
                ResourceLocation cowFile = BloodAndBones.asResource("minecraft/cow");
                JsonObject cow = files.containsKey(cowFile) ? JsonParser.parseString(files.get(cowFile)).getAsJsonObject() : new JsonObject();
                cow.addProperty("weight_class", "bloodandbones:test_lead");
                // a rot time under a tick is refused (rot divides by it), so the class's stands
                cow.addProperty("rot_time", 0);
                files.put(cowFile, cow.toString());
            }
            store.load(kind, files, ops);
        }
        WeightClass lead = CarcassBody.weightClass(store, id(EntityType.COW));
        if (lead.drag() != 2.0F || lead.bloodPerWeight() != 1500.0F || lead.buoyancy() != 0.3F) {
            helper.fail("A cow whose own file names a class should take that class's figures, got " + lead);
            return;
        }
        if (CarcassBody.rotTime(store, id(EntityType.COW)) != 1234) {
            helper.fail("With no rot time of its own (a 0 refused) or its groups', a cow should rot in its class's time, not " + CarcassBody.rotTime(store, id(EntityType.COW)));
            return;
        }
        if (CarcassBody.rotTime(store, id(EntityType.TADPOLE)) != 4000) {
            helper.fail("A tadpole's own rot time should beat its class's");
            return;
        }
        float plain = CarcassDrag.penaltyFor(0.8);
        if (Math.abs(CarcassDrag.penaltyFor(0.8, lead.drag()) - Math.min(1.0F, plain * 2.0F)) > 1.0E-5F) {
            helper.fail("A class's drag should multiply the penalty");
            return;
        }
        helper.succeed();
    }

    /** A pool of water, glass-walled, from y 2 to 5, inside x and z 1 to 9. */
    private static void pool(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                for (int y = 2; y <= 5; y++) {
                    boolean wall = x == 0 || x == 10 || z == 0 || z == 10;
                    helper.setBlock(new BlockPos(x, y, z), wall ? Blocks.GLASS.defaultBlockState() : Blocks.WATER.defaultBlockState());
                }
            }
        }
    }

    /**
     * ARCHITECTURE 5: light classes float, heavy ones sink. A chicken (small, buoyancy 1.2) built on the floor of a pool comes
     * up to the top; a cow (large, 0.9) built with its back at the surface goes down to the floor. Sable's own lift, which
     * floated every carcass alike, is off for carcass blocks.
     */
    @GameTest(template = "empty", timeoutTicks = 340)
    public static void lightCarcassFloatsHeavyOneSinks(GameTestHelper helper) {
        pool(helper);
        ServerLevel level = helper.getLevel();
        Mob chicken = helper.spawn(EntityType.CHICKEN, new BlockPos(3, 2, 5));
        Mob cow = helper.spawn(EntityType.COW, new BlockPos(7, 4, 5));
        chicken.setNoAi(true);
        cow.setNoAi(true);
        double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
        CarcassSavedData.Carcass[] built = new CarcassSavedData.Carcass[2];
        // the floor's and the water's colliders exist only once the arena has stood a moment; and a test's delayed steps
        // are all set out here, since one set from inside another may run twice
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            built[0] = CarcassAssembler.assemble(chicken, null);
            built[1] = CarcassAssembler.assemble(cow, null);
            chicken.discard();
            cow.discard();
            if (built[0] == null || built[1] == null) {
                helper.fail("Both carcasses should build in water");
                return;
            }
            if (CarcassBody.weightClass(built[0]).buoyancy() <= 1.0F || CarcassBody.weightClass(built[1]).buoyancy() >= 1.0F) {
                helper.fail("A chicken's class should float and a cow's sink");
            }
        });
        helper.runAfterDelay(SETTLE_TICKS + 240, () -> {
            CarcassSavedData.Carcass light = built[0];
            CarcassSavedData.Carcass heavy = built[1];
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            ServerSubLevel up = light == null ? null : (ServerSubLevel) container.getSubLevel(light.bones.get(light.rootBone));
            ServerSubLevel down = heavy == null ? null : (ServerSubLevel) container.getSubLevel(heavy.bones.get(heavy.rootBone));
            if (up == null || down == null) {
                helper.fail("Both carcasses should still be in the pool");
                return;
            }
            double chickenAt = up.logicalPose().position().y - floor;
            double cowAt = down.logicalPose().position().y - floor;
            if (chickenAt < 3.2) {
                helper.fail("The chicken should have floated up to the surface (4 above the floor), its body is " + String.format("%.2f", chickenAt) + " above it");
                return;
            }
            if (cowAt > 1.3) {
                helper.fail("The cow should have sunk to the floor, its body is " + String.format("%.2f", cowAt) + " above it");
                return;
            }
            remove(level, light);
            remove(level, heavy);
            helper.succeed();
        });
    }

    /**
     * In water a carcass floats or sinks and is never standing on its legs, so its legs are never made to give way under it
     * (CarcassSlump): a chicken floating up from the floor of a pool, which floated upright, and a cow sunk onto the
     * pool's floor, which stood on it, are never tipped over by it in 300 ticks. (Water has no collision shape, so nothing
     * was under either torso, and the chicken was spun over within half a second and the cow again and again.)
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void carcassesInWaterAreNotMadeToGiveWay(GameTestHelper helper) {
        pool(helper);
        ServerLevel level = helper.getLevel();
        Mob chicken = helper.spawn(EntityType.CHICKEN, new BlockPos(3, 2, 5));
        Mob cow = helper.spawn(EntityType.COW, new BlockPos(7, 4, 5));
        chicken.setNoAi(true);
        cow.setNoAi(true);
        CarcassSavedData.Carcass[] built = new CarcassSavedData.Carcass[2];
        int[] seen = {0, 0};
        int[] gaveWay = {0, 0};
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            built[0] = CarcassAssembler.assemble(chicken, null);
            built[1] = CarcassAssembler.assemble(cow, null);
            chicken.discard();
            cow.discard();
            if (built[0] == null || built[1] == null) {
                helper.fail("Both carcasses should build in water");
            }
        });
        int[] t = {0};
        helper.onEachTick(() -> {
            if (built[0] == null || built[1] == null || t[0] < 0) {
                return;
            }
            for (int i = 0; i < 2; i++) {
                // every time its legs give way the count goes up (it starts again only once it is down a while)
                if (built[i].slumps > seen[i]) {
                    gaveWay[i] += built[i].slumps - seen[i];
                }
                seen[i] = built[i].slumps;
            }
            if (++t[0] < 300) {
                return;
            }
            t[0] = -1;
            if (gaveWay[0] > 0 || gaveWay[1] > 0) {
                helper.fail("In water a carcass should never be made to give way, but the chicken's legs gave way " + gaveWay[0] + " times and the cow's "
                        + gaveWay[1]);
                return;
            }
            remove(level, built[0]);
            remove(level, built[1]);
            helper.succeed();
        });
    }

    /**
     * Every vanilla mob's butchery table, as the rig targets' own butchery sections wrote it before they moved into groups
     * and mob files: a fingerprint of each table's every yield, bone by bone. A change here means a mob now butchers
     * differently.
     */
    private static final Map<String, Integer> OLD_TABLES = Map.ofEntries(
            Map.entry("allay", 1927193700), Map.entry("armadillo", 326240371), Map.entry("axolotl", -1650715176), Map.entry("bat", -1333462379),
            Map.entry("bee", 1995558195), Map.entry("blaze", 1459769514), Map.entry("bogged", -982331238), Map.entry("breeze", 1421096567),
            Map.entry("camel", 399975671), Map.entry("cat", -559315369), Map.entry("cave_spider", 1668090530), Map.entry("chicken", 983113822),
            Map.entry("cod", 348777437), Map.entry("cow", -1993460388), Map.entry("creeper", 491495121), Map.entry("dolphin", -1444252232),
            Map.entry("donkey", -349053035), Map.entry("drowned", 763917847), Map.entry("elder_guardian", -1439713639), Map.entry("enderman", 595360045),
            Map.entry("endermite", 1834891033), Map.entry("evoker", -601381348), Map.entry("fox", 1388487408), Map.entry("frog", -776630023),
            Map.entry("ghast", 2107751350), Map.entry("glow_squid", 2113943136), Map.entry("goat", 579344297), Map.entry("guardian", 2062371476),
            Map.entry("hoglin", -233029223), Map.entry("horse", -1086888700), Map.entry("husk", -380683350), Map.entry("illusioner", 1325800762),
            Map.entry("iron_golem", -588457890), Map.entry("llama", -1040358301), Map.entry("magma_cube", -917825905), Map.entry("mooshroom", -1489324044),
            Map.entry("mule", 2121735177), Map.entry("ocelot", -798954648), Map.entry("panda", 1709654126), Map.entry("parrot", -927210133),
            Map.entry("phantom", -1654426725), Map.entry("pig", -1371321170), Map.entry("piglin", 1293906128), Map.entry("piglin_brute", 66694447),
            Map.entry("pillager", -1369893136), Map.entry("polar_bear", 139647705), Map.entry("pufferfish", -1743414815), Map.entry("rabbit", -1450984103),
            Map.entry("ravager", -1707211213), Map.entry("salmon", -1320181865), Map.entry("sheep", 1383423946), Map.entry("shulker", -1465936373),
            Map.entry("silverfish", -1740470542), Map.entry("skeleton", 814122805), Map.entry("skeleton_horse", -469698517), Map.entry("slime", 3857820),
            Map.entry("sniffer", -47604825), Map.entry("snow_golem", 472185638), Map.entry("spider", 2029193124), Map.entry("squid", -1438720114),
            Map.entry("stray", 814122805), Map.entry("strider", 1484686151), Map.entry("tadpole", 1557385996), Map.entry("trader_llama", -1040358301),
            Map.entry("turtle", -515294991), Map.entry("vex", 1342370007), Map.entry("villager", 514002728), Map.entry("vindicator", 1341066792),
            Map.entry("wandering_trader", -2145570712), Map.entry("warden", -1396555672), Map.entry("witch", -489685423), Map.entry("wither", -305555631),
            Map.entry("wither_skeleton", 357332042), Map.entry("wolf", 137380021), Map.entry("zoglin", -1266492281), Map.entry("zombie", 1381677849),
            Map.entry("zombie_horse", -524738877), Map.entry("zombie_villager", -422523232), Map.entry("zombified_piglin", -2052593994));

    /**
     * Butchery by group (rule 2), a mob's own file only where it differs: no mob has a table file (a datapack may give
     * one), so every vanilla mob's table is worked out from its groups and gives what its own table gave; the giant, which
     * no group gives butchery, gets the plain defaults (meat by size). Retuning a group retunes its mobs: on a copy of the
     * server's data, the fowl group's hide doubled gives a chicken twice the feathers, and the grazer group's meat changed
     * changes a cow's but not a goat's or a llama's (their own files name mutton and plain meat).
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void butcheryComesFromGroupsAndRetunesByGroup(GameTestHelper helper) {
        StringBuilder wrong = new StringBuilder();
        if (OLD_TABLES.size() != RigManager.all().size()) {
            wrong.append(' ').append(OLD_TABLES.size()).append(" old tables for ").append(RigManager.all().size()).append(" rigs");
        }
        for (Map.Entry<String, Integer> e : OLD_TABLES.entrySet()) {
            ResourceLocation mob = ResourceLocation.withDefaultNamespace(e.getKey());
            if (ButcheryManager.fileTable(mob).isPresent()) {
                wrong.append(' ').append(e.getKey()).append(" has a table file");
            }
            ButcheryTable table = ButcheryManager.forEntity(mob).orElse(null);
            int now = table == null ? 0 : fingerprint(table);
            if (now != e.getValue()) {
                wrong.append(' ').append(e.getKey()).append(" butchers differently: ").append(table == null ? "no table"
                        : ButcheryTable.CODEC.encodeStart(JsonOps.INSTANCE, table).getOrThrow());
            }
        }
        ResourceLocation giant = id(EntityType.GIANT);
        ButcheryTable table = ButcheryManager.forEntity(giant).orElse(null);
        if (table == null || ButcheryManager.fileTable(giant).isPresent()) {
            wrong.append(" a giant has no table of its own and should get one from its groups");
        } else {
            Rig rig = RigManager.forEntity(giant).orElseThrow();
            float meat = table.part("body").stream().filter(y -> y.kind().equals("meat")).map(Yield::count).findFirst().orElse(0.0F);
            float expected = Math.round(rig.bone("body").orElseThrow().volume() * 8.0F * 100.0F) / 100.0F;
            if (Math.abs(meat - expected) > 0.011F) {
                wrong.append(" a giant's body should give meat by its size, ").append(expected).append(", got ").append(meat);
            }
        }
        // the retune, on a copy of the server's data
        PartsData.Store store = new PartsData.Store();
        RegistryOps<com.google.gson.JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        for (PartsData.Kind kind : PartsData.Kind.values()) {
            Map<ResourceLocation, String> files = new HashMap<>(PartsData.SERVER.raw(kind));
            if (kind == PartsData.Kind.MOB_GROUP) {
                for (String group : List.of("fowl", "grazer")) {
                    ResourceLocation file = BloodAndBones.asResource(group);
                    JsonObject json = JsonParser.parseString(files.get(file)).getAsJsonObject();
                    JsonObject butchery = json.getAsJsonObject("butchery");
                    if (group.equals("fowl")) {
                        butchery.addProperty("hide_per_weight", 2.0F * butchery.get("hide_per_weight").getAsFloat());
                    } else {
                        butchery.addProperty("meat", "minecraft:cooked_beef");
                    }
                    files.put(file, json.toString());
                }
            }
            store.load(kind, files, ops);
        }
        float chicken = hide(ButcheryManager.forEntity(id(EntityType.CHICKEN)).orElseThrow());
        float retuned = hide(ButcheryManager.byGroup(id(EntityType.CHICKEN), RigManager.forEntity(id(EntityType.CHICKEN)).orElseThrow(), store).orElseThrow());
        if (Math.abs(retuned - Math.max(1.0F, 2.0F * chicken)) > 0.011F) {
            wrong.append(" the fowl group's hide doubled should double a chicken's feathers: ").append(chicken).append(" then ").append(retuned);
        }
        for (EntityType<?> type : List.of(EntityType.COW, EntityType.LLAMA, EntityType.GOAT)) {
            String meat = ButcheryManager.byGroup(id(type), RigManager.forEntity(id(type)).orElseThrow(), store).orElseThrow().part("body").stream()
                    .filter(y -> y.kind().equals("meat")).map(Yield::item).findFirst().orElse("none");
            String expected = type == EntityType.GOAT ? "minecraft:mutton" : type == EntityType.LLAMA ? "bloodandbones:raw_meat" : "minecraft:cooked_beef";
            if (!meat.equals(expected)) {
                wrong.append(' ').append(id(type)).append(" should give ").append(expected).append(" with the grazer group's meat changed, not ").append(meat);
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("Butchery by group:" + wrong);
            return;
        }
        helper.succeed();
    }

    /** A table's every yield, the hide's then each bone's in name order, as one number. */
    private static int fingerprint(ButcheryTable table) {
        StringBuilder all = new StringBuilder("hide").append(table.hide());
        for (String bone : new java.util.TreeSet<>(table.parts().keySet())) {
            all.append(bone).append(table.part(bone));
        }
        return all.toString().hashCode();
    }

    /** How much hide skinning the whole of it gives. */
    private static float hide(ButcheryTable table) {
        return (float) table.hide().stream().filter(y -> y.kind().equals("hide")).mapToDouble(Yield::count).sum();
    }

    /**
     * The rig targets' hand-written joints, parents and decor were turned into naming rules (docs/MODDED-MOBS.md); the rigs
     * the data run writes from them are what the targets gave. A squid's tentacles, a silverfish's links, a salmon's back
     * half and a spider's legs take the rules' joints; the silverfish's links hang one off the next; a spider's legs hang off
     * its front body; a cod's fins ride on its body and a goat's horns are drawn with its head, none a body of its own. The
     * generic arthropod's legs, held out flat, get a spider's joints by the same rule.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void namingRulesGiveWhatTheTargetsSpelledOut(GameTestHelper helper) {
        StringBuilder wrong = new StringBuilder();
        joint(wrong, "squid", "tentacle3", com.avicagan.bloodandbones.carcass.rig.JointRules.TENTACLE);
        joint(wrong, "silverfish", "segment5", com.avicagan.bloodandbones.carcass.rig.JointRules.SEGMENT);
        joint(wrong, "salmon", "body_back", com.avicagan.bloodandbones.carcass.rig.JointRules.jointFor("tail"));
        joint(wrong, "spider", "left_middle_front_leg", com.avicagan.bloodandbones.carcass.rig.JointRules.FLAT_LEG);
        joint(wrong, "cave_spider", "right_hind_leg", com.avicagan.bloodandbones.carcass.rig.JointRules.FLAT_LEG);
        parent(wrong, "silverfish", "segment6", "segment5");
        parent(wrong, "silverfish", "segment0", "segment1");
        parent(wrong, "spider", "left_hind_leg", "body0");
        parent(wrong, "wolf", "right_front_leg", "upper_body");
        parent(wrong, "snow_golem", "head", "upper_body");
        Rig cod = RigManager.fileRig(ResourceLocation.withDefaultNamespace("cod")).orElseThrow();
        if (cod.bone("left_fin").isPresent() || cod.bone("body").orElseThrow().extras().stream().noneMatch(e -> e.part().equals("left_fin"))) {
            wrong.append(" a cod's fin should ride on its body");
        }
        Rig goat = RigManager.fileRig(ResourceLocation.withDefaultNamespace("goat")).orElseThrow();
        if (goat.bones().stream().anyMatch(b -> b.name().contains("horn"))) {
            wrong.append(" a goat's horns should be drawn with its head, not be bodies");
        }
        Rig horse = RigManager.fileRig(ResourceLocation.withDefaultNamespace("horse")).orElseThrow();
        if (horse.bones().stream().anyMatch(b -> b.name().contains("saddle") || b.name().contains("baby_leg"))
                || !horse.root().hide().contains("saddle")) {
            wrong.append(" a horse's saddle and baby legs should be left off");
        }
        Rig bug = RigManager.forEntity(id(EntityType.SPIDER)).orElseThrow();
        var arthropod = PartsData.SERVER.genericRig(BloodAndBones.asResource("arthropod"));
        Rig generic = arthropod.build(id(EntityType.SPIDER), 1.4F, 0.9F);
        if (!generic.bone("right_middle_leg").orElseThrow().jointOrDefault().equals(com.avicagan.bloodandbones.carcass.rig.JointRules.FLAT_LEG) || bug.fitted()) {
            wrong.append(" the generic arthropod's flat legs should take a spider's joints");
        }
        // a generic body's file may set a bone's joint, as a rig file does; one it leaves out still comes from the rules
        var serpent = com.avicagan.bloodandbones.carcass.rig.GenericRig.parse(BloodAndBones.asResource("test_serpent"), JsonParser.parseString(
                "{\"bones\": [{\"name\": \"body\", \"box\": [-0.2, 0, -0.5, 0.2, 0.3, 0.5]},"
                        + " {\"name\": \"head\", \"parent\": \"body\", \"box\": [-0.15, 0, -0.8, 0.15, 0.25, -0.5], \"pivot\": [0, 0.1, -0.5],"
                        + " \"joint\": {\"min_degrees\": [-5, -70, -5], \"max_degrees\": [5, 70, 5], \"damping\": 3.0}},"
                        + " {\"name\": \"tail\", \"parent\": \"body\", \"box\": [-0.1, 0, 0.5, 0.1, 0.2, 1.0], \"pivot\": [0, 0.1, 0.5]}]}").getAsJsonObject())
                .build(BloodAndBones.asResource("test_serpent"), 1.0F, 0.5F);
        var head = serpent.bone("head").orElseThrow().jointOrDefault();
        if (head.maxDegrees().y != 70.0F || head.minDegrees().x != -5.0F || head.damping() != 3.0F) {
            wrong.append(" a generic body file's own joint should be used, got ").append(head);
        }
        if (!serpent.bone("tail").orElseThrow().jointOrDefault().equals(com.avicagan.bloodandbones.carcass.rig.JointRules.jointFor("tail"))) {
            wrong.append(" a generic bone with no joint should take the rules' one");
        }
        if (!wrong.isEmpty()) {
            helper.fail("Naming rules:" + wrong);
            return;
        }
        helper.succeed();
    }

    private static void joint(StringBuilder wrong, String mob, String bone, com.avicagan.bloodandbones.carcass.rig.JointSpec expected) {
        var got = RigManager.fileRig(ResourceLocation.withDefaultNamespace(mob)).flatMap(r -> r.bone(bone)).map(Bone::jointOrDefault).orElse(null);
        if (!expected.equals(got)) {
            wrong.append(' ').append(mob).append(' ').append(bone).append(" has joint ").append(got);
        }
    }

    private static void parent(StringBuilder wrong, String mob, String bone, String expected) {
        String got = RigManager.fileRig(ResourceLocation.withDefaultNamespace(mob)).flatMap(r -> r.bone(bone)).flatMap(Bone::parent).orElse(null);
        if (!expected.equals(got)) {
            wrong.append(' ').append(mob).append(' ').append(bone).append(" hangs off ").append(got);
        }
    }

    /** Every vanilla mob's rot time as it was when each rig named its own: now from groups and classes, a mob's file where it differs. */
    private static final Map<String, Integer> OLD_ROT_TIMES = Map.ofEntries(
            Map.entry("allay", 6000), Map.entry("armadillo", 20000), Map.entry("axolotl", 12000), Map.entry("bat", 12000), Map.entry("bee", 8000),
            Map.entry("blaze", 1000000), Map.entry("bogged", 36000), Map.entry("breeze", 1000000), Map.entry("camel", 36000), Map.entry("cat", 20000),
            Map.entry("cave_spider", 20000), Map.entry("chicken", 12000), Map.entry("cod", 8000), Map.entry("cow", 24000), Map.entry("creeper", 12000),
            Map.entry("dolphin", 24000), Map.entry("donkey", 36000), Map.entry("drowned", 6000), Map.entry("elder_guardian", 24000), Map.entry("enderman", 24000),
            Map.entry("endermite", 6000), Map.entry("evoker", 20000), Map.entry("fox", 20000), Map.entry("frog", 16000), Map.entry("ghast", 24000),
            Map.entry("glow_squid", 12000), Map.entry("goat", 24000), Map.entry("guardian", 24000), Map.entry("hoglin", 30000), Map.entry("horse", 36000),
            Map.entry("husk", 12000), Map.entry("illusioner", 20000), Map.entry("iron_golem", 1000000), Map.entry("llama", 30000), Map.entry("magma_cube", 1000000),
            Map.entry("mooshroom", 24000), Map.entry("mule", 36000), Map.entry("ocelot", 20000), Map.entry("panda", 36000), Map.entry("parrot", 16000),
            Map.entry("phantom", 6000), Map.entry("pig", 20000), Map.entry("piglin", 20000), Map.entry("piglin_brute", 20000), Map.entry("pillager", 20000),
            Map.entry("polar_bear", 36000), Map.entry("pufferfish", 8000), Map.entry("rabbit", 16000), Map.entry("ravager", 36000), Map.entry("salmon", 8000),
            Map.entry("sheep", 20000), Map.entry("shulker", 1000000), Map.entry("silverfish", 6000), Map.entry("skeleton", 48000), Map.entry("skeleton_horse", 48000),
            Map.entry("slime", 1000000), Map.entry("sniffer", 40000), Map.entry("snow_golem", 1000000), Map.entry("spider", 20000), Map.entry("squid", 12000),
            Map.entry("stray", 48000), Map.entry("strider", 20000), Map.entry("tadpole", 4000), Map.entry("trader_llama", 30000), Map.entry("turtle", 30000),
            Map.entry("vex", 6000), Map.entry("villager", 20000), Map.entry("vindicator", 20000), Map.entry("wandering_trader", 20000), Map.entry("warden", 36000),
            Map.entry("witch", 20000), Map.entry("wither", 96000), Map.entry("wither_skeleton", 48000), Map.entry("wolf", 18000), Map.entry("zoglin", 8000),
            Map.entry("zombie", 6000), Map.entry("zombie_horse", 6000), Map.entry("zombie_villager", 6000), Map.entry("zombified_piglin", 8000));

    /** Rot times moved out of the rigs into groups, weight classes and mob files: every mob's is what it was. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void rotTimesAreAsTheyWere(GameTestHelper helper) {
        if (OLD_ROT_TIMES.size() != RigManager.all().size()) {
            helper.fail(OLD_ROT_TIMES.size() + " old rot times for " + RigManager.all().size() + " rigs");
            return;
        }
        StringBuilder wrong = new StringBuilder();
        for (Map.Entry<String, Integer> e : OLD_ROT_TIMES.entrySet()) {
            ResourceLocation mob = ResourceLocation.withDefaultNamespace(e.getKey());
            int now = CarcassBody.rotTime(PartsData.SERVER, mob);
            if (now != e.getValue()) {
                wrong.append(' ').append(e.getKey()).append(' ').append(now).append(" (was ").append(e.getValue()).append(')');
            }
            if (RigManager.fileRig(mob).orElseThrow().rotTime().isPresent()) {
                wrong.append(' ').append(e.getKey()).append(" still names its rot time in its rig");
            }
        }
        if (!wrong.isEmpty()) {
            helper.fail("Rot times changed:" + wrong);
            return;
        }
        helper.succeed();
    }

    /**
     * The knobs moved into the server config and data keep today's figures: every config default is the constant it
     * replaced, every weight class keeps the old drag and blood, the implants' data files give the figures the items were
     * built with, the Beheader's skull map and the slimes' baby yields are what the code said.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void movedKnobsKeepTheirOldFigures(GameTestHelper helper) {
        StringBuilder wrong = new StringBuilder();
        check(wrong, "cuts_to_sever", BBServerConfig.cutsToSever(), CarcassButchery.CUTS_TO_SEVER);
        check(wrong, "cuts_to_butcher", BBServerConfig.cutsToButcher(), CarcassButchery.CUTS_TO_BUTCHER);
        check(wrong, "strokes_to_skin", BBServerConfig.strokesToSkin(), CarcassButchery.STROKES_TO_SKIN);
        check(wrong, "carry_mass", BBServerConfig.carryMass(), CarcassButchery.LIGHT_MASS);
        check(wrong, "drag_light_mass", BBServerConfig.dragLightMass(), CarcassDrag.CHICKEN_MASS);
        check(wrong, "drag_light_penalty", BBServerConfig.dragLightPenalty(), CarcassDrag.CHICKEN_PENALTY);
        check(wrong, "drag_heavy_mass", BBServerConfig.dragHeavyMass(), CarcassDrag.RAVAGER_MASS);
        check(wrong, "drag_heavy_penalty", BBServerConfig.dragHeavyPenalty(), CarcassDrag.RAVAGER_PENALTY);
        check(wrong, "going_off_below", BBServerConfig.goingOffBelow(), com.avicagan.bloodandbones.registry.BBItemAttributes.FRESH);
        check(wrong, "rotting_below", BBServerConfig.rottenBelow(), com.avicagan.bloodandbones.registry.BBItemAttributes.ROTTING);
        check(wrong, "rotting_below (minions)", BBServerConfig.rottenBelow(), com.avicagan.bloodandbones.minion.MinionAssembly.FRESH_ENOUGH);
        for (var kind : com.avicagan.bloodandbones.machine.MachineKind.values()) {
            check(wrong, kind.id + "_stress", BBServerConfig.machineStress(kind), kind.stress);
            check(wrong, kind.id + "_pace", BBServerConfig.machinePace(kind), kind.pace);
        }
        check(wrong, "roast_min_ticks", BBServerConfig.roastMin(), com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity.MIN_COOK);
        check(wrong, "roast_max_ticks", BBServerConfig.roastMax(), com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity.MAX_COOK);
        check(wrong, "roast_max_whole_ticks", BBServerConfig.roastMaxWhole(), com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity.MAX_COOK_WHOLE);
        check(wrong, "roast_ticks_per_block", BBServerConfig.roastPerBlock(), com.avicagan.bloodandbones.cooking.SpitRoastBlockEntity.COOK_PER_BLOCK);
        check(wrong, "spool_ticks", BBServerConfig.spoolTicks(), com.avicagan.bloodandbones.cyber.Throttle.SPOOL_TICKS);
        check(wrong, "full_spool_drain", BBServerConfig.fullSpoolDrain(), com.avicagan.bloodandbones.cyber.Throttle.FULL_DRAIN);
        check(wrong, "fire_cost", BBServerConfig.fireCost(), com.avicagan.bloodandbones.cyber.Throttle.TAP);
        for (var module : com.avicagan.bloodandbones.cyber.Module.values()) {
            check(wrong, module.getSerializedName() + "_upkeep", module.upkeep(), module.defaultUpkeep());
        }
        check(wrong, "necrosis per_swing", BBServerConfig.necrosisPerSwing(), com.avicagan.bloodandbones.body.Necrosis.SWING);
        check(wrong, "necrosis blocks_per_point", BBServerConfig.necrosisBlocksPerPoint(), com.avicagan.bloodandbones.body.Necrosis.BLOCKS_PER_POINT);
        check(wrong, "necrosis per_meal", BBServerConfig.necrosisPerMeal(), com.avicagan.bloodandbones.body.Necrosis.MEAL);
        check(wrong, "perfuse_mb", BBServerConfig.perfuseMb(), com.avicagan.bloodandbones.body.Necrosis.PERFUSE_MB);
        check(wrong, "perfuse_points", BBServerConfig.perfusePoints(), com.avicagan.bloodandbones.body.Necrosis.PERFUSE_POINTS);
        Map<ResourceLocation, WeightClass> classes = PartsData.SERVER.weightClasses();
        if (classes.size() != 6) {
            wrong.append(" there should be six weight classes, not ").append(classes.size());
        }
        classes.forEach((id, cls) -> {
            check(wrong, id + " drag", cls.drag(), 1.0);
            check(wrong, id + " blood_per_weight", cls.bloodPerWeight(), CarcassBleeding.BLOOD_PER_WEIGHT);
        });
        int implants = 0;
        for (var item : BuiltInRegistries.ITEM) {
            if (item instanceof com.avicagan.bloodandbones.body.ImplantItem implant) {
                implants++;
                if (!implant.spec().equals(implant.defaultSpec())) {
                    wrong.append(' ').append(BuiltInRegistries.ITEM.getKey(item)).append("'s data gives ").append(implant.spec()).append(", built ").append(implant.defaultSpec());
                }
                if (!PartsData.SERVER.implantFiles().containsKey(BuiltInRegistries.ITEM.getKey(item))) {
                    wrong.append(' ').append(BuiltInRegistries.ITEM.getKey(item)).append(" has no figures file");
                }
            }
        }
        if (implants < 15) {
            wrong.append(" only ").append(implants).append(" implants found");
        }
        Map<EntityType<?>, net.minecraft.world.item.Item> skulls = Map.of(EntityType.ZOMBIE, net.minecraft.world.item.Items.ZOMBIE_HEAD,
                EntityType.HUSK, net.minecraft.world.item.Items.ZOMBIE_HEAD, EntityType.DROWNED, net.minecraft.world.item.Items.ZOMBIE_HEAD,
                EntityType.SKELETON, net.minecraft.world.item.Items.SKELETON_SKULL, EntityType.STRAY, net.minecraft.world.item.Items.SKELETON_SKULL,
                EntityType.BOGGED, net.minecraft.world.item.Items.SKELETON_SKULL, EntityType.WITHER_SKELETON, net.minecraft.world.item.Items.WITHER_SKELETON_SKULL,
                EntityType.CREEPER, net.minecraft.world.item.Items.CREEPER_HEAD, EntityType.PIGLIN, net.minecraft.world.item.Items.PIGLIN_HEAD,
                EntityType.PIGLIN_BRUTE, net.minecraft.world.item.Items.PIGLIN_HEAD);
        for (Map.Entry<EntityType<?>, net.minecraft.world.item.Item> e : skulls.entrySet()) {
            var skull = e.getKey().builtInRegistryHolder().getData(com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.SKULLS);
            float chance = e.getValue() == net.minecraft.world.item.Items.WITHER_SKELETON_SKULL
                    ? com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.WITHER_SKULL_CHANCE
                    : com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.SKULL_CHANCE;
            if (skull == null || skull.item() != e.getValue() || skull.chance() != chance) {
                wrong.append(' ').append(id(e.getKey())).append("'s skull is ").append(skull);
            }
        }
        if (EntityType.COW.builtInRegistryHolder().getData(com.avicagan.bloodandbones.machine.CarcassMachineBlockEntity.SKULLS) != null) {
            wrong.append(" a cow has no skull to keep");
        }
        check(wrong, "slime baby_yield", PartsData.SERVER.resolve(id(EntityType.SLIME), false).carcass().babyYield().orElse(-1.0F), 0.25);
        check(wrong, "magma cube baby_yield", PartsData.SERVER.resolve(id(EntityType.MAGMA_CUBE), false).carcass().babyYield().orElse(-1.0F), 0.0);
        if (!wrong.isEmpty()) {
            helper.fail("Moved knobs changed:" + wrong);
            return;
        }
        helper.succeed();
    }

    private static void check(StringBuilder wrong, String what, double now, double was) {
        if (Math.abs(now - was) > 1.0E-6) {
            wrong.append(' ').append(what).append(' ').append(now).append(" (was ").append(was).append(')');
        }
    }
}
