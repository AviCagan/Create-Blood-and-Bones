package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.carcass.CarcassLook;
import com.avicagan.bloodandbones.minion.MinionBody;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionData;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.MinionJobs;
import com.avicagan.bloodandbones.minion.MinionStats;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.registry.BBEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.FrogVariant;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The sapper, what a carcass keeps of its mob and what those variants make of the parts (docs/PARTS-AND-TRAITS.md sections
 * 6.9, 8.2 and 9, slice 3), and where a minion holds and wears things (section 6.10). The sapper's pen is walled in glass,
 * so it never sees another test's monsters.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class MinionVariantTests {
    private static final ResourceLocation CREEPER = ResourceLocation.withDefaultNamespace("creeper");

    private static PieceRef ref(String entity, String bone) {
        return ref(entity, bone, Map.of());
    }

    private static PieceRef ref(String entity, String bone, Map<String, String> traits) {
        return new PieceRef(ResourceLocation.withDefaultNamespace(entity), bone, ResourceLocation.withDefaultNamespace("textures/entity/" + entity + ".png"),
                List.of(), 1.0F, false, traits, false);
    }

    /** A cow with a creeper's head, on its own legs, with a creeper's powder sac in it (a charged creeper's, if so). */
    private static MinionBuild sapper(boolean charged) {
        MinionBuild build = MinionBuild.of(ref("cow", "body")).with("head", ref("creeper", "head"));
        for (String leg : new String[]{"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"}) {
            build = build.with(leg, ref("cow", leg));
        }
        return build.withOrgan(Optional.of(new CarcassArmour.Organ(BloodAndBones.asResource("powder_sac"), CREEPER, false,
                charged ? Map.of("charged", "true") : Map.of())));
    }

    private static MinionEntity minion(GameTestHelper helper, BlockPos pos, MinionBuild build) {
        ServerLevel level = helper.getLevel();
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos at = helper.absolutePos(pos);
        // stood beside it, so it has no call to walk off after them
        maker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() - 0.5);
        MinionEntity minion = BBEntities.MINION.get().create(level);
        minion.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, at, build, 1000.0F);
        level.addFreshEntity(minion);
        return minion;
    }

    /** Glass round the pen's edge, y 2 to 4. */
    private static void pen(GameTestHelper helper) {
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 10; z++) {
                if (x == 0 || z == 0 || x == 10 || z == 10) {
                    for (int y = 2; y <= 4; y++) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.GLASS);
                    }
                }
            }
        }
    }

    private static boolean floorWhole(GameTestHelper helper) {
        for (int x = 1; x <= 9; x++) {
            for (int z = 1; z <= 9; z++) {
                if (helper.getBlockState(new BlockPos(x, 1, z)).isAir()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A creeper's head offers the sapper (its family's data) only to a body with a detonating organ in it. Put to it, the
     * minion walks up to the husk it has for a target, hisses, and blows up beside it: the husk is hurt, no block breaks
     * (the server allows minions none), and the minion lies powered down, whole and unhurt, never destroyed.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void sapperDetonatesAndPowersDown(GameTestHelper helper) {
        pen(helper);
        MinionBuild build = sapper(false);
        MinionBuild noSac = build.withOrgan(Optional.empty());
        MinionStats stats = MinionStats.of(PartsData.SERVER, build);
        if (!stats.jobs().contains(MinionJobs.SAPPER)
                || !MinionJobs.offered(PartsData.SERVER, build, stats, ItemStack.EMPTY, false).contains(MinionJobs.SAPPER)
                || MinionJobs.offered(PartsData.SERVER, noSac, MinionStats.of(PartsData.SERVER, noSac), ItemStack.EMPTY, false).contains(MinionJobs.SAPPER)) {
            helper.fail("A creeper's head should offer the sapper, and only with a detonating organ in: " + stats.jobs());
            return;
        }
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 5), build);
        if (!minion.setJob(MinionJobs.SAPPER)) {
            helper.fail("It should take the sapper's job");
            return;
        }
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 5));
        husk.setNoAi(true);
        float huskHealth = husk.getHealth();
        float health = minion.getHealth();
        minion.setTarget(husk);
        helper.succeedWhen(() -> {
            if (!minion.poweredDown()) {
                if (husk.isAlive() && minion.getTarget() == null) {
                    minion.setTarget(husk);
                }
                helper.fail("the sapper has not blown up yet: at " + helper.relativeVec(minion.position()));
            }
            boolean whole = !minion.isRemoved() && minion.isAlive() && minion.getHealth() >= health;
            boolean hurt = !husk.isAlive() || husk.getHealth() < huskHealth;
            boolean near = minion.distanceTo(husk) < 4.0F;
            boolean floor = floorWhole(helper);
            minion.discard();
            husk.discard();
            helper.assertTrue(whole, "the sapper should lie powered down, whole: " + minion.getHealth());
            helper.assertTrue(hurt && near, "it should have walked up to the husk and hurt it: near " + near);
            helper.assertTrue(floor, "no block should break when the server allows minions none");
        });
    }

    /**
     * Handed a banner, a sapper goes for the banner of that colour standing near home (its mark) and blows up there; the
     * other colour's is left alone, and both stand after, the blast breaking no blocks.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sapperGoesForItsBanner(GameTestHelper helper) {
        pen(helper);
        helper.setBlock(new BlockPos(8, 2, 8), Blocks.RED_BANNER);
        helper.setBlock(new BlockPos(8, 2, 2), Blocks.BLUE_BANNER);
        MinionEntity minion = minion(helper, new BlockPos(2, 2, 5), sapper(false));
        minion.setJob(MinionJobs.SAPPER);
        minion.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.RED_BANNER));
        helper.succeedWhen(() -> {
            helper.assertTrue(minion.poweredDown(), "the sapper has not blown up at its banner yet: at " + helper.relativeVec(minion.position()));
            double red = helper.relativeVec(minion.position()).distanceTo(new net.minecraft.world.phys.Vec3(8.5, 2.0, 8.5));
            boolean standing = helper.getBlockState(new BlockPos(8, 2, 8)).is(Blocks.RED_BANNER) && helper.getBlockState(new BlockPos(8, 2, 2)).is(Blocks.BLUE_BANNER);
            minion.discard();
            helper.assertTrue(red < 2.5, "it should have blown up at the red banner, not " + red + " from it");
            helper.assertTrue(standing, "the banners should stand, the blast breaking no blocks");
        });
    }

    /**
     * A charged creeper's powder sac holds a bigger blast (spec 8.1: power 4, not 2): the organ's variant raises its
     * self-destruct a level, and a husk beside it is hurt the more.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void chargedCreeperSacIsStronger(GameTestHelper helper) {
        ResourceLocation selfDestruct = BloodAndBones.asResource("self_destruct");
        List<List<TraitList.Resolved>> plainLists = MinionData.traits(PartsData.SERVER, sapper(false));
        List<List<TraitList.Resolved>> chargedLists = MinionData.traits(PartsData.SERVER, sapper(true));
        int plain = plainLists.get(plainLists.size() - 1).stream().filter(t -> t.id().equals(selfDestruct)).mapToInt(TraitList.Resolved::level).max().orElse(0);
        int charged = chargedLists.get(chargedLists.size() - 1).stream().filter(t -> t.id().equals(selfDestruct)).mapToInt(TraitList.Resolved::level).max().orElse(0);
        if (plain != 1 || charged != 2) {
            helper.fail("A charged creeper's sac should self-destruct a level higher: " + plain + " and " + charged);
            return;
        }
        MinionEntity weak = minion(helper, new BlockPos(2, 2, 2), sapper(false));
        MinionEntity strong = minion(helper, new BlockPos(8, 2, 8), sapper(true));
        if (ActiveTraits.of(strong).level(selfDestruct) != 2) {
            helper.fail("The minion with the charged sac should have Self-Destruct II");
            return;
        }
        Husk near = helper.spawn(EntityType.HUSK, new BlockPos(2, 2, 4));
        Husk far = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 6));
        near.setNoAi(true);
        far.setNoAi(true);
        float full = near.getMaxHealth();
        // the plain one first, then the charged one, so neither blast reaches the other's husk
        weak.setTarget(near);
        float[] weakDid = {-1.0F};
        helper.onEachTick(() -> {
            if (weakDid[0] < 0.0F && weak.poweredDown()) {
                weakDid[0] = full - (near.isAlive() ? near.getHealth() : 0.0F);
                strong.setTarget(far);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(weakDid[0] >= 0.0F && strong.poweredDown(), "both have not blown up yet");
            float strongDid = full - (far.isAlive() ? far.getHealth() : 0.0F);
            weak.discard();
            strong.discard();
            near.discard();
            far.discard();
            helper.assertTrue(weakDid[0] > 0.0F && strongDid > weakDid[0], "the charged sac's blast should hurt more: " + strongDid + " against " + weakDid[0]);
        });
    }

    /**
     * A snow fox's hide is insulated (spec 8.2, a variant): a flesh minion built of a snow fox keeps its hide's trait and
     * takes no harm from freezing; one of a red fox is hurt.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void snowFoxHideInsulates(GameTestHelper helper) {
        Map<String, String> snow = Map.of("variant", "snow");
        Map<String, String> red = Map.of("variant", "red");
        MinionEntity snowFox = minion(helper, new BlockPos(3, 2, 5), MinionBuild.of(ref("fox", "body", snow)).with("head", ref("fox", "head", snow)));
        MinionEntity redFox = minion(helper, new BlockPos(7, 2, 5), MinionBuild.of(ref("fox", "body", red)).with("head", ref("fox", "head", red)));
        ResourceLocation insulated = BloodAndBones.asResource("insulated");
        if (ActiveTraits.of(snowFox).level(insulated) != 1 || ActiveTraits.of(redFox).level(insulated) != 0) {
            helper.fail("Only the snow fox's hide should be insulated");
            return;
        }
        float snowHealth = snowFox.getHealth();
        float redHealth = redFox.getHealth();
        snowFox.hurt(snowFox.damageSources().freeze(), 2.0F);
        redFox.hurt(redFox.damageSources().freeze(), 2.0F);
        boolean spared = snowFox.getHealth() >= snowHealth;
        boolean hurt = redFox.getHealth() < redHealth;
        snowFox.discard();
        redFox.discard();
        if (!spared || !hurt) {
            helper.fail("Freezing should spare the snow fox and hurt the red one: " + spared + ", " + hurt);
            return;
        }
        helper.succeed();
    }

    /**
     * What a carcass keeps of its mob (spec 9, slice 3): a creeper's charge, a fox's and a frog's and a rabbit's variant, a
     * panda's gene, a given name. The heads they make: the killer bunny's is a berserk bodyguard or guard with a bite of 8,
     * a vindicator named Johnny is berserk, a zoglin's always is, and a weak panda's sneezes.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void carcassKeepsVariants(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Creeper creeper = EntityType.CREEPER.create(level);
        CompoundTag tag = new CompoundTag();
        creeper.addAdditionalSaveData(tag);
        tag.putBoolean("powered", true);
        creeper.readAdditionalSaveData(tag);
        Fox fox = EntityType.FOX.create(level);
        fox.setVariant(Fox.Type.SNOW);
        Frog frog = EntityType.FROG.create(level);
        frog.setVariant(level.registryAccess().registryOrThrow(Registries.FROG_VARIANT).getHolderOrThrow(FrogVariant.WARM));
        Rabbit rabbit = EntityType.RABBIT.create(level);
        rabbit.setVariant(Rabbit.Variant.EVIL);
        Panda panda = EntityType.PANDA.create(level);
        panda.setMainGene(Panda.Gene.WEAK);
        panda.setHiddenGene(Panda.Gene.WEAK);
        var vindicator = EntityType.VINDICATOR.create(level);
        vindicator.setCustomName(Component.literal("Johnny"));
        Map<String, String> charged = CarcassLook.traits(creeper);
        if (!"true".equals(charged.get("charged")) || !"snow".equals(CarcassLook.traits(fox).get("variant"))
                || !"warm".equals(CarcassLook.traits(frog).get("variant")) || !"evil".equals(CarcassLook.traits(rabbit).get("variant"))
                || !"weak".equals(CarcassLook.traits(panda).get("gene")) || !"Johnny".equals(CarcassLook.traits(vindicator).get("name"))) {
            helper.fail("A carcass should keep a creeper's charge, the variants, a panda's gene and a name: " + charged + ", " + CarcassLook.traits(fox)
                    + ", " + CarcassLook.traits(frog) + ", " + CarcassLook.traits(rabbit) + ", " + CarcassLook.traits(panda) + ", " + CarcassLook.traits(vindicator));
            return;
        }
        MinionBuild killer = MinionBuild.of(ref("cow", "body")).with("head", ref("rabbit", "head", CarcassLook.traits(rabbit)));
        MinionStats bunny = MinionStats.of(PartsData.SERVER, killer);
        MinionStats tame = MinionStats.of(PartsData.SERVER, MinionBuild.of(ref("cow", "body")).with("head", ref("rabbit", "head", Map.of("variant", "brown"))));
        if (!bunny.berserk() || Math.abs(bunny.biteDamage() - 8.0F) > 0.01F || !bunny.jobs().contains(BloodAndBones.asResource("bodyguard")) || tame.berserk()) {
            helper.fail("The killer bunny's head should be a berserk bodyguard biting for 8, a brown rabbit's not: " + bunny.jobs() + ", " + bunny.biteDamage());
            return;
        }
        MinionStats johnny = MinionStats.of(PartsData.SERVER, MinionBuild.of(ref("cow", "body")).with("head", ref("vindicator", "head", Map.of("name", "Johnny"))));
        MinionStats vince = MinionStats.of(PartsData.SERVER, MinionBuild.of(ref("cow", "body")).with("head", ref("vindicator", "head", Map.of("name", "Vince"))));
        MinionStats zoglin = MinionStats.of(PartsData.SERVER, MinionBuild.of(ref("cow", "body")).with("head", ref("zoglin", "head")));
        if (!johnny.berserk() || vince.berserk() || !zoglin.berserk()) {
            helper.fail("Johnny's head and a zoglin's should be berserk, any other vindicator's not");
            return;
        }
        List<List<TraitList.Resolved>> weak = MinionData.traits(PartsData.SERVER, MinionBuild.of(ref("cow", "body")).with("head", ref("panda", "head", Map.of("gene", "weak"))));
        if (weak.stream().flatMap(List::stream).noneMatch(t -> t.id().equals(BloodAndBones.asResource("sneeze")))) {
            helper.fail("A weak panda's head should sneeze");
            return;
        }
        helper.succeed();
    }

    /**
     * Where a minion holds and wears things (spec 6.10), the same on both sides: a zombie's right arm grips at its hand's
     * end, as vanilla's hand layer does (1 pixel in, 10 down, 2 forward); a villager's pair of arms in front of them; with
     * no hand, a fox's head in its mouth; the helmet on the head it has.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void heldItemAnchors(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        MinionBuild zombie = MinionBuild.of(ref("zombie", "body")).with("head", ref("zombie", "head")).with("right_arm", ref("zombie", "right_arm"))
                .with("left_arm", ref("zombie", "left_arm"));
        MinionBody.Layout layout = MinionBody.layout(store, zombie);
        MinionBody.Anchors anchors = MinionBody.anchors(store, zombie, layout);
        if (anchors.hold() < 0 || !"right_arm".equals(layout.pieces().get(anchors.hold()).socket()) || !anchors.right() || !"hand".equals(anchors.how())
                || anchors.holdAt().distance(new org.joml.Vector3f(-1.0F, 10.0F, -2.0F)) > 0.01F
                || anchors.head() < 0 || !"head".equals(layout.pieces().get(anchors.head()).socket())) {
            helper.fail("A zombie should hold things at its right hand's end and wear a helmet on its head: " + anchors);
            return;
        }
        MinionBuild villager = MinionBuild.of(ref("villager", "body")).with("head", ref("villager", "head")).with("arms", ref("villager", "arms"));
        MinionBody.Anchors folded = MinionBody.anchors(store, villager, MinionBody.layout(store, villager));
        MinionBuild fox = MinionBuild.of(ref("fox", "body")).with("head", ref("fox", "head"));
        MinionBody.Layout foxLayout = MinionBody.layout(store, fox);
        MinionBody.Anchors mouth = MinionBody.anchors(store, fox, foxLayout);
        if (!"pair".equals(folded.how()) || !"mouth".equals(mouth.how()) || mouth.hold() != mouth.head() || mouth.head() < 0) {
            helper.fail("A villager's folded arms should hold in front, and a fox with no hand in its mouth: " + folded + ", " + mouth);
            return;
        }
        helper.succeed();
    }
}
