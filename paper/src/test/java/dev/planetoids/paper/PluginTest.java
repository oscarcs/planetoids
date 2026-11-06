package dev.planetoids.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class PluginTest {
    private PlanetoidsPlugin plugin;

    @BeforeEach
    void start() {
        MockBukkit.mock();
        plugin = MockBukkit.load(PlanetoidsPlugin.class);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    private World world(boolean planetoids) {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        if (planetoids) {
            when(world.getGenerator()).thenReturn(plugin.getDefaultWorldGenerator("test", ""));
        }
        return world;
    }

    @Test
    void pluginLoadsConfigurationAndRegistersSpawnSafety() {
        assertTrue(plugin.isEnabled());
        assertInstanceOf(PlanetoidsGenerator.class, plugin.getDefaultWorldGenerator("test", ""));
        assertTrue(
                HandlerList.getRegisteredListeners(plugin).stream()
                        .anyMatch(listener -> listener.getListener() instanceof SpawnSafety));
        assertThrows(
                IllegalArgumentException.class,
                () -> plugin.getDefaultWorldGenerator("test", "typo"));
    }

    @Test
    void initialSpawnOnlyChangesNewPlayersInPlanetoidWorlds() {
        SpawnSafety safety = new SpawnSafety();
        World w = world(true);
        Location original = new Location(w, 100, 250, 100);
        var connection = mock(PlayerConfigurationConnection.class);
        var event = new AsyncPlayerSpawnLocationEvent(connection, original, true);
        safety.onInitialSpawn(event);
        assertEquals(0.5, event.getSpawnLocation().getX());
        assertEquals(91, event.getSpawnLocation().getBlockY());
        event = new AsyncPlayerSpawnLocationEvent(connection, original, false);
        safety.onInitialSpawn(event);
        assertEquals(original, event.getSpawnLocation());
        Location vanilla = new Location(world(false), 42, 70, 42);
        event = new AsyncPlayerSpawnLocationEvent(connection, vanilla, true);
        safety.onInitialSpawn(event);
        assertEquals(vanilla, event.getSpawnLocation());
    }

    @Test
    void respawnPreservesBedsAndAnchorsButProtectsDefaultSpawn() {
        SpawnSafety safety = new SpawnSafety();
        World w = world(true);
        Player player = mock(Player.class);
        Location original = new Location(w, 100, 250, 100);
        for (int kind = 0; kind < 3; kind++) {
            var event =
                    new PlayerRespawnEvent(
                            player,
                            original,
                            kind == 1,
                            kind == 2,
                            false,
                            PlayerRespawnEvent.RespawnReason.DEATH);
            safety.onRespawn(event);
            if (kind == 0) {
                assertEquals(91, event.getRespawnLocation().getBlockY());
            } else {
                assertEquals(original, event.getRespawnLocation());
            }
        }
    }
}
