package com.mcgee.smp.social;

import com.mcgee.smp.economy.Balances;
import com.mcgee.smp.economy.Money;
import com.mcgee.smp.shards.Shards;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kill shards (DonutSMP gives 10 per player kill) and bounties.
 * Kill shards are limited to once per killer/victim pair per cooldown, so two
 * friends can't farm each other. Bounties are paid from money taken when the
 * bounty is placed, so no money is ever created.
 */
public final class Combat implements Listener, CommandExecutor {
    private final JavaPlugin plugin;
    private final Shards shards;
    private final Balances balances;
    private final File file;
    private final Map<UUID, Double> bounties = new HashMap<>();
    private final Map<String, Long> lastReward = new HashMap<>();

    public Combat(JavaPlugin plugin, Shards shards, Balances balances) {
        this.plugin = plugin;
        this.shards = shards;
        this.balances = balances;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("bounties");
        if (s != null) for (String k : s.getKeys(false)) {
            try { bounties.put(UUID.fromString(k), s.getDouble(k)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        bounties.forEach((k, v) -> y.set("bounties." + k, v));
        try { plugin.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save bounties.yml: " + e.getMessage()); }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        long per = plugin.getConfig().getLong("shards.per-kill", 10);
        long cooldown = plugin.getConfig().getLong("shards.per-kill-cooldown-minutes", 10) * 60000L;
        String pair = killer.getUniqueId() + ">" + victim.getUniqueId();
        long now = System.currentTimeMillis();
        if (per > 0 && now - lastReward.getOrDefault(pair, 0L) >= cooldown) {
            lastReward.put(pair, now);
            shards.add(killer.getUniqueId(), per);
            killer.sendActionBar(Component.text("+" + per + " shards for killing " + victim.getName(), NamedTextColor.LIGHT_PURPLE));
        }

        Double bounty = bounties.remove(victim.getUniqueId());
        if (bounty != null && bounty > 0) {
            save();
            balances.deposit(killer.getUniqueId(), bounty);
            Bukkit.broadcast(Component.text(killer.getName() + " claimed the " + Money.format(bounty)
                    + " bounty on " + victim.getName() + "!", NamedTextColor.GOLD));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length < 2) {
            List<Map.Entry<UUID, Double>> top = new ArrayList<>(bounties.entrySet());
            top.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            sender.sendMessage(Component.text("Bounties (/bounty <player> <amount> to add):", NamedTextColor.GOLD));
            if (top.isEmpty()) sender.sendMessage(Component.text("  none right now", NamedTextColor.GRAY));
            for (int i = 0; i < Math.min(10, top.size()); i++) {
                sender.sendMessage(Component.text("  " + balances.nameOf(top.get(i).getKey()) + " - "
                        + Money.format(top.get(i).getValue()), NamedTextColor.YELLOW));
            }
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can place bounties."); return true; }
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) { p.sendMessage(Component.text("No player called " + args[0] + ".", NamedTextColor.RED)); return true; }
        if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(Component.text("You can't put a bounty on yourself.", NamedTextColor.RED)); return true; }
        double amount = Money.parse(args[1]);
        double min = plugin.getConfig().getDouble("bounty.minimum", 1000);
        if (amount < min) { p.sendMessage(Component.text("Bounties start at " + Money.format(min) + ".", NamedTextColor.RED)); return true; }
        if (!balances.withdraw(p.getUniqueId(), amount)) { p.sendMessage(Component.text("You can't afford that.", NamedTextColor.RED)); return true; }
        double total = bounties.merge(target.getUniqueId(), Money.round(amount), Double::sum);
        save();
        Bukkit.broadcast(Component.text(p.getName() + " put " + Money.format(amount) + " on " + target.getName()
                + "'s head (now " + Money.format(total) + ").", NamedTextColor.GOLD));
        return true;
    }
}
