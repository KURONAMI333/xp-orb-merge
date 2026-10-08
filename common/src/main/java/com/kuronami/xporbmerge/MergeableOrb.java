package com.kuronami.xporbmerge;

/** Added to every experience orb by the mixin so merging code can read and set its private state. */
public interface MergeableOrb {

    /** XP this orb carries: its value times its vanilla unit count. */
    long xp_orb_merge$total();

    int xp_orb_merge$count();

    int xp_orb_merge$age();

    /**
     * Rewrites what the orb carries: {@code count} pickups of {@code value} each, and its despawn age.
     */
    void xp_orb_merge$set(int value, int count, int age);
}
