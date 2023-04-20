package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.debug.DebugWindow;
import me.hydos.vkinteropexperiments.debug.Surface;
import me.hydos.vkinteropexperiments.graph.command.CommandPool;
import me.hydos.vkinteropexperiments.graph.setup.Instance;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.PhysicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.Queue;
import me.hydos.vkinteropexperiments.graph.swapchain.SurfaceSwapchain;
import me.hydos.vkinteropexperiments.graph.swapchain.Swapchain;
import me.hydos.vkinteropexperiments.scene.Scene;

import java.io.Closeable;

public class Renderer implements Closeable {

    public final Instance instance;
    public final PhysicalDevice physicalDevice;
    public final LogicalDevice logicalDevice;
    public final Surface surface;
    public final Queue.Graphics graphicsQueue;
    public final Swapchain swapchain;
    public final CommandPool cmdPool;
    public final Queue.Present presentQueue;
    public final ForwardRenderActivity fwdRenderActivity;

    public Renderer(boolean enableDebug, DebugWindow window, Settings settings) {
        this.instance = new Instance(enableDebug, window != null);
        this.physicalDevice = PhysicalDevice.create(instance, settings.preferredDevice);
        this.logicalDevice = new LogicalDevice(physicalDevice);
        this.surface = window != null ? new Surface(physicalDevice, window.pointer) : null;
        this.graphicsQueue = new Queue.Graphics(logicalDevice, 0);
        this.presentQueue = new Queue.Present(logicalDevice, surface, 0);
        this.swapchain = window != null ? new SurfaceSwapchain(logicalDevice, surface, window, settings.imageCount, settings.vSync) : null;
        this.cmdPool = new CommandPool(logicalDevice, graphicsQueue.queueFamilyIndex);
        this.fwdRenderActivity = new ForwardRenderActivity(((SurfaceSwapchain) swapchain), cmdPool);
    }

    public void render(DebugWindow window, Scene scene) {
        ((SurfaceSwapchain) swapchain).acquireNextImage();
        fwdRenderActivity.submit(presentQueue);
        ((SurfaceSwapchain) swapchain).presentImage(graphicsQueue);
    }

    @Override
    public void close() {
        presentQueue.waitIdle();
        graphicsQueue.waitIdle();
        logicalDevice.waitIdle();
        fwdRenderActivity.close();
        cmdPool.close();
        swapchain.close();
        surface.close();
        logicalDevice.close();
        physicalDevice.close();
        instance.close();
    }

    public record Settings(
            boolean vSync,
            int imageCount,
            String preferredDevice
    ) {}
}
