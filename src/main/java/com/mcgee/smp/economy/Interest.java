package com.mcgee.smp.economy;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Compound interest. Every interval each eligible balance earns rate% and the
 * interest is added to the balance, so the next payout is on the bigger number.
 *
 * Guards so it can't spiral:
 *  - only the first max-balance dollars earn (past that, growth is flat),
 *  - a hard cap on any single payout,
 *  - balances under min-balance earn nothing,
 *  - by default only players who are online earn (no parking money offline).
 * Money held in orders, bounties or auction listings isn't in the balance, so
 * it earns nothing while it sits there.
 */
public final class Interest implements CommandExecutor {
    private final JavaPlugin plugin;
    private final Balances balances;
    private final Map<UUID, Double> earnedSinceStart = new ConcurrentHashMap<>();
    private volatile long nextPayoutMillis;

    public Interest(JavaPlugin plugin, Balances balances) {
        this.plugin = plugin;
        this.balances = balances;
    }

    private boolean enabled() { return plugin.getConfig().getBoolean("economy.interest.enabled", true); }
    private double ratePercent() { return Math.max(0, plugin.getConfig().getDouble("economy.interest.rate-percent", 0.5)); }
    private long intervalMinutes() { return Math.max(1, plugin.getConfig().getLong("economy.interest.interval-minutes", 60)); }
    private double minBalance() { return Math.max(0, plugin.getConfig().getDouble("economy.interest.min-balance", 100)); }
    private double maxBalance() { return Math.max(0, plugin.getConfig().getDouble("economy.interest.max-balance", 1_000_000)); }
    private double maxPayout() { return Math.max(0, plugin.getConfig().getDouble("economy.interest.max-payout", 5000)); }
    private boolean onlineOnly() { return plugin.getConfig().getBoolean("economy.interest.online-only", true); }

    /** Interest a balance would earn on the next payout. */
    public double payoutFor(double balance) {
        if (!enabled() || balance < minBalance()) return 0;
        double eligible = Math.min(balance, maxBalance());
        return Money.round(Math.min(eligible * ratePercent() / 100.0, maxPayout()));
    }

    public void start() {
        if (!enabled()) {
            plugin.getLogger().info("Compound interest: off.");
            return;
        }
        long ticks = intervalMinutes() * 60L * 20L;
        nextPayoutMillis = System.currentTimeMillis() + intervalMinutes() * 60_000L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::payout, ticks, ticks);
        plugin.getLogger().info("Compound interest: " + ratePercent() + "% every " + intervalMinutes()
                + " min (balance " + Money.format(minBalance()) + " to " + Money.format(maxBalance())
                + " earns, max " + Money.format(maxPayout()) + " per payout, "
                + (onlineOnly() ? "online players only" : "everyone") + ").");
    }

    private void payout() {
        nextPayoutMillis = System.currentTimeMillis() + intervalMinutes() * 60_000L;
        double created = 0;
        int count = 0;
        if (onlineOnly()) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                double paid = pay(p.getUniqueId());
                if (paid > 0) {
                    created += paid;
                    count++;
                    p.sendMessage(Component.text("Interest: +" + Money.format(paid) + "  (balance "
                            + Money.format(balances.get(p.getUniqueId())) + ")", NamedTextColor.GREEN));
                }
            }
        } else {
            for (Map.Entry<UUID, Double> entry : balances.top(Integer.MAX_VALUE)) {
                UUID id = entry.getKey();
                double paid = pay(id);
                if (paid > 0) {
                    created += paid;
                    count++;
                    Player p = plugin.getServer().getPlayer(id);
                    if (p != null) {
                        p.sendMessage(Component.text("Interest: +" + Money.format(paid) + "  (balance "
                                + Money.format(balances.get(id)) + ")", NamedTextColor.GREEN));
                    }
                }
            }
        }
        if (count > 0) {
            plugin.getLogger().info("Interest paid: " + Money.format(created) + " created across " + count + " players.");
        }
    }

    private double pay(UUID id) {
        double amount = payoutFor(balances.get(id));
        if (amount <= 0) return 0;
        balances.deposit(id, amount);
        earnedSinceStart.merge(id, amount, Double::sum);
        return amount;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!enabled()) {
            sender.sendMessage("Compound interest: off.");
            return true;
        }
        sender.sendMessage("Compound interest: " + ratePercent() + "% every " + intervalMinutes() + " min, added to your balance so it compounds.");
        sender.sendMessage("Balances under " + Money.format(minBalance()) + " earn nothing; only the first "
                + Money.format(maxBalance()) + " earns; max " + Money.format(maxPayout()) + " per payout"
                + (onlineOnly() ? "; you must be online." : "."));
        if (sender instanceof Player p) {
            long left = Math.max(0, nextPayoutMillis - System.currentTimeMillis()) / 1000;
            double next = payoutFor(balances.get(p.getUniqueId()));
            sender.sendMessage("Next payout in " + (left / 60) + "m " + (left % 60) + "s: about " + Money.format(next)
                    + ". Earned since the server started: " + Money.format(earnedSinceStart.getOrDefault(p.getUniqueId(), 0.0)) + ".");
        }
        return true;
    }
}
