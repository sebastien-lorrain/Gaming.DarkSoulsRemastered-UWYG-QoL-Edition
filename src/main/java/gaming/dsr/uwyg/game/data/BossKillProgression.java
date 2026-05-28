package gaming.dsr.uwyg.game.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BossKillProgression {

    public record EventFlagWord(int byteOffset, int mask) {}
    public record BossKillTally(int total, List<String> killedBossNames) {}

    public static final Map<String, Integer> BOSS_DEFEATED_EVENT_FLAGS;

    static {
        final Map<String, Integer> m = new LinkedHashMap<>();
        m.put("Asylum Demon", 16);
        m.put("Bell Gargoyle", 3);
        m.put("Capra Demon", 11010902);
        m.put("Ceaseless Discharge", 11410900);
        m.put("Centipede Demon", 11410901);
        m.put("Chaos Witch Quelaag", 9);
        m.put("Crossbreed Priscilla", 4);
        m.put("Dark Sun Gwyndolin", 11510900);
        m.put("Demon Firesage", 11410410);
        m.put("Four Kings", 13);
        m.put("Gaping Dragon", 2);
        m.put("Great Grey Wolf Sif", 5);
        m.put("Gwyn Lord of Cinder", 15);
        m.put("Iron Golem", 11);
        m.put("Moonlight Butterfly", 11200900);
        m.put("Nito", 7);
        m.put("Ornstein And Smough", 12);
        m.put("Pinwheel", 6);
        m.put("Seath the Scaleless", 14);
        m.put("Stray Demon", 11810900);
        m.put("Taurus Demon", 11010901);
        m.put("The Bed of Chaos", 10);
        m.put("Artorias the Abysswalker", 11210001);
        m.put("Black Dragon Kalameet", 11210004);
        m.put("Manus, Father of the Abyss", 11210002);
        m.put("Sanctuary Guardian", 11210000);
        BOSS_DEFEATED_EVENT_FLAGS = Collections.unmodifiableMap(m);
    }

    private static final Map<String, Integer> EVENT_FLAG_GROUPS = Map.of(
            "0", 0x00000,
            "1", 0x00500,
            "5", 0x05F00,
            "6", 0x0B900,
            "7", 0x11300);

    private static final Map<String, Integer> EVENT_FLAG_AREAS = Map.ofEntries(
            Map.entry("000", 0),
            Map.entry("100", 1),
            Map.entry("101", 2),
            Map.entry("102", 3),
            Map.entry("110", 4),
            Map.entry("120", 5),
            Map.entry("121", 6),
            Map.entry("130", 7),
            Map.entry("131", 8),
            Map.entry("132", 9),
            Map.entry("140", 10),
            Map.entry("141", 11),
            Map.entry("150", 12),
            Map.entry("151", 13),
            Map.entry("160", 14),
            Map.entry("170", 15),
            Map.entry("180", 16),
            Map.entry("181", 17));

    /**
     * Resolves how a boss event flag is stored: byte offset into the flag blob and a little-endian uint32 mask.
     */
    public static EventFlagWord decodeEventFlag(final int eventFlagId) {

        if (eventFlagId < 0) {
            throw new IllegalArgumentException("Event flag id must be non-negative: " + eventFlagId);
        }

        final String idString = String.format("%08d", eventFlagId);
        if (idString.length() != 8) {
            throw new IllegalArgumentException("Event flag id out of 8-digit range: " + eventFlagId);
        }

        final String group = idString.substring(0, 1);
        final String area = idString.substring(1, 4);
        final int section = Integer.parseInt(idString.substring(4, 5));
        final int number = Integer.parseInt(idString.substring(5, 8));
        final Integer groupOffset = EVENT_FLAG_GROUPS.get(group);
        final Integer areaIndex = EVENT_FLAG_AREAS.get(area);

        if (groupOffset == null || areaIndex == null) {
            throw new IllegalArgumentException("Unknown event flag id: " + eventFlagId);
        }

        int offset = groupOffset + areaIndex * 0x500 + section * 128;
        offset += (number - number % 32) / 8;

        final int mask = 0x80000000 >>> (number % 32);
        return new EventFlagWord(offset, mask);
    }

    private BossKillProgression() {}
}
