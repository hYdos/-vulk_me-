package me.hydos.vkinteropexperiments.graph.shader;

import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;

import static me.hydos.vkinteropexperiments.graph.VkUtils.ok;

public class ShaderProgram implements Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShaderProgram.class);
    private final LogicalDevice logicalDevice;
    public final ShaderModule[] shaderModules;

    public ShaderProgram(LogicalDevice logicalDevice, ShaderModuleData[] shaderModuleData) {
        this.logicalDevice = logicalDevice;
        var moduleCount = shaderModuleData != null ? shaderModuleData.length : 0;
        shaderModules = new ShaderModule[moduleCount];

        for (var i = 0; i < moduleCount; i++) {
            var module = shaderModuleData[i];
            var moduleContents = ShaderCompiler.compile(module.path(), module.shaderStage);
            var moduleHandle = createShaderModule(moduleContents);
            shaderModules[i] = new ShaderModule(module.shaderStage(), moduleHandle);
        }
    }

    @Override
    public void close() {
        for (var shaderModule : shaderModules) VK10.vkDestroyShaderModule(logicalDevice.vk(), shaderModule.handle(), null);
    }

    private long createShaderModule(byte[] code) {
        try (var stack = MemoryStack.stackPush()) {
            var pCode = stack.malloc(code.length).put(0, code);

            var moduleCreateInfo = VkShaderModuleCreateInfo.calloc(stack)
                    .sType$Default()
                    .pCode(pCode);

            var pShaderModule = stack.mallocLong(1);
            ok(VK10.vkCreateShaderModule(logicalDevice.vk(), moduleCreateInfo, null, pShaderModule), "Failed to create shader module");
            return pShaderModule.get(0);
        }
    }

    public record ShaderModule(
            int shaderStage,
            long handle
    ) {}

    public record ShaderModuleData(
            int shaderStage,
            String path
    ) {}
}