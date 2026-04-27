# Power Ruble

Server-side Fabric 1.20.1 economy mod.

## Optional Addons

- `power-ruble-orgcontracts` - separate addon jar with organizations, shared balances, and escrow-backed item delivery contracts.
- addon sources live in [addons/power-ruble-orgcontracts](/home/amd-btw/projects/power-ruble/addons/power-ruble-orgcontracts/README.md)

## Commands

- `/balance` - show your balance.
- `/balance <player>` - show another player's balance, including offline players known to the server, operator-only.
- `/pay <player> <amount>` - transfer currency to a player, including offline players known to the server.
- `/payconfirm` - confirm a large pending transfer.
- `/paycancel` - cancel a large pending transfer.
- `/sell <player> <price> [comment]` - offer the item in your main hand to a specific online player.
- `/buyconfirm` - confirm a pending direct item purchase.
- `/buycancel` - cancel a pending direct item purchase.
- `/topbalance` - show the richest balances. Can be disabled for regular players in config.
- `/topdebt` - show the largest negative balances. Can be disabled for regular players in config.
- `/bank balance` - show the server bank balance when bank support is enabled.
- `/bank deposit <amount> [comment]` - transfer money from your balance to the server bank.
- `/ruble help` - show mod commands.
- `/ruble give <player> <amount>` - add rubles, including offline players known to the server, operator-only.
- `/ruble take <player> <amount> [reason]` - remove rubles, including offline players known to the server, operator-only.
- `/ruble set <player> <amount>` - set balance, including offline players known to the server, operator-only.
- `/ruble history <player>` - show recent economy operations, including offline players known to the server, operator-only.
- `/ruble paylog <player>` - show structured operation log for a player, operator-only.
- `/ruble paylog recent` - show recent structured economy operations, operator-only.
- `/ruble debtors` - show the largest negative balances, operator-only.
- `/ruble bank balance` - show the server bank balance, operator-only.
- `/ruble bank give <amount>` - add money to the server bank, operator-only.
- `/ruble bank take <amount>` - remove money from the server bank, operator-only.
- `/ruble bank set <amount>` - set the server bank balance, operator-only.
- `/ruble bank pay <player> <amount> [comment]` - transfer money from the server bank to a player, operator-only.
- `/ruble reload` - reload config, operator-only.

Player transfers can move the sender down to the configured debt limit. Operator `/ruble take` can move a player below zero without a fixed debt limit.

Balances and structured operation logs are saved in the overworld persistent state and survive server restarts, including negative balances.

On online-mode servers, commands do not create synthetic offline UUIDs for unknown player names. The player must be known to the server profile cache. Offline-mode servers use Minecraft's standard offline UUID format.

The mod contains only server-side behavior, but its Fabric environment is set to `*` so it also loads in singleplayer/integrated-server testing.

## Config

The mod creates `config/power-ruble.json5` on first launch. JSON5 allows comments, trailing commas, and unquoted keys:

```json5
{
  // Text appended after money amounts in chat messages.
  currency: {
    name: "RUB",
  },

  transfers: {
    // Minimum and maximum amount allowed for one /pay command.
    minAmount: 1,
    maxAmount: 100000,

    // Lowest balance a player may have after /pay.
    debtLimit: -1000,

    // Transfers at or above this amount require /payconfirm. 0 disables confirmation.
    confirmAbove: 0,

    fee: {
      // Fixed fee charged on every /pay.
      fixed: 0,

      // Percent fee. 2.5 means 2.5%.
      percent: 0.0,
      min: 0,
      max: 0,

      // exchange = fee disappears, bank = server bank, any other value = player name.
      recipient: "exchange",
    },
  },

  top: {
    playersEnabled: true,
    size: 10,
    showDebtTop: true,
  },

  bank: {
    enabled: true,
    accountName: "bank",
  },

  limits: {
    // Anti-spam and anti-abuse limits for regular players. Operators bypass them.
    // dailyTransferLimit is kept in memory and resets after server restart.
    payCooldownSeconds: 0,
    dailyTransferLimit: 0,
  },

  history: {
    perPlayerEntries: 50,
    globalEntries: 500,
  },

  taxes: {
    enabled: false,
    intervalMinutes: 1440,
    wealthTaxPercent: 0.0,
    minimumBalance: 0,
  },
}
```

- `currency.name` - text shown after amounts in chat messages.
- `transfers.minAmount` - minimum amount for one `/pay`.
- `transfers.maxAmount` - maximum amount for one `/pay`.
- `transfers.debtLimit` - minimum balance allowed after `/pay`.
- `transfers.confirmAbove` - transfers at or above this amount require `/payconfirm`; `0` disables confirmation.
- `transfers.fee.fixed` - fixed fee charged on each `/pay`.
- `transfers.fee.percent` - percent fee charged on each `/pay`; `2.5` means 2.5%.
- `transfers.fee.min` - minimum fee when a fee is charged.
- `transfers.fee.max` - maximum fee; `0` disables the cap.
- `transfers.fee.recipient` - `exchange` removes the fee from circulation, `bank` sends it to the server bank, any other value is treated as a player name that receives the fee.
- `top.playersEnabled` - whether regular players can use `/topbalance`; operators can always use it.
- `top.size` - number of entries shown by `/topbalance`.
- `top.showDebtTop` - whether regular players can use `/topdebt`; operators can always use it.
- `bank.enabled` - whether `/bank balance` is available to regular players and whether fees can go to the bank.
- `bank.accountName` - display name for the bank account.
- `limits.payCooldownSeconds` - cooldown between regular player `/pay` commands; operators bypass it.
- `limits.dailyTransferLimit` - in-memory daily transfer limit for regular players; `0` disables it, operators bypass it, and usage resets after server restart.
- `history.perPlayerEntries` - number of entries shown by player-specific history/paylog commands.
- `history.globalEntries` - number of entries shown by `/ruble paylog recent`.

Some fields are intentionally reserved for upcoming features: taxes.

If `config/power-ruble.properties` exists from an older version and `power-ruble.json5` does not, the mod reads the old file and creates a migrated JSON5 config. The old file is left in place as a backup.

The mod also creates `config/power-ruble-messages.properties`; edit it to change chat messages without rebuilding the mod.

After editing config or messages, restart the server or run `/ruble reload`.

## Build

Install JDK 17, then run:

```sh
./gradlew build
```

This local workspace also has a portable JDK and Gradle under `.tools/`, so it can be built without system packages:

```sh
JAVA_HOME="$PWD/.tools/jdk-17.0.18+8" .tools/gradle-8.8/bin/gradle build
```

The mod jar will be generated in `build/libs/`.
