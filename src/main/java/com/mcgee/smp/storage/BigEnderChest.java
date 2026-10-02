package com.mcgee.smp.storage;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * Bigger ender chests (task doc #22).
 *
 * The first 3 rows ARE the normal vanilla ender chest, read and written back
 * every time. Only the extra rows live in our own file
 * (plugins/McGeeSMP/enderchests/<uuid>.yml). So if this plugin is ever
 * removed, everything in the normal 27 slots is exactly where it always was,
 * and the extra rows are still recoverable from the file.
 */
public final class BigEnderChest implements Listener {
    private static final int VANILLA = 27;

    private static final class Holder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final JavaPlugin plugin;
    private final File dir;

    public BigEnderChest(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "enderchests");
    }

    private int size() {
        int rows = Math.min(6, Math.max(3, plugin.getConfig().getInt("enderchest.rows", 6)));
        return rows * 9;
    }

    @EventHandler(ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (size() <= VANILLA) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory inv = e.getInventory();
        if (inv.getType() != InventoryType.ENDER_CHEST) return;
        // Only your OWN ender chest. An admin tool showing someone else's is left alone.
        boolean own = inv.equals(p.getEnderChest())
                || (inv.getHolder() instanceof HumanEntity h && h.getUniqueId().equals(p.getUniqueId()));
        if (!own) return;
        e.setCancelled(true);
        // Opening a new inventory inside an open event isn't allowed; do it next tick.
        Bukkit.getScheduler().runTask(plugin, () -> openBig(p));
    }

    private void openBig(Player p) {
        if (!p.isOnline()) return;
        int size = size();
        Holder holder = new Holder();
        Inventory big = Bukkit.createInventory(holder, size, Component.text("Ender Chest"));
        holder.inventory = big;
        ItemStack[] vanilla = p.getEnderChest().getContents();
        for (int i = 0; i < Math.min(vanilla.length, VANILLA); i++) big.setItem(i, vanilla[i]);
        ItemStack[] extra = loadExtra(p.getUniqueId(), size - VANILLA);
        for (int i = 0; i < extra.length; i++) big.setItem(VANILLA + i, extra[i]);
        p.openInventory(big);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1f);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory big = e.getInventory();
        ItemStack[] first = new ItemStack[VANILLA];
        for (int i = 0; i < VANILLA; i++) first[i] = big.getItem(i);
        p.getEnderChest().setContents(first);
        ItemStack[] extra = new ItemStack[big.getSize() - VANILLA];
        for (int i = 0; i < extra.length; i++) extra[i] = big.getItem(VANILLA + i);
        saveExtra(p.getUniqueId(), extra);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_CLOSE, 0.5f, 1f);
    }

    /** Called on shutdown so nobody's open chest is lost. */
    public void closeAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) p.closeInventory();
        }
    }

    private File fileOf(UUID id) {
        return new File(dir, id + ".yml");
    }

    private ItemStack[] loadExtra(UUID id, int count) {
        ItemStack[] out = new ItemStack[Math.max(0, count)];
        File f = fileOf(id);
        if (!f.exists()) return out;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(f).getConfigurationSection("slots");
        if (s == null) return out;
        for (String key : s.getKeys(false)) {
            try {
                int i = Integer.parseInt(key);
                if (i >= 0 && i < out.length) out[i] = s.getItemStack(key);
            } catch (NumberFormatException ignored) { }
        }
        return out;
    }

    private void saveExtra(UUID id, ItemStack[] extra) {
        File f = fileOf(id);
        YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        // Only overwrite the slots that were on screen. If the size was lowered
        // in the config, items in rows no longer shown are kept, not deleted.
        for (int i = 0; i < extra.length; i++) {
            ItemStack it = extra[i];
            y.set("slots." + i, (it == null || it.getType().isAir()) ? null : it);
        }
        try {
            dir.mkdirs();
            y.save(f);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save ender chest for " + id + ": " + ex.getMessage());
        }
    }
}
