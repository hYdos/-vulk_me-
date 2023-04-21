package me.hydos.vkinteropexperiments.graph.pipeline;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkPipelineCacheCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class PipelineCache implements Closeable, VkObjectHolder<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineCache.class);
    public final LogicalDevice logicalDevice;
    private final long pipelineCache;

    public PipelineCache(LogicalDevice logicalDevice) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.info("Creating PiplineCache");
            this.logicalDevice = logicalDevice;
            var createInfo = VkPipelineCacheCreateInfo.calloc(stack).sType$Default();

            var pPipelineCache = stack.mallocLong(1);
            ok(VK10.vkCreatePipelineCache(logicalDevice.vk(), createInfo, null, pPipelineCache), "Failed to create PipelineCache");
            this.pipelineCache = pPipelineCache.get(0);
        }
    }

    @Override
    public void close() {
        LOGGER.info("Closing");
        VK10.vkDestroyPipelineCache(logicalDevice.vk(), pipelineCache, null);
    }

    @Override
    public Long vk() {
        return pipelineCache;
    }
}
