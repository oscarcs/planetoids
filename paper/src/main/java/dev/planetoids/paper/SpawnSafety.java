package dev.planetoids.paper;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;

final class SpawnSafety implements Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInitialSpawn(AsyncPlayerSpawnLocationEvent event) {
        World world = event.getSpawnLocation().getWorld();
        // Only immutable world metadata is read here; no chunks or entities are accessed.
        if (event.isNewPlayer()
                && world != null
                && world.getGenerator() instanceof PlanetoidsGenerator generator) {
            event.setSpawnLocation(generator.spawnLocation(world));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRespawn(PlayerRespawnEvent event) {
        World world = event.getRespawnLocation().getWorld();
        if (!event.isBedSpawn()
                && !event.isAnchorSpawn()
                && world != null
                && world.getGenerator() instanceof PlanetoidsGenerator generator) {
            event.setRespawnLocation(generator.spawnLocation(world));
        }
    }
}
