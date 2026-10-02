package com.mcgee.smp.tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds shard-shop items and recognises them again later.
 * Items are identified by a hidden data tag, not by their name, so renaming
 * one in an anvil changes nothing and a renamed normal item can't pass as one.
 */
public final class Tools {
    private final JavaPlugin plugin;
    private final NamespacedKey key;

    public Tools(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "tool");
    }

    public NamespacedKey key() {
        return key;
    }

    public int cost(ToolType t) {
        return Math.max(0, plugin.getConfig().getInt("tools." + t.id + ".cost", t.defaultCost));
    }

    private static Component plain(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    /** A real, usable item. */
    public ItemStack create(ToolType t) {
        ItemStack item = new ItemStack(t.material);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = t.kind == ToolType.Kind.ETERNAL ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD;
        meta.displayName(plain(t.display, color));
        List<Component> lore = new ArrayList<>();
        lore.add(plain(t.description(), NamedTextColor.GRAY));
        meta.lore(lore);
        if (t.kind == ToolType.Kind.ETERNAL) meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, t.id);
        item.setItemMeta(meta);
        return item;
    }

    /** A picture of the item for the shop menu. Deliberately NOT tagged, so
     *  even if one somehow left the menu it would be an ordinary item. */
    public ItemStack display(ToolType t, long shardsOwned) {
        ItemStack item = new ItemStack(t.material);
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = t.kind == ToolType.Kind.ETERNAL ? NamedTextColor.LIGHT_PURPLE : NamedTextColor.GOLD;
        meta.displayName(plain(t.display, color));
        int cost = cost(t);
        List<Component> lore = new ArrayList<>();
        lore.add(plain(t.description(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(plain("Cost: " + cost + " shards", NamedTextColor.LIGHT_PURPLE));
        lore.add(shardsOwned >= cost
                ? plain("Click to buy", NamedTextColor.GREEN)
                : plain("You need " + (cost - shardsOwned) + " more shards", NamedTextColor.RED));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public ToolType typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? null : ToolType.byId(id);
    }
}
