package com.terrasect.common.generation;

import static org.junit.jupiter.api.Assertions.*;

import com.terrasect.common.Context;
import com.terrasect.common.definition.GenerationStrategy;
import com.terrasect.common.definition.Region;
import com.terrasect.common.definition.RegionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class WorldTest {

    @BeforeEach void setUp() {
        World.clear();
    }

    @AfterEach void tearDown() {
        World.clear();
    }

    @Test void registersRootForDimension() {
        var overworldRoot = buildSimpleRoot("OVERWORLD");

        World.register(overworldRoot, World.OVERWORLD);

        assertEquals("OVERWORLD", World.getRoot(World.OVERWORLD).name());
        assertTrue(World.hasRoot(World.OVERWORLD));
    }

    @Test void supportsDifferentRootsPerDimension() {
        var overworldRoot = buildSimpleRoot("OVERWORLD");
        var endRoot = buildSimpleRoot("END");

        World.register(overworldRoot, World.OVERWORLD);
        World.register(endRoot, World.THE_END);

        assertEquals("OVERWORLD", World.getRoot(World.OVERWORLD).name());
        assertEquals("END", World.getRoot(World.THE_END).name());

        assertEquals(2, World.getRegisteredDimensions().size());
    }

    @Test void returnsNullForUnregisteredDimension() {
        var overworldRoot = buildSimpleRoot("OVERWORLD");
        World.register(overworldRoot, World.OVERWORLD);

        Region result = World.getRoot("mymod:custom_dimension");
        assertNull(result);

        assertEquals("OVERWORLD", World.getRoot(World.OVERWORLD).name());
    }

    @Test void returnsNullWhenNoDimensionsRegistered() {
        assertNull(World.getRoot("mymod:custom_dimension"));
        assertNull(World.getRoot(World.OVERWORLD));
    }

    @Test void allowsSameRootForMultipleDimensions() {
        var sharedRoot = buildSimpleRoot("SHARED");

        World.register(sharedRoot, World.OVERWORLD, "mymod:overworld_copy", "mymod:alternate_world");

        assertEquals("SHARED", World.getRoot(World.OVERWORLD).name());
        assertEquals("SHARED", World.getRoot("mymod:overworld_copy").name());
        assertEquals("SHARED", World.getRoot("mymod:alternate_world").name());

        assertSame(World.getRoot(World.OVERWORLD), World.getRoot("mymod:overworld_copy"));
    }

    @Test void getRootReturnsNullForUnregistered() {
        var overworldRoot = buildSimpleRoot("OVERWORLD");
        World.register(overworldRoot, World.OVERWORLD);

        assertNotNull(World.getRoot(World.OVERWORLD));
        assertNull(World.getRoot(World.THE_END));
    }

    @Test void clearRemovesAllRegistrations() {
        World.register(buildSimpleRoot("OVERWORLD"), World.OVERWORLD);
        World.register(buildSimpleRoot("END"), World.THE_END);

        World.clear();

        assertTrue(World.getRegisteredDimensions().isEmpty());
    }

    @Test void emptyRootCreatesValidEmptyRegion() {
        Region empty = World.emptyRoot("EMPTY_DIM");

        assertEquals("EMPTY_DIM", empty.name());
        assertTrue(empty.children().isEmpty());
        assertNotNull(empty.definition());
    }

    private Region buildSimpleRoot(String name) {
        var registry = new RegionRegistry();
        registry.region(name).strategy(GenerationStrategy.hex()).radius(1000);
        return registry.build(name);
    }

    private Context createTestContext(long seed) {
        return new com.terrasect.common.Context() {
            @Override public long getSeed() {
                return seed;
            }

            @Override public long getInfluence(int x, int z) {
                return 0L;
            }
        };
    }

    @Test void anchoredRegion_RegionA_appearsAtOrigin() {
        testAnchoredRegionAppearsAtOrigin("REGION_A");
    }

    @Test void anchoredRegion_RegionB_appearsAtOrigin() {
        testAnchoredRegionAppearsAtOrigin("REGION_B");
    }

    @Test void anchoredRegion_RegionC_appearsAtOrigin() {
        testAnchoredRegionAppearsAtOrigin("REGION_C");
    }

    @Test void anchoredRegion_RegionD_appearsAtOrigin() {
        testAnchoredRegionAppearsAtOrigin("REGION_D");
    }

    private void testAnchoredRegionAppearsAtOrigin(String anchoredName) {
        var seed = 12345L;
        var context = createTestContext(seed);

        var root = buildTestRegionsWithAnchor(anchoredName);
        World.register(root, World.OVERWORLD);

        World.initialize(context);

        var atOrigin = World.traverse(context, 0, 0).region;
        assertNotNull(atOrigin, "Should find a region at origin after anchoring");
        assertEquals(
                anchoredName, atOrigin.name(), "Region at origin should be the anchored region '" + anchoredName + "'");
    }

    private Region buildTestRegionsWithAnchor(String anchoredName) {
        var registry = new RegionRegistry();
        registry.region("ROOT")
                .strategy(GenerationStrategy.hex())
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

    @Test void deeplyNestedAnchoredRegion_SamplingOriginReturnsAnchoredRegion() {
        var seed = 54321L;
        var context = createTestContext(seed);

        var root = buildDeeplyNestedHierarchy();
        World.register(root, World.OVERWORLD);

        World.initialize(context);

        var atOriginDepth4 = World.traverse(context, 0, 0, 4).region;
        assertNotNull(atOriginDepth4, "Should find a region at origin depth 4");
        assertEquals(
                "SPAWN_POINT",
                atOriginDepth4.name(),
                "Region at origin should be the deeply nested anchored region 'SPAWN_POINT'");

        var atOriginFull = World.traverse(context, 0, 0).region;
        assertNotNull(atOriginFull, "Should find a region at origin (full depth)");
        assertEquals(
                "SPAWN_POINT", atOriginFull.name(), "Full depth sampling at origin should also return 'SPAWN_POINT'");
    }

    private Region buildDeeplyNestedHierarchy() {
        var registry = new RegionRegistry();
        registry.region("ROOT")
                .strategy(GenerationStrategy.hex())
                .radius(50000)
                .child("CONTINENT", continent -> continent
                        .strategy(GenerationStrategy.subdivision())
                        .radius(10000)
                        .child("KINGDOM", kingdom -> kingdom.strategy(GenerationStrategy.voronoi())
                                .radius(3000)
                                .child("PROVINCE", province -> province.strategy(GenerationStrategy.subdivision())
                                        .radius(1000)
                                        .child("SPAWN_POINT", spawn -> spawn.radius(200)
                                                .anchoredToOrigin())
                                        .child("WILDERNESS", wild -> wild.radius(800)))));
        return registry.build("ROOT");
    }

    @Test void withoutAnchoring_originRegionIsProcedural() {
        var seed = 99999L;
        var context = createTestContext(seed);

        var registry = new RegionRegistry();
        registry.region("ROOT")
                .strategy(GenerationStrategy.hex())
                .radius(5000)
                .child("REGION_A", r -> r.radius(1000))
                .child("REGION_B", r -> r.radius(1000))
                .child("REGION_C", r -> r.radius(1000));
        var root = registry.build("ROOT");

        World.register(root, World.OVERWORLD);
        World.initialize(context);

        var atOrigin = World.traverse(context, 0, 0).region;
        assertNotNull(atOrigin, "Should find some region at origin");

        assertTrue(
                atOrigin.name().equals("REGION_A")
                        || atOrigin.name().equals("REGION_B")
                        || atOrigin.name().equals("REGION_C"),
                "Origin region should be one of the defined regions");
    }

    @Test void anchoredRegion_sameSeededProducesSameOffset() {
        var seed = 77777L;
        var context = createTestContext(seed);

        var root1 = buildTestRegionsWithAnchor("REGION_B");
        World.register(root1, World.OVERWORLD);
        World.initialize(context);

        var atOrigin1 = World.traverse(context, 0, 0).region;
        assertNotNull(atOrigin1);
        assertEquals("REGION_B", atOrigin1.name());

        World.clear();

        var root2 = buildTestRegionsWithAnchor("REGION_B");
        World.register(root2, World.OVERWORLD);
        World.initialize(context);

        var atOrigin2 = World.traverse(context, 0, 0).region;
        assertNotNull(atOrigin2);
        assertEquals(
                "REGION_B",
                atOrigin2.name(),
                "Re-initialized world with same seed should produce same result at origin");
    }

    @Test void differentAnchoredRegions_produceDifferentResults() {
        var seed = 88888L;
        var context = createTestContext(seed);

        var root1 = buildTestRegionsWithAnchor("REGION_A");
        World.register(root1, World.OVERWORLD);
        World.initialize(context);
        var atOrigin1 = World.traverse(context, 0, 0).region;
        assertEquals("REGION_A", atOrigin1.name());

        World.clear();

        var root2 = buildTestRegionsWithAnchor("REGION_C");
        World.register(root2, World.OVERWORLD);
        World.initialize(context);
        var atOrigin2 = World.traverse(context, 0, 0).region;
        assertEquals("REGION_C", atOrigin2.name());

        assertNotEquals(
                atOrigin1.name(), atOrigin2.name(), "Different anchored regions should produce different origins");
    }
}
