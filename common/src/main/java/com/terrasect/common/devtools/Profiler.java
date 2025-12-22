package com.terrasect.common.devtools;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Zero-overhead profiler for identifying performance bottlenecks in hot paths.
 * 
 * <p>Design principles:
 * <ul>
 *   <li><b>Compile-time eliminable:</b> When {@link #ENABLED} is false, the JIT can eliminate all calls</li>
 *   <li><b>No allocations:</b> Uses pre-registered metrics with primitive counters only</li>
 *   <li><b>Minimal invasiveness:</b> Single-line instrumentation with static methods</li>
 *   <li><b>Aggregation:</b> Accumulates across frames/chunks for statistical analysis</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Manual timing (use thread-local start time)
 * long t0 = Profiler.begin();
 * // ... work ...
 * Profiler.end(Profiler.CLIMATE_MODIFY, t0);
 * 
 * // Count allocations or events
 * Profiler.count(Profiler.BIOME_FILTERED);
 * 
 * // Count with amount (e.g., bytes allocated)
 * Profiler.count(Profiler.REGION_LOOKUP, 3);
 * 
 * // Print summary periodically or at shutdown
 * Profiler.printSummary();
 * </pre>
 * 
 * <h2>Performance</h2>
 * <p>When ENABLED=false, calls become dead code after inlining since they immediately return.
 * When ENABLED=true, overhead is ~10-20ns per timing (System.nanoTime + atomic increment).
 */
public final class Profiler {
    
    // ==================== Runtime Control ====================
    
    /**
     * Master switch for profiling. Disabled by default.
     * Enable programmatically via {@link #enable()} before measuring.
     * 
     * <p>When false, all profiling methods return immediately.
     * The JIT may inline and eliminate these calls after warm-up.
     */
    private static volatile boolean enabled = false;
    
    /**
     * Enable detailed per-call tracking (more overhead). 
     * Only effective when profiling is enabled.
     */
    public static final boolean DETAILED = Boolean.getBoolean("terrasect.profiler.detailed");
    
    /**
     * Check if profiling is currently enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Enable profiling and reset all metrics.
     */
    public static void enable() {
        reset();
        enabled = true;
    }
    
    /**
     * Disable profiling.
     */
    public static void disable() {
        enabled = false;
    }
    
    // ==================== Metric Keys (interned strings for identity comparison) ====================
    
    // Climate modification path
    public static final String CLIMATE_MODIFY = "climate.modify";
    public static final String CLIMATE_REGION_LOOKUP = "climate.regionLookup";
    public static final String CLIMATE_EDGE_CALC = "climate.edgeCalc";
    public static final String CLIMATE_OFFSET_CALC = "climate.offsetCalc";
    
    // Biome selection path
    public static final String BIOME_SELECT = "biome.select";
    public static final String BIOME_REGION_LOOKUP = "biome.regionLookup";
    public static final String BIOME_FILTER = "biome.filter";
    public static final String BIOME_FIND_VALUE = "biome.findValue";
    
    // Region field calculations
    public static final String REGION_FIELD_DATA = "regionField.getData";
    public static final String REGION_FIELD_NOISE = "regionField.noise";
    public static final String REGION_FIELD_VORONOI = "regionField.voronoi";
    
    // Terrain modification
    public static final String TERRAIN_HEIGHT_CHECK = "terrain.heightCheck";
    public static final String TERRAIN_DENSITY_COMPUTE = "terrain.densityCompute";
    public static final String TERRAIN_FILL_ARRAY = "terrain.fillArray";
    
    // World/region lookup
    public static final String WORLD_GET_REGION = "world.getRegion";
    public static final String WORLD_TRAVERSE = "world.traverse";
    
    // Allocation tracking (count-only metrics)
    public static final String ALLOC_CLIMATE_RESULT = "alloc.climateResult";
    public static final String ALLOC_TARGET_POINT = "alloc.targetPoint";
    public static final String ALLOC_PARAMETER_LIST = "alloc.parameterList";
    
    // ==================== Metric Storage ====================
    
    private static final Map<String, Metric> METRICS = new ConcurrentHashMap<>();
    
    // Pre-register all metrics at class load
    static {
        registerAll(
            CLIMATE_MODIFY, CLIMATE_REGION_LOOKUP, CLIMATE_EDGE_CALC, CLIMATE_OFFSET_CALC,
            BIOME_SELECT, BIOME_REGION_LOOKUP, BIOME_FILTER, BIOME_FIND_VALUE,
            REGION_FIELD_DATA, REGION_FIELD_NOISE, REGION_FIELD_VORONOI,
            TERRAIN_HEIGHT_CHECK, TERRAIN_DENSITY_COMPUTE, TERRAIN_FILL_ARRAY,
            WORLD_GET_REGION, WORLD_TRAVERSE,
            ALLOC_CLIMATE_RESULT, ALLOC_TARGET_POINT, ALLOC_PARAMETER_LIST
        );
    }
    
    private static void registerAll(String... keys) {
        for (String key : keys) {
            METRICS.put(key, new Metric());
        }
    }
    
    private Profiler() {}
    
    // ==================== Timing API ====================
    
    /**
     * Begin a timed section. Returns the start time to pass to {@link #end(String, long)}.
     * When profiling is disabled, returns 0.
     */
    public static long begin() {
        if (!enabled) return 0L;
        return System.nanoTime();
    }
    
    /**
     * End a timed section and record the elapsed time.
     * 
     * @param key Metric key (use the static constants)
     * @param startTime Value returned from {@link #begin()}
     */
    public static void end(String key, long startTime) {
        if (!enabled) return;
        long elapsed = System.nanoTime() - startTime;
        Metric m = METRICS.get(key);
        if (m != null) {
            m.recordTime(elapsed);
        }
    }
    
    /**
     * Record a timing directly (when start time was captured elsewhere).
     */
    public static void recordNanos(String key, long nanos) {
        if (!enabled) return;
        Metric m = METRICS.get(key);
        if (m != null) {
            m.recordTime(nanos);
        }
    }
    
    // ==================== Counting API ====================
    
    /**
     * Increment a counter by 1 (for allocation tracking, event counting, etc.)
     */
    public static void count(String key) {
        if (!enabled) return;
        Metric m = METRICS.get(key);
        if (m != null) {
            m.incrementCount();
        }
    }
    
    /**
     * Increment a counter by a specific amount.
     */
    public static void count(String key, long amount) {
        if (!enabled) return;
        Metric m = METRICS.get(key);
        if (m != null) {
            m.addCount(amount);
        }
    }
    
    // ==================== Results API ====================
    
    /**
     * Reset all metrics. Call at the start of a profiling session.
     */
    public static void reset() {
        METRICS.values().forEach(Metric::reset);
    }
    
    /**
     * Get a snapshot of a specific metric.
     */
    public static MetricSnapshot getMetric(String key) {
        Metric m = METRICS.get(key);
        return m != null ? m.snapshot() : MetricSnapshot.EMPTY;
    }
    
    /**
     * Print a summary of all metrics to stdout, sorted by total time descending.
     */
    public static void printSummary() {
        if (!enabled) {
            System.out.println("[Profiler] Profiling is disabled. Call Profiler.enable() first.");
            return;
        }
        
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                           TERRASECT PROFILER SUMMARY                             ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ %-30s │ %10s │ %12s │ %10s │ %8s ║%n", 
            "Metric", "Calls", "Total (ms)", "Avg (µs)", "% Total");
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        
        // Calculate grand total for percentage
        double grandTotalNs = METRICS.values().stream()
            .mapToLong(m -> m.totalNanos.sum())
            .sum();
        double grandTotalMs = grandTotalNs / 1_000_000.0;
        
        // Sort by total time descending, filtering out zero-call metrics
        METRICS.entrySet().stream()
            .filter(e -> e.getValue().callCount.sum() > 0)
            .sorted(Comparator.comparingLong((Map.Entry<String, Metric> e) -> 
                e.getValue().totalNanos.sum()).reversed())
            .forEach(e -> {
                String name = e.getKey();
                Metric m = e.getValue();
                long calls = m.callCount.sum();
                double totalMs = m.totalNanos.sum() / 1_000_000.0;
                double avgUs = calls > 0 ? (m.totalNanos.sum() / 1000.0) / calls : 0;
                double pct = grandTotalNs > 0 ? (m.totalNanos.sum() / grandTotalNs) * 100 : 0;
                
                System.out.printf("║ %-30s │ %10d │ %12.2f │ %10.2f │ %7.1f%% ║%n",
                    truncate(name, 30), calls, totalMs, avgUs, pct);
            });
        
        System.out.println("╠══════════════════════════════════════════════════════════════════════════════════╣");
        System.out.printf("║ %-30s │            │ %12.2f │            │          ║%n", "GRAND TOTAL", grandTotalMs);
        System.out.println("╚══════════════════════════════════════════════════════════════════════════════════╝\n");
    }
    
    /**
     * Print summary in markdown format (for logging/reports).
     */
    public static String getSummaryMarkdown() {
        if (!enabled) {
            return "Profiling is disabled. Call Profiler.enable() first.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n### Terrasect Profiler Summary\n\n");
        sb.append("| Metric | Calls | Total (ms) | Avg (µs) | % Total |\n");
        sb.append("|--------|-------|------------|----------|--------|\n");
        
        double grandTotalNs = METRICS.values().stream()
            .mapToLong(m -> m.totalNanos.sum())
            .sum();
        
        METRICS.entrySet().stream()
            .filter(e -> e.getValue().callCount.sum() > 0)
            .sorted(Comparator.comparingLong((Map.Entry<String, Metric> e) -> 
                e.getValue().totalNanos.sum()).reversed())
            .forEach(e -> {
                String name = e.getKey();
                Metric m = e.getValue();
                long calls = m.callCount.sum();
                double totalMs = m.totalNanos.sum() / 1_000_000.0;
                double avgUs = calls > 0 ? (m.totalNanos.sum() / 1000.0) / calls : 0;
                double pct = grandTotalNs > 0 ? (m.totalNanos.sum() / grandTotalNs) * 100 : 0;
                
                sb.append(String.format("| %s | %d | %.2f | %.2f | %.1f%% |\n",
                    name, calls, totalMs, avgUs, pct));
            });
        
        return sb.toString();
    }
    
    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
    
    // ==================== Metric Storage Classes ====================
    
    /**
     * Thread-safe metric accumulator using LongAdder for minimal contention.
     */
    private static final class Metric {
        final LongAdder callCount = new LongAdder();
        final LongAdder totalNanos = new LongAdder();
        
        void recordTime(long nanos) {
            callCount.increment();
            totalNanos.add(nanos);
        }
        
        void incrementCount() {
            callCount.increment();
        }
        
        void addCount(long amount) {
            callCount.add(amount);
        }
        
        void reset() {
            callCount.reset();
            totalNanos.reset();
        }
        
        MetricSnapshot snapshot() {
            return new MetricSnapshot(callCount.sum(), totalNanos.sum());
        }
    }
    
    /**
     * Immutable snapshot of a metric's state.
     */
    public record MetricSnapshot(long calls, long totalNanos) {
        public static final MetricSnapshot EMPTY = new MetricSnapshot(0, 0);
        
        public double totalMs() {
            return totalNanos / 1_000_000.0;
        }
        
        public double avgUs() {
            return calls > 0 ? (totalNanos / 1000.0) / calls : 0;
        }
        
        public double avgNs() {
            return calls > 0 ? (double) totalNanos / calls : 0;
        }
    }
}
