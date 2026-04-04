package com.cpvp.modules;

import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Instant Anchor — triggers on right click while looking at an uncharged anchor.
 * Sequence:
 * 1. Switch to glowstone, charge anchor once
 * 2. Switch to totem
 * 3. Right click anchor to detonate
 */
public class InstantAnchorModule {

    private static final long TICK_MS   = 50;
    private static final long JITTER_MS = 10;

    private boolean enabled       = false;
    private boolean running       = false;
    private int     step          = 0;
    private long    stepStartTime = 0;

    // Remember the anchor position and hit result
    private BlockHitResult savedHit = null;

    // Track right click to detect trigger
    private boolean prevUsePressed = false;

    private final Random random = new Random();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean val) {
        enabled = val;
        if (!enabled) reset();
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) return;

        ClientPlayerEntity player = client.player;
        boolean usePressed = client.options.useKey.isPressed();

        // Trigger on right click press (not hold)
        if (usePressed && !prevUsePressed && !running) {
            // Check we're looking at an uncharged respawn anchor
            if (client.crosshairTarget instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos pos   = hit.getBlockPos();
                var      state = client.world.getBlockState(pos);
                if (state.isOf(Blocks.RESPAWN_ANCHOR)) {
                    int charges = state.get(RespawnAnchorBlock.CHARGES);
                    if (charges == 0) {
                        savedHit = hit;
                        startSequence();
                    }
                }
            }
        }

        prevUsePressed = usePressed;

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
        if (player == null || savedHit == null) { reset(); return; }

        switch (step) {

            case 0 -> {
                // Switch to glowstone
                int glowSlot = findInHotbar(player, Items.GLOWSTONE);
                if (glowSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = glowSlot;
                advance(now);
            }

            case 1 -> {
                // Charge anchor once
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, savedHit);
                advance(now);
            }

            case 2 -> {
                // Switch to totem
                int totemSlot = findInHotbar(player, Items.TOTEM_OF_UNDYING);
                if (totemSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = totemSlot;
                advance(now);
            }

            case 3 -> {
                // Detonate
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, savedHit);
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
        savedHit      = null;
    }
}
