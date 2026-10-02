package com.mcgee.smp.shards;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everyone's shards, keyed by UUID. Shards are the premium currency: earned by
 * playing (a few every few minutes online) or bought with money, and spent on
 * eternal gear and ability tools.
 * Saved to plugins/McGeeSMP/shards.yml periodically and on shutdown.
 */
public final class Shards {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Long> shards = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public Shards(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shards.yml");
        load();
    }

    public synchronized void load() {
        shards.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            try {
                shards.put(UUID.fromString(key), Math.max(0L, s.getLong(key)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping malformed entry in shards.yml: " + key);
            }
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : shards.entrySet()) {
            y.set("players." + e.getKey(), e.getValue());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save shards.yml: " + ex.getMessage());
        }
    }

    public long get(UUID id) {
        return shards.getOrDefault(id, 0L);
    }

    public synchronized void add(UUID id, long amount) {
        if (amount <= 0) return;
        shards.merge(id, amount, Long::sum);
        dirty = true;
    }

    /** Removes shards only if the player has enough. */
    public synchronized boolean take(UUID id, long amount) {
        if (amount < 0) return false;
        long cur = get(id);
        if (cur < amount) return false;
        shards.put(id, cur - amount);
        dirty = true;
        return true;
    }

    public synchronized void set(UUID id, long amount) {
        shards.put(id, Math.max(0L, amount));
        dirty = true;
    }
}
