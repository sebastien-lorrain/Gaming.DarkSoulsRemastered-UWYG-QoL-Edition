package gaming.dsr.uwyg.game.data.keyitem;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class ItemLotPickupFlagsResolver {

    /** Extra item-lot ids tied to a race location (e.g. O&amp;S Smough lot at 2620). */
    private static final Map<Integer, int[]> LINKED_LOT_IDS_BY_LOCATION = Map.of(
            2610, new int[] {2620}
    );

    /** Item randomizer may place the lot in the first free id in [locationId, locationId + maxSize). */
    private static final int LOT_PLACEMENT_SCAN_RANGE = 16;

    private ItemLotPickupFlagsResolver() {}

    static int[] resolvePickupFlags(final int locationId, final Map<Integer, ItemLotRecord> lotsById) {

        final LinkedHashSet<Integer> flags = new LinkedHashSet<>();
        collectPickupFlagsForLocation(locationId, lotsById, flags);
        final int[] linked = LINKED_LOT_IDS_BY_LOCATION.get(locationId);
        if (linked != null) {
            for (final int linkedLotId : linked) {
                collectPickupFlags(linkedLotId, lotsById, flags);
            }
        }
        return flags.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void collectPickupFlagsForLocation(
            final int locationId,
            final Map<Integer, ItemLotRecord> lotsById,
            final Set<Integer> flags
    ) {

        final int flagsBefore = flags.size();
        collectPickupFlags(locationId, lotsById, flags);
        if (flags.size() > flagsBefore) {
            return;
        }
        for (int offset = 1; offset < LOT_PLACEMENT_SCAN_RANGE; offset++) {
            collectPickupFlags(locationId + offset, lotsById, flags);
            if (flags.size() > flagsBefore) {
                return;
            }
        }
    }

    private static void collectPickupFlags(
            final int lotId,
            final Map<Integer, ItemLotRecord> lotsById,
            final Set<Integer> flags
    ) {

        final ItemLotRecord lot = lotsById.get(lotId);
        if (lot == null) {
            return;
        }
        if (lot.getItemLotFlag() > 0) {
            flags.add(lot.getItemLotFlag());
            return;
        }
        for (final int itemFlag : lot.itemFlags()) {
            if (itemFlag > 0) {
                flags.add(itemFlag);
            }
        }
    }
}
