package net.kendo.nightfall;

import net.kendo.nightfall.ModelPreferenceManager;
import net.kendo.nightfall.Network.SkinNetworkHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Quaternionf;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SkinChangerScreen extends Screen {
    private static final int DROP_ZONE_WIDTH = 350;
    private static final int DROP_ZONE_HEIGHT = 180;
    private static final int HISTORY_WIDTH = 150;
    private static final int HISTORY_ITEM_HEIGHT = 70;
    private static final int HISTORY_PADDING = 10;
    
    // 3D Model viewer variables
    private static final int MODEL_VIEWER_WIDTH = 200;
    private static final int MODEL_VIEWER_HEIGHT = 300;
    private float modelRotationY = 0f;
    private float modelRotationX = 0f;
    private float modelScale = 100f;  // Base scale for the model
    private float cameraZoom = 1050f;  // Camera distance (lower = closer/zoomed in)
    private boolean isDraggingModel = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    private File draggedFile = null;
    private String statusMessage = "Drag & Drop skin file OR enter URL below";
    private int statusColor = 0xFFFFFF;
    private List<String> draggedFiles = new ArrayList<>();
    private boolean isDragging = false;
    private boolean isSlimModel;
    private int historyScroll = 0;
    private int maxHistoryScroll = 0;

    private TextFieldWidget urlTextField;
    private boolean isLoadingFromUrl = false;
    private int selectedResolution = 512;
    private int previousGuiScale = -1;
    private long lastClickTime = 0;
    private static final long DOUBLE_CLICK_DELAY = 300;

    public SkinChangerScreen() {
        super(Text.literal("Skin Changer"));
    }

    @Override
    protected void init() {
        if (previousGuiScale == -1) {
            previousGuiScale = client.options.getGuiScale().getValue();
            if (previousGuiScale != 2) {
                client.options.getGuiScale().setValue(2);
                client.onResolutionChanged();
                return;
            }
        }

        this.clearChildren();
        super.init();

        isSlimModel = ModelPreferenceManager.isSlimPreference();

        int visibleHeight = this.height - 80;
        int totalHistoryHeight = SkinHistory.getHistory().size() * (HISTORY_ITEM_HEIGHT + HISTORY_PADDING);
        maxHistoryScroll = Math.max(0, totalHistoryHeight - visibleHeight);

        // Calculate positions - History(left) -> Model Viewer(middle) -> Controls(right)
        int modelViewerX = HISTORY_WIDTH + 30;
        int controlsX = modelViewerX + MODEL_VIEWER_WIDTH + 30;
        int dropZoneY = this.height / 2 - DROP_ZONE_HEIGHT / 2;
        
        // Right side buttons X position (to the right of drop zone)
        int buttonX = controlsX + DROP_ZONE_WIDTH + 20;

        // URL text field
        urlTextField = new TextFieldWidget(this.textRenderer, controlsX + 10,
                dropZoneY + DROP_ZONE_HEIGHT + 30, DROP_ZONE_WIDTH - 20, 20, Text.literal("URL"));
        urlTextField.setPlaceholder(Text.literal("https://example.com/skin.png"));
        urlTextField.setMaxLength(500);
        this.addSelectableChild(urlTextField);

        // Load from URL button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("โหลด"), button -> {
            loadSkinFromUrl();
        }).dimensions(controlsX + 10, dropZoneY + DROP_ZONE_HEIGHT + 55,
                (DROP_ZONE_WIDTH - 30) / 2, 20).build());

        // Clear URL button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("ล้าง"), button -> {
            urlTextField.setText("");
            if (draggedFile == null) {
                statusMessage = "โยนหรือลากไฟล์ลงในกรอบ";
                statusColor = 0xFFFFFF;
            }
        }).dimensions(controlsX + (DROP_ZONE_WIDTH - 30) / 2 + 20, dropZoneY + DROP_ZONE_HEIGHT + 55,
                (DROP_ZONE_WIDTH - 30) / 2, 20).build());

        // Model type toggle - RIGHT OF DROP ZONE
        this.addDrawableChild(ButtonWidget.builder(Text.literal((isSlimModel ? "Slim" : "Wide")), button -> {
            isSlimModel = !isSlimModel;
            ModelPreferenceManager.setSlimPreference(isSlimModel);
            button.setMessage(Text.literal((isSlimModel ? "Slim" : "Wide")));

            if (SkinManager.getCurrentCustomSkin() != null) {
                try {
                    SkinManager.SkinData currentSkin = SkinManager.getSkinData(client.player.getUuid());
                    if (currentSkin != null && currentSkin.imageData != null) {
                        BufferedImage skinImage = ImageIO.read(new java.io.ByteArrayInputStream(currentSkin.imageData));
                        SkinManager.applySkin(client, skinImage, isSlimModel);
                        statusMessage = "Model changed to " + (isSlimModel ? "Slim" : "Wide") + "!";
                        statusColor = 0x00FF00;
                    }
                } catch (Exception e) {
                    NightfallSkin.LOGGER.error("Failed to change model type", e);
                    statusMessage = "Failed to change model type!";
                    statusColor = 0xFF0000;
                }
            } else if (draggedFile != null || !urlTextField.getText().isEmpty()) {
                statusMessage = "Model will be " + (isSlimModel ? "Slim" : "Wide") + " when applied";
                statusColor = 0xFFFF00;
            } else {
                statusMessage = "Model preference saved: " + (isSlimModel ? "Slim" : "Wide");
                statusColor = 0x00FF00;
            }
        }).dimensions(buttonX, dropZoneY + 75, 100, 20).build());

        // Apply button - RIGHT OF DROP ZONE
        this.addDrawableChild(ButtonWidget.builder(Text.literal("เปลี่ยนสกิน"), button -> {
            if (draggedFile != null && draggedFile.exists()) {
                applySkin(draggedFile);
            } else if (!urlTextField.getText().isEmpty() && !isLoadingFromUrl) {
                loadSkinFromUrl();
            }
        }).dimensions(buttonX, dropZoneY + 100, 100, 20).build());

        // Open Downloads folder button - RIGHT OF DROP ZONE
        this.addDrawableChild(ButtonWidget.builder(Text.literal("เปิดโฟลเดอร์"), button -> {
            browseAndSelectFile();
        }).dimensions(buttonX, dropZoneY + 125, 100, 20).build());

        // Close button - RIGHT OF DROP ZONE
        this.addDrawableChild(ButtonWidget.builder(Text.literal("§cปิด"), button -> {
            this.close();
        }).dimensions(buttonX, dropZoneY + 150, 100, 20).build());
        
        // Reset model view button - BELOW MODEL VIEWER
        this.addDrawableChild(ButtonWidget.builder(Text.literal("รีเซ็ตมุมมอง"), button -> {
            modelRotationY = 0f;
            modelRotationX = 0f;
            modelScale = 100f;
            cameraZoom = 1050f;  // Reset camera zoom
        }).dimensions(modelViewerX, this.height - 100, MODEL_VIEWER_WIDTH, 20).build());
    }

    private void browseAndSelectFile() {
        new Thread(() -> {
            try {
                String userHome = System.getProperty("user.home");
                File downloadsDir = new File(userHome, "Downloads");
                String defaultPath = downloadsDir.exists() ? downloadsDir.getAbsolutePath() : userHome;

                String selectedPath = org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog(
                        "Select Minecraft Skin",
                        defaultPath,
                        null,
                        null,
                        false
                );

                if (selectedPath != null && !selectedPath.isEmpty()) {
                    File selectedFile = new File(selectedPath);

                    client.execute(() -> {
                        if (isValidSkinFile(selectedFile)) {
                            draggedFile = selectedFile;
                            applySkin(selectedFile);
                        } else {
                            statusMessage = "Invalid skin file! Must be PNG (64x64 to 512x512)";
                            statusColor = 0xFF0000;
                        }
                    });
                } else {
                    client.execute(() -> {
                        statusMessage = "File selection cancelled";
                        statusColor = 0xFFFFFF;
                    });
                }

            } catch (Exception e) {
                NightfallSkin.LOGGER.error("Failed to open file chooser", e);
                e.printStackTrace();
                client.execute(() -> {
                    statusMessage = "Failed to open file browser!";
                    statusColor = 0xFF0000;
                });
            }
        }).start();

        statusMessage = "Opening file browser...";
        statusColor = 0x00FFFF;
    }

    private void loadSkinFromUrl() {
        String url = urlTextField.getText().trim();

        if (url.isEmpty()) {
            statusMessage = "Please enter a URL!";
            statusColor = 0xFF0000;
            return;
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            statusMessage = "URL must start with http:// or https://";
            statusColor = 0xFF0000;
            return;
        }

        isLoadingFromUrl = true;
        statusMessage = "Downloading skin from URL...";
        statusColor = 0xFFFF00;
        draggedFile = null;

        SkinManager.applySkinFromUrl(client, url, isSlimModel)
                .thenAccept(textureId -> {
                    client.execute(() -> {
                        isLoadingFromUrl = false;
                        if (textureId != null) {
                            statusMessage = "Skin loaded from URL and added to history!";
                            statusColor = 0x00FF00;

                            int visibleHeight = this.height - 80;
                            int totalHistoryHeight = SkinHistory.getHistory().size() * (HISTORY_ITEM_HEIGHT + HISTORY_PADDING);
                            maxHistoryScroll = Math.max(0, totalHistoryHeight - visibleHeight);

                            this.clearAndInit();
                        } else {
                            statusMessage = "Failed to load skin from URL!";
                            statusColor = 0xFF0000;
                        }
                    });
                })
                .exceptionally(e -> {
                    client.execute(() -> {
                        isLoadingFromUrl = false;
                        statusMessage = "Error: " + e.getMessage();
                        if (statusMessage.length() > 80) {
                            statusMessage = statusMessage.substring(0, 77) + "...";
                        }
                        statusColor = 0xFF0000;
                        NightfallSkin.LOGGER.error("Failed to load skin from URL", e);
                    });
                    return null;
                });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render custom blur background
        renderCustomBlurBackground(context);
        
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        // Draw panels in order: History -> Model Viewer -> Drop Zone
        drawHistoryPanel(context, mouseX, mouseY);
        draw3DModelViewer(context, mouseX, mouseY, delta);
        drawDropZone(context, mouseX, mouseY);

        urlTextField.render(context, mouseX, mouseY, delta);
        
        // Draw URL label
        int modelViewerX = HISTORY_WIDTH + 30;
        int controlsX = modelViewerX + MODEL_VIEWER_WIDTH + 30;
        int dropZoneY = this.height / 2 - DROP_ZONE_HEIGHT / 2;
        context.drawTextWithShadow(this.textRenderer, "Skin URL:",
                controlsX + 10, dropZoneY + DROP_ZONE_HEIGHT + 18, 0xFFFFFF);
    }
    
    private void drawDropZone(DrawContext context, int mouseX, int mouseY) {
        int modelViewerX = HISTORY_WIDTH + 30;
        int controlsX = modelViewerX + MODEL_VIEWER_WIDTH + 30;
        int dropZoneX = controlsX;
        int dropZoneY = this.height / 2 - DROP_ZONE_HEIGHT / 2;

        int borderColor = isDragging ? 0xFF00FF00 : 0xFF888888;
        context.fill(dropZoneX, dropZoneY, dropZoneX + DROP_ZONE_WIDTH, dropZoneY + DROP_ZONE_HEIGHT, 0x80000000);

        context.fill(dropZoneX, dropZoneY, dropZoneX + DROP_ZONE_WIDTH, dropZoneY + 2, borderColor);
        context.fill(dropZoneX, dropZoneY + DROP_ZONE_HEIGHT - 2, dropZoneX + DROP_ZONE_WIDTH, dropZoneY + DROP_ZONE_HEIGHT, borderColor);
        context.fill(dropZoneX, dropZoneY, dropZoneX + 2, dropZoneY + DROP_ZONE_HEIGHT, borderColor);
        context.fill(dropZoneX + DROP_ZONE_WIDTH - 2, dropZoneY, dropZoneX + DROP_ZONE_WIDTH, dropZoneY + DROP_ZONE_HEIGHT, borderColor);

        List<String> lines = wrapText(statusMessage, DROP_ZONE_WIDTH - 20);
        int startY = dropZoneY + (DROP_ZONE_HEIGHT - lines.size() * 10) / 2;
        for (int i = 0; i < lines.size(); i++) {
            context.drawCenteredTextWithShadow(this.textRenderer, lines.get(i), 
                    dropZoneX + DROP_ZONE_WIDTH / 2, startY + i * 10, statusColor);
        }

        if (draggedFile != null) {
            context.drawCenteredTextWithShadow(this.textRenderer, "File: " + draggedFile.getName(),
                    dropZoneX + DROP_ZONE_WIDTH / 2, dropZoneY + DROP_ZONE_HEIGHT - 15, 0xFFFFFF);
        }
    }
    
    private void draw3DModelViewer(DrawContext context, int mouseX, int mouseY, float delta) {
        int viewerX = HISTORY_WIDTH + 30;
        int viewerY = 100;
        
        // Draw background panel with transparency (same as drop zone)
        context.fill(viewerX, viewerY, viewerX + MODEL_VIEWER_WIDTH, 
                    viewerY + MODEL_VIEWER_HEIGHT, 0x80000000);
        
        // Draw title
        context.drawCenteredTextWithShadow(this.textRenderer, "Skin Preview", 
                viewerX + MODEL_VIEWER_WIDTH / 2, viewerY + 5, 0xFFFFFF);
        
        // Draw border
        int borderColor = 0xFF888888;
        context.fill(viewerX, viewerY, viewerX + MODEL_VIEWER_WIDTH, viewerY + 2, borderColor);
        context.fill(viewerX, viewerY + MODEL_VIEWER_HEIGHT - 2, viewerX + MODEL_VIEWER_WIDTH, 
                    viewerY + MODEL_VIEWER_HEIGHT, borderColor);
        context.fill(viewerX, viewerY, viewerX + 2, viewerY + MODEL_VIEWER_HEIGHT, borderColor);
        context.fill(viewerX + MODEL_VIEWER_WIDTH - 2, viewerY, viewerX + MODEL_VIEWER_WIDTH, 
                    viewerY + MODEL_VIEWER_HEIGHT, borderColor);
        
        // Auto-rotate when not dragging
        if (!isDraggingModel) {
            modelRotationY += delta * 0.5f;
        }
        
        // Draw instructions
        context.drawCenteredTextWithShadow(this.textRenderer, "Drag: Rotate Left/Right", 
                viewerX + MODEL_VIEWER_WIDTH / 2, viewerY + MODEL_VIEWER_HEIGHT - 30, 0xAAAAAA);
        
        // Render the 3D player model
        if (client.player != null) {
            renderPlayerModel(context, viewerX + MODEL_VIEWER_WIDTH / 2, 
                            viewerY + MODEL_VIEWER_HEIGHT / 2 + 90,
                            modelScale, cameraZoom, modelRotationX, modelRotationY);
        }
    }
    
    private void renderPlayerModel(DrawContext context, int x, int y, float scale, 
                                   float zoom, float rotationX, float rotationY) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        
        // Position the model with zoom (z-axis distance)
        matrices.translate(x, y, zoom);
        
        // FIXED: Flip Y scale to make model right-side up
        matrices.scale(1.0f, -1.0f, 1.0f);
        
        // Create rotation quaternion - rotate around Y axis for horizontal rotation
        Quaternionf quaternion = new Quaternionf();
        quaternion.rotateY((float) Math.toRadians(-rotationY)); // Normal Y rotation
        quaternion.rotateX((float) Math.toRadians(-rotationX)); // Normal X rotation
        
        matrices.multiply(quaternion);
        matrices.scale(scale, scale, scale);
        
        // Setup lighting
        DiffuseLighting.method_34742();
        
        EntityRenderDispatcher dispatcher = this.client.getEntityRenderDispatcher();
        quaternion.conjugate();
        dispatcher.setRotation(quaternion);
        
        // Render the player entity
        VertexConsumerProvider.Immediate immediate = this.client.getBufferBuilders().getEntityVertexConsumers();
        
        try {
            dispatcher.render(client.player, 0, 0, 0, 0.0F, 1.0F, matrices, immediate, 15728880);
            immediate.draw();
        } catch (Exception e) {
            // Silently handle rendering errors
        }
        
        DiffuseLighting.enableGuiDepthLighting();
        matrices.pop();
    }

    private void drawHistoryPanel(DrawContext context, int mouseX, int mouseY) {
        int panelX = 10;
        int panelY = 40;
        int panelHeight = this.height - 80;

        // Draw background panel with transparency (same as drop zone)
        context.fill(panelX, panelY, panelX + HISTORY_WIDTH, panelY + panelHeight, 0x80000000);
        
        // Draw border for history panel
        int borderColor = 0xFF888888;
        context.fill(panelX, panelY, panelX + HISTORY_WIDTH, panelY + 2, borderColor);
        context.fill(panelX, panelY + panelHeight - 2, panelX + HISTORY_WIDTH, panelY + panelHeight, borderColor);
        context.fill(panelX, panelY, panelX + 2, panelY + panelHeight, borderColor);
        context.fill(panelX + HISTORY_WIDTH - 2, panelY, panelX + HISTORY_WIDTH, panelY + panelHeight, borderColor);
        context.drawTextWithShadow(this.textRenderer, "Recent Skins", panelX + 5, panelY + 5, 0xFFFFFF);

        List<SkinHistory.SkinEntry> history = SkinHistory.getHistory();
        int yOffset = panelY + 25 - historyScroll;

        for (int i = 0; i < history.size(); i++) {
            SkinHistory.SkinEntry entry = history.get(i);
            int itemY = yOffset + i * (HISTORY_ITEM_HEIGHT + HISTORY_PADDING);

            if (itemY + HISTORY_ITEM_HEIGHT < panelY + 25 || itemY > panelY + panelHeight) {
                continue;
            }

            boolean hovered = mouseX >= panelX && mouseX <= panelX + HISTORY_WIDTH &&
                    mouseY >= itemY && mouseY <= itemY + HISTORY_ITEM_HEIGHT;

            // Transparent background for history items
            int bgColor = hovered ? 0x60FFFFFF : 0x40000000;
            context.fill(panelX + 5, itemY, panelX + HISTORY_WIDTH - 5, itemY + HISTORY_ITEM_HEIGHT, bgColor);
            
            // White border for history items
            int itemBorderColor = 0xFFFFFFFF;
            context.fill(panelX + 5, itemY, panelX + HISTORY_WIDTH - 5, itemY + 1, itemBorderColor); // Top
            context.fill(panelX + 5, itemY + HISTORY_ITEM_HEIGHT - 1, panelX + HISTORY_WIDTH - 5, itemY + HISTORY_ITEM_HEIGHT, itemBorderColor); // Bottom
            context.fill(panelX + 5, itemY, panelX + 6, itemY + HISTORY_ITEM_HEIGHT, itemBorderColor); // Left
            context.fill(panelX + HISTORY_WIDTH - 6, itemY, panelX + HISTORY_WIDTH - 5, itemY + HISTORY_ITEM_HEIGHT, itemBorderColor); // Right

            Identifier textureId = entry.getTextureId();

            boolean textureExists = false;
            if (textureId != null) {
                try {
                    java.lang.reflect.Field texturesField = client.getTextureManager().getClass().getDeclaredField("textures");
                    texturesField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    java.util.Map<Identifier, net.minecraft.client.texture.AbstractTexture> textures =
                            (java.util.Map<Identifier, net.minecraft.client.texture.AbstractTexture>) texturesField.get(client.getTextureManager());

                    net.minecraft.client.texture.AbstractTexture texture = textures.get(textureId);
                    textureExists = (texture != null && texture instanceof net.minecraft.client.texture.NativeImageBackedTexture);
                } catch (Exception e) {
                    textureExists = false;
                }
            }

            if (!textureExists) {
                try {
                    if (entry.getFile().exists()) {
                        BufferedImage skinImage = ImageIO.read(entry.getFile());
                        if (skinImage != null) {
                            Identifier newTextureId = new Identifier("skinchanger",
                                    "history_preview_" + entry.getFile().getName().hashCode() + "_" + System.currentTimeMillis());
                            net.minecraft.client.texture.NativeImage nativeImage = convertToNativeImage(skinImage);
                            net.minecraft.client.texture.NativeImageBackedTexture texture =
                                    new net.minecraft.client.texture.NativeImageBackedTexture(nativeImage);
                            client.getTextureManager().registerTexture(newTextureId, texture);
                            entry.setTextureId(newTextureId);
                            textureId = newTextureId;
                            textureExists = true;
                        }
                    }
                } catch (Exception ex) {
                    NightfallSkin.LOGGER.debug("Could not load history preview texture for {}", entry.getFileName());
                    textureId = null;
                    textureExists = false;
                }
            }

            if (textureExists && textureId != null) {
                try {
                    context.drawTexture(textureId,
                            panelX + 10, itemY + 5,
                            40, 40,
                            8.0f, 8.0f,
                            8, 8,
                            64, 64);
                } catch (Exception e) {
                    context.fill(panelX + 10, itemY + 5, panelX + 50, itemY + 45, 0xFF444444);
                }
            } else {
                context.fill(panelX + 10, itemY + 5, panelX + 50, itemY + 45, 0xFF444444);
            }

            String fileName = entry.getDisplayName();
            if (this.textRenderer.getWidth(fileName) > HISTORY_WIDTH - 60) {
                fileName = fileName.substring(0, Math.min(12, fileName.length())) + "...";
            }
            context.drawTextWithShadow(this.textRenderer, fileName, panelX + 60, itemY + 8, 0xFFFFFF);

            String modelType = entry.isSlim() ? "Slim" : "Classic";
            context.drawTextWithShadow(this.textRenderer, modelType, panelX + 60, itemY + 20, 0xAAAAAAA);

            context.drawTextWithShadow(this.textRenderer, entry.getTimeAgo(), panelX + 60, itemY + 32, 0x888888);
        }

        if (maxHistoryScroll > 0) {
            int scrollbarHeight = Math.max(20, (panelHeight * panelHeight) / (history.size() * (HISTORY_ITEM_HEIGHT + HISTORY_PADDING)));
            int scrollbarY = panelY + 25 + (int)((panelHeight - 25 - scrollbarHeight) * ((float)historyScroll / maxHistoryScroll));
            context.fill(panelX + HISTORY_WIDTH - 8, scrollbarY, panelX + HISTORY_WIDTH - 5, scrollbarY + scrollbarHeight, 0xFF888888);
        }
    }

    private net.minecraft.client.texture.NativeImage convertToNativeImage(BufferedImage bufferedImage) {
        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        net.minecraft.client.texture.NativeImage nativeImage =
                new net.minecraft.client.texture.NativeImage(width, height, true);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = bufferedImage.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                int abgr = (alpha << 24) | (blue << 16) | (green << 8) | red;
                nativeImage.setColor(x, y, abgr);
            }
        }

        return nativeImage;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // Scroll in history panel
        if (mouseX >= 10 && mouseX <= 10 + HISTORY_WIDTH) {
            historyScroll = Math.max(0, Math.min(maxHistoryScroll, historyScroll - (int)(amount * 20)));
            return true;
        }
        
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastClickTime < DOUBLE_CLICK_DELAY) {
            return true;
        }
        lastClickTime = currentTime;

        // Check if clicking in model viewer for dragging
        int viewerX = HISTORY_WIDTH + 30;
        int viewerY = 40;
        if (button == 0 && mouseX >= viewerX && mouseX <= viewerX + MODEL_VIEWER_WIDTH &&
            mouseY >= viewerY && mouseY <= viewerY + MODEL_VIEWER_HEIGHT) {
            isDraggingModel = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        if (button == 0) {
            int panelX = 10;
            int panelY = 40;
            int panelHeight = this.height - 80;

            if (mouseX >= panelX && mouseX <= panelX + HISTORY_WIDTH &&
                    mouseY >= panelY + 25 && mouseY <= panelY + panelHeight) {

                List<SkinHistory.SkinEntry> history = SkinHistory.getHistory();
                int yOffset = panelY + 25 - historyScroll;

                for (int i = 0; i < history.size(); i++) {
                    SkinHistory.SkinEntry entry = history.get(i);
                    int itemY = yOffset + i * (HISTORY_ITEM_HEIGHT + HISTORY_PADDING);

                    if (mouseY >= itemY && mouseY <= itemY + HISTORY_ITEM_HEIGHT) {
                        loadFromHistory(entry);
                        return true;
                    }
                }
            }
        }
        else if (button == 1) {
            int panelX = 10;
            int panelY = 40;
            int panelHeight = this.height - 80;

            if (mouseX >= panelX && mouseX <= panelX + HISTORY_WIDTH &&
                    mouseY >= panelY + 25 && mouseY <= panelY + panelHeight) {

                List<SkinHistory.SkinEntry> history = SkinHistory.getHistory();
                int yOffset = panelY + 25 - historyScroll;

                for (int i = 0; i < history.size(); i++) {
                    SkinHistory.SkinEntry entry = history.get(i);
                    int itemY = yOffset + i * (HISTORY_ITEM_HEIGHT + HISTORY_PADDING);

                    if (mouseY >= itemY && mouseY <= itemY + HISTORY_ITEM_HEIGHT) {
                        openRenameDialog(entry);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingModel && button == 0) {
            // Rotate the model only horizontally (left and right)
            modelRotationY += (float)(mouseX - lastMouseX) * 0.5f;
            
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private void openRenameDialog(SkinHistory.SkinEntry entry) {
        client.setScreen(new SkinRenameScreen(this, entry));
    }

    private void loadFromHistory(SkinHistory.SkinEntry entry) {
        try {
            draggedFile = entry.getFile();
            isSlimModel = ModelPreferenceManager.isSlimPreference();
            applySkin(draggedFile);
            this.clearAndInit();
        } catch (Exception e) {
            statusMessage = "Failed to load from history!";
            statusColor = 0xFF0000;
            NightfallSkin.LOGGER.error("Failed to load from history", e);
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (this.textRenderer.getWidth(testLine) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                }
                currentLine = new StringBuilder(word);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDragging = false;
        isDraggingModel = false;

        if (!draggedFiles.isEmpty()) {
            File file = new File(draggedFiles.get(0));
            if (file.exists() && isValidSkinFile(file)) {
                draggedFile = file;
                statusMessage = "File loaded! Click 'Apply Skin' to use it.";
                statusColor = 0x00FF00;
            } else {
                statusMessage = "Invalid file! Must be PNG (64x64 to 512x512)";
                statusColor = 0xFF0000;
            }
            draggedFiles.clear();
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    public void onFilesDragged(List<String> paths) {
        isDragging = true;
        draggedFiles = new ArrayList<>(paths);
    }

    private boolean isValidSkinFile(File file) {
        try {
            if (!file.getName().toLowerCase().endsWith(".png")) {
                return false;
            }

            BufferedImage image = ImageIO.read(file);
            if (image == null) return false;

            int width = image.getWidth();
            int height = image.getHeight();

            if (width != height) return false;
            if (width < 64 || width > 512) return false;

            return width % 64 == 0;

        } catch (Exception e) {
            return false;
        }
    }

    private void applySkin(File skinFile) {
        try {
            BufferedImage skinImage = ImageIO.read(skinFile);
            if (skinImage == null) {
                statusMessage = "Failed to read image!";
                statusColor = 0xFF0000;
                return;
            }

            Identifier textureId = SkinManager.applySkin(client, skinImage, isSlimModel);

            SkinHistory.addSkin(skinFile, textureId, isSlimModel);

            // Play random sound effect
            playRandomSkinChangeSound();

            statusMessage = "Skin applied successfully! Model: " + (isSlimModel ? "Slim" : "Classic");
            statusColor = 0x00FF00;

            int visibleHeight = this.height - 80;
            int totalHistoryHeight = SkinHistory.getHistory().size() * (HISTORY_ITEM_HEIGHT + HISTORY_PADDING);
            maxHistoryScroll = Math.max(0, totalHistoryHeight - visibleHeight);

        } catch (Exception e) {
            statusMessage = "Error applying skin: " + e.getMessage();
            statusColor = 0xFF0000;
            NightfallSkin.LOGGER.error("Error applying skin", e);
        }
    }

    /**
     * Custom blur background renderer for Minecraft 1.20.1
     * Creates a uniform transparent background without vignette
     */
    private void renderCustomBlurBackground(DrawContext context) {
        // Fill with uniform darkened transparent background
        // You can adjust the alpha (0x60) for more/less darkness: 0x40 = lighter, 0x80 = darker
        context.fill(0, 0, this.width, this.height, 0x60101010);
    }

    @Override
    public void removed() {
        if (previousGuiScale != -1) {
            client.options.getGuiScale().setValue(previousGuiScale);
            client.onResolutionChanged();
            previousGuiScale = -1;
        }
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /**
     * Plays a random skin change sound effect
     */
    private void playRandomSkinChangeSound() {
        if (client.player != null && client.world != null) {
            // Randomly choose between the two sounds
            net.minecraft.sound.SoundEvent sound = Math.random() < 0.5 ?
                    ModSounds.SKIN_CHANGE_1 : ModSounds.SKIN_CHANGE_2;

            // Play the sound at the player's position with SoundCategory
            client.world.playSound(
                    client.player,  // Player (can be null for client-side)
                    client.player.getBlockPos(),  // Position
                    sound,  // Sound event
                    net.minecraft.sound.SoundCategory.PLAYERS,  // Category
                    0.5f,  // Volume
                    1.0f   // Pitch
            );
        }
    }
}
