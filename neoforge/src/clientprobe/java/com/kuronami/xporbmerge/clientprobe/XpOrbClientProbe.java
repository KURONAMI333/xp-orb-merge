package com.kuronami.xporbmerge.clientprobe;

import com.kuronami.xporbmerge.XpOrbMergeCommon;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Drops XP out of the player's reach (one award and a cluster of mixed orbs), counts the orbs, then
 * walks the player over them and checks every point arrived.
 */
@Mod(value = "xp_orb_merge_clientprobe", dist = Dist.CLIENT)
public class XpOrbClientProbe extends ProbeHarness {

    private static final int AWARD = 500;
    private static final int[] CLUSTER = {1, 3, 7, 17, 37, 73, 149, 307, 1, 3};

    private Vec3 spot;
    private int xpBefore;
    private int expected;
    private volatile List<Integer> clientValues;

    public XpOrbClientProbe() {
        super("xp-orb-merge");
    }

    private List<ExperienceOrb> orbs(ServerLevel level) {
        return level.getEntitiesOfClass(ExperienceOrb.class, new AABB(spot, spot).inflate(6), orb -> !orb.isRemoved());
    }

    @Override
    protected void onServerTick(MinecraftServer server, ServerPlayer player, int tick) {
        ServerLevel level = server.overworld();
        if (tick == 40) {
            result.put("active", XpOrbMergeCommon.isActive());
            Vec3 feet = player.position();
            player.teleportTo(level, feet.x, feet.y, feet.z, 0.0F, 10.0F);
            // 12 blocks south: beyond the 8 blocks at which orbs start flying to a player.
            spot = feet.add(0, 0.5, 12);
            xpBefore = player.totalExperience;
            ExperienceOrb.award(level, spot, AWARD);
            result.put("orbsFromAward500", orbs(level).size());
            expected = AWARD;
            for (int i = 0; i < CLUSTER.length; i++) {
                ExperienceOrb orb = new ExperienceOrb(level, spot.x + 2 + (i % 3) * 0.3, spot.y, spot.z + (i / 3) * 0.3, CLUSTER[i]);
                orb.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(orb);
                expected += CLUSTER[i];
            }
            result.put("orbsRightAfterCluster", orbs(level).size());
        } else if (tick == 80) {
            result.put("orbsAfterTwoSeconds", orbs(level).size());
            result.put("serverOrbValues", orbs(level).stream().map(ExperienceOrb::getValue).sorted().collect(Collectors.toList()));
            AABB box = new AABB(spot, spot).inflate(6);
            Minecraft mc = Minecraft.getInstance();
            // What the client draws: each orb's size comes from the value the client was told.
            mc.execute(() -> clientValues = mc.level.getEntitiesOfClass(ExperienceOrb.class, box).stream()
                    .map(ExperienceOrb::getValue).sorted().collect(Collectors.toList()));
            screenshot("1-orbs-on-ground");
        } else if (tick == 88) {
            result.put("clientOrbValues", clientValues);
        } else if (tick == 90) {
            player.teleportTo(level, spot.x + 1, spot.y - 0.5, spot.z, 0.0F, 30.0F);
        } else if (tick == 150) {
            int gained = player.totalExperience - xpBefore;
            result.put("xpExpected", expected);
            result.put("xpGained", gained);
            result.put("orbsLeft", orbs(level).size());
            result.put("playerLevel", player.experienceLevel);
            screenshot("2-after-pickup");
            Object after = result.get("orbsAfterTwoSeconds");
            boolean pass = XpOrbMergeCommon.isActive()
                    && result.get("serverOrbValues").equals(result.get("clientOrbValues"))
                    && ((Integer) result.get("orbsFromAward500")) == 1
                    && after instanceof Integer n && n <= 2
                    && gained == expected
                    && orbs(level).isEmpty();
            finish(pass);
        }
    }
}
