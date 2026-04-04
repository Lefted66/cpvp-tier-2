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
 * Instant Anchor — triggers when a respawn anchor is placed.
 * Sequence:
 * 1. Switch to glowstone, charge anchor once
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

    private BlockPos       anchorPos = null;
    private BlockHitResult anchorHit = null;

    // Track when anchor is placed
    private boolean prevHoldingAnchor = false;
    private boolean prevUsePressed    = false;

    private final Random random = new Random();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean val) {
        enabled = val;
        if (!enabled) reset();
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null || client.world == null) return;

        ClientPlayerEntity player      = client.player;
        boolean            holdingAnchor = player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR);
        boolean            usePressed    = client.options.useKey.isPressed();

        // Detect: player just placed an anchor (was holding anchor + right clicked)
        if (!running && holdingAnchor && usePressed && !prevUsePressed) {
            if (client.crosshairTarget instanceof BlockHitResult hit
                    && hit.getType() == HitResult.Type.BLOCK) {
                // The anchor will be placed on the face of the targeted block
                BlockPos placedPos = hit.getBlockPos().offset(hit.getSide());

                // Schedule a check next tick to confirm anchor was placed
                anchorPos = placedPos;
                anchorHit = new BlockHitResult(
                    hit.getPos(),
                    hit.getSide().getOpposite(),
                    placedPos,
                    false
                );
                // Start sequence on next tick so block exists in world
                running       = true;
                step          = -1; // wait one tick for block to exist
                stepStartTime = System.currentTimeMillis();
            }
        }

        prevHoldingAnchor = holdingAnchor;
        prevUsePressed    = usePressed;

        if (running) tickSequence(client);
    }

    private void tickSequence(MinecraftClient client) {
        long now   = System.currentTimeMillis();
        long delay = TICK_MS + (long)(random.nextDouble() * JITTER_MS);
        if (now - stepStartTime < delay) return;

        ClientPlayerEntity player = client.player;
        if (player == null || anchorPos == null) { reset(); return; }

        switch (step) {

            case -1 -> {
                // Wait one tick, confirm anchor exists in world
                if (client.world == null
                        || !client.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
                    reset();
                    return;
                }
                // Build correct hit result now that block exists
                anchorHit = new BlockHitResult(
                    net.minecraft.util.math.Vec3d.ofCenter(anchorPos),
                    net.minecraft.util.math.Direction.UP,
                    anchorPos,
                    false
                );
                advance(now);
            }

            case 0 -> {
                // Switch to glowstone
                int glowSlot = findInHotbar(player, Items.GLOWSTONE);
                if (glowSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = glowSlot;
                advance(now);
            }

            case 1 -> {
                // Charge anchor once
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, anchorHit);
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
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, anchorHit);
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
        anchorPos     = null;
        anchorHit     = null;
    }
}
