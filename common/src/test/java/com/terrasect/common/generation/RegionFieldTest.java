package com.terrasect.common.generation;

import com.terrasect.common.runtime.RegionField;
import com.terrasect.common.util.Packer;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

public class RegionFieldTest {

    @Test
    public void testDeterminism() {
        long seed = 12345L;
        int x = 100;
        int z = 200;

        long regionData1 = RegionField.getRegionData(x, z, seed, 512, 200.0f, 2048);
        long regionData2 = RegionField.getRegionData(x, z, seed, 512, 200.0f, 2048);
        assertEquals(regionData1, regionData2, "Region data should be deterministic");
    }

    @Test
    public void testParallelStability() {
        long seed = 67890L;
        int size = 100;
        
        long[] sequential = new long[size];
        for (int i = 0; i < size; i++) {
            sequential[i] = RegionField.getRegionData(i * 10, i * 10, seed, 512, 200.0f, 2048);
        }

        long[] parallel = IntStream.range(0, size).parallel().mapToLong(i -> {
            return RegionField.getRegionData(i * 10, i * 10, seed, 512, 200.0f, 2048);
        }).toArray();

        assertArrayEquals(sequential, parallel, "Parallel execution should match sequential");
    }
    
    @Test
    public void testUnpack() {
        long packed = Packer.packIntFloat(123, 45.6f);
        assertEquals(123, RegionField.unpackRegionId(packed));
        assertEquals(45.6f, RegionField.unpackEdge(packed), 0.0001f);
    }
}
