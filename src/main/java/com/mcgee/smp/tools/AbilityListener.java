package com.mcgee.smp.tools;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Tree Breaker, Excavator and Harvester.
 *
 * Every extra block is broken through a normal BlockBreakEvent first, so any
 * protection plugin can still say no to it, and drops honour Fortune and Silk
 * Touch because the block is broken with the real tool.
 */
public final class AbilityListener implements Listener {
    private static final int MAX_LOGS = 128;
    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);

    private final Tools tools;
    /** Players whose extra blocks are being broken right now, so the events we
     *  fire for those blocks don't set the ability off again. */
    private final Set<UUID> busy = new HashSet<>();

    public AbilityListener(Tools tools) {
        this.tools = tools;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (busy.contains(p.getUniqueId())) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        ToolType type = tools.typeOf(tool);
        if (type == null || type.kind != ToolType.Kind.ABILITY) return;

        Block origin = event.getBlock();
        busy.add(p.getUniqueId());
        try {
            switch (type) {
                case TREE_AXE -> { if (!p.isSneaking() && Tag.LOGS.isTagged(origin.getType())) fellTree(p, origin); }
                case EXCAVATOR -> { if (!p.isSneaking()) excavate(p, origin); }
                case HARVESTER -> harvest(p, origin, event);
                default -> { }
            }
        } finally {
            busy.remove(p.getUniqueId());
        }
    }

    /** Break one extra block as if the player had, honouring protections. */
    private boolean breakAs(Player p, Block b) {
        BlockBreakEvent check = new BlockBreakEvent(b, p);
        Bukkit.getPluginManager().callEvent(check);
        if (check.isCancelled()) return false;
        ItemStack tool = p.getInventory().getItemInMainHand();
        if (tool.getType().isAir()) return false;
        b.breakNaturally(tool);
        p.damageItemStack(EquipmentSlot.HAND, 1);
        return true;
    }

    private boolean toolGone(Player p) {
        return p.getInventory().getItemInMainHand().getType().isAir();
    }

    private void fellTree(Player p, Block origin) {
        Deque<Block> queue = new ArrayDeque<>();
        Set<Block> seen = new HashSet<>();
        seen.add(origin);
        queue.add(origin);
        List<Block> logs = new ArrayList<>();
        while (!queue.isEmpty() && logs.size() < MAX_LOGS) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                Block n = b.getRelative(dx, dy, dz);
                if (seen.add(n) && Tag.LOGS.isTagged(n.getType())) {
                    logs.add(n);
                    queue.add(n);
                }
            }
        }
        for (Block log : logs) {
            if (toolGone(p)) break;
            breakAs(p, log);
        }
    }

    private void excavate(Player p, Block origin) {
        BlockFace face = p.getTargetBlockFace(6);
        if (face == null) face = BlockFace.UP;
        ItemStack tool = p.getInventory().getItemInMainHand();
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                Block n = switch (face) {
                    case UP, DOWN -> origin.getRelative(a, 0, b);
                    case NORTH, SOUTH -> origin.getRelative(a, b, 0);
                    default -> origin.getRelative(0, a, b);
                };
                if (toolGone(p)) return;
                if (n.getType().isAir() || n.getType().getHardness() < 0) continue;   // bedrock etc.
                if (n.getState() instanceof Container) continue;                    // never spill a chest
                if (!n.isPreferredTool(tool)) continue;                             // pickaxe blocks only
                breakAs(p, n);
            }
        }
    }

    private static boolean ripe(Block b) {
        return CROPS.contains(b.getType())
                && b.getBlockData() instanceof Ageable age
                && age.getAge() == age.getMaximumAge();
    }

    private void harvest(Player p, Block origin, BlockBreakEvent event) {
        if (!ripe(origin)) return;   // unripe crops break normally
        event.setCancelled(true);    // we harvest and replant the centre ourselves
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = origin.getRelative(dx, 0, dz);
                if (!ripe(b)) continue;
                if (toolGone(p)) return;
                Material crop = b.getType();
                if (b.equals(origin)) {
                    // The centre already passed protection checks in the real event.
                    b.breakNaturally(p.getInventory().getItemInMainHand());
                    p.damageItemStack(EquipmentSlot.HAND, 1);
                } else if (!breakAs(p, b)) {
                    continue;
                }
                b.setType(crop);   // replanted at age 0
            }
        }
    }
}
