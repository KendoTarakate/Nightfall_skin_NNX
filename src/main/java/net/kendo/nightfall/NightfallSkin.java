package net.kendo.nightfall;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.kendo.nightfall.Network.SkinNetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NightfallSkin implements ModInitializer {
    public static final String MOD_ID = "skinchanger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Skin Changer Mod Initialized!");

        // Initialize sounds
        ModSounds.initialize();

        // Initialize server-side storage
        ServerSkinStorage.initialize();

        // Register server-side receivers
        SkinNetworkHandler.registerServerReceivers();

        // When a player joins, they will request skins themselves (client-side)
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            LOGGER.info("Player {} joined the server", handler.getPlayer().getName().getString());
            
            // No need to send all skins immediately - client will request them
            // This prevents the ping spike issue!
        });
        
        // Log storage info on startup
        long storageSize = ServerSkinStorage.getStorageSize();
        int skinCount = ServerSkinStorage.getAllStoredPlayerUuids().size();
        LOGGER.info("Loaded {} stored skins ({} bytes total)", skinCount, storageSize);
    }
}
