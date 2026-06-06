package gaming.dsr.uwyg.game.data.keyitem;

import java.util.List;

public record AreaGroup(String areaId, LocalizedText area, List<KeyItemLocation> locations) {}
