package com.avicagan.bloodandbones.minion;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.item.CleaverItem;
import com.avicagan.bloodandbones.item.FlensingKnifeItem;
import com.avicagan.bloodandbones.parts.ActiveTraits;
import com.avicagan.bloodandbones.parts.PartSlot;
import com.avicagan.bloodandbones.parts.PartSlots;
import com.avicagan.bloodandbones.parts.PartsData;
import com.avicagan.bloodandbones.parts.TraitEffects;
import com.avicagan.bloodandbones.parts.Trigger;
import com.avicagan.bloodandbones.parts.effect.HitscanEffect;
import com.avicagan.bloodandbones.parts.effect.ProjectileEffect;
import com.avicagan.bloodandbones.parts.effect.StorageEffect;
import com.avicagan.bloodandbones.registry.BBAttributes;
import com.google.gson.JsonElement;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * How well a minion does each task (docs/NEXT.md 1.2), worked out with no world from its build, its stats, what it holds
 * and carries and the time of day: one number per task, shown as a percentage, where 100% is how the task works today.
 * <p>
 * fitness = knack × disposition × main × √second, held between {@link #LEAST} and {@link #MOST}, or it cannot. The main and
 * second are two of the stats a task's data names, each over the value that is 1 ({@link #PACE} and the rest), held
 * between {@link #FACTOR_LEAST} and {@link #FACTOR_MOST}; the knack comes from its parts' data and traits
 * ({@link MinionStats#knacks}), the disposition from its head ({@link MinionDisposition}). Stats that traits change are read
 * after them. A task is shut only when the body lacks what it needs (something to strike with, a detonating organ, anything
 * its grip table lets it work with); a missing tool never shuts one: it waits for it.
 * <p>
 * Pure, as {@link MinionStats} is: the same build, data and context always give the same rows, so a datapack's retune
 * reaches every minion, and none of it is saved. The levers the tasks' goals scale by it are here too ({@link #quicker}...).
 */
public final class MinionFitness {
    /** A fitness is held between 10% and 200%. */
    public static final float LEAST = 0.1F;
    public static final float MOST = 2.0F;
    /** Each stat over its reference is held between these. */
    public static final float FACTOR_LEAST = 0.25F;
    public static final float FACTOR_MOST = 2.0F;
    /** Where a lever uses the fitness, it is held between 25% and 200%, so nothing is ever more than four times slower. */
    public static final float LEVER_LEAST = 0.25F;
    public static final float LEVER_MOST = 2.0F;
    /** The blood used at work reads it held between 50% and 200%: 12.5 to 50 mB a minute. */
    public static final float BLOOD_LEAST = 0.5F;
    public static final float BLOOD_MOST = 2.0F;

    // what each stat is at 1 (docs/NEXT.md 1.2)
    /** Pace: movement speed; a zombie's legs 0.92, a rabbit's 1.3. */
    public static final float PACE = 0.25F;
    /** Sight: blocks it notices things within; most heads 1. */
    public static final float SIGHT = 16.0F;
    /** Blow: its hardest strike times its strike rate; a zombie's arm. */
    public static final float BLOW = 2.5F;
    /** Toughness: its most health. */
    public static final float TOUGHNESS = 20.0F;
    /** Carry: the slots it carries in. */
    public static final float CARRY = 9.0F;
    /** Pull: its torso mob's rig weight, whose square root counts (a cow's). */
    public static final float PULL = 0.8F;
    /** Ranged with none: a bow in a hand that fights, or an innate shot, is 1. */
    public static final float NO_RANGED = 0.25F;
    /** Each holder of the best kind past two adds this to its hands, and each striking arm past two to its strike rate... */
    public static final float MORE = 0.15F;
    /** ...up to four more (+60%). */
    public static final int MOST_MORE = 4;
    /** A berserk head's blows land half again as hard. */
    public static final float BERSERK = MinionStats.BERSERK_DAMAGE;

    // the surgeon's stump (docs/NEXT.md 1.5)
    /** A surgeon at least this fit leaves a stump of one bucket, as the brief's surgeon heads do today... */
    public static final float CLEAN_CUT = 1.5F;
    /** ...one at least this fit a stump of two, and a worse one three. */
    public static final float FAIR_CUT = 0.75F;

    private MinionFitness() {
    }

    /**
     * What it has and where it is when its fitness is read: what it holds and carries, whether it is night, where the task is
     * centred (home, or with its maker), whether the server lets mobs grief (a hunter waits for it), and whether a chest is
     * fitted (a beast of burden's storage).
     */
    public record Context(ItemStack held, List<ItemStack> carried, boolean night, MinionTask.Anchor anchor, boolean griefing, boolean chested) {
        /** Holding nothing, carrying nothing, by day, at home, mobs allowed to grief, no chest. */
        public static final Context NONE = new Context(ItemStack.EMPTY, List.of(), false, MinionTask.Anchor.HOME, true, false);

        public Context holding(ItemStack stack) {
            return new Context(stack, carried, night, anchor, griefing, chested);
        }

        public Context carrying(List<ItemStack> stacks) {
            return new Context(held, List.copyOf(stacks), night, anchor, griefing, chested);
        }

        public Context atNight(boolean isNight) {
            return new Context(held, carried, isNight, anchor, griefing, chested);
        }

        public Context at(MinionTask.Anchor at) {
            return new Context(held, carried, night, at, griefing, chested);
        }

        public Context griefing(boolean allowed) {
            return new Context(held, carried, night, anchor, allowed, chested);
        }

        public Context chested(boolean fitted) {
            return new Context(held, carried, night, anchor, griefing, fitted);
        }
    }

    /**
     * Where part of a number came from, for the reasons a task screen shows: a piece of the build ({@code id} its mob,
     * {@code detail} its part and grip), a trait ({@code id} the trait, {@code detail} its level), an item it holds or
     * carries ({@code id} the item), or a rule ({@code id} the rule: "blind", "mindless", "sense", "bite", "own_way",
     * "more", "strike_rate", "berserk", "no_ranged", "no_tool", "chest"; {@code detail} a number).
     */
    public record Source(Type type, ResourceLocation id, String detail) {
        public enum Type {
            PIECE, TRAIT, ITEM, RULE
        }

        static Source piece(ResourceLocation mob, String detail) {
            return new Source(Type.PIECE, mob, detail);
        }

        static Source trait(ResourceLocation trait, int level) {
            return new Source(Type.TRAIT, trait, Integer.toString(level));
        }

        static Source rule(String rule, String detail) {
            return new Source(Type.RULE, BloodAndBones.asResource(rule), detail);
        }

        static Source item(ItemStack stack) {
            return new Source(Type.ITEM, BuiltInRegistries.ITEM.getKey(stack.getItem()), "");
        }
    }

    /**
     * One stat a task reads: what it is ({@code stat}: speed, blocks, grip, damage, health, slots, weight...), what it counts
     * for ({@code value}, held between {@link #FACTOR_LEAST} and {@link #FACTOR_MOST}), and where it came from.
     */
    public record Factor(MinionTask.Stat of, float stat, float value, List<Source> from) {
    }

    /**
     * One task's row: its fitness (with a tool it waits for taken as had), before it was held ({@code raw}), why it cannot
     * (a lang key; the fitness then means nothing), what it waits for (a lang key), the fitness a tool it lacks would give
     * where that differs, the stats it read, its knack and where that came from, and its disposition's share and name.
     */
    public record Row(MinionTask task, float fitness, float raw, Optional<String> cannot, Optional<String> waitsFor, Optional<Float> withTool,
                      Optional<Factor> main, Optional<Factor> second, float knack, List<MinionStats.KnackPart> knackFrom, float disposition,
                      String dispositionName) {
        public boolean can() {
            return cannot.isEmpty();
        }

        /** Whether it would take the task and set to work now, waiting on nothing. */
        public boolean ready() {
            return cannot.isEmpty() && waitsFor.isEmpty();
        }
    }

    /**
     * What a build brings to every task, worked out once: its stats with its traits' changes to speed, health, damage,
     * drag strength and slots; whether it can strike, has an innate shot, a detonating organ; what its head fishes or digs
     * with no tool; whether its head is a surgeon's (docs/NEXT.md 1.5); its knacks' parts and its disposition.
     */
    public record Body(MinionStats stats, float speed, List<Source> speedFrom, float health, List<Source> healthFrom, float hardest, List<Source> hardestFrom,
                       int striking, float damageOut, boolean canStrike, boolean handWeapon, Optional<Source> innateShot, int storage, int chestStorage,
                       List<Source> storageFrom, float drag, List<Source> dragFrom, boolean detonator, List<ResourceLocation> noTool, boolean surgeonHead,
                       ResourceLocation torso, @Nullable ResourceLocation head, Map<ResourceLocation, List<MinionStats.KnackPart>> knackParts,
                       MinionDisposition disposition) {
        /** Strikes land this much more often for each arm that strikes past two, up to +60%. */
        public float strikeRate() {
            return 1.0F + MORE * Math.max(0, Math.min(MOST_MORE, striking - 2));
        }
    }

    /** What a build brings to every task. */
    public static Body body(PartsData.Store store, MinionBuild build, MinionStats stats) {
        Map<ResourceLocation, Integer> levels = MinionData.levels(store, build);
        var attributes = MinionData.effects(store, levels, TraitEffects.AttributeEffect.class);
        // speed, as its attribute takes its traits, their rises held to the cap ActiveTraits holds them to
        float speed = (float) MinionData.attributed(stats.speed(), Attributes.MOVEMENT_SPEED, attributes);
        speed = Math.min(speed, stats.speed() * (1.0F + ActiveTraits.SPEED_CAP));
        float health = (float) MinionData.attributed(stats.health(), Attributes.MAX_HEALTH, attributes);
        health = Math.max(MinionStats.MIN_HEALTH, Math.min(MinionStats.MAX_HEALTH, health));
        float drag = (float) Math.max(0.0, Math.min(0.75, MinionData.attributed(0.0, BBAttributes.DRAG_STRENGTH, attributes)));
        PieceRef torso = build.torso();
        PieceRef head = MinionStats.head(store, build);
        List<Source> speedFrom = new ArrayList<>();
        for (MinionBuild.Fitted fitted : build.parts()) {
            PieceRef piece = fitted.piece();
            var rig = store.rig(piece.entity(), piece.baby());
            if (rig.isPresent() && PartSlots.of(store, piece.entity(), rig.get(), piece.bone()).slot()
                    == PartSlot.LEG && speedFrom.stream().noneMatch(s -> s.id().equals(piece.entity()))) {
                speedFrom.add(Source.piece(piece.entity(), "leg"));
            }
        }
        if (speedFrom.isEmpty() || MinionStats.SELF_FLYING.contains(stats.mode())) {
            speedFrom.clear();
            speedFrom.add(Source.rule("own_way", stats.mode()));
        }
        speedFrom.addAll(traitSources(attributes, Attributes.MOVEMENT_SPEED));
        List<Source> healthFrom = new ArrayList<>(List.of(Source.piece(torso.entity(), "torso")));
        healthFrom.addAll(traitSources(attributes, Attributes.MAX_HEALTH));
        List<Source> dragFrom = new ArrayList<>(List.of(Source.piece(torso.entity(), "torso")));
        dragFrom.addAll(traitSources(attributes, BBAttributes.DRAG_STRENGTH));
        // its hardest blow: an arm's, or with no arm that strikes, its bite
        float hardest = 0.0F;
        ResourceLocation hardestArm = null;
        int striking = 0;
        List<ResourceLocation> armMobs = new ArrayList<>();
        for (MinionBuild.Fitted fitted : build.parts()) {
            PieceRef piece = fitted.piece();
            var rig = store.rig(piece.entity(), piece.baby());
            if (rig.isPresent() && PartSlots.of(store, piece.entity(), rig.get(), piece.bone()).slot()
                    == PartSlot.ARM) {
                armMobs.add(piece.entity());
            }
        }
        List<MinionStats.Strike> strikes = stats.strikes();
        for (int i = 0; i < strikes.size(); i++) {
            MinionStats.Strike strike = strikes.get(i);
            if (!"pacifist".equals(strike.style()) && strike.damage() > 0.0F) {
                striking++;
                if (strike.damage() > hardest) {
                    hardest = strike.damage();
                    hardestArm = i < armMobs.size() ? armMobs.get(i) : null;
                }
            }
        }
        List<Source> hardestFrom = new ArrayList<>();
        if (striking > 0) {
            if (hardestArm != null) {
                hardestFrom.add(Source.piece(hardestArm, "arm"));
            }
        } else if (head != null) {
            hardest = stats.biteDamage();
            hardestFrom.add(Source.rule("bite", ""));
            hardestFrom.add(Source.piece(head.entity(), "head"));
        }
        // blows its traits make harder or softer, always (an attack or passive entry, with no condition, any damage)
        float out = 1.0F;
        for (var found : MinionData.effects(store, levels, TraitEffects.DamageEffect.class)) {
            if ("out".equals(found.effect().direction()) && found.effect().damageTags().isEmpty() && found.facet().requirements().isEmpty()
                    && (found.facet().trigger() == Trigger.ATTACK || found.facet().trigger() == Trigger.PASSIVE)) {
                out *= 1.0F + (found.effect().multiplier().calculate(found.level()) - 1.0F) * TraitEffects.strength();
                hardestFrom.add(Source.trait(found.trait(), found.level()));
            }
        }
        boolean canStrike = strikes.stream().anyMatch(s -> !"pacifist".equals(s.style())) || head != null;
        // a held bow, crossbow or trident needs a hand that fights to draw or throw it
        boolean handWeapon = strikes.stream().anyMatch(s -> "hand".equals(s.grip()) && !"pacifist".equals(s.style()));
        Optional<Source> innate = Optional.empty();
        for (var found : MinionData.effects(store, levels, ProjectileEffect.class)) {
            if (found.facet().trigger() == Trigger.PASSIVE) {
                innate = Optional.of(Source.trait(found.trait(), found.level()));
            }
        }
        for (var found : MinionData.effects(store, levels, HitscanEffect.class)) {
            if (found.facet().trigger() == Trigger.PASSIVE && innate.isEmpty()) {
                innate = Optional.of(Source.trait(found.trait(), found.level()));
            }
        }
        int storage = 0;
        int chestStorage = 0;
        List<Source> storageFrom = new ArrayList<>(List.of(Source.piece(torso.entity(), "torso")));
        for (var found : MinionData.effects(store, levels, StorageEffect.class)) {
            if (found.facet().trigger() == Trigger.PASSIVE) {
                int slots = Math.max(0, (int) found.effect().slots().calculate(found.level()));
                if (found.effect().chest()) {
                    chestStorage += slots;
                } else {
                    storage += slots;
                }
                storageFrom.add(Source.trait(found.trait(), found.level()));
            }
        }
        List<ResourceLocation> noTool = List.of();
        boolean surgeonHead = false;
        if (head != null) {
            var headMob = store.resolve(head.entity(), head.baby());
            noTool = MinionData.ids(headMob, head.traits(), "head", "no_tool");
            surgeonHead = MinionData.field(headMob, head.traits(), "head", "surgeon").filter(JsonElement::isJsonPrimitive)
                    .map(JsonElement::getAsBoolean).orElse(false);
        }
        return new Body(stats, speed, List.copyOf(speedFrom), health, List.copyOf(healthFrom), hardest, List.copyOf(hardestFrom), striking, out, canStrike,
                handWeapon, innate, storage, chestStorage, List.copyOf(storageFrom), drag, List.copyOf(dragFrom), MinionSapper.hasDetonator(store, build),
                noTool, surgeonHead, torso.entity(), head == null ? null : head.entity(), MinionStats.knackParts(store, build),
                store.disposition(stats.disposition()));
    }

    private static List<Source> traitSources(List<MinionData.Found<TraitEffects.AttributeEffect>> effects, Holder<Attribute> attribute) {
        List<Source> out = new ArrayList<>();
        for (var found : effects) {
            if (found.always() && found.effect().attribute().is(attribute)) {
                out.add(Source.trait(found.trait(), found.level()));
            }
        }
        return out;
    }

    /** Every task's row, in the task list's order. */
    public static List<Row> rows(PartsData.Store store, MinionBuild build, MinionStats stats, Context context) {
        Body body = body(store, build, stats);
        List<Row> out = new ArrayList<>();
        for (MinionTask task : MinionTask.values()) {
            out.add(row(store, body, task, context));
        }
        return out;
    }

    /** One task's row. */
    public static Row of(PartsData.Store store, MinionBuild build, MinionStats stats, Context context, MinionTask task) {
        return row(store, body(store, build, stats), task, context);
    }

    /** One task's row for a body worked out already. */
    public static Row row(PartsData.Store store, Body body, MinionTask task, Context context) {
        MinionTask.Data data = store.task(task);
        MinionStats stats = body.stats();
        float disposition = body.disposition().multiplier(task, context.anchor(), context.night());
        List<MinionStats.KnackPart> knackFrom = body.knackParts().getOrDefault(task.id, List.of());
        float knack = stats.knack(task.id);
        if (!task.rated()) {
            return new Row(task, 1.0F, 1.0F, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), 1.0F, List.of(),
                    1.0F, stats.disposition());
        }
        Optional<String> cannot = cannot(task, data, body);
        boolean has = hasTool(task, data, body, context);
        Optional<String> waits = Optional.empty();
        if (cannot.isEmpty()) {
            if (data.tool().map(MinionTask.Tool::required).orElse(false) && !has) {
                waits = Optional.of("bloodandbones.minion.wants." + task.id.getPath());
            } else if (task == MinionTask.HUNTER && !context.griefing()) {
                waits = Optional.of("bloodandbones.minion.wants.griefing");
            }
        }
        // a tool it waits for is taken as had: that is how it will work once it has it
        boolean tooled = has || waits.isPresent() && data.tool().isPresent();
        Optional<Factor> main = data.main().map(stat -> factor(stat, task, data, body, context, tooled));
        Optional<Factor> second = data.second().map(stat -> factor(stat, task, data, body, context, tooled));
        float raw = raw(knack, disposition, main, second);
        Optional<Float> withTool = Optional.empty();
        if (cannot.isEmpty() && !tooled && data.tool().isPresent()) {
            float with = raw(knack, disposition, data.main().map(stat -> factor(stat, task, data, body, context, true)),
                    data.second().map(stat -> factor(stat, task, data, body, context, true)));
            if (held(with) != held(raw)) {
                withTool = Optional.of(held(with));
            }
        }
        return new Row(task, held(raw), raw, cannot, waits, withTool, main, second, knack, knackFrom, disposition, stats.disposition());
    }

    private static float raw(float knack, float disposition, Optional<Factor> main, Optional<Factor> second) {
        return knack * disposition * main.map(Factor::value).orElse(1.0F) * (float) Math.sqrt(second.map(Factor::value).orElse(1.0F));
    }

    /** A fitness held between {@link #LEAST} and {@link #MOST}. */
    public static float held(float raw) {
        return Math.max(LEAST, Math.min(MOST, raw));
    }

    /** Why this body cannot do a task at all, if it cannot (docs/NEXT.md 1.2, "Cannot when"). */
    static Optional<String> cannot(MinionTask task, MinionTask.Data data, Body body) {
        boolean shut = switch (task.need) {
            case NONE -> false;
            case STRIKE -> !body.canStrike();
            case DETONATOR -> !body.detonator();
            case GRIP -> best(data.grips(), task, body).value() <= 0.0F;
        };
        return shut ? Optional.ofNullable(task.cannotKey()) : Optional.empty();
    }

    /** Whether it has the task's tool: in hand, or among what it carries for one that is carried (a medic's potions). */
    static boolean hasTool(MinionTask task, MinionTask.Data data, Body body, Context context) {
        if (data.tool().isEmpty()) {
            return false;
        }
        MinionTask.Tool tool = data.tool().get();
        List<ItemStack> where = tool.carried() ? context.carried() : List.of(context.held());
        for (ItemStack stack : where) {
            if (!stack.isEmpty() && (tool.items().isPresent() ? matches(tool.items().get(), stack) : takes(task, stack))) {
                return true;
            }
        }
        return false;
    }

    /** An item id, or a "#tag", this stack is. */
    private static boolean matches(String items, ItemStack stack) {
        if (items.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(items.substring(1));
            return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        }
        ResourceLocation id = ResourceLocation.tryParse(items);
        return id != null && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
    }

    /** The tools the tasks' own code takes (today's tests): a blade, a rod, a bow, crossbow or trident, healing, any bait or sample. */
    private static boolean takes(MinionTask task, ItemStack stack) {
        return switch (task) {
            case BUTCHER -> stack.getItem() instanceof CleaverItem || stack.getItem() instanceof FlensingKnifeItem;
            case FISHER -> stack.getItem() instanceof FishingRodItem;
            case SENTRY -> weapon(stack);
            case MEDIC -> MinionTasks.heals(stack);
            default -> true;
        };
    }

    private static boolean weapon(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem;
    }

    /** The best a body holds things with for this grip table, and how many of that it has. */
    private record Best(float value, int count, @Nullable MinionStats.Holder holder) {
    }

    /**
     * The best of what a body holds things with that this grip table allows (its head's mouth doing as well as a hand at
     * what its data says it does with no tool: a fish fishes with its mouth), with its own body as the last resort ("none"),
     * and how many of that same grip it has.
     */
    private static Best best(Map<String, Float> grips, MinionTask task, Body body) {
        float best = grips.getOrDefault("none", 0.0F);
        MinionStats.Holder from = null;
        for (MinionStats.Holder holder : body.stats().holders()) {
            float value = value(grips, task, body, holder);
            if (value > best) {
                best = value;
                from = holder;
            }
        }
        // more of that same thing (a spider's eight hands, not a mouth that works as well as two)
        int count = 0;
        if (from != null) {
            for (MinionStats.Holder holder : body.stats().holders()) {
                if (holder.grip().equals(from.grip()) && value(grips, task, body, holder) == best) {
                    count += holder.count();
                }
            }
        }
        return new Best(best, count, from);
    }

    private static float value(Map<String, Float> grips, MinionTask task, Body body, MinionStats.Holder holder) {
        if ("mouth".equals(holder.grip()) && grips.containsKey("mouth") && body.noTool().contains(task.id)) {
            return 1.0F;
        }
        return grips.getOrDefault(holder.grip(), 0.0F);
    }

    /** One stat a task reads, over its reference and held. */
    static Factor factor(MinionTask.Stat stat, MinionTask task, MinionTask.Data data, Body body, Context context, boolean tooled) {
        MinionStats stats = body.stats();
        return switch (stat) {
            case PACE -> held(stat, body.speed(), body.speed() / PACE, body.speedFrom());
            case SIGHT -> {
                List<Source> from = new ArrayList<>();
                if (body.head() == null) {
                    from.add(Source.rule("mindless", ""));
                } else {
                    from.add(Source.piece(body.head(), "head"));
                    if (stats.sight() == MinionStats.BLIND_SIGHT) {
                        from.add(Source.rule("blind", ""));
                    }
                }
                yield held(stat, stats.sight(), stats.sight() / SIGHT, from);
            }
            case HANDS -> {
                Map<String, Float> grips = data.grips();
                List<Source> from = new ArrayList<>();
                if (tooled && data.tool().flatMap(MinionTask.Tool::grips).isPresent()) {
                    grips = data.tool().get().grips().get();
                    if (!context.held().isEmpty()) {
                        from.add(Source.item(context.held()));
                    }
                }
                Best best = best(grips, task, body);
                if (best.holder() != null) {
                    from.add(0, Source.piece(best.holder().mob(), best.holder().part() + ":" + best.holder().grip()));
                    if ("mouth".equals(best.holder().grip()) && body.noTool().contains(task.id)) {
                        from.add(Source.rule("no_tool", ""));
                    }
                } else {
                    from.add(0, Source.rule("own_body", ""));
                }
                float more = MORE * Math.max(0, Math.min(MOST_MORE, best.count() - 2));
                if (more > 0.0F) {
                    from.add(Source.rule("more", Integer.toString(best.count())));
                }
                float hands = best.value() * (1.0F + more);
                yield held(stat, hands, hands, from);
            }
            case BLOW -> {
                List<Source> from = new ArrayList<>(body.hardestFrom());
                float rate = body.strikeRate();
                if (rate > 1.0F) {
                    from.add(Source.rule("strike_rate", Integer.toString(body.striking())));
                }
                float blow = body.hardest() * rate * body.damageOut();
                if (stats.berserk()) {
                    blow *= BERSERK;
                    from.add(Source.rule("berserk", ""));
                }
                yield held(stat, blow, blow / BLOW, from);
            }
            case RANGED -> {
                boolean inHand = body.handWeapon() && (weapon(context.held()) || tooled && task == MinionTask.SENTRY);
                if (inHand) {
                    yield held(stat, 1.0F, 1.0F, context.held().isEmpty() ? List.of() : List.of(Source.item(context.held())));
                }
                if (body.innateShot().isPresent()) {
                    yield held(stat, 1.0F, 1.0F, List.of(body.innateShot().get()));
                }
                yield held(stat, 0.0F, NO_RANGED, List.of(Source.rule("no_ranged", "")));
            }
            case TOUGHNESS -> held(stat, body.health(), body.health() / TOUGHNESS, body.healthFrom());
            case CARRY -> {
                int slots = Math.min(54, stats.slots() + body.storage() + (context.chested() ? body.chestStorage() : 0));
                List<Source> from = new ArrayList<>(body.storageFrom());
                if (context.chested() && body.chestStorage() > 0) {
                    from.add(Source.rule("chest", Integer.toString(body.chestStorage())));
                }
                yield held(stat, slots, slots / CARRY, from);
            }
            case PULL -> {
                // as knockback resistance reads the torso's weight, its square root; drag strength takes its share off the slowdown
                float pull = (float) Math.sqrt(stats.torsoWeight() / PULL) / (1.0F - body.drag());
                yield held(stat, stats.torsoWeight(), pull, body.dragFrom());
            }
        };
    }

    private static Factor held(MinionTask.Stat stat, float value, float factor, List<Source> from) {
        return new Factor(stat, value, Math.max(FACTOR_LEAST, Math.min(FACTOR_MOST, factor)), List.copyOf(from));
    }

    // ---- the surgeon (docs/NEXT.md 1.5)

    /**
     * How many buckets of blood the stump a surgeon of this fitness leaves costs to fit anything but a crude prosthetic into
     * later: one at {@link #CLEAN_CUT} and over (today's price), two from {@link #FAIR_CUT}, three below.
     */
    public static int stumpBuckets(float fitness) {
        return fitness >= CLEAN_CUT ? 1 : fitness >= FAIR_CUT ? 2 : 3;
    }

    /**
     * Whether a body set to Surgeon may do the ritual's cutting (docs/NEXT.md 1.5; the owner's call). By default any minion
     * that can be a surgeon at all (it has a hand) may, and its fitness sets the stump's price. With the surgeon task's
     * {@code "needs_surgeon_head": true}, only a head whose data says {@code "surgeon": true} may (the villager and illager
     * families and the witch, as the brief's words have it); anyone may still tend at the table.
     */
    public static boolean mayCut(MinionTask.Data surgeon, Body body) {
        return cannot(MinionTask.SURGEON, surgeon, body).isEmpty() && (!surgeon.needsSurgeonHead() || body.surgeonHead());
    }

    // ---- the levers: what a task's goals scale by its fitness (docs/NEXT.md 1.2), each today's constant at 100%

    /** The fitness a lever reads, held between {@link #LEVER_LEAST} and {@link #LEVER_MOST}. */
    public static float lever(float fitness) {
        return Math.max(LEVER_LEAST, Math.min(LEVER_MOST, fitness));
    }

    /** Ticks something takes that a fitter minion does sooner (a stroke, a catch, a throw), never under {@code least}. */
    public static int quicker(float ticks, float fitness, float least) {
        return Math.round(Math.max(least, ticks / lever(fitness)));
    }

    /** Ticks something lasts that a fitter minion keeps at longer (a herder waiting on a stray). */
    public static int longer(float ticks, float fitness) {
        return Math.round(ticks * lever(fitness));
    }

    /** The spread of its aim, a fitter one's truer. */
    public static float spread(float base, float fitness) {
        return base / lever(fitness);
    }

    /** What share of a cut's yield a butcher gets: all at 100% and over, less below (waste). */
    public static float yieldShare(float fitness) {
        return Math.min(1.0F, lever(fitness));
    }

    /**
     * A hauler's slowdown while towing: a player's at 100% and over (never less), a player's ÷ fitness below, at most
     * {@code most}.
     */
    public static float towing(float playerSlowdown, float fitness, float most) {
        return fitness >= 1.0F ? playerSlowdown : Math.min(Math.max(most, playerSlowdown), playerSlowdown / lever(fitness));
    }

    /** mB of blood a minute it uses at work: today's 25 ÷ its fitness held between 50% and 200%, so 12.5 to 50. Brass uses a quarter. */
    public static float workingDrain(float fitness) {
        return MinionEntity.WORKING / Math.max(BLOOD_LEAST, Math.min(BLOOD_MOST, fitness));
    }

    /** A fisher's ticks between catches, the least and the most, never under a sixth of the least (Lure's floor). */
    public static int[] catchTicks(MinionTask.Data fisher, float fitness) {
        float min = fisher.number("catch_min", 600.0F);
        float max = fisher.number("catch_max", 1200.0F);
        float floor = min * fisher.number("catch_least", 1.0F / 6.0F);
        return new int[]{quicker(min, fitness, floor), quicker(max, fitness, floor)};
    }

    /** A digger's ticks between finds, the least and the most. */
    public static int[] digTicks(MinionTask.Data digger, float fitness) {
        return new int[]{quicker(digger.number("dig_min", 1200.0F), fitness, 1.0F), quicker(digger.number("dig_max", 2400.0F), fitness, 1.0F)};
    }

    /** A butcher's ticks between strokes. */
    public static int strokeTicks(MinionTask.Data butcher, float fitness) {
        return quicker(butcher.number("stroke", 15.0F), fitness, butcher.number("stroke_least", 6.0F));
    }

    /** A barterer's ticks looking the gold over. */
    public static int admireTicks(MinionTask.Data barterer, float fitness) {
        return quicker(barterer.number("admire", 120.0F), fitness, 1.0F);
    }

    /** A surgeon's ticks between the hearts it tends back into a patient. */
    public static int tendTicks(MinionTask.Data surgeon, float fitness) {
        return quicker(surgeon.number("tend_every", 100.0F), fitness, surgeon.number("tend_least", 40.0F));
    }

    /** A medic's ticks between throws. */
    public static int throwTicks(MinionTask.Data medic, float fitness) {
        return quicker(medic.number("throw_every", 60.0F), fitness, medic.number("throw_least", 20.0F));
    }

    /** How often a task that looks round looks (a farmer, a courier, a tender), in ticks. */
    public static int lookTicks(MinionTask.Data data, float fitness) {
        return quicker(data.number("look_every", 10.0F), fitness, 1.0F);
    }
}
