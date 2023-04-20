package me.hydos.vkinteropexperiments.graph.swapchain;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageViewCreateInfo;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class ImageView implements Closeable, VkObjectHolder<Long> {

    private final LogicalDevice logicalDevice;
    public final int aspectMask;
    public final int mipLevels;
    private final long imageView;

    public ImageView(LogicalDevice logicalDevice, long vkImage, ImageViewData imageViewData) {
        this.logicalDevice = logicalDevice;
        this.aspectMask = imageViewData.aspectMask();
        this.mipLevels = imageViewData.mipLevels();

        try (var stack = MemoryStack.stackPush()) {
            var pImageView = stack.mallocLong(1);
            var viewCreateInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(vkImage)
                    .viewType(imageViewData.viewType())
                    .format(imageViewData.format())
                    .subresourceRange(it -> it
                            .aspectMask(aspectMask)
                            .baseMipLevel(0)
                            .levelCount(mipLevels)
                            .baseArrayLayer(imageViewData.baseArrayLayer())
                            .layerCount(imageViewData.layerCount())
                    );

            ok(VK10.vkCreateImageView(logicalDevice.vk(), viewCreateInfo, null, pImageView), "Failed to create ImageView");
            this.imageView = pImageView.get(0);
        }
    }

    @Override
    public Long vk() {
        return imageView;
    }

    @Override
    public void close() {
        VK10.vkDestroyImageView(logicalDevice.vk(), imageView, null);
    }
}
