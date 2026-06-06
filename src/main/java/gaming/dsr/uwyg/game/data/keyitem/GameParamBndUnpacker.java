package gaming.dsr.uwyg.game.data.keyitem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

final class GameParamBndUnpacker {

    private static final int RECORD_OFFSET = 0x20;

    private GameParamBndUnpacker() {}

    static byte[] extractItemLotParam(final byte[] content) throws IOException {

        if (content.length < 4 || content[0] != 'B' || content[1] != 'N' || content[2] != 'D') {
            throw new IOException("Not a BND3 archive");
        }
        final ByteBuffer header = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        header.position(0x0c);
        final int magicFlag = header.getInt();
        final int recordCount = header.getInt();
        int offset = RECORD_OFFSET;
        for (int recordIndex = 0; recordIndex < recordCount; recordIndex++) {
            header.position(offset);
            final int recordSep = header.getInt();
            final int fileSize = header.getInt();
            final int fileOffset = header.getInt();
            header.getInt();
            final int filenameOffset = header.getInt();
            if (magicFlag != 0x70) {
                header.getInt();
                offset += 24;
            } else {
                offset += 20;
            }
            if (recordSep != 0x40) {
                throw new IOException("Malformed BND3 record");
            }
            final String filename = readNullTerminatedUtf8(content, filenameOffset);
            if (filename.endsWith("ItemLotParam.param")) {
                return Arrays.copyOfRange(content, fileOffset, fileOffset + fileSize);
            }
        }
        return null;
    }

    private static String readNullTerminatedUtf8(final byte[] content, int offset) {
        final int start = offset;
        while (offset < content.length && content[offset] != 0) {
            offset++;
        }
        return new String(content, start, offset - start, StandardCharsets.UTF_8);
    }
}
