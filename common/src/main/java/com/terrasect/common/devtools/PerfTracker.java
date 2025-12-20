package com.terrasect.common.devtools;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple performance tracker for identifying bottlenecks in generation code.
 * Thread-safe and designed for low overhead when disabled.
 * 
 * Usage:
 *   PerfTracker.start("regionLookup");
 *   // ... code ...
 *   PerfTracker.stop("regionLookup");
 *   
 *   // At end of test:
 *   PerfTracker.printSummary();
 */
public final class PerfTracker {
    
    private static volatile boolean enabled = false;
    
    private static final Map<String, AtomicLong> totalTimes = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> callCounts = new ConcurrentHashMap<>();
    private static final ThreadLocal<Map<String, Long>> startTimes = ThreadLocal.withInitial(ConcurrentHashMap::new);
    
    private PerfTracker() {}
    
    /**
     * Enable performance tracking. Call before the code you want to measure.
     */
    public static void enable() {
        enabled = true;
        reset();
    }
    
    /**
     * Disable performance tracking and clear data.
     */
    public static void disable() {
        enabled = false;
        reset();
    }
    
    /**
     * Check if tracking is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Reset all accumulated timing data.
     */
    public static void reset() {
        totalTimes.clear();
        callCounts.clear();
        startTimes.get().clear();
    }
    
    /**
     * Start timing a named operation. Must be paired with stop().
     */
    public static void start(String name) {
        if (!enabled) return;
        startTimes.get().put(name, System.nanoTime());
    }
    
    /**
     * Stop timing a named operation and accumulate the elapsed time.
     */
    public static void stop(String name) {
        if (!enabled) return;
        
        Long startTime = startTimes.get().remove(name);
        if (startTime == null) return;
        
        long elapsed = System.nanoTime() - startTime;
        totalTimes.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(elapsed);
        callCounts.computeIfAbsent(name, k -> new AtomicLong()).incrementAndGet();
    }
    
    /**
     * Convenience method to time a runnable.
     */
    public static void time(String name, Runnable action) {
        start(name);
        try {
            action.run();
        } finally {
            stop(name);
        }
    }
    
    /**
     * Get total time spent in a named operation (in milliseconds).
     */
    public static double getTotalMs(String name) {
        AtomicLong total = totalTimes.get(name);
        return total != null ? total.get() / 1_000_000.0 : 0.0;
    }
    
    /**
     * Get call count for a named operation.
     */
    public static long getCallCount(String name) {
        AtomicLong count = callCounts.get(name);
        return count != null ? count.get() : 0;
    }
    
    /**
     * Get average time per call (in microseconds).
     */
    public static double getAverageUs(String name) {
        AtomicLong total = totalTimes.get(name);
        AtomicLong count = callCounts.get(name);
        if (total == null || count == null || count.get() == 0) return 0.0;
        return (total.get() / 1000.0) / count.get();
    }
    
    /**
     * Print a summary of all tracked operations, sorted by total time descending.
     */
    public static void printSummary() {
        if (totalTimes.isEmpty()) {
            System.out.println("=== Performance Summary: No data collected ===");
            return;
        }
        
        System.out.println("\n=== Performance Summary ===");
        System.out.printf("%-35s %12s %12s %12s %10s%n", 
            "Operation", "Total (ms)", "Calls", "Avg (µs)", "% Total");
        System.out.println("-".repeat(85));
        
        // Calculate total time across all operations
        double grandTotal = totalTimes.values().stream()
            .mapToLong(AtomicLong::get)
            .sum() / 1_000_000.0;
        
        // Sort by total time descending
        totalTimes.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .forEach(entry -> {
                String name = entry.getKey();
                double totalMs = entry.getValue().get() / 1_000_000.0;
                long calls = callCounts.getOrDefault(name, new AtomicLong()).get();
                double avgUs = calls > 0 ? (entry.getValue().get() / 1000.0) / calls : 0;
                double pct = grandTotal > 0 ? (totalMs / grandTotal) * 100 : 0;
                
                System.out.printf("%-35s %12.2f %12d %12.2f %9.1f%%%n",
                    truncate(name, 35), totalMs, calls, avgUs, pct);
            });
        
        System.out.println("-".repeat(85));
        System.out.printf("%-35s %12.2f%n", "GRAND TOTAL", grandTotal);
        System.out.println();
    }
    
    /**
     * Print a summary formatted as markdown table for easy sharing.
     */
    public static void printMarkdownSummary() {
        if (totalTimes.isEmpty()) {
            System.out.println("No performance data collected.");
            return;
        }
        
        System.out.println("\n| Operation | Total (ms) | Calls | Avg (µs) | % Total |");
        System.out.println("|-----------|------------|-------|----------|---------|");
        
        double grandTotal = totalTimes.values().stream()
            .mapToLong(AtomicLong::get)
            .sum() / 1_000_000.0;
        
        totalTimes.entrySet().stream()
            .sorted((a, b) -> Long.compare(b.getValue().get(), a.getValue().get()))
            .forEach(entry -> {
                String name = entry.getKey();
                double totalMs = entry.getValue().get() / 1_000_000.0;
                long calls = callCounts.getOrDefault(name, new AtomicLong()).get();
                double avgUs = calls > 0 ? (entry.getValue().get() / 1000.0) / calls : 0;
                double pct = grandTotal > 0 ? (totalMs / grandTotal) * 100 : 0;
                
                System.out.printf("| %s | %.2f | %d | %.2f | %.1f%% |%n",
                    name, totalMs, calls, avgUs, pct);
            });
    }
    
    private static String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
