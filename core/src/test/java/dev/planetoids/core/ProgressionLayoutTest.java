package dev.planetoids.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;

class ProgressionLayoutTest {
    private static final Geometry G = new Geometry(64, 12, 26, 6, 3);

    @Test
    void everySeedHasAllStagesWithNoReservedOrRandomOverlaps() {
        for (long seed : new long[] {0, 1, -1, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var base = new Placement(seed, G, -64, 320, 183);
            var layout = new ProgressionLayout(seed, G, base.spawn(), -64, 320);
            assertEquals(9, layout.sites().size());
            assertEquals(
                    9,
                    layout.sites().stream().map(ProgressionLayout.Site::kind).distinct().count());
            var placement = new Placement(seed, G, -64, 320, 183, layout.reservations());
            var reserved = new java.util.ArrayList<>(layout.reservations());
            reserved.add(base.spawn());
            for (int i = 0; i < reserved.size(); i++) {
                for (int j = i + 1; j < reserved.size(); j++) {
                    assertFalse(reserved.get(i).conflicts(reserved.get(j), G.gap()));
                }
            }
            for (var site : layout.sites()) {
                var body = site.body();
                assertTrue(body.y() - body.radius() >= -64 && body.y() + body.radius() < 320);
                var queried =
                        placement.intersecting(
                                body.x() - 40,
                                -64,
                                body.z() - 40,
                                body.x() + 40,
                                319,
                                body.z() + 40);
                assertTrue(queried.contains(body));
                for (var p : queried) {
                    if (p.material() >= 0) {
                        assertFalse(p.conflicts(body, G.gap()));
                    }
                }
            }
            assertEquals(
                    layout.sites(), new ProgressionLayout(seed, G, base.spawn(), -64, 320).sites());
        }
    }

    @Test
    void reservedChunksAreIdenticalInAnyThreadOrder() throws Exception {
        var base = new Placement(123, G, -64, 320, 183);
        var layout = new ProgressionLayout(123, G, base.spawn(), -64, 320);
        var placement = new Placement(123, G, -64, 320, 183, layout.reservations());
        try (var pool = Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (var site : layout.sites().reversed()) {
                int cx = Math.floorDiv(site.x(), 16), cz = Math.floorDiv(site.z(), 16);
                var expected = placement.chunk(cx, cz);
                futures.add(pool.submit(() -> assertEquals(expected, placement.chunk(cx, cz))));
            }
            for (var future : futures) {
                future.get();
            }
        }
    }

    @Test
    void endVaultQueriesFindBothSidesOfPositiveAndNegativeSeams() {
        for (int centre : List.of(-3072, -1024, 1024, 3072)) {
            var found = new HashSet<ProgressionLayout.Site>();
            for (int x = Math.floorDiv(centre - 16, 16); x <= Math.floorDiv(centre + 16, 16); x++) {
                for (int z = Math.floorDiv(centre - 16, 16);
                        z <= Math.floorDiv(centre + 16, 16);
                        z++) {
                    var sites = ProgressionLayout.endVaults(x, z, 0, 256);
                    var body = new Planet(new Cell(0, 0, 0), centre, 128, centre, 16, 0, -11);
                    assertEquals(
                            body.intersects(x * 16L, 0, z * 16L, x * 16L + 15, 255, z * 16L + 15),
                            sites.stream().anyMatch(s -> s.x() == centre && s.z() == centre));
                    found.addAll(sites);
                }
            }
            assertEquals(1, found.size());
        }
        assertTrue(ProgressionLayout.endVaults(0, 0, 0, 256).isEmpty());
    }

    @Test
    void progressionRejectsWorldsTooSmallForFeatures() {
        var g = new Geometry(16, 4, 8, 0, 2);
        var base = new Placement(0, g, -64, 320, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProgressionLayout(0, g, base.spawn(), -64, 320));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Placement(0, G, -64, 320, 1, List.of(base.spawn())));
    }
}
