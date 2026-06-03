package net.kendo.nightfall;

import dev.architectury.event.events.common.PlayerEvent;
import net.kendo.nightfall.Network.SkinNetworkHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common (loader-agnostic) entry point. Called by both the Fabric and Forge
 * platform initializers. Anything client-only lives in
 * {@link net.kendo.nightfall.client.NightfallSkinClient}.
 */
public final class NightfallSkin {
    public static final String MOD_ID = "skinchanger";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private NightfallSkin() {
    }

    public static void init() {
        LOGGER.info("Skin Changer Mod Initialized!");

        // Register sounds (Architectury DeferredRegister)
        ModSounds.initialize();

        // Initialize server-side storage
        ServerSkinStorage.initialize();

        // Register server-side (C2S) packet receivers
        SkinNetworkHandler.registerServerReceivers();

        // Log when a player joins (client requests skins itself to avoid ping spikes)
        PlayerEvent.PLAYER_JOIN.register(player -> {
            LOGGER.info("Player {} joined the server", player.getName().getString());
        });

        // Log storage info on startup
        long storageSize = ServerSkinStorage.getStorageSize();
        int skinCount = ServerSkinStorage.getAllStoredPlayerUuids().size();
        LOGGER.info("Loaded {} stored skins ({} bytes total)", skinCount, storageSize);
    }
}
