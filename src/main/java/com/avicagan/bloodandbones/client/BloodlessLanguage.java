package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBClientConfig;
import com.avicagan.bloodandbones.config.BloodlessWords;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * The game's language, with this mod's names and descriptions reworded in bloodless mode (see
 * {@link BloodlessWords}): a Bucket of Blood reads as essence, the Bleeding Rack as a Draining Rack. It
 * wraps whatever language the game loaded, and is put back in front after every reload. Text already on
 * screen is looked up again when the wrapper changes, so switching the mode renames things at once.
 */
public final class BloodlessLanguage extends Language {
    /** I18n keeps its own reference (Create's item descriptions read through it); it has no public setter. */
    private static final Field I18N_LANGUAGE = ObfuscationReflectionHelper.findField(I18n.class, "language");

    private final Language delegate;

    private BloodlessLanguage(Language delegate) {
        this.delegate = delegate;
    }

    /** Put a wrapper in front of the game's language if a reload replaced it. Cheap; called every tick. */
    public static void install() {
        if (!(Language.getInstance() instanceof BloodlessLanguage)) {
            wrap(Language.getInstance());
        }
    }

    /** Bloodless mode changed: a fresh wrapper, so every piece of text is looked up again. */
    public static void refresh() {
        Language current = Language.getInstance();
        wrap(current instanceof BloodlessLanguage wrapper ? wrapper.delegate : current);
    }

    private static void wrap(Language base) {
        BloodlessLanguage wrapper = new BloodlessLanguage(base);
        Language.inject(wrapper);
        try {
            I18N_LANGUAGE.set(null, wrapper);
        } catch (IllegalAccessException e) {
            BloodAndBones.LOGGER.warn("Could not reword descriptions for bloodless mode", e);
        }
    }

    @Override
    public String getOrDefault(String key, String defaultValue) {
        if (!BBClientConfig.bloodless() || !BloodlessWords.reworded(key)) {
            return delegate.getOrDefault(key, defaultValue);
        }
        String own = "bloodless." + key;
        if (delegate.has(own)) {
            return delegate.getOrDefault(own, defaultValue);
        }
        return BloodlessWords.soften(delegate.getOrDefault(key, defaultValue));
    }

    @Override
    public boolean has(String key) {
        return delegate.has(key);
    }

    @Override
    public boolean isDefaultRightToLeft() {
        return delegate.isDefaultRightToLeft();
    }

    @Override
    public FormattedCharSequence getVisualOrder(FormattedText text) {
        return delegate.getVisualOrder(text);
    }

    @Override
    public Map<String, String> getLanguageData() {
        return delegate.getLanguageData();
    }

    @Override
    @Nullable
    public Component getComponent(String key) {
        return delegate.getComponent(key);
    }
}
