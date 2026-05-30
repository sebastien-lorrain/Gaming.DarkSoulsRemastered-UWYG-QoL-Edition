package gaming.dsr.uwyg.scan;

import gaming.dsr.uwyg.windows.ProcessBinding;
import gaming.dsr.uwyg.windows.Win32GameMemory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public final class ProcessMemoryScanner {

    private final Win32GameMemory gameMemory;

    public ProcessMemoryScanner(final Win32GameMemory gameMemory) {
        this.gameMemory = gameMemory;
    }

    /** Scan a single committed region (e.g. main executable image) for {@link MemoryPattern} matches. */
    public List<Long> findMatchesInRegion(
            final ProcessBinding process,
            final MemoryPattern pattern,
            final long regionBaseAddress,
            final int regionSize
    ) {
        if (regionSize <= 0) {
            return List.of();
        }
        final byte[] regionBytes = gameMemory.readMemory(process, regionBaseAddress, regionSize);
        if (regionBytes == null) {
            return Collections.emptyList();
        }
        final List<Long> matches = new ArrayList<>();
        for (final Integer patternOffsetInRegion : patternScan(regionBytes, pattern)) {
            matches.add(regionBaseAddress + Integer.toUnsignedLong(patternOffsetInRegion));
        }
        return matches;
    }

    List<Integer> patternScan(
            final byte[] data,
            final MemoryPattern pattern
    ) {
        final List<Integer> offsets = new ArrayList<>();
        final byte[] patternBytes = pattern.getBytes();
        final boolean[] wildcardMask = pattern.getMask();
        if (data.length < patternBytes.length) {
            return offsets;
        }
        final int lastPossibleOffsetInclusive = data.length - patternBytes.length;
        for (int scanBaseOffset = 0;
                scanBaseOffset <= lastPossibleOffsetInclusive;
                scanBaseOffset += pattern.getAlignment()) {
            if (patternMatchesAt(data, patternBytes, wildcardMask, scanBaseOffset)) {
                offsets.add(scanBaseOffset);
            }
        }
        return offsets;
    }

    private static boolean patternMatchesAt(
            final byte[] haystack,
            final byte[] patternBytes,
            final boolean[] wildcardMask,
            final int scanBaseOffset
    ) {
        for (int patternByteIndex = 0; patternByteIndex < patternBytes.length; patternByteIndex++) {
            if (wildcardMask[patternByteIndex]
                    && haystack[scanBaseOffset + patternByteIndex] != patternBytes[patternByteIndex]) {
                return false;
            }
        }
        return true;
    }
}
