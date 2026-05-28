package gaming.dsr.uwyg.equipment;

import gaming.dsr.uwyg.equipment.definition.EquipmentDefinitionTables;
import gaming.dsr.uwyg.equipment.definition.SpellDefinitions;
import gaming.dsr.uwyg.equipment.equivalence.ArmorUpgradeEquivalence;
import gaming.dsr.uwyg.equipment.equivalence.WeaponUpgradeEquivalence;
import gaming.dsr.uwyg.equipment.types.BaseEquipmentDefinition;
import gaming.dsr.uwyg.equipment.types.InventorySlot;
import gaming.dsr.uwyg.equipment.types.enums.*;
import gaming.dsr.uwyg.game.GameAddresses;
import gaming.dsr.uwyg.game.data.DataOffsets;
import gaming.dsr.uwyg.session.UwygSession;
import gaming.dsr.uwyg.windows.ProcessBinding;
import gaming.dsr.uwyg.windows.Win32GameMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.OptionalInt;

import static gaming.dsr.uwyg.game.data.DataOffsets.*;

@Component
public final class EquipmentAutoUpgrader {
    private static final Logger LOGGER = LoggerFactory.getLogger(EquipmentAutoUpgrader.class);

    private record EquipMemoryTargets(long itemIdAddress, long slotIndexAddress) {}

    private final Win32GameMemory gameMemory;
    private final EquipmentClassifier catalog;

    public EquipmentAutoUpgrader(
            final Win32GameMemory gameMemory,
            final EquipmentClassifier catalogResolver
    ) {
        this.gameMemory = gameMemory;
        this.catalog = catalogResolver;
    }

    public void apply(
            final UwygSession session,
            final EquipChangeKind equipChangeKind,
            final int inventorySlotIndex
    ) {
        final ProcessBinding gameProcess = session.getProcess();
        final InventorySlot currentInventorySlot = session.getInventoryCopy()[inventorySlotIndex];

        if (equipChangeKind == EquipChangeKind.EQUIP_SPELL) {
            ensureCharacterHasAttunementAttributeAtLeastOf10(session);
            writeSpellToFirstAttunementSlot(session, currentInventorySlot);
            return;
        }
        if (equipChangeKind == EquipChangeKind.EQUIP_WEAPON && isArrowOrBoltPickup(currentInventorySlot)) {
            return;
        }
        if (equipChangeKind == EquipChangeKind.EQUIP_RING && isManualEquipRing(currentInventorySlot)) {
            LOGGER.info("Skipping auto-equip for manual-equip ring");
            return;
        }
        if (equipChangeKind == EquipChangeKind.EQUIP_ARMOR || equipChangeKind == EquipChangeKind.EQUIP_WEAPON) {
            maybeAutoUpgradeToPeerEquivalentTier(session, equipChangeKind, inventorySlotIndex);
        }

        resolveEquipMemoryTargets(session, equipChangeKind, currentInventorySlot)
                .ifPresent(
                        equipMemoryTargets ->
                                writeEquipToMemory(
                                        gameProcess, equipMemoryTargets, currentInventorySlot.getId(), inventorySlotIndex));
    }

    private boolean isArrowOrBoltPickup(final InventorySlot inventorySlot) {
        final WeaponType weaponSlotKind = catalog.weaponTypeFromId(inventorySlot.getId(), inventorySlot.getType());
        return weaponSlotKind == WeaponType.ARROW || weaponSlotKind == WeaponType.BOLT;
    }

    /**
     * Rings the player must equip themselves manually as part of UWYG rules.
     */
    private static boolean isManualEquipRing(final InventorySlot inventorySlot) {
        return isManualEquipRingCatalogKey(
                EquipmentClassifier.catalogKeyFromInventoryId(inventorySlot.getId(), inventorySlot.getType()));
    }

    private static boolean isManualEquipRingCatalogKey(final int catalogKey) {
        return catalogKey == EquipmentDefinitionTables.BASE_ITEM_ID_RING_COVENANT_OF_ARTORIAS
                || catalogKey == EquipmentDefinitionTables.BASE_ITEM_ID_RING_ORANGE_CHARRED;
    }

    private Optional<EquipMemoryTargets> resolveEquipMemoryTargets(
            final UwygSession session,
            final EquipChangeKind equipChangeKind,
            final InventorySlot inventorySlot
    ) {
        final GameAddresses memoryAddresses = session.getAddresses();
        return switch (equipChangeKind) {
            case EQUIP_ARMOR -> resolveArmorEquipTargets(memoryAddresses, inventorySlot);
            case EQUIP_WEAPON -> Optional.of(resolveWeaponEquipTargets(memoryAddresses, inventorySlot));
            case EQUIP_RING -> Optional.of(resolveRingEquipTargets(session, memoryAddresses));
            case EQUIP_SPELL, NONE -> Optional.empty();
        };
    }

    private void ensureCharacterHasAttunementAttributeAtLeastOf10(final UwygSession session) {

        final Long playerGameData = session.getPlayerGameData();
        if (playerGameData == null) {
            LOGGER.warn("Player game data unavailable; cannot raise attunement attribute");
            return;
        }

        final Long dataContainer = gameMemory.readUInt64(session.getProcess(), playerGameData + DataOffsets.DATA_CONTAINER_OFFSET);
        if (dataContainer == null || dataContainer == 0L) {
            LOGGER.warn("Attributes block pointer unresolved");
            return;
        }

        final long attunementAttributeAddress = dataContainer + DataOffsets.ATTRIBUTE_ATTUNEMENT_OFFSET;
        final Integer currentAttunementAttribute = gameMemory.readUInt32(session.getProcess(), attunementAttributeAddress);
        if (currentAttunementAttribute == null) {
            LOGGER.warn("Failed to read current attunement attribute; skipping raise");
            return;
        }
        if (Integer.toUnsignedLong(currentAttunementAttribute) >= 10L) {
            return;
        }

        if (!gameMemory.writeUInt32(session.getProcess(), attunementAttributeAddress, 10)) {
            LOGGER.error("Failed to raise attunement attribute to 10");
        } else {
            LOGGER.info(
                    "Raised attunement attribute from {} to 10",
                    Integer.toUnsignedString(currentAttunementAttribute));
        }
    }

    private void writeSpellToFirstAttunementSlot(
            final UwygSession session,
            final InventorySlot inventorySlot
    ) {

        final Long playerGameData = session.getPlayerGameData();
        if (playerGameData == null) {
            LOGGER.warn("Player game data unavailable; cannot attune spell");
            return;
        }

        final Long dataContainer = gameMemory.readUInt64(session.getProcess(), playerGameData + DataOffsets.DATA_CONTAINER_OFFSET);
        if (dataContainer == null || dataContainer == 0L) {
            LOGGER.warn("Magic data container pointer unresolved");
            return;
        }

        final Long magicBlock = gameMemory.readUInt64(session.getProcess(), dataContainer + DataOffsets.MAGIC_DATA_BLOCK_OFFSET);
        if (magicBlock == null || magicBlock == 0L) {
            LOGGER.warn("Magic/attunement block pointer unresolved");
            return;
        }

        final long spellIdAddress = magicBlock + DataOffsets.ATTUNEMENT_SLOT_1_SPELL_ID_OSSET;
        final int catalogSpellId = EquipmentClassifier.catalogKeyFromInventoryId(inventorySlot.getId(), inventorySlot.getType());
        final int spellUseCount = SpellDefinitions.useCountForBaseItemId(catalogSpellId);
        final long usesField = (long) spellUseCount * 3L;
        final long attunementPayload = (usesField << 32) | Integer.toUnsignedLong(catalogSpellId);

        if (!gameMemory.writeUInt64(session.getProcess(), spellIdAddress, attunementPayload)) {
            LOGGER.error("Failed to write attunement slot 1");
        } else {
            LOGGER.info(
                    "Attunement slot 1 set to spell id {} with {} uses",
                    Integer.toUnsignedString(catalogSpellId),
                    spellUseCount);
        }

        final long selectedAttunementSlotAddress = magicBlock + DataOffsets.SELECTED_ATTUNEMENT_SLOT_OFFSET;
        if (!gameMemory.writeUInt32(session.getProcess(), selectedAttunementSlotAddress, 0)) {
            LOGGER.error("Failed to select attunement slot 1");
        }
    }

    private Optional<EquipMemoryTargets> resolveArmorEquipTargets(
            final GameAddresses memoryAddresses,
            final InventorySlot inventorySlot
    ) {
        final ArmorType armorPieceKind = catalog.armorTypeFromId(inventorySlot.getId(), inventorySlot.getType());
        if (armorPieceKind == ArmorType.UNKNOWN) {
            LOGGER.warn("Unknown armor type, no action executed");
            return Optional.empty();
        }
        logArmorEquip(armorPieceKind);
        final long armorSlotByteOffset = 4L * armorPieceKind.ordinal();
        return Optional.of(
                new EquipMemoryTargets(
                        memoryAddresses.getArmorId() + armorSlotByteOffset,
                        memoryAddresses.getArmorSlot() + armorSlotByteOffset));
    }

    private void logArmorEquip(final ArmorType armorPieceKind) {
        switch (armorPieceKind) {
            case HEAD -> LOGGER.info("Equip helmet");
            case HANDS -> LOGGER.info("Equip gloves");
            case CHEST -> LOGGER.info("Equip chest armor");
            case LEGS -> LOGGER.info("Equip pants");
            default -> {
            }
        }
    }

    private EquipMemoryTargets resolveWeaponEquipTargets(
            final GameAddresses memoryAddresses,
            final InventorySlot inventorySlot
    ) {
        final long weaponHandByteOffset = weaponHandByteOffsetForSlot(inventorySlot);
        return new EquipMemoryTargets(
                memoryAddresses.getWeaponId() + weaponHandByteOffset,
                memoryAddresses.getWeaponSlot() + weaponHandByteOffset);
    }

    private long weaponHandByteOffsetForSlot(final InventorySlot inventorySlot) {
        if (isSkullLantern(inventorySlot)) {
            LOGGER.info("Equip Skull Lantern in secondary left hand weapon slot");
            return SECONDARY_LEFT_HAND_WEAPON_OFFSET;
        }
        final WeaponType weaponSlotKind = catalog.weaponTypeFromId(inventorySlot.getId(), inventorySlot.getType());
        return switch (weaponSlotKind) {
            case RIGHT_HAND -> {
                LOGGER.info("Equip right hand weapon");
                yield PRIMARY_RIGHT_HAND_WEAPON_OFFSET;
            }
            case LEFT_HAND -> {
                LOGGER.info("Equip left hand weapon");
                yield PRIMARY_LEFT_HAND_WEAPON_OFFSET;
            }
            default -> PRIMARY_LEFT_HAND_WEAPON_OFFSET;
        };
    }

    private static boolean isSkullLantern(final InventorySlot inventorySlot) {
        return EquipmentClassifier.catalogKeyFromInventoryId(inventorySlot.getId(), inventorySlot.getType())
                == EquipmentDefinitionTables.BASE_ITEM_ID_SHIELD_SKULL_LANTERN;
    }

    private EquipMemoryTargets resolveRingEquipTargets(
            final UwygSession session,
            final GameAddresses memoryAddresses
    ) {
        final boolean leftSlotReservedForManualRing = isLeftRingSlotReservedForManualEquip(session, memoryAddresses);
        final boolean useRightRingSlot = leftSlotReservedForManualRing || session.getRingAlternationSlot() == 1;
        final long ringSlotByteOffset = useRightRingSlot ? PRIMARY_RIGHT_RING_OFFSET : PRIMARY_LEFT_RING_OFFSET;

        if (useRightRingSlot) {
            session.setRingAlternationSlot(leftSlotReservedForManualRing ? 1 : 0);
            LOGGER.info("Equip ring in right slot");
        } else {
            session.setRingAlternationSlot(1);
            LOGGER.info("Equip ring in left slot");
        }

        return new EquipMemoryTargets(
                memoryAddresses.getRingId() + ringSlotByteOffset,
                memoryAddresses.getRingSlot() + ringSlotByteOffset);
    }

    /**
     * Left ring slot holds a manual-equip UWYG ring; auto-equip must use the right slot only.
     */
    private boolean isLeftRingSlotReservedForManualEquip(
            final UwygSession session,
            final GameAddresses memoryAddresses
    ) {
        final Integer leftRingMemoryId = gameMemory.readUInt32(session.getProcess(), memoryAddresses.getRingId() + PRIMARY_LEFT_RING_OFFSET);

        if (leftRingMemoryId == null || leftRingMemoryId == 0) {
            return false;
        }

        return isManualEquipRingCatalogKey(
                EquipmentClassifier.catalogKeyFromInventoryId(leftRingMemoryId, ItemType.RING));
    }

    private void writeEquipToMemory(
            final ProcessBinding gameProcess,
            final EquipMemoryTargets equipMemoryTargets,
            final int itemId,
            final int inventorySlotIndex
    ) {
        final boolean writeSucceeded =
                gameMemory.writeUInt32(gameProcess, equipMemoryTargets.itemIdAddress(), itemId)
                        && gameMemory.writeUInt32(gameProcess, equipMemoryTargets.slotIndexAddress(), inventorySlotIndex);
        if (!writeSucceeded) {
            LOGGER.error("Failed to equip item :*(");
        }
    }

    /**
     * Raises upgrade level (+n on the item path) on the looted slot to match the best equivalent tier already present among
     * peers (melee weapons, shields, bows, and reinforcement-eligible spell tools share one group; armor matches armor only),
     * without exceeding this item's own maximum for its upgrade path.
     */
    private void maybeAutoUpgradeToPeerEquivalentTier(
            final UwygSession session,
            final EquipChangeKind equipChangeKind,
            final int inventorySlotIndex
    ) {
        final InventorySlot inventorySlot = session.getInventoryCopy()[inventorySlotIndex];
        final Optional<BaseEquipmentDefinition> catalogDefinitionOptional =
                EquipmentClassifier.resolveToDefinition(inventorySlot.getId(), inventorySlot.getType());
        if (catalogDefinitionOptional.isEmpty()) {
            return;
        }
        final BaseEquipmentDefinition catalogDefinition = catalogDefinitionOptional.get();
        if (!catalog.shouldAutoUpgradeEquippedPiece(catalogDefinition, equipChangeKind)) {
            return;
        }

        final OptionalInt upgradedMemoryItemIdOptional =
                resolvePeerEquivalentUpgradedItemId(session, inventorySlotIndex, inventorySlot, catalogDefinition);
        if (upgradedMemoryItemIdOptional.isEmpty()) {
            return;
        }
        final int upgradedMemoryItemId = upgradedMemoryItemIdOptional.getAsInt();
        if (upgradedMemoryItemId == inventorySlot.getId()) {
            return;
        }
        final boolean inventoryWriteSucceeded =
                gameMemory.writeInventorySlot(
                        session.getProcess(),
                        session.getAddresses().getInventoryBase(),
                        inventorySlotIndex,
                        new Win32GameMemory.SlotWrite(
                                inventorySlot.getType(),
                                upgradedMemoryItemId,
                                inventorySlot.getCount(),
                                inventorySlot.getMysteriousNumber(),
                                inventorySlot.getValid(),
                                inventorySlot.getDurability(),
                                inventorySlot.getHits()));
        if (inventoryWriteSucceeded) {
            inventorySlot.setId(upgradedMemoryItemId);
        }
    }

    private OptionalInt resolvePeerEquivalentUpgradedItemId(
            final UwygSession session,
            final int inventorySlotIndex,
            final InventorySlot inventorySlot,
            final BaseEquipmentDefinition catalogDefinition
    ) {
        if (catalogDefinition.category() == EquipmentCategory.ARMOR) {
            return resolveArmorPeerEquivalentUpgradedItemId(
                    session, inventorySlotIndex, inventorySlot, catalogDefinition);
        }
        if (WeaponUpgradeEquivalence.shouldApplyWeaponEquivalenceRules(catalogDefinition)) {
            return resolveWeaponPeerEquivalentUpgradedItemId(
                    session, inventorySlotIndex, inventorySlot, catalogDefinition);
        }
        return OptionalInt.empty();
    }

    private OptionalInt resolveArmorPeerEquivalentUpgradedItemId(
            final UwygSession session,
            final int inventorySlotIndex,
            final InventorySlot inventorySlot,
            final BaseEquipmentDefinition catalogDefinition
    ) {
        final int peerMaximumEquivalentArmorTier =
                catalog.peerMaximumEquivalentArmorTier(
                        session.getInventoryCopy(), catalogDefinition, inventorySlotIndex);
        final int definitionMaximumEquivalentArmorTier =
                ArmorUpgradeEquivalence.maximumEquivalentArmorTier(catalogDefinition);
        final int targetEquivalentArmorTier =
                Math.min(peerMaximumEquivalentArmorTier, definitionMaximumEquivalentArmorTier);
        final int currentUpgradeLevel =
                EquipmentClassifier.decodeUpgradeLevel(
                        inventorySlot.getId(), inventorySlot.getType(), catalogDefinition);
        final int currentEquivalentArmorTier =
                ArmorUpgradeEquivalence.equivalentArmorTier(catalogDefinition, currentUpgradeLevel);
        if (targetEquivalentArmorTier <= currentEquivalentArmorTier) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(
                EquipmentClassifier.encodeArmorMemoryIdForEquivalentTier(
                        catalogDefinition, targetEquivalentArmorTier, inventorySlot.getType()));
    }

    private OptionalInt resolveWeaponPeerEquivalentUpgradedItemId(
            final UwygSession session,
            final int inventorySlotIndex,
            final InventorySlot inventorySlot,
            final BaseEquipmentDefinition catalogDefinition
    ) {
        final int peerMaximumEquivalentNormalTier =
                catalog.peerMaximumEquivalentNormalTier(
                        session.getInventoryCopy(), catalogDefinition, inventorySlotIndex);
        final int targetEquivalentNormalTier = Math.min(peerMaximumEquivalentNormalTier, 15);
        final Optional<WeaponUpgradeDecode> lootWeaponUpgradeDecode =
                EquipmentClassifier.parseWeaponUpgradeState(
                        inventorySlot.getId(), inventorySlot.getType(), catalogDefinition);
        final int currentEquivalentNormalTier =
                lootWeaponUpgradeDecode
                        .map(weaponUpgradeDecode -> WeaponUpgradeEquivalence.equivalentNormalTier(catalogDefinition, weaponUpgradeDecode))
                        .orElse(0);
        if (targetEquivalentNormalTier <= currentEquivalentNormalTier) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(
                EquipmentClassifier.encodeWeaponMemoryIdMatchingPeerEquivalent(
                        catalogDefinition,
                        targetEquivalentNormalTier,
                        inventorySlot.getType(),
                        lootWeaponUpgradeDecode));
    }
}
