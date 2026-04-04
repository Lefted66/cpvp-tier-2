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
 * Safe Anchor — triggered on V key press while looking at an uncharged anchor.
 * Sequence:
 * 1. Switch to glowstone
 * 2. Look at the SIDE of the anchor (not top) and charge it once
 * 3. Switch to totem
 * 4. Look back at anchor top face and detonate
 *
 * Placing glowstone on the side means the explosion shoots sideways,
 * not up toward the player — safe detonation.
 */
public class SafeAnchorModule {

    private static final long TICK_MS   = 50;
    private static final long JITTER_MS = 10;

    private boolean enabled       = false;
    private boolean triggered     = false;
    private boolean running       = false;
    private int     step          = 0;
    private long    stepStartTime = 0;

    private BlockPos        anchorPos   = null;
    private BlockHitResult  anchorHit   = null;
    private Direction       safeDir     = null;

    private final Random random = new Random();

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean val) {
        enabled = val;
        if (!enabled) reset();
    }

    public void trigger(MinecraftClient client) {
        if (!enabled || running || client.player == null || client.world == null) return;

        // Must be looking at an uncharged respawn anchor
        if (!(client.crosshairTarget instanceof BlockHitResult hit)) return;
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos   = hit.getBlockPos();
        var      state = client.world.getBlockState(pos);
        if (!state.isOf(Blocks.RESPAWN_ANCHOR)) return;

        int charges = state.get(RespawnAnchorBlock.CHARGES);
        if (charges != 0) return; // already charged

        anchorPos = pos;
        anchorHit = hit;
        safeDir   = getSafeDirection(client.player);
        triggered = true;
    }

    public void onTick(MinecraftClient client) {
        if (!enabled || client.player == null) return;

        if (triggered && !running) {
            triggered = false;
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
                // Switch to glowstone
                int glowSlot = findInHotbar(player, Items.GLOWSTONE);
                if (glowSlot == -1) { reset(); return; }
                player.getInventory().selectedSlot = glowSlot;
                advance(now);
            }

            case 1 -> {
                // Look at the SIDE face of the anchor and charge it
                // The glowstone goes on the side, explosion goes sideways not up
                Vec3d sideTarget = Vec3d.ofCenter(anchorPos)
                        .add(Vec3d.of(safeDir.getVector()).multiply(0.5));
                lookAt(player, sideTarget);

                // Build a hit result targeting the side face of the anchor
                BlockHitResult sideHit = new BlockHitResult(
                        sideTarget,
                        safeDir,
                        anchorPos,
                        false
                );
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, sideHit);
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
                // Look back at the top of the anchor and detonate
                Vec3d topTarget = Vec3d.ofCenter(anchorPos).add(0, 0.5, 0);
                lookAt(player, topTarget);

                BlockHitResult topHit = new BlockHitResult(
                        topTarget,
                        Direction.UP,
                        anchorPos,
                        false
                );
                client.interactionManager.interactBlock(player, Hand.MAIN_HAND, topHit);
                reset();
            }
        }
    }

    /**
     * Returns a horizontal direction perpendicular to the player's facing,
     * so the glowstone goes to the side, not toward or away from the player.
     */
    private Direction getSafeDirection(ClientPlayerEntity player) {
        float yaw = ((player.getYaw() % 360) + 360) % 360;
        // Return a direction perpendicular to player facing
        if (yaw < 45 || yaw >= 315 || (yaw >= 135 && yaw < 225)) {
            return Direction.EAST;
        } else {
            return Direction.NORTH;
        }
    }

    private void lookAt(ClientPlayerEntity player, Vec3d target) {
        Vec3d  diff  = target.subtract(player.getEyePos());
        double dist  = diff.horizontalLength();
        float  yaw   = (float)(Math.toDegrees(Math.atan2(-diff.x, diff.z)));
        float  pitch = (float)(Math.toDegrees(-Math.atan2(diff.y, dist)));
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
        anchorHit     = null;
        safeDir       = null;
    }
}
