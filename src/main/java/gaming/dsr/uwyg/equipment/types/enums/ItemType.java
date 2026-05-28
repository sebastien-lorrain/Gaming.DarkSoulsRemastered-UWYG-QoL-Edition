package gaming.dsr.uwyg.equipment.types.enums;

import lombok.Getter;

@Getter
public enum ItemType {
    WEAPON(0x00000000),
    ARMOR(0x10000000),
    RING(0x20000000),
    CONSUMABLE(0x40000000),
    NONE(0xFFFFFFFF);

    private final int value;

    ItemType(final int value) {
        this.value = value;
    }

    public static ItemType fromRaw(final int raw) {
        for (final ItemType type : values()) {
            if (type.value == raw) {
                return type;
            }
        }
        return NONE;
    }
}
