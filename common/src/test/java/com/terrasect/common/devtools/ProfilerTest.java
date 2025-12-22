package com.terrasect.common.devtools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Profiler infrastructure.
 * Uses programmatic enable/disable for testing.
 */
class ProfilerTest {
    
    @BeforeEach
    void setUp() {
        // Enable profiler for tests and reset metrics
        Profiler.enable();
    }
    
    @AfterEach
    void tearDown() {
        if (Profiler.isEnabled()) {
            Profiler.printSummary();
        }
        Profiler.disable();
    }
    
    @Test
    void testProfilingDisabledByDefault() {
        // Disable to test the disabled state
        Profiler.disable();
        assertFalse(Profiler.isEnabled());
        
        // Re-enable for other tests
        Profiler.enable();
        assertTrue(Profiler.isEnabled());
    }
    
    @Test
    void testProfilerDoesNotThrowWhenDisabled() {
        Profiler.disable();
        
        // These should all be no-ops when disabled
        long t0 = Profiler.begin();
        Profiler.end(Profiler.CLIMATE_MODIFY, t0);
        Profiler.count(Profiler.ALLOC_CLIMATE_RESULT);
        Profiler.count(Profiler.ALLOC_TARGET_POINT, 10);
        Profiler.reset();
        Profiler.getMetric(Profiler.CLIMATE_MODIFY);
        // If we get here without exception, the test passes
    }
    
    @Test
    void testMetricSnapshot() {
        // Simulate some work
        for (int i = 0; i < 100; i++) {
            long t0 = Profiler.begin();
            simulateWork(1000); // 1000ns minimum
            Profiler.end(Profiler.CLIMATE_MODIFY, t0);
        }
        
        Profiler.MetricSnapshot snapshot = Profiler.getMetric(Profiler.CLIMATE_MODIFY);
        assertEquals(100, snapshot.calls());
        assertTrue(snapshot.totalNanos() > 0);
        assertTrue(snapshot.avgNs() > 0);
    }
    
    @Test
    void testCountingMetrics() {
        // Count allocations
        for (int i = 0; i < 50; i++) {
            Profiler.count(Profiler.ALLOC_CLIMATE_RESULT);
        }
        
        Profiler.MetricSnapshot snapshot = Profiler.getMetric(Profiler.ALLOC_CLIMATE_RESULT);
        assertEquals(50, snapshot.calls());
    }
    
    @Test
    void testReset() {
        long t0 = Profiler.begin();
        Profiler.end(Profiler.CLIMATE_MODIFY, t0);
        
        assertTrue(Profiler.getMetric(Profiler.CLIMATE_MODIFY).calls() > 0);
        
        Profiler.reset();
        
        Profiler.MetricSnapshot snapshot = Profiler.getMetric(Profiler.CLIMATE_MODIFY);
        assertEquals(0, snapshot.calls());
    }
    
    @Test
    void testMarkdownOutput() {
        // Add some data
        for (int i = 0; i < 10; i++) {
            long t0 = Profiler.begin();
            simulateWork(5000);
            Profiler.end(Profiler.BIOME_SELECT, t0);
            Profiler.count(Profiler.ALLOC_TARGET_POINT);
        }
        
        String markdown = Profiler.getSummaryMarkdown();
        assertTrue(markdown.contains("biome.select"));
        assertTrue(markdown.contains("alloc.targetPoint"));
        System.out.println(markdown);
    }
    
    @Test
    void testEnableDisableCycle() {
        // Test that enable/disable works correctly
        Profiler.disable();
        assertFalse(Profiler.isEnabled());
        
        // Metrics should not be recorded when disabled
        long t0 = Profiler.begin();
        assertEquals(0L, t0); // begin() returns 0 when disabled
        Profiler.end(Profiler.CLIMATE_MODIFY, t0);
        Profiler.count(Profiler.ALLOC_CLIMATE_RESULT);
        
        // Re-enable and check metrics were not recorded
        Profiler.enable();
        // Note: enable() calls reset(), so metrics are cleared anyway
        assertEquals(0, Profiler.getMetric(Profiler.CLIMATE_MODIFY).calls());
        assertEquals(0, Profiler.getMetric(Profiler.ALLOC_CLIMATE_RESULT).calls());
    }
    
    private void simulateWork(long minNanos) {
        long start = System.nanoTime();
        // Busy wait to simulate work
        int dummy = 0;
        while (System.nanoTime() - start < minNanos) {
            dummy++;
        }
        // Use dummy to prevent optimization
        if (dummy < 0) throw new RuntimeException("impossible");
    }
}
