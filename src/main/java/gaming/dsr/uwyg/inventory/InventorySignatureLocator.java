package gaming.dsr.uwyg.inventory;

import gaming.dsr.uwyg.scan.MemoryPattern;
import gaming.dsr.uwyg.scan.ProcessMemoryScanner;
import gaming.dsr.uwyg.windows.ProcessBinding;
import gaming.dsr.uwyg.windows.Win32GameMemory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public final class InventorySignatureLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(InventorySignatureLocator.class);

    private final ProcessMemoryScanner memoryScanner;
    private final Win32GameMemory gameMemory;

    public InventorySignatureLocator(
            final ProcessMemoryScanner memoryScanner,
            final Win32GameMemory gameMemory
    ) {
        this.memoryScanner = memoryScanner;
        this.gameMemory = gameMemory;
    }

    public List<Long> findInventorySignatureAddresses(final ProcessBinding process) {

        final MemoryPattern inventoryTablePattern = MemoryPattern.darkSoulsRemasterInventoryTable();
        final List<Long> candidateAddresses = memoryScanner.findMatches(process, inventoryTablePattern);
        LOGGER.info("Found {} possible addresses", candidateAddresses.size());

        final List<Long> validatedInventorySignatureAddresses = new ArrayList<>();
        for (final Long candidateSignatureAddress : candidateAddresses) {
            final Integer memorySelfReferenceValue = gameMemory.readUInt32(process, candidateSignatureAddress);
            if (memorySelfReferenceValue != null
                    && Integer.toUnsignedLong(memorySelfReferenceValue) == candidateSignatureAddress) {
                validatedInventorySignatureAddresses.add(candidateSignatureAddress);
            }
        }
        return validatedInventorySignatureAddresses;
    }
}
