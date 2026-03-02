package net.kendo.nightfall.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractClientPlayerEntity.class)
public interface AbstractClientPlayerEntityAccessor {

    @Accessor("playerListEntry")
    PlayerListEntry getPlayerListEntryAccessor();

    @Accessor("playerListEntry")
    void setPlayerListEntryAccessor(PlayerListEntry entry);
}