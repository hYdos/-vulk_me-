package me.hydos.vkinteropexperiments;

import com.thepokecraftmod.rks.assimp.AssimpModelLoader;
import me.hydos.vkinteropexperiments.debug.DebugWindow;
import me.hydos.vkinteropexperiments.graph.Renderer;
import me.hydos.vkinteropexperiments.scene.ModelData;
import me.hydos.vkinteropexperiments.scene.RenderEntity;
import me.hydos.vkinteropexperiments.scene.Scene;
import org.joml.Vector3f;
import org.lwjgl.assimp.Assimp;
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
        System.loadLibrary("renderdoc");
        INSTANCE = this;
        this.debugWindow = new DebugWindow();
        this.testScene = new Scene();
        this.renderer = new Renderer(true, debugWindow, testScene, new Renderer.Settings(
                false,
                3,
                "NVIDIA GeForce RTX 2070 SUPER"
        ));
    }

    public RenderEntity loadTestEntity() {
        var locator = new ResourceCachedFileLocator("rayquaza");
        var model = AssimpModelLoader.load("model.gltf", locator, Assimp.aiProcess_GenNormals | Assimp.aiProcess_LimitBoneWeights);
        var materialUploader = new MaterialUploader(model, locator, renderer);

        var meshes = new ArrayList<ModelData.MeshData>();
        for (var mesh : model.meshes()) {
            var positions = new float[mesh.positions().size() * 3];
            for (var i = 0; i < mesh.positions().size(); i++) {
                var pos = mesh.positions().get(i);
                positions[i * 3 + 0] = pos.x;
                positions[i * 3 + 1] = pos.y;
                positions[i * 3 + 2] = pos.z;
            }

            var uvs = new float[mesh.uvs().size() * 2];
            for (var i = 0; i < mesh.uvs().size(); i++) {
                var uv = mesh.uvs().get(i);
                uvs[i * 2 + 0] = uv.x;
                uvs[i * 2 + 1] = uv.y;
            }

            var indices = new int[mesh.indices().size()];
            for (var i = 0; i < mesh.indices().size(); i++) indices[i] = mesh.indices().get(i);

            meshes.add(new ModelData.MeshData(
                    positions,
                    uvs,
                    indices,
                    0
            ));
        }
        var modelData = new ModelData("TestModel", materialUploader.materials.values().stream()
                .map(material -> material.textures.get(0))
                .toList(), meshes);
        renderer.loadModels(List.of(modelData));

        var entity = new RenderEntity("TestEntity", "TestModel", new Vector3f(0, 0, -2));
        testScene.addEntity(entity);
        return entity;
    }

    public void render() {
        GLFW.glfwPollEvents();
        renderer.render(debugWindow);
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
