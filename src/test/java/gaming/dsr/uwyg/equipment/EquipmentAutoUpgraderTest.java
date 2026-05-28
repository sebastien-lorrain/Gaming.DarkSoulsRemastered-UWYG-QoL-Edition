package gaming.dsr.uwyg.equipment;

import gaming.dsr.uwyg.equipment.types.InventorySlot;
import gaming.dsr.uwyg.equipment.types.enums.EquipChangeKind;
import gaming.dsr.uwyg.equipment.types.enums.ItemType;
import gaming.dsr.uwyg.game.GameAddresses;
import gaming.dsr.uwyg.game.data.DataOffsets;
import gaming.dsr.uwyg.inventory.InventorySnapshotReader;
import gaming.dsr.uwyg.session.UwygSession;
import gaming.dsr.uwyg.windows.Win32GameMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipmentAutoUpgraderTest {

    private static final int SLOT_INDEX = 7;

    private static final long INVENTORY_BASE_ADDRESS = 0x1000_0000_0000L;
    private static final long ARMOR_ID_BASE_ADDRESS = 0x2000_0000_0000L;
    private static final long ARMOR_SLOT_BASE_ADDRESS = 0x2100_0000_0000L;
    private static final long WEAPON_ID_BASE_ADDRESS = 0x2200_0000_0000L;
    private static final long WEAPON_SLOT_BASE_ADDRESS = 0x2300_0000_0000L;
    private static final long RING_ID_BASE_ADDRESS = 0x2400_0000_0000L;
    private static final long RING_SLOT_BASE_ADDRESS = 0x2500_0000_0000L;

    private static final long PLAYER_GAME_DATA_POINTER = 0x100L;
    private static final long DATA_CONTAINER_POINTER = 0x200L;
    private static final long MAGIC_DATA_BLOCK_POINTER = 0x300L;
    private static final long ATTUNEMENT_ATTRIBUTE_ADDRESS = 0x248L;

    private static final int BASE_ITEM_ID_SHORTSWORD = 200000;
    private static final int BASE_ITEM_ID_CATARINA_HELM = 10000;
    private static final int BASE_ITEM_ID_SOUL_ARROW_SPELL = 3000;
    private static final int SPELL_USE_COUNT_SOUL_ARROW = 30;
    private static final int RING_ITEM_ID_ANY = 100000;
    private static final int BASE_ITEM_ID_RING_HAVELS = 100;
    private static final int BASE_ITEM_ID_RING_COVENANT_OF_ARTORIAS = 138;
    private static final int BASE_ITEM_ID_RING_ORANGE_CHARRED = 139;
    private static final int ITEM_TYPE_VALUE_RING_MASK = 0x20000000;
    private static final int BASE_ITEM_ID_STANDARD_ARROW = 2000000;
    private static final int BASE_ITEM_ID_STANDARD_BOLT = 2100000;
    private static final int BASE_ITEM_ID_SHIELD_SKULL_LANTERN = 1396000;
    private static final long SECONDARY_LEFT_HAND_WEAPON_OFFSET = 0x08L;

    @Mock private Win32GameMemory gameMemory;

    @Spy private EquipmentClassifier catalog = new EquipmentClassifier();

    @InjectMocks private EquipmentAutoUpgrader upgrader;

    private UwygSession session;

    @BeforeEach
    void setUpSession() {
        session = new UwygSession();
        session.getProcess().setProcessId(1234);
        session.setInventoryCopy(InventorySnapshotReader.newBlankSlots());

        final GameAddresses addresses = new GameAddresses();
        addresses.setInventoryBase(INVENTORY_BASE_ADDRESS);
        addresses.setArmorId(ARMOR_ID_BASE_ADDRESS);
        addresses.setArmorSlot(ARMOR_SLOT_BASE_ADDRESS);
        addresses.setWeaponId(WEAPON_ID_BASE_ADDRESS);
        addresses.setWeaponSlot(WEAPON_SLOT_BASE_ADDRESS);
        addresses.setRingId(RING_ID_BASE_ADDRESS);
        addresses.setRingSlot(RING_SLOT_BASE_ADDRESS);
        session.setAddresses(addresses);
    }

    private void placeSlot(
            final ItemType type,
            final int id,
            final int count
    ) {
        final InventorySlot inventorySlot = session.getInventoryCopy()[SLOT_INDEX];
        inventorySlot.setType(type);
        inventorySlot.setId(id);
        inventorySlot.setCount(count);
        inventorySlot.setValid(1);
    }

    @Test
    void apply_none_writesNothing() {
        placeSlot(ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD, 1);
        upgrader.apply(session, EquipChangeKind.NONE, SLOT_INDEX);
        verifyNoInteractions(gameMemory);
    }

    @ParameterizedTest(name = "EQUIP_WEAPON on {0} ammo stack does nothing")
    @ValueSource(ints = {BASE_ITEM_ID_STANDARD_ARROW, BASE_ITEM_ID_STANDARD_BOLT})
    void apply_equipWeapon_arrowOrBoltPickup_writesNothing(
            final int ammoBaseItemId
    ) {
        placeSlot(ItemType.WEAPON, ammoBaseItemId, 999);
        upgrader.apply(session, EquipChangeKind.EQUIP_WEAPON, SLOT_INDEX);
        verifyNoInteractions(gameMemory);
    }

    @Test
    void apply_equipWeapon_peerStrongerThanCurrent_rewritesInventoryAndEquipsUpgradedId() {
        placeSlot(ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD, 1);
        doReturn(5).when(catalog).peerMaximumEquivalentNormalTier(any(), any(), eq(SLOT_INDEX));
        when(gameMemory.writeInventorySlot(any(), anyLong(), anyInt(), any())).thenReturn(true);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_WEAPON, SLOT_INDEX);

        final int expectedUpgradedItemId = BASE_ITEM_ID_SHORTSWORD + 5;
        final ArgumentCaptor<Win32GameMemory.SlotWrite> slotWriteCaptor =
                ArgumentCaptor.forClass(Win32GameMemory.SlotWrite.class);
        verify(gameMemory)
                .writeInventorySlot(
                        any(), eq(INVENTORY_BASE_ADDRESS), eq(SLOT_INDEX), slotWriteCaptor.capture());
        assertEquals(ItemType.WEAPON, slotWriteCaptor.getValue().type());
        assertEquals(expectedUpgradedItemId, slotWriteCaptor.getValue().id());

        assertEquals(expectedUpgradedItemId, session.getInventoryCopy()[SLOT_INDEX].getId());

        verify(gameMemory).writeUInt32(any(), eq(WEAPON_ID_BASE_ADDRESS + 4L), eq(expectedUpgradedItemId));
        verify(gameMemory).writeUInt32(any(), eq(WEAPON_SLOT_BASE_ADDRESS + 4L), eq(SLOT_INDEX));
    }

    @Test
    void apply_equipWeapon_skullLantern_writesToSecondaryLeftHandWeaponSlot() {
        placeSlot(ItemType.WEAPON, BASE_ITEM_ID_SHIELD_SKULL_LANTERN, 1);
        doReturn(0).when(catalog).peerMaximumEquivalentNormalTier(any(), any(), eq(SLOT_INDEX));
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_WEAPON, SLOT_INDEX);

        verify(gameMemory, never()).writeInventorySlot(any(), anyLong(), anyInt(), any());
        verify(gameMemory)
                .writeUInt32(
                        any(),
                        eq(WEAPON_ID_BASE_ADDRESS + SECONDARY_LEFT_HAND_WEAPON_OFFSET),
                        eq(BASE_ITEM_ID_SHIELD_SKULL_LANTERN));
        verify(gameMemory)
                .writeUInt32(
                        any(),
                        eq(WEAPON_SLOT_BASE_ADDRESS + SECONDARY_LEFT_HAND_WEAPON_OFFSET),
                        eq(SLOT_INDEX));
    }

    @Test
    void apply_equipWeapon_peerNotStrongerThanCurrent_onlyWritesEquipMemory() {
        placeSlot(ItemType.WEAPON, BASE_ITEM_ID_SHORTSWORD, 1);
        doReturn(0).when(catalog).peerMaximumEquivalentNormalTier(any(), any(), eq(SLOT_INDEX));
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_WEAPON, SLOT_INDEX);

        verify(gameMemory, never()).writeInventorySlot(any(), anyLong(), anyInt(), any());
        verify(gameMemory).writeUInt32(any(), eq(WEAPON_ID_BASE_ADDRESS + 4L), eq(BASE_ITEM_ID_SHORTSWORD));
        verify(gameMemory).writeUInt32(any(), eq(WEAPON_SLOT_BASE_ADDRESS + 4L), eq(SLOT_INDEX));
    }

    @Test
    void apply_equipArmor_peerStrongerThanCurrent_rewritesInventoryAndEquipsUpgradedId() {
        placeSlot(ItemType.ARMOR, BASE_ITEM_ID_CATARINA_HELM, 1);
        doReturn(3).when(catalog).peerMaximumEquivalentArmorTier(any(), any(), eq(SLOT_INDEX));
        when(gameMemory.writeInventorySlot(any(), anyLong(), anyInt(), any())).thenReturn(true);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_ARMOR, SLOT_INDEX);

        final int expectedUpgradedItemId = BASE_ITEM_ID_CATARINA_HELM + 3;
        final ArgumentCaptor<Win32GameMemory.SlotWrite> slotWriteCaptor =
                ArgumentCaptor.forClass(Win32GameMemory.SlotWrite.class);
        verify(gameMemory)
                .writeInventorySlot(
                        any(), eq(INVENTORY_BASE_ADDRESS), eq(SLOT_INDEX), slotWriteCaptor.capture());
        assertEquals(ItemType.ARMOR, slotWriteCaptor.getValue().type());
        assertEquals(expectedUpgradedItemId, slotWriteCaptor.getValue().id());

        assertEquals(expectedUpgradedItemId, session.getInventoryCopy()[SLOT_INDEX].getId());

        verify(gameMemory).writeUInt32(any(), eq(ARMOR_ID_BASE_ADDRESS), eq(expectedUpgradedItemId));
        verify(gameMemory).writeUInt32(any(), eq(ARMOR_SLOT_BASE_ADDRESS), eq(SLOT_INDEX));
    }

    @Test
    void apply_equipArmor_peerNotStrongerThanCurrent_onlyWritesEquipMemory() {
        placeSlot(ItemType.ARMOR, BASE_ITEM_ID_CATARINA_HELM, 1);
        doReturn(0).when(catalog).peerMaximumEquivalentArmorTier(any(), any(), eq(SLOT_INDEX));
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_ARMOR, SLOT_INDEX);

        verify(gameMemory, never()).writeInventorySlot(any(), anyLong(), anyInt(), any());
        verify(gameMemory).writeUInt32(any(), eq(ARMOR_ID_BASE_ADDRESS), eq(BASE_ITEM_ID_CATARINA_HELM));
        verify(gameMemory).writeUInt32(any(), eq(ARMOR_SLOT_BASE_ADDRESS), eq(SLOT_INDEX));
    }

    @Test
    void apply_equipRing_alternatesBetweenLeftAndRightSlots() {
        placeSlot(ItemType.RING, RING_ITEM_ID_ANY, 1);
        when(gameMemory.readUInt32(any(), eq(RING_ID_BASE_ADDRESS))).thenReturn(0);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_RING, SLOT_INDEX);
        verify(gameMemory).writeUInt32(any(), eq(RING_ID_BASE_ADDRESS), eq(RING_ITEM_ID_ANY));
        verify(gameMemory).writeUInt32(any(), eq(RING_SLOT_BASE_ADDRESS), eq(SLOT_INDEX));
        assertEquals(1, session.getRingAlternationSlot());

        upgrader.apply(session, EquipChangeKind.EQUIP_RING, SLOT_INDEX);
        verify(gameMemory)
                .writeUInt32(any(), eq(RING_ID_BASE_ADDRESS + DataOffsets.PRIMARY_RIGHT_RING_OFFSET), eq(RING_ITEM_ID_ANY));
        verify(gameMemory)
                .writeUInt32(any(), eq(RING_SLOT_BASE_ADDRESS + DataOffsets.PRIMARY_RIGHT_RING_OFFSET), eq(SLOT_INDEX));
        assertEquals(0, session.getRingAlternationSlot());
    }

    @ParameterizedTest(name = "EQUIP_RING on manual-equip ring {0} writes nothing")
    @ValueSource(ints = {BASE_ITEM_ID_RING_COVENANT_OF_ARTORIAS, BASE_ITEM_ID_RING_ORANGE_CHARRED})
    void apply_equipRing_manualEquipRing_writesNothing(
            final int manualEquipRingId
    ) {
        placeSlot(ItemType.RING, manualEquipRingId, 1);
        upgrader.apply(session, EquipChangeKind.EQUIP_RING, SLOT_INDEX);
        verifyNoInteractions(gameMemory);
        assertEquals(0, session.getRingAlternationSlot());
    }

    @Test
    void apply_equipRing_autoEquipRing_autoEquipsAndAdvancesAlternationSlot() {
        placeSlot(ItemType.RING, BASE_ITEM_ID_RING_HAVELS, 1);
        when(gameMemory.readUInt32(any(), eq(RING_ID_BASE_ADDRESS))).thenReturn(0);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_RING, SLOT_INDEX);

        verify(gameMemory).writeUInt32(any(), eq(RING_ID_BASE_ADDRESS), eq(BASE_ITEM_ID_RING_HAVELS));
        verify(gameMemory).writeUInt32(any(), eq(RING_SLOT_BASE_ADDRESS), eq(SLOT_INDEX));
        assertEquals(1, session.getRingAlternationSlot());
    }

    @ParameterizedTest(name = "left slot manual ring {0} forces right slot auto-equip")
    @ValueSource(ints = {BASE_ITEM_ID_RING_COVENANT_OF_ARTORIAS, BASE_ITEM_ID_RING_ORANGE_CHARRED})
    void apply_equipRing_leftSlotHasManualRing_autoEquipsToRightSlot(
            final int manualRingInLeftSlot
    ) {
        session.setRingAlternationSlot(0);
        placeSlot(ItemType.RING, BASE_ITEM_ID_RING_HAVELS, 1);
        when(gameMemory.readUInt32(any(), eq(RING_ID_BASE_ADDRESS))).thenReturn(manualRingInLeftSlot);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_RING, SLOT_INDEX);

        verify(gameMemory, never()).writeUInt32(any(), eq(RING_ID_BASE_ADDRESS), anyInt());
        verify(gameMemory, never()).writeUInt32(any(), eq(RING_SLOT_BASE_ADDRESS), anyInt());
        verify(gameMemory)
                .writeUInt32(
                        any(),
                        eq(RING_ID_BASE_ADDRESS + DataOffsets.PRIMARY_RIGHT_RING_OFFSET),
                        eq(BASE_ITEM_ID_RING_HAVELS));
        verify(gameMemory)
                .writeUInt32(
                        any(), eq(RING_SLOT_BASE_ADDRESS + DataOffsets.PRIMARY_RIGHT_RING_OFFSET), eq(SLOT_INDEX));
        assertEquals(1, session.getRingAlternationSlot());
    }

    @Test
    void apply_equipRing_leftSlotHasManualRingWithMask_autoEquipsToRightSlot() {
        session.setRingAlternationSlot(0);
        placeSlot(ItemType.RING, BASE_ITEM_ID_RING_HAVELS, 1);
        when(gameMemory.readUInt32(any(), eq(RING_ID_BASE_ADDRESS)))
                .thenReturn(ITEM_TYPE_VALUE_RING_MASK | BASE_ITEM_ID_RING_COVENANT_OF_ARTORIAS);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_RING, SLOT_INDEX);

        verify(gameMemory)
                .writeUInt32(
                        any(),
                        eq(RING_ID_BASE_ADDRESS + DataOffsets.PRIMARY_RIGHT_RING_OFFSET),
                        eq(BASE_ITEM_ID_RING_HAVELS));
        assertEquals(1, session.getRingAlternationSlot());
    }

    @Test
    void apply_equipRing_leftSlotHasManualRing_repeatedAutoEquipsStayOnRightSlot() {
        session.setRingAlternationSlot(0);
        placeSlot(ItemType.RING, BASE_ITEM_ID_RING_HAVELS, 1);
        when(gameMemory.readUInt32(any(), eq(RING_ID_BASE_ADDRESS)))
                .thenReturn(BASE_ITEM_ID_RING_ORANGE_CHARRED);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_RING, SLOT_INDEX);
        upgrader.apply(session, EquipChangeKind.EQUIP_RING, SLOT_INDEX);

        verify(gameMemory, times(2))
                .writeUInt32(any(), eq(RING_ID_BASE_ADDRESS + DataOffsets.PRIMARY_RIGHT_RING_OFFSET), anyInt());
        verify(gameMemory, never()).writeUInt32(any(), eq(RING_ID_BASE_ADDRESS), anyInt());
        assertEquals(1, session.getRingAlternationSlot());
    }

    @ParameterizedTest(name = "EQUIP_RING on manual-equip ring {0} (with RING mask applied) writes nothing")
    @ValueSource(ints = {BASE_ITEM_ID_RING_COVENANT_OF_ARTORIAS, BASE_ITEM_ID_RING_ORANGE_CHARRED})
    void apply_equipRing_manualEquipRingWithMask_writesNothing(
            final int manualEquipRingId
    ) {
        placeSlot(ItemType.RING, ITEM_TYPE_VALUE_RING_MASK | manualEquipRingId, 1);
        upgrader.apply(session, EquipChangeKind.EQUIP_RING, SLOT_INDEX);
        verifyNoInteractions(gameMemory);
    }

    @Test
    void apply_equipSpell_writesAttunementPayloadAndSelectsFirstSlot() {
        placeSlot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, SPELL_USE_COUNT_SOUL_ARROW);
        session.setPlayerGameData(PLAYER_GAME_DATA_POINTER);

        when(gameMemory.readUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS))).thenReturn(50);
        when(gameMemory.readUInt64(any(), eq(PLAYER_GAME_DATA_POINTER + 0x10L)))
                .thenReturn(DATA_CONTAINER_POINTER);
        when(gameMemory.readUInt64(any(), eq(DATA_CONTAINER_POINTER + 0x418L)))
                .thenReturn(MAGIC_DATA_BLOCK_POINTER);
        when(gameMemory.writeUInt64(any(), anyLong(), anyLong())).thenReturn(true);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_SPELL, SLOT_INDEX);

        final long expectedUsesField = (long) SPELL_USE_COUNT_SOUL_ARROW * 3L;
        final long expectedAttunementPayload =
                (expectedUsesField << 32) | Integer.toUnsignedLong(BASE_ITEM_ID_SOUL_ARROW_SPELL);
        verify(gameMemory)
                .writeUInt64(
                        any(), eq(MAGIC_DATA_BLOCK_POINTER + 0x18L), eq(expectedAttunementPayload));
        verify(gameMemory).writeUInt32(any(), eq(MAGIC_DATA_BLOCK_POINTER + 0x78L), eq(0));
        verify(gameMemory, never()).writeUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS), anyInt());
        verify(gameMemory, never()).writeInventorySlot(any(), anyLong(), anyInt(), any());
    }

    @Test
    void apply_equipSpell_attunementBelowTen_raisesAttunementToTenBeforeAttuningSpell() {
        placeSlot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, SPELL_USE_COUNT_SOUL_ARROW);
        session.setPlayerGameData(PLAYER_GAME_DATA_POINTER);

        when(gameMemory.readUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS))).thenReturn(3);
        when(gameMemory.readUInt64(any(), eq(PLAYER_GAME_DATA_POINTER + 0x10L)))
                .thenReturn(DATA_CONTAINER_POINTER);
        when(gameMemory.readUInt64(any(), eq(DATA_CONTAINER_POINTER + 0x418L)))
                .thenReturn(MAGIC_DATA_BLOCK_POINTER);
        when(gameMemory.writeUInt64(any(), anyLong(), anyLong())).thenReturn(true);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_SPELL, SLOT_INDEX);

        final InOrder inOrder = inOrder(gameMemory);
        inOrder.verify(gameMemory).writeUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS), eq(10));
        inOrder.verify(gameMemory).writeUInt64(any(), eq(MAGIC_DATA_BLOCK_POINTER + 0x18L), anyLong());
    }

    @Test
    void apply_equipSpell_attunementExactlyTen_skipsAttunementRaise() {
        placeSlot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, SPELL_USE_COUNT_SOUL_ARROW);
        session.setPlayerGameData(PLAYER_GAME_DATA_POINTER);

        when(gameMemory.readUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS))).thenReturn(10);
        when(gameMemory.readUInt64(any(), eq(PLAYER_GAME_DATA_POINTER + 0x10L)))
                .thenReturn(DATA_CONTAINER_POINTER);
        when(gameMemory.readUInt64(any(), eq(DATA_CONTAINER_POINTER + 0x418L)))
                .thenReturn(MAGIC_DATA_BLOCK_POINTER);
        when(gameMemory.writeUInt64(any(), anyLong(), anyLong())).thenReturn(true);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_SPELL, SLOT_INDEX);

        verify(gameMemory, never()).writeUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS), anyInt());
        verify(gameMemory).writeUInt64(any(), eq(MAGIC_DATA_BLOCK_POINTER + 0x18L), anyLong());
    }

    @Test
    void apply_equipSpell_attunementWellAboveTen_skipsAttunementRaise() {
        placeSlot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, SPELL_USE_COUNT_SOUL_ARROW);
        session.setPlayerGameData(PLAYER_GAME_DATA_POINTER);

        when(gameMemory.readUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS))).thenReturn(99);
        when(gameMemory.readUInt64(any(), eq(PLAYER_GAME_DATA_POINTER + 0x10L)))
                .thenReturn(DATA_CONTAINER_POINTER);
        when(gameMemory.readUInt64(any(), eq(DATA_CONTAINER_POINTER + 0x418L)))
                .thenReturn(MAGIC_DATA_BLOCK_POINTER);
        when(gameMemory.writeUInt64(any(), anyLong(), anyLong())).thenReturn(true);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_SPELL, SLOT_INDEX);

        verify(gameMemory, never()).writeUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS), anyInt());
    }

    @Test
    void apply_equipSpell_attunementReadFails_skipsAttunementRaiseButContinuesSpellFlow() {
        placeSlot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, SPELL_USE_COUNT_SOUL_ARROW);
        session.setPlayerGameData(PLAYER_GAME_DATA_POINTER);

        when(gameMemory.readUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS))).thenReturn(null);
        when(gameMemory.readUInt64(any(), eq(PLAYER_GAME_DATA_POINTER + 0x10L)))
                .thenReturn(DATA_CONTAINER_POINTER);
        when(gameMemory.readUInt64(any(), eq(DATA_CONTAINER_POINTER + 0x418L)))
                .thenReturn(MAGIC_DATA_BLOCK_POINTER);
        when(gameMemory.writeUInt64(any(), anyLong(), anyLong())).thenReturn(true);
        when(gameMemory.writeUInt32(any(), anyLong(), anyInt())).thenReturn(true);

        upgrader.apply(session, EquipChangeKind.EQUIP_SPELL, SLOT_INDEX);

        verify(gameMemory, never()).writeUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS), anyInt());
        verify(gameMemory).writeUInt64(any(), eq(MAGIC_DATA_BLOCK_POINTER + 0x18L), anyLong());
        verify(gameMemory).writeUInt32(any(), eq(MAGIC_DATA_BLOCK_POINTER + 0x78L), eq(0));
    }

    @Test
    void apply_equipSpell_missingPlayerGameData_writesNothing() {
        placeSlot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, SPELL_USE_COUNT_SOUL_ARROW);
        session.setPlayerGameData(null);

        upgrader.apply(session, EquipChangeKind.EQUIP_SPELL, SLOT_INDEX);

        verifyNoInteractions(gameMemory);
    }

    @Test
    void apply_equipSpell_unresolvedMagicContainer_writesNothingFurther() {
        placeSlot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, SPELL_USE_COUNT_SOUL_ARROW);
        session.setPlayerGameData(PLAYER_GAME_DATA_POINTER);
        when(gameMemory.readUInt64(any(), eq(PLAYER_GAME_DATA_POINTER + 0x10L))).thenReturn(0L);

        upgrader.apply(session, EquipChangeKind.EQUIP_SPELL, SLOT_INDEX);

        verify(gameMemory, times(2)).readUInt64(any(), anyLong());
        verify(gameMemory, never()).readUInt32(any(), anyLong());
        verify(gameMemory, never()).writeUInt64(any(), anyLong(), anyLong());
        verify(gameMemory, never()).writeUInt32(any(), anyLong(), anyInt());
    }

    @Test
    void apply_equipSpell_unresolvedMagicBlock_writesNothingFurther() {
        placeSlot(ItemType.CONSUMABLE, BASE_ITEM_ID_SOUL_ARROW_SPELL, SPELL_USE_COUNT_SOUL_ARROW);
        session.setPlayerGameData(PLAYER_GAME_DATA_POINTER);
        when(gameMemory.readUInt32(any(), eq(ATTUNEMENT_ATTRIBUTE_ADDRESS))).thenReturn(50);
        when(gameMemory.readUInt64(any(), eq(PLAYER_GAME_DATA_POINTER + 0x10L)))
                .thenReturn(DATA_CONTAINER_POINTER);
        when(gameMemory.readUInt64(any(), eq(DATA_CONTAINER_POINTER + 0x418L)))
                .thenReturn(null);

        upgrader.apply(session, EquipChangeKind.EQUIP_SPELL, SLOT_INDEX);

        verify(gameMemory, times(3)).readUInt64(any(), anyLong());
        verify(gameMemory, never()).writeUInt64(any(), anyLong(), anyLong());
        verify(gameMemory, never()).writeUInt32(any(), anyLong(), anyInt());
    }
}
