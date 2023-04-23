package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.graph.command.CommandBuffer;
import me.hydos.vkinteropexperiments.graph.command.CommandPool;
import me.hydos.vkinteropexperiments.graph.descriptor.DescriptorPool;
import me.hydos.vkinteropexperiments.graph.descriptor.DescriptorSet;
import me.hydos.vkinteropexperiments.graph.descriptor.DescriptorSetLayout;
import me.hydos.vkinteropexperiments.graph.descriptor.TextureDescriptorSet;
import me.hydos.vkinteropexperiments.graph.image.ImageAttachment;
import me.hydos.vkinteropexperiments.graph.cache.PipelineCache;
import me.hydos.vkinteropexperiments.graph.image.texture.Texture;
import me.hydos.vkinteropexperiments.graph.image.texture.TextureSampler;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.Queue;
import me.hydos.vkinteropexperiments.graph.shader.ShaderProgram;
import me.hydos.vkinteropexperiments.graph.swapchain.RenderPass;
import me.hydos.vkinteropexperiments.graph.swapchain.SurfaceSwapchain;
import me.hydos.vkinteropexperiments.graph.sync.Fence;
import me.hydos.vkinteropexperiments.graph.vertex.GpuModel;
import me.hydos.vkinteropexperiments.graph.vertex.VertexBufferStructure;
import me.hydos.vkinteropexperiments.memory.VkBuffer;
import me.hydos.vkinteropexperiments.scene.Scene;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.*;

public class RendererImpl implements Closeable {

    private final CommandBuffer[] cmdBuffers;
    private final Fence[] fences;
    private final ShaderProgram shader;
    private final Pipeline pipeline;
    private final RenderPass renderPass;
    private final Scene scene;
    private final LogicalDevice logicalDevice;
    private FrameBuffer[] frameBuffers;
    private ImageAttachment[] depthAttachments;
    private SurfaceSwapchain swapchain;
    private DescriptorPool descriptorPool;
    private DescriptorSetLayout[] descriptorSetLayouts;
    private Map<BufferedImage, TextureDescriptorSet> descriptorSetMap;
    private DescriptorSet.UniformDescriptorSet projMatrixDescriptorSet;
    private VkBuffer projMatrixUniform;
    private DescriptorSetLayout.SamplerDescriptorSetLayout textureDescriptorSetLayout;
    private TextureSampler textureSampler;
    private DescriptorSetLayout.UniformDescriptorSetLayout uniformDescriptorSetLayout;

    public RendererImpl(SurfaceSwapchain swapchain, CommandPool cmdPool, PipelineCache pipelineCache, Scene scene) {
        this.swapchain = swapchain;
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

        createDescriptorSets();

        this.pipeline = new Pipeline(pipelineCache, new Pipeline.CreationInfo(
                renderPass.vk(),
                shader,
                1,
                true,
                Float.BYTES * 4 * 4,
                new VertexBufferStructure(),
                descriptorSetLayouts
        ));

        this.cmdBuffers = new CommandBuffer[imgCount];
        this.fences = new Fence[imgCount];

        for (var i = 0; i < imgCount; i++) {
            cmdBuffers[i] = new CommandBuffer(cmdPool, true, false);
            fences[i] = new Fence(logicalDevice, true);
        }
    }

    private void createDescriptorSets() {
        this.uniformDescriptorSetLayout = new DescriptorSetLayout.UniformDescriptorSetLayout(logicalDevice, 0, VK10.VK_SHADER_STAGE_VERTEX_BIT);
        this.textureDescriptorSetLayout = new DescriptorSetLayout.SamplerDescriptorSetLayout(logicalDevice, 0, VK10.VK_SHADER_STAGE_FRAGMENT_BIT);
        this.descriptorSetLayouts = new DescriptorSetLayout[]{
                uniformDescriptorSetLayout,
                textureDescriptorSetLayout,
        };

        var descriptorTypeCounts = new ArrayList<DescriptorPool.DescriptorTypeCount>();
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        descriptorTypeCounts.add(new DescriptorPool.DescriptorTypeCount(1, VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        this.descriptorPool = new DescriptorPool(logicalDevice, descriptorTypeCounts);
        this.descriptorSetMap = new HashMap<>();
        this.textureSampler = new TextureSampler(logicalDevice, 1);
        this.projMatrixUniform = new VkBuffer(logicalDevice, Float.BYTES * 4 * 4, VK10.VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT, VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT);
        this.projMatrixDescriptorSet = new DescriptorSet.UniformDescriptorSet(descriptorPool, uniformDescriptorSetLayout, projMatrixUniform, 0);
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

            var descriptorSets = stack.mallocLong(2).put(0, projMatrixDescriptorSet.vk());

            for (var model : models) {
                var name = model.name;
                var entities = scene.getEntitiesByModelId(name);
                if (entities.isEmpty()) continue;

                for (var material : model.materials) {
                    if (material.meshes().isEmpty()) continue;

                    var textureDescriptorSet = descriptorSetMap.get(material.texture().cpuTexture);
                    descriptorSets.put(1, textureDescriptorSet.vk());

                    for (var mesh : material.meshes()) {
                        vertexBuffer.put(0, mesh.verticesBuffer().buffer);
                        VK10.vkCmdBindVertexBuffers(cmdHandle, 0, vertexBuffer, offsets);
                        VK10.vkCmdBindIndexBuffer(cmdHandle, mesh.indicesBuffer().buffer, 0, VK10.VK_INDEX_TYPE_UINT32);

                        for (var entity : entities) {
                            VK10.vkCmdBindDescriptorSets(cmdHandle, VK10.VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.layout, 0, descriptorSets, null);
                            VkUtils.setMatrixAsPushConstant(pipeline, cmdHandle, entity.translation);
                            VK10.vkCmdDrawIndexed(cmdHandle, mesh.indexCount(), 1, 0, 0, 0);
                        }
                    }
                }
            }

            VK10.vkCmdEndRenderPass(cmdHandle);
            cmdBuffer.endRecording();
        }
    }

    public void registerModels(List<GpuModel> models) {
        logicalDevice.waitIdle();

        for (var vulkanModel : models) {
            for (var material : vulkanModel.materials) {
                if (material.meshes().isEmpty()) continue;
                updateTextureDescriptorSet(material.texture());
            }
        }
    }

    private void updateTextureDescriptorSet(Texture texture) {
        var cpuReference = texture.cpuTexture;
        descriptorSetMap.computeIfAbsent(cpuReference, image -> new TextureDescriptorSet(descriptorPool, textureDescriptorSetLayout, texture, textureSampler, 0));
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
            VkUtils.copyMatrix(projMatrixUniform, scene.getProjection());
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
        projMatrixUniform.close();
        textureSampler.close();
        descriptorPool.close();
        Arrays.stream(descriptorSetLayouts).forEach(DescriptorSetLayout::close);
        pipeline.close();
        Arrays.stream(depthAttachments).forEach(ImageAttachment::close);
        shader.close();
        Arrays.stream(frameBuffers).forEach(FrameBuffer::close);
        renderPass.close();
        Arrays.stream(cmdBuffers).forEach(CommandBuffer::close);
        Arrays.stream(fences).forEach(Fence::close);
    }
}
