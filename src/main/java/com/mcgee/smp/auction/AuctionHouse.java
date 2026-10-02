package com.mcgee.smp.auction;

import com.mcgee.smp.economy.Balances;
import com.mcgee.smp.economy.Money;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * /ah - the auction house.
 *   /ah                  browse (45 per page, no limit on pages)
 *   /ah sell <price>     list the stack in your hand
 *   /ah search <text>    filter by item or seller
 *   /ah mine             your listings; click one to take it back
 * Plus a server restock: one useful stack every few minutes.
 */
public final class AuctionHouse implements CommandExecutor, TabCompleter, Listener {
    private enum Sort {
        NEWEST("Newest"), CHEAPEST("Cheapest"), PRICIEST("Most expensive");
        final String label;
        Sort(String label) { this.label = label; }
        Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static final class Browse implements InventoryHolder {
        Inventory inventory;
        int page;
        Sort sort = Sort.NEWEST;
        String filter;
        boolean mine;
        final Map<Integer, UUID> slots = new HashMap<>();
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final class Confirm implements InventoryHolder {
        Inventory inventory;
        UUID listingId;
        Browse back;
        @Override public Inventory getInventory() { return inventory; }
    }

    private static final int PER_PAGE = 45;

    private final JavaPlugin plugin;
    private final AuctionStore store;
    private final Balances balances;

    public AuctionHouse(JavaPlugin plugin, AuctionStore store, Balances balances) {
        this.plugin = plugin;
        this.store = store;
        this.balances = balances;
    }

    private static Component plain(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    static String nameOf(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        StringBuilder b = new StringBuilder();
        for (String w : item.getType().name().toLowerCase(Locale.ROOT).split("_")) {
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // Operators (and the console) can force a restock: handy, and used by the CI boot test.
        if (args.length > 0 && args[0].equalsIgnoreCase("restock")) {
            if (!sender.hasPermission("mcgee.admin")) { sender.sendMessage("Operators only."); return true; }
            String added = restockOnce();
            sender.sendMessage(added == null ? "Auction restock: nothing configured to add."
                    : "Auction restock: added " + added + " (" + store.all().size() + " listings total).");
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Only players can use the auction house."); return true; }
        if (args.length == 0) { open(p, new Browse()); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> sell(p, args);
            case "search" -> {
                if (args.length < 2) { p.sendMessage(plain("Usage: /ah search <item or player>", NamedTextColor.RED)); return true; }
                Browse b = new Browse();
                b.filter = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)).toLowerCase(Locale.ROOT);
                open(p, b);
            }
            case "mine" -> { Browse b = new Browse(); b.mine = true; open(p, b); }
            default -> p.sendMessage(plain("Usage: /ah, /ah sell <price>, /ah search <text>, /ah mine", NamedTextColor.RED));
        }
        return true;
    }

    private void sell(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(plain("Hold an item and type /ah sell <price>, e.g. /ah sell 2.5k", NamedTextColor.RED)); return; }
        double price = Money.parse(args[1]);
        double maxPrice = plugin.getConfig().getDouble("auction.max-price", 1e11);
        if (price <= 0 || price > maxPrice) { p.sendMessage(plain("That isn't a valid price. Try 500, 2.5k or 1m.", NamedTextColor.RED)); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { p.sendMessage(plain("Hold the item you want to sell.", NamedTextColor.RED)); return; }
        int max = plugin.getConfig().getInt("auction.max-listings", 20);
        if (store.countBy(p.getUniqueId()) >= max) {
            p.sendMessage(plain("You already have " + max + " listings. Take one back with /ah mine.", NamedTextColor.RED));
            return;
        }
        ItemStack listed = hand.clone();
        p.getInventory().setItemInMainHand(null);   // out of the hand before it's in the store: no window to dupe
        store.add(new Listing(UUID.randomUUID(), p.getUniqueId(), p.getName(), listed, Money.round(price), System.currentTimeMillis()));
        p.sendMessage(plain("Listed " + listed.getAmount() + "x " + nameOf(listed) + " for " + Money.format(price) + ".", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) for (String s : List.of("sell", "search", "mine")) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        return out;
    }

    // ---------------------------------------------------------------- menus

    private List<Listing> visible(Player p, Browse b) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : store.all()) {
            if (b.mine && !p.getUniqueId().equals(l.seller)) continue;
            if (b.filter != null && !nameOf(l.item).toLowerCase(Locale.ROOT).contains(b.filter)
                    && !l.sellerName.toLowerCase(Locale.ROOT).contains(b.filter)) continue;
            out.add(l);
        }
        switch (b.sort) {
            case NEWEST -> out.sort(Comparator.comparingLong((Listing l) -> l.created).reversed());
            case CHEAPEST -> out.sort(Comparator.comparingDouble(l -> l.price));
            case PRICIEST -> out.sort(Comparator.comparingDouble((Listing l) -> l.price).reversed());
        }
        return out;
    }

    private ItemStack button(Material m, String name, NamedTextColor c, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(plain(name, c));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(plain(s, NamedTextColor.GRAY));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack display(Listing l, Player viewer) {
        ItemStack d = l.item.clone();
        ItemMeta meta = d.getItemMeta();
        if (meta == null) return d;
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(plain("Price: " + Money.format(l.price), NamedTextColor.GOLD));
        lore.add(plain("Seller: " + (l.isServer() ? "Server" : l.sellerName), NamedTextColor.GRAY));
        lore.add(viewer.getUniqueId().equals(l.seller)
                ? plain("Click to take it back", NamedTextColor.YELLOW)
                : plain("Click to buy", NamedTextColor.GREEN));
        meta.lore(lore);
        d.setItemMeta(meta);
        return d;
    }

    private void open(Player p, Browse b) {
        List<Listing> list = visible(p, b);
        int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
        b.page = Math.max(0, Math.min(b.page, pages - 1));
        String title = "Auction House" + (b.mine ? " - Mine" : b.filter != null ? " - \"" + b.filter + "\"" : "")
                + " (" + (b.page + 1) + "/" + pages + ")";
        Inventory inv = Bukkit.createInventory(b, 54, Component.text(title));
        b.inventory = inv;
        b.slots.clear();
        int start = b.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            Listing l = list.get(start + i);
            inv.setItem(i, display(l, p));
            b.slots.put(i, l.id);
        }
        if (list.isEmpty()) {
            inv.setItem(22, button(Material.BARRIER, b.mine ? "You have no listings" : "Nothing here yet", NamedTextColor.GRAY,
                    "Hold an item and type /ah sell <price>"));
        }
        if (b.page > 0) inv.setItem(45, button(Material.ARROW, "Previous page", NamedTextColor.YELLOW));
        inv.setItem(47, button(Material.CHEST, b.mine ? "Show everything" : "My listings", NamedTextColor.AQUA,
                b.mine ? "Back to all listings" : "See and take back your items"));
        inv.setItem(48, button(Material.HOPPER, "Sort: " + b.sort.label, NamedTextColor.AQUA, "Click to change"));
        inv.setItem(49, button(Material.SUNFLOWER, "Refresh", NamedTextColor.GREEN, list.size() + " listing(s)"));
        inv.setItem(50, button(Material.OAK_SIGN, "Search", NamedTextColor.AQUA, "Type /ah search <text>"));
        if (b.page < pages - 1) inv.setItem(53, button(Material.ARROW, "Next page", NamedTextColor.YELLOW));
        p.openInventory(inv);
    }

    private void openConfirm(Player p, Listing l, Browse back) {
        Confirm c = new Confirm();
        c.listingId = l.id;
        c.back = back;
        Inventory inv = Bukkit.createInventory(c, 27, Component.text("Buy for " + Money.format(l.price) + "?"));
        c.inventory = inv;
        inv.setItem(13, display(l, p));
        inv.setItem(11, button(Material.LIME_CONCRETE, "Buy for " + Money.format(l.price), NamedTextColor.GREEN,
                "You have " + Money.format(balances.get(p.getUniqueId()))));
        inv.setItem(15, button(Material.RED_CONCRETE, "Cancel", NamedTextColor.RED));
        p.openInventory(inv);
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        InventoryHolder top = e.getView().getTopInventory().getHolder();
        if (!(top instanceof Browse) && !(top instanceof Confirm)) return;
        e.setCancelled(true);   // nothing moves in or out of these menus
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getView().getTopInventory().getSize()) return;

        if (top instanceof Confirm c) {
            if (slot == 11) { buy(p, c.listingId); later(() -> open(p, c.back)); }
            else if (slot == 15) later(() -> open(p, c.back));
            return;
        }
        Browse b = (Browse) top;
        switch (slot) {
            case 45 -> { b.page--; later(() -> open(p, b)); return; }
            case 53 -> { b.page++; later(() -> open(p, b)); return; }
            case 47 -> { b.mine = !b.mine; b.page = 0; later(() -> open(p, b)); return; }
            case 48 -> { b.sort = b.sort.next(); b.page = 0; later(() -> open(p, b)); return; }
            case 49 -> { later(() -> open(p, b)); return; }
            default -> { }
        }
        UUID id = b.slots.get(slot);
        if (id == null) return;
        Listing l = store.get(id);
        if (l == null) {
            p.sendMessage(plain("That listing is gone.", NamedTextColor.RED));
            later(() -> open(p, b));
            return;
        }
        if (p.getUniqueId().equals(l.seller)) {
            takeBack(p, l);
            later(() -> open(p, b));
        } else {
            later(() -> openConfirm(p, l, b));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        InventoryHolder top = e.getView().getTopInventory().getHolder();
        if (top instanceof Browse || top instanceof Confirm) e.setCancelled(true);
    }

    private void takeBack(Player p, Listing l) {
        if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage(plain("Make room in your inventory first.", NamedTextColor.RED));
            return;
        }
        Listing removed = store.remove(l.id);
        if (removed == null) return;
        p.getInventory().addItem(removed.item);
        p.sendMessage(plain("Took back " + removed.item.getAmount() + "x " + nameOf(removed.item) + ".", NamedTextColor.GREEN));
    }

    private void buy(Player p, UUID id) {
        Listing l = store.get(id);
        if (l == null) { p.sendMessage(plain("Someone else bought that first.", NamedTextColor.RED)); return; }
        if (p.getUniqueId().equals(l.seller)) return;
        if (p.getInventory().firstEmpty() == -1) {
            p.sendMessage(plain("Make room in your inventory first.", NamedTextColor.RED));
            return;
        }
        if (!balances.withdraw(p.getUniqueId(), l.price)) {
            p.sendMessage(plain("That costs " + Money.format(l.price) + " but you have "
                    + Money.format(balances.get(p.getUniqueId())) + ".", NamedTextColor.RED));
            return;
        }
        Listing removed = store.remove(id);
        if (removed == null) {   // can't happen on one thread, but never take money for nothing
            balances.deposit(p.getUniqueId(), l.price);
            p.sendMessage(plain("Someone else bought that first. You weren't charged.", NamedTextColor.RED));
            return;
        }
        p.getInventory().addItem(removed.item);
        if (removed.seller != null) {
            // Paid even if the seller is offline: balances are stored by UUID.
            balances.deposit(removed.seller, removed.price);
            Player seller = Bukkit.getPlayer(removed.seller);
            if (seller != null) seller.sendMessage(plain(p.getName() + " bought your " + removed.item.getAmount() + "x "
                    + nameOf(removed.item) + " for " + Money.format(removed.price) + ".", NamedTextColor.GREEN));
        }
        p.sendMessage(plain("Bought " + removed.item.getAmount() + "x " + nameOf(removed.item) + " for "
                + Money.format(removed.price) + ".", NamedTextColor.GREEN));
    }

    // ---------------------------------------------------------------- restock

    /** One useful stack from the config list every few minutes (task doc #18). */
    public void startRestock() {
        if (!plugin.getConfig().getBoolean("auction.restock.enabled", true)) return;
        long minutes = Math.max(1, plugin.getConfig().getLong("auction.restock.interval-minutes", 5));
        long ticks = minutes * 60L * 20L;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::restockOnce, ticks, ticks);
        plugin.getLogger().info("Auction restock: one stack every " + minutes + " min.");
    }

    /** Adds one server listing. Returns what was added, or null. */
    public String restockOnce() {
        ConfigurationSection items = plugin.getConfig().getConfigurationSection("auction.restock.items");
        if (items == null || items.getKeys(false).isEmpty()) return null;
        List<String> keys = new ArrayList<>(items.getKeys(false));
        String key = keys.get(ThreadLocalRandom.current().nextInt(keys.size()));
        Material m = Material.matchMaterial(key);
        if (m == null || !m.isItem()) {
            plugin.getLogger().warning("auction.restock.items: unknown item " + key);
            return null;
        }
        int amount = Math.max(1, Math.min(m.getMaxStackSize(), items.getInt(key + ".amount", m.getMaxStackSize())));
        double base = items.getDouble(key + ".price", 1000);
        double price = Money.round(base * (0.9 + ThreadLocalRandom.current().nextDouble() * 0.2));   // +/- 10%
        store.add(new Listing(UUID.randomUUID(), null, "Server", new ItemStack(m, amount), price, System.currentTimeMillis()));
        int cap = Math.max(1, plugin.getConfig().getInt("auction.restock.max-server-listings", 30));
        List<Listing> server = store.serverListings();
        for (int i = 0; i < server.size() - cap; i++) store.remove(server.get(i).id);   // oldest go first
        return amount + "x " + m.name() + " for " + Money.format(price);
    }
}
