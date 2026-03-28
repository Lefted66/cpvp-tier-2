package com.cpvp.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.Random;

public class AutoTotemModule {

    private static final int  TOTEM_HOTBAR_INDEX = 8;
    private static final long TICK_MS            = 50;
    private static final long JITTER_MS          = 10;

    private boolean enabled             = false;
    private boolean running             = false;
    private boolean autoClose           = false;
    private boolean prevInvOpen         = false;
    private boolean prevOffhandWasTotem = false;
    private boolean prevSlot9WasTotem   = false;
    private boolean offhandPopped       = false;
    private boolean slot9Popped         = false;

    // Cooldown to prevent false triggers
    private long lastPopTime = 0;
    private static final long POP_COOLDOWN_MS = 1000;

    private int  step          = 0;
    private long stepStartTime = 0;

    private final Random random = new Random();

    public boolean isEnabled() { return enabled; }

    public void toggle() {
        enabled = !enabled;
        if (!enabled) reset();
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null) return;

        ClientPlayerEntity player = client.player;
        boolean invOpen           = client.currentScreen instanceof InventoryScreen;
        boolean offhandHasTotem   = player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean slot9HasTotem     = player.getInventory().getStack(TOTEM_HOTBAR_INDEX)
                                          .isOf(Items.TOTEM_OF_UNDYING);
        long now = System.currentTimeMillis();

        // ── Pop detection ─────────────────────────────────────────────────
        // Detect purely from totem disappearing — no health check needed
        // Cooldown prevents double-firing
        if (!running && (now - lastPopTime) > POP_COOLDOWN_MS) {
            boolean offhandJustPopped = prevOffhandWasTotem && !offhandHasTotem;
            boolean slot9JustPopped   = prevSlot9WasTotem   && !slot9HasTotem;

            if (offhandJustPopped || slot9JustPopped) {
                lastPopTime   = now;
                offhandPopped = offhandJustPopped;
                slot9Popped   = slot9JustPopped;
                onPop(client, player);
            }
        }

        prevOffhandWasTotem = offhandHasTotem;
        prevSlot9WasTotem   = slot9HasTotem;

        // ── Manual inventory open ─────────────────────────────────────────
        if (!prevInvOpen && invOpen && !running) {
            autoClose     = false;
            offhandPopped = false;
            slot9Popped   = false;
            startAt(1);
        }
        prevInvOpen = invOpen;

        if (running) tickSequence(client);
    }

    private void onPop(MinecraftClient client, ClientPlayerEntity player) {
        // Double hand: switch to slot 9 immediately via hotbar key press
        // Works without inventory open
        client.execute(() -> {
            if (client.gameRenderer != null && client.player != null) {
                // Send hotbar slot change packet directly
                client.player.networkHandler.sendPacket(
                    new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(
                        TOTEM_HOTBAR_INDEX
                    )
                );
            }
        });

        autoClose = true;
        startAt(0);
    }

    private void startAt(int startStep) {
        running       = true;
        step          = startStep;
        stepStartTime = System.currentTimeMillis();
    }

    private void tickSequence(MinecraftClient client) {
        long now   = System.currentTimeMillis();
        long delay = TICK_MS + (long)(random.nextDouble() * JITTER_MS);
        if (now - stepStartTime < delay) return;

        ClientPlayerEntity player = client.player;
        if (player == null) { reset(); return; }

        boolean offhandFull = player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean slot9Full   = player.getInventory().getStack(TOTEM_HOTBAR_INDEX)
                                    .isOf(Items.TOTEM_OF_UNDYING);

        switch (step) {

            case 0 -> {
                // Open inventory
                client.execute(() ->
                    client.setScreen(new InventoryScreen(player)));
                advance(now);
            }

            case 1 -> {
                // Double pop: fill slot 9 first
                if (slot9Popped && !slot9Full) {
                    int slot = findTotem(player.getInventory(), TOTEM_HOTBAR_INDEX);
                    if (slot != -1) swapToHotbar(client, player, slot, TOTEM_HOTBAR_INDEX);
                }
                advance(now);
            }

            case 2 -> {
                // Fill offhand if needed
                if (!offhandFull && (offhandPopped || (!offhandPopped && !slot9Popped))) {
                    int slot = findTotem(player.getInventory(), -1);
                    if (slot != -1) moveToOffhand(client, player, slot);
                }
                advance(now);
            }

            case 3 -> {
                // Fill slot 9 if offhand-only pop or manual open
                if (!slot9Full && !slot9Popped) {
                    int slot = findTotem(player.getInventory(), TOTEM_HOTBAR_INDEX);
                    if (slot != -1 && slot != TOTEM_HOTBAR_INDEX)
                        swapToHotbar(client, player, slot, TOTEM_HOTBAR_INDEX);
                }
                advance(now);
            }

            case 4 -> {
                if (autoClose) closeInventory(client);
                reset();
            }
        }
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void moveToOffhand(MinecraftClient client, ClientPlayerEntity player, int invSlot) {
        int syncId     = player.playerScreenHandler.syncId;
        int screenFrom = toScreenSlot(invSlot);
        client.interactionManager.clickSlot(syncId, screenFrom, 0, SlotActionType.PICKUP, player);
        client.interactionManager.clickSlot(syncId, 45,        0, SlotActionType.PICKUP, player);
        if (!player.playerScreenHandler.getCursorStack().isEmpty()) {
            client.interactionManager.clickSlot(syncId, screenFrom, 0, SlotActionType.PICKUP, player);
        }
    }

    private void swapToHotbar(MinecraftClient client, ClientPlayerEntity player,
                               int fromInvSlot, int toHotbarIndex) {
        int syncId     = player.playerScreenHandler.syncId;
        int screenFrom = toScreenSlot(fromInvSlot);
        client.interactionManager.clickSlot(
            syncId, screenFrom, toHotbarIndex, SlotActionType.SWAP, player);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private void closeInventory(MinecraftClient client) {
        if (client.currentScreen != null) {
            client.execute(() -> client.setScreen(null));
        }
    }

    private void advance(long now) {
        step++;
        stepStartTime = now;
    }

    private void reset() {
        running       = false;
        autoClose     = false;
        offhandPopped = false;
        slot9Popped   = false;
        step          = 0;
        stepStartTime = 0;
    }
}
