package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.definition.StrategySettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Comprehensive snapshot tests for all generation strategies.
 * 
 * Each test:
 * 1. Builds a region hierarchy with specific strategy configuration
 * 2. Samples the world to generate debug images
 * 3. Computes a digest for regression testing
 * 4. Saves images to build/test-snapshots/{testName}/
 */
public class StrategySnapshotTest {

    private static final long SEED = 42424242L;
    private static final int IMG_SIZE = 256;
    private static final int WORLD_SIZE = 2048;
    
    // Collect digests from all tests for output
    private static final Map<String, String> COLLECTED_DIGESTS = new ConcurrentHashMap<>();
    
    // Expected digests for each test - update when intentionally changing output
    private static final Map<String, String> EXPECTED_DIGESTS = Map.ofEntries(
        Map.entry("voronoi_default", "559706033d4f1987690a80877bb7731b77934ff9083abbb363219ef989618f83"),
        Map.entry("voronoi_low_relaxation", "a4118eccdc7ac3a6372236955eb91fcd875d3d7bb268db74d6caefd54944964b"),
        Map.entry("voronoi_high_relaxation", "25d3f610060f10c7ebb1ccb72bb2779a687f232c0163707c52a9bb4e823c4604"),
        Map.entry("subdivision_default", "26f2682bfae14d56a9400433eb9f9dcaf4b4680adc350ed3f948ad700096434a"),
        Map.entry("subdivision_low_jitter", "648e3a05442da6f4c600f955f8d305f2a10ff95efd1ef3b527e0b9256c07cca6"),
        Map.entry("subdivision_high_jitter", "05c5570121a8c59ff7b7991402bda31e45d890cc7bd8b1162700c51cfdcdbfa7"),
        Map.entry("template_binary", "2313264fdacb7482190ac25895b903bc011f63103a3b552c61f0a4ecfd25059c"),
        Map.entry("template_triangle", "0b64170eeddc232d84cca73744fb94c56aecfb36b782e49e45e1badebcdbf6b3"),
        Map.entry("template_center_surround", "d3fef65726cc09d832c1bf2c870ccb11e88c294f817280608b2249f7597a4760"),
        Map.entry("template_center_surround_named", "e319292d5a93afff91ae1b44ff97369dbbb66a5157c3835ddfa8e1e00b25a930"),
        Map.entry("template_radial", "db9c4795f3b1f445e273727126b9f882300ec86e2a5db3d61effcd02f7041bf4"),
        Map.entry("hex_default", "26985e4522a5a547306b35417b95bab7e4e32b7c2c5d83db8cc593e657a4471f"),
        Map.entry("hex_with_ring", "5c5fa6273f53012374ae55f5257f88089c263125742fe875a92dfec96b3ed45f"),
        Map.entry("nested_hex_voronoi", "9545f414bf4bce9e5971cbb785aaa63a636604c94a0cc8f25238f715ed3e8e4d"),
        Map.entry("nested_hex_subdivision", "a2d50caf0a5bdd85da5478e63ee9a51cc28bed11cb2099483566e13248662d60"),
        Map.entry("nested_voronoi_template", "4e8cf6e664745258fae8b46232d3ad827907ac8c51bf745d462e17830237f9bc"),
        Map.entry("nested_subdivision_template", "b6accafd60b7ce253dd2e9d510c1f33decee723397ae582e36d73380a8107c18"),
        Map.entry("deeply_nested", "921e5d2d248920de5b11367f3f587c4f1008900d702b4f10a950901b4319e0e3"),
        Map.entry("kitchen_sink", "291416fca164d4b0e874dd18d0fae86599651d65b1ef93d5f22b05405b2a8518")
    );

    private File outDir;
    private Strategy context;

    @AfterAll
    static void writeDigestsSummary() throws IOException {
        File digestFile = new File("build/test-snapshots/DIGESTS.txt");
        digestFile.getParentFile().mkdirs();
        try (PrintWriter out = new PrintWriter(new FileWriter(digestFile))) {
            out.println("// Copy these to EXPECTED_DIGESTS map:");
            out.println();
            COLLECTED_DIGESTS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> out.printf("Map.entry(\"%s\", \"%s\"),%n", e.getKey(), e.getValue()));
        }
        System.out.println("\n=== DIGESTS WRITTEN TO: " + digestFile.getAbsolutePath() + " ===\n");
    }

    @BeforeEach
    void setup() {
        context = new SnapshotTest.MockStrategy(SEED);
    }

    // ========== VORONOI STRATEGY TESTS ==========

    @Test
    void voronoi_default() throws Exception {
        Region root = buildSimpleHierarchy(GenerationStrategyType.VORONOI, null);
        runSnapshot("voronoi_default", root, 2);
    }

    @Test
    void voronoi_low_relaxation() throws Exception {
        StrategySettings settings = StrategySettings.builder()
            .voronoiRelaxation(2)
            .build();
        Region root = buildSimpleHierarchy(GenerationStrategyType.VORONOI, settings);
        runSnapshot("voronoi_low_relaxation", root, 2);
    }

    @Test
    void voronoi_high_relaxation() throws Exception {
        StrategySettings settings = StrategySettings.builder()
            .voronoiRelaxation(20)
            .build();
        Region root = buildSimpleHierarchy(GenerationStrategyType.VORONOI, settings);
        runSnapshot("voronoi_high_relaxation", root, 2);
    }

    // ========== SUBDIVISION STRATEGY TESTS ==========

    @Test
    void subdivision_default() throws Exception {
        Region root = buildSimpleHierarchy(GenerationStrategyType.SUBDIVISION, null);
        runSnapshot("subdivision_default", root, 2);
    }

    @Test
    void subdivision_low_jitter() throws Exception {
        StrategySettings settings = StrategySettings.builder()
            .subdivisionJitter(0.0f)
            .build();
        Region root = buildSimpleHierarchy(GenerationStrategyType.SUBDIVISION, settings);
        runSnapshot("subdivision_low_jitter", root, 2);
    }

    @Test
    void subdivision_high_jitter() throws Exception {
        StrategySettings settings = StrategySettings.builder()
            .subdivisionJitter(0.5f)
            .build();
        Region root = buildSimpleHierarchy(GenerationStrategyType.SUBDIVISION, settings);
        runSnapshot("subdivision_high_jitter", root, 2);
    }

    // ========== TEMPLATE STRATEGY TESTS ==========

    @Test
    void template_binary() throws Exception {
        StrategySettings settings = StrategySettings.builder()
            .template(StrategySettings.TemplateType.BINARY)
            .build();
        Region root = buildBinaryHierarchy(settings);
        runSnapshot("template_binary", root, 2);
    }

    @Test
    void template_triangle() throws Exception {
        StrategySettings settings = StrategySettings.builder()
            .template(StrategySettings.TemplateType.TRIANGLE)
            .build();
        Region root = buildTriangleHierarchy(settings);
        runSnapshot("template_triangle", root, 2);
    }

    @Test
    void template_center_surround() throws Exception {
        StrategySettings settings = StrategySettings.builder()
            .template(StrategySettings.TemplateType.CENTER_SURROUND)
            .build();
        Region root = buildCenterSurroundHierarchy(settings, null);
        runSnapshot("template_center_surround", root, 2);
    }

    @Test
    void template_center_surround_named() throws Exception {
        // Explicitly name a non-dominant region as center
        StrategySettings settings = StrategySettings.builder()
            .template(StrategySettings.TemplateType.CENTER_SURROUND)
            .centerSurround("SMALL_CENTER")
            .build();
        Region root = buildCenterSurroundHierarchy(settings, "SMALL_CENTER");
        runSnapshot("template_center_surround_named", root, 2);
    }

    @Test
    void template_radial() throws Exception {
        StrategySettings settings = StrategySettings.builder()
            .template(StrategySettings.TemplateType.RADIAL)
            .build();
        Region root = buildRadialHierarchy(settings);
        runSnapshot("template_radial", root, 2);
    }

    // ========== HEX STRATEGY TESTS ==========

    @Test
    void hex_default() throws Exception {
        Region root = buildHexHierarchy(null);
        runSnapshot("hex_default", root, 2);  // Depth 2 shows hex tiles
    }

    @Test
    void hex_with_ring() throws Exception {
        StrategySettings settings = StrategySettings.builder()
            .hexRing("BORDER")
            .build();
        Region root = buildHexHierarchy(settings);
        runSnapshot("hex_with_ring", root, 2);  // Depth 2 shows tiles + ring borders
    }

    // ========== NESTED STRATEGY TESTS ==========

    @Test
    void nested_hex_voronoi() throws Exception {
        // HEX at root -> VORONOI for children
        Region root = buildNestedHexVoronoi();
        runSnapshot("nested_hex_voronoi", root, 2);
    }

    @Test
    void nested_hex_subdivision() throws Exception {
        // HEX at root -> SUBDIVISION for children
        Region root = buildNestedHexSubdivision();
        runSnapshot("nested_hex_subdivision", root, 2);
    }

    @Test
    void nested_voronoi_template() throws Exception {
        // VORONOI at root -> TEMPLATE for children
        Region root = buildNestedVoronoiTemplate();
        runSnapshot("nested_voronoi_template", root, 3);
    }

    @Test
    void nested_subdivision_template() throws Exception {
        // SUBDIVISION at root -> TEMPLATE for children
        Region root = buildNestedSubdivisionTemplate();
        runSnapshot("nested_subdivision_template", root, 3);
    }

    @Test
    void deeply_nested() throws Exception {
        // HEX -> VORONOI -> SUBDIVISION -> TEMPLATE (4 levels)
        Region root = buildDeeplyNested();
        runSnapshot("deeply_nested", root, 4);
    }

    // ========== KITCHEN SINK TEST ==========

    @Test
    void kitchen_sink() throws Exception {
        Region root = buildKitchenSink();
        runSnapshot("kitchen_sink", root, 4);
    }

    // ========== HIERARCHY BUILDERS ==========

    private Region buildSimpleHierarchy(GenerationStrategyType strategy, StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(strategy)
                .settings(settings)
                .child("ZONE_A", a -> a.budget(100))
                .child("ZONE_B", b -> b.budget(200))
                .child("ZONE_C", c -> c.budget(150))
                .child("ZONE_D", d -> d.budget(50)));
        return registry.build("WORLD");
    }

    private Region buildBinaryHierarchy(StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("LEFT", a -> a.budget(100))
                .child("RIGHT", b -> b.budget(100)));
        return registry.build("WORLD");
    }

    private Region buildTriangleHierarchy(StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("NORTH", a -> a.budget(100))
                .child("SOUTHWEST", b -> b.budget(100))
                .child("SOUTHEAST", c -> c.budget(100)));
        return registry.build("WORLD");
    }

    private Region buildCenterSurroundHierarchy(StrategySettings settings, String centerName) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("CAPITAL", c -> c.budget(centerName == null ? 300 : 50))
                .child("VILLAGE_N", a -> a.budget(100))
                .child("VILLAGE_E", b -> b.budget(100))
                .child("VILLAGE_S", c2 -> c2.budget(100))
                .child("VILLAGE_W", d -> d.budget(100))
                .child("SMALL_CENTER", sc -> sc.budget(centerName != null ? 300 : 50)));
        return registry.build("WORLD");
    }

    private Region buildRadialHierarchy(StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("SECTOR_1", a -> a.budget(80))
                .child("SECTOR_2", b -> b.budget(80))
                .child("SECTOR_3", c -> c.budget(80))
                .child("SECTOR_4", d -> d.budget(80))
                .child("SECTOR_5", e -> e.budget(80))
                .child("SECTOR_6", f -> f.budget(80)));
        return registry.build("WORLD");
    }

    private Region buildHexHierarchy(StrategySettings settings) {
        // Simple hex grid with multiple tile types to show pattern
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .settings(settings)
            .child("PLAINS_HEX", plains -> plains.budget(100))
            .child("FOREST_HEX", forest -> forest.budget(80))
            .child("MOUNTAIN_HEX", mountain -> mountain.budget(50))
            .child("BORDER", border -> border.budget(30)); // Ring region
        return registry.build("WORLD");
    }

    private Region buildNestedHexVoronoi() {
        // Multiple hex tile types so hex grid is visible
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("PLAINS_REALM", plains -> plains
                .budget(100)
                .strategy(GenerationStrategyType.VORONOI)
                .child("FARMLAND", f -> f.budget(60))
                .child("VILLAGE_N", v -> v.budget(20))
                .child("GRASSLAND", g -> g.budget(40)))
            .child("FOREST_REALM", forest -> forest
                .budget(80)
                .strategy(GenerationStrategyType.VORONOI)
                .child("DENSE_FOREST", d -> d.budget(50))
                .child("GROVE", g -> g.budget(30))
                .child("CLEARING", c -> c.budget(20)))
            .child("MOUNTAIN_REALM", mountain -> mountain
                .budget(50)
                .strategy(GenerationStrategyType.VORONOI)
                .child("PEAK_1", p -> p.budget(40))
                .child("VALLEY", v -> v.budget(30))
                .child("MOUNTAIN_PASS", m -> m.budget(20)));
        return registry.build("WORLD");
    }

    private Region buildNestedHexSubdivision() {
        // Multiple hex tile types so hex grid is visible
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("PLAINS_REALM", plains -> plains
                .budget(100)
                .strategy(GenerationStrategyType.SUBDIVISION)
                .settings(StrategySettings.builder().subdivisionJitter(0.1f).build())
                .child("NORTH_LANDS", n -> n.budget(200))
                .child("SOUTH_LANDS", s -> s.budget(200)))
            .child("FOREST_REALM", forest -> forest
                .budget(80)
                .strategy(GenerationStrategyType.SUBDIVISION)
                .settings(StrategySettings.builder().subdivisionJitter(0.1f).build())
                .child("EAST_LANDS", e -> e.budget(150))
                .child("WEST_LANDS", w -> w.budget(150)))
            .child("MOUNTAIN_REALM", mountain -> mountain
                .budget(50)
                .strategy(GenerationStrategyType.SUBDIVISION)
                .settings(StrategySettings.builder().subdivisionJitter(0.1f).build())
                .child("PEAK_1", p -> p.budget(100))
                .child("VALLEY", v -> v.budget(100)));
        return registry.build("WORLD");
    }

    private Region buildNestedVoronoiTemplate() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.VORONOI)
                .child("CAPITAL_REGION", cap -> cap
                    .budget(300)
                    .strategy(GenerationStrategyType.TEMPLATE)
                    .settings(StrategySettings.builder()
                        .template(StrategySettings.TemplateType.CENTER_SURROUND)
                        .centerSurround("PALACE")
                        .build())
                    .child("PALACE", p -> p.budget(100))
                    .child("GARDENS", g -> g.budget(100))
                    .child("MARKET", m -> m.budget(100)))
                .child("FARMLAND", farm -> farm.budget(200))
                .child("FOREST", forest -> forest.budget(150)));
        return registry.build("WORLD");
    }

    private Region buildNestedSubdivisionTemplate() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.SUBDIVISION)
                .child("CITY", city -> city
                    .budget(250)
                    .strategy(GenerationStrategyType.TEMPLATE)
                    .settings(StrategySettings.builder()
                        .template(StrategySettings.TemplateType.RADIAL)
                        .build())
                    .child("DISTRICT_1", d1 -> d1.budget(50))
                    .child("DISTRICT_2", d2 -> d2.budget(50))
                    .child("DISTRICT_3", d3 -> d3.budget(50))
                    .child("DISTRICT_4", d4 -> d4.budget(50)))
                .child("OUTSKIRTS", out -> out.budget(200))
                .child("WILDERNESS", wild -> wild.budget(250)));
        return registry.build("WORLD");
    }

    private Region buildDeeplyNested() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("COSMOS")
            .strategy(GenerationStrategyType.HEX)
            .child("GALAXY", galaxy -> galaxy
                .strategy(GenerationStrategyType.VORONOI)
                .child("STAR_SYSTEM", system -> system
                    .budget(400)
                    .strategy(GenerationStrategyType.SUBDIVISION)
                    .child("INNER_PLANETS", inner -> inner
                        .budget(200)
                        .strategy(GenerationStrategyType.TEMPLATE)
                        .settings(StrategySettings.builder()
                            .template(StrategySettings.TemplateType.RADIAL)
                            .build())
                        .child("MERCURY", m -> m.budget(30))
                        .child("VENUS", v -> v.budget(50))
                        .child("EARTH", e -> e.budget(60))
                        .child("MARS", mars -> mars.budget(40)))
                    .child("OUTER_PLANETS", outer -> outer.budget(200)))
                .child("NEBULA", neb -> neb.budget(200))
                .child("VOID", v -> v.budget(100)));
        return registry.build("COSMOS");
    }

    private Region buildKitchenSink() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .settings(StrategySettings.builder().hexRing("OCEAN").build())
            
            // Main landmass using Voronoi
            .child("LANDMASS", land -> land
                .strategy(GenerationStrategyType.VORONOI)
                
                // Kingdom with Template center-surround
                .child("KINGDOM", kingdom -> kingdom
                    .budget(400)
                    .strategy(GenerationStrategyType.TEMPLATE)
                    .settings(StrategySettings.builder()
                        .template(StrategySettings.TemplateType.CENTER_SURROUND)
                        .centerSurround("CASTLE")
                        .build())
                    .child("CASTLE", castle -> castle
                        .budget(100)
                        .strategy(GenerationStrategyType.SUBDIVISION)
                        .settings(StrategySettings.builder().subdivisionJitter(0.1f).build())
                        .child("KEEP", k -> k.budget(50))
                        .child("COURTYARD", cy -> cy.budget(30))
                        .child("BARRACKS", b -> b.budget(20)))
                    .child("VILLAGE_NORTH", vn -> vn.budget(75))
                    .child("VILLAGE_SOUTH", vs -> vs.budget(75))
                    .child("FARMLAND", farm -> farm.budget(150)))
                
                // Wilderness with Subdivision
                .child("WILDERNESS", wild -> wild
                    .budget(350)
                    .strategy(GenerationStrategyType.SUBDIVISION)
                    .settings(StrategySettings.builder().subdivisionJitter(0.3f).build())
                    .child("DENSE_FOREST", df -> df
                        .budget(150)
                        .strategy(GenerationStrategyType.VORONOI)
                        .child("GROVE", g -> g.budget(50))
                        .child("THICKET", t -> t.budget(50))
                        .child("CLEARING", c -> c.budget(50)))
                    .child("SWAMP", swamp -> swamp.budget(100))
                    .child("HILLS", hills -> hills.budget(100)))
                
                // Mountains with Radial template
                .child("MOUNTAINS", mtns -> mtns
                    .budget(250)
                    .strategy(GenerationStrategyType.TEMPLATE)
                    .settings(StrategySettings.builder()
                        .template(StrategySettings.TemplateType.RADIAL)
                        .build())
                    .child("PEAK_1", p1 -> p1.budget(60))
                    .child("PEAK_2", p2 -> p2.budget(60))
                    .child("PEAK_3", p3 -> p3.budget(60))
                    .child("VALLEY", val -> val.budget(70))))
            
            // Ocean (ring region)
            .child("OCEAN", ocean -> ocean
                .budget(1000)
                .strategy(GenerationStrategyType.VORONOI)
                .child("DEEP_SEA", deep -> deep.budget(500))
                .child("SHALLOWS", shallow -> shallow.budget(300))
                .child("REEF", reef -> reef.budget(200)));
        
        return registry.build("WORLD");
    }

    // ========== SNAPSHOT RUNNER ==========

    private void runSnapshot(String testName, Region root, int maxDepth) throws Exception {
        World.setRoot(root);
        outDir = new File("build/test-snapshots/" + testName);
        outDir.mkdirs();

        int step = WORLD_SIZE / IMG_SIZE;
        
        // Create images for each depth level
        List<BufferedImage> depthImages = new ArrayList<>();
        for (int d = 0; d <= maxDepth; d++) {
            depthImages.add(new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB));
        }
        
        // Combined image showing all layers
        BufferedImage imgCombined = new BufferedImage(IMG_SIZE, IMG_SIZE, BufferedImage.TYPE_INT_RGB);
        
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        
        // Sample statistics
        Map<Integer, Map<String, Integer>> depthCounts = new HashMap<>();
        for (int d = 0; d <= maxDepth; d++) {
            depthCounts.put(d, new HashMap<>());
        }

        for (int y = 0; y < IMG_SIZE; y++) {
            for (int x = 0; x < IMG_SIZE; x++) {
                // Center the view around origin
                int wx = (x - IMG_SIZE / 2) * step;
                int wz = (y - IMG_SIZE / 2) * step;

                // Sample each depth
                for (int d = 1; d <= maxDepth; d++) {
                    Region region = World.getRegionAtDepth(wx, wz, context, d);
                    long regionSeed = World.getRegionSeedAtDepth(wx, wz, context, d);
                    
                    // Update statistics
                    depthCounts.get(d).merge(region.name(), 1, Integer::sum);
                    
                    // Update digest
                    updateDigest(digest, region.name().hashCode());
                    updateDigest(digest, (int) regionSeed);
                    
                    // Compute color
                    int color = getRegionColor(region);
                    
                    // Detect edges
                    Region right = World.getRegionAtDepth(wx + step, wz, context, d);
                    long rightSeed = World.getRegionSeedAtDepth(wx + step, wz, context, d);
                    Region down = World.getRegionAtDepth(wx, wz + step, context, d);
                    long downSeed = World.getRegionSeedAtDepth(wx, wz + step, context, d);
                    
                    boolean isEdge = !region.name().equals(right.name()) || regionSeed != rightSeed ||
                                     !region.name().equals(down.name()) || regionSeed != downSeed;
                    
                    if (isEdge) {
                        color = darken(color, 0.5f);
                    }
                    
                    depthImages.get(d).setRGB(x, y, color);
                }
                
                // Combined: use deepest level with parent edges overlaid
                Region leafRegion = World.getRegionAtDepth(wx, wz, context, maxDepth);
                int color = getRegionColor(leafRegion);
                
                // Check for edges at all levels
                for (int d = 1; d <= maxDepth; d++) {
                    Region region = World.getRegionAtDepth(wx, wz, context, d);
                    long regionSeed = World.getRegionSeedAtDepth(wx, wz, context, d);
                    
                    Region right = World.getRegionAtDepth(wx + step, wz, context, d);
                    long rightSeed = World.getRegionSeedAtDepth(wx + step, wz, context, d);
                    Region down = World.getRegionAtDepth(wx, wz + step, context, d);
                    long downSeed = World.getRegionSeedAtDepth(wx, wz + step, context, d);
                    
                    if (!region.name().equals(right.name()) || regionSeed != rightSeed ||
                        !region.name().equals(down.name()) || regionSeed != downSeed) {
                        // Edge color based on depth level
                        float edgeBrightness = 0.3f + 0.2f * (maxDepth - d);
                        color = darken(color, edgeBrightness);
                    }
                }
                
                imgCombined.setRGB(x, y, color);
            }
        }

        // Save images
        for (int d = 1; d <= maxDepth; d++) {
            ImageIO.write(depthImages.get(d), "png", new File(outDir, "depth_" + d + ".png"));
        }
        ImageIO.write(imgCombined, "png", new File(outDir, "combined.png"));
        
        // Generate legend
        generateLegend(testName, depthCounts, maxDepth);

        // Check digest
        String actualDigest = java.util.HexFormat.of().formatHex(digest.digest());
        COLLECTED_DIGESTS.put(testName, actualDigest);
        System.out.println("[" + testName + "] Digest: " + actualDigest);
        
        // Print statistics
        for (int d = 1; d <= maxDepth; d++) {
            System.out.println("  Depth " + d + ": " + depthCounts.get(d));
        }
        
        String expected = EXPECTED_DIGESTS.get(testName);
        if (!"PENDING".equals(expected)) {
            assertEquals(expected, actualDigest, "Snapshot digest mismatch for " + testName);
        }
    }

    private void generateLegend(String testName, Map<Integer, Map<String, Integer>> depthCounts, int maxDepth) throws IOException {
        // Collect all unique region names
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

    // ========== UTILITIES ==========

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
        // Use hash-based colors for consistent coloring
        int hash = name.hashCode();
        
        // Predefined colors for common names
        return switch (name) {
            // Terrain types
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
            
            // Civilization
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
            
            // Wilderness
            case "WILDERNESS" -> 0x3CB371;
            case "OUTSKIRTS" -> 0x8FBC8F;
            
            // Generic zones
            case "ZONE_A", "SECTOR_1", "DISTRICT_1" -> 0xFF6347;
            case "ZONE_B", "SECTOR_2", "DISTRICT_2" -> 0x4682B4;
            case "ZONE_C", "SECTOR_3", "DISTRICT_3" -> 0x32CD32;
            case "ZONE_D", "SECTOR_4", "DISTRICT_4" -> 0xBA55D3;
            case "SECTOR_5" -> 0xFF69B4;
            case "SECTOR_6" -> 0x00CED1;
            
            // Directions
            case "LEFT", "WEST_LANDS" -> 0xFFA07A;
            case "RIGHT", "EAST_LANDS" -> 0x87CEFA;
            case "NORTH", "NORTH_LANDS" -> 0xE6E6FA;
            case "SOUTHWEST", "SOUTH_LANDS" -> 0xF0FFF0;
            case "SOUTHEAST" -> 0xFFE4E1;
            
            // Space
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
            
            // Misc
            case "REALM", "WORLD", "ROOT" -> 0x808080;
            case "TILE" -> 0xE0A060;  // Golden tan for hex tiles
            case "PLAINS_HEX", "PLAINS_REALM" -> 0xC2B280;  // Khaki
            case "FOREST_HEX", "FOREST_REALM" -> 0x355E3B;  // Hunter green
            case "MOUNTAIN_HEX", "MOUNTAIN_REALM" -> 0x8B7355;  // Burlywood
            case "GRASSLAND" -> 0x7CFC00;  // Lawn green
            case "BORDER" -> 0x2F4F4F;
            case "SMALL_CENTER" -> 0xFF1493;
            
            // Hash fallback
            default -> {
                int r = ((hash >> 16) & 0x7F) + 0x40;
                int g = ((hash >> 8) & 0x7F) + 0x40;
                int b = (hash & 0x7F) + 0x40;
                yield (r << 16) | (g << 8) | b;
            }
        };
    }
}
