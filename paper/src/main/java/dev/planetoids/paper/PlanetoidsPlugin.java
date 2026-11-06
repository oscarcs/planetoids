package dev.planetoids.paper;

import dev.planetoids.core.Geometry;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PlanetoidsPlugin extends JavaPlugin {
    private PlanetoidsGenerator generator;

    @Override
    public void onLoad() {
        saveDefaultConfig();
        generator =
                new PlanetoidsGenerator(
                        loadGeometry(),
                        loadPalettes(),
                        getConfig().getBoolean("progression.enabled", true),
                        BaseConfig.read(getConfig()));
    }

    private Geometry loadGeometry() {
        return new Geometry(
                getConfig().getInt("cell-size"),
                getConfig().getInt("min-radius"),
                getConfig().getInt("max-radius"),
                getConfig().getInt("gap"),
                getConfig().getInt("crust-thickness"));
    }

    private List<Palette> loadPalettes() {
        List<Palette> palettes = new ArrayList<>();
        for (Map<?, ?> entry : getConfig().getMapList("planets")) {
            try {
                palettes.add(Palette.from(entry));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid planet preset: " + entry.get("name"), exception);
            }
        }
        return palettes;
    }

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(new SpawnSafety(), this);
        String endKey = getConfig().getString("progression.end-world-key", "minecraft:the_end");
        if (generator.hasProgression()) {
            var progression = new ProgressionListener(endKey);
            getServer().getPluginManager().registerEvents(progression, this);
            getServer().getWorlds().forEach(progression::attachEndVaults);
        }
        java.util.Objects.requireNonNull(getCommand("planetoids"))
                .setExecutor(new ProgressionCommand(this, endKey));
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        if (id != null && !id.isEmpty()) {
            throw new IllegalArgumentException("Planetoids has no generator IDs; use Planetoids");
        }
        if (generator == null) {
            throw new IllegalStateException("Planetoids configuration did not load");
        }
        return generator;
    }
}
