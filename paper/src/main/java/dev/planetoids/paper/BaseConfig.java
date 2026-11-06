package dev.planetoids.paper;

import dev.planetoids.core.WorldBase;

import org.bukkit.configuration.ConfigurationSection;

final class BaseConfig {
    private BaseConfig() {}

    static WorldBase read(ConfigurationSection config) {
        return new WorldBase(
                WorldBase.Mode.parse(config.getString("base.mode", "nothing")),
                integer(config, "base.height", -60),
                integer(config, "base.depth", 3));
    }

    private static int integer(ConfigurationSection config, String path, int fallback) {
        Object value = config.get(path, fallback);
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) {
            throw new IllegalArgumentException(path + " must be an integer");
        }
        return number.intValue();
    }
}
