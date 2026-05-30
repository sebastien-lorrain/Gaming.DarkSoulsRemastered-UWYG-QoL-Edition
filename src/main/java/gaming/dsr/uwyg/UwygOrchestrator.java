package gaming.dsr.uwyg;

import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.ptr.IntByReference;
import gaming.dsr.uwyg.equipment.EquipmentAutoUpgrader;
import gaming.dsr.uwyg.equipment.EquipmentClassifier;
import gaming.dsr.uwyg.equipment.types.enums.EquipChangeKind;
import gaming.dsr.uwyg.game.DarkSoulsProcessLocator;
import gaming.dsr.uwyg.game.GameAddresses;
import gaming.dsr.uwyg.game.MainMenuDetector;
import gaming.dsr.uwyg.game.data.AchievementsMonitorer;
import gaming.dsr.uwyg.game.data.BossKillProgression;
import gaming.dsr.uwyg.game.data.DataOffsets;
import gaming.dsr.uwyg.inventory.InventorySignatureLocator;
import gaming.dsr.uwyg.inventory.InventorySnapshotReader;
import gaming.dsr.uwyg.session.UwygSession;
import gaming.dsr.uwyg.session.UwygSession.Phase;
import gaming.dsr.uwyg.windows.Win32Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public final class UwygOrchestrator {
    private static final Logger LOGGER = LoggerFactory.getLogger(UwygOrchestrator.class);

    private final DarkSoulsProcessLocator processLocator;
    private final InventorySignatureLocator signatureLocator;
    private final InventorySnapshotReader snapshotReader;
    private final MainMenuDetector mainMenuDetector;
    private final EquipmentClassifier equipmentClassifier;
    private final EquipmentAutoUpgrader memoryEquipper;
    private final AchievementsMonitorer achievementsMonitorer;

    private long lastDeathPollingTimeNanos;

    public UwygOrchestrator(
            final DarkSoulsProcessLocator processLocator,
            final InventorySignatureLocator signatureLocator,
            final InventorySnapshotReader snapshotReader,
            final MainMenuDetector mainMenuDetector,
            final EquipmentClassifier equipmentClassifier,
            final EquipmentAutoUpgrader memoryEquipper,
            final AchievementsMonitorer achievementsMonitorer
    ) {
        this.processLocator = processLocator;
        this.signatureLocator = signatureLocator;
        this.snapshotReader = snapshotReader;
        this.mainMenuDetector = mainMenuDetector;
        this.equipmentClassifier = equipmentClassifier;
        this.memoryEquipper = memoryEquipper;
        this.achievementsMonitorer = achievementsMonitorer;
    }

    public void runForever() {
        final UwygSession session = new UwygSession();

        while (true) {
            if (session.getPhase() == Phase.FINDING_GAME) {

                if (processLocator.tryAttachRunningGame(session.getProcess())) {
                    session.setPhase(Phase.FINDING_INV);
                } else {
                    sleep(5000);
                }
            } else {

                final IntByReference exitCode = new IntByReference();
                if (Kernel32.INSTANCE.GetExitCodeProcess(session.getProcess().getHandle(), exitCode)
                        && exitCode.getValue() != Win32Constants.STILL_ACTIVE) {

                    LOGGER.info("Game has been closed, resuming lookup");
                    LOGGER.info("[Close this window if you are done playing]");
                    session.clearDeathCountTracking();

                    achievementsMonitorer.resetStatFileSyncState();

                    session.setPhase(Phase.FINDING_GAME);
                    Kernel32.INSTANCE.CloseHandle(session.getProcess().getHandle());
                }
            }

            final Phase currentPhase = session.getPhase();
            if (currentPhase == Phase.FINDING_INV) {

                LOGGER.info("Scanning game to find inventory location...");
                final List<Long> inventoryAddress = signatureLocator.findInventoryAddresses(session.getProcess());

                if (inventoryAddress.size() > 1) {

                    LOGGER.warn("Failure to uniquely locate the inventory");
                    sleep(5000);
                } else if (inventoryAddress.isEmpty()) {

                    LOGGER.warn("No inventory location found");
                    sleep(5000);
                } else {

                    LOGGER.info("Inventory location found!");

                    final GameAddresses mappedAddresses = GameAddresses.fromInventorySignature(inventoryAddress.getFirst());
                    session.setAddresses(mappedAddresses);
                    session.clearDeathCountTracking();

                    achievementsMonitorer.resetStatFileSyncState();
                    achievementsMonitorer.locatePlayerGameData(session.getProcess()).ifPresent(session::setPlayerGameData);
                    achievementsMonitorer.locateWorldProgressionFlags(session.getProcess()).ifPresent(session::setWorldProgressionFlags);

                    session.setPhase(Phase.MAIN_MENU);
                    session.resetInventorySnapshots();
                }
            } else if (currentPhase == Phase.INV_START) {

                if (snapshotReader.readInto(session.getProcess(), session.getAddresses().getInventoryBase(), session.getInventory())) {

                    session.setPhase(Phase.INV_UPDATE);
                    LOGGER.info("Initial inventory scan successful");
                } else {
                    LOGGER.warn("Initial inventory scan failed, trying again");
                    sleep(1000);
                }
            } else if (currentPhase == Phase.MAIN_MENU) {

                if (!mainMenuDetector.isMainMenu(session.getProcess(), session.getAddresses())) {

                    LOGGER.info("Opening/Resuming a game run");
                    session.setPhase(Phase.INV_START);
                } else {
                    LOGGER.debug("Displaying title menu");
                    sleep(5000);
                }
            } else if (currentPhase == Phase.INV_UPDATE) {

                if (mainMenuDetector.isMainMenu(session.getProcess(), session.getAddresses())) {

                    LOGGER.info("Going back to title menu");
                    session.setPhase(Phase.MAIN_MENU);
                } else {

                    pollDeathCount(session);

                    if (!snapshotReader.readInto(session.getProcess(), session.getAddresses().getInventoryBase(), session.getInventoryCopy())) {
                        LOGGER.warn("Inventory update failed, trying again");
                    } else {

                        for (int inventorySlotIndex = 0; inventorySlotIndex < session.getInventoryCopy().length; inventorySlotIndex++) {
                            if (!session.getInventory()[inventorySlotIndex].equals(session.getInventoryCopy()[inventorySlotIndex])) {
                                final EquipChangeKind equipChange =
                                        equipmentClassifier.detectEquipChange(
                                                session.getInventory()[inventorySlotIndex],
                                                session.getInventoryCopy()[inventorySlotIndex]);
                                if (equipChange != EquipChangeKind.NONE) {
                                    memoryEquipper.apply(session, equipChange, inventorySlotIndex);
                                }
                            }
                        }
                        session.setInventory(InventorySnapshotReader.copySlots(session.getInventoryCopy()));
                    }
                }
                sleep(1000);
            }
        }
    }

    private void pollDeathCount(final UwygSession session) {

        final Long worldProgressionFlags = session.getWorldProgressionFlags();

        if (worldProgressionFlags != null) {
            achievementsMonitorer.syncKilledBossesFileFromGameIfNeeded(
                    achievementsMonitorer.tallyKilledBosses(session.getProcess(), worldProgressionFlags));
        } else {
            achievementsMonitorer.syncKilledBossesFileFromGameIfNeeded(
                    new BossKillProgression.BossKillTally(0, List.of()));
        }

        final Long playerGameData = session.getPlayerGameData();
        if (playerGameData == null) {
            return;
        }

        final Integer deathNum = achievementsMonitorer.readUInt32(
                session.getProcess(), playerGameData, DataOffsets.DEATH_NUM);
        if (deathNum == null) {
            return;
        }

        achievementsMonitorer.syncCumulativeDeathFileFromGameIfNeeded(deathNum);
        final long now = System.nanoTime();
        if (lastDeathPollingTimeNanos == 0L || now - lastDeathPollingTimeNanos >= TimeUnit.SECONDS.toNanos(2L)) {
            lastDeathPollingTimeNanos = now;
        }

        final Integer previous = session.getLastObservedDeathCount();
        session.setLastObservedDeathCount(deathNum);

        if (previous != null && Integer.compareUnsigned(deathNum, previous) > 0) {
            achievementsMonitorer.recordDeathCountIncrease(deathNum);
        }
    }

    private static void sleep(final long millis) {
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted", interrupted);
        }
    }
}
