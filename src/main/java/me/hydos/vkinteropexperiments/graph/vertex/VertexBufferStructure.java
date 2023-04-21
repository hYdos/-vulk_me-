package me.hydos.vkinteropexperiments.graph.vertex;

import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

public class VertexBufferStructure implements VertexInputStateInfo {
    private static final int NUMBER_OF_ATTRIBUTES = 1;
    private static final int POSITION_COMPONENTS = 3;
    private final VkPipelineVertexInputStateCreateInfo vertexInput;
    private final VkVertexInputAttributeDescription.Buffer vertInAttribs;
    private final VkVertexInputBindingDescription.Buffer vertInBindings;

    public VertexBufferStructure() {
        this.vertInAttribs = VkVertexInputAttributeDescription.calloc(NUMBER_OF_ATTRIBUTES);
        this.vertInBindings = VkVertexInputBindingDescription.calloc(1);
        this.vertexInput = VkPipelineVertexInputStateCreateInfo.calloc();

        var i = 0;
        // Position
        vertInAttribs.get(i)
                .binding(0)
                .location(i)
                .format(VK10.VK_FORMAT_R32G32B32_SFLOAT)
                .offset(0);

        vertInBindings.get(0)
                .binding(0)
                .stride(POSITION_COMPONENTS * Float.BYTES)
                .inputRate(VK10.VK_VERTEX_INPUT_RATE_VERTEX);

        vertexInput
                .sType$Default()
                .pVertexBindingDescriptions(vertInBindings)
                .pVertexAttributeDescriptions(vertInAttribs);
    }

    @Override
    public VkPipelineVertexInputStateCreateInfo getVertexInput() {
        return vertexInput;
    }

    @Override
    public void close() {
        VertexInputStateInfo.super.close();
        vertInBindings.free();
        vertInAttribs.free();
    }
}
