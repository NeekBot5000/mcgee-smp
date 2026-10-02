package com.mcgee.smp.misc;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Keeps the locator bar off in every world (task doc #31): on startup, for any
 * world loaded later, and re-checked every few minutes so it stays off even
 * if someone flips the game rule back.
 * Looked up by name so this still loads on versions without the rule.
 */
public final class LocatorBar implements Listener {
    private final JavaPlugin plugin;
    private final GameRule<Boolean> rule;

    @SuppressWarnings("unchecked")
    public LocatorBar(JavaPlugin plugin) {
        this.plugin = plugin;
        GameRule<?> found = GameRule.getByName("locatorBar");
        this.rule = (found != null && found.getType() == Boolean.class) ? (GameRule<Boolean>) found : null;
        if (rule == null) {
            plugin.getLogger().info("No locator bar game rule on this version - nothing to turn off.");
            return;
        }
        int n = 0;
        for (World w : plugin.getServer().getWorlds()) if (apply(w)) n++;
        plugin.getLogger().info("Locator bar off in " + n + " world(s).");
        plugin.getServer().getScheduler().runTaskTimer(plugin,
                () -> plugin.getServer().getWorlds().forEach(this::apply), 6000L, 6000L);
    }

    private boolean apply(World w) {
        if (rule == null) return false;
        if (Boolean.FALSE.equals(w.getGameRuleValue(rule))) return true;
        return w.setGameRule(rule, false);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        apply(e.getWorld());
    }
}
