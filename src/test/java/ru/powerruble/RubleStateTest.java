package ru.powerruble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.Test;

final class RubleStateTest {
    private static final UUID SENDER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FEE_RECIPIENT = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @Test
    void transferMovesAmountAndFeeAtomically() {
        RubleState state = new RubleState();
        state.setBalance(SENDER, 100L);

        RubleState.TransferResult result = state.transfer(SENDER, TARGET, FEE_RECIPIENT, 75L, 5L, -10L);

        assertEquals(RubleState.TransferResult.OK, result);
        assertEquals(20L, state.getBalance(SENDER));
        assertEquals(75L, state.getBalance(TARGET));
        assertEquals(5L, state.getBalance(FEE_RECIPIENT));
    }

    @Test
    void transferRejectsDebtLimitWithoutChangingBalances() {
        RubleState state = new RubleState();
        state.setBalance(SENDER, 10L);

        RubleState.TransferResult result = state.transfer(SENDER, TARGET, null, 11L, 0L, 0L);

        assertEquals(RubleState.TransferResult.NOT_ENOUGH_MONEY, result);
        assertEquals(10L, state.getBalance(SENDER));
        assertEquals(0L, state.getBalance(TARGET));
    }

    @Test
    void transferRejectsTargetOverflowWithoutChangingBalances() {
        RubleState state = new RubleState();
        state.setBalance(SENDER, 100L);
        state.setBalance(TARGET, Long.MAX_VALUE);

        RubleState.TransferResult result = state.transfer(SENDER, TARGET, null, 1L, 0L, 0L);

        assertEquals(RubleState.TransferResult.OVERFLOW, result);
        assertEquals(100L, state.getBalance(SENDER));
        assertEquals(Long.MAX_VALUE, state.getBalance(TARGET));
    }

    @Test
    void transferRejectsFeeRecipientOverflowWithoutChangingBalances() {
        RubleState state = new RubleState();
        state.setBalance(SENDER, 100L);
        state.setBalance(TARGET, 0L);
        state.setBalance(FEE_RECIPIENT, Long.MAX_VALUE);

        RubleState.TransferResult result = state.transfer(SENDER, TARGET, FEE_RECIPIENT, 10L, 1L, 0L);

        assertEquals(RubleState.TransferResult.FEE_OVERFLOW, result);
        assertEquals(100L, state.getBalance(SENDER));
        assertEquals(0L, state.getBalance(TARGET));
        assertEquals(Long.MAX_VALUE, state.getBalance(FEE_RECIPIENT));
    }

    @Test
    void transferRejectsCombinedTargetAndFeeOverflowWithoutChangingBalances() {
        RubleState state = new RubleState();
        state.setBalance(SENDER, 100L);
        state.setBalance(TARGET, Long.MAX_VALUE - 10L);

        RubleState.TransferResult result = state.transfer(SENDER, TARGET, TARGET, 10L, 1L, 0L);

        assertEquals(RubleState.TransferResult.FEE_OVERFLOW, result);
        assertEquals(100L, state.getBalance(SENDER));
        assertEquals(Long.MAX_VALUE - 10L, state.getBalance(TARGET));
    }

    @Test
    void transferChargesOnlyNetDeltaWhenSenderReceivesFee() {
        RubleState state = new RubleState();
        state.setBalance(SENDER, 100L);

        RubleState.TransferResult result = state.transfer(SENDER, TARGET, SENDER, 40L, 5L, 0L);

        assertEquals(RubleState.TransferResult.OK, result);
        assertEquals(60L, state.getBalance(SENDER));
        assertEquals(40L, state.getBalance(TARGET));
    }

    @Test
    void historyKeepsTwentyNewestEntries() {
        RubleState state = new RubleState();

        for (int index = 1; index <= 25; index++) {
            state.addHistory(SENDER, "entry-" + index);
        }

        assertEquals(20, state.getHistory(SENDER).size());
        assertEquals("entry-25", state.getHistory(SENDER).get(0));
        assertEquals("entry-6", state.getHistory(SENDER).get(19));
    }

    @Test
    void zeroBalanceIsNotIncludedInTopBalances() {
        RubleState state = new RubleState();
        state.setBalance(SENDER, 10L);
        state.setBalance(TARGET, 0L);

        assertEquals(1, state.topBalances(10).size());
        assertTrue(state.topBalances(10).stream().anyMatch(entry -> entry.playerId().equals(SENDER)));
        assertFalse(state.topBalances(10).stream().anyMatch(entry -> entry.playerId().equals(TARGET)));
    }

    @Test
    void topDebtsReturnsNegativeBalancesFirst() {
        RubleState state = new RubleState();
        state.setBalance(SENDER, -10L);
        state.setBalance(TARGET, -50L);
        state.setBalance(FEE_RECIPIENT, 100L);

        var debts = state.topDebts(10);

        assertEquals(2, debts.size());
        assertEquals(TARGET, debts.get(0).playerId());
        assertEquals(-50L, debts.get(0).balance());
        assertEquals(SENDER, debts.get(1).playerId());
        assertEquals(-10L, debts.get(1).balance());
    }

    @Test
    void structuredTransactionsRoundTripThroughNbt() {
        RubleState state = new RubleState();
        state.rememberName(SENDER, "sender");
        state.rememberName(TARGET, "target");
        state.rememberName(FEE_RECIPIENT, "bank");
        state.addTransaction(RubleTransaction.transfer(
            Instant.parse("2026-04-22T00:00:00Z"),
            SENDER,
            "sender",
            TARGET,
            "target",
            25L,
            3L,
            Optional.of(FEE_RECIPIENT),
            "bank",
            ""
        ));

        RubleState loaded = RubleState.fromNbt(state.writeNbt(new NbtCompound()));

        assertEquals(1, loaded.getTransactions(SENDER).size());
        assertEquals(1, loaded.getTransactions(TARGET).size());
        assertEquals(1, loaded.getTransactions(FEE_RECIPIENT).size());
        RubleTransaction transaction = loaded.getTransactions(SENDER).get(0);
        assertEquals(RubleTransaction.Type.TRANSFER, transaction.type());
        assertEquals(25L, transaction.amount());
        assertEquals(3L, transaction.fee());
        assertEquals(Optional.of(FEE_RECIPIENT), transaction.feeRecipientId());
    }

    @Test
    void adminTakeTransactionKeepsReason() {
        RubleTransaction transaction = RubleTransaction.admin(
            RubleTransaction.Type.ADMIN_TAKE,
            Instant.parse("2026-04-22T00:00:00Z"),
            TARGET,
            "target",
            15L,
            "штраф"
        );

        assertEquals("штраф", transaction.reason());
        assertTrue(transaction.describe("RUB").contains("reason: штраф"));
    }

    @Test
    void transferTransactionKeepsComment() {
        RubleTransaction transaction = RubleTransaction.transfer(
            Instant.parse("2026-04-22T00:00:00Z"),
            SENDER,
            "sender",
            TARGET,
            "target",
            15L,
            0L,
            Optional.empty(),
            "",
            "за товар"
        );

        assertEquals("за товар", transaction.reason());
        assertTrue(transaction.describe("RUB").contains("reason: за товар"));
    }
}
