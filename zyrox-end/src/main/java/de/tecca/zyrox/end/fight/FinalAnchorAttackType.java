package de.tecca.zyrox.end.fight;

enum FinalAnchorAttackType {
    GROUND_WAVE,
    ROTATING_BEAM;

    FinalAnchorAttackType next() {
        return this == GROUND_WAVE ? ROTATING_BEAM : GROUND_WAVE;
    }
}
