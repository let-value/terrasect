package com.terrasect.common.testing;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SnapshotHashes {
    private SnapshotHashes() {
    }

    public static String sha256Hex(String value) {
        return sha256Hex(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] value) {
        MessageDigest digest = sha256();
        digest.update(value);
        return HexFormat.of().formatHex(digest.digest());
    }

    public static String imageSha256(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] row = new int[width];
        MessageDigest digest = sha256();
        for (int y = 0; y < height; y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int value : row) {
                updateDigest(digest, value);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing SHA-256", e);
        }
    }

    private static void updateDigest(MessageDigest digest, int value) {
        digest.update((byte) (value >> 24));
        digest.update((byte) (value >> 16));
        digest.update((byte) (value >> 8));
        digest.update((byte) value);
    }
}
