package com.cpvp;

import com.cpvp.modules.AutoTotemModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
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
                GLFW.GLFW_KEY_H,
                CPVP_CATEGORY
        ));

        // Detect totem pop via network packet (status 35 = totem activated)
        // This works on all 1.21.x versions and requires no mixin at all.
        ClientPlayNetworking.registerGlobalReceiver(
                EntityStatusS2CPacket.class,
                (packet, context) -> {
                    // Status 35 is the totem of undying activation status
                    if (packet.getStatus() == 35) {
                        context.client().execute(() -> {
                            if (context.client().player != null
                                    && packet.getEntity(context.client().world)
                                       == context.client().player) {
                                AUTO_TOTEM.onTotemPop(context.client());
                            }
                        });
                    }
                }
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) AUTO_TOTEM.toggle();
            if (client.player == null || client.world == null) return;
            AUTO_TOTEM.onTick(client);
        });

        LOGGER.info("[cPvP Tier 2] Loaded.");
    }
}
