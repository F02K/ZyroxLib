package de.tecca.zyrox.end.fight;

final class CinematicFrameBudget {

    enum Priority {
        AMBIENT,
        TELEGRAPH,
        IMPACT
    }

    private final int maximum;
    private int used;

    CinematicFrameBudget(int maximum) {
        this.maximum = Math.max(1, maximum);
    }

    int claim(Priority priority, int requested) {
        int safeRequested = Math.max(0, requested);
        int reserve = switch (priority) {
            case AMBIENT -> maximum / 2;
            case TELEGRAPH -> maximum * 4 / 5;
            case IMPACT -> maximum;
        };
        int allowed = Math.max(0, reserve - used);
        int granted = Math.min(safeRequested, allowed);
        used += granted;
        return granted;
    }

    int used() {
        return used;
    }

    int remaining() {
        return Math.max(0, maximum - used);
    }
}
