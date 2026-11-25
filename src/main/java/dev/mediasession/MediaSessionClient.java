package dev.mediasession;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class MediaSessionClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath("mediasession", "main"));

    static KeyMapping OPEN;
    static KeyMapping TOGGLE;
    static KeyMapping NEXT;
    static KeyMapping PREVIOUS;

    @Override
    public void onInitializeClient() {
        OPEN = key("open", InputConstants.KEY_M);
        TOGGLE = key("toggle", InputConstants.KEY_F6);
        PREVIOUS = key("previous", InputConstants.KEY_F7);
        NEXT = key("next", InputConstants.KEY_F8);

        MusicMute.init();
        MediaBridge.start();

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("mediasession", "now_playing"),
                MediaHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(MediaSessionClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MusicMute.shutdown();
            MediaBridge.get().shutdown();
        });
    }

    private static KeyMapping key(String name, int code) {
        return KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.mediasession." + name, InputConstants.Type.KEYBOARD, code, CATEGORY));
    }

    private static void tick(Minecraft client) {
        MusicMute.tick(client);
        while (OPEN.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new MediaScreen());
            }
        }
        while (TOGGLE.consumeClick()) {
            MediaBridge.get().toggle();
            MediaHud.poke();
        }
        while (PREVIOUS.consumeClick()) {
            MediaBridge.get().previous();
            MediaHud.poke();
        }
        while (NEXT.consumeClick()) {
            MediaBridge.get().next();
            MediaHud.poke();
        }
    }
}
