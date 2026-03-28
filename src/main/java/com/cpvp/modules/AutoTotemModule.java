package com.cpvp.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.Random;

public class AutoTotemModule {

    private static final int  TOTEM_HOTBAR_INDEX = 8;
    private static final long BASE_DELAY_MS      = 50;
    private static final long JITTER_MAX_MS      = 20;
    private static final long MAX_TOTAL_MS       = 600;

    private boolean enabled        = false;
    private boolean running        = false;
    private boolean doublePop      = false;
    private boolean popQueued      = false;

    private int  step              = 0;
    private long stepStartTime     = 0;
    private long sequenceStartTime = 0;
    private long currentJitter     = 0;

    // Offhand tracking for totem pop detection (no mixin needed)
    private boolean prevOffhandWasTotem = false;

    private final Random random = new Random();

    public boolean isEnabled() { return enabled; }

    public void toggle() {
        enabled = !enabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[cPvP] AutoTotem: " + (enabled ? "§aON" : "§cOFF")),
                true
            );
        }
        if (!enabled) reset();
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null) return;

        // ── Totem pop detection via offhand polling ───────────────────────
        // A totem pop = offhand HAD a totem last tick, now it doesn't,
        // AND health is at or below 1 (the server sets it to 1 on pop).
        ClientPlayerEntity player = client.player;
        boolean offhandHasTotem = player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);

        if (prevOffhandWasTotem && !offhandHasTotem && player.getHealth() <= 1.0f) {
            onTotemPop(client);
        }
        prevOffhandWasTotem = offhandHasTotem;

        // ── Inventory open detection ──────────────────────────────────────
        boolean invOpen = isInventoryOpen(client);

        if (popQueued && invOpen && !running) {
            popQueued = false;
            startSequence(false);
        }

        if (!running && invOpen) {
            startSequence(false);
        }

        if (running) tickSequence(client);
    }

    public void onTotemPop(MinecraftClient client) {
        if (!enabled || client.player == null) return;

        ClientPlayerEntity player = client.player;
        boolean offhandEmpty = !player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean slot9Empty   = !player.getInventory().getStack(TOTEM_HOTBAR_INDEX)
                                      .isOf(Items.TOTEM_OF_UNDYING);
        doublePop = offhandEmpty && slot9Empty;

        if (running) return;

        if (isInventoryOpen(client)) {
            startSequence(false);
        } else {
            popQueued = true;
            startSequence(true);
        }
    }

    private void startSequence(boolean needsOpen) {
        running = true;
        step    = needsOpen ? 0 : 1;
        long now = System.currentTimeMillis();
        sequenceStartTime = now;
        stepStartTime     = now;
        currentJitter     = nextJitter();
    }

    private void tickSequence(MinecraftClient client) {
        long now = System.currentTimeMillis();

        if (now - sequenceStartTime > MAX_TOTAL_MS) {
            closeInventory(client);
            reset();
            return;
        }

        if (now - stepStartTime < BASE_DELAY_MS + currentJitter) return;

        ClientPlayerEntity player = client.player;
        if (player == null) { reset(); return; }

        switch (step) {

            case 0 -> {
                client.execute(() ->
                    client.setScreen(new InventoryScreen(player)));
                advance(now);
            }

            case 1 -> {
                if (doublePop) fillSlot9(client, player);
                else           fillOffhand(client, player);
                advance(now);
            }

            case 2 -> {
                if (doublePop) fillOffhand(client, player);
                else           fillSlot9(client, player);
                advance(now);
            }

            case 3 -> {
                closeInventory(client);
                reset();
            }
        }
    }

    private void fillOffhand(MinecraftClient client, ClientPlayerEntity player) {
        if (player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return;
        int slot = findTotem(player.getInventory(), -1);
        if (slot == -1) return;
        moveToOffhand(client, player, slot);
    }

    private void fillSlot9(MinecraftClient client, ClientPlayerEntity player) {
        if (player.getInventory().getStack(TOTEM_HOTBAR_INDEX).isOf(Items.TOTEM_OF_UNDYING)) return;
        int slot = findTotem(player.getInventory(), TOTEM_HOTBAR_INDEX);
        if (slot == -1 || slot == TOTEM_HOTBAR_INDEX) return;
        swapToHotbar(client, player, slot, TOTEM_HOTBAR_INDEX);
    }

    private void moveToOffhand(MinecraftClient client, ClientPlayerEntity player, int invSlot) {
        int syncId     = player.playerScreenHandler.syncId;
        int screenFrom = toScreenSlot(invSlot);
        int offhand    = 45;
        client.interactionManager.clickSlot(syncId, screenFrom, 0, SlotActionType.PICKUP, player);
        client.interactionManager.clickSlot(syncId, offhand,    0, SlotActionType.PICKUP, player);
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

    private boolean isInventoryOpen(MinecraftClient client) {
        return client.currentScreen instanceof InventoryScreen;
    }

    private void closeInventory(MinecraftClient client) {
        if (client.currentScreen != null) {
            client.execute(() -> client.setScreen(null));
        }
    }

    private void advance(long now) {
        step++;
        stepStartTime = now;
        currentJitter = nextJitter();
    }

    private long nextJitter() {
        return (long)(random.nextDouble() * JITTER_MAX_MS);
    }

    private void reset() {
        running   = false;
        doublePop = false;
        popQueued = false;
        step      = 0;
        stepStartTime     = 0;
        sequenceStartTime = 0;
        currentJitter     = 0;
    }
}
