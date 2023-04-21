package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.graph.command.CommandBuffer;
import me.hydos.vkinteropexperiments.graph.command.CommandPool;
import me.hydos.vkinteropexperiments.graph.image.ImageAttachment;
import me.hydos.vkinteropexperiments.graph.pipeline.Pipeline;
import me.hydos.vkinteropexperiments.graph.pipeline.PipelineCache;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.Queue;
import me.hydos.vkinteropexperiments.graph.shader.ShaderProgram;
import me.hydos.vkinteropexperiments.graph.swapchain.RenderPass;
import me.hydos.vkinteropexperiments.graph.swapchain.SurfaceSwapchain;
import me.hydos.vkinteropexperiments.graph.swapchain.Swapchain;
import me.hydos.vkinteropexperiments.graph.sync.Fence;
import me.hydos.vkinteropexperiments.graph.vertex.GpuModel;
import me.hydos.vkinteropexperiments.graph.vertex.VertexBufferStructure;
import me.hydos.vkinteropexperiments.scene.Scene;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class ForwardRenderActivity implements Closeable {

    private final CommandBuffer[] cmdBuffers;
    private final Fence[] fences;
    private final ShaderProgram shader;
    private final Pipeline pipeline;
    private final PipelineCache pipelineCache;
    private final RenderPass renderPass;
    private final Scene scene;
    private final LogicalDevice logicalDevice;
    private FrameBuffer[] frameBuffers;
    private ImageAttachment[] depthAttachments;
    private SurfaceSwapchain swapchain;

    public ForwardRenderActivity(SurfaceSwapchain swapchain, CommandPool cmdPool, PipelineCache pipelineCache, Scene scene) {
        this.swapchain = swapchain;
        this.pipelineCache = pipelineCache;
        this.scene = scene;
        this.logicalDevice = swapchain.logicalDevice;

        var imgCount = swapchain.imageViews.length;
        createDepthImages();
        renderPass = new RenderPass(swapchain, depthAttachments[0].vk().format);
        createFrameBuffers();

        this.shader = new ShaderProgram(logicalDevice, new ShaderProgram.ShaderModuleData[]{
                new ShaderProgram.ShaderModuleData(VK10.VK_SHADER_STAGE_VERTEX_BIT, "triangle.v.glsl"),
                new ShaderProgram.ShaderModuleData(VK10.VK_SHADER_STAGE_FRAGMENT_BIT, "triangle.f.glsl")
        });

        this.pipeline = new Pipeline(pipelineCache, new Pipeline.CreationInfo(
                renderPass.vk(),
                shader,
                1,
                true,
                Float.BYTES * 4 * 4 * 2,
                new VertexBufferStructure()
        ));

        this.cmdBuffers = new CommandBuffer[imgCount];
        this.fences = new Fence[imgCount];

        for (var i = 0; i < imgCount; i++) {
            cmdBuffers[i] = new CommandBuffer(cmdPool, true, false);
            fences[i] = new Fence(logicalDevice, true);
        }
    }

    private void createDepthImages() {
        int imageCount = swapchain.imageViews.length;
        var swapChainExtent = swapchain.extent;
        this.depthAttachments = new ImageAttachment[imageCount];
        for (int i = 0; i < imageCount; i++) {
            depthAttachments[i] = new ImageAttachment(logicalDevice, swapChainExtent.width(), swapChainExtent.height(), VK10.VK_FORMAT_D32_SFLOAT, VK10.VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT);
        }
    }

    private void createFrameBuffers() {
        try (var stack = MemoryStack.stackPush()) {
            var swapChainExtent = swapchain.extent;
            var imageViews = swapchain.imageViews;
            var numImages = imageViews.length;

            var pAttachments = stack.mallocLong(2);
            this.frameBuffers = new FrameBuffer[numImages];
            for (var i = 0; i < numImages; i++) {
                pAttachments.put(0, imageViews[i].vk());
                pAttachments.put(1, depthAttachments[i].view.vk());
                frameBuffers[i] = new FrameBuffer(logicalDevice, swapChainExtent.width(), swapChainExtent.height(), pAttachments, renderPass.vk());
            }
        }
    }

    public void recordCmdBuffer(List<GpuModel> models) {
        try (var stack = MemoryStack.stackPush()) {
            var extent = swapchain.extent;
            int width = extent.width();
            int height = extent.height();
            int idx = swapchain.currentFrame;

            Fence fence = fences[idx];
            var cmdBuffer = cmdBuffers[idx];
            var frameBuffer = frameBuffers[idx];

            fence.waitForFence();
            fence.reset();

            cmdBuffer.reset();
            var clearValues = VkClearValue.calloc(2, stack)
                    .apply(0, v -> v.color()
                            .float32(0, 1f)
                            .float32(1, 0f)
                            .float32(2, 0f)
                            .float32(3, 1f)
                    )
                    .apply(1, v -> v.depthStencil().depth(1.0f));

            var renderPassBeginInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(renderPass.vk())
                    .pClearValues(clearValues)
                    .renderArea(a -> a.extent().set(width, height))
                    .framebuffer(frameBuffer.vk());

            cmdBuffer.beginRecording();
            var cmdHandle = cmdBuffer.vk();
            VK10.vkCmdBeginRenderPass(cmdHandle, renderPassBeginInfo, VK10.VK_SUBPASS_CONTENTS_INLINE);

            VK10.vkCmdBindPipeline(cmdHandle, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.vk());

            var viewport = VkViewport.calloc(1, stack)
                    .x(0)
                    .y(height)
                    .height(-height)
                    .width(width)
                    .minDepth(0.0f)
                    .maxDepth(1.0f);
            VK10.vkCmdSetViewport(cmdHandle, 0, viewport);

            var scissor = VkRect2D.calloc(1, stack)
                    .extent(it -> it
                            .width(width)
                            .height(height)
                    ).offset(it -> it
                            .x(0)
                            .y(0)
                    );
            VK10.vkCmdSetScissor(cmdHandle, 0, scissor);

            var offsets = stack.mallocLong(1).put(0, 0L);
            var vertexBuffer = stack.mallocLong(1);

            var pushConstantBuffer = stack.malloc(Float.BYTES * 4 * 4 * 2);
            for (var model : models) {
                var name = model.name;
                var entities = scene.getEntitiesByModelId(name);
                if (entities.isEmpty()) continue;

                for (var mesh : model.gpuMeshList) {
                    vertexBuffer.put(0, mesh.verticesBuffer().buffer);
                    VK10.vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                    VK10.vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer().buffer, 0, VK10.VK_INDEX_TYPE_UINT32);

                    for (var entity : entities) {
                        setPushConstants(cmdHandle, scene.getProjection(), entity.translation, pushConstantBuffer);
                        VK10.vkCmdDrawIndexed(cmdHandle, mesh.indexCount(), 1, 0, 0, 0);
                    }
                }
            }

            VK10.vkCmdEndRenderPass(cmdHandle);
            cmdBuffer.endRecording();
        }
    }

    private void setPushConstants(VkCommandBuffer cmdBuffer, Matrix4f projMatrix, Matrix4f modelMatrix, ByteBuffer pushConstantBuffer) {
        projMatrix.get(pushConstantBuffer);
        modelMatrix.get(Float.BYTES * 4 * 4, pushConstantBuffer);
        VK10.vkCmdPushConstants(cmdBuffer, pipeline.layout, VK10.VK_SHADER_STAGE_VERTEX_BIT, 0, pushConstantBuffer);
    }

    public void resize(SurfaceSwapchain swapchain) {
        this.swapchain = swapchain;
        Arrays.stream(frameBuffers).forEach(FrameBuffer::close);
        Arrays.stream(depthAttachments).forEach(ImageAttachment::close);
        createDepthImages();
        createFrameBuffers();
    }

    public void submit(Queue queue) {
        try (var stack = MemoryStack.stackPush()) {
            var idx = swapchain.currentFrame;
            var commandBuffer = cmdBuffers[idx];
            var currentFence = fences[idx];
            var syncSemaphores = swapchain.syncSemaphores[idx];
            queue.submit(stack.pointers(commandBuffer.vk()),
                    stack.longs(syncSemaphores.imgAcquisitionSemaphore().vk()),
                    stack.ints(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT),
                    stack.longs(syncSemaphores.renderCompleteSemaphore().vk()), currentFence);
        }
    }

    @Override
    public void close() {
        pipeline.close();
        Arrays.stream(depthAttachments).forEach(ImageAttachment::close);
        shader.close();
        Arrays.stream(frameBuffers).forEach(FrameBuffer::close);
        renderPass.close();
        Arrays.stream(cmdBuffers).forEach(CommandBuffer::close);
        Arrays.stream(fences).forEach(Fence::close);
    }
}
