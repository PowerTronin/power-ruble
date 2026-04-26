package ru.powerruble;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import net.fabricmc.loader.api.FabricLoader;

public final class RubleMessages {
    private static final String CONFIG_FILE_NAME = PowerRubleMod.MOD_ID + "-messages.properties";

    private final Properties properties;

    private RubleMessages(Properties properties) {
        this.properties = properties;
    }

    public static RubleMessages load() {
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

        return new RubleMessages(properties);
    }

    public String get(String key, String... replacements) {
        String message = properties.getProperty(key, key);
        Map<String, String> replacementMap = new HashMap<>();
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            replacementMap.put(replacements[index], replacements[index + 1]);
        }

        for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return message;
    }

    private static Properties defaultProperties() {
        Properties properties = new Properties();
        properties.setProperty("help.header", "Команды Power Ruble:");
        properties.setProperty("help.balance", "/balance - показать свой баланс");
        properties.setProperty("help.pay", "/pay <игрок> <сумма> - перевести {currency} игроку");
        properties.setProperty("help.payconfirm", "/payconfirm - подтвердить крупный перевод, /paycancel - отменить его");
        properties.setProperty("help.top", "/topbalance - показать топ балансов");
        properties.setProperty("help.topdebt", "/topdebt - показать топ долгов");
        properties.setProperty("help.bank", "/bank balance, /bank deposit <сумма> [комментарий] - операции с банком");
        properties.setProperty("help.self", "/ruble help - показать эту справку");
        properties.setProperty("help.admin.balance", "/balance <игрок> - показать баланс игрока");
        properties.setProperty("help.admin.give", "/ruble give <игрок> <сумма> - начислить {currency}");
        properties.setProperty("help.admin.take", "/ruble take <игрок> <сумма> [причина] - списать {currency}");
        properties.setProperty("help.admin.set", "/ruble set <игрок> <сумма> - установить баланс");
        properties.setProperty("help.admin.history", "/ruble history <игрок> - показать последние операции");
        properties.setProperty("help.admin.paylog", "/ruble paylog <игрок|recent> - показать журнал операций");
        properties.setProperty("help.admin.debtors", "/ruble debtors - показать топ долгов");
        properties.setProperty("help.admin.bank", "/ruble bank <balance|give|take|set|pay> - управление банком");
        properties.setProperty("help.admin.reload", "/ruble reload - перезагрузить конфиг");
        properties.setProperty("config.reloaded", "Конфиг Power Ruble перезагружен.");
        properties.setProperty("balance", "{player}: {amount}");
        properties.setProperty("pay.min", "Сумма перевода не может быть меньше {amount}.");
        properties.setProperty("pay.max", "Сумма перевода не может быть больше {amount}.");
        properties.setProperty("pay.self", "Нельзя перевести {currency} самому себе.");
        properties.setProperty("pay.not-enough", "Недостаточно {currency}. После перевода баланс не может быть ниже {limit}. Ваш баланс: {balance}.");
        properties.setProperty("pay.overflow", "Баланс получателя слишком большой для этого перевода.");
        properties.setProperty("pay.fee-overflow", "Баланс получателя комиссии слишком большой для этого перевода.");
        properties.setProperty("pay.confirm-required", "Подтвердите перевод {amount} игроку {target}. Комиссия: {fee}. Выполните /payconfirm в течение {seconds} секунд или /paycancel для отмены.");
        properties.setProperty("pay.confirm-required.comment", "Подтвердите перевод {amount} игроку {target}. Комиссия: {fee}. Комментарий: {comment}. Выполните /payconfirm в течение {seconds} секунд или /paycancel для отмены.");
        properties.setProperty("payconfirm.none", "Нет перевода, ожидающего подтверждения.");
        properties.setProperty("payconfirm.expired", "Ожидающий перевод устарел. Повторите /pay.");
        properties.setProperty("paycancel.done", "Перевод {amount} игроку {target} отменен.");
        properties.setProperty("pay.cooldown", "Подождите {seconds} секунд перед следующим переводом.");
        properties.setProperty("pay.daily-limit", "Превышен дневной лимит переводов. Сегодня осталось: {remaining}.");
        properties.setProperty("pay.sent", "Вы перевели {amount} игроку {target}. Комиссия: {fee}.");
        properties.setProperty("pay.sent.comment", "Вы перевели {amount} игроку {target}. Комиссия: {fee}. Комментарий: {comment}");
        properties.setProperty("pay.received", "{sender} перевел вам {amount}.");
        properties.setProperty("pay.received.comment", "{sender} перевел вам {amount}. Комментарий: {comment}");
        properties.setProperty("admin.give.overflow", "Баланс игрока слишком большой для начисления.");
        properties.setProperty("admin.give.done", "Начислено {amount} игроку {player}. Баланс: {balance}");
        properties.setProperty("admin.give.received", "Вам начислено {amount}.");
        properties.setProperty("admin.take.too-small", "Баланс игрока слишком маленький для списания этой суммы.");
        properties.setProperty("admin.take.done", "Списано {amount} у игрока {player}. Баланс: {balance}");
        properties.setProperty("admin.take.done.reason", "Списано {amount} у игрока {player}. Баланс: {balance}. Причина: {reason}");
        properties.setProperty("admin.take.received", "У вас списано {amount}.");
        properties.setProperty("admin.take.received.reason", "У вас списано {amount}. Причина: {reason}");
        properties.setProperty("admin.set.done", "Баланс игрока {player} установлен: {amount}");
        properties.setProperty("admin.set.received", "Ваш баланс установлен: {amount}");
        properties.setProperty("top.disabled", "Топ балансов отключен для игроков.");
        properties.setProperty("top.empty", "Балансов пока нет.");
        properties.setProperty("top.header", "Топ балансов:");
        properties.setProperty("top.entry", "#{rank}. {player}: {amount}");
        properties.setProperty("topdebt.disabled", "Топ долгов отключен для игроков.");
        properties.setProperty("topdebt.empty", "Долгов пока нет.");
        properties.setProperty("topdebt.header", "Топ долгов:");
        properties.setProperty("topdebt.entry", "#{rank}. {player}: {amount}");
        properties.setProperty("bank.disabled", "Банк отключен.");
        properties.setProperty("bank.balance", "Баланс банка: {amount}");
        properties.setProperty("bank.overflow", "Баланс банка слишком большой для начисления.");
        properties.setProperty("bank.not-enough", "В банке недостаточно средств.");
        properties.setProperty("bank.give.done", "В банк начислено {amount}. Баланс: {balance}");
        properties.setProperty("bank.take.done", "Из банка списано {amount}. Баланс: {balance}");
        properties.setProperty("bank.set.done", "Баланс банка установлен: {amount}");
        properties.setProperty("bank.deposit.not-enough", "У вас недостаточно средств для перевода в банк.");
        properties.setProperty("bank.deposit.done", "Вы перевели в банк {amount}. Баланс банка: {balance}");
        properties.setProperty("bank.deposit.done.comment", "Вы перевели в банк {amount}. Баланс банка: {balance}. Комментарий: {comment}");
        properties.setProperty("bank.deposit.received", "{sender} перевел в банк {amount}.");
        properties.setProperty("bank.deposit.received.comment", "{sender} перевел в банк {amount}. Комментарий: {comment}");
        properties.setProperty("bank.pay.done", "Из банка переведено {amount} игроку {target}. Баланс банка: {balance}");
        properties.setProperty("bank.pay.done.comment", "Из банка переведено {amount} игроку {target}. Баланс банка: {balance}. Комментарий: {comment}");
        properties.setProperty("bank.pay.received", "Банк перевел вам {amount}.");
        properties.setProperty("bank.pay.received.comment", "Банк перевел вам {amount}. Комментарий: {comment}");
        properties.setProperty("history.empty", "История операций игрока {player} пуста.");
        properties.setProperty("history.header", "История операций игрока {player}:");
        properties.setProperty("history.entry", "{entry}");
        properties.setProperty("paylog.empty", "Журнал операций игрока {player} пуст.");
        properties.setProperty("paylog.header", "Журнал операций игрока {player}:");
        properties.setProperty("paylog.recent.empty", "Журнал операций пуст.");
        properties.setProperty("paylog.recent.header", "Последние операции:");
        properties.setProperty("paylog.entry", "{entry}");
        return properties;
    }

    private static void writeDefaults(Path path, Properties properties) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "Power Ruble messages");
            }
        } catch (IOException exception) {
            PowerRubleMod.LOGGER.warn("Could not create default messages config {}", path, exception);
        }
    }
}
