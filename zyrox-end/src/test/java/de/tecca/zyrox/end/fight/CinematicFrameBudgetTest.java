package de.tecca.zyrox.end.fight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CinematicFrameBudgetTest {

    @Test
    void reservesCapacityForTelegraphsAndImpacts() {
        CinematicFrameBudget budget = new CinematicFrameBudget(100);

        assertEquals(50, budget.claim(CinematicFrameBudget.Priority.AMBIENT, 90));
        assertEquals(30, budget.claim(CinematicFrameBudget.Priority.TELEGRAPH, 90));
        assertEquals(20, budget.claim(CinematicFrameBudget.Priority.IMPACT, 90));
        assertEquals(100, budget.used());
        assertEquals(0, budget.remaining());
    }

    @Test
    void neverGrantsNegativeOrMoreThanRequested() {
        CinematicFrameBudget budget = new CinematicFrameBudget(200);

        assertEquals(0, budget.claim(CinematicFrameBudget.Priority.IMPACT, -4));
        assertEquals(12, budget.claim(CinematicFrameBudget.Priority.IMPACT, 12));
    }
}
