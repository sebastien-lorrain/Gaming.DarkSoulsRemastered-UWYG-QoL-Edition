package gaming.dsr.uwyg.equipment;

import gaming.dsr.uwyg.equipment.types.BaseEquipmentDefinition;
import gaming.dsr.uwyg.equipment.types.InventorySlot;
import gaming.dsr.uwyg.inventory.InventorySnapshotReader;
import gaming.dsr.uwyg.equipment.types.enums.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class EquipmentClassifierTest {

    private static final int BASE_ITEM_ID_SHORTSWORD = 200000;
    private static final int BASE_ITEM_ID_LONGSWORD = 201000;
    private static final int BASE_ITEM_ID_GHOST_BLADE = 102000;
    private static final int BASE_ITEM_ID_PARRYING_DAGGER = 101000;
    private static final int BASE_ITEM_ID_DARK_HAND = 904000;
    private static final int BASE_ITEM_ID_SHORT_BOW = 1200000;
    private static final int BASE_ITEM_ID_EAST_WEST_SHIELD = 1400000;
    private static final int BASE_ITEM_ID_HEATER_SHIELD = 1450000;
    private static final int BASE_ITEM_ID_PYRO_FLAME = 1330000;
    private static final int BASE_ITEM_ID_PYRO_ASCENDED = 1332000;
    private static final int BASE_ITEM_ID_TALISMAN = 1360000;
    private static final int BASE_ITEM_ID_STANDARD_ARROW = 2000000;
    private static final int BASE_ITEM_ID_STANDARD_BOLT = 2100000;
    private static final int BASE_ITEM_ID_CATARINA_HELM = 10000;
    private static final int BASE_ITEM_ID_CATARINA_ARMOR_CHEST = 11000;
    private static final int BASE_ITEM_ID_CATARINA_LEGGINGS = 13000;
    private static final int BASE_ITEM_ID_BRIGAND_HOOD = 50000;
    private static final int BASE_ITEM_ID_SMOUGHS_HELM = 80000;
    private static final int BASE_ITEM_ID_SOUL_ARROW_SPELL = 3000;
    private static final int BASE_ITEM_ID_RING_HAVELS = 100;
    private static final int BASE_ITEM_ID_RING_COVENANT_OF_ARTORIAS = 138;
    private static final int BASE_ITEM_ID_RING_ORANGE_CHARRED = 139;
    private static final int BASE_ITEM_ID_RING_ANY = 100000;
    private static final int BASE_ITEM_ID_UNKNOWN_CONSUMABLE = 999999;

    private static final int ITEM_TYPE_VALUE_ARMOR_MASK = 0x10000000;
    private static final int ITEM_TYPE_VALUE_RING_MASK = 0x20000000;
    private static final int ITEM_TYPE_VALUE_CONSUMABLE_MASK = 0x40000000;

    private final EquipmentClassifier resolver = new EquipmentClassifier();

    @ParameterizedTest(name = "catalogKeyFromInventoryId({0}, {1}) == {2}")
    @MethodSource("catalogKeyCases")
    void catalogKeyFromInventoryId_stripsMaskWhenPresent(
            final int rawMemoryId,
            final ItemType slotItemType,
            final int expectedCatalogKey
    ) {
        assertEquals(
                expectedCatalogKey,
                EquipmentClassifier.catalogKeyFromInventoryId(rawMemoryId, slotItemType));
    }

    static Stream<Arguments> catalogKeyCases() {
        return Stream.of(
                Arguments.of(BASE_ITEM_ID_SHORTSWORD, ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD),
                Arguments.of(BASE_ITEM_ID_CATARINA_HELM, ItemType.ARMOR, BASE_ITEM_ID_CATARINA_HELM),
                Arguments.of(
                        ITEM_TYPE_VALUE_ARMOR_MASK | BASE_ITEM_ID_CATARINA_HELM,
                        ItemType.ARMOR,
                        BASE_ITEM_ID_CATARINA_HELM),
                Arguments.of(
                        ITEM_TYPE_VALUE_CONSUMABLE_MASK | BASE_ITEM_ID_SOUL_ARROW_SPELL,
                        ItemType.CONSUMABLE,
                        BASE_ITEM_ID_SOUL_ARROW_SPELL),
                Arguments.of(BASE_ITEM_ID_SHORTSWORD, ItemType.NONE, BASE_ITEM_ID_SHORTSWORD),
                Arguments.of(BASE_ITEM_ID_SHORTSWORD, null, BASE_ITEM_ID_SHORTSWORD));
    }

    @ParameterizedTest(name = "inventoryIdFromCatalogKey({0}, {1}) == {2}")
    @MethodSource("inventoryIdCases")
    void inventoryIdFromCatalogKey_addsMaskOnlyWhenKeyExceedsMask(
            final int catalogKey,
            final ItemType slotItemType,
            final int expectedInventoryId
    ) {
        assertEquals(
                expectedInventoryId,
                EquipmentClassifier.inventoryIdFromCatalogKey(catalogKey, slotItemType));
    }

    static Stream<Arguments> inventoryIdCases() {
        return Stream.of(
                Arguments.of(BASE_ITEM_ID_SHORTSWORD, ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD),
                Arguments.of(BASE_ITEM_ID_CATARINA_HELM, ItemType.ARMOR, BASE_ITEM_ID_CATARINA_HELM),
                Arguments.of(
                        ITEM_TYPE_VALUE_ARMOR_MASK,
                        ItemType.ARMOR,
                        ITEM_TYPE_VALUE_ARMOR_MASK + ITEM_TYPE_VALUE_ARMOR_MASK),
                Arguments.of(BASE_ITEM_ID_SHORTSWORD, ItemType.NONE, BASE_ITEM_ID_SHORTSWORD),
                Arguments.of(BASE_ITEM_ID_SHORTSWORD, null, BASE_ITEM_ID_SHORTSWORD));
    }

    @Test
    void resolveToDefinition_exactWeapon_returnsCatalogRow() {
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(BASE_ITEM_ID_SHORTSWORD, ItemType.WEAPON);
        assertTrue(definition.isPresent());
        assertEquals(BASE_ITEM_ID_SHORTSWORD, definition.get().baseItemId());
        assertEquals(EquipmentCategory.MELEE_WEAPON, definition.get().category());
        assertEquals(ItemUpgradePath.INFUSABLE, definition.get().upgradePath());
    }

    @Test
    void resolveToDefinition_upgradedNormalInfusion_resolvesBackToBaseDefinition() {
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(BASE_ITEM_ID_SHORTSWORD + 5, ItemType.WEAPON);
        assertTrue(definition.isPresent());
        assertEquals(BASE_ITEM_ID_SHORTSWORD, definition.get().baseItemId());
    }

    @Test
    void resolveToDefinition_upgradedInfusedWeapon_resolvesBackToBaseDefinition() {
        final int lightningPlusThreeId =
                BASE_ITEM_ID_SHORTSWORD + 3 + WeaponInfusionPath.LIGHTNING.getItemIdOffset();
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(lightningPlusThreeId, ItemType.WEAPON);
        assertTrue(definition.isPresent());
        assertEquals(BASE_ITEM_ID_SHORTSWORD, definition.get().baseItemId());
    }

    @Test
    void resolveToDefinition_uniqueWeaponUpgraded_resolvesBackToBaseDefinition() {
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(BASE_ITEM_ID_GHOST_BLADE + 4, ItemType.WEAPON);
        assertTrue(definition.isPresent());
        assertEquals(BASE_ITEM_ID_GHOST_BLADE, definition.get().baseItemId());
        assertEquals(ItemUpgradePath.UNIQUE, definition.get().upgradePath());
    }

    @Test
    void resolveToDefinition_standardArmorUpgraded_resolvesBackToBaseDefinition() {
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(
                        BASE_ITEM_ID_BRIGAND_HOOD + 7, ItemType.ARMOR);
        assertTrue(definition.isPresent());
        assertEquals(BASE_ITEM_ID_BRIGAND_HOOD, definition.get().baseItemId());
    }

    @Test
    void resolveToDefinition_armorWithMaskInMemoryId_stripsAndResolves() {
        final int maskedId = ITEM_TYPE_VALUE_ARMOR_MASK | BASE_ITEM_ID_CATARINA_HELM;
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(maskedId, ItemType.ARMOR);
        assertTrue(definition.isPresent());
        assertEquals(BASE_ITEM_ID_CATARINA_HELM, definition.get().baseItemId());
    }

    @Test
    void resolveToDefinition_consumableSpell_returnsSpellRow() {
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(
                        BASE_ITEM_ID_SOUL_ARROW_SPELL, ItemType.CONSUMABLE);
        assertTrue(definition.isPresent());
        assertEquals(EquipmentCategory.SPELL, definition.get().category());
    }

    @Test
    void resolveToDefinition_pyromancyFlameAscendedUpgraded_resolvesBackToBaseDefinition() {
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(
                        BASE_ITEM_ID_PYRO_ASCENDED + 300, ItemType.WEAPON);
        assertTrue(definition.isPresent());
        assertEquals(BASE_ITEM_ID_PYRO_ASCENDED, definition.get().baseItemId());
        assertEquals(ItemUpgradePath.PYRO_ASCENDED, definition.get().upgradePath());
    }

    @Test
    void resolveToDefinition_pyromancyFlameUpgradedWithNonMultipleStep_returnsEmpty() {
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(
                        BASE_ITEM_ID_PYRO_FLAME + 50, ItemType.WEAPON);
        assertFalse(definition.isPresent());
    }

    @Test
    void resolveToDefinition_unknownWeapon_returnsEmpty() {
        assertFalse(
                EquipmentClassifier.resolveToDefinition(999_999, ItemType.WEAPON).isPresent());
    }

    @Test
    void resolveToDefinition_ringWithUnknownId_returnsEmpty() {
        assertFalse(EquipmentClassifier.resolveToDefinition(1234, ItemType.RING).isPresent());
    }

    @Test
    void resolveToDefinition_knownRing_returnsRingRow() {
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(BASE_ITEM_ID_RING_HAVELS, ItemType.RING);
        assertTrue(definition.isPresent());
        assertEquals(BASE_ITEM_ID_RING_HAVELS, definition.get().baseItemId());
        assertEquals(EquipmentCategory.RING, definition.get().category());
        assertEquals(ItemUpgradePath.NONE, definition.get().upgradePath());
        assertEquals("Havel's Ring", definition.get().displayName());
    }

    @Test
    void resolveToDefinition_manualEquipRings_resolveByCatalogKey() {
        assertTrue(
                EquipmentClassifier.resolveToDefinition(
                                BASE_ITEM_ID_RING_COVENANT_OF_ARTORIAS, ItemType.RING)
                        .isPresent());
        assertTrue(
                EquipmentClassifier.resolveToDefinition(BASE_ITEM_ID_RING_ORANGE_CHARRED, ItemType.RING)
                        .isPresent());
    }

    @Test
    void resolveToDefinition_ringWithRingMaskInMemoryId_stripsAndResolves() {
        final int maskedId = ITEM_TYPE_VALUE_RING_MASK | BASE_ITEM_ID_RING_HAVELS;
        final Optional<BaseEquipmentDefinition> definition =
                EquipmentClassifier.resolveToDefinition(maskedId, ItemType.RING);
        assertTrue(definition.isPresent());
        assertEquals(BASE_ITEM_ID_RING_HAVELS, definition.get().baseItemId());
    }

    @Test
    void resolveToDefinition_noneType_returnsEmpty() {
        assertFalse(EquipmentClassifier.resolveToDefinition(1234, ItemType.NONE).isPresent());
    }

    @ParameterizedTest(name = "maximumUpgradeLevel({0}) == {1}")
    @CsvSource({
        "NONE,0",
        "UNIQUE,5",
        "STANDARD_ARMOR,10",
        "INFUSABLE,15",
        "INFUSABLE_RESTRICTED,15",
        "PYRO_FLAME,15",
        "PYRO_ASCENDED,5"
    })
    void maximumUpgradeLevel_byUpgradePath(
            final ItemUpgradePath upgradePath,
            final int expectedMaximum
    ) {
        assertEquals(expectedMaximum, EquipmentClassifier.maximumUpgradeLevel(upgradePath));
    }

    @ParameterizedTest(name = "maximumUpgradeLevel({0}, {1}) == {2}")
    @CsvSource({
        "INFUSABLE,NORMAL,15",
        "INFUSABLE,LIGHTNING,5",
        "INFUSABLE,FIRE,10",
        "INFUSABLE,MAGIC,10",
        "INFUSABLE,DIVINE,10",
        "INFUSABLE,CHAOS,5",
        "INFUSABLE_RESTRICTED,NORMAL,15",
        "INFUSABLE_RESTRICTED,LIGHTNING,5",
        "INFUSABLE_RESTRICTED,FIRE,10"
    })
    void maximumUpgradeLevel_byInfusionPath(
            final ItemUpgradePath upgradePath,
            final WeaponInfusionPath infusionPath,
            final int expectedMaximum
    ) {
        final OptionalInt actual = EquipmentClassifier.maximumUpgradeLevel(upgradePath, infusionPath);
        assertTrue(actual.isPresent());
        assertEquals(expectedMaximum, actual.getAsInt());
    }

    @ParameterizedTest(name = "INFUSABLE_RESTRICTED + {0} is unavailable")
    @CsvSource({"RAW", "ENCHANTED", "OCCULT", "CHAOS"})
    void maximumUpgradeLevel_restrictedInfusableUnavailablePaths_areEmpty(
            final WeaponInfusionPath unavailableInfusionPath
    ) {
        assertFalse(
                EquipmentClassifier.maximumUpgradeLevel(
                                ItemUpgradePath.INFUSABLE_RESTRICTED, unavailableInfusionPath)
                        .isPresent());
    }

    @Test
    void maximumUpgradeLevel_byBaseEquipmentDefinition_delegatesToPath() {
        final BaseEquipmentDefinition uniqueDefinition =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_GHOST_BLADE,
                        1,
                        ItemUpgradePath.UNIQUE,
                        EquipmentCategory.MELEE_WEAPON,
                        "Ghost Blade");
        assertEquals(5, EquipmentClassifier.maximumUpgradeLevel(uniqueDefinition));
    }

    @ParameterizedTest(name = "armorTypeFromId({0}) == {1}")
    @CsvSource({
        "10000,HEAD",
        "11000,CHEST",
        "12000,HANDS",
        "13000,LEGS",
        "14000,UNKNOWN"
    })
    void armorTypeFromId_byArmorPieceIndex(
            final int armorMemoryId,
            final ArmorType expectedArmorType
    ) {
        assertEquals(expectedArmorType, resolver.armorTypeFromId(armorMemoryId, ItemType.ARMOR));
    }

    @Test
    void armorTypeFromId_withMask_stripsAndResolves() {
        assertEquals(
                ArmorType.HEAD,
                resolver.armorTypeFromId(
                        ITEM_TYPE_VALUE_ARMOR_MASK | BASE_ITEM_ID_CATARINA_HELM, ItemType.ARMOR));
        assertEquals(
                ArmorType.LEGS,
                resolver.armorTypeFromId(
                        ITEM_TYPE_VALUE_ARMOR_MASK | BASE_ITEM_ID_CATARINA_LEGGINGS, ItemType.ARMOR));
    }

    @ParameterizedTest(name = "weaponTypeFromId(id={0}, type={1}) == {2}")
    @MethodSource("weaponTypeCases")
    void weaponTypeFromId_branchesPerCategory(
            final int weaponMemoryId,
            final ItemType slotItemType,
            final WeaponType expectedKind
    ) {
        assertEquals(expectedKind, resolver.weaponTypeFromId(weaponMemoryId, slotItemType));
    }

    static Stream<Arguments> weaponTypeCases() {
        return Stream.of(
                Arguments.of(BASE_ITEM_ID_SHORTSWORD, ItemType.WEAPON, WeaponType.RIGHT_HAND),
                Arguments.of(BASE_ITEM_ID_PARRYING_DAGGER, ItemType.WEAPON, WeaponType.LEFT_HAND),
                Arguments.of(BASE_ITEM_ID_EAST_WEST_SHIELD, ItemType.WEAPON, WeaponType.LEFT_HAND),
                Arguments.of(BASE_ITEM_ID_SHORT_BOW, ItemType.WEAPON, WeaponType.LEFT_HAND),
                Arguments.of(BASE_ITEM_ID_STANDARD_ARROW, ItemType.WEAPON, WeaponType.ARROW),
                Arguments.of(BASE_ITEM_ID_STANDARD_BOLT, ItemType.WEAPON, WeaponType.BOLT),
                Arguments.of(BASE_ITEM_ID_PYRO_FLAME, ItemType.WEAPON, WeaponType.LEFT_HAND),
                Arguments.of(BASE_ITEM_ID_TALISMAN, ItemType.WEAPON, WeaponType.LEFT_HAND),
                Arguments.of(BASE_ITEM_ID_CATARINA_HELM, ItemType.ARMOR, WeaponType.NONE),
                Arguments.of(BASE_ITEM_ID_SOUL_ARROW_SPELL, ItemType.CONSUMABLE, WeaponType.NONE),
                Arguments.of(BASE_ITEM_ID_RING_COVENANT_OF_ARTORIAS, ItemType.RING, WeaponType.NONE),
                Arguments.of(999_999, ItemType.WEAPON, WeaponType.RIGHT_HAND),
                Arguments.of(999_999, ItemType.CONSUMABLE, WeaponType.RIGHT_HAND));
    }

    @ParameterizedTest(name = "parseWeaponUpgradeState UNIQUE +{0} → Twinkling(+{0})")
    @CsvSource({"0", "1", "3", "5"})
    void parseWeaponUpgradeState_uniqueWithinRange_decodesTwinkling(
            final int upgradeLevel
    ) {
        final BaseEquipmentDefinition definition =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_GHOST_BLADE,
                        1,
                        ItemUpgradePath.UNIQUE,
                        EquipmentCategory.MELEE_WEAPON,
                        "Ghost Blade");
        final Optional<WeaponUpgradeDecode> decoded =
                EquipmentClassifier.parseWeaponUpgradeState(
                        BASE_ITEM_ID_GHOST_BLADE + upgradeLevel, ItemType.WEAPON, definition);
        assertTrue(decoded.isPresent());
        assertTrue(decoded.get() instanceof WeaponUpgradeDecode.Twinkling);
        assertEquals(
                upgradeLevel,
                ((WeaponUpgradeDecode.Twinkling) decoded.get()).twinklingUpgradeLevel());
    }

    @Test
    void parseWeaponUpgradeState_uniqueOutOfRange_returnsEmpty() {
        final BaseEquipmentDefinition definition =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_GHOST_BLADE,
                        1,
                        ItemUpgradePath.UNIQUE,
                        EquipmentCategory.MELEE_WEAPON,
                        "Ghost Blade");
        assertFalse(
                EquipmentClassifier.parseWeaponUpgradeState(
                                BASE_ITEM_ID_GHOST_BLADE + 99, ItemType.WEAPON, definition)
                        .isPresent());
    }

    @Test
    void parseWeaponUpgradeState_infusableLightningUpgrade_decodesInfusion() {
        final BaseEquipmentDefinition definition = catalogShortswordDefinition();
        final int lightningPlusThreeId =
                BASE_ITEM_ID_SHORTSWORD + 3 + WeaponInfusionPath.LIGHTNING.getItemIdOffset();
        final Optional<WeaponUpgradeDecode> decoded =
                EquipmentClassifier.parseWeaponUpgradeState(
                        lightningPlusThreeId, ItemType.WEAPON, definition);
        assertTrue(decoded.isPresent());
        assertTrue(decoded.get() instanceof WeaponUpgradeDecode.Infused);
        final WeaponUpgradeDecode.Infused infused = (WeaponUpgradeDecode.Infused) decoded.get();
        assertEquals(WeaponInfusionPath.LIGHTNING, infused.infusionPath());
        assertEquals(3, infused.upgradeLevel());
    }

    @Test
    void parseWeaponUpgradeState_pyromancyFlameUpgrade_decodesSteps() {
        final BaseEquipmentDefinition definition =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_PYRO_FLAME,
                        1,
                        ItemUpgradePath.PYRO_FLAME,
                        EquipmentCategory.SPELL_TOOL,
                        "Pyromancy Flame");
        final Optional<WeaponUpgradeDecode> decoded =
                EquipmentClassifier.parseWeaponUpgradeState(
                        BASE_ITEM_ID_PYRO_FLAME + 700, ItemType.WEAPON, definition);
        assertTrue(decoded.isPresent());
        assertEquals(7, ((WeaponUpgradeDecode.PyromancyFlame) decoded.get()).upgradeSteps());
    }

    @Test
    void parseWeaponUpgradeState_noneUpgradePath_returnsEmpty() {
        final BaseEquipmentDefinition darkHand =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_DARK_HAND,
                        1,
                        ItemUpgradePath.NONE,
                        EquipmentCategory.MELEE_WEAPON,
                        "Dark Hand");
        assertFalse(
                EquipmentClassifier.parseWeaponUpgradeState(
                                BASE_ITEM_ID_DARK_HAND, ItemType.WEAPON, darkHand)
                        .isPresent());
    }

    @ParameterizedTest(name = "decodeUpgradeLevel {0} returns {1}")
    @MethodSource("decodeUpgradeLevelCases")
    void decodeUpgradeLevel_perPath(
            final BaseEquipmentDefinition definition,
            final int rawMemoryId,
            final int expectedUpgradeLevel
    ) {
        assertEquals(
                expectedUpgradeLevel,
                EquipmentClassifier.decodeUpgradeLevel(rawMemoryId, ItemType.WEAPON, definition));
    }

    static Stream<Arguments> decodeUpgradeLevelCases() {
        final BaseEquipmentDefinition shortswordInfusable = catalogShortswordDefinition();
        final BaseEquipmentDefinition ghostBladeUnique =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_GHOST_BLADE,
                        1,
                        ItemUpgradePath.UNIQUE,
                        EquipmentCategory.MELEE_WEAPON,
                        "Ghost Blade");
        final BaseEquipmentDefinition brigandHoodStandard =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_BRIGAND_HOOD,
                        1,
                        ItemUpgradePath.STANDARD_ARMOR,
                        EquipmentCategory.ARMOR,
                        "Brigand Hood");
        final BaseEquipmentDefinition pyroFlame =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_PYRO_FLAME,
                        1,
                        ItemUpgradePath.PYRO_FLAME,
                        EquipmentCategory.SPELL_TOOL,
                        "Pyromancy Flame");
        final BaseEquipmentDefinition pyroAscended =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_PYRO_ASCENDED,
                        1,
                        ItemUpgradePath.PYRO_ASCENDED,
                        EquipmentCategory.SPELL_TOOL,
                        "Pyromancy Flame (Ascended)");
        final BaseEquipmentDefinition darkHandNone =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_DARK_HAND,
                        1,
                        ItemUpgradePath.NONE,
                        EquipmentCategory.MELEE_WEAPON,
                        "Dark Hand");

        return Stream.of(
                Arguments.of(shortswordInfusable, BASE_ITEM_ID_SHORTSWORD + 5, 5),
                Arguments.of(
                        shortswordInfusable,
                        BASE_ITEM_ID_SHORTSWORD + 3 + WeaponInfusionPath.LIGHTNING.getItemIdOffset(),
                        3),
                Arguments.of(ghostBladeUnique, BASE_ITEM_ID_GHOST_BLADE + 4, 4),
                Arguments.of(ghostBladeUnique, BASE_ITEM_ID_GHOST_BLADE + 99, 0),
                Arguments.of(brigandHoodStandard, BASE_ITEM_ID_BRIGAND_HOOD + 7, 7),
                Arguments.of(brigandHoodStandard, BASE_ITEM_ID_BRIGAND_HOOD + 99, 0),
                Arguments.of(pyroFlame, BASE_ITEM_ID_PYRO_FLAME + 700, 7),
                Arguments.of(pyroFlame, BASE_ITEM_ID_PYRO_FLAME + 50, 0),
                Arguments.of(pyroAscended, BASE_ITEM_ID_PYRO_ASCENDED + 400, 4),
                Arguments.of(darkHandNone, BASE_ITEM_ID_DARK_HAND, 0));
    }

    @Test
    void encodeWeaponMemoryIdMatchingPeerEquivalent_uniqueWeapon_picksHighestQualifyingTwinklingLevel() {
        final BaseEquipmentDefinition ghostBlade =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_GHOST_BLADE,
                        1,
                        ItemUpgradePath.UNIQUE,
                        EquipmentCategory.MELEE_WEAPON,
                        "Ghost Blade");
        final int encoded =
                EquipmentClassifier.encodeWeaponMemoryIdMatchingPeerEquivalent(
                        ghostBlade, 9, ItemType.WEAPON, Optional.empty());
        assertEquals(BASE_ITEM_ID_GHOST_BLADE + 3, encoded);
    }

    @Test
    void encodeWeaponMemoryIdMatchingPeerEquivalent_infusableWithoutLootDecode_usesNormalPath() {
        final BaseEquipmentDefinition shortsword = catalogShortswordDefinition();
        final int encoded =
                EquipmentClassifier.encodeWeaponMemoryIdMatchingPeerEquivalent(
                        shortsword, 7, ItemType.WEAPON, Optional.empty());
        assertEquals(BASE_ITEM_ID_SHORTSWORD + 7, encoded);
    }

    @Test
    void encodeWeaponMemoryIdMatchingPeerEquivalent_infusableWithInfusedLootDecode_keepsInfusionPath() {
        final BaseEquipmentDefinition shortsword = catalogShortswordDefinition();
        final Optional<WeaponUpgradeDecode> lootDecode =
                Optional.of(new WeaponUpgradeDecode.Infused(WeaponInfusionPath.LIGHTNING, 0));
        final int encoded =
                EquipmentClassifier.encodeWeaponMemoryIdMatchingPeerEquivalent(
                        shortsword, 12, ItemType.WEAPON, lootDecode);
        final int expected =
                BASE_ITEM_ID_SHORTSWORD + 2 + WeaponInfusionPath.LIGHTNING.getItemIdOffset();
        assertEquals(expected, encoded);
    }

    @Test
    void encodeWeaponMemoryIdMatchingPeerEquivalent_pyromancyFlame_addsHundredPerStep() {
        final BaseEquipmentDefinition pyroFlame =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_PYRO_FLAME,
                        1,
                        ItemUpgradePath.PYRO_FLAME,
                        EquipmentCategory.SPELL_TOOL,
                        "Pyromancy Flame");
        final int encoded =
                EquipmentClassifier.encodeWeaponMemoryIdMatchingPeerEquivalent(
                        pyroFlame, 10, ItemType.WEAPON, Optional.empty());
        assertEquals(BASE_ITEM_ID_PYRO_FLAME + 1000, encoded);
    }

    @Test
    void encodeWeaponMemoryIdMatchingPeerEquivalent_pyromancyAscended_usesTwinklingMapping() {
        final BaseEquipmentDefinition pyroAscended =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_PYRO_ASCENDED,
                        1,
                        ItemUpgradePath.PYRO_ASCENDED,
                        EquipmentCategory.SPELL_TOOL,
                        "Pyromancy Flame (Ascended)");
        final int encoded =
                EquipmentClassifier.encodeWeaponMemoryIdMatchingPeerEquivalent(
                        pyroAscended, 15, ItemType.WEAPON, Optional.empty());
        assertEquals(BASE_ITEM_ID_PYRO_ASCENDED + 500, encoded);
    }

    @Test
    void encodeArmorMemoryIdForEquivalentTier_uniqueArmor_addsTargetTierDirectly() {
        final BaseEquipmentDefinition catarinaHelm =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_CATARINA_HELM,
                        1,
                        ItemUpgradePath.UNIQUE,
                        EquipmentCategory.ARMOR,
                        "Catarina Helm");
        assertEquals(
                BASE_ITEM_ID_CATARINA_HELM + 3,
                EquipmentClassifier.encodeArmorMemoryIdForEquivalentTier(
                        catarinaHelm, 3, ItemType.ARMOR));
    }

    @Test
    void encodeArmorMemoryIdForEquivalentTier_standardArmor_usesBandMaximumUpgradeLevel() {
        final BaseEquipmentDefinition brigandHood =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_BRIGAND_HOOD,
                        1,
                        ItemUpgradePath.STANDARD_ARMOR,
                        EquipmentCategory.ARMOR,
                        "Brigand Hood");
        assertEquals(
                BASE_ITEM_ID_BRIGAND_HOOD + 9,
                EquipmentClassifier.encodeArmorMemoryIdForEquivalentTier(
                        brigandHood, 4, ItemType.ARMOR));
    }

    @Test
    void encodeArmorMemoryIdForEquivalentTier_noneUpgradePath_returnsBaseItemId() {
        final BaseEquipmentDefinition smoughsHelm =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_SMOUGHS_HELM,
                        1,
                        ItemUpgradePath.NONE,
                        EquipmentCategory.ARMOR,
                        "Smough's Helm");
        assertEquals(
                BASE_ITEM_ID_SMOUGHS_HELM,
                EquipmentClassifier.encodeArmorMemoryIdForEquivalentTier(
                        smoughsHelm, 4, ItemType.ARMOR));
    }

    @ParameterizedTest(name = "itemIdAtNormalUpgradeLevel {0} +{1} → {2}")
    @MethodSource("itemIdAtNormalCases")
    void itemIdAtNormalUpgradeLevel_perPath(
            final BaseEquipmentDefinition definition,
            final int normalPathUpgradeLevel,
            final int expectedItemId
    ) {
        assertEquals(
                expectedItemId,
                EquipmentClassifier.itemIdAtNormalUpgradeLevel(definition, normalPathUpgradeLevel));
    }

    static Stream<Arguments> itemIdAtNormalCases() {
        final BaseEquipmentDefinition shortsword = catalogShortswordDefinition();
        final BaseEquipmentDefinition ghostBladeUnique =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_GHOST_BLADE,
                        1,
                        ItemUpgradePath.UNIQUE,
                        EquipmentCategory.MELEE_WEAPON,
                        "Ghost Blade");
        final BaseEquipmentDefinition brigandHood =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_BRIGAND_HOOD,
                        1,
                        ItemUpgradePath.STANDARD_ARMOR,
                        EquipmentCategory.ARMOR,
                        "Brigand Hood");
        final BaseEquipmentDefinition pyroFlame =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_PYRO_FLAME,
                        1,
                        ItemUpgradePath.PYRO_FLAME,
                        EquipmentCategory.SPELL_TOOL,
                        "Pyromancy Flame");
        final BaseEquipmentDefinition darkHandNone =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_DARK_HAND,
                        1,
                        ItemUpgradePath.NONE,
                        EquipmentCategory.MELEE_WEAPON,
                        "Dark Hand");

        return Stream.of(
                Arguments.of(shortsword, 5, BASE_ITEM_ID_SHORTSWORD + 5),
                Arguments.of(shortsword, 99, BASE_ITEM_ID_SHORTSWORD + 15),
                Arguments.of(ghostBladeUnique, 3, BASE_ITEM_ID_GHOST_BLADE + 3),
                Arguments.of(ghostBladeUnique, 99, BASE_ITEM_ID_GHOST_BLADE + 5),
                Arguments.of(brigandHood, 7, BASE_ITEM_ID_BRIGAND_HOOD + 7),
                Arguments.of(pyroFlame, 5, BASE_ITEM_ID_PYRO_FLAME + 500),
                Arguments.of(darkHandNone, 99, BASE_ITEM_ID_DARK_HAND));
    }

    @Test
    void peerMaximumEquivalentNormalTier_emptyInventory_returnsZero() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        final BaseEquipmentDefinition shortsword = catalogShortswordDefinition();
        assertEquals(0, resolver.peerMaximumEquivalentNormalTier(inventory, shortsword, 0));
    }

    @Test
    void peerMaximumEquivalentNormalTier_normalInfusionPeer_returnsPeerLevel() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        placeSlot(inventory, 0, ItemType.WEAPON, BASE_ITEM_ID_LONGSWORD + 7);
        final BaseEquipmentDefinition shortsword = catalogShortswordDefinition();
        assertEquals(7, resolver.peerMaximumEquivalentNormalTier(inventory, shortsword, 5));
    }

    @Test
    void peerMaximumEquivalentNormalTier_takesMaximumAcrossMultiplePeers() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        placeSlot(inventory, 0, ItemType.WEAPON, BASE_ITEM_ID_LONGSWORD + 2);
        placeSlot(inventory, 1, ItemType.WEAPON, BASE_ITEM_ID_GHOST_BLADE + 4);
        placeSlot(inventory, 2, ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD + 5);
        final BaseEquipmentDefinition shortsword = catalogShortswordDefinition();
        assertEquals(12, resolver.peerMaximumEquivalentNormalTier(inventory, shortsword, 99));
    }

    @Test
    void peerMaximumEquivalentNormalTier_excludesGivenSlot() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        placeSlot(inventory, 3, ItemType.WEAPON, BASE_ITEM_ID_GHOST_BLADE + 5);
        final BaseEquipmentDefinition shortsword = catalogShortswordDefinition();
        assertEquals(0, resolver.peerMaximumEquivalentNormalTier(inventory, shortsword, 3));
    }

    @Test
    void peerMaximumEquivalentNormalTier_skipsInvalidSlots() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        final InventorySlot invalidSlot = inventory[0];
        invalidSlot.setType(ItemType.WEAPON);
        invalidSlot.setId(BASE_ITEM_ID_GHOST_BLADE + 5);
        invalidSlot.setValid(0);
        final BaseEquipmentDefinition shortsword = catalogShortswordDefinition();
        assertEquals(0, resolver.peerMaximumEquivalentNormalTier(inventory, shortsword, 99));
    }

    @Test
    void peerMaximumEquivalentNormalTier_armorAndAmmoPeers_areIgnored() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        placeSlot(inventory, 0, ItemType.ARMOR, BASE_ITEM_ID_CATARINA_HELM + 4);
        placeSlot(inventory, 1, ItemType.WEAPON, BASE_ITEM_ID_STANDARD_ARROW);
        final BaseEquipmentDefinition shortsword = catalogShortswordDefinition();
        assertEquals(0, resolver.peerMaximumEquivalentNormalTier(inventory, shortsword, 99));
    }

    @Test
    void peerMaximumEquivalentArmorTier_emptyInventory_returnsZero() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        final BaseEquipmentDefinition catarinaHelm = catalogCatarinaHelmDefinition();
        assertEquals(0, resolver.peerMaximumEquivalentArmorTier(inventory, catarinaHelm, 0));
    }

    @Test
    void peerMaximumEquivalentArmorTier_uniqueAndStandardArmorPeers_returnsMaximumTier() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        placeSlot(inventory, 0, ItemType.ARMOR, BASE_ITEM_ID_CATARINA_ARMOR_CHEST + 3);
        placeSlot(inventory, 1, ItemType.ARMOR, BASE_ITEM_ID_BRIGAND_HOOD + 10);
        final BaseEquipmentDefinition catarinaHelm = catalogCatarinaHelmDefinition();
        assertEquals(5, resolver.peerMaximumEquivalentArmorTier(inventory, catarinaHelm, 99));
    }

    @Test
    void peerMaximumEquivalentArmorTier_weaponPeer_isIgnored() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        placeSlot(inventory, 0, ItemType.WEAPON, BASE_ITEM_ID_GHOST_BLADE + 5);
        final BaseEquipmentDefinition catarinaHelm = catalogCatarinaHelmDefinition();
        assertEquals(0, resolver.peerMaximumEquivalentArmorTier(inventory, catarinaHelm, 99));
    }

    @Test
    void peerMaximumEquivalentArmorTier_excludesGivenSlot() {
        final InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
        placeSlot(inventory, 4, ItemType.ARMOR, BASE_ITEM_ID_CATARINA_HELM + 5);
        final BaseEquipmentDefinition catarinaHelm = catalogCatarinaHelmDefinition();
        assertEquals(0, resolver.peerMaximumEquivalentArmorTier(inventory, catarinaHelm, 4));
    }

    private static InventorySlot slot(
            final ItemType type,
            final int id,
            final int count,
            final int valid
    ) {
        final InventorySlot inventorySlot = new InventorySlot();
        inventorySlot.setType(type);
        inventorySlot.setId(id);
        inventorySlot.setCount(count);
        inventorySlot.setValid(valid);
        return inventorySlot;
    }

    private static InventorySlot blankSlot() {
        return new InventorySlot();
    }

    @Test
    void detectEquipChange_bothInvalid_returnsNone() {
        assertEquals(EquipChangeKind.NONE, resolver.detectEquipChange(blankSlot(), blankSlot()));
    }

    @Test
    void detectEquipChange_unequip_returnsNone() {
        final InventorySlot previous = slot(ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD, 1, 1);
        final InventorySlot current = slot(ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD, 1, 0);
        assertEquals(EquipChangeKind.NONE, resolver.detectEquipChange(previous, current));
    }

    @ParameterizedTest(name = "first {0} pickup → {2}")
    @CsvSource({
        "WEAPON,200000,EQUIP_WEAPON",
        "ARMOR,10000,EQUIP_ARMOR",
        "RING,100000,EQUIP_RING"
    })
    void detectEquipChange_firstEquip_byType_returnsExpectedKind(
            final ItemType type,
            final int id,
            final EquipChangeKind expectedKind
    ) {
        final InventorySlot current = slot(type, id, 1, 1);
        assertEquals(expectedKind, resolver.detectEquipChange(blankSlot(), current));
    }

    @Test
    void detectEquipChange_firstEquipKnownSpell_returnsEquipSpell() {
        final InventorySlot current = slot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, 30, 1);
        assertEquals(EquipChangeKind.EQUIP_SPELL, resolver.detectEquipChange(blankSlot(), current));
    }

    @Test
    void detectEquipChange_firstEquipUnknownConsumable_returnsNone() {
        final InventorySlot current = slot(ItemType.CONSUMABLE, BASE_ITEM_ID_UNKNOWN_CONSUMABLE, 1, 1);
        assertEquals(EquipChangeKind.NONE, resolver.detectEquipChange(blankSlot(), current));
    }

    @Test
    void detectEquipChange_firstEquipUnknownType_returnsNone() {
        final InventorySlot current = slot(ItemType.NONE, BASE_ITEM_ID_SHORTSWORD, 1, 1);
        assertEquals(EquipChangeKind.NONE, resolver.detectEquipChange(blankSlot(), current));
    }

    @Test
    void detectEquipChange_sameSpellIdAndCountChanged_returnsEquipSpell() {
        final InventorySlot previous = slot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, 30, 1);
        final InventorySlot current = slot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, 29, 1);
        assertEquals(EquipChangeKind.EQUIP_SPELL, resolver.detectEquipChange(previous, current));
    }

    @Test
    void detectEquipChange_sameSpellIdAndCountUnchanged_returnsNone() {
        final InventorySlot previous = slot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, 30, 1);
        final InventorySlot current = slot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, 30, 1);
        assertEquals(EquipChangeKind.NONE, resolver.detectEquipChange(previous, current));
    }

    @Test
    void detectEquipChange_sameIdNonSpellAcrossUpdate_returnsNone() {
        final InventorySlot previous = slot(ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD, 1, 1);
        final InventorySlot current = slot(ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD, 1, 1);
        assertEquals(EquipChangeKind.NONE, resolver.detectEquipChange(previous, current));
    }

    @Test
    void detectEquipChange_sameIdUnknownConsumableCountChanged_returnsNone() {
        final InventorySlot previous = slot(ItemType.CONSUMABLE, BASE_ITEM_ID_UNKNOWN_CONSUMABLE, 1, 1);
        final InventorySlot current = slot(ItemType.CONSUMABLE, BASE_ITEM_ID_UNKNOWN_CONSUMABLE, 2, 1);
        assertEquals(EquipChangeKind.NONE, resolver.detectEquipChange(previous, current));
    }

    @Test
    void detectEquipChange_differentIdsBothValid_returnsNone() {
        final InventorySlot previous = slot(ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD, 1, 1);
        final InventorySlot current = slot(ItemType.WEAPON, BASE_ITEM_ID_LONGSWORD, 1, 1);
        assertEquals(EquipChangeKind.NONE, resolver.detectEquipChange(previous, current));
    }

    @Test
    void detectEquipChange_firstEquipArmorWithCatarinaHelmId_returnsEquipArmor() {
        final InventorySlot current = slot(ItemType.ARMOR, BASE_ITEM_ID_CATARINA_HELM, 1, 1);
        assertEquals(EquipChangeKind.EQUIP_ARMOR, resolver.detectEquipChange(blankSlot(), current));
    }

    @Test
    void detectEquipChange_firstEquipRingWithAnyId_returnsEquipRing() {
        final InventorySlot current = slot(ItemType.RING, BASE_ITEM_ID_RING_ANY, 1, 1);
        assertEquals(EquipChangeKind.EQUIP_RING, resolver.detectEquipChange(blankSlot(), current));
    }

    @ParameterizedTest(name = "shouldAutoUpgradeEquippedPiece({1}, {2}) == {3}")
    @MethodSource("shouldAutoUpgradeCases")
    void shouldAutoUpgradeEquippedPiece_byChangeAndCategory(
            final String description,
            final BaseEquipmentDefinition definition,
            final EquipChangeKind equipChangeKind,
            final boolean expected
    ) {
        assertEquals(expected, resolver.shouldAutoUpgradeEquippedPiece(definition, equipChangeKind));
    }

    static Stream<Arguments> shouldAutoUpgradeCases() {
        final BaseEquipmentDefinition armorPiece = catalogCatarinaHelmDefinition();
        final BaseEquipmentDefinition meleeWeapon = catalogShortswordDefinition();
        final BaseEquipmentDefinition shield =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_HEATER_SHIELD,
                        1,
                        ItemUpgradePath.INFUSABLE_RESTRICTED,
                        EquipmentCategory.SHIELD,
                        "Heater Shield");
        final BaseEquipmentDefinition bow =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_SHORT_BOW,
                        1,
                        ItemUpgradePath.INFUSABLE,
                        EquipmentCategory.RANGED_WEAPON,
                        "Short Bow");
        final BaseEquipmentDefinition arrowStack =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_STANDARD_ARROW,
                        999,
                        ItemUpgradePath.NONE,
                        EquipmentCategory.RANGED_WEAPON,
                        "Standard Arrow");
        final BaseEquipmentDefinition pyroFlame =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_PYRO_FLAME,
                        1,
                        ItemUpgradePath.PYRO_FLAME,
                        EquipmentCategory.SPELL_TOOL,
                        "Pyromancy Flame");
        final BaseEquipmentDefinition talisman =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_TALISMAN,
                        1,
                        ItemUpgradePath.NONE,
                        EquipmentCategory.SPELL_TOOL,
                        "Talisman");
        final BaseEquipmentDefinition spell =
                new BaseEquipmentDefinition(
                        BASE_ITEM_ID_SOUL_ARROW_SPELL,
                        1,
                        ItemUpgradePath.NONE,
                        EquipmentCategory.SPELL,
                        "Soul Arrow");

        return Stream.of(
                Arguments.of("EQUIP_ARMOR + ARMOR", armorPiece, EquipChangeKind.EQUIP_ARMOR, true),
                Arguments.of("EQUIP_ARMOR + melee", meleeWeapon, EquipChangeKind.EQUIP_ARMOR, false),
                Arguments.of("EQUIP_WEAPON + melee", meleeWeapon, EquipChangeKind.EQUIP_WEAPON, true),
                Arguments.of("EQUIP_WEAPON + shield", shield, EquipChangeKind.EQUIP_WEAPON, true),
                Arguments.of("EQUIP_WEAPON + bow", bow, EquipChangeKind.EQUIP_WEAPON, true),
                Arguments.of("EQUIP_WEAPON + arrow stack", arrowStack, EquipChangeKind.EQUIP_WEAPON, false),
                Arguments.of("EQUIP_WEAPON + pyro flame", pyroFlame, EquipChangeKind.EQUIP_WEAPON, true),
                Arguments.of("EQUIP_WEAPON + talisman", talisman, EquipChangeKind.EQUIP_WEAPON, false),
                Arguments.of("EQUIP_WEAPON + armor", armorPiece, EquipChangeKind.EQUIP_WEAPON, false),
                Arguments.of("EQUIP_WEAPON + spell", spell, EquipChangeKind.EQUIP_WEAPON, false),
                Arguments.of("EQUIP_RING + melee", meleeWeapon, EquipChangeKind.EQUIP_RING, false),
                Arguments.of("EQUIP_SPELL + spell", spell, EquipChangeKind.EQUIP_SPELL, false),
                Arguments.of("NONE + armor", armorPiece, EquipChangeKind.NONE, false));
    }

    private static BaseEquipmentDefinition catalogShortswordDefinition() {
        return new BaseEquipmentDefinition(
                BASE_ITEM_ID_SHORTSWORD,
                1,
                ItemUpgradePath.INFUSABLE,
                EquipmentCategory.MELEE_WEAPON,
                "Shortsword");
    }

    private static BaseEquipmentDefinition catalogCatarinaHelmDefinition() {
        return new BaseEquipmentDefinition(
                BASE_ITEM_ID_CATARINA_HELM,
                1,
                ItemUpgradePath.UNIQUE,
                EquipmentCategory.ARMOR,
                "Catarina Helm");
    }

    private static void placeSlot(
            final InventorySlot[] inventorySlots,
            final int inventorySlotIndex,
            final ItemType type,
            final int id
    ) {
        final InventorySlot inventorySlot = inventorySlots[inventorySlotIndex];
        inventorySlot.setType(type);
        inventorySlot.setId(id);
        inventorySlot.setCount(1);
        inventorySlot.setValid(1);
    }
}
