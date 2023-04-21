package me.hydos.vkinteropexperiments;

import me.hydos.vkinteropexperiments.debug.DebugWindow;
import me.hydos.vkinteropexperiments.graph.Renderer;
import me.hydos.vkinteropexperiments.scene.ModelData;
import me.hydos.vkinteropexperiments.scene.Scene;
import org.lwjgl.glfw.GLFW;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

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

        var meshData = new ModelData.MeshData(new float[]{
                -0.5f, -0.5f, 0.0f,
                0.0f, 0.5f, 0.0f,
                0.5f, -0.5f, 0.0f
        }, new int[]{0, 1, 2});
        var modelData = new ModelData("TestTriangle", List.of(meshData));
        renderer.loadModels(List.of(modelData));
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
