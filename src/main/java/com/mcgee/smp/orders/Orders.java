package com.mcgee.smp.orders;

import com.mcgee.smp.economy.Balances;
import com.mcgee.smp.economy.Money;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Buy orders, DonutSMP style.
 *   /order <amount> <price each>  order more of what you're holding (you keep it);
 *                                 the full cost is held up front
 *   /orders                       everyone's orders; click one to fill it from
 *                                 your inventory, or click your own to cancel
 *   /stash                        collect items delivered to your orders
 * Delivered items wait in the stash, so a full or offline buyer loses nothing.
 * Only plain items count towards an order (no renamed or enchanted copies).
 */
public final class Orders implements CommandExecutor, Listener {
    private static final class Order {
        final UUID id, buyer;
        final String buyerName;
        final Material mat;
        int remaining;
        final double each;
        Order(UUID id, UUID buyer, String buyerName, Material mat, int remaining, double each) {
            this.id = id; this.buyer = buyer; this.buyerName = buyerName; this.mat = mat; this.remaining = remaining; this.each = each;
        }
    }

    private static final class Holder implements InventoryHolder {
        Inventory inventory;
        int page;
        final Map<Integer, UUID> slots = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }

    private final JavaPlugin plugin;
    private final Balances balances;
    private final File file;
    private final Map<UUID, Order> orders = new LinkedHashMap<>();
    private final Map<UUID, Map<Material, Integer>> stash = new HashMap<>();

    public Orders(JavaPlugin plugin, Balances balances) {
        this.plugin = plugin;
        this.balances = balances;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
        load();
    }

    // ------------------------------------------------------------ storage
    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection os = y.getConfigurationSection("orders");
        if (os != null) for (String k : os.getKeys(false)) {
            try {
                ConfigurationSection o = os.getConfigurationSection(k);
                Material m = Material.matchMaterial(o.getString("item", ""));
                if (m == null) continue;
                orders.put(UUID.fromString(k), new Order(UUID.fromString(k), UUID.fromString(o.getString("buyer")),
                        o.getString("buyer-name", "?"), m, o.getInt("remaining"), o.getDouble("each")));
            } catch (Exception e) { plugin.getLogger().warning("Skipping bad order " + k); }
        }
        ConfigurationSection ss = y.getConfigurationSection("stash");
        if (ss != null) for (String k : ss.getKeys(false)) {
            Map<Material, Integer> items = new EnumMap<>(Material.class);
            ConfigurationSection s = ss.getConfigurationSection(k);
            for (String mk : s.getKeys(false)) {
                Material m = Material.matchMaterial(mk);
                if (m != null) items.put(m, s.getInt(mk));
            }
            stash.put(UUID.fromString(k), items);
        }
    }

    /** Saved on every change: money and items move here, so nothing should be lost to a crash. */
    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Order o : orders.values()) {
            String k = "orders." + o.id;
            y.set(k + ".buyer", o.buyer.toString());
            y.set(k + ".buyer-name", o.buyerName);
            y.set(k + ".item", o.mat.name());
            y.set(k + ".remaining", o.remaining);
            y.set(k + ".each", o.each);
        }
        stash.forEach((id, items) -> items.forEach((m, n) -> { if (n > 0) y.set("stash." + id + "." + m.name(), n); }));
        try { plugin.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException e) { plugin.getLogger().severe("Could not save orders.yml: " + e.getMessage()); }
    }

    private static Component plain(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private static String pretty(Material m) {
        StringBuilder b = new StringBuilder();
        for (String w : m.name().toLowerCase(Locale.ROOT).split("_")) {
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    // ------------------------------------------------------------ commands
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can use orders."); return true; }
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "order" -> create(p, args);
            case "orders" -> { Holder h = new Holder(); open(p, h); }
            case "stash" -> collect(p);
            default -> { return false; }
        }
        return true;
    }

    private void create(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(plain("Hold the item you want, then /order <amount> <price each>", NamedTextColor.RED)); return; }
        Material m = p.getInventory().getItemInMainHand().getType();
        if (m.isAir()) { p.sendMessage(plain("Hold an example of the item you want. You keep it.", NamedTextColor.RED)); return; }
        int amount;
        try { amount = (int) Money.parse(args[0]); } catch (Exception e) { amount = -1; }
        double each = Money.parse(args[1]);
        int maxAmount = plugin.getConfig().getInt("orders.max-amount", 100000);
        if (amount < 1 || amount > maxAmount) { p.sendMessage(plain("Amount must be 1 to " + maxAmount + " (1k works too).", NamedTextColor.RED)); return; }
        if (each <= 0) { p.sendMessage(plain("That isn't a valid price.", NamedTextColor.RED)); return; }
        int mine = 0;
        for (Order o : orders.values()) if (o.buyer.equals(p.getUniqueId())) mine++;
        int maxOrders = plugin.getConfig().getInt("orders.max-per-player", 10);
        if (mine >= maxOrders) { p.sendMessage(plain("You already have " + maxOrders + " orders open.", NamedTextColor.RED)); return; }
        double total = Money.round(amount * each);
        if (!balances.withdraw(p.getUniqueId(), total)) {
            p.sendMessage(plain("That order costs " + Money.format(total) + " up front.", NamedTextColor.RED));
            return;
        }
        Order o = new Order(UUID.randomUUID(), p.getUniqueId(), p.getName(), m, amount, Money.round(each));
        orders.put(o.id, o);
        save();
        p.sendMessage(plain("Ordered " + amount + "x " + pretty(m) + " at " + Money.format(each) + " each. "
                + Money.format(total) + " is held until it's filled or you cancel.", NamedTextColor.GREEN));
    }

    private void open(Player p, Holder h) {
        List<Order> list = new ArrayList<>(orders.values());
        int pages = Math.max(1, (list.size() + 44) / 45);
        h.page = Math.max(0, Math.min(h.page, pages - 1));
        Inventory inv = Bukkit.createInventory(h, 54, Component.text("Orders (" + (h.page + 1) + "/" + pages + ")"));
        h.inventory = inv;
        h.slots.clear();
        for (int i = 0; i < 45 && h.page * 45 + i < list.size(); i++) {
            Order o = list.get(h.page * 45 + i);
            ItemStack it = new ItemStack(o.mat, Math.max(1, Math.min(o.remaining, o.mat.getMaxStackSize())));
            ItemMeta meta = it.getItemMeta();
            boolean own = o.buyer.equals(p.getUniqueId());
            meta.lore(List.of(
                    plain("Wants: " + o.remaining + " more", NamedTextColor.WHITE),
                    plain("Pays: " + Money.format(o.each) + " each", NamedTextColor.GOLD),
                    plain("Buyer: " + o.buyerName, NamedTextColor.GRAY),
                    own ? plain("Click to cancel and get your money back", NamedTextColor.YELLOW)
                        : plain("Click to deliver from your inventory", NamedTextColor.GREEN)));
            it.setItemMeta(meta);
            inv.setItem(i, it);
            h.slots.put(i, o.id);
        }
        if (h.page > 0) inv.setItem(45, button(Material.ARROW, "Previous page"));
        inv.setItem(47, button(Material.SUNFLOWER, "Refresh (" + list.size() + " orders)"));
        inv.setItem(49, button(Material.ENDER_CHEST, "Your stash: " + stashCount(p.getUniqueId()) + " items (/stash)"));
        if (h.page < pages - 1) inv.setItem(53, button(Material.ARROW, "Next page"));
        p.openInventory(inv);
    }

    private ItemStack button(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plain(name, NamedTextColor.AQUA));
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot == 45) { h.page--; Bukkit.getScheduler().runTask(plugin, () -> open(p, h)); return; }
        if (slot == 53) { h.page++; Bukkit.getScheduler().runTask(plugin, () -> open(p, h)); return; }
        if (slot == 47) { Bukkit.getScheduler().runTask(plugin, () -> open(p, h)); return; }
        if (slot == 49) { Bukkit.getScheduler().runTask(plugin, () -> { p.closeInventory(); collect(p); }); return; }
        UUID id = h.slots.get(slot);
        if (id == null) return;
        Order o = orders.get(id);
        if (o == null) { p.sendMessage(plain("That order is gone.", NamedTextColor.RED)); }
        else if (o.buyer.equals(p.getUniqueId())) cancel(p, o);
        else deliver(p, o);
        Bukkit.getScheduler().runTask(plugin, () -> open(p, h));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }

    private void cancel(Player p, Order o) {
        orders.remove(o.id);
        double refund = Money.round(o.remaining * o.each);
        balances.deposit(p.getUniqueId(), refund);
        save();
        p.sendMessage(plain("Order cancelled. " + Money.format(refund) + " returned.", NamedTextColor.GREEN));
    }

    private void deliver(Player p, Order o) {
        ItemStack plainItem = new ItemStack(o.mat);
        PlayerInventory inv = p.getInventory();
        int have = 0;
        for (ItemStack it : inv.getStorageContents()) if (it != null && it.isSimilar(plainItem)) have += it.getAmount();
        int n = Math.min(have, o.remaining);
        if (n <= 0) { p.sendMessage(plain("You don't have any plain " + pretty(o.mat) + ".", NamedTextColor.RED)); return; }
        int left = n;
        while (left > 0) {
            int chunk = Math.min(left, o.mat.getMaxStackSize());
            inv.removeItem(new ItemStack(o.mat, chunk));
            left -= chunk;
        }
        o.remaining -= n;
        if (o.remaining <= 0) orders.remove(o.id);
        stash.computeIfAbsent(o.buyer, k -> new EnumMap<>(Material.class)).merge(o.mat, n, Integer::sum);
        double pay = Money.round(n * o.each);
        balances.deposit(p.getUniqueId(), pay);
        save();
        p.sendMessage(plain("Delivered " + n + "x " + pretty(o.mat) + " for " + Money.format(pay) + ".", NamedTextColor.GREEN));
        Player buyer = Bukkit.getPlayer(o.buyer);
        if (buyer != null) buyer.sendMessage(plain(p.getName() + " delivered " + n + "x " + pretty(o.mat)
                + " to your order. Collect it with /stash.", NamedTextColor.GREEN));
    }

    private int stashCount(UUID id) {
        int n = 0;
        for (int v : stash.getOrDefault(id, Map.of()).values()) n += v;
        return n;
    }

    private void collect(Player p) {
        Map<Material, Integer> items = stash.get(p.getUniqueId());
        if (items == null || items.isEmpty()) { p.sendMessage(plain("Your stash is empty.", NamedTextColor.GRAY)); return; }
        int given = 0;
        for (Map.Entry<Material, Integer> e : new ArrayList<>(items.entrySet())) {
            int left = e.getValue();
            while (left > 0) {
                int chunk = Math.min(left, e.getKey().getMaxStackSize());
                int notFit = 0;
                for (ItemStack rest : p.getInventory().addItem(new ItemStack(e.getKey(), chunk)).values()) notFit += rest.getAmount();
                given += chunk - notFit;
                left -= chunk - notFit;
                if (notFit > 0) break;   // inventory full
            }
            if (left > 0) items.put(e.getKey(), left); else items.remove(e.getKey());
        }
        if (items.isEmpty()) stash.remove(p.getUniqueId());
        save();
        p.sendMessage(plain("Collected " + given + " items." + (stashCount(p.getUniqueId()) > 0
                ? " Inventory full - " + stashCount(p.getUniqueId()) + " still waiting." : ""), NamedTextColor.GREEN));
    }
}
