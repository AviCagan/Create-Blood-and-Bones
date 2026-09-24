package com.avicagan.bloodandbones.parts;

import com.avicagan.bloodandbones.minion.MinionData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code /bloodandbones traits explain <mob> [baby]}: the layers a mob's data is built from and what each of its parts,
 * its hide, its organs and a full set of it give, on a minion and in armour (docs/PARTS-AND-TRAITS.md section 4.11).
 * {@code /bloodandbones traits dump}: every rigged mob's traits as rows of a CSV file in the server's folder, for
 * balancing and for pack authors (operators only).
 */
public final class TraitsCommand {
    /** Where the dump goes, in the server's own folder (the run folder in development). */
    public static final String DUMP_FILE = "bloodandbones-traits.csv";

    private TraitsCommand() {
    }

    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("bloodandbones").then(Commands.literal("traits")
                .then(Commands.literal("explain").then(Commands.argument("mob", ResourceArgument.resource(context, Registries.ENTITY_TYPE))
                        .executes(ctx -> explain(ctx, false))
                        .then(Commands.literal("baby").executes(ctx -> explain(ctx, true)))))
                .then(Commands.literal("dump").requires(source -> source.hasPermission(2)).executes(TraitsCommand::dump))));
    }

    private static int explain(CommandContext<CommandSourceStack> ctx, boolean baby) throws CommandSyntaxException {
        ResourceLocation mob = ResourceArgument.getEntityType(ctx, "mob").key().location();
        List<Component> lines = explain(PartsData.SERVER, mob, baby);
        for (Component line : lines) {
            ctx.getSource().sendSuccess(() -> line, false);
        }
        return lines.size();
    }

    /** The lines of "explain", for a mob: the layers, each part, the hide, the organs, the full set. */
    public static List<Component> explain(PartsData.Store store, ResourceLocation mob, boolean baby) {
        ResolvedMob resolved = store.resolve(mob, baby);
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("bloodandbones.command.explain.title", com.avicagan.bloodandbones.parts.ScrapsItem.mobName(mob),
                Component.literal(mob.toString())).withStyle(ChatFormatting.GOLD));
        MutableComponent layers = Component.empty();
        for (int i = 0; i < resolved.layers().size(); i++) {
            ResourceLocation id = resolved.layers().get(i);
            MobGroup group = store.groups().get(id);
            String kind = group != null ? group.kind().name().toLowerCase(java.util.Locale.ROOT) : "mob";
            layers.append(i == 0 ? Component.empty() : Component.literal(" > ")).append(Component.literal(id.getPath()).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" (" + kind + ")").withStyle(ChatFormatting.DARK_GRAY));
        }
        out.add(Component.translatable("bloodandbones.command.explain.layers", layers).withStyle(ChatFormatting.GRAY));
        out.add(Component.translatable("bloodandbones.command.explain.material",
                Component.translatable("scrap_material." + resolved.material().getNamespace() + "." + resolved.material().getPath())).withStyle(ChatFormatting.GRAY));
        for (String key : partKeys(resolved)) {
            ResolvedMob.Part part = resolved.parts().get(key);
            MutableComponent armour = list(store, part == null ? List.of() : part.armour());
            if (part != null) {
                for (Map.Entry<String, List<TraitList.Resolved>> piece : part.pieces().entrySet()) {
                    armour.append(Component.literal("; ")).append(Component.literal(piece.getKey() + " ")).append(list(store, piece.getValue()));
                }
            }
            out.add(Component.translatable("bloodandbones.command.explain.part", partName(key), list(store, MinionData.traits(resolved, key)), armour)
                    .withStyle(ChatFormatting.GRAY));
        }
        out.add(Component.translatable("bloodandbones.command.explain.hide", list(store, resolved.hide())).withStyle(ChatFormatting.GRAY));
        for (Map.Entry<String, List<ResourceLocation>> held : resolved.organLists().entrySet()) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < held.getValue().size(); i++) {
                names.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Organs.name(store, held.getValue().get(i)));
            }
            out.add(Component.translatable("bloodandbones.command.explain.organs", partName(held.getKey()), names).withStyle(ChatFormatting.GRAY));
        }
        for (ResourceLocation organ : organIds(resolved)) {
            ResolvedMob.Organ traits = resolved.organs().get(organ);
            out.add(Component.translatable("bloodandbones.command.explain.organ", Organs.name(store, organ),
                    list(store, traits == null ? List.of() : traits.minion()), String.join(", ", Organs.kind(store, organ).armourPieces()),
                    list(store, traits == null ? List.of() : traits.armour())).withStyle(ChatFormatting.GRAY));
        }
        resolved.fullSet().ifPresent(set -> out.add(Component.translatable("bloodandbones.command.explain.set", Component.translatable(set.name()),
                list(store, set.bonus()), list(store, set.drawback())).withStyle(ChatFormatting.DARK_PURPLE)));
        return out;
    }

    /** Every part key the mob's data names, minion or armour side, the plain ones before their sub-keys. */
    private static List<String> partKeys(ResolvedMob resolved) {
        Set<String> keys = new java.util.TreeSet<>(resolved.parts().keySet());
        keys.addAll(resolved.minion().keySet());
        return new ArrayList<>(keys);
    }

    /** The organs it holds in its lists, then any others its data gives traits to. */
    private static Set<ResourceLocation> organIds(ResolvedMob resolved) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        resolved.organLists().values().forEach(ids::addAll);
        ids.addAll(resolved.organs().keySet());
        return ids;
    }

    /** "Leg (hind)", "Torso". */
    private static MutableComponent partName(String key) {
        String base = key.contains(".") ? key.substring(0, key.indexOf('.')) : key;
        MutableComponent name = Component.translatable("bloodandbones.part_key." + base);
        return key.contains(".") ? name.append(" (" + key.substring(key.indexOf('.') + 1) + ")") : name;
    }

    /** Traits by name and level, or "nothing". */
    private static MutableComponent list(PartsData.Store store, List<TraitList.Resolved> traits) {
        if (traits.isEmpty()) {
            return Component.translatable("bloodandbones.command.explain.nothing").withStyle(ChatFormatting.DARK_GRAY);
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < traits.size(); i++) {
            out.append(i == 0 ? Component.empty() : Component.literal(", ")).append(Traits.describe(store, traits.get(i)).withStyle(ChatFormatting.DARK_AQUA));
        }
        return out;
    }

    private static int dump(CommandContext<CommandSourceStack> ctx) {
        Path file = ctx.getSource().getServer().getServerDirectory().resolve(DUMP_FILE);
        List<String> rows = rows(PartsData.SERVER);
        try {
            Files.write(file, rows, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.translatable("bloodandbones.command.dump.failed", e.getMessage()));
            return 0;
        }
        int mobs = Organs.rigged(PartsData.SERVER).size();
        ctx.getSource().sendSuccess(() -> Component.translatable("bloodandbones.command.dump.done", rows.size() - 1, mobs, file.toAbsolutePath().toString()), true);
        return rows.size() - 1;
    }

    /**
     * The dump as CSV rows, a header first: one row per trait a facet gives, with the mob's archetype, family and
     * overlays, the facet (a part key, the hide, an organ, a full set, an organ list) and who gets it (minion, armour, a
     * piece, a set's bonus or drawback). An organ list's rows name the organ in the trait column, its place in the level.
     */
    public static List<String> rows(PartsData.Store store) {
        List<String> out = new ArrayList<>();
        out.add("mob,archetype,family,overlays,material,facet,host,trait,level");
        for (ResourceLocation mob : Organs.rigged(store)) {
            ResolvedMob resolved = store.resolve(mob, false);
            String archetype = "";
            String family = "";
            List<String> overlays = new ArrayList<>();
            for (ResourceLocation id : resolved.layers()) {
                MobGroup group = store.groups().get(id);
                if (group == null) {
                    continue;
                }
                switch (group.kind()) {
                    case ARCHETYPE -> archetype = id.toString();
                    case FAMILY -> family = id.toString();
                    case OVERLAY -> overlays.add(id.getPath());
                    default -> {
                    }
                }
            }
            String head = csv(mob.toString()) + "," + csv(archetype) + "," + csv(family) + "," + csv(String.join(" ", overlays)) + ","
                    + csv(resolved.material().toString()) + ",";
            for (String key : partKeys(resolved)) {
                traits(out, head, "part:" + key, "minion", MinionData.traits(resolved, key));
                ResolvedMob.Part part = resolved.parts().get(key);
                if (part != null) {
                    traits(out, head, "part:" + key, "armour", part.armour());
                    part.pieces().forEach((piece, list) -> traits(out, head, "part:" + key, "armour:" + piece, list));
                }
            }
            traits(out, head, "hide", "armour", resolved.hide());
            resolved.organLists().forEach((key, list) -> {
                for (int i = 0; i < list.size(); i++) {
                    out.add(head + csv("organs:" + key) + ",," + csv(list.get(i).toString()) + "," + (i + 1));
                }
            });
            for (ResourceLocation organ : organIds(resolved)) {
                ResolvedMob.Organ traits = resolved.organs().get(organ);
                if (traits != null) {
                    traits(out, head, "organ:" + organ, "minion", traits.minion());
                    traits(out, head, "organ:" + organ, "armour:" + String.join(" ", Organs.kind(store, organ).armourPieces()), traits.armour());
                }
            }
            resolved.fullSet().ifPresent(set -> {
                traits(out, head, "set:" + set.name(), "bonus", set.bonus());
                traits(out, head, "set:" + set.name(), "drawback", set.drawback());
            });
        }
        return out;
    }

    private static void traits(List<String> out, String head, String facet, String host, List<TraitList.Resolved> traits) {
        for (TraitList.Resolved trait : traits) {
            out.add(head + csv(facet) + "," + csv(host) + "," + csv(trait.id().toString()) + "," + trait.level());
        }
    }

    private static String csv(String value) {
        return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}
