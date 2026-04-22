# Power Ruble

Server-side Fabric 1.20.1 economy mod.

## Commands

- `/balance` - show your balance.
- `/balance <player>` - show another player's balance, operator-only.
- `/pay <player> <amount>` - transfer currency to an online player.
- `/ruble help` - show mod commands.
- `/ruble give <player> <amount>` - add rubles, operator-only.
- `/ruble take <player> <amount>` - remove rubles, operator-only.
- `/ruble set <player> <amount>` - set balance, operator-only.
- `/ruble reload` - reload config, operator-only.

Player transfers can move the sender down to the configured debt limit. Operator `/ruble take` can move a player below zero without a fixed debt limit.

Balances are saved in the overworld persistent state and survive server restarts, including negative balances.

## Config

The mod creates `config/power-ruble.properties` on first launch:

```properties
max-transfer-amount=100000
transfer-debt-limit=-1000
currency-name=RUB
```

- `max-transfer-amount` - maximum amount for one `/pay`.
- `transfer-debt-limit` - minimum balance allowed after `/pay`.
- `currency-name` - text shown after amounts in chat messages.

After editing the config, restart the server or run `/ruble reload`.

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
