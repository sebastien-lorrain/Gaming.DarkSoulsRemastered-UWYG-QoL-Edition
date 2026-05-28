package gaming.dsr.uwyg.windows;

import gaming.dsr.uwyg.equipment.types.enums.ItemType;
import gaming.dsr.uwyg.game.GameConstants;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.ptr.IntByReference;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Component
public final class Win32GameMemory {

    public byte[] readMemory(
            final ProcessBinding process,
            final long address,
            final int size
    ) {
        final Memory output = new Memory(size);
        final IntByReference bytesRead = new IntByReference();
        final boolean readSucceeded = Kernel32.INSTANCE.ReadProcessMemory(
                process.getHandle(),
                new Pointer(address),
                output,
                size,
                bytesRead
        );
        if (!readSucceeded) {
            return null;
        }
        final long bytesActuallyRead = Integer.toUnsignedLong(bytesRead.getValue());
        if (bytesActuallyRead != size) {
            return null;
        }
        return output.getByteArray(0, size);
    }

    public Integer readUInt32(
            final ProcessBinding process,
            final long address
    ) {
        final byte[] bytes = readMemory(process, address, 4);
        if (bytes == null) {
            return null;
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    public Long readUInt64(
            final ProcessBinding process,
            final long address
    ) {
        final byte[] bytes = readMemory(process, address, 8);
        if (bytes == null) {
            return null;
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getLong();
    }

    public boolean writeUInt32(
            final ProcessBinding process,
            final long address,
            final int value
    ) {
        final Memory input = new Memory(4);
        input.setInt(0, value);
        final IntByReference bytesWritten = new IntByReference();
        final boolean writeSucceeded = Kernel32.INSTANCE.WriteProcessMemory(
                process.getHandle(),
                new Pointer(address),
                input,
                4,
                bytesWritten
        );
        return writeSucceeded && bytesWritten.getValue() == 4;
    }

    public boolean writeUInt64(
            final ProcessBinding process,
            final long address,
            final long value
    ) {
        final Memory input = new Memory(8);
        input.setLong(0, value);
        final IntByReference bytesWritten = new IntByReference();
        final boolean writeSucceeded = Kernel32.INSTANCE.WriteProcessMemory(
                process.getHandle(),
                new Pointer(address),
                input,
                8,
                bytesWritten
        );
        return writeSucceeded && bytesWritten.getValue() == 8;
    }

    public boolean writeInventorySlot(
            final ProcessBinding process,
            final long inventoryAddress,
            final int slotIndex,
            final SlotWrite payload
    ) {
        final long slotAddress = inventoryAddress + ((long) slotIndex * GameConstants.INV_SLOT_SIZE);
        final Memory input = new Memory(GameConstants.INV_SLOT_SIZE);
        input.setInt(0, payload.type().getValue());
        input.setInt(4, payload.id());
        input.setInt(8, payload.count());
        input.setInt(12, payload.mysteriousNumber());
        input.setInt(16, payload.valid());
        input.setInt(20, payload.durability());
        input.setInt(24, payload.hits());

        final IntByReference bytesWritten = new IntByReference();
        final boolean writeSucceeded = Kernel32.INSTANCE.WriteProcessMemory(
                process.getHandle(),
                new Pointer(slotAddress),
                input,
                GameConstants.INV_SLOT_SIZE,
                bytesWritten
        );
        return writeSucceeded && bytesWritten.getValue() == GameConstants.INV_SLOT_SIZE;
    }

    public record SlotWrite(
            ItemType type,
            int id,
            int count,
            int mysteriousNumber,
            int valid,
            int durability,
            int hits
    ) {}
}
