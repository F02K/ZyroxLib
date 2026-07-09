package de.tecca.zyrox.end.fight;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

public final class ParticleEffects {

    private ParticleEffects() {
    }

    public static void spawnSimple(World world, Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || location == null) {
            return;
        }
        if (requiresFloatData(particle)) {
            spawnFloat(world, particle, location, count, offsetX, offsetY, offsetZ, extra, 1.0F);
            return;
        }
        if (particle.getDataType() == Void.class) {
            try {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            } catch (IllegalArgumentException exception) {
                if (exception.getMessage() != null && exception.getMessage().contains("java.lang.Float")) {
                    spawnFloat(world, particle, location, count, offsetX, offsetY, offsetZ, extra, 1.0F);
                }
            }
            return;
        }
        world.spawnParticle(Particle.END_ROD, location, count, offsetX, offsetY, offsetZ, extra);
    }

    public static void spawnFloat(World world, Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, float data) {
        if (world == null || location == null) {
            return;
        }
        if (requiresFloatData(particle)) {
            try {
                world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
            } catch (IllegalArgumentException exception) {
                if (exception.getMessage() != null && exception.getMessage().contains("java.lang.Void")) {
                    world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
                }
            }
            return;
        }
        spawnSimple(world, particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    static boolean requiresFloatData(Particle particle) {
        return particle.getDataType() == Float.class
                || particle == Particle.DRAGON_BREATH
                || particle == Particle.PORTAL;
    }

    public static void spawnDust(World world, Location location, Color color, float size, int count, double offsetX, double offsetY, double offsetZ) {
        if (world == null || location == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, location, count, offsetX, offsetY, offsetZ, 0.0D, new Particle.DustOptions(color, size));
    }

    public static void spawnCircle(World world, Location center, double radius, Particle particle, int points) {
        if (world == null || center == null) {
            return;
        }
        int safePoints = Math.max(8, points);
        for (int i = 0; i < safePoints; i++) {
            double angle = Math.PI * 2.0D * i / safePoints;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.05D, Math.sin(angle) * radius);
            spawnSimple(world, particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void spawnHelix(World world, Location base, double radius, double height, double phase, Particle particle) {
        if (world == null || base == null) {
            return;
        }
        for (int i = 0; i < 18; i++) {
            double progress = i / 18.0D;
            double angle = phase + progress * Math.PI * 4.0D;
            Location point = base.clone().add(Math.cos(angle) * radius, progress * height, Math.sin(angle) * radius);
            spawnSimple(world, particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void spawnBeam(World world, Location from, Location to, Particle particle) {
        if (world == null || from == null || to == null) {
            return;
        }
        double distance = from.distance(to);
        int steps = Math.max(4, Math.min(32, (int) Math.ceil(distance / 3.0D)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location point = from.clone().add(
                    (to.getX() - from.getX()) * t,
                    (to.getY() - from.getY()) * t,
                    (to.getZ() - from.getZ()) * t
            );
            spawnSimple(world, particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void spawnShockwave(World world, Location center, double radius, Particle particle) {
        spawnCircle(world, center, radius, particle, Math.max(24, (int) (radius * 8.0D)));
    }

    public static void spawnForcedSimple(
            World world,
            Particle particle,
            Location location,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ,
            double extra
    ) {
        if (world == null || location == null) {
            return;
        }
        for (Player player : ritualViewers(world, location)) {
            if (requiresFloatData(particle)) {
                player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, 1.0F, true);
            } else if (particle.getDataType() == Void.class) {
                player.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, null, true);
            } else {
                player.spawnParticle(Particle.END_ROD, location, count, offsetX, offsetY, offsetZ, extra, null, true);
            }
        }
    }

    public static void spawnForcedDust(
            World world,
            Location location,
            Color color,
            float size,
            int count,
            double offsetX,
            double offsetY,
            double offsetZ
    ) {
        if (world != null && location != null) {
            for (Player player : ritualViewers(world, location)) {
                player.spawnParticle(
                        Particle.DUST,
                        location,
                        count,
                        offsetX,
                        offsetY,
                        offsetZ,
                        0.0D,
                        new Particle.DustOptions(color, size),
                        true
                );
            }
        }
    }

    public static void spawnForcedOrbit(
            World world,
            Location center,
            double radius,
            double phase,
            double yWave,
            Particle particle,
            int points
    ) {
        if (world == null || center == null) {
            return;
        }
        int safePoints = Math.max(8, points);
        for (int i = 0; i < safePoints; i++) {
            double angle = phase + Math.PI * 2.0D * i / safePoints;
            Location point = center.clone().add(
                    Math.cos(angle) * radius,
                    Math.sin(angle * 2.0D) * yWave,
                    Math.sin(angle) * radius
            );
            spawnForcedSimple(world, particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void spawnForcedBeam(World world, Location from, Location to, Particle particle, int maxSteps) {
        if (world == null || from == null || to == null) {
            return;
        }
        double distance = from.distance(to);
        int steps = Math.max(4, Math.min(Math.max(4, maxSteps), (int) Math.ceil(distance / 2.5D)));
        for (int i = 0; i <= steps; i++) {
            double progress = i / (double) steps;
            Location point = interpolate(from, to, progress);
            spawnForcedSimple(world, particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void spawnForcedHelix(
            World world,
            Location base,
            double radius,
            double height,
            double phase,
            Particle particle
    ) {
        if (world == null || base == null) {
            return;
        }
        for (int i = 0; i < 24; i++) {
            double progress = i / 24.0D;
            double angle = phase + progress * Math.PI * 5.0D;
            Location point = base.clone().add(
                    Math.cos(angle) * radius,
                    progress * height,
                    Math.sin(angle) * radius
            );
            spawnForcedSimple(world, particle, point, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    public static Location interpolate(Location from, Location to, double progress) {
        double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        return from.clone().add(
                (to.getX() - from.getX()) * clamped,
                (to.getY() - from.getY()) * clamped,
                (to.getZ() - from.getZ()) * clamped
        );
    }

    private static Iterable<Player> ritualViewers(World world, Location effectLocation) {
        return world.getPlayers().stream()
                .filter(player -> player.getLocation().distanceSquared(effectLocation) <= 192.0D * 192.0D)
                .toList();
    }
}
