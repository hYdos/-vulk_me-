package me.hydos.vkinteropexperiments.graph.descriptor;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorSetLayoutBinding;
import org.lwjgl.vulkan.VkDescriptorSetLayoutCreateInfo;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public abstract class DescriptorSetLayout implements Closeable, VkObjectHolder<Long> {

    public final LogicalDevice logicalDevice;
    public long layout;

    protected DescriptorSetLayout(LogicalDevice logicalDevice) {
        this.logicalDevice = logicalDevice;
    }

    @Override
    public void close() {
        VK10.vkDestroyDescriptorSetLayout(logicalDevice.vk(), layout, null);
    }

    @Override
    public Long vk() {
        return layout;
    }

    public static class SamplerDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public SamplerDescriptorSetLayout(LogicalDevice logicalDevice, int binding, int stage) {
            super(logicalDevice, VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, binding, stage);
        }
    }

    public static class SimpleDescriptorSetLayout extends DescriptorSetLayout {
        public SimpleDescriptorSetLayout(LogicalDevice device, int descriptorType, int binding, int stage) {
            super(device);
            try (var stack = MemoryStack.stackPush()) {
                var layoutBindings = VkDescriptorSetLayoutBinding.calloc(1, stack);
                layoutBindings.get(0)
                        .binding(binding)
                        .descriptorType(descriptorType)
                        .descriptorCount(1)
                        .stageFlags(stage);

                var layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                        .sType$Default()
                        .pBindings(layoutBindings);

                var pSetLayout = stack.mallocLong(1);
                ok(VK10.vkCreateDescriptorSetLayout(device.vk(), layoutInfo, null, pSetLayout), "Failed to create descriptor set layout");
                this.layout = pSetLayout.get(0);
            }
        }
    }

    public static class UniformDescriptorSetLayout extends SimpleDescriptorSetLayout {
        public UniformDescriptorSetLayout(LogicalDevice device, int binding, int stage) {
            super(device, VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, binding, stage);
        }
    }
}