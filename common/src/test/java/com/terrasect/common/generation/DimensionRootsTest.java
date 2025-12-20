package com.terrasect.common.generation;

import com.terrasect.common.generation.definition.GenerationStrategyType;
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
    void fallsBackToDefaultWhenDimensionNotRegistered() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        
        // Query for an unregistered dimension should return fallback (Overworld)
        Region result = DimensionRoots.getRoot("mymod:custom_dimension");
        assertEquals("OVERWORLD", result.name());
    }

    @Test
    void throwsWhenNoFallbackAvailable() {
        assertThrows(IllegalStateException.class, () -> {
            DimensionRoots.getRoot("mymod:custom_dimension");
        });
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
    void setsOverworldAsFallbackAutomatically() {
        Region endRoot = buildSimpleRoot("END");
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        
        // Register End first
        DimensionRoots.register(DimensionRoots.THE_END, endRoot);
        // End becomes fallback since it's first
        assertEquals("END", DimensionRoots.getFallback().name());
        
        // Register Overworld - should become new fallback
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        assertEquals("OVERWORLD", DimensionRoots.getFallback().name());
    }

    @Test
    void getRootOrNullReturnsNullForUnregistered() {
        Region overworldRoot = buildSimpleRoot("OVERWORLD");
        DimensionRoots.register(DimensionRoots.OVERWORLD, overworldRoot);
        
        assertNotNull(DimensionRoots.getRootOrNull(DimensionRoots.OVERWORLD));
        assertNull(DimensionRoots.getRootOrNull(DimensionRoots.THE_END));
    }

    @Test
    void clearRemovesAllRegistrations() {
        DimensionRoots.register(DimensionRoots.OVERWORLD, buildSimpleRoot("OVERWORLD"));
        DimensionRoots.register(DimensionRoots.THE_END, buildSimpleRoot("END"));
        
        DimensionRoots.clear();
        
        assertTrue(DimensionRoots.getRegisteredDimensions().isEmpty());
        assertNull(DimensionRoots.getFallback());
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

    @Test
    void legacyWorldApiStillWorks() {
        Region root = buildSimpleRoot("LEGACY");
        
        // Legacy API
        World.setRoot(root);
        
        // Should work with legacy API
        assertEquals("LEGACY", World.getRoot().name());
        
        // Should also be available via new API (as fallback)
        assertEquals("LEGACY", DimensionRoots.getFallback().name());
    }

    private Region buildSimpleRoot(String name) {
        RegionRegistry registry = new RegionRegistry();
        registry.region(name)
            .strategy(GenerationStrategyType.HEX)
            .radius(1000);
        return registry.build(name);
    }
}
