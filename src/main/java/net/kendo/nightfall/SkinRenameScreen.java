package net.kendo.nightfall;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class SkinRenameScreen extends Screen {
    private final Screen parent;
    private final SkinHistory.SkinEntry entry;
    private TextFieldWidget nameField;
    private boolean confirmingDelete = false;

    public SkinRenameScreen(Screen parent, SkinHistory.SkinEntry entry) {
        super(Text.literal("Rename Skin"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (!confirmingDelete) {
            // Normal rename mode
            // Name input field
            nameField = new TextFieldWidget(
                    this.textRenderer,
                    centerX - 100,
                    centerY - 10,
                    200,
                    20,
                    Text.literal("Name")
            );

            String currentName = entry.getCustomName() != null ? entry.getCustomName() : entry.getFileName();
            nameField.setText(currentName);
            nameField.setMaxLength(50);
            nameField.setFocused(true);

            this.addSelectableChild(nameField);
            this.setInitialFocus(nameField);

            // Save button
            this.addDrawableChild(ButtonWidget.builder(Text.literal("§aSave"), button -> {
                String newName = nameField.getText().trim();
                if (!newName.isEmpty()) {
                    entry.setCustomName(newName);
                }
                this.close();
            }).dimensions(centerX - 102, centerY + 25, 100, 20).build());

            // Cancel button
            this.addDrawableChild(ButtonWidget.builder(Text.literal("§7Cancel"), button -> {
                this.close();
            }).dimensions(centerX + 2, centerY + 25, 100, 20).build());

            // Delete button (bottom)
            this.addDrawableChild(ButtonWidget.builder(Text.literal("§cDelete Skin"), button -> {
                confirmingDelete = true;
                this.clearAndInit();
            }).dimensions(centerX - 100, centerY + 55, 200, 20).build());

        } else {
            // Confirmation mode
            // Confirm Delete button
            this.addDrawableChild(ButtonWidget.builder(Text.literal("§c§lConfirm Delete"), button -> {
                SkinHistory.removeSkin(entry);
                this.close();
            }).dimensions(centerX - 102, centerY + 10, 100, 20).build());

            // Cancel button
            this.addDrawableChild(ButtonWidget.builder(Text.literal("§aCancel"), button -> {
                confirmingDelete = false;
                this.clearAndInit();
            }).dimensions(centerX + 2, centerY + 10, 100, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        if (!confirmingDelete) {
            // Normal rename panel
            context.fill(centerX - 120, centerY - 50, centerX + 120, centerY + 90, 0xCC000000);

            // Border
            context.fill(centerX - 120, centerY - 50, centerX + 120, centerY - 48, 0xFF888888);
            context.fill(centerX - 120, centerY + 88, centerX + 120, centerY + 90, 0xFF888888);
            context.fill(centerX - 120, centerY - 50, centerX - 118, centerY + 90, 0xFF888888);
            context.fill(centerX + 118, centerY - 50, centerX + 120, centerY + 90, 0xFF888888);

            // Title
            context.drawCenteredTextWithShadow(this.textRenderer, "§lRename Skin", centerX, centerY - 40, 0xFFFFFF);

            // Instruction
            context.drawCenteredTextWithShadow(this.textRenderer, "§7Enter a new name:", centerX, centerY - 25, 0xAAAAAA);

            // Render text field
            if (nameField != null) {
                nameField.render(context, mouseX, mouseY, delta);
            }

        } else {
            // Confirmation panel
            context.fill(centerX - 120, centerY - 40, centerX + 120, centerY + 45, 0xCC000000);

            // Border (red for danger)
            context.fill(centerX - 120, centerY - 40, centerX + 120, centerY - 38, 0xFFFF0000);
            context.fill(centerX - 120, centerY + 43, centerX + 120, centerY + 45, 0xFFFF0000);
            context.fill(centerX - 120, centerY - 40, centerX - 118, centerY + 45, 0xFFFF0000);
            context.fill(centerX + 118, centerY - 40, centerX + 120, centerY + 45, 0xFFFF0000);

            // Warning title
            context.drawCenteredTextWithShadow(this.textRenderer, "§c§lDelete Skin?", centerX, centerY - 30, 0xFF0000);

            // Warning text
            String skinName = entry.getDisplayName();
            if (this.textRenderer.getWidth(skinName) > 220) {
                skinName = skinName.substring(0, 20) + "...";
            }
            context.drawCenteredTextWithShadow(this.textRenderer, "§7Delete: §f" + skinName, centerX, centerY - 10, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, "§7This cannot be undone!", centerX, centerY + 2, 0xFFAAAA);
        }
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}