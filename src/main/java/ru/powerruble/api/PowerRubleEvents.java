package ru.powerruble.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.MinecraftServer;
import ru.powerruble.PowerRubleMod;
import ru.powerruble.RubleTransaction;

public final class PowerRubleEvents {
    private static final List<TransactionListener> TRANSACTION_LISTENERS = new CopyOnWriteArrayList<>();

    private PowerRubleEvents() {
    }

    public static AutoCloseable registerTransactionListener(TransactionListener listener) {
        TRANSACTION_LISTENERS.add(listener);
        return () -> TRANSACTION_LISTENERS.remove(listener);
    }

    public static void notifyTransaction(MinecraftServer server, RubleTransaction transaction) {
        for (TransactionListener listener : TRANSACTION_LISTENERS) {
            try {
                listener.onTransaction(server, transaction);
            } catch (RuntimeException exception) {
                PowerRubleMod.LOGGER.warn("Power Ruble transaction listener failed", exception);
            }
        }
    }

    @FunctionalInterface
    public interface TransactionListener {
        void onTransaction(MinecraftServer server, RubleTransaction transaction);
    }
}
