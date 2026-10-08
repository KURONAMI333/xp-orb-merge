package com.kuronami.xporbmerge;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import com.kuronami.xporbmerge.mixin.ChunkMapAccessor;
import com.kuronami.xporbmerge.mixin.TrackedEntityAccessor;
import net.minecraft.network.protocol.game.ClientboundAddExperienceOrbPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Folds a group of orbs into as few as possible while carrying exactly the same XP.
 * <p>
 * The surviving orbs are edited in place (no new entities, so nothing that reacts to orbs entering
 * the world runs twice) and re-announced to the players who can see them, because clients only learn
 * an orb's value, and so its size on screen, when it is announced.
 */
public final class OrbMerging {

    private OrbMerging() {
    }

    /** Plain vanilla orbs only: subclasses from other mods keep their own type and behaviour. */
    public static boolean isPlainOrb(ExperienceOrb orb) {
        return orb.getType() == EntityType.EXPERIENCE_ORB && !orb.isRemoved();
    }

    private static boolean isCompact(ExperienceOrb orb) {
        MergeableOrb data = (MergeableOrb) orb;
        return data.xp_orb_merge$count() == 1 || orb.getValue() == XpOrbMergeCommon.MAX_ORB_VALUE;
    }

    /**
     * True when the group is already as merged as it can get: at most one orb of full-size units and
     * one smaller orb. Without this check a total too big for one orb would be redone every second.
     */
    public static boolean isMerged(List<ExperienceOrb> group) {
        int full = 0;
        int partial = 0;
        for (ExperienceOrb orb : group) {
            if (!isCompact(orb)) {
                return false;
            }
            if (orb.getValue() == XpOrbMergeCommon.MAX_ORB_VALUE) {
                full++;
            } else {
                partial++;
            }
        }
        return full <= 1 && partial <= 1;
    }

    /**
     * Moves all XP of {@code group} into its first orb (and, for totals beyond one orb, into the second
     * orb as well), then removes the rest. Leaves everything untouched if the result could not be
     * represented exactly.
     */
    public static void merge(ServerLevel level, List<ExperienceOrb> group) {
        if (group.size() < 2 && (group.isEmpty() || isCompact(group.get(0)))) {
            return;
        }
        long total = 0;
        int youngest = Integer.MAX_VALUE;
        for (ExperienceOrb orb : group) {
            MergeableOrb data = (MergeableOrb) orb;
            total += data.xp_orb_merge$total();
            youngest = Math.min(youngest, data.xp_orb_merge$age());
        }
        int max = XpOrbMergeCommon.MAX_ORB_VALUE;
        long fullUnits = total / max;
        int rest = (int) (total % max);
        ExperienceOrb first = group.get(0);
        if (fullUnits == 0) {
            set(level, first, rest, 1, youngest);
            discardFrom(group, 1);
        } else if (fullUnits <= Integer.MAX_VALUE && (rest == 0 || group.size() >= 2)) {
            set(level, first, max, (int) fullUnits, youngest);
            if (rest > 0) {
                set(level, group.get(1), rest, 1, youngest);
                discardFrom(group, 2);
            } else {
                discardFrom(group, 1);
            }
        }
    }

    private static void discardFrom(List<ExperienceOrb> group, int start) {
        for (int i = start; i < group.size(); i++) {
            group.get(i).discard();
        }
    }

    /**
     * Adds {@code amount} to {@code orb} if the sum still fits in one orb.
     *
     * @return false (and no change) when it does not fit
     */
    public static boolean addTo(ServerLevel level, ExperienceOrb orb, int amount) {
        MergeableOrb data = (MergeableOrb) orb;
        long total = data.xp_orb_merge$total() + amount;
        if (total > XpOrbMergeCommon.MAX_ORB_VALUE) {
            return false;
        }
        set(level, orb, (int) total, 1, 0);
        return true;
    }

    private static void set(ServerLevel level, ExperienceOrb orb, int value, int count, int age) {
        int shownBefore = orb.getValue();
        ((MergeableOrb) orb).xp_orb_merge$set(value, count, age);
        if (value != shownBefore) {
            reannounce(level, orb, value);
        }
    }

    /**
     * Sends the orb again under the same id so the client drops its copy and shows the new size. Uses the
     * position the server last sent (as vanilla's own add packet does), because later movement updates
     * are deltas from it; then resends the motion, which the client copy would otherwise randomise.
     */
    private static void reannounce(ServerLevel level, ExperienceOrb orb, int value) {
        Object tracked = ((ChunkMapAccessor) level.getChunkSource().chunkMap).xp_orb_merge$entityMap().get(orb.getId());
        if (tracked == null) {
            return; // nobody can see it yet; players who start tracking it get the current value anyway
        }
        Vec3 base = ((TrackedEntityAccessor) tracked).xp_orb_merge$serverEntity().getPositionBase();
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeVarInt(orb.getId());
            buf.writeDouble(base.x);
            buf.writeDouble(base.y);
            buf.writeDouble(base.z);
            buf.writeShort(value);
            level.getChunkSource().broadcast(orb, ClientboundAddExperienceOrbPacket.STREAM_CODEC.decode(buf));
        } finally {
            buf.release();
        }
        level.getChunkSource().broadcast(orb, new ClientboundSetEntityMotionPacket(orb));
    }
}
