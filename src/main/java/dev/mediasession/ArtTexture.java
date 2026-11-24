package dev.mediasession;

import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

final class ArtTexture {
    private static String loadedPath = "";
    private static Identifier current;
    private static int counter;

    private ArtTexture() {
    }

    static Identifier update(MediaState state) {
        String path = state.art();
        if (!path.equals(loadedPath)) {
            loadedPath = path;
            release();
            if (!path.isEmpty()) {
                load(path);
            }
        }
        return current;
    }

    private static void load(String path) {
        Path file = Path.of(path);
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            DynamicTexture texture = new DynamicTexture(() -> "mediasession_art", image);
            texture.upload();
            Identifier id = Identifier.fromNamespaceAndPath("mediasession", "art_" + (++counter));
            Minecraft.getInstance().getTextureManager().register(id, texture);
            current = id;
        } catch (Exception e) {
            current = null;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static void release() {
        if (current != null) {
            Minecraft.getInstance().getTextureManager().release(current);
            current = null;
        }
    }
}
