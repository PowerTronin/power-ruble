package ru.powerruble.api;

public final class PowerRuble {
    private static volatile PowerRubleApi api;

    private PowerRuble() {
    }

    public static void setApi(PowerRubleApi api) {
        PowerRuble.api = api;
    }

    public static PowerRubleApi api() {
        PowerRubleApi current = api;
        if (current == null) {
            throw new IllegalStateException("Power Ruble API is not available yet.");
        }

        return current;
    }
}
