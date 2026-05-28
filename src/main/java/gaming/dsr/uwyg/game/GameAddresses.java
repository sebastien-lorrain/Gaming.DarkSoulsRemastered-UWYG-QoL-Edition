package gaming.dsr.uwyg.game;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class GameAddresses {

    private long inventoryBase;
    private long armorId;
    private long armorSlot;
    private long weaponId;
    private long weaponSlot;
    private long ringId;
    private long ringSlot;
    private long inGame;

    public static GameAddresses fromInventorySignature(final long signatureAddress) {

        final GameAddresses mappedAddresses = new GameAddresses();
        mappedAddresses.inventoryBase = signatureAddress + 16;
        mappedAddresses.armorId = signatureAddress - 0x32CL;
        mappedAddresses.weaponId = signatureAddress - 0x34CL;
        mappedAddresses.armorSlot = signatureAddress - 0x3ACL;
        mappedAddresses.weaponSlot = signatureAddress - 0x3CCL;
        mappedAddresses.ringSlot = signatureAddress - 0x398L;
        mappedAddresses.ringId = signatureAddress - 0x318L;
        mappedAddresses.inGame = signatureAddress - 0x660L;
        return mappedAddresses;
    }
}
