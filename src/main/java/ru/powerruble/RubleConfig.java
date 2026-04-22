package ru.powerruble;

import blue.endless.jankson.Jankson;
import blue.endless.jankson.JsonElement;
import blue.endless.jankson.JsonObject;
import blue.endless.jankson.JsonPrimitive;
import blue.endless.jankson.api.SyntaxError;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class RubleConfig {
    private static final String CONFIG_FILE_NAME = PowerRubleMod.MOD_ID + ".json5";
    private static final String LEGACY_CONFIG_FILE_NAME = PowerRubleMod.MOD_ID + ".properties";

    private static final long DEFAULT_MAX_TRANSFER_AMOUNT = 100_000L;
    private static final long DEFAULT_MIN_TRANSFER_AMOUNT = 1L;
    private static final long DEFAULT_TRANSFER_DEBT_LIMIT = -1000L;
    private static final long DEFAULT_TRANSFER_CONFIRM_ABOVE = 0L;
    private static final long DEFAULT_TRANSFER_FEE_AMOUNT = 0L;
    private static final double DEFAULT_TRANSFER_FEE_PERCENT = 0.0D;
    private static final long DEFAULT_TRANSFER_FEE_MIN = 0L;
    private static final long DEFAULT_TRANSFER_FEE_MAX = 0L;
    private static final String DEFAULT_TRANSFER_FEE_RECIPIENT = "exchange";
    private static final String DEFAULT_CURRENCY_NAME = "RUB";
    private static final boolean DEFAULT_TOP_BALANCE_PLAYERS_ENABLED = true;
    private static final int DEFAULT_TOP_BALANCE_SIZE = 10;
    private static final boolean DEFAULT_TOP_DEBT_ENABLED = true;
    private static final boolean DEFAULT_BANK_ENABLED = true;
    private static final String DEFAULT_BANK_ACCOUNT_NAME = "bank";
    private static final int DEFAULT_PAY_COOLDOWN_SECONDS = 0;
    private static final long DEFAULT_DAILY_TRANSFER_LIMIT = 0L;
    private static final int DEFAULT_HISTORY_PER_PLAYER_ENTRIES = 50;
    private static final int DEFAULT_HISTORY_GLOBAL_ENTRIES = 500;
    private static final boolean DEFAULT_TAXES_ENABLED = false;
    private static final int DEFAULT_TAX_INTERVAL_MINUTES = 1440;
    private static final double DEFAULT_WEALTH_TAX_PERCENT = 0.0D;
    private static final long DEFAULT_TAX_MINIMUM_BALANCE = 0L;

    private final long maxTransferAmount;
    private final long minTransferAmount;
    private final long transferDebtLimit;
    private final long transferConfirmAbove;
    private final long transferFeeAmount;
    private final double transferFeePercent;
    private final long transferFeeMin;
    private final long transferFeeMax;
    private final String transferFeeRecipient;
    private final String currencyName;
    private final boolean topBalancePlayersEnabled;
    private final int topBalanceSize;
    private final boolean topDebtEnabled;
    private final boolean bankEnabled;
    private final String bankAccountName;
    private final int payCooldownSeconds;
    private final long dailyTransferLimit;
    private final int historyPerPlayerEntries;
    private final int historyGlobalEntries;
    private final boolean taxesEnabled;
    private final int taxIntervalMinutes;
    private final double wealthTaxPercent;
    private final long taxMinimumBalance;

    private RubleConfig(
        long maxTransferAmount,
        long minTransferAmount,
        long transferDebtLimit,
        long transferConfirmAbove,
        long transferFeeAmount,
        double transferFeePercent,
        long transferFeeMin,
        long transferFeeMax,
        String transferFeeRecipient,
        String currencyName,
        boolean topBalancePlayersEnabled,
        int topBalanceSize,
        boolean topDebtEnabled,
        boolean bankEnabled,
        String bankAccountName,
        int payCooldownSeconds,
        long dailyTransferLimit,
        int historyPerPlayerEntries,
        int historyGlobalEntries,
        boolean taxesEnabled,
        int taxIntervalMinutes,
        double wealthTaxPercent,
        long taxMinimumBalance
    ) {
        this.maxTransferAmount = maxTransferAmount;
        this.minTransferAmount = minTransferAmount;
        this.transferDebtLimit = transferDebtLimit;
        this.transferConfirmAbove = transferConfirmAbove;
        this.transferFeeAmount = transferFeeAmount;
        this.transferFeePercent = transferFeePercent;
        this.transferFeeMin = transferFeeMin;
        this.transferFeeMax = transferFeeMax;
        this.transferFeeRecipient = transferFeeRecipient;
        this.currencyName = currencyName;
        this.topBalancePlayersEnabled = topBalancePlayersEnabled;
        this.topBalanceSize = topBalanceSize;
        this.topDebtEnabled = topDebtEnabled;
        this.bankEnabled = bankEnabled;
        this.bankAccountName = bankAccountName;
        this.payCooldownSeconds = payCooldownSeconds;
        this.dailyTransferLimit = dailyTransferLimit;
        this.historyPerPlayerEntries = historyPerPlayerEntries;
        this.historyGlobalEntries = historyGlobalEntries;
        this.taxesEnabled = taxesEnabled;
        this.taxIntervalMinutes = taxIntervalMinutes;
        this.wealthTaxPercent = wealthTaxPercent;
        this.taxMinimumBalance = taxMinimumBalance;
    }

    public static RubleConfig load() {
        return load(FabricLoader.getInstance().getConfigDir());
    }

    static RubleConfig load(Path configDir) {
        Path path = configDir.resolve(CONFIG_FILE_NAME);
        if (Files.exists(path)) {
            RubleConfig config = readJson5(path);
            if (config != null) {
                return config;
            }

            return defaults();
        }

        RubleConfig config = readLegacyProperties(configDir.resolve(LEGACY_CONFIG_FILE_NAME));
        writeJson5(path, config);
        return config;
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

    public long transferConfirmAbove() {
        return transferConfirmAbove;
    }

    public long transferFeeAmount() {
        return transferFeeAmount;
    }

    public double transferFeePercent() {
        return transferFeePercent;
    }

    public long transferFeeMin() {
        return transferFeeMin;
    }

    public long transferFeeMax() {
        return transferFeeMax;
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

    public boolean topDebtEnabled() {
        return topDebtEnabled;
    }

    public boolean bankEnabled() {
        return bankEnabled;
    }

    public String bankAccountName() {
        return bankAccountName;
    }

    public int payCooldownSeconds() {
        return payCooldownSeconds;
    }

    public long dailyTransferLimit() {
        return dailyTransferLimit;
    }

    public int historyPerPlayerEntries() {
        return historyPerPlayerEntries;
    }

    public int historyGlobalEntries() {
        return historyGlobalEntries;
    }

    public boolean taxesEnabled() {
        return taxesEnabled;
    }

    public int taxIntervalMinutes() {
        return taxIntervalMinutes;
    }

    public double wealthTaxPercent() {
        return wealthTaxPercent;
    }

    public long taxMinimumBalance() {
        return taxMinimumBalance;
    }

    private static RubleConfig readJson5(Path path) {
        try {
            JsonObject root = Jankson.builder().build().load(path.toFile());
            return fromJson(root);
        } catch (IOException | SyntaxError exception) {
            PowerRubleMod.LOGGER.warn("Could not read {}, using defaults", path, exception);
            return null;
        }
    }

    private static RubleConfig fromJson(JsonObject root) {
        JsonObject currency = section(root, "currency");
        JsonObject transfers = section(root, "transfers");
        JsonObject fee = section(transfers, "fee");
        JsonObject top = section(root, "top");
        JsonObject bank = section(root, "bank");
        JsonObject limits = section(root, "limits");
        JsonObject history = section(root, "history");
        JsonObject taxes = section(root, "taxes");

        long maxTransferAmount = positiveLong(transfers, "maxAmount", DEFAULT_MAX_TRANSFER_AMOUNT);
        long minTransferAmount = positiveLong(transfers, "minAmount", DEFAULT_MIN_TRANSFER_AMOUNT);
        if (minTransferAmount > maxTransferAmount) {
            PowerRubleMod.LOGGER.warn("Config 'transfers.minAmount' is greater than 'transfers.maxAmount', using {}", DEFAULT_MIN_TRANSFER_AMOUNT);
            minTransferAmount = DEFAULT_MIN_TRANSFER_AMOUNT;
        }

        long transferDebtLimit = longValue(transfers, "debtLimit", DEFAULT_TRANSFER_DEBT_LIMIT);
        long transferConfirmAbove = nonNegativeLong(transfers, "confirmAbove", DEFAULT_TRANSFER_CONFIRM_ABOVE);
        long transferFeeAmount = nonNegativeLong(fee, "fixed", DEFAULT_TRANSFER_FEE_AMOUNT);
        double transferFeePercent = nonNegativeDouble(fee, "percent", DEFAULT_TRANSFER_FEE_PERCENT);
        long transferFeeMin = nonNegativeLong(fee, "min", DEFAULT_TRANSFER_FEE_MIN);
        long transferFeeMax = nonNegativeLong(fee, "max", DEFAULT_TRANSFER_FEE_MAX);
        if (transferFeeMax > 0L && transferFeeMin > transferFeeMax) {
            PowerRubleMod.LOGGER.warn("Config 'transfers.fee.min' is greater than 'transfers.fee.max', using {}", DEFAULT_TRANSFER_FEE_MIN);
            transferFeeMin = DEFAULT_TRANSFER_FEE_MIN;
        }

        return new RubleConfig(
            maxTransferAmount,
            minTransferAmount,
            transferDebtLimit,
            transferConfirmAbove,
            transferFeeAmount,
            transferFeePercent,
            transferFeeMin,
            transferFeeMax,
            nonBlankString(fee, "recipient", DEFAULT_TRANSFER_FEE_RECIPIENT),
            nonBlankString(currency, "name", DEFAULT_CURRENCY_NAME),
            booleanValue(top, "playersEnabled", DEFAULT_TOP_BALANCE_PLAYERS_ENABLED),
            positiveInt(top, "size", DEFAULT_TOP_BALANCE_SIZE),
            booleanValue(top, "showDebtTop", DEFAULT_TOP_DEBT_ENABLED),
            booleanValue(bank, "enabled", DEFAULT_BANK_ENABLED),
            nonBlankString(bank, "accountName", DEFAULT_BANK_ACCOUNT_NAME),
            nonNegativeInt(limits, "payCooldownSeconds", DEFAULT_PAY_COOLDOWN_SECONDS),
            nonNegativeLong(limits, "dailyTransferLimit", DEFAULT_DAILY_TRANSFER_LIMIT),
            positiveInt(history, "perPlayerEntries", DEFAULT_HISTORY_PER_PLAYER_ENTRIES),
            positiveInt(history, "globalEntries", DEFAULT_HISTORY_GLOBAL_ENTRIES),
            booleanValue(taxes, "enabled", DEFAULT_TAXES_ENABLED),
            positiveInt(taxes, "intervalMinutes", DEFAULT_TAX_INTERVAL_MINUTES),
            nonNegativeDouble(taxes, "wealthTaxPercent", DEFAULT_WEALTH_TAX_PERCENT),
            longValue(taxes, "minimumBalance", DEFAULT_TAX_MINIMUM_BALANCE)
        );
    }

    private static RubleConfig readLegacyProperties(Path path) {
        if (!Files.exists(path)) {
            return defaults();
        }

        Properties properties = defaultProperties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            PowerRubleMod.LOGGER.info("Migrating legacy config {} to {}", path.getFileName(), CONFIG_FILE_NAME);
        } catch (IOException exception) {
            PowerRubleMod.LOGGER.warn("Could not read {}, using defaults", path, exception);
            return defaults();
        }

        long maxTransferAmount = readPositiveLong(properties, "max-transfer-amount", DEFAULT_MAX_TRANSFER_AMOUNT);
        long minTransferAmount = readPositiveLong(properties, "min-transfer-amount", DEFAULT_MIN_TRANSFER_AMOUNT);
        if (minTransferAmount > maxTransferAmount) {
            PowerRubleMod.LOGGER.warn("Config 'min-transfer-amount' is greater than 'max-transfer-amount', using {}", DEFAULT_MIN_TRANSFER_AMOUNT);
            minTransferAmount = DEFAULT_MIN_TRANSFER_AMOUNT;
        }

        return new RubleConfig(
            maxTransferAmount,
            minTransferAmount,
            readLong(properties, "transfer-debt-limit", DEFAULT_TRANSFER_DEBT_LIMIT),
            DEFAULT_TRANSFER_CONFIRM_ABOVE,
            readNonNegativeLong(properties, "transfer-fee-amount", DEFAULT_TRANSFER_FEE_AMOUNT),
            DEFAULT_TRANSFER_FEE_PERCENT,
            DEFAULT_TRANSFER_FEE_MIN,
            DEFAULT_TRANSFER_FEE_MAX,
            nonBlankProperty(properties, "transfer-fee-recipient", DEFAULT_TRANSFER_FEE_RECIPIENT),
            nonBlankProperty(properties, "currency-name", DEFAULT_CURRENCY_NAME),
            readBoolean(properties, "top-balance-players-enabled", DEFAULT_TOP_BALANCE_PLAYERS_ENABLED),
            readPositiveInt(properties, "top-balance-size", DEFAULT_TOP_BALANCE_SIZE),
            DEFAULT_TOP_DEBT_ENABLED,
            DEFAULT_BANK_ENABLED,
            DEFAULT_BANK_ACCOUNT_NAME,
            DEFAULT_PAY_COOLDOWN_SECONDS,
            DEFAULT_DAILY_TRANSFER_LIMIT,
            DEFAULT_HISTORY_PER_PLAYER_ENTRIES,
            DEFAULT_HISTORY_GLOBAL_ENTRIES,
            DEFAULT_TAXES_ENABLED,
            DEFAULT_TAX_INTERVAL_MINUTES,
            DEFAULT_WEALTH_TAX_PERCENT,
            DEFAULT_TAX_MINIMUM_BALANCE
        );
    }

    private static RubleConfig defaults() {
        return new RubleConfig(
            DEFAULT_MAX_TRANSFER_AMOUNT,
            DEFAULT_MIN_TRANSFER_AMOUNT,
            DEFAULT_TRANSFER_DEBT_LIMIT,
            DEFAULT_TRANSFER_CONFIRM_ABOVE,
            DEFAULT_TRANSFER_FEE_AMOUNT,
            DEFAULT_TRANSFER_FEE_PERCENT,
            DEFAULT_TRANSFER_FEE_MIN,
            DEFAULT_TRANSFER_FEE_MAX,
            DEFAULT_TRANSFER_FEE_RECIPIENT,
            DEFAULT_CURRENCY_NAME,
            DEFAULT_TOP_BALANCE_PLAYERS_ENABLED,
            DEFAULT_TOP_BALANCE_SIZE,
            DEFAULT_TOP_DEBT_ENABLED,
            DEFAULT_BANK_ENABLED,
            DEFAULT_BANK_ACCOUNT_NAME,
            DEFAULT_PAY_COOLDOWN_SECONDS,
            DEFAULT_DAILY_TRANSFER_LIMIT,
            DEFAULT_HISTORY_PER_PLAYER_ENTRIES,
            DEFAULT_HISTORY_GLOBAL_ENTRIES,
            DEFAULT_TAXES_ENABLED,
            DEFAULT_TAX_INTERVAL_MINUTES,
            DEFAULT_WEALTH_TAX_PERCENT,
            DEFAULT_TAX_MINIMUM_BALANCE
        );
    }

    private static void writeJson5(Path path, RubleConfig config) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, toJson5(config));
        } catch (IOException exception) {
            PowerRubleMod.LOGGER.warn("Could not create default config {}", path, exception);
        }
    }

    private static String toJson5(RubleConfig config) {
        return """
            {
              // Text appended after money amounts in chat messages.
              currency: {
                name: "%s",
              },

              transfers: {
                // Minimum and maximum amount allowed for one /pay command.
                minAmount: %d,
                maxAmount: %d,

                // Lowest balance a player may have after /pay.
                debtLimit: %d,

                // Transfers at or above this amount require /payconfirm. 0 disables confirmation.
                confirmAbove: %d,

                fee: {
                  // Fixed fee charged on every /pay.
                  fixed: %d,

                  // Percent fee. 2.5 means 2.5%%.
                  percent: %s,
                  min: %d,
                  max: %d,

                  // exchange = fee disappears, bank = future server bank, any other value = player name.
                  recipient: "%s",
                },
              },

              top: {
                playersEnabled: %s,
                size: %d,
                showDebtTop: %s,
              },

              bank: {
                enabled: %s,
                accountName: "%s",
              },

              limits: {
                // Anti-spam and anti-abuse limits for regular players. Operators bypass them.
                // dailyTransferLimit is kept in memory and resets after server restart.
                payCooldownSeconds: %d,
                dailyTransferLimit: %d,
              },

              history: {
                perPlayerEntries: %d,
                globalEntries: %d,
              },

              taxes: {
                enabled: %s,
                intervalMinutes: %d,
                wealthTaxPercent: %s,
                minimumBalance: %d,
              },
            }
            """.formatted(
            escape(config.currencyName),
            config.minTransferAmount,
            config.maxTransferAmount,
            config.transferDebtLimit,
            config.transferConfirmAbove,
            config.transferFeeAmount,
            Double.toString(config.transferFeePercent),
            config.transferFeeMin,
            config.transferFeeMax,
            escape(config.transferFeeRecipient),
            Boolean.toString(config.topBalancePlayersEnabled),
            config.topBalanceSize,
            Boolean.toString(config.topDebtEnabled),
            Boolean.toString(config.bankEnabled),
            escape(config.bankAccountName),
            config.payCooldownSeconds,
            config.dailyTransferLimit,
            config.historyPerPlayerEntries,
            config.historyGlobalEntries,
            Boolean.toString(config.taxesEnabled),
            config.taxIntervalMinutes,
            Double.toString(config.wealthTaxPercent),
            config.taxMinimumBalance
        );
    }

    private static JsonObject section(JsonObject object, String key) {
        JsonObject section = object.getObject(key);
        return section == null ? new JsonObject() : section;
    }

    private static String nonBlankString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element instanceof JsonPrimitive primitive) {
            String value = primitive.asString().trim();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return fallback;
    }

    private static boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element instanceof JsonPrimitive primitive) {
            return primitive.asBoolean(fallback);
        }
        return fallback;
    }

    private static int positiveInt(JsonObject object, String key, int fallback) {
        long value = positiveLong(object, key, fallback);
        if (value > Integer.MAX_VALUE) {
            PowerRubleMod.LOGGER.warn("Config '{}' is too large, using {}", key, fallback);
            return fallback;
        }
        return (int) value;
    }

    private static int nonNegativeInt(JsonObject object, String key, int fallback) {
        long value = nonNegativeLong(object, key, fallback);
        if (value > Integer.MAX_VALUE) {
            PowerRubleMod.LOGGER.warn("Config '{}' is too large, using {}", key, fallback);
            return fallback;
        }
        return (int) value;
    }

    private static long positiveLong(JsonObject object, String key, long fallback) {
        long value = longValue(object, key, fallback);
        if (value < 1L) {
            PowerRubleMod.LOGGER.warn("Config '{}' must be at least 1, using {}", key, fallback);
            return fallback;
        }
        return value;
    }

    private static long nonNegativeLong(JsonObject object, String key, long fallback) {
        long value = longValue(object, key, fallback);
        if (value < 0L) {
            PowerRubleMod.LOGGER.warn("Config '{}' must not be negative, using {}", key, fallback);
            return fallback;
        }
        return value;
    }

    private static long longValue(JsonObject object, String key, long fallback) {
        JsonElement element = object.get(key);
        if (element instanceof JsonPrimitive primitive) {
            return primitive.asLong(fallback);
        }
        return fallback;
    }

    private static double nonNegativeDouble(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        double value = element instanceof JsonPrimitive primitive ? primitive.asDouble(fallback) : fallback;
        if (value < 0.0D) {
            PowerRubleMod.LOGGER.warn("Config '{}' must not be negative, using {}", key, fallback);
            return fallback;
        }
        return value;
    }

    private static String escape(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"");
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

    private static String nonBlankProperty(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key, fallback).trim();
        return value.isEmpty() ? fallback : value;
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
