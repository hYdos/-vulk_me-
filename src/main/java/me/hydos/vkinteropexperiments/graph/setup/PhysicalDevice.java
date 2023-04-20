package me.hydos.vkinteropexperiments.graph.setup;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK10.vkGetPhysicalDeviceMemoryProperties;

public class PhysicalDevice implements Closeable, VkObjectHolder<VkPhysicalDevice> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhysicalDevice.class);
    public final VkExtensionProperties.Buffer deviceExtensions;
    public final VkPhysicalDeviceMemoryProperties memoryProperties;
    private final VkPhysicalDevice physicalDevice;
    public final VkPhysicalDeviceFeatures physicalDeviceFeatures;
    public final VkPhysicalDeviceProperties physicalDeviceProperties;
    public final VkQueueFamilyProperties.Buffer queueFamilyProps;

    public PhysicalDevice(VkPhysicalDevice physicalDevice) {
        try (var stack = MemoryStack.stackPush()) {
            this.physicalDevice = physicalDevice;
            var pp = stack.mallocInt(1);

            this.physicalDeviceProperties = VkPhysicalDeviceProperties.calloc();
            vkGetPhysicalDeviceProperties(physicalDevice, physicalDeviceProperties);

            ok(vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, pp, null), "Failed to get extension property count");
            this.deviceExtensions = VkExtensionProperties.calloc(pp.get(0));
            ok(vkEnumerateDeviceExtensionProperties(physicalDevice, (String) null, pp, deviceExtensions), "Failed to get extension properties");

            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pp, null);
            this.queueFamilyProps = VkQueueFamilyProperties.calloc(pp.get(0));
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, pp, queueFamilyProps);

            this.physicalDeviceFeatures = VkPhysicalDeviceFeatures.calloc();
            vkGetPhysicalDeviceFeatures(physicalDevice, physicalDeviceFeatures);

            this.memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
        }
    }

    public String getName() {
        return physicalDeviceProperties.deviceNameString();
    }

    private boolean hasGraphicsQueueFamily() {
        var result = false;
        var queueFamilyCount = queueFamilyProps != null ? queueFamilyProps.capacity() : 0;

        for (var i = 0; i < queueFamilyCount; i++) {
            var familyProps = queueFamilyProps.get(i);
            if ((familyProps.queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) {
                result = true;
                break;
            }
        }
        return result;
    }

    private boolean hasKHRSwapChainExtension() {
        var result = false;
        var extensionCount = deviceExtensions != null ? deviceExtensions.capacity() : 0;

        for (var i = 0; i < extensionCount; i++) {
            var extensionName = deviceExtensions.get(i).extensionNameString();
            if (KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME.equals(extensionName)) {
                result = true;
                break;
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return "PhysicalDevice{" + getName() + "}";
    }

    public static PhysicalDevice create(Instance instance, String preferredDevice) {
        LOGGER.info("Selecting physical devices");

        try (var stack = MemoryStack.stackPush()) {
            var selectedPhysicalDevice = (PhysicalDevice) null;

            // Get available devices
            var pPhysicalDevices = getPhysicalDevices(instance, stack);
            int deviceCount = pPhysicalDevices != null ? pPhysicalDevices.capacity() : 0;
            if (deviceCount <= 0) {
                throw new RuntimeException("No physical devices found");
            }

            var devices = new ArrayList<PhysicalDevice>();
            for (int i = 0; i < deviceCount; i++) {
                var vkPhysicalDevice = new VkPhysicalDevice(pPhysicalDevices.get(i), instance.vk());
                var physicalDevice = new PhysicalDevice(vkPhysicalDevice);

                var deviceName = physicalDevice.getName();
                if (physicalDevice.hasGraphicsQueueFamily() && physicalDevice.hasKHRSwapChainExtension()) {
                    LOGGER.info("{} supports required extensions", deviceName);
                    if (deviceName.equals(preferredDevice)) {
                        selectedPhysicalDevice = physicalDevice;
                        break;
                    }
                    devices.add(physicalDevice);
                } else {
                    LOGGER.info("{} does not support required extensions", deviceName);
                    physicalDevice.close();
                }
            }

            // No preferences. Pick the first. TODO: maybe do some performance characteristic weighting to find the best option. This will only be a problem between integrated vs dedicated
            selectedPhysicalDevice = selectedPhysicalDevice == null && !devices.isEmpty() ? devices.remove(0) : selectedPhysicalDevice;
            for (var physicalDevice : devices) physicalDevice.close();
            if (selectedPhysicalDevice == null) throw new RuntimeException("No suitable physical devices found");

            return selectedPhysicalDevice;
        }
    }

    private static PointerBuffer getPhysicalDevices(Instance instance, MemoryStack stack) {
        var pDeviceCount = stack.mallocInt(1);
        ok(VK10.vkEnumeratePhysicalDevices(instance.vk(), pDeviceCount, null), "Failed to get physical device count");
        var deviceCount = pDeviceCount.get(0);
        LOGGER.info("Found {} available devices", deviceCount);

        var pPhysicalDevices = stack.mallocPointer(deviceCount);
        ok(VK10.vkEnumeratePhysicalDevices(instance.vk(), pDeviceCount, pPhysicalDevices), "Failed to get physical devices");
        return pPhysicalDevices;
    }

    @Override
    public void close() {
        LOGGER.info("Closing " + getName());
        memoryProperties.free();
        physicalDeviceFeatures.free();
        queueFamilyProps.free();
        deviceExtensions.free();
        physicalDeviceProperties.free();
    }

    @Override
    public VkPhysicalDevice vk() {
        return physicalDevice;
    }
}
