package me.hydos.vkinteropexperiments.graph.vertex;

import org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo;

import java.io.Closeable;

public interface VertexInputStateInfo extends Closeable {

    VkPipelineVertexInputStateCreateInfo getVertexInput();

    @Override
    default void close() {
        getVertexInput().free();
    }
}
