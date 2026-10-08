package com.kuronami.xporbmerge;

import com.kuronami.xporbmerge.platform.Services;

import java.util.List;

/**
 * Shared state for every loader. The orb mixin asks {@link #isActive()} before changing anything.
 */
public final class XpOrbMergeCommon {

    /**
     * Mods that already merge orbs. Two mergers rewriting the same orb could lose or duplicate XP,
     * so if one is installed we stand down and leave orbs to it.
     */
    static final List<String> EQUIVALENT_MODS = List.of(
            "clumps",        // Clumps (Jaredlll08)
            "mr_ly_clumps"   // Clumps (ly, datapack-as-mod build)
    );

    /**
     * Largest XP one orb may hold. Orbs save their value as a short, so anything above this would be
     * cut on the next world save. Bigger totals are kept as several units of this size (the orb's
     * vanilla "count").
     */
    public static final int MAX_ORB_VALUE = Short.MAX_VALUE;

    /**
     * Largest orb vanilla ever makes. Mending repairs are worked out in pieces of this size, so the
     * repair maths sees the same numbers it does in vanilla however big the merged orb is.
     */
    public static final int VANILLA_MAX_ORB_VALUE = 2477;

    /**
     * How far, in blocks, an orb's box is grown when it looks for others to merge with. Vanilla uses
     * 0.5 for equal values. 0.75 still cannot reach an orb on the far side of a full block.
     */
    public static final double MERGE_RADIUS = 0.75;

    private static volatile Boolean active;

    private XpOrbMergeCommon() {
    }

    public static void init() {
        resolve();
    }

    public static boolean isActive() {
        Boolean value = active;
        return value != null ? value : resolve();
    }

    private static synchronized boolean resolve() {
        if (active == null) {
            String found = EQUIVALENT_MODS.stream().filter(Services.PLATFORM::isModLoaded).findFirst().orElse(null);
            if (found != null) {
                Constants.LOG.info("'{}' is installed and already merges experience orbs; {} stays inactive.", found, Constants.MOD_NAME);
            }
            active = found == null;
        }
        return active;
    }
}
