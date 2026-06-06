package gaming.dsr.uwyg.game.data;

import gaming.dsr.uwyg.game.GameConstants;
import gaming.dsr.uwyg.game.MainExecutableModuleLocator;
import gaming.dsr.uwyg.game.data.keyitem.ItemLotPickupIndex;
import gaming.dsr.uwyg.game.data.keyitem.KeyItemLocationTally;
import gaming.dsr.uwyg.scan.MemoryPattern;
import gaming.dsr.uwyg.scan.ProcessMemoryScanner;
import gaming.dsr.uwyg.windows.ProcessBinding;
import gaming.dsr.uwyg.windows.Win32GameMemory;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Locates player game data and reads arbitrary uint32 fields from that block.
 * Death-count file logging lives here so persistence stays next to the same domain.
 */
@Component
public class AchievementsMonitorer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementsMonitorer.class);
    private static final Path DEATH_COUNT_FILE = Path.of("count_death.txt");
    private static final Path KILLED_BOSSES_FILE = Path.of("count_killed_bosses.txt");
    private static final Path KEY_ITEM_LOCATIONS_COUNT_FILE = Path.of("count_key_item_locations.txt");

    private final ProcessMemoryScanner memoryScanner;
    private final Win32GameMemory gameMemory;

    private int lastSyncedDeathCount = -1;
    private int lastSyncedBossKillCount = -1;
    private List<String> lastSyncedKilledBossIds = List.of();
    private int lastSyncedKeyItemLocationOpenCount = -1;
    private ItemLotPickupIndex cachedPickupIndex;
    private long cachedGameParamModifiedEpochMillis = -1L;

    public AchievementsMonitorer(
            final ProcessMemoryScanner memoryScanner,
            final Win32GameMemory gameMemory
    ) {
        this.memoryScanner = memoryScanner;
        this.gameMemory = gameMemory;
    }

    /** Resets cached values so the next attach rewrites death/boss files from live memory. */
    public synchronized void resetStatFileSyncState() {
        lastSyncedDeathCount = -1;
        lastSyncedBossKillCount = -1;
        lastSyncedKilledBossIds = List.of();
        lastSyncedKeyItemLocationOpenCount = -1;
        cachedPickupIndex = null;
        cachedGameParamModifiedEpochMillis = -1L;
    }

    /**
     * Keeps {@code count_death.txt} aligned with the live in-game death stat whenever it changes.
     */
    public synchronized void syncDeathRunFileFromGameIfNeeded(final int deathNum) {

        if (lastSyncedDeathCount != -1 && Integer.compareUnsigned(deathNum, lastSyncedDeathCount) == 0) {
            return;
        }

        try {
            writeIntegerFile(DEATH_COUNT_FILE, Integer.toUnsignedLong(deathNum));
            lastSyncedDeathCount = deathNum;
        } catch (final IOException exception) {
            LOGGER.error("Failed to sync {}", DEATH_COUNT_FILE.toAbsolutePath(), exception);
        }
    }

    /**
     * Writes {@code count_killed_bosses.txt} when defeated-boss progress changes, and EN/FR overlay HTML when
     * {@code templates/boss_kill_overlay.html.template} exists in the JVM working directory.
     */
    public synchronized void syncKilledBossesFileFromGameIfNeeded(final BossKillProgression.BossKillTally tally) {

        if (lastSyncedBossKillCount != -1 && tally.killedBossIds().equals(lastSyncedKilledBossIds)) {
            return;
        }
        final String namesJoined = tally.killedBossIds().isEmpty()
                ? "(none)"
                : String.join(", ", tally.killedBossIds().stream()
                        .map(BossKillProgression::englishNameForId)
                        .toList());
        LOGGER.info("{} (bosses read as killed: {})", tally.total(), namesJoined);

        try {
            writeIntegerFile(KILLED_BOSSES_FILE, Integer.toUnsignedLong(tally.total()));
            if (!BossKillProgression.writeBossKillOverlaysIfTemplatePresent(tally)) {
                LOGGER.debug(
                        "Boss kill overlay template not found at {}; skipping HTML output",
                        BossKillProgression.overlayTemplatePath().toAbsolutePath());
            }
            lastSyncedBossKillCount = tally.total();
            lastSyncedKilledBossIds = tally.killedBossIds();
        } catch (final IOException exception) {
            LOGGER.error("Failed to sync boss kill files in {}", Path.of(".").toAbsolutePath(), exception);
        }
    }

    /**
     * Creates progress counter files and overlay HTML as zero/empty when missing.
     */
    @PostConstruct
    public void ensureProgressFilesInitialized() {
        try {
            if (!Files.exists(DEATH_COUNT_FILE)) {
                writeIntegerFile(DEATH_COUNT_FILE, 0L);
            }
            if (!Files.exists(KILLED_BOSSES_FILE)) {
                writeIntegerFile(KILLED_BOSSES_FILE, 0L);
            }
            if (!Files.exists(KEY_ITEM_LOCATIONS_COUNT_FILE)) {
                writeIntegerFile(KEY_ITEM_LOCATIONS_COUNT_FILE, 0L);
            }
            if (BossKillProgression.isOverlayTemplatePresent()) {
                BossKillProgression.writeBossKillOverlaysIfTemplatePresent(
                        new BossKillProgression.BossKillTally(0, List.of()));
            }
            if (KeyItemLocationProgression.isOverlayTemplatePresent()) {
                KeyItemLocationProgression.writeOverlaysIfTemplatePresent(
                        new KeyItemLocationTally(0, KeyItemLocationCatalog.locationCount(), List.of()),
                        null);
            }
        } catch (final IOException exception) {
            LOGGER.error("Could not initialize stat files in {}", Path.of(".").toAbsolutePath(), exception);
        }
    }

    /** Follow BaseB once and return the player game data pointer (validated). */
    public Optional<Long> locatePlayerGameData(final ProcessBinding process) {

        final Optional<MainExecutableModuleLocator.ModuleImage> image =
                MainExecutableModuleLocator.findDarkSoulsExecutableImage(process);

        if (image.isEmpty()) {
            LOGGER.warn("Could not enumerate main module; player game data unavailable");
            return Optional.empty();
        }

        final MainExecutableModuleLocator.ModuleImage moduleImage = image.get();

        final List<Long> matches = memoryScanner.findMatchesInRegion(
                process,
                MemoryPattern.darkSoulsRemasteredBaseBInstruction(),
                moduleImage.baseAddress(),
                moduleImage.size());

        if (matches.isEmpty()) {
            LOGGER.warn(
                    "BaseB AOB not found in {}; player game data unavailable (patch/version mismatch?)",
                    GameConstants.PROCESS_NAME);
            return Optional.empty();
        }

        for (final long instructionAddress : matches) {

            final byte[] prefix = gameMemory.readMemory(process, instructionAddress, 7);
            if (prefix == null || prefix.length < 7) {
                continue;
            }
            if (prefix[0] != (byte) 0x48 || prefix[1] != (byte) 0x8B || prefix[2] != (byte) 0x05) {
                continue;
            }

            final long pointerVariableAddress = MemoryPattern.movRaxRipDispTarget(instructionAddress, prefix);
            final Long playerGameData = gameMemory.readUInt64(process, pointerVariableAddress);
            if (playerGameData == null || playerGameData == 0L) {
                continue;
            }

            if (gameMemory.readMemory(process, playerGameData, 1) != null) {
                return Optional.of(playerGameData);
            }
        }

        LOGGER.warn("BaseB pattern matched but player game data could not be validated");
        return Optional.empty();
    }

    /**
     * DSR event-flag block base.
     * Same two-step as {@code BaseB}: static slot → first pointer → second pointer = flag storage read by
     * {@link #tallyKilledBosses}.
     */
    public Optional<Long> locateWorldProgressionFlags(final ProcessBinding process) {

        final Optional<MainExecutableModuleLocator.ModuleImage> image =
                MainExecutableModuleLocator.findDarkSoulsExecutableImage(process);

        if (image.isEmpty()) {
            LOGGER.warn("Could not enumerate main module; progression flags unavailable");
            return Optional.empty();
        }

        final MainExecutableModuleLocator.ModuleImage moduleImage = image.get();

        final List<Long> matches = memoryScanner.findMatchesInRegion(
                process,
                MemoryPattern.darkSoulsRemasteredProgressionFlagsInstruction(),
                moduleImage.baseAddress(),
                moduleImage.size());

        if (matches.isEmpty()) {
            LOGGER.warn("EventFlags AOB not found in {}; boss kills unavailable", GameConstants.PROCESS_NAME);
            return Optional.empty();
        }

        for (final long instructionAddress : matches) {

            final byte[] prefix = gameMemory.readMemory(process, instructionAddress, 7);
            if (prefix == null || prefix.length < 7) {
                continue;
            }

            if (prefix[0] != (byte) 0x48 || prefix[1] != (byte) 0x8B || prefix[2] != (byte) 0x0D) {
                continue;
            }

            final long pointerVariableAddress = MemoryPattern.movRaxRipDispTarget(instructionAddress, prefix);
            final Long firstLevelPointer = gameMemory.readUInt64(process, pointerVariableAddress);
            if (firstLevelPointer == null || firstLevelPointer == 0L) {
                continue;
            }

            final Long worldProgressionFlags = gameMemory.readUInt64(process, firstLevelPointer);
            if (worldProgressionFlags == null || worldProgressionFlags == 0L) {
                continue;
            }

            if (gameMemory.readMemory(process, worldProgressionFlags, 1) != null) {
                return Optional.of(worldProgressionFlags);
            }
        }

        LOGGER.warn("EventFlags pattern matched but event flag pointer could not be validated");
        return Optional.empty();
    }

    /**
     * Reads SoulSplitter-style boss {@linkplain BossKillProgression#BOSS_DEFEATED_EVENT_FLAGS event flags}
     * (uint32 words at decoded byte offsets from {@code eventFlagsBase}).
     */
    public BossKillProgression.BossKillTally tallyKilledBosses(
            final ProcessBinding process,
            final long eventFlagsBase
    ) {

        final List<String> killedIds = new ArrayList<>();

        for (final BossKillProgression.BossDefinition boss : BossKillProgression.BOSSES) {
            final BossKillProgression.EventFlagWord word =
                    BossKillProgression.decodeEventFlag(boss.eventFlagId());
            final Integer leWord = gameMemory.readUInt32(process, eventFlagsBase + word.byteOffset());
            if (leWord == null) {
                continue;
            }
            if ((leWord & word.mask()) != 0) {
                killedIds.add(boss.id());
            }
        }

        return new BossKillProgression.BossKillTally(killedIds.size(), List.copyOf(killedIds));
    }

    /**
     * Writes {@code count_key_item_locations.txt}, logs the opened count, and refreshes overlay HTML when it changes.
     */
    public synchronized void syncKeyItemLocationsFromGameIfNeeded(
            final ProcessBinding process,
            final long eventFlagsBase
    ) {

        final ItemLotPickupIndex pickupIndex = resolvePickupIndex(process);
        final KeyItemLocationTally tally =
                tallyOpenedKeyItemLocations(process, eventFlagsBase, pickupIndex);

        if (lastSyncedKeyItemLocationOpenCount != -1 && tally.openedCount() == lastSyncedKeyItemLocationOpenCount) {
            return;
        }

        LOGGER.info("Key item locations opened: {}/{}", tally.openedCount(), tally.totalCount());

        try {
            writeIntegerFile(KEY_ITEM_LOCATIONS_COUNT_FILE, Integer.toUnsignedLong(tally.openedCount()));
            if (!KeyItemLocationProgression.writeOverlaysIfTemplatePresent(tally, pickupIndex)) {
                LOGGER.debug(
                        "Key item location overlay template not found at {}; skipping HTML output",
                        KeyItemLocationProgression.overlayTemplatePath().toAbsolutePath());
            }
            lastSyncedKeyItemLocationOpenCount = tally.openedCount();
        } catch (final IOException exception) {
            LOGGER.error("Failed to sync key item files in {}", Path.of(".").toAbsolutePath(), exception);
        }
    }

    public KeyItemLocationTally tallyOpenedKeyItemLocations(
            final ProcessBinding process,
            final long eventFlagsBase,
            final ItemLotPickupIndex pickupIndex
    ) {

        return KeyItemLocationProgression.tallyOpenedLocations(
                byteOffset -> gameMemory.readUInt32(process, eventFlagsBase + byteOffset),
                pickupIndex);
    }

    private synchronized ItemLotPickupIndex resolvePickupIndex(final ProcessBinding process) {

        final Optional<Path> installDirectory =
                MainExecutableModuleLocator.findGameInstallDirectory(process);
        if (installDirectory.isEmpty()) {
            LOGGER.debug("Game install directory unavailable; key item flags not loaded");
            return null;
        }
        final Optional<Path> gameParamFile =
                MainExecutableModuleLocator.findGameParamFile(installDirectory.get());
        if (gameParamFile.isEmpty()) {
            LOGGER.warn(
                    "GameParam.parambnd.dcx not found under {}; key item locations cannot be tracked",
                    installDirectory.get());
            return null;
        }
        try {
            final long modified = Files.getLastModifiedTime(gameParamFile.get()).toMillis();
            if (cachedPickupIndex != null && modified == cachedGameParamModifiedEpochMillis) {
                return cachedPickupIndex;
            }
            final Optional<ItemLotPickupIndex> loaded =
                    ItemLotPickupIndex.loadForCatalog(gameParamFile.get());
            if (loaded.isEmpty()) {
                LOGGER.warn("ItemLotParam.param missing or unreadable in {}", gameParamFile.get());
                return null;
            }
            cachedPickupIndex = loaded.get();
            cachedGameParamModifiedEpochMillis = modified;
            LOGGER.info(
                    "Loaded {} trackable key item pickup flags from {}",
                    cachedPickupIndex.trackableLocationCount(),
                    gameParamFile.get());
            return cachedPickupIndex;
        } catch (final IOException exception) {
            LOGGER.error("Failed to read {}", gameParamFile.get(), exception);
            return null;
        }
    }

    /** Read a 32-bit little-endian value from {@code playerGameDataBase + offsetFromBase}. */
    public Integer readUInt32(
            final ProcessBinding process,
            final long playerGameDataBase,
            final long offsetFromBase
    ) {
        return gameMemory.readUInt32(process, playerGameDataBase + offsetFromBase);
    }

    /** Uses the JDK default options (create or truncate, then write) — avoids fragile option combos on Windows. */
    private static void writeIntegerFile(final Path path, final long value) throws IOException {
        Files.writeString(path, Long.toUnsignedString(value), StandardCharsets.UTF_8);
    }
}
