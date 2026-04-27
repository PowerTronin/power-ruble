package ru.powerruble;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import ru.powerruble.api.PowerRubleApi;

final class PowerRubleApiImpl implements PowerRubleApi {
    @Override
    public RubleConfig config() {
        return PowerRubleMod.config();
    }

    @Override
    public RubleMessages messages() {
        return PowerRubleMod.messages();
    }

    @Override
    public UUID bankAccountId() {
        return PowerRubleMod.bankId();
    }

    @Override
    public RubleState state(MinecraftServer server) {
        return RubleState.get(server);
    }

    @Override
    public long getBalance(MinecraftServer server, UUID accountId) {
        return state(server).getBalance(accountId);
    }

    @Override
    public String getAccountName(MinecraftServer server, UUID accountId) {
        return state(server).getName(accountId);
    }

    @Override
    public void rememberAccountName(MinecraftServer server, UUID accountId, String name) {
        state(server).rememberName(accountId, name);
    }

    @Override
    public void setBalance(MinecraftServer server, UUID accountId, long amount) {
        state(server).setBalance(accountId, amount);
    }

    @Override
    public boolean add(MinecraftServer server, UUID accountId, long amount) {
        return state(server).add(accountId, amount);
    }

    @Override
    public boolean subtract(MinecraftServer server, UUID accountId, long amount) {
        return state(server).subtract(accountId, amount);
    }

    @Override
    public boolean subtractAllowingDebt(MinecraftServer server, UUID accountId, long amount) {
        return state(server).subtractAllowingDebt(accountId, amount);
    }

    @Override
    public RubleState.TransferResult transfer(
        MinecraftServer server,
        UUID senderId,
        UUID targetId,
        @Nullable UUID feeRecipientId,
        long amount,
        long fee,
        long debtLimit
    ) {
        return state(server).transfer(senderId, targetId, feeRecipientId, amount, fee, debtLimit);
    }

    @Override
    public void addTransaction(MinecraftServer server, RubleTransaction transaction) {
        state(server).addTransaction(transaction);
    }
}
