package me.hydos.vkinteropexperiments.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.hydos.vkinteropexperiments.VKInteropExperiments;
import me.hydos.vkinteropexperiments.scene.RenderEntity;
import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;

import java.util.HashMap;
import java.util.Map;

@Mixin(CreeperRenderer.class)
public abstract class CreeperRendererMixin extends MobRenderer<Creeper, CreeperModel<Creeper>> {
    private static final Map<Creeper, RenderEntity> ENTITY_MAP = new HashMap<>();

    public CreeperRendererMixin(EntityRendererProvider.Context context, CreeperModel<Creeper> entityModel, float f) {
        super(context, entityModel, f);
    }

    @Override
    public void render(Creeper entity, float entityYaw, float partialTicks, PoseStack modelViewStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, modelViewStack, buffer, packedLight);
        modelViewStack.pushPose();
        var renderEntity = ENTITY_MAP.computeIfAbsent(entity, creeper -> VKInteropExperiments.getInstance().loadTestEntity());
        modelViewStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot)));
        var transform = modelViewStack.last().pose();
        renderEntity.translation.set(transform);
        modelViewStack.popPose();
    }
}
