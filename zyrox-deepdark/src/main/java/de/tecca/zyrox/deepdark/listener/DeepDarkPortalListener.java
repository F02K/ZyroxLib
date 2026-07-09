package de.tecca.zyrox.deepdark.listener;

import de.tecca.zyrox.deepdark.portal.DeepDarkPortalService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class DeepDarkPortalListener implements Listener {

    private final DeepDarkPortalService portalService;

    public DeepDarkPortalListener(DeepDarkPortalService portalService) {
        this.portalService = portalService;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getItem() == null) {
            return;
        }
        if (event.getItem().getType() != portalService.activationItem()) {
            return;
        }

        portalService.activateFrame(event.getClickedBlock()).ifPresent(portal -> {
            event.setCancelled(true);
            event.getPlayer().sendMessage("DeepDark portal activated: " + portal.id());
        });
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().getBlock().equals(event.getTo().getBlock())) {
            return;
        }
        portalService.handlePortalMove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        if (portalService.handleVanillaNetherPortal(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (portalService.shouldBlockVanillaNetherPortal(event.getFrom())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event instanceof PlayerPortalEvent || event.getCause() != PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }
        if (portalService.handleVanillaNetherPortal(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        if (portalService.shouldBlockVanillaNetherPortal(event.getFrom())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (portalService.shouldBlockVanillaNetherPortal(event.getFrom())) {
            event.setCancelled(true);
        }
    }
}
