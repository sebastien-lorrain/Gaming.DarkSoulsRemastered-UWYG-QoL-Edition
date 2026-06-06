package gaming.dsr.uwyg.game.data;

import gaming.dsr.uwyg.game.data.keyitem.Area;
import gaming.dsr.uwyg.game.data.keyitem.KeyItemLocation;
import gaming.dsr.uwyg.game.data.keyitem.LocalizedText;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KeyItemLocationCatalog {

    private static LocalizedText text(final String english, final String french) {
        return new LocalizedText(english, french);
    }

    private static Area area(final String id, final String english, final String french) {
        return new Area(id, text(english, french));
    }

    private static final Area[] AREAS = {
            area("undead_asylum", "Undead Asylum", "Refuge nord des Mort-vivants"),
            area("firelink_shrine", "Firelink Shrine", "Sanctuaire du Lige-feu"),
            area("undead_burg", "Undead Burg", "Village des Mort-vivants"),
            area("undead_parish", "Undead Parish", "Paroisse des Mort-vivants"),
            area("depths", "Depths", "Profondeurs"),
            area("blighttown", "Blighttown", "Hameau du Crépuscule"),
            area("great_hollow", "Great Hollow", "Grande Carcasse"),
            area("sens_fortress", "Sen\'s Fortress", "Forteresse de Sen"),
            area("anor_londo", "Anor Londo", "Anor Londo"),
            area("darkroot_garden", "Darkroot Garden", "Jardin de Noiresouche"),
            area("darkroot_basin", "Darkroot Basin", "Bassin de Noiresouche"),
            area("new_londo_ruins", "New Londo Ruins", "Ruines de la Nouvelle Londo"),
            area("dukes_archives", "Duke\'s Archives", "Archives du Duc"),
            area("demon_ruins", "Demon Ruins", "Ruines démoniaques"),
            area("lost_izalith", "Lost Izalith", "Izalith la Perdue"),
            area("catacombs", "Catacombs", "Catacombes"),
            area("tomb_of_the_giants", "Tomb of the Giants", "Tombeau des Géants"),
            area("painted_world", "Painted World", "Monde peint d\'Ariamis"),
            area("oolacile_sanctuary", "Oolacile Sanctuary", "Sanctuaire d\'Oolacile"),
            area("royal_wood", "Royal Wood", "Forêt royale"),
            area("oolacile_township", "Oolacile Township", "Ville d\'Oolacile"),
            area("chasm_of_the_abyss", "Chasm of the Abyss", "Gouffre des Abysses"),
            area("kiln_of_the_first_flame", "Kiln of the First Flame", "Kiln de la Première Flamme"),
    };

    private static final Map<String, Area> AREAS_BY_ID = buildAreasById();

    public static final List<String> AREA_PROGRESSION_ORDER = List.of(
            "undead_asylum", "firelink_shrine", "undead_burg", "undead_parish", "depths", "blighttown", "sens_fortress", "anor_londo", "painted_world", "great_hollow", "darkroot_garden", "darkroot_basin", "new_londo_ruins", "dukes_archives", "demon_ruins", "lost_izalith", "catacombs", "tomb_of_the_giants", "oolacile_sanctuary", "royal_wood", "oolacile_township", "chasm_of_the_abyss", "kiln_of_the_first_flame"
    );

    public static Area areaById(final String areaId) {
        final Area area = AREAS_BY_ID.get(areaId);
        if (area == null) {
            throw new IllegalArgumentException("Unknown area id: " + areaId);
        }
        return area;
    }

    private static Map<String, Area> buildAreasById() {
        final Map<String, Area> map = new LinkedHashMap<>();
        for (final Area area : AREAS) {
            map.put(area.id(), area);
        }
        return Map.copyOf(map);
    }

    private static KeyItemLocation entry(
            final String id,
            final int locationId,
            final String areaId,
            final LocalizedText label
    ) {
        return new KeyItemLocation(id, locationId, areaId, label);
    }

    public static int locationCount() {
        return ENTRIES.length;
    }

    private static final KeyItemLocation[] ENTRIES = {
            entry("loc_1100", 1100, "new_londo_ruins", text("Ingward Gift / Drop", "Don d\'Ingward / butin")),
            entry("loc_2500", 2500, "depths", text("Gaping Dragon Drop", "Butin du Dragon béant")),
            entry("loc_2510", 2510, "depths", text("Capra Demon Drop", "Butin du Démon Capra")),
            entry("loc_2520", 2520, "painted_world", text("Crossbreed Priscilla Drop", "Butin de Priscilla la Métissée")),
            entry("loc_2530", 2530, "darkroot_garden", text("Moonlight Butterfly Drop", "Butin du Papillon Clair de Lune")),
            entry("loc_2540", 2540, "darkroot_garden", text("Great Gray Wolf Sif Drop", "Butin du Grand loup gris Sif")),
            entry("loc_2550", 2550, "catacombs", text("Pinwheel Drop", "Butin de Virevent")),
            entry("loc_2560", 2560, "tomb_of_the_giants", text("Gravelord Nito Drop", "Butin de Seigneur des tombes Nito")),
            entry("loc_2570", 2570, "blighttown", text("Chaos Witch Quelaag Drop", "Butin de la Sorcière du Chaos Quelaag")),
            entry("loc_2580", 2580, "lost_izalith", text("Bed of Chaos Drop", "Butin du Foyer du Chaos")),
            entry("loc_2590", 2590, "sens_fortress", text("Iron Golem Drop", "Butin du Golem de Fer")),
            entry("loc_2600", 2600, "anor_londo", text("Dark Sun Gwyndolin Drop", "Butin de Gwendolin le Soleil noir")),
            entry("loc_2610", 2610, "anor_londo", text("Dragon Slayer Ornstein and Executioner Smough Drop", "Butin d\'Ornstein et Smough")),
            entry("loc_2630", 2630, "new_londo_ruins", text("Four Kings Drop", "Butin des Quatre Rois")),
            entry("loc_2640", 2640, "dukes_archives", text("Seath the Scaleless Drop", "Butin de Seath l\'Écorché")),
            entry("loc_2650", 2650, "kiln_of_the_first_flame", text("Gwyn, Lord of Cinder Drop", "Butin de Gwyn, Seigneur des Cendres")),
            entry("loc_2670", 2670, "demon_ruins", text("Centipede Demon Drop", "Butin du Démon Centipède")),
            entry("loc_2680", 2680, "oolacile_sanctuary", text("Sanctuary Guardian Drop", "Butin du Gardien du Sanctuaire")),
            entry("loc_2690", 2690, "royal_wood", text("Knight Artorias Drop", "Butin d\'Artorias le Marcheur des Abysses")),
            entry("loc_2700", 2700, "chasm_of_the_abyss", text("Manus, Father of the Abyss Drop", "Butin de Manus, Père des Abysses")),
            entry("loc_2710", 2710, "royal_wood", text("Black Dragon Kalameet Drop", "Butin de Kalameet le Dragon noir")),
            entry("loc_6190", 6190, "undead_parish", text("Andre of Astora shop item / drop", "Boutique / butin d\'André d\'Astora")),
            entry("loc_6231", 6231, "undead_burg", text("Undead Merchant shop item / drop", "Boutique / butin du marchand")),
            entry("loc_1000240", 1000240, "depths", text("Sewer Chamber Key", "Clé de la chambre des égouts")),
            entry("loc_1000500", 1000500, "depths", text("Chest", "Coffre")),
            entry("loc_1010000", 1010000, "undead_parish", text("Mystery Key", "Clé mystère")),
            entry("loc_1010140", 1010140, "undead_parish", text("Basement Key", "Clé du sous-sol")),
            entry("loc_1010450", 1010450, "undead_burg", text("Chest #1", "Coffre nº1")),
            entry("loc_1010460", 1010460, "undead_burg", text("Chest #2", "Coffre nº2")),
            entry("loc_1020070", 1020070, "firelink_shrine", text("Chest #1", "Coffre nº1")),
            entry("loc_1020180", 1020180, "firelink_shrine", text("Chest #2", "Coffre nº2")),
            entry("loc_1020190", 1020190, "firelink_shrine", text("Chest #3", "Coffre nº3")),
            entry("loc_1020200", 1020200, "firelink_shrine", text("Chest #4", "Coffre nº4")),
            entry("loc_1020210", 1020210, "firelink_shrine", text("Undead Asylum F2 West Key", "Clé ouest RDC de l\'Asile")),
            entry("loc_1100140", 1100140, "painted_world", text("Annex Key", "Clé de l\'Annexe")),
            entry("loc_1100370", 1100370, "painted_world", text("Andre Statue", "Statue d\'André")),
            entry("loc_1100500", 1100500, "painted_world", text("Chest", "Coffre")),
            entry("loc_1200140", 1200140, "darkroot_garden", text("Andre Statue", "Statue d\'André")),
            entry("loc_1200500", 1200500, "darkroot_garden", text("Chest #1", "Coffre nº1")),
            entry("loc_1200510", 1200510, "darkroot_garden", text("Chest #2", "Coffre nº2")),
            entry("loc_1210500", 1210500, "royal_wood", text("Chest #1", "Coffre nº1")),
            entry("loc_1210550", 1210550, "royal_wood", text("Chest #2", "Coffre nº2")),
            entry("loc_1210510", 1210510, "oolacile_township", text("Chest #1", "Coffre nº1")),
            entry("loc_1210520", 1210520, "oolacile_township", text("Chest #2", "Coffre nº2")),
            entry("loc_1210540", 1210540, "oolacile_township", text("Chest #3", "Coffre nº3")),
            entry("loc_1300020", 1300020, "catacombs", text("Darkmoon Seance Ring", "Anneau de Séance de Lune Noire")),
            entry("loc_1310500", 1310500, "tomb_of_the_giants", text("Andre Statue", "Statue d\'André")),
            entry("loc_1320180", 1320180, "great_hollow", text("Chest", "Coffre")),
            entry("loc_1400500", 1400500, "blighttown", text("Chest #1", "Coffre nº1")),
            entry("loc_1400510", 1400510, "blighttown", text("Chest #2", "Coffre nº2")),
            entry("loc_1400520", 1400520, "blighttown", text("Chest #3", "Coffre nº3")),
            entry("loc_1410100", 1410100, "demon_ruins", text("Chest #1", "Coffre nº1")),
            entry("loc_1410410", 1410410, "lost_izalith", text("Chest #1", "Coffre nº1")),
            entry("loc_1410500", 1410500, "lost_izalith", text("Chest #2", "Coffre nº2")),
            entry("loc_1410520", 1410520, "lost_izalith", text("Chest #3", "Coffre nº3")),
            entry("loc_1410530", 1410530, "demon_ruins", text("Chaos Flame Ember", "Emplacement de la braise du Chaos")),
            entry("loc_1500000", 1500000, "sens_fortress", text("Chest #1", "Coffre nº1")),
            entry("loc_1500020", 1500020, "sens_fortress", text("Chest #2", "Coffre nº2")),
            entry("loc_1500040", 1500040, "sens_fortress", text("Chest #3", "Coffre nº3")),
            entry("loc_1500090", 1500090, "sens_fortress", text("Chest #4", "Coffre nº4")),
            entry("loc_1500100", 1500100, "sens_fortress", text("Chest #5", "Coffre nº5")),
            entry("loc_1500150", 1500150, "sens_fortress", text("Cage Key", "Clé de la cage")),
            entry("loc_1510510", 1510510, "anor_londo", text("Chest #1", "Coffre nº1")),
            entry("loc_1510520", 1510520, "anor_londo", text("Chest #2", "Coffre nº2")),
            entry("loc_1510530", 1510530, "anor_londo", text("Chest #3", "Coffre nº3")),
            entry("loc_1510540", 1510540, "anor_londo", text("Chest #4", "Coffre nº4")),
            entry("loc_1510560", 1510560, "anor_londo", text("Chest #5", "Coffre nº5")),
            entry("loc_1510570", 1510570, "anor_londo", text("Chest #6", "Coffre nº6")),
            entry("loc_1510580", 1510580, "anor_londo", text("Chest #7", "Coffre nº7")),
            entry("loc_1510590", 1510590, "anor_londo", text("Chest #8", "Coffre nº8")),
            entry("loc_1510600", 1510600, "anor_londo", text("Chest #9", "Coffre nº9")),
            entry("loc_1510610", 1510610, "anor_londo", text("Chest #10", "Coffre nº10")),
            entry("loc_1510620", 1510620, "anor_londo", text("Chest #11", "Coffre nº11")),
            entry("loc_1510650", 1510650, "anor_londo", text("Chest #12", "Coffre nº12")),
            entry("loc_1510660", 1510660, "anor_londo", text("Chest #13", "Coffre nº13")),
            entry("loc_1510670", 1510670, "anor_londo", text("Chest #14", "Coffre nº14")),
            entry("loc_1510680", 1510680, "anor_londo", text("Chest #15", "Coffre nº15")),
            entry("loc_1510690", 1510690, "anor_londo", text("Chest #16", "Coffre nº16")),
            entry("loc_1600290", 1600290, "new_londo_ruins", text("Chest #1", "Coffre nº1")),
            entry("loc_1600500", 1600500, "new_londo_ruins", text("Chest #2", "Coffre nº2")),
            entry("loc_1600510", 1600510, "new_londo_ruins", text("Chest #3", "Coffre nº3")),
            entry("loc_1700020", 1700020, "dukes_archives", text("Chest #1", "Coffre nº1")),
            entry("loc_1700050", 1700050, "dukes_archives", text("Chest #2", "Coffre nº2")),
            entry("loc_1700210", 1700210, "dukes_archives", text("Archive Prison Extra Key", "Clé supplémentaire de la Prison")),
            entry("loc_1700510", 1700510, "dukes_archives", text("Chest #3", "Coffre nº3")),
            entry("loc_1700520", 1700520, "dukes_archives", text("Chest #4", "Coffre nº4")),
            entry("loc_1700530", 1700530, "dukes_archives", text("Chest #5", "Coffre nº5")),
            entry("loc_1700540", 1700540, "dukes_archives", text("Chest #6", "Coffre nº6")),
            entry("loc_1700560", 1700560, "dukes_archives", text("Chest #7", "Coffre nº7")),
            entry("loc_1700580", 1700580, "dukes_archives", text("Chest #8", "Coffre nº8")),
            entry("loc_1700590", 1700590, "dukes_archives", text("Chest #9", "Coffre nº9")),
            entry("loc_1700600", 1700600, "dukes_archives", text("Chest #10", "Coffre nº10")),
            entry("loc_1700630", 1700630, "dukes_archives", text("Chest #11", "Coffre nº11")),
            entry("loc_1810080", 1810080, "undead_asylum", text("Peculiar Doll", "Poupée étrange")),
            entry("loc_27100200", 27100200, "dukes_archives", text("Duke\'s Archives Special Golem", "Golem spécial des Archives")),
            entry("loc_27803001", 27803001, "oolacile_township", text("2nd Mimic - Crest Key", "2e Mimique — Clé du blason")),
            entry("loc_27900000", 27900000, "undead_burg", text("Black Knight", "Chevalier noir")),
            entry("loc_27900100", 27900100, "undead_parish", text("Black Knight", "Chevalier noir")),
            entry("loc_27901000", 27901000, "darkroot_basin", text("Black Knight", "Chevalier noir")),
            entry("loc_27902000", 27902000, "catacombs", text("Black Knight", "Chevalier noir")),
            entry("loc_27903000", 27903000, "tomb_of_the_giants", text("Black Knight", "Chevalier noir")),
            entry("loc_27907000", 27907000, "undead_asylum", text("Black Knight", "Chevalier noir")),
    };

    public static final List<KeyItemLocation> KEY_ITEM_LOCATIONS = List.copyOf(Arrays.asList(ENTRIES));

    private KeyItemLocationCatalog() {}
}
