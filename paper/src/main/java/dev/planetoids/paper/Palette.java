package dev.planetoids.paper;

import dev.planetoids.core.Planet;

import org.bukkit.Material;

import java.util.Map;

record Palette(Material crust, Material core, int weight, Material surface, Material bottom) {
    Palette(Material crust, Material core) {
        this(crust, core, 1, null, null);
    }

    Palette {
        if (weight < 1 || weight > 10000) {
            throw new IllegalArgumentException("Weight must be 1..10000");
        }
        if (!block(crust) || !block(core)) {
            throw new IllegalArgumentException("Crust and core must be block materials");
        }
        if (!core.isAir() && !core.isSolid() && core != Material.WATER && core != Material.LAVA) {
            throw new IllegalArgumentException("Core must be solid, air or water/lava");
        }
        if (!crust.isSolid() || (crust.hasGravity() && bottom == null)) {
            throw new IllegalArgumentException(
                    "Crust must be solid; gravity crusts need a supporting bottom");
        }
        if (bottom != null && (!block(bottom) || !bottom.isSolid() || bottom.hasGravity())) {
            throw new IllegalArgumentException("Bottom must be a solid block without gravity");
        }
        if (surface != null && (!block(surface) || !surface.isSolid() || surface.hasGravity())) {
            throw new IllegalArgumentException("Surface must be a solid block without gravity");
        }
    }

    private static boolean block(Material material) {
        return material != null && material.isBlock();
    }

    private static Material material(Map<?, ?> entry, String key, boolean optional) {
        Object value = entry.get(key);
        if (value == null && optional) {
            return null;
        }
        Material material = Material.matchMaterial(String.valueOf(value));
        if (material == null) {
            throw new IllegalArgumentException("Unknown material for " + key + ": " + value);
        }
        return material;
    }

    static Palette from(Map<?, ?> entry) {
        Object rawWeight = entry.containsKey("weight") ? entry.get("weight") : 1;
        if (!(rawWeight instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException("Weight must be an integer: " + rawWeight);
        }
        return new Palette(
                material(entry, "crust", false),
                material(entry, "core", false),
                number.intValue(),
                material(entry, "surface", true),
                material(entry, "bottom", true));
    }

    Material blockAt(Planet planet, long x, int y, long z, boolean shell) {
        if (bottom != null && !planet.contains(x, (long) y - 1, z)) {
            return bottom;
        }
        if (surface != null && !planet.contains(x, (long) y + 1, z)) {
            return surface;
        }
        return shell ? crust : core;
    }
}
