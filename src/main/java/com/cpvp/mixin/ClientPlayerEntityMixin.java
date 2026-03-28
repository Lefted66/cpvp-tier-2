package com.cpvp.mixin;

import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;

// Mixin kept empty intentionally.
// Totem pop is detected via EntityStatusS2CPacket in CpvpClient instead.
@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {
}
