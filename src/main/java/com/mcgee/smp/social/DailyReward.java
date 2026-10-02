package com.mcgee.smp.social;

import com.mcgee.smp.economy.Balances;
import com.mcgee.smp.economy.Money;
import com.mcgee.smp.shards.Shards;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** /daily: some money and shards once a day. */
public final class DailyReward implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Balances balances;
    private final Shards shards;
    private final NamespacedKey key;

    public DailyReward(JavaPlugin plugin, Balances balances, Shards shards) {
        this.plugin = plugin;
        this.balances = balances;
        this.shards = shards;
        this.key = new NamespacedKey(plugin, "daily_last");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can claim a daily reward."); return true; }
        long gap = plugin.getConfig().getLong("daily.hours", 20) * 3600000L;
        Long last = p.getPersistentDataContainer().get(key, PersistentDataType.LONG);
        long now = System.currentTimeMillis();
        if (last != null && now - last < gap) {
            long mins = (gap - (now - last)) / 60000;
            p.sendMessage(Component.text("Come back in " + (mins / 60) + "h " + (mins % 60) + "m.", NamedTextColor.YELLOW));
            return true;
        }
        double money = plugin.getConfig().getDouble("daily.money", 1000);
        long sh = plugin.getConfig().getLong("daily.shards", 2);
        p.getPersistentDataContainer().set(key, PersistentDataType.LONG, now);
        balances.deposit(p.getUniqueId(), money);
        shards.add(p.getUniqueId(), sh);
        p.sendMessage(Component.text("Daily reward: " + Money.format(money) + " and " + sh + " shards.", NamedTextColor.GREEN));
        return true;
    }
}
