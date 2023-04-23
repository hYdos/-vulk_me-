package me.hydos.vkinteropexperiments.mixin;

import me.hydos.vkinteropexperiments.VKInteropExperiments;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.vehicle.Minecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "close", at = @At("HEAD"))
    private void closeVkRenderer(CallbackInfo ci) {
        VKInteropExperiments.getInstance().close();
    }
}
