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
    private static final long DEFAULT_MIN_TRANSFER_AMOUNT = 1L;
    private static final long DEFAULT_TRANSFER_DEBT_LIMIT = -1000L;
    private static final long DEFAULT_TRANSFER_FEE_AMOUNT = 0L;
    private static final String DEFAULT_CURRENCY_NAME = "RUB";
    private static final String DEFAULT_TRANSFER_FEE_RECIPIENT = "exchange";
    private static final boolean DEFAULT_TOP_BALANCE_PLAYERS_ENABLED = true;
    private static final int DEFAULT_TOP_BALANCE_SIZE = 10;

    private final long maxTransferAmount;
    private final long minTransferAmount;
    private final long transferDebtLimit;
    private final long transferFeeAmount;
    private final String transferFeeRecipient;
    private final String currencyName;
    private final boolean topBalancePlayersEnabled;
    private final int topBalanceSize;

    private RubleConfig(
        long maxTransferAmount,
        long minTransferAmount,
        long transferDebtLimit,
        long transferFeeAmount,
        String transferFeeRecipient,
        String currencyName,
        boolean topBalancePlayersEnabled,
        int topBalanceSize
    ) {
        this.maxTransferAmount = maxTransferAmount;
        this.minTransferAmount = minTransferAmount;
        this.transferDebtLimit = transferDebtLimit;
        this.transferFeeAmount = transferFeeAmount;
        this.transferFeeRecipient = transferFeeRecipient;
        this.currencyName = currencyName;
        this.topBalancePlayersEnabled = topBalancePlayersEnabled;
        this.topBalanceSize = topBalanceSize;
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
        long minTransferAmount = readPositiveLong(properties, "min-transfer-amount", DEFAULT_MIN_TRANSFER_AMOUNT);
        if (minTransferAmount > maxTransferAmount) {
            PowerRubleMod.LOGGER.warn("Config 'min-transfer-amount' is greater than 'max-transfer-amount', using {}", DEFAULT_MIN_TRANSFER_AMOUNT);
            minTransferAmount = DEFAULT_MIN_TRANSFER_AMOUNT;
        }

        long transferDebtLimit = readLong(properties, "transfer-debt-limit", DEFAULT_TRANSFER_DEBT_LIMIT);
        long transferFeeAmount = readNonNegativeLong(properties, "transfer-fee-amount", DEFAULT_TRANSFER_FEE_AMOUNT);
        String transferFeeRecipient = properties.getProperty("transfer-fee-recipient", DEFAULT_TRANSFER_FEE_RECIPIENT).trim();
        if (transferFeeRecipient.isEmpty()) {
            transferFeeRecipient = DEFAULT_TRANSFER_FEE_RECIPIENT;
        }

        String currencyName = properties.getProperty("currency-name", DEFAULT_CURRENCY_NAME).trim();
        if (currencyName.isEmpty()) {
            currencyName = DEFAULT_CURRENCY_NAME;
        }

        boolean topBalancePlayersEnabled = readBoolean(properties, "top-balance-players-enabled", DEFAULT_TOP_BALANCE_PLAYERS_ENABLED);
        int topBalanceSize = readPositiveInt(properties, "top-balance-size", DEFAULT_TOP_BALANCE_SIZE);

        return new RubleConfig(
            maxTransferAmount,
            minTransferAmount,
            transferDebtLimit,
            transferFeeAmount,
            transferFeeRecipient,
            currencyName,
            topBalancePlayersEnabled,
            topBalanceSize
        );
    }

    public long maxTransferAmount() {
        return maxTransferAmount;
    }

    public long minTransferAmount() {
        return minTransferAmount;
    }

    public long transferDebtLimit() {
        return transferDebtLimit;
    }

    public long transferFeeAmount() {
        return transferFeeAmount;
    }

    public String transferFeeRecipient() {
        return transferFeeRecipient;
    }

    public boolean transferFeeGoesToExchange() {
        return "exchange".equalsIgnoreCase(transferFeeRecipient);
    }

    public String currencyName() {
        return currencyName;
    }

    public boolean topBalancePlayersEnabled() {
        return topBalancePlayersEnabled;
    }

    public int topBalanceSize() {
        return topBalanceSize;
    }

    private static Properties defaultProperties() {
        Properties properties = new Properties();
        properties.setProperty("max-transfer-amount", Long.toString(DEFAULT_MAX_TRANSFER_AMOUNT));
        properties.setProperty("min-transfer-amount", Long.toString(DEFAULT_MIN_TRANSFER_AMOUNT));
        properties.setProperty("transfer-debt-limit", Long.toString(DEFAULT_TRANSFER_DEBT_LIMIT));
        properties.setProperty("transfer-fee-amount", Long.toString(DEFAULT_TRANSFER_FEE_AMOUNT));
        properties.setProperty("transfer-fee-recipient", DEFAULT_TRANSFER_FEE_RECIPIENT);
        properties.setProperty("currency-name", DEFAULT_CURRENCY_NAME);
        properties.setProperty("top-balance-players-enabled", Boolean.toString(DEFAULT_TOP_BALANCE_PLAYERS_ENABLED));
        properties.setProperty("top-balance-size", Integer.toString(DEFAULT_TOP_BALANCE_SIZE));
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

    private static long readNonNegativeLong(Properties properties, String key, long fallback) {
        long value = readLong(properties, key, fallback);
        if (value < 0L) {
            PowerRubleMod.LOGGER.warn("Config '{}' must not be negative, using {}", key, fallback);
            return fallback;
        }
        return value;
    }

    private static int readPositiveInt(Properties properties, String key, int fallback) {
        long value = readPositiveLong(properties, key, fallback);
        if (value > Integer.MAX_VALUE) {
            PowerRubleMod.LOGGER.warn("Config '{}' is too large, using {}", key, fallback);
            return fallback;
        }
        return (int) value;
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String rawValue = properties.getProperty(key);
        if (rawValue == null) {
            return fallback;
        }

        String normalized = rawValue.trim().toLowerCase();
        if ("true".equals(normalized)) {
            return true;
        }

        if ("false".equals(normalized)) {
            return false;
        }

        PowerRubleMod.LOGGER.warn("Config '{}' has invalid value '{}', using {}", key, rawValue, fallback);
        return fallback;
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
