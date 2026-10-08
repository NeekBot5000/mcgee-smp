package com.mcgee.smp.misc;

import org.bukkit.GameMode;
import org.bukkit.Material;
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

import java.util.EnumSet;
import java.util.Set;

/**
 * Breeding food comes from your whole inventory, not just your hand.
 *
 * The moment you right-click an animal while holding one of its breeding foods, we top
 * the stack in your hand up to a full stack using the same food from anywhere else in
 * your inventory. This runs BEFORE other plugins (such as a mob stacker) look at the
 * click, so a stacked group that needs lots of food sees a full hand and uses it.
 */
public final class BreedingRefill implements Listener {
    /** Every food any breedable animal accepts. Names that don't exist on this version are skipped. */
    private static final Set<Material> FOODS = build(
            "WHEAT", "CARROT", "GOLDEN_CARROT", "POTATO", "BEETROOT", "WHEAT_SEEDS", "MELON_SEEDS",
            "PUMPKIN_SEEDS", "BEETROOT_SEEDS", "TORCHFLOWER_SEEDS", "PITCHER_POD", "HAY_BLOCK",
            "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE", "APPLE", "SWEET_BERRIES", "GLOW_BERRIES",
            "SEAGRASS", "KELP", "DRIED_KELP", "DANDELION", "COD", "SALMON", "TROPICAL_FISH", "PUFFERFISH",
            "BEEF", "COOKED_BEEF", "PORKCHOP", "COOKED_PORKCHOP", "CHICKEN", "COOKED_CHICKEN",
            "MUTTON", "COOKED_MUTTON", "RABBIT", "COOKED_RABBIT", "ROTTEN_FLESH", "SPIDER_EYE",
            "BAMBOO", "CACTUS", "BROWN_MUSHROOM", "RED_MUSHROOM", "SLIME_BALL", "SUGAR_CANE",
            "WARPED_FUNGUS", "CRIMSON_FUNGUS", "LILY_PAD", "BONE", "HONEY_BOTTLE", "COCOA_BEANS");

    private final JavaPlugin plugin;

    public BreedingRefill(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private static Set<Material> build(String... names) {
        Set<Material> out = EnumSet.noneOf(Material.class);
        for (String n : names) {
            Material m = Material.matchMaterial(n);
            if (m != null) out.add(m);
        }
        return out;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onFeed(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Animals)) return;
        Player p = event.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE) return;
        EquipmentSlot hand = event.getHand();
        PlayerInventory inv = p.getInventory();
        ItemStack held = inv.getItem(hand);
        if (held == null || held.getType().isAir() || !FOODS.contains(held.getType())) return;

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
