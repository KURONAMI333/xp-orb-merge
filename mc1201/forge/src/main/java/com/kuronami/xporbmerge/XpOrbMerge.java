package com.kuronami.xporbmerge;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;

@Mod(Constants.MOD_ID)
public class XpOrbMerge {

    public XpOrbMerge() {
        // Server-side logic only: clients without the mod can join, so don't flag the server as incompatible.
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (remote, isServer) -> true));
        XpOrbMergeCommon.init();
    }
}
