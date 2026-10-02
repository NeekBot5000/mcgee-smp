package com.mcgee.smp.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /rtp [overworld|nether] - random teleport.
 *
 * Per the task doc: real random destinations, a cooldown that is actually
 * enforced, and no End. The chunk is loaded in the background and checked for
 * solid, dry ground before the player is moved, so nobody lands in lava, the
 * ocean or inside a wall.
 */
public final class RandomTeleport implements CommandExecutor, TabCompleter {
    private static final Set<Material> UNSAFE = Set.of(
            Material.LAVA, Material.WATER, Material.MAGMA_BLOCK, Material.CACTUS, Material.FIRE,
            Material.SOUL_FIRE, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW,
            Material.SWEET_BERRY_BUSH, Material.POINTED_DRIPSTONE);

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Set<UUID> searching = new HashSet<>();

    public RandomTeleport(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can use /rtp."); return true; }
        String which = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "overworld";
        if (which.startsWith("e")) {
            p.sendMessage(Component.text("Random teleport to the End is turned off.", NamedTextColor.RED));
            return true;
        }
        World world = find(which.startsWith("n") ? World.Environment.NETHER : World.Environment.NORMAL);
        if (world == null) { p.sendMessage(Component.text("That dimension isn't available.", NamedTextColor.RED)); return true; }

        long cooldownMs = Math.max(0, plugin.getConfig().getLong("rtp.cooldown-seconds", 60)) * 1000L;
        long since = System.currentTimeMillis() - lastUse.getOrDefault(p.getUniqueId(), 0L);
        if (since < cooldownMs && !p.hasPermission("mcgee.rtp.bypass")) {
            long wait = (cooldownMs - since + 999) / 1000;
            p.sendMessage(Component.text("You can /rtp again in " + wait + "s.", NamedTextColor.YELLOW));
            return true;
        }
        if (!searching.add(p.getUniqueId())) {
            p.sendMessage(Component.text("Already looking for a spot...", NamedTextColor.YELLOW));
            return true;
        }
        p.sendActionBar(Component.text("Finding a safe spot...", NamedTextColor.YELLOW));
        attempt(p, world, 0);
        return true;
    }

    private static World find(World.Environment env) {
        for (World w : Bukkit.getWorlds()) if (w.getEnvironment() == env) return w;
        return null;
    }

    private void attempt(Player p, World w, int tries) {
        int maxTries = Math.max(1, plugin.getConfig().getInt("rtp.attempts", 10));
        if (!p.isOnline()) { searching.remove(p.getUniqueId()); return; }
        if (tries >= maxTries) {
            searching.remove(p.getUniqueId());
            p.sendMessage(Component.text("Couldn't find anywhere safe. Try again.", NamedTextColor.RED));
            return;
        }
        double scale = w.getEnvironment() == World.Environment.NETHER ? 0.125 : 1.0;
        int min = (int) (plugin.getConfig().getInt("rtp.min-radius", 500) * scale);
        int max = (int) (plugin.getConfig().getInt("rtp.max-radius", 5000) * scale);
        int border = (int) (w.getWorldBorder().getSize() / 2.0) - 16;
        max = Math.min(max, border);
        if (max <= min) min = 0;
        Location center = w.getWorldBorder().getCenter();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double angle = r.nextDouble(Math.PI * 2);
        double dist = min + r.nextDouble() * Math.max(1, max - min);
        int x = (int) (center.getX() + Math.cos(angle) * dist);
        int z = (int) (center.getZ() + Math.sin(angle) * dist);

        w.getChunkAtAsync(x >> 4, z >> 4).whenComplete((chunk, error) ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location spot = error == null ? safeSpot(w, x, z) : null;
                if (spot == null) { attempt(p, w, tries + 1); return; }
                p.teleportAsync(spot).whenComplete((ok, err) -> {
                    searching.remove(p.getUniqueId());
                    if (Boolean.TRUE.equals(ok)) {
                        lastUse.put(p.getUniqueId(), System.currentTimeMillis());
                        p.sendMessage(Component.text("Teleported to " + spot.getBlockX() + ", "
                                + spot.getBlockY() + ", " + spot.getBlockZ() + ".", NamedTextColor.GREEN));
                    } else {
                        p.sendMessage(Component.text("Teleport failed. Try again.", NamedTextColor.RED));
                    }
                });
            }));
    }

    private static boolean unsafe(Material m) {
        return UNSAFE.contains(m) || Tag.LEAVES.isTagged(m);
    }

    private static Location safeSpot(World w, int x, int z) {
        if (w.getEnvironment() == World.Environment.NETHER) {
            // No sky in the nether: scan down under the roof for a floor with headroom.
            for (int y = 118; y > w.getMinHeight() + 5; y--) {
                Block feet = w.getBlockAt(x, y, z);
                Block head = feet.getRelative(0, 1, 0);
                Block ground = feet.getRelative(0, -1, 0);
                if (feet.isEmpty() && head.isEmpty() && ground.getType().isSolid() && !unsafe(ground.getType())) {
                    return feet.getLocation().add(0.5, 0, 0.5);
                }
            }
            return null;
        }
        Block top = w.getHighestBlockAt(x, z);
        Material m = top.getType();
        if (!m.isSolid() || unsafe(m)) return null;   // oceans, lava, treetops
        return top.getLocation().add(0.5, 1, 0.5);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("overworld", "nether")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        }
        return out;
    }
}
