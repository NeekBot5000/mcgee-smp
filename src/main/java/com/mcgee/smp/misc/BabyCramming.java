package com.mcgee.smp.misc;

import org.bukkit.World;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Baby mobs have tiny hitboxes, so dozens fit in one block and dodge the normal
 * entity-cramming limit (the usual lag/farm trick). This counts every baby as if
 * it were an adult: if more than the limit are packed into an adult-sized space,
 * the babies take cramming damage, just like adults would.
 */
public final class BabyCramming {
    private final JavaPlugin plugin;

    public BabyCramming(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfig().getBoolean("cramming.babies-count-as-adults", true)) {
            plugin.getLogger().info("Baby cramming protection: off.");
            return;
        }
        int limit = Math.max(2, plugin.getConfig().getInt("cramming.max-entities", 24));
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> sweep(limit), 40L, 20L);
        plugin.getLogger().info("Baby cramming protection: babies count as adults (limit " + limit + " per block).");
    }

    private void sweep(int limit) {
        for (World w : plugin.getServer().getWorlds()) {
            for (Ageable a : w.getEntitiesByClass(Ageable.class)) {
                if (a.isAdult() || a.isDead() || !a.isValid()) continue;
                // Adult-sized box around the baby (half-extents).
                List<Entity> near = a.getNearbyEntities(0.3, 0.5, 0.3);
                int crowd = 1;
                for (Entity e : near) {
                    if (e instanceof LivingEntity && !(e instanceof ArmorStand)) crowd++;
                }
                if (crowd > limit) a.damage(6.0);
            }
        }
    }
}
