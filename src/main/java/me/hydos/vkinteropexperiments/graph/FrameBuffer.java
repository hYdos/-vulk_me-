package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;

import java.io.Closeable;
import java.io.IOException;
import java.nio.LongBuffer;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class FrameBuffer implements Closeable, VkObjectHolder<Long> {

    private final LogicalDevice logicalDevice;
    private final long frameBuffer;

    public FrameBuffer(LogicalDevice logicalDevice, int width, int height, LongBuffer pAttachments, long renderPass) {
        try (var stack = MemoryStack.stackPush()) {
            this.logicalDevice = logicalDevice;
            var createInfo = VkFramebufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(pAttachments)
                    .width(width)
                    .height(height)
                    .layers(1)
                    .renderPass(renderPass);


            var pFrameBuffer = stack.mallocLong(1);
            ok(VK10.vkCreateFramebuffer(logicalDevice.vk(), createInfo, null, pFrameBuffer), "Failed to create FrameBuffer");
            this.frameBuffer = pFrameBuffer.get(0);
        }
    }

    @Override
    public void close() {
        VK10.vkDestroyFramebuffer(logicalDevice.vk(), frameBuffer, null);
    }

    @Override
    public Long vk() {
        return frameBuffer;
    }
}
