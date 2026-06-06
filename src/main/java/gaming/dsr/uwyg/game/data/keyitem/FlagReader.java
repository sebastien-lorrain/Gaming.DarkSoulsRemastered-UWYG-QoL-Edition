package gaming.dsr.uwyg.game.data.keyitem;

@FunctionalInterface
public interface FlagReader {
    Integer readUInt32(int byteOffset);
}
