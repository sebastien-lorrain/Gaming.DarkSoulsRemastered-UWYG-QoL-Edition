package gaming.dsr.uwyg.game.data.keyitem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

final class ItemLotParamParser {

    private static final int HEADER_SKIP = 0x30;
    private static final int RECORD_ENTRY_SIZE = 12;
    private static final int DATA_RECORD_SIZE = 0x94;
    private static final int GET_ITEM_LOT_FLAG_OFFSET = 128;
    private static final int ITEM_FLAG_OFFSET = 96;

    private ItemLotParamParser() {}

    static Map<Integer, ItemLotRecord> parseLots(final byte[] fileContent) throws IOException {

        if (fileContent.length < HEADER_SKIP + 8) {
            throw new IOException("ItemLotParam too small");
        }
        final ByteBuffer header = ByteBuffer.wrap(fileContent).order(ByteOrder.LITTLE_ENDIAN);
        // ItemLotParam header: <IIHH at 0 — count is the second uint16 at offset 10
        final int lotCount = Short.toUnsignedInt(header.getShort(10));
        final Map<Integer, ItemLotRecord> lots = new HashMap<>(lotCount * 2);
        int offset = HEADER_SKIP;
        for (int index = 0; index < lotCount; index++) {
            if (offset + RECORD_ENTRY_SIZE > fileContent.length) {
                break;
            }
            final int lotId = header.getInt(offset);
            final int dataOffset = header.getInt(offset + 4);
            offset += RECORD_ENTRY_SIZE;
            if (dataOffset + DATA_RECORD_SIZE > fileContent.length) {
                continue;
            }
            final int getItemLotFlag = ByteBuffer.wrap(fileContent)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt(dataOffset + GET_ITEM_LOT_FLAG_OFFSET);
            final int[] itemFlags = new int[8];
            for (int itemIndex = 0; itemIndex < 8; itemIndex++) {
                itemFlags[itemIndex] = ByteBuffer.wrap(fileContent)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .getInt(dataOffset + ITEM_FLAG_OFFSET + itemIndex * 4);
            }
            lots.put(lotId, new ItemLotRecord(lotId, getItemLotFlag, itemFlags));
        }
        return lots;
    }
}
