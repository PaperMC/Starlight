package ca.spottedleaf.starlight;

import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fmllegacy.network.FMLNetworkConstants;

@Mod("starlight")
public final class Starlight {

    public Starlight() {
        // starlight is network compatible both ways
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> {
            return new IExtensionPoint.DisplayTest(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true);
        });
    }

}
