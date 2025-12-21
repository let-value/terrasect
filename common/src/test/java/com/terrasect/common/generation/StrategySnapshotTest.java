package com.terrasect.common.generation;

import com.terrasect.common.api.Region;
import com.terrasect.common.api.RegionRegistry;
import com.terrasect.common.api.Context;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.generation.definition.StrategySettings;
import com.terrasect.common.runtime.World;
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
    // Updated for MC 1.21.11 (different RNG/float behavior)
    private static final Map<String, String> EXPECTED_DIGESTS = Map.ofEntries(
        Map.entry("deeply_nested", "ad7502cbaf31598771c52d1af1b1faa94cc8fbcc19b0f9eb4f4b79d2c2b46324"),
        Map.entry("hex_default", "68acdb9b8d9c3b35da2a313da0404b5e1d80a866f292622a2b9a8ce6eba17d7b"),
        Map.entry("hex_with_ring", "e932860fe54368f1df4e04b7b69c94f83205f7668e387a7769e1722377af2bca"),
        Map.entry("kitchen_sink", "521c8ccf28bc47a3e10c63367649f60e56965cd2bf7eac9499b1df53f19a17a4"),
        Map.entry("nested_hex_subdivision", "1f1406cb315c0aa6d23b3f28e1c07441b1be24836e7365af1f75804ce1ec23ad"),
        Map.entry("nested_hex_voronoi", "541a4aa11df4ff988f5e39d56ed548781deb605cc6524bab6bb82012e650448d"),
        Map.entry("nested_subdivision_template", "1a93f63bf663eaaf1d650cf9632c9c3d73f94097e8a696fa680474c777d68191"),
        Map.entry("nested_voronoi_template", "42c4177fe9d66630674919cd72a452646cda3eb300b8731107e3753959127e25"),
        Map.entry("subdivision_default", "7b45100d40faaedd0aa19c0e12b059524bc852e3dfaf166a033978c87926cc12"),
        Map.entry("subdivision_high_jitter", "8c3a8ad8ace906fa4dd9c736eab06db6f1b3f74aefa964026ac69848728bc7ec"),
        Map.entry("subdivision_low_jitter", "3123bc3190c1fb04df9721720f68b0fd53df5634211058eb5fe2877937efe8b4"),
        Map.entry("template_binary", "b00a0860a1f1d25c43535e69b9832c5cd8de4282ce003a6fa03051d2d9160f04"),
        Map.entry("template_center_surround", "134fd07cacde9bbf419e68b8881ff5fcc696b58b8501b59531906ff0b97e7870"),
        Map.entry("template_center_surround_named", "afe789b6a3c8348f0a6c589f9c9953832aecab1e79618ad3388a2c3504c81a9d"),
        Map.entry("template_radial", "08a3e775f0a5b726b9062e78e00dd4c86105becdf9d3b945de08365756b19cf2"),
        Map.entry("template_triangle", "2efb9a9bfd02d9e583fede0c27880b31d150767e108ef3d08d31b548b0b2c147"),
        Map.entry("voronoi_default", "6d1f8d3e82dba41636fb6f3404fd6ba547d9cdcb0fcedfd7acdcab6bce313665"),
        Map.entry("voronoi_high_relaxation", "dab01562315178054408e03822906b5e8ff42258b756090674e1c5798a4d448e"),
        Map.entry("voronoi_low_relaxation", "6f1904742d5e00f028935f826bcc14604c5f9ec51799dd8dc222875ae8eaacab")
    );

    private File outDir;
    private Context context;

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
                .child("ZONE_A", a -> a.radius(100))
                .child("ZONE_B", b -> b.radius(200))
                .child("ZONE_C", c -> c.radius(150))
                .child("ZONE_D", d -> d.radius(50)));
        return registry.build("WORLD");
    }

    private Region buildBinaryHierarchy(StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("LEFT", a -> a.radius(100))
                .child("RIGHT", b -> b.radius(100)));
        return registry.build("WORLD");
    }

    private Region buildTriangleHierarchy(StrategySettings settings) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.TEMPLATE)
                .settings(settings)
                .child("NORTH", a -> a.radius(100))
                .child("SOUTHWEST", b -> b.radius(100))
                .child("SOUTHEAST", c -> c.radius(100)));
        return registry.build("WORLD");
    }

    private Region buildCenterSurroundHierarchy(StrategySettings settings, String centerName) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.TEMPLATE)
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
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.TEMPLATE)
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
        // Simple hex grid with multiple tile types to show pattern
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
            .child("BORDER", border -> border.radius(30)); // Ring region
        return registry.build("WORLD");
    }

    private Region buildNestedHexVoronoi() {
        // Multiple hex tile types so hex grid is visible
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("PLAINS_REALM", plains -> plains
                .radius(100)
                .strategy(GenerationStrategyType.VORONOI)
                .child("FARMLAND", f -> f.radius(60))
                .child("VILLAGE_N", v -> v.radius(20))
                .child("GRASSLAND", g -> g.radius(40)))
            .child("FOREST_REALM", forest -> forest
                .radius(80)
                .strategy(GenerationStrategyType.VORONOI)
                .child("DENSE_FOREST", d -> d.radius(50))
                .child("GROVE", g -> g.radius(30))
                .child("CLEARING", c -> c.radius(20)))
            .child("MOUNTAIN_REALM", mountain -> mountain
                .radius(50)
                .strategy(GenerationStrategyType.VORONOI)
                .child("PEAK_1", p -> p.radius(40))
                .child("VALLEY", v -> v.radius(30))
                .child("MOUNTAIN_PASS", m -> m.radius(20)));
        return registry.build("WORLD");
    }

    private Region buildNestedHexSubdivision() {
        // Multiple hex tile types so hex grid is visible
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("PLAINS_REALM", plains -> plains
                .radius(100)
                .strategy(GenerationStrategyType.SUBDIVISION)
                .settings(StrategySettings.builder().subdivisionJitter(0.1f).build())
                .child("NORTH_LANDS", n -> n.radius(200))
                .child("SOUTH_LANDS", s -> s.radius(200)))
            .child("FOREST_REALM", forest -> forest
                .radius(80)
                .strategy(GenerationStrategyType.SUBDIVISION)
                .settings(StrategySettings.builder().subdivisionJitter(0.1f).build())
                .child("EAST_LANDS", e -> e.radius(150))
                .child("WEST_LANDS", w -> w.radius(150)))
            .child("MOUNTAIN_REALM", mountain -> mountain
                .radius(50)
                .strategy(GenerationStrategyType.SUBDIVISION)
                .settings(StrategySettings.builder().subdivisionJitter(0.1f).build())
                .child("PEAK_1", p -> p.radius(100))
                .child("VALLEY", v -> v.radius(100)));
        return registry.build("WORLD");
    }

    private Region buildNestedVoronoiTemplate() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.VORONOI)
                .child("CAPITAL_REGION", cap -> cap
                    .radius(300)
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
        registry.region("WORLD")
            .strategy(GenerationStrategyType.HEX)
            .child("REALM", realm -> realm
                .strategy(GenerationStrategyType.SUBDIVISION)
                .child("CITY", city -> city
                    .radius(250)
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
        registry.region("COSMOS")
            .strategy(GenerationStrategyType.HEX)
            .child("GALAXY", galaxy -> galaxy
                .strategy(GenerationStrategyType.VORONOI)
                .child("STAR_SYSTEM", system -> system
                    .radius(400)
                    .strategy(GenerationStrategyType.SUBDIVISION)
                    .child("INNER_PLANETS", inner -> inner
                        .radius(200)
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
            
            // Main landmass using Voronoi
            .child("LANDMASS", land -> land
                .strategy(GenerationStrategyType.VORONOI)
                
                // Kingdom with Template center-surround
                .child("KINGDOM", kingdom -> kingdom
                    .radius(400)
                    .strategy(GenerationStrategyType.TEMPLATE)
                    .settings(StrategySettings.builder()
                        .template(StrategySettings.TemplateType.CENTER_SURROUND)
                        .centerSurround("CASTLE")
                        .build())
                    .child("CASTLE", castle -> castle
                        .radius(100)
                        .strategy(GenerationStrategyType.SUBDIVISION)
                        .settings(StrategySettings.builder().subdivisionJitter(0.1f).build())
                        .child("KEEP", k -> k.radius(50))
                        .child("COURTYARD", cy -> cy.radius(30))
                        .child("BARRACKS", b -> b.radius(20)))
                    .child("VILLAGE_NORTH", vn -> vn.radius(75))
                    .child("VILLAGE_SOUTH", vs -> vs.radius(75))
                    .child("FARMLAND", farm -> farm.radius(150)))
                
                // Wilderness with Subdivision
                .child("WILDERNESS", wild -> wild
                    .radius(350)
                    .strategy(GenerationStrategyType.SUBDIVISION)
                    .settings(StrategySettings.builder().subdivisionJitter(0.3f).build())
                    .child("DENSE_FOREST", df -> df
                        .radius(150)
                        .strategy(GenerationStrategyType.VORONOI)
                        .child("GROVE", g -> g.radius(50))
                        .child("THICKET", t -> t.radius(50))
                        .child("CLEARING", c -> c.radius(50)))
                    .child("SWAMP", swamp -> swamp.radius(100))
                    .child("HILLS", hills -> hills.radius(100)))
                
                // Mountains with Radial template
                .child("MOUNTAINS", mtns -> mtns
                    .radius(250)
                    .strategy(GenerationStrategyType.TEMPLATE)
                    .settings(StrategySettings.builder()
                        .template(StrategySettings.TemplateType.RADIAL)
                        .build())
                    .child("PEAK_1", p1 -> p1.radius(60))
                    .child("PEAK_2", p2 -> p2.radius(60))
                    .child("PEAK_3", p3 -> p3.radius(60))
                    .child("VALLEY", val -> val.radius(70))))
            
            // Ocean (ring region)
            .child("OCEAN", ocean -> ocean
                .radius(1000)
                .strategy(GenerationStrategyType.VORONOI)
                .child("DEEP_SEA", deep -> deep.radius(500))
                .child("SHALLOWS", shallow -> shallow.radius(300))
                .child("REEF", reef -> reef.radius(200)));
        
        return registry.build("WORLD");
    }

    // ========== SNAPSHOT RUNNER ==========

    private void runSnapshot(String testName, Region root, int maxDepth) throws Exception {
        World.register(World.OVERWORLD, root);
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
                    Region region = World.getRegionAtDepth(World.OVERWORLD, wx, wz, context, d);
                    long regionSeed = World.getRegionSeedAtDepth(World.OVERWORLD, wx, wz, context, d);
                    
                    // Update statistics
                    depthCounts.get(d).merge(region.name(), 1, Integer::sum);
                    
                    // Update digest
                    updateDigest(digest, region.name().hashCode());
                    updateDigest(digest, (int) regionSeed);
                    
                    // Compute color
                    int color = getRegionColor(region);
                    
                    // Detect edges
                    Region right = World.getRegionAtDepth(World.OVERWORLD, wx + step, wz, context, d);
                    long rightSeed = World.getRegionSeedAtDepth(World.OVERWORLD, wx + step, wz, context, d);
                    Region down = World.getRegionAtDepth(World.OVERWORLD, wx, wz + step, context, d);
                    long downSeed = World.getRegionSeedAtDepth(World.OVERWORLD, wx, wz + step, context, d);
                    
                    boolean isEdge = !region.name().equals(right.name()) || regionSeed != rightSeed ||
                                     !region.name().equals(down.name()) || regionSeed != downSeed;
                    
                    if (isEdge) {
                        color = darken(color, 0.5f);
                    }
                    
                    depthImages.get(d).setRGB(x, y, color);
                }
                
                // Combined: use deepest level with parent edges overlaid
                Region leafRegion = World.getRegionAtDepth(World.OVERWORLD, wx, wz, context, maxDepth);
                int color = getRegionColor(leafRegion);
                
                // Check for edges at all levels
                for (int d = 1; d <= maxDepth; d++) {
                    Region region = World.getRegionAtDepth(World.OVERWORLD, wx, wz, context, d);
                    long regionSeed = World.getRegionSeedAtDepth(World.OVERWORLD, wx, wz, context, d);
                    
                    Region right = World.getRegionAtDepth(World.OVERWORLD, wx + step, wz, context, d);
                    long rightSeed = World.getRegionSeedAtDepth(World.OVERWORLD, wx + step, wz, context, d);
                    Region down = World.getRegionAtDepth(World.OVERWORLD, wx, wz + step, context, d);
                    long downSeed = World.getRegionSeedAtDepth(World.OVERWORLD, wx, wz + step, context, d);
                    
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
