package me.hydos.vkinteropexperiments.memory;

import me.hydos.vkinteropexperiments.graph.setup.Instance;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.PhysicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.Vma;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.VK12;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class Allocator {

    private static long createVmaAllocator(PhysicalDevice physicalDevice, LogicalDevice logicalDevice, Instance instance) {
        try (var stack = MemoryStack.stackPush()) {
            var pAllocator = stack.mallocPointer(1);
            ok(Vma.vmaCreateAllocator(
                            VmaAllocatorCreateInfo
                                    .calloc(stack)
                                    .flags(Vma.VMA_ALLOCATOR_CREATE_BUFFER_DEVICE_ADDRESS_BIT)
                                    .physicalDevice(physicalDevice.vk())
                                    .device(logicalDevice.vk())
                                    .pVulkanFunctions(VmaVulkanFunctions.calloc(stack)
                                            .set(instance.vk(), logicalDevice.vk()))
                                    .instance(instance.vk())
                                    .vulkanApiVersion(VK12.VK_API_VERSION_1_2),
                            pAllocator),
                    "Failed to create VMA allocator");
            return pAllocator.get(0);
        }
    }
}
