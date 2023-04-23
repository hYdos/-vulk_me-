package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.graph.cache.PipelineCache;
import me.hydos.vkinteropexperiments.graph.descriptor.DescriptorSetLayout;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.shader.ShaderProgram;
import me.hydos.vkinteropexperiments.graph.vertex.VertexInputStateInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class Pipeline implements Closeable, VkObjectHolder<Long> {

    private final LogicalDevice logicalDevice;
    private final long pipeline;
    public final long layout;

    public Pipeline(PipelineCache cache, CreationInfo creationInfo) {
        try (var stack = MemoryStack.stackPush()) {
            this.logicalDevice = cache.logicalDevice;
            var pp = stack.mallocLong(1);
            var shaderModules = creationInfo.shaderProgram.shaderModules;
            var moduleCount = shaderModules.length;
            var shaderStages = VkPipelineShaderStageCreateInfo.calloc(moduleCount, stack);
            for (var i = 0; i < moduleCount; i++) {
                shaderStages.get(i)
                        .sType$Default()
                        .stage(shaderModules[i].shaderStage())
                        .module(shaderModules[i].handle())
                        .pName(stack.UTF8("main"));
            }

            var inputAssemblyStateCreateInfo = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK10.VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);

            var viewportStateCreateInfo = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);

            var rasterizationStateCreateInfo = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .polygonMode(VK10.VK_POLYGON_MODE_FILL)
                    .cullMode(VK10.VK_CULL_MODE_NONE)
                    .frontFace(VK10.VK_FRONT_FACE_CLOCKWISE)
                    .lineWidth(1.0f);

            var multisampleStateCreateInfo = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(VK10.VK_SAMPLE_COUNT_1_BIT);

            var depthStencilCreateInfo = (VkPipelineDepthStencilStateCreateInfo) null;
            if (creationInfo.enableDepth) {
                depthStencilCreateInfo = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                        .sType$Default()
                        .depthTestEnable(true)
                        .depthWriteEnable(true)
                        .depthCompareOp(VK10.VK_COMPARE_OP_LESS_OR_EQUAL)
                        .depthBoundsTestEnable(false)
                        .stencilTestEnable(false);
            }

            var blendAttachmentState = VkPipelineColorBlendAttachmentState.calloc(creationInfo.colorAttachmentCount(), stack);

            for (var i = 0; i < creationInfo.colorAttachmentCount(); i++) {
                blendAttachmentState.get(i)
                        .colorWriteMask(VK10.VK_COLOR_COMPONENT_R_BIT | VK10.VK_COLOR_COMPONENT_G_BIT | VK10.VK_COLOR_COMPONENT_B_BIT | VK10.VK_COLOR_COMPONENT_A_BIT)
                        .blendEnable(creationInfo.enableBlend());

                if (creationInfo.enableBlend()) {
                    blendAttachmentState.get(i)
                            .colorBlendOp(VK10.VK_BLEND_OP_ADD)
                            .alphaBlendOp(VK10.VK_BLEND_OP_ADD)
                            .srcColorBlendFactor(VK10.VK_BLEND_FACTOR_SRC_ALPHA)
                            .dstColorBlendFactor(VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                            .srcAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ONE)
                            .dstAlphaBlendFactor(VK10.VK_BLEND_FACTOR_ZERO);
                }
            }

            var colorBlendState = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(blendAttachmentState);

            var dynamicStateCreateInfo = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(
                            VK10.VK_DYNAMIC_STATE_VIEWPORT,
                            VK10.VK_DYNAMIC_STATE_SCISSOR
                    ));

            var pushConstantRange = VkPushConstantRange.calloc(1, stack)
                    .stageFlags(VK10.VK_SHADER_STAGE_VERTEX_BIT)
                    .offset(0)
                    .size(creationInfo.pushConstantsSize);

            var descriptorSetLayouts = creationInfo.descriptorSetLayouts();
            var numLayouts = descriptorSetLayouts != null ? descriptorSetLayouts.length : 0;
            var pLayouts = stack.mallocLong(numLayouts);
            for (int i = 0; i < numLayouts; i++) pLayouts.put(i, descriptorSetLayouts[i].layout);

            var layoutCreateInfo = VkPipelineLayoutCreateInfo.calloc(stack)
                    .sType$Default()
                    .pSetLayouts(pLayouts)
                    .pPushConstantRanges(pushConstantRange);
            ok(VK10.vkCreatePipelineLayout(logicalDevice.vk(), layoutCreateInfo, null, pp), "Failed to create PipelineLayout");
            this.layout = pp.get(0);

            var pipeline = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType$Default()
                    .pStages(shaderStages)
                    .pVertexInputState(creationInfo.vertInputStateInfo.getVertexInput())
                    .pInputAssemblyState(inputAssemblyStateCreateInfo)
                    .pViewportState(viewportStateCreateInfo)
                    .pRasterizationState(rasterizationStateCreateInfo)
                    .pMultisampleState(multisampleStateCreateInfo)
                    .pColorBlendState(colorBlendState)
                    .pDynamicState(dynamicStateCreateInfo)
                    .layout(layout)
                    .renderPass(creationInfo.vkRenderPass)
                    .pDepthStencilState(depthStencilCreateInfo);

            ok(VK10.vkCreateGraphicsPipelines(logicalDevice.vk(), cache.vk(), pipeline, null, pp), "Error creating Pipeline");
            this.pipeline = pp.get(0);
        }
    }

    @Override
    public Long vk() {
        return pipeline;
    }

    @Override
    public void close() {
        VK10.vkDestroyPipelineLayout(logicalDevice.vk(), layout, null);
        VK10.vkDestroyPipeline(logicalDevice.vk(), pipeline, null);
    }

    public record CreationInfo(
            long vkRenderPass,
            ShaderProgram shaderProgram,
            int colorAttachmentCount,
            boolean enableDepth,
            boolean enableBlend,
            int pushConstantsSize,
            VertexInputStateInfo vertInputStateInfo,
            DescriptorSetLayout[] descriptorSetLayouts
    ) implements Closeable {
        @Override
        public void close() {
            vertInputStateInfo.close();
        }
    }
}
