package dev.planetoids.core;

import java.util.Locale;
import java.util.Objects;

/**
 * Absolute block Y coordinates: height is the top liquid layer; depth excludes the bedrock floor.
 */
public record WorldBase(Mode mode, int height, int depth) {
    public enum Mode {
        NOTHING,
        WATER,
        LAVA;

        public static Mode parse(String value) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw new IllegalArgumentException(
                        "base.mode must be nothing, water or lava", exception);
            }
        }
    }

    public WorldBase {
        Objects.requireNonNull(mode, "base mode");
        if (depth < 1) {
            throw new IllegalArgumentException("base.depth must be a positive integer");
        }
    }

    public static WorldBase nothing() {
        return new WorldBase(Mode.NOTHING, -60, 3);
    }

    public boolean hasLiquid() {
        return mode != Mode.NOTHING;
    }

    public int bedrockY() {
        return Math.toIntExact((long) height - depth);
    }

    public int minimumPlanetY(int worldMin, int worldMax) {
        if (worldMin >= worldMax) {
            throw new IllegalArgumentException("Invalid world height bounds");
        }
        if (!hasLiquid()) {
            return worldMin;
        }
        int offset = mode == Mode.LAVA ? 5 : 2;
        if ((long) height - depth < worldMin || (long) height + offset >= worldMax) {
            throw new IllegalArgumentException(
                    "base.height/depth must fit the world, including bedrock and space above the"
                            + " liquid");
        }
        // Lava needs four air layers to keep its natural fire ticks from reaching wooden planets.
        return Math.toIntExact((long) height + offset);
    }
}
