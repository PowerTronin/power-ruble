package ru.powerruble;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
    private static final Duration PENDING_TRANSFER_TTL = Duration.ofSeconds(30);

    private static final SimpleCommandExceptionType EMPTY_PROFILE_EXCEPTION =
        new SimpleCommandExceptionType(Text.literal("Игрок не найден."));
    private static final SimpleCommandExceptionType MULTIPLE_PROFILES_EXCEPTION =
        new SimpleCommandExceptionType(Text.literal("Укажите ровно одного игрока."));
    private static final DynamicCommandExceptionType UNKNOWN_ONLINE_PROFILE_EXCEPTION =
        new DynamicCommandExceptionType(name -> Text.literal("Игрок " + name + " не найден на online-mode сервере."));

    private static RubleConfig config;
    private static RubleMessages messages;
    private static final Map<UUID, PendingTransfer> pendingTransfers = new HashMap<>();
    private static final Map<UUID, Instant> lastPayTimes = new HashMap<>();
    private static final Map<UUID, DailyTransferUsage> dailyTransferUsage = new HashMap<>();

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
                                LongArgumentType.getLong(context, "amount"),
                                ""
                            ))
                            .then(CommandManager.argument("comment", StringArgumentType.greedyString())
                                .executes(context -> pay(
                                    context.getSource(),
                                    singleProfile(GameProfileArgumentType.getProfileArgument(context, "player")),
                                    LongArgumentType.getLong(context, "amount"),
                                    StringArgumentType.getString(context, "comment")
                                ))
                            ))
                        )
            );

            dispatcher.register(
                CommandManager.literal("payconfirm")
                    .executes(context -> confirmPay(context.getSource()))
            );

            dispatcher.register(
                CommandManager.literal("paycancel")
                    .executes(context -> cancelPay(context.getSource()))
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
                    .then(CommandManager.literal("deposit")
                        .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                            .executes(context -> depositToBank(
                                context.getSource(),
                                LongArgumentType.getLong(context, "amount"),
                                ""
                            ))
                            .then(CommandManager.argument("comment", StringArgumentType.greedyString())
                                .executes(context -> depositToBank(
                                    context.getSource(),
                                    LongArgumentType.getLong(context, "amount"),
                                    StringArgumentType.getString(context, "comment")
                                ))
                            )
                        )
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
                    .then(CommandManager.literal("paylog")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("recent")
                            .executes(context -> showRecentPaylog(context.getSource()))
                        )
                        .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                            .executes(context -> showPaylog(
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
                        .then(CommandManager.literal("pay")
                            .then(CommandManager.argument("player", GameProfileArgumentType.gameProfile())
                                .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                                    .executes(context -> payFromBank(
                                        context.getSource(),
                                        singleProfile(GameProfileArgumentType.getProfileArgument(context, "player")),
                                        LongArgumentType.getLong(context, "amount"),
                                        ""
                                    ))
                                    .then(CommandManager.argument("comment", StringArgumentType.greedyString())
                                        .executes(context -> payFromBank(
                                            context.getSource(),
                                            singleProfile(GameProfileArgumentType.getProfileArgument(context, "player")),
                                            LongArgumentType.getLong(context, "amount"),
                                            StringArgumentType.getString(context, "comment")
                                        ))
                                    )
                                )
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
                                    LongArgumentType.getLong(context, "amount"),
                                    ""
                                ))
                                .then(CommandManager.argument("reason", StringArgumentType.greedyString())
                                    .executes(context -> take(
                                        context.getSource(),
                                        singleProfile(GameProfileArgumentType.getProfileArgument(context, "player")),
                                        LongArgumentType.getLong(context, "amount"),
                                        StringArgumentType.getString(context, "reason")
                                    ))
                                )
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
        sendMessage(source, message("help.payconfirm"));
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
            sendMessage(source, message("help.admin.paylog"));
            sendMessage(source, message("help.admin.debtors"));
            sendMessage(source, message("help.admin.bank"));
            sendMessage(source, message("help.admin.reload"));
        }

        return 1;
    }

    private static int reloadConfig(ServerCommandSource source) {
        config = RubleConfig.load();
        messages = RubleMessages.load();
        pendingTransfers.clear();
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

    private static int pay(ServerCommandSource source, GameProfile targetProfile, long amount, String comment) throws CommandSyntaxException {
        ServerPlayerEntity sender = source.getPlayerOrThrow();
        String normalizedComment = normalizeReason(comment);

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

        long feeAmount = calculateTransferFee(amount, config);
        if (!source.hasPermissionLevel(2) && !checkPayLimits(source, sender.getUuid(), amount, Instant.now())) {
            return 0;
        }

        if (requiresConfirmation(amount)) {
            pendingTransfers.put(sender.getUuid(), new PendingTransfer(
                targetId,
                targetName,
                amount,
                feeAmount,
                normalizedComment,
                Instant.now().plus(PENDING_TRANSFER_TTL)
            ));
            sendMessage(source, message(
                normalizedComment.isBlank() ? "pay.confirm-required" : "pay.confirm-required.comment",
                "amount", format(amount),
                "target", targetName,
                "fee", format(feeAmount),
                "seconds", Long.toString(PENDING_TRANSFER_TTL.toSeconds()),
                "comment", normalizedComment
            ));
            return 1;
        }

        return executePay(source, sender, targetId, targetName, amount, feeAmount, normalizedComment);
    }

    private static int confirmPay(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity sender = source.getPlayerOrThrow();
        PendingTransfer pending = pendingTransfers.remove(sender.getUuid());
        if (pending == null) {
            source.sendError(Text.literal(message("payconfirm.none")));
            return 0;
        }

        if (Instant.now().isAfter(pending.expiresAt())) {
            source.sendError(Text.literal(message("payconfirm.expired")));
            return 0;
        }

        return executePay(source, sender, pending.targetId(), pending.targetName(), pending.amount(), pending.fee(), pending.comment());
    }

    private static int cancelPay(ServerCommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity sender = source.getPlayerOrThrow();
        PendingTransfer pending = pendingTransfers.remove(sender.getUuid());
        if (pending == null) {
            source.sendError(Text.literal(message("payconfirm.none")));
            return 0;
        }

        sendMessage(source, message("paycancel.done", "amount", format(pending.amount()), "target", pending.targetName()));
        return 1;
    }

    private static int executePay(
        ServerCommandSource source,
        ServerPlayerEntity sender,
        UUID targetId,
        String targetName,
        long amount,
        long feeAmount,
        String comment
    ) throws CommandSyntaxException {
        RubleState state = RubleState.get(source.getServer());
        state.rememberName(sender.getUuid(), playerName(sender));
        state.rememberName(targetId, targetName);

        Instant now = Instant.now();
        if (!source.hasPermissionLevel(2) && !checkPayLimits(source, sender.getUuid(), amount, now)) {
            return 0;
        }

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

        recordSuccessfulPay(sender.getUuid(), amount, now);

        String fee = format(feeAmount);
        sendMessage(source, message(
            comment.isBlank() ? "pay.sent" : "pay.sent.comment",
            "amount", format(amount),
            "target", targetName,
            "fee", fee,
            "comment", comment
        ));
        ServerPlayerEntity onlineTarget = source.getServer().getPlayerManager().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendMessage(Text.literal(message(
                comment.isBlank() ? "pay.received" : "pay.received.comment",
                "sender", playerName(sender),
                "amount", format(amount),
                "comment", comment
            )), false);
        }

        state.addTransaction(RubleTransaction.transfer(
            now,
            sender.getUuid(),
            playerName(sender),
            targetId,
            targetName,
            amount,
            feeAmount,
            Optional.ofNullable(feeRecipientId),
            feeRecipientName(state, feeRecipientId),
            comment
        ));

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

    private static int showPaylog(ServerCommandSource source, GameProfile profile) throws CommandSyntaxException {
        UUID playerId = profileId(source, profile);
        RubleState state = RubleState.get(source.getServer());
        state.rememberName(playerId, profile.getName());
        var entries = state.getTransactions(playerId, config.historyPerPlayerEntries());

        if (entries.isEmpty()) {
            sendMessage(source, message("paylog.empty", "player", profile.getName()));
            return 1;
        }

        sendMessage(source, message("paylog.header", "player", profile.getName()));
        entries.forEach(entry -> sendMessage(source, message("paylog.entry", "entry", entry.describe(config.currencyName()))));
        return 1;
    }

    private static int showRecentPaylog(ServerCommandSource source) {
        RubleState state = RubleState.get(source.getServer());
        var entries = state.recentTransactions(config.historyGlobalEntries());

        if (entries.isEmpty()) {
            sendMessage(source, message("paylog.recent.empty"));
            return 1;
        }

        sendMessage(source, message("paylog.recent.header"));
        entries.forEach(entry -> sendMessage(source, message("paylog.entry", "entry", entry.describe(config.currencyName()))));
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
        state.addTransaction(RubleTransaction.admin(RubleTransaction.Type.ADMIN_GIVE, Instant.now(), playerId, playerName, amount, "admin give"));
        return 1;
    }

    private static int take(ServerCommandSource source, GameProfile profile, long amount, String reason) throws CommandSyntaxException {
        UUID playerId = profileId(source, profile);
        String playerName = profile.getName();
        RubleState state = RubleState.get(source.getServer());
        state.rememberName(playerId, playerName);
        if (!state.subtractAllowingDebt(playerId, amount)) {
            source.sendError(Text.literal(message("admin.take.too-small")));
            return 0;
        }

        String normalizedReason = normalizeReason(reason);
        sendMessage(source, message(
            normalizedReason.isBlank() ? "admin.take.done" : "admin.take.done.reason",
            "amount", format(amount),
            "player", playerName,
            "balance", format(state.getBalance(playerId)),
            "reason", normalizedReason
        ));
        ServerPlayerEntity onlinePlayer = source.getServer().getPlayerManager().getPlayer(playerId);
        if (onlinePlayer != null) {
            onlinePlayer.sendMessage(Text.literal(message(
                normalizedReason.isBlank() ? "admin.take.received" : "admin.take.received.reason",
                "amount", format(amount),
                "reason", normalizedReason
            )), false);
        }
        state.addTransaction(RubleTransaction.admin(RubleTransaction.Type.ADMIN_TAKE, Instant.now(), playerId, playerName, amount, normalizedReason));
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
        state.addTransaction(RubleTransaction.admin(RubleTransaction.Type.ADMIN_SET, Instant.now(), playerId, playerName, amount, "admin set"));
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
        state.addTransaction(RubleTransaction.bank(RubleTransaction.Type.BANK_GIVE, Instant.now(), BANK_ID, config.bankAccountName(), amount, "bank give"));
        return 1;
    }

    private static int depositToBank(ServerCommandSource source, long amount, String comment) throws CommandSyntaxException {
        if (!config.bankEnabled()) {
            source.sendError(Text.literal(message("bank.disabled")));
            return 0;
        }

        ServerPlayerEntity sender = source.getPlayerOrThrow();
        RubleState state = RubleState.get(source.getServer());
        rememberBank(state);
        state.rememberName(sender.getUuid(), playerName(sender));
        String normalizedComment = normalizeReason(comment);

        RubleState.TransferResult result = state.transfer(sender.getUuid(), BANK_ID, null, amount, 0L, 0L);
        if (result == RubleState.TransferResult.NOT_ENOUGH_MONEY) {
            source.sendError(Text.literal(message("bank.deposit.not-enough")));
            return 0;
        }

        if (result == RubleState.TransferResult.OVERFLOW) {
            source.sendError(Text.literal(message("bank.overflow")));
            return 0;
        }

        sendMessage(source, message(
            normalizedComment.isBlank() ? "bank.deposit.done" : "bank.deposit.done.comment",
            "amount", format(amount),
            "balance", format(state.getBalance(BANK_ID)),
            "comment", normalizedComment
        ));
        state.addTransaction(RubleTransaction.transfer(
            Instant.now(),
            sender.getUuid(),
            playerName(sender),
            BANK_ID,
            config.bankAccountName(),
            amount,
            0L,
            Optional.empty(),
            "",
            normalizedComment
        ));
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
        state.addTransaction(RubleTransaction.bank(RubleTransaction.Type.BANK_TAKE, Instant.now(), BANK_ID, config.bankAccountName(), amount, "bank take"));
        return 1;
    }

    private static int payFromBank(ServerCommandSource source, GameProfile profile, long amount, String comment) throws CommandSyntaxException {
        UUID targetId = profileId(source, profile);
        String targetName = profile.getName();
        RubleState state = RubleState.get(source.getServer());
        rememberBank(state);
        state.rememberName(targetId, targetName);
        String normalizedComment = normalizeReason(comment);

        RubleState.TransferResult result = state.transfer(BANK_ID, targetId, null, amount, 0L, 0L);
        if (result == RubleState.TransferResult.NOT_ENOUGH_MONEY) {
            source.sendError(Text.literal(message("bank.not-enough")));
            return 0;
        }

        if (result == RubleState.TransferResult.OVERFLOW) {
            source.sendError(Text.literal(message("pay.overflow")));
            return 0;
        }

        sendMessage(source, message(
            normalizedComment.isBlank() ? "bank.pay.done" : "bank.pay.done.comment",
            "amount", format(amount),
            "target", targetName,
            "balance", format(state.getBalance(BANK_ID)),
            "comment", normalizedComment
        ));
        ServerPlayerEntity onlineTarget = source.getServer().getPlayerManager().getPlayer(targetId);
        if (onlineTarget != null) {
            onlineTarget.sendMessage(Text.literal(message(
                normalizedComment.isBlank() ? "bank.pay.received" : "bank.pay.received.comment",
                "amount", format(amount),
                "comment", normalizedComment
            )), false);
        }
        state.addTransaction(RubleTransaction.transfer(
            Instant.now(),
            BANK_ID,
            config.bankAccountName(),
            targetId,
            targetName,
            amount,
            0L,
            Optional.empty(),
            "",
            normalizedComment
        ));
        return 1;
    }

    private static int setBank(ServerCommandSource source, long amount) {
        RubleState state = RubleState.get(source.getServer());
        rememberBank(state);
        state.setBalance(BANK_ID, amount);
        sendMessage(source, message("bank.set.done", "amount", format(amount)));
        state.addTransaction(RubleTransaction.bank(RubleTransaction.Type.BANK_SET, Instant.now(), BANK_ID, config.bankAccountName(), amount, "bank set"));
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

    private static boolean requiresConfirmation(long amount) {
        return config.transferConfirmAbove() > 0L && amount >= config.transferConfirmAbove();
    }

    private static boolean checkPayLimits(ServerCommandSource source, UUID playerId, long amount, Instant now) {
        int cooldownRemaining = payCooldownRemaining(playerId, now);
        if (cooldownRemaining > 0) {
            source.sendError(Text.literal(message("pay.cooldown", "seconds", Integer.toString(cooldownRemaining))));
            return false;
        }

        long remainingDailyLimit = remainingDailyLimit(playerId, now);
        if (remainingDailyLimit >= 0L && amount > remainingDailyLimit) {
            source.sendError(Text.literal(message("pay.daily-limit", "remaining", format(remainingDailyLimit))));
            return false;
        }

        return true;
    }

    private static int payCooldownRemaining(UUID playerId, Instant now) {
        int cooldownSeconds = config.payCooldownSeconds();
        if (cooldownSeconds <= 0) {
            return 0;
        }

        Instant lastPay = lastPayTimes.get(playerId);
        if (lastPay == null) {
            return 0;
        }

        long elapsedSeconds = Duration.between(lastPay, now).getSeconds();
        long remainingSeconds = cooldownSeconds - elapsedSeconds;
        return remainingSeconds > 0L ? (int) remainingSeconds : 0;
    }

    private static long remainingDailyLimit(UUID playerId, Instant now) {
        long dailyLimit = config.dailyTransferLimit();
        if (dailyLimit <= 0L) {
            return -1L;
        }

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        DailyTransferUsage usage = dailyTransferUsage.get(playerId);
        if (usage == null || !usage.date().equals(today)) {
            return dailyLimit;
        }

        long used = usage.amount();
        if (used >= dailyLimit) {
            return 0L;
        }

        return dailyLimit - used;
    }

    private static void recordSuccessfulPay(UUID playerId, long amount, Instant now) {
        lastPayTimes.put(playerId, now);
        if (config.dailyTransferLimit() <= 0L) {
            return;
        }

        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        DailyTransferUsage usage = dailyTransferUsage.get(playerId);
        long used = usage == null || !usage.date().equals(today) ? 0L : usage.amount();
        long nextUsed = Long.MAX_VALUE - used < amount ? Long.MAX_VALUE : used + amount;
        dailyTransferUsage.put(playerId, new DailyTransferUsage(today, nextUsed));
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

    private static String feeRecipientName(RubleState state, UUID feeRecipientId) {
        if (feeRecipientId == null) {
            return "";
        }

        if (BANK_ID.equals(feeRecipientId)) {
            return config.bankAccountName();
        }

        return state.getName(feeRecipientId);
    }

    private static void rememberBank(RubleState state) {
        state.rememberName(BANK_ID, config.bankAccountName());
    }

    private static String normalizeReason(String reason) {
        return reason == null ? "" : reason.trim();
    }

    private static String message(String key, String... replacements) {
        String[] withCurrency = new String[replacements.length + 2];
        System.arraycopy(replacements, 0, withCurrency, 0, replacements.length);
        withCurrency[replacements.length] = "currency";
        withCurrency[replacements.length + 1] = config.currencyName();
        return messages.get(key, withCurrency);
    }

    static String currencyName() {
        return config == null ? "RUB" : config.currencyName();
    }

    static int historyPerPlayerEntries() {
        return config == null ? 20 : config.historyPerPlayerEntries();
    }

    static int historyGlobalEntries() {
        return config == null ? 500 : config.historyGlobalEntries();
    }

    private static String format(long amount) {
        return amount + " " + config.currencyName();
    }

    private record PendingTransfer(UUID targetId, String targetName, long amount, long fee, String comment, Instant expiresAt) {
    }

    private record DailyTransferUsage(LocalDate date, long amount) {
    }
}
