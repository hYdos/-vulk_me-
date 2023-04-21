package me.hydos.vkinteropexperiments.graph.image;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.VkUtils;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class Image implements Closeable, VkObjectHolder<Long> {

    private final LogicalDevice logicalDevice;
    public final int format;
    public final int mipLevels;
    private final long image;
    public final long memory;

    private Image(LogicalDevice logicalDevice, Builder builder) {
        try (var stack = MemoryStack.stackPush()) {
            this.logicalDevice = logicalDevice;
            this.format = builder.format;
            this.mipLevels = builder.mipLevels;

            var imageCreateInfo = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .imageType(VK10.VK_IMAGE_TYPE_2D)
                    .format(format)
                    .extent(it -> it
                            .width(builder.width)
                            .height(builder.height)
                            .depth(1)
                    )
                    .mipLevels(mipLevels)
                    .arrayLayers(builder.arrayLayers)
                    .samples(builder.sampleCount)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .tiling(VK10.VK_IMAGE_TILING_OPTIMAL)
                    .usage(builder.usage);

            var pp = stack.mallocLong(1);
            ok(VK10.vkCreateImage(logicalDevice.vk(), imageCreateInfo, null, pp), "Failed to create image");
            this.image = pp.get(0);

            var memReqs = VkMemoryRequirements.calloc(stack);
            VK10.vkGetImageMemoryRequirements(logicalDevice.vk(), image, memReqs);

            var memAlloc = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memReqs.size())
                    .memoryTypeIndex(VkUtils.memoryTypeFromProperties(logicalDevice.physicalDevice, memReqs.memoryTypeBits(), 0));

            ok(VK10.vkAllocateMemory(logicalDevice.vk(), memAlloc, null, pp), "Failed to allocate memory");
            this.memory = pp.get(0);

            ok(VK10.vkBindImageMemory(logicalDevice.vk(), image, memory, 0), "Failed to bind image memory");
        }
    }

    @Override
    public void close() {
        VK10.vkDestroyImage(logicalDevice.vk(), image, null);
        VK10.vkFreeMemory(logicalDevice.vk(), memory, null);
    }

    @Override
    public Long vk() {
        return image;
    }

    public static class Builder {
        private int arrayLayers;
        private int format;
        private int height;
        private int mipLevels;
        private int sampleCount;
        private int usage;
        private int width;

        public Builder() {
            this.format = VK10.VK_FORMAT_R8G8B8A8_SRGB;
            this.mipLevels = 1;
            this.sampleCount = 1;
            this.arrayLayers = 1;
        }

        public Builder arrayLayers(int arrayLayers) {
            this.arrayLayers = arrayLayers;
            return this;
        }

        public Builder format(int format) {
            this.format = format;
            return this;
        }

        public Builder height(int height) {
            this.height = height;
            return this;
        }

        public Builder mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public Builder sampleCount(int sampleCount) {
            this.sampleCount = sampleCount;
            return this;
        }

        public Builder usage(int usage) {
            this.usage = usage;
            return this;
        }

        public Builder width(int width) {
            this.width = width;
            return this;
        }

        public Image build(LogicalDevice logicalDevice) {
            return new Image(logicalDevice, this);
        }
    }
}
