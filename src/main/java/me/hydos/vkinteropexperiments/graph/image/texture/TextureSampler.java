package me.hydos.vkinteropexperiments.graph.image.texture;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkSamplerCreateInfo;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class TextureSampler implements Closeable, VkObjectHolder<Long> {
    private static final int MAX_ANISOTROPY = 16;
    private final LogicalDevice logicalDevice;
    private final long sampler;

    public TextureSampler(LogicalDevice logicalDevice, int mipLevels) {
        try (var stack = MemoryStack.stackPush()) {
            this.logicalDevice = logicalDevice;
            var samplerInfo = VkSamplerCreateInfo.calloc(stack)
                    .sType$Default()
                    .magFilter(VK10.VK_FILTER_LINEAR)
                    .minFilter(VK10.VK_FILTER_LINEAR)
                    .addressModeU(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeV(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .addressModeW(VK10.VK_SAMPLER_ADDRESS_MODE_REPEAT)
                    .borderColor(VK10.VK_BORDER_COLOR_INT_OPAQUE_BLACK)
                    .unnormalizedCoordinates(false)
                    .compareEnable(false)
                    .compareOp(VK10.VK_COMPARE_OP_ALWAYS)
                    .mipmapMode(VK10.VK_SAMPLER_MIPMAP_MODE_LINEAR)
                    .minLod(0.0f)
                    .maxLod(mipLevels)
                    .mipLodBias(0.0f);
            if (logicalDevice.samplerAnisotropy) {
                samplerInfo
                        .anisotropyEnable(true)
                        .maxAnisotropy(MAX_ANISOTROPY);
            }

            var lp = stack.mallocLong(1);
            ok(VK10.vkCreateSampler(logicalDevice.vk(), samplerInfo, null, lp), "Failed to create sampler");
            sampler = lp.get(0);
        }
    }

    @Override
    public void close() {
        VK10.vkDestroySampler(logicalDevice.vk(), sampler, null);
    }

    @Override
    public Long vk() {
        return sampler;
    }
}
