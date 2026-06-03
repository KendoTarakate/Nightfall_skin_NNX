package net.kendo.nightfall;

/**
 * Server-side skin manager - now just a wrapper around ServerSkinStorage
 * Kept for backward compatibility
 */
public class ServerSkinManager {
    
    /**
     * @deprecated Use ServerSkinStorage.SkinData instead
     */
    @Deprecated
    public static class SkinData {
        public final byte[] imageData;
        public final boolean isSlim;
        public final long timestamp;

        public SkinData(byte[] imageData, boolean isSlim) {
            this.imageData = imageData;
            this.isSlim = isSlim;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * @deprecated Use ServerSkinStorage.saveSkin() instead
     */
    @Deprecated
    public static void storeSkin(java.util.UUID playerUuid, byte[] imageData, boolean isSlim) {
        ServerSkinStorage.saveSkin(playerUuid, imageData, isSlim);
    }

    /**
     * Get a player's skin data
     */
    public static SkinData getSkinData(java.util.UUID playerUuid) {
        ServerSkinStorage.SkinData data = ServerSkinStorage.loadSkin(playerUuid);
        if (data != null) {
            return new SkinData(data.imageData, data.isSlim);
        }
        return null;
    }

    /**
     * Check if a player has a custom skin
     */
    public static boolean hasSkin(java.util.UUID playerUuid) {
        return ServerSkinStorage.hasSkin(playerUuid);
    }

    /**
     * @deprecated Use ServerSkinStorage.deleteSkin() instead
     */
    @Deprecated
    public static void removeSkin(java.util.UUID playerUuid) {
        ServerSkinStorage.deleteSkin(playerUuid);
    }

    /**
     * @deprecated No longer needed - data persists in files
     */
    @Deprecated
    public static void clearAllSkins() {
        NightfallSkin.LOGGER.warn("clearAllSkins() is deprecated - skins are stored in files");
    }
}
