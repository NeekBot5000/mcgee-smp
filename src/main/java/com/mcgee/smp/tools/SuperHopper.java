package com.mcgee.smp.tools;

import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.block.TileState;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Set;

/**
 * Super Hopper: a normal hopper, tagged. A hopper moves one item every 8 ticks; each
 * time a Super Hopper does a transfer we move 9 more straight away, so it shifts
 * 10 items per cycle (10x). The tag lives on the placed block, so it survives
 * restarts, and breaking it drops the Super Hopper item again.
 */
public final class SuperHopper implements Listener {
    /** Only boost between plain storage; furnaces, brewing stands etc. have special slots. */
    private static final Set<Material> STORAGE = EnumSet.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.HOPPER,
            Material.DROPPER, Material.DISPENSER);

    private static final int EXTRA_PER_CYCLE = 9;   // + the 1 vanilla moves = 10x

    private final JavaPlugin plugin;
    private final Tools tools;

    public SuperHopper(JavaPlugin plugin, Tools tools) {
        this.plugin = plugin;
        this.tools = tools;
    }

    private boolean isSuper(BlockState state) {
        return state instanceof TileState ts
                && ts.getPersistentDataContainer().has(tools.key(), PersistentDataType.STRING)
                && ToolType.SUPER_HOPPER.id.equals(
                        ts.getPersistentDataContainer().get(tools.key(), PersistentDataType.STRING));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (tools.typeOf(event.getItemInHand()) != ToolType.SUPER_HOPPER) return;
        BlockState state = event.getBlockPlaced().getState();
        if (!(state instanceof TileState ts)) return;
        ts.getPersistentDataContainer().set(tools.key(), PersistentDataType.STRING, ToolType.SUPER_HOPPER.id);
        ts.update();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent event) {
        if (!isSuper(event.getBlockState())) return;
        for (Item drop : event.getItems()) {
            ItemStack stack = drop.getItemStack();
            if (stack.getType() == Material.HOPPER && stack.getAmount() == 1
                    && tools.typeOf(stack) == null) {
                drop.setItemStack(tools.create(ToolType.SUPER_HOPPER));
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        InventoryHolder starter = event.getInitiator().getHolder();
        if (!(starter instanceof Hopper hopper) || !isSuper(hopper)) return;
        Inventory from = event.getSource();
        Inventory to = event.getDestination();
        if (!STORAGE.contains(typeOf(from)) || !STORAGE.contains(typeOf(to))) return;
        // Run after the vanilla single move has finished.
        plugin.getServer().getScheduler().runTask(plugin, () -> extra(from, to));
    }

    private static Material typeOf(Inventory inv) {
        InventoryHolder h = inv.getHolder();
        if (h instanceof org.bukkit.block.BlockState bs) return bs.getType();
        if (h instanceof org.bukkit.block.DoubleChest dc && dc.getLeftSide() instanceof org.bukkit.block.BlockState l) return l.getType();
        return Material.AIR;
    }

    private void extra(Inventory from, Inventory to) {
        for (int i = 0; i < EXTRA_PER_CYCLE; i++) {
            ItemStack source = null;
            int slot = -1;
            for (int s = 0; s < from.getSize(); s++) {
                ItemStack it = from.getItem(s);
                if (it != null && !it.getType().isAir()) { source = it; slot = s; break; }
            }
            if (source == null) return;
            ItemStack one = source.clone();
            one.setAmount(1);
            if (!to.addItem(one).isEmpty()) return;   // destination full for this item
            if (source.getAmount() <= 1) from.setItem(slot, null);
            else { source.setAmount(source.getAmount() - 1); from.setItem(slot, source); }
        }
    }
}
