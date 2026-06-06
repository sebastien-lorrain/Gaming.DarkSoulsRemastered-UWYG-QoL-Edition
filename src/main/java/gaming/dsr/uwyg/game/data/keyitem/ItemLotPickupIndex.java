package gaming.dsr.uwyg.game.data.keyitem;

import gaming.dsr.uwyg.game.data.KeyItemLocationCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record ItemLotPickupIndex(Map<Integer, int[]> pickupFlagsByLocationId) {

    private static final Logger LOGGER = LoggerFactory.getLogger(ItemLotPickupIndex.class);

    public int[] pickupFlagsForLocation(final int locationId) {
        return pickupFlagsByLocationId.getOrDefault(locationId, new int[0]);
    }

    public boolean isTrackable(final int locationId) {
        return pickupFlagsForLocation(locationId).length > 0;
    }

    public int trackableLocationCount() {
        int count = 0;
        for (final int[] flags : pickupFlagsByLocationId.values()) {
            if (flags.length > 0) {
                count++;
            }
        }
        return count;
    }

    public static Optional<ItemLotPickupIndex> loadForCatalog(final Path gameParamFile) throws IOException {
        final byte[] fileBytes = Files.readAllBytes(gameParamFile);
        final byte[] bndBytes = GameParamDcxDecompressor.decompressIfDcx(fileBytes);
        final byte[] itemLotParamBytes = GameParamBndUnpacker.extractItemLotParam(bndBytes);
        if (itemLotParamBytes == null) {
            return Optional.empty();
        }
        final Map<Integer, ItemLotRecord> lotsById = ItemLotParamParser.parseLots(itemLotParamBytes);
        LOGGER.debug("Parsed {} item lots from ItemLotParam", lotsById.size());
        final Map<Integer, int[]> flagsByLocation = new LinkedHashMap<>();
        for (final KeyItemLocation location : KeyItemLocationCatalog.KEY_ITEM_LOCATIONS) {
            final int[] flags = ItemLotPickupFlagsResolver.resolvePickupFlags(location.locationId(), lotsById);
            if (flags.length > 0) {
                flagsByLocation.put(location.locationId(), flags);
            }
        }
        return Optional.of(new ItemLotPickupIndex(Map.copyOf(flagsByLocation)));
    }
}
