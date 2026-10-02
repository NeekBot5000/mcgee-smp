package com.mcgee.smp.misc;

import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Keeps the locator bar off in every world (task doc #31): on startup, for any
 * world loaded later, and re-checked every few minutes so it stays off even
 * if someone flips the game rule back.
 *
 * The rule is found by searching every game rule for one with "locator" in
 * its name. Minecraft has renamed game rules between versions (locatorBar vs
 * locator_bar), and a hard-coded name silently matched nothing on 26.2.
 */
public final class LocatorBar implements Listener {
    private final JavaPlugin plugin;
    private final GameRule<Boolean> rule;

    @SuppressWarnings({"unchecked", "deprecation"})
    public LocatorBar(JavaPlugin plugin) {
        this.plugin = plugin;
        GameRule<Boolean> found = null;
        for (GameRule<?> r : GameRule.values()) {
            if (r.getType() == Boolean.class && r.getName().toLowerCase(Locale.ROOT).contains("locator")) {
                found = (GameRule<Boolean>) r;
                break;
            }
        }
        this.rule = found;
        if (rule == null) {
            plugin.getLogger().info("No locator bar game rule on this version - nothing to turn off.");
            return;
        }
        int n = 0;
        for (World w : plugin.getServer().getWorlds()) if (apply(w)) n++;
        plugin.getLogger().info("Locator bar (" + rule.getName() + ") off in " + n + " world(s).");
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
