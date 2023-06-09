package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.debug.DebugWindow;
import me.hydos.vkinteropexperiments.debug.Surface;
import me.hydos.vkinteropexperiments.graph.cache.TextureCache;
import me.hydos.vkinteropexperiments.graph.command.CommandPool;
import me.hydos.vkinteropexperiments.graph.cache.PipelineCache;
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
import java.util.Collections;
import java.util.List;

public class Renderer implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(Renderer.class);
    public final Instance instance;
    public final PhysicalDevice physicalDevice;
    public final LogicalDevice logicalDevice;
    public final Surface surface;
    public final Queue.Graphics graphicsQueue;
    public final CommandPool cmdPool;
    private final PipelineCache pipelineCache;
    public final Queue.Present presentQueue;
    public final RendererImpl impl;
    private final List<GpuModel> models = new ArrayList<>();
    public final TextureCache textureCache = new TextureCache();
    public Swapchain swapchain;

    public Renderer(boolean enableDebug, DebugWindow window, Scene scene, Settings settings) {
        this.instance = new Instance(enableDebug, window != null);
        this.physicalDevice = PhysicalDevice.create(instance, settings.preferredDevice);
        this.logicalDevice = new LogicalDevice(physicalDevice);
        this.surface = window != null ? new Surface(physicalDevice, window.pointer) : null;
        this.graphicsQueue = new Queue.Graphics(logicalDevice, 0);
        this.presentQueue = new Queue.Present(logicalDevice, surface, 0);
        this.swapchain = window != null ? new SurfaceSwapchain(logicalDevice, surface, window, settings.imageCount, settings.vSync) : null;
        this.cmdPool = new CommandPool(logicalDevice, graphicsQueue.queueFamilyIndex);
        this.pipelineCache = new PipelineCache(logicalDevice);
        this.impl = new RendererImpl(((SurfaceSwapchain) swapchain), cmdPool, pipelineCache, scene);
    }

    public void loadModels(List<ModelData> models) {
        LOGGER.info("Loading {} models", models.size());
        this.models.addAll(GpuModel.transformModels(models, textureCache, cmdPool, graphicsQueue));
        LOGGER.info("Loaded {} models", models.size());

        this.models.forEach(m -> m.materials.sort((a, b) -> Boolean.compare(a.isTransparent(), b.isTransparent())));
        this.models.sort((a, b) -> {
            var aHasTransparentMat = a.materials.stream().anyMatch(GpuModel.Material::isTransparent);
            var bHasTransparentMat = b.materials.stream().anyMatch(GpuModel.Material::isTransparent);

            return Boolean.compare(aHasTransparentMat, bHasTransparentMat);
        });

        impl.registerModels(this.models);
    }

    public void render(DebugWindow window) {
        if (window.width <= 0 && window.height <= 0) return;
        else if (window.resized || ((SurfaceSwapchain) swapchain).acquireNextImage()) {
            window.resized = false;
            resize(window);
            ((SurfaceSwapchain) swapchain).acquireNextImage();
        }

        impl.recordCmdBuffer(models);
        impl.submit(presentQueue);

        if (((SurfaceSwapchain) swapchain).presentImage(graphicsQueue)) {
            window.resized = true;
        }
    }

    private void resize(DebugWindow window) {
        logicalDevice.waitIdle();
        graphicsQueue.waitIdle();
        var oldSwapchain = (SurfaceSwapchain) swapchain;
        oldSwapchain.close();
        swapchain = new SurfaceSwapchain(logicalDevice, surface, window, oldSwapchain.requestedImgCount, oldSwapchain.vsync);
    }

    @Override
    public void close() {
        waitFor();
        textureCache.close();
        models.forEach(GpuModel::close);
        impl.close();
        cmdPool.close();
        swapchain.close();
        surface.close();
        logicalDevice.close();
        physicalDevice.close();
        instance.close();
    }

    public void waitFor() {
        presentQueue.waitIdle();
        graphicsQueue.waitIdle();
        logicalDevice.waitIdle();
    }

    public record Settings(
            boolean vSync,
            int imageCount,
            String preferredDevice
    ) {}
}
