package net.kendo.nightfall.fabric.client;

import net.fabricmc.api.ClientModInitializer;
import net.kendo.nightfall.client.NightfallSkinClient;

public final class NightfallSkinClientFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        NightfallSkinClient.initClient();
    }
}
