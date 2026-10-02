package com.mcgee.smp;

import com.mcgee.smp.economy.Balances;
import com.mcgee.smp.economy.EconomyCommands;
import com.mcgee.smp.economy.JoinListener;
import com.mcgee.smp.economy.Money;
import com.mcgee.smp.shards.ShardCommands;
import com.mcgee.smp.shards.Shards;
import com.mcgee.smp.tools.AbilityListener;
import com.mcgee.smp.tools.ShardShop;
import com.mcgee.smp.tools.SmithingListener;
import com.mcgee.smp.tools.Tools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** McGee SMP: the server's own economy plus DonutSMP-style systems. */
public final class McGeeSMP extends JavaPlugin {
    private Balances balances;
    private Shards shards;
    private boolean vaultHooked;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        balances = new Balances(this);

        EconomyCommands eco = new EconomyCommands(balances);
        for (String name : new String[]{"balance", "pay", "baltop", "eco"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(eco);
                c.setTabCompleter(eco);
            }
        }
        getServer().getPluginManager().registerEvents(new JoinListener(this, balances), this);

        shards = new Shards(this);
        ShardCommands shardCmds = new ShardCommands(this, shards, balances);
        for (String name : new String[]{"shards", "buyshards", "shardsadmin"}) {
            PluginCommand c = getCommand(name);
            if (c != null) {
                c.setExecutor(shardCmds);
                c.setTabCompleter(shardCmds);
            }
        }
        startShardTimer();

        Tools tools = new Tools(this);
        ShardShop shop = new ShardShop(this, tools, shards);
        PluginCommand shopCmd = getCommand("shardshop");
        if (shopCmd != null) shopCmd.setExecutor(shop);
        getServer().getPluginManager().registerEvents(shop, this);
        getServer().getPluginManager().registerEvents(new AbilityListener(tools), this);
        getServer().getPluginManager().registerEvents(new SmithingListener(tools), this);
        getLogger().info("Shard shop ready: eternal gear and ability tools (/shardshop).");

        vaultHooked = hookVault();

        // Save once a minute; also on shutdown.
        getServer().getScheduler().runTaskTimer(this, balances::save, 1200L, 1200L);
        getServer().getScheduler().runTaskTimer(this, shards::save, 1200L, 1200L);

        getLogger().info("McGeeSMP " + getDescription().getVersion() + " enabled.");
        getLogger().info(vaultHooked
            ? "Economy registered with Vault - shop, auction and spawner plugins will use it."
            : "Vault/VaultUnlocked not installed - /bal and /pay work, but other plugins can't see the economy yet.");
    }

    /** Everyone online earns shards on a timer, like DonutSMP's AFK shards. */
    private void startShardTimer() {
        long minutes = Math.max(1, getConfig().getLong("shards.interval-minutes", 5));
        long amount = Math.max(1, getConfig().getLong("shards.amount", 1));
        long ticks = minutes * 60L * 20L;
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : getServer().getOnlinePlayers()) {
                shards.add(p.getUniqueId(), amount);
                long total = shards.get(p.getUniqueId());
                p.sendActionBar(Component.text("+" + amount + (amount == 1 ? " shard" : " shards")
                        + "  (" + total + " total)", NamedTextColor.LIGHT_PURPLE));
            }
        }, ticks, ticks);
        getLogger().info("Shards: +" + amount + " every " + minutes + " min for everyone online.");
    }

    /** Kept separate so the Vault classes are only loaded when Vault exists. */
    private boolean hookVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null
                && getServer().getPluginManager().getPlugin("VaultUnlocked") == null) {
            return false;
        }
        try {
            getServer().getServicesManager().register(
                net.milkbowl.vault.economy.Economy.class,
                new com.mcgee.smp.economy.VaultHook(balances),
                this,
                ServicePriority.High);
            return true;
        } catch (Throwable t) {
            getLogger().warning("Could not register with Vault: " + t);
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (balances != null) balances.save();
        if (shards != null) shards.save();
        getLogger().info("McGeeSMP disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("smpinfo")) return false;
        sender.sendMessage("McGeeSMP " + getDescription().getVersion() + " is loaded.");
        sender.sendMessage("Economy linked to other plugins: " + (vaultHooked ? "yes" : "no - install VaultUnlocked"));
        sender.sendMessage("Starting balance: " + Money.format(balances.startingBalance()));
        sender.sendMessage("Shards: +" + getConfig().getLong("shards.amount", 1) + " every "
                + getConfig().getLong("shards.interval-minutes", 5) + " min, "
                + Money.format(getConfig().getDouble("shards.price", 10000)) + " each to buy");
        return true;
    }
}
