package com.mcgee.smp;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Skeleton for the McGee SMP plugin.
 *
 * This exists first on purpose: it proves the whole pipeline works
 * (source -> GitHub -> CI compile -> downloadable jar) before any real
 * feature is written. Economy, shards, spawners and the rest build on it.
 */
public final class McGeeSMP extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("McGeeSMP " + getDescription().getVersion() + " enabled.");
        getLogger().info("Server: " + getServer().getName() + " " + getServer().getVersion());
    }

    @Override
    public void onDisable() {
        getLogger().info("McGeeSMP disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("smpinfo")) return false;
        sender.sendMessage("McGeeSMP " + getDescription().getVersion() + " is loaded.");
        sender.sendMessage("Server: " + getServer().getVersion());
        return true;
    }
}
