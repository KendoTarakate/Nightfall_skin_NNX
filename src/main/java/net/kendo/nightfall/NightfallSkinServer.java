package net.kendo.nightfall;

import net.fabricmc.api.DedicatedServerModInitializer;

public class NightfallSkinServer implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        NightfallSkin.LOGGER.info("Skin Changer Dedicated Server Initialized!");
    }
}