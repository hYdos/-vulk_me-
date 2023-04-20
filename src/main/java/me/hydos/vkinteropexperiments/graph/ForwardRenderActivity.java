package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.graph.command.CommandBuffer;
import me.hydos.vkinteropexperiments.graph.command.CommandPool;
import me.hydos.vkinteropexperiments.graph.setup.Queue;
import me.hydos.vkinteropexperiments.graph.swapchain.RenderPass;
import me.hydos.vkinteropexperiments.graph.swapchain.SurfaceSwapchain;
import me.hydos.vkinteropexperiments.graph.swapchain.Swapchain;
import me.hydos.vkinteropexperiments.graph.sync.Fence;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;

import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

public class ForwardRenderActivity implements Closeable {

    private final SurfaceSwapchain swapchain;
    private final RenderPass renderPass;
    private final FrameBuffer[] frameBuffers;
    private final CommandBuffer[] commandBuffers;
    private final Fence[] fences;

    public ForwardRenderActivity(SurfaceSwapchain swapchain, CommandPool cmdPool) {
        try (var stack = MemoryStack.stackPush()) {
            this.swapchain = swapchain;
            var logicalDevice = swapchain.logicalDevice;
            var swapchainExtent = swapchain.extent;
            var imgViews = swapchain.imageViews;
            int imgCount = imgViews.length;
            this.renderPass = new RenderPass(swapchain);

            var pAttachments = stack.mallocLong(1);
            this.frameBuffers = new FrameBuffer[imgCount];
            for (int i = 0; i < imgCount; i++) {
                pAttachments.put(0, imgViews[i].vk());
                frameBuffers[i] = new FrameBuffer(logicalDevice, swapchainExtent.width(), swapchainExtent.height(), pAttachments, renderPass.vk());
            }

            this.commandBuffers = new CommandBuffer[imgCount];
            this.fences = new Fence[imgCount];
            for (var i = 0; i < imgCount; i++) {
                commandBuffers[i] = new CommandBuffer(cmdPool, true, false);
                fences[i] = new Fence(logicalDevice, true);
                recordCommandBuffer(commandBuffers[i], frameBuffers[i], swapchainExtent.width(), swapchainExtent.height());
            }
        }
    }

    private void recordCommandBuffer(CommandBuffer cmdBuffer, FrameBuffer frameBuffer, int width, int height) {
        try (var stack = MemoryStack.stackPush()) {
            var clearValues = VkClearValue.calloc(1, stack);
            clearValues.apply(0, v -> v.color()
                    .float32(0, 1f)
                    .float32(1, 0f)
                    .float32(2, 0f)
                    .float32(3, 1));
            var renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass.vk())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.vk());

            cmdBuffer.beginRecording();
            VK10.vkCmdBeginRenderPass(cmdBuffer.vk(), renderPassBeginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);
            VK10.vkCmdEndRenderPass(cmdBuffer.vk());
            cmdBuffer.endRecording();
        }
    }

    public void submit(Queue queue) {
        try (var stack = MemoryStack.stackPush()) {
            var idx = swapchain.currentFrame;
            var commandBuffer = commandBuffers[idx];
            var currentFence = fences[idx];
            currentFence.waitForFence();
            currentFence.reset();
            var syncSemaphores = swapchain.syncSemaphores[idx];
            queue.submit(stack.pointers(commandBuffer.vk()),
                    stack.longs(syncSemaphores.imgAcquisitionSemaphore().vk()),
                    stack.ints(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.renderCompleteSemaphore().vk()), currentFence);
        }
    }


    @Override
    public void close() {
        Arrays.stream(frameBuffers).forEach(FrameBuffer::close);
        renderPass.close();
        Arrays.stream(commandBuffers).forEach(CommandBuffer::close);
        Arrays.stream(fences).forEach(Fence::close);
    }
}
