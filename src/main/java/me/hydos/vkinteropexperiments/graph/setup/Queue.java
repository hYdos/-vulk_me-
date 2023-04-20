package me.hydos.vkinteropexperiments.graph.setup;

import me.hydos.vkinteropexperiments.debug.Surface;
import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.sync.Fence;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;
import static org.lwjgl.vulkan.VK10.vkGetDeviceQueue;

public class Queue implements VkObjectHolder<VkQueue> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Queue.class);
    private final VkQueue queue;
    public final int queueFamilyIndex;

    public Queue(LogicalDevice device, int queueFamilyIndex, int queueIndex) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.debug("Creating queue");
            this.queueFamilyIndex = queueFamilyIndex;
            var pQueue = stack.mallocPointer(1);
            vkGetDeviceQueue(device.vk(), queueFamilyIndex, queueIndex, pQueue);
            this.queue = new VkQueue(pQueue.get(0), device.vk());
        }
    }

    public void submit(PointerBuffer commandBuffers, LongBuffer waitSemaphores, IntBuffer dstStageMasks, LongBuffer signalSemaphores, Fence fence) {
        try (var stack = MemoryStack.stackPush()) {
            var submitInfo = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(commandBuffers)
                    .pSignalSemaphores(signalSemaphores);
            if (waitSemaphores != null) {
                submitInfo.waitSemaphoreCount(waitSemaphores.capacity())
                        .pWaitSemaphores(waitSemaphores)
                        .pWaitDstStageMask(dstStageMasks);
            } else submitInfo.waitSemaphoreCount(0);

            var fenceHandle = fence != null ? fence.vk() : VK10.VK_NULL_HANDLE;
            ok(VK10.vkQueueSubmit(queue, submitInfo, fenceHandle), "Failed to submit command to queue");
        }
    }

    public void waitIdle() {
        VK10.vkQueueWaitIdle(queue);
    }

    @Override
    public VkQueue vk() {
        return queue;
    }

    public static class Graphics extends Queue {

        public Graphics(LogicalDevice device, int queueIndex) {
            super(device, getGraphicsQueueFamilyIndex(device), queueIndex);
        }

        private static int getGraphicsQueueFamilyIndex(LogicalDevice device) {
            int index = -1;
            var physicalDevice = device.physicalDevice;
            var queuePropsBuff = physicalDevice.queueFamilyProps;

            int queueFamilyCount = queuePropsBuff.capacity();
            for (var i = 0; i < queueFamilyCount; i++) {
                var props = queuePropsBuff.get(i);
                var graphicsQueue = (props.queueFlags() & VK10.VK_QUEUE_GRAPHICS_BIT) != 0;

                if (graphicsQueue) {
                    index = i;
                    break;
                }
            }

            if (index < 0) throw new RuntimeException("Failed to get graphics Queue family index");
            return index;
        }
    }

    public static class Present extends Queue {

        public Present(LogicalDevice device, Surface surface, int queueIndex) {
            super(device, getPresentQueueFamilyIndex(device, surface), queueIndex);
        }

        private static int getPresentQueueFamilyIndex(LogicalDevice device, Surface surface) {
            try (var stack = MemoryStack.stackPush()) {
                var index = -1;
                var physicalDevice = device.physicalDevice;
                var queuePropsBuff = physicalDevice.queueFamilyProps;
                int numQueuesFamilies = queuePropsBuff.capacity();
                var pResult = stack.mallocInt(1);

                for (var i = 0; i < numQueuesFamilies; i++) {
                    KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice.vk(), i, surface.vk(), pResult);
                    var supportsPresentation = pResult.get(0) == VK10.VK_TRUE;

                    if (supportsPresentation) {
                        index = i;
                        break;
                    }
                }

                if (index < 0) throw new RuntimeException("Failed to get Presentation Queue family index");
                return index;
            }
        }
    }
}