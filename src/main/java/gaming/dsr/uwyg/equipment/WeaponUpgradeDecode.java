package gaming.dsr.uwyg.equipment;

import gaming.dsr.uwyg.equipment.types.enums.WeaponInfusionPath;

/**
 * Decoded weapon upgrade state from a catalog item id: {@link Twinkling}, {@link Infused}, or pyromancy flames.
 */
public sealed interface WeaponUpgradeDecode
        permits WeaponUpgradeDecode.Twinkling,
                WeaponUpgradeDecode.Infused,
                WeaponUpgradeDecode.PyromancyFlame {

    /** Twinkling / UNIQUE offset treated as upgrade level +0…+5 on that path. */
    record Twinkling(int twinklingUpgradeLevel) implements WeaponUpgradeDecode {}

    record Infused(WeaponInfusionPath infusionPath, int upgradeLevel) implements WeaponUpgradeDecode {}

    /**
     * Pyromancy flame reinforcement (+{@code upgradeSteps}×100 on the catalog id); ascended flame uses steps 0…5 only.
     */
    record PyromancyFlame(int upgradeSteps) implements WeaponUpgradeDecode {}
}
