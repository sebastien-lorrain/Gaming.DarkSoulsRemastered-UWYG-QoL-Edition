package gaming.dsr.uwyg.inventory;

import gaming.dsr.uwyg.game.GameConstants;
import gaming.dsr.uwyg.game.MainExecutableModuleLocator;
import gaming.dsr.uwyg.scan.MemoryPattern;
import gaming.dsr.uwyg.scan.ProcessMemoryScanner;
import gaming.dsr.uwyg.windows.ProcessBinding;
import gaming.dsr.uwyg.windows.Win32GameMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Locates the inventory metadata block in live game memory.
 *
 * <p>Discovery is anchored on a known instruction sequence from the inventory management
 * code ({@code mov eax,[rcx+20]} …). That function also loads global pointers via
 * {@code mov reg,[rip+disp]}. We resolve those static slots, follow a short pointer
 * chain into the heap, and validate candidates against the inventory table header layout.
 */
@Component
public final class InventorySignatureLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventorySignatureLocator.class);

    /** Bytes to read above the anchor when looking for global pointer loads. */
    private static final int BYTES_BEFORE_ANCHOR = 0x400;

    /** How far around a heap pointer we search for the inventory header pattern. */
    private static final long HEADER_SEARCH_RADIUS = 0x2_000_000L;

    /** How many pointer dereferences to attempt from each static slot. */
    private static final int MAX_POINTER_CHAIN_DEPTH = 3;

    /** Size of {@code 48/4C 8B xx [rip+disp32]} on x86-64. */
    private static final int RIP_RELATIVE_MOV_SIZE = 7;

    private final ProcessMemoryScanner memoryScanner;
    private final Win32GameMemory gameMemory;

    public InventorySignatureLocator(
            final ProcessMemoryScanner memoryScanner,
            final Win32GameMemory gameMemory
    ) {
        this.memoryScanner = memoryScanner;
        this.gameMemory = gameMemory;
    }

    public List<Long> findInventoryAddresses(final ProcessBinding process) {
        final List<Long> anchorAddresses = findInventoryAnchorAddresses(process);
        if (anchorAddresses.isEmpty()) {
            return List.of();
        }

        final Set<Long> signatures = new LinkedHashSet<>();
        for (final long anchorAddress : anchorAddresses) {
            for (final long staticSlot : staticSlotsBeforeAnchor(process, anchorAddress)) {
                signatures.addAll(inventorySignaturesFromStaticSlot(process, staticSlot));
            }
        }
        return List.copyOf(signatures);
    }

    // --- Step 1: anchor inside the game executable ---------------------------------

    private List<Long> findInventoryAnchorAddresses(final ProcessBinding process) {
        final Optional<MainExecutableModuleLocator.ModuleImage> image =
                MainExecutableModuleLocator.findDarkSoulsExecutableImage(process);
        if (image.isEmpty()) {
            LOGGER.warn("Could not enumerate main module");
            return List.of();
        }

        final MainExecutableModuleLocator.ModuleImage module = image.get();
        final List<Long> matches = memoryScanner.findMatchesInRegion(
                process,
                MemoryPattern.darkSoulsRemasteredInventoryAnchorInstruction(),
                module.baseAddress(),
                module.size());

        if (matches.isEmpty()) {
            LOGGER.warn("Inventory anchor pattern not found in {}; inventory unavailable (game patch mismatch?)", GameConstants.PROCESS_NAME);
            return List.of();
        }

        if (matches.size() > 1) {
            LOGGER.warn("Inventory anchor pattern matched {} times in {}; will check each, which hopefully will help discriminate", matches.size(), GameConstants.PROCESS_NAME);
        }
        return matches;
    }

    // --- Step 2: global pointer loads in the same function -------------------------

    /**
     * The anchored function receives its slot pointer from earlier instructions.
     * Those loads typically use RIP-relative addressing into {@code .data}.
     */
    private List<Long> staticSlotsBeforeAnchor(final ProcessBinding process, final long anchorAddress) {
        final long regionStart = anchorAddress - BYTES_BEFORE_ANCHOR;
        final byte[] code = gameMemory.readMemory(process, regionStart, BYTES_BEFORE_ANCHOR);
        if (code == null || code.length < RIP_RELATIVE_MOV_SIZE) {
            return List.of();
        }

        final Set<Long> staticSlots = new LinkedHashSet<>();
        for (int offset = 0; offset <= code.length - RIP_RELATIVE_MOV_SIZE; offset++) {
            if (!isRipRelativeMov(code, offset)) {
                continue;
            }

            final long instructionAddress = regionStart + offset;
            final byte[] instructionBytes = Arrays.copyOfRange(code, offset, offset + RIP_RELATIVE_MOV_SIZE);
            staticSlots.add(MemoryPattern.ripRelativeMovDispTarget(instructionAddress, instructionBytes));
        }
        return List.copyOf(staticSlots);
    }

    /** {@code 48/4C 8B reg, [rip+disp32]} — a 7-byte global pointer load. */
    private static boolean isRipRelativeMov(final byte[] code, final int offset) {
        final byte rex = code[offset];
        if (rex != (byte) 0x48 && rex != (byte) 0x4C) {
            return false;
        }
        if (code[offset + 1] != (byte) 0x8B) {
            return false;
        }
        return MemoryPattern.isRipRelativeModRm(code[offset + 2]);
    }

    // --- Step 3: heap search + header validation -----------------------------------

    private List<Long> inventorySignaturesFromStaticSlot(final ProcessBinding process, final long staticSlot) {
        final List<Long> signatures = new ArrayList<>();

        Long heapPointer = gameMemory.readUInt64(process, staticSlot);
        for (int depth = 0; depth < MAX_POINTER_CHAIN_DEPTH && heapPointer != null && heapPointer != 0L; depth++) {
            signatures.addAll(validatedHeadersNear(process, heapPointer));
            heapPointer = gameMemory.readUInt64(process, heapPointer);
        }
        return signatures;
    }

    private List<Long> validatedHeadersNear(final ProcessBinding process, final long heapPointer) {
        final long searchStart = Math.max(0L, heapPointer - HEADER_SEARCH_RADIUS);
        final int searchSize = (int) Math.min(
                HEADER_SEARCH_RADIUS * 2,
                Integer.MAX_VALUE - 1L);

        final List<Long> candidates = memoryScanner.findMatchesInRegion(
                process,
                MemoryPattern.darkSoulsRemasterInventoryTable(),
                searchStart,
                searchSize);

        final List<Long> validated = new ArrayList<>();
        for (final long candidate : candidates) {
            if (isInventoryTableHeader(gameMemory, process, candidate)) {
                validated.add(candidate);
            }
        }
        return validated;
    }

    /**
     * Expected 16-byte inventory metadata header:
     * {@code [sentinel, 0, slotCount, 0]} where {@code slotCount == 2048}.
     * The sentinel is either {@code 0xFFFFFFFF} or a self-referencing pointer (legacy).
     */
    static boolean isInventoryTableHeader(
            final Win32GameMemory gameMemory,
            final ProcessBinding process,
            final long address
    ) {
        final Integer sentinel = gameMemory.readUInt32(process, address);
        final Integer reserved1 = gameMemory.readUInt32(process, address + 4);
        final Integer slotCount = gameMemory.readUInt32(process, address + 8);
        final Integer reserved2 = gameMemory.readUInt32(process, address + 12);

        if (sentinel == null || reserved1 == null || slotCount == null || reserved2 == null) {
            return false;
        }
        if (reserved1 != 0 || slotCount != GameConstants.INVENTORY_SLOTS || reserved2 != 0) {
            return false;
        }

        if (Integer.toUnsignedLong(sentinel) == 0xFFFFFFFFL) {
            return true;
        }

        final Long selfPointer = gameMemory.readUInt64(process, address);
        return selfPointer != null && selfPointer == address;
    }
}
