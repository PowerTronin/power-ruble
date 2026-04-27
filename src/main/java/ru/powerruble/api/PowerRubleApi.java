package ru.powerruble.api;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import ru.powerruble.RubleConfig;
import ru.powerruble.RubleMessages;
import ru.powerruble.RubleState;
import ru.powerruble.RubleTransaction;

public interface PowerRubleApi {
    RubleConfig config();

    RubleMessages messages();

    UUID bankAccountId();

    RubleState state(MinecraftServer server);

    long getBalance(MinecraftServer server, UUID accountId);

    String getAccountName(MinecraftServer server, UUID accountId);

    void rememberAccountName(MinecraftServer server, UUID accountId, String name);

    void setBalance(MinecraftServer server, UUID accountId, long amount);

    boolean add(MinecraftServer server, UUID accountId, long amount);

    boolean subtract(MinecraftServer server, UUID accountId, long amount);

    boolean subtractAllowingDebt(MinecraftServer server, UUID accountId, long amount);

    RubleState.TransferResult transfer(
        MinecraftServer server,
        UUID senderId,
        UUID targetId,
        @Nullable UUID feeRecipientId,
        long amount,
        long fee,
        long debtLimit
    );

    void addTransaction(MinecraftServer server, RubleTransaction transaction);
}
