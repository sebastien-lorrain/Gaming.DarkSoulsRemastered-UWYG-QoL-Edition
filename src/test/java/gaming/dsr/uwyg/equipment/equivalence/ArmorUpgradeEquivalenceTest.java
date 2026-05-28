package gaming.dsr.uwyg.equipment.equivalence;

import gaming.dsr.uwyg.equipment.types.BaseEquipmentDefinition;
import gaming.dsr.uwyg.equipment.types.enums.EquipmentCategory;
import gaming.dsr.uwyg.equipment.types.enums.ItemUpgradePath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link ArmorUpgradeEquivalence}.
 *
 * <p>The shared comparison scale for armor is the <strong>equivalent armor tier (+0…+5)</strong>.
 * Armor on the {@link ItemUpgradePath#UNIQUE} (twinkling) and {@link ItemUpgradePath#STANDARD_ARMOR}
 * paths reinforce on completely different domains (max upgrade levels of 5 vs 10), so auto-upgrade
 * normalizes both onto this 0…5 scale before picking a target.
 *
 * <h2>Expected per-path mapping (upgradeLevel → equivalentArmorTier)</h2>
 *
 * <pre>
 * NONE (never reinforces)
 *     always equivalentArmorTier 0
 *
 * UNIQUE / Twinkling (max upgradeLevel 5)
 *     +0  → 0      +1  → 1      +2  → 2
 *     +3  → 3      +4  → 4      +5  → 5      (identity)
 *
 * STANDARD_ARMOR (max upgradeLevel 10) — banded:
 *     +0           → 0
 *     +1, +2       → 1
 *     +3, +4       → 2
 *     +5, +6       → 3
 *     +7, +8, +9   → 4
 *     +10          → 5
 * </pre>
 *
 * <h2>Inverse mapping (equivalentArmorTier → STANDARD_ARMOR upgradeLevel)</h2>
 *
 * When picking a STANDARD_ARMOR item id for a target tier, the <em>highest</em> upgrade level inside
 * the band is used so the player keeps every cumulative bonus the band confers:
 *
 * <pre>
 *     tier 0 → +0     tier 1 → +2     tier 2 → +4
 *     tier 3 → +6     tier 4 → +9     tier 5 → +10
 * </pre>
 *
 * <h2>Cross-type upgrade-path consistency</h2>
 *
 * For every <em>ordered pair</em> of armor upgrade paths (sourcePath, targetPath) — across the cross
 * product
 *
 * <pre>
 *     { NONE, UNIQUE/Twinkling, STANDARD_ARMOR } × { NONE, UNIQUE/Twinkling, STANDARD_ARMOR }
 * </pre>
 *
 * <p>given a peer on {@code sourcePath} at every valid {@code upgradeLevel} (yielding ceiling tier T),
 * encoding the target item id for tier T must satisfy:
 *
 * <ol>
 *   <li>chosen target {@code upgradeLevel} projects to a tier ≤ T (never overshoots the peer)</li>
 *   <li>chosen target tier == min(T, target's own max tier) — fills as much of T as path allows</li>
 *   <li>for STANDARD_ARMOR targets, chosen upgrade level is the band <em>maximum</em>
 *       (the +2 / +4 / +6 / +9 / +10 endpoints), never a lower band member</li>
 *   <li>for NONE targets, no upgrade is encoded (catalogId stays at baseItemId)</li>
 * </ol>
 *
 * <p>This guarantees a UNIQUE armor at +N (tier N) and a STANDARD_ARMOR peer arrive at the same
 * equivalent armor tier on the shared scale, and the auto-upgrade target item id matches the
 * band-maximum reinforcement that fits below that ceiling.
 */
class ArmorUpgradeEquivalenceTest {

    private static final int BASE_ITEM_ID_UNIQUE_CATARINA_HELM = 10000;
    private static final int BASE_ITEM_ID_STANDARD_ARMOR_BRIGAND_HOOD = 50000;
    private static final int BASE_ITEM_ID_NONE_SMOUGHS_HELM = 80000;

    private static final BaseEquipmentDefinition UNIQUE_ARMOR_DEFINITION =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_UNIQUE_CATARINA_HELM,
                    1,
                    ItemUpgradePath.UNIQUE,
                    EquipmentCategory.ARMOR,
                    "Catarina Helm");

    private static final BaseEquipmentDefinition STANDARD_ARMOR_DEFINITION =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_STANDARD_ARMOR_BRIGAND_HOOD,
                    1,
                    ItemUpgradePath.STANDARD_ARMOR,
                    EquipmentCategory.ARMOR,
                    "Brigand Hood");

    private static final BaseEquipmentDefinition NONE_ARMOR_DEFINITION =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_NONE_SMOUGHS_HELM,
                    1,
                    ItemUpgradePath.NONE,
                    EquipmentCategory.ARMOR,
                    "Smough's Helm");

    @Test
    @DisplayName("maximumEquivalentArmorTier: UNIQUE and STANDARD_ARMOR both reach 5; NONE stays at 0")
    void maximumEquivalentArmorTier_perUpgradePath() {
        assertEquals(5, ArmorUpgradeEquivalence.maximumEquivalentArmorTier(UNIQUE_ARMOR_DEFINITION));
        assertEquals(5, ArmorUpgradeEquivalence.maximumEquivalentArmorTier(STANDARD_ARMOR_DEFINITION));
        assertEquals(0, ArmorUpgradeEquivalence.maximumEquivalentArmorTier(NONE_ARMOR_DEFINITION));
    }

    @Test
    @DisplayName("maximumEquivalentArmorTier: non-armor upgrade paths fall through to 0")
    void maximumEquivalentArmorTier_nonArmorPaths_areZero() {
        final BaseEquipmentDefinition pseudoInfusableArmor =
                new BaseEquipmentDefinition(
                        200000, 1, ItemUpgradePath.INFUSABLE, EquipmentCategory.ARMOR, "x");
        assertEquals(0, ArmorUpgradeEquivalence.maximumEquivalentArmorTier(pseudoInfusableArmor));

        final BaseEquipmentDefinition pseudoPyroFlameArmor =
                new BaseEquipmentDefinition(
                        300000, 1, ItemUpgradePath.PYRO_FLAME, EquipmentCategory.ARMOR, "x");
        assertEquals(0, ArmorUpgradeEquivalence.maximumEquivalentArmorTier(pseudoPyroFlameArmor));
    }

    @ParameterizedTest(name = "UNIQUE +{0} → equivalentArmorTier {0}")
    @CsvSource({"0,0", "1,1", "2,2", "3,3", "4,4", "5,5"})
    @DisplayName("UNIQUE armor: equivalentArmorTier equals upgradeLevel")
    void equivalentArmorTier_unique_isIdentity(
            final int upgradeLevel,
            final int expectedTier
    ) {
        assertEquals(
                expectedTier,
                ArmorUpgradeEquivalence.equivalentArmorTier(UNIQUE_ARMOR_DEFINITION, upgradeLevel));
    }

    @ParameterizedTest(name = "STANDARD_ARMOR +{0} → equivalentArmorTier {1}")
    @CsvSource({
        "0,0",
        "1,1", "2,1",
        "3,2", "4,2",
        "5,3", "6,3",
        "7,4", "8,4", "9,4",
        "10,5"
    })
    @DisplayName("STANDARD_ARMOR: banded mapping (1-2 → 1, 3-4 → 2, 5-6 → 3, 7-8-9 → 4, 10 → 5)")
    void equivalentArmorTier_standardArmor_followsBandMapping(
            final int upgradeLevel,
            final int expectedTier
    ) {
        assertEquals(
                expectedTier,
                ArmorUpgradeEquivalence.equivalentArmorTier(STANDARD_ARMOR_DEFINITION, upgradeLevel));
    }

    @ParameterizedTest(name = "NONE +{0} → equivalentArmorTier 0")
    @CsvSource({"0", "1", "5", "10", "999"})
    @DisplayName("NONE armor: equivalentArmorTier is always 0 regardless of upgradeLevel")
    void equivalentArmorTier_none_isAlwaysZero(
            final int upgradeLevel
    ) {
        assertEquals(
                0, ArmorUpgradeEquivalence.equivalentArmorTier(NONE_ARMOR_DEFINITION, upgradeLevel));
    }

    @Test
    @DisplayName("UNIQUE and STANDARD_ARMOR clamp out-of-range upgradeLevel inputs to nearest endpoint")
    void equivalentArmorTier_clampsOutOfRange() {
        assertEquals(0, ArmorUpgradeEquivalence.equivalentArmorTier(UNIQUE_ARMOR_DEFINITION, -3));
        assertEquals(5, ArmorUpgradeEquivalence.equivalentArmorTier(UNIQUE_ARMOR_DEFINITION, 42));
        assertEquals(0, ArmorUpgradeEquivalence.equivalentArmorTier(STANDARD_ARMOR_DEFINITION, -2));
        assertEquals(5, ArmorUpgradeEquivalence.equivalentArmorTier(STANDARD_ARMOR_DEFINITION, 99));
    }

    @ParameterizedTest(name = "UNIQUE target tier {0} → catalogId baseItemId + {0}")
    @CsvSource({"0,0", "1,1", "2,2", "3,3", "4,4", "5,5"})
    @DisplayName("catalogItemIdForEquivalentArmorTier UNIQUE: baseItemId + targetTier")
    void catalogItemIdForEquivalentArmorTier_unique_addsTargetTier(
            final int targetTier,
            final int expectedDelta
    ) {
        final int catalogId =
                ArmorUpgradeEquivalence.catalogItemIdForEquivalentArmorTier(
                        UNIQUE_ARMOR_DEFINITION, targetTier);
        assertEquals(BASE_ITEM_ID_UNIQUE_CATARINA_HELM + expectedDelta, catalogId);
    }

    @ParameterizedTest(name = "STANDARD_ARMOR target tier {0} → catalogId baseItemId + {1}")
    @CsvSource({"0,0", "1,2", "2,4", "3,6", "4,9", "5,10"})
    @DisplayName("catalogItemIdForEquivalentArmorTier STANDARD_ARMOR: baseItemId + band-max upgradeLevel")
    void catalogItemIdForEquivalentArmorTier_standardArmor_picksBandMaximum(
            final int targetTier,
            final int expectedBandMaximumUpgradeLevel
    ) {
        final int catalogId =
                ArmorUpgradeEquivalence.catalogItemIdForEquivalentArmorTier(
                        STANDARD_ARMOR_DEFINITION, targetTier);
        assertEquals(
                BASE_ITEM_ID_STANDARD_ARMOR_BRIGAND_HOOD + expectedBandMaximumUpgradeLevel, catalogId);
    }

    @ParameterizedTest(name = "NONE target tier {0} → catalogId stays at baseItemId")
    @CsvSource({"0", "1", "3", "5", "99"})
    @DisplayName("catalogItemIdForEquivalentArmorTier NONE: catalogId always == baseItemId")
    void catalogItemIdForEquivalentArmorTier_none_returnsBase(
            final int targetTier
    ) {
        final int catalogId =
                ArmorUpgradeEquivalence.catalogItemIdForEquivalentArmorTier(
                        NONE_ARMOR_DEFINITION, targetTier);
        assertEquals(BASE_ITEM_ID_NONE_SMOUGHS_HELM, catalogId);
    }

    @Test
    @DisplayName("catalogItemIdForEquivalentArmorTier clamps the targetTier to the definition's own maximum")
    void catalogItemIdForEquivalentArmorTier_clampsTargetTier() {
        final int uniqueCatalogIdHugeTier =
                ArmorUpgradeEquivalence.catalogItemIdForEquivalentArmorTier(UNIQUE_ARMOR_DEFINITION, 9999);
        assertEquals(BASE_ITEM_ID_UNIQUE_CATARINA_HELM + 5, uniqueCatalogIdHugeTier);

        final int standardCatalogIdHugeTier =
                ArmorUpgradeEquivalence.catalogItemIdForEquivalentArmorTier(
                        STANDARD_ARMOR_DEFINITION, 9999);
        assertEquals(BASE_ITEM_ID_STANDARD_ARMOR_BRIGAND_HOOD + 10, standardCatalogIdHugeTier);

        final int uniqueCatalogIdNegativeTier =
                ArmorUpgradeEquivalence.catalogItemIdForEquivalentArmorTier(UNIQUE_ARMOR_DEFINITION, -4);
        assertEquals(BASE_ITEM_ID_UNIQUE_CATARINA_HELM, uniqueCatalogIdNegativeTier);
    }

    /**
     * Cross-type contract test. For every ordered pair (sourceSample, targetDescriptor) across the
     * armor upgrade-path cross-product, encoding the target's catalog item id for the source's
     * equivalent armor tier must:
     *
     * <ul>
     *   <li>not exceed the peer's ceiling tier when re-projected</li>
     *   <li>match min(ceilingTier, target's own maximum tier) exactly (we always lift the target
     *       as high as its path allows)</li>
     *   <li>encode to baseItemId for NONE targets (no offset)</li>
     *   <li>encode to baseItemId + band-max upgrade level for STANDARD_ARMOR targets</li>
     *   <li>encode to baseItemId + chosenTier for UNIQUE targets (identity-tier mapping)</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0}  →  target {1}")
    @MethodSource("crossArmorPathPairs")
    @DisplayName("Cross-type: target catalog id matches band-maximum upgradeLevel that fits peer's equivalentArmorTier")
    void crossUpgradePath_targetAtOrBelowPeerTier(
            final ArmorPathSample sourceSample,
            final ArmorPathTarget targetDescriptor
    ) {
        final int peerCeilingTier = sourceSample.expectedTier();
        final int targetMaximumTier = targetDescriptor.maximumEquivalentArmorTier();
        final int expectedTargetTier = Math.min(peerCeilingTier, targetMaximumTier);

        final int encodedCatalogItemId =
                ArmorUpgradeEquivalence.catalogItemIdForEquivalentArmorTier(
                        targetDescriptor.definition(), peerCeilingTier);
        final int encodedUpgradeLevel = encodedCatalogItemId - targetDescriptor.baseItemId();
        final int projectedTier = targetDescriptor.projectUpgradeLevelToTier(encodedUpgradeLevel);

        assertEquals(
                expectedTargetTier,
                projectedTier,
                () -> "target "
                        + targetDescriptor
                        + " encoded for peer ceiling "
                        + peerCeilingTier
                        + " yielded upgradeLevel +"
                        + encodedUpgradeLevel
                        + " (projected tier "
                        + projectedTier
                        + "), expected min(peerCeiling, targetMax) = "
                        + expectedTargetTier);

        assertEquals(
                targetDescriptor.expectedUpgradeLevelForTier(expectedTargetTier),
                encodedUpgradeLevel,
                () -> "target "
                        + targetDescriptor
                        + " for tier "
                        + expectedTargetTier
                        + " should encode upgradeLevel +"
                        + targetDescriptor.expectedUpgradeLevelForTier(expectedTargetTier)
                        + " (band-max), but got +"
                        + encodedUpgradeLevel);
    }

    static Stream<Arguments> crossArmorPathPairs() {
        final List<ArmorPathSample> sourceSamples = allSourceSamples();
        final List<ArmorPathTarget> targetDescriptors = allTargetDescriptors();
        final List<Arguments> orderedPairs = new ArrayList<>();
        for (final ArmorPathSample source : sourceSamples) {
            for (final ArmorPathTarget target : targetDescriptors) {
                orderedPairs.add(Arguments.of(source, target));
            }
        }
        return orderedPairs.stream();
    }

    private static List<ArmorPathSample> allSourceSamples() {
        final List<ArmorPathSample> samples = new ArrayList<>();
        for (int upgradeLevel = 0; upgradeLevel <= 5; upgradeLevel++) {
            samples.add(new ArmorPathSample("UNIQUE+" + upgradeLevel, upgradeLevel));
        }
        final int[] standardArmorBandMaximums = {0, 1, 1, 2, 2, 3, 3, 4, 4, 4, 5};
        for (int upgradeLevel = 0; upgradeLevel <= 10; upgradeLevel++) {
            samples.add(
                    new ArmorPathSample(
                            "STANDARD_ARMOR+" + upgradeLevel, standardArmorBandMaximums[upgradeLevel]));
        }
        samples.add(new ArmorPathSample("NONE (no reinforcement)", 0));
        return samples;
    }

    private static List<ArmorPathTarget> allTargetDescriptors() {
        return List.of(
                new ArmorPathTarget(
                        "NONE",
                        NONE_ARMOR_DEFINITION,
                        BASE_ITEM_ID_NONE_SMOUGHS_HELM,
                        0) {
                    @Override
                    int projectUpgradeLevelToTier(final int upgradeLevel) {
                        return 0;
                    }

                    @Override
                    int expectedUpgradeLevelForTier(final int targetTier) {
                        return 0;
                    }
                },
                new ArmorPathTarget(
                        "UNIQUE",
                        UNIQUE_ARMOR_DEFINITION,
                        BASE_ITEM_ID_UNIQUE_CATARINA_HELM,
                        5) {
                    @Override
                    int projectUpgradeLevelToTier(final int upgradeLevel) {
                        return ArmorUpgradeEquivalence.equivalentArmorTier(
                                UNIQUE_ARMOR_DEFINITION, upgradeLevel);
                    }

                    @Override
                    int expectedUpgradeLevelForTier(final int targetTier) {
                        return targetTier;
                    }
                },
                new ArmorPathTarget(
                        "STANDARD_ARMOR",
                        STANDARD_ARMOR_DEFINITION,
                        BASE_ITEM_ID_STANDARD_ARMOR_BRIGAND_HOOD,
                        5) {
                    private final int[] bandMaximumUpgradeLevels = {0, 2, 4, 6, 9, 10};

                    @Override
                    int projectUpgradeLevelToTier(final int upgradeLevel) {
                        return ArmorUpgradeEquivalence.equivalentArmorTier(
                                STANDARD_ARMOR_DEFINITION, upgradeLevel);
                    }

                    @Override
                    int expectedUpgradeLevelForTier(final int targetTier) {
                        return bandMaximumUpgradeLevels[targetTier];
                    }
                });
    }

    /**
     * Source/peer sample: a known armor upgrade-state described by its label and the equivalent
     * armor tier that the equivalence rules must assign to it.
     */
    private record ArmorPathSample(String label, int expectedTier) {
        @Override
        public String toString() {
            return label + " (tier " + expectedTier + ")";
        }
    }

    /**
     * Target descriptor: a candidate armor upgrade path that should be raised "as high as possible
     * without exceeding the peer's equivalent armor tier".
     */
    private abstract static class ArmorPathTarget {
        private final String label;
        private final BaseEquipmentDefinition definition;
        private final int baseItemId;
        private final int maximumEquivalentArmorTier;

        ArmorPathTarget(
                final String label,
                final BaseEquipmentDefinition definition,
                final int baseItemId,
                final int maximumEquivalentArmorTier
        ) {
            this.label = label;
            this.definition = definition;
            this.baseItemId = baseItemId;
            this.maximumEquivalentArmorTier = maximumEquivalentArmorTier;
        }

        abstract int projectUpgradeLevelToTier(int upgradeLevel);

        abstract int expectedUpgradeLevelForTier(int targetTier);

        BaseEquipmentDefinition definition() {
            return definition;
        }

        int baseItemId() {
            return baseItemId;
        }

        int maximumEquivalentArmorTier() {
            return maximumEquivalentArmorTier;
        }

        @Override
        public String toString() {
            return label + " (max tier " + maximumEquivalentArmorTier + ")";
        }
    }
}
