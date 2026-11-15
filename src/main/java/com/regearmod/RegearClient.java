package com.regearmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class RegearClient implements ClientModInitializer {
    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("regear", "main"));
    private static KeyBinding startKey;
    private static KeyBinding pauseKey;
    private static KeyBinding settingsKey;

    @Override
    public void onInitializeClient() {
        RegearConfig.load();

        startKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.regear.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY));
        pauseKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.regear.pause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.regear.settings", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY));

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.of("regear", "regear_hud"), RegearHud::render);

        // Purchase / inventory-full / not-enough-money messages from the server.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> RegearRunner.onChat(message.getString()));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (startKey.wasPressed()) {
                if (RegearRunner.isRunning()) {
                    RegearRunner.cancel(client);
                } else if (client.world != null && client.currentScreen == null) {
                    RegearRunner.start(client);
                }
            }
            while (pauseKey.wasPressed()) {
                RegearRunner.togglePause(client);
            }
            while (settingsKey.wasPressed()) {
                if (client.world != null && client.currentScreen == null) {
                    client.setScreen(new RegearConfigScreen(null));
                }
            }
            RegearRunner.tick(client);
        });
    }
}
