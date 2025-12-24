package com.terrasect.common.generation;

import com.terrasect.common.Context;
import com.terrasect.common.definition.GenerationStrategyType;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for dimension-aware region root registration via World class.
 */
public class WorldTest {

    @BeforeEach
    void setUp() {
        World.clear();
    }

    @AfterEach
    void tearDown() {
        World.clear();
    }

    @Test
    void registersRootForDimension() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");

        World.register(overworldRoot, World.OVERWORLD);

        assertEquals("OVERWORLD", World.getRoot(World.OVERWORLD).name());
        assertTrue(World.hasRoot(World.OVERWORLD));
    }

    @Test
    void supportsDifferentRootsPerDimension() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        Region endRoot = buildSimpleRoot("END");

        World.register(overworldRoot, World.OVERWORLD);
        World.register(endRoot, World.THE_END);

        assertEquals("OVERWORLD", World.getRoot(World.OVERWORLD).name());
        assertEquals("END", World.getRoot(World.THE_END).name());

        assertEquals(2, World.getRegisteredDimensions().size());
    }

    @Test
    void returnsNullForUnregisteredDimension() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        World.register(overworldRoot, World.OVERWORLD);

        Region result = World.getRoot("mymod:custom_dimension");
        assertNull(result);

        assertEquals("OVERWORLD", World.getRoot(World.OVERWORLD).name());
    }

    @Test
    void returnsNullWhenNoDimensionsRegistered() {
        assertNull(World.getRoot("mymod:custom_dimension"));
        assertNull(World.getRoot(World.OVERWORLD));
    }

    @Test
    void allowsSameRootForMultipleDimensions() {
        Region sharedRoot = buildSimpleRoot("SHARED");

        World.register(sharedRoot,
                World.OVERWORLD,
                "mymod:overworld_copy",
                "mymod:alternate_world");

        assertEquals("SHARED", World.getRoot(World.OVERWORLD).name());
        assertEquals("SHARED", World.getRoot("mymod:overworld_copy").name());
        assertEquals("SHARED", World.getRoot("mymod:alternate_world").name());

        assertSame(
                World.getRoot(World.OVERWORLD),
                World.getRoot("mymod:overworld_copy"));
    }

    @Test
    void getRootReturnsNullForUnregistered() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        World.register(overworldRoot, World.OVERWORLD);

        assertNotNull(World.getRoot(World.OVERWORLD));
        assertNull(World.getRoot(World.THE_END));
    }

    @Test
    void clearRemovesAllRegistrations() {
        World.register(buildSimpleRoot("OVERWORLD"), World.OVERWORLD);
        World.register(buildSimpleRoot("END"), World.THE_END);

        World.clear();

        assertTrue(World.getRegisteredDimensions().isEmpty());
    }

    @Test
    void emptyRootCreatesValidEmptyRegion() {
        Region empty = World.emptyRoot("EMPTY_DIM");

        assertEquals("EMPTY_DIM", empty.name());
        assertTrue(empty.children().isEmpty());
        assertNotNull(empty.definition());
    }

    private Region buildSimpleRoot(String name) {
        RegionRegistry registry = new RegionRegistry();
        registry.region(name)
                .strategy(GenerationStrategyType.HEX)
                .radius(1000);
        return registry.build(name);
    }

    private Context createTestContext(long seed) {
        return new com.terrasect.common.Context() {
            @Override
            public long getSeed() {
                return seed;
            }

            @Override
            public long getInfluence(int x, int z) {
                return 0L;
            }
        };
    }

    /**
     * Test that anchoring a region causes it to appear at origin.
     * We create a simple hierarchy with distinct regions using HEX strategy,
     * which tiles the world with hexagonal cells. Each run anchors a different
     * region and verifies it appears at (0,0).
     */
    @Test
    void anchoredRegion_RegionA_appearsAtOrigin() {
        testAnchoredRegionAppearsAtOrigin("REGION_A");
    }

    @Test
    void anchoredRegion_RegionB_appearsAtOrigin() {
        testAnchoredRegionAppearsAtOrigin("REGION_B");
    }

    @Test
    void anchoredRegion_RegionC_appearsAtOrigin() {
        testAnchoredRegionAppearsAtOrigin("REGION_C");
    }

    @Test
    void anchoredRegion_RegionD_appearsAtOrigin() {
        testAnchoredRegionAppearsAtOrigin("REGION_D");
    }

    private void testAnchoredRegionAppearsAtOrigin(String anchoredName) {
        long seed = 12345L;
        Context context = createTestContext(seed);

        Region root = buildTestRegionsWithAnchor(anchoredName);
        World.register(root, World.OVERWORLD);

        World.initialize(context);

        Region atOrigin = World.traverse(context, 0, 0).region;
        assertNotNull(atOrigin, "Should find a region at origin after anchoring");
        assertEquals(anchoredName, atOrigin.name(),
                "Region at origin should be the anchored region '" + anchoredName + "'");
    }

    /**
     * Builds a hierarchy with 4 child regions using HEX strategy.
     * HEX creates a hexagonal grid, ensuring regions are spatially distinct.
     */
    private Region buildTestRegionsWithAnchor(String anchoredName) {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
                .strategy(GenerationStrategyType.HEX)
                .radius(5000)
                .child("REGION_A", r -> {
                    r.radius(1000);
                    if ("REGION_A".equals(anchoredName))
                        r.anchoredToOrigin();
                })
                .child("REGION_B", r -> {
                    r.radius(1000);
                    if ("REGION_B".equals(anchoredName))
                        r.anchoredToOrigin();
                })
                .child("REGION_C", r -> {
                    r.radius(1000);
                    if ("REGION_C".equals(anchoredName))
                        r.anchoredToOrigin();
                })
                .child("REGION_D", r -> {
                    r.radius(1000);
                    if ("REGION_D".equals(anchoredName))
                        r.anchoredToOrigin();
                });
        return registry.build("ROOT");
    }

    /**
     * Edge case: Anchored region at depth 4 (deeply nested).
     * Verifies that even small, deeply nested regions can be anchored to origin.
     */
    @Test
    void deeplyNestedAnchoredRegion_SamplingOriginReturnsAnchoredRegion() {
        long seed = 54321L;
        Context context = createTestContext(seed);

        Region root = buildDeeplyNestedHierarchy();
        World.register(root, World.OVERWORLD);

        World.initialize(context);

        Region atOriginDepth4 = World.traverse(context, 0, 0, 4).region;
        assertNotNull(atOriginDepth4, "Should find a region at origin depth 4");
        assertEquals("SPAWN_POINT", atOriginDepth4.name(),
                "Region at origin should be the deeply nested anchored region 'SPAWN_POINT'");

        Region atOriginFull = World.traverse(context, 0, 0).region;
        assertNotNull(atOriginFull, "Should find a region at origin (full depth)");
        assertEquals("SPAWN_POINT", atOriginFull.name(),
                "Full depth sampling at origin should also return 'SPAWN_POINT'");
    }

    /**
     * Builds a 4-level deep hierarchy:
     * ROOT (level 0)
     * └── CONTINENT (level 1)
     * └── KINGDOM (level 2)
     * └── PROVINCE (level 3)
     * └── SPAWN_POINT (level 4, anchored, small radius)
     */
    private Region buildDeeplyNestedHierarchy() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
                .strategy(GenerationStrategyType.HEX)
                .radius(50000)
                .child("CONTINENT", continent -> continent
                        .strategy(GenerationStrategyType.SUBDIVISION)
                        .radius(10000)
                        .child("KINGDOM", kingdom -> kingdom
                                .strategy(GenerationStrategyType.VORONOI)
                                .radius(3000)
                                .child("PROVINCE", province -> province
                                        .strategy(GenerationStrategyType.SUBDIVISION)
                                        .radius(1000)
                                        .child("SPAWN_POINT", spawn -> spawn
                                                .radius(200)
                                                .anchoredToOrigin())
                                        .child("WILDERNESS", wild -> wild
                                                .radius(800)))));
        return registry.build("ROOT");
    }

    /**
     * Verifies that without anchoring, different regions appear at origin
     * based on natural procedural generation.
     */
    @Test
    void withoutAnchoring_originRegionIsProcedural() {
        long seed = 99999L;
        Context context = createTestContext(seed);

        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
                .strategy(GenerationStrategyType.HEX)
                .radius(5000)
                .child("REGION_A", r -> r.radius(1000))
                .child("REGION_B", r -> r.radius(1000))
                .child("REGION_C", r -> r.radius(1000));
        Region root = registry.build("ROOT");

        World.register(root, World.OVERWORLD);
        World.initialize(context);

        Region atOrigin = World.traverse(context, 0, 0).region;
        assertNotNull(atOrigin, "Should find some region at origin");

        assertTrue(
                atOrigin.name().equals("REGION_A") ||
                        atOrigin.name().equals("REGION_B") ||
                        atOrigin.name().equals("REGION_C"),
                "Origin region should be one of the defined regions");
    }

    /**
     * Verifies that the same seed produces the same offset for the same anchored
     * region.
     */
    @Test
    void anchoredRegion_sameSeededProducesSameOffset() {
        long seed = 77777L;
        var context = createTestContext(seed);

        Region root1 = buildTestRegionsWithAnchor("REGION_B");
        World.register(root1, World.OVERWORLD);
        World.initialize(context);

        Region atOrigin1 = World.traverse(context, 0, 0).region;
        assertNotNull(atOrigin1);
        assertEquals("REGION_B", atOrigin1.name());

        World.clear();

        Region root2 = buildTestRegionsWithAnchor("REGION_B");
        World.register(root2, World.OVERWORLD);
        World.initialize(context);

        Region atOrigin2 = World.traverse(context, 0, 0).region;
        assertNotNull(atOrigin2);
        assertEquals("REGION_B", atOrigin2.name(),
                "Re-initialized world with same seed should produce same result at origin");
    }

    /**
     * Verifies that different anchored regions produce different offsets.
     * This ensures the offset calculation is actually finding each specific region.
     */
    @Test
    void differentAnchoredRegions_produceDifferentResults() {
        long seed = 88888L;
        var context = createTestContext(seed);

        Region root1 = buildTestRegionsWithAnchor("REGION_A");
        World.register(root1, World.OVERWORLD);
        World.initialize(context);
        Region atOrigin1 = World.traverse(context, 0, 0).region;
        assertEquals("REGION_A", atOrigin1.name());

        World.clear();

        Region root2 = buildTestRegionsWithAnchor("REGION_C");
        World.register(root2, World.OVERWORLD);
        World.initialize(context);
        Region atOrigin2 = World.traverse(context, 0, 0).region;
        assertEquals("REGION_C", atOrigin2.name());

        assertNotEquals(atOrigin1.name(), atOrigin2.name(),
                "Different anchored regions should produce different origins");
    }
}
