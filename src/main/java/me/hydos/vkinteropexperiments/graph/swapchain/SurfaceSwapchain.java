package me.hydos.vkinteropexperiments.graph.swapchain;

import me.hydos.vkinteropexperiments.debug.DebugWindow;
import me.hydos.vkinteropexperiments.debug.Surface;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.PhysicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.Queue;
import me.hydos.vkinteropexperiments.graph.sync.Semaphore;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.Arrays;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class SurfaceSwapchain implements Swapchain {
    private static final Logger LOGGER = LoggerFactory.getLogger(SurfaceSwapchain.class);
    public final LogicalDevice logicalDevice;
    public final ImageView[] imageViews;
    public final SyncSemaphores[] syncSemaphores;
    public final SurfaceFormat surfaceFormat;
    public final long swapchain;
    public final VkExtent2D extent;
    public int currentFrame;

    public SurfaceSwapchain(LogicalDevice logicalDevice, Surface surface, DebugWindow window, int requestedImgCount, boolean enableVsync) {
        LOGGER.info("Creating Window based Swapchain");
        this.logicalDevice = logicalDevice;

        try (var stack = MemoryStack.stackPush()) {
            var physicalDevice = logicalDevice.physicalDevice;
            var surfaceCaps = VkSurfaceCapabilitiesKHR.calloc(stack);
            ok(KHRSurface.vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice.vk(), surface.vk(), surfaceCaps), "Failed to get surface capabilities");
            var imageCount = getImageCount(surfaceCaps, requestedImgCount);
            this.surfaceFormat = getSurfaceFormat(physicalDevice, surface);
            this.extent = getExtent(window, surfaceCaps);

            var createInfo = VkSwapchainCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .surface(surface.vk())
                    .minImageCount(imageCount)
                    .imageFormat(surfaceFormat.imageFormat())
                    .imageColorSpace(surfaceFormat.colorSpace())
                    .imageExtent(extent)
                    .imageArrayLayers(1)
                    .imageUsage(VK10.VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                    .imageSharingMode(VK10.VK_SHARING_MODE_EXCLUSIVE)
                    .preTransform(surfaceCaps.currentTransform())
                    .compositeAlpha(KHRSurface.VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .clipped(true)
                    .presentMode(enableVsync ? KHRSurface.VK_PRESENT_MODE_FIFO_KHR : KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR);

            var pSwapchain = stack.mallocLong(1);
            ok(KHRSwapchain.vkCreateSwapchainKHR(logicalDevice.vk(), createInfo, null, pSwapchain), "Failed to create Swapchain");
            this.swapchain = pSwapchain.get(0);
            this.imageViews = createImageViews(stack, logicalDevice, swapchain, surfaceFormat.imageFormat());
            this.syncSemaphores = new SyncSemaphores[imageCount];
            for (var i = 0; i < imageCount; i++) syncSemaphores[i] = new SyncSemaphores(logicalDevice);
            this.currentFrame = 0;
        }
    }

    public boolean acquireNextImage() {
        try (var stack = MemoryStack.stackPush()) {
            var resize = false;
            var pFrame = stack.mallocInt(1);

            var result = KHRSwapchain.vkAcquireNextImageKHR(logicalDevice.vk(), swapchain, ~0L, syncSemaphores[currentFrame].imgAcquisitionSemaphore().vk(), MemoryUtil.NULL, pFrame);
            switch (result) {
                case KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR -> resize = true;
                case KHRSwapchain.VK_SUBOPTIMAL_KHR -> {} // Not optimal but swapchain can still be used
                default -> ok(result, "Failed to acquire image");
            }

            currentFrame = pFrame.get(0);
            return resize;
        }
    }

    public boolean presentImage(Queue queue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var resize = false;
            var present = VkPresentInfoKHR.calloc(stack)
                    .sType(KHRSwapchain.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR)
                    .pWaitSemaphores(stack.longs(syncSemaphores[currentFrame].renderCompleteSemaphore().vk()))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(swapchain))
                    .pImageIndices(stack.ints(currentFrame));

            var result = KHRSwapchain.vkQueuePresentKHR(queue.vk(), present);
            switch (result) {
                case KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR -> resize = true;
                case KHRSwapchain.VK_SUBOPTIMAL_KHR -> {} // Not optimal but swapchain can still be used
                default -> ok(result, "Failed to present KHR");
            }

            currentFrame = (currentFrame + 1) % imageViews.length;
            return resize;
        }
    }

    @Override
    public Long vk() {
        return swapchain;
    }

    @Override
    public void close() {
        LOGGER.info("Closing SurfaceSwapchain");
        Arrays.stream(imageViews).forEach(ImageView::close);
        Arrays.stream(syncSemaphores).forEach(SyncSemaphores::close);
        KHRSwapchain.vkDestroySwapchainKHR(logicalDevice.vk(), swapchain, null);
    }

    private static int getImageCount(VkSurfaceCapabilitiesKHR surfaceCaps, int requestedImgCount) {
        var maxImages = surfaceCaps.maxImageCount();
        var minImages = surfaceCaps.minImageCount();
        var result = minImages;
        if (maxImages != 0) result = Math.min(requestedImgCount, maxImages);
        result = Math.max(result, minImages);
        LOGGER.info("Requested {} images, got {} images", requestedImgCount, result);
        return result;
    }

    private static SurfaceFormat getSurfaceFormat(PhysicalDevice physicalDevice, Surface surface) {
        try (var stack = MemoryStack.stackPush()) {
            var pInt = stack.mallocInt(1);
            ok(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.vk(), surface.vk(), pInt, null), "Failed to get surface format count");
            var formatCount = pInt.get(0);
            if (formatCount <= 0) throw new RuntimeException("No Available surface formats");

            var surfaceFormats = VkSurfaceFormatKHR.calloc(formatCount, stack);
            ok(KHRSurface.vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice.vk(), surface.vk(), pInt, surfaceFormats), "Failed to get surface formats");

            var imageFormat = VK10.VK_FORMAT_B8G8R8A8_SRGB;
            var colorSpace = surfaceFormats.get(0).colorSpace();

            for (var i = 0; i < formatCount; i++) {
                var surfaceFormatKHR = surfaceFormats.get(i);
                if (surfaceFormatKHR.format() == VK10.VK_FORMAT_B8G8R8A8_SRGB && surfaceFormatKHR.colorSpace() == KHRSurface.VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    imageFormat = surfaceFormatKHR.format();
                    colorSpace = surfaceFormatKHR.colorSpace();
                    break;
                }
            }

            return new SurfaceFormat(imageFormat, colorSpace);
        }
    }

    private static VkExtent2D getExtent(DebugWindow window, VkSurfaceCapabilitiesKHR surfaceCaps) {
        if (surfaceCaps.currentExtent().width() == 0xFFFFFFFF) {
            var width = Math.min(window.width, surfaceCaps.maxImageExtent().width());
            width = Math.max(width, surfaceCaps.minImageExtent().width());
            var height = Math.min(window.height, surfaceCaps.maxImageExtent().height());
            height = Math.max(height, surfaceCaps.minImageExtent().height());

            return VkExtent2D.calloc()
                    .width(width)
                    .height(height);
        } else return VkExtent2D.calloc().set(surfaceCaps.currentExtent());
    }

    private static ImageView[] createImageViews(MemoryStack stack, LogicalDevice logicalDevice, long swapchain, int imageFormat) {
        var pImageCount = stack.mallocInt(1);
        ok(KHRSwapchain.vkGetSwapchainImagesKHR(logicalDevice.vk(), swapchain, pImageCount, null), "Failed to get number of surface images");
        int imageCount = pImageCount.get(0);

        var pSwapchainImages = stack.mallocLong(imageCount);
        ok(KHRSwapchain.vkGetSwapchainImagesKHR(logicalDevice.vk(), swapchain, pImageCount, pSwapchainImages), "Failed to get surface images");

        var images = new ImageView[imageCount];
        var imageViewData = new ImageViewData.Builder()
                .format(imageFormat)
                .aspectMask(VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                .build();

        for (var i = 0; i < imageCount; i++)
            images[i] = new ImageView(logicalDevice, pSwapchainImages.get(i), imageViewData);
        return images;
    }

    public record SyncSemaphores(Semaphore imgAcquisitionSemaphore, Semaphore renderCompleteSemaphore) implements Closeable {

        public SyncSemaphores(LogicalDevice device) {
            this(new Semaphore(device), new Semaphore(device));
        }

        @Override
        public void close() {
            imgAcquisitionSemaphore.close();
            renderCompleteSemaphore.close();
        }
    }
}
