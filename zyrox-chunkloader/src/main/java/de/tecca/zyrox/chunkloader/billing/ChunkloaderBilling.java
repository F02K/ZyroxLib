package de.tecca.zyrox.chunkloader.billing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChunkloaderBilling {

    private ChunkloaderBilling() {
    }

    public static List<Decision> evaluate(List<Input> inputs, Instant now, long baseCost, Duration cycle) {
        Map<UUID, List<Input>> byOwner = new LinkedHashMap<>();
        for (Input input : inputs) {
            byOwner.computeIfAbsent(input.ownerUuid(), ignored -> new ArrayList<>()).add(input);
        }

        List<Decision> decisions = new ArrayList<>();
        for (List<Input> ownerInputs : byOwner.values()) {
            ownerInputs.sort(Comparator.comparingInt(Input::priority).thenComparingLong(Input::id));
            int rank = 1;
            for (Input input : ownerInputs) {
                if (!input.enabled() || input.disabledWorld()) {
                    decisions.add(Decision.inactive(input, 0, 0, input.reserveBlocks(), input.paidUntil()));
                    continue;
                }
                Instant paidUntil = input.paidUntil();
                long cost = Math.multiplyExact(Math.max(1L, baseCost), rank);
                if (paidUntil != null && paidUntil.isAfter(now)) {
                    decisions.add(Decision.active(input, rank, cost, 0, input.reserveBlocks(), paidUntil));
                    rank++;
                    continue;
                }
                if (input.reserveBlocks() >= cost) {
                    Instant nextPaidUntil = now.plus(cycle);
                    decisions.add(Decision.active(input, rank, cost, cost, input.reserveBlocks() - cost, nextPaidUntil));
                    rank++;
                    continue;
                }
                decisions.add(Decision.inactive(input, rank, cost, input.reserveBlocks(), paidUntil));
            }
        }
        return decisions;
    }

    public record Input(
            long id,
            UUID ownerUuid,
            int priority,
            boolean enabled,
            boolean disabledWorld,
            long reserveBlocks,
            Instant paidUntil
    ) {
    }

    public record Decision(
            long id,
            UUID ownerUuid,
            boolean active,
            int rank,
            long nextCost,
            long chargedBlocks,
            long reserveAfter,
            Instant paidUntilAfter
    ) {

        private static Decision active(Input input, int rank, long nextCost, long chargedBlocks, long reserveAfter, Instant paidUntilAfter) {
            return new Decision(input.id(), input.ownerUuid(), true, rank, nextCost, chargedBlocks, reserveAfter, paidUntilAfter);
        }

        private static Decision inactive(Input input, int rank, long nextCost, long reserveAfter, Instant paidUntilAfter) {
            return new Decision(input.id(), input.ownerUuid(), false, rank, nextCost, 0, reserveAfter, paidUntilAfter);
        }
    }
}
