package com.cpvp;

import com.cpvp.modules.AutoTotemModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CpvpClient implements ClientModInitializer {

    public static final Logger LOGGER = LoggerFactory.getLogger("cpvptier2");
    public static final AutoTotemModule AUTO_TOTEM = new AutoTotemModule();
    public static KeyBinding toggleKey;

    private static final KeyBinding.Category CPVP_CATEGORY =
            KeyBinding.Category.create(Identifier.of("cpvptier2", "main"));

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.cpvptier2.autototem",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                CPVP_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) {
                LOGGER.info("[cPvP Tier 2] K pressed, toggling AutoTotem");
                AUTO_TOTEM.toggle();
                // Send as chat message too, not just action bar
                if (client.player != null) {
                    client.player.sendMessage(
                        Text.literal("[cPvP] AutoTotem: "
                            + (AUTO_TOTEM.isEnabled() ? "§aON" : "§cOFF")),
                        false
                    );
                }
            }
            if (client.player == null || client.world == null) return;
            AUTO_TOTEM.onTick(client);
        });

        LOGGER.info("[cPvP Tier 2] Loaded. Toggle key: K");
    }
}
