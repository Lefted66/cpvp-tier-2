package com.cpvp.modules;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Safe Anchor — on V key press:
 * 1. Switch to respawn anchor, place it
 * 2. Switch to glowstone, aim to side of anchor, charge once
 * 3. Switch to totem, look back at anchor, detonate
 *
 * The glowstone is placed on the side of the anchor so the explosion
 * goes away from the player, not toward them.
 */
public class SafeAnchorModule {

    private static final long TICK_MS   = 50;
    private static final long JITTER_MS = 10;

    private boolean enabled       = false;
    private boolean triggered     = false;
    private boolean running       = false;
    private int     step          = 0;
    private long    stepStartTime = 0;

    // Remember where we placed the anchor
    private BlockPos anchorPos = null;

    private final Random random = new Random();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean val) {
        enabled = val;
        if (!enabled) reset();
    }

    public void trigger(MinecraftClient client) {
        if (!enabled || running) return;
        triggered = true;
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null) return;

        if (triggered && !running) {
            triggered = false;
            startSequence(client);
        }

        if (running) tickSequence(client);
    }

    private void startSequence(MinecraftClient client) {
        if (!(client.crosshairTarget instanceof BlockHitResult hit)) return;

        // Place anchor at the block face we're looking at
        anchorPos = hit.getBlockPos().offset(hit.getSide());

        running       = true;
        step          = 0;
        stepStartTime = System.currentTimeMillis();
    }

    private void tickSequence(MinecraftClient client) {
        long now   = System.currentTimeMillis();
        long delay = TICK_MS + (long)(random.nextDouble() * JITTER_MS);
        if (now - stepStartTime < delay) return;

        ClientPlayerEntity player = client.player;
        if (player == null || anchorPos == null) { reset(); return; }

        switch (step) {

            case 0 -> {
                // Switch to anchor and place it
                int anchorSlot = findInHotbar(player, Items.RESPAWN_ANCHOR);
                if (anchorSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = anchorSlot;

                if (!(client.crosshairTarget instanceof BlockHitResult hit)) { reset(); return; }
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, hit);
                advance(now);
            }

            case 1 -> {
                // Switch to glowstone
                int glowSlot = findInHotbar(player, Items.GLOWSTONE);
                if (glowSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = glowSlot;
                advance(now);
            }

            case 2 -> {
                // Aim at the SIDE of the anchor and place glowstone there
                // Pick a horizontal direction perpendicular to player facing
                Direction sideDir = getSafeDirection(player);
                BlockPos glowPos  = anchorPos.offset(sideDir);

                // Look at the side face of the anchor
                Vec3d lookTarget = Vec3d.ofCenter(anchorPos)
                    .add(Vec3d.of(sideDir.getVector()).multiply(0.5));
                lookAt(client, player, lookTarget);

                // Place glowstone on the side of the anchor
                BlockHitResult glowHit = new BlockHitResult(
                    lookTarget,
                    sideDir.getOpposite(),
                    anchorPos,
                    false
                );
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, glowHit);
                advance(now);
            }

            case 3 -> {
                // Switch to totem
                int totemSlot = findInHotbar(player, Items.TOTEM_OF_UNDYING);
                if (totemSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = totemSlot;
                advance(now);
            }

            case 4 -> {
                // Look back at anchor and detonate
                Vec3d anchorCenter = Vec3d.ofCenter(anchorPos);
                lookAt(client, player, anchorCenter);

                BlockHitResult detonateHit = new BlockHitResult(
                    anchorCenter,
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
     * Returns a horizontal direction that is to the SIDE of the player,
     * not in front or behind — so the explosion goes sideways.
     */
    private Direction getSafeDirection(ClientPlayerEntity player) {
        float yaw = player.getYaw();
        // Perpendicular to facing direction
        Direction facing = Direction.fromRotation(yaw);
        return switch (facing) {
            case NORTH, SOUTH -> Direction.EAST;
            default           -> Direction.NORTH;
        };
    }

    private void lookAt(MinecraftClient client, ClientPlayerEntity player, Vec3d target) {
        Vec3d diff  = target.subtract(player.getEyePos());
        double dist = diff.horizontalLength();
        float yaw   = (float)(Math.toDegrees(Math.atan2(-diff.x, diff.z)));
        float pitch = (float)(Math.toDegrees(-Math.atan2(diff.y, dist)));
        player.setYaw(yaw);
        player.setPitch(pitch);
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
    }
}
