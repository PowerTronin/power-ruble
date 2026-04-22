package ru.powerruble;

import com.mojang.brigadier.arguments.LongArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PowerRubleMod implements ModInitializer {
    public static final String MOD_ID = "power-ruble";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
            CommandManager.literal("ruble")
                .then(CommandManager.literal("balance")
                    .executes(context -> showOwnBalance(context.getSource()))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(context -> showBalance(
                            context.getSource(),
                            EntityArgumentType.getPlayer(context, "player")
                        ))
                    )
                )
                .then(CommandManager.literal("pay")
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                            .executes(context -> pay(
                                context.getSource(),
                                EntityArgumentType.getPlayer(context, "player"),
                                LongArgumentType.getLong(context, "amount")
                            ))
                        )
                    )
                )
                .then(CommandManager.literal("give")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                            .executes(context -> give(
                                context.getSource(),
                                EntityArgumentType.getPlayer(context, "player"),
                                LongArgumentType.getLong(context, "amount")
                            ))
                        )
                    )
                )
                .then(CommandManager.literal("take")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("amount", LongArgumentType.longArg(1))
                            .executes(context -> take(
                                context.getSource(),
                                EntityArgumentType.getPlayer(context, "player"),
                                LongArgumentType.getLong(context, "amount")
                            ))
                        )
                    )
                )
                .then(CommandManager.literal("set")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("player", EntityArgumentType.player())
                        .then(CommandManager.argument("amount", LongArgumentType.longArg(0))
                            .executes(context -> set(
                                context.getSource(),
                                EntityArgumentType.getPlayer(context, "player"),
                                LongArgumentType.getLong(context, "amount")
                            ))
                        )
                    )
                )
        ));

        LOGGER.info("Power Ruble economy commands registered");
    }

    private static int showOwnBalance(ServerCommandSource source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return showBalance(source, source.getPlayerOrThrow());
    }

    private static int showBalance(ServerCommandSource source, ServerPlayerEntity player) {
        long balance = RubleState.get(source.getServer()).getBalance(player.getUuid());
        sendMessage(source, playerName(player) + ": " + format(balance));
        return 1;
    }

    private static int pay(ServerCommandSource source, ServerPlayerEntity target, long amount) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity sender = source.getPlayerOrThrow();

        if (sender.getUuid().equals(target.getUuid())) {
            source.sendError(Text.literal("Нельзя перевести рубли самому себе."));
            return 0;
        }

        RubleState state = RubleState.get(source.getServer());
        RubleState.TransferResult result = state.transfer(sender.getUuid(), target.getUuid(), amount);

        if (result == RubleState.TransferResult.NOT_ENOUGH_MONEY) {
            source.sendError(Text.literal("Недостаточно рублей. После перевода баланс не может быть ниже " + format(RubleState.TRANSFER_DEBT_LIMIT) + ". Ваш баланс: " + format(state.getBalance(sender.getUuid()))));
            return 0;
        }

        if (result == RubleState.TransferResult.OVERFLOW) {
            source.sendError(Text.literal("Баланс получателя слишком большой для этого перевода."));
            return 0;
        }

        sendMessage(source, "Вы перевели " + format(amount) + " игроку " + playerName(target) + ".");
        target.sendMessage(Text.literal(playerName(sender) + " перевел вам " + format(amount) + "."), false);
        return 1;
    }

    private static int give(ServerCommandSource source, ServerPlayerEntity player, long amount) {
        RubleState state = RubleState.get(source.getServer());
        if (!state.add(player.getUuid(), amount)) {
            source.sendError(Text.literal("Баланс игрока слишком большой для начисления."));
            return 0;
        }

        sendMessage(source, "Начислено " + format(amount) + " игроку " + playerName(player) + ". Баланс: " + format(state.getBalance(player.getUuid())));
        player.sendMessage(Text.literal("Вам начислено " + format(amount) + "."), false);
        return 1;
    }

    private static int take(ServerCommandSource source, ServerPlayerEntity player, long amount) {
        RubleState state = RubleState.get(source.getServer());
        if (!state.subtractAllowingDebt(player.getUuid(), amount)) {
            source.sendError(Text.literal("Баланс игрока слишком маленький для списания этой суммы."));
            return 0;
        }

        sendMessage(source, "Списано " + format(amount) + " у игрока " + playerName(player) + ". Баланс: " + format(state.getBalance(player.getUuid())));
        player.sendMessage(Text.literal("У вас списано " + format(amount) + "."), false);
        return 1;
    }

    private static int set(ServerCommandSource source, ServerPlayerEntity player, long amount) {
        RubleState state = RubleState.get(source.getServer());
        state.setBalance(player.getUuid(), amount);

        sendMessage(source, "Баланс игрока " + playerName(player) + " установлен: " + format(amount));
        player.sendMessage(Text.literal("Ваш баланс установлен: " + format(amount)), false);
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

    private static String format(long amount) {
        return amount + " RUB";
    }
}
