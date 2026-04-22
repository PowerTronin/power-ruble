package ru.powerruble;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

public final class RubleState extends PersistentState {
    public static final long TRANSFER_DEBT_LIMIT = -1000L;

    private static final String STATE_KEY = PowerRubleMod.MOD_ID;
    private static final String BALANCES_KEY = "balances";

    private final Map<UUID, Long> balances = new HashMap<>();

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

        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound balanceData = new NbtCompound();
        balances.forEach((uuid, balance) -> balanceData.putLong(uuid.toString(), balance));
        nbt.put(BALANCES_KEY, balanceData);
        return nbt;
    }

    public long getBalance(UUID playerId) {
        return balances.getOrDefault(playerId, 0L);
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

    public TransferResult transfer(UUID senderId, UUID targetId, long amount) {
        long senderBalance = getBalance(senderId);
        if (senderBalance < TRANSFER_DEBT_LIMIT + amount) {
            return TransferResult.NOT_ENOUGH_MONEY;
        }

        long targetBalance = getBalance(targetId);
        if (Long.MAX_VALUE - targetBalance < amount) {
            return TransferResult.OVERFLOW;
        }

        setBalance(senderId, senderBalance - amount);
        setBalance(targetId, targetBalance + amount);
        return TransferResult.OK;
    }

    public enum TransferResult {
        OK,
        NOT_ENOUGH_MONEY,
        OVERFLOW
    }
}
