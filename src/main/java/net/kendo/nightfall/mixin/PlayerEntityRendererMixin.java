package net.kendo.nightfall.mixin;

import com.mojang.authlib.GameProfile;
import net.kendo.nightfall.SkinManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class PlayerEntityRendererMixin {

    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void onGetSkinTexture(CallbackInfoReturnable<Identifier> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        GameProfile profile = player.getGameProfile();

        // Check if this player has a custom skin
        Identifier customSkin = SkinManager.getCustomSkin(profile);
        if (customSkin != null) {
            cir.setReturnValue(customSkin);
            cir.cancel();
        }
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void onGetModel(CallbackInfoReturnable<String> cir) {
        AbstractClientPlayerEntity player = (AbstractClientPlayerEntity) (Object) this;
        GameProfile profile = player.getGameProfile();

        // Check if this player has a custom skin with a specific model
        Identifier customSkin = SkinManager.getCustomSkin(profile);
        if (customSkin != null) {
            boolean isSlim = SkinManager.isSlimModel(profile);
            cir.setReturnValue(isSlim ? "slim" : "default");
            cir.cancel();
        }
    }
}