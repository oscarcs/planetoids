package dev.planetoids.paper;

import dev.planetoids.core.ProgressionLayout.Kind;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

final class ProgressionCommand implements CommandExecutor {
    private final PlanetoidsPlugin plugin;
    private final String endWorldKey;

    ProgressionCommand(PlanetoidsPlugin plugin, String endWorldKey) {
        this.plugin = plugin;
        this.endWorldKey = endWorldKey;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1 || !args[0].equalsIgnoreCase("guide")) {
            sender.sendMessage("Use /planetoids guide");
            return true;
        }
        World world = worldFor(sender);
        if (world == null) {
            sender.sendMessage("No Planetoids world is loaded.");
            return true;
        }
        if (world.getEnvironment() == World.Environment.THE_END
                && world.getKey().toString().equals(endWorldKey)) {
            showEndGuide(sender, world);
            return true;
        }
        if (!(world.getGenerator() instanceof PlanetoidsGenerator generator)
                || !generator.hasProgression()) {
            sender.sendMessage("Progression planets are not enabled in this world.");
            return true;
        }
        showPlanetGuide(sender, world, generator);
        return true;
    }

    private World worldFor(CommandSender sender) {
        return sender instanceof Player player
                ? player.getWorld()
                : plugin.getServer().getWorlds().stream()
                        .filter(w -> w.getGenerator() instanceof PlanetoidsGenerator)
                        .findFirst()
                        .orElse(null);
    }

    private void showEndGuide(CommandSender sender, World world) {
        var location =
                sender instanceof Player player ? player.getLocation() : world.getSpawnLocation();
        long x = Math.floorDiv(location.getBlockX(), 2048) * 2048L + 1024;
        long z = Math.floorDiv(location.getBlockZ(), 2048) * 2048L + 1024;
        sender.sendMessage(
                "Outer-End vault: "
                        + x
                        + ", 128, "
                        + z
                        + ". Watch for shulkers; the vault holds an elytra.");
    }

    private void showPlanetGuide(CommandSender sender, World world, PlanetoidsGenerator generator) {
        sender.sendMessage(
                "Harvest the tree planet, craft planks and bridge to nearby resources. Destination"
                        + " floors:");
        for (var site : generator.progression(world).sites()) {
            if (site.kind() == Kind.PORTAL && sender instanceof Player) {
                continue;
            }
            String name =
                    switch (site.kind()) {
                        case FARM -> "Farm: crops, sugar cane, bamboo, cocoa and mushrooms";
                        case PASTURE -> "Pasture: breeding pairs for food, leather, wool and eggs";
                        case QUARRY -> "Quarry: stone and coal";
                        case IRON -> "Iron";
                        case WATER -> "Water vessel";
                        case LAVA -> "Lava reservoir";
                        case BREWING -> "Brewing outpost: blazes and nether wart";
                        case TEMPLATE -> "Guarded vault: netherite upgrade template";
                        default -> site.kind().name();
                    };
            sender.sendMessage(name + " — " + site.x() + ", " + site.floorY() + ", " + site.z());
        }
        sender.sendMessage(
                "Craft Eyes of Ender to find the End portal planet. Vaults in the outer End hold"
                        + " elytra and shulkers.");
    }
}
