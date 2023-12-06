package ca.spottedleaf.starlight;

import net.neoforged.fml.IExtensionPoint;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;

@Mod("starlight")
public final class Starlight {

    public Starlight() {
        // starlight is network compatible both ways
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> {
            return new IExtensionPoint.DisplayTest(() -> IExtensionPoint.DisplayTest.IGNORESERVERONLY, (a, b) -> true);
        });
    }

}
