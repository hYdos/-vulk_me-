package me.hydos.vkinteropexperiments.graph.image;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.vulkan.VK10;

import java.io.Closeable;

public class ImageAttachment implements Closeable, VkObjectHolder<Image> {

    private final Image image;
    public final ImageView view;
    public boolean depthAttachment;

    public ImageAttachment(LogicalDevice logicalDevice, int width, int height, int format, int usage) {
        this.image = new Image.Builder().width(width).height(height)
                .usage(usage | VK10.VK_IMAGE_USAGE_SAMPLED_BIT)
                .format(format)
                .build(logicalDevice);

        var aspectMask = 0;
        if ((usage & VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT) > 0) {
            aspectMask = VK10.VK_IMAGE_ASPECT_COLOR_BIT;
            this.depthAttachment = false;
        }
        if ((usage & VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT) > 0) {
            aspectMask = VK10.VK_IMAGE_ASPECT_DEPTH_BIT;
            this.depthAttachment = true;
        }

        this.view = new ImageView(logicalDevice, image.vk(), new ImageViewData.Builder()
                .format(image.format)
                .aspectMask(aspectMask)
                .build()
        );
    }

    @Override
    public void close() {
        view.close();
        image.close();
    }

    @Override
    public Image vk() {
        return image;
    }
}
