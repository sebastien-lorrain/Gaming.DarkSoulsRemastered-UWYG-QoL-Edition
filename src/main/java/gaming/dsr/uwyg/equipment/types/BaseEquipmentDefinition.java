package gaming.dsr.uwyg.equipment.types;

import gaming.dsr.uwyg.equipment.types.enums.EquipmentCategory;
import gaming.dsr.uwyg.equipment.types.enums.ItemUpgradePath;

public record BaseEquipmentDefinition(
        int baseItemId,
        int stackLimit,
        ItemUpgradePath upgradePath,
        EquipmentCategory category,
        String displayName
) {}
