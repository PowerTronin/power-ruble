package ru.powerruble;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RubleConfigTest {
    @TempDir
    private Path tempDir;

    @Test
    void loadCreatesJson5DefaultsWhenNoConfigExists() throws IOException {
        RubleConfig config = RubleConfig.load(tempDir);

        Path path = tempDir.resolve("power-ruble.json5");
        assertTrue(Files.exists(path));
        assertEquals("RUB", config.currencyName());
        assertEquals(100_000L, config.maxTransferAmount());
        assertTrue(Files.readString(path).contains("// Text appended after money amounts"));
    }

    @Test
    void loadReadsJson5WithComments() throws IOException {
        Files.writeString(tempDir.resolve("power-ruble.json5"), """
            {
              // Human-readable currency label.
              currency: {
                name: "руб.",
              },
              transfers: {
                minAmount: 2,
                maxAmount: 5000,
                debtLimit: -250,
                confirmAbove: 1000,
                fee: {
                  fixed: 7,
                  percent: 1.5,
                  min: 1,
                  max: 50,
                  recipient: "bank",
                },
              },
              top: {
                playersEnabled: false,
                size: 5,
                showDebtTop: true,
              },
              bank: {
                enabled: true,
                accountName: "treasury",
              },
              limits: {
                payCooldownSeconds: 3,
                dailyTransferLimit: 10000,
              },
              history: {
                perPlayerEntries: 25,
                globalEntries: 1000,
              },
              taxes: {
                enabled: true,
                intervalMinutes: 60,
                wealthTaxPercent: 2.25,
                minimumBalance: 100,
              },
            }
            """);

        RubleConfig config = RubleConfig.load(tempDir);

        assertEquals("руб.", config.currencyName());
        assertEquals(2L, config.minTransferAmount());
        assertEquals(5000L, config.maxTransferAmount());
        assertEquals(-250L, config.transferDebtLimit());
        assertEquals(1000L, config.transferConfirmAbove());
        assertEquals(7L, config.transferFeeAmount());
        assertEquals(1.5D, config.transferFeePercent());
        assertEquals(1L, config.transferFeeMin());
        assertEquals(50L, config.transferFeeMax());
        assertEquals("bank", config.transferFeeRecipient());
        assertEquals(false, config.topBalancePlayersEnabled());
        assertEquals(5, config.topBalanceSize());
        assertEquals("treasury", config.bankAccountName());
        assertEquals(3, config.payCooldownSeconds());
        assertEquals(10000L, config.dailyTransferLimit());
        assertEquals(25, config.historyPerPlayerEntries());
        assertEquals(1000, config.historyGlobalEntries());
        assertEquals(true, config.taxesEnabled());
        assertEquals(60, config.taxIntervalMinutes());
        assertEquals(2.25D, config.wealthTaxPercent());
        assertEquals(100L, config.taxMinimumBalance());
    }

    @Test
    void loadMigratesLegacyPropertiesToJson5() throws IOException {
        Files.writeString(tempDir.resolve("power-ruble.properties"), """
            max-transfer-amount=9000
            min-transfer-amount=3
            transfer-debt-limit=-500
            transfer-fee-amount=9
            transfer-fee-recipient=exchange
            currency-name=PR
            top-balance-players-enabled=false
            top-balance-size=4
            """);

        RubleConfig config = RubleConfig.load(tempDir);

        assertEquals("PR", config.currencyName());
        assertEquals(3L, config.minTransferAmount());
        assertEquals(9000L, config.maxTransferAmount());
        assertEquals(-500L, config.transferDebtLimit());
        assertEquals(9L, config.transferFeeAmount());
        assertEquals(false, config.topBalancePlayersEnabled());
        assertEquals(4, config.topBalanceSize());
        assertTrue(Files.exists(tempDir.resolve("power-ruble.json5")));
        assertTrue(Files.exists(tempDir.resolve("power-ruble.properties")));
    }

    @Test
    void transferFeeUsesFixedPercentMinAndMax() throws IOException {
        Files.writeString(tempDir.resolve("power-ruble.json5"), """
            {
              transfers: {
                minAmount: 1,
                maxAmount: 100000,
                debtLimit: 0,
                fee: {
                  fixed: 5,
                  percent: 10.0,
                  min: 20,
                  max: 50,
                  recipient: "bank",
                },
              },
            }
            """);

        RubleConfig config = RubleConfig.load(tempDir);

        assertEquals(20L, PowerRubleMod.calculateTransferFee(100L, config));
        assertEquals(35L, PowerRubleMod.calculateTransferFee(300L, config));
        assertEquals(50L, PowerRubleMod.calculateTransferFee(1000L, config));
    }
}
