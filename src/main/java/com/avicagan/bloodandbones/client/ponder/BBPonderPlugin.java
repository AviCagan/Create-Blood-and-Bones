package com.avicagan.bloodandbones.client.ponder;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.registry.BBBlocks;
import com.avicagan.bloodandbones.registry.BBItems;
import com.simibubi.create.infrastructure.ponder.AllCreatePonderTags;
import com.tterrag.registrate.util.entry.ItemProviderEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Registered from client setup. Scene text lives in the lang file (see BBScenes). */
public class BBPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return BloodAndBones.MOD_ID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemProviderEntry<?, ?>> scenes = helper.withKeyFunction(RegistryEntry::getId);
        scenes.forComponents(BBBlocks.MANGLER).addStoryBoard("mangler", (b, u) -> BBScenes.machine(b, u, com.avicagan.bloodandbones.machine.MachineKind.MANGLER,
                "Grinding Carcasses with the Mangler", "The Mangler tears each limb off a carcass and grinds it at once, then the body: the quickest way through a carcass. It keeps the least meat and bone, but gives armour scraps and the mob's own drops",
                new ItemStack(Items.BEEF)), AllCreatePonderTags.KINETIC_APPLIANCES);
        scenes.forComponents(BBBlocks.GUILLOTINE).addStoryBoard("guillotine", (b, u) -> BBScenes.machine(b, u, com.avicagan.bloodandbones.machine.MachineKind.GUILLOTINE,
                "Taking Limbs Off with the Guillotine", "The Guillotine winds its blade up while it turns. A redstone pulse drops it through one limb, whole. It never takes the head",
                new ItemStack(BBItems.CARCASS_PIECE.get())), AllCreatePonderTags.KINETIC_APPLIANCES);
        scenes.forComponents(BBBlocks.BEHEADER).addStoryBoard("beheader", (b, u) -> BBScenes.machine(b, u, com.avicagan.bloodandbones.machine.MachineKind.BEHEADER,
                "Taking Heads with the Beheader", "The Beheader takes heads off as they come, one quick stroke each, under a line of hanging carcasses too. Zombies, skeletons, creepers and piglins sometimes leave their skull whole",
                new ItemStack(Items.ZOMBIE_HEAD)), AllCreatePonderTags.KINETIC_APPLIANCES);
        scenes.forComponents(BBBlocks.DEGLOVER).addStoryBoard("deglover", (b, u) -> BBScenes.machine(b, u, com.avicagan.bloodandbones.machine.MachineKind.DEGLOVER,
                "Skinning with the Deglover", "The Deglover rolls the hide off a carcass whole, and a sheep's wool with it. It costs a lot of stress: turn it slowly",
                new ItemStack(BBItems.RAW_HIDE.get())), AllCreatePonderTags.KINETIC_APPLIANCES);
        scenes.forComponents(BBBlocks.BLEEDING_RACK).addStoryBoard("bleeding_rack", BBScenes::bleedingRack, AllCreatePonderTags.FLUIDS);
        scenes.forComponents(BBBlocks.BUTCHER_HOOK, BBBlocks.BLOODY_CASING).addStoryBoard("butcher_hook",
                (b, u) -> BBScenes.butcherHook(b, u, java.util.List.of(piece("pig", "right_front_leg"), piece("cow", "head"),
                        piece("sheep", "left_hind_leg"), piece("chicken", "right_wing"))), AllCreatePonderTags.DECORATION);
        scenes.forComponents(BBBlocks.BUTCHER_TABLE).addStoryBoard("butcher_table",
                (b, u) -> BBScenes.butcherTable(b, u, piece("cow", "right_hind_leg"), new ItemStack(BBItems.CLEAVER.get()),
                        java.util.List.of(new ItemStack(Items.BEEF, 2), new ItemStack(Items.BONE))), AllCreatePonderTags.DECORATION);
        scenes.forComponents(BBBlocks.SPIT_ROAST).addStoryBoard("spit_roast",
                (b, u) -> BBScenes.spitRoast(b, u, new ItemStack(BBItems.CARCASS_PIECE.get())), AllCreatePonderTags.KINETIC_APPLIANCES);
    }

    /** A fresh piece of a vanilla mob, as the hook in the scene holds it. */
    private static ItemStack piece(String mob, String bone) {
        ItemStack stack = new ItemStack(BBItems.CARCASS_PIECE.get());
        String texture = switch (mob) {
            case "cow" -> "textures/entity/cow/cow.png";
            case "pig" -> "textures/entity/pig/pig.png";
            case "sheep" -> "textures/entity/sheep/sheep.png";
            default -> "textures/entity/chicken.png";
        };
        stack.set(com.avicagan.bloodandbones.registry.BBDataComponents.PIECE.get(), new com.avicagan.bloodandbones.item.CarcassPieceItem.Piece(
                ResourceLocation.withDefaultNamespace(mob), bone, ResourceLocation.withDefaultNamespace(texture), java.util.List.of(), 1.0F,
                false, java.util.Map.of(), 0.0F, 0.0F, 0.0F, false));
        return stack;
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        PonderTagRegistrationHelper<RegistryEntry<?, ?>> tags = helper.withKeyFunction(RegistryEntry::getId);
        tags.addToTag(AllCreatePonderTags.KINETIC_APPLIANCES)
                .add(BBBlocks.MANGLER)
                .add(BBBlocks.GUILLOTINE)
                .add(BBBlocks.BEHEADER)
                .add(BBBlocks.DEGLOVER)
                .add(BBBlocks.SPIT_ROAST);
        tags.addToTag(AllCreatePonderTags.FLUIDS)
                .add(BBBlocks.BLEEDING_RACK);
        tags.addToTag(AllCreatePonderTags.DECORATION)
                .add(BBBlocks.BUTCHER_HOOK)
                .add(BBBlocks.BUTCHER_TABLE)
                .add(BBBlocks.BLOODY_CASING);
    }
}
