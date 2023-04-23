package me.hydos.vkinteropexperiments;

import com.thebombzen.jxlatte.JXLDecoder;
import com.thebombzen.jxlatte.JXLOptions;
import com.thepokecraftmod.rks.FileLocator;
import com.thepokecraftmod.rks.model.Model;
import com.thepokecraftmod.rks.model.config.TextureFilter;
import com.thepokecraftmod.rks.model.texture.TextureType;
import me.hydos.vkinteropexperiments.graph.Renderer;
import org.lwjgl.vulkan.VK10;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MaterialUploader {
    private static final Logger LOGGER = LoggerFactory.getLogger("Material Uploader");
    public final Map<String, Material> materials = new HashMap<>();
    public final List<Runnable> mainThreadUploads = new ArrayList<>();
    private final Renderer renderer;

    public MaterialUploader(Model model, FileLocator locator, Renderer renderer) {
        var filter = model.config().textureFiltering;
        this.renderer = renderer;

        for (var entry : model.config().materials.entrySet()) {
            var name = entry.getKey();
            var meshMaterial = entry.getValue();
            var material = new Material(name);

            var texture = meshMaterial.getTextures(TextureType.ALBEDO);

            if (texture.size() < 1) LOGGER.debug("Shader expects " + TextureType.ALBEDO + " but the texture is missing");
            else upload(material, mergeAndLoad(locator, filter, texture));

            materials.put(name, material);
        }
    }

    private BufferedImage mergeAndLoad(FileLocator locator, TextureFilter filter, List<String> textures) {
        var imageReferences = textures.stream().map(s -> "textures/" + s).map(locator::getFile).toList();

        var loadedImages = imageReferences.stream().map(bytes -> {
            try {
                var options = new JXLOptions();
                options.hdr = JXLOptions.HDR_OFF;
                options.threads = 2;
                var reader = new JXLDecoder(new ByteArrayInputStream(bytes), options);
                var image = reader.decode();
                return image.fillColor().asBufferedImage();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).toList();
        var processedImages = new ArrayList<BufferedImage>();

        for (var image : loadedImages) {
            var width = image.getWidth();
            var height = image.getHeight();
            var needMirror = height / width == 2;

            if (needMirror) {
                var mirror = new BufferedImage(width * 2, height, BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < height; y++) {
                    for (int lx = 0, rx = width * 2 - 1; lx < width; lx++, rx--) {
                        int p = image.getRGB(lx, y);
                        mirror.setRGB(lx, y, p);
                        mirror.setRGB(rx, y, p);
                    }
                }

                processedImages.add(mirror);
            } else processedImages.add(image);
        }

        var baseImage = processedImages.get(0);
        var topLayers = processedImages.subList(1, textures.size());
        for (var topLayer : topLayers) {
            for (int x = 0; x < baseImage.getWidth(); x++) {
                for (int y = 0; y < baseImage.getHeight(); y++) {
                    var p = topLayer.getRGB(x, y);
                    var alpha = 0xFF & (p >> 24);
                    var red = 0xFF & (p >> 16);
                    var green = 0xFF & (p >> 8);
                    var blue = 0xFF & (p);
                    // TODO: option to set bg color
                    if (green < 200) baseImage.setRGB(x, y, p);
                }
            }
        }

        return baseImage;
    }

    private void upload(Material material, BufferedImage image) {
        renderer.textureCache.createTexture(renderer.logicalDevice, image, VK10.VK_FORMAT_R8G8B8A8_SRGB, false);
        material.textures.add(image);
    }

    public static class Material {
        public final String name;
        public final List<BufferedImage> textures = new ArrayList<>();

        public Material(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "Material{" + name + '}';
        }
    }
}
