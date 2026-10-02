package com.mcgee.smp.misc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.ArrayList;

/**
 * Maps don't work (task doc #30): empty maps can't be filled in, maps can't be
 * crafted, and any map that does exist draws nothing.
 */
public final class MapBlocker implements Listener {

    // Not ignoreCancelled: Bukkit marks right-clicking the air as cancelled by
    // default, and that's exactly the click that fills in a map.
    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it == null || it.getType() != Material.MAP) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Component.text("Maps are turned off on this server.", NamedTextColor.RED));
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack r = e.getInventory().getResult();
        if (r != null && (r.getType() == Material.MAP || r.getType() == Material.FILLED_MAP)) {
            e.getInventory().setResult(null);
        }
    }

    @EventHandler
    public void onMapInit(MapInitializeEvent e) {
        MapView view = e.getMap();
        for (MapRenderer r : new ArrayList<>(view.getRenderers())) view.removeRenderer(r);
    }
}
