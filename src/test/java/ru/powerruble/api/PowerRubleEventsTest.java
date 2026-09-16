package ru.powerruble.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import ru.powerruble.RubleTransaction;

final class PowerRubleEventsTest {
    @Test
    void brokenListenerDoesNotBlockOtherListeners() throws Exception {
        AtomicBoolean called = new AtomicBoolean(false);
        RubleTransaction transaction = RubleTransaction.transfer(
            Instant.EPOCH,
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "sender",
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            "target",
            1L,
            0L,
            Optional.empty(),
            "",
            ""
        );

        AutoCloseable brokenListener = PowerRubleEvents.registerTransactionListener((server, event) -> {
            throw new IllegalStateException("boom");
        });
        AutoCloseable workingListener = PowerRubleEvents.registerTransactionListener((server, event) -> called.set(true));

        try {
            assertDoesNotThrow(() -> PowerRubleEvents.notifyTransaction(null, transaction));
            assertTrue(called.get());
        } finally {
            brokenListener.close();
            workingListener.close();
        }
    }
}
