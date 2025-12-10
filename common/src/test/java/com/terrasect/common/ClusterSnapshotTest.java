package com.terrasect.common;

import com.terrasect.common.generation.ClusterDefinition;
import com.terrasect.common.generation.ClusterMapGenerator;
import com.terrasect.common.generation.ClusterSnapshotRenderer;
import com.terrasect.common.generation.RegionDefinition;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ClusterSnapshotTest {
    private static final long SEED = 123456789L;
    private static final int MAP_SIZE = 3_000;
    private static final Path DEBUG_OUTPUT = Path.of("dist/cluster-outlines.png");
    // Frozen digest of the expected cluster snapshot to avoid storing a large binary in the repo.
    private static final String SNAPSHOT_DIGEST = "a1e35dda89b6e11486a3a82a7872c7257fe1ea7e72c37511737ac7a0512feaaf";

    @Test
    void clusterOutlinesSnapshotShouldMatch() throws IOException, NoSuchAlgorithmException {
        ClusterDefinition cluster = ClusterDefinition.autoSize(List.of(
            RegionDefinition.of("village-1", 62_000, List.of("village-2")),
            RegionDefinition.of("village-2", 58_000, List.of("orchard")),
            RegionDefinition.of("orchard", 42_000, List.of("wilds")),
            RegionDefinition.of("wilds", 77_000, List.of("village-1")),
            RegionDefinition.of("ruins", 21_000, List.of("wilds"))
        ));

        ClusterMapGenerator generator = new ClusterMapGenerator();
        ClusterMapGenerator.ClusterPattern pattern = generator.generate(cluster, SEED);
        BufferedImage image = ClusterSnapshotRenderer.render(pattern, MAP_SIZE, MAP_SIZE);
        byte[] png = ClusterSnapshotRenderer.toPngBytes(image);
        String digest = sha256(png);

        Files.createDirectories(DEBUG_OUTPUT.getParent());
        ImageIO.write(image, "png", DEBUG_OUTPUT.toFile());

        if (!digest.equals(SNAPSHOT_DIGEST)) {
            fail("Cluster snapshot differed; wrote debug image to " + DEBUG_OUTPUT.toAbsolutePath());
        }
        assertEquals(SNAPSHOT_DIGEST, digest, "Snapshot digest should be stable");
    }

    private String sha256(byte[] payload) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(payload);
        return HexFormat.of().formatHex(hash);
    }
}
