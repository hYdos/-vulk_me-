package me.hydos.vkinteropexperiments.debug;

import org.lwjgl.glfw.Callbacks;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;

import java.io.Closeable;

public class DebugWindow implements Closeable {

    public final long pointer;
    public boolean resized;
    public int width;
    public int height;

    public DebugWindow() {
        if (!GLFW.glfwInit()) throw new IllegalStateException("Unable to initialize GLFW");
        if (!GLFWVulkan.glfwVulkanSupported()) throw new IllegalStateException("Cannot find a Vulkan driver");

        var monitors = GLFW.glfwGetMonitors();
        var vidMode = GLFW.glfwGetVideoMode(monitors.get(monitors.limit() - 1));
        this.width = vidMode.width() / 2;
        this.height = vidMode.height() / 2;

        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
        GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, GLFW.GLFW_FALSE);
        this.pointer = GLFW.glfwCreateWindow(width, height, "Vulkan Debug Window", 0, 0);

        GLFW.glfwSetFramebufferSizeCallback(pointer, this::resize);
    }

    private void resize(long pWindow, int width, int height) {
        this.resized = true;
    }

    @Override
    public void close() {
        Callbacks.glfwFreeCallbacks(pointer);
        GLFW.glfwDestroyWindow(pointer);
        GLFW.glfwTerminate();
    }
}
