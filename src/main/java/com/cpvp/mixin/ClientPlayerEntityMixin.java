package com.cpvp.mixin;

import com.cpvp.CpvpClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class ClientPlayerEntityMixin {

    /**
     * Hooks into LivingEntity#damage.
     * We detect a totem pop by checking: damage would have killed us,
     * but we survived with 1 HP — meaning a totem fired.
     * We check BEFORE the damage is applied (HEAD) to record health,
     * then check AFTER (TAIL) to see if health is exactly 1 (totem saved us).
     */
    @Inject(method = "damage", at = @At("TAIL"))
    private void onDamagePost(DamageSource source, float amount,
                               CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Only care about our own player
        LivingEntity self = (LivingEntity)(Object)this;
        if (self != client.player) return;

        // Totem pop leaves health at exactly 1.0
        // and the offhand totem gets consumed (becomes air)
        ClientPlayerEntity player = client.player;
        boolean offhandWasTotem = !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean healthIsOne = Math.abs(player.getHealth() - 1.0f) < 0.01f;

        if (healthIsOne && offhandWasTotem) {
            CpvpClient.AUTO_TOTEM.onTotemPop(client);
        }
    }
}
