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
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public final class RubleState extends PersistentState {
    private static final String STATE_KEY = PowerRubleMod.MOD_ID;
    private static final String BALANCES_KEY = "balances";
    private static final String NAMES_KEY = "names";
    private static final String HISTORY_KEY = "history";
    private static final int MAX_HISTORY_ENTRIES = 20;

    private final Map<UUID, Long> balances = new HashMap<>();
    private final Map<UUID, String> names = new HashMap<>();
    private final Map<UUID, List<String>> history = new HashMap<>();

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

        NbtCompound historyData = nbt.getCompound(HISTORY_KEY);
        for (String key : historyData.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                NbtList list = historyData.getList(key, NbtString.STRING_TYPE);
                List<String> entries = new ArrayList<>();
                for (int index = 0; index < list.size(); index++) {
                    entries.add(list.getString(index));
                }
                state.history.put(uuid, entries);
            } catch (IllegalArgumentException exception) {
                PowerRubleMod.LOGGER.warn("Skipping invalid ruble history owner UUID '{}'", key);
            }
        }

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

        NbtCompound historyData = new NbtCompound();
        history.forEach((uuid, entries) -> {
            NbtList list = new NbtList();
            entries.forEach(entry -> list.add(NbtString.of(entry)));
            historyData.put(uuid.toString(), list);
        });
        nbt.put(HISTORY_KEY, historyData);

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
        return List.copyOf(history.getOrDefault(playerId, List.of()));
    }

    public void addHistory(UUID playerId, String entry) {
        List<String> entries = history.computeIfAbsent(playerId, ignored -> new ArrayList<>());
        entries.add(0, entry);

        while (entries.size() > MAX_HISTORY_ENTRIES) {
            entries.remove(entries.size() - 1);
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
        long senderBalance = getBalance(senderId);
        if (debtLimit > Long.MAX_VALUE - totalCost) {
            return TransferResult.NOT_ENOUGH_MONEY;
        }

        if (senderBalance < debtLimit + totalCost) {
            return TransferResult.NOT_ENOUGH_MONEY;
        }

        long targetBalance = getBalance(targetId);
        if (Long.MAX_VALUE - targetBalance < amount) {
            return TransferResult.OVERFLOW;
        }

        if (feeRecipientId != null) {
            long feeRecipientBalance = getBalance(feeRecipientId);
            if (Long.MAX_VALUE - feeRecipientBalance < fee) {
                return TransferResult.FEE_OVERFLOW;
            }
        }

        setBalance(senderId, senderBalance - totalCost);
        setBalance(targetId, targetBalance + amount);
        if (feeRecipientId != null && fee > 0L) {
            setBalance(feeRecipientId, getBalance(feeRecipientId) + fee);
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
}
