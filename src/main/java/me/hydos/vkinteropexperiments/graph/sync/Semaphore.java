package me.hydos.vkinteropexperiments.graph.sync;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class Semaphore implements Closeable, VkObjectHolder<Long> {

    private final LogicalDevice logicalDevice;
    private final long semaphore;

    public Semaphore(LogicalDevice device) {
        try (var stack = MemoryStack.stackPush()) {
            this.logicalDevice = device;
            var createInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();

            var pSemaphore = stack.mallocLong(1);
            ok(VK10.vkCreateSemaphore(device.vk(), createInfo, null, pSemaphore), "Failed to create semaphore");
            this.semaphore = pSemaphore.get(0);
        }
    }

    @Override
    public void close() {
        VK10.vkDestroySemaphore(logicalDevice.vk(), semaphore, null);
    }

    @Override
    public Long vk() {
        return semaphore;
    }
}
