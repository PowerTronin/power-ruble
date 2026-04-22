# Power Ruble

Server-side Fabric 1.20.1 economy mod.

## Commands

- `/ruble balance` - show your balance.
- `/ruble balance <player>` - show another player's balance, operator-only.
- `/ruble pay <player> <amount>` - transfer rubles to an online player.
- `/ruble give <player> <amount>` - add rubles, operator-only.
- `/ruble take <player> <amount>` - remove rubles, operator-only.
- `/ruble set <player> <amount>` - set balance, operator-only.

Player transfers can move the sender down to `-1000 RUB`. Operator `/ruble take` can move a player below zero without a fixed debt limit.

Balances are saved in the overworld persistent state and survive server restarts, including negative balances.

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
