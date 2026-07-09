package de.tecca.zyrox.end.command;

import de.tecca.zyrox.end.plugin.ZyroxEndPlugin;
import de.tecca.zyrox.end.fight.EndFightSession;
import de.tecca.zyrox.end.fight.PillarRitualNode;
import de.tecca.zyrox.end.service.FirstDragonEggState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.block.Block;
import org.bukkit.Material;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ZyroxEndCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());

    private final ZyroxEndPlugin plugin;

    public ZyroxEndCommand(ZyroxEndPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            sender.sendMessage("ZyroxEnd reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("egg") && args.length >= 2 && args[1].equalsIgnoreCase("bind")) {
            bindLegacyEgg(sender);
            return true;
        }

        sender.sendMessage("Usage: /" + label + " <reload|status|egg bind>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(plugin.adminPermission())) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("reload", "status", "egg").stream()
                    .filter(option -> option.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("egg")) {
            return List.of("bind").stream()
                    .filter(option -> option.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    private void sendStatus(CommandSender sender) {
        FirstDragonEggState state = plugin.dragonEggService().state();
        sender.sendMessage("ZyroxEnd status:");
        sender.sendMessage("First dragon killed: " + yesNo(state.firstDragonKilled()));
        sender.sendMessage("First ritual: " + state.ritualStatus()
                + state.ritualWorldName().map(world -> " (" + world + ")").orElse(""));
        if (state.firstDragonKilled()) {
            sender.sendMessage("First dragon world: " + state.firstDragonWorldName().orElse(""));
            sender.sendMessage("First dragon killed at: " + formatTimestamp(state.firstDragonKilledAtEpochMillis()));
        }
        sender.sendMessage("First egg claimed: " + yesNo(state.eggClaimed()));
        sender.sendMessage("Egg claim phase: " + state.eggClaimPhase());
        if (state.eggClaimed()) {
            sender.sendMessage("Egg id: " + state.eggId().map(Object::toString).orElse(""));
            sender.sendMessage("Owner: " + state.ownerName().orElse("") + " ("
                    + state.ownerUniqueId().map(Object::toString).orElse("") + ")");
            sender.sendMessage("Claimed at: " + formatTimestamp(state.claimedAtEpochMillis()));
        }
        state.pendingEggClaim().ifPresent(claim ->
                sender.sendMessage("Pending egg id: " + claim.eggId() + " owner=" + claim.ownerName()));
        state.eggTracking().ifPresent(tracking -> {
            sender.sendMessage("Egg tracking: " + tracking.form() + " (" + tracking.description() + ")");
            sender.sendMessage("Egg tracking updated: " + formatTimestamp(tracking.updatedAtEpochMillis()));
        });
        sender.sendMessage("Active fights: " + plugin.fightService().sessions().size());
        for (EndFightSession session : plugin.fightService().sessions()) {
            sender.sendMessage("- " + session.worldName()
                    + ": profile=" + session.fightProfile()
                    + ", activation=" + session.activationState()
                    + ", phase=" + session.phase()
                    + ", pillars=" + session.detectedPillarCount()
                    + ", activeNodes=" + session.activeNodeCount()
                    + ", lockedNodes=" + session.lockedNodeCount()
                    + ", finalAnchors=" + session.finalAnchorCount()
                    + ", tempCrystals=" + session.temporaryCrystalCount()
                    + ", participants=" + session.activeParticipantCount(plugin.fightService().config())
                    + ", guardians=" + session.guardianUniqueIds().size()
                    + ", guardianBudgetUsed=" + session.guardianBudgetConsumed(session.phase())
                    + ", final=" + yesNo(session.finalPhaseActive())
                    + ", exposed=" + yesNo(session.exposed()));
            for (PillarRitualNode node : session.nodes()) {
                if (!node.active()) {
                    continue;
                }
                sender.sendMessage("  - " + node.type()
                        + " phase=" + node.requiredPhase()
                        + " " + (node.locked() ? "locked" : "unlocked")
                        + " " + String.format("%.0f/%.0f", node.health(), node.maxHealth())
                        + (node.temporaryCrystal() ? " temporary" : ""));
            }
        }
    }

    private void bindLegacyEgg(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by a player.");
            return;
        }
        Block target = player.getTargetBlockExact(6);
        if (target == null || target.getType() != Material.DRAGON_EGG) {
            sender.sendMessage("Look directly at the Dragon Egg to bind it.");
            return;
        }
        if (!plugin.dragonEggService().bindLegacyBlock(target.getLocation())) {
            sender.sendMessage("The legacy Egg could not be bound. A claimed Egg ID is required.");
            return;
        }
        sender.sendMessage("The placed Dragon Egg is now bound to the stored first Egg ID.");
    }

    private String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0L) {
            return "unknown";
        }
        return DATE_FORMATTER.format(Instant.ofEpochMilli(epochMillis));
    }
}
