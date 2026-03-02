package net.kendo.nightfall;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    // Register the two sound events
    public static final SoundEvent SKIN_CHANGE_1 = registerSound("updateskin_fun");
    public static final SoundEvent SKIN_CHANGE_2 = registerSound("updateskinka");

    private static SoundEvent registerSound(String name) {
        Identifier id = new Identifier(NightfallSkin.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void initialize() {
        // This method is called to ensure the class is loaded and sounds are registered
        NightfallSkin.LOGGER.info("Registering mod sounds");
    }
}
