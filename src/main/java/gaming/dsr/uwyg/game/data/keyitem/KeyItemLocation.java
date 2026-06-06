package gaming.dsr.uwyg.game.data.keyitem;

import gaming.dsr.uwyg.game.data.KeyItemLocationCatalog;

public record KeyItemLocation(String id, int locationId, String areaId, LocalizedText label) {

    public Area area() {
        return KeyItemLocationCatalog.areaById(areaId);
    }
}
