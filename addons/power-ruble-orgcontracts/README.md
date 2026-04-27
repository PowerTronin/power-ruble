# Power Ruble Org Contracts

Optional addon module for `power-ruble`.

Server-side Fabric 1.20.1 addon that adds:
- player organizations with shared balances;
- organization membership and roles;
- item delivery contracts with escrow-backed rewards.

This addon requires the base `power-ruble` mod and is shipped as a separate jar.

## Commands

### Organizations

- `/org create <name>` - create an organization.
- `/org info` - show your organization overview.
- `/org members` - list members and roles.
- `/org balance` - show organization balance.
- `/org deposit <amount> [comment]` - transfer your money to the organization.
- `/org pay <player> <amount> [comment]` - pay a player from the organization balance; owner/manager only.
- `/org history` - show recent organization money operations.
- `/org invite <player>` - invite an online player; owner/manager only.
- `/org join` - accept your pending invite.
- `/org leave` - leave the organization.
- `/org kick <player>` - remove a member; manager can remove only members, owner can remove anyone except owner.
- `/org role <player> manager|member` - change role; owner only.

### Contracts

- `/contract create item <item> <count> <reward>` - create an item delivery contract and lock reward in escrow.
- `/contract list` - list all contracts.
- `/contract view <id>` - show one contract.
- `/contract accept <id>` - accept an open contract on behalf of your organization; owner/manager only.
- `/contract deliver <id> <amount>` - deliver matching items from your inventory into the contract.
- `/contract cancel <id>` - cancel a contract.
- `/contract history <id>` - show contract event history.

### Admin

- `/orgcontracts reload` - reload addon config.

## Contract rules

- reward is reserved immediately when the contract is created;
- accepted contracts belong to one organization at a time;
- delivery is partial, by item id only, without NBT matching;
- when the required amount is reached, escrow is paid to the organization balance;
- creator can cancel only `OPEN` contracts;
- operator can cancel `OPEN` and `ACCEPTED` contracts;
- on cancel, remaining escrow is returned to the creator.

## Config

The addon creates `config/power-ruble-orgcontracts.json5`:

```json5
{
  organizations: {
    enabled: true,
    allowPlayerCreate: true,
    minNameLength: 3,
    maxNameLength: 24
  },
  contracts: {
    enabled: true,
    allowPlayerCreate: true,
    minReward: 1,
    maxReward: 1000000,
    maxOpenPerPlayer: 3,
    maxAcceptedPerOrganization: 5
  }
}
```

- `organizations.enabled` - enable or disable all organization commands.
- `organizations.allowPlayerCreate` - whether regular players may create organizations.
- `organizations.minNameLength` / `maxNameLength` - allowed organization name length.
- `contracts.enabled` - enable or disable all contract commands.
- `contracts.allowPlayerCreate` - whether regular players may create contracts.
- `contracts.minReward` / `maxReward` - allowed reward range for one contract.
- `contracts.maxOpenPerPlayer` - limit of open contracts created by one player.
- `contracts.maxAcceptedPerOrganization` - limit of active accepted contracts per organization.

After editing config, run `/orgcontracts reload` or restart the server.

## Build

From repository root:

```sh
./gradlew :addons:power-ruble-orgcontracts:build
```

Addon jar:

```text
addons/power-ruble-orgcontracts/build/libs/power-ruble-orgcontracts-<version>.jar
```
