package me.hydos.vkinteropexperiments.graph.vertex;

import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;
import org.lwjgl.vulkan.VkVertexInputAttributeDescription;
import org.lwjgl.vulkan.VkVertexInputBindingDescription;

public class VertexBufferStructure implements VertexInputStateInfo {
    private static final int NUMBER_OF_ATTRIBUTES = 2;
    private static final int POS_SIZE = 3 * Float.BYTES;
    public static final int UV_SIZE = 2 * Float.BYTES;
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

        // UV's
        i++;
        vertInAttribs.get(i)
                .binding(0)
                .location(i)
                .format(VK10.VK_FORMAT_R32G32_SFLOAT)
                .offset(POS_SIZE);

        vertInBindings.get(0)
                .binding(0)
                .stride(POS_SIZE + UV_SIZE)
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
