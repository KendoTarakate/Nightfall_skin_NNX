package net.kendo.nightfall;

import com.mojang.authlib.GameProfile;
import net.kendo.nightfall.Network.SkinNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SkinManager {
    // Store skins by UUID
    private static final Map<UUID, SkinData> customSkins = new HashMap<>();

    // Current player's skin info
    private static Identifier currentCustomSkin = null;
    private static boolean currentIsSlim = false;

    public static class SkinData {
        public final Identifier textureId;
        public final boolean isSlim;
        public final byte[] imageData; // Store for network transmission

        public SkinData(Identifier textureId, boolean isSlim, byte[] imageData) {
            this.textureId = textureId;
            this.isSlim = isSlim;
            this.imageData = imageData;
        }
    }

    /**
     * Apply skin from URL (async)
     */
    public static CompletableFuture<Identifier> applySkinFromUrl(MinecraftClient client, String url, boolean isSlim) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                NightfallSkin.LOGGER.info("Downloading skin from URL: {}", url);

                // Download image
                BufferedImage skinImage = downloadImageFromUrl(url);

                if (skinImage == null) {
                    throw new RuntimeException("Failed to download or decode image from URL");
                }

                // Validate skin dimensions
                if (!isValidSkinDimensions(skinImage)) {
                    throw new RuntimeException("Invalid skin dimensions: " + skinImage.getWidth() + "x" + skinImage.getHeight());
                }

                NightfallSkin.LOGGER.info("Downloaded skin: {}x{}", skinImage.getWidth(), skinImage.getHeight());

                // Save to cache directory for history
                File cachedFile = saveSkinToCache(skinImage, url);

                // Apply the skin on the main thread
                final BufferedImage finalImage = skinImage;
                final File finalCachedFile = cachedFile;

                return client.submit(() -> {
                    Identifier textureId = applySkin(client, finalImage, isSlim);

                    // Add to history with the cached file
                    if (textureId != null && finalCachedFile != null) {
                        SkinHistory.addSkin(finalCachedFile, textureId, isSlim);
                    }

                    return textureId;
                }).join();

            } catch (Exception e) {
                NightfallSkin.LOGGER.error("Failed to apply skin from URL: {}", url, e);
                throw new RuntimeException("Failed to apply skin from URL: " + e.getMessage());
            }
        });
    }

    /**
     * Save downloaded skin to cache directory
     */
    private static File saveSkinToCache(BufferedImage image, String url) {
        try {
            // Create cache directory
            File cacheDir = new File("skinchanger_cache");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            // Generate filename from URL
            String fileName = "url_" + Math.abs(url.hashCode()) + "_" + System.currentTimeMillis() + ".png";
            File cacheFile = new File(cacheDir, fileName);

            // Save image
            ImageIO.write(image, "PNG", cacheFile);

            NightfallSkin.LOGGER.info("Cached skin to: {}", cacheFile.getAbsolutePath());
            return cacheFile;

        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to cache skin", e);
            return null;
        }
    }

    /**
     * Download image from URL
     */
    private static BufferedImage downloadImageFromUrl(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Set user agent to avoid blocks
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setConnectTimeout(10000); // 10 seconds
        connection.setReadTimeout(10000);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("HTTP error code: " + responseCode);
        }

        // Check content type
        String contentType = connection.getContentType();
        if (contentType != null && !contentType.startsWith("image/")) {
            throw new IOException("URL does not point to an image. Content-Type: " + contentType);
        }

        try (InputStream inputStream = connection.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                throw new IOException("Failed to decode image");
            }
            return image;
        }
    }

    /**
     * Validate skin dimensions
     */
    private static boolean isValidSkinDimensions(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        // Must be square
        if (width != height) return false;

        // Must be between 64x64 and 512x512
        if (width < 64 || width > 512) return false;

        // Must be power of 2 or multiple of 64
        return width % 64 == 0;
    }

    /**
     * Apply skin for the local player (called from GUI)
     */
    public static Identifier applySkin(MinecraftClient client, BufferedImage skinImage, boolean isSlim) {
        try {
            if (client.player == null) return null;

            // Convert to PNG byte array for network transmission
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(skinImage, "PNG", baos);
            byte[] imageData = baos.toByteArray();

            NativeImage nativeImage = convertToNativeImage(skinImage);
            Identifier textureId = new Identifier("skinchanger", "skin_" + System.currentTimeMillis());

            // Register texture
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            client.getTextureManager().registerTexture(textureId, texture);

            // Store for local player
            UUID playerUuid = client.player.getUuid();
            customSkins.put(playerUuid, new SkinData(textureId, isSlim, imageData));

            // Save current skin info
            currentCustomSkin = textureId;
            currentIsSlim = isSlim;

            NightfallSkin.LOGGER.info("Applied custom skin locally: {} (Model: {})", textureId, (isSlim ? "Slim" : "Classic"));

            // Send to server if in multiplayer
            if (client.getNetworkHandler() != null) {
                sendSkinToServer(client, imageData, isSlim);
            }

            return textureId;

        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to apply skin", e);
            throw new RuntimeException("Failed to apply skin: " + e.getMessage());
        }
    }

    /**
     * Send skin to server (upload to storage)
     */
    private static void sendSkinToServer(MinecraftClient client, byte[] imageData, boolean isSlim) {
        try {
            if (client.player == null) return;

            NightfallSkin.LOGGER.info("Uploading skin to server storage ({} bytes, slim={})", imageData.length, isSlim);

            // Use the network handler to upload
            SkinNetworkHandler.uploadSkinToServer(imageData, isSlim);

        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to upload skin to server", e);
        }
    }

    /**
     * Apply skin received from another player via network
     */
    public static void applyRemoteSkin(MinecraftClient client, UUID playerUuid, byte[] imageData, boolean isSlim) {
        try {
            BufferedImage skinImage = ImageIO.read(new ByteArrayInputStream(imageData));
            if (skinImage == null) {
                NightfallSkin.LOGGER.error("Failed to decode remote skin image");
                return;
            }

            NativeImage nativeImage = convertToNativeImage(skinImage);
            Identifier textureId = new Identifier("skinchanger", "remote_" + playerUuid.toString().replace("-", ""));

            // Cleanup old texture if exists
            SkinData oldSkin = customSkins.get(playerUuid);
            if (oldSkin != null && oldSkin.textureId != null) {
                try {
                    client.getTextureManager().destroyTexture(oldSkin.textureId);
                } catch (Exception e) {
                    // Ignore cleanup errors
                }
            }

            // Register new texture
            NativeImageBackedTexture texture = new NativeImageBackedTexture(nativeImage);
            client.getTextureManager().registerTexture(textureId, texture);

            // Store skin data
            customSkins.put(playerUuid, new SkinData(textureId, isSlim, imageData));

            NightfallSkin.LOGGER.info("Applied remote skin for player {}: {} (Slim: {})",
                    playerUuid, textureId, isSlim);

        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to apply remote skin for player " + playerUuid, e);
        }
    }

    /**
     * Reset skin to default
     */
    public static void resetSkin(MinecraftClient client) {
        if (client.player == null) return;

        UUID playerUuid = client.player.getUuid();

        // Cleanup texture
        if (currentCustomSkin != null) {
            try {
                client.getTextureManager().destroyTexture(currentCustomSkin);
            } catch (Exception e) {
                // Ignore
            }
            currentCustomSkin = null;
        }

        // Remove from map
        customSkins.remove(playerUuid);
        currentIsSlim = false;

        NightfallSkin.LOGGER.info("Reset to default skin");

        // Send reset to server if in multiplayer
        if (client.getNetworkHandler() != null) {
            SkinNetworkHandler.sendSkinReset();
        }
    }

    /**
     * Get custom skin by GameProfile (called from mixin)
     */
    public static Identifier getCustomSkin(GameProfile profile) {
        if (profile == null || profile.getId() == null) return null;

        SkinData skinData = customSkins.get(profile.getId());
        return skinData != null ? skinData.textureId : null;
    }

    /**
     * Check if player uses slim model (called from mixin)
     */
    public static boolean isSlimModel(GameProfile profile) {
        if (profile == null || profile.getId() == null) return false;

        SkinData skinData = customSkins.get(profile.getId());
        return skinData != null && skinData.isSlim;
    }

    /**
     * Get current player's custom skin
     */
    public static Identifier getCurrentCustomSkin() {
        return currentCustomSkin;
    }

    /**
     * Check if current player uses slim model
     */
    public static boolean isCurrentSlim() {
        return currentIsSlim;
    }

    /**
     * Check if a player has a custom skin
     */
    public static boolean hasCustomSkin(UUID playerUuid) {
        return customSkins.containsKey(playerUuid);
    }

    /**
     * Get skin data for network transmission
     */
    public static SkinData getSkinData(UUID playerUuid) {
        return customSkins.get(playerUuid);
    }

    /**
     * Convert BufferedImage to NativeImage
     */
    private static NativeImage convertToNativeImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        NativeImage nativeImage = new NativeImage(width, height, true);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = bufferedImage.getRGB(x, y);

                // Extract ARGB components
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;

                // NativeImage uses ABGR format
                int abgr = (alpha << 24) | (blue << 16) | (green << 8) | red;

                nativeImage.setColor(x, y, abgr);
            }
        }

        return nativeImage;
    }

    /**
     * Remove a remote player's skin
     */
    public static void removeRemoteSkin(UUID playerUuid) {
        SkinData oldSkin = customSkins.remove(playerUuid);
        if (oldSkin != null && oldSkin.textureId != null) {
            try {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client != null) {
                    client.getTextureManager().destroyTexture(oldSkin.textureId);
                }
            } catch (Exception e) {
                // Ignore cleanup errors
            }
            NightfallSkin.LOGGER.info("Removed remote skin for player {}", playerUuid);
        }
    }

    /**
     * Clear all custom skins (for cleanup)
     */
    public static void clearAllSkins() {
        customSkins.clear();
        currentCustomSkin = null;
        currentIsSlim = false;
    }
}