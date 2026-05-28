package gaming.dsr.uwyg.equipment.types;

import gaming.dsr.uwyg.equipment.types.enums.ItemType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class InventorySlot {

    private ItemType type = ItemType.NONE;
    private int id;
    private int count;
    private int mysteriousNumber;
    private int valid;
    private int durability;
    private int hits;

    /** Deep copy suitable for snapshots. */
    public InventorySlot copy() {
        final InventorySlot copiedInventorySlot = new InventorySlot();
        copiedInventorySlot.type = type;
        copiedInventorySlot.id = id;
        copiedInventorySlot.count = count;
        copiedInventorySlot.mysteriousNumber = mysteriousNumber;
        copiedInventorySlot.valid = valid;
        copiedInventorySlot.durability = durability;
        copiedInventorySlot.hits = hits;
        return copiedInventorySlot;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof final InventorySlot otherInventorySlot)) {
            return false;
        }
        return type == otherInventorySlot.type
                && id == otherInventorySlot.id
                && count == otherInventorySlot.count
                && mysteriousNumber == otherInventorySlot.mysteriousNumber
                && valid == otherInventorySlot.valid
                && durability == otherInventorySlot.durability
                && hits == otherInventorySlot.hits;
    }

    @Override
    public int hashCode() {
        return type.hashCode() ^ id ^ count ^ mysteriousNumber ^ valid ^ durability ^ hits;
    }
}
