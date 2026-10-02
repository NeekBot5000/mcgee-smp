package com.mcgee.smp.economy;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Opens an account on first join, with a special amount for named players. */
public final class JoinListener implements Listener {
    private final JavaPlugin plugin;
    private final Balances balances;

    public JoinListener(JavaPlugin plugin, Balances balances) {
        this.plugin = plugin;
        this.balances = balances;
    }

    /** Java names, Bedrock names via Floodgate (".Name_With_Spaces") and the
     *  gamertag as typed ("Name With Spaces") all normalise to the same key. */
    static String normalise(String name) {
        String s = name.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith(".")) s = s.substring(1);
        return s.replace(' ', '_');
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        balances.rememberName(p.getUniqueId(), p.getName());
        if (balances.hasAccount(p.getUniqueId())) return;

        double opening = balances.startingBalance();
        ConfigurationSection special = plugin.getConfig().getConfigurationSection("economy.starting-balances");
        if (special != null) {
            String me = normalise(p.getName());
            for (String key : special.getKeys(false)) {
                if (normalise(key).equals(me)) {
                    opening = special.getDouble(key, opening);
                    plugin.getLogger().info("Starting balance for " + p.getName() + ": " + Money.format(opening));
                    break;
                }
            }
        }
        balances.create(p.getUniqueId(), p.getName(), opening);
    }
}
