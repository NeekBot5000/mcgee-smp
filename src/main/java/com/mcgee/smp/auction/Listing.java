package com.mcgee.smp.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** One item for sale. A null seller means a server restock listing. */
public final class Listing {
    public final UUID id;
    public final UUID seller;
    public final String sellerName;
    public final ItemStack item;
    public final double price;
    public final long created;

    public Listing(UUID id, UUID seller, String sellerName, ItemStack item, double price, long created) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.created = created;
    }

    public boolean isServer() {
        return seller == null;
    }
}
