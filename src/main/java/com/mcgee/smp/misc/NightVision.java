package com.mcgee.smp.misc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * /nv - permanent night vision you can switch on and off.
 * The choice is stored on the player, so it survives logging out, dying and
 * drinking milk.
 */
public final class NightVision implements CommandExecutor, Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey key;

    public NightVision(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "nightvision");
        // Cheap safety net for anything else that clears effects.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::reapplyAll, 200L, 200L);
    }

    private boolean enabled(Player p) {
        return p.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    private static void apply(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION, 0, false, false, false));
    }

    private void applyLater(Player p) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && enabled(p)) apply(p);
        }, 2L);
    }

    private void reapplyAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (enabled(p) && !p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) apply(p);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can use /nv."); return true; }
        if (enabled(p)) {
            p.getPersistentDataContainer().remove(key);
            p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            p.sendMessage(Component.text("Night vision off.", NamedTextColor.GRAY));
        } else {
            p.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            apply(p);
            p.sendMessage(Component.text("Night vision on. Type /nv again to turn it off.", NamedTextColor.GREEN));
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { applyLater(e.getPlayer()); }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) { applyLater(e.getPlayer()); }

    @EventHandler
    public void onDrink(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() == Material.MILK_BUCKET) applyLater(e.getPlayer());
    }
}
