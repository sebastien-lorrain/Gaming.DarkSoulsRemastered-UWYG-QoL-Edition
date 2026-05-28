package gaming.dsr.uwyg.equipment.definition;

import java.util.Map;

public final class SpellDefinitions {
    private static final int DEFAULT_SPELL_USE_COUNT = 0;

    private static final Map<Integer, Integer> USE_COUNT_BY_BASE_ITEM_ID = Map.<Integer, Integer>ofEntries(
            Map.entry(3000, 30), // Soul Arrow
            Map.entry(3010, 20), // Great Soul Arrow
            Map.entry(3020, 12), // Heavy Soul Arrow
            Map.entry(3030, 8), // Great Heavy Soul Arrow
            Map.entry(3040, 10), // Homing Soulmass
            Map.entry(3050, 10), // Homing Crystal Soulmass
            Map.entry(3060, 4), // Soul Spear
            Map.entry(3070, 4), // Crystal Soul Spear
            Map.entry(3100, 5), // Magic Weapon
            Map.entry(3110, 3), // Great Magic Weapon
            Map.entry(3120, 3), // Crystal Magic Weapon
            Map.entry(3300, 5), // Magic Shield
            Map.entry(3310, 3), // Strong Magic Shield
            Map.entry(3400, 3), // Hidden Weapon
            Map.entry(3410, 3), // Hidden Body
            Map.entry(3500, 3), // Cast Light
            Map.entry(3510, 6), // Hush
            Map.entry(3520, 20), // Aural Decoy
            Map.entry(3530, 1), // Repair
            Map.entry(3540, 10), // Fall Control
            Map.entry(3550, 11), // Chameleon
            Map.entry(3600, 4), // Resist Curse
            Map.entry(3610, 4), // Remedy
            Map.entry(3700, 20), // White Dragon Breath
            Map.entry(3710, 12), // Dark Orb
            Map.entry(3720, 6), // Dark Bead
            Map.entry(3730, 2), // Dark Fog
            Map.entry(3740, 2), // Pursuers
            Map.entry(4000, 8), // Fireball
            Map.entry(4010, 6), // Fire Orb
            Map.entry(4020, 4), // Great Fireball
            Map.entry(4030, 20), // Firestorm
            Map.entry(4040, 20), // Fire Tempest
            Map.entry(4050, 80), // Fire Surge
            Map.entry(4060, 80), // Fire Whip
            Map.entry(4100, 16), // Combustion
            Map.entry(4110, 8), // Great Combustion
            Map.entry(4200, 3), // Poison Mist
            Map.entry(4210, 1), // Toxic Mist
            Map.entry(4220, 2), // Acid Surge
            Map.entry(4300, 3), // Iron Flesh
            Map.entry(4310, 3), // Flash Sweat
            Map.entry(4360, 7), // Undead Rapport
            Map.entry(4400, 1), // Power Within
            Map.entry(4500, 4), // Great Chaos Fireball
            Map.entry(4510, 20), // Chaos Storm
            Map.entry(4520, 80), // Chaos Fire Whip
            Map.entry(4530, 8), // Black Flame
            Map.entry(5000, 5), // Heal
            Map.entry(5010, 3), // Great Heal
            Map.entry(5020, 1), // Great Heal Except
            Map.entry(5030, 3), // Soothing Sunlight
            Map.entry(5040, 2), // Replenishment
            Map.entry(5050, 2), // Bountiful Sunlight
            Map.entry(5100, 40), // Gravelord Sword Dance
            Map.entry(5110, 40), // Gravelord Greatsword Dance
            Map.entry(5200, 1), // Escape Death
            Map.entry(5210, 1), // Homeward
            Map.entry(5300, 21), // Force
            Map.entry(5310, 3), // Wrath of the Gods
            Map.entry(5320, 6), // Emit Force
            Map.entry(5400, 5), // Seek Guidance
            Map.entry(5500, 10), // Lightning Spear
            Map.entry(5510, 10), // Great Lightning Spear
            Map.entry(5520, 5), // Sunlight Spear
            Map.entry(5600, 4), // Magic Barrier
            Map.entry(5610, 2), // Great Magic Barrier
            Map.entry(5700, 4), // Karmic Justice
            Map.entry(5800, 5), // Tranquil Walk of Peace
            Map.entry(5810, 2), // Vow of Silence
            Map.entry(5900, 1), // Sunlight Blade
            Map.entry(5910, 1)); // Darkmoon Blade

    private SpellDefinitions() {}

    public static int useCountForBaseItemId(final int baseItemId) {
        return USE_COUNT_BY_BASE_ITEM_ID.getOrDefault(baseItemId, DEFAULT_SPELL_USE_COUNT);
    }
}
