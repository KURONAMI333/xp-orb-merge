package com.kuronami.xporbmergetest;

import com.kuronami.xporbmerge.XpOrbMergeCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@GameTestHolder("xp_orb_merge")
@PrefixGameTestTemplate(false)
public class XpOrbGameTests {

    /** Set by the build when a same-feature mod is on the classpath (-PcoexistJar). */
    private static boolean expectInactive() {
        return Boolean.getBoolean("xp_orb_merge.expectInactive");
    }

    private static List<ExperienceOrb> orbs(GameTestHelper helper) {
        AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(7, 7, 7);
        return helper.getLevel().getEntitiesOfClass(ExperienceOrb.class, box, orb -> !orb.isRemoved());
    }

    /** XP an orb carries, read the way the world save sees it (value x count). */
    private static long carried(ExperienceOrb orb) {
        CompoundTag tag = new CompoundTag();
        orb.saveWithoutId(tag);
        return (long) tag.getShort("Value") * tag.getInt("Count");
    }

    private static long total(List<ExperienceOrb> orbs) {
        return orbs.stream().mapToLong(XpOrbGameTests::carried).sum();
    }

    /**
     * Like GameTestHelper#makeMockServerPlayerInLevel, but its connection gets an (empty) channel first:
     * on 1.20.1 Forge, logging in and sending packets fail on a connection without one.
     */
    private static ServerPlayer mockPlayer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "test-mock-player"));
        net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
    }

    /** Mock players all appear at the world spawn; move each into its own test area so tests can't share orbs. */
    private static ServerPlayer playerInside(GameTestHelper helper, ServerPlayer player) {
        Vec3 at = centre(helper);
        player.teleportTo(at.x, at.y - 0.5, at.z);
        return player;
    }

    private static Vec3 centre(GameTestHelper helper) {
        return Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)));
    }

    @GameTest(template = "empty7")
    public static void activeMatchesInstalledMods(GameTestHelper helper) {
        boolean expected = !expectInactive();
        if (XpOrbMergeCommon.isActive() != expected) {
            helper.fail("isActive=" + XpOrbMergeCommon.isActive() + " but expected " + expected);
        }
        helper.succeed();
    }

    /** Vanilla splits 500 XP into 307 + 149 + 37 + 7 = four orbs. */
    @GameTest(template = "empty7")
    public static void awardSpawnsOneOrb(GameTestHelper helper) {
        if (expectInactive()) {
            helper.succeed();
            return;
        }
        ExperienceOrb.award(helper.getLevel(), centre(helper), 500);
        List<ExperienceOrb> found = orbs(helper);
        if (found.size() != 1 || total(found) != 500) {
            helper.fail("expected 1 orb carrying 500, got " + found.size() + " orbs carrying " + total(found));
        }
        helper.succeed();
    }

    /** Orbs of different values dropped next to each other end up as one, total unchanged. */
    @GameTest(template = "empty7", timeoutTicks = 60)
    public static void mixedOrbsMergeExactly(GameTestHelper helper) {
        if (expectInactive()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        Vec3 c = centre(helper);
        int[] values = {1, 3, 7, 17, 37, 73, 149, 307, 1, 3};
        long expected = 0;
        for (int i = 0; i < values.length; i++) {
            ExperienceOrb orb = new ExperienceOrb(level, c.x + (i % 3) * 0.3, c.y, c.z + (i / 3) * 0.3, values[i]);
            orb.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(orb);
            expected += values[i];
        }
        long want = expected;
        helper.runAfterDelay(30, () -> {
            List<ExperienceOrb> found = orbs(helper);
            if (found.size() != 1 || total(found) != want) {
                helper.fail("expected 1 orb carrying " + want + ", got " + found.size() + " orbs carrying " + total(found));
            }
            if (found.get(0).getValue() != want) {
                helper.fail("merged orb should show its whole value " + want + ", shows " + found.get(0).getValue());
            }
            helper.succeed();
        });
    }

    /** Totals above what one saved orb can hold stay exact across the split and a save round trip. */
    @GameTest(template = "empty7")
    public static void hugeAwardStaysExact(GameTestHelper helper) {
        if (expectInactive()) {
            helper.succeed();
            return;
        }
        ExperienceOrb.award(helper.getLevel(), centre(helper), 100_000);
        List<ExperienceOrb> found = orbs(helper);
        if (total(found) != 100_000 || found.size() > 2) {
            helper.fail("expected <=2 orbs carrying 100000, got " + found.size() + " orbs carrying " + total(found));
        }
        for (ExperienceOrb orb : found) {
            if (orb.getValue() > Short.MAX_VALUE) {
                helper.fail("orb value " + orb.getValue() + " would be cut when saved");
            }
        }
        helper.succeed();
    }

    /** Two orbs that already hold a huge total are left alone instead of being rebuilt every second. */
    @GameTest(template = "empty7", timeoutTicks = 80)
    public static void mergedHugeOrbsAreLeftAlone(GameTestHelper helper) {
        if (expectInactive()) {
            helper.succeed();
            return;
        }
        ExperienceOrb.award(helper.getLevel(), centre(helper), 100_000);
        Set<Integer> ids = new HashSet<>();
        orbs(helper).forEach(orb -> ids.add(orb.getId()));
        helper.runAfterDelay(45, () -> {
            Set<Integer> now = new HashSet<>();
            orbs(helper).forEach(orb -> now.add(orb.getId()));
            if (!now.equals(ids) || total(orbs(helper)) != 100_000) {
                helper.fail("orbs were rebuilt or changed: before " + ids + " after " + now + " total " + total(orbs(helper)));
            }
            helper.succeed();
        });
    }

    /** Vanilla's Player#aiStep touches one random orb per tick; here every orb in reach goes in that tick. */
    @GameTest(template = "empty7")
    public static void touchingOrbsArePickedUpTogether(GameTestHelper helper) {
        if (expectInactive()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerInside(helper, mockPlayer(helper));
        player.setGameMode(GameType.SURVIVAL);
        int before = player.totalExperience;
        int expected = 0;
        ExperienceOrb[] dropped = new ExperienceOrb[5];
        for (int i = 0; i < dropped.length; i++) {
            dropped[i] = new ExperienceOrb(level, player.getX() + (i - 2) * 0.3, player.getY(), player.getZ(), 3 + i);
            dropped[i].setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(dropped[i]);
            expected += 3 + i;
        }
        player.aiStep();
        int gained = player.totalExperience - before;
        if (gained != expected) {
            helper.fail("expected to gain " + expected + " XP in one player tick, gained " + gained);
        }
        for (ExperienceOrb orb : dropped) {
            if (!orb.isRemoved()) {
                helper.fail("an orb was left behind");
            }
        }
        helper.succeed();
    }

    /** A caller that still holds orbs the sweep already took must not collect them a second time. */
    @GameTest(template = "empty7")
    public static void sweptOrbsAreNotCollectedTwice(GameTestHelper helper) {
        if (expectInactive()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerInside(helper, mockPlayer(helper));
        player.setGameMode(GameType.SURVIVAL);
        int before = player.totalExperience;
        ExperienceOrb[] dropped = new ExperienceOrb[4];
        int expected = 0;
        for (int i = 0; i < dropped.length; i++) {
            dropped[i] = new ExperienceOrb(level, player.getX() + (i - 1.5) * 0.3, player.getY(), player.getZ(), 5 + i);
            dropped[i].setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(dropped[i]);
            expected += 5 + i;
        }
        // Like a magnet mod iterating a list it built before the pickup.
        for (ExperienceOrb orb : dropped) {
            orb.playerTouch(player);
        }
        int gained = player.totalExperience - before;
        if (gained != expected) {
            helper.fail("expected " + expected + " XP once, gained " + gained);
        }
        helper.succeed();
    }

    /** A 30000 XP orb on a damaged Mending pickaxe: repairs it fully and the rest goes to the player, as in vanilla. */
    @GameTest(template = "empty7")
    public static void mendingRepairsAsInVanilla(GameTestHelper helper) {
        if (expectInactive()) {
            helper.succeed();
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer player = playerInside(helper, mockPlayer(helper));
        player.setGameMode(GameType.SURVIVAL);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.enchant(Enchantments.MENDING, 1);
        pick.setDamageValue(1500);
        player.setItemInHand(InteractionHand.MAIN_HAND, pick);
        int before = player.totalExperience;
        ExperienceOrb orb = new ExperienceOrb(level, player.getX(), player.getY(), player.getZ(), 30_000);
        level.addFreshEntity(orb);
        orb.playerTouch(player);
        int gained = player.totalExperience - before;
        // Mending repairs 2 durability per XP point: 1500 damage costs 750 XP.
        if (player.getMainHandItem().getDamageValue() != 0 || gained != 30_000 - 750) {
            helper.fail("damage left " + player.getMainHandItem().getDamageValue() + ", XP gained " + gained + " (expected 0 and 29250)");
        }
        helper.succeed();
    }
}
