package com.cpvp.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.Random;

/**
 * Instant Anchor — on right click while holding a respawn anchor:
 * 1. Charge it once with glowstone
 * 2. Switch to totem
 * 3. Detonate
 */
public class InstantAnchorModule {

    private static final long TICK_MS   = 50;
    private static final long JITTER_MS = 10;

    private boolean enabled       = false;
    private boolean running       = false;
    private int     step          = 0;
    private long    stepStartTime = 0;

    private final Random random = new Random();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean val) {
        enabled = val;
        if (!enabled) reset();
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null) return;

        ClientPlayerEntity player = client.player;

        // Trigger: right clicking while holding respawn anchor
        if (!running && player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
            if (client.options.useKey.isPressed()) {
                // Make sure we're looking at an uncharged anchor
                if (client.crosshairTarget instanceof BlockHitResult hit) {
                    if (client.world != null) {
                        var state = client.world.getBlockState(hit.getBlockPos());
                        if (state.isOf(net.minecraft.block.Blocks.RESPAWN_ANCHOR)) {
                            int charges = state.get(net.minecraft.block.RespawnAnchorBlock.CHARGES);
                            if (charges == 0) {
                                startSequence();
                            }
                        }
                    }
                }
            }
        }

        if (running) tickSequence(client);
    }

    private void startSequence() {
        running       = true;
        step          = 0;
        stepStartTime = System.currentTimeMillis();
    }

    private void tickSequence(MinecraftClient client) {
        long now   = System.currentTimeMillis();
        long delay = TICK_MS + (long)(random.nextDouble() * JITTER_MS);
        if (now - stepStartTime < delay) return;

        ClientPlayerEntity player = client.player;
        if (player == null) { reset(); return; }

        if (!(client.crosshairTarget instanceof BlockHitResult hit)) { reset(); return; }

        switch (step) {

            case 0 -> {
                // Switch to glowstone and charge
                int glowSlot = findInHotbar(player, Items.GLOWSTONE);
                if (glowSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = glowSlot;
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                advance(now);
            }

            case 1 -> {
                // Switch to totem
                int totemSlot = findInHotbar(player, Items.TOTEM_OF_UNDYING);
                if (totemSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = totemSlot;
                advance(now);
            }

            case 2 -> {
                // Detonate
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                reset();
            }
        }
    }

    private int findInHotbar(ClientPlayerEntity player, net.minecraft.item.Item item) {
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getStack(i).isOf(item)) return i;
        }
        return -1;
    }

    private void advance(long now) {
        step++;
        stepStartTime = now;
    }

    private void reset() {
        running       = false;
        step          = 0;
        stepStartTime = 0;
    }
}
