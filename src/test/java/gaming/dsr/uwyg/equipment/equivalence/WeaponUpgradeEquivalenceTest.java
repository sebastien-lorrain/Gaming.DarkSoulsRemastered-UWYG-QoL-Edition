package gaming.dsr.uwyg.equipment.equivalence;

import gaming.dsr.uwyg.equipment.WeaponUpgradeDecode;
import gaming.dsr.uwyg.equipment.types.BaseEquipmentDefinition;
import gaming.dsr.uwyg.equipment.types.enums.EquipmentCategory;
import gaming.dsr.uwyg.equipment.types.enums.ItemUpgradePath;
import gaming.dsr.uwyg.equipment.types.enums.WeaponInfusionPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link WeaponUpgradeEquivalence}.
 *
 * <p>The shared comparison scale for weapons is the <strong>equivalent NORMAL tier (+0…+15)</strong>.
 * Every weapon upgrade path projects its own {@code upgradeLevel} onto this scale; this is what lets
 * auto-upgrade pick "the same effective +" across different weapon families, infusions, and pyromancy
 * flames.
 *
 * <h2>Expected per-path mapping (upgradeLevel → equivalentNormalTier)</h2>
 *
 * <pre>
 * NORMAL infusion (INFUSABLE / INFUSABLE_RESTRICTED, max upgradeLevel 15)
 *     +0  → 0      +1  → 1      …       +15 → 15        (identity)
 *
 * UNIQUE / Twinkling (max upgradeLevel 5)
 *     +0  → 0      +1  → 3      +2  → 6      +3  → 9
 *     +4  → 12     +5  → 15
 *
 * DIVINE / MAGIC / FIRE infusion (max upgradeLevel 10)
 *     +0  → 5      +1  → 6      …       +10 → 15
 *
 * Short-rare infusions: LIGHTNING, CRYSTAL, OCCULT, ENCHANTED, CHAOS, RAW (max upgradeLevel 5)
 *     +0  → 10     +1  → 11     …       +5  → 15
 *
 * PYRO_FLAME (max upgradeSteps 15)
 *     +0  → 0      +1  → 1      …       +15 → 15        (identity)
 *
 * PYRO_ASCENDED (max upgradeSteps 5; same mapping as UNIQUE/Twinkling)
 *     +0  → 0      +1  → 3      +2  → 6      +3  → 9
 *     +4  → 12     +5  → 15
 *
 * NONE / STANDARD_ARMOR upgrade paths
 *     always 0    (these paths do not participate in the NORMAL-tier scale)
 * </pre>
 *
 * <h2>Cross-type upgrade-path consistency</h2>
 *
 * For every <em>ordered pair</em> of weapon upgrade paths (sourcePath, targetPath) — across the cross
 * product
 *
 * <pre>
 *     { NORMAL infusion, UNIQUE/Twinkling, DIVINE, MAGIC, FIRE,
 *       LIGHTNING, CRYSTAL, OCCULT, ENCHANTED, CHAOS, RAW,
 *       PYRO_FLAME, PYRO_ASCENDED }
 *         ×
 *     { same set }
 * </pre>
 *
 * <p>given a peer on {@code sourcePath} at every valid {@code upgradeLevel} (yielding ceiling tier T),
 * the helper that picks "highest target {@code upgradeLevel} whose mapped tier ≤ T" must satisfy:
 *
 * <ol>
 *   <li>if any target {@code upgradeLevel} projects to a tier ≤ T, the chosen level is the
 *       <em>highest</em> such level (auto-upgrade lifts the item as high as the peer allows)</li>
 *   <li>if no target {@code upgradeLevel} qualifies — typical when the target's minimum
 *       equivalent tier exceeds T (e.g., DIVINE +0 maps to tier 5, ceiling T = 1) — the chosen
 *       level is {@code 0} as a documented best-effort fallback; the higher-layer auto-upgrade
 *       (see {@code EquipmentAutoUpgrader}) then declines to apply the change because the loot's
 *       own current tier already exceeds T</li>
 *   <li>chosen targetLevel is always in {@code [0, targetPath max]}</li>
 * </ol>
 *
 * <p>This guarantees the auto-upgrade contract: a freshly looted item on path X is raised to the
 * <em>highest</em> reinforcement on X that does not exceed the player's strongest peer on path Y,
 * or left untouched when the path cannot fit below the peer at all.
 */
class WeaponUpgradeEquivalenceTest {

    private static final int BASE_ITEM_ID_INFUSABLE_SHORTSWORD = 200000;
    private static final int BASE_ITEM_ID_INFUSABLE_RESTRICTED_HEATER_SHIELD = 1450000;
    private static final int BASE_ITEM_ID_UNIQUE_GHOST_BLADE = 102000;
    private static final int BASE_ITEM_ID_PYRO_FLAME = 1330000;
    private static final int BASE_ITEM_ID_PYRO_ASCENDED = 1332000;
    private static final int BASE_ITEM_ID_TALISMAN_NO_REINFORCE = 1360000;
    private static final int BASE_ITEM_ID_ARROW_STACK_STANDARD = 2000000;
    private static final int BASE_ITEM_ID_BOLT_STACK_STANDARD = 2100000;
    private static final int BASE_ITEM_ID_BOW_SHORT = 1200000;

    private static final BaseEquipmentDefinition INFUSABLE_MELEE_WEAPON =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_INFUSABLE_SHORTSWORD,
                    1,
                    ItemUpgradePath.INFUSABLE,
                    EquipmentCategory.MELEE_WEAPON,
                    "Shortsword");

    private static final BaseEquipmentDefinition INFUSABLE_RESTRICTED_SHIELD =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_INFUSABLE_RESTRICTED_HEATER_SHIELD,
                    1,
                    ItemUpgradePath.INFUSABLE_RESTRICTED,
                    EquipmentCategory.SHIELD,
                    "Heater Shield");

    private static final BaseEquipmentDefinition UNIQUE_MELEE_WEAPON =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_UNIQUE_GHOST_BLADE,
                    1,
                    ItemUpgradePath.UNIQUE,
                    EquipmentCategory.MELEE_WEAPON,
                    "Ghost Blade");

    private static final BaseEquipmentDefinition PYRO_FLAME_TOOL =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_PYRO_FLAME,
                    1,
                    ItemUpgradePath.PYRO_FLAME,
                    EquipmentCategory.SPELL_TOOL,
                    "Pyromancy Flame");

    private static final BaseEquipmentDefinition PYRO_ASCENDED_TOOL =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_PYRO_ASCENDED,
                    1,
                    ItemUpgradePath.PYRO_ASCENDED,
                    EquipmentCategory.SPELL_TOOL,
                    "Pyromancy Flame (Ascended)");

    private static final BaseEquipmentDefinition NON_REINFORCING_SPELL_TOOL =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_TALISMAN_NO_REINFORCE,
                    1,
                    ItemUpgradePath.NONE,
                    EquipmentCategory.SPELL_TOOL,
                    "Talisman");

    private static final BaseEquipmentDefinition ARROW_STACK_AMMO =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_ARROW_STACK_STANDARD,
                    999,
                    ItemUpgradePath.NONE,
                    EquipmentCategory.RANGED_WEAPON,
                    "Standard Arrow");

    private static final BaseEquipmentDefinition BOLT_STACK_AMMO =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_BOLT_STACK_STANDARD,
                    999,
                    ItemUpgradePath.NONE,
                    EquipmentCategory.RANGED_WEAPON,
                    "Standard Bolt");

    private static final BaseEquipmentDefinition SHORT_BOW_RANGED_WEAPON =
            new BaseEquipmentDefinition(
                    BASE_ITEM_ID_BOW_SHORT,
                    1,
                    ItemUpgradePath.INFUSABLE,
                    EquipmentCategory.RANGED_WEAPON,
                    "Short Bow");

    @ParameterizedTest(name = "NORMAL +{0} → equivalentNormalTier {0}")
    @CsvSource({
        "0,0", "1,1", "2,2", "3,3", "4,4", "5,5", "6,6", "7,7",
        "8,8", "9,9", "10,10", "11,11", "12,12", "13,13", "14,14", "15,15"
    })
    @DisplayName("NORMAL infusion: upgradeLevel maps identity to equivalentNormalTier 0…15")
    void equivalentNormalTier_normalInfusion_isIdentity(
            final int upgradeLevel,
            final int expectedTier
    ) {
        final WeaponUpgradeDecode decoded =
                new WeaponUpgradeDecode.Infused(WeaponInfusionPath.NORMAL, upgradeLevel);
        assertEquals(
                expectedTier, WeaponUpgradeEquivalence.equivalentNormalTier(INFUSABLE_MELEE_WEAPON, decoded));
    }

    @ParameterizedTest(name = "UNIQUE +{0} → equivalentNormalTier {1}")
    @CsvSource({"0,0", "1,3", "2,6", "3,9", "4,12", "5,15"})
    @DisplayName("UNIQUE/Twinkling: 0,3,6,9,12,15 stepped mapping")
    void equivalentNormalTier_unique_followsTwinklingMapping(
            final int twinklingUpgradeLevel,
            final int expectedTier
    ) {
        final WeaponUpgradeDecode decoded = new WeaponUpgradeDecode.Twinkling(twinklingUpgradeLevel);
        assertEquals(
                expectedTier, WeaponUpgradeEquivalence.equivalentNormalTier(UNIQUE_MELEE_WEAPON, decoded));
    }

    @ParameterizedTest(name = "{0} +{1} → equivalentNormalTier {2}")
    @CsvSource({
        "DIVINE,0,5", "DIVINE,1,6", "DIVINE,5,10", "DIVINE,10,15",
        "MAGIC,0,5", "MAGIC,3,8", "MAGIC,10,15",
        "FIRE,0,5", "FIRE,7,12", "FIRE,10,15"
    })
    @DisplayName("DIVINE / MAGIC / FIRE infusion: upgradeLevel + 5 = equivalentNormalTier (0..10 → 5..15)")
    void equivalentNormalTier_divineMagicFire_addsBaseFive(
            final WeaponInfusionPath infusionPath,
            final int upgradeLevel,
            final int expectedTier
    ) {
        final WeaponUpgradeDecode decoded = new WeaponUpgradeDecode.Infused(infusionPath, upgradeLevel);
        assertEquals(
                expectedTier, WeaponUpgradeEquivalence.equivalentNormalTier(INFUSABLE_MELEE_WEAPON, decoded));
    }

    @ParameterizedTest(name = "{0} +{1} → equivalentNormalTier {2}")
    @CsvSource({
        "LIGHTNING,0,10", "LIGHTNING,1,11", "LIGHTNING,5,15",
        "CRYSTAL,0,10", "CRYSTAL,3,13", "CRYSTAL,5,15",
        "OCCULT,0,10", "OCCULT,5,15",
        "ENCHANTED,0,10", "ENCHANTED,5,15",
        "CHAOS,0,10", "CHAOS,2,12", "CHAOS,5,15",
        "RAW,0,10", "RAW,4,14", "RAW,5,15"
    })
    @DisplayName("Short-rare infusions: upgradeLevel + 10 = equivalentNormalTier (0..5 → 10..15)")
    void equivalentNormalTier_shortRareInfusion_addsBaseTen(
            final WeaponInfusionPath infusionPath,
            final int upgradeLevel,
            final int expectedTier
    ) {
        final WeaponUpgradeDecode decoded = new WeaponUpgradeDecode.Infused(infusionPath, upgradeLevel);
        assertEquals(
                expectedTier, WeaponUpgradeEquivalence.equivalentNormalTier(INFUSABLE_MELEE_WEAPON, decoded));
    }

    @ParameterizedTest(name = "PYRO_FLAME +{0} → equivalentNormalTier {0}")
    @CsvSource({
        "0,0", "1,1", "5,5", "10,10", "15,15"
    })
    @DisplayName("PYRO_FLAME: upgradeSteps maps identity to equivalentNormalTier 0…15")
    void equivalentNormalTier_pyromancyFlame_isIdentity(
            final int upgradeSteps,
            final int expectedTier
    ) {
        final WeaponUpgradeDecode decoded = new WeaponUpgradeDecode.PyromancyFlame(upgradeSteps);
        assertEquals(
                expectedTier, WeaponUpgradeEquivalence.equivalentNormalTier(PYRO_FLAME_TOOL, decoded));
    }

    @ParameterizedTest(name = "PYRO_ASCENDED +{0} → equivalentNormalTier {1}")
    @CsvSource({"0,0", "1,3", "2,6", "3,9", "4,12", "5,15"})
    @DisplayName("PYRO_ASCENDED: 0,3,6,9,12,15 stepped mapping (same as UNIQUE/Twinkling)")
    void equivalentNormalTier_pyromancyAscended_followsTwinklingMapping(
            final int upgradeSteps,
            final int expectedTier
    ) {
        final WeaponUpgradeDecode decoded = new WeaponUpgradeDecode.PyromancyFlame(upgradeSteps);
        assertEquals(
                expectedTier, WeaponUpgradeEquivalence.equivalentNormalTier(PYRO_ASCENDED_TOOL, decoded));
    }

    @Test
    @DisplayName("UNIQUE: out-of-range twinkling levels clamp to nearest endpoint of [0,5]")
    void equivalentNormalTier_unique_clampsOutOfRange() {
        assertEquals(0, WeaponUpgradeEquivalence.equivalentNormalTier(
                UNIQUE_MELEE_WEAPON, new WeaponUpgradeDecode.Twinkling(-7)));
        assertEquals(15, WeaponUpgradeEquivalence.equivalentNormalTier(
                UNIQUE_MELEE_WEAPON, new WeaponUpgradeDecode.Twinkling(42)));
    }

    @Test
    @DisplayName("PYRO_FLAME / PYRO_ASCENDED: out-of-range upgradeSteps clamp at endpoints")
    void equivalentNormalTier_pyromancy_clampsOutOfRange() {
        assertEquals(0, WeaponUpgradeEquivalence.equivalentNormalTier(
                PYRO_FLAME_TOOL, new WeaponUpgradeDecode.PyromancyFlame(-3)));
        assertEquals(15, WeaponUpgradeEquivalence.equivalentNormalTier(
                PYRO_FLAME_TOOL, new WeaponUpgradeDecode.PyromancyFlame(99)));
        assertEquals(0, WeaponUpgradeEquivalence.equivalentNormalTier(
                PYRO_ASCENDED_TOOL, new WeaponUpgradeDecode.PyromancyFlame(-1)));
        assertEquals(15, WeaponUpgradeEquivalence.equivalentNormalTier(
                PYRO_ASCENDED_TOOL, new WeaponUpgradeDecode.PyromancyFlame(99)));
    }

    @Test
    @DisplayName("NONE / STANDARD_ARMOR upgrade paths always project to equivalentNormalTier 0")
    void equivalentNormalTier_nonWeaponPaths_areZero() {
        final BaseEquipmentDefinition noUpgradeWeapon =
                new BaseEquipmentDefinition(
                        904000, 1, ItemUpgradePath.NONE, EquipmentCategory.MELEE_WEAPON, "Dark Hand");
        assertEquals(0, WeaponUpgradeEquivalence.equivalentNormalTier(
                noUpgradeWeapon, new WeaponUpgradeDecode.Twinkling(5)));

        final BaseEquipmentDefinition pseudoStandardArmorWeapon =
                new BaseEquipmentDefinition(
                        50000, 1, ItemUpgradePath.STANDARD_ARMOR, EquipmentCategory.MELEE_WEAPON, "x");
        assertEquals(0, WeaponUpgradeEquivalence.equivalentNormalTier(
                pseudoStandardArmorWeapon, new WeaponUpgradeDecode.Twinkling(5)));
    }

    @ParameterizedTest(name = "ceiling tier {0} → twinkling level {1}")
    @CsvSource({
        "0,0", "1,0", "2,0",
        "3,1", "4,1", "5,1",
        "6,2", "7,2", "8,2",
        "9,3", "10,3", "11,3",
        "12,4", "13,4", "14,4",
        "15,5"
    })
    @DisplayName("twinklingUpgradeLevelAtOrBelowEquivalentNormalTier picks the highest level that fits the ceiling")
    void twinklingUpgradeLevelAtOrBelow_returnsHighestQualifyingLevel(
            final int ceilingTier,
            final int expectedTwinklingLevel
    ) {
        assertEquals(
                expectedTwinklingLevel,
                WeaponUpgradeEquivalence.twinklingUpgradeLevelAtOrBelowEquivalentNormalTier(ceilingTier));
    }

    @Test
    @DisplayName("twinklingUpgradeLevelAtOrBelowEquivalentNormalTier clamps negative or >15 ceilings")
    void twinklingUpgradeLevelAtOrBelow_clampsCeiling() {
        assertEquals(
                0,
                WeaponUpgradeEquivalence.twinklingUpgradeLevelAtOrBelowEquivalentNormalTier(-100));
        assertEquals(
                5,
                WeaponUpgradeEquivalence.twinklingUpgradeLevelAtOrBelowEquivalentNormalTier(999));
    }

    @ParameterizedTest(name = "{0} max +{1}, ceiling tier {2} → chosen +{3}")
    @MethodSource("infusionAtOrBelowCases")
    @DisplayName("maximumInfusionUpgradeLevelAtOrBelowEquivalentNormalTier per infusion path picks highest qualifying level")
    void maximumInfusionUpgradeLevelAtOrBelow_perInfusion(
            final WeaponInfusionPath infusionPath,
            final int infusionMax,
            final int ceilingTier,
            final int expectedUpgradeLevel
    ) {
        assertEquals(
                expectedUpgradeLevel,
                WeaponUpgradeEquivalence.maximumInfusionUpgradeLevelAtOrBelowEquivalentNormalTier(
                        infusionPath, infusionMax, ceilingTier));
    }

    static Stream<Arguments> infusionAtOrBelowCases() {
        return Stream.of(
                Arguments.of(WeaponInfusionPath.NORMAL, 15, 0, 0),
                Arguments.of(WeaponInfusionPath.NORMAL, 15, 7, 7),
                Arguments.of(WeaponInfusionPath.NORMAL, 15, 15, 15),
                Arguments.of(WeaponInfusionPath.NORMAL, 15, 99, 15),
                Arguments.of(WeaponInfusionPath.FIRE, 10, 4, 0),
                Arguments.of(WeaponInfusionPath.FIRE, 10, 5, 0),
                Arguments.of(WeaponInfusionPath.FIRE, 10, 6, 1),
                Arguments.of(WeaponInfusionPath.FIRE, 10, 15, 10),
                Arguments.of(WeaponInfusionPath.MAGIC, 10, 5, 0),
                Arguments.of(WeaponInfusionPath.MAGIC, 10, 11, 6),
                Arguments.of(WeaponInfusionPath.DIVINE, 10, 9, 4),
                Arguments.of(WeaponInfusionPath.LIGHTNING, 5, 9, 0),
                Arguments.of(WeaponInfusionPath.LIGHTNING, 5, 10, 0),
                Arguments.of(WeaponInfusionPath.LIGHTNING, 5, 11, 1),
                Arguments.of(WeaponInfusionPath.LIGHTNING, 5, 15, 5),
                Arguments.of(WeaponInfusionPath.CHAOS, 5, 14, 4),
                Arguments.of(WeaponInfusionPath.RAW, 5, 12, 2),
                Arguments.of(WeaponInfusionPath.ENCHANTED, 5, 13, 3));
    }

    @Test
    @DisplayName("spellToolParticipatesInWeaponPeerGroup: only reinforcement-eligible spell tools participate")
    void spellToolParticipates_includesReinforcementPaths() {
        assertTrue(WeaponUpgradeEquivalence.spellToolParticipatesInWeaponPeerGroup(PYRO_FLAME_TOOL));
        assertTrue(WeaponUpgradeEquivalence.spellToolParticipatesInWeaponPeerGroup(PYRO_ASCENDED_TOOL));
        assertFalse(WeaponUpgradeEquivalence.spellToolParticipatesInWeaponPeerGroup(NON_REINFORCING_SPELL_TOOL));

        final BaseEquipmentDefinition meleeUnique =
                new BaseEquipmentDefinition(
                        102000, 1, ItemUpgradePath.UNIQUE, EquipmentCategory.MELEE_WEAPON, "x");
        assertFalse(WeaponUpgradeEquivalence.spellToolParticipatesInWeaponPeerGroup(meleeUnique));
    }

    @ParameterizedTest
    @EnumSource(EquipmentCategory.class)
    @DisplayName("shouldApplyWeaponEquivalenceRules: melee/shield always true; ammo false; armor/spell/ring false")
    void shouldApplyWeaponEquivalenceRules_byCategory(
            final EquipmentCategory category
    ) {
        final BaseEquipmentDefinition representative =
                switch (category) {
                    case MELEE_WEAPON -> INFUSABLE_MELEE_WEAPON;
                    case SHIELD -> INFUSABLE_RESTRICTED_SHIELD;
                    case RANGED_WEAPON -> SHORT_BOW_RANGED_WEAPON;
                    case SPELL_TOOL -> PYRO_FLAME_TOOL;
                    case ARMOR ->
                            new BaseEquipmentDefinition(
                                    10000, 1, ItemUpgradePath.UNIQUE, EquipmentCategory.ARMOR, "x");
                    case SPELL ->
                            new BaseEquipmentDefinition(
                                    3000, 1, ItemUpgradePath.NONE, EquipmentCategory.SPELL, "x");
                    case RING ->
                            new BaseEquipmentDefinition(
                                    100, 1, ItemUpgradePath.NONE, EquipmentCategory.RING, "x");
                };

        final boolean shouldParticipate =
                WeaponUpgradeEquivalence.shouldApplyWeaponEquivalenceRules(representative);
        switch (category) {
            case MELEE_WEAPON, SHIELD, RANGED_WEAPON -> assertTrue(shouldParticipate);
            case SPELL_TOOL -> assertTrue(shouldParticipate);
            case ARMOR, SPELL, RING -> assertFalse(shouldParticipate);
        }
    }

    @Test
    @DisplayName("shouldApplyWeaponEquivalenceRules: arrow / bolt ammo stacks are excluded from weapon peer group")
    void shouldApplyWeaponEquivalenceRules_excludesAmmunition() {
        assertFalse(WeaponUpgradeEquivalence.shouldApplyWeaponEquivalenceRules(ARROW_STACK_AMMO));
        assertFalse(WeaponUpgradeEquivalence.shouldApplyWeaponEquivalenceRules(BOLT_STACK_AMMO));
        assertTrue(WeaponUpgradeEquivalence.shouldApplyWeaponEquivalenceRules(SHORT_BOW_RANGED_WEAPON));
    }

    @Test
    @DisplayName("shouldApplyWeaponEquivalenceRules: non-reinforcing spell tools are excluded from weapon peer group")
    void shouldApplyWeaponEquivalenceRules_excludesPlainSpellTools() {
        assertFalse(WeaponUpgradeEquivalence.shouldApplyWeaponEquivalenceRules(NON_REINFORCING_SPELL_TOOL));
    }

    /**
     * Cross-type contract test. For every ordered pair (sourceSample, targetPathDescriptor):
     *
     * <ul>
     *   <li>compute the peer's ceiling tier T = sourceSample.expectedTier</li>
     *   <li>ask the equivalence helpers for the chosen target {@code upgradeLevel}</li>
     *   <li>if any candidate level qualifies (its tier ≤ T), verify chosen is the highest such
     *       level — this is the "lift the loot as high as the peer allows" property</li>
     *   <li>otherwise (target's minimum tier &gt; T, e.g. DIVINE +0 = tier 5 with T = 1),
     *       verify chosen is {@code 0} (best-effort fallback; auto-upgrade declines higher up)</li>
     *   <li>verify chosen is always within {@code [0, targetMax]}</li>
     * </ul>
     */
    @ParameterizedTest(name = "{0}  →  target {1}")
    @MethodSource("crossPathPairs")
    @DisplayName("Cross-type: target picks highest qualifying reinforcement, or 0 fallback when path cannot fit")
    void crossUpgradePath_targetAtOrBelowPeerTier(
            final WeaponPathSample sourceSample,
            final WeaponPathTarget targetDescriptor
    ) {
        final int peerCeilingTier = sourceSample.expectedTier();

        final int chosenTargetUpgradeLevel = targetDescriptor.pickHighestAtOrBelowTier(peerCeilingTier);
        final int expectedChosenUpgradeLevel =
                targetDescriptor.expectedChosenUpgradeLevelForCeiling(peerCeilingTier);

        assertEquals(
                expectedChosenUpgradeLevel,
                chosenTargetUpgradeLevel,
                () -> "target "
                        + targetDescriptor
                        + " for peer ceiling "
                        + peerCeilingTier
                        + " should pick upgradeLevel +"
                        + expectedChosenUpgradeLevel
                        + " (highest projecting ≤ ceiling, or 0 fallback when no level fits),"
                        + " but picked +"
                        + chosenTargetUpgradeLevel);

        assertTrue(
                chosenTargetUpgradeLevel >= 0
                        && chosenTargetUpgradeLevel <= targetDescriptor.maximumUpgradeLevel(),
                () -> "chosen upgrade level "
                        + chosenTargetUpgradeLevel
                        + " is outside [0, "
                        + targetDescriptor.maximumUpgradeLevel()
                        + "]");

        final int chosenTargetTier = targetDescriptor.projectUpgradeLevelToTier(chosenTargetUpgradeLevel);
        final int targetMinimumTier = targetDescriptor.projectUpgradeLevelToTier(0);
        if (peerCeilingTier >= targetMinimumTier) {
            assertTrue(
                    chosenTargetTier <= peerCeilingTier,
                    () -> "target "
                            + targetDescriptor
                            + " at +"
                            + chosenTargetUpgradeLevel
                            + " projects to tier "
                            + chosenTargetTier
                            + " which overshoots peer ceiling "
                            + peerCeilingTier
                            + " (target minTier "
                            + targetMinimumTier
                            + " fits; a qualifying level should have been picked)");
        }
    }

    static Stream<Arguments> crossPathPairs() {
        final List<WeaponPathSample> sourceSamples = allSourceSamples();
        final List<WeaponPathTarget> targetDescriptors = allTargetDescriptors();
        final List<Arguments> orderedPairs = new ArrayList<>();
        for (final WeaponPathSample source : sourceSamples) {
            for (final WeaponPathTarget target : targetDescriptors) {
                orderedPairs.add(Arguments.of(source, target));
            }
        }
        return orderedPairs.stream();
    }

    private static List<WeaponPathSample> allSourceSamples() {
        final List<WeaponPathSample> samples = new ArrayList<>();
        for (int upgradeLevel = 0; upgradeLevel <= 15; upgradeLevel++) {
            samples.add(new WeaponPathSample("NORMAL+" + upgradeLevel, upgradeLevel));
        }
        final int[] twinklingTierTable = {0, 3, 6, 9, 12, 15};
        for (int twinklingLevel = 0; twinklingLevel <= 5; twinklingLevel++) {
            samples.add(new WeaponPathSample("UNIQUE+" + twinklingLevel, twinklingTierTable[twinklingLevel]));
        }
        for (final WeaponInfusionPath infusionPath :
                List.of(WeaponInfusionPath.DIVINE, WeaponInfusionPath.MAGIC, WeaponInfusionPath.FIRE)) {
            for (int upgradeLevel = 0; upgradeLevel <= 10; upgradeLevel++) {
                samples.add(new WeaponPathSample(infusionPath + "+" + upgradeLevel, 5 + upgradeLevel));
            }
        }
        for (final WeaponInfusionPath infusionPath :
                List.of(
                        WeaponInfusionPath.LIGHTNING,
                        WeaponInfusionPath.CRYSTAL,
                        WeaponInfusionPath.OCCULT,
                        WeaponInfusionPath.ENCHANTED,
                        WeaponInfusionPath.CHAOS,
                        WeaponInfusionPath.RAW)) {
            for (int upgradeLevel = 0; upgradeLevel <= 5; upgradeLevel++) {
                samples.add(new WeaponPathSample(infusionPath + "+" + upgradeLevel, 10 + upgradeLevel));
            }
        }
        for (int upgradeSteps = 0; upgradeSteps <= 15; upgradeSteps++) {
            samples.add(new WeaponPathSample("PYRO_FLAME+" + upgradeSteps, upgradeSteps));
        }
        for (int upgradeSteps = 0; upgradeSteps <= 5; upgradeSteps++) {
            samples.add(new WeaponPathSample("PYRO_ASCENDED+" + upgradeSteps, twinklingTierTable[upgradeSteps]));
        }
        return samples;
    }

    private static List<WeaponPathTarget> allTargetDescriptors() {
        final List<WeaponPathTarget> targets = new ArrayList<>();
        targets.add(new WeaponPathTarget("NORMAL", 15) {
            @Override
            int pickHighestAtOrBelowTier(final int ceilingTier) {
                return WeaponUpgradeEquivalence.maximumInfusionUpgradeLevelAtOrBelowEquivalentNormalTier(
                        WeaponInfusionPath.NORMAL, 15, ceilingTier);
            }

            @Override
            int projectUpgradeLevelToTier(final int upgradeLevel) {
                return WeaponUpgradeEquivalence.equivalentNormalTier(
                        INFUSABLE_MELEE_WEAPON,
                        new WeaponUpgradeDecode.Infused(WeaponInfusionPath.NORMAL, upgradeLevel));
            }
        });
        targets.add(new WeaponPathTarget("UNIQUE", 5) {
            @Override
            int pickHighestAtOrBelowTier(final int ceilingTier) {
                return WeaponUpgradeEquivalence.twinklingUpgradeLevelAtOrBelowEquivalentNormalTier(ceilingTier);
            }

            @Override
            int projectUpgradeLevelToTier(final int upgradeLevel) {
                return WeaponUpgradeEquivalence.equivalentNormalTier(
                        UNIQUE_MELEE_WEAPON, new WeaponUpgradeDecode.Twinkling(upgradeLevel));
            }
        });
        for (final WeaponInfusionPath infusionPath :
                List.of(WeaponInfusionPath.DIVINE, WeaponInfusionPath.MAGIC, WeaponInfusionPath.FIRE)) {
            targets.add(new WeaponPathTarget(infusionPath.name(), 10) {
                @Override
                int pickHighestAtOrBelowTier(final int ceilingTier) {
                    return WeaponUpgradeEquivalence.maximumInfusionUpgradeLevelAtOrBelowEquivalentNormalTier(
                            infusionPath, 10, ceilingTier);
                }

                @Override
                int projectUpgradeLevelToTier(final int upgradeLevel) {
                    return WeaponUpgradeEquivalence.equivalentNormalTier(
                            INFUSABLE_MELEE_WEAPON,
                            new WeaponUpgradeDecode.Infused(infusionPath, upgradeLevel));
                }
            });
        }
        for (final WeaponInfusionPath infusionPath :
                List.of(
                        WeaponInfusionPath.LIGHTNING,
                        WeaponInfusionPath.CRYSTAL,
                        WeaponInfusionPath.OCCULT,
                        WeaponInfusionPath.ENCHANTED,
                        WeaponInfusionPath.CHAOS,
                        WeaponInfusionPath.RAW)) {
            targets.add(new WeaponPathTarget(infusionPath.name(), 5) {
                @Override
                int pickHighestAtOrBelowTier(final int ceilingTier) {
                    return WeaponUpgradeEquivalence.maximumInfusionUpgradeLevelAtOrBelowEquivalentNormalTier(
                            infusionPath, 5, ceilingTier);
                }

                @Override
                int projectUpgradeLevelToTier(final int upgradeLevel) {
                    return WeaponUpgradeEquivalence.equivalentNormalTier(
                            INFUSABLE_MELEE_WEAPON,
                            new WeaponUpgradeDecode.Infused(infusionPath, upgradeLevel));
                }
            });
        }
        targets.add(new WeaponPathTarget("PYRO_FLAME", 15) {
            @Override
            int pickHighestAtOrBelowTier(final int ceilingTier) {
                return Math.clamp(ceilingTier, 0, 15);
            }

            @Override
            int projectUpgradeLevelToTier(final int upgradeLevel) {
                return WeaponUpgradeEquivalence.equivalentNormalTier(
                        PYRO_FLAME_TOOL, new WeaponUpgradeDecode.PyromancyFlame(upgradeLevel));
            }
        });
        targets.add(new WeaponPathTarget("PYRO_ASCENDED", 5) {
            @Override
            int pickHighestAtOrBelowTier(final int ceilingTier) {
                return WeaponUpgradeEquivalence.twinklingUpgradeLevelAtOrBelowEquivalentNormalTier(ceilingTier);
            }

            @Override
            int projectUpgradeLevelToTier(final int upgradeLevel) {
                return WeaponUpgradeEquivalence.equivalentNormalTier(
                        PYRO_ASCENDED_TOOL, new WeaponUpgradeDecode.PyromancyFlame(upgradeLevel));
            }
        });
        return targets;
    }

    /**
     * Source/peer sample: a known weapon upgrade-state described by its label and the equivalent
     * NORMAL tier that the equivalence rules must assign to it.
     */
    private record WeaponPathSample(String label, int expectedTier) {
        @Override
        public String toString() {
            return label + " (tier " + expectedTier + ")";
        }
    }

    /**
     * Target descriptor: a candidate upgrade path that should be raised "as high as possible without
     * exceeding the peer's equivalent NORMAL tier".
     */
    private abstract static class WeaponPathTarget {
        private final String label;
        private final int maximumUpgradeLevel;

        WeaponPathTarget(
                final String label,
                final int maximumUpgradeLevel
        ) {
            this.label = label;
            this.maximumUpgradeLevel = maximumUpgradeLevel;
        }

        abstract int pickHighestAtOrBelowTier(int ceilingTier);

        abstract int projectUpgradeLevelToTier(int upgradeLevel);

        int maximumUpgradeLevel() {
            return maximumUpgradeLevel;
        }

        /**
         * Predict the picker's output independently from its iteration logic: highest level whose
         * projected tier ≤ ceiling, falling back to {@code 0} when no level qualifies (the documented
         * picker behaviour when the path's minimum tier already exceeds the ceiling).
         */
        int expectedChosenUpgradeLevelForCeiling(final int peerCeilingTier) {
            int highestQualifyingUpgradeLevel = -1;
            for (int candidateUpgradeLevel = 0;
                    candidateUpgradeLevel <= maximumUpgradeLevel;
                    candidateUpgradeLevel++) {
                if (projectUpgradeLevelToTier(candidateUpgradeLevel) <= peerCeilingTier) {
                    highestQualifyingUpgradeLevel = candidateUpgradeLevel;
                }
            }
            return Math.max(highestQualifyingUpgradeLevel, 0);
        }

        @Override
        public String toString() {
            return label + " (max +" + maximumUpgradeLevel + ")";
        }
    }
}
