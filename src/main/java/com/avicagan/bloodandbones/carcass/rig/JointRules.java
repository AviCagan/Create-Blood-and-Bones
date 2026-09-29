package com.avicagan.bloodandbones.carcass.rig;

import org.joml.Vector3f;

/**
 * Joint limits by part name: how far a head, a leg, a tail, a tentacle or a second body segment may swing, for every bone
 * that does not spell out its own. The rig derivation (datagen) reads them for every vanilla mob, and the generic bodies of mobs
 * with no rig file (GenericRig) read them too, so both sides of a server and a client agree. docs/MODDED-MOBS.md lists
 * the names.
 */
public final class JointRules {
    private JointRules() {
    }

    /** Joint limits by part name, for a part on top of its parent. */
    public static JointSpec jointFor(String name) {
        return jointFor(name, true);
    }

    public static JointSpec jointFor(String name, boolean onTop) {
        return jointFor(name, onTop, false);
    }

    /** A spider's leg: held out flat, it swings up and down little and sweeps and twists a good way. */
    public static final JointSpec FLAT_LEG = new JointSpec(new Vector3f(-20, -40, -60), new Vector3f(20, 40, 60), 2.5F, 0.1F, false);
    /** A squid's tentacle: loose every way, and heavily damped so a bundle of them does not whip about. */
    public static final JointSpec TENTACLE = new JointSpec(new Vector3f(-60, -30, -60), new Vector3f(60, 30, 60), 1.5F, 0.05F, false);
    /** One link of a silverfish's body: bends side to side more than up and down. */
    public static final JointSpec SEGMENT = new JointSpec(new Vector3f(-30, -35, -20), new Vector3f(30, 35, 20), 2.0F, 0.5F, false);

    /**
     * @param onTop whether the part sits on top of its parent (a biped's head on its shoulders); only a head or neck asks
     * @param flat  whether the part lies flat, much longer than it is thick, along the level (a spider's leg held out);
     *              only a leg asks
     */
    public static JointSpec jointFor(String name, boolean onTop, boolean flat) {
        // judge by the part's own name, not the path above it ("body/tail" is a tail)
        String lower = name.substring(name.lastIndexOf('/') + 1).toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("tentacle")) {
            return TENTACLE;
        }
        if (lower.startsWith("segment")) {
            return SEGMENT;
        }
        if (lower.contains("head") || lower.contains("neck")) {
            // a dead neck: the head lolls forward well past level and turns and tips a good way, with only a faint pull
            // back toward the pose so a long neck does not fold flat at once. A head on top of the body (a biped's) keeps
            // colliding with it, so it cannot sink into the chest as it nods; one held out in front (a cow's) must not,
            // or its throat meets the chest at the first nod and it holds the head level
            return new JointSpec(new Vector3f(-30, -45, -15), new Vector3f(60, 45, 15), 2.5F, 0.8F, onTop);
        }
        if (flat && lower.contains("leg")) {
            return FLAT_LEG;
        }
        if (lower.contains("leg") || lower.contains("arm")) {
            // limbs swing fore and aft and splay well out sideways; damped so they settle instead of flailing,
            // and with almost no pull back toward standing: a dead animal pushed from any side collapses
            // (with little sideways give, a push from the side left it standing on four stiff legs)
            return new JointSpec(new Vector3f(-75, -20, -40), new Vector3f(75, 20, 40), 3.0F, 0.1F, false);
        }
        // the back half of a body (a salmon's) swings as a tail does
        if (lower.contains("tail") || lower.contains("wing") || lower.contains("ear") || lower.endsWith("_back")) {
            return new JointSpec(new Vector3f(-45, -45, -45), new Vector3f(45, 45, 45), 2.0F, 1.0F, false);
        }
        if (lower.contains("body")) {
            // a second body segment: a stiff spine
            return new JointSpec(new Vector3f(-20, -15, -10), new Vector3f(20, 15, 10), 4.0F, 2.0F, false);
        }
        return JointSpec.DEFAULT;
    }
}
