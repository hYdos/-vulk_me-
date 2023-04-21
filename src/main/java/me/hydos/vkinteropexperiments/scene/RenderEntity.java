package me.hydos.vkinteropexperiments.scene;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class RenderEntity {

    public final String name;
    public final String model;
    public final Matrix4f translation = new Matrix4f();

    public RenderEntity(String name, String model, Vector3f position) {
        this.name = name;
        this.model = model;
        translation.translation(position);
    }
}
