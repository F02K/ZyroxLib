package de.tecca.zyrox.end.fight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class VisualCenterTest {

    @Test
    void wholeNumberCoordinatesMoveToBlockCenters() {
        assertEquals(0.5D, VisualCenter.coordinate(0.0D), 0.0001D);
        assertEquals(1.5D, VisualCenter.coordinate(1.0D), 0.0001D);
        assertEquals(-0.5D, VisualCenter.coordinate(-1.0D), 0.0001D);
        assertEquals(0.5D, VisualCenter.coordinate(0.5D), 0.0001D);
        assertEquals(12.25D, VisualCenter.coordinate(12.25D), 0.0001D);
    }
}
