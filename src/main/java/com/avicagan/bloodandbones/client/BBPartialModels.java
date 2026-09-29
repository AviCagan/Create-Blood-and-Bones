package com.avicagan.bloodandbones.client;

import com.avicagan.bloodandbones.BloodAndBones;
import com.avicagan.bloodandbones.config.BBClientConfig;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/**
 * The machines' moving parts, drawn by their renderers (Create's AllPartialModels pattern): the Guillotine's blade and
 * rope, the Beheader's saw, a Deglover roller and a Mangler grinder. Each bloody one has a clean twin that bloodless mode
 * draws instead ({@link #pick}), as the machines' own models are swapped by BloodlessSwap. Made before models load.
 */
public final class BBPartialModels {
    public static final PartialModel GUILLOTINE_BLADE = block("guillotine_blade");
    public static final PartialModel GUILLOTINE_BLADE_CLEAN = block("guillotine_blade_clean");
    public static final PartialModel GUILLOTINE_ROPE = block("guillotine_rope");
    public static final PartialModel BEHEADER_SAW = block("beheader_saw");
    public static final PartialModel BEHEADER_SAW_CLEAN = block("beheader_saw_clean");
    public static final PartialModel DEGLOVER_ROLLER = block("deglover_roller");
    public static final PartialModel DEGLOVER_ROLLER_CLEAN = block("deglover_roller_clean");
    public static final PartialModel MANGLER_GRINDER = block("mangler_grinder");
    public static final PartialModel MANGLER_GRINDER_CLEAN = block("mangler_grinder_clean");

    private BBPartialModels() {
    }

    private static PartialModel block(String path) {
        return PartialModel.of(BloodAndBones.asResource("block/" + path));
    }

    /** The bloody part, or its clean twin in bloodless mode. */
    public static PartialModel pick(PartialModel bloody, PartialModel clean) {
        return BBClientConfig.bloodless() ? clean : bloody;
    }

    /** Loads the class, so the models are asked for before they are baked. */
    public static void init() {
    }
}
