package me.hydos.vkinteropexperiments.debug;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import me.hydos.vkinteropexperiments.graph.setup.PhysicalDevice;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSurface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

public class Surface implements Closeable, VkObjectHolder<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Surface.class);
    private final PhysicalDevice physicalDevice;
    private final long surface;

    public Surface(PhysicalDevice physicalDevice, long pWindow) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.info("Creating Debug Window Surface");
            this.physicalDevice = physicalDevice;
            var pSurface = stack.mallocLong(1);
            GLFWVulkan.glfwCreateWindowSurface(this.physicalDevice.vk().getInstance(), pWindow, null, pSurface);
            this.surface = pSurface.get(0);
        }
    }

    @Override
    public void close() {
        LOGGER.info("Closing");
        KHRSurface.vkDestroySurfaceKHR(physicalDevice.vk().getInstance(), surface, null);
    }

    @Override
    public Long vk() {
        return surface;
    }
}
