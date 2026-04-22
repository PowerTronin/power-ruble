# Power Ruble

Server-side Fabric 1.20.1 economy mod.

## Commands

- `/balance` - show your balance.
- `/balance <player>` - show another player's balance, operator-only.
- `/pay <player> <amount>` - transfer currency to a player, including offline players known to the server.
- `/topbalance` - show the richest balances. Can be disabled for regular players in config.
- `/ruble help` - show mod commands.
- `/ruble give <player> <amount>` - add rubles, operator-only.
- `/ruble take <player> <amount>` - remove rubles, operator-only.
- `/ruble set <player> <amount>` - set balance, operator-only.
- `/ruble history <player>` - show recent economy operations, operator-only.
- `/ruble reload` - reload config, operator-only.

Player transfers can move the sender down to the configured debt limit. Operator `/ruble take` can move a player below zero without a fixed debt limit.

Balances are saved in the overworld persistent state and survive server restarts, including negative balances.

## Config

The mod creates `config/power-ruble.properties` on first launch:

```properties
max-transfer-amount=100000
min-transfer-amount=1
transfer-debt-limit=-1000
transfer-fee-amount=0
transfer-fee-recipient=exchange
currency-name=RUB
top-balance-players-enabled=true
top-balance-size=10
```

- `max-transfer-amount` - maximum amount for one `/pay`.
- `min-transfer-amount` - minimum amount for one `/pay`.
- `transfer-debt-limit` - minimum balance allowed after `/pay`.
- `transfer-fee-amount` - fixed fee charged on each `/pay`.
- `transfer-fee-recipient` - `exchange` removes the fee from circulation; any other value is treated as a player name that receives the fee.
- `currency-name` - text shown after amounts in chat messages.
- `top-balance-players-enabled` - whether regular players can use `/topbalance`; operators can always use it.
- `top-balance-size` - number of entries shown by `/topbalance`.

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
