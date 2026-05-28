package gaming.dsr.uwyg.equipment.types.enums;

import lombok.Getter;

/**
 * Weapon/shield/bow reinforcement infusion. Each path has its own maximum reinforcement level.
 * The {@code unavailableForRestrictedInfusable} flag marks paths that are not offered for equipment
 * whose upgrade path is {@link ItemUpgradePath#INFUSABLE_RESTRICTED}.
 */
@Getter
public enum WeaponInfusionPath {
    NORMAL(0, 15, false),
    CRYSTAL(100, 5, false),
    RAW(300, 5, true),
    MAGIC(400, 10, false),
    ENCHANTED(500, 5, true),
    DIVINE(600, 10, false),
    OCCULT(700, 5, true),
    FIRE(800, 10, false),
    CHAOS(900, 5, true),
    LIGHTNING(200, 5, false);

    private final int itemIdOffset;
    private final int maxReinforcement;
    private final boolean unavailableForRestrictedInfusable;

    WeaponInfusionPath(
            final int itemIdOffset,
            final int maxReinforcement,
            final boolean unavailableForRestrictedInfusable
    ) {
        this.itemIdOffset = itemIdOffset;
        this.maxReinforcement = maxReinforcement;
        this.unavailableForRestrictedInfusable = unavailableForRestrictedInfusable;
    }
}
