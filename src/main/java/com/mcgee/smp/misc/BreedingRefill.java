package com.mcgee.smp.misc;

import org.bukkit.GameMode;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Breeding food comes from your whole inventory, not just your hand.
 *
 * The moment you right-click an animal with its breeding food, we top the stack in
 * your hand up to a full stack using the same food from anywhere else in your
 * inventory. This runs BEFORE other plugins (such as a mob stacker) look at the
 * click, so a stacked group that needs lots of food sees a full hand and uses it.
 */
public final class BreedingRefill implements Listener {
    private final JavaPlugin plugin;

    public BreedingRefill(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Animals animal)) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        EquipmentSlot hand = event.getHand();
        PlayerInventory inv = p.getInventory();
        ItemStack held = inv.getItem(hand);
        if (held == null || held.getType().isAir() || !animal.isBreedingItem(held)) return;

        int max = held.getMaxStackSize();
        int need = max - held.getAmount();
        if (need <= 0) return;
        for (int slot = 0; slot < 36 && need > 0; slot++) {
            ItemStack it = inv.getItem(slot);
            if (it == null || it == held || !it.isSimilar(held)) continue;
            int take = Math.min(need, it.getAmount());
            held.setAmount(held.getAmount() + take);
            need -= take;
            if (take >= it.getAmount()) inv.setItem(slot, null);
            else it.setAmount(it.getAmount() - take);
        }
        inv.setItem(hand, held);
    }
}
