package com.mcgee.smp.hud;

import com.mcgee.smp.economy.Balances;
import com.mcgee.smp.economy.Money;
import com.mcgee.smp.shards.Shards;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A sidebar that shows each player THEIR OWN stats (task doc #5).
 * Bedrock couldn't do this; Java can, because every player gets their own
 * scoreboard. Lines are updated through team prefixes, so nothing flickers.
 * /sb hides or shows it.
 */
public final class PersonalBoard implements Listener, CommandExecutor {
    private static final int LINES = 7;

    private final JavaPlugin plugin;
    private final Balances balances;
    private final Shards shards;
    private final NamespacedKey hiddenKey;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public PersonalBoard(JavaPlugin plugin, Balances balances, Shards shards) {
        this.plugin = plugin;
        this.balances = balances;
        this.shards = shards;
        this.hiddenKey = new NamespacedKey(plugin, "sidebar_hidden");
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 40L);
    }

    private boolean hidden(Player p) {
        return p.getPersistentDataContainer().has(hiddenKey, PersistentDataType.BYTE);
    }

    private void show(Player p) {
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = sb.registerNewObjective("mcgee", Criteria.DUMMY,
                Component.text("McGee SMP", NamedTextColor.GOLD, TextDecoration.BOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        obj.numberFormat(NumberFormat.blank());   // hide the red numbers on the right
        for (int i = 0; i < LINES; i++) {
            String entry = "\u00a7" + Integer.toHexString(i) + "\u00a7r";
            Team t = sb.registerNewTeam("line" + i);
            t.addEntry(entry);
            obj.getScore(entry).setScore(LINES - i);
        }
        boards.put(p.getUniqueId(), sb);
        p.setScoreboard(sb);
        update(p, sb);
    }

    private static Component line(String label, String value, NamedTextColor color) {
        return Component.text(label + " ", NamedTextColor.GRAY).append(Component.text(value, color));
    }

    private void update(Player p, Scoreboard sb) {
        UUID id = p.getUniqueId();
        long minutes = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200L;   // the stat counts ticks
        Component[] lines = {
                Component.empty(),
                line("Money", Money.format(balances.get(id)), NamedTextColor.GREEN),
                line("Shards", String.valueOf(shards.get(id)), NamedTextColor.LIGHT_PURPLE),
                line("Kills", String.valueOf(p.getStatistic(Statistic.PLAYER_KILLS)), NamedTextColor.WHITE),
                line("Deaths", String.valueOf(p.getStatistic(Statistic.DEATHS)), NamedTextColor.WHITE),
                line("Playtime", (minutes / 60) + "h " + (minutes % 60) + "m", NamedTextColor.WHITE),
                Component.empty(),
        };
        for (int i = 0; i < LINES; i++) {
            Team t = sb.getTeam("line" + i);
            if (t != null) t.prefix(lines[i]);
        }
    }

    private void updateAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Scoreboard sb = boards.get(p.getUniqueId());
            if (sb != null && p.getScoreboard() == sb) update(p, sb);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!hidden(e.getPlayer())) show(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players have a sidebar."); return true; }
        if (hidden(p)) {
            p.getPersistentDataContainer().remove(hiddenKey);
            show(p);
            p.sendMessage(Component.text("Sidebar on.", NamedTextColor.GREEN));
        } else {
            p.getPersistentDataContainer().set(hiddenKey, PersistentDataType.BYTE, (byte) 1);
            boards.remove(p.getUniqueId());
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            p.sendMessage(Component.text("Sidebar off. Type /sb to bring it back.", NamedTextColor.GRAY));
        }
        return true;
    }
}
