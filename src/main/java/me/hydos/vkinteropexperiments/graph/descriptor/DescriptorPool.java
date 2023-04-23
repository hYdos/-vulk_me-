package me.hydos.vkinteropexperiments.graph.descriptor;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorPoolCreateInfo;
import org.lwjgl.vulkan.VkDescriptorPoolSize;

import java.io.Closeable;
import java.util.List;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class DescriptorPool implements Closeable, VkObjectHolder<Long> {

    public final LogicalDevice logicalDevice;
    private final long pool;

    public DescriptorPool(LogicalDevice logicalDevice, List<DescriptorTypeCount> descriptorTypeCounts) {
        this.logicalDevice = logicalDevice;
        try (var stack = MemoryStack.stackPush()) {
            var maxSets = 0;
            var numTypes = descriptorTypeCounts.size();
            var typeCounts = VkDescriptorPoolSize.calloc(numTypes, stack);

            for (var i = 0; i < numTypes; i++) {
                maxSets += descriptorTypeCounts.get(i).count();
                typeCounts.get(i)
                        .type(descriptorTypeCounts.get(i).descriptorType())
                        .descriptorCount(descriptorTypeCounts.get(i).count());
            }

            var descriptorPoolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK10.VK_DESCRIPTOR_POOL_CREATE_FREE_DESCRIPTOR_SET_BIT)
                    .pPoolSizes(typeCounts)
                    .maxSets(maxSets);

            var pDescriptorPool = stack.mallocLong(1);
            ok(VK10.vkCreateDescriptorPool(logicalDevice.vk(), descriptorPoolInfo, null, pDescriptorPool), "Failed to create descriptor pool");
            this.pool = pDescriptorPool.get(0);
        }
    }

    public void freeDescriptorSet(long descriptorSet) {
        try (var stack = MemoryStack.stackPush()) {
            var pDescSets = stack.mallocLong(1);
            pDescSets.put(0, descriptorSet);

            ok(VK10.vkFreeDescriptorSets(logicalDevice.vk(), pool, pDescSets), "Failed to free descriptor set");
        }
    }

    @Override
    public void close() {
        VK10.vkDestroyDescriptorPool(logicalDevice.vk(), pool, null);
    }

    @Override
    public Long vk() {
        return pool;
    }

    public record DescriptorTypeCount(
            int count,
            int descriptorType
    ) {}
}
