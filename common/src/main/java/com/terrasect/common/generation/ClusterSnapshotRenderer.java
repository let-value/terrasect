package com.terrasect.common.generation;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Renders a procedural cluster map into a PNG snapshot using Voronoi-shaped cluster ownership and
 * organic in-cluster region layouts.
 */
public final class ClusterSnapshotRenderer {
    private ClusterSnapshotRenderer() {
    }

    public static BufferedImage render(ClusterMapGenerator.ClusterPattern pattern, int width, int height) {
        Objects.requireNonNull(pattern, "pattern");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] pixels = new int[width * height];
        int[] regionGrid = new int[width * height];
        long[] siteKeys = new long[width * height];
        int clusterOutlineColor = Color.BLACK.getRGB();
        int regionOutlineColor = new Color(32, 32, 32).getRGB();

        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                ClusterMapGenerator.ClusterSite site = pattern.siteForPoint(x, y);
                siteKeys[idx] = site.key();
                regionGrid[idx] = pattern.regionForPoint(x, y);
            }
        });

        IntStream.range(0, height).parallel().forEach(y -> {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;
                int regionIndex = regionGrid[idx];
                long siteKey = siteKeys[idx];
                boolean clusterBorder = x == 0 || y == 0 || x == width - 1 || y == height - 1;
                boolean regionBorder = clusterBorder;

                if (!clusterBorder) {
                    int rightIdx = idx + 1;
                    int downIdx = idx + width;
                    clusterBorder = siteKeys[rightIdx] != siteKey || siteKeys[downIdx] != siteKey;
                    regionBorder = clusterBorder
                        || regionGrid[rightIdx] != regionIndex
                        || regionGrid[downIdx] != regionIndex;
                }

                if (clusterBorder) {
                    pixels[idx] = clusterOutlineColor;
                } else if (regionBorder) {
                    pixels[idx] = regionOutlineColor;
                } else {
                    pixels[idx] = clusterFillColor(siteKey);
                }
            }
        });

        image.setRGB(0, 0, width, height, pixels, 0, width);
        return image;
    }

    public static byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private static int clusterFillColor(long siteKey) {
        // HSB palette anchored by the site key to keep clusters easy to follow.
        double goldenRatio = 0.6180339887498949;
        double hueSeed = (siteKey * goldenRatio) % 1.0;
        if (hueSeed < 0) {
            hueSeed += 1.0;
        }

        float hue = (float) hueSeed;
        float saturation = 0.55f + (float) ((Long.rotateRight(siteKey, 16) & 0xFF) / 512f);
        float brightness = 0.70f + (float) ((Long.rotateRight(siteKey, 32) & 0xFF) / 1024f);
        return Color.HSBtoRGB(hue, Math.min(1.0f, saturation), Math.min(1.0f, brightness));
    }
}
