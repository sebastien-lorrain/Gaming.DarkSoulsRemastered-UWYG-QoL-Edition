package gaming.dsr.uwyg.game.data;

import gaming.dsr.uwyg.game.data.keyitem.Area;
import gaming.dsr.uwyg.game.data.keyitem.AreaGroup;
import gaming.dsr.uwyg.game.data.keyitem.FlagReader;
import gaming.dsr.uwyg.game.data.keyitem.ItemLotPickupIndex;
import gaming.dsr.uwyg.game.data.keyitem.KeyItemLocation;
import gaming.dsr.uwyg.game.data.keyitem.KeyItemLocationTally;
import gaming.dsr.uwyg.game.data.keyitem.KeyItemOverlayLocale;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Tracks key item pickup locations and renders OBS overlay HTML grouped by area.
 */
public final class KeyItemLocationProgression {

    private static final String OVERLAY_TEMPLATE_FILE = "key_item_location_overlay.html.template";

    private static final Path OVERLAY_TEMPLATE_PATH =
            Path.of("templates", OVERLAY_TEMPLATE_FILE);

    private static final String AREA_GROUP_REPEAT_START = "<!-- @repeat area-group -->";
    private static final String LOCATION_ITEM_REPEAT_START = "<!-- @repeat location-item -->";
    private static final String REPEAT_END = "<!-- @endrepeat -->";

    public static Path overlayTemplatePath() {
        return OVERLAY_TEMPLATE_PATH;
    }

    public static Optional<Path> findOverlayTemplatePath() {
        return Files.isRegularFile(OVERLAY_TEMPLATE_PATH) ? Optional.of(OVERLAY_TEMPLATE_PATH) : Optional.empty();
    }

    public static boolean isOverlayTemplatePresent() {
        return findOverlayTemplatePath().isPresent();
    }

    public static List<AreaGroup> groupByArea() {
        final Map<String, List<KeyItemLocation>> byAreaId = new LinkedHashMap<>();
        for (final KeyItemLocation location : KeyItemLocationCatalog.KEY_ITEM_LOCATIONS) {
            byAreaId.computeIfAbsent(location.areaId(), ignored -> new ArrayList<>()).add(location);
        }

        final Map<String, AreaGroup> groupsByAreaId = new HashMap<>(byAreaId.size());
        for (final Map.Entry<String, List<KeyItemLocation>> entry : byAreaId.entrySet()) {
            final List<KeyItemLocation> locations = entry.getValue();
            final Area area = KeyItemLocationCatalog.areaById(entry.getKey());
            groupsByAreaId.put(entry.getKey(), new AreaGroup(entry.getKey(), area.name(), List.copyOf(locations)));
        }

        final List<AreaGroup> ordered = new ArrayList<>(groupsByAreaId.size());
        for (final String areaId : KeyItemLocationCatalog.AREA_PROGRESSION_ORDER) {
            final AreaGroup group = groupsByAreaId.remove(areaId);
            if (group != null) {
                ordered.add(group);
            }
        }
        for (final KeyItemLocation location : KeyItemLocationCatalog.KEY_ITEM_LOCATIONS) {
            final AreaGroup group = groupsByAreaId.remove(location.areaId());
            if (group != null) {
                ordered.add(group);
            }
        }
        return List.copyOf(ordered);
    }

    public static boolean isLocationOpened(final KeyItemLocation location, final Set<String> openedIds) {
        return openedIds.contains(location.id());
    }

    public static boolean isEventFlagSet(
            final BossKillProgression.EventFlagWord word,
            final Integer leWord
    ) {
        return leWord != null && (leWord & word.mask()) != 0;
    }

    public static boolean areFlagsSet(final int[] eventFlagIds, final FlagReader flagReader) {
        for (final int eventFlagId : eventFlagIds) {
            try {
                final BossKillProgression.EventFlagWord word = BossKillProgression.decodeEventFlag(eventFlagId);
                if (isEventFlagSet(word, flagReader.readUInt32(word.byteOffset()))) {
                    return true;
                }
            } catch (final IllegalArgumentException ignored) {
                // skip invalid flag ids
            }
        }
        return false;
    }

    public static KeyItemLocationTally tallyOpenedLocations(
            final FlagReader flagReader,
            final ItemLotPickupIndex pickupIndex
    ) {

        if (pickupIndex == null) {
            return new KeyItemLocationTally(0, 0, List.of());
        }

        final List<String> openedIds = new ArrayList<>();

        for (final KeyItemLocation location : KeyItemLocationCatalog.KEY_ITEM_LOCATIONS) {
            final int[] pickupFlags = pickupIndex.pickupFlagsForLocation(location.locationId());
            if (pickupFlags.length == 0) {
                continue;
            }
            if (areFlagsSet(pickupFlags, flagReader)) {
                openedIds.add(location.id());
            }
        }

        return new KeyItemLocationTally(
                openedIds.size(),
                pickupIndex.trackableLocationCount(),
                List.copyOf(openedIds));
    }

    public static boolean writeOverlaysIfTemplatePresent(
            final KeyItemLocationTally tally,
            final ItemLotPickupIndex pickupIndex
    ) throws IOException {

        final Optional<Path> templatePath = findOverlayTemplatePath();
        if (templatePath.isEmpty()) {
            return false;
        }

        final String template = Files.readString(templatePath.get(), StandardCharsets.UTF_8);
        final Set<String> opened = Set.copyOf(tally.openedLocationIds());

        for (final KeyItemOverlayLocale locale : KeyItemOverlayLocale.values()) {
            Files.writeString(
                    Path.of(locale.outputFileName()),
                    renderOverlayHtml(template, tally, opened, locale, pickupIndex),
                    StandardCharsets.UTF_8);
        }

        return true;
    }

    static String renderOverlayHtml(
            final String template,
            final KeyItemLocationTally tally,
            final Set<String> openedIds,
            final KeyItemOverlayLocale locale,
            final ItemLotPickupIndex pickupIndex
    ) {

        final int areaOuterStart = template.indexOf(AREA_GROUP_REPEAT_START);
        final int locationInnerStart = template.indexOf(LOCATION_ITEM_REPEAT_START, areaOuterStart);
        final int locationInnerEnd = template.indexOf(REPEAT_END, locationInnerStart);
        final int areaOuterEnd = template.indexOf(REPEAT_END, locationInnerEnd + REPEAT_END.length());

        if (areaOuterStart < 0 || locationInnerStart < 0 || locationInnerEnd < 0 || areaOuterEnd < 0) {
            throw new IllegalStateException(
                    "key_item_location_overlay.html.template must contain nested "
                            + "area-group and location-item repeat blocks");
        }

        final String areaPrefix = template.substring(
                areaOuterStart + AREA_GROUP_REPEAT_START.length(), locationInnerStart);
        final String locationItemTemplate = template.substring(
                locationInnerStart + LOCATION_ITEM_REPEAT_START.length(), locationInnerEnd);
        final String areaSuffix = template.substring(locationInnerEnd + REPEAT_END.length(), areaOuterEnd);

        final StringBuilder areaGroups = new StringBuilder();

        for (final AreaGroup group : groupByArea()) {
            final StringBuilder locationItems = new StringBuilder();
            int areaOpened = 0;
            int areaTotal = 0;
            for (final KeyItemLocation location : group.locations()) {
                final boolean trackable = isLocationTrackableInOverlay(location, pickupIndex);
                if (trackable) {
                    areaTotal++;
                    if (isLocationOpened(location, openedIds)) {
                        areaOpened++;
                    }
                }
                final String checked =
                        trackable && isLocationOpened(location, openedIds) ? " checked" : "";
                final String trackableClass = trackable ? "" : " untracked not-checkable";
                locationItems.append(locationItemTemplate
                        .replace("{{LOCATION_ID}}", HtmlEscapes.escape(location.id()))
                        .replace("{{LOCATION_LABEL}}", HtmlEscapes.escape(locale.displayText(location.label())))
                        .replace("{{CHECKED}}", checked)
                        .replace("{{UNTRACKED_CLASS}}", trackableClass)
                        .replace("{{OPENED_LABEL}}", HtmlEscapes.escape(locale.openedLabel())));
            }

            final String areaDisplay = HtmlEscapes.escape(locale.displayText(group.area()));
            final String areaBlock = areaPrefix
                    .replace("{{AREA_ID}}", HtmlEscapes.escape(group.areaId()))
                    .replace("{{AREA_NAME}}", areaDisplay)
                    .replace("{{AREA_OPENED_COUNT}}", Integer.toString(areaOpened))
                    .replace("{{AREA_TOTAL_COUNT}}", Integer.toString(areaTotal))
                    + locationItems
                    + areaSuffix
                            .replace("{{AREA_ID}}", HtmlEscapes.escape(group.areaId()))
                            .replace("{{AREA_NAME}}", areaDisplay)
                            .replace("{{AREA_OPENED_COUNT}}", Integer.toString(areaOpened))
                            .replace("{{AREA_TOTAL_COUNT}}", Integer.toString(areaTotal));
            areaGroups.append(areaBlock);
        }

        final String assembled = template.substring(0, areaOuterStart)
                + areaGroups
                + template.substring(areaOuterEnd + REPEAT_END.length());

        return assembled
                .replace("{{LANG}}", locale.htmlLang())
                .replace("{{HEADER_TITLE}}", HtmlEscapes.escape(locale.headerTitle()))
                .replace("{{OPENED_COUNT}}", Integer.toString(tally.openedCount()))
                .replace("{{TOTAL_LOCATIONS}}", Integer.toString(tally.totalCount()))
                .replace("{{OPENED_LABEL}}", HtmlEscapes.escape(locale.openedLabel()));
    }

    public static boolean isLocationTrackableInOverlay(
            final KeyItemLocation location,
            final ItemLotPickupIndex pickupIndex
    ) {
        return pickupIndex != null && pickupIndex.isTrackable(location.locationId());
    }

    private KeyItemLocationProgression() {}
}
