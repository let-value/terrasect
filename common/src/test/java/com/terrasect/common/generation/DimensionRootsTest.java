package com.terrasect.common.generation;

import com.terrasect.common.api.DimensionRoots;
import com.terrasect.common.api.Region;
import com.terrasect.common.api.RegionRegistry;
import com.terrasect.common.generation.definition.GenerationStrategyType;
import com.terrasect.common.runtime.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for dimension-aware region root registration.
 */
public class DimensionRootsTest {

    @BeforeEach
    void setUp() {
        DimensionRoots.clear();
    }

    @AfterEach
    void tearDown() {
        DimensionRoots.clear();
    }

    @Test
    void registersRootForDimension() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        
        assertEquals("OVERWORLD", DimensionRoots.getRoot(DimensionRoots.OVERWORLD).name());
        assertTrue(DimensionRoots.hasRoot(DimensionRoots.OVERWORLD));
    }

    @Test
    void supportsDifferentRootsPerDimension() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        Region endRoot = buildSimpleRoot("END");
        
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        DimensionRoots.register(DimensionRoots.THE_END, endRoot);
        
        assertEquals("OVERWORLD", DimensionRoots.getRoot(DimensionRoots.OVERWORLD).name());
        assertEquals("END", DimensionRoots.getRoot(DimensionRoots.THE_END).name());
        
        // Should have 2 dimensions registered
        assertEquals(2, DimensionRoots.getRegisteredDimensions().size());
    }

    @Test
    void returnsNullForUnregisteredDimension() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        
        // Query for an unregistered dimension should return null (no influence)
        Region result = DimensionRoots.getRoot("mymod:custom_dimension");
        assertNull(result);
        
        // Overworld should still work
        assertEquals("OVERWORLD", DimensionRoots.getRoot(DimensionRoots.OVERWORLD).name());
    }

    @Test
    void returnsNullWhenNoDimensionsRegistered() {
        assertNull(DimensionRoots.getRoot("mymod:custom_dimension"));
        assertNull(DimensionRoots.getRoot(DimensionRoots.OVERWORLD));
    }

    @Test
    void allowsSameRootForMultipleDimensions() {
        Region sharedRoot = buildSimpleRoot("SHARED");
        
        DimensionRoots.registerForDimensions(sharedRoot, 
            DimensionRoots.OVERWORLD, 
            "mymod:overworld_copy",
            "mymod:alternate_world");
        
        assertEquals("SHARED", DimensionRoots.getRoot(DimensionRoots.OVERWORLD).name());
        assertEquals("SHARED", DimensionRoots.getRoot("mymod:overworld_copy").name());
        assertEquals("SHARED", DimensionRoots.getRoot("mymod:alternate_world").name());
        
        // All three should reference the same object
        assertSame(
            DimensionRoots.getRoot(DimensionRoots.OVERWORLD),
            DimensionRoots.getRoot("mymod:overworld_copy")
        );
    }

    @Test
    void getRootReturnsNullForUnregistered() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        
        assertNotNull(DimensionRoots.getRoot(DimensionRoots.OVERWORLD));
        assertNull(DimensionRoots.getRoot(DimensionRoots.THE_END));
    }

    @Test
    void clearRemovesAllRegistrations() {
        DimensionRoots.register(DimensionRoots.OVERWORLD, buildSimpleRoot("OVERWORLD"));
        DimensionRoots.register(DimensionRoots.THE_END, buildSimpleRoot("END"));
        
        DimensionRoots.clear();
        
        assertTrue(DimensionRoots.getRegisteredDimensions().isEmpty());
    }

    @Test
    void emptyRootCreatesValidEmptyRegion() {
        Region empty = DimensionRoots.emptyRoot("EMPTY_DIM");
        
        assertEquals("EMPTY_DIM", empty.name());
        assertTrue(empty.children().isEmpty());
        assertNotNull(empty.definition());
    }

    @Test
    void worldFacadeUsesNewDimensionApi() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        Region endRoot = buildSimpleRoot("END");
        
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        DimensionRoots.register(DimensionRoots.THE_END, endRoot);
        
        // New dimension-aware API
        assertEquals("OVERWORLD", World.getRoot(DimensionRoots.OVERWORLD).name());
        assertEquals("END", World.getRoot(DimensionRoots.THE_END).name());
    }

    private Region buildSimpleRoot(String name) {
        RegionRegistry registry = new RegionRegistry();
        registry.region(name)
            .strategy(GenerationStrategyType.HEX)
            .radius(1000);
        return registry.build(name);
    }
}
