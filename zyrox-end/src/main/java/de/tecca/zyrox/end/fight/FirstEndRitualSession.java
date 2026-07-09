package de.tecca.zyrox.end.fight;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class FirstEndRitualSession {

    private final List<UUID> orderedNodeIds;
    private final Map<UUID, UUID> evokerByNode = new HashMap<>();
    private long elapsedTicks;
    private int activatedNodes;

    FirstEndRitualSession(List<PillarRitualNode> nodes, Location center) {
        orderedNodeIds = new ArrayList<>(nodes.stream()
                .sorted(Comparator.comparingDouble(node -> angle(center, node.pillarTop())))
                .map(PillarRitualNode::id)
                .toList());
    }

    long elapsedTicks() {
        return elapsedTicks;
    }

    long advance(long ticks) {
        long previous = elapsedTicks;
        elapsedTicks += Math.max(0L, ticks);
        return previous;
    }

    int nodeCount() {
        return orderedNodeIds.size();
    }

    int activatedNodes() {
        return activatedNodes;
    }

    int targetActivatedNodes(long buildUpTicks, long activationTicks) {
        if (elapsedTicks < buildUpTicks || orderedNodeIds.isEmpty()) {
            return 0;
        }
        long progress = Math.min(activationTicks, elapsedTicks - buildUpTicks);
        return Math.min(orderedNodeIds.size(), (int) Math.floor(progress * orderedNodeIds.size() / (double) activationTicks));
    }

    Optional<UUID> channelingNodeId(long buildUpTicks, long activationTicks) {
        if (elapsedTicks < buildUpTicks || elapsedTicks >= buildUpTicks + activationTicks) {
            return Optional.empty();
        }
        return nextNodeId();
    }

    double channelProgress(long buildUpTicks, long activationTicks) {
        if (orderedNodeIds.isEmpty() || elapsedTicks < buildUpTicks) {
            return 0.0D;
        }
        double slotTicks = activationTicks / (double) orderedNodeIds.size();
        double slotStart = buildUpTicks + activatedNodes * slotTicks;
        return Math.max(0.0D, Math.min(1.0D, (elapsedTicks - slotStart) / slotTicks));
    }

    FirstEndRitualAct act(long buildUpTicks, long activationTicks, long finaleStartTicks, long totalTicks) {
        if (elapsedTicks >= totalTicks) {
            return FirstEndRitualAct.COMPLETE;
        }
        if (elapsedTicks >= finaleStartTicks) {
            return FirstEndRitualAct.FINALE;
        }
        if (elapsedTicks >= buildUpTicks + activationTicks) {
            return FirstEndRitualAct.CONVERGENCE;
        }
        if (elapsedTicks >= buildUpTicks) {
            return FirstEndRitualAct.TRANSFORMATIONS;
        }
        return FirstEndRitualAct.AWAKENING;
    }

    boolean crossed(long previousTicks, long thresholdTicks) {
        return previousTicks < thresholdTicks && elapsedTicks >= thresholdTicks;
    }

    Optional<UUID> nextNodeId() {
        if (activatedNodes >= orderedNodeIds.size()) {
            return Optional.empty();
        }
        return Optional.of(orderedNodeIds.get(activatedNodes));
    }

    void markActivated() {
        activatedNodes = Math.min(orderedNodeIds.size(), activatedNodes + 1);
    }

    void bindEvoker(UUID nodeId, UUID evokerId) {
        evokerByNode.put(nodeId, evokerId);
    }

    Optional<UUID> evoker(UUID nodeId) {
        return Optional.ofNullable(evokerByNode.get(nodeId));
    }

    List<UUID> evokerIds() {
        return List.copyOf(evokerByNode.values());
    }

    private static double angle(Location center, Location location) {
        return Math.atan2(location.getZ() - center.getZ(), location.getX() - center.getX());
    }
}
