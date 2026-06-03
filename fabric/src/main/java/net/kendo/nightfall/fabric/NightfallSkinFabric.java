package net.kendo.nightfall.fabric;

import net.fabricmc.api.ModInitializer;
import net.kendo.nightfall.NightfallSkin;

public final class NightfallSkinFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        NightfallSkin.init();
    }
}
