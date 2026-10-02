package com.mcgee.smp.tools;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Upgrading eternal diamond gear to netherite keeps it eternal.
 * Vanilla smithing usually carries item data across already; this makes sure
 * of it, whatever the version does.
 */
public final class SmithingListener implements Listener {
    private final Tools tools;

    public SmithingListener(Tools tools) {
        this.tools = tools;
    }

    @EventHandler
    public void onPrepare(PrepareSmithingEvent event) {
        ItemStack base = event.getInventory().getInputEquipment();
        ToolType type = tools.typeOf(base);
        ItemStack result = event.getResult();
        if (type == null || result == null || result.getType().isAir()) return;
        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(tools.key(), PersistentDataType.STRING, type.id);
        if (type.kind == ToolType.Kind.ETERNAL) meta.setUnbreakable(true);
        result.setItemMeta(meta);
        event.setResult(result);
    }
}
