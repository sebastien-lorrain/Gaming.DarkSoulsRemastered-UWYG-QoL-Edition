package gaming.dsr.uwyg.game.data.keyitem;

import java.util.function.Function;

public enum KeyItemOverlayLocale {
    EN(
            "key_item_location_overlay_EN.html",
            "en",
            "Key locations",
            "opened",
            LocalizedText::english),
    FR(
            "key_item_location_overlay_FR.html",
            "fr",
            "Emplacements-clé",
            "ouvert",
            LocalizedText::french);

    private final String outputFileName;
    private final String htmlLang;
    private final String headerTitle;
    private final String openedLabel;
    private final Function<LocalizedText, String> textSelector;

    KeyItemOverlayLocale(
            final String outputFileName,
            final String htmlLang,
            final String headerTitle,
            final String openedLabel,
            final Function<LocalizedText, String> textSelector
    ) {
        this.outputFileName = outputFileName;
        this.htmlLang = htmlLang;
        this.headerTitle = headerTitle;
        this.openedLabel = openedLabel;
        this.textSelector = textSelector;
    }

    public String outputFileName() {
        return outputFileName;
    }

    public String htmlLang() {
        return htmlLang;
    }

    public String headerTitle() {
        return headerTitle;
    }

    public String openedLabel() {
        return openedLabel;
    }

    public String displayText(final LocalizedText text) {
        return textSelector.apply(text);
    }
}
