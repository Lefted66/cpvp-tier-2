package com.cpvp.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

public class AutoTotemModule {

    private static final int  TOTEM_HOTBAR_INDEX = 8;

    private boolean enabled            = false;
    private boolean prevInvOpen        = false;
    private boolean prevOffhandWasTotem = false;

    public boolean isEnabled() { return enabled; }

    public void toggle() {
        enabled = !enabled;
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null) return;

        ClientPlayerEntity player = client.player;
        boolean invOpen = client.currentScreen instanceof InventoryScreen;

        // ── Totem pop detection (offhand just disappeared + health at 1) ──
        boolean offhandHasTotem = player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean justPopped = prevOffhandWasTotem && !offhandHasTotem && player.getHealth() <= 1.0f;
        prevOffhandWasTotem = offhandHasTotem;

        // ── Fill slot 9 when inventory opens OR on pop ────────────────────
        // On pop: fill slot 9 immediately (inventory not needed)
        // On manual open: fill slot 9 on the tick the inventory opens
        boolean shouldFill = justPopped || (!prevInvOpen && invOpen);

        if (shouldFill) {
            fillSlot9(client, player);
        }

        prevInvOpen = invOpen;
    }

    private void fillSlot9(MinecraftClient client, ClientPlayerEntity player) {
        // Already has a totem in slot 9 — nothing to do
        if (player.getInventory().getStack(TOTEM_HOTBAR_INDEX).isOf(Items.TOTEM_OF_UNDYING)) return;

        // Find a totem anywhere in main inventory (excluding slot 9 itself)
        int slot = findTotem(player.getInventory(), TOTEM_HOTBAR_INDEX);
        if (slot == -1) return;

        swapToHotbar(client, player, slot, TOTEM_HOTBAR_INDEX);
    }

    private void swapToHotbar(MinecraftClient client, ClientPlayerEntity player,
                               int fromInvSlot, int toHotbarIndex) {
        int syncId     = player.playerScreenHandler.syncId;
        int screenFrom = toScreenSlot(fromInvSlot);
        client.interactionManager.clickSlot(
            syncId, screenFrom, toHotbarIndex, SlotActionType.SWAP, player);
    }

    private int findTotem(PlayerInventory inv, int excludeSlot) {
        for (int i = 0; i < 36; i++) {
            if (i == excludeSlot) continue;
            if (inv.getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private static int toScreenSlot(int invSlot) {
        return invSlot < 9 ? invSlot + 36 : invSlot;
    }
}
