package me.hydos.vkinteropexperiments.graph.swapchain;

import me.hydos.vkinteropexperiments.graph.VkObjectHolder;

import java.io.Closeable;

public interface Swapchain extends Closeable, VkObjectHolder<Long> {

    @Override
    void close();
}
