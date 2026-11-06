package dev.planetoids.core;

public record Cell(long x, long y, long z) implements Comparable<Cell> {
    @Override
    public int compareTo(Cell other) {
        int c = Long.compare(x, other.x);
        if (c == 0) {
            c = Long.compare(y, other.y);
        }
        if (c == 0) {
            c = Long.compare(z, other.z);
        }
        return c;
    }
}
