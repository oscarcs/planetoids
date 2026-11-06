package dev.planetoids.paper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import dev.planetoids.core.Geometry;
import dev.planetoids.core.ProgressionLayout;
import dev.planetoids.core.ProgressionLayout.Kind;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

class ProgressionTest {
    private World world;
    private PlanetoidsGenerator generator;

    @BeforeEach
    void start() {
        MockBukkit.mock();
        world = mock(World.class);
        when(world.getSeed()).thenReturn(123456789L);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        generator =
                new PlanetoidsGenerator(
                        new Geometry(64, 12, 26, 6, 3),
                        List.of(new Palette(Material.DIRT, Material.STONE)),
                        true);
        when(world.getGenerator()).thenReturn(generator);
    }

    @AfterEach
    void stop() {
        MockBukkit.unmock();
    }

    @Test
    void everyBlueprintStateIsValidAndFeaturesFitTheirReservedSpheres() {
        var specs = new java.util.HashSet<ProgressionBlocks.Spec>();
        var sites = new java.util.ArrayList<>(generator.progression(world).sites());
        sites.addAll(ProgressionLayout.endVaults(64, 64, 0, 256));
        for (var site : sites) {
            int r = site.body().radius();
            for (int x = site.x() - r; x <= site.x() + r; x++) {
                for (int z = site.z() - r; z <= site.z() + r; z++) {
                    for (int y = site.floorY() - r; y <= site.floorY() + r; y++) {
                        if (site.body().contains(x, y, z)) {
                            specs.add(ProgressionBlocks.at(site, x, y, z));
                        }
                    }
                }
            }
        }
        for (var spec : specs) {
            assertEquals(spec.material(), spec.data().getMaterial());
        }
        for (Kind kind : List.of(Kind.BREWING, Kind.TEMPLATE, Kind.PORTAL)) {
            var site = generator.progression(world).site(kind);
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    for (int y = 1; y <= 7; y++) {
                        var spec =
                                ProgressionBlocks.at(
                                        site, site.x() + x, site.floorY() + y, site.z() + z);
                        if (!spec.material().isAir()) {
                            assertTrue(
                                    site.body()
                                            .contains(
                                                    site.x() + x, site.floorY() + y, site.z() + z));
                        }
                    }
                }
            }
        }
    }

    @Test
    void portalHasExactlyTwelveEmptyFramesFacingInward() {
        var portal = generator.progression(world).site(Kind.PORTAL);
        int frames = 0;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                var spec =
                        ProgressionBlocks.at(
                                portal, portal.x() + x, portal.floorY() + 1, portal.z() + z);
                if (spec.material() != Material.END_PORTAL_FRAME) {
                    continue;
                }
                frames++;
                EndPortalFrame data = (EndPortalFrame) spec.data();
                assertFalse(data.hasEye());
                assertEquals(
                        z == -2
                                ? BlockFace.SOUTH
                                : z == 2
                                        ? BlockFace.NORTH
                                        : x == -2 ? BlockFace.EAST : BlockFace.WEST,
                        data.getFacing());
            }
        }
        assertEquals(12, frames);
    }

    @Test
    void chunkWritesStayLocalAndFarmCropsHaveSupportAndWater() {
        var site = generator.progression(world).site(Kind.FARM);
        for (int cx = Math.floorDiv(site.x() - 12, 16);
                cx <= Math.floorDiv(site.x() + 12, 16);
                cx++) {
            for (int cz = Math.floorDiv(site.z() - 12, 16);
                    cz <= Math.floorDiv(site.z() + 12, 16);
                    cz++) {
                ChunkGenerator.ChunkData data = mock(ChunkGenerator.ChunkData.class);
                doAnswer(
                                call -> {
                                    int x = call.getArgument(0),
                                            y = call.getArgument(1),
                                            z = call.getArgument(2);
                                    assertTrue(
                                            x >= 0 && x < 16 && z >= 0 && z < 16 && y >= -64
                                                    && y < 320);
                                    return null;
                                })
                        .when(data)
                        .setBlock(anyInt(), anyInt(), anyInt(), any(BlockData.class));
                generator.generateNoise(world, new Random(0), cx, cz, data);
            }
        }
        for (int x : List.of(-3, -1, 1, 3)) {
            for (int z = -4; z <= 2; z++) {
                assertEquals(
                        Material.FARMLAND,
                        ProgressionBlocks.at(site, site.x() + x, site.floorY(), site.z() + z)
                                .material());
                assertEquals(
                        Material.WATER,
                        ProgressionBlocks.at(site, site.x(), site.floorY(), site.z() + z)
                                .material());
            }
        }
        assertEquals(
                Material.SUGAR_CANE,
                ProgressionBlocks.at(site, site.x() + 5, site.floorY() + 1, site.z()).material());
        assertEquals(
                Material.WATER,
                ProgressionBlocks.at(site, site.x() + 4, site.floorY(), site.z()).material());
    }

    @Test
    void eachAnimalAndTileIsCreatedOnlyByItsOwningChunk() {
        LimitedRegion region = mock(LimitedRegion.class);
        when(region.getWorld()).thenReturn(world);
        Map<List<Integer>, CreatureSpawner> spawners = new HashMap<>();
        Map<List<Integer>, Inventory> chests = new HashMap<>();
        for (var site : generator.progression(world).sites()) {
            if (site.kind() != Kind.BREWING && site.kind() != Kind.TEMPLATE) {
                continue;
            }
            var spawner = mock(CreatureSpawner.class);
            spawners.put(List.of(site.x(), site.floorY() + 2, site.z()), spawner);
            when(region.getBlockState(site.x(), site.floorY() + 2, site.z())).thenReturn(spawner);
            var chest = mock(Chest.class);
            var inventory = mock(Inventory.class);
            when(chest.getSnapshotInventory()).thenReturn(inventory);
            chests.put(List.of(site.x() + 4, site.floorY() + 1, site.z() + 3), inventory);
            when(region.getBlockState(site.x() + 4, site.floorY() + 1, site.z() + 3))
                    .thenReturn(chest);
        }
        var populator = new ProgressionPopulator(generator);
        var chunks = new java.util.HashSet<List<Integer>>();
        for (var site : generator.progression(world).sites()) {
            for (int x = Math.floorDiv(site.x() - 12, 16);
                    x <= Math.floorDiv(site.x() + 12, 16);
                    x++) {
                for (int z = Math.floorDiv(site.z() - 12, 16);
                        z <= Math.floorDiv(site.z() + 12, 16);
                        z++) {
                    chunks.add(List.of(x, z));
                }
            }
        }
        for (var chunk : chunks) {
            populator.populate(world, new Random(0), chunk.getFirst(), chunk.getLast(), region);
        }
        verify(region, times(2)).spawnEntity(any(Location.class), eq(EntityType.COW));
        verify(region, times(2)).spawnEntity(any(Location.class), eq(EntityType.SHEEP));
        verify(region, times(2)).spawnEntity(any(Location.class), eq(EntityType.CHICKEN));
        verify(region, times(2)).spawnEntity(any(Location.class), eq(EntityType.PIG));
        for (var spawner : spawners.values()) {
            verify(spawner, times(1)).update(true, false);
        }
        var template = generator.progression(world).site(Kind.TEMPLATE);
        verify(chests.get(List.of(template.x() + 4, template.floorY() + 1, template.z() + 3)))
                .setItem(0, new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        verify(world, never()).getChunkAt(anyInt(), anyInt());
    }

    @Test
    void lootSurvivesApplyingTheCapturedChestState() {
        var blocks = MockBukkit.getMock().addSimpleWorld("loot-state-test");
        var site = generator.progression(world).site(Kind.TEMPLATE);
        var block = blocks.getBlockAt(site.x() + 4, site.floorY() + 1, site.z() + 3);
        block.setType(Material.CHEST);
        var chest = (Chest) block.getState();
        LimitedRegion region = mock(LimitedRegion.class);
        when(region.getBlockState(site.x() + 4, site.floorY() + 1, site.z() + 3)).thenReturn(chest);
        when(region.getBlockState(site.x(), site.floorY() + 2, site.z()))
                .thenReturn(mock(CreatureSpawner.class));
        new ProgressionPopulator(generator)
                .populate(
                        world,
                        new Random(0),
                        Math.floorDiv(site.x() + 4, 16),
                        Math.floorDiv(site.z() + 3, 16),
                        region);
        assertEquals(
                new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                ((Chest) block.getState()).getBlockInventory().getItem(0));
    }

    @Test
    void eyesConsumeOneItemAndFindOurPortalWithoutAStronghold() {
        var player = mock(Player.class);
        var inventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getEyeLocation()).thenReturn(new Location(world, 0.5, 92.6, 0.5));
        ItemStack held = new ItemStack(Material.ENDER_EYE, 5);
        when(inventory.getItem(EquipmentSlot.HAND)).thenReturn(held);
        var signal = mock(EnderSignal.class);
        when(signal.isValid()).thenReturn(true);
        doAnswer(
                        call -> {
                            Consumer<EnderSignal> initialise = call.getArgument(2);
                            initialise.accept(signal);
                            return signal;
                        })
                .when(world)
                .spawn(
                        any(Location.class),
                        eq(EnderSignal.class),
                        org.mockito.ArgumentMatchers.<Consumer<EnderSignal>>any());
        var event =
                new PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_AIR,
                        held,
                        null,
                        BlockFace.SELF,
                        EquipmentSlot.HAND);
        new ProgressionListener("minecraft:the_end").onEye(event);
        var portal = generator.progression(world).site(Kind.PORTAL);
        verify(signal)
                .setTargetLocation(
                        new Location(
                                world, portal.x() + 0.5, portal.floorY() + 1, portal.z() + 0.5));
        verify(inventory).setItem(EquipmentSlot.HAND, new ItemStack(Material.ENDER_EYE, 4));
        assertEquals(Event.Result.DENY, event.useItemInHand());
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        new ProgressionListener("minecraft:the_end")
                .onEye(
                        new PlayerInteractEvent(
                                player,
                                Action.RIGHT_CLICK_AIR,
                                held,
                                null,
                                BlockFace.SELF,
                                EquipmentSlot.HAND));
        verify(inventory, times(1)).setItem(any(EquipmentSlot.class), any(ItemStack.class));
    }

    @Test
    void eyeHandlerPreservesPortalInsertionAndOtherPluginsCancellation() {
        var player = mock(Player.class);
        var frame = mock(Block.class);
        when(frame.getType()).thenReturn(Material.END_PORTAL_FRAME);
        var handler = new ProgressionListener("minecraft:the_end");
        var event =
                new PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_BLOCK,
                        new ItemStack(Material.ENDER_EYE),
                        frame,
                        BlockFace.UP,
                        EquipmentSlot.HAND);
        handler.onEye(event);
        assertEquals(Event.Result.DEFAULT, event.useItemInHand());
        event =
                new PlayerInteractEvent(
                        player,
                        Action.RIGHT_CLICK_AIR,
                        new ItemStack(Material.ENDER_EYE),
                        null,
                        BlockFace.SELF,
                        EquipmentSlot.HAND);
        event.setUseItemInHand(Event.Result.DENY);
        handler.onEye(event);
        verifyNoInteractions(player);
    }

    @Test
    void endVaultAttachmentIsScopedAndIdempotent() {
        var end = mock(World.class);
        when(end.getEnvironment()).thenReturn(World.Environment.THE_END);
        when(end.getKey()).thenReturn(NamespacedKey.minecraft("the_end"));
        var populators = new java.util.ArrayList<org.bukkit.generator.BlockPopulator>();
        when(end.getPopulators()).thenReturn(populators);
        var handler = new ProgressionListener("minecraft:the_end");
        handler.attachEndVaults(end);
        handler.attachEndVaults(end);
        assertEquals(1, populators.size());
        handler.attachEndVaults(world);
        verify(world, never()).getPopulators();
    }
}
