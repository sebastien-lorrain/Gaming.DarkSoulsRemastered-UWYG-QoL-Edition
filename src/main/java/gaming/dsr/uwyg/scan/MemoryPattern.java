package gaming.dsr.uwyg.scan;

import lombok.Getter;
import lombok.Setter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

@Getter
@Setter
public final class MemoryPattern {

    private byte[] bytes = new byte[0];
    private boolean[] mask = new boolean[0];
    private int alignment = 1;

    public static MemoryPattern darkSoulsRemasterInventoryTable() {
        final MemoryPattern pattern = new MemoryPattern();
        pattern.load(new int[]{0xFFFFFFFF, 0, 2048, 0}, new boolean[]{false, true, true, true});
        return pattern;
    }

    /**
     * Static slot whose value points at player game data.
     */
    public static MemoryPattern darkSoulsRemasteredBaseBInstruction() {
        final MemoryPattern pattern = new MemoryPattern();
        final byte[] rawBytes = new byte[]{
                (byte) 0x48, (byte) 0x8B, (byte) 0x05,
                0, 0, 0, 0,
                (byte) 0x45, (byte) 0x33, (byte) 0xED,
                (byte) 0x48, (byte) 0x8B, (byte) 0xF1,
                (byte) 0x48, (byte) 0x85, (byte) 0xC0
        };
        final boolean[] fixed = new boolean[]{
                true, true, true,
                false, false, false, false,
                true, true, true, true, true, true, true, true, true
        };
        pattern.loadRaw(rawBytes, fixed, 1);
        return pattern;
    }

    /**
     * Static slot for the event-flag pointer chain used by boss flags.
     */
    public static MemoryPattern darkSoulsRemasteredProgressionFlagsInstruction() {
        final MemoryPattern pattern = new MemoryPattern();
        final byte[] rawBytes = new byte[]{
                (byte) 0x48, (byte) 0x8B, (byte) 0x0D,
                0, 0, 0, 0,
                (byte) 0x99,
                (byte) 0x33, (byte) 0xC2,
                (byte) 0x45, (byte) 0x33, (byte) 0xC0,
                (byte) 0x2B, (byte) 0xC2,
                (byte) 0x8D, (byte) 0x50, (byte) 0xF6
        };
        final boolean[] fixed = new boolean[]{
                true, true, true,
                false, false, false, false,
                true, true, true, true, true, true, true, true, true, true, true
        };
        pattern.loadRaw(rawBytes, fixed, 1);
        return pattern;
    }

    public static long movRaxRipDispTarget(
            final long instructionAddress,
            final byte[] instructionPrefix7
    ) {
        if (instructionPrefix7.length < 7) {
            throw new IllegalArgumentException("Need at least 7 instruction bytes");
        }
        final int displacement = ByteBuffer.wrap(instructionPrefix7).order(ByteOrder.LITTLE_ENDIAN).getInt(3);
        final long ripAfterInstruction = instructionAddress + 7;
        return ripAfterInstruction + displacement;
    }

    private void load(
            final int[] values,
            final boolean[] valueMask
    ) {
        alignment = 4;
        bytes = new byte[alignment * values.length];
        mask = new boolean[alignment * values.length];
        Arrays.fill(mask, true);

        for (int packedIntIndex = 0; packedIntIndex < valueMask.length; packedIntIndex++) {
            if (!valueMask[packedIntIndex]) {
                for (int byteWithinAlignedWord = 0; byteWithinAlignedWord < alignment; byteWithinAlignedWord++) {
                    mask[byteWithinAlignedWord + packedIntIndex * alignment] = false;
                }
            }
        }

        for (int packedIntIndex = 0; packedIntIndex < values.length; packedIntIndex++) {
            final int packedLittleEndianValue = values[packedIntIndex];
            for (int byteWithinAlignedWord = 0; byteWithinAlignedWord < alignment; byteWithinAlignedWord++) {
                bytes[byteWithinAlignedWord + packedIntIndex * alignment] =
                        (byte) (packedLittleEndianValue >> (byteWithinAlignedWord * 8));
            }
        }
    }

    private void loadRaw(
            final byte[] rawBytes,
            final boolean[] fixedMask,
            final int byteAlignment
    ) {
        alignment = byteAlignment;
        bytes = Arrays.copyOf(rawBytes, rawBytes.length);
        mask = Arrays.copyOf(fixedMask, fixedMask.length);
    }
}
