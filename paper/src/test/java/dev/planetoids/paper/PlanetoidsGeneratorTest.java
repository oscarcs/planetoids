package dev.planetoids.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.planetoids.core.Geometry;
import dev.planetoids.core.Placement;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

class PlanetoidsGeneratorTest {
    @BeforeAll
    static void startServer() {
        MockBukkit.mock();
    }

    @AfterAll
    static void stopServer() {
        MockBukkit.unmock();
    }

    private static final Geometry GEOMETRY = new Geometry(64, 12, 26, 6, 3);

    private static PlanetoidsGenerator generator() {
        return new PlanetoidsGenerator(
                GEOMETRY, List.of(new Palette(Material.DIRT, Material.STONE)));
    }

    private static World world() {
        World w = mock(World.class);
        when(w.getSeed()).thenReturn(-1234L);
        when(w.getMinHeight()).thenReturn(-64);
        when(w.getMaxHeight()).thenReturn(320);
        return w;
    }

    @Test
    void adapterWritesExactlyTheCoreOutputAndNeverConsultsRandomOrChunks() {
        var generator = generator();
        World world = world();
        Random random = mock(Random.class);
        for (int cx : new int[] {-3, -1, 0, 1, 3}) {
            ChunkGenerator.ChunkData data = mock(ChunkGenerator.ChunkData.class);
            Map<List<Integer>, Material> actual = new HashMap<>();
            doAnswer(
                            invocation -> {
                                int x = invocation.getArgument(0),
                                        y = invocation.getArgument(1),
                                        z = invocation.getArgument(2);
                                assertTrue(
                                        x >= 0 && x < 16 && z >= 0 && z < 16 && y >= -64
                                                && y < 320);
                                assertNull(actual.put(List.of(x, y, z), invocation.getArgument(3)));
                                return null;
                            })
                    .when(data)
                    .setBlock(anyInt(), anyInt(), anyInt(), any(Material.class));
            doAnswer(
                            invocation -> {
                                int x = invocation.getArgument(0),
                                        y = invocation.getArgument(1),
                                        z = invocation.getArgument(2);
                                BlockData block = invocation.getArgument(3);
                                assertInstanceOf(Leaves.class, block);
                                assertFalse(((Leaves) block).isPersistent());
                                assertTrue(
                                        ((Leaves) block).getDistance() >= 1
                                                && ((Leaves) block).getDistance() <= 6);
                                assertNull(actual.put(List.of(x, y, z), block.getMaterial()));
                                return null;
                            })
                    .when(data)
                    .setBlock(anyInt(), anyInt(), anyInt(), any(BlockData.class));
            generator.generateNoise(world, random, cx, -1, data);
            Map<List<Integer>, Material> expected = new HashMap<>();
            new Placement(-1234, GEOMETRY, -64, 320, 1)
                    .generateChunk(
                            cx,
                            -1,
                            (x, y, z, material, crust) ->
                                    expected.put(
                                            List.of(x, y, z),
                                            material < 0
                                                    ? (crust
                                                            ? Material.OAK_LEAVES
                                                            : Material.OAK_LOG)
                                                    : (crust ? Material.DIRT : Material.STONE)));
            assertEquals(expected, actual);
        }
        verifyNoInteractions(random);
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void disablesVanillaPassesAndUsesFixedBiome() {
        var g = generator();
        WorldInfo w = world();
        Random random = mock(Random.class);
        assertFalse(g.shouldGenerateNoise(w, random, 0, 0));
        assertFalse(g.shouldGenerateSurface(w, random, 0, 0));
        assertFalse(g.shouldGenerateCaves(w, random, 0, 0));
        assertFalse(g.shouldGenerateDecorations(w, random, 0, 0));
        assertFalse(g.shouldGenerateStructures(w, random, 0, 0));
        assertFalse(g.shouldGenerateMobs(w, random, 0, 0));
        var biomes = g.getDefaultBiomeProvider(w);
        assertNotNull(biomes);
        assertEquals(List.of(Biome.PLAINS), biomes.getBiomes(w));
        assertEquals(Biome.PLAINS, biomes.getBiome(w, -999, 64, 999));
    }

    @Test
    void fixedSpawnAndBaseHeightMatchGeneratedBlocks() {
        var g = generator();
        World w = world();
        var spawn = g.getFixedSpawnLocation(w, new Random(42));
        assertEquals(0.5, spawn.getX());
        assertEquals(0.5, spawn.getZ());
        assertEquals(91, spawn.getBlockY());
        Map<Integer, Material> spawnColumn = new HashMap<>();
        new Placement(-1234, GEOMETRY, -64, 320, 1)
                .generateChunk(
                        0,
                        0,
                        (x, y, z, material, crust) -> {
                            if (x == 0 && z == 0) {
                                spawnColumn.put(
                                        y,
                                        material < 0
                                                ? (crust ? Material.OAK_LEAVES : Material.OAK_LOG)
                                                : Material.DIRT);
                            }
                        });
        assertEquals(Material.OAK_LEAVES, spawnColumn.get(spawn.getBlockY() - 1));
        assertFalse(spawnColumn.containsKey(spawn.getBlockY()));
        assertFalse(spawnColumn.containsKey(spawn.getBlockY() + 1));
        for (int x = -40; x <= 40; x += 8) {
            int[] top = {-64};
            int local = Math.floorMod(x, 16);
            new Placement(-1234, GEOMETRY, -64, 320, 1)
                    .generateChunk(
                            Math.floorDiv(x, 16),
                            0,
                            (bx, y, bz, material, crust) -> {
                                if (bx == local && bz == 0) {
                                    top[0] = Math.max(top[0], y);
                                }
                            });
            assertEquals(top[0], g.getBaseHeight(w, new Random(2), x, 0, HeightMap.OCEAN_FLOOR_WG));
        }
    }

    @Test
    void leafExcludingHeightMapFindsTheLogCore() {
        World w = world();
        when(w.getMaxHeight()).thenReturn(100);
        var g = generator();
        assertEquals(90, g.getBaseHeight(w, new Random(1), 0, 0, HeightMap.WORLD_SURFACE_WG));
        assertEquals(
                87, g.getBaseHeight(w, new Random(1), 0, 0, HeightMap.MOTION_BLOCKING_NO_LEAVES));
    }

    private static Map<List<Integer>, String> renderStates(
            PlanetoidsGenerator generator, World world, int cx, int cz) {
        Map<List<Integer>, String> blocks = new HashMap<>();
        ChunkGenerator.ChunkData data = mock(ChunkGenerator.ChunkData.class);
        doAnswer(
                        call -> {
                            assertNull(
                                    blocks.put(
                                            List.of(
                                                    call.getArgument(0),
                                                    call.getArgument(1),
                                                    call.getArgument(2)),
                                            ((Material) call.getArgument(3)).name()));
                            return null;
                        })
                .when(data)
                .setBlock(anyInt(), anyInt(), anyInt(), any(Material.class));
        doAnswer(
                        call -> {
                            assertNull(
                                    blocks.put(
                                            List.of(
                                                    call.getArgument(0),
                                                    call.getArgument(1),
                                                    call.getArgument(2)),
                                            ((BlockData) call.getArgument(3)).getAsString()));
                            return null;
                        })
                .when(data)
                .setBlock(anyInt(), anyInt(), anyInt(), any(BlockData.class));
        generator.generateNoise(world, new Random(0), cx, cz, data);
        return blocks;
    }

    @Test
    void leafStatesAcrossSpawnChunkSeamsAreReproducibleUnderConcurrentGeneration()
            throws Exception {
        var generator = generator();
        World world = world();
        List<List<Integer>> chunks =
                List.of(List.of(-1, -1), List.of(0, -1), List.of(-1, 0), List.of(0, 0));
        Map<List<Integer>, Map<List<Integer>, String>> expected = new HashMap<>();
        for (var chunk : chunks) {
            expected.put(chunk, renderStates(generator, world, chunk.get(0), chunk.get(1)));
        }
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (var chunk : chunks.reversed()) {
                futures.add(
                        pool.submit(
                                () ->
                                        assertEquals(
                                                expected.get(chunk),
                                                renderStates(
                                                        generator,
                                                        world,
                                                        chunk.get(0),
                                                        chunk.get(1)))));
            }
            for (var future : futures) {
                future.get();
            }
        }
    }

    @Test
    void materialConfigurationRejectsGravityAndAir() {
        assertThrows(
                IllegalArgumentException.class, () -> new Palette(Material.SAND, Material.STONE));
        assertThrows(
                IllegalArgumentException.class, () -> new Palette(Material.AIR, Material.STONE));
        assertThrows(IllegalArgumentException.class, () -> new Palette(null, Material.STONE));
        assertThrows(
                IllegalArgumentException.class, () -> new PlanetoidsGenerator(GEOMETRY, List.of()));
    }
}
