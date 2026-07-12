package de.tecca.zyrox.end.fight;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

final class WeightedRandomSelector {

    private WeightedRandomSelector() {
    }

    static <T> Optional<T> choose(Map<T, Integer> weights) {
        int total = weights.values().stream()
                .filter(weight -> weight != null && weight > 0)
                .mapToInt(Integer::intValue)
                .sum();
        if (total <= 0) {
            return Optional.empty();
        }
        return choose(weights, ThreadLocalRandom.current().nextInt(total));
    }

    static <T> Optional<T> choose(Map<T, Integer> weights, int roll) {
        int remaining = Math.max(0, roll);
        for (Map.Entry<T, Integer> entry : weights.entrySet()) {
            int weight = entry.getValue() == null ? 0 : entry.getValue();
            if (weight <= 0) {
                continue;
            }
            if (remaining < weight) {
                return Optional.of(entry.getKey());
            }
            remaining -= weight;
        }
        return Optional.empty();
    }
}
