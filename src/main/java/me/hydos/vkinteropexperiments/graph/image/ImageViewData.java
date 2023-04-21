package me.hydos.vkinteropexperiments.graph.image;

import org.lwjgl.vulkan.VK10;

public record ImageViewData(
        int aspectMask,
        int baseArrayLayer,
        int format,
        int layerCount,
        int mipLevels,
        int viewType
) {
    
    public static class Builder {
        private int aspectMask;
        private int baseArrayLayer;
        private int format;
        private int layerCount;
        private int mipLevels;
        private int viewType;

        public Builder() {
            this.baseArrayLayer = 0;
            this.layerCount = 1;
            this.mipLevels = 1;
            this.viewType = VK10.VK_IMAGE_VIEW_TYPE_2D;
        }

        public Builder aspectMask(int aspectMask) {
            this.aspectMask = aspectMask;
            return this;
        }

        public Builder baseArrayLayer(int baseArrayLayer) {
            this.baseArrayLayer = baseArrayLayer;
            return this;
        }

        public Builder format(int format) {
            this.format = format;
            return this;
        }

        public Builder layerCount(int layerCount) {
            this.layerCount = layerCount;
            return this;
        }

        public Builder mipLevels(int mipLevels) {
            this.mipLevels = mipLevels;
            return this;
        }

        public Builder viewType(int viewType) {
            this.viewType = viewType;
            return this;
        }

        public ImageViewData build() {
            return new ImageViewData(
                    aspectMask,
                    baseArrayLayer,
                    format,
                    layerCount,
                    mipLevels,
                    viewType
            );
        }
    }
}
