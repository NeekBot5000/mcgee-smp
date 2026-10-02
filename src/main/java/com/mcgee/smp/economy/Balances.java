package com.mcgee.smp.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everyone's money, keyed by UUID so renames never lose a balance.
 * Saved to plugins/McGeeSMP/balances.yml: periodically, and on shutdown.
 */
public final class Balances {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    public Balances(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "balances.yml");
        load();
    }

    public double startingBalance() {
        return plugin.getConfig().getDouble("economy.starting-balance", 100);
    }

    public synchronized void load() {
        balances.clear();
        names.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                balances.put(id, s.getDouble(key + ".balance"));
                String name = s.getString(key + ".name");
                if (name != null) names.put(id, name);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping malformed entry in balances.yml: " + key);
            }
        }
    }

    public synchronized void save() {
        if (!dirty) return;
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Double> e : balances.entrySet()) {
            String k = "players." + e.getKey();
            y.set(k + ".balance", e.getValue());
            String name = names.get(e.getKey());
            if (name != null) y.set(k + ".name", name);
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save balances.yml: " + ex.getMessage());
        }
    }

    public boolean hasAccount(UUID id) {
        return balances.containsKey(id);
    }

    /** Creates the account if missing. Returns true when it was newly created. */
    public synchronized boolean create(UUID id, String name, double opening) {
        if (balances.containsKey(id)) return false;
        balances.put(id, Money.round(opening));
        if (name != null) names.put(id, name);
        dirty = true;
        return true;
    }

    public void rememberName(UUID id, String name) {
        if (name != null && !name.equals(names.get(id))) {
            names.put(id, name);
            dirty = true;
        }
    }

    public String nameOf(UUID id) {
        return names.getOrDefault(id, id.toString().substring(0, 8));
    }

    public double get(UUID id) {
        return balances.getOrDefault(id, 0.0);
    }

    public synchronized boolean withdraw(UUID id, double amount) {
        if (amount < 0) return false;
        double cur = get(id);
        if (cur + 1e-9 < amount) return false;
        balances.put(id, Money.round(cur - amount));
        dirty = true;
        return true;
    }

    public synchronized boolean deposit(UUID id, double amount) {
        if (amount < 0) return false;
        balances.put(id, Money.round(get(id) + amount));
        dirty = true;
        return true;
    }

    public synchronized void set(UUID id, double amount) {
        balances.put(id, Money.round(Math.max(0, amount)));
        dirty = true;
    }

    /** Transfer used by /pay: both sides change together or neither does. */
    public synchronized boolean transfer(UUID from, UUID to, double amount) {
        if (amount <= 0 || from.equals(to)) return false;
        if (!withdraw(from, amount)) return false;
        deposit(to, amount);
        return true;
    }

    public List<Map.Entry<UUID, Double>> top(int n) {
        List<Map.Entry<UUID, Double>> all = new ArrayList<>(balances.entrySet());
        all.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));
        return all.subList(0, Math.min(n, all.size()));
    }
}
