package com.xeon.runexplorer.util;

public final class CombatLevel {

    private CombatLevel() {
    }

    // OSRS combat formula (approx; standard)
    public static int osrsCombat(int att, int str, int def, int hp, int pray, int range, int mage) {
        double base = 0.25 * (def + hp + Math.floor(pray / 2.0));
        double melee = 0.325 * (att + str);
        double ranged = 0.325 * (Math.floor(range / 2.0) + range);
        double magic = 0.325 * (Math.floor(mage / 2.0) + mage);
        return (int) Math.floor(base + Math.max(melee, Math.max(ranged, magic)));
    }

    // RS3 combat is more complex; return -1 if you want to avoid "wrong" values.
    public static int rs3CombatUnknown() {
        return -1;
    }
}
