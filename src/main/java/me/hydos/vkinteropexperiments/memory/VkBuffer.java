package me.hydos.vkinteropexperiments.memory;

import me.hydos.vkinteropexperiments.graph.VkUtils;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;
import static org.lwjgl.vulkan.VK10.vkCreateBuffer;

public class VkBuffer implements Closeable {

    public final long allocationSize;
    public final long buffer;
    public final LogicalDevice logicalDevice;
    public final long memory;
    public final PointerBuffer pBuffer;
    public final long requestedSize;
    public long mappedMemory;

    public VkBuffer(LogicalDevice logicalDevice, long size, int usage, int reqMask) {
        this.logicalDevice = logicalDevice;
        this.requestedSize = size;
        this.mappedMemory = MemoryUtil.NULL;
        try (var stack = MemoryStack.stackPush()) {
            var createInfo = VkBufferCreateInfo.calloc(stack)
                    .sType$Default()
                    .size(size)
                    .usage(usage)
                    .sharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE);

            var pBuffer = stack.mallocLong(1);
            ok(vkCreateBuffer(logicalDevice.vk(), createInfo, null, pBuffer), "Failed to create VkBuffer");
            this.buffer = pBuffer.get(0);

            var memRequirements = VkMemoryRequirements.malloc(stack);
            VK10.vkGetBufferMemoryRequirements(logicalDevice.vk(), buffer, memRequirements);

            var allocInfo = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .allocationSize(memRequirements.size())
                    .memoryTypeIndex(VkUtils.memoryTypeFromProperties(logicalDevice.physicalDevice, memRequirements.memoryTypeBits(), reqMask));

            ok(VK10.vkAllocateMemory(logicalDevice.vk(), allocInfo, null, pBuffer), "Failed to allocate memory");
            this.allocationSize = allocInfo.allocationSize();
            this.memory = pBuffer.get(0);
            this.pBuffer = MemoryUtil.memAllocPointer(1);

            ok(VK10.vkBindBufferMemory(logicalDevice.vk(), buffer, memory, 0), "Failed to bind buffer memory");
        }
    }

    public long map() {
        if (mappedMemory == MemoryUtil.NULL) {
            ok(VK10.vkMapMemory(logicalDevice.vk(), memory, 0, allocationSize, 0, pBuffer), "Failed to map Buffer");
            this.mappedMemory = pBuffer.get(0);
        }

        return mappedMemory;
    }

    public void unMap() {
        if (mappedMemory != MemoryUtil.NULL) {
            VK10.vkUnmapMemory(logicalDevice.vk(), memory);
            mappedMemory = MemoryUtil.NULL;
        }
    }


    @Override
    public void close() {
        VK10.vkDestroyBuffer(logicalDevice.vk(), buffer, null);
        VK10.vkFreeMemory(logicalDevice.vk(), memory, null);
    }
}
