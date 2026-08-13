package gaming.dsr.uwyg.equipment.definition;

import java.util.List;
import java.util.Map;

/**
 * Boss-soul weapons and the full id ranges their transposed reward can occupy.
 *
 * <p>Transposing (trading) a boss soul yields one weapon picked at random from a fixed pool. Each pool's ids run
 * {@code min, min+100, …, max}, and the lowest id of a pool is the catalogue base weapon (see
 * {@link EquipmentDefinitionTables}). A picked-up weapon may therefore carry any step-100 id inside its base's
 * range — the offset is unbounded by the +0…+5 / infusion encoding
 * Shield spans {@code 1411000}–{@code 1414600}). Ids inside a pool that have no catalogue row of their own fold into
 * the owning base weapon.
 */
public final class BossSoulWeaponTranspositions {

    /** Inclusive id ranges (stepped by 100) per base weapon id; concatenated pool segments are listed together. */
    private static final Map<Integer, List<int[]>> RANGES_BY_BASE_ITEM_ID = Map.ofEntries(
            Map.entry(406000, List.of(new int[] {406000, 406500})),                                         // Quelaag's Furysword
            Map.entry(503000, List.of(new int[] {503000, 503200})),                                         // Chaos Blade
            Map.entry(307000, List.of(new int[] {307000, 307100})),                                         // Greatsword of Artorias
            Map.entry(311000, List.of(new int[] {311000, 312700})),                                         // Greatsword of Artorias (Cursed)
            Map.entry(314000, List.of(new int[] {314000, 315700})),                                         // Great Lord Greatsword
            Map.entry(1507000, List.of(new int[] {1507000, 1510600})),                                      // Greatshield of Artorias
            Map.entry(704000, List.of(new int[] {704000, 704600})),                                         // Golem Axe
            Map.entry(903000, List.of(new int[] {903000, 903100})),                                         // Dragon Bone Fist
            Map.entry(1051000, List.of(new int[] {1051000, 1051900}, new int[] {1054000, 1054000})),        // Dragonslayer Spear
            Map.entry(1052000, List.of(new int[] {1052000, 1053000})),                                      // Moonlight Butterfly Horn
            Map.entry(1411000, List.of(new int[] {1411000, 1414600})),                                      // Crystal Ring Shield
            Map.entry(856000, List.of(new int[] {856000, 857100})),                                         // Smough's Hammer
            Map.entry(1151000, List.of(new int[] {1151000, 1151800})),                                      // Lifehunt Scythe
            Map.entry(1205000, List.of(new int[] {1205000, 1205300})),                                      // Darkmoon Bow
            Map.entry(1304000, List.of(new int[] {1304000, 1304500})),                                      // Tin Darkmoon Catalyst
            Map.entry(9012000, List.of(new int[] {9012000, 9012800}, new int[] {9013000, 9013700})),        // Abyss Greatsword
            Map.entry(9017000, List.of(new int[] {9017000, 9017500})));                                     // Manus Catalyst

    private BossSoulWeaponTranspositions() {
    }

    /**
     * Whether {@code rawUnsigned} is a transposed variant id of the boss-soul weapon {@code baseUnsigned} — i.e. it
     * lands on a step-of-100 inside one of that weapon's transposition ranges. Returns {@code false} for any weapon
     * that is not a boss-soul reward.
     */
    public static boolean isTransposedVariant(final long baseUnsigned, final long rawUnsigned) {
        final List<int[]> ranges = RANGES_BY_BASE_ITEM_ID.get((int) baseUnsigned);
        if (ranges == null) {
            return false;
        }
        for (final int[] range : ranges) {
            final long min = Integer.toUnsignedLong(range[0]);
            final long max = Integer.toUnsignedLong(range[1]);
            if (rawUnsigned >= min && rawUnsigned <= max && (rawUnsigned - min) % 100L == 0L) {
                return true;
            }
        }
        return false;
    }
}
