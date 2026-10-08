package com.kuronami.xporbmerge.mixin;

import com.kuronami.xporbmerge.MergeableOrb;
import com.kuronami.xporbmerge.OrbMerging;
import com.kuronami.xporbmerge.XpOrbMergeCommon;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Changes to vanilla orbs (plain {@code minecraft:experience_orb} only):
 * <ul>
 *   <li>{@code award} (mob kills, smelting, breeding, bottles...) adds the whole amount to an orb
 *       already at that spot, or drops it as one orb, instead of splitting it into fixed sizes.</li>
 *   <li>Every orb, on its regular once-a-second scan, takes in the orbs right around it whatever their
 *       values (vanilla only joins equal values).</li>
 *   <li>A player picks up every orb touching them in the same tick (vanilla takes one at random).</li>
 *   <li>Mending works out a big orb in vanilla-sized pieces, so repairs come out as in vanilla.</li>
 * </ul>
 * XP totals never change; only how many entities carry them.
 */
@Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin extends Entity implements MergeableOrb {

    /** Set while one pickup sweeps up the other touching orbs, so their own pickups don't sweep again. */
    @Unique
    private static boolean xp_orb_merge$sweeping;

    @Shadow
    public int value;
    @Shadow
    private int count;
    @Shadow
    private int age;

    private ExperienceOrbMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Shadow
    private int repairPlayerItems(ServerPlayer player, int value) {
        throw new AssertionError();
    }

    @Override
    public long xp_orb_merge$total() {
        return (long) this.value * this.count;
    }

    @Override
    public int xp_orb_merge$count() {
        return this.count;
    }

    @Override
    public int xp_orb_merge$age() {
        return this.age;
    }

    @Override
    public void xp_orb_merge$set(int value, int count, int age) {
        this.value = value;
        this.count = count;
        this.age = age;
    }

    @Inject(method = "scanForEntities", at = @At("TAIL"))
    private void xp_orb_merge$mergeNearby(CallbackInfo ci) {
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        if (!XpOrbMergeCommon.isActive() || !(this.level() instanceof ServerLevel level) || !OrbMerging.isPlainOrb(self)) {
            return;
        }
        List<ExperienceOrb> group = new ArrayList<>();
        group.add(self);
        group.addAll(level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class),
                this.getBoundingBox().inflate(XpOrbMergeCommon.MERGE_RADIUS),
                orb -> orb != self && OrbMerging.isPlainOrb(orb)));
        if (!OrbMerging.isMerged(group)) {
            OrbMerging.merge(level, group);
        }
    }

    @Inject(method = "award", at = @At("HEAD"), cancellable = true)
    private static void xp_orb_merge$awardAsOne(ServerLevel level, Vec3 pos, int amount, CallbackInfo ci) {
        if (!XpOrbMergeCommon.isActive()) {
            return;
        }
        ci.cancel();
        if (amount <= 0) {
            return;
        }
        // Same reach as vanilla's award merge: an orb dropped at this spot a moment ago (mob farms).
        List<ExperienceOrb> here = level.getEntities(EntityTypeTest.forClass(ExperienceOrb.class),
                AABB.ofSize(pos, 1.0, 1.0, 1.0), OrbMerging::isPlainOrb);
        for (ExperienceOrb orb : here) {
            if (OrbMerging.addTo(level, orb, amount)) {
                return;
            }
        }
        int max = XpOrbMergeCommon.MAX_ORB_VALUE;
        if (amount >= max) {
            ExperienceOrb full = new ExperienceOrb(level, pos.x(), pos.y(), pos.z(), max);
            ((MergeableOrb) full).xp_orb_merge$set(max, amount / max, 0);
            level.addFreshEntity(full);
        }
        if (amount % max > 0) {
            level.addFreshEntity(new ExperienceOrb(level, pos.x(), pos.y(), pos.z(), amount % max));
        }
    }

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void xp_orb_merge$noPickupWait(Player player, CallbackInfo ci) {
        if (!XpOrbMergeCommon.isActive() || !(player instanceof ServerPlayer)) {
            return;
        }
        // Vanilla doesn't check for removed orbs here; its 2-tick pickup wait covered that. With the wait
        // gone, an orb already taken (e.g. by our sweep) could otherwise be collected again by any caller
        // still holding it in a list.
        if (this.isRemoved()) {
            ci.cancel();
            return;
        }
        player.takeXpDelay = 0;
    }

    @Inject(method = "playerTouch", at = @At("TAIL"))
    private void xp_orb_merge$pickUpTheRest(Player player, CallbackInfo ci) {
        if (!XpOrbMergeCommon.isActive() || xp_orb_merge$sweeping || !(player instanceof ServerPlayer)) {
            return;
        }
        // The same box Player#aiStep uses to find orbs to touch.
        AABB reach = player.isPassenger() && !player.getVehicle().isRemoved()
                ? player.getBoundingBox().minmax(player.getVehicle().getBoundingBox()).inflate(1.0, 0.0, 1.0)
                : player.getBoundingBox().inflate(1.0, 0.5, 1.0);
        ExperienceOrb self = (ExperienceOrb) (Object) this;
        xp_orb_merge$sweeping = true;
        try {
            // Plain orbs only: Player#aiStep already touches orb subclasses every tick as ordinary entities.
            for (ExperienceOrb orb : player.level().getEntitiesOfClass(ExperienceOrb.class, reach, orb -> orb != self && OrbMerging.isPlainOrb(orb))) {
                if (!orb.isRemoved()) {
                    orb.playerTouch(player);
                }
            }
        } finally {
            xp_orb_merge$sweeping = false;
        }
    }

    @Inject(method = "repairPlayerItems", at = @At("HEAD"), cancellable = true)
    private void xp_orb_merge$repairInVanillaPieces(ServerPlayer player, int value, CallbackInfoReturnable<Integer> cir) {
        if (value <= XpOrbMergeCommon.VANILLA_MAX_ORB_VALUE) {
            return;
        }
        int left = 0;
        for (int rest = value; rest > 0; ) {
            int piece = Math.min(rest, XpOrbMergeCommon.VANILLA_MAX_ORB_VALUE);
            rest -= piece;
            left += this.repairPlayerItems(player, piece);
        }
        cir.setReturnValue(left);
    }
}
