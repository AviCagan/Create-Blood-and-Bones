package com.avicagan.bloodandbones.body;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * Something fitted where a limb was, at the Surgery Table. A basic prosthetic (a peg leg, a hook hand) needs
 * nothing to run and always works, a little worse than flesh.
 *
 * @param kind    the sort of part it replaces, either side
 * @param walk    how well a leg walks, flesh being 1
 * @param work    how fast an arm breaks blocks, flesh being 1
 * @param texture drawn in the part's place on the player, laid out like a player skin
 */
public class ImplantItem extends Item {
    private final BodyPart.Kind kind;
    private final float walk;
    private final float work;
    private final ResourceLocation texture;

    public ImplantItem(Properties properties, BodyPart.Kind kind, float walk, float work, ResourceLocation texture) {
        super(properties.stacksTo(1));
        this.kind = kind;
        this.walk = walk;
        this.work = work;
        this.texture = texture;
    }

    public BodyPart.Kind kind() {
        return kind;
    }

    public boolean fits(BodyPart part) {
        return part.kind() == kind;
    }

    public float walk() {
        return walk;
    }

    public float work() {
        return work;
    }

    public ResourceLocation texture() {
        return texture;
    }

    /** Whether it is doing its job on this wearer. A basic prosthetic always is. */
    public boolean working(@Nullable LivingEntity wearer) {
        return true;
    }
}
