package net.kendo.nightfall;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Registers the mod's sound events through Architectury's {@link DeferredRegister},
 * which defers registration to the correct moment on each loader.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(NightfallSkin.MOD_ID, RegistryKeys.SOUND_EVENT);

    public static final RegistrySupplier<SoundEvent> SKIN_CHANGE_1 =
            SOUNDS.register("updateskin_fun", () -> SoundEvent.of(new Identifier(NightfallSkin.MOD_ID, "updateskin_fun")));
    public static final RegistrySupplier<SoundEvent> SKIN_CHANGE_2 =
            SOUNDS.register("updateskinka", () -> SoundEvent.of(new Identifier(NightfallSkin.MOD_ID, "updateskinka")));

    private ModSounds() {
    }

    public static void initialize() {
        // Hooks the DeferredRegister into the platform's registry events.
        SOUNDS.register();
        NightfallSkin.LOGGER.info("Registering mod sounds");
    }
}
