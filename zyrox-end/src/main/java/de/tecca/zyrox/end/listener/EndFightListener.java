package de.tecca.zyrox.end.listener;

import de.tecca.zyrox.end.fight.EndFightService;
import org.bukkit.World;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class EndFightListener implements Listener {

    private final EndFightService fightService;

    public EndFightListener(EndFightService fightService) {
        this.fightService = fightService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof EnderDragon dragon) {
            fightService.trackDragon(dragon);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (event.getPlayer().getWorld().getEnvironment() == World.Environment.THE_END) {
            fightService.handleEndEntry(event.getPlayer());
            fightService.markActive(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer().getWorld().getEnvironment() == World.Environment.THE_END) {
            fightService.ensureFirstRitualStarted(event.getPlayer().getWorld());
            fightService.markActive(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        if (dragon.getWorld().getEnvironment() != World.Environment.THE_END) {
            return;
        }

        double damage = event.getDamage();
        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
            damage = fightService.cappedExplosionDamage(damage);
        }
        event.setDamage(fightService.dragonDamage(dragon, damage));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player player = playerFromDamager(event.getDamager()).orElse(null);
        if (event.getEntity() instanceof EnderCrystal crystal && fightService.isLinkedCrystal(crystal)) {
            event.setCancelled(true);
            fightService.damageCrystalNode(crystal, player, crystalDamage(event.getDamager()), event.getDamager().getLocation());
            return;
        }

        if (player == null) {
            return;
        }
        Entity target = event.getEntity();
        if (target instanceof EnderDragon || target instanceof EnderCrystal) {
            fightService.markActive(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null || event.getFrom().distanceSquared(event.getTo()) < 1.0D) {
            return;
        }
        fightService.markActive(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }
        fightService.handlePlayerTeleport(event.getPlayer(), event.getFrom(), event.getTo());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragonDeath(EntityDeathEvent event) {
        if (fightService.isSpecialFightMob(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        if (event.getEntity() instanceof EnderDragon dragon) {
            fightService.finishDragon(dragon);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDragonRegainHealthLowest(EntityRegainHealthEvent event) {
        blockDragonRegainHealth(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonRegainHealthHighest(EntityRegainHealthEvent event) {
        blockDragonRegainHealth(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrystalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderCrystal crystal) || event instanceof EntityDamageByEntityEvent) {
            return;
        }
        if (fightService.isLinkedCrystal(crystal)) {
            event.setCancelled(true);
            if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                    || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {
                fightService.damageCrystalNode(crystal, null, fightService.config().crystalExplosionDamage(), crystal.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCrystalDamageByEntity(EntityDamageByEntityEvent event) {
        // Linked crystal damage is handled and cancelled at HIGHEST priority.
    }

    private java.util.Optional<Player> playerFromDamager(Entity damager) {
        if (damager instanceof Player player) {
            return java.util.Optional.of(player);
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            if (source instanceof Player player) {
                return java.util.Optional.of(player);
            }
        }
        return java.util.Optional.empty();
    }

    private double crystalDamage(Entity damager) {
        if (damager instanceof Projectile) {
            return fightService.config().crystalProjectileDamage();
        }
        return fightService.config().crystalMeleeDamage();
    }

    private void blockDragonRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof EnderDragon dragon)) {
            return;
        }
        if (!fightService.blockVanillaDragonHealing(dragon)) {
            return;
        }

        event.setAmount(0.0D);
        event.setCancelled(true);
    }
}
