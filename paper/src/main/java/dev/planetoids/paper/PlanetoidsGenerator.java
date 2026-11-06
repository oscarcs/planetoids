package dev.planetoids.paper;

import dev.planetoids.core.Geometry;
import dev.planetoids.core.Placement;
import dev.planetoids.core.Planet;
import dev.planetoids.core.ProgressionLayout;
import dev.planetoids.core.WorldBase;

import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class PlanetoidsGenerator extends ChunkGenerator {
    private final Geometry geometry;
    private final List<Palette> palettes;
    private final int totalWeight;
    private final Palette spawnPalette;
    private final boolean progressionEnabled;
    private final WorldBase worldBase;

    PlanetoidsGenerator(Geometry geometry, List<Palette> palettes) {
        this(geometry, palettes, false);
    }

    PlanetoidsGenerator(Geometry geometry, List<Palette> palettes, boolean progressionEnabled) {
        this(geometry, palettes, progressionEnabled, WorldBase.nothing());
    }

    PlanetoidsGenerator(
            Geometry geometry,
            List<Palette> palettes,
            boolean progressionEnabled,
            WorldBase worldBase) {
        this.worldBase = java.util.Objects.requireNonNull(worldBase);
        if (progressionEnabled && geometry.maxRadius() < 12) {
            throw new IllegalArgumentException("Progression requires max-radius >= 12");
        }
        this.progressionEnabled = progressionEnabled;
        this.geometry = geometry;
        spawnPalette = new Palette(Material.OAK_LEAVES, Material.OAK_LOG);
        this.palettes = List.copyOf(palettes);
        if (palettes.isEmpty()) {
            throw new IllegalArgumentException("At least one palette is required");
        }
        totalWeight = palettes.stream().mapToInt(Palette::weight).reduce(0, Math::addExact);
        if (totalWeight > 1_000_000) {
            throw new IllegalArgumentException("Total palette weight exceeds 1000000");
        }
    }

    private int minimumPlanetY(WorldInfo world) {
        int minimum = worldBase.minimumPlanetY(world.getMinHeight(), world.getMaxHeight());
        long required = Math.max(2L * geometry.maxRadius() + 4, progressionEnabled ? 64L : 0L);
        if ((long) world.getMaxHeight() - minimum < required) {
            throw new IllegalArgumentException(
                    "The configured base leaves too little vertical room for planets and spawn");
        }
        return minimum;
    }

    private Material liquidMaterial() {
        return worldBase.mode() == WorldBase.Mode.WATER ? Material.WATER : Material.LAVA;
    }

    private Placement placement(WorldInfo world) {
        Placement base =
                new Placement(
                        world.getSeed(),
                        geometry,
                        minimumPlanetY(world),
                        world.getMaxHeight(),
                        totalWeight);
        if (!progressionEnabled) {
            return base;
        }
        return new Placement(
                world.getSeed(),
                geometry,
                minimumPlanetY(world),
                world.getMaxHeight(),
                totalWeight,
                progression(world).reservations());
    }

    boolean hasProgression() {
        return progressionEnabled;
    }

    ProgressionLayout progression(WorldInfo world) {
        var base =
                new Placement(
                        world.getSeed(),
                        geometry,
                        minimumPlanetY(world),
                        world.getMaxHeight(),
                        totalWeight);
        return new ProgressionLayout(
                world.getSeed(),
                geometry,
                base.spawn(),
                minimumPlanetY(world),
                world.getMaxHeight());
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return progressionEnabled ? List.of(new ProgressionPopulator(this)) : List.of();
    }

    Palette palette(int slot) {
        if (slot < 0) {
            return spawnPalette;
        }
        for (Palette palette : palettes) {
            if (slot < palette.weight()) {
                return palette;
            }
            slot -= palette.weight();
        }
        throw new IllegalArgumentException("Palette slot out of range");
    }

    private record LeafKey(Material material, int distance) {}

    @Override
    public void generateNoise(
            WorldInfo world, Random ignored, int chunkX, int chunkZ, ChunkData data) {
        Placement placement = placement(world);
        drawWorldBase(data);
        ChunkWriter writer = new ChunkWriter(world, chunkX, chunkZ, data);
        placement.generateChunkPlanets(chunkX, chunkZ, writer::write);
    }

    private void drawWorldBase(ChunkData data) {
        if (!worldBase.hasLiquid()) {
            return;
        }

        int floor = worldBase.bedrockY();
        data.setRegion(0, floor, 0, 16, floor + 1, 16, Material.BEDROCK);
        data.setRegion(0, floor + 1, 0, 16, worldBase.height() + 1, 16, liquidMaterial());
    }

    private List<ProgressionLayout.Site> sites(WorldInfo world) {
        return progressionEnabled ? progression(world).sites() : List.of();
    }

    private static ProgressionLayout.Site siteFor(
            Planet planet, List<ProgressionLayout.Site> sites) {
        return sites.stream()
                .filter(site -> site.kind().material() == planet.material())
                .findFirst()
                .orElseThrow();
    }

    /**
     * Each callback owns its caches; no mutable block data is shared between generation threads.
     */
    private final class ChunkWriter {
        private final long originX;
        private final long originZ;
        private final ChunkData data;
        private final List<ProgressionLayout.Site> sites;
        private final Map<LeafKey, BlockData> leafStates = new HashMap<>();
        private final Map<ProgressionBlocks.Spec, BlockData> featureStates = new HashMap<>();

        private ChunkWriter(WorldInfo world, int chunkX, int chunkZ, ChunkData data) {
            originX = chunkX * 16L;
            originZ = chunkZ * 16L;
            this.data = data;
            sites = sites(world);
        }

        private void write(int x, int y, int z, Planet planet, boolean crust) {
            long worldX = originX + x;
            long worldZ = originZ + z;

            if (planet.material() < -1) {
                var spec = ProgressionBlocks.at(siteFor(planet, sites), worldX, y, worldZ);
                data.setBlock(
                        x, y, z, featureStates.computeIfAbsent(spec, ProgressionBlocks.Spec::data));
                return;
            }

            Palette palette = palette(planet.material());
            Material material = palette.blockAt(planet, worldX, y, worldZ, crust);
            if (isLeaves(material)) {
                data.setBlock(x, y, z, leafState(planet, palette, material, worldX, y, worldZ));
            } else {
                data.setBlock(x, y, z, material);
            }
        }

        private BlockData leafState(
                Planet planet, Palette palette, Material material, long x, int y, long z) {
            boolean logCore =
                    palette.core().name().endsWith("_LOG")
                            || palette.core().name().endsWith("_WOOD");
            int innerRadius = planet.radius() - geometry.crustThickness();
            int distance = logCore ? LeafSupport.distance(planet, innerRadius, x, y, z) : 7;
            return leafStates.computeIfAbsent(
                    new LeafKey(material, distance),
                    key -> {
                        Leaves state = (Leaves) key.material().createBlockData();
                        state.setPersistent(false);
                        state.setDistance(key.distance());
                        return state;
                    });
        }
    }

    @Override
    public int getBaseHeight(WorldInfo world, Random ignored, int x, int z, HeightMap map) {
        Placement placement = placement(world);
        int highest = baseHeight(world, map);
        var sites = sites(world);

        for (Planet planet :
                placement.intersecting(
                        x, world.getMinHeight(), z, x, world.getMaxHeight() - 1, z)) {
            highest = highestBlock(planet, x, z, highest, map, sites);
        }
        return highest;
    }

    private int baseHeight(WorldInfo world, HeightMap map) {
        if (!worldBase.hasLiquid()) {
            return world.getMinHeight();
        }
        return contributesToHeight(liquidMaterial(), map)
                ? worldBase.height()
                : worldBase.bedrockY();
    }

    private int highestBlock(
            Planet planet,
            int x,
            int z,
            int minimum,
            HeightMap map,
            List<ProgressionLayout.Site> sites) {
        var site = planet.material() < -1 ? siteFor(planet, sites) : null;
        Palette palette = site == null ? palette(planet.material()) : null;
        int innerRadius = planet.radius() - geometry.crustThickness();
        double innerSquared = (double) innerRadius * innerRadius;

        for (int y = (int) (planet.y() + planet.radius()); y > minimum; y--) {
            if (!planet.contains(x, y, z)) {
                continue;
            }

            Material material =
                    site != null
                            ? ProgressionBlocks.at(site, x, y, z).material()
                            : palette.blockAt(
                                    planet,
                                    x,
                                    y,
                                    z,
                                    planet.distanceSquared(x, y, z) > innerSquared);
            if (contributesToHeight(material, map)) {
                return y;
            }
        }
        return minimum;
    }

    private static boolean isLeaves(Material material) {
        return material.name().endsWith("_LEAVES");
    }

    private static boolean contributesToHeight(Material material, HeightMap map) {
        if (material.isAir()) {
            return false;
        }
        return switch (map) {
            case WORLD_SURFACE, WORLD_SURFACE_WG -> true;
            case OCEAN_FLOOR, OCEAN_FLOOR_WG -> material.isSolid();
            case MOTION_BLOCKING ->
                    material.isSolid() || material == Material.WATER || material == Material.LAVA;
            case MOTION_BLOCKING_NO_LEAVES ->
                    !isLeaves(material)
                            && (material.isSolid()
                                    || material == Material.WATER
                                    || material == Material.LAVA);
        };
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random ignored) {
        return spawnLocation(world);
    }

    Location spawnLocation(World world) {
        Planet p = placement(world).spawn();
        return new Location(world, 0.5, p.y() + p.radius() + 1, 0.5);
    }

    @Override
    public boolean canSpawn(World world, int x, int z) {
        return x == 0 && z == 0;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo world) {
        placement(world); // Validate height-dependent settings before any generation workers run.
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.PLAINS);
            }
        };
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }
}
