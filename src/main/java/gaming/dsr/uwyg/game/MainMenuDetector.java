package gaming.dsr.uwyg.game;

import gaming.dsr.uwyg.windows.ProcessBinding;
import gaming.dsr.uwyg.windows.Win32GameMemory;

import org.springframework.stereotype.Component;

@Component
public final class MainMenuDetector {

    private final Win32GameMemory gameMemory;

    public MainMenuDetector(final Win32GameMemory gameMemory) {
        this.gameMemory = gameMemory;
    }

    public boolean isMainMenu(
            final ProcessBinding process,
            final GameAddresses addresses
    ) {

        final Integer inGameFlagOrSentinel = gameMemory.readUInt32(process, addresses.getInGame());
        return inGameFlagOrSentinel == null || inGameFlagOrSentinel == 0xFFFFFFFF;
    }
}
