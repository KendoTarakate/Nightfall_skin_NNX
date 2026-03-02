package net.kendo.nightfall.Network;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.kendo.nightfall.NightfallSkin;
import net.kendo.nightfall.ServerSkinStorage;
import net.kendo.nightfall.SkinManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SkinNetworkHandler {
    // Upload skin to server
    public static final Identifier UPLOAD_SKIN_START = new Identifier("skinchanger", "upload_skin_start");
    public static final Identifier UPLOAD_SKIN_CHUNK = new Identifier("skinchanger", "upload_skin_chunk");
    public static final Identifier UPLOAD_SKIN_END = new Identifier("skinchanger", "upload_skin_end");
    
    // Request/Download skin from server
    public static final Identifier REQUEST_SKIN = new Identifier("skinchanger", "request_skin");
    public static final Identifier REQUEST_SKIN_LIST = new Identifier("skinchanger", "request_skin_list");
    public static final Identifier DOWNLOAD_SKIN_START = new Identifier("skinchanger", "download_skin_start");
    public static final Identifier DOWNLOAD_SKIN_CHUNK = new Identifier("skinchanger", "download_skin_chunk");
    public static final Identifier DOWNLOAD_SKIN_END = new Identifier("skinchanger", "download_skin_end");
    public static final Identifier SKIN_LIST_RESPONSE = new Identifier("skinchanger", "skin_list_response");
    
    // Reset skin
    public static final Identifier RESET_SKIN = new Identifier("skinchanger", "reset_skin");
    public static final Identifier PLAYER_SKIN_RESET = new Identifier("skinchanger", "player_skin_reset");

    private static final int CHUNK_SIZE = 20000;
    private static int maxMultiplayerSize = 512;

    // Storage for assembling chunked data
    private static final Map<UUID, ChunkedSkinData> receivingUploads = new ConcurrentHashMap<>();
    private static final Map<UUID, ChunkedSkinData> receivingDownloads = new ConcurrentHashMap<>();

    private static class ChunkedSkinData {
        final int totalChunks;
        final Map<Integer, byte[]> chunks;
        final boolean isSlim;
        final long timestamp;

        ChunkedSkinData(int totalChunks, boolean isSlim) {
            this.totalChunks = totalChunks;
            this.chunks = new HashMap<>();
            this.isSlim = isSlim;
            this.timestamp = System.currentTimeMillis();
        }

        void addChunk(int index, byte[] data) {
            chunks.put(index, data);
        }

        boolean isComplete() {
            return chunks.size() == totalChunks;
        }

        byte[] assemble() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (int i = 0; i < totalChunks; i++) {
                byte[] chunk = chunks.get(i);
                if (chunk == null) {
                    throw new RuntimeException("Missing chunk " + i);
                }
                baos.write(chunk, 0, chunk.length);
            }
            return baos.toByteArray();
        }
    }

    public static void setMaxMultiplayerSize(int size) {
        maxMultiplayerSize = size;
        NightfallSkin.LOGGER.info("Set max multiplayer skin size to: {}x{}", size, size);
    }

    public static void registerClientReceivers() {
        // Receive list of players with custom skins
        ClientPlayNetworking.registerGlobalReceiver(SKIN_LIST_RESPONSE, (client, handler, buf, responseSender) -> {
            int count = buf.readInt();
            List<UUID> playerUuids = new ArrayList<>();
            
            for (int i = 0; i < count; i++) {
                playerUuids.add(buf.readUuid());
            }
            
            NightfallSkin.LOGGER.info("Received skin list: {} players with custom skins", count);
            
            client.execute(() -> {
                // Request each skin
                for (UUID uuid : playerUuids) {
                    if (!uuid.equals(client.player.getUuid())) {
                        requestSkinFromServer(uuid);
                    }
                }
            });
        });
        
        // Start receiving downloaded skin from server
        ClientPlayNetworking.registerGlobalReceiver(DOWNLOAD_SKIN_START, (client, handler, buf, responseSender) -> {
            UUID playerUuid = buf.readUuid();
            boolean isSlim = buf.readBoolean();
            int totalChunks = buf.readInt();
            int totalSize = buf.readInt();

            NightfallSkin.LOGGER.info("Downloading skin for player {} ({} bytes in {} chunks)",
                    playerUuid, totalSize, totalChunks);

            client.execute(() -> {
                receivingDownloads.put(playerUuid, new ChunkedSkinData(totalChunks, isSlim));
            });
        });

        // Receive skin chunk from server
        ClientPlayNetworking.registerGlobalReceiver(DOWNLOAD_SKIN_CHUNK, (client, handler, buf, responseSender) -> {
            UUID playerUuid = buf.readUuid();
            int chunkIndex = buf.readInt();
            int chunkSize = buf.readInt();
            byte[] chunkData = new byte[chunkSize];
            buf.readBytes(chunkData);

            client.execute(() -> {
                ChunkedSkinData data = receivingDownloads.get(playerUuid);
                if (data != null) {
                    data.addChunk(chunkIndex, chunkData);
                    NightfallSkin.LOGGER.debug("Received chunk {}/{} for player {}",
                            chunkIndex + 1, data.totalChunks, playerUuid);
                }
            });
        });

        // Complete skin download
        ClientPlayNetworking.registerGlobalReceiver(DOWNLOAD_SKIN_END, (client, handler, buf, responseSender) -> {
            UUID playerUuid = buf.readUuid();

            client.execute(() -> {
                ChunkedSkinData data = receivingDownloads.remove(playerUuid);
                if (data != null && data.isComplete()) {
                    try {
                        byte[] fullData = data.assemble();
                        SkinManager.applyRemoteSkin(client, playerUuid, fullData, data.isSlim);
                        NightfallSkin.LOGGER.info("Successfully downloaded and applied skin for player {}", playerUuid);
                    } catch (Exception e) {
                        NightfallSkin.LOGGER.error("Failed to assemble downloaded skin for player " + playerUuid, e);
                    }
                } else {
                    NightfallSkin.LOGGER.error("Failed to download complete skin for player {}", playerUuid);
                }
            });
        });

        // Receive notification that a player reset their skin
        ClientPlayNetworking.registerGlobalReceiver(PLAYER_SKIN_RESET, (client, handler, buf, responseSender) -> {
            UUID playerUuid = buf.readUuid();
            NightfallSkin.LOGGER.info("Player {} reset their skin", playerUuid);
            
            client.execute(() -> {
                SkinManager.removeRemoteSkin(playerUuid);
            });
        });
    }

    public static void registerServerReceivers() {
        // CLIENT -> SERVER: Upload skin start
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_SKIN_START, (server, player, handler, buf, responseSender) -> {
            UUID senderUuid = player.getUuid();
            boolean isSlim = buf.readBoolean();
            int totalChunks = buf.readInt();
            int totalSize = buf.readInt();

            NightfallSkin.LOGGER.info("Receiving skin upload from {} ({} bytes in {} chunks)",
                    player.getName().getString(), totalSize, totalChunks);

            server.execute(() -> {
                receivingUploads.put(senderUuid, new ChunkedSkinData(totalChunks, isSlim));
            });
        });

        // CLIENT -> SERVER: Upload skin chunk
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_SKIN_CHUNK, (server, player, handler, buf, responseSender) -> {
            UUID senderUuid = player.getUuid();
            int chunkIndex = buf.readInt();
            int chunkSize = buf.readInt();
            byte[] chunkData = new byte[chunkSize];
            buf.readBytes(chunkData);

            server.execute(() -> {
                ChunkedSkinData data = receivingUploads.get(senderUuid);
                if (data != null) {
                    data.addChunk(chunkIndex, chunkData);
                }
            });
        });

        // CLIENT -> SERVER: Upload complete - save to storage
        ServerPlayNetworking.registerGlobalReceiver(UPLOAD_SKIN_END, (server, player, handler, buf, responseSender) -> {
            UUID senderUuid = player.getUuid();

            server.execute(() -> {
                ChunkedSkinData data = receivingUploads.remove(senderUuid);
                if (data != null && data.isComplete()) {
                    try {
                        byte[] fullData = data.assemble();

                        // Save to disk storage
                        boolean saved = ServerSkinStorage.saveSkin(senderUuid, fullData, data.isSlim);
                        
                        if (saved) {
                            NightfallSkin.LOGGER.info("Saved skin for {} to storage ({} bytes)",
                                    player.getName().getString(), fullData.length);
                            
                            // IMPORTANT: Notify all OTHER online players about the new skin
                            for (ServerPlayerEntity otherPlayer : server.getPlayerManager().getPlayerList()) {
                                if (!otherPlayer.getUuid().equals(senderUuid)) {
                                    try {
                                        // Send the skin to this player immediately
                                        sendSkinToPlayer(otherPlayer, senderUuid, fullData, data.isSlim);
                                        NightfallSkin.LOGGER.info("Sent new skin from {} to {}", 
                                            player.getName().getString(), otherPlayer.getName().getString());
                                    } catch (Exception e) {
                                        NightfallSkin.LOGGER.error("Failed to send skin to " + otherPlayer.getName().getString(), e);
                                    }
                                }
                            }
                        } else {
                            NightfallSkin.LOGGER.error("Failed to save skin for {}", player.getName().getString());
                        }
                    } catch (Exception e) {
                        NightfallSkin.LOGGER.error("Failed to process uploaded skin", e);
                    }
                }
            });
        });

        // CLIENT -> SERVER: Request list of players with skins
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_SKIN_LIST, (server, player, handler, buf, responseSender) -> {
            server.execute(() -> {
                List<UUID> playerUuids = new ArrayList<>();
                
                // Get all online players who have stored skins
                for (ServerPlayerEntity onlinePlayer : server.getPlayerManager().getPlayerList()) {
                    UUID uuid = onlinePlayer.getUuid();
                    if (ServerSkinStorage.hasSkin(uuid)) {
                        playerUuids.add(uuid);
                    }
                }
                
                // Send list back to requester
                PacketByteBuf response = new PacketByteBuf(Unpooled.buffer());
                response.writeInt(playerUuids.size());
                for (UUID uuid : playerUuids) {
                    response.writeUuid(uuid);
                }
                
                ServerPlayNetworking.send(player, SKIN_LIST_RESPONSE, response);
                
                NightfallSkin.LOGGER.info("Sent skin list to {} ({} skins available)", 
                    player.getName().getString(), playerUuids.size());
            });
        });

        // CLIENT -> SERVER: Request specific player's skin
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_SKIN, (server, player, handler, buf, responseSender) -> {
            UUID requestedUuid = buf.readUuid();
            
            server.execute(() -> {
                // Load from storage
                ServerSkinStorage.SkinData skinData = ServerSkinStorage.loadSkin(requestedUuid);
                
                if (skinData != null) {
                    sendSkinToPlayer(player, requestedUuid, skinData.imageData, skinData.isSlim);
                    NightfallSkin.LOGGER.info("Sent skin {} to {}", requestedUuid, player.getName().getString());
                } else {
                    NightfallSkin.LOGGER.warn("Skin not found for {}, requested by {}", 
                        requestedUuid, player.getName().getString());
                }
            });
        });

        // CLIENT -> SERVER: Reset skin
        ServerPlayNetworking.registerGlobalReceiver(RESET_SKIN, (server, player, handler, buf, responseSender) -> {
            UUID senderUuid = player.getUuid();
            
            server.execute(() -> {
                // Delete from storage
                boolean deleted = ServerSkinStorage.deleteSkin(senderUuid);
                
                if (deleted) {
                    NightfallSkin.LOGGER.info("Deleted skin for {}", player.getName().getString());
                    
                    // Notify other players
                    for (ServerPlayerEntity targetPlayer : server.getPlayerManager().getPlayerList()) {
                        if (!targetPlayer.getUuid().equals(senderUuid)) {
                            try {
                                PacketByteBuf resetBuf = new PacketByteBuf(Unpooled.buffer());
                                resetBuf.writeUuid(senderUuid);
                                ServerPlayNetworking.send(targetPlayer, PLAYER_SKIN_RESET, resetBuf);
                            } catch (Exception e) {
                                NightfallSkin.LOGGER.error("Failed to send reset notification", e);
                            }
                        }
                    }
                }
            });
        });
    }

    /**
     * CLIENT: Upload skin to server
     */
    public static void uploadSkinToServer(byte[] imageData, boolean isSlim) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));

            // Downscale if needed
            if (image.getWidth() > maxMultiplayerSize || image.getHeight() > maxMultiplayerSize) {
                NightfallSkin.LOGGER.info("Downscaling skin from {}x{} to {}x{}",
                        image.getWidth(), image.getHeight(), maxMultiplayerSize, maxMultiplayerSize);
                image = downscaleImage(image, maxMultiplayerSize, maxMultiplayerSize);
            }

            // Convert to PNG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] pngData = baos.toByteArray();

            // Further compression if needed
            while (pngData.length > 100000 && image.getWidth() > 64) {
                int newSize = image.getWidth() / 2;
                NightfallSkin.LOGGER.warn("Skin still too large ({} bytes), downscaling to {}x{}",
                        pngData.length, newSize, newSize);
                image = downscaleImage(image, newSize, newSize);
                baos.reset();
                ImageIO.write(image, "PNG", baos);
                pngData = baos.toByteArray();
            }

            NightfallSkin.LOGGER.info("Uploading skin: {}x{}, {} bytes",
                    image.getWidth(), image.getHeight(), pngData.length);

            sendChunkedUpload(pngData, isSlim);

        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to upload skin", e);
            throw new RuntimeException("Failed to upload skin: " + e.getMessage());
        }
    }

    /**
     * CLIENT: Send chunked upload to server
     */
    private static void sendChunkedUpload(byte[] data, boolean isSlim) {
        int totalChunks = (int) Math.ceil((double) data.length / CHUNK_SIZE);

        // Send start
        PacketByteBuf startBuf = new PacketByteBuf(Unpooled.buffer());
        startBuf.writeBoolean(isSlim);
        startBuf.writeInt(totalChunks);
        startBuf.writeInt(data.length);
        ClientPlayNetworking.send(UPLOAD_SKIN_START, startBuf);

        // Send chunks
        for (int i = 0; i < totalChunks; i++) {
            int offset = i * CHUNK_SIZE;
            int chunkSize = Math.min(CHUNK_SIZE, data.length - offset);

            PacketByteBuf chunkBuf = new PacketByteBuf(Unpooled.buffer());
            chunkBuf.writeInt(i);
            chunkBuf.writeInt(chunkSize);
            chunkBuf.writeBytes(data, offset, chunkSize);
            ClientPlayNetworking.send(UPLOAD_SKIN_CHUNK, chunkBuf);
        }

        // Send end
        PacketByteBuf endBuf = new PacketByteBuf(Unpooled.buffer());
        ClientPlayNetworking.send(UPLOAD_SKIN_END, endBuf);

        NightfallSkin.LOGGER.info("Uploaded {} chunks ({} bytes total)", totalChunks, data.length);
    }

    /**
     * SERVER: Send skin to a player
     */
    private static void sendSkinToPlayer(ServerPlayerEntity player, UUID skinOwnerUuid, byte[] data, boolean isSlim) {
        try {
            int totalChunks = (int) Math.ceil((double) data.length / CHUNK_SIZE);

            // Send start
            PacketByteBuf startBuf = new PacketByteBuf(Unpooled.buffer());
            startBuf.writeUuid(skinOwnerUuid);
            startBuf.writeBoolean(isSlim);
            startBuf.writeInt(totalChunks);
            startBuf.writeInt(data.length);
            ServerPlayNetworking.send(player, DOWNLOAD_SKIN_START, startBuf);

            // Send chunks
            for (int i = 0; i < totalChunks; i++) {
                int offset = i * CHUNK_SIZE;
                int chunkSize = Math.min(CHUNK_SIZE, data.length - offset);

                PacketByteBuf chunkBuf = new PacketByteBuf(Unpooled.buffer());
                chunkBuf.writeUuid(skinOwnerUuid);
                chunkBuf.writeInt(i);
                chunkBuf.writeInt(chunkSize);
                chunkBuf.writeBytes(data, offset, chunkSize);
                ServerPlayNetworking.send(player, DOWNLOAD_SKIN_CHUNK, chunkBuf);
            }

            // Send end
            PacketByteBuf endBuf = new PacketByteBuf(Unpooled.buffer());
            endBuf.writeUuid(skinOwnerUuid);
            ServerPlayNetworking.send(player, DOWNLOAD_SKIN_END, endBuf);

        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to send skin to player", e);
        }
    }

    /**
     * CLIENT: Request list of available skins
     */
    public static void requestSkinList() {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        ClientPlayNetworking.send(REQUEST_SKIN_LIST, buf);
        NightfallSkin.LOGGER.info("Requested skin list from server");
    }

    /**
     * CLIENT: Request specific player's skin
     */
    public static void requestSkinFromServer(UUID playerUuid) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeUuid(playerUuid);
        ClientPlayNetworking.send(REQUEST_SKIN, buf);
        NightfallSkin.LOGGER.debug("Requested skin for player {}", playerUuid);
    }

    /**
     * CLIENT: Send skin reset to server
     */
    public static void sendSkinReset() {
        try {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            ClientPlayNetworking.send(RESET_SKIN, buf);
            NightfallSkin.LOGGER.info("Sent skin reset to server");
        } catch (Exception e) {
            NightfallSkin.LOGGER.error("Failed to send skin reset", e);
        }
    }

    private static BufferedImage downscaleImage(BufferedImage original, int targetWidth, int targetHeight) {
        BufferedImage resized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(original, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return resized;
    }
}
