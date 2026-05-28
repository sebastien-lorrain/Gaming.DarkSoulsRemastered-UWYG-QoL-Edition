package gaming.dsr.uwyg.equipment;

import gaming.dsr.uwyg.equipment.definition.EquipmentDefinitionTables;
import gaming.dsr.uwyg.equipment.equivalence.ArmorUpgradeEquivalence;
import gaming.dsr.uwyg.equipment.equivalence.WeaponUpgradeEquivalence;
import gaming.dsr.uwyg.equipment.types.BaseEquipmentDefinition;
import gaming.dsr.uwyg.equipment.types.InventorySlot;
import gaming.dsr.uwyg.equipment.types.enums.*;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Catalog lookups use ids <strong>without</strong> {@link ItemType} masks (see {@link #catalogKeyFromInventoryId}).
 * Memory may store either {@code mask + catalogKey} or catalog-only ids with {@link ItemType} in the slot.
 */
@SuppressWarnings("ALL")
@Component
public final class EquipmentClassifier {

    private static final EquipmentCategory[] WEAPON_TABLE_CATEGORIES = {
        EquipmentCategory.RANGED_WEAPON,
        EquipmentCategory.MELEE_WEAPON,
        EquipmentCategory.SHIELD,
        EquipmentCategory.SPELL_TOOL
    };

    /**
     * Catalog row id as in {@link EquipmentDefinitionTables}. If the memory id includes this slot's
     * {@link ItemType} mask bits, strips them; otherwise returns the id unchanged (catalog-only encoding).
     */
    public static int catalogKeyFromInventoryId(
            final int rawMemoryId,
            final ItemType slotItemType
    ) {
        if (slotItemType == null || slotItemType == ItemType.NONE) {
            return rawMemoryId;
        }
        final int mask = slotItemType.getValue();
        if (mask == 0) {
            return rawMemoryId;
        }
        if ((rawMemoryId & mask) == mask) {
            return rawMemoryId - mask;
        }
        return rawMemoryId;
    }

    /**
     * Memory id for the game: mirrors {@link #catalogKeyFromInventoryId} — use bare catalog keys when
     * they fit below the type mask (inventory stores type separately); otherwise add the mask.
     */
    public static int inventoryIdFromCatalogKey(
            final int catalogKey,
            final ItemType slotItemType
    ) {
        if (slotItemType == null || slotItemType == ItemType.NONE) {
            return catalogKey;
        }
        final int mask = slotItemType.getValue();
        if (mask == 0) {
            return catalogKey;
        }
        if (Integer.compareUnsigned(catalogKey, mask) < 0) {
            return catalogKey;
        }
        return catalogKey + mask;
    }

    public ArmorType armorTypeFromId(
            final int armorMemoryId,
            final ItemType slotItemType
    ) {
        final int catalogKey = catalogKeyFromInventoryId(armorMemoryId, slotItemType);
        final long unsigned = Integer.toUnsignedLong(catalogKey);
        final long type = (unsigned % 10000L) / 1000L;
        if (type < 4) {
            return switch ((int) type) {
                case 0 -> ArmorType.HEAD;
                case 1 -> ArmorType.CHEST;
                case 2 -> ArmorType.HANDS;
                case 3 -> ArmorType.LEGS;
                default -> ArmorType.UNKNOWN;
            };
        }
        return ArmorType.UNKNOWN;
    }

    public WeaponType weaponTypeFromId(
            final int weaponMemoryId,
            final ItemType slotItemType
    ) {
        return resolveWeaponSlotInternal(weaponMemoryId, slotItemType);
    }

    /**
     * Resolves a memory item id plus slot {@link ItemType} to a catalogue row (exact or upgraded).
     * Rings have no upgrade path, so only an exact match is attempted for {@link ItemType#RING}.
     */
    public static Optional<BaseEquipmentDefinition> resolveToDefinition(
            final int rawMemoryId,
            final ItemType slotItemType
    ) {
        if (slotItemType == ItemType.NONE) {
            return Optional.empty();
        }
        final int catalogKey = catalogKeyFromInventoryId(rawMemoryId, slotItemType);
        final Optional<BaseEquipmentDefinition> exactDefinitionOptional = resolveExact(catalogKey, slotItemType);
        if (exactDefinitionOptional.isPresent()) {
            return exactDefinitionOptional;
        }
        return resolveUpgraded(Integer.toUnsignedLong(catalogKey), slotItemType);
    }

    private static WeaponType resolveWeaponSlotInternal(
            final int rawMemoryId,
            final ItemType slotItemType
    ) {
        if (slotItemType == ItemType.CONSUMABLE) {
            return resolveToDefinition(rawMemoryId, slotItemType)
                    .map(EquipmentClassifier::weaponTypeFromDefinition)
                    .orElse(WeaponType.RIGHT_HAND);
        }
        if (slotItemType != ItemType.WEAPON) {
            return WeaponType.NONE;
        }
        return resolveToDefinition(rawMemoryId, slotItemType)
                .map(EquipmentClassifier::weaponTypeFromDefinition)
                .orElse(WeaponType.RIGHT_HAND);
    }

    /** Highest {@code upgradeLevel} for the upgrade path when infusion is unknown (NORMAL uses 15 for infusable types). */
    public static int maximumUpgradeLevel(final ItemUpgradePath upgradePath) {
        return switch (upgradePath) {
            case NONE -> 0;
            case UNIQUE, PYRO_ASCENDED -> 5;
            case STANDARD_ARMOR -> 10;
            case INFUSABLE, INFUSABLE_RESTRICTED -> WeaponInfusionPath.NORMAL.getMaxReinforcement();
            case PYRO_FLAME -> 15;
        };
    }

    /** Maximum {@code upgradeLevel} for {@code infusionPath}; empty when invalid for {@link ItemUpgradePath#INFUSABLE_RESTRICTED}. */
    public static OptionalInt maximumUpgradeLevel(
            final ItemUpgradePath upgradePath,
            final WeaponInfusionPath infusionPath
    ) {
        return switch (upgradePath) {
            case NONE -> OptionalInt.of(0);
            case UNIQUE, PYRO_ASCENDED -> OptionalInt.of(5);
            case STANDARD_ARMOR -> OptionalInt.of(10);
            case INFUSABLE -> OptionalInt.of(infusionPath.getMaxReinforcement());
            case INFUSABLE_RESTRICTED -> {
                if (infusionPath.isUnavailableForRestrictedInfusable()) {
                    yield OptionalInt.empty();
                }
                yield OptionalInt.of(infusionPath.getMaxReinforcement());
            }
            case PYRO_FLAME -> OptionalInt.of(15);
        };
    }

    public static int maximumUpgradeLevel(final BaseEquipmentDefinition equipmentDefinition) {
        return maximumUpgradeLevel(equipmentDefinition.upgradePath());
    }

    private static WeaponType weaponTypeFromDefinition(final BaseEquipmentDefinition equipmentDefinition) {
        return switch (equipmentDefinition.category()) {
            case MELEE_WEAPON ->
                    isSpecialLeftHandMeleeWeapon(equipmentDefinition) ? WeaponType.LEFT_HAND : WeaponType.RIGHT_HAND;
            case SHIELD, SPELL_TOOL -> WeaponType.LEFT_HAND;
            case RANGED_WEAPON -> rangedSlotKind(equipmentDefinition.baseItemId());
            case ARMOR, SPELL, RING -> WeaponType.NONE;
        };
    }

    private static boolean isSpecialLeftHandMeleeWeapon(final BaseEquipmentDefinition equipmentDefinition) {
        return equipmentDefinition.category() == EquipmentCategory.MELEE_WEAPON
                && equipmentDefinition.baseItemId() == EquipmentDefinitionTables.BASE_ITEM_ID_MELEE_PARRYING_DAGGER;
    }

    private static WeaponType rangedSlotKind(final int baseItemId) {
        final long baseItemIdUnsigned = Integer.toUnsignedLong(baseItemId);
        if (baseItemIdUnsigned >= 2000000L && baseItemIdUnsigned < 2100000L) {
            return WeaponType.ARROW;
        }
        if (baseItemIdUnsigned >= 2100000L && baseItemIdUnsigned < 2200000L) {
            return WeaponType.BOLT;
        }
        return WeaponType.LEFT_HAND;
    }

    private static EquipmentCategory[] categoriesForItemType(final ItemType slotItemType) {
        return switch (slotItemType) {
            case WEAPON -> WEAPON_TABLE_CATEGORIES;
            case ARMOR -> new EquipmentCategory[] {EquipmentCategory.ARMOR};
            case RING -> new EquipmentCategory[] {EquipmentCategory.RING};
            case CONSUMABLE -> new EquipmentCategory[] {EquipmentCategory.SPELL};
            default -> new EquipmentCategory[0];
        };
    }

    private static Optional<BaseEquipmentDefinition> resolveExact(
            final int catalogKey,
            final ItemType slotItemType
    ) {
        for (final EquipmentCategory equipmentCategory : categoriesForItemType(slotItemType)) {
            final Optional<BaseEquipmentDefinition> found = EquipmentDefinitionTables.find(catalogKey, equipmentCategory);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<BaseEquipmentDefinition> resolveUpgraded(
            final long catalogRawUnsigned,
            final ItemType slotItemType
    ) {
        for (final EquipmentCategory equipmentCategory : categoriesForItemType(slotItemType)) {
            for (final BaseEquipmentDefinition catalogDefinition : EquipmentDefinitionTables.byCategory(equipmentCategory).values()) {
                if (matchesUpgrade(catalogDefinition, catalogRawUnsigned)) {
                    return Optional.of(catalogDefinition);
                }
            }
        }
        return Optional.empty();
    }

    private static boolean matchesUpgrade(
            final BaseEquipmentDefinition equipmentDefinition,
            final long rawUnsigned
    ) {
        final long baseUnsigned = Integer.toUnsignedLong(equipmentDefinition.baseItemId());
        return switch (equipmentDefinition.upgradePath()) {
            case NONE -> rawUnsigned == baseUnsigned;
            case UNIQUE -> rawUnsigned >= baseUnsigned && rawUnsigned <= baseUnsigned + 5;
            case INFUSABLE, INFUSABLE_RESTRICTED -> matchesInfusable(equipmentDefinition, rawUnsigned, baseUnsigned);
            case STANDARD_ARMOR ->
                    rawUnsigned >= baseUnsigned && rawUnsigned <= baseUnsigned + 10;
            case PYRO_FLAME -> matchesPyromancyFlameUpgrade(rawUnsigned, baseUnsigned, 15);
            case PYRO_ASCENDED -> matchesPyromancyFlameUpgrade(rawUnsigned, baseUnsigned, 5);
        };
    }

    private static boolean matchesPyromancyFlameUpgrade(
            final long rawUnsigned,
            final long baseUnsigned,
            final int maxUpgradeLevelInclusive
    ) {
        if (rawUnsigned < baseUnsigned) {
            return false;
        }
        final long delta = rawUnsigned - baseUnsigned;
        if (delta % 100L != 0) {
            return false;
        }
        final long steps = delta / 100L;
        return steps >= 0 && steps <= maxUpgradeLevelInclusive;
    }

    /** Arrows and bolts (stackable ammo), not bows. */
    private static boolean isAmmunition(final BaseEquipmentDefinition equipmentDefinition) {
        if (equipmentDefinition.category() != EquipmentCategory.RANGED_WEAPON) {
            return false;
        }
        final long baseItemIdUnsigned = Integer.toUnsignedLong(equipmentDefinition.baseItemId());
        return baseItemIdUnsigned >= 2000000L && baseItemIdUnsigned < 2200000L;
    }

    private static boolean differentAutoUpgradePeerGroup(
            final BaseEquipmentDefinition firstEquipmentDefinition,
            final BaseEquipmentDefinition secondEquipmentDefinition
    ) {
        return peerKind(firstEquipmentDefinition) != peerKind(secondEquipmentDefinition);
    }

    private enum PeerKind {
        /** Melee weapons, shields, bows (not ammo), and reinforcement-eligible spell tools: shared peer group. */
        WEAPON,
        ARMOR,
        OTHER
    }

    private static PeerKind peerKind(final BaseEquipmentDefinition equipmentDefinition) {
        return switch (equipmentDefinition.category()) {
            case ARMOR -> PeerKind.ARMOR;
            case SHIELD, MELEE_WEAPON -> PeerKind.WEAPON;
            case RANGED_WEAPON -> isAmmunition(equipmentDefinition) ? PeerKind.OTHER : PeerKind.WEAPON;
            case SPELL_TOOL ->
                    WeaponUpgradeEquivalence.spellToolParticipatesInWeaponPeerGroup(equipmentDefinition)
                            ? PeerKind.WEAPON
                            : PeerKind.OTHER;
            case SPELL, RING -> PeerKind.OTHER;
        };
    }

    /**
     * Highest {@link WeaponUpgradeEquivalence#equivalentNormalTier} (+0…+15) among weapon peers — melee, shields, bows,
     * and upgradeable spell tools together. Armor uses {@link #peerMaximumEquivalentArmorTier}.
     */
    public int peerMaximumEquivalentNormalTier(
            final InventorySlot[] inventorySlots,
            final BaseEquipmentDefinition subjectEquipmentDefinition,
            final int excludeSlotIndex
    ) {
        int peerMaximumEquivalentNormalTier = 0;
        for (int inventorySlotIndex = 0; inventorySlotIndex < inventorySlots.length; inventorySlotIndex++) {
            if (inventorySlotIndex == excludeSlotIndex) {
                continue;
            }
            final InventorySlot peerSlot = inventorySlots[inventorySlotIndex];
            if (peerSlot.getValid() == 0) {
                continue;
            }
            final Optional<BaseEquipmentDefinition> peerDefinition =
                    resolveToDefinition(peerSlot.getId(), peerSlot.getType());
            if (peerDefinition.isEmpty()) {
                continue;
            }
            final BaseEquipmentDefinition peerCatalogDefinition = peerDefinition.get();
            if (differentAutoUpgradePeerGroup(subjectEquipmentDefinition, peerCatalogDefinition)) {
                continue;
            }
            if (!WeaponUpgradeEquivalence.shouldApplyWeaponEquivalenceRules(peerCatalogDefinition)) {
                continue;
            }
            final int equivalentNormalTier =
                    parseWeaponUpgradeState(peerSlot.getId(), peerSlot.getType(), peerCatalogDefinition)
                            .map(weaponUpgradeDecode -> WeaponUpgradeEquivalence.equivalentNormalTier(peerCatalogDefinition, weaponUpgradeDecode))
                            .orElse(0);
            peerMaximumEquivalentNormalTier = Math.max(peerMaximumEquivalentNormalTier, equivalentNormalTier);
        }
        return peerMaximumEquivalentNormalTier;
    }

    /**
     * Highest {@link ArmorUpgradeEquivalence#equivalentArmorTier} (+0…+5) among peer armor rows.
     */
    public int peerMaximumEquivalentArmorTier(
            final InventorySlot[] inventorySlots,
            final BaseEquipmentDefinition subjectEquipmentDefinition,
            final int excludeSlotIndex
    ) {
        int peerMaximumEquivalentArmorTier = 0;
        for (int inventorySlotIndex = 0; inventorySlotIndex < inventorySlots.length; inventorySlotIndex++) {
            if (inventorySlotIndex == excludeSlotIndex) {
                continue;
            }
            final InventorySlot peerSlot = inventorySlots[inventorySlotIndex];
            if (peerSlot.getValid() == 0) {
                continue;
            }
            final Optional<BaseEquipmentDefinition> peerDefinition =
                    resolveToDefinition(peerSlot.getId(), peerSlot.getType());
            if (peerDefinition.isEmpty()) {
                continue;
            }
            final BaseEquipmentDefinition peerCatalogDefinition = peerDefinition.get();
            if (differentAutoUpgradePeerGroup(subjectEquipmentDefinition, peerCatalogDefinition)) {
                continue;
            }
            if (peerCatalogDefinition.category() != EquipmentCategory.ARMOR) {
                continue;
            }
            final int upgradeLevel =
                    decodeUpgradeLevel(peerSlot.getId(), peerSlot.getType(), peerCatalogDefinition);
            peerMaximumEquivalentArmorTier =
                    Math.max(
                            peerMaximumEquivalentArmorTier,
                            ArmorUpgradeEquivalence.equivalentArmorTier(peerCatalogDefinition, upgradeLevel));
        }
        return peerMaximumEquivalentArmorTier;
    }

    /**
     * Memory item id for armor matching {@code targetEquivalentArmorTier} (0–5); applies {@link ItemType} masking if needed.
     */
    public static int encodeArmorMemoryIdForEquivalentTier(
            final BaseEquipmentDefinition equipmentDefinition,
            final int targetEquivalentArmorTier,
            final ItemType slotItemType
    ) {
        final int catalogId =
                ArmorUpgradeEquivalence.catalogItemIdForEquivalentArmorTier(equipmentDefinition, targetEquivalentArmorTier);
        return inventoryIdFromCatalogKey(catalogId, slotItemType);
    }

    /**
     * Parses UNIQUE {@link WeaponUpgradeDecode.Twinkling} or infusion {@link WeaponUpgradeDecode.Infused} state from the catalog id.
     */
    public static Optional<WeaponUpgradeDecode> parseWeaponUpgradeState(
            final int rawMemoryId,
            final ItemType slotItemType,
            final BaseEquipmentDefinition equipmentDefinition
    ) {
        if (!WeaponUpgradeEquivalence.shouldApplyWeaponEquivalenceRules(equipmentDefinition)) {
            return Optional.empty();
        }
        final int catalogRawKey = catalogKeyFromInventoryId(rawMemoryId, slotItemType);
        final long rawCatalogIdUnsigned = Integer.toUnsignedLong(catalogRawKey);
        final long baseItemIdUnsigned = Integer.toUnsignedLong(equipmentDefinition.baseItemId());
        return switch (equipmentDefinition.upgradePath()) {
            case UNIQUE -> {
                final long catalogOffsetFromBase = rawCatalogIdUnsigned - baseItemIdUnsigned;
                if (catalogOffsetFromBase >= 0 && catalogOffsetFromBase <= 5) {
                    yield Optional.of(new WeaponUpgradeDecode.Twinkling((int) catalogOffsetFromBase));
                }
                yield Optional.empty();
            }
            case INFUSABLE, INFUSABLE_RESTRICTED ->
                    findMatchingInfusedWeapon(equipmentDefinition, rawCatalogIdUnsigned, baseItemIdUnsigned);
            case PYRO_FLAME ->
                    Optional.of(
                            new WeaponUpgradeDecode.PyromancyFlame(
                                    decodePyromancyFlameUpgradeLevel(rawCatalogIdUnsigned, baseItemIdUnsigned, 15)));
            case PYRO_ASCENDED ->
                    Optional.of(
                            new WeaponUpgradeDecode.PyromancyFlame(
                                    decodePyromancyFlameUpgradeLevel(rawCatalogIdUnsigned, baseItemIdUnsigned, 5)));
            default -> Optional.empty();
        };
    }

    /** First matching infusion path and upgrade level (enum iteration order; NORMAL is checked early). */
    private static Optional<WeaponUpgradeDecode> findMatchingInfusedWeapon(
            final BaseEquipmentDefinition equipmentDefinition,
            final long rawUnsigned,
            final long baseUnsigned
    ) {
        final ItemUpgradePath itemUpgradePath = equipmentDefinition.upgradePath();
        for (final WeaponInfusionPath candidateInfusionPath : WeaponInfusionPath.values()) {
            final OptionalInt infusionMaximumOptional = maximumUpgradeLevel(itemUpgradePath, candidateInfusionPath);
            if (infusionMaximumOptional.isEmpty()) {
                continue;
            }
            final int infusionMaximumUpgradeLevel = infusionMaximumOptional.getAsInt();
            final int catalogByteOffset = candidateInfusionPath.getItemIdOffset();
            for (int upgradeLevel = 0;
                    upgradeLevel <= infusionMaximumUpgradeLevel;
                    upgradeLevel++) {
                if (rawUnsigned == baseUnsigned + upgradeLevel + (long) catalogByteOffset) {
                    return Optional.of(
                            new WeaponUpgradeDecode.Infused(candidateInfusionPath, upgradeLevel));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Builds the in-memory item id so the weapon matches {@code targetEquivalentNormalTier} on the shared equivalent
     * NORMAL tier scale (+0…+15), then applies {@link ItemType} masking if needed.
     *
     * <p><strong>UNIQUE</strong> (twinkling): highest {@code twinklingUpgradeLevel} whose mapped tier does not exceed
     * {@code targetEquivalentNormalTier}. <strong>INFUSABLE</strong>: if {@code lootWeaponUpgradeDecode} contains
     * {@link WeaponUpgradeDecode.Infused}, {@code upgradeLevel} on that {@code infusionPath} is raised up to that ceiling;
     * otherwise the NORMAL infusion path is used.
     */
    public static int encodeWeaponMemoryIdMatchingPeerEquivalent(
            final BaseEquipmentDefinition equipmentDefinition,
            final int targetEquivalentNormalTier,
            final ItemType slotItemType,
            final Optional<WeaponUpgradeDecode> lootWeaponUpgradeDecode
    ) {
        final int clampedTargetEquivalentNormalTier = Math.clamp(targetEquivalentNormalTier, 0, 15);
        final long baseItemIdUnsigned = Integer.toUnsignedLong(equipmentDefinition.baseItemId());
        final int catalogItemId =
                switch (equipmentDefinition.upgradePath()) {
                    case UNIQUE -> {
                        final int chosenTwinklingUpgradeLevel =
                                WeaponUpgradeEquivalence.twinklingUpgradeLevelAtOrBelowEquivalentNormalTier(
                                        clampedTargetEquivalentNormalTier);
                        yield (int) (baseItemIdUnsigned + chosenTwinklingUpgradeLevel);
                    }
                    case INFUSABLE, INFUSABLE_RESTRICTED -> {
                        if (lootWeaponUpgradeDecode.isPresent()
                                && lootWeaponUpgradeDecode.get()
                                        instanceof final WeaponUpgradeDecode.Infused infusedLootState) {
                            final ItemUpgradePath itemUpgradePath = equipmentDefinition.upgradePath();
                            final WeaponInfusionPath lootInfusionPath = infusedLootState.infusionPath();
                            final OptionalInt infusionPathMaximumUpgradeLevelOptional =
                                    maximumUpgradeLevel(itemUpgradePath, lootInfusionPath);
                            if (infusionPathMaximumUpgradeLevelOptional.isEmpty()) {
                                final int fallbackNormalPathUpgradeLevel =
                                        Math.min(clampedTargetEquivalentNormalTier, maximumUpgradeLevel(equipmentDefinition));
                                yield itemIdAtNormalUpgradeLevel(equipmentDefinition, fallbackNormalPathUpgradeLevel);
                            }
                            final int targetInfusionUpgradeLevel =
                                    WeaponUpgradeEquivalence.maximumInfusionUpgradeLevelAtOrBelowEquivalentNormalTier(
                                            lootInfusionPath,
                                            infusionPathMaximumUpgradeLevelOptional.getAsInt(),
                                            clampedTargetEquivalentNormalTier);
                            yield catalogItemIdForInfusionUpgradeLevel(
                                    equipmentDefinition, lootInfusionPath, targetInfusionUpgradeLevel);
                        }
                        final int normalPathUpgradeLevel =
                                Math.min(clampedTargetEquivalentNormalTier, maximumUpgradeLevel(equipmentDefinition));
                        yield itemIdAtNormalUpgradeLevel(equipmentDefinition, normalPathUpgradeLevel);
                    }
                    case PYRO_FLAME -> {
                        final int maxSteps = maximumUpgradeLevel(equipmentDefinition);
                        final int targetSteps = Math.min(clampedTargetEquivalentNormalTier, maxSteps);
                        yield catalogItemIdAtPyromancyFlameUpgradeLevel(equipmentDefinition, targetSteps);
                    }
                    case PYRO_ASCENDED -> {
                        final int chosenSteps =
                                WeaponUpgradeEquivalence.twinklingUpgradeLevelAtOrBelowEquivalentNormalTier(
                                        clampedTargetEquivalentNormalTier);
                        yield catalogItemIdAtPyromancyFlameUpgradeLevel(equipmentDefinition, chosenSteps);
                    }
                    default -> equipmentDefinition.baseItemId();
                };
        return inventoryIdFromCatalogKey(catalogItemId, slotItemType);
    }

    private static int catalogItemIdForInfusionUpgradeLevel(
            final BaseEquipmentDefinition equipmentDefinition,
            final WeaponInfusionPath infusionPath,
            final int upgradeLevel
    ) {
        final OptionalInt infusionMaximumOptional =
                maximumUpgradeLevel(equipmentDefinition.upgradePath(), infusionPath);
        final int infusionMaximumUpgradeLevel = infusionMaximumOptional.orElse(0);
        final int clampedUpgradeLevel =
                Math.clamp(upgradeLevel, 0, infusionMaximumUpgradeLevel);
        final long baseItemIdUnsigned = Integer.toUnsignedLong(equipmentDefinition.baseItemId());
        return (int)
                (baseItemIdUnsigned
                        + clampedUpgradeLevel
                        + (long) infusionPath.getItemIdOffset());
    }

    /** Decodes current {@code upgradeLevel} (+n) from memory id (with type prefix) and catalog definition. */
    public static int decodeUpgradeLevel(
            final int rawMemoryId,
            final ItemType slotItemType,
            final BaseEquipmentDefinition equipmentDefinition
    ) {
        final int catalogRawKey = catalogKeyFromInventoryId(rawMemoryId, slotItemType);
        final long rawCatalogIdUnsigned = Integer.toUnsignedLong(catalogRawKey);
        final long baseItemIdUnsigned = Integer.toUnsignedLong(equipmentDefinition.baseItemId());
        return switch (equipmentDefinition.upgradePath()) {
            case NONE -> 0;
            case UNIQUE -> {
                final long catalogOffsetFromBase = rawCatalogIdUnsigned - baseItemIdUnsigned;
                yield (catalogOffsetFromBase >= 0 && catalogOffsetFromBase <= 5)
                        ? (int) catalogOffsetFromBase
                        : 0;
            }
            case STANDARD_ARMOR -> {
                final long catalogOffsetFromBase = rawCatalogIdUnsigned - baseItemIdUnsigned;
                yield (catalogOffsetFromBase >= 0 && catalogOffsetFromBase <= 10)
                        ? (int) catalogOffsetFromBase
                        : 0;
            }
            case INFUSABLE, INFUSABLE_RESTRICTED ->
                    decodeInfusableUpgradeLevel(equipmentDefinition, rawCatalogIdUnsigned, baseItemIdUnsigned);
            case PYRO_FLAME -> decodePyromancyFlameUpgradeLevel(rawCatalogIdUnsigned, baseItemIdUnsigned, 15);
            case PYRO_ASCENDED -> decodePyromancyFlameUpgradeLevel(rawCatalogIdUnsigned, baseItemIdUnsigned, 5);
        };
    }

    private static int decodePyromancyFlameUpgradeLevel(
            final long rawUnsigned,
            final long baseUnsigned,
            final int maxStepsInclusive
    ) {
        final long delta = rawUnsigned - baseUnsigned;
        if (delta < 0 || delta % 100L != 0) {
            return 0;
        }
        final int steps = (int) (delta / 100L);
        return Math.clamp(steps, 0, maxStepsInclusive);
    }

    private static int decodeInfusableUpgradeLevel(
            final BaseEquipmentDefinition equipmentDefinition,
            final long rawUnsigned,
            final long baseUnsigned
    ) {
        final ItemUpgradePath itemUpgradePath = equipmentDefinition.upgradePath();
        int highestDecodedUpgradeLevel = 0;
        for (final WeaponInfusionPath candidateInfusionPath : WeaponInfusionPath.values()) {
            final OptionalInt infusionMaximumOptional = maximumUpgradeLevel(itemUpgradePath, candidateInfusionPath);
            if (infusionMaximumOptional.isEmpty()) {
                continue;
            }
            final int infusionMaximumUpgradeLevel = infusionMaximumOptional.getAsInt();
            final int catalogByteOffset = candidateInfusionPath.getItemIdOffset();
            for (int upgradeLevel = 0;
                    upgradeLevel <= infusionMaximumUpgradeLevel;
                    upgradeLevel++) {
                if (rawUnsigned == baseUnsigned + upgradeLevel + (long) catalogByteOffset) {
                    highestDecodedUpgradeLevel =
                            Math.max(highestDecodedUpgradeLevel, upgradeLevel);
                }
            }
        }
        return highestDecodedUpgradeLevel;
    }

    /**
     * Catalog-space item id for {@code normalPathUpgradeLevel} on the NORMAL infusion path; combine with
     * {@link #inventoryIdFromCatalogKey(int, ItemType)} before writing memory.
     */
    public static int itemIdAtNormalUpgradeLevel(
            final BaseEquipmentDefinition equipmentDefinition,
            final int normalPathUpgradeLevel
    ) {
        final int catalogMaximumUpgradeLevel = maximumUpgradeLevel(equipmentDefinition);
        final int clampedNormalPathUpgradeLevel =
                Math.clamp(normalPathUpgradeLevel, 0, catalogMaximumUpgradeLevel);
        final long baseItemIdUnsigned = Integer.toUnsignedLong(equipmentDefinition.baseItemId());
        return switch (equipmentDefinition.upgradePath()) {
            case NONE -> equipmentDefinition.baseItemId();
            case UNIQUE, STANDARD_ARMOR -> (int) (baseItemIdUnsigned + clampedNormalPathUpgradeLevel);
            case INFUSABLE, INFUSABLE_RESTRICTED -> {
                final OptionalInt normalInfusionMaximumOptional =
                        maximumUpgradeLevel(equipmentDefinition.upgradePath(), WeaponInfusionPath.NORMAL);
                final int normalInfusionMaximumUpgradeLevel = normalInfusionMaximumOptional.orElse(0);
                final int appliedNormalPathUpgradeLevel =
                        Math.min(clampedNormalPathUpgradeLevel, normalInfusionMaximumUpgradeLevel);
                yield (int)
                        (baseItemIdUnsigned
                                + appliedNormalPathUpgradeLevel
                                + (long) WeaponInfusionPath.NORMAL.getItemIdOffset());
            }
            case PYRO_FLAME, PYRO_ASCENDED ->
                    catalogItemIdAtPyromancyFlameUpgradeLevel(equipmentDefinition, clampedNormalPathUpgradeLevel);
        };
    }

    private static int catalogItemIdAtPyromancyFlameUpgradeLevel(
            final BaseEquipmentDefinition equipmentDefinition,
            final int upgradeSteps
    ) {
        final long baseUnsigned = Integer.toUnsignedLong(equipmentDefinition.baseItemId());
        return switch (equipmentDefinition.upgradePath()) {
            case PYRO_FLAME, PYRO_ASCENDED -> {
                final int clampedUpgradeSteps = Math.clamp(upgradeSteps, 0, maximumUpgradeLevel(equipmentDefinition));
                yield (int) (baseUnsigned + (long) clampedUpgradeSteps * 100L);
            }
            default -> equipmentDefinition.baseItemId();
        };
    }

    public EquipChangeKind detectEquipChange(
            final InventorySlot previousInventorySlot,
            final InventorySlot currentInventorySlot
    ) {

        if (previousInventorySlot.getValid() != 0 && currentInventorySlot.getValid() == 0) {
            return EquipChangeKind.NONE;
        }

        if (previousInventorySlot.getValid() == 0 && currentInventorySlot.getValid() != 0) {
            if (currentInventorySlot.getType() == ItemType.ARMOR) {
                return EquipChangeKind.EQUIP_ARMOR;
            }
            if (currentInventorySlot.getType() == ItemType.WEAPON) {
                return EquipChangeKind.EQUIP_WEAPON;
            }
            if (currentInventorySlot.getType() == ItemType.RING) {
                return EquipChangeKind.EQUIP_RING;
            }
            if (isSpell(currentInventorySlot)) {
                return EquipChangeKind.EQUIP_SPELL;
            }
            return EquipChangeKind.NONE;
        }

        if (currentInventorySlot.getId() == previousInventorySlot.getId()) {
            if (isSpell(currentInventorySlot)
                    && currentInventorySlot.getCount() != previousInventorySlot.getCount()) {
                return EquipChangeKind.EQUIP_SPELL;
            }
            return EquipChangeKind.NONE;
        }

        return EquipChangeKind.NONE;
    }

    private static boolean isSpell(final InventorySlot inventorySlot) {
        if (inventorySlot.getType() != ItemType.CONSUMABLE) {
            return false;
        }
        return resolveToDefinition(inventorySlot.getId(), inventorySlot.getType())
                .map(definition -> definition.category() == EquipmentCategory.SPELL)
                .orElse(false);
    }

    public boolean shouldAutoUpgradeEquippedPiece(
            final BaseEquipmentDefinition equipmentDefinition,
            final EquipChangeKind equipChangeKind
    ) {
        if (equipChangeKind == EquipChangeKind.EQUIP_ARMOR) {
            return equipmentDefinition.category() == EquipmentCategory.ARMOR;
        }
        if (equipChangeKind == EquipChangeKind.EQUIP_WEAPON) {
            return switch (equipmentDefinition.category()) {
                case MELEE_WEAPON, SHIELD -> true;
                case RANGED_WEAPON -> !isAmmunition(equipmentDefinition);
                case SPELL_TOOL -> WeaponUpgradeEquivalence.spellToolParticipatesInWeaponPeerGroup(equipmentDefinition);
                case ARMOR, SPELL, RING -> false;
            };
        }
        return false;
    }

    private static boolean matchesInfusable(
            final BaseEquipmentDefinition equipmentDefinition,
            final long rawUnsigned,
            final long baseUnsigned
    ) {

        final ItemUpgradePath itemUpgradePath = equipmentDefinition.upgradePath();
        for (final WeaponInfusionPath candidateInfusionPath : WeaponInfusionPath.values()) {

            final OptionalInt infusionMaximumOptional = maximumUpgradeLevel(itemUpgradePath, candidateInfusionPath);
            if (infusionMaximumOptional.isEmpty()) {
                continue;
            }

            final int infusionMaximumUpgradeLevel = infusionMaximumOptional.getAsInt();
            final int catalogByteOffset = candidateInfusionPath.getItemIdOffset();
            for (int upgradeLevel = 0; upgradeLevel <= infusionMaximumUpgradeLevel; upgradeLevel++) {

                if (rawUnsigned == baseUnsigned + upgradeLevel + (long) catalogByteOffset) {
                    return true;
                }
            }
        }

        return false;
    }
}
