package com.terrasect.common.generation;

import com.terrasect.common.Context;
import com.terrasect.common.definition.GenerationStrategyType;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import com.terrasect.common.definition.StrategySettings;
import de.skuzzle.test.snapshots.Snapshot;
import com.terrasect.common.testing.SnapshotTests;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SnapshotTests
public class StrategySnapshotTest {

    private static final long SEED = 42424242L;
    private static final int IMG_SIZE = 256;
    private static final int WORLD_SIZE = 2048;

    private File outDir;
    private Context context;

    @BeforeEach
    void setup() {
        context = new SnapshotTest.MockStrategy(SEED);
    }

    @Test
    void voronoi_default(Snapshot snapshot) throws Exception {
        Region root = buildSimpleHierarchy(GenerationStrategyType.VORONOI, null);
        runSnapshotTest(snapshot, "voronoi_default", root, 2);
    }

    @Test
    void voronoi_low_relaxation(Snapshot snapshot) throws Exception {
        StrategySettings settings =
                StrategySettings.builder().voronoiRelaxation(2).build();
        Region root = buildSimpleHierarchy(GenerationStrategyType.VORONOI, settings);
        runSnapshotTest(snapshot, "voronoi_low_relaxation", root, 2);
    }

    @Test
    void voronoi_high_relaxation(Snapshot snapshot) throws Exception {
        StrategySettings settings =
                StrategySettings.builder().voronoiRelaxation(20).build();
        Region root = buildSimpleHierarchy(GenerationStrategyType.VORONOI, settings);
        runSnapshotTest(snapshot, "voronoi_high_relaxation", root, 2);
    }

    @Test
    void subdivision_default(Snapshot snapshot) throws Exception {
        Region root = buildSimpleHierarchy(GenerationStrategyType.SUBDIVISION, null);
        runSnapshotTest(snapshot, "subdivision_default", root, 2);
    }

    @Test
    void subdivision_low_jitter(Snapshot snapshot) throws Exception {
        StrategySettings settings =
                StrategySettings.builder().subdivisionJitter(0.0f).build();
        Region root = buildSimpleHierarchy(GenerationStrategyType.SUBDIVISION, settings);
        runSnapshotTest(snapshot, "subdivision_low_jitter", root, 2);
    }

    @Test
    void subdivision_high_jitter(Snapshot snapshot) throws Exception {
        StrategySettings settings =
                StrategySettings.builder().subdivisionJitter(0.5f).build();
        Region root = buildSimpleHierarchy(GenerationStrategyType.SUBDIVISION, settings);
        runSnapshotTest(snapshot, "subdivision_high_jitter", root, 2);
    }

    @Test
    void template_binary(Snapshot snapshot) throws Exception {
        StrategySettings settings = StrategySettings.builder()
                .template(StrategySettings.TemplateType.BINARY)
                .build();
        Region root = buildBinaryHierarchy(settings);
        runSnapshotTest(snapshot, "template_binary", root, 2);
    }

    @Test
    void template_triangle(Snapshot snapshot) throws Exception {
        StrategySettings settings = StrategySettings.builder()
                .template(StrategySettings.TemplateType.TRIANGLE)
                .build();
        Region root = buildTriangleHierarchy(settings);
        runSnapshotTest(snapshot, "template_triangle", root, 2);
    }

    @Test
    void template_center_surround(Snapshot snapshot) throws Exception {
        StrategySettings settings = StrategySettings.builder()
                .template(StrategySettings.TemplateType.CENTER_SURROUND)
                .build();
        Region root = buildCenterSurroundHierarchy(settings, null);
        runSnapshotTest(snapshot, "template_center_surround", root, 2);
    }

    @Test
    void template_center_surround_named(Snapshot snapshot) throws Exception {

        StrategySettings settings = StrategySettings.builder()
                .template(StrategySettings.TemplateType.CENTER_SURROUND)
                .centerSurround("SMALL_CENTER")
                .build();
        Region root = buildCenterSurroundHierarchy(settings, "SMALL_CENTER");
        runSnapshotTest(snapshot, "template_center_surround_named", root, 2);
    }

    @Test
    void template_radial(Snapshot snapshot) throws Exception {
        StrategySettings settings = StrategySettings.builder()
                .template(StrategySettings.TemplateType.RADIAL)
                .build();
        Region root = buildRadialHierarchy(settings);
        runSnapshotTest(snapshot, "template_radial", root, 2);
    }

    @Test
    void hex_default(Snapshot snapshot) throws Exception {
        Region root = buildHexHierarchy(null);
        runSnapshotTest(snapshot, "hex_default", root, 2);
    }

    @Test
    void hex_with_ring(Snapshot snapshot) throws Exception {
        StrategySettings settings = StrategySettings.builder().hexRing("BORDER").build();
        Region root = buildHexHierarchy(settings);
        runSnapshotTest(snapshot, "hex_with_ring", root, 2);
    }

    @Test
    void nested_hex_voronoi(Snapshot snapshot) throws Exception {

        Region root = buildNestedHexVoronoi();
        runSnapshotTest(snapshot, "nested_hex_voronoi", root, 2);
    }

    @Test
    void nested_hex_subdivision(Snapshot snapshot) throws Exception {

        Region root = buildNestedHexSubdivision();
        runSnapshotTest(snapshot, "nested_hex_subdivision", root, 2);
    }

    @Test
    void nested_voronoi_template(Snapshot snapshot) throws Exception {

        Region root = buildNestedVoronoiTemplate();
        runSnapshotTest(snapshot, "nested_voronoi_template", root, 3);
    }

    @Test
    void nested_subdivision_template(Snapshot snapshot) throws Exception {

        Region root = buildNestedSubdivisionTemplate();
        runSnapshotTest(snapshot, "nested_subdivision_template", root, 3);
    }

    @Test
    void deeply_nested(Snapshot snapshot) throws Exception {

        Region root = buildDeeplyNested();
        runSnapshotTest(snapshot, "deeply_nested", root, 4);
    }

    @Test
    void kitchen_sink(Snapshot snapshot) throws Exception {
        Region root = buildKitchenSink();
        runSnapshotTest(snapshot, "kitchen_sink", root, 4);
    }

    private void runSnapshotTest(Snapshot snapshot, String testName, Region root, int maxDepth) throws Exception {
        String digest = runSnapshot(testName, root, maxDepth);
        snapshot.assertThat(digest).asText().matchesSnapshotText();
    }

    private Region buildSimpleHierarchy(GenerationStrategyType strategy, StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD").strategy(GenerationStrategyType.HEX).child("REALM", realm -> realm.strategy(strategy)
                .settings(settings)
                .child("ZONE_A", a -> a.radius(100))
                .child("ZONE_B", b -> b.radius(200))
                .child("ZONE_C", c -> c.radius(150))
                .child("ZONE_D", d -> d.radius(50)));
        return registry.build("WORLD");
    }

    private Region buildBinaryHierarchy(StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD").strategy(GenerationStrategyType.HEX).child("REALM", realm -> realm.strategy(
                        GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("LEFT", a -> a.radius(100))
                .child("RIGHT", b -> b.radius(100)));
        return registry.build("WORLD");
    }

    private Region buildTriangleHierarchy(StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD").strategy(GenerationStrategyType.HEX).child("REALM", realm -> realm.strategy(
                        GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("NORTH", a -> a.radius(100))
                .child("SOUTHWEST", b -> b.radius(100))
                .child("SOUTHEAST", c -> c.radius(100)));
        return registry.build("WORLD");
    }

    private Region buildCenterSurroundHierarchy(StrategySettings settings, String centerName) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD").strategy(GenerationStrategyType.HEX).child("REALM", realm -> realm.strategy(
                        GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("CAPITAL", c -> c.radius(centerName == null ? 300 : 50))
                .child("VILLAGE_N", a -> a.radius(100))
                .child("VILLAGE_E", b -> b.radius(100))
                .child("VILLAGE_S", c2 -> c2.radius(100))
                .child("VILLAGE_W", d -> d.radius(100))
                .child("SMALL_CENTER", sc -> sc.radius(centerName != null ? 300 : 50)));
        return registry.build("WORLD");
    }

    private Region buildRadialHierarchy(StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD").strategy(GenerationStrategyType.HEX).child("REALM", realm -> realm.strategy(
                        GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("SECTOR_1", a -> a.radius(80))
                .child("SECTOR_2", b -> b.radius(80))
                .child("SECTOR_3", c -> c.radius(80))
                .child("SECTOR_4", d -> d.radius(80))
                .child("SECTOR_5", e -> e.radius(80))
                .child("SECTOR_6", f -> f.radius(80)));
        return registry.build("WORLD");
    }

    private Region buildHexHierarchy(StrategySettings settings) {

        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
                .strategy(GenerationStrategyType.HEX)
                .settings(settings)
                .child("PLAINS_HEX", plains -> plains.strategy(GenerationStrategyType.VORONOI)
                        .child("FARMLAND", f -> f.radius(60))
                        .child("VILLAGE_N", v -> v.radius(20))
                        .child("GRASSLAND", g -> g.radius(40)))
                .child("FOREST_HEX", forest -> forest.radius(80))
                .child("MOUNTAIN_HEX", mountain -> mountain.radius(50))
                .child("BORDER", border -> border.radius(30));
        return registry.build("WORLD");
    }

    private Region buildNestedHexVoronoi() {

        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
                .strategy(GenerationStrategyType.HEX)
                .child("PLAINS_REALM", plains -> plains.radius(100)
                        .strategy(GenerationStrategyType.VORONOI)
                        .child("FARMLAND", f -> f.radius(60))
                        .child("VILLAGE_N", v -> v.radius(20))
                        .child("GRASSLAND", g -> g.radius(40)))
                .child("FOREST_REALM", forest -> forest.radius(80)
                        .strategy(GenerationStrategyType.VORONOI)
                        .child("DENSE_FOREST", d -> d.radius(50))
                        .child("GROVE", g -> g.radius(30))
                        .child("CLEARING", c -> c.radius(20)))
                .child("MOUNTAIN_REALM", mountain -> mountain.radius(50)
                        .strategy(GenerationStrategyType.VORONOI)
                        .child("PEAK_1", p -> p.radius(40))
                        .child("VALLEY", v -> v.radius(30))
                        .child("MOUNTAIN_PASS", m -> m.radius(20)));
        return registry.build("WORLD");
    }

    private Region buildNestedHexSubdivision() {

        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
                .strategy(GenerationStrategyType.HEX)
                .child("PLAINS_REALM", plains -> plains.radius(100)
                        .strategy(GenerationStrategyType.SUBDIVISION)
                        .settings(StrategySettings.builder()
                                .subdivisionJitter(0.1f)
                                .build())
                        .child("NORTH_LANDS", n -> n.radius(200))
                        .child("SOUTH_LANDS", s -> s.radius(200)))
                .child("FOREST_REALM", forest -> forest.radius(80)
                        .strategy(GenerationStrategyType.SUBDIVISION)
                        .settings(StrategySettings.builder()
                                .subdivisionJitter(0.1f)
                                .build())
                        .child("EAST_LANDS", e -> e.radius(150))
                        .child("WEST_LANDS", w -> w.radius(150)))
                .child("MOUNTAIN_REALM", mountain -> mountain.radius(50)
                        .strategy(GenerationStrategyType.SUBDIVISION)
                        .settings(StrategySettings.builder()
                                .subdivisionJitter(0.1f)
                                .build())
                        .child("PEAK_1", p -> p.radius(100))
                        .child("VALLEY", v -> v.radius(100)));
        return registry.build("WORLD");
    }

    private Region buildNestedVoronoiTemplate() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD").strategy(GenerationStrategyType.HEX).child("REALM", realm -> realm.strategy(
                        GenerationStrategyType.VORONOI)
                .child("CAPITAL_REGION", cap -> cap.radius(300)
                        .strategy(GenerationStrategyType.TEMPLATE)
                        .settings(StrategySettings.builder()
                                .template(StrategySettings.TemplateType.CENTER_SURROUND)
                                .centerSurround("PALACE")
                                .build())
                        .child("PALACE", p -> p.radius(100))
                        .child("GARDENS", g -> g.radius(100))
                        .child("MARKET", m -> m.radius(100)))
                .child("FARMLAND", farm -> farm.radius(200))
                .child("FOREST", forest -> forest.radius(150)));
        return registry.build("WORLD");
    }

    private Region buildNestedSubdivisionTemplate() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD").strategy(GenerationStrategyType.HEX).child("REALM", realm -> realm.strategy(
                        GenerationStrategyType.SUBDIVISION)
                .child("CITY", city -> city.radius(250)
                        .strategy(GenerationStrategyType.TEMPLATE)
                        .settings(StrategySettings.builder()
                                .template(StrategySettings.TemplateType.RADIAL)
                                .build())
                        .child("DISTRICT_1", d1 -> d1.radius(50))
                        .child("DISTRICT_2", d2 -> d2.radius(50))
                        .child("DISTRICT_3", d3 -> d3.radius(50))
                        .child("DISTRICT_4", d4 -> d4.radius(50)))
                .child("OUTSKIRTS", out -> out.radius(200))
                .child("WILDERNESS", wild -> wild.radius(250)));
        return registry.build("WORLD");
    }

    private Region buildDeeplyNested() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("COSMOS").strategy(GenerationStrategyType.HEX).child("GALAXY", galaxy -> galaxy.strategy(
                        GenerationStrategyType.VORONOI)
                .child("STAR_SYSTEM", system -> system.radius(400)
                        .strategy(GenerationStrategyType.SUBDIVISION)
                        .child("INNER_PLANETS", inner -> inner.radius(200)
                                .strategy(GenerationStrategyType.TEMPLATE)
                                .settings(StrategySettings.builder()
                                        .template(StrategySettings.TemplateType.RADIAL)
                                        .build())
                                .child("MERCURY", m -> m.radius(30))
                                .child("VENUS", v -> v.radius(50))
                                .child("EARTH", e -> e.radius(60))
                                .child("MARS", mars -> mars.radius(40)))
                        .child("OUTER_PLANETS", outer -> outer.radius(200)))
                .child("NEBULA", neb -> neb.radius(200))
                .child("VOID", v -> v.radius(100)));
        return registry.build("COSMOS");
    }

    private Region buildKitchenSink() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
                .strategy(GenerationStrategyType.HEX)
                .settings(StrategySettings.builder().hexRing("OCEAN").build())
                .child("LANDMASS", land -> land.strategy(GenerationStrategyType.VORONOI)
                        .child("KINGDOM", kingdom -> kingdom.radius(400)
                                .strategy(GenerationStrategyType.TEMPLATE)
                                .settings(StrategySettings.builder()
                                        .template(StrategySettings.TemplateType.CENTER_SURROUND)
                                        .centerSurround("CASTLE")
                                        .build())
                                .child("CASTLE", castle -> castle.radius(100)
                                        .strategy(GenerationStrategyType.SUBDIVISION)
                                        .settings(StrategySettings.builder()
                                                .subdivisionJitter(0.1f)
                                                .build())
                                        .child("KEEP", k -> k.radius(50))
                                        .child("COURTYARD", cy -> cy.radius(30))
                                        .child("BARRACKS", b -> b.radius(20)))
                                .child("VILLAGE_NORTH", vn -> vn.radius(75))
                                .child("VILLAGE_SOUTH", vs -> vs.radius(75))
                                .child("FARMLAND", farm -> farm.radius(150)))
                        .child("WILDERNESS", wild -> wild.radius(350)
                                .strategy(GenerationStrategyType.SUBDIVISION)
                                .settings(StrategySettings.builder()
                                        .subdivisionJitter(0.3f)
                                        .build())
                                .child("DENSE_FOREST", df -> df.radius(150)
                                        .strategy(GenerationStrategyType.VORONOI)
                                        .child("GROVE", g -> g.radius(50))
                                        .child("THICKET", t -> t.radius(50))
                                        .child("CLEARING", c -> c.radius(50)))
                                .child("SWAMP", swamp -> swamp.radius(100))
                                .child("HILLS", hills -> hills.radius(100)))
                        .child("MOUNTAINS", mtns -> mtns.radius(250)
                                .strategy(GenerationStrategyType.TEMPLATE)
                                .settings(StrategySettings.builder()
                                        .template(StrategySettings.TemplateType.RADIAL)
                                        .build())
                                .child("PEAK_1", p1 -> p1.radius(60))
                                .child("PEAK_2", p2 -> p2.radius(60))
                                .child("PEAK_3", p3 -> p3.radius(60))
                                .child("VALLEY", val -> val.radius(70))))
                .child("OCEAN", ocean -> ocean.radius(1000)
                        .strategy(GenerationStrategyType.VORONOI)
                        .child("DEEP_SEA", deep -> deep.radius(500))
                        .child("SHALLOWS", shallow -> shallow.radius(300))
                        .child("REEF", reef -> reef.radius(200)));

        return registry.build("WORLD");
    }

    private String runSnapshot(String testName, Region root, int maxDepth) throws Exception {
        World.register(root, World.OVERWORLD);
        outDir = new File("build/test-snapshots/" + testName);
        outDir.mkdirs();
        int step = WORLD_SIZE / IMG_SIZE;

        List<BufferedImage> depthImages = new ArrayList<>();
        for (int d = 0; d <= maxDepth; d++) {
            depthImages.add(new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB));
        }

        BufferedImage imgCombined = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        Map<Integer, Map<String, Integer>> depthCounts = new HashMap<>();
        for (int d = 0; d <= maxDepth; d++) {
            depthCounts.put(d, new HashMap<>());
        }

        for (int y = 0; y < IMG_SIZE; y++) {
            for (int x = 0; x < IMG_SIZE; x++) {

                int wx = (x - IMG_SIZE / 2) * step;
                int wz = (y - IMG_SIZE / 2) * step;

                for (int d = 1; d <= maxDepth; d++) {
                    TraversalResult traversal = World.traverse(context, wx, wz, d);
                    Region region = traversal.region;
                    long regionSeed = traversal.seed;

                    depthCounts.get(d).merge(region.name(), 1, Integer::sum);

                    updateDigest(digest, region.name().hashCode());
                    updateDigest(digest, (int) regionSeed);

                    int color = getRegionColor(region);

                    TraversalResult rightTraversal = World.traverse(context, wx + step, wz, d);
                    Region right = rightTraversal.region;
                    long rightSeed = rightTraversal.seed;
                    TraversalResult downTraversal = World.traverse(context, wx, wz + step, d);
                    Region down = downTraversal.region;
                    long downSeed = downTraversal.seed;

                    boolean isEdge = !region.name().equals(right.name())
                            || regionSeed != rightSeed
                            || !region.name().equals(down.name())
                            || regionSeed != downSeed;

                    if (isEdge) {
                        color = darken(color, 0.5f);
                    }

                    depthImages.get(d).setRGB(x, y, color);
                }

                Region leafRegion = World.traverse(context, wx, wz, maxDepth).region;
                int color = getRegionColor(leafRegion);

                for (int d = 1; d <= maxDepth; d++) {
                    TraversalResult traversal = World.traverse(context, wx, wz, d);
                    Region region = traversal.region;
                    long regionSeed = traversal.seed;

                    TraversalResult rightTraversal = World.traverse(context, wx + step, wz, d);
                    Region right = rightTraversal.region;
                    long rightSeed = rightTraversal.seed;
                    TraversalResult downTraversal = World.traverse(context, wx, wz + step, d);
                    Region down = downTraversal.region;
                    long downSeed = downTraversal.seed;

                    if (!region.name().equals(right.name())
                            || regionSeed != rightSeed
                            || !region.name().equals(down.name())
                            || regionSeed != downSeed) {

                        float edgeBrightness = 0.3f + 0.2f * (maxDepth - d);
                        color = darken(color, edgeBrightness);
                    }
                }

                imgCombined.setRGB(x, y, color);
            }
        }

        for (int d = 1; d <= maxDepth; d++) {
            ImageIO.write(depthImages.get(d), "png", new File(outDir, "depth_" + d + ".png"));
        }
        ImageIO.write(imgCombined, "png", new File(outDir, "combined.png"));

        generateLegend(testName, depthCounts, maxDepth);

        String actualDigest = java.util.HexFormat.of().formatHex(digest.digest());
        System.out.println("[" + testName + "] Digest: " + actualDigest);

        for (int d = 1; d <= maxDepth; d++) {
            System.out.println("  Depth " + d + ": " + depthCounts.get(d));
        }

        return actualDigest;
    }

    private void generateLegend(String testName, Map<Integer, Map<String, Integer>> depthCounts, int maxDepth)
            throws IOException {

        Set<String> allRegions = new TreeSet<>();
        for (int d = 1; d <= maxDepth; d++) {
            allRegions.addAll(depthCounts.get(d).keySet());
        }

        int legendWidth = 200;
        int rowHeight = 20;
        int legendHeight = allRegions.size() * rowHeight + 30;

        BufferedImage legend = new BufferedImage(legendWidth, legendHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = legend.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, legendWidth, legendHeight);

        g.setColor(Color.BLACK);
        g.drawString(testName, 5, 15);

        int y = 30;
        for (String regionName : allRegions) {
            int color = getRegionColor(regionName);
            g.setColor(new Color(color));
            g.fillRect(5, y - 12, 15, 15);
            g.setColor(Color.BLACK);
            g.drawRect(5, y - 12, 15, 15);
            g.drawString(regionName, 25, y);
            y += rowHeight;
        }

        g.dispose();
        ImageIO.write(legend, "png", new File(outDir, "legend.png"));
    }

    private void updateDigest(MessageDigest digest, int val) {
        digest.update((byte) (val >> 24));
        digest.update((byte) (val >> 16));
        digest.update((byte) (val >> 8));
        digest.update((byte) val);
    }

    private int darken(int color, float factor) {
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (r << 16) | (g << 8) | b;
    }

    private int getRegionColor(Region region) {
        return getRegionColor(region.name());
    }

    private int getRegionColor(String name) {

        int hash = name.hashCode();

        return switch (name) {
            case "OCEAN", "DEEP_SEA" -> 0x1E90FF;
            case "SHALLOWS" -> 0x87CEEB;
            case "REEF" -> 0x00CED1;
            case "LANDMASS", "CONTINENT" -> 0x8B4513;
            case "FOREST", "DENSE_FOREST", "GROVE", "FORBIDDEN_WOODS" -> 0x228B22;
            case "THICKET" -> 0x006400;
            case "CLEARING" -> 0x90EE90;
            case "SWAMP" -> 0x556B2F;
            case "HILLS" -> 0x9ACD32;
            case "MOUNTAINS", "PEAK_1", "PEAK_2", "PEAK_3" -> 0x696969;
            case "VALLEY", "MOUNTAIN_PASS" -> 0xBDB76B;

            case "KINGDOM", "EMPIRE", "REPUBLIC" -> 0xDAA520;
            case "CAPITAL", "CAPITAL_REGION" -> 0xFFD700;
            case "CASTLE", "PALACE" -> 0xB8860B;
            case "KEEP" -> 0x8B8682;
            case "COURTYARD", "GARDENS" -> 0x98FB98;
            case "BARRACKS" -> 0xA0522D;
            case "VILLAGE_NORTH", "VILLAGE_SOUTH", "VILLAGE_N", "VILLAGE_E", "VILLAGE_S", "VILLAGE_W" -> 0xDEB887;
            case "FARMLAND" -> 0xF0E68C;
            case "MARKET" -> 0xFFE4B5;
            case "HARBOR" -> 0x4169E1;

            case "WILDERNESS" -> 0x3CB371;
            case "OUTSKIRTS" -> 0x8FBC8F;

            case "ZONE_A", "SECTOR_1", "DISTRICT_1" -> 0xFF6347;
            case "ZONE_B", "SECTOR_2", "DISTRICT_2" -> 0x4682B4;
            case "ZONE_C", "SECTOR_3", "DISTRICT_3" -> 0x32CD32;
            case "ZONE_D", "SECTOR_4", "DISTRICT_4" -> 0xBA55D3;
            case "SECTOR_5" -> 0xFF69B4;
            case "SECTOR_6" -> 0x00CED1;

            case "LEFT", "WEST_LANDS" -> 0xFFA07A;
            case "RIGHT", "EAST_LANDS" -> 0x87CEFA;
            case "NORTH", "NORTH_LANDS" -> 0xE6E6FA;
            case "SOUTHWEST", "SOUTH_LANDS" -> 0xF0FFF0;
            case "SOUTHEAST" -> 0xFFE4E1;

            case "COSMOS", "GALAXY" -> 0x191970;
            case "STAR_SYSTEM" -> 0xFFD700;
            case "NEBULA" -> 0x9932CC;
            case "VOID" -> 0x0D0D0D;
            case "INNER_PLANETS" -> 0xFFA500;
            case "OUTER_PLANETS" -> 0x4169E1;
            case "MERCURY" -> 0xB0B0B0;
            case "VENUS" -> 0xFFD700;
            case "EARTH" -> 0x1E90FF;
            case "MARS" -> 0xCD5C5C;

            case "REALM", "WORLD", "ROOT" -> 0x808080;
            case "TILE" -> 0xE0A060;
            case "PLAINS_HEX", "PLAINS_REALM" -> 0xC2B280;
            case "FOREST_HEX", "FOREST_REALM" -> 0x355E3B;
            case "MOUNTAIN_HEX", "MOUNTAIN_REALM" -> 0x8B7355;
            case "GRASSLAND" -> 0x7CFC00;
            case "BORDER" -> 0x2F4F4F;
            case "SMALL_CENTER" -> 0xFF1493;

            default -> {
                int r = ((hash >> 16) & 0x7F) + 0x40;
                int g = ((hash >> 8) & 0x7F) + 0x40;
                int b = (hash & 0x7F) + 0x40;
                yield (r << 16) | (g << 8) | b;
            }
        };
    }
}
