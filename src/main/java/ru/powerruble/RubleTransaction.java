package ru.powerruble;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.nbt.NbtCompound;

public record RubleTransaction(
    String id,
    Type type,
    Instant time,
    Optional<UUID> actorId,
    String actorName,
    Optional<UUID> fromId,
    String fromName,
    Optional<UUID> toId,
    String toName,
    long amount,
    long fee,
    Optional<UUID> feeRecipientId,
    String feeRecipientName,
    String reason,
    String legacyText
) {
    private static final String ID_KEY = "id";
    private static final String TYPE_KEY = "type";
    private static final String TIME_KEY = "time";
    private static final String ACTOR_ID_KEY = "actorId";
    private static final String ACTOR_NAME_KEY = "actorName";
    private static final String FROM_ID_KEY = "fromId";
    private static final String FROM_NAME_KEY = "fromName";
    private static final String TO_ID_KEY = "toId";
    private static final String TO_NAME_KEY = "toName";
    private static final String AMOUNT_KEY = "amount";
    private static final String FEE_KEY = "fee";
    private static final String FEE_RECIPIENT_ID_KEY = "feeRecipientId";
    private static final String FEE_RECIPIENT_NAME_KEY = "feeRecipientName";
    private static final String REASON_KEY = "reason";
    private static final String LEGACY_TEXT_KEY = "legacyText";

    public static RubleTransaction legacy(Instant time, UUID ownerId, String ownerName, String text) {
        return new RubleTransaction(
            UUID.randomUUID().toString(),
            Type.LEGACY,
            time,
            Optional.empty(),
            "",
            Optional.empty(),
            "",
            Optional.of(ownerId),
            ownerName,
            0L,
            0L,
            Optional.empty(),
            "",
            "",
            text
        );
    }

    public static RubleTransaction transfer(
        Instant time,
        UUID senderId,
        String senderName,
        UUID targetId,
        String targetName,
        long amount,
        long fee,
        Optional<UUID> feeRecipientId,
        String feeRecipientName
    ) {
        return new RubleTransaction(
            UUID.randomUUID().toString(),
            Type.TRANSFER,
            time,
            Optional.of(senderId),
            senderName,
            Optional.of(senderId),
            senderName,
            Optional.of(targetId),
            targetName,
            amount,
            fee,
            feeRecipientId,
            feeRecipientName,
            "",
            ""
        );
    }

    public static RubleTransaction admin(
        Type type,
        Instant time,
        UUID targetId,
        String targetName,
        long amount,
        String reason
    ) {
        return new RubleTransaction(
            UUID.randomUUID().toString(),
            type,
            time,
            Optional.empty(),
            "admin",
            Optional.empty(),
            "",
            Optional.of(targetId),
            targetName,
            amount,
            0L,
            Optional.empty(),
            "",
            reason,
            ""
        );
    }

    public static RubleTransaction bank(Type type, Instant time, UUID bankId, String bankName, long amount, String reason) {
        return new RubleTransaction(
            UUID.randomUUID().toString(),
            type,
            time,
            Optional.empty(),
            "admin",
            Optional.empty(),
            "",
            Optional.of(bankId),
            bankName,
            amount,
            0L,
            Optional.empty(),
            "",
            reason,
            ""
        );
    }

    public static RubleTransaction fromNbt(NbtCompound nbt) {
        Type type = Type.fromName(nbt.getString(TYPE_KEY));
        Instant time = parseInstant(nbt.getString(TIME_KEY));
        return new RubleTransaction(
            nonBlank(nbt.getString(ID_KEY), UUID.randomUUID().toString()),
            type,
            time,
            uuid(nbt.getString(ACTOR_ID_KEY)),
            nbt.getString(ACTOR_NAME_KEY),
            uuid(nbt.getString(FROM_ID_KEY)),
            nbt.getString(FROM_NAME_KEY),
            uuid(nbt.getString(TO_ID_KEY)),
            nbt.getString(TO_NAME_KEY),
            nbt.getLong(AMOUNT_KEY),
            nbt.getLong(FEE_KEY),
            uuid(nbt.getString(FEE_RECIPIENT_ID_KEY)),
            nbt.getString(FEE_RECIPIENT_NAME_KEY),
            nbt.getString(REASON_KEY),
            nbt.getString(LEGACY_TEXT_KEY)
        );
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString(ID_KEY, id);
        nbt.putString(TYPE_KEY, type.name());
        nbt.putString(TIME_KEY, time.toString());
        actorId.ifPresent(uuid -> nbt.putString(ACTOR_ID_KEY, uuid.toString()));
        nbt.putString(ACTOR_NAME_KEY, actorName);
        fromId.ifPresent(uuid -> nbt.putString(FROM_ID_KEY, uuid.toString()));
        nbt.putString(FROM_NAME_KEY, fromName);
        toId.ifPresent(uuid -> nbt.putString(TO_ID_KEY, uuid.toString()));
        nbt.putString(TO_NAME_KEY, toName);
        nbt.putLong(AMOUNT_KEY, amount);
        nbt.putLong(FEE_KEY, fee);
        feeRecipientId.ifPresent(uuid -> nbt.putString(FEE_RECIPIENT_ID_KEY, uuid.toString()));
        nbt.putString(FEE_RECIPIENT_NAME_KEY, feeRecipientName);
        nbt.putString(REASON_KEY, reason);
        nbt.putString(LEGACY_TEXT_KEY, legacyText);
        return nbt;
    }

    public boolean involves(UUID playerId) {
        return actorId.filter(playerId::equals).isPresent()
            || fromId.filter(playerId::equals).isPresent()
            || toId.filter(playerId::equals).isPresent()
            || feeRecipientId.filter(playerId::equals).isPresent();
    }

    public String describe(String currencyName) {
        if (type == Type.LEGACY) {
            return legacyText;
        }

        String amountText = amount + " " + currencyName;
        String feeText = fee + " " + currencyName;
        String feeSuffix = feeRecipientName.isBlank() ? ", fee " + feeText : ", fee " + feeText + " -> " + feeRecipientName;
        return switch (type) {
            case TRANSFER -> time + " transfer " + fromName + " -> " + toName + ": " + amountText + feeSuffix;
            case ADMIN_GIVE -> time + " admin give -> " + toName + ": +" + amountText;
            case ADMIN_TAKE -> time + " admin take -> " + toName + ": -" + amountText + reasonSuffix();
            case ADMIN_SET -> time + " admin set -> " + toName + ": =" + amountText;
            case BANK_GIVE -> time + " bank give: +" + amountText;
            case BANK_TAKE -> time + " bank take: -" + amountText;
            case BANK_SET -> time + " bank set: =" + amountText;
            case TAX -> time + " tax -> " + toName + ": " + amountText;
            case LEGACY -> legacyText;
        };
    }

    private static Instant parseInstant(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Instant.EPOCH;
        }

        try {
            return Instant.parse(rawValue);
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }

    private static Optional<UUID> uuid(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(UUID.fromString(rawValue));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String reasonSuffix() {
        return reason.isBlank() ? "" : ", reason: " + reason;
    }

    public enum Type {
        TRANSFER,
        ADMIN_GIVE,
        ADMIN_TAKE,
        ADMIN_SET,
        BANK_GIVE,
        BANK_TAKE,
        BANK_SET,
        TAX,
        LEGACY;

        private static Type fromName(String name) {
            if (name == null || name.isBlank()) {
                return LEGACY;
            }

            try {
                return Type.valueOf(name);
            } catch (IllegalArgumentException exception) {
                return LEGACY;
            }
        }
    }
}
