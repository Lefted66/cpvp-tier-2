package com.cpvp.mixin;

import com.cpvp.CpvpClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin {

    @Inject(method = "tryUseTotem", at = @At("TAIL"))
    private void onTotemPop(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        CpvpClient.AUTO_TOTEM.onTotemPop(client);
    }
}
