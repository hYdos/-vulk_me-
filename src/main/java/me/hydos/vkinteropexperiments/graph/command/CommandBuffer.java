package me.hydos.vkinteropexperiments.graph.command;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class CommandBuffer implements Closeable, VkObjectHolder<VkCommandBuffer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandBuffer.class);
    private final CommandPool pool;
    private final boolean oneTimeSubmit;
    private final VkCommandBuffer commandBuffer;

    public CommandBuffer(CommandPool pool, boolean primary, boolean oneTimeSubmit) {
        try (var stack = MemoryStack.stackPush()) {
            this.pool = pool;
            this.oneTimeSubmit = oneTimeSubmit;
            var device = pool.logicalDevice;
            var allocInfo = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(pool.vk())
                    .level(primary ? VK10.VK_COMMAND_BUFFER_LEVEL_PRIMARY : VK10.VK_COMMAND_BUFFER_LEVEL_SECONDARY)
                    .commandBufferCount(1);

            var pCommandBuffer = stack.mallocPointer(1);
            ok(VK10.vkAllocateCommandBuffers(device.vk(), allocInfo, pCommandBuffer), "Failed to allocate a CommandBuffer");
            this.commandBuffer = new VkCommandBuffer(pCommandBuffer.get(0), device.vk());
        }
    }

    public void beginRecording() {
        try (var stack = MemoryStack.stackPush()) {
            var beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(oneTimeSubmit ? VK10.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT : 0);
            ok(VK10.vkBeginCommandBuffer(commandBuffer, beginInfo), "Failed to begin CommandBuffer");
        }
    }

    public void endRecording() {
        ok(VK10.vkEndCommandBuffer(commandBuffer), "Failed to end CommandBuffer");
    }

    public void reset() {
        ok(VK10.vkResetCommandBuffer(commandBuffer, VK10.VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT), "Failed to reset CommandBuffer");
    }

    @Override
    public void close() {
        LOGGER.info("Closing");
        VK10.vkFreeCommandBuffers(pool.logicalDevice.vk(), pool.vk(), commandBuffer);
    }

    @Override
    public VkCommandBuffer vk() {
        return commandBuffer;
    }
}
