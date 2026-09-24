package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.backtank.BacktankTier;
import com.avicagan.bloodandbones.backtank.FluidBacktankItem;
import com.avicagan.bloodandbones.body.Surgery;
import com.avicagan.bloodandbones.body.SurgeryTableBlock;
import com.avicagan.bloodandbones.body.SurgeryTableBlockEntity;
import com.avicagan.bloodandbones.body.TableAttachment;
import com.avicagan.bloodandbones.carcass.CarcassAssembler;
import com.avicagan.bloodandbones.carcass.CarcassSavedData;
import com.avicagan.bloodandbones.config.BloodlessWords;
import com.avicagan.bloodandbones.item.CarcassPieceItem;
import com.avicagan.bloodandbones.minion.MinionAssembly;
import com.avicagan.bloodandbones.minion.MinionBuild;
import com.avicagan.bloodandbones.minion.MinionData;
import com.avicagan.bloodandbones.minion.MinionEntity;
import com.avicagan.bloodandbones.minion.PieceRef;
import com.avicagan.bloodandbones.network.OrganActivatePayload;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.CarcassArmour;
import com.avicagan.bloodandbones.parts.CarcassArmourItem;
import com.avicagan.bloodandbones.parts.MobGroup;
import com.avicagan.bloodandbones.parts.OrganKind;
import com.avicagan.bloodandbones.parts.Organs;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.ResolvedMob;
import com.avicagan.bloodandbones.parts.Source;
import com.avicagan.bloodandbones.parts.TraitsCommand;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBDataComponents;
import com.avicagan.bloodandbones.registry.BBEntities;
import com.avicagan.bloodandbones.registry.BBFluids;
import com.avicagan.bloodandbones.registry.BBItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Organs as items (docs/PARTS-AND-TRAITS.md sections 4.9, 5.7, 7.1 and 7.3): every organ the data names has a file; the
 * Gland carries its organ and its mob; the Surgical Rig's Cleaver takes a piece's organs out in its data's order (a
 * creeper's powder sac spilling gunpowder, a skeleton's marrow, a rabbit's hind leg its foot), and a heavy carcass's
 * lying over the table; organs fit only the armour pieces their file names and give back the one they replace, in armour
 * and in a minion; vanilla items are organs of their mobs by a data map; the names read right in bloodless mode; and the
 * traits commands run.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class OrganTests {
    private static final ResourceLocation COW = ResourceLocation.withDefaultNamespace("cow");
    private static final ResourceLocation CREEPER = ResourceLocation.withDefaultNamespace("creeper");
    private static final ResourceLocation SKELETON = ResourceLocation.withDefaultNamespace("skeleton");
    private static final ResourceLocation RABBIT = ResourceLocation.withDefaultNamespace("rabbit");
    private static final ResourceLocation SQUID = ResourceLocation.withDefaultNamespace("squid");
    private static final ResourceLocation GLOW_SQUID = ResourceLocation.withDefaultNamespace("glow_squid");
    private static final ResourceLocation SPIDER = ResourceLocation.withDefaultNamespace("spider");
    /** The organs every body of a kind has; any other is a special one. */
    private static final Set<String> GENERIC = Set.of("heart", "lungs", "stomach", "eye", "core");
    /** Words a bloodless name must never have: organs are machine parts there (spec 7.10). */
    private static final Pattern FLESHY = Pattern.compile("(?i)(?<![a-z])(blood\\w*|bleed\\w*|gor[ey]|guts?|organs?|glands?|sacs?|hearts?|lungs"
            + "|stomachs?|bladders?|marrow|flesh|eyes?|carcass\\w*)(?![a-z])");

    private static ResourceLocation bb(String id) {
        return BloodAndBones.asResource(id);
    }

    private static SurgeryTableBlockEntity table(GameTestHelper helper, BlockPos at, TableAttachment attachment) {
        helper.setBlock(at, BBBlocks.SURGERY_TABLE.getDefaultState().setValue(SurgeryTableBlock.ATTACHMENT, attachment));
        return (SurgeryTableBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(at));
    }

    /** A fresh carcass piece of this mob's bone (the torso is the rig's root). */
    private static ItemStack piece(ResourceLocation entity, @Nullable String bone) {
        String name = bone != null ? bone : com.avicagan.bloodandbones.carcass.rig.RigManager.forEntity(entity).orElseThrow().root().name();
        ItemStack stack = new ItemStack(BBItems.CARCASS_PIECE.get());
        stack.set(BBDataComponents.PIECE.get(), new CarcassPieceItem.Piece(entity, name,
                ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png"), List.of(), 1.0F, false, Map.of(), 0.0F, 0.0F, 0.0F, false));
        return stack;
    }

    private static int count(Player player, Item item) {
        int n = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** The first stack of this item in the player's inventory, or empty. */
    private static ItemStack first(Player player, Item item) {
        return player.getInventory().items.stream().filter(s -> s.is(item)).findFirst().orElse(ItemStack.EMPTY);
    }

    /** Use what the player holds on the block there, as a right-click does (the block's own path). */
    private static void click(GameTestHelper helper, Player player, BlockPos at, ItemStack held) {
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        BlockPos pos = helper.absolutePos(at);
        helper.getLevel().getBlockState(pos).useItemOn(player.getItemInHand(InteractionHand.MAIN_HAND), helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos).add(0.0, 0.4, 0.0), Direction.UP, pos, false));
    }

    /** These items, anywhere in a crafting grid: what they make and what stays in the grid; null if nothing. */
    @Nullable
    private static List<ItemStack> craft(GameTestHelper helper, ItemStack... items) {
        List<ItemStack> grid = new ArrayList<>();
        for (ItemStack item : items) {
            grid.add(item.copy());
        }
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        CraftingInput input = CraftingInput.of(3, 3, grid);
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).map(holder -> {
            List<ItemStack> out = new ArrayList<>();
            out.add(holder.value().assemble(input, helper.getLevel().registryAccess()));
            holder.value().getRemainingItems(input).stream().filter(s -> !s.isEmpty()).forEach(out::add);
            return out;
        }).orElse(null);
    }

    private static List<String> tooltip(GameTestHelper helper, ItemStack stack) {
        return stack.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), null, TooltipFlag.NORMAL).stream().map(Component::getString).toList();
    }

    private static JsonObject lang(GameTestHelper helper) {
        try (var in = BloodAndBones.class.getResourceAsStream("/assets/bloodandbones/lang/en_us.json")) {
            return JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            helper.fail("Could not read the language file: " + e);
            return null;
        }
    }

    // ---- files

    /**
     * Every organ any group or mob file names (in an organ list or its organ_traits) has an organ file: an item, a named
     * look, pieces of armour that take it, a name and a bloodless name the language has. Every one of the 79 vanilla mobs
     * holds at least one special organ with an ability in its lists, so it can be cut out. What is missing is listed.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyOrganHasAFile(GameTestHelper helper) {
        JsonObject lang = lang(helper);
        if (lang == null) {
            return;
        }
        PartsData.Store store = PartsData.SERVER;
        Set<ResourceLocation> named = new LinkedHashSet<>();
        List<MobGroup> layers = new ArrayList<>(store.groups().values());
        layers.addAll(store.mobFiles().values());
        for (MobGroup layer : layers) {
            named.addAll(layer.organTraits().keySet());
            layer.organs().values().forEach(list -> {
                named.addAll(list.add());
                named.addAll(list.remove());
            });
        }
        List<String> missing = new ArrayList<>();
        for (ResourceLocation organ : named) {
            if (store.organ(organ) == null) {
                missing.add(organ.toString());
            }
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<ResourceLocation, OrganKind> e : store.organs().entrySet()) {
            OrganKind kind = e.getValue();
            if (kind.item() == Items.AIR) {
                problems.add(e.getKey() + " has no item");
            }
            if (!OrganKind.LOOKS.contains(kind.look()) || kind.armourPieces().isEmpty()
                    || !List.of("helmet", "chestplate", "leggings", "boots").containsAll(kind.armourPieces())) {
                problems.add(e.getKey() + " looks " + kind.look() + " and fits " + kind.armourPieces());
            }
            if (!lang.has(kind.name()) || kind.bloodlessName().isEmpty() || !lang.has(kind.bloodlessName().get())) {
                problems.add(e.getKey() + " has no name " + kind.name() + " or bloodless name " + kind.bloodlessName());
            }
        }
        for (String[] row : BreadthTests.MOBS) {
            ResourceLocation mob = ResourceLocation.withDefaultNamespace(row[0]);
            ResolvedMob resolved = store.resolve(mob, false);
            boolean special = resolved.organLists().values().stream().flatMap(List::stream).anyMatch(organ -> !GENERIC.contains(organ.getPath())
                    && resolved.organs().containsKey(organ) && !(resolved.organs().get(organ).minion().isEmpty() && resolved.organs().get(organ).armour().isEmpty()));
            if (!special) {
                problems.add(row[0] + " holds no special organ with an ability: " + resolved.organLists());
            }
        }
        BloodAndBones.LOGGER.info("Organs named in the data: {}; with files: {}; missing: {}", named.size(), store.organs().size(), missing);
        if (!missing.isEmpty() || !problems.isEmpty() || named.size() < 70) {
            helper.fail("Organs with no file " + missing + "; " + BreadthTests.shortList(problems));
            return;
        }
        helper.succeed();
    }

    // ---- the Gland

    /**
     * A creeper's powder sac is a Gland carrying its organ and its mob (the torso it came from), named "Creeper's Powder
     * Sac", with what it gives in armour (Blast, in a chestplate) and in a minion (Self-Destruct) on its tooltip. Two of
     * them stack; a cow's rumen is another thing; a blank Gland is no organ at all. The heart still comes out as the heart.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void glandCarriesSourceAndName(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        ItemStack sac = Organs.stack(store, bb("powder_sac"), CREEPER, false);
        if (!sac.is(BBItems.GLAND.get()) || !bb("powder_sac").equals(sac.get(BBDataComponents.ORGAN.get()))
                || !new Source(CREEPER, "torso", false).equals(sac.get(BBDataComponents.SOURCE.get()))) {
            helper.fail("A powder sac should be a Gland stamped powder_sac, from a creeper's torso: " + sac.getComponents());
            return;
        }
        if (!sac.getHoverName().getString().equals("Creeper's Powder Sac")) {
            helper.fail("The sac should be named for its mob and organ: " + sac.getHoverName().getString());
            return;
        }
        String lines = String.join(" | ", tooltip(helper, sac));
        if (!lines.contains("Chestplate") || !lines.contains("Blast") || !lines.contains("Self-Destruct") || !lines.contains("Creeper")) {
            helper.fail("The sac's tooltip should say it is a creeper's, fits a chestplate for Blast and gives a minion Self-Destruct: " + lines);
            return;
        }
        ItemStack rumen = Organs.stack(store, bb("rumen"), COW, false);
        if (!ItemStack.isSameItemSameComponents(sac, Organs.stack(store, bb("powder_sac"), CREEPER, false)) || ItemStack.isSameItemSameComponents(sac, rumen)
                || !rumen.getHoverName().getString().equals("Cow's Rumen")) {
            helper.fail("Two powder sacs should stack, and a cow's rumen be something else: " + rumen.getHoverName().getString());
            return;
        }
        if (!new CarcassArmour.Organ(bb("powder_sac"), CREEPER, false).equals(Organs.of(sac, store)) || Organs.of(new ItemStack(BBItems.GLAND.get()), store) != null
                || !new ItemStack(BBItems.GLAND.get()).getHoverName().getString().equals("Gland")) {
            helper.fail("The sac should read back as the creeper's powder sac, and a blank Gland as no organ");
            return;
        }
        ItemStack heart = Organs.stack(store, bb("heart"), COW, false);
        if (!heart.is(BBItems.HEART.get()) || !new CarcassArmour.Organ(bb("heart"), COW, false).equals(Organs.of(heart, store))
                || !heart.getHoverName().getString().contains("Cow")) {
            helper.fail("A heart should still come out as the heart item, the cow's: " + heart.getComponents());
            return;
        }
        helper.succeed();
    }

    // ---- harvesting

    /**
     * The Cleaver takes a piece's organs out in its data's order, one a cut: a cow's torso its heart, lungs, stomach and
     * rumen, then nothing; a creeper's torso (no blood: no heart, lungs or stomach) its powder sac, with one or two
     * gunpowder; a rabbit's hind leg its foot, as a plain rabbit's foot, and its front leg nothing. The piece counts them.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void cleaverTakesTorsoOrgansInOrder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3), TableAttachment.SURGICAL);
        Player surgeon = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack blade = new ItemStack(BBItems.CLEAVER.get());
        table.put(piece(COW, null));
        List<ResourceLocation> order = new ArrayList<>();
        while (Surgery.harvest(level, surgeon, table, blade)) {
            order.add(Organs.of(surgeon.getInventory().items.stream().filter(s -> !s.isEmpty()).reduce((a, b) -> b).orElseThrow(), PartsData.SERVER).organ());
            if (order.size() > 8) {
                break;
            }
        }
        if (!order.equals(List.of(bb("heart"), bb("lungs"), bb("stomach"), bb("rumen"))) || Surgery.organsTaken(CarcassPieceItem.piece(table.item())) != 4) {
            helper.fail("A cow's torso should give its heart, lungs, stomach and rumen in that order: " + order);
            return;
        }
        table.take();
        Player bomber = helper.makeMockPlayer(GameType.SURVIVAL);
        table.put(piece(CREEPER, null));
        if (!Surgery.harvest(level, bomber, table, blade)) {
            helper.fail("A creeper's torso should give its powder sac");
            return;
        }
        ItemStack sac = first(bomber, BBItems.GLAND.get());
        int gunpowder = count(bomber, Items.GUNPOWDER);
        if (!new CarcassArmour.Organ(bb("powder_sac"), CREEPER, false).equals(Organs.of(sac, PartsData.SERVER)) || gunpowder < 1 || gunpowder > 2
                || Surgery.harvest(level, bomber, table, blade)) {
            helper.fail("A creeper's torso should give its powder sac and 1-2 gunpowder, then nothing: " + sac.getComponents() + ", gunpowder " + gunpowder);
            return;
        }
        table.take();
        Player hunter = helper.makeMockPlayer(GameType.SURVIVAL);
        table.put(piece(RABBIT, "right_front_leg"));
        boolean front = Surgery.harvest(level, hunter, table, blade);
        table.take();
        table.put(piece(RABBIT, "left_haunch"));
        boolean hind = Surgery.harvest(level, hunter, table, blade);
        ItemStack foot = first(hunter, Items.RABBIT_FOOT);
        if (front || !hind || foot.isEmpty() || !foot.getComponentsPatch().isEmpty() || Surgery.harvest(level, hunter, table, blade)) {
            helper.fail("A rabbit's hind leg should give a plain rabbit's foot and its front leg nothing: front " + front + ", hind " + hind + " "
                    + foot.getComponentsPatch());
            return;
        }
        helper.succeed();
    }

    /**
     * A skeleton has no blood but still has something to give (spec 7.1: "bloodless mobs now give their core"): its torso
     * gives its marrow, with a little bone meal, dry (the Cleaver comes away clean), then nothing; its head gives nothing.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void skeletonGivesMarrow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SurgeryTableBlockEntity table = table(helper, new BlockPos(3, 2, 3), TableAttachment.SURGICAL);
        Player surgeon = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack blade = new ItemStack(BBItems.CLEAVER.get());
        table.put(piece(SKELETON, null));
        boolean cut = Surgery.harvest(level, surgeon, table, blade);
        ItemStack marrow = first(surgeon, BBItems.GLAND.get());
        int meal = count(surgeon, Items.BONE_MEAL);
        if (!cut || !new CarcassArmour.Organ(bb("marrow"), SKELETON, false).equals(Organs.of(marrow, PartsData.SERVER)) || meal < 1 || meal > 2) {
            helper.fail("A skeleton's torso should give its marrow and 1-2 bone meal: " + marrow.getComponents() + ", bone meal " + meal);
            return;
        }
        if (Surgery.harvest(level, surgeon, table, blade) || blade.has(BBDataComponents.BLOODIED_AT.get())) {
            helper.fail("A skeleton's torso should give nothing more, and leave the Cleaver clean");
            return;
        }
        table.take();
        table.put(piece(SKELETON, "head"));
        if (Surgery.harvest(level, surgeon, table, blade)) {
            helper.fail("A skeleton's head has nothing in it");
            return;
        }
        helper.succeed();
    }

    /**
     * A cow too heavy to carry, lying over a Surgical Rig table with nothing laid on it: the Cleaver takes its organs out
     * where it lies, its torso's first (heart, lungs, stomach, rumen), then its head's two eyes, each bone counting its
     * own, and then nothing; the Cleaver is never laid on the table meanwhile.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void heavyCarcassOrgansOnRig(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(5, 2, 5);
        SurgeryTableBlockEntity table = table(helper, at, TableAttachment.SURGICAL);
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 3, 5));
        CarcassSavedData.Carcass carcass = CarcassAssembler.assemble(cow, null);
        if (carcass == null) {
            helper.fail("Carcass assembly returned null");
            return;
        }
        cow.discard();
        Player butcher = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(30, () -> {
            try {
                if (Surgery.carcassOn(level, table.getBlockPos()) != carcass) {
                    helper.fail("The cow should lie over the table");
                    return;
                }
                List<ResourceLocation> order = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    int before = butcher.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
                    click(helper, butcher, at, new ItemStack(BBItems.CLEAVER.get()));
                    int after = butcher.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
                    if (after == before) {
                        break;
                    }
                    ItemStack newest = butcher.getInventory().items.stream().filter(s -> !s.isEmpty() && !s.is(BBItems.CLEAVER.get()))
                            .filter(s -> Organs.of(s, PartsData.SERVER) != null).reduce((a, b) -> b).orElse(ItemStack.EMPTY);
                    order.add(Organs.of(newest, PartsData.SERVER) == null ? bb("none") : Organs.of(newest, PartsData.SERVER).organ());
                }
                if (!order.equals(List.of(bb("heart"), bb("lungs"), bb("stomach"), bb("rumen"), bb("eye"), bb("eye"))) || !table.item().isEmpty()
                        || count(butcher, BBItems.EYE.get()) != 2 || !"4".equals(carcass.traits.get(Surgery.ORGANS_TAKEN + ":" + carcass.rootBone))) {
                    helper.fail("The cow lying on the table should give its torso's four organs and its head's two eyes, the Cleaver kept in hand: "
                            + order + ", table " + table.item() + ", " + carcass.traits);
                    return;
                }
                helper.succeed();
            } finally {
                // other tests share this world's carcasses: this one goes
                var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(level);
                for (java.util.UUID id : List.copyOf(carcass.bones.values())) {
                    if (container != null && container.getSubLevel(id) instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel body && !body.isRemoved()) {
                        ((dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer) container).removeSubLevel(body,
                                dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
                    }
                }
                CarcassSavedData.get(level).forget(carcass);
            }
        });
    }

    // ---- fitting

    /**
     * An organ fits only the pieces its file names: a cow's rumen a chestplate or leggings, never a helmet or boots; a
     * rabbit's foot leggings or boots, never a chestplate. A creeper's sac fitted in place of the rumen gives the rumen
     * back exactly as it went in, and the piece then gives Blast.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void glandFitsOnlyAllowedPieces(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        ItemStack rumen = Organs.stack(store, bb("rumen"), COW, false);
        ItemStack foot = new ItemStack(Items.RABBIT_FOOT);
        if (craft(helper, TestTraits.piece("helmet", COW), rumen) != null || craft(helper, TestTraits.piece("boots", COW), rumen) != null
                || craft(helper, TestTraits.piece("leggings", COW), rumen) == null || craft(helper, TestTraits.piece("chestplate", RABBIT), foot) != null
                || craft(helper, TestTraits.piece("boots", RABBIT), foot) == null) {
            helper.fail("A rumen should fit only a chestplate or leggings, a rabbit's foot only leggings or boots");
            return;
        }
        List<ItemStack> withRumen = craft(helper, TestTraits.piece("chestplate", COW), rumen);
        if (withRumen == null || withRumen.size() != 1
                || !CarcassArmourItem.armour(withRumen.get(0)).organ().equals(Optional.of(new CarcassArmour.Organ(bb("rumen"), COW, false)))) {
            helper.fail("A cow's rumen should fit a cow chestplate, nothing coming back");
            return;
        }
        ItemStack sac = Organs.stack(store, bb("powder_sac"), CREEPER, false);
        List<ItemStack> swapped = craft(helper, withRumen.get(0), sac);
        if (swapped == null || swapped.size() != 2 || !ItemStack.isSameItemSameComponents(swapped.get(1), rumen)
                || !CarcassArmourItem.armour(swapped.get(0)).organ().equals(Optional.of(new CarcassArmour.Organ(bb("powder_sac"), CREEPER, false)))) {
            helper.fail("The sac should take the rumen's place and the rumen come back as it went in: " + swapped);
            return;
        }
        boolean blast = CarcassArmourItem.armour(swapped.get(0)).traits(store).stream().anyMatch(t -> t.id().equals(bb("blast")));
        boolean cleanse = CarcassArmourItem.armour(withRumen.get(0)).traits(store).stream().anyMatch(t -> t.id().equals(bb("cleanse")));
        if (!blast || !cleanse) {
            helper.fail("The rumen in armour should give Cleanse (" + cleanse + "), the sac Blast (" + blast + ")");
            return;
        }
        helper.succeed();
    }

    /**
     * The whole path: a creeper's torso laid on the Surgical Rig, cut by a player's Cleaver click; the powder sac fitted to a
     * creeper chestplate in a crafting grid (a helmet refuses it); the chestplate, with a tank of blood strapped on, worn;
     * the Organ Ability key pressed below half health: the blast spares the wearer, hurts the zombie beside them and costs
     * 50 mB.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void creeperSacChestplateEndToEnd(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = new BlockPos(1, 2, 1);
        SurgeryTableBlockEntity table = table(helper, at, TableAttachment.SURGICAL);
        table.put(piece(CREEPER, null));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        click(helper, player, at, new ItemStack(BBItems.CLEAVER.get()));
        ItemStack sac = first(player, BBItems.GLAND.get());
        if (sac.isEmpty() || count(player, Items.GUNPOWDER) < 1) {
            helper.fail("The Cleaver's click should cut the creeper's powder sac out, with gunpowder");
            return;
        }
        if (craft(helper, TestTraits.piece("helmet", CREEPER), sac) != null) {
            helper.fail("A powder sac should not fit a helmet");
            return;
        }
        List<ItemStack> made = craft(helper, TestTraits.piece("chestplate", CREEPER), sac);
        if (made == null) {
            helper.fail("The powder sac should fit a creeper chestplate");
            return;
        }
        ItemStack tank = new ItemStack(BBItems.backtank(BacktankTier.IRON));
        FluidBacktankItem.setFluid(tank, new FluidStack(BBFluids.blood(), 1000));
        player.setItemSlot(EquipmentSlot.CHEST, FluidBacktankItem.strap(made.get(0), tank));
        Vec3 stand = helper.absoluteVec(new Vec3(5.5, 2.0, 5.5));
        player.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        level.addFreshEntity(player);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 5));
        zombie.setNoAi(true);
        float zombieHealth = zombie.getHealth();
        try {
            ActiveTraits.rebuild(player);
            if (ActiveTraits.of(player).level(bb("blast")) != 1) {
                helper.fail("The fitted sac should give the worn chestplate Blast: " + ActiveTraits.of(player).entries());
                return;
            }
            player.setHealth(8.0F);
            GameRules.BooleanValue griefing = level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING);
            boolean griefed = griefing.get();
            try {
                // the rule is the whole server's: changed and put back within this one call
                griefing.set(false, level.getServer());
                OrganActivatePayload.handle(player);
            } finally {
                griefing.set(griefed, level.getServer());
            }
            int left = FluidBacktankItem.fluid(player.getItemBySlot(EquipmentSlot.CHEST)).getAmount();
            boolean hurt = !zombie.isAlive() || zombie.getHealth() < zombieHealth;
            if (player.getHealth() != 8.0F || !hurt || left != 950) {
                helper.fail("The blast should spare its wearer (" + player.getHealth() + " of 8), hurt the zombie (" + hurt + ") and cost 50 mB (" + left + " left)");
                return;
            }
        } finally {
            player.discard();
            zombie.discard();
        }
        helper.succeed();
    }

    /**
     * A Gland used on a minion being built on the Assembly Frame goes into its organ slot, and a second takes its place,
     * the first coming back as it went in. The minion's traits are the organ's minion traits: a woken cow with a creeper's
     * sac in it has Self-Destruct; with a cow's rumen, Forager.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void glandInMinionGivesTraits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        PartsData.Store store = PartsData.SERVER;
        BlockPos at = new BlockPos(3, 2, 3);
        SurgeryTableBlockEntity table = table(helper, at, TableAttachment.ASSEMBLY);
        Player maker = helper.makeMockPlayer(GameType.SURVIVAL);
        if (!MinionAssembly.layDown(table, piece(COW, null), level)) {
            helper.fail("A cow torso should lie down as a frame");
            return;
        }
        ItemStack sac = Organs.stack(store, bb("powder_sac"), CREEPER, false);
        click(helper, maker, at, sac.copy());
        MinionBuild withSac = table.build().orElseThrow();
        if (!withSac.organ().equals(Optional.of(new CarcassArmour.Organ(bb("powder_sac"), CREEPER, false))) || !maker.getMainHandItem().isEmpty()) {
            helper.fail("The sac should go into the frame's organ slot from the hand: " + withSac.organ() + ", hand " + maker.getMainHandItem());
            return;
        }
        boolean destructs = MinionData.traits(store, withSac).stream().flatMap(List::stream).anyMatch(t -> t.id().equals(bb("self_destruct")));
        click(helper, maker, at, Organs.stack(store, bb("rumen"), COW, false));
        MinionBuild withRumen = table.build().orElseThrow();
        ItemStack back = first(maker, BBItems.GLAND.get());
        boolean forages = MinionData.traits(store, withRumen).stream().flatMap(List::stream).anyMatch(t -> t.id().equals(bb("forager")));
        if (!destructs || !forages || !ItemStack.isSameItemSameComponents(back, sac)
                || !withRumen.organ().equals(Optional.of(new CarcassArmour.Organ(bb("rumen"), COW, false)))) {
            helper.fail("The sac should give Self-Destruct (" + destructs + "), the rumen Forager (" + forages + "), and the sac come back as it went in: " + back);
            return;
        }
        // woken: the organ's trait is the minion's
        MinionEntity minion = BBEntities.MINION.get().create(level);
        BlockPos where = helper.absolutePos(new BlockPos(6, 2, 6));
        maker.moveTo(where.getX() + 1.5, where.getY(), where.getZ() + 0.5);
        minion.moveTo(where.getX() + 0.5, where.getY(), where.getZ() + 0.5, 0.0F, 0.0F);
        minion.setup(maker, where, MinionBuild.of(PieceRef.of(CarcassPieceItem.piece(piece(COW, null)))).withOrgan(withSac.organ()), 1000.0F);
        minion.setNoAi(true);
        level.addFreshEntity(minion);
        int level1 = ActiveTraits.of(minion).level(bb("self_destruct"));
        minion.discard();
        if (level1 != 1) {
            helper.fail("A woken minion with the sac in it should have Self-Destruct: " + ActiveTraits.of(minion).entries());
            return;
        }
        helper.succeed();
    }

    // ---- vanilla organs

    /**
     * The organ_sources data map: a plain ink sac is a squid's ink sac, a glow ink sac a glow squid's glow sac, a spider eye
     * a spider's eye, a rabbit's foot a rabbit's; each fits armour as that. An ink sac cut out of a squid comes out plain
     * (it stacks with the ones squid drop); one cut out of a glow squid is stamped as the glow squid's.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void organSourcesInkSacIsSquid(GameTestHelper helper) {
        PartsData.Store store = PartsData.SERVER;
        if (!new CarcassArmour.Organ(bb("ink_sac"), SQUID, false).equals(Organs.of(new ItemStack(Items.INK_SAC), store))
                || !new CarcassArmour.Organ(bb("glow_sac"), GLOW_SQUID, false).equals(Organs.of(new ItemStack(Items.GLOW_INK_SAC), store))
                || !new CarcassArmour.Organ(bb("eye"), SPIDER, false).equals(Organs.of(new ItemStack(Items.SPIDER_EYE), store))
                || !new CarcassArmour.Organ(bb("rabbit_foot"), RABBIT, false).equals(Organs.of(new ItemStack(Items.RABBIT_FOOT), store))
                || Organs.of(new ItemStack(Items.BONE), store) != null) {
            helper.fail("Ink sacs, glow ink sacs, spider eyes and rabbit's feet should be their mobs' organs, and a bone none");
            return;
        }
        ItemStack squids = Organs.stack(store, bb("ink_sac"), SQUID, false);
        ItemStack glowSquids = Organs.stack(store, bb("ink_sac"), GLOW_SQUID, false);
        if (!squids.is(Items.INK_SAC) || !squids.getComponentsPatch().isEmpty() || !glowSquids.is(Items.INK_SAC)
                || !new CarcassArmour.Organ(bb("ink_sac"), GLOW_SQUID, false).equals(Organs.of(glowSquids, store))) {
            helper.fail("A squid's ink sac should come out plain, a glow squid's stamped as its own: " + squids.getComponentsPatch() + " / " + glowSquids.getComponentsPatch());
            return;
        }
        List<ItemStack> inked = craft(helper, TestTraits.piece("chestplate", SQUID), new ItemStack(Items.INK_SAC));
        List<ItemStack> eyed = craft(helper, TestTraits.piece("helmet", SPIDER), new ItemStack(Items.SPIDER_EYE));
        if (inked == null || !CarcassArmourItem.armour(inked.get(0)).organ().equals(Optional.of(new CarcassArmour.Organ(bb("ink_sac"), SQUID, false)))
                || eyed == null || !CarcassArmourItem.armour(eyed.get(0)).organ().equals(Optional.of(new CarcassArmour.Organ(bb("eye"), SPIDER, false)))) {
            helper.fail("A plain ink sac should fit a chestplate as a squid's, a spider eye a helmet as a spider's");
            return;
        }
        helper.succeed();
    }

    // ---- words

    /**
     * Every organ's bloodless name is a machine part (no sac, gland, heart, marrow...); the Gland itself is a Core, and the
     * new words (its description, where it came from, what is still inside a piece, the Body Parts page, the traits
     * command's organs) have bloodless wording. The Gland's model has a clean machined look for every look, drawn.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bloodlessOrganNames(GameTestHelper helper) {
        JsonObject lang = lang(helper);
        if (lang == null) {
            return;
        }
        List<String> problems = new ArrayList<>();
        for (Map.Entry<ResourceLocation, OrganKind> e : PartsData.SERVER.organs().entrySet()) {
            String key = e.getValue().bloodlessName().orElse("bloodless." + e.getValue().name());
            if (!lang.has(key) || FLESHY.matcher(lang.get(key).getAsString()).find()) {
                problems.add(e.getKey() + " is '" + (lang.has(key) ? lang.get(key).getAsString() : "missing") + "' in bloodless mode");
            }
        }
        for (String key : List.of("item.bloodandbones.gland", "item.bloodandbones.gland.tooltip.summary", "item.bloodandbones.gland.tooltip.behaviour1",
                "item.bloodandbones.gland.tooltip.behaviour2", "bloodandbones.organ.of", "bloodandbones.organ.inside", "bloodandbones.organ.minion",
                "bloodandbones.jei.category.body_parts", "bloodandbones.jei.body_parts.organs", "bloodandbones.jei.body_parts.hide_line",
                "bloodandbones.jei.gland.1", "bloodandbones.jei.gland.2", "bloodandbones.command.explain.organs", "bloodandbones.command.explain.hide",
                "block.bloodandbones.surgery_table.tooltip.behaviour5")) {
            String shown = !lang.has(key) ? null : lang.has("bloodless." + key) ? lang.get("bloodless." + key).getAsString()
                    : BloodlessWords.soften(lang.get(key).getAsString());
            if (shown == null || FLESHY.matcher(shown).find()) {
                problems.add(key + " is '" + shown + "' in bloodless mode");
            }
        }
        if (!lang.has("bloodless.item.bloodandbones.gland") || !lang.get("bloodless.item.bloodandbones.gland").getAsString().equals("Core")) {
            problems.add("the Gland is not a Core in bloodless mode");
        }
        // the model: every look has its bloodless model, and it and its textures exist
        try (var in = BloodAndBones.class.getResourceAsStream("/assets/bloodandbones/models/item/gland.json")) {
            JsonArray overrides = JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject().getAsJsonArray("overrides");
            for (int i = 0; i < OrganKind.LOOKS.size(); i++) {
                String clean = null;
                for (JsonElement e : overrides) {
                    JsonObject predicate = e.getAsJsonObject().getAsJsonObject("predicate");
                    if (predicate.get("bloodandbones:look").getAsFloat() == i && predicate.get("bloodandbones:bloodless").getAsFloat() == 1.0F) {
                        clean = e.getAsJsonObject().get("model").getAsString();
                    }
                }
                String look = OrganKind.LOOKS.get(i);
                if (clean == null || !clean.equals("bloodandbones:item/gland_" + look + "_clean")
                        || BloodAndBones.class.getResource("/assets/bloodandbones/models/item/gland_" + look + "_clean.json") == null
                        || BloodAndBones.class.getResource("/assets/bloodandbones/textures/item/gland/" + look + "_clean.png") == null
                        || BloodAndBones.class.getResource("/assets/bloodandbones/textures/item/gland/" + look + "_gore.png") == null) {
                    problems.add("the " + look + " look has no clean model or texture (" + clean + ")");
                }
            }
        } catch (Exception e) {
            problems.add("the Gland's model: " + e);
        }
        if (!problems.isEmpty()) {
            helper.fail("Bloodless organ words " + BreadthTests.shortList(problems));
            return;
        }
        helper.succeed();
    }

    // ---- commands

    /**
     * "/bloodandbones traits explain minecraft:creeper" runs and says its layers (the bloodless overlay), its organs (the
     * Powder Sac) and what the sac does (Self-Destruct, Blast); "/bloodandbones traits dump" writes a CSV with the sac's
     * Blast in a chestplate among its rows.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void explainCommandRuns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> said = new ArrayList<>();
        net.minecraft.commands.CommandSource listener = new net.minecraft.commands.CommandSource() {
            @Override
            public void sendSystemMessage(Component message) {
                said.add(message.getString());
            }

            @Override
            public boolean acceptsSuccess() {
                return true;
            }

            @Override
            public boolean acceptsFailure() {
                return true;
            }

            @Override
            public boolean shouldInformAdmins() {
                return false;
            }
        };
        net.minecraft.commands.CommandSourceStack source = new net.minecraft.commands.CommandSourceStack(listener, Vec3.atCenterOf(helper.absolutePos(BlockPos.ZERO)),
                net.minecraft.world.phys.Vec2.ZERO, level, 4, "organ_test", Component.literal("organ_test"), level.getServer(), null);
        level.getServer().getCommands().performPrefixedCommand(source, "bloodandbones traits explain minecraft:creeper");
        String all = String.join("\n", said);
        if (said.size() < 5 || !all.contains("bloodless_mob") || !all.contains("Powder Sac") || !all.contains("Self-Destruct") || !all.contains("Blast")) {
            helper.fail("Explain should list the creeper's layers, its powder sac and what it does: " + all);
            return;
        }
        said.clear();
        level.getServer().getCommands().performPrefixedCommand(source, "bloodandbones traits dump");
        java.nio.file.Path file = level.getServer().getServerDirectory().resolve(TraitsCommand.DUMP_FILE);
        List<String> rows;
        try {
            rows = java.nio.file.Files.readAllLines(file);
        } catch (java.io.IOException e) {
            helper.fail("The dump should write " + file + ": " + e + " (said " + said + ")");
            return;
        }
        boolean sacRow = rows.stream().anyMatch(r -> r.startsWith("minecraft:creeper,") && r.contains("organ:bloodandbones:powder_sac,armour:chestplate,bloodandbones:blast,1"));
        if (rows.isEmpty() || !rows.get(0).startsWith("mob,") || !sacRow || rows.size() < 500) {
            helper.fail("The dump should have a header and the creeper's sac's Blast among " + rows.size() + " rows");
            return;
        }
        helper.succeed();
    }
}
