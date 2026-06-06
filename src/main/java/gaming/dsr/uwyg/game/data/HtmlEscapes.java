package gaming.dsr.uwyg.game.data;

import org.apache.commons.text.StringEscapeUtils;

/** HTML escaping for OBS overlay templates. */
final class HtmlEscapes {

    static String escape(final String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return StringEscapeUtils.escapeHtml4(text);
    }

    private HtmlEscapes() {}
}
