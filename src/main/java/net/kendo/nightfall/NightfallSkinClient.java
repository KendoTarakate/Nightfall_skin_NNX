package net.kendo.nightfall;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kendo.nightfall.Network.SkinNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class NightfallSkinClient implements ClientModInitializer {
    private static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        NightfallSkin.LOGGER.info("Skin Changer Client Initialized!");

        // Register network handlers
        SkinNetworkHandler.registerClientReceivers();

        // Register keybinding (I key)
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.skinchanger.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "category.skinchanger.general"
        ));

        // Register tick event to check for keybinding
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.player != null) {
                    client.setScreen(new SkinChangerScreen());
                }
            }
        });

        // Auto-apply last used skin when joining a world/server
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
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
