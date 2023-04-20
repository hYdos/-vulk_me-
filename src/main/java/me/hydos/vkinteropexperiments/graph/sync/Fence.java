package me.hydos.vkinteropexperiments.graph.sync;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkFenceCreateInfo;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class Fence implements Closeable, VkObjectHolder<Long> {

    private final LogicalDevice logicalDevice;
    private final long fence;

    public Fence(LogicalDevice logicalDevice, boolean signaled) {
        try (var stack = MemoryStack.stackPush()) {
            this.logicalDevice = logicalDevice;
            var createInfo = VkFenceCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(signaled ? VK10.VK_FENCE_CREATE_SIGNALED_BIT : 0);

            var lp = stack.mallocLong(1);
            ok(VK10.vkCreateFence(logicalDevice.vk(), createInfo, null, lp), "Failed to create semaphore");
            this.fence = lp.get(0);
        }
    }

    public void waitForFence() {
        VK10.vkWaitForFences(logicalDevice.vk(), fence, true, Long.MAX_VALUE);
    }

    public void reset() {
        VK10.vkResetFences(logicalDevice.vk(), fence);
    }

    @Override
    public void close() {
        VK10.vkDestroyFence(logicalDevice.vk(), fence, null);
    }

    @Override
    public Long vk() {
        return fence;
    }
}
