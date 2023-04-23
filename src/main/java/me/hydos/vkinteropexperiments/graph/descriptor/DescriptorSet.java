package me.hydos.vkinteropexperiments.graph.descriptor;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.memory.VkBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorBufferInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class DescriptorSet implements VkObjectHolder<Long> {

    protected long descriptorSet;

    @Override
    public Long vk() {
        return descriptorSet;
    }

    public static class SimpleDescriptorSet extends DescriptorSet {
        public SimpleDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout, VkBuffer buffer, int binding, int type, long size) {
            try (var stack = MemoryStack.stackPush()) {
                var logicalDevice = descriptorPool.logicalDevice.vk();
                var pDescriptorSetLayout = stack.mallocLong(1);
                pDescriptorSetLayout.put(0, descriptorSetLayout.vk());
                var allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                        .sType$Default()
                        .descriptorPool(descriptorPool.vk())
                        .pSetLayouts(pDescriptorSetLayout);

                var pDescriptorSet = stack.mallocLong(1);
                ok(VK10.vkAllocateDescriptorSets(logicalDevice, allocInfo, pDescriptorSet), "Failed to create descriptor set");

                this.descriptorSet = pDescriptorSet.get(0);

                var bufferInfo = VkDescriptorBufferInfo.calloc(1, stack)
                        .buffer(buffer.buffer)
                        .offset(0)
                        .range(size);

                var descrBuffer = VkWriteDescriptorSet.calloc(1, stack);

                descrBuffer.get(0)
                        .sType$Default()
                        .dstSet(descriptorSet)
                        .dstBinding(binding)
                        .descriptorType(type)
                        .descriptorCount(1)
                        .pBufferInfo(bufferInfo);

                VK10.vkUpdateDescriptorSets(logicalDevice, descrBuffer, null);
            }
        }
    }

    public static class UniformDescriptorSet extends SimpleDescriptorSet {
        public UniformDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout, VkBuffer buffer, int binding) {
            super(descriptorPool, descriptorSetLayout, buffer, binding, VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, buffer.requestedSize);
        }
    }
}
