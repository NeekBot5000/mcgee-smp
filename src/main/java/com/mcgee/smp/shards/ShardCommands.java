package com.mcgee.smp.shards;

import com.mcgee.smp.economy.Balances;
import com.mcgee.smp.economy.Money;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /shards, /buyshards and the operator-only /shardsadmin. */
public final class ShardCommands implements CommandExecutor, TabCompleter {
    private static final String PINK = "\u00a7d", GREEN = "\u00a7a", RED = "\u00a7c",
            GRAY = "\u00a77", WHITE = "\u00a7f", YELLOW = "\u00a7e";
    private final JavaPlugin plugin;
    private final Shards shards;
    private final Balances balances;

    public ShardCommands(JavaPlugin plugin, Shards shards, Balances balances) {
        this.plugin = plugin;
        this.shards = shards;
        this.balances = balances;
    }

    private double price() {
        return Math.max(0, plugin.getConfig().getDouble("shards.price", 10000));
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return (off.hasPlayedBefore() || balances.hasAccount(off.getUniqueId())) ? off : null;
    }

    private String nameOf(OfflinePlayer p) {
        return p.getName() != null ? p.getName() : balances.nameOf(p.getUniqueId());
    }

    private static String plural(long n) {
        return n == 1 ? "shard" : "shards";
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "shards" -> show(sender, args);
            case "buyshards" -> buy(sender, args);
            case "shardsadmin" -> admin(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void show(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            OfflinePlayer t = resolve(args[0]);
            if (t == null) { sender.sendMessage(RED + "No player called " + args[0] + "."); return; }
            long n = shards.get(t.getUniqueId());
            sender.sendMessage(YELLOW + nameOf(t) + GRAY + " has " + PINK + n + " " + plural(n));
            return;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage(RED + "Usage: /shards <player>"); return; }
        long n = shards.get(p.getUniqueId());
        long every = Math.max(1, plugin.getConfig().getLong("shards.interval-minutes", 5));
        long amount = Math.max(1, plugin.getConfig().getLong("shards.amount", 1));
        sender.sendMessage(GRAY + "Shards: " + PINK + n + GRAY + "  (+" + amount + " every "
                + every + " min online, or /buyshards)");
    }

    private void buy(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(RED + "Only players can buy shards."); return; }
        double each = price();
        if (args.length < 1) {
            sender.sendMessage(GRAY + "Shards cost " + GREEN + Money.format(each) + GRAY + " each. Usage: /buyshards <amount>");
            return;
        }
        long n;
        try { n = Long.parseLong(args[0]); } catch (NumberFormatException e) { n = -1; }
        long max = Math.max(1, plugin.getConfig().getLong("shards.max-buy", 1000));
        if (n < 1 || n > max) { sender.sendMessage(RED + "Pick a whole number from 1 to " + max + "."); return; }

        double total = Money.round(each * n);
        // Shards are expensive, so a typo shouldn't be able to drain someone's
        // balance. The first run only quotes the price.
        if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
            sender.sendMessage(GRAY + n + " " + plural(n) + " cost " + GREEN + Money.format(total)
                    + GRAY + ". You have " + GREEN + Money.format(balances.get(p.getUniqueId())) + GRAY + ".");
            sender.sendMessage(YELLOW + "Type /buyshards " + n + " confirm to buy.");
            return;
        }
        if (!balances.withdraw(p.getUniqueId(), total)) {
            sender.sendMessage(RED + "That costs " + Money.format(total) + " but you have "
                    + Money.format(balances.get(p.getUniqueId())) + ".");
            return;
        }
        shards.add(p.getUniqueId(), n);
        sender.sendMessage(GREEN + "Bought " + PINK + n + " " + plural(n) + GREEN + " for " + WHITE
                + Money.format(total) + GREEN + ". You now have " + PINK + shards.get(p.getUniqueId()) + GREEN + ".");
    }

    private void admin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcgee.admin")) { sender.sendMessage(RED + "Operators only."); return; }
        if (args.length < 3) { sender.sendMessage(RED + "Usage: /shardsadmin <give|take|set> <player> <amount>"); return; }
        OfflinePlayer t = resolve(args[1]);
        if (t == null) { sender.sendMessage(RED + "No player called " + args[1] + "."); return; }
        long n;
        try { n = Long.parseLong(args[2]); } catch (NumberFormatException e) { n = -1; }
        if (n < 0) { sender.sendMessage(RED + "That isn't a valid amount."); return; }
        UUID id = t.getUniqueId();
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> shards.add(id, n);
            case "take" -> { if (!shards.take(id, n)) shards.set(id, 0); }
            case "set" -> shards.set(id, n);
            default -> { sender.sendMessage(RED + "Use give, take or set."); return; }
        }
        sender.sendMessage(GREEN + nameOf(t) + " now has " + PINK + shards.get(id) + " " + plural(shards.get(id)) + GREEN + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("shardsadmin") && args.length == 1) {
            for (String s : List.of("give", "take", "set")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        int playerArg = name.equals("shardsadmin") ? 2 : (name.equals("shards") ? 1 : -1);
        if (args.length == playerArg) {
            String typed = args[playerArg - 1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(typed)) out.add(p.getName());
        }
        return out;
    }
}
