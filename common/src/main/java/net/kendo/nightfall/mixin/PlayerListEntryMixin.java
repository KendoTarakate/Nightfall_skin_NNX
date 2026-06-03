package net.kendo.nightfall.mixin;

import com.mojang.authlib.GameProfile;
import net.kendo.nightfall.SkinManager;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerListEntry.class)
public abstract class PlayerListEntryMixin {

    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getSkinTexture", at = @At("HEAD"), cancellable = true)
    private void onGetSkinTexture(CallbackInfoReturnable<Identifier> cir) {
        GameProfile profile = this.getProfile();

        // Check if this player has a custom skin
        Identifier customSkin = SkinManager.getCustomSkin(profile);
        if (customSkin != null) {
            cir.setReturnValue(customSkin);
            cir.cancel();
        }
    }

    @Inject(method = "getModel", at = @At("HEAD"), cancellable = true)
    private void onGetModel(CallbackInfoReturnable<String> cir) {
        GameProfile profile = this.getProfile();

        // Check if this player has a custom skin with a specific model
        Identifier customSkin = SkinManager.getCustomSkin(profile);
        if (customSkin != null) {
            boolean isSlim = SkinManager.isSlimModel(profile);
            cir.setReturnValue(isSlim ? "slim" : "default");
            cir.cancel();
        }
    }
}