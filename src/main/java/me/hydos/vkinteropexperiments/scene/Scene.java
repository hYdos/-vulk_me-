package me.hydos.vkinteropexperiments.scene;

import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scene {

    public final Map<String, List<RenderEntity>> entityMap = new HashMap<>();

    public void addEntity(RenderEntity entity) {
        var entities = entityMap.get(entity.name);
        if (entities == null) {
            entities = new ArrayList<RenderEntity>();
            entityMap.put(entity.model, entities);
        }

        entities.add(entity);
    }

    public List<RenderEntity> getEntitiesByModelId(String modelId) {
        return entityMap.get(modelId);
    }

    public void clear() {
        entityMap.clear();
    }

    public void removeEntity(RenderEntity entity) {
        var entities = entityMap.get(entity.model);
        if (entities != null) entities.removeIf(e -> e.model.equals(entity.name));
    }

    public Matrix4f getProjection() {
        return RenderSystem.getProjectionMatrix();
    }
}
