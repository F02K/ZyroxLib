package de.tecca.zyrox.end.fight;

public final class EndFightDamageCalculator {

    public double dragonDamage(double baseDamage, EndFightSession session, EndFightConfig config) {
        double damage = Math.max(0.0D, baseDamage);
        if (session.finalPhaseActive() && session.finalAnchorCount() > 0) {
            damage *= 1.0D - config.finalAnchorReduction();
        }

        double shieldReduction = Math.min(
                config.shieldReductionCap(),
                session.activeNodeCount(RitualNodeType.DRAGON_WARD) * config.shieldReductionPerTotem()
        );
        damage *= 1.0D - shieldReduction;

        if (session.exposed()) {
            damage *= 1.0D + config.exposedBonus();
        }
        return Math.max(0.0D, damage);
    }

    public double cappedExplosionDamage(double baseDamage, EndFightConfig config) {
        return Math.min(Math.max(0.0D, baseDamage), config.explosionDragonDamageCap());
    }
}
