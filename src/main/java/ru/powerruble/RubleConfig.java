package ru.powerruble;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class RubleConfig {
    private static final String CONFIG_FILE_NAME = PowerRubleMod.MOD_ID + ".properties";

    private static final long DEFAULT_MAX_TRANSFER_AMOUNT = 100_000L;
    private static final long DEFAULT_TRANSFER_DEBT_LIMIT = -1000L;
    private static final String DEFAULT_CURRENCY_NAME = "RUB";

    private final long maxTransferAmount;
    private final long transferDebtLimit;
    private final String currencyName;

    private RubleConfig(long maxTransferAmount, long transferDebtLimit, String currencyName) {
        this.maxTransferAmount = maxTransferAmount;
        this.transferDebtLimit = transferDebtLimit;
        this.currencyName = currencyName;
    }

    public static RubleConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(CONFIG_FILE_NAME);
        Properties properties = defaultProperties();

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            } catch (IOException exception) {
                PowerRubleMod.LOGGER.warn("Could not read {}, using defaults", path, exception);
            }
        } else {
            writeDefaults(path, properties);
        }

        long maxTransferAmount = readPositiveLong(properties, "max-transfer-amount", DEFAULT_MAX_TRANSFER_AMOUNT);
        long transferDebtLimit = readLong(properties, "transfer-debt-limit", DEFAULT_TRANSFER_DEBT_LIMIT);
        String currencyName = properties.getProperty("currency-name", DEFAULT_CURRENCY_NAME).trim();
        if (currencyName.isEmpty()) {
            currencyName = DEFAULT_CURRENCY_NAME;
        }

        return new RubleConfig(maxTransferAmount, transferDebtLimit, currencyName);
    }

    public long maxTransferAmount() {
        return maxTransferAmount;
    }

    public long transferDebtLimit() {
        return transferDebtLimit;
    }

    public String currencyName() {
        return currencyName;
    }

    private static Properties defaultProperties() {
        Properties properties = new Properties();
        properties.setProperty("max-transfer-amount", Long.toString(DEFAULT_MAX_TRANSFER_AMOUNT));
        properties.setProperty("transfer-debt-limit", Long.toString(DEFAULT_TRANSFER_DEBT_LIMIT));
        properties.setProperty("currency-name", DEFAULT_CURRENCY_NAME);
        return properties;
    }

    private static void writeDefaults(Path path, Properties properties) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "Power Ruble configuration");
            }
        } catch (IOException exception) {
            PowerRubleMod.LOGGER.warn("Could not create default config {}", path, exception);
        }
    }

    private static long readPositiveLong(Properties properties, String key, long fallback) {
        long value = readLong(properties, key, fallback);
        if (value < 1L) {
            PowerRubleMod.LOGGER.warn("Config '{}' must be at least 1, using {}", key, fallback);
            return fallback;
        }
        return value;
    }

    private static long readLong(Properties properties, String key, long fallback) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null) {
            return fallback;
        }

        try {
            return Long.parseLong(rawValue.trim());
        } catch (NumberFormatException exception) {
            PowerRubleMod.LOGGER.warn("Config '{}' has invalid value '{}', using {}", key, rawValue, fallback);
            return fallback;
        }
    }
}
