package dev.planetoids.paper;

import static org.junit.jupiter.api.Assertions.*;

import dev.planetoids.core.Cell;
import dev.planetoids.core.Geometry;
import dev.planetoids.core.Planet;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.Map;

class PaletteTest {
    @BeforeAll
    static void start() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stop() {
        MockBukkit.unmock();
    }

    private static Planet sphere(int radius) {
        return new Planet(new Cell(-1, 0, -1), -1, 64, -1, radius, 0, 0);
    }

    @Test
    void weightedSlotsAreExactAndOldPresetsStillWork() {
        Palette a = Palette.from(Map.of("crust", "STONE", "core", "IRON_ORE", "weight", 2));
        Palette b =
                Palette.from(
                        Map.of("crust", "DEEPSLATE", "core", "DEEPSLATE_COPPER_ORE", "weight", 5));
        var g = new PlanetoidsGenerator(new Geometry(32, 6, 15, 3, 2), List.of(a, b));
        for (int i = 0; i < 7; i++) {
            assertSame(i < 2 ? a : b, g.palette(i));
        }
        assertThrows(IllegalArgumentException.class, () -> g.palette(7));
        assertEquals(1, Palette.from(Map.of("crust", "STONE", "core", "COAL_ORE")).weight());
        for (Object invalid : List.of(0, -1, 1.5, "2", 10001)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            Palette.from(
                                    Map.of(
                                            "crust",
                                            "STONE",
                                            "core",
                                            "COAL_ORE",
                                            "weight",
                                            invalid)));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> Palette.from(Map.of("crust", "NO_SUCH_BLOCK", "core", "STONE")));
    }

    @Test
    void everySandColumnHasStableSupportAndEarthHasAnUpperSurface() {
        Planet p = sphere(12);
        Palette sand = new Palette(Material.SAND, Material.SAND, 1, null, Material.CLAY);
        Palette earth = new Palette(Material.DIRT, Material.DIRT, 1, Material.GRASS_BLOCK, null);
        for (long x = -13; x <= 11; x++) {
            for (long z = -13; z <= 11; z++) {
                int bottom = Integer.MAX_VALUE, top = Integer.MIN_VALUE;
                for (int y = 52; y <= 76; y++) {
                    if (p.contains(x, y, z)) {
                        bottom = Math.min(bottom, y);
                        top = Math.max(top, y);
                    }
                }
                if (top == Integer.MIN_VALUE) {
                    continue;
                }
                assertEquals(Material.CLAY, sand.blockAt(p, x, bottom, z, true));
                assertEquals(Material.GRASS_BLOCK, earth.blockAt(p, x, top, z, true));
                if (top > bottom) {
                    assertEquals(Material.SAND, sand.blockAt(p, x, bottom + 1, z, false));
                    assertEquals(Material.DIRT, earth.blockAt(p, x, top - 1, z, false));
                }
            }
        }
    }

    @Test
    void fluidAndGravityCoresAreCompletelyEnclosed() {
        Planet p = sphere(12);
        for (Material core :
                List.of(Material.WATER, Material.LAVA, Material.GRAVEL, Material.AIR)) {
            Palette palette = new Palette(Material.GLASS, core);
            int inner = 9;
            for (int x = -13; x <= 11; x++) {
                for (int z = -13; z <= 11; z++) {
                    for (int y = 52; y <= 76; y++) {
                        if (p.distanceSquared(x, y, z) > inner * inner) {
                            continue;
                        }
                        assertEquals(core, palette.blockAt(p, x, y, z, false));
                        for (int[] d :
                                new int[][] {
                                    {1, 0, 0},
                                    {-1, 0, 0},
                                    {0, 1, 0},
                                    {0, -1, 0},
                                    {0, 0, 1},
                                    {0, 0, -1}
                                }) {
                            assertTrue(p.contains(x + d[0], y + d[1], z + d[2]));
                        }
                    }
                }
            }
        }
    }

    @Test
    void leafSupportMatchesNearestLogAcrossNegativeChunkBorders() {
        Planet p = sphere(8);
        int inner = 5;
        for (int x = -9; x <= 7; x++) {
            for (int z = -9; z <= 7; z++) {
                for (int y = 56; y <= 72; y++) {
                    if (!p.contains(x, y, z) || p.distanceSquared(x, y, z) <= inner * inner) {
                        continue;
                    }
                    int expected = 7;
                    for (int a = -6; a <= 6; a++) {
                        for (int b = -6; b <= 6; b++) {
                            for (int c = -6; c <= 6; c++) {
                                int distance = Math.abs(a) + Math.abs(b) + Math.abs(c);
                                if (distance < expected
                                        && p.distanceSquared(x + a, y + b, z + c)
                                                <= inner * inner) {
                                    expected = distance;
                                }
                            }
                        }
                    }
                    assertEquals(expected, LeafSupport.distance(p, inner, x, y, z));
                }
            }
        }
    }

    @Test
    void bundledPresetsUseValidCurrentMaterialsAndCoverModernResources() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (var input = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(input);
            config.load(
                    new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        }
        List<Palette> palettes = config.getMapList("planets").stream().map(Palette::from).toList();
        assertEquals(54, palettes.size());
        for (Material core :
                List.of(
                        Material.COPPER_ORE,
                        Material.DEEPSLATE_DIAMOND_ORE,
                        Material.LAPIS_ORE,
                        Material.EMERALD_ORE,
                        Material.ANCIENT_DEBRIS,
                        Material.SULFUR,
                        Material.CINNABAR,
                        Material.POPLAR_LOG)) {
            assertTrue(palettes.stream().anyMatch(p -> p.core() == core), core.name());
        }
        assertTrue(
                palettes.stream()
                                .filter(p -> p.core() == Material.COAL_ORE)
                                .mapToInt(Palette::weight)
                                .sum()
                        > palettes.stream()
                                .filter(p -> p.core() == Material.DIAMOND_ORE)
                                .mapToInt(Palette::weight)
                                .sum());
    }
}
