package ru.powerruble;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public final class RubleState extends PersistentState {
    private static final String STATE_KEY = PowerRubleMod.MOD_ID;
    private static final String BALANCES_KEY = "balances";
    private static final String NAMES_KEY = "names";
    private static final String HISTORY_KEY = "history";
    private static final String TRANSACTIONS_KEY = "transactions";
    private static final int MAX_HISTORY_ENTRIES = 20;
    private static final int MAX_GLOBAL_TRANSACTIONS = 500;

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final List<RubleTransaction> transactions = new ArrayList<>();

    public static RubleState get(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(RubleState::fromNbt, RubleState::new, STATE_KEY);
    }

    public static RubleState fromNbt(NbtCompound nbt) {
        RubleState state = new RubleState();
        NbtCompound balanceData = nbt.getCompound(BALANCES_KEY);

        for (String key : balanceData.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                state.balances.put(uuid, balanceData.getLong(key));
            } catch (IllegalArgumentException exception) {
                PowerRubleMod.LOGGER.warn("Skipping invalid ruble balance owner UUID '{}'", key);
            }
        }

        NbtCompound nameData = nbt.getCompound(NAMES_KEY);
        for (String key : nameData.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                state.names.put(uuid, nameData.getString(key));
            } catch (IllegalArgumentException exception) {
                PowerRubleMod.LOGGER.warn("Skipping invalid ruble name owner UUID '{}'", key);
            }
        }

        state.readTransactions(nbt);

        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound balanceData = new NbtCompound();
        balances.forEach((uuid, balance) -> balanceData.putLong(uuid.toString(), balance));
        nbt.put(BALANCES_KEY, balanceData);

        NbtCompound nameData = new NbtCompound();
        names.forEach((uuid, name) -> nameData.putString(uuid.toString(), name));
        nbt.put(NAMES_KEY, nameData);

        NbtList transactionData = new NbtList();
        transactions.forEach(transaction -> transactionData.add(transaction.toNbt()));
        nbt.put(TRANSACTIONS_KEY, transactionData);

        return nbt;
    }

    public long getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, 0L);
    }

    public String getName(UUID playerId) {
        return names.getOrDefault(playerId, playerId.toString());
    }

    public void rememberName(UUID playerId, String name) {
        if (name == null || name.isBlank()) {
            return;
        }

        names.put(playerId, name);
        markDirty();
    }

    public List<BalanceEntry> topBalances(int limit) {
        return balances.entrySet().stream()
            .sorted(Map.Entry.<UUID, Long>comparingByValue(Comparator.reverseOrder()))
            .limit(limit)
            .map(entry -> new BalanceEntry(entry.getKey(), getName(entry.getKey()), entry.getValue()))
            .toList();
    }

    public List<BalanceEntry> topDebts(int limit) {
        return balances.entrySet().stream()
            .filter(entry -> entry.getValue() < 0L)
            .sorted(Map.Entry.comparingByValue())
            .limit(limit)
            .map(entry -> new BalanceEntry(entry.getKey(), getName(entry.getKey()), entry.getValue()))
            .toList();
    }

    public List<String> getHistory(UUID playerId) {
        return getTransactions(playerId, PowerRubleMod.historyPerPlayerEntries()).stream()
            .map(transaction -> transaction.describe(PowerRubleMod.currencyName()))
            .toList();
    }

    public void addHistory(UUID playerId, String entry) {
        addTransaction(RubleTransaction.legacy(java.time.Instant.now(), playerId, getName(playerId), entry));
    }

    public List<RubleTransaction> getTransactions(UUID playerId) {
        return getTransactions(playerId, MAX_HISTORY_ENTRIES);
    }

    public List<RubleTransaction> getTransactions(UUID playerId, int limit) {
        return transactions.stream()
            .filter(transaction -> transaction.involves(playerId))
            .limit(limit)
            .toList();
    }

    public List<RubleTransaction> recentTransactions(int limit) {
        return transactions.stream()
            .limit(limit)
            .toList();
    }

    public void addTransaction(RubleTransaction transaction) {
        transactions.add(0, transaction);

        int maxTransactions = Math.max(MAX_GLOBAL_TRANSACTIONS, PowerRubleMod.historyGlobalEntries());
        while (transactions.size() > maxTransactions) {
            transactions.remove(transactions.size() - 1);
        }

        markDirty();
    }

    public void setBalance(UUID playerId, long amount) {
        if (amount == 0L) {
            balances.remove(playerId);
        } else {
            balances.put(playerId, amount);
        }
        markDirty();
    }

    public boolean add(UUID playerId, long amount) {
        long current = getBalance(playerId);
        if (Long.MAX_VALUE - current < amount) {
            return false;
        }

        setBalance(playerId, current + amount);
        return true;
    }

    public boolean subtract(UUID playerId, long amount) {
        long current = getBalance(playerId);
        if (current < amount) {
            return false;
        }

        setBalance(playerId, current - amount);
        return true;
    }

    public boolean subtractAllowingDebt(UUID playerId, long amount) {
        long current = getBalance(playerId);
        if (Long.MIN_VALUE + amount > current) {
            return false;
        }

        setBalance(playerId, current - amount);
        return true;
    }

    public TransferResult transfer(UUID senderId, UUID targetId, UUID feeRecipientId, long amount, long fee, long debtLimit) {
        if (Long.MAX_VALUE - amount < fee) {
            return TransferResult.NOT_ENOUGH_MONEY;
        }

        long totalCost = amount + fee;
        Map<UUID, Long> deltas = new HashMap<>();
        TransferResult deltaResult = addDelta(deltas, senderId, -totalCost, TransferResult.NOT_ENOUGH_MONEY);
        if (deltaResult != TransferResult.OK) {
            return deltaResult;
        }

        deltaResult = addDelta(deltas, targetId, amount, TransferResult.OVERFLOW);
        if (deltaResult != TransferResult.OK) {
            return deltaResult;
        }

        if (feeRecipientId != null && fee > 0L) {
            deltaResult = addDelta(deltas, feeRecipientId, fee, TransferResult.FEE_OVERFLOW);
            if (deltaResult != TransferResult.OK) {
                return deltaResult;
            }
        }

        Map<UUID, Long> nextBalances = new HashMap<>();
        for (Map.Entry<UUID, Long> entry : deltas.entrySet()) {
            UUID playerId = entry.getKey();
            long currentBalance = getBalance(playerId);
            long delta = entry.getValue();
            TransferResult result = validateBalanceChange(playerId, currentBalance, delta, feeRecipientId);
            if (result != TransferResult.OK) {
                return result;
            }

            long nextBalance = currentBalance + delta;
            if (playerId.equals(senderId) && nextBalance < debtLimit) {
                return TransferResult.NOT_ENOUGH_MONEY;
            }

            nextBalances.put(playerId, nextBalance);
        }

        nextBalances.forEach(this::setBalance);
        return TransferResult.OK;
    }

    private static TransferResult addDelta(Map<UUID, Long> deltas, UUID playerId, long delta, TransferResult overflowResult) {
        long currentDelta = deltas.getOrDefault(playerId, 0L);
        if (delta > 0L && currentDelta > Long.MAX_VALUE - delta) {
            return overflowResult;
        }

        if (delta < 0L && currentDelta < Long.MIN_VALUE - delta) {
            return TransferResult.NOT_ENOUGH_MONEY;
        }

        deltas.put(playerId, currentDelta + delta);
        return TransferResult.OK;
    }

    private static TransferResult validateBalanceChange(UUID playerId, long currentBalance, long delta, UUID feeRecipientId) {
        if (delta > 0L && currentBalance > Long.MAX_VALUE - delta) {
            return playerId.equals(feeRecipientId) ? TransferResult.FEE_OVERFLOW : TransferResult.OVERFLOW;
        }

        if (delta < 0L && currentBalance < Long.MIN_VALUE - delta) {
            return TransferResult.NOT_ENOUGH_MONEY;
        }

        return TransferResult.OK;
    }

    public record BalanceEntry(UUID playerId, String name, long balance) {
    }

    public enum TransferResult {
        OK,
        NOT_ENOUGH_MONEY,
        OVERFLOW,
        FEE_OVERFLOW
    }

    private void readTransactions(NbtCompound nbt) {
        if (nbt.contains(TRANSACTIONS_KEY, NbtElement.LIST_TYPE)) {
            NbtList transactionData = nbt.getList(TRANSACTIONS_KEY, NbtElement.COMPOUND_TYPE);
            for (int index = 0; index < transactionData.size(); index++) {
                transactions.add(RubleTransaction.fromNbt(transactionData.getCompound(index)));
            }
            return;
        }

        NbtCompound historyData = nbt.getCompound(HISTORY_KEY);
        for (String key : historyData.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtList list = historyData.getList(key, NbtString.STRING_TYPE);
                for (int index = 0; index < list.size(); index++) {
                    transactions.add(RubleTransaction.legacy(java.time.Instant.EPOCH, uuid, getName(uuid), list.getString(index)));
                }
            } catch (IllegalArgumentException exception) {
                PowerRubleMod.LOGGER.warn("Skipping invalid ruble history owner UUID '{}'", key);
            }
        }
    }
}
