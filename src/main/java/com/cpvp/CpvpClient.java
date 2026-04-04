package com.cpvp;

import com.cpvp.modules.AutoTotemModule;
import com.cpvp.modules.InstantAnchorModule;
import com.cpvp.modules.SafeAnchorModule;
import com.cpvp.modules.PredictDoubleHandModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CpvpClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("cpvptier2");

    // Modules
    public static final AutoTotemModule        AUTO_TOTEM     = new AutoTotemModule();
    public static final InstantAnchorModule    INSTANT_ANCHOR = new InstantAnchorModule();
    public static final SafeAnchorModule       SAFE_ANCHOR    = new SafeAnchorModule();
    public static final PredictDoubleHandModule PREDICT       = new PredictDoubleHandModule();

    // Keybinds
    public static KeyBinding toggleKey;        // Y — autototem
    public static KeyBinding safeAnchorKey;    // V — safe anchor
    public static KeyBinding masterToggleKey;  // B — toggle all modules

    private static final KeyBinding.Category CPVP_CATEGORY =
            KeyBinding.Category.create(Identifier.of("cpvptier2", "main"));

    // Master toggle state
    private static boolean allEnabled = false;

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cpvptier2.autototem",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_Y,
                CPVP_CATEGORY
        ));

        safeAnchorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cpvptier2.safeanchor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CPVP_CATEGORY
        ));

        masterToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cpvptier2.master",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                CPVP_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Master toggle — B
            while (masterToggleKey.wasPressed()) {
                allEnabled = !allEnabled;
                if (allEnabled) {
                    AUTO_TOTEM.setEnabled(true);
                    INSTANT_ANCHOR.setEnabled(true);
                    SAFE_ANCHOR.setEnabled(true);
                    PREDICT.setEnabled(true);
                } else {
                    AUTO_TOTEM.setEnabled(false);
                    INSTANT_ANCHOR.setEnabled(false);
                    SAFE_ANCHOR.setEnabled(false);
                    PREDICT.setEnabled(false);
                }
                client.player.sendMessage(
                    Text.literal("§6cPvP Tier 2 §r" + (allEnabled ? "§aEnabled" : "§cDisabled")),
                    true
                );
            }

            // AutoTotem individual toggle — Y
            while (toggleKey.wasPressed()) {
                AUTO_TOTEM.toggle();
                client.player.sendMessage(
                    Text.literal("§6AutoTotem §r" + (AUTO_TOTEM.isEnabled() ? "§aEnabled" : "§cDisabled")),
                    true
                );
            }

            // Safe anchor — V (hold to trigger)
            while (safeAnchorKey.wasPressed()) {
                SAFE_ANCHOR.trigger(client);
            }

            // Tick all modules
            AUTO_TOTEM.onTick(client);
            INSTANT_ANCHOR.onTick(client);
            SAFE_ANCHOR.onTick(client);
            PREDICT.onTick(client);
        });

        LOGGER.info("[cPvP Tier 2] Loaded. B = master toggle, Y = autototem, V = safe anchor.");
    }
}
