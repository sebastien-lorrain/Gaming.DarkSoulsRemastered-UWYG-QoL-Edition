package gaming.dsr.uwyg.game.data.keyitem;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

final class GameParamDcxDecompressor {

    private GameParamDcxDecompressor() {}

    static byte[] decompressIfDcx(final byte[] content) throws IOException {

        if (content.length < 4 || content[0] != 'D' || content[1] != 'C' || content[2] != 'X') {
            return content;
        }
        int offset = 4;
        if (readLeInt(content, offset) != 0x100) {
            throw new IOException("Unexpected DCX header");
        }
        offset += 4;
        offset += 12;
        offset += 4;
        offset += 4;
        final int uncompSize = readBeInt(content, offset);
        int compSize = readBeInt(content, offset + 4);
        offset += 8;
        offset += 8;
        offset += 0x18;
        offset += 4;
        offset += 4;
        if (content[offset] != 0x78 || content[offset + 1] != (byte) 0xDA) {
            throw new IOException("Unexpected DCX zlib header");
        }
        offset += 2;
        compSize -= 2;
        final Inflater inflater = new Inflater(true);
        inflater.setInput(content, offset, compSize);
        final byte[] output = new byte[uncompSize];
        try {
            final int inflated = inflater.inflate(output);
            if (inflated != uncompSize) {
                throw new IOException("DCX inflate size mismatch");
            }
        } catch (final DataFormatException exception) {
            throw new IOException("Failed to decompress DCX", exception);
        } finally {
            inflater.end();
        }
        return output;
    }

    private static int readLeInt(final byte[] content, final int offset) {
        return ByteBuffer.wrap(content, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private static int readBeInt(final byte[] content, final int offset) {
        return ByteBuffer.wrap(content, offset, 4).order(ByteOrder.BIG_ENDIAN).getInt();
    }
}
