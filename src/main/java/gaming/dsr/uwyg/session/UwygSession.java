package gaming.dsr.uwyg.session;

import gaming.dsr.uwyg.equipment.types.InventorySlot;
import gaming.dsr.uwyg.game.GameAddresses;
import gaming.dsr.uwyg.inventory.InventorySnapshotReader;
import gaming.dsr.uwyg.windows.ProcessBinding;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class UwygSession {

    public enum Phase {
        FINDING_GAME,
        FINDING_INV,
        MAIN_MENU,
        INV_START,
        INV_UPDATE
    }

    private Phase phase = Phase.FINDING_GAME;
    private final ProcessBinding process = new ProcessBinding();
    private GameAddresses addresses;
    private InventorySlot[] inventory = InventorySnapshotReader.newBlankSlots();
    private InventorySlot[] inventoryCopy = InventorySnapshotReader.newBlankSlots();
    private int ringAlternationSlot;

    private Long playerGameData;
    private Long worldProgressionFlags;
    private Integer lastObservedDeathCount;

    public void clearDeathCountTracking() {
        playerGameData = null;
        worldProgressionFlags = null;
        lastObservedDeathCount = null;
    }

    public void resetInventorySnapshots() {
        setInventory(InventorySnapshotReader.newBlankSlots());
        setInventoryCopy(InventorySnapshotReader.newBlankSlots());
    }
}
