package com.avicagan.bloodandbones.gametest;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BloodlessWords;
import com.avicagan.bloodandbones.registry.BBSounds;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforgespi.language.ModFileScanData;
import net.neoforged.neoforgespi.locating.IModFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Bloodless mode is presentation only (brief rule 4: "Identical mechanics, presentation only, no separate logic
 * paths"), and it covers the whole mod: docs/BRIEF-AUDIT.md package 9. These read the mod's own class files and
 * language file, so a new game-logic read of the setting, a new gory word left in bloodless text or a new wet sound
 * with no metal twin fails here, not in play.
 */
@GameTestHolder(BloodAndBones.MOD_ID)
@PrefixGameTestTemplate(false)
public class BloodlessTests {
    private static final String CONFIG = "com/avicagan/bloodandbones/config/BBClientConfig";
    private static final String RULES = "com/avicagan/bloodandbones/registry/BBGameRules";

    /**
     * Where reading the setting is allowed: drawing, words, sounds and the setting's own plumbing. Whole packages end in
     * a slash; a class alone, or a class and a method after '#'. Anything else that asks is game logic, and fails.
     */
    private static final List<String> PRESENTATION = List.of(
            "com/avicagan/bloodandbones/client/",
            CONFIG,
            // the game rule itself, which tells clients when it changes
            RULES,
            // the tests (this one, and the one that flips the rule)
            "com/avicagan/bloodandbones/gametest/",
            // the colour blood is drawn in, and the fog under it
            "com/avicagan/bloodandbones/registry/BBFluids$TintedFluidType#colour",
            "com/avicagan/bloodandbones/registry/BBFluids$TintedFluidType#getCustomFogColor",
            // Create's item descriptions, built again when the words change
            "com/avicagan/bloodandbones/item/BBDescriptionModifier#checkLocale",
            // the texture carcass armour is drawn with on a body
            "com/avicagan/bloodandbones/parts/CarcassArmourItem#getArmorTexture",
            // a renderer that lives beside its entity
            "com/avicagan/bloodandbones/decoration/HangingGutChainRenderer");

    /** Wet vanilla sounds the mod must not play itself: it plays its own, which have metal twins (BBSounds). */
    private static final Set<String> WET_VANILLA = Set.of("SLIME_SQUISH", "SLIME_SQUISH_SMALL", "SLIME_BLOCK_BREAK", "SLIME_BLOCK_FALL", "SLIME_BLOCK_HIT",
            "SLIME_BLOCK_PLACE", "SLIME_BLOCK_STEP", "HONEY_BLOCK_BREAK", "HONEY_BLOCK_FALL", "HONEY_BLOCK_HIT", "HONEY_BLOCK_PLACE", "HONEY_BLOCK_SLIDE",
            "HONEY_BLOCK_STEP");

    /**
     * Nothing outside presentation code reads the bloodless setting (the client toggle, or the game rule): every
     * class of the mod is read, and every method that asks is listed against the places allowed to.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void onlyPresentationReadsBloodless(GameTestHelper helper) {
        List<String> readers = new ArrayList<>();
        List<String> wetSounds = new ArrayList<>();
        int classes = scan((owner, method, name, fieldOwner, isField) -> {
            boolean setting = !isField && fieldOwner.equals(CONFIG) && name.equals("bloodless")
                    || isField && fieldOwner.equals(CONFIG) && name.equals("BLOODLESS_MODE")
                    || isField && fieldOwner.equals(RULES) && name.equals("BLOODLESS");
            if (setting) {
                readers.add(owner + "#" + method);
            }
            if (isField && fieldOwner.equals("net/minecraft/sounds/SoundEvents") && WET_VANILLA.contains(name)) {
                wetSounds.add(owner + "#" + method + " plays " + name);
            }
        });
        if (classes < 200) {
            helper.fail("Only " + classes + " of the mod's classes were read: the scan is not seeing the mod");
            return;
        }
        List<String> logic = readers.stream().filter(reader -> PRESENTATION.stream().noneMatch(allowed -> allows(allowed, reader))).distinct().toList();
        if (!logic.isEmpty()) {
            helper.fail("Game logic reads the bloodless setting (presentation only, brief rule 4): " + logic);
            return;
        }
        // the scan sees real readers: the swap of bloody textures, the stains, the words and the sounds
        for (String known : List.of("com/avicagan/bloodandbones/client/BloodlessSwap", "com/avicagan/bloodandbones/client/BloodlessLanguage",
                "com/avicagan/bloodandbones/client/BloodlessSounds", "com/avicagan/bloodandbones/client/CarcassModels")) {
            if (readers.stream().noneMatch(reader -> reader.startsWith(known + "#"))) {
                helper.fail("The scan should have found " + known + " reading the setting; it found " + readers.size() + " readers");
                return;
            }
        }
        if (!wetSounds.isEmpty()) {
            helper.fail("The mod plays wet vanilla sounds with no bloodless twin; play BBSounds' own instead: " + wetSounds);
            return;
        }
        helper.succeed();
    }

    /**
     * Every line of the mod's text that bloodless mode rewords comes out with no blood, gore, carcass, flesh, severing
     * or rot of the body in it: its own {@code bloodless.} text if it has one, else the rewording. And the names the
     * audit asked for read as they should.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void bloodlessTextIsClean(GameTestHelper helper) {
        JsonObject lang = json(helper, "assets/bloodandbones/lang/en_us.json");
        if (lang == null) {
            return;
        }
        List<String> gory = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : lang.entrySet()) {
            String key = entry.getKey();
            // a Create description's bare key is only asked whether it exists, never shown
            if (!BloodlessWords.reworded(key) || key.endsWith(".tooltip")) {
                continue;
            }
            String shown = shown(lang, key);
            String word = BloodlessWords.gory(shown);
            if (word != null) {
                gory.add(key + " (\"" + word + "\"): " + shown);
            }
        }
        if (!gory.isEmpty()) {
            helper.fail(gory.size() + " lines still gory in bloodless mode, first " + gory.subList(0, Math.min(5, gory.size())));
            return;
        }
        Map<String, String> names = Map.ofEntries(
                Map.entry("block.bloodandbones.carcass_part", "Wreck"),
                Map.entry("item.bloodandbones.carcass_piece", "Wreck Piece"),
                Map.entry("item.bloodandbones.flesh_arm", "Plated Arm"),
                Map.entry("item.bloodandbones.sinew_leg", "Cabled Leg"),
                Map.entry("item.bloodandbones.furnace_stomach", "Furnace Hopper"),
                Map.entry("item.bloodandbones.severed_arm", "Detached Arm"),
                Map.entry("item.bloodandbones.severed_leg", "Detached Leg"),
                Map.entry("item.bloodandbones.heart", "Pump"),
                Map.entry("item.bloodandbones.carcass_boots", "Plated Boots"),
                Map.entry("item.bloodandbones.congealed_blood", "Congealed Essence"),
                Map.entry("create.item_attributes.bloodandbones.fresh_piece", "is in sound condition"));
        for (Map.Entry<String, String> name : names.entrySet()) {
            String shown = shown(lang, name.getKey());
            if (!name.getValue().equals(shown)) {
                helper.fail(name.getKey() + " should read " + name.getValue() + " in bloodless mode, reads " + shown);
                return;
            }
        }
        helper.succeed();
    }

    /**
     * Every wet sound has a metal twin, registered, in sounds.json with vanilla sounds under it (for real files to
     * replace later), and with its own subtitle; and the Gut Chain and blood stains sound through them.
     */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void everyWetSoundHasATwin(GameTestHelper helper) {
        JsonObject sounds = json(helper, "assets/bloodandbones/sounds.json");
        JsonObject lang = json(helper, "assets/bloodandbones/lang/en_us.json");
        if (sounds == null || lang == null) {
            return;
        }
        if (BBSounds.twins().size() < 10) {
            helper.fail("Expected every wet sound to have a twin, found " + BBSounds.twins().size());
            return;
        }
        for (BBSounds.Twin twin : BBSounds.twins().values()) {
            for (ResourceLocation id : twin.layer() == null ? List.of(twin.wet().getId(), twin.clean().getId())
                    : List.of(twin.wet().getId(), twin.clean().getId(), twin.layer().getId())) {
                if (!BuiltInRegistries.SOUND_EVENT.containsKey(id)) {
                    helper.fail("Sound " + id + " is not registered");
                    return;
                }
                JsonObject entry = sounds.getAsJsonObject(id.getPath());
                if (entry == null || !lang.has("subtitles.bloodandbones." + id.getPath()) || entry.getAsJsonArray("sounds").isEmpty()) {
                    helper.fail("Sound " + id + " should be in sounds.json with sounds and a subtitle");
                    return;
                }
            }
            if (!twin.clean().getId().getPath().equals("bloodless." + twin.wet().getId().getPath())) {
                helper.fail("A twin is named for its wet sound: " + twin.clean().getId());
                return;
            }
            // the twin is metal: none of its sounds is one of the wet ones
            for (JsonElement sound : sounds.getAsJsonObject(twin.clean().getId().getPath()).getAsJsonArray("sounds")) {
                String name = sound.getAsJsonObject().get("name").getAsString();
                if (name.contains("slime") || name.contains("honey") || name.startsWith(BloodAndBones.MOD_ID + ":")) {
                    helper.fail("The twin " + twin.clean().getId() + " plays " + name);
                    return;
                }
            }
        }
        if (BBSounds.twin(com.avicagan.bloodandbones.registry.BBBlocks.GUT_CHAIN.getDefaultState().getSoundType().getBreakSound().getLocation()) == null
                || BBSounds.twin(com.avicagan.bloodandbones.registry.BBBlocks.BLOOD_STAIN.getDefaultState().getSoundType().getStepSound().getLocation()) == null) {
            helper.fail("The Gut Chain and blood stains should sound through wet sounds with twins");
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** What bloodless mode shows for a key: its own bloodless text, else the rewording (BloodlessLanguage). */
    private static String shown(JsonObject lang, String key) {
        if (lang.has("bloodless." + key)) {
            return lang.get("bloodless." + key).getAsString();
        }
        return lang.has(key) ? BloodlessWords.soften(lang.get(key).getAsString()) : "(missing)";
    }

    private static boolean allows(String allowed, String reader) {
        if (allowed.endsWith("/")) {
            return reader.startsWith(allowed);
        }
        if (allowed.contains("#")) {
            return reader.equals(allowed) || reader.startsWith(allowed + "#");
        }
        return reader.startsWith(allowed + "#") || reader.startsWith(allowed + "$");
    }

    @org.jetbrains.annotations.Nullable
    private static JsonObject json(GameTestHelper helper, String path) {
        Path file = modFile().findResource(path.split("/"));
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            helper.fail("Could not read " + path + ": " + e);
            return null;
        }
    }

    private static IModFile modFile() {
        return ModList.get().getModFileById(BloodAndBones.MOD_ID).getFile();
    }

    /** A field read (GETSTATIC / GETFIELD) or a method called, seen from inside {@code owner#method}. */
    private interface Use {
        void seen(String owner, String method, String name, String fieldOwner, boolean isField);
    }

    /** Every instruction of every method of every class of the mod; returns how many classes were read. */
    private static int scan(Use use) {
        IModFile file = modFile();
        Set<String> names = new TreeSet<>();
        for (ModFileScanData.ClassData data : file.getScanResult().getClasses()) {
            names.add(data.clazz().getInternalName());
        }
        int read = 0;
        for (String name : names) {
            Path path = file.findResource((name + ".class").split("/"));
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(path);
            } catch (Exception e) {
                continue;
            }
            read++;
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String field, String fieldDescriptor) {
                            use.seen(name, method, field, owner, true);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String called, String calledDescriptor, boolean isInterface) {
                            use.seen(name, method, called, owner, false);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return read;
    }
}
