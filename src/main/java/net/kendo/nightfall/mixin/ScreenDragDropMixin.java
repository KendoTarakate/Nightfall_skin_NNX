package net.kendo.nightfall.mixin;

import net.kendo.nightfall.SkinChangerScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWDropCallback;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(MinecraftClient.class)
public class ScreenDragDropMixin {
    private GLFWDropCallback previousCallback;

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;

        if (screen instanceof net.kendo.nightfall.SkinChangerScreen) {
            // Set up drag and drop callback
            if (client.getWindow() != null) {
                long windowHandle = client.getWindow().getHandle();

                // Store previous callback
                previousCallback = GLFW.glfwSetDropCallback(windowHandle, null);

                // Set new callback
                GLFW.glfwSetDropCallback(windowHandle, (window, count, names) -> {
                    List<String> paths = new ArrayList<>();
                    for (int i = 0; i < count; i++) {
                        paths.add(GLFWDropCallback.getName(names, i));
                    }

                    // Pass to screen
                    if (client.currentScreen instanceof net.kendo.nightfall.SkinChangerScreen) {
                        ((net.kendo.nightfall.SkinChangerScreen) client.currentScreen).onFilesDragged(paths);
                    }
                });
            }
        } else if (previousCallback != null) {
            // Restore previous callback when leaving the screen
            if (client.getWindow() != null) {
                long windowHandle = client.getWindow().getHandle();
                GLFW.glfwSetDropCallback(windowHandle, previousCallback);
                previousCallback = null;
            }
        }
    }
}