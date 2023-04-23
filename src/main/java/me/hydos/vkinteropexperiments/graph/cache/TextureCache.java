package me.hydos.vkinteropexperiments.graph.cache;

import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.image.texture.Texture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;

public class TextureCache implements Closeable {
    public static final BufferedImage MISSING;
    private final Map<BufferedImage, Texture> textureMap = new HashMap<>();
    private final List<BufferedImage> textures = new ArrayList<>();

    public Texture createTexture(LogicalDevice logicalDevice, BufferedImage cpuTexture, int format) {
        return textureMap.computeIfAbsent(cpuTexture, image -> {
            var texture = new Texture(logicalDevice, cpuTexture, format, 1);
            textures.add(image);
            return texture;
        });
    }

    public Texture getTexture(BufferedImage cpuTexture) {
        if (!textureMap.containsKey(cpuTexture)) throw new RuntimeException("Tried accessing non-existent texture");
        return textureMap.get(cpuTexture);
    }

    public int getTextureId(BufferedImage cpuTexture) {
        return textures.indexOf(cpuTexture);
    }

    @Override
    public void close() {
        textureMap.values().forEach(Texture::close);
    }

    static {
        try {
            MISSING = ImageIO.read(Objects.requireNonNull(TextureCache.class.getResourceAsStream("/missing.png")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
