package dev.planetoids.core;

import java.util.List;

/**
 * Reserved resource destinations. All geometry, including decoration space, fits inside each
 * sphere.
 */
public final class ProgressionLayout {
    public enum Kind {
        FARM(-2),
        QUARRY(-3),
        IRON(-4),
        WATER(-5),
        LAVA(-6),
        PASTURE(-7),
        BREWING(-8),
        TEMPLATE(-9),
        PORTAL(-10),
        END_VAULT(-11);
        private final int material;

        Kind(int material) {
            this.material = material;
        }

        public int material() {
            return material;
        }
    }

    public record Site(Kind kind, Planet body) {
        public int x() {
            return Math.toIntExact(body.x());
        }

        public int floorY() {
            return Math.toIntExact(body.y());
        }

        public int z() {
            return Math.toIntExact(body.z());
        }

        public boolean owns(int x, int z, int chunkX, int chunkZ) {
            return Math.floorDiv(x, 16) == chunkX && Math.floorDiv(z, 16) == chunkZ;
        }
    }

    private final List<Site> sites;

    public ProgressionLayout(long seed, Geometry geometry, Planet spawn, int minY, int maxY) {
        if (geometry.maxRadius() < 12 || (long) maxY - minY < 64) {
            throw new IllegalArgumentException(
                    "Progression requires max-radius >= 12 and at least 64 blocks of world height");
        }
        int spacing = Math.max(72, 2 * geometry.maxRadius() + geometry.gap() + 24);
        int floor = (int) Math.min(spawn.y() + spawn.radius(), (long) maxY - 13);
        int rotation = (int) (Placement.mix64(seed ^ 0x42626c7565707269L) & 3);
        sites =
                List.of(
                        site(Kind.FARM, spacing, 0, floor, rotation),
                        site(Kind.QUARRY, 0, spacing, floor, rotation),
                        site(Kind.IRON, -spacing, 0, floor, rotation),
                        site(Kind.WATER, 0, -spacing, floor, rotation),
                        site(Kind.LAVA, spacing, -spacing, floor, rotation),
                        site(Kind.PASTURE, -spacing, spacing, floor, rotation),
                        site(Kind.BREWING, 6 * spacing, 2 * spacing, floor, rotation),
                        site(Kind.TEMPLATE, -6 * spacing, -2 * spacing, floor, rotation),
                        site(Kind.PORTAL, 12 * spacing, 4 * spacing, floor, rotation));
    }

    private static Site site(Kind kind, int x, int z, int floor, int rotation) {
        for (int i = 0; i < rotation; i++) {
            int oldX = x;
            x = -z;
            z = oldX;
        }
        return new Site(
                kind, new Planet(new Cell(x, floor, z), x, floor, z, 12, 0, kind.material()));
    }

    public List<Site> sites() {
        return sites;
    }

    public Site site(Kind kind) {
        return sites.stream().filter(s -> s.kind() == kind).findFirst().orElseThrow();
    }

    public List<Planet> reservations() {
        return sites.stream().map(Site::body).toList();
    }

    /** One vault per 2048-block square in the outer End; query is constant-sized. */
    public static List<Site> endVaults(int chunkX, int chunkZ, int minY, int maxY) {
        int floor = Math.clamp(128, minY + 17, maxY - 17);
        long x = chunkX * 16L, z = chunkZ * 16L;
        var result = new java.util.ArrayList<Site>();
        for (long cx = Math.floorDiv(x - 16, 2048); cx <= Math.floorDiv(x + 31, 2048); cx++) {
            for (long cz = Math.floorDiv(z - 16, 2048); cz <= Math.floorDiv(z + 31, 2048); cz++) {
                long bx = cx * 2048 + 1024, bz = cz * 2048 + 1024;
                if (bx < Integer.MIN_VALUE
                        || bx > Integer.MAX_VALUE
                        || bz < Integer.MIN_VALUE
                        || bz > Integer.MAX_VALUE) {
                    continue;
                }
                Planet body =
                        new Planet(
                                new Cell(cx, 0, cz),
                                bx,
                                floor,
                                bz,
                                16,
                                0,
                                Kind.END_VAULT.material());
                if (body.intersects(x, minY, z, x + 15, maxY - 1, z + 15)) {
                    result.add(new Site(Kind.END_VAULT, body));
                }
            }
        }
        return List.copyOf(result);
    }
}
