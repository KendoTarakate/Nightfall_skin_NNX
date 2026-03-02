package net.kendo.nightfall;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Handles server-side file storage for player skins
 */
public class ServerSkinStorage {
    private static final String STORAGE_DIR = "skinchanger_storage";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static class SkinMetadata {
        public String uuid;
        public String model; // "slim" or "wide"
        public long uploadedTimestamp;
        public String fileName;
        
        public SkinMetadata() {}
        
        public SkinMetadata(UUID uuid, boolean isSlim) {
            this.uuid = uuid.toString();
            this.model = isSlim ? "slim" : "wide";
            this.uploadedTimestamp = System.currentTimeMillis();
            this.fileName = null; // Will be set when saving
        }
        
        public boolean isSlim() {
            return "slim".equals(model);
        }
    }
    
    public static class SkinData {
        public final byte[] imageData;
        public final boolean isSlim;
        
        public SkinData(byte[] imageData, boolean isSlim) {
            this.imageData = imageData;
            this.isSlim = isSlim;
        }
    }
    
    /**
     * Initialize storage directory
     */
    public static void initialize() {
        try {
            Path storageDir = Paths.get(STORAGE_DIR);
            if (!Files.exists(storageDir)) {
                Files.createDirectories(storageDir);
                NightfallSkin.LOGGER.info("Created skin storage directory: {}", storageDir.toAbsolutePath());
            }
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to create storage directory", e);
        }
    }
    
    /**
     * Save a player's skin to disk
     */
    public static boolean saveSkin(UUID playerUuid, byte[] imageData, boolean isSlim) {
        try {
            // Create player directory
            Path playerDir = Paths.get(STORAGE_DIR, playerUuid.toString());
            Files.createDirectories(playerDir);
            
            // Generate filename with timestamp to NOT overwrite old skins
            long timestamp = System.currentTimeMillis();
            String fileName = "skin_" + timestamp + ".png";
            
            // Save skin image with timestamp
            Path skinPath = playerDir.resolve(fileName);
            Files.write(skinPath, imageData);
            
            // Save metadata pointing to the latest skin
            SkinMetadata metadata = new SkinMetadata(playerUuid, isSlim);
            metadata.fileName = fileName; // Update to use new filename
            Path metadataPath = playerDir.resolve("metadata.json");
            String json = GSON.toJson(metadata);
            Files.writeString(metadataPath, json);
            
            NightfallSkin.LOGGER.info("Saved skin for player {} ({} bytes, model: {}, file: {})", 
                playerUuid, imageData.length, metadata.model, fileName);
            
            return true;
            
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to save skin for player " + playerUuid, e);
            return false;
        }
    }
    
    /**
     * Load a player's skin from disk
     */
    public static SkinData loadSkin(UUID playerUuid) {
        try {
            Path playerDir = Paths.get(STORAGE_DIR, playerUuid.toString());
            
            if (!Files.exists(playerDir)) {
                return null;
            }
            
            // Load metadata
            Path metadataPath = playerDir.resolve("metadata.json");
            if (!Files.exists(metadataPath)) {
                NightfallSkin.LOGGER.warn("Metadata not found for player {}", playerUuid);
                return null;
            }
            
            String json = Files.readString(metadataPath);
            SkinMetadata metadata = GSON.fromJson(json, SkinMetadata.class);
            
            // Load skin image
            Path skinPath = playerDir.resolve(metadata.fileName);
            if (!Files.exists(skinPath)) {
                NightfallSkin.LOGGER.warn("Skin file not found for player {}", playerUuid);
                return null;
            }
            
            byte[] imageData = Files.readAllBytes(skinPath);
            
            NightfallSkin.LOGGER.debug("Loaded skin for player {} ({} bytes, model: {})", 
                playerUuid, imageData.length, metadata.model);
            
            return new SkinData(imageData, metadata.isSlim());
            
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to load skin for player " + playerUuid, e);
            return null;
        }
    }
    
    /**
     * Check if a player has a stored skin
     */
    public static boolean hasSkin(UUID playerUuid) {
        Path playerDir = Paths.get(STORAGE_DIR, playerUuid.toString());
        Path metadataPath = playerDir.resolve("metadata.json");
        
        // Only check if metadata exists (it points to the actual skin file)
        if (!Files.exists(metadataPath)) {
            return false;
        }
        
        // Verify the metadata points to an existing file
        try {
            String json = Files.readString(metadataPath);
            SkinMetadata metadata = GSON.fromJson(json, SkinMetadata.class);
            
            if (metadata.fileName == null) {
                return false;
            }
            
            Path skinPath = playerDir.resolve(metadata.fileName);
            return Files.exists(skinPath);
            
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Delete a player's skin
     */
    public static boolean deleteSkin(UUID playerUuid) {
        try {
            Path playerDir = Paths.get(STORAGE_DIR, playerUuid.toString());
            
            if (!Files.exists(playerDir)) {
                return false;
            }
            
            // Delete all files in directory
            Files.walk(playerDir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        NightfallSkin.LOGGER.error("Failed to delete: " + path, e);
                    }
                });
            
            NightfallSkin.LOGGER.info("Deleted skin for player {}", playerUuid);
            return true;
            
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to delete skin for player " + playerUuid, e);
            return false;
        }
    }
    
    /**
     * Get all player UUIDs that have stored skins
     */
    public static List<UUID> getAllStoredPlayerUuids() {
        List<UUID> uuids = new ArrayList<>();
        
        try {
            Path storageDir = Paths.get(STORAGE_DIR);
            
            if (!Files.exists(storageDir)) {
                return uuids;
            }
            
            Files.list(storageDir)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        String uuidStr = dir.getFileName().toString();
                        UUID uuid = UUID.fromString(uuidStr);
                        
                        // Verify it has required files
                        if (hasSkin(uuid)) {
                            uuids.add(uuid);
                        }
                    } catch (IllegalArgumentException e) {
                        // Invalid UUID directory name, skip
                    }
                });
                
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to list stored skins", e);
        }
        
        return uuids;
    }
    
    /**
     * Get metadata for a player without loading the full image
     */
    public static SkinMetadata getMetadata(UUID playerUuid) {
        try {
            Path metadataPath = Paths.get(STORAGE_DIR, playerUuid.toString(), "metadata.json");
            
            if (!Files.exists(metadataPath)) {
                return null;
            }
            
            String json = Files.readString(metadataPath);
            return GSON.fromJson(json, SkinMetadata.class);
            
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to read metadata for player " + playerUuid, e);
            return null;
        }
    }
    
    /**
     * Clean up old skins (optional utility method)
     */
    public static int cleanupOldSkins(long maxAgeMillis) {
        int cleaned = 0;
        long currentTime = System.currentTimeMillis();
        
        for (UUID uuid : getAllStoredPlayerUuids()) {
            SkinMetadata metadata = getMetadata(uuid);
            
            if (metadata != null && (currentTime - metadata.uploadedTimestamp) > maxAgeMillis) {
                if (deleteSkin(uuid)) {
                    cleaned++;
                }
            }
        }
        
        NightfallSkin.LOGGER.info("Cleaned up {} old skins", cleaned);
        return cleaned;
    }
    
    /**
     * Get total storage size in bytes
     */
    public static long getStorageSize() {
        try {
            Path storageDir = Paths.get(STORAGE_DIR);
            
            if (!Files.exists(storageDir)) {
                return 0;
            }
            
            return Files.walk(storageDir)
                .filter(Files::isRegularFile)
                .mapToLong(path -> {
                    try {
                        return Files.size(path);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
                
        } catch (IOException e) {
            NightfallSkin.LOGGER.error("Failed to calculate storage size", e);
            return 0;
        }
    }
}
