package sysModules.sysPlayer.model;

public final class PlaybackData {
    private final PlaybackState.Phase phase;
    private final long position;
    private final long duration;
    private final int bufferPercent;
    private final RepeatMode repeatMode;
    private final boolean isShuffled;
    private final float speed;
    private final float pitch;
    private final boolean isMuted;

    private PlaybackData(Builder builder) {
        this.phase = builder.phase;
        this.position = builder.position;
        this.duration = builder.duration;
        this.bufferPercent = builder.bufferPercent;
        this.repeatMode = builder.repeatMode;
        this.isShuffled = builder.isShuffled;
        this.speed = builder.speed;
        this.pitch = builder.pitch;
        this.isMuted = builder.isMuted;
    }

    public PlaybackState.Phase getPhase() { return phase; }
    public long getPosition() { return position; }
    public long getDuration() { return duration; }
    public int getBufferPercent() { return bufferPercent; }
    public RepeatMode getRepeatMode() { return repeatMode; }
    public boolean isShuffled() { return isShuffled; }
    public float getSpeed() { return speed; }
    public float getPitch() { return pitch; }
    public boolean isMuted() { return isMuted; }

    public boolean isPlaying() { return phase != null && phase.isPlaying(); }
    public boolean isPaused() { return phase != null && phase.isPaused(); }
    public boolean isCompleted() { return phase == PlaybackState.Phase.COMPLETED; }

    public static final class Builder {
        private PlaybackState.Phase phase = PlaybackState.Phase.PREFLIGHT;
        private long position;
        private long duration;
        private int bufferPercent;
        private RepeatMode repeatMode = RepeatMode.NONE;
        private boolean isShuffled;
        private float speed = 1.0f;
        private float pitch = 1.0f;
        private boolean isMuted;

        public Builder phase(PlaybackState.Phase phase) { this.phase = phase; return this; }
        public Builder position(long position) { this.position = position; return this; }
        public Builder duration(long duration) { this.duration = duration; return this; }
        public Builder bufferPercent(int bufferPercent) { this.bufferPercent = bufferPercent; return this; }
        public Builder repeatMode(RepeatMode repeatMode) { this.repeatMode = repeatMode; return this; }
        public Builder shuffled(boolean shuffled) { this.isShuffled = shuffled; return this; }
        public Builder speed(float speed) { this.speed = speed; return this; }
        public Builder pitch(float pitch) { this.pitch = pitch; return this; }
        public Builder muted(boolean muted) { this.isMuted = muted; return this; }

        public PlaybackData build() {
            return new PlaybackData(this);
        }
    }
}
