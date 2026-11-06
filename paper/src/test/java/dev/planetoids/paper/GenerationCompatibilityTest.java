package dev.planetoids.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.planetoids.core.Geometry;
import dev.planetoids.core.Placement;
import dev.planetoids.core.ProgressionLayout;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Fixed outputs guard against accidental world changes during refactoring. */
class GenerationCompatibilityTest {
    private static final Geometry GEOMETRY = new Geometry(64, 12, 26, 6, 3);

    @Test
    void placementMatchesReleasedWorlds() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (long seed : new long[] {0, -1, 123456789L, Long.MIN_VALUE, Long.MAX_VALUE}) {
            var base = new Placement(seed, GEOMETRY, -64, 320, 183);
            var layout = new ProgressionLayout(seed, GEOMETRY, base.spawn(), -64, 320);
            var placement = new Placement(seed, GEOMETRY, -64, 320, 183, layout.reservations());
            for (var planet : placement.intersecting(-256, -64, -256, 256, 319, 256)) {
                digest.update((planet.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        assertEquals(
                "fb40946a5805791383f7182990cdd8f5c9abb07fba9d26d0c631b2d475801fb6",
                HexFormat.of().formatHex(digest.digest()));
    }

    @Test
    void blueprintsMatchReleasedWorlds() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (long seed : new long[] {0, -1, 123456789L}) {
            var base = new Placement(seed, GEOMETRY, -64, 320, 183);
            var sites =
                    new java.util.ArrayList<>(
                            new ProgressionLayout(seed, GEOMETRY, base.spawn(), -64, 320).sites());
            sites.addAll(ProgressionLayout.endVaults(-64, 64, 0, 256));
            for (var site : sites) {
                int radius = site.body().radius();
                for (int x = site.x() - radius; x <= site.x() + radius; x++) {
                    for (int z = site.z() - radius; z <= site.z() + radius; z++) {
                        for (int y = site.floorY() - radius; y <= site.floorY() + radius; y++) {
                            if (!site.body().contains(x, y, z)) {
                                continue;
                            }
                            var block = ProgressionBlocks.at(site, x, y, z);
                            digest.update(
                                    (block.material().name() + "[" + block.properties() + "]\n")
                                            .getBytes(StandardCharsets.UTF_8));
                        }
                    }
                }
            }
        }
        assertEquals(
                "edd02079e04ce46c396fefbfef17f7b15c40c73505a84bec8a4aeb67dae82b7f",
                HexFormat.of().formatHex(digest.digest()));
    }
}
