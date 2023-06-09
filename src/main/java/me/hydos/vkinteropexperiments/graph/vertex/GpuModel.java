package me.hydos.vkinteropexperiments.graph.vertex;

import me.hydos.vkinteropexperiments.graph.cache.TextureCache;
import me.hydos.vkinteropexperiments.graph.command.CommandBuffer;
import me.hydos.vkinteropexperiments.graph.command.CommandPool;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.Queue;
import me.hydos.vkinteropexperiments.graph.sync.Fence;
import me.hydos.vkinteropexperiments.graph.image.texture.Texture;
import me.hydos.vkinteropexperiments.memory.VkBuffer;
import me.hydos.vkinteropexperiments.scene.ModelData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;

import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public class GpuModel implements Closeable {

    public final String name;
    public final List<Material> materials = new ArrayList<>();

    public GpuModel(String name) {
        this.name = name;
    }

    private static TransferBuffers createIndexBuffers(LogicalDevice logicalDevice, ModelData.MeshData meshData) {
        int[] indices = meshData.indices();
        int numIndices = indices.length;
        int bufferSize = numIndices * Integer.BYTES;

        VkBuffer srcBuffer = new VkBuffer(logicalDevice, bufferSize, VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        VkBuffer dstBuffer = new VkBuffer(logicalDevice, bufferSize, VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        long mappedMemory = srcBuffer.map();
        var data = MemoryUtil.memIntBuffer(mappedMemory, (int) srcBuffer.requestedSize);
        data.put(indices);
        srcBuffer.unMap();

        return new TransferBuffers(srcBuffer, dstBuffer);
    }

    private static TransferBuffers createVertexBuffers(LogicalDevice logicalDevice, ModelData.MeshData meshData) {
        float[] positions = meshData.positions();
        float[] uvs = meshData.uvs();
        if (uvs == null || uvs.length == 0) uvs = new float[(positions.length / 3) * 2];

        var elementCount = positions.length + uvs.length;
        var bufferSize = elementCount * Float.BYTES;

        var srcBuffer = new VkBuffer(logicalDevice, bufferSize, VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var dstBuffer = new VkBuffer(logicalDevice, bufferSize, VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        var mappedMemory = srcBuffer.map();
        var data = MemoryUtil.memFloatBuffer(mappedMemory, (int) srcBuffer.requestedSize);

        int rows = positions.length / 3;
        for (var row = 0; row < rows; row++) {
            var startPos = row * 3;
            var startTextCoord = row * 2;
            data.put(positions[startPos]);
            data.put(positions[startPos + 1]);
            data.put(positions[startPos + 2]);
            data.put(uvs[startTextCoord]);
            data.put(uvs[startTextCoord + 1]);
        }

        srcBuffer.unMap();
        return new TransferBuffers(srcBuffer, dstBuffer);
    }

    private static void recordTransferCommand(CommandBuffer cmd, TransferBuffers transferBuffers) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var copyRegion = VkBufferCopy.calloc(1, stack)
                    .srcOffset(0)
                    .dstOffset(0)
                    .size(transferBuffers.srcBuffer().requestedSize);

            VK10.vkCmdCopyBuffer(cmd.vk(), transferBuffers.srcBuffer().buffer, transferBuffers.dstBuffer().buffer, copyRegion);
        }
    }

    private static Material transformMaterial(BufferedImage material, LogicalDevice logicalDevice, TextureCache cache, CommandBuffer cmdBuffer, List<Texture> textures) {
        var texture = cache.createTexture(logicalDevice, material, VK10.VK_FORMAT_R8G8B8A8_SRGB, false);
        texture.recordTransition(cmdBuffer);
        textures.add(texture);
        return new Material(texture, new ArrayList<>());
    }

    public static List<GpuModel> transformModels(List<ModelData> modelDataList, TextureCache cache, CommandPool cmdPool, Queue queue) {
        var gpuModels = new ArrayList<GpuModel>();
        var logicalDevice = cmdPool.logicalDevice;
        var cmd = new CommandBuffer(cmdPool, true, true);
        var stagingBufferList = new ArrayList<VkBuffer>();
        var textures = new ArrayList<Texture>();

        cmd.beginRecording();
        for (var modelData : modelDataList) {
            var vulkanModel = new GpuModel(modelData.name());
            gpuModels.add(vulkanModel);

            var defaultVulkanMaterial = (Material) null;
            for (var material : modelData.materials()) {
                var vulkanMaterial = transformMaterial(material, logicalDevice, cache, cmd, textures);
                vulkanModel.materials.add(vulkanMaterial);
            }

            for (var meshData : modelData.meshes()) {
                var verticesBuffers = createVertexBuffers(logicalDevice, meshData);
                var indicesBuffers = createIndexBuffers(logicalDevice, meshData);
                stagingBufferList.add(verticesBuffers.srcBuffer());
                stagingBufferList.add(indicesBuffers.srcBuffer());
                recordTransferCommand(cmd, verticesBuffers);
                recordTransferCommand(cmd, indicesBuffers);

                var vulkanMesh = new Mesh(verticesBuffers.dstBuffer(), indicesBuffers.dstBuffer(), meshData.indices().length);
                var vulkanMaterial = (Material) null;
                var materialIdx = meshData.materialIdx();

                if (materialIdx >= 0 && materialIdx < vulkanModel.materials.size())
                    vulkanMaterial = vulkanModel.materials.get(materialIdx);
                else {
                    if (defaultVulkanMaterial == null)
                        defaultVulkanMaterial = transformMaterial(TextureCache.MISSING, logicalDevice, cache, cmd, textures);
                    vulkanMaterial = defaultVulkanMaterial;
                }

                vulkanMaterial.meshes.add(vulkanMesh);
            }
        }
        cmd.endRecording();

        try (var stack = MemoryStack.stackPush()) {
            var fence = new Fence(logicalDevice, true);
            fence.reset();
            queue.submit(stack.pointers(cmd.vk()), null, null, null, fence);
            fence.waitForFence();

            fence.close();
            cmd.close();
            stagingBufferList.forEach(VkBuffer::close);
            textures.forEach(Texture::closeStagingBuffer);
        }

        return gpuModels;
    }

    @Override
    public void close() {
        materials.forEach(material -> material.meshes.forEach(Mesh::close));
    }

    private record TransferBuffers(
            VkBuffer srcBuffer,
            VkBuffer dstBuffer
    ) {}

    public record Mesh(
            VkBuffer verticesBuffer,
            VkBuffer indicesBuffer,
            int indexCount
    ) implements Closeable {
        @Override
        public void close() {
            verticesBuffer.close();
            indicesBuffer.close();
        }
    }

    public record Material(
            Texture texture,
            List<Mesh> meshes
    ) {
        public boolean isTransparent() {
            return texture.transparent;
        }
    }
}
