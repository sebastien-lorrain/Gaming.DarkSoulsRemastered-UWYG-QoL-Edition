package gaming.dsr.uwyg.windows;

import com.sun.jna.platform.win32.WinNT;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class ProcessBinding {

    private int processId;
    private WinNT.HANDLE handle;
}
