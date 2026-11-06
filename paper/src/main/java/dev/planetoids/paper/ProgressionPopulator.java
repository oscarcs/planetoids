package dev.planetoids.paper;

import dev.planetoids.core.ProgressionLayout;
import dev.planetoids.core.ProgressionLayout.Kind;
import dev.planetoids.core.ProgressionLayout.Site;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Animals;
import org.bukkit.entity.EntityType;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

final class ProgressionPopulator extends BlockPopulator {
    private final PlanetoidsGenerator generator;
    private final boolean end;

    ProgressionPopulator(PlanetoidsGenerator generator) {
        this.generator = generator;
        end = false;
    }

    ProgressionPopulator() {
        generator = null;
        end = true;
    }

    @Override
    public void populate(
            WorldInfo world, Random ignored, int chunkX, int chunkZ, LimitedRegion region) {
        List<Site> sites =
                end
                        ? ProgressionLayout.endVaults(
                                chunkX, chunkZ, world.getMinHeight(), world.getMaxHeight())
                        : generator.progression(world).sites();
        for (Site site : sites) {
            if (end) {
                drawEndVault(site, chunkX, chunkZ, region);
            }
            if (site.kind() == Kind.PASTURE) {
                animals(site, chunkX, chunkZ, region);
            }
            if (site.kind() == Kind.BREWING
                    || site.kind() == Kind.TEMPLATE
                    || site.kind() == Kind.END_VAULT) {
                initialiseSpawner(site, chunkX, chunkZ, region);
                initialiseChest(site, chunkX, chunkZ, region);
            }
        }
    }

    private static void drawEndVault(Site site, int cx, int cz, LimitedRegion region) {
        Map<ProgressionBlocks.Spec, BlockData> states = new HashMap<>();
        for (int x = cx * 16; x < cx * 16 + 16; x++) {
            for (int z = cz * 16; z < cz * 16 + 16; z++) {
                for (int y = site.floorY() - site.body().radius();
                        y <= site.floorY() + site.body().radius();
                        y++) {
                    if (!site.body().contains(x, y, z)) {
                        continue;
                    }
                    var spec = ProgressionBlocks.at(site, x, y, z);
                    region.setBlockData(
                            x, y, z, states.computeIfAbsent(spec, ProgressionBlocks.Spec::data));
                }
            }
        }
    }

    private static void initialiseSpawner(Site site, int cx, int cz, LimitedRegion region) {
        int x = site.x(), y = site.floorY() + 2, z = site.z();
        if (!site.owns(x, z, cx, cz)) {
            return;
        }
        if (!(region.getBlockState(x, y, z) instanceof CreatureSpawner spawner)) {
            throw new IllegalStateException("Missing progression spawner");
        }
        spawner.setSpawnedType(
                switch (site.kind()) {
                    case BREWING -> EntityType.BLAZE;
                    case TEMPLATE -> EntityType.WITHER_SKELETON;
                    default -> EntityType.SHULKER;
                });
        spawner.setSpawnCount(2);
        spawner.setMaxNearbyEntities(4);
        spawner.setSpawnRange(4);
        spawner.setMinSpawnDelay(200);
        spawner.setMaxSpawnDelay(400);
        spawner.setDelay(100);
        spawner.update(true, false);
    }

    private static void initialiseChest(Site site, int cx, int cz, LimitedRegion region) {
        int x = site.x() + 4, y = site.floorY() + 1, z = site.z() + 3;
        if (!site.owns(x, z, cx, cz)) {
            return;
        }
        if (!(region.getBlockState(x, y, z) instanceof Chest chest)) {
            throw new IllegalStateException("Missing progression chest");
        }
        var inventory = chest.getSnapshotInventory();
        // Population runs once. Chunk loading never re-fills a looted or broken container.
        fillChest(site.kind(), inventory);
        chest.update(true, false);
    }

    private static void fillChest(Kind kind, Inventory inventory) {
        switch (kind) {
            case BREWING -> {
                inventory.setItem(0, new ItemStack(Material.SOUL_SAND, 8));
                inventory.setItem(1, new ItemStack(Material.NETHER_WART, 8));
            }
            case TEMPLATE ->
                    inventory.setItem(
                            0, new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
            case END_VAULT -> {
                inventory.setItem(0, new ItemStack(Material.ELYTRA));
                inventory.setItem(1, new ItemStack(Material.ENDER_PEARL, 4));
            }
            default -> throw new IllegalArgumentException("Not a treasure site");
        }
    }

    private static void animals(Site site, int cx, int cz, LimitedRegion region) {
        List<EntityType> types =
                List.of(
                        EntityType.COW,
                        EntityType.COW,
                        EntityType.SHEEP,
                        EntityType.SHEEP,
                        EntityType.CHICKEN,
                        EntityType.CHICKEN,
                        EntityType.PIG,
                        EntityType.PIG);
        for (int i = 0; i < types.size(); i++) {
            int x = site.x() - 3 + 2 * (i % 4), z = site.z() - 2 + 4 * (i / 4);
            if (!site.owns(x, z, cx, cz)) {
                continue;
            }
            // getWorld is only an entity location reference; no World reads or writes during
            // population.
            var entity =
                    region.spawnEntity(
                            new Location(region.getWorld(), x + 0.5, site.floorY() + 1, z + 0.5),
                            types.get(i));
            if (entity instanceof Animals animal) {
                animal.setAdult();
                animal.setRemoveWhenFarAway(false);
            }
        }
    }
}
