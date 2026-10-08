package com.kuronami.xporbmerge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class XpOrbMerge {

    public XpOrbMerge(IEventBus eventBus) {
        XpOrbMergeCommon.init();
    }
}
