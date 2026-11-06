package dev.planetoids.core;

import java.util.ArrayList;
import java.util.List;

/** Immutable, seed-addressed placement. Coordinates are block lattice positions. */
public final class Placement {
    private final long seed;
    private final Geometry geometry;
    private final int minY, maxY, materials;
    private final Planet spawn;
    private final List<Planet> reserved;

    public Placement(long seed, Geometry geometry, int minY, int maxY, int materials) {
        this(seed, geometry, minY, maxY, materials, List.of());
    }

    public Placement(
            long seed,
            Geometry geometry,
            int minY,
            int maxY,
            int materials,
            List<Planet> additionalReservations) {
        if ((long) maxY - minY < 2L * geometry.maxRadius() + 4 || materials < 1) {
            throw new IllegalArgumentException(
                    "World must fit a planet plus spawn headroom; palette must not be empty");
        }
        this.seed = seed;
        this.geometry = geometry;
        this.minY = minY;
        this.maxY = maxY;
        this.materials = materials;
        int r = geometry.maxRadius();
        long y = Math.clamp(64L, (long) minY + r, (long) maxY - r - 3);
        spawn =
                new Planet(
                        new Cell(0, Math.floorDiv(y, geometry.cellSize()), 0), 0, y, 0, r, 0, -1);
        reserved = validateReservations(additionalReservations);
    }

    private List<Planet> validateReservations(List<Planet> additionalReservations) {
        var reservations = new ArrayList<Planet>();
        reservations.add(spawn);

        for (Planet planet : additionalReservations) {
            if (planet.radius() < 1
                    || planet.radius() > geometry.maxRadius()
                    || !fits(planet)
                    || planet.material() >= -1) {
                throw new IllegalArgumentException("Invalid reserved planet");
            }

            for (Planet other : reservations) {
                if (planet.conflicts(other, geometry.gap())) {
                    throw new IllegalArgumentException("Reserved planets overlap");
                }
            }
            reservations.add(planet);
        }
        return List.copyOf(reservations);
    }

    /** SplitMix64 finalizer; every attribute uses a separately salted hash. */
    public static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private long hash(Cell c) {
        return mix64(
                mix64(seed ^ 0x9e3779b97f4a7c15L)
                        ^ mix64(c.x() + 0x632be59bd9b4e019L)
                        ^ mix64(c.y() + 0x8cb92baa3f3d8dd7L)
                        ^ mix64(c.z() + 0xdb4f0b9175ae2165L));
    }

    private static int attribute(long hash, long salt, int bound) {
        return (int) Long.remainderUnsigned(mix64(hash + salt), bound);
    }

    public Cell cellAt(long x, long y, long z) {
        int s = geometry.cellSize();
        return new Cell(Math.floorDiv(x, s), Math.floorDiv(y, s), Math.floorDiv(z, s));
    }

    public Planet candidate(Cell cell) {
        long h = hash(cell);
        int s = geometry.cellSize();
        return new Planet(
                cell,
                Math.addExact(Math.multiplyExact(cell.x(), s), attribute(h, 1, s)),
                Math.addExact(Math.multiplyExact(cell.y(), s), attribute(h, 2, s)),
                Math.addExact(Math.multiplyExact(cell.z(), s), attribute(h, 3, s)),
                geometry.minRadius()
                        + attribute(h, 4, geometry.maxRadius() - geometry.minRadius() + 1),
                mix64(h + 5),
                attribute(h, 6, materials));
    }

    public Planet spawn() {
        return spawn;
    }

    private boolean fits(Planet p) {
        return p.y() - p.radius() >= minY && p.y() + p.radius() < maxY;
    }

    private static boolean outranks(Planet a, Planet b) {
        int comparison = Long.compareUnsigned(a.priority(), b.priority());
        return comparison > 0 || (comparison == 0 && a.cell().compareTo(b.cell()) > 0);
    }

    public boolean accepted(Cell cell) {
        Planet p = candidate(cell);
        // Reserve two air blocks for the player even when the configured gap is zero.
        if (!fits(p)) {
            return false;
        }
        for (Planet reservation : reserved) {
            if (p.conflicts(reservation, Math.max(3, geometry.gap()))) {
                return false;
            }
        }
        return !hasHigherPriorityConflict(p);
    }

    private boolean hasHigherPriorityConflict(Planet planet) {
        Cell cell = planet.cell();
        int n = geometry.neighbourRange();
        for (long x = cell.x() - n; x <= cell.x() + n; x++) {
            for (long y = cell.y() - n; y <= cell.y() + n; y++) {
                for (long z = cell.z() - n; z <= cell.z() + n; z++) {
                    Cell neighbour = new Cell(x, y, z);
                    if (neighbour.equals(cell)) {
                        continue;
                    }
                    Planet q = candidate(neighbour);
                    // Compare against candidates, including ones rejected elsewhere: no recursion.
                    if (fits(q) && outranks(q, planet) && planet.conflicts(q, geometry.gap())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Inclusive block AABB. Intended for bounded chunk/column queries, not whole worlds. */
    public List<Planet> intersecting(
            long minX, int lowY, long minZ, long maxX, int highY, long maxZ) {
        if (minX > maxX || lowY > highY || minZ > maxZ) {
            throw new IllegalArgumentException("Inverted bounds");
        }
        int lo = Math.max(minY, lowY), hi = Math.min(maxY - 1, highY);
        if (lo > hi) {
            return List.of();
        }
        int r = geometry.maxRadius();
        Cell first =
                cellAt(Math.subtractExact(minX, r), lo - (long) r, Math.subtractExact(minZ, r));
        Cell last = cellAt(Math.addExact(maxX, r), hi + (long) r, Math.addExact(maxZ, r));
        long count =
                Math.multiplyExact(
                        Math.multiplyExact(last.x() - first.x() + 1, last.y() - first.y() + 1),
                        last.z() - first.z() + 1);
        if (count > 100_000 || count < 1) {
            throw new IllegalArgumentException("Query exceeds 100000 candidate cells");
        }
        List<Planet> result = new ArrayList<>();
        for (Planet reservation : reserved) {
            if (reservation.intersects(minX, lo, minZ, maxX, hi, maxZ)) {
                result.add(reservation);
            }
        }
        appendAccepted(first, last, new Bounds(minX, lo, minZ, maxX, hi, maxZ), result);
        return List.copyOf(result);
    }

    private record Bounds(long minX, int minY, long minZ, long maxX, int maxY, long maxZ) {
        boolean intersects(Planet planet) {
            return planet.intersects(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private void appendAccepted(Cell first, Cell last, Bounds bounds, List<Planet> result) {
        for (long x = first.x(); x <= last.x(); x++) {
            for (long y = first.y(); y <= last.y(); y++) {
                for (long z = first.z(); z <= last.z(); z++) {
                    Cell c = new Cell(x, y, z);
                    Planet p = candidate(c);
                    if (bounds.intersects(p) && accepted(c)) {
                        result.add(p);
                    }
                }
            }
        }
    }

    public List<Planet> chunk(int chunkX, int chunkZ) {
        long x = (long) chunkX * 16, z = (long) chunkZ * 16;
        return intersecting(x, minY, z, x + 15, maxY - 1, z + 15);
    }

    @FunctionalInterface
    public interface BlockSink {
        /** material -1 is the reserved spawn planet. */
        void set(int localX, int y, int localZ, int material, boolean crust);
    }

    @FunctionalInterface
    public interface PlanetBlockSink {
        void set(int localX, int y, int localZ, Planet planet, boolean crust);
    }

    public void generateChunk(int chunkX, int chunkZ, BlockSink sink) {
        generateChunkPlanets(
                chunkX,
                chunkZ,
                (x, y, z, planet, crust) -> sink.set(x, y, z, planet.material(), crust));
    }

    public void generateChunkPlanets(int chunkX, int chunkZ, PlanetBlockSink sink) {
        long originX = (long) chunkX * 16;
        long originZ = (long) chunkZ * 16;

        for (Planet planet : chunk(chunkX, chunkZ)) {
            drawPlanet(planet, originX, originZ, sink);
        }
    }

    private void drawPlanet(Planet planet, long originX, long originZ, PlanetBlockSink sink) {
        int coreRadius = planet.radius() - geometry.crustThickness();
        int firstX = (int) Math.max(0, planet.x() - planet.radius() - originX);
        int lastX = (int) Math.min(15, planet.x() + planet.radius() - originX);
        int firstZ = (int) Math.max(0, planet.z() - planet.radius() - originZ);
        int lastZ = (int) Math.min(15, planet.z() + planet.radius() - originZ);

        for (int x = firstX; x <= lastX; x++) {
            for (int z = firstZ; z <= lastZ; z++) {
                drawColumn(planet, originX, originZ, x, z, coreRadius, sink);
            }
        }
    }

    private void drawColumn(
            Planet planet,
            long originX,
            long originZ,
            int x,
            int z,
            int coreRadius,
            PlanetBlockSink sink) {
        double radiusSquared = (double) planet.radius() * planet.radius();
        double coreSquared = (double) coreRadius * coreRadius;

        for (int y = (int) (planet.y() - planet.radius()); y <= planet.y() + planet.radius(); y++) {
            double distance = planet.distanceSquared(originX + x, y, originZ + z);
            if (distance <= radiusSquared) {
                sink.set(x, y, z, planet, distance > coreSquared);
            }
        }
    }
}
