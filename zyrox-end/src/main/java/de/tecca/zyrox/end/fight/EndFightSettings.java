package de.tecca.zyrox.end.fight;

import org.bukkit.configuration.file.FileConfiguration;

public record EndFightSettings(
        Ritual ritual,
        RepeatProfile repeat,
        GuardianVisual guardianVisual,
        PhaseBudgets phaseBudgets
) {

    public static EndFightSettings from(FileConfiguration config) {
        return new EndFightSettings(
                new Ritual(
                        config.getBoolean("fight.first-ritual.enabled", true),
                        seconds(config.getDouble("fight.first-ritual.total-seconds", 90.0D)),
                        seconds(config.getDouble("fight.first-ritual.build-up-seconds", 15.0D)),
                        seconds(config.getDouble("fight.first-ritual.activation-seconds", 50.0D)),
                        seconds(config.getDouble("fight.first-ritual.finale-seconds", 8.0D)),
                        Math.max(1L, config.getLong("fight.first-ritual.animation-interval-ticks", 4L))
                ),
                new RepeatProfile(
                        clampMultiplier(config.getDouble("fight.profiles.repeat.dragon-health-multiplier", 0.85D)),
                        clampMultiplier(config.getDouble("fight.profiles.repeat.node-health-multiplier", 0.85D)),
                        clampMultiplier(config.getDouble("fight.profiles.repeat.damage-multiplier", 0.85D)),
                        Math.max(1.0D, config.getDouble("fight.profiles.repeat.cooldown-multiplier", 1.15D)),
                        clampMultiplier(config.getDouble("fight.profiles.repeat.mob-budget-multiplier", 0.75D))
                ),
                new GuardianVisual(
                        Math.max(0.25D, config.getDouble("fight.guardians.visual.scale", 1.15D)),
                        Math.max(1L, seconds(config.getDouble("fight.guardians.visual.particle-interval-seconds", 1.0D)))
                ),
                new PhaseBudgets(
                        budget(config, "opening", 0, 0, 0),
                        budget(config, "totems", 2, 2, 10),
                        budget(config, "escalation", 3, 2, 12),
                        budget(config, "final", 4, 2, 14)
                )
        );
    }

    public double dragonHealth(FightProfile profile, double base) {
        return base * (profile == FightProfile.REPEAT ? repeat.dragonHealthMultiplier() : 1.0D);
    }

    public double nodeHealth(FightProfile profile, double base) {
        return base * (profile == FightProfile.REPEAT ? repeat.nodeHealthMultiplier() : 1.0D);
    }

    public double damage(FightProfile profile, double base) {
        return base * (profile == FightProfile.REPEAT ? repeat.damageMultiplier() : 1.0D);
    }

    public long cooldown(FightProfile profile, long base) {
        double multiplier = profile == FightProfile.REPEAT ? repeat.cooldownMultiplier() : 1.0D;
        return Math.max(1L, Math.round(base * multiplier));
    }

    public int guardianBudget(FightProfile profile, FightPhase phase, int playerCount) {
        Budget budget = phaseBudgets.forPhase(phase);
        int total = Math.min(budget.maximum(), budget.base() + budget.perPlayer() * Math.max(0, playerCount));
        if (profile == FightProfile.REPEAT) {
            total = (int) Math.ceil(total * repeat.mobBudgetMultiplier());
        }
        return Math.max(0, total);
    }

    private static Budget budget(FileConfiguration config, String phase, int base, int perPlayer, int maximum) {
        String path = "fight.guardians.phase-budgets." + phase + ".";
        return new Budget(
                Math.max(0, config.getInt(path + "base", base)),
                Math.max(0, config.getInt(path + "per-player", perPlayer)),
                Math.max(0, config.getInt(path + "maximum", maximum))
        );
    }

    private static double clampMultiplier(double value) {
        return Math.max(0.0D, value);
    }

    private static long seconds(double seconds) {
        return Math.max(1L, Math.round(seconds * 20.0D));
    }

    public record Ritual(
            boolean enabled,
            long totalTicks,
            long buildUpTicks,
            long activationTicks,
            long finaleTicks,
            long animationIntervalTicks
    ) {
        public Ritual {
            buildUpTicks = Math.min(buildUpTicks, totalTicks);
            activationTicks = Math.min(activationTicks, Math.max(1L, totalTicks - buildUpTicks));
            finaleTicks = Math.min(finaleTicks, totalTicks);
            animationIntervalTicks = Math.max(1L, animationIntervalTicks);
        }

        public long convergenceStartTick() {
            return Math.min(totalTicks, buildUpTicks + activationTicks);
        }

        public long finaleStartTick() {
            return Math.max(convergenceStartTick(), totalTicks - finaleTicks);
        }
    }

    public record RepeatProfile(
            double dragonHealthMultiplier,
            double nodeHealthMultiplier,
            double damageMultiplier,
            double cooldownMultiplier,
            double mobBudgetMultiplier
    ) {
    }

    public record GuardianVisual(double scale, long particleIntervalTicks) {
    }

    public record PhaseBudgets(Budget opening, Budget totems, Budget escalation, Budget finalPhase) {
        public Budget forPhase(FightPhase phase) {
            return switch (phase) {
                case OPENING -> opening;
                case TOTEMS -> totems;
                case ESCALATION -> escalation;
                case FINAL -> finalPhase;
            };
        }
    }

    public record Budget(int base, int perPlayer, int maximum) {
    }
}
