package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.debug.DebugWindow;
import me.hydos.vkinteropexperiments.debug.Surface;
import me.hydos.vkinteropexperiments.graph.command.CommandPool;
import me.hydos.vkinteropexperiments.graph.pipeline.PipelineCache;
import me.hydos.vkinteropexperiments.graph.setup.Instance;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.PhysicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.Queue;
import me.hydos.vkinteropexperiments.graph.swapchain.SurfaceSwapchain;
import me.hydos.vkinteropexperiments.graph.swapchain.Swapchain;
import me.hydos.vkinteropexperiments.graph.vertex.GpuModel;
import me.hydos.vkinteropexperiments.scene.ModelData;
import me.hydos.vkinteropexperiments.scene.Scene;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public class Renderer implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Renderer.class);
    public final Instance instance;
    public final PhysicalDevice physicalDevice;
    public final LogicalDevice logicalDevice;
    public final Surface surface;
    public final Queue.Graphics graphicsQueue;
    public final Swapchain swapchain;
    public final CommandPool cmdPool;
    private final PipelineCache pipelineCache;
    public final Queue.Present presentQueue;
    public final ForwardRenderActivity fwdRenderActivity;
    private final List<GpuModel> models = new ArrayList<>();

    public Renderer(boolean enableDebug, DebugWindow window, Settings settings) {
        this.instance = new Instance(enableDebug, window != null);
        this.physicalDevice = PhysicalDevice.create(instance, settings.preferredDevice);
        this.logicalDevice = new LogicalDevice(physicalDevice);
        this.surface = window != null ? new Surface(physicalDevice, window.pointer) : null;
        this.graphicsQueue = new Queue.Graphics(logicalDevice, 0);
        this.presentQueue = new Queue.Present(logicalDevice, surface, 0);
        this.swapchain = window != null ? new SurfaceSwapchain(logicalDevice, surface, window, settings.imageCount, settings.vSync) : null;
        this.cmdPool = new CommandPool(logicalDevice, graphicsQueue.queueFamilyIndex);
        this.pipelineCache = new PipelineCache(logicalDevice);
        this.fwdRenderActivity = new ForwardRenderActivity(((SurfaceSwapchain) swapchain), cmdPool, pipelineCache);
    }

    public void loadModels(List<ModelData> models) {
        LOGGER.info("Loading {} model(s)", models.size());
        this.models.addAll(GpuModel.transformModels(models, cmdPool, graphicsQueue));
        LOGGER.info("Loaded {} model(s)", models.size());
    }

    public void render(DebugWindow window, Scene scene) {
        ((SurfaceSwapchain) swapchain).acquireNextImage();

        fwdRenderActivity.recordCmdBuffer(models);
        fwdRenderActivity.submit(presentQueue);

        ((SurfaceSwapchain) swapchain).presentImage(graphicsQueue);
    }

    @Override
    public void close() {
        presentQueue.waitIdle();
        graphicsQueue.waitIdle();
        logicalDevice.waitIdle();
        models.forEach(GpuModel::close);
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
