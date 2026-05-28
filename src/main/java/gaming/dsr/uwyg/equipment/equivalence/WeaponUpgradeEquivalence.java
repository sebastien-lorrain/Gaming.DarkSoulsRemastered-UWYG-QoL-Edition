package gaming.dsr.uwyg.equipment.equivalence;

import gaming.dsr.uwyg.equipment.WeaponUpgradeDecode;
import gaming.dsr.uwyg.equipment.types.BaseEquipmentDefinition;
import gaming.dsr.uwyg.equipment.types.enums.EquipmentCategory;
import gaming.dsr.uwyg.equipment.types.enums.ItemUpgradePath;
import gaming.dsr.uwyg.equipment.types.enums.WeaponInfusionPath;

/**
 * Maps weapon upgrade level (+n on the current path or infusion) to a comparable
 * <strong>equivalent NORMAL tier (+0…+15)</strong> scale.
 * Used for weapons (melee, ranged non-ammo, shields). Armor uses {@link ArmorUpgradeEquivalence}.
 *
 * <p><strong>REGULAR / NORMAL infusion</strong>: {@code upgradeLevel} equals {@code equivalentNormalTier}
 * (0…15), capped by the item.
 *
 * <p><strong>Twinkling ({@link ItemUpgradePath#UNIQUE})</strong> — {@code upgradeLevel} 0…5:
 *
 * <pre>
 * UNIQUE +0 → equivalentNormalTier 0
 * UNIQUE +1 → equivalentNormalTier 3
 * UNIQUE +2 → equivalentNormalTier 6
 * UNIQUE +3 → equivalentNormalTier 9
 * UNIQUE +4 → equivalentNormalTier 12
 * UNIQUE +5 → equivalentNormalTier 15
 * </pre>
 *
 * <p><strong>Short-rare infusions</strong> (Lightning, Crystal, Occult, Enchanted, Chaos, Raw), max {@code upgradeLevel} 5:
 *
 * <pre>
 * +0 → equivalentNormalTier 10
 * …
 * +5 → equivalentNormalTier 15
 * </pre>
 *
 * <p><strong>Divine, Magic, Fire</strong> — max {@code upgradeLevel} 10:
 *
 * <pre>
 * +0 → equivalentNormalTier 5
 * …
 * +10 → equivalentNormalTier 15
 * </pre>
 */
@SuppressWarnings("ALL")
public final class WeaponUpgradeEquivalence {

    private static final int MAXIMUM_EQUIVALENT_NORMAL_TIER = 15;

    /** Divine / Magic / Fire +0 maps to equivalent NORMAL tier +5. */
    private static final int DIVINE_MAGIC_FIRE_BASE_EQUIVALENT_NORMAL_TIER = 5;

    /** Short-rare infusions +0 maps to equivalent NORMAL tier +10. */
    private static final int SHORT_RARE_INFUSION_BASE_EQUIVALENT_NORMAL_TIER = 10;

    /** UNIQUE {@code upgradeLevel} (0…5) → equivalent NORMAL tier index on the comparison scale. */
    private static final int[] TWINKLING_UPGRADE_LEVEL_TO_EQUIVALENT_NORMAL_TIER = {
        0, 3, 6, 9, 12, 15
    };

    private WeaponUpgradeEquivalence() {}

    /**
     * Spell tools whose catalog upgrade path actually reinforces (twinkling, infusion, pyromancy flame, …)
     * participate in the same peer pool as melee weapons, shields, and bows.
     */
    public static boolean spellToolParticipatesInWeaponPeerGroup(final BaseEquipmentDefinition equipmentCatalogDefinition) {
        if (equipmentCatalogDefinition.category() != EquipmentCategory.SPELL_TOOL) {
            return false;
        }
        return switch (equipmentCatalogDefinition.upgradePath()) {
            case UNIQUE, INFUSABLE, INFUSABLE_RESTRICTED, PYRO_FLAME, PYRO_ASCENDED -> true;
            case NONE, STANDARD_ARMOR -> false;
        };
    }

    public static boolean shouldApplyWeaponEquivalenceRules(final BaseEquipmentDefinition equipmentCatalogDefinition) {
        return switch (equipmentCatalogDefinition.category()) {
            case MELEE_WEAPON, SHIELD -> true;
            case RANGED_WEAPON -> !isArrowOrBoltStack(equipmentCatalogDefinition);
            case SPELL_TOOL -> spellToolParticipatesInWeaponPeerGroup(equipmentCatalogDefinition);
            case ARMOR, SPELL, RING -> false;
        };
    }

    private static boolean isArrowOrBoltStack(final BaseEquipmentDefinition equipmentCatalogDefinition) {
        if (equipmentCatalogDefinition.category() != EquipmentCategory.RANGED_WEAPON) {
            return false;
        }
        final long baseItemIdUnsigned = Integer.toUnsignedLong(equipmentCatalogDefinition.baseItemId());
        return baseItemIdUnsigned >= 2000000L && baseItemIdUnsigned < 2200000L;
    }

    /** Equivalent NORMAL tier (0–15) on the shared comparison scale. */
    public static int equivalentNormalTier(
            final BaseEquipmentDefinition equipmentCatalogDefinition,
            final WeaponUpgradeDecode weaponUpgradeDecode
    ) {
        return switch (equipmentCatalogDefinition.upgradePath()) {
            case UNIQUE -> {
                if (weaponUpgradeDecode instanceof WeaponUpgradeDecode.Twinkling(final int twinklingUpgradeLevel)) {
                    final int clampedTwinklingUpgradeLevel = Math.clamp(twinklingUpgradeLevel, 0, 5);
                    yield TWINKLING_UPGRADE_LEVEL_TO_EQUIVALENT_NORMAL_TIER[clampedTwinklingUpgradeLevel];
                }
                yield 0;
            }
            case INFUSABLE, INFUSABLE_RESTRICTED -> {
                if (weaponUpgradeDecode
                        instanceof WeaponUpgradeDecode.Infused(
                                final WeaponInfusionPath infusionPath, final int upgradeLevel)) {
                    yield infusionUpgradeLevelToEquivalentNormalTier(infusionPath, upgradeLevel);
                }
                yield 0;
            }
            case NONE, STANDARD_ARMOR -> 0;
            case PYRO_FLAME -> {
                if (weaponUpgradeDecode instanceof WeaponUpgradeDecode.PyromancyFlame(final int upgradeSteps)) {
                    yield Math.clamp(upgradeSteps, 0, MAXIMUM_EQUIVALENT_NORMAL_TIER);
                }
                yield 0;
            }
            case PYRO_ASCENDED -> {
                if (weaponUpgradeDecode instanceof WeaponUpgradeDecode.PyromancyFlame(final int upgradeSteps)) {
                    final int clampedAscendedSteps = Math.clamp(upgradeSteps, 0, 5);
                    yield TWINKLING_UPGRADE_LEVEL_TO_EQUIVALENT_NORMAL_TIER[clampedAscendedSteps];
                }
                yield 0;
            }
        };
    }

    /**
     * Highest UNIQUE {@code upgradeLevel} (0…5) whose {@link #equivalentNormalTier} does not exceed
     * {@code ceilingEquivalentNormalTier}.
     */
    public static int twinklingUpgradeLevelAtOrBelowEquivalentNormalTier(final int ceilingEquivalentNormalTier) {

        final int ceilingEquivalentNormalTierClamped = Math.clamp(ceilingEquivalentNormalTier, 0, MAXIMUM_EQUIVALENT_NORMAL_TIER);

        int chosenTwinklingUpgradeLevel = 0;
        for (int candidateTwinklingUpgradeLevel = 0; candidateTwinklingUpgradeLevel <= 5; candidateTwinklingUpgradeLevel++) {
            if (TWINKLING_UPGRADE_LEVEL_TO_EQUIVALENT_NORMAL_TIER[candidateTwinklingUpgradeLevel] <= ceilingEquivalentNormalTierClamped) {
                chosenTwinklingUpgradeLevel = candidateTwinklingUpgradeLevel;
            }
        }
        return chosenTwinklingUpgradeLevel;
    }

    private static int infusionUpgradeLevelToEquivalentNormalTier(
            final WeaponInfusionPath infusionPath,
            final int upgradeLevel
    ) {
        if (infusionPath == WeaponInfusionPath.NORMAL) {
            return Math.clamp(upgradeLevel, 0, MAXIMUM_EQUIVALENT_NORMAL_TIER);
        }
        if (isDivineMagicFireInfusion(infusionPath)) {
            return divineMagicFireUpgradeLevelToEquivalentNormalTier(upgradeLevel);
        }
        if (isShortRareInfusion(infusionPath)) {
            return shortRareInfusionUpgradeLevelToEquivalentNormalTier(upgradeLevel);
        }
        return Math.clamp(upgradeLevel, 0, MAXIMUM_EQUIVALENT_NORMAL_TIER);
    }

    public static int maximumInfusionUpgradeLevelAtOrBelowEquivalentNormalTier(
            final WeaponInfusionPath infusionPath,
            final int infusionMaximumUpgradeLevel,
            final int ceilingEquivalentNormalTier
    ) {
        final int ceilingEquivalentNormalTierClamped = Math.clamp(ceilingEquivalentNormalTier, 0, MAXIMUM_EQUIVALENT_NORMAL_TIER);
        final int infusionMaximumUpgradeLevelSanitized = Math.max(0, infusionMaximumUpgradeLevel);

        int maximumQualifyingUpgradeLevel = 0;
        for (int candidateUpgradeLevel = 0; candidateUpgradeLevel <= infusionMaximumUpgradeLevelSanitized; candidateUpgradeLevel++) {

            if (infusionUpgradeLevelToEquivalentNormalTier(infusionPath, candidateUpgradeLevel) <= ceilingEquivalentNormalTierClamped) {
                maximumQualifyingUpgradeLevel = candidateUpgradeLevel;
            }
        }
        return maximumQualifyingUpgradeLevel;
    }

    private static int divineMagicFireUpgradeLevelToEquivalentNormalTier(final int upgradeLevel) {
        final int clampedUpgradeLevel = Math.clamp(upgradeLevel, 0, 10);
        final int mappedEquivalentNormalTier =
                DIVINE_MAGIC_FIRE_BASE_EQUIVALENT_NORMAL_TIER + clampedUpgradeLevel;
        return Math.clamp(mappedEquivalentNormalTier, 0, MAXIMUM_EQUIVALENT_NORMAL_TIER);
    }

    private static int shortRareInfusionUpgradeLevelToEquivalentNormalTier(final int upgradeLevel) {
        final int clampedUpgradeLevel = Math.clamp(upgradeLevel, 0, 5);
        final int mappedEquivalentNormalTier =
                SHORT_RARE_INFUSION_BASE_EQUIVALENT_NORMAL_TIER + clampedUpgradeLevel;
        return Math.clamp(mappedEquivalentNormalTier, 0, MAXIMUM_EQUIVALENT_NORMAL_TIER);
    }

    private static boolean isShortRareInfusion(final WeaponInfusionPath infusionPath) {
        return switch (infusionPath) {
            case LIGHTNING, CRYSTAL, OCCULT, ENCHANTED, CHAOS, RAW -> true;
            default -> false;
        };
    }

    private static boolean isDivineMagicFireInfusion(final WeaponInfusionPath infusionPath) {
        return infusionPath == WeaponInfusionPath.DIVINE
                || infusionPath == WeaponInfusionPath.MAGIC
                || infusionPath == WeaponInfusionPath.FIRE;
    }
}
