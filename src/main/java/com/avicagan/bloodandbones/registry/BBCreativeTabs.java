package com.avicagan.bloodandbones.registry;

import com.avicagan.bloodandbones.BloodAndBones;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;

public class BBCreativeTabs {
    public static final RegistryEntry<CreativeModeTab, CreativeModeTab> MAIN = BloodAndBones.REGISTRATE
            .defaultCreativeTab("main", builder -> builder
                    .title(Component.translatable("itemGroup.bloodandbones.main"))
                    .icon(() -> BBItems.MEAT_HOOK.asStack()))
            .register();

    public static void register() {
        BloodAndBones.REGISTRATE.addRawLang("itemGroup.bloodandbones.main", "Create: Blood & Bones");
    }
}
