package dev.planetoids.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

class PlacementTest {
    private static final Geometry GEOMETRY = new Geometry(32, 6, 15, 3, 2);

    private static Placement world(long seed) {
        return new Placement(seed, GEOMETRY, -64, 128, 3);
    }

    private record Block(long x, int y, long z) {}

    private record Value(int material, boolean crust) {}

    private static Map<Block, Value> render(Placement p, int cx, int cz) {
        Map<Block, Value> blocks = new HashMap<>();
        p.generateChunk(
                cx,
                cz,
                (x, y, z, material, crust) -> {
                    assertTrue(x >= 0 && x < 16 && z >= 0 && z < 16);
                    assertTrue(y >= -64 && y < 128);
                    assertNull(
                            blocks.put(
                                    new Block((long) cx * 16 + x, y, (long) cz * 16 + z),
                                    new Value(material, crust)),
                            "Overlapping voxels");
                });
        return blocks;
    }

    @Test
    void negativeCoordinatesUseFloorDivision() {
        Placement p = world(0);
        assertEquals(new Cell(-1, -1, -1), p.cellAt(-1, -32, -31));
        assertEquals(new Cell(-2, -2, 0), p.cellAt(-33, -33, 0));
        for (long x = -100; x <= 100; x++) {
            Cell c = new Cell(x, -x, 2 * x);
            Planet planet = p.candidate(c);
            assertEquals(c, p.cellAt(planet.x(), planet.y(), planet.z()));
        }
    }

    @Test
    void hashHasStableGoldenValues() {
        assertEquals(0L, Placement.mix64(0));
        assertEquals(0x5692161d100b05e5L, Placement.mix64(1));
        assertEquals(0xb4d055fcf2cbbd7bL, Placement.mix64(-1));
    }

    @Test
    void reproducibilityAndSeedSensitivity() {
        Placement a = world(Long.MIN_VALUE), b = world(Long.MIN_VALUE), c = world(Long.MAX_VALUE);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                assertEquals(a.chunk(x, z), b.chunk(x, z));
                assertEquals(render(a, x, z), render(b, x, z));
            }
        }
        assertNotEquals(a.candidate(new Cell(-3, 1, 2)), c.candidate(new Cell(-3, 1, 2)));
    }

    @Test
    void acceptedPlanetsHaveNoOverlapOrGapViolation() {
        for (long seed : new long[] {0, 1, -1, Long.MIN_VALUE, Long.MAX_VALUE}) {
            Placement p = world(seed);
            List<Planet> planets = p.intersecting(-160, -64, -160, 160, 127, 160);
            assertTrue(planets.size() > 10, "Must exercise ordinary planets");
            assertTrue(planets.contains(p.spawn()));
            for (int i = 0; i < planets.size(); i++) {
                Planet a = planets.get(i);
                for (int j = i + 1; j < planets.size(); j++) {
                    assertFalse(a.conflicts(planets.get(j), GEOMETRY.gap()));
                }
            }
        }
    }

    @Test
    void chunkQueriesMatchExhaustiveCandidateScan() {
        Placement p = world(123);
        for (int cx : new int[] {-3, -1, 0, 2}) {
            for (int cz : new int[] {-2, -1, 0, 3}) {
                long x = cx * 16L, z = cz * 16L;
                Set<Planet> expected = new HashSet<>();
                if (p.spawn().intersects(x, -64, z, x + 15, 127, z + 15)) {
                    expected.add(p.spawn());
                }
                for (int a = -5; a <= 5; a++) {
                    for (int b = -4; b <= 5; b++) {
                        for (int c = -5; c <= 5; c++) {
                            Cell cell = new Cell(a, b, c);
                            Planet q = p.candidate(cell);
                            if (q.intersects(x, -64, z, x + 15, 127, z + 15) && p.accepted(cell)) {
                                expected.add(q);
                            }
                        }
                    }
                }
                assertEquals(expected, new HashSet<>(p.chunk(cx, cz)));
            }
        }
    }

    @Test
    void independentlyRenderedChunksMatchWorldSpaceSpheresAcrossSeams() {
        Placement p = world(19);
        List<Planet> region = p.intersecting(-32, -64, -32, 31, 127, 31);
        int ordinaryBlocks = 0;
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) {
                Map<Block, Value> actual = render(p, cx, cz);
                Map<Block, Value> expected = new HashMap<>();
                for (Planet q : region) {
                    int inner = q.radius() - GEOMETRY.crustThickness();
                    for (int x = cx * 16; x < cx * 16 + 16; x++) {
                        for (int z = cz * 16; z < cz * 16 + 16; z++) {
                            for (int y = -64; y < 128; y++) {
                                if (q.contains(x, y, z)) {
                                    expected.put(
                                            new Block(x, y, z),
                                            new Value(
                                                    q.material(),
                                                    q.distanceSquared(x, y, z) > inner * inner));
                                }
                            }
                        }
                    }
                }
                ordinaryBlocks +=
                        (int) actual.values().stream().filter(v -> v.material() >= 0).count();
                assertEquals(expected, actual);
            }
        }
        assertTrue(ordinaryBlocks > 0);
    }

    @Test
    void concurrentAndShuffledGenerationMatchesSequential() throws Exception {
        Placement p = world(-9876);
        List<int[]> chunks = new ArrayList<>();
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                chunks.add(new int[] {x, z});
            }
        }
        Map<String, Map<Block, Value>> expected = new HashMap<>();
        for (int[] c : chunks) {
            expected.put(c[0] + "," + c[1], render(p, c[0], c[1]));
        }
        Collections.shuffle(chunks, new java.util.Random(123));
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (int[] c : chunks) {
                jobs.add(
                        () -> {
                            assertEquals(expected.get(c[0] + "," + c[1]), render(p, c[0], c[1]));
                            return null;
                        });
            }
            for (var future : executor.invokeAll(jobs)) {
                future.get();
            }
        }
    }

    @Test
    void spawnIsSolidWithHeadroomAndNoConflicts() {
        for (long seed = 0; seed < 10; seed++) {
            Placement p = world(seed);
            Planet s = p.spawn();
            int top = (int) (s.y() + s.radius());
            var blocks = render(p, 0, 0);
            assertEquals(new Value(-1, true), blocks.get(new Block(0, top, 0)));
            assertFalse(blocks.containsKey(new Block(0, top + 1, 0)));
            assertFalse(blocks.containsKey(new Block(0, top + 2, 0)));
        }
    }

    @Test
    void extremeGeometryStillSeparatesPlanetsAndProtectsSpawn() {
        for (Geometry geometry :
                List.of(new Geometry(16, 4, 16, 0, 4), new Geometry(16, 16, 16, 16, 1))) {
            for (long seed = -3; seed <= 3; seed++) {
                Placement p = new Placement(seed, geometry, -64, 128, 1);
                List<Planet> planets = p.intersecting(-64, -64, -64, 64, 127, 64);
                for (int i = 0; i < planets.size(); i++) {
                    Planet a = planets.get(i);
                    for (int j = i + 1; j < planets.size(); j++) {
                        assertFalse(a.conflicts(planets.get(j), geometry.gap()));
                    }
                    if (!a.equals(p.spawn())) {
                        assertFalse(a.conflicts(p.spawn(), 3));
                    }
                }
            }
        }
    }

    @Test
    void worldEdgesAndChunkCoordinatesDoNotOverflow() {
        Placement p = world(1234);
        for (int x : new int[] {Integer.MIN_VALUE, -1_875_000, 1_875_000, Integer.MAX_VALUE}) {
            for (Planet q : p.chunk(x, x)) {
                assertTrue(q.intersects(x * 16L, -64, x * 16L, x * 16L + 15, 127, x * 16L + 15));
            }
        }
    }

    @Test
    void validatesGeometryAndBounds() {
        assertThrows(IllegalArgumentException.class, () -> new Geometry(0, 6, 15, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Geometry(32, 16, 15, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Geometry(32, 6, 33, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Geometry(32, 6, 15, 2, 7));
        assertThrows(IllegalArgumentException.class, () -> new Placement(0, GEOMETRY, 0, 30, 1));
        assertThrows(
                IllegalArgumentException.class, () -> world(1).intersecting(1, 0, 0, -1, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        world(1).intersecting(
                                        -1_000_000, -64, -1_000_000, 1_000_000, 127, 1_000_000));
    }
}
