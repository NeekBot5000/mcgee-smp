package com.mcgee.smp.economy;

import net.milkbowl.vault.economy.AbstractEconomy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

/**
 * Exposes our balances through the standard Vault economy interface, so the
 * shop, auction house and spawner plugins all use the same money.
 *
 * The OfflinePlayer methods are the real ones (UUID-based). The old
 * name-based methods just look the player up and delegate.
 */
public final class VaultHook extends AbstractEconomy {
    private final Balances balances;

    public VaultHook(Balances balances) {
        this.balances = balances;
    }

    @Override public boolean isEnabled() { return true; }
    @Override public String getName() { return "McGeeSMP"; }
    @Override public boolean hasBankSupport() { return false; }
    @Override public int fractionalDigits() { return 2; }
    @Override public String format(double amount) { return Money.format(amount); }
    @Override public String currencyNamePlural() { return "dollars"; }
    @Override public String currencyNameSingular() { return "dollar"; }

    // ---- UUID-based (what modern plugins call) ----------------------------

    @Override public boolean hasAccount(OfflinePlayer p) { return balances.hasAccount(p.getUniqueId()); }
    @Override public boolean hasAccount(OfflinePlayer p, String world) { return hasAccount(p); }
    @Override public double getBalance(OfflinePlayer p) { return balances.get(p.getUniqueId()); }
    @Override public double getBalance(OfflinePlayer p, String world) { return getBalance(p); }
    @Override public boolean has(OfflinePlayer p, double amount) { return balances.get(p.getUniqueId()) + 1e-9 >= amount; }
    @Override public boolean has(OfflinePlayer p, String world, double amount) { return has(p, amount); }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer p, double amount) {
        if (amount < 0) return fail(p, "Cannot withdraw a negative amount");
        if (!balances.withdraw(p.getUniqueId(), amount)) return fail(p, "Insufficient funds");
        return new EconomyResponse(amount, balances.get(p.getUniqueId()), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer p, String world, double amount) {
        return withdrawPlayer(p, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer p, double amount) {
        if (amount < 0) return fail(p, "Cannot deposit a negative amount");
        balances.create(p.getUniqueId(), p.getName(), balances.startingBalance());
        balances.deposit(p.getUniqueId(), amount);
        return new EconomyResponse(amount, balances.get(p.getUniqueId()), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer p, String world, double amount) {
        return depositPlayer(p, amount);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer p) {
        return balances.create(p.getUniqueId(), p.getName(), balances.startingBalance());
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer p, String world) {
        return createPlayerAccount(p);
    }

    private EconomyResponse fail(OfflinePlayer p, String why) {
        return new EconomyResponse(0, balances.get(p.getUniqueId()), ResponseType.FAILURE, why);
    }

    // ---- legacy name-based methods ----------------------------------------

    @SuppressWarnings("deprecation")
    private static OfflinePlayer byName(String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    @Override public boolean hasAccount(String name) { return hasAccount(byName(name)); }
    @Override public boolean hasAccount(String name, String world) { return hasAccount(byName(name)); }
    @Override public double getBalance(String name) { return getBalance(byName(name)); }
    @Override public double getBalance(String name, String world) { return getBalance(byName(name)); }
    @Override public boolean has(String name, double amount) { return has(byName(name), amount); }
    @Override public boolean has(String name, String world, double amount) { return has(byName(name), amount); }
    @Override public EconomyResponse withdrawPlayer(String name, double amount) { return withdrawPlayer(byName(name), amount); }
    @Override public EconomyResponse withdrawPlayer(String name, String world, double amount) { return withdrawPlayer(byName(name), amount); }
    @Override public EconomyResponse depositPlayer(String name, double amount) { return depositPlayer(byName(name), amount); }
    @Override public EconomyResponse depositPlayer(String name, String world, double amount) { return depositPlayer(byName(name), amount); }
    @Override public boolean createPlayerAccount(String name) { return createPlayerAccount(byName(name)); }
    @Override public boolean createPlayerAccount(String name, String world) { return createPlayerAccount(byName(name)); }

    // ---- banks: not supported ----------------------------------------------

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks are not supported");
    }

    @Override public EconomyResponse createBank(String name, String player) { return noBanks(); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return noBanks(); }
    @Override public EconomyResponse deleteBank(String name) { return noBanks(); }
    @Override public EconomyResponse bankBalance(String name) { return noBanks(); }
    @Override public EconomyResponse bankHas(String name, double amount) { return noBanks(); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return noBanks(); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return noBanks(); }
    @Override public EconomyResponse isBankOwner(String name, String player) { return noBanks(); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return noBanks(); }
    @Override public EconomyResponse isBankMember(String name, String player) { return noBanks(); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return noBanks(); }
    @Override public List<String> getBanks() { return Collections.emptyList(); }
}
