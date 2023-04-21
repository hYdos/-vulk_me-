package me.hydos.vkinteropexperiments.graph.vertex;

import me.hydos.vkinteropexperiments.graph.command.CommandBuffer;
import me.hydos.vkinteropexperiments.graph.command.CommandPool;
import me.hydos.vkinteropexperiments.graph.setup.LogicalDevice;
import me.hydos.vkinteropexperiments.graph.setup.Queue;
import me.hydos.vkinteropexperiments.graph.sync.Fence;
import me.hydos.vkinteropexperiments.memory.VkBuffer;
import me.hydos.vkinteropexperiments.scene.ModelData;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VkBufferCopy;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;

public class GpuModel implements Closeable {

    public final String name;
    public final List<GpuMesh> gpuMeshList;

    public GpuModel(String name) {
        this.name = name;
        this.gpuMeshList = new ArrayList<>();
    }

    private static TransferBuffers createIndicesBuffers(LogicalDevice logicalDevice, ModelData.MeshData meshData) {
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

    private static TransferBuffers createVerticesBuffers(LogicalDevice logicalDevice, ModelData.MeshData meshData) {
        var positions = meshData.positions();
        var positionCount = positions.length;
        var bufferSize = positionCount * Float.BYTES;

        var srcBuffer = new VkBuffer(logicalDevice, bufferSize, VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT, VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        var dstBuffer = new VkBuffer(logicalDevice, bufferSize, VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, VK10.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);

        var mappedMemory = srcBuffer.map();
        var data = MemoryUtil.memFloatBuffer(mappedMemory, (int) srcBuffer.requestedSize);
        data.put(positions);
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

    public static List<GpuModel> transformModels(List<ModelData> modelDataList, CommandPool cmdPool, Queue queue) {
        var vulkanModelList = new ArrayList<GpuModel>();
        var logicalDevice = cmdPool.logicalDevice;
        var cmd = new CommandBuffer(cmdPool, true, true);
        var stagingBufferList = new ArrayList<VkBuffer>();

        cmd.beginRecording();
        for (var modelData : modelDataList) {
            var vulkanModel = new GpuModel(modelData.name());
            vulkanModelList.add(vulkanModel);

            // Transform meshes loading their data into GPU buffers
            for (var meshData : modelData.meshDataList()) {
                TransferBuffers verticesBuffers = createVerticesBuffers(logicalDevice, meshData);
                TransferBuffers indicesBuffers = createIndicesBuffers(logicalDevice, meshData);
                stagingBufferList.add(verticesBuffers.srcBuffer());
                stagingBufferList.add(indicesBuffers.srcBuffer());
                recordTransferCommand(cmd, verticesBuffers);
                recordTransferCommand(cmd, indicesBuffers);

                vulkanModel.gpuMeshList.add(new GpuMesh(verticesBuffers.dstBuffer(), indicesBuffers.dstBuffer(), meshData.indices().length));
            }
        }

        cmd.endRecording();

        try (var stack = MemoryStack.stackPush()) {
            var fence = new Fence(logicalDevice, true);
            fence.reset();
            queue.submit(stack.pointers(cmd.vk()), null, null, null, fence);
            fence.waitForFence();
            fence.close();
        }

        cmd.close();
        stagingBufferList.forEach(VkBuffer::close);
        return vulkanModelList;
    }

    @Override
    public void close() {
        gpuMeshList.forEach(GpuMesh::close);
    }

    private record TransferBuffers(
            VkBuffer srcBuffer,
            VkBuffer dstBuffer
    ) {}

    public record GpuMesh(
            VkBuffer verticesBuffer,
            VkBuffer indicesBuffer,
            int numIndices
    ) implements Closeable {
        @Override
        public void close() {
            verticesBuffer.close();
            indicesBuffer.close();
        }
    }
}
