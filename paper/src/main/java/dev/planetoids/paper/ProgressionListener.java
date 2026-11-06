package dev.planetoids.paper;

import dev.planetoids.core.ProgressionLayout.Kind;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EnderSignal;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.inventory.EquipmentSlot;

final class ProgressionListener implements Listener {
    private final String endWorldKey;

    ProgressionListener(String endWorldKey) {
        this.endWorldKey = endWorldKey;
    }

    void attachEndVaults(World world) {
        if (world.getEnvironment() == World.Environment.THE_END
                && world.getKey().toString().equals(endWorldKey)
                && world.getPopulators().stream()
                        .noneMatch(p -> p instanceof ProgressionPopulator)) {
            world.getPopulators().add(new ProgressionPopulator());
        }
    }

    @EventHandler
    public void onWorldInit(WorldInitEvent event) {
        attachEndVaults(event.getWorld());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEye(PlayerInteractEvent event) {
        if (!isEyeThrow(event)) {
            return;
        }

        var player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.hasCooldown(Material.ENDER_EYE)) {
            return;
        }
        World world = player.getWorld();
        if (!(world.getGenerator() instanceof PlanetoidsGenerator generator)
                || !generator.hasProgression()) {
            return;
        }
        var portal = generator.progression(world).site(Kind.PORTAL);
        Location target =
                new Location(world, portal.x() + 0.5, portal.floorY() + 1, portal.z() + 0.5);
        event.setUseItemInHand(Event.Result.DENY);
        // Vanilla never creates a signal without a stronghold. Launch our own, then retain vanilla
        // flight/drop behaviour.
        EnderSignal signal =
                world.spawn(
                        player.getEyeLocation(),
                        EnderSignal.class,
                        eye -> eye.setTargetLocation(target));
        if (!signal.isValid()) {
            return;
        }
        consumeEye(player, event.getHand());
        player.setCooldown(Material.ENDER_EYE, 10);
    }

    private static boolean isEyeThrow(PlayerInteractEvent event) {
        return event.getAction() == Action.RIGHT_CLICK_AIR
                && event.getItem() != null
                && event.getItem().getType() == Material.ENDER_EYE
                && event.getHand() != null
                && event.useItemInHand() != Event.Result.DENY;
    }

    private static void consumeEye(Player player, EquipmentSlot hand) {
        if (player.getGameMode() != GameMode.CREATIVE) {
            var held = player.getInventory().getItem(hand).clone();
            held.setAmount(held.getAmount() - 1);
            player.getInventory().setItem(hand, held);
        }
    }
}
