package me.hydos.vkinteropexperiments.graph;

import org.lwjgl.vulkan.VK10;

/**
 * Rosella my beloved (I tried to avoid looking at rosella for this I think I remember there being a class called this)
 */
public class VkUtils {

    public static void ok(int vkReturnCode, String message) {
        if (vkReturnCode != VK10.VK_SUCCESS) throw new RuntimeException(message + ". Error Code " + vkReturnCode); //TODO: decode to strings
    }
}
