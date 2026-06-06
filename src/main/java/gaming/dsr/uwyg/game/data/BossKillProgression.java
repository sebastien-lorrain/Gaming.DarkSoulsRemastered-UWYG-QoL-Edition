package gaming.dsr.uwyg.game.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class BossKillProgression {

    public record EventFlagWord(int byteOffset, int mask) {}
    public record LocalizedBossName(String english, String french) {}
    public record BossDefinition(String id, LocalizedBossName names, int eventFlagId) {}
    public record BossKillTally(int total, List<String> killedBossIds) {}

    public enum OverlayLocale {
        EN("boss_kill_overlay_EN.html", "en", "Bosses", "defeated", LocalizedBossName::english),
        FR("boss_kill_overlay_FR.html", "fr", "Boss", "vaincu", LocalizedBossName::french);

        private final String outputFileName;
        private final String htmlLang;
        private final String headerTitle;
        private final String defeatedLabel;
        private final Function<LocalizedBossName, String> nameSelector;

        OverlayLocale(
                final String outputFileName,
                final String htmlLang,
                final String headerTitle,
                final String defeatedLabel,
                final Function<LocalizedBossName, String> nameSelector
        ) {
            this.outputFileName = outputFileName;
            this.htmlLang = htmlLang;
            this.headerTitle = headerTitle;
            this.defeatedLabel = defeatedLabel;
            this.nameSelector = nameSelector;
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

        public String defeatedLabel() {
            return defeatedLabel;
        }

        public String displayName(final LocalizedBossName names) {
            return nameSelector.apply(names);
        }
    }

    private static final String OVERLAY_TEMPLATE_FILE = "boss_kill_overlay.html.template";

    private static final Path OVERLAY_TEMPLATE_PATH =
            Path.of("templates", OVERLAY_TEMPLATE_FILE);

    /** {@code templates/boss_kill_overlay.html.template} relative to the JVM working directory. */
    public static Path overlayTemplatePath() {
        return OVERLAY_TEMPLATE_PATH;
    }

    public static Optional<Path> findOverlayTemplatePath() {
        return Files.isRegularFile(OVERLAY_TEMPLATE_PATH) ? Optional.of(OVERLAY_TEMPLATE_PATH) : Optional.empty();
    }

    /** Bosses in overlay / tally order (LinkedHashMap insertion order). */
    public static final List<BossDefinition> BOSSES = List.of(
            boss("asylum_demon", "Asylum Demon", "Démon du Refuge", 16),
            boss("bell_gargoyles", "Bell Gargoyles", "Gargouilles de la cloche", 3),
            boss("capra_demon", "Capra Demon", "Démon Capra", 11010902),
            boss("taurus_demon", "Taurus Demon", "Démon Taureau", 11010901),
            boss("moonlight_butterfly", "Moonlight Butterfly", "Papillon Clair de Lune", 11200900),
            boss("stray_demon", "Stray Demon", "Démon errant", 11810900),
            boss("gaping_dragon", "Gaping Dragon", "Dragon Béant", 2),
            boss("chaos_witch_quelaag", "Chaos Witch Quelaag", "Sorcière du Chaos Quelaag", 9),
            boss("iron_golem", "Iron Golem", "Golem de Fer", 11),
            boss(
                    "ornstein_and_smough",
                    "Dragon Slayer Ornstein and Executioner Smough",
                    "Ornstein le Tueur de dragons et Smough le Bourreau",
                    12),
            boss("dark_sun_gwyndolin", "Dark Sun Gwyndolin", "Gwendolin le Soleil noir", 11510900),
            boss("four_kings", "Four Kings", "Quatre Rois", 13),
            boss("great_grey_wolf_sif", "Great Grey Wolf Sif", "Grand loup gris Sif", 5),
            boss("ceaseless_discharge", "Ceaseless Discharge", "Décharge incessante", 11410900),
            boss("demon_firesage", "Demon Firesage", "Démon Pyrosage", 11410410),
            boss("centipede_demon", "Centipede Demon", "Démon Centipède", 11410901),
            boss("bed_of_chaos", "Bed of Chaos", "Foyer du Chaos", 10),
            boss("pinwheel", "Pinwheel", "Virevent", 6),
            boss("gravelord_nito", "Gravelord Nito", "Seigneur des tombes Nito", 7),
            boss("seath_the_scaleless", "Seath the Scaleless", "Seath l'écorché", 14),
            boss("crossbreed_priscilla", "Crossbreed Priscilla", "Priscilla la Métissée", 4),
            boss("gwyn_lord_of_cinder", "Gwyn, Lord of Cinder", "Gwyn, Seigneur des Cendres", 15),
            boss("sanctuary_guardian", "Sanctuary Guardian", "Gardien du Sanctuaire", 11210000),
            boss("artorias_the_abysswalker", "Artorias the Abysswalker", "Artorias le Marcheur des Abysses", 11210001),
            boss("black_dragon_kalameet", "Black Dragon Kalameet", "Kalameet le Dragon noir", 11210004),
            boss("manus_father_of_the_abyss", "Manus, Father of the Abyss", "Manus, Père des Abysses", 11210002));

    /** SoulSplitter-style event flags keyed by stable boss id (same iteration order as {@link #BOSSES}). */
    public static final Map<String, Integer> BOSS_DEFEATED_EVENT_FLAGS = buildBossDefeatedEventFlags();

    private static Map<String, Integer> buildBossDefeatedEventFlags() {
        final Map<String, Integer> flags = new LinkedHashMap<>();
        for (final BossDefinition boss : BOSSES) {
            flags.put(boss.id(), boss.eventFlagId());
        }
        return Collections.unmodifiableMap(flags);
    }

    private static final Map<String, BossDefinition> BOSSES_BY_ID = BOSSES.stream()
            .collect(Collectors.toUnmodifiableMap(BossDefinition::id, Function.identity()));

    private static final Map<String, Integer> EVENT_FLAG_GROUPS = Map.of(
            "0", 0x00000,
            "1", 0x00500,
            "5", 0x05F00,
            "6", 0x0B900,
            "7", 0x11300);

    private static final Map<String, Integer> EVENT_FLAG_AREAS = Map.ofEntries(
            Map.entry("000", 0),
            Map.entry("100", 1),
            Map.entry("101", 2),
            Map.entry("102", 3),
            Map.entry("110", 4),
            Map.entry("120", 5),
            Map.entry("121", 6),
            Map.entry("130", 7),
            Map.entry("131", 8),
            Map.entry("132", 9),
            Map.entry("140", 10),
            Map.entry("141", 11),
            Map.entry("150", 12),
            Map.entry("151", 13),
            Map.entry("160", 14),
            Map.entry("170", 15),
            Map.entry("180", 16),
            Map.entry("181", 17));

    private static final Pattern BOSS_ITEM_REPEAT =
            Pattern.compile("<!--\\s*@repeat\\s+boss-item\\s*-->(.*?)<!--\\s*@endrepeat\\s*-->", Pattern.DOTALL);

    private static BossDefinition boss(
            final String id,
            final String english,
            final String french,
            final int eventFlagId
    ) {
        return new BossDefinition(id, new LocalizedBossName(english, french), eventFlagId);
    }

    public static String englishNameForId(final String bossId) {
        final BossDefinition boss = BOSSES_BY_ID.get(bossId);
        return boss == null ? bossId : boss.names().english();
    }

    /**
     * Resolves how a boss event flag is stored: byte offset into the flag blob and a little-endian uint32 mask.
     */
    public static EventFlagWord decodeEventFlag(final int eventFlagId) {

        if (eventFlagId < 0) {
            throw new IllegalArgumentException("Event flag id must be non-negative: " + eventFlagId);
        }

        final String idString = String.format("%08d", eventFlagId);
        if (idString.length() != 8) {
            throw new IllegalArgumentException("Event flag id out of 8-digit range: " + eventFlagId);
        }

        final String group = idString.substring(0, 1);
        final String area = idString.substring(1, 4);
        final int section = Integer.parseInt(idString.substring(4, 5));
        final int number = Integer.parseInt(idString.substring(5, 8));
        final Integer groupOffset = EVENT_FLAG_GROUPS.get(group);
        final Integer areaIndex = EVENT_FLAG_AREAS.get(area);

        if (groupOffset == null || areaIndex == null) {
            throw new IllegalArgumentException("Unknown event flag id: " + eventFlagId);
        }

        int offset = groupOffset + areaIndex * 0x500 + section * 128;
        offset += (number - number % 32) / 8;

        final int mask = 0x80000000 >>> (number % 32);
        return new EventFlagWord(offset, mask);
    }

    /**
     * Writes {@code boss_kill_overlay_EN.html} and {@code boss_kill_overlay_FR.html} when the template exists.
     *
     * @return {@code true} if overlay files were written, {@code false} if the template file is missing
     */
    public static boolean writeBossKillOverlaysIfTemplatePresent(final BossKillTally tally) throws IOException {

        final Optional<Path> templatePath = findOverlayTemplatePath();
        if (templatePath.isEmpty()) {
            return false;
        }

        final String template = Files.readString(templatePath.get(), StandardCharsets.UTF_8);

        for (final OverlayLocale locale : OverlayLocale.values()) {
            Files.writeString(
                    Path.of(locale.outputFileName()),
                    renderBossKillOverlayHtml(template, tally, locale),
                    StandardCharsets.UTF_8);
        }

        return true;
    }

    public static boolean isOverlayTemplatePresent() {
        return findOverlayTemplatePath().isPresent();
    }

    static String renderBossKillOverlayHtml(
            final String template,
            final BossKillTally tally,
            final OverlayLocale locale
    ) {

        final Set<String> killed = Set.copyOf(tally.killedBossIds());
        final Matcher repeatMatcher = BOSS_ITEM_REPEAT.matcher(template);

        if (!repeatMatcher.find()) {
            throw new IllegalStateException(
                    "boss_kill_overlay.html.template must contain <!-- @repeat boss-item --> ... <!-- @endrepeat -->");
        }

        final String bossItemTemplate = repeatMatcher.group(1);
        final StringBuilder bossItems = new StringBuilder(BOSSES.size() * bossItemTemplate.length());

        for (final BossDefinition boss : BOSSES) {
            final String displayName = HtmlEscapes.escape(locale.displayName(boss.names()));
            final String checked = killed.contains(boss.id()) ? " checked" : "";
            bossItems.append(bossItemTemplate
                    .replace("{{BOSS_ID}}", HtmlEscapes.escape(boss.id()))
                    .replace("{{BOSS_NAME}}", displayName)
                    .replace("{{CHECKED}}", checked));
        }

        return repeatMatcher.reset()
                .replaceFirst(Matcher.quoteReplacement(bossItems.toString()))
                .replace("{{LANG}}", locale.htmlLang())
                .replace("{{HEADER_TITLE}}", HtmlEscapes.escape(locale.headerTitle()))
                .replace("{{KILLED_COUNT}}", Integer.toString(tally.total()))
                .replace("{{TOTAL_BOSSES}}", Integer.toString(BOSSES.size()))
                .replace("{{DEFEATED_LABEL}}", HtmlEscapes.escape(locale.defeatedLabel()));
    }

    private BossKillProgression() {}
}
