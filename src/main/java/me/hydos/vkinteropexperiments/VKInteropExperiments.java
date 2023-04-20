package me.hydos.vkinteropexperiments;

import me.hydos.vkinteropexperiments.debug.DebugWindow;
import me.hydos.vkinteropexperiments.graph.Renderer;
import me.hydos.vkinteropexperiments.scene.Scene;
import org.lwjgl.glfw.GLFW;

import java.io.Closeable;

public class VKInteropExperiments implements Closeable {
    public static VKInteropExperiments INSTANCE;
    public Renderer renderer;
    public Scene testScene;
    public DebugWindow debugWindow;

    public VKInteropExperiments() {
        INSTANCE = this;
        this.debugWindow = new DebugWindow();
        this.renderer = new Renderer(true, debugWindow, new Renderer.Settings(
                false,
                3,
                "NVIDIA GeForce RTX 2070 SUPER"
        ));
        this.testScene = new Scene();
    }

    public void render() {
        GLFW.glfwPollEvents();
        renderer.render(debugWindow, testScene);
    }

    public static void main(String[] args) {
        var experiments = new VKInteropExperiments();
        while (!GLFW.glfwWindowShouldClose(experiments.debugWindow.pointer)) experiments.render();
        experiments.close();
    }

    public static VKInteropExperiments getInstance() {
        return INSTANCE;
    }

    @Override
    public void close() {
        if (debugWindow != null) debugWindow.close();
        renderer.close();
    }
}
