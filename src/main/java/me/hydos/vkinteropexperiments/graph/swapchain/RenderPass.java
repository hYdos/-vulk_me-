package me.hydos.vkinteropexperiments.graph.swapchain;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class RenderPass implements Closeable, VkObjectHolder<Long> {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderPass.class);
    private final SurfaceSwapchain swapchain;
    private final long renderPass;

    public RenderPass(SurfaceSwapchain swapchain, int depthImageFormat) {
        try (var stack = MemoryStack.stackPush()) {
            LOGGER.info("Creating RenderPass");
            this.swapchain = swapchain;
            var attachments = VkAttachmentDescription.calloc(2, stack);

            attachments.get(0)
                    .format(swapchain.surfaceFormat.imageFormat())
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_STORE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);

            attachments.get(1)
                    .format(depthImageFormat)
                    .samples(VK10.VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK10.VK_ATTACHMENT_LOAD_OP_CLEAR)
                    .storeOp(VK10.VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK10.VK_IMAGE_LAYOUT_UNDEFINED)
                    .finalLayout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            var colorReference = VkAttachmentReference.calloc(1, stack)
                    .attachment(0)
                    .layout(VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);

            var depthReference = VkAttachmentReference.malloc(stack)
                    .attachment(1)
                    .layout(VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);

            var subPass = VkSubpassDescription.calloc(1, stack)
                    .pipelineBindPoint(VK10.VK_PIPELINE_BIND_POINT_GRAPHICS)
                    .colorAttachmentCount(colorReference.remaining())
                    .pColorAttachments(colorReference)
                    .pDepthStencilAttachment(depthReference);

            var subPassDependencies = VkSubpassDependency.calloc(1, stack);
            subPassDependencies.get(0)
                    .srcSubpass(VK10.VK_SUBPASS_EXTERNAL)
                    .dstSubpass(0)
                    .srcStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .dstStageMask(VK10.VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
                    .srcAccessMask(0)
                    .dstAccessMask(VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);

            var createInfo = VkRenderPassCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(attachments)
                    .pSubpasses(subPass)
                    .pDependencies(subPassDependencies);

            var pRenderPass = stack.mallocLong(1);
            ok(VK10.vkCreateRenderPass(swapchain.logicalDevice.vk(), createInfo, null, pRenderPass), "Failed to create render pass");
            this.renderPass = pRenderPass.get(0);
        }
    }

    @Override
    public void close() {
        VK10.vkDestroyRenderPass(swapchain.logicalDevice.vk(), renderPass, null);
    }

    @Override
    public Long vk() {
        return renderPass;
    }
}
