package gaming.dsr.uwyg.equipment.types.enums;

/**
 * Reinforcement behavior for a base item, derived from the third numeric field in the item definition
 * lists (armor, melee, ranged, shields): 0 none, 1 unique/twinkling, 2 standard armor, 3 weapon with full
 * infusion set, 4 weapon/shield/bow with a reduced infusion set, 5 pyromancy flame, 6 ascended flame.
 */
@SuppressWarnings("ALL")
public enum ItemUpgradePath {
    NONE,
    UNIQUE,
    STANDARD_ARMOR,
    INFUSABLE,
    INFUSABLE_RESTRICTED,
    PYRO_FLAME,
    PYRO_ASCENDED
}
