package de.tecca.zyrox.end.fight;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class WeightedRandomSelectorTest {

    @Test
    void selectsEveryPositiveWeightAcrossItsRange() {
        Map<String, Integer> weights = new LinkedHashMap<>();
        weights.put("enderman", 2);
        weights.put("disabled", 0);
        weights.put("phantom", 3);
        weights.put("vex", 4);

        assertEquals("enderman", WeightedRandomSelector.choose(weights, 0).orElseThrow());
        assertEquals("enderman", WeightedRandomSelector.choose(weights, 1).orElseThrow());
        assertEquals("phantom", WeightedRandomSelector.choose(weights, 2).orElseThrow());
        assertEquals("phantom", WeightedRandomSelector.choose(weights, 4).orElseThrow());
        assertEquals("vex", WeightedRandomSelector.choose(weights, 5).orElseThrow());
        assertEquals("vex", WeightedRandomSelector.choose(weights, 8).orElseThrow());
    }

    @Test
    void ignoresNonPositiveWeightsAndEmptySelections() {
        assertEquals("vex", WeightedRandomSelector.choose(
                new LinkedHashMap<>(Map.of("disabled", 0, "vex", 1)),
                0
        ).orElseThrow());
        assertTrue(WeightedRandomSelector.choose(Map.of("disabled", 0, "negative", -2)).isEmpty());
    }
}
