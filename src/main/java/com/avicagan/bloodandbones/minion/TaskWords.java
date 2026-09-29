package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.TraitList;
import com.avicagan.bloodandbones.parts.Traits;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The words a minion's fitness is shown in (docs/NEXT.md 1.3 and 1.4): a task's name and how fit it is ("Farmer 120%,
 * Able"), where it works, what it waits for, and each part of a row with where it came from ({@link MinionFitness.Source}
 * in the screen's words: "Hands ×1.3: hand, 4 of them: Zombie arm"). Built on the server as translatable text, so each
 * client reads it in its own language, reworded in bloodless mode.
 */
public final class TaskWords {
    /** The words for how fit it is, from the bottom: Hopeless under 50%, Fair to 90%, Able to 130%, Good to 170%, Born to it above. */
    private static final float[] BANDS = {0.5F, 0.9F, 1.3F, 1.7F};
    private static final String[] WORDS = {"hopeless", "fair", "able", "good", "born"};

    private TaskWords() {
    }

    /** Which of the five words a fitness is: 0 (Hopeless) to 4 (Born to it). */
    public static int band(float fitness) {
        int band = 0;
        while (band < BANDS.length && fitness >= BANDS[band] - 1.0E-4F) {
            band++;
        }
        return band;
    }

    /** The key of its word: "bloodandbones.minion.fit.able". */
    public static String wordKey(float fitness) {
        return "bloodandbones.minion.fit." + WORDS[band(fitness)];
    }

    public static Component name(MinionTask task) {
        return Component.translatable(task.nameKey());
    }

    /** "142%". */
    public static String percent(float fitness) {
        return Math.round(fitness * 100.0F) + "%";
    }

    /** A number as the screen shows it: whole if it is, else to two places ("1.5", "0.33"). */
    public static String number(float value) {
        if (Math.abs(value - Math.round(value)) < 0.005F) {
            return Integer.toString(Math.round(value));
        }
        String out = String.format(Locale.ROOT, "%.2f", value);
        return out.endsWith("0") ? out.substring(0, out.length() - 1) : out;
    }

    /**
     * Where it works now: at home (a sentry at its post, a surgeon at its table), with its maker, or at home while its maker
     * is away (docs/NEXT.md 1.1).
     */
    public static Component where(MinionEntity minion) {
        if (minion.anchor() == MinionTask.Anchor.MAKER && PartsData.of(minion.level()).task(minion.task()).allows(MinionTask.Anchor.MAKER)) {
            return Component.translatable(minion.withMaker() ? "bloodandbones.minion.where.maker" : "bloodandbones.minion.where.maker_away");
        }
        return Component.translatable(switch (minion.task()) {
            case SENTRY -> "bloodandbones.minion.where.post";
            case SURGEON -> atTable(minion) ? "bloodandbones.minion.where.table" : "bloodandbones.minion.where.home";
            default -> "bloodandbones.minion.where.home";
        });
    }

    /** Whether its home is a Surgery Table (a home unloaded, far off, is not looked at). */
    private static boolean atTable(MinionEntity minion) {
        return minion.level().isLoaded(minion.home())
                && minion.level().getBlockState(minion.home()).getBlock() instanceof com.avicagan.bloodandbones.body.SurgeryTableBlock;
    }

    /** What it is doing, for its status line: "Farmer 120% at home", "Idle with its maker". */
    public static Component doing(MinionEntity minion) {
        MinionTask task = minion.task();
        if (!task.rated()) {
            return Component.translatable("bloodandbones.minion.doing_idle", name(task), where(minion));
        }
        return Component.translatable("bloodandbones.minion.doing", name(task), percent(minion.fitness(task)), where(minion));
    }

    /**
     * What stands in its way, for its status line: the tool or rule it waits for ("waiting for a Cleaver or a Flensing
     * Knife"), what its work waits on in the world ("no still water within 8 of home"), or why a data reload took its task.
     * Null for nothing.
     */
    @Nullable
    public static Component waiting(MinionEntity minion) {
        MinionTask lost = minion.lostTask();
        if (lost != null && minion.lostReason() != null) {
            return Component.translatable("bloodandbones.minion.lost", name(lost), Component.translatable(minion.lostReason()));
        }
        MinionTask task = minion.task();
        if (task.rated()) {
            MinionFitness.Row row = minion.row(task, minion.anchor());
            if (row.waitsFor().isPresent()) {
                return waits(PartsData.of(minion.level()), task, row.waitsFor().get());
            }
        }
        return minion.idleReason();
    }

    /**
     * What a row waits for, in words: its task's tool as the task file names it where the file narrows it ("waiting for a
     * Flensing Knife"), the task's own words otherwise.
     */
    public static Component waits(PartsData.Store store, MinionTask task, String key) {
        if (key.equals("bloodandbones.minion.wants." + task.id.getPath())) {
            java.util.Optional<String> items = store.task(task).tool().flatMap(MinionTask.Tool::items);
            if (items.isPresent()) {
                return Component.translatable("bloodandbones.minion.wants.items", items(items.get()));
            }
        }
        return Component.translatable(key);
    }

    /** Its task's tool in words: the task file's items where it names them, the task's own words otherwise ("a fishing rod"). */
    public static Component tool(PartsData.Store store, MinionTask task) {
        return store.task(task).tool().flatMap(MinionTask.Tool::items).map(TaskWords::items)
                .orElseGet(() -> Component.translatable("bloodandbones.minion.tool." + task.id.getPath()));
    }

    /** An item id's name, or a "#tag"'s (its conventional name where it has one, the tag itself otherwise). */
    static Component items(String items) {
        if (items.startsWith("#")) {
            net.minecraft.resources.ResourceLocation tag = net.minecraft.resources.ResourceLocation.tryParse(items.substring(1));
            return tag == null ? Component.literal(items) : Component.translatableWithFallback(net.neoforged.neoforge.common.Tags.getTagTranslationKey(
                    net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tag)), items);
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(items);
        return id == null ? Component.literal(items) : BuiltInRegistries.ITEM.getOptional(id).map(item -> item.getDescription())
                .orElse(Component.literal(items));
    }

    // ---- what a part brings, for JEI's Body Parts page and a piece's tooltip (docs/NEXT.md 1.4)

    /**
     * What a part brings to a minion's tasks, from its mob's data: its knacks ("Knacks: Surgeon ×1.5, Farmer ×1.25"), what it
     * holds things with (an arm's grip, and a front leg's, which works by hand on a body of four legs or more) and, for a
     * head, its disposition and whether it is a surgeon's head. These are the part's own facts: a fitness needs a whole
     * build. Knacks of 1 change nothing and are left out. {@code traits} are what the carcass kept (a villager's
     * profession), which its data's variants read; none reads the part as its mob has it.
     */
    public static List<Component> partFacts(com.avicagan.bloodandbones.parts.ResolvedMob mob, java.util.Map<String, String> traits, String key) {
        List<Component> out = new ArrayList<>();
        List<Component> knacks = new ArrayList<>();
        MinionData.knacks(mob, traits, key).forEach((id, value) -> {
            if (Math.abs(value - 1.0F) > 1.0E-3F) {
                MinionTask task = MinionTask.byId(id);
                knacks.add(Component.translatable("bloodandbones.minion.facts.knack", task == null ? Component.literal(id.toString()) : name(task), number(value)));
            }
        });
        if (!knacks.isEmpty()) {
            out.add(Component.translatable("bloodandbones.minion.facts.knacks", join(knacks)));
        }
        String base = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
        switch (base) {
            case "arm" -> {
                String grip = MinionData.field(mob, traits, key, "grip").filter(com.google.gson.JsonElement::isJsonPrimitive)
                        .map(com.google.gson.JsonElement::getAsString).orElse("hand");
                int hands = Math.max(1, Math.round(MinionData.scalar(mob, traits, key, "hands", 1.0F)));
                if (!"none".equals(grip)) {
                    out.add(Component.translatable(hands > 1 ? "bloodandbones.minion.facts.grip_pair" : "bloodandbones.minion.facts.grip", grip(grip), hands));
                }
            }
            case "leg" -> MinionData.field(mob, traits, key, "grip").filter(com.google.gson.JsonElement::isJsonPrimitive)
                    .map(com.google.gson.JsonElement::getAsString).filter(g -> !"none".equals(g))
                    .ifPresent(g -> out.add(Component.translatable("bloodandbones.minion.facts.leg_grip", grip(g), MinionStats.GRIPPING_LEGS)));
            case "head" -> {
                String disposition = MinionData.field(mob, traits, "head", "disposition").filter(com.google.gson.JsonElement::isJsonPrimitive)
                        .map(com.google.gson.JsonElement::getAsString).orElse("none");
                out.add(Component.translatable("bloodandbones.minion.facts.disposition", Component.translatable(MinionDisposition.nameKey(disposition))));
                if (MinionData.field(mob, traits, "head", "surgeon").filter(com.google.gson.JsonElement::isJsonPrimitive)
                        .map(com.google.gson.JsonElement::getAsBoolean).orElse(false)) {
                    out.add(Component.translatable("bloodandbones.minion.facts.surgeon"));
                }
            }
            default -> {
            }
        }
        return out;
    }

    /**
     * What a part brings as a mob of its kind has it new (JEI's Body Parts page, one per mob; docs/NEXT.md 1.4): its facts
     * read with the traits a new one records of itself ({@code fresh}: an unemployed villager's profession, "none"), and
     * which of the traits a carcass keeps change them ("Its profession changes these"): those a new one records, and a name,
     * which any mob may be given. A carried piece's tooltip reads its own traits instead ({@link #partFacts}).
     */
    public static List<Component> mobFacts(com.avicagan.bloodandbones.parts.ResolvedMob mob, java.util.Map<String, String> fresh, String key) {
        List<Component> out = partFacts(mob, fresh, key);
        List<Component> varies = new ArrayList<>();
        for (String trait : MinionData.variantTraits(mob, key, "knacks", "jobs", "disposition", "surgeon")) {
            if (fresh.containsKey(trait) || "name".equals(trait)) {
                varies.add(Component.translatableWithFallback("bloodandbones.minion.facts.trait." + trait, trait));
            }
        }
        if (!varies.isEmpty() && !out.isEmpty()) {
            out.add(Component.translatable("bloodandbones.minion.facts.varies", or(varies)));
        }
        return out;
    }

    /** These, with "or" before the last. */
    private static Component or(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(i == parts.size() - 1 ? Component.translatable("bloodandbones.minion.facts.or") : Component.literal(", "));
            }
            out.append(parts.get(i));
        }
        return out;
    }

    /** A grip's name: "hand", "paw"; one the mod has no words for as its data names it. */
    private static Component grip(String grip) {
        return Component.translatableWithFallback("bloodandbones.minion.grip." + grip, grip);
    }

    // ---- a row's reasons, for the task screen's hover (docs/NEXT.md 1.3)

    /**
     * A row's lines, as the task screen shows them over the row: its name and fitness and word (or why it cannot), what it
     * does, what it waits for and what a tool it lacks would make of it, then each stat it reads with its value and where
     * that came from, its knack, its disposition, what its fitness makes of its work (its levers), and the blood it uses at
     * work.
     */
    public static List<Component> lines(PartsData.Store store, MinionEntity minion, MinionFitness.Row row, MinionTask.Anchor at) {
        List<Component> out = new ArrayList<>();
        MinionTask task = row.task();
        if (!task.rated()) {
            out.add(name(task).copy().withStyle(ChatFormatting.WHITE));
        } else if (!row.can()) {
            out.add(Component.translatable("bloodandbones.minion.screen.cannot_line", name(task), Component.translatable("bloodandbones.minion.screen.cannot"))
                    .withStyle(ChatFormatting.WHITE));
            out.add(Component.translatable(row.cannot().get()).withStyle(ChatFormatting.RED));
        } else {
            out.add(Component.translatable("bloodandbones.minion.screen.fit_line", name(task), percent(row.fitness()), Component.translatable(wordKey(row.fitness())))
                    .withStyle(ChatFormatting.WHITE));
        }
        out.add(Component.translatable(task.nameKey() + ".desc").withStyle(ChatFormatting.GRAY));
        if (!task.rated() || !row.can()) {
            return out;
        }
        row.waitsFor().ifPresent(key -> out.add(waits(store, task, key).copy().withStyle(ChatFormatting.GOLD)));
        row.withTool().ifPresent(with -> out.add(Component.translatable("bloodandbones.minion.with_tool", tool(store, task), percent(with))
                .withStyle(ChatFormatting.GOLD)));
        row.main().ifPresent(f -> out.add(factor(store, f, false)));
        row.second().ifPresent(f -> out.add(factor(store, f, true)));
        if (Math.abs(row.knack() - 1.0F) > 1.0E-3F) {
            List<Component> from = new ArrayList<>();
            for (MinionStats.KnackPart part : row.knackFrom()) {
                from.add(Component.translatable("bloodandbones.minion.knack_part", knackSource(store, part), "×" + number(part.value())));
            }
            out.add(line("bloodandbones.minion.knack", number(row.knack()), join(from)));
        }
        if (Math.abs(row.disposition() - 1.0F) > 1.0E-3F) {
            out.add(line("bloodandbones.minion.disposition_line", number(row.disposition()),
                    Component.translatable(MinionDisposition.nameKey(row.dispositionName()))));
        }
        out.addAll(levers(store, minion, row));
        float drain = MinionFitness.workingDrain(row.fitness()) * (minion.cybernetic() ? MinionEntity.BRASS_DRAIN : 1.0F);
        out.add(Component.translatable(minion.cybernetic() ? "bloodandbones.minion.at_work_brass" : "bloodandbones.minion.at_work", number(drain))
                .withStyle(ChatFormatting.GRAY));
        if (at == MinionTask.Anchor.MAKER) {
            out.add(Component.translatable("bloodandbones.minion.screen.row_with_me").withStyle(ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    /**
     * What its fitness makes of its work (docs/NEXT.md 1.2), each today's at 100%: "A stroke every 0.4 s, keeping all of each
     * cut", "Looks round every 0.25 s". A guard's or hunter's fights run on its own damage, health and speed; how often it
     * strikes is its arms'.
     */
    static List<Component> levers(PartsData.Store store, MinionEntity minion, MinionFitness.Row row) {
        MinionTask.Data data = store.task(row.task());
        float f = row.fitness();
        List<Component> out = new ArrayList<>();
        switch (row.task()) {
            case GUARD, HUNTER -> out.add(lever("strike", seconds(MinionGoals.blowTicks(minion))));
            case SENTRY -> out.add(lever("sentry", seconds(MinionFitness.shotTicks(data.number("bow_every", 20.0F), f)),
                    number(MinionFitness.shotSpread(data, minion.level().getDifficulty().getId(), f))));
            case SURGEON -> {
                out.add(lever("surgeon", seconds(MinionFitness.tendTicks(data, f))));
                MinionFitness.Body body = minion.fitnessBody();
                out.add(body != null && MinionFitness.mayCut(data, body)
                        ? lever("surgeon_stump", com.avicagan.bloodandbones.body.Surgery.buckets(MinionFitness.stumpBuckets(data, f)))
                        : lever("surgeon_no_cut"));
            }
            case MEDIC -> out.add(lever("medic", seconds(MinionFitness.throwTicks(data, f)), number(MinionFitness.throwSpread(data, f))));
            case HERDER -> out.add(lever("herder", seconds(MinionFitness.strayTicks(data, f))));
            case TENDER, COURIER -> out.add(lever("look", seconds(MinionFitness.lookTicks(data, f))));
            case HAULER -> {
                // towing slowed as a player is, never less, or as many times more (to at most the task's most)
                float times = f >= 1.0F ? 1.0F : 1.0F / MinionFitness.lever(f);
                out.add(times <= 1.0F + 1.0E-4F ? lever("hauler_player") : lever("hauler", number(times), percent(data.number("slowdown_most", 0.9F))));
            }
            case FARMER -> out.add(lever("farmer", seconds(MinionFitness.lookTicks(data, f))));
            case FISHER -> {
                int[] catches = MinionFitness.catchTicks(data, f);
                out.add(lever("fisher", seconds(catches[0]), seconds(catches[1])));
            }
            case BUTCHER -> out.add(lever("butcher", seconds(MinionFitness.strokeTicks(data, f)), percent(MinionFitness.yieldShare(f))));
            case BARTERER -> out.add(lever("barterer", seconds(MinionFitness.admireTicks(data, f))));
            case DIGGER -> {
                int[] finds = MinionFitness.digTicks(data, f);
                out.add(lever("digger", seconds(finds[0]), seconds(finds[1])));
            }
            default -> {
            }
        }
        return out;
    }

    private static Component lever(String key, Object... args) {
        return Component.translatable("bloodandbones.minion.lever." + key, args).withStyle(ChatFormatting.GRAY);
    }

    /** Ticks as seconds: "0.4", "2.5", "30". */
    private static String seconds(int ticks) {
        return number(ticks / 20.0F);
    }

    private static Component line(String key, String multiplier, Component detail) {
        return Component.translatable(key, multiplier, detail).withStyle(ChatFormatting.GRAY);
    }

    /** "Hands ×1.3: hand, 4 of them: Zombie arm"; the second stat counts at half weight, and says so. */
    private static Component factor(PartsData.Store store, MinionFitness.Factor factor, boolean second) {
        float counts = second ? (float) Math.sqrt(factor.value()) : factor.value();
        List<Component> parts = new ArrayList<>();
        parts.add(value(factor));
        for (MinionFitness.Source source : factor.from()) {
            Component said = source(store, source);
            if (said != null) {
                parts.add(said);
            }
        }
        return Component.translatable(second ? "bloodandbones.minion.factor_second" : "bloodandbones.minion.factor",
                Component.translatable("bloodandbones.minion.stat." + factor.of().key()), number(counts), join(parts)).withStyle(ChatFormatting.GRAY);
    }

    /** What a stat was: "speed 0.34", "48 blocks", "hand", "2.5 a blow", "15 health", "9 slots", "weight 0.8". */
    private static Component value(MinionFitness.Factor factor) {
        return switch (factor.of()) {
            case PACE -> Component.translatable("bloodandbones.minion.value.pace", number(factor.stat()));
            case SIGHT -> Component.translatable("bloodandbones.minion.value.sight", number(factor.stat()));
            case HANDS -> {
                String grip = factor.from().stream().filter(s -> s.type() == MinionFitness.Source.Type.PIECE && s.detail().contains(":"))
                        .map(s -> s.detail().substring(s.detail().indexOf(':') + 1)).findFirst().orElse("none");
                yield Component.translatable("bloodandbones.minion.grip." + grip);
            }
            case BLOW -> Component.translatable("bloodandbones.minion.value.blow", number(factor.stat()));
            case RANGED -> Component.translatable(factor.stat() > 0.0F ? "bloodandbones.minion.value.ranged" : "bloodandbones.minion.value.no_ranged");
            case TOUGHNESS -> Component.translatable("bloodandbones.minion.value.toughness", number(factor.stat()));
            case CARRY -> Component.translatable("bloodandbones.minion.value.carry", number(factor.stat()));
            case PULL -> Component.translatable("bloodandbones.minion.value.pull", number(factor.stat()));
        };
    }

    /** Where part of a number came from, in words; null for what the value already says (no ranged attack). */
    @Nullable
    private static Component source(PartsData.Store store, MinionFitness.Source source) {
        return switch (source.type()) {
            case PIECE -> {
                String part = source.detail().contains(":") ? source.detail().substring(0, source.detail().indexOf(':')) : source.detail();
                yield Component.translatable("bloodandbones.minion.source.piece", mob(source.id()), Component.translatable("bloodandbones.minion.part." + part));
            }
            case TRAIT -> Traits.describe(store, new TraitList.Resolved(source.id(), parse(source.detail())));
            case ITEM -> BuiltInRegistries.ITEM.get(source.id()).getDescription();
            case RULE -> "no_ranged".equals(source.id().getPath()) ? null
                    : Component.translatable("bloodandbones.minion.rule." + source.id().getPath(), source.detail());
        };
    }

    private static Component knackSource(PartsData.Store store, MinionStats.KnackPart part) {
        if ("trait".equals(part.slot())) {
            return Traits.describe(store, new TraitList.Resolved(part.from(), 1));
        }
        return Component.translatable("bloodandbones.minion.source.piece", mob(part.from()), Component.translatable("bloodandbones.minion.part." + part.slot()));
    }

    /** A mob's name, from its entity type (a mob no longer in the game by its id). */
    private static Component mob(net.minecraft.resources.ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).map(type -> (Component) type.getDescription()).orElse(Component.literal(id.toString()));
    }

    private static int parse(String level) {
        try {
            return Integer.parseInt(level);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** These, with commas between. */
    private static Component join(List<Component> parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            out.append(parts.get(i));
        }
        return out;
    }
}
