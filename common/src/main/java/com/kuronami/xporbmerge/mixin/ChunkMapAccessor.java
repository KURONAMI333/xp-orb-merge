package com.kuronami.xporbmerge.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Entity id -> ChunkMap.TrackedEntity (package-private, hence the wildcard). */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {

    @Accessor("entityMap")
    Int2ObjectMap<?> xp_orb_merge$entityMap();
}
