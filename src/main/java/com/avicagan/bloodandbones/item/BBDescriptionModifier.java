package com.avicagan.bloodandbones.item;

import com.avicagan.bloodandbones.config.BBClientConfig;
import com.simibubi.create.foundation.item.ItemDescription;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.world.item.Item;

/**
 * Create's item description, built again when bloodless mode changes as well as when the language does,
 * so the reworded text shows at once.
 */
public class BBDescriptionModifier extends ItemDescription.Modifier {
    private boolean cachedBloodless;

    public BBDescriptionModifier(Item item, FontHelper.Palette palette) {
        super(item, palette);
    }

    @Override
    protected boolean checkLocale() {
        boolean changed = super.checkLocale();
        boolean bloodless = BBClientConfig.bloodless();
        if (bloodless != cachedBloodless) {
            cachedBloodless = bloodless;
            return true;
        }
        return changed;
    }
}
