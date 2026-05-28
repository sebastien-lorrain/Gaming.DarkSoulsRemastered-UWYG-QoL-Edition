package gaming.dsr.uwyg.inventory;

import gaming.dsr.uwyg.equipment.types.InventorySlot;
import gaming.dsr.uwyg.equipment.types.enums.ItemType;
import gaming.dsr.uwyg.game.GameConstants;
import gaming.dsr.uwyg.windows.ProcessBinding;
import gaming.dsr.uwyg.windows.Win32GameMemory;

import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Component
public final class InventorySnapshotReader {

    private final Win32GameMemory gameMemory;

    public InventorySnapshotReader(final Win32GameMemory gameMemory) {
        this.gameMemory = gameMemory;
    }

    public boolean readInto(
            final ProcessBinding process,
            final long inventoryBase,
            final InventorySlot[] inventorySlots
    ) {

        final int bytesNeeded = GameConstants.INVENTORY_SLOTS * GameConstants.INV_SLOT_SIZE;
        final byte[] bytes = gameMemory.readMemory(process, inventoryBase, bytesNeeded);
        if (bytes == null || bytes.length != bytesNeeded) {
            return false;
        }

        final ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        for (int inventorySlotIndex = 0; inventorySlotIndex < GameConstants.INVENTORY_SLOTS; inventorySlotIndex++) {
            final InventorySlot inventorySlot = inventorySlots[inventorySlotIndex];
            inventorySlot.setType(ItemType.fromRaw(buffer.getInt()));
            inventorySlot.setId(buffer.getInt());
            inventorySlot.setCount(buffer.getInt());
            inventorySlot.setMysteriousNumber(buffer.getInt());
            inventorySlot.setValid(buffer.getInt());
            inventorySlot.setDurability(buffer.getInt());
            inventorySlot.setHits(buffer.getInt());
        }
        return true;
    }

    public static InventorySlot[] newBlankSlots() {
        final InventorySlot[] inventorySlots = new InventorySlot[GameConstants.INVENTORY_SLOTS];
        for (int inventorySlotIndex = 0; inventorySlotIndex < inventorySlots.length; inventorySlotIndex++) {
            inventorySlots[inventorySlotIndex] = new InventorySlot();
        }
        return inventorySlots;
    }

    public static InventorySlot[] copySlots(final InventorySlot[] source) {
        final InventorySlot[] inventorySlotsCopy = new InventorySlot[source.length];
        for (int inventorySlotIndex = 0; inventorySlotIndex < source.length; inventorySlotIndex++) {
            inventorySlotsCopy[inventorySlotIndex] = source[inventorySlotIndex].copy();
        }
        return inventorySlotsCopy;
    }
}
