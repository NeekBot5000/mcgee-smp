package com.mcgee.smp.economy;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** /bal, /pay, /baltop and the operator-only /eco. */
public final class EconomyCommands implements CommandExecutor, TabCompleter {
    private static final String GREEN = "\u00a7a", YELLOW = "\u00a7e", RED = "\u00a7c", GRAY = "\u00a77", WHITE = "\u00a7f", GOLD = "\u00a76";
    private final Balances balances;

    public EconomyCommands(Balances balances) {
        this.balances = balances;
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return (off.hasPlayedBefore() || balances.hasAccount(off.getUniqueId())) ? off : null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "balance" -> balance(sender, args);
            case "pay" -> pay(sender, args);
            case "baltop" -> baltop(sender);
            case "eco" -> eco(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void balance(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            OfflinePlayer target = resolve(args[0]);
            if (target == null) { sender.sendMessage(RED + "No player called " + args[0] + "."); return; }
            sender.sendMessage(GOLD + nameOf(target) + GRAY + " has " + GREEN + Money.format(balances.get(target.getUniqueId())));
            return;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage(RED + "Usage: /bal <player>"); return; }
        sender.sendMessage(GRAY + "Balance: " + GREEN + Money.format(balances.get(p.getUniqueId())));
    }

    private void pay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(RED + "Only players can pay."); return; }
        if (args.length < 2) { sender.sendMessage(RED + "Usage: /pay <player> <amount>"); return; }
        OfflinePlayer target = resolve(args[0]);
        if (target == null) { sender.sendMessage(RED + "No player called " + args[0] + "."); return; }
        if (target.getUniqueId().equals(p.getUniqueId())) { sender.sendMessage(RED + "You can't pay yourself."); return; }
        double amount = Money.parse(args[1]);
        if (amount <= 0) { sender.sendMessage(RED + "That isn't a valid amount. Try 500, 2.5k or 1m."); return; }
        balances.create(target.getUniqueId(), target.getName(), balances.startingBalance());
        if (!balances.transfer(p.getUniqueId(), target.getUniqueId(), amount)) {
            sender.sendMessage(RED + "You only have " + Money.format(balances.get(p.getUniqueId())) + ".");
            return;
        }
        sender.sendMessage(GREEN + "Sent " + WHITE + Money.format(amount) + GREEN + " to " + WHITE + nameOf(target) + GREEN + ".");
        Player online = target.getPlayer();
        if (online != null) online.sendMessage(GREEN + "You received " + WHITE + Money.format(amount) + GREEN + " from " + WHITE + p.getName() + GREEN + ".");
    }

    private void baltop(CommandSender sender) {
        sender.sendMessage(GOLD + "\u00a7lRichest players");
        int rank = 1;
        for (Map.Entry<UUID, Double> e : balances.top(10)) {
            sender.sendMessage(YELLOW + "#" + rank++ + " " + WHITE + balances.nameOf(e.getKey()) + GRAY + " - " + GREEN + Money.format(e.getValue()));
        }
        if (rank == 1) sender.sendMessage(GRAY + "Nobody has any money yet.");
    }

    private void eco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mcgee.admin")) { sender.sendMessage(RED + "Operators only."); return; }
        if (args.length < 3) { sender.sendMessage(RED + "Usage: /eco <give|take|set> <player> <amount>"); return; }
        OfflinePlayer target = resolve(args[1]);
        if (target == null) { sender.sendMessage(RED + "No player called " + args[1] + "."); return; }
        double amount = Money.parse(args[2]);
        if (amount < 0) { sender.sendMessage(RED + "That isn't a valid amount."); return; }
        UUID id = target.getUniqueId();
        balances.create(id, target.getName(), balances.startingBalance());
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> balances.deposit(id, amount);
            case "take" -> { if (!balances.withdraw(id, amount)) balances.set(id, 0); }
            case "set" -> balances.set(id, amount);
            default -> { sender.sendMessage(RED + "Use give, take or set."); return; }
        }
        sender.sendMessage(GREEN + nameOf(target) + " now has " + WHITE + Money.format(balances.get(id)) + GREEN + ".");
    }

    private String nameOf(OfflinePlayer p) {
        return p.getName() != null ? p.getName() : balances.nameOf(p.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("eco") && args.length == 1) {
            for (String s : List.of("give", "take", "set")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        int playerArg = name.equals("eco") ? 2 : 1;
        if (args.length == playerArg) {
            String typed = args[playerArg - 1].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers()) if (p.getName().toLowerCase(Locale.ROOT).startsWith(typed)) out.add(p.getName());
        }
        return out;
    }
}
