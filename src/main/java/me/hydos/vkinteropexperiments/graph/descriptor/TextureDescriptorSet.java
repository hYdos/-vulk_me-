package me.hydos.vkinteropexperiments.graph.descriptor;

import me.hydos.vkinteropexperiments.graph.image.texture.Texture;
import me.hydos.vkinteropexperiments.graph.image.texture.TextureSampler;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkDescriptorImageInfo;
import org.lwjgl.vulkan.VkDescriptorSetAllocateInfo;
import org.lwjgl.vulkan.VkWriteDescriptorSet;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class TextureDescriptorSet extends DescriptorSet {

    public TextureDescriptorSet(DescriptorPool descriptorPool, DescriptorSetLayout descriptorSetLayout, Texture texture, TextureSampler textureSampler, int binding) {
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
            descriptorSet = pDescriptorSet.get(0);

            var imageInfo = VkDescriptorImageInfo.calloc(1, stack)
                    .imageLayout(VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .imageView(texture.view.vk())
                    .sampler(textureSampler.vk());

            var descrBuffer = VkWriteDescriptorSet.calloc(1, stack);
            descrBuffer.get(0)
                    .sType$Default()
                    .dstSet(descriptorSet)
                    .dstBinding(binding)
                    .descriptorType(VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(1)
                    .pImageInfo(imageInfo);

            VK10.vkUpdateDescriptorSets(logicalDevice, descrBuffer, null);
        }
    }
}
