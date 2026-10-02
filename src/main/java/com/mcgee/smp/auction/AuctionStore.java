package com.mcgee.smp.auction;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * All listings, saved to plugins/McGeeSMP/auction.yml.
 *
 * Items are stored with Paper's own byte format, which keeps everything:
 * enchantments, names, shulker box contents, our eternal tags. It also
 * upgrades old items when Minecraft updates.
 *
 * Every change is saved immediately. If the server crashed after a sale but
 * before a save, the sold item would come back on restart - a duplicate.
 * Writing straight away closes that gap; the file is small.
 */
public final class AuctionStore {
    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, Listing> listings = new LinkedHashMap<>();

    public AuctionStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auction.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        ConfigurationSection s = YamlConfiguration.loadConfiguration(file).getConfigurationSection("listings");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            try {
                ConfigurationSection l = s.getConfigurationSection(key);
                if (l == null) continue;
                String sellerRaw = l.getString("seller", "server");
                UUID seller = "server".equals(sellerRaw) ? null : UUID.fromString(sellerRaw);
                ItemStack item = ItemStack.deserializeBytes(Base64.getDecoder().decode(l.getString("item", "")));
                Listing listing = new Listing(UUID.fromString(key), seller, l.getString("seller-name", "?"),
                        item, l.getDouble("price"), l.getLong("created"));
                listings.put(listing.id, listing);
            } catch (Exception e) {
                plugin.getLogger().warning("Skipping unreadable auction listing " + key + ": " + e.getMessage());
            }
        }
        plugin.getLogger().info("Auction house: " + listings.size() + " listings loaded.");
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String k = "listings." + l.id;
            y.set(k + ".seller", l.seller == null ? "server" : l.seller.toString());
            y.set(k + ".seller-name", l.sellerName);
            y.set(k + ".price", l.price);
            y.set(k + ".created", l.created);
            y.set(k + ".item", Base64.getEncoder().encodeToString(l.item.serializeAsBytes()));
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save auction.yml: " + e.getMessage());
        }
    }

    public void add(Listing l) {
        listings.put(l.id, l);
        save();
    }

    /** Removes and returns the listing, or null if someone already took it. */
    public Listing remove(UUID id) {
        Listing l = listings.remove(id);
        if (l != null) save();
        return l;
    }

    public Listing get(UUID id) {
        return listings.get(id);
    }

    public List<Listing> all() {
        return new ArrayList<>(listings.values());
    }

    public int countBy(UUID seller) {
        int n = 0;
        for (Listing l : listings.values()) if (seller.equals(l.seller)) n++;
        return n;
    }

    /** Server restock listings, oldest first. */
    public List<Listing> serverListings() {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (l.isServer()) out.add(l);
        out.sort(Comparator.comparingLong(l -> l.created));
        return out;
    }
}
