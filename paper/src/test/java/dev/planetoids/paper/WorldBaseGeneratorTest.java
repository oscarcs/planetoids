package dev.planetoids.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.planetoids.core.Geometry;
import dev.planetoids.core.WorldBase;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

class WorldBaseGeneratorTest {
    private static final Geometry G = new Geometry(64, 12, 26, 6, 3);

    @BeforeAll
    static void start() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stop() {
        MockBukkit.unmock();
    }

    private static World world() {
        var w = mock(World.class);
        when(w.getMinHeight()).thenReturn(-64);
        when(w.getMaxHeight()).thenReturn(320);
        when(w.getSeed()).thenReturn(123456789L);
        return w;
    }

    private static PlanetoidsGenerator generator(WorldBase base, boolean progression) {
        return new PlanetoidsGenerator(
                G, List.of(new Palette(Material.DIRT, Material.STONE)), progression, base);
    }

    private static void put(
            Map<List<Integer>, Material> blocks, int x, int y, int z, Material material) {
        assertTrue(x >= 0 && x < 16 && z >= 0 && z < 16 && y >= -64 && y < 320);
        assertNull(
                blocks.put(List.of(x, y, z), material), "Base and planet writes must not overlap");
    }

    private static Map<List<Integer>, Material> render(
            PlanetoidsGenerator generator, World world, int cx, int cz) {
        Map<List<Integer>, Material> blocks = new HashMap<>();
        var data = mock(ChunkGenerator.ChunkData.class);
        doAnswer(
                        call -> {
                            put(
                                    blocks,
                                    call.getArgument(0),
                                    call.getArgument(1),
                                    call.getArgument(2),
                                    call.getArgument(3));
                            return null;
                        })
                .when(data)
                .setBlock(anyInt(), anyInt(), anyInt(), any(Material.class));
        doAnswer(
                        call -> {
                            put(
                                    blocks,
                                    call.getArgument(0),
                                    call.getArgument(1),
                                    call.getArgument(2),
                                    ((BlockData) call.getArgument(3)).getMaterial());
                            return null;
                        })
                .when(data)
                .setBlock(anyInt(), anyInt(), anyInt(), any(BlockData.class));
        doAnswer(
                        call -> {
                            int x1 = call.getArgument(0),
                                    y1 = call.getArgument(1),
                                    z1 = call.getArgument(2);
                            int x2 = call.getArgument(3),
                                    y2 = call.getArgument(4),
                                    z2 = call.getArgument(5);
                            Material material = call.getArgument(6);
                            for (int x = x1; x < x2; x++) {
                                for (int z = z1; z < z2; z++) {
                                    for (int y = y1; y < y2; y++) {
                                        put(blocks, x, y, z, material);
                                    }
                                }
                            }
                            return null;
                        })
                .when(data)
                .setRegion(
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        any(Material.class));
        generator.generateNoise(world, new Random(0), cx, cz, data);
        return blocks;
    }

    @Test
    void bothLiquidsHaveExactDepthAndAContinuousFloorAcrossNegativeChunkSeams() {
        for (var mode : List.of(WorldBase.Mode.WATER, WorldBase.Mode.LAVA)) {
            var base = new WorldBase(mode, -40, 5);
            var generator = generator(base, true);
            for (int cx : List.of(-1, 0)) {
                for (int cz : List.of(-1, 0)) {
                    var blocks = render(generator, world(), cx, cz);
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            assertEquals(Material.BEDROCK, blocks.get(List.of(x, -45, z)));
                            for (int y = -44; y <= -40; y++) {
                                assertEquals(
                                        mode == WorldBase.Mode.WATER
                                                ? Material.WATER
                                                : Material.LAVA,
                                        blocks.get(List.of(x, y, z)));
                            }
                            assertNull(blocks.get(List.of(x, -46, z)));
                            assertNull(blocks.get(List.of(x, -39, z)));
                        }
                    }
                }
            }
        }
    }

    @Test
    void nothingIsCompatibleWithThePreviousGenerator() {
        var previous =
                new PlanetoidsGenerator(
                        G, List.of(new Palette(Material.DIRT, Material.STONE)), true);
        var configured = generator(new WorldBase(WorldBase.Mode.NOTHING, 300, 10), true);
        for (int x : List.of(-1, 0, 5)) {
            assertEquals(render(previous, world(), x, -1), render(configured, world(), x, -1));
        }
    }

    @Test
    void highBaseMovesSpawnAndProgressionAboveTheLiquid() {
        var base = new WorldBase(WorldBase.Mode.LAVA, 200, 8);
        var generator = generator(base, true);
        var w = world();
        var spawn = generator.getFixedSpawnLocation(w, new Random(0));
        assertTrue(spawn.getY() > 200 + 2 * G.maxRadius());
        var blocks = render(generator, w, 0, 0);
        assertEquals(Material.OAK_LEAVES, blocks.get(List.of(0, spawn.getBlockY() - 1, 0)));
        assertNull(blocks.get(List.of(0, spawn.getBlockY(), 0)));
        assertNull(blocks.get(List.of(0, spawn.getBlockY() + 1, 0)));
        for (var site : generator.progression(w).sites()) {
            assertTrue(site.body().y() - site.body().radius() >= 205);
        }
    }

    @Test
    void baseHeightMapsMatchRenderedBlocks() {
        var w = world();
        for (var mode : List.of(WorldBase.Mode.WATER, WorldBase.Mode.LAVA)) {
            var generator = generator(new WorldBase(mode, 0, 4), false);
            int cx = -3, cz = 2;
            var blocks = render(generator, w, cx, cz);
            for (int x = 0; x < 16; x += 3) {
                for (int z = 0; z < 16; z += 3) {
                    int top = -64, solid = -64;
                    for (int y = -64; y < 320; y++) {
                        Material material = blocks.get(List.of(x, y, z));
                        if (material == null || material.isAir()) {
                            continue;
                        }
                        top = y;
                        if (material.isSolid()) {
                            solid = y;
                        }
                    }
                    assertEquals(
                            top,
                            generator.getBaseHeight(
                                    w,
                                    new Random(0),
                                    cx * 16 + x,
                                    cz * 16 + z,
                                    HeightMap.WORLD_SURFACE_WG));
                    assertEquals(
                            solid,
                            generator.getBaseHeight(
                                    w,
                                    new Random(0),
                                    cx * 16 + x,
                                    cz * 16 + z,
                                    HeightMap.OCEAN_FLOOR_WG));
                }
            }
        }
    }

    @Test
    void invalidHeightSettingsFailBeforeWritingAnyChunks() {
        var w = world();
        for (var base :
                List.of(
                        new WorldBase(WorldBase.Mode.WATER, -64, 1),
                        new WorldBase(WorldBase.Mode.LAVA, 280, 3))) {
            var generator = generator(base, true);
            assertThrows(
                    IllegalArgumentException.class, () -> generator.getDefaultBiomeProvider(w));
            var data = mock(ChunkGenerator.ChunkData.class);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> generator.generateNoise(w, new Random(0), 0, 0, data));
            verifyNoInteractions(data);
        }
    }

    @Test
    void baseConfigurationIsOptionalAndRejectsMalformedValues() throws Exception {
        assertEquals(WorldBase.nothing(), BaseConfig.read(new YamlConfiguration()));
        var config = new YamlConfiguration();
        config.loadFromString("base:\n  mode: water\n  height: -40\n  depth: 5\n");
        assertEquals(new WorldBase(WorldBase.Mode.WATER, -40, 5), BaseConfig.read(config));
        config.set("base.mode", "ocean");
        assertThrows(IllegalArgumentException.class, () -> BaseConfig.read(config));
        config.set("base.mode", "lava");
        config.set("base.height", 1.5);
        assertThrows(IllegalArgumentException.class, () -> BaseConfig.read(config));
        config.set("base.height", 0);
        config.set("base.depth", "three");
        assertThrows(IllegalArgumentException.class, () -> BaseConfig.read(config));
    }
}
