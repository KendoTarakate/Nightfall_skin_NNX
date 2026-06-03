package net.kendo.nightfall.forge;

import dev.architectury.platform.forge.EventBuses;
import net.kendo.nightfall.NightfallSkin;
import net.kendo.nightfall.client.NightfallSkinClient;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod(NightfallSkin.MOD_ID)
public final class NightfallSkinForge {
    public NightfallSkinForge() {
        // Let Architectury API register our content (e.g. DeferredRegister) on the right time.
        EventBuses.registerModEventBus(NightfallSkin.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        NightfallSkin.init();

        // Client-only initialization (keybinding, client packet receivers, client events).
        if (FMLEnvironment.dist.isClient()) {
            NightfallSkinClient.initClient();
        }
    }
}
