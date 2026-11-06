package dev.planetoids.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorldBaseTest {
    @Test
    void liquidDepthExcludesTheBedrockLayer() {
        for (var mode : new WorldBase.Mode[] {WorldBase.Mode.WATER, WorldBase.Mode.LAVA}) {
            var base = new WorldBase(mode, -40, 5);
            assertEquals(mode == WorldBase.Mode.WATER ? -38 : -35, base.minimumPlanetY(-64, 320));
            assertEquals(-45, base.bedrockY());
            assertEquals(5, base.height() - base.bedrockY());
            var lowest = new WorldBase(mode, -61, 3);
            assertEquals(mode == WorldBase.Mode.WATER ? -59 : -56, lowest.minimumPlanetY(-64, 320));
            assertEquals(-64, lowest.bedrockY());
        }
    }

    @Test
    void nothingKeepsTheOriginalVerticalRange() {
        var base = new WorldBase(WorldBase.Mode.NOTHING, Integer.MAX_VALUE, 3);
        assertEquals(-64, base.minimumPlanetY(-64, 320));
        assertFalse(base.hasLiquid());
    }

    @Test
    void malformedModesAndDepthsAreRejected() {
        assertEquals(WorldBase.Mode.WATER, WorldBase.Mode.parse(" Water "));
        assertThrows(IllegalArgumentException.class, () -> WorldBase.Mode.parse("ocean"));
        assertThrows(IllegalArgumentException.class, () -> WorldBase.Mode.parse(null));
        assertThrows(
                IllegalArgumentException.class, () -> new WorldBase(WorldBase.Mode.LAVA, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new WorldBase(WorldBase.Mode.WATER, 0, -1));
    }

    @Test
    void boundsAndLargeDepthsNeverSilentlyClipOrOverflow() {
        for (var base :
                new WorldBase[] {
                    new WorldBase(WorldBase.Mode.WATER, -64, 1),
                    new WorldBase(WorldBase.Mode.WATER, -40, 25),
                    new WorldBase(WorldBase.Mode.LAVA, 319, 3),
                    new WorldBase(WorldBase.Mode.LAVA, Integer.MIN_VALUE, Integer.MAX_VALUE)
                }) {
            assertThrows(IllegalArgumentException.class, () -> base.minimumPlanetY(-64, 320));
        }
        assertThrows(
                IllegalArgumentException.class, () -> WorldBase.nothing().minimumPlanetY(0, 0));
    }
}
