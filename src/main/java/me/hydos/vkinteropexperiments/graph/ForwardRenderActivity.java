package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.graph.command.CommandBuffer;
import me.hydos.vkinteropexperiments.graph.command.CommandPool;
import me.hydos.vkinteropexperiments.graph.pipeline.Pipeline;
import me.hydos.vkinteropexperiments.graph.pipeline.PipelineCache;
import me.hydos.vkinteropexperiments.graph.setup.Queue;
import me.hydos.vkinteropexperiments.graph.shader.ShaderProgram;
import me.hydos.vkinteropexperiments.graph.swapchain.RenderPass;
import me.hydos.vkinteropexperiments.graph.swapchain.SurfaceSwapchain;
import me.hydos.vkinteropexperiments.graph.sync.Fence;
import me.hydos.vkinteropexperiments.graph.vertex.GpuModel;
import me.hydos.vkinteropexperiments.graph.vertex.VertexBufferStructure;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;

public class ForwardRenderActivity implements Closeable {

    private final CommandBuffer[] cmdBuffers;
    private final Fence[] fences;
    private final FrameBuffer[] frameBuffers;
    private final ShaderProgram shader;
    private final Pipeline pipeline;
    private final SurfaceSwapchain swapchain;
    private final RenderPass renderPass;

    public ForwardRenderActivity(SurfaceSwapchain swapchain, CommandPool cmdPool, PipelineCache pipelineCache) {
        try (var stack = MemoryStack.stackPush()) {
            this.swapchain = swapchain;
            var logicalDevice = swapchain.logicalDevice;
            var swapchainExtent = swapchain.extent;
            var imgViews = swapchain.imageViews;
            int imgCount = imgViews.length;
            this.renderPass = new RenderPass(swapchain);

            var pAttachments = stack.mallocLong(1);
            this.frameBuffers = new FrameBuffer[imgCount];
            for (int i = 0; i < imgCount; i++) {
                pAttachments.put(0, imgViews[i].vk());
                frameBuffers[i] = new FrameBuffer(logicalDevice, swapchainExtent.width(), swapchainExtent.height(), pAttachments, renderPass.vk());
            }

            this.shader = new ShaderProgram(logicalDevice, new ShaderProgram.ShaderModuleData[]{
                    new ShaderProgram.ShaderModuleData(VK10.VK_SHADER_STAGE_VERTEX_BIT, "triangle.v.glsl"),
                    new ShaderProgram.ShaderModuleData(VK10.VK_SHADER_STAGE_FRAGMENT_BIT, "triangle.f.glsl")
            });

            this.pipeline = new Pipeline(pipelineCache, new Pipeline.CreationInfo(
                    renderPass.vk(),
                    shader,
                    1,
                    new VertexBufferStructure()
            ));

            this.cmdBuffers = new CommandBuffer[imgCount];
            this.fences = new Fence[imgCount];

            for (var i = 0; i < imgCount; i++) {
                cmdBuffers[i] = new CommandBuffer(cmdPool, true, false);
                fences[i] = new Fence(logicalDevice, true);
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
            var clearValues = VkClearValue.calloc(1, stack).apply(0, v -> v.color()
                    .float32(0, 1f)
                    .float32(1, 0f)
                    .float32(2, 0f)
                    .float32(3, 1f)
            );

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

            for (var model : models) {
                for (var mesh : model.gpuMeshList) {
                    vertexBuffer.put(0, mesh.verticesBuffer().buffer);
                    VK10.vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                    VK10.vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer().buffer, 0, VK10.VK_INDEX_TYPE_UINT32);
                    VK10.vkCmdDrawIndexed(cmdHandle, mesh.numIndices(), 1, 0, 0, 0);
                }
            }

            VK10.vkCmdEndRenderPass(cmdHandle);
            cmdBuffer.endRecording();
        }
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
        shader.close();
        Arrays.stream(frameBuffers).forEach(FrameBuffer::close);
        renderPass.close();
        Arrays.stream(cmdBuffers).forEach(CommandBuffer::close);
        Arrays.stream(fences).forEach(Fence::close);
    }
}
