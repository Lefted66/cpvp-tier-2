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
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Safe Anchor — triggered on V key press.
 * Sequence:
 * 1. Switch to respawn anchor, place it where looking
 * 2. Switch to glowstone, place it on the FRONT face of the anchor
 *    (the face facing toward player) — charges it safely
 * 3. Switch to totem, look UP at anchor, detonate
 */
public class SafeAnchorModule {

    private static final long TICK_MS   = 50;
    private static final long JITTER_MS = 10;

    private boolean enabled       = false;
    private boolean triggered     = false;
    private boolean running       = false;
    private int     step          = 0;
    private long    stepStartTime = 0;

    private BlockPos       anchorPos    = null;
    private BlockHitResult placeHit     = null;
    private Direction      frontFace    = null; // face of anchor toward player

    private final Random random = new Random();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean val) {
        enabled = val;
        if (!enabled) reset();
    }

    public void trigger(MinecraftClient client) {
        if (!enabled || running || client.player == null || client.world == null) return;
        if (!(client.crosshairTarget instanceof BlockHitResult hit)) return;
        if (hit.getType() != HitResult.Type.BLOCK) return;

        // The anchor will be placed on the face of the block we're looking at
        BlockPos placed = hit.getBlockPos().offset(hit.getSide());
        anchorPos = placed;
        placeHit  = hit;

        // Front face = face of anchor closest to player = direction from anchor to player
        frontFace = getFrontFace(client.player, placed);

        triggered = true;
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null) return;

        if (triggered && !running) {
            triggered     = false;
            running       = true;
            step          = 0;
            stepStartTime = System.currentTimeMillis();
        }

        if (running) tickSequence(client);
    }

    private void tickSequence(MinecraftClient client) {
        long now   = System.currentTimeMillis();
        long delay = TICK_MS + (long)(random.nextDouble() * JITTER_MS);
        if (now - stepStartTime < delay) return;

        ClientPlayerEntity player = client.player;
        if (player == null || anchorPos == null) { reset(); return; }

        switch (step) {

            case 0 -> {
                // Switch to respawn anchor
                int anchorSlot = findInHotbar(player, Items.RESPAWN_ANCHOR);
                if (anchorSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = anchorSlot;
                advance(now);
            }

            case 1 -> {
                // Place the anchor
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, placeHit);
                advance(now);
            }

            case 2 -> {
                // Confirm anchor placed, switch to glowstone
                if (client.world == null
                        || !client.world.getBlockState(anchorPos).isOf(Blocks.RESPAWN_ANCHOR)) {
                    reset(); return;
                }
                int glowSlot = findInHotbar(player, Items.GLOWSTONE);
                if (glowSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = glowSlot;
                advance(now);
            }

            case 3 -> {
                // Place glowstone on the FRONT face of the anchor
                // Front face = face of the anchor that faces toward the player
                // This charges the anchor safely — no need to look up/down
                Vec3d frontCenter = Vec3d.ofCenter(anchorPos)
                        .add(Vec3d.of(frontFace.getVector()).multiply(0.5));

                BlockHitResult glowHit = new BlockHitResult(
                        frontCenter,
                        frontFace,
                        anchorPos,
                        false
                );
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, glowHit);
                advance(now);
            }

            case 4 -> {
                // Switch to totem
                int totemSlot = findInHotbar(player, Items.TOTEM_OF_UNDYING);
                if (totemSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = totemSlot;
                advance(now);
            }

            case 5 -> {
                // Look up slightly and detonate anchor
                player.setPitch(-30f); // look slightly up toward anchor

                BlockHitResult detonateHit = new BlockHitResult(
                        Vec3d.ofCenter(anchorPos).add(0, 0.5, 0),
                        Direction.UP,
                        anchorPos,
                        false
                );
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, detonateHit);
                reset();
            }
        }
    }

    /**
     * Returns the direction from the anchor block toward the player.
     * This is the "front face" — the face closest to the player.
     */
    private Direction getFrontFace(ClientPlayerEntity player, BlockPos anchor) {
        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        Vec3d diff      = playerPos.subtract(Vec3d.ofCenter(anchor));
        // Pick the horizontal axis with the greatest difference
        if (Math.abs(diff.x) >= Math.abs(diff.z)) {
            return diff.x > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return diff.z > 0 ? Direction.SOUTH : Direction.NORTH;
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
        triggered     = false;
        step          = 0;
        stepStartTime = 0;
        anchorPos     = null;
        placeHit      = null;
        frontFace     = null;
    }
}
