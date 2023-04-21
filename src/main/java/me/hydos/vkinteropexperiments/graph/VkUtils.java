package me.hydos.vkinteropexperiments.graph;

import me.hydos.vkinteropexperiments.graph.setup.PhysicalDevice;
import org.lwjgl.vulkan.*;

/**
 * Rosella my beloved (I tried to avoid looking at rosella for this I think I remember there being a class called this)
 */
public class VkUtils {

    public static void ok(int vkReturnCode, String message) {
        if (vkReturnCode != VK10.VK_SUCCESS) throw new RuntimeException(message + ". Error Code " + translateVulkanResult(vkReturnCode));
    }

    /**
     * Translates a Vulkan {@code VkResult} value to a String describing the result.
     *
     * @param result the {@code VkResult} value
     *
     * @return the result description
     */
    public static String translateVulkanResult(int result) {
        return switch (result) {
            // Success codes
            case VK10.VK_SUCCESS -> "Command successfully completed.";
            case VK10.VK_NOT_READY -> "A fence or query has not yet completed.";
            case VK10.VK_TIMEOUT -> "A wait operation has not completed in the specified time.";
            case VK10.VK_EVENT_SET -> "An event is signaled.";
            case VK10.VK_EVENT_RESET -> "An event is unsignaled.";
            case VK10.VK_INCOMPLETE -> "A return array was too small for the result.";
            case KHRSwapchain.VK_SUBOPTIMAL_KHR ->
                    "A swapchain no longer matches the surface properties exactly, but can still be used to present to the surface successfully.";

            // Error codes
            case VK10.VK_ERROR_OUT_OF_HOST_MEMORY -> "A host memory allocation has failed.";
            case VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY -> "A device memory allocation has failed.";
            case VK10.VK_ERROR_INITIALIZATION_FAILED ->
                    "Initialization of an object could not be completed for implementation-specific reasons.";
            case VK10.VK_ERROR_DEVICE_LOST -> "The logical or physical device has been lost.";
            case VK10.VK_ERROR_MEMORY_MAP_FAILED -> "Mapping of a memory object has failed.";
            case VK10.VK_ERROR_LAYER_NOT_PRESENT -> "A requested layer is not present or could not be loaded.";
            case VK10.VK_ERROR_EXTENSION_NOT_PRESENT -> "A requested extension is not supported.";
            case VK10.VK_ERROR_FEATURE_NOT_PRESENT -> "A requested feature is not supported.";
            case VK10.VK_ERROR_INCOMPATIBLE_DRIVER ->
                    "The requested version of Vulkan is not supported by the driver or is otherwise incompatible for implementation-specific reasons.";
            case VK10.VK_ERROR_TOO_MANY_OBJECTS -> "Too many objects of the type have already been created.";
            case VK10.VK_ERROR_FORMAT_NOT_SUPPORTED -> "A requested format is not supported on this device.";
            case KHRSurface.VK_ERROR_SURFACE_LOST_KHR -> "A surface is no longer available.";
            case KHRSurface.VK_ERROR_NATIVE_WINDOW_IN_USE_KHR ->
                    "The requested window is already connected to a VkSurfaceKHR, or to some other non-Vulkan API.";
            case KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR ->
                    "A surface has changed in such a way that it is no longer compatible with the swapchain, and further presentation requests using the "
                            + "swapchain will fail. Applications must query the new surface properties and recreate their swapchain if they wish to continue"
                            + "presenting to the surface.";
            case KHRDisplaySwapchain.VK_ERROR_INCOMPATIBLE_DISPLAY_KHR ->
                    "The display used by a swapchain does not use the same presentable image layout, or is incompatible in a way that prevents sharing an"
                            + " image.";
            case EXTDebugReport.VK_ERROR_VALIDATION_FAILED_EXT -> "A validation layer found an error.";
            default -> String.format("%s [%d]", "Unknown", result);
        };
    }

    public static int memoryTypeFromProperties(PhysicalDevice physicalDevice, int typeBits, int reqsMask) {
        var result = -1;
        var memoryTypes = physicalDevice.memoryProperties.memoryTypes();

        for (var i = 0; i < VK10.VK_MAX_MEMORY_TYPES; i++) {
            if ((typeBits & 1) == 1 && (memoryTypes.get(i).propertyFlags() & reqsMask) == reqsMask) {
                result = i;
                break;
            }
            typeBits >>= 1;
        }

        if (result < 0) throw new RuntimeException("Failed to find memoryType");
        return result;
    }
}
