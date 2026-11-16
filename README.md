# QueueMenu

A Paper plugin for a protected Queue Menu totem in hotbar slot 4.

## Features
- Gives each player a **Totem of Undying** named **QUEUE MENU**.
- Uses bold pink/red text inspired by the supplied hotbar screenshot.
- Locks the item to the fourth hotbar slot (Bukkit slot index 3).
- Right-clicking the totem opens a 54-slot inventory titled **Queues › Preset**.
- Builds a read-only preset-queue layout inspired by the supplied GUI screenshot, with a lime glass top strip and preset icons.
- Cancels dropping, inventory movement, hotbar swaps, and moving items inside the menu.
- Restores the totem on join/respawn and only replaces it during periodic checks if the slot is actually missing or overwritten. It does not constantly recreate the item, avoiding flicker/jitter.
- Removes the protected item from death drops.

## Requirements
- Paper 1.21.11
- Java 21

## Build
Run:
```bash
mvn -B clean package
```
The plugin JAR is `target/QueueMenu.jar`.

## Install
1. Copy `QueueMenu.jar` into the server's `plugins` folder.
2. Restart the Paper server.
3. Right-click the totem in hotbar slot 4 to open the preset menu.

## Important
The menu is a visual preset layout only. Selecting a preset does not join a live queue yet because no queue plugin/API has been specified. Connect the click actions to your server's queue system to make the entries functional.
