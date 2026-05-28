package gaming.dsr.uwyg.game.data;

import gaming.dsr.uwyg.game.GameConstants;
import gaming.dsr.uwyg.game.MainExecutableModuleLocator;
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
import java.util.Map;
import java.util.Optional;

/**
 * Locates player game data and reads arbitrary uint32 fields from that block.
 * Death-count file logging lives here so persistence stays next to the same domain.
 */
@Component
public class AchievementsMonitorer {
    private static final Logger LOGGER = LoggerFactory.getLogger(AchievementsMonitorer.class);
    private static final Path CUMULATIVE_DEATH_COUNT_FILE = Path.of("count_death_cumulative.txt");
    private static final Path RUN_DEATH_COUNT_FILE = Path.of("count_death_run.txt");
    private static final Path KILLED_BOSSES_FILE = Path.of("count_killed_bosses.txt");

    private final ProcessMemoryScanner memoryScanner;
    private final Win32GameMemory gameMemory;

    private int lastSyncedCumulativeDeathCount = -1;
    private int lastSyncedBossKillCount = -1;

    public AchievementsMonitorer(
            final ProcessMemoryScanner memoryScanner,
            final Win32GameMemory gameMemory
    ) {
        this.memoryScanner = memoryScanner;
        this.gameMemory = gameMemory;
    }

    /** Resets cached values so the next attach rewrites death/boss files from live memory. */
    public synchronized void resetStatFileSyncState() {
        lastSyncedCumulativeDeathCount = -1;
        lastSyncedBossKillCount = -1;
    }

    /**
     * Keeps {@code count_death_cumulative.txt} aligned with the live game stat whenever it changes
     * (not only on death).
     */
    public synchronized void syncCumulativeDeathFileFromGameIfNeeded(final int deathNum) {

        if (lastSyncedCumulativeDeathCount != -1 && Integer.compareUnsigned(deathNum, lastSyncedCumulativeDeathCount) == 0) {
            return;
        }

        try {
            writeIntegerFile(CUMULATIVE_DEATH_COUNT_FILE, Integer.toUnsignedLong(deathNum));
            lastSyncedCumulativeDeathCount = deathNum;
        } catch (final IOException exception) {
            LOGGER.error("Failed to sync {}", CUMULATIVE_DEATH_COUNT_FILE.toAbsolutePath(), exception);
        }
    }

    /**
     * Writes {@code count_killed_bosses.txt} as a single decimal integer when the defeated-boss total changes.
     */
    public synchronized void syncKilledBossesFileFromGameIfNeeded(final BossKillProgression.BossKillTally tally) {

        if (lastSyncedBossKillCount != -1 && tally.total() == lastSyncedBossKillCount) {
            return;
        }
        final String namesJoined =
                tally.killedBossNames().isEmpty() ? "(none)" : String.join(", ", tally.killedBossNames());
        LOGGER.info("{} (bosses read as killed: {})", tally.total(), namesJoined);

        try {
            writeIntegerFile(KILLED_BOSSES_FILE, Integer.toUnsignedLong(tally.total()));
            lastSyncedBossKillCount = tally.total();
        } catch (final IOException exception) {
            LOGGER.error("Failed to sync {}", KILLED_BOSSES_FILE.toAbsolutePath(), exception);
        }
    }

    /**
     * Creates counter files as {@code 0} when missing so they exist under the JVM working directory.
     */
    @PostConstruct
    public void ensureDeathCountFilesInitialized() {
        try {
            if (!Files.exists(RUN_DEATH_COUNT_FILE)) {
                writeIntegerFile(RUN_DEATH_COUNT_FILE, 0L);
            }
            if (!Files.exists(CUMULATIVE_DEATH_COUNT_FILE)) {
                writeIntegerFile(CUMULATIVE_DEATH_COUNT_FILE, 0L);
            }
            if (!Files.exists(KILLED_BOSSES_FILE)) {
                writeIntegerFile(KILLED_BOSSES_FILE, 0L);
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

        final List<String> killedNames = new ArrayList<>();

        for (final Map.Entry<String, Integer> entry : BossKillProgression.BOSS_DEFEATED_EVENT_FLAGS.entrySet()) {
            final BossKillProgression.EventFlagWord word =
                    BossKillProgression.decodeEventFlag(entry.getValue());
            final Integer leWord = gameMemory.readUInt32(process, eventFlagsBase + word.byteOffset());
            if (leWord == null) {
                continue;
            }
            if ((leWord & word.mask()) != 0) {
                killedNames.add(entry.getKey());
            }
        }

        return new BossKillProgression.BossKillTally(killedNames.size(), List.copyOf(killedNames));
    }

    /** Read a 32-bit little-endian value from {@code playerGameDataBase + offsetFromBase}. */
    public Integer readUInt32(
            final ProcessBinding process,
            final long playerGameDataBase,
            final long offsetFromBase
    ) {
        return gameMemory.readUInt32(process, playerGameDataBase + offsetFromBase);
    }

    /**
     * Bumps {@code count_death_run.txt}; cumulative totals are kept by {@link #syncCumulativeDeathFileFromGameIfNeeded}.
     * Logs the in-game cumulative value (unsigned uint32) immediately before touching the run file.
     */
    public synchronized void recordDeathCountIncrease(final int deathCountBits) {

        LOGGER.info("Death increased; in-game cumulative deaths (uint32): {}", Integer.toUnsignedLong(deathCountBits));

        try {
            final long previousRun = readRunDeathCountFromFile();
            writeIntegerFile(RUN_DEATH_COUNT_FILE, previousRun + 1);
        } catch (final IOException exception) {
            LOGGER.error("Failed to update {}", RUN_DEATH_COUNT_FILE.toAbsolutePath(), exception);
        }
    }

    private static long readRunDeathCountFromFile() throws IOException {

        if (!Files.exists(RUN_DEATH_COUNT_FILE)) {
            return 0L;
        }

        final String raw = Files.readString(RUN_DEATH_COUNT_FILE, StandardCharsets.UTF_8).trim();
        if (raw.isEmpty()) {
            return 0L;
        }

        try {
            return Long.parseUnsignedLong(raw);
        } catch (final NumberFormatException exception) {
            LOGGER.warn("Ignoring invalid contents in {} ({}), treating run count as 0", RUN_DEATH_COUNT_FILE, raw);
            return 0L;
        }
    }

    /** Uses the JDK default options (create or truncate, then write) — avoids fragile option combos on Windows. */
    private static void writeIntegerFile(final Path path, final long value) throws IOException {
        Files.writeString(path, Long.toUnsignedString(value), StandardCharsets.UTF_8);
    }
}
