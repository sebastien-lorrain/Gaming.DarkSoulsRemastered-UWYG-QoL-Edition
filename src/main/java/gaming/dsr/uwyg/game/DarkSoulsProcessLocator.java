package gaming.dsr.uwyg.game;

import gaming.dsr.uwyg.windows.ProcessBinding;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Tlhelp32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.WinNT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public final class DarkSoulsProcessLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DarkSoulsProcessLocator.class);

    public boolean tryAttachRunningGame(final ProcessBinding outputProcessBinding) {

        LOGGER.info("Trying to find running game");
        final ProcessBinding locatedProcessBinding = findProcess();

        if (locatedProcessBinding.getHandle() == null) {
            return false;
        }

        outputProcessBinding.setProcessId(locatedProcessBinding.getProcessId());
        outputProcessBinding.setHandle(locatedProcessBinding.getHandle());
        return true;
    }

    private static ProcessBinding findProcess() {

        final ProcessBinding locatedProcessBinding = new ProcessBinding();
        final WinNT.HANDLE processSnapshotHandle =
                Kernel32.INSTANCE.CreateToolhelp32Snapshot(Tlhelp32.TH32CS_SNAPPROCESS, new WinDef.DWORD(0));

        if (WinNT.INVALID_HANDLE_VALUE.equals(processSnapshotHandle)) {

            LOGGER.warn("Invalid process snapshot finding the process id");
            return locatedProcessBinding;
        }

        final Tlhelp32.PROCESSENTRY32 processDirectoryEntry = new Tlhelp32.PROCESSENTRY32();
        if (Kernel32.INSTANCE.Process32First(processSnapshotHandle, processDirectoryEntry)) {

            do {
                final String executableFileName = Native.toString(processDirectoryEntry.szExeFile);
                if (!GameConstants.PROCESS_NAME.equalsIgnoreCase(executableFileName)) {
                    continue;
                }
                final WinNT.HANDLE openedProcessHandle =
                        openProcess(processDirectoryEntry.th32ProcessID.intValue());
                if (openedProcessHandle != null) {
                    locatedProcessBinding.setProcessId(processDirectoryEntry.th32ProcessID.intValue());
                    locatedProcessBinding.setHandle(openedProcessHandle);
                    LOGGER.info("Found game process");
                    Kernel32.INSTANCE.CloseHandle(processSnapshotHandle);
                    return locatedProcessBinding;
                }
            } while (Kernel32.INSTANCE.Process32Next(processSnapshotHandle, processDirectoryEntry));
        }

        LOGGER.info("Can't find game process");
        Kernel32.INSTANCE.CloseHandle(processSnapshotHandle);
        return locatedProcessBinding;
    }

    private static WinNT.HANDLE openProcess(final int processId) {

        final WinNT.HANDLE openedProcessHandle =
                Kernel32.INSTANCE.OpenProcess(WinNT.PROCESS_ALL_ACCESS, false, processId);

        if (openedProcessHandle == null) {

            final int windowsErrorCode = Kernel32.INSTANCE.GetLastError();

            if (windowsErrorCode == 5) {
                LOGGER.warn("Can't get a valid process handle: Try launching the application as administrator");
            } else {
                LOGGER.warn("Can't get a valid process handle: Error code: {}", windowsErrorCode);
            }
        }

        return openedProcessHandle;
    }
}
