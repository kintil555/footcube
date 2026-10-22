package com.kintil555.football.action;

/** Every player action that can be performed on the ball. Sent client -> server by ordinal. */
public enum FootballAction {
    /** Left click near the ball: kick along the crosshair. */
    KICK,
    /** Middle click on the ball: lift it above the head. */
    GRAB,
    /** Release of middle click while holding the ball; payload arg = charge ticks held. */
    THROW,
    /** Left click on a player while holding the ball; payload arg = target entity id. */
    PASS,
    /** Double-tap SPACE (or X): catch a falling ball, otherwise juggle / nutmeg. */
    JUGGLE,
    FEINT,
    /** SHIFT: drop the held ball or cancel the dribble to break away from the ball. */
    LET_GO,
    /** R: tackle the player in front; they fall for 4 seconds. */
    TACKLE,
    /** Left click on an incoming shot: the ball keeps its direction but loses a bit of speed. */
    SAVE;

    private static final FootballAction[] VALUES = values();

    public static FootballAction byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : null;
    }
}
