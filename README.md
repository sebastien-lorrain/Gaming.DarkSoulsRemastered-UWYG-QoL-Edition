# Dark Souls Remastered — UWYG QoL Edition

A companion tool for **Dark Souls Remastered** that automates the **Use What You Get (UWYG) QoL Edition** challenge run.
Every piece of equipment the player picks up is auto-equipped and auto-upgraded to stay on par with the rest of the loadout, so the run plays itself within the rules of the challenge without any inventory micromanagement.

This tools functional decisions were dictated by **legendary player and speedrunner Owarida (https://www.twitch.tv/owarida)** to bring an easy to use UWYG experience to the Souls Community.
Any request to modify this tool (which is at the root of the challenge rules) should be addressed first to him.
Then based on acceptation (for fairness and adequacy with the spirit of the challenge), yours truly will implement the requested changes.

## What it does

- Detects the running game process and locks onto the player's inventory.
- Watches for inventory changes (new loot, dropped items, etc.) and reacts in real time.
- Automatically equips the newly-picked weapon, shield, armor, ring, or spell in the appropriate slot.
- Brings the equipped piece up to a fair reinforcement level based on the rest of the loadout (see [AUTO-UPGRADE.md](AUTO-UPGRADE.md) for the exact rules).
- Tracks deaths and boss kills in plain text files next to the executable for streaming overlays to easily use (such as OBS).

The tool runs alongside the game as a separate process and communicates with it through memory reads/writes — no game files are modified.

## Requirements

- Windows 10 / 11
- A legitimate, up-to-date copy of **Dark Souls Remastered**
- Java 21+ (only if running the dependent build; the standalone build is self-contained)

## Getting started

1. Launch Dark Souls Remastered.
2. Start the tool.
3. Play. The tool will attach, locate your inventory, and take care of the rest. Run statistics appear in the `count_*.txt` files next to the executable.

## Project status

Active, experimental. Memory layouts are pinned to the current retail build of Dark Souls Remastered and may need re-tuning after game patches.

---

## Credits

| Name                                        | Contribution                                                                                                                | Link                                                |
|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------|
| Chainsboyo (modder at Nexus Mods)           | Creator of a dedicated auto-equip mod for DSR, which was very helpful <br/> to understand the inventory logic and data structure | https://www.nexusmods.com/profile/Chainsboyo        |
| JKAnderson (DSR Gadget github)              | Cheat Tool whose items database was of a great help to map advanced behaviors for auto-upgrade addition                     | https://github.com/JKAnderson/DSR-Gadget            |
| FrankvdStam (SoulSplitter github)           | Souls games event-based splitter tool for LiveSplit, from which the killed bosses metrics was inspired from                 | https://github.com/FrankvdStam/SoulSplitter         |
| FearLess Cheat Engine (DSR cheat community) | Cheat table for DSR, which provided a good head-start for advanced memory exploration                                       | https://fearlessrevolution.com/viewtopic.php?t=6856 |
