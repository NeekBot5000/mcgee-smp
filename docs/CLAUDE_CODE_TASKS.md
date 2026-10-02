# Tasks for Claude Code (on Nic's PC)

Everything from the task doc that needs code is in the McGeeSMP plugin. What's
left is installing and configuring other plugins on the server. Do these in
order, stopping the server (type `stop`) before changing files. Ask Nic before
deleting anything or buying anything.

## 1. Update McGeeSMP
Download the newest `McGeeSMP-*.jar` from
https://github.com/NeekBot5000/mcgee-smp/releases (highest build number),
replace the old one in `plugins/`, start the server, run `smpinfo`.
Its existing `config.yml` won't gain new sections automatically. The defaults
are built in, but to make settings editable, merge the new sections from the
repo's `src/main/resources/config.yml` into `plugins/McGeeSMP/config.yml`.

## 2. Homes, /tpa, /spawn (EssentialsX)
Run `plugins` in the console. If EssentialsX isn't there, install EssentialsX
(core only) for Paper 26.2. Then, because McGeeSMP owns the economy, open
`plugins/Essentials/config.yml` and add our commands to `disabled-commands`:

    disabled-commands:
      - balance
      - bal
      - money
      - pay
      - baltop
      - balancetop
      - eco

Otherwise Essentials' /bal and /pay answer instead of ours and show different
numbers. (Our economy registers with Vault at High priority, so other plugins
already use ours.)

## 3. Remove zAuctionHouse if installed
McGeeSMP now has its own auction house on `/ah`. Two plugins on `/ah` clash.

## 4. Shop (EconomyShopGUI) - task doc items 2, 6, 16, 20, 28, 33
- Remove buying (keep selling) for diamonds, iron, wood/logs, enchanted
  golden apples.
- Make ender chests and shulker boxes buyable.
- Turn on sell value in item lore, sell-all above a stack, and sell wands.
- **Arbitrage check:** for every item in McGeeSMP's `auction.restock.items`,
  the restock price for that stack must be ABOVE what the shop pays to buy
  that many back. Raise any restock price that isn't. Otherwise players can
  buy from /ah and sell to the shop for free money.

## 5. Spawners (SmartSpawner) - item 27
Install SmartSpawner for Paper 26.2 if missing; make broken spawners drop as
items (silk touch requirement is Nic's call).

## 6. Combat logging and X-ray - items 25, 32
Install CombatLogX and Orebfuscator for Paper 26.2. Defaults are fine.

## 7. Check
Restart, then in game: `/smpinfo`, `/bal`, `/ah`, `/orders`, `/shardshop`,
`/rtp`, `/leaderboard`. Read the startup log for any plugin that failed.
