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
 * +0, +1       → equivalentArmorTier 0 (same as UNIQUE +0)
 * +2, +3       → equivalentArmorTier 1 (same as UNIQUE +1)
 * +4, +5       → equivalentArmorTier 2
 * +6, +7       → equivalentArmorTier 3
 * +8, +9       → equivalentArmorTier 4
 * +10          → equivalentArmorTier 5 (same as UNIQUE +5)
 * </pre>
 *
 * <p>The reverse direction is a UNIQUE→STANDARD_ARMOR conversion: because the {@code equivalentArmorTier} is the
 * UNIQUE/twinkling scale (tier equals UNIQUE {@code upgradeLevel}), a tier converts to the STANDARD_ARMOR
 * {@code upgradeLevel} of the same strength — UNIQUE +N becomes STANDARD_ARMOR +2N (+0, +2, +4, +6, +8, +10).
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
                final int convertedStandardArmorUpgradeLevel =
                        standardArmorUpgradeLevelForEquivalentArmorTier(clampedTargetEquivalentArmorTier);
                final int catalogMaximumUpgradeLevel =
                        EquipmentClassifier.maximumUpgradeLevel(equipmentDefinition);
                final int appliedStandardArmorUpgradeLevel =
                        Math.min(convertedStandardArmorUpgradeLevel, catalogMaximumUpgradeLevel);
                yield (int) (baseItemIdUnsigned + appliedStandardArmorUpgradeLevel);
            }
            default -> equipmentDefinition.baseItemId();
        };
    }

    /**
     * Maps STANDARD {@code upgradeLevel} +0…+10 to {@code equivalentArmorTier} +0…+5 (reverse:
     * {@link #standardArmorUpgradeLevelForEquivalentArmorTier}).
     */
    private static int standardArmorUpgradeLevelToEquivalentArmorTier(final int upgradeLevel) {
        final int clampedUpgradeLevel = Math.clamp(upgradeLevel, 0, 10);
        if (clampedUpgradeLevel <= 1) {
            return 0;
        }
        if (clampedUpgradeLevel <= 3) {
            return 1;
        }
        if (clampedUpgradeLevel <= 5) {
            return 2;
        }
        if (clampedUpgradeLevel <= 7) {
            return 3;
        }
        if (clampedUpgradeLevel <= 9) {
            return 4;
        }
        return 5;
    }

    /** STANDARD_ARMOR {@code upgradeLevel} a tier converts to: UNIQUE +N → STANDARD_ARMOR +2N (tier 0…5). */
    private static int standardArmorUpgradeLevelForEquivalentArmorTier(
            final int targetEquivalentArmorTier
    ) {
        final int clampedTargetEquivalentArmorTier = Math.clamp(targetEquivalentArmorTier, 0, MAXIMUM_EQUIVALENT_ARMOR_TIER);
        return switch (clampedTargetEquivalentArmorTier) {
            case 0 -> 0;
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 6;
            case 4 -> 8;
            default -> 10;
        };
    }
}
