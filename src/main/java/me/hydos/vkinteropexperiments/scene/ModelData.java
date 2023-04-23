package me.hydos.vkinteropexperiments.scene;

import java.awt.image.BufferedImage;
import java.util.List;

public record ModelData(
        String name,
        List<BufferedImage> materials,
        List<MeshData> meshes
) {
    public record MeshData(
            float[] positions,
            float[] uvs,
            int[] indices,
            int materialIdx
    ) {}
}