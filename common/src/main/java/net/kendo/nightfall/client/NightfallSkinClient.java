package net.kendo.nightfall.client;

import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kendo.nightfall.ModelPreferenceManager;
import net.kendo.nightfall.NightfallSkin;
import net.kendo.nightfall.Network.SkinNetworkHandler;
import net.kendo.nightfall.SkinChangerScreen;
import net.kendo.nightfall.SkinHistory;
import net.kendo.nightfall.SkinManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Common client entry point. Registers the keybinding, client-side (S2C) packet
 * receivers and client lifecycle hooks through the Architectury cross-loader API,
 * so the exact same code runs on both Fabric and Forge.
 */
@Environment(EnvType.CLIENT)
public final class NightfallSkinClient {
    private static KeyBinding openGuiKey;

    private NightfallSkinClient() {
    }

    public static void initClient() {
        NightfallSkin.LOGGER.info("Skin Changer Client Initialized!");

        // Register client-side (S2C) packet receivers
        SkinNetworkHandler.registerClientReceivers();

        // Register keybinding (I key) via Architectury so it works on both loaders
        openGuiKey = new KeyBinding(
                "key.skinchanger.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "category.skinchanger.general"
        );
        KeyMappingRegistry.register(openGuiKey);

        // Open the GUI when the keybinding is pressed
        ClientTickEvent.CLIENT_POST.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new SkinChangerScreen());
                }
            }
        });

        // Auto-apply last used skin when joining a world/server
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
            MinecraftClient client = MinecraftClient.getInstance();

            // Small delay to ensure client is ready
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                client.execute(() -> {
                    // Get most recent skin from history
                    SkinHistory.SkinEntry lastSkin = SkinHistory.getMostRecentSkin();
                    if (lastSkin != null && lastSkin.getFile().exists()) {
                        try {
                            NightfallSkin.LOGGER.info("Auto-applying last used skin: {}", lastSkin.getDisplayName());
                            java.awt.image.BufferedImage skinImage = javax.imageio.ImageIO.read(lastSkin.getFile());
                            if (skinImage != null) {
                                // Use the saved model preference instead of the skin's stored model
                                boolean useSlim = ModelPreferenceManager.isSlimPreference();
                                SkinManager.applySkin(client, skinImage, useSlim);
                                NightfallSkin.LOGGER.info("Successfully reapplied skin on startup with model: {}",
                                        useSlim ? "Slim" : "Wide");
                            }
                        } catch (Exception e) {
                            NightfallSkin.LOGGER.error("Failed to auto-apply last skin", e);
                        }
                    } else {
                        NightfallSkin.LOGGER.info("No previous skin to apply");
                    }

                    // Request list of players with custom skins from server
                    if (client.getNetworkHandler() != null) {
                        try {
                            Thread.sleep(500); // Small delay to ensure connection is stable
                            SkinNetworkHandler.requestSkinList();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            }).start();
        });
    }
}
