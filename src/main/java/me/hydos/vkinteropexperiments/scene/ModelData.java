package me.hydos.vkinteropexperiments.scene;

import java.util.List;

public record ModelData(
        String name,
        List<MeshData> meshDataList
) {
    public record MeshData(
            float[] positions,
            int[] indices
    ) {}
}