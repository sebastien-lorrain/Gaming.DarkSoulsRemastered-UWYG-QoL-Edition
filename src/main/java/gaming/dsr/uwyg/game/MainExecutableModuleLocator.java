package gaming.dsr.uwyg.game;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import gaming.dsr.uwyg.windows.ProcessBinding;

import java.nio.file.Path;
import java.util.Optional;

/** Locates the loaded image of {@link GameConstants#PROCESS_NAME} for AOB scans scoped to the game exe. */
public final class MainExecutableModuleLocator {

    public record ModuleImage(long baseAddress, int size) {}

    private MainExecutableModuleLocator() {
    }

    public static Optional<ModuleImage> findDarkSoulsExecutableImage(final ProcessBinding process) {

        final WinNT.HANDLE snapshot = Kernel32.INSTANCE.CreateToolhelp32Snapshot(
                Tlhelp32.TH32CS_SNAPMODULE,
                new WinDef.DWORD(process.getProcessId())
        );

        if (WinNT.INVALID_HANDLE_VALUE.equals(snapshot)) {
            return Optional.empty();
        }

        try {

            final Tlhelp32.MODULEENTRY32W moduleEntry = new Tlhelp32.MODULEENTRY32W();
            moduleEntry.dwSize = new WinDef.DWORD(moduleEntry.size());

            if (!Kernel32.INSTANCE.Module32FirstW(snapshot, moduleEntry)) {
                return Optional.empty();
            }

            do {
                final String moduleFileName = Native.toString(moduleEntry.szModule);
                if (GameConstants.PROCESS_NAME.equalsIgnoreCase(moduleFileName)) {
                    final long base = Pointer.nativeValue(moduleEntry.modBaseAddr);
                    return Optional.of(new ModuleImage(base, moduleEntry.modBaseSize.intValue()));
                }
            } while (Kernel32.INSTANCE.Module32NextW(snapshot, moduleEntry));

            return Optional.empty();
        } finally {
            Kernel32.INSTANCE.CloseHandle(snapshot);
        }
    }

    /** Directory containing {@link GameConstants#PROCESS_NAME} (game install root). */
    public static Optional<Path> findGameInstallDirectory(final ProcessBinding process) {

        final char[] pathBuffer = new char[WinDef.MAX_PATH];
        final int length = Psapi.INSTANCE.GetModuleFileNameExW(
                process.getHandle(),
                null,
                pathBuffer,
                pathBuffer.length);
        if (length == 0) {
            return Optional.empty();
        }
        return Optional.of(Path.of(new String(pathBuffer, 0, length)).getParent());
    }

    /**
     * {@code GameParam.parambnd.dcx} next to the exe or under {@code param/GameParam/}.
     */
    public static Optional<Path> findGameParamFile(final Path gameInstallDirectory) {

        final Path path = gameInstallDirectory.resolve("param").resolve("GameParam").resolve("GameParam.parambnd.dcx");
        if (java.nio.file.Files.isRegularFile(path)) {
            return Optional.of(path);
        }
        return Optional.empty();
    }
}
