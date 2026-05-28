package gaming.dsr.uwyg.equipment.equivalence;

import gaming.dsr.uwyg.equipment.EquipmentClassifier;
import gaming.dsr.uwyg.equipment.types.BaseEquipmentDefinition;
import gaming.dsr.uwyg.equipment.types.enums.ItemType;
import gaming.dsr.uwyg.equipment.types.enums.ItemUpgradePath;

/**
 * Maps armor {@code upgradeLevel} (+n on the item path) to a shared <strong>equivalent armor tier (+0…+5)</strong> scale so
 * {@link ItemUpgradePath#STANDARD_ARMOR} and {@link ItemUpgradePath#UNIQUE} pieces can be compared for auto-upgrade.
 *
 * <p>{@link ItemUpgradePath#NONE}: no upgrades — always {@code equivalentArmorTier} 0.
 *
 * <p><strong>UNIQUE</strong> (twinkling armor, max +5): {@code equivalentArmorTier} equals {@code upgradeLevel}.
 *
 * <p><strong>STANDARD_ARMOR</strong> (max {@code upgradeLevel} 10):
 *
 * <pre>
 * +0           → equivalentArmorTier 0 (same as UNIQUE +0)
 * +1, +2       → equivalentArmorTier 1 (same as UNIQUE +1)
 * +3, +4       → equivalentArmorTier 2
 * +5, +6       → equivalentArmorTier 3
 * +7, +8, +9   → equivalentArmorTier 4
 * +10          → equivalentArmorTier 5 (same as UNIQUE +5)
 * </pre>
 *
 * <p>When encoding a target {@code equivalentArmorTier} back to a STANDARD id, the <em>highest</em> {@code upgradeLevel}
 * in each equivalent armor tier band is used (+2, +4, +6, +9, +10).
 */
public final class ArmorUpgradeEquivalence {

    /** Matches UNIQUE max (+5) and STANDARD max mapped tier (+10 upgrade level → equivalentArmorTier 5). */
    private static final int MAXIMUM_EQUIVALENT_ARMOR_TIER = 5;

    private ArmorUpgradeEquivalence() {}

    /** Highest {@code equivalentArmorTier} this definition can reach on the shared scale (0…5). */
    public static int maximumEquivalentArmorTier(final BaseEquipmentDefinition equipmentDefinition) {
        return switch (equipmentDefinition.upgradePath()) {
            case UNIQUE, STANDARD_ARMOR -> MAXIMUM_EQUIVALENT_ARMOR_TIER;
            default -> 0;
        };
    }

    /**
     * {@code equivalentArmorTier} (0–5) from decoded {@code upgradeLevel}
     * ({@link EquipmentClassifier#decodeUpgradeLevel}).
     */
    public static int equivalentArmorTier(
            final BaseEquipmentDefinition equipmentDefinition,
            final int upgradeLevel
    ) {
        return switch (equipmentDefinition.upgradePath()) {
            case UNIQUE -> Math.clamp(upgradeLevel, 0, MAXIMUM_EQUIVALENT_ARMOR_TIER);
            case STANDARD_ARMOR -> standardArmorUpgradeLevelToEquivalentArmorTier(upgradeLevel);
            default -> 0;
        };
    }

    /** Catalog-space item id (no {@link ItemType} mask) for {@code targetEquivalentArmorTier}. */
    public static int catalogItemIdForEquivalentArmorTier(
            final BaseEquipmentDefinition equipmentDefinition,
            final int targetEquivalentArmorTier
    ) {
        final int definitionMaximumEquivalentArmorTier = maximumEquivalentArmorTier(equipmentDefinition);
        final int clampedTargetEquivalentArmorTier = Math.clamp(targetEquivalentArmorTier, 0, definitionMaximumEquivalentArmorTier);
        final long baseItemIdUnsigned = Integer.toUnsignedLong(equipmentDefinition.baseItemId());

        return switch (equipmentDefinition.upgradePath()) {
            case UNIQUE -> (int) (baseItemIdUnsigned + clampedTargetEquivalentArmorTier);
            case STANDARD_ARMOR -> {
                final int standardArmorBandMaximumUpgradeLevel =
                        maximumStandardArmorUpgradeLevelForEquivalentArmorTier(clampedTargetEquivalentArmorTier);
                final int catalogMaximumUpgradeLevel =
                        EquipmentClassifier.maximumUpgradeLevel(equipmentDefinition);
                final int appliedStandardArmorUpgradeLevel =
                        Math.min(standardArmorBandMaximumUpgradeLevel, catalogMaximumUpgradeLevel);
                yield (int) (baseItemIdUnsigned + appliedStandardArmorUpgradeLevel);
            }
            default -> equipmentDefinition.baseItemId();
        };
    }

    /**
     * Maps STANDARD {@code upgradeLevel} +0…+10 to {@code equivalentArmorTier} +0…+5 (inverse:
     * {@link #maximumStandardArmorUpgradeLevelForEquivalentArmorTier}).
     */
    private static int standardArmorUpgradeLevelToEquivalentArmorTier(final int upgradeLevel) {
        final int clampedUpgradeLevel = Math.clamp(upgradeLevel, 0, 10);
        if (clampedUpgradeLevel == 0) {
            return 0;
        }
        if (clampedUpgradeLevel <= 2) {
            return 1;
        }
        if (clampedUpgradeLevel <= 4) {
            return 2;
        }
        if (clampedUpgradeLevel <= 6) {
            return 3;
        }
        if (clampedUpgradeLevel <= 9) {
            return 4;
        }
        return 5;
    }

    /** Highest STANDARD {@code upgradeLevel} in the band for {@code targetEquivalentArmorTier} (0…5). */
    private static int maximumStandardArmorUpgradeLevelForEquivalentArmorTier(
            final int targetEquivalentArmorTier
    ) {
        final int clampedTargetEquivalentArmorTier = Math.clamp(targetEquivalentArmorTier, 0, MAXIMUM_EQUIVALENT_ARMOR_TIER);
        return switch (clampedTargetEquivalentArmorTier) {
            case 0 -> 0;
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 6;
            case 4 -> 9;
            default -> 10;
        };
    }
}
