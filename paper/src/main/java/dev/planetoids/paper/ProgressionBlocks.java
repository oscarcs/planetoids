package dev.planetoids.paper;

import dev.planetoids.core.ProgressionLayout.Kind;
import dev.planetoids.core.ProgressionLayout.Site;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

/** Pure blueprint; callers cache the immutable specifications per generation invocation. */
final class ProgressionBlocks {
    record Spec(Material material, String properties) {
        Spec(Material material) {
            this(material, "");
        }

        BlockData data() {
            return properties.isEmpty()
                    ? material.createBlockData()
                    : material.createBlockData("[" + properties + "]");
        }
    }

    private ProgressionBlocks() {}

    static Spec at(Site site, long x, int y, long z) {
        int dx = Math.toIntExact(x - site.body().x()), dz = Math.toIntExact(z - site.body().z());
        int dy = y - site.floorY();
        boolean shell =
                site.body().distanceSquared(x, y, z) > Math.pow(site.body().radius() - 3, 2);
        return switch (site.kind()) {
            case QUARRY -> spec(shell ? Material.STONE : Material.COAL_ORE);
            case IRON -> spec(shell ? Material.STONE : Material.IRON_ORE);
            case WATER -> spec(shell ? Material.GLASS : Material.WATER);
            case LAVA -> spec(shell ? Material.STONE : Material.LAVA);
            case FARM -> farm(dx, dy, dz);
            case PASTURE -> pasture(dx, dy, dz);
            case BREWING, TEMPLATE, END_VAULT -> outpost(site.kind(), dx, dy, dz);
            case PORTAL -> portal(dx, dy, dz);
        };
    }

    private static Spec spec(Material material) {
        return new Spec(material);
    }

    private static Spec farm(int x, int y, int z) {
        if (y < 0) {
            return spec(Material.DIRT);
        }
        if (y == 0) {
            return farmFloor(x, z);
        }
        if (y == 1 && isCropRow(x, z)) {
            return crop(x);
        }

        if (x == 5 && z >= -3 && z <= 1 && y >= 1 && y <= 3) {
            return spec(Material.SUGAR_CANE);
        }
        if (x == 5 && z == 4 && y >= 1 && y <= 3) {
            return spec(Material.BAMBOO);
        }
        if (y == 1 && z == 4 && x == -5) {
            return spec(Material.MELON);
        }
        if (y == 1 && z == 4 && x == -3) {
            return spec(Material.PUMPKIN);
        }
        if (x == -5 && z == -4 && y >= 1 && y <= 3) {
            return spec(Material.JUNGLE_LOG);
        }
        if (x == -6 && z == -4 && y == 2) {
            return new Spec(Material.COCOA, "age=2,facing=east");
        }
        if (x == -5 && y == 1 && z == 0) {
            return spec(Material.RED_MUSHROOM);
        }
        if (x == -5 && y == 1 && z == 1) {
            return spec(Material.BROWN_MUSHROOM);
        }
        return spec(Material.AIR);
    }

    private static boolean isCropRow(int x, int z) {
        return (x == -3 || x == -1 || x == 1 || x == 3) && z >= -4 && z <= 2;
    }

    private static Spec farmFloor(int x, int z) {
        boolean channel = (x == 0 || x == 4) && z >= -5 && z <= 4;
        if (channel) {
            return spec(Material.WATER);
        }
        if (isCropRow(x, z)) {
            return new Spec(Material.FARMLAND, "moisture=7");
        }
        if (x == -5 && (z == 0 || z == 1)) {
            return spec(Material.MYCELIUM);
        }
        return spec(Material.GRASS_BLOCK);
    }

    private static Spec crop(int x) {
        Material material =
                switch (x) {
                    case -3 -> Material.WHEAT;
                    case -1 -> Material.CARROTS;
                    case 1 -> Material.POTATOES;
                    default -> Material.BEETROOTS;
                };
        int age = material == Material.BEETROOTS ? 3 : 7;
        return new Spec(material, "age=" + age);
    }

    private static Spec pasture(int x, int y, int z) {
        if (y < 0) {
            return spec(Material.DIRT);
        }
        if (y == 0) {
            return spec(Material.GRASS_BLOCK);
        }
        if (y == 1
                && Math.abs(x) <= 6
                && Math.abs(z) <= 6
                && (Math.abs(x) == 6 || Math.abs(z) == 6)) {
            if (x == 0 && z == -6) {
                return new Spec(Material.OAK_FENCE_GATE, "facing=south");
            }
            boolean north = (Math.abs(x) == 6 && z > -6), south = (Math.abs(x) == 6 && z < 6);
            boolean west = (Math.abs(z) == 6 && x > -6), east = (Math.abs(z) == 6 && x < 6);
            return new Spec(
                    Material.OAK_FENCE,
                    "north=" + north + ",south=" + south + ",west=" + west + ",east=" + east);
        }
        return spec(Material.AIR);
    }

    private static Spec outpost(Kind kind, int x, int y, int z) {
        Material wall = kind == Kind.END_VAULT ? Material.PURPUR_BLOCK : Material.NETHER_BRICKS;
        if (y < 0) {
            return spec(kind == Kind.END_VAULT ? Material.END_STONE : Material.BLACKSTONE);
        }
        if (y == 0) {
            if (kind == Kind.BREWING && x >= -4 && x <= -2 && z >= -3 && z <= -1) {
                return spec(Material.SOUL_SAND);
            }
            return spec(wall);
        }
        if (y == 1 && kind == Kind.BREWING && x >= -4 && x <= -2 && z >= -3 && z <= -1) {
            return new Spec(Material.NETHER_WART, "age=3");
        }
        if (x == 0 && y == 2 && z == 0) {
            return spec(Material.SPAWNER);
        }
        if (x == 4 && y == 1 && z == 3) {
            return new Spec(Material.CHEST, "facing=north");
        }
        if (Math.abs(x) <= 6 && Math.abs(z) <= 6) {
            if (y == 7) {
                return spec(wall);
            }
            if (y >= 1 && y <= 6 && (Math.abs(x) == 6 || Math.abs(z) == 6)) {
                if (z == -6 && Math.abs(x) <= 1 && y <= 3) {
                    return spec(Material.AIR);
                }
                return spec(wall);
            }
        }
        return spec(Material.AIR);
    }

    private static Spec portal(int x, int y, int z) {
        if (y <= 0) {
            return spec(Material.END_STONE_BRICKS);
        }
        if (y == 1) {
            if (z == -2 && Math.abs(x) <= 1) {
                return new Spec(Material.END_PORTAL_FRAME, "facing=south,eye=false");
            }
            if (z == 2 && Math.abs(x) <= 1) {
                return new Spec(Material.END_PORTAL_FRAME, "facing=north,eye=false");
            }
            if (x == -2 && Math.abs(z) <= 1) {
                return new Spec(Material.END_PORTAL_FRAME, "facing=east,eye=false");
            }
            if (x == 2 && Math.abs(z) <= 1) {
                return new Spec(Material.END_PORTAL_FRAME, "facing=west,eye=false");
            }
        }
        if (Math.abs(x) == 5 && Math.abs(z) == 5) {
            if (y <= 3) {
                return spec(Material.OBSIDIAN);
            }
            if (y == 4) {
                return spec(Material.SEA_LANTERN);
            }
        }
        return spec(Material.AIR);
    }
}
