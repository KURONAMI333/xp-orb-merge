package com.kuronami.xporbmerge.mixin;

import net.minecraft.server.level.ServerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityAccessor {

    @Accessor("serverEntity")
    ServerEntity xp_orb_merge$serverEntity();
}
