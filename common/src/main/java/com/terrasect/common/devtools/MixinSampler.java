package com.terrasect.common.devtools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Sampling infrastructure for collecting data from mixin calls during gameplay.
 * 
 * This facade allows integration tests to observe actual behavior of our mixins
 * by collecting samples of region queries, climate modifications, and biome filtering.
 * 
 * Usage:
 * <pre>
 * MixinSampler.enable();
 * // ... run game code ...
 * MixinSampler.disable();
 * 
 * List<ClimateSample> samples = MixinSampler.getClimateSamples();
 * Map<String, Integer> regionCounts = MixinSampler.getRegionCounts();
 * </pre>
 * 
 * The sampler is disabled by default and has minimal overhead when disabled.
 */
public final class MixinSampler {
    
    private static final AtomicBoolean ENABLED = new AtomicBoolean(false);
    private static final AtomicInteger SAMPLE_RATE = new AtomicInteger(100); // Sample every Nth call
    
    // Climate modification samples
    private static final List<ClimateSample> CLIMATE_SAMPLES = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicLong CLIMATE_CALL_COUNT = new AtomicLong(0);
    private static final AtomicLong CLIMATE_MODIFIED_COUNT = new AtomicLong(0);
    
    // Region query samples
    private static final List<RegionSample> REGION_SAMPLES = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, AtomicInteger> REGION_COUNTS = new ConcurrentHashMap<>();
    private static final AtomicLong REGION_CALL_COUNT = new AtomicLong(0);
    
    // Biome filter samples
    private static final List<BiomeFilterSample> BIOME_SAMPLES = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, AtomicInteger> BIOME_COUNTS = new ConcurrentHashMap<>();
    private static final AtomicLong BIOME_CALL_COUNT = new AtomicLong(0);
    
    // Coordinate tracking for discontinuity analysis
    private static final List<CoordinateSample> COORD_SAMPLES = Collections.synchronizedList(new ArrayList<>());
    
    // Seed tracking for debugging
    private static final Map<Long, AtomicInteger> SEED_COUNTS = new ConcurrentHashMap<>();
    
    // Raw mixin input/output tracking
    private static final List<MixinIOSample> MIXIN_IO_SAMPLES = Collections.synchronizedList(new ArrayList<>());
    private static final AtomicLong MIXIN_IO_COUNT = new AtomicLong(0);
    
    private static final int MAX_SAMPLES = 10000;
    
    private MixinSampler() {}
    
    // ==================== Control Methods ====================
    
    /**
     * Enable sampling. Call before running code you want to analyze.
     */
    public static void enable() {
        ENABLED.set(true);
    }
    
    /**
     * Disable sampling. Call after collecting the data you need.
     */
    public static void disable() {
        ENABLED.set(false);
    }
    
    /**
     * Check if sampling is enabled.
     */
    public static boolean isEnabled() {
        return ENABLED.get();
    }
    
    /**
     * Set how often to record detailed samples (every Nth call).
     * Lower values = more samples but more memory/overhead.
     */
    public static void setSampleRate(int rate) {
        SAMPLE_RATE.set(Math.max(1, rate));
    }
    
    /**
     * Clear all collected samples and counters.
     */
    public static void clear() {
        CLIMATE_SAMPLES.clear();
        CLIMATE_CALL_COUNT.set(0);
        CLIMATE_MODIFIED_COUNT.set(0);
        
        REGION_SAMPLES.clear();
        REGION_COUNTS.clear();
        REGION_CALL_COUNT.set(0);
        
        BIOME_SAMPLES.clear();
        BIOME_COUNTS.clear();
        BIOME_CALL_COUNT.set(0);
        
        COORD_SAMPLES.clear();
        SEED_COUNTS.clear();
        MIXIN_IO_SAMPLES.clear();
        MIXIN_IO_COUNT.set(0);
    }
    
    // ==================== Recording Methods (called by mixins) ====================
    
    /**
     * Record a climate modification call.
     * Called by ClimateMixin/ClimateHandler.
     */
    public static void recordClimateCall(
            int quartX, int quartY, int quartZ,
            long originalTemp, long originalHumid,
            long modifiedTemp, long modifiedHumid,
            String regionName, boolean wasModified) {
        
        if (!ENABLED.get()) return;
        
        long callNum = CLIMATE_CALL_COUNT.incrementAndGet();
        if (wasModified) {
            CLIMATE_MODIFIED_COUNT.incrementAndGet();
        }
        
        // Record detailed sample periodically
        if (callNum % SAMPLE_RATE.get() == 0 && CLIMATE_SAMPLES.size() < MAX_SAMPLES) {
            CLIMATE_SAMPLES.add(new ClimateSample(
                quartX, quartY, quartZ,
                quartX << 2, quartZ << 2, // block coords
                originalTemp, originalHumid,
                modifiedTemp, modifiedHumid,
                regionName, wasModified
            ));
        }
    }
    
    /**
     * Record a region query.
     * Called by ClimateHandler/BiomeHandler when looking up regions.
     */
    public static void recordRegionQuery(int blockX, int blockZ, String regionName) {
        if (!ENABLED.get()) return;
        
        long callNum = REGION_CALL_COUNT.incrementAndGet();
        
        // Always count region hits
        if (regionName != null) {
            REGION_COUNTS.computeIfAbsent(regionName, k -> new AtomicInteger(0)).incrementAndGet();
        }
        
        // Record detailed sample periodically
        if (callNum % SAMPLE_RATE.get() == 0 && REGION_SAMPLES.size() < MAX_SAMPLES) {
            REGION_SAMPLES.add(new RegionSample(blockX, blockZ, regionName));
        }
    }
    
    /**
     * Record a biome filter call.
     * Called by BiomeMixin/BiomeHandler.
     */
    public static void recordBiomeFilter(
            int quartX, int quartZ,
            String selectedBiome, String regionName,
            boolean wasFiltered) {
        
        if (!ENABLED.get()) return;
        
        long callNum = BIOME_CALL_COUNT.incrementAndGet();
        
        // Always count biome selections
        if (selectedBiome != null) {
            BIOME_COUNTS.computeIfAbsent(selectedBiome, k -> new AtomicInteger(0)).incrementAndGet();
        }
        
        // Record detailed sample periodically
        if (callNum % SAMPLE_RATE.get() == 0 && BIOME_SAMPLES.size() < MAX_SAMPLES) {
            BIOME_SAMPLES.add(new BiomeFilterSample(
                quartX, quartZ,
                quartX << 2, quartZ << 2,
                selectedBiome, regionName, wasFiltered
            ));
        }
    }
    
    /**
     * Record coordinate for discontinuity analysis.
     * Records the region at a specific coordinate to detect fragmentation.
     */
    public static void recordCoordinate(int blockX, int blockZ, String regionName, 
                                         long temperature, long humidity) {
        if (!ENABLED.get()) return;
        
        if (COORD_SAMPLES.size() < MAX_SAMPLES) {
            COORD_SAMPLES.add(new CoordinateSample(blockX, blockZ, regionName, temperature, humidity));
        }
    }
    
    /**
     * Record seed used for region lookup.
     * Helps verify consistent seed usage across calls.
     */
    public static void recordSeed(long seed) {
        if (!ENABLED.get()) return;
        
        SEED_COUNTS.computeIfAbsent(seed, k -> new AtomicInteger(0)).incrementAndGet();
    }
    
    /**
     * Record raw mixin input/output for debugging integration issues.
     * Called directly from the mixin before any processing.
     */
    public static void recordMixinIO(
            int inputQuartX, int inputQuartY, int inputQuartZ,
            long inputTemp, long inputHumid, long inputCont, long inputErosion, long inputDepth, long inputWeird,
            long outputTemp, long outputHumid,
            String regionName, boolean wasModified) {
        
        if (!ENABLED.get()) return;
        
        long callNum = MIXIN_IO_COUNT.incrementAndGet();
        
        // Always record near-spawn samples (within 50 quarts = 200 blocks)
        int blockX = inputQuartX << 2;
        int blockZ = inputQuartZ << 2;
        boolean nearSpawn = (blockX * blockX + blockZ * blockZ) < (200 * 200);
        
        // Record detailed sample: always for near-spawn, periodically otherwise
        if ((nearSpawn || callNum % SAMPLE_RATE.get() == 0) && MIXIN_IO_SAMPLES.size() < MAX_SAMPLES) {
            MIXIN_IO_SAMPLES.add(new MixinIOSample(
                inputQuartX, inputQuartY, inputQuartZ,
                blockX, blockZ,
                inputTemp, inputHumid, inputCont, inputErosion, inputDepth, inputWeird,
                outputTemp, outputHumid,
                regionName, wasModified
            ));
        }
    }
    
    public static List<MixinIOSample> getMixinIOSamples() {
        return new ArrayList<>(MIXIN_IO_SAMPLES);
    }
    
    // ==================== Query Methods (called by tests) ====================
    
    public static List<ClimateSample> getClimateSamples() {
        return new ArrayList<>(CLIMATE_SAMPLES);
    }
    
    public static long getClimateCallCount() {
        return CLIMATE_CALL_COUNT.get();
    }
    
    public static long getClimateModifiedCount() {
        return CLIMATE_MODIFIED_COUNT.get();
    }
    
    public static List<RegionSample> getRegionSamples() {
        return new ArrayList<>(REGION_SAMPLES);
    }
    
    public static Map<String, Integer> getRegionCounts() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        REGION_COUNTS.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
    
    public static long getRegionCallCount() {
        return REGION_CALL_COUNT.get();
    }
    
    public static List<BiomeFilterSample> getBiomeSamples() {
        return new ArrayList<>(BIOME_SAMPLES);
    }
    
    public static Map<String, Integer> getBiomeCounts() {
        Map<String, Integer> result = new ConcurrentHashMap<>();
        BIOME_COUNTS.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
    
    public static long getBiomeCallCount() {
        return BIOME_CALL_COUNT.get();
    }
    
    public static List<CoordinateSample> getCoordinateSamples() {
        return new ArrayList<>(COORD_SAMPLES);
    }
    
    // ==================== Analysis Methods ====================
    
    /**
     * Analyze region fragmentation by checking how often neighboring coordinates
     * have different regions.
     * 
     * @return Fragmentation score (0.0 = no fragmentation, 1.0 = every neighbor differs)
     */
    public static FragmentationAnalysis analyzeFragmentation() {
        List<CoordinateSample> samples = new ArrayList<>(COORD_SAMPLES);
        if (samples.size() < 2) {
            return new FragmentationAnalysis(0, 0, 0.0);
        }
        
        // Group by coordinates for quick lookup
        Map<Long, CoordinateSample> byCoord = new ConcurrentHashMap<>();
        for (CoordinateSample s : samples) {
            long key = ((long) s.blockX << 32) | (s.blockZ & 0xFFFFFFFFL);
            byCoord.put(key, s);
        }
        
        int checks = 0;
        int discontinuities = 0;
        
        for (CoordinateSample s : samples) {
            // Check neighbors at +1 in each direction
            long keyRight = ((long) (s.blockX + 4) << 32) | (s.blockZ & 0xFFFFFFFFL);
            long keyDown = ((long) s.blockX << 32) | ((s.blockZ + 4) & 0xFFFFFFFFL);
            
            CoordinateSample right = byCoord.get(keyRight);
            CoordinateSample down = byCoord.get(keyDown);
            
            if (right != null) {
                checks++;
                if (!s.regionName.equals(right.regionName)) {
                    discontinuities++;
                }
            }
            if (down != null) {
                checks++;
                if (!s.regionName.equals(down.regionName)) {
                    discontinuities++;
                }
            }
        }
        
        double score = checks > 0 ? (double) discontinuities / checks : 0.0;
        return new FragmentationAnalysis(checks, discontinuities, score);
    }
    
    /**
     * Get a summary of all collected data.
     */
    public static String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== MixinSampler Summary ===\n");
        
        sb.append("\n--- Climate ---\n");
        sb.append(String.format("Total calls: %d\n", CLIMATE_CALL_COUNT.get()));
        sb.append(String.format("Modified: %d (%.1f%%)\n", 
            CLIMATE_MODIFIED_COUNT.get(),
            CLIMATE_CALL_COUNT.get() > 0 ? 100.0 * CLIMATE_MODIFIED_COUNT.get() / CLIMATE_CALL_COUNT.get() : 0));
        sb.append(String.format("Samples collected: %d\n", CLIMATE_SAMPLES.size()));
        
        sb.append("\n--- Regions ---\n");
        sb.append(String.format("Total queries: %d\n", REGION_CALL_COUNT.get()));
        sb.append("Distribution:\n");
        REGION_COUNTS.entrySet().stream()
            .sorted((a, b) -> b.getValue().get() - a.getValue().get())
            .forEach(e -> sb.append(String.format("  %s: %d\n", e.getKey(), e.getValue().get())));
        
        sb.append("\n--- Biomes ---\n");
        sb.append(String.format("Total calls: %d\n", BIOME_CALL_COUNT.get()));
        sb.append("Distribution:\n");
        BIOME_COUNTS.entrySet().stream()
            .sorted((a, b) -> b.getValue().get() - a.getValue().get())
            .limit(20)
            .forEach(e -> sb.append(String.format("  %s: %d\n", e.getKey(), e.getValue().get())));
        
        if (!COORD_SAMPLES.isEmpty()) {
            sb.append("\n--- Fragmentation ---\n");
            FragmentationAnalysis fa = analyzeFragmentation();
            sb.append(String.format("Checks: %d, Discontinuities: %d, Score: %.3f\n",
                fa.checks, fa.discontinuities, fa.score));
        }
        
        if (!SEED_COUNTS.isEmpty()) {
            sb.append("\n--- Seeds Used ---\n");
            sb.append(String.format("Unique seeds: %d\n", SEED_COUNTS.size()));
            SEED_COUNTS.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(5)
                .forEach(e -> sb.append(String.format("  %d: %d times\n", e.getKey(), e.getValue().get())));
        }
        
        // Mixin I/O analysis
        if (!MIXIN_IO_SAMPLES.isEmpty()) {
            sb.append("\n--- Mixin I/O Samples ---\n");
            sb.append(String.format("Total I/O calls: %d, Samples: %d\n", MIXIN_IO_COUNT.get(), MIXIN_IO_SAMPLES.size()));
            
            // Show coordinate ranges
            int minQX = Integer.MAX_VALUE, maxQX = Integer.MIN_VALUE;
            int minQZ = Integer.MAX_VALUE, maxQZ = Integer.MIN_VALUE;
            int minBX = Integer.MAX_VALUE, maxBX = Integer.MIN_VALUE;
            int minBZ = Integer.MAX_VALUE, maxBZ = Integer.MIN_VALUE;
            
            for (MixinIOSample s : MIXIN_IO_SAMPLES) {
                minQX = Math.min(minQX, s.inputQuartX);
                maxQX = Math.max(maxQX, s.inputQuartX);
                minQZ = Math.min(minQZ, s.inputQuartZ);
                maxQZ = Math.max(maxQZ, s.inputQuartZ);
                minBX = Math.min(minBX, s.computedBlockX);
                maxBX = Math.max(maxBX, s.computedBlockX);
                minBZ = Math.min(minBZ, s.computedBlockZ);
                maxBZ = Math.max(maxBZ, s.computedBlockZ);
            }
            
            sb.append(String.format("Quart coord range: X[%d to %d], Z[%d to %d]\n", minQX, maxQX, minQZ, maxQZ));
            sb.append(String.format("Block coord range: X[%d to %d], Z[%d to %d]\n", minBX, maxBX, minBZ, maxBZ));
            
            // Show first few samples as examples
            sb.append("\nFirst 10 samples (sorted by distance from origin):\n");
            List<MixinIOSample> sortedByDist = new ArrayList<>(MIXIN_IO_SAMPLES);
            sortedByDist.sort((a, b) -> {
                int distA = a.computedBlockX * a.computedBlockX + a.computedBlockZ * a.computedBlockZ;
                int distB = b.computedBlockX * b.computedBlockX + b.computedBlockZ * b.computedBlockZ;
                return Integer.compare(distA, distB);
            });
            int count = 0;
            for (MixinIOSample s : sortedByDist) {
                if (count++ >= 10) break;
                int dist = (int) Math.sqrt(s.computedBlockX * s.computedBlockX + s.computedBlockZ * s.computedBlockZ);
                sb.append(String.format("  dist=%d Q(%d,%d,%d) -> B(%d,%d) | in:T=%d,H=%d | out:T=%d,H=%d | region=%s | mod=%b\n",
                    dist,
                    s.inputQuartX, s.inputQuartY, s.inputQuartZ,
                    s.computedBlockX, s.computedBlockZ,
                    s.inputTemp, s.inputHumid,
                    s.outputTemp, s.outputHumid,
                    s.regionName, s.wasModified));
            }
            
            // Analyze samples NEAR SPAWN (within 200 blocks)
            sb.append("\nSamples near spawn (within 200 blocks):\n");
            int nearSpawnCount = 0;
            Map<String, Integer> nearSpawnRegions = new java.util.HashMap<>();
            for (MixinIOSample s : MIXIN_IO_SAMPLES) {
                int dist = (int) Math.sqrt(s.computedBlockX * s.computedBlockX + s.computedBlockZ * s.computedBlockZ);
                if (dist <= 200) {
                    nearSpawnCount++;
                    String region = s.regionName != null ? s.regionName : "NULL";
                    nearSpawnRegions.merge(region, 1, Integer::sum);
                    if (nearSpawnCount <= 15) {
                        sb.append(String.format("  dist=%d B(%d,%d) region=%s mod=%b\n",
                            dist, s.computedBlockX, s.computedBlockZ, s.regionName, s.wasModified));
                    }
                }
            }
            sb.append(String.format("Total near-spawn samples: %d\n", nearSpawnCount));
            sb.append("Near-spawn region distribution:\n");
            nearSpawnRegions.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> sb.append(String.format("  %s: %d\n", e.getKey(), e.getValue())));
            
            // Analyze adjacent quart discontinuities
            sb.append("\nAdjacent quart region changes (first 20):\n");
            int changes = 0;
            List<MixinIOSample> samples = new ArrayList<>(MIXIN_IO_SAMPLES);
            for (int i = 0; i < samples.size() && changes < 20; i++) {
                MixinIOSample s1 = samples.get(i);
                for (int j = i + 1; j < samples.size() && changes < 20; j++) {
                    MixinIOSample s2 = samples.get(j);
                    // Check if adjacent (within 1 quart = 4 blocks)
                    int dqx = Math.abs(s1.inputQuartX - s2.inputQuartX);
                    int dqz = Math.abs(s1.inputQuartZ - s2.inputQuartZ);
                    if (dqx <= 1 && dqz <= 1 && (dqx + dqz > 0)) {
                        if (s1.regionName != null && s2.regionName != null && !s1.regionName.equals(s2.regionName)) {
                            sb.append(String.format("  Q(%d,%d)->%s vs Q(%d,%d)->%s (delta: %d,%d quarts)\n",
                                s1.inputQuartX, s1.inputQuartZ, s1.regionName,
                                s2.inputQuartX, s2.inputQuartZ, s2.regionName,
                                s2.inputQuartX - s1.inputQuartX, s2.inputQuartZ - s1.inputQuartZ));
                            changes++;
                        }
                    }
                }
            }
            if (changes == 0) {
                sb.append("  (no adjacent region changes found in samples)\n");
            }
        }
        
        return sb.toString();
    }
    
    // ==================== Sample Records ====================
    
    public record ClimateSample(
        int quartX, int quartY, int quartZ,
        int blockX, int blockZ,
        long originalTemp, long originalHumid,
        long modifiedTemp, long modifiedHumid,
        String regionName, boolean wasModified
    ) {
        public long tempDelta() { return modifiedTemp - originalTemp; }
        public long humidDelta() { return modifiedHumid - originalHumid; }
    }
    
    public record RegionSample(
        int blockX, int blockZ,
        String regionName
    ) {}
    
    public record BiomeFilterSample(
        int quartX, int quartZ,
        int blockX, int blockZ,
        String selectedBiome,
        String regionName,
        boolean wasFiltered
    ) {}
    
    public record CoordinateSample(
        int blockX, int blockZ,
        String regionName,
        long temperature, long humidity
    ) {}
    
    public record FragmentationAnalysis(
        int checks,
        int discontinuities,
        double score
    ) {}
    
    /**
     * Raw input/output sample from the mixin.
     * Captures exactly what Minecraft sends and what we return.
     */
    public record MixinIOSample(
        int inputQuartX, int inputQuartY, int inputQuartZ,
        int computedBlockX, int computedBlockZ,
        long inputTemp, long inputHumid, long inputCont, long inputErosion, long inputDepth, long inputWeird,
        long outputTemp, long outputHumid,
        String regionName, boolean wasModified
    ) {
        public long tempDelta() { return outputTemp - inputTemp; }
        public long humidDelta() { return outputHumid - inputHumid; }
    }
}
