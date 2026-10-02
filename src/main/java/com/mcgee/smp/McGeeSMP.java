package com.mcgee.smp;

import com.mcgee.smp.economy.Balances;
import com.mcgee.smp.economy.EconomyCommands;
import com.mcgee.smp.economy.JoinListener;
import com.mcgee.smp.economy.Money;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

/** McGee SMP: the server's own economy plus DonutSMP-style systems. */
public final class McGeeSMP extends JavaPlugin {
    private Balances balances;
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

        vaultHooked = hookVault();

        // Save once a minute; also on shutdown.
        getServer().getScheduler().runTaskTimer(this, balances::save, 1200L, 1200L);

        getLogger().info("McGeeSMP " + getDescription().getVersion() + " enabled.");
        getLogger().info(vaultHooked
            ? "Economy registered with Vault - shop, auction and spawner plugins will use it."
            : "Vault/VaultUnlocked not installed - /bal and /pay work, but other plugins can't see the economy yet.");
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
        getLogger().info("McGeeSMP disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("smpinfo")) return false;
        sender.sendMessage("McGeeSMP " + getDescription().getVersion() + " is loaded.");
        sender.sendMessage("Economy linked to other plugins: " + (vaultHooked ? "yes" : "no - install VaultUnlocked"));
        sender.sendMessage("Starting balance: " + Money.format(balances.startingBalance()));
        return true;
    }
}
