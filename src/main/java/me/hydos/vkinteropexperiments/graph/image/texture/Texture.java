package me.hydos.vkinteropexperiments.graph.image.texture;

import me.hydos.vkinteropexperiments.graph.command.CommandBuffer;
import me.hydos.vkinteropexperiments.graph.image.Image;
import me.hydos.vkinteropexperiments.graph.image.ImageView;
import me.hydos.vkinteropexperiments.graph.image.ImageViewData;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.memory.VkBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferImageCopy;
import org.lwjgl.vulkan.VkImageMemoryBarrier;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.io.Closeable;
import java.nio.ByteBuffer;

public class Texture implements Closeable {

    public final Image image;
    public final ImageView view;
    public final int width;
    public final int height;
    public final int mipLevels;
    public final BufferedImage cpuTexture;
    private VkBuffer stagingBuf;
    private boolean recordedTransition;

    public Texture(LogicalDevice logicalDevice, BufferedImage image, int format, int mipLevels) {
        var imgBuffer = image.getData().getDataBuffer();
        var rgbaBuffer = (ByteBuffer) null;
        this.width = image.getWidth();
        this.height = image.getHeight();
        this.mipLevels = mipLevels;
        this.cpuTexture = image;

        if (imgBuffer instanceof DataBufferFloat intBuffer) {
            var rawData = intBuffer.getData();
            rgbaBuffer = MemoryUtil.memAlloc(rawData.length * 4);

            for (var hdrChannel : rawData) {
                var pixel = hdrToRgb(hdrChannel);
                rgbaBuffer.put((byte) ((pixel >> 16) & 0xFF));
                rgbaBuffer.put((byte) ((pixel >> 8) & 0xFF));
                rgbaBuffer.put((byte) (pixel & 0xFF));
                rgbaBuffer.put((byte) ((pixel >> 24) & 0xFF));
            }

            rgbaBuffer.flip();
        } else if (imgBuffer instanceof DataBufferInt floatBuffer) {
            var rawData = floatBuffer.getData();
            rgbaBuffer = MemoryUtil.memAlloc(rawData.length * 4);

            for (var pixel : rawData) {
                rgbaBuffer.put((byte) ((pixel >> 16) & 0xFF));
                rgbaBuffer.put((byte) ((pixel >> 8) & 0xFF));
                rgbaBuffer.put((byte) (pixel & 0xFF));
                rgbaBuffer.put((byte) ((pixel >> 24) & 0xFF));
            }

            rgbaBuffer.flip();
        } else throw new RuntimeException("Unknown Data Type: " + imgBuffer.getClass().getName());

        createStagingBuffer(logicalDevice, rgbaBuffer);

        this.image = new Image.Builder()
                .width(image.getWidth())
                .height(image.getHeight())
                .usage(VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK10.VK_IMAGE_USAGE_SAMPLED_BIT)
                .format(format)
                .mipLevels(mipLevels)
                .build(logicalDevice);

        this.view = new ImageView(logicalDevice, this.image.vk(), new ImageViewData.Builder()
                .format(this.image.format)
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .mipLevels(mipLevels)
                .build()
        );

        MemoryUtil.memFree(rgbaBuffer);
    }

    public void recordTransition(CommandBuffer cmdBuffer) {
        if (stagingBuf != null && !recordedTransition) {
            try (var stack = MemoryStack.stackPush()) {
                this.recordedTransition = true;
                recordImageTransition(stack, cmdBuffer, VK10.VK_IMAGE_LAYOUT_UNDEFINED, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
                recordCopyBuffer(stack, cmdBuffer, stagingBuf);
                recordImageTransition(stack, cmdBuffer, VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
            }
        }
    }

    private void recordCopyBuffer(MemoryStack stack, CommandBuffer cmd, VkBuffer bufferData) {
        var region = VkBufferImageCopy.calloc(1, stack)
                .bufferOffset(0)
                .bufferRowLength(0)
                .bufferImageHeight(0)
                .imageSubresource(it ->
                        it.aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                                .mipLevel(0)
                                .baseArrayLayer(0)
                                .layerCount(1)
                )
                .imageOffset(it -> it.x(0).y(0).z(0))
                .imageExtent(it -> it
                        .width(width)
                        .height(height)
                        .depth(1));

        VK10.vkCmdCopyBufferToImage(cmd.vk(), bufferData.buffer, image.vk(), VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);
    }

    private void recordImageTransition(MemoryStack stack, CommandBuffer cmd, int oldLayout, int newLayout) {
        var barrier = VkImageMemoryBarrier.calloc(1, stack)
                .sType$Default()
                .oldLayout(oldLayout)
                .newLayout(newLayout)
                .srcQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK10.VK_QUEUE_FAMILY_IGNORED)
                .image(image.vk())
                .subresourceRange(it -> it
                        .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                        .baseMipLevel(0)
                        .levelCount(mipLevels)
                        .baseArrayLayer(0)
                        .layerCount(1));

        int srcStage;
        int srcAccessMask;
        int dstAccessMask;
        int dstStage;

        if (oldLayout == VK10.VK_IMAGE_LAYOUT_UNDEFINED && newLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            srcStage = VK10.VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT;
            srcAccessMask = 0;
            dstStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
            dstAccessMask = VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
        } else if (oldLayout == VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL && newLayout == VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            srcStage = VK10.VK_PIPELINE_STAGE_TRANSFER_BIT;
            srcAccessMask = VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
            dstStage = VK10.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT;
            dstAccessMask = VK10.VK_ACCESS_SHADER_READ_BIT;
        } else {
            throw new RuntimeException("Unsupported layout transition");
        }

        barrier
                .srcAccessMask(srcAccessMask)
                .dstAccessMask(dstAccessMask);
        VK10.vkCmdPipelineBarrier(cmd.vk(), srcStage, dstStage, 0, null, null, barrier);
    }

    private void createStagingBuffer(LogicalDevice device, ByteBuffer data) {
        var size = data.remaining();
        this.stagingBuf = new VkBuffer(device, size, VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var mappedMemory = stagingBuf.map();
        var buffer = MemoryUtil.memByteBuffer(mappedMemory, (int) stagingBuf.requestedSize);
        buffer.put(data);
        data.flip();
        stagingBuf.unMap();
    }

    private static int hdrToRgb(float hdr) {
        return (int) Math.min(Math.max(Math.pow(hdr, 1.0 / 2.2) * 255, 0), 255);
    }

    public void closeStagingBuffer() {
        if (stagingBuf != null) stagingBuf.close();
    }

    @Override
    public void close() {
        view.close();
        image.close();
    }
}
