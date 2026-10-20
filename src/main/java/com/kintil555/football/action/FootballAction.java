package com.kintil555.football.action;

/** Every player action that can be performed on the ball. Sent client -> server by ordinal. */
public enum FootballAction {
    KICK,
    LONG_KICK,
    THROW,
    JUGGLE,
    FEINT,
    DRIBBLE;

    private static final FootballAction[] VALUES = values();

    public static FootballAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
