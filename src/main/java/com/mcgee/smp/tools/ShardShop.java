package com.mcgee.smp.tools;

import com.mcgee.smp.shards.Shards;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** /shardshop (alias /tools): a chest menu for spending shards. */
public final class ShardShop implements Listener, CommandExecutor {
    /** Marks our menu so clicks in it can be told apart from real chests. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        private final Map<Integer, ToolType> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int[] ETERNAL_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};
    private static final int[] ABILITY_SLOTS = {29, 31, 33};

    private final JavaPlugin plugin;
    private final Tools tools;
    private final Shards shards;

    public ShardShop(JavaPlugin plugin, Tools tools, Shards shards) {
        this.plugin = plugin;
        this.tools = tools;
        this.shards = shards;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can open the shard shop.");
            return true;
        }
        open(p);
        return true;
    }

    public void open(Player p) {
        long owned = shards.get(p.getUniqueId());
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 45,
                Component.text("Shard Shop - " + owned + " shards", NamedTextColor.DARK_PURPLE));
        holder.inventory = inv;

        ItemStack info = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(Component.text("You have " + owned + " shards", NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Top row: eternal gear, never breaks", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Bottom row: ability tools", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Earn shards by playing, or /buyshards", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        info.setItemMeta(meta);
        inv.setItem(4, info);

        int e = 0, a = 0;
        for (ToolType t : ToolType.values()) {
            int slot;
            if (t.kind == ToolType.Kind.ETERNAL && e < ETERNAL_SLOTS.length) slot = ETERNAL_SLOTS[e++];
            else if (t.kind == ToolType.Kind.ABILITY && a < ABILITY_SLOTS.length) slot = ABILITY_SLOTS[a++];
            else continue;
            inv.setItem(slot, tools.display(t, owned));
            holder.slots.put(slot, t);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        // Nothing moves in or out of this menu, whichever side was clicked.
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ToolType t = holder.slots.get(event.getRawSlot());
        if (t == null) return;
        buy(p, t);
        // Reopen next tick so the shard count in the title updates.
        Bukkit.getScheduler().runTask(plugin, () -> open(p));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private void buy(Player p, ToolType t) {
        int cost = tools.cost(t);
        if (!shards.take(p.getUniqueId(), cost)) {
            p.sendMessage(Component.text("You need " + (cost - shards.get(p.getUniqueId()))
                    + " more shards for the " + t.display + ".", NamedTextColor.RED));
            return;
        }
        ItemStack item = tools.create(t);
        // A full inventory drops the item at the player's feet rather than losing it.
        p.getInventory().addItem(item).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        p.sendMessage(Component.text("Bought the " + t.display + " for " + cost + " shards. "
                + shards.get(p.getUniqueId()) + " left.", NamedTextColor.GREEN));
    }
}
