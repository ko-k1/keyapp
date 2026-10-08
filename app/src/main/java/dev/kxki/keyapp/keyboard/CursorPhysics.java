package dev.kxki.keyapp.keyboard;

/**
 * Pure space-long-press cursor math: quantizes a drag into key steps and
 * accelerates fast swipes with a smoothstep curve.
 */
public final class CursorPhysics {
    private CursorPhysics() {}

    /** Quantizes a drag distance into whole steps; 0 means "not far enough yet". */
    public static int steps(float distancePx, float stepPx) {
        return (int) (distancePx / stepPx);
    }

    /**
     * Accelerates {@code steps} by swipe speed. Both speeds are in px/sec;
     * {@code fullSpeedPxPerSec} is the speed that reaches maximum acceleration.
     */
    public static int accelerate(int steps, float speedPxPerSec, float fullSpeedPxPerSec) {
        if (steps == 0 || fullSpeedPxPerSec <= 0) return steps;
        float speed = Math.min(1f, Math.abs(speedPxPerSec) / fullSpeedPxPerSec);
        float acceleration = 1f + 2f * speed * speed * (3f - 2f * speed);
        return Math.round(Math.abs(steps) * acceleration) * Integer.signum(steps);
    }
}
