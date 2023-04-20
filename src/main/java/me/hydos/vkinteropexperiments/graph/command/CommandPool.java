package me.hydos.vkinteropexperiments.graph.command;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class CommandPool implements Closeable, VkObjectHolder<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandPool.class);
    public final LogicalDevice logicalDevice;
    private final long commandPool;

    public CommandPool(LogicalDevice logicalDevice, int queueFamilyIndex) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.info("Creating CommandPool");
            this.logicalDevice = logicalDevice;
            var createInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(queueFamilyIndex);

            var pCommandPool = stack.mallocLong(1);
            ok(VK10.vkCreateCommandPool(logicalDevice.vk(), createInfo, null, pCommandPool), "Failed to create CommandPool");
            this.commandPool = pCommandPool.get(0);
        }
    }

    @Override
    public void close() {
        VK10.vkDestroyCommandPool(logicalDevice.vk(), commandPool, null);
    }

    @Override
    public Long vk() {
        return commandPool;
    }
}
