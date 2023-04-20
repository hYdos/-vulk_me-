package me.hydos.vkinteropexperiments.graph.setup;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;
import static org.lwjgl.vulkan.VK10.*;

public class LogicalDevice implements Closeable, VkObjectHolder<VkDevice> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LogicalDevice.class);
    public final PhysicalDevice physicalDevice;
    private final VkDevice vkDevice;

    public LogicalDevice(PhysicalDevice physicalDevice) {
        LOGGER.info("Creating LogicalDevice derived from" + physicalDevice.getName());

        this.physicalDevice = physicalDevice;
        try (var stack = MemoryStack.stackPush()) {
            var requiredExtensions = stack.mallocPointer(1);
            requiredExtensions.put(0, stack.ASCII(KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME));

            var features = VkPhysicalDeviceFeatures.calloc(stack);

            var queuePropsBuff = physicalDevice.queueFamilyProps;
            int numQueuesFamilies = queuePropsBuff.capacity();
            var queueCreationInfo = VkDeviceQueueCreateInfo.calloc(numQueuesFamilies, stack);
            for (var i = 0; i < numQueuesFamilies; i++) {
                var pPriorities = stack.callocFloat(queuePropsBuff.get(i).queueCount());
                queueCreationInfo.get(i)
                        .sType$Default()
                        .queueFamilyIndex(i)
                        .pQueuePriorities(pPriorities);
            }

            var deviceCreateInfo = VkDeviceCreateInfo.calloc(stack)
                    .sType$Default()
                    .ppEnabledExtensionNames(requiredExtensions)
                    .pEnabledFeatures(features)
                    .pQueueCreateInfos(queueCreationInfo);

            var pDevice = stack.mallocPointer(1);
            ok(vkCreateDevice(physicalDevice.vk(), deviceCreateInfo, null, pDevice), "Failed to create LogicalDevice");
            this.vkDevice = new VkDevice(pDevice.get(0), physicalDevice.vk(), deviceCreateInfo);
        }
    }

    @Override
    public void close() {
        LOGGER.info("Closing");
        vkDestroyDevice(vkDevice, null);
    }

    @Override
    public VkDevice vk() {
        return vkDevice;
    }

    public void waitIdle() {
        vkDeviceWaitIdle(vkDevice);
    }
}