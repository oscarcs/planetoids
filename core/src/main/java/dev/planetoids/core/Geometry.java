package dev.planetoids.core;

public record Geometry(int cellSize, int minRadius, int maxRadius, int gap, int crustThickness) {
    public Geometry {
        if (cellSize < 16
                || cellSize > 256
                || minRadius < 4
                || maxRadius < minRadius
                || maxRadius > cellSize
                || gap < 0
                || gap > cellSize
                || crustThickness < 1
                || crustThickness > minRadius) {
            throw new IllegalArgumentException(
                    "Require cell 16..256, radii 4..cell, gap 0..cell, crust 1..minRadius");
        }
    }

    public int neighbourRange() {
        return (2 * maxRadius + gap + cellSize - 1) / cellSize;
    }
}
