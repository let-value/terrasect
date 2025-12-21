package com.terrasect.common.generation;

import com.terrasect.common.api.Context;
import com.terrasect.common.api.Region;
import com.terrasect.common.api.RegionRegistry;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.runtime.World;
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
        
        World.register(World.OVERWORLD, overworldRoot);
        
        assertEquals("OVERWORLD", World.getRoot(World.OVERWORLD).name());
        assertTrue(World.hasRoot(World.OVERWORLD));
    }

    @Test
    void supportsDifferentRootsPerDimension() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        Region endRoot = buildSimpleRoot("END");
        
        World.register(World.OVERWORLD, overworldRoot);
        World.register(World.THE_END, endRoot);
        
        assertEquals("OVERWORLD", World.getRoot(World.OVERWORLD).name());
        assertEquals("END", World.getRoot(World.THE_END).name());
        
        // Should have 2 dimensions registered
        assertEquals(2, World.getRegisteredDimensions().size());
    }

    @Test
    void returnsNullForUnregisteredDimension() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        World.register(World.OVERWORLD, overworldRoot);
        
        // Query for an unregistered dimension should return null (no influence)
        Region result = World.getRoot("mymod:custom_dimension");
        assertNull(result);
        
        // Overworld should still work
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
        
        // All three should reference the same object
        assertSame(
            World.getRoot(World.OVERWORLD),
            World.getRoot("mymod:overworld_copy")
        );
    }

    @Test
    void getRootReturnsNullForUnregistered() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        World.register(World.OVERWORLD, overworldRoot);
        
        assertNotNull(World.getRoot(World.OVERWORLD));
        assertNull(World.getRoot(World.THE_END));
    }

    @Test
    void clearRemovesAllRegistrations() {
        World.register(World.OVERWORLD, buildSimpleRoot("OVERWORLD"));
        World.register(World.THE_END, buildSimpleRoot("END"));
        
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
    
    // ========== Anchor to Origin Tests ==========
    
    private Context createTestContext(long seed) {
        return new com.terrasect.common.api.Context() {
            @Override
            public long getSeed() { return seed; }
            
            @Override
            public float getRiverInfluence(int x, int z) { return 0.0f; }
            
            @Override
            public float getRidgeInfluence(int x, int z) { return 0.0f; }
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
        
        // Build hierarchy with 4 regions, anchor the specified one
        Region root = buildTestRegionsWithAnchor(anchoredName);
        World.register(World.OVERWORLD, root);
        
        // Initialize - this triggers offset calculation
        World.initialize(World.OVERWORLD, seed, context);
        
        // Sample at origin - should return the anchored region
        Region atOrigin = World.getRegion(World.OVERWORLD, 0, 0, context);
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
                if ("REGION_A".equals(anchoredName)) r.anchoredToOrigin();
            })
            .child("REGION_B", r -> {
                r.radius(1000);
                if ("REGION_B".equals(anchoredName)) r.anchoredToOrigin();
            })
            .child("REGION_C", r -> {
                r.radius(1000);
                if ("REGION_C".equals(anchoredName)) r.anchoredToOrigin();
            })
            .child("REGION_D", r -> {
                r.radius(1000);
                if ("REGION_D".equals(anchoredName)) r.anchoredToOrigin();
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
        
        // Build a 4-level deep hierarchy with a small anchored region at the bottom
        Region root = buildDeeplyNestedHierarchy();
        World.register(World.OVERWORLD, root);
        
        // Initialize to trigger offset calculation
        World.initialize(World.OVERWORLD, seed, context);
        
        // Sample at origin at depth 4 - should return the anchored region
        Region atOriginDepth4 = World.getRegionAtDepth(World.OVERWORLD, 0, 0, context, 4);
        assertNotNull(atOriginDepth4, "Should find a region at origin depth 4");
        assertEquals("SPAWN_POINT", atOriginDepth4.name(),
            "Region at origin should be the deeply nested anchored region 'SPAWN_POINT'");
        
        // Also verify sampling with getRegion (full depth) returns the anchored region
        Region atOriginFull = World.getRegion(World.OVERWORLD, 0, 0, context);
        assertNotNull(atOriginFull, "Should find a region at origin (full depth)");
        assertEquals("SPAWN_POINT", atOriginFull.name(),
            "Full depth sampling at origin should also return 'SPAWN_POINT'");
    }
    
    /**
     * Builds a 4-level deep hierarchy:
     * ROOT (level 0)
     *   └── CONTINENT (level 1)
     *         └── KINGDOM (level 2)
     *               └── PROVINCE (level 3)
     *                     └── SPAWN_POINT (level 4, anchored, small radius)
     */
    private Region buildDeeplyNestedHierarchy() {
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
            .strategy(GenerationStrategyType.HEX)
            .radius(50000) // Very large world
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
                            .radius(200) // Small region
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
        
        // Build hierarchy WITHOUT any anchored region
        RegionRegistry registry = new RegionRegistry();
        registry.region("ROOT")
            .strategy(GenerationStrategyType.HEX)
            .radius(5000)
            .child("REGION_A", r -> r.radius(1000))
            .child("REGION_B", r -> r.radius(1000))
            .child("REGION_C", r -> r.radius(1000));
        Region root = registry.build("ROOT");
        
        World.register(World.OVERWORLD, root);
        World.initialize(World.OVERWORLD, seed, context);
        
        // Sample at origin - result depends on procedural generation, not anchoring
        Region atOrigin = World.getRegion(World.OVERWORLD, 0, 0, context);
        assertNotNull(atOrigin, "Should find some region at origin");
        // We don't assert which region, just that SOME region is found
        assertTrue(
            atOrigin.name().equals("REGION_A") || 
            atOrigin.name().equals("REGION_B") || 
            atOrigin.name().equals("REGION_C"),
            "Origin region should be one of the defined regions"
        );
    }
    
    /**
     * Verifies that the same seed produces the same offset for the same anchored region.
     */
    @Test
    void anchoredRegion_sameSeededProducesSameOffset() {
        long seed = 77777L;
        
        // First initialization
        Region root1 = buildTestRegionsWithAnchor("REGION_B");
        World.register(World.OVERWORLD, root1);
        World.initialize(World.OVERWORLD, seed, createTestContext(seed));
        
        Region atOrigin1 = World.getRegion(World.OVERWORLD, 0, 0, createTestContext(seed));
        assertNotNull(atOrigin1);
        assertEquals("REGION_B", atOrigin1.name());
        
        // Clear and re-register with same config
        World.clear();
        
        Region root2 = buildTestRegionsWithAnchor("REGION_B");
        World.register(World.OVERWORLD, root2);
        World.initialize(World.OVERWORLD, seed, createTestContext(seed));
        
        Region atOrigin2 = World.getRegion(World.OVERWORLD, 0, 0, createTestContext(seed));
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
        
        // Test that anchoring REGION_A and REGION_C produce different origins
        // (when they aren't already the procedural origin)
        
        // First: anchor REGION_A
        Region root1 = buildTestRegionsWithAnchor("REGION_A");
        World.register(World.OVERWORLD, root1);
        World.initialize(World.OVERWORLD, seed, createTestContext(seed));
        Region atOrigin1 = World.getRegion(World.OVERWORLD, 0, 0, createTestContext(seed));
        assertEquals("REGION_A", atOrigin1.name());
        
        World.clear();
        
        // Second: anchor REGION_C  
        Region root2 = buildTestRegionsWithAnchor("REGION_C");
        World.register(World.OVERWORLD, root2);
        World.initialize(World.OVERWORLD, seed, createTestContext(seed));
        Region atOrigin2 = World.getRegion(World.OVERWORLD, 0, 0, createTestContext(seed));
        assertEquals("REGION_C", atOrigin2.name());
        
        // They should be different (unless by coincidence both are at origin naturally,
        // which is statistically unlikely with 4 regions)
        assertNotEquals(atOrigin1.name(), atOrigin2.name(),
            "Different anchored regions should produce different origins");
    }
}
