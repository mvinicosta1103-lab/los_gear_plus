package com.example.losgearplus.shiftarm;

/** Balance / feel values of the Shifter Arm. Tweak only here. Times are in ticks (20 = 1 s). */
public final class ShifterArmConfig {
    private ShifterArmConfig() {}

    /** Cooldown after turning the arm on or off (creative ignores). */
    public static final int TOGGLE_COOLDOWN_TICKS = 40;
    /** Pause between two actions (punch / grab / throw). */
    public static final int ACTION_COOLDOWN_TICKS = 6;

    // Animation lengths (the animation file: punch 0.96 s, grab 1.0 s, throw 1.25 s)
    public static final int PUNCH_TICKS = 19;
    public static final int GRAB_TICKS = 20;
    public static final int THROW_TICKS = 25;
    /** Tick of each animation where the effect happens. */
    public static final int PUNCH_IMPACT_TICK = 12;
    public static final int GRAB_CLOSE_TICK = 12;
    public static final int THROW_RELEASE_TICK = 16;

    /** Hand position, relative to the owner's feet: forward / to the right / up (blocks). The arm is ~4.5 blocks long. */
    public static final double HAND_FORWARD = 3.6;
    public static final double HAND_RIGHT = 0.3;
    public static final double HAND_UP = 1.35;

    /** Hit zone of the punch / grab (box around the hand zone). */
    public static final double REACH_FORWARD = 3.4;
    public static final double REACH_HALF_WIDTH = 1.7;
    public static final double REACH_HALF_HEIGHT = 1.4;

    public static final float PUNCH_DAMAGE = 12.0f;
    public static final double PUNCH_KNOCKBACK = 1.9;
    public static final double PUNCH_LIFT = 0.45;

    /** Biggest being (hitbox height, in blocks) the arm can hold. Bigger ones (titans) cannot be grabbed. */
    public static final float MAX_GRAB_HEIGHT = 4.0f;
    /** The held being is released after this long, or if it gets farther than this from the owner. */
    public static final int MAX_HOLD_TICKS = 20 * 12;
    public static final double MAX_HOLD_DISTANCE = 8.0;

    public static final double THROW_SPEED = 2.3;
    public static final double THROW_LIFT = 0.30;
    /**
     * Thrown beings get this much "fall distance" (plus a bit per tick in the air, capped), so the NORMAL fall damage
     * (armor, Feather Falling, water, etc. all apply) hurts when they land. Vanilla damage = distance - 3.
     */
    public static final float THROW_FALL_BONUS = 16.0f;
    public static final float THROW_FALL_MAX = 34.0f;
    public static final int THROW_TRACK_MAX_TICKS = 20 * 8;
    /** Hitting a wall at speed while thrown. */
    public static final double WALL_SLAM_SPEED = 0.7;
    public static final float WALL_SLAM_DAMAGE = 6.0f;

    /** false = players cannot be grabbed (punch still follows the server's PvP setting). */
    public static final boolean CAN_GRAB_PLAYERS = true;
}
