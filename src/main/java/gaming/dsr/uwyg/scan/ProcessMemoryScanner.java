package gaming.dsr.uwyg.scan;

import gaming.dsr.uwyg.windows.ProcessBinding;
import gaming.dsr.uwyg.windows.Win32GameMemory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinNT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public final class ProcessMemoryScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessMemoryScanner.class);

    private final Win32GameMemory gameMemory;

    public ProcessMemoryScanner(final Win32GameMemory gameMemory) {
        this.gameMemory = gameMemory;
    }

    public List<Long> findMatches(
            final ProcessBinding process,
            final MemoryPattern pattern
    ) {
        final List<Long> result = new ArrayList<>();
        long totalSize = 0;
        long scannedSize = 0;
        long regionPointer = 0;
        final WinNT.MEMORY_BASIC_INFORMATION memoryRegionInfo = new WinNT.MEMORY_BASIC_INFORMATION();

        LOGGER.info("STARTING SCAN");
        while (Kernel32.INSTANCE.VirtualQueryEx(
                        process.getHandle(),
                        new Pointer(regionPointer),
                        memoryRegionInfo,
                        new BaseTSD.SIZE_T(memoryRegionInfo.size()))
                .longValue() != 0) {
            final long regionSize = memoryRegionInfo.regionSize.longValue();
            totalSize += regionSize;
            final int regionState = memoryRegionInfo.state.intValue();
            final int regionType = memoryRegionInfo.type.intValue();
            final boolean shouldScanRegion =
                    regionState == WinNT.MEM_COMMIT
                            && (regionType == WinNT.MEM_PRIVATE || regionType == WinNT.MEM_IMAGE);

            if (shouldScanRegion && regionSize > 0 && regionSize <= Integer.MAX_VALUE) {
                LOGGER.debug(
                        "SCANNING: 0x{} -> 0x{}",
                        Long.toHexString(regionPointer),
                        Long.toHexString(regionPointer + regionSize));
                final byte[] regionBytes = gameMemory.readMemory(process, regionPointer, (int) regionSize);
                if (regionBytes != null) {
                    for (final Integer patternOffsetInRegion : patternScan(regionBytes, pattern)) {
                        result.add(regionPointer + Integer.toUnsignedLong(patternOffsetInRegion));
                    }
                    scannedSize += regionSize;
                }
            }

            if (regionSize <= 0) {
                break;
            }
            regionPointer += regionSize;
        }

        LOGGER.info("TOTAL SIZE: {} bytes SCANNED: {} bytes", (double) totalSize, (double) scannedSize);
        return result;
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
