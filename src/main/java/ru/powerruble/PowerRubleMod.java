package ru.powerruble;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PowerRubleMod implements ModInitializer {
    public static final String MOD_ID = "power-ruble";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final UUID BANK_ID = UUID.nameUUIDFromBytes("PowerRuble:bank".getBytes(StandardCharsets.UTF_8));

    private static final SimpleCommandExceptionType EMPTY_PROFILE_EXCEPTION =
        new SimpleCommandExceptionType(Text.literal("Игрок не найден."));
    private static final SimpleCommandExceptionType MULTIPLE_PROFILES_EXCEPTION =
        new SimpleCommandExceptionType(Text.literal("Укажите ровно одного игрока."));
    private static final DynamicCommandExceptionType UNKNOWN_ONLINE_PROFILE_EXCEPTION =
        new DynamicCommandExceptionType(name -> Text.literal("Игрок " + name + " не найден на online-mode сервере."));

    private static RubleConfig config;
    private static RubleMessages messages;

    @Override
    public void onInitialize() {
        config = RubleConfig.load();
        messages = RubleMessages.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("balance")
                    .executes(context -> showOwnBalance(context.getSource()))
                    .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> showBalance(
                            context.getSource(),
                            singleProfile(GameProfileArgumentType.getProfileArgument(context, "player"))
                        ))
                    )
            );

            dispatcher.register(
                CommandManager.literal("pay")
                    .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                        .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                            .executes(context -> pay(
                                context.getSource(),
                                singleProfile(GameProfileArgumentType.getProfileArgument(context, "player")),
                                LongArgumentType.getLong(context, "amount")
                            ))
                        )
                    )
            );

            dispatcher.register(
                CommandManager.literal("topbalance")
                    .executes(context -> showTopBalance(context.getSource()))
            );

            dispatcher.register(
                CommandManager.literal("topdebt")
                    .executes(context -> showTopDebt(context.getSource()))
            );

            dispatcher.register(
                CommandManager.literal("bank")
                    .then(CommandManager.literal("balance")
                        .executes(context -> showBankBalance(context.getSource()))
                    )
            );

            dispatcher.register(
                CommandManager.literal("ruble")
                    .then(CommandManager.literal("help")
                        .executes(context -> showHelp(context.getSource()))
                    )
                    .then(CommandManager.literal("reload")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> reloadConfig(context.getSource()))
                    )
                    .then(CommandManager.literal("history")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                            .executes(context -> showHistory(
                                context.getSource(),
                                singleProfile(GameProfileArgumentType.getProfileArgument(context, "player"))
                            ))
                        )
                    )
                    .then(CommandManager.literal("debtors")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> showTopDebt(context.getSource()))
                    )
                    .then(CommandManager.literal("bank")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("balance")
                            .executes(context -> showBankBalance(context.getSource()))
                        )
                        .then(CommandManager.literal("give")
                            .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> giveBank(
                                    context.getSource(),
                                    LongArgumentType.getLong(context, "amount")
                                ))
                            )
                        )
                        .then(CommandManager.literal("take")
                            .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> takeBank(
                                    context.getSource(),
                                    LongArgumentType.getLong(context, "amount")
                                ))
                            )
                        )
                        .then(CommandManager.literal("set")
                            .then(CommandManager.argument("amount", LongArgumentType.longArg(0))
                                .executes(context -> setBank(
                                    context.getSource(),
                                    LongArgumentType.getLong(context, "amount")
                                ))
                            )
                        )
                    )
                    .then(CommandManager.literal("give")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                            .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> give(
                                    context.getSource(),
                                    singleProfile(GameProfileArgumentType.getProfileArgument(context, "player")),
                                    LongArgumentType.getLong(context, "amount")
                                ))
                            )
                        )
                    )
                    .then(CommandManager.literal("take")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                            .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                                .executes(context -> take(
                                    context.getSource(),
                                    singleProfile(GameProfileArgumentType.getProfileArgument(context, "player")),
                                    LongArgumentType.getLong(context, "amount")
                                ))
                            )
                        )
                    )
                    .then(CommandManager.literal("set")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                            .then(CommandManager.argument("amount", LongArgumentType.longArg(0))
                                .executes(context -> set(
                                    context.getSource(),
                                    singleProfile(GameProfileArgumentType.getProfileArgument(context, "player")),
                                    LongArgumentType.getLong(context, "amount")
                                ))
                            )
                        )
                    )
            );
        });

        LOGGER.info("Power Ruble economy commands registered");
    }

    private static int showHelp(ServerCommandSource source) {
        sendMessage(source, message("help.header"));
        sendMessage(source, message("help.balance"));
        sendMessage(source, message("help.pay"));
        if (config.topBalancePlayersEnabled() || source.hasPermissionLevel(2)) {
            sendMessage(source, message("help.top"));
        }
        if (config.topDebtEnabled() || source.hasPermissionLevel(2)) {
            sendMessage(source, message("help.topdebt"));
        }
        if (config.bankEnabled() || source.hasPermissionLevel(2)) {
            sendMessage(source, message("help.bank"));
        }
        sendMessage(source, message("help.self"));

        if (source.hasPermissionLevel(2)) {
            sendMessage(source, message("help.admin.balance"));
            sendMessage(source, message("help.admin.give"));
            sendMessage(source, message("help.admin.take"));
            sendMessage(source, message("help.admin.set"));
            sendMessage(source, message("help.admin.history"));
            sendMessage(source, message("help.admin.debtors"));
            sendMessage(source, message("help.admin.bank"));
            sendMessage(source, message("help.admin.reload"));
        }

        return 1;
    }

    private static int reloadConfig(ServerCommandSource source) {
        config = RubleConfig.load();
        messages = RubleMessages.load();
        sendMessage(source, message("config.reloaded"));
        return 1;
    }

    private static int showOwnBalance(ServerCommandSource source) throws CommandSyntaxException {
        return showBalance(source, source.getPlayerOrThrow());
    }

    private static int showBalance(ServerCommandSource source, ServerPlayerEntity player) {
        RubleState state = RubleState.get(source.getServer());
        state.rememberName(player.getUuid(), playerName(player));
        sendMessage(source, message(
            "balance",
            "player", playerName(player),
            "amount", format(state.getBalance(player.getUuid()))
        ));
        return 1;
    }

    private static int showBalance(ServerCommandSource source, GameProfile profile) throws CommandSyntaxException {
        UUID playerId = profileId(source, profile);
        String playerName = profile.getName();
        RubleState state = RubleState.get(source.getServer());
        state.rememberName(playerId, playerName);
        sendMessage(source, message(
            "balance",
            "player", playerName,
            "amount", format(state.getBalance(playerId))
        ));
        return 1;
    }

    private static int pay(ServerCommandSource source, GameProfile targetProfile, long amount) throws CommandSyntaxException {
        ServerPlayerEntity sender = source.getPlayerOrThrow();

        if (amount < config.minTransferAmount()) {
            source.sendError(Text.literal(message("pay.min", "amount", format(config.minTransferAmount()))));
            return 0;
        }

        if (amount > config.maxTransferAmount()) {
            source.sendError(Text.literal(message("pay.max", "amount", format(config.maxTransferAmount()))));
            return 0;
        }

        UUID targetId = profileId(source, targetProfile);
        String targetName = targetProfile.getName();

        if (sender.getUuid().equals(targetId)) {
            source.sendError(Text.literal(message("pay.self")));
            return 0;
        }

        RubleState state = RubleState.get(source.getServer());
        state.rememberName(sender.getUuid(), playerName(sender));
        state.rememberName(targetId, targetName);

        long feeAmount = calculateTransferFee(amount, config);
        UUID feeRecipientId = feeRecipientId(source, state, feeAmount);

        RubleState.TransferResult result = state.transfer(
            sender.getUuid(),
            targetId,
            feeRecipientId,
            amount,
            feeAmount,
            config.transferDebtLimit()
        );

        if (result == RubleState.TransferResult.NOT_ENOUGH_MONEY) {
            source.sendError(Text.literal(message(
                "pay.not-enough",
                "limit", format(config.transferDebtLimit()),
                "balance", format(state.getBalance(sender.getUuid()))
            )));
            return 0;
        }

        if (result == RubleState.TransferResult.OVERFLOW) {
            source.sendError(Text.literal(message("pay.overflow")));
            return 0;
        }

        if (result == RubleState.TransferResult.FEE_OVERFLOW) {
            source.sendError(Text.literal(message("pay.fee-overflow")));
            return 0;
        }

        String fee = format(feeAmount);
        sendMessage(source, message("pay.sent", "amount", format(amount), "target", targetName, "fee", fee));
        ServerPlayerEntity onlineTarget = source.getServer().getPlayerManager().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendMessage(Text.literal(message("pay.received", "sender", playerName(sender), "amount", format(amount))), false);
        }

        String timestamp = Instant.now().toString();
        state.addHistory(sender.getUuid(), timestamp + " -" + format(amount) + " -> " + targetName + ", fee " + fee);
        state.addHistory(targetId, timestamp + " +" + format(amount) + " <- " + playerName(sender));
        if (feeRecipientId != null && feeAmount > 0L) {
            state.addHistory(feeRecipientId, timestamp + " +" + fee + " fee <- " + playerName(sender));
        }

        return 1;
    }

    private static int showTopDebt(ServerCommandSource source) {
        if (!config.topDebtEnabled() && !source.hasPermissionLevel(2)) {
            source.sendError(Text.literal(message("topdebt.disabled")));
            return 0;
        }

        RubleState state = RubleState.get(source.getServer());
        var entries = state.topDebts(config.topBalanceSize());
        if (entries.isEmpty()) {
            sendMessage(source, message("topdebt.empty"));
            return 1;
        }

        sendMessage(source, message("topdebt.header"));
        for (int index = 0; index < entries.size(); index++) {
            RubleState.BalanceEntry entry = entries.get(index);
            sendMessage(source, message(
                "topdebt.entry",
                "rank", Integer.toString(index + 1),
                "player", entry.name(),
                "amount", format(entry.balance())
            ));
        }

        return 1;
    }

    private static int showTopBalance(ServerCommandSource source) {
        if (!config.topBalancePlayersEnabled() && !source.hasPermissionLevel(2)) {
            source.sendError(Text.literal(message("top.disabled")));
            return 0;
        }

        RubleState state = RubleState.get(source.getServer());
        var entries = state.topBalances(config.topBalanceSize());
        if (entries.isEmpty()) {
            sendMessage(source, message("top.empty"));
            return 1;
        }

        sendMessage(source, message("top.header"));
        for (int index = 0; index < entries.size(); index++) {
            RubleState.BalanceEntry entry = entries.get(index);
            sendMessage(source, message(
                "top.entry",
                "rank", Integer.toString(index + 1),
                "player", entry.name(),
                "amount", format(entry.balance())
            ));
        }

        return 1;
    }

    private static int showHistory(ServerCommandSource source, GameProfile profile) throws CommandSyntaxException {
        UUID playerId = profileId(source, profile);
        RubleState state = RubleState.get(source.getServer());
        state.rememberName(playerId, profile.getName());
        var entries = state.getHistory(playerId);

        if (entries.isEmpty()) {
            sendMessage(source, message("history.empty", "player", profile.getName()));
            return 1;
        }

        sendMessage(source, message("history.header", "player", profile.getName()));
        entries.forEach(entry -> sendMessage(source, message("history.entry", "entry", entry)));
        return 1;
    }

    private static int give(ServerCommandSource source, GameProfile profile, long amount) throws CommandSyntaxException {
        UUID playerId = profileId(source, profile);
        String playerName = profile.getName();
        RubleState state = RubleState.get(source.getServer());
        state.rememberName(playerId, playerName);
        if (!state.add(playerId, amount)) {
            source.sendError(Text.literal(message("admin.give.overflow")));
            return 0;
        }

        sendMessage(source, message(
            "admin.give.done",
            "amount", format(amount),
            "player", playerName,
            "balance", format(state.getBalance(playerId))
        ));
        ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(playerId);
        if (onlinePlayer != null) {
            onlinePlayer.sendMessage(Text.literal(message("admin.give.received", "amount", format(amount))), false);
        }
        state.addHistory(playerId, Instant.now() + " +" + format(amount) + " admin give");
        return 1;
    }

    private static int take(ServerCommandSource source, GameProfile profile, long amount) throws CommandSyntaxException {
        UUID playerId = profileId(source, profile);
        String playerName = profile.getName();
        RubleState state = RubleState.get(source.getServer());
        state.rememberName(playerId, playerName);
        if (!state.subtractAllowingDebt(playerId, amount)) {
            source.sendError(Text.literal(message("admin.take.too-small")));
            return 0;
        }

        sendMessage(source, message(
            "admin.take.done",
            "amount", format(amount),
            "player", playerName,
            "balance", format(state.getBalance(playerId))
        ));
        ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(playerId);
        if (onlinePlayer != null) {
            onlinePlayer.sendMessage(Text.literal(message("admin.take.received", "amount", format(amount))), false);
        }
        state.addHistory(playerId, Instant.now() + " -" + format(amount) + " admin take");
        return 1;
    }

    private static int set(ServerCommandSource source, GameProfile profile, long amount) throws CommandSyntaxException {
        UUID playerId = profileId(source, profile);
        String playerName = profile.getName();
        RubleState state = RubleState.get(source.getServer());
        state.rememberName(playerId, playerName);
        state.setBalance(playerId, amount);

        sendMessage(source, message("admin.set.done", "player", playerName, "amount", format(amount)));
        ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(playerId);
        if (onlinePlayer != null) {
            onlinePlayer.sendMessage(Text.literal(message("admin.set.received", "amount", format(amount))), false);
        }
        state.addHistory(playerId, Instant.now() + " =" + format(amount) + " admin set");
        return 1;
    }

    private static int showBankBalance(ServerCommandSource source) {
        if (!config.bankEnabled() && !source.hasPermissionLevel(2)) {
            source.sendError(Text.literal(message("bank.disabled")));
            return 0;
        }

        RubleState state = RubleState.get(source.getServer());
        rememberBank(state);
        sendMessage(source, message("bank.balance", "amount", format(state.getBalance(BANK_ID))));
        return 1;
    }

    private static int giveBank(ServerCommandSource source, long amount) {
        RubleState state = RubleState.get(source.getServer());
        rememberBank(state);
        if (!state.add(BANK_ID, amount)) {
            source.sendError(Text.literal(message("bank.overflow")));
            return 0;
        }

        sendMessage(source, message("bank.give.done", "amount", format(amount), "balance", format(state.getBalance(BANK_ID))));
        state.addHistory(BANK_ID, Instant.now() + " +" + format(amount) + " bank give");
        return 1;
    }

    private static int takeBank(ServerCommandSource source, long amount) {
        RubleState state = RubleState.get(source.getServer());
        rememberBank(state);
        if (!state.subtract(BANK_ID, amount)) {
            source.sendError(Text.literal(message("bank.not-enough")));
            return 0;
        }

        sendMessage(source, message("bank.take.done", "amount", format(amount), "balance", format(state.getBalance(BANK_ID))));
        state.addHistory(BANK_ID, Instant.now() + " -" + format(amount) + " bank take");
        return 1;
    }

    private static int setBank(ServerCommandSource source, long amount) {
        RubleState state = RubleState.get(source.getServer());
        rememberBank(state);
        state.setBalance(BANK_ID, amount);
        sendMessage(source, message("bank.set.done", "amount", format(amount)));
        state.addHistory(BANK_ID, Instant.now() + " =" + format(amount) + " bank set");
        return 1;
    }

    private static void sendMessage(ServerCommandSource source, String message) {
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            player.sendMessage(Text.literal(message), false);
            return;
        }

        source.sendFeedback(() -> Text.literal(message), false);
    }

    private static String playerName(ServerPlayerEntity player) {
        return player.getName().getString();
    }

    private static GameProfile singleProfile(Collection<GameProfile> profiles) throws CommandSyntaxException {
        if (profiles.isEmpty()) {
            throw EMPTY_PROFILE_EXCEPTION.create();
        }

        if (profiles.size() > 1) {
            throw MULTIPLE_PROFILES_EXCEPTION.create();
        }

        return profiles.iterator().next();
    }

    private static GameProfile findProfile(ServerCommandSource source, String name) {
        Optional<GameProfile> cachedProfile = source.getServer().getUserCache().findByName(name);
        return cachedProfile.orElseGet(() -> new GameProfile(offlineUuid(name), name));
    }

    private static UUID offlineUuid(String playerName) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID profileId(ServerCommandSource source, GameProfile profile) throws CommandSyntaxException {
        UUID id = profile.getId();
        if (id != null) {
            return id;
        }

        if (!source.getServer().isOnlineMode()) {
            return offlineUuid(profile.getName());
        }

        throw UNKNOWN_ONLINE_PROFILE_EXCEPTION.create(profile.getName());
    }

    static long calculateTransferFee(long amount, RubleConfig config) {
        long fixedFee = config.transferFeeAmount();
        long percentFee = calculatePercentFee(amount, config.transferFeePercent());
        if (Long.MAX_VALUE - fixedFee < percentFee) {
            return Long.MAX_VALUE;
        }

        long fee = fixedFee + percentFee;
        if (fee > 0L && config.transferFeeMin() > fee) {
            fee = config.transferFeeMin();
        }

        if (config.transferFeeMax() > 0L && fee > config.transferFeeMax()) {
            fee = config.transferFeeMax();
        }

        return fee;
    }

    private static long calculatePercentFee(long amount, double percent) {
        if (amount <= 0L || percent <= 0.0D) {
            return 0L;
        }

        double fee = Math.floor(amount * percent / 100.0D);
        if (fee >= Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }

        return (long) fee;
    }

    private static UUID feeRecipientId(ServerCommandSource source, RubleState state, long feeAmount) throws CommandSyntaxException {
        if (feeAmount <= 0L || config.transferFeeGoesToExchange()) {
            return null;
        }

        if ("bank".equalsIgnoreCase(config.transferFeeRecipient())) {
            if (!config.bankEnabled()) {
                return null;
            }

            rememberBank(state);
            return BANK_ID;
        }

        GameProfile feeRecipient = findProfile(source, config.transferFeeRecipient());
        UUID feeRecipientId = profileId(source, feeRecipient);
        state.rememberName(feeRecipientId, feeRecipient.getName());
        return feeRecipientId;
    }

    private static void rememberBank(RubleState state) {
        state.rememberName(BANK_ID, config.bankAccountName());
    }

    private static String message(String key, String... replacements) {
        String[] withCurrency = new String[replacements.length + 2];
        System.arraycopy(replacements, 0, withCurrency, 0, replacements.length);
        withCurrency[replacements.length] = "currency";
        withCurrency[replacements.length + 1] = config.currencyName();
        return messages.get(key, withCurrency);
    }

    private static String format(long amount) {
        return amount + " " + config.currencyName();
    }
}
