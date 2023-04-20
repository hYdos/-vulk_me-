package me.hydos.vkinteropexperiments.graph;

public interface VkObjectHolder<T> {

    /**
     * Utility method for getting the vulkan object the class holds
     */
    T vk();
}
