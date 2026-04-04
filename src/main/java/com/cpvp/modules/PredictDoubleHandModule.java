package com.cpvp.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

/**
 * Predict Double Hand — watches nearby opponents and predicts when
 * they are about to pop their totem. When a pop is predicted,
 * switches the local player to slot 9 as a defensive double hand.
 *
 * Detection heuristics:
 * - Opponent health drops to near-lethal levels (≤ 4 HP)
 * - Opponent is holding a crystal or sword (aggressive context)
 * - Opponent offhand has a totem (they are at risk)
 */
public class PredictDoubleHandModule {

    private static final int    TOTEM_HOTBAR_INDEX  = 8;
    private static final float  LETHAL_HEALTH        = 4.0f;
    private static final double WATCH_RANGE          = 8.0;
    private static final long   PREDICTION_COOLDOWN  = 3000; // ms between predictions

    private boolean enabled         = false;
    private long    lastPredictTime = 0;

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean val) {
        enabled = val;
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) return;

        long now = System.currentTimeMillis();
        if (now - lastPredictTime < PREDICTION_COOLDOWN) return;

        ClientPlayerEntity self  = client.player;
        ClientWorld        world = client.world;

        // Scan nearby players
        for (PlayerEntity opponent : world.getPlayers()) {
            if (opponent == self) continue;
            if (opponent.distanceTo(self) > WATCH_RANGE) continue;
            if (opponent.isDead()) continue;

            boolean opponentHasTotem = opponent.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)
                    || inventoryHasTotem(opponent);

            boolean opponentLowHealth = opponent.getHealth() <= LETHAL_HEALTH;

            boolean opponentAggressive =
                    opponent.getMainHandStack().isOf(Items.END_CRYSTAL)
                    || opponent.getMainHandStack().getItem().toString().contains("sword")
                    || opponent.getMainHandStack().getItem().toString().contains("axe");

            // Predict: opponent is low, has totem, and is in combat
            if (opponentHasTotem && opponentLowHealth && opponentAggressive) {
                // They are about to pop — switch our hotbar to slot 9 defensively
                self.getInventory().selectedSlot = TOTEM_HOTBAR_INDEX;
                lastPredictTime = now;

                // Notify player
                self.sendMessage(
                    net.minecraft.text.Text.literal("§c[Predict] §fOpponent near pop — double handed"),
                    true
                );
                break;
            }
        }
    }

    private boolean inventoryHasTotem(PlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            if (player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return true;
        }
        return false;
    }
}
