package com.mcgee.smp.social;

import com.mcgee.smp.economy.Balances;
import com.mcgee.smp.economy.Money;
import com.mcgee.smp.shards.Shards;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

/** /leaderboard: richest, most shards, most kills, most playtime. */
public final class Leaderboards implements CommandExecutor, Listener {
    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    private final Balances balances;
    private final Shards shards;

    public Leaderboards(Balances balances, Shards shards) {
        this.balances = balances;
        this.shards = shards;
    }

    private static Component plain(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack board(Material m, String title, List<String> rows) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plain(title, NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        if (rows.isEmpty()) lore.add(plain("Nobody yet", NamedTextColor.GRAY));
        for (int i = 0; i < rows.size(); i++) lore.add(plain((i + 1) + ". " + rows.get(i), i == 0 ? NamedTextColor.YELLOW : NamedTextColor.WHITE));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private static List<String> statTop(ToLongFunction<OfflinePlayer> f, java.util.function.LongFunction<String> fmt) {
        List<OfflinePlayer> all = new ArrayList<>();
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) if (p.getName() != null) all.add(p);
        all.sort((a, b) -> Long.compare(f.applyAsLong(b), f.applyAsLong(a)));
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(10, all.size()); i++) out.add(all.get(i).getName() + " - " + fmt.apply(f.applyAsLong(all.get(i))));
        return out;
    }

    private static long stat(OfflinePlayer p, Statistic s) {
        try { return p.getStatistic(s); } catch (Exception e) { return 0; }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can open the leaderboards."); return true; }
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Component.text("Leaderboards"));
        h.inventory = inv;

        List<String> rich = new ArrayList<>();
        for (Map.Entry<UUID, Double> e : balances.top(10)) rich.add(balances.nameOf(e.getKey()) + " - " + Money.format(e.getValue()));
        List<String> sh = new ArrayList<>();
        for (Map.Entry<UUID, Long> e : shards.top(10)) sh.add(balances.nameOf(e.getKey()) + " - " + e.getValue());

        inv.setItem(10, board(Material.GOLD_INGOT, "Richest", rich));
        inv.setItem(12, board(Material.AMETHYST_SHARD, "Most shards", sh));
        inv.setItem(14, board(Material.IRON_SWORD, "Most kills", statTop(o -> stat(o, Statistic.PLAYER_KILLS), String::valueOf)));
        inv.setItem(16, board(Material.CLOCK, "Most playtime", statTop(o -> stat(o, Statistic.PLAY_ONE_MINUTE),
                t -> (t / 72000) + "h " + (t / 1200 % 60) + "m")));
        p.openInventory(inv);
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
