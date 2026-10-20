package com.kintil555.football.action;

/** Every player action that can be performed on the ball. Sent client -> server by ordinal. */
public enum FootballAction {
    /** Released SPACE; payload arg = charge ticks held. Direction follows WASD input. */
    KICK,
    /** Middle click: lift the ball above the head, or throw it forward when already held. */
    HOLD,
    /** Left click on a player while holding the ball; payload arg = target entity id. */
    PASS,
    JUGGLE,
    FEINT,
    DRIBBLE;

    private static final FootballAction[] VALUES = values();

    public static FootballAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
