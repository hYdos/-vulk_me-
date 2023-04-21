package me.hydos.vkinteropexperiments.graph.setup;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class Instance implements Closeable, VkObjectHolder<VkInstance> {
    private static final Logger LOGGER = LoggerFactory.getLogger(Instance.class);
    private static final String KHRONOS_VALIDATION = "VK_LAYER_KHRONOS_validation";
    private static final String LUNARG_VALIDATION = "VK_LAYER_LUNARG_standard_validation";
    public static final int MESSAGE_SEVERITY_BITMASK = EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
    public static final int MESSAGE_TYPE_BITMASK = EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT | EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
    private final VkInstance instance;
    private final long pDebugCallback;
    private final VkDebugUtilsMessengerCreateInfoEXT debugCallback;

    public Instance(boolean enableDebug, boolean windowAvailable) {
        LOGGER.info("Creating VkInstance");
        try (var stack = MemoryStack.stackPush()) {
            var appInfo = VkApplicationInfo.calloc(stack)
                    .sType$Default()
                    .pApplicationName(stack.UTF8("hydos' VK/GL Interoperability Experiments"))
                    .pEngineName(stack.UTF8("N/A"))
                    .engineVersion(0)
                    .apiVersion(VK12.VK_API_VERSION_1_2);

            var requiredLayers = new ArrayList<String>();
            var requiredExtensions = new ArrayList<String>();

            if (enableDebug) requiredLayers.addAll(getSupportedValidationLayers());
            if (enableDebug) requiredExtensions.add(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);

            if (windowAvailable) {
                var glfwExtensions = GLFWVulkan.glfwGetRequiredInstanceExtensions();
                if (glfwExtensions == null) throw new RuntimeException("No GLFW surface extensions found");

                for (var i = 0; i < glfwExtensions.remaining(); i++)
                    requiredExtensions.add(MemoryUtil.memASCII(glfwExtensions.get(i)));
            }

            var pRequiredLayers = stack.mallocPointer(requiredLayers.size());
            for (var i = 0; i < requiredLayers.size(); i++) {
                LOGGER.info("Using Layer {}", requiredLayers.get(i));
                pRequiredLayers.put(i, stack.ASCII(requiredLayers.get(i)));
            }

            var pRequiredExtensions = stack.mallocPointer(requiredExtensions.size());
            for (var i = 0; i < requiredExtensions.size(); i++) {
                LOGGER.info("Using Extension {}", requiredExtensions.get(i));
                pRequiredExtensions.put(i, stack.ASCII(requiredExtensions.get(i)));
            }

            if (enableDebug) this.debugCallback = createDebugCallback();
            else this.debugCallback = null;

            var creationInfo = VkInstanceCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(debugCallback != null ? debugCallback.address() : VK10.VK_NULL_HANDLE)
                    .pApplicationInfo(appInfo)
                    .ppEnabledLayerNames(pRequiredLayers)
                    .ppEnabledExtensionNames(pRequiredExtensions);

            var pInstance = stack.mallocPointer(1);
            ok(VK10.vkCreateInstance(creationInfo, null, pInstance), "Failed creating instance");
            this.instance = new VkInstance(pInstance.get(0), creationInfo);

            if (enableDebug) {
                var pHandle = stack.mallocLong(1);
                ok(EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(instance, debugCallback, null, pHandle), "Failed creating debug utils");
                this.pDebugCallback = pHandle.get(0);
            } else this.pDebugCallback = VK10.VK_NULL_HANDLE;
        }
    }

    private static VkDebugUtilsMessengerCreateInfoEXT createDebugCallback() {
        return VkDebugUtilsMessengerCreateInfoEXT.calloc()
                .sType$Default()
                .messageSeverity(MESSAGE_SEVERITY_BITMASK)
                .messageType(MESSAGE_TYPE_BITMASK)
                .pfnUserCallback((severity, types, pCallbackData, pUserData) -> {
                    var data = VkDebugUtilsMessengerCallbackDataEXT.create(pCallbackData);
                    var message = data.pMessageString();

                    if ((severity & EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0)
                        LOGGER.info("[Vulkan] {}", message);
                    else if ((severity & EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0)
                        LOGGER.warn("[Vulkan] {}", message);
                    else if ((severity & EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0)
                        LOGGER.error("[Vulkan] {}", message);
                    else LOGGER.debug("[Vulkan] {}", message);

                    return VK10.VK_FALSE;
                });
    }

    private static List<String> getSupportedValidationLayers() {
        try (var stack = MemoryStack.stackPush()) {
            var pLayerCount = stack.callocInt(1);
            VK10.vkEnumerateInstanceLayerProperties(pLayerCount, null);
            var layerCount = pLayerCount.get(0);
            LOGGER.info("{} Vulkan layers supported", layerCount);

            var pProperties = VkLayerProperties.calloc(layerCount, stack);
            VK10.vkEnumerateInstanceLayerProperties(pLayerCount, pProperties);

            var supportedLayers = new ArrayList<String>();
            for (var i = 0; i < layerCount; i++) {
                var property = pProperties.get(i);
                supportedLayers.add(property.layerNameString());
            }

            if (supportedLayers.contains(KHRONOS_VALIDATION)) return List.of(KHRONOS_VALIDATION);
            else if (supportedLayers.contains(LUNARG_VALIDATION)) return List.of(LUNARG_VALIDATION);
            else {
                return Stream.of(
                        "VK_LAYER_GOOGLE_threading",
                        "VK_LAYER_LUNARG_parameter_validation",
                        "VK_LAYER_LUNARG_object_tracker",
                        "VK_LAYER_LUNARG_core_validation",
                        "VK_LAYER_GOOGLE_unique_objects"
                ).filter(supportedLayers::contains).toList();
            }
        }
    }

    @Override
    public void close() {
        LOGGER.info("Closing");
        if (pDebugCallback != VK10.VK_NULL_HANDLE)
            EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(instance, pDebugCallback, null);

        if (debugCallback != null) {
            debugCallback.pfnUserCallback().free();
            debugCallback.free();
        }

        VK10.vkDestroyInstance(instance, null);
    }

    @Override
    public VkInstance vk() {
        return instance;
    }
}
