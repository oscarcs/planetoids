package dev.planetoids.core;

public record Planet(Cell cell, long x, long y, long z, int radius, long priority, int material) {
    public double distanceSquared(long bx, long by, long bz) {
        double dx = (double) bx - x, dy = (double) by - y, dz = (double) bz - z;
        return dx * dx + dy * dy + dz * dz;
    }

    public boolean contains(long bx, long by, long bz) {
        return distanceSquared(bx, by, bz) <= (double) radius * radius;
    }

    public boolean conflicts(Planet other, int gap) {
        double reach = radius + other.radius + gap;
        return distanceSquared(other.x, other.y, other.z) <= reach * reach;
    }

    public boolean intersects(long minX, long minY, long minZ, long maxX, long maxY, long maxZ) {
        return contains(
                Math.clamp(x, minX, maxX), Math.clamp(y, minY, maxY), Math.clamp(z, minZ, maxZ));
    }
}
