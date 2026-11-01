package sysModules.sysPlayer.model;

public final class PlaybackState {

    public enum Phase {
        PREFLIGHT, BLOCKED, BUFFERING, PLAYING, PAUSED, PAUSED_SEEK, COMPLETED;

        public boolean canTransitionTo(Phase target) {
            switch (this) {
                case PREFLIGHT:
                    return target == BLOCKED || target == BUFFERING;
                case BLOCKED:
                    return target == BUFFERING;
                case BUFFERING:
                    return target == PLAYING || target == PAUSED || target == COMPLETED || target == PREFLIGHT;
                case PLAYING:
                    return target == PAUSED || target == BUFFERING || target == COMPLETED || target == PREFLIGHT;
                case PAUSED:
                    return target == PLAYING || target == BUFFERING || target == PAUSED_SEEK || target == PREFLIGHT;
                case PAUSED_SEEK:
                    return target == PAUSED || target == PLAYING || target == PREFLIGHT;
                case COMPLETED:
                    return target == PREFLIGHT || target == BUFFERING;
                default:
                    return false;
            }
        }

        public boolean isPlaying() {
            return this == PLAYING || this == BUFFERING;
        }

        public boolean isPaused() {
            return this == PAUSED || this == PAUSED_SEEK;
        }
    }

    public static final class IllegalStateTransitionException extends RuntimeException {
        public IllegalStateTransitionException(Phase from, Phase to) {
            super("Cannot transition from " + from + " to " + to);
        }
    }

    private final Phase phase;

    private PlaybackState(Phase phase) {
        this.phase = phase;
    }

    public Phase getPhase() {
        return phase;
    }

    public PlaybackState transitionTo(Phase target) {
        if (!phase.canTransitionTo(target)) {
            throw new IllegalStateTransitionException(phase, target);
        }
        return new PlaybackState(target);
    }

    public static PlaybackState preflight() { return new PlaybackState(Phase.PREFLIGHT); }
    public static PlaybackState blocked() { return new PlaybackState(Phase.BLOCKED); }
    public static PlaybackState buffering() { return new PlaybackState(Phase.BUFFERING); }
    public static PlaybackState playing() { return new PlaybackState(Phase.PLAYING); }
    public static PlaybackState paused() { return new PlaybackState(Phase.PAUSED); }
    public static PlaybackState pausedSeek() { return new PlaybackState(Phase.PAUSED_SEEK); }
    public static PlaybackState completed() { return new PlaybackState(Phase.COMPLETED); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaybackState that = (PlaybackState) o;
        return phase == that.phase;
    }

    @Override
    public int hashCode() {
        return phase.hashCode();
    }

    @Override
    public String toString() {
        return phase.name();
    }
}
