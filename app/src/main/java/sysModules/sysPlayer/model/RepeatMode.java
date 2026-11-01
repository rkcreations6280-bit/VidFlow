package sysModules.sysPlayer.model;

public enum RepeatMode {
    NONE, ONE, ALL;

    public RepeatMode cycleNext() {
        switch (this) {
            case NONE: return ONE;
            case ONE: return ALL;
            case ALL: return NONE;
            default: return NONE;
        }
    }
}
