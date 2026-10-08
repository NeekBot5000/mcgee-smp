package com.mcgee.smp.tools;

import org.bukkit.Material;

/**
 * Everything in the shard shop.
 *
 * ETERNAL items never lose durability. They are diamond so a player can upgrade
 * them to netherite in a smithing table and keep the eternal effect.
 * ABILITY tools wear out like normal tools but do far more per swing.
 * Per the task doc, eternal items cost about half the ability tools.
 */
public enum ToolType {
    ETERNAL_SWORD("eternal_sword", Material.DIAMOND_SWORD, "Eternal Sword", Kind.ETERNAL, 50),
    ETERNAL_PICKAXE("eternal_pickaxe", Material.DIAMOND_PICKAXE, "Eternal Pickaxe", Kind.ETERNAL, 50),
    ETERNAL_AXE("eternal_axe", Material.DIAMOND_AXE, "Eternal Axe", Kind.ETERNAL, 50),
    ETERNAL_SHOVEL("eternal_shovel", Material.DIAMOND_SHOVEL, "Eternal Shovel", Kind.ETERNAL, 50),
    ETERNAL_HOE("eternal_hoe", Material.DIAMOND_HOE, "Eternal Hoe", Kind.ETERNAL, 50),
    ETERNAL_HELMET("eternal_helmet", Material.DIAMOND_HELMET, "Eternal Helmet", Kind.ETERNAL, 50),
    ETERNAL_CHESTPLATE("eternal_chestplate", Material.DIAMOND_CHESTPLATE, "Eternal Chestplate", Kind.ETERNAL, 50),
    ETERNAL_LEGGINGS("eternal_leggings", Material.DIAMOND_LEGGINGS, "Eternal Leggings", Kind.ETERNAL, 50),
    ETERNAL_BOOTS("eternal_boots", Material.DIAMOND_BOOTS, "Eternal Boots", Kind.ETERNAL, 50),

    TREE_AXE("tree_axe", Material.DIAMOND_AXE, "Tree Breaker Axe", Kind.ABILITY, 75),
    EXCAVATOR("excavator", Material.DIAMOND_PICKAXE, "Excavator Pickaxe", Kind.ABILITY, 100),
    HARVESTER("harvester", Material.DIAMOND_HOE, "Harvester Hoe", Kind.ABILITY, 150),

    SUPER_HOPPER("super_hopper", Material.HOPPER, "Super Hopper", Kind.BLOCK, 50);

    public enum Kind { ETERNAL, ABILITY, BLOCK }

    public final String id;
    public final Material material;
    public final String display;
    public final Kind kind;
    public final int defaultCost;

    ToolType(String id, Material material, String display, Kind kind, int defaultCost) {
        this.id = id;
        this.material = material;
        this.display = display;
        this.kind = kind;
        this.defaultCost = defaultCost;
    }

    public String description() {
        return switch (this) {
            case TREE_AXE -> "Fells the whole tree. Sneak to cut one log.";
            case EXCAVATOR -> "Mines a 3x3 area. Sneak to mine one block.";
            case HARVESTER -> "Harvests and replants ripe crops in a 3x3.";
            case SUPER_HOPPER -> "Moves items 10x faster than a normal hopper.";
            default -> "Never breaks. Upgrade it to netherite and it stays unbreakable.";
        };
    }

    public static ToolType byId(String id) {
        for (ToolType t : values()) if (t.id.equals(id)) return t;
        return null;
    }
}
